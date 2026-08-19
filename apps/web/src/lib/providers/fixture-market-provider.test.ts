import { describe, expect, it, vi } from "vitest";
import type { CallsProvider } from "./calls-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";
import { FixtureMarketProvider } from "./fixture-market-provider";
import { FixtureMarketTreemapProvider } from "./fixture-market-treemap-provider";
import type {
  MarketTreemapProvider,
  MarketTreemapSnapshot,
  MarketTreemapUniverse,
} from "./market-treemap-provider";

function callsPort(overrides: Partial<CallsProvider> = {}): CallsProvider {
  const delegate = new FixtureCallsProvider();

  return {
    list: (query) => delegate.list(query),
    metadata: () => delegate.metadata(),
    findById: (id) => delegate.findById(id),
    findContextByCallId: (id) => delegate.findContextByCallId(id),
    ...overrides,
  };
}

function treemapPort(
  mutate?: (snapshot: MarketTreemapSnapshot, universe: MarketTreemapUniverse) => void,
): MarketTreemapProvider {
  const delegate = new FixtureMarketTreemapProvider();

  return {
    async findByUniverse(universe) {
      const snapshot = structuredClone(await delegate.findByUniverse(universe));
      mutate?.(snapshot, universe);
      return snapshot;
    },
  };
}

describe("FixtureMarketProvider dashboard composition", () => {
  it("composes canonical calls and both independently sourced PRICE_CHANGE previews", async () => {
    const delegate = new FixtureCallsProvider();
    const treemapDelegate = new FixtureMarketTreemapProvider();
    const list = vi.fn((query) => delegate.list(query));
    const provider = new FixtureMarketProvider(callsPort({ list }), treemapPort());
    const snapshot = await provider.dashboard();

    expect(list).toHaveBeenCalledWith({
      page: 0,
      size: 3,
      sort: "eventTime",
      order: "desc",
    });
    expect(Object.keys(snapshot)).toEqual([
      "dataMode",
      "latestCalls",
      "mapPreviews",
      "marketBoard",
      "eventCalendar",
      "ranking",
    ]);
    expect(snapshot).not.toHaveProperty("asOf");
    expect(snapshot).not.toHaveProperty("source");
    expect(snapshot.dataMode).toBe("DEMO");
    expect(snapshot.latestCalls.items.map(({ call }) => call.callId)).toEqual([
      "demo-call-002",
      "demo-call-001",
      "demo-call-003",
    ]);
    expect(snapshot.latestCalls.items).toEqual((await delegate.list({
      page: 0,
      size: 3,
      sort: "eventTime",
      order: "desc",
    })).items);
    expect(snapshot.latestCalls).toMatchObject({
      asOf: "2026-08-18T00:00:00Z",
      dataMode: "DEMO",
      source: "fixture-analyst-calls-v1",
    });
    expect(snapshot.mapPreviews.map(({ universe, mode, provenance }) => ({
      universe,
      mode,
      provenanceId: provenance.id,
    }))).toEqual([
      {
        universe: "sp500",
        mode: "PRICE_CHANGE",
        provenanceId: "fixture-market-treemap-sp500-v1",
      },
      {
        universe: "nasdaq100",
        mode: "PRICE_CHANGE",
        provenanceId: "fixture-market-treemap-nasdaq100-v1",
      },
    ]);
    expect(snapshot.mapPreviews).toEqual(await Promise.all([
      treemapDelegate.findByUniverse("sp500"),
      treemapDelegate.findByUniverse("nasdaq100"),
    ]));
    expect(snapshot.marketBoard).toEqual({ status: "NOT_PUBLISHED", missingDisplay: "NA" });
    expect(snapshot.eventCalendar).toEqual({ status: "NOT_PUBLISHED", missingDisplay: "NA" });
    expect(snapshot.ranking).toEqual({ status: "P3_DEFERRED", missingDisplay: "NA" });
    expect(Object.keys(snapshot.ranking)).toEqual(["status", "missingDisplay"]);
  });

  it("requires the calls fixture to be exact DEMO evidence", async () => {
    const delegate = new FixtureCallsProvider();
    const calls = callsPort({
      async metadata() {
        return { ...(await delegate.metadata()), dataMode: "EOD" };
      },
    });

    await expect(new FixtureMarketProvider(calls, treemapPort()).dashboard()).rejects.toThrow(
      /requires DEMO data mode/i,
    );
  });

  it.each([
    ["call", (page: Awaited<ReturnType<CallsProvider["list"]>>) => {
      page.items[0].call.dataMode = "EOD";
    }],
    ["source document", (page: Awaited<ReturnType<CallsProvider["list"]>>) => {
      page.items[0].source.document.dataMode = "EOD";
    }],
    ["source reference", (page: Awaited<ReturnType<CallsProvider["list"]>>) => {
      page.items[0].source.reference.dataMode = "EOD";
    }],
  ] as const)("rejects a selected %s with mixed data mode", async (_label, mutate) => {
    const delegate = new FixtureCallsProvider();
    const calls = callsPort({
      async list(query) {
        const page = structuredClone(await delegate.list(query));
        mutate(page);
        return page;
      },
    });

    await expect(new FixtureMarketProvider(calls, treemapPort()).dashboard()).rejects.toThrow(
      /calls have inconsistent data mode/i,
    );
  });

  it("rejects a calls page that does not honor the requested page contract", async () => {
    const delegate = new FixtureCallsProvider();
    const calls = callsPort({
      async list(query) {
        const page = await delegate.list(query);
        return { ...page, page: { ...page.page, size: 25 } };
      },
    });

    await expect(new FixtureMarketProvider(calls, treemapPort()).dashboard()).rejects.toThrow(
      /requested page contract/i,
    );
  });

  it("rejects reordered or duplicated calls without hard-coding identities", async () => {
    const delegate = new FixtureCallsProvider();
    const reordered = callsPort({
      async list(query) {
        const page = await delegate.list(query);
        return { ...page, items: [page.items[1], page.items[0], page.items[2]] };
      },
    });
    const duplicated = callsPort({
      async list(query) {
        const page = await delegate.list(query);
        return { ...page, items: [page.items[0], page.items[0], page.items[2]] };
      },
    });

    await expect(new FixtureMarketProvider(reordered, treemapPort()).dashboard()).rejects.toThrow(
      /not ordered/i,
    );
    await expect(new FixtureMarketProvider(duplicated, treemapPort()).dashboard()).rejects.toThrow(
      /duplicate call/i,
    );
  });

  it("preserves microsecond precision when validating call order", async () => {
    const delegate = new FixtureCallsProvider();
    const calls = callsPort({
      async list(query) {
        const page = structuredClone(await delegate.list(query));
        page.items[0].call.eventTime = "2026-08-11T14:20:00.000001Z";
        page.items[1].call.eventTime = "2026-08-11T14:20:00.000002Z";
        return page;
      },
    });

    await expect(new FixtureMarketProvider(calls, treemapPort()).dashboard()).rejects.toThrow(
      /not ordered/i,
    );
  });

  it("requires call ID ascending as the equal-event-time tie break", async () => {
    const delegate = new FixtureCallsProvider();
    const descendingIds = callsPort({
      async list(query) {
        const page = structuredClone(await delegate.list(query));
        page.items[0].call.eventTime = "2026-08-11T14:20:00.000001Z";
        page.items[1].call.eventTime = "2026-08-11T14:20:00.000001Z";
        return page;
      },
    });
    const ascendingIds = callsPort({
      async list(query) {
        const page = structuredClone(await delegate.list(query));
        page.items[0].call.eventTime = "2026-08-11T14:20:00.000001Z";
        page.items[1].call.eventTime = "2026-08-11T14:20:00.000001Z";
        page.items = [page.items[1], page.items[0], page.items[2]];
        return page;
      },
    });

    await expect(
      new FixtureMarketProvider(descendingIds, treemapPort()).dashboard(),
    ).rejects.toThrow(/not ordered/i);
    await expect(
      new FixtureMarketProvider(ascendingIds, treemapPort()).dashboard(),
    ).resolves.toMatchObject({ dataMode: "DEMO" });
  });

  it("rejects empty or count-mismatched map preview coverage", async () => {
    const empty = treemapPort((snapshot, universe) => {
      if (universe === "sp500") {
        snapshot.cells = [];
        snapshot.coverage.cellCount = 0;
      }
    });
    const mismatched = treemapPort((snapshot, universe) => {
      if (universe === "nasdaq100") snapshot.coverage.cellCount += 1;
    });

    await expect(new FixtureMarketProvider(callsPort(), empty).dashboard()).rejects.toThrow(
      /invalid populated SAMPLE coverage/i,
    );
    await expect(new FixtureMarketProvider(callsPort(), mismatched).dashboard()).rejects.toThrow(
      /invalid populated SAMPLE coverage/i,
    );
  });

  it("rejects non-PRICE_CHANGE and mixed-mode map previews", async () => {
    const wrongMode = treemapPort((snapshot, universe) => {
      if (universe === "sp500") {
        (snapshot as unknown as { mode: string }).mode = "ANALYST_CONSENSUS";
      }
    });
    const envelopeMode = treemapPort((snapshot, universe) => {
      if (universe === "sp500") snapshot.dataMode = "EOD";
    });
    const cellMode = treemapPort((snapshot, universe) => {
      if (universe === "nasdaq100") snapshot.cells[0].dataMode = "EOD";
    });

    await expect(new FixtureMarketProvider(callsPort(), wrongMode).dashboard()).rejects.toThrow(
      /not PRICE_CHANGE evidence/i,
    );
    await expect(new FixtureMarketProvider(callsPort(), envelopeMode).dashboard()).rejects.toThrow(
      /inconsistent data mode/i,
    );
    await expect(new FixtureMarketProvider(callsPort(), cellMode).dashboard()).rejects.toThrow(
      /inconsistent data mode/i,
    );
  });

  it("rejects wrong-universe and duplicate document provenance", async () => {
    const wrongUniverse = treemapPort((snapshot, universe) => {
      if (universe === "nasdaq100") snapshot.universe = "sp500";
    });
    const duplicateProvenance = treemapPort((snapshot, universe) => {
      if (universe === "nasdaq100") {
        snapshot.provenance.id = "fixture-market-treemap-sp500-v1";
        snapshot.cells.forEach((cell) => {
          cell.provenanceId = snapshot.provenance.id;
        });
      }
    });

    await expect(new FixtureMarketProvider(callsPort(), wrongUniverse).dashboard()).rejects.toThrow(
      /returned sp500 for nasdaq100/i,
    );
    await expect(
      new FixtureMarketProvider(callsPort(), duplicateProvenance).dashboard(),
    ).rejects.toThrow(/distinct provenance/i);
  });

  it("rejects inconsistent cell provenance and chronology", async () => {
    const provenance = treemapPort((snapshot, universe) => {
      if (universe === "sp500") snapshot.cells[0].provenanceId = "another-source";
    });
    const chronology = treemapPort((snapshot, universe) => {
      if (universe === "sp500") snapshot.provenance.capturedAt = "2026-08-19T02:00:00Z";
    });

    await expect(new FixtureMarketProvider(callsPort(), provenance).dashboard()).rejects.toThrow(
      /inconsistent cell provenance/i,
    );
    await expect(new FixtureMarketProvider(callsPort(), chronology).dashboard()).rejects.toThrow(
      /invalid evidence chronology/i,
    );
  });

  it("rejects sub-millisecond cell evidence after the preview as-of time", async () => {
    const treemaps = treemapPort((snapshot, universe) => {
      if (universe === "sp500") {
        snapshot.asOf = "2026-08-19T00:30:00.000001Z";
        snapshot.cells[0].timestamp = "2026-08-19T00:30:00.000999Z";
      }
    });

    await expect(new FixtureMarketProvider(callsPort(), treemaps).dashboard()).rejects.toThrow(
      /evidence after its as-of time/i,
    );
  });

  it("propagates injected provider failures without returning partial fallback data", async () => {
    const calls = callsPort({
      async list() {
        throw new Error("call provider unavailable");
      },
    });
    const treemaps: MarketTreemapProvider = {
      async findByUniverse() {
        throw new Error("treemap provider unavailable");
      },
    };

    await expect(new FixtureMarketProvider(calls, treemapPort()).dashboard()).rejects.toThrow(
      "call provider unavailable",
    );
    await expect(new FixtureMarketProvider(callsPort(), treemaps).dashboard()).rejects.toThrow(
      "treemap provider unavailable",
    );
  });
});
