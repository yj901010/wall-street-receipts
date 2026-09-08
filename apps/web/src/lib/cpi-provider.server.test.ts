// @vitest-environment node
import { describe, expect, it, vi } from "vitest";
import { loadCpi } from "./cpi-provider.server";
import { cpiFixture } from "@/test/cpi-fixture";
const env = { CPI_PROVIDER: "api", API_BASE_URL: "http://127.0.0.1:8080/private/" };
function response() { return Response.json({ ...cpiFixture(), dataMode: "OBSERVED_MONTHLY" }); }
describe("CPI server transport", () => {
  it("is off by default without touching network", async () => {
    const fetcher = vi.fn(); expect(await loadCpi({}, fetcher)).toEqual({ kind: "disabled" }); expect(fetcher).not.toHaveBeenCalled();
  });
  it("reads the stored endpoint once with a deadline and no cache or redirect", async () => {
    const fetcher = vi.fn().mockResolvedValue(response());
    expect((await loadCpi(env, fetcher)).kind).toBe("ready");
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0].href).toBe("http://127.0.0.1:8080/private/v1/macro/cpi");
    expect(fetcher.mock.calls[0][1]).toMatchObject({ method: "GET", redirect: "error", cache: "no-store", headers: { Accept: "application/json" } });
    expect(fetcher.mock.calls[0][1].signal).toBeInstanceOf(AbortSignal);
  });
  it("represents 404 as empty, never DEMO", async () => {
    expect(await loadCpi(env, vi.fn().mockResolvedValue(new Response(null, { status: 404 })))).toEqual({ kind: "empty" });
  });
  it.each(["fixture", "API", "", "live"])("rejects invalid mode %s", async mode => {
    const fetcher = vi.fn(); await expect(loadCpi({ ...env, CPI_PROVIDER: mode }, fetcher)).rejects.toThrow(); expect(fetcher).not.toHaveBeenCalled();
  });
  it.each(["", "ftp://localhost", "https://user:secret@localhost", "https://localhost/?key=secret", "https://localhost/#fragment"])("rejects unsafe base URL %s", async url => {
    const fetcher = vi.fn(); await expect(loadCpi({ ...env, API_BASE_URL: url }, fetcher)).rejects.toThrow("No substitute"); expect(fetcher).not.toHaveBeenCalled();
  });
  it.each([429, 500, 302, 401])("does not retry HTTP %s", async status => {
    const fetcher = vi.fn().mockResolvedValue(new Response("private failure", { status }));
    await expect(loadCpi(env, fetcher)).rejects.toThrow("No substitute"); expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it("rejects oversized, malformed, non-JSON and DEMO replies", async () => {
    for (const reply of [new Response("x".repeat(262145), { headers: { "content-type": "application/json" } }),
      new Response("{}"), Response.json(cpiFixture()), new Response("{", { headers: { "content-type": "application/json" } })]) {
      await expect(loadCpi(env, vi.fn().mockResolvedValue(reply))).rejects.toThrow("No substitute");
    }
  });
});
