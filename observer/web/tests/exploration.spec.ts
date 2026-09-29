import { test, expect, APIRequestContext } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import path from "node:path";

const url = process.env.SAVED_MAP_URL || "http://127.0.0.1:8766";
const token = process.env.OBSERVER_TEST_POSITION_TOKEN;
test.skip(
  !token || !["127.0.0.1", "localhost"].includes(new URL(url).hostname),
  "Requires an isolated local receiver and explicit test token",
);
const session = randomUUID(),
  started = Date.now() - 1000;
let sequence = 0;
async function send(request: APIRequestContext, expanded: boolean) {
  const input = {
    session,
    started,
    sequence: ++sequence,
    captured: Date.now(),
    cells: expanded
      ? [
          [10752, 9792, 3],
          [10784, 9792, 1],
        ]
      : [[10752, 9792, 3]],
  };
  const code = `import sys,json,zlib
from observer.proto.positions_pb2 import ExplorationSnapshot,PlayerExploration
d=json.load(sys.stdin); raw=bytearray(501*501*16)
for x,y,flags in d['cells']:
    ux,uy=(x+64000)//32,(y+64000)//32
    raw[uy*1002+ux//4]|=flags<<((ux%4)*2)
p=PlayerExploration(username='akryllax',min_cell_x=-250,min_cell_y=-250,max_cell_x=250,max_cell_y=250,world_version=249,visited_zlib=zlib.compress(raw))
m=ExplorationSnapshot(protocol_version=1,world='AKR_Exploratory',server_session=d['session'],session_started_at_ms=d['started'],sequence=d['sequence'],captured_at_ms=d['captured'],players=[p])
sys.stdout.buffer.write(m.SerializeToString())`;
  const payload = execFileSync(path.resolve("../.venv/bin/python"), ["-c", code], {
    cwd: path.resolve(".."),
    input: JSON.stringify(input),
  });
  const response = await request.post(url + "/internal/v1/exploration", {
    data: payload,
    headers: { Authorization: "Bearer " + token, "Content-Type": "application/x-protobuf" },
  });
  expect(response.status(), await response.text()).toBe(204);
}

test("exact exploration refreshes both viewers without panning; unchanged masks do not refetch tiles", async ({
  browser,
}) => {
  const context = await browser.newContext(),
    a = await context.newPage(),
    b = await context.newPage();
  const errors: string[] = [];
  for (const p of [a, b]) p.on("pageerror", (e) => errors.push(e.message));
  try {
    await send(context.request, false);
    const ericBefore = await (
      await context.request.get(url + "/api/v1/coverage?observer=eric")
    ).json();
    for (const page of [a, b]) {
      await page.goto(url);
      await expect(page.locator("#known-count")).toContainText("known blocks");
      await page.locator("#coverage-source").selectOption("akryllax");
      await expect(page.locator("#known-count")).toHaveText("1 known blocks · 32 × 32 tiles each");
      await page.locator("#coordinates").fill("10782,9808");
      await page.locator("#coordinate-search button").click();
      await expect(page.locator("#map-exploration-status")).toHaveText("Live exploration");
    }
    await a.waitForTimeout(500);
    const pane = await a.locator(".leaflet-map-pane").getAttribute("style");
    const initial = await a.locator(".leaflet-tile-loaded").first().getAttribute("src");
    await send(context.request, true);
    for (const page of [a, b])
      await expect(page.locator("#known-count")).toHaveText("2 known blocks · 32 × 32 tiles each");
    await expect
      .poll(() => a.locator(".leaflet-tile-loaded").first().getAttribute("src"))
      .not.toBe(initial);
    expect(await a.locator(".leaflet-map-pane").getAttribute("style")).toBe(pane);
    const ericAfter = await (
      await context.request.get(url + "/api/v1/coverage?observer=eric")
    ).json();
    expect(ericAfter.cells).toEqual(ericBefore.cells);
    await a.waitForTimeout(600);
    let tiles = 0,
      streams = 0;
    a.on("request", (r) => {
      if (r.url().includes("/map/tiles/")) tiles++;
      if (r.url().endsWith("/events")) streams++;
    });
    await send(context.request, true);
    await a.waitForTimeout(650);
    expect(tiles).toBe(0);
    expect(streams).toBe(0);
    expect(errors).toEqual([]);
    await a.screenshot({ path: "../artifacts/exploration-fix/browser-live.png" });
    await b.setViewportSize({ width: 390, height: 844 });
    await expect(b.locator("#map-exploration-status")).toContainText("Exploration delayed", {
      timeout: 18000,
    });
    await expect(b.locator("#known-count")).toContainText("2 known blocks");
    await send(context.request, true);
    await expect(b.locator("#map-exploration-status")).toHaveText("Live exploration");
    await b.screenshot({ path: "../artifacts/exploration-fix/browser-mobile.png" });
  } finally {
    await context.close();
  }
});
