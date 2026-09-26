"""MCP server for the finances API (D-17).

Lets Claude Code drive the finance app directly: read balances and spending, record and categorize
transactions, import statements, and undo mistakes. The API remains the system of record — every
tool here is a thin call onto an endpoint that already enforces the rules.

Two conventions from docs/DOMAIN.md are repeated in the tool descriptions rather than left implicit,
because getting either wrong produces a wrong number that still looks plausible:

* **Amounts are always positive**; ``direction`` carries the sign. A debit reduces an account's
  balance for every account type, so net worth is a plain sum.
* **Transfers are not spending.** Paying a credit card moves money between two accounts the user
  already owns; counting it as an expense double-charges the budget, because the purchase was
  charged when it happened.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

from mcp.server.mcpserver import MCPServer
from mcp.types import ToolAnnotations

from . import env
from .client import ApiError, FinancesClient

mcp = MCPServer("finances")

# Hints the client can use to decide what needs confirming. Marking the reads read-only is what
# earns the writes their weight: if everything looked equally consequential, nothing would.
READS = ToolAnnotations(read_only_hint=True)
WRITES = ToolAnnotations(read_only_hint=False, destructive_hint=False)
REMOVES = ToolAnnotations(read_only_hint=False, destructive_hint=True)

_client: FinancesClient | None = None


def client() -> FinancesClient:
    """The API client, created on first use so import never fails on a missing token."""
    global _client
    if _client is None:
        _client = FinancesClient()
    return _client


def _page(result: Any, page: int, size: int) -> Any:
    """Flattens Spring's ``Page`` envelope into something predictable.

    Some endpoints return a page object and some a bare list, which is a fine distinction inside
    the API and a trap out here: a caller that does the obvious thing and iterates gets the *field
    names* of the envelope instead of any transactions, and nothing raises. Every listing tool
    therefore answers the same shape, with ``has_more`` stated rather than left to be inferred from
    arithmetic on totals.
    """
    if isinstance(result, dict) and "content" in result:
        rows = result["content"]
        total = result.get("totalElements", len(rows))
        has_more = not result["last"] if "last" in result else (page + 1) * size < total
    elif isinstance(result, list):
        # A bare list says nothing about what it left out. A full page may be the end or may
        # not; a short page is the end. Saying "no more" for a full page of 100 was a lie the
        # model believed, and this is the honest reading of the only evidence there is.
        rows = result
        total = len(rows)
        has_more = len(rows) >= size
    else:
        # An error, or some other single object. Wrapping it would produce exactly the trap this
        # function exists to prevent: len(dict) is a key count, and the model would read
        # "total: 1" over a payload whose "transactions" is a bag of field names.
        return result
    return {
        "transactions": rows,
        "total": total,
        "page": page,
        "page_size": size,
        "has_more": has_more,
    }


def _guard(call: Any) -> Any:
    """Turns an API refusal into a sentence rather than a stack trace.

    An MCP tool that raises gives the model an opaque failure; one that returns the reason lets it
    explain the problem or fix the call. Server faults are re-raised — those are not the model's to
    work around, and quietly reporting one as a result would hide a real outage.
    """
    try:
        return call()
    except ApiError as error:
        if error.status >= 500:
            raise
        return {"error": error.detail, "status": error.status}


# ---------------------------------------------------------------------------------------------
# Reading
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=READS)
def list_accounts() -> Any:
    """Every account with its current balance.

    Balances are signed: negative means money is owed (a credit card balance of -696.31 means
    $696.31 is owed on it), positive means money is held.
    """
    return _guard(lambda: client().get("/api/v1/accounts"))


@mcp.tool(annotations=READS)
def net_worth() -> Any:
    """Net worth overall and per set of books.

    The row with a null ``ledgerEntityId`` is the combined total; the others are per entity
    (personal vs Feeling Froggy). Use ``list_entities`` to turn those ids into names.
    """
    return _guard(lambda: client().get("/api/v1/reports/net-worth"))


@mcp.tool(annotations=READS)
def list_entities() -> Any:
    """The sets of books — personal and business. Every account belongs to exactly one."""
    return _guard(lambda: client().get("/api/v1/entities"))


@mcp.tool(annotations=READS)
def list_categories() -> Any:
    """Spending and income categories, with their kind (``expense`` or ``income``)."""
    return _guard(lambda: client().get("/api/v1/categories"))


@mcp.tool(annotations=READS)
def list_transactions(
    date_from: str | None = None,
    date_to: str | None = None,
    page: int = 0,
    size: int = 50,
) -> Any:
    """Transactions in a date range, newest first.

    Args:
        date_from: ISO date (YYYY-MM-DD) to start from. Omitted, the API uses the first of the
            current month — not the beginning of time. Pass a date to reach further back.
        date_to: ISO date to stop at. Omitted, the API uses today.
        page: zero-based page number.
        size: rows per page; the API caps it at 200.

    Returns ``transactions`` along with ``total`` and ``has_more`` for paging.

    ``signedAmount`` is the one to sum: negative for money out, positive for money in, for every
    account type. Deleted rows are never included — use ``list_deleted_transactions`` for those.
    """
    return _page(
        _guard(
            lambda: client().get(
                "/api/v1/transactions",
                **{"from": date_from, "to": date_to, "page": page, "size": size},
            )
        ),
        page,
        size,
    )


@mcp.tool(annotations=READS)
def review_queue(page: int = 0, size: int = 50) -> Any:
    """Transactions that still need a category.

    Transfers are deliberately excluded: an uncategorized transfer is correct, not pending, and
    giving one a spending category would double-count the money.

    Returns ``transactions`` along with ``total`` and ``has_more`` for paging.
    """
    return _page(
        _guard(lambda: client().get("/api/v1/transactions/review", page=page, size=size)),
        page,
        size,
    )


@mcp.tool(annotations=READS)
def spending(date_from: str | None = None, date_to: str | None = None) -> Any:
    """Spending by category against its budget target, per month.

    Args:
        date_from: ISO date; defaults to the start of the current month on the server.
        date_to: ISO date; defaults to today.

    Returns one row per category per month, with ``netAmount`` spent, the ``targetAmount`` if a
    budget is set, and what ``remaining``.
    """
    return _guard(
        lambda: client().get(
            "/api/v1/reports/spend-vs-target", **{"from": date_from, "to": date_to}
        )
    )


@mcp.tool(annotations=READS)
def reconciliation() -> Any:
    """Statement closing balances against what the ledger computes.

    A non-zero ``difference`` means a transaction is missing, duplicated, or has the wrong amount —
    it is the strongest signal that an import went wrong. ``baseline`` says whether the computed
    figure started from the statement's own opening balance (a difference is real) or summed the
    whole history (a difference may only mean earlier history was never imported).
    """
    return _guard(lambda: client().get("/api/v1/reports/reconciliation"))


@mcp.tool(annotations=READS)
def monthly_totals(date_from: str | None = None, date_to: str | None = None) -> Any:
    """What moved each month, categorized or not: money out, money in, and how much of the month
    is still uncategorized.

    Every figure from ``spending`` is categorized spend only, so a month where most rows are still
    in the review queue looks cheap there. This is the denominator. Transfers are excluded;
    ``moneyIn`` includes refunds, which is why it is not called income. The row with a null
    ``ledgerEntityId`` is the combined figure.

    Args:
        date_from: ISO date; defaults to the start of the current month.
        date_to: ISO date; defaults to today.
    """
    return _guard(
        lambda: client().get("/api/v1/reports/monthly-totals", **{"from": date_from, "to": date_to})
    )


@mcp.tool(annotations=REMOVES)
def delete_checkpoint(statement_id: int) -> Any:
    """Remove a reconciliation checkpoint — a statement's closing balance — by its ``statementId``
    from ``reconciliation``.

    For a checkpoint no file will ever replace: a re-import replaces one it disagrees with on its
    own. This is a real delete, not a soft one; a checkpoint is metadata about the ledger, not
    money in it, and importing the statement again recreates it.
    """
    result = _guard(lambda: client().delete(f"/api/v1/statements/{statement_id}"))
    if isinstance(result, dict) and "error" in result:
        return result
    return {"deleted_checkpoint": statement_id}


@mcp.tool(annotations=READS)
def import_history() -> Any:
    """Past statement imports: file, status, rows applied, and any warning recorded."""
    return _guard(lambda: client().get("/api/v1/imports"))


@mcp.tool(annotations=READS)
def list_holdings(account_id: int | None = None) -> Any:
    """What is owned — the most recent positions snapshot, largest position first.

    Args:
        account_id: limit to one account. Omit for everything.

    Each row carries ``asOf``, the date of the snapshot it came from. A market value is only as
    current as the last positions file imported, so check it before quoting a figure as today's.

    ``marketValue`` is always positive here — a holding is a magnitude, not a signed ledger amount.
    Cash and money-market rows (``cash: true``) are included, because a brokerage's uninvested cash
    is part of what the account is worth.
    """
    path = "/api/v1/holdings" if account_id is None else f"/api/v1/holdings/account/{account_id}"
    return _guard(lambda: client().get(path))


@mcp.tool(annotations=READS)
def list_deleted_transactions(page: int = 0, size: int = 100) -> Any:
    """Transactions that have been deleted, most recently deleted first.

    Deletion here is soft, so anything listed can be brought back with ``restore_transaction``.

    Returns ``transactions`` along with ``total`` and ``has_more`` for paging. This endpoint
    answers a bare page rather than a count, so ``total`` is the rows on this page and ``has_more``
    is true whenever the page came back full — ask for the next page to be sure.
    """
    return _page(
        _guard(lambda: client().get("/api/v1/transactions/deleted", page=page, size=size)),
        page,
        size,
    )


# ---------------------------------------------------------------------------------------------
# Market (M7a). A price is not money: nothing here touches the ledger or a balance.
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=READS)
def market_status() -> Any:
    """Whether quotes can be had at all: which provider is configured, whether the API polls on a
    timer, whether a notification channel is set, and when quotes were last fetched."""
    return _guard(lambda: client().get("/api/v1/market/status"))


@mcp.tool(annotations=READS)
def watchlist() -> Any:
    """Symbols being watched, held or not."""
    return _guard(lambda: client().get("/api/v1/market/watchlist"))


@mcp.tool(annotations=WRITES)
def watch_symbol(symbol: str, note: str | None = None) -> Any:
    """Start watching a ticker. It is quoted on every refresh and can carry price alerts.

    Watching a symbol is not owning it: the watchlist is separate from holdings, which arrive only
    from a positions import.
    """
    return _guard(
        lambda: client().post("/api/v1/market/watchlist", {"symbol": symbol, "note": note})
    )


@mcp.tool(annotations=REMOVES)
def unwatch_symbol(watchlist_id: int) -> Any:
    """Stop watching, by the ``id`` from ``watchlist``. Alerts on the symbol are kept."""
    result = _guard(lambda: client().delete(f"/api/v1/market/watchlist/{watchlist_id}"))
    if isinstance(result, dict) and "error" in result:
        return result
    return {"unwatched": watchlist_id}


@mcp.tool(annotations=READS)
def quotes() -> Any:
    """The latest quote for every watched or held symbol, with today's move.

    ``changePct`` is against the previous close. ``source`` says where the price came from; a
    quote from the ``fake`` provider is a stand-in for a stack with no vendor and must never be
    presented as a market price. ``asOf`` is the vendor's timestamp for the trade.
    """
    return _guard(lambda: client().get("/api/v1/market/quotes"))


@mcp.tool(annotations=WRITES)
def refresh_quotes() -> Any:
    """Fetch quotes now rather than waiting for the timer, and evaluate every alert against them.

    Returns how many were fetched and stored, the provider's warnings (a symbol it does not know
    is a warning, never a silent gap), and how many alerts fired. A 503 means no market-data
    provider is configured — a state, not a fault.
    """
    return _guard(lambda: client().post("/api/v1/market/quotes/refresh", {}))


@mcp.tool(annotations=READS)
def holdings_at_market() -> Any:
    """Every position in the latest snapshots valued two ways: as the positions file said
    (``snapshotValue``, on ``snapshotAsOf``) and at the latest quote (``liveValue``).

    The second is a moment; the first is what the broker asserted. Account balances and net worth
    use the snapshot and never the quote — say so if asked why the two differ.
    """
    return _guard(lambda: client().get("/api/v1/market/holdings"))


@mcp.tool(annotations=READS)
def list_price_alerts() -> Any:
    """Every price alert, active or paused, with whether it is currently armed."""
    return _guard(lambda: client().get("/api/v1/market/alerts"))


@mcp.tool(annotations=WRITES)
def set_price_alert(symbol: str, rule: str, threshold: str, note: str | None = None) -> Any:
    """Add a price alert on a ticker.

    Args:
        symbol: the ticker.
        rule: ``above`` (price crosses above threshold), ``below`` (crosses below), or
            ``pct_move`` (moved more than threshold percent against the previous close today).
        threshold: a price for above/below, a percentage for pct_move. As a string, never a float.
        note: why, for the person reading the list later.

    An alert fires once when its condition becomes true and re-arms when it is false again; a
    percent-move alert also re-arms each trading day. Firing is pushed to the configured
    notification channel and always recorded, deliverable or not.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/market/alerts",
            {"symbol": symbol, "rule": rule, "threshold": threshold, "note": note},
        )
    )


@mcp.tool(annotations=WRITES)
def pause_price_alert(alert_id: int, active: bool) -> Any:
    """Pause (``active=false``) or resume an alert. Resuming re-arms it."""
    return _guard(
        lambda: client().put(f"/api/v1/market/alerts/{alert_id}/active", {"active": active})
    )


@mcp.tool(annotations=REMOVES)
def delete_price_alert(alert_id: int) -> Any:
    """Remove an alert and its history of firings."""
    result = _guard(lambda: client().delete(f"/api/v1/market/alerts/{alert_id}"))
    if isinstance(result, dict) and "error" in result:
        return result
    return {"deleted_alert": alert_id}


@mcp.tool(annotations=READS)
def alert_events(size: int = 50) -> Any:
    """Recent alert firings, newest first, each with the price that fired it and whether the
    notification was delivered (and why not, when it was not)."""
    return _guard(lambda: client().get("/api/v1/market/alerts/events", size=size))


# ---------------------------------------------------------------------------------------------
# Orders (M7b, D-18). Propose, then confirm by restating — and only with the person's explicit
# go-ahead in the conversation. Nothing here is a ledger row.
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=READS)
def trading_status() -> Any:
    """Whether orders can be sent: the kill switch, the daily cap and what today has used of it,
    and what the broker says about itself (paper or not, market open, buying power)."""
    return _guard(lambda: client().get("/api/v1/orders/status"))


@mcp.tool(annotations=READS)
def list_orders(size: int = 50) -> Any:
    """Recent orders, newest first, with status, sizing, broker ids and fills."""
    return _guard(lambda: client().get("/api/v1/orders", size=size))


@mcp.tool(annotations=WRITES)
def propose_order(
    symbol: str,
    side: str,
    quantity: str,
    order_type: str = "market",
    limit_price: str | None = None,
    venue: str = "paper",
    rationale: str | None = None,
) -> Any:
    """Draft an order. Nothing is sent: a draft is sized against the latest quote (or the limit
    price) and waits for a confirmation that restates it.

    Args:
        symbol: the ticker.
        side: ``buy`` or ``sell``.
        quantity: shares, as a string — never a float.
        order_type: ``market`` or ``limit``.
        limit_price: required for a limit order, as a string.
        venue: ``paper`` sends to the paper broker on confirmation; ``manual`` is a ticket the
            person places at Fidelity by hand and marks placed afterwards.
        rationale: why. It is recorded on the order and read by the person before confirming.

    The proposer is recorded as the assistant. Say what you proposed and why, then stop: the
    person confirms, not you, unless they have told you in so many words to confirm on their behalf.
    """
    body = {
        "symbol": symbol,
        "side": side,
        "quantity": quantity,
        "orderType": order_type,
        "limitPrice": limit_price,
        "venue": venue,
        "proposedBy": "assistant",
        "rationale": rationale,
    }
    return _guard(lambda: client().post("/api/v1/orders", body))


@mcp.tool(annotations=REMOVES)
def confirm_order(
    order_id: int, symbol: str, side: str, quantity: str, limit_price: str | None = None
) -> Any:
    """Confirm a draft by restating it exactly — symbol, side, quantity, and the limit price if it
    has one. A restatement that does not match the draft confirms nothing.

    THIS SENDS A REAL ORDER to the paper broker (or marks a manual ticket confirmed) when trading
    is on. Call it only when the person has explicitly told you to confirm this specific order.
    After the restatement come two more gates you do not control: the kill switch
    (TRADING_ENABLED) and the daily notional cap; either refuses with a sentence.
    """
    body = {
        "symbol": symbol,
        "side": side,
        "quantity": quantity,
        "limitPrice": limit_price,
        "actor": "assistant",
    }
    return _guard(lambda: client().post(f"/api/v1/orders/{order_id}/confirm", body))


@mcp.tool(annotations=REMOVES)
def cancel_order(order_id: int) -> Any:
    """Cancel a draft here, or an open order at the broker."""
    return _guard(
        lambda: client().post(f"/api/v1/orders/{order_id}/cancel", {"actor": "assistant"})
    )


@mcp.tool(annotations=WRITES)
def mark_order_placed(order_id: int, fill_price: str | None = None) -> Any:
    """Record that a manual ticket was placed at Fidelity by hand, with the fill price if known.
    Only the person knows this happened; call it when they say so."""
    return _guard(
        lambda: client().post(
            f"/api/v1/orders/{order_id}/placed", {"fillPrice": fill_price, "actor": "assistant"}
        )
    )


@mcp.tool(annotations=WRITES)
def sync_orders() -> Any:
    """Ask the broker about every open order now rather than waiting for the timer."""
    return _guard(lambda: client().post("/api/v1/orders/sync", {}))


@mcp.tool(annotations=READS)
def order_events(order_id: int) -> Any:
    """The audit trail of one order: who moved it from what to what, and why."""
    return _guard(lambda: client().get(f"/api/v1/orders/{order_id}/events"))


# ---------------------------------------------------------------------------------------------
# Strategies and backtests (M7c, D-19). A backtest is a claim with its doubts attached; a saved
# strategy that is on proposes drafts on a timer and never trades by itself.
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=READS)
def strategy_catalog() -> Any:
    """The strategies the system knows — kind, what it does, whether it needs intraday bars, and
    each parameter with its default and bounds. Read this before running a backtest."""
    return _guard(lambda: client().get("/api/v1/strategies/catalog"))


@mcp.tool(annotations=WRITES)
def run_backtest(
    kind: str,
    symbol: str,
    timeframe: str,
    start: str,
    end: str,
    params: dict[str, str] | None = None,
    initial_cash: str = "10000",
    slippage_bps: str = "5",
    commission: str = "0",
    out_of_sample_fraction: str = "0.3",
) -> Any:
    """Run a strategy over history and keep the result.

    Args:
        kind: a kind from ``strategy_catalog``.
        symbol: the ticker.
        timeframe: 1Min, 5Min, 15Min, 1Hour or 1Day. Intraday strategies refuse 1Day.
        start: first day, YYYY-MM-DD.
        end: last day, YYYY-MM-DD.
        params: parameter name → value as a string; defaults fill the rest.
        initial_cash: starting cash, as a string.
        slippage_bps: basis points paid on every fill. Zero is a lie the result will name.
        commission: dollars per order.
        out_of_sample_fraction: the last part of the period held back and scored separately.

    Read ``warnings`` first and repeat them to the person: fake bars, too few trades, buy and
    hold won, in-sample beat out-of-sample, the pattern day trader rule. A backtest is a claim,
    not a forecast; never present its return without the buy-and-hold return beside it.
    """
    body = {
        "kind": kind,
        "symbol": symbol,
        "timeframe": timeframe,
        "start": start,
        "end": end,
        "params": params or {},
        "initialCash": initial_cash,
        "slippageBps": slippage_bps,
        "commission": commission,
        "outOfSampleFraction": out_of_sample_fraction,
    }
    return _guard(lambda: client().post("/api/v1/backtests", body))


@mcp.tool(annotations=READS)
def list_backtests(size: int = 20) -> Any:
    """Past backtests, newest first: the rule, the period, the strategy's return beside buy and
    hold, the held-out return, trades, and how many things there are to doubt."""
    return _guard(lambda: client().get("/api/v1/backtests", size=size))


@mcp.tool(annotations=READS)
def backtest_detail(backtest_id: int) -> Any:
    """One backtest whole: metrics, the in-sample and out-of-sample halves, the equity curve, every
    trade with its reasons, and the warnings."""
    return _guard(lambda: client().get(f"/api/v1/backtests/{backtest_id}"))


@mcp.tool(annotations=REMOVES)
def delete_backtest(backtest_id: int) -> Any:
    """Remove a kept backtest."""
    return _guard(lambda: client().delete(f"/api/v1/backtests/{backtest_id}"))


@mcp.tool(annotations=READS)
def list_strategies() -> Any:
    """Saved strategies: rule, symbol, bars, shares per signal, whether it is on, and what it said
    the last time it was asked."""
    return _guard(lambda: client().get("/api/v1/strategies"))


@mcp.tool(annotations=WRITES)
def save_strategy(
    name: str,
    kind: str,
    symbol: str,
    timeframe: str,
    quantity: str,
    params: dict[str, str] | None = None,
    notes: str | None = None,
) -> Any:
    """Save a rule as a strategy. It is OFF until switched on; on, it proposes DRAFT orders of
    ``quantity`` shares on a timer, which the person confirms. It never trades by itself.

    Args:
        quantity: shares per signal, as a string. The size is the person's choice, not yours;
            ask them unless they have said.
    """
    body = {
        "name": name,
        "kind": kind,
        "symbol": symbol,
        "timeframe": timeframe,
        "quantity": quantity,
        "params": params or {},
        "notes": notes,
    }
    return _guard(lambda: client().post("/api/v1/strategies", body))


@mcp.tool(annotations=WRITES)
def set_strategy_active(strategy_id: int, active: bool) -> Any:
    """Switch a strategy on or off. On means it is asked for its opinion on a timer and a signal
    becomes a draft order with a notification; confirming the draft is still the person's act.
    Switch one on only when the person has said to."""
    return _guard(
        lambda: client().put(f"/api/v1/strategies/{strategy_id}/active", {"active": active})
    )


@mcp.tool(annotations=REMOVES)
def delete_strategy(strategy_id: int) -> Any:
    """Remove a saved strategy. Its past backtests and orders stay."""
    return _guard(lambda: client().delete(f"/api/v1/strategies/{strategy_id}"))


@mcp.tool(annotations=WRITES)
def evaluate_strategies() -> Any:
    """Ask every active strategy for its opinion now rather than waiting for the timer. Returns
    what each one did: no signal, already proposed, waiting on an order, or a new draft's id.
    Nothing is sent to a broker by this call."""
    return _guard(lambda: client().post("/api/v1/strategies/evaluate", {}))


# ---------------------------------------------------------------------------------------------
# The app speaks up (M8, D-20): what needs a look, and reminders.
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=READS)
def needs_a_look() -> Any:
    """What the app has noticed that needs a person: statements the ledger disagrees with,
    orders waiting on a decision or refused, reminders coming due, categories over their target
    this month, accounts nobody has imported for a while, undelivered alerts, strategies that could
    not be evaluated. Each item is a sentence with the number in it, worst first, and a screen to
    go to. The same list the daily digest pushes. Start a session here."""
    return _guard(lambda: client().get("/api/v1/digest/preview"))


@mcp.tool(annotations=WRITES)
def send_digest() -> Any:
    """Push the needs-a-look list to the person's phone now (ntfy), even if all is quiet. The run
    is recorded either way; ``sent`` false with ``deliveryError`` says why not."""
    return _guard(lambda: client().post("/api/v1/digest/send", {}))


@mcp.tool(annotations=READS)
def digest_settings() -> Any:
    """When the daily digest goes out and what counts as stale: digestEnabled, digestTime (HH:mm
    in the app's zone), staleAfterDays, draftWaitHours, quietWhenEmpty."""
    return _guard(lambda: client().get("/api/v1/digest/settings"))


@mcp.tool(annotations=WRITES)
def set_digest_settings(
    digest_enabled: bool | None = None,
    digest_time: str | None = None,
    stale_after_days: int | None = None,
    draft_wait_hours: int | None = None,
    quiet_when_empty: bool | None = None,
) -> Any:
    """Change the digest's timing or thresholds. Only the fields given change."""
    body = {
        "digestEnabled": digest_enabled,
        "digestTime": digest_time,
        "staleAfterDays": stale_after_days,
        "draftWaitHours": draft_wait_hours,
        "quietWhenEmpty": quiet_when_empty,
    }
    return _guard(lambda: client().put("/api/v1/digest/settings", body))


@mcp.tool(annotations=READS)
def list_reminders() -> Any:
    """Reminders, active first by due date: title, due date, cadence, lead days, amount."""
    return _guard(lambda: client().get("/api/v1/reminders"))


@mcp.tool(annotations=WRITES)
def add_reminder(
    title: str,
    due_on: str,
    cadence: str = "once",
    lead_days: int = 3,
    amount: str | None = None,
    notes: str | None = None,
) -> Any:
    """Add a dated reminder — estimated taxes, an LLC filing, a bill.

    Args:
        due_on: YYYY-MM-DD.
        cadence: once, weekly, monthly, quarterly or yearly. Done on a recurring one moves it
            to its next occurrence.
        lead_days: how many days before the due date the digest starts mentioning it.
        amount: optional, as a string.
    """
    body = {
        "title": title,
        "dueOn": due_on,
        "cadence": cadence,
        "leadDays": lead_days,
        "amount": amount,
        "notes": notes,
    }
    return _guard(lambda: client().post("/api/v1/reminders", body))


@mcp.tool(annotations=WRITES)
def complete_reminder(reminder_id: int) -> Any:
    """Mark a reminder done. A one-off is finished; a recurring one advances to its next date."""
    return _guard(lambda: client().post(f"/api/v1/reminders/{reminder_id}/done", {}))


@mcp.tool(annotations=REMOVES)
def delete_reminder(reminder_id: int) -> Any:
    """Remove a reminder entirely. To stop being nagged for now, complete it instead."""
    return _guard(lambda: client().delete(f"/api/v1/reminders/{reminder_id}"))


# ---------------------------------------------------------------------------------------------
# Cash flow and net worth history (M9, D-21).
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=READS)
def recurring_charges(days: int = 30) -> Any:
    """Recurring charges found in the ledger (three or more occurrences at a steady interval),
    each with its evidence — cadence, typical amount, how many times, last seen, next expected —
    and a status: upcoming, on track, or missing. Also what is expected in the next ``days`` with
    totals in and out, and anomalies: possible double charges and amounts far from usual. Nothing
    is declared by hand; transfers are left out."""
    return _guard(lambda: client().get("/api/v1/cashflow", days=days))


@mcp.tool(annotations=READS)
def net_worth_history(days: int = 90) -> Any:
    """What net worth was on each day a snapshot exists, combined (ledgerEntityId null) and per set
    of books. Snapshots are taken once a day by the housekeeping tick; history starts when that
    started, not before."""
    return _guard(lambda: client().get("/api/v1/reports/net-worth/history", days=days))


@mcp.tool(annotations=WRITES)
def snapshot_net_worth() -> Any:
    """Take today's net worth snapshot now. Taking it twice in a day replaces the first."""
    return _guard(lambda: client().post("/api/v1/reports/net-worth/snapshot", {}))


# ---------------------------------------------------------------------------------------------
# Writing
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=WRITES)
def create_entity(name: str, kind: str) -> Any:
    """Create a set of books.

    Args:
        name: what to call it, e.g. "Feeling Froggy LLC".
        kind: ``personal`` or ``business``.
    """
    return _guard(lambda: client().post("/api/v1/entities", {"name": name, "kind": kind}))


@mcp.tool(annotations=WRITES)
def create_account(
    name: str,
    account_type: str,
    ledger_entity_id: int,
    mask: str | None = None,
    currency: str | None = None,
    external_id: str | None = None,
) -> Any:
    """Create an account.

    Args:
        name: the account's name, e.g. "CACU Checking".
        account_type: one of ``checking``, ``savings``, ``credit_card``, ``brokerage``, ``loan``,
            ``cash``.
        ledger_entity_id: which set of books it belongs to — see ``list_entities``.
        mask: last four digits, for recognising it. Never the full account number.
        currency: ISO code, defaults to USD.
        external_id: the ``key`` an import reported under ``unlinkedAccounts``. It is an opaque
            link derived from the file, never an account number, and it is what lets that import —
            and every later one — match its rows to this account exactly. Pass it together with
            the reported ``mask`` and ``name`` when creating an account a file asked for.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/accounts",
            {
                "name": name,
                "accountType": account_type,
                "ledgerEntityId": ledger_entity_id,
                "mask": mask,
                "currency": currency,
                "externalId": external_id,
            },
        )
    )


@mcp.tool(annotations=WRITES)
def create_category(name: str, kind: str, parent_id: int | None = None) -> Any:
    """Create a category.

    Args:
        name: e.g. "Groceries".
        kind: ``expense`` or ``income``.
        parent_id: optional parent, for a sub-category.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/categories", {"name": name, "kind": kind, "parentId": parent_id}
        )
    )


@mcp.tool(annotations=WRITES)
def set_target(
    category_id: int,
    ledger_entity_id: int,
    amount: str,
    cadence: str | None = None,
    effective_from: str | None = None,
    note: str | None = None,
) -> Any:
    """Set a budget target for a category.

    Args:
        category_id: the category to budget.
        ledger_entity_id: which set of books.
        amount: a decimal string such as "450.00". Pass a string, never a float — binary floats
            cannot represent most money amounts exactly.
        cadence: ``weekly``, ``monthly``, ``quarterly`` or ``yearly``. Defaults to monthly.
        effective_from: ISO date this target starts applying.
        note: free text.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/targets",
            {
                "categoryId": category_id,
                "ledgerEntityId": ledger_entity_id,
                "amount": amount,
                "cadence": cadence,
                "effectiveFrom": effective_from,
                "note": note,
            },
        )
    )


@mcp.tool(annotations=WRITES)
def record_transaction(
    account_id: int,
    transaction_date: str,
    amount: str,
    direction: str,
    description: str,
    category_id: int | None = None,
    transfer: bool = False,
    transfer_account_id: int | None = None,
) -> Any:
    """Record a transaction by hand.

    Args:
        account_id: the account it happened on.
        transaction_date: ISO date (YYYY-MM-DD).
        amount: a positive decimal string, e.g. "84.31". Always positive — the sign comes from
            ``direction``. Pass a string, never a float.
        direction: ``debit`` for money leaving the account, ``credit`` for money arriving. A debit
            is negative for every account type, including credit cards.
        description: what it was.
        category_id: optional. Leave unset to put it in the review queue.
        transfer: set true when moving money between two accounts the user already owns — paying a
            credit card, moving to savings, funding the brokerage. This writes *both* legs, so the
            other account is updated too, and keeps the amount out of spending totals. Categorizing
            it instead would double-count money that was already charged when it was spent.
        transfer_account_id: the other account. Required when ``transfer`` is true; setting it
            without ``transfer`` records an ordinary one-sided transaction.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/transactions",
            {
                "accountId": account_id,
                "transactionDate": transaction_date,
                "amount": amount,
                "direction": direction,
                "description": description,
                "categoryId": category_id,
                "transfer": transfer or None,
                "transferAccountId": transfer_account_id,
            },
        )
    )


@mcp.tool(annotations=WRITES)
def categorize_transaction(transaction_id: int, category_id: int | None) -> Any:
    """Set or clear a transaction's category.

    Pass ``category_id`` as null to clear it. Transfers cannot be categorized and will be refused —
    that is the double-counting guard doing its job, not an error to work around.
    """
    return _guard(
        lambda: client().put(
            f"/api/v1/transactions/{transaction_id}/category", {"categoryId": category_id}
        )
    )


@mcp.tool(annotations=REMOVES)
def delete_transaction(transaction_id: int) -> Any:
    """Delete a transaction.

    The deletion is soft and fully reversible with ``restore_transaction`` — nothing is destroyed.
    Deleting one leg of a transfer removes both, since half a transfer would show money leaving one
    account without arriving anywhere.
    """
    result = _guard(lambda: client().delete(f"/api/v1/transactions/{transaction_id}"))
    if isinstance(result, dict) and "error" in result:
        return result
    # Reversibility is the API's contract, not this tool's guess: DELETE on a transaction is a
    # soft delete by design (TransactionController), and the dedupe key stays claimed so a
    # re-import cannot resurrect it either. `restore_transaction` is the proof.
    return {
        "deleted": transaction_id,
        "reversible": True,
        "undo_with": f"restore_transaction({transaction_id})",
    }


@mcp.tool(annotations=WRITES)
def set_transfer(transaction_id: int, transfer: bool) -> Any:
    """Mark a transaction as a transfer, or say it is not one after all.

    A transfer is money moving between the user's own accounts and is never spending, so marking
    one clears its category. Un-marking is for a row the importer or a rule called a transfer
    wrongly — a Zelle to a plumber, a wire to a contractor — so it can be categorized again. Only
    single-sided rows can be un-marked: a manual transfer has two legs and is removed with
    ``delete_transaction`` instead.
    """
    return _guard(
        lambda: client().put(
            f"/api/v1/transactions/{transaction_id}/transfer", {"transfer": transfer}
        )
    )


@mcp.tool(annotations=WRITES)
def restore_transaction(transaction_id: int) -> Any:
    """Undo a delete, bringing the transaction back exactly as it was.

    Restores every leg removed by that same delete, so an undone transfer is whole again. Returns
    404 if the id is not a deleted transaction — including when it has already been restored.
    """
    return _guard(lambda: client().post(f"/api/v1/transactions/{transaction_id}/restore"))


# Where statement files are allowed to come from. Downloads and Desktop, the repo's own ignored
# drop folders, and anything named in FINANCES_IMPORT_ROOTS (colon-separated). Everything is
# resolved before it is compared, so a symlink or a ../ that lands outside is refused on where it
# lands, not on how it was written.
#
# This exists because the first version would read and upload any file on the machine. The threat
# is not the person at the keyboard; it is that statement descriptions are merchant-typed text
# that flows into the model's context, and the model chooses the path.
_STATEMENT_SUFFIXES = frozenset({".csv", ".ofx", ".qfx"})
_MAX_STATEMENT_BYTES = 10 * 1024 * 1024  # matches the API's own limit


def _allowed_roots() -> list[Path]:
    home = Path.home()
    roots = [home / "Downloads", home / "Desktop"]
    repo = env.find_env_file(Path(__file__).resolve().parent)
    if repo is not None:
        roots += [repo.parent / "statements", repo.parent / "imports"]
    for extra in os.environ.get("FINANCES_IMPORT_ROOTS", "").split(":"):
        if extra.strip():
            roots.append(Path(extra).expanduser())
    return [r.resolve() for r in roots if r.exists()]


def _within_allowed(path: Path) -> bool:
    resolved = path.resolve()
    return any(resolved == root or root in resolved.parents for root in _allowed_roots())


def _statement_path(file_path: str) -> Path | dict[str, Any]:
    """A readable statement file inside the allowed folders, or an error to hand back."""
    path = Path(file_path).expanduser()
    if not _within_allowed(path):
        return {
            "error": (
                f"{path} is outside the folders statements may be read from "
                f"({', '.join(str(r) for r in _allowed_roots())}). Move the file into one of "
                "them, or set FINANCES_IMPORT_ROOTS."
            ),
            "status": 400,
        }
    if not path.is_file():
        return {"error": f"No file at {path}", "status": 400}
    if path.suffix.lower() not in _STATEMENT_SUFFIXES:
        return {"error": f"{path.name} is not a statement file (csv, ofx or qfx).", "status": 400}
    if path.stat().st_size > _MAX_STATEMENT_BYTES:
        return {"error": f"{path.name} is larger than 10 MB, which no statement is.", "status": 400}
    return path


@mcp.tool(annotations=WRITES)
def import_statement(file_path: str, account_id: int | None = None) -> Any:
    """Import a statement file (CSV, OFX or QFX) from this machine.

    Args:
        file_path: path to the file, e.g. "~/Downloads/cacu.csv". Expanded, so ``~`` works.
        account_id: which account the rows belong to. Optional for exports that name an account on
            every row, such as a Fidelity history covering the whole portfolio; required for a
            single-account statement, which carries nothing to identify itself by.

    Safe to repeat: rows already present are counted as duplicates and skipped, so importing the
    same file twice is a no-op rather than a doubling. If the file names accounts that do not exist
    here, the result lists them under ``unlinkedAccounts`` (``key``, ``mask``, ``name``,
    ``transactionCount``) and nothing is guessed. Create each one with ``create_account`` passing
    that ``key`` as ``external_id`` along with the ``mask`` and ``name`` (a Fidelity history names
    brokerage accounts), then import the same file again.
    """
    path = _statement_path(file_path)
    if isinstance(path, dict):
        return path
    return _guard(
        lambda: client().upload("/api/v1/imports", path.name, path.read_bytes(), account_id)
    )


@mcp.tool(annotations=WRITES)
def import_positions(file_path: str) -> Any:
    """Import a brokerage positions export (a holdings snapshot) from this machine.

    Args:
        file_path: path to the file, e.g. "~/Downloads/Portfolio_Positions_Aug-27-2026.csv".

    A positions file is a *snapshot* of what is held, not a transaction history — it records no
    money movement and adds nothing to the ledger. Use ``import_statement`` for a transactions
    export such as Fidelity's Accounts History; the two files are different and are not
    interchangeable.

    Importing one changes how the account's balance is computed: from then on the account is worth
    its holdings' market value rather than the sum of cash paid in, which is the difference between
    a correct net worth and one understated by every dollar of growth.

    Safe to repeat. Re-importing the same file updates each position in place; a file downloaded on
    a later date lands as a new snapshot beside the old one, which is what gives a position any
    history at all.
    """
    path = _statement_path(file_path)
    if isinstance(path, dict):
        return path
    result = _guard(
        lambda: client().upload("/api/v1/imports/positions", path.name, path.read_bytes(), None)
    )
    return _positions_result(result)


def _positions_result(result: Any) -> Any:
    """Say what the count means. The API reuses the statement batch's ``duplicateCount`` for a
    positions import, where it counts holdings updated in place — nothing was skipped."""
    if isinstance(result, dict) and "duplicateCount" in result:
        result = dict(result)
        result["updatedCount"] = result.pop("duplicateCount")
        result["counts"] = (
            "appliedCount is new positions; updatedCount is positions refreshed in place"
        )
    return result


@mcp.tool(annotations=READS)
def find_statement_files(directory: str = "~/Downloads") -> Any:
    """List importable statement files in a directory, newest first.

    Args:
        directory: where to look. Defaults to the Downloads folder.

    Reports names, sizes and modification times of statement files only, and only within the
    folders statements may be read from (Downloads, Desktop, the repo's statements/ and imports/,
    and FINANCES_IMPORT_ROOTS). It does not open the files. Filenames can still say something —
    an employer or an institution — so it is limited to those folders rather than to the whole disk.
    """
    folder = Path(directory).expanduser()
    if not _within_allowed(folder):
        return {
            "error": f"{folder} is outside the folders statements may be read from.",
            "status": 400,
        }
    if not folder.is_dir():
        return {"error": f"No directory at {folder}", "status": 400}

    found = []
    for entry in folder.iterdir():
        try:
            if not entry.is_file() or entry.suffix.lower() not in _STATEMENT_SUFFIXES:
                continue
            stat = entry.stat()
        except OSError:
            # An entry we cannot stat is not a reason to fail the whole listing.
            continue
        found.append(
            {
                "path": str(entry),
                "name": entry.name,
                "bytes": stat.st_size,
                "modified": int(stat.st_mtime),
            }
        )
    return sorted(found, key=lambda item: item["modified"], reverse=True)


def main() -> None:
    """Entry point for `finances-mcp`, speaking MCP over stdio."""
    # The repo's .env is the single home for the token; Claude Code launches this with the editor's
    # environment, which knows nothing about it.
    env.load(Path(__file__).resolve().parent)

    # Checked at startup so a missing token fails here, where the message is visible, rather than
    # on the first tool call where it surfaces as an unexplained error mid-conversation.
    if not os.environ.get("LOCAL_API_TOKEN"):
        raise SystemExit(
            "LOCAL_API_TOKEN is not set, and no .env above this file defines it.\n"
            "Generate one with `make mcp-token`, which writes it to .env and tells you what to "
            "restart."
        )
    mcp.run()


if __name__ == "__main__":
    main()
