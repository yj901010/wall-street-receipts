export type ScreenerShellState = Readonly<{
  dataMode: "DEMO";
  scope: "HISTORICAL_EQUITY_SCREENING";
  status: "P8_DEFERRED";
  reasonCode: "NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG";
  missingDisplay: "NA";
}>;

/**
 * Application-owned release policy. This is deliberately not fixture evidence
 * and must not acquire source, provenance, timestamp, filter, or result fields.
 */
export const SCREENER_SHELL_STATE: ScreenerShellState = Object.freeze({
  dataMode: "DEMO",
  scope: "HISTORICAL_EQUITY_SCREENING",
  status: "P8_DEFERRED",
  reasonCode: "NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG",
  missingDisplay: "NA",
});
