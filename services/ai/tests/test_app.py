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
