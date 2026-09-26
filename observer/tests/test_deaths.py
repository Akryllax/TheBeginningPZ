import sqlite3
from datetime import datetime

import pytest
from fastapi.testclient import TestClient

from observer.deaths import parse_death
from observer.saved_api import create_saved_app
from observer.saved_map import SavedMap

START = datetime.fromisoformat("2026-09-14T01:00:00+00:00").timestamp()


def death(name="akryllax", second=1, x=12):
    return f"[14-09-26 01:00:{second:02}.123] user {name} died at ({x},23,0) (non pvp).\n"


@pytest.fixture
def sources(tmp_path, monkeypatch):
    monkeypatch.setattr("observer.deaths.time.time", lambda: START)
    save, maps, logs = (tmp_path / name for name in ("save", "maps", "logs"))
    for path in (save, maps, logs):
        path.mkdir()
    with sqlite3.connect(save / "players.db") as db:
        db.execute("CREATE TABLE networkPlayers(id,username,name,x,y,z,isDead)")
        db.execute("INSERT INTO networkPlayers VALUES(1,'akryllax','Tommy',4,5,0,0)")
    (logs / "2026-09-14_00-00_user.txt").touch()
    monkeypatch.setenv("OBSERVER_LOGS", str(logs))
    monkeypatch.setenv("OBSERVER_MAPS", str(maps))
    return tmp_path / "data", save, maps, logs


def test_exact_death_formats_and_victim_coordinates():
    event = parse_death(death("Name With Spaces"), "user")
    assert event["name"] == "Name With Spaces"
    assert (event["x"], event["y"], event["z"]) == (12, 23, 0)
    assert event["occurred_at"] == int(START * 1000) + 1123
    pvp = '[14-09-26 01:00:01.123][IMPORTANT] Kill: "attacker" (100,200,0) killed "akryllax" (12,23,0).\n'
    assert parse_death(pvp, "pvp") == parse_death(death(), "user")
    # Connections, injuries, announcements and malformed lines are not deaths.
    for line in (
        '[14-09-26 01:00:01.123] "akryllax" disconnected player (12,23,0).',
        pvp.replace("Kill:", "Combat:").replace(" killed ", " hit "),
        "akryllax is dead.",
        death().replace("14-09-26", "99-99-26"),
        death(x=200000),
        death().replace(",23,0)", ",23,99)"),
        death("bad\tname"),
    ):
        assert parse_death(line, "user") is None
        assert parse_death(line, "pvp") is None


def test_new_deaths_only_account_filter_and_delayed_first_save(sources):
    data, save, maps, logs = sources
    state = SavedMap(data, save, maps, logs=logs)
    log = next(logs.glob("*_user.txt"))
    log.write_text(death().replace("14-09-26", "13-09-26") + death("Bob") + death("miki") + death())
    before = (log.read_bytes(), (save / "players.db").read_bytes())
    state.read_deaths()
    assert [d["name"] for d in state.status()["deaths"]] == ["akryllax"]
    assert before == (log.read_bytes(), (save / "players.db").read_bytes())
    revision = state.revision
    state.read_deaths()
    assert state.revision == revision
    # An event can precede a new player's first DB save; do not lose it.
    with sqlite3.connect(save / "players.db") as db:
        db.execute("INSERT INTO networkPlayers VALUES(2,'miki','Michael',50,50,0,1)")
    state.read_deaths()
    assert {d["name"] for d in state.status()["deaths"]} == {"akryllax", "miki"}
    state.close()


def test_history_survives_respawn_restart_rotation_and_repeated_deaths(sources):
    data, save, maps, logs = sources
    state = SavedMap(data, save, maps, logs=logs)
    log = next(logs.glob("*_user.txt"))
    log.write_text(death())
    state.read_deaths()
    first = state.status()["deaths"][0]
    state.close()
    # Respawn overwrites the live character row; history uses logged coordinates.
    with sqlite3.connect(save / "players.db") as db:
        db.execute("UPDATE networkPlayers SET name='New Survivor',x=900,y=800,isDead=0")
    archive = logs / "logs_2026-09-14"
    archive.mkdir()
    log = log.rename(archive / log.name)
    with log.open("a") as file:
        file.write(death(second=2))
    state = SavedMap(data, save, maps, logs=logs)
    assert state.status()["deaths"] == [first]
    state.read_deaths()
    assert len(state.status()["deaths"]) == 2
    assert all(d["x"] == 12 for d in state.status()["deaths"])
    # A copied archive cannot duplicate markers.
    copy = logs / "2026-09-14_01-00_user.txt"
    copy.write_bytes(log.read_bytes())
    state.read_deaths()
    assert len(state.status()["deaths"]) == 2
    # Replace a file in-place with the same byte length, then truncate it.
    copy.write_text(death(second=3) + death(second=4))
    state.read_deaths()
    copy.write_text(death(second=5))
    state.read_deaths()
    assert len(state.status()["deaths"]) == 5
    assert len({d["id"] for d in state.status()["deaths"]}) == 5
    state.close()


def test_partial_append_and_read_failure_retain_history(sources):
    data, save, maps, logs = sources
    state = SavedMap(data, save, maps, logs=logs)
    log = next(logs.glob("*_user.txt"))
    log.write_text(death() + death(second=2)[:40])
    state.read_deaths()
    assert len(state.status()["deaths"]) == 1
    state.close()
    with log.open("a") as file:
        file.write(death(second=2)[40:])
    state = SavedMap(data, save, maps, logs=logs)
    state.read_deaths()
    previous = state.status()["deaths"]
    assert len(previous) == 2
    lock = sqlite3.connect(save / "players.db")
    lock.execute("BEGIN EXCLUSIVE")
    state.read_deaths()
    assert state.status()["sources"]["deaths"]["error"]
    assert state.status()["deaths"] == previous
    lock.rollback()
    lock.close()
    state.read_deaths()
    assert state.status()["sources"]["deaths"]["error"] is None
    logs.rename(logs.with_name("unavailable"))
    state.read_deaths()
    assert state.status()["sources"]["deaths"]["error"]
    assert state.status()["deaths"] == previous
    state.close()


def test_death_api_contains_only_confirmed_markers(sources):
    data, save, _maps, logs = sources
    log = next(logs.glob("*_user.txt"))
    log.write_text(death() + death("Bob") + "[14-09-26 01:00:01.123] private connection metadata.\n")
    app = create_saved_app(data=data, save=save, polling=False)
    app.state.saved_map.read_deaths()
    with TestClient(app) as client:
        response = client.get("/api/v1/world")
        assert response.status_code == 200
        payload = response.json()
        assert payload["death_markers_since"] == int(START * 1000)
        assert len(payload["deaths"]) == 1
        assert set(payload["deaths"][0]) == {"id", "name", "x", "y", "z", "occurred_at"}
        assert "Bob" not in response.text and "private connection" not in response.text
        assert client.get("/logs/" + log.name).status_code == 404
