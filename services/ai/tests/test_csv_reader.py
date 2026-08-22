from datetime import date
from decimal import Decimal

import pytest

from finances_ai.ingest import ParserNotFoundError, parse_csv
from finances_ai.ingest.csv_reader import dedupe_key, normalize_description
from finances_ai.models import TransactionDirection

CHASE_CSV = """Transaction Date,Post Date,Description,Category,Type,Amount,Memo
08/14/2026,08/15/2026,KROGER #0123,Groceries,Sale,-84.32,
08/12/2026,08/13/2026,SHELL OIL 9876543,Gas,Sale,-41.10,
08/01/2026,08/02/2026,Payment Thank You - Web,,Payment,500.00,
"""

GENERIC_CSV = """Date,Description,Amount
2026-08-14,COFFEE SHOP,-4.50
2026-08-15,PAYCHECK,"2,500.00"
"""


def test_parses_chase_export():
    result = parse_csv(CHASE_CSV, account_ref="chase-1234")

    assert result.source_format == "chase_card"
    assert len(result.transactions) == 3
    assert result.warnings == []

    groceries = result.transactions[0]
    assert groceries.transaction_date == date(2026, 8, 14)
    assert groceries.posted_date == date(2026, 8, 15)
    assert groceries.amount == Decimal("84.32")
    assert groceries.direction == TransactionDirection.DEBIT

    payment = result.transactions[2]
    assert payment.direction == TransactionDirection.CREDIT
    assert payment.amount == Decimal("500.00")


def test_amounts_are_decimal_not_float():
    result = parse_csv(CHASE_CSV, account_ref="chase-1234")
    for transaction in result.transactions:
        assert isinstance(transaction.amount, Decimal)


def test_parses_generic_export_with_thousands_separator():
    result = parse_csv(GENERIC_CSV, account_ref="bank-1")

    assert result.source_format == "generic"
    assert result.transactions[1].amount == Decimal("2500.00")
    assert result.transactions[1].direction == TransactionDirection.CREDIT


def test_reimport_produces_identical_dedupe_keys():
    """Re-importing an overlapping statement must be a no-op for the API."""
    first = parse_csv(CHASE_CSV, account_ref="chase-1234")
    second = parse_csv(CHASE_CSV, account_ref="chase-1234")

    assert [t.dedupe_key for t in first.transactions] == [t.dedupe_key for t in second.transactions]


def test_dedupe_keys_differ_across_accounts():
    same_day = dedupe_key("acct-a", date(2026, 8, 14), Decimal("-10.00"), "KROGER")
    other = dedupe_key("acct-b", date(2026, 8, 14), Decimal("-10.00"), "KROGER")

    assert same_day != other


def test_dedupe_key_survives_store_number_noise():
    a = dedupe_key("acct-a", date(2026, 8, 14), Decimal("-10.00"), "KROGER #0123")
    b = dedupe_key("acct-a", date(2026, 8, 14), Decimal("-10.00"), "kroger  #0123")

    assert a == b


def test_normalize_description_strips_trailing_reference():
    assert normalize_description("  SHELL OIL   9876543 ") == "SHELL OIL"
    assert normalize_description("KROGER #0123") == "KROGER"


def test_bad_rows_become_warnings_not_failures():
    csv_text = "Date,Description,Amount\nnot-a-date,X,-1.00\n2026-08-14,Y,-2.00\n"
    result = parse_csv(csv_text)

    assert len(result.transactions) == 1
    assert len(result.warnings) == 1
    assert "line 2" in result.warnings[0]


def test_unknown_format_raises():
    with pytest.raises(ParserNotFoundError):
        parse_csv("Col1,Col2\n1,2\n")
