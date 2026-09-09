import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { randomBytes } from "node:crypto";
// Node 24 runs this module directly; Next and Vitest compile the same closed adapter.
// @ts-expect-error Native Node requires the TypeScript extension.
import { adaptAttempts, readAttemptJson, ATTEMPT_ID } from "../src/lib/operator-cpi.ts";

type PageHandler = (request: IncomingMessage, response: ServerResponse) => Promise<void>;
export function port(value: string | undefined): number {
  if (!value || !/^[1-9]\d{3,4}$/.test(value) || Number(value) > 65535) throw new Error("An explicit port from 1024 to 65535 is required");
  if (Number(value) < 1024) throw new Error("An explicit port from 1024 to 65535 is required");
  return Number(value);
}
/** Dedicated process only. No ambient API URL, server token, cookies, proxy headers or arbitrary fetch destinations. */
export function gateway(apiPort: number, page: PageHandler, fetcher: typeof fetch = fetch) {
  port(String(apiPort));
  let active = false;
  let nextRead = 0;
  const server = createServer({ maxHeaderSize: 8192, headersTimeout: 5_000, requestTimeout: 10_000 }, async (req, res) => {
    const nonce = randomBytes(18).toString("base64");
    const csp = `default-src 'none'; script-src 'nonce-${nonce}' 'strict-dynamic'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; font-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`;
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Content-Security-Policy", csp);
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("X-Frame-Options", "DENY");
    res.setHeader("X-Robots-Tag", "noindex, nofollow");
    const fail = (status: number) => { res.statusCode = status; res.setHeader("Content-Type", "application/json"); res.end(JSON.stringify({ error: "CPI_OPERATOR_QUERY_UNAVAILABLE" })); };
    const host = `127.0.0.1:${req.socket.localPort}`;
    const one = (name: string) => req.rawHeaders.filter((_, index) => index % 2 === 0 && req.rawHeaders[index].toLowerCase() === name).length === 1;
    if (req.socket.remoteAddress !== "127.0.0.1" || !one("host") || req.headers.host !== host
      || req.headers.origin && req.headers.origin !== `http://${host}`
      || req.headers["sec-fetch-site"] && !["same-origin", "none"].includes(String(req.headers["sec-fetch-site"]))) { fail(403); return; }
    // No body, cookies, forwarding, rewrite or Next internal headers are trusted.
    if (req.headers["transfer-encoding"] || req.headers["content-length"] && req.headers["content-length"] !== "0") { fail(400); return; }
    const raw = req.url ?? "";
    const prefix = "/operator/cpi/query";
    const isQuery = raw === prefix || raw.startsWith(prefix + "/");
    if (isQuery) {
      if (req.method !== "GET") { fail(405); return; }
      const selection = raw === prefix ? "" : raw.slice(prefix.length + 1);
      if (selection && !ATTEMPT_ID.test(selection) || raw.endsWith("/") || raw.includes("?")) { fail(400); return; }
      if (!one("authorization") || !/^Bearer [A-Za-z0-9+/]{43}=$/.test(req.headers.authorization ?? "")) { fail(401); return; }
      if (!one("x-wsr-operator") || req.headers["x-wsr-operator"] !== "cpi-read-v1") { fail(403); return; }
      if (active || Date.now() < nextRead) { res.setHeader("Retry-After", "1"); fail(429); return; }
      active = true; nextRead = Date.now() + 1000;
      const aborted = new AbortController();
      const close = () => { if (!res.writableEnded) aborted.abort(); };
      res.on("close", close);
      try {
        const upstream = await fetcher(`http://127.0.0.1:${apiPort}/internal/v1/cpi/collection-attempts${selection ? "/" + selection : ""}`, {
          method: "GET", redirect: "error", cache: "no-store", credentials: "omit",
          signal: AbortSignal.any([aborted.signal, AbortSignal.timeout(5_000)]),
          headers: { Authorization: req.headers.authorization!, Accept: "application/json" },
        });
        if (upstream.status !== 200) {
          await upstream.body?.cancel();
          fail([400, 401, 403, 404, 429].includes(upstream.status) ? upstream.status : 503); return;
        }
        const payload = await readAttemptJson(upstream);
        adaptAttempts(payload, selection); // Reject malformed evidence before it reaches the browser.
        res.setHeader("Content-Type", "application/json");
        res.end(JSON.stringify(payload));
      } catch { if (!res.destroyed) fail(503); }
      finally { active = false; res.off("close", close); }
      return;
    }
    if (!["GET", "HEAD"].includes(req.method ?? "")) { fail(405); return; }
    const asset = /^\/_next\/static\/[A-Za-z0-9_./%()\[\]~-]+$/.test(raw) && !raw.includes("..") && !/%2e|%2f|%5c/i.test(raw);
    if (raw !== "/operator/cpi" && !asset) { fail(404); return; }
    // Never pass credentials or client-supplied Next routing instructions to rendering/logging.
    req.headers = { host, "content-security-policy": csp, "x-nonce": nonce };
    req.rawHeaders = ["Host", host];
    try { await page(req, res); } catch { if (!res.headersSent) fail(503); else res.destroy(); }
  });
  server.maxRequestsPerSocket = 100;
  server.keepAliveTimeout = 5000;
  server.on("clientError", (_error, socket) => { socket.end("HTTP/1.1 400 Bad Request\r\nConnection: close\r\nCache-Control: no-store\r\nContent-Length: 0\r\n\r\n"); });
  return server;
}
