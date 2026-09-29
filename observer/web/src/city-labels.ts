import L from "leaflet";

type CityLabel = { id: string; label: string; x: number; y: number };
export type CityCoverage = { revision: number; cells: number[][]; city_labels?: CityLabel[] };
const namespace = "http://www.w3.org/2000/svg";

function svg<K extends keyof SVGElementTagNameMap>(tag: K, attrs: Record<string, string | number>) {
  const element = document.createElementNS(namespace, tag);
  for (const [key, value] of Object.entries(attrs)) element.setAttribute(key, String(value));
  return element;
}

export function enableCityLabels(map: L.Map) {
  const pane = map.createPane("cityLabels");
  pane.style.zIndex = "350"; // Above terrain, below routes, players and notes.
  pane.style.pointerEvents = "none";
  const layer = L.layerGroup().addTo(map);
  let labels: CityLabel[] = [],
    known = new Set<string>(),
    zooming = false;

  function render() {
    if (zooming) return;
    layer.clearLayers();
    const zoom = map.getZoom(),
      scale = 2 ** zoom,
      fontSize = Math.max(14, Math.min(26, 22 + zoom * 2));
    for (const [index, city] of labels.entries()) {
      if (!known.has(`${Math.floor(city.x / 32) * 32},${Math.floor(city.y / 32) * 32}`)) continue;
      const width = Math.ceil(city.label.length * (fontSize + 2) + 16),
        height = fontSize * 2;
      const anchor = map.project([-city.y, city.x], zoom).round();
      const left = (anchor.x - width / 2) / scale,
        top = (anchor.y - height / 2) / scale;
      const clipId = `city-label-clip-${index}`;
      const image = svg("svg", {
        width,
        height,
        viewBox: `0 0 ${width} ${height}`,
        role: "img",
        "aria-label": city.label,
      });
      const defs = svg("defs", {}),
        clip = svg("clipPath", { id: clipId, clipPathUnits: "userSpaceOnUse" });
      // Clip the letters themselves, not just the anchor, so text cannot spill
      // into unknown terrain when zooming out or at an exploration boundary.
      for (let x = Math.floor(left / 32) * 32; x < left + width / scale; x += 32) {
        for (let y = Math.floor(top / 32) * 32; y < top + height / scale; y += 32) {
          if (known.has(`${x},${y}`))
            clip.append(
              svg("rect", {
                x: x * scale - anchor.x + width / 2,
                y: y * scale - anchor.y + height / 2,
                width: 32 * scale,
                height: 32 * scale,
              }),
            );
        }
      }
      const text = svg("text", {
        x: width / 2,
        y: height / 2,
        "text-anchor": "middle",
        "dominant-baseline": "central",
        "font-size": fontSize,
        "clip-path": `url(#${clipId})`,
        class: "city-label-text",
      });
      text.textContent = city.label.toUpperCase();
      defs.append(clip);
      image.append(defs, text);
      L.marker([-city.y, city.x], {
        pane: "cityLabels",
        interactive: false,
        keyboard: false,
        icon: L.divIcon({
          className: "city-label",
          iconSize: [width, height],
          iconAnchor: [width / 2, height / 2],
          html: image.outerHTML,
        }),
      }).addTo(layer);
    }
    pane.style.visibility = "";
  }
  const hideDuringZoom = () => {
    zooming = true;
    pane.style.visibility = "hidden";
  };
  const finishZoom = () => {
    zooming = false;
    render();
  };
  map.on("zoomstart", hideDuringZoom).on("zoomend", finishZoom);
  return {
    layer,
    update(coverage: CityCoverage) {
      labels = coverage.city_labels || [];
      known = new Set(coverage.cells.filter((c) => c[2]).map(([x, y]) => `${x},${y}`));
      render();
    },
    clear() {
      labels = [];
      known.clear();
      layer.clearLayers();
    },
    dispose() {
      map.off("zoomstart", hideDuringZoom).off("zoomend", finishZoom);
      layer.remove();
      pane.remove();
    },
  };
}
