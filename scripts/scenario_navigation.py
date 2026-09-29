"""Versioned offline navigation tiles derived from installed original map data.

Each tile is two bytes: surface cost (0 blocked, 10 asphalt), then a conservative
clearance in quarter tiles. The latter is distance to the nearest blocked tile
boundary under the chessboard metric, which never overstates Euclidean clearance.
The worker still uses the prebuilt protobuf graph; this grid accelerates baking
and offline route validation, with live occupancy supplied by the game later.
"""

from __future__ import annotations

from collections import Counter
from functools import lru_cache
import hashlib
import json
import math
from pathlib import Path
import struct
import time
import statistics

from scenario_roads import ROOT, RoadSurface, corridor_cells, explicit_road_floor
from observer.roads import cells_on_line

SCHEMA = 1
CELL = 256
MAGIC = b"LFNAV01\0"
HEADER = struct.Struct("<8sIiiI")
PAYLOAD_SIZE = CELL * CELL * 2
PROFILE = {
    "surface_cost": {"blocked": 0, "asphalt": 10},
    "floor_materials": ["Road_06"],
    "clearance_unit_tiles": 0.25,
    "clearance_metric": "conservative_chessboard_to_blocked_tile_boundary",
    "edge_scoring": "length * mean(surface_cost/10 * (1 + 0.15/(1 + max(0, clearance-radius))))",
    "outside_bounds": "blocked",
    "dynamic_obstacles_included": False,
}


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def clearance_scores(costs, width, height):
    """Two-pass eight-neighbor distance transform, with a blocked outer frame."""
    if width <= 0 or height <= 0 or len(costs) != width * height:
        raise ValueError("Invalid navigation raster dimensions")
    stride = width + 2
    distance = bytearray((width + 2) * (height + 2))
    for y in range(height):
        begin = (y + 1) * stride + 1
        distance[begin : begin + width] = bytes(
            255 if c else 0 for c in costs[y * width : (y + 1) * width]
        )
    for y in range(1, height + 1):
        for x in range(1, width + 1):
            i = y * stride + x
            if distance[i]:
                distance[i] = min(
                    distance[i],
                    distance[i - 1] + 1,
                    distance[i - stride - 1] + 1,
                    distance[i - stride] + 1,
                    distance[i - stride + 1] + 1,
                )
    for y in range(height, 0, -1):
        for x in range(width, 0, -1):
            i = y * stride + x
            if distance[i]:
                distance[i] = min(
                    distance[i],
                    distance[i + 1] + 1,
                    distance[i + stride - 1] + 1,
                    distance[i + stride] + 1,
                    distance[i + stride + 1] + 1,
                )
    result = bytearray(width * height)
    for y in range(height):
        for x in range(width):
            d = distance[(y + 1) * stride + x + 1]
            if d:
                result[y * width + x] = min(255, 4 * d - 2)
    return result


def source_identity(map_root, definitions, bounds, supplemental_definitions=()):
    """Hash inputs once per offline session, never during an edge query."""
    x0, y0, x1, y1 = bounds
    paths = [map_root / "worldmap.xml.bin", definitions, definitions.with_suffix("")]
    for source in supplemental_definitions:
        paths.extend([Path(source), Path(source).with_suffix("")])
    for cx in range(x0 // CELL, (x1 - 1) // CELL + 1):
        for cy in range(y0 // CELL, (y1 - 1) // CELL + 1):
            paths.extend([map_root / f"{cx}_{cy}.lotheader", map_root / f"world_{cx}_{cy}.lotpack"])
    for relative in (
        "scripts/scenario_navigation.py",
        "scripts/scenario_roads.py",
        "observer/observer/roads.py",
        "observer/observer/terrain.py",
    ):
        paths.append(ROOT / relative)
    sources = {str(path.resolve()): digest(path) if path.is_file() else None for path in paths}
    identity = {"schema": SCHEMA, "bounds": list(bounds), "profile": PROFILE, "sources": sources}
    token = hashlib.sha256(
        json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    return token, identity


class NavigationGrid:
    """Read-only bounded cache of independently hashed navigation chunks."""

    def __init__(self, root, manifest):
        self.root, self.manifest = Path(root), manifest
        if manifest.get("schema") != SCHEMA or manifest.get("profile") != PROFILE:
            raise ValueError("Unsupported navigation schema/profile")
        self.bounds = tuple(manifest["bounds"])
        self.chunks = {(c["x"], c["y"]): c for c in manifest["chunks"]}

    @lru_cache(maxsize=32)
    def chunk(self, cx, cy):
        info = self.chunks.get((cx, cy))
        if info is None:
            return bytes(PAYLOAD_SIZE)
        path = (self.root / info["file"]).resolve()
        if not path.is_relative_to(self.root.resolve()):
            raise ValueError("Navigation chunk outside cache")
        data = path.read_bytes()
        if (
            len(data) != HEADER.size + PAYLOAD_SIZE
            or hashlib.sha256(data).hexdigest() != info["sha256"]
        ):
            raise ValueError("Navigation chunk checksum/size mismatch")
        magic, schema, x, y, size = HEADER.unpack_from(data)
        if (magic, schema, x, y, size) != (MAGIC, SCHEMA, cx, cy, PAYLOAD_SIZE):
            raise ValueError("Navigation chunk header mismatch")
        return data[HEADER.size :]

    def sample(self, x, y):
        x0, y0, x1, y1 = self.bounds
        if not x0 <= x < x1 or not y0 <= y < y1:
            return 0, 0
        data = self.chunk(x // CELL, y // CELL)
        offset = (y % CELL * CELL + x % CELL) * 2
        return data[offset], data[offset + 1]

    def cost(self, x, y):
        return self.sample(x, y)[0]

    def center_clear(self, x, y, radius=2.25, step=0.25):
        cost, quarters = self.sample(x, y)
        return bool(cost) and quarters / 4 >= radius + step / 2

    def segment_clear(self, a, b, radius=2.25, step=0.25):
        return all(self.cost(x, y) for x, y in corridor_cells([a, b], radius, step))

    def edge_cost(self, a, b, radius=2.25, step=0.25):
        """Hard clearance first; then a <=15% preference for roomier surfaces."""
        if not self.segment_clear(a, b, radius, step):
            return None
        values = []
        for x, y in cells_on_line(a, b):
            surface, quarters = self.sample(x, y)
            values.append(surface / 10 * (1 + 0.15 / (1 + max(0, quarters / 4 - radius))))
        return math.dist(a, b) * sum(values) / len(values)


def write_grid(root, bounds, costs, scores, identity, token):
    root = Path(root)
    x0, y0, x1, y1 = bounds
    width, height = x1 - x0, y1 - y0
    if len(costs) != width * height or len(scores) != len(costs):
        raise ValueError("Grid payload dimensions do not match bounds")
    directory = root / token[:16]
    directory.mkdir(parents=True, exist_ok=True)
    chunks = []
    for cx in range(x0 // CELL, (x1 - 1) // CELL + 1):
        for cy in range(y0 // CELL, (y1 - 1) // CELL + 1):
            payload = bytearray(PAYLOAD_SIZE)
            for y in range(max(y0, cy * CELL), min(y1, (cy + 1) * CELL)):
                for x in range(max(x0, cx * CELL), min(x1, (cx + 1) * CELL)):
                    src, dst = (y - y0) * width + x - x0, ((y % CELL) * CELL + x % CELL) * 2
                    payload[dst], payload[dst + 1] = costs[src], scores[src]
            data = HEADER.pack(MAGIC, SCHEMA, cx, cy, PAYLOAD_SIZE) + payload
            path = directory / f"{cx}_{cy}.nav"
            temporary = path.with_suffix(".pending")
            temporary.write_bytes(data)
            temporary.replace(path)
            chunks.append(
                {
                    "x": cx,
                    "y": cy,
                    "file": str(path.relative_to(root)),
                    "sha256": hashlib.sha256(data).hexdigest(),
                    "bytes": len(data),
                }
            )
    manifest = {
        **identity,
        "identity": token,
        "chunks": chunks,
        "tiles": width * height,
        "passable_tiles": sum(bool(c) for c in costs),
        "cost_histogram": dict(sorted(Counter(costs).items())),
        "bytes": sum(c["bytes"] for c in chunks),
        "limitations": [
            "Static installed-map surfaces only; saved/player-built obstacles require live overlays.",
            "Conservative road material allowlist; no lane, direction, speed or intersection rules.",
            "Clearance scores do not prove vehicle steering or dynamic turn feasibility.",
        ],
    }
    temporary = root / "manifest.pending"
    temporary.write_text(json.dumps(manifest, indent=2) + "\n")
    temporary.replace(root / "manifest.json")
    return NavigationGrid(root, manifest)


def bake_navigation(map_root, definitions, bounds, output, road_cache):
    bounds = tuple(bounds)
    x0, y0, x1, y1 = bounds
    width, height = x1 - x0, y1 - y0
    if width <= 0 or height <= 0 or width * height > 4_000_000:
        raise ValueError("Offline navigation region exceeds four million tiles")
    started = time.perf_counter()
    token, identity = source_identity(Path(map_root), Path(definitions), bounds)
    hashed = time.perf_counter()
    output = Path(output)
    manifest_path = output / "manifest.json"
    if manifest_path.is_file():
        old = json.loads(manifest_path.read_text())
        if old.get("identity") == token:
            grid = NavigationGrid(output, old)
            for cx, cy in grid.chunks:
                grid.chunk(cx, cy)  # Validate once on cache open, not per path query.
            return grid, {
                "cache_hit": True,
                "source_hash_ms": (hashed - started) * 1000,
                "total_ms": (time.perf_counter() - started) * 1000,
            }
    # The Observer raster's own key uses file size/mtime. A navigation rebake
    # must also invalidate that prerequisite when content changes with those
    # attributes preserved, or when its classification code changes.
    surface = RoadSurface(Path(map_root), Path(road_cache) / token, Path(definitions))
    costs = bytearray(width * height)
    for cx in range(x0 // CELL, (x1 - 1) // CELL + 1):
        for cy in range(y0 // CELL, (y1 - 1) // CELL + 1):
            mask = surface.roads.image(cx, cy)
            for local, eligible in enumerate(mask):
                if not eligible:
                    continue
                x, y = cx * CELL + local % CELL, cy * CELL + local // CELL
                if x0 <= x < x1 and y0 <= y < y1 and surface.asphalt(x, y):
                    costs[(y - y0) * width + x - x0] = 10
    parsed = time.perf_counter()
    scores = clearance_scores(costs, width, height)
    transformed = time.perf_counter()
    grid = write_grid(output, bounds, costs, scores, identity, token)
    return grid, {
        "cache_hit": False,
        "source_hash_ms": (hashed - started) * 1000,
        "parse_ms": (parsed - hashed) * 1000,
        "clearance_ms": (transformed - parsed) * 1000,
        "write_ms": (time.perf_counter() - transformed) * 1000,
        "total_ms": (time.perf_counter() - started) * 1000,
    }


def benchmark_queries(grid, map_root, definitions, road_cache, points, repeats=30):
    """Compare preprocessing only; native per-resident A* is not measured here."""
    cells = corridor_cells(points)

    def timing(function):
        samples = []
        for _ in range(repeats):
            started = time.perf_counter()
            function()
            samples.append((time.perf_counter() - started) * 1000)
        return {
            "p50_ms": statistics.median(samples),
            "p95_ms": sorted(samples)[math.ceil(repeats * 0.95) - 1],
        }

    started = time.perf_counter()
    surface = RoadSurface(
        Path(map_root), Path(road_cache) / grid.manifest["identity"], Path(definitions)
    )
    direct = [surface.asphalt(x, y) for x, y in cells]
    first_source_ms = (time.perf_counter() - started) * 1000
    baked = [bool(grid.cost(x, y)) for x, y in cells]
    if direct != baked:
        raise ValueError("Baked/direct classification mismatch")
    return {
        "scope": "offline preprocessing queries; no per-NPC planner latency measurement",
        "tiles": len(cells),
        "segments": len(points) - 1,
        "repetitions": repeats,
        "first_source_parse_and_route_ms": first_source_ms,
        "source_floor_reclassification": timing(
            lambda: [
                surface.road(x, y) and explicit_road_floor(surface.names(x, y), surface.properties)
                for x, y in cells
            ]
        ),
        "source_already_memoized_lookup": timing(lambda: [surface.asphalt(x, y) for x, y in cells]),
        "baked_tile_lookup": timing(lambda: [grid.cost(x, y) for x, y in cells]),
        "baked_full_corridor_validation": timing(
            lambda: [grid.segment_clear(a, b) for a, b in zip(points, points[1:])]
        ),
        "notes": [
            "OS page cache and existing broad Observer road raster may be warm.",
            "Source memoization can beat binary lookup once parsed; baking avoids repeated raw floor parsing across processes.",
            "Geometry generation is included only in full corridor validation; other rows use the same precomputed tile list.",
        ],
    }
