"""Exact server exploration masks, independent of slow world-save writes."""

import asyncio
import hashlib
import hmac
import json
import struct
import time
import zlib
from itertools import islice

from fastapi import HTTPException, Request
from fastapi.responses import Response
from google.protobuf.message import DecodeError

from .formats import coverage_length, decode_coverage_bits
from .positions import LivePositions
from .proto.positions_pb2 import ExplorationSnapshot


class LiveExploration:
    max_body = 8 * 1024 * 1024
    stale_ms = 15000
    max_cells = 262144

    def __init__(self, state, token=None, clock=time.time):
        self.state, self.token, self.clock = state, token, clock
        self.session = None
        self.started = self.sequence = self.captured = self.received = 0
        with state.lock, state.db:
            state.db.execute(
                "CREATE TABLE IF NOT EXISTS exploration_masks("
                "username TEXT PRIMARY KEY,payload TEXT,digest TEXT,captured INTEGER)"
            )
            self.masks = {
                name: {"payload": json.loads(payload), "digest": digest, "captured_at": captured}
                for name, payload, digest, captured in state.db.execute(
                    "SELECT * FROM exploration_masks"
                )
            }
            row = state.db.execute(
                "SELECT value FROM metadata WHERE key='exploration_watermark'"
            ).fetchone()
            if row:
                self.session, self.started, self.sequence, self.captured = json.loads(row[0])

    def select(self, name, saved):
        mask = self.masks.get(name)
        if mask and mask["captured_at"] >= (saved.get("modified_at") or 0):
            return mask["payload"]
        return saved.get("payload", [])

    def snapshot(self):
        with self.state.lock:
            now = int(self.clock() * 1000)
            fresh = self.received and max(now - self.received, now - self.captured) < self.stale_ms
            return {
                "enabled": self.token is not None,
                "status": "live"
                if fresh
                else "stale"
                if self.received
                else "waiting"
                if self.token
                else "disabled",
                "received_at": self.received or None,
                "captured_at": self.captured if self.received else None,
                "stale_after_ms": self.stale_ms,
                "interval_ms": 2000,
            }

    def accept(self, payload):
        if len(payload) > self.max_body:
            raise HTTPException(413, "Exploration snapshot too large")
        try:
            message = ExplorationSnapshot.FromString(payload)
        except DecodeError as exc:
            raise HTTPException(422, "Invalid exploration snapshot") from exc
        now = int(self.clock() * 1000)
        if message.protocol_version != 1 or message.world != self.state.world:
            raise HTTPException(422, "Unsupported exploration protocol or world")
        if (
            not LivePositions.uuid(message.server_session)
            or not 0 < message.sequence < 2**63
            or not 0 < message.session_started_at_ms <= message.captured_at_ms
            or not now - self.stale_ms < message.captured_at_ms <= now + 5000
            or len(message.players) > 1
        ):
            raise HTTPException(422, "Invalid or expired exploration snapshot")
        changed = False
        # The endpoint runs in a worker. This lock serializes validation with
        # saved-file imports and prevents accepting out-of-order parallel posts.
        with self.state.lock:
            if message.server_session == self.session:
                if (
                    message.session_started_at_ms != self.started
                    or message.sequence <= self.sequence
                    or message.captured_at_ms < self.captured
                ):
                    raise HTTPException(409, "Out-of-order exploration snapshot")
            elif self.session and message.session_started_at_ms <= self.started:
                raise HTTPException(409, "Retired exploration exporter")
            updates = []
            for p in message.players:
                bounds = self.state.bounds
                if not LivePositions.text(p.username) or (
                    p.min_cell_x,
                    p.min_cell_y,
                    p.max_cell_x,
                    p.max_cell_y,
                    p.world_version,
                ) != (bounds.min_x, bounds.min_y, bounds.max_x, bounds.max_y, bounds.world_version):
                    raise HTTPException(422, "Invalid exploration player or world bounds")
                if p.username not in self.masks and len(self.masks) >= 256:
                    raise HTTPException(422, "Too many exploration players")
                digest = hashlib.sha256(p.visited_zlib).hexdigest()
                previous = self.masks.get(p.username)
                if previous and previous["digest"] == digest:
                    cells = previous["payload"]
                else:
                    expected = coverage_length(bounds)
                    try:
                        inflater = zlib.decompressobj()
                        raw = inflater.decompress(p.visited_zlib, expected + 1)
                        if (
                            len(raw) != expected
                            or not inflater.eof
                            or inflater.unused_data
                            or inflater.unconsumed_tail
                        ):
                            raise ValueError("Invalid exploration length or compressed stream")
                        cells = list(islice(decode_coverage_bits(raw, bounds), self.max_cells + 1))
                        if len(cells) > self.max_cells:
                            raise ValueError("Exploration exceeds known-block limit")
                    except (ValueError, zlib.error) as exc:
                        raise HTTPException(422, "Invalid exploration flags") from exc
                before = self.state.known(p.username)
                updates.append((p.username, cells, digest, before))
            # Commit the mask and watermark together; no partial state on bad input.
            with self.state.db:
                for name, cells, digest, _ in updates:
                    self.state.db.execute(
                        "INSERT OR REPLACE INTO exploration_masks VALUES(?,?,?,?)",
                        (
                            name,
                            json.dumps(cells, separators=(",", ":")),
                            digest,
                            message.captured_at_ms,
                        ),
                    )
                self.state.db.execute(
                    "INSERT OR REPLACE INTO metadata VALUES('exploration_watermark',?)",
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
            for name, cells, digest, before in updates:
                self.masks[name] = {
                    "payload": cells,
                    "digest": digest,
                    "captured_at": message.captured_at_ms,
                }
                changed |= before != self.state.known(name)
            self.session, self.started = message.server_session, message.session_started_at_ms
            self.sequence, self.captured, self.received = (
                message.sequence,
                message.captured_at_ms,
                now,
            )
            if changed:
                self.state.revision += 1
                self.state.coverage_revision += 1
        if changed:
            try:
                self.state.terrain.index_known(self.state.known())
                self.state.index_error = None
            except (OSError, ValueError, struct.error) as exc:
                # Accepted exploration remains valid; the normal collector retries indexing.
                self.state.index_error = str(exc)[:180]


def install_exploration(app, state, token):
    exploration = app.state.exploration = state.exploration = LiveExploration(state, token)

    @app.post("/internal/v1/exploration")
    async def ingest(request: Request):
        if exploration.token is None:
            raise HTTPException(404, "Exploration feed is disabled")
        if not hmac.compare_digest(
            request.headers.get("authorization", "").encode(),
            ("Bearer " + exploration.token).encode(),
        ):
            raise HTTPException(401, "Invalid exploration exporter credentials")
        if request.headers.get("content-type", "").split(";", 1)[0] != "application/x-protobuf":
            raise HTTPException(415, "Expected application/x-protobuf")
        body = bytearray()
        async for chunk in request.stream():
            if len(body) + len(chunk) > exploration.max_body:
                raise HTTPException(413, "Exploration snapshot too large")
            body.extend(chunk)
        await asyncio.to_thread(exploration.accept, bytes(body))
        return Response(status_code=204)

    return exploration
