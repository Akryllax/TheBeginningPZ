import json
import sqlite3
import threading
import time
from pathlib import Path

from .models import Batch


def encode(value):
    return json.dumps(value, separators=(",", ":"), ensure_ascii=False)


class Store:
    def __init__(self, path: Path, world: str):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.world = world
        self.lock = threading.RLock()
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
            PRAGMA journal_mode=WAL;
            PRAGMA synchronous=NORMAL;
            CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS streams(session TEXT PRIMARY KEY,seq INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS objects(observer TEXT,id TEXT,x REAL,y REAL,z INTEGER,
                at INTEGER,deleted INTEGER,payload TEXT,PRIMARY KEY(observer,id));
            CREATE INDEX IF NOT EXISTS objects_position ON objects(x,y,z);
            CREATE INDEX IF NOT EXISTS objects_id ON objects(id,at);
            CREATE TABLE IF NOT EXISTS inspections(observer TEXT,id TEXT,category TEXT,slot TEXT,at INTEGER,payload TEXT,
                PRIMARY KEY(observer,id,category,slot));
            CREATE TABLE IF NOT EXISTS object_resets(observer TEXT,id TEXT,at INTEGER,PRIMARY KEY(observer,id));
            CREATE TABLE IF NOT EXISTS tiles(observer TEXT,x INTEGER,y INTEGER,z INTEGER,at INTEGER,
                PRIMARY KEY(observer,x,y,z));
            CREATE TABLE IF NOT EXISTS coverage(observer TEXT,x INTEGER,y INTEGER,flags INTEGER,
                PRIMARY KEY(observer,x,y));
            CREATE TABLE IF NOT EXISTS players(name TEXT PRIMARY KEY,payload TEXT,at INTEGER);
            CREATE TABLE IF NOT EXISTS markers(id TEXT PRIMARY KEY,payload TEXT);
            CREATE TABLE IF NOT EXISTS cursors(path TEXT PRIMARY KEY,identity TEXT,offset INTEGER);
        """)
        with self.db:
            found = self.db.execute("SELECT value FROM meta WHERE key='world'").fetchone()
            if found and json.loads(found[0]) != world:
                raise ValueError(
                    "Database belongs to a different world; use a separate data directory"
                )
            self.set_meta("world", world)

    def close(self):
        self.db.close()

    def set_meta(self, key, value):
        self.db.execute("INSERT OR REPLACE INTO meta VALUES(?,?)", (key, encode(value)))

    def meta(self, key, default=None):
        with self.lock:
            row = self.db.execute("SELECT value FROM meta WHERE key=?", (key,)).fetchone()
            return json.loads(row[0]) if row else default

    def revision(self):
        return self.meta("revision", 0)

    def touch(self):
        self.set_meta("revision", self.revision() + 1)

    def ingest(self, batch: Batch):
        if batch.world != self.world:
            raise ValueError("observation world mismatch")
        if batch.observed_at > time.time() * 1000 + 60_000:
            raise ValueError("observation timestamp is in the future")
        with self.lock, self.db:
            row = self.db.execute(
                "SELECT seq FROM streams WHERE session=?", (batch.session,)
            ).fetchone()
            previous = row[0] if row else 0
            if batch.sequence <= previous:
                return False
            if batch.sequence != previous + 1:
                self.set_meta(
                    "last_gap",
                    {
                        "session": batch.session,
                        "expected": previous + 1,
                        "received": batch.sequence,
                        "at": batch.observed_at,
                    },
                )
            self.db.execute(
                "INSERT OR REPLACE INTO streams VALUES(?,?)", (batch.session, batch.sequence)
            )
            who, at = batch.observer, batch.observed_at
            coverage_changed = False
            for tile in batch.tiles:
                if any(
                    int(obj.x // 1) != tile.x or int(obj.y // 1) != tile.y or obj.z != tile.z
                    for obj in tile.objects
                ):
                    raise ValueError("element is outside its observed tile")
                previous_tile = self.db.execute(
                    "SELECT at FROM tiles WHERE observer=? AND x=? AND y=? AND z=?",
                    (who, tile.x, tile.y, tile.z),
                ).fetchone()
                if previous_tile and previous_tile[0] > at:
                    continue
                self.db.execute(
                    "INSERT OR REPLACE INTO tiles VALUES(?,?,?,?,?)",
                    (who, tile.x, tile.y, tile.z, at),
                )
                present = {obj.id for obj in tile.objects}
                # Seeing an empty tile is an observation too. Tombstones supersede earlier
                # sightings by any group member, without deleting the other member's history.
                known = self.db.execute(
                    "SELECT DISTINCT id FROM objects WHERE x>=? AND x<? AND y>=? AND y<? AND z=?",
                    (tile.x, tile.x + 1, tile.y, tile.y + 1, tile.z),
                ).fetchall()
                for old in known:
                    if old[0] not in present:
                        latest = self.db.execute(
                            "SELECT x,y,z,at FROM objects WHERE id=? ORDER BY at DESC LIMIT 1",
                            (old[0],),
                        ).fetchone()
                        if (
                            latest
                            and (int(latest["x"] // 1), int(latest["y"] // 1), latest["z"])
                            == (tile.x, tile.y, tile.z)
                            and latest["at"] <= at
                        ):
                            self._object(who, old[0], tile.x, tile.y, tile.z, at, 1, "{}")
                for obj in tile.objects:
                    self._object(
                        who,
                        obj.id,
                        obj.x,
                        obj.y,
                        obj.z,
                        at,
                        0,
                        encode(obj.model_dump(exclude_none=True)),
                    )
                coverage_changed |= self._coverage(who, tile.x // 32 * 32, tile.y // 32 * 32, 3)
            for cell in batch.coverage:
                coverage_changed |= self._coverage(who, cell.x, cell.y, cell.flags)
            for seen in batch.seen:
                self.db.execute(
                    "UPDATE objects SET at=? WHERE observer=? AND x>=? AND x<? AND y>=? AND y<? AND z=? AND at<? AND deleted=0",
                    (at, who, seen.x, seen.x + 1, seen.y, seen.y + 1, seen.z, at),
                )
                self.db.execute(
                    "UPDATE tiles SET at=? WHERE observer=? AND x=? AND y=? AND z=? AND at<?",
                    (at, who, seen.x, seen.y, seen.z, at),
                )
            for inspection in batch.inspections:
                # Inspection must refer to something this observer has actually recorded.
                known = self.db.execute(
                    "SELECT deleted FROM objects WHERE observer=? AND id=?", (who, inspection.id)
                ).fetchone()
                if known is None or known[0]:
                    continue
                self.db.execute(
                    """INSERT INTO inspections VALUES(?,?,?,?,?,?) ON CONFLICT(observer,id,category,slot)
                    DO UPDATE SET at=excluded.at,payload=excluded.payload WHERE excluded.at>=inspections.at""",
                    (
                        who,
                        inspection.id,
                        inspection.category,
                        inspection.slot,
                        at,
                        encode(inspection.model_dump()),
                    ),
                )
            if batch.kind == "heartbeat" and at >= self.meta("heartbeat_at", 0):
                self.db.execute("DELETE FROM players")
                for player in batch.players:
                    self.db.execute(
                        "INSERT INTO players VALUES(?,?,?)",
                        (player.name, encode(player.model_dump()), at),
                    )
                self.set_meta("heartbeat_at", at)
            if batch.kind == "markers" and at >= self.meta("marker_at:" + who, 0):
                self.set_meta("marker_at:" + who, at)
                if any(m.author != who for m in batch.markers):
                    raise ValueError("marker author does not match observer")
                self.db.execute(
                    "DELETE FROM markers WHERE json_extract(payload,'$.author')=?", (who,)
                )
                for marker in batch.markers:
                    self.db.execute(
                        "INSERT OR REPLACE INTO markers VALUES(?,?)",
                        (marker.id, encode(marker.model_dump())),
                    )
                authors = self.meta("live_marker_authors", [])
                self.set_meta("live_marker_authors", sorted(set(authors + [who])))
            if batch.bounds:
                bounds = batch.bounds.model_dump()
                if bounds["max_x"] < bounds["min_x"] or bounds["max_y"] < bounds["min_y"]:
                    raise ValueError("invalid world bounds")
                self.set_meta("bounds", bounds)
            self.set_meta("last_received_at", int(time.time() * 1000))
            if batch.tiles:
                self.set_meta("scene_revision", self.meta("scene_revision", 0) + 1)
            if coverage_changed:
                self.set_meta("coverage_revision", self.meta("coverage_revision", 0) + 1)
            self.touch()
        return True

    def _object(self, who, id, x, y, z, at, deleted, payload):
        reset = bool(deleted)
        if not deleted and id.startswith("tile:"):
            current = json.loads(payload)
            if current["kind"] in {"container", "furniture"}:
                old = self.db.execute(
                    "SELECT payload,at,deleted FROM objects WHERE observer=? AND id=?", (who, id)
                ).fetchone()
                if old and old["at"] <= at and not old["deleted"]:
                    previous = json.loads(old["payload"])
                    reset = (
                        previous.get("kind"),
                        previous.get("sprite"),
                        previous.get("label"),
                    ) != (
                        current.get("kind"),
                        current.get("sprite"),
                        current.get("label"),
                    )
        if reset:
            self.db.execute(
                """INSERT INTO object_resets VALUES(?,?,?) ON CONFLICT(observer,id)
                DO UPDATE SET at=excluded.at WHERE excluded.at>object_resets.at""",
                (who, id, at),
            )
        self.db.execute(
            """INSERT INTO objects VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(observer,id)
            DO UPDATE SET x=excluded.x,y=excluded.y,z=excluded.z,at=excluded.at,deleted=excluded.deleted,
            payload=excluded.payload WHERE excluded.at>=objects.at""",
            (who, id, x, y, z, at, deleted, payload),
        )

    def _coverage(self, who, x, y, flags):
        result = self.db.execute(
            """INSERT INTO coverage VALUES(?,?,?,?) ON CONFLICT(observer,x,y)
                           DO UPDATE SET flags=coverage.flags | excluded.flags
                           WHERE (coverage.flags | excluded.flags) != coverage.flags""",
            (who, x, y, flags),
        )
        return bool(result.rowcount)

    def import_coverage(self, who, cells):
        with self.lock, self.db:
            changed = False
            for x, y, flags in cells:
                changed |= self._coverage(who, x, y, flags)
            if changed:
                self.set_meta("coverage_revision", self.meta("coverage_revision", 0) + 1)
                self.touch()

    def replace_markers(self, markers, bump=True):
        with self.lock, self.db:
            authors = self.meta("live_marker_authors", [])
            existing = self.db.execute("SELECT id,payload FROM markers").fetchall()
            for row in existing:
                if json.loads(row["payload"])["author"] not in authors:
                    self.db.execute("DELETE FROM markers WHERE id=?", (row["id"],))
            for marker in markers:
                if marker["author"] not in authors:
                    self.db.execute(
                        "INSERT OR REPLACE INTO markers VALUES(?,?)", (marker["id"], encode(marker))
                    )
            if bump:
                self.touch()

    def _ranked(self, table, observer, partition):
        where, args = ("WHERE observer=?", [observer]) if observer else ("", [])
        return (
            f"WITH latest AS (SELECT *,ROW_NUMBER() OVER (PARTITION BY {partition} ORDER BY at DESC,observer ASC) AS rn FROM {table} {where}) ",
            args,
        )

    def objects(self, bounds, level, observer=None, limit=40_000):
        with self.lock:
            # Rank only IDs with a sighting inside this window, but consider all their
            # positions so a vehicle observed elsewhere cannot leave a ghost here.
            who_filter = " AND observer=?" if observer else ""
            args = list(bounds) + [level] + ([observer] if observer else [])
            sql = (
                """WITH candidates AS (
                SELECT DISTINCT id FROM objects WHERE x>=? AND y>=? AND x<? AND y<? AND z=?"""
                + who_filter
                + """),
                latest AS (SELECT *, ROW_NUMBER() OVER (PARTITION BY id ORDER BY at DESC,observer ASC) AS rn
                FROM objects WHERE id IN (SELECT id FROM candidates)"""
                + who_filter
                + ") "
            )
            if observer:
                args.append(observer)
            rows = self.db.execute(
                sql
                + "SELECT * FROM latest WHERE rn=1 AND deleted=0 AND x>=? AND y>=? AND x<? AND y<? AND z=? LIMIT ?",
                args + list(bounds) + [level, limit + 1],
            ).fetchall()
            return [
                {**json.loads(r["payload"]), "observed_at": r["at"], "observer": r["observer"]}
                for r in rows[:limit]
            ], len(rows) > limit

    def element(self, id, observer=None):
        with self.lock:
            sql, args = self._ranked("objects", observer, "id")
            row = self.db.execute(
                sql + "SELECT * FROM latest WHERE rn=1 AND id=? AND deleted=0", args + [id]
            ).fetchone()
            if not row:
                return None
            sql, args = self._ranked("inspections", observer, "id,category,slot")
            inspections = self.db.execute(
                sql + "SELECT * FROM latest WHERE rn=1 AND id=?", args + [id]
            ).fetchall()
            reset = (
                self.db.execute(
                    "SELECT MAX(at) FROM object_resets WHERE id=?"
                    + (" AND observer=?" if observer else ""),
                    [id] + ([observer] if observer else []),
                ).fetchone()[0]
                or 0
            )
            return {
                **json.loads(row["payload"]),
                "observed_at": row["at"],
                "observer": row["observer"],
                "inspections": [
                    {**json.loads(r["payload"]), "observed_at": r["at"], "observer": r["observer"]}
                    for r in inspections
                    if r["at"] >= reset
                ],
            }

    def coverage(self, observer=None):
        with self.lock:
            rows = self.db.execute(
                "SELECT x,y,flags FROM coverage" + (" WHERE observer=?" if observer else ""),
                [observer] if observer else [],
            ).fetchall()
            merged = {}
            for row in rows:
                key = (row["x"], row["y"])
                merged[key] = merged.get(key, 0) | row["flags"]
            return [[x, y, flags] for (x, y), flags in merged.items()]

    def status(self):
        with self.lock:
            now = int(time.time() * 1000)
            players = [
                {
                    **json.loads(r["payload"]),
                    "observed_at": r["at"],
                    "online": now - r["at"] < 15_000,
                }
                for r in self.db.execute("SELECT * FROM players")
            ]
            observers = [
                r[0]
                for r in self.db.execute(
                    "SELECT observer FROM coverage UNION SELECT observer FROM tiles ORDER BY observer"
                )
            ]
            heartbeat = self.meta("heartbeat_at", 0)
            return {
                "world": self.world,
                "revision": self.revision(),
                "scene_revision": self.meta("scene_revision", 0),
                "coverage_revision": self.meta("coverage_revision", 0),
                "online": bool(heartbeat and now - heartbeat < 15_000),
                "heartbeat_at": heartbeat or None,
                "players": players,
                "observers": observers,
                "bounds": self.meta("bounds"),
                "gap": self.meta("last_gap"),
                "last_received_at": self.meta("last_received_at"),
                "collector_error": self.meta("collector_error"),
                "observed_tiles": self.db.execute(
                    "SELECT COUNT(*) FROM (SELECT DISTINCT x,y,z FROM tiles)"
                ).fetchone()[0],
                "markers": [
                    json.loads(r[0]) for r in self.db.execute("SELECT payload FROM markers")
                ],
            }
