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
    # Enforced, not just described. The API's sign convention — net worth is a plain SUM because
    # every amount is a magnitude — is only true if nothing on the wire is negative, infinite or
    # NaN, and a description alone enforces nothing. ge rather than gt: the schema allows a
    # zero-amount row (a waived fee is a real line on a statement).
    amount: Decimal = Field(
        ge=0,
        max_digits=19,
        decimal_places=4,
        allow_inf_nan=False,
        description="Always a positive magnitude; direction carries the sign",
    )
    direction: TransactionDirection
    external_id: str | None = Field(
        default=None, description="Provider's own ID, when the source gives one"
    )
    # NOT read by the API, which computes the one identity itself (TransactionService.dedupeKey)
    # from the resolved account, the institution's id when there is one, and otherwise the date,
    # magnitude and description. Kept so the parser's output stands on its own for anyone else
    # reading it; two implementations in two languages drifted three ways before the API took it
    # over.
    dedupe_key: str = Field(description="Stable hash; informational, the API derives its own")
    # Set only for exports that cover several accounts in one file — a brokerage history or a
    # positions download. The API routes the row by these rather than by a single nominated
    # account. docs/SECURITY.md: the full number is never returned.
    account_mask: str | None = None
    account_key: str | None = None
    # The pre-2026-09-12 unsalted key, so the API can re-link an account created under it. One
    # release only; see finances_ai.ingest.common.legacy_account_hash.
    legacy_account_key: str | None = None
    account_name: str | None = Field(
        default=None,
        description="The institution's own name for the account, when the file states one.",
    )
    is_probable_transfer: bool = Field(
        default=False,
        description=(
            "The source file's own row type says this is a payment to the account — money moving "
            "between the user's own accounts — rather than a purchase. A HINT, not a decision: the "
            "parser reports what the file says and the API decides what it means."
        ),
    )
    is_probable_refund: bool = Field(
        default=False,
        description=(
            "The source file's own row type says refund or adjustment. Deliberately NOT folded "
            "into is_probable_transfer: a transfer is not spending and must stay "
            "uncategorizable, while "
            "a refund is negative spending and must be bookable against the category it refunds. "
            "One flag carrying both meanings forced every refund into the transfer path, where the "
            "database's CHECK constraint made it uncategorizable forever."
        ),
    )
    # The file's own word for the row — Chase's Type ("Sale", "Payment", "Return"), OFX's TRNTYPE
    # ("XFER"), a brokerage's Action — which is what the transfer and refund hints were read from.
    # This replaced `raw`, which echoed every column of every row verbatim behind a blocklist of
    # column names, was read by nothing on the Java side, and was the one place a cardholder's
    # address could ride along under whatever heading an issuer chose for it.
    source_type: str | None = Field(
        default=None, description="The source file's own row type, when it has one"
    )


class StatementSummary(BaseModel):
    """Reconciliation metadata, when the file carries it.

    A CSV export almost never does; OFX/QFX do. Present so the API can create a `statement`
    checkpoint and prove the imported rows add up to what the institution says.
    """

    period_start: date | None = None
    period_end: date | None = None
    # The balance the period STARTED from. Derived from a running-balance column (oldest row's
    # balance minus that row's own movement) or read from the file. Load-bearing for
    # reconciliation: without it the ledger can only be checked against the closing balance by
    # summing every transaction since the account opened, which is wrong for any account whose
    # history was not imported from day one — i.e. every real account, on its first import.
    opening_balance: Decimal | None = None
    closing_balance: Decimal | None = Field(
        default=None,
        description="Signed per the project convention: negative means owed.",
    )
    # Set only on a per-account summary from a multi-statement file, by the same key the file's
    # rows carry, so the API records the checkpoint against the account it resolved the rows to.
    account_key: str | None = None
    account_mask: str | None = None


class ParseResult(BaseModel):
    source_format: str
    transactions: list[ParsedTransaction]
    warnings: list[str] = Field(default_factory=list)
    # The checkpoint for the account the caller nominated. None for a multi-account file.
    statement: StatementSummary | None = None
    # One checkpoint per account for a file holding several statements. Empty otherwise.
    statements: list[StatementSummary] = Field(default_factory=list)


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
            "Stable keyed id derived from the full account number. Lets the API match a row to "
            "the same account on every re-import without ever handling the number itself."
        )
    )
    # See ParsedTransaction.legacy_account_key.
    legacy_account_key: str | None = None
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


class PositionsResult(BaseModel):
    source_format: str
    as_of: date | None = None
    positions: list[ParsedPosition]
    warnings: list[str] = Field(default_factory=list)


class CategorySuggestion(BaseModel):
    dedupe_key: str
    category: str | None
    confidence: float = Field(ge=0.0, le=1.0)
    method: str = Field(
        description=(
            "rule | similarity | model — or none, when no tier had anything to say. Distinct from "
            "a rule that fired and decided on no category (a transfer): none means escalate."
        )
    )
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
    # The API's own account-type code (checking, credit_card, ...) for the account these rows
    # belong to, when it knows. The same words mean different things on different accounts —
    # "PAYMENT THANK YOU" is a card being paid on a card statement and merchant receipt text on
    # a checking one — and a rule that cannot see the account type has to guess.
    account_type: str | None = Field(
        default=None, description="checking | savings | credit_card | brokerage | loan | cash"
    )


class CategorizeResponse(BaseModel):
    suggestions: list[CategorySuggestion]
