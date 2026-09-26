"""FastAPI entrypoint for the AI service.

Internal network only. This service is never exposed to the browser and holds no credentials —
the Java API is the only client. See docs/ARCHITECTURE.md and docs/SECURITY.md.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, Query, UploadFile

from finances_ai import __version__
from finances_ai.categorize import categorize
from finances_ai.ingest import (
    OfxParseError,
    ParserNotFoundError,
    PositionsParseError,
    parse_csv,
    parse_ofx,
    parse_positions,
    registered_formats,
)
from finances_ai.ingest.common import AccountKeySecretMissing, account_key_secret
from finances_ai.market import (
    BrokerError,
    BrokerUnavailable,
    MarketDataError,
    MarketDataUnavailable,
    broker_from_env,
    provider_from_env,
)
from finances_ai.models import (
    BrokerOrder,
    BrokerStatus,
    CategorizeRequest,
    CategorizeResponse,
    MarketStatus,
    OrderRequest,
    ParseResult,
    PositionsResult,
    QuotesResponse,
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def _require_secrets(_: FastAPI):
    # Refuse to start rather than derive weaker keys. The account ids the importer links rows by
    # are HMACs under ACCOUNT_KEY_SECRET; without it the only alternative is a bare hash of the
    # account number, which is the account number with extra steps. A container that will not
    # come up is visible in `docker ps`; a key that quietly leaks is not.
    account_key_secret()
    yield


app = FastAPI(
    title="finances-ai",
    version=__version__,
    description="Statement parsing and categorization for finances_manager",
    lifespan=_require_secrets,
)


def _unavailable_without_secret(exc: AccountKeySecretMissing) -> HTTPException:
    logger.error("%s", exc)
    return HTTPException(status_code=503, detail=str(exc))


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "finances-ai", "version": __version__}


@app.get("/formats")
def formats() -> dict[str, list[str]]:
    """Statement formats this service can currently parse."""
    return {
        "csv": registered_formats(),
        "ofx": ["ofx", "qfx"],
        "positions": ["fidelity_positions"],
    }


@app.post("/parse/csv", response_model=ParseResult)
async def parse_csv_endpoint(
    file: UploadFile = File(...),
    account_ref: str = Query(
        default="unknown", description="Opaque account identifier from the API"
    ),
) -> ParseResult:
    """Parse an uploaded CSV statement into normalized transactions.

    Never persists the file. The API service owns storage and retention (docs/SECURITY.md).
    """
    content = await file.read()
    try:
        # Checked before parsing, not only when a file happens to name an account: the answer to
        # "is this service configured" should not depend on which file was uploaded.
        account_key_secret()
        result = parse_csv(content, account_ref=account_ref)
    except ParserNotFoundError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except AccountKeySecretMissing as exc:
        raise _unavailable_without_secret(exc) from exc

    # Row counts only. Never log descriptions, amounts, or account identifiers.
    logger.info(
        "parsed statement format=%s rows=%d warnings=%d",
        result.source_format,
        len(result.transactions),
        len(result.warnings),
    )
    return result


@app.post("/parse/ofx", response_model=ParseResult)
async def parse_ofx_endpoint(
    file: UploadFile = File(...),
    account_ref: str = Query(
        default="unknown", description="Opaque account identifier from the API"
    ),
) -> ParseResult:
    """Parse an uploaded OFX or QFX statement.

    Unlike CSV, these carry the institution's own transaction ids and a closing balance, so the API
    can both dedupe on a provider id and create a reconciliation checkpoint.

    Never persists the file. The API service owns storage and retention (docs/SECURITY.md).
    """
    content = await file.read()
    try:
        account_key_secret()
        result = parse_ofx(content, account_ref=account_ref)
    except OfxParseError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except AccountKeySecretMissing as exc:
        raise _unavailable_without_secret(exc) from exc

    # Row counts only. Never log descriptions, amounts, or account identifiers.
    logger.info(
        "parsed statement format=%s rows=%d warnings=%d has_balance=%s",
        result.source_format,
        len(result.transactions),
        len(result.warnings),
        result.statement is not None and result.statement.closing_balance is not None,
    )
    return result


@app.post("/parse/positions", response_model=PositionsResult)
async def parse_positions_endpoint(file: UploadFile = File(...)) -> PositionsResult:
    """Parse a brokerage positions export into normalized holdings.

    A snapshot of what is held, not a history of money moving — so the result feeds holdings and
    cost basis (M4), never the transaction ledger.

    Takes no account_ref: a positions export spans the whole portfolio, and each row carries its own
    account. The API matches them by the masked number and the stable account key.
    """
    content = await file.read()
    try:
        account_key_secret()
        result = parse_positions(content)
    except PositionsParseError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except AccountKeySecretMissing as exc:
        raise _unavailable_without_secret(exc) from exc

    # Counts only. Never log symbols, values, or account identifiers.
    logger.info(
        "parsed positions rows=%d accounts=%d warnings=%d",
        len(result.positions),
        len({position.account_key for position in result.positions}),
        len(result.warnings),
    )
    return result


@app.get("/market/status", response_model=MarketStatus)
def market_status() -> MarketStatus:
    """Which market-data provider is configured, if any. Shown on the Markets screen."""
    try:
        provider = provider_from_env()
    except MarketDataUnavailable as exc:
        return MarketStatus(provider="none", available=False, detail=str(exc))
    except MarketDataError as exc:
        return MarketStatus(provider="misconfigured", available=False, detail=str(exc))
    return MarketStatus(provider=provider.name, available=True)


@app.get("/market/quotes", response_model=QuotesResponse)
def market_quotes(
    symbols: str = Query(description="Comma-separated tickers, e.g. AAPL,MSFT"),
) -> QuotesResponse:
    """Latest price and previous close for each symbol (M7).

    503 with a sentence when no provider is configured — the API reads that as "market data is
    off", not as a fault — and 502 when the vendor refused or could not be reached. Symbols the
    vendor does not know come back as warnings, never as silent gaps.
    """
    wanted = [s for s in symbols.split(",") if s.strip()]
    if not wanted:
        raise HTTPException(status_code=422, detail="No symbols given")
    if len(wanted) > 200:
        raise HTTPException(status_code=422, detail="At most 200 symbols per request")
    try:
        provider = provider_from_env()
        quotes, warnings = provider.quotes(wanted)
    except MarketDataUnavailable as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except MarketDataError as exc:
        logger.warning("market data: %s", exc)
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    # Counts only, never symbols or prices: a watchlist says what someone is thinking of buying.
    logger.info("quotes provider=%s asked=%d answered=%d", provider.name, len(wanted), len(quotes))
    return QuotesResponse(provider=provider.name, quotes=quotes, warnings=warnings)


def _broker():
    try:
        return broker_from_env()
    except BrokerUnavailable as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except BrokerError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.get("/broker/status", response_model=BrokerStatus)
def broker_status() -> BrokerStatus:
    """Whether orders can be sent at all, to which broker, and whether the market is open."""
    try:
        return broker_from_env().status()
    except BrokerUnavailable as exc:
        return BrokerStatus(broker="none", available=False, detail=str(exc))
    except BrokerError as exc:
        return BrokerStatus(broker="misconfigured", available=False, detail=str(exc))


@app.post("/broker/orders", response_model=BrokerOrder)
def broker_submit(order: OrderRequest) -> BrokerOrder:
    """Send an order the API has already confirmed (M7b).

    Nothing here decides whether to send it. The person's confirmation, the daily cap and the kill
    switch are the API's, checked against its database before this is called. 503 when no broker
    is configured; 502 with the broker's own sentence when it refused.
    """
    try:
        result = _broker().submit(order)
    except BrokerError as exc:
        logger.warning("broker submit refused: %s", exc)
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    # The id and the outcome, never the symbol or the size.
    logger.info("order submitted broker=%s status=%s", result.broker, result.status)
    return result


@app.get("/broker/orders/{broker_order_id}", response_model=BrokerOrder)
def broker_lookup(broker_order_id: str) -> BrokerOrder:
    try:
        return _broker().lookup(broker_order_id)
    except BrokerError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.delete("/broker/orders/{broker_order_id}", response_model=BrokerOrder)
def broker_cancel(broker_order_id: str) -> BrokerOrder:
    try:
        result = _broker().cancel(broker_order_id)
    except BrokerError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    logger.info("order cancelled broker=%s status=%s", result.broker, result.status)
    return result


@app.post("/categorize", response_model=CategorizeResponse)
def categorize_endpoint(request: CategorizeRequest) -> CategorizeResponse:
    """Suggest categories. Advisory only — the API decides what to persist."""
    return CategorizeResponse(
        suggestions=categorize(request.transactions, account_type=request.account_type)
    )
