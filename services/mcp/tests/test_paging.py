"""Every listing tool answers the same shape (D-17)."""

import httpx
import pytest

from finances_mcp import server
from finances_mcp.client import FinancesClient

TOKEN = "test-token-that-is-long-enough-to-be-accepted"


@pytest.fixture
def api(monkeypatch):
    body: dict = {}

    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=body["value"])

    monkeypatch.setattr(
        server,
        "_client",
        FinancesClient(
            base_url="http://api.test", token=TOKEN, transport=httpx.MockTransport(handler)
        ),
    )
    return body


def test_a_spring_page_is_flattened_to_transactions(api):
    """The trap: iterating the raw envelope yields field names, and nothing raises."""
    api["value"] = {"content": [{"id": 1}, {"id": 2}], "totalElements": 7}

    result = server.list_transactions()

    assert result["transactions"] == [{"id": 1}, {"id": 2}]
    assert result["total"] == 7


def test_a_bare_list_is_wrapped_the_same_way(api):
    """The deleted listing returns a plain array; callers should not have to know that."""
    api["value"] = [{"id": 3}]

    result = server.list_deleted_transactions()

    assert result["transactions"] == [{"id": 3}]
    assert result["total"] == 1
    assert result["has_more"] is False


def test_has_more_is_stated_rather_than_left_to_arithmetic(api):
    api["value"] = {"content": [{"id": 1}] * 50, "totalElements": 120}

    assert server.list_transactions(page=0, size=50)["has_more"] is True
    assert server.list_transactions(page=2, size=50)["has_more"] is False


def test_an_error_passes_through_unwrapped(api):
    """A refusal must not be dressed up as an empty page — that reads as "no transactions"."""
    api["value"] = {"content": [], "totalElements": 0}
    server._client._client._transport = httpx.MockTransport(
        lambda _: httpx.Response(400, json={"message": "Bad date"})
    )

    result = server.list_transactions(date_from="not-a-date")

    assert result["status"] == 400
    assert "transactions" not in result


def test_a_plain_object_is_returned_as_itself_not_as_one_transaction(api):
    """len(dict) is a key count. Wrapping it produced {"transactions": {...}, "total": 1} — exactly
    the trap the helper exists to prevent."""
    api["value"] = {"ok": True, "note": "not a listing"}

    result = server.list_transactions()

    assert result == {"ok": True, "note": "not a listing"}


def test_has_more_prefers_the_envelope_over_arithmetic(api):
    api["value"] = {"content": [{"id": 1}], "totalElements": 1, "last": False}

    assert server.list_transactions(page=0, size=50)["has_more"] is True


def test_a_full_bare_page_says_there_may_be_more(api):
    """The deleted listing is a bare list capped by size. A full page used to answer has_more:
    False, which the model read as "that is all of them"."""
    api["value"] = [{"id": n} for n in range(100)]

    assert server.list_deleted_transactions(page=0, size=100)["has_more"] is True
    api["value"] = [{"id": 1}]
    assert server.list_deleted_transactions(page=1, size=100)["has_more"] is False
