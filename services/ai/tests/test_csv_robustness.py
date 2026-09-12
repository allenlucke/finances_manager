"""What a real export does to a parser that was written against a clean sample.

Every case here is a shape that either crashed the whole import, or worse, dropped a row without
a word. The rule the parser now follows: a row with a date is always attempted and its failure
reported; a row with no date that is narrower than the header is footer prose and is skipped.
"""

import time
from datetime import date
from decimal import Decimal

import pytest

from finances_ai.ingest import ParserNotFoundError, parse_csv
from finances_ai.models import TransactionDirection

CACU_HEADER = (
    "Transaction Number,Date,Description,Memo,Amount Debit,Amount Credit,Balance,"
    "Check Number,Fees  \n"
)


def test_a_short_row_that_carries_a_date_is_attempted_and_its_failure_reported():
    """A three-field rent line in a nine-column export used to vanish with no warning."""
    content = (
        CACU_HEADER
        + '"20260826",08/26/2026,"KROGER","4521",-84.31,,"1000.00",,\n'
        + '"20260801",08/01/2026,"RENT"\n'
    )
    result = parse_csv(content, account_ref="acct")

    assert len(result.transactions) == 1
    assert len(result.warnings) == 1
    assert "line 3" in result.warnings[0]
    assert "neither a debit nor a credit" in result.warnings[0]


def test_footer_prose_without_a_date_is_skipped_without_comment():
    content = (
        "Date,Description,Amount\n"
        "08/14/2026,KROGER,-84.31\n"
        "\n"
        '"Brokerage services are provided by Example Brokerage Services LLC, Member NYSE, SIPC."\n'
        "Date downloaded 08/27/2026 02:19 am\n"
    )
    result = parse_csv(content, account_ref="acct")

    assert len(result.transactions) == 1
    assert result.warnings == []


def test_a_full_width_row_with_an_unreadable_date_is_a_broken_row_and_is_reported():
    content = "Date,Description,Amount\n08/14/2026,KROGER,-84.31\nnope,COFFEE,-4.50\n"
    result = parse_csv(content, account_ref="acct")

    assert len(result.transactions) == 1
    assert len(result.warnings) == 1
    assert "line 3" in result.warnings[0]
    assert "Unrecognized date" in result.warnings[0]


def test_extra_fields_beyond_the_header_are_ignored():
    """A trailing comma on every row, or a stray extra column, must not lose the row."""
    content = "Date,Description,Amount\n08/14/2026,KROGER,-84.31,,extra\n"
    result = parse_csv(content, account_ref="acct")

    assert len(result.transactions) == 1
    assert result.warnings == []


def test_a_header_line_repeated_mid_file_is_reported_and_the_rest_survives():
    content = (
        "Date,Description,Amount\n"
        "08/14/2026,KROGER,-84.31\n"
        "Date,Description,Amount\n"
        "08/15/2026,SHELL,-41.10\n"
    )
    result = parse_csv(content, account_ref="acct")

    assert len(result.transactions) == 2
    assert len(result.warnings) == 1
    assert "line 3" in result.warnings[0]


def test_an_oversized_field_is_refused_as_unreadable_rather_than_crashing():
    """csv.Error is not a row problem: the reader cannot tell where the next row begins."""
    content = "Date,Description,Amount\n08/14/2026," + "x" * 200_000 + ",-1.00\n"
    with pytest.raises(ParserNotFoundError, match="Not readable as CSV"):
        parse_csv(content, account_ref="acct")


def test_an_unterminated_quote_is_refused_as_unreadable():
    """Python's default reader swallows everything after an open quote into one field, so one
    broken row silently ate the five real rows after it and was reported as "Empty amount". The
    reader runs strict now: a quote that never closes is a refusal with a sentence."""
    content = 'Date,Description,Amount\n08/14/2026,"KROGER,-1.00\n' + "08/15/2026,X,-2.00\n" * 5
    with pytest.raises(ParserNotFoundError, match="Not readable as CSV"):
        parse_csv(content, account_ref="acct")


def test_a_space_between_digits_is_not_a_grouping_mark():
    """'84 31' read as 8431 — the hundredfold trap the comma rule refuses, by another separator."""
    content = 'Date,Description,Amount\n08/14/2026,X,"84 31"\n08/14/2026,Y,"$ 12.00"\n'
    result = parse_csv(content, account_ref="acct")

    assert [t.description for t in result.transactions] == ["Y"]
    assert result.transactions[0].amount == Decimal("12.00")
    assert len(result.warnings) == 1 and "84 31" in result.warnings[0]


def test_nothing_on_the_wire_echoes_a_column_the_format_does_not_read():
    """`raw` used to echo every column verbatim behind a blocklist of four names, and every export
    invents its own name for the cardholder's address. It is gone; the file's own row type is the
    one thing kept, as `source_type`."""
    content = (
        "Date,Description,Amount,Cardholder Home Address,Phone\n"
        "08/14/2026,KROGER,-84.31,123 MAIN ST,555-0100\n"
    )
    result = parse_csv(content, account_ref="acct")

    on_the_wire = result.transactions[0].model_dump_json()
    assert "123 MAIN ST" not in on_the_wire
    assert "555-0100" not in on_the_wire
    assert result.transactions[0].source_type is None


@pytest.mark.parametrize("amount", ["NaN", "Infinity", "1E5", "1_000", "abc"])
def test_amounts_that_are_not_money_become_warnings_not_a_500(amount):
    content = f"Date,Description,Amount\n08/14/2026,X,{amount}\n08/15/2026,Y,-2.00\n"
    result = parse_csv(content, account_ref="acct")

    assert len(result.transactions) == 1
    assert result.transactions[0].description == "Y"
    assert len(result.warnings) == 1
    assert "line 2" in result.warnings[0]


def test_a_european_looking_amount_is_refused_rather_than_read_a_hundredfold():
    """'84,31' was 8431.00. A refusal is a warning the person sees; 8431 is a number they don't."""
    content = 'Date,Description,Amount\n08/14/2026,X,"84,31"\n'
    result = parse_csv(content, account_ref="acct")

    assert result.transactions == []
    assert len(result.warnings) == 1
    assert "84,31" in result.warnings[0]


def test_a_trailing_sign_and_cr_dr_shorthand_are_understood():
    content = (
        "Date,Description,Amount\n"
        "08/14/2026,DEBIT,84.31-\n"
        "08/14/2026,CREDIT,84.31 CR\n"
        "08/14/2026,DEBIT TOO,84.31 DR\n"
    )
    result = parse_csv(content, account_ref="acct")

    assert result.warnings == []
    assert [t.direction for t in result.transactions] == [
        TransactionDirection.DEBIT,
        TransactionDirection.CREDIT,
        TransactionDirection.DEBIT,
    ]
    assert all(t.amount == Decimal("84.31") for t in result.transactions)


def test_ambiguous_dates_are_read_month_first_and_the_file_is_flagged():
    content = "Date,Description,Amount\n03/04/2026,X,-1.00\n05/06/2026,Y,-2.00\n"
    result = parse_csv(content, account_ref="acct")

    assert [t.transaction_date for t in result.transactions] == [
        date(2026, 3, 4),
        date(2026, 5, 6),
    ]
    assert len(result.warnings) == 1
    assert "month/day" in result.warnings[0]
    assert "03/04/2026" in result.warnings[0]


def test_a_day_first_file_is_read_day_first_throughout():
    """Per-row first-fit read 25/08 as August 25 and 03/08 as March 8 in the same file."""
    content = "Date,Description,Amount\n25/08/2026,X,-1.00\n03/08/2026,Y,-2.00\n"
    result = parse_csv(content, account_ref="acct")

    assert [t.transaction_date for t in result.transactions] == [
        date(2026, 8, 25),
        date(2026, 8, 3),
    ]
    # Only one format fits every date, so nothing was guessed and nothing is flagged.
    assert result.warnings == []


def test_dates_that_read_the_same_either_way_are_not_flagged():
    content = "Date,Description,Amount\n05/05/2026,X,-1.00\n"
    result = parse_csv(content, account_ref="acct")

    assert result.transactions[0].transaction_date == date(2026, 5, 5)
    assert result.warnings == []


def test_a_broken_date_does_not_stop_the_file_level_format_being_chosen():
    content = (
        "Date,Description,Amount\n25/08/2026,X,-1.00\nnot-a-date,Y,-2.00\n03/08/2026,Z,-3.00\n"
    )
    result = parse_csv(content, account_ref="acct")

    assert [t.transaction_date for t in result.transactions] == [
        date(2026, 8, 25),
        date(2026, 8, 3),
    ]
    assert len(result.warnings) == 1
    assert "line 3" in result.warnings[0]


def test_a_windows_encoded_file_is_read_and_the_encoding_is_reported():
    content = "Date,Description,Amount\n08/14/2026,CAF\xc9 DU MONDE,-4.50\n".encode("cp1252")
    result = parse_csv(content, account_ref="acct")

    assert result.transactions[0].description == "CAFÉ DU MONDE"
    assert any("Windows-1252" in w for w in result.warnings)


def test_a_bare_account_label_in_the_preamble_is_a_name_not_a_number():
    """'Account : Cashback Free Checking' used to yield an account mask of 'king'."""
    content = (
        '"Account : Cashback Free Checking"\n'
        + CACU_HEADER
        + '"20260826",08/26/2026,"KROGER","4521",-84.31,,"1000.00",,\n'
    )
    result = parse_csv(content, account_ref="acct")

    assert result.transactions[0].account_mask is None
    assert result.transactions[0].account_key is None


def test_an_unreadable_running_balance_is_reported_rather_than_silently_dropped():
    content = (
        CACU_HEADER
        + '"20260826",08/26/2026,"KROGER","4521",-84.31,,"abc",,\n'
        + '"20260825",08/25/2026,"SHELL","4521",-41.10,,"1084.31",,\n'
    )
    result = parse_csv(content, account_ref="acct")

    assert len(result.transactions) == 2
    assert len(result.warnings) == 1
    assert "line 2" in result.warnings[0] and "balance" in result.warnings[0]
    # The newest row's own balance was unreadable, but its movement is known, so the checkpoint
    # walks the readable balance forward through it: 1084.31 - 84.31. Taking the newest *readable*
    # balance instead would have recorded a closing figure one transaction stale.
    assert result.statement is not None
    assert result.statement.closing_balance == Decimal("1000.00")
    assert result.statement.opening_balance == Decimal("1125.41")


def test_a_hundred_thousand_rows_parse_in_bounded_time_and_memory():
    lines = ["Date,Description,Amount"]
    lines += [
        f"2026-08-{(i % 28) + 1:02d},MERCHANT {i} MAIN ST,-{(i % 900) + 1}.25"
        for i in range(100_000)
    ]
    content = "\n".join(lines) + "\n"

    started = time.perf_counter()
    result = parse_csv(content, account_ref="acct")
    elapsed = time.perf_counter() - started

    assert len(result.transactions) == 100_000
    assert result.warnings == []
    assert len({t.dedupe_key for t in result.transactions}) == 100_000
    # Generous: the point is linear, not fast. A quadratic step shows up as minutes.
    assert elapsed < 60
