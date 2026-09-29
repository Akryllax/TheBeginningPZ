"""Read-only B42 terrain and building metadata, matching IsoLot.load ordering."""

import hashlib
import io
import json
import math
import re
import struct
import threading
from collections import OrderedDict
from pathlib import Path

from PIL import Image

PALETTE = ["#e9e5cc", "#898d91", "#326e43", "#aa7d63", "#d5bc93", "#699da5", "#b49a72"]
UNKNOWN = "#25342f"
# Bump when classification, palette or rasterization changes. Persisted cell
# images and already-open browsers must both stop using the previous rendering.
RENDER_VERSION = 2
GROUND, PAVEMENT, VEGETATION, STRUCTURE, FLOOR, WATER, DIRT = range(7)
# Ground surfaces stay beneath bridges, flooring, walls and trees.
STACK_PRIORITY = (STRUCTURE, FLOOR, VEGETATION, PAVEMENT, DIRT, WATER)
CATEGORIES = {
    "library": ("Library", {"library"}),
    "bookshop": ("Bookshop", {"bookstore", "bookstorestorage"}),
    "mechanic": ("Mechanics shop", {"mechanic", "mechanicstorage", "autoshop", "carrepair"}),
    "garage": ("Garage", {"garage", "garagestorage"}),
    "hardware": (
        "Hardware / tool shop",
        {"toolstore", "toolstorage", "hardwarestore", "hardwarestorage"},
    ),
    "construction": (
        "Construction supplies / workshop",
        {
            "construction",
            "constructionstorage",
            "metalshop",
            "carpentry",
            "woodwork",
            "lumberstorage",
            "logging",
            "factory",
            "factorysaw",
            "factorysawstorage",
        },
    ),
    "warehouse": (
        "Warehouse / storage",
        {"warehouse", "warehousestorage", "storageunit", "storage", "factorywarehouse"},
    ),
    "grocery": (
        "Grocery / supermarket",
        {
            "grocery",
            "grocerystorage",
            "supermarket",
            "conveniencestore",
            "cornerstore",
            "cornerstorestorage",
            "gigamart",
            "gigamartstorage",
        },
    ),
    "surplus": ("Military surplus store", {"armysurplus", "armysurplusguns"}),
    "gunstore": ("Gun store", {"gunstore", "gunstorestorage", "gunstorage"}),
    "armory": (
        "Armory / weapons storage",
        {"armory", "policegunstorage", "prisonarmory", "policeswat"},
    ),
    "military": ("Military site / storage", {"armystorage", "armytent", "oldarmy"}),
    "hunting": ("Hunting shop / section", {"hunting"}),
    "police": (
        "Police station",
        {"police", "policeoffice", "policestorage", "evidenceroom", "interrogationroom"},
    ),
    "medical": (
        "Medical facility / pharmacy",
        {"medical", "pharmacy", "pharmacystorage", "hospitalroom", "hospital", "clinic", "dentist"},
    ),
    "gas": ("Gas station", {"gasstore", "gas2go", "gasstorage", "fossoil", "spiffoil"}),
    "fire": ("Fire station", {"firestorage", "firestation", "firegarage"}),
    "restaurant": (
        "Restaurant / café",
        {
            "restaurant",
            "restaurantdining",
            "restaurantkitchen",
            "cafe",
            "cafekitchen",
            "spiffos",
            "spiffoskitchen",
            "pizzakitchen",
            "diner",
        },
    ),
}
WEAPON_CATEGORIES = {"surplus", "gunstore", "armory", "military", "hunting"}
ALIASES = {
    "library": {"library", "bookshop"},
    "libraries": {"library", "bookshop"},
    "books": {"library", "bookshop"},
    "bookstore": {"bookshop"},
    "bookshop": {"bookshop"},
    "mechanic": {"mechanic"},
    "mechanics": {"mechanic"},
    "auto repair": {"mechanic"},
    "garage": {"garage", "mechanic"},
    "garages": {"garage", "mechanic"},
    "builder": {"hardware", "construction", "warehouse"},
    "builders": {"hardware", "construction", "warehouse"},
    "building supplies": {"hardware", "construction", "warehouse"},
    "construction": {"hardware", "construction", "warehouse"},
    "workshop": {"construction"},
    "hardware": {"hardware"},
    "tools": {"hardware"},
    "warehouse": {"warehouse"},
    "storage": {"warehouse"},
    "supermarket": {"grocery"},
    "groceries": {"grocery"},
    "grocery": {"grocery"},
    "food": {"grocery", "restaurant"},
    "military surplus": {"surplus"},
    "military surplus store": {"surplus"},
    "military surplus stores": {"surplus"},
    "military surplus shop": {"surplus"},
    "army surplus": {"surplus"},
    "army surplus store": {"surplus"},
    "army surplus stores": {"surplus"},
    "army surplus shop": {"surplus"},
    "surplus": {"surplus"},
    "surplus store": {"surplus"},
    "surplus stores": {"surplus"},
    "surplus shop": {"surplus"},
    "military": {"military", "surplus"},
    "army": {"military", "surplus"},
    "military base": {"military"},
    "army base": {"military"},
    "military storage": {"military"},
    "gun store": {"gunstore"},
    "gun stores": {"gunstore"},
    "gun shop": {"gunstore"},
    "gun shops": {"gunstore"},
    "gunstore": {"gunstore"},
    "gunshop": {"gunstore"},
    "armory": {"armory"},
    "armories": {"armory"},
    "armoury": {"armory"},
    "armouries": {"armory"},
    "weapons storage": {"armory"},
    "hunting": {"hunting"},
    "hunting shop": {"hunting"},
    "hunting store": {"hunting"},
    "gun": WEAPON_CATEGORIES,
    "guns": WEAPON_CATEGORIES,
    "weapon": WEAPON_CATEGORIES,
    "weapons": WEAPON_CATEGORIES,
    "weapon shop": WEAPON_CATEGORIES,
    "weapons shop": WEAPON_CATEGORIES,
    "firearm": WEAPON_CATEGORIES,
    "firearms": WEAPON_CATEGORIES,
    "ammo": WEAPON_CATEGORIES,
    "ammunition": WEAPON_CATEGORIES,
    "police station": {"police"},
    "police": {"police"},
    "hospital": {"medical"},
    "pharmacy": {"medical"},
    "medical": {"medical"},
    "gas station": {"gas"},
    "petrol": {"gas"},
    "fuel": {"gas"},
    "fire station": {"fire"},
    "fire": {"fire"},
    "restaurant": {"restaurant"},
    "cafe": {"restaurant"},
}


class Binary:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, n):
        if n < 0 or self.pos + n > len(self.data):
            raise ValueError("Truncated map metadata")
        result = self.data[self.pos : self.pos + n]
        self.pos += n
        return result

    def integer(self):
        return struct.unpack("<i", self.take(4))[0]

    def count(self, maximum=100000):
        n = self.integer()
        if not 0 <= n <= maximum:
            raise ValueError("Invalid map record count")
        return n

    def line(self):
        end = self.data.find(b"\n", self.pos, self.pos + 4096)
        if end < 0:
            raise ValueError("Invalid map string")
        return self.take(end - self.pos + 1)[:-1].decode("utf-8").strip()


def read_header(data, cx, cy):
    b = Binary(data)
    if data[:4] == b"LOTH":
        b.take(4)
    version = b.integer()
    if version not in (0, 1):
        raise ValueError("Unsupported lotheader version")
    names = [b.line() for _ in range(b.count())]
    if version == 0:
        b.take(1)
    if (b.integer(), b.integer()) != (8, 8):
        raise ValueError("Expected Build 42 map dimensions")
    lo, hi = (b.integer(), b.integer()) if version else (0, b.integer() - 1)
    if not -32 <= lo <= hi <= 64:
        raise ValueError("Invalid map levels")
    rooms = []
    for _ in range(b.count()):
        name, z = b.line(), b.integer()
        rects = []
        for _ in range(b.count()):
            x, y, w, h = (b.integer() for _ in range(4))
            if w <= 0 or h <= 0 or w > 2048 or h > 2048:
                raise ValueError("Invalid room rectangle")
            rects.append([cx * 256 + x, cy * 256 + y, w, h])
        b.take(b.count() * 12)
        rooms.append({"name": name, "z": z, "rects": rects})
    buildings = []
    for _ in range(b.count()):
        ids = [b.integer() for _ in range(b.count())]
        if any(i < 0 or i >= len(rooms) for i in ids):
            raise ValueError("Invalid building room reference")
        buildings.append(ids)
    b.take(1024)
    return {"names": names, "min_z": lo, "max_z": hi, "rooms": rooms, "buildings": buildings}


def sprite_kind(name):
    sheet, _, suffix = name.rpartition("_")
    index = int(suffix) if suffix.isdecimal() else -1
    # Verified against B42 newtiledefinitions.tiles FloorMaterial/grassFloor
    # properties and ISShovelGroundCursor.GetDirtGravelSand. A sheet also has
    # shoreline/edge overlays, so include those rather than only full tiles.
    if sheet == "blends_natural_02" and 0 <= index < 16:
        return WATER
    if sheet == "blends_natural_01" and (64 <= index < 80 or 96 <= index < 128):
        return DIRT
    if sheet == "blends_street_01" and 48 <= index < 60:
        return DIRT  # Gravel, including its edge overlays.
    if sheet == "floors_exterior_natural_01":
        return GROUND if index in (0, 1, 2, 24) else DIRT
    if name.startswith("floors_exterior_street_"):
        return PAVEMENT
    if name.startswith("blends_water"):
        return WATER
    if name.startswith(
        (
            "walls_",
            "fencing_",
            "fixtures_",
            "furniture_",
            "appliances_",
            "industry_",
            "constructedobjects_",
            "carpentry_",
        )
    ):
        return STRUCTURE
    if name.startswith("floors_"):
        return FLOOR
    if "vegetation" in name or "trees" in name:
        return VEGETATION
    if "street" in name or "asphalt" in name:
        return PAVEMENT
    return GROUND


def classify(names):
    kinds = {sprite_kind(name) for name in names}
    return next((kind for kind in STACK_PRIORITY if kind in kinds), GROUND)


def ground_image(data, header):
    b = Binary(data)
    version = 0
    if data[:4] == b"LOTP":
        b.take(4)
        version = b.integer()
        if version != 1:
            raise ValueError("Unsupported lotpack version")
    table = (8 if version else 0) + 4
    image = Image.new("RGB", (256, 256), PALETTE[0])
    pixels = image.load()
    colors = [tuple(bytes.fromhex(c[1:])) for c in PALETTE]
    types = [sprite_kind(name) for name in header["names"]]
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
                        b.integer()  # room index
                        refs = [b.integer() for _ in range(count - 1)]
                        if any(i < 0 or i >= len(types) for i in refs):
                            raise ValueError("Invalid tile reference")
                        if z == 0:
                            # Classify the stack independently of arbitrary sprite order.
                            t = [types[i] for i in refs]
                            kind = next((k for k in STACK_PRIORITY if k in t), GROUND)
                            pixels[lx * 8 + x, ly * 8 + y] = colors[kind]
    return image


def visible_rects(rects, known):
    result = []
    for x, y, w, h in rects:
        for bx in range(math.floor(x / 32) * 32, math.ceil((x + w) / 32) * 32, 32):
            for by in range(math.floor(y / 32) * 32, math.ceil((y + h) / 32) * 32, 32):
                if (bx, by) in known:
                    a, b, c, d = max(x, bx), max(y, by), min(x + w, bx + 32), min(y + h, by + 32)
                    if c > a and d > b:
                        result.append([a, b, c - a, d - b])
    return result


class Terrain:
    def __init__(self, root: Path, cache: Path):
        self.root, self.cache = root, cache
        cache.mkdir(parents=True, exist_ok=True)
        self.headers, self.places = {}, {}
        self.lock = threading.RLock()
        self.tiles = OrderedDict()
        self.revision = RENDER_VERSION << 32
        self.pack_fingerprints = {}

    def header(self, cx, cy):
        p = self.root / f"{cx}_{cy}.lotheader"
        stat = p.stat()
        identity = (stat.st_size, stat.st_mtime_ns)
        key = (cx, cy)
        if key not in self.headers or self.headers[key][0] != identity:
            h = read_header(p.read_bytes(), cx, cy)
            self.headers[key] = (identity, h)
            self.index_places(cx, cy, h)
            self.revision += 1
        return self.headers[key][1]

    def index_places(self, cx, cy, header):
        entries = []
        grouped = set()
        groups = list(header["buildings"])
        for group in groups:
            grouped.update(group)
        groups += [[i] for i in range(len(header["rooms"])) if i not in grouped]
        for group in groups:
            rooms = [header["rooms"][i] for i in group]
            residential = any(r["name"] in {"bedroom", "livingroom", "kidsbedroom"} for r in rooms)
            for category, (label, types) in CATEGORIES.items():
                matching = [r for r in rooms if r["name"].lower() in types]
                if not matching:
                    continue
                rects = sorted({tuple(rect) for r in matching for rect in r["rects"]})
                if not rects:
                    continue
                identity = hashlib.sha256(json.dumps([category, rects]).encode()).hexdigest()[:20]
                entries.append(
                    {
                        "id": identity,
                        "label": "Residential garage"
                        if category == "garage" and residential
                        else label,
                        "category": category,
                        "z": min(r["z"] for r in matching),
                        "rects": rects,
                    }
                )
        self.places[(cx, cy)] = entries

    def index_known(self, known):
        if not self.root.is_dir():
            raise FileNotFoundError("Installed map directory is unavailable")
        cells = {
            (x // 256 + dx, y // 256 + dy)
            for x, y in known
            for dx in (-1, 0, 1)
            for dy in (-1, 0, 1)
        }
        with self.lock:
            for cx, cy in sorted(cells):
                if (self.root / f"{cx}_{cy}.lotheader").exists():
                    self.header(cx, cy)
                    pack = self.root / f"world_{cx}_{cy}.lotpack"
                    if pack.exists():
                        stat = pack.stat()
                        fingerprint = (stat.st_size, stat.st_mtime_ns)
                        if self.pack_fingerprints.get((cx, cy)) != fingerprint:
                            self.pack_fingerprints[(cx, cy)] = fingerprint
                            self.revision += 1

    def search(self, query, known, x, y, limit=20):
        query = re.sub(r"\s+", " ", query.lower().strip())
        categories = ALIASES.get(query)
        results = {}
        with self.lock:
            for entries in self.places.values():
                for place in entries:
                    if categories is not None:
                        if place["category"] not in categories:
                            continue
                    elif not all(
                        word in (place["label"] + " " + place["category"]).lower()
                        for word in query.split()
                    ):
                        continue
                    rects = visible_rects(place["rects"], known)
                    if not rects:
                        continue
                    # Anchor inside a known matching room, never in an unknown centroid.
                    rect = min(
                        rects, key=lambda r: (r[0] + r[2] / 2 - x) ** 2 + (r[1] + r[3] / 2 - y) ** 2
                    )
                    px, py = rect[0] + rect[2] / 2, rect[1] + rect[3] / 2
                    results[place["id"]] = {k: v for k, v in place.items() if k != "rects"} | {
                        "x": px,
                        "y": py,
                        "distance": round(math.hypot(px - x, py - y), 1),
                        "rects": rects,
                    }
        # Different cells/floors may describe the same destination. Merge only
        # matching categories whose actual room footprints overlap.
        merged = []
        for place in sorted(results.values(), key=lambda p: (abs(p["z"]), p["id"])):
            overlaps = []
            for other in merged:
                if other["category"] == place["category"] and any(
                    a < e + g and e < a + c and b < f + h and f < b + d
                    for a, b, c, d in place["rects"]
                    for e, f, g, h in other["rects"]
                ):
                    overlaps.append(other)
            if overlaps:
                primary = overlaps[0]
                primary["rects"] = [
                    list(r)
                    for r in sorted({tuple(r) for p in [place, *overlaps] for r in p["rects"]})
                ]
                for duplicate in overlaps[1:]:
                    merged.remove(duplicate)
            else:
                merged.append(place)
        return sorted(merged, key=lambda p: (p["distance"], p["id"]))[:limit]

    def cell_image(self, cx, cy):
        header = self.header(cx, cy)
        p = self.root / f"world_{cx}_{cy}.lotpack"
        stat = p.stat()
        fingerprint = hashlib.sha256(
            repr(
                (RENDER_VERSION, self.headers[(cx, cy)][0], stat.st_size, stat.st_mtime_ns)
            ).encode()
        ).hexdigest()[:16]
        target = self.cache / f"{cx}_{cy}_{fingerprint}.png"
        if target.exists():
            with Image.open(target) as cached:
                return cached.convert("RGB")
        image = ground_image(p.read_bytes(), header)
        image.save(target)
        self.prune()
        return image

    def prune(self):
        paths = sorted(self.cache.glob("*.png"), key=lambda p: p.stat().st_mtime)
        total = sum(p.stat().st_size for p in paths)
        while total > 512 * 1024 * 1024 and paths:
            p = paths.pop(0)
            total -= p.stat().st_size
            p.unlink()

    def tile(self, zoom, tx, ty, known, token):
        span = 256 * 2 ** (-zoom)
        x0, y0 = tx * span, ty * span
        key = (zoom, tx, ty, token, self.revision)
        with self.lock:
            if key in self.tiles:
                self.tiles.move_to_end(key)
                return self.tiles[key]
            result = Image.new("RGB", (256, 256), UNKNOWN)
            blocks = [
                (x, y)
                for x, y in known
                if x < x0 + span and x + 32 > x0 and y < y0 + span and y + 32 > y0
            ]
            cells = {}
            scale = 256 / span
            for x, y in blocks:
                cx, cy = x // 256, y // 256
                if (cx, cy) not in cells:
                    try:
                        cells[(cx, cy)] = self.cell_image(cx, cy)
                    except FileNotFoundError:
                        cells[(cx, cy)] = None
                source = cells[(cx, cy)]
                if source is None:
                    continue
                a, b, c, d = max(x, x0), max(y, y0), min(x + 32, x0 + span), min(y + 32, y0 + span)
                crop = source.crop(
                    (int(a - cx * 256), int(b - cy * 256), int(c - cx * 256), int(d - cy * 256))
                )
                size = (round((c - a) * scale), round((d - b) * scale))
                # Average only this known block when zooming out. Nearest-neighbor
                # sampling can miss an entire narrow rural road or stream. Cropping
                # before filtering prevents hidden neighbors from leaking through.
                result.paste(
                    crop.resize(
                        size, Image.Resampling.BOX if scale < 1 else Image.Resampling.NEAREST
                    ),
                    (round((a - x0) * scale), round((b - y0) * scale)),
                )
            output = io.BytesIO()
            result.save(output, format="PNG")
            payload = output.getvalue()
            self.tiles[key] = payload
            while len(self.tiles) > 256:
                self.tiles.popitem(last=False)
            return payload
