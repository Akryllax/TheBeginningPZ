---
type: runbook
status: active
updated: 2026-09-26
---

# Private mod distribution

The client package is the project's original `LofersStoryteller` Lua companion mod. Bandits and other Workshop dependencies are obtained separately when required by the selected scenario. Source history in `Akryllax/TheBeginningPZ` retains the existing GPL-3.0 license; a source commit or locally generated ZIP is not a tested release or Workshop publication.

The accepted runtime direction allows Java injection on the controlled server and uses ordinary Lua clients. Do not install or distribute the experimental client Java helper, modify the client's launch flags, replace game classes or include decompiled game code. Server-only driving and multiplayer consistency still need runtime validation.

## Build and inspect

```bash
./dayone test
./dayone package
```

The command packages `mods/LofersStoryteller/` using its `VERSION` into `artifacts/LofersStoryteller-<version>.zip` with a `.zip.sha256` sidecar. Inspect the file list and checksum before sharing. Include only the original Lua mod and relevant install/dependency information; exclude game binaries, upstream Workshop source, private save data, caches and credentials. Native planner binaries, server Java agents and experimental client artifacts are outside this ZIP. Record the version/hash in the playtest experiment.

## Install consistently

Close the game before replacing client mod files. Extract so the `LofersStoryteller` directory sits under the client's Zomboid `mods/` directory, with the packaged layout intact. Remove or archive older duplicate copies outside the scanned mods directory. The dedicated server must use the same companion version and enable its actual Mod ID. `./dayone start` installs the current original source while the game is stopped; `./dayone install-mod` exposes that step explicitly. Restarting is required after changing the loaded mod.

Install any required Bandits dependency through its normal distribution. Confirm the selected server's Mod IDs and current compatibility manifest before joining. The disposable server vehicle probe intentionally enables no game mods and requires no companion installation. If the package's install instructions differ because of a verified Build 42 layout requirement, follow the package and update this runbook with the evidence.

## Rollback

Keep the prior original-mod ZIP and a consistent pre-upgrade world backup. Restore matching mod versions across server and clients. A source rollback does not guarantee persistent state is backward compatible; check state schema/migration notes before reopening the world.

The Observer exporter, scenario server hook and native planner are separate server artifacts, deployed with project tooling and compatibility manifests. Their presence does not establish that a scenario is enabled or validated. See [[Design/Architecture]], [[Runbooks/Operations]] and [[Runbooks/Backup and Restore]].
