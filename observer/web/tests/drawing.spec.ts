import { test, expect } from "@playwright/test";
const url = process.env.SAVED_MAP_URL || "http://127.0.0.1:8766";
test("Ctrl drag shares before release, survives reload, pans normally and clears for all", async ({
  browser,
}) => {
  const context = await browser.newContext();
  await context.addInitScript(() => {
    const Original = window.EventSource;
    (window as any).drawingIds = {};
    window.EventSource = class extends Original {
      constructor(url: string | URL, options?: EventSourceInit) {
        super(url, options);
        if (String(url).includes("/drawings/events")) {
          this.addEventListener("reset", () => {
            (window as any).drawingIds = {};
          });
          this.addEventListener("drawings", (event) => {
            const update = JSON.parse((event as MessageEvent).data);
            for (const s of update.strokes) (window as any).drawingIds[s.id] = s;
            for (const id of update.removed) delete (window as any).drawingIds[id];
          });
        }
      }
    };
  });
  const a = await context.newPage(),
    b = await context.newPage();
  let owner = "";
  try {
    await Promise.all([a.goto(url), b.goto(url)]);
    for (const page of [a, b])
      await expect(page.locator("#drawing-status")).toContainText("connected");
    await expect(a.locator("#survivors")).toContainText("akryllax");
    await a.waitForTimeout(500);
    const box = (await a.locator("#saved-map").boundingBox())!;
    const x = box.x + box.width * 0.55,
      y = box.y + box.height * 0.45;
    const upload = a.waitForRequest(
      (r) => r.url().endsWith("/api/v1/drawings") && r.method() === "POST",
    );
    await a.keyboard.down("Control");
    await a.mouse.move(x, y);
    await a.mouse.down();
    await a.mouse.move(x + 90, y + 40, { steps: 15 });
    const payload = (await upload).postDataJSON();
    owner = payload.owner;
    const id = payload.id;
    const observed = (page: typeof a) =>
      page.evaluate((id) => Boolean((window as any).drawingIds[id]), id);
    await expect.poll(() => observed(b)).toBe(true); // Mouse is still down.
    await expect(b.locator(".shared-drawing").first()).toBeAttached();
    await a.mouse.move(x + 150, y - 20, { steps: 10 });
    await a.mouse.up();
    await a.keyboard.up("Control");
    await expect(a.locator("#saved-map")).not.toHaveClass(/is-drawing/);
    await b.reload();
    await expect.poll(() => observed(b)).toBe(true);
    const path = b.locator(".shared-drawing").first();
    const before = (await path.boundingBox())!.x;
    await b.mouse.move(x, y + 100);
    await b.mouse.down();
    await b.mouse.move(x + 80, y + 140, { steps: 15 });
    await b.mouse.up();
    await expect.poll(async () => (await path.boundingBox())!.x).not.toBe(before);
    await b.locator("#clear-drawings").click();
    await expect.poll(() => observed(b)).toBe(true);
    await a.reload();
    await expect.poll(() => observed(a)).toBe(true);
    await a.locator("#clear-drawings").click();
    await expect.poll(() => observed(a)).toBe(false);
    await expect.poll(() => observed(b)).toBe(false);
  } finally {
    if (owner) await context.request.delete(`${url}/api/v1/drawings/${owner}`);
    await context.close();
  }
});
