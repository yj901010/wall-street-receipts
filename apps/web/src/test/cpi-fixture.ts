import { CPI_IDS, CPI_SOURCE, type CpiSnapshot } from "@/lib/cpi";
/** Test-only synthetic data. The production provider has no fixture mode. */
export function cpiFixture(): CpiSnapshot {
  return {
    schemaVersion: "1.0.0", dataMode: "DEMO", policyVersion: "BLS_CPI_RETRIEVAL_V1",
    captureId: "00000000-0000-4000-8000-000000000064", capturedAt: "2026-09-08T01:00:00Z", servedAt: "2026-09-08T01:00:01Z",
    responseSha256: "0".repeat(64), source: "BLS", sourceUrl: CPI_SOURCE, releaseTimeStatus: "NOT_PROVIDED",
    vintageStatus: "RETRIEVAL_VINTAGE_ONLY", seasonality: "NSA", unit: "1982-84=100", calculationVersion: "WSR_CPI_YOY_HALF_UP_1DP_V1",
    series: CPI_IDS.map(id => ({ id, observations: [
      { month: "2026-07", index: "103.050", yearOverYearPct: "3.1", footnotes: [] },
      { month: "2026-06", index: null, yearOverYearPct: null, footnotes: ["X: Synthetic missing value"] },
      { month: "2025-07", index: "100.000", yearOverYearPct: null, footnotes: [] },
    ] })),
  };
}
