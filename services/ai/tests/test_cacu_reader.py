"""Community America (CACU) checking exports.

The fixture is synthetic but reproduces the shape of a real export: metadata lines above the
header, a trailing-space column name, split debit/credit columns, a running balance, and — the one
that matters most — rows in **newest-first** order.
"""

from decimal import Decimal
from pathlib import Path

from finances_ai.ingest import parse_csv
from finances_ai.models import TransactionDirection

SAMPLE = Path(__file__).parent / "cacu.csv"


def read_sample() -> bytes:
    return SAMPLE.read_bytes()


def test_finds_the_header_beneath_the_metadata_preamble():
    """The file opens with three quoted metadata lines before the real header.

    A reader that assumes row one is the header sees a single nonsense column and rejects the whole
    file.
    """
    result = parse_csv(read_sample(), account_ref="cacu")

    assert result.source_format == "cacu"
    assert len(result.transactions) == 5
    assert result.warnings == []


def test_reads_the_split_debit_and_credit_columns():
    result = parse_csv(read_sample(), account_ref="cacu")
    by_id = {t.external_id: t for t in result.transactions}

    withdrawal = by_id["20260826"]
    assert withdrawal.direction == TransactionDirection.DEBIT
    assert withdrawal.amount == Decimal("84.31")

    deposit = by_id["20260815"]
    assert deposit.direction == TransactionDirection.CREDIT
    assert deposit.amount == Decimal("2000.00")


def test_a_debit_already_written_negative_is_not_negated_twice():
    """This export writes debits as "-84.31" already.

    Negating unconditionally would turn every withdrawal into a deposit — the balance would drift
    the wrong way and every spending total would be wrong.
    """
    result = parse_csv(read_sample(), account_ref="cacu")
    withdrawal = next(t for t in result.transactions if t.external_id == "20260826")

    assert withdrawal.amount > 0
    assert withdrawal.direction == TransactionDirection.DEBIT


def test_uses_the_banks_own_transaction_number():
    result = parse_csv(read_sample(), account_ref="cacu")

    # Survives a description being reworded, which a hash of the description does not.
    assert all(t.external_id for t in result.transactions)


def test_identical_transactions_on_the_same_day_stay_separate():
    """Found on a real export, where it silently lost $2,100.

    Three $1,000 transfers to the same payee on the same day match on every visible field. Only the
    bank's Transaction Number tells them apart, and hashing date/amount/description collapsed them
    into one — money vanishing from a ledger with nothing to indicate anything had happened.
    """
    content = SAMPLE.read_text().replace(
        '"20260826",08/26/2026,"Point Of Sale Withdrawal KROGER","4521 ANYTOWN KSUS",-84.31,,"1000.00",,',
        '"20260826",08/26/2026,"Transfer To Brokerage","MONEYLINE",-1000.00,,"1000.00",,\n'
        '"20260825",08/26/2026,"Transfer To Brokerage","MONEYLINE",-1000.00,,"2000.00",,\n'
        '"20260824",08/26/2026,"Transfer To Brokerage","MONEYLINE",-1000.00,,"3000.00",,',
    )

    result = parse_csv(content, account_ref="cacu")
    keys = [t.dedupe_key for t in result.transactions]

    assert len(result.transactions) == 7
    assert len(set(keys)) == 7


def test_the_dedupe_key_is_the_banks_id_when_there_is_one():
    result = parse_csv(read_sample(), account_ref="cacu")

    # Stable across exports and immune to a description being reworded later.
    first, second = result.transactions[0], result.transactions[1]
    assert first.dedupe_key != second.dedupe_key
    assert all(t.external_id for t in result.transactions)


def test_takes_the_statement_period_from_the_preamble():
    result = parse_csv(read_sample(), account_ref="cacu")

    assert result.statement is not None
    assert result.statement.period_start.isoformat() == "2026-08-01"
    assert result.statement.period_end.isoformat() == "2026-08-27"


def test_closing_balance_comes_from_the_newest_row_not_the_last_row():
    """The single most important assertion in this file.

    The export is newest-first, so the *last* row carries the *oldest* balance. Reading it as the
    closing balance produces a plausible-looking wrong number, and reconciliation then accuses a
    correct ledger of being out by the whole period's movement.
    """
    result = parse_csv(read_sample(), account_ref="cacu")

    # 1000.00 sits beside the 08/26 row (newest); -369.67 beside 08/01 (oldest).
    assert result.statement.closing_balance == Decimal("1000.00")


def test_an_oldest_first_export_is_handled_too():
    """The same logic must not simply flip to "always take the first row"."""
    lines = SAMPLE.read_text().splitlines()
    header, rows = lines[:4], lines[4:]
    reversed_file = "\n".join(header + list(reversed(rows))) + "\n"

    result = parse_csv(reversed_file, account_ref="cacu")

    assert result.statement.closing_balance == Decimal("1000.00")


def test_falls_back_to_the_memo_when_a_description_is_missing():
    content = SAMPLE.read_text().replace('"Point Of Sale Withdrawal KROGER"', '""')

    result = parse_csv(content, account_ref="cacu")

    # An empty description would make the row unidentifiable in the review queue.
    assert any("4521 ANYTOWN" in t.description for t in result.transactions)


def test_trailing_space_in_a_column_name_does_not_break_lookup():
    # The real export ships a "Fees  " column. Header cells are stripped before use.
    result = parse_csv(read_sample(), account_ref="cacu")

    assert len(result.transactions) == 5


_CACU_PREAMBLE = (
    '"Account Name : Cashback Free Checking"\n'
    '"Account Number : 1234567K8901"\n'
    '"Date Range : 08/01/2026-08/27/2026"\n'
    "Transaction Number,Date,Description,Memo,Amount Debit,Amount Credit,Balance,Check Number,Fees  \n"
)
_NEWEST = '"20260826",08/26/2026,"Point Of Sale Withdrawal KROGER","ANYTOWN",-84.31,,"1000.00",,\n'
_MIDDLE = (
    '"20260815",08/15/2026,"Descriptive Deposit Payroll","Direct Deposit",,2000.00,"1084.31",,\n'
)
_OLDEST = '"20260801",08/01/2026,"Point Of Sale Deposit WAL-MART","ANYTOWN",,12.00,"-915.69",,\n'


def _summary(order: str):
    rows = {
        "newest_first": [_NEWEST, _MIDDLE, _OLDEST],
        "oldest_first": [_OLDEST, _MIDDLE, _NEWEST],
        "shuffled": [_MIDDLE, _NEWEST, _OLDEST],
    }[order]
    return parse_csv(_CACU_PREAMBLE + "".join(rows), account_ref="cacu").statement


def test_closing_balance_is_the_newest_row_whatever_the_file_order():
    """The old heuristic compared the first and last dates and chose wrong for anything else."""
    for order in ("newest_first", "oldest_first", "shuffled"):
        assert _summary(order).closing_balance == Decimal("1000.00"), order


def test_opening_balance_is_derived_from_the_oldest_row():
    """Oldest row: balance -915.69 after a 12.00 credit, so the period started at -927.69.

    This is what lets reconciliation work for an account whose history was not imported from the
    day it opened — every account, the first time.
    """
    for order in ("newest_first", "oldest_first", "shuffled"):
        assert _summary(order).opening_balance == Decimal("-927.69"), order


def test_a_same_day_export_is_read_the_right_way_round():
    """Every row on one date. The old heuristic compared the first and last dates, found them
    equal, and read a newest-first file as oldest-first: the closing balance came back as the
    oldest balance and the opening as the same figure, with no warning — and a statement is unique
    per period with nothing to delete it, so the wrong checkpoint was permanent.

    The balances settle it: only one ordering makes every running balance agree.
    """
    newest_first = (
        _CACU_PREAMBLE
        + '"2",08/26/2026,"KROGER","",-84.31,,"1000.00",,\n'
        + '"1",08/26/2026,"PAYROLL","",,2000.00,"1084.31",,\n'
    )
    oldest_first = (
        _CACU_PREAMBLE
        + '"1",08/26/2026,"PAYROLL","",,2000.00,"1084.31",,\n'
        + '"2",08/26/2026,"KROGER","",-84.31,,"1000.00",,\n'
    )
    for content in (newest_first, oldest_first):
        result = parse_csv(content, account_ref="cacu")
        assert result.warnings == []
        assert result.statement.closing_balance == Decimal("1000.00")
        assert result.statement.opening_balance == Decimal("-915.69")


def test_a_blank_balance_on_the_oldest_row_is_walked_through_not_skipped():
    """The opening balance precedes the oldest *transaction*, whether or not that row's balance
    cell was filled in. Skipping the row put the opening one movement too late, silently."""
    content = (
        _CACU_PREAMBLE
        + _NEWEST
        + _MIDDLE
        + '"20260801",08/01/2026,"Point Of Sale Deposit WAL-MART","ANYTOWN",,12.00,"",,\n'
    )
    result = parse_csv(content, account_ref="cacu")

    assert result.warnings == []
    assert result.statement.closing_balance == Decimal("1000.00")
    assert result.statement.opening_balance == Decimal("-927.69")


def test_balances_that_do_not_add_up_on_a_same_day_file_record_no_checkpoint_and_say_so():
    """Two rows, one date, the movements do not connect the balances in either direction, so
    nothing can say which is newer. A checkpoint that might be wrong accuses a correct ledger."""
    content = (
        _CACU_PREAMBLE
        + '"2",08/26/2026,"KROGER","",-84.31,,"1000.00",,\n'
        + '"1",08/26/2026,"PAYROLL","",,2000.00,"5.00",,\n'
    )
    result = parse_csv(content, account_ref="cacu")

    assert len(result.transactions) == 2
    assert result.statement.closing_balance is None
    assert result.statement.opening_balance is None
    assert len(result.warnings) == 1
    assert "do not add up" in result.warnings[0]
    assert "closing or opening" in result.warnings[0]
