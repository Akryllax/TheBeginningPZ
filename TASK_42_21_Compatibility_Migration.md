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
- Builder validates guards before packaging, avoiding creation of an unguarded candidate
  JAR. The existing 42.20 guard intentionally still rejects the candidate runtime build.
- Short owned test socket directories replace paths dependent on checkout depth.

## Still required before runtime qualification

1. Complete dependency inventory (reflection and Lua included), subsystem ownership and
   fine-grained test selection. Current mapping is deliberately conservative and partial;
   test success still exits 2 while coverage/review is incomplete. Expand cache self-tests.
2. Establish reviewed generated BuildProfile/CompatibilityContract data as the single
   source for gameplay/Observer guards and package identity. Do not blanket-refresh hashes.
3. Split transformer rules by subsystem; require hook counts at actual premain startup,
   before a save is loaded. Current hook-count check is offline only.
4. Finish migration review: native population register/release bookkeeping, scoped combat
   effects exactly once, interruption, player path unchanged, Observer full-version/maps/
   GlobalModData and Observer-first dual-agent startup, updated Bandits pins.
5. Fix candidate JRE library layout and implement profile-aware isolated launchers,
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
