import json
import math
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from observer.proto.positions_pb2 import StorytellerSnapshot
from observer.saved_api import create_saved_app
from observer.storyteller import Storyteller

TOKEN = "c" * 64
HEADERS = {"Authorization": "Bearer " + TOKEN, "Content-Type": "application/x-protobuf"}


@pytest.fixture
def diagnostic(tmp_path, monkeypatch):
    monkeypatch.setenv("OBSERVER_STORYTELLER_DEBUG", "1")
    monkeypatch.setenv("OBSERVER_MAPS", str(tmp_path / "maps"))
    monkeypatch.delenv("OBSERVER_POSITION_TOKEN_FILE", raising=False)
    frontend = tmp_path / "frontend"
    frontend.mkdir()
    (frontend / "index.html").write_text("<html>diagnostic frontend</html>")
    app = create_saved_app(
        data=tmp_path / "data",
        save=tmp_path / "save",
        frontend=frontend,
        polling=False,
        position_token=TOKEN,
    )
    clock = [2000.0]
    app.state.storyteller.clock = lambda: clock[0]
    message = StorytellerSnapshot(
        protocol_version=1,
        world=app.state.saved_map.world,
        server_session=str(uuid4()),
        session_started_at_ms=1999000,
        captured_at_ms=2000000,
        sequence=1,
        published_tick=5,
        phase="outbreak",
        mode="observe",
        world_age_hours=2,
        pressure=5,
        budget=3,
        online_players=2,
        npc_count=-1,
        cell_count=1,
        scan_queue=2,
        health="Observation only",
    )
    message.cells.add(x=10624, y=9888, z=0, dwell=3, wealth=15, confidence=0.25, age_hours=1)
    message.decisions.add(
        id="decision-1", event="patrol", outcome="deferred", reason="Grace period", at_hour=2
    )
    with TestClient(app) as client:
        yield app, client, clock, message


def send(diagnostic, message=None):
    return diagnostic[1].post(
        "/internal/v1/storyteller",
        content=(message or diagnostic[3]).SerializeToString(),
        headers=HEADERS,
    )


def test_optional_first_week_diagnostics_are_private_and_preserve_unknowns(diagnostic):
    message = diagnostic[3]
    message.scenario.status = "calm"
    message.scenario.phase = "waiting"
    message.scenario.residents = 24
    message.scenario.materialized = 8
    message.scenario.worker_health = "ready"
    message.scenario.worker_queue = -1
    assert send(diagnostic).status_code == 204
    snapshot = diagnostic[1].get("/debug/storyteller/snapshot").json()["snapshot"]
    assert snapshot["scenario"]["residents"] == 24
    assert snapshot["scenario"]["worker_queue"] is None
    assert "scenario" not in diagnostic[1].get("/api/v1/world").json()


def test_invalid_scenario_diagnostics_do_not_replace_valid_feed(diagnostic):
    message = diagnostic[3]
    assert send(diagnostic).status_code == 204
    message.sequence += 1
    message.scenario.status = "calm"
    message.scenario.phase = "waiting"
    message.scenario.worker_compute_ms = float("nan")
    assert send(diagnostic).status_code == 422
    assert diagnostic[1].get("/debug/storyteller/snapshot").json()["snapshot"]["scenario"] is None


def test_private_routes_disabled_by_default(tmp_path, monkeypatch):
    monkeypatch.delenv("OBSERVER_STORYTELLER_DEBUG", raising=False)
    monkeypatch.delenv("OBSERVER_POSITION_TOKEN_FILE", raising=False)
    monkeypatch.setenv("OBSERVER_MAPS", str(tmp_path / "maps"))
    app = create_saved_app(
        data=tmp_path / "data", save=tmp_path / "save", polling=False, position_token=TOKEN
    )
    with TestClient(app) as client:
        assert client.get("/debug/storyteller").status_code == 404
        assert client.get("/debug/storyteller/snapshot").status_code == 404
        assert client.post("/internal/v1/storyteller", headers=HEADERS).status_code == 404


def test_auth_validation_and_payload_limits(diagnostic):
    _, client, _, message = diagnostic
    assert client.post("/internal/v1/storyteller").status_code == 401
    assert (
        client.post(
            "/internal/v1/storyteller", headers={"Authorization": "Bearer " + TOKEN}
        ).status_code
        == 415
    )
    assert (
        client.post("/internal/v1/storyteller", content=b"x" * 65537, headers=HEADERS).status_code
        == 413
    )
    assert (
        client.post("/internal/v1/storyteller", content=b"\x80", headers=HEADERS).status_code == 422
    )
    for field, value in [
        ("world", "production"),
        ("protocol_version", 2),
        ("sequence", 0),
        ("mode", "cheat"),
        ("pressure", math.nan),
        ("npc_count", -2),
        ("captured_at_ms", 1980000),
        ("captured_at_ms", 2006000),
    ]:
        bad = StorytellerSnapshot()
        bad.CopyFrom(message)
        setattr(bad, field, value)
        assert send(diagnostic, bad).status_code == 422, field
    for field, value in [("x", 10625), ("confidence", 1.01), ("wealth", math.inf), ("z", 65)]:
        bad = StorytellerSnapshot()
        bad.CopyFrom(message)
        setattr(bad.cells[0], field, value)
        assert send(diagnostic, bad).status_code == 422, field
    bad = StorytellerSnapshot()
    bad.CopyFrom(message)
    bad.cells.append(message.cells[0])
    assert send(diagnostic, bad).status_code == 422
    bad.ClearField("cells")
    for i in range(65):
        bad.cells.add(x=i * 32, confidence=0.1)
    assert send(diagnostic, bad).status_code == 422
    assert send(diagnostic).status_code == 204


def test_latest_only_restart_replay_staleness_and_public_separation(diagnostic):
    app, client, clock, message = diagnostic
    assert client.get("/debug/storyteller/snapshot").json()["status"] == "waiting"
    assert client.get("/debug/storyteller").status_code == 200
    assert send(diagnostic).status_code == 204
    latest = client.get("/debug/storyteller/snapshot").json()
    assert latest["status"] == "live"
    assert latest["snapshot"]["npc_count"] is None
    assert latest["snapshot"]["cells"][0]["wealth"] == 15
    assert message.server_session not in json.dumps(latest) and TOKEN not in json.dumps(latest)
    assert "storyteller" not in json.dumps(client.get("/api/v1/world").json())
    assert client.get("/api/v1/storyteller").status_code == 404
    assert client.post("/debug/storyteller/snapshot", json={"mode": "active"}).status_code in (
        404,
        405,
    )
    assert send(diagnostic).status_code == 409
    restarted = Storyteller(app.state.saved_map, TOKEN, enabled=True, clock=lambda: clock[0])
    assert restarted.snapshot()["status"] == "stale"
    assert restarted.snapshot()["snapshot"]["cells"] == latest["snapshot"]["cells"]
    with pytest.raises(Exception) as error:
        restarted.accept(message.SerializeToString())
    assert error.value.status_code == 409
    clock[0] += 21
    assert client.get("/debug/storyteller/snapshot").json()["status"] == "stale"
    message.sequence += 1
    message.captured_at_ms = int(clock[0] * 1000)
    message.ClearField("cells")
    assert send(diagnostic).status_code == 204
    assert client.get("/debug/storyteller/snapshot").json()["snapshot"]["cells"] == []
    old = StorytellerSnapshot()
    old.CopyFrom(message)
    message.server_session = str(uuid4())
    message.session_started_at_ms += 1000
    message.sequence = 1
    assert send(diagnostic).status_code == 204
    old.sequence += 10
    assert send(diagnostic, old).status_code == 409
