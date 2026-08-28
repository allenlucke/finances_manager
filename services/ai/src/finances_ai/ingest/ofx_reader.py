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

from finances_ai.ingest.csv_reader import dedupe_key, normalize_description
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
        raise OfxParseError(f"Not a readable OFX file: {exc}") from exc

    statements = list(getattr(ofx, "statements", []) or [])
    if not statements:
        raise OfxParseError("The file contains no statements")

    transactions: list[ParsedTransaction] = []
    warnings: list[str] = []

    for statement in statements:
        for entry in getattr(statement, "transactions", []) or []:
            try:
                transactions.append(_map_transaction(entry, account_ref))
            except (ValueError, AttributeError, TypeError) as exc:
                # One bad row must not cost the other several hundred. The API surfaces the count.
                warnings.append(f"transaction {getattr(entry, 'fitid', '?')}: {exc}")

    if len(statements) > 1:
        # One file, several accounts. Every row is applied to the account the caller nominated,
        # which would be wrong — say so rather than silently mixing them.
        warnings.append(
            f"File contains {len(statements)} statements; all rows were read as one account."
        )

    return ParseResult(
        source_format="ofx",
        transactions=transactions,
        warnings=warnings,
        statement=_map_statement(statements[0]),
    )


def _map_transaction(entry, account_ref: str) -> ParsedTransaction:
    amount = Decimal(str(entry.trnamt))
    posted: date = entry.dtposted.date()
    # DTUSER is when the user made the purchase, DTPOSTED when it hit the account. Prefer the
    # former when present: it is the date on the receipt, and the one a person remembers.
    occurred: date = entry.dtuser.date() if getattr(entry, "dtuser", None) else posted

    description = (getattr(entry, "name", None) or getattr(entry, "memo", None) or "").strip()
    if not description:
        raise ValueError("Row has neither NAME nor MEMO")

    trntype = (getattr(entry, "trntype", "") or "").upper()

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
        dedupe_key=dedupe_key(account_ref, occurred, amount, description),
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
