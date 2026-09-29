import { test, expect } from "@playwright/test";
const url = process.env.SAVED_MAP_URL || "http://127.0.0.1:8766";
test("notes have permanent legible text and vehicle categories use distinct colors", async ({
  page,
}) => {
  const world = await (await page.request.get(`${url}/api/v1/world`)).json();
  const p = world.players.find((p: any) => p.name === "akryllax") || world.players[0];
  // A controlled note keeps this read-only check independent of duplicate or
  // changing player-authored labels in the live save.
  world.markers = [
    {
      id: "map-symbol-test",
      label: "Test map note",
      author: "Browser check",
      x: p.x,
      y: p.y + 20,
      z: 0,
      color: "#985fe0",
    },
  ];
  await page.route("**/api/v1/world", (route) => route.fulfill({ json: world }));
  await page.route("**/api/v1/map/features?*", (route) =>
    route.fulfill({
      json: {
        vehicles: [
          { id: 90001, label: "Keyed car", x: p.x + 35, y: p.y + 40, z: 0, category: "keyed" },
          {
            id: 90002,
            label: "Hotwired van",
            x: p.x + 65,
            y: p.y + 40,
            z: 0,
            category: "hotwired",
          },
          { id: 90003, label: "Burnt wreck", x: p.x + 95, y: p.y + 40, z: 0, category: "wreck" },
        ],
      },
    }),
  );
  await page.goto(url);
  await expect(page.locator("#survivors")).toContainText("akryllax");
  for (const [kind, color] of [
    ["keyed", "#29b765"],
    ["hotwired", "#f3ad32"],
    ["wreck", "#de6756"],
  ]) {
    await expect(page.locator(`.vehicle-${kind}`)).toHaveAttribute("fill", color);
  }
  const note = world.markers[0];
  if (note) {
    await page.locator("#public-markers button").first().click();
    const label = page.locator(".public-marker-label").filter({ hasText: note.label });
    await expect(label).toHaveCount(1);
    await expect(label).toBeVisible();
    await expect(label).toHaveCSS("color", "rgb(255, 255, 255)");
    await page.locator("#show-markers").uncheck();
    await expect(label).toHaveCount(0);
    await page.locator("#show-markers").check();
    await expect(label).toBeVisible();
  }
  await expect(page.locator(".saved-legend")).toContainText("Keys carried");
  await expect(page.locator(".saved-legend")).toContainText("Hotwired");
  await expect(page.locator(".saved-legend")).toContainText("Wreck");
  await page.screenshot({ path: "../artifacts/map-symbols-desktop.png" });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: "../artifacts/map-symbols-mobile.png" });
});
