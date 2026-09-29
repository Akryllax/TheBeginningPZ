import { test, expect, type Page } from "@playwright/test";

const url = process.env.SAVED_MAP_URL || "http://127.0.0.1:8766";
type Player = {
  id: number;
  name: string;
  character: string;
  x: number;
  y: number;
  z: number;
  source: string;
  online: boolean | null;
};

async function fixture(page: Page) {
  let players: Player[] = [
    {
      id: 1,
      name: "akryllax",
      character: "Tommy",
      x: 10768,
      y: 9904,
      z: 0,
      source: "live",
      online: true,
    },
    {
      id: 2,
      name: "eric",
      character: "Eric",
      x: 10880,
      y: 9952,
      z: 0,
      source: "live",
      online: true,
    },
  ];
  let status = "live";
  const frame = () => ({
    players,
    position_feed: {
      enabled: true,
      status,
      received_at: Date.now(),
      captured_at: Date.now(),
      stale_after_ms: 60000,
      interval_ms: 1000,
      online_count: players.filter((p) => p.online).length,
    },
  });
  await page.addInitScript(() => {
    const Native = window.EventSource;
    window.EventSource = class extends EventTarget {
      url: string;
      onerror = null;
      onmessage = null;
      readyState = 1;
      constructor(input: string | URL, options?: EventSourceInit) {
        super();
        this.url = String(input);
        if (!this.url.endsWith("/api/v1/events")) return new Native(input, options) as any;
        (window as any).cameraTestStream = this;
        setTimeout(() => this.dispatchEvent(new Event("open")), 0);
      }
      close() {}
    } as unknown as typeof EventSource;
  });
  await page.route("**/api/v1/world", (route) =>
    route.fulfill({
      json: {
        mode: "saved_map",
        world: "Camera fixture",
        revision: 1,
        coverage_revision: 1,
        terrain_revision: 1,
        indexing: false,
        index_error: null,
        observers: ["akryllax", "eric"],
        ...frame(),
        markers: [],
        deaths: [],
        death_markers_since: null,
        sources: {},
      },
    }),
  );
  await page.route("**/api/v1/coverage*", (route) =>
    route.fulfill({ json: { revision: 1, unit_size: 32, cells: [], city_labels: [] } }),
  );
  await page.route("**/api/v1/map/features?*", (route) =>
    route.fulfill({ json: { vehicles: [] } }),
  );
  await page.goto(url);
  await expect(page.locator('.camera-follow-toggle[aria-label="Follow akryllax"]')).toHaveCount(1);
  await settled(page);
  return async (name: string, changes: Partial<Player>, feedStatus = "live") => {
    players = players.map((p) => (p.name === name ? { ...p, ...changes } : p));
    status = feedStatus;
    await page.evaluate(
      (value) =>
        (window as any).cameraTestStream.dispatchEvent(
          new MessageEvent("positions", { data: JSON.stringify(value) }),
        ),
      frame(),
    );
    await settled(page);
  };
}

async function settled(page: Page) {
  // Leaflet schedules wheel/button zooms and drag inertia on the next frame.
  // Observe a stable camera, including animations that have not started yet.
  await expect
    .poll(async () => {
      if (await page.locator(".leaflet-pan-anim,.leaflet-zoom-anim").count()) return false;
      const before = await page.locator(".leaflet-map-pane").getAttribute("style");
      await page.waitForTimeout(100);
      return (
        before === (await page.locator(".leaflet-map-pane").getAttribute("style")) &&
        (await page.locator(".leaflet-pan-anim,.leaflet-zoom-anim").count()) === 0
      );
    })
    .toBe(true);
}

async function center(page: Page) {
  const box = (await page.locator("#saved-map").boundingBox())!;
  const x = box.x + box.width / 2,
    y = box.y + box.height / 2;
  await page.mouse.move(x + 2, y + 2);
  // Leaflet throttles canvas-marker hover events for 32 ms. Let the second
  // movement reach the coordinate readout instead of sampling the offset point.
  await page.waitForTimeout(40);
  await page.mouse.move(x, y);
  await page.waitForTimeout(40);
  const text = await page.locator("#cursor-coordinates").innerText();
  return text.match(/-?\d+/g)!.map(Number);
}

async function expectCenter(page: Page, x: number, y: number) {
  await expect
    .poll(async () => {
      const actual = await center(page);
      return Math.max(Math.abs(actual[0] - x), Math.abs(actual[1] - y));
    })
    .toBeLessThanOrEqual(1);
}

test("Follow centers live updates, keeps zoom, waits through outages and switches players", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  const send = await fixture(page);
  await page.getByRole("button", { name: "Follow akryllax", exact: true }).click();
  await expect(page.locator("#camera-follow-status")).toContainText("Following akryllax");
  await send("akryllax", { x: 10832, y: 9872 });
  await expectCenter(page, 10832, 9872);
  await page.locator(".leaflet-control-zoom-out").click();
  await settled(page);
  await expect(
    page.getByRole("button", { name: "Stop following akryllax", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await expectCenter(page, 10832, 9872);
  await send("akryllax", { x: 10848, y: 9856 });
  await expectCenter(page, 10848, 9856);
  await send("akryllax", { source: "last_seen", online: null }, "stale");
  await expect(page.locator("#camera-follow-source")).toHaveText("Waiting for live positions");
  await expectCenter(page, 10848, 9856);
  await send("akryllax", { source: "last_seen", online: false });
  await expect(page.locator("#camera-follow-source")).toHaveText("Offline · last position");
  await send("akryllax", { source: "live", online: true, x: 10864, y: 9840 });
  await expectCenter(page, 10864, 9840);
  await page.getByRole("button", { name: "Follow eric", exact: true }).click();
  await settled(page);
  await expectCenter(page, 10880, 9952);
  await expect(page.getByRole("button", { name: "Follow akryllax", exact: true })).toHaveAttribute(
    "aria-pressed",
    "false",
  );
  await send("akryllax", { x: 11000, y: 10000 });
  await expectCenter(page, 10880, 9952);
  await send("eric", { x: 10912, y: 9984 });
  await expectCenter(page, 10912, 9984);
  await page.screenshot({ path: "../artifacts/camera-follow/desktop.png" });
  expect(errors).toEqual([]);
});

test("manual dragging and navigation release the camera and later samples do not pull it back", async ({
  page,
}) => {
  const send = await fixture(page);
  await page.getByRole("button", { name: "Follow akryllax", exact: true }).click();
  await settled(page);
  const box = (await page.locator("#saved-map").boundingBox())!;
  await page.mouse.move(box.x + box.width / 2 + 120, box.y + box.height / 2 + 80);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + 220, box.y + box.height / 2 + 140, { steps: 8 });
  await page.mouse.up();
  await expect(page.locator("#camera-follow-status")).toBeHidden();
  await settled(page);
  const manual = await center(page);
  await send("akryllax", { x: 10912, y: 9984 });
  expect(await center(page)).toEqual(manual);
  await page.getByRole("button", { name: "Follow akryllax", exact: true }).click();
  await settled(page);
  await page.locator("#coordinates").fill("10600,10000");
  await page.locator("#coordinate-search button").click();
  await settled(page);
  await expect(page.locator("#camera-follow-status")).toBeHidden();
  await expectCenter(page, 10600, 10000);
  await send("akryllax", { x: 11000, y: 10100 });
  await expectCenter(page, 10600, 10000);
  await page.getByRole("button", { name: "Follow eric", exact: true }).click();
  await settled(page);
  await page.locator("#fit-players").click();
  await settled(page);
  await expect(page.locator("#camera-follow-status")).toBeHidden();
});

test("mobile Follow closes the controls and offers a visible Stop button", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const send = await fixture(page);
  await page.getByRole("button", { name: "Toggle map controls" }).click();
  await page.getByRole("button", { name: "Follow akryllax", exact: true }).click();
  await expect(page.locator("#controls")).toBeHidden();
  await expect(page.locator("#camera-follow-status")).toBeInViewport();
  await send("akryllax", { x: 10800, y: 9936 });
  await expectCenter(page, 10800, 9936);
  await page.screenshot({ path: "../artifacts/camera-follow/mobile.png" });
  await page.getByRole("button", { name: "Stop following player", exact: true }).click();
  await expect(page.locator("#camera-follow-status")).toBeHidden();
  const stopped = await center(page);
  await send("akryllax", { x: 10900, y: 10000 });
  expect(await center(page)).toEqual(stopped);
});
