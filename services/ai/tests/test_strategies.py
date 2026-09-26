"""Strategies and bars (M7c): indicators, signals on handcrafted series, and both bar sources."""

from datetime import UTC, date, datetime
from decimal import Decimal

import httpx
import pytest

from conftest import bars_from
from finances_ai.market import (
    AlpacaProvider,
    FakeProvider,
    StrategyError,
    build_strategy,
    resolve_params,
    session_bars,
)
from finances_ai.market.strategies import Position, rsi, sma
from finances_ai.models import Bar


def test_sma_is_none_until_it_has_enough_and_exact_after():
    values = [Decimal(v) for v in (1, 2, 3, 4, 5)]
    assert sma(values, 3) == [None, None, Decimal(2), Decimal(3), Decimal(4)]


def test_rsi_is_100_on_a_straight_rise_and_bounded_otherwise():
    rising = [Decimal(v) for v in range(1, 20)]
    assert rsi(rising, 14)[-1] == Decimal(100)
    wobbly = [Decimal(str(v)) for v in (10, 11, 10, 12, 9, 13, 8, 14, 7, 15, 6, 16, 5, 17, 4, 18)]
    values = [r for r in rsi(wobbly, 14) if r is not None]
    assert values and all(Decimal(0) <= r <= Decimal(100) for r in values)


def test_sma_cross_buys_on_the_cross_up_and_sells_on_the_cross_down():
    closes = [10, 10, 10, 10, 10, 12, 14, 16, 18, 20, 18, 16, 14, 12, 10, 9]
    bars = bars_from(closes)
    strategy = build_strategy("sma_cross", {"fast": Decimal(2), "slow": Decimal(4)})
    strategy.prepare(bars)

    flat = Position()
    buys = [i for i in range(len(bars)) if (s := strategy.on_bar(i, flat)) and s.action == "buy"]
    assert buys, "a rally after a flat stretch must produce a cross up"
    long = Position(long=True, entered_index=buys[0])
    sells = [
        i
        for i in range(buys[0], len(bars))
        if (s := strategy.on_bar(i, long)) and s.action == "sell"
    ]
    assert sells and sells[0] > buys[0]
    assert "crossed above" in strategy.on_bar(buys[0], flat).reason
    # Long already: no second buy on the same cross.
    assert strategy.on_bar(buys[0], long) is None


def test_rsi_reversion_sells_at_the_holding_limit():
    closes = [10, 9, 8, 7, 6, 5, 4, 3, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17]
    bars = bars_from(closes)
    strategy = build_strategy(
        "rsi_reversion",
        {
            "period": Decimal(5),
            "oversold": Decimal(30),
            "overbought": Decimal(99),
            "max_hold": Decimal(3),
        },
    )
    strategy.prepare(bars)
    long = Position(long=True, entered_index=10)

    assert strategy.on_bar(13, long).reason == "held 3 bars, the limit"
    assert strategy.on_bar(12, long) is None


def test_opening_range_breakout_enters_above_the_range_and_exits_at_the_close():
    stamps = session_bars(date(2026, 9, 21), "5Min")  # 78 bars, 09:30..15:55 New York
    bars = []
    for i, ts in enumerate(stamps):
        # The first six bars (30 minutes) range 100..101; then a breakout close at 102.
        close = Decimal("100.5") if i < 6 else Decimal("102")
        bars.append(
            Bar(
                ts=ts,
                open=close,
                high=Decimal("101") if i < 6 else close,
                low=Decimal("100"),
                close=close,
            )
        )
    strategy = build_strategy("opening_range_breakout", {"range_minutes": Decimal(30)})
    strategy.prepare(bars)

    assert all(strategy.on_bar(i, Position()) is None for i in range(6))
    entry = strategy.on_bar(6, Position())
    assert entry.action == "buy" and "30-minute opening range" in entry.reason
    long = Position(long=True, entered_index=6)
    assert strategy.on_bar(40, long) is None
    assert strategy.on_bar(75, long) is None  # 15:45: not yet
    # 15:50 is the exit bar: the fill lands on the 15:55 open, inside the session.
    assert strategy.on_bar(76, long).reason == "end of session"
    assert strategy.on_bar(77, long).reason == "end of session"
    # No entry from the exit bar on: there would be nothing to exit into.
    assert strategy.on_bar(76, Position()) is None
    assert strategy.on_bar(77, Position()) is None


def test_opening_range_breakout_ignores_pre_market_bars():
    early = datetime(2026, 9, 21, 8, 0, tzinfo=UTC)  # 04:00 New York
    bars = [Bar(ts=early, open=Decimal(90), high=Decimal(90), low=Decimal(90), close=Decimal(90))]
    bars += [
        Bar(ts=ts, open=Decimal(100), high=Decimal(101), low=Decimal(100), close=Decimal(105))
        for ts in session_bars(date(2026, 9, 21), "15Min")
    ]
    strategy = build_strategy("opening_range_breakout", {"range_minutes": Decimal(15)})
    strategy.prepare(bars)

    assert strategy.on_bar(0, Position()) is None
    assert strategy.on_bar(1, Position()) is None  # 09:30 sets the range
    assert strategy.on_bar(2, Position()).action == "buy"


def test_params_get_defaults_bounds_and_refusals():
    assert resolve_params("sma_cross", {}) == {"fast": Decimal(10), "slow": Decimal(30)}
    with pytest.raises(StrategyError, match="no parameter"):
        resolve_params("sma_cross", {"speed": Decimal(1)})
    with pytest.raises(StrategyError, match="whole number"):
        resolve_params("sma_cross", {"fast": Decimal("2.5")})
    with pytest.raises(StrategyError, match="at least"):
        resolve_params("sma_cross", {"fast": Decimal(1)})
    with pytest.raises(StrategyError, match="shorter than slow"):
        build_strategy("sma_cross", {"fast": Decimal(30), "slow": Decimal(10)})
    with pytest.raises(StrategyError, match="No strategy called"):
        resolve_params("martingale", {})


def test_fake_bars_are_deterministic_weekday_sessions_in_decimal():
    provider = FakeProvider()
    start = datetime(2026, 9, 21, tzinfo=UTC)  # a Monday
    end = datetime(2026, 9, 27, 23, 59, tzinfo=UTC)

    daily = provider.bars("AAPL", "1Day", start, end)
    again = provider.bars("aapl", "1Day", start, end)
    minute = provider.bars("AAPL", "1Min", start, datetime(2026, 9, 21, 23, 59, tzinfo=UTC))
    five = provider.bars("AAPL", "5Min", start, datetime(2026, 9, 21, 23, 59, tzinfo=UTC))

    assert len(daily) == 5 and [b.close for b in daily] == [b.close for b in again]
    assert len(minute) == 390 and len(five) == 78
    assert all(isinstance(b.close, Decimal) and b.low <= b.close <= b.high for b in daily)
    assert minute[0].ts == datetime(2026, 9, 21, 13, 30, tzinfo=UTC)  # 09:30 New York in September
    assert all(b.ts.weekday() < 5 for b in daily)


def test_alpaca_bars_follow_pages_and_send_the_feed_and_adjustment():
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(dict(request.url.params))
        if request.url.params.get("page_token") == "p2":
            return httpx.Response(
                200,
                json={
                    "bars": [
                        {"t": "2026-09-22T13:30:00Z", "o": 2, "h": 3, "l": 1, "c": 2.5, "v": 7}
                    ],
                    "next_page_token": None,
                },
            )
        return httpx.Response(
            200,
            json={
                "bars": [{"t": "2026-09-21T13:30:00Z", "o": 1, "h": 2, "l": 0.5, "c": 1.5, "v": 9}],
                "next_page_token": "p2",
            },
        )

    provider = AlpacaProvider(key="k", secret="s", transport=httpx.MockTransport(handler))
    bars = provider.bars(
        "aapl", "1Min", datetime(2026, 9, 21, tzinfo=UTC), datetime(2026, 9, 23, tzinfo=UTC)
    )

    assert [b.close for b in bars] == [Decimal("1.5"), Decimal("2.5")]
    assert seen[0]["feed"] == "iex" and seen[0]["adjustment"] == "split"
    assert seen[0]["timeframe"] == "1Min" and seen[0]["start"].endswith("Z")
    assert seen[1]["page_token"] == "p2"
