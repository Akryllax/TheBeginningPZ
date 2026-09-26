---
type: runbook
status: active
updated: 2026-09-26
---

# Inspecting the installed Java game

Use this workflow to answer concrete questions about the installed game's APIs,
ownership transfers, vehicle behavior, population factories, and hook signatures.
It complements the original Lua source and [[Research/Bandits Compatibility]];
it does not replace [[Runbooks/Multiplayer Validation]].

## Tooling and commands

The project pins Vineflower **1.12.0** at
`.tooling/decompiler/vineflower-1.12.0.jar` and uses the existing JDK under
`.tooling/agent/`. Downloads, generated source, and supporting evidence stay inside
the project. No global Java installation or Steam game modification is needed.

From the project root:

```sh
python3 scripts/decompile_java.py zombie.vehicles.BaseVehicle
python3 scripts/decompile_java.py zombie.popman.NetworkZombieManager zombie.popman.NetworkZombiePacker
python3 scripts/decompile_java.py 'zombie.vehicles.BaseVehicle$Authorization'
python3 scripts/decompile_java.py --jar data/game-files/java/projectzomboid.jar zombie.Lua.Event
python3 scripts/decompile_java.py --output artifacts/decompiled/vehicle-review zombie.core.physics.CarController
```

The default input is `data/game-files/java/projectzomboid.jar`. Default output is
`artifacts/decompiled/<jarsha12>/`; an explicit output directory should remain
inside the project. Quote `$` in nested Java class names so the shell does not
expand it. Inspect selected classes before requesting broader dependencies.

The wrapper produces reconstructed `.java` files, `javap` evidence, and a
manifest identifying the archive/classes and tools. Read the manifest rather
than assuming that two folders with similar game version labels contain the
same build. Keep it beside the source when recording an experiment.

## What the evidence establishes

Decompiled Java helps reveal callers, casts, field updates, authority checks,
and error paths. It reconstructs source from bytecode and can present synthetic
or optimized code imperfectly. Check the corresponding `javap` disassembly and
method descriptors when a result depends on a suspicious expression, cast,
branch, or overload.

Follow the entire effect path. For example, validating an NPC's own update
callback does not establish exclusive ownership if another zombie's callback
can change that NPC's health. Vehicle methods can also delegate to native Bullet
code whose behavior is not contained in the Java archive.

Record findings using [[Templates/Experiment]] with the input archive hash,
relevant class/method, artifact location, and the specific conclusion. Distinguish:

- **Source evidence:** a method or bytecode path exists and performs particular
  checks or calls.
- **Fixture evidence:** a controlled test exercises an interface or invariant.
- **Runtime evidence:** the pinned game successfully performs the behavior on
  the relevant client/server thread.
- **Multiplayer evidence:** separate clients observe consistent behavior through
  ownership changes, reconnects, and chunk loading.

A decompiler result alone cannot establish game-thread safety, actual vehicle
seat replication, or the absence of duplicate combat effects. Keep unanswered
questions explicit in [[Experiments/Implementation Ledger]].

## Current client integration boundary

As of 2026-09-26, the chosen direction permits JVM injection on the controlled
server and uses ordinary Lua clients. Do not install or launch the superseded
experimental client Java helper. Server-owned vehicle physics and stock
replication are being investigated; actual NPC driving and multiplayer safety
remain unproven. The user also authorized project-local Java inspection.
Reading archives and generating inspection artifacts does not inject code into
a running client or alter installed game files, and does not require repeating
an approval request.

Decompiled proprietary game code is private inspection material. Keep it under
generated artifacts, outside public source repositories and the distributable
companion mod. Share original integration code, concise findings, and hashes
instead of repackaging reconstructed game source. The same separation applies
to upstream mod code obtained for research.
