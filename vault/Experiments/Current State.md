---
type: handoff
status: runtime-framework-planned
updated: 2026-09-27
---

# Current state

Snapshot taken **2026-09-27 12:18 UTC**. Runtime observations are historical; recheck before
operating. This handoff supersedes the earlier instruction to immediately run a head-on test.
The user first requested a runtime/reload/batch-testing architecture review, then asked to
save the plan and state. No framework implementation or deployment is claimed.

## Resume here

1. Read [PLAN_SchedulerAPI.md](../../PLAN_SchedulerAPI.md) and [[Design/Live Event Runtime]].
   They define the persistent runtime shared by testing and the future Storyteller.
2. Reconcile the incomplete opposing-car edits below during runtime extraction. Do not
   build/deploy the current draft as if it were a completed two-car implementation.
3. Implement the scheduler, event resource ownership and staged reload boundary before
   resuming individual collision iterations. Follow the validation order in the plan.
4. After empty-server two-body validation, return to the requested opposing-car test.
   Preserve the rock scene for later rollover research; it has not produced a rollover.

## Deployment at the snapshot

| Item | Observed value |
| --- | --- |
| Workspace | `/var/home/akr/Documents/Projects/ZomboidDayOne` |
| Source branch | `work/first-week-server-runtime` |
| Latest validated-source/documentation checkpoint before this handoff | `5519618` (pushed) |
| Behavior checkpoint | `58eecb2` (pushed) |
| Disposable world | `LofersVehicleProbe_20260926_235220_a91875` |
| Container | `lofers-vehicle-probe` |
| Game endpoint | `192.168.1.132:16281`, secondary UDP `16282` |
| Test RCON | loopback TCP `27035` |
| Private save/config root | `artifacts/vehicle-probe/20260926_235220_a91875/` |
| Receipt | `artifacts/vehicle-probe/current.json` |
| Epoch | `9ca7d9b9-5d89-4dca-ae62-585969af9dd0` |
| Probe phase | `complete`, empty error |
| Managed body | unregistered; native vehicle count after cleanup `0` |
| Online players | `0` at snapshot |

Deployed JAR SHA256:
`43c057c30ad71397f2fcf86461d141a7f5da751c10f6799aee64b2bb49711c8e`.
The running container has its private immutable copy. No restart or event execution occurred
while saving this handoff. Normal `.132:16271/16272` and production `.160` were not changed.
Do not use `./dayone rcon` for this probe: it targets the normal world. See [[Runbooks/Operations]].

## Validated behavior and remaining uncertainty

One ordinary client accepted native driving/turning, stop/wait, road bypass with a horn,
and static obstacle collisions with damage and sound. The failed shoulder pass through a
pole led to real native terrain-body activation and stricter obstacle checks. Detailed
history is in [[Implementation Ledger]].

Latest accepted client case: `artifacts/scenario-agent/stove-impact-client-20260927_111158/`.
The user reported “Saw and heard it; game stayed responsive.” Peak 103.35 km/h, final approach
sample 100.40 km/h, native impact severity 81.2255, maximum tilt 4.46°. No new captured client
errors. Stove removal took 3.15 seconds after observed contact; managed car cleanup restored
native count zero, with five unrelated cars unchanged. Warm added work averaged 0.215 ms,
p99 upper bound 0.9 ms, maximum 1.108 ms. These are one-car measurements, not a fleet budget.

The longer empty-server stove run peaked at 114.66 km/h, approaching at 110.23 km/h while
braking. Its maximum tilt was 11.54°. A different stove run tilted 44.56°, but neither it nor
the stock/custom rock trials rolled the car. Speed varies with actual stock vehicle properties.
No living NPC occupant, NPC death, total engine destruction, managed car-to-car impact or
two-client consistency has been demonstrated. Ordinary parked Java cars are not automatically
registered Bullet bodies.

The earlier client attempt `stove-impact-client-20260927_110926` coincided with a client
freeze and aborted at roughly 1 km/h with `invalid_observation`; both fixtures were cleaned.
The cause of the freeze remains unknown. The successful reconnect does not establish a cause.

The latest complete build passed Java fixtures and 191 Python tests. Those results precede
the unfinished source edits below. Test logs: `artifacts/stove-impact-agent-tests.log` and
`artifacts/stove-impact-python-tests.log`.

## Unfinished source and preserved work

The opposing-car draft was interrupted before the helper was created or built:

- `ProbeControl.java` adds an `opposing` flag and references **missing `ProbeOpposingCar`**.
  Consequently this draft is not buildable as a complete agent.
- `ProbeCrashFeedback.java` drafts two-object registration and per-car counters. It is
  untested and not part of the running JAR.
- `scripts/vehicle_probe_ops.py` drafts centered opposing-scene validation/config output.
  The actual two-body creation, control, safety and cleanup implementation is absent.
- `npc-service/CMakeLists.txt` has an unrelated existing user change enabling compile-command
  export. Preserve it; do not fold it into this task or revert it.

A local snapshot preserves the complete tracked diff for these four files and a plan copy:
`artifacts/handoffs/20260927-live-event-runtime/working-tree.patch`, `scheduler-plan.md` and
`state.json` (base commit, timestamps and SHA256 hashes). This is ignored local recovery
evidence, not a tested change or a remotely published code checkpoint. The source edits
remain in place; documentation is versioned separately.

## Current private experiment tools

- `artifacts/scenario-map/vehicle-stove-impact.json`: current 600-tile course, stock SportsCar,
  requested ceiling 120 km/h, failed-brake experiment and tagged antique stove.
- `artifacts/scenario-map/vehicle-rock-impact.json`: retained rock/rollover attempt.
- `artifacts/scenario-agent/high-speed-impact-config.json`: private matching fixture/config.
- `artifacts/stove_impact_run.py`: serial private harness; `--client` expects sole observer
  `akr` on foot. It is not the future general event API or batch runner.
- `artifacts/probe_fixture_tools.py`: world-scoped, temporary Lua-reload helper with an exact
  source hash and restoration in `finally`. Never run its Lua operations concurrently.
- `artifacts/verify_stove_impact.py`: verifies native cleanup and unrelated-car snapshots.

The approved viewing area was approximately X 10605, Y 10060, ground level, outside the
12-tile route exclusion. Confirm players, actual positions, loaded terrain and daylight
before any watched experiment. Prior readiness is not evidence of a currently ready client.
