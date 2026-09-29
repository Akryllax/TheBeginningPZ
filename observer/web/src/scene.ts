import * as T from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { palette, template, templateKey } from "./models";
import type { Element, Player, Marker } from "./types";

export class WorldScene {
  readonly renderer: T.WebGLRenderer;
  readonly scene = new T.Scene();
  readonly perspective = new T.PerspectiveCamera(45, 1, 0.1, 1600);
  readonly iso = new T.OrthographicCamera(-40, 40, 40, -40, 0.1, 1600);
  camera: T.Camera;
  controls: OrbitControls;
  level = 0;
  cutaway = false;
  origin = { x: 10818, y: 9844 };
  private meshes = new T.Group();
  private coverageMesh: T.InstancedMesh | null = null;
  private playerGroup = new T.Group();
  private markerGroup = new T.Group();
  private markerSignature = "";
  private geometry = new Map<string, T.BufferGeometry>();
  private material = new T.MeshStandardMaterial({
    vertexColors: true,
    roughness: 0.92,
    metalness: 0,
  });
  private records = new Map<string, Element>();
  private meshRecords = new Map<T.Object3D, string[]>();
  private selection = new T.Box3Helper(new T.Box3(), new T.Color("#f0ce79"));
  private selectedId: string | null = null;
  private signature = "";
  private cells: number[][] = [];
  private coverageSignature = "";
  private hidden = new Set<string>();
  private onSelect: (id: string) => void;
  private animation = 0;
  private resizeObserver: ResizeObserver;

  constructor(
    private host: HTMLElement,
    onSelect: (id: string) => void,
    onMove: () => void,
  ) {
    this.onSelect = onSelect;
    this.renderer = new T.WebGLRenderer({
      antialias: true,
      alpha: false,
      powerPreference: "high-performance",
    });
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, 1.75));
    this.renderer.setClearColor("#25342f");
    this.renderer.outputColorSpace = T.SRGBColorSpace;
    this.renderer.domElement.setAttribute(
      "aria-label",
      "Interactive observed world. Drag to pan, scroll to zoom.",
    );
    this.renderer.domElement.tabIndex = 0;
    host.append(this.renderer.domElement);
    this.scene.background = new T.Color("#25342f");
    this.scene.fog = new T.Fog("#25342f", 260, 750);
    this.scene.add(new T.HemisphereLight("#e7f1dc", "#667263", 2.2));
    const sun = new T.DirectionalLight("#fff0d1", 2.6);
    sun.position.set(-50, 100, 30);
    this.scene.add(sun);
    this.perspective.position.set(38, 44, 48);
    this.camera = this.perspective;
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.maxPolarAngle = Math.PI * 0.475;
    this.controls.minDistance = 3;
    this.controls.maxDistance = 450;
    this.controls.mouseButtons = {
      LEFT: T.MOUSE.PAN,
      MIDDLE: T.MOUSE.DOLLY,
      RIGHT: T.MOUSE.ROTATE,
    };
    this.controls.addEventListener("end", onMove);
    this.scene.add(this.meshes, this.playerGroup, this.markerGroup, this.selection);
    this.selection.visible = false;
    let down = { x: 0, y: 0 };
    this.renderer.domElement.addEventListener("pointerdown", (e) => {
      down = { x: e.clientX, y: e.clientY };
    });
    this.renderer.domElement.addEventListener("pointerup", (e) => {
      if (e.button !== 0 || Math.hypot(e.clientX - down.x, e.clientY - down.y) > 4) return;
      const rect = this.renderer.domElement.getBoundingClientRect();
      const ray = new T.Raycaster();
      ray.setFromCamera(
        new T.Vector2(
          ((e.clientX - rect.left) / rect.width) * 2 - 1,
          (-(e.clientY - rect.top) / rect.height) * 2 + 1,
        ),
        this.camera,
      );
      for (const hit of ray.intersectObjects(this.meshes.children, false)) {
        const id = this.meshRecords.get(hit.object)?.[hit.instanceId ?? 0];
        if (id) {
          this.select(id);
          onSelect(id);
          break;
        }
      }
    });
    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(host);
    const animate = () => {
      this.animation = requestAnimationFrame(animate);
      if (document.hidden) return;
      this.controls.update();
      this.renderer.render(this.scene, this.camera);
    };
    animate();
  }

  resize() {
    const w = this.host.clientWidth,
      h = this.host.clientHeight;
    if (!w || !h) return;
    this.renderer.setSize(w, h);
    this.perspective.aspect = w / h;
    this.perspective.updateProjectionMatrix();
    const half = (this.iso.top - this.iso.bottom) / 2;
    this.iso.left = (-half * w) / h;
    this.iso.right = (half * w) / h;
    this.iso.updateProjectionMatrix();
  }

  setMode(mode: "3d" | "iso") {
    const old = this.camera,
      target = this.controls.target.clone();
    const distance = old.position.distanceTo(target);
    if (mode === "iso") {
      const half =
        old === this.perspective
          ? distance * Math.tan(T.MathUtils.degToRad(this.perspective.fov / 2))
          : this.iso.top / this.iso.zoom;
      this.iso.zoom = 1;
      this.iso.top = half;
      this.iso.bottom = -half;
      this.iso.position
        .copy(target)
        .add(new T.Vector3(1, 1, 1).normalize().multiplyScalar(distance));
      this.camera = this.iso;
    } else {
      const d =
        old === this.iso
          ? this.iso.top / this.iso.zoom / Math.tan(T.MathUtils.degToRad(this.perspective.fov / 2))
          : distance;
      this.perspective.position
        .copy(target)
        .add(old.position.clone().sub(target).normalize().multiplyScalar(d));
      this.camera = this.perspective;
    }
    this.controls.object = this.camera;
    this.controls.enableRotate = mode === "3d";
    this.controls.target.copy(target);
    this.camera.lookAt(target);
    this.resize();
    this.controls.update();
  }

  focus(x: number, y: number, z = this.level) {
    const next = new T.Vector3(x - this.origin.x, z * 3, y - this.origin.y);
    const delta = next.clone().sub(this.controls.target);
    this.camera.position.add(delta);
    this.controls.target.copy(next);
    this.controls.update();
  }

  center() {
    return { x: this.controls.target.x + this.origin.x, y: this.controls.target.z + this.origin.y };
  }

  setLayer(kind: string, visible: boolean) {
    if (visible) this.hidden.delete(kind);
    else this.hidden.add(kind);
    this.signature = "";
    this.setObjects([...this.records.values()]);
  }

  setCutaway(enabled: boolean) {
    this.cutaway = enabled;
    this.signature = "";
    this.setObjects([...this.records.values()]);
  }

  setObjects(objects: Element[]) {
    this.records = new Map(objects.map((o) => [o.id, o]));
    const signature = JSON.stringify(objects.map(({ observed_at, observer, ...o }) => o));
    if (signature === this.signature) return;
    this.signature = signature;
    for (const mesh of this.meshes.children) (mesh as T.InstancedMesh).dispose();
    this.meshes.clear();
    this.meshRecords.clear();
    const groups = new Map<string, Element[]>();
    for (const o of objects) {
      if (this.hidden.has(o.kind) || (this.cutaway && o.kind === "roof")) continue;
      const key = templateKey(o);
      const group = groups.get(key) ?? [];
      group.push(o);
      groups.set(key, group);
    }
    const matrix = new T.Matrix4(),
      rotation = new T.Quaternion(),
      scale = new T.Vector3(),
      position = new T.Vector3();
    for (const [key, group] of groups) {
      if (!this.geometry.has(key)) this.geometry.set(key, template(key));
      const mesh = new T.InstancedMesh(this.geometry.get(key)!, this.material, group.length);
      group.forEach((o, i) => {
        position.set(o.x - this.origin.x, o.z * 3, o.y - this.origin.y);
        let angle = T.MathUtils.degToRad(o.rotation);
        if (o.kind === "door" && o.state.open) angle += Math.PI / 2;
        rotation.setFromAxisAngle(new T.Vector3(0, 1, 0), -angle);
        let h = o.height;
        if (this.cutaway && ["wall", "door", "window"].includes(o.kind)) h = Math.min(h, 0.85);
        scale.set(o.width, h, o.depth);
        matrix.compose(position, rotation, scale);
        mesh.setMatrixAt(i, matrix);
        mesh.setColorAt(i, new T.Color(o.color ?? palette[o.kind] ?? palette.unknown));
      });
      mesh.instanceMatrix.needsUpdate = true;
      if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
      mesh.computeBoundingSphere();
      this.meshes.add(mesh);
      this.meshRecords.set(
        mesh,
        group.map((o) => o.id),
      );
    }
    if (this.selectedId) this.select(this.selectedId);
  }

  setCoverage(cells: number[][]) {
    const signature = JSON.stringify(cells);
    if (signature === this.coverageSignature) return;
    this.coverageSignature = signature;
    this.cells = cells;
    if (this.coverageMesh) {
      this.scene.remove(this.coverageMesh);
      this.coverageMesh.geometry.dispose();
      (this.coverageMesh.material as T.Material).dispose();
      this.coverageMesh.dispose();
    }
    const geometry = new T.PlaneGeometry(31.8, 31.8);
    geometry.rotateX(-Math.PI / 2);
    const mesh = new T.InstancedMesh(
      geometry,
      new T.MeshBasicMaterial({ transparent: true, opacity: 0.55, depthWrite: false }),
      cells.length,
    );
    const matrix = new T.Matrix4();
    cells.forEach(([x, y, flags], i) => {
      matrix.makeTranslation(x + 16 - this.origin.x, -0.12, y + 16 - this.origin.y);
      mesh.setMatrixAt(i, matrix);
      mesh.setColorAt(i, new T.Color(flags & 1 ? "#54715f" : "#424e45"));
    });
    mesh.computeBoundingSphere();
    this.coverageMesh = mesh;
    this.scene.add(mesh);
  }

  setPlayers(players: Player[]) {
    for (const obj of this.playerGroup.children) {
      const mesh = obj as T.Mesh;
      mesh.geometry.dispose();
      (mesh.material as T.Material).dispose();
    }
    this.playerGroup.clear();
    for (const p of players) {
      if (p.z !== this.level) continue;
      const g = new T.ConeGeometry(0.45, 1.3, 4);
      g.rotateX(Math.PI / 2);
      const mesh = new T.Mesh(
        g,
        new T.MeshBasicMaterial({ color: p.online ? "#a9ecc7" : "#869e8d" }),
      );
      mesh.rotation.y = -T.MathUtils.degToRad(p.heading);
      mesh.position.set(p.x - this.origin.x, p.z * 3 + 2.6, p.y - this.origin.y);
      this.playerGroup.add(mesh);
    }
  }

  setMarkers(markers: Marker[]) {
    const signature = JSON.stringify([this.level, markers]);
    if (signature === this.markerSignature) return;
    this.markerSignature = signature;
    this.markerGroup.traverse((obj) => {
      if (obj instanceof T.Mesh) {
        obj.geometry.dispose();
        (obj.material as T.Material).dispose();
      }
    });
    this.markerGroup.clear();
    for (const m of markers) {
      if (m.z !== this.level) continue;
      const flag = new T.Group();
      const pole = new T.Mesh(
        new T.CylinderGeometry(0.045, 0.045, 3, 6),
        new T.MeshBasicMaterial({ color: "#e9ddbb" }),
      );
      pole.position.y = 1.5;
      const cloth = new T.Mesh(
        new T.PlaneGeometry(1.1, 0.7),
        new T.MeshBasicMaterial({ color: m.color || "#f0ce79", side: T.DoubleSide }),
      );
      cloth.position.set(0.55, 2.65, 0);
      flag.add(pole, cloth);
      flag.position.set(m.x - this.origin.x, m.z * 3, m.y - this.origin.y);
      this.markerGroup.add(flag);
    }
  }

  select(id: string | null) {
    this.selectedId = id;
    const o = id ? this.records.get(id) : null;
    this.selection.visible = !!o;
    if (o) {
      const half = Math.max(o.width, o.depth) / 2 + 0.08;
      this.selection.box.set(
        new T.Vector3(o.x - this.origin.x - half, o.z * 3 - 0.05, o.y - this.origin.y - half),
        new T.Vector3(
          o.x - this.origin.x + half,
          o.z * 3 + o.height + 0.12,
          o.y - this.origin.y + half,
        ),
      );
      this.selection.updateMatrixWorld(true);
    }
  }

  stats() {
    return {
      drawCalls: this.renderer.info.render.calls,
      triangles: this.renderer.info.render.triangles,
      geometries: this.renderer.info.memory.geometries,
      objects: this.records.size,
    };
  }
  dispose() {
    cancelAnimationFrame(this.animation);
    this.resizeObserver.disconnect();
    this.controls.dispose();
    this.geometry.forEach((g) => g.dispose());
    this.material.dispose();
    this.renderer.dispose();
  }
}
