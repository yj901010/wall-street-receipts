import revisionsFixtureJson from "../../../../../fixtures/v1/analyst-call-revisions.json";
import { describe, expect, it } from "vitest";
import {
  adaptCallContextResponse,
  adaptCallDetailResponse,
  adaptCallRevisionsResponse,
  validateCallAuditSnapshot,
} from "./call-audit-adapter";
import type { CallAuditSnapshot } from "./call-audit-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";

function clone<T>(value: T): T {
  return structuredClone(value);
}

async function fixtureAudit(callId = "demo-call-002"): Promise<CallAuditSnapshot> {
  const calls = new FixtureCallsProvider();
  const detail = await calls.findById(callId);
  const context = await calls.findContextByCallId(callId);
  if (!detail || !context) throw new Error("Test fixture call is unavailable.");
  const revisionPayload = revisionsFixtureJson.revisions.filter((revision) => revision.callId === callId);
  return {
    detail: adaptCallDetailResponse(clone(detail)),
    context: adaptCallContextResponse(clone(context)),
    revisions: adaptCallRevisionsResponse(clone(revisionPayload), callId),
  };
}

describe("call audit adapters", () => {
  it("accepts the exact closed Spring response shapes and preserves nullable evidence", async () => {
    const audit = validateCallAuditSnapshot(await fixtureAudit());

    expect(audit.detail.call.callId).toBe("demo-call-002");
    expect(audit.context).toEqual({ macroSnapshot: null, eventContext: null });
    expect(audit.revisions.map(({ revisionId }) => revisionId)).toEqual([
      "demo-call-revision-001",
      "demo-call-revision-002",
    ]);
    expect(audit.revisions[1]?.correctedTerms).toBeNull();
  });

  it("adapts response documents without mutating transport-owned payloads", async () => {
    const calls = new FixtureCallsProvider();
    const detail = clone(await calls.findById("demo-call-002"));
    const context = clone(await calls.findContextByCallId("demo-call-002"));
    const revisions = clone(revisionsFixtureJson.revisions);
    const before = JSON.stringify({ detail, context, revisions });

    adaptCallDetailResponse(detail);
    adaptCallContextResponse(context);
    adaptCallRevisionsResponse(revisions, "demo-call-002");

    expect(JSON.stringify({ detail, context, revisions })).toBe(before);
  });

  it("preserves every nullable detail field instead of filling missing evidence", async () => {
    const calls = new FixtureCallsProvider();
    const detail = await calls.findById("demo-call-003");
    if (!detail) throw new Error("Expected nullable detail fixture.");

    const adapted = adaptCallDetailResponse(clone(detail));
    expect(adapted.analyst).toBeNull();
    expect(adapted.call).toMatchObject({
      analystId: null,
      originalRating: null,
      previousTarget: null,
      target: null,
      currency: null,
      targetDate: null,
    });
    expect(adapted.source.document).toMatchObject({
      publisher: null,
      canonicalUrl: null,
      publishedAt: null,
      externalId: null,
      contentHash: null,
    });
    expect(adapted.source.reference).toMatchObject({
      page: null,
      startMs: null,
      endMs: null,
      extractedFragment: null,
      extractionConfidence: null,
    });
    expect(adapted.snapshot).toBeNull();
  });

  it("rejects additive detail, context, revision, and corrected-term fields", async () => {
    const audit = await fixtureAudit();
    const detail = clone(audit.detail) as unknown as Record<string, unknown>;
    detail.extra = true;
    expect(() => adaptCallDetailResponse(detail)).toThrow(/exactly/);

    expect(() => adaptCallContextResponse({ ...clone(audit.context), extra: true })).toThrow(/exactly/);

    const revisions = clone(revisionsFixtureJson.revisions) as Array<Record<string, unknown>>;
    revisions[0]!.extra = true;
    expect(() => adaptCallRevisionsResponse(revisions, "demo-call-002")).toThrow(/exactly/);

    const corrected = clone(revisionsFixtureJson.revisions) as Array<Record<string, unknown>>;
    (corrected[0]!.correctedTerms as Record<string, unknown>).extra = true;
    expect(() => adaptCallRevisionsResponse(corrected, "demo-call-002")).toThrow(/exactly/);
  });

  it("rejects missing, mistyped, invalid-enum, invalid-date, and invalid-instant detail evidence", async () => {
    const calls = new FixtureCallsProvider();
    const detail = await calls.findById("demo-call-002");
    if (!detail) throw new Error("Expected detail fixture.");

    const missing = clone(detail) as unknown as Record<string, unknown>;
    delete (missing.call as Record<string, unknown>).provider;
    expect(() => adaptCallDetailResponse(missing)).toThrow(/exactly/);

    const mistyped = clone(detail) as unknown as { source: { reference: { verified: unknown } } };
    mistyped.source.reference.verified = "true";
    expect(() => adaptCallDetailResponse(mistyped)).toThrow(/must be a boolean/);

    const invalidEnum = clone(detail) as unknown as { call: { direction: unknown } };
    invalidEnum.call.direction = "UP";
    expect(() => adaptCallDetailResponse(invalidEnum)).toThrow(/must be one of/);

    const invalidDate = clone(detail) as unknown as { call: { targetDate: unknown } };
    invalidDate.call.targetDate = "2026-02-30";
    expect(() => adaptCallDetailResponse(invalidDate)).toThrow(/real calendar date/);

    const invalidInstant = clone(detail) as unknown as { call: { eventTime: unknown } };
    invalidInstant.call.eventTime = "2026-08-11T14:20:00.0000001Z";
    expect(() => adaptCallDetailResponse(invalidInstant)).toThrow(/microsecond precision/);
  });

  it("locks lineage and permits the same event ID under distinct providers in the provider-event identity tuple", () => {
    const sameExternalId = clone(revisionsFixtureJson.revisions);
    sameExternalId[1]!.provider = "second-fixture-provider";
    sameExternalId[1]!.providerEventId = sameExternalId[0]!.providerEventId;
    expect(adaptCallRevisionsResponse(sameExternalId, "demo-call-002")).toHaveLength(2);

    const duplicateTuple = clone(sameExternalId);
    duplicateTuple[1]!.provider = duplicateTuple[0]!.provider;
    expect(() => adaptCallRevisionsResponse(duplicateTuple, "demo-call-002")).toThrow(
      /duplicates a provider event ID/,
    );

    const gap = clone(revisionsFixtureJson.revisions);
    gap[1]!.sequenceNumber = 3;
    expect(() => adaptCallRevisionsResponse(gap, "demo-call-002")).toThrow(/sequence gap/);

    const afterCancellation = clone(revisionsFixtureJson.revisions) as Array<Record<string, unknown>>;
    afterCancellation.push({
      ...clone(afterCancellation[0]!),
      revisionId: "demo-call-revision-003",
      providerEventId: "fixture-call-revision-003",
      sequenceNumber: 3,
      supersedesRevisionId: "demo-call-revision-002",
      eventTime: "2026-08-11T15:10:00Z",
      processingTime: "2026-08-11T15:11:00Z",
      capturedAt: "2026-08-11T15:11:00Z",
    });
    expect(() => adaptCallRevisionsResponse(afterCancellation, "demo-call-002")).toThrow(
      /terminal cancellation/,
    );
  });

  it("enforces root, duplicate, correction, cancellation, enum, and nullable-term revision rules", () => {
    const rootParent = clone(revisionsFixtureJson.revisions);
    rootParent[0]!.supersedesRevisionId = "unexpected-parent";
    expect(() => adaptCallRevisionsResponse(rootParent, "demo-call-002")).toThrow(/immediately preceding/);

    const duplicateId = clone(revisionsFixtureJson.revisions);
    duplicateId[1]!.revisionId = duplicateId[0]!.revisionId;
    expect(() => adaptCallRevisionsResponse(duplicateId, "demo-call-002")).toThrow(/duplicates a revision ID/);

    const correctionWithoutTerms = clone(revisionsFixtureJson.revisions) as Array<Record<string, unknown>>;
    correctionWithoutTerms[0]!.correctedTerms = null;
    expect(() => adaptCallRevisionsResponse(correctionWithoutTerms, "demo-call-002")).toThrow(
      /CORRECTION/,
    );

    const cancellationWithTerms = clone(revisionsFixtureJson.revisions) as Array<Record<string, unknown>>;
    cancellationWithTerms[1]!.correctedTerms = clone(cancellationWithTerms[0]!.correctedTerms);
    expect(() => adaptCallRevisionsResponse(cancellationWithTerms, "demo-call-002")).toThrow(
      /CANCELLATION/,
    );

    const invalidType = clone(revisionsFixtureJson.revisions) as Array<Record<string, unknown>>;
    invalidType[0]!.revisionType = "UPDATE";
    expect(() => adaptCallRevisionsResponse(invalidType, "demo-call-002")).toThrow(/must be one of/);

    const nullableTerms = clone(revisionsFixtureJson.revisions) as Array<Record<string, unknown>>;
    nullableTerms.splice(1);
    nullableTerms[0]!.correctedTerms = {
      direction: "NEUTRAL",
      originalRating: null,
      previousTarget: null,
      target: null,
      currency: null,
      targetDate: null,
    };
    expect(adaptCallRevisionsResponse(nullableTerms, "demo-call-002")[0]?.correctedTerms).toEqual(
      nullableTerms[0]!.correctedTerms,
    );
    expect(adaptCallRevisionsResponse([], "demo-call-002")).toEqual([]);
  });

  it("requires only eventTime to be nondecreasing across records", () => {
    const payload = clone(revisionsFixtureJson.revisions);
    payload[0]!.eventTime = "2026-08-11T15:00:00Z";
    payload[0]!.processingTime = "2026-08-11T15:10:00Z";
    payload[0]!.capturedAt = "2026-08-11T15:20:00Z";
    payload[1]!.eventTime = "2026-08-11T15:00:00Z";
    payload[1]!.processingTime = "2026-08-11T15:05:00Z";
    payload[1]!.capturedAt = "2026-08-11T15:06:00Z";
    expect(adaptCallRevisionsResponse(payload, "demo-call-002")).toHaveLength(2);

    payload[1]!.eventTime = "2026-08-11T14:59:59Z";
    expect(() => adaptCallRevisionsResponse(payload, "demo-call-002")).toThrow(/earlier than the prior/);
  });

  it("rejects revisions before the original event", async () => {
    const predating = await fixtureAudit();
    const revisions = clone(predating.revisions) as typeof predating.revisions;
    (revisions[0] as { eventTime: string }).eventTime = "2026-08-11T14:19:59Z";
    expect(() => validateCallAuditSnapshot({ ...predating, revisions })).toThrow(/predates/);
  });

  it.each(["REALTIME", "DELAYED", "EOD"] as const)(
    "rejects the base call mode when every dependent surface consistently uses %s",
    async (mode) => {
      const nonDemo = clone(await fixtureAudit("demo-call-001"));
      nonDemo.detail.call.dataMode = mode;
      nonDemo.detail.source.document.dataMode = mode;
      nonDemo.detail.source.reference.dataMode = mode;
      if (nonDemo.detail.snapshot) nonDemo.detail.snapshot.dataMode = mode;
      if (nonDemo.context.macroSnapshot) {
        nonDemo.context.macroSnapshot.dataMode = mode;
        for (const observation of nonDemo.context.macroSnapshot.observations) observation.dataMode = mode;
      }
      if (nonDemo.context.eventContext) nonDemo.context.eventContext.dataMode = mode;
      for (const revision of nonDemo.revisions) revision.dataMode = mode;
      expect(() => validateCallAuditSnapshot(nonDemo)).toThrow(/must remain DEMO/);
    },
  );

  it("rejects a non-DEMO mode independently on every audit surface", async () => {
    const nestedCases: Array<{
      name: string;
      mutate: (audit: CallAuditSnapshot) => void;
      message: RegExp;
    }> = [
      {
        name: "base call",
        mutate: (audit) => {
          audit.detail.call.dataMode = "REALTIME";
        },
        message: /source document data-mode mismatch/,
      },
      {
        name: "source document",
        mutate: (audit) => {
          audit.detail.source.document.dataMode = "REALTIME";
        },
        message: /source document data-mode mismatch/,
      },
      {
        name: "source reference",
        mutate: (audit) => {
          audit.detail.source.reference.dataMode = "REALTIME";
        },
        message: /source reference data-mode mismatch/,
      },
      {
        name: "market snapshot",
        mutate: (audit) => {
          if (!audit.detail.snapshot) throw new Error("Expected market snapshot.");
          audit.detail.snapshot.dataMode = "REALTIME";
        },
        message: /snapshot identity, event-time, or data-mode mismatch/,
      },
      {
        name: "macro snapshot",
        mutate: (audit) => {
          if (!audit.context.macroSnapshot) throw new Error("Expected macro snapshot.");
          audit.context.macroSnapshot.dataMode = "REALTIME";
        },
        message: /macro snapshot identity, event-time, or data-mode mismatch/,
      },
      {
        name: "event context",
        mutate: (audit) => {
          if (!audit.context.eventContext) throw new Error("Expected event context.");
          audit.context.eventContext.dataMode = "REALTIME";
        },
        message: /event context identity, event-time, or data-mode mismatch/,
      },
    ];

    for (const nestedCase of nestedCases) {
      const audit = clone(await fixtureAudit("demo-call-001"));
      nestedCase.mutate(audit);
      expect(() => validateCallAuditSnapshot(audit), nestedCase.name).toThrow(nestedCase.message);
    }

    const macroAudit = clone(await fixtureAudit("demo-call-001"));
    if (!macroAudit.context.macroSnapshot) throw new Error("Expected macro snapshot.");
    for (let index = 0; index < macroAudit.context.macroSnapshot.observations.length; index += 1) {
      const observationAudit = clone(await fixtureAudit("demo-call-001"));
      if (!observationAudit.context.macroSnapshot) throw new Error("Expected macro snapshot.");
      observationAudit.context.macroSnapshot.observations[index]!.dataMode = "REALTIME";
      expect(() => validateCallAuditSnapshot(observationAudit), `macro observation ${index}`).toThrow(
        /macro observation data-mode mismatch/,
      );
    }

    const revisionAudit = clone(await fixtureAudit("demo-call-002"));
    revisionAudit.revisions[0]!.dataMode = "REALTIME";
    expect(() => validateCallAuditSnapshot(revisionAudit)).toThrow(/data-mode mismatch with the call/);
  });

  it("requires the closed context object while preserving its explicit known-empty state", () => {
    expect(() => adaptCallContextResponse(null)).toThrow(/must be an object/);
    expect(adaptCallContextResponse({ macroSnapshot: null, eventContext: null })).toEqual({
      macroSnapshot: null,
      eventContext: null,
    });
  });

  it("accepts inclusive macro vintage bounds and permits earnings before the call event", async () => {
    const calls = new FixtureCallsProvider();
    const context = clone(await calls.findContextByCallId("demo-call-001"));
    if (!context?.macroSnapshot || !context.eventContext) throw new Error("Expected populated context.");
    context.macroSnapshot.observations[0]!.vintageStart = "2026-08-10";
    context.macroSnapshot.observations[0]!.vintageEnd = "2026-08-10";
    context.eventContext.earningsAt = "2026-08-09T12:00:00Z";

    const adapted = adaptCallContextResponse(context);
    expect(adapted.macroSnapshot?.observations[0]).toMatchObject({
      vintageStart: "2026-08-10",
      vintageEnd: "2026-08-10",
    });
    expect(adapted.eventContext?.earningsAt).toBe("2026-08-09T12:00:00Z");
  });

  it("preserves independent provenance IDs while enforcing documented joins", async () => {
    const audit = clone(await fixtureAudit());
    audit.detail.source.document.provenanceId = "independent-document-provenance";
    audit.detail.source.reference.provenanceId = "independent-reference-provenance";
    expect(validateCallAuditSnapshot(audit)).toBe(audit);

    audit.detail.source.reference.sourceDocumentId = "different-document";
    expect(() => validateCallAuditSnapshot(audit)).toThrow(/source-document join mismatch/);

    const snapshotMismatch = clone(await fixtureAudit());
    if (!snapshotMismatch.detail.snapshot) throw new Error("Expected snapshot.");
    snapshotMismatch.detail.snapshot.callId = "different-call";
    expect(() => validateCallAuditSnapshot(snapshotMismatch)).toThrow(/snapshot identity/);
  });

  it("rejects non-positive asset prices and invalid macro vintage/schedule bounds", async () => {
    const populated = new FixtureCallsProvider();
    const detail = clone(await populated.findById("demo-call-002"));
    if (!detail?.snapshot) throw new Error("Expected market snapshot.");
    detail.snapshot.assetPrice = -1;
    expect(() => adaptCallDetailResponse(detail)).toThrow(/must be positive/);

    const context = clone(await populated.findContextByCallId("demo-call-001"));
    if (!context?.macroSnapshot || !context.eventContext) throw new Error("Expected populated context.");
    context.macroSnapshot.observations[0]!.vintageStart = "2026-08-11";
    expect(() => adaptCallContextResponse(context)).toThrow(/active on the snapshot event date/);

    const schedule = clone(await populated.findContextByCallId("demo-call-001"));
    if (!schedule?.eventContext) throw new Error("Expected scheduled context.");
    schedule.eventContext.nextCpiAt = "2026-08-10T11:59:59Z";
    expect(() => adaptCallContextResponse(schedule)).toThrow(/must not precede eventTime/);
  });

  it.each([1e26, -1e26, 1e100, Number.POSITIVE_INFINITY])(
    "rejects macro values outside the canonical magnitude bound (%s)",
    async (value) => {
    const calls = new FixtureCallsProvider();
    const context = clone(await calls.findContextByCallId("demo-call-001"));
    if (!context?.macroSnapshot) throw new Error("Expected populated macro context.");
    context.macroSnapshot.observations[0]!.value = value;
    expect(() => adaptCallContextResponse(context)).toThrow(/finite number|absolute magnitude below 1e26/);
    },
  );
});
