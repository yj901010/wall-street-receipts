import outcomesFixtureJson from "../../../../../fixtures/v1/call-outcomes.json";
import revisionsFixtureJson from "../../../../../fixtures/v1/analyst-call-revisions.json";
import { describe, expect, it } from "vitest";
import {
  adaptCallContextResponse,
  adaptCallDetailResponse,
  adaptCallOutcomesResponse,
  adaptCallRevisionsResponse,
  validateCallAuditSnapshot,
} from "./call-audit-adapter";
import type { CallAuditSnapshot } from "./call-audit-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";

type MutableOutcome = Record<string, unknown>;

const metricFields = [
  "assetReturn", "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha", "mfe", "mae",
  "targetHit", "directionalWin", "targetError",
] as const;

function clone<T>(value: T): T {
  return structuredClone(value);
}

function orderedFixtureOutcomes(): MutableOutcome[] {
  const [d1v1First, d1v1Second, m1v1First, d1v2First] = clone(outcomesFixtureJson.outcomes);
  return [d1v1First!, d1v1Second!, d1v2First!, m1v1First!];
}

function payload(): MutableOutcome[] {
  return orderedFixtureOutcomes();
}

function rootOutcome(overrides: MutableOutcome): MutableOutcome {
  return Object.assign(clone(outcomesFixtureJson.outcomes[0]) as unknown as MutableOutcome, overrides);
}

async function aggregate(
  callId: "demo-call-001" | "demo-call-002",
  rawOutcomes: readonly unknown[],
): Promise<CallAuditSnapshot> {
  const calls = new FixtureCallsProvider();
  const detail = await calls.findById(callId);
  const context = await calls.findContextByCallId(callId);
  if (!detail || !context) throw new Error("Expected aggregate fixture evidence.");
  const revisions = revisionsFixtureJson.revisions.filter((revision) => revision.callId === callId);
  return {
    detail: adaptCallDetailResponse(clone(detail)),
    context: adaptCallContextResponse(clone(context)),
    revisions: adaptCallRevisionsResponse(clone(revisions), callId),
    outcomes: adaptCallOutcomesResponse(clone(rawOutcomes), callId),
  };
}

async function outcomeForRevisedCall() {
  const calls = new FixtureCallsProvider();
  const detail = await calls.findById("demo-call-002");
  if (!detail?.snapshot) throw new Error("Expected revised call snapshot.");
  const outcome = clone(outcomesFixtureJson.outcomes[0]) as unknown as MutableOutcome;
  Object.assign(outcome, {
    outcomeId: "outcome-demo-call-002-d1-v1-001",
    callId: "demo-call-002",
    basisRevisionId: "demo-call-revision-001",
    snapshotId: detail.snapshot.snapshotId,
    inputFingerprint: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    eventTime: "2026-08-11T15:01:00Z",
    processingTime: "2026-08-11T15:02:00Z",
    capturedAt: "2026-08-11T15:02:00Z",
    provenanceId: "independent-outcome-provenance",
  });
  return outcome;
}

describe("call outcome adapter", () => {
  it("adapts the exact 31-field response without mutating or reordering transport evidence", () => {
    const input = payload();
    const before = JSON.stringify(input);
    const adapted = adaptCallOutcomesResponse(input, "demo-call-001");

    expect(adapted.map(({ outcomeId }) => outcomeId)).toEqual([
      "outcome-demo-call-001-d1-v1-001",
      "outcome-demo-call-001-d1-v1-002",
      "outcome-demo-call-001-d1-v2-001",
      "outcome-demo-call-001-m1-v1-001",
    ]);
    expect(JSON.stringify(input)).toBe(before);
    expect(adapted[0]).not.toBe(input[0]);
    expect(Object.keys(adapted[0] ?? {})).toHaveLength(31);
    expect(adaptCallOutcomesResponse([], "demo-call-002")).toEqual([]);
  });

  it("rejects missing, additive, mistyped, invalid ID, enum, hash, and instant evidence", () => {
    const mutations: Array<[string, (outcome: MutableOutcome) => void, RegExp]> = [
      ["missing", (outcome) => { delete outcome.provenanceId; }, /exactly/],
      ["additive", (outcome) => { outcome.extra = true; }, /exactly/],
      ["mistyped", (outcome) => { outcome.sequenceNumber = "1"; }, /integer/],
      ["schema version", (outcome) => { outcome.schemaVersion = "2.0.0"; }, /must equal 1.0.0/],
      ["identifier", (outcome) => { outcome.outcomeId = "bad id"; }, /opaque identifier/],
      ["horizon", (outcome) => { outcome.horizon = "D2"; }, /must be one of/],
      ["status", (outcome) => { outcome.evaluationStatus = "UNKNOWN"; }, /must be one of/],
      ["reason", (outcome) => { outcome.reasonCode = "OTHER"; }, /must be one of/],
      ["methodology version", (outcome) => { outcome.methodologyVersion = "bad version"; }, /methodology version/],
      ["definition hash", (outcome) => { outcome.methodologyDefinitionHash = "A".repeat(64); }, /lowercase SHA-256/],
      ["fingerprint", (outcome) => { outcome.inputFingerprint = "f".repeat(63); }, /lowercase SHA-256/],
      ["instant", (outcome) => { outcome.capturedAt = "2026-08-11T20:01:00.0000001Z"; }, /microsecond/],
    ];

    for (const [label, mutate, message] of mutations) {
      const input = payload();
      mutate(input[0]!);
      expect(() => adaptCallOutcomesResponse(input, "demo-call-001"), label).toThrow(message);
    }
    expect(() => adaptCallOutcomesResponse(null, "demo-call-001")).toThrow(/must be an array/);
  });

  it.each(metricFields)("rejects non-null %s instead of publishing lossy calculated evidence", (field) => {
    const input = payload();
    input[0]![field] = field === "targetHit" || field === "directionalWin" ? false : 0;
    expect(() => adaptCallOutcomesResponse(input, "demo-call-001")).toThrow(
      /must remain JSON null in the P2 audit-only boundary/,
    );
  });

  it("rejects coherent CALCULATED and EXCLUDED projections at the P2 publication guard", () => {
    const calculated = payload();
    Object.assign(calculated[0]!, {
      evaluationStatus: "CALCULATED",
      reasonCode: null,
      dataComplete: true,
    });
    expect(() => adaptCallOutcomesResponse(calculated, "demo-call-001")).toThrow(
      /must remain PENDING or INCOMPLETE/,
    );

    const excluded = payload();
    Object.assign(excluded[0]!, {
      evaluationStatus: "EXCLUDED",
      reasonCode: "CALL_CANCELLED",
      cancellationRevisionId: "demo-call-revision-002",
      dataComplete: false,
    });
    expect(() => adaptCallOutcomesResponse(excluded, "demo-call-001")).toThrow(
      /must remain PENDING or INCOMPLETE/,
    );
  });

  it("requires the exact PENDING/INCOMPLETE reason and incomplete-state fields", () => {
    const wrongPendingReason = payload();
    wrongPendingReason[3]!.reasonCode = "HORIZON_DATA_MISSING";
    expect(() => adaptCallOutcomesResponse(wrongPendingReason, "demo-call-001")).toThrow(
      /HORIZON_NOT_REACHED/,
    );

    const wrongIncompleteReason = payload();
    wrongIncompleteReason[0]!.reasonCode = "HORIZON_NOT_REACHED";
    expect(() => adaptCallOutcomesResponse(wrongIncompleteReason, "demo-call-001")).toThrow(
      /HORIZON_DATA_MISSING/,
    );

    const complete = payload();
    complete[0]!.dataComplete = true;
    expect(() => adaptCallOutcomesResponse(complete, "demo-call-001")).toThrow(/must remain false/);

    const cancellation = payload();
    cancellation[0]!.cancellationRevisionId = "demo-call-revision-002";
    expect(() => adaptCallOutcomesResponse(cancellation, "demo-call-001")).toThrow(/must remain JSON null/);
  });

  it.each(["REALTIME", "DELAYED", "EOD"])("rejects a coherent %s outcome response", (mode) => {
    const input = payload();
    for (const outcome of input) outcome.dataMode = mode;
    expect(() => adaptCallOutcomesResponse(input, "demo-call-001")).toThrow(/must remain DEMO/);
  });

  it("rejects duplicate record and natural identities while allowing a fingerprint under another method version", () => {
    const duplicateId = payload();
    duplicateId[1]!.outcomeId = duplicateId[0]!.outcomeId;
    expect(() => adaptCallOutcomesResponse(duplicateId, "demo-call-001")).toThrow(/duplicates an outcome ID/);

    const duplicateNaturalIdentity = payload();
    duplicateNaturalIdentity[1]!.inputFingerprint = duplicateNaturalIdentity[0]!.inputFingerprint;
    expect(() => adaptCallOutcomesResponse(duplicateNaturalIdentity, "demo-call-001")).toThrow(
      /duplicates a natural outcome identity/,
    );

    const distinctMethodVersion = payload();
    distinctMethodVersion[2]!.inputFingerprint = distinctMethodVersion[1]!.inputFingerprint;
    expect(adaptCallOutcomesResponse(distinctMethodVersion, "demo-call-001")).toHaveLength(4);
  });

  it("rejects conflicting hashes for one methodology identity", () => {
    const input = payload();
    input[1]!.methodologyDefinitionHash = "b".repeat(64);
    expect(() => adaptCallOutcomesResponse(input, "demo-call-001")).toThrow(/definition hash/);
  });

  it("locks each scoped lineage root, sequence, predecessor, and three monotonic timestamps", () => {
    const gap = payload();
    gap[1]!.sequenceNumber = 3;
    expect(() => adaptCallOutcomesResponse(gap, "demo-call-001")).toThrow(/sequence gap/);

    const predecessor = payload();
    predecessor[1]!.supersedesOutcomeId = "other-outcome";
    expect(() => adaptCallOutcomesResponse(predecessor, "demo-call-001")).toThrow(/immediate lineage predecessor/);

    for (const field of ["eventTime", "processingTime", "capturedAt"] as const) {
      const backwards = payload();
      backwards[1]![field] = "2026-08-11T19:59:59Z";
      if (field === "processingTime") backwards[1]!.eventTime = "2026-08-11T19:59:58Z";
      if (field === "capturedAt") {
        backwards[1]!.eventTime = "2026-08-11T19:59:57Z";
        backwards[1]!.processingTime = "2026-08-11T19:59:58Z";
      }
      expect(() => adaptCallOutcomesResponse(backwards, "demo-call-001"), field).toThrow(
        /moves a lineage timestamp backwards/,
      );
    }
  });

  it("does not impose timestamp monotonicity across unrelated lineages", () => {
    const input = payload();
    Object.assign(input[2]!, {
      eventTime: "2026-08-11T20:00:00Z",
      processingTime: "2026-08-11T20:00:01Z",
      capturedAt: "2026-08-11T20:00:01Z",
    });
    expect(adaptCallOutcomesResponse(input, "demo-call-001")).toHaveLength(4);
  });

  it("validates and preserves every server-order key without sorting the response", () => {
    for (const swap of [[0, 1], [1, 2], [2, 3]] as const) {
      const input = payload();
      [input[swap[0]], input[swap[1]]] = [input[swap[1]]!, input[swap[0]]!];
      const before = input.map((item) => item.outcomeId);
      expect(() => adaptCallOutcomesResponse(input, "demo-call-001")).toThrow(
        /deterministic server order|lineage sequence gap/,
      );
      expect(input.map((item) => item.outcomeId)).toEqual(before);
    }
  });

  it("locks methodologyId final tie order with a mutation-sensitive response pair", () => {
    const first = rootOutcome({
      outcomeId: "outcome-methodology-Z",
      methodologyId: "Z-methodology",
      inputFingerprint: "a".repeat(64),
    });
    const second = rootOutcome({
      outcomeId: "outcome-methodology-a",
      methodologyId: "a-methodology",
      inputFingerprint: "b".repeat(64),
    });
    expect(adaptCallOutcomesResponse([first, second], "demo-call-001")).toHaveLength(2);
    expect(() => adaptCallOutcomesResponse([second, first], "demo-call-001")).toThrow(
      /deterministic server order/,
    );
  });

  it("locks methodologyVersion as raw lexical text rather than semantic-version order", () => {
    const lexicalFirst = rootOutcome({
      outcomeId: "outcome-version-10",
      methodologyVersion: "10.0.0",
      inputFingerprint: "a".repeat(64),
    });
    const lexicalSecond = rootOutcome({
      outcomeId: "outcome-version-2",
      methodologyVersion: "2.0.0",
      inputFingerprint: "b".repeat(64),
    });
    expect(adaptCallOutcomesResponse([lexicalFirst, lexicalSecond], "demo-call-001"))
      .toHaveLength(2);
    expect(() => adaptCallOutcomesResponse([lexicalSecond, lexicalFirst], "demo-call-001")).toThrow(
      /deterministic server order/,
    );
  });

  it("locks outcomeId final tie order across independent basis lineages", () => {
    const first = rootOutcome({
      outcomeId: "Z-outcome-basis-001",
      basisRevisionId: "basis-Z",
      inputFingerprint: "a".repeat(64),
    });
    const second = rootOutcome({
      outcomeId: "a-outcome-basis-001",
      basisRevisionId: "basis-a",
      inputFingerprint: "b".repeat(64),
    });
    expect(adaptCallOutcomesResponse([first, second], "demo-call-001")).toHaveLength(2);
    expect(() => adaptCallOutcomesResponse([second, first], "demo-call-001")).toThrow(
      /deterministic server order/,
    );
  });

  it("accepts a valid distinct-basis lineage interleave without folding either lineage", () => {
    const firstA = rootOutcome({
      outcomeId: "outcome-basis-a-001",
      basisRevisionId: "basis-a",
      inputFingerprint: "a".repeat(64),
    });
    const firstB = rootOutcome({
      outcomeId: "outcome-basis-b-001",
      basisRevisionId: "basis-b",
      inputFingerprint: "b".repeat(64),
    });
    const secondTemplate = clone(outcomesFixtureJson.outcomes[1]) as unknown as MutableOutcome;
    const secondA = Object.assign(clone(secondTemplate), {
      outcomeId: "outcome-basis-a-002",
      basisRevisionId: "basis-a",
      supersedesOutcomeId: "outcome-basis-a-001",
      inputFingerprint: "c".repeat(64),
    });
    const secondB = Object.assign(clone(secondTemplate), {
      outcomeId: "outcome-basis-b-002",
      basisRevisionId: "basis-b",
      supersedesOutcomeId: "outcome-basis-b-001",
      inputFingerprint: "d".repeat(64),
    });
    expect(adaptCallOutcomesResponse(
      [firstA, firstB, secondA, secondB],
      "demo-call-001",
    ).map(({ outcomeId }) => outcomeId)).toEqual([
      "outcome-basis-a-001",
      "outcome-basis-b-001",
      "outcome-basis-a-002",
      "outcome-basis-b-002",
    ]);
  });

  it("accepts every closed horizon in canonical server order", () => {
    const horizons = ["D1", "W1", "M1", "M3", "M6", "Y1"];
    const input = horizons.map((horizon, index) => rootOutcome({
      outcomeId: `outcome-horizon-${String(index + 1)}`,
      horizon,
      inputFingerprint: `${index + 1}`.repeat(64),
    }));
    expect(adaptCallOutcomesResponse(input, "demo-call-001").map(({ horizon }) => horizon)).toEqual(horizons);
  });

  it("joins outcome event, processing, capture, snapshot, and independent provenance point-in-time evidence", async () => {
    const audit = await aggregate("demo-call-001", payload());
    audit.detail.call.provenanceId = "independent-detail-provenance";
    audit.detail.source.document.provenanceId = "independent-document-provenance";
    audit.outcomes[0]!.provenanceId = "independent-outcome-provenance";
    expect(validateCallAuditSnapshot(audit)).toBe(audit);

    const beforeEvent = clone(audit);
    beforeEvent.outcomes[0]!.eventTime = "2026-08-09T23:59:59Z";
    beforeEvent.outcomes[0]!.processingTime = "2026-08-11T20:01:00Z";
    expect(() => validateCallAuditSnapshot(beforeEvent)).toThrow(/predates the original call evidence/);

    const beforeCallCapture = clone(audit);
    beforeCallCapture.outcomes[0]!.processingTime = "2026-08-10T12:01:00Z";
    beforeCallCapture.outcomes[0]!.capturedAt = "2026-08-10T12:01:00Z";
    expect(() => validateCallAuditSnapshot(beforeCallCapture)).toThrow(/predates the original call evidence/);

    const wrongSnapshot = clone(audit);
    wrongSnapshot.outcomes[0]!.snapshotId = "other-snapshot";
    expect(() => validateCallAuditSnapshot(wrongSnapshot)).toThrow(/snapshot join mismatch/);
  });

  it("preserves a null snapshot ID as explicit nullable evidence without inventing a join", async () => {
    const input = payload();
    input[0]!.snapshotId = null;
    const audit = await aggregate("demo-call-001", input);
    expect(validateCallAuditSnapshot(audit).outcomes[0]?.snapshotId).toBeNull();
  });

  it("rejects each snapshot availability timestamp independently when unavailable by outcome processing", async () => {
    const baseline = await aggregate("demo-call-001", payload());
    if (!baseline.detail.snapshot) throw new Error("Expected outcome snapshot evidence.");
    for (const field of ["processingTime", "capturedAt"] as const) {
      const audit = clone(baseline);
      audit.detail.snapshot![field] = "2026-08-11T20:01:00.000001Z";
      expect(() => validateCallAuditSnapshot(audit), field).toThrow(/snapshot.*not available/);
    }
  });

  it("requires a same-call timely CORRECTION for a non-null basis without projecting it as effective", async () => {
    const rawOutcome = await outcomeForRevisedCall();
    const audit = await aggregate("demo-call-002", [rawOutcome]);
    expect(validateCallAuditSnapshot(audit).outcomes[0]?.basisRevisionId).toBe("demo-call-revision-001");

    const cancellationBasis = clone(audit);
    cancellationBasis.outcomes[0]!.basisRevisionId = "demo-call-revision-002";
    expect(() => validateCallAuditSnapshot(cancellationBasis)).toThrow(/same-call CORRECTION/);

    const futureBasis = clone(audit);
    futureBasis.outcomes[0]!.processingTime = "2026-08-11T14:39:59Z";
    futureBasis.outcomes[0]!.capturedAt = "2026-08-11T14:39:59Z";
    expect(() => validateCallAuditSnapshot(futureBasis)).toThrow(/basis revision.*not available/);
  });

});
