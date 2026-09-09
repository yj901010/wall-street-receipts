// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";
import { request } from "node:http";
import type { AddressInfo } from "node:net";
import { gateway, port } from "../../operator/gateway";
import { operatorFixture } from "../test/operator-cpi-fixture";

const TOKEN = Buffer.alloc(32, 7).toString("base64"); // Synthetic DEMO credential.
const headers = { Authorization: `Bearer ${TOKEN}`, "X-WSR-Operator": "cpi-read-v1" };
const servers: ReturnType<typeof gateway>[] = [];
afterEach(async () => { for (const server of servers.splice(0)) { server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve())); } });
async function start(fetcher = vi.fn<typeof fetch>().mockResolvedValue(Response.json(operatorFixture()))) {
  const page = vi.fn(async (_req, res) => { res.end("operator shell"); });
  const server = gateway(18999, page, fetcher); servers.push(server);
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  const origin = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  return { server, origin, fetcher, page };
}
describe("dedicated loopback CPI bridge", () => {
  it("requires explicit numeric ports", () => {
    for (const value of [undefined, "80", "0", "1023", "65536", " 8000", "1e4", "https://host"]) expect(() => port(value)).toThrow();
    expect(port("1024")).toBe(1024); expect(port("65535")).toBe(65535);
  });
  it("forwards only fixed GET and bearer; returns no-store and strips upstream errors", async () => {
    const { origin, fetcher } = await start();
    const response = await fetch(origin + "/operator/cpi/query", { headers: { ...headers, Cookie: "not-forwarded", "X-Forwarded-Host": "evil" } });
    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.headers.get("content-security-policy")).toContain("frame-ancestors 'none'");
    expect(await response.json()).toEqual(operatorFixture());
    const [url, init] = fetcher.mock.calls[0];
    expect(url).toBe("http://127.0.0.1:18999/internal/v1/cpi/collection-attempts");
    expect(init).toMatchObject({ method: "GET", redirect: "error", headers: { Authorization: `Bearer ${TOKEN}`, Accept: "application/json" } });
    expect(Object.keys(init!.headers!)).toHaveLength(2);
  });
  it.each([
    ["/operator/cpi/query", {}, 401],
    ["/operator/cpi/query", { Authorization: "Bearer invalid" }, 401],
    ["/operator/cpi/query", { Authorization: `Bearer ${TOKEN}` }, 403],
    ["/operator/cpi/query", { ...headers, Origin: "https://evil.example" }, 403],
    ["/operator/cpi/query", { ...headers, "Sec-Fetch-Site": "cross-site" }, 403],
    ["/operator/cpi/query/../sec", headers, 404],
    ["/operator/cpi/query/?token=hidden", headers, 400],
    ["/operator/cpi/query/INVALID", headers, 400],
    ["/v1/macro/cpi", headers, 404],
    ["/internal/v1/sec/collection-attempts", headers, 404],
  ] as const)("rejects untrusted request %s %#", async (path, custom, status) => {
    const { origin, fetcher, page } = await start();
    const response = await fetch(origin + path, { headers: custom });
    expect(response.status).toBe(status); expect(fetcher).not.toHaveBeenCalled(); expect(page).not.toHaveBeenCalled();
    expect(await response.text()).not.toContain("hidden");
  });
  it("rejects non-GET, host rebinding and duplicate credentials before fetch", async () => {
    const { origin, fetcher } = await start();
    for (const method of ["POST", "PUT", "DELETE", "OPTIONS", "HEAD"]) {
      expect((await fetch(origin + "/operator/cpi/query", { method, headers })).status).toBe(405);
    }
    for (const custom of [{ Host: "evil.example", ...headers }, { ...headers, Authorization: [`Bearer ${TOKEN}`, `Bearer ${TOKEN}`] }]) {
      const status = await new Promise(resolve => {
        const req = request(origin + "/operator/cpi/query", { headers: custom }, res => { res.resume(); resolve(res.statusCode); }); req.end();
      });
      expect([401, 403]).toContain(status);
    }
    expect(fetcher).not.toHaveBeenCalled();
  });
  it("does not forward shell credentials, cookies or Next internal routing headers", async () => {
    const { origin, page } = await start();
    const response = await fetch(origin + "/operator/cpi", { headers: { ...headers, Cookie: "secret", "X-Middleware-Rewrite": "/private", RSC: "1" } });
    expect(await response.text()).toBe("operator shell");
    expect(page.mock.calls[0][0].headers).not.toHaveProperty("authorization");
    expect(page.mock.calls[0][0].headers).not.toHaveProperty("cookie");
    expect(page.mock.calls[0][0].headers).not.toHaveProperty("rsc");
  });
  it.each([401, 403, 404, 429, 500, 302])("never relays raw error body/headers (%i)", async status => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response("secret SQL failure", { status, headers: { Location: "https://evil.example", "Set-Cookie": "secret" } }));
    const { origin } = await start(fetcher);
    const response = await fetch(origin + "/operator/cpi/query", { headers });
    expect(response.status).toBe(status >= 500 || status === 302 ? 503 : status);
    expect(response.headers.get("location")).toBeNull(); expect(response.headers.get("set-cookie")).toBeNull();
    expect(await response.text()).not.toContain("secret");
  });
  it("fails closed on oversized/malformed payloads and exact selection mismatch", async () => {
    for (const response of [Response.json({ ...operatorFixture(), extra: "secret" }), new Response("x".repeat(65_537), { headers: { "Content-Type": "application/json" } })]) {
      const { origin } = await start(vi.fn<typeof fetch>().mockResolvedValue(response));
      expect((await fetch(origin + "/operator/cpi/query", { headers })).status).toBe(503);
    }
    const raw = operatorFixture();
    const { origin } = await start(vi.fn<typeof fetch>().mockResolvedValue(Response.json({ metadata: raw.metadata, attempt: raw.attempts[0] })));
    expect((await fetch(origin + "/operator/cpi/query/00000000-0000-0000-0000-000000000002", { headers })).status).toBe(503);
  });
  it("bounds concurrent reads and provides Retry-After without queuing", async () => {
    let release!: (response: Response) => void;
    const fetcher = vi.fn<typeof fetch>().mockImplementation(() => new Promise(resolve => { release = resolve; }));
    const { origin } = await start(fetcher);
    const first = fetch(origin + "/operator/cpi/query", { headers });
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledOnce());
    const second = await fetch(origin + "/operator/cpi/query", { headers });
    expect(second.status).toBe(429); expect(second.headers.get("retry-after")).toBe("1");
    release(Response.json(operatorFixture())); expect((await first).status).toBe(200);
    expect(fetcher).toHaveBeenCalledOnce();
  });
});
