import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  use: {
    baseURL: process.env.OBSERVER_TEST_BASE_URL || "http://127.0.0.1:8765",
    viewport: { width: 1440, height: 960 },
    launchOptions: { args: ["--enable-unsafe-swiftshader"] },
  },
  workers: 1,
  reporter: "list",
});
