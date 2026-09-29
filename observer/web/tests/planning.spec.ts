import { test, expect, Page, APIRequestContext } from "@playwright/test";
const url = process.env.SAVED_MAP_URL || "http://127.0.0.1:8766";
const center = async (page: Page) => {
  const b = (await page.locator("#saved-map").boundingBox())!;
  return { x: b.x + b.width * 0.52, y: b.y + b.height * 0.48 };
};
async function ready(page: Page) {
  await page.goto(url);
  await expect(page.locator("#ping-status")).toContainText("Ctrl + click");
  await expect(page.locator("#survivors")).toContainText("akryllax");
  await page.waitForTimeout(300);
}
async function draft(page: Page) {
  return page.evaluate(() => JSON.parse(localStorage.getItem("observer-trip-draft")!));
}
async function saved(page: Page) {
  await expect(page.locator("#trip-sync")).toBeVisible();
  await expect(page.locator("#trip-sync")).toHaveText("Saved");
  return draft(page);
}
async function cleanup(request: APIRequestContext, id?: string) {
  if (!id) return;
  const list = await (await request.get(`${url}/api/v1/trips`)).json();
  const t = list.trips.find((t: any) => t.id === id);
  if (t) await request.delete(`${url}/api/v1/trips/${id}?version=${t.version}`);
}
async function shiftClick(page: Page, x: number, y: number) {
  await page.keyboard.down("Shift");
  await page.mouse.click(x, y);
  await page.keyboard.up("Shift");
}
async function drag(page: Page, x: number, y: number, dx: number, dy: number) {
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + dx, y + dy, { steps: 8 });
  await page.mouse.up();
}

test("Ctrl click pings once, jitter and cancellations do not paint, Ctrl drag does not ping", async ({
  browser,
}) => {
  const context = await browser.newContext(),
    a = await context.newPage(),
    b = await context.newPage();
  let posts: string[] = [];
  a.on("request", (r) => {
    if (r.method() === "POST") posts.push(new URL(r.url()).pathname);
  });
  try {
    await Promise.all([ready(a), ready(b)]);
    const p = await center(a);
    await a.keyboard.down("Control");
    await drag(a, p.x, p.y, 2, 1);
    await a.keyboard.up("Control");
    await expect(b.locator(".shared-ping")).toHaveCount(1);
    expect(posts.filter((p) => p === "/api/v1/pings")).toHaveLength(1);
    expect(posts).not.toContain("/api/v1/drawings");
    await a.keyboard.down("Control");
    await a.mouse.move(p.x, p.y);
    await a.mouse.down();
    await a.keyboard.press("Escape");
    await a.mouse.up();
    await a.keyboard.up("Control");
    expect(posts.filter((p) => p === "/api/v1/pings")).toHaveLength(1);
    await a.keyboard.down("Control");
    await drag(a, p.x, p.y, 100, 50);
    await a.keyboard.up("Control");
    await expect(b.locator(".shared-drawing").first()).toBeAttached();
    expect(posts.filter((p) => p === "/api/v1/pings")).toHaveLength(1);
    await a.locator("#clear-drawings").click();
    await expect(b.locator(".shared-drawing")).toHaveCount(0);
    await expect(b.locator(".shared-ping")).toHaveCount(0, { timeout: 15000 });
  } finally {
    await context.close();
  }
});

test("planning mode supports arbitrary points, moving, edge insertion, removal and undo; view mode locks editing", async ({
  browser,
}) => {
  const context = await browser.newContext(),
    a = await context.newPage(),
    b = await context.newPage();
  let id: string | undefined;
  try {
    await Promise.all([ready(a), ready(b)]);
    const p = await center(a);
    await shiftClick(a, p.x, p.y);
    await expect(a.locator(".trip-pin")).toHaveCount(0);
    const normalColor = await a
      .locator(".saved-app>header")
      .evaluate((e) => getComputedStyle(e).backgroundColor);
    await a.locator("#trip-mode").click();
    expect(
      await a.locator(".saved-app>header").evaluate((e) => getComputedStyle(e).backgroundColor),
    ).not.toBe(normalColor);
    await shiftClick(a, p.x - 100, p.y);
    await expect(a.locator(".trip-pin")).toHaveCount(1);
    id = (await saved(a)).id;
    await shiftClick(a, p.x + 100, p.y);
    await expect(a.locator(".trip-pin")).toHaveCount(2);
    const original = await saved(a);
    await b.locator("#trip-details summary").click();
    await b.locator("#trip-select").selectOption(id!);
    await expect(b.locator(".trip-pin")).toHaveCount(2);
    await a.keyboard.down("Shift");
    await drag(a, p.x, p.y, 0, -85);
    await a.keyboard.up("Shift");
    await expect(a.locator(".trip-pin")).toHaveCount(3);
    const inserted = await saved(a);
    expect(inserted.stops[0].id).toBe(original.stops[0].id);
    expect(inserted.stops[2].id).toBe(original.stops[1].id);
    await expect(a.locator(".leaflet-zoom-box")).toHaveCount(0);
    await expect(b.locator(".trip-pin")).toHaveCount(3);
    await drag(a, p.x, p.y - 85, 35, -20);
    const moved = await saved(a);
    expect(moved.stops[1].id).toBe(inserted.stops[1].id);
    expect(moved.stops[1].x).not.toBe(inserted.stops[1].x);
    // Esc restores the starting geometry and does not save the preview.
    await a.mouse.move(p.x + 35, p.y - 105);
    await a.mouse.down();
    await a.mouse.move(p.x + 70, p.y - 140, { steps: 5 });
    await a.keyboard.press("Escape");
    await a.mouse.up();
    expect((await draft(a)).stops).toEqual(moved.stops);
    await a.mouse.click(p.x + 35, p.y - 105, { button: "right" });
    await expect(a.locator(".trip-pin")).toHaveCount(2);
    await saved(a);
    await a.locator("#trip-edit-undo").click();
    await expect(a.locator(".trip-pin")).toHaveCount(3);
    await saved(a);
    await a.locator("#trip-clear").click();
    await expect(a.locator(".trip-pin")).toHaveCount(0);
    await saved(a);
    await expect(b.locator(".trip-pin")).toHaveCount(0);
    await a.locator("#trip-edit-undo").click();
    await expect(a.locator(".trip-pin")).toHaveCount(3);
    await saved(a);
    await a.locator("#trip-mode").click();
    await expect(a.locator("#trip-mode")).toHaveText("Plan trip");
    const locked = (await draft(a)).stops;
    await shiftClick(a, p.x, p.y + 80);
    await drag(a, p.x - 100, p.y, -20, 20);
    expect((await draft(a)).stops).toEqual(locked);
    await expect(a.locator(".place-add")).toHaveCount(0);
    await a.locator("#show-trip").uncheck();
    await a.locator("#trip-mode").click();
    await a.locator("#trip-name").fill("Hidden route editing");
    await saved(a);
    await expect(a.locator(".trip-pin")).toHaveCount(0);
    await a.locator("#show-trip").check();
    await a.locator("#trip-mode").click();
    await a.reload();
    await expect(a.locator(".trip-pin")).toHaveCount(3);
    await expect(a.locator("#trip-mode")).toHaveText("Plan trip");
    await a.screenshot({ path: "../artifacts/planning-controls-desktop.png" });
  } finally {
    await cleanup(context.request, id);
    await context.close();
  }
});

test("autosave preserves edits during slow responses, retries creation and rejects concurrent route changes", async ({
  browser,
}) => {
  const context = await browser.newContext(),
    a = await context.newPage(),
    b = await context.newPage();
  let id: string | undefined;
  try {
    await Promise.all([ready(a), ready(b)]);
    await a.locator("#trip-mode").click();
    const p = await center(a);
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    let started: () => void = () => {};
    const waiting = new Promise<void>((r) => (started = r));
    await a.route("**/api/v1/trips", async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      const response = await route.fetch();
      started();
      await gate;
      await route.fulfill({ response });
    });
    await shiftClick(a, p.x - 100, p.y);
    await waiting;
    await shiftClick(a, p.x + 100, p.y);
    release();
    id = (await saved(a)).id;
    expect((await draft(a)).stops).toHaveLength(2);
    await a.unroute("**/api/v1/trips");
    await b.locator("#trip-details summary").click();
    await b.locator("#trip-select").selectOption(id!);
    await b.locator("#trip-mode").click();
    // Hold A's edit before it reaches the server; B commits a different edit first.
    let resume: () => void = () => {};
    const held = new Promise<void>((r) => (resume = r));
    let requested: () => void = () => {};
    const arrived = new Promise<void>((r) => (requested = r));
    await a.route(`**/api/v1/trips/${id}`, async (route) => {
      if (route.request().method() !== "PUT") return route.continue();
      requested();
      await held;
      await route.continue();
    });
    await a.locator("#trip-name").fill("Local conflicting route");
    await expect(a.locator("#trip-select")).toBeDisabled();
    await arrived;
    await b.locator("#trip-name").fill("Other shared route");
    await saved(b);
    resume();
    await expect(a.locator("#trip-sync")).toHaveText("Sync conflict");
    await expect(a.locator("#trip-name")).toHaveValue("Local conflicting route");
    await a.unroute(`**/api/v1/trips/${id}`);
    await a.locator("#trip-reload").click();
    await expect(a.locator("#trip-name")).toHaveValue("Other shared route");
  } finally {
    await cleanup(context.request, id);
    await context.close();
  }
});

test.describe("touch controls", () => {
  test.use({ hasTouch: true, isMobile: true });
  test("mobile placement, visible removal and planning mode remain usable", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    let id: string | undefined;
    try {
      await ready(page);
      await page.locator("#trip-mode").click();
      await page.locator("#toggle-controls").click();
      await page.locator("#trip-map-stop").click();
      const p = await center(page);
      await page.touchscreen.tap(p.x, p.y);
      id = (await saved(page)).id;
      await page.locator("#toggle-controls").click();
      await page.locator("#trip-map-stop").click();
      await page.touchscreen.tap(p.x + 60, p.y + 50);
      await saved(page);
      const beforeTouch = (await draft(page)).stops[1];
      const touch = await page.context().newCDPSession(page);
      await touch.send("Input.dispatchTouchEvent", {
        type: "touchStart",
        touchPoints: [{ x: p.x + 60, y: p.y + 50 }],
      });
      await touch.send("Input.dispatchTouchEvent", {
        type: "touchMove",
        touchPoints: [{ x: p.x + 85, y: p.y + 75 }],
      });
      await touch.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
      await saved(page);
      expect((await draft(page)).stops[1].x).not.toBe(beforeTouch.x);
      await touch.detach();
      await page.locator("#toggle-controls").click();
      await page.locator("#trip-stops .trip-stop-actions button").last().click();
      await saved(page);
      await page.locator("#trip-edit-undo").click();
      await saved(page);
      await page.locator("#trip-fit").click();
      await page.screenshot({ path: "../artifacts/planning-controls-mobile.png" });
      await page.locator("#trip-mode").click();
      await page.locator("#toggle-controls").click();
      await page.locator("#place-ping").click();
      await page.touchscreen.tap(p.x, p.y);
      await expect(page.locator(".shared-ping")).toHaveCount(1);
    } finally {
      await cleanup(page.request, id);
    }
  });
});

test("a save response during dragging preserves the preview; a lost creation response recovers once", async ({
  page,
}) => {
  let id: string | undefined;
  try {
    await ready(page);
    await page.locator("#trip-mode").click();
    const p = await center(page);
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    let started: () => void = () => {};
    const waiting = new Promise<void>((r) => (started = r));
    await page.route("**/api/v1/trips", async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      await route.fetch();
      started();
      await gate;
      await route.abort("failed");
    });
    await shiftClick(page, p.x, p.y);
    id = (await draft(page)).id;
    await waiting;
    await page.mouse.move(p.x, p.y);
    await page.mouse.down();
    await page.mouse.move(p.x + 80, p.y - 60, { steps: 5 });
    release();
    await expect.poll(async () => (await draft(page)).version).toBe(1);
    await page.mouse.move(p.x + 100, p.y - 80, { steps: 5 });
    await page.mouse.up();
    const result = await saved(page);
    expect(result.stops).toHaveLength(1);
    const marker = (await page.locator(".trip-pin").boundingBox())!;
    expect(Math.abs(marker.x + 12 - (p.x + 100))).toBeLessThan(3);
    const all = await (await page.request.get(`${url}/api/v1/trips`)).json();
    expect(all.trips.filter((t: any) => t.id === id)).toHaveLength(1);
  } finally {
    await cleanup(page.request, id);
  }
});

test("autosave rebases checkpoint-only updates and preserves reached pins across reload", async ({
  page,
}) => {
  let id: string | undefined;
  try {
    await ready(page);
    await page.locator("#trip-mode").click();
    const p = await center(page);
    for (const offset of [-100, 0, 100]) {
      await shiftClick(page, p.x + offset, p.y + 60);
      await saved(page);
    }
    id = (await draft(page)).id;
    await page.locator("#trip-player").selectOption("akryllax");
    await saved(page);
    await page.locator("#trip-start").click();
    await expect(page.locator(".trip-reached")).toHaveCount(1);
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    let started: () => void = () => {};
    const waiting = new Promise<void>((r) => (started = r));
    await page.route(`**/api/v1/trips/${id}`, async (route) => {
      if (route.request().method() !== "PUT") return route.continue();
      started();
      await gate;
      await route.continue();
    });
    await page.locator("#trip-name").fill("Checkpoint-safe autosave");
    await waiting;
    const all = await (await page.request.get(`${url}/api/v1/trips`)).json();
    const trip = all.trips.find((t: any) => t.id === id);
    const progress = await page.request.post(`${url}/api/v1/trips/${id}/progress`, {
      data: { version: trip.version, action: "next" },
    });
    expect(progress.ok()).toBe(true);
    release();
    await saved(page);
    await expect(page.locator(".trip-reached")).toHaveCount(2);
    await expect(page.locator(".trip-completed-leg")).toHaveCount(1);
    await page.reload();
    await expect(page.locator(".trip-reached")).toHaveCount(2);
    await expect(page.locator(".trip-completed-leg")).toHaveCount(1);
  } finally {
    await cleanup(page.request, id);
  }
});

test("unsynced edits survive reload and resume autosaving", async ({ page }) => {
  let id: string | undefined;
  try {
    await ready(page);
    await page.locator("#trip-mode").click();
    const p = await center(page);
    await shiftClick(page, p.x - 70, p.y);
    id = (await saved(page)).id;
    const endpoint = `**/api/v1/trips/${id}`;
    await page.route(endpoint, (route) =>
      route.request().method() === "PUT" ? route.abort("failed") : route.continue(),
    );
    await page.locator("#trip-name").fill("Recovered after reload");
    await expect.poll(async () => (await draft(page)).dirty).toBe(true);
    await expect(page.locator("#trip-status")).toContainText("Changes waiting to sync");
    await page.unroute(endpoint);
    await page.reload();
    const recovered = await saved(page);
    expect(recovered.id).toBe(id);
    expect(recovered.name).toBe("Recovered after reload");
    await expect(page.locator("#trip-mode")).toHaveText("Plan trip");
  } finally {
    await cleanup(page.request, id);
  }
});
