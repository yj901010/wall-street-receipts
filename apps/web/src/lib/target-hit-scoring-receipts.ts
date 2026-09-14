/** Wire validation only. Java's pinned deterministic evaluator calculates every metric. */
export const RECEIPT_METHOD_HASH = "aaa8684e12a5d83df36eff5a15107828931526b36ccfcca71ac8f968804b1b1e";
export const CALL_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
export const RECEIPT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export type Metric = { state: "AVAILABLE" | "PENDING" | "UNAVAILABLE" | "NOT_APPLICABLE"; decimalValue: string | null; booleanValue: boolean | null; reasons: string[] };
export type TargetHitScoringReceipt = {
  receiptId: string; callId: string; snapshotId: string; snapshotProvenanceId: string; snapshotRole: string;
  basisRevisionId: string | null; basisRevisionSequence: number | null; basisEventTimeUtc: string;
  methodologyId: string; methodologyVersion: string; methodologyDefinitionHash: string;
  inputFingerprint: string; ledgerFingerprint: string; evaluationAsOfUtc: string; evaluationAsOfKst: string;
  recordedAtUtc: string; recordedAtKst: string; dataMode: "DEMO"; scope: "PARTIAL_TARGET_HIT"; dataComplete: false;
  source: "PERSISTED_DEMO_INPUT_REPLAY"; termsProvenanceId: string; horizon: string;
  assetReturn: Metric; directionalWin: Metric; targetError: Metric; benchmarkReturn: Metric; sectorReturn: Metric;
  benchmarkEvidence: ReferenceEvidence | null; sectorEvidence: ReferenceEvidence | null;
  targetHit: Metric; windowEvidence: WindowEvidence | null;
};
export const WINDOW_ATTESTATION = "CALLER_ATTESTED_DEMO_CAUSAL_WINDOW_NOT_RAW_TRADE_VERIFICATION";
export const WINDOW_ADJUSTMENT = "SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED";
export type EvidenceTime = { availableAtUtc: string; capturedAtUtc: string };
export type TargetEvidence = EvidenceTime & { evidenceId: string; provenanceId: string; currency: string; adjustmentBasis: string; value: string };
export type WindowBinding = EvidenceTime & { bindingId: string; revision: string; assetId: string; primaryVenueId: string; currency: string; priceSourceId: string; priceSourceRevision: string; provenanceId: string };
export type WindowObservation = EvidenceTime & {
  observationId: string; providerEventId: string; assetId: string; venueId: string; currency: string;
  priceSourceId: string; priceSourceRevision: string; provenanceId: string; calendarId: string; catalogRevision: string;
  orderedSessionIds: string[]; lowerBoundUtc: string; lowerBoundType: string; upperBoundUtc: string; upperBoundType: string;
  priceField: string; coverageCompleteness: string; adjustmentBasis: string; corporateActionContinuity: string; windowHigh: string; windowLow: string;
};
export type WindowEvidence = { attestationScope: string; target: TargetEvidence; binding: WindowBinding; observation: WindowObservation; selectedField: "HIGH" | "LOW"; selectedValue: string };
export type ReferenceLevel = {observationId: string; providerEventId: string; value: string; observedAtUtc: string; sourceId: string; sourceRevision: string; provenanceId: string};
export type ReferenceEvidence = {
  bindingId: string; bindingProvenanceId: string; sourceBindingRole: "BENCHMARK_ASSIGNMENT" | "SECTOR_MAPPING"; sourceBindingId: string;
  referenceAssetId: string; providerId: string; indexId: string; definitionRevision: string; currency: string;
  calendarId: string; calendarRevision: string; basisLevel: ReferenceLevel; endpointLevel: ReferenceLevel;
  continuityEvidenceId: string; continuityProvenanceId: string;
};
export type ReceiptState =
  | { kind: "disabled" | "invalid" | "missing" | "unavailable" }
  | { kind: "ready"; items: TargetHitScoringReceipt[]; hasMore: boolean; selected: boolean };

function requireThat(condition: unknown): asserts condition { if (!condition) throw new Error("Invalid DEMO scoring response"); }
function object(value: unknown): Record<string, unknown> { requireThat(value !== null && typeof value === "object" && !Array.isArray(value)); return value as Record<string, unknown>; }
function keys(value: Record<string, unknown>, expected: string[]) { requireThat(Object.keys(value).length === expected.length && expected.every(key => Object.hasOwn(value, key))); }
function text(value: unknown, max = 256): asserts value is string { requireThat(typeof value === "string" && value.length > 0 && value.length <= max && value.trim() === value && !/[\u0000-\u001f\u007f]/.test(value)); }
function hash(value: unknown) { requireThat(typeof value === "string" && /^[0-9a-f]{64}$/.test(value)); }

/** Compare UTC microseconds without losing JDBC sub-millisecond ordering to JS Number. */
function instant(value: unknown, kst = false): bigint {
  text(value, 40);
  const normalized = kst ? value.replace(/T(\d{2}:\d{2})\+09:00$/, "T$1:00+09:00") : value;
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?(Z|\+09:00)$/.exec(normalized);
  requireThat(match && !match[1].startsWith("0000-") && match[3] === (kst ? "+09:00" : "Z"));
  const millis = Date.parse(match[1] + match[3]); requireThat(Number.isFinite(millis));
  const local = new Date(millis + (kst ? 9 * 3600000 : 0)).toISOString().slice(0, 19);
  requireThat(local === match[1]);
  return BigInt(millis) * 1000n + BigInt((match[2] ?? "").padEnd(6, "0"));
}
function metric(value: unknown, kind: "assetReturn" | "directionalWin" | "targetError" | "benchmarkReturn" | "sectorReturn" | "targetHit"): Metric {
  const m = object(value); keys(m, ["state", "decimalValue", "booleanValue", "reasons"]);
  requireThat(["AVAILABLE", "PENDING", "UNAVAILABLE", "NOT_APPLICABLE"].includes(String(m.state)));
  requireThat(Array.isArray(m.reasons) && m.reasons.length <= 3 && m.reasons.every(r => typeof r === "string" && /^[A-Z][A-Z0-9_]{0,127}$/.test(r)));
  if (m.state === "AVAILABLE") {
    requireThat(m.reasons.length === 0);
    if (kind === "directionalWin" || kind === "targetHit") requireThat(m.decimalValue === null && typeof m.booleanValue === "boolean");
    else {
      requireThat(m.booleanValue === null && typeof m.decimalValue === "string" && /^-?(0|[1-9]\d{0,25})\.\d{12}$/.test(m.decimalValue));
      const scaled = BigInt(m.decimalValue.replace(".", ""));
      requireThat(scaled >= (kind === "targetError" ? 0n : -1000000000000n));
    }
  } else {
    requireThat(m.decimalValue === null && m.booleanValue === null && m.reasons.length > 0);
    requireThat(m.state !== "NOT_APPLICABLE" || ["directionalWin", "benchmarkReturn", "sectorReturn", "targetHit"].includes(kind));
  }
  return m as Metric;
}
export function receipt(value: unknown, callId: string, selectedId?: string): TargetHitScoringReceipt {
  const r = object(value);
  keys(r, ["receiptId", "callId", "snapshotId", "snapshotProvenanceId", "snapshotRole", "basisRevisionId", "basisRevisionSequence", "basisEventTimeUtc",
    "methodologyId", "methodologyVersion", "methodologyDefinitionHash", "inputFingerprint", "ledgerFingerprint", "evaluationAsOfUtc", "evaluationAsOfKst",
    "recordedAtUtc", "recordedAtKst", "dataMode", "scope", "dataComplete", "source", "termsProvenanceId", "horizon", "assetReturn", "directionalWin", "targetError", "benchmarkReturn", "sectorReturn", "benchmarkEvidence", "sectorEvidence", "targetHit", "windowEvidence"]);
  text(r.receiptId); requireThat(RECEIPT_ID.test(r.receiptId) && (!selectedId || selectedId === r.receiptId) && r.callId === callId);
  requireThat(r.dataMode === "DEMO" && r.scope === "PARTIAL_TARGET_HIT" && r.dataComplete === false && r.source === "PERSISTED_DEMO_INPUT_REPLAY");
  requireThat(r.snapshotRole === "ORIGINAL_CALL_CONTEXT_ONLY_NOT_PRICE_SOURCE");
  for (const key of ["snapshotId", "snapshotProvenanceId", "termsProvenanceId"]) text(r[key]);
  requireThat(r.methodologyId === "wsr-demo-target-hit-preview" && r.methodologyVersion === "1.0.0" && r.methodologyDefinitionHash === RECEIPT_METHOD_HASH);
  hash(r.inputFingerprint); hash(r.ledgerFingerprint);
  requireThat(["D1", "W1", "M1", "M3", "M6", "Y1"].includes(String(r.horizon)));
  if (r.basisRevisionId === null) requireThat(r.basisRevisionSequence === null);
  else { text(r.basisRevisionId, 128); requireThat(CALL_ID.test(r.basisRevisionId) && Number.isSafeInteger(r.basisRevisionSequence) && Number(r.basisRevisionSequence) > 0); }
  requireThat(instant(r.basisEventTimeUtc) <= instant(r.evaluationAsOfUtc) && instant(r.evaluationAsOfUtc) <= instant(r.recordedAtUtc));
  requireThat(instant(r.evaluationAsOfUtc) === instant(r.evaluationAsOfKst, true) && instant(r.recordedAtUtc) === instant(r.recordedAtKst, true));
  metric(r.assetReturn, "assetReturn"); metric(r.directionalWin, "directionalWin"); metric(r.targetError, "targetError");
  const benchmark = metric(r.benchmarkReturn, "benchmarkReturn"), sector = metric(r.sectorReturn, "sectorReturn");
  reference(r.benchmarkEvidence, benchmark, "BENCHMARK_ASSIGNMENT", r.basisEventTimeUtc, r.evaluationAsOfUtc);
  reference(r.sectorEvidence, sector, "SECTOR_MAPPING", r.basisEventTimeUtc, r.evaluationAsOfUtc);
  windowEvidence(r.windowEvidence, metric(r.targetHit, "targetHit"), r.basisEventTimeUtc, r.evaluationAsOfUtc);
  return r as TargetHitScoringReceipt;
}
export function receiptPage(value: unknown, callId: string): Extract<ReceiptState, {kind: "ready"}> {
  const page = object(value); keys(page, ["dataMode", "source", "limit", "hasMore", "items"]);
  requireThat(page.dataMode === "DEMO" && page.source === "PERSISTED_DEMO_INPUT_REPLAY" && page.limit === 20 && typeof page.hasMore === "boolean");
  requireThat(Array.isArray(page.items) && page.items.length <= 20 && (!page.hasMore || page.items.length === 20));
  const items = page.items.map(item => receipt(item, callId)); const seen = new Set<string>();
  for (let n = 0; n < items.length; n++) {
    const current = items[n]; requireThat(!seen.has(current.receiptId)); seen.add(current.receiptId);
    if (n > 0) { const previous = items[n - 1]; const diff = instant(previous.recordedAtUtc) - instant(current.recordedAtUtc);
      requireThat(diff > 0n || (diff === 0n && previous.receiptId > current.receiptId)); }
  }
  return { kind: "ready", items, hasMore: page.hasMore, selected: false };
}

/** Selected DEMO provenance is preserved text, not a signature or browser-calculated proof. */
function preservedText(value: unknown): asserts value is string {
  requireThat(typeof value === "string" && value.length > 0 && value.length <= 65536);
}
function reference(value: unknown, metric: Metric, role: ReferenceEvidence["sourceBindingRole"], basis: unknown, asof: unknown) {
  const selected = metric.state === "AVAILABLE" || (metric.state === "UNAVAILABLE" && metric.reasons.length === 1 && metric.reasons[0] === "OUTPUT_NOT_REPRESENTABLE");
  if (!selected) { requireThat(value === null); return; }
  const r = object(value);
  keys(r, ["bindingId", "bindingProvenanceId", "sourceBindingRole", "sourceBindingId", "referenceAssetId", "providerId", "indexId", "definitionRevision",
    "currency", "calendarId", "calendarRevision", "basisLevel", "endpointLevel", "continuityEvidenceId", "continuityProvenanceId"]);
  requireThat(r.sourceBindingRole === role && typeof r.currency === "string" && /^[A-Z]{3}$/.test(r.currency));
  for (const key of ["bindingId", "bindingProvenanceId", "sourceBindingId", "referenceAssetId", "providerId", "indexId", "definitionRevision",
    "calendarId", "calendarRevision", "continuityEvidenceId", "continuityProvenanceId"]) preservedText(r[key]);
  const start = level(r.basisLevel), end = level(r.endpointLevel);
  requireThat(instant(start.observedAtUtc) === instant(basis) && instant(end.observedAtUtc) >= instant(start.observedAtUtc)
    && instant(end.observedAtUtc) <= instant(asof));
  requireThat(start.sourceId === end.sourceId && start.sourceRevision === end.sourceRevision);
}
function level(value: unknown): ReferenceLevel {
  const r = object(value); keys(r, ["observationId", "providerEventId", "value", "observedAtUtc", "sourceId", "sourceRevision", "provenanceId"]);
  for (const key of ["observationId", "providerEventId", "sourceId", "sourceRevision", "provenanceId"]) preservedText(r[key]);
  requireThat(typeof r.value === "string" && /^(0|[1-9]\d{0,25})(?:\.\d{1,12})?$/.test(r.value));
  requireThat(BigInt(r.value.replace(".", "")) > 0n); instant(r.observedAtUtc);
  return r as ReferenceLevel;
}

/** Numeric comparison checks wire consistency only; never derives a target-hit outcome. */
function positiveLevel(value: unknown): bigint {
  requireThat(typeof value === "string" && /^(0|[1-9]\d{0,25})(?:\.\d{1,12})?$/.test(value));
  const [whole, fraction = ""] = value.split(".");
  const scaled = BigInt(whole + fraction.padEnd(12, "0")); requireThat(scaled > 0n); return scaled;
}
function evidenceTime(value: Record<string, unknown>, asof: unknown) {
  requireThat(instant(value.availableAtUtc) <= instant(value.capturedAtUtc) && instant(value.capturedAtUtc) <= instant(asof));
}
function windowEvidence(value: unknown, hit: Metric, basis: unknown, asof: unknown) {
  if (hit.state !== "AVAILABLE") { requireThat(value === null); return; }
  const r = object(value); keys(r, ["attestationScope", "target", "binding", "observation", "selectedField", "selectedValue"]);
  requireThat(r.attestationScope === WINDOW_ATTESTATION && ["HIGH", "LOW"].includes(String(r.selectedField)));
  const t = object(r.target), b = object(r.binding), o = object(r.observation);
  keys(t, ["evidenceId", "provenanceId", "currency", "adjustmentBasis", "value", "availableAtUtc", "capturedAtUtc"]);
  keys(b, ["bindingId", "revision", "assetId", "primaryVenueId", "currency", "priceSourceId", "priceSourceRevision", "availableAtUtc", "capturedAtUtc", "provenanceId"]);
  keys(o, ["observationId", "providerEventId", "assetId", "venueId", "currency", "priceSourceId", "priceSourceRevision", "provenanceId", "calendarId", "catalogRevision",
    "orderedSessionIds", "lowerBoundUtc", "lowerBoundType", "upperBoundUtc", "upperBoundType", "priceField", "coverageCompleteness", "adjustmentBasis", "corporateActionContinuity", "availableAtUtc", "capturedAtUtc", "windowHigh", "windowLow"]);
  for (const key of ["evidenceId", "provenanceId"]) preservedText(t[key]);
  for (const key of ["bindingId", "revision", "assetId", "primaryVenueId", "priceSourceId", "priceSourceRevision", "provenanceId"]) preservedText(b[key]);
  for (const key of ["observationId", "providerEventId", "assetId", "venueId", "priceSourceId", "priceSourceRevision", "provenanceId", "calendarId", "catalogRevision"]) preservedText(o[key]);
  requireThat(typeof t.currency === "string" && /^[A-Z]{3}$/.test(t.currency) && t.currency === b.currency && b.currency === o.currency);
  requireThat(b.assetId === o.assetId && b.primaryVenueId === o.venueId && b.priceSourceId === o.priceSourceId && b.priceSourceRevision === o.priceSourceRevision);
  requireThat(t.adjustmentBasis === WINDOW_ADJUSTMENT && o.adjustmentBasis === WINDOW_ADJUSTMENT && o.corporateActionContinuity === "SPLIT_REVERSE_SPLIT_CONTINUOUS");
  requireThat(o.priceField === "PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR" && o.coverageCompleteness === "EXACT_CAUSAL_WINDOW_SESSION_UNION");
  requireThat(o.lowerBoundType === "EXCLUSIVE" && o.upperBoundType === "INCLUSIVE");
  requireThat(instant(o.lowerBoundUtc) === instant(basis) && instant(o.lowerBoundUtc) < instant(o.upperBoundUtc) && instant(o.upperBoundUtc) <= instant(o.availableAtUtc));
  evidenceTime(t, asof); evidenceTime(b, asof); evidenceTime(o, asof);
  requireThat(Array.isArray(o.orderedSessionIds) && o.orderedSessionIds.length >= 1 && o.orderedSessionIds.length <= 4096);
  o.orderedSessionIds.forEach(preservedText); requireThat(new Set(o.orderedSessionIds).size === o.orderedSessionIds.length);
  positiveLevel(t.value); const high = positiveLevel(o.windowHigh), low = positiveLevel(o.windowLow);
  requireThat(low <= high && positiveLevel(r.selectedValue) === (r.selectedField === "HIGH" ? high : low));
}
