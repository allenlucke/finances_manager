"""Order execution: the one place an order leaves this system (M7b, D-18).

A broker answers four things: is trading possible right now (``status``), send this order
(``submit``), what happened to it (``lookup``), and stop it (``cancel``). The Java API owns the
order's life — draft, confirmed, submitted, filled — and asks here only for the two steps that touch
a broker. This module never decides whether an order should be sent; that decision, the
confirmation, the daily cap and the kill switch all live on the API side, where they are tested
against a database. Here there is only the wire.

Two brokers. ``AlpacaBroker`` talks to Alpaca's PAPER endpoint and only that: the live endpoint is
not a configuration option in this release, on purpose. ``FakeBroker`` accepts everything, fills at
the fake provider's price on the next lookup, and marks every order ``fake`` so nothing it does can
be mistaken for an execution.

Quantities and prices are ``Decimal`` and travel as strings. Never floats.
"""

from __future__ import annotations

import contextlib
import os
from datetime import UTC, datetime
from decimal import Decimal
from uuid import uuid4

import httpx

from finances_ai.market.provider import FakeProvider, _decimal
from finances_ai.models import BrokerOrder, BrokerStatus, OrderRequest

BROKER_VARIABLE = "TRADING_BROKER"
ALPACA_PAPER_URL = "https://paper-api.alpaca.markets"

# Alpaca's order statuses, folded to the handful the API's state machine understands.
_OPEN = {"new", "accepted", "pending_new", "accepted_for_bidding", "partially_filled", "held"}
_DONE = {"filled"}
_GONE = {
    "canceled",
    "cancelled",
    "expired",
    "rejected",
    "done_for_day",
    "replaced",
    "stopped",
    "suspended",
    "pending_cancel",
    "pending_replace",
    "calculated",
}


class BrokerError(RuntimeError):
    """The broker answered, and the answer was a refusal or nonsense."""


class BrokerUnavailable(RuntimeError):
    """No broker is configured. Trading is off; the API reads this as a state, not a fault."""

    def __init__(self) -> None:
        super().__init__(
            f"{BROKER_VARIABLE} is 'none'. Set it to 'alpaca_paper' with ALPACA_API_KEY and "
            "ALPACA_API_SECRET to send paper orders, or 'fake' for a stack with no broker."
        )


def _fold(status: str) -> str:
    lowered = (status or "").lower()
    if lowered in _DONE:
        return "filled"
    if lowered in _OPEN:
        return "partially_filled" if lowered == "partially_filled" else "accepted"
    if lowered in _GONE:
        return "cancelled" if lowered in ("canceled", "cancelled", "pending_cancel") else lowered
    return lowered or "unknown"


class FakeBroker:
    """Accepts everything, fills on the next lookup at the fake provider's price."""

    name = "fake"

    def __init__(self) -> None:
        self._orders: dict[str, tuple[OrderRequest, int]] = {}
        self._prices = FakeProvider()

    def status(self) -> BrokerStatus:
        return BrokerStatus(
            broker=self.name,
            available=True,
            paper=True,
            market_open=True,
            detail="Fake broker: every order is accepted and filled on the next lookup.",
        )

    def submit(self, order: OrderRequest) -> BrokerOrder:
        broker_id = f"fake-{uuid4().hex[:12]}"
        self._orders[broker_id] = (order, 0)
        return BrokerOrder(
            broker=self.name,
            broker_order_id=broker_id,
            status="accepted",
            submitted_at=datetime.now(UTC),
            filled_quantity=Decimal(0),
        )

    def lookup(self, broker_order_id: str) -> BrokerOrder:
        if broker_order_id not in self._orders:
            raise BrokerError(f"Fake broker knows no order {broker_order_id}")
        order, looks = self._orders[broker_order_id]
        self._orders[broker_order_id] = (order, looks + 1)
        if looks == 0:
            return BrokerOrder(
                broker=self.name,
                broker_order_id=broker_order_id,
                status="accepted",
                filled_quantity=Decimal(0),
            )
        quotes, _ = self._prices.quotes([order.symbol])
        price = order.limit_price or quotes[0].price
        return BrokerOrder(
            broker=self.name,
            broker_order_id=broker_order_id,
            status="filled",
            filled_quantity=order.quantity,
            filled_avg_price=price,
            filled_at=datetime.now(UTC),
        )

    def cancel(self, broker_order_id: str) -> BrokerOrder:
        if broker_order_id not in self._orders:
            raise BrokerError(f"Fake broker knows no order {broker_order_id}")
        order, _ = self._orders.pop(broker_order_id)
        return BrokerOrder(
            broker=self.name,
            broker_order_id=broker_order_id,
            status="cancelled",
            filled_quantity=Decimal(0),
        )


class AlpacaBroker:
    """Alpaca's paper-trading API. The live URL is deliberately not a parameter."""

    name = "alpaca_paper"

    def __init__(
        self,
        key: str,
        secret: str,
        transport: httpx.BaseTransport | None = None,
        base_url: str = ALPACA_PAPER_URL,
    ) -> None:
        if not key or not secret:
            raise BrokerError("Alpaca needs ALPACA_API_KEY and ALPACA_API_SECRET")
        if "paper-api" not in base_url:
            # Belt and braces: even a test may not point this class at a live endpoint.
            raise BrokerError("This release trades on Alpaca's paper endpoint only")
        self._client = httpx.Client(
            base_url=base_url,
            headers={"APCA-API-KEY-ID": key, "APCA-API-SECRET-KEY": secret},
            timeout=15.0,
            transport=transport,
        )

    def _call(self, method: str, path: str, **kwargs) -> httpx.Response:
        try:
            response = self._client.request(method, path, **kwargs)
        except httpx.HTTPError as exc:
            raise BrokerError(f"Alpaca could not be reached ({type(exc).__name__})") from exc
        if response.status_code in (401, 403):
            raise BrokerError("Alpaca refused the API key")
        if response.status_code == 422:
            detail = None
            with contextlib.suppress(ValueError):
                detail = response.json().get("message")
            raise BrokerError(f"Alpaca rejected the order: {detail or 'no reason given'}")
        if response.status_code == 429:
            raise BrokerError("Alpaca rate limit reached; try again shortly")
        if response.status_code >= 400:
            raise BrokerError(f"Alpaca answered {response.status_code}")
        return response

    def status(self) -> BrokerStatus:
        account = self._call("GET", "/v2/account").json()
        clock = self._call("GET", "/v2/clock").json()
        return BrokerStatus(
            broker=self.name,
            available=True,
            paper=True,
            market_open=bool(clock.get("is_open")),
            buying_power=_decimal(account.get("buying_power")),
            portfolio_value=_decimal(account.get("portfolio_value")),
            detail=f"Alpaca paper account {account.get('status', '?')}; next open "
            f"{clock.get('next_open', '?')}",
        )

    def submit(self, order: OrderRequest) -> BrokerOrder:
        body = {
            "symbol": order.symbol,
            "qty": str(order.quantity),
            "side": order.side,
            "type": order.order_type,
            "time_in_force": order.time_in_force,
            # The API's own id, so a retry after a timeout cannot place the order twice.
            "client_order_id": order.client_order_id,
        }
        if order.order_type == "limit":
            body["limit_price"] = str(order.limit_price)
        return self._to_order(self._call("POST", "/v2/orders", json=body).json())

    def lookup(self, broker_order_id: str) -> BrokerOrder:
        return self._to_order(self._call("GET", f"/v2/orders/{broker_order_id}").json())

    def cancel(self, broker_order_id: str) -> BrokerOrder:
        self._call("DELETE", f"/v2/orders/{broker_order_id}")
        return self.lookup(broker_order_id)

    def _to_order(self, body: dict) -> BrokerOrder:
        def when(key: str) -> datetime | None:
            value = body.get(key)
            if not value:
                return None
            try:
                return datetime.fromisoformat(str(value).replace("Z", "+00:00"))
            except ValueError:
                return None

        return BrokerOrder(
            broker=self.name,
            broker_order_id=str(body.get("id")),
            status=_fold(str(body.get("status", ""))),
            broker_status=str(body.get("status", "")),
            submitted_at=when("submitted_at"),
            filled_at=when("filled_at"),
            filled_quantity=_decimal(body.get("filled_qty")) or Decimal(0),
            filled_avg_price=_decimal(body.get("filled_avg_price")),
        )


_fake_singleton: FakeBroker | None = None


def broker_from_env(environ: dict[str, str] | None = None):
    """The configured broker, or ``BrokerUnavailable``. The fake keeps state within the process."""
    global _fake_singleton
    env = os.environ if environ is None else environ
    choice = env.get(BROKER_VARIABLE, "none").strip().lower()
    if choice in ("", "none", "off"):
        raise BrokerUnavailable()
    if choice == "fake":
        if _fake_singleton is None:
            _fake_singleton = FakeBroker()
        return _fake_singleton
    if choice == "alpaca_paper":
        return AlpacaBroker(
            key=env.get("ALPACA_API_KEY", ""), secret=env.get("ALPACA_API_SECRET", "")
        )
    if choice in ("alpaca", "alpaca_live"):
        raise BrokerError(
            f"{BROKER_VARIABLE}={choice!r}: live trading is not a configuration option in this "
            "release. Use 'alpaca_paper'."
        )
    raise BrokerError(f"{BROKER_VARIABLE}={choice!r} is not a broker this service knows")
