"""Read-only Build 42 death logs, with durable history in Observer's own DB.

The server's user log also records some animals as the IsoPlayer default name
"Bob". Only names present in networkPlayers may become public death markers.
Logs, rather than periodic player positions, preserve deaths across respawns.
The deployed game writes UTC timestamps; no game settings are changed here.
"""

import hashlib
import json
import re
import sqlite3
import time
from contextlib import closing
from datetime import UTC, datetime

STAMP = r"\[(\d{2}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\]"
COORDS = r"\((-?\d{1,6}),(-?\d{1,6}),(-?\d{1,3})\)"
USER_DEATH = re.compile(STAMP + r" user (.{1,128}?) died at " + COORDS + r" \(non pvp\)\.?")
PVP_DEATH = re.compile(
    STAMP
    + r'\[IMPORTANT\] Kill: "[^"\r\n]{1,128}" '
    + r"\(-?\d{1,6},-?\d{1,6},-?\d{1,3}\)"
    + r' killed "([^"\r\n]{1,128})" '
    + COORDS
    + r"\.?"
)
MAX_READ = 4 * 1024 * 1024


def parse_death(line, kind):
    match = (USER_DEATH if kind == "user" else PVP_DEATH).fullmatch(line.rstrip("\r\n"))
    if not match:
        return None
    stamp, name, x, y, z = match.groups()
    if not name.strip() or any(ord(c) < 32 or ord(c) == 127 for c in name):
        return None
    x, y, z = int(x), int(y), int(z)
    if abs(x) >= 200000 or abs(y) >= 200000 or not -32 <= z <= 64:
        return None
    try:
        occurred = datetime.strptime(stamp, "%d-%m-%y %H:%M:%S.%f").replace(tzinfo=UTC)
    except ValueError:
        return None
    event = {"name": name, "x": x, "y": y, "z": z, "occurred_at": int(occurred.timestamp() * 1000)}
    event["id"] = hashlib.sha256(json.dumps(event, sort_keys=True).encode()).hexdigest()
    return event


def registered_players(path):
    with closing(sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True, timeout=0.1)) as db:
        db.execute("PRAGMA query_only=ON")
        # Include deceased characters. A fast respawn can reuse the same row.
        return {r[0] for r in db.execute("SELECT DISTINCT username FROM networkPlayers") if r[0]}


class DeathLog:
    def __init__(self, db, logs):
        self.db, self.logs = db, logs
        with db:
            db.execute(
                "CREATE TABLE IF NOT EXISTS death_events("
                "id TEXT PRIMARY KEY,name TEXT,x INTEGER,y INTEGER,z INTEGER,occurred_at INTEGER)"
            )
            db.execute("CREATE TABLE IF NOT EXISTS death_players(name TEXT PRIMARY KEY)")
            db.execute(
                "CREATE TABLE IF NOT EXISTS death_cursors(name TEXT PRIMARY KEY,offset INTEGER,anchor BLOB)"
            )
            db.execute(
                "INSERT OR IGNORE INTO metadata VALUES('death_capture_started',?)", (int(time.time() * 1000),)
            )
        self.since = int(
            db.execute("SELECT value FROM metadata WHERE key='death_capture_started'").fetchone()[0]
        )

    def scan(self, path):
        # Logger filenames survive moves into logs_YYYY-MM-DD archive folders.
        cursor = self.db.execute(
            "SELECT offset,anchor FROM death_cursors WHERE name=?", (path.name,)
        ).fetchone()
        offset, anchor = cursor or (0, hashlib.sha256(b"").digest())
        reset = False
        with path.open("rb") as file:
            file.seek(0, 2)
            size = file.tell()
            if offset <= size:
                file.seek(max(0, offset - 64))
            if offset > size or hashlib.sha256(file.read(min(64, offset))).digest() != anchor:
                offset = 0  # Truncation/replacement; event IDs prevent duplicates.
                reset = True
            file.seek(offset)
            chunk = file.read(MAX_READ)
            end = chunk.rfind(b"\n") + 1
            if not end and len(chunk) == MAX_READ:
                raise ValueError("Death log contains an oversized unfinished line")
            # A writer may be halfway through a line. Retry its bytes next poll.
            for raw in chunk[:end].splitlines():
                if len(raw) > 4096:
                    continue
                try:
                    event = parse_death(
                        raw.decode("utf-8"), "user" if path.name.endswith("_user.txt") else "pvp"
                    )
                except UnicodeDecodeError:
                    continue
                if event and event["occurred_at"] >= self.since:
                    self.db.execute(
                        "INSERT OR IGNORE INTO death_events VALUES(:id,:name,:x,:y,:z,:occurred_at)", event
                    )
            position = offset + end
            if reset or not cursor or position != cursor[0]:
                file.seek(max(0, position - 64))
                anchor = hashlib.sha256(file.read(min(64, position))).digest()
                self.db.execute(
                    "INSERT OR REPLACE INTO death_cursors VALUES(?,?,?)", (path.name, position, anchor)
                )

    def poll(self, names):
        # Caller holds the SavedMap lock/transaction: events and cursors commit
        # together. Raw logs (connections, Steam IDs, combat) are never persisted.
        if not self.logs.is_dir():
            raise FileNotFoundError("Death log directory is unavailable")
        paths = sorted(p for p in self.logs.rglob("*.txt") if p.name.endswith(("_user.txt", "_pvp.txt")))
        if not paths:
            raise ValueError("No user or PvP logs found")
        self.db.executemany("INSERT OR IGNORE INTO death_players VALUES(?)", [(name,) for name in names])
        for path in paths:
            self.scan(path)
        # Candidate events stay private until the username is known to the save,
        # even if the first player save arrives after the death log entry.
        rows = self.db.execute(
            "SELECT id,name,x,y,z,occurred_at FROM death_events JOIN death_players USING(name) "
            "ORDER BY occurred_at DESC,id"
        ).fetchall()
        events = [dict(zip(("id", "name", "x", "y", "z", "occurred_at"), row)) for row in rows]
        return events, max(p.stat().st_mtime_ns for p in paths) // 1000000
