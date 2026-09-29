"""Pure map-decoding and vehicle-footprint regressions; no game assets needed."""

import math
from pathlib import Path
import struct
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from scenario_roads import (
    RoadSurface,
    corridor_cells,
    explicit_road_floor,
    ground_stacks,
    probe_waypoints,
    tile_properties,
)


def test_explicit_supplement_resolves_overlay_but_ambiguous_and_unknown_tiles_stay_blocked(
    tmp_path,
):
    (tmp_path / "worldmap.xml.bin").write_bytes(
        b"IGMB" + struct.pack("<8i", 2, 256, 1, 1, 0, 0, 0, 0)
    )
    base = tmp_path / "base.tiles.txt"
    extra = tmp_path / "erosion.tiles.txt"
    base.write_text(
        "// asphalt\ntile\n{\nFloorMaterial = Road_06\nsolidfloor =\n}\n"
        "// ambiguous\ntile\n{\nFloorOverlay =\n}\n"
    )
    extra.write_text("// crack\ntile\n{\nFloorOverlay =\n}\n// ambiguous\ntile\n{\nsolid =\n}\n")
    ordinary = RoadSurface(tmp_path, tmp_path / "cache", base)
    assert not explicit_road_floor(["asphalt", "crack"], ordinary.properties)
    supplied = RoadSurface(tmp_path, tmp_path / "cache", base, [extra, extra])
    assert explicit_road_floor(["asphalt", "crack"], supplied.properties)
    for overlay in ["ambiguous", "unlisted"]:
        assert not explicit_road_floor(["asphalt", overlay], supplied.properties)


@pytest.mark.parametrize(
    "rows",
    [
        [{"x": 0, "y": 0}, {"x": 1, "y": 0}],
        [{"x": 0, "y": 0}, {"x": 2, "y": 0, "z": 1}],
        [{"x": 60000, "y": 0}, {"x": 60002, "y": 0}],
        [{"x": -20001, "y": 0}, {"x": -19998, "y": 0}],
        [{"x": 0, "y": 0}, {"x": 0.2, "y": 0}, {"x": 3, "y": 0}],
        [{"x": 0, "y": 0}, {"x": 0.4, "y": 0}, {"x": 3, "y": 0}],
        [{"x": 0, "y": 0}, {"x": 41, "y": 0}],
        [{"x": 0, "y": 0}, {"x": 61, "y": 0}],
        [{"x": True, "y": 0}, {"x": 3, "y": 0}],
    ],
)
def test_probe_request_matches_runtime_bounds_and_rejects_silent_floor_flattening(rows):
    with pytest.raises(ValueError):
        probe_waypoints(rows)


def test_probe_request_preserves_valid_points_and_accepts_explicit_ground_level():
    assert probe_waypoints([{"x": -20000, "y": 60000, "z": 0}, {"x": -19998, "y": 60000}]) == [
        (-20000, 60000),
        (-19998, 60000),
    ]


def test_tile_definitions_keep_empty_collision_and_floor_flags():
    props = tile_properties("""
    // original_test_asphalt_0
    tile
    {
        FloorMaterial = Road_06
        solidfloor =
        exterior =
    }
    // original_test_overlay_0
    tile
    {
        FloorMaterial = Road_03
        FloorOverlay =
    }
    """)
    assert props["original_test_asphalt_0"]["solidfloor"] == ""
    assert explicit_road_floor(["original_test_asphalt_0"], props)
    assert not explicit_road_floor(["original_test_asphalt_0", "original_test_overlay_0"], props)


def test_asphalt_under_sidewalk_or_curb_is_not_a_vehicle_corridor():
    props = {
        "asphalt": {"FloorMaterial": "Road_06", "solidfloor": ""},
        "sidewalk": {"solidfloor": ""},
        "traffic_line": {"FloorOverlay": ""},
        "street_curbs_01_test": {"FloorOverlay": ""},
        "wall": {"collideW": ""},
    }
    assert explicit_road_floor(["asphalt", "traffic_line"], props)
    for overlay in ["sidewalk", "street_curbs_01_test", "wall", "unknown"]:
        assert not explicit_road_floor(["asphalt", overlay], props)


@pytest.mark.parametrize(
    "obstacle",
    [
        {"PhysicsShape": shape}
        for shape in ["Tree", "Solid", "WallN", "WallS", "WallE", "WallW", "", "Unknown"]
    ]
    + [{"PhysicsMesh": mesh} for mesh in ["Base.Pole", "Floor", "", "Unknown"]]
    + [{"StopCar": ""}, {"HitByCar": ""}],
)
def test_walkable_physical_object_blocks_road_and_shoulder_routes(obstacle):
    props = {
        "road": {"FloorMaterial": "Road_06", "solidfloor": ""},
        "grass": {"FloorMaterial": "Grass_Dark", "solidfloor": ""},
        "pole": obstacle,
    }
    assert not explicit_road_floor(["road", "pole"], props)
    assert not explicit_road_floor(["grass", "pole"], props, {"Grass_Dark"})


def test_floor_exemption_does_not_hide_mesh_or_car_collision():
    props = {"road": {"FloorMaterial": "Road_06", "solidfloor": "", "PhysicsShape": "Floor"}}
    assert explicit_road_floor(["road"], props)
    props["road"]["PhysicsMesh"] = "Base.Pole"
    assert not explicit_road_floor(["road"], props)


@pytest.mark.parametrize(
    "sprite",
    [
        "lighting_outdoor_01_0",
        "recreational_sports_01_19",
        "recreational_sports_01_21",
        "recreational_sports_01_32",
    ],
)
def test_legacy_native_columns_block_swept_road_footprint(sprite):
    props = {"road": {"FloorMaterial": "Road_06", "solidfloor": ""}, sprite: {}}
    assert not explicit_road_floor(["road", sprite], props)
    props[sprite]["MoveType"] = "WallObject"
    assert explicit_road_floor(["road", sprite], props)
    props[sprite]["PhysicsShape"] = "Tree"
    assert not explicit_road_floor(["road", sprite], props)


def test_pole_outside_centerline_still_rejects_the_swept_vehicle_corridor():
    props = {
        "road": {"FloorMaterial": "Road_06", "solidfloor": ""},
        "pole": {"PhysicsShape": "Tree", "StopCar": "", "HitByCar": ""},
    }
    # The car center never occupies (1, 5), but its edge would hit this pole.
    corridor = corridor_cells([(0.5, 0.5), (0.5, 10.5)], radius=1)
    assert all(explicit_road_floor(["road"], props) for y in range(11))
    assert any(
        not explicit_road_floor(["road", "pole"] if tile == (1, 5) else ["road"], props)
        for tile in corridor
    )


def test_ground_stacks_decode_skip_runs_and_do_not_confuse_basement_with_ground():
    offset = 12 + 32 * 32 * 8
    raw = bytearray(b"LOTP" + struct.pack("<ii", 1, 0) + bytes(32 * 32 * 8))
    struct.pack_into("<i", raw, 12, offset)
    raw.extend(struct.pack("<ii", -1, 64))  # Entire basement is empty.
    raw.extend(struct.pack("<iiii", 3, -1, 0, 1))  # Two sprites at local ground (0,0).
    raw.extend(struct.pack("<ii", -1, 63))
    header = {"min_z": -1, "max_z": 0, "names": ["asphalt", "traffic_line"]}
    assert ground_stacks(bytes(raw), header) == {(0, 0): ("asphalt", "traffic_line")}
    with pytest.raises(ValueError, match="tile reference"):
        ground_stacks(bytes(raw), {**header, "names": ["asphalt"]})


def test_swept_footprint_covers_off_center_obstacles_and_preserves_corner():
    corridor = set(corridor_cells([(0.5, 0.5), (0.5, 10.5), (10.5, 10.5)], radius=1))
    assert (1, 5) in corridor  # Vehicle edge, away from its centerline.
    assert (-1, -1) in corridor  # Starting footprint is checked too.
    assert (5, 5) not in corridor  # No straight-line shortcut across the L bend.
    assert (10, 11) in corridor  # End footprint remains covered.


def test_diagonal_across_road_corner_fails_even_when_entire_centerline_is_asphalt():
    # Two perpendicular six-tile roads: every point on the diagonal lies on
    # asphalt, but a car-sized disk cuts the inside sidewalk corner.
    road = lambda x, y: 0 <= y < 6 or 6 <= x < 12
    start, end = (4.5, 3.5), (8.5, -0.5)
    from observer.roads import cells_on_line

    assert all(road(x, y) for x, y in cells_on_line(start, end))
    assert any(not road(x, y) for x, y in corridor_cells([start, end], radius=2.25))
    interior = [(4.5, 3.5), (8.5, 3.5), (8.5, -0.5)]
    assert all(road(x, y) for x, y in corridor_cells(interior, radius=2.25))


@pytest.mark.parametrize(
    "points", [[], [(1, 2)], [(math.nan, 2), (3, 4)], [(0, 0), (0, 0)], [(0, 0), (2000, 0)]]
)
def test_corridor_rejects_unbounded_or_invalid_routes(points):
    with pytest.raises(ValueError):
        corridor_cells(points)


@pytest.mark.parametrize("option", ["radius", "step"])
@pytest.mark.parametrize("value", [math.nan, math.inf, -math.inf])
def test_corridor_rejects_nonfinite_geometry_parameters(option, value):
    with pytest.raises(ValueError, match="bounded corridor"):
        corridor_cells([(0, 0), (2, 0)], **{option: value})


@pytest.mark.parametrize("step", [1e-12, 5e-324])
def test_tiny_finite_step_rejected_before_any_disk_sampling(monkeypatch, step):
    import scenario_roads

    def forbidden(*args):
        raise AssertionError("Sampling started before budget validation")

    monkeypatch.setattr(scenario_roads, "disk_cells", forbidden)
    with pytest.raises(ValueError, match="sample budget"):
        corridor_cells([(0, 0), (2, 0)], step=step)


def test_sample_budget_applies_to_entire_route_before_sampling(monkeypatch):
    import scenario_roads

    def forbidden(*args):
        raise AssertionError("Sampling started before the whole route was bounded")

    monkeypatch.setattr(scenario_roads, "disk_cells", forbidden)
    points = [(i % 2 * 0.5, 0) for i in range(128)]
    with pytest.raises(ValueError, match="sample budget"):
        corridor_cells(points, step=0.001)
