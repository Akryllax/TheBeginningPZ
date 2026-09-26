"""Private offline grid format, invalidation and conservative clearance tests."""
import math
import os
from pathlib import Path
import struct
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from scenario_navigation import NavigationGrid, PROFILE, SCHEMA, clearance_scores, source_identity, write_grid
import scenario_navigation


def grid(tmp_path, bounds, costs):
    x0, y0, x1, y1 = bounds
    identity = {'schema': SCHEMA, 'profile': PROFILE, 'bounds': list(bounds), 'sources': {}}
    return write_grid(tmp_path, bounds, bytearray(costs), clearance_scores(costs, x1-x0, y1-y0),
                      identity, 'a'*64)


def test_navigation_chunk_roundtrip_across_cell_boundaries_and_blocked_exterior(tmp_path):
    costs = [10] * 36
    costs[2 * 6 + 3] = 0
    original = grid(tmp_path, (254, 254, 260, 260), costs)
    restored = NavigationGrid(tmp_path, original.manifest)
    assert len(restored.chunks) == 4
    for y in range(254, 260):
        for x in range(254, 260):
            assert restored.cost(x, y) == costs[(y-254)*6+x-254]
    assert restored.sample(253, 255) == (0, 0)
    assert restored.sample(260, 255) == (0, 0)
    assert restored.sample(257, 256) == (0, 0)


def test_navigation_tampering_and_schema_mismatch_fail_closed(tmp_path):
    first = grid(tmp_path, (0, 0, 6, 6), [10]*36)
    chunk = tmp_path / first.manifest['chunks'][0]['file']
    data = bytearray(chunk.read_bytes());data[-1] ^= 1;chunk.write_bytes(data)
    with pytest.raises(ValueError, match='checksum'):
        NavigationGrid(tmp_path, first.manifest).sample(0, 0)
    with pytest.raises(ValueError, match='schema'):
        NavigationGrid(tmp_path, {**first.manifest, 'schema': 999})


def test_source_identity_detects_same_size_map_changes_and_tile_definition_changes(tmp_path):
    root = tmp_path/'map';root.mkdir()
    (root/'worldmap.xml.bin').write_bytes(b'first')
    definitions = tmp_path/'newtiledefinitions.tiles.txt';definitions.write_text('one')
    (tmp_path/'newtiledefinitions.tiles').write_bytes(b'compiled')
    first, _ = source_identity(root, definitions, (0, 0, 8, 8))
    (root/'worldmap.xml.bin').write_bytes(b'other')
    second, _ = source_identity(root, definitions, (0, 0, 8, 8))
    assert first != second
    definitions.write_text('two')
    third, _ = source_identity(root, definitions, (0, 0, 8, 8))
    assert second != third
    (root/'0_0.lotheader').write_bytes(b'newly present source')
    fourth, _ = source_identity(root, definitions, (0, 0, 8, 8))
    assert third != fourth


def test_rebake_refreshes_prerequisite_road_mask_when_pack_size_and_mtime_are_preserved(tmp_path):
    """Exercise both caches with real tiny map records, not a mocked mask."""
    root = tmp_path / 'map';root.mkdir()
    strings = ['Polygon', 'highway', 'secondary']
    worldmap = bytearray(b'IGMB' + struct.pack('<5i', 2, 256, 1, 1, len(strings)))
    for value in strings:
        raw = value.encode()
        worldmap.extend(struct.pack('<h', len(raw)) + raw)
    worldmap.extend(struct.pack('<3i', 0, 0, 1))
    worldmap.extend(struct.pack('<hBh8hB2h', 0, 1, 4, 0, 0, 7, 0, 7, 7, 0, 7, 1, 1, 2))
    (root / 'worldmap.xml.bin').write_bytes(worldmap)
    names = ['blends_natural_01_0', 'floors_exterior_street_01_0']
    header = b'LOTH' + struct.pack('<2i', 1, len(names))
    header += ''.join(name + '\n' for name in names).encode()
    header += struct.pack('<6i', 8, 8, 0, 0, 0, 0) + bytes(1024)
    (root / '0_0.lotheader').write_bytes(header)
    offset = 12 + 32 * 32 * 8
    pack = bytearray(b'LOTP' + struct.pack('<2i', 1, 0) + bytes(32 * 32 * 8))
    struct.pack_into('<i', pack, 12, offset)
    pack.extend(struct.pack('<5i', 2, -1, 0, -1, 63))
    pack_path = root / 'world_0_0.lotpack'
    pack_path.write_bytes(pack)
    definitions = tmp_path / 'newtiledefinitions.tiles.txt'
    definitions.write_text(''.join(
        f'// {name}\ntile\n{{\nFloorMaterial = {material}\nsolidfloor =\n}}\n'
        for name, material in zip(names, ['Grass', 'Road_06'])))
    definitions.with_suffix('').write_bytes(b'fixture compiled definitions')
    args = root, definitions, (0, 0, 8, 8), tmp_path / 'navigation', tmp_path / 'roads'
    first, timing = scenario_navigation.bake_navigation(*args)
    assert not timing['cache_hit'] and first.cost(0, 0) == 0

    before = pack_path.stat()
    struct.pack_into('<i', pack, offset + 8, 1)  # Ground becomes asphalt.
    pack_path.write_bytes(pack)
    os.utime(pack_path, ns=(before.st_atime_ns, before.st_mtime_ns))
    assert (pack_path.stat().st_size, pack_path.stat().st_mtime_ns) == (before.st_size, before.st_mtime_ns)
    second, timing = scenario_navigation.bake_navigation(*args)
    assert not timing['cache_hit']
    assert second.manifest['identity'] != first.manifest['identity']
    assert second.cost(0, 0) == 10
    restored, timing = scenario_navigation.bake_navigation(*args)
    assert timing['cache_hit'] and restored.cost(0, 0) == 10


def test_clearance_never_overstates_distance_to_blocked_tile_boundary():
    width, height = 11, 9
    costs = [10 if (x*3+y*7)%19 else 0 for y in range(height) for x in range(width)]
    score = clearance_scores(costs, width, height)
    blocked = [(x,y) for y in range(-1,height+1) for x in range(-1,width+1)
               if not (0<=x<width and 0<=y<height) or not costs[y*width+x]]
    for y in range(height):
        for x in range(width):
            px,py = x+0.5,y+0.5
            distance = min(math.hypot(max(bx-px,0,px-bx-1),max(by-py,0,py-by-1))
                           for bx,by in blocked)
            assert score[y*width+x]/4 <= distance+1e-9


def test_baked_scores_reject_narrow_roads_and_swept_diagonal_corner(tmp_path):
    bounds=(-4,-6,16,10);width=bounds[2]-bounds[0]
    road=lambda x,y: 0<=y<6 or 6<=x<12
    costs=[10 if road(x,y) else 0 for y in range(bounds[1],bounds[3]) for x in range(bounds[0],bounds[2])]
    baked=grid(tmp_path,bounds,costs)
    assert baked.center_clear(4,3)
    assert not baked.center_clear(4,0)
    assert not baked.segment_clear((4.5,3.5),(8.5,-0.5))
    assert baked.segment_clear((4.5,3.5),(8.5,3.5))
    assert baked.segment_clear((8.5,3.5),(8.5,-0.5))
    assert baked.edge_cost((4.5,3.5),(8.5,-0.5)) is None


def test_positive_edge_scores_prefer_roomier_interior_without_relaxing_clearance(tmp_path):
    baked=grid(tmp_path, (0,0,20,12), [10]*240)
    narrow=baked.edge_cost((4.5,2.5),(14.5,2.5))
    roomy=baked.edge_cost((4.5,5.5),(14.5,5.5))
    assert 10 <= roomy < narrow <= 11.5
    assert baked.edge_cost((4.5,0.5),(14.5,0.5)) is None


def test_cached_open_does_not_parse_game_map_again(tmp_path, monkeypatch):
    prepared=grid(tmp_path, (0,0,6,6), [10]*36)
    monkeypatch.setattr(scenario_navigation, 'source_identity', lambda *args: ('a'*64, prepared.manifest))
    def forbidden(*args):
        raise AssertionError('Map parsed on a valid cache hit')
    monkeypatch.setattr(scenario_navigation, 'RoadSurface', forbidden)
    restored, timings=scenario_navigation.bake_navigation(tmp_path,tmp_path/'defs',(0,0,6,6),tmp_path,tmp_path)
    assert timings['cache_hit'] and restored.cost(3,3)==10


def test_chunk_boundary_has_no_artificial_wall_and_keeps_cross_boundary_obstacles(tmp_path):
    costs=[10]*576
    open_grid=grid(tmp_path/'open',(248,248,272,272),costs)
    assert open_grid.segment_clear((251.5,259.5),(268.5,259.5))
    costs[(260-248)*24+256-248]=0
    blocked_grid=grid(tmp_path/'blocked',(248,248,272,272),costs)
    assert not blocked_grid.segment_clear((251.5,259.5),(268.5,259.5))
