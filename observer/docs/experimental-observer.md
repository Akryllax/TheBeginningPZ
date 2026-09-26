# Zomboid Observer

An independent, read-only web service for the group's last observed Project Zomboid world. Built for the `AKR_Exploratory` server on Build 42.20.4 (save version 249).

- Public viewer: **https://map.lofers.net:8443**
- LAN viewer: **http://192.168.1.160:8089**
- Server project: `/home/akr/projects/zomboid-observer`
- Source project: `/var/home/akr/Projects/zomboid-observer`

The renderer reconstructs procedural 3D objects from observations, with perspective/isometric cameras, floor selection, cutaway, layer controls, public marker flags, survivor locations, and clickable contents/mechanics inspection. Geometry is an approximation, not original game art. A shaded 32×32-tile grid represents imported exploration; those files cannot recreate historical buildings, vehicles, or containers. Detailed geometry starts when a player runs the exporter.

## Install the player mod

Download `/downloads/ZomboidObserver.zip` from the viewer. Extract the `ZomboidObserver` folder to:

- Linux: `~/Zomboid/mods/`
- Windows: `%UserProfile%\Zomboid\mods\`

The resulting path ends in `mods/ZomboidObserver/42/mod.info`. Exit and restart the game, then join the server. Every connecting player needs this manually distributed mod because it is enabled in the server's `Mods=` setting. No Workshop item is published.

**Published data:** everyone’s observed world geometry, names/locations/headings of online survivors, selected/opened world-container contents, inspected vehicle parts, and map symbols shared to everyone. Personal inventories, nested bag contents, and private/group-only annotations are excluded. The viewer is public: do not use everyone-sharing for a private annotation.

## Run the web service

The game can continue running while the web containers are rebuilt or stopped.

```bash
cp .env.example .env
mkdir -p data secrets
chmod 700 secrets
# Create secrets/porkbun.env with PORKBUN_API_KEY and PORKBUN_API_SECRET_KEY.
chmod 600 secrets/porkbun.env
docker compose up -d --build
```

Set `OBSERVER_UID`/`OBSERVER_GID` to the owner of `data/`. On lofnet these are 1001. Pre-create the game's `Lua/ZomboidObserver` directory with its game user's ownership. App source mounts are read-only; only its own SQLite database is writable.

DNS for `map.lofers.net` must resolve to the server's public IP. Forward **TCP 8443 → 192.168.1.160:8443** on the router. Existing game UDP ports are separate. LAN port 8089 is bound to the private address; it does not need a router forward. Caddy gets and renews certificates through Porkbun DNS TXT challenges, without using ports 80 or 443.

The domain's wildcard ALIAS synthesized a CNAME for the ACME challenge name. A persistent TXT record `_acme-challenge.map.lofers.net = zomboid-observer-acme` overrides that wildcard at this exact name; Caddy adds/removes a separate temporary TXT value for validation. Keep the placeholder for renewal. No A, ALIAS, or DDNS configuration is managed by this project. Caddy's challenge resolver/timeout options follow its [TLS documentation](https://caddyserver.com/docs/caddyfile/directives/tls).

## Operations

```bash
docker compose ps
docker compose logs --tail 60 app gateway
curl http://192.168.1.160:8089/healthz
curl --resolve map.lofers.net:8443:192.168.1.160 https://map.lofers.net:8443/api/v1/world
```

The health endpoint reports API availability. Check `online`, `heartbeat_at`, `collector_error`, `gap`, and `observed_tiles` in `/api/v1/world` to verify recording. Empty-server pause can leave recording status offline; the existing snapshot remains readable. During play, client pulses drive roughly one-second position heartbeats. Detail refresh depends on visibility, scan budget and movement; it is not guaranteed to be instantaneous.

The exporter rotates four 16 MiB journals in the game's `Lua/ZomboidObserver/` directory. JSON Lines content uses `.txt` because the game restricts writable extensions. The collector reads complete lines every 500 ms, checkpoints offsets, validates records and retains the latest snapshot in `data/observer.sqlite`. If the viewer is offline long enough for all four journals to be overwritten, missed changes are not recoverable; a sequence-gap notice appears. Observing an area again refreshes it.

Back up the independent database with SQLite's backup API, or stop the app while copying `data/`. Do not copy a live SQLite database without its WAL. Game save backups are separate. For a new/wiped world, archive the viewer data and journals; use an empty data directory rather than mixing two worlds.

## Enable/disable the server mod

The production installation is under `/home/akr/projects/zomboid/data/Zomboid/mods/ZomboidObserver`; `ZomboidObserver` is appended to the profile's `Mods=` list. Other mod IDs and Workshop IDs remain as configured. Before changing it, ensure everyone is logged out, save and gracefully quit via the existing RCON helper, and wait for the container to exit. Back up the server INI and any previous mod directory, install, then restore its restart policy and start it. Never forcibly stop the game during a save.

Rollback: gracefully stop the game when empty, remove only `ZomboidObserver` from `Mods=` (and `PZ_MOD_IDS` if set), remove its mod directory, and restart. Stop this project with `docker compose down`; retain `data/` and Caddy volumes. The exporter never edits the game world/save. Its journal directory can be archived after disabling it.

## Development and verification

```bash
uv sync --dev
uv run pytest -q
uv run ruff check observer tests
luajit tests/client_harness.lua
luajit tests/server_harness.lua
npm ci --prefix web
npm run build --prefix web
python3 scripts/package_mod.py
```

Browser tests use a separate, clearly labelled fabricated demonstration database:

```bash
uv run python scripts/demo.py
OBSERVER_WORLD=Observer_Demo OBSERVER_DATA=artifacts/demo uv run uvicorn observer.app:create_app --factory --host 127.0.0.1 --port 8765
# Another terminal:
npm test --prefix web
```

Never import the demo into the production database. `scripts/import_maps.py` can bootstrap historical coverage from runtime metadata exported by an isolated server using the identical build and Map configuration; run it with the collector stopped, `PYTHONPATH=.`, and explicit paths. Live startup metadata normally makes manual bootstrap unnecessary.

See [architecture](docs/architecture.md) and [validation](docs/validation.md) for boundaries and the remaining real-client acceptance checks.
