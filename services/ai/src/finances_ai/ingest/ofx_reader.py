"""OFX/QFX statement parsing.

OFX carries three things a CSV export almost never does, and each removes a guess:

* ``FITID`` — the institution's own permanent id for a transaction. Far better than a hash of
  date/amount/description, because it stays stable when a description is later cleaned up, and it
  distinguishes two genuinely separate purchases that happen to match on every other field.
* **A statement period and closing balance**, which become a reconciliation checkpoint. This is what
  lets the ledger be *proved* against what the institution says rather than merely assumed.
* ``TRNTYPE`` — the institution's own classification of the row.

Parsing is delegated to ``ofxtools`` rather than hand-rolled. OFX is SGML-ish with a header block,
optional closing tags, and per-institution quirks; a bespoke reader is the kind of thing that works
on the sample file and fails on a real export. The trade is that ``ofxtools`` validates strictly
against the spec, so a genuinely malformed file raises rather than silently yielding partial data —
which is the right failure for money, but means the error has to be reported usefully.
"""

from __future__ import annotations

import io
from datetime import date
from decimal import Decimal

from ofxtools.Parser import OFXTree

from finances_ai.ingest.common import account_hash, dedupe_key, normalize_description
from finances_ai.models import (
    ParsedTransaction,
    ParseResult,
    StatementSummary,
    TransactionDirection,
)

# TRNTYPE values that unambiguously move money between the user's own accounts.
#
# Deliberately just XFER. It is tempting to add PAYMENT, but PAYMENT is ambiguous without knowing
# the account: on a credit card it is a card payment (a transfer), while on a checking account it
# is usually a bill payment (a real expense). The parser does not know the account type, and this
# flag suppresses spending — a false positive here makes real expenses silently vanish from the
# budget, which is far worse than a card payment landing in the review queue.
_TRANSFER_TYPES = frozenset({"XFER"})


class OfxParseError(ValueError):
    """Raised when a file is not usable OFX."""


def parse_ofx(content: str | bytes, account_ref: str = "unknown") -> ParseResult:
    """Parse an OFX/QFX export into normalized transactions.

    QFX is Quicken's flavour of OFX and parses identically; the extension differs, the grammar
    does not.
    """
    if isinstance(content, str):
        content = content.encode("utf-8")

    tree = OFXTree()
    try:
        tree.parse(io.BytesIO(content))
        ofx = tree.convert()
    except Exception as exc:  # ofxtools raises a family of parse and spec errors
        # The class name, not the message: the API now shows a parser's reason to the person, and
        # a library's parse error can quote the bytes it choked on.
        raise OfxParseError(f"Not a readable OFX file ({type(exc).__name__})") from exc

    statements = list(getattr(ofx, "statements", []) or [])
    if not statements:
        raise OfxParseError("The file contains no statements")

    transactions: list[ParsedTransaction] = []
    warnings: list[str] = []

    # One file, several accounts. Each row carries its own statement's account id, so the API
    # routes it the way it routes a brokerage history — by the stable key, through the
    # unlinked-account flow when the account is not set up yet — rather than applying every
    # statement to whichever account the caller nominated. That used to be the behaviour, and the
    # warning saying so never reached anyone. A single-statement file keeps the nominated account.
    several = len(statements) > 1
    for statement in statements:
        account = _statement_account(statement) if several else None
        if several and account is None:
            warnings.append(
                "A statement in this file names no account id; its rows were read against the "
                "account chosen for the upload."
            )
        for entry in getattr(statement, "transactions", []) or []:
            try:
                transactions.append(_map_transaction(entry, account_ref, account))
            except (ValueError, AttributeError, TypeError) as exc:
                # One bad row must not cost the other several hundred.
                warnings.append(f"transaction {getattr(entry, 'fitid', '?')}: {exc}")

    if several:
        # A checkpoint belongs to one account, and the import nominates at most one. Recording
        # the first statement's closing balance against it — the old behaviour — would have
        # reconciled one account against another's figure.
        warnings.append(
            f"This file holds statements for {len(statements)} accounts. Each row was matched to "
            "its own account by the file's account id. No closing balance was recorded: import "
            "each account's own statement to reconcile it."
        )

    return ParseResult(
        source_format="ofx",
        transactions=transactions,
        warnings=warnings,
        statement=None if several else _map_statement(statements[0]),
    )


def _statement_account(statement) -> tuple[str, str, str | None] | None:
    """(mask, key, name) for the account a statement is for, or None when it does not say.

    The full account id is used to derive the key and then dropped — never returned
    (docs/SECURITY.md). OFX carries no account name; the account type is the nearest thing.
    """
    account = getattr(statement, "account", None)
    acctid = str(getattr(account, "acctid", "") or "").strip()
    if not acctid:
        return None
    accttype = getattr(account, "accttype", None)
    name = str(accttype).title() if accttype else None
    if name is None and type(statement).__name__.startswith("CC"):
        name = "Credit card"
    return acctid[-4:], account_hash(acctid), name


def _map_transaction(
    entry, account_ref: str, account: tuple[str, str, str | None] | None = None
) -> ParsedTransaction:
    amount = Decimal(str(entry.trnamt))
    posted: date = entry.dtposted.date()
    # DTUSER is when the user made the purchase, DTPOSTED when it hit the account. Prefer the
    # former when present: it is the date on the receipt, and the one a person remembers.
    occurred: date = entry.dtuser.date() if getattr(entry, "dtuser", None) else posted

    description = (getattr(entry, "name", None) or getattr(entry, "memo", None) or "").strip()
    if not description:
        raise ValueError("Row has neither NAME nor MEMO")

    trntype = (getattr(entry, "trntype", "") or "").upper()
    account_mask, account_key, account_name = account or (None, None, None)

    return ParsedTransaction(
        transaction_date=occurred,
        posted_date=posted,
        description=description,
        merchant=normalize_description(description),
        amount=abs(amount),
        # Negative TRNAMT is money leaving the account, for every account type.
        direction=TransactionDirection.DEBIT if amount < 0 else TransactionDirection.CREDIT,
        # The institution's own permanent id. The API prefers this over the hash when present.
        external_id=str(entry.fitid) if getattr(entry, "fitid", None) else None,
        # FITID is the identity when present. This call omitted it, which re-opened the exact
        # bug csv_reader.dedupe_key documents at length — identical same-day transfers collapsing
        # into one. (The API now computes its own key and reads external_id directly, so this is
        # belt and braces; it keeps the parser's key honest for anyone else reading it.)
        dedupe_key=dedupe_key(
            account_key or account_ref,
            occurred,
            amount,
            description,
            str(entry.fitid) if getattr(entry, "fitid", None) else None,
        ),
        account_mask=account_mask,
        account_key=account_key,
        account_name=account_name,
        is_probable_transfer=trntype in _TRANSFER_TYPES,
        raw={
            "trntype": trntype,
            "fitid": str(getattr(entry, "fitid", "") or ""),
            "trnamt": str(amount),
            "dtposted": posted.isoformat(),
        },
    )


def _map_statement(statement) -> StatementSummary | None:
    """The reconciliation checkpoint, when the file carries one."""
    balance = getattr(statement, "balance", None)
    closing = None
    if balance is not None and getattr(balance, "balamt", None) is not None:
        # Passed through with the institution's own sign. OFX reports a credit-card balance as
        # negative when owed, which matches this project's convention — but issuers vary, and the
        # reconciliation view exists precisely to surface a disagreement rather than hide it.
        closing = Decimal(str(balance.balamt))

    start = statement.dtstart.date() if getattr(statement, "dtstart", None) else None
    end = statement.dtend.date() if getattr(statement, "dtend", None) else None
    if end is None and balance is not None and getattr(balance, "dtasof", None):
        end = balance.dtasof.date()

    if closing is None and start is None and end is None:
        return None

    return StatementSummary(period_start=start, period_end=end, closing_balance=closing)
