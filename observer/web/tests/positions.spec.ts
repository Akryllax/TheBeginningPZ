import { test, expect, APIRequestContext, Page } from "@playwright/test";
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
  connection = randomUUID(),
  started = Date.now() - 1000;
let sequence = 0;
async function send(request: APIRequestContext, x: number | null = 10790, y = 9810) {
  const data = {
    protocol_version: 1,
    world: "AKR_Exploratory",
    server_session: session,
    session_started_at_ms: started,
    sequence: ++sequence,
    captured_at_ms: Date.now(),
    players:
      x === null
        ? []
        : [
            {
              username: "akryllax",
              character: "Tommy Reinhardt",
              x,
              y,
              z: 0,
              connection_id: connection,
            },
          ],
  };
  const payload = execFileSync(
    path.resolve("../.venv/bin/python"),
    [
      "-c",
      "import sys,json; from observer.proto.positions_pb2 import PositionSnapshot; sys.stdout.buffer.write(PositionSnapshot(**json.load(sys.stdin)).SerializeToString())",
    ],
    { cwd: path.resolve(".."), input: JSON.stringify(data) },
  );
  const response = await request.post(url + "/internal/v1/positions", {
    data: payload,
    headers: { Authorization: "Bearer " + token, "Content-Type": "application/x-protobuf" },
  });
  expect(response.status(), await response.text()).toBe(204);
}
async function ready(page: Page) {
  await page.goto(url);
  await expect(page.locator("#survivors")).toContainText("akryllax");
  await expect(page.locator("#known-count")).toContainText("known blocks");
}

test("live positions reach two viewers without terrain reloads, extra streams or automatic panning", async ({
  browser,
}) => {
  const context = await browser.newContext(),
    a = await context.newPage(),
    b = await context.newPage();
  const errors: string[] = [];
  for (const page of [a, b]) page.on("pageerror", (e) => errors.push(e.message));
  try {
    await send(context.request);
    await Promise.all([ready(a), ready(b)]);
    await expect(a.locator("#map-position-status")).toContainText("1 online");
    const survivor = a.locator(".survivor").filter({ hasText: "akryllax" });
    await survivor.click();
    await a.waitForTimeout(300);
    const pane = await a.locator(".leaflet-map-pane").getAttribute("style");
    let expensive = 0,
      streams = 0;
    a.on("request", (r) => {
      if (/\/api\/v1\/(world|coverage|map\/tiles|map\/features)/.test(r.url())) expensive++;
      if (r.url().endsWith("/events")) streams++;
    });
    const ref = await a.locator("#search-origin").inputValue();
    await send(context.request, 10840, 9815);
    for (const page of [a, b])
      await expect(page.locator(".survivor").filter({ hasText: "akryllax" })).toContainText(
        "10840, 9815",
      );
    expect(await a.locator("#search-origin").inputValue()).toBe(ref);
    expect(await a.locator(".leaflet-map-pane").getAttribute("style")).toBe(pane);
    expect(expensive).toBe(0);
    expect(streams).toBe(0);
    expect(errors).toEqual([]);
    await a.screenshot({ path: "../artifacts/live-positions-desktop.png" });
    await send(context.request, null);
    await expect(survivor).toContainText("Offline");
    await expect(a.locator("#map-position-status")).toContainText("0 online");
    await expect(survivor).toContainText("10840, 9815");
  } finally {
    await context.close();
  }
});

test("stale positions are labeled on desktop and mobile, then recover", async ({ page }) => {
  await send(page.request);
  await page.setViewportSize({ width: 390, height: 844 });
  await ready(page);
  await expect(page.locator("#map-position-status")).toContainText("1 online");
  await expect(page.locator("#map-position-status")).toContainText("Positions delayed", {
    timeout: 13000,
  });
  await page.locator("#toggle-controls").click();
  await expect(page.locator(".survivor").filter({ hasText: "akryllax" })).toContainText(
    "connection unknown",
  );
  await expect(page.locator("#position-note")).toContainText("checkpoints wait");
  await send(page.request, 10800, 9820);
  await expect(page.locator(".survivor").filter({ hasText: "akryllax" })).toContainText(
    "Online · live",
  );
  await page.screenshot({ path: "../artifacts/live-positions-mobile.png" });
});

test("a live arrival updates a shared trip and preserves planner edits", async ({ page }) => {
  let id: string | undefined;
  try {
    await send(page.request);
    await ready(page);
    const created = await page.request.post(url + "/api/v1/trips", {
      data: {
        name: "Live checkpoint test",
        follow_player: "akryllax",
        arrival_radius: 10,
        stops: [
          { label: "Start", x: 10790, y: 9810 },
          { label: "Meet here", x: 10890, y: 9810 },
        ],
      },
    });
    const trip = await created.json();
    id = trip.id;
    expect(
      (
        await page.request.post(url + `/api/v1/trips/${id}/progress`, {
          data: { version: trip.version, action: "start" },
        })
      ).status(),
    ).toBe(200);
    await page.locator("#trip-details summary").click();
    await page.locator("#trip-select").selectOption(id!);
    await expect(page.locator("#trip-progress")).toContainText("0 / 1");
    await send(page.request, 10890, 9810);
    await expect(page.locator("#trip-progress")).toContainText("Trip complete");
    await expect(page.locator("#trip-progress")).toContainText("1 / 1");
    await expect(page.locator("#checkpoint-source-note")).toContainText("once a second");
  } finally {
    if (id) {
      const data = await (await page.request.get(url + "/api/v1/trips")).json();
      const trip = data.trips.find((t: any) => t.id === id);
      if (trip) await page.request.delete(url + `/api/v1/trips/${id}?version=${trip.version}`);
    }
  }
});
