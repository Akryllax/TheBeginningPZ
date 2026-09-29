import L from "leaflet";
import type { PositionFeed } from "./refresh-status";
import { enableGPS, knownParts, type Routing, type GPSContext } from "./gps";
import type { CityCoverage } from "./city-labels";

export type TripStop = {
  id?: string;
  label: string;
  x: number;
  y: number;
  z: number;
  kind?: "manual" | "generated" | "origin";
};
type Tracking = {
  reached_stop_ids: string[];
  player: string | null;
  radius: number;
  active: boolean;
  completed: number;
  started_at: number | null;
  note: string;
};
type Trip = {
  tracking: Tracking;
  id: string;
  name: string;
  stops: TripStop[];
  version: number;
  created_at: number;
  updated_at: number;
  distance: number;
  routing?: Routing | null;
};
type Draft = {
  follow_player: string | null;
  arrival_radius: number;
  id: string | null;
  name: string;
  stops: TripStop[];
  version: number;
  dirty: boolean;
  routing: Routing | null;
};
type Ping = { id: string; x: number; y: number; created_at: number; expires_at: number };
type Snapshot = { progress_error: string | null; revision: number; trips: Trip[]; pings: Ping[] };
const xy = (p: { x: number; y: number }): L.LatLngTuple => [-p.y, p.x];
const empty = (): Draft => ({
  id: uid(),
  name: "Trip · " + new Date().toLocaleString(),
  stops: [],
  version: 0,
  dirty: false,
  follow_player: null,
  arrival_radius: 40,
  routing: null,
});
function element<K extends keyof HTMLElementTagNameMap>(tag: K, text?: string, cls?: string) {
  const e = document.createElement(tag);
  if (text !== undefined) e.textContent = text;
  if (cls) e.className = cls;
  return e;
}
function uid() {
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 15) | 64;
  b[8] = (b[8] & 63) | 128;
  const s = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`;
}
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value));
const routeOptions = (r: Routing | null | undefined) =>
  r ? { observer: r.observer, auto_reroute: r.auto_reroute } : null;
const editable = (t: Trip | Draft) =>
  JSON.stringify({
    name: t.name.trim(),
    stops: t.stops.map((p) => ({
      id: p.id,
      label: p.label.trim(),
      x: p.x,
      y: p.y,
      z: p.z,
      kind: p.kind || "manual",
    })),
    player: "tracking" in t ? t.tracking.player : t.follow_player,
    radius: "tracking" in t ? t.tracking.radius : t.arrival_radius,
    routing: routeOptions(t.routing),
  });
function ownerId() {
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 15) | 64;
  b[8] = (b[8] & 63) | 128;
  const s = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  let id = `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`;
  try {
    id = sessionStorage.getItem("observer-ping-owner") || id;
    sessionStorage.setItem("observer-ping-owner", id);
  } catch {
    /* A temporary identity also works. */
  }
  return id;
}
async function api<T>(url: string, method = "GET", body?: unknown): Promise<T> {
  const r = await fetch(url, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await r.json();
  if (!r.ok)
    throw Object.assign(
      new Error(typeof data.detail === "string" ? data.detail : "Request failed. Try again."),
      { status: r.status },
    );
  return data;
}
function validStop(p: any): p is TripStop {
  return (
    p &&
    typeof p.label === "string" &&
    p.label.length > 0 &&
    p.label.length <= 120 &&
    Number.isFinite(p.x) &&
    Number.isFinite(p.y) &&
    Math.abs(p.x) <= 200000 &&
    Math.abs(p.y) <= 200000 &&
    Number.isInteger(p.z) &&
    p.z >= -32 &&
    p.z <= 64
  );
}

export function enablePlanning(
  map: L.Map,
  reference: () => TripStop,
  events: EventSource,
  onNavigate = () => {},
  gpsContext: () => GPSContext = () => ({ observer: null, players: [], selectedPlace: null }),
) {
  const host = document.getElementById("planning-panel")!;
  host.innerHTML = `<details id="trip-details"><summary>Saved trips <span id="trip-count"></span></summary><fieldset id="trip-fields">
    <label class="small-label" for="trip-select">SHARED TRIPS</label><select id="trip-select"><option value="">New trip</option></select>
    <label class="small-label" for="trip-name">TRIP NAME</label><input id="trip-name" maxlength="80" placeholder="Surplus-store run"/>
    <div id="gps-helper"></div>
    <div class="trip-actions"><button class="plan-edit" id="trip-reference">+ Reference</button><button class="plan-edit" id="trip-map-stop" aria-pressed="false">+ Map stop</button><button class="plan-edit" id="trip-new">New trip</button></div>
    <p class="muted">Planning: Shift + click adds a point; drag points to move; Shift + drag a line inserts a point; right-click a point removes it.</p>
    <ol id="trip-stops"></ol><p id="trip-distance" class="muted">Add destinations to plan a trip.</p>
    <div class="trip-actions"><button id="trip-save" hidden>Retry sync</button><button id="trip-copy" hidden>Save as new</button><button class="plan-edit" id="trip-clear">Clear all waypoints</button><button class="plan-edit" id="trip-edit-undo">Undo edit</button><button id="trip-reload" hidden>Reload saved</button><button id="trip-fit">Fit route</button><button class="plan-edit" id="trip-delete">Delete saved trip</button><button class="plan-edit" id="trip-undo" hidden>Undo delete</button></div>
    <label class="small-label" for="trip-player">AUTO CHECKPOINTS · FOLLOW PLAYER</label><select id="trip-player"><option value="">Manual progress only</option></select>
    <label class="small-label" for="trip-radius">ARRIVAL RADIUS (TILES)</label><input id="trip-radius" type="number" min="10" max="200" step="1" value="40"/>
    <p id="checkpoint-source-note" class="muted">Start treats stop 1 as the departure point. Later stops resolve in order from fresh saved positions, about every 3 minutes. Brief visits may be missed. New or moved points need reaching; unchanged reached points keep their status.</p>
    <progress id="trip-progress-bar" max="1" value="0" aria-label="Completed trip legs"></progress><p id="trip-progress" class="muted"></p>
    <div class="trip-actions"><button id="trip-start">Start / resume</button><button id="trip-pause">Pause</button><button id="trip-next">Mark next reached</button><button id="trip-reset">Reset progress</button></div>
    <label class="trip-visibility"><input id="show-trip" type="checkbox" checked/> Show selected route</label>
    <p id="trip-status" class="muted" role="status">Connecting shared trips…</p></fieldset></details>`;
  const $ = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id)! as T;
  const select = $<HTMLSelectElement>("trip-select"),
    name = $<HTMLInputElement>("trip-name"),
    status = $("trip-status");
  const pingButton = $<HTMLButtonElement>("place-ping"),
    pingStatus = $("ping-status");
  const notice = element("button", undefined, "ping-notice");
  notice.hidden = true;
  notice.id = "ping-notice";
  map.getContainer().parentElement!.append(notice);
  let draft = empty(),
    trips: Trip[] = [],
    revision = -1,
    armed: "ping" | "stop" | "from" | "to" | null = null,
    saving = false,
    deleted: Trip | null = null,
    connected = false,
    players: string[] = [],
    progressError: string | null = null;
  let editing = false,
    blocked = false,
    snapshotReady = false,
    serial = 0,
    base: Trip | null = null,
    actionBusy = false,
    retryAttempts = 0,
    creationSent = false;
  let saveTimer: ReturnType<typeof setTimeout> | null = null;
  let undoHistory: { stops: TripStop[]; routing: Routing | null }[] = [];
  let known = new Set<string>();
  const visible = (p: TripStop) =>
    known.has(`${Math.floor(p.x / 32) * 32},${Math.floor(p.y / 32) * 32}`);
  const initialBoxZoom = map.boxZoom.enabled();
  const modeTools = element("div", undefined, "trip-mode-tools");
  const modeButton = element("button", "Plan trip");
  modeButton.id = "trip-mode";
  modeButton.setAttribute("aria-pressed", "false");
  const modeHint = element("span", "Planning · changes save for everyone", "trip-mode-hint");
  const syncBadge = element("span", "", "trip-sync");
  syncBadge.id = "trip-sync";
  syncBadge.setAttribute("role", "status");
  modeTools.append(modeButton, modeHint, syncBadge);
  map.getContainer().parentElement!.append(modeTools);
  const storageKey = "observer-trip-draft";
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || "null");
    if (
      saved &&
      typeof saved.name === "string" &&
      saved.name.length <= 80 &&
      Array.isArray(saved.stops) &&
      saved.stops.length <= 256 &&
      saved.stops.every(validStop) &&
      Number.isInteger(saved.version) &&
      (saved.id === null || typeof saved.id === "string")
    )
      draft = {
        ...saved,
        follow_player: typeof saved.follow_player === "string" ? saved.follow_player : null,
        arrival_radius: Number.isInteger(saved.arrival_radius) ? saved.arrival_radius : 40,
        routing: saved.routing || null,
      };
    creationSent = Boolean(saved?.creationSent);
    if (
      saved?.base &&
      Array.isArray(saved.base.stops) &&
      saved.base.stops.every(validStop) &&
      saved.base.tracking
    )
      base = saved.base;
  } catch {
    /* Ignore invalid browser storage. */
  }
  draft.id ||= uid();
  draft.stops.forEach((p) => (p.id ||= uid()));
  let lastStops = copy(draft.stops);
  let lastRouting = copy(draft.routing);
  const gps = enableGPS($("gps-helper"), gpsContext, reference, () => draft, {
    id: uid,
    arm: (which) => arm(which),
    apply(stops, routing) {
      if (!editing || actionBusy || blocked) return;
      draft.stops = stops;
      draft.routing = routing;
      changed(true, true);
    },
    auto(on) {
      if (draft.routing) {
        draft.routing.auto_reroute = on;
        changed(true, true);
      }
    },
    recalculate: () => void recalculate(),
  });
  const route = L.layerGroup().addTo(map),
    renderer = L.svg();
  const pins = new Map<string, { marker: L.Marker; ping: Ping }>();
  const owner = ownerId();
  map.createPane("shared-pings").style.zIndex = "640";
  map.getPane("shared-pings")!.style.pointerEvents = "none";
  function remember() {
    try {
      localStorage.setItem(storageKey, JSON.stringify({ ...draft, base, creationSent }));
    } catch {
      /* Saving a shared trip still works without browser storage. */
    }
  }
  function focus(p: { x: number; y: number }) {
    onNavigate();
    map.setView(xy(p), Math.max(map.getZoom(), 1));
    $("controls").classList.remove("open");
  }
  function choices() {
    select.replaceChildren(
      new Option("New trip", ""),
      ...trips.map((t) => new Option(t.name, t.id)),
    );
    if (draft.version && draft.id && !trips.some((t) => t.id === draft.id))
      select.add(new Option("Deleted trip · local draft", draft.id));
    select.value = draft.version ? draft.id || "" : "";
    $("trip-count").textContent = trips.length ? `(${trips.length})` : "";
  }
  function reachedIds() {
    const t = trips.find((t) => t.id === draft.id);
    if (!t) return new Set<string>();
    return new Set(
      t.tracking.reached_stop_ids.filter((id) =>
        draft.stops.some(
          (p) =>
            p.id === id &&
            t.stops.some((old) => old.id === id && old.x === p.x && old.y === p.y && old.z === p.z),
        ),
      ),
    );
  }
  function drawRoute() {
    route.clearLayers();
    const points = draft.stops.map(xy),
      t = trips.find((t) => t.id === draft.id)?.tracking,
      reached = reachedIds();
    const next = draft.stops.find((p) => !reached.has(p.id!))?.id;
    if (draft.routing) {
      for (const segment of draft.routing.geometry || []) {
        const complete = reached.has(segment.from) && reached.has(segment.to),
          access = segment.kind === "access";
        for (const part of knownParts(segment.points, known)) {
          const line = part.map(([x, y]) => [-y, x] as L.LatLngTuple);
          L.polyline(line, {
            renderer,
            color: "#17334a",
            weight: 6,
            opacity: 0.8,
            interactive: false,
          }).addTo(route);
          L.polyline(line, {
            renderer,
            className: complete ? "trip-completed-leg" : access ? "trip-access" : "trip-route",
            color: complete ? "#80dc9a" : access ? "#edbe74" : "#80d9ff",
            weight: complete ? 4 : 3,
            dashArray: access ? "4 6" : undefined,
            opacity: draft.routing.status === "ready" ? 1 : 0.45,
            interactive: false,
          }).addTo(route);
        }
      }
    } else {
      if (points.length > 1) {
        L.polyline(points, {
          renderer,
          color: "#17334a",
          weight: 6,
          opacity: 0.8,
          interactive: false,
        }).addTo(route);
        L.polyline(points, {
          renderer,
          className: "trip-route",
          color: "#80d9ff",
          weight: 3,
          dashArray: "8 6",
          interactive: false,
        }).addTo(route);
      }
      for (let i = 1; i < points.length; i++)
        if (reached.has(draft.stops[i - 1].id!) && reached.has(draft.stops[i].id!))
          L.polyline([points[i - 1], points[i]], {
            renderer,
            className: "trip-completed-leg",
            color: "#80dc9a",
            weight: 4,
            interactive: false,
          }).addTo(route);
    }
    draft.stops.forEach((p, i) => {
      if (draft.routing && p.kind && p.kind !== "manual" && !visible(p)) return;
      const icon = L.divIcon({
        className: `trip-pin${p.kind === "generated" ? " trip-generated" : ""}${reached.has(p.id!) ? " trip-reached" : ""}${p.id === next && t?.active ? " trip-next" : ""}`,
        html: element("span", String(i + 1)),
        iconSize: [25, 25],
        iconAnchor: [12, 12],
      });
      const popup = element("div", undefined, "map-popup");
      popup.append(
        element("strong", `${i + 1}. ${p.label}`),
        element("p", `${Math.round(p.x)}, ${Math.round(p.y)} · Floor ${p.z}`),
      );
      if (editing) {
        const remove = element("button", "Remove waypoint");
        remove.onclick = () => removePoint(p.id!);
        popup.append(remove);
      }
      const marker = L.marker(xy(p), { icon, title: `Stop ${i + 1}: ${p.label}` })
        .bindTooltip(element("span", p.label), { direction: "top", className: "vehicle-label" })
        .bindPopup(popup);
      marker.on("add", () => {
        const node = marker.getElement();
        if (node) node.dataset.waypointId = p.id;
      });
      marker.addTo(route);
    });
    distanceLabel();
  }
  function distanceLabel() {
    const distance = draft.stops
      .slice(1)
      .reduce((sum, p, i) => sum + Math.hypot(p.x - draft.stops[i].x, p.y - draft.stops[i].y), 0);
    $("trip-distance").textContent = draft.routing
      ? `${draft.stops.length} checkpoints · ${Math.round(draft.routing.road_distance || 0).toLocaleString()} road tiles + ${Math.round(draft.routing.access_distance || 0).toLocaleString()} unverified access${draft.routing.status === "ready" ? "" : " · recalculation needed"}`
      : `${draft.stops.length} waypoints · ${Math.round(distance).toLocaleString()} tiles · straight-line distance`;
  }
  function render() {
    choices();
    $<HTMLFieldSetElement>("trip-fields").disabled = actionBusy;
    $<HTMLSelectElement>("trip-select").disabled = saving || draft.dirty;
    for (const id of ["trip-name", "trip-player", "trip-radius"])
      $<HTMLInputElement>(id).disabled = !editing;
    name.value = draft.name;
    $<HTMLInputElement>("trip-radius").value = String(draft.arrival_radius);
    playerChoices();
    const list = $("trip-stops");
    list.replaceChildren();
    draft.stops.forEach((p, i) => {
      const hidden = Boolean(draft.routing && p.kind && p.kind !== "manual" && !visible(p));
      const row = element("li", undefined, "trip-stop"),
        label = element(
          "button",
          `${i + 1}. ${hidden ? "Turn outside this map knowledge" : p.label}`,
          "trip-stop-label",
        );
      label.onclick = () => focus(p);
      label.disabled = hidden;
      const actions = element("div", undefined, "trip-stop-actions");
      for (const [text, title, offset] of [
        ["↑", "Move up", -1],
        ["↓", "Move down", 1],
      ] as const) {
        const b = element("button", text);
        b.setAttribute("aria-label", `${title}: ${hidden ? "Unknown turn" : p.label}`);
        b.disabled = !editing || i + offset < 0 || i + offset >= draft.stops.length;
        b.onclick = () => {
          p.kind = "manual";
          [draft.stops[i], draft.stops[i + offset]] = [draft.stops[i + offset], draft.stops[i]];
          changed();
        };
        actions.append(b);
      }
      const remove = element("button", "×");
      remove.setAttribute("aria-label", `Remove: ${p.label}`);
      remove.onclick = () => removePoint(p.id!);
      remove.disabled = !editing;
      actions.classList.add("plan-edit");
      actions.append(remove);
      row.append(label, actions);
      list.append(row);
    });
    $("trip-save").hidden = !draft.dirty || saving;
    $("trip-copy").hidden = !blocked;
    $("trip-reload").hidden = !blocked;
    $<HTMLButtonElement>("trip-edit-undo").disabled = !undoHistory.length;
    $<HTMLButtonElement>("trip-clear").disabled = !draft.stops.length;
    $<HTMLButtonElement>("trip-new").disabled = saving || draft.dirty;
    $<HTMLButtonElement>("trip-delete").disabled = saving || draft.dirty || !draft.version;
    $<HTMLButtonElement>("trip-fit").disabled = !draft.stops.length;
    drawRoute();
    renderProgress();
    gps.render(draft.id || "", editing && !actionBusy && !blocked);
  }
  function scheduleSave(delay = 0) {
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      saveTimer = null;
      void save();
    }, delay);
  }
  function changed(record = true, preserveRoute = false) {
    retryAttempts = 0;
    const moved = JSON.stringify(lastStops) !== JSON.stringify(draft.stops);
    if (
      record &&
      (moved ||
        JSON.stringify(routeOptions(lastRouting)) !== JSON.stringify(routeOptions(draft.routing)))
    ) {
      undoHistory.push({ stops: copy(lastStops), routing: copy(lastRouting) });
      if (undoHistory.length > 50) undoHistory.shift();
    }
    if (draft.routing && moved && !preserveRoute)
      draft.routing = {
        ...routeOptions(draft.routing)!,
        status: "pending",
        message: "Calculating roads for edited checkpoints…",
        geometry: [],
      };
    if (draft.stops.length < 2) draft.routing = null;
    lastStops = copy(draft.stops);
    lastRouting = copy(draft.routing);
    serial++;
    draft.dirty = true;
    remember();
    render();
    status.textContent = blocked
      ? "Shared route changed · reload shared or save as new."
      : "Saving changes for everyone…";
    if (!blocked) scheduleSave();
  }
  function removePoint(id: string) {
    if (!editing || actionBusy) return;
    draft.stops = draft.stops.filter((p) => p.id !== id);
    changed();
  }
  function addStop(p: TripStop) {
    if (!editing || actionBusy) return;
    if (!validStop(p)) {
      status.textContent = "This location has invalid coordinates.";
      return;
    }
    if (draft.stops.length >= 256) {
      status.textContent = "A trip can have up to 256 waypoints.";
      return;
    }
    draft.stops.push({ ...p, id: uid() });
    changed();
    $<HTMLDetailsElement>("trip-details").open = true;
  }
  function mode(value: boolean) {
    cancelGesture();
    editing = value;
    arm(null);
    document.querySelector(".saved-app")!.classList.toggle("is-planning", value);
    modeButton.textContent = value ? "Done" : "Plan trip";
    modeButton.setAttribute("aria-pressed", String(value));
    if (value) {
      map.boxZoom.disable();
      $<HTMLDetailsElement>("trip-details").open = true;
    } else if (initialBoxZoom) map.boxZoom.enable();
    if (!value && draft.dirty && !blocked) scheduleSave();
    render();
    if (value) $("controls").scrollTop = 0;
  }
  modeButton.onclick = () => mode(!editing);
  $("trip-clear").onclick = () => {
    if (editing) {
      draft.stops = [];
      draft.routing = null;
      gps.notice();
      changed();
    }
  };
  $("trip-edit-undo").onclick = () => {
    const before = undoHistory.pop();
    if (editing && before) {
      draft.stops = before.stops;
      draft.routing = before.routing;
      gps.notice();
      changed(false, true);
    }
  };
  function load(t: Trip) {
    creationSent = false;
    base = copy(t);
    blocked = false;
    draft = {
      follow_player: t.tracking?.player || null,
      arrival_radius: t.tracking?.radius || 40,
      id: t.id,
      name: t.name.trim(),
      stops: t.stops.map((p) => ({ ...p })),
      version: t.version,
      dirty: false,
      routing: t.routing || null,
    };
    lastStops = copy(draft.stops);
    lastRouting = copy(draft.routing);
    gps.notice();
    remember();
    render();
    status.textContent = "Saved · shared with everyone.";
  }
  function arm(value: typeof armed) {
    armed = value;
    pingButton.setAttribute("aria-pressed", String(value === "ping"));
    $("trip-map-stop").setAttribute("aria-pressed", String(value === "stop"));
    map.getContainer().classList.toggle("placing-map-point", value !== null);
    if (value) {
      $("controls").classList.remove("open");
      notice.hidden = false;
      notice.textContent =
        value === "ping"
          ? "Click the map to ping · Esc cancels"
          : value === "stop"
            ? "Click the map to add a stop · Esc cancels"
            : `Click the map to choose ${value === "from" ? "From" : "To"} · Esc cancels`;
      notice.onclick = () => arm(null);
    } else prunePings();
  }
  function retain(t: Trip) {
    const newer = trips.find((p) => p.id === t.id && p.version > t.version);
    trips = [newer || t, ...trips.filter((p) => p.id !== t.id)];
  }
  function syncCurrent() {
    const current = trips.find((t) => t.id === draft.id);
    if (saving || gesture) return;
    if (!current) {
      if (draft.version && snapshotReady) {
        blocked = true;
        draft.dirty = true;
        remember();
        status.textContent = "This shared trip was deleted. Save as new to keep your local route.";
        render();
      }
      return;
    }
    if (!draft.dirty) {
      if (current.version !== draft.version) {
        if (base && JSON.stringify(base.stops) !== JSON.stringify(current.stops)) undoHistory = [];
        load(current);
      }
      return;
    }
    if (current.version === draft.version) {
      base ||= copy(current);
      return;
    }
    if (!draft.version) {
      base = copy(current);
      draft.version = current.version;
      remember();
      return;
    }
    if (base && editable(current) === editable(base)) {
      base = copy(current);
      draft.version = current.version;
      remember();
    } else {
      blocked = true;
      status.textContent = "This route changed in another viewer. Reload shared or save as new.";
      render();
    }
  }
  async function save(asNew = false) {
    if (asNew) {
      if (saving) return;
      draft.id = uid();
      draft.version = 0;
      creationSent = false;
      base = null;
      blocked = false;
      serial++;
      draft.dirty = true;
      remember();
    }
    if (blocked || !draft.dirty) return;
    if (saving || gesture) {
      scheduleSave(100);
      return;
    }
    if (!connected || !snapshotReady) {
      status.textContent = "Changes waiting to sync · local copy kept.";
      return;
    }
    if (!draft.version && !draft.stops.length && !creationSent) {
      draft.dirty = false;
      remember();
      render();
      status.textContent = "Place the first waypoint to start sharing this trip.";
      return;
    }
    if (!draft.name.trim()) {
      status.textContent = "Give this trip a name to sync it.";
      return;
    }
    syncCurrent();
    if (blocked) return;
    const sent = copy(draft),
      sentSerial = serial;
    sent.name = sent.name.trim();
    saving = true;
    render();
    status.textContent = "Saving changes for everyone…";
    let retry = false;
    try {
      if (!sent.version) {
        creationSent = true;
        remember();
      }
      const saved = await api<Trip>(
        sent.version ? `/api/v1/trips/${sent.id}` : "/api/v1/trips",
        sent.version ? "PUT" : "POST",
        {
          ...(sent.version ? {} : { id: sent.id }),
          name: sent.name,
          stops: sent.stops,
          version: sent.version,
          follow_player: sent.follow_player,
          arrival_radius: sent.arrival_radius,
          routing: sent.routing
            ? { ...routeOptions(sent.routing), request_id: sent.routing.request_id || null }
            : null,
        },
      );
      retain(saved);
      base = copy(saved);
      retryAttempts = 0;
      if (serial === sentSerial && !gesture && editable(saved) === editable(sent)) load(saved);
      else {
        draft.version = saved.version;
        remember();
        retry = true;
      }
    } catch (e) {
      status.textContent = "Changes waiting to sync · " + (e as Error).message;
      if (
        !(e as { status?: number }).status ||
        [404, 409].includes((e as { status: number }).status)
      )
        try {
          const snapshot = await api<Snapshot>("/api/v1/trips");
          trips = snapshot.trips;
          const current = trips.find((t) => t.id === draft.id);
          if (current && editable(current) === editable(sent)) {
            base = copy(current);
            draft.version = current.version;
            if (serial === sentSerial && !gesture) load(current);
            else retry = true;
            remember();
          } else if (current && (!sent.version || (base && editable(current) === editable(base)))) {
            base = copy(current);
            draft.version = current.version;
            remember();
            retry = ++retryAttempts <= 3;
          } else if ((current && current.version !== sent.version) || (!current && sent.version)) {
            blocked = true;
            status.textContent = "This route changed or was deleted. Reload shared or save as new.";
          }
        } catch {
          /* Keep the local copy for Retry sync or reconnect. */
        }
    } finally {
      saving = false;
      syncCurrent();
      render();
      if (retry && !blocked) scheduleSave(retryAttempts ? 1500 : 0);
    }
  }
  function playerChoices() {
    const field = $<HTMLSelectElement>("trip-player");
    field.replaceChildren(
      new Option("Manual progress only", ""),
      ...players.map((n) => new Option(n, n)),
    );
    if (draft.follow_player && !players.includes(draft.follow_player))
      field.add(new Option(draft.follow_player, draft.follow_player));
    field.value = draft.follow_player || "";
  }
  function renderProgress() {
    syncBadge.hidden = !draft.version && !draft.dirty;
    syncBadge.textContent = draft.dirty
      ? saving
        ? "Saving…"
        : blocked
          ? "Sync conflict"
          : "Changes waiting to sync"
      : "Saved";
    const t = trips.find((t) => t.id === draft.id)?.tracking,
      ready = Boolean(t && !draft.dirty && !gesture),
      count = draft.stops.length,
      reached = reachedIds();
    const legs = draft.stops
        .slice(1)
        .filter((p, i) => reached.has(p.id!) && reached.has(draft.stops[i].id!)).length,
      next = draft.stops.find((p) => !reached.has(p.id!));
    const bar = $<HTMLProgressElement>("trip-progress-bar");
    bar.max = Math.max(1, count - 1);
    bar.value = legs;
    $("trip-progress").textContent = ready
      ? `${legs} / ${Math.max(0, count - 1)} legs complete · ${progressError && t!.active ? progressError : t!.note}${next ? " · Next: " + next.label : ""}`
      : "Changes waiting to sync before checkpoint controls are available.";
    $<HTMLButtonElement>("trip-start").disabled =
      saving || !ready || count < 2 || !t?.player || t.active || !next;
    $<HTMLButtonElement>("trip-pause").disabled = saving || !ready || !t?.active;
    $<HTMLButtonElement>("trip-next").disabled = saving || !ready || count < 2 || !next;
    $<HTMLButtonElement>("trip-reset").disabled = saving || !ready;
  }
  async function progress(action: string) {
    if (!draft.id || draft.dirty || saving) return;
    saving = true;
    actionBusy = true;
    render();
    try {
      const saved = await api<Trip>(`/api/v1/trips/${draft.id}/progress`, "POST", {
        action,
        version: draft.version,
      });
      retain(saved);
      load(saved);
      status.textContent = saved.tracking.note;
    } catch (e) {
      status.textContent = (e as Error).message;
    } finally {
      saving = false;
      actionBusy = false;
      syncCurrent();
      render();
    }
  }
  async function recalculate() {
    if (!draft.id || draft.dirty || saving || !draft.routing) return;
    saving = true;
    actionBusy = true;
    gps.notice();
    render();
    try {
      const saved = await api<Trip>(`/api/v1/trips/${draft.id}/reroute`, "POST", {
        version: draft.version,
      });
      retain(saved);
      load(saved);
    } catch (e) {
      status.textContent = (e as Error).message;
    } finally {
      saving = false;
      actionBusy = false;
      syncCurrent();
      render();
    }
  }
  for (const action of ["start", "pause", "next", "reset"])
    $(`trip-${action}`).onclick = () => void progress(action);
  $<HTMLSelectElement>("trip-player").onchange = () => {
    draft.follow_player = $<HTMLSelectElement>("trip-player").value || null;
    changed();
  };
  $<HTMLInputElement>("trip-radius").onchange = () => {
    const value = Number($<HTMLInputElement>("trip-radius").value);
    if (!Number.isInteger(value) || value < 10 || value > 200) {
      status.textContent = "Choose an arrival radius from 10 to 200 tiles.";
      return;
    }
    draft.arrival_radius = value;
    changed();
  };
  name.oninput = () => {
    select.disabled = true;
    $<HTMLButtonElement>("trip-new").disabled = true;
    $<HTMLButtonElement>("trip-delete").disabled = true;
    retryAttempts = 0;
    draft.name = name.value;
    serial++;
    draft.dirty = true;
    remember();
    renderProgress();
    status.textContent = "Saving changes for everyone…";
    if (!blocked) scheduleSave(400);
  };
  select.onchange = () => {
    if (draft.dirty || saving) {
      render();
      return;
    }
    undoHistory = [];
    const t = trips.find((t) => t.id === select.value);
    if (t) load(t);
    else {
      draft = empty();
      creationSent = false;
      base = null;
      lastStops = [];
      lastRouting = null;
      remember();
      mode(true);
      status.textContent = "Shift + click to place the starting point.";
    }
  };
  $("trip-new").onclick = () => {
    if (draft.dirty || saving) return;
    draft = empty();
    creationSent = false;
    base = null;
    undoHistory = [];
    lastStops = [];
    lastRouting = null;
    remember();
    mode(true);
    status.textContent = "Shift + click to place the starting point.";
  };
  $("trip-reference").onclick = () => addStop(reference());
  $("trip-map-stop").onclick = () => arm(armed === "stop" ? null : "stop");
  $("trip-save").onclick = () => {
    blocked = false;
    retryAttempts = 0;
    void save();
  };
  $("trip-copy").onclick = () => void save(true);
  $("trip-reload").onclick = async () => {
    if (!draft.id || saving) return;
    saving = true;
    actionBusy = true;
    render();
    try {
      const snapshot = await api<Snapshot>("/api/v1/trips");
      trips = snapshot.trips;
      revision = snapshot.revision;
      undoHistory = [];
      const saved = trips.find((t) => t.id === draft.id);
      if (!saved) throw new Error("This trip was deleted. Save your draft as new.");
      load(saved);
    } catch (e) {
      status.textContent = (e as Error).message;
    } finally {
      saving = false;
      actionBusy = false;
      syncCurrent();
      render();
    }
  };
  $("trip-fit").onclick = () => {
    if (draft.stops.length) {
      onNavigate();
      map.fitBounds(
        [
          ...draft.stops.map(xy),
          ...(draft.routing?.geometry || []).flatMap((g) =>
            knownParts(g.points, known).flatMap((part) =>
              part.map(([x, y]) => [-y, x] as L.LatLngTuple),
            ),
          ),
        ],
        { padding: [55, 55], maxZoom: 1 },
      );
      $("controls").classList.remove("open");
    }
  };
  $<HTMLInputElement>("show-trip").onchange = () => {
    if ($<HTMLInputElement>("show-trip").checked) route.addTo(map);
    else route.remove();
  };
  $("trip-delete").onclick = async () => {
    if (!draft.id || saving) return;
    saving = true;
    actionBusy = true;
    render();
    try {
      deleted = await api<Trip>(`/api/v1/trips/${draft.id}?version=${draft.version}`, "DELETE");
      trips = trips.filter((t) => t.id !== draft.id);
      draft = empty();
      creationSent = false;
      base = null;
      lastStops = [];
      undoHistory = [];
      remember();
      $("trip-undo").hidden = false;
      status.textContent = "Shared trip deleted. You can undo this deletion.";
    } catch (e) {
      status.textContent = (e as Error).message;
    } finally {
      saving = false;
      actionBusy = false;
      syncCurrent();
      render();
    }
  };
  $("trip-undo").onclick = async () => {
    if (!deleted || saving) return;
    saving = true;
    actionBusy = true;
    render();
    try {
      const saved = await api<Trip>(`/api/v1/trips/${deleted.id}/restore`, "POST");
      trips = [saved, ...trips];
      load(saved);
      deleted = null;
      $("trip-undo").hidden = true;
      status.textContent = "Trip restored for everyone.";
    } catch (e) {
      status.textContent = (e as Error).message;
    } finally {
      saving = false;
      actionBusy = false;
      syncCurrent();
      render();
    }
  };
  async function ping(x: number, y: number) {
    if (!connected) {
      pingStatus.textContent = "Pings are reconnecting. Try again shortly.";
      return;
    }
    try {
      await api("/api/v1/pings", "POST", {
        owner,
        x: Math.round(x * 100) / 100,
        y: Math.round(y * 100) / 100,
      });
      pingStatus.textContent = "Ping shared · visible for 12 seconds";
    } catch (e) {
      pingStatus.textContent = (e as Error).message;
    }
  }
  pingButton.onclick = () => arm(armed === "ping" ? null : "ping");
  function mapPoint(e: MouseEvent): TripStop {
    const ll = map.mouseEventToLatLng(e);
    return {
      id: uid(),
      label: `Map point ${Math.round(ll.lng)}, ${Math.round(-ll.lat)}`,
      x: ll.lng,
      y: -ll.lat,
      z: 0,
    };
  }
  type Gesture = {
    pointer: number;
    start: L.Point;
    before: TripStop[];
    kind: "move" | "edge" | "add";
    index: number;
    moved: boolean;
    restorePan: boolean;
  };
  let gesture: Gesture | null = null,
    suppressClickUntil = 0;
  let armedPointer: { id: number; start: L.Point; moved: boolean } | null = null;
  const mapHost = map.getContainer();
  function finishGesture(cancel = false) {
    const g = gesture;
    if (!g) return;
    gesture = null;
    suppressClickUntil = performance.now() + 100;
    if (mapHost.hasPointerCapture(g.pointer)) mapHost.releasePointerCapture(g.pointer);
    if (g.restorePan) map.dragging.enable();
    if (cancel) {
      draft.stops = g.before;
      remember();
      render();
      syncCurrent();
      return;
    }
    if (g.moved && g.kind !== "add") changed();
    else if (!g.moved && g.kind !== "move") addStop({ ...mapPointFromPixel(g.start) });
    render();
    syncCurrent();
  }
  function mapPointFromPixel(pixel: L.Point): TripStop {
    const ll = map.containerPointToLatLng(pixel);
    return {
      label: `Map point ${Math.round(ll.lng)}, ${Math.round(-ll.lat)}`,
      x: ll.lng,
      y: -ll.lat,
      z: 0,
    };
  }
  function cancelGesture() {
    armedPointer = null;
    finishGesture(true);
  }
  function pointerDown(e: PointerEvent) {
    if (
      armed &&
      !e.ctrlKey &&
      e.button === 0 &&
      !(e.target as Element).closest(".leaflet-control,.leaflet-popup")
    ) {
      if (armedPointer) {
        armedPointer.moved = true;
        return;
      }
      armedPointer = { id: e.pointerId, start: map.mouseEventToContainerPoint(e), moved: false };
      return;
    }
    if (
      !editing ||
      actionBusy ||
      e.ctrlKey ||
      e.button !== 0 ||
      (e.target as Element).closest(".leaflet-control,.leaflet-popup")
    )
      return;
    const marker = (e.target as Element).closest<HTMLElement>(".trip-pin");
    const index = marker ? draft.stops.findIndex((p) => p.id === marker.dataset.waypointId) : -1;
    if (index < 0 && !e.shiftKey) return;
    const pixel = map.mouseEventToContainerPoint(e);
    let edge = -1,
      best = 10;
    if (index < 0 && map.hasLayer(route)) {
      const edges = draft.routing
        ? (draft.routing.geometry || []).flatMap((segment) =>
            knownParts(segment.points, known).flatMap((part) =>
              part.slice(1).map((p, i) => ({
                a: { x: part[i][0], y: part[i][1] },
                b: { x: p[0], y: p[1] },
                index: draft.stops.findIndex((s) => s.id === segment.to),
              })),
            ),
          )
        : draft.stops.slice(1).map((p, i) => ({ a: draft.stops[i], b: p, index: i + 1 }));
      for (const segment of edges) {
        if (segment.index < 1) continue;
        const a = map.latLngToContainerPoint(xy(segment.a)),
          b = map.latLngToContainerPoint(xy(segment.b));
        const dx = b.x - a.x,
          dy = b.y - a.y,
          t = Math.max(
            0,
            Math.min(1, ((pixel.x - a.x) * dx + (pixel.y - a.y) * dy) / (dx * dx + dy * dy || 1)),
          );
        const distance = pixel.distanceTo(L.point(a.x + t * dx, a.y + t * dy));
        if (distance < best) {
          best = distance;
          edge = segment.index;
        }
      }
    }
    gesture = {
      pointer: e.pointerId,
      start: pixel,
      before: copy(draft.stops),
      kind: index >= 0 ? "move" : edge >= 0 ? "edge" : "add",
      index: index >= 0 ? index : edge,
      moved: false,
      restorePan: map.dragging.enabled(),
    };
    e.preventDefault();
    e.stopImmediatePropagation();
    map.dragging.disable();
    mapHost.setPointerCapture(e.pointerId);
  }
  function pointerMove(e: PointerEvent) {
    if (armedPointer?.id === e.pointerId) {
      if (armedPointer.start.distanceTo(map.mouseEventToContainerPoint(e)) >= 6)
        armedPointer.moved = true;
      return;
    }
    const g = gesture;
    if (!g || g.pointer !== e.pointerId) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    if (!g.moved && g.start.distanceTo(map.mouseEventToContainerPoint(e)) < 6) return;
    if (!g.moved && g.kind === "edge") {
      if (draft.stops.length >= 256) {
        cancelGesture();
        status.textContent = "A trip can have up to 256 waypoints.";
        return;
      }
      draft.stops.splice(g.index, 0, mapPoint(e));
    }
    g.moved = true;
    if (g.kind === "add") return;
    const point = mapPoint(e);
    if (!validStop(point)) return;
    const old = draft.stops[g.index];
    draft.stops[g.index] = {
      ...old,
      x: point.x,
      y: point.y,
      kind: "manual",
      label: old.label.startsWith("Map point ") ? point.label : old.label,
    };
    drawRoute();
  }
  function pointerUp(e: PointerEvent) {
    if (armedPointer?.id === e.pointerId) {
      pointerMove(e);
      const placement = armed,
        point = mapPoint(e),
        moved = armedPointer.moved;
      armedPointer = null;
      if (placement && !moved) {
        e.preventDefault();
        e.stopImmediatePropagation();
        suppressClickUntil = performance.now() + 400;
        arm(null);
        if (placement === "ping") void ping(point.x, point.y);
        else if (placement === "from" || placement === "to") {
          gps.set(placement, point);
          $("controls").classList.add("open");
        } else addStop(point);
      }
      return;
    }
    if (gesture?.pointer !== e.pointerId) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    pointerMove(e);
    const g = gesture;
    finishGesture();
    if (g?.kind === "move" && !g.moved) {
      route.eachLayer((layer) => {
        if (
          layer instanceof L.Marker &&
          layer.getElement()?.dataset.waypointId === draft.stops[g.index]?.id
        )
          layer.openPopup();
      });
    }
  }
  function context(e: MouseEvent) {
    const marker = (e.target as Element).closest<HTMLElement>(".trip-pin");
    if (editing && marker) {
      e.preventDefault();
      e.stopImmediatePropagation();
      removePoint(marker.dataset.waypointId!);
    }
  }
  function mouse(e: MouseEvent) {
    if (gesture) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }
  function click(e: L.LeafletMouseEvent) {
    if (
      performance.now() < suppressClickUntil ||
      e.originalEvent.ctrlKey ||
      e.originalEvent.metaKey ||
      (e.originalEvent.target as Element).closest(".leaflet-control,.leaflet-popup")
    )
      return;
    if (armed === "ping") {
      arm(null);
      void ping(e.latlng.lng, -e.latlng.lat);
    } else if (editing && (armed === "from" || armed === "to")) {
      const which = armed;
      arm(null);
      gps.set(which, {
        label: `Map point ${Math.round(e.latlng.lng)}, ${Math.round(-e.latlng.lat)}`,
        x: e.latlng.lng,
        y: -e.latlng.lat,
        z: 0,
      });
      $("controls").classList.add("open");
    } else if (editing && armed === "stop") {
      arm(null);
      addStop({
        label: `Map point ${Math.round(e.latlng.lng)}, ${Math.round(-e.latlng.lat)}`,
        x: e.latlng.lng,
        y: -e.latlng.lat,
        z: 0,
      });
    }
  }
  function armedClick(e: MouseEvent) {
    if (
      performance.now() < suppressClickUntil &&
      !(e.target as Element).closest(".leaflet-control,.leaflet-popup")
    ) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }
  function key(e: KeyboardEvent) {
    if (e.key === "Escape") {
      cancelGesture();
      arm(null);
    }
  }
  mapHost.addEventListener("click", armedClick, true);
  mapHost.addEventListener("pointerdown", pointerDown, true);
  mapHost.addEventListener("pointermove", pointerMove, true);
  mapHost.addEventListener("pointerup", pointerUp, true);
  mapHost.addEventListener("pointercancel", cancelGesture);
  mapHost.addEventListener("lostpointercapture", cancelGesture);
  mapHost.addEventListener("contextmenu", context, true);
  mapHost.addEventListener("mousedown", mouse, true);
  window.addEventListener("blur", cancelGesture);
  map.on("click", click);
  window.addEventListener("keydown", key);
  function prunePings() {
    for (const [id, { marker, ping }] of pins)
      if (ping.expires_at <= Date.now()) {
        marker.remove();
        pins.delete(id);
      }
    if (armed) return;
    const newest = [...pins.values()].sort((a, b) => b.ping.created_at - a.ping.created_at)[0]
      ?.ping;
    notice.hidden = !newest;
    if (newest) {
      notice.textContent = `Look here · ${Math.round(newest.x)}, ${Math.round(newest.y)}`;
      notice.onclick = () => focus(newest);
    }
  }
  function receive(snapshot: Snapshot) {
    progressError = snapshot.progress_error || null;
    if (snapshot.revision !== revision) {
      revision = snapshot.revision;
      trips = snapshot.trips;
      choices();
      snapshotReady = true;
      syncCurrent();
      if (!draft.dirty && !saving && !gesture) {
        drawRoute();
        if (!draft.version) status.textContent = "Enter planning mode to create a trip.";
      }
      if (draft.dirty && !saving && !blocked) scheduleSave();
    }
    const ids = new Set(snapshot.pings.map((p) => p.id));
    for (const [id, { marker }] of pins)
      if (!ids.has(id)) {
        marker.remove();
        pins.delete(id);
      }
    for (const p of snapshot.pings)
      if (!pins.has(p.id) && p.expires_at > Date.now()) {
        const icon = L.divIcon({
          className: "shared-ping",
          html: '<span class="ping-ring"></span><span class="ping-center"></span>',
          iconSize: [40, 40],
          iconAnchor: [20, 20],
        });
        const marker = L.marker(xy(p), { icon, pane: "shared-pings", interactive: false })
          .bindTooltip(element("span", "Look here"), {
            permanent: true,
            direction: "bottom",
            offset: [0, 16],
            className: "ping-label",
            opacity: 1,
          })
          .addTo(map);
        pins.set(p.id, { marker, ping: p });
      }
    prunePings();
    renderProgress();
  }
  const opened = () => {
    connected = true;
    if (status.textContent?.startsWith("Shared trips reconnecting"))
      status.textContent = draft.dirty
        ? "Reconnected · unsaved draft kept."
        : "Shared trips connected.";
    pingStatus.textContent = "Ctrl + click, or place a ping · expires after 12 seconds";
    if (draft.dirty && !blocked) scheduleSave();
  };
  const failed = () => {
    connected = false;
    pingStatus.textContent = "Pings reconnecting…";
    status.textContent = "Shared trips reconnecting · your draft stays here.";
  };
  const updated = (e: Event) => receive(JSON.parse((e as MessageEvent).data));
  events.addEventListener("open", opened);
  events.addEventListener("error", failed);
  events.addEventListener("planning", updated);
  const timer = setInterval(prunePings, 250);
  render();
  return {
    addStop,
    ping,
    updateGPS() {
      gps.render(draft.id || "", editing && !actionBusy && !blocked);
    },
    updateCoverage(c: CityCoverage) {
      known = new Set(c.cells.map(([x, y]) => `${x},${y}`));
      gps.updateCities(c.city_labels || []);
      render();
    },
    clearCoverage() {
      known.clear();
      gps.updateCities([]);
      render();
    },
    updatePositionFeed(feed: PositionFeed | undefined) {
      $("checkpoint-source-note").textContent = feed?.enabled
        ? "Start treats stop 1 as the departure point. Later stops resolve from fresh live positions, about once a second, on the same floor. Tracking waits during an outage. Resume after reconnecting or changing character. New or moved points need reaching."
        : "Start treats stop 1 as the departure point. Later stops resolve in order from fresh saved positions, about every 3 minutes. Brief visits may be missed. New or moved points need reaching; unchanged reached points keep their status.";
    },
    updatePlayers(names: string[]) {
      if (JSON.stringify(names) === JSON.stringify(players)) return;
      players = names;
      playerChoices();
    },
    dispose() {
      gps.dispose();
      if (saveTimer) clearTimeout(saveTimer);
      cancelGesture();
      modeTools.remove();
      mapHost.removeEventListener("click", armedClick, true);
      mapHost.removeEventListener("pointerdown", pointerDown, true);
      mapHost.removeEventListener("pointermove", pointerMove, true);
      mapHost.removeEventListener("pointerup", pointerUp, true);
      mapHost.removeEventListener("pointercancel", cancelGesture);
      mapHost.removeEventListener("lostpointercapture", cancelGesture);
      mapHost.removeEventListener("contextmenu", context, true);
      mapHost.removeEventListener("mousedown", mouse, true);
      window.removeEventListener("blur", cancelGesture);
      events.removeEventListener("open", opened);
      events.removeEventListener("error", failed);
      events.removeEventListener("planning", updated);
      clearInterval(timer);
      map.off("click", click);
      window.removeEventListener("keydown", key);
      route.remove();
      for (const { marker } of pins.values()) marker.remove();
      notice.remove();
    },
  };
}
