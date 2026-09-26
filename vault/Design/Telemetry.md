---
type: design
status: implemented-validation-in-progress
updated: 2026-09-26
---

# Telemetry and debug views

The existing server-only Java exporter reports positions and exact exploration. The storyteller extension carries a bounded read-only snapshot through protobuf to Observer without making game simulation depend on the web service. Automated and deployment validation are tracked separately in [[Experiments/Implementation Ledger]].

## Contract

The private endpoint is `POST /internal/v1/storyteller`, authenticated with the shared positions token and validated against world `AKR_DayOne`. It is separate from existing `/internal/v1/positions` and `/internal/v1/exploration` messages. Existing field numbers and compatibility guards remain important when extending the protocol. The app persists replay protection and its latest accepted snapshot; it does not record a position or director history.

The Lua producer writes schema-1 telemetry under the `LofersStoryteller` ModData key. Snapshots carry bounded diagnostics: world/sequence/time, director mode/phase, pressure and recovery, cell dwell/wealth/confidence/age and floor, recent decisions, counters/timing and adapter/NPC status. An NPC count of `-1` is displayed as unknown. Raw complete inventories are excluded.

The Java capture copies at most 64 cell origins and 32 decisions every five seconds into immutable detached records. It uses a soft 1 ms capture deadline; a whole row is atomic and initialization is separate, so this is not a hard proof that every invocation finishes within 1 ms. An independent one-slot daemon sender uses a two-second HTTP timeout and retains no backlog. The app rejects bodies above 64 KiB and marks the feed stale after 20 seconds. Failures in this optional feed are isolated from position/exploration delivery and gameplay.

Enable capture with `storyteller_enabled=true` in the agent properties and local diagnostics with `OBSERVER_STORYTELLER_DEBUG=1` in Observer. Disabling the diagnostics must not alter game behavior.

## Exposure

| Surface | Access | Information |
| --- | --- | --- |
| Normal map API and UI | LAN/public gateway | Existing fog-filtered shared map |
| Storyteller ingestion | Internal container network | Authenticated server reports |
| Detailed debug page/API | `127.0.0.1:8098` | Scores, candidates, field heatmap, decisions and profiling |

The page is `/debug/storyteller`; its read-only snapshot API is `/debug/storyteller/snapshot`. The field layer displays supplied diagnostics while the underlying terrain remains fog-filtered.

Caddy blocks `/internal`, `/internal/*`, `/debug` and `/debug/*` on both public HTTPS and LAN map listeners. Keep all debug API routes under that protected prefix. The direct app port binds loopback, not all interfaces. Do not expose hidden base/stock data through ordinary world or event responses.

Observer remains read-only with respect to gameplay. Shared map drawings/trips are web state and do not authorize server commands. No RCON credential belongs in the Observer container.

## Validation

Test wrong token/world, oversized/malformed payloads, stale/reordered sequences, empty snapshots, process restart, unavailable receiver and recovery. Check actual gateway requests to private path variants as well as app tests. Verify normal map fog remains unchanged. Current evidence belongs in [[Experiments/Implementation Ledger]].
