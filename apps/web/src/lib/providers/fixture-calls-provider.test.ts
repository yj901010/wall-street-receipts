import { describe, expect, it } from "vitest";
import { FixtureCallsProvider } from "./fixture-calls-provider";

describe("FixtureCallsProvider", () => {
  const provider = new FixtureCallsProvider();

  it("joins canonical call identities and exposes zero-based page metadata", async () => {
    const result = await provider.list({ size: 1, page: 0 });

    expect(result.items).toHaveLength(1);
    expect(result.items[0]).toMatchObject({
      call: {
        callId: "demo-call-002",
        provider: "fixture",
        sourceReferenceId: "source-ref-demo-002",
        dataMode: "DEMO",
      },
      institution: { institutionId: "inst-gs", canonicalName: "Goldman Sachs" },
      analyst: { analystId: "analyst-demo-b", canonicalName: "Demo Analyst B" },
      asset: { assetId: "asset-nvda", assetType: "EQUITY", ticker: "NVDA" },
      source: {
        document: { sourceDocumentId: "source-demo-video-002" },
        reference: { sourceReferenceId: "source-ref-demo-002" },
      },
    });
    expect(result.page).toEqual({
      number: 0,
      size: 1,
      totalElements: 3,
      totalPages: 3,
      first: true,
      last: false,
      sort: { field: "eventTime", order: "desc" },
    });
  });

  it("applies canonical filters, ordering, pagination, and an empty result", async () => {
    const filtered = await provider.list({
      institutionId: "inst-jpm",
      ticker: "spx",
      direction: "BULLISH",
      status: "ACTIVE",
      dataMode: "DEMO",
      from: "2026-08-10T00:00:00Z",
      to: "2026-08-11T00:00:00Z",
      sort: "capturedAt",
      order: "asc",
    });
    const empty = await provider.list({ ticker: "TSLA" });

    expect(filtered.items.map((item) => item.call.callId)).toEqual(["demo-call-001"]);
    expect(filtered.page.sort).toEqual({ field: "capturedAt", order: "asc" });
    expect(empty.items).toEqual([]);
    expect(empty.page.totalElements).toBe(0);
    expect(empty.page.number).toBe(0);
  });

  it("returns source evidence and the immutable point-in-time snapshot", async () => {
    const detail = await provider.findById("demo-call-002");

    expect(detail?.source).toMatchObject({
      document: {
        sourceDocumentId: "source-demo-video-002",
        canonicalUrl: "https://example.invalid/demo-call-002",
      },
      reference: { sourceReferenceId: "source-ref-demo-002", verified: false },
    });
    expect(detail?.snapshot).toMatchObject({
      snapshotId: "market-snapshot-demo-002",
      immutable: true,
      assetPrice: 183.42,
      vix: null,
    });
    expect(await provider.findById("unknown-call")).toBeNull();
  });

  it("preserves nullable source document metadata instead of inventing values", async () => {
    const detail = await provider.findById("demo-call-003");

    expect(detail?.source.document).toMatchObject({
      sourceDocumentId: "source-demo-article-003",
      publisher: null,
      canonicalUrl: null,
      publishedAt: null,
      externalId: null,
      contentHash: null,
    });
  });

  it("materializes ordered point-in-time context with its source lineage", async () => {
    const context = await provider.findContextByCallId("demo-call-001");

    expect(context?.macroSnapshot).toMatchObject({
      schemaVersion: "1.0.0",
      macroSnapshotId: "macro-snapshot-demo-001",
      callId: "demo-call-001",
      immutable: true,
      dataMode: "DEMO",
      provenanceId: "fixture-call-contexts-v1",
    });
    expect(context?.macroSnapshot).not.toHaveProperty("observationIds");
    expect(context?.macroSnapshot?.observations.map(({ series }) => series)).toEqual([
      "FED_FUNDS_LOWER",
      "FED_FUNDS_UPPER",
      "CPI_YOY",
      "CORE_CPI_YOY",
      "PPI_YOY",
      "UNEMPLOYMENT_RATE",
    ]);
    expect(context?.macroSnapshot?.observations.find(({ series }) => series === "CPI_YOY")).toMatchObject({
      schemaVersion: "1.0.0",
      macroObservationId: "macro-observation-demo-cpi-original-001",
      value: 3.1,
      sourceReferenceId: "source-ref-demo-macro-inflation-original-001",
      dataMode: "DEMO",
      provenanceId: "fixture-call-contexts-v1",
    });
    expect(context?.macroSnapshot?.observations.find(({ series }) => series === "PPI_YOY")?.value).toBeNull();
    expect(context?.macroSnapshot?.observations.map(({ macroObservationId }) => macroObservationId)).not.toContain(
      "macro-observation-demo-cpi-revision-001",
    );
    expect(context?.eventContext).toMatchObject({
      schemaVersion: "1.0.0",
      eventContextId: "event-context-demo-001",
      earningsAt: null,
      nextCpiAt: "2026-08-12T12:30:00Z",
      sourceReferenceId: "source-ref-demo-event-calendar-001",
      immutable: true,
      dataMode: "DEMO",
      provenanceId: "fixture-call-contexts-v1",
    });
    expect(await provider.findById("demo-call-001")).not.toHaveProperty("macroSnapshot");
  });

  it("returns the exact nullable response for a known-empty call", async () => {
    const knownEmptyContext = {
      macroSnapshot: null,
      eventContext: null,
    };

    await expect(provider.findContextByCallId("demo-call-002")).resolves.toEqual(knownEmptyContext);
    await expect(provider.findContextByCallId("demo-call-003")).resolves.toEqual(knownEmptyContext);
    await expect(provider.findContextByCallId("unknown-call")).resolves.toBeNull();
  });

  it("echoes an out-of-range page and rejects invalid exclusive time ranges", async () => {
    const result = await provider.list({ page: 99, size: 25 });

    expect(result.items).toEqual([]);
    expect(result.page.number).toBe(99);
    expect(result.page.last).toBe(true);
    await expect(provider.list({ from: "not-an-instant" })).rejects.toThrow("Invalid from instant");
    await expect(
      provider.list({ from: "2026-08-11T00:00:00Z", to: "2026-08-11T00:00:00Z" }),
    ).rejects.toThrow("to must be later than from");
  });
});
