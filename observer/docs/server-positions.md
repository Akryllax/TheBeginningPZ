# Optional server-only positions and exploration

Observer can receive a complete list of online player positions approximately once per real second. A small Java startup agent samples the dedicated server's game thread, then a separate daemon sends Protocol Buffers to Observer over a private Docker network. No client installation, Workshop subscription, Lua file, game checksum setting, or game command is involved. The previous `ZomboidObserver` Lua mod remains disabled.

The map displays live online positions and clearly labeled last-seen/saved positions. Checkpoint arrivals use fresh live samples on the same floor. Start/resume establishes a baseline; an arrival requires a later sample. An outage never substitutes an older save for a live arrival. Reconnecting, replacing a character or restarting the game requires resuming an active trip, since the exporter deliberately uses an ephemeral connection identity. Death also pauses progress.

Terrain, annotations, death markers and eligible cars continue using the existing collectors. Exact explored/learned masks now have their own feed, described below, with saved exploration ZIPs as a fallback. There is **no travel history or replay**. Each exporter worker keeps one pending snapshot; Observer keeps at most one recent position per player (256 names maximum). These positions disappear when Observer restarts. Current exploration masks, trip progress and anti-replay watermarks persist; there is no position trail or raw inventory archive.

## Exact exploration

Build 42 keeps connected players' exploration in `WorldMapVisitedServer.dictionary` and writes its ZIP files on world save/logout. The production hourly save job caused long delays despite successful 30-second Observer polls. The added adapter reads the existing dictionary on the same game-thread hook as positions. It does not invoke exploration updates, visibility checks, reveals, save methods or game commands. No areas are inferred from player locations, loaded chunks, or line interpolation. Both visited and map-learned bits are preserved, including masks that have been cleared.

`GameExploration` verifies SHA256 of the installed `WorldMapVisitedServer` and `WorldMapVisited` classes before using reflection. It copies one online player's packed two-bit mask every two seconds, in round-robin order; with two online players each mask updates about every four seconds. The native array is cloned, so the sender never accesses mutable game objects. A separate bounded worker compresses and posts to `/internal/v1/exploration`; slow uploads and errors cannot block the position sender. Empty-server messages are heartbeats. A connecting player whose mask has not loaded yet is skipped until a later capture. Unsupported exploration state disables only exploration; position export continues.

The protobuf contains world/session/sequence/capture time and at most one complete player mask with map bounds, save version and zlib-compressed flags. The receiver shares the existing private network/token protection and Caddy `/internal/*` restriction. It validates authentication, dimensions/version, session transitions, freshness, size and compression termination. Limits are 8 MiB per request, less than 32 MiB inflated flags, 262,144 known blocks per player and 256 stored players. Unchanged compressed masks reuse the previously decoded cells. Decoding/indexing runs in a worker thread; changed effective masks advance the coverage revision and existing browser stream.

Observer persists only each player's latest mask and capture time in `exploration_masks`, plus an independent anti-replay watermark. It chooses the newer of that mask and the player's saved ZIP. An old ZIP cannot overwrite new discoveries; a later logout/save can supersede the last live capture. Full replacement also respects map clearing instead of permanently unioning obsolete knowledge. Unlisted players retain their last available data. Outages/restarts preserve known areas; there is no movement history. Ordinary source polling continues as a fallback.

The status strip distinguishes **Live exploration**, delayed exploration, and exploration-file age. Discovery updates refresh tiles and place search for open viewers without moving the map. Positions and unchanged exploration masks do not trigger terrain reloads.

## Compatibility and isolation

Validated target: **Build 42.20.4, commit b0bbce05d5, Java 25**. The adapter checks the full `Core.getVersion()` string. On this build `getVersionNumber()` omits the hotfix number. The transformer also verifies SHA256 of `zombie/network/RCONServer.class` before inserting a no-argument callback at the start of its `update()` method. `GameServer.main` calls this method once per loop even if RCON itself is disabled, and when the empty server's game clock is paused. The exporter neither uses RCON nor obtains its password. Only in-memory class bytes change. An unrecognized class or game version disables exporting; it does not relax compatibility checks.

An ordinary `media/lua/server` mod is insufficient for this server's no-client-install requirement. Inspection of the installed `LuaManager.LoadDirBase`, `NetChecksum.Checksummer`, `ConnectionDetails.writeMods` and `ConnectToServerState.CheckMods` shows that enabled mod IDs are sent to clients and server Lua contributes to the negotiated checksum. No supported `serverOnly` mod flag was present. The dedicated startup agent avoids that client loading path.

The implementation uses Java 25's [class-file transformation API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/classfile/package-summary.html) and [startup instrumentation](https://docs.oracle.com/en/java/javase/25/docs/api/java.instrument/java/lang/instrument/package-summary.html). Game objects are accessed only on the main loop thread, through cached public getters. The network thread receives immutable primitive records. Client position reports may already have some game/network delay; this is a current server view, not a client camera feed or interpolation.

Failures in setup, transformation, capture or sending are contained. Capture errors disable the adapter until restart. HTTP connect timeout is 500 ms and request timeout is 2 seconds. Old pending samples are replaced, samples older than 5 seconds are dropped, redirects are disabled, and repeated network warnings are limited to one per minute. Neither HTTP nor disk I/O runs in the per-second game capture. A missing or corrupt agent jar is a JVM startup configuration error, so retain the rollback instructions below. Exploration adds one detached byte-array copy per two-second capture; class/resource verification happens only during adapter initialization, and compression/network work stays off the game thread.

## Protocol and receiver

The versioned contract is [positions.proto](../protocol/positions.proto), using the official protobuf compiler and Java Lite/Python runtimes. It contains the world, exporter session/start time, sequence, capture time and a full online-player list: username, character name, X/Y/Z, dead flag and ephemeral connection identity. An empty list means zero online players. There are no Steam IDs, keys, containers, command fields or inventory contents.

`POST /internal/v1/positions` accepts `application/x-protobuf` with `Authorization: Bearer <token>`. A 32-byte random token is read from mounted secret files. It is never sent to browsers. The receiver validates world/protocol, session transitions, timestamps, finite coordinate bounds, duplicates, identity formats and a 128-player/64 KiB limit. Out-of-order or retired sessions are rejected, including after an Observer restart. Samples older than 10 seconds are rejected; a feed also becomes stale after 10 seconds without a fresh sample. Hosts need reasonably synchronized clocks (at most 5 seconds ahead accepted).

Caddy returns 404 for `/internal` and `/internal/*` on both LAN and HTTPS routes. The app is not published directly; its position network is internal and shared only with the game container. Authentication remains required on that private network. Position snapshots and exploration health are multiplexed as `positions` and `exploration` events over the existing `/api/v1/events` stream, alongside trips/pings/world revisions. No additional SSE connection is opened. Position changes update survivor pins, reference coordinates and labels without rebuilding terrain or forcing a world refresh. Browsers independently mark the feed stale after connection loss.

## Build and validation

```bash
uv sync --dev
python scripts/build_position_agent.py
uv run pytest -q
uv run ruff check observer tests scripts/build_position_agent.py scripts/test_position_agent.py
npm run build --prefix web
PYTHONPATH=. uv run python scripts/test_position_agent.py \
  --jdk "$OBSERVER_BUILD_JDK" --game-jar "$OBSERVER_GAME_JAR"
```

The build script downloads SHA256-pinned Temurin JDK 25.0.4.1, protoc 36.1 and protobuf-javalite 4.36.1 into an isolated user cache. It does not replace the game runtime. Generated Python code uses protobuf 7.36.1. The output is `artifacts/position-agent/observer-position-agent.jar` with the upstream runtime and license notices bundled. The Java fixture test loads the **actual installed RCON class**, uses controlled player objects, verifies Python decoding of Java protobuf output, checks game-thread access, death/disconnect snapshots, an unknown-class rejection, and a deliberately slow HTTP receiver with no backlog.

An isolated dedicated-server startup test must also pass against the deployed game files, mounted read-only with a separate world/cache and private test network. Fixtures alone do not prove all live game APIs. Browser checks in `web/tests/positions.spec.ts` require a local preview with an explicit test token; they refuse to mutate a remote deployment. Existing saved-map and planning checks run with the exporter disabled, while live checkpoint behavior has separate tests.

## Deployment and rollback

The optional overlays are [Observer](../deploy/positions.compose.yaml) and [game](../deploy/game-positions.compose.yaml). Prepare and review them before restarting the game. Do not enable the old Lua mod.

1. Back up Observer's SQLite database using its backup API, both Compose configurations and the current game INI. Retain the previous app image and agent jar.
2. Create `secrets/positions.token` with `secrets.token_hex(32)`, mode 0600, owned by UID 1001. The live game and Observer both use UID/GID 1001. Keep the parent secrets directory mode 0700.
3. Copy the built jar and a configured `positions.properties` into `server-agent/release/`. Set the world to the exact server name. Use `http://observer-positions:8000/internal/v1/positions` and `/run/secrets/observer-positions-token`.
4. Install the Observer overlay as `compose.override.yaml` (merge with any existing override) and deploy the app. Reload Caddy with the new private-path restriction. This creates the internal `zomboid-observer-positions` network. No public port or DNS change is needed.
5. Render the game overlay with the absolute Observer installation path and install it as the game project's `docker-compose.override.yml` (merge existing content if present). This sets `JAVA_TOOL_OPTIONS=-javaagent:...`, two read-only mounts, and the private network, while retaining the original default game network/ports. Persist the override so ordinary `docker compose up` and power recovery retain the exporter. Do not replace an existing `JAVA_TOOL_OPTIONS` value without merging it.
6. At a suitable restart window, save and stop the game gracefully, then recreate only `pzserver`. Verify the startup log, unchanged Mods/WorkshopItems/checksums, feed timestamps continuing with zero players, and the first real online survivor position. The normal launcher also performs a Java version probe; the environment agent option may produce an additional harmless setup log for that short-lived JVM.

Rollback: remove the exporter entries/override from the game project and recreate only `pzserver`. The jar is never copied over game classes. Remove the Observer overlay/environment to return checkpoint tracking to coarse saved positions; restart only Observer. Paused trips may be resumed manually. Removing the optional live feed does not remove maps, trips, annotations or death history. Keep the gateway's `/internal` restriction.

## Possible later additions

- **Travel recording/replay:** opt-in retention, sample reduction, gap-aware playback, per-player deletion, and explicit pauses while offline. Keep recording separate from this latest-position feed.
- **Vehicle updates:** faster locations/condition/fuel for the existing keyed, hotwired and wreck eligibility rules. Preserve ownership filtering; loaded server vehicles are not automatically player observations.
- **World changes:** player constructions, doors, barricades and other loaded objects. Restrict collection to agreed observed areas; server-loaded chunks do not prove a player saw every object or container.
- **Death/disconnect events:** explicit events could complement the current reliable death-log collector if they provide useful information.
- **Map annotations:** continue the saved public-marker import until exact sharing/visibility rules are supported in an exporter. Exploration itself is implemented above; private markers and unsent client-only information are not exported.

These are extension points, not enabled features. Add new protobuf fields/messages compatibly; never reuse existing field numbers or turn the telemetry endpoint into a game-command channel.
