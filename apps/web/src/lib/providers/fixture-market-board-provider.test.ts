import marketBoardFixture from "../../../../../fixtures/v1/market-board.json";
import { describe, expect, it } from "vitest";
import {
  FixtureMarketBoardProvider,
  mapMarketBoardFixtureDocument,
} from "./fixture-market-board-provider";

function fixtureDocument() {
  return structuredClone(marketBoardFixture);
}

describe("FixtureMarketBoardProvider", () => {
  it("maps the exact known-unavailable DEMO publication state without quote rows", async () => {
    const snapshot = await new FixtureMarketBoardProvider().snapshot();

    expect(snapshot).toEqual(marketBoardFixture);
    expect(Object.keys(snapshot)).toEqual([
      "schemaVersion",
      "fixtureVersion",
      "dataMode",
      "generatedAt",
      "provenance",
      "scope",
      "publicationStatus",
      "publicationReasonCode",
      "marketAsOf",
      "missingDisplay",
      "quotes",
      "disclaimer",
    ]);
    expect(snapshot).toMatchObject({
      dataMode: "DEMO",
      scope: "GLOBAL_MARKET_OVERVIEW",
      publicationStatus: "NOT_PUBLISHED",
      publicationReasonCode: "NO_CANONICAL_GLOBAL_QUOTE_CATALOG",
      marketAsOf: null,
      missingDisplay: "NA",
      quotes: [],
    });
    expect(snapshot.provenance.sourcePaths).toEqual([
      "schemas/market-board.schema.json",
      "quality/P2_ACCEPTANCE.md",
    ]);
  });

  it("does not return mutable references from the imported fixture", () => {
    const document = fixtureDocument();
    const before = structuredClone(document);
    const snapshot = mapMarketBoardFixtureDocument(document);

    snapshot.provenance.sourcePaths.reverse();

    expect(document).toEqual(before);
    expect(snapshot.provenance.sourcePaths).not.toBe(document.provenance.sourcePaths);
  });

  it("rejects non-object, open root, and open provenance shapes", () => {
    const rootExtra = fixtureDocument();
    const provenanceExtra = fixtureDocument();
    (rootExtra as unknown as Record<string, unknown>).unexpected = true;
    (provenanceExtra.provenance as unknown as Record<string, unknown>).unexpected = true;

    expect(() => mapMarketBoardFixtureDocument(null)).toThrow(/must be an object/i);
    expect(() => mapMarketBoardFixtureDocument(rootExtra)).toThrow(/closed fixture shape/i);
    expect(() => mapMarketBoardFixtureDocument(provenanceExtra)).toThrow(/closed fixture shape/i);
  });

  it.each([
    ["schema version", (document: ReturnType<typeof fixtureDocument>) => {
      document.schemaVersion = "2.0.0";
    }],
    ["fixture version", (document: ReturnType<typeof fixtureDocument>) => {
      document.fixtureVersion = "v2";
    }],
    ["data mode", (document: ReturnType<typeof fixtureDocument>) => {
      document.dataMode = "EOD";
    }],
    ["scope", (document: ReturnType<typeof fixtureDocument>) => {
      document.scope = "ASSET_QUOTES";
    }],
    ["publication status", (document: ReturnType<typeof fixtureDocument>) => {
      document.publicationStatus = "PUBLISHED";
    }],
    ["reason", (document: ReturnType<typeof fixtureDocument>) => {
      document.publicationReasonCode = "UNKNOWN";
    }],
    ["missing display", (document: ReturnType<typeof fixtureDocument>) => {
      document.missingDisplay = "0";
    }],
  ])("rejects an unsupported %s", (_label, mutate) => {
    const document = fixtureDocument();
    mutate(document);

    expect(() => mapMarketBoardFixtureDocument(document)).toThrow(
      /supported known-unavailable publication state/i,
    );
  });

  it("requires null marketAsOf and an exactly empty quote collection", () => {
    const marketAsOf = fixtureDocument();
    const quotes = fixtureDocument();
    (marketAsOf as unknown as { marketAsOf: string | null }).marketAsOf =
      "2026-08-19T02:00:00Z";
    (quotes.quotes as unknown[]).push({ symbol: "SPX", price: 5278.52 });

    expect(() => mapMarketBoardFixtureDocument(marketAsOf)).toThrow(
      /known-unavailable publication state/i,
    );
    expect(() => mapMarketBoardFixtureDocument(quotes)).toThrow(
      /known-unavailable publication state/i,
    );
  });

  it.each([
    ["identifier", (document: ReturnType<typeof fixtureDocument>) => {
      document.provenance.id = "Fixture-Market-Board-V1";
    }],
    ["source type", (document: ReturnType<typeof fixtureDocument>) => {
      document.provenance.sourceType = "MARKET_DATA_PROVIDER";
    }],
    ["synthetic marker", (document: ReturnType<typeof fixtureDocument>) => {
      document.provenance.synthetic = false;
    }],
    ["license", (document: ReturnType<typeof fixtureDocument>) => {
      document.provenance.licenseClass = "PUBLIC";
    }],
  ])("rejects invalid provenance %s", (_label, mutate) => {
    const document = fixtureDocument();
    mutate(document);

    expect(() => mapMarketBoardFixtureDocument(document)).toThrow(/provenance/i);
  });

  it("rejects a grammar-valid but noncanonical provenance identity", () => {
    const document = fixtureDocument();
    document.provenance.id = "fixture-market-board-v2";

    expect(() => mapMarketBoardFixtureDocument(document)).toThrow(/DEMO provenance/i);
  });

  it("requires the exact ordered, trimmed source paths", () => {
    const reversed = fixtureDocument();
    const whitespace = fixtureDocument();
    reversed.provenance.sourcePaths.reverse();
    whitespace.provenance.sourcePaths[0] = ` ${whitespace.provenance.sourcePaths[0]}`;

    expect(() => mapMarketBoardFixtureDocument(reversed)).toThrow(/source path/i);
    expect(() => mapMarketBoardFixtureDocument(whitespace)).toThrow(/source path/i);
  });

  it("validates canonical UTC instants and microsecond chronology", () => {
    const invalid = fixtureDocument();
    invalid.generatedAt = "2026-02-29T02:00:00Z";

    const capturedAfterGeneration = fixtureDocument();
    capturedAfterGeneration.generatedAt = "2026-08-19T02:00:00.000001Z";
    capturedAfterGeneration.provenance.capturedAt = "2026-08-19T02:00:00.000002Z";

    expect(() => mapMarketBoardFixtureDocument(invalid)).toThrow(/invalid UTC instant/i);
    expect(() => mapMarketBoardFixtureDocument(capturedAfterGeneration)).toThrow(
      /captured after document generation/i,
    );
  });

  it("accepts equal canonical microsecond policy timestamps without creating market freshness", () => {
    const document = fixtureDocument();
    document.generatedAt = "2026-08-19T02:00:00.000001Z";
    document.provenance.capturedAt = "2026-08-19T02:00:00.000001Z";

    const snapshot = mapMarketBoardFixtureDocument(document);

    expect(snapshot.generatedAt).toBe("2026-08-19T02:00:00.000001Z");
    expect(snapshot.provenance.capturedAt).toBe("2026-08-19T02:00:00.000001Z");
    expect(snapshot.marketAsOf).toBeNull();
  });

  it.each(["", " leading", "trailing ", "x".repeat(769)])(
    "rejects an invalid disclaimer %j",
    (disclaimer) => {
      const document = fixtureDocument();
      document.disclaimer = disclaimer;

      expect(() => mapMarketBoardFixtureDocument(document)).toThrow(/invalid disclaimer/i);
    },
  );

  it("rejects a trimmed but weakened disclaimer", () => {
    const document = fixtureDocument();
    document.disclaimer = "Known-unavailable DEMO publication state only. Not investment advice.";

    expect(() => mapMarketBoardFixtureDocument(document)).toThrow(
      /supported known-unavailable publication state/i,
    );
  });
});
