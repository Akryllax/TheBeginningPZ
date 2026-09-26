"""Read carried car keys from verified version-249 player inventory structures.

The reader walks inventory records and nested containers; it never searches
character blobs for matching key bytes. Only car-key items qualify.
"""

import re

from .formats import stable_read
from .vehicle_flags import Reader


def item_types(dictionary, scripts):
    """Resolve world registry IDs against this installation's vanilla item types."""
    wanted = {"Base.CarKey": "key"}
    text = (scripts / "generated/items/container.txt").read_text()
    if not re.match(r"\s*module\s+Base\s*\{", text):
        raise ValueError("Unsupported container script module")
    for match in re.finditer(r"\bitem\s+(\w+)\s*\{(.*?)(?=\n\s*item\s+|\Z)", text, re.DOTALL):
        if re.search(r"\bItemType\s*=\s*base:container\s*,", match[2]):
            wanted["Base." + match[1]] = "container"
    text = stable_read(dictionary).decode("utf-8")
    result = {}
    for match in re.finditer(r'\bregistryID\s*=\s*(\d+),\s*fulltype\s*=\s*"([\w.]+)"', text):
        if match[2] in wanted:
            ident = int(match[1])
            if ident in result:
                raise ValueError("Duplicate inventory registry ID")
            result[ident] = wanted[match[2]]
    if not result or not any(v == "key" for v in result.values()):
        raise ValueError("Car key registry unavailable")
    return result


class KeyReader(Reader):
    def item_visual(self):
        flags = self.number("B")
        if flags & ~31:
            raise ValueError("Unknown item visual flags")
        for _ in range(3):
            self.string()
        for bit, size in ((1, 3), (2, 1), (4, 1), (8, 4)):
            if flags & bit:
                self.skip(size)
        if flags & 16:
            self.string()
        for _ in range(6):
            self.skip(self.count("b", 127))

    def human_visual(self):
        flags = self.number("B")
        if flags & ~127:
            raise ValueError("Unknown human visual flags")
        for bit in (4, 2, 8):
            if flags & bit:
                self.skip(3)
        self.skip(3)
        for bit in (64, 16, 32):
            if flags & bit:
                self.string()
        for _ in range(3):
            self.skip(self.count("b", 127))
        for _ in range(self.count("b", 127)):
            self.item_visual()
        self.string()
        flags = self.number("B")
        if flags & ~6:
            raise ValueError("Unknown hair color flags")
        for bit in (4, 2):
            if flags & bit:
                self.skip(3)

    def item_base(self):
        self.skip(4)  # Item instance ID
        header = self.number("B")
        if header & 128:
            raise ValueError("Unknown inventory flags")
        if header & 1:
            self.skip(4)
        if header & 4:
            self.skip(1)
        if header & 8:
            self.item_visual()
        if header & 16:
            self.skip(4)
        if header & 32:
            self.skip(4)
        if not header & 64:
            return
        bits = self.number("I")
        if bits & 0x88000000:
            raise ValueError("Unsupported inventory extension")
        if bits & 1:
            self.table()
        if bits & 4:
            self.skip(2)
        if bits & 8:
            self.string()
        if bits & 16:
            self.block()
        if bits & 32:
            self.skip(self.count() * 2)
        for bit, size in ((128, 4), (256, 4), (1024, 8), (2048, 3)):
            if bits & bit:
                self.skip(size)
        if bits & 4096:
            self.string()
        if bits & 8192:
            self.skip(4)
        if bits & 32768:
            self.string()
        for bit in (131072, 262144):
            if bits & bit:
                self.skip(4)
        for bit in (524288, 0x100000):
            if bits & bit:
                self.string()
        if bits & 0x200000:
            self.skip(4)
        if bits & 0x400000:
            self.skip(2)
        if bits & 0x1000000:
            self.skip(4)
        if bits & 0x4000000:
            for _ in range(self.count("b", 127)):
                self.block()
        if bits & 0x10000000:
            self.string()
        if bits & 0x20000000:
            self.skip(4)
        if bits & 0x40000000:
            self.skip(12)

    def inventory(self, types, depth=0):
        if depth > 16:
            raise ValueError("Inventory too deeply nested")
        keys = set()
        self.string()
        self.flag()
        for _ in range(self.count("h")):
            identical = self.count(limit=100000)
            if not identical:
                raise ValueError("Invalid inventory item count")
            length = self.count(limit=16 * 1024 * 1024)
            start = self.pos
            self.skip(length)
            item = KeyReader(self.data[start : self.pos])
            registry = item.number("h")
            if item.number("b") != -1:
                raise ValueError("Unsupported inventory save type")
            kind = types.get(registry)
            if kind:
                item.item_base()
                if kind == "key":
                    key = item.number("i")
                    item.skip(1)
                    if key >= 0:
                        keys.add(key)
                else:
                    item.skip(8)
                    keys.update(item.inventory(types, depth + 1))
                if item.pos != len(item.data):
                    raise ValueError("Unexpected item trailing data")
            self.skip((identical - 1) * 4)
        self.flag()
        self.skip(4)
        return keys


def decode_player_keys(data, version, types):
    if version != 249 or not isinstance(data, bytes) or not 28 <= len(data) <= 16 * 1024 * 1024:
        raise ValueError("Unsupported player record")
    if data[:2] != b"\x01\x01":
        raise ValueError("Unsupported player header")
    r = KeyReader(data)
    r.skip(26)
    if r.flag():
        r.table()
    if r.flag():
        r.skip(4)
        for _ in range(3):
            r.string()
        r.skip(4)
        r.string()
        extra = r.count(limit=1)
        if extra:
            for _ in range(r.count()):
                r.string()
        for _ in range(r.count()):
            r.string()
            r.skip(4)
        r.string()
        r.skip(8)
    r.human_visual()
    return r.inventory(types)
