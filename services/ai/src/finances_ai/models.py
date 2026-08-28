"""Shared wire models.

These types define the contract between the Java API and this service. Changing a field here is a
breaking change on both sides — update the Java DTOs in the same commit.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel, Field


class TransactionDirection(StrEnum):
    DEBIT = "debit"
    CREDIT = "credit"


class ParsedTransaction(BaseModel):
    """One normalized row extracted from a statement.

    Money is Decimal everywhere. Never float — see CLAUDE.md.
    """

    transaction_date: date
    posted_date: date | None = None
    description: str
    merchant: str | None = None
    amount: Decimal = Field(description="Always positive; direction carries the sign")
    direction: TransactionDirection
    external_id: str | None = Field(
        default=None, description="Provider's own ID, when the source gives one"
    )
    dedupe_key: str = Field(description="Stable hash used by the API to reject re-imports")
    # Set only for exports that cover several accounts in one file — a brokerage history or a
    # positions download. The API routes the row by these rather than by a single nominated
    # account. docs/SECURITY.md: the full number is never returned.
    account_mask: str | None = None
    account_key: str | None = None
    account_name: str | None = Field(
        default=None,
        description="The institution's own name for the account, when the file states one.",
    )
    is_probable_transfer: bool = Field(
        default=False,
        description=(
            "The source file's own row type says this is a payment, refund or adjustment rather "
            "than a purchase — e.g. Chase's Type column. A HINT, not a decision: the parser "
            "reports what the file says and the API decides what it means. Far more reliable "
            "than pattern-matching the description, which is all the categorizer can otherwise do."
        ),
    )
    raw: dict[str, str] = Field(default_factory=dict, description="Original source row, verbatim")


class StatementSummary(BaseModel):
    """Reconciliation metadata, when the file carries it.

    A CSV export almost never does; OFX/QFX do. Present so the API can create a `statement`
    checkpoint and prove the imported rows add up to what the institution says.
    """

    period_start: date | None = None
    period_end: date | None = None
    closing_balance: Decimal | None = Field(
        default=None,
        description="Signed per the project convention: negative means owed.",
    )


class ParseResult(BaseModel):
    source_format: str
    transactions: list[ParsedTransaction]
    warnings: list[str] = Field(default_factory=list)
    statement: StatementSummary | None = None


class ParsedPosition(BaseModel):
    """One holding from a brokerage positions export.

    A position is a *snapshot*, not a money movement: quantity, price and cost basis at a moment in
    time. It must not be forced into the transaction model — docs/DOMAIN.md is explicit about that,
    and the project's debit/credit sign convention does not apply to a market value.
    """

    # docs/SECURITY.md: account numbers are stored masked unless there is a concrete reason.
    # The full number is deliberately never returned by the parser.
    account_mask: str = Field(description="Last four characters of the account number")
    account_key: str = Field(
        description=(
            "Stable non-reversible id derived from the full account number. Lets the API match a "
            "row to the same account on every re-import without ever handling the number itself."
        )
    )
    account_name: str | None = None

    symbol: str = Field(description="Ticker, with any footnote markers stripped")
    description: str | None = None

    # Cash and money-market rows carry a value but no quantity or price, so these are optional.
    quantity: Decimal | None = None
    last_price: Decimal | None = None
    current_value: Decimal
    cost_basis_total: Decimal | None = None
    average_cost_basis: Decimal | None = None
    total_gain_loss: Decimal | None = None

    is_cash: bool = Field(
        default=False,
        description=(
            "A cash or money-market sweep row rather than a security holding. Determined by the "
            "symbol, never by the export's Type column — that carries the account registration."
        ),
    )
    account_registration: str | None = Field(
        default=None, description="Cash or Margin, as the broker classifies the account."
    )
    raw: dict[str, str] = Field(default_factory=dict)


class PositionsResult(BaseModel):
    source_format: str
    as_of: date | None = None
    positions: list[ParsedPosition]
    warnings: list[str] = Field(default_factory=list)


class CategorySuggestion(BaseModel):
    dedupe_key: str
    category: str | None
    confidence: float = Field(ge=0.0, le=1.0)
    method: str = Field(description="rule | similarity | model")
    rationale: str | None = None
    is_transfer: bool = Field(
        default=False,
        description=(
            "True for card payments and account transfers. These are NOT expenses — counting them "
            "double-charges the budget. See docs/DOMAIN.md."
        ),
    )


class CategorizeRequest(BaseModel):
    transactions: list[ParsedTransaction]


class CategorizeResponse(BaseModel):
    suggestions: list[CategorySuggestion]
