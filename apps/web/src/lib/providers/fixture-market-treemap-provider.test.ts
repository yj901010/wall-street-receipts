import nasdaq100Fixture from "../../../../../fixtures/v1/market-treemap-nasdaq100.json";
import sp500Fixture from "../../../../../fixtures/v1/market-treemap-sp500.json";
import { describe, expect, it } from "vitest";
import {
  FixtureMarketTreemapProvider,
  mapMarketTreemapFixtureDocument,
  mapMarketTreemapFixtureDocuments,
} from "./fixture-market-treemap-provider";

function sp500Document() {
  return structuredClone(sp500Fixture);
}

type Sp500Document = ReturnType<typeof sp500Document>;

describe("FixtureMarketTreemapProvider", () => {
  const provider = new FixtureMarketTreemapProvider();

  it("maps the exact S&P 500 nested DEMO evidence without deriving price change or size", async () => {
    const snapshot = await provider.findByUniverse("sp500");

    expect(snapshot).toMatchObject({
      schemaVersion: "1.0.0",
      fixtureVersion: "v1",
      dataMode: "DEMO",
      generatedAt: "2026-08-19T01:00:00Z",
      universe: "sp500",
      mode: "PRICE_CHANGE",
      asOf: "2026-08-19T00:30:00Z",
      provenance: {
        id: "fixture-market-treemap-sp500-v1",
        sourceType: "LOCAL_SPECIFICATION",
        capturedAt: "2026-08-19T01:00:00Z",
        synthetic: true,
        licenseClass: "INTERNAL_DEMO",
      },
      metric: {
        name: "priceChangePercent",
        unit: "percent",
        scaleMinimum: -5,
        scaleMaximum: 5,
        missingDisplay: "NA",
      },
      geometry: {
        type: "NESTED_TREEMAP",
        groupBy: ["sector", "industry"],
        unclassifiedDisplay: "Unclassified",
        areaField: "syntheticMarketCapProxy",
        areaUnit: "relative",
      },
      coverage: {
        kind: "SAMPLE",
        completeUniverse: false,
        cellCount: 3,
        weightBasis: "SYNTHETIC_MARKET_CAP_PROXY",
      },
    });
    expect(snapshot.cells.map(({ ticker }) => ticker)).toEqual(["NVDA", "MSFT", "AAPL"]);
    expect(snapshot.cells.map(({ industry }) => industry)).toEqual([
      "Semiconductors",
      "Software",
      "Consumer Electronics",
    ]);
    expect(snapshot.cells.map(({ syntheticMarketCapProxy }) => syntheticMarketCapProxy)).toEqual([
      144,
      121,
      100,
    ]);
    expect(snapshot.cells.map(({ priceChangePercent }) => priceChangePercent)).toEqual([
      1.25,
      -0.75,
      null,
    ]);
    expect(snapshot.disclaimer).toBe(sp500Fixture.disclaimer);
  });

  it("keeps equal cross-universe evidence under distinct canonical provenance", async () => {
    const [sp500, nasdaq100] = await Promise.all([
      provider.findByUniverse("sp500"),
      provider.findByUniverse("nasdaq100"),
    ]);

    expect(nasdaq100.provenance.id).toBe("fixture-market-treemap-nasdaq100-v1");
    expect(nasdaq100.provenance.id).not.toBe(sp500.provenance.id);
    expect(nasdaq100.cells.map((cell) => ({ ...cell, provenanceId: "universe-specific" }))).toEqual(
      sp500.cells.map((cell) => ({ ...cell, provenanceId: "universe-specific" })),
    );
  });

  it.each<[string, (document: Sp500Document) => void, RegExp]>([
    [
      "a malformed cell shape",
      (document) => Reflect.deleteProperty(document.cells[0], "industry"),
      /closed record shape/i,
    ],
    [
      "an unsupported zero proxy",
      (document) => { document.cells[0].syntheticMarketCapProxy = 0; },
      /invalid synthetic market-cap proxy/i,
    ],
    [
      "a fractional proxy",
      (document) => { document.cells[0].syntheticMarketCapProxy = 1.5; },
      /invalid synthetic market-cap proxy/i,
    ],
    [
      "a stored price change below the safety bound",
      (document) => { document.cells[0].priceChangePercent = -100.01; },
      /out-of-range price-change metric/i,
    ],
    [
      "a stored price change above the safety bound",
      (document) => { document.cells[0].priceChangePercent = 1_000_000.01; },
      /out-of-range price-change metric/i,
    ],
    [
      "a non-finite stored price change",
      (document) => { document.cells[0].priceChangePercent = Number.NaN; },
      /out-of-range price-change metric/i,
    ],
    [
      "an overlong classification",
      (document) => { document.cells[0].industry = "x".repeat(129); },
      /invalid industry/i,
    ],
    [
      "the reserved unclassified display literal",
      (document) => { document.cells[0].sector = "Unclassified"; },
      /invalid sector/i,
    ],
    [
      "an industry without a sector",
      (document) => { Reflect.set(document.cells[0], "sector", null); },
      /cannot classify an industry without a sector/i,
    ],
    [
      "a future-dated cell",
      (document) => { document.cells[0].timestamp = "2026-08-19T00:30:00.000001Z"; },
      /after the treemap as-of/i,
    ],
    [
      "a mismatched cell provenance",
      (document) => { document.cells[0].provenanceId = "fixture-other"; },
      /inconsistent DEMO provenance/i,
    ],
    [
      "an invalid geometry definition",
      (document) => { document.geometry.groupBy = ["industry", "sector"]; },
      /unsupported geometry definition/i,
    ],
    [
      "an inconsistent coverage count",
      (document) => { document.coverage.cellCount = 2; },
      /inconsistent sample coverage/i,
    ],
    [
      "an invalid provenance source path",
      (document) => { document.provenance.sourcePaths[0] = "x".repeat(257); },
      /inconsistent DEMO provenance/i,
    ],
    [
      "an overlong disclaimer",
      (document) => { document.disclaimer = "x".repeat(769); },
      /invalid disclaimer/i,
    ],
  ])("rejects %s", (_label, mutate, error) => {
    const document = sp500Document();
    mutate(document);
    expect(() => mapMarketTreemapFixtureDocument(document)).toThrow(error);
  });

  it("accepts finite stored values outside the visualization scale without clamping evidence", () => {
    const document = sp500Document();
    document.cells[0].priceChangePercent = -7.25;
    document.cells[1].priceChangePercent = 12.5;

    expect(mapMarketTreemapFixtureDocument(document).cells.map(({ priceChangePercent }) => priceChangePercent))
      .toEqual([-7.25, 12.5, null]);
  });

  it("accepts both exact raw metric safety bounds", () => {
    const document = sp500Document();
    document.cells[0].priceChangePercent = -100;
    document.cells[1].priceChangePercent = 1_000_000;

    expect(mapMarketTreemapFixtureDocument(document).cells.map(({ priceChangePercent }) => priceChangePercent))
      .toEqual([-100, 1_000_000, null]);
  });

  it("accepts exact proxy bounds while preserving canonical hierarchy order", () => {
    const document = sp500Document();
    document.cells[0].syntheticMarketCapProxy = 1_000_000_000_000;
    document.cells[1].syntheticMarketCapProxy = 2;
    document.cells[2].syntheticMarketCapProxy = 1;

    expect(
      mapMarketTreemapFixtureDocument(document).cells.map(
        ({ syntheticMarketCapProxy }) => syntheticMarketCapProxy,
      ),
    ).toEqual([1_000_000_000_000, 2, 1]);
  });

  it("accepts an explicit known-empty sample without manufacturing cells", () => {
    const document = sp500Document();
    document.cells = [];
    document.coverage.cellCount = 0;

    expect(mapMarketTreemapFixtureDocument(document).cells).toEqual([]);
  });

  it("rejects duplicate cells and incomplete or duplicate registries", () => {
    const duplicateCell = sp500Document();
    duplicateCell.cells.push(structuredClone(duplicateCell.cells[0]));
    duplicateCell.coverage.cellCount = duplicateCell.cells.length;
    expect(() => mapMarketTreemapFixtureDocument(duplicateCell)).toThrow(/duplicate cell identity/i);

    expect(() => mapMarketTreemapFixtureDocuments([sp500Document()])).toThrow(/exactly two universes/i);
    expect(() => mapMarketTreemapFixtureDocuments([sp500Document(), sp500Document()])).toThrow(
      /duplicate universe/i,
    );
  });

  it("rejects excessive cells and non-canonical hierarchy order", () => {
    const excessive = sp500Document();
    excessive.cells = Array.from({ length: 1_001 }, (_, index) => ({
      ...structuredClone(excessive.cells[0]),
      assetId: `asset-${index}`,
      ticker: `T${index}`,
    }));
    excessive.coverage.cellCount = excessive.cells.length;
    expect(() => mapMarketTreemapFixtureDocument(excessive)).toThrow(/supported cell count/i);

    const reordered = sp500Document();
    reordered.cells.reverse();
    expect(() => mapMarketTreemapFixtureDocument(reordered)).toThrow(/canonical hierarchy order/i);
  });

  it("allows overlapping assets but rejects a reused provenance identity across universes", () => {
    expect(() => mapMarketTreemapFixtureDocuments([sp500Fixture, nasdaq100Fixture])).not.toThrow();

    const duplicateProvenance = structuredClone(nasdaq100Fixture);
    duplicateProvenance.provenance.id = sp500Fixture.provenance.id;
    for (const cell of duplicateProvenance.cells) {
      cell.provenanceId = duplicateProvenance.provenance.id;
    }
    expect(() => mapMarketTreemapFixtureDocuments([sp500Fixture, duplicateProvenance])).toThrow(
      /duplicate provenance identity/i,
    );
  });

  it("rejects divergent shared-cell evidence at the same as-of instant", () => {
    const divergent = structuredClone(nasdaq100Fixture);
    divergent.cells[0].priceChangePercent = 1.5;

    expect(() => mapMarketTreemapFixtureDocuments([sp500Fixture, divergent])).toThrow(
      /divergent shared-cell evidence/i,
    );
  });
});
