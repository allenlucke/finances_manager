"""The helpers every reader shares. One implementation, so one set of tests."""

from decimal import Decimal

import pytest

from finances_ai.ingest import csv_reader, positions_reader
from finances_ai.ingest.common import (
    account_hash,
    decode_text,
    parse_money,
    parse_optional_money,
)


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("$1,234.56", Decimal("1234.56")),
        ("+12.34", Decimal("12.34")),
        ("-4.50", Decimal("-4.50")),
        ("(100.00)", Decimal("-100.00")),
        # Trailing sign and CR/DR shorthand: both real, both used to be refused outright.
        ("84.31-", Decimal("-84.31")),
        ("84.31 CR", Decimal("84.31")),
        ("84.31 DR", Decimal("-84.31")),
        ("DR 84.31", Decimal("-84.31")),
        ("1,000", Decimal(1000)),
        (".5", Decimal("0.5")),
        ("7", Decimal(7)),
        ("0.00", Decimal("0.00")),
    ],
)
def test_parse_money_reads_the_ways_banks_write_a_figure(text, expected):
    assert parse_money(text) == expected


@pytest.mark.parametrize(
    "text",
    [
        # Decimal() accepts every one of these, and none of them is money.
        "NaN",
        "Infinity",
        "-Infinity",
        "1E5",
        "1_000",
        # Grouping that is not grouping. "84,31" is 84.31 in half the world; reading it as 8,431
        # is what stripping the comma did.
        "84,31",
        "1,2345",
        "1,00.5",
        "--",
        "",
        "   ",
        "abc",
        "$",
        "1.2.3",
    ],
)
def test_parse_money_refuses_what_is_not_money(text):
    with pytest.raises(ValueError):
        parse_money(text)


def test_a_comma_decimal_locale_is_a_format_setting_not_a_guess():
    assert parse_money("1.234,56", decimal_separator=",") == Decimal("1234.56")
    assert parse_money("84,31", decimal_separator=",") == Decimal("84.31")
    with pytest.raises(ValueError):
        parse_money("84.31", decimal_separator=",")


@pytest.mark.parametrize("text", [None, "", "  ", "--", "-", "n/a", "N/A"])
def test_parse_optional_money_reads_not_applicable_as_absent(text):
    assert parse_optional_money(text) is None


def test_parse_optional_money_still_refuses_garbage():
    """Absent and unreadable are different answers, and the caller needs to know which."""
    with pytest.raises(ValueError):
        parse_optional_money("abc")


def test_utf8_with_a_bom_decodes_without_comment():
    text, note = decode_text("﻿Date,Description,Amount\n".encode())
    assert text.startswith("Date")
    assert note is None


def test_a_windows_export_is_read_as_cp1252_and_says_so():
    text, note = decode_text("CAF\xc9 DU MONDE".encode("cp1252"))
    assert text == "CAFÉ DU MONDE"
    assert note is not None and "Windows-1252" in note


def test_every_reader_uses_the_one_account_hash():
    """Two implementations had already drifted once. There is one now, and the readers share it."""
    assert csv_reader.account_hash is account_hash
    assert positions_reader.account_hash is account_hash
    assert account_hash(" 1234567K8901 ") == account_hash("1234567K8901")
    assert len(account_hash("1234567K8901")) == 16
