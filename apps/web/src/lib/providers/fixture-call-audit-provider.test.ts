import revisionsFixtureJson from "../../../../../fixtures/v1/analyst-call-revisions.json";
import { describe, expect, it } from "vitest";
import { FixtureCallsProvider } from "./fixture-calls-provider";
import { FixtureCallAuditProvider } from "./fixture-call-audit-provider";

function fixture(value: unknown = revisionsFixtureJson) {
  return new FixtureCallAuditProvider(new FixtureCallsProvider(), structuredClone(value));
}

describe("FixtureCallAuditProvider", () => {
  it("returns one coherent detail/context/revision aggregate", async () => {
    const audit = await fixture().findById("demo-call-002");

    expect(audit?.detail.call.callId).toBe("demo-call-002");
    expect(audit?.context).toEqual({ macroSnapshot: null, eventContext: null });
    expect(audit?.revisions.map(({ revisionId }) => revisionId)).toEqual([
      "demo-call-revision-001",
      "demo-call-revision-002",
    ]);
    expect(audit?.revisions.every(({ dataMode }) => dataMode === "DEMO")).toBe(true);
  });

  it("distinguishes a known-empty revision response from an unknown call", async () => {
    await expect(fixture().findById("demo-call-001")).resolves.toMatchObject({ revisions: [] });
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
});
