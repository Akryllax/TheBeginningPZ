#!/usr/bin/env python3
"""Extract a bounded original scenario index from the installed game's map data."""

from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "observer"))
from observer.terrain import read_header  # noqa: E402 - project-local observer path added above
from scenario_roads import RoadSurface, corridor_cells, probe_waypoints  # noqa: E402
from scenario_navigation import bake_navigation, benchmark_queries, source_identity  # noqa: E402

BOUNDS = (10400, 9400, 11200, 10752)
ROAD_RADIUS = 2.25
ROAD_SAMPLE_STEP = 0.25
ROOM_ROLES = {
    "home": {"livingroom"},
    "shop": {
        "grocery",
        "grocerystorage",
        "conveniencestore",
        "gigamart",
        "cornerstore",
        "supermarket",
    },
    "work": {
        "office",
        "restaurant",
        "restaurantkitchen",
        "warehouse",
        "factory",
        "store",
        "gasstore",
    },
    "mechanic": {"mechanic", "mechanicstorage", "autoshop", "carrepair"},
    "police": {"police", "policeoffice", "policestorage"},
    "clinic": {"medical", "pharmacy", "hospitalroom", "hospital", "clinic"},
}


def navigation():
    return bake_navigation(
        ROOT / "data/game-files/media/maps/Muldraugh, KY",
        ROOT / "data/game-files/media/newtiledefinitions.tiles.txt",
        BOUNDS,
        ROOT / "artifacts/scenario-map/navigation",
        ROOT / ".tooling/scenario-roads",
    )


def probe_route(request: Path):
    """Prepare ignored road evidence; the live probe must validate loaded tiles."""
    config = json.loads(request.read_text())
    points = probe_waypoints(config.get("waypoints"))
    distances = [math.dist(a, b) for a, b in zip(points, points[1:])]
    source = ROOT / "data/game-files/media/maps/Muldraugh, KY"
    definitions = ROOT / "data/game-files/media/newtiledefinitions.tiles.txt"
    radius, step = 2.25, 0.25
    cells = corridor_cells(points, radius, step)
    bounds = (
        min(x for x, y in cells),
        min(y for x, y in cells),
        max(x for x, y in cells) + 1,
        max(y for x, y in cells) + 1,
    )
    token, _ = source_identity(source, definitions, bounds)
    surface = RoadSurface(source, ROOT / ".tooling/scenario-roads" / token, definitions)
    failures = [p for p in cells if not surface.asphalt(*p)]
    if failures:
        raise ValueError(
            f"Probe corridor crosses {len(failures)} non-road/sidewalk/uncertain tiles; first={failures[:8]}"
        )
    headings = [
        math.degrees(math.atan2(b[0] - a[0], b[1] - a[1])) % 360 for a, b in zip(points, points[1:])
    ]
    turns = [(b - a + 180) % 360 - 180 for a, b in zip(headings, headings[1:])]
    source_paths = [
        source / "worldmap.xml.bin",
        definitions,
        ROOT / "data/game-files/media/newtiledefinitions.tiles",
        ROOT / "data/game-files/media/scripts/generated/vehicles/vehicle_car_small.txt",
    ]
    for cx, cy in sorted({(x // 256, y // 256) for x, y in cells}):
        source_paths.extend([source / f"{cx}_{cy}.lotheader", source / f"world_{cx}_{cy}.lotpack"])
    sprites = Counter(name for point in cells for name in surface.names(*point))
    evidence = {
        "schema": 1,
        "kind": "static-road-probe-route",
        "waypoints": [{"x": float(x), "y": float(y)} for x, y in points],
        "length_tiles": sum(distances),
        "heading_degrees": headings[0],
        "segment_headings_degrees": headings,
        "turn_degrees": turns,
        "clearance": {
            "method": "conservative sampled swept disk with half-step expansion",
            "radius_tiles": radius,
            "sample_step_tiles": step,
            "sample_expansion_tiles": step / 2,
            "verified_tile_count": len(cells),
            "allowed_floor_materials": ["Road_06"],
            "requires_solidfloor": True,
            "rejects_conflicting_floor_overlays_and_curbs": True,
            "inside_mapped_highway": True,
            "mapped_highway_classes": ["primary", "secondary", "tertiary"],
            "live_loaded_squares_verified": False,
        },
        "tile_sprite_counts": dict(sorted(sprites.items())),
        "sources": {
            str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in source_paths
        },
        "limitations": [
            "Static installed-map proof, not a current save or loaded-world collision check.",
            "The route uses an empty road interior; it does not implement lane discipline or traffic rules.",
            "The live probe must recheck loaded ground, floor overlays, room/collision state and obstacles.",
            "Road_06 is a conservative allowlist for this probe, not a complete road-material classifier.",
            "Waypoint chords remain intact; the controller must not shortcut the bend.",
        ],
    }
    output = ROOT / "artifacts/scenario-map"
    output.mkdir(parents=True, exist_ok=True)
    path = output / "vehicle-probe-route.json"
    path.write_text(json.dumps(evidence, indent=2) + "\n")
    path.chmod(0o600)
    props = output / "vehicle-probe-route.properties"
    props.write_text(
        "vehicle_probe.waypoints=" + ";".join(f"{x:.6f},{y:.6f}" for x, y in points) + "\n"
    )
    props.chmod(0o600)
    print(
        json.dumps(
            {
                "artifact": str(path.relative_to(ROOT)),
                "waypoints": len(points),
                "length_tiles": sum(distances),
                "corridor_tiles": len(cells),
            },
            indent=2,
        )
    )


def generate():
    started = time.perf_counter()
    source = ROOT / "data/game-files/media/maps/Muldraugh, KY"
    output = ROOT / "artifacts/scenario-map"
    output.mkdir(parents=True, exist_ok=True)
    generated = output / "generated"
    generated.mkdir(exist_ok=True)
    subprocess.run(
        [
            str(ROOT / ".tooling/agent/protoc/bin/protoc"),
            "-I",
            str(ROOT / "protocol"),
            f"--python_out={generated}",
            str(ROOT / "protocol/npc_control.proto"),
        ],
        check=True,
    )
    spec = importlib.util.spec_from_file_location(
        "scenario_map_protocol", generated / "npc_control_pb2.py"
    )
    protocol = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(protocol)
    x0, y0, x1, y1 = BOUNDS
    places = []
    source_hashes = {}
    for cx in range(x0 // 256, (x1 - 1) // 256 + 1):
        for cy in range(y0 // 256, (y1 - 1) // 256 + 1):
            path = source / f"{cx}_{cy}.lotheader"
            if not path.exists():
                continue
            data = path.read_bytes()
            source_hashes[path.name] = hashlib.sha256(data).hexdigest()
            header = read_header(data, cx, cy)
            for index, room_ids in enumerate(header["buildings"]):
                rooms = [header["rooms"][i] for i in room_ids]
                residential = any(
                    r["name"] in {"bedroom", "kidsbedroom", "bedroom4"} for r in rooms
                )
                for kind, names in ROOM_ROLES.items():
                    if kind == "home" and not residential:
                        continue
                    if kind != "home" and residential:
                        continue
                    matches = [r for r in rooms if r["z"] == 0 and r["name"] in names]
                    if not matches:
                        continue
                    rect = max(
                        (rect for room in matches for rect in room["rects"]),
                        key=lambda r: r[2] * r[3],
                    )
                    x, y = rect[0] + rect[2] // 2 + 0.5, rect[1] + rect[3] // 2 + 0.5
                    if not (x0 <= x < x1 and y0 <= y < y1):
                        continue
                    places.append(
                        {
                            "id": f"{cx}:{cy}:{index}:{kind}",
                            "kind": kind,
                            "position": {"x": x, "y": y, "z": 0},
                            "available": True,
                            "revision": 1,
                        }
                    )
    grid_data, navigation_timings = navigation()
    # Conservative Road_06-only vehicle graph. Both every node footprint and
    # every swept edge must clear the road/overlay mask. Point-only validation
    # lets a diagonal across an asphalt intersection clip the sidewalk corner.
    # This graph deliberately loses uncertain/narrow connections rather than
    # inventing a drive over terrain the current vehicle profile cannot clear.
    nodes, grid, position_ids = [], {}, {}
    offsets = sorted(
        ((dx, dy) for dx in range(-4, 5) for dy in range(-4, 5)),
        key=lambda p: (p[0] * p[0] + p[1] * p[1], p),
    )
    for gx in range(x0 // 8, x1 // 8):
        for gy in range(y0 // 8, y1 // 8):
            candidate = None
            for dx, dy in offsets:
                point = gx * 8 + 4 + dx, gy * 8 + 4 + dy
                if grid_data.center_clear(*point, ROAD_RADIUS, ROAD_SAMPLE_STEP):
                    candidate = point
                    break
            if candidate is None:
                continue
            if candidate not in position_ids:
                if len(nodes) >= 4096:
                    raise RuntimeError("Scenario road node cap exceeded")
                position_ids[candidate] = len(nodes) + 1
                nodes.append(
                    {
                        "id": len(nodes) + 1,
                        "position": {"x": candidate[0] + 0.5, "y": candidate[1] + 0.5, "z": 0},
                    }
                )
            grid[gx, gy] = position_ids[candidate]
    by_id = {n["id"]: n["position"] for n in nodes}
    edges, seen = [], set()
    for (gx, gy), a in grid.items():
        for dx, dy in ((1, 0), (0, 1), (1, 1), (1, -1)):
            b = grid.get((gx + dx, gy + dy))
            if not b or a == b:
                continue
            pair = min(a, b), max(a, b)
            if pair in seen:
                continue
            seen.add(pair)
            p, q = by_id[a], by_id[b]
            weight = grid_data.edge_cost(
                (p["x"], p["y"]), (q["x"], q["y"]), ROAD_RADIUS, ROAD_SAMPLE_STEP
            )
            if weight is None:
                continue
            edges.extend(
                [
                    {"from": a, "to": b, "cost": weight, "blocked": False},
                    {"from": b, "to": a, "cost": weight, "blocked": False},
                ]
            )
    if len(edges) > 16384:
        raise RuntimeError("Scenario road edge cap exceeded")
    batch = protocol.ObservationBatch(
        revision=1, phase="waiting", seed=1, navigation_id=grid_data.manifest["identity"]
    )
    for p in places:
        batch.places.add(**p)
    for n in nodes:
        batch.road_nodes.add(**n)
    for e in edges:
        batch.road_edges.add(**e)
    payload = batch.SerializeToString()
    if len(payload) > 1024 * 1024:
        raise RuntimeError("Static map index unexpectedly large")
    (output / "map-index.pb").write_bytes(payload)
    (output / "map-index.json").write_text(
        json.dumps(
            {"bounds": BOUNDS, "places": places, "road_nodes": nodes, "road_edges": edges},
            separators=(",", ":"),
        )
        + "\n"
    )
    # Server-only derived coordinates; no textures, map binaries, or dependency code.
    lua = [
        "-- Generated by scripts/build_scenario_map.py from the installed map.",
        'return { navigation_id = "' + grid_data.manifest["identity"] + '", places = {',
    ]
    for p in places:
        q = p["position"]
        lua.append(
            '  {id="%s",kind="%s",position={x=%.1f,y=%.1f,z=0},available=true,revision=1},'
            % (p["id"], p["kind"], q["x"], q["y"])
        )
    lua.extend(["} }", ""])
    destination = ROOT / "mods/AKRStoryteller/42/media/lua/server/AKRScenario/MapIndex.lua"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n".join(lua))
    counts = {kind: sum(p["kind"] == kind for p in places) for kind in ROOM_ROLES}
    manifest = {
        "bounds": BOUNDS,
        "places": counts,
        "nodes": len(nodes),
        "edges": len(edges),
        "bytes": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "source_headers": source_hashes,
        "road_revision": grid_data.manifest["sources"][
            str((source / "worldmap.xml.bin").resolve())
        ][:16],
        "navigation_identity": grid_data.manifest["identity"],
        "navigation_manifest": "artifacts/scenario-map/navigation/manifest.json",
        "navigation_timings": navigation_timings,
        "total_build_ms": (time.perf_counter() - started) * 1000,
        "road_clearance": {
            "radius_tiles": ROAD_RADIUS,
            "sample_step_tiles": ROAD_SAMPLE_STEP,
            "sampling_expansion_tiles": ROAD_SAMPLE_STEP / 2,
            "allowed_floor_materials": ["Road_06"],
            "nodes_and_edges_swept": True,
            "edge_scoring": grid_data.manifest["profile"]["edge_scoring"],
            "tile_definitions_sha256": grid_data.manifest["sources"][
                str((ROOT / "data/game-files/media/newtiledefinitions.tiles.txt").resolve())
            ],
        },
        "limitations": [
            "The initial road graph covers the configured Muldraugh area only.",
            "Conservative asphalt-only clearance omits uncertain materials and narrow roads.",
            "Footprint disk assumes a vehicle no larger than the configured radius.",
            "Graph bends are retained; steering and turning feasibility need controller validation.",
            "Loaded terrain, occupancy, doors and traffic still require live checks.",
        ],
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({k: v for k, v in manifest.items() if k != "source_headers"}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--probe-route",
        type=Path,
        help="Validate a private JSON waypoint request and emit probe road evidence",
    )
    parser.add_argument(
        "--bake-navigation",
        action="store_true",
        help="Only build/verify the versioned navigation cost and clearance grid",
    )
    parser.add_argument(
        "--benchmark-navigation",
        type=Path,
        help="Benchmark offline baked/direct queries using a private JSON waypoint route",
    )
    args = parser.parse_args()
    if args.probe_route:
        probe_route(args.probe_route)
    elif args.benchmark_navigation:
        points = probe_waypoints(json.loads(args.benchmark_navigation.read_text()).get("waypoints"))
        grid, timings = navigation()
        with tempfile.TemporaryDirectory(
            prefix="nav-benchmark-", dir=ROOT / "artifacts/scenario-map"
        ) as directory:
            _, cold = bake_navigation(
                ROOT / "data/game-files/media/maps/Muldraugh, KY",
                ROOT / "data/game-files/media/newtiledefinitions.tiles.txt",
                BOUNDS,
                Path(directory),
                ROOT / ".tooling/scenario-roads",
            )
            _, cached = bake_navigation(
                ROOT / "data/game-files/media/maps/Muldraugh, KY",
                ROOT / "data/game-files/media/newtiledefinitions.tiles.txt",
                BOUNDS,
                Path(directory),
                ROOT / ".tooling/scenario-roads",
            )
        result = benchmark_queries(
            grid,
            ROOT / "data/game-files/media/maps/Muldraugh, KY",
            ROOT / "data/game-files/media/newtiledefinitions.tiles.txt",
            ROOT / ".tooling/scenario-roads",
            points,
        )
        result.update(
            {
                "navigation_identity": grid.manifest["identity"],
                "open_timings": timings,
                "cold_bake": cold,
                "cached_bake": cached,
            }
        )
        target = ROOT / "artifacts/scenario-map/navigation-benchmark.json"
        target.write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result, indent=2))
    elif args.bake_navigation:
        grid, timings = navigation()
        print(
            json.dumps(
                {
                    "navigation_identity": grid.manifest["identity"],
                    "tiles": grid.manifest["tiles"],
                    "passable_tiles": grid.manifest["passable_tiles"],
                    "bytes": grid.manifest["bytes"],
                    "timings": timings,
                },
                indent=2,
            )
        )
    else:
        generate()
