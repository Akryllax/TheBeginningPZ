import math
import struct
from itertools import islice
from types import SimpleNamespace

import pytest

from observer.roads import RouteFailure, Search, cells_on_line, read_roads


def grid(roads, known=None, **kwargs):
    cells = {}
    for (x, y), cost in roads.items():
        cell = cells.setdefault((x // 256, y // 256), bytearray(65536))
        cell[y % 256 * 256 + x % 256] = cost
    source = SimpleNamespace(
        image=lambda x, y: cells.get((x, y), bytes(65536)), build_time=0, revision="test"
    )
    known = known if known is not None else {(x // 32 * 32, y // 32 * 32) for x, y in roads}
    return Search(source, known, **kwargs)


def point(id, x, y, **kwargs):
    return {"id": id, "label": id, "x": x, "y": y, "z": 0, "kind": "manual", **kwargs}


def test_paved_preference_and_dirt_alternative():
    roads = {(x, 10): 2 for x in range(2, 30)}
    roads.update({(x, 5): 1 for x in range(2, 30)})
    roads.update({(x, y): 1 for x in (2, 29) for y in range(5, 11)})
    search = grid(roads)
    path = search.path((2, 10), (29, 10))
    assert any(y == 5.5 for x, y in path)
    dirt = grid({(x, 10): 2 for x in range(2, 30)})
    assert dirt.path((2, 10), (29, 10))


def test_fog_gaps_cell_boundaries_and_no_diagonal_corner_cutting():
    road = {(x, 10): 1 for x in range(200, 301)}
    assert grid(road).path((200, 10), (300, 10))[-1] == (300.5, 10.5)
    hidden = grid(road, known={(192, 0), (224, 0), (288, 0)})
    with pytest.raises(RouteFailure, match="Known roads"):
        hidden.path((200, 10), (300, 10))
    with pytest.raises(RouteFailure):
        grid({(1, 1): 1, (2, 2): 1}).path((1, 1), (2, 2))
    assert {(0, 1), (1, 0)} <= set(cells_on_line((0.5, 0.5), (1.5, 1.5)))


def test_generate_turns_and_simplify_without_crossing_blocked_ground():
    road = {(x, y): 1 for x in range(5, 51) for y in range(8, 13)}
    road.update({(x, y): 1 for x in range(46, 51) for y in range(10, 71)})
    search = grid(road)
    route = search.route([point("a", 5.5, 10.5), point("b", 48.5, 70.5)])
    assert route["status"] == "ready"
    assert 2 < len(route["stops"]) < 10
    assert route["stops"][0]["id"] == "a" and route["stops"][-1]["id"] == "b"
    assert all(p["kind"] == "generated" for p in route["stops"][1:-1])
    assert all(search.line(a, b) for g in route["geometry"] for a, b in zip(g["points"], g["points"][1:]))
    assert route["road_distance"] > math.dist((5.5, 10.5), (48.5, 70.5))
    fixed = search.route([point("a", 5.5, 10.5), point("b", 48.5, 70.5)], turns=False)
    assert len(fixed["stops"]) == 2
    assert len(fixed["geometry"][0]["points"]) > 2


def test_access_preserves_pins_and_never_crosses_unknown_blocks():
    road = {(x, 40): 1 for x in range(10, 90)}
    search = grid(road, known={(x, y) for x in (0, 32, 64) for y in (0, 32)})
    route = search.route([point("a", 15, 10), point("b", 85, 40)])
    assert (route["stops"][0]["x"], route["stops"][0]["y"]) == (15, 10)
    assert route["geometry"][0]["kind"] == "access"
    assert 29 < route["access_distance"] < 32
    with pytest.raises(RouteFailure):
        grid(road).snap(point("unknown", 15, 10))
    with pytest.raises(RouteFailure):
        search.snap(point("upstairs", 15, 10, z=1))
    with pytest.raises(RouteFailure):
        grid(road, known={(0, 0), (0, 64)}).snap(point("gap", 15, 10))


def test_budget_is_bounded():
    search = grid({(x, y): 1 for x in range(100) for y in range(100)}, max_expansions=1)
    with pytest.raises(RouteFailure, match="limit"):
        search.path((0, 0), (99, 99))


def test_line_terminates_on_integer_endpoints_in_all_directions():
    for end in ((0, 1), (1, 0), (0, 0), (1, 1), (-2, 3), (3, -2), (-2, -2)):
        for start in ((0.5, 0.5), (0, 0)):
            for size in (1, 32):
                cells = list(
                    islice(
                        cells_on_line(tuple(v * size for v in start), tuple(v * size for v in end), size), 100
                    )
                )
                assert len(cells) < 100
                assert cells[-1] == end


def test_binary_version_coordinates_road_filter_and_truncation(tmp_path):
    strings = ["Polygon", "highway", "secondary", "trail", "railway", "rail"]
    data = bytearray(b"IGMB" + struct.pack("<5i", 2, 256, 1, 1, len(strings)))
    for text in strings:
        payload = text.encode()
        data += struct.pack("<h", len(payload)) + payload
    data += struct.pack("<3i", 42, 38, 3)
    for key, value in ((1, 2), (1, 3), (4, 5)):
        data += struct.pack("<hBh8hB2h", 0, 1, 4, 0, 0, 8, 0, 8, 200, 0, 200, 1, key, value)
    path = tmp_path / "worldmap.xml.bin"
    path.write_bytes(data)
    cells, _ = read_roads(path)
    assert len(cells[42, 38]) == 1
    assert cells[42, 38][0][1][0][0] == (10752, 9728)
    data[4:8] = struct.pack("<i", 1)
    path.write_bytes(data)
    with pytest.raises(ValueError, match="256-tile"):
        read_roads(path)
    path.write_bytes(b"IGMB")
    with pytest.raises(struct.error):
        read_roads(path)


def test_surface_mask_blocks_water_structures_and_fields_but_allows_mapped_bridge(tmp_path, monkeypatch):
    from PIL import Image

    from observer import roads as module
    from observer.terrain import DIRT, FLOOR, PALETTE, PAVEMENT, STRUCTURE, WATER

    feature = ("Polygon", (((0, 0), (20, 0), (20, 20), (0, 20)),), 1)
    monkeypatch.setattr(module, "read_roads", lambda p: ({(0, 0): [feature]}, "test"))
    image = Image.new("RGB", (256, 256), PALETTE[PAVEMENT])
    for x, kind in ((1, WATER), (2, STRUCTURE), (3, FLOOR), (4, DIRT)):
        image.putpixel((x, 5), tuple(bytes.fromhex(PALETTE[kind][1:])))
    monkeypatch.setattr(module, "read_header", lambda *a: {})
    monkeypatch.setattr(module, "ground_image", lambda *a: image)
    (tmp_path / "0_0.lotheader").write_bytes(b"fixture")
    (tmp_path / "world_0_0.lotpack").write_bytes(b"fixture")
    mask = module.Roads(tmp_path, tmp_path / "cache").image(0, 0)
    assert [mask[5 * 256 + x] for x in range(1, 6)] == [0, 0, 1, 2, 1]
    assert mask[5 * 256 + 50] == 0  # Pavement outside a mapped road is not a shortcut.
