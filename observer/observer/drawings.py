"""Bounded, temporary web annotations; never touches game or Observer save files."""

import asyncio
import json
import time
from uuid import UUID

from fastapi import HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field, ValidationError


class Stroke(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    id: UUID
    owner: UUID
    points: list[tuple[float, float]] = Field(min_length=1, max_length=1024)


class Drawings:
    ttl = 300
    colors = ("#ffcc33", "#ff729f", "#65e5ff", "#bc9cff", "#8fff8a")

    def __init__(self, clock=time.time):
        self.clock = clock
        self.strokes = {}
        self.version = 0

    def prune(self):
        for key, stroke in list(self.strokes.items()):
            if stroke["expires"] <= self.clock():
                del self.strokes[key]

    def put(self, value: Stroke):
        self.prune()
        key, owner = str(value.id), str(value.owner)
        if any(abs(n) > 200000 for point in value.points for n in point):
            raise HTTPException(422, "Coordinates outside supported bounds")
        old = self.strokes.get(key)
        if old and old["owner"] != owner:
            raise HTTPException(403, "Stroke belongs to another viewer")
        if not old and (
            len(self.strokes) >= 256
            or sum(s["owner"] == owner for s in self.strokes.values()) >= 32
        ):
            raise HTTPException(
                429, "Drawing limit reached; clear your drawings or wait for expiry"
            )
        self.version += 1
        self.strokes[key] = {
            "id": key,
            "owner": owner,
            "points": value.points,
            "color": self.colors[value.owner.int % len(self.colors)],
            "expires": self.clock() + self.ttl,
            "version": self.version,
        }

    def clear(self, owner):
        self.strokes = {k: s for k, s in self.strokes.items() if s["owner"] != str(owner)}

    def changes(self, previous):
        self.prune()
        current = {key: s["version"] for key, s in self.strokes.items()}
        return current, {
            "strokes": [
                {k: v for k, v in s.items() if k != "owner"}
                for key, s in self.strokes.items()
                if previous.get(key) != s["version"]
            ],
            "removed": list(previous.keys() - current.keys()),
        }


def install_drawings(app):
    hub = Drawings()
    app.state.drawings = hub

    def same_origin(request):
        origin = request.headers.get("origin")
        if origin and origin != f"{request.url.scheme}://{request.headers.get('host')}":
            raise HTTPException(403, "Same-origin requests required")

    @app.post("/api/v1/drawings")
    async def put(request: Request):
        same_origin(request)
        payload = bytearray()
        async for chunk in request.stream():
            payload.extend(chunk)
            if len(payload) > 65536:
                raise HTTPException(413, "Drawing too large")
        try:
            value = Stroke.model_validate_json(payload)
        except ValidationError as exc:
            raise HTTPException(422, "Invalid drawing") from exc
        hub.put(value)
        return {"ok": True, "ttl": hub.ttl}

    @app.delete("/api/v1/drawings/{owner}")
    async def clear(owner: UUID, request: Request):
        same_origin(request)
        hub.clear(owner)
        return {"ok": True}

    @app.get("/api/v1/drawings/events")
    async def events(request: Request):
        async def stream():
            previous, ticks = {}, 0
            yield "event: reset\ndata: {}\n\n"
            while not await request.is_disconnected():
                current, changes = hub.changes(previous)
                if changes["strokes"] or changes["removed"]:
                    yield f"event: drawings\ndata: {json.dumps(changes)}\n\n"
                elif ticks % 100 == 0:
                    yield ": heartbeat\n\n"
                previous = current
                ticks += 1
                await asyncio.sleep(0.1)

        return StreamingResponse(
            stream(), media_type="text/event-stream", headers={"X-Accel-Buffering": "no"}
        )
