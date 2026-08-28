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
"""

from __future__ import annotations

import csv
import hashlib
import io
import re
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, InvalidOperation

from finances_ai.models import (
    ParsedTransaction,
    ParseResult,
    StatementSummary,
    TransactionDirection,
)

_WHITESPACE = re.compile(r"\s+")
# Trailing store/reference numbers that make otherwise identical merchants look distinct.
_TRAILING_REF = re.compile(r"[\s#*]+[0-9]{3,}$")

# How far to look for a header before giving up. Metadata preambles are a few lines, never dozens.
_MAX_PREAMBLE_ROWS = 12


class ParserNotFoundError(ValueError):
    """Raised when no registered format matches the uploaded file's header row."""


@dataclass(frozen=True)
class CsvFormat:
    name: str
    required_columns: frozenset[str]
    date_column: str
    description_column: str
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
    non_expense_types: frozenset[str] = frozenset()
    # For exports whose type field is free text rather than a short enum — a brokerage writes
    # "YOU BOUGHT ... (Cash)", not "Buy" — matched case-insensitively against the type column.
    non_expense_patterns: tuple[str, ...] = ()
    # Some exports put outflows as negative, some as positive in a separate column.
    negative_is_debit: bool = True


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
        non_expense_types=frozenset({"Payment", "Return", "Adjustment"}),
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


def normalize_description(description: str) -> str:
    """Collapse a raw statement description toward a stable merchant string.

    Deliberately conservative — it only removes noise that is definitely noise. Aggressive
    normalization loses information the categorizer needs.
    """
    cleaned = _WHITESPACE.sub(" ", description).strip().upper()
    cleaned = _TRAILING_REF.sub("", cleaned)
    return cleaned.strip()


def _parse_date(value: str, formats: tuple[str, ...]) -> date:
    for fmt in formats:
        try:
            return datetime.strptime(value.strip(), fmt).date()
        except ValueError:
            continue
    raise ValueError(f"Unrecognized date {value!r} (tried {', '.join(formats)})")


def _parse_amount(value: str) -> Decimal:
    cleaned = value.strip().replace("$", "").replace(",", "")
    if cleaned.startswith("(") and cleaned.endswith(")"):
        cleaned = "-" + cleaned[1:-1]
    if not cleaned:
        raise ValueError("Empty amount")
    try:
        return Decimal(cleaned)
    except InvalidOperation as exc:
        raise ValueError(f"Unparseable amount {value!r}") from exc


def account_hash(account_number: str) -> str:
    """Stable, non-reversible id for an account number.

    Lets a row be matched to the same account across re-imports without the full number ever being
    returned or stored (docs/SECURITY.md).
    """
    return hashlib.sha256(account_number.strip().encode("utf-8")).hexdigest()[:16]


def dedupe_key(
    account_ref: str,
    transaction_date: date,
    amount: Decimal,
    description: str,
    external_id: str | None = None,
) -> str:
    """Stable identity for a transaction, so re-imports don't duplicate.

    **When the export carries the institution's own transaction id, that is the identity.** Nothing
    else can distinguish genuinely separate transactions that happen to match on every visible
    field, and they are not rare: a real month contained three $1,000 transfers to the same payee on
    the same day, distinguishable only by the bank's Transaction Number. Hashing date, amount and
    description alone collapsed them into one and silently lost $2,100 — money missing from a
    ledger, with nothing to show anything had gone wrong.

    Falling back to the hash is for exports that provide no id (a Chase card CSV). It deliberately
    excludes anything that varies between exports of the same period — row order, posted date,
    formatting — and includes the normalized description so two same-day, same-amount purchases at
    different merchants stay distinct.
    """
    if external_id:
        payload = f"{account_ref}|id|{external_id.strip()}"
    else:
        payload = "|".join(
            [
                account_ref,
                transaction_date.isoformat(),
                f"{amount:.4f}",
                normalize_description(description),
            ]
        )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:32]


def detect_format(fieldnames: list[str]) -> CsvFormat:
    present = {name.strip() for name in fieldnames if name}
    for fmt in _FORMATS:
        if fmt.required_columns <= present:
            return fmt
    raise ParserNotFoundError(
        f"No parser matches columns {sorted(present)}. "
        f"Known formats: {', '.join(registered_formats())}"
    )


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
    if isinstance(content, bytes):
        content = content.decode("utf-8-sig")

    rows = list(csv.reader(io.StringIO(content)))
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

    transactions: list[ParsedTransaction] = []
    warnings: list[str] = []
    # Paired with the row's date, because which end of the file holds the closing balance depends
    # on the export's ordering — see _statement_summary.
    balances: list[tuple[date, Decimal]] = []

    for offset, values in enumerate(rows[header_index + 1 :]):
        line_number = header_index + offset + 2
        if not any(cell.strip() for cell in values):
            continue
        # Footer prose and trailing notes come through as one-or-two-field rows. Without this they
        # are parsed as data and every one produces a spurious warning about an unreadable date.
        if len(values) < max(2, len(header) // 2):
            continue

        row = dict(zip(header, values, strict=False))
        try:
            transactions.append(_map_row(row, fmt, account_ref, file_account))
        except ValueError as exc:
            warnings.append(f"line {line_number}: {exc}")
            continue

        if fmt.balance_column:
            running = _parse_optional_amount(row.get(fmt.balance_column))
            if running is not None:
                balances.append((transactions[-1].transaction_date, running))

    return ParseResult(
        source_format=fmt.name,
        transactions=transactions,
        warnings=warnings,
        statement=_statement_summary(preamble, transactions, balances),
    )


def _statement_summary(
    preamble: dict[str, str],
    transactions: list[ParsedTransaction],
    balances: list[tuple[date, Decimal]],
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

    # The closing balance is the running balance after the MOST RECENT transaction, which is not
    # simply the last row: real exports come newest-first at least as often as oldest-first.
    # Taking the last row from a newest-first file reports the oldest balance as the closing one —
    # a wrong figure that looks entirely plausible, and the reconciliation view would then accuse
    # a correct ledger of being out by the whole period's movement.
    closing = None
    if balances:
        newest_first = balances[0][0] > balances[-1][0]
        closing = balances[0][1] if newest_first else balances[-1][1]

    if start is None and end is None and closing is None:
        return None
    return StatementSummary(period_start=start, period_end=end, closing_balance=closing)


def _try_date(value: str) -> date | None:
    for fmt in ("%m/%d/%Y", "%Y-%m-%d", "%m/%d/%y"):
        try:
            return datetime.strptime(value, fmt).date()
        except ValueError:
            continue
    return None


def _parse_optional_amount(value: str | None) -> Decimal | None:
    if value is None or not value.strip():
        return None
    try:
        return _parse_amount(value)
    except ValueError:
        return None


def _account_from_preamble(preamble: dict[str, str]) -> tuple[str, str, str | None] | None:
    """The account this whole file belongs to, when the metadata names one.

    Returns (mask, key, name). The full number is used to derive the key and then dropped — it is
    never returned (docs/SECURITY.md).
    """
    number = ""
    for label in ("Account Number", "Account number", "Account"):
        if preamble.get(label):
            number = preamble[label].strip()
            break
    if not number:
        return None

    name = None
    for label in ("Account Name", "Account name"):
        if preamble.get(label):
            name = preamble[label].strip()
            break
    return number[-4:], account_hash(number), name


def _map_row(
    row: dict[str, str],
    fmt: CsvFormat,
    account_ref: str,
    file_account: tuple[str, str, str | None] | None = None,
) -> ParsedTransaction:
    raw_amount = _row_amount(row, fmt)
    txn_date = _parse_date(row[fmt.date_column], fmt.date_formats)
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
        try:
            posted = _parse_date(row[fmt.posted_date_column], fmt.date_formats)
        except ValueError:
            posted = None

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
        is_probable_transfer=looks_like_non_expense(row_type, fmt),
        raw={k: v for k, v in row.items() if k and v is not None},
    )


def _row_amount(row: dict[str, str], fmt: CsvFormat) -> Decimal:
    """The row's signed amount, however this bank chose to express it.

    Two arrangements exist in the wild: one signed column, or a debit/credit pair with the value in
    whichever applies. The pair needs care — a debit is already written negative in at least one
    real export, so negating it unconditionally would turn every payment into a deposit.
    """
    if fmt.amount_column:
        return _parse_amount(row[fmt.amount_column])

    debit = _parse_optional_amount(row.get(fmt.debit_column or ""))
    credit = _parse_optional_amount(row.get(fmt.credit_column or ""))

    if debit is not None and credit is not None:
        raise ValueError("Row has both a debit and a credit amount")
    if debit is not None:
        # Taken as written when already signed; negated only when the bank omits the sign.
        return debit if debit < 0 else -debit
    if credit is not None:
        return abs(credit)
    raise ValueError("Row has neither a debit nor a credit amount")


def looks_like_non_expense(row_type: str | None, fmt: CsvFormat) -> bool:
    """Whether a source row type marks a payment/refund rather than a purchase.

    Kept separate from the mapping logic on purpose: this reports what the file *says*, and the API
    decides what it *means*. Surfaced on every row as ``is_probable_transfer``.
    """
    if not row_type or not fmt.type_column:
        return False
    value = row_type.strip()
    if value in fmt.non_expense_types:
        return True
    return any(re.search(pattern, value, re.IGNORECASE) for pattern in fmt.non_expense_patterns)
