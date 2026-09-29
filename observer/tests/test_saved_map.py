import io
import sqlite3
import struct
import zipfile

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from observer import terrain as terrain_module
from observer.models import Bounds
from observer.saved_api import create_saved_app
from observer.saved_map import SavedMap
from observer.terrain import (
    DIRT,
    FLOOR,
    GROUND,
    PALETTE,
    PAVEMENT,
    STRUCTURE,
    UNKNOWN,
    VEGETATION,
    WATER,
    Terrain,
    classify,
    ground_image,
    read_header,
)


def i(n):
    return struct.pack("<i", n)


def header_fixture():
    names = ["blends_street_01_1", "vegetation_foliage_01_1", "walls_exterior_01_1"]
    data = (
        b"LOTH"
        + i(1)
        + i(len(names))
        + b"".join(n.encode() + b"\n" for n in names)
        + i(8)
        + i(8)
        + i(0)
        + i(0)
    )
    rooms = [
        ("library", 1, 2, 4, 4),
        ("garage", 40, 2, 4, 4),
        ("bedroom", 40, 10, 4, 4),
        ("toolstore", 20, 20, 4, 4),
    ]
    data += i(len(rooms))
    for name, x, y, w, h in rooms:
        data += name.encode() + b"\n" + i(0) + i(1) + b"".join(i(v) for v in [x, y, w, h]) + i(0)
    data += i(3) + i(1) + i(0) + i(2) + i(1) + i(2) + i(1) + i(3) + bytes(1024)
    return data


def lotpack_fixture():
    prefix = bytearray(b"LOTP" + i(1) + i(1024) + bytes(8192))
    # Deliberately asymmetric chunk and tile coordinates reveal transposition.
    pos = len(prefix)
    struct.pack_into("<ii", prefix, 12 + (1 * 32 + 2) * 8, pos, 0)
    body = i(-1) + i(10)  # skip x=0,y=0 through x=1,y=1
    body += i(2) + i(0) + i(1)  # x=1,y=2 is vegetation
    body += i(-1) + i(53)
    return bytes(prefix) + body


def setup_sources(tmp_path):
    save = tmp_path / "save"
    save.mkdir()
    db = sqlite3.connect(save / "players.db")
    db.execute("CREATE TABLE networkPlayers(id,username,name,x,y,z,isDead)")
    db.execute('INSERT INTO networkPlayers VALUES(1,"akryllax","Tommy",4,5,0,0)')
    db.commit()
    db.close()
    db = sqlite3.connect(save / "vehicles.db")
    db.execute("CREATE TABLE vehicles(id,x,y,worldversion,data)")
    db.executemany(
        "INSERT INTO vehicles VALUES(?,?,?,?,?)",
        [(2, 3.0, 3.0, 249, b""), (3, 40.0, 3.0, 249, b"")],
    )
    db.commit()
    db.close()
    maps = tmp_path / "maps"
    maps.mkdir()
    (maps / "0_0.lotheader").write_bytes(header_fixture())
    (maps / "world_0_0.lotpack").write_bytes(lotpack_fixture())
    visited = save / "map_visited_server"
    visited.mkdir()
    raw = struct.pack(">i", 249) + bytes([3]) + bytes(15)
    with zipfile.ZipFile(visited / "akryllax.zip", "w") as z:
        z.writestr("akryllax", raw)
    (save / "servermap_symbols.bin").write_bytes(
        b"WMSY" + struct.pack(">ii", 249, 2) + b"\0" + struct.pack(">i", 0)
    )
    return save, maps


def test_header_and_lotpack_orientation_and_skip():
    header = read_header(header_fixture(), 0, 0)
    assert header["rooms"][0]["rects"] == [[1, 2, 4, 4]]
    image = ground_image(lotpack_fixture(), header)
    assert image.getpixel((9, 18)) == (50, 110, 67)
    assert image.getpixel((18, 9)) != (50, 110, 67)


@pytest.mark.parametrize(
    ("names", "expected"),
    [
        (["blends_natural_02_0"], WATER),
        (["blends_natural_02_5"], WATER),
        (["blends_natural_02_6"], WATER),
        (["blends_natural_02_7"], WATER),
        (["blends_natural_02_9", "blends_natural_01_16"], WATER),
        (["blends_natural_02_16"], GROUND),  # Unused sprite, not the water material.
        (["blends_natural_01_64", "blends_natural_01_20"], DIRT),
        (["blends_natural_01_69"], DIRT),
        (["blends_natural_01_71"], DIRT),
        (["blends_natural_01_79"], DIRT),
        (["blends_natural_01_103"], DIRT),
        (["blends_street_01_53", "blends_natural_01_75"], DIRT),
        (["floors_exterior_natural_01_13"], DIRT),
        (["blends_natural_01_64", "vegetation_foliage_01_14"], VEGETATION),
        (["blends_natural_01_0"], GROUND),  # Sand and grass are not dirt roads.
        (["blends_natural_01_48"], GROUND),
        (["blends_natural_01_80"], GROUND),
        (["floors_exterior_natural_01_0"], GROUND),
        (["floors_exterior_natural_01_24"], GROUND),
        (["floors_exterior_street_01_0"], PAVEMENT),
        (["blends_natural_02_0", "floors_exterior_street_01_0"], PAVEMENT),
        (["blends_natural_02_0", "floors_interior_tilesandwood_01_0"], FLOOR),
        (["blends_natural_02_0", "walls_exterior_01_1"], STRUCTURE),
    ],
)
def test_installed_water_and_rural_surfaces(names, expected):
    # Includes actual stacks from explored tiles near Muldraugh. Test both the
    # classifier and the lotpack renderer, whose priority previously diverged.
    assert classify(names) == expected
    assert classify(list(reversed(names))) == expected
    prefix = bytearray(b"LOTP" + i(1) + i(1024) + bytes(8192))
    struct.pack_into("<ii", prefix, 12, len(prefix), 0)
    body = i(len(names) + 1) + i(0) + b"".join(i(n) for n in range(len(names))) + i(-1) + i(63)
    image = ground_image(bytes(prefix) + body, {"names": names, "min_z": 0, "max_z": 0})
    assert image.getpixel((0, 0)) == tuple(bytes.fromhex(PALETTE[expected][1:]))


@pytest.mark.parametrize("zoom", [-4, -3, -2, -1])
@pytest.mark.parametrize("kind", [PAVEMENT, DIRT, WATER])
def test_zoomed_out_roads_and_water_remain_visible_without_revealing_neighbors(
    tmp_path, zoom, kind
):
    terrain = Terrain(tmp_path, tmp_path / "cache")
    # These one-tile-wide features fall between the old nearest-neighbor samples
    # at every overview zoom. The whole neighboring block is deliberately hidden.
    cell = Image.new("RGB", (256, 256), PALETTE[GROUND])
    cell.paste(PALETTE[kind], (0, 0, 32, 1))
    cell.paste(PALETTE[kind], (32, 0, 64, 32))
    terrain.cell_image = lambda cx, cy: cell
    image = Image.open(io.BytesIO(terrain.tile(zoom, 0, 0, {(0, 0): 3}, 1)))
    ground = tuple(bytes.fromhex(PALETTE[GROUND][1:]))
    unknown = tuple(bytes.fromhex(UNKNOWN[1:]))
    width = int(32 * 2**zoom)
    assert all(image.getpixel((x, 0)) != ground for x in range(width))
    assert all(image.getpixel((x, 1)) == ground for x in range(width))
    assert image.getpixel((width, 0)) == unknown
    assert image.getpixel((0, width)) == unknown
    hidden = Image.open(io.BytesIO(terrain.tile(zoom, 0, 0, {}, 2)))
    assert hidden.getcolors() == [(256 * 256, unknown)]


def test_renderer_update_invalidates_disk_and_browser_tiles(tmp_path, monkeypatch):
    _, maps = setup_sources(tmp_path)
    cache = tmp_path / "cache"
    before = Terrain(maps, cache)
    assert before.cell_image(0, 0).getpixel((9, 18)) == (50, 110, 67)
    colors = list(PALETTE)
    colors[VEGETATION] = "#010203"
    monkeypatch.setattr(terrain_module, "PALETTE", colors)
    monkeypatch.setattr(terrain_module, "RENDER_VERSION", terrain_module.RENDER_VERSION + 1)
    after = Terrain(maps, cache)
    assert after.cell_image(0, 0).getpixel((9, 18)) == (1, 2, 3)
    assert after.revision != before.revision
    assert len(list(cache.glob("*.png"))) == 2


def test_search_and_tiles_enforce_knowledge(tmp_path):
    save, maps = setup_sources(tmp_path)
    state = SavedMap(
        tmp_path / "data", save, maps, bounds=Bounds(min_x=0, min_y=0, max_x=0, max_y=0)
    )
    state.poll()
    known = state.known()
    assert known == {(0, 0): 3}
    assert state.terrain.search("library", known, 0, 0)[0]["label"] == "Library"
    assert state.terrain.search("garage", known, 0, 0) == []
    assert state.terrain.search("builder", known, 0, 0)[0]["category"] == "hardware"
    assert state.terrain.search("garage", {(32, 0): 3}, 0, 0)[0]["label"] == "Residential garage"
    # Spatial masking is independent of the binary-classification tests.
    state.publish(
        "vehicles",
        [{"id": 2, "x": 3, "y": 3, "hotwired": True}, {"id": 3, "x": 40, "y": 3, "wreck": True}],
        1,
        1,
    )
    assert state.features((0, 0, 64, 64))["vehicles"][0]["id"] == 2
    assert len(state.features((0, 0, 64, 64))["vehicles"]) == 1
    assert state.features((0, 0, 64, 64), "eric")["vehicles"] == []
    payload = state.terrain.tile(0, 0, 0, known, 1)
    image = Image.open(io.BytesIO(payload))
    assert image.getpixel((9, 18)) == (50, 110, 67)
    assert image.getpixel((40, 3)) == tuple(bytes.fromhex(UNKNOWN[1:]))
    # Coarse tiles must not blend hidden geometry into visible pixels.
    coarse = Image.open(io.BytesIO(state.terrain.tile(-4, 0, 0, known, 1)))
    assert coarse.getpixel((2, 0)) == tuple(bytes.fromhex(UNKNOWN[1:]))
    state.close()


def test_stale_sources_locked_db_and_no_false_freshness(tmp_path):
    save, maps = setup_sources(tmp_path)
    state = SavedMap(
        tmp_path / "data", save, maps, bounds=Bounds(min_x=0, min_y=0, max_x=0, max_y=0)
    )
    state.poll()
    before = state.status()
    revision = state.revision
    state.poll()
    assert state.revision == revision
    assert (
        state.status()["sources"]["players"]["modified_at"]
        == before["sources"]["players"]["modified_at"]
    )
    lock = sqlite3.connect(save / "players.db")
    lock.execute("BEGIN EXCLUSIVE")
    state.poll()
    assert state.status()["sources"]["players"]["error"]
    assert state.status()["players"][0]["name"] == "akryllax"
    lock.rollback()
    lock.close()
    state.poll()
    assert state.status()["sources"]["players"]["error"] is None
    (save / "map_visited_server/akryllax.zip").write_bytes(b"partial")
    state.poll()
    assert state.known() == {(0, 0): 3}
    assert state.status()["sources"]["coverage:akryllax"]["error"]
    state.close()


def test_save_markers_override_old_public_snapshot(tmp_path):
    save, maps = setup_sources(tmp_path)
    state = SavedMap(
        tmp_path / "data", save, maps, bounds=Bounds(min_x=0, min_y=0, max_x=0, max_y=0)
    )
    state.publish("markers", [{"id": "old", "public": True}], 1, 1)
    state.poll()
    assert state.status()["markers"] == []
    state.close()


def test_saved_api_and_persistence(tmp_path, monkeypatch):
    save, maps = setup_sources(tmp_path)
    monkeypatch.setenv("OBSERVER_MAPS", str(maps))
    app = create_saved_app(data=tmp_path / "data", save=save, polling=False)
    state = app.state.saved_map
    state.bounds = Bounds(min_x=0, min_y=0, max_x=0, max_y=0)
    state.poll()
    with TestClient(app) as client:
        assert client.get("/healthz").json()["mode"] == "saved_map"
        assert "online" not in client.get("/api/v1/world").json()
        assert client.get("/api/v1/places/search?q=library&x=4&y=5").json()["results"]
        assert not client.get("/api/v1/places/search?q=library&x=4&y=5&observer=eric").json()[
            "results"
        ]
        assert client.get("/api/v1/map/tiles/0/0/0.png").headers["content-type"] == "image/png"
        assert client.get("/api/v1/map/tiles/99/0/0.png").status_code == 422
        assert client.get("/downloads/ZomboidObserver.zip").status_code == 404
        assert client.get("/api/v1/elements/vehicle:2").status_code == 404
        assert client.post("/api/v1/map/features?x=0&y=0").status_code in (404, 405)
    reopened = SavedMap(
        tmp_path / "data", save, maps, bounds=Bounds(min_x=0, min_y=0, max_x=0, max_y=0)
    )
    assert reopened.known() == {(0, 0): 3}
    assert reopened.status()["players"][0]["name"] == "akryllax"
    reopened.close()


def test_search_merges_overlapping_floors_without_revealing_hidden_rooms(tmp_path):
    from observer.terrain import Terrain

    terrain = Terrain(tmp_path, tmp_path / "cache")
    terrain.places = {
        (0, 0): [
            {
                "id": "a",
                "label": "Police station",
                "category": "police",
                "z": 0,
                "rects": [[1, 1, 5, 5], [40, 40, 5, 5]],
            },
            {
                "id": "b",
                "label": "Police station",
                "category": "police",
                "z": -1,
                "rects": [[2, 2, 5, 5]],
            },
        ]
    }
    found = terrain.search("police", {(0, 0): 3}, 0, 0)
    assert len(found) == 1 and found[0]["z"] == 0
    assert all(r[0] < 32 and r[1] < 32 for r in found[0]["rects"])
