import asyncio
import contextlib
import json
import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import Response, StreamingResponse
from fastapi.staticfiles import StaticFiles

from .city_labels import read_city_labels, visible_city_labels
from .drawings import install_drawings
from .exploration import install_exploration
from .planning import install_planning
from .positions import install_positions
from .routing import install_routing
from .saved_map import SavedMap
from .storyteller import install_storyteller


def create_saved_app(data=None, save=None, frontend=None, polling=True, position_token=None):
    data = data or Path(os.getenv("OBSERVER_DATA", "data"))
    save = save or Path(os.getenv("OBSERVER_SAVE", "/save"))
    frontend = frontend or Path(os.getenv("OBSERVER_FRONTEND", "web/dist"))
    state = SavedMap(
        data,
        save,
        Path(os.getenv("OBSERVER_MAPS", "/game/media/maps/Muldraugh, KY")),
        world=os.getenv("OBSERVER_WORLD", "AKR_Exploratory"),
        logs=Path(os.environ["OBSERVER_LOGS"]) if os.getenv("OBSERVER_LOGS") else None,
    )
    city_labels = read_city_labels(state.terrain.root)

    @asynccontextmanager
    async def lifespan(app):
        async def loop():
            while True:
                try:
                    await asyncio.to_thread(state.poll)
                except Exception:
                    logging.getLogger(__name__).exception("Saved-map collection failed")
                    state.index_error = "Collection failed; retaining the last successful snapshot"
                await asyncio.sleep(30)

        task = asyncio.create_task(loop()) if polling else None

        async def route_loop():
            while True:
                try:
                    await asyncio.to_thread(app.state.routing.tick, app.state.planning)
                except Exception:
                    logging.getLogger(__name__).exception("Trip routing failed")
                await asyncio.sleep(0.5)

        route_task = asyncio.create_task(route_loop())
        yield
        route_task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await route_task
        await asyncio.to_thread(app.state.routing.close)
        if task:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task
        # A cancelled to_thread operation may still be finishing a source read.
        await asyncio.to_thread(state.poll_lock.acquire)
        try:
            state.close()
        finally:
            state.poll_lock.release()

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.saved_map = state

    @app.middleware("http")
    async def headers(request, call_next):
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "same-origin"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'"
        )
        response.headers["Cache-Control"] = "no-cache"
        return response

    @app.get("/healthz")
    def health():
        return {"ok": True, "mode": "saved_map"}

    @app.get("/api/v1/world")
    def world():
        return state.status()

    @app.get("/api/v1/coverage")
    def coverage(observer: str | None = Query(None, max_length=128)):
        # Keep labels and their clipping mask in one consistent snapshot.
        with state.lock:
            known = state.known(observer)
            return {
                "revision": state.coverage_revision,
                "unit_size": 32,
                "cells": [[x, y, f] for (x, y), f in known.items()],
                "city_labels": visible_city_labels(city_labels, known),
            }

    @app.get("/api/v1/map/tiles/{zoom}/{x}/{y}.png")
    def tile(zoom: int, x: int, y: int, observer: str | None = Query(None, max_length=128)):
        if (
            not -4 <= zoom <= 3
            or abs(x * 256 * 2 ** (-zoom)) > 201000
            or abs(y * 256 * 2 ** (-zoom)) > 201000
        ):
            raise HTTPException(422, "Tile outside supported bounds")
        try:
            payload = state.terrain.tile(
                zoom, x, y, state.known(observer), (observer, state.coverage_revision)
            )
        except (OSError, ValueError) as exc:
            raise HTTPException(503, "Terrain temporarily unavailable") from exc
        return Response(payload, media_type="image/png")

    @app.get("/api/v1/map/features")
    def features(
        x: float = Query(..., ge=-200000, le=200000),
        y: float = Query(..., ge=-200000, le=200000),
        radius: float = Query(512, ge=1, le=16000),
        observer: str | None = Query(None, max_length=128),
    ):
        return state.features((x - radius, y - radius, x + radius, y + radius), observer)

    @app.get("/api/v1/places/search")
    def search(
        q: str = Query(..., min_length=1, max_length=100),
        x: float = Query(..., ge=-200000, le=200000),
        y: float = Query(..., ge=-200000, le=200000),
        observer: str | None = Query(None, max_length=128),
        limit: int = Query(20, ge=1, le=20),
    ):
        return {
            "results": state.terrain.search(q, state.known(observer), x, y, limit),
            "indexing": state.indexing,
            "error": state.index_error,
        }

    @app.get("/api/v1/events")
    async def events(request: Request):
        async def stream():
            previous = None
            previous_planning = None
            previous_positions = None
            previous_exploration = None
            ticks = 0
            while not await request.is_disconnected():
                version = (state.revision, state.terrain.revision, state.indexing)
                if version != previous:
                    yield f"event: revision\ndata: {json.dumps(version)}\n\n"
                    previous = version
                planning = json.dumps(
                    {**app.state.planning.snapshot(), "pings": app.state.planning.active_pings()}
                )
                if planning != previous_planning:
                    yield f"event: planning\ndata: {planning}\n\n"
                    previous_planning = planning
                positions = json.dumps(app.state.positions.snapshot())
                if positions != previous_positions:
                    yield f"event: positions\ndata: {positions}\n\n"
                    previous_positions = positions
                exploration = json.dumps(app.state.exploration.snapshot())
                if exploration != previous_exploration:
                    yield f"event: exploration\ndata: {exploration}\n\n"
                    previous_exploration = exploration
                if ticks % 40 == 0:
                    yield ": heartbeat\n\n"
                ticks += 1
                await asyncio.sleep(0.25)

        return StreamingResponse(
            stream(), media_type="text/event-stream", headers={"X-Accel-Buffering": "no"}
        )

    install_drawings(app)
    install_planning(app, state)
    install_routing(app, state)
    install_positions(app, state, position_token)
    install_exploration(app, state, app.state.positions.token)
    install_storyteller(app, state, app.state.positions.token, frontend)

    @app.api_route("/downloads/{path:path}", methods=["GET", "POST"])
    @app.api_route("/api/{path:path}", methods=["GET", "POST", "PUT", "DELETE"])
    def disabled(path: str):
        raise HTTPException(404, "Not available in saved-map mode")

    if frontend.is_dir():
        app.mount("/", StaticFiles(directory=frontend, html=True), name="frontend")
    return app
