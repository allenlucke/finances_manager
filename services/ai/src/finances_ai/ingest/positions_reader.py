"""Brokerage positions exports (Fidelity's "Portfolio Positions" CSV).

A positions file is a **snapshot of holdings**, not a transaction history: quantity, price, market
value and cost basis at a moment in time. It is the raw material for net worth and cost basis
(M4) and for anything that watches the market (M7) — but it contains no money movements, so it
cannot feed the ledger.

Every quirk handled below was found in a real export rather than imagined:

* A **UTF-8 BOM** on the header, so the first column reads ``\\ufeffAccount number``.
* A **trailing comma** on every data row, producing a phantom final column.
* **Footer prose** — several quoted legal paragraphs and a "Date downloaded ..." line after a blank
  line. Parsed as rows by any naive reader, and they are not rows.
* **Footnote markers glued to tickers**: ``SPAXX**``, ``USD***``. The asterisks are not the symbol.
* **Cash and money-market rows** with a value but no quantity, price or cost basis.
* **``--``** meaning "not applicable", alongside genuinely empty fields.
* Currency and percentages formatted for humans: ``$1,234.56``, ``+$12.34``, ``-1.23%``.
* **Several accounts in one file.** A positions export is not per-account, so the importing side
  must map each row to an account rather than assume one.
* A **``Type`` column that does not mean what it looks like**: it carries the account registration
  (Cash or Margin), not whether the row is cash. Reading it as the latter marks ordinary equity
  holdings as cash.
"""

from __future__ import annotations

import csv
import io
import re
from datetime import date, datetime
from decimal import InvalidOperation

from finances_ai.ingest.common import (
    account_hash,
    decode_text,
    legacy_account_hash,
    parse_optional_money,
)
from finances_ai.models import ParsedPosition, PositionsResult

# Trailing footnote markers on a ticker, e.g. SPAXX** or USD***.
_FOOTNOTE_MARKER = re.compile(r"[*†‡]+$")

# "Date downloaded Aug-27-2026 2:14 a.m ET"
_DOWNLOADED = re.compile(r"Date downloaded\s+([A-Za-z]{3}-\d{1,2}-\d{4})", re.IGNORECASE)

_REQUIRED_COLUMNS = frozenset({"Symbol", "Current value"})

# Never echoed back in `raw`. See docs/SECURITY.md: account numbers are stored masked.
_REDACTED_COLUMNS = frozenset({"Account number"})

# Symbols Fidelity uses for the cash sweep rather than a security.
_CASH_SYMBOLS = frozenset({"SPAXX", "FDRXX", "FZFXX", "USD", "CASH", "FCASH"})


class PositionsParseError(ValueError):
    """Raised when a file is not a readable positions export."""


def parse_positions(content: str | bytes, source: str = "fidelity_positions") -> PositionsResult:
    """Parse a brokerage positions export into normalized holdings."""
    warnings: list[str] = []
    if isinstance(content, bytes):
        # Strict UTF-8 with the BOM stripped, and a cp1252 fallback that says so — the same policy
        # as every other reader. This one used to decode with errors="replace", which turned a
        # Windows export's curly apostrophe into U+FFFD without a word.
        content, encoding_note = decode_text(content)
        if encoding_note:
            warnings.append(encoding_note)

    try:
        reader = csv.DictReader(io.StringIO(content))
        fieldnames = reader.fieldnames
    except csv.Error as exc:
        raise PositionsParseError(f"Not readable as CSV: {exc}") from exc
    if not fieldnames:
        raise PositionsParseError("File has no header row")

    columns = {name.strip().lstrip("﻿") for name in fieldnames if name}
    missing = _REQUIRED_COLUMNS - columns
    if missing:
        raise PositionsParseError(
            f"Not a positions export: missing {', '.join(sorted(missing))}. "
            "A transaction history export has different columns."
        )

    positions: list[ParsedPosition] = []
    as_of: date | None = None

    for line_number, row in enumerate(reader, start=2):
        cleaned = {
            (key.strip().lstrip("﻿") if key else ""): (value or "").strip()
            for key, value in row.items()
            if key is not None  # drops the phantom column from the trailing comma
        }

        # The footer begins once rows stop looking like holdings. Capture the download date from it
        # rather than discarding it — it is the only "as of" the file gives.
        if not cleaned.get("Symbol") or not cleaned.get("Current value"):
            found = _DOWNLOADED.search(" ".join(v for v in cleaned.values() if v))
            if found:
                as_of = _parse_download_date(found.group(1))
            continue

        try:
            positions.append(_map_position(cleaned))
        except (ValueError, InvalidOperation) as exc:
            warnings.append(f"line {line_number}: {exc}")

    if not positions:
        raise PositionsParseError("No positions found in the file")

    return PositionsResult(
        source_format=source, as_of=as_of, positions=positions, warnings=warnings
    )


def _map_position(row: dict[str, str]) -> ParsedPosition:
    account_number = row.get("Account number", "")
    if not account_number:
        raise ValueError("Row has no account number")

    symbol = _FOOTNOTE_MARKER.sub("", row.get("Symbol", "")).strip().upper()
    if not symbol:
        raise ValueError("Row has no symbol")

    current_value = parse_optional_money(row.get("Current value"))
    if current_value is None:
        raise ValueError("Row has no current value")

    return ParsedPosition(
        account_mask=account_number[-4:],
        account_key=account_hash(account_number),
        legacy_account_key=legacy_account_hash(account_number),
        account_name=row.get("Account name") or None,
        symbol=symbol,
        description=row.get("Description") or None,
        quantity=parse_optional_money(row.get("Quantity")),
        last_price=parse_optional_money(row.get("Last price")),
        current_value=current_value,
        cost_basis_total=parse_optional_money(row.get("Cost basis total")),
        average_cost_basis=parse_optional_money(row.get("Average cost basis")),
        total_gain_loss=parse_optional_money(row.get("Total gain/loss dollar")),
        # Decided by the symbol alone. The file's "Type" column looks like it means this and does
        # not: it is the account's registration (Cash vs Margin), so trusting it flagged AAPL in a
        # cash-registered account as a cash holding. Caught only by running a real export.
        is_cash=symbol in _CASH_SYMBOLS,
        account_registration=row.get("Type") or None,
        # The account number is deliberately excluded. `raw` is a convenience passthrough of the
        # source row, and without this filter the full number rides along inside it — defeating the
        # masking two fields above and putting it into every response and stored payload
        # (docs/SECURITY.md).
        raw={
            key: value
            for key, value in row.items()
            if key and value and key not in _REDACTED_COLUMNS
        },
    )


def _parse_download_date(value: str) -> date | None:
    """Fidelity writes the footer date as Mon-DD-YYYY."""
    try:
        return datetime.strptime(value, "%b-%d-%Y").date()
    except ValueError:
        return None
