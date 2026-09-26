---
type: decision
status: accepted
date: 2026-09-26
---

# 0003 — Observation before encounters

## Context

The director combines unfamiliar NPC ownership behavior with inferred bases and wealth. Automated scheduling tests cannot prove real multiplayer effects or whether the inferred situation matches what players experience.

## Decision

Roll out in stages: fresh baseline, compatibility source audit, real two-client NPC trial, observation-only field/director, then progressively enabled event families. Keep hostile execution disabled while required integration evidence is missing.

Use local Observer diagnostics to inspect proposed decisions, confidence, recovery, cost and timing. Proposals must be visibly identified as proposals and must not pretend encounters occurred. Passing a unit test or server boot is not the two-client gate.

## Consequences

The first usable deployment may collect and display data before it creates hostile events. Record unsupported engine operations and validation gaps explicitly. Enable an event family only after its placement, ownership, persistence, outcomes and cleanup have concrete evidence. Routine authorized development continues while the real-player gate is pending.

See [[Runbooks/Multiplayer Validation]] and [[Experiments/Implementation Ledger]].
