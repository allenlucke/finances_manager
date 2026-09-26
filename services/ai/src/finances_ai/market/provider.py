"""Market data: the one place quotes come from (M7, D-18).

A provider answers "what is this symbol worth right now, and what did it close at yesterday" for a
list of symbols. That is all the rest of the system needs: the API stores the answers as a time
series, evaluates alerts against them, and shows holdings at their latest quote beside the value
the last positions file gave them. Prices are ``Decimal`` — they are money-adjacent and feed a
NUMERIC column — and never floats, which is why every vendor float is converted through ``str``.

Two providers. ``AlpacaProvider`` calls Alpaca's market-data API, whose free tier serves the IEX
feed for any account holder, funded or not, and whose paper-trading account is where M7b's orders
go. ``FakeProvider`` answers deterministically from a small table, for tests and for a stack with
no vendor keys, and says so in every response so a fake price can never be mistaken for a real one.

Selection is by environment (``MARKET_DATA_PROVIDER``): ``none`` is the default and the endpoint
answers 503, because a service that silently served fake prices in production would be a worse
outcome than one that said it had no data.
"""

from __future__ import annotations

import os
from datetime import UTC, datetime
from decimal import Decimal, InvalidOperation
from typing import Protocol

import httpx

from finances_ai.models import Quote

PROVIDER_VARIABLE = "MARKET_DATA_PROVIDER"
ALPACA_DATA_URL = "https://data.alpaca.markets"


class MarketDataError(RuntimeError):
    """The vendor answered, and the answer was a refusal or nonsense."""


class MarketDataUnavailable(RuntimeError):
    """No provider is configured. The API reads this as 'market data is off', not as a fault."""

    def __init__(self) -> None:
        super().__init__(
            f"{PROVIDER_VARIABLE} is 'none'. Set it to 'alpaca' with ALPACA_API_KEY and "
            "ALPACA_API_SECRET to watch prices, or 'fake' for a stack with no vendor."
        )


class Provider(Protocol):
    name: str

    def quotes(self, symbols: list[str]) -> tuple[list[Quote], list[str]]:
        """Quotes for the symbols it could price, and a warning for each one it could not."""


def _decimal(value: object) -> Decimal | None:
    if value is None:
        return None
    try:
        return Decimal(str(value))
    except InvalidOperation:
        return None


def _clean(symbols: list[str]) -> list[str]:
    seen: list[str] = []
    for symbol in symbols:
        upper = symbol.strip().upper()
        if upper and upper not in seen:
            seen.append(upper)
    return seen


class FakeProvider:
    """Deterministic prices, labelled as such.

    A fixed table for a handful of symbols, and a price derived from the letters for anything else,
    so a test can watch any symbol it likes and get the same number every time. ``source`` on every
    quote is ``fake``; the API carries that through, and the screen shows it.
    """

    name = "fake"

    _TABLE: dict[str, tuple[str, str]] = {
        "AAPL": ("189.30", "187.10"),
        "MSFT": ("412.05", "415.60"),
        "FXAIX": ("201.44", "200.90"),
        "SPAXX": ("1.00", "1.00"),
    }

    def quotes(self, symbols: list[str]) -> tuple[list[Quote], list[str]]:
        now = datetime.now(UTC)
        quotes = []
        for symbol in _clean(symbols):
            price, previous = self._TABLE.get(symbol, self._derived(symbol))
            quotes.append(
                Quote(
                    symbol=symbol,
                    price=Decimal(price),
                    previous_close=Decimal(previous),
                    as_of=now,
                    source=self.name,
                )
            )
        return quotes, []

    @staticmethod
    def _derived(symbol: str) -> tuple[str, str]:
        cents = sum(ord(ch) for ch in symbol) % 9000 + 1000
        return f"{cents / 100:.2f}", f"{(cents - 37) / 100:.2f}"


class AlpacaProvider:
    """Alpaca market data, ``GET /v2/stocks/snapshots``.

    One request for every symbol. A snapshot carries the latest trade, the day's bar and the
    previous day's bar; the latest trade's price is the quote and the previous daily close is what
    "change today" is measured against. The free tier's feed is IEX, which sees a fraction of
    volume but real prices; ``ALPACA_FEED`` selects it explicitly rather than letting the vendor
    default decide.

    Symbols the vendor does not know come back absent from the response rather than as an error,
    so each one becomes a warning — never a silent gap.
    """

    name = "alpaca"

    def __init__(
        self,
        key: str,
        secret: str,
        base_url: str = ALPACA_DATA_URL,
        feed: str = "iex",
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        if not key or not secret:
            raise MarketDataError("Alpaca needs ALPACA_API_KEY and ALPACA_API_SECRET")
        self._feed = feed
        self._client = httpx.Client(
            base_url=base_url,
            headers={"APCA-API-KEY-ID": key, "APCA-API-SECRET-KEY": secret},
            timeout=10.0,
            transport=transport,
        )

    def quotes(self, symbols: list[str]) -> tuple[list[Quote], list[str]]:
        wanted = _clean(symbols)
        if not wanted:
            return [], []
        try:
            response = self._client.get(
                "/v2/stocks/snapshots", params={"symbols": ",".join(wanted), "feed": self._feed}
            )
        except httpx.HTTPError as exc:
            raise MarketDataError(f"Alpaca could not be reached ({type(exc).__name__})") from exc
        if response.status_code in (401, 403):
            raise MarketDataError("Alpaca refused the API key")
        if response.status_code == 429:
            raise MarketDataError("Alpaca rate limit reached; the next refresh will try again")
        if response.status_code >= 400:
            raise MarketDataError(f"Alpaca answered {response.status_code}")

        body = response.json()
        if not isinstance(body, dict):
            raise MarketDataError("Alpaca answered something that is not a snapshot map")

        quotes: list[Quote] = []
        warnings: list[str] = []
        for symbol in wanted:
            snapshot = body.get(symbol)
            quote = self._from_snapshot(symbol, snapshot) if isinstance(snapshot, dict) else None
            if quote is None:
                warnings.append(
                    f"{symbol}: no quote from Alpaca (unknown symbol, or no trades yet)"
                )
            else:
                quotes.append(quote)
        return quotes, warnings

    def _from_snapshot(self, symbol: str, snapshot: dict) -> Quote | None:
        trade = snapshot.get("latestTrade") or {}
        daily = snapshot.get("dailyBar") or {}
        previous = snapshot.get("prevDailyBar") or {}
        price = _decimal(trade.get("p")) or _decimal(daily.get("c"))
        if price is None:
            return None
        stamp = trade.get("t") or daily.get("t")
        try:
            as_of = datetime.fromisoformat(str(stamp).replace("Z", "+00:00")) if stamp else None
        except ValueError:
            as_of = None
        return Quote(
            symbol=symbol,
            price=price,
            previous_close=_decimal(previous.get("c")),
            as_of=as_of or datetime.now(UTC),
            source=self.name,
        )


def provider_from_env(environ: dict[str, str] | None = None) -> Provider:
    """The configured provider, or ``MarketDataUnavailable`` when there is none.

    Read at request time rather than import time so a test can point it anywhere, and so the
    container's environment — not the build's — decides.
    """
    env = os.environ if environ is None else environ
    choice = env.get(PROVIDER_VARIABLE, "none").strip().lower()
    if choice in ("", "none", "off"):
        raise MarketDataUnavailable()
    if choice == "fake":
        return FakeProvider()
    if choice == "alpaca":
        return AlpacaProvider(
            key=env.get("ALPACA_API_KEY", ""),
            secret=env.get("ALPACA_API_SECRET", ""),
            base_url=env.get("ALPACA_DATA_URL", ALPACA_DATA_URL),
            feed=env.get("ALPACA_FEED", "iex"),
        )
    raise MarketDataError(f"{PROVIDER_VARIABLE}={choice!r} is not a provider this service knows")
