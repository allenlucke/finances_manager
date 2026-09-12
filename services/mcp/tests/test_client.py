"""The HTTP layer between Claude Code and the API (D-17)."""

import httpx
import pytest

from finances_mcp.client import ApiError, FinancesClient

TOKEN = "test-token-that-is-long-enough-to-be-accepted"


def client_returning(handler) -> FinancesClient:
    return FinancesClient(
        base_url="http://api.test", token=TOKEN, transport=httpx.MockTransport(handler)
    )


def test_every_request_carries_the_bearer_token():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["auth"] = request.headers.get("Authorization")
        return httpx.Response(200, json=[])

    client_returning(handler).get("/api/v1/accounts")

    assert seen["auth"] == f"Bearer {TOKEN}"


def test_a_missing_token_fails_immediately_with_instructions(monkeypatch):
    monkeypatch.delenv("LOCAL_API_TOKEN", raising=False)

    with pytest.raises(RuntimeError, match="LOCAL_API_TOKEN"):
        FinancesClient(base_url="http://api.test")


def test_a_401_explains_the_likely_cause():
    """The raw body for a 401 is empty, which tells nobody anything."""

    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(401)

    with pytest.raises(ApiError) as caught:
        client_returning(handler).get("/api/v1/auth/me")

    assert caught.value.status == 401
    assert "LOCAL_API_TOKEN" in caught.value.detail


def test_an_error_body_is_surfaced_rather_than_the_status_alone():
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(400, json={"message": "Unknown account"})

    with pytest.raises(ApiError) as caught:
        client_returning(handler).post("/api/v1/transactions", {"accountId": 99})

    assert caught.value.detail == "Unknown account"


def test_none_valued_fields_are_dropped_rather_than_sent_as_null():
    """Spring binds an explicit null over a default, so sending one changes behaviour."""
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["body"] = request.read().decode()
        return httpx.Response(200, json={})

    client_returning(handler).post("/api/v1/accounts", {"name": "Checking", "mask": None})

    assert "mask" not in seen["body"]
    assert "Checking" in seen["body"]


def test_a_204_with_no_body_is_not_a_parse_error():
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(204)

    assert client_returning(handler).delete("/api/v1/transactions/1") is None


def test_upload_lets_httpx_write_the_multipart_header():
    """The bug this guards against is recorded in CLAUDE.md against services/ai.

    Setting Content-Type: multipart/form-data by hand pins the header without a boundary, the
    receiver parses zero parts, and the failure is reported as a *missing field* — which points at
    entirely the wrong thing. The header must carry a boundary, and the part must arrive.
    """
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["content_type"] = request.headers.get("Content-Type", "")
        seen["body"] = request.read()
        return httpx.Response(201, json={"id": 1})

    client_returning(handler).upload("/api/v1/imports", "cacu.csv", b"Date,Amount\n", 7)

    assert seen["content_type"].startswith("multipart/form-data")
    assert "boundary=" in seen["content_type"]
    assert b'name="file"' in seen["body"]
    assert b"cacu.csv" in seen["body"]


def test_upload_omits_the_account_when_the_file_names_its_own():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        return httpx.Response(201, json={"id": 1})

    client_returning(handler).upload("/api/v1/imports", "history.csv", b"x", None)

    # A brokerage export covers several accounts; nominating one would file all of it against that
    # account, which is silently wrong rather than loudly wrong.
    assert "accountId" not in seen["url"]


def test_an_error_body_that_is_not_an_object_does_not_crash_the_unwrap():
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(400, json="Bad Request")

    with pytest.raises(ApiError) as caught:
        client_returning(handler).get("/api/v1/accounts")

    assert caught.value.status == 400
    assert "Bad Request" in caught.value.detail


def test_validation_reasons_are_read_out_rather_than_returned_as_raw_json():
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            400, json={"title": "Not accepted", "fields": {"amount": "must be greater than 0"}}
        )

    with pytest.raises(ApiError) as caught:
        client_returning(handler).post("/api/v1/transactions", {"amount": "0"})

    assert caught.value.detail == "amount: must be greater than 0"
