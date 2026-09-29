export interface Item {
  type: string;
  name: string;
  count: number;
  condition?: number;
}
export interface Inspection {
  category: "container" | "mechanics";
  slot: string;
  items: Item[];
  parts: { name: string; condition: number }[];
  observed_at: number;
  observer: string;
}
export interface Element {
  id: string;
  kind: string;
  label: string;
  sprite: string;
  x: number;
  y: number;
  z: number;
  width: number;
  depth: number;
  height: number;
  rotation: number;
  color?: string;
  state: { open?: boolean; broken?: boolean; material?: string; variant?: string };
  observed_at: number;
  observer: string;
  inspections?: Inspection[];
}
export interface Player {
  name: string;
  x: number;
  y: number;
  z: number;
  heading: number;
  online: boolean;
  observed_at: number;
}
export interface Marker {
  id: string;
  label: string;
  author: string;
  x: number;
  y: number;
  z: number;
  color: string;
}
export interface World {
  world: string;
  revision: number;
  scene_revision: number;
  coverage_revision: number;
  online: boolean;
  heartbeat_at: number | null;
  players: Player[];
  observers: string[];
  markers: Marker[];
  observed_tiles: number;
  collector_error: { message: string; at: number } | null;
  gap: { expected: number; received: number; at: number } | null;
}
