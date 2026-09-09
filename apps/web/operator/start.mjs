import next from "next";
import { fileURLToPath } from "node:url";
import { gateway, port } from "./gateway.ts";

// No public bind option, server credential or automatic API/collector startup.
// These explicit ports are read before Next's standard application environment loading.
if (process.argv.length !== 2) throw new Error("This launcher does not accept arguments");
const uiPort = port(process.env.CPI_OPERATOR_UI_PORT);
const apiPort = port(process.env.CPI_OPERATOR_API_PORT);
if (uiPort === apiPort) throw new Error("UI and API ports must differ");
process.env.NODE_ENV = "production";
const app = next({ dev: false, dir: fileURLToPath(new URL("..", import.meta.url)), hostname: "127.0.0.1", port: uiPort });
await app.prepare();
const server = gateway(apiPort, app.getRequestHandler());
await new Promise((resolve, reject) => {
  server.once("error", reject);
  server.listen(uiPort, "127.0.0.1", resolve);
});
globalThis[Symbol.for("wsr.cpi.operator.loopback.v1")] = true;
console.log(`CPI operator view: http://127.0.0.1:${uiPort}/operator/cpi (manual read only; API not activated)`);
let stopping = false;
async function stop() {
  if (stopping) return;
  stopping = true;
  delete globalThis[Symbol.for("wsr.cpi.operator.loopback.v1")];
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
  await app.close();
}
process.on("SIGINT", () => { void stop(); });
process.on("SIGTERM", () => { void stop(); });
