---
type: runbook
status: offline-suite-implemented
updated: 2026-09-27
---

# Runtime testing

The unit layer now includes the real Lua 5.1 civilian FSM/controller/ownership ledger
and detached Java pool, A*, escape selection, path admission, traversal and perception
fixtures. It runs 2,000 pool reuse cycles and fault injection without starting a world.
The integration layer additionally verifies the pinned Actor hook sites and copies a
real pooled native Path before clearing/reusing its nodes. See [[Civilian Qualification Batch]]
for the separately deferred native/visual cases. Stock body save/load and climbing remain
unqualified; compilation is not a native round-trip test.

Run from the project root:

```sh
./dayone runtime-test offline
```

This builds and runs the implemented Python, Java and C++ runtime checks. It never starts,
stops, restarts or submits commands to a game server. Dependencies and installed game files
must be present locally; the native build script can obtain its pinned dependencies under
`.tooling/` if missing. Build outputs remain project-local. This command does not deploy them.

Use narrower selections during development:

```sh
./dayone runtime-test unit
./dayone runtime-test integration
./dayone runtime-test worker
```

| Selection | Executed checks |
| --- | --- |
| `unit` | Python component/regression suite; detached Java scheduler, resources, native-owner backend faults, geometry/controller and impact-definition checks |
| `integration` | Java file control, Protobuf, real Unix IPC, installed-build hashes, transformed bytecode, original driver asset, agent bootstrap and pinned launcher discovery |
| `worker` | Native planner core tests and process/socket Protobuf tests |
| `offline` | All three selections, building/running the Java layers together |

Java unit execution is detached, but compilation still uses the pinned game JAR. Integration
fixtures exercise actual JVM/native libraries where appropriate; they do not start a playable
world or demonstrate collision behavior. Python includes configuration, operations and Lua
component checks; it is not exclusively pure unit tests. Observer/UI tests remain under the
existing `./dayone test` command rather than this runtime-specific suite.

## Reports and failure handling

Each run writes a unique directory under `artifacts/runtime-tests/`, containing `report.json`,
`report.md` and one log per step. Reports record source revision, working-tree dirtiness,
selection, elapsed times, exit status and pending in-game gates. A dirty revision is not a
reproducible source snapshot; preserve source changes with the evidence before sharing a result.

Failures produce a nonzero CLI exit. Later independent layers still run so their evidence is
retained. Each process has a timeout; timeout/interrupt terminates its own process group.
Interrupted runs retain completed results and identify later steps that were not run.
Overlapping `runtime-test` invocations are rejected because they share build outputs. This
lock does not cover direct manual builder invocations; do not run those concurrently.

The report's `passed` status applies only to the selected offline checks. Native functional,
same-session reload, native soak, watched and two-client gates remain explicitly pending.
The suite does not infer acceptance from the absence of failures or fabricate skipped gates
as passing. See [[Design/Live Event Runtime]] for rollout requirements.

## Regression coverage

| Regression | Automated protection | Remaining game evidence |
| --- | --- | --- |
| Commands overwritten while busy | File FIFO order, capacity, priority stop | Same-session live submission |
| Duplicate execution after retries | Scheduler identity/conflict/stale-epoch fixtures | Durable restart recovery |
| Next event starts before cleanup | Resource bounds, cancellation, partial failure and blocked-cleanup recovery | Java/native removal on a running server |
| Native world repeatedly initialized | 100 detached leases; foreign-owner and partial-initialization rejection | Native same-process batch |
| Terrain ownership lost after exceptions | Partial activation/deactivation fault injection | Real native failure/recovery observations |
| Jerky turns or unsafe steering | Curve/controller simulations and vehicle-capability limits | Client-observed movement |
| Passing without clearance | Footprint, bypass and reservation fixtures | Multi-car native avoidance |
| Protocol stalls/corruption | Fragmented/malformed frames, epoch rejection, queue bounds, reconnect and acknowledgments | Full event API protocol still to implement |
| Test failure reported as success | Runner exit, timeout, missing-tool, interruption and report regression tests | None; this concerns the offline harness |

Add a reproducing case when fixing a bug. Keep detached numerical/controller tests alongside
their Java fixtures and operations/reporting tests in `tests/`. Engine regressions additionally
need recorded native scenarios once the runtime batch API exists; mocks cannot replace those.

## Pending functional suite

The event API will provide the runner for route/stop, avoidance, generated static impact and
low-speed two-car cases. Each must check event outcome, exact owned-resource cleanup, baseline
restoration and measured tick costs before advancing. Reload cases must retain process/epoch;
the soak gate remains 100 real cases and 20 reloads. Use an empty disposable server for
unattended runs. Watched cases pause for operator advancement and verify observer placement.
Two-client replication remains a separate gate. Neither normal `.132` nor `.160` is a test target.

First unified offline execution: `artifacts/runtime-tests/20260927-125049-bb1ba0ea/` —
204 Python tests, Java unit/integration fixtures, native core and 13 worker wire scenarios
passed. No live server changes or native-world event tests occurred during that run.

## Isolated 42.21 migration

Run the commands from `.tooling/worktrees/pz42.21`, not the frozen root checkout.
`./dayone compat test --profile pz42.21 --full` runs fresh code-only checks. Exit 2 can
mean the selected checks passed but remaining migration review is explicit; read the report.
`./dayone civilian-headless run`, `survival`, and `planner` use separate disposable worlds,
container `akr-civilian-headless-pz42-21`, unpublished game UDP ports and loopback RCON
27065. Only that container is stopped by the candidate runner. Images share the existing
project Podman store; saves, agent copies, receipts and worker runtime files are separate.

Both runtime agents now take identity from the reviewed test-only profile. The game agent
rejects normal worlds and client-JVM mode, checks the full JAR, and checks exact hook counts
before world load. Observer must load first. Existing normal/watched launchers and dist
instructions have not yet been migrated; do not use them for a 42.21 client test.
