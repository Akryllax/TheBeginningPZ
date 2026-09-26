"""B42.20.4 save adapters. Read copies only; never modify game files."""

import io
import struct
import zipfile
from pathlib import Path

from .models import Bounds, Marker


def stable_read(path: Path, limit=32 * 1024 * 1024):
    before = path.stat()
    if before.st_size > limit:
        raise ValueError("source exceeds size limit")
    data = path.read_bytes()
    after = path.stat()
    if (before.st_ino, before.st_size, before.st_mtime_ns) != (
        after.st_ino,
        after.st_size,
        after.st_mtime_ns,
    ):
        raise ValueError("source is changing; retry next poll")
    return data


def decode_coverage(data: bytes, username: str, bounds: Bounds):
    expected = coverage_length(bounds) + 4
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        entry = archive.getinfo(username)
        if entry.file_size != expected:
            raise ValueError("exploration length does not match world metadata")
        payload = archive.read(entry)  # validates CRC as well
    if struct.unpack(">i", payload[:4])[0] != bounds.world_version:
        raise ValueError("unsupported exploration world version")
    yield from decode_coverage_bits(payload[4:], bounds)


def coverage_length(bounds: Bounds):
    width = (bounds.max_x - bounds.min_x + 1) * 8
    height = (bounds.max_y - bounds.min_y + 1) * 8
    expected = width // 4 * height
    if width <= 0 or height <= 0 or not 0 < expected <= 32 * 1024 * 1024 - 4:
        raise ValueError("invalid exploration dimensions")
    return expected


def decode_coverage_bits(payload: bytes, bounds: Bounds):
    if len(payload) != coverage_length(bounds):
        raise ValueError("exploration length does not match world metadata")
    width = (bounds.max_x - bounds.min_x + 1) * 8
    stride = width // 4
    for index, packed in enumerate(payload):
        if not packed:
            continue
        row, col = divmod(index, stride)
        for unit in range(4):
            flags = (packed >> (unit * 2)) & 3
            if flags:
                yield (bounds.min_x * 256 + (col * 4 + unit) * 32, bounds.min_y * 256 + row * 32, flags)


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, n):
        value = self.data[self.pos : self.pos + n]
        if len(value) != n:
            raise ValueError("truncated marker file")
        self.pos += n
        return value

    def number(self, fmt):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(">" + fmt)))[0]

    def string(self):
        return self.take(self.number("H")).decode("utf-8")


def decode_public_markers(data):
    r = Reader(data)
    if r.take(4) != b"WMSY" or r.number("i") != 249 or r.number("i") != 2:
        raise ValueError("unsupported shared marker format")
    fonts = [r.string() for _ in range(r.number("B"))]
    count = r.number("i")
    if not 0 <= count <= 8192:
        raise ValueError("invalid marker count")
    public = []
    for _ in range(count):
        id, author, sharing = r.number("i"), r.string(), r.number("B")
        if sharing & ~15:
            raise ValueError("unknown sharing flags")
        if sharing & 8:
            for _ in range(r.number("B")):
                r.string()
        kind = r.number("B")
        if kind not in (0, 1):
            raise ValueError("unknown marker kind")
        base = r.take(30)
        x, y = struct.unpack(">ff", base[:8])
        flags = base[-1]
        if flags & ~15:
            raise ValueError("unknown symbol flags")
        r.take(4 * (bool(flags & 4) + bool(flags & 8)))
        label = r.string()
        if kind == 0:
            r.number("B")
            font_index = r.number("B")
            if font_index >= len(fonts):
                raise ValueError("invalid font index")
        if sharing & 1:
            public.append(
                Marker(
                    id=f"marker:{id}",
                    author=author,
                    label=label,
                    x=x,
                    y=y,
                    public=True,
                    color="#" + base[24:27].hex(),
                ).model_dump()
            )
    if r.pos != len(data):
        raise ValueError("trailing marker data")
    return public
