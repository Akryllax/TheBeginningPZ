---
type: design
status: implemented-static-preprocessing
updated: 2026-09-27
---

# Precomputed navigation

Installed map files are baked offline into scored navigation tiles and a vehicle-clearance
road graph. The native worker continues to consume the prebuilt Protobuf graph; it does
not unpack lot files or interpret tile properties while planning. This implements static
preprocessing for [[Design/First Week]], not complete vehicle navigation or multiplayer acceptance.

## Coverage and surface evidence

The initial Muldraugh rectangle is **X [10400,11200), Y [9400,10752)**: 1,081,600 tiles
across 24 map cells. The current bake marks 50,423 tiles as passable asphalt. It does not
cover the entire world. The offline baker caps each region at four million tiles; the
chunk format permits additional regions after their profiles and sources are verified.

Inputs are the installed `worldmap.xml.bin`, `.lotheader`/`.lotpack` files and binary/text
tile definitions. A tile must lie within a mapped primary/secondary/tertiary road and
contain a solid floor with `FloorMaterial=Road_06`. The complete stack is checked:
conflicting floor materials, curbs, static collision flags and unknown sprite definitions
block it. Asphalt beneath a `Road_03` sidewalk overlay therefore fails even when the
Observer's broader pavement raster labels the position as road.

This deliberately narrow profile excludes uncertain materials and narrow connections.
It does not establish traffic lanes, one-way rules, bridge suitability or general access
over parking areas, dirt and grass. Broader profiles need separate semantic and physical checks.

## Format, identity and clearance

Private output lives under `artifacts/scenario-map/navigation/`. `manifest.json` records
schema **1**, region bounds, profile, source/algorithm hashes, content identity and each
chunk's SHA256. Inputs are hashed once when an offline build/session opens the dataset.
A changed source, profile or parser identity invalidates the cache; chunk size/header/hash
mismatches fail closed. The prerequisite road raster is also namespaced by that complete
identity, including when source bytes change with file size and timestamp preserved. Route-edge queries use loaded chunks without rereading source maps.

Each independently hashed `.nav` file holds one 256×256 cell:

| Field | Representation |
| --- | --- |
| Header | 24 bytes, little-endian `<8sIiiI`: `LFNAV01\0`, schema, signed cell X/Y, payload size |
| Tile order | Row-major Y then X, two unsigned bytes per tile |
| Surface cost | `0` hard blocked; `10` confirmed asphalt |
| Clearance score | Quarter-tile units; `0` blocked, capped at 255 |

The 24 chunks occupy **3,146,304 bytes** (about 3 MiB), excluding the small manifest.
The reader caches at most 32 chunks. Clearance is a conservative chessboard-distance
lower bound to the nearest blocked tile boundary, not exact Euclidean clearance. The
distance transform spans the full region before chunking, so internal chunk seams do not
create walls or lose nearby obstacles. Outside the configured rectangle, including partial
chunk padding, is blocked; there is no assumed free halo.

The graph checks every node and swept edge with a **2.25-tile disk radius**, sampling
every 0.25 tiles and expanding samples by 0.125 tiles to cover gaps. This profile is only
suitable for vehicle footprints contained by that disk. Clearance remains a hard constraint;
the positive edge score then adds up to 15% cost to favor roomier road interiors:

`length × mean(surface_cost/10 × (1 + 0.15/(1 + max(0, clearance − radius))))`

The resulting `map-index.pb` has **1,076 nodes and 2,426 directed edges**, down from
2,777/11,922 in the earlier point-only graph; the full Protobuf index is 79,556 bytes.
The local probe-area route remains reachable through nine graph vertices, with all swept
edges clear. A* retains those intermediate vertices. The former diagonal across the inside
sidewalk corner is rejected. A polygonal route can still demand an infeasible steering
change, so smoothing, turning radius and controller execution require their own validation.

## Reproduce the bake and measurements

From the project root after bootstrap and local game installation:

```sh
.tooling/venv/bin/python scripts/build_scenario_map.py --bake-navigation
.tooling/venv/bin/python scripts/build_scenario_map.py
.tooling/venv/bin/python scripts/build_scenario_map.py --probe-route artifacts/scenario-map/vehicle-probe-route-request.json
.tooling/venv/bin/python scripts/build_scenario_map.py --benchmark-navigation artifacts/scenario-map/vehicle-probe-route.json
.tooling/venv/bin/python -m pytest -q tests/test_scenario_roads.py tests/test_scenario_navigation.py
python3 scripts/build_npc_service.py --test
```

The first command builds or validates the navigation cache; the second constructs the
Protobuf graph and generated server place index. The last two artifact commands require
the prepared private waypoint request/route, which are excluded from Git. Requests use
`waypoints: [{x, y}, ...]`: 2–16 points, ground level only, X/Y within [-20000,60000],
segments 0.5–40 tiles and total length 2–60 tiles. Preparing an artifact does not deploy it
or start a game server. The earlier probe used a six-point, 55.21-tile polyline route
whose 307-tile swept corridor passed the static checks. The corrected lane course below
supersedes that particular driving test, not the graph bake.

Final preprocessing measurements on this development machine on **2026-09-26**,
navigation identity `f086062b762a6096d7d6123b33d43b36ad0d75061c5ef27f7aac36494f95b366`:

| Measurement | Result |
| --- | --- |
| First bake with a new prerequisite road raster | 6.508 s |
| Navigation cache miss with the prerequisite raster already cached | 3.592 s |
| Validated navigation cache hit, including source hashing | 80.3 ms |
| Complete graph rebuild using cached navigation | 1.932 s |
| First source floor parse and 307-tile route check | 436.6 ms |
| Reclassify those tiles from already-loaded source stacks, p50 | 0.379 ms |
| Already-memoized source lookups, p50 | 0.026 ms |
| Baked tile lookups, p50 / p95 | 0.101 / 0.110 ms |
| Full five-segment swept-corridor validation, p50 / p95 | 3.502 / 3.607 ms |

Lookup/corridor measurements use 30 repetitions. “Cache miss” means the navigation output
was absent; OS file caches and the broad Observer road raster may already be warm. The
full corridor row includes geometry generation; the lookup rows reuse the same tile list.
An already-memoized source lookup is faster here. Baking chiefly avoids repeated raw-map
parsing across processes and supplies reusable scores/clearance. These figures do **not**
measure or demonstrate lower per-NPC A* latency or game-thread cost: the native worker
already used a prebuilt graph.

Private evidence is in `navigation-benchmark.json`, `navigation-reachability.json` and
the map/navigation manifests under `artifacts/scenario-map/`. The reachability report uses
a Python mirror of native A* ordering; native core tests separately verify bend preservation.
The final 116-test project suite passed. Its navigation/road regressions cover
roundtrip/corruption, content changes preserving size/timestamp, blocked surfaces,
cross-chunk clearance, diagonal corner cutting, clearance-weighted costs and the
32,768-sample geometry budget, including tiny finite and nonfinite sample parameters.

## Runtime boundary

Loaded game squares remain authoritative for vehicles, players, construction, doors,
damage and other live obstacles. The probe must validate its loaded corridor and brake
when it becomes unsafe or unavailable, even if static navigation accepts it. Future
dynamic blockage updates can close or penalize graph edges without rewriting the static
dataset. A disconnected graph must not fabricate an off-road driving connection.

Game-derived outputs stay ignored and local; distribute original tooling rather than
installed map data. Record runtime/steering and two-client results separately in
[[Experiments/Implementation Ledger]] and [[Runbooks/Multiplayer Validation]].

## Bézier driving and reviewed lane course

`scripts/scenario_lanes.py` verifies the installed intersection's asphalt cross-sections
and west-facing stop sign, then writes `artifacts/scenario-map/vehicle-lane-course.json`.
Eastbound centre Y=9861.5 and northbound centre X=10820.5 put this particular car in the
right-hand lanes. The stop centre is at path progress 24.75, before the intersection,
with a two-second dwell below 0.25 km/h. This is one reviewed intersection, not automatic
map-wide lane, sign or traffic-priority inference.

The 57.426-tile trajectory has a straight cubic, a radius-six quarter-turn approximation
and a straight cubic. Joins require matching position/tangent; no C2 continuity claim is
made. Analytic derivatives provide heading/curvature, with a bounded arc-length table,
progress-window projection and signed lateral error. Steering looks 2.3–4 tiles ahead;
speed regulation previews 10–64 tiles across segment joins. A one-second bicycle rollout
includes future steering adjustments across joins and estimates deviation/heading. The
reviewed curved course now specifies a **50 km/h street limit**, further reduced by vehicle
capabilities, curvature and stopping envelopes. Its short approach and required stops cannot
demonstrate sustained 50 km/h travel. The original polyline tests retain their 5/15 km/h bounds.

The latest native adapter calls the installed, hash-guarded drivetrain and braking methods;
it does not reproduce their engine/gear/RPM implementation or replace game classes. Requested
force is capped by the stock drivetrain result; brake demand is a fraction of the car's
condition-dependent service brake strength. The loaded mass is passed to Bullet, and steering
uses the script's speed-dependent angle clamp and a slew limit derived from its increment.
Missing/nonfunctional tyres or invalid capabilities reject the probe. Planning uses current
power, mass, top speed and tyre friction with conservative braking/lateral estimates. These
estimates are driving preferences, not verified SI conversions of Bullet brake/friction units.
The current native proof remains specific to the reviewed, repaired SmallCar, not arbitrary
modded vehicles, worn components, wet surfaces or towing. No pose or velocity is forced to
meet the street limit; native physics determines the resulting motion.

The implementation uses original Java/Python mathematics, with no ROS dependency or
copied controller code. Velocity-scaled lookahead, curvature regulation and forward
collision projection are established approaches described in the
[Nav2 regulated pure pursuit documentation](https://api.nav2.org/nav2-rolling/html/md_nav2_regulated_pure_pursuit_controller_README.html).

The pinned SmallCar footprint uses an oriented rectangle with sampling margin, rather
than the graph's larger direction-independent disk. Its 192 swept tiles pass both source
and loaded-world checks. Current body placement and the nearby curved stopping corridor
are rechecked on the server. Live checks complete synchronously within hard limits of 256
distinct road tiles, 64 nearby vehicles and 64 sampled native bodies. Incomplete or invalid
checks still stop the car. Scan duration and scans exceeding 1 ms are measured separately;
merely crossing a wall-clock millisecond no longer injects a full-brake command. Staged
cold corridor validation retains its cooperative deadline. Native calls/JVM pauses can
still exceed a duration target; report measured hook latency separately.

Moving-vehicle prediction performs bounded continuous swept-circle tests between samples
using a complete, bounded native velocity snapshot; a parked car without a native body is
handled as static only when server-owned, unoccupied and reporting zero motion. Missing
snapshot entries and failed/incomplete snapshots are distinct: the latter stop the probe.
Unknown/client-owned nearby vehicles also stop it because packet freshness is not established.
The forecast is capped at 12 seconds and 65 poses. Circles are conservative and are not a
production adjacent-lane passing policy. Current corridor checks also reject actors,
construction and unloaded terrain. Moving pedestrian forecasts, full streaming-range
coverage and waiting/replanning remain pending. One native parked-car trial now demonstrates
conservative braking before contact; moving-blocker trials remain pending. Forecasting
contact does not simulate impact, prove damage or authorize deliberate contact.

Reproduce the isolated course after building the agent and stopping the probe:

```sh
python3 scripts/scenario_lanes.py
./dayone vehicle-probe-route artifacts/scenario-map/vehicle-lane-course.json
./dayone vehicle-probe-start
./dayone vehicle-probe-control start
```

Runtime driving currently belongs to the server Java probe; the C++ worker is not connected
to this reviewed test course. See [[Implementation Roadmap]] for the intended planner /
executor split and [[Traffic Incidents]] for the separate crash lifecycle.
