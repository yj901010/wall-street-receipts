import { describe, expect, it } from "vitest";
import type { MarketMapCell, MarketMapMetric } from "@/lib/providers";
import { presentMarketMapCell } from "./market-map-engine";

const metric: MarketMapMetric = {
  name: "analystConsensus",
  unit: "score",
  minimum: -1,
  maximum: 1,
  missingDisplay: "NA",
};

function cell(value: number | null, sector: string | null = "Technology"): MarketMapCell {
  return {
    assetId: "asset-demo",
    ticker: "DEMO",
    sector,
    weight: 1,
    metric: value,
    callCount: 0,
    timestamp: "2026-08-12T06:00:00Z",
    dataMode: "DEMO",
    provenanceId: "fixture-market-map-v1",
  };
}

describe("presentMarketMapCell", () => {
  it("keeps a missing metric unavailable rather than coercing it to zero or negative", () => {
    expect(presentMarketMapCell(cell(null, null), metric)).toEqual({
      metricDisplay: "NA",
      metricTone: "unavailable",
      sectorDisplay: "NA",
    });
  });

  it.each([
    [-0.4, "negative"],
    [0, "neutral"],
    [0.4, "positive"],
  ] as const)("presents the stored metric %s with its sign tone", (value, tone) => {
    expect(presentMarketMapCell(cell(value), metric)).toMatchObject({
      metricDisplay: String(value),
      metricTone: tone,
    });
  });
});
