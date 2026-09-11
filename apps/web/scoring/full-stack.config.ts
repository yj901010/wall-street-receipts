import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
const port = process.env.WSR_SCORING_UI_PORT ?? "";
const api = process.env.WSR_SCORING_API_PORT ?? "";
const phase = process.env.WSR_SCORING_PHASE ?? "";
for (const value of [port, api]) if (!/^[1-9]\d{3,4}$/.test(value) || Number(value) < 1024 || Number(value) > 65535) throw new Error("Explicit owned ports required");
if (port === api || !["empty", "ready", "unavailable", "recovered"].includes(phase)) throw new Error("Explicit DEMO rehearsal context required");
const baseURL = `http://127.0.0.1:${port}`;
export default defineConfig({
  testDir: "./tests", workers: 1, retries: 0, maxFailures: 1, timeout: 60000, reporter: "list",
  outputDir: fileURLToPath(new URL(`../.cache/scoring-full-stack/${phase}`, import.meta.url)),
  use: {baseURL,trace:"off",video:"off",screenshot:"off",launchOptions:{args:["--no-proxy-server"]}},
  webServer: {command:`node node_modules/next/dist/bin/next start --hostname 127.0.0.1 --port ${port}`,
    cwd:fileURLToPath(new URL("..",import.meta.url)),url:baseURL+"/calls/demo-call/scoring-receipts",reuseExistingServer:false,timeout:30000,
    env:{SCORING_RECEIPTS_PROVIDER:"api",CALL_AUDIT_PROVIDER:"api",API_BASE_URL:`http://127.0.0.1:${api}`,NEXT_TELEMETRY_DISABLED:"1"}},
  projects: [1440,1280,390].map(width=>({name:`scoring-${width}`,use:{viewport:{width,height:1000}}})),
});
