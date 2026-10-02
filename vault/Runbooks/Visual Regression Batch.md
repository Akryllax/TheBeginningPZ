---
type: runbook
status: partially-executed
updated: 2026-09-29
---

# Visual regression batch

User scope: exercise implemented cars, NPC movement/pooling, minimal combat and basic
routines. After a noncritical failure, preserve the failure, clean up and continue.
Preparation alone does not start a watched session. The user authorized the one-seat run
and confirmed readiness on 2026-09-29; results and remaining harness blockers are recorded
in [[../Experiments/Current State]]. Fresh runs still require current in-world readiness.

`./dayone visual-test prepare` freezes a 17-case catalogue, available vehicle routes,
source hashes and a checklist under `artifacts/visual-regression/`. It does not launch,
install or submit anything. `./dayone visual-test status` prints that preparation.
This is a grouped operator batch, not a new unified hot-reload API: NPC runtime cases
are automated together; pooling/chase/lifecycle and vehicle probes have separate harnesses.
Do not claim that all groups run under one command or one server process.

## Spectator startup parameter

Default to **one** spot for every startup, including after a previous multiplayer test.
Only use more when the user explicitly announces guests for this test:

```sh
./dayone visual-test prepare --viewers 2
./dayone civilian-combat start --viewers 2
```

The runtime encounter and dedicated lifecycle harness support 1–4 spots. `akr` is seat 1;
other connected players fill remaining seats in username order. The full roster must be
present before setup; it is fixed for the case. Each receives a distinct adjacent position,
god/invisible/ghost protection, the loadout, and independent visibility/loadout/drift checks.
Guests do not receive admin rights. Missing/replaced/extra connections block progression.
Cleanup considers all viewers. Per-viewer telemetry is recorded in the typed case report.

The parameter is rejected for other launch commands. Legacy pooling/chase and vehicle
harnesses still have their original single-viewer assumptions: multi-viewer preparation
marks those cases BLOCKED rather than silently treating them as covered. Do not run them
with additional viewers until their own visibility/cleanup gates are adapted. No two-client
qualification is implied by these changes. Nothing auto-starts from the preparation command.

## Shared procedure

Follow [[Watched Testing]]. Preserve automatic unobstructed positioning/facing before
**every** case and wave, admin/god/invisible/ghost, clear noon, loaded 9mm pistol, two
loaded spare magazines and spare ammo. Confirm current connection/epoch/loadout and
actual visibility. Announce group, case, exact counts, 2-second countdown, 8-second
encounter viewing hold, cleanup and result. Vehicles retain their existing 20-second
viewing hold; do not silently change native probe timing while preparing regression.

After each group collect one case-scoped human verdict: animation, movement, clipping,
identity, sound and obvious errors. Missing feedback is pending, not pass. User damage
to participants invalidates the affected case. Do not ask the user to navigate coordinates.

## Failure isolation

The encounter runner now continues after terminal FAILED/CANCELLED only when the native
cleanup receipt is verified and the resource ledger is empty. It retains phase, scenario
outcome, issue, client log slice and sampled native report for each case. A fresh runtime
handshake must match the same world/epoch and report READY before the next submission.
A completed batch with failures returns nonzero **after** running all remaining safe cases.

Critical failures stop the entire batch, including later groups: unverified ownership or
cleanup, retained dead/uncertain bodies, changed epoch/identity, unavailable control channel,
disconnected/stale observer, invalid observer protection or serious JVM/runtime failure.
An operator timeout requests cancellation and permits at most 24 seconds for cleanup.
The outer interruption path also attempts bounded cancellation and records its receipt;
it never treats sending cancel as proof of removal. No automatic retry or easier substitute.

A recovered scene failure or new nonfatal client error does not erase its failed outcome.
NOT_EXERCISED (e.g. a native zombie does not engage) stays separate from pass and failure.
Missing/rotated client logs are unavailable evidence and fail that case's evidence gate.
Blocked harness preparation is BLOCKED, not a gameplay failure and not a pass.

Legacy startup harnesses do not have the new automatic failure isolator. The operator
must inspect their reports and reconcile exact resources before switching worlds. If
cleanup cannot be established, stop the whole batch. Never use a fresh world to conceal
unreconciled resources from the previous process.

## Group A — nine automated NPC runtime cases

When authorized to launch, prepare/start a fresh AKR encounter world, join the pinned
ordinary client, then wait for the user to be in-world before `run regression`:

```sh
./dayone civilian-combat encounter-create
./dayone civilian-combat start
./dayone civilian-combat join
# Only after current in-world readiness:
./dayone civilian-combat run regression
```

| Order | Actors / hunters | Case and visual acceptance |
| --- | --- | --- |
| 1 | 2 / 0 | Parallel 50-tile WALK/RUN; runner faster, both smooth, human appearance and footsteps |
| 2 | 1 / 0 | Existing calm routine: walk to nearby activity, wait, walk home; no stale actions/teleports |
| 3 | 1 / 1 held | Existing locomotion course: gait, direction changes, interruption and resumption |
| 4 | 1 / 1 | Open escape from native threat |
| 5 | 1 / 1 | Incoming native injury and escape; no fabricated attack |
| 6 | 1 / 1 | Defensive shove/hammer, contact sound/damage and escape |
| 7–9 | 4 / 4 per case | Three repetitions of independent defense/escape; pooled identities and clean boundaries |

The ROUTINE enum is an additive private test capability using the existing
NativeResidentController and optional worker/Lua fallback. It does not add a new planner,
new movement executor, scheduled employment or home persistence. It checks logical plan
completion, actual travel, home arrival, current client replication and normal cleanup.
The planner source is reported. The four-body pool stays alive across these cases.

## Group B — pooled identities and off-screen entrance

After verified Group A cleanup, stop its disposable server before preparing another
harness (all use the same disposable ports). Keep immutable evidence from each world.

```sh
./dayone civilian-combat stop
./dayone civilian-watch create 4 0
./dayone civilian-watch start
./dayone civilian-watch join
```

Watch four southbound 150-tile waves with four Actors each: identity changes, footsteps,
no missing waves and reuse of the original four engine objects. Confirm final native/world
cleanup before stopping/switching. These are startup-triggered harnesses; joining starts
their existing readiness flow. Coordinate readiness before the join, not afterward.

```sh
./dayone civilian-watch stop
./dayone civilian-chase create
./dayone civilian-chase start
./dayone civilian-chase join
```

Watch one civilian and native zombie enter view already moving. Check no visible pop-in,
reasonable pursuit, stock ownership and exact cleanup. This is deliberately one entrance
regression, not a substitute for the separately deferred 64/128 crowd test.

## Group C — accepted fatal lifecycle regression

After chase cleanup, stop its disposable server and create the dedicated lifecycle world:

```sh
./dayone civilian-chase stop
./dayone civilian-combat lifecycle-create
./dayone civilian-combat start
./dayone civilian-combat join
# After fresh in-world readiness:
./dayone civilian-combat run lifecycle
```

Watch defense, native injuries/death, corpse contents, reanimation, exact original Actor
reuse with changed identity, replacement walking and cleanup. This fixture holds civilian
movement to exercise fatal contact. No scripted kill, forced successful attack or automatic
retry when the zombie does not engage. Repeated fatal lifecycles/four independent deaths
remain the next milestone, not part of this accepted single-lifecycle regression.

## Group D — five vehicle probe cases

Routes are frozen with SHA-256 hashes in the prepared artifact. They are **candidate
recipes requiring operator scene validation**, not new native qualification. Vehicle route
configuration currently requires the probe stopped, so these are separate probe sessions.
The startup probe has no NPC runtime/fixture bridge; do not run it concurrently with one.

For each candidate: reconcile preceding resources, stop the disposable harness, prepare
`vehicle-probe-create driver-model`, configure the copied route with `vehicle-probe-route`,
start, install/verify its ordinary asset mod while the client is closed, then use the pinned
client and fresh readiness. Review route provenance and current geometry first. Apply
spectator setup/loadout and confirm real sightlines before `vehicle-probe-control start`.

| Case | Candidate route | Exact fixture and acceptance |
| --- | --- | --- |
| Turn/stop | `vehicle-lane-course.json` | No added blocker; continuous turn, correct lane/stop behavior and plausible speed |
| Queue/pass | `vehicle-bypass-course.json` | Track one parked car's exact native ID; stop, wait, optional honk, clearance and lane return |
| Shoulder refusal | `vehicle-shoulder-course.json` | Track both blockers and denial fixture; pole must block detour, no phasing |
| Pole impact | `vehicle-high-speed-impact.json` | Current AKR-tagged pole/token; actual native crash, sound and damage |
| Brake failure/stove | `vehicle-stove-impact.json` | Exact tagged stove and actual stock brake-part damage; native crash/damage/audio |

The old local impact helpers in `artifacts/` contain historical reloadlua fixture calls and Lofers
identifiers. **Do not execute them unmodified or claim they are the new AKR batch runner.**
Before an impact/blocker case, re-establish a supported original server fixture adapter and
record its source/IDs/token. If unavailable, record BLOCKED and continue with independent
prepared cases; never simulate success via scripted damage or leave an uncertain fixture.

Maintain at least 12 tiles between players and the crash path. Hold the impact obstacle
for three seconds after actual contact, then remove only the exact tagged object; keep the
existing damaged-car viewing hold. Record part conditions before/after and native crash
counter/audio diagnostics independently of human feedback. Verify body unregistered,
world/chunk/registry absence and exact fixture cleanup before switching sessions.

## Scope limits

Scheduled household/work routines, controlled restart, crash recovery, two-client checks,
64/128 stress, opposing-car collision and guaranteed rollover are not silently included.
Their implementation/qualification is incomplete. Observer webpage features are outside
this in-game batch. Functional short runs are not sustained performance qualification.

After execution, archive group report paths beneath the prepared batch and update Current
State with actual machine/human outcomes. Keep NOT_RUN/BLOCKED/NOT_EXERCISED explicit.
