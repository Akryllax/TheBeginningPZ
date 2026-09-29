import L from "leaflet";

type Stroke = { id: string; points: [number, number][]; color?: string };
function uuid() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function enableSharedDrawing(map: L.Map, ping: (x: number, y: number) => void) {
  // The random owner capability stays private; broadcasts never include it.
  let owner = uuid();
  try {
    owner = sessionStorage.getItem("observer-drawing-owner") || owner;
    sessionStorage.setItem("observer-drawing-owner", owner);
  } catch {
    /* Private storage may be disabled. */
  }
  const host = map.getContainer();
  const status = document.getElementById("drawing-status")!;
  const clear = document.getElementById("clear-drawings")! as HTMLButtonElement;
  map.createPane("drawings").style.pointerEvents = "none";
  const renderer = L.svg({ pane: "drawings" });
  const lines = new Map<string, L.Polyline>();
  const local = new Map<string, Stroke>();
  let active: Stroke | null = null,
    inFlight: Promise<void> | null = null;
  const pending = new Map<string, Stroke>();
  let pointer: number | null = null,
    restoreDrag = false,
    connected = false,
    clearing = false;
  let lastPixel: L.Point | null = null,
    start: PointerEvent | null = null;
  let dragged = false;
  function render(stroke: Stroke) {
    const newest = local.get(stroke.id);
    const points =
      newest && newest.points.length > stroke.points.length ? newest.points : stroke.points;
    const coords = points.map(([x, y]): L.LatLngTuple => [-y, x]);
    let line = lines.get(stroke.id);
    if (!line) {
      line = L.polyline(coords, {
        renderer,
        pane: "drawings",
        color: stroke.color || "#ffcc33",
        weight: 4,
        opacity: 0.95,
        interactive: false,
        className: "shared-drawing",
      }).addTo(map);
      lines.set(stroke.id, line);
    } else {
      line.setLatLngs(coords);
      if (stroke.color) line.setStyle({ color: stroke.color });
    }
  }
  function remove(id: string) {
    lines.get(id)?.remove();
    lines.delete(id);
    local.delete(id);
  }
  const events = new EventSource("/api/v1/drawings/events");
  events.onopen = () => {
    connected = true;
    status.textContent = "Live drawing connected · expires after 5 minutes";
  };
  events.onerror = () => {
    connected = false;
    finish();
    status.textContent = "Drawing disconnected · reconnecting…";
  };
  events.addEventListener("reset", () => {
    for (const line of lines.values()) line.remove();
    lines.clear();
    local.clear();
  });
  events.addEventListener("drawings", (event) => {
    const update = JSON.parse((event as MessageEvent).data) as {
      strokes: Stroke[];
      removed: string[];
    };
    update.removed.forEach(remove);
    update.strokes.forEach(render);
  });
  function flush() {
    if (inFlight || !pending.size || clearing) return;
    const stroke = pending.values().next().value!;
    pending.delete(stroke.id);
    const body = JSON.stringify({ id: stroke.id, owner, points: stroke.points });
    inFlight = (async () => {
      try {
        const response = await fetch("/api/v1/drawings", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body,
        });
        if (!response.ok) throw new Error(String(response.status));
      } catch {
        status.textContent = "Drawing could not be shared · try again";
        remove(stroke.id);
        if (active?.id === stroke.id) finish();
        pending.delete(stroke.id);
      }
    })().finally(() => {
      inFlight = null;
      flush();
    });
  }
  function add(event: PointerEvent) {
    if (!active) return;
    const pixel = map.mouseEventToContainerPoint(event);
    if (lastPixel && lastPixel.distanceTo(pixel) < 3) return;
    const ll = map.containerPointToLatLng(pixel);
    if (Math.abs(ll.lng) > 200000 || Math.abs(ll.lat) > 200000) return;
    lastPixel = pixel;
    active.points.push([Math.round(ll.lng * 100) / 100, Math.round(-ll.lat * 100) / 100]);
    render(active);
    pending.set(active.id, active);
    if (active.points.length >= 1024) finish();
  }
  function finish() {
    const released = pointer;
    pointer = null;
    start = null;
    if (released !== null && host.hasPointerCapture(released)) host.releasePointerCapture(released);
    active = null;
    lastPixel = null;
    host.classList.remove("is-drawing");
    if (restoreDrag) map.dragging.enable();
    restoreDrag = false;
    flush();
  }
  function down(event: PointerEvent) {
    if (!event.ctrlKey || event.button !== 0 || event.pointerType !== "mouse" || clearing) return;
    if ((event.target as Element).closest(".leaflet-control,.leaflet-popup")) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    restoreDrag = map.dragging.enabled();
    map.dragging.disable();
    pointer = event.pointerId;
    host.setPointerCapture(pointer);
    start = event;
    dragged = false;
  }
  function move(event: PointerEvent) {
    if (pointer === event.pointerId) {
      event.preventDefault();
      event.stopImmediatePropagation();
      if (
        start &&
        !dragged &&
        Math.hypot(event.clientX - start.clientX, event.clientY - start.clientY) >= 6
      ) {
        dragged = true;
        if (connected) {
          active = { id: uuid(), points: [] };
          local.set(active.id, active);
          host.classList.add("is-drawing");
          add(start);
        }
      }
      add(event);
    }
  }
  function up(event: PointerEvent) {
    if (pointer === event.pointerId) {
      event.preventDefault();
      event.stopImmediatePropagation();
      move(event);
      const click = !dragged && start;
      const ll = click ? map.mouseEventToLatLng(start!) : null;
      add(event);
      finish();
      if (ll) ping(ll.lng, -ll.lat);
    }
  }
  // Capture before Leaflet's mouse handling so ordinary left-drag still pans.
  function mouse(event: MouseEvent) {
    if ((event.target as Element).closest(".leaflet-control,.leaflet-popup")) return;
    if (pointer !== null || (event.ctrlKey && event.button === 0)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }
  function key(event: KeyboardEvent) {
    if (event.key === "Escape") finish();
  }
  host.addEventListener("pointerdown", down, true);
  host.addEventListener("pointermove", move, true);
  host.addEventListener("pointerup", up, true);
  host.addEventListener("pointercancel", finish);
  host.addEventListener("lostpointercapture", finish);
  host.addEventListener("mousedown", mouse, true);
  window.addEventListener("blur", finish);
  window.addEventListener("keydown", key);
  const timer = setInterval(flush, 80);
  clear.onclick = async () => {
    clearing = true;
    clear.disabled = true;
    finish();
    pending.clear();
    await inFlight;
    try {
      const response = await fetch(`/api/v1/drawings/${owner}`, { method: "DELETE" });
      if (!response.ok) throw new Error();
      for (const id of [...local.keys()]) remove(id);
    } catch {
      status.textContent = "Could not clear drawings · try again";
    } finally {
      clearing = false;
      clear.disabled = false;
    }
  };
  return {
    dispose() {
      clearInterval(timer);
      events.close();
      pending.clear();
      finish();
      host.removeEventListener("pointerdown", down, true);
      host.removeEventListener("pointermove", move, true);
      host.removeEventListener("pointerup", up, true);
      host.removeEventListener("pointercancel", finish);
      host.removeEventListener("lostpointercapture", finish);
      host.removeEventListener("mousedown", mouse, true);
      window.removeEventListener("blur", finish);
      window.removeEventListener("keydown", key);
    },
  };
}
