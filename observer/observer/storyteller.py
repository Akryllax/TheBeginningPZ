"""Optional, read-only diagnostics. Latest bounded sample, never public map data."""

import hmac
import json
import math
import os
import time
from pathlib import Path

from fastapi import HTTPException, Request
from fastapi.responses import FileResponse, Response
from google.protobuf.message import DecodeError

from .positions import LivePositions
from .proto.positions_pb2 import StorytellerSnapshot


class Storyteller:
    max_body = 65536
    stale_ms = 20000

    def __init__(self, state, token, enabled=False, clock=time.time):
        self.state, self.token, self.enabled, self.clock = state, token, enabled, clock
        self.session = None
        self.started = self.sequence = self.captured = self.received = 0
        self.latest = None
        with state.lock:
            row = state.db.execute(
                "SELECT value FROM metadata WHERE key='storyteller_watermark'"
            ).fetchone()
            if row:
                self.session, self.started, self.sequence, self.captured = json.loads(row[0])
            row = state.db.execute(
                "SELECT value FROM metadata WHERE key='storyteller_latest'"
            ).fetchone()
            if row:
                self.latest = json.loads(row[0])

    @staticmethod
    def number(value, low=0, high=1e12):
        return math.isfinite(value) and low <= value <= high

    @staticmethod
    def text(value, limit=128, empty=False):
        return (
            (empty or bool(value.strip()))
            and len(value) <= limit
            and not any(ord(c) < 32 or ord(c) == 127 for c in value)
        )

    def accept(self, payload):
        if len(payload) > self.max_body:
            raise HTTPException(413, "Storyteller snapshot too large")
        try:
            m = StorytellerSnapshot.FromString(payload)
        except DecodeError as exc:
            raise HTTPException(422, "Invalid protobuf snapshot") from exc
        now = int(self.clock() * 1000)
        if m.protocol_version != 1 or m.world != self.state.world:
            raise HTTPException(422, "Unsupported storyteller protocol or world")
        if (
            not LivePositions.uuid(m.server_session)
            or not 0 < m.sequence < 2**63
            or not 0 <= m.published_tick < 2**53
            or not 0 < m.session_started_at_ms <= m.captured_at_ms
            or not now - self.stale_ms < m.captured_at_ms <= now + 5000
            or m.mode not in ("observe", "active")
            or m.phase not in ("outbreak", "aftermath", "survival")
            or m.online_players > 128
            or not -1 <= m.npc_count <= 10000
            or not self.text(m.health, 256, empty=True)
            or len(m.cells) > 64
            or len(m.decisions) > 32
            or any(v > 1000000 for v in (m.cell_count, m.scan_queue, m.dropped_cells))
            or not all(self.number(v) for v in (m.world_age_hours, m.pressure, m.budget))
            or not all(
                self.number(v, high=60000)
                for v in (
                    m.timings_ms.last,
                    m.timings_ms.p95,
                    m.timings_ms.p99,
                    m.timings_ms.max,
                    m.capture_ms,
                )
            )
        ):
            raise HTTPException(422, "Invalid or expired storyteller snapshot")
        cells = []
        unique = set()
        for c in m.cells:
            key = c.x, c.y, c.z
            if (
                key in unique
                or not all(-200000 <= v <= 200000 and v % 32 == 0 for v in (c.x, c.y))
                or not -32 <= c.z <= 64
                or not all(self.number(v) for v in (c.dwell, c.wealth, c.age_hours))
                or not self.number(c.confidence, high=1)
            ):
                raise HTTPException(422, "Invalid storyteller cell")
            unique.add(key)
            cells.append(
                {
                    "x": c.x,
                    "y": c.y,
                    "z": c.z,
                    "dwell": c.dwell,
                    "wealth": c.wealth,
                    "confidence": c.confidence,
                    "age_hours": c.age_hours,
                }
            )
        decisions = []
        for d in m.decisions:
            if (
                not self.text(d.id)
                or not self.text(d.event, 64)
                or not self.text(d.outcome, 64)
                or not self.text(d.reason, 256, empty=True)
                or not self.number(d.at_hour)
            ):
                raise HTTPException(422, "Invalid storyteller decision")
            decisions.append(
                {
                    "id": d.id,
                    "event": d.event,
                    "outcome": d.outcome,
                    "reason": d.reason,
                    "at_hour": d.at_hour,
                }
            )
        scenario = None
        if m.HasField("scenario"):
            s = m.scenario
            if (
                not self.text(s.status, 32)
                or not self.text(s.phase, 32)
                or not self.text(s.worker_health, 128, empty=True)
                or not self.number(s.elapsed_hours)
                or any(
                    v > 1000000
                    for v in (
                        s.residents,
                        s.materialized,
                        s.pedestrians,
                        s.vehicles,
                        s.plan_rejections,
                        s.lease_changes,
                    )
                )
                or not -1 <= s.worker_queue <= 1000000
                or not all(
                    self.number(v, high=60000)
                    for v in (s.worker_compute_ms, s.last_step_ms, s.p95_step_ms, s.p99_step_ms)
                )
            ):
                raise HTTPException(422, "Invalid First Week diagnostics")
            scenario = {
                k: getattr(s, k)
                for k in (
                    "status",
                    "phase",
                    "elapsed_hours",
                    "residents",
                    "materialized",
                    "pedestrians",
                    "vehicles",
                    "worker_health",
                    "worker_compute_ms",
                    "plan_rejections",
                    "lease_changes",
                    "last_step_ms",
                    "p95_step_ms",
                    "p99_step_ms",
                )
            }
            scenario["worker_queue"] = None if s.worker_queue == -1 else s.worker_queue
        latest = {
            "world": m.world,
            "tick": m.published_tick,
            "world_age_hours": m.world_age_hours,
            "phase": m.phase,
            "mode": m.mode,
            "pressure": m.pressure,
            "budget": m.budget,
            "online_players": m.online_players,
            "npc_count": None if m.npc_count == -1 else m.npc_count,
            "timings_ms": {k: getattr(m.timings_ms, k) for k in ("last", "p95", "p99", "max")},
            "cells": cells,
            "decisions": decisions,
            "cell_count": m.cell_count,
            "scan_queue": m.scan_queue,
            "dropped_cells": m.dropped_cells,
            "health": m.health,
            "truncated": m.truncated,
            "capture_ms": m.capture_ms,
            "captured_at": m.captured_at_ms,
            "scenario": scenario,
        }
        with self.state.lock, self.state.db:
            if m.server_session == self.session:
                if (
                    m.session_started_at_ms != self.started
                    or m.sequence <= self.sequence
                    or m.captured_at_ms < self.captured
                ):
                    raise HTTPException(409, "Out-of-order storyteller snapshot")
            elif self.session and m.session_started_at_ms <= self.started:
                raise HTTPException(409, "Retired exporter session")
            self.state.db.execute(
                "INSERT OR REPLACE INTO metadata VALUES('storyteller_watermark',?)",
                (
                    json.dumps(
                        [m.server_session, m.session_started_at_ms, m.sequence, m.captured_at_ms]
                    ),
                ),
            )
            self.state.db.execute(
                "INSERT OR REPLACE INTO metadata VALUES('storyteller_latest',?)",
                (json.dumps(latest),),
            )
            self.latest = latest
            self.session, self.started = m.server_session, m.session_started_at_ms
            self.sequence, self.captured, self.received = m.sequence, m.captured_at_ms, now

    def snapshot(self):
        with self.state.lock:
            now = int(self.clock() * 1000)
            fresh = bool(
                self.received and max(now - self.received, now - self.captured) < self.stale_ms
            )
            return {
                "status": "live" if fresh else "stale" if self.latest else "waiting",
                "received_at": self.received or None,
                "stale_after_ms": self.stale_ms,
                "interval_ms": 5000,
                "snapshot": self.latest,
            }


def install_storyteller(app, state, token, frontend: Path):
    feed = app.state.storyteller = Storyteller(
        state, token, enabled=os.getenv("OBSERVER_STORYTELLER_DEBUG") == "1"
    )

    @app.post("/internal/v1/storyteller")
    async def ingest(request: Request):
        if not feed.enabled or not feed.token:
            raise HTTPException(404, "Storyteller diagnostics are disabled")
        if not hmac.compare_digest(
            request.headers.get("authorization", "").encode(), ("Bearer " + feed.token).encode()
        ):
            raise HTTPException(401, "Invalid storyteller exporter credentials")
        if request.headers.get("content-type", "").split(";", 1)[0] != "application/x-protobuf":
            raise HTTPException(415, "Expected application/x-protobuf")
        body = bytearray()
        async for chunk in request.stream():
            if len(body) + len(chunk) > feed.max_body:
                raise HTTPException(413, "Storyteller snapshot too large")
            body.extend(chunk)
        feed.accept(bytes(body))
        return Response(status_code=204)

    @app.get("/debug/storyteller/snapshot")
    def snapshot():
        if not feed.enabled:
            raise HTTPException(404, "Storyteller diagnostics are disabled")
        return feed.snapshot()

    @app.get("/debug/storyteller")
    @app.get("/debug/storyteller/")
    def page():
        if not feed.enabled:
            raise HTTPException(404, "Storyteller diagnostics are disabled")
        if not (frontend / "index.html").is_file():
            raise HTTPException(503, "Frontend has not been built")
        return FileResponse(frontend / "index.html")
