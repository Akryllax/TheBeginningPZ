---
name: dayone-observer-telemetry
description: Extend DayOne's server-only protobuf exporter and local Observer storyteller debug views while preserving fog, bounded work and gameplay independence.
---

# Observer telemetry

Read `vault/Design/Telemetry.md` and `observer/docs/server-positions.md`. Use the DayOne root deployment paths and ports; upstream Observer docs describe the separate `.160` world.

The agent hooks are build-specific. Preserve class-hash/API compatibility guards and verify against the installed game and Java build before loading a replacement. Copy game-owned data on the game thread into bounded detached snapshots. Serialize and send on a background worker with a latest-only queue, finite request timeouts and rate limits.

Preserve field numbers and wire compatibility in existing position/exploration protobufs. Put optional storyteller data on its own versioned message and `/internal/v1/storyteller` endpoint. Validate token, world identity, payload bounds and ordering before accepting state. A telemetry error must not disable gameplay or corrupt the existing feeds.

Detailed base values, candidate locations and director decisions belong behind the loopback-only debug boundary. Public Caddy must block `/internal`, `/internal/*`, `/debug` and `/debug/*`; verify path variants and debug API placement. Do not add a game-control endpoint to Observer. Normal map APIs must retain fog filtering.

Test wrong-world/token rejection, malformed/oversize snapshots, stale ordering, empty state, restart, unavailable Observer and recovery. Validate the actual public and loopback surfaces, not only FastAPI route tests. Report telemetry availability separately from source freshness and from the storyteller's execution mode.
