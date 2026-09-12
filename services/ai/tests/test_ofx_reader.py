"""OFX parsing.

OFX matters more than CSV for reconciliation: it carries the institution's own transaction ids and
a closing balance, so the ledger can be *proved* rather than assumed.
"""

from decimal import Decimal
from pathlib import Path

import pytest

from finances_ai.ingest import OfxParseError, parse_ofx
from finances_ai.models import TransactionDirection

SAMPLE = Path(__file__).parent / "sample.ofx"

# The fixture is arithmetically self-consistent on purpose: the five transactions sum to -41.32,
# which is exactly the LEDGERBAL. That makes it model a real statement, so importing it reconciles
# to zero and anyone reading the file learns the right shape. Detection of a *mismatch* is covered
# separately, by ReportingViewsTest on the Java side.


def read_sample() -> bytes:
    return SAMPLE.read_bytes()


def test_parses_transactions_with_direction_from_the_sign():
    result = parse_ofx(read_sample(), account_ref="1")

    assert result.source_format == "ofx"
    assert len(result.transactions) == 5

    purchase = result.transactions[0]
    assert purchase.description == "KROGER #4521"
    assert purchase.amount == Decimal("84.31")
    # Amount is stored as a magnitude; a negative TRNAMT becomes a debit.
    assert purchase.direction == TransactionDirection.DEBIT

    refund = result.transactions[4]
    assert refund.direction == TransactionDirection.CREDIT


def test_uses_the_institutions_own_transaction_id():
    result = parse_ofx(read_sample(), account_ref="1")

    # FITID is permanent and survives a description being cleaned up later, which a hash does not.
    assert result.transactions[0].external_id == "2026081401"
    assert all(t.external_id for t in result.transactions)


def test_extracts_the_reconciliation_checkpoint():
    result = parse_ofx(read_sample(), account_ref="1")

    assert result.statement is not None
    assert result.statement.period_start.isoformat() == "2026-08-01"
    assert result.statement.period_end.isoformat() == "2026-08-31"
    # Passed through with the institution's own sign; negative means owed.
    assert result.statement.closing_balance == Decimal("-41.32")


def test_only_xfer_is_treated_as_a_transfer():
    """PAYMENT is deliberately not included — see the note in ofx_reader.

    A false positive here suppresses a real expense from the budget silently, which is far worse
    than a card payment landing in the review queue.
    """
    result = parse_ofx(read_sample(), account_ref="1")

    flagged = [t for t in result.transactions if t.is_probable_transfer]
    assert len(flagged) == 1
    assert flagged[0].source_type == "XFER"

    # The CREDIT refund is NOT flagged: a credit is not automatically a transfer.
    refund = result.transactions[4]
    assert refund.direction == TransactionDirection.CREDIT
    assert refund.is_probable_transfer is False


def test_dedupe_keys_are_stable_across_reparsing():
    first = parse_ofx(read_sample(), account_ref="1")
    second = parse_ofx(read_sample(), account_ref="1")

    # Re-import depends on this being identical, every time.
    assert [t.dedupe_key for t in first.transactions] == [t.dedupe_key for t in second.transactions]


def test_dedupe_keys_differ_between_accounts():
    one = parse_ofx(read_sample(), account_ref="1")
    two = parse_ofx(read_sample(), account_ref="2")

    # The same statement applied to a different account must not collide with the first.
    assert one.transactions[0].dedupe_key != two.transactions[0].dedupe_key


def test_rejects_a_file_that_is_not_ofx():
    with pytest.raises(OfxParseError):
        parse_ofx(b"Date,Description,Amount\n2026-08-14,KROGER,-84.31\n", account_ref="1")


def test_rejects_an_empty_file():
    with pytest.raises(OfxParseError):
        parse_ofx(b"", account_ref="1")


def test_accepts_a_string_as_well_as_bytes():
    result = parse_ofx(SAMPLE.read_text(), account_ref="1")

    assert len(result.transactions) == 5


def test_identical_entries_differing_only_by_fitid_get_different_keys():
    """The lost-transfers bug, on the OFX path. Three identical same-day transfers were one key."""
    from finances_ai.ingest import parse_ofx

    def entry(fitid: str) -> str:
        return (
            "<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260814000000</DTPOSTED>"
            f"<TRNAMT>-1000.00</TRNAMT><FITID>{fitid}</FITID><NAME>TRANSFER TO SAVINGS</NAME></STMTTRN>"
        )

    ofx = (
        "OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nSECURITY:NONE\nENCODING:USASCII\n"
        "CHARSET:1252\nCOMPRESSION:NONE\nOLDFILEUID:NONE\nNEWFILEUID:NONE\n\n"
        "<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>"
        "<DTSERVER>20260831000000</DTSERVER><LANGUAGE>ENG</LANGUAGE></SONRS></SIGNONMSGSRSV1>"
        "<BANKMSGSRSV1><STMTTRNRS><TRNUID>1</TRNUID><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>"
        "<STMTRS><CURDEF>USD</CURDEF><BANKACCTFROM><BANKID>1</BANKID><ACCTID>XXXX1234</ACCTID>"
        "<ACCTTYPE>CHECKING</ACCTTYPE></BANKACCTFROM><BANKTRANLIST><DTSTART>20260801</DTSTART>"
        f"<DTEND>20260831</DTEND>{entry('A1')}{entry('A2')}{entry('A3')}</BANKTRANLIST>"
        "<LEDGERBAL><BALAMT>100.00</BALAMT><DTASOF>20260831</DTASOF></LEDGERBAL>"
        "</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"
    )
    result = parse_ofx(ofx.encode(), account_ref="1")

    keys = {t.dedupe_key for t in result.transactions}
    assert len(result.transactions) == 3
    assert len(keys) == 3, "identical transfers collapsed into one key"


def _bank_statement(acctid: str, accttype: str, fitid: str, amount: str, name: str) -> str:
    return (
        "<STMTTRNRS><TRNUID>1</TRNUID><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>"
        f"<STMTRS><CURDEF>USD</CURDEF><BANKACCTFROM><BANKID>1</BANKID><ACCTID>{acctid}</ACCTID>"
        f"<ACCTTYPE>{accttype}</ACCTTYPE></BANKACCTFROM><BANKTRANLIST><DTSTART>20260801</DTSTART>"
        "<DTEND>20260831</DTEND><STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260814000000</DTPOSTED>"
        f"<TRNAMT>{amount}</TRNAMT><FITID>{fitid}</FITID><NAME>{name}</NAME></STMTTRN></BANKTRANLIST>"
        "<LEDGERBAL><BALAMT>100.00</BALAMT><DTASOF>20260831</DTASOF></LEDGERBAL>"
        "</STMTRS></STMTTRNRS>"
    )


def _ofx(*statements: str) -> bytes:
    return (
        "OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nSECURITY:NONE\nENCODING:USASCII\n"
        "CHARSET:1252\nCOMPRESSION:NONE\nOLDFILEUID:NONE\nNEWFILEUID:NONE\n\n"
        "<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>"
        "<DTSERVER>20260831000000</DTSERVER><LANGUAGE>ENG</LANGUAGE></SONRS></SIGNONMSGSRSV1>"
        "<BANKMSGSRSV1>" + "".join(statements) + "</BANKMSGSRSV1></OFX>"
    ).encode()


def test_a_multi_statement_file_routes_each_row_to_its_own_account():
    """Two accounts in one file, the same FITID in each. Every row used to be filed against the
    account nominated for the upload, and the shared FITID then made one of them a "duplicate" of
    the other — a row from savings silently dropped as a copy of a row from checking."""
    result = parse_ofx(
        _ofx(
            _bank_statement("CHK1", "CHECKING", "F1", "-10.00", "GROCER"),
            _bank_statement("SAV2", "SAVINGS", "F1", "-20.00", "GROCER"),
        ),
        account_ref="1",
    )

    assert len(result.transactions) == 2
    checking, savings = result.transactions
    assert checking.account_key and savings.account_key
    assert checking.account_key != savings.account_key
    assert (checking.account_mask, savings.account_mask) == ("CHK1", "SAV2")
    assert (checking.account_name, savings.account_name) == ("Checking", "Savings")
    assert checking.dedupe_key != savings.dedupe_key
    # No single checkpoint — it would belong to one account, and the upload nominates at most
    # one — but one per statement, each carrying the key its rows carry.
    assert result.statement is None
    assert [s.account_key for s in result.statements] == [
        checking.account_key,
        savings.account_key,
    ]
    assert [s.account_mask for s in result.statements] == ["CHK1", "SAV2"]
    assert all(s.closing_balance == Decimal("100.00") for s in result.statements)
    assert any("2 accounts" in w and "account id" in w for w in result.warnings)


def test_a_single_statement_file_keeps_the_nominated_account():
    result = parse_ofx(read_sample(), account_ref="1")

    assert all(t.account_key is None for t in result.transactions)
    assert result.statement is not None
    assert result.statements == []


def test_a_parse_failure_names_the_error_class_not_the_bytes():
    """The API now shows a parser's reason to the person; a library's message can quote the file."""
    with pytest.raises(OfxParseError) as refused:
        parse_ofx(b"OFXHEADER:100\n\n<OFX><BROKEN>", account_ref="1")
    assert "Not a readable OFX file (" in str(refused.value)
    assert "BROKEN" not in str(refused.value)
