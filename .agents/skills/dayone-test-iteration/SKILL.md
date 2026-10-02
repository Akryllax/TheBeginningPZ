---
name: dayone-test-iteration
description: Develop and qualify ZomboidDayOne native NPC changes through targeted tests, headless native fixtures, repeatable watched batches and evidence-based handoffs.
---

# Native iteration

Read [Current State](../../../vault/Experiments/Current%20State.md) and the active design.
Use [Runtime Testing](../../../vault/Runbooks/Runtime%20Testing.md) for test commands and
[Watched Testing](../../../vault/Runbooks/Watched%20Testing.md) when a real client is needed.

Start from the most recent accepted behavior and preserve it as regression coverage.
Inspect the pinned implementation with dayone-java-inspection for engine assumptions.
Do not bypass hash guards or infer native behavior from mocks or successful compilation.

During implementation run targeted component tests. At a coherent checkpoint run the
Java/build-guard integration checks and applicable project tests. Use the separate
`./dayone civilian-headless` disposable world for native tests that do not need an owner.
Client-owned zombie pursuit and incoming attacks require an ordinary connected client;
headless tests must explicitly leave those gates pending.

Keep the reusable Actor pool alive across cases. Assignment must not allocate new engine
bodies. Check identity generations, stale callbacks, path/action cancellation, snapshots,
injury/equipment reset and verified world/network absence before reassignment. Retain
dead or uncertain resources instead of freeing them to satisfy a test.

Prefer bounded runtime submit/status/cancel over a new startup-only fixture. Separate
deploying code from repeating definitions. An epoch change invalidates prior readiness
and receipts. A failed or interrupted batch must retain evidence and block automatic
advancement until ownership is reconciled.

Measure total added game-thread work and component costs, including diagnostics.
Separate cold initialization from steady work without hiding it. Record p95/p99/max,
path queue age and constructor/reuse/retained counts. Current release targets are p95
<2 ms and p99 <5 ms; functional success is not capacity qualification.

Complete independently testable work before requesting visual feedback. Use
dayone-watched-testing for the prepared batch. Record exact source/build/mod identity,
machine result, human result and outstanding gates in the current handoff and append
material evidence to the ledger. Keep evolving status in the vault, not copied into
every skill. Update procedures when commands change.

For a game-version migration, read `TASK_42_21_Compatibility_Migration.md` and
`compatibility/README.md` first. Use the isolated version worktree's wrapper: the root
worktree is the frozen rollback. `compat` is code-only; native evidence belongs to the
separate headless receipts. Snapshotting must not approve or refresh runtime pins.
Candidate guards currently permit disposable worlds only. Load Observer before gameplay.
Do not infer performance qualification from a functional pass, or launch the old pinned
client against the candidate server. Watched candidate tooling must identify both versions.
