# Offline compatibility auditor

Run from the isolated 42.21 worktree:

```sh
./dayone compat snapshot --profile pz42.21
./dayone compat compare --baseline pz42.20 --candidate pz42.21
./dayone compat test --profile pz42.21 --full
```

Profiles name exact local inputs, API descriptors/access flags and reviewed structural
hook counts. They do not currently replace the runtime guards. Private immutable snapshots
and timestamped reports are under `artifacts/compat/`. Snapshotting does not modify a guard,
launch a game process, or approve an upgrade. Tracked files contain authored code and
hash/API metadata only. Raw class files and decompiled game sources must remain ignored.

Exit codes: 0 means a clean reviewed offline qualification; 1 means a definite contract
break or failed test; 2 means missing inputs or unresolved review/coverage. The initial
profiles intentionally cannot produce a qualified 0 yet. The known baseline comparison
must fail with the removed movement/combat/factory contracts, even after our candidate
adapter code is ported: it describes upstream incompatibility, not a deployment test.

The initial incremental policy is conservative: Python/Lua successes can be reused only
for the same complete authored-content, game snapshot, profile and tool fingerprint.
`--full` forces fresh checks. Java compilation, structural verification and exact hook
counts always run fresh, followed by detached Java components. Reports identify cached
results and their source run. Per-subsystem selection, complete dependency coverage,
reviewed runtime guard generation and native qualification are still tracked in
`TASK_42_21_Compatibility_Migration.md`.
