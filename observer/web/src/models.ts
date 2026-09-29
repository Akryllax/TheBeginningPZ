import * as T from "three";
import { mergeGeometries } from "three/addons/utils/BufferGeometryUtils.js";
import type { Element } from "./types";

export const palette: Record<string, string> = {
  floor: "#708570",
  wall: "#e0d7bf",
  door: "#997857",
  window: "#8cb3ac",
  roof: "#627c73",
  stairs: "#b5ab95",
  fence: "#b7a486",
  tree: "#557757",
  vegetation: "#78955b",
  furniture: "#c6a783",
  container: "#ac9574",
  vehicle: "#d5a257",
  zombie: "#ab7970",
  animal: "#b7a886",
  item: "#c9bc88",
  unknown: "#b393b4",
};

export function templateKey(o: Element): string {
  if (o.kind !== "furniture" && o.kind !== "container") return o.kind;
  const name = (o.state.variant + " " + o.label + " " + o.sprite).toLowerCase();
  for (const kind of [
    "bed",
    "chair",
    "table",
    "shelf",
    "counter",
    "cabinet",
    "sofa",
    "fridge",
    "sink",
    "toilet",
  ]) {
    if (name.includes(kind)) return kind;
  }
  return o.kind;
}

export function template(kind: string): T.BufferGeometry {
  const parts: T.BufferGeometry[] = [];
  function add(g: T.BufferGeometry, x: number, y: number, z: number, color = "#ffffff") {
    g.translate(x, y, z);
    const count = g.getAttribute("position").count,
      c = new T.Color(color),
      colors = new Float32Array(count * 3);
    for (let i = 0; i < count; i++) c.toArray(colors, i * 3);
    g.setAttribute("color", new T.BufferAttribute(colors, 3));
    parts.push(g);
  }
  const box = (w: number, h: number, d: number, x = 0, y = h / 2, z = 0, c = "#ffffff") =>
    add(new T.BoxGeometry(w, h, d), x, y, z, c);
  const cylinder = (
    rt: number,
    rb: number,
    h: number,
    x: number,
    y: number,
    z: number,
    c = "#ffffff",
  ) => add(new T.CylinderGeometry(rt, rb, h, 7), x, y, z, c);
  switch (kind) {
    case "floor":
      box(1, 0.055, 1, 0, -0.035);
      break;
    case "wall":
      box(1, 1, 0.09);
      break;
    case "door":
      box(0.82, 0.96, 0.1, 0, 0.48);
      box(0.08, 0.08, 0.14, 0.29, 0.46, 0, "#d6b473");
      break;
    case "window":
      box(1, 0.33, 0.09, 0, 0.165);
      box(1, 0.17, 0.09, 0, 0.915);
      box(0.12, 0.5, 0.09, -0.44, 0.58);
      box(0.12, 0.5, 0.09, 0.44, 0.58);
      box(0.74, 0.42, 0.035, 0, 0.58, 0, "#659fba");
      break;
    case "roof":
      box(1, 0.08, 1, 0, 1);
      break;
    case "stairs":
      for (let i = 0; i < 6; i++) box(1, (i + 1) / 6, 1 / 6, 0, (i + 1) / 12, (i + 0.5) / 6 - 0.5);
      break;
    case "fence":
      for (let i = 0; i < 5; i++) box(0.09, 1, 0.09, i / 4 - 0.5, 0.5, 0);
      box(1, 0.1, 0.08, 0, 0.25);
      box(1, 0.1, 0.08, 0, 0.78);
      break;
    case "tree":
      cylinder(0.06, 0.09, 0.4, 0, 0.2, 0, "#796c56");
      cylinder(0.05, 0.48, 0.72, 0, 0.64, 0);
      break;
    case "vegetation":
      cylinder(0.13, 0.4, 0.5, 0, 0.25, 0);
      break;
    case "vehicle":
      box(0.92, 0.48, 0.96, 0, 0.39);
      box(0.78, 0.42, 0.55, 0, 0.76, -0.1, "#d8ddd3");
      box(0.81, 0.2, 0.025, 0, 0.79, -0.39, "#42646b");
      box(0.81, 0.2, 0.025, 0, 0.79, 0.19, "#42646b");
      for (const x of [-0.44, 0.44])
        for (const z of [-0.3, 0.3]) box(0.16, 0.3, 0.18, x, 0.19, z, "#34413d");
      box(0.6, 0.08, 0.02, 0, 0.4, -0.495, "#e8e5b8");
      break;
    case "bed":
      box(0.95, 0.28, 0.95, 0, 0.2);
      box(0.9, 0.22, 0.83, 0, 0.45, 0.04, "#d1d3c1");
      box(0.92, 0.18, 0.19, 0, 0.59, -0.31, "#eff0de");
      break;
    case "chair":
      box(0.8, 0.12, 0.8, 0, 0.52);
      box(0.8, 0.48, 0.12, 0, 0.76, 0.34);
      for (const x of [-0.29, 0.29])
        for (const z of [-0.29, 0.29]) box(0.09, 0.5, 0.09, x, 0.25, z);
      break;
    case "table":
      box(1, 0.14, 1, 0, 0.87);
      for (const x of [-0.4, 0.4]) for (const z of [-0.4, 0.4]) box(0.1, 0.8, 0.1, x, 0.4, z);
      break;
    case "shelf":
    case "cabinet":
      box(0.08, 1, 0.8, -0.46, 0.5);
      box(0.08, 1, 0.8, 0.46, 0.5);
      box(1, 1, 0.08, 0, 0.5, 0.38);
      for (let i = 0; i < 4; i++) box(0.9, 0.05, 0.75, 0, 0.08 + i * 0.3);
      break;
    case "sofa":
      box(1, 0.5, 0.85, 0, 0.3);
      box(1, 0.65, 0.2, 0, 0.65, 0.34);
      box(0.13, 0.4, 0.85, -0.44, 0.6);
      box(0.13, 0.4, 0.85, 0.44, 0.6);
      break;
    case "fridge":
      box(0.88, 1, 0.9, 0, 0.5, 0, "#dbe1d4");
      box(0.06, 0.3, 0.04, 0.3, 0.5, -0.47, "#4e5e57");
      break;
    case "sink":
    case "toilet":
      box(0.85, 0.65, 0.7, 0, 0.33, 0, "#d9e1d5");
      cylinder(0.36, 0.32, 0.18, 0, 0.72, -0.07, "#eef0df");
      break;
    case "container":
      box(0.88, 0.85, 0.88, 0, 0.43);
      box(0.98, 0.1, 0.98, 0, 0.9);
      break;
    case "animal":
      box(0.5, 0.4, 0.8, 0, 0.4);
      box(0.35, 0.4, 0.32, 0, 0.65, -0.4);
      for (const x of [-0.2, 0.2]) for (const z of [-0.28, 0.28]) box(0.1, 0.3, 0.1, x, 0.15, z);
      break;
    case "zombie":
      cylinder(0.2, 0.17, 0.55, 0, 0.5, 0);
      add(new T.IcosahedronGeometry(0.16, 0), 0, 0.9, 0);
      box(0.13, 0.3, 0.14, -0.11, 0.15);
      box(0.13, 0.3, 0.14, 0.11, 0.15);
      break;
    case "item":
      box(0.5, 0.25, 0.5, 0, 0.15);
      break;
    default:
      box(0.85, 0.85, 0.85, 0, 0.425);
  }
  const result = mergeGeometries(parts, false);
  parts.forEach((p) => p.dispose());
  return result;
}
