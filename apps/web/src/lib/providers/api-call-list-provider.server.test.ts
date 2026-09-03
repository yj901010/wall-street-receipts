import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { effectiveCallListQuery } from "./call-list-adapter";
import { ApiCallListProvider } from "./api-call-list-provider.server";
import type { AnalystCallPage, CallsQuery } from "./calls-provider";
import { FixtureCallListProvider } from "./fixture-call-list-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";

function json(payload: unknown, status = 200, contentType = "application/json; charset=utf-8") {
  return new Response(JSON.stringify(payload), { status, headers: { "content-type": contentType } });
}

function emptyPage(query: CallsQuery = {}): AnalystCallPage {
  const effective = effectiveCallListQuery(query);
  return {
    items: [],
    page: {
      number: effective.page,
      size: effective.size,
      totalElements: 0,
      totalPages: 0,
      first: effective.page === 0,
      last: true,
      sort: { field: effective.sort, order: effective.order },
    },
  };
}

describe("ApiCallListProvider", () => {
  beforeEach(() => {
    vi.stubGlobal("window", undefined);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("uses one private server GET with exact defaults and no metadata request", async () => {
    const payload = await new FixtureCallsProvider().list({ dataMode: "DEMO" });
    const fetcher = vi.fn<typeof fetch>(async () => json(payload));
    const snapshot = await new ApiCallListProvider("http://api.example.test/base///", fetcher).list();

    expect(fetcher).toHaveBeenCalledOnce();
    expect(String(fetcher.mock.calls[0]![0])).toBe(
      "http://api.example.test/base/v1/calls?dataMode=DEMO&page=0&size=25&sort=eventTime&order=desc",
    );
    expect(fetcher.mock.calls[0]![1]).toEqual({
      method: "GET",
      cache: "no-store",
      redirect: "error",
      headers: { Accept: "application/json" },
    });
    expect(snapshot.items).toHaveLength(3);
    expect(snapshot.datasetEvidence).toEqual({
      availability: "NOT_EXPOSED",
      reason: "LIST_API_HAS_NO_DATASET_METADATA",
      asOf: null,
      source: null,
      disclaimer: null,
    });
  });

  it("serializes every filter and preserves Spring-compatible nanosecond offset instants", async () => {
    const query = {
      assetId: "asset:opaque",
      ticker: "BRK/B",
      institutionId: "inst:opaque",
      analystId: "analyst:opaque",
      direction: "BULLISH",
      status: "ACTIVE",
      dataMode: "DEMO",
      from: "2026-08-11T14:20:00.000000001+18:00",
      to: "2026-08-12T14:20:00.000000002-18:00",
      page: 7,
      size: 1,
      sort: "capturedAt",
      order: "asc",
    } satisfies CallsQuery;
    const fetcher = vi.fn<typeof fetch>(async () => json(emptyPage(query)));

    await new ApiCallListProvider("https://api.example.test", fetcher).list(query);

    expect(String(fetcher.mock.calls[0]![0])).toBe(
      "https://api.example.test/v1/calls?assetId=asset%3Aopaque&ticker=BRK%2FB&institutionId=inst%3Aopaque&analystId=analyst%3Aopaque&direction=BULLISH&status=ACTIVE&dataMode=DEMO&from=2026-08-11T14%3A20%3A00.000000001%2B18%3A00&to=2026-08-12T14%3A20%3A00.000000002-18%3A00&page=7&size=1&sort=capturedAt&order=asc",
    );
  });

  it.each(["REALTIME", "DELAYED", "EOD"] as const)(
    "rejects direct %s mode before transport instead of forcing DEMO",
    async (dataMode) => {
      const fetcher = vi.fn();
      const provider = new ApiCallListProvider("http://api.example.test", fetcher);
      await expect(provider.list({ dataMode })).rejects.toThrow(/must equal DEMO/);
      expect(fetcher).not.toHaveBeenCalled();
    },
  );

  it.each([
    ["network failure", async () => { throw new TypeError("offline"); }, /request failed/],
    ["redirect rejection", async () => { throw new TypeError("unexpected redirect"); }, /request failed/],
    ["HTTP 400", async () => json({}, 400), /HTTP 400/],
    ["HTTP 404", async () => json({}, 404), /HTTP 404/],
    ["HTTP 500", async () => json({}, 500), /HTTP 500/],
    ["JSONP", async () => json({}, 200, "application/jsonp"), /did not return application\/json/],
    ["HTML", async () => json({}, 200, "text/html"), /did not return application\/json/],
    ["malformed JSON", async () => new Response("{", {
      status: 200,
      headers: { "content-type": "application/json" },
    }), /malformed JSON/],
  ] satisfies Array<[string, () => Promise<Response>, RegExp]>) (
    "fails closed for %s and never falls back to fixture rows",
    async (_label, implementation, message) => {
      const fixture = vi.spyOn(FixtureCallListProvider.prototype, "list");
      const fetcher = vi.fn(implementation);
      const provider = new ApiCallListProvider("http://api.example.test", fetcher);
      await expect(provider.list()).rejects.toThrow(message);
      expect(fetcher).toHaveBeenCalledTimes(1);
      expect(fixture).not.toHaveBeenCalled();
    },
  );

  it("rejects malformed response shape as an error rather than an empty list", async () => {
    const fixture = vi.spyOn(FixtureCallListProvider.prototype, "list");
    const fetcher = vi.fn(async () => json({ items: [], page: emptyPage().page, metadata: {} }));
    const provider = new ApiCallListProvider(
      "http://api.example.test",
      fetcher,
    );
    await expect(provider.list()).rejects.toThrow(/must contain exactly/);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fixture).not.toHaveBeenCalled();
  });

  it.each([
    "relative/path",
    "ftp://api.example.test",
    "http://user:secret@api.example.test",
    "http://api.example.test?mode=demo",
    "http://api.example.test#fragment",
  ])("rejects invalid private API origin %s", (baseUrl) => {
    expect(() => new ApiCallListProvider(baseUrl)).toThrow(/API_BASE_URL/);
  });

  it("rejects construction in a browser runtime", () => {
    vi.stubGlobal("window", {});
    expect(() => new ApiCallListProvider("http://api.example.test")).toThrow(/server-only/);
  });
});
