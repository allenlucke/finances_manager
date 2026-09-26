"""The backtester (M7c): next-bar fills, costs, the benchmark, the honesty notes, the endpoints."""

from datetime import UTC, date, datetime
from decimal import Decimal

import pytest
from fastapi.testclient import TestClient

from conftest import bars_from
from finances_ai.app import app
from finances_ai.market import BacktestError, FakeProvider, evaluate, run_backtest
from finances_ai.models import BacktestRequest, EvaluateRequest

client = TestClient(app)


def request(**overrides) -> BacktestRequest:
    fields = {
        "strategy": "buy_and_hold",
        "symbol": "AAPL",
        "timeframe": "1Day",
        "start": date(2026, 1, 5),
        "end": date(2026, 9, 25),
        "initial_cash": Decimal("1000"),
        "slippage_bps": Decimal("10"),
        "commission_per_order": Decimal("1"),
        "out_of_sample_fraction": Decimal("0"),
    }
    fields.update(overrides)
    return BacktestRequest(**fields)


def test_a_signal_fills_at_the_next_open_with_slippage_and_whole_shares():
    bars = bars_from([100, 100, 110, 120, 130, 125])
    # buy_and_hold signals on bar 0; the fill is bar 1's open (100) plus 10 bps = 100.10.
    result = run_backtest(request(), bars, "fake")

    trade = result.trades[0]
    assert trade.entered_at == bars[1].ts
    assert trade.entry_price == Decimal("100.1000")
    assert trade.quantity == 9  # (1000 - 1 commission) / 100.10 = 9.98 → 9 whole shares
    assert result.metrics.final_equity == Decimal("1000") - 9 * Decimal("100.1") - 1 + 9 * Decimal(
        "125"
    )
    assert trade.reason_out.startswith("end of test")
    assert "still open" in " ".join(result.warnings)
    # Buy and hold IS the benchmark here, so the two returns agree to the cent.
    assert result.metrics.total_return_pct == result.metrics.benchmark_return_pct


def test_a_signal_on_the_last_bar_cannot_fill():
    bars = bars_from([10] * 6 + [20])  # the cross up happens on the final bar
    result = run_backtest(
        request(strategy="sma_cross", params={"fast": Decimal(2), "slow": Decimal(4)}), bars, "fake"
    )

    assert result.metrics.trades == 0
    assert result.metrics.final_equity == Decimal("1000.00")


def test_too_few_bars_is_a_sentence_not_a_number():
    with pytest.raises(BacktestError, match="needs at least"):
        run_backtest(request(strategy="sma_cross"), bars_from([1, 2, 3]), "fake")


def test_an_intraday_strategy_refuses_daily_bars():
    with pytest.raises(BacktestError, match="intraday strategy"):
        run_backtest(request(strategy="opening_range_breakout"), bars_from([1] * 50), "fake")


def test_a_year_of_fake_daily_bars_reports_honestly():
    provider = FakeProvider()
    req = request(
        strategy="sma_cross",
        params={"fast": Decimal(5), "slow": Decimal(20)},
        initial_cash=Decimal("10000"),
        out_of_sample_fraction=Decimal("0.3"),
    )
    bars = provider.bars(
        "AAPL", "1Day", datetime(2026, 1, 5, tzinfo=UTC), datetime(2026, 9, 25, 23, 59, tzinfo=UTC)
    )

    result = run_backtest(req, bars, provider.name)

    assert result.metrics.trades > 0
    assert result.in_sample is not None and result.out_of_sample is not None
    assert result.warnings[0].startswith("These bars are fake")
    assert (
        any("Only" in w and "closed trade" in w for w in result.warnings)
        or result.metrics.trades >= 30
    )
    assert len(result.equity_curve) <= 500
    assert result.equity_curve[-1].equity == result.metrics.final_equity
    assert result.metrics.max_drawdown_pct >= 0
    assert result.params == {"fast": Decimal(5), "slow": Decimal(20)}
    # Every closed trade carries both prices, its P&L and its reasons.
    assert all(
        t.exit_price is not None and t.pnl is not None and t.reason_out for t in result.trades
    )


def test_day_trades_are_counted_and_the_pattern_day_trader_rule_is_named():
    provider = FakeProvider()
    req = request(
        strategy="opening_range_breakout",
        timeframe="5Min",
        start=date(2026, 9, 14),
        end=date(2026, 9, 25),
        initial_cash=Decimal("50000"),
    )
    bars = provider.bars(
        "MSFT", "5Min", datetime(2026, 9, 14, tzinfo=UTC), datetime(2026, 9, 25, 23, 59, tzinfo=UTC)
    )

    result = run_backtest(req, bars, provider.name)

    assert result.metrics.trades > 0
    assert result.metrics.day_trades == result.metrics.trades
    assert any("pattern day trader" in w for w in result.warnings)
    assert all(t.same_day for t in result.trades)


def test_evaluate_reports_a_signal_from_the_newest_bars_or_none():
    bars = bars_from([10, 10, 10, 10, 10, 12, 14, 16])
    req = EvaluateRequest(
        strategy="sma_cross",
        params={"fast": Decimal(2), "slow": Decimal(4)},
        symbol="AAPL",
        timeframe="1Day",
    )

    flat = evaluate(req, bars, "fake")
    long = evaluate(req.model_copy(update={"position": "long"}), bars, "fake")

    assert flat.action == "buy" and "crossed above" in flat.reason
    assert flat.as_of in {b.ts for b in bars[-3:]}
    assert long.action is None  # a cross up says nothing to someone already long
    assert flat.warnings[0].startswith("Fake bars")


def test_the_endpoints_refuse_without_a_provider_and_explain_bad_requests(monkeypatch):
    monkeypatch.delenv("MARKET_DATA_PROVIDER", raising=False)
    body = {
        "strategy": "sma_cross",
        "symbol": "AAPL",
        "timeframe": "1Day",
        "start": "2026-01-05",
        "end": "2026-09-25",
    }

    assert client.post("/backtests", json=body).status_code == 503
    assert client.get("/strategies").status_code == 200

    monkeypatch.setenv("MARKET_DATA_PROVIDER", "fake")
    unknown = client.post("/backtests", json={**body, "strategy": "martingale"})
    backwards = client.post("/backtests", json={**body, "start": "2026-09-25", "end": "2026-01-05"})
    daily_orb = client.post("/backtests", json={**body, "strategy": "opening_range_breakout"})
    bad_param = client.post("/backtests", json={**body, "params": {"fast": "2.5"}})

    assert unknown.status_code == 422 and "No strategy called" in unknown.json()["detail"]
    assert backwards.status_code == 422 and "before the start" in backwards.json()["detail"]
    assert daily_orb.status_code == 422 and "intraday" in daily_orb.json()["detail"]
    assert bad_param.status_code == 422 and "whole number" in bad_param.json()["detail"]


def test_the_endpoints_run_a_fake_backtest_and_an_evaluation(monkeypatch):
    monkeypatch.setenv("MARKET_DATA_PROVIDER", "fake")

    ran = client.post(
        "/backtests",
        json={
            "strategy": "rsi_reversion",
            "symbol": "AAPL",
            "timeframe": "1Day",
            "start": "2026-01-05",
            "end": "2026-09-25",
            "initial_cash": "10000",
        },
    )
    catalog = client.get("/strategies").json()
    evaluated = client.post(
        "/strategies/evaluate",
        json={"strategy": "sma_cross", "symbol": "AAPL", "timeframe": "1Day", "lookback_bars": 60},
    )

    assert ran.status_code == 200, ran.text
    body = ran.json()
    assert body["provider"] == "fake" and body["warnings"][0].startswith("These bars are fake")
    assert isinstance(body["metrics"]["final_equity"], str)  # Decimal on the wire, never a float
    assert {c["kind"] for c in catalog} >= {
        "buy_and_hold",
        "sma_cross",
        "rsi_reversion",
        "opening_range_breakout",
    }
    assert next(c for c in catalog if c["kind"] == "sma_cross")["params"][0]["default"] == "10"
    assert evaluated.status_code == 200, evaluated.text
    assert evaluated.json()["bars"] == 60
    assert evaluated.json()["action"] in (None, "buy", "sell")
