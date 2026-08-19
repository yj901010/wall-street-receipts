import type { DataMode } from "@/lib/data-mode";

export const MARKET_MAP_UNIVERSES = ["sp500", "nasdaq100"] as const;

export type MarketMapUniverse = (typeof MARKET_MAP_UNIVERSES)[number];
export type MarketMapMode = "ANALYST_CONSENSUS";
export type MarketMapCoverage = {
  kind: "SAMPLE";
  completeUniverse: false;
  cellCount: number;
  weightBasis: "SYNTHETIC_RELATIVE";
};

export type MarketMapMetric = {
  name: "analystConsensus";
  unit: "score";
  minimum: number;
  maximum: number;
  missingDisplay: "NA";
};

export type MarketMapCell = {
  assetId: string;
  ticker: string;
  sector: string | null;
  weight: number;
  metric: number | null;
  callCount: number;
  timestamp: string;
  dataMode: DataMode;
  provenanceId: string;
};

export type MarketMapSnapshot = {
  schemaVersion: "1.0.0";
  fixtureVersion: "v1";
  universe: MarketMapUniverse;
  mode: MarketMapMode;
  asOf: string;
  generatedAt: string;
  capturedAt: string;
  dataMode: DataMode;
  source: string;
  coverage: MarketMapCoverage;
  metric: MarketMapMetric;
  cells: MarketMapCell[];
  disclaimer: string;
};

export interface MarketMapProvider {
  findByUniverse(universe: MarketMapUniverse): Promise<MarketMapSnapshot>;
}

export function isMarketMapUniverse(value: string): value is MarketMapUniverse {
  return MARKET_MAP_UNIVERSES.some((universe) => universe === value);
}
