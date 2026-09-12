"""HTTP access to services/api, authenticated with the local token (D-17).

Deliberately thin. The API is the system of record and owns every rule about money; this module
adds no logic of its own, so there is no second place for the rules to drift to. Its whole job is
to attach the token, unwrap responses, and turn failures into messages a model can act on.
"""

from __future__ import annotations

import os
from typing import Any

import httpx

DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_TIMEOUT = 30.0


class ApiError(RuntimeError):
    """A request the API refused.

    Carries the status code so callers can tell "you asked for something impossible" (4xx) from
    "the server broke" (5xx). The message is written for a model to read and relay: a bare
    ``HTTP 422`` tells the user nothing they can act on.
    """

    def __init__(self, status: int, detail: str) -> None:
        super().__init__(detail)
        self.status = status
        self.detail = detail


class FinancesClient:
    """Calls the finances API as the account owner."""

    def __init__(
        self,
        base_url: str | None = None,
        token: str | None = None,
        timeout: float = DEFAULT_TIMEOUT,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self.base_url = (base_url or os.environ.get("API_URL") or DEFAULT_BASE_URL).rstrip("/")
        self.token = token if token is not None else os.environ.get("LOCAL_API_TOKEN", "")
        if not self.token:
            raise RuntimeError(
                "LOCAL_API_TOKEN is not set. Generate one with `make mcp-token`, put it in .env, "
                "and restart the API so it picks the value up."
            )
        self._client = httpx.Client(
            base_url=self.base_url,
            timeout=timeout,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/json",
            },
            # HTTP/1.1 only. The Java service is behind the same reasoning recorded in CLAUDE.md
            # for services/ai — an h2c upgrade attempt against a server that does not speak it
            # mangles request framing, and multipart is where that shows up.
            http2=False,
            # Tests substitute a transport here rather than starting a server. Everything above it
            # — headers, token, unwrapping — is exercised exactly as in production.
            transport=transport,
        )

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> FinancesClient:
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def _unwrap(self, response: httpx.Response) -> Any:
        if response.is_success:
            if not response.content:
                return None
            try:
                return response.json()
            except ValueError:
                return response.text

        # Spring puts the useful part in "message" or "detail" depending on which handler ran; a
        # validation failure carries its reasons under "fields" with no detail at all.
        detail = response.text
        try:
            body = response.json()
        except ValueError:
            body = None
        if isinstance(body, dict):
            fields = body.get("fields")
            described = (
                "; ".join(f"{name}: {reason}" for name, reason in fields.items())
                if isinstance(fields, dict) and fields
                else None
            )
            detail = (
                body.get("message")
                or body.get("detail")
                or described
                or body.get("error")
                or detail
            )

        if response.status_code == 401:
            detail = (
                "The API rejected the local token. Check that LOCAL_API_TOKEN here matches the "
                "one the API was started with, and that the API is reachable on loopback."
            )
        raise ApiError(response.status_code, detail)

    def get(self, path: str, **params: Any) -> Any:
        clean = {k: v for k, v in params.items() if v is not None}
        return self._unwrap(self._client.get(path, params=clean))

    def post(self, path: str, body: dict[str, Any] | None = None) -> Any:
        clean = {k: v for k, v in (body or {}).items() if v is not None}
        return self._unwrap(self._client.post(path, json=clean))

    def put(self, path: str, body: dict[str, Any] | None = None) -> Any:
        clean = {k: v for k, v in (body or {}).items() if v is not None}
        return self._unwrap(self._client.put(path, json=clean))

    def delete(self, path: str) -> Any:
        return self._unwrap(self._client.delete(path))

    def upload(self, path: str, filename: str, content: bytes, account_id: int | None) -> Any:
        """Posts a statement file as multipart.

        Note what is *not* set: a Content-Type header. Setting ``multipart/form-data`` by hand
        pins it without a boundary and the receiver parses zero parts — the exact failure recorded
        in CLAUDE.md against services/ai. httpx writes the header, boundary and all.
        """
        params = {"accountId": account_id} if account_id is not None else {}
        return self._unwrap(
            self._client.post(
                path,
                params=params,
                files={"file": (filename, content, "application/octet-stream")},
            )
        )
