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

from finances_ai.models import ParsedTransaction, ParseResult, TransactionDirection

_WHITESPACE = re.compile(r"\s+")
# Trailing store/reference numbers that make otherwise identical merchants look distinct.
_TRAILING_REF = re.compile(r"[\s#*]+[0-9]{3,}$")


class ParserNotFoundError(ValueError):
    """Raised when no registered format matches the uploaded file's header row."""


@dataclass(frozen=True)
class CsvFormat:
    name: str
    required_columns: frozenset[str]
    date_column: str
    description_column: str
    amount_column: str
    date_formats: tuple[str, ...]
    posted_date_column: str | None = None
    type_column: str | None = None
    non_expense_types: frozenset[str] = frozenset()
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


def dedupe_key(account_ref: str, transaction_date: date, amount: Decimal, description: str) -> str:
    """Stable identity for a transaction, so re-imports don't duplicate.

    Intentionally excludes anything that varies between exports of the same period (row order,
    posted date, formatting) and includes the normalized description so two same-day, same-amount
    purchases at different merchants stay distinct.
    """
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


def parse_csv(content: str | bytes, account_ref: str = "unknown") -> ParseResult:
    """Parse a CSV statement export into normalized transactions."""
    if isinstance(content, bytes):
        content = content.decode("utf-8-sig")

    reader = csv.DictReader(io.StringIO(content))
    if not reader.fieldnames:
        raise ParserNotFoundError("File has no header row")

    fmt = detect_format(list(reader.fieldnames))
    transactions: list[ParsedTransaction] = []
    warnings: list[str] = []

    for line_number, row in enumerate(reader, start=2):
        try:
            transactions.append(_map_row(row, fmt, account_ref))
        except ValueError as exc:
            warnings.append(f"line {line_number}: {exc}")

    return ParseResult(source_format=fmt.name, transactions=transactions, warnings=warnings)


def _map_row(row: dict[str, str], fmt: CsvFormat, account_ref: str) -> ParsedTransaction:
    raw_amount = _parse_amount(row[fmt.amount_column])
    txn_date = _parse_date(row[fmt.date_column], fmt.date_formats)
    description = (row.get(fmt.description_column) or "").strip()

    posted: date | None = None
    if fmt.posted_date_column and row.get(fmt.posted_date_column):
        try:
            posted = _parse_date(row[fmt.posted_date_column], fmt.date_formats)
        except ValueError:
            posted = None

    is_outflow = raw_amount < 0 if fmt.negative_is_debit else raw_amount > 0
    direction = TransactionDirection.DEBIT if is_outflow else TransactionDirection.CREDIT

    return ParsedTransaction(
        transaction_date=txn_date,
        posted_date=posted,
        description=description,
        merchant=normalize_description(description),
        amount=abs(raw_amount),
        direction=direction,
        dedupe_key=dedupe_key(account_ref, txn_date, raw_amount, description),
        raw={k: v for k, v in row.items() if k and v is not None},
    )


def looks_like_non_expense(row_type: str | None, fmt: CsvFormat) -> bool:
    """Whether a source row type marks a payment/refund rather than a purchase.

    Kept separate from parsing on purpose: the *parser* reports what the file says, the
    *categorizer* decides what it means.
    """
    if not row_type or not fmt.type_column:
        return False
    return row_type.strip() in fmt.non_expense_types
