---
type: decision
status: accepted
updated: 2026-09-26
---

# Server runtime extensions

The user explicitly permits JVM injection on the controlled server, while preferring ordinary
Lua mods on player clients. Behavior must be injected without changing installed core game
files. Future distributions contain original extensions, not replacement engine classes or
decompiled game code. The initial client Java prototype is superseded.

Keep the C++ planner and server-owned durable scenario. Capture the relevant Bandits event
registrations through a scoped Lua integration, with verified load order and native ownership
checks. Incoming effects on managed victims also require isolation; gating only the victim's
own callback does not cover an ordinary zombie attacker.

Investigate real NPC driving through server-owned vehicle simulation and native vehicle
packets. The installed build has Server authority and client interpolation, but dedicated
servers skip normal Bullet body registration/control/cleanup. Stock passenger packets encode
players only. An isolated empty-car probe must establish native motion, braking and cleanup;
collision ownership and visible NPC occupants require additional tests with real clients.

This decision supersedes the client-agent integration in the initial implementation proposal.
It does not waive the real driving requirement or authorize claiming a kinematic mock is a
functional NPC vehicle. Existing worlds remain intact until acceptance and archival.

Tooling is local: pinned Vineflower plus the existing JDK, wrapped by
`scripts/decompile_java.py`. Record exact bytecode evidence beside reconstructed source in
ignored artifacts. See [[Runbooks/Java Inspection]] and [[Design/First Week]].

Source repository: `https://github.com/Akryllax/TheBeginningPZ`. Preserve its existing
license/history. Public source versioning is distinct from releasing binaries or publishing
a Workshop mod.
