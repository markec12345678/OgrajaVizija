import json
import os
from pathlib import Path

from fastapi.testclient import TestClient


def test_inquiry_roundtrip(tmp_path: Path, monkeypatch):
    monkeypatch.setenv("OVIZ_DATA", str(tmp_path))
    monkeypatch.setenv("OVIZ_MODELS", str(tmp_path / "models"))
    monkeypatch.setenv("OVIZ_INQUIRY_TOKEN", "test-token")

    import importlib
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)

    client = TestClient(app.main.app)
    headers = {"Authorization": "Bearer test-token"}
    project = {
        "id": "p_test",
        "name": "Test Roksal",
        "config": {
            "category": "OGRAJA",
            "customerName": "Test Stranka",
        },
    }
    payload = {"project": project, "inquiryText": "ROKSAL POVPRAŠEVANJE"}

    response = client.post(
        "/inquiries",
        headers=headers,
        data={"payload": json.dumps(payload)},
        files={
            "original": ("original.jpg", b"fake-jpg", "image/jpeg"),
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    inquiry_id = body["inquiryId"]

    listing = client.get("/inquiries", headers=headers)
    assert listing.status_code == 200
    assert listing.json()[0]["id"] == inquiry_id

    detail = client.get(f"/inquiries/{inquiry_id}", headers=headers)
    assert detail.status_code == 200
    assert detail.json()["project"]["id"] == "p_test"
    assert "original.jpg" in detail.json()["attachments"]

    updated = client.patch(
        f"/inquiries/{inquiry_id}",
        headers=headers,
        json={"status": "ROKSAL_REVIEW"},
    )
    assert updated.status_code == 200
    assert updated.json()["status"] == "ROKSAL_REVIEW"

    health = client.get("/inquiries/health", headers=headers)
    assert health.status_code == 200
    assert health.json()["total"] == 1
    assert health.json()["lastStatus"] == "ROKSAL_REVIEW"


def test_inquiry_requires_token(tmp_path: Path, monkeypatch):
    monkeypatch.setenv("OVIZ_DATA", str(tmp_path))
    monkeypatch.setenv("OVIZ_MODELS", str(tmp_path / "models"))
    monkeypatch.setenv("OVIZ_INQUIRY_TOKEN", "test-token")

    import importlib
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)

    client = TestClient(app.main.app)
    response = client.get("/inquiries")
    assert response.status_code == 401
