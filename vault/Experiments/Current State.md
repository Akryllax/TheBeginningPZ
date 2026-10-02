---
type: handoff
status: stationary-lifecycle-accepted-neighborhood-planned
updated: 2026-10-02
---

# Current state

## 42.21 watched batch halted on retained death resources (2026-10-02)

After fresh readiness, ran the requested one-seat NPC regression on
`AKR_DayOne_Test_20261002_105215_1b1675`, epoch
`850f89c7-6e33-4e47-a4ce-06a84c493160`. Batch `20261002-110238-81acc69b`.
WALK/RUN, routine, locomotion, open escape and incoming injury completed with verified
cleanup; human acceptance pending. Case 6 DEFENSE_ESCAPE reached
`unexpected_death_resources_retained` and CLEANUP_BLOCKED. The shared guard checks both
Actor and hunter alive; **which participant died is not established**. Last Actor health
91.44, four injuries, one contact, ~5.95 tiles travelled. Do not call this proven Actor
death or a planner regression. Asked for user observation/interference information.

All advancement stopped, including four-Actor cases and later groups. Terrain, Actor,
aftermath and hunter resources remain owned; do not force-clear or use another world to
conceal unresolved cleanup. Current server remains available for inspection. No auto retry.
Evidence under the world's `encounter-batches/20261002-110238-81acc69b/`; linked into
`artifacts/visual-regression/20261002-105213-91c635b1/plan.json`.

## 42.21 watched regression preparation (2026-10-02)

Candidate watched ports are now 16301/16302 and loopback RCON 27055; dedicated container
`akr-scenario-test-pz42-21`. The ordinary pinned 42.21 client uses isolated
`artifacts/candidate-client/Zomboid` for mods, connection profile and diagnostic logs.
No 42.20 player save was imported into the fresh candidate world. One spectator.

Initial client joined but presentation guard failed before any case submission. Diagnosis:
getFirstLineOfClosure returns Prototype.lines[0], not the Lua declaration line. Real
42.21 Kahlua compilation of the hash-pinned Bandits source reports update/hit/dead/body
2069/2335/2402/2599. Previous 2067/2289/2400/2590 were declaration positions. Profile now
records both separately; both client gates and server manifest corrected. The code-only
suite compiles upstream Lua without executing it and checks actual closure metadata.
This corrects the earlier incomplete source-line check; initial attempt is not a pass.
After coordinated relaunch the client reported presentation ready. No case submitted yet.
User reported missing spectator protection before submission: join now applies targeted
42.21 `godmodeplayer` and `invisibleplayer` commands (the latter sets ghost mode), plus
admin; it requires server confirmations before returning. Self-only godmode/invisible
commands do not accept a target in 42.21. Current observer was explicitly protected.
The stopped worker namespace left its socket: old container identity was confirmed gone,
connection refused, socket preserved under a stopped suffix, then fresh worker started.

## 42.21 guarded native qualification (2026-10-02)

The isolated candidate now builds with a shared generated identity for gameplay and
Observer: 780 API contracts, 168 class pins, exact JAR identity, native/dependency pins
and 46 hook sites. Snapshotting cannot approve these pins. The reviewed candidate permits
**disposable server test worlds only**; client JVM mode and normal-world startup are rejected.
Runtime hook-count enforcement now fails before premain accepts missing/duplicate injections.
The expanded upstream comparison detects seven removed contracts, including the changed
`getSpecialObjects` return descriptor and Lua door overload missed by the initial five-contract
inventory. Explicit reflection/Lua bindings and inherited Java declarations are inventoried.

Three fresh headless worlds passed:
- `20261002-093256-18f0ac`: all 23 base cases, including doors, repeated reuse, native corpse/
  reanimation and 32-body prewarm; complete cleanup.
- `20261002-093536-019bff`: all 26 cases including native combat and 13 autonomous survival
  encounters/contacts; zero occupied resources after cleanup.
- `20261002-094346-5af95e`: eight native worker plans/assignments and four initialized bodies,
  including WALK/RUN/reaction routines; zero occupied resources. WALK 23.10 s, RUN 12.10 s.

Java integration/premain and native search-path guards passed. Observer's five-mode
export/failure-isolation fixture passed. Real dual-agent premain passed with Observer first;
reverse order does not install Observer's hook and remains unsupported. The unchanged,
independently copied worker bundle passed 16 wire scenarios (32 residents, 30 batches;
round-trip p95 16.75 ms). This is not a fresh C++ rebuild or game-thread latency.

**Performance remains unqualified:** base walking p95/p99 1.4/3.1 ms, warm materialization
p95 4.9 ms; survival work p95/p99 6.2/21.9 ms. Planner routine p95 1.5 ms. These short
functional batches do not qualify the release cap or establish a like-for-like regression.
Watched and two-client behavior, full traversal, transformer subsystem split, finer test
selection, candidate client/watched launchers and updated dist remain pending.

All game processes from these runs stopped gracefully. No client launched and the frozen
42.20 installation/.160 deployment were not changed. Candidate artifacts/receipts are under
`artifacts/compat/` and `artifacts/civilian-headless/` in the 42.21 worktree. See the root
`TASK_42_21_Compatibility_Migration.md` there for the remaining implementation checklist.

### Final identity and client-guard checks

The first three runs above used the 42.21 engine but retained old text labels in Actor
snapshots and the worker handshake. Those labels now come from BuildProfile. A fresh
planner run `20261002-095605-886be9` passed eight plans/assignments on four initialized
bodies (WALK 23.10 s, RUN 12.10 s, p95/p99 1.7/3.4 ms, zero occupied resources).
Base rerun `20261002-100241-70c975` passed all 23 cases with the corrected snapshot
identity, including roundtrip restoration, fatal lifecycle and 32-body prewarm. Walking
p95/p99 was 1.5/3.0 ms; warm materialization p95 5.2 ms remains above the release target.

The reviewed Bandits callback declarations moved to 2067/2289/2400/2590. Both Lua gates
and the server manifest now match Workshop manifest 319258424172526049. The builder and
code-only suite verify these declarations against the pinned upstream input and reject
profile/client drift; a synthetic negative check catches a stale callback line. Candidate
mods require 42.21. No ordinary client has been launched, so this is not client acceptance.

Final code-only report `artifacts/compat/runs/20261002T100429.294186Z/report.md` passes
both Python components and fresh Java/hook checks. Overall exit 2 intentionally retains
incomplete review/selection and runtime qualification gates; it is not a release approval.
All native test processes are stopped. Frozen 42.20 and production .160 remain untouched.

## 42.21 native worker and packaging checkpoint (2026-10-02)

Fresh candidate-local C++ compilation (352 build steps, pinned dependencies) passed
CTest's npc-core suite and all 16 actual wire scenarios. Wire test: 32 residents,
30 batches, p50/p95/max 16.70/17.79/18.11 ms asynchronous round trip. The new local runtime
bundle replaces only the candidate copy; no baseline outputs were overwritten.

`./dayone dist` built and verified matching server/client packages under the candidate
worktree's `dist/`. Manifests record 42.21.0/4a0e9546ec, disposable-worlds-only guards and
pending watched/two-client qualification. Packaging now rejects mixed profile/build/JAR/
Bandits identities and incorrect guard scope. Updated ordinary Lua client/server install
notes and candidate client depot reference. Archive inspection found no game archive,
Workshop payload, secrets, saves or decompiled output; client contains no JVM/native agent.

307 Python tests, lint, fresh Java code/hook checks and 24 targeted packaging/vehicle
operation checks passed. Compatibility run `20261002T103921.567529Z` still returns 2 for
incomplete selection/review and native/watched/multiplayer gates. Existing watched launchers
remain unported and must not be used for this candidate. No client launched.
The fresh-worker native run `20261002-104109-9c49c9` passed: eight worker plans and
assignments, four initialized bodies, WALK 23.10 s versus RUN 12.10 s, routine p95/p99
1.5/2.9 ms, zero occupied resources. The disposable server and worker stopped cleanly.
This remains sequential routine coverage, not the planned independent-concurrency test.

Evidence: `artifacts/compat/worker-fresh-build.log`, `dist-42.21-build.log`,
`port-packaging-python.log`, `port-packaging-lint.log` and corresponding compat report.

## Cost and NPC scheduling follow-up (2026-10-02)

User requested recording this analysis while continuing the 42.21 migration. Do not
silently expand the port into a scheduler redesign. The C++ planner already has two
worker threads, but Java permits one outstanding batch, worker collection waits for
all jobs, and plannerPending is global rather than resident-specific. Survival advances
all active controllers per tick; routine fixtures and event admission are sequential.
This does not prove independent progress under a delayed planner job.

Next optimization work: export per-component timings and separate fixture/setup costs;
measure queue age, compute, IPC and apply separately; bounded per-resident outstanding
requests and independent completions; fair game-thread application; a four-resident
concurrency regression with one deliberately delayed plan. Keep engine mutations on
the game thread and preserve revision/generation guards. Stage Actor preparation safely
before visibility rather than assuming pooling removes restore/dressing costs.

Measured cold initialization p95 ~159 ms, warm materialization 5.2 ms; walking fixture
p95/p99 1.5/3.0 ms; survival batch 6.2/21.9 ms (includes fixture/setup/cleanup); planner
routine 1.7/3.4 ms. Wire p95 16.75 ms is asynchronous round-trip latency. These are not
32/64-Actor capacity qualification or attribution of the survival cost to one component.

## 42.21 migration foundation (2026-10-02)

Work is isolated in `.tooling/worktrees/pz42.21` on `pz/42.21.x`.
`main`, `pz/42.20.x` and tag `pz-42.20.4-baseline-2026-10-02` preserve source checkpoint
`4a93d8c`; these refs were pushed. Root runtime/distribution artifacts were independently
preserved with 1,653 checksummed files before candidate work. See
`TASK_42_21_Compatibility_Migration.md` and `compatibility/README.md` for scope and gaps.

The new offline class-file auditor reproduced four removed upstream contracts (movement
return type, combat packet setter and two zombie-factory overloads). It compares exact
JAR/class/native/dependency identities and normalized code, with unknown metadata retained
for review. Candidate first-pass adapters compile, and structural inspection verifies 46
exact hook sites. The detached Java component suite passed after moving test Unix sockets
to short private runtime directories. Python/Lua components passed; synthetic auditor
checks cover removed/changed APIs, access/static changes, code-vs-debug changes, switches,
exception handlers, native/unmapped changes, dirty identities and hook count failures.

Movement uses a shared ShortFlags encoder; combat relays the native WeaponHit list and
scopes native list collection; the factory hook includes persistentId. The Lua door fixture
uses the new facing enum, matched against native Lua usage. These are code-only results.
Runtime guards intentionally remain frozen and refuse 42.21 packaging; no server/client was
started, no native compatibility or multiplayer claim is made. Reports retain exit 2 for
incomplete dependency coverage/review even when selected tests pass. Finer incremental
selection, generated reviewed guards, Observer, native validation and packaging remain open.

Evidence lives under the candidate worktree's `artifacts/compat/`, including immutable
snapshots, JSON/Markdown reports, compiler/component logs and exact structural hook counts.

## 42.21 acquisition and upgrade assessment complete (2026-09-30)

See [[../Research/42.21 Upgrade Impact]] for the migration sequence, effort estimate,
exact identities, PDF cross-check and evidence limits. Recommendation: proceed with an
isolated port; the port itself is pending. Existing 42.20.4 launchers, saves, mods, agents
and dist remain unchanged. The before/after check verified all 93,520 recorded files.

Both clean candidate copies are under `.tooling/game-builds/`: client
`42.21-client-25485521/client/`, server `42.21-server-25485538/server/`.
47,823 client files and 39,185 server files match their depot manifests. Candidate JARs
are identical, SHA256 `e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33`.
The user-assisted Steam depot download completed; its temporary destination link was
restored. The separate candidate Bandits snapshot is retained without installation.

Unchanged agent compilation fails with 11 errors in three files: movement flag API and
combat packet arguments. Of 63 guarded classes, 31 changed. Standalone transformer
inspection found three missing method/helper entries (six old call sites), despite
structurally valid class output: combat relay and zombie-factory suppression need porting.
The user PDF also exposed one direct Lua door-fixture incompatibility. Observer's
exploration guard and the updated Bandits dependency need review. Bullet library bytes
match the baseline; native pathfinding/population libraries changed. No guard bypass or
gameplay port was performed.

Baseline validation in a frozen source copy passed 288 Python/Lua components, Java
unit/integration reruns, CTest and 16 worker wire scenarios. Initial deep-path socket
failures are retained, with the successful short-runtime-path reruns recorded separately.
The candidate vanilla server booted, answered RCON, saved and exited 0 in a fresh private
world, without mods or clients. Study containers were removed. Content/preload warnings
remain in its log; this is startup evidence, not a modded or multiplayer qualification.

Evidence: `artifacts/upgrade-study/20260930-100608-42.21/`. No candidate client was launched,
no watched feedback was requested, and `.160` was untouched. Keep the existing four-Actor
divergence, unqualified chase and blocked vehicle harness separate from upgrade regressions.
The four-resident neighborhood task remains pending.

## Private server/client distribution (2026-09-29)

`./dayone dist` rebuilds the guarded server Java agent and native worker, then stages
`dist/server/`, `dist/client/`, and separate deterministic ZIP archives. Both contain
installation instructions, original AKR Lua modules (including test presentation and
original driver assets), version/dependency manifests and checksums. Server includes
matching tracked source/build tooling, the Java JAR and private Linux worker runtime.
No game binaries, Workshop assets, live configuration, accounts, saves, credentials,
generated map data, decompiled sources or client Java injection are packaged.
`AKRDevConnect` is excluded; recipients join manually. Optional Observer is included as
source, with build/deployment instructions, rather than copying a live exporter artifact.
This is an operator development kit, not automatic fresh-host provisioning or a qualified
First Week release. Exact 42.20.4/Bandits compatibility remains required.

Build is staged and checked before replacing a previous packager-owned dist. Unknown
existing directories and symlink inputs are refused. `./dayone dist verify` verifies every
payload and rejects unexpected additions. Targeted tests cover corruption, unlisted files,
symlink escapes, unknown output ownership, repeatable archives, executable permissions and
empty mod directories. Packaging does not start, stop or install into any game session.



## One-seat regression execution (2026-09-29)

Executed revision `8fd17bd` with one ordinary pinned 42.20.4 client. The user
confirmed readiness. Results are **machine evidence with human visual/audio feedback
pending**, not blanket acceptance or two-client qualification.

Batch index: `artifacts/visual-regression/20260929-125816-168ad3a1/plan.json`.

- Nine runtime NPC cases: first eight passed; final four-Actor defense/escape failed
  `client_replica_divergence:akr:2:3.52`. Cleanup verified, resources empty, four bodies
  parked; continuation followed the ordinary-failure policy. No new scoped client errors.
- Pooling: four southbound waves, four bodies, sixteen assignments passed the machine
  gates and final removal. Archived report under the batch's `pool/` directory.
- Off-screen chase: completed unqualified. Neither participant reported visible;
  civilian travelled through the route while hunter travelled only about 13.59 tiles.
  Hidden priming passed, but entrance/pursuit did not qualify. Zero retained resources;
  archive under `chase/`. Investigate this independently from the position divergence.
- Fatal lifecycle: passed machine gates, ten defensive contacts, eighteen damaging
  injuries, native corpse/reanimation, exact Actor reuse, replacement route and cleanup.
  No new scoped client errors. Evidence:
  `artifacts/scenario-tests/20260929-140611-5e12c2/encounter-batches/20260929-140746-2aedec78/`.
- Five vehicle cases: BLOCKED, not executed. The legacy vehicle harness lacks the current
  verified automatic spectator/loadout/visibility setup; the four obstacle cases also
  require updated exact-owned fixture controls. Historical Lofers helper scripts were
  not run. Prepared lane world `AKRVehicleProbe_20260929_141337_282c75` was never started,
  and no vehicle or fixture was spawned. Port the harness before the next watched group.

Total: ten machine passes awaiting human feedback, one failed, one unqualified, five
blocked. NPC evidence:
`artifacts/scenario-tests/20260929-134056-07b84f/encounter-batches/20260929-134335-5967a421/`.
The stale worker bundle still referenced `opt/lofers`; rebuilding the project-local bundle
and restarting the empty disposable server resolved startup before any case was submitted.
Deployed agent SHA256: `b84dcd26d152672763bd450726ae1933de6b3b5a5db671a826f4cb63de2795d8`.
At session end the pinned client was closed and disposable servers stopped cleanly;
`podman ps` showed no running containers. No production .160 changes were made.

Next: collect human observations, diagnose the four-Actor divergence and failed chase
visibility/pursuit, and bring vehicle spectator/fixture controls up to the maintained
watched-test contract. Do not call the entire 17-case catalogue passed.


## Explicit spectator spots (2026-09-29)

`civilian-combat start --viewers N` now configures 1–4 spectator spots for runtime encounter
and lifecycle sessions. Every start defaults to 1 unless explicitly overridden; this preference
is also in AGENTS.md. `visual-test prepare --viewers N` records the same requested count.
The current prepared batch is still **one spectator**:
`artifacts/visual-regression/20260929-125816-168ad3a1/`.

The roster requires akr plus exactly N−1 other distinct connected players, ordered by name.
It stays fixed for each case. Adjacent floor-checked positions, protection and loadouts apply
to every viewer; guests are not elevated to admin. Visibility, lifecycle viewing, loadout
acks, replica drift and cleanup now require independent per-player reports. Connection
identity prevents old/reconnected player objects from supplying current readiness. New
additive typed viewer samples record per-viewer positions, freshness and drift peaks.
Unknown connections retain resources rather than weakening cleanup.

Legacy pooling/chase/vehicle harnesses remain single-viewer: multi-viewer preparation marks
those groups blocked instead of pretending their old cleanup supports extra spectators.
No live world/client changed and no second viewer was assumed or launched. Actual two-client
presentation and ownership behavior remain unvalidated.

Full offline runtime suite passed at
`artifacts/runtime-tests/20260929-125721-3e46f6e7/`; lint also passed. The final targeted
Python/Lua run passed 21 tests, including per-viewer rate limits, outsider rejection,
connection-scoped reports, guest loadouts without admin and default-seat reset.

## Cross-feature visual regression prepared (2026-09-29)

The user requested preparation of a watched batch covering implemented vehicles, NPCs,
minimal combat and routines, continuing after ordinary failures once cleanup is verified.
The maintained procedure is [[../Runbooks/Visual Regression Batch]].
`./dayone visual-test prepare` freezes 17 cases and candidate vehicle routes without
launching anything. The nine-case NPC group uses `civilian-combat run regression`;
separate existing harnesses cover four-Actor reuse, off-screen chase and fatal lifecycle.
Five vehicle cases remain operator-managed probe sessions with explicit fixture/setup gates;
the old untracked Lofers reloadlua scripts are not a qualified AKR automation adapter.
No vehicle case or missing adapter is marked passed by preparation.

Added private ROUTINE scenario 6 to exercise the existing controller's short
walk/activity-wait/home plan with one Actor and no hunter. It uses ordinary visibility,
replica drift, loadout and pool cleanup checks. Scheduled real homes and controlled restart
remain in the next neighborhood task. No new behavior planner or movement executor.

Encounter case failures now preserve their reason and continue only after terminal cleanup
is verified, no resources remain, and the runtime remains READY in the same world/epoch.
Unknown ownership, blocked cleanup, connection/readiness loss and transport/epoch failure
stop the batch. Timeouts request bounded cancellation. Missing client-log evidence fails the
case; new scoped client errors remain visible. Batch failures return nonzero after safe
remaining cases run. Source capture now includes every split Java source root and worker rules.

Offline qualification passed at `artifacts/runtime-tests/20260929-124422-955b8955/`:
280 Python components, Java unit/integration/build guards and worker/protocol tests. Dedicated
operator tests prove continuation after a clean failed case and refusal after uncertain
cleanup. This preparation has not been deployed, run natively or accepted visually.
Normal services and clients were not started/restarted for this task.
Prepared manifest/checklist: `artifacts/visual-regression/20260929-124816-c4d95427/`
(17 cases, all five route candidates captured). Formatting/lint and the updated watched
skill validation also passed.

## Source normalization and AKR identity (2026-09-29)

The source now builds the gameplay agent as `net.akr.scenario` and the separate Observer
agent as `net.akr.observer`. The original companion mod is `AKRStoryteller`, its Lua
state and calls use `AKRScenario`/`AKRNative`, and the planner's protobuf namespace and
binary use `akr`. The `lofers.net` DNS name and Git remote are unchanged. This is a
source/build change: neither the running `.132` services nor `.160` production were
replaced or restarted. Existing development saves remain on the previous build;
the rebuilt prior agent and matching source archive are under
`artifacts/legacy-builds/230ead9/` with SHA-256 checksums. New AKR tests need a fresh
disposable world.

The one-JAR Java agent now has actual `compat`, `bridge` and `npc.core` packages.
Coupled runtime, engine, experiment and vehicle classes retain package-private
access within `net.akr.scenario`, but are divided into five matching Java source
roots so no source folder contains the previous hundred-file pile. A trial vehicle
package split exposed extensive internal route/controller state and was reverted.
The compiled `BuildGuard` contract now supplies manifest hashes, eliminating the
source-spacing regex that would have lost all guarded classes after formatting.
See [source style](../../CODE_STYLE.md) for the pinned formatter workflow and
Javadoc policy.

`./dayone runtime-test offline` passed at
`artifacts/runtime-tests/20260929-113504-63a74606/` after the namespace, package and source-root
changes. `./dayone test` passed 274 root Python tests, 116 Observer tests and the
Observer web TypeScript/Vite build. `./dayone lint`, `./dayone docs java`, the
Observer agent build and the C++ worker tests also passed locally. These checks do
not establish an in-game AKR migration, a watched NPC run or two-client replication.

A fresh disposable native world, `AKR_DayOne_Test_Headless_20260929_114249_8158e8`,
then passed all 23 headless checks with the renamed AKR build. The cases covered
walking and cancellation, locked/open doors, four-slot Actor reuse across residents,
terminal receipts, corpse handoff, reanimation, and 32-body prewarm. The report is
`artifacts/civilian-headless/20260929-114249-8158e8/ipc/native-report.json`;
the isolated test container stopped after the run. This is a functional engine check
without clients, not a watched or multiplayer qualification.

## Source checkpoint and tomorrow's task (2026-09-29)

The accepted next plan is saved in
[TASK_Four_Resident_Neighborhood.md](../../TASK_Four_Resident_Neighborhood.md)
for **2026-09-30**. It covers the independent movement regression, repeated lifecycle,
four independent civilians, ground-floor homes/routines and controlled restart of living
residents. Crash recovery and corpse/reanimation restoration remain explicit later work.
No neighborhood implementation, deployment or new watched test occurred while saving
this task and preparing the source checkpoint.

`./dayone runtime-test offline` passed in
`artifacts/runtime-tests/20260928-230404-2b11c513/`: 274 Python tests, the Java
unit/component/build-guard/premain fixtures, and C++ worker components plus Protobuf/socket
coverage. The rebuilt local agent was not deployed. The generic offline report's pending
in-game gates do not supersede the specific accepted watched evidence below.

This source checkpoint preserves the accumulated original runtime, Lua modules, planner,
Actor pooling/navigation/combat/lifecycle work, fixtures, skills and experiment records.
Downloaded dependencies, game files, credentials, saves and generated artifacts remain
outside source control. Passing offline checks is not release qualification: moving
flee/turn replication, repeated/four-resident lifecycle, sustained performance and actual
two-client checks remain open.

## Stationary fatal lifecycle accepted with one ordinary client (2026-09-29)

Batch `20260928-223514-adb64ed6`, world `AKR_DayOne_Test_20260928_223313_996072`,
epoch/event `621685ec-307a-46cc-a3ae-6909d55e0ead.1`, agent
`7a19134a247ecb9f1ac7b37b3bc24d6fecf1d2a51af56f4c84a54f15d57151fb`.
Machine outcome PASSED / COMPLETED, cleanup VERIFIED, zero retained resources.
Native attacks inflicted 19 damaging injuries; civilian made eight defensive contacts
before death. Native corpse/inventory/reanimation, durable terminal receipt, client-visible
reanimation hold, exact-object reassignment and replacement eight-tile walk all completed.
One Actor constructed, two assignments, one parked after cleanup. No new client error
lines in the scoped log. Controller p95/p99 0.8/2.8 ms; runtime 1.0/3.8 ms for this short
one-Actor fixture, not a population-capacity qualification.

Human confirmation: “Yes—replacement appeared and walked normally.” Separate passed
feedback archived. Previous human acceptance covers defense, death, reanimation and gun;
this completes the watched stationary lifecycle slice. The observer-facing correction
resolved this repeat's visibility timeout without altering native identity/replication.

Server remains running with no active case or retained resources. Next gates remain:
repeated fatal lifecycle in one session, four-Actor correctness, independent flee/turn
replication regression, full save/reload aftermath fidelity, performance at scale and
actual two-client qualification. Stationary fixture acceptance does not certify an
unrestricted pursuit/escape/death scenario or general multiplayer replication.


## Correction: reused replica exists; observer-facing omission fixed (2026-09-29)

Detailed audit supersedes the preceding post-death replication diagnosis. All **30 fresh
REUSE_POSITION samples** in `20260928-222547-01539f19` report the replacement at
(10589.5,10080.5), transitioning GenericDefaultState → IdleState. Client actor logs
independently agree. The absent report was from CLEANING/COMPLETE and was wrongly
attributed to reuse. Native client replication did occur; visible presentation gate failed.
Evidence: batch-local `reuse-visibility-audit.json`. Do not change online IDs or clear
native death bookkeeping based on the disproven missing-replica hypothesis.

EncounterWatch only oriented the observer in POSITIONING, omitting REUSE_POSITION.
It now faces the actual Actor in both reuse positioning and reuse walk. Visibility still
requires stock screen/LOS checks; no fog bypass. Separate presence/on-screen/loaded/LOS/
visible diagnostics are bounded to 200 characters and exported in additive EncounterPair
field 43 (`client_visibility`). A Lua regression verifies facing for all viewing stages
and no forced facing during ordinary combat. Native inspection of pinned GameClient,
ConnectedPacket, PlayerPacket and PlayerTimeout confirms no engine changes were needed.
Exact visual cause remains pending confirmation by the repeat; no native reuse failure
is inferred from the old visibility timeout.

All 274 Python tests and Java unit/integration/build guards/premain pass. Agent SHA:
`7a19134a247ecb9f1ac7b37b3bc24d6fecf1d2a51af56f4c84a54f15d57151fb`.
No additional headless run: only observer-facing/diagnostics changed; previous native
lifecycle/survival evidence remains applicable within its documented limits. Previous
cleaned server stopped; new watched world `AKR_DayOne_Test_20260928_223313_996072`
prepared. Test client confirmed closed before installation. Await fresh readiness for
the same stationary-defense lifecycle sequence. Prior visual acceptance of defense,
death and reanimation remains; replacement walk and two-client acceptance remain pending.


## Stationary lifecycle watched: reanimation visible gate passed; reuse replica missing (2026-09-29)

User confirmed in-world readiness. Batch `20260928-222547-01539f19`, epoch/event
`f6bfdc35-f0aa-478d-ad03-d85825803501.1`, world
`AKR_DayOne_Test_20260928_222229_cda5f4`, revision
`37e84914692be49f4095aeec69ee8c0033ee5f6f422b1fb299c44cbdbb43eb05`.
Stationary-defense fixture recorded 31 damaging injuries and 11 defensive contacts before
native death. Native corpse/reanimation completed and the fresh client reanimated-visible
hold passed before relocation. Durable receipt and same-object server reassignment passed.
However replacement Actor presentation again timed out at REUSE_POSITION (client reported
absent). Thus prior heartbeat fix was insufficient; inspect native dead-player/online-ID
replica bookkeeping rather than claiming post-death reuse validated.

Final phase FAILED; exact cleanup VERIFIED, resources empty, one Actor parked. No automatic
retry. Human confirmed “Saw all three; gun present”: defense, death and reanimation presentation
accepted for this stationary one-client fixture. Recorded as partial feedback because
replacement replica presentation failed; full lifecycle remains unqualified. A pre-countdown PlayerHitSquare inconsistent-packet warning exists; do not claim
an entirely clean network log. No validated observer hit was recorded by interference gate.
The server remains running without another submission. The flee replication regression
and two-client acceptance are still pending independently.


## Flee drift investigation and stationary lifecycle fixture (2026-09-29)

The `20260928-221607-385fbdb0` trace stays below ~0.93-tile peak during its first
straight RUN, then grows during short WALK/turn segments after resuming movement.
There is a concrete wire/translation mismatch: switching to WALK immediately sends
walking flags/blend, while MovementTransition previously retained RUN speed during
its 200ms deceleration. Cap transitional translation at the current native clip rate;
retain the existing acceleration warm-up. Classify forecast/caution on each new edge
before its first movement, rather than inheriting the previous edge's classification.
The unchanged >3 tiles / 750ms drift gate remains. This is a candidate correction,
not proof the whole observed gap is resolved; native turning/prediction remains a
watched regression gate. No client transforms, collision bypass or threshold relaxation.

Per user authorization, prepare an explicitly announced stationary-defense lifecycle
fixture on the open ground with no doors. It uses the existing controller movement-hold
boundary and an explicit fixture-only unavailable-escape observation. Navigation
requests are suppressed only for that controller fixture; normal civilians are unchanged.
Native defense, injury attempts, damage/reactions and death remain active. It starts at
100 HP against one Superhuman-strength, Pinpoint-hearing, Eagle-sight fast shambler.
No lethal contact in 120 seconds stays NOT_EXERCISED. This isolates terminal lifecycle;
it is not autonomous-survival acceptance. Gun acknowledgment, visibility hold, exact
cleanup and separate human feedback remain required.

Java unit/integration and 274 Python tests pass. Native headless survival validation is
passed in `20260928-222158-842d0e` (epoch
`242066c0-d97d-4d6b-a0cf-1db57e31565f`): 13 encounters/13 contacts,
native corpse/terminal receipt/reanimation/exact-body reuse and cleanup. Walking p95/p99
1.2/2.4 ms; survival controller 8.3/30.82 ms; total 7.6/146.09 ms includes cold prewarm.
Release performance gates remain unmet. Agent built/deployed SHA
`37e84914692be49f4095aeec69ee8c0033ee5f6f422b1fb299c44cbdbb43eb05`.
All 55 installed client files match; fresh client launch requested, awaiting readiness.
New watched world prepared:
`AKR_DayOne_Test_20260928_222229_cda5f4`, no case submitted. Prior cleaned watched server
was stopped gracefully. Production/normal .132 untouched.


## Door-free lifecycle attempt: escape, replication failure, cleanup verified (2026-09-29)

After explicit user readiness, ran batch `20260928-221607-385fbdb0`, event
`1cf19cf2-1c9f-4f67-9079-cb9f0d7cd170.1` in world
`AKR_DayOne_Test_20260928_221306_50f2a7` with revision
`e1d4996d218218705285edb56a5933687633721fe1f5672788432ca4f8a05b83`.
The current-case loaded-pistol acknowledgment arrived before countdown. The full-health
civilian fled 26.49 tiles; zero injury attempts, injuries or defensive contacts. It
remained at 100 HP. User observed: “Hahahaahaha, the NPC just fucked off.” Separate partial
human feedback is archived; this does not qualify death, reanimation or post-death reuse.

The test failed `client_replica_divergence:0:3.23` before the encounter timeout, rather
than reaching NOT_EXERCISED. Cleanup subsequently verified: resources empty, one
constructed/reused body parked. Machine report retains original failure; case client
log slice has zero error lines. Controller p95/p99 3.2/5.4 ms; total 3.5/65.79 ms including
initialization. Performance targets not qualified. Server remains ready without a new
submission. No automatic retry. Next: diagnose divergent flee replication and design a
visible native fatal-contact fixture that does not depend on a shambler catching a healthy
runner or on view-blocking doors. Preserve escape behavior and do not force death.


## Fatal watched retry failed; visibility/loadout corrections prepared (2026-09-29)

Batch `20260928-220300-e7815a7c`, event
`4885aa10-16b0-4f45-87ff-217d05b2965f.3`, recorded four damaging incoming attacks,
two defensive contacts, native death/corpse/reanimation, durable terminal receipt and
same-Java-object reassignment. It failed at `REUSE_POSITION`; cleanup remained blocked
with Actor, hunter, aftermath and geometry reservations. The disposable server was
stopped gracefully and the complete failed world `20260928-215420-1d4250` preserved.
Do not classify its resources as cleaned or the lifecycle as accepted.

Human feedback (also archived verbatim): death looked sudden, reanimation was not visible,
and the pistol was missing. User requested stronger hearing/strength and removal of side
doors. Original fixture started at ~10 HP. Source review found immediate teleport after
server reanimation, lifecycle early returns bypassing Actor replication while waiting
for visibility, and a corpse cleanup predicate requiring a null square reference even
though stock removal retains that reference. These explain concrete harness defects;
corrected native cleanup and visible reuse still require execution evidence.

Prepared revision `e1d4996d218218705285edb56a5933687633721fe1f5672788432ca4f8a05b83`:
- Full-health civilian; no fixture doors. Only native damage may cause death. Escape is
  permitted; lack of a fatal encounter stays NOT_EXERCISED, with no automatic retry.
- Dedicated world ZombieLore: Strength=1 (Superhuman), Hearing=1 (Pinpoint), Sight=1 (Eagle).
- Eight continuous seconds of client-confirmed visible reanimation before teleport/reuse.
- Lifecycle replication/config publishing runs even while waiting; corpse cleanup checks
  actual memberships instead of a retained square pointer.
- Loadout acknowledgment is scoped to current player object, epoch and event. Every new
  case supplies/refills the marked pistol and two spare magazines; repeated requests within
  a case do not replenish spent ammunition. Countdown waits for that case's confirmation.

Java unit/integration, pinned build guards and premain passed; **274 Python tests passed**. Focused Lua/Python regressions
cover stale-event/reconnect loadout acknowledgments and diagnostic identity fencing.
The fresh prepared world is `AKR_DayOne_Test_20260928_221306_50f2a7`, .132:16281.
Client process was confirmed absent before installation. No new watched case submitted.
Previous failure, full native cleanup, actual reanimation visibility and two-client
qualification remain separate gates; no production or normal .132 changes.


## Lifecycle watched attempt interrupted before contact (2026-09-29)

World `AKR_DayOne_Test_20260928_215420_1d4250`, epoch
`4885aa10-16b0-4f45-87ff-217d05b2965f`. Batch `20260928-215914-aedfd06e`
started after explicit readiness. The user reported the hunter attacking a door and
requested a restart. No damaging contacts or death occurred. Cancelled and verified
cleanup: zero resources, one constructed Actor, one parked. Verbatim partial feedback
is archived independently; lifecycle acceptance remains pending.

The requested same-session retry `20260928-220032-b388c539` stayed WAIT_OBSERVER with
zero participants because the local client had closed. Cancelled that empty submission;
cleanup verified. Relaunching the pinned client and awaiting fresh user readiness before
another `run lifecycle`. Zombie sight/AI and fixture parameters remain unchanged.

## Fatal lifecycle implementation prepared; watched acceptance pending (2026-09-28)

Implemented the next death/aftermath/reuse slice in the private runtime. `LIFECYCLE=5`
is additive; a separate one-Actor session runs native incoming injury → death → durable
terminal receipt → original corpse inventory → native reanimation → same engine object
as a different resident → eight-tile walk. Added scoped reanimation-return observation,
terminal planner fences, bounded asynchronous fsync journal, population provenance,
independent scenario/cleanup outcomes and cleanup without a named observer when every
connection is confirmed absent. Unknown viewers/removal still retain exact resources.
The initial dying fixture has ~10 HP, ordinary clothing, nested inventory and no weapon.
Only this new disposable world uses transmission 1, mortality 7 and reanimation 5;
after the eight-second corpse hold only its native timer is shortened. No forced kill,
attack-acquisition override or client Java injection. No fatal encounter within 120 seconds
is NOT_EXERCISED, followed by cleanup without automatic retry.

Manual review caught and fixed a terminal-state bug: ordinary `Plan.detach` reset DEAD
residents to IDLE. Terminal state now survives detach; Lua/Java reject late planning.
Death during an incoming-injury tick stops later actions in the same update. Death before
native retirement starts enters DYING; death after uncertain removal remains unresolved.
Journal files from an earlier process block admission pending world-save reconciliation.
This is not proof of full world-save/ModData atomicity or restored aftermath fidelity.

Validation:
- Final Java unit/component, transformed-bytecode/build guards, native asset import and
  premain integration pass; **273 Python tests passed**; watched skill validates.
- Headless `20260928-214538-fa5766`, epoch `97ad51b9-4767-4b73-bd6f-e6f5afb53868`,
  passed early native reanimation, durable receipt, nested inventory/exact Actor reuse,
  doors and **13 strengthened survival encounters / 13 contacts**. Controller p95/p99
  **5.8/22.37 ms**: correctness passed; release performance targets remain unmet.
- The previous timeout reproduced in `20260928-214056-ff2336` at round 2, fourth Actor,
  X10762.5/Y9847.5, repeatedly replanning without native movement. Source showed escape
  search allowing unqualified climbs while execution supported only WALK/DOOR. Filtered
  escape edges by the Actor's executable capabilities; added a detached regression.
  Native rerun passed without weakening collision or raising deadlines. Original failed
  evidence remains preserved; the old trace did not record its exact rejected edge.
- Headless `20260928-214921-b2014f`, epoch `62ae52d9-9400-43ed-92cb-3f5c3e2faeff`,
  passed reanimation after Actor release, terminal receipt and nested inventory preservation,
  same-object reuse, door/snapshot/pool tests. Total p95/p99 **4.2/86.54 ms** includes cold
  prewarm (32-body capacity check); not a steady-state capacity claim.
- Final deployed agent `acf9f88709491236ed3b0551d24678ede201efa6c2656628caa7b50b56af99fa`.
  Later final changes are fixture validation/metadata, replacement resident registration,
  ordinary-client diagnostics and the offline/dispose death guard; final component/integration
  checks cover those changes. No human acceptance of this new lifecycle case yet.

Fresh watched world **AKR_DayOne_Test_20260928_215420_1d4250**, epoch
`4885aa10-16b0-4f45-87ff-217d05b2965f`, is **READY**, .132:16281.
All 55 installed ordinary-client mod file hashes match its receipt. Startup error signatures
match the previous watched server apart from the launcher stderr preload notice; runtime
reached READY with no submitted event. See its `lifecycle-preparation.json`. No event submitted,
no client launched for this iteration. Wait for readiness and use
`./dayone civilian-combat join`, then `./dayone civilian-combat run lifecycle` after
confirmed in-world readiness. Retain automatic placement/protection/daylight/pistol/chat.
The earlier uncertain watched world is preserved; production .160 and normal .132 untouched.

Remaining gates: actual client-owned fatal contact and death/reanimation presentation,
watched old-replica absence and reuse/cleanup, full world-reload aftermath fidelity,
two-client comparison and performance. Unexpected hunter death remains retained rather
than claiming its unproven corpse cleanup succeeds. The normal admission limit remains four.
See [[../Design/Civilian Defense and Death]] and [[../Runbooks/Operations]].

## Door/corner watched behavior accepted by user, 2026-09-28

Batch `20260928-211357-bfef6568`, epoch `7ae7eb1c-dd11-4004-aaa5-acf49dfbf79c`.
User verdict, verbatim: **“One of the zombie never attacked due to it's normal behavior.
Test accepted and passed”**. Separate passed human receipt is archived. Accept the
observed one-client door/corner/startup behavior; do not require every native zombie to
attack merely to force a deterministic demonstration. Three civilians defended/escaped;
one hunter retained a native target but did not attack. Reported peak replica gaps were
1.048/0.777/0.778/0.783 tiles, versus the previous rejected run's 6.34-tile peak.
No sustained divergence gate fired. Client geometry confirmation allowed exits normally.

The client disconnected before the fourth encounter completed. Machine reason became
`observer_disconnected`; cleanup remained blocked with four Actors, four hunters and
fixture geometry tracked, zero parked. Preserve the original machine failure/ownership:
human acceptance does not verify cleanup or four completed combat encounters. Operator
cancelled the pending case and stopped the disposable server gracefully, preserving its
world/evidence; do not reuse this uncertain pool as though retirement passed. No further
case is running. Two-client, strengthened headless survival timeout and sustained load
remain separate gates. Later watched tests should distinguish native non-acquisition
from presentation failure while still requiring exact cleanup accounting.

## Door/corner corrections deployed for watched verification, 2026-09-28

Fresh disposable world `AKR_DayOne_Test_20260928_211224_4c4226` is READY on .132:16281,
epoch `7ae7eb1c-dd11-4004-aaa5-acf49dfbf79c`. Deployed agent
`bb6bf189dc7ce57d36ad6e3b2dde6391fe0bf358e5a4e1dadb4a34da3c615d6f` and all 55
installed client files verified. Previous session stopped gracefully, client closed
before Lua installation, pinned ordinary client relaunched. No event submitted.
Wait for explicit user readiness, then `./dayone civilian-combat run defense 4`.
Watch startup acceleration, door bumps, corner continuity and apparent wall teleportation;
retain automatic placement/protection/loadout/daylight/announcements. Failures stop
advance. No visual acceptance of these corrections yet.

## Four-civilian visual rejection: doors/corners, 2026-09-28

The user rejected batch `20260928-205933-7da56482`: running civilians bumped into a
door and appeared to teleport through walls despite apparently valid server paths.
Verbatim failure feedback is archived separately. Its machine pass remains immutable;
**the encounter is not visually accepted**. Straight WALK/RUN comparison acceptance is
limited to its unobstructed track.

Position analysis found sampled client/server separation up to **6.34 tiles**. Native
client path-failure recovery can teleport remote players; the exact branch was not
captured. Fixture exit doors were removed without client confirmation. No single cause
is claimed proven. Candidate changes and limits are in [[../Design/Resident Plans and Locomotion]].

Added startup blend/ramp, WALK approaches to nearby turns/doors, prompt heading/gait
packets, downward prediction quantization, bounded client exit-geometry confirmation,
and a sustained replica-gap watched failure gate (3 tiles/750 ms, fresh reports only).
Java checks and 271 Python tests pass, including blocked/unloaded/cleared exit reports,
transition/reset behavior and divergence-gate tests. Native movement/reuse validation passed; evidence follows. New code/Lua have **not** been installed in the running watched session;
no further visual test has been started. Keep the separate headless survival timeout
and two-client gates open.

Native validation completed: world `20260928-210702-d85943`, epoch
`669a7813-2095-455f-803c-d868cec8fa56`, passed eight worker routine/run/reaction
assignments plus the two-Actor 50-tile comparison. WALK **23.0975 s**, RUN **12.0987 s**;
four constructed, ten assignments, four parked, zero occupied. Work p95/p99 **1.7/3.3 ms**.
Snapshot agent `2d00c35097c61de351399c506b9b8544bdcbcd083c385f111ada48ed7e56aa07`.
Final build (including watched drift gate and exit-hold refinement)
`bb6bf189dc7ce57d36ad6e3b2dde6391fe0bf358e5a4e1dadb4a34da3c615d6f` passes Java
integration/guards; Python 271 passed. Native movement changes were in the tested
snapshot; later changes affect the watched fixture only. Headless world stopped
automatically. Live watched session remains on its old build, idle. Deployment requires
server restart and a fresh client load for the updated diagnostic Lua. No visual check
was started. Client door API/replication timing and corner smoothness remain native-client
gates; detached report checks do not prove them.

## Four-civilian rerun after explicit readiness, 2026-09-28

Batch `20260928-205933-7da56482`, same epoch `819447fe-fb20-47cb-910f-dfe75549a435`,
started after the user said “I am in”. Four civilians/four shamblers completed:
one defensive contact each, incoming injuries 10/5/2/3, escape travel
16.46/13.98/17.53/18.35 tiles. Cleanup verified; four constructed, ten cumulative
assignments, four parked, zero event resources. Client ERROR/Exception count zero.
Machine pass; human feedback requested and pending. The prior successful watched
run was missed by the user and does not count as human acceptance. No automatic
next case. Separate strengthened headless survival timeout remains unresolved.

## Watched rerun held for explicit readiness, 2026-09-28

User missed batch `20260928-205424-5e4b9d53`; verbatim feedback is recorded as partial,
not visual acceptance. Requested rerun `20260928-205753-70d2081d` found no connected
client and was cancelled during WAIT_OBSERVER before spawning. Cancellation verified,
no resources; separate reconciliation receipt saved. Pinned client relaunched. Wait
for the user's **“in and watching”** response before submitting `run defense 4` again.
Same server epoch and pool remain available. No pending test will start automatically.

## Four-civilian watched defense/escape machine pass, 2026-09-28

Batch `20260928-205424-5e4b9d53` reused the comparison server and its four bodies
(epoch `819447fe-fb20-47cb-910f-dfe75549a435`). The client was closed when requested;
automatic reconnect completed before scene preparation. Four civilians and four native
shamblers: each civilian landed one defensive contact and escaped, travelling
23.88/17.06/19.14/17.52 tiles with 2/7/8/8 incoming injuries respectively.
The previously reproduced null MoodlesUI combat exception did not recur.

Cleanup verified: four constructed, six cumulative assignments, four parked, no event
resources. Case-scoped client ERROR/Exception count zero. Runtime work p95/p99/max
1.5/3.9/40.73 ms; controller p95/p99 1.0/2.7 ms. Short functional case only, not sustained
capacity qualification. Human visual/audio feedback requested and pending.
The stronger headless pain/interruption survival fixture remains separately failed;
this watched success does not erase its unexplained escape timeout. Two-client and
crowd gates remain pending. Server/client remain available, no automatic next case.

## Parallel WALK/RUN accepted in one client, 2026-09-28

Batch `20260928-205224-4a741075`, world `20260928_205047_e4dcd9`, epoch
`819447fe-fb20-47cb-910f-dfe75549a435`: two civilians, zero zombies, parallel 50-tile
tracks. WALK **23.00 s**, RUN **11.90 s**, approximately **1.93× faster**.
User verdict, verbatim: **“Runner clearly faster; both smooth”**. Separate human
receipt saved beside the immutable machine report. The test completed with verified
cleanup, four bodies parked, two assignments and zero remaining event resources.
Case-scoped client log: zero ERROR/Exception lines. Native animation warnings about
`turning180` appeared in server logs; no observed motion defect was reported.

This accepts the speed/presentation correction in one ordinary client. It does not
qualify two-client replication, crowds, terrain-specific animation profiles or the
outstanding strengthened four-civilian survival timeout. Server/client remain available;
no additional case is submitted automatically.

## Parallel WALK/RUN watched test deployed, 2026-09-28

At user request, fresh disposable world `AKR_DayOne_Test_20260928_205047_e4dcd9`
is READY on .132:16281, epoch `819447fe-fb20-47cb-910f-dfe75549a435`.
Agent `5130a56ffaad03f02442d9e6a3831f3a212fb30d7d94e67595618015fa5abbf1`
and all 55 installed client files verified. Pinned ordinary client launched.
No event submitted yet: wait for the user's readiness, then `./dayone civilian-combat run compare`.
Exactly two civilians, zero zombies, matched healthy profiles, parallel 50-tile tracks.
Retain normal automatic placement, protection, loadout, daylight and announcements.
Watch actual speed separation and correction jumps; full-track camera coverage remains
unproven. Machine native timing passed previously; this entry grants no visual acceptance.

## Gait speed and combat diagnostics, 2026-09-28

The user confirms running animation now appears, but rejects equal-looking WALK/RUN
velocity and jumpy client motion. Exact feedback is preserved in the original watched
world's `human-feedback-verbatim-speed.json`; prior “human pending” entries are historical.

The server speed formula incorrectly treated WalkSpeed (a blend coordinate) as a linear
physical multiplier. The replacement caches the installed animation root-motion profiles
and uses the pinned native blend picker. Default healthy WALK/RUN commands are about
2.18/4.22 tiles/s. Native collision stays enabled. Stock prediction now follows verified
collinear buffered edges up to 0.6 seconds, bounded by turns, doors, obstacles and deferred
stop boundaries. No secondary protocol or client JVM hook was introduced. Mean clip
speed does not qualify every terrain/weapon/transition animation; smoothness remains a
human gate. The new `run compare` definition uses two matched civilians and two parallel
50-tile tracks, zero zombies, independent times and verified cleanup.

Diagnostic batch `20260928-203325-b2c5c39a`, world `20260928_203127_5ef695`, reproduced
the four-Actor exception with nested evidence: `CombatManager.attackCollisionCheck`
calls `MoodlesUI.wiggle()` on a dedicated server where that UI is null. The scoped hook
skips only this presentation call during managed native combat; damage calculations
remain native. The diagnostic failed as expected and later verified four parked bodies,
zero event resources. Server was then stopped; no client remains running.

Java unit/integration/hash guards pass (agent
`5130a56ffaad03f02442d9e6a3831f3a212fb30d7d94e67595618015fa5abbf1`), as do 271 Python tests.
Native survival `20260928-202716-2572f9` passed 25 checks and 13 contacts with zero owned
Actors. Survival p95/p99 6.9/39.21 ms still exceed budgets. A stronger pain/UI regression
in `20260928-203658-614669` exercised guarded painful attacks successfully, but failed
later: one of four civilians stopped after 4.05 tiles and exhausted its escape timeout
(57 path requests). Preserve this failed world; the full strengthened survival gate is
**not passed**, and its blockage is not yet diagnosed. No crowd admission change.

Native planner/comparison world `20260928-204341-c60c3b`, epoch
`9554d282-dc71-4532-8baf-2545c51c7bfa`, **passed**: eight worker-driven routine/reaction
assignments followed by two simultaneous 50-tile tracks. WALK **23.0003 s**, RUN
**11.8987 s** (1.933× faster; achieved 2.174/4.202 tiles/s). Four engine bodies,
ten assignments, four parked, zero occupied; added work p95/p99 **1.7/3.2 ms**.
Snapshot agent `61d1429c6475955f2398b9edfcc2e557d54d2a9f68c5f494726f527b99455e37`.
This confirms authoritative speed separation, continuous native execution and cleanup,
not client visual smoothness. The first comparison attempt `20260928-203111-62fba1`
correctly rejected an obstructed lane; the final fixture selects clear lanes with bounded
native preflight instead of disabling collision. Report includes exact elapsed times.
Disposable services are stopped. Latest watched comparison code is built but not deployed
or human-accepted; verify both full tracks and camera coverage on preparation.

## Incremental watched batch stopped at four Actors, 2026-09-28

Batch `20260928-200957-b3846c22` in world `20260928_200711_1bc087`, epoch
`6cfdd74e-41c8-4acd-a038-3a31122cbb7e`: all four one-Actor cases completed with
verified cleanup. LOCOMOTION exercised walk/run/handoff/deferred-stop/resume;
OPEN_ESCAPE moved 28.50 tiles; INCOMING_INJURY recorded two damaging hits and 13.8
tiles; DEFENSE_ESCAPE recorded one defensive contact, seven incoming hits and 13.0
tiles. Machine evidence only; human visual verdict is pending.

First four-Actor DEFENSE_ESCAPE wave failed with
`IllegalStateException:resident_execution:6cfdd74e-41c8-4acd-a038-3a31122cbb7e.5-resident-1`.
That Actor had resumed from an injury into SwipeStatePlayer. The captured error loses
its underlying cause; do not claim a diagnosed combat/navigation cause. Cases six and
seven did not run. Cleanup subsequently verified: four constructed/four parked bodies,
eight total assignments, no remaining event resources. Original machine report remains
failed/unreconciled; subsequent status evidence records cleanup independently.
Captured client log contains zero ERROR/Exception lines. This does not supply visual
acceptance. Next retain nested failure diagnostics and reproduce this case before
advancing population. Server remains idle; no automatic rerun.

## Incremental watched batch prepared, 2026-09-28

User requested a progression from confirmation of the latest movement changes to
multi-Actor scenarios. Fresh world `AKR_DayOne_Test_20260928_200711_1bc087`, epoch
`6cfdd74e-41c8-4acd-a038-3a31122cbb7e`, is READY on .132:16281. No cases submitted yet;
await user readiness. Pinned ordinary client launch initiated. Previous failed world
and exact unresolved ownership remain preserved.

Seven cases / 16 assignments on the same four-body pool: enhanced LOCOMOTION,
OPEN_ESCAPE, INCOMING_INJURY, DEFENSE_ESCAPE, then three DEFENSE_ESCAPE waves of four.
LOCOMOTION now checks four tiles WALK, eight tiles RUN with staged continuation,
reverse movement with deferred cancellation on the first edge, two seconds stationary,
then resumed RUN. A held shambler remains explicit; actual pursuit begins in case two.
Normal spectator protection, loaded pistol, automatic placement, clear noon,
announcements and 8/2/2 timing are retained. Failure or uncertain cleanup stops advance.

Agent SHA256 `9229ba9430f4415c8e0cd16710e4948b3a1aa5a6fd928c2c57cd2e039512d8db`;
Java unit/integration/guards and five targeted operator/client tests pass. Installed
client Lua and deployed agent hashes verified. Prepared definitions/source hashes are
saved in the new world's directory. These are preparation results, not visual acceptance.

## Continuous movement and planner feedback — implementation, 2026-09-28

Active design: [[../Design/Resident Plans and Locomotion]]. The user requested
implementation with **no visual check yet**. No client was launched. The previous watched
world and its unresolved ownership remain preserved; production .160 and normal .132
are unchanged.

Implemented an eight-edge window with refill at four, staged boundary handoffs retaining
the movement clock, deferred cancellation of safe edges, immediate interruption for
unsafe execution, and candidate invalidation. Active C++ progress no longer generates
replacement actions; completed/cancelled/failed outcomes use a 64-slot acknowledged
session journal with reconnect retry. Local survival and resource cleanup do not wait
for the worker. Goals and actual effects remain game-owned.

The pinned engine's idle Actor action caused stock PlayerVariables.set to omit WalkSpeed
and WalkInjury. Fresh RUN client samples had zero WalkSpeed; stock run animation uses that
blend axis. The adapter now supplies these native fields explicitly under class-hash
guards, preserving stock serialization. Real headless serialize/parse/apply tests pass
for WALK/RUN and injury blending. **Visible running and smoothness remain unverified**;
this is a substantiated packet fix, not human acceptance. Client action, packet variables,
walk modifier and perception age/rejection diagnostics are available for the later batch.

Validation so far: 270 Python tests, Java guards/component tests (including real IPC
lost-ack/reconnect retry), and C++ core plus 16 wire scenarios pass. Initial planner world
`AKR_DayOne_Test_Headless_20260928_195047_d48558` passed eight assignments on four bodies,
stock movement-field round-trips, continuous eight-edge RUN joins, owned hit recovery,
and receipt draining: zero occupied, four parked, work p95/p99 **1.0/2.2 ms**.
Final world `AKR_DayOne_Test_Headless_20260928_195821_2301fc`, epoch
`625daba5-8cb0-49ab-b96c-7f1d998e6cfd`, also passed eight assignments, now additionally
checking native deferred cancellation on every assignment: exactly one active edge
completed, Actor centered on that boundary, gait IDLE, no continuation executed.
Zero occupied/four parked; work p95/p99 **1.0/3.0 ms**. Agent snapshot SHA256
`4e70b4298703812b5523ad54f9647a7e14986a9503faee8a931447988dbc55df`.
The final Java integration layer also passed. All disposable containers are stopped;
no watched server or client was started.

Survival world `AKR_DayOne_Test_Headless_20260928_195552_9f647c`, epoch
`6543bc6a-45a6-439e-93dc-b09401775dbd`, passed all 25 native checks, including 13
survival encounters/contacts, snapshot/identity reuse, doors, corpse handoff and native
reanimation. Zero owned Actors at completion. Survival work p95/p99 **4.1/18.2869 ms**;
whole-fixture work **4.0/192.137079 ms**, with cold initialization p95 **187.502343 ms**.
These exceed release budgets; small-fixture success does not qualify crowds. Preserve
both cold and steady measurements. Report and source snapshot are under each world's
`artifacts/civilian-headless/` directory. Both completed worlds stopped automatically.

Remaining gates: watched running/smoothness/reaction timing, true client-owned pursuit,
two-client replication, incoming injury/death integration, unsupported traversal and
performance. Threat-memory/perception behavior was instrumented, not silently relaxed.

## Watched retry — running animation still failed, 2026-09-28

World `AKR_DayOne_Test_20260928_191738_658aa9`, epoch
`ed0b3121-3c21-483a-b32e-32945403e19c`, batch `20260928-191922-d57501d1`.
LOCOMOTION completed with verified cleanup; this is a machine result only.
OPEN_ESCAPE moved the civilian 33.23 tiles and hunter 35.21 tiles before the user
closed the client. The user explicitly reports no running animation, while bush and
attack reaction animations work. Human verdict is **failed**, archived verbatim in the
batch. No later case ran; smoothness/diagonal visual acceptance is not established.
Before disconnect, the controller also recorded `candidate_origin_stale` after a
RECOVER transition; this remains a separate navigation issue to diagnose.
Disconnect left CLEANUP_BLOCKED with exact Actor, hunter and terrain ownership retained
(three bodies parked, fourth retained). Preserve this world; do not claim cleanup passed.
Disposable server stopped gracefully; no project containers remain running. No further watched retry until a substantive running
presentation fix has independent checks. Next inspect stock remote IsoPlayer running
animation inputs and packet decoding against the pinned engine, alongside our Actor gait
publication; do not infer animation success from RUN flags or translation speed.


## Resident plans and locomotion — visual feedback, 2026-09-28

Active design: [[../Design/Resident Plans and Locomotion]]. User observed only the first
two cases: basic navigation/flee behavior worked, but civilians visibly walked instead
of running, made small position jumps, and followed cardinal-only paths. This is **partial
functional acceptance and failed running/smoothness presentation**, not acceptance of the
injury/defense/four-person cases. No batch advanced beyond the first two cases.

Durable server-owned goals and the real C++ three-action routine are implemented, along
with local fallback, native reaction pause/recovery, retained pending replies, bounded
continuous traversal, geometry caching and diagnostic traces. Native worker run
`20260928-184215-387006` passed eight assignments on four bodies including an injected
reaction during path planning: zero occupied, four parked, work p95/p99 0.7/1.9 ms.
The broader survival regression `20260928-182814-e612da` passed 25 native checks and
13 contacts, but work p95/p99 6.0/25.2 ms exceeds release targets. No crowd scaling.
268 component tests and Java compatibility/fixture checks passed before the latest
presentation/diagonal changes; the new Java fixtures pass too.

Watched world `AKR_DayOne_Test_20260928_184805_ac1133`, batch
`20260928-185010-9b395844`: LOCOMOTION completed; OPEN_ESCAPE had a real pursuit and
three damaging attacks with three resumes (health about 86.3 at cancellation request),
but did not establish escape. Cancel requested, then disposable server/client stopped.
Human feedback is archived verbatim against this batch. The earlier non-pursuing-hunter
case is not treated as a successful escape. All worlds/evidence are preserved.

Latest source, not visually qualified: replacement paths start at the committed route's
reachable endpoint and wait to join there; graph offers eight neighbours at Euclidean
cost, preserving native corner guards; movement packets publish once after traversal
instead of mid-edge, and stationary heartbeats cannot overwrite an active gait. Native
planner/diagonal run `20260928-185937-3ef7e4` passed eight assignments with four
constructed/parked bodies and zero occupied (work p95/p99 1.8/3.6 ms). The diagonal
round-trip bound passed. Survival/corner regression `20260928-190354-252326` passed all 25 checks, including
13 encounters/contacts and clean ownership. Work p95/p99 **8.7/36.042234 ms** is above
release targets and worse than the prior 6.0/25.2 ms run: profile geometry/cold fixture
work before any population growth. Both headless worlds stopped gracefully. The user is AFK; do not launch another
watched test until they return. The test server and client are stopped.

Ordinary clients, pinned native hooks, collision and production isolation are preserved.
Narrow-door navigation, final running animation/smoothness, complete incoming pursuit,
integrated death and two-client agreement remain explicit qualification gates.

## Moving encounter retry — shorter waits and fixture release

User requested 60% shorter idle periods. Moving batch hold/countdown/off-screen quiet
are now 8/2/2 seconds. Unrecoverable death ends operator polling immediately; transient
cleanup has a 24-second bound. Native readiness and active case timeout are unchanged.
Prior batch `20260928-172613-80a959d2` passed OPEN_ESCAPE with verified four-body
cleanup; INCOMING_INJURY stalled at (10590.5, 10068.76855), died, and retained resources.
That world was stopped and preserved. The rotated exit-door collision is suspected,
not proven. Revised fixture removes its nine east-side barriers incrementally after
injury/defense rather than swinging a single door into the escape route. Native movement
collision remains unchanged; narrow-door navigation is still unqualified.
Retry world `AKR_DayOne_Test_20260928_173720_4991f6`; Java build/fixtures passed,
agent SHA `a7fdc286800da801e71e08915398a57b323b122e59e202adfae3d96a2a5b1540`.
Watched outcome pending.

## Reusable moving encounters — 2026-09-28, watched batch pending

Implemented the typed encounter backend on existing private runtime submit/status/cancel,
retained four-body session pool, event/generation-scoped client readiness, pursuit mode,
injury interruption, component timing and operator batch/evidence commands. No full Java
hot reload or ambient population activation is implied. The stationary fixture remains.

New maintained skills: `dayone-watched-testing` and `dayone-test-iteration`; procedures
live in [[../Runbooks/Watched Testing]]. Automatic positioning/protection, clear noon,
loaded 9mm loadout and announcements are retained. Feedback comes after the agreed batch.

260 project tests and Java unit/integration/guard fixtures passed. Both skills validate.
The native headless checkpoint `20260928-170403-f714ac` passed all thirteen stationary
encounters, native lifecycle and body reuse, then stopped gracefully. Performance remains
above release targets (survival p95 3.4 ms / p99 6.5 ms). Moving backend not yet qualified.

Prepared world `AKR_DayOne_Test_20260928_170922_c7b03e`, epoch
`0c2e6a3f-8217-4bb7-8d88-ed0e16a8df47`; deployed agent SHA-256
`21f7be70d23f38dda2ee56fd962815db83afd154f8c7414feaf3a681f0c8a946`.
The first status call revealed host Python's incompatible protobuf module; combat commands
now re-exec in the project venv. Runtime reports READY; no encounter was submitted before
the user's request to restart the sequence from the beginning. Client join is underway.

## Autonomous defense and escape adapter — 2026-09-28

The existing Lua civilian model now drives native perception, bounded A* escape,
walking and a reusable wind-up/contact/recovery executor. Native selected-target
filtering precedes damage. The native incoming-bite adapter has once-per-attack and
owner/freshness checks; its live owner path remains unqualified.

The disposable headless batch passed 13 autonomous locked-door defense/escape cases
(one, then four residents for three more waves), 13 native hammer contacts, and reuse
of the original four engine objects. Existing native death/corpse inventory/reanimation
checks also passed in that run. These are stationary-threat fixtures, not a live chase
or an integrated combat-to-death proof. Lowering geometry capture reduced batch work
p95 from 5.1 to 3.4 ms; do not raise crowd admission yet. Native evidence:
`artifacts/civilian-headless/20260928-162214-56760b/ipc/native-report.json`.

The final mixed weapon/unarmed run also passed all 13 encounters; its p95 was 4.6 ms.
Evidence: `artifacts/civilian-headless/20260928-162551-451fee/ipc/native-report.json`.
Do not report the earlier 3.4 ms as the latest measurement. The final source additionally
preserves unknown geometry as unknown, retains stationary replicas during the viewing
hold, and excludes observer waiting time from the fixture timeout. Java unit/integration,
guarded class loading, and 256 project tests pass; new audio idempotency tests cover
repeated/stale actions. These final small guards have automated/startup verification;
the watched defense/escape subsequently passed and was accepted by the user.

Fresh watched world: `AKR_DayOne_Test_20260928_163023_07ce36`, at `.132:16281`.
Original Lua modules installed locally while the game was closed. Start evidence:
`artifacts/survival-watched-start.log`; never reuse the prior failed-cleanup world.

Watched result: the user confirmed “Test succeeded”. Epoch
`54689636-7cb3-43b0-95a0-f9091fa0da72` reached phase 6,
`passed_visual_pending`: one native contact, escape over seven tiles, and verified
cleanup with four warm bodies. Human acceptance is recorded separately in
`artifacts/scenario-tests/20260928-163023-07ce36/survival-human-acceptance.json`;
the machine report is preserved unchanged. Client console is archived alongside it.
This accepts the single-client stationary-threat fixture, not moving pursuit or
incoming bites. After completion and observer relocation, MOWoodenWalFrame logged
missing IsoThumpable objects at distant floor-one coordinates; do not claim globally
error-free server logs.

Details and remaining gates: [[../Design/Civilian Defense and Death]]. The watched
entry point is `./dayone civilian-combat survival-create/start/join/status/stop`
(one subcommand per invocation). Pinned 42.20.4 ordinary clients remain required.


## Watched melee contact accepted; pinned client restored — 2026-09-28

The user confirmed **correct civilian attack animations, audible hammer swing and
impact, and success** after the final ordinary-client test. This qualifies the
narrow watched hammer-contact slice, not autonomous DEFEND or two-client combat.

- Test world `AKR_DayOne_Test_20260928_152217_49a655`; final epoch
  `04deaa25-9325-4d6b-8050-822b871d3a73`.
- One native contact and one stock PlayerHitZombie packet; health 1.705 ->
  1.5226494. Client logged `HammerHit` and `HammerSwing` with nonzero handles.
  135 Actor samples, zero new client errors during the observed test. Added work
  p95 <=0.1 ms; max 88.1 ms includes cold initialization.
- Automatic protected/admin observer placement, noon/clear weather, chat countdown
  and viewing hold ran. The user quit during the final hold: the automated report
  correctly records `observer_disconnected`, and cleanup was not certified for
  this last run. Owned resources were retained; the empty disposable server was
  stopped gracefully. Preserve this world; use a fresh world for the next test.
  The preceding clean run completed full Actor/zombie cleanup successfully.
- Evidence: `artifacts/scenario-tests/20260928-152217-49a655/` contains
  `combat-r4-human-acceptance.json`, `combat-r4-client-console.txt` and
  `ipc/combat-report.json`. Human acceptance does not overwrite the automated
  cleanup failure. Post-disconnect health on a native recycled zombie reference
  is not valid damage evidence; use the recorded before/after contact values.

The first run produced stance without swing. Preparing the weapon's stock attack
type and calculated combat speed fixed it. An attempted Lua animation-track sampler
was not exposed and caused error-counter spam; it was removed. Only the supported
`AttackAnim` flag is reported now, never equated to human visual confirmation.
Audio uses bounded, once-per-probe Lua presentation on stock `OnWeaponSwing` and
`OnWeaponHitCharacter` callbacks, exact configured Actor/target, stock weapon sound
names and Body material. No damage changes, duplicate network sounds or client
Java injection. Client sound-call order is tied to packet callbacks; full
animation-timed combat execution remains a separate gate.

Per the user's request, akr gets a loaded 9mm pistol (15-round magazine + chambered
round), two loaded spare magazines and a 9mm ammo box once per disposable test-server
epoch. Tagged equipment is reused/refilled rather than continually duplicated.
The client and server both confirmed the loadout. Java compatibility/bytecode
fixtures and 25 Lua/tooling tests passed.

Steam had upgraded the local client to 42.21 before the first join. Restored official
Linux depot 108603 / 6267392422221692966 (42.20.4, build 24909800) project-locally.
Its game JAR SHA-256 matches the server exactly:
`80e405a4bfc42f6072e75b3735f458a6514143da011d3226007ded305a442f44`.
`scripts/launch-pinned-client` and application-menu entry **Project Zomboid 42.20.4
(DayOne)** use the installed Steam Linux runtime, with hash verification. Watched
joins now use this pinned copy; ordinary Steam Play still targets its separately
managed latest version. Saves/mods were preserved. See [[../Runbooks/Operations]].

## Civilian defense/death implementation checkpoint — 2026-09-28

Bounded C++ combat policy, matching Lua escape/defense fallback, and a 32-slot native
pool default are implemented. An isolated headless server passed native infected
death, corpse/inventory transfer, fast reanimation before Actor release,
same-object Actor reuse, and 32
incremental off-world prewarms. The final short batch measured a 147.1 ms cold construction
p95; prewarm must happen before gameplay admission. Detached Lua, C++, and Java
checks passed. See [[../Design/Civilian Defense and Death]] and
`artifacts/civilian-combat-probe-7.log`.

The opt-in zero-client probe directly invoked stock combat and reduced zombie
health, then retired the Actor cleanly. The timed attack executor, animation and
hit replication, and generalized incoming-contact bridge remain open. The pinned
engine's animation callback skips connectionless Actors, so `DEFEND` is still a
policy result in the live path. No new ordinary-client or two-client
combat qualification was performed. The 64/128 chase failure below still stands;
this isolated death fixture does not resolve that stress gate. No live server or
production deployment was changed for this checkpoint.

## Latest result — actual 64/128 run failed on contact, 2026-09-28

The corrected full-size southbound batch ran in `20260928-114603-53af81`.
All 64 Actor replicas and 128 native zombie replicas acknowledged; 128 native owners and
128 targets, but only 56 zombies had actually moved >=5 net tiles by failure. After ~38.1s
and ~50.6 route tiles, zombie 26192 reached lunge/attack and triggered the contact guard.
No wave completed; no four-wave reuse/cleanup or multiplayer qualification at this load.
The disposable server is stopped gracefully, evidence/world retained. User did see the
full group, but no visual acceptance is inferred from screenshots alone.

Measured movement windows averaged **52.47 render FPS**, worst-window p95/p99 <=34/48ms,
maximum frame interval323ms; material client stutter. Server movement CPU ~23.91% of one
core, TX43.56kB/s; peak cgroup4.318GB. Whole-harness work p95/p99 1.5/2.8ms, warm assignment
max4.957ms. Exact scope and caveats: [retained report](../../artifacts/scenario-tests/20260928-114603-53af81/report.md).

Next work: replace median-gap speed control with closest-pursuer/closing-speed protection
including replication margin; diagnose the many nonmoving hunters separately from target
assignment; keep native probabilistic spotting/contact and strict cleanup. Original
64/128 four-wave scope/direction remains accepted; no more unlabeled one-pair substitutes.

## Corrected next batch — 64 civilians / 128 pursuers, 2026-09-28

The one-pair cleanup repeat `20260928-111646-179a6a` used a reverse candidate and
was correctly rejected by the user as the wrong test. It failed/retained on disconnect;
no cleanup pass is claimed. No further single-pair repeats are planned.

The authorized stress batch is four southbound (+Y) 150-tile passes, each with 64
prewarmed/reused Actors in an 8x8 grid and 128 native fast shamblers, two targets per
Actor. Native spotting probability is preserved; at least 116/128 must actually pursue.
All 128 remain cleanup-owned regardless of acquisition. Client reports are bounded,
wave-scoped, and distinguish unseen/absent/unknown. Staging requires fresh all-observer
hidden reports and a loaded client footprint. A scoped 600ms native-manager ownership
lease covers only admitted zombies and expires on stale observation/disconnect/movement.
There is no custom zombie transform stream or client Java agent.

Unit/components passed: `20260928-113226-973c2fdc`; pinned engine integration passed:
`20260928-113308-c1c03bf0`. First full staging attempt `20260928-113326-67e8d9` stopped after 11 assignments
when its visibility admission became unavailable; no chase or hunters ran. Added explicit
pre-ownership deferral without freeing reservations or weakening visibility checks. Unit
checks (248 Python plus Java fixtures) passed in `20260928-113808-92ee4bad`, integration
in `20260928-113854-7be95adf`. Retry `20260928-113923-1cbffd` staged all 64/128 but did not start movement, waiting
for replica acknowledgements. Interrupted to add stationary Actor replication during
readiness and stop reapplying birth-visibility admission after all bodies exist. New
run `20260928-114603-53af81` reached movement then failed (see latest result); integration `20260928-114538-36b7917a` passed.
No chase/reuse pass is claimed yet. Observer setup uses the open roadside and checks LOS/egress, keeps protection,
and returns the observer before each wave. Default population remains four.

## Offscreen staging implemented; natural chase entrance confirmed, 2026-09-28

User confirmed **"Yes—natural entrance and chase"** for
`artifacts/scenario-tests/20260928-110457-25ff6f/`. Pursuit began hidden, both entities
subsequently became actually visible, and the observer stayed at the verified open
roadside (0.204-tile displacement). Four prewarmed bodies, one resident registry binding,
67.038 tiles of native zombie movement, no construction during assignment.

The user exited before offscreen retirement; the run correctly retained resources with
`observer_disconnected_owned`. This is a staging/reveal pass, **not a cleanup pass**.
The later single-pair repeat was interrupted too; see the corrected stress batch above. Harness p95/p99 work was 0.2/0.8 ms; cold
initialization maximum 138.195 ms occurred before client admission.

Implementation: `OffscreenAdmission`, scoped `NativeCivilianActors.StagingPolicy`,
`OffscreenChaseHarness`, bounded ordinary Lua loading/visibility/replica reports,
resident registry initialization, native ownership/targeting, and `./dayone civilian-chase`.
The default near-player admission rule is preserved. New tests cover multi-observer veto,
staleness, revision mismatch, displacement, missing loaded owner, visibility versus viewport,
unknown/absent bodies, telemetry bounds, and the corrected Residents dependency manifest.
Unit/component checks: `20260928-110445-9ea13f7b`; integration: `20260928-110202-ad660f8d`.
No ambient auto-activation; this is one directed road encounter. Handoff, two-client and
64+16 chase lifecycle gates remain pending. See [[../Design/Offscreen Chase Staging]].

## Latest direction — offscreen chase staging; 64 chase batch not passed

See [[../Design/Offscreen Chase Staging]]. The retry `20260928-103932-bb3eea`
failed before retirement: all 16 hunters acquired targets, but minimum travel was 3.436
rather than five tiles. The user reports (and screenshot confirms) the near-start viewer
point was inside a walled school playground. Reject that coordinate and the projection-only
visibility claim. The next experiment must verify loaded client chunks, actual visibility,
native ownership and pursuit **before** revealing the encounter, starting with one pair.
Actor prewarming/reuse exists; ambient staging, civilian-table binding, observation leases
and LOS qualification are not yet implemented. Do not rerun the failed viewpoint unchanged.

## In progress — 64 civilians with 16 native pursuers, 2026-09-28

User authorized four 8×8 waves, medium-speed zombie pursuit, and a protected observer.
The disposable harness now repositions `akr` automatically before **every** wave,
verifies admin/god/invisible/ghost flags, and moves the observer away before cleanup.
No coordinate navigation is required. Sixteen stock fast shamblers are created one per
tick per wave, controlled through native ownership and ZombieControl targeting. Scoped
ordinary-client perception refreshes only the configured zombies' actual Actor targets.
Target acquisition, movement and visibility are required; spawned zombies alone do not
qualify pursuit. This is pursuit-only: contact/death stops the test and retains ownership.
Default four-Actor behavior/path budgets remain unchanged; no ambient modules activated.

Unit/component checks passed at `artifacts/runtime-tests/20260928-102907-ec9e7da9/`;
engine integration passed at `artifacts/runtime-tests/20260928-103233-3602722b/`.
The new disposable run is `artifacts/scenario-tests/20260928-103315-ffb1ba/`.
First 64 attempt completed one civilian pass but failed `hunter_chase_not_observed`:
the start was outside the midpoint observer's native zombie relevance; owners were acquired
only when the observer approached later, too late for nearby target perception. No cleanup
was pretended. Full failure evidence is in that run's report. The disposable server was
stopped gracefully. The retry positions the protected observer beside the starting group
**after** off-screen materialization and waits for native ownership before movement.
Retry: `artifacts/scenario-tests/20260928-103932-bb3eea/`; unit/integration checks passed in
`20260928-103900-d15246cc` and `20260928-103931-340a57fe`. Pursuit and reuse remain pending.

## Latest checkpoint — 32-Actor 8×4 stress batch passed, 2026-09-28

Four waves of 32 civilians completed the same 150-tile routes in one ordinary-client
session. **32 engine objects, 128 resident assignments, 128 saved outgoing bodies,
19,200 actor-tiles**, no hot constructors and zero retained native bodies after cleanup.
Client presence/appearance and absence-before-reuse passed for all 32 in every wave.
Captured screenshots show the formation. Native server chat announced each wave and
completion. The disposable server was left running; normal .132 and production .160
were untouched. This is an opt-in stress harness; default Actor capacity remains four.

Full evidence and analysis: `artifacts/scenario-tests/20260928-100710-9f7bda/report.md`.
Agent SHA-256 `81bc90bc357d1310be5a6ff201c8227db4728f5aa49230969d3cbc1e20cfd483`.
Per-wave harness work p95 **0.4–0.7 ms**, p99 **0.7–1.1 ms** (now includes report I/O).
Warm assignment max **2.026 ms**, p95 histogram upper bound 1.0 ms. Cold initialization
max **161.964 ms**, before clients joined. Assignment/retirement/parking are staggered.

Client render callbacks averaged **143.92 FPS marching / 143.23 FPS during admission**;
interval p95/p99 ≤10/≤11 ms, marching maximum 27 ms. These include diagnostics and the
off-screen approach/departure, not isolated GPU cost. Container delta CPU during movement
averaged 6.26% of one core (peak sample 16.33%); sampled memory peaked at **4.201 GB**
cgroup / **2.521 GB** Java heap. Cgroup includes game/cache, not just Actors. Transmit
traffic averaged 13.96 kB/s; 42,688 native Actor update packets over the run.

Zero client exceptions/new errors, zero per-frame >1-tile corrections, all 314 fear
samples zero. **Position accuracy remains a failed gate:** 29,416 same-host aligned pairs
measured p95 4.279 tiles, p99 4.467, max 5.579, without lag compensation. Smoothness and
pool viability are separate from correct contact/replication. This does not qualify 32
AI/perception/native-path jobs or two-client agreement. No normal population increase.
The user's audio correction stands: footsteps were audible; the earlier missing-audio
finding is superseded. No custom sound workaround was added.

Implementation adds bounded opt-in 32-slot native/pool capacity, tested 8×4 geometry,
server-configured 4/32 telemetry with count/order/epoch validation, fixed-size render
interval histograms and per-phase/container measurements. Unit/component checks passed
in `artifacts/runtime-tests/20260928-100615-0f6a7c34/` (241 Python tests plus Java fixtures),
engine integration in `artifacts/runtime-tests/20260928-100709-e1890f58/`. Added tests for
32-slot overflow, one assignment per tick, all 32 retirement receipts, formation distances
and malformed/stale client telemetry. Next: prediction timing, then representative native
behavior/path workloads and paired-client validation; do not infer full crowd capacity.

## Follow-up — audio confirmed; 32-Actor stress test authorized

The user corrected the earlier audio report: footsteps **were audible** in the successful
four-wave test. Treat missing footsteps below as superseded user feedback, not a confirmed
open defect. Position accuracy and two-client qualification remain open. The user now
authorizes 32 simultaneous civilians in an 8-column × 4-row formation, repeated over
four waves to measure costs and viability. This is an opt-in disposable stress experiment;
normal pool/controller budgets remain four and ambient population stays disabled.

## Latest checkpoint — four watched reuse waves passed, 2026-09-28

The user confirmed **"Test successful"** after four simultaneous NPCs ran the same
150-tile paths four times in one ordinary-client session. Exactly **four prewarmed
engine objects** served **16 different resident assignments**, with identity/profile
changes between waves, no assignment-time constructors, 16 outgoing body snapshots,
confirmed client absence before reuse, and zero active/parked/retained bodies after
cleanup. All four object identities are stable across the four waves. The user previously
confirmed changing identities and reuse but reported **missing local footstep audio**;
that remains open and was not fixed by the test changes.

Evidence: `artifacts/scenario-tests/20260928-094756-a2fa59/`, especially
`ipc/watched-report.json`, `ipc/watched-samples.jsonl`, `analysis.json`, source/client
hash receipts, copied agent manifest, redacted logs and wave screenshots. Agent SHA-256:
`a41ee04b02fb6e91e9618c8bd5134b6dfc182ecdf8661aacc5a58e4015567289`.
The final report's wave counter is 5 because it advances after completion; its completed
wave records are correctly 1–4. The disposable server was left running; no .160 or normal
.132 deployment was changed. Local AKRCore/AKRDevTools were backed up and installed for
this explicitly authorized test. There is no client Java agent.

Each movement pass took about **57.6 seconds** (2.6 tiles/s) and each full wave about
66.0–66.2 seconds including retirement. Warm assignment maximum **1.655 ms**, p95
histogram upper bound 1.7 ms. Per-wave harness work p95/p99 upper bounds were
0.3/0.4, 0.2/0.3, 0.2/0.2 and 0.1/0.2 ms. Cold prewarm maximum **106.4 ms** remains
visible; these are harness timings, not whole-server or client frame-time measurements.

This is a lifecycle/reuse pass, **not replication/performance release qualification**.
Same-host wall-clock interpolation of 3,692 client/server pairs, with no time-shift
compensation, measured position error p95 **4.455 tiles**, p99 4.664, max 7.199.
That fails the ≤1-tile target. Four per-frame >1-tile corrections occurred together near
wave 1's departure/finish; their on-screen visibility was not recorded, so do not label
them confirmed visible skips. No client ERROR/Exception lines occurred after the first
Actor sample. All 292 fear samples had zero visible zombies, panic and stress. Ordinary
startup asset warnings and server `turning180` animation warnings remain. Two-client
agreement, audio, complete traversal and combat are still open.

Implemented `./dayone civilian-watch create/start/join/status/stop` and the opt-in guarded
`WatchedCivilianHarness`, plus bounded four-Actor client visibility telemetry. Each wave,
finish, off-screen wait, confirmed retirement and final pass/failure now uses the game's
native server-alert chat API (inspection: `artifacts/decompiled/watched-messages/`).
Between waves the observer returns to the viewing point and a 12-second admission stage
rechecks fresh telemetry and clearance. `join` waits for warmup before launching the client.
Materialization failures now retain their specific reason; the harness inspects unresolved
slots before returning for a missing body, so it cannot silently wait until timeout.

Previous evidence is preserved: the overnight run `20260927-220044-d3a555` failed on
observer disconnect in wave 1; retry `20260928-093617-aa2b0b` completed two waves but
stalled before assigning wave 3. It was stopped for diagnosis. The old harness hid the
pre-body failure reason, so its exact original refusal remains unproven; do not retrospectively
claim a confirmed reset bug or blame observer movement. Improved admission/diagnostics
were followed by the complete four-wave pass above.

Validation: Python components (240 tests) and Java unit/vehicle regression fixtures passed
in `artifacts/runtime-tests/20260928-094725-9aee591d/`; exact-engine integration checks
passed in `artifacts/runtime-tests/20260928-094755-f93f1359/`. Added a regression case
for a materialization refusal before body ownership, retaining capacity and the refusal
reason. Next work: native remote-player sound/prediction audit, then measured paired-client
replication. Idle/roam/flee controller integration and full traversal remain separate gates.

## Latest checkpoint — cross-resident initialized pooling, 2026-09-27

The user reiterated that general Actor reuse was the point. **The resident-affine cache
is superseded.** The native adapter now prewarms exactly four initialized `IsoPlayer`
objects, keeps them in a reusable FIFO, and applies an explicit reset/load boundary when
assigning any new or saved resident. `materialize` has no constructor fallback. Four
constructors occurred during warmup; **15 subsequent assignments reused those objects**.
No normal gameplay session or client installation was changed.

`NativeActorReset` clears stock-load omissions (hands/equipment, ModData, appended history
collections and fitness state) and transient locomotion/action/path/network state before
loading the incoming snapshot. New residents start from a pristine per-body snapshot and
receive their own descriptor/outfit. Busy, dead, burning, vehicle, grapple/climb/attack,
downed or ragdoll bodies cannot be reset into another person. Failures remain owned.
Exact old descriptor pointers/registry entries and delayed emitter callbacks are detached.
Source inspection is retained under `artifacts/decompiled/actor-reset/`; newly inspected
reset dependencies have explicit BuildGuard hashes. See [[../Design/Civilian Pool and Navigation]].

Native report: `artifacts/civilian-headless/20260927-214700-fb96f8/ipc/native-report.json`.
Agent SHA-256 `75adb7f4052101e01d278d84b8b2de8252ed22680bf1e772d1515ea97a822571`.
World `AKR_DayOne_Test_Headless_20260927_214700_fb96f8`, no clients. The batch dirtied four
male residents, reassigned the same four objects to fresh female residents, then restored
the originals on **different** physical objects. Assertions passed for no leaked inventory,
hands, wounds, recipes/books/literature/fitness, ModData or posture; incoming sex, original
saved state, stale-token rejection, movement, busy-reset refusal and descriptor cleanup also
passed. Existing native path/cancel/body/door tests passed. Final retained/parked/active
counts were zero; the isolated server stopped gracefully.

Warm assignment max **5.895462 ms** (15 assignments), p95 upper bound 5.9 ms. Warmup cold
p95 upper bound 138.65 ms. Total harness p95/p99 upper bounds 2.4/145.17 ms include warmup;
walking was 0.3/2.3 ms. This meets the no-hot-constructor requirement, not the full performance
target. Reset/outfit/restoration still cost time; the former same-resident-cache timings do
not measure this stronger operation. Earlier incremental runs `20260927-214351-f2e7a4`
and `20260927-214545-89cd6c` passed before the final descriptor-cleanup assertions.

240 Python tests and Java unit/vehicle regressions passed:
`artifacts/runtime-tests/20260927-214743-40e88be1/report.md`. Engine compatibility/hook/premain
checks passed: `artifacts/runtime-tests/20260927-214812-2834cf16/report.md`.
Remaining work: native idle/roam/flee controller integration, runtime warmup/admission wiring,
full traversal, richer combat/lifecycle reset coverage, and visual/two-client reuse. This is
headless qualification of the implemented ambient Actor boundary, not every arbitrary player
or third-party mod state. The reopened combat gate remains separate.

## Superseded resident-affine cache experiment — 2026-09-27

The user clarified that pooling must retain initialized engine objects, not merely reuse
online IDs. Added a four-body resident-affine cache in `NativeCivilianActors`. It parks
verified detached bodies, checks identity/profile/snapshot and a serialized fingerprint
before reuse, replaces assignment/replication bindings, and evicts only verified parked
references when capacity is needed. Cross-resident reset is not implemented: inspected
stock load paths append some collections and leave some empty equipment references intact.
See [[../Design/Civilian Pool and Navigation]] for the restricted contract and provenance.

The extended headless batch passed **17 checks**, including eight warm reactivations of
the exact same four Java objects, retained item IDs, stale-token rejection and walking.
Existing stock save/load and door cases also passed. Final parked/active/owned counts were
zero and the isolated server stopped gracefully. No interactive/normal/production session
or client install was changed. Final report:
`artifacts/civilian-headless/20260927-213421-fd9522/ipc/native-report.json`.
Agent SHA-256 `0527111a72c87229a0c917f059cf9946681c5da6bca137b90a3c5ee5912bbd69`.
Private inspection: `artifacts/decompiled/actor-reuse/`. It also exposed the stock delayed
emitter cleanup; parking finishes that cleanup for the owned body so it cannot stop its
sounds after reuse. Existing IsoPlayer hash guards cover the reflection dependency.

Warm activation maximum **1.246405 ms** (8 samples), p95 histogram upper bound 1.3 ms;
cold p95 upper bound 130.64 ms. Seven constructors include the separate stock restoration
and door fixtures; the four-body reuse rounds constructed only four objects. Total harness
p95/p99 upper bounds 5.4/132.74 ms, walking 0.5/1.7 ms. Release performance still fails its
target; startup prewarming and cache-miss scheduling are not implemented. The earlier warm
run `20260927-213253-fdda0a` passed before the emitter cleanup fix, with warm max 1.67 ms.
Both are short functional runs, not statistical capacity/soak qualification.

Java unit/vehicle regressions ran before each native batch; pinned-engine integration passed
in `artifacts/runtime-tests/20260927-213438-68ee4148/report.md`. Visual/two-client reuse,
full traversal, integrated idle/roam/flee, reopened combat gate 3 and general resident-to-body
reset remain pending. Next priorities: explicit warmup/admission policy and reset feasibility,
then connect the existing controller to native pool/path/perception ports and exercise
roam → visible-threat flee → blocked/trapped → recovery headlessly before the watched batch.

## Previous checkpoint — native headless civilian batch, 2026-09-27

The user clarified that native functional work can run headlessly and authorized it.
Implemented `./dayone civilian-headless [run|status|stop]`: builds/tests the agent, starts
an isolated dedicated server with no clients, runs the cases in one process, retains
reports/logs/manifests and stops gracefully. It never uses the interactive test receipt.
No existing game session, client install, normal `.132` world or `.160` was changed.

**15 native checks passed**, including neighborhood loading without players, one Actor,
existing native pathfinder callback and collision-constrained walking, in-flight request
cancellation, stock body save/remove/load, three four-Actor reuse rounds with overflow
refusal, closed-door collision, locked-door refusal, stock door opening/crossing and
verified final removal. Body checks cover a scratch, item IDs/condition, equipped hammer,
nested bag/pen and clothing count; they do not prove every serialized field or appearance.

Evidence: `artifacts/civilian-headless/20260927-210440-841d18/ipc/native-report.json`.
Sibling `receipt.json`, `agent/manifest.json` and `container.log` retain identity and boot/
shutdown evidence. World `AKR_DayOne_Test_Headless_20260927_210440_841d18`, epoch
`239288f0-577c-4368-ac55-22a5783c0bb5`, agent SHA-256
`cb5f43287b0bf4bf1a1dd05c1de3dc6304454af4eb8ba1d0666887c7a13375fb`.
The isolated container `akr-civilian-headless` is stopped. Game UDP 16291/16292 was not
published; only loopback RCON 27045 was published. No players connected; final occupied=0.

Detached regression suites also passed: **240 Python tests** and Java unit fixtures
(`artifacts/runtime-tests/20260927-210730-d2655876/report.md`), then pinned-engine
integration/premain/hook-site checks
(`artifacts/runtime-tests/20260927-210934-673fc2e8/report.md`). The older report footer
says native work is pending generically; the separate native report above supersedes that
statement for its covered cases. The runner now points to separate headless evidence.
Boot logs still include engine asset/property diagnostics; a native case pass does not
claim an error-free game boot.

**Performance is not qualified.** Short-run histogram estimates: walking p95 ≤0.6 ms,
p99 ≤1.5 ms; total harness p95 ≤4.8 ms, p99 ≤156.86 ms; materialization p95 ≤149.81 ms.
Overflow histogram buckets report the observed maximum, so these are upper bounds, not
precise quantiles. Creation spikes exceed the game-thread target. These measurements
exclude asynchronous solver work and cannot establish release-cap capacity.

Full native stairs/climbing, dynamic obstacles/replanning, integrated idle/roam/flee,
visual/two-client replication and client-owned hunter checks remain pending. Gate 3 stays
reopened. The dedicated harness invokes native components directly; it does not complete
the Lua controller/session bridge or add an ambient gameplay spawn loop. See
[[../Runbooks/Civilian Qualification Batch]] for coverage and next gates.

## Prior source checkpoint — offline civilian foundation, 2026-09-27

The user requested abstract testing now and a later native functional/visual batch.
Implemented four-slot `CivilianPool`, `AKRPopulation` and `AKRResidents` identity/FSM/controller,
bounded layered A*, escape selection, native request admission, fair local perception,
action sequencing, and compiled native pool/stock body codec/path/geometry/LOS/walk/door ports.
Source details and exact limits: [[../Design/Civilian Pool and Navigation]].

Validation: `./dayone runtime-test unit` passed **237 Python tests** and Java fixtures,
including 2,000 pool reuse cycles and retained vehicle regressions. Report:
`artifacts/runtime-tests/20260927-204454-8de22b1b/report.md`.
`./dayone runtime-test integration` passed pinned build, native pooled-path copying,
Actor hook-site/JVM checks, original driver import and premain fixtures. Report:
`artifacts/runtime-tests/20260927-204517-ec601c1b/report.md`.
Built (not deployed) agent SHA-256:
`a2cded325e185840b19ac675938e77b9ea180a04567e249f190a7c2ea25cfb4a`.

No game container, mod list, local client install or live world was operated/changed.
No current live-state claim was refreshed; deployment statements further down are historical.
The existing socket still runs its legacy one-Actor case. New modules register APIs only;
live session-driver wiring and scheduler-integrated multi-Actor submission remain pending.

**Full traversal is incomplete.** Doors/walking have candidate adapters. Stairs, windows,
low fences and high walls have path/action contracts and offline tests, but require the
scoped native state/animation/placement/outcome executor. High-wall outcome branches assume
a local player; stock remote Actor updates do not advance these actions correctly. Do not
substitute teleports or describe them as working. Native body round-trip, four Actors and
two-client validation remain open; combat gate 3 remains reopened, and corpse/reanimation
work is separate. The prepared next sequence is [[../Runbooks/Civilian Qualification Batch]].

## Historical checkpoints

The sections below preserve earlier implementation/deployment evidence; they are not a
single current deployment description. The latest source checkpoint above takes precedence.

## Civilian entity decision — 2026-09-27

The user required that zombies natively target healthy NPCs. Disguised `IsoZombie`
civilians cannot meet that requirement through stock engine paths, so their presentation
fixes were not implemented. The proposed replacement is server-hosted connectionless
`IsoPlayer` civilians, with bounded server-simulated zombies engaging them. It is
source-analyzed only, with four spike gates in [[Decisions/0006 Targetable Civilians]].
No code or deployment changed. The disposable server is still running epoch
`6d9c5a22-e65e-43ed-884c-631561fb13a6`, deployed JAR `82b947f8…0fbb`, with no live NPC.
The one-NPC walker results stay valid evidence for server-owned zombie simulation.
The user accepted the Actor-pool design: off-engine civilian state, with pooled server
`IsoPlayer` Actors as the engine replicator. Gate 1 (Actor pool v0) passed its server and
replication checks with one ordinary client: two completed events in one process, a
reassigned identity on the same slot, verified release, and no errors. The user's audio and
moodle observation is pending. The running disposable server uses agent
`0312d859…ed53`, epoch `54d9ed5e-3444-46ac-987a-af268bdc5c1a`, with no live Actor.
`./dayone pedestrian-test-join` rejoins the local client unattended. Gate 2 (walking)
passed with one client: agent `cd7a5ae7…9ec0`, epoch `640c9ef4-44f4-44bd-9257-7c78f7c2aa96`,
aligned p95 0.35 tiles, 0 skips, zero fear counts. Gate 3 core passed with one client. A stock server-simulated sprinter spotted, chased
and bit a fleeing, tripped Actor (health 100 → 99.05, one scratch), with the stock hit and
state packets relayed to the client (ledger 2026-09-27). Running agent `bb5d5e38…60f6`,
epoch `a385d259-fc69-4df4-838f-cc88e4a1a520`; no Actor or hunter is live and the client is
closed. Gate 3 passes with client-owned hunters (user decision). The stock owner client ran the
chase with no teleports; the Actor tripped visibly; the server applied the stock bite from
the owner's replicated attack and relayed the reaction (ledger). Agent `4d53bb8b…9253`,
epoch `3c07bbb6-1905-45f3-b2e7-1391f93adf56`, client closed. Next: graceful dematerialize on
chunk unload, a second observer client, and the resident state table.

## Pedestrian-first checkpoint — 2026-09-27

Latest retry: `AKR_DayOne_Test_20260927_141446_320776`, same container/ports. The first
watched submission in the preceding world stopped at `spawn_not_authorized` before native
creation because the legacy bridge expected a sandbox flag supplied by LofersStoryteller.
The guard now accepts the explicitly enabled disposable pedestrian runtime. Confirmed
pre-creation refusals release reservations; ambiguous failures still retain them. The old
world is preserved and its test character database was copied into the new disposable world.
Deployed JAR SHA256: `d7356a6053b6485c7ec68932cc94f6fd3f95db06ec37e0921365266a62d3c4f2`.

The native spawn-permit preflight now passes during adapter initialization. A narrow idle Lua
refresh was applied without restarting that new epoch; evidence is
`artifacts/scenario-tests/20260927-141446-320776/first-lua-reload.json`. The server Lua controller
explicitly advances the native pathfinder; no synthetic movement vector is supplied.
Its timing is separate from the Java hook. The user has been asked to reconnect for retry.
The following earlier checkpoint describes the first bootstrap and remains historical.


The accepted priority is now [[Design/NPC First Slice]], superseding the full-runtime-first
and opposing-car order below. Implemented an initial pedestrian definition, shared scheduler
entry points (private Protobuf Unix socket and server Lua), bounded queues, cancellation,
resource accounting, narrow server ownership/stock-stream hooks and AKRDevTools presentation.
AKRCore is adopted by this experiment. Population/Residents gameplay separation and crowd
behavior remain after the native feasibility gate, not implemented by this checkpoint.

`./dayone runtime-test offline` passed (216 Python tests, Java unit/integration/launcher,
C++ core/wire) in `artifacts/runtime-tests/20260927-140257-ea8a1bf2/`. A subsequent Kahlua
startup failure exposed missing `next()`; it was replaced with bounded iteration and the
Lua regression fixture now removes `next` (12 focused tests pass). CLI dispatch now uses
project Python and handles long project socket paths through a directory descriptor.

Current disposable world: `AKR_DayOne_Test_20260927_140310_3dd59f`, container
`lofers-scenario-test`, game `.132:16281/16282`, loopback RCON `27035`. Receipt:
`artifacts/scenario-tests/current.json`. Previous vehicle world stopped gracefully and is
retained unchanged in its prior directory. Normal `.132` and `.160` were not operated.
The original AKRCore/AKRDevTools mods were installed under the local client's `Zomboid/mods`;
checksum receipt: `artifacts/scenario-agent/pedestrian-client-install.json`.

The corrected boot registered the Lua adapter without the prior initialization error.
Current epoch: `75d6570e-3e8e-4655-9c7e-ebe1b91cd5ca`; deployed private JAR SHA256:
`43a71b42ae81f3acf85c50fc7bfa37a28fd14de83fe54b2a8794827f7891f1ea`.
Empty-server submit/status succeeded: event `.1` failed with `observer_required`, reached a
terminal state and retained no resources. Evidence:
`artifacts/scenario-tests/20260927-140310-3dd59f/empty-server-api-check.json`.
Startup log: `artifacts/scenario-agent/pedestrian-startup.log`. This checks real command
execution/guarding, not a native pedestrian. The operator has asked the user to join as `akr`.

Follow [[Runbooks/Pedestrian Experiment]]. No actor has been demonstrated moving on the
server. Ordinary client observation and two-client replication are pending. Dead/corpse
cleanup is deliberately unresolved, diagnostic samples lack qualified clock alignment, and
generic behavior reload/durable runtime recovery/batches remain unimplemented. Do not promote
this experiment to campaign capability or proceed to crowd scaling until the gate passes.

The older runtime and car snapshots below are retained historical context, not the active
priority or current deployment.


## Latest implementation checkpoint — 2026-09-27

The layered offline command is now `./dayone runtime-test offline`, with `unit`, `integration`
and `worker` subsets. It retains reports/logs, propagates errors/timeouts, prevents overlapping
runner invocations and explicitly keeps in-game gates pending. First full run passed:
`artifacts/runtime-tests/20260927-125049-bb1ba0ea/` (204 Python tests, Java layers, native core
and worker wire tests). See [[Runbooks/Runtime Testing]]. Existing IDE and `--generate-only`
builder edits were preserved. No event socket/reload implementation or deployment is implied.

Subsequent source integration connects the legacy probe to `EventScheduler`, tracks car,
terrain and passing ownership, and extracts native-world/terrain ownership into
`ResidentPhysics` / `GamePhysicsBackend`. `ScenarioAgent` creates the resident owner only
after the pinned-launcher exemption and server configuration checks. Failed cleanup retains
the car/lease and prevents another event; occupied cars are not forcibly removed. The file
command adapter is now a bounded FIFO with priority stop and explicit full-queue status.

`ResidentPhysicsFixture` exercises one initialization across 100 detached event leases,
competing owners, wrong-thread calls, partial activation/deactivation and initialization
failure. Full Java fixtures pass, including launcher discovery and bytecode guards; see
`artifacts/runtime-resident-agent-tests.log`. These tests do not prove native behavior.

No deployment/restart occurred. Entity/control extraction is not yet complete and routes
remain startup-bound. Next: finish the event adapter and typed submission boundary, then
reload/batch tools before the planned installation restart. The older foundation checkpoint
below describes the preceding slice; the running-server snapshot remains historical.

Read [PLAN_SchedulerAPI.md](../../PLAN_SchedulerAPI.md) first. The first implementation slice
adds `EventScheduler.java`, `EventResources.java` and `EventSchedulerFixture.java` to the
agent. It covers bounded FIFO submission, submission deduplication, stale identity rejection,
capacity-independent cancellation, single-event lifecycle, resource limits and fail-closed
cleanup. It is detached from the game and **not deployed**. Next work is extracting resident
native-world/resource ownership from `ServerVehicleProbe` and connecting the lifecycle.
Socket/Lua APIs, complete event definitions, persistence/recovery, reloads and batches remain.

The missing `ProbeOpposingCar` reference is replaced by explicit rejection of the legacy
opposing option. The source now builds. Full Java fixtures and 191 Python tests passed;
logs are `artifacts/runtime-foundation-agent-tests.log` and
`artifacts/runtime-foundation-python-tests.log`. The 128 detached lifecycle cases are not
native soak evidence. Built JAR SHA256:
`a4acde99feb822267199430113eee3e8b805812fc444d507056442110b082adf`.
No server restart, live event or deployment occurred. The running JAR remains the historical
validated single-car build below. The crash-feedback and Python opposing configuration draft
remain pending integration; the unrelated CMake change is preserved.

The following deployment snapshot and missing-helper discussion describe the earlier handoff,
not the latest source status.

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

### Pedestrian test visibility correction (2026-09-27)

Added a disposable-runtime-only fog admin override to AKRDevTools, checked every
five seconds with weather stopped. Applied live in world
`AKR_DayOne_Test_20260927_141446_320776`; server confirmed fog intensity zero.
Client visibility still requires observation. Seven pedestrian tooling tests pass.
A second `reloadlua Pedestrians.lua` falsely reported success while logging a doubled
absolute-path FileNotFoundException. Applied the callback through the already indexed
Gate module and restored its original on-disk contents immediately afterward. No core
files changed or server restart; reusable reload path handling remains unresolved.

### First clear-weather native pedestrian run — failed walking gate

World `AKR_DayOne_Test_20260927_141446_320776`, epoch
`605b4d8c-aed5-4ed2-a993-08ce619a0fda`, event `.1`, request
`watched-walk-clear-1`. User confirmed clear fog and readiness; one real client online.
Spawn succeeded (online ID 4009), remained server-owned, native path reported found.
At ~4 seconds animation player was absent; later present, but all captured deferred
movement readings remained zero. Last sample at ~30 seconds was (10757.598,9854.800),
2.27 tiles from spawn, rather than reaching eastbound (10776,9856). Cause of the small
wrong-direction displacement is not established. Event FAILED with route_timeout;
terminal reply contains no resource reservations. Client observation is pending.
Do not advance to four actors or claim server walking feasibility. Evidence is
`artifacts/scenario-tests/20260927-141446-320776/watched-walk-clear-1*`.
Hook timing: recorded maximum169.13ms (includes preparation), terminal p95≤0.1ms,
p99≤0.7ms; these exclude other engine work and Lua controller work.

#### Client observation for watched-walk-clear-1

User confirmed the actor appeared, stood idle, and could be physically pushed. It
looked like a zombie and made zombie noises, without ordinary zombie behavior.
Single-client spawning, visibility and physical interaction are partial successes;
autonomous walking and human presentation failed. Pushing is a plausible source
of measured displacement, not evidence of autonomous locomotion. No two-client
replication claim follows. Next isolate native locomotion/state activation and the
managed presentation/identity gate independently; keep the four-actor gate closed.

### Follow-up candidate deployed — native locomotion and civilian presentation

Private agent SHA256 `f209328ec48d2a2c551c407482dac3967a8f7b6bbe6a24d873f6c98933d60ca3`
in the same disposable world, new epoch `6f6a939f-80a2-452d-b970-99308f7b8de8`.
This is a candidate, not a passing walking result. Exact installed engine inspection
found dedicated-server zombie constructors skip variable/event registrations and five
animation-variable setters refuse server zombies. A scoped hook now delegates those
setters to the stock variable store only for actors bound to the pedestrian adapter.
Admission invokes original registrations (listeners once per Java entity). Removed
manual Lua path stepping so the native state machine owns path updates. No global
server flag changes, synthetic movement, client Java injection or core-file edits.

Client companion now preserves its shared gate registry on repeated load, normalizes
outfit keys, checks network identity, and discovers remote actors with an incremental
32-actor/100ms cursor. Cosmetic initialization uses the upstream body helper plus an
original bounded visual wardrobe; avoids ApplyVisuals health/death-loot mutations.
Zombie voices are suppressed, and gate/brain/presentation diagnostics are logged.
Neither the gate registry nor remote callback issue is yet proven as the original
appearance failure; diagnostics distinguish them on the next actual-client run.

31 focused Python/Lua tests passed; full Java unit/integration/bytecode/premain build
passed. Server/client BanditUpdate hashes match the existing pinned manifest.
Local original companion files installed with checksum-checked backup. Client must
restart to load them. Native one-actor rerun and visible civilian verification remain
pending. No four-actor run until walking passes. Research is private under
`artifacts/decompiled/pedestrian-locomotion`; build output is
`artifacts/pedestrian-locomotion-build.log`.

#### User observation for watched-walk-animation-1

The 30-second run in epoch `6f6a939f-80a2-452d-b970-99308f7b8de8`, event `.1`,
FAILED with route_timeout; no displacement, no outstanding reservation. Gate reports
ready and client callback reports presented=1, but user saw a naked zombie-looking
idle actor. Zombie voice suppression passed. Human appearance and walking failed.
Do not equate callback execution with visible presentation success. Evidence:
`watched-walk-animation-1.jsonl` in the current disposable artifact directory.

### Current candidate: bound-actor scheduler admission

Supersedes the failed animation-registration-only run above. JAR SHA256
`4990f6b1be0cdae77128fecad44b8c4a7beac9b33978b0c3ac171b5042ab4d94`.
Same disposable world; one-NPC retry pending after server and client restart.
New private research confirms stock server scheduler excludes all zombie updates;
only our maximum-four bound actors are now explicitly admitted. Sight/sound/wander
zombie goals are gated for those actors. Native update/postupdate timings are reported
as nativeP99ms/nativeMaxMs alongside path diagnostics. Client retries cosmetic repair
when the actual skin/clothing count differs, with actual-state logging. Voice suppression
is user-confirmed; appearance and walking remain failed until a new observed pass.
32 focused tests and Java build checks pass; evidence in
`artifacts/pedestrian-scheduler-build.log`. Client originals updated with backup and
checksum receipt. No changes to production or normal .132 world.

### Latest real-client result — human presentation passed; walk/cleanup blocked

Current epoch `2a08d6c9-fb03-429e-bb18-67b154049f08`, event `.1`, request
`watched-walk-scheduler-1`. User confirmed a brief zombie appearance at spawn, then
human appearance that persisted, with no walking. Client recorded MaleBody01a and
three clothing visuals and two initial cosmetic repairs. Thus stable human
presentation now passes this one-client observation; initial spawn flash remains.
Voice suppression passed the prior run. No two-client validation.

Position remained (10756.96484375,9856.859375) throughout the 30-second route timeout.
Native update/postupdate now execute (p99<=0.4ms, max4.245787ms in final sample), but
action state remains idle, finder notrunning, deferred movement zero. The scheduler
change alone is not a walking solution; do not claim one or advance to four actors.

**Current scheduler remains CLEANUP_BLOCKED**, with the original resource reservation
retained. Actor network ID became -1; a scoped read-only Lua diagnostic found no
remaining AKRPedestrianServer.actors or saved pending entries. This suggests native
retirement ran, but does not prove the Java backend's world/network absence checks
all passed. Repeated Lua retirement cannot currently confirm an already-removed actor.
Do not clear the reservation, submit more actors, or silently restart away this outcome.
Next isolate retirement acknowledgment from absence verification, exclude retiring
actors from the native scheduler, and record which world-list predicate failed.

Private evidence in current artifact directory:
`watched-walk-scheduler-1.jsonl`, `watched-walk-scheduler-1-server.log`,
`watched-walk-scheduler-1-client.log`, `Gate.lua.before-cleanup-diagnostic`.
Diagnostic used a one-time existing-module reload and restored its original file;
no server restart or additional NPC submitted after this failure.

### Cleanup diagnosis and next locomotion fix (2026-09-27)

The retained reservation was a false-positive absence test: stock
IsoMovingObject.removeFromSquare clears current/last/moving membership but the parent
IsoObject.square reference survives; getSquare falls back to it. After the player
left, a bounded operator audit found world/zombie/add/remove lists all empty and the
saved pending record absent. Evidence: `retirement-audit.log`. The old runtime receipt
is preserved as CLEANUP_BLOCKED, not rewritten to success. The server was then stopped
gracefully and restarted with corrected membership-based verification and idempotent
retirement acknowledgment. Retiring actors no longer enter native buckets or packets.

Further source tracing found IsoGameCharacter.CanUsePathfindState returns false on a
dedicated server. pathToAux therefore never initializes bPathfind, while the action
condition's isFalse requires an actual false value (missing does not satisfy it).
A bound-actor-only permission hook is prepared; keep vanilla behavior for every other
character. The new diagnostic fields also distinguish isMoving, bMoving, baseMoving,
alerted/sitting/get-up/client conditions and the next available transition.

User suggested a secondary protobuf channel. Current choice remains stock transform
replication plus ordinary mod messages for semantic state/diagnostics; protobuf for
external server workers. Extra client/server transport cannot repair native locomotion.
A leased client movement executor remains an explicit architectural fallback only;
no silent authority switch or client Java requirement was introduced.

#### Latest deployed revision and readiness

Current epoch `6d9c5a22-e65e-43ed-884c-631561fb13a6`; private JAR
`82b947f8155a5bb39d5d2bf34a52280964356b4500b77d328c252fb2247b0fbb`.
Includes CanUsePathfindState bound-actor permission and the corrected retirement logic.
Native condition regression proves missing bPathfind blocks isFalse while explicit false
passes; full Java checks and 33 focused tests passed. Startup and adapter registration
confirmed. Zero players online at final check; no actor submitted this epoch yet.
Await the pending reconnect question, then use one actor first and retain a full trace.
Confirm cleanup releases resources, then repeat in the same epoch. No new client files
or second protobuf channel needed. Build log: `artifacts/pedestrian-path-state-build.log`.

### 2026-09-27 — First native walking completion; repeat cleanup passed

Epoch `6d9c5a22-e65e-43ed-884c-631561fb13a6`, events `.1` and `.2`, requests
`watched-walk-path-permission-1` and `watched-walk-path-permission-2`, both reached
COMPLETED / route_complete with no retained resources. Second submission succeeded
in the same process after first cleanup; no restart or authority fallback.
First status capture began after completion, so it establishes the terminal receipt
only. Second trace has 18 samples: server-owned actor moved from
(10756.5166,9856.7998) to (10775.7344,9856.5078), 19.22 tiles, entered native
walktoward with nonzero deferred movement, then WAIT/idle with zero movement before
retirement. This is the first measured server locomotion pass.

Second actor's native update/postupdate measured maximum 0.203006ms. Runtime-wide
work maximum was 98.722194ms, p99 2.8ms at completion; this includes earlier work and
is not a population performance acceptance result. First-use spawn/work latency
requires attribution before scaling. Client logs show both replicas with human skin
and three clothing visuals, two initial repairs each, and no new ERROR/exception
entries during these runs (existing startup errors and unrelated object warnings
remain). Human visual confirmation of this walking revision is still pending.
Do not describe smooth client walking or multiplayer validation as passed yet.

Evidence in `artifacts/scenario-tests/20260927-141446-320776/`:
`watched-walk-path-permission-1.jsonl`, `watched-walk-path-permission-2.jsonl`,
`watched-walk-path-permission-2-submit.json`,
`watched-walk-path-permission-server.log`, `watched-walk-path-permission-client.log`.
Next: collect user observation, tune any presentation/speed issue, then four-actor
feasibility test. Two actual clients remain required for multiplayer acceptance.

### 2026-09-27 — Walking observed; presentation failed; Opus handoff

User confirmed walking with human appearance/clothing, but zombie gait, position skips,
and a brief zombie spawn presentation that triggers jumpscare sound and an anxiety-like
moodle. Thus native locomotion/lifecycle passed; smooth civilian presentation and fear
classification failed. No four-actor advancement. No fixes for this latest feedback
were deployed before the user requested delegation to Opus.

Full task, deployment, evidence, constraints and next gates are recorded in
[TASK_Opus_NPC_Presentation_Replication.md](../../TASK_Opus_NPC_Presentation_Replication.md).
New private inspection identifies stock WalkType.fromString("Walk") falling back to WT1;
remote native packets overwrite the client walk type. IsoPlayer.updateLOS counts
IsoZombie instances without a Bandit exclusion and triggers fear/sound itself. These
are concrete source findings; resolving visible jitter and civilian fear under ordinary
Lua clients still requires implementation/validation. Read the handoff before changes.

At handoff the server remains running, epoch6d9c5a22-e65e-43ed-884c-631561fb13a6,
one player akr connected, no new event submitted and no server/client modifications.
