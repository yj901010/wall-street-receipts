import nasdaq100Fixture from "../../../../../fixtures/v1/market-map-nasdaq100.json";
import sp500Fixture from "../../../../../fixtures/v1/market-map.json";
import { describe, expect, it } from "vitest";
import {
  FixtureMarketMapProvider,
  mapMarketMapFixtureDocument,
  mapMarketMapFixtureDocuments,
} from "./fixture-market-map-provider";

function sp500Document() {
  return structuredClone(sp500Fixture);
}

type Sp500Document = ReturnType<typeof sp500Document>;

describe("FixtureMarketMapProvider", () => {
  const provider = new FixtureMarketMapProvider();

  it("maps the limited S&P 500 sample without deriving or reordering cell evidence", async () => {
    const snapshot = await provider.findByUniverse("sp500");

    expect(snapshot).toMatchObject({
      schemaVersion: "1.0.0",
      fixtureVersion: "v1",
      universe: "sp500",
      mode: "ANALYST_CONSENSUS",
      asOf: "2026-08-12T06:00:00Z",
      capturedAt: "2026-08-19T00:00:00Z",
      generatedAt: "2026-08-19T00:00:00Z",
      dataMode: "DEMO",
      source: "fixture-market-map-v1",
      coverage: {
        kind: "SAMPLE",
        completeUniverse: false,
        cellCount: 3,
        weightBasis: "SYNTHETIC_RELATIVE",
      },
      metric: {
        name: "analystConsensus",
        unit: "score",
        minimum: -1,
        maximum: 1,
        missingDisplay: "NA",
      },
    });
    expect(snapshot.cells.map(({ ticker }) => ticker)).toEqual(["NVDA", "MSFT", "AAPL"]);
    expect(snapshot.cells.map(({ weight }) => weight)).toEqual([8.1, 7, 6.5]);
    expect(snapshot.cells.map(({ metric }) => metric)).toEqual([0.82, 0.71, null]);
    expect(snapshot.cells.map(({ callCount }) => callCount)).toEqual([18, 14, 0]);
    expect(snapshot.disclaimer).toMatch(
      /sector labels, weights, analyst-consensus metrics, and call counts are synthetic fixture values/i,
    );
  });

  it("returns the canonical Nasdaq 100 known-empty evidence instead of substituting S&P cells", async () => {
    const snapshot = await provider.findByUniverse("nasdaq100");

    expect(snapshot).toMatchObject({
      universe: "nasdaq100",
      source: "fixture-market-map-nasdaq100-v1",
      dataMode: "DEMO",
      coverage: {
        kind: "SAMPLE",
        completeUniverse: false,
        cellCount: 0,
        weightBasis: "SYNTHETIC_RELATIVE",
      },
      metric: {
        name: "analystConsensus",
        unit: "score",
        minimum: -1,
        maximum: 1,
        missingDisplay: "NA",
      },
      cells: [],
    });
    expect(snapshot.disclaimer).toMatch(/known-empty Nasdaq 100 DEMO SAMPLE/i);
  });

  it.each<[
    string,
    (document: Sp500Document) => void,
    RegExp,
  ]>([
    [
      "a malformed cell",
      (document) => Reflect.deleteProperty(document.cells[0], "metric"),
      /closed record shape/i,
    ],
    [
      "an inconsistent coverage count",
      (document) => {
        document.coverage.cellCount = 4;
      },
      /inconsistent sample coverage/i,
    ],
    [
      "an invalid synthetic weight",
      (document) => {
        document.cells[0].weight = 0;
      },
      /invalid synthetic relative weight/i,
    ],
    [
      "an out-of-range metric",
      (document) => {
        document.cells[0].metric = 1.01;
      },
      /out-of-range metric/i,
    ],
    [
      "an invalid call count",
      (document) => {
        document.cells[0].callCount = -1;
      },
      /invalid call count/i,
    ],
    [
      "a future-dated cell",
      (document) => {
        document.cells[0].timestamp = "2026-08-12T06:00:00.000001Z";
      },
      /after the map as-of instant/i,
    ],
    [
      "a mismatched provenance identity",
      (document) => {
        document.cells[0].provenanceId = "fixture-other-map";
      },
      /inconsistent DEMO provenance/i,
    ],
    [
      "an invalid provenance identifier",
      (document) => {
        document.provenance.id = "invalid source id";
      },
      /inconsistent DEMO provenance/i,
    ],
    [
      "an overlong provenance source path",
      (document) => {
        document.provenance.sourcePaths[0] = "x".repeat(257);
      },
      /inconsistent DEMO provenance/i,
    ],
    [
      "an overlong sector",
      (document) => {
        document.cells[0].sector = "x".repeat(129);
      },
      /invalid sector/i,
    ],
    [
      "an overlong disclaimer",
      (document) => {
        document.disclaimer = "x".repeat(513);
      },
      /invalid disclaimer/i,
    ],
  ])("rejects %s", (_label, mutate, expectedError) => {
    const document = sp500Document();
    mutate(document);

    expect(() => mapMarketMapFixtureDocument(document)).toThrow(expectedError);
  });

  it("rejects duplicate cells and incomplete or duplicate universe registries", () => {
    const duplicateCellDocument = sp500Document();
    duplicateCellDocument.cells.push(structuredClone(duplicateCellDocument.cells[0]));
    duplicateCellDocument.coverage.cellCount = duplicateCellDocument.cells.length;

    expect(() => mapMarketMapFixtureDocument(duplicateCellDocument)).toThrow(/duplicate cell identity/i);
    expect(() => mapMarketMapFixtureDocuments([sp500Document()])).toThrow(/missing the nasdaq100 universe/i);
    expect(() => mapMarketMapFixtureDocuments([sp500Document(), sp500Document()])).toThrow(
      /duplicate universe/i,
    );
  });

  it("allows the same asset identity in different universe documents", () => {
    const overlap = sp500Document();
    overlap.universe = "nasdaq100";
    overlap.provenance.id = "fixture-market-map-overlap-v1";
    for (const cell of overlap.cells) {
      cell.provenanceId = overlap.provenance.id;
    }

    expect(() => mapMarketMapFixtureDocuments([sp500Document(), overlap])).not.toThrow();
  });

  it("rejects a reused provenance identity across otherwise distinct universes", () => {
    const duplicateProvenance = sp500Document();
    duplicateProvenance.universe = "nasdaq100";

    expect(() => mapMarketMapFixtureDocuments([sp500Document(), duplicateProvenance])).toThrow(
      /duplicate provenance identity/i,
    );
  });

  it("maps both committed fixture documents into the exact supported registry", () => {
    expect(
      mapMarketMapFixtureDocuments([sp500Fixture, nasdaq100Fixture]).map(({ universe }) => universe),
    ).toEqual(["sp500", "nasdaq100"]);
  });
});
