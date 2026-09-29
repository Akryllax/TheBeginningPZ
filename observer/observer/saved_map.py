"""Independent saved-map snapshots. No game commands or source writes."""

import json
import math
import sqlite3
import struct
import threading
import time
import zipfile
from contextlib import closing
from pathlib import Path

from .deaths import DeathLog, registered_players
from .formats import decode_coverage, decode_public_markers, stable_read
from .models import Bounds
from .player_keys import decode_player_keys, item_types
from .terrain import Terrain
from .vehicle_flags import decode_vehicle

# Runtime bounds verified on this Build 42.20.4 world; ZIP lengths and version
# must match before any coverage is accepted. Override only for another world.
DEFAULT_BOUNDS = Bounds(min_x=-250, min_y=-250, max_x=250, max_y=250)


def read_positions(path, kind):
    # A short read transaction obtains a coherent table view, including WAL.
    # Never use immutable=1 or copy only the main file of a live SQLite DB.
    with closing(
        sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True, timeout=0.1)
    ) as db:
        db.execute("PRAGMA query_only=ON")
        if kind == "players":
            rows = db.execute(
                "SELECT id,username,name,x,y,z FROM networkPlayers WHERE isDead=0"
            ).fetchall()
            return [
                {"id": r[0], "name": r[1], "character": r[2], "x": r[3], "y": r[4], "z": r[5]}
                for r in rows
                if all(
                    isinstance(v, (float, int)) and math.isfinite(v) and abs(v) < 200000
                    for v in r[3:6]
                )
            ]
        rows = db.execute(
            "SELECT id,x,y,worldversion,CASE WHEN length(data)<=16777216 THEN data END FROM vehicles"
        ).fetchall()
    records = []
    for id, x, y, version, data in rows:
        if not all(
            isinstance(v, (float, int)) and math.isfinite(v) and abs(v) < 200000 for v in (x, y)
        ):
            continue
        try:
            flags = decode_vehicle(data, version, x, y)
        except (ValueError, struct.error):
            continue  # Unknown layouts must never reveal unclassified cars.
        records.append({"id": id, "x": x, "y": y, "label": flags["model"], **flags})
    return records


class SavedMap:
    def __init__(
        self,
        data: Path,
        save: Path,
        maps: Path,
        world="AKR_Exploratory",
        bounds=DEFAULT_BOUNDS,
        logs=None,
    ):
        data.mkdir(parents=True, exist_ok=True)
        self.save, self.world, self.bounds = save, world, bounds
        self.scripts = maps.parent.parent / "scripts"
        self.lock = threading.RLock()
        self.poll_lock = threading.Lock()
        self.on_player_snapshot = None
        self.positions = None
        self.exploration = None
        self.db = sqlite3.connect(data / "saved-map.sqlite", check_same_thread=False)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute(
            "CREATE TABLE IF NOT EXISTS sources(name TEXT PRIMARY KEY,payload TEXT,modified INTEGER,checked INTEGER,error TEXT)"
        )
        self.db.execute("CREATE TABLE IF NOT EXISTS metadata(key TEXT PRIMARY KEY,value TEXT)")
        old = self.db.execute("SELECT value FROM metadata WHERE key='world'").fetchone()
        if old and old[0] != world:
            raise ValueError("Saved-map database belongs to another world")
        self.db.execute("INSERT OR IGNORE INTO metadata VALUES('world',?)", (world,))
        self.db.commit()
        self.death_log = DeathLog(self.db, logs) if logs is not None else None
        self.sources = {
            r[0]: {
                "payload": json.loads(r[1]),
                "modified_at": r[2],
                "checked_at": r[3],
                "error": r[4],
            }
            for r in self.db.execute("SELECT * FROM sources")
        }
        self.fingerprints = {}
        self.revision = int(time.time() * 1000)
        self.coverage_revision = self.revision
        self.indexing = True
        self.index_error = None
        self.terrain = Terrain(maps, data / "terrain-cache")

    def close(self):
        self.db.close()

    def publish(self, name, payload, modified, checked, error=None):
        with self.lock, self.db:
            previous = self.sources.get(name)
            content_changed = previous is None or previous["payload"] != payload
            if content_changed or previous["error"] != error or previous["modified_at"] != modified:
                self.revision += 1
                if name.startswith("coverage:") and content_changed:
                    self.coverage_revision += 1
            self.sources[name] = {
                "payload": payload,
                "modified_at": modified,
                "checked_at": checked,
                "error": error,
            }
            self.db.execute(
                "INSERT OR REPLACE INTO sources VALUES(?,?,?,?,?)",
                (name, json.dumps(payload, separators=(",", ":")), modified, checked, error),
            )

    def read_source(self, name, path, reader):
        now = int(time.time() * 1000)
        try:
            stat = path.stat()
            fingerprint = (stat.st_ino, stat.st_size, stat.st_mtime_ns)
            # Databases are always read: a WAL can change without the main file changing.
            if path.suffix != ".db" and self.fingerprints.get(name) == fingerprint:
                with self.lock:
                    self.sources[name]["checked_at"] = now
                return
            payload = reader(path)
            modified = (
                max(
                    [stat.st_mtime_ns]
                    + [
                        p.stat().st_mtime_ns
                        for p in (Path(str(path) + "-wal"), Path(str(path) + "-journal"))
                        if p.exists()
                    ]
                )
                // 1000000
            )
            self.publish(name, payload, modified, now)
            self.fingerprints[name] = fingerprint
        except (
            OSError,
            ValueError,
            KeyError,
            sqlite3.Error,
            struct.error,
            zipfile.BadZipFile,
        ) as exc:
            with self.lock:
                old = self.sources.get(name, {"payload": [], "modified_at": None})
            self.publish(
                name,
                [] if name == "car_keys" else old["payload"],
                old["modified_at"],
                now,
                f"{type(exc).__name__}: {str(exc)[:160]}",
            )

    def read_car_keys(self, path):
        types = item_types(self.save / "WorldDictionaryReadable.lua", self.scripts)
        keys = set()
        with closing(
            sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True, timeout=0.1)
        ) as db:
            db.execute("PRAGMA query_only=ON")
            for version, blob in db.execute(
                "SELECT worldversion,CASE WHEN length(data)<=16777216 THEN data END FROM networkPlayers WHERE isDead=0"
            ):
                keys.update(decode_player_keys(blob, version, types))
        return sorted(keys)

    def read_deaths(self):
        if self.death_log is None:
            return
        now = int(time.time() * 1000)
        try:
            names = registered_players(self.save / "players.db")
            with self.lock, self.db:
                events, modified = self.death_log.poll(names)
            self.publish("deaths", events, modified, now)
        except (OSError, ValueError, sqlite3.Error) as exc:
            with self.lock:
                old = self.sources.get("deaths", {"payload": [], "modified_at": None})
            self.publish(
                "deaths",
                old["payload"],
                old["modified_at"],
                now,
                f"{type(exc).__name__}: {str(exc)[:160]}",
            )

    def poll(self):
        with self.poll_lock:
            self.read_source("car_keys", self.save / "players.db", self.read_car_keys)
            for kind in ("players", "vehicles"):
                self.read_source(
                    kind, self.save / f"{kind}.db", lambda p, k=kind: read_positions(p, k)
                )
            self.read_deaths()
            if self.on_player_snapshot:
                self.on_player_snapshot()
            self.read_source(
                "markers",
                self.save / "servermap_symbols.bin",
                lambda p: decode_public_markers(stable_read(p)),
            )
            paths = list((self.save / "map_visited_server").glob("*.zip"))
            for p in sorted(paths):
                self.read_source(
                    "coverage:" + p.stem,
                    p,
                    lambda path: list(decode_coverage(stable_read(path), path.stem, self.bounds)),
                )
            try:
                self.terrain.index_known(self.known())
                self.index_error = None
            except (OSError, ValueError, struct.error) as exc:
                self.index_error = str(exc)[:180]
            finally:
                self.indexing = False

    def known(self, observer=None):
        with self.lock:
            result = {}
            for name in self.observers():
                if observer is not None and name != observer:
                    continue
                source = self.sources.get("coverage:" + name, {})
                cells = (
                    self.exploration.select(name, source)
                    if self.exploration
                    else source.get("payload", [])
                )
                for x, y, flags in cells:
                    if flags:
                        result[(x, y)] = result.get((x, y), 0) | flags
            return result

    def observers(self):
        return sorted(
            {k[9:] for k in self.sources if k.startswith("coverage:")}
            | (self.exploration.masks.keys() if self.exploration else set())
        )

    def status(self):
        with self.lock:
            sources = {
                k: {f: v for f, v in source.items() if f != "payload"}
                for k, source in self.sources.items()
            }
            players = self.sources.get("players", {}).get("payload", [])
            return {
                "mode": "saved_map",
                "world": self.world,
                "revision": self.revision,
                "coverage_revision": self.coverage_revision,
                "terrain_revision": self.terrain.revision,
                "indexing": self.indexing,
                "index_error": self.index_error,
                "observers": self.observers(),
                "players": players,
                **(self.positions.snapshot() if self.positions else {}),
                **({"exploration_feed": self.exploration.snapshot()} if self.exploration else {}),
                "markers": self.sources.get("markers", {}).get("payload", []),
                "deaths": self.sources.get("deaths", {}).get("payload", []),
                "death_markers_since": self.death_log.since if self.death_log else None,
                "sources": sources,
                "bounds": self.bounds.model_dump(),
                "poll_seconds": 30,
            }

    def features(self, bounds, observer=None):
        known = self.known(observer)
        x0, y0, x1, y1 = bounds
        with self.lock:
            keys = set(self.sources.get("car_keys", {}).get("payload", []))
            vehicles = [
                {
                    **{k: v for k, v in r.items() if k != "key_id"},
                    "category": "wreck"
                    if r.get("wreck")
                    else "keyed"
                    if r.get("key_id") in keys
                    else "hotwired",
                }
                for r in self.sources.get("vehicles", {}).get("payload", [])
                if (r.get("wreck") or r.get("hotwired") or r.get("key_id") in keys)
                and x0 <= r["x"] < x1
                and y0 <= r["y"] < y1
                and (math.floor(r["x"] / 32) * 32, math.floor(r["y"] / 32) * 32) in known
            ]
            return {"revision": self.revision, "vehicles": vehicles}
