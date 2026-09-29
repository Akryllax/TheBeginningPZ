import { test, expect } from "@playwright/test";

test("private diagnostics render bounded field, floors, safe text and stale state", async ({
  page,
}) => {
  let stale = false;
  const snapshot = {
    world: "AKR_DayOne",
    tick: 5,
    world_age_hours: 2,
    phase: "outbreak",
    mode: "observe",
    pressure: 5,
    budget: 3,
    online_players: 2,
    npc_count: null,
    timings_ms: { last: 0.1, p95: 0.2, p99: 0.3, max: 0.4 },
    cells: [
      { x: 10624, y: 9888, z: 0, dwell: 4, wealth: 15, confidence: 0.25, age_hours: 2 },
      { x: 10656, y: 9888, z: 1, dwell: 2, wealth: 10, confidence: 0.6, age_hours: 1 },
    ],
    decisions: [
      {
        id: "decision-1",
        event: "patrol",
        outcome: "deferred",
        reason: "<img src=x onerror=alert(1)> Grace period",
        at_hour: 2,
      },
    ],
    cell_count: 100,
    scan_queue: 3,
    dropped_cells: 0,
    health: "Observing",
    truncated: true,
    capture_ms: 0.5,
    captured_at: 2000000,
  };
  await page.route("**/debug/storyteller/snapshot", (route) =>
    route.fulfill({
      json: {
        status: stale ? "stale" : "live",
        snapshot,
        stale_after_ms: 20000,
        interval_ms: 5000,
      },
    }),
  );
  await page.route("**/api/v1/map/tiles/**", (route) =>
    route.fulfill({ status: 204, contentType: "image/png" }),
  );
  await page.goto("/debug/storyteller");
  await expect(page.getByRole("heading", { name: "Living world · diagnostics" })).toBeVisible();
  await expect(page.getByRole("status")).toHaveText("Receiving diagnostics");
  await expect(page.locator("#story-metrics")).toContainText("Observation only");
  await expect(page.locator("#story-metrics")).toContainText("Unknown");
  await expect(page.locator("#field-note")).toContainText(
    "1 cells shown on this floor · 2 in preview / 100 tracked",
  );
  await expect(page.locator("#field-note")).toContainText("partial preview");
  await page.getByLabel("Floor", { exact: true }).selectOption("1");
  await page.getByLabel("Color by").selectOption("wealth");
  await page.getByRole("button", { name: "Fit observed cells" }).click();
  await expect(page.locator("#story-decisions")).toContainText(
    "<img src=x onerror=alert(1)> Grace period",
  );
  await expect(page.locator("#story-decisions img")).toHaveCount(0);
  stale = true;
  await expect(page.getByRole("status")).toHaveText("Stale · last sample retained", {
    timeout: 10000,
  });
  await expect(page.locator("#feed-detail")).toContainText("no longer publishing");
  await expect(page.locator("#story-metrics")).toContainText("Observation only");
});

test("missing diagnostics show a useful waiting state", async ({ page }) => {
  await page.route("**/debug/storyteller/snapshot", (route) =>
    route.fulfill({ json: { status: "waiting", snapshot: null, stale_after_ms: 20000 } }),
  );
  await page.route("**/api/v1/map/tiles/**", (route) =>
    route.fulfill({ status: 204, contentType: "image/png" }),
  );
  await page.goto("/debug/storyteller");
  await expect(page.getByRole("status")).toHaveText("Waiting for storyteller");
  await expect(page.locator("#feed-detail")).toContainText("No gameplay controls are exposed");
});
