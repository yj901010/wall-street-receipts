import type { DataMode } from "@/lib/data-mode";

export const CALL_DIRECTIONS = [
  "STRONG_BULLISH",
  "BULLISH",
  "NEUTRAL",
  "BEARISH",
  "STRONG_BEARISH",
] as const;

export const CALL_STATUSES = ["ACTIVE", "CORRECTED", "CANCELLED"] as const;
export const CALL_SORT_FIELDS = ["eventTime", "processingTime", "capturedAt"] as const;

export type CallDirection = (typeof CALL_DIRECTIONS)[number];
export type CallStatus = (typeof CALL_STATUSES)[number];
export type CallSortField = (typeof CALL_SORT_FIELDS)[number];
export type SortOrder = "asc" | "desc";
export type AssetType = "INDEX" | "EQUITY" | "ETF" | "BOND" | "COMMODITY" | "FX";

export type CallsQuery = {
  assetId?: string;
  ticker?: string;
  institutionId?: string;
  analystId?: string;
  direction?: CallDirection;
  status?: CallStatus;
  dataMode?: DataMode;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: CallSortField;
  order?: SortOrder;
};

export type AnalystCall = {
  schemaVersion: "1.0.0";
  callId: string;
  provider: string;
  providerEventId: string;
  institutionId: string;
  analystId: string | null;
  assetId: string;
  eventTime: string;
  processingTime: string;
  direction: CallDirection;
  originalRating: string | null;
  previousTarget: number | null;
  target: number | null;
  currency: string | null;
  targetDate: string | null;
  sourceReferenceId: string;
  status: CallStatus;
  dataMode: DataMode;
  capturedAt: string;
  provenanceId: string;
};

export type InstitutionSummary = {
  institutionId: string;
  canonicalName: string;
  slug: string;
};

export type AnalystSummary = {
  analystId: string;
  canonicalName: string;
};

export type AssetSummary = {
  assetId: string;
  assetType: AssetType;
  canonicalName: string;
  ticker: string | null;
};

export type SourceDocument = {
  schemaVersion: "1.0.0";
  sourceDocumentId: string;
  sourceType: "VIDEO" | "ARTICLE" | "RESEARCH" | "PODCAST" | "FILING" | "TRANSCRIPT";
  publisher: string | null;
  title: string;
  canonicalUrl: string | null;
  publishedAt: string | null;
  provider: string;
  externalId: string | null;
  contentHash: string | null;
  licenseClass: string;
  dataMode: DataMode;
  capturedAt: string;
  provenanceId: string;
};

export type SourceReference = {
  schemaVersion: "1.0.0";
  sourceReferenceId: string;
  sourceDocumentId: string;
  page: number | null;
  startMs: number | null;
  endMs: number | null;
  extractedFragment: string | null;
  extractionConfidence: number | null;
  verified: boolean;
  dataMode: DataMode;
  capturedAt: string;
  provenanceId: string;
};

export type SourceEvidence = {
  document: SourceDocument;
  reference: SourceReference;
};

export type MarketSnapshot = {
  schemaVersion: "1.0.0";
  snapshotId: string;
  callId: string;
  assetId: string;
  eventTime: string;
  processingTime: string;
  assetPrice: number | null;
  spx: number | null;
  ndx: number | null;
  vix: number | null;
  treasury2y: number | null;
  treasury10y: number | null;
  realYield: number | null;
  dxy: number | null;
  wti: number | null;
  gold: number | null;
  volatility: number | null;
  distanceFrom52WeekHigh: number | null;
  distanceFromAth: number | null;
  immutable: true;
  dataMode: DataMode;
  capturedAt: string;
  provenanceId: string;
};

export type AnalystCallView = {
  call: AnalystCall;
  institution: InstitutionSummary;
  analyst: AnalystSummary | null;
  asset: AssetSummary;
  source: SourceEvidence;
};

export type AnalystCallDetail = AnalystCallView & {
  snapshot: MarketSnapshot | null;
};

export type PageMetadata = {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  sort: { field: CallSortField; order: SortOrder };
};

export type AnalystCallPage = {
  items: AnalystCallView[];
  page: PageMetadata;
};

export type CallsMetadata = {
  asOf: string;
  dataMode: DataMode;
  source: string;
  disclaimer: string;
  facets: {
    institutions: InstitutionSummary[];
    analysts: AnalystSummary[];
    assets: AssetSummary[];
    directions: CallDirection[];
    statuses: CallStatus[];
  };
};

export interface CallsProvider {
  list(query?: CallsQuery): Promise<AnalystCallPage>;
  metadata(): Promise<CallsMetadata>;
  findById(id: string): Promise<AnalystCallDetail | null>;
}
