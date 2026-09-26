"""Shared web-only trips and short-lived pings. No game commands or writes."""

import hashlib
import json
import math
import sqlite3
import time
from contextlib import closing
from itertools import pairwise
from typing import Literal
from uuid import UUID, uuid4

from fastapi import HTTPException, Query, Request
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from .roads import MAX_STOPS
from .routing import RoutingOptions


class Stop(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False, str_strip_whitespace=True)
    id: UUID | None = None
    label: str = Field(min_length=1, max_length=120)
    x: float = Field(ge=-200000, le=200000)
    y: float = Field(ge=-200000, le=200000)
    z: int = Field(default=0, ge=-32, le=64)
    kind: Literal["manual", "generated", "origin"] = "manual"


class TripInput(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    id: UUID | None = None
    name: str = Field(min_length=1, max_length=80)
    stops: list[Stop] = Field(min_length=0, max_length=MAX_STOPS)
    version: int = Field(default=0, ge=0)
    follow_player: str | None = Field(default=None, min_length=1, max_length=128)
    arrival_radius: int = Field(default=40, ge=10, le=200)
    routing: RoutingOptions | None = None


class ProgressInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    version: int = Field(ge=1)
    action: Literal["start", "pause", "next", "reset"]


class PingInput(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    owner: UUID
    x: float = Field(ge=-200000, le=200000)
    y: float = Field(ge=-200000, le=200000)


class Planning:
    ttl = 12

    def __init__(self, state, clock=time.time):
        self.state, self.clock = state, clock
        self.pings = {}
        self.trash = {}
        self.progress_error = None
        self.routes = None
        self._snapshot_cache = (-1, [])
        with state.lock, state.db:
            state.db.execute(
                "CREATE TABLE IF NOT EXISTS trips(id TEXT PRIMARY KEY,name TEXT,stops TEXT,"
                "version INTEGER,created_at INTEGER,updated_at INTEGER)"
            )
            state.db.execute("INSERT OR IGNORE INTO metadata VALUES('trip_revision','0')")
            if "tracking" not in {r[1] for r in state.db.execute("PRAGMA table_info(trips)")}:
                state.db.execute("ALTER TABLE trips ADD COLUMN tracking TEXT NOT NULL DEFAULT '{}'")
            if "routing" not in {r[1] for r in state.db.execute("PRAGMA table_info(trips)")}:
                state.db.execute("ALTER TABLE trips ADD COLUMN routing TEXT")
            state.db.execute(
                "UPDATE trips SET tracking=? WHERE tracking='{}'",
                (
                    json.dumps(
                        {
                            "player": None,
                            "radius": 40,
                            "active": False,
                            "completed": 0,
                            "started_at": None,
                            "note": "Ready to start",
                        }
                    ),
                ),
            )

            # Migrate the old reached prefix without losing checkpoint history.
            for row in state.db.execute("SELECT * FROM trips").fetchall():
                stops, tracking = json.loads(row[2]), json.loads(row[6])
                if "reached_stop_ids" in tracking and all(p.get("id") for p in stops):
                    continue
                for point in stops:
                    point.setdefault("id", str(uuid4()))
                tracking["reached_stop_ids"] = [p["id"] for p in stops[: tracking.get("completed", 0)]]
                state.db.execute(
                    "UPDATE trips SET stops=?,tracking=?,version=version+1 WHERE id=?",
                    (json.dumps(stops), json.dumps(tracking), row[0]),
                )
                state.db.execute(
                    "UPDATE metadata SET value=CAST(value AS INTEGER)+1 WHERE key='trip_revision'"
                )

    @staticmethod
    def normalize_progress(stops, tracking):
        reached = set(tracking.get("reached_stop_ids", []))
        tracking["reached_stop_ids"] = [p["id"] for p in stops if p["id"] in reached]
        prefix = 0
        for point in stops:
            if point["id"] not in reached:
                break
            prefix += 1
        tracking["completed"] = prefix  # Compatibility with older viewers.
        if len(stops) < 2:
            tracking.update(active=False, note="Add at least two waypoints")
        elif tracking.get("note") == "Add at least two waypoints":
            tracking["note"] = "Ready to resume" if tracking.get("started_at") else "Ready to start"
        if len(stops) >= 2 and all(p["id"] in reached for p in stops):
            tracking.update(active=False, note="Trip complete")

    def trip(self, row):
        keys = ("id", "name", "stops", "version", "created_at", "updated_at", "tracking", "routing")
        result = dict(zip(keys, row))
        result["tracking"] = {
            k: v for k, v in json.loads(result["tracking"]).items() if not k.startswith("_")
        }
        stops = result["stops"] = json.loads(result["stops"])
        routing = json.loads(result["routing"]) if result.get("routing") else None
        result["routing"] = {k: v for k, v in routing.items() if not k.startswith("_")} if routing else None
        result["distance"] = round(
            sum(math.hypot(a["x"] - b["x"], a["y"] - b["y"]) for a, b in pairwise(stops)), 1
        )
        return result

    def snapshot(self):
        with self.state.lock:
            version = int(
                self.state.db.execute("SELECT value FROM metadata WHERE key='trip_revision'").fetchone()[0]
            )
            if version != self._snapshot_cache[0]:
                self._snapshot_cache = (
                    version,
                    [
                        self.trip(r)
                        for r in self.state.db.execute("SELECT * FROM trips ORDER BY updated_at DESC,id")
                    ],
                )
            trips = self._snapshot_cache[1]
        return {"revision": version, "trips": trips, "progress_error": self.progress_error}

    def save(self, value, id=None):
        with self.state.lock, self.state.db:
            db = self.state.db
            if not id and value.id:
                existing = db.execute("SELECT * FROM trips WHERE id=?", (str(value.id),)).fetchone()
                if existing:
                    return self.trip(existing)  # Retry of the initial autosave.
            old = db.execute("SELECT * FROM trips WHERE id=?", (id,)).fetchone() if id else None
            if id and not old:
                raise HTTPException(404, "This trip was deleted. Save your draft as a new trip.")
            if old and old[3] != value.version:
                raise HTTPException(
                    409, "Someone changed this trip. Reload it or save your draft as a new trip."
                )
            if not id and db.execute("SELECT count(*) FROM trips").fetchone()[0] >= 100:
                raise HTTPException(409, "The shared list is full (100 trips). Remove an unused trip first.")
            id = id or str(value.id or uuid4())
            now = int(self.clock() * 1000)
            previous = json.loads(old[2]) if old else []
            points = [s.model_dump(mode="json") for s in value.stops]
            used = {p["id"] for p in points if p["id"]}
            if len(used) != sum(bool(p["id"]) for p in points):
                raise HTTPException(422, "Waypoint IDs must be unique within a trip")
            for point, supplied in zip(points, value.stops):
                if not point["id"]:
                    match = next(
                        (
                            p
                            for p in previous
                            if p["id"] not in used and all(p[k] == point[k] for k in ("x", "y", "z"))
                        ),
                        None,
                    )
                    point["id"] = match["id"] if match else str(uuid4())
                    used.add(point["id"])
                before = next((p for p in previous if p["id"] == point["id"]), None)
                if before and "kind" not in supplied.model_fields_set:
                    point["kind"] = before.get("kind", "manual")
                if (
                    before
                    and before.get("kind") in ("generated", "origin")
                    and any(point[k] != before[k] for k in ("x", "y", "z"))
                ):
                    point["kind"] = "manual"
            stops = json.dumps(points)
            tracking = json.loads(old[6]) if old else {}
            if not old or tracking.get("player") != value.follow_player:
                tracking = {
                    "player": value.follow_player,
                    "active": False,
                    "completed": 0,
                    "reached_stop_ids": [],
                    "started_at": None,
                    "note": "Ready to start",
                }
            else:
                unchanged = {
                    p["id"]
                    for p in points
                    for before in previous
                    if p["id"] == before["id"] and all(p[k] == before[k] for k in ("x", "y", "z"))
                }
                tracking["reached_stop_ids"] = [
                    i for i in tracking.get("reached_stop_ids", []) if i in unchanged
                ]
                if tracking.get("note") == "Trip complete" and len(tracking["reached_stop_ids"]) < len(
                    points
                ):
                    tracking["note"] = "Ready to resume"
            tracking["radius"] = value.arrival_radius
            self.normalize_progress(points, tracking)
            settings = value.routing
            if old and "routing" not in value.model_fields_set and old[7]:
                previous_routing = json.loads(old[7])
                settings = RoutingOptions(
                    observer=previous_routing.get("observer"),
                    auto_reroute=previous_routing.get("auto_reroute", True),
                )
            row = (
                id,
                value.name,
                stops,
                old[3] + 1 if old else 1,
                old[4] if old else now,
                now,
                json.dumps(tracking),
                json.dumps(self.routes.saved_options(settings, old, points))
                if self.routes and settings and len(points) >= 2
                else None,
            )
            db.execute("INSERT OR REPLACE INTO trips VALUES(?,?,?,?,?,?,?,?)", row)
            db.execute("UPDATE metadata SET value=CAST(value AS INTEGER)+1 WHERE key='trip_revision'")
            return self.trip(row)

    def player_samples(self):
        if self.state.positions and self.state.positions.enabled:
            # An enabled live feed never silently switches checkpoint arrival
            # back to an older save during an outage or disconnect.
            return self.state.positions.player_samples()
        # Only a fingerprint and the latest position are used, never a trail or
        # inventory archive. Hash changes identify a fresh per-player save;
        # another player's DB write must not make stale coordinates count again.
        path = self.state.save / "players.db"
        with closing(sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True, timeout=0.1)) as db:
            db.execute("PRAGMA query_only=ON")
            rows = db.execute(
                "SELECT username,name,x,y,z,CASE WHEN length(data)<=16777216 THEN data END FROM networkPlayers WHERE isDead=0"
            ).fetchall()
        samples = {}
        for name, character, x, y, z, blob in rows:
            if (
                not isinstance(blob, bytes)
                or not blob
                or not all(
                    isinstance(n, (float, int)) and math.isfinite(n) and abs(n) < 200000 for n in (x, y, z)
                )
            ):
                continue
            digest = hashlib.sha256(blob + json.dumps([character, x, y, z]).encode()).hexdigest()
            samples[name] = {"character": character, "x": x, "y": y, "z": z, "sample": digest}
        return samples

    def progress(self, id, value):
        with self.state.lock, self.state.db:
            row = self.state.db.execute("SELECT * FROM trips WHERE id=?", (id,)).fetchone()
            if not row:
                raise HTTPException(404, "Trip not found")
            if row[3] != value.version:
                raise HTTPException(409, "Trip progress changed. Reload the saved trip and try again.")
            tracking = json.loads(row[6])
            stops = json.loads(row[2])
            count = len(stops)
            reached = set(tracking.get("reached_stop_ids", []))
            if value.action in ("start", "next") and count < 2:
                raise HTTPException(422, "Add at least two waypoints before tracking progress")
            if value.action == "start":
                if not tracking.get("player"):
                    raise HTTPException(422, "Choose a player to follow and save the trip first.")
                if len(reached) >= count:
                    raise HTTPException(409, "This trip is complete. Reset progress to run it again.")
                try:
                    sample = self.player_samples().get(tracking["player"])
                except (OSError, sqlite3.Error) as exc:
                    raise HTTPException(503, "Saved player positions are unavailable") from exc
                if sample is None:
                    raise HTTPException(409, "No fresh position is available for this player")
                if sample.get("dead"):
                    raise HTTPException(409, "This character is dead. Resume after reconnecting.")
                if tracking.get("started_at") is None:
                    reached.add(stops[0]["id"])
                tracking.update(
                    active=True,
                    started_at=int(self.clock() * 1000),
                    _sample=sample["sample"],
                    _character=sample["character"],
                    _source=sample.get("source", "saved"),
                    _connection=sample.get("connection"),
                    note="Waiting for a fresh live position"
                    if sample.get("source") == "live"
                    else "Waiting for a fresh player save",
                )
            elif value.action == "pause":
                tracking.update(active=False, note="Paused")
            elif value.action == "reset":
                reached.clear()
                tracking.update(active=False, completed=0, started_at=None, note="Ready to start")
                tracking.pop("_sample", None)
                tracking.pop("_character", None)
                tracking.pop("_source", None)
                tracking.pop("_connection", None)
            elif value.action == "next":
                next_point = next((p for p in stops if p["id"] not in reached), None)
                if next_point:
                    reached.add(next_point["id"])
                tracking["note"] = "Marked reached by a viewer"
            tracking["reached_stop_ids"] = list(reached)
            self.normalize_progress(stops, tracking)
            self.write_progress(row, tracking, visible=True)
            return self.trip(self.state.db.execute("SELECT * FROM trips WHERE id=?", (id,)).fetchone())

    def write_progress(self, row, tracking, visible):
        self.state.db.execute(
            "UPDATE trips SET tracking=?,version=?,updated_at=? WHERE id=?",
            (
                json.dumps(tracking),
                row[3] + int(visible),
                int(self.clock() * 1000) if visible else row[5],
                row[0],
            ),
        )
        if visible:
            self.state.db.execute(
                "UPDATE metadata SET value=CAST(value AS INTEGER)+1 WHERE key='trip_revision'"
            )

    def poll_progress(self):
        try:
            with self.state.lock:
                rows = self.state.db.execute("SELECT * FROM trips").fetchall()
                active = [r for r in rows if json.loads(r[6]).get("active")]
            if not active:
                self.progress_error = None
                return
            samples = self.player_samples()
            initial = {r[0]: (r[3], r[6]) for r in active}
            with self.state.lock, self.state.db:
                # Re-read after game DB access: a viewer may have edited a trip.
                for row in self.state.db.execute("SELECT * FROM trips").fetchall():
                    if initial.get(row[0]) != (row[3], row[6]):
                        continue
                    t = json.loads(row[6])
                    if not t.get("active"):
                        continue
                    sample = samples.get(t["player"])
                    deaths = self.state.sources.get("deaths", {}).get("payload", [])
                    died = any(
                        d["name"] == t["player"] and d["occurred_at"] >= t["started_at"] for d in deaths
                    )
                    if died or sample and (sample.get("dead") or sample["character"] != t.get("_character")):
                        t.update(active=False, note="Character died or changed. Resume when ready.")
                        self.write_progress(row, t, True)
                        continue
                    if not sample or sample["sample"] == t.get("_sample"):
                        continue
                    if sample.get("source", "saved") != t.get("_source", "saved"):
                        # Upgrading an active saved-position trip establishes a
                        # baseline; an old location cannot count as a new arrival.
                        t.update(
                            _source=sample.get("source", "saved"),
                            _connection=sample.get("connection"),
                            _sample=sample["sample"],
                            note="Waiting for a fresh live position"
                            if sample.get("source") == "live"
                            else "Waiting for a fresh player save",
                        )
                        self.write_progress(row, t, True)
                        continue
                    if sample.get("connection") != t.get("_connection"):
                        t.update(
                            active=False, note="Player reconnected or character changed. Resume when ready."
                        )
                        self.write_progress(row, t, True)
                        continue
                    t["_sample"] = sample["sample"]
                    stops = json.loads(row[2])
                    reached = set(t.get("reached_stop_ids", []))
                    before = len(reached)
                    # Only unresolved points, in route order, near a fresh actual save.
                    for stop in stops:
                        if stop["id"] in reached:
                            continue
                        if (
                            math.floor(sample["z"]) != stop["z"]
                            or math.hypot(sample["x"] - stop["x"], sample["y"] - stop["y"]) > t["radius"]
                        ):
                            break
                        reached.add(stop["id"])
                    if len(reached) != before:
                        t["note"] = (
                            "Reached in latest live position"
                            if sample.get("source") == "live"
                            else "Reached in latest saved position"
                        )
                    t["reached_stop_ids"] = list(reached)
                    self.normalize_progress(stops, t)
                    self.write_progress(row, t, len(reached) != before)
            self.progress_error = (
                "Live positions are unavailable; automatic checkpoints are waiting."
                if self.state.positions and self.state.positions.enabled and not self.state.positions.fresh()
                else None
            )
        except (OSError, sqlite3.Error, ValueError, KeyError):
            self.progress_error = "Auto progress is waiting for readable saved player positions."

    def delete(self, id, version):
        with self.state.lock, self.state.db:
            row = self.state.db.execute("SELECT * FROM trips WHERE id=?", (id,)).fetchone()
            if not row:
                raise HTTPException(404, "Trip not found")
            if row[3] != version:
                raise HTTPException(409, "Someone changed this trip. Reload it before deleting.")
            self.state.db.execute("DELETE FROM trips WHERE id=?", (id,))
            self.state.db.execute(
                "UPDATE metadata SET value=CAST(value AS INTEGER)+1 WHERE key='trip_revision'"
            )
            self.trash = {k: v for k, v in self.trash.items() if v[1] > self.clock()}
            if len(self.trash) >= 100:
                self.trash.pop(next(iter(self.trash)))
            self.trash[id] = (row, self.clock() + 600)
            return self.trip(row)

    def restore(self, id):
        with self.state.lock, self.state.db:
            record = self.trash.get(id)
            if not record or record[1] <= self.clock():
                raise HTTPException(404, "Undo has expired (10 minutes, or an Observer restart)")
            if self.state.db.execute("SELECT count(*) FROM trips").fetchone()[0] >= 100:
                raise HTTPException(409, "The shared list is full")
            row = list(record[0])
            row[3] += 1
            row[5] = int(self.clock() * 1000)
            self.state.db.execute("INSERT INTO trips VALUES(?,?,?,?,?,?,?,?)", row)
            self.state.db.execute(
                "UPDATE metadata SET value=CAST(value AS INTEGER)+1 WHERE key='trip_revision'"
            )
            del self.trash[id]
            return self.trip(row)

    def active_pings(self):
        now = self.clock()
        self.pings = {k: p for k, p in self.pings.items() if p["expires_at"] > now * 1000}
        return [{k: v for k, v in p.items() if k != "owner"} for p in self.pings.values()]

    def ping(self, value):
        self.active_pings()
        owner = str(value.owner)
        now = self.clock() * 1000
        mine = [p for p in self.pings.values() if p["owner"] == owner]
        if any(now - p["created_at"] < 1000 for p in mine):
            raise HTTPException(429, "Wait a second before pinging again")
        if len(self.pings) >= 64 and not mine:
            raise HTTPException(429, "Too many active pings; try again shortly")
        # Each viewer has one current pointer, rather than a map full of pings.
        for p in mine:
            del self.pings[p["id"]]
        id = str(uuid4())
        self.pings[id] = {
            "id": id,
            "owner": owner,
            "x": value.x,
            "y": value.y,
            "created_at": now,
            "expires_at": now + self.ttl * 1000,
        }
        return {"id": id, "ttl": self.ttl}


async def body(request, model):
    origin = request.headers.get("origin")
    if origin and origin != f"{request.url.scheme}://{request.headers.get('host')}":
        raise HTTPException(403, "Same-origin requests required")
    payload = bytearray()
    async for chunk in request.stream():
        payload.extend(chunk)
        if len(payload) > 131072:
            raise HTTPException(413, "Request too large")
    try:
        return model.model_validate_json(payload)
    except ValidationError as exc:
        raise HTTPException(422, "Check the name, coordinates and stop count (up to 256)") from exc


def install_planning(app, state):
    planning = Planning(state)
    app.state.planning = planning
    state.on_player_snapshot = planning.poll_progress

    @app.get("/api/v1/trips")
    def trips():
        return planning.snapshot()

    @app.post("/api/v1/trips")
    async def create(request: Request):
        return planning.save(await body(request, TripInput))

    @app.put("/api/v1/trips/{id}")
    async def update(id: UUID, request: Request):
        return planning.save(await body(request, TripInput), str(id))

    @app.delete("/api/v1/trips/{id}")
    async def delete(id: UUID, request: Request, version: int = Query(..., ge=1)):
        origin = request.headers.get("origin")
        if origin and origin != f"{request.url.scheme}://{request.headers.get('host')}":
            raise HTTPException(403, "Same-origin requests required")
        return planning.delete(str(id), version)

    @app.post("/api/v1/trips/{id}/progress")
    async def progress(id: UUID, request: Request):
        return planning.progress(str(id), await body(request, ProgressInput))

    @app.post("/api/v1/trips/{id}/restore")
    async def restore(id: UUID, request: Request):
        origin = request.headers.get("origin")
        if origin and origin != f"{request.url.scheme}://{request.headers.get('host')}":
            raise HTTPException(403, "Same-origin requests required")
        return planning.restore(str(id))

    @app.get("/api/v1/pings")
    async def pings():
        return {"pings": planning.active_pings()}

    @app.post("/api/v1/pings")
    async def ping(request: Request):
        return planning.ping(await body(request, PingInput))
