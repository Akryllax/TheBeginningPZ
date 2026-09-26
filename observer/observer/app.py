import asyncio
import contextlib
import json
import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles

from .collector import Collector
from .store import Store


def create_app(
    data: Path | None = None,
    spool: Path | None = None,
    save: Path | None = None,
    frontend: Path | None = None,
    polling=True,
):
    if os.getenv("OBSERVER_MODE") == "saved_map":
        from .saved_api import create_saved_app

        return create_saved_app(data=data, save=save, frontend=frontend, polling=polling)
    data = data or Path(os.getenv("OBSERVER_DATA", "data"))
    spool = spool or (Path(os.environ["OBSERVER_SPOOL"]) if os.getenv("OBSERVER_SPOOL") else None)
    save = save or (Path(os.environ["OBSERVER_SAVE"]) if os.getenv("OBSERVER_SAVE") else None)
    frontend = frontend or Path(os.getenv("OBSERVER_FRONTEND", "web/dist"))
    store = Store(data / "observer.sqlite", os.getenv("OBSERVER_WORLD", "AKR_Exploratory"))
    collector = Collector(store, spool, save)

    @asynccontextmanager
    async def lifespan(app):
        async def poll():
            while True:
                try:
                    await asyncio.to_thread(collector.poll)
                except Exception as exc:  # noqa: BLE001 - preserve the last snapshot if the collector fails
                    collector.error(f"collector unavailable: {type(exc).__name__}")
                await asyncio.sleep(0.5)

        task = asyncio.create_task(poll()) if polling else None
        yield
        if task:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task
        store.close()

    app = FastAPI(
        title="Zomboid Observer",
        version="0.1.0",
        lifespan=lifespan,
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    app.state.store = store
    app.state.collector = collector

    @app.middleware("http")
    async def headers(request: Request, call_next):
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "same-origin"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; worker-src 'self' blob:; object-src 'none'; frame-ancestors 'none'"
        )
        if request.url.path.startswith("/api/"):
            response.headers["Cache-Control"] = "no-cache"
        return response

    @app.get("/healthz")
    def health():
        return {"ok": True, "revision": store.revision()}

    @app.get("/api/v1/world")
    def world():
        return store.status()

    @app.get("/api/v1/coverage")
    def coverage(observer: str | None = Query(None, max_length=128)):
        return {"revision": store.revision(), "unit_size": 32, "cells": store.coverage(observer)}

    @app.get("/api/v1/chunks")
    def chunks(
        x: float = Query(..., ge=-200_000, le=200_000),
        y: float = Query(..., ge=-200_000, le=200_000),
        radius: int = Query(96, ge=16, le=256),
        z: int = Query(0, ge=-32, le=64),
        observer: str | None = Query(None, max_length=128),
    ):
        objects, truncated = store.objects((x - radius, y - radius, x + radius, y + radius), z, observer)
        return {"revision": store.revision(), "objects": objects, "truncated": truncated}

    @app.get("/api/v1/elements/{id:path}")
    def element(id: str, observer: str | None = Query(None, max_length=128)):
        if len(id) > 128:
            raise HTTPException(404)
        value = store.element(id, observer)
        if value is None:
            raise HTTPException(404, "No recorded observation of this element")
        return value

    @app.get("/api/v1/events")
    async def events(request: Request):
        async def stream():
            previous = -1
            idle = 0
            while not await request.is_disconnected():
                revision = store.revision()
                if revision != previous:
                    yield f"id: {revision}\nevent: revision\ndata: {json.dumps({'revision': revision})}\n\n"
                    previous, idle = revision, 0
                elif idle >= 15:
                    yield ": heartbeat\n\n"
                    idle = 0
                idle += 1
                await asyncio.sleep(1)

        return StreamingResponse(
            stream(), media_type="text/event-stream", headers={"X-Accel-Buffering": "no"}
        )

    @app.get("/downloads/ZomboidObserver.zip")
    def mod_download():
        path = Path(os.getenv("OBSERVER_MOD_ZIP", "artifacts/ZomboidObserver.zip"))
        if not path.exists():
            raise HTTPException(404, "Mod package has not been built")
        return FileResponse(path, filename="ZomboidObserver.zip", media_type="application/zip")

    if frontend.is_dir():
        app.mount("/", StaticFiles(directory=frontend, html=True), name="frontend")
    return app
