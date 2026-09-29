"""Original bounded lane-course geometry; reads installed map evidence, never save files."""

import math


def footprint_cells(x, y, fx, fy, half_width=0.95, half_length=1.95):
    """Conservative oriented rectangle versus tile SAT, including boundary contacts."""
    norm = math.hypot(fx, fy)
    if not math.isfinite(norm) or norm < 0.5:
        raise ValueError("Invalid heading")
    fx, fy = fx / norm, fy / norm
    rx, ry = fy, -fx
    ex = abs(fx) * half_length + abs(rx) * half_width
    ey = abs(fy) * half_length + abs(ry) * half_width
    for tx in range(math.floor(x - ex), math.floor(x + ex) + 1):
        for ty in range(math.floor(y - ey), math.floor(y + ey) + 1):
            dx, dy = tx + 0.5 - x, ty + 0.5 - y
            if abs(dx * fx + dy * fy) > half_length + 0.5 * (abs(fx) + abs(fy)):
                continue
            if abs(dx * rx + dy * ry) > half_width + 0.5 * (abs(rx) + abs(ry)):
                continue
            yield tx, ty


def swept_lane_cells(points):
    cells = set()
    previous = None
    for a, b in zip(points, points[1:]):
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy)
        heading = math.atan2(dx, dy)
        if previous is not None:
            change = math.atan2(math.sin(heading - previous), math.cos(heading - previous))
            n = max(1, math.ceil(abs(change) / math.radians(2)))
            for i in range(n + 1):
                angle = previous + change * i / n
                cells.update(footprint_cells(*a, math.sin(angle), math.cos(angle)))
        n = max(1, math.ceil(length / 0.25))
        for i in range(n + 1):
            cells.update(
                footprint_cells(a[0] + dx * i / n, a[1] + dy * i / n, dx / length, dy / length)
            )
        previous = heading
    return cells


def bezier_samples(curves, *, extended_impact=False):
    if not isinstance(curves, list) or not 1 <= len(curves) <= 15:
        raise ValueError("Bezier curve count outside 1..15")
    previous = None
    length = 0
    last_point = None
    last_tangent = None
    for curve in curves:
        if not isinstance(curve, list) or len(curve) != 4:
            raise ValueError("Four Bezier control points required")
        coords = []
        for p in curve:
            if not isinstance(p, dict):
                raise ValueError("Invalid Bezier point")
            x, y = p.get("x"), p.get("y")
            if any(
                type(v) not in (int, float) or not math.isfinite(v) or not -20000 <= v <= 60000
                for v in (x, y)
            ):
                raise ValueError("Invalid Bezier coordinate")
            coords.append((x, y))
        p0, p1, p2, p3 = coords
        chord = (p3[0] - p0[0], p3[1] - p0[1])
        for a, b in zip(coords, coords[1:]):
            if (b[0] - a[0]) * chord[0] + (b[1] - a[1]) * chord[1] <= 0:
                raise ValueError("Non-forward Bezier controls")
        for j in range(129):
            t = j / 128
            u = 1 - t
            point = tuple(
                u**3 * p0[k] + 3 * u * u * t * p1[k] + 3 * u * t * t * p2[k] + t**3 * p3[k]
                for k in (0, 1)
            )
            first = tuple(
                3
                * (u * u * (p1[k] - p0[k]) + 2 * u * t * (p2[k] - p1[k]) + t * t * (p3[k] - p2[k]))
                for k in (0, 1)
            )
            second = tuple(
                6 * (u * (p2[k] - 2 * p1[k] + p0[k]) + t * (p3[k] - 2 * p2[k] + p1[k]))
                for k in (0, 1)
            )
            norm = math.hypot(*first)
            if norm < 1e-5:
                raise ValueError("Stationary Bezier tangent")
            curvature = (first[1] * second[0] - first[0] * second[1]) / norm**3
            if abs(curvature) > 0.35:
                raise ValueError("Bezier curvature too sharp")
            tangent = (first[0] / norm, first[1] / norm)
            if j == 0 and previous:
                if math.dist(previous[0], point) > 0.001 or sum(
                    a * b for a, b in zip(previous[1], tangent)
                ) < math.cos(math.radians(1)):
                    raise ValueError("Disconnected Bezier curve or tangent")
            if last_point is not None:
                distance = math.dist(last_point, point)
                turn = math.atan2(first[0], first[1]) - math.atan2(last_tangent[0], last_tangent[1])
                if distance + 2.2 * abs(math.atan2(math.sin(turn), math.cos(turn))) > 0.45:
                    raise ValueError("Bezier sampling exceeds footprint margin")
                length += distance
            last_point = point
            last_tangent = tangent
            yield (*point, *tangent)
        previous = (point, tangent)
    if not 2 <= length <= (600 if extended_impact else 60):
        raise ValueError("Bezier length exceeds bounded course")


def bezier_cells(curves):
    cells = set()
    for x, y, fx, fy in bezier_samples(curves):
        cells.update(footprint_cells(x, y, fx, fy))
    return cells


def muldraugh_lane_course(surface):
    """Reviewed eastbound-to-northbound test intersection, not a general traffic planner."""
    # Eastbound uses the south lane; northbound uses the east lane.
    points = [(10783.5, 9861.5), (10814.5, 9861.5)]
    for degrees in range(75, -1, -15):
        angle = math.radians(degrees)
        points.append((10814.5 + 6 * math.cos(angle), 9855.5 + 6 * math.sin(angle)))
    points.append((10820.5, 9838.5))
    sign = (10810, 9864)
    matches = [
        n
        for n in surface.names(*sign)
        if surface.properties.get(n, {}).get("GroupName") == "Stop"
        and surface.properties.get(n, {}).get("Facing") == "W"
    ]
    if not matches:
        raise ValueError("Reviewed west-facing stop sign no longer exists")
    # Verify each lane's physical road edges away from the intersection flare.
    if [y for y in range(9854, 9866) if surface.asphalt(10800, y)] != list(range(9857, 9863)):
        raise ValueError("Eastbound road cross-section changed")
    if [x for x in range(10812, 10826) if surface.asphalt(x, 9845)] != list(range(10816, 10822)):
        raise ValueError("Northbound road cross-section changed")

    def line(a, b):
        return [
            {"x": a[0] + (b[0] - a[0]) * t, "y": a[1] + (b[1] - a[1]) * t}
            for t in (0, 1 / 3, 2 / 3, 1)
        ]

    tangent = 6 * 4 / 3 * math.tan(math.pi / 8)
    curves = [
        line(points[0], points[1]),
        [
            {"x": 10814.5, "y": 9861.5},
            {"x": 10814.5 + tangent, "y": 9861.5},
            {"x": 10820.5, "y": 9855.5 + tangent},
            {"x": 10820.5, "y": 9855.5},
        ],
        line((10820.5, 9855.5), points[-1]),
    ]
    cells = bezier_cells(curves)
    bad = [p for p in sorted(cells) if not surface.asphalt(*p)]
    if bad:
        raise ValueError(f"Lane footprint leaves asphalt: {bad[:8]}")
    return {
        "waypoints": [{"x": x, "y": y} for x, y in points],
        "lane_mode": True,
        "beziers": curves,
        "speed_kmh": 50,
        "stops": [{"progress": 24.75, "hold_seconds": 2}],
        "traffic_evidence": {
            "sign_xy": list(sign),
            "sprites": matches,
            "facing": "W",
            "eastbound_lane_y": 9861.5,
            "northbound_lane_x": 10820.5,
            "verified_footprint_tiles": len(cells),
            "half_width": 0.95,
            "half_length": 1.95,
            "path": "tangent-continuous cubic Bezier",
            "scope": "reviewed intersection only; no automatic traffic priority inference",
        },
    }


def muldraugh_bypass_course(surface):
    """Review both lanes on a straight block, away from the previous junction."""
    points = [(10666.5, 9861.5), (10688.5, 9861.5), (10710.5, 9861.5)]
    # Both directions and the parked-car maneuver fit inside this strip. A
    # changed/missing tile rejects the artifact rather than inventing a bypass.
    bad = [
        (x, y) for x in range(10664, 10713) for y in range(9857, 9863) if not surface.asphalt(x, y)
    ]
    if bad:
        raise ValueError(f"Reviewed passing strip is not clear asphalt: {bad[:8]}")

    def line(a, b):
        return [
            {"x": a[0] + (b[0] - a[0]) * t, "y": a[1] + (b[1] - a[1]) * t}
            for t in (0, 1 / 3, 2 / 3, 1)
        ]

    curves = [line(a, b) for a, b in zip(points, points[1:])]
    return {
        "waypoints": [{"x": x, "y": y} for x, y in points],
        "lane_mode": True,
        "beziers": curves,
        "speed_kmh": 40,
        "stops": [],
        "bypass": True,
        "shoulder": True,
        "traffic_evidence": {
            "scope": "reviewed straight two-lane passing test only",
            "reviewed_road_bounds": [10664, 9857, 10713, 9863],
            "verified_road_tiles": 49 * 6,
            "suggested_fixture_command": [10689, 9862, 0],
            "expected_fixture_xy": [10688, 9861.9],
            "observer_xy": [10688.5, 9864.5],
            "passing_limit_kmh": 15,
            "shoulder_scope": "personality-dependent fallback; native ground/wall/actor checks mandatory; not offline-approved grass driving",
        },
    }


def muldraugh_shoulder_course(surface):
    """Separate road course beside an open southern verge; live clearance is mandatory."""
    points = [(10732.5, 9861.5), (10754.5, 9861.5), (10776.5, 9861.5)]
    bad = [
        (x, y) for x in range(10730, 10779) for y in range(9857, 9863) if not surface.asphalt(x, y)
    ]
    if bad:
        raise ValueError(f"Reviewed shoulder approach is not clear asphalt: {bad[:8]}")

    def line(a, b):
        return [
            {"x": a[0] + (b[0] - a[0]) * t, "y": a[1] + (b[1] - a[1]) * t}
            for t in (0, 1 / 3, 2 / 3, 1)
        ]

    return {
        "waypoints": [{"x": x, "y": y} for x, y in points],
        "lane_mode": True,
        "beziers": [line(a, b) for a, b in zip(points, points[1:])],
        "speed_kmh": 40,
        "stops": [],
        "bypass": True,
        "shoulder": True,
        "traffic_evidence": {
            "scope": "reviewed shoulder test approach; off-road corridor requires live validation",
            "reviewed_road_bounds": [10730, 9857, 10779, 9863],
            "verified_road_tiles": 294,
            "fixture_tiles": [[10754, 9862, 0], [10754, 9858, 0]],
            "fixture_heading_degrees": 90,
            "observer_xy": [10753.5, 9854.5],
            "shoulder_limit_kmh": 8,
            "denial_fixture_tile": [10758, 9864, 0],
        },
    }


if __name__ == "__main__":
    import argparse
    import hashlib
    import json
    from pathlib import Path
    from scenario_roads import RoadSurface
    from scenario_navigation import source_identity

    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--bypass", action="store_true")
    modes.add_argument("--shoulder", action="store_true")
    args = parser.parse_args()
    source = root / "data/game-files/media/maps/Muldraugh, KY"
    definitions = root / "data/game-files/media/newtiledefinitions.tiles.txt"
    bounds = (
        (10730, 9854, 10779, 9867)
        if args.shoulder
        else (10664, 9855, 10713, 9866)
        if args.bypass
        else (10780, 9835, 10828, 9870)
    )
    supplemental = (
        [definitions.with_name("tiledefinitions_erosion.tiles.txt")] if args.shoulder else []
    )
    identity, inputs = source_identity(source, definitions, bounds, supplemental)
    surface = RoadSurface(
        source, root / ".tooling/scenario-roads" / identity, definitions, supplemental
    )
    result = (
        muldraugh_shoulder_course(surface)
        if args.shoulder
        else muldraugh_bypass_course(surface)
        if args.bypass
        else muldraugh_lane_course(surface)
    )
    if supplemental:
        result["supplemental_definitions"] = {
            str(path.relative_to(root)): inputs["sources"][str(path.resolve())]
            for path in supplemental
        }
    result["source_identity"] = identity
    result["baker_sha256"] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    output = (
        root
        / "artifacts/scenario-map"
        / (
            "vehicle-shoulder-course.json"
            if args.shoulder
            else "vehicle-bypass-course.json"
            if args.bypass
            else "vehicle-lane-course.json"
        )
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(output)
    print(json.dumps(result["traffic_evidence"]))
