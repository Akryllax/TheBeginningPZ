import copy
from concurrent.futures import Future
from types import SimpleNamespace
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from test_roads import grid, point

from observer.routing import point_key
from observer.saved_api import create_saved_app


@pytest.fixture
def routed(tmp_path, monkeypatch):
    monkeypatch.setenv("OBSERVER_MAPS", str(tmp_path / "maps"))
    monkeypatch.delenv("OBSERVER_LOGS", raising=False)
    app = create_saved_app(data=tmp_path / "data", save=tmp_path / "save", polling=False)
    state, routing = app.state.saved_map, app.state.routing
    state.publish(
        "coverage:akryllax", [[x, y, 1] for x in range(0, 224, 32) for y in range(0, 128, 32)], 1, 1
    )
    tiles = {(x, y): 1 for x in range(4, 201) for y in (10, 90)}
    tiles.update({(x, y): 1 for x in (4, 100, 200) for y in range(10, 91)})

    class Executor:
        calls = 0

        def submit(self, fn, root, cache, known, points, turns, terrain):
            self.calls += 1
            future = Future()
            result = grid(tiles, known).route(points, turns)
            future.set_result(result)
            return future

        def shutdown(self, **kwargs):
            pass

    routing.executor = Executor()
    clock = [100.0]
    routing.clock = lambda: clock[0]
    sample = {
        "x": 4.5,
        "y": 10.5,
        "z": 0,
        "sample": "initial",
        "character": "Tommy",
        "connection": "connection-1",
        "source": "live",
        "dead": False,
    }
    positions = SimpleNamespace(
        enabled=True, player_samples=lambda: {"akryllax": dict(sample)}, fresh=lambda: True
    )
    state.positions = positions
    client = TestClient(
        app
    )  # Explicit tick() calls; no background lifespan in these deterministic tests.
    yield app, client, sample, clock
    client.close()
    routing.close()
    state.close()


def make_trip(app, client, auto=True):
    points = [point(str(uuid4()), 4.5, 10.5), point(str(uuid4()), 100.5, 80.5)]
    payload = {"stops": points}
    assert client.post("/api/v1/routes", json=payload).json()["status"] == "preparing"
    route = client.post("/api/v1/routes", json=payload).json()
    assert route["status"] == "ready"
    value = {
        "name": "GPS trip",
        "stops": route["stops"],
        "follow_player": "akryllax",
        "arrival_radius": 10,
        "routing": {"auto_reroute": auto, "observer": None, "request_id": route["request_id"]},
    }
    response = client.post("/api/v1/trips", json=value)
    assert response.status_code == 200, response.text
    trip = response.json()
    assert trip["routing"]["status"] == "ready"
    return trip, value


def latest(client, id):
    return next(t for t in client.get("/api/v1/trips").json()["trips"] if t["id"] == id)


def start(client, trip):
    r = client.post(
        f"/api/v1/trips/{trip['id']}/progress", json={"version": trip["version"], "action": "start"}
    )
    assert r.status_code == 200, r.text
    return r.json()


def test_preview_deduplicates_and_does_not_mutate_trip_or_expose_other_knowledge(routed):
    app, client, _sample, _clock = routed
    trip, value = make_trip(app, client)
    assert app.state.routing.executor.calls == 1
    assert "_coverage" not in client.get("/api/v1/trips").text
    unknown = client.post(
        "/api/v1/routes", json={"stops": value["stops"][:2], "observer": "eric"}
    ).json()
    assert unknown["status"] == "unknown"
    assert latest(client, trip["id"])["stops"] == trip["stops"]
    assert (
        client.post(
            "/api/v1/routes",
            json={"stops": value["stops"][:2]},
            headers={"Origin": "https://elsewhere.test"},
        ).status_code
        == 403
    )


def test_reroute_keeps_completed_and_manual_stops_and_off_switch(routed):
    app, client, sample, clock = routed
    trip, value = make_trip(app, client, auto=False)
    # Explicitly claimed generated turns become protected manual destinations.
    pinned = value["stops"][1]
    pinned["kind"] = "manual"
    value["version"] = trip["version"]
    trip = client.put(f"/api/v1/trips/{trip['id']}", json=value).json()
    app.state.routing.tick(app.state.planning)
    app.state.routing.tick(app.state.planning)
    trip = start(client, latest(client, trip["id"]))
    sample.update(x=4.5, y=90.5)
    for i in range(4):
        sample["sample"] = str(i)
        clock[0] += 1
        app.state.routing.tick(app.state.planning)
    assert latest(client, trip["id"])["stops"] == trip["stops"]
    value.update(
        stops=trip["stops"],
        version=trip["version"],
        routing={"auto_reroute": True, "observer": None},
    )
    changed = client.put(f"/api/v1/trips/{trip['id']}", json=value)
    assert changed.status_code == 200, changed.text
    for i in range(5, 10):
        sample["sample"] = str(i)
        clock[0] += 1
        app.state.routing.tick(app.state.planning)
    result = latest(client, trip["id"])
    assert result["routing"]["status"] == "ready"
    assert any(p.get("kind") == "origin" for p in result["stops"])
    assert any(p["id"] == pinned["id"] and p["kind"] == "manual" for p in result["stops"])
    assert result["stops"][-1]["id"] == trip["stops"][-1]["id"]
    assert trip["stops"][0]["id"] in result["tracking"]["reached_stop_ids"]
    assert result["tracking"]["active"]


def test_stale_or_changed_connection_never_reroutes_and_version_guard_rejects_edits(routed):
    app, client, sample, _clock = routed
    trip, _value = make_trip(app, client)
    trip = start(client, trip)
    sample.update(x=4.5, y=90.5, connection="different-connection")
    for i in range(4):
        sample["sample"] = str(i)
        app.state.routing.tick(app.state.planning)
    assert latest(client, trip["id"])["stops"] == trip["stops"]
    app.state.saved_map.positions.player_samples = dict
    app.state.routing.tick(app.state.planning)
    assert app.state.routing.executor.calls == 1
    assert (
        client.post(f"/api/v1/trips/{trip['id']}/reroute", json={"version": 0}).status_code == 422
    )
    assert (
        client.post(
            f"/api/v1/trips/{trip['id']}/reroute", json={"version": trip["version"] - 1}
        ).status_code
        == 409
    )


def test_moved_bend_is_manual_clearing_disables_gps_and_old_format_remains_valid(routed):
    app, client, _sample, _clock = routed
    trip, value = make_trip(app, client)
    before = copy.deepcopy(value)
    value["stops"][1]["x"] -= 1
    value["version"] = trip["version"]
    result = client.put(f"/api/v1/trips/{trip['id']}", json=value).json()
    assert result["stops"][1]["kind"] == "manual"
    assert result["routing"]["status"] == "pending"
    before["version"] = result["version"]
    restored = client.put(f"/api/v1/trips/{trip['id']}", json=before).json()
    assert point_key(restored["stops"]) == point_key(before["stops"])
    cleared = client.put(
        f"/api/v1/trips/{trip['id']}", json={**before, "version": restored["version"], "stops": []}
    ).json()
    assert cleared["routing"] is None
    assert (
        client.post(
            "/api/v1/trips",
            json={
                "name": "Old client",
                "stops": [point(str(uuid4()), 0, 0), point(str(uuid4()), 30, 0)],
            },
        ).json()["routing"]
        is None
    )


def test_completed_job_cannot_overwrite_a_concurrent_manual_edit(routed):
    app, client, sample, _clock = routed
    trip, value = make_trip(app, client)
    trip = start(client, trip)
    sample.update(x=4.5, y=90.5)
    for i in range(3):
        sample["sample"] = str(i)
        app.state.routing.tick(app.state.planning)
    assert trip["id"] in app.state.routing.running
    value.update(
        version=trip["version"],
        stops=trip["stops"],
        routing={"observer": None, "auto_reroute": False},
    )
    value["stops"][-1]["x"] = 150.5
    response = client.put(f"/api/v1/trips/{trip['id']}", json=value)
    assert response.status_code == 200
    app.state.routing.tick(app.state.planning)
    assert latest(client, trip["id"])["stops"][-1]["x"] == 150.5
    assert not any(p.get("kind") == "origin" for p in latest(client, trip["id"])["stops"])


def test_missed_generated_turn_reroutes_even_on_the_following_road(routed):
    app, client, sample, clock = routed
    trip, _ = make_trip(app, client)
    trip = start(client, trip)
    # Jump beyond a bend without reporting an arrival within its radius.
    sample.update(x=100.5, y=60.5)
    for i in range(5):
        sample["sample"] = f"beyond-{i}"
        clock[0] += 1
        app.state.routing.tick(app.state.planning)
    changed = latest(client, trip["id"])
    assert any(p.get("kind") == "origin" for p in changed["stops"])
    assert trip["stops"][1]["id"] not in {p["id"] for p in changed["stops"]}
    assert trip["stops"][1]["id"] not in changed["tracking"]["reached_stop_ids"]


def test_old_client_edits_keep_routing_settings_and_generated_kind(routed):
    app, client, _sample, _clock = routed
    trip, value = make_trip(app, client)
    value.pop("routing")
    for p in value["stops"]:
        p.pop("kind", None)
    value.update(version=trip["version"], name="Old browser rename")
    response = client.put(f"/api/v1/trips/{trip['id']}", json=value)
    assert response.status_code == 200
    assert response.json()["routing"]["auto_reroute"]
    assert response.json()["stops"][1]["kind"] == "generated"


def test_complete_trip_recalculate_is_a_clear_error(routed):
    app, client, _sample, _clock = routed
    trip, _ = make_trip(app, client)
    for _ in trip["stops"]:
        trip = client.post(
            f"/api/v1/trips/{trip['id']}/progress",
            json={"version": trip["version"], "action": "next"},
        ).json()
    assert (
        client.post(
            f"/api/v1/trips/{trip['id']}/reroute", json={"version": trip["version"]}
        ).status_code
        == 409
    )
    assert latest(client, trip["id"])["routing"]["status"] == "ready"
