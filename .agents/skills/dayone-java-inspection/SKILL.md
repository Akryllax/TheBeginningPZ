---
name: dayone-java-inspection
description: Inspect installed Zomboid Java classes and bytecode to verify game APIs, ownership, vehicle behavior, population hooks, and compatibility against the exact local build.
---

# Inspect the installed Java implementation

Use the project wrapper from the project root:

```sh
python3 scripts/decompile_java.py zombie.vehicles.BaseVehicle zombie.popman.NetworkZombieManager
```

It uses pinned Vineflower 1.12.0 in `.tooling/decompiler/` and the existing project
JDK. The default game archive is `data/game-files/java/projectzomboid.jar`;
`--jar` selects another installed archive and `--output` selects a project-local
artifact directory. Default output is `artifacts/decompiled/<jarsha12>/`.
See [the Java inspection runbook](../../../vault/Runbooks/Java%20Inspection.md)
for examples and evidence recording.

Inspect the narrowest relevant classes, then follow callers and callees when
ownership, game-thread affinity, native calls, or side effects depend on them.
Quote nested class names containing `$` in shell commands. Keep generated source,
`javap` output, and the manifest together so findings remain tied to the exact
input archive, class hashes, and tool version.

Treat decompiled Java as a readable reconstruction. Use the accompanying
bytecode/descriptors to resolve suspicious casts, control flow, signatures, and
synthetic methods. JNI/native behavior and actual network ownership require
separate evidence. A compiled helper or a plausible method body does not prove
that a call succeeds on the server, on remote clients, or after handoff.

For NPC replication audits, trace effects on the **target**, including callbacks
invoked for an ordinary attacker. A gate on the managed NPC's own update does not
cover another entity's code changing its health, inventory, or action state.
Record source findings, hypotheses, fixture results, and real multiplayer
observations separately.

This workflow reads installed files and creates inspection artifacts; it does
not install or launch a client agent. Honor the current session's client
integration boundary without turning read-only inspection into an approval
gate. Keep proprietary decompiled game source out of companion mod packages,
public repositories, and published documentation; record concise findings and
private artifact references instead.
