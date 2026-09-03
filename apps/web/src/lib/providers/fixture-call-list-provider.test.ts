import { describe, expect, it, vi } from "vitest";
import type { CallsProvider, CallsQuery } from "./calls-provider";
import { FixtureCallListProvider } from "./fixture-call-list-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";

describe("FixtureCallListProvider", () => {
  it("returns one coherent fixture snapshot with AVAILABLE dataset evidence", async () => {
    const snapshot = await new FixtureCallListProvider().list({ size: 1 });

    expect(snapshot.items).toHaveLength(1);
    expect(snapshot.items[0]!.call.callId).toBe("demo-call-002");
    expect(snapshot.dataMode).toBe("DEMO");
    expect(snapshot.returnedPageEvidence).toEqual({
      scope: "RETURNED_PAGE",
      latestCallCapturedAt: "2026-08-11T14:22:00Z",
      callProvenanceIds: ["fixture-analyst-calls-v1"],
    });
    expect(snapshot.datasetEvidence).toMatchObject({
      availability: "AVAILABLE",
      asOf: "2026-08-18T00:00:00Z",
      source: "fixture-analyst-calls-v1",
    });
  });

  it.each([
    ["assetId", { assetId: "ASSET-NVDA" }],
    ["institutionId", { institutionId: "INST-GS" }],
    ["analystId", { analystId: "ANALYST-DEMO-B" }],
  ] satisfies Array<[string, CallsQuery]>) (
    "uses exact case-sensitive %s identity semantics and returns a valid empty snapshot",
    async (_label, query) => {
      const snapshot = await new FixtureCallListProvider().list(query);
      expect(snapshot.items).toEqual([]);
      expect(snapshot.page).toMatchObject({ totalElements: 0, totalPages: 0, last: true });
      expect(snapshot.returnedPageEvidence.latestCallCapturedAt).toBeNull();
    },
  );

  it("keeps ticker matching explicitly case-insensitive", async () => {
    const lower = await new FixtureCallListProvider().list({ ticker: "nvda" });
    const upper = await new FixtureCallListProvider().list({ ticker: "NVDA" });
    expect(lower.items.map(({ call }) => call.callId)).toEqual(["demo-call-002"]);
    expect(upper.items).toEqual(lower.items);
  });

  it.each(["REALTIME", "DELAYED", "EOD"] as const)(
    "rejects direct %s mode before reading fixture list or metadata",
    async (dataMode) => {
      const delegate = new FixtureCallsProvider();
      const list = vi.spyOn(delegate, "list");
      const metadata = vi.spyOn(delegate, "metadata");
      await expect(new FixtureCallListProvider(delegate).list({ dataMode })).rejects.toThrow(/must equal DEMO/);
      expect(list).not.toHaveBeenCalled();
      expect(metadata).not.toHaveBeenCalled();
    },
  );

  it("supports Spring-compatible nanosecond and legal offset range filters", async () => {
    const provider = new FixtureCallListProvider();
    const nanos = await provider.list({ from: "2026-08-11T14:20:00.000000001Z" });
    const offset = await provider.list({
      from: "2026-08-11T14:20:00.000000001+00:00",
      to: "2026-08-13T12:00:00.000000002+18:00",
    });
    expect(nanos.items).toEqual([]);
    expect(offset.items).toEqual([]);
    await expect(provider.list({ from: "2026-08-11T00:00:00+18:01" }))
      .rejects.toThrow(/Java-compatible UTC offset/);
  });

  it("propagates list and metadata failures without returning a partial snapshot", async () => {
    const base = new FixtureCallsProvider();
    const page = await base.list({ dataMode: "DEMO" });
    const metadata = await base.metadata();
    const listFailure: CallsProvider = {
      ...base,
      list: vi.fn(async () => { throw new Error("list unavailable"); }),
      metadata: vi.fn(async () => metadata),
      findById: base.findById.bind(base),
      findContextByCallId: base.findContextByCallId.bind(base),
    };
    const metadataFailure: CallsProvider = {
      ...base,
      list: vi.fn(async () => page),
      metadata: vi.fn(async () => { throw new Error("metadata unavailable"); }),
      findById: base.findById.bind(base),
      findContextByCallId: base.findContextByCallId.bind(base),
    };

    await expect(new FixtureCallListProvider(listFailure).list()).rejects.toThrow("list unavailable");
    await expect(new FixtureCallListProvider(metadataFailure).list()).rejects.toThrow("metadata unavailable");
  });

  it("rejects non-DEMO or stale fixture dataset evidence", async () => {
    const base = new FixtureCallsProvider();
    const page = await base.list({ dataMode: "DEMO" });
    const metadata = await base.metadata();
    const port = (mode: "REALTIME" | "DEMO", asOf: string): CallsProvider => ({
      list: vi.fn(async () => structuredClone(page)),
      metadata: vi.fn(async () => ({ ...structuredClone(metadata), dataMode: mode, asOf })),
      findById: base.findById.bind(base),
      findContextByCallId: base.findContextByCallId.bind(base),
    });

    await expect(new FixtureCallListProvider(port("REALTIME", metadata.asOf)).list())
      .rejects.toThrow(/metadata must remain DEMO/);
    await expect(new FixtureCallListProvider(port("DEMO", "2026-08-11T14:21:59.999999Z")).list())
      .rejects.toThrow(/captured after the fixture dataset asOf/);
  });
});
