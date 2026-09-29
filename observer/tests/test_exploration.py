import io
import json
import zlib
from uuid import uuid4

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from PIL import Image
from test_saved_map import setup_sources

from observer.exploration import LiveExploration
from observer.models import Bounds
from observer.proto.positions_pb2 import ExplorationSnapshot, PlayerExploration
from observer.saved_api import create_saved_app
from observer.terrain import UNKNOWN

TOKEN = "c" * 64
HEADERS = {"Authorization": "Bearer " + TOKEN, "Content-Type": "application/x-protobuf"}


@pytest.fixture
def exploration(tmp_path, monkeypatch):
    save, maps = setup_sources(tmp_path)
    monkeypatch.setenv("OBSERVER_MAPS", str(maps))
    monkeypatch.delenv("OBSERVER_LOGS", raising=False)
    monkeypatch.delenv("OBSERVER_POSITION_TOKEN_FILE", raising=False)
    app = create_saved_app(data=tmp_path / "data", save=save, polling=False, position_token=TOKEN)
    state = app.state.saved_map
    state.bounds = Bounds(min_x=0, min_y=0, max_x=0, max_y=0)
    state.publish("coverage:akryllax", [[0, 0, 3]], 1999000, 2000000)
    state.terrain.index_known(state.known())
    now = [2000.0]
    app.state.exploration.clock = lambda: now[0]
    frame = ExplorationSnapshot(
        protocol_version=1,
        world=state.world,
        server_session=str(uuid4()),
        session_started_at_ms=1999000,
        sequence=1,
        captured_at_ms=2000000,
    )
    # Block 0 is visited, block 1 is learned, everything else is still hidden.
    frame.players.append(
        PlayerExploration(
            username="akryllax",
            world_version=249,
            visited_zlib=zlib.compress(bytes([7]) + bytes(15)),
        )
    )
    with TestClient(app) as client:
        yield app, client, now, frame


def send(fixture, frame=None, headers=HEADERS):
    _, client, _, current = fixture
    return client.post(
        "/internal/v1/exploration", content=(frame or current).SerializeToString(), headers=headers
    )


def advance(fixture, raw=None):
    _, _, now, frame = fixture
    now[0] += 2
    frame.sequence += 1
    frame.captured_at_ms = int(now[0] * 1000)
    if raw is not None:
        frame.players[0].visited_zlib = zlib.compress(raw)
    return frame


def test_exact_mask_updates_tiles_search_and_per_player_knowledge(exploration):
    app, client, _, _ = exploration
    state = app.state.saved_map
    revision = state.coverage_revision
    source = state.save / "map_visited_server/akryllax.zip"
    original = source.read_bytes()
    url = "/api/v1/map/tiles/0/0/0.png?observer=akryllax"
    before = Image.open(io.BytesIO(client.get(url).content))
    assert before.getpixel((40, 5)) == tuple(bytes.fromhex(UNKNOWN[1:]))
    assert not state.terrain.search("garage", state.known("akryllax"), 0, 0)
    assert send(exploration).status_code == 204
    assert state.known("akryllax") == {(0, 0): 3, (32, 0): 1}
    assert state.known("eric") == {}
    assert state.coverage_revision > revision
    assert state.terrain.search("garage", state.known("akryllax"), 0, 0)
    after = Image.open(io.BytesIO(client.get(url).content))
    assert after.getpixel((40, 5)) != before.getpixel((40, 5))
    assert after.getpixel((70, 5)) == before.getpixel((70, 5))
    assert after.getpixel((40, 40)) == before.getpixel((40, 40))
    world = client.get("/api/v1/world").json()
    assert world["exploration_feed"]["status"] == "live"
    assert "visited_zlib" not in json.dumps(world) and TOKEN not in json.dumps(world)
    assert source.read_bytes() == original


def test_unchanged_masks_do_not_rebuild_map_but_clearing_is_authoritative(exploration):
    app, _, _, _ = exploration
    assert send(exploration).status_code == 204
    revision = app.state.saved_map.coverage_revision
    world_revision = app.state.saved_map.revision
    advance(exploration)
    assert send(exploration).status_code == 204
    assert app.state.saved_map.coverage_revision == revision
    assert app.state.saved_map.revision == world_revision
    advance(exploration, bytes(16))
    assert send(exploration).status_code == 204
    assert app.state.saved_map.known() == {}  # No union with an obsolete pre-forget save.
    assert app.state.saved_map.coverage_revision > revision


def test_old_save_cannot_roll_back_live_mask_and_newer_save_can_replace_it(exploration):
    app, _, _, _ = exploration
    state = app.state.saved_map
    assert send(exploration).status_code == 204
    state.publish("coverage:akryllax", [[0, 0, 3]], 1999000, 2001000)
    assert state.known() == {(0, 0): 3, (32, 0): 1}
    # Disconnect/world save after the last live capture is authoritative.
    state.publish("coverage:akryllax", [[64, 0, 3]], 2001000, 2002000)
    assert state.known() == {(64, 0): 3}
    advance(exploration)
    assert send(exploration).status_code == 204
    assert state.known() == {(0, 0): 3, (32, 0): 1}


def test_offline_empty_heartbeat_stale_and_restart_keep_masks_without_positions(exploration):
    app, _, now, frame = exploration
    state = app.state.saved_map
    assert send(exploration).status_code == 204
    advance(exploration)
    del frame.players[:]
    assert send(exploration).status_code == 204
    known = state.known()
    now[0] += 16
    assert app.state.exploration.snapshot()["status"] == "stale"
    assert state.known() == known
    assert not app.state.positions.latest
    reopened = LiveExploration(state, TOKEN, clock=lambda: now[0])
    state.exploration = reopened
    assert reopened.snapshot()["status"] == "waiting"
    assert state.known() == known
    now[0] -= 16
    with pytest.raises(HTTPException) as error:
        reopened.accept(frame.SerializeToString())
    assert error.value.status_code == 409


def test_auth_validation_decompression_limits_and_atomic_rejection(exploration):
    app, client, _, frame = exploration
    assert send(exploration, headers={}).status_code == 401
    assert send(exploration, headers={"Authorization": "Bearer " + TOKEN}).status_code == 415
    assert (
        client.post("/internal/v1/exploration", content=b"\x80", headers=HEADERS).status_code == 422
    )
    for field, value in [
        ("world", "wrong"),
        ("protocol_version", 2),
        ("captured_at_ms", 1980000),
        ("captured_at_ms", 2006000),
        ("server_session", "bad"),
        ("sequence", 0),
    ]:
        invalid = ExplorationSnapshot()
        invalid.CopyFrom(frame)
        setattr(invalid, field, value)
        assert send(exploration, invalid).status_code == 422
    for field, value in [
        ("world_version", 248),
        ("min_cell_x", -1),
        ("max_cell_y", 1),
        ("username", "\n"),
    ]:
        invalid = ExplorationSnapshot()
        invalid.CopyFrom(frame)
        setattr(invalid.players[0], field, value)
        assert send(exploration, invalid).status_code == 422
    for compressed in [
        b"bad",
        zlib.compress(bytes(15)),
        zlib.compress(bytes(17)),
        zlib.compress(bytes(16))[:-1],
        zlib.compress(bytes(16)) + b"trailing",
        zlib.compress(bytes(1000000)),
    ]:
        invalid = ExplorationSnapshot()
        invalid.CopyFrom(frame)
        invalid.players[0].visited_zlib = compressed
        assert send(exploration, invalid).status_code == 422
    invalid = ExplorationSnapshot()
    invalid.CopyFrom(frame)
    invalid.players.append(frame.players[0])
    assert send(exploration, invalid).status_code == 422
    app.state.exploration.max_cells = 1
    assert send(exploration).status_code == 422
    app.state.exploration.max_cells = 262144
    app.state.exploration.max_body = 10
    assert send(exploration).status_code == 413
    app.state.exploration.max_body = 8 * 1024 * 1024
    assert not app.state.exploration.masks
    assert app.state.saved_map.known() == {(0, 0): 3}
    assert send(exploration).status_code == 204
    app.state.exploration.token = None
    assert send(exploration).status_code == 404


def test_replay_sessions_new_players_and_unlisted_masks(exploration):
    app, _, _, frame = exploration
    assert send(exploration).status_code == 204
    assert send(exploration).status_code == 409
    old = ExplorationSnapshot()
    old.CopyFrom(frame)
    advance(exploration, bytes([0, 3]) + bytes(14))
    frame.players[0].username = "eric"
    frame.server_session = str(uuid4())
    frame.session_started_at_ms += 2000
    frame.sequence = 1
    assert send(exploration).status_code == 204
    assert app.state.saved_map.observers() == ["akryllax", "eric"]
    assert app.state.saved_map.known("akryllax") == {(0, 0): 3, (32, 0): 1}
    assert app.state.saved_map.known("eric") == {(128, 0): 3}
    old.sequence = 100
    old.captured_at_ms += 2000
    assert send(exploration, old).status_code == 409
