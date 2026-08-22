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
    raw: dict[str, str] = Field(default_factory=dict, description="Original source row, verbatim")


class ParseResult(BaseModel):
    source_format: str
    transactions: list[ParsedTransaction]
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
