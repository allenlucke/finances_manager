"""Market data (M7): the provider seam, the vendor parsing, and the endpoint's refusals."""

from decimal import Decimal

import httpx
import pytest
from fastapi.testclient import TestClient

from finances_ai.app import app
from finances_ai.market import (
    AlpacaProvider,
    FakeProvider,
    MarketDataError,
    MarketDataUnavailable,
    provider_from_env,
)

client = TestClient(app)

SNAPSHOT = {
    "AAPL": {
        "latestTrade": {"t": "2026-09-25T19:59:58.123Z", "p": 189.3, "s": 100},
        "dailyBar": {"t": "2026-09-25T04:00:00Z", "o": 188.0, "h": 190.1, "l": 187.5, "c": 189.3},
        "prevDailyBar": {"t": "2026-09-24T04:00:00Z", "c": 187.1},
    },
    "MSFT": {
        "latestTrade": {"t": "2026-09-25T19:59:59Z", "p": 412.05},
        "prevDailyBar": {"c": 415.6},
    },
}


def alpaca(handler) -> AlpacaProvider:
    return AlpacaProvider(
        key="k", secret="s", base_url="https://data.test", transport=httpx.MockTransport(handler)
    )


def test_the_fake_provider_is_deterministic_and_says_it_is_fake():
    first, _ = FakeProvider().quotes(["aapl", "AAPL", " msft ", "ZZZQ"])
    second, _ = FakeProvider().quotes(["AAPL", "MSFT", "ZZZQ"])

    assert [q.symbol for q in first] == ["AAPL", "MSFT", "ZZZQ"]
    assert [q.price for q in first] == [q.price for q in second]
    assert all(q.source == "fake" for q in first)
    assert all(isinstance(q.price, Decimal) and q.price > 0 for q in first)


def test_alpaca_snapshots_become_decimal_quotes_with_the_previous_close():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["key"] = request.headers.get("APCA-API-KEY-ID")
        return httpx.Response(200, json=SNAPSHOT)

    quotes, warnings = alpaca(handler).quotes(["AAPL", "MSFT"])

    assert seen["key"] == "k"
    assert "symbols=AAPL%2CMSFT" in seen["url"] or "symbols=AAPL,MSFT" in seen["url"]
    assert "feed=iex" in seen["url"]
    assert warnings == []
    by_symbol = {q.symbol: q for q in quotes}
    # Through str(), never Decimal(float): 189.3 stays 189.3.
    assert by_symbol["AAPL"].price == Decimal("189.3")
    assert by_symbol["AAPL"].previous_close == Decimal("187.1")
    assert by_symbol["AAPL"].as_of.isoformat().startswith("2026-09-25T19:59:58")
    assert by_symbol["MSFT"].previous_close == Decimal("415.6")
    assert all(q.source == "alpaca" for q in quotes)


def test_a_symbol_alpaca_does_not_know_is_a_warning_not_a_silent_gap():
    quotes, warnings = alpaca(lambda _: httpx.Response(200, json=SNAPSHOT)).quotes(["AAPL", "NOPE"])

    assert [q.symbol for q in quotes] == ["AAPL"]
    assert warnings == ["NOPE: no quote from Alpaca (unknown symbol, or no trades yet)"]


@pytest.mark.parametrize(
    ("status", "expected"),
    [(401, "refused the API key"), (429, "rate limit"), (500, "answered 500")],
)
def test_vendor_refusals_are_sentences(status, expected):
    with pytest.raises(MarketDataError, match=expected):
        alpaca(lambda _: httpx.Response(status, json={"message": "x"})).quotes(["AAPL"])


def test_a_vendor_that_cannot_be_reached_is_a_sentence_too():
    def down(_: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("boom")

    with pytest.raises(MarketDataError, match="could not be reached"):
        alpaca(down).quotes(["AAPL"])


def test_provider_selection_defaults_to_none_and_refuses_the_unknown():
    with pytest.raises(MarketDataUnavailable):
        provider_from_env({})
    with pytest.raises(MarketDataUnavailable):
        provider_from_env({"MARKET_DATA_PROVIDER": "none"})
    assert provider_from_env({"MARKET_DATA_PROVIDER": "fake"}).name == "fake"
    with pytest.raises(MarketDataError, match="not a provider"):
        provider_from_env({"MARKET_DATA_PROVIDER": "bloomberg"})
    with pytest.raises(MarketDataError, match="ALPACA_API_KEY"):
        provider_from_env({"MARKET_DATA_PROVIDER": "alpaca"})


def test_the_endpoint_says_market_data_is_off_when_no_provider_is_configured(monkeypatch):
    monkeypatch.delenv("MARKET_DATA_PROVIDER", raising=False)

    response = client.get("/market/quotes", params={"symbols": "AAPL"})
    status = client.get("/market/status")

    assert response.status_code == 503
    assert "MARKET_DATA_PROVIDER" in response.json()["detail"]
    assert status.json() == {
        "provider": "none",
        "available": False,
        "detail": response.json()["detail"],
    }


def test_the_endpoint_serves_the_fake_provider_and_labels_it(monkeypatch):
    monkeypatch.setenv("MARKET_DATA_PROVIDER", "fake")

    response = client.get("/market/quotes", params={"symbols": "AAPL,MSFT"})

    assert response.status_code == 200
    body = response.json()
    assert body["provider"] == "fake"
    assert [q["symbol"] for q in body["quotes"]] == ["AAPL", "MSFT"]
    # Decimal on the wire as a string, like every amount this service sends.
    assert body["quotes"][0]["price"] == "189.30"
    assert body["quotes"][0]["source"] == "fake"
    assert client.get("/market/status").json()["provider"] == "fake"


def test_the_endpoint_refuses_an_empty_symbol_list(monkeypatch):
    monkeypatch.setenv("MARKET_DATA_PROVIDER", "fake")

    assert client.get("/market/quotes", params={"symbols": " , "}).status_code == 422


def test_quotes_are_never_logged_with_symbols_or_prices(monkeypatch, caplog):
    """A watchlist says what someone is thinking of buying. Counts only, like every other log."""
    monkeypatch.setenv("MARKET_DATA_PROVIDER", "fake")
    with caplog.at_level("INFO", logger="finances_ai.app"):
        client.get("/market/quotes", params={"symbols": "AAPL"})

    ours = [r.getMessage() for r in caplog.records if r.name.startswith("finances_ai")]
    assert ours, "the service logs one line per request"
    joined = " ".join(ours)
    assert "AAPL" not in joined and "189" not in joined
