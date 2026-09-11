import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
import publicConfig from "../playwright.config";

// Standard public CI regression in development mode on the same secret-free source mirror.
const port = process.env.WSR_SCORING_UI_PORT ?? "";
if (!/^[1-9]\d{3,4}$/.test(port) || Number(port) < 1024 || Number(port) > 65535) {
  throw new Error("Explicit owned public rehearsal port required");
}
const baseURL = `http://127.0.0.1:${port}`;
export default defineConfig({
  ...publicConfig,
  testDir: "../e2e", workers: 1, retries: 0, maxFailures: 1, reporter: "list",
  outputDir: fileURLToPath(new URL("../.cache/scoring-full-stack/public", import.meta.url)),
  use: { ...publicConfig.use, baseURL, trace: "off", video: "off" },
  webServer: {
    command: `node node_modules/next/dist/bin/next dev --hostname 127.0.0.1 --port ${port}`,
    cwd: fileURLToPath(new URL("..", import.meta.url)), url: baseURL,
    reuseExistingServer: false, timeout: 30000,
    env: { SCORING_RECEIPTS_PROVIDER: "disabled", CALL_AUDIT_PROVIDER: "fixture", NEXT_TELEMETRY_DISABLED: "1" },
  },
});
