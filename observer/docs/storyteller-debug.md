# Optional storyteller diagnostics

This copied Observer belongs to the independent `AKR_DayOne` deployment.
Use the [project operations guide](../../vault/Runbooks/Operations.md), not the
older production deployment commands in this directory's original documentation.

The companion Lua mod owns all game decisions and persistent state. Its detached
`ModData.getOrCreate("LofersStoryteller").telemetry` publication is schema 1.
Observer only reads it. The Java adapter calls `GlobalModData.get`, never
`getOrCreate`, `transmit`, `save`, inventory scans or game commands.

Enable `storyteller_enabled=true` in the position agent's properties and
`OBSERVER_STORYTELLER_DEBUG=1` in the Observer service. Both are optional. The
adapter checks the exact Build 42.20.4 `GlobalModData` and `KahluaTable` class
hashes and shares the existing, unchanged version-guarded server-thread hook.
An absent mod yields no samples. A malformed schema disables this adapter alone;
position and exploration feeds continue.

Every five seconds the game thread can detach up to 64 cells and 32 recent
decisions, with a one-millisecond soft loop deadline. Metadata, a single row,
class initialization and JVM pauses can exceed that deadline: this is a bounded
work policy, not a hard real-time guarantee. A partial preview is explicitly
marked. Coordinates are 32-tile cell origins with separate integer floors.
The mod rotates a bounded preview of larger spatial fields; the page is not a
complete historical heatmap. Duplicate publication ticks are skipped so an old
Lua publication cannot appear newly updated.

Only detached immutable primitive records reach the dedicated sender thread.
There is one pending sample, no growing backlog, no disk writes on the game
thread, and a separate two-second HTTP timeout. An unavailable Observer does
not block the game or other exporters. HTTP payloads are at most 64 KiB.

`POST /internal/v1/storyteller` accepts the `StorytellerSnapshot` protobuf defined
in [positions.proto](../protocol/positions.proto). It requires the existing
64-character bearer token and exact world identity. Session, sequence and
timestamp checks reject replayed, expired and retired-session messages. The
latest snapshot and anti-replay watermark are retained in the Observer SQLite
metadata table; no travel history or diagnostic time series is recorded.
Missing NPC counts become `null`, never zero. The feed becomes stale after
20 seconds, retaining the last sample. A web restart also marks retained data stale.

The local debug page is `http://127.0.0.1:8098/debug/storyteller` and its only data
route is `GET /debug/storyteller/snapshot`. These routes return 404 without the
debug environment flag. The project gateway denies `/internal`, `/internal/*`,
`/debug` and `/debug/*`; port 8098 is bound to loopback. This deployment boundary
must remain intact: the debug flag itself is not authentication. Public world
and event APIs contain no storyteller diagnostics.

The page shows dwell, observed wealth, confidence and age by floor, pressure,
budget, lifecycle mode, scan counters, timing summaries, unknown NPC state and
recent decisions. Terrain uses the ordinary shared explored-map tiles and keeps
their fog. The page has no gameplay actions. All mod-supplied text is inserted
as text rather than HTML.

Validation added for this integration:

- Python API tests cover opt-in routes, bearer authentication, size/schema/value
  validation, replay and session retirement, latest-only persistence, restart
  staleness and separation from public data.
- Browser tests cover the waiting state, field floor/metric controls, partial
  previews, unknown NPCs, stale data retention and text injection handling.
- The cross-language Java fixture uses the real installed `RCONServer`,
  `GlobalModData`, Kahlua interface and exploration classes with controlled
  players/tables. It verifies detached snapshots, thread ownership, class hash
  rejection, item caps, independent slow HTTP receivers and failure isolation.
  It prints a separate warmed capture benchmark; fixture timings are not a
  multiplayer performance validation.

Two real clients and the actual NPC engine still require the project's separate
multiplayer compatibility trial before active event spawning is described as
validated.

## Validation recorded 2026-09-26

The full Observer Python suite passed (114 tests), both new diagnostic browser
tests passed, and TypeScript plus the production frontend build passed. All five
Java fixture modes passed. With 70 input cells and 40 input decisions (deliberate
overflow), 200 warmed bounded captures per mode measured p95 0.083–0.115 ms,
p99 0.298–0.434 ms and maximum 0.404–0.769 ms on this workstation. The caps held
at 64 cells and 32 decisions. The combined position/exploration fixture had
12–13 ms maximum hook calls; those include startup and exploration work and
are not equivalent to the storyteller capture timings. Raw output is generated
at `observer/artifacts/storyteller-agent-test.txt`.

The fresh local game also published real diagnostic data through the complete
Lua → Java → protobuf → Observer path: tick 79, zero online players,
`observe` mode, `observing_native_spawns_disabled`, unknown NPC count and no
spatial cells. Its short empty-world sample reported Lua p95 1 ms, p99 2 ms,
maximum 2 ms and Java capture 0.190 ms. The game was briefly unpaused for this
check, then `PauseEmpty=true` was restored. The diagnostic page consequently
shows retained stale data while the empty game remains paused. A real-page
browser check produced no JavaScript errors; screenshot:
`observer/artifacts/storyteller-live.png`.

These checks establish transport, isolation and the empty-world adapter path.
They do not establish multiplayer NPC behavior or performance under player and
inventory load.
