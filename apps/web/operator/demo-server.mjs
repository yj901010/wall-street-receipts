/** Disposable offline browser rehearsal only. Never used by start.mjs. No real credentials or provider. */
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
const TOKEN = Buffer.alloc(32, 7).toString("base64");
const id = n => `00000000-0000-0000-0000-${String(n).padStart(12, "0")}`;
const metadata = { schemaVersion: "1.0.0", dataMode: "UNVERIFIED", evidenceMode: "PERSISTED_ATTEMPT_RECORDS", timezone: "Asia/Seoul",
  observedAtKst: "2026-09-09T00:01:00+09:00", limitations: ["NOT_A_HEARTBEAT", "NOT_CPI_FRESHNESS", "NOT_POINT_IN_TIME_HISTORY", "RECEIPTS_NOT_REPLAYED", "NO_PROVIDER_REQUEST_PROOF", "NO_PRE_V11_OR_MISSING_START_HISTORY"] };
const row = n => ({ attemptId: id(n), trigger: n % 2 ? "MANUAL" : "SCHEDULED", startedAtKst: "2026-09-08T23:59:59.123456+09:00", gatePermitted: true, status: "UNKNOWN", terminal: null });
const api = createServer((req, res) => {
  res.setHeader("Content-Type", "application/json"); res.setHeader("Cache-Control", "no-store");
  if (req.method !== "GET" || req.headers.authorization !== `Bearer ${TOKEN}`) { res.statusCode = 401; res.end('{"error":"DEMO rejected"}'); return; }
  const path = "/internal/v1/cpi/collection-attempts";
  if (req.url === path) { res.end(JSON.stringify({ metadata, limit: 20, hasMore: true, order: "STARTED_AT_DESC_ATTEMPT_ID_DESC", attempts: Array.from({ length: 20 }, (_, i) => row(24 - i)) })); return; }
  if (req.url === path + "/" + id(1)) { res.end(JSON.stringify({ metadata, attempt: { ...row(1), status: "SAVED", terminal: { completedAtKst: "2026-09-09T00:00:00+09:00", captureId: id(90), capturedAtKst: "2026-09-08T23:59:59.999999+09:00", failureCode: null, retryNotBeforeKst: null } } })); return; }
  res.statusCode = 404; res.end('{"error":"DEMO missing"}');
});
await new Promise(resolve => api.listen(0, "127.0.0.1", resolve));
// Forward only runtime necessities; never inherit provider keys or operator secrets.
const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => /^(PATH|SYSTEMROOT|WINDIR|TEMP|TMP|COMSPEC|PATHEXT)$/i.test(key)));
const child = spawn(process.execPath, [fileURLToPath(new URL("./start.mjs", import.meta.url))], {
  cwd: fileURLToPath(new URL("..", import.meta.url)), windowsHide: true, stdio: "inherit",
  env: { ...env, CPI_OPERATOR_UI_PORT: "3470", CPI_OPERATOR_API_PORT: String(api.address().port), NEXT_TELEMETRY_DISABLED: "1" },
});
child.on("exit", code => { api.close(); process.exitCode = code ?? 1; });
function stop() { child.kill(); api.closeAllConnections(); api.close(); }
process.on("SIGTERM", stop); process.on("SIGINT", stop);
