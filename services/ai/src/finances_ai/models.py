"""Shared wire models.

These types define the contract between the Java API and this service. Changing a field here is a
breaking change on both sides — update the Java DTOs in the same commit.
"""

from __future__ import annotations

from datetime import date, datetime
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


class Quote(BaseModel):
    """One price for one symbol at one moment (M7).

    A price is money-adjacent, not money: it has no direction and never enters the ledger. Decimal
    all the same — it multiplies a share count into a market value that does.
    """

    symbol: str
    price: Decimal = Field(gt=0, allow_inf_nan=False)
    previous_close: Decimal | None = Field(default=None, allow_inf_nan=False)
    as_of: datetime
    # "alpaca", "fake". Carried through to the row and the screen so a fake price can never be
    # mistaken for a real one.
    source: str


class QuotesResponse(BaseModel):
    provider: str
    quotes: list[Quote]
    # One per symbol the provider could not price. A missing quote is never silent.
    warnings: list[str] = Field(default_factory=list)


class MarketStatus(BaseModel):
    provider: str
    available: bool
    detail: str | None = None


class OrderRequest(BaseModel):
    """An order the API has already confirmed and now wants sent (M7b).

    Everything that decides whether it SHOULD be sent — the person's confirmation, the daily cap,
    the kill switch — happened on the API side before this crosses the wire. ``client_order_id`` is
    the API's own id, so a retry after a timeout cannot place the same order twice.
    """

    client_order_id: str = Field(min_length=1, max_length=48)
    symbol: str = Field(pattern=r"^[A-Z][A-Z0-9.\-]{0,15}$")
    side: str = Field(pattern=r"^(buy|sell)$")
    quantity: Decimal = Field(gt=0, allow_inf_nan=False)
    order_type: str = Field(default="market", pattern=r"^(market|limit)$")
    limit_price: Decimal | None = Field(default=None, gt=0, allow_inf_nan=False)
    time_in_force: str = Field(default="day", pattern=r"^(day|gtc)$")


class BrokerOrder(BaseModel):
    """What the broker says about an order. Statuses are folded to the API's state machine:
    accepted, partially_filled, filled, cancelled, rejected, expired."""

    broker: str
    broker_order_id: str
    status: str
    broker_status: str | None = None
    submitted_at: datetime | None = None
    filled_at: datetime | None = None
    filled_quantity: Decimal = Decimal(0)
    filled_avg_price: Decimal | None = None


class BrokerStatus(BaseModel):
    broker: str
    available: bool
    paper: bool = True
    market_open: bool | None = None
    buying_power: Decimal | None = None
    portfolio_value: Decimal | None = None
    detail: str | None = None


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


# --- Strategies and backtests (M7c, D-19) ---------------------------------------------------

TIMEFRAME_PATTERN = r"^(1Min|5Min|15Min|1Hour|1Day)$"


class Bar(BaseModel):
    """One OHLCV bar. Prices are Decimal for the same reason quotes are."""

    ts: datetime
    open: Decimal = Field(gt=0, allow_inf_nan=False)
    high: Decimal = Field(gt=0, allow_inf_nan=False)
    low: Decimal = Field(gt=0, allow_inf_nan=False)
    close: Decimal = Field(gt=0, allow_inf_nan=False)
    volume: int = 0


class StrategyParam(BaseModel):
    name: str
    label: str
    type: str = Field(pattern=r"^(int|decimal)$")
    default: Decimal
    min: Decimal | None = None
    max: Decimal | None = None
    description: str


class StrategyInfo(BaseModel):
    kind: str
    label: str
    description: str
    # True when the strategy only makes sense on intraday bars (it is flat by each close).
    intraday: bool
    params: list[StrategyParam]


class BacktestRequest(BaseModel):
    """A backtest as the API asks for it. Every assumption is a field, with a default that errs
    against the strategy: slippage on, commission zero only because most brokers charge none."""

    strategy: str = Field(min_length=1, max_length=40)
    params: dict[str, Decimal] = Field(default_factory=dict)
    symbol: str = Field(pattern=r"^[A-Z][A-Z0-9.\-]{0,15}$")
    timeframe: str = Field(pattern=TIMEFRAME_PATTERN)
    start: date
    end: date
    initial_cash: Decimal = Field(default=Decimal("10000"), gt=0, allow_inf_nan=False)
    slippage_bps: Decimal = Field(default=Decimal("5"), ge=0, le=500, allow_inf_nan=False)
    commission_per_order: Decimal = Field(default=Decimal("0"), ge=0, allow_inf_nan=False)
    # The last part of the period is held back and reported separately: a strategy that only
    # works on the bars it was tuned on shows itself here.
    out_of_sample_fraction: Decimal = Field(default=Decimal("0.3"), ge=0, lt=1)


class BacktestTrade(BaseModel):
    entered_at: datetime
    exited_at: datetime | None = None
    quantity: int
    entry_price: Decimal
    exit_price: Decimal | None = None
    pnl: Decimal | None = None
    return_pct: Decimal | None = None
    reason_in: str
    reason_out: str | None = None
    # Opened and closed in the same New York session: what the pattern day trader rule counts.
    same_day: bool = False


class BacktestMetrics(BaseModel):
    bars: int
    trades: int
    total_return_pct: Decimal
    # Buying at the first bar the strategy could have acted on and holding to the last close, with
    # the same slippage and commission. Always shown beside the strategy's return.
    benchmark_return_pct: Decimal
    max_drawdown_pct: Decimal
    win_rate_pct: Decimal | None = None
    profit_factor: Decimal | None = None
    avg_trade_pct: Decimal | None = None
    exposure_pct: Decimal
    # A dimensionless statistic, not money; the one float in this file.
    sharpe: float | None = None
    final_equity: Decimal
    day_trades: int


class EquityPoint(BaseModel):
    ts: datetime
    equity: Decimal


class BacktestResult(BaseModel):
    provider: str
    strategy: str
    params: dict[str, Decimal]
    symbol: str
    timeframe: str
    start: date
    end: date
    metrics: BacktestMetrics
    in_sample: BacktestMetrics | None = None
    out_of_sample: BacktestMetrics | None = None
    equity_curve: list[EquityPoint]
    trades: list[BacktestTrade]
    # The honesty notes: fake bars, too few trades, buy-and-hold won, in-sample beat out-of-sample,
    # the pattern day trader rule. Never empty when any of them applies.
    warnings: list[str] = Field(default_factory=list)


class EvaluateRequest(BaseModel):
    """ "What does this strategy say right now?" — for a live strategy that proposes drafts."""

    strategy: str = Field(min_length=1, max_length=40)
    params: dict[str, Decimal] = Field(default_factory=dict)
    symbol: str = Field(pattern=r"^[A-Z][A-Z0-9.\-]{0,15}$")
    timeframe: str = Field(pattern=TIMEFRAME_PATTERN)
    position: str = Field(default="flat", pattern=r"^(flat|long)$")
    lookback_bars: int = Field(default=300, ge=20, le=5000)


class EvaluateResult(BaseModel):
    provider: str
    symbol: str
    bars: int
    as_of: datetime | None = None
    last_close: Decimal | None = None
    # buy, sell, or nothing. A signal from one of the last three bars, newest first.
    action: str | None = None
    reason: str | None = None
    warnings: list[str] = Field(default_factory=list)
