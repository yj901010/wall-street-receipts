import { describe, expect, it, vi } from "vitest";
import type {
  AnalystCallPage,
  CallsMetadata,
  CallsProvider,
} from "./calls-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";
import {
  FixtureSp500HistoryProvider,
  SP500_HISTORY_QUERY,
} from "./fixture-sp500-history-provider";

function callsPort(metadata: CallsMetadata, page: AnalystCallPage): CallsProvider {
  return {
    async metadata() {
      return metadata;
    },
    async list() {
      return page;
    },
    async findById() {
      throw new Error("History provider must not request call detail.");
    },
    async findContextByCallId() {
      throw new Error("History provider must not request call context.");
    },
  };
}

async function fixtureInputs() {
  const delegate = new FixtureCallsProvider();
  return {
    metadata: structuredClone(await delegate.metadata()),
    page: structuredClone(await delegate.list(SP500_HISTORY_QUERY)),
  };
}

function setPageCount(page: AnalystCallPage, totalElements: number) {
  page.page.totalElements = totalElements;
  page.page.totalPages = Math.ceil(totalElements / page.page.size);
  page.page.first = true;
  page.page.last = page.page.totalPages <= 1;
}

describe("FixtureSp500HistoryProvider", () => {
  it("requests the exact SPX page once and preserves the complete canonical call view", async () => {
    const delegate = new FixtureCallsProvider();
    const metadataValue = await delegate.metadata();
    const pageValue = await delegate.list(SP500_HISTORY_QUERY);
    const metadata = vi.fn(async () => metadataValue);
    const list = vi.fn(async () => pageValue);
    const provider = new FixtureSp500HistoryProvider({
      ...callsPort(metadataValue, pageValue),
      metadata,
      list,
    });

    const snapshot = await provider.history();

    expect(metadata).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalledWith({
      assetId: "asset-spx",
      page: 0,
      size: 25,
      sort: "eventTime",
      order: "desc",
    });
    expect(Object.keys(snapshot)).toEqual([
      "dataMode",
      "asOf",
      "source",
      "disclaimer",
      "asset",
      "items",
      "page",
    ]);
    expect(snapshot).toMatchObject({
      dataMode: "DEMO",
      asOf: "2026-08-18T00:00:00Z",
      source: "fixture-analyst-calls-v1",
      asset: {
        assetId: "asset-spx",
        assetType: "INDEX",
        canonicalName: "S&P 500 Index",
        ticker: "SPX",
      },
      page: {
        number: 0,
        size: 25,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
        sort: { field: "eventTime", order: "desc" },
      },
    });
    expect(snapshot.items).toEqual(pageValue.items);
    expect(snapshot.page).toEqual(pageValue.page);
    expect(snapshot.items.map(({ call }) => call.callId)).toEqual(["demo-call-001"]);
    expect(snapshot.disclaimer).toBe(
      "Synthetic DEMO events only; no record represents a real JPMorgan or Goldman Sachs analyst statement.",
    );
    expect(snapshot.items[0].source.document.contentHash).toBeNull();
    expect(snapshot.items[0]).not.toHaveProperty("snapshot");
    expect(snapshot.items[0]).not.toHaveProperty("context");
    expect(snapshot.items[0]).not.toHaveProperty("outcome");
  });

  it("accepts an honest empty fixed query without creating a placeholder", async () => {
    const { metadata, page } = await fixtureInputs();
    page.items = [];
    setPageCount(page, 0);

    const snapshot = await new FixtureSp500HistoryProvider(callsPort(metadata, page)).history();

    expect(snapshot.items).toEqual([]);
    expect(snapshot.page).toMatchObject({ totalElements: 0, totalPages: 0, last: true });
    expect(snapshot.asset.assetId).toBe("asset-spx");
  });

  it("accepts later canonical rows in deterministic order without mutating provider output", async () => {
    const { metadata, page } = await fixtureInputs();
    const appended = structuredClone(page.items[0]);
    appended.call.callId = "demo-call-004";
    appended.call.providerEventId = "fixture-call-004";
    appended.call.eventTime = "2026-08-09T12:00:00Z";
    page.items.push(appended);
    setPageCount(page, 2);
    const beforeMetadata = structuredClone(metadata);
    const beforePage = structuredClone(page);

    const snapshot = await new FixtureSp500HistoryProvider(callsPort(metadata, page)).history();

    expect(snapshot.items.map(({ call }) => call.callId)).toEqual([
      "demo-call-001",
      "demo-call-004",
    ]);
    expect(snapshot.items).toEqual(beforePage.items);
    expect(metadata).toEqual(beforeMetadata);
    expect(page).toEqual(beforePage);
  });

  it("passes through valid catalog metadata without hard-coding current fixture values", async () => {
    const { metadata, page } = await fixtureInputs();
    metadata.asOf = "2026-08-18T00:00:00.000001Z";
    metadata.source = "fixture-spx-history-appended-v2";
    metadata.disclaimer = "Distinct synthetic DEMO call-catalog disclaimer for append testing.";
    for (const view of page.items) {
      view.call.provenanceId = metadata.source;
      view.source.document.provenanceId = metadata.source;
      view.source.reference.provenanceId = metadata.source;
    }
    const beforeMetadata = structuredClone(metadata);
    const beforePage = structuredClone(page);

    const snapshot = await new FixtureSp500HistoryProvider(callsPort(metadata, page)).history();

    expect(snapshot.asOf).toBe("2026-08-18T00:00:00.000001Z");
    expect(snapshot.source).toBe("fixture-spx-history-appended-v2");
    expect(snapshot.disclaimer).toBe(
      "Distinct synthetic DEMO call-catalog disclaimer for append testing.",
    );
    expect(metadata).toEqual(beforeMetadata);
    expect(page).toEqual(beforePage);
  });

  it("requires call ID ascending for equal event-time rows", async () => {
    const correct = await fixtureInputs();
    const second = structuredClone(correct.page.items[0]);
    second.call.callId = "demo-call-004";
    second.call.providerEventId = "fixture-call-004-tie";
    correct.page.items.push(second);
    setPageCount(correct.page, 2);

    await expect(
      new FixtureSp500HistoryProvider(callsPort(correct.metadata, correct.page)).history(),
    ).resolves.toMatchObject({
      items: [
        { call: { callId: "demo-call-001" } },
        { call: { callId: "demo-call-004" } },
      ],
    });

    const wrong = structuredClone(correct.page);
    wrong.items.reverse();
    await expect(
      new FixtureSp500HistoryProvider(callsPort(correct.metadata, wrong)).history(),
    ).rejects.toThrow(/call ID ascending/i);
  });

  it("preserves microsecond precision when rejecting reordered event times", async () => {
    const { metadata, page } = await fixtureInputs();
    const second = structuredClone(page.items[0]);
    page.items[0].call.eventTime = "2026-08-10T12:00:00.000001Z";
    second.call.callId = "demo-call-004";
    second.call.providerEventId = "fixture-call-004";
    second.call.eventTime = "2026-08-10T12:00:00.000002Z";
    page.items.push(second);
    setPageCount(page, 2);

    await expect(
      new FixtureSp500HistoryProvider(callsPort(metadata, page)).history(),
    ).rejects.toThrow(/not ordered/i);
  });

  it.each([
    ["number", (page: AnalystCallPage) => {
      page.page.number = 1;
    }],
    ["size", (page: AnalystCallPage) => {
      page.page.size = 24;
    }],
    ["sort", (page: AnalystCallPage) => {
      page.page.sort.field = "capturedAt";
    }],
    ["order", (page: AnalystCallPage) => {
      page.page.sort.order = "asc";
    }],
    ["total pages", (page: AnalystCallPage) => {
      page.page.totalPages = 2;
    }],
    ["last marker", (page: AnalystCallPage) => {
      page.page.last = false;
    }],
    ["truncated items", (page: AnalystCallPage) => {
      page.items = [];
    }],
  ])("rejects a mismatched fixed page %s", async (_label, mutate) => {
    const { metadata, page } = await fixtureInputs();
    mutate(page);

    await expect(
      new FixtureSp500HistoryProvider(callsPort(metadata, page)).history(),
    ).rejects.toThrow(/fixed page contract/i);
  });

  it.each([
    ["missing facet", (metadata: CallsMetadata) => {
      metadata.facets.assets = metadata.facets.assets.filter(({ assetId }) => assetId !== "asset-spx");
    }],
    ["duplicate facet", (metadata: CallsMetadata) => {
      metadata.facets.assets.push(structuredClone(
        metadata.facets.assets.find(({ assetId }) => assetId === "asset-spx")!,
      ));
    }],
  ])("rejects an invalid asset-spx metadata %s", async (_label, mutate) => {
    const { metadata, page } = await fixtureInputs();
    mutate(metadata);

    await expect(
      new FixtureSp500HistoryProvider(callsPort(metadata, page)).history(),
    ).rejects.toThrow(/one asset-spx facet/i);
  });

  it("preserves future canonical display fields supplied by the calls provider", async () => {
    const { metadata, page } = await fixtureInputs();
    const facet = metadata.facets.assets.find(({ assetId }) => assetId === "asset-spx")!;
    facet.assetType = "ETF";
    facet.canonicalName = "Renamed canonical SPX display";
    facet.ticker = null;
    page.items[0].asset = structuredClone(facet);

    const snapshot = await new FixtureSp500HistoryProvider(callsPort(metadata, page)).history();

    expect(snapshot.asset).toEqual(facet);
    expect(snapshot.items[0].asset).toEqual(facet);
  });

  it.each([
    ["call to asset", (page: AnalystCallPage) => {
      page.items[0].call.assetId = "asset-nvda";
    }],
    ["view to asset facet", (page: AnalystCallPage) => {
      page.items[0].asset.ticker = "SPY";
    }],
    ["call to institution", (page: AnalystCallPage) => {
      page.items[0].call.institutionId = "inst-other";
    }],
    ["call to analyst", (page: AnalystCallPage) => {
      page.items[0].call.analystId = null;
    }],
    ["call to source reference", (page: AnalystCallPage) => {
      page.items[0].call.sourceReferenceId = "source-ref-other";
    }],
    ["reference to source document", (page: AnalystCallPage) => {
      page.items[0].source.reference.sourceDocumentId = "source-document-other";
    }],
  ])("rejects an inconsistent canonical %s join", async (_label, mutate) => {
    const { metadata, page } = await fixtureInputs();
    mutate(page);

    await expect(
      new FixtureSp500HistoryProvider(callsPort(metadata, page)).history(),
    ).rejects.toThrow(/inconsistent canonical joins/i);
  });

  it.each([
    ["metadata", (metadata: CallsMetadata, page: AnalystCallPage) => {
      void page;
      metadata.dataMode = "EOD";
    }],
    ["call", (_metadata: CallsMetadata, page: AnalystCallPage) => {
      page.items[0].call.dataMode = "EOD";
    }],
    ["document", (_metadata: CallsMetadata, page: AnalystCallPage) => {
      page.items[0].source.document.dataMode = "EOD";
    }],
    ["reference", (_metadata: CallsMetadata, page: AnalystCallPage) => {
      page.items[0].source.reference.dataMode = "EOD";
    }],
  ])("rejects mixed DEMO mode in %s", async (_label, mutate) => {
    const { metadata, page } = await fixtureInputs();
    mutate(metadata, page);

    await expect(
      new FixtureSp500HistoryProvider(callsPort(metadata, page)).history(),
    ).rejects.toThrow(/DEMO/i);
  });

  it("rejects duplicate calls, divergent provenance, and invalid chronology", async () => {
    const duplicate = await fixtureInputs();
    duplicate.page.items.push(duplicate.page.items[0]);
    setPageCount(duplicate.page, 2);

    const provenance = await fixtureInputs();
    provenance.page.items[0].source.document.provenanceId = "another-source";

    const chronology = await fixtureInputs();
    chronology.page.items[0].call.processingTime = "2026-08-10T11:59:59Z";

    await expect(
      new FixtureSp500HistoryProvider(callsPort(duplicate.metadata, duplicate.page)).history(),
    ).rejects.toThrow(/duplicate call/i);
    await expect(
      new FixtureSp500HistoryProvider(callsPort(provenance.metadata, provenance.page)).history(),
    ).rejects.toThrow(/inconsistent provenance/i);
    await expect(
      new FixtureSp500HistoryProvider(callsPort(chronology.metadata, chronology.page)).history(),
    ).rejects.toThrow(/invalid evidence chronology/i);
  });

  it("rejects source capture after the call or a call captured after the catalog cutoff", async () => {
    const document = await fixtureInputs();
    document.page.items[0].source.document.capturedAt = "2026-08-10T12:03:00.000001Z";
    const reference = await fixtureInputs();
    reference.page.items[0].source.reference.capturedAt = "2026-08-10T12:03:00.000001Z";
    const catalog = await fixtureInputs();
    catalog.metadata.asOf = "2026-08-10T12:02:59.999999Z";

    for (const input of [document, reference, catalog]) {
      await expect(
        new FixtureSp500HistoryProvider(callsPort(input.metadata, input.page)).history(),
      ).rejects.toThrow(/invalid evidence chronology/i);
    }
  });

  it.each([
    "2026-08-10T12:00:00+00:00",
    "2026-08-10T12:00:00.0000001Z",
  ])("rejects a noncanonical event instant %s", async (eventTime) => {
    const { metadata, page } = await fixtureInputs();
    page.items[0].call.eventTime = eventTime;

    await expect(
      new FixtureSp500HistoryProvider(callsPort(metadata, page)).history(),
    ).rejects.toThrow(/invalid UTC instant/i);
  });

  it("propagates metadata and list failures without returning fallback rows", async () => {
    const { metadata, page } = await fixtureInputs();
    const metadataFailure = callsPort(metadata, page);
    metadataFailure.metadata = async () => {
      throw new Error("metadata unavailable");
    };
    const listFailure = callsPort(metadata, page);
    listFailure.list = async () => {
      throw new Error("SPX calls unavailable");
    };

    await expect(new FixtureSp500HistoryProvider(metadataFailure).history())
      .rejects.toThrow("metadata unavailable");
    await expect(new FixtureSp500HistoryProvider(listFailure).history())
      .rejects.toThrow("SPX calls unavailable");
  });
});
