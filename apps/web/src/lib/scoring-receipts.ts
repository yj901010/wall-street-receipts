/** Wire validation only. Java's pinned deterministic evaluator calculates every metric. */
export const RECEIPT_METHOD_HASH = "91abc0fcbc986b47e505bbddba346977911c9977664621e4c88cb8a2cbf8ea27";
export const CALL_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
export const RECEIPT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export type Metric = { state: "AVAILABLE" | "PENDING" | "UNAVAILABLE" | "NOT_APPLICABLE"; decimalValue: string | null; booleanValue: boolean | null; reasons: string[] };
export type ScoringReceipt = {
  receiptId: string; callId: string; snapshotId: string; snapshotProvenanceId: string; snapshotRole: string;
  basisRevisionId: string | null; basisRevisionSequence: number | null; basisEventTimeUtc: string;
  methodologyId: string; methodologyVersion: string; methodologyDefinitionHash: string;
  inputFingerprint: string; ledgerFingerprint: string; evaluationAsOfUtc: string; evaluationAsOfKst: string;
  recordedAtUtc: string; recordedAtKst: string; dataMode: "DEMO"; scope: "PARTIAL_ENDPOINT"; dataComplete: false;
  source: "PERSISTED_DEMO_INPUT_REPLAY"; termsProvenanceId: string; horizon: string;
  assetReturn: Metric; directionalWin: Metric; targetError: Metric;
};
export type ReceiptState =
  | { kind: "disabled" | "invalid" | "missing" | "unavailable" }
  | { kind: "ready"; items: ScoringReceipt[]; hasMore: boolean; selected: boolean };

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
function metric(value: unknown, kind: "assetReturn" | "directionalWin" | "targetError"): Metric {
  const m = object(value); keys(m, ["state", "decimalValue", "booleanValue", "reasons"]);
  requireThat(["AVAILABLE", "PENDING", "UNAVAILABLE", "NOT_APPLICABLE"].includes(String(m.state)));
  requireThat(Array.isArray(m.reasons) && m.reasons.length <= 3 && m.reasons.every(r => typeof r === "string" && /^[A-Z][A-Z0-9_]{0,127}$/.test(r)));
  if (m.state === "AVAILABLE") {
    requireThat(m.reasons.length === 0);
    if (kind === "directionalWin") requireThat(m.decimalValue === null && typeof m.booleanValue === "boolean");
    else {
      requireThat(m.booleanValue === null && typeof m.decimalValue === "string" && /^-?(0|[1-9]\d{0,25})\.\d{12}$/.test(m.decimalValue));
      const scaled = BigInt(m.decimalValue.replace(".", ""));
      requireThat(scaled >= (kind === "assetReturn" ? -1000000000000n : 0n));
    }
  } else {
    requireThat(m.decimalValue === null && m.booleanValue === null && m.reasons.length > 0);
    requireThat(m.state !== "NOT_APPLICABLE" || kind === "directionalWin");
  }
  return m as Metric;
}
export function receipt(value: unknown, callId: string, selectedId?: string): ScoringReceipt {
  const r = object(value);
  keys(r, ["receiptId", "callId", "snapshotId", "snapshotProvenanceId", "snapshotRole", "basisRevisionId", "basisRevisionSequence", "basisEventTimeUtc",
    "methodologyId", "methodologyVersion", "methodologyDefinitionHash", "inputFingerprint", "ledgerFingerprint", "evaluationAsOfUtc", "evaluationAsOfKst",
    "recordedAtUtc", "recordedAtKst", "dataMode", "scope", "dataComplete", "source", "termsProvenanceId", "horizon", "assetReturn", "directionalWin", "targetError"]);
  text(r.receiptId); requireThat(RECEIPT_ID.test(r.receiptId) && (!selectedId || selectedId === r.receiptId) && r.callId === callId);
  requireThat(r.dataMode === "DEMO" && r.scope === "PARTIAL_ENDPOINT" && r.dataComplete === false && r.source === "PERSISTED_DEMO_INPUT_REPLAY");
  requireThat(r.snapshotRole === "ORIGINAL_CALL_CONTEXT_ONLY_NOT_PRICE_SOURCE");
  for (const key of ["snapshotId", "snapshotProvenanceId", "termsProvenanceId"]) text(r[key]);
  requireThat(r.methodologyId === "wsr-demo-endpoint-preview" && r.methodologyVersion === "1.0.0" && r.methodologyDefinitionHash === RECEIPT_METHOD_HASH);
  hash(r.inputFingerprint); hash(r.ledgerFingerprint);
  requireThat(["D1", "W1", "M1", "M3", "M6", "Y1"].includes(String(r.horizon)));
  if (r.basisRevisionId === null) requireThat(r.basisRevisionSequence === null);
  else { text(r.basisRevisionId, 128); requireThat(CALL_ID.test(r.basisRevisionId) && Number.isSafeInteger(r.basisRevisionSequence) && Number(r.basisRevisionSequence) > 0); }
  requireThat(instant(r.basisEventTimeUtc) <= instant(r.evaluationAsOfUtc) && instant(r.evaluationAsOfUtc) <= instant(r.recordedAtUtc));
  requireThat(instant(r.evaluationAsOfUtc) === instant(r.evaluationAsOfKst, true) && instant(r.recordedAtUtc) === instant(r.recordedAtKst, true));
  metric(r.assetReturn, "assetReturn"); metric(r.directionalWin, "directionalWin"); metric(r.targetError, "targetError");
  return r as ScoringReceipt;
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
