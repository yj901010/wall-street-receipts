export type MarketBoardProvenance = {
  id: string;
  sourceType: "LOCAL_SPECIFICATION";
  sourcePaths: [string, string];
  capturedAt: string;
  synthetic: true;
  licenseClass: "INTERNAL_DEMO";
};

export type MarketBoardSnapshot = {
  schemaVersion: "1.0.0";
  fixtureVersion: "v1";
  dataMode: "DEMO";
  generatedAt: string;
  provenance: MarketBoardProvenance;
  scope: "GLOBAL_MARKET_OVERVIEW";
  publicationStatus: "NOT_PUBLISHED";
  publicationReasonCode: "NO_CANONICAL_GLOBAL_QUOTE_CATALOG";
  marketAsOf: null;
  missingDisplay: "NA";
  quotes: [];
  disclaimer: string;
};

export interface MarketBoardProvider {
  snapshot(): Promise<MarketBoardSnapshot>;
}
