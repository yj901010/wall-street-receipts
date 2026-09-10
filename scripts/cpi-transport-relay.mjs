// ADR-078 test-only byte relay. No protocol decoding, payload recording or runtime image inclusion.
import { createServer as tcpServer, createConnection } from "node:net";
import { createServer as httpServer } from "node:http";
import { pathToFileURL } from "node:url";
import assert from "node:assert/strict";

export async function startRelay(options = {}) {
  const { upstreamHost = "postgres", upstreamPort = 5432, listenHost = "0.0.0.0",
    listenPort = 15432, controlPort = 15433 } = options;
  assert.ok(["postgres", "127.0.0.1"].includes(upstreamHost));
  assert.ok(["0.0.0.0", "127.0.0.1"].includes(listenHost));
  for (const port of [upstreamPort, listenPort, controlPort]) assert.ok(Number.isInteger(port) && port >= 0 && port <= 65535);
  assert.ok(upstreamPort > 0);
  const links = new Map();
  let sequence = 0;
  const snapshot = () => [...links.values()].map(({ id, silenced, discardedBytes, closed }) => ({ id, silenced, discardedBytes, closed }));
  const server = tcpServer(client => {
    if (sequence >= 128 || [...links.values()].filter(link => !link.closed).length >= 16) { client.destroy(); return; }
    const upstream = createConnection({ host: upstreamHost, port: upstreamPort });
    const link = { id: ++sequence, silenced: false, discardedBytes: 0, closed: false, client, upstream };
    links.set(link.id, link);
    const close = () => { link.closed = true; client.destroy(); upstream.destroy(); };
    for (const socket of [client, upstream]) { socket.on("error", close); socket.on("close", close); }
    client.on("data", chunk => { if (!upstream.write(chunk)) client.pause(); });
    upstream.on("drain", () => client.resume());
    upstream.on("data", chunk => {
      if (link.silenced) link.discardedBytes += chunk.length;
      else if (!client.write(chunk)) upstream.pause();
    });
    client.on("drain", () => upstream.resume());
    upstream.setTimeout(30000, close);
  });
  const control = httpServer({ maxHeaderSize: 1024, requestTimeout: 2000, headersTimeout: 2000 }, (req, res) => {
    res.setHeader("Cache-Control", "no-store");
    if (req.socket.remoteAddress !== "127.0.0.1" || req.headers["transfer-encoding"]
      || req.headers["content-length"] && req.headers["content-length"] !== "0") {
      res.writeHead(400).end(); return;
    }
    if (req.method === "POST" && req.url === "/silence") {
      const active = [...links.values()].filter(link => !link.closed && !link.silenced);
      if (!active.length) { res.writeHead(409).end(); return; }
      for (const link of active) link.silenced = true;
    } else if (req.method !== "GET" || req.url !== "/status") { res.writeHead(404).end(); return; }
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ links: snapshot() }));
  });
  control.on("clientError", (_error, socket) => socket.destroy());
  const listen = (socket, port, host) => new Promise((resolve, reject) => {
    socket.once("error", reject); socket.listen(port, host, () => { socket.off("error", reject); resolve(); });
  });
  const close = async () => {
    for (const link of links.values()) { link.client.destroy(); link.upstream.destroy(); }
    control.closeAllConnections();
    await Promise.all([server, control].map(socket => new Promise(resolve => socket.close(resolve))));
  };
  try { await listen(server, listenPort, listenHost); await listen(control, controlPort, "127.0.0.1"); }
  catch (error) { await close(); throw error; }
  return { port: server.address().port, controlPort: control.address().port, snapshot, close };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    assert.equal(process.argv.length, 3);
    const mode = process.argv[2];
    if (mode === "--confirm-disposable-demo") {
      const relay = await startRelay();
      let stopping = false;
      for (const signal of ["SIGTERM", "SIGINT"]) process.on(signal, async () => {
        if (!stopping) { stopping = true; await relay.close(); }
      });
      console.log("DEMO transport relay ready");
    } else {
      assert.ok(["status", "silence"].includes(mode));
      const response = await fetch("http://127.0.0.1:15433/" + mode, {
        method: mode === "silence" ? "POST" : "GET", redirect: "error", signal: AbortSignal.timeout(2000),
      });
      assert.equal(response.status, 200);
      console.log(JSON.stringify(await response.json()));
    }
  } catch { console.error("DEMO transport relay failed"); process.exitCode = 1; }
}
