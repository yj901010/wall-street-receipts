import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import fixture from "./fixtures/sec-manifest-audit-demo.json";
import { ApiSecManifestAuditProvider } from "./api-sec-manifest-audit-provider.server";
import type {
  SecManifestAuditQuery,
  SecManifestAuditView,
} from "./sec-manifest-audit-provider";

function query(view: SecManifestAuditView = "summary"): SecManifestAuditQuery {
  return {
    manifestId: fixture.manifestId,
    evaluationAsOf: fixture.evaluationAsOf,
    view,
    page: 0,
    size: 25,
  };
}

function json(value: unknown, status = 200, contentType = "application/json; charset=utf-8") {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": contentType },
  });
}

describe("ApiSecManifestAuditProvider", () => {
  beforeEach(() => {
    vi.stubGlobal("window", undefined);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it.each([
    ["summary", ""],
    ["descriptors", "/descriptors"],
    ["accessions", "/accessions"],
    ["occurrences", "/occurrences"],
  ] as const)("requests only the selected %s resource with exact no-store transport", async (view, suffix) => {
    const calls: Array<{ url: URL; init?: RequestInit }> = [];
    const fetcher = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: new URL(input instanceof Request ? input.url : input.toString()), init });
      return json(structuredClone(fixture[view]));
    });
    const provider = new ApiSecManifestAuditProvider("http://api.example.test/base", fetcher);

    await expect(provider.findExact(query(view))).resolves.toMatchObject({ view });
    expect(fetcher).toHaveBeenCalledOnce();
    expect(calls[0]?.url.pathname).toBe(
      `/base/v1/sec/filing-history/manifests/${fixture.manifestId}${suffix}`,
    );
    expect(calls[0]?.url.searchParams.get("evaluationAsOf")).toBe(fixture.evaluationAsOf);
    expect(calls[0]?.url.searchParams.has("page")).toBe(view !== "summary");
    expect(calls[0]?.url.searchParams.has("size")).toBe(view !== "summary");
    expect(calls[0]?.init).toMatchObject({
      method: "GET",
      cache: "no-store",
      redirect: "error",
      headers: { Accept: "application/json" },
    });
    expect(calls[0]?.init?.credentials).toBeUndefined();
  });

  it("maps only an exact 404 to unavailable without a fixture fallback", async () => {
    const fetcher = vi.fn(async () => json({ code: "NOT_FOUND" }, 404, "application/problem+json"));
    await expect(new ApiSecManifestAuditProvider("http://api.example.test", fetcher)
      .findExact(query())).resolves.toBeNull();
    expect(fetcher).toHaveBeenCalledOnce();
  });

  it.each([
    ["network", async () => { throw new TypeError("offline"); }, /request failed/],
    ["status", async () => json({ code: "UPSTREAM" }, 503), /HTTP 503/],
    ["content type", async () => json({}, 200, "text/html"), /application\/json/],
    ["malformed JSON", async () => new Response("{", {
      status: 200,
      headers: { "content-type": "application/json" },
    }), /malformed JSON/],
  ] as const)("fails closed on %s failure", async (_label, transport, message) => {
    const fetcher = vi.fn(transport);
    await expect(new ApiSecManifestAuditProvider("http://api.example.test", fetcher)
      .findExact(query())).rejects.toThrow(message);
  });

  it("accepts equivalent cutoff serialization and rejects additive or divergent identity", async () => {
    for (const [requested, serialized] of [
      ["2026-08-25T03:30:00Z", "2026-08-25T03:30:00Z"],
      ["2026-08-25T03:30:00.1Z", "2026-08-25T03:30:00.100Z"],
      ["2026-08-25T03:30:00.12Z", "2026-08-25T03:30:00.120Z"],
      ["2026-08-25T03:30:00.123Z", "2026-08-25T03:30:00.123Z"],
      ["2026-08-25T03:30:00.1234Z", "2026-08-25T03:30:00.123400Z"],
      ["2026-08-25T03:30:00.12345Z", "2026-08-25T03:30:00.123450Z"],
      ["2026-08-25T03:30:00.123456Z", "2026-08-25T03:30:00.123456Z"],
    ] as const) {
      const equivalent = structuredClone(fixture.summary);
      equivalent.evaluationAsOf = serialized;
      equivalent.assembledAt = "2026-08-25T03:29:00.123456Z";
      const equivalentFetch = vi.fn(async () => json(equivalent));
      await expect(new ApiSecManifestAuditProvider(
        "http://api.example.test",
        equivalentFetch,
      ).findExact({ ...query(), evaluationAsOf: requested })).resolves.toMatchObject({
        view: "summary",
        data: { evaluationAsOf: serialized },
      });
    }

    const additive = { ...structuredClone(fixture.summary), current: true };
    const additiveFetch = vi.fn(async () => json(additive));
    await expect(new ApiSecManifestAuditProvider("http://api.example.test", additiveFetch)
      .findExact(query())).rejects.toThrow("summary field set");

    const divergent = { ...structuredClone(fixture.summary), manifestId: "b".repeat(64) };
    const divergentFetch = vi.fn(async () => json(divergent));
    await expect(new ApiSecManifestAuditProvider("http://api.example.test", divergentFetch)
      .findExact(query())).rejects.toThrow("exact request identity");
  });

  it("rejects unsafe base URLs and malformed internal queries before transport", async () => {
    expect(() => new ApiSecManifestAuditProvider("ftp://api.example.test", vi.fn()))
      .toThrow(/HTTP\(S\)/);
    expect(() => new ApiSecManifestAuditProvider("http://user:secret@api.example.test", vi.fn()))
      .toThrow(/without credentials/);
    expect(() => new ApiSecManifestAuditProvider("http://api.example.test?mode=demo", vi.fn()))
      .toThrow(/without credentials, query, or fragment/);

    const fetcher = vi.fn();
    const provider = new ApiSecManifestAuditProvider("http://api.example.test", fetcher);
    await expect(provider.findExact({ ...query("descriptors"), size: 101 }))
      .rejects.toThrow("invalid internal query");
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("rejects construction in a browser runtime", () => {
    vi.stubGlobal("window", {});
    expect(() => new ApiSecManifestAuditProvider("http://api.example.test", vi.fn()))
      .toThrow("server-only");
  });
});
