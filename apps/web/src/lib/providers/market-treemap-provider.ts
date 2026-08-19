import type { DataMode } from "@/lib/data-mode";

export const MARKET_TREEMAP_UNIVERSES = ["sp500", "nasdaq100"] as const;

export type MarketTreemapUniverse = (typeof MARKET_TREEMAP_UNIVERSES)[number];
export type MarketTreemapMode = "PRICE_CHANGE";

export type MarketTreemapMetric = {
  name: "priceChangePercent";
  unit: "percent";
  scaleMinimum: number;
  scaleMaximum: number;
  missingDisplay: "NA";
};

export type MarketTreemapGeometry = {
  type: "NESTED_TREEMAP";
  groupBy: readonly ["sector", "industry"];
  unclassifiedDisplay: "Unclassified";
  areaField: "syntheticMarketCapProxy";
  areaUnit: "relative";
};

export type MarketTreemapCoverage = {
  kind: "SAMPLE";
  completeUniverse: false;
  cellCount: number;
  weightBasis: "SYNTHETIC_MARKET_CAP_PROXY";
};

export type MarketTreemapProvenance = {
  id: string;
  sourceType: "LOCAL_SPECIFICATION";
  sourcePaths: readonly string[];
  capturedAt: string;
  synthetic: true;
  licenseClass: "INTERNAL_DEMO";
};

export type MarketTreemapCell = {
  assetId: string;
  ticker: string;
  sector: string | null;
  industry: string | null;
  syntheticMarketCapProxy: number;
  priceChangePercent: number | null;
  timestamp: string;
  dataMode: DataMode;
  provenanceId: string;
};

export type MarketTreemapSnapshot = {
  schemaVersion: "1.0.0";
  fixtureVersion: "v1";
  dataMode: DataMode;
  generatedAt: string;
  provenance: MarketTreemapProvenance;
  universe: MarketTreemapUniverse;
  mode: MarketTreemapMode;
  asOf: string;
  metric: MarketTreemapMetric;
  geometry: MarketTreemapGeometry;
  coverage: MarketTreemapCoverage;
  cells: readonly MarketTreemapCell[];
  disclaimer: string;
};

export interface MarketTreemapProvider {
  findByUniverse(universe: MarketTreemapUniverse): Promise<MarketTreemapSnapshot>;
}

export function isMarketTreemapUniverse(value: string): value is MarketTreemapUniverse {
  return MARKET_TREEMAP_UNIVERSES.some((universe) => universe === value);
}
