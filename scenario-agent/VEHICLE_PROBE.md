# Server-only empty-car physics probe

This is an opt-in experiment for one disposable world, not the NPC driving
integration. It requires no client Java agent and does not manufacture a player.
It changes no installed game file. The main scenario Lua may be disabled.

Use the existing server agent with these additional properties:

```properties
side=server
scenario.enabled=true
world=LofersVehicleProbe_20260926
vehicle_probe.enabled=true
vehicle_probe.directory=/run/lofers/vehicle-probe
vehicle_probe.x=10756.5
vehicle_probe.y=9856.5
vehicle_probe.z=0
vehicle_probe.heading_degrees=90
vehicle_probe.distance=10
vehicle_probe.speed_kmh=4
```

The directory must exist, be private to the operator, and not be a symlink.
The world name must match `LofersVehicleProbe_[A-Za-z0-9_-]{1,64}`. A playable
world name or client side is refused. Legacy straight distance is limited to 12
tiles and target speed to 5 km/h. Heading is a native rotation around the vertical axis: 0 faces
world +Y; 90 faces world +X. The test uses `Base.SmallCar` only.

The server writes `status.properties` atomically from a daemon I/O thread.
Read its current `server_epoch` and create `control.properties` in that same
directory:

```properties
server_epoch=<exact value from status.properties>
command_id=1
action=start
```

Use a larger command ID and `action=stop` to brake and clean up. The boot epoch
prevents an old file from starting another car after a JVM restart. Files are
limited to 4096 bytes and the only actions are start/stop. There is no code
evaluation, remote endpoint or game object on the file thread.

The game-thread tick requests a bounded loaded area only after ServerMap's grid
and cell map exist. On an explicit start, it invokes the engine's `Bullet.init()`
when needed (the bundled headless native library on Linux servers) and initializes
WorldSimulation if the empty dedicated server has not created its Bullet world
yet. It waits for actual loaded chunks, checks a ground-level
outdoor clear corridor, creates the bounded native server cells covering the
road, and sends one chunk's collision data per tick. The pinned native server
cell is 5×5 eight-tile chunks; Java ServerMap cells have a different size.
Native server mode ignores the client chunk-map activation path. Cell creation
requires a fresh native world created by this probe and an empty native vehicle
world; cleanup removes only the exact cells this probe created. It then
defines the vehicle script in Bullet, creates one BaseVehicle through normal
world methods, explicitly registers/activates its native body, and confirms the
native body count and readback API. Dedicated-server vanilla code skips those
native registration operations, so they are scoped to this one owned test car.

After two seconds settling, the probe uses native engine force, steering and
braking to follow its route, holds the stopped car for three seconds and removes
it. The legacy straight body lifetime is limited to 30 seconds. It aborts for unloaded terrain, authority change, an
unexpected occupant, invalid physical height, excessive speed/distance or failed
braking. Cleanup explicitly removes the native body because vanilla server
removal omits it. An unexpectedly occupied car is left in the world after its
probe body is removed, rather than deleting an occupied car.

Motion is read back by the normal WorldSimulation path. BaseVehicle's normal
update maintains its position/chunk; the probe marks position updates for the
normal VehicleManager full/update packet stream. It never uses teleportation
for motion and does not send a custom client transform protocol. Ownership
stays Server/-1; an ownership change aborts instead of competing with the
engine. Player-car collisions and NPC seats are later experiments.

Diagnostics include phase, error, native registration/cleanup counts, physical
position, speed, distance, native authority, physics frames, game ticks, maximum
tick cost and position publications. `position_publications` counts dirty-state
publications, **not measured network packets**. `client_validation=not_observed`
is intentional: a server file cannot establish what two clients rendered.

`python3 scripts/build_scenario_agent.py --test` verifies build/bytecode contracts,
the server JVM bootstrap, IPC and private control boundaries. These fixtures do
not execute native driving. Record actual disposable-world results separately.

## Recorded native-motion proof: 2026-09-26

The isolated no-mod server, with zero players connected, completed the native
physics run using agent SHA256
`106c3f900cb80a89e755ab881a9f9a81ae61b0d6980cca1f79198c09d97599b3`.
Its bundled `linux64/libPZBulletNoOpenGL64.so` SHA256 was
`256304a998a33fa9ba356182cad3ebaad0db14ac36762b806a950d0f08e95d6f`.
The 5×5 native server-cell layout is specific to this binary. Before using this
path in a normal world, compatibility checks must cover that native library as
well as Java classes.

One empty SmallCar settled on the paved test strip, drove from `(10756.5,9856.5)` to
`(10766.5546875,9856.5)`, braked, remained in the world for the three-second hold and
was removed. Stopping distance was 10.0546875 tiles; maximum observed speed was
4.8211 km/h and final speed was 0.0528 km/h, below the 0.25 km/h stopped threshold.
Physical height settled near 0.13951. The native vehicle count returned from
zero to one to zero; both owned native terrain cells were removed. The final
phase was `complete` with no error, 175 active physics frames and 176 position
dirty-state publications. Motion came from native force/brake control and
normal readback, with no coordinate-driven movement or additional physics step.

The largest measured probe tick was 96.3 ms during one-time vehicle creation.
This is not a steady-state performance result or evidence of production capacity.
The disposable server was then stopped gracefully and left prepared for a later
ordinary-client observation session. Full local receipts are
`artifacts/scenario-agent/vehicle-probe-result.json` and
`artifacts/scenario-agent/vehicle-probe-server-cell-run.jsonl`.

This proves the bounded server-native motion and cleanup path. It does not yet
prove ordinary-client interpolation or packet delivery, late joins, turning,
obstacle handling, player-car collisions, ownership transfer, NPC passengers,
or reliability in a normal populated world. No game client was launched for
this test and no client Java agent or core-file replacement was used.

## One-client observation: 2026-09-26

The same agent ran in epoch `843041a8-4934-4e1c-8c39-bb98af7e11e1` with one player
connected to the isolated server on port 16281. The player reported that the car
appeared, briefly accelerated and disappeared, then confirmed: “Yes, it moved
smoothly, but drove on the sidewalk.” This establishes qualitative visibility and
smooth movement on one ordinary client. It does not measure the duration of the
client-visible trajectory, interpolation delay or agreement between two clients.

The probe uses fixed coordinates and straight force control. Its strip was on the
sidewalk; it does not yet use the planner's road graph or steering. Automatic
removal after a three-second stopped hold is deliberate test cleanup.

Server evidence independently recorded 10.09375 tiles traveled, maximum speed
4.8211 km/h, final speed 0.05275 km/h and Server/-1 authority throughout the captured
live samples. The run completed without error. The native body count returned to
zero and both temporary native terrain cells were removed. The maximum measured
probe tick was 170.6 ms; steady-state capacity remains unmeasured. Raw server status
retains `client_validation=not_observed` because it cannot attest to rendering;
the separate receipt records the attributed player report.

Local evidence is under
`artifacts/scenario-agent/client-observation-20260926T192615Z-843041a8/`.

## Bounded road-turn controller

An explicit `vehicle_probe.waypoints` property enables the road-turn probe. The
start point must match `vehicle_probe.x/y`, and the initial heading must match
the first segment within ten degrees. The operator selects the route while the
disposable server is stopped; an in-flight control file cannot change it.

The locally validated example starts at `(10783.5,9860.5)` with heading 90:

```properties
vehicle_probe.x=10783.5
vehicle_probe.y=9860.5
vehicle_probe.heading_degrees=90
vehicle_probe.speed_kmh=4
vehicle_probe.waypoints=10783.5,9860.5;10814.5,9860.5;10816.5,9859.964102;10817.964102,9858.5;10818.5,9856.5;10818.5,9838.5
```

This 55.211657-tile east-to-north course uses six points and a radius-four turn.
It is a road-interior experiment, not traffic-lane planning. The route parser
accepts 2–16 points, 0.5–40-tile segments, a total length of 2–60 tiles and no
segment turn greater than 100 degrees. Its cached coverage is bounded by 1,500
road tiles, 96 collision chunks and nine native cells. Loaded area retention
covers this entire short route. Cell creation and chunk uploads are staged one
operation per tick, and owned cells are removed after the body is removed.

Before spawning, the server checks a swept radius-2.25 disk, sampled every 0.25
tiles with a half-sample margin. For the example this comprises 307 distinct
tiles. Each must be loaded, outdoors, solid, clear, without stairs/wall edges,
and have an actual `Road_06` floor. Every square object is inspected for
conflicting road materials, so `Road_06` beneath a `Road_03` sidewalk overlay is
rejected. The scan is staged to at most 48 tiles and one millisecond per tick.
The loaded SmallCar chassis half-diagonal must fit radius 2.25; its wheelbase is
read from loaded wheel positions. Actors in the spawn footprint prevent creation.

The pure-pursuit controller reads the native forward vector and position and
only applies force, brake and steering. It limits steering rate, slows before
turns, slows near the destination, and brakes on arrival. Motion after spawn
does not set world coordinates. While driving, the actual footprint and stopping
space are checked against current road tiles, actors and nearby vehicles with
bounded loops and a one-millisecond check deadline. An exceeded check budget,
road departure, stale/invalid observation, stalled progress or obstruction
requests braking and cleanup. Authority changes still abort. The road mode has
a bounded deadline of at most 120 seconds; it does not activate NPC driving.

Diagnostics separate body-active warm ticks from native initialization, cell
creation, chunk uploads, vehicle creation and cleanup. Warm p50/p95/p99 values
are fixed-histogram upper bounds with 0.1 ms buckets; maximum, mean, sample count
and counts exceeding 2/5 ms are reported separately. Cold operation maxima must
not be presented as steady-state timings. Dirty publications remain distinct
from measured packets, and server status cannot attest client rendering.

Fixtures cover route bounds, angle wrapping, speed and turn slowdown, departure,
emergency braking, stale observations, stalls, arrival and an independent
bicycle-model course simulation. The simulation's maximum cross-track distance
was 0.2185 tiles. This is a controller geometry check, not proof of Bullet
traction, native steering response, obstacle behavior or client interpolation.
The controller's separate native run is recorded below.

For this path, premain also verifies the pinned headless Bullet library using
the JVM's startup-captured library search paths. Missing, changed or shadowing
libraries fail before world load; changing a mutable System property cannot
mask an unsafe startup path. The manifest records the native filename/hash
beside the pinned Java class hashes. No client helper or core-file replacement
is needed for the empty-car experiment.

## Recorded native road-turn proof: 2026-09-26

The isolated server completed the six-point road course with zero players and
agent SHA256
`f135a657cc1f4f119ffd9d18d8e35bcc72114567bc84844caf982d6716763c51`.
The boot epoch was `70d34a75-51f1-4344-8962-b325fa6fd9ad`; the native library hash
remained `256304a998a33fa9ba356182cad3ebaad0db14ac36762b806a950d0f08e95d6f`.
All 307 road tiles passed live validation. The loaded vehicle's chassis
half-diagonal was 1.81620 tiles and wheelbase 1.93994 tiles. Its 33 collision
chunks occupied three temporary native cells.

Native steering changed the heading from 90 degrees to 179.9999 degrees. The car
slowed to approximately 2.2 km/h through the turn, resumed its 4 km/h target and
braked at the destination. It finished at `(10818.5,9839.1796875)`, 0.6796875 tiles
from the goal, within the configured arrival tolerance. The final phase was
`complete`, the reason was `route_arrived`, and no error was reported. Actual
distance was 53.87804 tiles versus 55.21166 tiles along the specified course;
the difference includes corner rounding and stopping tolerance. Maximum speed
was 4.00022 km/h and final speed 0.05275 km/h. The captured maximum cross-track
distance was 0.59333 tiles. This observed native deviation is larger than the
ideal-model result and is the relevant evidence for further clearance design.
Actual footprint checks continued to pass throughout this test.

Every captured live ownership sample reported Server/-1. The native body count
returned from zero to one to zero, and all three owned terrain cells were removed
after the stopped hold. There were 651 native physics frames and 652 position
dirty-state publications, with no extra physics stepping or teleport movement.

Across 650 body-active warm ticks, the measured mean was 0.1263 ms, p50 at most
0.1 ms, p95 at most 0.4 ms, p99 at most 0.6 ms and maximum 1.0824 ms. None exceeded
2 ms or 5 ms. These figures measure the probe's game-thread hook, including its
checks and status preparation; they do not measure the engine's total physics,
network or chunk-streaming cost. Separate cold operation maxima were 35.4523 ms
for vehicle creation, 12.3612 ms for cleanup, 4.5643 ms for native-world setup,
0.7253 ms for one chunk upload and 0.1980 ms for one cell creation. The maximum
complete cold tick was 36.1054 ms. This one empty-car run is not a scale or
populated-world capacity result.

Local evidence consists of `samples.jsonl`, `result.json`, `summary.json` and
`probe-log.txt` under
`artifacts/scenario-agent/road-turn-70d34a75-51f1-4344-8962-b325fa6fd9ad/`.
The summary was taken after the final tick metrics settled; 141 external
snapshots establish the captured trajectory and ownership observations. The
disposable server then acknowledged quit, completed world saving and shutdown,
exited with code zero, and closed its RCON port.

This proves one native road turn, arrival braking and cleanup on a verified
empty asphalt course. It does not establish traffic-lane behavior, dynamic
obstacle or player-car collision handling, multi-car scale, ownership transfer,
late joins, NPC passengers, or ordinary-client rendering of this turning run.
The earlier straight run has a separate one-client visual report; it cannot
substitute for observing this course. No clients were connected for the road
test, and `client_validation=not_observed` remains accurate. NPC vehicle
execution and deployment to a normal world remain disabled.
