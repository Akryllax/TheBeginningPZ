---
type: runbook
status: first-native-walk-pending
updated: 2026-09-27
---

# Pedestrian experiment

Follow [[Design/NPC First Slice]]. This is the one/four-actor feasibility gate, not a
campaign population. Ordinary Lua clients require Bandits2, AKRCore and AKRDevTools.
Do not enable AKRDevTools on a normal world. Source-independent native behavior and two-client
agreement remain unvalidated; a successful socket command only establishes command handling.

## Prepare and operate

Run `./dayone runtime-test offline`, then `./dayone pedestrian-test-create` and
`./dayone pedestrian-test-start`. They use a fresh world under `artifacts/scenario-tests/`
and `.132:16281/16282`, loopback RCON `27035`; the other disposable server must first stop.
The normal `.132` game and `.160` deployment are not targets. Each start copies the agent
and its manifest privately, so a subsequent build cannot replace the loaded JAR.

The current receipt records the exact directory. Let `<test>` below mean that directory:

```bash
./dayone runtime hello <test>/ipc/runtime.sock
./dayone runtime submit <test>/ipc/runtime.sock <test>/one-pedestrian.json unique-request-id
./dayone runtime status <test>/ipc/runtime.sock <returned-event-id>
./dayone runtime cancel <test>/ipc/runtime.sock <returned-event-id>
./dayone pedestrian-test-stop
```

Definitions specify `actors` (1 or 4), `route` (2–8 ground-level points within 64 tiles
of its origin), `timeout_seconds` (10–180), `hold_seconds` (0–60) and `seed`.
The seed is recorded; it does not yet make upstream appearance generation deterministic.
Reusing a request ID with the same definition returns the same event; changed data rejects.
There are eight pending slots and 256 retained submission receipts per process. Cancellation
does not consume admission capacity. World and epoch are required for every mutation.
The operator transport is a private Unix socket, separate from the planner; there is no
network-accessible arbitrary Lua/Java evaluation endpoint.

Server Lua receives the same scheduler through AKRRuntime submit/status/cancel. The Core
registry publishes that API after the experiment's factory is registered. It is explicitly
marked `pedestrian-experiment-unvalidated`, not a validated `native.npc` capability.

## Actor pool (Decision 0006)

Actor cases use the same `submit`, with `"entity": "ACTOR"` and optional
`"reassign_after_seconds"` (below the hold). Gate 1 Actors stand at `route[0]`. The prepared
files are `one-actor.json` and `one-actor-near.json`. The spawn square must already be loaded
by a player; Actors never load chunks.

Walking Actors set `"walk_tiles_per_second"`. Use 1.45: it is the measured stock walk
speed of a remote player on this build, and faster values drift until the stock 7-tile
correction. Routes are straight segments between tile centres; a blocked segment is refused.
`one-actor-walk.json` is the reference U route. The client diagnostic lines
`[AKRActorSample]`, `[AKRActorSkip]` and `[AKRActorFear]` in the client DebugLog align with the
server's `wallMs` samples.

`./dayone pedestrian-test-join` closes and relaunches the local client into this world. It
needs the developer-only `AKRDevConnect` mod enabled at the main menu, and a saved account
for the server in the stock server list. It then sends one synthetic click to pass
"Click to Start". Never add `AKRDevConnect` to a server mod list.

## First observed test

1. Join as `akr`, remain on foot and inspect the spawn/route before submitting. The prepared
   example starts at approximately `(10756,9856,0)` and heads 20 tiles east. Its live square
   and route clearance still need inspection; this is an operator-visible fixture.
2. Submit one actor. Save repeated status replies with timestamps. Record whether it appears,
   walks continuously, stops and remains visible during the hold. Compare native path state,
   animation-player presence, deferred movement and measured displacement.
3. Cancel if anything is wrong. Verify terminal state and no owned resources. A failed or
   ambiguous spawn retains its reservation. Dead actors/corpses currently block automatic
   cleanup rather than being falsely reported absent; investigate before another case.
4. Repeat with changed route parameters in the same epoch. Only after one actor works, test
   turning/blocked passage and four actors. Injury/death, reconnect/streaming, reliable
   semantic presentation and actual two-client agreement remain separate acceptance checks.

The stock ownership selector skips only adapter-bound actors; their snapshots are appended
to the stock relevance-filtered zombie stream. Incoming movement from a client remains subject
to vanilla owner checks. The experiment does not inject movement vectors or teleport walkers.
Source inspection found ordinary server zombie variable setters are no-ops and path movement
depends on animation-derived displacement. Measure the result; do not assume those paths work.

## Evidence and limits

Status exposes fixed-space update timing, actual positions/displacement, path state/request
age and ownership. These update timings exclude ordinary engine work occurring outside our
hook and the separate Lua publication callback; they are not whole-server cost measurements.

The client has a 256-frame interval ring and sends bounded diagnostic actor samples once per
second. The server retains the latest reports in `AKRPedestrianMeasurements`. Server timestamps
are echoed but these reports do **not** yet implement robust clock alignment or the 10 Hz paired
capture needed to qualify the 500 ms action target. That measurement gate remains pending.

Generic Java hot reload, full durable event receipts, batch CLI and stress qualification are
later work. A saved pending actor disables factory registration after restart; there is no
automatic replay or automatic saved-actor reconciliation. This is fail-closed containment,
not transactional recovery across world and ModData saves.

Bandits remains separately obtained. Only original companion source is installed locally;
the game files are mounted read-only. Record logs/manifests and distinguish offline tests,
empty-server API checks, one-client observation and two-client validation in
[[Experiments/Implementation Ledger]].
