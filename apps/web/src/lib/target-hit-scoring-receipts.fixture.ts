import { RECEIPT_METHOD_HASH, WINDOW_ADJUSTMENT, WINDOW_ATTESTATION, type TargetHitScoringReceipt, type ReferenceEvidence } from "./target-hit-scoring-receipts";

/** Synthetic wire fixture, never a runtime fallback or an observed receipt. */
export function sampleReceipt(): TargetHitScoringReceipt {
  const evidence = (role: ReferenceEvidence["sourceBindingRole"], start: string, end: string): ReferenceEvidence => ({
    bindingId: "demo-binding", bindingProvenanceId: "demo-binding-provenance", sourceBindingRole: role, sourceBindingId: "demo-assignment-or-mapping",
    referenceAssetId: "demo-index-asset", providerId: "demo-provider", indexId: "demo-index", definitionRevision: "r1", currency: "USD",
    calendarId: "demo-calendar", calendarRevision: "r1", continuityEvidenceId: "demo-continuity", continuityProvenanceId: "demo-continuity-provenance",
    basisLevel: {observationId: "demo-start", providerEventId: "demo-start-event", value: start, observedAtUtc: "2026-01-05T15:00:00Z", sourceId: "demo-levels", sourceRevision: "r1", provenanceId: "demo-start-provenance"},
    endpointLevel: {observationId: "demo-end", providerEventId: "demo-end-event", value: end, observedAtUtc: "2026-01-05T21:00:00Z", sourceId: "demo-levels", sourceRevision: "r1", provenanceId: "demo-end-provenance"}
  });
  return { targetHit: {state: "AVAILABLE", decimalValue: null, booleanValue: true, reasons: []},
    windowEvidence: {
      attestationScope: WINDOW_ATTESTATION, selectedField: "HIGH", selectedValue: "160",
      target: {evidenceId:"demo-target", provenanceId:"demo-target-provenance", currency:"USD", adjustmentBasis:WINDOW_ADJUSTMENT, value:"150", availableAtUtc:"2026-01-05T15:00:00Z", capturedAtUtc:"2026-01-05T15:00:00Z"},
      binding: {bindingId:"demo-window-binding", revision:"r1", assetId:"demo-asset", primaryVenueId:"demo-venue", currency:"USD", priceSourceId:"demo-window-source", priceSourceRevision:"r1", provenanceId:"demo-window-binding-provenance", availableAtUtc:"2026-01-05T15:00:00Z", capturedAtUtc:"2026-01-05T15:00:00Z"},
      observation: {observationId:"demo-window", providerEventId:"demo-window-event", assetId:"demo-asset", venueId:"demo-venue", currency:"USD", priceSourceId:"demo-window-source", priceSourceRevision:"r1", provenanceId:"demo-window-provenance", calendarId:"demo-calendar", catalogRevision:"r1", orderedSessionIds:["demo-session"], lowerBoundUtc:"2026-01-05T15:00:00Z", lowerBoundType:"EXCLUSIVE", upperBoundUtc:"2026-01-05T21:00:00Z", upperBoundType:"INCLUSIVE", priceField:"PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR", coverageCompleteness:"EXACT_CAUSAL_WINDOW_SESSION_UNION", adjustmentBasis:WINDOW_ADJUSTMENT, corporateActionContinuity:"SPLIT_REVERSE_SPLIT_CONTINUOUS", availableAtUtc:"2026-01-05T21:00:00Z", capturedAtUtc:"2026-01-05T21:00:00Z", windowHigh:"160", windowLow:"80"}
    }, benchmarkReturn: {state: "AVAILABLE", decimalValue: "0.100000000000", booleanValue: null, reasons: []},
    sectorReturn: {state: "AVAILABLE", decimalValue: "-0.050000000000", booleanValue: null, reasons: []},
    benchmarkEvidence: evidence("BENCHMARK_ASSIGNMENT", "4000", "4400"), sectorEvidence: evidence("SECTOR_MAPPING", "2000", "1900"), receiptId: "00000000-0000-0000-0000-000000000001", callId: "demo-call", snapshotId: "snapshot-demo", snapshotProvenanceId: "snapshot-provenance",
    snapshotRole: "ORIGINAL_CALL_CONTEXT_ONLY_NOT_PRICE_SOURCE", basisRevisionId: null, basisRevisionSequence: null, basisEventTimeUtc: "2026-01-05T15:00:00Z",
    methodologyId: "wsr-demo-target-hit-preview", methodologyVersion: "1.0.0", methodologyDefinitionHash: RECEIPT_METHOD_HASH,
    inputFingerprint: "a".repeat(64), ledgerFingerprint: "b".repeat(64), evaluationAsOfUtc: "2026-01-06T00:00:00Z", evaluationAsOfKst: "2026-01-06T09:00+09:00",
    recordedAtUtc: "2026-01-06T00:01:00.123456Z", recordedAtKst: "2026-01-06T09:01:00.123456+09:00", dataMode: "DEMO", scope: "PARTIAL_TARGET_HIT", dataComplete: false,
    source: "PERSISTED_DEMO_INPUT_REPLAY", termsProvenanceId: "terms-provenance", horizon: "D1",
    assetReturn: {state: "AVAILABLE", decimalValue: "0.200000000000", booleanValue: null, reasons: []},
    directionalWin: {state: "AVAILABLE", decimalValue: null, booleanValue: true, reasons: []},
    targetError: {state: "AVAILABLE", decimalValue: "0.250000000000", booleanValue: null, reasons: []} };
}
export function samplePage(items = [sampleReceipt()]) { return {dataMode: "DEMO", source: "PERSISTED_DEMO_INPUT_REPLAY", limit: 20, hasMore: false, items}; }
