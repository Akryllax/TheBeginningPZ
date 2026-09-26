"""Route jobs and shared trip navigation. A single process owns expensive map work."""

import copy
import hashlib
import json
import logging
import math
import multiprocessing
import threading
import time
from collections import OrderedDict
from concurrent.futures import ProcessPoolExecutor
from itertools import pairwise
from typing import Literal
from uuid import NAMESPACE_URL, UUID, uuid4, uuid5

from fastapi import HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field

from .roads import MAX_STOPS, calculate, cells_on_line


class RoutingOptions(BaseModel):
    model_config = ConfigDict(extra="forbid")
    auto_reroute: bool = True
    observer: str | None = Field(default=None, min_length=1, max_length=128)
    request_id: str | None = Field(default=None, pattern=r"^[a-f0-9]{64}$")


class Endpoint(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False, str_strip_whitespace=True)
    id: UUID | None = None
    label: str = Field(min_length=1, max_length=120)
    x: float = Field(ge=-200000, le=200000)
    y: float = Field(ge=-200000, le=200000)
    z: int = Field(default=0, ge=-32, le=64)
    kind: Literal["manual", "generated", "origin"] = "manual"


class RouteInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    stops: list[Endpoint] = Field(min_length=2, max_length=MAX_STOPS)
    observer: str | None = Field(default=None, min_length=1, max_length=128)


class RecalculateInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    version: int = Field(ge=1)


def point_key(points):
    return [(p.get("id"), p["x"], p["y"], p.get("z", 0), p.get("kind", "manual")) for p in points]


def segment_distance(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = dx * dx + dy * dy
    t = max(0, min(1, ((p["x"] - a[0]) * dx + (p["y"] - a[1]) * dy) / length)) if length else 0
    return math.hypot(p["x"] - a[0] - t * dx, p["y"] - a[1] - t * dy)


class Routing:
    def __init__(self, state, clock=time.monotonic):
        self.state, self.clock = state, clock
        self.lock = threading.RLock()
        self.tick_lock = threading.Lock()
        self.jobs = OrderedDict()
        self.executor = None
        self.navigation = {}
        self.running = {}
        self.closed = False

    def close(self):
        with self.tick_lock, self.lock:
            self.closed = True
            if self.executor:
                self.executor.shutdown(wait=True, cancel_futures=True)

    def request(self, points, observer, turns=True):
        with self.state.lock:
            known = sorted(self.state.known(observer))
            coverage = self.state.coverage_revision
            terrain = self.state.terrain.revision
        if not known:
            return {"status": "unknown", "message": "No map knowledge is available for this selection."}
        if any(p.get("z", 0) != 0 for p in points):
            return {
                "status": "floor",
                "message": "Driving routes need ground-level endpoints. Pick road access on the map.",
            }
        mask = set(known)
        if any((math.floor(p["x"] / 32) * 32, math.floor(p["y"] / 32) * 32) not in mask for p in points):
            return {"status": "unknown", "message": "An endpoint is outside the selected map knowledge."}
        # Selection and mask are included; one observer cannot reuse another's route.
        key = hashlib.sha256(
            json.dumps([points, observer, known, terrain, turns], sort_keys=True).encode()
        ).hexdigest()
        with self.lock:
            if self.closed:
                return {"status": "unavailable", "message": "Routing is restarting."}
            now = self.clock()
            for job_id, job in list(self.jobs.items()):
                if job["future"].done() and now - job["used"] > 600:
                    del self.jobs[job_id]
            if key in self.jobs:
                self.jobs[key]["used"] = now
                self.jobs.move_to_end(key)
                result = self.result(key)
                if result["status"] != "preparing" or not self.jobs[key]["future"].done():
                    return result
                del self.jobs[key]  # Retry after a bounded cold-cache indexing slice.
            if sum(not j["future"].done() for j in self.jobs.values()) >= 8:
                return {"status": "busy", "message": "The route worker is busy. Retrying shortly…"}
            while len(self.jobs) >= 64:
                oldest = next((k for k, j in self.jobs.items() if j["future"].done()), None)
                if oldest is None:
                    break
                del self.jobs[oldest]
            if self.executor is None:
                self.executor = ProcessPoolExecutor(
                    max_workers=1, mp_context=multiprocessing.get_context("spawn")
                )
            future = self.executor.submit(
                calculate,
                str(self.state.terrain.root),
                str(self.state.terrain.cache.parent / "roads"),
                known,
                points,
                turns,
                terrain,
            )
            self.jobs[key] = {"future": future, "used": now, "observer": observer, "coverage": coverage}
            return {"status": "preparing", "message": "Calculating known roads…", "request_id": key}

    def result(self, key):
        with self.lock:
            job = self.jobs.get(key)
            if job is None:
                return None
            if not job["future"].done():
                return {"status": "preparing", "message": "Calculating known roads…", "request_id": key}
            try:
                value = job["future"].result()
            except Exception:
                logging.getLogger(__name__).exception("Road worker failed")
                value = {
                    "status": "unavailable",
                    "message": "The route worker could not finish this request.",
                }
            return {**value, "request_id": key, "observer": job["observer"], "_coverage": job["coverage"]}

    def saved_options(self, options, old, stops):
        if not options or len(stops) < 2:
            return None
        result = {"observer": options.observer, "auto_reroute": options.auto_reroute}
        if old:
            previous = json.loads(old[7]) if old[7] else None
            if (
                previous
                and previous.get("observer") == options.observer
                and point_key(json.loads(old[2])) == point_key(stops)
            ):
                return {**previous, **result}
        cached = self.result(options.request_id) if options.request_id else None
        if (
            cached
            and cached["status"] == "ready"
            and cached.get("observer") == options.observer
            and point_key(cached["stops"]) == point_key(stops)
            and self.still_known(cached, options.observer)
        ):
            return {**self.route_data(cached), **result}
        return {
            **result,
            "status": "pending",
            "message": "Calculating roads for edited checkpoints…",
            "geometry": [],
        }

    def still_known(self, value, observer):
        known = self.state.known(observer)
        return all(
            (x * 32, y * 32) in known
            for g in value.get("geometry", [])
            for a, b in pairwise(g["points"])
            for x, y in cells_on_line(a, b, 32)
        )

    @staticmethod
    def route_data(value):
        return {
            k: copy.deepcopy(value[k])
            for k in (
                "status",
                "message",
                "geometry",
                "road_distance",
                "access_distance",
                "map_revision",
                "_coverage",
            )
            if k in value
        }

    def write(self, planning, row, routing, stops=None, tracking=None):
        now = int(planning.clock() * 1000)
        self.state.db.execute(
            "UPDATE trips SET routing=?,stops=?,tracking=?,version=version+1,updated_at=? WHERE id=?",
            (
                json.dumps(routing),
                json.dumps(stops) if stops is not None else row[2],
                json.dumps(tracking) if tracking is not None else row[6],
                now,
                row[0],
            ),
        )
        self.state.db.execute("UPDATE metadata SET value=CAST(value AS INTEGER)+1 WHERE key='trip_revision'")

    def recalculate(self, planning, id, version):
        with self.state.lock, self.state.db:
            row = self.state.db.execute("SELECT * FROM trips WHERE id=?", (id,)).fetchone()
            if not row:
                raise HTTPException(404, "Trip not found")
            if row[3] != version:
                raise HTTPException(409, "This trip changed. Reload it before recalculating.")
            routing = json.loads(row[7]) if row[7] else None
            if not routing or len(json.loads(row[2])) < 2:
                raise HTTPException(422, "Fill a road route first")
            tracking = json.loads(row[6])
            if len(tracking.get("reached_stop_ids", [])) >= len(json.loads(row[2])):
                raise HTTPException(409, "Trip complete. Reset progress before recalculating.")
            routing.update(status="pending", message="Recalculating remaining route…", _rebuild=True)
            self.write(planning, row, routing)
            return planning.trip(self.state.db.execute("SELECT * FROM trips WHERE id=?", (id,)).fetchone())

    @staticmethod
    def live_sample(tracking, samples):
        sample = samples.get(tracking.get("player"))
        if (
            not sample
            or sample.get("dead")
            or math.floor(sample["z"]) != 0
            or sample.get("connection") != tracking.get("_connection")
            or sample.get("character") != tracking.get("_character")
        ):
            return None
        return sample

    def deviated(self, id, row, routing, stops, tracking, sample):
        nav = self.navigation.setdefault(id, {"sample": None, "count": 0, "attempt": -math.inf})
        if sample["sample"] == nav["sample"]:
            return False
        nav["sample"] = sample["sample"]
        reached = set(tracking.get("reached_stop_ids", []))
        next_stop = next((p for p in stops if p["id"] not in reached), None)
        indices = {p["id"]: i for i, p in enumerate(stops)}
        closest, nearest_leg = math.inf, -1
        for segment in routing.get("geometry", []):
            if segment["to"] in reached:
                continue
            # Include access connectors: standing at an off-road waypoint isn't a missed turn.
            for a, b in pairwise(segment["points"]):
                distance = segment_distance(sample, a, b)
                if distance < closest:
                    closest, nearest_leg = distance, indices.get(segment["from"], -1)
        missed = bool(
            next_stop
            and next_stop.get("kind") == "generated"
            and nearest_leg >= indices[next_stop["id"]]
            and math.hypot(sample["x"] - next_stop["x"], sample["y"] - next_stop["y"]) > tracking["radius"]
        )
        nav["count"] = nav["count"] + 1 if closest > 30 or missed else 0
        if nav["count"] >= 3 and self.clock() - nav["attempt"] >= 5:
            nav.update(count=0, attempt=self.clock())
            return True
        return False

    def tick(self, planning):
        with self.tick_lock:
            if not self.closed:
                self._tick(planning)

    def _tick(self, planning):
        with self.state.lock:
            rows = self.state.db.execute("SELECT * FROM trips WHERE routing IS NOT NULL").fetchall()
        samples = (
            self.state.positions.player_samples()
            if self.state.positions and self.state.positions.enabled
            else {}
        )
        ids = {r[0] for r in rows}
        self.running = {k: v for k, v in self.running.items() if k in ids}
        self.navigation = {k: v for k, v in self.navigation.items() if k in ids}
        for row in rows:
            routing, stops, tracking = json.loads(row[7]), json.loads(row[2]), json.loads(row[6])
            if not routing or len(stops) < 2:
                continue
            sample = self.live_sample(tracking, samples)
            if not sample or not tracking.get("active") or not routing.get("auto_reroute"):
                self.navigation.pop(row[0], None)
            rebuild = routing.get("_rebuild", False)
            failed_position = routing.get("_failed_position")
            moved_after_failure = bool(
                sample
                and failed_position
                and math.hypot(sample["x"] - failed_position[0], sample["y"] - failed_position[1]) > 30
            )
            if (
                (routing.get("status") == "ready" or moved_after_failure)
                and sample
                and tracking.get("active")
                and routing.get("auto_reroute")
                and self.deviated(
                    row[0],
                    row,
                    {**routing, "geometry": []} if moved_after_failure else routing,
                    stops,
                    tracking,
                    sample,
                )
            ):
                rebuild = True
            pending = routing.get("status") in ("pending", "waiting")
            # Retry a disconnected route only when exploration actually changes.
            retry = (
                routing.get("status") not in ("ready", "pending", "waiting")
                and routing.get("_coverage") != self.state.coverage_revision
            )
            if not (pending or rebuild or retry or row[0] in self.running):
                continue
            if retry and failed_position and tracking.get("active"):
                rebuild = True
            running = self.running.get(row[0])
            if running and running["version"] != row[3]:
                del self.running[row[0]]
                running = None
            if running:
                rebuild = running["rebuild"]
            reached = set(tracking.get("reached_stop_ids", []))
            prefix, origin, inputs = [], None, stops
            if rebuild:
                prefix = [p for p in stops if p["id"] in reached and p.get("kind") != "origin"]
                anchors = [p for p in stops if p["id"] not in reached and p.get("kind", "manual") == "manual"]
                if tracking.get("active"):
                    if not sample:
                        continue
                    origin = (
                        running["origin"]
                        if running
                        else {
                            "id": str(uuid4()),
                            "label": "Rejoin route",
                            "kind": "origin",
                            "x": sample["x"],
                            "y": sample["y"],
                            "z": 0,
                        }
                    )
                else:
                    origin = prefix[-1] if prefix else stops[0]
                    anchors = [p for p in anchors if p["id"] != origin["id"]]
                if not anchors:
                    remaining = [p for p in stops if p["id"] not in reached and p["id"] != origin["id"]]
                    if remaining:
                        # Removing the original destination makes the final surviving
                        # checkpoint the new destination, even if it began as a bend.
                        anchors = [{**remaining[-1], "kind": "manual"}]
                    else:
                        continue
                inputs = [origin, *anchors]
            if not running:
                value = self.request(inputs, routing.get("observer"), turns=rebuild)
                if value.get("request_id"):
                    self.running[row[0]] = {
                        "version": row[3],
                        "request_id": value["request_id"],
                        "rebuild": rebuild,
                        "origin": origin,
                        "sample": sample,
                    }
            else:
                value = self.result(running["request_id"])
                if value is None or value["status"] == "preparing":
                    # request() advances cold-cache jobs after each bounded indexing slice.
                    value = self.request(inputs, routing.get("observer"), turns=rebuild)
                    if value.get("request_id"):
                        running["request_id"] = value["request_id"]
            if value["status"] in ("preparing", "busy"):
                continue
            self.running.pop(row[0], None)
            with self.state.lock, self.state.db:
                current = self.state.db.execute("SELECT * FROM trips WHERE id=?", (row[0],)).fetchone()
                if not current or current[3] != row[3]:
                    continue
                if rebuild and tracking.get("active"):
                    fresh = self.live_sample(tracking, self.state.positions.player_samples())
                    if not fresh or math.hypot(fresh["x"] - origin["x"], fresh["y"] - origin["y"]) > 30:
                        continue
                if value["status"] == "ready" and not self.still_known(value, routing.get("observer")):
                    continue
                result = {**routing, **self.route_data(value), "_coverage": self.state.coverage_revision}
                result.pop("_rebuild", None)
                if value["status"] != "ready":
                    if rebuild and sample:
                        result["_failed_position"] = [sample["x"], sample["y"]]
                    self.write(planning, row, result)
                    continue
                result.pop("_failed_position", None)
                if rebuild:
                    ids = {p["id"] for p in prefix}
                    updated = prefix + [p for p in value["stops"] if p["id"] not in ids]
                    if len(updated) > MAX_STOPS:
                        result.update(
                            status="limit", message="Too many completed checkpoints. Start a new trip."
                        )
                        self.write(planning, row, result)
                        continue
                    if tracking.get("active"):
                        reached.add(origin["id"])
                    tracking["reached_stop_ids"] = list(reached)
                    planning.normalize_progress(updated, tracking)
                    history = [g for g in routing.get("geometry", []) if g["from"] in ids and g["to"] in ids]
                    result["geometry"] = history + result["geometry"]
                    result["road_distance"] = round(
                        sum(
                            math.dist(a, b)
                            for g in result["geometry"]
                            if g["kind"] == "road"
                            for a, b in pairwise(g["points"])
                        ),
                        1,
                    )
                    result["access_distance"] = round(
                        sum(
                            math.dist(a, b)
                            for g in result["geometry"]
                            if g["kind"] == "access"
                            for a, b in pairwise(g["points"])
                        ),
                        1,
                    )
                    self.write(planning, row, result, updated, tracking)
                else:
                    self.write(planning, row, result)


def install_routing(app, state):
    from .planning import body

    routing = app.state.routing = Routing(state)
    app.state.planning.routes = routing

    @app.post("/api/v1/routes")
    async def route(request: Request):
        value = await body(request, RouteInput)
        points = [p.model_dump(mode="json") for p in value.stops]
        for i, p in enumerate(points):
            # Stable IDs from callers permit request deduplication and retry.
            if not p["id"]:
                p["id"] = str(uuid5(NAMESPACE_URL, json.dumps([i, p], sort_keys=True)))
        if len({p["id"] for p in points}) != len(points):
            raise HTTPException(422, "Route endpoint IDs must be unique")
        result = routing.request(points, value.observer)
        return {k: v for k, v in result.items() if not k.startswith("_")}

    @app.post("/api/v1/trips/{id}/reroute")
    async def recalculate(id: UUID, request: Request):
        value = await body(request, RecalculateInput)
        return routing.recalculate(app.state.planning, str(id), value.version)

    return routing
