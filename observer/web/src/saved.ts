import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "./saved.css";
import { enableSharedDrawing } from "./drawing";
import { enableRefreshStatus, type PositionFeed, type ExplorationFeed } from "./refresh-status";
import { enablePlanning } from "./planning";
import { enableCityLabels, type CityCoverage } from "./city-labels";
import { enableCameraFollow } from "./camera-follow";

type Player = {
  id: number | string;
  name: string;
  character: string;
  x: number;
  y: number;
  z: number;
  source?: "live" | "last_seen" | "saved";
  online?: boolean | null;
  observed_at?: number | null;
};
type Marker = {
  id: string;
  label: string;
  author: string;
  x: number;
  y: number;
  z: number;
  color: string;
};
type Death = { id: string; name: string; x: number; y: number; z: number; occurred_at: number };
type Source = { modified_at: number | null; checked_at: number; error: string | null };
type World = {
  mode: string;
  world: string;
  revision: number;
  coverage_revision: number;
  terrain_revision: number;
  indexing: boolean;
  index_error: string | null;
  observers: string[];
  players: Player[];
  position_feed?: PositionFeed;
  exploration_feed?: ExplorationFeed;
  markers: Marker[];
  deaths: Death[];
  death_markers_since: number | null;
  sources: Record<string, Source>;
};
type PositionFrame = Pick<World, "players" | "position_feed">;
type Place = {
  id: string;
  label: string;
  category: string;
  x: number;
  y: number;
  z: number;
  distance: number;
  rects: number[][];
};
type Vehicle = {
  id: number;
  label: string;
  model: string | null;
  x: number;
  y: number;
  z: number | null;
  category: "keyed" | "hotwired" | "wreck";
  hotwired: boolean;
  wreck: boolean;
};
const app = document.querySelector("#app")!;
app.innerHTML = `<div class="saved-app">
<header><div class="saved-brand"><span class="brand-mark">⌖</span><div><h1>Zomboid Observer</h1><p>Shared map · saved world information</p></div></div><span id="connection">Connecting…</span></header>
<div class="saved-workspace"><aside id="controls">
<section><label for="coverage-source">MAP KNOWLEDGE</label><select id="coverage-source"><option value="">Everyone · visited + learned</option></select><p id="known-count" class="muted">Reading explored areas…</p></section>
<section><label for="place-query">FIND A PLACE</label><form id="place-search"><input id="place-query" placeholder="Library, mechanic, guns, surplus…" autocomplete="off" maxlength="100"/><button type="submit" aria-label="Search places">Search</button></form><div class="quick-search"><button data-query="library">Books</button><button data-query="garage">Garages</button><button data-query="builder">Building supplies</button><button data-query="supermarket">Groceries</button><button data-query="police">Police</button><button data-query="weapons">Weapons</button><button data-query="military surplus">Surplus</button></div><label class="small-label" for="search-origin">DISTANCE FROM</label><select id="search-origin"><option value="">Map center</option></select><p id="search-note" class="muted">Search within known areas. Distances are straight-line.</p><div id="results" aria-live="polite"></div></section>
<section><label for="coordinates">GO TO COORDINATES</label><form id="coordinate-search"><input id="coordinates" placeholder="X, Y — e.g. 10632, 9913"/><button aria-label="Go to coordinates">Go</button></form><p id="coordinate-error" class="error" role="alert"></p></section>
<section><div class="row"><label>SURVIVORS</label><button id="fit-players" class="text-button">Fit all</button></div><div id="survivors"></div><p id="position-note" class="muted">Saved positions; online status is unavailable.</p></section>
<section id="planning-panel"></section>
<section><label>LAYERS</label><div class="layer-options"><label><input id="show-cities" type="checkbox" checked/> City names</label><label><input id="show-players" type="checkbox" checked/> Survivors</label><label><input id="show-vehicles" type="checkbox" checked/> Keyed / hotwired cars & wrecks</label><label><input id="show-markers" type="checkbox" checked/> Public markers</label><label><input id="show-deaths" type="checkbox" checked/> Death locations</label></div><p class="muted">Cars appear only when a living player carries their key, they are hotwired, or they are wrecks. Keys in key rings and bags count.</p><div id="public-markers"></div><details id="death-history"><summary>Death locations (<span id="death-count">0</span>)</summary><p id="death-note" class="muted"></p><div id="death-markers"></div></details></section>
<section><label>SHARED DRAWING</label><p class="muted">Hold Ctrl + left-drag over the map to draw for everyone watching.</p><button id="clear-drawings">Clear my drawings</button><p id="drawing-status" class="muted" aria-live="polite">Connecting live drawing…</p><button id="place-ping" aria-pressed="false">Place a ping</button><p id="ping-status" class="muted" role="status">Connecting shared pings…</p></section>
<section><label>SOURCE STATUS</label><div id="source-status"></div><p class="muted">Saved files checked every 30 seconds. Live exploration arrives separately when available.</p></section>
</aside><main><div id="map-update-strip" aria-label="Map update status"></div><div id="saved-map" aria-label="Shared saved world map"></div><div id="camera-follow-status" hidden role="status" aria-live="polite"></div><button id="toggle-controls" aria-label="Toggle map controls">☰ Search & players</button><div class="saved-legend"><span><i class="legend-dot" style="background:#248db3"></i>Survivor</span><span><i class="legend-dot" style="background:#29b765"></i>Keys carried</span><span><i class="legend-dot" style="background:#f3ad32"></i>Hotwired</span><span><i class="legend-dot" style="background:#de6756"></i>Wreck</span><span><i class="legend-dot" style="background:#985fe0"></i>Map note</span><span><i class="legend-death" aria-hidden="true">×</i>Death</span><span><i class="legend-route"></i>Trip</span><span><i class="legend-route legend-reached"></i>Reached</span><span class="legend-break"></span><span><i style="background:#898d91"></i>Pavement</span><span><i style="background:#b49a72"></i>Dirt / gravel</span><span><i style="background:#699da5"></i>Water</span><span><i style="background:#326e43"></i>Vegetation</span><span><i style="background:#aa7d63"></i>Walls & fixtures</span><span><i style="background:#d5bc93"></i>Interiors</span><span><i style="background:#25342f"></i>Unknown</span></div><div class="saved-map-footer"><span id="cursor-coordinates">World coordinates</span><span>Original ground-floor map · player-made changes not shown</span></div></main></div></div>`;
const $ = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id)! as T;
function node<K extends keyof HTMLElementTagNameMap>(tag: K, text?: string, cls?: string) {
  const n = document.createElement(tag);
  if (text !== undefined) n.textContent = text;
  if (cls) n.className = cls;
  return n;
}
function time(at: number | null | undefined) {
  return at ? new Date(at).toLocaleString() : "Not available";
}
async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  const r = await fetch(path, { signal });
  if (!r.ok) throw new Error(`Request failed (${r.status})`);
  return r.json();
}
const xy = (x: number, y: number): L.LatLngTuple => [-y, x];
const map = L.map("saved-map", {
  crs: L.CRS.Simple,
  minZoom: -4,
  maxZoom: 3,
  preferCanvas: true,
  zoomControl: false,
}).setView(xy(10632, 9913), 1);
L.control.zoom({ position: "topright" }).addTo(map);

map.attributionControl.setPrefix(false);
map.attributionControl.addAttribution("Terrain: Project Zomboid · saved-map view");
const terrain = L.tileLayer("/api/v1/map/tiles/{z}/{x}/{y}.png", {
  tileSize: 256,
  minZoom: -4,
  maxZoom: 3,
  noWrap: true,
  keepBuffer: 1,
}).addTo(map);
const playerLayer = L.layerGroup().addTo(map),
  vehicleLayer = L.layerGroup().addTo(map),
  markerLayer = L.layerGroup().addTo(map),
  deathLayer = L.layerGroup().addTo(map),
  selectionLayer = L.layerGroup().addTo(map);
const cityLabels = enableCityLabels(map);
const vehicleRenderer = L.svg();
const deathIcon = L.divIcon({
  className: "death-marker",
  iconSize: [32, 32],
  iconAnchor: [16, 16],
  popupAnchor: [0, -14],
  tooltipAnchor: [0, 0],
  html: '<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M6 5 Q14 15 25 26 M25 6 Q16 15 5 25" fill="none" stroke="#351616" stroke-width="7" stroke-linecap="round"/><path d="M6 5 Q14 15 25 26 M25 6 Q16 15 5 25" fill="none" stroke="#ff5454" stroke-width="4" stroke-linecap="round"/></svg>',
});
const vehicleStyles = {
  keyed: { color: "#29b765", label: "Key carried by a living player" },
  hotwired: { color: "#f3ad32", label: "Hotwired" },
  wreck: { color: "#de6756", label: "Burnt or smashed wreck" },
};
let world: World | null = null,
  observer = "",
  initial = true,
  lastCoverage = "",
  lastTerrain = "",
  busy = false,
  again = false;
let featureRequest: AbortController | null = null,
  searchRequest: AbortController | null = null,
  featureTimer: ReturnType<typeof setTimeout> | null = null;
let chosenPlace: Place | null = null;
const refreshStatus = enableRefreshStatus($("map-update-strip"), () => void refresh());
const events = new EventSource("/api/v1/events");
const cameraFollow = enableCameraFollow(map, $("camera-follow-status"), updateFollowButtons);
const planning = enablePlanning(
  map,
  () => {
    const p = world?.players.find(
      (p) => String(p.id) === $<HTMLSelectElement>("search-origin").value,
    );
    return p
      ? { label: p.name + " · " + positionLabel(p), x: p.x, y: p.y, z: Math.floor(p.z) }
      : { label: "Map center", ...origin(), z: 0 };
  },
  events,
  () => cameraFollow.stop(),
  () => ({ observer: observer || null, players: world?.players || [], selectedPlace: chosenPlace }),
);
const drawing = enableSharedDrawing(map, (x, y) => planning.ping(x, y));
function origin() {
  const p = world?.players.find(
    (p) => String(p.id) === $<HTMLSelectElement>("search-origin").value,
  );
  const c = map.getCenter();
  return p ? { x: p.x, y: p.y } : { x: c.lng, y: -c.lat };
}
function popup(title: string, lines: string[]) {
  const box = node("div", undefined, "map-popup");
  box.append(node("strong", title));
  for (const line of lines) box.append(node("p", line));
  return box;
}
function focus(x: number, y: number) {
  cameraFollow.stop();
  map.setView(xy(x, y), Math.max(map.getZoom(), 1));
  $("controls").classList.remove("open");
}
function params() {
  return observer ? "&observer=" + encodeURIComponent(observer) : "";
}
function positionLabel(p: Player) {
  return p.source === "live"
    ? "live position"
    : p.source === "last_seen"
      ? "last seen"
      : "saved position";
}
const playerPins = new Map<
  string,
  { pin: L.CircleMarker; button: HTMLButtonElement; row: HTMLDivElement; follow: HTMLButtonElement }
>();
function updateFollowButtons() {
  for (const [name, entry] of playerPins) {
    const active = cameraFollow.selected === name;
    entry.follow.textContent = active ? "Following" : "Follow";
    entry.follow.setAttribute("aria-pressed", String(active));
    entry.follow.setAttribute("aria-label", `${active ? "Stop following" : "Follow"} ${name}`);
  }
}
let positionEpoch = 0,
  pendingPositions: PositionFrame | null = null;
function renderPlayers(frame: PositionFrame) {
  if (!world) {
    pendingPositions = frame;
    return;
  }
  world.players = frame.players;
  world.position_feed = frame.position_feed;
  refreshStatus.positions(frame.position_feed);
  const names = frame.players.map((p) => p.name),
    ref = $<HTMLSelectElement>("search-origin"),
    currentRef = ref.value;
  planning.updatePlayers(names);
  const options = frame.players.map((p) => [String(p.id), p.name + " · " + positionLabel(p)]);
  if (
    JSON.stringify([...ref.options].slice(1).map((o) => [o.value, o.text])) !==
    JSON.stringify(options)
  ) {
    ref.replaceChildren(
      new Option("Map center", ""),
      ...options.map(([value, label]) => new Option(label, value)),
    );
    ref.value = currentRef;
    if (initial) {
      const p = frame.players.find((p) => p.name === "akryllax");
      if (p) ref.value = String(p.id);
    }
  }
  if (initial && frame.players.length) {
    const p = frame.players.find((p) => p.name === "akryllax") ?? frame.players[0];
    map.setView(xy(p.x, p.y), 1);
    initial = false;
  }
  for (const [name, entry] of playerPins)
    if (!names.includes(name)) {
      playerLayer.removeLayer(entry.pin);
      entry.row.remove();
      playerPins.delete(name);
    }
  for (const p of frame.players) {
    let entry = playerPins.get(p.name);
    if (!entry) {
      const button = node("button", undefined, "survivor"),
        row = node("div", undefined, "survivor-row"),
        follow = node("button", "Follow", "camera-follow-toggle");
      follow.type = "button";
      follow.title = "Keep the camera centered on this survivor";
      follow.onclick = () => {
        cameraFollow.toggle(p.name);
        $("controls").classList.remove("open");
      };
      row.append(button, follow);
      $("survivors").append(row);
      const pin = L.circleMarker(xy(p.x, p.y), {
        radius: 9,
        color: "#fff",
        weight: 2,
        fillColor: "#248db3",
        fillOpacity: 1,
      })
        .bindTooltip(node("span", p.name), {
          permanent: true,
          direction: "top",
          className: "survivor-label",
        })
        .addTo(playerLayer);
      entry = { pin, button, row, follow };
      playerPins.set(p.name, entry);
    }
    const status =
      p.source === "live"
        ? "Online · live"
        : p.online === false
          ? "Offline · " + positionLabel(p)
          : p.source === "last_seen"
            ? "Last seen · connection unknown"
            : "Saved · online status unknown";
    entry.button.replaceChildren(
      node("strong", p.name),
      node(
        "span",
        `${p.character} · ${Math.round(p.x)}, ${Math.round(p.y)} · Z ${Math.floor(p.z)}`,
      ),
      node("span", status, "player-position-status"),
    );
    entry.button.dataset.source = p.source || "saved";
    entry.button.onclick = () => {
      ref.value = String(p.id);
      selectionLayer.clearLayers();
      chosenPlace = null;
      focus(p.x, p.y);
      if ($<HTMLInputElement>("place-query").value) void search();
    };
    entry.pin.setLatLng(xy(p.x, p.y)).setStyle({
      fillColor: p.source === "live" ? "#28b8e0" : "#248db3",
      fillOpacity: p.source === "live" ? 1 : 0.7,
      dashArray: p.source === "last_seen" ? "3 3" : undefined,
    });
    const content = popup(p.name, [
      p.character,
      `${p.x.toFixed(1)}, ${p.y.toFixed(1)} · Floor ${Math.floor(p.z)}`,
      status,
      p.observed_at
        ? `Observed: ${time(p.observed_at)}`
        : `Source file updated: ${time(world.sources.players?.modified_at)}`,
      p.source === "saved" || !p.source
        ? "Exact per-character save time is unavailable."
        : "Latest observation only; travel history is not recorded.",
    ]);
    if (entry.pin.getPopup()) entry.pin.setPopupContent(content);
    else entry.pin.bindPopup(content);
  }
  const feed = frame.position_feed;
  $("position-note").textContent = !feed?.enabled
    ? "Saved positions; online status is unavailable."
    : feed.status === "live"
      ? "Online positions update about once a second. Offline survivors keep their last known position."
      : "Live positions are unavailable. Last known positions remain visible; automatic checkpoints wait.";
  planning.updatePositionFeed(feed);
  cameraFollow.update(frame.players, feed);
  updateFollowButtons();
}
function choose(place: Place, reference: { x: number; y: number; label: string }) {
  chosenPlace = place;
  planning.updateGPS();
  selectionLayer.clearLayers();
  const line = [xy(reference.x, reference.y), xy(place.x, place.y)];
  L.polyline(line, {
    renderer: vehicleRenderer,
    color: "#18261e",
    weight: 4,
    dashArray: "1 7",
    lineCap: "round",
    opacity: 0.8,
    interactive: false,
  }).addTo(selectionLayer);
  L.polyline(line, {
    renderer: vehicleRenderer,
    className: "finder-reference-line",
    color: "#f8d37b",
    weight: 2,
    dashArray: "1 7",
    lineCap: "round",
    opacity: 1,
    interactive: false,
  }).addTo(selectionLayer);
  L.circleMarker(xy(reference.x, reference.y), {
    renderer: vehicleRenderer,
    className: "finder-reference-origin",
    radius: 4,
    weight: 2,
    color: "#fff5d5",
    fillColor: "#b98426",
    fillOpacity: 1,
  })
    .bindTooltip(
      node("span", `${reference.label} · ${Math.round(place.distance)} tiles to selection`),
      { direction: "top", className: "vehicle-label" },
    )
    .addTo(selectionLayer);
  for (const [x, y, w, h] of place.rects)
    L.rectangle([xy(x, y), xy(x + w, y + h)], {
      color: "#f5b544",
      weight: 3,
      fillOpacity: 0.22,
    }).addTo(selectionLayer);
  L.circleMarker(xy(place.x, place.y), {
    radius: 7,
    color: "#fff",
    fillColor: "#e98b20",
    fillOpacity: 1,
  })
    .bindPopup(
      popup(place.label, [
        `${place.x.toFixed(0)}, ${place.y.toFixed(0)} · Floor ${place.z}`,
        `${place.distance.toFixed(0)} tiles from search origin`,
        "Building type from installed map; contents are not known.",
      ]),
    )
    .addTo(selectionLayer)
    .openPopup();
  focus(place.x, place.y);
}
async function search() {
  const query = $<HTMLInputElement>("place-query").value.trim();
  if (!query) return;
  searchRequest?.abort();
  const controller = new AbortController();
  searchRequest = controller;
  const p = {
    ...origin(),
    label:
      $<HTMLSelectElement>("search-origin").selectedOptions[0]?.text || "Map center at search time",
  };
  $("search-note").textContent = "Searching known places…";
  try {
    const data = await get<{ results: Place[]; indexing: boolean; error: string | null }>(
      `/api/v1/places/search?q=${encodeURIComponent(query)}&x=${p.x}&y=${p.y}${params()}`,
      controller.signal,
    );
    const results = $("results");
    results.replaceChildren();
    for (const place of data.results) {
      const button = node("button", undefined, "place-result");
      button.append(
        node("strong", place.label),
        node(
          "span",
          `${Math.round(place.distance)} tiles · ${Math.round(place.x)}, ${Math.round(place.y)} · Z ${place.z}`,
        ),
      );
      button.onclick = () => choose(place, p);
      const row = node("div", undefined, "place-match"),
        add = node("button", "+", "place-add");
      add.setAttribute("aria-label", `Add ${place.label} to trip`);
      add.title = "Add to trip";
      add.onclick = () =>
        planning.addStop({ label: place.label, x: place.x, y: place.y, z: place.z });
      row.append(button, add);
      results.append(row);
    }
    $("search-note").textContent = data.error
      ? "Some map metadata is unavailable. Results may be incomplete."
      : data.indexing
        ? "Building the place index; results will improve shortly."
        : data.results.length
          ? `${data.results.length} nearest matches · straight-line distance`
          : "No matching places in known areas.";
  } catch (e) {
    if ((e as Error).name !== "AbortError")
      $("search-note").textContent = "Search unavailable. Try again shortly.";
  }
}
$("place-search").onsubmit = (e) => {
  e.preventDefault();
  void search();
};
document.querySelectorAll<HTMLButtonElement>("[data-query]").forEach(
  (b) =>
    (b.onclick = () => {
      $<HTMLInputElement>("place-query").value = b.dataset.query!;
      void search();
    }),
);
$<HTMLSelectElement>("search-origin").onchange = () => {
  selectionLayer.clearLayers();
  chosenPlace = null;
  if ($<HTMLInputElement>("place-query").value) void search();
};
$("coordinate-search").onsubmit = (e) => {
  e.preventDefault();
  const s = $<HTMLInputElement>("coordinates").value.trim();
  const parts = s.split(/[,\s]+/).map(Number);
  if (!s || parts.length !== 2 || parts.some((n) => !Number.isFinite(n) || Math.abs(n) > 200000)) {
    $("coordinate-error").textContent = "Enter X, Y, for example 10632, 9913.";
    return;
  }
  $("coordinate-error").textContent = "";
  focus(parts[0], parts[1]);
};
$("toggle-controls").onclick = () => $("controls").classList.toggle("open");
$("fit-players").onclick = () => {
  cameraFollow.stop();
  if (world?.players.length)
    map.fitBounds(
      world.players.map((p) => xy(p.x, p.y)),
      { padding: [50, 50], maxZoom: 1 },
    );
};
for (const [id, layer] of [
  ["show-cities", cityLabels.layer],
  ["show-players", playerLayer],
  ["show-vehicles", vehicleLayer],
  ["show-markers", markerLayer],
  ["show-deaths", deathLayer],
] as const)
  $<HTMLInputElement>(id).onchange = () => {
    if ($<HTMLInputElement>(id).checked) layer.addTo(map);
    else map.removeLayer(layer);
  };
$<HTMLSelectElement>("coverage-source").onchange = () => {
  observer = $<HTMLSelectElement>("coverage-source").value;
  lastCoverage = "";
  lastTerrain = "";
  cityLabels.clear();
  selectionLayer.clearLayers();
  chosenPlace = null;
  planning.clearCoverage();
  void refresh();
  scheduleFeatures();
  if ($<HTMLInputElement>("place-query").value) void search();
};
map.on("mousemove", (e: L.LeafletMouseEvent) => {
  $("cursor-coordinates").textContent =
    `X ${Math.floor(e.latlng.lng)} · Y ${Math.floor(-e.latlng.lat)}`;
});
function scheduleFeatures() {
  if (featureTimer) clearTimeout(featureTimer);
  featureTimer = setTimeout(() => void features(), 200);
}
map.on("moveend", scheduleFeatures);
async function features() {
  featureRequest?.abort();
  const controller = new AbortController();
  featureRequest = controller;
  const c = map.getCenter(),
    b = map.getBounds();
  const radius = Math.min(
    16000,
    Math.max(32, b.getEast() - b.getWest(), b.getNorth() - b.getSouth()) / 2 + 32,
  );
  try {
    const data = await get<{ vehicles: Vehicle[] }>(
      `/api/v1/map/features?x=${c.lng}&y=${-c.lat}&radius=${radius}${params()}`,
      controller.signal,
    );
    vehicleLayer.clearLayers();
    for (const v of data.vehicles) {
      const style = vehicleStyles[v.category];
      if (!style) continue;
      L.circleMarker(xy(v.x, v.y), {
        renderer: vehicleRenderer,
        className: `vehicle-pin vehicle-${v.category}`,
        radius: map.getZoom() < 0 ? 4 : 6,
        weight: 2,
        color: "#fff7e8",
        fillColor: style.color,
        fillOpacity: 1,
        dashArray: v.category === "wreck" ? "2 2" : undefined,
      })
        .bindTooltip(node("span", `${v.label.replace(/^Base\./, "")} · ${style.label}`), {
          direction: "top",
          className: "vehicle-label",
        })
        .bindPopup(
          popup(v.label.replace(/^Base\./, ""), [
            style.label,
            `Vehicle #${v.id} · ${v.x.toFixed(1)}, ${v.y.toFixed(1)}`,
            v.z === null ? "Floor unknown" : `Saved floor ${v.z}`,
            `Source file updated: ${time(world?.sources.vehicles?.modified_at)}`,
          ]),
        )
        .addTo(vehicleLayer);
    }
  } catch (e) {
    if ((e as Error).name !== "AbortError")
      $("connection").textContent = "Vehicle positions unavailable · last snapshot";
  }
}
async function refresh() {
  if (busy) {
    again = true;
    return;
  }
  busy = true;
  const epoch = positionEpoch;
  refreshStatus.begin();
  try {
    const next = await get<World>("/api/v1/world");
    const previous = world;
    if (positionEpoch !== epoch && previous) {
      next.players = previous.players;
      next.position_feed = previous.position_feed;
    }
    world = next;
    refreshStatus.received(next);
    $("connection").textContent = next.indexing ? "Reading map metadata…" : "Saved map · connected";
    $("connection").classList.remove("error");
    const source = $<HTMLSelectElement>("coverage-source");
    if (
      JSON.stringify([...source.options].slice(1).map((o) => o.value)) !==
      JSON.stringify(next.observers)
    ) {
      source.replaceChildren(
        new Option("Everyone · visited + learned", ""),
        ...next.observers.map((n) => new Option(n, n)),
      );
      source.value = observer;
    }
    renderPlayers(pendingPositions || next);
    pendingPositions = null;
    markerLayer.clearLayers();
    $("public-markers").replaceChildren();
    for (const m of next.markers) {
      L.circleMarker(xy(m.x, m.y), {
        radius: 7,
        weight: 2,
        color: "#fff",
        fillColor: "#985fe0",
        fillOpacity: 1,
      })
        .bindTooltip(node("span", m.label || "Map note"), {
          permanent: true,
          direction: "right",
          offset: [10, 0],
          className: "public-marker-label",
          opacity: 1,
        })
        .bindPopup(
          popup(m.label, [`Shared by ${m.author}`, `${Math.round(m.x)}, ${Math.round(m.y)}`]),
        )
        .addTo(markerLayer);
      const b = node("button", `${m.label} · ${m.author}`, "text-button");
      b.onclick = () => focus(m.x, m.y);
      $("public-markers").append(b);
    }
    const deaths = next.deaths || [];
    $("death-count").textContent = String(deaths.length);
    $("death-note").textContent = next.death_markers_since
      ? `Recording since ${time(next.death_markers_since)}. Markers remain after respawning.`
      : "Death tracking is not configured.";
    if (!previous || JSON.stringify(previous.deaths) !== JSON.stringify(deaths)) {
      deathLayer.clearLayers();
      $("death-markers").replaceChildren();
      for (const d of deaths) {
        const label = `${d.name} died here`;
        L.marker(xy(d.x, d.y), { icon: deathIcon, title: label, alt: label, riseOnHover: true })
          .bindTooltip(node("span", label), {
            permanent: true,
            direction: "bottom",
            offset: [0, 15],
            className: "death-marker-label",
            opacity: 1,
          })
          .bindPopup(
            popup(label, [
              `${d.x}, ${d.y} · Floor ${d.z}`,
              `Died: ${time(d.occurred_at)}`,
              "Recorded death location; the body may have moved or reanimated.",
            ]),
          )
          .addTo(deathLayer);
        const b = node("button", undefined, "death-result");
        b.append(node("strong", label), node("span", `${time(d.occurred_at)} · ${d.x}, ${d.y}`));
        b.onclick = () => {
          $<HTMLInputElement>("show-deaths").checked = true;
          deathLayer.addTo(map);
          focus(d.x, d.y);
        };
        $("death-markers").append(b);
      }
    }
    const status = $("source-status");
    status.replaceChildren();
    for (const [name, s] of Object.entries(next.sources)) {
      const p = node("p", undefined, "source-entry");
      p.append(
        node(
          "strong",
          name === "car_keys"
            ? "Car keys"
            : name === "deaths"
              ? "Death locations"
              : name.replace("coverage:", "Map · "),
        ),
        node("span", `File updated: ${time(s.modified_at)}`),
        node("span", `Checked: ${time(s.checked_at)}`),
      );
      if (s.error)
        p.append(
          node(
            "span",
            name === "car_keys"
              ? "Key inventory unavailable; cars requiring a carried key are hidden."
              : "Read failed; retaining the previous snapshot.",
            "error",
          ),
        );
      status.append(p);
    }
    if (next.index_error)
      status.append(node("p", "Some terrain metadata could not be indexed.", "error"));
    const coverageToken = `${observer}:${next.coverage_revision}`;
    if (coverageToken !== lastCoverage) {
      const sourceObserver = observer;
      const c = await get<CityCoverage>(
        "/api/v1/coverage" + (observer ? "?observer=" + encodeURIComponent(observer) : ""),
      );
      if (sourceObserver === observer) {
        cityLabels.update(c);
        planning.updateCoverage(c);
        $("known-count").textContent =
          `${c.cells.length.toLocaleString()} known blocks · 32 × 32 tiles each`;
        lastCoverage = coverageToken;
      }
    }
    const tileToken = `${coverageToken}:${next.terrain_revision}`;
    if (tileToken !== lastTerrain) {
      terrain.setUrl(
        `/api/v1/map/tiles/{z}/{x}/{y}.png?v=${encodeURIComponent(tileToken)}${params()}`,
      );
      lastTerrain = tileToken;
    }
    if (!previous || previous.revision !== next.revision) {
      scheduleFeatures();
      if (chosenPlace && previous?.coverage_revision !== next.coverage_revision) {
        selectionLayer.clearLayers();
        chosenPlace = null;
      }
      if ($<HTMLInputElement>("place-query").value) void search();
    }
    if (previous?.indexing && !next.indexing && $<HTMLInputElement>("place-query").value)
      void search();
  } catch {
    refreshStatus.failed();
    $("connection").textContent = "Connection interrupted · last snapshot";
    $("connection").classList.add("error");
  } finally {
    busy = false;
    refreshStatus.end();
    if (again) {
      again = false;
      void refresh();
    }
  }
}
terrain.on("tileerror", () => {
  $("connection").textContent = "Some terrain tiles unavailable · retrying on refresh";
});
events.addEventListener("revision", () => void refresh());
events.addEventListener("exploration", (event) => {
  try {
    refreshStatus.exploration(JSON.parse((event as MessageEvent).data));
  } catch {
    /* Retain the last valid feed status. */
  }
});
events.addEventListener("positions", (event) => {
  try {
    positionEpoch++;
    renderPlayers(JSON.parse((event as MessageEvent).data));
  } catch {
    /* Keep the last validated server snapshot. */
  }
});
function stalePositions() {
  if (!world?.position_feed || world.position_feed.status !== "live") return;
  positionEpoch++;
  renderPlayers({
    position_feed: { ...world.position_feed, status: "stale", online_count: null },
    players: world.players.map((p) => ({
      ...p,
      source: p.source === "live" ? "last_seen" : p.source,
      online: null,
    })),
  });
}
const positionTimer = setInterval(() => {
  const f = world?.position_feed;
  if (f?.received_at && Date.now() - f.received_at >= f.stale_after_ms) stalePositions();
}, 1000);
events.onerror = () => {
  $("connection").textContent = "Reconnecting · last snapshot";
  stalePositions();
};
void refresh();
scheduleFeatures();
window.addEventListener("beforeunload", () => {
  clearInterval(positionTimer);
  refreshStatus.dispose();
  planning.dispose();
  drawing.dispose();
  cityLabels.dispose();
  cameraFollow.dispose();
  events.close();
  map.remove();
});
