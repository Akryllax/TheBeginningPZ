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
outdoor clear corridor, and sends one chunk's collision data per tick. It then
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
