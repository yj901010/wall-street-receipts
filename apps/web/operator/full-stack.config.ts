import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";

// Invoked only by CpiOperatorBrowserIT with its owned API, random in-memory token and UI port.
const uiPort = process.env.CPI_OPERATOR_UI_PORT ?? "";
const apiPort = process.env.CPI_OPERATOR_API_PORT ?? "";
const phase = process.env.WSR_CPI_BROWSER_PHASE ?? "";
for (const port of [uiPort, apiPort]) {
  if (!/^[1-9]\d{3,4}$/.test(port) || Number(port) < 1024 || Number(port) > 65535) throw new Error("Explicit rehearsal ports required");
}
if (uiPort === apiPort || !["empty", "seeded", "unavailable", "recovered"].includes(phase)
  || !/^[A-Za-z0-9+/]{43}=$/.test(process.env.WSR_CPI_BROWSER_TOKEN ?? "")) throw new Error("Disposable Java rehearsal context required");
const baseURL = `http://127.0.0.1:${uiPort}`;
export default defineConfig({
  testDir: "./full-stack-tests", workers: 1, retries: 0, timeout: 60_000,
  outputDir: fileURLToPath(new URL(`../.cache/operator-full-stack/${phase}`, import.meta.url)),
  reporter: "list",
  use: { baseURL, trace: "off", video: "off", screenshot: "off", launchOptions: { args: ["--no-proxy-server"] } },
  // No synthetic HTTP server. This is the unchanged real production operator launcher.
  webServer: { command: "node operator/start.mjs", cwd: fileURLToPath(new URL("..", import.meta.url)),
    url: baseURL + "/operator/cpi", reuseExistingServer: false, timeout: 30_000 },
  projects: [1440, 1280, 390].map(width => ({ name: `full-stack-${width}`, use: { viewport: { width, height: 1000 } } })),
});
