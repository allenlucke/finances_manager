"""The backtester (M7c, D-19).

A backtest is a claim, and this one is built to be hard on itself. A signal on one bar fills at
the *next* bar's open, never the close it was decided on. Every fill pays slippage and commission.
Position size is whole shares, all-in. Buy and hold over the same bars is computed with the same
costs and always reported beside the strategy. The last part of the period is held back and
scored separately, so a strategy tuned to the first part shows itself. And the result carries a
list of warnings a person is meant to read: fake bars, too few trades, buy-and-hold won, the
in-sample half beat the out-of-sample half, the pattern day trader rule.

Money — cash, equity, P&L — is ``Decimal`` throughout. The one float is the Sharpe ratio, a
dimensionless statistic.
"""

from __future__ import annotations

import math
import statistics
from dataclasses import dataclass, field
from decimal import ROUND_HALF_UP, Decimal

from finances_ai.market.strategies import (
    Position,
    Signal,
    Strategy,
    build_strategy,
    catalog_entry,
    resolve_params,
    session_days,
)
from finances_ai.models import (
    BacktestMetrics,
    BacktestRequest,
    BacktestResult,
    BacktestTrade,
    Bar,
    EquityPoint,
    EvaluateRequest,
    EvaluateResult,
)

BARS_PER_YEAR = {
    "1Min": 252 * 390,
    "5Min": 252 * 78,
    "15Min": 252 * 26,
    "1Hour": 252 * 7,
    "1Day": 252,
}
PCT = Decimal("0.01")
PRICE = Decimal("0.0001")
MAX_POINTS = 500
MAX_TRADES = 500
FEW_TRADES = 30
PDT_NOTE = (
    "{n} round trip(s) opened and closed in the same session. Under $25,000 of equity a margin "
    "account is limited to three day trades per five business days (FINRA's pattern day trader "
    "rule); a cash account is not, but its proceeds must settle before they can be reused."
)


class BacktestError(ValueError):
    """The request cannot be run as asked — too few bars, a strategy that needs intraday bars."""


def pct(ratio: Decimal) -> Decimal:
    return (ratio * 100).quantize(PCT, ROUND_HALF_UP)


@dataclass
class _Run:
    equity: list[Decimal]
    trades: list[BacktestTrade]
    bars_in_position: int
    benchmark_return: Decimal
    open_at_end: bool
    notes: list[str] = field(default_factory=list)


def _fill_price(bar_open: Decimal, side: str, slippage_bps: Decimal) -> Decimal:
    slip = slippage_bps / Decimal(10_000)
    factor = 1 + slip if side == "buy" else 1 - slip
    return (bar_open * factor).quantize(PRICE, ROUND_HALF_UP)


def _simulate(strategy: Strategy, bars: list[Bar], request: BacktestRequest) -> _Run:
    strategy.prepare(bars)
    warmup = strategy.warmup()
    days = session_days(bars)
    cash = request.initial_cash
    commission = request.commission_per_order
    quantity = 0
    cost_basis = Decimal(0)
    entered_index: int | None = None
    open_trade: BacktestTrade | None = None
    pending: Signal | None = None
    equity: list[Decimal] = []
    trades: list[BacktestTrade] = []
    notes: list[str] = []
    bars_in_position = 0
    said_too_small = False

    for i, bar in enumerate(bars):
        if pending is not None:
            price = _fill_price(bar.open, pending.action, request.slippage_bps)
            if pending.action == "buy":
                affordable = int((cash - commission) / price) if price > 0 else 0
                if affordable <= 0:
                    if not said_too_small:
                        notes.append(
                            f"Cash could not buy one share at {price} on {bar.ts.date()}; the "
                            "signal was skipped."
                        )
                        said_too_small = True
                else:
                    quantity = affordable
                    cost_basis = quantity * price + commission
                    cash -= cost_basis
                    entered_index = i
                    open_trade = BacktestTrade(
                        entered_at=bar.ts,
                        quantity=quantity,
                        entry_price=price,
                        reason_in=pending.reason,
                    )
            elif quantity > 0 and open_trade is not None:
                proceeds = quantity * price - commission
                cash += proceeds
                pnl = proceeds - cost_basis
                trades.append(
                    open_trade.model_copy(
                        update={
                            "exited_at": bar.ts,
                            "exit_price": price,
                            "pnl": pnl.quantize(PCT, ROUND_HALF_UP),
                            "return_pct": pct(pnl / cost_basis),
                            "reason_out": pending.reason,
                            "same_day": days[i] == days[entered_index or i],
                        }
                    )
                )
                quantity = 0
                open_trade = None
                entered_index = None
            pending = None

        equity.append(cash + quantity * bar.close)
        if quantity > 0:
            bars_in_position += 1
        if i >= warmup and i + 1 < len(bars):
            signal = strategy.on_bar(i, Position(long=quantity > 0, entered_index=entered_index))
            if signal is not None and (
                (signal.action == "buy" and quantity == 0)
                or (signal.action == "sell" and quantity > 0)
            ):
                pending = signal

    open_at_end = quantity > 0
    if open_at_end and open_trade is not None:
        last = bars[-1]
        proceeds = quantity * last.close
        pnl = proceeds - cost_basis
        trades.append(
            open_trade.model_copy(
                update={
                    "exited_at": last.ts,
                    "exit_price": last.close,
                    "pnl": pnl.quantize(PCT, ROUND_HALF_UP),
                    "return_pct": pct(pnl / cost_basis),
                    "reason_out": "end of test: the open position is marked at the last close",
                    "same_day": days[-1] == days[entered_index or len(bars) - 1],
                }
            )
        )

    # Buy and hold from the first bar the strategy could have acted on, same costs.
    first = min(warmup + 1, len(bars) - 1)
    price = _fill_price(bars[first].open, "buy", request.slippage_bps)
    held = int((request.initial_cash - commission) / price) if price > 0 else 0
    if held <= 0:
        benchmark = Decimal(0)
        notes.append("Buy and hold could not afford one share either; its return is shown as 0%.")
    else:
        final = request.initial_cash - held * price - commission + held * bars[-1].close
        benchmark = final / request.initial_cash - 1

    return _Run(equity, trades, bars_in_position, benchmark, open_at_end, notes)


def _metrics(run: _Run, request: BacktestRequest) -> BacktestMetrics:
    equity = run.equity
    initial = request.initial_cash
    peak = equity[0] if equity else initial
    drawdown = Decimal(0)
    for value in equity:
        peak = max(peak, value)
        if peak > 0:
            drawdown = max(drawdown, (peak - value) / peak)
    closed = [t for t in run.trades if t.pnl is not None]
    wins = [t for t in closed if t.pnl is not None and t.pnl > 0]
    losses = [t for t in closed if t.pnl is not None and t.pnl < 0]
    gross_win = sum((t.pnl for t in wins), Decimal(0))
    gross_loss = -sum((t.pnl for t in losses), Decimal(0))
    returns = [
        float(equity[i] / equity[i - 1] - 1) for i in range(1, len(equity)) if equity[i - 1] > 0
    ]
    sharpe = None
    if len(returns) >= 2:
        spread = statistics.pstdev(returns)
        if spread > 0:
            sharpe = round(
                statistics.fmean(returns) / spread * math.sqrt(BARS_PER_YEAR[request.timeframe]), 2
            )
    return BacktestMetrics(
        bars=len(equity),
        trades=len(closed),
        total_return_pct=pct(equity[-1] / initial - 1) if equity else Decimal(0),
        benchmark_return_pct=pct(run.benchmark_return),
        max_drawdown_pct=pct(drawdown),
        win_rate_pct=pct(Decimal(len(wins)) / len(closed)) if closed else None,
        profit_factor=(gross_win / gross_loss).quantize(PCT, ROUND_HALF_UP)
        if gross_loss > 0
        else None,
        avg_trade_pct=(
            sum((t.return_pct for t in closed if t.return_pct is not None), Decimal(0))
            / len(closed)
        ).quantize(PCT, ROUND_HALF_UP)
        if closed
        else None,
        exposure_pct=pct(Decimal(run.bars_in_position) / len(equity)) if equity else Decimal(0),
        sharpe=sharpe,
        final_equity=equity[-1].quantize(PCT, ROUND_HALF_UP) if equity else initial,
        day_trades=sum(1 for t in closed if t.same_day),
    )


def _thin(bars: list[Bar], equity: list[Decimal]) -> list[EquityPoint]:
    step = max(1, math.ceil(len(equity) / MAX_POINTS))
    points = [
        EquityPoint(ts=bars[i].ts, equity=equity[i].quantize(PCT, ROUND_HALF_UP))
        for i in range(0, len(equity), step)
    ]
    if equity and (len(equity) - 1) % step != 0:
        points.append(EquityPoint(ts=bars[-1].ts, equity=equity[-1].quantize(PCT, ROUND_HALF_UP)))
    return points


def run_backtest(request: BacktestRequest, bars: list[Bar], provider: str) -> BacktestResult:
    params = resolve_params(request.strategy, request.params)
    info = catalog_entry(request.strategy)
    if info.intraday and request.timeframe == "1Day":
        raise BacktestError(
            f"{info.label} is an intraday strategy; choose a timeframe under a day."
        )
    strategy = build_strategy(request.strategy, params)
    needed = strategy.warmup() + 3
    if len(bars) < needed:
        raise BacktestError(
            f"Only {len(bars)} bars for {request.symbol} at {request.timeframe} between "
            f"{request.start} and {request.end}; {info.label} needs at least {needed}."
        )

    full = _simulate(strategy, bars, request)
    metrics = _metrics(full, request)
    warnings: list[str] = list(full.notes)

    in_sample = out_of_sample = None
    if request.out_of_sample_fraction > 0:
        split = int(len(bars) * (1 - request.out_of_sample_fraction))
        if split >= needed and len(bars) - split >= needed:
            in_sample = _metrics(
                _simulate(build_strategy(request.strategy, params), bars[:split], request), request
            )
            out_of_sample = _metrics(
                _simulate(build_strategy(request.strategy, params), bars[split:], request), request
            )
        else:
            warnings.append("Too few bars to hold back an out-of-sample part; none was.")

    if provider == "fake":
        warnings.insert(
            0,
            "These bars are fake: a deterministic random walk from the fake provider. The result "
            "says nothing about any market.",
        )
    elif provider == "alpaca" and request.timeframe != "1Day":
        warnings.append(
            "Bars are from the IEX feed only, a fraction of consolidated volume; quiet symbols "
            "look quieter than they are, and fills at these prices are optimistic."
        )
    if metrics.trades < FEW_TRADES:
        warnings.append(f"Only {metrics.trades} closed trade(s): the statistics are mostly noise.")
    if metrics.benchmark_return_pct >= metrics.total_return_pct:
        warnings.append(
            f"Buy and hold returned {metrics.benchmark_return_pct}% over the same bars; the "
            f"strategy returned {metrics.total_return_pct}%."
        )
    if (
        in_sample is not None
        and out_of_sample is not None
        and in_sample.total_return_pct > 0
        and out_of_sample.total_return_pct < in_sample.total_return_pct / 2
    ):
        held = pct(request.out_of_sample_fraction)
        warnings.append(
            f"In-sample {in_sample.total_return_pct}% but out-of-sample "
            f"{out_of_sample.total_return_pct}%: what worked on the first {100 - held}% of the "
            f"period did not carry to the last {held}%. Overfitting is the usual reason."
        )
    if metrics.day_trades > 0:
        warnings.append(PDT_NOTE.format(n=metrics.day_trades))
    if request.slippage_bps == 0:
        warnings.append("Zero slippage assumed; real fills are worse.")
    if full.open_at_end:
        warnings.append("The last position was still open and is marked at the final close.")
    if len(full.trades) > MAX_TRADES:
        warnings.append(f"Only the last {MAX_TRADES} of {len(full.trades)} trades are listed.")

    return BacktestResult(
        provider=provider,
        strategy=request.strategy,
        params=params,
        symbol=request.symbol,
        timeframe=request.timeframe,
        start=request.start,
        end=request.end,
        metrics=metrics,
        in_sample=in_sample,
        out_of_sample=out_of_sample,
        equity_curve=_thin(bars, full.equity),
        trades=full.trades[-MAX_TRADES:],
        warnings=warnings,
    )


def evaluate(request: EvaluateRequest, bars: list[Bar], provider: str) -> EvaluateResult:
    """The strategy's opinion on the newest bars: a signal from one of the last three, if any."""
    params = resolve_params(request.strategy, request.params)
    strategy = build_strategy(request.strategy, params)
    warnings: list[str] = []
    if provider == "fake":
        warnings.append("Fake bars: this signal says nothing about any market.")
    result = EvaluateResult(
        provider=provider,
        symbol=request.symbol,
        bars=len(bars),
        as_of=bars[-1].ts if bars else None,
        last_close=bars[-1].close if bars else None,
        warnings=warnings,
    )
    warmup = strategy.warmup()
    if len(bars) <= warmup + 1:
        warnings.append(f"Only {len(bars)} bars; the strategy needs more than {warmup + 1}.")
        return result
    strategy.prepare(bars)
    position = Position(long=request.position == "long", entered_index=None)
    for index in range(len(bars) - 1, max(len(bars) - 4, warmup) - 1, -1):
        signal = strategy.on_bar(index, position)
        if signal is None:
            continue
        if (signal.action == "buy") == position.long:
            continue
        return result.model_copy(
            update={"action": signal.action, "reason": signal.reason, "as_of": bars[index].ts}
        )
    return result
