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
world name or client side is refused. Distance is limited to 12 tiles and target
speed to 5 km/h. Heading is a native rotation around the vertical axis: 0 faces
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

After two seconds settling, the probe applies low native engine force on a
straight line, brakes at the distance cap or a 15-second drive deadline, holds
the stopped car for three seconds and removes it. The complete body lifetime is
limited to 30 seconds. It aborts for unloaded terrain, authority change, an
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

One empty SmallCar settled on the road, drove from `(10756.5,9856.5)` to
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
