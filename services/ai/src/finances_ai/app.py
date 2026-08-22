"""FastAPI entrypoint for the AI service.

Internal network only. This service is never exposed to the browser and holds no credentials —
the Java API is the only client. See docs/ARCHITECTURE.md and docs/SECURITY.md.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, File, HTTPException, Query, UploadFile

from finances_ai import __version__
from finances_ai.categorize import categorize
from finances_ai.ingest import ParserNotFoundError, parse_csv, registered_formats
from finances_ai.models import CategorizeRequest, CategorizeResponse, ParseResult

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
    return {"csv": registered_formats()}


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


@app.post("/categorize", response_model=CategorizeResponse)
def categorize_endpoint(request: CategorizeRequest) -> CategorizeResponse:
    """Suggest categories. Advisory only — the API decides what to persist."""
    return CategorizeResponse(suggestions=categorize(request.transactions))
