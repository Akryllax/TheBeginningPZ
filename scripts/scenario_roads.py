"""Read-only road-surface evidence and bounded vehicle-corridor geometry.

The Observer raster is a navigation hint: PAVEMENT includes sidewalks. Vehicle
probes additionally require explicit installed floor materials and room/collision
checks on the live server. This module never reads or writes a world save.
"""
from __future__ import annotations

from functools import lru_cache
import math
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAX_CORRIDOR_SAMPLES = 32768
sys.path.insert(0, str(ROOT / "observer"))
from observer.roads import Roads
from observer.terrain import Binary, read_header


def probe_waypoints(rows):
    """Match the disposable runtime's ground-only bounded route contract."""
    if not isinstance(rows, list) or not 2 <= len(rows) <= 16:
        raise ValueError('Probe route needs 2..16 waypoints')
    points = []
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError('Waypoints must be x/y objects')
        z = row.get('z', 0)
        if isinstance(z, bool) or not isinstance(z, (float, int)) or z != 0:
            raise ValueError('Probe route must remain at ground level')
        point = tuple(row.get(k) for k in ('x', 'y'))
        if any(isinstance(v, bool) or not isinstance(v, (float, int)) or
               not math.isfinite(v) or not -20000 <= v <= 60000 for v in point):
            raise ValueError('Waypoints must contain bounded finite x/y coordinates')
        points.append(point)
    distances = [math.dist(a, b) for a, b in zip(points, points[1:])]
    if any(not 0.5 <= d <= 40 for d in distances) or not 2 <= sum(distances) <= 60:
        raise ValueError('Probe route needs segment length 0.5..40 and total length 2..60 tiles')
    return points


def tile_properties(text: str) -> dict[str, dict[str, str]]:
    """Read the installed text companion to tile definitions, retaining flags."""
    result = {}
    for match in re.finditer(r"// ([A-Za-z0-9_]+)\s*\n\s*tile\s*\{([^{}]*)\}", text):
        result[match[1]] = {
            key.strip(): value.strip()
            for key, value in re.findall(r"^\s*([A-Za-z0-9_]+)\s*=([^\n]*)", match[2], re.M)
        }
    if not result:
        raise ValueError("No tile definitions found")
    return result


def ground_stacks(data: bytes, header: dict) -> dict[tuple[int, int], tuple[str, ...]]:
    """Decode ground-level stacks with the same B42 chunk ordering as IsoLot."""
    b = Binary(data)
    version = 0
    if data[:4] == b"LOTP":
        b.take(4)
        version = b.integer()
        if version != 1:
            raise ValueError("Unsupported lotpack version")
    table = (8 if version else 0) + 4
    stacks = {}
    for lx in range(32):
        for ly in range(32):
            b.pos = table + (lx * 32 + ly) * 8
            pos = b.integer()
            if pos <= 0:
                continue
            b.pos, skip = pos, 0
            for z in range(max(-32, header["min_z"]), min(0, header["max_z"]) + 1):
                for x in range(8):
                    for y in range(8):
                        if skip:
                            skip -= 1
                            continue
                        count = b.integer()
                        if count == -1:
                            skip = b.count(65536)
                            if skip:
                                skip -= 1
                                continue
                        if count <= 1:
                            continue
                        if count > 1024:
                            raise ValueError("Invalid terrain stack")
                        b.integer()  # Room index; live room/outdoor checks remain mandatory.
                        refs = [b.integer() for _ in range(count - 1)]
                        if any(i < 0 or i >= len(header["names"]) for i in refs):
                            raise ValueError("Invalid tile reference")
                        if z == 0:
                            stacks[lx * 8 + x, ly * 8 + y] = tuple(header["names"][i] for i in refs)
    return stacks


def physical_tile_obstacle(name, props):
    """Reject physical objects even when their square is walkable by a person.

    Only the built-in floor shape is a planning exemption. Custom meshes and
    unknown/empty declarations require clearance rather than guessed geometry.
    Native physics is still required to handle unplanned contact correctly.
    """
    if "PhysicsMesh" in props or "StopCar" in props or "HitByCar" in props:
        return True
    if "PhysicsShape" in props:
        return props["PhysicsShape"] != "Floor"
    return props.get("MoveType") != "WallObject" and (
        "lighting_outdoor_" in name or name in {
            "recreational_sports_01_19", "recreational_sports_01_21", "recreational_sports_01_32"})


def explicit_road_floor(names, properties, materials=frozenset({"Road_06"})):
    """Reject physical objects and curb overlays above an otherwise valid floor."""
    floors = []
    for name in names:
        props = properties.get(name)
        if props is None:
            return False
        if physical_tile_obstacle(name, props):
            return False
        if any(flag in props for flag in ("solid", "solidtrans", "collideN", "collideW")):
            return False
        if name.startswith("street_curbs_"):
            return False
        material = props.get("FloorMaterial")
        if material and material not in materials:
            return False
        if "solidfloor" in props:
            if material not in materials:
                return False
            floors.append(name)
    return bool(floors)


class RoadSurface:
    def __init__(self, map_root: Path, cache: Path, definitions: Path, supplemental_definitions=()):
        self.root, self.definitions = Path(map_root), Path(definitions)
        self.properties = tile_properties(self.definitions.read_text())
        self.ambiguous_definitions = set()
        for source in supplemental_definitions:
            additions = tile_properties(Path(source).read_text())
            for name, properties in additions.items():
                if name in self.ambiguous_definitions:
                    continue
                if name in self.properties and self.properties[name] != properties:
                    # Do not guess the engine's override order. A used ambiguous
                    # tile remains unknown and fails the normal surface checks.
                    self.ambiguous_definitions.add(name)
                    del self.properties[name]
                else:
                    self.properties[name] = properties
        self.roads = Roads(self.root, cache)

    @lru_cache(maxsize=32)
    def cell(self, cx, cy):
        header = read_header((self.root / f"{cx}_{cy}.lotheader").read_bytes(), cx, cy)
        return ground_stacks((self.root / f"world_{cx}_{cy}.lotpack").read_bytes(), header)

    def names(self, x, y):
        return self.cell(x // 256, y // 256).get((x % 256, y % 256), ())

    def road(self, x, y):
        return bool(self.roads.image(x // 256, y // 256)[y % 256 * 256 + x % 256])

    @lru_cache(maxsize=262144)
    def asphalt(self, x, y):
        return self.road(x, y) and explicit_road_floor(self.names(x, y), self.properties)


def disk_cells(point, radius):
    """Every tile intersecting a disk, including boundary contacts."""
    x, y = point
    result = []
    for tx in range(math.floor(x - radius), math.floor(x + radius) + 1):
        for ty in range(math.floor(y - radius), math.floor(y + radius) + 1):
            dx, dy = max(tx - x, 0, x - tx - 1), max(ty - y, 0, y - ty - 1)
            if dx * dx + dy * dy <= radius * radius:
                result.append((tx, ty))
    return result


def corridor_cells(points, radius=2.25, step=0.25):
    """Conservative swept disk: includes every intersected tile, not just centers.

    A disk containing the entire vehicle footprint is invariant under heading.
    Samples expand by half a step so gaps between samples cannot hide a tile.
    """
    if (not 2 <= len(points) <= 128 or not math.isfinite(radius) or not math.isfinite(step)
            or not 0 < radius <= 8 or not 0 < step <= 1):
        raise ValueError("Invalid bounded corridor")
    if any(len(p) != 2 or any(not math.isfinite(v) for v in p) for p in points):
        raise ValueError("Invalid route point")
    distances = [math.dist(a, b) for a, b in zip(points, points[1:])]
    total = sum(distances)
    if not 0 < total <= 1024:
        raise ValueError("Invalid route length")
    counts, total_samples = [], 0
    for distance in distances:
        ratio = distance / step
        if not math.isfinite(ratio) or ratio > MAX_CORRIDOR_SAMPLES:
            raise ValueError("Corridor sample budget exceeded")
        count = max(1, math.ceil(ratio))
        total_samples += count + 1
        if total_samples > MAX_CORRIDOR_SAMPLES:
            raise ValueError("Corridor sample budget exceeded")
        counts.append(count)
    cells = set()
    pad = radius + step / 2
    for a, b, count in zip(points, points[1:], counts):
        for i in range(count + 1):
            x, y = (a[j] + (b[j] - a[j]) * i / count for j in range(2))
            cells.update(disk_cells((x, y), pad))
    return sorted(cells)
