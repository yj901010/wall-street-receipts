import test from "node:test";
import assert from "node:assert/strict";
import { createServer, createConnection } from "node:net";
import { once } from "node:events";
import { startRelay } from "../cpi-transport-relay.mjs";

async function waitFor(predicate) {
  const deadline = Date.now() + 2000;
  while (!predicate()) {
    assert.ok(Date.now() < deadline, "Bounded relay observation");
    await new Promise(resolve => setTimeout(resolve, 10));
  }
}

test("real bytes forward, selected established transport silences, new connection recovers", { timeout: 8000 }, async t => {
  const sockets = new Set();
  const upstream = createServer(socket => {
    sockets.add(socket); socket.on("close", () => sockets.delete(socket));
    socket.on("error", () => socket.destroy()); socket.pipe(socket);
  });
  upstream.listen(0, "127.0.0.1"); await once(upstream, "listening");
  t.after(async () => { for (const socket of sockets) socket.destroy(); await new Promise(resolve => upstream.close(resolve)); });
  const relay = await startRelay({ upstreamHost: "127.0.0.1", upstreamPort: upstream.address().port,
    listenHost: "127.0.0.1", listenPort: 0, controlPort: 0 });
  t.after(() => relay.close());
  const url = `http://127.0.0.1:${relay.controlPort}`;
  const client = createConnection({ host: "127.0.0.1", port: relay.port });
  t.after(() => client.destroy()); await once(client, "connect");
  const first = once(client, "data"); client.write("DEMO"); assert.equal((await first)[0].toString(), "DEMO");
  const silence = await fetch(url + "/silence", { method: "POST" });
  assert.equal(silence.status, 200); await silence.json();
  let unexpected = false; client.on("data", () => { unexpected = true; }); client.write("DISCARDED");
  await waitFor(() => relay.snapshot()[0].discardedBytes === 9);
  const next = createConnection({ host: "127.0.0.1", port: relay.port });
  t.after(() => next.destroy()); await once(next, "connect");
  const reply = once(next, "data"); next.write("RECOVERED"); assert.equal((await reply)[0].toString(), "RECOVERED");
  assert.equal(unexpected, false);
  client.destroy(); next.destroy(); await waitFor(() => relay.snapshot().every(link => link.closed));
  assert.deepEqual(relay.snapshot(), [
    { id: 1, silenced: true, discardedBytes: 9, closed: true },
    { id: 2, silenced: false, discardedBytes: 0, closed: true },
  ]);
});

test("closed control vocabulary, no active-link silence, no request bodies", { timeout: 5000 }, async t => {
  const relay = await startRelay({ listenHost: "127.0.0.1", listenPort: 0, controlPort: 0 });
  t.after(() => relay.close());
  const url = `http://127.0.0.1:${relay.controlPort}`;
  for (const [path, options, expected] of [["/status", {}, 200], ["/silence", { method: "POST" }, 409],
    ["/status?target=external", {}, 404], ["/silence", {}, 404], ["/status", { method: "POST" }, 404],
    ["/silence", { method: "POST", body: "external" }, 400]]) {
    const response = await fetch(url + path, options);
    assert.equal(response.status, expected); assert.equal(response.headers.get("cache-control"), "no-store"); await response.text();
  }
});

test("remote destinations, alternate binds and invalid ports fail before listening", async () => {
  for (const options of [{ upstreamHost: "example.com" }, { upstreamHost: "::1" }, { listenHost: "192.0.2.1" },
    { upstreamPort: 0 }, { listenPort: -1 }, { controlPort: 65536 }, { upstreamPort: "5432" }]) {
    await assert.rejects(startRelay(options));
  }
});

test("shutdown releases both listeners and duplicate bind rejects", { timeout: 5000 }, async () => {
  const relay = await startRelay({ listenHost: "127.0.0.1", listenPort: 0, controlPort: 0 });
  try { await assert.rejects(startRelay({ listenHost: "127.0.0.1", listenPort: relay.port, controlPort: 0 })); }
  finally { await relay.close(); }
  const replacement = await startRelay({ listenHost: "127.0.0.1", listenPort: relay.port, controlPort: relay.controlPort });
  await replacement.close();
});
