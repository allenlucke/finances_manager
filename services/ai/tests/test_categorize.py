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


def test_autopay_is_a_transfer():
    assert categorize_one(make_transaction("AUTOPAY 12345 THANK YOU")).is_transfer is True


def test_unknown_merchant_admits_it_rather_than_guessing():
    suggestion = categorize_one(make_transaction("SOME BRAND NEW PLACE"))

    assert suggestion.category is None
    assert suggestion.confidence == 0.0
    assert suggestion.is_transfer is False
