"""Read-only Build 42.20.4 / world version 249 vehicle classification.

Follows the installed BaseVehicle/VehiclePart save layout. Item and entity
payloads are skipped by their declared sizes, never searched for byte patterns.
Unknown layouts raise ValueError so callers can hide unclassified vehicles.
"""

import math
import struct


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def skip(self, size):
        if size < 0 or self.pos + size > len(self.data):
            raise ValueError("Truncated vehicle record")
        self.pos += size

    def number(self, fmt):
        size = struct.calcsize(fmt)
        start = self.pos
        self.skip(size)
        return struct.unpack_from(">" + fmt, self.data, start)[0]

    def count(self, fmt="i", limit=10000):
        value = self.number(fmt)
        if not 0 <= value <= limit:
            raise ValueError("Invalid vehicle count")
        return value

    def flag(self):
        value = self.number("B")
        if value not in (0, 1):
            raise ValueError("Invalid vehicle flag")
        return bool(value)

    def string(self):
        size = self.count("h", 32767)
        start = self.pos
        self.skip(size)
        return self.data[start : self.pos].decode("utf-8")

    def block(self):
        self.skip(self.count(limit=16 * 1024 * 1024))

    def table(self, depth=0):
        if depth > 16:
            raise ValueError("Vehicle mod data too deeply nested")
        for _ in range(self.count() * 2):
            kind = self.number("B")
            if kind == 0:
                self.string()
            elif kind == 1:
                self.skip(8)
            elif kind == 2:
                self.table(depth + 1)
            elif kind == 3:
                self.flag()
            else:
                raise ValueError("Unknown vehicle mod data type")

    def container(self):
        self.string()
        self.flag()
        for _ in range(self.count("h")):
            identical = self.count(limit=100000)
            if not identical:
                raise ValueError("Invalid identical-item count")
            self.block()
            self.skip((identical - 1) * 4)
        self.flag()
        self.skip(4)

    def device(self):
        self.string()
        self.flag()
        self.skip(8)
        self.flag()
        self.skip(8)
        for _ in range(4):
            self.flag()
        self.skip(12)
        self.flag()
        self.flag()
        self.skip(12)
        if self.flag():
            self.skip(4)
            for _ in range(self.count()):
                self.string()
                self.skip(4)
        self.skip(3)
        if self.flag():
            self.string()
        self.flag()

    def part(self):
        self.string()
        self.flag()
        self.skip(4)
        if self.flag():
            self.block()  # InventoryItem.saveWithSize
        if self.flag():
            self.container()
        if self.flag():
            self.table()
        if self.flag():
            self.device()
        if self.flag():
            self.flag()
            self.skip(20)  # VehicleLight
        if self.flag():
            for _ in range(3):
                self.flag()  # VehicleDoor
        if self.flag():
            self.skip(1)
            self.flag()  # VehicleWindow
        self.skip(20)
        if self.flag():
            for _ in range(self.count("b", 127)):
                self.block()  # GameEntity component ByteBlock


def decode_vehicle(data, version, x, y):
    if version != 249 or not isinstance(data, bytes) or not 50 <= len(data) <= 16 * 1024 * 1024:
        raise ValueError("Unsupported vehicle record")
    if data[:2] != b"\x01\x21" or data[26] != 0:
        raise ValueError("Unsupported vehicle object header")
    bx, by, z = struct.unpack_from(">fff", data, 10)
    if not all(math.isfinite(v) for v in (bx, by, z)) or abs(bx - x) > 0.05 or abs(by - y) > 0.05:
        raise ValueError("Vehicle coordinates disagree")
    r = Reader(data)
    r.skip(47)
    model = r.string()
    if not model or len(model) > 128:
        raise ValueError("Invalid vehicle model")
    r.skip(4)
    r.flag()  # Engine running
    r.skip(24)  # Durability, engine loudness/quality
    key_id = r.number("i")
    r.skip(1)  # Key spawn state (not ownership)
    for _ in range(4):
        r.flag()
    r.skip(2)  # Lightbar modes
    for _ in range(r.count("h", 256)):
        r.part()
    r.flag()  # Key on door
    hotwired = r.flag()
    r.flag()  # Broken hotwire attempt does not qualify
    r.flag()  # Key in ignition does not establish ownership
    r.skip(26)  # Rust, paint, engine power, vehicle and mechanical IDs
    r.flag()
    r.skip(8)
    r.string()  # Alarm sound
    r.skip(8)
    if r.flag():
        r.block()  # Current key
    for _ in range(r.count("b", 127)):
        r.string()
        r.skip(1)
    if r.flag():
        r.skip(4)
        r.string()
        r.string()
        r.skip(4)
    r.skip(4)
    r.flag()  # Previously entered is not ownership
    r.flag()  # Previously moved is not ownership
    r.block()  # Animal payload has an explicit byte length
    if r.pos != len(data):
        raise ValueError("Unexpected vehicle trailing data")
    return {
        "model": model,
        "z": z,
        "key_id": key_id,
        "hotwired": hotwired,
        "wreck": "Burnt" in model or "Smashed" in model,
    }
