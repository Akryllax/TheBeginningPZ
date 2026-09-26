# Saved-map validation — 2026-09-13

The saved-map implementation is independent of the experimental game mod. No Lua code is loaded by the game for this feature.

- 21 Python tests pass, including 6 saved-map tests. New coverage checks B42 chunk/tile ordering and run-length skips, hidden terrain at detailed and overview zooms, per-user feature/search masking, category aliases, residential garage identification, overlapping-floor deduplication, preserved snapshots during SQLite locks/corrupt ZIPs, marker removal, timestamp stability, persistence, and disabled experimental endpoints.
- Two saved-map Playwright cases pass on desktop/mobile using copies of real exploration files and a minimal position/header snapshot. This verifies raster terrain, survivor controls, place search, popup/highlight, source switching, coordinate errors and no mod download requirement.
- Real metadata indexing covered 796 cells around known areas without parser errors in the preview. Example searches found a library at approximately 10693,10312 and a police station near 10633,10399, subject to the selected knowledge mask.
- The terrain reader follows Build 42 IsoLot.load directly; it does not use the old temporary lotpack parser whose index/skip behavior differed from the engine. The previous relocation survey is used for a real-coordinate comparison.

Source positions are persisted database values, not live observations. World-file timestamps are not exact per-character save times. Original terrain and category labels do not prove current building condition or available loot.

Production acceptance: both saved-map Playwright tests also passed against http://192.168.1.160:8089 after deployment (6.5 seconds). The API imported all three saved players with no source or indexing errors. The terrain renderer matched 22,240 tiles in the previous relocation survey. HTTPS /healthz returned saved_map mode using the production certificate and LAN address. Before/after Docker container ID, start time, server INI hash and Mods list matched exactly; the game server was not restarted or reconfigured. The app used approximately 125 MiB after indexing.

The pre-deployment observer.sqlite backup and old Compose file are under remote artifacts/before-saved-map-20260913T203219Z. Source snapshots for this mode live in a separate saved-map.sqlite.

### Temporary shared drawing (2026-09-13)

Implemented Ctrl+left-drag with in-memory, five-minute strokes, SSE updates at 100 ms intervals, world-coordinate anchoring, and per-tab clearing. No game files or game configuration changed. Limits and ownership/expiry validation are covered by `tests/test_drawings.py`.

Validation: 23 Python tests passed; frontend production build and Ruff passed. Existing desktop/mobile browser tests passed against LAN deployment. The two-viewer drawing acceptance test passed against both local preview and the deployed gateway: live arrival before mouse release, page reload, ordinary drag panning, per-tab isolation, and removal propagation. The deployment check identifies its own stroke so concurrent users can keep drawing; cleanup deletes only test-owned strokes. HTTPS health and same-origin mutation validation also passed via LAN DNS override.

Only the Observer app container was recreated. Game container remained `a3316a8ef8d9b1229e12c29e4c05e1bfe9c01d1d2ce0fb386e0fafe339bde56a`, started `2026-09-13T17:41:36.875631319Z`, before and after deployment.

### Vehicle eligibility and map labels (2026-09-13)

User selected carried keys as the definition of ownership. There is no manual claim list or hardcoded van exception. API eligibility is the union of matching carried car keys from living saved players, successful hotwiring, and burnt/smashed wreck models, further restricted by selected explored/learned coverage. Current key in ignition, a failed hotwire attempt, prior entry/movement, dead players, and arbitrary key-looking bytes inside other items do not qualify. Key IDs and inventories are excluded from public responses. Unsupported records fail closed; failed key-source reads clear key eligibility.

Verified the vehicle decoder on all 1,111 records in the existing relocation snapshot and all 1,128 live records (zero format errors), including five hotwired vehicles and 356 wrecks at inspection. The player reader successfully walked all three living saved inventories and found 5/13/9 distinct carried car-key IDs, including nested key rings/bags. The Citr8 Step Van matches a carried key.

Validation: 28 Python tests passed, Ruff and production frontend build passed. Tests cover nested inventories, misleading embedded item bytes, truncation/version rejection, dead-player exclusion, key removal/read failure, server-side coverage and eligibility, old unclassified cache entries, key-ID non-disclosure, and read-only SQLite access. Local drawing regression and saved-map desktop/mobile cases passed. Three deployed browser tests passed, including persistent high-contrast public note text, separate keyed/hotwired/wreck SVG colors, layer toggles, and the legend. Visual category fixtures are intercepted only inside the browser test and never written to the service.

Live API after rollout returned 298 vehicles in shared known coverage: 24 keyed, 4 hotwired, and 270 wrecks. Vehicle #2 (Citr8 van) is keyed. Every source reported no error; HTTPS health passed via the LAN DNS override. Only the Observer app container was recreated. The game container ID and startup timestamp remained the same as the drawing rollout above.

### Map update strip (2026-09-13)

Added a 30-pixel status row above the map: latest saved-source age (exact timestamp on hover), most recent source-check age, refresh countdown, and a 3-pixel progress bar. The countdown follows the page's actual 30-second refresh timer, independently of source save timestamps and SSE-triggered refreshes. Loading is indeterminate; failed refreshes and source-read errors are visible. Mobile controls and map bounds sit below the row; long status text can scroll horizontally.

Production frontend build passed. The existing three desktop/mobile and map-symbol browser tests passed locally. Browser checks with an accelerated clock verified countdown movement, unchanged source timestamps, failed-refresh display, and non-overlapping desktop/mobile geometry. Inspected mobile screenshots and verified the deployed strip with real timestamps and no source errors. Game container ID/start time stayed unchanged. Only the web app was recreated.

### Finder distance connector

Selected finder results now have a thin, outlined dotted connector and a small origin dot. Each result captures the exact distance-reference coordinates used for its search, so panning or auto-centering cannot change the displayed reference. Changing reference/coverage clears the previous selection; survivor selection updates the reference and search together.

Frontend build and the two existing desktop/mobile finder tests passed. Browser verification compared the connector's pixel length against the result's world distance after moving the map between search and selection; they matched within rounding tolerance. Changing the reference cleared the old connector. Verified dotted SVG stroke and origin dot on the deployed service. Only the Observer app was recreated; game container/start time remained unchanged.

### Military surplus and weapon-related search (2026-09-14)

Added surplus, gun-store, armory, military-site/storage and hunting categories using room names verified across the installed map headers. Added common search aliases and Weapons/Surplus shortcuts. Weapon/ammo queries search these building categories; results do not assert current loot. Specific surplus/gun-store/armory queries remain narrow, and existing known-area masking still applies.

Validation: all installed headers parsed successfully; new aliases matched equivalent queries and empty coverage returned no results. Current shared coverage produced five surplus results, ten gun-store results, four armory results and one hunting result. The broad weapons/ammo search returns the nearest 20 matches. All 28 Python tests, Ruff and the frontend build passed. Both deployed desktop/mobile finder tests passed; browser checks confirmed the new shortcuts return results and surplus selection draws the existing distance connector. Only the web app was recreated; the game container/start timestamp remained unchanged.


### Persistent death markers (2026-09-14)

Player deaths now have a red, outlined X with a permanent high-contrast “<username> died here” label beneath it. The layer has a toggle, a jump-to-location history list, and popups with death time and coordinates/floor. Labels use text nodes for player names and stay in place on ordinary source refreshes. Recorded markers remain after respawning and web restarts.

The collector reads only the existing `_user.txt` and `_pvp.txt` death records through a new read-only `/logs` mount. Formats and victim coordinates were checked against the installed Build 42.20.4 `IsoGameCharacter.DoDeath` and `PVPLogTool.logKill` classes; player DB rows are reused after respawn, making periodic position samples insufficient. The game container uses UTC and already has `PVPLogToolFile=true`. Non-PvP log records can include animals as “Bob,” so public events require a username found in the saved player DB. Delayed first saves are handled by private candidate records. Raw logs and connection identifiers are not retained; cursor anchors are hashes.

Capture begins at first activation of this feature; no older or imported-save deaths are inferred. Events, registered names, cursor offsets and the capture start persist in Observer's existing `saved-map.sqlite`, with stable event IDs and complete-line processing across rotation, truncation and app restart. Failed reads preserve the last successful history. Death-log file updates do not change the map strip's Saved age.

Validation: 33 Python tests passed, including new death parsing, victim coordinates, animal/account filtering, delayed first saves, exact history after respawn, repeated deaths, log copy/rotation/truncation, partial writes, source-read failures, API projection and source-file immutability. Ruff and frontend production build passed. Six deployed desktop/mobile browser checks passed, including the red X, centered text beneath it, persistent labels, zoom, layer toggles, location buttons, popup details, reload and HTML-safe names, plus existing map symbols/search regressions. Browser death fixtures never enter server state.

Production health passed over LAN and HTTPS using the LAN DNS override. All sources/indexing reported no error. Death collection read 26 log files / 41,205 bytes and registered akryllax, eric and miki; there were no confirmed deaths since activation. The app sees `/game`, `/save` and `/logs` read-only and writes only `/data`. Only the Observer app was recreated; game container ID/start time remained `a3316a8ef8d9b1229e12c29e4c05e1bfe9c01d1d2ce0fb386e0fafe339bde56a` / `2026-09-13T17:41:36.875631319Z`.

The pre-deployment Observer DB backup and Compose file are under remote `artifacts/before-death-markers-20260914T114538Z`.


### Shared pings, saved trips and checkpoint progress (2026-09-14)

Added Shift+click / tap-placement pings with a 12-second expiry and an optional jump-to-location banner. Saved trips support finder destinations, captured distance references, custom map stops, ordered numbered checkpoints, straight-line distance, shared editing, draft persistence, explicit reload/copy after conflicts, route visibility and temporary deletion undo. Shared progress can follow a selected saved player within a configurable radius, or be marked manually. Completed legs/stops are green; remaining legs are blue.

Full travel recording and replay were explicitly postponed by the user. Checkpoint evaluation stores only the trip's current progress and a private last-save fingerprint. It never builds a positional history. The installed `NetworkPlayerManager.update` and `UdpConnection.playerSave` classes confirm a 180,000 ms save interval for connected players, independent of scheduled whole-world saves. Progress starts from a captured fingerprint, accepts only a changed per-player save, checks same-floor proximity to consecutive stops, never interpolates checkpoint visits, and pauses on a recorded death or changed character. Snapshot/Start races are guarded. Reordering stops or changing the followed player resets progress; a rename preserves it.

Validation: all 40 Python tests passed, including seven new planning tests for ordered distance, optimistic edit/delete versions, undo, persistence, bounds, origin checks, ping expiry/rate limiting/identity projection, fresh-snapshot arrival, floor/order checks, unrelated-player writes, restart, death/character changes, corrupt/locked sources and concurrent Start. Source reads leave game files unchanged, and raw player blobs/fingerprints never enter public trip responses. Ruff and frontend builds passed.

All ten relevant browser cases passed across local and production checks: new shared-ping, collaborative-trip and mobile-placement cases; existing drawing, search, marker and death cases. Testing uncovered an HTTP connection-limit regression with two drawing tabs when a third event stream was added. Planning/pings now share the existing world SSE connection; the two-tab live drawing case passed after the change. Browser checks cover ping delivery without forced map movement, expiry, stop reordering, cross-viewer saves/progress, draft conflict handling, explicit reload, page reload, green completed legs, delete/undo and mobile placement. New planning mutations were tested on the isolated preview. Production planning/SSE/rendering checks used a browser-only fixture and did not create trips or pings for live viewers.

Deployment health and planning reads passed over LAN and HTTPS via the LAN DNS override. Existing six read-only production desktop/mobile map/marker/death tests passed. Source/index errors remained empty, death capture's start timestamp was preserved, and game mounts stayed read-only. Only the Observer app was recreated; the game container/start time remained `a3316a8ef8d9b1229e12c29e4c05e1bfe9c01d1d2ce0fb386e0fafe339bde56a` / `2026-09-13T17:41:36.875631319Z`.

Pre-deployment Observer DB/Compose backup: remote `artifacts/before-planning-20260914T122159Z`. Previous Observer image: `sha256:cfc55367240dd72b62637d0fd87ffc931e767421b91474ad108fcab84c560ce2`.

## 2026-09-14 — Planning mode and control remap

Implemented explicit blue planning mode with its sidebar controls first, and a green view mode with fixed waypoint geometry. Ctrl click pings; Ctrl drag paints; Shift click adds an arbitrary point; dragging moves a waypoint; Shift dragging a route edge inserts a point. Right-click removal, clear-all and geometry undo autosave shared changes. The first point is the route start, without an implicit player/reference point.

Autosave uses serialized requests, client trip IDs for retry-safe creation, local recovery, and version conflict handling. Progress-only revisions rebase without losing edits. Routes support 0–32 points; tracking requires two. Stable waypoint IDs preserve unchanged reached points through edits. Existing reached prefixes migrate to ID sets; moving a point makes only that point unreached. Undo restores geometry without rewinding checkpoint progress.

Validation: 43 Python tests passed; Ruff and the production frontend build passed. Fourteen relevant browser checks passed across the final targeted runs, covering desktop and actual touch placement/dragging/pings, gesture thresholds/cancellation, edge insertion, normal-mode locks, hidden-route rendering, clear/undo, same-browser shared drawing, delayed/lost responses, concurrent route/progress updates, reload recovery, finder behavior and existing map/death labels. Screenshots: `artifacts/planning-controls-desktop.png` and `artifacts/planning-controls-mobile.png`.

Pre-deployment SQLite/Compose/container backup: `/home/akr/projects/zomboid-observer/artifacts/before-planner-controls-20260914T130210Z`. There were zero shared trips at backup time. The game mod remains disabled; only Observer is deployed.

Deployed and verified at 2026-09-14 13:07 UTC. Observer app container `51356b8d579b3b33725fdbe4211288cfa39ac7888f9f943e6d0a83a8776eadb0` is healthy; LAN and hostname/TLS health checks passed. Read-only live browser checks confirmed desktop/mobile mode controls, tint, sidebar ordering and view-mode locks with zero browser errors or mutation requests. SQLite quick-check passed; no shared trips were present. The backup also contains `saved-map-before-restart.sqlite`, refreshed immediately before replacement. Game container `a3316a8ef8d9b1229e12c29e4c05e1bfe9c01d1d2ce0fb386e0fafe339bde56a` retained its `2026-09-13T17:41:36.875631319Z` start time. The gateway was not restarted.

## Server position exporter — 2026-09-14

Implemented the optional Java 25 startup exporter and protobuf position receiver described in [server-positions.md](server-positions.md). The previous Observer Lua mod remains disabled. Production started successfully on the unchanged Build 42.20.4 / commit b0bbce05d5. The new feed was verified with successive one-second empty-server snapshots and working RCON; Mods, WorkshopItems, DoLuaChecksum, anti-cheat checksum and game ports match the pre-deployment configuration.

Validation: 51 Python tests, Ruff and the production frontend build passed. Java/Python contract tests use the actual game hook class and verify thread isolation, movement/death/disconnect output, unknown-class rejection, bounded queuing and a slow receiver. An isolated dedicated server with read-only game files successfully exported while empty, and its receiver became stale after the server stopped. The initial version-guard mismatch was fixed: the engine's full version includes the hotfix whereas `getVersionNumber()` does not.

Three new browser checks passed for two-viewer position updates without terrain refetches/panning/additional SSE streams, mobile/desktop stale recovery, and live checkpoint completion. Nine existing saved-map/planning browser cases passed; the coarse checkpoint case requires a fixture containing the opaque `data` column used for saved-position freshness. The minimal terrain-only fixture lacked it, so that case was rerun with a separate complete synthetic fixture. Two read-only saved-map browser checks also passed against the LAN deployment.

LAN and HTTPS ingestion paths return 404 at Caddy. Direct private ingestion requires the exporter token. The gateway now mounts the deploy directory so atomic configuration replacements are visible on reload; its previous single-file mount retained the old inode during the initial deployment check. The app has no published port, Docker socket or RCON credentials. The game and app share only the additional internal position network and their individual read-only token mounts. The game exporter override persists across normal Compose restarts and power recovery.

The game exited cleanly before its save/config backup. Rollback artifacts are in `/home/akr/projects/zomboid-observer/artifacts/before-position-exporter-20260914T175106Z`, including a 178,665,348-byte stopped-world archive, pre-change game/Observer configurations and an SQLite backup. One existing shared trip was retained. Previous app/game images have `*-before-positions:20260914` tags. The precise startup, agent hash and feed samples are recorded in `artifacts/position-agent/deployment.json` locally and `artifacts/position-validation/deployment.json` remotely. Test containers/network and the local preview were removed/stopped.

A real online player's movement still needs the final in-game check. Production was empty at the verification above. Travel recording/replay remains postponed; only last positions and current checkpoint progress are implemented.

## Water and rural roads — 2026-09-17

Corrected terrain classification using the installed Build 42 tile definitions and shovel-ground Lua: water uses `blends_natural_02_0`–`15`, dirt/clay have distinct natural-sheet ranges, and gravel includes street-blend and exterior-ground sprites. Exterior streets no longer appear as interior floors. Water is blue; dirt/gravel is tan and has its own legend entry. Structures, floors and vegetation retain precedence over ground surfaces, including water beneath bridges/decks. Sand and grass are not classified as dirt roads.

Overview tiles now average each already-masked known block instead of taking a single nearest-neighbor sample, which could miss a whole narrow rural road or stream. No filtering crosses an unknown block boundary. A renderer version invalidates both persisted PNGs and the public terrain revision, so open viewers also refresh after an update.

Validation: 87 Python tests, Ruff and the frontend production build passed. Regressions cover actual installed water/shoreline/dirt/gravel stacks, bridge/floor precedence, road/stream survival at every overview zoom, hidden neighboring blocks, empty coverage, and persistent/browser cache invalidation. Real explored cells around Muldraugh and the northern river were rendered before/after; comparison artifacts are in `artifacts/terrain-fix/`. Both desktop/mobile saved-map browser checks passed locally.

Pre-deployment source/container backup: remote `artifacts/before-terrain-20260917T194158Z`. The old app image is tagged `zomboid-observer-app:before-terrain-20260917T194158Z`.

Deployed at 19:43 UTC. Desktop/mobile browser checks passed against production. Live PNGs show water at (10767, 9798) and (11008, 10245), and dirt/gravel at (10755, 9729) and (11015, 10216). All 57,252 unknown pixels in the tested overview tile remained masked. LAN/HTTPS health, Docker health, SQLite integrity and source/index status passed; both shared trips were retained and the position feed stayed live for both online players. Only Observer's app container was replaced. Game container `32d07645d5d0e4eed7e9bfb7e0512a4d4d7d0d3713eacf0c874c1844a4ce28ec` kept its `2026-09-14T17:54:36.784480577Z` start time.

## Exact exploration updates — 2026-09-17

Traced the delayed map to the game save boundary, not the browser: at 19:54 UTC the files still held the 19:00 exploration snapshot, while positions continued updating every second. The 20:00 full-world save immediately added 220 shared known blocks. `WorldMapVisitedServer` updates its in-memory masks during play but saves its ZIPs on world save/logout.

Added a separate read-only exploration adapter/worker to the existing server-only startup agent. It copies the game's exact packed mask, including visited and learned flags, one online player every two seconds in round-robin order. No proximity inference, reveal commands, client code, game-file edits or travel history are involved. The authenticated protobuf receiver retains each player's latest full mask and prefers it over an older ZIP; newer saves and deliberate clearing remain authoritative. Only changed effective coverage invalidates tiles/search. An exploration status indicator and event share the existing browser connection.

Validation: 93 Python tests, Ruff, production frontend build, and six browser checks passed. The new two-viewer browser case covers automatic coverage/tile refresh without a reload or pan, per-player isolation, unchanged-mask caching, mobile stale status and recovery. Python checks cover mask replacement, old/new save precedence, hidden pixels/search, persistence, replay/session guards, input/compression/known-block limits and unchanged game files.

Java contract tests use the actual installed exploration/hook classes and detached synthetic masks at real-world dimensions. Normal, slow-position and slow-exploration receivers pass; map uploads do not block positions or game-thread work. Wrong-thread access, array aliasing and unknown class hashes are rejected. The isolated dedicated server started successfully with the installed files read-only and its own save/network, exported successive empty-server position/exploration heartbeats, and became stale after shutdown. Its native startup property warnings also occur in the earlier baseline. Production's private exploration route returns 404 through HTTPS.

Rollback/source/image/database artifacts: remote `artifacts/before-exploration-20260917T201130Z`; test logs are in local `artifacts/exploration-fix/` and remote `artifacts/exploration-validation/`. Candidate agent SHA256: `e94588221c86694e2c1692e718aba7a5e0a952d44adb570574a16f9c90e80bea`. A one-off `pinned-start.yaml` launches the installed game directly for this rollout, avoiding a SteamCMD game upgrade; the normal game Compose files remain unchanged.

Production activation completed after a successful RCON save and clean game shutdown (exit 0). The stopped-world archive is 198,905,249 bytes. Game container `b1b12c6fede4e7a7e8172020f527c6b5536e3d1631791f40795e2ce397de7357` started at `2026-09-17T20:25:55.553993164Z` on the installed Build 42.20.4, with restart policy `unless-stopped`. All game INI key/value settings match the stopped backup; startup rewrote formatting only. The previous Lua Observer mod remains disabled. The one-off entrypoint survives restarts of this container; a later normal Compose recreation resumes the usual SteamCMD launcher unless the pinned override is included again.

Verified real online masks for both akryllax and eric at about four-second intervals. By 20:34 UTC, akryllax's mask increased from 4,785 to 4,787 known blocks and eric's from 7,556 to 7,559, while both saved ZIP/source timestamps remained at the pre-restart save. Coverage revisions advanced with these discoveries, independently of world saves. Both feeds stayed live and source/index errors remained empty. The two read-only production desktop/mobile browser checks passed, and the deployed status strip shows Live exploration without browser errors. LAN/HTTPS health, private-route exclusion and SQLite integrity passed; both existing shared trips were retained. Test containers/network and the local preview were stopped/removed.


## City and town labels — 2026-09-23

Town anchors and English names are read from the installed `worldmap-annotations.lua` text-town entries and `MapLabel.json`, without executing Lua. All 12 current towns are supported, including Build 42 additions. Missing annotation files disable this layer without breaking maps; absent translations fall back to the annotation key's words.

The coverage API returns city labels and their mask atomically. Only anchors in the selected observer's visited/learned blocks are included, so hidden city names and coordinates never enter the browser payload. An SVG layer clips the lettering to those same known blocks, sits beneath routes and markers, and does not intercept clicks or dragging. Its City names checkbox is enabled by default. Coverage changes redraw labels without moving the map. Switching observers clears labels immediately, including while a request is pending; zoom animations temporarily hide them until their clip geometry is rebuilt.

Validation: 50 Python checks covering the new annotation/coverage behavior and existing saved-map/exploration behavior passed. TypeScript/Vite build, Ruff and whitespace checks passed. Five relevant browser cases passed: fog-boundary pixels, exploration refresh without panning, observer/layer selection, zoom, dragging, mobile labels, existing saved-map search/navigation, and marker styling. The marker check now uses a browser-only note fixture so duplicate player-authored names cannot invalidate its single-note assertion. Visual previews and the pre-change files are retained under `artifacts/city-labels/`.


## Camera follow — 2026-09-23

Each survivor has a Follow toggle. This browser-local camera preference consumes the existing position frames and never changes shared trip/checkpoint settings or adds an exporter/stream. The map pans to changed positions at its current zoom, honors reduced-motion preferences, and recenters after zoom/resize. The on-map status distinguishes live, offline, last-seen and saved positions; following remains selected through outages. Disappearing survivors clear the selection.

Dragging, keyboard panning, box zoom and explicit navigation release the camera. Search/coordinate/marker jumps, Fit all, route-stop jumps, Fit route and clicked shared pings all release Follow before navigation. The Follow control closes mobile controls and leaves a visible Stop button above the map. The existing survivor focus button still performs a one-time jump.

Validation: TypeScript/Vite build and whitespace checks passed. Three camera browser cases verified streamed movement, zoom, delayed/offline recovery, switching survivors, manual drag, coordinate navigation, Fit all and mobile Stop. Existing city-label and saved-map desktop/mobile cases passed, and the planner's arbitrary-waypoint/move/insert/remove/undo case passed on the isolated preview. Browser fixtures deliver position frames locally without changing live players or saves. Screenshots and pre-change source are under `artifacts/camera-follow/`.

### GPS road helper (2026-09-23)

The existing planner now offers From/To generation, editable bend checkpoints, known-road A* with paved preference, and shared automatic rerouting (default on). Moving/reordering a bend protects it as a manual stop. Recalculation retains reached/manual checkpoints and checks the trip version before committing. Stale positions, changed connections, unknown endpoints and disconnected roads cannot invent arrivals or overwrite a trip. Clear/undo, legacy clients, shared editing and the 256-point limit are covered.

All 111 Python tests passed. Four GPS browser cases and fifteen existing planner/Follow/city/marker/saved-map cases passed. Tests cover route replacement, failed/obsolete results, manual edits, undo, mobile map picks and fog selection; planner mutations used the isolated local snapshot. A grid-boundary regression test covers integer endpoints in every direction. Original terrain tests verify that water, structures and unmapped pavement cannot become road shortcuts, while mapped bridge flooring is eligible.

The local Rosewood case produced 43 checkpoints over 4,451.8 road tiles; a West Point case correctly reported no connected route in the current known-road graph. Cold indexing is bounded and resumes from cached masks. During a cold 26-second search, local health requests had a 1.09 ms median and a 75.62 ms maximum. The measured route process peak was about 164 MiB.

Deployed Observer image `sha256:bf97303c8b86ce82544ee4ea343a5c2466e20b1ce1bd44b8932b06fc93dd975d` at 21:52 UTC. The live short route returned nine checkpoints, 207.8 road tiles and 38 access tiles in 1,293 ms of worker time; concurrent health requests had a 2.23 ms median. LAN and HTTPS health and HTTPS same-origin routing passed. Both feeds remained live, source/index errors were empty, SQLite quick-check passed, and all four pre-existing trips retained their exact waypoint data. Read-only production desktop/mobile checks verified the helper, city picker, default checkbox and layout with no browser errors or shared mutations.

Only Observer's app container changed. The game retained container `b1b12c6fede4e7a7e8172020f527c6b5536e3d1631791f40795e2ce397de7357` and start time `2026-09-23T19:04:31.138869388Z`; the Compose override was unchanged. Source/database backup and rollback image are recorded under `/home/akr/projects/zomboid-observer/artifacts/gps-20260923T215016Z`. Rolling back to the old image requires its matching database backup because GPS adds a trip column. Local reports/screenshots are under `artifacts/gps/`.
