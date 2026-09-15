import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
import publicConfig from "../playwright.config";

const baseURL = "http://127.0.0.1:3119";
export default defineConfig({
  ...publicConfig,
  testDir: "../e2e",
  retries: 0,
  workers: 1,
  reporter: "list",
  outputDir: "../.cache/reference-public-results",
  use: { ...publicConfig.use, baseURL },
  webServer: {
    cwd: fileURLToPath(new URL("..", import.meta.url)),
    // Invoke Node directly so a package-manager auto-install cannot change the
    // dependency links of a disposable source mirror while workers are running.
    command: "node node_modules/next/dist/bin/next dev --hostname 127.0.0.1 --port 3119",
    url: baseURL,
    reuseExistingServer: false,
    timeout: 60_000,
    env: { VUNELIX_REFERENCE_WIDGET: "disabled", NEXT_TELEMETRY_DISABLED: "1" },
  },
});
