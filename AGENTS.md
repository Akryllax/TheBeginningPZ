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
the isolated straight-line physics probe passed and one ordinary client reported smooth
movement. A separate empty-server asphalt turn passed, followed by a one-client turn with clear
visibility but incorrect lane choice, no stop-sign handling and deliberately low speed.
Detailed seated-driver visibility is low priority by user choice. A corrected Bézier lane
course and stop-sign dwell have passed empty-server native runs; one client confirmed a
smooth turn after fixing braking pulses, but requested faster driving. The current iteration
uses a 50 km/h road ceiling constrained by stock drivetrain/brakes, loaded mass and script
steering properties. That ceiling is not a measured cruising speed on the short test course.
One client subsequently accepted the faster run (24.1 km/h peak, about 13 km/h through the
bend). The early abrupt parked-car stop and disappearance were rejected even after a longer
cleanup delay. Parked cars now produce a planned stopping point and persistent wait/resume;
one client confirmed a smooth stop and continued visibility during a 66-second wait. The
server resumed and completed the original trip after exact fixture removal. Temperament
selects optional honking; that observed driver chose silence, so audible horn playback is
still pending. Passing reservations have detached conflict/expiry tests, but safe bypass
execution, managed multi-car traffic, NPC boarding and two-client consistency remain
unvalidated; see the implementation ledger and traffic design for limits and next gates.

See `SKILLS.md` for task-specific workflows. Existing user authorization applies to
routine implementation and validation; do not add repeated approval prompts.
