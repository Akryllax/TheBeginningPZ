---
type: runbook
status: maintained
updated: 2026-09-28
---

# Watched testing

The ordinary client and server are pinned to 42.21.0. Use project commands and
`scripts/launch-pinned-client`, never Steam Play for these tests.

## Preparation

Read the active receipt and Current State. Record the scenario, exact counts, seed,
agent/game/mod hashes and source identity including dirty-source hashes. Preserve
previous evidence. Stop only the preceding disposable server when deployment requires
it. Copy Lua only while the test client is closed. Validate compatible installed files
before launching; never claim a running client loaded edited Lua.

Disposable character copies must remap matching `networkPlayers.world` rows as well as
account world names. Preserve the source database and unrelated-world rows.

Use the existing create/start/join commands listed in [[Operations]]. Runtime cases use
submit/status/cancel on the existing private socket; no repeated restart for unchanged
code. Never run a legacy startup harness and a runtime Actor case concurrently.

## Observation

Automatically position akr before each case/wave. Verify on-foot admin/god/invisible/ghost,
clear noon, facing toward the participants at initial and reuse viewing stages, and fresh client reports from the actual viewing area. Reject blocked sightlines
or missing reports. Supply the idempotent loaded pistol, two spare magazines and ammo. Encounter positioning
waits for a loadout confirmation scoped to the current player connection, epoch and case;
never reuse a prior connection’s acknowledgment.
Announce preparation, exact population/scenario, countdown, each wave, the viewing hold,
cleanup and final outcome. The moving encounter batch uses an 8-second viewing hold, 2-second off-screen quiet period and 2-second countdown (60% shorter fixed idle waits). Readiness, collision and verified cleanup remain condition-based; active-case timeouts are unchanged. Unrecoverable death stops the operator immediately; transient cleanup gets at most 24 seconds.

Run the agreed batch continuously and collect feedback afterward. Under the user's
2026-09-29 regression policy, record a noncritical failure and continue only after verified
cleanup, an empty resource ledger and stable runtime identity. Disconnect, stale readiness,
critical runtime failure or uncertain cleanup stops the whole batch. See [[Visual Regression Batch]].
Do not replace a failed case with an easier demonstration.
A user may shoot; damage to participants invalidates the affected case rather than proving
an NPC result. Retire only exact tracked resources out of view of all observers.

## Evidence and acceptance

Machine evidence records epoch/event/case identities, deadlines, contacts and misses,
state transitions, native damage, ownership/freshness, positions, constructor/reuse counts,
cleanup and timing. Archive logs with a real byte-offset or timestamp boundary captured
before the case. Empty/missing/rotated boundaries are unavailable evidence, not zero errors.
Identify unrelated preexisting warnings separately.

Human acceptance is a separate receipt with verbatim feedback and scope. Preserve original
machine reports, including failure. Never infer sound/motion from flags or screenshots.
Single-client, headless, detached and two-client results have separate qualification gates.

Update Current State and the Implementation Ledger after a material result. Store evolving
scenario details in the design/runbook and link them from skills rather than duplicating them.


Client diagnostic APIs require a real Lua exposure check: a public Java getter may return
an opaque object that Lua cannot index (for example NetworkPlayerAI). Do not infer exposure
from a Java compilation or a permissive Lua mock. Diagnostic errors block a watched run;
record the failed attempt and fix them before advancing.

## Fatal lifecycle case

Use a separate `civilian-combat lifecycle-create` session and submit `run lifecycle`
after in-world readiness. One initialized body must be reused. Keep native non-attack
(`NOT_EXERCISED`), scenario failure and cleanup outcomes separate. Do not force a kill
or automatically retry. Confirmed empty connections allow native cleanup verification;
connected observers require fresh replica-absence reports, and unknown viewers block it.
The full sequence and remaining gates are in [[../Design/Civilian Defense and Death]].

When a visibility gate fails, inspect its own stage samples before interpreting later
cleanup absence. Distinguish replica presence, screen position, loaded geometry and LOS;
`client_visibility` records these independently. A present-but-hidden Actor is not evidence
of a failed identity reset or native replication.
