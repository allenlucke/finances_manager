"""Order execution (M7b): the broker seam, Alpaca's paper wire, and the endpoint's refusals."""

from decimal import Decimal

import httpx
import pytest
from fastapi.testclient import TestClient

from finances_ai.app import app
from finances_ai.market import (
    AlpacaBroker,
    BrokerError,
    BrokerUnavailable,
    FakeBroker,
    broker_from_env,
)
from finances_ai.models import OrderRequest

client = TestClient(app)


def order(**overrides) -> OrderRequest:
    fields = {
        "client_order_id": "order-7",
        "symbol": "AAPL",
        "side": "buy",
        "quantity": Decimal("2"),
        "order_type": "limit",
        "limit_price": Decimal("185.50"),
    }
    fields.update(overrides)
    return OrderRequest(**fields)


def test_the_fake_broker_accepts_then_fills_and_says_it_is_fake():
    broker = FakeBroker()

    submitted = broker.submit(order())
    assert submitted.status == "accepted"
    assert submitted.broker == "fake"
    first = broker.lookup(submitted.broker_order_id)
    second = broker.lookup(submitted.broker_order_id)

    assert first.status == "accepted"
    assert second.status == "filled"
    assert second.filled_quantity == Decimal("2")
    assert second.filled_avg_price == Decimal("185.50")
    assert broker.status().paper is True


def test_the_fake_broker_can_cancel_and_forgets_the_order():
    broker = FakeBroker()
    submitted = broker.submit(order(order_type="market", limit_price=None))

    cancelled = broker.cancel(submitted.broker_order_id)

    assert cancelled.status == "cancelled"
    with pytest.raises(BrokerError):
        broker.lookup(submitted.broker_order_id)


def alpaca(handler) -> AlpacaBroker:
    return AlpacaBroker(key="k", secret="s", transport=httpx.MockTransport(handler))


def test_alpaca_submit_sends_strings_and_the_client_order_id():
    import json

    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["method"] = request.method
        seen["path"] = request.url.path
        seen["body"] = json.loads(request.read())
        return httpx.Response(
            200,
            json={
                "id": "b-1",
                "status": "accepted",
                "submitted_at": "2026-09-26T14:30:00Z",
                "filled_qty": "0",
                "filled_avg_price": None,
            },
        )

    result = alpaca(handler).submit(order())

    assert seen["method"] == "POST" and seen["path"] == "/v2/orders"
    # Strings, never floats: a quantity of 2 and a price of 185.50 arrive exactly as typed.
    assert seen["body"]["qty"] == "2" and seen["body"]["limit_price"] == "185.50"
    assert seen["body"]["client_order_id"] == "order-7"
    assert seen["body"]["time_in_force"] == "day"
    assert result.broker_order_id == "b-1"
    assert result.status == "accepted"
    assert result.filled_quantity == Decimal(0)


def test_alpaca_statuses_fold_to_the_state_machine():
    def handler(request: httpx.Request) -> httpx.Response:
        status = request.url.path.rsplit("/", 1)[-1]
        return httpx.Response(
            200,
            json={
                "id": status,
                "status": status,
                "filled_qty": "1.5",
                "filled_avg_price": "186.1",
                "filled_at": "2026-09-26T14:31:00Z",
            },
        )

    broker = alpaca(handler)
    assert broker.lookup("filled").status == "filled"
    assert broker.lookup("filled").filled_avg_price == Decimal("186.1")
    assert broker.lookup("partially_filled").status == "partially_filled"
    assert broker.lookup("new").status == "accepted"
    assert broker.lookup("canceled").status == "cancelled"
    assert broker.lookup("rejected").status == "rejected"
    assert broker.lookup("expired").status == "expired"


def test_alpaca_rejections_carry_the_brokers_sentence():
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(422, json={"message": "insufficient buying power"})

    with pytest.raises(BrokerError, match="insufficient buying power"):
        alpaca(handler).submit(order())


def test_the_live_endpoint_is_not_a_configuration_option():
    with pytest.raises(BrokerError, match="paper endpoint only"):
        AlpacaBroker(key="k", secret="s", base_url="https://api.alpaca.markets")
    with pytest.raises(BrokerError, match="live trading is not"):
        broker_from_env(
            {"TRADING_BROKER": "alpaca_live", "ALPACA_API_KEY": "k", "ALPACA_API_SECRET": "s"}
        )
    with pytest.raises(BrokerError, match="live trading is not"):
        broker_from_env({"TRADING_BROKER": "alpaca"})


def test_broker_selection_defaults_to_none():
    with pytest.raises(BrokerUnavailable):
        broker_from_env({})
    assert broker_from_env({"TRADING_BROKER": "fake"}).name == "fake"
    with pytest.raises(BrokerError, match="ALPACA_API_KEY"):
        broker_from_env({"TRADING_BROKER": "alpaca_paper"})


def test_the_endpoints_refuse_when_no_broker_is_configured(monkeypatch):
    monkeypatch.delenv("TRADING_BROKER", raising=False)

    submitted = client.post(
        "/broker/orders",
        json={
            "client_order_id": "o-1",
            "symbol": "AAPL",
            "side": "buy",
            "quantity": "1",
        },
    )

    assert submitted.status_code == 503
    assert "TRADING_BROKER" in submitted.json()["detail"]
    assert client.get("/broker/status").json()["available"] is False


def test_the_endpoints_drive_the_fake_broker_end_to_end(monkeypatch):
    monkeypatch.setenv("TRADING_BROKER", "fake")

    submitted = client.post(
        "/broker/orders",
        json={
            "client_order_id": "o-2",
            "symbol": "MSFT",
            "side": "sell",
            "quantity": "3",
            "order_type": "market",
        },
    )
    assert submitted.status_code == 200
    broker_id = submitted.json()["broker_order_id"]
    assert submitted.json()["status"] == "accepted"

    client.get(f"/broker/orders/{broker_id}")
    filled = client.get(f"/broker/orders/{broker_id}").json()
    assert filled["status"] == "filled"
    assert filled["filled_quantity"] == "3"
    assert filled["broker"] == "fake"


def test_the_wire_refuses_a_bad_order_before_any_broker_sees_it(monkeypatch):
    monkeypatch.setenv("TRADING_BROKER", "fake")

    for bad in (
        {"client_order_id": "o", "symbol": "aapl", "side": "buy", "quantity": "1"},  # lowercase
        {"client_order_id": "o", "symbol": "AAPL", "side": "hold", "quantity": "1"},
        {"client_order_id": "o", "symbol": "AAPL", "side": "buy", "quantity": "0"},
        {
            "client_order_id": "o",
            "symbol": "AAPL",
            "side": "buy",
            "quantity": "1",
            "order_type": "stop",
        },
    ):
        assert client.post("/broker/orders", json=bad).status_code == 422, bad
