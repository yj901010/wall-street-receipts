import callOutcomesFixture from "../../../../../fixtures/v1/call-outcomes.json";
import { describe, expect, it } from "vitest";
import {
  FixtureMethodologyProvider,
  mapMethodologyFixtureDocument,
} from "./fixture-methodology-provider";

function fixtureDocument() {
  return structuredClone(callOutcomesFixture);
}

type FixtureDocument = ReturnType<typeof fixtureDocument>;

describe("FixtureMethodologyProvider", () => {
  const provider = new FixtureMethodologyProvider();

  it("preserves the canonical fixture order and every evidence field", async () => {
    const catalog = await provider.catalog();

    expect(catalog).toMatchObject({
      asOf: "2026-08-18T00:10:00Z",
      dataMode: "DEMO",
      source: "fixture-call-outcomes-v1",
    });
    expect(catalog.items.map(({ methodologyVersion }) => methodologyVersion)).toEqual([
      "1.0.0",
      "2.0.0",
    ]);
    expect(catalog.items[0]).toEqual({
      methodologyId: "standard-call-outcome",
      methodologyVersion: "1.0.0",
      schemaVersion: "1.0.0",
      definitionHash: "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
      status: "MODEL_ONLY",
      effectiveAt: "2026-08-10T00:00:00Z",
      dataMode: "DEMO",
      capturedAt: "2026-08-10T00:00:00Z",
      provenanceId: "fixture-call-outcomes-v1",
    });
    expect(catalog.items[1]).toEqual({
      methodologyId: "standard-call-outcome",
      methodologyVersion: "2.0.0",
      schemaVersion: "1.0.0",
      definitionHash: "256056d7cb2b292a1ec0bd7b905f856134bb38851a65b8a2fceaca41489db3e8",
      status: "MODEL_ONLY",
      effectiveAt: "2026-08-18T00:00:00Z",
      dataMode: "DEMO",
      capturedAt: "2026-08-18T00:00:00Z",
      provenanceId: "fixture-call-outcomes-v1",
    });
  });

  it("exposes registry metadata without calculating outcome values", async () => {
    const catalog = await provider.catalog();

    expect(catalog.items).toHaveLength(2);
    expect(catalog.items.every(({ status }) => status === "MODEL_ONLY")).toBe(true);
    for (const methodology of catalog.items) {
      expect(methodology).not.toHaveProperty("alpha");
      expect(methodology).not.toHaveProperty("targetHit");
      expect(methodology).not.toHaveProperty("directionalWin");
      expect(methodology.definitionHash).toMatch(/^[0-9a-f]{64}$/);
    }
  });

  it.each<[
    string,
    (document: FixtureDocument) => void,
    RegExp,
  ]>([
    [
      "a malformed record",
      (document) => Reflect.deleteProperty(document.methodologies[0], "definitionHash"),
      /does not match the methodology record shape/i,
    ],
    [
      "a duplicate identity",
      (document) => {
        document.methodologies[1].methodologyVersion = "1.0.0";
      },
      /duplicate fixture methodology identity/i,
    ],
    [
      "an invalid definition hash",
      (document) => {
        document.methodologies[0].definitionHash = "not-a-sha256";
      },
      /invalid definition hash/i,
    ],
    [
      "an unsupported status",
      (document) => {
        document.methodologies[0].status = "CURRENT";
      },
      /unsupported methodology status/i,
    ],
    [
      "an invalid instant",
      (document) => {
        document.methodologies[0].effectiveAt = "2026-08-10 00:00:00";
      },
      /invalid UTC instant/i,
    ],
    [
      "a record captured after the provenance envelope",
      (document) => {
        document.methodologies[0].capturedAt = "2026-08-18T00:00:00.000001Z";
      },
      /captured after its provenance envelope/i,
    ],
    [
      "a mismatched provenance identity",
      (document) => {
        document.methodologies[0].provenanceId = "fixture-other-source";
      },
      /inconsistent provenance/i,
    ],
    [
      "non-DEMO provenance",
      (document) => {
        document.provenance.synthetic = false;
      },
      /inconsistent DEMO provenance/i,
    ],
  ])("rejects %s", (_label, mutate, expectedError) => {
    const document = fixtureDocument();
    mutate(document);

    expect(() => mapMethodologyFixtureDocument(document)).toThrow(expectedError);
  });
});
