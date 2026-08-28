from datetime import date
from decimal import Decimal

from finances_ai.categorize import categorize_one
from finances_ai.models import ParsedTransaction, TransactionDirection


def make_transaction(description: str) -> ParsedTransaction:
    return ParsedTransaction(
        transaction_date=date(2026, 8, 14),
        description=description,
        merchant=description.upper(),
        amount=Decimal("10.00"),
        direction=TransactionDirection.DEBIT,
        dedupe_key="k" * 32,
    )


def test_matches_merchant_rule():
    suggestion = categorize_one(make_transaction("KROGER"))

    assert suggestion.category == "Groceries"
    assert suggestion.method == "rule"
    assert suggestion.confidence >= 0.9
    assert suggestion.is_transfer is False


def test_card_payment_is_flagged_as_transfer_not_expense():
    """The double-counting guard. See docs/DOMAIN.md — this is the one that matters."""
    suggestion = categorize_one(make_transaction("Payment Thank You - Web"))

    assert suggestion.is_transfer is True
    assert suggestion.category is None


def test_a_card_autopay_is_still_a_transfer():
    """The card-payment wording is what identifies it, not the word "autopay"."""
    assert categorize_one(make_transaction("AUTOPAY 12345 THANK YOU")).is_transfer is True
    assert categorize_one(make_transaction("ACH PAYMENT CHASE CREDIT CRD - AUTOPAY")).is_transfer


def test_a_utility_paid_by_autopay_is_an_expense_not_a_transfer():
    """This replaced a test asserting that any "AUTOPAY" row is a transfer.

    That assumption came from card statements, where autopay means paying the card. A real checking
    export disproved it: "ACH PAYMENT EVERGY METRO ... AUTOPAY" is an electric bill, and treating it
    as a transfer silently removed a genuine expense from the budget — the worst kind of wrong,
    because nothing looks broken.
    """
    suggestion = categorize_one(make_transaction("ACH PAYMENT EVERGY METRO EVERGY AUTOPAY - AUTO"))

    assert suggestion.is_transfer is False
    assert suggestion.category == "Utilities"


def test_a_named_merchant_outranks_generic_transfer_wording():
    """Ordering, pinned.

    Merchant rules run before transfer patterns because identifying who was paid is stronger
    evidence than phrasing that only describes how. With the weaker check first, the utility above
    was classified as a transfer.
    """
    suggestion = categorize_one(make_transaction("TRANSFER TO KROGER #4521"))

    assert suggestion.category == "Groceries"
    assert suggestion.is_transfer is False


def test_a_transfer_to_the_users_own_brokerage_is_not_spending():
    """Eight of forty rows in one real month were these.

    Money moved into an investment account has not been spent; counting it inflates the budget by
    everything being invested.
    """
    suggestion = categorize_one(make_transaction("ACH PAYMENT FID BKG SVC LLC - MONEYLINE"))

    assert suggestion.is_transfer is True
    assert suggestion.category is None


def test_unknown_merchant_admits_it_rather_than_guessing():
    suggestion = categorize_one(make_transaction("SOME BRAND NEW PLACE"))

    assert suggestion.category is None
    assert suggestion.confidence == 0.0
    assert suggestion.is_transfer is False


def test_source_row_type_outranks_description_patterns():
    """A card payment with an unremarkable description must still be flagged."""
    transaction = ParsedTransaction(
        transaction_date=date(2026, 8, 10),
        description="ACH DEBIT 8842",  # matches none of the transfer patterns
        merchant="ACH DEBIT",
        amount=Decimal("512.44"),
        direction=TransactionDirection.CREDIT,
        dedupe_key="k1",
        is_probable_transfer=True,
    )

    suggestion = categorize_one(transaction)

    assert suggestion.is_transfer is True
    assert suggestion.category is None
    # Higher than a pattern match, because the file said so rather than a regex inferring it.
    assert suggestion.confidence > 0.95


def test_a_purchase_is_not_flagged_just_because_it_is_a_credit():
    transaction = ParsedTransaction(
        transaction_date=date(2026, 8, 10),
        description="KROGER #4521",
        merchant="KROGER",
        amount=Decimal("84.31"),
        direction=TransactionDirection.DEBIT,
        dedupe_key="k2",
        is_probable_transfer=False,
    )

    suggestion = categorize_one(transaction)

    assert suggestion.is_transfer is False
    assert suggestion.category == "Groceries"
