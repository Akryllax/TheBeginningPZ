"""Optional authenticated protobuf position feed; latest samples only, no trail."""

import hmac
import json
import math
import os
import re
import time
from pathlib import Path
from uuid import UUID

from fastapi import HTTPException, Request
from fastapi.responses import Response
from google.protobuf.message import DecodeError

from .proto.positions_pb2 import PositionSnapshot


class LivePositions:
    max_body = 65536
    stale_ms = 10000

    def __init__(self, state, token=None, clock=time.time):
        self.state, self.token, self.clock = state, token, clock
        self.session = None
        self.started = self.sequence = self.captured = self.received = 0
        self.latest = {}
        self.online = set()
        # Only anti-replay counters survive a web restart, never position history.
        with state.lock:
            row = state.db.execute(
                "SELECT value FROM metadata WHERE key='position_watermark'"
            ).fetchone()
            if row:
                self.session, self.started, self.sequence, self.captured = json.loads(row[0])
        if token is not None and not re.fullmatch(r"[a-f0-9]{64}", token):
            raise ValueError(
                "Position ingest token must contain 64 lowercase hexadecimal characters"
            )

    @property
    def enabled(self):
        return self.token is not None

    def fresh(self):
        now = int(self.clock() * 1000)
        return bool(self.received and max(now - self.received, now - self.captured) < self.stale_ms)

    @staticmethod
    def uuid(value):
        try:
            return str(UUID(value)) == value
        except (ValueError, TypeError, AttributeError):
            return False

    @staticmethod
    def text(value):
        return (
            0 < len(value) <= 128
            and value.strip()
            and not any(ord(c) < 32 or ord(c) == 127 for c in value)
        )

    def accept(self, payload):
        if len(payload) > self.max_body:
            raise HTTPException(413, "Position snapshot too large")
        try:
            message = PositionSnapshot.FromString(payload)
        except DecodeError as exc:
            raise HTTPException(422, "Invalid protobuf snapshot") from exc
        now = int(self.clock() * 1000)
        if message.protocol_version != 1 or message.world != self.state.world:
            raise HTTPException(422, "Unsupported position protocol or world")
        if (
            not self.uuid(message.server_session)
            or not 0 < message.sequence < 2**63
            or not 0 < message.session_started_at_ms <= message.captured_at_ms
            or not now - self.stale_ms < message.captured_at_ms <= now + 5000
            or len(message.players) > 128
        ):
            raise HTTPException(422, "Invalid or expired position snapshot")
        samples = {}
        connections = set()
        for p in message.players:
            if (
                not self.text(p.username)
                or not self.text(p.character)
                or p.username in samples
                or not self.uuid(p.connection_id)
                or p.connection_id in connections
                or not all(math.isfinite(n) and abs(n) <= 200000 for n in (p.x, p.y))
                or not math.isfinite(p.z)
                or not -32 <= p.z <= 64
            ):
                raise HTTPException(422, "Invalid player position")
            connections.add(p.connection_id)
            samples[p.username] = {
                "name": p.username,
                "character": p.character,
                "x": p.x,
                "y": p.y,
                "z": p.z,
                "dead": p.dead,
                "observed_at": message.captured_at_ms,
                "connection": f"{message.server_session}:{p.connection_id}",
                "sample": f"live:{message.server_session}:{message.sequence}",
                "source": "live",
            }
        with self.state.lock, self.state.db:
            if message.server_session == self.session:
                if (
                    message.session_started_at_ms != self.started
                    or message.sequence <= self.sequence
                    or message.captured_at_ms < self.captured
                ):
                    raise HTTPException(409, "Out-of-order position snapshot")
            elif self.session and message.session_started_at_ms <= self.started:
                raise HTTPException(409, "Retired exporter session")
            self.state.db.execute(
                "INSERT OR REPLACE INTO metadata VALUES('position_watermark',?)",
                (
                    json.dumps(
                        [
                            message.server_session,
                            message.session_started_at_ms,
                            message.sequence,
                            message.captured_at_ms,
                        ]
                    ),
                ),
            )
            self.session, self.started = message.server_session, message.session_started_at_ms
            self.sequence, self.captured, self.received = (
                message.sequence,
                message.captured_at_ms,
                now,
            )
            self.online = set(samples)
            self.latest.update(samples)
            # Bound even a long-running server with many distinct visitors.
            for name, _ in sorted(self.latest.items(), key=lambda item: item[1]["observed_at"]):
                if len(self.latest) <= 256:
                    break
                if name not in self.online:
                    del self.latest[name]

    def player_samples(self):
        with self.state.lock:
            if not self.fresh():
                return {}
            return {name: dict(self.latest[name]) for name in self.online}

    def snapshot(self):
        with self.state.lock:
            fresh = self.fresh()
            saved = self.state.sources.get("players", {}).get("payload", [])
            players = {
                p["name"]: {
                    **p,
                    "source": "saved",
                    "online": False if fresh else None,
                    "observed_at": None,
                }
                for p in saved
            }
            for name, sample in self.latest.items():
                previous = players.get(name)
                # A genuinely different saved character supersedes an old, offline observation.
                if (
                    (name not in self.online or not fresh)
                    and previous
                    and previous["character"] != sample["character"]
                ):
                    continue
                if sample["dead"]:
                    players.pop(name, None)
                    continue
                players[name] = {
                    "id": previous["id"] if previous else f"player:{name}",
                    **{k: sample[k] for k in ("name", "character", "x", "y", "z", "observed_at")},
                    "source": "live" if fresh and name in self.online else "last_seen",
                    "online": name in self.online if fresh else None,
                }
            return {
                "players": sorted(players.values(), key=lambda p: p["name"]),
                "position_feed": {
                    "enabled": self.enabled,
                    "status": "live"
                    if fresh
                    else "stale"
                    if self.received
                    else "waiting"
                    if self.enabled
                    else "disabled",
                    "received_at": self.received or None,
                    "captured_at": self.captured or None,
                    "stale_after_ms": self.stale_ms,
                    "interval_ms": 1000,
                    "online_count": len(self.online) if fresh else None,
                },
            }


def install_positions(app, state, token=None):
    if token is None and os.getenv("OBSERVER_POSITION_TOKEN_FILE"):
        token = Path(os.environ["OBSERVER_POSITION_TOKEN_FILE"]).read_text().strip()
    positions = app.state.positions = LivePositions(state, token)
    state.positions = positions

    @app.post("/internal/v1/positions")
    async def ingest(request: Request):
        if not positions.enabled:
            raise HTTPException(404, "Position feed is disabled")
        if not hmac.compare_digest(
            request.headers.get("authorization", "").encode(),
            ("Bearer " + positions.token).encode(),
        ):
            raise HTTPException(401, "Invalid position exporter credentials")
        if request.headers.get("content-type", "").split(";", 1)[0] != "application/x-protobuf":
            raise HTTPException(415, "Expected application/x-protobuf")
        body = bytearray()
        async for chunk in request.stream():
            if len(body) + len(chunk) > positions.max_body:
                raise HTTPException(413, "Position snapshot too large")
            body.extend(chunk)
        positions.accept(bytes(body))
        # All samples are now detached primitives. No game/save-file read is required.
        app.state.planning.poll_progress()
        return Response(status_code=204)

    return positions
