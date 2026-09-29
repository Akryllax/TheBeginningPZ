import sqlite3
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from observer.planning import ProgressInput
from observer.saved_api import create_saved_app


@pytest.fixture
def setup(tmp_path, monkeypatch):
    save, maps = tmp_path / "save", tmp_path / "maps"
    save.mkdir()
    maps.mkdir()
    monkeypatch.setenv("OBSERVER_MAPS", str(maps))
    monkeypatch.delenv("OBSERVER_LOGS", raising=False)
    with sqlite3.connect(save / "players.db") as db:
        db.execute("CREATE TABLE networkPlayers(id,username,name,x,y,z,isDead,data)")
        db.execute(
            "INSERT INTO networkPlayers VALUES(1,'akryllax','Tommy',0,0,0,0,?)",
            (b"private-player-blob",),
        )
        db.execute(
            "INSERT INTO networkPlayers VALUES(2,'eric','Eric',0,0,0,0,?)", (b"other-private-blob",)
        )
    return tmp_path / "data", save


def app_for(setup):
    data, save = setup
    return create_saved_app(data=data, save=save, polling=False)


def plan(follow=None):
    return {
        "name": "Supply run",
        "follow_player": follow,
        "arrival_radius": 10,
        "stops": [
            {"label": "Home", "x": 0, "y": 0, "z": 0},
            {"label": "Surplus", "x": 100, "y": 0, "z": 0},
            {"label": "Garage", "x": 200, "y": 0, "z": 0},
        ],
    }


def latest(client):
    return client.get("/api/v1/trips").json()["trips"][0]


def action(client, trip, name):
    response = client.post(
        f"/api/v1/trips/{trip['id']}/progress", json={"version": trip["version"], "action": name}
    )
    assert response.status_code == 200, response.text
    return response.json()


def move(setup, x, z=0, name="Tommy"):
    with sqlite3.connect(setup[1] / "players.db") as db:
        db.execute(
            "UPDATE networkPlayers SET x=?,z=?,name=?,data=? WHERE username='akryllax'",
            (x, z, name, str(uuid4()).encode()),
        )


def test_trip_distance_order_conflicts_and_undo(setup):
    with TestClient(app_for(setup)) as client:
        value = plan()
        value["stops"][1].update(x=3, y=4)
        value["stops"][2].update(x=3, y=16)
        response = client.post("/api/v1/trips", json=value)
        assert response.status_code == 200
        saved = response.json()
        assert saved["distance"] == 17
        value["stops"][1:] = value["stops"][1:][::-1]
        value["version"] = saved["version"]
        updated = client.put(f"/api/v1/trips/{saved['id']}", json=value).json()
        assert updated["distance"] == 28.3
        assert [s["label"] for s in updated["stops"]] == ["Home", "Garage", "Surplus"]
        assert client.put(f"/api/v1/trips/{saved['id']}", json=value).status_code == 409
        assert (
            client.delete(f"/api/v1/trips/{saved['id']}?version={saved['version']}").status_code
            == 409
        )
        updated = action(client, updated, "next")
        assert (
            client.delete(f"/api/v1/trips/{saved['id']}?version={updated['version']}").status_code
            == 200
        )
        assert client.get("/api/v1/trips").json()["trips"] == []
        restored = client.post(f"/api/v1/trips/{saved['id']}/restore").json()
        assert restored["tracking"]["completed"] == 1
        assert restored["id"] == saved["id"]
        assert restored["version"] > updated["version"]


def test_api_bounds_same_origin_and_private_fields(setup):
    app = app_for(setup)
    with TestClient(app) as client:
        for value in (
            {**plan(), "name": " "},
            {**plan(), "stops": plan()["stops"] * 86},
            {**plan(), "arrival_radius": 10000},
        ):
            assert client.post("/api/v1/trips", json=value).status_code == 422
        assert client.post("/api/v1/trips", content=b"x" * 131073).status_code == 413
        assert (
            client.post(
                "/api/v1/trips", json=plan(), headers={"Origin": "https://elsewhere.test"}
            ).status_code
            == 403
        )
        saved = client.post("/api/v1/trips", json=plan("akryllax")).json()
        action(client, saved, "start")
        response = client.get("/api/v1/trips")
        assert "_sample" not in response.text and "_character" not in response.text
        assert "private-player-blob" not in response.text
        assert (
            client.post(
                f"/api/v1/trips/{saved['id']}/progress",
                json={"version": 1, "action": "reset"},
                headers={"Origin": "https://elsewhere.test"},
            ).status_code
            == 403
        )
        assert (
            client.delete(
                f"/api/v1/trips/{saved['id']}?version=2",
                headers={"Origin": "https://elsewhere.test"},
            ).status_code
            == 403
        )
        assert client.get("/api/v1/travel").status_code == 404


def test_pings_expire_replace_and_do_not_persist(setup):
    app = app_for(setup)
    clock = [100.0]
    app.state.planning.clock = lambda: clock[0]
    owner = str(uuid4())
    with TestClient(app) as client:
        ping = {"owner": owner, "x": 5, "y": 10}
        assert client.post("/api/v1/pings", json=ping).status_code == 200
        assert client.post("/api/v1/pings", json=ping).status_code == 429
        public = client.get("/api/v1/pings")
        assert owner not in public.text and len(public.json()["pings"]) == 1
        clock[0] += 2
        assert client.post("/api/v1/pings", json={**ping, "x": 20}).status_code == 200
        assert len(client.get("/api/v1/pings").json()["pings"]) == 1
        assert client.get("/api/v1/pings").json()["pings"][0]["x"] == 20
        assert client.post("/api/v1/pings", json={**ping, "x": 999999}).status_code == 422
        assert (
            client.post(
                "/api/v1/pings", json=ping, headers={"Origin": "https://elsewhere.test"}
            ).status_code
            == 403
        )
        clock[0] += 13
        assert client.get("/api/v1/pings").json()["pings"] == []
        client.post("/api/v1/pings", json=ping)
        saved = client.post("/api/v1/trips", json=plan()).json()
    with TestClient(app_for(setup)) as client:
        assert client.get("/api/v1/pings").json()["pings"] == []
        assert latest(client)["id"] == saved["id"]


def test_auto_progress_uses_fresh_player_save_order_and_floor(setup):
    app = app_for(setup)
    with TestClient(app) as client:
        saved = client.post("/api/v1/trips", json=plan("akryllax")).json()
        saved = action(client, saved, "start")
        assert saved["tracking"]["completed"] == 1
        baseline = (setup[1] / "players.db").read_bytes()
        app.state.planning.poll_progress()
        assert (setup[1] / "players.db").read_bytes() == baseline
        assert latest(client)["version"] == saved["version"]
        # Arriving at stop 3 first cannot skip stop 2 or infer a line crossing.
        move(setup, 200)
        app.state.planning.poll_progress()
        assert latest(client)["tracking"]["completed"] == 1
        move(setup, 100, z=1)
        app.state.planning.poll_progress()
        assert latest(client)["tracking"]["completed"] == 1
        move(setup, 100)
        app.state.planning.poll_progress()
        arrived = latest(client)
        assert arrived["tracking"]["completed"] == 2
        with sqlite3.connect(setup[1] / "players.db") as db:
            db.execute(
                "UPDATE networkPlayers SET data=? WHERE username='eric'",
                (b"another player's save",),
            )
        app.state.planning.poll_progress()
        assert latest(client)["version"] == arrived["version"]
        move(setup, 200)
        app.state.planning.poll_progress()
        done = latest(client)
        assert done["tracking"]["completed"] == 3 and not done["tracking"]["active"]
        assert done["tracking"]["note"] == "Trip complete"


def test_progress_persists_pauses_on_death_and_preserves_on_route_edit(setup):
    app = app_for(setup)
    with TestClient(app) as client:
        saved = client.post("/api/v1/trips", json=plan("akryllax")).json()
        saved = action(client, saved, "start")
    app = app_for(setup)
    with TestClient(app) as client:
        app.state.planning.poll_progress()
        assert latest(client)["version"] == saved["version"]
        app.state.saved_map.sources["deaths"] = {
            "payload": [{"name": "akryllax", "occurred_at": saved["tracking"]["started_at"] + 1}]
        }
        app.state.planning.poll_progress()
        paused = latest(client)
        assert not paused["tracking"]["active"] and paused["tracking"]["completed"] == 1
        manual = action(client, paused, "next")
        assert manual["tracking"]["completed"] == 2
        renamed = client.put(
            f"/api/v1/trips/{manual['id']}",
            json={**plan("akryllax"), "name": "Renamed run", "version": manual["version"]},
        ).json()
        assert renamed["tracking"]["completed"] == 2
        value = plan("akryllax")
        value["stops"][1], value["stops"][2] = value["stops"][2], value["stops"][1]
        value["version"] = renamed["version"]
        reset = client.put(f"/api/v1/trips/{manual['id']}", json=value).json()
        assert reset["tracking"]["completed"] == 1 and not reset["tracking"]["active"]
        assert set(reset["tracking"]["reached_stop_ids"]) == set(
            renamed["tracking"]["reached_stop_ids"]
        )


def test_read_failure_and_character_change_do_not_complete_trip(setup):
    app = app_for(setup)
    with TestClient(app) as client:
        saved = client.post("/api/v1/trips", json=plan("akryllax")).json()
        action(client, saved, "start")
        lock = sqlite3.connect(setup[1] / "players.db")
        lock.execute("BEGIN EXCLUSIVE")
        app.state.planning.poll_progress()
        assert client.get("/api/v1/trips").json()["progress_error"]
        assert latest(client)["tracking"]["completed"] == 1
        lock.rollback()
        lock.close()
        move(setup, 100, name="New character")
        app.state.planning.poll_progress()
        assert not latest(client)["tracking"]["active"]
        assert latest(client)["tracking"]["completed"] == 1


def test_start_during_collection_cannot_use_older_snapshot(setup, monkeypatch):
    app = app_for(setup)
    with TestClient(app) as client:
        saved = client.post("/api/v1/trips", json=plan("akryllax")).json()
        action(client, saved, "start")
        planning = app.state.planning
        original = planning.player_samples
        move(setup, 100)

        def racing_sample():
            stale = original()
            move(setup, 500)
            monkeypatch.setattr(planning, "player_samples", original)
            current = latest(client)
            planning.progress(
                current["id"], ProgressInput(version=current["version"], action="start")
            )
            return stale

        monkeypatch.setattr(planning, "player_samples", racing_sample)
        planning.poll_progress()
        assert latest(client)["tracking"]["completed"] == 1


def update_trip(client, trip, stops):
    result = client.put(
        f"/api/v1/trips/{trip['id']}",
        json={
            "name": trip["name"],
            "stops": stops,
            "version": trip["version"],
            "follow_player": trip["tracking"]["player"],
            "arrival_radius": trip["tracking"]["radius"],
        },
    )
    assert result.status_code == 200, result.text
    return result.json()


def test_waypoint_identity_edits_preserve_reached_and_active_tracking(setup):
    app = app_for(setup)
    with TestClient(app) as client:
        trip = client.post("/api/v1/trips", json=plan("akryllax")).json()
        trip = action(client, trip, "start")
        move(setup, 100)
        app.state.planning.poll_progress()
        trip = latest(client)
        home, surplus, garage = trip["stops"]
        inserted = {"id": str(uuid4()), "label": "Road junction", "x": 50, "y": 0, "z": 0}
        trip = update_trip(client, trip, [home, inserted, surplus, garage])
        assert trip["tracking"]["active"]
        assert set(trip["tracking"]["reached_stop_ids"]) == {home["id"], surplus["id"]}
        assert trip["tracking"]["completed"] == 1
        # Existing position must not resolve an inserted point; future points cannot skip it.
        app.state.planning.poll_progress()
        move(setup, 200)
        app.state.planning.poll_progress()
        assert latest(client)["tracking"]["completed"] == 1
        move(setup, 50)
        app.state.planning.poll_progress()
        trip = latest(client)
        assert trip["tracking"]["completed"] == 3
        # Move one reached point: other reached points survive, including a reorder.
        moved = {**surplus, "y": 100}
        trip = update_trip(client, trip, [home, moved, inserted, garage])
        assert set(trip["tracking"]["reached_stop_ids"]) == {home["id"], inserted["id"]}
        assert trip["tracking"]["active"]
        trip = update_trip(client, trip, [home, inserted, garage])
        assert trip["tracking"]["completed"] == 2
        move(setup, 200)
        app.state.planning.poll_progress()
        assert latest(client)["tracking"]["note"] == "Trip complete"


def test_empty_routes_retry_creation_and_moved_departure(setup):
    with TestClient(app_for(setup)) as client:
        payload = {**plan("akryllax"), "id": str(uuid4()), "stops": []}
        trip = client.post("/api/v1/trips", json=payload).json()
        assert trip["id"] == payload["id"] and trip["distance"] == 0
        assert client.post("/api/v1/trips", json=payload).json() == trip
        assert len(client.get("/api/v1/trips").json()["trips"]) == 1
        for stops in [[], plan()["stops"][:1]]:
            trip = update_trip(client, trip, stops)
            for command in ["start", "next"]:
                assert (
                    client.post(
                        f"/api/v1/trips/{trip['id']}/progress",
                        json={"version": trip["version"], "action": command},
                    ).status_code
                    == 422
                )
        trip = update_trip(client, trip, plan()["stops"])
        trip = action(client, trip, "start")
        trip = action(client, trip, "pause")
        points = trip["stops"]
        points[0]["y"] = 500
        trip = update_trip(client, trip, points)
        trip = action(client, trip, "start")
        assert trip["tracking"]["completed"] == 0  # Resume cannot re-mark the moved departure.
        trip = update_trip(client, trip, [])
        assert not trip["tracking"]["active"]
        assert trip["tracking"]["reached_stop_ids"] == []
        duplicate = {**plan(), "stops": [points[0], points[0]]}
        assert client.post("/api/v1/trips", json=duplicate).status_code == 422


def test_legacy_progress_migration_is_persistent_and_idempotent(setup):
    with TestClient(app_for(setup)) as client:
        trip = client.post("/api/v1/trips", json=plan()).json()
        app = client.app
        import json

        with app.state.saved_map.lock, app.state.saved_map.db:
            tracking = dict(trip["tracking"], completed=2)
            tracking.pop("reached_stop_ids")
            app.state.saved_map.db.execute(
                "UPDATE trips SET stops=?,tracking=? WHERE id=?",
                (json.dumps(plan()["stops"]), json.dumps(tracking), trip["id"]),
            )
    with TestClient(app_for(setup)) as client:
        migrated = latest(client)
        assert migrated["version"] == trip["version"] + 1
        assert migrated["tracking"]["reached_stop_ids"] == [p["id"] for p in migrated["stops"][:2]]
    with TestClient(app_for(setup)) as client:
        assert latest(client) == migrated
