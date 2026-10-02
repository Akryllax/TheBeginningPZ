# Offline compatibility auditor

Run from the isolated 42.21 worktree:

```sh
./dayone compat snapshot --profile pz42.21
./dayone compat compare --baseline pz42.20 --candidate pz42.21
./dayone compat test --profile pz42.21 --full
```

Profiles name exact local inputs, API descriptors/access flags and reviewed structural
hook counts. The reviewed test-only profile generates gameplay and Observer build guards from the same pins. Runtime startup verifies the whole game JAR, mapped classes and per-method hook counts. Private immutable snapshots
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
results and their source run. Per-subsystem selection, remaining dynamic dependency review, packaging and watched qualification are still tracked in
`TASK_42_21_Compatibility_Migration.md`.

The candidate profile now records 780 API contracts and 168 class pins, including explicit
reflection/door bindings. Its only permitted gameplay worlds start with `AKR_DayOne_Test_`
or `AKRVehicleProbe_`; client JVM use is rejected. This restriction is independent of
successful compilation and headless tests. `compat_inventory.py` writes review proposals
from authored bytecode and resolves inherited declarations; it never approves or edits a
profile. JDK-derived inspection files remain private under `artifacts/compat/jdk-image/`.

Load Observer before the gameplay agent. The dual-agent premain fixture proves that order;
loading Observer after gameplay has already loaded RCON does not install its hook.
