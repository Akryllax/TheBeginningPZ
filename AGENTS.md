# ZomboidDayOne

This project hosts the experimental world `AKR_DayOne` on 192.168.1.132.
The existing game and Observer on 192.168.1.160 are a separate production deployment.
The accepted target is a civilian opening, manual outbreak activation, and seven game days
of contagion/collapse. A planner, telemetry feed, or vanilla populated world alone is not
completion. See `vault/Design/First Week.md` for the accepted behavior and release gates.

Current priority (2026-09-27): follow `vault/Design/NPC First Slice.md`.
Build minimum reusable event controls and measurements, then prove one/four server-controlled
pedestrians before completing Java hot reload or growing crowds. Server pedestrian simulation
is unproven; stop at the documented feasibility gate rather than silently switching authority.
Keep accepted car behavior as regression coverage. Read `vault/Experiments/Current State.md`
for implementation/deployment evidence and `PLAN_SchedulerAPI.md` for the runtime contract.
Preserve unrelated working-tree changes and uncertain resource ownership.

Current iteration: civilian pooling/navigation and idle/roam/flee preparation;
see `vault/Design/Civilian Pool and Navigation.md`. The user subsequently authorized
headless native tests: use `./dayone civilian-headless run` in its separate disposable
world. Walking, doors, snapshot restoration and four-slot reuse have passed there.
Actor reuse means prewarmed engine objects reassigned across residents through
the explicit NativeActorReset boundary. Do not substitute resident-affine caching or
construct bodies during assignment. Busy/dead/uncertain bodies stay owned; see the design.
Four watched reuse waves passed with one ordinary client; the user confirmed audio.
The authorized 32-Actor 8x4 test passed four waves (128 assignments, 32 engine bodies).
Position accuracy remains outside its target; see Current State for measurements.
The pool class now allows 32 slots by default and headless prewarm of 32 passed;
keep normal live admission at four until attack/contact, performance and multiplayer
gates pass. Opt-in 32/64 stress tests do not qualify crowds or parallel path jobs.
The user authorized four 8x8 waves of 64 Actors plus 128 native fast shamblers (two per
Actor), in the original southbound marching direction. Announce exact counts before each
wave. Permit up to 10% acquisition failures (at least 116/128 actual pursuits); account for
and clean up every spawned zombie, including nonfollowers. Do not substitute single-pair
checks or reverse approaches. The user authorized admin/god/invisible observer
protection, and automatic spectator repositioning before every wave. Do not ask them to
navigate coordinate-only waiting points. Preserve native zombie ownership and fail on
unproven contact/cleanup; pursue this in the disposable watched harness only.
The actual 64/128 run reached movement but failed on contact in wave 1; see Current State.
Fix nearest-pursuer clearance and inspect nonmoving hunters before repeating; 64/128 reuse
and cleanup remain unqualified. Two-client checks remain deferred. Do not auto-activate ambient modules or
equate headless results with multiplayer qualification. Full traversal
(stairs/windows/fences/walls as well as doors) remains required; explicit native
execution blockers are recorded in the design and qualification runbook.
Current defensive-combat work is in `vault/Design/Civilian Defense and Death.md`.
The stationary-threat autonomous defense/escape watched test is accepted; moving
pursuit, generalized incoming injuries, integrated combat death and two-client
qualification are separate gates. Use the latest Current State, not historical blockers.
The one-Actor stationary fatal lifecycle is now accepted with one ordinary client:
durable terminal receipt, native corpse/reanimation, exact Actor reuse, replacement walk
and verified cleanup. The next task, scheduled for 2026-09-30, is
`TASK_Four_Resident_Neighborhood.md`: movement regression, repeated lifecycle,
four independent civilians, real ground-floor homes and living-resident controlled restart.
Crash recovery and aftermath restoration are explicitly deferred follow-ups, not qualified
by controlled restart. Read that task and the latest Current State before continuing.

Use `dayone-test-iteration` for the native implementation/qualification loop and
`dayone-watched-testing` for watched tests. The latter preserves automatic placement,
protection, daylight, the loaded 9mm loadout, announcements and feedback after the batch.

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
The user permits JVM injection on the controlled server with ordinary Lua clients.
Keep stock collision and replication semantics. Native ownership requires engine bookkeeping;
setting an owner field alone is insufficient. Preserve the existing physics coordinate frame.
Detailed experiment history belongs in the vault ledger, not in this instruction file.

See `SKILLS.md` for task-specific workflows. Existing user authorization applies to
routine implementation and validation; do not add repeated approval prompts.
