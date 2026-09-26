---
type: runbook
status: active
updated: 2026-09-26
---

# Backup and restore

A copy made during an active world write is not a verified game backup. Preserve the game, director state, Observer database and matching configuration together before upgrades or resets.

## Create a consistent backup

```bash
./dayone backup
```

The command gracefully stops the normal `.132` game and its Observer/gateway/worker, copies state and records a checksum. It writes a temporary `.partial` archive, finalizes the archive/checksum, then retains the latest seven completed backups. Incomplete writes do not count toward retention. The command **leaves these services stopped**. Inspect the result before starting again with `./dayone start`. A failed graceful shutdown prevents backup creation. There is no automatic backup schedule.

Independent scenario-test and vehicle-probe containers are not stopped by this command. Stop them with their own commands before separately preserving their state. Their disposable worlds are outside the normal backup, and the `.160` deployment is untouched.

## What the archive contains

Existing paths are included; optional scenario artifacts need not exist for a backup to succeed:

| Scope | Included paths |
| --- | --- |
| Playable state and credentials | `data/Zomboid/`, `data/observer/`, `secrets/` |
| Mod/reference and selected source | `mods/`, `references/`, `npc-service/`, `scenario-agent/`, `protocol/`, `client/`, `scripts/` |
| Deployment configuration | `compose.yaml`, `game/entrypoint.sh`, `game/scenario.json` |
| Built hooks, map and acceptance | `artifacts/agent/`, `artifacts/scenario-agent/`, `artifacts/scenario-map/`, `artifacts/scenario-tests/acceptance.json` |

The world includes persistent storyteller state. Stopping Observer preserves its SQLite database with its WAL; copying only a live database can lose data. The archive also contains account databases, configuration and the complete `secrets/` directory, so **it is private**. Archives use mode `0600`; keep extracted copies protected too. Retained experimental `client/` source or artifacts are recovery material, not permission to install or distribute the superseded client Java helper.

This is not a complete repository or dependency backup. It excludes `data/game-files/`, `.tooling/`, the native runtime bundle, `observer/` source, decompiled research output, and disposable test/probe worlds. Preserve source history and record exact dependency/build identities separately. Hash manifests identify dependencies but do not reconstruct them. Never share a recovery archive as a source release or client mod package.

## Automated restore check

```bash
./dayone restore-test backups/<archive>.tar.gz
```

This verifies the archive checksum, extracts into a private directory under `artifacts/restore-tests/` and checks SQLite integrity. It does not overwrite the running world or restart services. Inspect the output for the extraction/evidence path; the extracted secrets remain private.

An intact archive and SQLite integrity result prove useful but limited things: readable bytes, successful extraction and database structure. They do **not** prove the game can load all regions, restore NPC ownership or resume a director lifecycle correctly.

## Playable restore validation

Use an isolated copy and unused ports/world identity. Keep its exporter separate from the normal Observer or disable delivery so restored data cannot enter the active world's map. Launch with the exact recorded game/mod/agent versions, inspect save loading and persistent state, then check players, inventories and any active encounters in-game.

Record restore duration, source archive/hash, build identities, load warnings and gameplay checks in [[Templates/Experiment]]. Stop and remove only the isolated test runtime when finished. Never test restoration by replacing the live save.

For a real recovery, select the known-good matching backup, stop the current services, preserve the damaged/newer state separately, restore while stopped, and validate before reopening access. Update [[Experiments/Implementation Ledger]] with the actual result.
