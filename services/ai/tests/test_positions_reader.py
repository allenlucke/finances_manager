"""Brokerage positions parsing.

The fixture is synthetic but reproduces every quirk found in a real Fidelity export: the BOM, the
trailing comma, footnote markers on tickers, cash rows with no quantity, ``--`` for not-applicable,
several accounts in one file, and the legal prose after the data. Real figures are never committed.
"""

from decimal import Decimal
from pathlib import Path

import pytest

from finances_ai.ingest import PositionsParseError, parse_positions

SAMPLE = Path(__file__).parent / "positions.csv"


def read_sample() -> bytes:
    return SAMPLE.read_bytes()


def test_reads_every_holding_and_skips_the_legal_footer():
    result = parse_positions(read_sample())

    # Five holdings; the disclaimer paragraphs are prose, not rows.
    assert len(result.positions) == 5
    assert result.warnings == []


def test_strips_the_byte_order_mark_from_the_first_column():
    # Without utf-8-sig the first column is named "﻿Account number" and every row loses its
    # account, which surfaces much later as rows that cannot be matched to anything.
    result = parse_positions(read_sample())

    assert all(position.account_mask for position in result.positions)


def test_captures_the_download_date_from_the_footer():
    result = parse_positions(read_sample())

    # The only "as of" the file provides — and it lives in the prose that gets skipped.
    assert result.as_of is not None
    assert result.as_of.isoformat() == "2026-08-27"


def test_strips_footnote_markers_from_tickers():
    result = parse_positions(read_sample())
    symbols = {position.symbol for position in result.positions}

    # SPAXX** and USD*** in the file; the asterisks are footnotes, not part of the ticker.
    assert "SPAXX" in symbols
    assert "USD" in symbols
    assert not any("*" in symbol for symbol in symbols)


def test_recognises_cash_by_symbol_not_by_the_type_column():
    """The Type column is the account's registration, not the row's nature.

    Reading it as "this row is cash" marks ordinary equity holdings as cash — AAPL below sits in a
    Cash-registered account. Found by running a real export, not by reading the header.
    """
    result = parse_positions(read_sample())
    by_symbol = {position.symbol: position for position in result.positions}

    assert by_symbol["SPAXX"].is_cash is True
    assert by_symbol["USD"].is_cash is True
    assert by_symbol["AAPL"].is_cash is False
    assert by_symbol["AAPL"].account_registration == "Cash"


def test_cash_rows_have_a_value_but_no_quantity_or_cost_basis():
    result = parse_positions(read_sample())
    cash = next(p for p in result.positions if p.symbol == "SPAXX")

    assert cash.current_value == Decimal("900.00")
    assert cash.quantity is None
    assert cash.cost_basis_total is None


def test_parses_human_formatted_money_and_keeps_it_exact():
    result = parse_positions(read_sample())
    apple = next(p for p in result.positions if p.symbol == "AAPL")

    assert apple.quantity == Decimal("10.000")
    assert apple.last_price == Decimal("220.00")
    assert apple.current_value == Decimal("2200.00")
    assert apple.cost_basis_total == Decimal("1800.00")
    # Decimal throughout: these feed cost basis and net worth, so never float.
    assert isinstance(apple.current_value, Decimal)


def test_keeps_the_sign_on_a_loss():
    result = parse_positions(read_sample())
    nvidia = next(p for p in result.positions if p.symbol == "NVDA")

    # "-$100.00" must not become +100. A loss read as a gain is a wrong number on a real screen.
    assert nvidia.total_gain_loss == Decimal("-100.00")


def test_double_dash_means_not_applicable_rather_than_zero():
    result = parse_positions(read_sample())
    dollars = next(p for p in result.positions if p.symbol == "USD")

    # The file writes "--" for percent-of-account on this row. Zero would be a claim; None is not.
    assert dollars.cost_basis_total is None


def test_separates_the_accounts_in_a_multi_account_file():
    result = parse_positions(read_sample())

    # A positions export covers the whole portfolio, so the importer cannot assume one account.
    assert len({position.account_key for position in result.positions}) == 3


def test_never_returns_the_full_account_number():
    result = parse_positions(read_sample())

    # docs/SECURITY.md: masked unless there is a concrete reason. The key is a one-way hash, so a
    # row can be matched to the same account on re-import without the number being handled.
    for position in result.positions:
        assert len(position.account_mask) == 4
        assert "Z11111111" not in position.account_key
        assert "9900112233" not in position.account_key


def test_the_raw_passthrough_does_not_smuggle_the_account_number():
    """`raw` is a convenience copy of the source row and quietly defeated the masking.

    The masked field and the hashed key are useless if the full number rides along beside them in
    every response and stored payload.
    """
    result = parse_positions(read_sample())

    for position in result.positions:
        assert "Account number" not in position.raw
        assert "Z11111111" not in str(position.raw)
        assert "9900112233" not in str(position.raw)


def test_the_account_key_is_stable_across_parses():
    first = parse_positions(read_sample())
    second = parse_positions(read_sample())

    assert [p.account_key for p in first.positions] == [p.account_key for p in second.positions]


def test_rejects_a_transaction_export_rather_than_half_reading_it():
    transactions = "Transaction Date,Post Date,Description,Type,Amount\n08/14/2026,08/15/2026,KROGER,Sale,-84.31\n"

    # The two Fidelity exports are both CSV. Failing clearly beats importing holdings as nothing.
    with pytest.raises(PositionsParseError, match="transaction history"):
        parse_positions(transactions)


def test_rejects_an_empty_file():
    with pytest.raises(PositionsParseError):
        parse_positions(b"")


def test_an_oversized_field_mid_file_is_a_refusal_not_a_crash():
    """csv.Error is raised lazily from inside the row loop; the guard used to cover the header only,
    so a broken row mid-file was a 500 while the same file in the statement reader was a 422."""
    import pytest

    from finances_ai.ingest import PositionsParseError, parse_positions

    content = (
        "Account number,Account name,Symbol,Description,Quantity,Last price,Current value\n"
        "Z11111111,Brokerage,AAPL,APPLE INC,10,220.00,$2200.00\n"
        "Z11111111,Brokerage," + "x" * 200_000 + ",BROKEN,1,1.00,$1.00\n"
    )
    with pytest.raises(PositionsParseError, match="Not readable as CSV"):
        parse_positions(content)
