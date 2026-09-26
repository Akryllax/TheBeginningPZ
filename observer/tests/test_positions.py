import json
import math
import sqlite3
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from observer.positions import LivePositions
from observer.proto.positions_pb2 import PlayerPosition, PositionSnapshot
from observer.saved_api import create_saved_app

TOKEN = "a" * 64
HEADERS = {"Authorization": "Bearer " + TOKEN, "Content-Type": "application/x-protobuf"}


@pytest.fixture
def live(tmp_path, monkeypatch):
    save = tmp_path / "save"
    save.mkdir()
    monkeypatch.setenv("OBSERVER_MAPS", str(tmp_path / "maps"))
    monkeypatch.delenv("OBSERVER_LOGS", raising=False)
    monkeypatch.delenv("OBSERVER_POSITION_TOKEN_FILE", raising=False)
    with sqlite3.connect(save / "players.db") as db:
        db.execute("CREATE TABLE networkPlayers(id,username,name,x,y,z,isDead,data)")
        db.execute("INSERT INTO networkPlayers VALUES(1,'akryllax','Tommy',10,20,0,0,?)", (b"private",))
    app = create_saved_app(data=tmp_path / "data", save=save, polling=False, position_token=TOKEN)
    clock = [2000.0]
    app.state.positions.clock = app.state.planning.clock = lambda: clock[0]
    app.state.saved_map.publish(
        "players",
        [{"id": 1, "name": "akryllax", "character": "Tommy", "x": 10, "y": 20, "z": 0}],
        1990000,
        2000000,
    )
    frame = PositionSnapshot(
        protocol_version=1,
        world="AKR_Exploratory",
        server_session=str(uuid4()),
        session_started_at_ms=1999000,
        sequence=1,
        captured_at_ms=2000000,
    )
    frame.players.append(
        PlayerPosition(username="akryllax", character="Tommy", x=100, y=200, z=0, connection_id=str(uuid4()))
    )
    with TestClient(app) as client:
        yield app, client, clock, frame


def send(live, frame=None):
    _, client, _, default = live
    frame = frame or default
    response = client.post("/internal/v1/positions", content=frame.SerializeToString(), headers=HEADERS)
    return response


def next_frame(live, **changes):
    _, _, clock, frame = live
    clock[0] += 1
    frame.sequence += 1
    frame.captured_at_ms = int(clock[0] * 1000)
    for key, value in changes.items():
        setattr(frame.players[0], key, value)
    return frame


def trip(client):
    value = {
        "name": "Live run",
        "follow_player": "akryllax",
        "arrival_radius": 10,
        "stops": [
            {"label": "Departure", "x": 100, "y": 200},
            {"label": "Stop", "x": 200, "y": 200},
            {"label": "Finish", "x": 300, "y": 200},
        ],
    }
    return client.post("/api/v1/trips", json=value).json()


def action(client, value, name="start"):
    return client.post(
        f"/api/v1/trips/{value['id']}/progress", json={"version": value["version"], "action": name}
    )


def latest(client):
    return client.get("/api/v1/trips").json()["trips"][0]


def test_auth_size_schema_world_and_coordinates(live):
    _, client, _, frame = live
    assert client.post("/internal/v1/positions", content=frame.SerializeToString()).status_code == 401
    assert (
        client.post("/internal/v1/positions", headers={"Authorization": "Bearer " + TOKEN}).status_code == 415
    )
    assert client.post("/internal/v1/positions", content=b"x" * 65537, headers=HEADERS).status_code == 413
    assert client.post("/internal/v1/positions", content=b"\x80", headers=HEADERS).status_code == 422
    for field, value in [
        ("world", "OtherWorld"),
        ("protocol_version", 2),
        ("sequence", 0),
        ("captured_at_ms", 1990000),
        ("captured_at_ms", 2006000),
    ]:
        invalid = PositionSnapshot()
        invalid.CopyFrom(frame)
        setattr(invalid, field, value)
        assert send(live, invalid).status_code == 422
    for field, value in [
        ("x", math.nan),
        ("y", math.inf),
        ("z", 65),
        ("username", "\n"),
        ("connection_id", "invalid"),
    ]:
        invalid = PositionSnapshot()
        invalid.CopyFrom(frame)
        setattr(invalid.players[0], field, value)
        assert send(live, invalid).status_code == 422
    duplicate = PositionSnapshot()
    duplicate.CopyFrom(frame)
    duplicate.players.append(frame.players[0])
    assert send(live, duplicate).status_code == 422
    assert send(live).status_code == 204


def test_replay_session_change_and_retired_session(live):
    assert send(live).status_code == 204
    assert send(live).status_code == 409
    old = PositionSnapshot()
    old.CopyFrom(live[3])
    next_frame(live)
    live[3].server_session = str(uuid4())
    live[3].session_started_at_ms += 1000
    live[3].sequence = 1
    assert send(live).status_code == 204
    old.sequence = 100
    old.captured_at_ms += 1000
    assert send(live, old).status_code == 409


def test_live_offline_stale_fallback_and_private_fields(live):
    app, client, clock, frame = live
    revision = app.state.saved_map.revision
    assert send(live).status_code == 204
    world = client.get("/api/v1/world").json()
    assert world["revision"] == revision  # Fast positions do not rebuild terrain/features.
    assert world["players"][0]["x"] == 100 and world["players"][0]["id"] == 1
    assert world["players"][0]["source"] == "live" and world["players"][0]["online"] is True
    assert world["position_feed"]["online_count"] == 1
    assert frame.players[0].connection_id not in json.dumps(world)
    assert frame.server_session not in json.dumps(world) and TOKEN not in json.dumps(world)
    next_frame(live)
    del frame.players[:]
    assert send(live).status_code == 204
    world = client.get("/api/v1/world").json()
    assert world["players"][0]["source"] == "last_seen" and world["players"][0]["online"] is False
    assert world["position_feed"]["online_count"] == 0
    clock[0] += 10
    world = client.get("/api/v1/world").json()
    assert world["position_feed"]["status"] == "stale" and world["players"][0]["online"] is None
    assert world["players"][0]["x"] == 100
    # Position snapshots and connection identities are never persisted as history.
    rows = app.state.saved_map.db.execute("SELECT payload FROM sources WHERE name='players'").fetchone()
    assert json.loads(rows[0])[0]["x"] == 10


def test_live_checkpoint_floor_freshness_outage_and_reconnect(live):
    app, client, clock, _ = live
    t = trip(client)
    assert action(client, t).status_code == 409  # No live baseline yet.
    assert send(live).status_code == 204
    t = action(client, t).json()
    assert t["tracking"]["completed"] == 1
    next_frame(live, x=200, z=1)
    assert send(live).status_code == 204
    assert latest(client)["tracking"]["completed"] == 1
    next_frame(live, z=0)
    assert send(live).status_code == 204
    assert latest(client)["tracking"]["completed"] == 2
    clock[0] += 11
    # An old/new save, including another player's save, cannot substitute for the lost feed.
    with sqlite3.connect(app.state.saved_map.save / "players.db") as db:
        db.execute("UPDATE networkPlayers SET x=300,y=200,data=?", (b"new-but-coarse",))
    app.state.planning.poll_progress()
    assert latest(client)["tracking"]["completed"] == 2
    assert "unavailable" in client.get("/api/v1/trips").json()["progress_error"]
    next_frame(live, x=300, connection_id=str(uuid4()))
    assert send(live).status_code == 204
    t = latest(client)
    assert not t["tracking"]["active"] and t["tracking"]["completed"] == 2
    t = action(client, t).json()
    assert t["tracking"]["active"] and t["tracking"]["completed"] == 2
    next_frame(live)
    assert send(live).status_code == 204
    assert latest(client)["tracking"]["note"] == "Trip complete"


def test_live_death_pauses_and_removes_player(live):
    _, client, _, _ = live
    assert send(live).status_code == 204
    action(client, trip(client))
    next_frame(live, dead=True)
    assert send(live).status_code == 204
    assert not latest(client)["tracking"]["active"]
    assert client.get("/api/v1/world").json()["players"] == []
    assert action(client, latest(client)).status_code == 409


def test_source_upgrade_baselines_existing_trip(live):
    app, client, _, frame = live
    app.state.positions.token = None
    t = action(client, trip(client)).json()
    assert t["tracking"]["active"]
    app.state.positions.token = TOKEN
    frame.players[0].x = 200
    assert send(live).status_code == 204
    assert latest(client)["tracking"]["completed"] == 1
    next_frame(live)
    assert send(live).status_code == 204
    assert latest(client)["tracking"]["completed"] == 2


def test_disabled_mode_remains_saved_and_has_no_ingestion(live):
    app, client, _, _ = live
    app.state.positions.token = None
    assert send(live).status_code == 404
    assert client.get("/api/v1/world").json()["position_feed"]["status"] == "disabled"
    assert app.state.planning.player_samples()["akryllax"]["x"] == 10


def test_web_restart_retains_replay_guard_but_no_positions(live):
    app, _, clock, frame = live
    assert send(live).status_code == 204
    restarted = LivePositions(app.state.saved_map, TOKEN, clock=lambda: clock[0])
    assert restarted.snapshot()["position_feed"]["status"] == "waiting"
    assert restarted.snapshot()["players"][0]["source"] == "saved"
    from fastapi import HTTPException

    with pytest.raises(HTTPException) as error:
        restarted.accept(frame.SerializeToString())
    assert error.value.status_code == 409
    next_frame(live)
    restarted.accept(frame.SerializeToString())
    assert restarted.snapshot()["position_feed"]["status"] == "live"
