import revisionsFixtureJson from "../../../../../fixtures/v1/analyst-call-revisions.json";
import outcomesFixtureJson from "../../../../../fixtures/v1/call-outcomes.json";
import { describe, expect, it } from "vitest";
import { FixtureCallsProvider } from "./fixture-calls-provider";
import { FixtureCallAuditProvider } from "./fixture-call-audit-provider";

function fixture(
  revisions: unknown = revisionsFixtureJson,
  outcomes: unknown = outcomesFixtureJson,
) {
  return new FixtureCallAuditProvider(
    new FixtureCallsProvider(),
    structuredClone(revisions),
    structuredClone(outcomes),
  );
}

describe("FixtureCallAuditProvider", () => {
  it("returns one coherent detail/context/revision/outcome aggregate", async () => {
    const audit = await fixture().findById("demo-call-002");

    expect(audit?.detail.call.callId).toBe("demo-call-002");
    expect(audit?.context).toEqual({ macroSnapshot: null, eventContext: null });
    expect(audit?.revisions.map(({ revisionId }) => revisionId)).toEqual([
      "demo-call-revision-001",
      "demo-call-revision-002",
    ]);
    expect(audit?.revisions.every(({ dataMode }) => dataMode === "DEMO")).toBe(true);
    expect(audit?.outcomes).toEqual([]);
  });

  it("normalizes a fixture copy to Spring order while preserving the append-only outcome records", async () => {
    const document = structuredClone(outcomesFixtureJson);
    const before = JSON.stringify(document);
    const audit = await fixture(revisionsFixtureJson, document).findById("demo-call-001");

    expect(audit?.revisions).toEqual([]);
    expect(audit?.outcomes.map(({ outcomeId }) => outcomeId)).toEqual([
      "outcome-demo-call-001-d1-v1-001",
      "outcome-demo-call-001-d1-v1-002",
      "outcome-demo-call-001-d1-v2-001",
      "outcome-demo-call-001-m1-v1-001",
    ]);
    expect(JSON.stringify(document)).toBe(before);
  });

  it("distinguishes a known-empty outcome response from an unknown call", async () => {
    await expect(fixture().findById("demo-call-002")).resolves.toMatchObject({ outcomes: [] });
    await expect(fixture().findById("missing-call")).resolves.toBeNull();
  });

  it.each([
    ["fixture version", (document: typeof revisionsFixtureJson) => { document.fixtureVersion = "v2"; }],
    ["provenance ID", (document: typeof revisionsFixtureJson) => { document.provenance.id = "other"; }],
    ["source type", (document: typeof revisionsFixtureJson) => { document.provenance.sourceType = "OTHER"; }],
    ["source path order", (document: typeof revisionsFixtureJson) => { document.provenance.sourcePaths.reverse(); }],
    ["license", (document: typeof revisionsFixtureJson) => { document.provenance.licenseClass = "OTHER"; }],
    ["disclaimer", (document: typeof revisionsFixtureJson) => { document.disclaimer = "Changed"; }],
  ])("rejects drift in locked %s evidence", async (_label, mutate) => {
    const document = structuredClone(revisionsFixtureJson);
    mutate(document);
    await expect(fixture(document).findById("demo-call-002")).rejects.toThrow();
  });

  it("uses microsecond precision for envelope and record chronology", async () => {
    const envelopeFuture = structuredClone(revisionsFixtureJson);
    envelopeFuture.generatedAt = "2026-08-18T00:00:00.000001Z";
    envelopeFuture.provenance.capturedAt = "2026-08-18T00:00:00.000002Z";
    await expect(fixture(envelopeFuture).findById("demo-call-002")).rejects.toThrow(
      /must not postdate generation/,
    );

    const revisionFuture = structuredClone(revisionsFixtureJson);
    revisionFuture.generatedAt = "2026-08-11T15:02:00.000002Z";
    revisionFuture.provenance.capturedAt = "2026-08-11T15:02:00Z";
    revisionFuture.revisions[1]!.capturedAt = "2026-08-11T15:02:00.000001Z";
    await expect(fixture(revisionFuture).findById("demo-call-002")).rejects.toThrow(
      /postdates fixture provenance capture/,
    );
  });

  it("rejects revision records for calls outside the canonical call fixture", async () => {
    const document = structuredClone(revisionsFixtureJson);
    document.revisions[0]!.callId = "unknown-call";
    await expect(fixture(document).findById("demo-call-002")).rejects.toThrow(/unknown call/);
  });

  it.each([
    ["fixture version", (document: typeof outcomesFixtureJson) => { document.fixtureVersion = "v2"; }],
    ["provenance ID", (document: typeof outcomesFixtureJson) => { document.provenance.id = "other"; }],
    ["source type", (document: typeof outcomesFixtureJson) => { document.provenance.sourceType = "OTHER"; }],
    ["source path order", (document: typeof outcomesFixtureJson) => { document.provenance.sourcePaths.reverse(); }],
    ["license", (document: typeof outcomesFixtureJson) => { document.provenance.licenseClass = "OTHER"; }],
    ["disclaimer", (document: typeof outcomesFixtureJson) => { document.disclaimer = "Changed"; }],
  ])("rejects drift in locked outcome %s evidence", async (_label, mutate) => {
    const document = structuredClone(outcomesFixtureJson);
    mutate(document);
    await expect(fixture(revisionsFixtureJson, document).findById("demo-call-001")).rejects.toThrow();
  });

  it("validates outcome envelope and record chronology with microsecond precision", async () => {
    const envelopeFuture = structuredClone(outcomesFixtureJson);
    envelopeFuture.generatedAt = "2026-08-18T00:00:00.000001Z";
    envelopeFuture.provenance.capturedAt = "2026-08-18T00:00:00.000002Z";
    await expect(fixture(revisionsFixtureJson, envelopeFuture).findById("demo-call-001")).rejects.toThrow(
      /must not postdate generation/,
    );

    const outcomeFuture = structuredClone(outcomesFixtureJson);
    outcomeFuture.generatedAt = "2026-08-18T00:09:59.999999Z";
    await expect(fixture(revisionsFixtureJson, outcomeFuture).findById("demo-call-001")).rejects.toThrow(
      /postdates fixture generation/,
    );
  });

  it("requires each outcome to match one model-only methodology definition available by processing time", async () => {
    const wrongHash = structuredClone(outcomesFixtureJson);
    wrongHash.outcomes[0]!.methodologyDefinitionHash = "b".repeat(64);
    await expect(fixture(revisionsFixtureJson, wrongHash).findById("demo-call-001")).rejects.toThrow(
      /matching methodology definition|definition hash/,
    );

    const activeClaim = structuredClone(outcomesFixtureJson);
    activeClaim.methodologies[0]!.status = "ACTIVE";
    await expect(fixture(revisionsFixtureJson, activeClaim).findById("demo-call-001")).rejects.toThrow(
      /invalid fixture evidence/,
    );

    const futureMethodology = structuredClone(outcomesFixtureJson);
    futureMethodology.methodologies[0]!.effectiveAt = "2026-08-12T00:06:00Z";
    futureMethodology.methodologies[0]!.capturedAt = "2026-08-12T00:06:00Z";
    await expect(fixture(revisionsFixtureJson, futureMethodology).findById("demo-call-001")).rejects.toThrow(
      /predates its methodology evidence/,
    );
  });

  it("rejects outcome records for calls outside the canonical call fixture", async () => {
    const document = structuredClone(outcomesFixtureJson);
    document.outcomes[0]!.callId = "unknown-call";
    await expect(fixture(revisionsFixtureJson, document).findById("demo-call-001")).rejects.toThrow(/unknown call/);
  });
});
