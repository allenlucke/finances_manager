"""Strategies: pure functions over bars (M7c, D-19).

A strategy sees the bars up to now and the position it holds, and says buy, sell, or nothing. The
same code runs inside the backtester and, for a live strategy, against the latest bars — it never
sends anything anywhere. In the live case the answer becomes a *draft* in the order pipeline, with
the strategy's reason as the rationale, for a person to confirm (D-18).

Indicators are computed once per series (``prepare``) and read per bar (``on_bar``), so a year of
minute bars is a single pass. Prices and indicators are ``Decimal``; a crossover decided by a
float rounding error would be the wrong kind of signal.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, time, timedelta
from decimal import Decimal
from typing import Protocol
from zoneinfo import ZoneInfo

from finances_ai.models import Bar, StrategyInfo, StrategyParam

NEW_YORK = ZoneInfo("America/New_York")
SESSION_OPEN = time(9, 30)
SESSION_CLOSE = time(16, 0)


class StrategyError(ValueError):
    """An unknown strategy or a parameter outside what it accepts. A 422, never a fault."""


@dataclass(frozen=True)
class Signal:
    action: str  # "buy" | "sell"
    reason: str


@dataclass(frozen=True)
class Position:
    """What the strategy holds: flat, or long since a bar index (None when unknown, live)."""

    long: bool = False
    entered_index: int | None = None


class Strategy(Protocol):
    kind: str

    def warmup(self) -> int:
        """How many bars it needs before its first opinion."""

    def prepare(self, bars: list[Bar]) -> None:
        """Compute whatever it needs over the whole series, once."""

    def on_bar(self, index: int, position: Position) -> Signal | None:
        """The opinion at bar ``index``, having seen bars 0..index and nothing later."""


# --- indicators --------------------------------------------------------------------------------


def sma(values: list[Decimal], period: int) -> list[Decimal | None]:
    """Simple moving average; None until ``period`` values exist."""
    out: list[Decimal | None] = [None] * len(values)
    total = Decimal(0)
    for i, value in enumerate(values):
        total += value
        if i >= period:
            total -= values[i - period]
        if i >= period - 1:
            out[i] = total / period
    return out


def rsi(values: list[Decimal], period: int) -> list[Decimal | None]:
    """Wilder's RSI, 0..100; None until ``period`` changes exist."""
    out: list[Decimal | None] = [None] * len(values)
    if len(values) <= period:
        return out
    gains = Decimal(0)
    losses = Decimal(0)
    for i in range(1, period + 1):
        change = values[i] - values[i - 1]
        if change > 0:
            gains += change
        else:
            losses -= change
    avg_gain = gains / period
    avg_loss = losses / period
    out[period] = _rsi_value(avg_gain, avg_loss)
    for i in range(period + 1, len(values)):
        change = values[i] - values[i - 1]
        gain = change if change > 0 else Decimal(0)
        loss = -change if change < 0 else Decimal(0)
        avg_gain = (avg_gain * (period - 1) + gain) / period
        avg_loss = (avg_loss * (period - 1) + loss) / period
        out[i] = _rsi_value(avg_gain, avg_loss)
    return out


def _rsi_value(avg_gain: Decimal, avg_loss: Decimal) -> Decimal:
    if avg_loss == 0:
        return Decimal(100) if avg_gain > 0 else Decimal(50)
    rs = avg_gain / avg_loss
    return Decimal(100) - Decimal(100) / (1 + rs)


def session_days(bars: list[Bar]) -> list[date]:
    """Each bar's New York trading date."""
    return [bar.ts.astimezone(NEW_YORK).date() for bar in bars]


# --- the strategies ----------------------------------------------------------------------------


class BuyAndHold:
    """The baseline every other strategy has to beat. Buys on the first bar, never sells."""

    kind = "buy_and_hold"

    def __init__(self, params: dict[str, Decimal]) -> None:
        pass

    def warmup(self) -> int:
        return 0

    def prepare(self, bars: list[Bar]) -> None:
        pass

    def on_bar(self, index: int, position: Position) -> Signal | None:
        if not position.long:
            return Signal("buy", "buy and hold")
        return None


class SmaCross:
    """Long when the fast average crosses above the slow one; flat when it crosses back."""

    kind = "sma_cross"

    def __init__(self, params: dict[str, Decimal]) -> None:
        self.fast = int(params["fast"])
        self.slow = int(params["slow"])
        if self.fast >= self.slow:
            raise StrategyError("sma_cross: fast must be shorter than slow")
        self._fast: list[Decimal | None] = []
        self._slow: list[Decimal | None] = []

    def warmup(self) -> int:
        return self.slow

    def prepare(self, bars: list[Bar]) -> None:
        closes = [bar.close for bar in bars]
        self._fast = sma(closes, self.fast)
        self._slow = sma(closes, self.slow)

    def on_bar(self, index: int, position: Position) -> Signal | None:
        if index < 1:
            return None
        f0, s0, f1, s1 = (
            self._fast[index - 1],
            self._slow[index - 1],
            self._fast[index],
            self._slow[index],
        )
        if None in (f0, s0, f1, s1):
            return None
        if not position.long and f0 <= s0 and f1 > s1:
            return Signal("buy", f"SMA{self.fast} crossed above SMA{self.slow}")
        if position.long and f0 >= s0 and f1 < s1:
            return Signal("sell", f"SMA{self.fast} crossed below SMA{self.slow}")
        return None


class RsiReversion:
    """Buys when RSI climbs back out of oversold; sells at overbought or after a holding limit."""

    kind = "rsi_reversion"

    def __init__(self, params: dict[str, Decimal]) -> None:
        self.period = int(params["period"])
        self.oversold = params["oversold"]
        self.overbought = params["overbought"]
        self.max_hold = int(params["max_hold"])
        if self.oversold >= self.overbought:
            raise StrategyError("rsi_reversion: oversold must be below overbought")
        self._rsi: list[Decimal | None] = []

    def warmup(self) -> int:
        return self.period + 1

    def prepare(self, bars: list[Bar]) -> None:
        self._rsi = rsi([bar.close for bar in bars], self.period)

    def on_bar(self, index: int, position: Position) -> Signal | None:
        if index < 1:
            return None
        r0, r1 = self._rsi[index - 1], self._rsi[index]
        if r0 is None or r1 is None:
            return None
        if not position.long and r0 < self.oversold <= r1:
            return Signal("buy", f"RSI{self.period} rose back through {self.oversold}")
        if position.long:
            if r1 >= self.overbought:
                return Signal("sell", f"RSI{self.period} reached {self.overbought}")
            held = None if position.entered_index is None else index - position.entered_index
            if held is not None and held >= self.max_hold:
                return Signal("sell", f"held {held} bars, the limit")
        return None


class OpeningRangeBreakout:
    """The day trader's classic: the first minutes set a range; a close above it is the entry, a
    close below it is the stop, and the last bar of the session is the exit no matter what.

    Only regular hours count. Bars before 09:30 or from 16:00 New York time (a real feed has
    them) set no range and get no signal. The exit is signalled on the session's *second-to-last*
    bar, because a signal fills at the next bar's open (the backtester's rule, and the live
    reality): signalled on the last bar it would fill the next morning, which is not a day trade.
    The session's end is known from the bar spacing, so the same rule holds live, where the
    newest bar is not the end of anything.
    """

    kind = "opening_range_breakout"

    def __init__(self, params: dict[str, Decimal]) -> None:
        self.range_minutes = int(params["range_minutes"])
        self._closes: list[Decimal] = []
        self._high: list[Decimal | None] = []
        self._low: list[Decimal | None] = []
        self._in_range: list[bool] = []
        self._outside: list[bool] = []
        self._last_of_session: list[bool] = []

    def warmup(self) -> int:
        return 0

    def prepare(self, bars: list[Bar]) -> None:
        n = len(bars)
        days = session_days(bars)
        self._closes = [bar.close for bar in bars]
        self._high, self._low = [None] * n, [None] * n
        self._in_range, self._outside = [False] * n, [False] * n
        self._last_of_session = [False] * n
        spacing = _bar_spacing(bars)
        high = low = None
        for i, bar in enumerate(bars):
            local = bar.ts.astimezone(NEW_YORK)
            if i == 0 or days[i] != days[i - 1]:
                high = low = None
            clock = local.time()
            if clock < SESSION_OPEN or clock >= SESSION_CLOSE:
                self._outside[i] = True
                self._high[i], self._low[i] = high, low
                continue
            session_open = local.replace(hour=9, minute=30, second=0, microsecond=0)
            if (local - session_open).total_seconds() / 60 < self.range_minutes:
                self._in_range[i] = True
                high = bar.high if high is None else max(high, bar.high)
                low = bar.low if low is None else min(low, bar.low)
            self._high[i], self._low[i] = high, low
            next_day = i + 1 < n and days[i + 1] != days[i]
            self._last_of_session[i] = next_day or (local + 2 * spacing).time() >= SESSION_CLOSE

    def on_bar(self, index: int, position: Position) -> Signal | None:
        if self._outside[index]:
            return None
        if position.long and self._last_of_session[index]:
            return Signal("sell", "end of session")
        if self._in_range[index]:
            return None
        high, low = self._high[index], self._low[index]
        if high is None or low is None:
            return None
        close = self._closes[index]
        if not position.long and not self._last_of_session[index] and close > high:
            return Signal("buy", f"closed above the {self.range_minutes}-minute opening range")
        if position.long and close < low:
            return Signal("sell", "closed below the opening range")
        return None


def _bar_spacing(bars: list[Bar]) -> timedelta:
    """The smallest gap between consecutive bars, from the first fifty: the timeframe."""
    gaps = [b.ts - a.ts for a, b in zip(bars[:50], bars[1:51], strict=False) if b.ts > a.ts]
    return min(gaps) if gaps else timedelta(minutes=1)


def _param(name, label, type_, default, description, min_=None, max_=None) -> StrategyParam:
    return StrategyParam(
        name=name,
        label=label,
        type=type_,
        default=Decimal(str(default)),
        min=None if min_ is None else Decimal(str(min_)),
        max=None if max_ is None else Decimal(str(max_)),
        description=description,
    )


CATALOG: list[StrategyInfo] = [
    StrategyInfo(
        kind="buy_and_hold",
        label="Buy and hold",
        description="Buys on the first bar and never sells. The baseline every strategy must beat.",
        intraday=False,
        params=[],
    ),
    StrategyInfo(
        kind="sma_cross",
        label="Moving-average crossover",
        description=(
            "Long while the fast simple moving average is above the slow one. Enters on the "
            "cross up, exits on the cross down."
        ),
        intraday=False,
        params=[
            _param("fast", "Fast average", "int", 10, "Bars in the fast average", 2, 500),
            _param("slow", "Slow average", "int", 30, "Bars in the slow average", 3, 1000),
        ],
    ),
    StrategyInfo(
        kind="rsi_reversion",
        label="RSI mean reversion",
        description=(
            "Buys when RSI climbs back out of oversold, sells at overbought or after a holding "
            "limit in bars."
        ),
        intraday=False,
        params=[
            _param("period", "RSI period", "int", 14, "Bars in Wilder's RSI", 2, 200),
            _param("oversold", "Oversold", "decimal", 30, "RSI level counted as oversold", 1, 99),
            _param(
                "overbought", "Overbought", "decimal", 70, "RSI level counted as overbought", 1, 99
            ),
            _param("max_hold", "Holding limit", "int", 20, "Bars to hold before selling", 1, 10000),
        ],
    ),
    StrategyInfo(
        kind="opening_range_breakout",
        label="Opening-range breakout",
        description=(
            "Intraday. The first minutes of the session set a range; a close above it enters, a "
            "close below it stops out, and the last bar of the session exits. Flat overnight."
        ),
        intraday=True,
        params=[
            _param(
                "range_minutes",
                "Opening range",
                "int",
                30,
                "Minutes after the open that set the range",
                5,
                120,
            ),
        ],
    ),
]

_BUILDERS = {
    "buy_and_hold": BuyAndHold,
    "sma_cross": SmaCross,
    "rsi_reversion": RsiReversion,
    "opening_range_breakout": OpeningRangeBreakout,
}


def catalog_entry(kind: str) -> StrategyInfo:
    for entry in CATALOG:
        if entry.kind == kind:
            return entry
    raise StrategyError(
        f"No strategy called {kind!r}. Known: {', '.join(e.kind for e in CATALOG)}."
    )


def resolve_params(kind: str, given: dict[str, Decimal]) -> dict[str, Decimal]:
    """Defaults filled in, bounds checked, unknown names refused."""
    info = catalog_entry(kind)
    known = {p.name: p for p in info.params}
    unknown = sorted(set(given) - set(known))
    if unknown:
        raise StrategyError(f"{kind} has no parameter {unknown[0]!r}")
    resolved: dict[str, Decimal] = {}
    for name, spec in known.items():
        value = Decimal(str(given.get(name, spec.default)))
        if spec.type == "int" and value != value.to_integral_value():
            raise StrategyError(f"{kind}: {name} must be a whole number")
        if spec.min is not None and value < spec.min:
            raise StrategyError(f"{kind}: {name} must be at least {spec.min}")
        if spec.max is not None and value > spec.max:
            raise StrategyError(f"{kind}: {name} must be at most {spec.max}")
        resolved[name] = value
    return resolved


def build_strategy(kind: str, params: dict[str, Decimal]) -> Strategy:
    resolved = resolve_params(kind, params)
    return _BUILDERS[kind](resolved)
