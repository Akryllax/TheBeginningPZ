# 42.21 compatibility auditor and migration

Accepted 2026-10-02. Worktree: `.tooling/worktrees/pz42.21`, branch `pz/42.21.x`.
Source checkpoint: `4a93d8c`; frozen rollback branch `pz/42.20.x` and annotated tag
`pz-42.20.4-baseline-2026-10-02`. Main was fast-forwarded to that checkpoint; all refs
were pushed normally. Root runtime remains 42.20.4. Do not deploy candidate artifacts
there or touch the .160 deployment. No client JVM injection or replacement game files.

## Intended result

Maintain one active game integration (42.21), with a frozen 42.20 rollback. Introduce
reviewed build profiles and subsystem contracts, immutable game snapshots, normalized
method fingerprints, exact hook counts and an incremental code-only regression report.
Snapshots must never refresh runtime guards or approve builds. Keep native/headless,
watched and two-client evidence separate from code-only checks.

## Implemented checkpoint

- Preserved 1,653 built/distribution/manifest files with SHA256 receipts under root
  `backups/pz-42.20.4-baseline-2026-10-02/`; source refs pushed.
- Isolated candidate source/output directories; only immutable downloaded tools and game
  inputs shared. Candidate game files are read-only inputs by convention; no core edits.
- `./dayone compat snapshot --profile pz42.21`, `compare --baseline pz42.20 --candidate
  pz42.21`, and `test --profile pz42.21 [--full]` produce JSON/Markdown reports.
- Class parser normalizes constant-pool references, instruction targets, switch branches
  and exception handlers; debug attributes omitted. Unknown attributes remain explicit
  hashed review inputs. Raw game bytecode stays out of tracked snapshots/source.
- Exact mapped API/access/static checks, native/dependency hashes, class/body diffs,
  conservative content cache, and fresh Java compile/structural/hook/component checks.
- Synthetic CI needs no proprietary input; absence of game inputs is BLOCKED, never PASS.
- First migration edits: shared ShortFlags movement encoding, updated native hit-list
  combat relay and collection guards, persistent-ID factory descriptor, Lua door API.
- Builder validates the exact reviewed game archive before packaging. A shared generated
  profile now supplies both agents; gameplay startup is limited to disposable server worlds.
- Short owned test socket directories replace paths dependent on checkout depth.

## Still required before runtime qualification

1. Complete dependency inventory (reflection and Lua included), subsystem ownership and
   fine-grained test selection. Current mapping is deliberately conservative and partial;
   test success still exits 2 while coverage/review is incomplete. Expand cache self-tests.
2. Generated test-only BuildProfile is implemented for both agents; normal-world approval
   remains blocked. Keep profile edits separate from snapshot capture.
3. Split transformer rules by subsystem. Exact hook counts now run in the transformer,
   before premain accepts its target classes; missing/duplicate tick rejection is tested.
4. Continue behavioral review and watched replication. Native population/lifecycle, scoped
   combat, Observer version/map/moddata bindings and dual-agent startup now have tests;
   actual incoming client-owned pursuit and two-client behavior remain separate gates.
5. JRE library layout and isolated headless launcher are fixed. Finish candidate watched launchers,
   watched ports 16301/16302 and loopback RCON 27055; headless RCON 27065, no published
   game ports. Do not repoint the frozen default launchers.
6. Run Java integration, C++/protobuf, packaging and isolated headless native navigation,
   doors, cancellation/restore, prewarm/reuse and fatal lifecycle, repeatedly in one process.
7. Build candidate server/client dist with matching manifests and ordinary Lua client
   instructions; no game or upstream binaries, credentials or client agent.
8. Prepare watched regression: WALK/RUN and doors, one/four combat, pooling/death/reanimation,
   routines/chase, then repaired vehicle harness. Fresh readiness required, one spectator
   by default, automatic placement/protection/daylight, loaded 9mm, announcements. Failure
   advances only after verified cleanup; uncertain ownership/connection stops the batch.
9. Update skills/runbooks to profiles and actual evidence. Keep known 42.20 divergence,
   unqualified chase and blocked vehicle harness visible; do not lower thresholds.

Release performance targets remain warmed p95 <2 ms and p99 <5 ms added game-thread work,
with cold initialization reported separately. Two clients require explicit invitation.
Neighborhood features, new crowds, hot reload and save migration remain deferred.

## 2026-10-02 second checkpoint evidence

- Java integration/premain/native-library guard passed. Whole-JAR verification and exact
  method-level hook counts now execute before world load.
- Native `run`: 23 cases passed, including repeat reuse, doors, corpse/reanimation and
  32-body prewarm. Native `survival`: 26 cases passed, 13 encounters/contacts, zero occupied
  resources after cleanup. These are separate disposable worlds, no client launched.
- Observer five-mode export suite and Observer-first real premain composition passed.
- Existing immutable worker bundle passed all 16 actual wire scenarios. This does not
  claim a fresh C++ rebuild; its source/ABI is unchanged by the game upgrade.
- Performance remains unqualified: first run walking p95/p99 1.4/3.1 ms, warm materialize
  p95 4.9 ms; survival added work p95/p99 6.2/21.9 ms. Do not relax thresholds or describe
  these short functional batches as capacity tests.
- Evidence: candidate `artifacts/compat/` and `artifacts/civilian-headless/`.

The subsequent planner run `20261002-094346-5af95e` also passed: eight plans/assignments,
four initialized bodies, WALK 23.10 s vs RUN 12.10 s, routine p95 1.5 ms, zero occupied
resources after cleanup. Candidate game processes are stopped; no watched client launched.

Final identity reruns and Bandits client-guard correction are recorded in Current State.
The corrected base/planner receipts are `20261002-100241-70c975` and
`20261002-095605-886be9`; both passed and cleaned up. Client guard/profile drift is now
checked during builds and offline qualification. The complete migration remains open.
