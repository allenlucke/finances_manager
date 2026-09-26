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


def test_importing_a_file_that_is_not_there_says_so(api, tmp_path, monkeypatch):
    monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path))
    result = server.import_statement(str(tmp_path / "missing.csv"))

    assert result["status"] == 400
    assert "No file at" in result["error"]
    assert api.calls == []


def test_importing_reads_the_file_and_posts_it(api, tmp_path, monkeypatch):
    monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path))
    statement = tmp_path / "cacu.csv"
    statement.write_text("Date,Amount\n2026-08-14,-84.31\n")
    api.reply("POST", "/api/v1/imports", 201, {"id": 1, "appliedCount": 1})

    result = server.import_statement(str(statement), account_id=7)

    assert result["appliedCount"] == 1
    assert b"cacu.csv" in api.last.read()


def test_finding_statements_lists_only_importable_files(api, tmp_path, monkeypatch):
    monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path))
    (tmp_path / "cacu.csv").write_text("x")
    (tmp_path / "statement.qfx").write_text("x")
    (tmp_path / "holiday.jpg").write_text("x")
    (tmp_path / "notes.txt").write_text("x")

    found = server.find_statement_files(str(tmp_path))

    assert {item["name"] for item in found} == {"cacu.csv", "statement.qfx"}
    # Nothing was read — this only reports metadata, so it is safe to run over a real Downloads
    # folder without pulling financial data into the conversation.
    assert api.calls == []


def test_finding_statements_in_a_missing_directory_says_so(api, tmp_path, monkeypatch):
    monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path))
    result = server.find_statement_files(str(tmp_path / "nowhere"))

    assert result["status"] == 400


def test_date_filters_use_the_names_the_api_expects(api):
    """`from` is a Python keyword, so the tool argument cannot be called that."""
    server.list_transactions(date_from="2026-08-01", date_to="2026-08-31")

    query = str(api.last.url)
    assert "from=2026-08-01" in query
    assert "to=2026-08-31" in query


class TestFileToolsStayInsideTheAllowedFolders:
    """The first version would read and upload any file on the machine: /etc/hosts, a key, anything.

    The person is not the threat. Statement descriptions are merchant-typed text that reaches the
    model's context, and the model chooses the path.
    """

    def test_a_file_outside_every_root_is_refused_before_it_is_read(
        self, api, tmp_path, monkeypatch
    ):
        monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path / "allowed"))
        (tmp_path / "allowed").mkdir()
        outside = tmp_path / "elsewhere.csv"
        outside.write_text("Date,Amount\n")

        result = server.import_statement(str(outside))

        assert result["status"] == 400
        assert "outside the folders" in result["error"]
        assert api.calls == []

    def test_a_traversal_is_judged_on_where_it_lands(self, api, tmp_path, monkeypatch):
        allowed = tmp_path / "allowed"
        allowed.mkdir()
        monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(allowed))
        (tmp_path / "secret.csv").write_text("x")

        result = server.import_statement(str(allowed / ".." / "secret.csv"))

        assert result["status"] == 400
        assert api.calls == []

    def test_a_symlink_pointing_out_is_refused(self, api, tmp_path, monkeypatch):
        allowed = tmp_path / "allowed"
        allowed.mkdir()
        monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(allowed))
        target = tmp_path / "outside.csv"
        target.write_text("x")
        (allowed / "looks-fine.csv").symlink_to(target)

        result = server.import_statement(str(allowed / "looks-fine.csv"))

        assert result["status"] == 400
        assert api.calls == []

    def test_only_statement_suffixes_are_uploaded(self, api, tmp_path, monkeypatch):
        monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path))
        (tmp_path / "id_rsa").write_text("x")

        result = server.import_statement(str(tmp_path / "id_rsa"))

        assert result["status"] == 400
        assert "not a statement file" in result["error"]
        assert api.calls == []

    def test_listing_outside_the_roots_is_refused(self, api, tmp_path, monkeypatch):
        monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path / "allowed"))
        (tmp_path / "allowed").mkdir()

        result = server.find_statement_files("/etc")

        assert result["status"] == 400
        assert api.calls == []

    def test_positions_import_is_guarded_the_same_way(self, api, tmp_path, monkeypatch):
        monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path / "allowed"))
        (tmp_path / "allowed").mkdir()

        result = server.import_positions("/etc/hosts")

        assert result["status"] == 400
        assert api.calls == []


def test_a_positions_import_says_updated_not_duplicate(api, tmp_path, monkeypatch):
    """The API reuses the statement batch's duplicateCount for holdings refreshed in place.

    Nothing was skipped, so the tool must not hand Claude a number labelled as if it were.
    """
    monkeypatch.setenv("FINANCES_IMPORT_ROOTS", str(tmp_path))
    positions = tmp_path / "Portfolio_Positions.csv"
    positions.write_text("Account number,Symbol,Current value\nX1,FXAIX,$10.00\n")
    api.reply(
        "POST", "/api/v1/imports/positions", 201, {"id": 1, "appliedCount": 2, "duplicateCount": 5}
    )

    result = server.import_positions(str(positions))

    assert result["appliedCount"] == 2
    assert result["updatedCount"] == 5
    assert "duplicateCount" not in result
    assert "updated" in result["counts"]


def test_creating_an_account_can_carry_the_link_an_import_reported(api):
    """The other half of ``unlinkedAccounts``: without this, Claude could see which accounts a
    file named but could not create them in a way the retry would match."""
    api.reply("POST", "/api/v1/accounts", 201, {"id": 9})

    server.create_account(
        "Individual - TOD", "brokerage", 1, mask="8901", external_id="a1b2c3d4e5f60718"
    )

    sent = json.loads(api.last.read())
    assert sent["externalId"] == "a1b2c3d4e5f60718"
    assert sent["mask"] == "8901"


def test_creating_an_account_without_a_link_sends_none(api):
    api.reply("POST", "/api/v1/accounts", 201, {"id": 9})

    server.create_account("Checking", "checking", 1)

    # The client drops nulls from the body, so the link is simply absent — never a blank string,
    # which the API would store as a real (empty) link.
    assert json.loads(api.last.read()).get("externalId") is None


def test_un_marking_a_transfer_puts_the_flag_to_the_transfer_endpoint(api):
    """A row wrongly called a transfer has a way back (review 2026-09-11, P8); the tool is one
    PUT and adds no rule of its own."""
    import json

    server.set_transfer(9, False)

    assert api.last.method == "PUT"
    assert api.last.url.path == "/api/v1/transactions/9/transfer"
    assert json.loads(api.last.content) == {"transfer": False}


def test_removing_a_checkpoint_deletes_the_statement(api):
    api.reply("DELETE", "/api/v1/statements/4", 204)

    result = server.delete_checkpoint(4)

    assert api.last.method == "DELETE"
    assert api.last.url.path == "/api/v1/statements/4"
    assert result == {"deleted_checkpoint": 4}


def test_monthly_totals_uses_the_names_the_api_expects(api):
    server.monthly_totals(date_from="2026-08-01", date_to="2026-08-31")

    assert api.last.url.path == "/api/v1/reports/monthly-totals"
    assert dict(api.last.url.params) == {"from": "2026-08-01", "to": "2026-08-31"}


def test_watching_a_symbol_posts_it_to_the_watchlist(api):
    import json

    server.watch_symbol("aapl", note="maybe")

    assert api.last.method == "POST"
    assert api.last.url.path == "/api/v1/market/watchlist"
    assert json.loads(api.last.content) == {"symbol": "aapl", "note": "maybe"}


def test_a_price_alert_sends_the_threshold_as_a_string_never_a_float(api):
    import json

    server.set_price_alert("AAPL", "above", "190.50")

    body = json.loads(api.last.content)
    assert body["threshold"] == "190.50"
    assert isinstance(body["threshold"], str)
    assert body["rule"] == "above"


def test_refreshing_quotes_is_one_post(api):
    server.refresh_quotes()

    assert api.last.method == "POST"
    assert api.last.url.path == "/api/v1/market/quotes/refresh"


def test_holdings_at_market_and_alert_events_read_the_market_endpoints(api):
    server.holdings_at_market()
    assert api.last.url.path == "/api/v1/market/holdings"

    server.alert_events(size=10)
    assert api.last.url.path == "/api/v1/market/alerts/events"
    assert dict(api.last.url.params) == {"size": "10"}


def test_a_proposed_order_is_recorded_as_the_assistants_and_sends_strings(api):
    import json

    server.propose_order(
        "AAPL", "buy", "2", order_type="limit", limit_price="185.50", rationale="dip"
    )

    body = json.loads(api.last.content)
    assert api.last.url.path == "/api/v1/orders"
    assert body["proposedBy"] == "assistant"
    assert body["quantity"] == "2" and body["limitPrice"] == "185.50"
    assert isinstance(body["quantity"], str)


def test_confirming_restates_the_order_and_names_the_assistant_as_actor(api):
    import json

    server.confirm_order(7, "AAPL", "buy", "2", limit_price="185.50")

    assert api.last.method == "POST"
    assert api.last.url.path == "/api/v1/orders/7/confirm"
    body = json.loads(api.last.content)
    assert body == {
        "symbol": "AAPL",
        "side": "buy",
        "quantity": "2",
        "limitPrice": "185.50",
        "actor": "assistant",
    }


def test_the_confirm_tool_is_marked_destructive_so_the_client_asks_first():
    from mcp.server.mcpserver import MCPServer  # noqa: F401 — the import documents the contract

    tool = next(t for t in server.mcp._tool_manager.list_tools() if t.name == "confirm_order")
    assert tool.annotations.destructive_hint is True
    proposed = next(t for t in server.mcp._tool_manager.list_tools() if t.name == "propose_order")
    assert proposed.annotations.destructive_hint is False


def test_a_backtest_request_sends_strings_and_fills_defaults(api):
    import json

    server.run_backtest(
        "sma_cross", "AAPL", "1Day", "2026-01-05", "2026-09-25", params={"fast": "5"}
    )

    body = json.loads(api.last.content)
    assert api.last.url.path == "/api/v1/backtests" and api.last.method == "POST"
    assert body["params"] == {"fast": "5"}
    assert body["initialCash"] == "10000" and body["slippageBps"] == "5"
    assert body["outOfSampleFraction"] == "0.3"
    assert isinstance(body["initialCash"], str)


def test_switching_a_strategy_on_is_a_put_and_deleting_is_marked_destructive(api):
    import json

    server.set_strategy_active(4, True)

    assert api.last.method == "PUT"
    assert api.last.url.path == "/api/v1/strategies/4/active"
    assert json.loads(api.last.content) == {"active": True}
    tools = {t.name: t for t in server.mcp._tool_manager.list_tools()}
    assert tools["delete_strategy"].annotations.destructive_hint is True
    assert tools["evaluate_strategies"].annotations.destructive_hint is False
    assert "never trades by itself" in tools["save_strategy"].description


def test_a_reminder_is_added_with_its_amount_as_a_string_and_removal_is_destructive(api):
    import json

    server.add_reminder(
        "Estimated taxes", "2026-10-15", cadence="quarterly", lead_days=5, amount="1200"
    )

    body = json.loads(api.last.content)
    assert api.last.url.path == "/api/v1/reminders" and api.last.method == "POST"
    assert body["amount"] == "1200" and body["cadence"] == "quarterly" and body["leadDays"] == 5
    tools = {t.name: t for t in server.mcp._tool_manager.list_tools()}
    assert tools["delete_reminder"].annotations.destructive_hint is True
    assert tools["needs_a_look"].annotations.read_only_hint is True
    assert "Start a session here" in tools["needs_a_look"].description


def test_recurring_charges_ask_for_a_window_and_history_for_days(api):
    server.recurring_charges(days=60)
    assert api.last.url.path == "/api/v1/cashflow" and api.last.url.params["days"] == "60"

    server.net_worth_history(days=30)
    assert api.last.url.path == "/api/v1/reports/net-worth/history"
    assert api.last.url.params["days"] == "30"


def test_year_in_review_passes_only_what_was_given(api):
    server.year_in_review()
    assert api.last.url.path == "/api/v1/reports/year" and "year" not in api.last.url.params

    server.year_in_review(year=2025, ledger_entity_id=2)
    assert api.last.url.params["year"] == "2025"
    assert api.last.url.params["ledgerEntityId"] == "2"


def test_suggesting_categories_posts_and_stats_are_read_only(api):
    server.suggest_categories()
    assert api.last.method == "POST" and api.last.url.path == "/api/v1/transactions/suggest"

    server.categorization_stats()
    assert api.last.method == "GET"
    assert api.last.url.path == "/api/v1/transactions/categorization-stats"
    tools = {t.name: t for t in server.mcp._tool_manager.list_tools()}
    assert tools["categorization_stats"].annotations.read_only_hint is True
