"""CSV statement parsing.

Descended from ``legacy/app/serverApp/djangofinancesserver/.../reader_chase.py``, which had the
right instinct and the wrong details. Changes worth knowing about:

* The legacy version treated Chase's ``Type == 'Payment'`` as a credit-card payment. In Chase's
  card export, ``Payment`` is genuinely a payment to the card *and* refunds show up as ``Return``,
  both with positive amounts. Both are non-expenses and both must be flagged, or the budget gets
  double-charged (see docs/DOMAIN.md).
* Amounts stay ``Decimal`` and keep their direction rather than being blindly ``abs()``-ed.
* Every row gets a ``dedupe_key`` so re-importing an overlapping statement is a no-op.

Adding a bank means adding one entry to ``_FORMATS``. Do not add branching to the generic parser.

What a row is, and what happens to one that is not
--------------------------------------------------
Real exports carry lines that are not transactions: a metadata preamble, footer prose, a "Date
downloaded" stamp. They used to be recognised by width — anything narrower than half the header
was dropped, silently, and so was a three-field rent line in a nine-column export. Now a row is
judged by whether it carries a date:

* a row with a date in the date column is **always attempted**, however narrow, and a failure is
  reported as a warning with its line number;
* a row that is narrower than the header *and* has no date is footer prose and is skipped;
* a full-width row with an unreadable date is a broken row, and is reported.
"""

from __future__ import annotations

import csv
import io
import re
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, InvalidOperation

from finances_ai.ingest.common import (
    account_hash,
    decode_text,
    dedupe_key,
    normalize_description,
    parse_money,
    parse_optional_money,
)
from finances_ai.models import (
    ParsedTransaction,
    ParseResult,
    StatementSummary,
    TransactionDirection,
)

# Re-exported: the helpers moved to ``common`` and callers still import them from here.
__all__ = [
    "CsvFormat",
    "ParserNotFoundError",
    "account_hash",
    "dedupe_key",
    "find_header",
    "normalize_description",
    "parse_csv",
    "registered_formats",
]

# Never echoed back in `raw`. docs/SECURITY.md: account numbers are stored masked. The positions
# parser has had this filter since its first real file; this one did not, and the Fidelity history
# format maps an "Account Number" column — so every row carried the full number in `raw` right
# beside the mask that was hiding it. Java does not persist `raw`, which limited the exposure to
# the internal hop and a DEBUG log line; it was still the exact thing the policy forbids.
_REDACTED_COLUMNS = frozenset({"Account Number", "Account number", "Account No", "Account No."})

# Preamble labels that carry the account NUMBER. Deliberately not a bare "Account": one export
# writes the account's *name* under that label, and taking the last four characters of "Cashback
# Free Checking" produced a mask of "king".
_PREAMBLE_NUMBER_LABELS = ("Account Number", "Account number", "Account No", "Account No.")
_PREAMBLE_NAME_LABELS = ("Account Name", "Account name")

# How far to look for a header before giving up. Metadata preambles are a few lines, never dozens.
_MAX_PREAMBLE_ROWS = 12

_DATE_FORMAT_NAMES = {
    "%m/%d/%Y": "month/day/year",
    "%d/%m/%Y": "day/month/year",
    "%Y-%m-%d": "year-month-day",
}


class ParserNotFoundError(ValueError):
    """Raised when no registered format matches the uploaded file's header row."""


@dataclass(frozen=True)
class CsvFormat:
    name: str
    required_columns: frozenset[str]
    date_column: str
    description_column: str
    # Tried in order. When more than one fits every date in a file and they disagree about what
    # those dates are, the first wins and the file gets a warning saying so — see
    # _resolve_date_formats.
    date_formats: tuple[str, ...]
    # One signed amount column, OR a separate debit/credit pair — banks do both. Exactly one of
    # these arrangements must be configured.
    amount_column: str | None = None
    debit_column: str | None = None
    credit_column: str | None = None
    posted_date_column: str | None = None
    memo_column: str | None = None
    # The institution's own transaction id, when the export carries one. Better than a hash: it
    # survives a description being reworded.
    external_id_column: str | None = None
    # A running balance. The last row's value is the closing balance, which turns the file into a
    # reconciliation checkpoint.
    balance_column: str | None = None
    # Exports covering several accounts carry the account on each row.
    account_number_column: str | None = None
    type_column: str | None = None
    # Row types that mean money moved between the user's own accounts — a card payment.
    transfer_types: frozenset[str] = frozenset()
    # Row types that mean a purchase was reversed. Not a transfer: it must stay categorizable.
    refund_types: frozenset[str] = frozenset()
    # For exports whose type field is free text rather than a short enum — a brokerage writes
    # "YOU BOUGHT ... (Cash)", not "Buy" — matched case-insensitively against the type column.
    non_expense_patterns: tuple[str, ...] = ()
    # Some exports put outflows as negative, some as positive in a separate column.
    negative_is_debit: bool = True
    # "." for banks that write 1,234.56 and "," for banks that write 1.234,56. Every registered
    # format is American so far; the field exists so the next one is a one-line change rather than
    # a hundredfold error in every amount.
    decimal_separator: str = "."


_FORMATS: tuple[CsvFormat, ...] = (
    CsvFormat(
        name="chase_card",
        required_columns=frozenset({"Transaction Date", "Description", "Amount"}),
        date_column="Transaction Date",
        posted_date_column="Post Date",
        description_column="Description",
        amount_column="Amount",
        date_formats=("%m/%d/%Y", "%Y-%m-%d"),
        type_column="Type",
        transfer_types=frozenset({"Payment"}),
        refund_types=frozenset({"Return", "Adjustment"}),
    ),
    CsvFormat(
        # Community America Credit Union. Two things make this shape distinct: the amount is split
        # across separate debit and credit columns rather than one signed field, and the file opens
        # with metadata lines before the header row.
        name="cacu",
        required_columns=frozenset({"Date", "Description", "Amount Debit", "Amount Credit"}),
        date_column="Date",
        description_column="Description",
        debit_column="Amount Debit",
        credit_column="Amount Credit",
        memo_column="Memo",
        external_id_column="Transaction Number",
        balance_column="Balance",
        date_formats=("%m/%d/%Y", "%Y-%m-%d"),
    ),
    CsvFormat(
        # Fidelity "Accounts History". Worth knowing before reading further: a brokerage history
        # contains no spending. Every row is cash moving in or out, or an investment action — so
        # essentially all of it is non-expense, and importing it naively would inflate the budget
        # by the entire amount being invested.
        name="fidelity_history",
        required_columns=frozenset({"Run Date", "Action", "Amount", "Account Number"}),
        date_column="Run Date",
        # "Action" carries the real text. The "Description" column reads "No Description" on every
        # cash movement, which is why it is not used here.
        description_column="Action",
        amount_column="Amount",
        posted_date_column="Settlement Date",
        account_number_column="Account Number",
        type_column="Action",
        non_expense_patterns=(
            r"ELECTRONIC\s+FUNDS\s+TRANSFER",
            r"WIRE\s+TRANSFER",
            r"\bTRANSFER(RED)?\b",
            r"^YOU\s+(BOUGHT|SOLD)",
            r"\bEXCHANGE",
            r"\bREINVEST",
            r"\bJOURNAL",
        ),
        date_formats=("%m/%d/%Y", "%Y-%m-%d"),
    ),
    CsvFormat(
        name="generic",
        required_columns=frozenset({"Date", "Description", "Amount"}),
        date_column="Date",
        description_column="Description",
        amount_column="Amount",
        date_formats=("%m/%d/%Y", "%Y-%m-%d", "%d/%m/%Y"),
    ),
)


def registered_formats() -> list[str]:
    return [fmt.name for fmt in _FORMATS]


def find_header(rows: list[list[str]]) -> tuple[int, CsvFormat, dict[str, str]]:
    """Locate the header row, skipping any metadata the bank puts above it.

    Several exports open with lines like ``"Account Name : Cashback Free Checking"`` before the
    real header. A reader that assumes row one is the header sees a single nonsense column and
    rejects the whole file — so the header is searched for, and the lines above it are kept: they
    carry the account and the statement date range, which nothing else in the file does.
    """
    preamble: dict[str, str] = {}

    for index, row in enumerate(rows[:_MAX_PREAMBLE_ROWS]):
        present = {cell.strip() for cell in row if cell and cell.strip()}
        for fmt in _FORMATS:
            if fmt.required_columns <= present:
                return index, fmt, preamble

        # Not a header. Keep anything shaped like "Key : Value" for the statement summary.
        for cell in row:
            if cell and " : " in cell:
                key, _, value = cell.partition(" : ")
                preamble[key.strip().strip('"')] = value.strip().strip('"')

    raise ParserNotFoundError(
        "No parser matches this file. Known formats: " + ", ".join(registered_formats())
    )


def parse_csv(content: str | bytes, account_ref: str = "unknown") -> ParseResult:
    """Parse a CSV statement export into normalized transactions."""
    warnings: list[str] = []
    if isinstance(content, bytes):
        content, encoding_note = decode_text(content)
        if encoding_note:
            warnings.append(encoding_note)

    try:
        rows = list(csv.reader(io.StringIO(content)))
    except csv.Error as exc:
        # A field longer than the reader's limit, or a quote that never closes. Not a row-level
        # problem: the reader cannot say where the next row begins, so nothing after it is safe.
        raise ParserNotFoundError(f"Not readable as CSV: {exc}") from exc
    if not rows:
        raise ParserNotFoundError("File is empty")

    header_index, fmt, preamble = find_header(rows)

    # Some exports name their account once, in the metadata above the header, rather than on every
    # row. Read it: it is what lets the importing side say "this file is your Cashback Free
    # Checking ending 8901" instead of asking a person to know that and type it in.
    file_account = _account_from_preamble(preamble)
    # Header cells are stripped: at least one bank ships a trailing-space column name ("Fees  "),
    # which otherwise never matches anything that looks it up.
    header = [cell.strip() for cell in rows[header_index]]
    date_index = header.index(fmt.date_column)

    body = [
        (header_index + offset + 2, values)
        for offset, values in enumerate(rows[header_index + 1 :])
        if any(cell.strip() for cell in values)
    ]

    # Decided once for the file, not once per row: a row-by-row first-fit reads 03/04 as March 4
    # and 25/04 as April 25 in the same file without noticing that it just changed its mind.
    date_formats, date_note = _resolve_date_formats(
        [values[date_index] for _, values in body if len(values) > date_index],
        fmt.date_formats,
    )
    if date_note:
        warnings.append(date_note)

    transactions: list[ParsedTransaction] = []
    # (date, running balance after this row, this row's signed movement). The date because which
    # end of the file holds the closing balance depends on the export's ordering; the movement so
    # the opening balance can be derived — see _statement_summary.
    balances: list[tuple[date, Decimal, Decimal]] = []

    for line_number, values in body:
        row = dict(zip(header, values, strict=False))
        if (
            len(values) < len(header)
            and _try_any(row.get(fmt.date_column, ""), fmt.date_formats) is None
        ):
            # Narrower than the header and no date: footer prose, a download stamp, a note.
            continue

        try:
            transactions.append(_map_row(row, fmt, account_ref, file_account, date_formats))
        except (ValueError, KeyError, InvalidOperation) as exc:
            # ValueError covers our own messages and pydantic's; KeyError a column the row lacks
            # that the mapper did not think to .get(); InvalidOperation a Decimal that slipped past
            # the money parser. One bad row costs one warning, never the file.
            warnings.append(f"line {line_number}: {exc}")
            continue

        if fmt.balance_column:
            try:
                running = parse_optional_money(
                    row.get(fmt.balance_column), decimal_separator=fmt.decimal_separator
                )
            except ValueError as exc:
                # The transaction stands; only the checkpoint loses a data point. Said out loud
                # because a reconciliation that quietly ignores a row is a reconciliation that
                # cannot be trusted.
                warnings.append(f"line {line_number}: balance ignored: {exc}")
                running = None
            if running is not None:
                latest = transactions[-1]
                signed = (
                    latest.amount
                    if latest.direction == TransactionDirection.CREDIT
                    else -latest.amount
                )
                balances.append((latest.transaction_date, running, signed))

    return ParseResult(
        source_format=fmt.name,
        transactions=transactions,
        warnings=warnings,
        statement=_statement_summary(preamble, transactions, balances),
    )


def _resolve_date_formats(
    values: list[str], formats: tuple[str, ...]
) -> tuple[tuple[str, ...], str | None]:
    """Pick the one date format that fits the whole file, and say when that choice was a guess.

    ``03/04/2026`` is March 4th in one country and April 3rd in another, and a format list that
    offers both — ``generic`` does — used to pick per row, first fit wins, with no warning. Now the
    candidates are the formats that parse *every* date in the file. One candidate: use it, and only
    it, so a stray row cannot be read differently from its neighbours. Several that agree on every
    value: no ambiguity, use the first. Several that disagree: the first still wins, because a
    month-first default is right for every bank registered here, but the file is flagged so the
    person can check one date against the bank's site. None: the file mixes formats or is broken;
    fall back to per-row first-fit and say so.

    Only values that at least one format can read take part, so footer prose does not vote.
    """
    if len(formats) < 2:
        return formats, None

    readable = [value for value in values if _try_any(value, formats) is not None]
    if not readable:
        return formats, None

    candidates = [
        fmt for fmt in formats if all(_try_strptime(value, fmt) is not None for value in readable)
    ]
    if not candidates:
        return formats, (
            "Dates in this file do not all fit one format; each row was read with the first format "
            "that fits it. Check the dates before trusting the import."
        )

    chosen = candidates[0]
    if len(candidates) > 1:
        differing = [
            value
            for value in readable
            if len({_try_strptime(value, fmt) for fmt in candidates}) > 1
        ]
        if differing:
            return (chosen,), (
                f"{len(differing)} dates such as {differing[0].strip()!r} could be read as either "
                f"month/day or day/month; read as {_DATE_FORMAT_NAMES.get(chosen, chosen)}. "
                "Check one against the bank's site."
            )
    return (chosen,), None


def _try_strptime(value: str, fmt: str) -> date | None:
    try:
        return datetime.strptime(value.strip(), fmt).date()
    except ValueError:
        return None


def _try_any(value: str, formats: tuple[str, ...]) -> date | None:
    for fmt in formats:
        parsed = _try_strptime(value, fmt)
        if parsed is not None:
            return parsed
    return None


def _parse_date(value: str, formats: tuple[str, ...]) -> date:
    parsed = _try_any(value, formats)
    if parsed is None:
        raise ValueError(f"Unrecognized date {value!r} (tried {', '.join(formats)})")
    return parsed


def _statement_summary(
    preamble: dict[str, str],
    transactions: list[ParsedTransaction],
    balances: list[tuple[date, Decimal, Decimal]],
) -> StatementSummary | None:
    """Build a reconciliation checkpoint when the file carries enough to support one.

    Most CSV exports carry nothing. Those that do — a stated date range, or a running balance —
    give the ledger something to prove itself against, which is the whole point of the checkpoint.
    """
    start: date | None = None
    end: date | None = None

    stated_range = preamble.get("Date Range")
    if stated_range and "-" in stated_range:
        first, _, second = stated_range.partition("-")
        start = _try_date(first.strip())
        end = _try_date(second.strip())

    if start is None and transactions:
        start = min(t.transaction_date for t in transactions)
    if end is None and transactions:
        end = max(t.transaction_date for t in transactions)

    # The closing balance is the running balance after the MOST RECENT transaction. That used to
    # be inferred from the file's ordering by comparing the first and last dates — which chose
    # wrong whenever those two dates were equal, whenever the file was unsorted, and whenever the
    # newest row failed to parse. Now: the row with the greatest date, full stop, and on a tie
    # the one nearest the top for a newest-first file and the bottom for an oldest-first one.
    #
    # The opening balance is the oldest row's balance with that row's own movement removed: the
    # balance the period started from. It is what lets reconciliation work for an account whose
    # history was not imported from the day it opened — which is every account, the first time.
    closing = None
    opening = None
    if balances:
        first_date, last_date = balances[0][0], balances[-1][0]
        newest_first = first_date > last_date
        newest = max(balances, key=lambda b: b[0])
        oldest = min(balances, key=lambda b: b[0])
        if newest_first:
            newest = next(b for b in balances if b[0] == newest[0])
            oldest = next(b for b in reversed(balances) if b[0] == oldest[0])
        else:
            newest = next(b for b in reversed(balances) if b[0] == newest[0])
            oldest = next(b for b in balances if b[0] == oldest[0])
        closing = newest[1]
        opening = oldest[1] - oldest[2]

    if start is None and end is None and closing is None:
        return None
    return StatementSummary(
        period_start=start, period_end=end, opening_balance=opening, closing_balance=closing
    )


def _try_date(value: str) -> date | None:
    return _try_any(value, ("%m/%d/%Y", "%Y-%m-%d", "%m/%d/%y"))


def _account_from_preamble(preamble: dict[str, str]) -> tuple[str, str, str | None] | None:
    """The account this whole file belongs to, when the metadata names one.

    Returns (mask, key, name). The full number is used to derive the key and then dropped — it is
    never returned (docs/SECURITY.md).
    """
    number = ""
    for label in _PREAMBLE_NUMBER_LABELS:
        if preamble.get(label):
            number = preamble[label].strip()
            break
    if not number:
        return None

    name = None
    for label in _PREAMBLE_NAME_LABELS:
        if preamble.get(label):
            name = preamble[label].strip()
            break
    return number[-4:], account_hash(number), name


def _map_row(
    row: dict[str, str],
    fmt: CsvFormat,
    account_ref: str,
    file_account: tuple[str, str, str | None] | None,
    date_formats: tuple[str, ...],
) -> ParsedTransaction:
    raw_amount = _row_amount(row, fmt)
    txn_date = _parse_date(row.get(fmt.date_column) or "", date_formats)
    description = (row.get(fmt.description_column) or "").strip()
    if not description and fmt.memo_column:
        # Some rows carry only a memo. An empty description would make the row unidentifiable in
        # the review queue and would weaken its dedupe key.
        description = (row.get(fmt.memo_column) or "").strip()
    if not description:
        raise ValueError("Row has no description")

    # The merchant string is built from the description AND the memo where both exist, because in
    # at least one real export the description is a generic transaction type — "Point Of Sale
    # Withdrawal" — and the actual merchant only appears in the memo. Categorizing on the
    # description alone leaves those rows permanently unidentifiable; in a real file it was 8 of 40.
    merchant_source = description
    if fmt.memo_column:
        memo = (row.get(fmt.memo_column) or "").strip()
        if memo and memo.upper() not in description.upper():
            merchant_source = f"{description} {memo}"

    posted: date | None = None
    if fmt.posted_date_column and row.get(fmt.posted_date_column):
        # Lenient on purpose: the posted date is informational, and the same file-level format
        # applies to it as to the transaction date.
        posted = _try_any(row[fmt.posted_date_column], date_formats)

    is_outflow = raw_amount < 0 if fmt.negative_is_debit else raw_amount > 0
    direction = TransactionDirection.DEBIT if is_outflow else TransactionDirection.CREDIT

    # The file's own row type, when it has one. Chase marks card payments as "Payment" and refunds
    # as "Return"; both are non-expenses. This is a stronger signal than any description pattern.
    row_type = row.get(fmt.type_column) if fmt.type_column else None

    external_id = None
    if fmt.external_id_column:
        external_id = (row.get(fmt.external_id_column) or "").strip() or None

    # A file covering several accounts must say which one each row belongs to, or the importer has
    # to guess — and a wrong guess files someone's brokerage activity against their checking.
    account_mask = None
    account_key = None
    account_name = None
    row_account = (
        (row.get(fmt.account_number_column) or "").strip() if fmt.account_number_column else ""
    )
    if row_account:
        account_mask = row_account[-4:]
        account_key = account_hash(row_account)
        # Fidelity's per-row "Account" column is the account's nickname, beside its number.
        account_name = (row.get("Account") or "").strip() or None
    elif file_account:
        # A single-account file, identified once in its own metadata.
        account_mask, account_key, account_name = file_account

    return ParsedTransaction(
        transaction_date=txn_date,
        posted_date=posted,
        description=description,
        merchant=normalize_description(merchant_source),
        amount=abs(raw_amount),
        direction=direction,
        external_id=external_id,
        # Scoped to the row's own account when the file names one, so identical activity in two
        # accounts on the same day does not collide into a single row.
        dedupe_key=dedupe_key(
            account_key or account_ref, txn_date, raw_amount, description, external_id
        ),
        account_mask=account_mask,
        account_key=account_key,
        account_name=account_name,
        is_probable_transfer=looks_like_transfer(row_type, fmt),
        is_probable_refund=looks_like_refund(row_type, fmt),
        raw={
            k: v
            for k, v in row.items()
            if k and v is not None and k not in _REDACTED_COLUMNS and k != fmt.account_number_column
        },
    )


def _row_amount(row: dict[str, str], fmt: CsvFormat) -> Decimal:
    """The row's signed amount, however this bank chose to express it.

    Two arrangements exist in the wild: one signed column, or a debit/credit pair with the value in
    whichever applies. The pair needs care — a debit is already written negative in at least one
    real export, so negating it unconditionally would turn every payment into a deposit.
    """
    separator = fmt.decimal_separator
    if fmt.amount_column:
        value = row.get(fmt.amount_column)
        if value is None or not value.strip():
            raise ValueError("Empty amount")
        return parse_money(value, decimal_separator=separator)

    debit = parse_optional_money(row.get(fmt.debit_column or ""), decimal_separator=separator)
    credit = parse_optional_money(row.get(fmt.credit_column or ""), decimal_separator=separator)

    if debit is not None and credit is not None:
        raise ValueError("Row has both a debit and a credit amount")
    if debit is not None:
        # Taken as written when already signed; negated only when the bank omits the sign.
        return debit if debit < 0 else -debit
    if credit is not None:
        return abs(credit)
    raise ValueError("Row has neither a debit nor a credit amount")


def looks_like_transfer(row_type: str | None, fmt: CsvFormat) -> bool:
    """Whether a source row type marks a payment or transfer rather than a purchase.

    Kept separate from the mapping logic on purpose: this reports what the file *says*, and the API
    decides what it *means*. Surfaced on every row as ``is_probable_transfer``.
    """
    if not row_type or not fmt.type_column:
        return False
    value = row_type.strip()
    if value in fmt.transfer_types:
        return True
    return any(re.search(pattern, value, re.IGNORECASE) for pattern in fmt.non_expense_patterns)


def looks_like_refund(row_type: str | None, fmt: CsvFormat) -> bool:
    """Whether a source row type marks a reversed purchase.

    A refund used to be reported as a transfer, and downstream that meant: not spending, never
    categorizable. But a refund IS spending — negative spending — and an $84 grocery refund that
    cannot be booked against Groceries leaves that category overcharged by $84 for good.
    """
    if not row_type or not fmt.type_column:
        return False
    return row_type.strip() in fmt.refund_types
