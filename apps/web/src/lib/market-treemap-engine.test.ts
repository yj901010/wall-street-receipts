import sp500Fixture from "../../../../fixtures/v1/market-treemap-sp500.json";
import { describe, expect, it } from "vitest";
import { mapMarketTreemapFixtureDocument } from "./providers/fixture-market-treemap-provider";
import type { MarketTreemapCell } from "./providers/market-treemap-provider";
import {
  buildMarketTreemapHierarchy,
  layoutMarketTreemap,
  MARKET_TREEMAP_CANVAS,
  marketTreemapLabelDensity,
  presentMarketTreemapCell,
} from "./market-treemap-engine";

const snapshot = mapMarketTreemapFixtureDocument(sp500Fixture);
const metric = snapshot.metric;

function cell(priceChangePercent: number | null): MarketTreemapCell {
  return { ...snapshot.cells[0], priceChangePercent };
}

function area(rect: { width: number; height: number }): number {
  return rect.width * rect.height;
}

describe("presentMarketTreemapCell", () => {
  it("keeps null unavailable and visually neutral instead of coercing it to zero", () => {
    expect(presentMarketTreemapCell(cell(null), metric)).toEqual({
      metricDisplay: "NA",
      metricTone: "unavailable",
      backgroundColor: "#303841",
      paletteStrength: 0,
    });
  });

  it("displays exact out-of-scale values while clamping only the color palette", () => {
    expect(presentMarketTreemapCell(cell(-7.25), metric)).toEqual({
      metricDisplay: "-7.25%",
      metricTone: "negative",
      backgroundColor: "rgb(138, 52, 56)",
      paletteStrength: 1,
    });
    expect(presentMarketTreemapCell(cell(12.5), metric)).toEqual({
      metricDisplay: "+12.5%",
      metricTone: "positive",
      backgroundColor: "rgb(33, 107, 80)",
      paletteStrength: 1,
    });
  });

  it("uses a continuous sign-aware palette within the declared visualization scale", () => {
    expect(presentMarketTreemapCell(cell(-2.5), metric)).toMatchObject({
      metricDisplay: "-2.5%",
      metricTone: "negative",
      paletteStrength: 0.5,
    });
    expect(presentMarketTreemapCell(cell(2.5), metric)).toMatchObject({
      metricDisplay: "+2.5%",
      metricTone: "positive",
      paletteStrength: 0.5,
    });
    expect(presentMarketTreemapCell(cell(-0), metric)).toMatchObject({
      metricDisplay: "0%",
      metricTone: "neutral",
      paletteStrength: 0,
    });
  });
});

describe("market treemap hierarchy", () => {
  it("groups the canonical evidence sector → industry → ticker without changing proxy ratios", () => {
    const hierarchy = buildMarketTreemapHierarchy(snapshot);
    expect(hierarchy).toHaveLength(1);
    expect(hierarchy[0].value).toEqual({ kind: "sector", label: "Technology" });
    expect("children" in hierarchy[0] && hierarchy[0].children).toHaveLength(3);

    const layout = layoutMarketTreemap(snapshot);
    const industries = layout[0].children;
    const leaves = industries.flatMap((industry) => industry.children);
    const byTicker = Object.fromEntries(leaves.map((leaf) => [leaf.value.label, leaf]));
    expect(area(byTicker.NVDA.rect) / area(byTicker.MSFT.rect)).toBeCloseTo(144 / 121, 7);
    expect(area(byTicker.MSFT.rect) / area(byTicker.AAPL.rect)).toBeCloseTo(121 / 100, 7);
    expect(leaves.reduce((sum, leaf) => sum + area(leaf.rect), 0)).toBeCloseTo(
      area(MARKET_TREEMAP_CANVAS),
      7,
    );
  });

  it("renders null classifications as the contract's explicit Unclassified label", () => {
    const unclassified = {
      ...snapshot,
      cells: [{ ...snapshot.cells[0], sector: null, industry: null }],
      coverage: { ...snapshot.coverage, cellCount: 1 },
    };
    const hierarchy = buildMarketTreemapHierarchy(unclassified);

    expect(hierarchy[0].value).toEqual({ kind: "sector", label: "Unclassified" });
    expect(
      "children" in hierarchy[0] && hierarchy[0].children[0].value,
    ).toEqual({ kind: "industry", label: "Unclassified", sectorLabel: "Unclassified" });
  });

  it("uses resolved display labels for equal-weight sector and industry tie ordering", () => {
    const tied = {
      ...snapshot,
      cells: [
        {
          ...snapshot.cells[0],
          assetId: "asset-unclassified",
          ticker: "UNC",
          sector: null,
          industry: null,
          syntheticMarketCapProxy: 100,
        },
        {
          ...snapshot.cells[1],
          assetId: "asset-alpha-industry",
          ticker: "ALPI",
          sector: "Alpha",
          industry: "Unicorn",
          syntheticMarketCapProxy: 50,
        },
        {
          ...snapshot.cells[2],
          assetId: "asset-alpha-beta",
          ticker: "ALPB",
          sector: "Alpha",
          industry: "Beta",
          syntheticMarketCapProxy: 50,
        },
      ],
      coverage: { ...snapshot.coverage, cellCount: 3 },
    };

    const layout = layoutMarketTreemap(tied);
    expect(layout.map(({ value }) => value.label)).toEqual(["Alpha", "Unclassified"]);
    expect(layout[0].children.map(({ value }) => value.label)).toEqual(["Beta", "Unicorn"]);
  });
});

describe("marketTreemapLabelDensity", () => {
  it("suppresses only visual copy in tiny rectangles without changing geometry", () => {
    expect(marketTreemapLabelDensity({ x: 0, y: 0, width: 89, height: 400 })).toBe("hidden");
    expect(marketTreemapLabelDensity({ x: 0, y: 0, width: 120, height: 100 })).toBe("compact");
    expect(marketTreemapLabelDensity({ x: 0, y: 0, width: 240, height: 180 })).toBe("full");
  });
});
