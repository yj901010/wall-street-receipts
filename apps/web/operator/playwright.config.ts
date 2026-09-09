import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
export default defineConfig({
  testDir: "./tests", workers: 1, retries: 0, timeout: 30_000,
  outputDir: fileURLToPath(new URL("../.cache/operator-browser", import.meta.url)),
  reporter: "list",
  use: { baseURL: "http://127.0.0.1:3470", trace: "off", video: "off", screenshot: "off",
    launchOptions: { args: ["--no-proxy-server"] } },
  webServer: { command: "node operator/demo-server.mjs", cwd: fileURLToPath(new URL("..", import.meta.url)),
    url: "http://127.0.0.1:3470/operator/cpi", reuseExistingServer: false, timeout: 60_000 },
  projects: [1440, 1280, 390].map(width => ({ name: `operator-${width}`, use: { viewport: { width, height: 1000 } } })),
});
