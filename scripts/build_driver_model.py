#!/usr/bin/env python3
"""Generate our original low-poly seated civilian and palette, without game assets.

Coordinates are metres, +Y up, +Z forward, origin at the seated pelvis.
The X mesh uses flat normals and palette UVs; no skinning/client Java is required.
"""

from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
MEDIA = ROOT / "mods/AKRStoryteller/42/media"


def geometry():
    vertices, faces, normals, uvs = [], [], [], []
    # Face-local vertices preserve hard edges and a single palette band per box.
    sides = [
        ((1, 0, 0), (1, 5, 7, 3)),
        ((-1, 0, 0), (4, 0, 2, 6)),
        ((0, 1, 0), (2, 3, 7, 6)),
        ((0, -1, 0), (0, 4, 5, 1)),
        ((0, 0, 1), (4, 6, 7, 5)),
        ((0, 0, -1), (0, 1, 3, 2)),
    ]

    def box(center, size, color):
        corners = [
            (
                center[0] + (i & 1 and 0.5 or -0.5) * size[0],
                center[1] + (i & 2 and 0.5 or -0.5) * size[1],
                center[2] + (i & 4 and 0.5 or -0.5) * size[2],
            )
            for i in range(8)
        ]
        for normal, indices in sides:
            first = len(vertices)
            vertices.extend(corners[i] for i in indices)
            normals.extend([normal] * 4)
            uvs.extend([((color + 0.5) / 4, 0.5)] * 4)
            faces.extend([(first, first + 2, first + 1), (first, first + 3, first + 2)])

    box((0, 0.27, 0), (0.36, 0.46, 0.20), 0)  # blue shirt
    box((0, 0.04, 0), (0.34, 0.18, 0.24), 1)  # trousers/hips
    box((0, 0.54, 0.01), (0.10, 0.10, 0.10), 2)
    box((0, 0.69, 0.02), (0.21, 0.23, 0.20), 2)
    box((0, 0.795, 0.0), (0.22, 0.06, 0.21), 3)  # short hair
    for side in (-1, 1):
        box((side * 0.22, 0.30, 0.02), (0.12, 0.30, 0.13), 0)
        box((side * 0.22, 0.17, 0.19), (0.10, 0.10, 0.30), 0)
        box((side * 0.22, 0.18, 0.37), (0.10, 0.10, 0.09), 2)
        box((side * 0.095, -0.045, 0.21), (0.15, 0.15, 0.42), 1)
        box((side * 0.095, -0.29, 0.40), (0.13, 0.42, 0.14), 1)
        box((side * 0.095, -0.50, 0.46), (0.15, 0.10, 0.27), 3)
    return vertices, faces, normals, uvs


def mesh_text():
    vertices, faces, normals, uvs = geometry()

    def rows(values):
        return ",\n".join(";".join(f"{n:.6f}" for n in row) + ";" for row in values) + ";\n"

    def triangles():
        return ",\n".join("3;" + ",".join(map(str, row)) + ";" for row in faces) + ";\n"

    return (
        "xof 0303txt 0032\nMesh AKRSeatedDriver {\n"
        + str(len(vertices))
        + ";\n"
        + rows(vertices)
        + str(len(faces))
        + ";\n"
        + triangles()
        + "MeshNormals {\n"
        + str(len(normals))
        + ";\n"
        + rows(normals)
        + str(len(faces))
        + ";\n"
        + triangles()
        + "}\nMeshTextureCoords {\n"
        + str(len(uvs))
        + ";\n"
        + rows(uvs)
        + "}\n}\n"
    )


def palette():
    colors = [(48, 85, 116), (44, 51, 63), (190, 142, 104), (39, 31, 25)]

    def chunk(kind, data):
        return (
            struct.pack(">I", len(data))
            + kind
            + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
        )

    # Four 16-pixel bands avoid sampling neighbouring colors after filtering.
    row = bytes(v for c in colors for _ in range(16) for v in (*c, 255))
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 16, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress((b"\0" + row) * 16))
        + chunk(b"IEND", b"")
    )


def build():
    model = MEDIA / "models_X/AKR/SeatedDriver.x"
    texture = MEDIA / "textures/AKR/DriverPalette.png"
    model.parent.mkdir(parents=True, exist_ok=True)
    texture.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(mesh_text())
    texture.write_bytes(palette())
    print(f"Original seated mesh: {len(geometry()[0])} vertices, {len(geometry()[1])} triangles")


if __name__ == "__main__":
    build()
