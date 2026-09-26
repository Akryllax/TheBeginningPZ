import hashlib
import json
import logging
import time
from pathlib import Path

from .formats import decode_coverage, decode_public_markers, stable_read
from .models import Batch, Bounds
from .store import Store

log = logging.getLogger(__name__)
MAX_LINE = 262_144


class Collector:
    def __init__(self, store: Store, spool: Path | None, save: Path | None):
        self.store, self.spool, self.save = store, spool, save
        self.imported = {}
        self.fingerprints = {}

    def error(self, message):
        log.warning("%s", message)
        with self.store.lock, self.store.db:
            self.store.set_meta("collector_error", {"message": message, "at": int(time.time() * 1000)})

    def poll(self):
        if self.spool and self.spool.exists():
            # Rotated files contain a header with their first sequence; process oldest first.
            paths = []
            for path in self.spool.glob("observer-*.txt"):
                try:
                    with path.open("rb") as f:
                        first = f.readline(MAX_LINE)
                    header = json.loads(first)
                    paths.append((header.get("created_at", 0), header.get("sequence", 0), path))
                except (OSError, ValueError):
                    continue
            for _, __, path in sorted(paths):
                try:
                    self.read_journal(path)
                except (OSError, ValueError) as exc:
                    self.error(f"{path.name}: {exc}")
        if self.save and self.save.exists():
            try:
                self.read_saves()
            except (OSError, ValueError, KeyError) as exc:
                self.error(f"save import: {exc}")

    def read_journal(self, path):
        with path.open("rb") as f:
            header_line = f.readline(MAX_LINE)
            if not header_line.endswith(b"\n"):
                return
            header = json.loads(header_line)
            if header.get("format") != "zomboid-observer-journal-v1":
                raise ValueError("unsupported journal header")
            identity = hashlib.sha256(header_line).hexdigest()
            with self.store.lock:
                cursor = self.store.db.execute("SELECT * FROM cursors WHERE path=?", (str(path),)).fetchone()
            offset = cursor["offset"] if cursor and cursor["identity"] == identity else len(header_line)
            if path.stat().st_size < offset:
                offset = len(header_line)
            f.seek(offset)
            for _ in range(256):
                start = f.tell()
                line = f.readline(MAX_LINE + 1)
                if not line or not line.endswith(b"\n"):
                    if len(line) > MAX_LINE:
                        raise ValueError("journal line exceeds size limit")
                    break
                try:
                    self.store.ingest(Batch.model_validate_json(line))
                except ValueError as exc:
                    self.error(f"rejected observation at byte {start}: {str(exc)[:180]}")
                # Committing the cursor after the observation permits safe replay after a crash.
                with self.store.lock, self.store.db:
                    self.store.db.execute(
                        "INSERT OR REPLACE INTO cursors VALUES(?,?,?)", (str(path), identity, f.tell())
                    )

    def read_saves(self):
        metadata = self.store.meta("bounds")
        if metadata:
            bounds = Bounds.model_validate(metadata)
            for path in sorted((self.save / "map_visited_server").glob("*.zip")):
                stat = path.stat()
                fingerprint = (stat.st_ino, stat.st_size, stat.st_mtime_ns)
                if self.fingerprints.get(str(path)) == fingerprint:
                    continue
                data = stable_read(path)
                digest = hashlib.sha256(data).hexdigest()
                if self.imported.get(str(path)) == digest:
                    continue
                cells = list(decode_coverage(data, path.stem, bounds))
                self.store.import_coverage(path.stem, cells)
                self.imported[str(path)] = digest
                self.fingerprints[str(path)] = fingerprint
        markers = self.save / "servermap_symbols.bin"
        # Once the live exporter supplies marker snapshots, older save files must not
        # resurrect a marker whose sharing was revoked since the last game save.
        if markers.exists():
            data = stable_read(markers)
            digest = hashlib.sha256(data).hexdigest()
            if self.imported.get(str(markers)) != digest:
                self.store.replace_markers(decode_public_markers(data))
                self.imported[str(markers)] = digest
