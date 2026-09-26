# Zomboid Observer — shared saved map

> This is the copied Observer source for the separate **AKR_DayOne** project.
> Use the [project README](../README.md) and `../dayone` for deployment; the
> `.160` addresses and commands below describe the original production instance.
> The new optional local diagnostic feed is documented in
> [storyteller diagnostics](docs/storyteller-debug.md).

A public 2D map for `AKR_Exploratory`, using installed Build 42 terrain and the server's saved exploration, positions and public markers. **No game mod or client installation is required.** The game server can keep running during web-service updates.

An optional [server-only exporter](docs/server-positions.md) adds live online-player positions, faster automatic checkpoints, and exact exploration updates through Protocol Buffers. It needs a game restart to load or upgrade the Java agent; players install nothing. Terrain uses installed map files. Exploration uses the game's in-memory visited/known masks when available, with saved files as a fallback. The old Observer Lua mod stays disabled, and travel recording/replay remains postponed. The linked guide covers deployment, rollback and validation.

- LAN: **http://192.168.1.160:8089**
- HTTPS: **https://map.lofers.net:8443**
- Remote project: `/home/akr/projects/zomboid-observer`
- Local source: `/var/home/akr/Projects/zomboid-observer`

## Using the map

Pan, zoom, enter X/Y coordinates, or click a survivor to center on their latest available position. Live, offline and last-seen positions are labeled when the optional exporter is enabled; otherwise positions come from saves. The coverage selector shows everyone's combined knowledge or one player's knowledge. Both visited areas and places learned from maps/books are visible. Unknown 32×32 blocks remain hidden in the server's image responses, not just underneath a browser overlay.

Search for **library, bookshop, mechanic, garage, builder, hardware, warehouse, supermarket, police, medical, pharmacy, gas station, fire station, restaurant, military surplus, gun store, armory, hunting, weapons, ammo** or related words. “Builder” includes hardware shops, construction/work sites and storage buildings. Residential garages are labelled separately. Weapons/Surplus shortcuts cover gun stores, military surplus, armories (including police/prison weapons storage), military sites and hunting sections. “Army surplus” and “military surplus” are equivalent; “guns,” “firearms,” “weapons,” “ammo,” and “ammunition” search all weapon-related categories. Specific “gun store” or “armory” searches stay narrow. These are mapped room types, not reports of available weapons or ammunition. Search returns up to 20 nearest matches within selected map knowledge. Distances use the selected survivor's latest available position, or the map center. Clicking a result highlights the known part of its matching rooms and draws a thin dotted line back to the distance reference, with a small origin dot. The line uses the same coordinates as the search distance, including the map center at the time of searching; panning does not move that reference. Changing the distance reference or map-knowledge selection clears the old line.

Building labels describe installed room types, not guaranteed loot or named businesses. Different floors/cells with overlapping matching footprints are merged. The map shows original ground-floor terrain, not player construction, destruction, live zombies or a verified route through doors. Source timestamps are file modification times; exact per-player save times are unavailable, and online status requires the optional position exporter. Vehicle pins show only cars with a matching **car key carried by a living saved player** (green), **hotwired cars** (amber), and **burnt/smashed wrecks** (red). Keys inside vanilla key rings and nested bags count; dropped keys, keys in vehicle cargo/ignition, dead characters' keys, merely entering/moving a car, and failed hotwire attempts do not qualify. All eligible vehicles must also be inside the selected map coverage. The union of all living players' carried keys applies regardless of coverage selection. This does not establish formal ownership or personal observation of every vehicle.

Use **Follow** beside a survivor to keep this browser’s camera centered on their latest position, at the current zoom. Live positions move the camera about once a second; saved or offline positions are identified in the on-map status. Following stays selected during an outage and resumes as fresh positions arrive. Zooming keeps Follow active. Dragging/panning the map, fitting a route, or jumping to a search result, marker, ping or coordinates stops Follow; the status pill also has a **Stop** button. Camera following is local to your page and does not change shared trip/checkpoint tracking.

City and town names use the installed game’s official annotation positions. A name appears when its anchor’s 32×32 block is visited or learned by the selected map-knowledge source; the lettering is also clipped wherever fog still covers the map. Labels update with exploration and coverage selection, stay beneath other map symbols, and can be hidden with **Layers → City names**.

Public map notes have persistent, high-contrast text labels and purple pins; survivors remain blue. The map legend explains the colors. Vehicle tooltips/popups explain why a car is visible.

Player deaths recorded from this feature's first activation appear as a **red X with “<username> died here” underneath**. The label stays visible without hovering. Click the X for death time and coordinates/floor, or open **Layers → Death locations** to jump to a marker. The Death locations checkbox hides/shows the layer. Markers are shared by all viewers and persist after respawning and Observer restarts; they mark the original death location, not a moving body or reanimated character. There is no automatic expiry.

Death locations come from the server's existing death logs, checked every 30 seconds, independently of character-save timing. Player accounts are checked against the save to exclude animal/NPC names. Both non-PvP and PvP deaths are supported (the server's `PVPLogToolFile` setting is already enabled). Earlier deaths and deaths from the imported save are not reconstructed. The start date is shown in Death locations. Missing logs retain existing markers and report a source error.

Every 30 seconds the collector reads the newest available saved data. Rechecking an unchanged file does not make it newer. The game's exploration ZIPs update on full-world saves or logout; production's hourly backup/save job can leave them almost an hour behind connected players. The optional exporter removes that delay by copying one player's exact visited/known mask every two seconds, rotating through online players (about four seconds with two players). It reveals no areas based on inferred proximity or loaded chunks. Masks persist in Observer; newer snapshots replace older masks, including deliberate map clearing. An older disk save cannot roll back a newer live mask, while a newer save after logout can supersede it. The exporter does not force saves or alter the save schedule. A changing, locked or invalid source normally retains its last successful snapshot and shows an error in Source Status. Car-key reads fail closed: if they cannot be validated, cars requiring a carried key are hidden until a successful read.

The thin row above the map shows live/delayed exploration separately from live positions, plus the latest saved source-file age, the latest source check, and a progress bar counting down to this page's next refresh (every 30 seconds). Without the exploration feed it shows the exploration-file age explicitly. Hover over these fields for details. The countdown does not force or predict a game save. Source errors and failed refreshes appear in the same row; narrow screens can scroll the status text horizontally.

## Running and updating

```bash
# First setup only:
cp .env.example .env
mkdir -p data secrets
chmod 700 secrets
# Supply secrets/porkbun.env with PORKBUN_API_KEY and PORKBUN_API_SECRET_KEY.
chmod 600 secrets/porkbun.env

docker compose up -d --build
```

Compose selects `saved_map` mode and mounts `zomboid_pz-server-files` read-only at `/game`, plus the game save read-only at `/save` and server logs read-only at `/logs`. `OBSERVER_LOGS=/logs` enables death collection; omit it for previews without game logs. Log timestamps are interpreted as UTC, verified against this game container. It writes only its own `data/` directory. No Docker socket or RCON credentials enter the app. UID/GID 1001 own the service data on lofnet.

Existing Caddy HTTPS and DNS TXT certificate renewal remain in place. TCP **8443 → 192.168.1.160:8443** must be forwarded on the router for WAN access. DNS must resolve `map.lofers.net` to the public IP. LAN port 8089 needs no forwarding. Keep the existing `_acme-challenge.map.lofers.net` placeholder TXT record for renewal; it prevents the domain's wildcard ALIAS from confusing the DNS challenge. This project does not update A/ALIAS/DDNS records.

```bash
docker compose ps
docker compose logs --tail 50 app
curl http://192.168.1.160:8089/healthz
curl --resolve map.lofers.net:8443:192.168.1.160 https://map.lofers.net:8443/api/v1/world
```

`data/saved-map.sqlite` persists source snapshots and death history independently of the old `observer.sqlite`. Death collection uses durable cursors with hashed anchors, complete-line reads and stable event IDs to handle log appends, rotation, truncation and restarts. Parsed death candidates remain private until matched to a saved player account. Raw logs, connection identifiers and combat details are never stored or served. The terrain PNG disk cache is capped at 512 MiB; in-memory rendered tiles are limited to 256. Terrain and place indexes refresh when known areas or installed metadata change. Initial indexing and first-time rendering may take longer than cached requests.

Back up SQLite with its backup API, or stop only the app before copying data. Do not copy a live database without its WAL. On a world reset, archive the saved-map DB and initialize a fresh one; historical data must not carry into a different world. Build 42.20.4 bounds and save version 249 are validated; another format requires adapter review.

## Public API

Game information routes are read-only. Shared trips, checkpoint progress, pings and temporary drawings use web-only mutation routes and never write to game files. Coordinates are Zomboid world X/Y; Leaflet uses `[-Y, X]` internally.

- `/api/v1/world`: saved-map mode, revisions, players, public markers, `deaths` (username, location, UTC epoch-millisecond death time), `death_markers_since`, per-source modification/check times and errors.
- `/api/v1/coverage?observer=akryllax`: known 32×32 blocks and `city_labels` whose anchors are known, in one consistent snapshot; omit observer for everyone. Hidden city names and coordinates are excluded.
- `/api/v1/map/tiles/{zoom}/{x}/{y}.png?observer=...`: masked 256-pixel tiles, zoom −4 through 3, one pixel per world tile at zoom 0.
- `/api/v1/map/features?x=10632&y=9913&radius=512&observer=...`: eligible saved vehicles within the viewport and selected coverage, with category `keyed`, `hotwired`, or `wreck`. Raw key IDs and inventories are never returned.
- `/api/v1/places/search?q=builder&x=10632&y=9913&observer=...&limit=20`: known destinations, labels, coordinates, floor, distance and clipped room rectangles.
- `/api/v1/events`: world revision notifications plus shared trip/progress/ping, live-position and exploration-feed status updates on the same connection; the browser also checks saved status every 30 seconds. Coverage changes invalidate tiles/search without panning; unchanged exploration snapshots do not refetch tiles.

Experimental journal ingestion, object inspection and mod downloads are disabled in saved-map mode. The older 3D implementation and mod source remain for future work; **do not re-enable the game mod** as part of a web deployment. Its previous real-client bugs still require validation. See [experimental notes](docs/experimental-observer.md) and [pause record](docs/PAUSED.md).

## Tests

```bash
uv sync --dev
uv run pytest -q
uv run ruff check observer tests
npm ci --prefix web
npm run build --prefix web
# With a saved-map preview running on port 8766:
npm test --prefix web -- saved.spec.ts
# Or point the read-only browser acceptance test at the LAN deployment:
SAVED_MAP_URL=http://192.168.1.160:8089 npm test --prefix web -- saved.spec.ts
```

The browser tests use real saved-map data containing akryllax/miki and known garages/supply sites. Unit tests use synthetic sources, including corrupt files and SQLite contention. The original 3D browser cases remain separate and require the labelled demo on port 8765.

## Temporary shared drawing

Hold **Ctrl + left mouse button** and drag over the map. Strokes appear for other viewers while you draw, stay anchored when panning/zooming, and expire five minutes after their last update. **Clear my drawings** removes strokes created in this browser tab (including after reloading). Ordinary dragging still pans; Escape or losing focus ends a stroke. Live drawing requires an active connection.

Drawings exist only in the web app's memory and disappear on app restart. They are shared across all map-knowledge selections. No game mod or client code installation is needed. Limits: 32 strokes per tab, 256 globally, 1,024 points per stroke, 64 KiB per upload. The anonymous random owner capability is kept in session storage and excluded from broadcasts.

Drawing endpoints: `POST /api/v1/drawings`, `DELETE /api/v1/drawings/{owner}`, and `GET /api/v1/drawings/events` (SSE, changes delivered at 100 ms intervals). New/reconnecting viewers receive the current strokes; expiry and deletion propagate to everyone. Requests to mutate drawings must be same-origin when an Origin header is present.

## Saved vehicle and key decoding

The readers follow the installed Build 42.20.4 / save-version-249 `BaseVehicle`, `VehiclePart`, `IsoPlayer`, `IsoGameCharacter`, `InventoryItem`, and `InventoryContainer` serialization. They walk explicit record boundaries; arbitrary byte-pattern searches do not establish hotwiring or key possession. Nested item/entity blobs are bounded and size-delimited, and unsupported vehicle formats are hidden. Car keys are identified through `WorldDictionaryReadable.lua` and the installed vanilla container definitions at `media/scripts/generated/items/container.txt`. Custom mod container/key subclasses require an adapter before their keys can count. A missing/invalid dictionary, malformed supported inventory, or unsupported player version clears the key eligibility snapshot instead of keeping an old claim. The official [vehicle API](https://projectzomboid.com/modding/zombie/vehicles/BaseVehicle.html) documents the hotwired state; exact binary layouts were verified against the locally installed game classes.

Internal Observer snapshots contain only decoded vehicle flags and a union of carried key IDs; full inventory blobs are read transiently and never saved by the collector or served by the API. Historic vehicle cache entries lacking eligibility fields are hidden immediately on upgrade. Key-based eligibility follows the latest saved character inventories, so picking up or dropping a key becomes visible after the character saves and the next collection cycle.


## Shared pings and saved trips

**Ctrl + click** sends a shared “Look here” ping; **Ctrl + left-drag** paints for everyone watching. A six-pixel movement threshold separates the gestures, so a drawing does not also ping. Pings expire after 12 seconds and drawings after five minutes. On touch screens, press **Place a ping**, then tap anywhere on the map. Pings never automatically move another viewer’s map. Each browser tab can move its current ping at most once per second.

Use **Plan trip** to enter the blue planning mode. Its controls move to the top of the sidebar. **Done** returns to the green normal mode and locks waypoint editing; shared updates and checkpoint progress continue arriving.

- **Shift + click** adds an arbitrary ground-level waypoint. The first click is the start; no player position or building is inserted automatically.
- **Drag a waypoint** to move it. **Shift + drag a connecting line** inserts a waypoint between its endpoints. Escape cancels an unfinished move or insertion.
- **Right-click a waypoint** to remove it. Visible Remove buttons, **Clear all waypoints**, and **Undo edit** are also available. Clearing retains the named shared trip; undo restores geometry as another shared edit, without restoring historical checkpoint progress.
- **+ Map stop** supports click/tap placement. Reference and finder shortcuts remain optional, available while planning. Stops can be reordered in the list. **Fit route** frames the route; distance is horizontal straight-line distance in tiles.

Completed edits autosave for everyone. New trips get a timestamped name and are created on their first waypoint. Text edits save after a short pause. Saving and sync status remain visible beside the mode button. Unsynced work survives reload locally; simultaneous route edits stop autosave and offer **Reload saved** or **Save as new**. Progress-only updates are rebased automatically. Deleting an entire saved trip has a separate **Undo delete** action lasting ten minutes or until an Observer restart. Limits: 100 shared trips, 0–256 waypoints each, an 80-character name, and a 128 KiB request body. Tracking needs at least two points.

### GPS road helper

In **Plan trip**, set **From** and **To** using coordinates, map picks, players, known city names or the existing place search. **Fill route** creates editable checkpoints at road bends; **Replace route** replaces the current plan in one undoable edit. The selected map knowledge limits generation. A failed calculation leaves the current trip intact. Road distance and dashed amber, unverified access connections (up to 100 tiles) are shown separately. Driving endpoints must be on the ground floor.

**Auto reroute** defaults on for generated routes. Choose the trip's tracked player and **Start / resume** to navigate. After three fresh observations more than 30 tiles off route, or beyond a missed generated bend, Observer recalculates toward the remaining manual destinations, at most once every five seconds. Moving or reordering a generated checkpoint makes it manual and protects it. Completed checkpoints and manual stops survive rerouting. With the checkbox off, use **Recalculate remaining route** explicitly. Position outages, offline players and changed characters/connections cannot trigger reroutes. Camera Follow is independent, and no travel recording is added.

Shared routes retain the knowledge selection used to generate them; another viewer's selection only changes visibility. Generated pins and road geometry are hidden outside that viewer's knowledge. Old straight-line trips retain their behavior. Clear removes GPS settings along with the points; undo restores the edit without rewinding checkpoint progress.

The routing worker reads the installed `worldmap.xml.bin` v2 (256-tile cells), intersects non-trail road geometry with original terrain surfaces, and runs A* with a 2× dirt/gravel cost. It cannot detect current vehicles, wrecks or player-made obstructions. Known terrain alone is not sufficient: the roads must connect in the installed map. Search is capped at 500,000 expansions/five seconds of search time; cold terrain indexing runs in bounded slices with a preparing state. One worker process and at most eight queued jobs keep calculation away from the HTTP/position handlers. Road masks use a 24 MiB memory cache and a 128 MiB disk cache under `data/roads`.

- `POST /api/v1/routes` accepts `stops` (two or more endpoint objects with stable IDs) and optional `observer`. Repeat the same request while `status` is `preparing`/`busy`; `ready` returns generated stops, geometry, distances and a `request_id`. It does not save a trip.
- Trip writes accept optional `routing: {observer, auto_reroute, request_id}` and stop `kind` (`manual`, `generated`, `origin`). Geometry is calculated by the server; edited plans recalculate connections in the background. Omitted routing settings from older clients preserve an existing route.
- `POST /api/v1/trips/{id}/reroute` accepts the current `version`. Shared updates use the existing planning events, with optimistic version checks before applying worker results.
- Deployment adds a nullable SQLite `trips.routing` column. Rolling back to a pre-GPS image also requires restoring the matching pre-deployment SQLite backup because older versions use positional trip inserts. Game saves are unaffected.

### Checkpoint progress

In planning mode choose **Follow player** and an arrival radius (default 40 tiles, range 10–200); after autosave, press **Start / resume**. Start treats stop 1 as the departure point. Unreached stops resolve in route order when a fresh saved position is within the chosen radius on the same floor. Completed legs and stops turn green, and progress is shared with other viewers. **Mark next reached**, **Pause**, and **Reset progress** provide manual control. Waypoints have stable identities. Reordering or renaming keeps their reached status; new or moved points need reaching again. Removing a point leaves the others intact. Active tracking continues in route order, skipping points already reached. Green legs connect two reached endpoints. Clear all or fewer than two points pauses tracking. Changing the followed player and explicit Reset still reset progress. Resume never automatically marks a moved departure reached.

The installed Build 42.20.4 `UdpConnection.playerSave` timer is 180,000 ms, consumed by `NetworkPlayerManager.update` for fully connected players. This runs independently of `SaveWorldEveryMinutes=0`. Observer checks every 30 seconds, so checkpoint detection usually follows a roughly three-minute character save plus collection delay. A brief visit between saves may be missed. Only the next checkpoint(s) near an actual recorded position count; crossing a connecting line never proves a visit. The last accepted player-save fingerprint prevents unchanged/offline coordinates or another player's save from advancing a trip. A recorded death or changed character pauses automatic progress. Start/resume waits for a new save instead of treating an old position as a new arrival.

With the optional position exporter, arrivals use one-second online snapshots. Start/resume waits for the next fresh sample, outages wait without falling back to old saves, and reconnects/character replacement require resuming. The status row shows live/stale state separately from saved terrain updates. Live updates do not reload terrain or pan the map.

**Travel recording and replay are postponed.** Automatic checkpoints retain only current trip progress and a private last-sample fingerprint, with no positional trail, per-player movement history, or raw inventory archive. The default saved-file mode needs no game restart. Activating the optional Java exporter requires one startup; it leaves game saves, Lua and the client mod list unchanged.

Planning routes:

- Trip stops include a stable `id`; tracking includes `reached_stop_ids`. The legacy `completed` field is the contiguous reached prefix. Existing trips migrate in place. POST accepts an optional client-generated trip `id` for retry-safe creation.
- `GET /api/v1/trips`, `POST /api/v1/trips`, `PUT /api/v1/trips/{id}`, `DELETE /api/v1/trips/{id}?version=N`.
- `POST /api/v1/trips/{id}/progress` with `version` and `action` (`start`, `pause`, `next`, `reset`); `POST /api/v1/trips/{id}/restore` for temporary undo.
- `GET /api/v1/pings`, `POST /api/v1/pings` (owner capability + X/Y; the capability is excluded from public responses).
- The existing `/api/v1/events` emits `planning` snapshots on changes, checked every 250 ms. Snapshot responses include public trip data and active pings, never private character fingerprints. Same-origin checks apply to mutations.
