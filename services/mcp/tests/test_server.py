"""The tools Claude Code actually calls (D-17)."""

import json

import httpx
import pytest

from finances_mcp import server
from finances_mcp.client import FinancesClient

TOKEN = "test-token-that-is-long-enough-to-be-accepted"


@pytest.fixture
def api(monkeypatch):
    """Swaps in a stub API and records what each tool sent."""
    calls: list[httpx.Request] = []
    responses: dict[str, httpx.Response] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        key = f"{request.method} {request.url.path}"
        return responses.get(key, httpx.Response(200, json={"ok": True}))

    stub = FinancesClient(
        base_url="http://api.test", token=TOKEN, transport=httpx.MockTransport(handler)
    )
    monkeypatch.setattr(server, "_client", stub)

    class Harness:
        def __init__(self) -> None:
            self.calls = calls
            self.responses = responses

        def reply(self, method: str, path: str, status: int, body=None) -> None:
            self.responses[f"{method} {path}"] = httpx.Response(status, json=body)

        @property
        def last(self) -> httpx.Request:
            return self.calls[-1]

    return Harness()


def test_amounts_travel_as_strings_not_floats(api):
    """Money is never a float, at any layer — including this one."""
    server.record_transaction(
        account_id=1,
        transaction_date="2026-08-14",
        amount="84.31",
        direction="debit",
        description="KROGER #4521",
    )

    body = json.loads(api.last.read())
    # The type is the assertion. A float would serialize to something that reads identically, so
    # eyeballing the JSON text proves nothing — 84.31 has no exact binary representation and the
    # error compounds once these are summed.
    assert body["amount"] == "84.31"
    assert isinstance(body["amount"], str)


def test_a_transfer_sends_both_the_flag_and_the_other_account(api):
    """transferAccountId alone silently records a one-sided transaction — the API is explicit."""
    server.record_transaction(
        account_id=1,
        transaction_date="2026-08-14",
        amount="500.00",
        direction="debit",
        description="Payment to card",
        transfer=True,
        transfer_account_id=2,
    )

    body = json.loads(api.last.read())
    assert body["transfer"] is True
    assert body["transferAccountId"] == 2


def test_a_plain_purchase_does_not_send_a_transfer_flag(api):
    server.record_transaction(
        account_id=1,
        transaction_date="2026-08-14",
        amount="84.31",
        direction="debit",
        description="KROGER",
    )

    # Absent, not false: the API branches on transfer being true, and a stray flag here would
    # turn an ordinary purchase into a two-legged transfer against an account nobody named.
    assert "transfer" not in json.loads(api.last.read())


def test_clearing_a_category_sends_an_explicit_null(api):
    """The one place a null must survive: it is how a category is removed."""
    server.categorize_transaction(transaction_id=5, category_id=None)

    assert api.last.method == "PUT"
    # _guard strips None from bodies, so clearing has to still reach the API as a real request.
    assert api.last.url.path == "/api/v1/transactions/5/category"


def test_delete_reports_how_to_undo_itself(api):
    api.reply("DELETE", "/api/v1/transactions/9", 204)

    result = server.delete_transaction(9)

    # Allen chose to let automation delete things. Handing back the undo makes that recoverable in
    # the same breath rather than something to go looking for afterwards.
    assert result["reversible"] is True
    assert "restore_transaction(9)" in result["undo_with"]


def test_a_refusal_comes_back_as_a_message_not_an_exception(api):
    api.reply(
        "PUT", "/api/v1/transactions/5/category", 422, {"message": "Transfers have no category"}
    )

    result = server.categorize_transaction(transaction_id=5, category_id=3)

    assert result["status"] == 422
    assert "Transfers" in result["error"]


def test_a_server_fault_is_raised_rather_than_reported_as_a_result(api):
    """A 500 is not the model's to work around, and hiding one hides an outage."""
    api.reply("GET", "/api/v1/accounts", 500, {"message": "boom"})

    with pytest.raises(Exception, match="boom"):
        server.list_accounts()


def test_importing_a_file_that_is_not_there_says_so(api):
    result = server.import_statement("/nowhere/at/all.csv")

    assert result["status"] == 400
    assert "No file at" in result["error"]
    assert api.calls == []


def test_importing_reads_the_file_and_posts_it(api, tmp_path):
    statement = tmp_path / "cacu.csv"
    statement.write_text("Date,Amount\n2026-08-14,-84.31\n")
    api.reply("POST", "/api/v1/imports", 201, {"id": 1, "appliedCount": 1})

    result = server.import_statement(str(statement), account_id=7)

    assert result["appliedCount"] == 1
    assert b"cacu.csv" in api.last.read()


def test_finding_statements_lists_only_importable_files(api, tmp_path):
    (tmp_path / "cacu.csv").write_text("x")
    (tmp_path / "statement.qfx").write_text("x")
    (tmp_path / "holiday.jpg").write_text("x")
    (tmp_path / "notes.txt").write_text("x")

    found = server.find_statement_files(str(tmp_path))

    assert {item["name"] for item in found} == {"cacu.csv", "statement.qfx"}
    # Nothing was read — this only reports metadata, so it is safe to run over a real Downloads
    # folder without pulling financial data into the conversation.
    assert api.calls == []


def test_finding_statements_in_a_missing_directory_says_so(api):
    result = server.find_statement_files("/nowhere/at/all")

    assert result["status"] == 400


def test_date_filters_use_the_names_the_api_expects(api):
    """`from` is a Python keyword, so the tool argument cannot be called that."""
    server.list_transactions(date_from="2026-08-01", date_to="2026-08-31")

    query = str(api.last.url)
    assert "from=2026-08-01" in query
    assert "to=2026-08-31" in query
