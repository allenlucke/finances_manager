"""Tier 1 of categorization: deterministic rules — and the arbitration every tier feeds into.

The full design is three tiers, cheapest first (docs/DECISIONS.md D-15):

1. rules            — this module. Free, instant, handles the long tail of repeat merchants.
2. similarity       — nearest neighbour against Allen's own corrected history. Not built yet.
3. model fallback   — an LLM, for genuinely novel merchants only. Not built yet.

Shape
-----
Each tier is a function ``(transaction, context) -> list[Candidate]``: zero candidates when it has
nothing to say, several when it is torn. Every candidate's confidence is on one scale — *how
strong is this evidence* — so a single arbitration point can pick the winner by confidence alone,
with the earlier tier breaking ties. There is no first-match-wins over two tuples any more, and no
"rule" stamped on a suggestion that no rule produced: an empty candidate list is reported as
``method="none"``, which is what lets the API tell "escalate to the next tier" from "a rule fired".

Adding tier 2 means writing one function and appending it to ``TIERS``.

The scale, so the numbers mean something:

* 0.99 — the institution said so. A Chase export marking a row ``Payment`` is a statement of
  fact, not an inference.
* 0.90 — a named merchant. Who was paid identifies what it was.
* 0.80 — wording that describes how money moved. "TRANSFER TO" is real evidence and weaker than
  a merchant name, which is why a utility paid by autopay is Utilities and not a transfer.

The most important job here is not picking the right expense category — it's recognizing what
*isn't an expense at all*. Card payments and inter-account transfers move money that was already
budgeted when the purchase happened; counting them again double-charges the budget. The legacy
schema encoded this with the ``paymentToCreditAccount`` flag and it is the single subtlest piece of
business logic in the project. See docs/DOMAIN.md.
"""

from __future__ import annotations

import json
import re
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from importlib import resources

from finances_ai.models import CategorySuggestion, ParsedTransaction

CONFIDENCE_SOURCE_HINT = 0.99
CONFIDENCE_MERCHANT = 0.90
CONFIDENCE_TRANSFER_WORDING = 0.80


@dataclass(frozen=True)
class Context:
    """What the caller knows that the row does not say.

    ``account_type`` is the API's own code for the account the rows belong to — ``credit_card``,
    ``checking`` and so on — or None when the caller does not know. It matters because the same
    words mean different things on different accounts: "PAYMENT THANK YOU" on a card is the card
    being paid, a transfer; nothing on a checking statement says thank you, and a bare "PAYMENT"
    there is a bill. The OFX reader has always refused to guess PAYMENT's meaning without this.
    """

    account_type: str | None = None


@dataclass(frozen=True)
class Candidate:
    category: str | None
    confidence: float
    method: str
    rationale: str
    is_transfer: bool = False


Tier = Callable[[ParsedTransaction, Context], list[Candidate]]


@dataclass(frozen=True)
class MerchantRule:
    pattern: re.Pattern[str]
    category: str
    # The descriptions the rule was written for, and near-misses it must refuse. Not documentation:
    # tests/test_rules_table.py runs every rule against both, which is how a rule that can never
    # match a real description — five of them, once — stops being possible to add.
    matches: tuple[str, ...]
    rejects: tuple[str, ...]


def _load_merchant_rules() -> tuple[MerchantRule, ...]:
    """Rules live in a data file rather than in source so corrections can append to them.

    Every confirmed correction from the review queue is meant to grow this table; a table that
    only a code change can grow never will.
    """
    text = resources.files("finances_ai.categorize").joinpath("merchant_rules.json").read_text()
    loaded = json.loads(text)["rules"]
    return tuple(
        MerchantRule(
            pattern=re.compile(rule["pattern"]),
            category=rule["category"],
            matches=tuple(rule.get("matches", ())),
            rejects=tuple(rule.get("rejects", ())),
        )
        for rule in loaded
    )


MERCHANT_RULES: tuple[MerchantRule, ...] = _load_merchant_rules()


@dataclass(frozen=True)
class TransferPattern:
    pattern: re.Pattern[str]
    # Account types the wording is a transfer on. None means any. See Context.account_type.
    on: frozenset[str] | None = None


_CARD_LIKE = frozenset({"credit_card"})

# Wording that means "this moves money between the user's own accounts."
TRANSFER_PATTERNS: tuple[TransferPattern, ...] = (
    # NOTE: a bare \bAUTOPAY\b rule used to live here and was removed. On a card statement
    # "autopay" means paying the card; on a checking account it means auto-paying a bill, so it
    # matched "ACH PAYMENT EVERGY METRO ... AUTOPAY" — an electric bill — and silently deleted
    # a real expense from the budget. Generic words describing *how* a payment was made say
    # nothing about *what* it was.
    #
    # "Thank you" only means a card payment when it accompanies a payment word, and only on a
    # card: it is the issuer thanking the cardholder. Restricted to card accounts (or an unknown
    # one) so the same words on a checking statement — a merchant's receipt text — are not read as
    # money moving between the user's own accounts.
    TransferPattern(re.compile(r"\b(PAYMENT|AUTOPAY|PMT)\b.{0,40}\bTHANK\s*YOU\b"), on=_CARD_LIKE),
    # ONLINE PAYMENT and E-PAYMENT describe *how*, exactly like AUTOPAY did. Bare, they matched
    # "ONLINE PAYMENT TO CITY UTILITIES" and "E-PAYMENT DENTIST" — and because a transfer is
    # uncategorizable by CHECK constraint, those expenses were not misfiled, they were removed
    # from the budget. They need a card-ish token alongside, the same way the THANK YOU rule
    # needs a payment word.
    TransferPattern(
        re.compile(
            r"\b(ONLINE|E-?)\s*PAYMENT\b.{0,40}"
            r"\b(CARD|CRD|VISA|MASTERCARD|AMEX|DISCOVER|CHASE|CITI|CAPITAL\s*ONE|BARCLAY)\b"
        )
    ),
    TransferPattern(re.compile(r"\bCARDMEMBER\s+SERV\b")),
    TransferPattern(re.compile(r"\bTRANSFER\s+(TO|FROM)\b")),
    # ZELLE is gone. A Zelle to a plumber, a landlord or a friend is spending, and nothing in the
    # description can tell that apart from a Zelle to your own savings. The review queue can; a
    # suppression cannot.
    TransferPattern(re.compile(r"\bINTERNAL\s+TRANSFER\b")),
    # Money moving to the user's own brokerage. Found in a real checking export as
    # "ACH PAYMENT FID BKG SVC LLC" — eight times in one month, every one of them counted as
    # spending, which inflates the budget by the whole amount being invested.
    TransferPattern(re.compile(r"\bFID\s+BKG\s+SVC\b")),
    TransferPattern(re.compile(r"\bFIDELITY\s+(BROKERAGE|INVESTMENTS)\b")),
    # Buying a Treasury bill from checking is the same thing: an asset the user still owns. It
    # used to be caught by a \bTREASURY\b merchant rule and booked as Taxes — spending, at 0.9.
    TransferPattern(re.compile(r"\bTREASURY\s*DIRECT\b")),
    # A card payment described from the paying account's side rather than the card's.
    # "ACH PAYMENT CHASE CREDIT CRD" is the other half of "PAYMENT THANK YOU".
    TransferPattern(re.compile(r"\bCREDIT\s+CRD\b")),
    TransferPattern(re.compile(r"\bCREDIT\s+CARD\s+(PAYMENT|PMT)\b")),
)


def _haystack(transaction: ParsedTransaction) -> str:
    return (transaction.merchant or transaction.description).upper()


def source_hint_tier(transaction: ParsedTransaction, context: Context) -> list[Candidate]:
    """What the file itself said about the row.

    Chase labelling a row "Payment" is the institution stating what it is; a regex over the
    description is a guess. Highest confidence on the scale, so a card payment with an unusual
    description is still caught.
    """
    if not transaction.is_probable_transfer:
        return []
    return [
        Candidate(
            category=None,
            confidence=CONFIDENCE_SOURCE_HINT,
            method="rule",
            rationale="Source file marks this row as a payment or transfer",
            is_transfer=True,
        )
    ]


def merchant_rule_tier(transaction: ParsedTransaction, context: Context) -> list[Candidate]:
    """Named merchants. Every rule that matches is a candidate; arbitration takes the first."""
    haystack = _haystack(transaction)
    return [
        Candidate(
            category=rule.category,
            confidence=CONFIDENCE_MERCHANT,
            method="rule",
            rationale=f"Matched merchant pattern {rule.pattern.pattern!r}",
        )
        for rule in MERCHANT_RULES
        if rule.pattern.search(haystack)
    ]


def transfer_wording_tier(transaction: ParsedTransaction, context: Context) -> list[Candidate]:
    """Wording that describes money moving rather than something being bought.

    Ranked below a merchant match on purpose. Transfer patterns match generic phrasing about how
    money moved; a merchant match identifies who it went to, which is far stronger evidence. With
    this tier ranked higher, an electric bill paid by autopay was classified as a transfer and
    vanished from spending.
    """
    haystack = _haystack(transaction)
    return [
        Candidate(
            category=None,
            confidence=CONFIDENCE_TRANSFER_WORDING,
            method="rule",
            rationale=f"Matched transfer pattern {entry.pattern.pattern!r}",
            is_transfer=True,
        )
        for entry in TRANSFER_PATTERNS
        if (entry.on is None or context.account_type is None or context.account_type in entry.on)
        and entry.pattern.search(haystack)
    ]


# In order. A later tier only breaks a tie with an earlier one; confidence decides otherwise.
TIERS: tuple[Tier, ...] = (source_hint_tier, merchant_rule_tier, transfer_wording_tier)


def arbitrate(candidates: list[Candidate]) -> Candidate | None:
    """The one arbitration point. Highest confidence wins; the earliest candidate breaks a tie."""
    if not candidates:
        return None
    return max(candidates, key=lambda candidate: candidate.confidence)


def categorize_one(
    transaction: ParsedTransaction, context: Context | None = None
) -> CategorySuggestion:
    """Suggest a category for a single transaction."""
    context = context or Context()
    candidates = [candidate for tier in TIERS for candidate in tier(transaction, context)]
    winner = arbitrate(candidates)

    if winner is None:
        # An honest "I don't know". method="none" rather than "rule": no rule fired, and the API
        # needs to be able to tell that from a rule that decided on "uncategorized".
        return CategorySuggestion(
            dedupe_key=transaction.dedupe_key,
            category=None,
            confidence=0.0,
            method="none",
            rationale="No tier produced a candidate; needs review",
        )

    return CategorySuggestion(
        dedupe_key=transaction.dedupe_key,
        category=winner.category,
        confidence=winner.confidence,
        method=winner.method,
        rationale=winner.rationale,
        is_transfer=winner.is_transfer,
    )


def categorize(
    transactions: Iterable[ParsedTransaction], account_type: str | None = None
) -> list[CategorySuggestion]:
    context = Context(account_type=account_type)
    return [categorize_one(transaction, context) for transaction in transactions]
