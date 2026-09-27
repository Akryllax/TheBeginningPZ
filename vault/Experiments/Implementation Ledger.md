---
type: experiment-ledger
status: first-week-implementation-in-progress
updated: 2026-09-27
---

# Implementation ledger

This ledger separates requested design from measured implementation evidence. The deployed
0.1.0 prototype remains an observation world. The 0.2.0 First Week implementation is under
development; it is not a complete or multiplayer-validated release.

## Brake failure on a longer clear-view course — 2026-09-27

Requested: a faster native crash on a different street, then damage the brakes while the
controller still attempts to stop. The isolated experiment now supports an explicitly marked,
straight extended impact course (maximum 320 tiles, 80 km/h ceiling). Ordinary routes retain
60 tiles / 50 km/h limits. Eleven or fewer 9-chunk loading anchors retain the corridor; native
map width is capped at 45, retained chunks at 192, road tiles at 1500. Forward safety work has
an explicit 1024-tile ceiling and 20-second forecast in this experiment only. No engine power,
velocity, impact damage, core files or client Java agent were modified.

Empty-server evidence: `artifacts/scenario-agent/brake-failure-impact-empty-20260927_100456/`.
JAR `e38ec046e2066ae2baaeadf5f8d8acfaca3729918836c561d79a0e513532e28e`.
Course: X 10588.5, Y 9900.5 to 10220.5; tagged pole at 10588,10060; observer
10605.5,10060.5. Original ground data and a bounded live inspection found no trees, walls or
fences across the side-view area. Real client visibility remains pending.

The car reached its observed stock 70 km/h maximum. At progress 129.56, speed 69.81 km/h,
all four brake part/item conditions were set to zero through stock APIs; available braking
force fell from 80 to 20. Zomboid retains residual braking at zero condition. The controller
requested 80% braking and native force was 16. Last pre-impact sample was 51.50 km/h;
this is a sampled approach speed, not an exact contact-speed measurement. One real crash
registered severity 40.43606 and one `VehicleCrash` sound request. Hood/windshield fell
100->72; headlights 100->69. Engine remained 100: **this was not a destroyed car or NPC death**.
There is still no living occupant in this fixture.

The damaged vehicle remained for 20 seconds. Tagged pole and native body were cleaned;
three other loaded vehicles retained their coordinates. Warm mean 0.219 ms, p99 upper bound
0.7 ms, maximum safety scan 0.759 ms; cold vehicle creation reached 17.01 ms. Full Java
fixtures and 191 Python tests passed; logs `artifacts/brake-failure-agent-tests.log` and
`artifacts/brake-failure-python-tests.log`. Private operator harness and receipts are under
`artifacts/`; brake degradation is an operator test, not an enabled ambient incident feature.
Ordinary-client observation of this particular run remains pending.

## Confirmed low-speed crash and faster impact preparation — 2026-09-27

The user confirmed the ordinary client saw and heard the 5 km/h pole collision:
“It did crash, with sound.” Evidence: `artifacts/scenario-agent/pole-client-contact-20260927_094426/`.
One native crash, one sound request, zero feedback errors; hood 100->96, windshield
100->97 and left front door/window damage. All 16 other loaded cars retained coordinates,
no new client error lines, tagged pole and managed body cleaned. This is one-client
collision/audio acceptance, not two-client consistency or car-to-car contact.

At the user's request, a faster scene now uses the normal drivetrain, a reviewed 60-tile
straight asphalt route and a 50 km/h ceiling. It explicitly exempts only one marked pole
at one configured tile from avoidance. Missing/wrong/duplicate targets, other obstacles,
bypasses and junction stops remain rejected. Every connected player must stay at least
12 tiles from the route; entering that envelope requests braking. A stock crash stops
driving immediately, retains physics/braking and holds the damaged car for 20 seconds.
No velocity, damage or part-condition injection is used. Normal route configuration clears
both impact target and token so the exemption cannot leak into another course.

Agent `21286219739b4f17157514815136239a02b4f99dac8bc3494c66370a7b81fe21` passed all Java
fixtures and 190 Python tests. Empty-server evidence:
`artifacts/scenario-agent/high-speed-impact-empty-20260927_094922/`. Actual peak 35.41 km/h,
one native impact with stock severity 27.367 and `VehicleCrash2`, hood 100->75,
windshield 100->84, headlights 100->79 each. The 50 km/h ceiling was not reached on
this short course. Normal braking/viewing hold completed, exact fixture removed and native
body count returned to zero.

The client retry at `artifacts/scenario-agent/high-speed-impact-client-20260927_095052/`
reached **38.81 km/h**, recorded one stock crash (severity 31.122), and broadcast
`VehicleCrash`. The user confirmed: “Collision, damage and sound look right.” No new
client error lines; all other loaded cars kept their coordinates. The 20-second viewing
hold and exact fixture/native cleanup completed. This accepts the one-client faster pole
impact. It does not validate native car-to-car impacts or two-client consistency.

## One-client pole-detour refusal — 2026-09-27

The ordinary client observed the corrected approach, wait and rejection of the pole-blocked
shoulder. User report: “Smooth stop; stays visible.” Evidence:
`artifacts/scenario-agent/pole-client-denial-20260927_093229/`, final agent
`36971f0a24ebb65e8c2d25eadcf442a7a74391d3cd7bcfb8ff1e0f3883bde157`.
The willing driver repeatedly rejected `candidate_physical_shape_obstacle`; it did not
attempt the shoulder. Both exact parked fixtures and the moving probe were cleaned up,
with native body count zero. This accepts visible avoidance refusal only. A separate
low-speed pole impact is prepared for client collision/sound validation; no client impact
acceptance is implied yet.

## Pole collision activation, planning exclusion and crash feedback — 2026-09-27

The original map and live square at (10753,9864,0) both identify the pole as
`appliances_com_01_94`, with `PhysicsShape=Tree`, `StopCar` and `HitByCar`; the live
square has no `CarSlowFactor`. The planning bug was ignoring vehicle-collision metadata
on otherwise walkable tiles. Live and offline checks now reject those declarations and
legacy column sprites, while retaining the exact Floor exemption. The approved asphalt
approach remains valid; the original shoulder path is blocked by the pole.

Native inspection independently found that headless ServerCells store uploaded shapes but
never activate their obstacle bodies. Their separate flat ground supported driving and
masked the missing obstacles. The native server collision mask also excluded vehicle-to-
vehicle contact. The isolated probe now initializes its exclusively owned Bullet world
with the ordinary collision mode and one bounded ChunkMap; Java remains a dedicated server,
using ServerMap and normal authoritative replication. Existing global offsets are preserved.
No native/core files or client JVM flags changed. Missing custom mesh registrations fail
bounded chunk preparation; loading arbitrary mesh assets is not implemented here.

Actual stock crash calls already apply server damage. Their SoundManager path was silent
on a dedicated server. An identity-, thread- and authority-scoped hook now broadcasts the
matching existing sound only when the managed body's stock crash method executes. It does
not inject collision, change speed/pose, or apply duplicate damage. Exact class hashes and
bytecode/scope fixtures guard this integration. The `inspect` control loads the reviewed
area without creating a body, for bounded read-only scene inspection.

Native runtime evidence:
- `artifacts/scenario-agent/pole-native-baseline/`, agent `7b1201959ca35b5b2f731dec63df8202f5ab689fde98674b4ea197363d397030`:
  ordinary 44-tile route arrived, peak 33.34 km/h, no crashes, native cleanup zero;
  measured warm hook maximum 1.74 ms. This is a short-course peak, not sustained cruise.
- `artifacts/scenario-agent/pole-contact-proof/`, same agent: an empty-server private
  low-speed experiment inserted one marked temporary pole after initial road validation.
  At 5 km/h the car made real native contact and stopped at X=10738.82, before the pole
  at X=10740.5. Stock crash count 1, severity 1.487, hood 100->99, windshield 100->96;
  one `VehicleCrash1` broadcast requested, feedback errors 0. The exact tagged pole and
  managed car were removed, native bodies returned to zero. No sound was heard/validated
  by a client during this empty-server run.
- `artifacts/scenario-agent/pole-bypass-denial/`, agent `c31d20bea002e106ba6060c9d894b478bb8f7b2fafe3b38464bab41398a0c15b`:
  willing driver approached two road obstructions, waited, then rejected the shoulder
  with `candidate_physical_shape_obstacle`. No pass or crash occurred. All three preloaded
  cars retained exact coordinates through native initialization and the test; all fixtures
  cleaned. One native map created/removed, native bodies zero. Warm hook p95 <=1.1 ms,
  p99 <=1.8 ms, maximum 11.54 ms; this does not measure all engine physics work.

Automated checks: 190 Python tests and the complete Java fixture/build suite passed,
including missing-mesh rejection outside the planned route and crash-hook scope/bytecode
checks. Logs: `artifacts/pole-project-tests.log`, `artifacts/pole-agent-tests.log`.
Final agent SHA256: `36971f0a24ebb65e8c2d25eadcf442a7a74391d3cd7bcfb8ff1e0f3883bde157`.
Final-build runtime smoke: `artifacts/scenario-agent/pole-native-final/`; route arrived,
no crashes/errors, native bodies/maps cleaned to zero. Warm hook p95 <=0.4 ms,
p99 <=0.5 ms, maximum 1.79 ms. The later container shutdown occurred after completion
and logged save completion; it is separate from the successful drive receipt.
Private native and bytecode evidence is retained under `artifacts/`; no proprietary code
belongs in the source commit. Ordinary parked Java vehicles still need separately owned
native bodies, so physical car-to-car impact and fleet cleanup remain pending. Client
presentation/audio and two-client consistency are separate, unperformed checks.

## Client correction: road avoidance works, pole collision fails — 2026-09-27

The client clarified the outcome of `shoulder-denial-pass-20260927_000818`: the moving
car successfully avoided the road obstacles, but phased through a pole on its avoidance
path. Preserve this distinction: the maneuver worked; physical collision acceptance failed.
Do not infer collision safety from route arrival, no client errors, or geometric fixtures.
Further shoulder trials are on hold pending collider investigation; this is an operational
hold, not evidence that the runtime shoulder option has been removed or disabled.

Read-only inspection shows chunk upload handles built-in `PhysicsShape` values separately
from custom `PhysicsMesh` values. Custom mesh upload depends on a registered Bullet mesh
index; a missing index skips that mesh. The probe calls chunk upload, but does not explicitly
initialize the mesh registry. This is a candidate cause, not a confirmed diagnosis of this
particular pole. Private research: `artifacts/decompiled/collision-shapes/`.

The same client-present run independently preserved all 17 preloaded vehicle coordinates
(maximum displacement 0.0). The saved control car and all test fixtures were removed.
A single warm hook outlier reached 145.906 ms (p95 <=0.4 ms, p99 <=0.8 ms); its cause is
unresolved. Collision shapes, native contact response and impact feedback must be verified
before further shoulder acceptance or managed multi-car trials.

## Shoulder refusal/pass and server coordinate frame — 2026-09-27

The next native shoulder test exposed two limitations in the original fixed detours.
First, the original route retained a nine-tile observation margin, while a shoulder detour
needed another chunk row. Passing routes now retain a bounded thirteen-tile margin up front;
candidate geometry still must fit entirely within that retained coverage. Second, the
RCON-created parked cars faced across the road. The inflated footprint correctly rejected
the narrow gap beside one of those cars. The revised fixture uses the game's debug factory
and sets a road-parallel heading before its first update; clearance was not weakened.
Evidence of the failed trial and exact fixture recovery is retained at
`artifacts/scenario-agent/shoulder-denial-pass-20260926_235516/`. Two fixtures had unloaded
before cleanup; their exact saved locations were reloaded and checked before native removal.
The successful harness removes fixtures during its terminal viewing hold, before unloading.

An independent inspection found unrelated parked vehicles with invalid coordinates in the
older disposable world. Its private historical database snapshots show repeated 64,000-tile
shifts. The late `WorldSimulation.create()` call changes global offsets after headless
vehicle transforms already exist. The probe now initializes its own native server world
in the existing coordinate frame, without changing global offsets or any vehicle pose.
It rejects a preexisting unowned native world. Source/bytecode evidence is under
`artifacts/decompiled/physics-world-offset/`; historical rows are recorded in
`artifacts/scenario-agent/frame-offset-investigation.json`. The old disposable world is
preserved, not repaired or reused for this proof. No playable or production world was touched.

Fresh world: `LofersVehicleProbe_20260926_235220_a91875`. Final tested agent SHA256:
`b13b83912a67839c1c96d01e7e42c86faa4c3734eafa634ade45f4407fd7a090`.
The new shoulder course at X=10732.5–10776.5, Y=9861.5 uses explicitly supplied installed
erosion definitions to resolve decorative road cracks. Text and binary inputs are hashed;
conflicting duplicate tile definitions remain unknown and fail surface validation.
Automated checks: **170 project tests** plus all Java fixtures, including the actual
chunk-boundary regression and blocked-shoulder geometry. Logs:
`artifacts/shoulder-project-tests.log`, `artifacts/shoulder-agent-tests.log`.

Empty-server evidence: `artifacts/scenario-agent/shoulder-denial-pass-20260927_000257/`,
epoch `a20db534-c1f3-47df-9d12-cdd7cbac5184`. A deliberately selected willing test personality
first refused the blocked shoulder. After two unsuccessful requests, only the shoulder
obstruction was removed. Both road fixtures remained. The next request passed along the
right shoulder, rejoined the lane and reached the destination. Shoulder peak was 7.43 km/h,
maximum Y=9864.6182; full-course approach peak was 17.55 km/h. Both retained parked fixtures
kept identical positions. All sampled coordinate offsets stayed zero. Warm hook p95 <=0.4 ms,
p99 <=0.6 ms, max 2.975 ms. Native body count returned to zero; all three obstruction
fixtures were removed. One separately recorded stationary control car was saved at
(10735,9855) for a restart/client-present initialization check. This is not client visual
acceptance or a complete preloaded-vehicle preservation proof; those checks are pending.

Two-car execution still requires a shared native-world/cell lifetime and one batched
reservation coordinator. Do not instantiate two independent copies of the single-car probe:
its body-count and terrain-ownership assumptions deliberately exclude that use. Moving
managed-car clearance, changes during a pass and opposing-driver fairness remain live gates.

## Parked-car passing, audible horn and optional shoulders — 2026-09-27

The user requested temperament-dependent passing, then clarified that willing drivers may
attempt off-road avoidance while respecting physical obstacles. The opt-in straight-road
probe now proposes two bounded cubic detours on the private worker thread. It validates
loaded surfaces incrementally, rejects stale observations, checks oriented vehicle
footprints and reserves the entry/pass/rejoin corridor before applying native controls.
Release requires the actual padded footprint to clear the reserved corridor. The static
obstacle remains in place for the whole maneuver; the original destination is preserved.
Road passing is capped at 15 km/h and retains vehicle capability constraints. Geometry work
is separate from the game thread. The probe seeds personality with its command identity,
avoiding reuse of a recycled native vehicle ID; residents still need durable resident seeds.

The separate 44-tile course runs east along Y=9861.5 from X=10666.5 to 10710.5. The offline
reader checked both lanes across 294 tiles. An earlier site had unknown decorative crack
definitions and was rejected without weakening the reader. The parked fixture spawns at
command (10689,9862,0), actual centre approximately (10688,9861.8984). The observer stood at
(10688.5,9864.5); the first artifact's 9866.5 suggestion was behind a fence and has been
corrected in the baker. Original evidence artifacts retain their original hashes/metadata.

Automated checks passed: **168 project tests** plus Java compatibility, premain, protocol,
native-asset, route/controller, reservation and new bypass fixtures. New coverage includes
oriented clearance, denial when both sides are blocked, independent physical approach/rejoin
simulation, terrain rejection, stable off-road choices and invalid route flag rejection.
Private logs: `artifacts/traffic-bypass-project-tests.log` and
`artifacts/traffic-bypass-tests.log`.

Empty-server evidence: `artifacts/scenario-agent/native-parked-bypass-20260926_232324/`
passed with agent `6f91bc76051a77ab00565610235974b404ad9e0dab7cdbf3e03ae07292c4e08f`:
27.90 seconds waiting, left pass, lane return and arrival. The following run,
`native-parked-bypass-20260926_233003/`, used the final deployed agent
`7969caf6661ea2e458dcb878b5aba476bcd19178d3834241fd8076e8c9dbe545` and epoch
`61e2a134-7e55-42b2-8c52-b8c6f562a1ed`. It waited 18.50 seconds, passed on asphalt,
rejoined and arrived; peak 18.33 km/h. Detached geometry took 10.89 ms. Warm hook
p95 <=0.6 ms, p99 <=1.3 ms, max 3.716 ms. Native bodies returned to zero and the exact
parked fixture was removed. These runs had no clients.

One-client evidence: `artifacts/scenario-agent/client-parked-bypass-20260926_233245/`,
same final agent and epoch, akr connected. The user confirmed **smooth pass and lane return;
heard a honk**. The car waited 20.60 seconds at (10679.2422,9861.5), passed on the left
to a minimum Y=9858.3984, rejoined, and arrived at (10709.8359,9861.5). Peak 16.56 km/h
on the complete course. All 83 waiting and 37 passing snapshots retained body, world,
registry and chunk membership. Three sampled snapshots had native horn state enabled;
audibility is established by the separate user report. There were **zero new client error
lines** in the captured console interval. Detached geometry took 15.40 ms; warm hook
p95 <=0.4 ms, p99 <=0.9 ms, max 4.485 ms. Cold hook max was 14.491 ms, dominated by
vehicle creation; these are added probe hook measurements, not total server tick timings.
One bypass completed, its reservation cleared and native body count returned to zero.
The exact parked fixture (native 245, persistent row 20) was removed through the game's
method with identity/rest/occupancy guards and verified absent from the database. The
temporary server Lua extension was restored byte-for-byte. No playable or production world
was changed. The disposable server remains available, with no active test car.

Shoulder fallback is implemented and fixture-tested but **not natively demonstrated**.
Road candidates are preferred; one stable episode roll can admit known dirt/grass/paved
edges at 6–8 km/h with 60% planning braking/lateral preferences. These are conservative
preferences, not measured terrain friction. Walls, actors, vehicles, supported ground and
a lane rejoin remain mandatory. Next gates are native shoulder success/denial, managed
multi-car arbitration, changing obstacles/rejoin blockage, native impact/damage, resident
commutes and two-client agreement. The C++ planner still does not drive this probe.

## Planned stopping, queue persistence and traffic personality — 2026-09-27

The longer-held obstacle trial still failed the user's visual acceptance: the car accelerated
about two tiles, stopped abruptly with a distant untouched obstacle, then appeared to disappear
after about five seconds. Private evidence `client-parked-visible-20260926_224219/` recorded
20 seconds of native-body presence but did not establish the client's view or world/registry
membership. Do not reinterpret that trace as a passed visual test. The specific parked fixture
was removed through the game's method and verified absent from the database.

Cause of premature braking: every finite predicted contact triggered full emergency braking,
even a parked car several seconds away. `ParkedObstacle` now computes a bounded geometric
stopping point; `ProbeDriver` uses the existing capability-aware speed/braking envelope,
waits without a stall failure and resumes its original route when the obstacle clears.
Scheduled stops are preserved. Moving, occupied, client-owned or unknown hazards retain
the stricter response. Queue time is excluded from the route deadline and bounded to five
minutes in this disposable harness. World, native registry and chunk membership are checked
and published separately from native-body existence. These fields are not packet receipts.

`TrafficTemperament` and `TrafficBlockage` provide stable preferences, optional one-episode
honking, patience and staggered bounded bypass requests. Stock server horn start/stop calls
are used; no client patch or replacement game class is involved. The probe uses native car
identity as its test seed; integrated residents will require their durable resident seed.
`TrafficPassReservations` has a bounded four-car arbitration model: oldest waiter wins an
overlapping corridor, stable ID breaks ties, native generations/token matches prevent stale
release, unused leases expire, and entered stale leases retain occupancy while stopping
permission. Fresh matching observations may reconcile the owner; confirmed exit releases
the corridor. It is not yet wired to a native multi-car executor. No bypass is executed by
this iteration. [[Design/Traffic Incidents]] records the accepted wait/honk/pass behavior,
safe opposing-lane use, conflict resolution and bounded imperfect-driving requirements.

Automated: compatibility/premain/protocol/native-asset fixtures and existing turn simulations
pass, plus independent parked-approach simulations for normal and weaker/heavier cars. They
wait 35 seconds without stalling, preserve the stop sign, then finish after obstacle removal.
Tests cover horn probability/delay, deterministic traits, bypass retry pacing and competing
reservations including stale occupied corridors and generation recovery. Evidence:
`artifacts/parked-approach-reservation-tests.log`. This is not multi-car physics validation.

Empty-server native evidence: `artifacts/scenario-agent/native-parked-queue-20260926_225816/`,
deployed agent SHA256 `6979d5977be560fe18274c16e2600418119d6bf4fc798c4f840147f194e54e7f`,
epoch `02c56483-d54d-454f-9eab-416ee4918588`. The car traveled 23.16 tiles and stopped at
(10806.6641, 9861.5), roughly 6.35 tiles from the parked fixture centre. It waited 40.69
seconds, retained every sampled body/world/registry/chunk check, then resumed and completed
the original route and stop after exact fixture removal. Peak 22.25 km/h; native body count
returned to zero. Warm hook p95 <=0.5 ms, p99 <=2.4 ms, max 4.192 ms. No client was connected.

One-client evidence: `artifacts/scenario-agent/client-parked-queue-20260926_230154/`, same
deployed agent and epoch. The user confirmed **smooth stopping and continued visibility**,
with no horn heard. The car stopped at (10806.5625, 9861.5), waited 66.34 seconds, then the
server recorded resumption, one served stop sign and `route_arrived`. Peak 24.43 km/h;
native bodies returned to zero. Warm hook p95 <=0.2 ms, p99 <=0.4 ms, max 3.529 ms. This
run produced no new client error lines. All 262 sampled waiting snapshots retained native
body, world, registry and chunk membership. This
driver's episode roll was 0.8770 against a 0.4913 horn chance, so silence was selected rather
than a failed sound invocation. Audible horn replication still needs a selected-horn trial.
The user accepted the approach and wait; post-removal driving is server trace evidence.
Both fixtures were removed through exact-ID/script/position/rest/occupancy-checked native
removal; the one-use test Lua extension was restored byte-for-byte. Production and playable
worlds were not changed. Passing, moving traffic, intentional native crashes, integrated
resident trips and two-client agreement remain separate pending gates.

The final source/build additionally contains the detached reservation arbiter and reserves
enough road deadline for the full five-second brake timeout plus 20-second viewing hold;
legacy straight probes keep their original eight-second reserve. The running test epoch
retains the explicitly recorded earlier JAR. Final source will load on its next restart;
do not attribute the native/client receipts above to an unrun binary hash.

## Observed smooth turn and vehicle-capability iteration — 2026-09-27

The first observed Bézier/stop run reached its goal but **failed visible smoothness**.
The user heard/saw braking during the turn. The trace showed a second, abrupt 5 km/h
curvature threshold and two full-brake commands caused only by cooperative scan timeouts;
speed fell to 1.31 km/h. Evidence: `artifacts/scenario-agent/client-suite-20260926_215614/`.

Removed the duplicate speed threshold, made service-brake corrections continuous, and
replaced live scan timeout braking with hard operation limits plus duration telemetry.
An empty-server run then held 5.870–5.904 km/h through the measured bend with zero braking.
Evidence: `artifacts/scenario-agent/continuous-turn-2e28eb76-82f4-483e-bd9e-b2241346006a/`.
The next one-client run held 5.869–5.902 km/h with zero bend braking; the user explicitly
confirmed an excellent, smooth turn, but rejected the low speed. It completed the configured
stop and used the correct exit lane, then stopped for `actor_in_vehicle_path` near the goal;
do not report a clean `route_arrived` result for this run. No new client errors were recorded.
Warm hook p95 <=0.2 ms, p99 <=0.3 ms, max 0.415 ms; maximum safety scan 0.358 ms.
Evidence: `artifacts/scenario-agent/client-continuous-turn-20260926_221020/`, agent SHA256
`3e155fed42dc09c6e04baab0e8e5313d3f20b3ed38dfb5152cf87e1bff77b783`.

The parked-car trial exposed a real adapter bug: `getOwnVehiclePhysics` throws
`Vehicle not found` for a normal parked car without a server native body. The probe failed
before moving and removed its own body. This was **not successful blocker braking**.
Evidence: `artifacts/scenario-agent/client-parked-obstacle-20260926_221214/`.
The parked fixture was spawned only in this disposable world, at approximately
(10803.0, 9861.8984); its SQLite persistent ID is 19, absent from the before-test snapshot.
Replacing the lookup with bounded native snapshot enumeration distinguishes body absence
from invalid/incomplete observations. Unknown moving or client-owned cars still stop the probe.

The user requested faster driving with a roughly 50 km/h street limit, while explicitly
respecting each car's engine power, rates and properties. The revised adapter uses the
installed drivetrain/gear/RPM and service-brake routines, caps requests by their outputs,
applies loaded mass to Bullet and respects script steering limits. Curvature and braking
preferences now use observed vehicle capabilities; details and limits are in
[[Design/Navigation]]. The test course remains too short to prove sustained 50 km/h travel.
The earlier smoothness pass applies only to the 6 km/h bend; faster results require separate
evidence. Raw source reconstruction remains private under
`artifacts/decompiled/vehicle-capabilities/`; no engine implementation is redistributed.

Automated checks: 163 project tests and 116 Observer tests/frontend build passed before
the capability changes. Native C++ core and 13 actual socket scenarios also passed, including
32-resident/30-batch round trips at p50 14.741 ms, p95 18.688 ms and max 20.723 ms. These
detached worker tests do not connect its plans to the current test car. The capability
iteration passes all **164 project tests** (including 31 targeted Python checks), Java compatibility/protocol/premain/native-asset
fixtures and ten independent bicycle simulations, including a weaker, heavier vehicle.
Private logs: `artifacts/all-test-project-observer.log`, `artifacts/all-test-native-worker.log`,
`artifacts/vehicle-capabilities-tests.log`. Native/client capability calibration is separate.

The first native-snapshot calibration rejected the JNI header's fractional integer encoding
(observed ID 253.1 / wheel count 4.1). The parser now follows the pinned engine's truncating
read, with finite/range/duplicate/completeness checks; a regression fixture includes this
encoding. The subsequent native parked-car test successfully detected `predicted_vehicle_contact`
and stopped at (10785.65625, 9861.5), about 17.35 tiles from the stationary fixture centre.
Peak speed 11.733 km/h, cleanup native body count zero; no client was connected. Warm hook
p95 <=0.5 ms, p99 <=2.0 ms, max 1.972 ms. Evidence:
`artifacts/scenario-agent/native-parked-fixed-20260926_222813/`.
This proves conservative detection/braking for one static obstacle, not contact/damage or
moving-traffic avoidance. A one-use native Lua removal attempt did not remove the fixture;
the original test Lua file was restored. After graceful shutdown, the recorded fixture row
alone was removed from a backed-up disposable vehicles database; all other blobs were
unchanged and SQLite integrity passed. No running save or player vehicle was edited.

The clear-road native capability run then completed with agent SHA256
`6b2e7f6664f80d6b48bb89410ab6b9d2b66b2b7500cae53bd455907a444fee5b`, epoch
`9b12adff-2a7d-4dcc-b22c-978f60f21dfb`. The street ceiling was 50 km/h; actual peak was
22.873 km/h on this short stop-sign course. Six 250 ms samples in progress 33–39 measured
11.950–11.996 km/h and **zero applied braking** through the bend. The repaired SmallCar's
loaded mass was 934 kg, top-speed property 70 km/h and steering slew limit 0.9 rad/s;
its observed tyre grip reduced the lateral preference to 1.875 m/s². Sampled engine/brake
forces never exceeded the installed drivetrain/service-brake outputs. These are sampled
limits, not an exhaustive proof for all vehicle states. One configured stop completed,
the car finished in the correct lane at (10820.5, 9839.1171875), and native bodies returned
to zero. Warm hook p95 <=0.4 ms, p99 <=1.9 ms, max 2.033 ms; cold creation 22.547 ms.
Evidence: `artifacts/scenario-agent/native-faster-turn-20260926_223200/`.
The subsequent one-client run completed and the user confirmed **“Speed and turn feel good.”**
Peak speed was 24.105 km/h; six measured bend samples held 12.910–12.945 km/h with no applied
braking. It served the stop, used the correct lane, cleaned up its native body and generated
no new client errors. Sampled engine/brake output stayed within native available force.
Warm hook p95 <=0.3 ms, p99 <=0.4 ms, max 0.733 ms. Evidence:
`artifacts/scenario-agent/client-faster-turn-20260926_223439/`, same agent/epoch. Sustained
50 km/h travel and two-client agreement remain untested.

The repeated client-present obstacle trial detected the parked car and stopped after
2.66 tiles, roughly 17 tiles short of it, with zero new client errors. The user saw the
moving car briefly accelerate and disappear, rather than clearly observing it stationary.
This is **inconclusive visual braking evidence**, despite the recorded safety stop.
Evidence: `artifacts/scenario-agent/client-parked-fixed-20260926_223605/`. Its exact parked
fixture was removed through the stock game method and confirmed absent from the database;
the one-use original Lua file change was restored. To make the next visual test useful,
road probes now remain in `stopped_visible` for 20 seconds instead of three before cleanup.
The legacy straight probe retains its shorter timeout. The next obstacle position will
allow a longer visible approach; this harness change does not change the accepted controller.

No physical crash director, incident sound adapter or durable aftermath materializer was
enabled by these tests. Two-client consistency and integrated resident commutes remain pending.

## Bézier lanes, stop handling and traffic-incident foundations — 2026-09-26

Implemented bounded cubic trajectories with arc-length lookup, analytic heading/curvature,
signed deviation, velocity-scaled steering lookahead, longer speed preview and a one-second
bicycle prediction. Added a source-verified Muldraugh lane course: eastbound Y=9861.5,
northbound X=10820.5, 15 km/h cruise, and a two-second stop before the west-facing sign.
Its 192-tile oriented swept footprint passes both source and loaded-world validation.
The route is a reviewed artifact, not automatic map-wide lane/sign extraction.

Early native attempts stopped on the 1 ms cooperative road-scan budget:
`lane-stop-619eda16-3288-4131-a5d0-da69e8109fa2` and
`bezier-56a9b965-670d-4f41-8b69-8a9a5d90ebf0` under `artifacts/scenario-agent/`.
Deduplicated tile checks and replaced timeout failure with immediate braking until a new
complete scan succeeds; persistent starvation still fails. The scan budget was not raised.
Actual-body checks and the curved stopping corridor remain authoritative over static data.

Two corrected empty-server native runs completed on the pinned B42.20.4 build. The final
run includes the conservative moving-vehicle forecast adapter and uses agent SHA256
`2dcc881de532d4cd9d95739756efd644041213049ef1183ed8417117b99847c4`:

| Measurement | Final native run |
| --- | --- |
| Epoch | `0b40cd92-c5d2-4295-a10c-57ff6f2845b6` |
| Finish | `complete`, `route_arrived`, one configured stop completed |
| Maximum speed | 15.000 km/h |
| Final centre | (10820.5, 9839.171875), correct northbound lane |
| Largest sampled signed deviation magnitude | 0.3795 tiles; status sampled every 0.5 s |
| Warm hook samples / p95 / p99 / max | 400 / <=0.7 ms / <=3.0 ms / 4.254 ms |
| Cooperative scan-budget brake ticks | 10; acceleration resumed only after complete scans |
| Cold vehicle creation / cleanup | 30.579 ms / 8.593 ms |
| Native bodies after cleanup | 0 |

Private evidence: `artifacts/scenario-agent/bezier-0b40cd92-c5d2-4295-a10c-57ff6f2845b6/`.
The preceding successful run is `bezier-2bddac02-6508-4401-bdbe-a900228b9d9c/`.
These timings measure the added Java hook, not total native physics/server frame cost.
There were no clients or other moving cars in either run. Visible smoothness, moving-blocker
behavior and two-client agreement remain untested; ten brief brake interventions may be
visible and should be assessed in the next client trial.

Continuous swept-circle fixtures cover crossing blockers between samples, stale data and
invalid observations. The native adapter queries server-owned vehicle physics; unknown or
client-owned nearby motion stops the probe. It does not yet support normal lane passing,
moving pedestrian forecasts or general streamed traffic. Detached incident-admission tests
also cover every supplied player's reachable area, incomplete audiences and protected scenes.

Added the accepted [[Design/Traffic Incidents]] requirement: off-screen sound must have
discoverable aftermath at the same persistent location. The Lua model stores IDs, poses,
part conditions and fixed loot; accounts for reservations in the shared fleet; requires
a durable intent revision and saved-world receipt before sound; and retains interrupted
creation for reconciliation while suppressing uncertain audio replay. It is bound to the
scenario ModData restore path. **No durable checkpoint writer, native aftermath/sound adapter,
automatic crash scheduler or visible crash execution is enabled.** Table assignment and
mock receipts do not establish crash-safe game persistence. The first adapter must save
hidden wrecks before releasing sound. Native two-car contact/damage/audio is still pending.

Validation: **163 project tests**, Java compatibility/premain/protocol/asset fixtures,
analytic Bézier checks and nine detached stop-and-drive simulations passed. Evidence logs:
`artifacts/traffic-project-tests.log`, `artifacts/traffic-agent-tests.log`.
Lua tests exercise wrong/stale receipts, single-use dispatch, interrupted effects,
unchanged loot on restore, cooldown/capacity and shared fleet accounting. These are model
tests, not game-save restore or multiplayer tests.

Control responsibility remains split: C++ provides high-level planning/road routes; the
server Java agent executes the current probe's steering/braking through native physics;
Lua manages scenario/resident state. The probe does not consume the C++ worker's routes yet.
Only the disposable `.132:16281` probe was restarted. The playable `.132:16271` world and
production `.160` were not changed; the new Lua incident foundation is source-only.

## First client driver-model run — 2026-09-26

The single-client run was stopped after the user reported Error 13 and obscuring fog.
The earliest relevant client error was failure to open the vehicle script: Linux path
resolution requested `~/Zomboid/mods/home/akr/zomboid/mods/lofersdriverprobe/42/media/scripts/lofers_driver.txt`.
Subsequent vehicle-sound null-script and vehicle-update byte-count errors were recorded;
no successful visible driver replication is claimed. Private client log and server samples
are in `artifacts/scenario-agent/client-driver-20260926_205618/`.

Installed a narrow local directory symlink from that requested mod path to the original
`LofersDriverProbe` directory; no installed game files were changed. Client restart and
confirmation that the script loads remain required. The generic `stopweather` command
was insufficient to clear the reported fog. Added a disposable-world-only server Lua
fog override to the test package. After graceful restart, epoch
`31591e9e-25f0-4287-9a6b-18df379dcf95` logged actual fog intensity zero and the probe armed.
Fourteen targeted packaging/operations/visibility tests pass.

After the client rejoined, the next run completed with one client connected and no new
client log errors during the monitored course. Travelled 53.888 tiles; final position
(10818.5, 9839.1875), finish `route_arrived`. Evidence:
`artifacts/scenario-agent/client-driver-20260926_210235/`. Separate startup checksum
(`absPath:null`) and chunk CRC warnings remain recorded; no clean-startup claim is made.
The user confirmed a visible turn and cleared fog, but reported excessively slow driving,
a skipped stop sign and the wrong exit lane. Detailed seated-driver visibility is now low
priority by user choice. This is a partial one-client result, not traffic or two-client
acceptance; the next iteration addresses those specific failures.

## Commute foundations and original driver — 2026-09-26

- Added [[Design/Implementation Roadmap]] as the accepted continuation. Persistent
  schedule seed now has an explicit schema-1 to schema-2 migration; event RNG advances
  cannot shift residents' routines. Population accounting counts walking car owners as
  pedestrians and excludes parked cars from the moving-car budget.
- Both native planning threads share immutable road adjacency and spatial lookup. Graph
  content changes invalidate the cache even when a caller repeats its claimed identity.
  Expiring directed closures remain separate from baked terrain. Added Protobuf navigation
  identity, route node IDs, observed vehicle pose/entry and occupancy revision fields.
  Absent nested Protobuf messages now preserve absence through the Java codec.
- Planning uses the observed car position. A separate approach plan walks to its entry
  before the six-action commute. Unavailable cars cannot start driving; occupied cars can
  still plan parking/exit recovery. These are planner and protocol changes: the gameplay
  adapter does not yet publish verified car observations or execute integrated commutes.
- Created an original 204-triangle seated civilian and palette with a reproducible generator.
  The installed native Assimp importer accepted its normals/UVs/static geometry. Added an
  original vehicle extension referencing installed art and an asset-only disposable probe
  package. No game art, decompiled code, or replacement engine classes are distributed.
- Dedicated test world `LofersVehicleProbe_20260926_204822_20bc4e`, epoch
  `de51b9ac-a9c0-4800-8b0d-6ffbc23bcb7f`, completed the six-point asphalt course with
  `Base.LofersSmallCar` and its driver part enabled. Travelled 53.866 tiles; stopped at
  (10818.5, 9839.1953125), approximately 0.695 tiles from the goal. No probe error;
  native bodies returned to zero. Warm hook p95 <=0.4 ms, p99 <=0.7 ms; cold creation
  8.515 ms. These are hook timings, not whole engine/physics frame costs.
  Evidence: `artifacts/scenario-agent/driver-model-de51b9ac-a9c0-4800-8b0d-6ffbc23bcb7f/`.
  The first isolated attempt found an unsupported line comment in the vehicle script;
  fixed before this successful run. Existing upstream startup warnings remain.
- Validation: 122 project tests; native core and actual Unix IPC checks including closure
  expiry and route IDs; Java compatibility/premain/protocol fixtures and native asset import.
  Regenerated the private Muldraugh map index with its navigation identity. Worker load
  fixture (32 residents, 30 batches) measured p50 14.28 ms, p95 15.73 ms, maximum 23.11 ms;
  this is external round-trip latency, not added game-thread work.
- **Still pending:** client view of the driver (scale, position, visibility and replication),
  vehicle-profile connectors/turns, managed persistent vehicles, inventory-preserving
  boarding/exit, complete commute, human takeover and two-client validation. The original
  `LofersDriverProbe` assets are installed in the local client's mods directory for the
  prepared `.132:16281` test. No client Java agent is used. Normal vehicle execution remains
  disabled; playable prototype and production `.160` were not changed.

## Navigation and road-turn iteration — 2026-09-26

- Added [[Design/Navigation]]: original installed-map stacks, floor definitions and road
  geometry produce content-identified navigation chunks. Two bytes per tile record surface
  cost and conservative quarter-tile clearance. Asphalt beneath sidewalk overlays is blocked.
  Vehicle graph edges require a swept footprint and use a bounded clearance penalty; no
  diagonal shortcut across a sidewalk is accepted. The native worker still reads the
  existing prebuilt Protobuf graph; no worker wire-contract change was required.
- Initial Muldraugh coverage contains 1,081,600 tiles, 50,423 permitted asphalt tiles and
  24 chunks occupying 3,146,304 bytes. The conservative graph has 1,076 nodes and 2,426
  directed edges. This excludes uncertain/narrow surfaces and does not establish driving
  coverage of the whole map, lane discipline, traffic rules or collision avoidance.
  The final first bake including its prerequisite raster took 6.508 s; a validated cache
  open took 80.3 ms and cached graph generation 1.932 s. These are offline costs, not
  measured per-NPC A* latency. Exact reproduction commands are in the navigation page.
- The server probe now accepts bounded waypoint routes, retains bends, steers with native
  force/brake control, slows for turns and checks loaded road/actor/car hazards. The probe
  is still one empty car in an isolated world. Route configuration is serialized with
  start/stop/preparation and allowed only while stopped; previous private configuration
  and source-route provenance are preserved.
- The native physics guard now hashes the first library the pinned JVM would actually
  select, using its startup-captured library paths. Mutable Java properties cannot mask
  an incompatible startup path. Eight subprocess path cases, premain, the pzexe bootstrap,
  Java IPC and controller fixtures passed. Core game files remain read-only.
- **Actual server-native asphalt turn passed**, with zero clients connected, agent SHA256
  `f135a657cc1f4f119ffd9d18d8e35bcc72114567bc84844caf982d6716763c51`, epoch
  `70d34a75-51f1-4344-8962-b325fa6fd9ad`. The six-point planned route was 55.2117 tiles;
  physical travel was 53.8780 tiles, including corner rounding and arrival tolerance.
  Heading changed from 90 to 180 degrees, maximum speed was 4.0002 km/h, and final speed
  was 0.05275 km/h at `(10818.5,9839.1796875)`, 0.6797 tiles from the requested endpoint.
  All 307 planned road tiles passed loaded-world validation. Largest captured cross-track
  distance was 0.5933 tiles (sampled evidence, not an every-tick maximum). Native bodies
  returned from zero to one to zero, and all three owned terrain cells were removed.
  Final status was `complete`, reason `route_arrived`, with no error.
- The measured 650 active warm probe ticks had mean 0.1263 ms, histogram p95 upper bound
  0.4 ms, p99 upper bound 0.6 ms, and maximum 1.0824 ms; none exceeded 2 ms. These timings
  cover the hook's control/check work, not total game/Bullet/network frame time. Cold
  vehicle creation cost 35.45 ms and cleanup 12.36 ms. A single empty-server car is not
  evidence of production capacity. Raw private evidence is under
  `artifacts/scenario-agent/road-turn-70d34a75-51f1-4344-8962-b325fa6fd9ad/`.
- Final validation: **116 project tests passed**, alongside native core/real IPC and
  the Java/controller/bootstrap fixtures. Review found and fixed stale prerequisite road
  masks when source bytes changed without size/timestamp changes; a real binary-map
  regression reproduces that case. Full-source identities now namespace all navigation
  and route-evidence prerequisite masks. Geometry sampling rejects nonfinite parameters
  and caps the total route at 32,768 samples before doing work.
- The disposable server acknowledged quit, logged completed world saving and shutdown,
  exited with code zero, and closed its RCON port. Final navigation/graph identity is
  `f086062b762a6096d7d6123b33d43b36ad0d75061c5ef27f7aac36494f95b366`.

This is a prepared and exercised turn controller, not full civilian driving. No client
observed the turn. NPC driver presentation, normal-world collision/ownership integration,
late joins, two-client comparison and the accepted full scenario remain open gates.
The original playable prototype and the `.160` production deployment were not changed.

## First Week iteration — 2026-09-26

- The accepted target is [[Design/First Week]]: calm civilians, manual outbreak, seven-day
  progression, real civilian/emergency driving and persistent survival behavior.
- The user selected [[Decisions/0004 Server Runtime Extensions]]: server JVM injection is
  allowed, clients use ordinary Lua, installed core game files remain unchanged. The client
  Java prototype was never installed or launched and is superseded.
- The C++20/Lua planner, bounded Protobuf IPC, server bridge, admin UI, map-index generator
  and initial resident/action model are implemented. The worker passes core and Unix-wire
  tests and an independent Java round trip. Real-map batches of 32 driving residents measured
  42.65 ms median / 44.90 ms maximum IPC roundtrip in the recorded synthetic benchmark.
- A six-step native planner → actual Lua `Model.acceptPlan`/`Server.receipt` fixture passed
  for road parking followed by walking to a building 25 tiles from the road. This checks
  acceptance semantics, not actual vehicle movement.
- The guarded JVM fixtures pass, including the real bundled pzexe launcher-discovery stage.
  The initial disposable boot exposed and led to fixing that startup-stage distinction.
- Added private First Week fields to Observer diagnostics. Five focused Python tests,
  frontend build and Java/Python fixtures for five exporter failure modes passed. New output
  is built locally; the playable world's mounted exporter has not been replaced.
- Installed pinned Vineflower 1.12.0 and added [[Runbooks/Java Inspection]], a focused skill,
  reusable decompile command, exact `javap` output and per-run provenance. Vehicle classes
  decompiled without warnings; game-derived output is ignored and excluded from distribution.
- Prepared and booted the disposable `LofersVehicleProbe_20260926_185224_d59d95` world.
  RCON responds with zero players. It has no game mods or client agent requirement, and
  core game files are mounted read-only. The first explicit attempt initialized the native
  library/world and registered a body, but the car fell below the terrain before driving.
  The height guard stopped the attempt and restored the native body count to zero.
  A second attempt explicitly activated the road's native chunk map and uploaded 20 chunks;
  it also fell below the terrain (physics Z −0.767), with no horizontal travel. Cleanup
  again restored zero native bodies. Native inspection then established that server physics
  reads a separate list of cells, each five game chunks wide, instead of client chunk maps.
  Creating the two required native server cells resolved the ground-contact failure.
- **The bounded server-only motion probe passed** with agent SHA-256
  `106c3f900cb80a89e755ab881a9f9a81ae61b0d6980cca1f79198c09d97599b3`:
  settled → drove → braked → stopped → cleaned up. The car traveled 10.0547 tiles along X,
  reached 4.8211 km/h, stopped at 0.05275 km/h and retained physical Z 0.13951.
  Native vehicle bodies returned from one to the zero baseline, and both owned native
  terrain cells were removed. The probe recorded 175 active physics frames and 176 dirty
  position publications; these are not packet measurements. It used the normal physics
  update path, with no extra simulation step, fake players or coordinate-driven movement.
  No client was connected. See `scenario-agent/VEHICLE_PROBE.md` for the exact pinned native
  library and evidence paths; normal-world integration, collision ownership and NPC seats
  are separate unpassed gates.
- **One ordinary client observed visible, smooth movement** in a subsequent run with one
  player on port 16281. The player identified the path as the sidewalk and initially
  described brief acceleration before disappearance. This was a fixed straight test strip,
  without road-following steering; removal after the short stopped hold was intentional.
  Server evidence recorded 10.09375 tiles, continuous Server/-1 ownership in captured live
  samples, no error, zero remaining native vehicle bodies and both native cells removed.
  Client-visible duration and interpolation latency were not measured, and no second
  client participated. The one-client peak probe tick was 170.6 ms, so no production
  performance claim follows from this test.
- Connected the workspace to `Akryllax/TheBeginningPZ`, retaining remote main commit
  `7b528a3` and its GPL-3.0 license. Read-only candidate audit found no actual credential
  matches or game/decompiled binary payloads. Raw live baseline configuration and generated
  map-index Lua are now ignored. Initial source commit `6cd5045` was pushed to
  `work/first-week-server-runtime`; remote main remains unchanged. The GitHub connector
  returned HTTP 403 when creating a draft PR, so no PR was created.
- Added project skills for native planning, NPC replication, scenario acceptance and Java
  inspection. Each new skill passed its structural validator.
- The ordinary-Lua pedestrian rewrite is implemented: verified callback capture, scoped
  target-effect isolation, native owner/lease checks, bounded contact reports and server
  validation. Client Runtime has no Java-helper calls. Native Kahlua compilation verified
  all ten scenario Lua files and the four callback signatures. Contact animation/armor
  behavior still needs real gameplay validation; vehicle actions are explicitly rejected
  pending the separate server physics experiment.
- Final automated project run for this checkpoint: **61 project tests, 116 Observer tests,
  and the frontend build passed**. Java bridge/premain/control-file fixtures also passed.
  The probe exposed missing empty-world physics initialization and missing native-library
  initialization; fixes use the shipped `Bullet.init()` and `WorldSimulation.create()` on
  explicit probe start. No game file replacement or client injection was used.
- Follow-up worker-outage fix: an advancing completed observation must renew a five-second
  freshness window. Missing/stale replies hold scenario time, disease/regional decisions,
  unfinished routines and materialization. Recovery requires an observation published after
  the hold; paused empty observations continue, so recovery does not depend on pending work.
  Native reconciliation, leases, defensive reactions and validated physical damage continue.
  Confirmed contact exposure is retained until recovery at the frozen scenario time. Manual
  pause remains independent, and Observer's existing worker-health field reports the hold
  reason. **68 project tests passed**, including seven new regressions; peer review passed.
  This has not been deployed or exercised with real multiplayer clients.

Outstanding gates include real-world ordinary-Lua effect isolation, resident lifecycle,
treatment/contact correctness, infected corpses, complete regional/adaptive progression,
live worker-outage behavior, normal-world vehicle physics integration and collisions, NPC occupancy,
and two-client convergence. The private `artifacts/scenario-tests/integration-audit.md`
records specific implementation gaps. The prototype has not been reset or archived for release.

The following sections preserve evidence for the earlier observation-only deployment.

## Project bootstrap evidence

- Project created under `/var/home/akr/Documents/Projects/ZomboidDayOne`; isolated source, data, secrets, artifact and tooling paths established.
- Observer source copied independently; `references/observer-source.json` records file hashes. The original source repository had no HEAD commit.
- Live baseline config copied read-only; new world/port identity and credentials created separately. No old world save is used as this world's starting state.
- Root/scoped agent guidance, five focused skills and this Markdown vault created. Skill/link validation results are recorded below when run.
- All five `.agents/skills/*/SKILL.md` files passed the local skill-creator `quick_validate.py` validator on 2026-09-26. This validates frontmatter/naming/scaffold structure; it does not prove in-game behavior.
- Root README/SKILLS Markdown links resolve, and `./dayone --help` matches the documented command list. Final vault validation checked 22 Markdown files and 76 wikilinks: no missing/ambiguous targets, no missing local Markdown links and valid design-page frontmatter.
- Deployment work reports the baseline dedicated server reached RCON readiness and completed a graceful quit. The full companion mod subsequently initialized in observation mode on the dedicated server without Lua errors; Linux animation case aliases resolved the identified Bandits animation-node errors. This is startup evidence, not a real-client NPC test.

## Validation status

| Gate | Status | Evidence / limit |
| --- | --- | --- |
| Project skills | Passed structural validation | All five skill-creator validators passed, 2026-09-26 |
| Vault links/frontmatter | Passed | 22 Markdown files, 76 resolved unambiguous wikilinks, all local Markdown links present |
| Local tool bootstrap | Prepared | Project-local Python 3.12.12, pinned Node/uv archives, dependencies and browser assets; tool/image manifests under `references/` |
| Configuration and container build | Built and restarted | Final game/Observer/gateway recreation completed; game had zero restarts/OOM events and RCON reported zero players |
| Fresh baseline server boot | Passed startup/RCON/shutdown | No real client evidence |
| Companion dedicated-server initialization | Passed startup observation | Final companion initialized in observation mode without storyteller Lua errors; real clients not tested. Upstream warnings remain, listed below |
| Observer automated tests | Passed | 114 Python tests, two browser tests and Java/Python cross-language validation; fixture covered five failure modes |
| Observer debug browser | Passed | Actual loopback debug page loaded without JavaScript errors; screenshot and fixture log under `observer/artifacts/` |
| Project/Lua automated tests | Passed | 19 Lua 5.1 tests via `lupa.lua51`, plus Ruff checks; see implementation page for coverage |
| Operations automated tests | Passed | Four checks: Observer SQLite backup/restore round trip, interrupted archive write, retention of completed backups only, and failed graceful stop preventing backup |
| Bandits source/API audit | Completed for observation release | Current V2 and old Week One MP interfaces differ; no active spawn/cleanup adapter claimed. See [[Research/Bandits Compatibility]] |
| Storyteller Lua implementation | Observation release implemented | Field/scanner, inference, persistent state, decision previews, recovery/limits, native schedule suppression and telemetry; see [[Design/Storyteller Implementation]] |
| Actual telemetry delivery | Passed with empty server | Real Lua → Java → protobuf → Observer reached tick 185 after the high-resolution-clock build, mode `observe`, phase `outbreak`, zero players, health `observing_native_spawns_disabled`. `PauseEmpty` was briefly disabled for each check and restored to true |
| Gateway boundary | Passed local/LAN checks | Observer LAN returned HTTP 200 from `.160`; loopback debug returned 200, LAN debug returned 404 |
| HTTPS certificate/routing on LAN | Passed | `map.lofers.net:8453` resolved explicitly to `.132` returned HTTP 200 with a valid certificate; this does not test WAN forwarding |
| Two-client NPC ownership/persistence | **Not tested** | Requires [[Runbooks/Multiplayer Validation]] |
| Empty-server timing | Measured, limited | High-resolution sample at tick 185: Lua last 0.042 ms, p95 0.377 ms, p99/max 1.125 ms; Java capture 0.194 ms. No online-player/storage/NPC load in this sample |
| Java bounded-capture fixture | Measured, synthetic | 200 warmed samples in each of five modes with 70 cells/40 decisions: p95 0.083–0.115 ms, p99 0.298–0.434 ms, max 0.404–0.769 ms. Combined-hook cold initialization reached 12–13 ms |
| Field inference and latency under player load | **Not measured** | Empty-server timing does not validate [[Design/Performance Budget]] scenarios |
| Consistent backup / automated restore | Passed | Final archive checksum and isolated SQLite checks passed; exact artifacts below |
| Playable isolated restore | **Not tested** | Archive/SQLite validation is a separate lesser check |
| WAN/router connectivity | **Not tested** | Requires direct forwarding and an external connection |
| Private companion package | Built and checksummed | Original `LofersStoryteller` 0.1.0 ZIP; checksum below |

Observer test procedure, fixture limits and evidence paths are documented in [the telemetry validation note](../../observer/docs/storyteller-debug.md). The fixture log is `observer/artifacts/storyteller-agent-test.txt`; the live debug screenshot is `observer/artifacts/storyteller-live.png`. These checks do not establish NPC multiplayer behavior or loaded-base scan performance.

## Deployment artifacts

- Runtime feed evidence: `artifacts/runtime-storyteller.json`.
- Final profile-enabled guard check: `artifacts/final-guard-smoke.json`; fresh mode `observe`, health `observing_native_spawns_disabled`, `PauseEmpty=true` restored, followed by an acknowledged RCON world save.
- Deployment state, test results and remaining gates: `artifacts/deployment-status.json`.
- Endpoint boundary evidence: `artifacts/endpoint-boundaries.json`.
- Installed original-mod hashes: `artifacts/installed-mod.json`.
- Verified private backup: `backups/AKR_DayOne-20260926-145311-827862265.tar.gz` plus its SHA256 sidecar. It includes secrets and must remain private; it precedes the final built-in-clan availability adjustment below.
- Isolated restored files: `artifacts/restore-tests/20260926-145333/`.
- Client package: `artifacts/LofersStoryteller-0.1.0.zip`, SHA256 `dbb456278426e186c6bf00ff2c48a4c4880124939f8218596ec8785031545f00`.

## Startup warnings retained for review

The final startup still reports upstream FluidLargeBucket/FuelPump sanitization, missing fence `ThumpSound`, mannequin-zone, duplicate basement ID and `map_meta` invalid-room skip warnings. These were not resolved by this deployment and should not be described as a completely clean log. The identified Bandits animation-parser errors and this project's missing-directory errors are gone. No storyteller Lua errors were seen during the empty-server smoke check; real-client behavior remains untested.

## Known rollout limits

The original companion must be installed on clients as well as the server; the Java Observer exporter is server-only. Hostile storyteller events remain gated on real integration evidence, and native Bandits scheduling is disabled during observation. The plan includes candidate event families beyond the initial executable implementation; consult implementation notes for actual support. Observer debug is read-only and local. Travel replay remains outside this task.

For the manual two-client trial, `General_OriginalBandits=true` makes built-in clan profiles available in the admin UI. The companion still sets their native scheduling chances to zero; this flag does not enable automatic encounters. See [[Research/Bandits Compatibility#Concrete next two-client trial|the prepared Clan Karate trial]]. The configuration adjustment was applied by a graceful restart; RCON reported zero players and the companion initialized successfully again.

For new evidence, add the command/scenario, date, exact build, result, artifact path and remaining limits here or link an experiment created from [[Templates/Experiment]].
