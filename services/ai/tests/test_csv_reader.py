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


def test_chase_type_column_marks_payments_as_probable_transfers():
    """The Type column is the institution stating what a row is, not a guess about it."""
    content = (
        "Transaction Date,Post Date,Description,Category,Type,Amount,Memo\n"
        "08/14/2026,08/15/2026,KROGER #4521,Groceries,Sale,-84.31,\n"
        "08/10/2026,08/11/2026,AUTOPAY 1234,,Payment,512.44,\n"
        "08/09/2026,08/10/2026,KROGER #4521,Groceries,Return,12.00,\n"
    )
    result = parse_csv(content, account_ref="chase-1234")

    purchase, payment, refund = result.transactions
    assert purchase.is_probable_transfer is False
    assert payment.is_probable_transfer is True
    # A refund is NOT a transfer. It used to be reported as one, and downstream that meant
    # uncategorizable forever — the category it refunded stayed overcharged.
    assert refund.is_probable_transfer is False
    assert refund.is_probable_refund is True
    assert payment.is_probable_refund is False


def test_generic_format_has_no_type_column_so_never_guesses_a_transfer():
    """Absence of the signal must read as "unknown", never as "not a transfer"."""
    content = "Date,Description,Amount\n08/14/2026,PAYMENT THANK YOU,512.44\n"
    result = parse_csv(content, account_ref="acct")

    # The description looks like a payment, but this format carries no Type column, so the parser
    # reports nothing. Deciding is the categorizer's job.
    assert result.transactions[0].is_probable_transfer is False


def test_a_negative_figure_in_the_credit_column_is_money_out():
    """A reversed deposit. abs() used to make it a deposit of the same size."""
    content = (
        "Transaction Number,Date,Description,Memo,Amount Debit,Amount Credit,Balance,Check Number,Fees\n"
        '"1",08/26/2026,"DEPOSIT REVERSAL","",,-25.00,"975.00",,\n'
    )
    result = parse_csv(content, account_ref="cacu")

    assert result.transactions[0].direction == TransactionDirection.DEBIT
    assert result.transactions[0].amount == Decimal("25.00")


AMEX_CSV = """Date,Description,Card Member,Account #,Amount,Extended Details,Appears On Your Statement As,Address,City/State,Zip Code,Country,Reference,Category
08/14/2026,KROGER #4521,A CARDMEMBER,-31004,84.31,KROGER #4521 ANYTOWN KS,KROGER #4521,123 MAIN ST,ANYTOWN KS,66000,UNITED STATES,'320262290000000001',Merchandise & Supplies-Groceries
08/10/2026,AUTOPAY PAYMENT - THANK YOU,A CARDMEMBER,-31004,-500.00,,AUTOPAY PAYMENT - THANK YOU,,,,,'320262250000000002',
"""


def test_an_amex_export_is_read_with_its_own_sign_convention():
    """Amex writes charges positive and payments negative — the opposite of every bank export.

    Its header is a superset of the generic shape, so without its own format the generic rule read
    every purchase as a credit and every payment as a debit, with no warning and no running balance
    to catch it.
    """
    result = parse_csv(AMEX_CSV, account_ref="amex")

    assert result.source_format == "amex_card"
    assert result.warnings == []
    purchase, payment = result.transactions
    assert purchase.direction == TransactionDirection.DEBIT
    assert purchase.amount == Decimal("84.31")
    assert payment.direction == TransactionDirection.CREDIT
    assert payment.amount == Decimal("500.00")


def test_an_amex_row_carries_neither_the_card_number_nor_the_address():
    result = parse_csv(AMEX_CSV, account_ref="amex")

    for transaction in result.transactions:
        on_the_wire = transaction.model_dump_json()
        assert "-31004" not in on_the_wire
        assert "123 MAIN ST" not in on_the_wire
        assert "A CARDMEMBER" not in on_the_wire


def test_a_generic_file_that_is_mostly_credits_is_flagged():
    """The generic rule has to assume a sign convention. A month of spending that reads as mostly
    money in is the signature of the assumption being wrong for this file."""
    # Days above 12, so the dates are unambiguous and the only warning is the one under test.
    content = "Date,Description,Amount\n" + "".join(
        f"08/{day:02d}/2026,STORE {day},{day}.00\n" for day in range(13, 23)
    )
    result = parse_csv(content, account_ref="x")

    assert result.source_format == "generic"
    assert len(result.warnings) == 1
    assert "10 of 10 rows read as money in" in result.warnings[0]
    assert "inverted" in result.warnings[0]


def test_a_generic_file_that_is_mostly_debits_is_not_flagged():
    content = "Date,Description,Amount\n" + "".join(
        f"08/{day:02d}/2026,STORE {day},-{day}.00\n" for day in range(13, 23)
    )
    assert parse_csv(content, account_ref="x").warnings == []
