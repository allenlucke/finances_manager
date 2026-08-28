"""FastAPI entrypoint for the AI service.

Internal network only. This service is never exposed to the browser and holds no credentials —
the Java API is the only client. See docs/ARCHITECTURE.md and docs/SECURITY.md.
"""

from __future__ import annotations

import logging

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
from finances_ai.models import (
    CategorizeRequest,
    CategorizeResponse,
    ParseResult,
    PositionsResult,
)

logger = logging.getLogger(__name__)

app = FastAPI(
    title="finances-ai",
    version=__version__,
    description="Statement parsing and categorization for finances_manager",
)


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
        result = parse_csv(content, account_ref=account_ref)
    except ParserNotFoundError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except UnicodeDecodeError as exc:
        raise HTTPException(status_code=422, detail="File is not valid UTF-8 text") from exc

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
        result = parse_ofx(content, account_ref=account_ref)
    except OfxParseError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

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
        result = parse_positions(content)
    except PositionsParseError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    # Counts only. Never log symbols, values, or account identifiers.
    logger.info(
        "parsed positions rows=%d accounts=%d warnings=%d",
        len(result.positions),
        len({position.account_key for position in result.positions}),
        len(result.warnings),
    )
    return result


@app.post("/categorize", response_model=CategorizeResponse)
def categorize_endpoint(request: CategorizeRequest) -> CategorizeResponse:
    """Suggest categories. Advisory only — the API decides what to persist."""
    return CategorizeResponse(suggestions=categorize(request.transactions))
