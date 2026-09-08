export const CPI_IDS = ["CUUR0000SA0", "CUUR0000SA0L1E"] as const;
export const CPI_SOURCE = "https://api.bls.gov/publicAPI/v2/timeseries/data/";
export type CpiObservation = { month: string; index: string | null; yearOverYearPct: string | null; footnotes: string[] };
export type CpiSnapshot = {
  schemaVersion: "1.0.0"; dataMode: "OBSERVED_MONTHLY" | "DEMO";
  policyVersion: "BLS_CPI_RETRIEVAL_V1"; captureId: string; capturedAt: string; servedAt: string;
  responseSha256: string; source: "BLS"; sourceUrl: typeof CPI_SOURCE;
  releaseTimeStatus: "NOT_PROVIDED"; vintageStatus: "RETRIEVAL_VINTAGE_ONLY";
  seasonality: "NSA"; unit: "1982-84=100"; calculationVersion: "WSR_CPI_YOY_HALF_UP_1DP_V1";
  series: { id: string; observations: CpiObservation[] }[];
};

function requireValue(condition: unknown): asserts condition {
  if (!condition) throw new Error("CPI snapshot failed validation");
}
function object(value: unknown, keys: string[]): Record<string, unknown> {
  requireValue(value !== null && typeof value === "object" && !Array.isArray(value));
  const result = value as Record<string, unknown>;
  requireValue(Object.keys(result).length === keys.length && keys.every(key => Object.hasOwn(result, key)));
  return result;
}
function instant(value: unknown): value is string {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value)
    && Number.isFinite(Date.parse(value));
}

/** The web never calculates financial returns or silently repairs provider values. */
export function adaptCpi(value: unknown): CpiSnapshot {
  const result = object(value, ["schemaVersion", "dataMode", "policyVersion", "captureId", "capturedAt", "servedAt",
    "responseSha256", "source", "sourceUrl", "releaseTimeStatus", "vintageStatus", "seasonality", "unit", "calculationVersion", "series"]);
  const constants = { schemaVersion: "1.0.0", dataMode: "OBSERVED_MONTHLY", policyVersion: "BLS_CPI_RETRIEVAL_V1",
    source: "BLS", sourceUrl: CPI_SOURCE, releaseTimeStatus: "NOT_PROVIDED", vintageStatus: "RETRIEVAL_VINTAGE_ONLY",
    seasonality: "NSA", unit: "1982-84=100", calculationVersion: "WSR_CPI_YOY_HALF_UP_1DP_V1" };
  for (const [key, expected] of Object.entries(constants)) requireValue(result[key] === expected);
  requireValue(typeof result.captureId === "string" && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(result.captureId));
  requireValue(typeof result.responseSha256 === "string" && /^[0-9a-f]{64}$/.test(result.responseSha256));
  requireValue(instant(result.capturedAt) && instant(result.servedAt) && Date.parse(result.capturedAt) <= Date.parse(result.servedAt));
  requireValue(Array.isArray(result.series) && result.series.length === 2);
  result.series.forEach((raw, position) => {
    const series = object(raw, ["id", "observations"]);
    requireValue(series.id === CPI_IDS[position] && Array.isArray(series.observations) && series.observations.length > 0 && series.observations.length <= 48);
    let previous = "9999-99";
    series.observations.forEach(rawRow => {
      const row = object(rawRow, ["month", "index", "yearOverYearPct", "footnotes"]);
      requireValue(typeof row.month === "string" && /^\d{4}-(0[1-9]|1[0-2])$/.test(row.month)
        && row.month < previous && row.month < (result.capturedAt as string).slice(0, 7)
        && Number(row.month.slice(0, 4)) >= Number((result.capturedAt as string).slice(0, 4)) - 3);
      previous = row.month;
      requireValue(row.index === null || typeof row.index === "string" && /^\d{1,6}(\.\d{1,3})?$/.test(row.index) && Number(row.index) > 0);
      requireValue(row.yearOverYearPct === null || typeof row.yearOverYearPct === "string" && /^-?\d{1,12}\.\d$/.test(row.yearOverYearPct));
      requireValue(row.index !== null || row.yearOverYearPct === null);
      requireValue(Array.isArray(row.footnotes) && row.footnotes.length <= 8 && row.footnotes.every(note => typeof note === "string" && note.length <= 2066));
    });
    for (const row of series.observations as CpiObservation[]) {
      const priorMonth = `${Number(row.month.slice(0, 4)) - 1}${row.month.slice(4)}`;
      if (!(series.observations as CpiObservation[]).some(prior => prior.month === priorMonth && prior.index !== null)) {
        requireValue(row.yearOverYearPct === null);
      }
    }
  });
  return result as CpiSnapshot;
}

/** Calendar rows, including absent months; no interpolation or zero filling. */
export function cpiMonths(snapshot: CpiSnapshot): string[] {
  const latest = snapshot.series.flatMap(series => series.observations.map(row => row.month)).sort().at(-1)!;
  const date = new Date(`${latest}-01T00:00:00Z`);
  return Array.from({ length: 24 }, (_, offset) => new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() - offset, 1)).toISOString().slice(0, 7));
}
