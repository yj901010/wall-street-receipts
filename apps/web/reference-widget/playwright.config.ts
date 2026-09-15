import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
import publicConfig from "../playwright.config";

// An owned, fresh production server; never reuse a user's running service.
const baseURL = "http://127.0.0.1:3118";
export default defineConfig({
  ...publicConfig,
  testDir: "./tests",
  retries: 0,
  workers: 1,
  reporter: "list",
  outputDir: "../.cache/reference-widget-results",
  use: { ...publicConfig.use, baseURL, serviceWorkers: "block" },
  webServer: {
    cwd: fileURLToPath(new URL("..", import.meta.url)),
    command: "node node_modules/next/dist/bin/next start --hostname 127.0.0.1 --port 3118",
    url: `${baseURL}/market/reference/AAPL`,
    reuseExistingServer: false,
    timeout: 60_000,
    env: { VUNELIX_REFERENCE_WIDGET: "enabled", NEXT_TELEMETRY_DISABLED: "1" },
  },
});
