# ZomboidDayOne

This project hosts the experimental world `AKR_DayOne` on 192.168.1.132.
The existing game and Observer on 192.168.1.160 are a separate production deployment.
The accepted target is a civilian opening, manual outbreak activation, and seven game days
of contagion/collapse. A planner, telemetry feed, or vanilla populated world alone is not
completion. See `vault/Design/First Week.md` for the accepted behavior and release gates.

Read `vault/Home.md` for the design index and `vault/Runbooks/Operations.md` for commands.
Use `./dayone` for project operations. Keep downloads, caches, images, references,
saves and build output under this project. Use the installed host Podman; do not
install global toolchains or change shell profiles. Runtime sockets can use `/run/user`.

Game decisions and persistent storyteller state belong to the server. Observer is
read-only telemetry, never a dependency of gameplay. Keep normal map fog and the
loopback-only debug boundary. Preserve existing protobuf fields and compatibility guards.

Measure added game-thread work. Use sparse fields, incremental cursors and explicit
budgets; never scan the whole world or every inventory on each update. Distinguish
missing/stale observations from zero wealth. Real two-client testing is required
before describing an NPC integration as multiplayer-validated.

Keep upstream mods as separately obtained dependencies. Record exact manifests and
integration evidence; package only our original companion mod. Preserve uncommitted
work. Record material decisions, test evidence and known limitations in the vault.
The project source repository is `git@github.com:Akryllax/TheBeginningPZ.git`; preserve its
existing GPL-3.0 license and history. Source versioning there is separate from a public
binary/Workshop release, which is not part of the current milestone.

Inject behavior through original Lua extensions and separately packaged runtime Java hooks;
never replace or patch installed core game files, and never distribute replacement
`zombie.*` engine classes or decompiled game code. This is an explicit user constraint for
future distribution. Keep decompiler tools and derived research output inside this project.
Runtime injection still depends on engine compatibility; do not describe it as update-proof.
The current architecture investigation targets server-owned vehicle physics and stock vehicle
replication with ordinary Lua clients. The user explicitly permits JVM injection on the
controlled server. Do not require a client Java agent for this revised architecture;
no actual driving or multiplayer safety has been established.

See `SKILLS.md` for task-specific workflows. Existing user authorization applies to
routine implementation and validation; do not add repeated approval prompts.
