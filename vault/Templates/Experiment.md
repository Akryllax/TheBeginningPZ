---
type: experiment-template
status: template
updated: 2026-09-26
---

# Experiment title

Copy this file into `Experiments/` and replace the title/frontmatter with the experiment's actual date and status. Keep failed and incomplete results as evidence.

## Question and acceptance criteria

State the behavior or performance claim being tested and what observable result would support it.

## Environment and source identity

Record game/Java builds, mod/version hashes, companion ZIP, server/agent versions, world/save, host, players/clients, configuration and resource limits. Link the source/dependency manifest and starting backup when relevant.

## Procedure

Record exact commands or in-game actions, meaningful timing, initial state, inputs and comparison baseline. Distinguish simulated engine calls from real engine behavior.

## Observations and evidence

Record results with artifact/log paths, counts/timing samples and screenshots when useful. Exclude secrets and unnecessary player identifiers. Separate observation from inference.

## Result and limits

Mark passed, failed or incomplete against each acceptance criterion. Record untested scenarios and whether the result supports enabling an event or only further testing. Update [[Experiments/Implementation Ledger]] and the relevant design note.

## Next change

State the narrow change or next experiment supported by the evidence. See [[Design/Performance Budget]] and [[Runbooks/Multiplayer Validation]] for reusable scenarios.
