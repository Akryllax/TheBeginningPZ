"""Bounded A* on the installed B42 roads. No save writes or game integration."""

import hashlib
import heapq
import math
import os
import struct
import time
from collections import OrderedDict, defaultdict
from pathlib import Path
from uuid import uuid4

from PIL import Image, ImageDraw

from .terrain import DIRT, FLOOR, PALETTE, PAVEMENT, ground_image, read_header

ROAD_VERSION = 1
MAX_STOPS = 256
MAX_EXPANSIONS = 500_000
SEARCH_SECONDS = 5


class RouteFailure(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


def cells_on_line(a, b, size=1):
    """Supercover traversal: include both sides when crossing an exact corner."""
    x, y = math.floor(a[0] / size), math.floor(a[1] / size)
    end = math.floor(b[0] / size), math.floor(b[1] / size)
    yield x, y
    dx, dy = b[0] - a[0], b[1] - a[1]
    sx, sy = (1 if dx > 0 else -1), (1 if dy > 0 else -1)
    tx = (((x + (dx > 0)) * size - a[0]) / dx) if dx else math.inf
    ty = (((y + (dy > 0)) * size - a[1]) / dy) if dy else math.inf
    stepx, stepy = abs(size / dx) if dx else math.inf, abs(size / dy) if dy else math.inf
    # End coordinates are finite and bounded by API validation.
    while (x, y) != end:
        # At t=1 a negative-going axis already occupies floor(endpoint), while
        # a positive-going axis may still need its last step. Do not overshoot
        # the negative axis at an integer endpoint and march away indefinitely.
        if min(tx, ty) >= 1 - 1e-12:
            if x != end[0] and y != end[1]:
                yield end[0], y
                yield x, end[1]
            yield end
            break
        if abs(tx - ty) < 1e-10:
            yield x + sx, y
            yield x, y + sy
            x, y, tx, ty = x + sx, y + sy, tx + stepx, ty + stepy
        elif tx < ty:
            x, tx = x + sx, tx + stepx
        else:
            y, ty = y + sy, ty + stepy
        yield x, y


def read_roads(path):
    """WorldMapBinary v2: little-endian, 256-square cells, indexed UTF-8 strings."""
    data = path.read_bytes()
    if len(data) > 32 * 1024 * 1024 or data[:4] != b"IGMB":
        raise ValueError("Unsupported installed road map")
    offset = 4

    def read(fmt):
        nonlocal offset
        result = struct.unpack_from("<" + fmt, data, offset)
        offset += struct.calcsize("<" + fmt)
        return result[0] if len(result) == 1 else result

    version, size, width, height = read("4i")
    if version != 2 or size != 256 or not 0 < width * height <= 100_000:
        raise ValueError("Road routing requires the current 256-tile B42 binary map")
    strings = []
    count = read("i")
    if not 0 <= count <= 32767:
        raise ValueError("Invalid map string table")
    for _ in range(count):
        length = read("h")
        if not 0 <= length <= 4096 or offset + length > len(data):
            raise ValueError("Invalid map string")
        strings.append(data[offset : offset + length].decode("utf-8"))
        offset += length

    def text():
        index = read("h")
        if not 0 <= index < len(strings):
            raise ValueError("Invalid map string index")
        return strings[index]

    cells = defaultdict(list)
    seen = set()
    for _ in range(width * height):
        cx = read("i")
        if cx == -1:
            continue
        cy, count = read("2i")
        if abs(cx) > 800 or abs(cy) > 800 or not 0 <= count <= 100_000:
            raise ValueError("Invalid map cell")
        for _ in range(count):
            kind, rings = text(), []
            for _ in range(read("B")):
                length = read("h")
                if not 0 <= length <= 32767:
                    raise ValueError("Invalid road geometry")
                rings.append(
                    tuple(
                        (cx * 256 + x, cy * 256 + y) for x, y in (read("2h") for _ in range(length))
                    )
                )
            props = {}
            for _ in range(read("B")):
                key = text()
                props[key] = text()
            if props.get("highway") not in ("primary", "secondary", "tertiary"):
                continue
            if kind not in ("Polygon", "LineString") or not rings or not rings[0]:
                continue
            # Conversion into 256-tile cells can duplicate complete geometries.
            feature = kind, tuple(rings), max(1, min(32, int(props.get("width", "1"))))
            if feature in seen:
                continue
            seen.add(feature)
            points = [p for ring in rings for p in ring]
            x0, x1 = min(p[0] for p in points), max(p[0] for p in points)
            y0, y1 = min(p[1] for p in points), max(p[1] for p in points)
            pad = feature[2] if kind == "LineString" else 0
            for x in range((x0 - pad) // 256, (x1 + pad) // 256 + 1):
                for y in range((y0 - pad) // 256, (y1 + pad) // 256 + 1):
                    cells[x, y].append(feature)
    if offset != len(data):
        raise ValueError("Unexpected trailing road map data")
    return cells, hashlib.sha256(data).hexdigest()[:16]


class Roads:
    def __init__(self, root, cache):
        self.root, self.cache = Path(root), Path(cache)
        self.features, self.revision = read_roads(self.root / "worldmap.xml.bin")
        self.images = OrderedDict()
        self.cache.mkdir(parents=True, exist_ok=True)
        self.build_time = 0.0

    def image(self, cx, cy):
        key = cx, cy
        if key in self.images:
            self.images.move_to_end(key)
            return self.images[key]
        if key not in self.features:
            return bytes(65536)
        started = time.monotonic()
        header = self.root / f"{cx}_{cy}.lotheader"
        pack = self.root / f"world_{cx}_{cy}.lotpack"
        try:
            identity = [(p.stat().st_size, p.stat().st_mtime_ns) for p in (header, pack)]
            digest = hashlib.sha256(
                repr((ROAD_VERSION, self.revision, identity)).encode()
            ).hexdigest()[:16]
            target = self.cache / f"{cx}_{cy}_{digest}.bin"
            if target.exists() and target.stat().st_size == 65536:
                result = target.read_bytes()
            else:
                mask = Image.new("1", (256, 256))
                draw = ImageDraw.Draw(mask)
                for kind, rings, width in self.features[key]:
                    shape = Image.new("1", (256, 256))
                    shape_draw = ImageDraw.Draw(shape)
                    for i, ring in enumerate(rings):
                        points = [(x - cx * 256, y - cy * 256) for x, y in ring]
                        if kind == "Polygon" and len(points) >= 3:
                            shape_draw.polygon(points, fill=1 if i == 0 else 0)
                        elif kind == "LineString" and len(points) >= 2:
                            shape_draw.line(points, fill=1, width=width)
                    draw.bitmap((0, 0), shape, fill=1)
                terrain = ground_image(pack.read_bytes(), read_header(header.read_bytes(), cx, cy))
                colors = {
                    tuple(bytes.fromhex(PALETTE[k][1:])): cost
                    for k, cost in ((PAVEMENT, 1), (DIRT, 2), (FLOOR, 1))
                }
                # Flooring is eligible only inside a mapped road (e.g. bridge decks).
                result = bytes(
                    colors.get(p, 0) if m else 0
                    for p, m in zip(terrain.get_flattened_data(), mask.get_flattened_data())
                )
                temporary = target.with_suffix(f".{os.getpid()}.tmp")
                temporary.write_bytes(result)
                temporary.replace(target)
                paths = sorted(self.cache.glob("*.bin"), key=lambda p: p.stat().st_mtime)
                for p in paths[:-2048]:  # 128 MiB on disk.
                    p.unlink(missing_ok=True)
        except FileNotFoundError:
            result = bytes(65536)
        finally:
            self.build_time += time.monotonic() - started
        self.images[key] = result
        while len(self.images) > 384:  # 24 MiB in the routing worker.
            self.images.popitem(last=False)
        return result


class Search:
    def __init__(self, roads, known, max_expansions=MAX_EXPANSIONS, seconds=SEARCH_SECONDS):
        self.roads, self.known = roads, set(map(tuple, known))
        self.expanded = 0
        self.max_expansions, self.seconds = max_expansions, seconds
        self.started, self.build_start = time.monotonic(), roads.build_time
        self.cells = {}

    def budget(self):
        elapsed = time.monotonic() - self.started
        indexing = self.roads.build_time - self.build_start
        if elapsed > 20 and indexing > 5:
            raise RouteFailure("preparing", "Preparing the known road map…")
        if self.expanded > self.max_expansions or elapsed - indexing > self.seconds:
            raise RouteFailure("limit", "Route search reached its limit. Try a shorter trip.")

    def cost(self, x, y):
        if (x // 32 * 32, y // 32 * 32) not in self.known:
            return 0
        key = x // 256, y // 256
        if key not in self.cells:
            self.budget()
            self.cells[key] = self.roads.image(*key)
        return self.cells[key][(y % 256) * 256 + x % 256]

    def visible(self, a, b):
        return all((x * 32, y * 32) in self.known for x, y in cells_on_line(a, b, 32))

    def line(self, a, b):
        return all(self.cost(x, y) for x, y in cells_on_line(a, b))

    def snap(self, p):
        point = p["x"], p["y"]
        if p.get("z", 0) != 0:
            raise RouteFailure(
                "floor", "Driving routes need ground-level endpoints. Pick road access on the map."
            )
        x, y = math.floor(point[0]), math.floor(point[1])
        if (x // 32 * 32, y // 32 * 32) not in self.known:
            raise RouteFailure("unknown", "An endpoint is outside the selected map knowledge.")
        if self.cost(x, y):
            return x, y
        best, distance = None, 101.0
        for r in range(1, 102):
            self.budget()
            if r - 1 > distance:
                break
            boundary = [(x + dx, y + dy) for dx in range(-r, r + 1) for dy in (-r, r)]
            boundary += [(x + dx, y + dy) for dy in range(-r + 1, r) for dx in (-r, r)]
            for tx, ty in boundary:
                d = math.hypot(tx + 0.5 - point[0], ty + 0.5 - point[1])
                if (
                    d <= 100
                    and d < distance
                    and self.cost(tx, ty)
                    and self.visible(point, (tx + 0.5, ty + 0.5))
                ):
                    best, distance = (tx, ty), d
        if best is None:
            raise RouteFailure("access", "No known road access within 100 tiles of an endpoint.")
        return best

    def path(self, start, end):
        # Direction costs are exact Euclidean lengths, so h remains admissible.
        queue = [(math.dist(start, end), 0.0, start)]
        distance, previous = {start: 0.0}, {}
        while queue:
            _, g, p = heapq.heappop(queue)
            if g != distance.get(p):
                continue
            if p == end:
                path = [p]
                while p in previous:
                    p = previous[p]
                    path.append(p)
                return [(x + 0.5, y + 0.5) for x, y in reversed(path)]
            self.expanded += 1
            if self.expanded % 256 == 0 or self.expanded > self.max_expansions:
                self.budget()
            x, y = p
            here = self.cost(x, y)
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (-1, 1), (1, -1), (1, 1)):
                q = x + dx, y + dy
                weight = self.cost(*q)
                if (
                    not weight
                    or dx
                    and dy
                    and (not self.cost(x + dx, y) or not self.cost(x, y + dy))
                ):
                    continue
                candidate = g + (math.sqrt(2) if dx and dy else 1) * (here + weight) / 2
                if candidate < distance.get(q, math.inf):
                    distance[q], previous[q] = candidate, p
                    heapq.heappush(queue, (candidate + math.dist(q, end), candidate, q))
        raise RouteFailure(
            "no_path",
            "Known roads do not connect these endpoints. Explore the gap or choose another destination.",
        )

    def simplify(self, path):
        if len(path) <= 2:
            return path
        # Collapse straight grid runs first; then constrained Douglas–Peucker.
        corners = [path[0]]
        for a, b, c in zip(path, path[1:], path[2:]):
            if (b[0] - a[0], b[1] - a[1]) != (c[0] - b[0], c[1] - b[1]):
                corners.append(b)
        corners.append(path[-1])
        kept, stack = {0, len(corners) - 1}, [(0, len(corners) - 1)]
        while stack:
            first, last = stack.pop()
            self.budget()
            if last - first < 2:
                continue
            a, b = corners[first], corners[last]
            dx, dy = b[0] - a[0], b[1] - a[1]
            length = dx * dx + dy * dy
            furthest, index = -1, first + 1
            for i in range(first + 1, last):
                p = corners[i]
                t = (
                    max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length))
                    if length
                    else 0
                )
                d = math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy)
                if d > furthest:
                    furthest, index = d, i
            if furthest > 2 or not self.line(a, b):
                kept.add(index)
                stack.extend(((first, index), (index, last)))
        return [corners[i] for i in sorted(kept)]

    def route(self, anchors, turns=True):
        snaps = [self.snap(p) for p in anchors]
        stops, geometry = [dict(anchors[0])], []
        road_distance = access_distance = 0.0
        for a, b, start, end in zip(anchors, anchors[1:], snaps, snaps[1:]):
            path = self.simplify(self.path(start, end))
            start_xy, end_xy = (a["x"], a["y"]), (b["x"], b["y"])
            start_access = not self.line(start_xy, path[0])
            end_access = not self.line(path[-1], end_xy)
            if not start_access:
                path[0] = start_xy
            else:
                path.insert(0, start_xy)
            if not end_access:
                # Two endpoints in the same tile must remain distinct.
                if len(path) == 1:
                    path.append(end_xy)
                else:
                    path[-1] = end_xy
            else:
                path.append(end_xy)
            modes = [
                "access" if i == 0 and start_access or i == len(path) - 2 and end_access else "road"
                for i in range(len(path) - 1)
            ]
            if turns:
                leg = [stops[-1]]
                for x, y in path[1:-1]:
                    leg.append(
                        {
                            "id": str(uuid4()),
                            "label": f"Road turn {len(stops) + len(leg) - 1}",
                            "x": x,
                            "y": y,
                            "z": 0,
                            "kind": "generated",
                        }
                    )
                leg.append(dict(b))
                stops.extend(leg[1:])
                for i, mode in enumerate(modes):
                    geometry.append(
                        {
                            "from": leg[i]["id"],
                            "to": leg[i + 1]["id"],
                            "kind": mode,
                            "points": [path[i], path[i + 1]],
                        }
                    )
            else:
                stops.append(dict(b))
                for i, mode in enumerate(modes):
                    if (
                        geometry
                        and geometry[-1]["from"] == a["id"]
                        and geometry[-1]["kind"] == mode
                    ):
                        geometry[-1]["points"].append(path[i + 1])
                    else:
                        geometry.append(
                            {
                                "from": a["id"],
                                "to": b["id"],
                                "kind": mode,
                                "points": [path[i], path[i + 1]],
                            }
                        )
            for i, mode in enumerate(modes):
                d = math.dist(path[i], path[i + 1])
                if mode == "road":
                    road_distance += d
                else:
                    access_distance += d
            if len(stops) > MAX_STOPS:
                raise RouteFailure(
                    "limit",
                    "This route has more than 256 checkpoints. Split it into shorter trips.",
                )
        return {
            "status": "ready",
            "message": "Road route ready",
            "stops": stops,
            "geometry": geometry,
            "road_distance": round(road_distance, 1),
            "access_distance": round(access_distance, 1),
            "map_revision": self.roads.revision,
            "expanded": self.expanded,
        }


_roads = None
_identity = None


def calculate(root, cache, known, anchors, turns, terrain_revision=0):
    global _roads, _identity
    started = time.monotonic()
    try:
        path = Path(root) / "worldmap.xml.bin"
        stat = path.stat()
        identity = root, stat.st_size, stat.st_mtime_ns, terrain_revision
        if _identity != identity:
            _roads, _identity = Roads(root, cache), identity
        result = Search(_roads, known).route(anchors, turns)
    except RouteFailure as exc:
        result = {"status": exc.status, "message": exc.message}
    except (OSError, ValueError, struct.error, IndexError):
        result = {
            "status": "unavailable",
            "message": "Installed road data is unavailable or unsupported.",
        }
    result["elapsed_ms"] = round((time.monotonic() - started) * 1000)
    return result
