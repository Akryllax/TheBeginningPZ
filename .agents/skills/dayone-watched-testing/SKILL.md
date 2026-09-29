---
name: dayone-watched-testing
description: Run and assess watched ZomboidDayOne tests with the pinned ordinary client, automatic spectator setup, announced batches and separate human acceptance. Use for launch, rerun and visual feedback requests.
---

# Watched tests

Read the latest checkpoint in [Current State](../../../vault/Experiments/Current%20State.md)
and the relevant case in [Operations](../../../vault/Runbooks/Operations.md).
Use [Watched Testing](../../../vault/Runbooks/Watched%20Testing.md) for the maintained procedure.

Use the disposable .132:16281 world, not normal .132 or production .160. Verify the
server receipt, current epoch, deployed agent and Lua hashes, and pinned ordinary
42.20.4 client before joining. The ordinary Steam launcher can update to an incompatible
version. Never install a client Java agent or replace engine files.

Existing authorization covers launching the pinned client, test administration and
automatic positioning. Do not repeatedly ask permission. If new Lua must be installed,
finish preparation first and close/relaunch the test client in coordination with the user;
do not install into a running client and assume it loaded the change.

Place akr at an unobstructed viewing point before every case/wave; verify actual fresh
client visibility, not just numeric distance. Apply admin/god/invisible/ghost, noon and
clear weather. Supply the established loaded 9mm pistol, two loaded spare magazines and
ammo through the idempotent loadout helper. Never ask the user to find coordinates.

Announce the exact scenario/counts, countdown, wave, viewing hold and completion/failure.
Run the agreed batch continuously, then ask for one visual/audio verdict. Failures,
disconnects, stale readiness or uncertain cleanup stop advancement. A player shooting a
participant makes that case interfered, not an NPC success. Do not substitute a different
case, population or approach direction silently.

Prefer same-process runtime submissions after verified cleanup. Engine-code changes may
require a coordinated restart; Lua client changes require a fresh client load. Do not
promise hot reload. Keep uncertain/dead bodies and exact fixture ownership recorded.

Archive case-scoped machine evidence and verbatim human feedback separately. An animation
flag, screenshot or sound handle cannot prove visible motion or audible sound. Human
success cannot erase failed cleanup; an automated pass cannot supply missing human
acceptance. One client never establishes two-client replication.

For fatal lifecycle checks use a fresh `civilian-combat lifecycle-create` session and
`run lifecycle` only after readiness. One prewarmed Actor proves exact-object reuse.
Keep NOT_EXERCISED separate from failure/pass and from cleanup. No fatal native contact
means no scripted kill or automatic retry. Confirmed zero connections permit native
cleanup verification; any unknown/reconnected viewer requires fresh evidence. Never
clear terminal receipts to bypass restart reconciliation.
