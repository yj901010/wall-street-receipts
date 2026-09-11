import { RECEIPT_METHOD_HASH, type ScoringReceipt } from "./scoring-receipts";

/** Synthetic wire fixture, never a runtime fallback or an observed receipt. */
export function sampleReceipt(): ScoringReceipt {
  return { receiptId: "00000000-0000-0000-0000-000000000001", callId: "demo-call", snapshotId: "snapshot-demo", snapshotProvenanceId: "snapshot-provenance",
    snapshotRole: "ORIGINAL_CALL_CONTEXT_ONLY_NOT_PRICE_SOURCE", basisRevisionId: null, basisRevisionSequence: null, basisEventTimeUtc: "2026-01-05T15:00:00Z",
    methodologyId: "wsr-demo-endpoint-preview", methodologyVersion: "1.0.0", methodologyDefinitionHash: RECEIPT_METHOD_HASH,
    inputFingerprint: "a".repeat(64), ledgerFingerprint: "b".repeat(64), evaluationAsOfUtc: "2026-01-06T00:00:00Z", evaluationAsOfKst: "2026-01-06T09:00+09:00",
    recordedAtUtc: "2026-01-06T00:01:00.123456Z", recordedAtKst: "2026-01-06T09:01:00.123456+09:00", dataMode: "DEMO", scope: "PARTIAL_ENDPOINT", dataComplete: false,
    source: "PERSISTED_DEMO_INPUT_REPLAY", termsProvenanceId: "terms-provenance", horizon: "D1",
    assetReturn: {state: "AVAILABLE", decimalValue: "0.200000000000", booleanValue: null, reasons: []},
    directionalWin: {state: "AVAILABLE", decimalValue: null, booleanValue: true, reasons: []},
    targetError: {state: "AVAILABLE", decimalValue: "0.250000000000", booleanValue: null, reasons: []} };
}
export function samplePage(items = [sampleReceipt()]) { return {dataMode: "DEMO", source: "PERSISTED_DEMO_INPUT_REPLAY", limit: 20, hasMore: false, items}; }
