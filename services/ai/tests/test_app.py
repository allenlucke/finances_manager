import io

from fastapi.testclient import TestClient

from finances_ai.app import app

client = TestClient(app)


def test_health():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_formats_lists_known_parsers():
    response = client.get("/formats")

    assert response.status_code == 200
    assert "chase_card" in response.json()["csv"]


def test_parse_csv_endpoint():
    csv_bytes = b"Date,Description,Amount\n2026-08-14,COFFEE,-4.50\n"
    response = client.post(
        "/parse/csv?account_ref=test",
        files={"file": ("statement.csv", io.BytesIO(csv_bytes), "text/csv")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["source_format"] == "generic"
    assert len(body["transactions"]) == 1


def test_parse_csv_rejects_unknown_format():
    response = client.post(
        "/parse/csv",
        files={"file": ("x.csv", io.BytesIO(b"A,B\n1,2\n"), "text/csv")},
    )

    assert response.status_code == 422


def test_parse_csv_accepts_a_windows_encoded_file_and_reports_it():
    """A cp1252 export used to be a 422 for one curly apostrophe. Now it is read, and flagged."""
    csv_bytes = "Date,Description,Amount\n2026-08-14,CAF\xc9 DU MONDE,-4.50\n".encode("cp1252")
    response = client.post(
        "/parse/csv?account_ref=test",
        files={"file": ("statement.csv", io.BytesIO(csv_bytes), "text/csv")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["transactions"][0]["description"] == "CAFÉ DU MONDE"
    assert any("Windows-1252" in warning for warning in body["warnings"])


def test_parse_csv_refuses_an_unreadable_file_with_a_422_not_a_500():
    csv_bytes = b"Date,Description,Amount\n2026-08-14," + b"x" * 200_000 + b",-1.00\n"
    response = client.post(
        "/parse/csv?account_ref=test",
        files={"file": ("statement.csv", io.BytesIO(csv_bytes), "text/csv")},
    )

    assert response.status_code == 422
    assert "Not readable as CSV" in response.json()["detail"]


def _row(description: str) -> dict:
    return {
        "transaction_date": "2026-08-14",
        "description": description,
        "merchant": description,
        "amount": "10.00",
        "direction": "debit",
        "dedupe_key": "k" * 32,
    }


def test_categorize_endpoint_threads_the_account_type_through():
    payload = {"transactions": [_row("PAYMENT THANK YOU - WEB"), _row("KROGER #4521")]}

    as_card = client.post("/categorize", json={**payload, "account_type": "credit_card"})
    as_checking = client.post("/categorize", json={**payload, "account_type": "checking"})
    unknown = client.post("/categorize", json=payload)

    assert as_card.status_code == as_checking.status_code == unknown.status_code == 200
    assert [s["is_transfer"] for s in as_card.json()["suggestions"]] == [True, False]
    assert [s["is_transfer"] for s in as_checking.json()["suggestions"]] == [False, False]
    assert [s["is_transfer"] for s in unknown.json()["suggestions"]] == [True, False]


def test_categorize_endpoint_says_none_when_no_rule_fired():
    response = client.post("/categorize", json={"transactions": [_row("SOME NEW PLACE")]})

    assert response.status_code == 200
    suggestion = response.json()["suggestions"][0]
    assert suggestion["method"] == "none"
    assert suggestion["category"] is None


def test_parsing_is_refused_without_the_account_key_secret(monkeypatch):
    """A weaker key is not a fallback. The service says what is missing and answers 503."""
    monkeypatch.setenv("ACCOUNT_KEY_SECRET", "")
    response = client.post(
        "/parse/csv?account_ref=test",
        files={
            "file": (
                "x.csv",
                io.BytesIO(b"Date,Description,Amount\n2026-08-14,X,-1.00\n"),
                "text/csv",
            )
        },
    )

    assert response.status_code == 503
    assert "ACCOUNT_KEY_SECRET" in response.json()["detail"]


def test_the_service_refuses_to_start_without_the_secret(monkeypatch):
    from fastapi.testclient import TestClient as Client

    monkeypatch.setenv("ACCOUNT_KEY_SECRET", "")
    try:
        with Client(app):
            raise AssertionError("started without ACCOUNT_KEY_SECRET")
    except RuntimeError as refused:
        assert "ACCOUNT_KEY_SECRET" in str(refused)
