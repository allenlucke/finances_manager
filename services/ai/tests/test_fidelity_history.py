"""Fidelity "Accounts History" — a brokerage transaction export.

The single most important property of this file: **it contains no spending**. Every row is cash
moving in or out of the brokerage, or an investment action. Importing it as ordinary expenses would
inflate a budget by the entire amount being invested — in one real month that was over $28,000.

The fixture is synthetic but reproduces the real shape: a BOM, two blank lines above the header,
several accounts in one file, free-text actions, and legal prose after the data.
"""

from decimal import Decimal
from pathlib import Path

from finances_ai.categorize import categorize
from finances_ai.ingest import parse_csv
from finances_ai.models import TransactionDirection

SAMPLE = Path(__file__).parent / "fidelity_history.csv"


def read_sample() -> bytes:
    return SAMPLE.read_bytes()


def test_finds_the_header_below_the_bom_and_blank_lines():
    result = parse_csv(read_sample(), account_ref="fidelity")

    assert result.source_format == "fidelity_history"
    assert len(result.transactions) == 5
    # The legal prose and the "Date downloaded" line are not rows, and must not become warnings.
    assert result.warnings == []


def test_uses_the_action_column_for_the_description():
    """The Description column reads "No Description" on every cash movement.

    Taking it literally would leave the ledger full of rows saying nothing.
    """
    result = parse_csv(read_sample(), account_ref="fidelity")

    assert all("No Description" not in t.description for t in result.transactions)
    assert any("Electronic Funds Transfer" in t.description for t in result.transactions)


def test_every_row_carries_its_own_account():
    """A brokerage history spans accounts, so the importer cannot be told one up front.

    Guessing files a child's UTMA activity against a joint account.
    """
    result = parse_csv(read_sample(), account_ref="fidelity")

    assert all(t.account_key for t in result.transactions)
    assert len({t.account_key for t in result.transactions}) == 3


def test_never_exposes_the_full_account_number():
    result = parse_csv(read_sample(), account_ref="fidelity")

    for transaction in result.transactions:
        assert len(transaction.account_mask) == 4
        assert "Z11111111" not in (transaction.account_key or "")


def test_identical_activity_in_two_accounts_does_not_collide():
    """Dedupe keys are scoped to the row's own account.

    Two children receiving the same amount on the same day is ordinary, and must not merge into one
    transaction.
    """
    content = SAMPLE.read_text().replace(
        '08/05/2026,Minor Two (Uniform Transfers to Minors),Z33333333,Electronic Funds Transfer Received (Cash),"",No Description,Cash,,"",USD,"",,,"","","",50,""',
        '08/24/2026,Minor Two (Uniform Transfers to Minors),Z33333333,Electronic Funds Transfer Received (Cash),"",No Description,Cash,,"",USD,"",,,"","","",1000,""',
    )
    result = parse_csv(content, account_ref="fidelity")

    keys = [t.dedupe_key for t in result.transactions]
    assert len(keys) == len(set(keys))


def test_signs_follow_the_amount_column():
    result = parse_csv(read_sample(), account_ref="fidelity")

    received = next(t for t in result.transactions if "Transfer Received" in t.description)
    assert received.direction == TransactionDirection.CREDIT
    assert received.amount == Decimal(1000)

    wire_out = next(t for t in result.transactions if "WIRE TRANSFER TO BANK" in t.description)
    assert wire_out.direction == TransactionDirection.DEBIT
    assert wire_out.amount == Decimal(500)


def test_nothing_in_a_brokerage_history_counts_as_spending():
    """The property that matters most, asserted over the whole file.

    Cash in, cash out, buys and exchanges are all movements or investments — none is a purchase of
    goods or services. A single row leaking through as an expense distorts the budget by its full
    value, and brokerage amounts are large.
    """
    result = parse_csv(read_sample(), account_ref="fidelity")
    suggestions = categorize(result.transactions)

    assert all(s.is_transfer for s in suggestions)
    assert all(s.category is None for s in suggestions)


def test_a_purchase_is_recognised_as_an_investment_not_an_expense():
    result = parse_csv(read_sample(), account_ref="fidelity")
    bought = next(t for t in result.transactions if t.description.startswith("YOU BOUGHT"))

    # Buying a fund moves cash into a holding within the same account. Not spending.
    assert bought.is_probable_transfer is True
    assert categorize([bought])[0].is_transfer is True
