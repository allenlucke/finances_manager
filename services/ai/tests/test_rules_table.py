"""Every rule proves itself against real descriptions, and the engine's shape is pinned.

Five merchant rules once could not match any real description — a word boundary after a truncated
word (UNITED AIR\\b) never lands — and two matched far too much (\\bDELTA\\b caught Delta Dental,
\\bTREASURY\\b booked a Treasury-bill purchase as Taxes). Neither kind of mistake survives a table
that runs every rule against the strings it was written for and the near-misses it must refuse.
"""

from datetime import date
from decimal import Decimal

import pytest

from finances_ai.categorize import (
    MERCHANT_RULES,
    TRANSFER_PATTERNS,
    Candidate,
    Context,
    arbitrate,
    categorize,
    categorize_one,
)
from finances_ai.ingest.common import normalize_description
from finances_ai.models import ParsedTransaction, TransactionDirection


def make_transaction(description: str, **overrides) -> ParsedTransaction:
    # The merchant is built the way the CSV reader builds it — normalized, which strips a trailing
    # store number — because that is the text the rules actually see. Uppercasing alone let two
    # fuel rules pass this table while "QT 1234" and "BP#9876" could never match in production:
    # the digits the patterns required were exactly what normalization removed.
    fields = {
        "transaction_date": date(2026, 8, 14),
        "description": description,
        "merchant": normalize_description(description),
        "amount": Decimal("10.00"),
        "direction": TransactionDirection.DEBIT,
        "dedupe_key": "k" * 32,
    }
    fields.update(overrides)
    return ParsedTransaction(**fields)


@pytest.mark.parametrize("rule", MERCHANT_RULES, ids=[r.category for r in MERCHANT_RULES])
def test_every_rule_carries_examples_and_near_misses(rule):
    assert rule.matches, f"{rule.category}: a rule with no example cannot be shown to work"
    assert rule.rejects, f"{rule.category}: a rule with no near-miss cannot be shown to be bounded"


@pytest.mark.parametrize(
    ("rule", "text"),
    [(rule, text) for rule in MERCHANT_RULES for text in rule.matches],
    ids=[f"{rule.category}:{text}" for rule in MERCHANT_RULES for text in rule.matches],
)
def test_every_rule_matches_each_description_it_was_written_for(rule, text):
    assert rule.pattern.search(text.upper()), f"{rule.pattern.pattern!r} misses {text!r}"
    # And end to end: the merchant wins the arbitration, not some transfer wording in the text.
    suggestion = categorize_one(make_transaction(text))
    assert suggestion.category == rule.category
    assert suggestion.is_transfer is False


@pytest.mark.parametrize(
    ("rule", "text"),
    [(rule, text) for rule in MERCHANT_RULES for text in rule.rejects],
    ids=[f"{rule.category}:{text}" for rule in MERCHANT_RULES for text in rule.rejects],
)
def test_every_rule_refuses_its_near_misses(rule, text):
    assert not rule.pattern.search(text.upper()), f"{rule.pattern.pattern!r} matches {text!r}"
    assert categorize_one(make_transaction(text)).category != rule.category


def test_no_two_rules_claim_the_same_example():
    seen: dict[str, str] = {}
    for rule in MERCHANT_RULES:
        for text in rule.matches:
            for other in MERCHANT_RULES:
                if other is not rule and other.pattern.search(text.upper()):
                    seen[text] = f"{rule.category} and {other.category}"
    assert seen == {}


@pytest.mark.parametrize(
    "description",
    [
        # Money to the user's own assets. The Treasury one was booked as Taxes at 0.9 for a while.
        "TREASURYDIRECT TREAS PURCHASE",
        "TREASURY DIRECT PURCHASE",
        "ACH PAYMENT FID BKG SVC LLC - MONEYLINE",
        "FIDELITY BROKERAGE",
        "TRANSFER TO SAVINGS 1234",
        "INTERNAL TRANSFER",
        "CARDMEMBER SERV WEB PYMT",
        "ACH PAYMENT CHASE CREDIT CRD - AUTOPAY",
        "CREDIT CARD PAYMENT",
    ],
)
def test_the_transfer_wording_list(description):
    suggestion = categorize_one(make_transaction(description))
    assert suggestion.is_transfer is True
    assert suggestion.category is None


@pytest.mark.parametrize(
    "description",
    [
        # Must never be a transfer. Each one is spending that a looser rule once removed from the
        # budget, or would.
        "DELTA DENTAL OF KANSAS",
        "ONLINE PAYMENT TO CITY UTILITIES",
        "E-PAYMENT DENTIST",
        "ZELLE PAYMENT TO PLUMBER JOE",
        "ZELLE TO LANDLORD",
        "AUTOPAY EVERGY METRO",
        "PAYMENT TO DR SMITH",
        "VENMO PAYMENT",
        "RENT PAYMENT",
        "PMT ORTHODONTIST",
    ],
)
def test_the_never_a_transfer_list(description):
    assert categorize_one(make_transaction(description)).is_transfer is False


def test_delta_dental_is_not_travel_and_not_anything_else_either():
    suggestion = categorize_one(make_transaction("DELTA DENTAL OF KANSAS"))
    assert suggestion.category is None
    assert suggestion.method == "none"


def test_nothing_matched_is_reported_as_none_not_as_a_rule():
    """The API has to tell "escalate to the next tier" from "a rule decided this"."""
    suggestion = categorize_one(make_transaction("SOME BRAND NEW PLACE"))
    assert suggestion.method == "none"
    assert suggestion.confidence == 0.0
    assert suggestion.category is None
    assert suggestion.is_transfer is False


def test_a_rule_that_fired_says_so():
    assert categorize_one(make_transaction("KROGER")).method == "rule"
    assert categorize_one(make_transaction("TRANSFER TO SAVINGS")).method == "rule"


def test_thank_you_wording_is_a_card_payment_only_on_a_card():
    """The same words mean different things on different accounts."""
    text = "PAYMENT THANK YOU - WEB"
    assert categorize_one(make_transaction(text), Context(account_type="credit_card")).is_transfer
    # Unknown account: the historical behaviour, a card payment.
    assert categorize_one(make_transaction(text), Context()).is_transfer
    # On a checking statement nothing says thank you but a merchant's receipt text.
    for account_type in ("checking", "savings", "brokerage"):
        suggestion = categorize_one(make_transaction(text), Context(account_type=account_type))
        assert suggestion.is_transfer is False, account_type


def test_account_type_reaches_every_row_through_categorize():
    rows = [make_transaction("PAYMENT THANK YOU"), make_transaction("KROGER")]
    checking = categorize(rows, account_type="checking")
    card = categorize(rows, account_type="credit_card")

    assert [s.is_transfer for s in checking] == [False, False]
    assert [s.is_transfer for s in card] == [True, False]


def test_confidences_are_on_one_scale_so_arbitration_is_by_confidence_alone():
    hint = categorize_one(make_transaction("ACH DEBIT 8842", is_probable_transfer=True))
    merchant = categorize_one(make_transaction("KROGER"))
    wording = categorize_one(make_transaction("TRANSFER TO SAVINGS"))

    assert hint.confidence > merchant.confidence > wording.confidence > 0.0


def test_a_merchant_outranks_transfer_wording_in_the_same_description():
    """Pinned: who was paid is stronger evidence than how. The utility-by-autopay lesson."""
    suggestion = categorize_one(make_transaction("TRANSFER TO KROGER #4521"))
    assert suggestion.category == "Groceries"
    assert suggestion.is_transfer is False


def test_the_source_hint_outranks_a_merchant_match():
    suggestion = categorize_one(make_transaction("KROGER", is_probable_transfer=True))
    assert suggestion.is_transfer is True
    assert suggestion.category is None


def test_arbitration_takes_the_highest_confidence_and_the_earliest_on_a_tie():
    first = Candidate(category="A", confidence=0.9, method="rule", rationale="first")
    second = Candidate(category="B", confidence=0.9, method="rule", rationale="second")
    stronger = Candidate(category="C", confidence=0.95, method="similarity", rationale="third")

    assert arbitrate([]) is None
    assert arbitrate([first, second]) is first
    assert arbitrate([first, second, stronger]) is stronger


def test_every_transfer_pattern_is_exercised_by_the_list_above():
    """A pattern nothing in the tests can trigger is a pattern nobody knows the effect of."""
    covered = set()
    for text in [
        "PAYMENT THANK YOU - WEB",
        "ONLINE PAYMENT CHASE CARD",
        "CARDMEMBER SERV WEB PYMT",
        "TRANSFER TO SAVINGS 1234",
        "INTERNAL TRANSFER",
        "ACH PAYMENT FID BKG SVC LLC",
        "FIDELITY BROKERAGE",
        "TREASURYDIRECT TREAS PURCHASE",
        "ACH PAYMENT CHASE CREDIT CRD",
        "CREDIT CARD PAYMENT",
    ]:
        for entry in TRANSFER_PATTERNS:
            if entry.pattern.search(text):
                covered.add(entry.pattern.pattern)
    assert covered == {entry.pattern.pattern for entry in TRANSFER_PATTERNS}
