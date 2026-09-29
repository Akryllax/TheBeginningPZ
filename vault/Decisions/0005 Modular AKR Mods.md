---
type: decision
status: accepted-phase-1-implemented
updated: 2026-09-27
---

# Modular AKR mods

## Context

The [[Reviews/2026-09-27 Project Review|2026-09-27 review]] found one Lua mod and one Java
agent carrying every feature behind a single all-or-nothing compatibility gate. A game or
Bandits update disables everything, and moving traffic — the riskiest feature — blocks the
outbreak story that does not need it. The user agreed to split the project into modular
mods, each self-sufficient where practical, with one required core, and to rename the
project from "Lofers" to "AKR". Constraints from [[Decisions/0004 Server Runtime Extensions]]
still hold: ordinary Lua clients, server-only JVM injection, no replaced or redistributed
engine code, fail closed on unknown builds.

## Decision

### Lua mods

Every feature is its own Project Zomboid mod that depends only on `AKRCore` plus the
dependencies listed. Modules find each other through versioned APIs in the core registry;
an absent optional module is a normal, reported state, not an error.

| Mod | Owns | Requires | Optional | Works alone? |
| --- | --- | --- | --- | --- |
| `AKRCore` | Module registry, event dispatcher, per-module saved state, capability report, admin panel shell, client handshake | — | — | Contracts only; no gameplay |
| `AKRCalmOpening` | Calm opening, population holds, explicit civilian spawn permits | Core, `native.population` | — | Yes |
| `AKROutbreak` | Manual start, 168-hour clock, phases, contagion model, admin start/pause/step | Core | Residents, CalmOpening | Yes: progression expressed through vanilla zombie pressure |
| `AKRPopulation` | Shared admission, physical bindings, interest areas, unresolved ownership | Core | — | Shared accounting; no behavior |
| `AKRResidents` | Persistent identities, homes/routines, cheap crowd controller, Bandits spawn/presentation adapter | Core, Population, Bandits2, validated `native.npc` | planner sidecar, Outbreak | Living town; server Lua behavior fallback when planner is absent |
| `AKRTraffic` | Managed vehicles, traffic incidents, staged crashes, seated driver model | Core, Population, `native.vehicles` | Residents (drivers) | Yes: set-piece crashes with empty cars |
| `AKRDirector` | Post-week adaptive storyteller, pressure, scanner | Core | Outbreak, Residents | Yes |
| `AKRDevTools` | Probe fixtures, test hooks, operator shortcuts | Core | any | Never enabled on campaign worlds |

The first shipped set is **Core + Population + CalmOpening + Outbreak + Residents**. Traffic and Director
follow; neither blocks the slice.

### Server Java

- **`akr-runtime-core.jar`** is the only `-javaagent`. It owns launch guards, configuration,
  the game-thread executor, the transformer host, the `AKRNative` Lua bridge, the resident
  physics world, the event scheduler and the plug-in loader. It keeps its own hooks minimal
  (Lua initialisation and the server update tick).
- **Hook plug-ins** are separate JARs, loaded at startup because bytecode transforms apply at
  class load: `akr-population`, `akr-npc` and `akr-vehicles`. Each declares its hook points and
  their fingerprints. The core verifies each plug-in independently; a failed plug-in is not
  installed and its capabilities are not published. Other plug-ins still load.
- **Behaviour plug-ins** (traffic controllers, event definitions) are reloadable through a fresh
  classloader per version, as already planned in [[Design/Live Event Runtime]]. They receive
  detached observations and return bounded intents; the core keeps engine objects and bodies.
- **Planner service** stays an optional sidecar. Residents fall back to a Lua schedule.

### Compatibility contracts

Each boundary has one owner, a version and additive-only change rules. A breaking change adds
a new major version and keeps the old one for one release.

| Boundary | Contract | Rules |
| --- | --- | --- |
| Lua module ↔ Lua module | `AKR.registry`: `provide(name, "x.y.z", api)` / `require(name, major, minimum)` | Semver; no reading another module's globals or saved state |
| Lua ↔ server Java | `AKRNative.capabilities()` → `{name = "x.y.z"}`; functions grouped per capability | Return `(ok, reason)`, never throw into Lua; no Java objects in saved state |
| Java core ↔ plug-ins | Parent-loaded `net.akr.runtime.api` package with its own major version | Plug-ins declare the API major they need; detached data in, intents out |
| Game engine ↔ hook plug-ins | Per hook: owner class, method, descriptor and a fingerprint of that method's bytecode | Replaces whole-class hashes; unrelated engine edits no longer disable a feature |
| Game ↔ planner | Protobuf `npc_control.proto` | Field numbers never reused; additive fields only |
| Saved state | `AKRCore/Store`: one slot per module with a schema number and stepwise migrations | Refuse newer schemas; never downgrade; legacy keys adopted once |
| Client ↔ server | One command module per mod plus a versioned `hello` handshake | Unknown or old clients are refused with a visible reason |

A `./dayone compat-check --game-jar PATH` command (planned) will report, per plug-in and per
mod, whether a new game build passes, and record results in a compatibility matrix under
`references/`. Method fingerprints narrow false alarms but do not prove semantics; each new
build still needs the offline fixtures and a short native smoke test.

### Rename to AKR

The rename happens during the carve-out, when each file moves anyway, so identifiers change
once. Categories:

| Kind | Examples | Handling |
| --- | --- | --- |
| Source only | `net.lofers.*` packages, Lua `require` paths, JAR, container and script names, docs | Rename directly; `net.lofers.scenario` becomes `net.akr.*` |
| Wire | Protobuf `lofers.npc.v1`, socket `/run/lofers/npc.sock`, planner build string | Rename both ends in one change; frames use field numbers only, so bytes are unchanged |
| Saved or durable | ModData `LofersScenario`/`LofersStoryteller`, per-zombie `LofersScenario` tag read by Java, `SandboxVars.LofersScenario`, mod ID `LofersStoryteller`, vehicle script `Base.LofersSmallCar`, command module `LofersScenario` | Migrate: adopt legacy ModData through `Store.open(..., legacy)`, read old tags for one version, keep an alias vehicle script until probe worlds are retired, update world `Mods=` lists |
| Leave unchanged | `map.lofers.net` domain and certificates, the production Observer on `.160`, git history, recorded evidence in the ledger and `artifacts/` | External or historical; the local Observer copy's `GameStoryteller` must read new keys in the same step as the ModData rename |

`AKR_DayOne` is due a fresh-world reset before scenario rollout, so durable migrations mainly
protect disposable test worlds during the transition.

## Revised order — 2026-09-27

[[Design/NPC First Slice]] supersedes full-runtime-first sequencing. Adopt Core in place
with minimum event controls, then test one/four server pedestrians. Keep Bandits as a
separate dependency for spawning and human presentation; suppress managed actor AI/effects.
Do not silently fall back to client movement. Crowds initially live inside Residents,
while Population owns shared admission. Snapshot/preserve in-progress source before edits.
After the gate, complete reload/batches and the full split.

## Original split phases

1. **Core contracts (done 2026-09-27).** `mods/AKRCore` provides `Version`, `Registry`,
   `Dispatch`, `Store`, `Capabilities` and a reload-safe `Core.instance()`. Tests:
   `tests/test_core.py`, 7 cases in Lua 5.1. It is not installed, not required by the
   existing mod and not tested in game.
2. **Adopt the core in place.** Make the install, package, scenario, probe, client-launch
   and map/model build scripts handle several mods; have the existing mod require `AKRCore`
   and use its dispatcher, store and capabilities. Verify the `require=` syntax on B42.20.
   Gate: server boots, admin panel works, state restores after restart.
3. **Carve out the slice with the rename.** `AKROutbreak` and `AKRResidents` first, then
   `AKRCalmOpening`, `AKRDirector`, `AKRTraffic` and `AKRDevTools`. Gate: each mod loads alone
   with Core, and in every supported combination.
4. **Split the Java agent.** Core agent plus hook plug-ins with per-method fingerprints and
   `AKRNative.capabilities()`, aligned with the `ResidentPhysics` extraction already under way.
5. **Compatibility tooling and slice validation.** `compat-check` and matrix; a real two-client
   session of the slice measuring agreement and server cost.

Preserve the in-progress runtime work when adopting Core; the revised user-approved order
does not require the full runtime to be complete before the pedestrian experiment.

## Consequences

- A game update disables only the plug-ins whose hooked methods changed. Dependent mods report
  the missing capability and refuse to enable, which keeps the fail-closed rule per feature.
- Traffic no longer blocks the outbreak slice.
- More packages to version and release. A shared `VERSION` per mod, a per-mod manifest and one
  packaging command keep this manageable.
- Server hosts install several mods plus the core agent; players install the Lua mods only.
- Revisit if the plug-in split causes more hook duplication than it saves, or if B42
  stabilises enough that one compatibility gate stops being costly.
