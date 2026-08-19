import masterDataFixture from "../../../../../fixtures/v1/master-data.json";
import { describe, expect, it } from "vitest";
import {
  compareAnalystDirectoryIdentities,
  compareAnalystIdentityCodePoints,
  FixtureAnalystDirectoryProvider,
  mapAnalystDirectoryFixtureDocument,
} from "./fixture-analyst-directory-provider";

function fixtureDocument() {
  return structuredClone(masterDataFixture);
}

type FixtureDocument = ReturnType<typeof fixtureDocument>;

describe("FixtureAnalystDirectoryProvider", () => {
  it("preserves the exact analyst evidence in canonical code-point order", async () => {
    const snapshot = await new FixtureAnalystDirectoryProvider().directory();

    expect(Object.keys(snapshot)).toEqual([
      "schemaVersion",
      "fixtureVersion",
      "dataMode",
      "generatedAt",
      "provenance",
      "analysts",
    ]);
    expect(Object.keys(snapshot.provenance)).toEqual([
      "id",
      "sourceType",
      "sourcePaths",
      "capturedAt",
      "synthetic",
      "licenseClass",
    ]);
    expect(snapshot).toMatchObject({
      schemaVersion: "1.0.0",
      fixtureVersion: "v1",
      dataMode: "DEMO",
      generatedAt: "2026-08-18T00:00:00Z",
      provenance: {
        id: "fixture-master-data-v1",
        sourceType: "LOCAL_SPECIFICATION",
        sourcePaths: [
          "docs/fixtures/institutions.json",
          "docs/docs/DOMAIN_MODEL.md",
        ],
        capturedAt: "2026-08-18T00:00:00Z",
        synthetic: true,
        licenseClass: "INTERNAL_DEMO",
      },
    });
    expect(snapshot.analysts).toEqual([
      {
        analystId: "analyst-demo-a",
        canonicalName: "Demo Analyst A",
        active: true,
        dataMode: "DEMO",
        effectiveAt: "2026-08-10T00:00:00Z",
        capturedAt: "2026-08-18T00:00:00Z",
        provenanceId: "fixture-master-data-v1",
      },
      {
        analystId: "analyst-demo-b",
        canonicalName: "Demo Analyst B",
        active: true,
        dataMode: "DEMO",
        effectiveAt: "2026-08-10T00:00:00Z",
        capturedAt: "2026-08-18T00:00:00Z",
        provenanceId: "fixture-master-data-v1",
      },
    ]);
    for (const analyst of snapshot.analysts) {
      expect(Object.keys(analyst)).toEqual([
        "analystId",
        "canonicalName",
        "active",
        "dataMode",
        "effectiveAt",
        "capturedAt",
        "provenanceId",
      ]);
      expect(analyst).not.toHaveProperty("institutionId");
      expect(analyst).not.toHaveProperty("employment");
      expect(analyst).not.toHaveProperty("callCount");
      expect(analyst).not.toHaveProperty("score");
      expect(analyst).not.toHaveProperty("rank");
    }
  });

  it("compares full Unicode code points with analyst ID as the equal-name tie break", () => {
    const bmpPrivateUse = "\uE000";
    const astral = "\u{10000}";

    expect(bmpPrivateUse < astral).toBe(false);
    expect(compareAnalystIdentityCodePoints(bmpPrivateUse, astral)).toBeLessThan(0);
    expect(compareAnalystIdentityCodePoints(`${astral}b`, `${astral}a`)).toBeGreaterThan(0);
    expect(compareAnalystIdentityCodePoints("same", "same")).toBe(0);
    expect(compareAnalystDirectoryIdentities(
      { canonicalName: "Same", analystId: "analyst-b" },
      { canonicalName: "Same", analystId: "analyst-a" },
    )).toBeGreaterThan(0);
  });

  it("accepts an explicit empty analyst collection without placeholders", () => {
    const document = fixtureDocument();
    document.analysts = [];

    expect(mapAnalystDirectoryFixtureDocument(document).analysts).toEqual([]);
  });

  it("sorts reversed input without mutating the source document", () => {
    const document = fixtureDocument();
    document.analysts.reverse();
    const before = structuredClone(document);

    const snapshot = mapAnalystDirectoryFixtureDocument(document);

    expect(snapshot.analysts.map(({ analystId }) => analystId)).toEqual([
      "analyst-demo-a",
      "analyst-demo-b",
    ]);
    expect(document).toEqual(before);
  });

  it("accepts and deterministically places a later valid identity", () => {
    const document = fixtureDocument();
    document.analysts.push({
      ...structuredClone(document.analysts[0]),
      analystId: "analyst-apex",
      canonicalName: "Apex Analyst",
    });

    const snapshot = mapAnalystDirectoryFixtureDocument(document);

    expect(snapshot.analysts.map(({ analystId }) => analystId)).toEqual([
      "analyst-apex",
      "analyst-demo-a",
      "analyst-demo-b",
    ]);
    expect(snapshot.analysts[0]).toEqual({
      analystId: "analyst-apex",
      canonicalName: "Apex Analyst",
      active: true,
      dataMode: "DEMO",
      effectiveAt: "2026-08-10T00:00:00Z",
      capturedAt: "2026-08-18T00:00:00Z",
      provenanceId: "fixture-master-data-v1",
    });
  });

  it("allows equal canonical names and orders them by analyst ID", () => {
    const document = fixtureDocument();
    document.analysts[0].canonicalName = "Same Analyst";
    document.analysts[1].canonicalName = "Same Analyst";
    document.analysts.reverse();

    expect(mapAnalystDirectoryFixtureDocument(document).analysts.map(({ analystId }) => analystId))
      .toEqual(["analyst-demo-a", "analyst-demo-b"]);
  });

  it.each<[string, (document: FixtureDocument) => void, RegExp]>([
    [
      "an extra envelope field",
      (document) => Object.assign(document, { rankings: [] }),
      /closed fixture shape/i,
    ],
    [
      "a missing envelope collection",
      (document) => Reflect.deleteProperty(document, "analystEmployments"),
      /closed fixture shape/i,
    ],
    [
      "a non-array unprojected collection",
      (document) => {
        (document as unknown as { institutions: unknown }).institutions = {};
      },
      /invalid envelope/i,
    ],
    [
      "an extra analyst field",
      (document) => Object.assign(document.analysts[0], { institutionId: "inst-jpm" }),
      /closed fixture shape/i,
    ],
    [
      "an invalid analyst ID",
      (document) => {
        document.analysts[0].analystId = "Analyst Demo A";
      },
      /invalid analystId/i,
    ],
    [
      "a padded canonical name",
      (document) => {
        document.analysts[0].canonicalName = " Demo Analyst A";
      },
      /invalid canonicalName/i,
    ],
    [
      "a non-boolean active marker",
      (document) => {
        (document.analysts[0] as unknown as { active: unknown }).active = "true";
      },
      /invalid active marker/i,
    ],
    [
      "a whitespace-only source path",
      (document) => {
        document.provenance.sourcePaths[0] = "   ";
      },
      /invalid source path/i,
    ],
    [
      "a reordered source path list",
      (document) => {
        document.provenance.sourcePaths.reverse();
      },
      /does not match the committed source paths/i,
    ],
    [
      "a replaced source path",
      (document) => {
        document.provenance.sourcePaths[0] = "docs/fixtures/other.json";
      },
      /does not match the committed source paths/i,
    ],
    [
      "a duplicate analyst ID",
      (document) => {
        document.analysts[1].analystId = document.analysts[0].analystId;
      },
      /duplicate analyst ID/i,
    ],
    [
      "a mismatched row mode",
      (document) => {
        document.analysts[0].dataMode = "EOD";
      },
      /does not match the fixture mode and provenance/i,
    ],
    [
      "a mismatched row provenance",
      (document) => {
        document.analysts[0].provenanceId = "fixture-other-v1";
      },
      /does not match the fixture mode and provenance/i,
    ],
    [
      "a noncanonical UTC instant",
      (document) => {
        document.analysts[0].effectiveAt = "2026-08-10T00:00:00.0000001Z";
      },
      /invalid UTC instant/i,
    ],
    [
      "a row captured before its effective time",
      (document) => {
        document.analysts[0].effectiveAt = "2026-08-18T00:00:00.000001Z";
      },
      /violates effective\/captured chronology/i,
    ],
    [
      "a row captured after the provenance envelope",
      (document) => {
        document.analysts[0].capturedAt = "2026-08-18T00:00:00.000001Z";
      },
      /violates effective\/captured chronology/i,
    ],
    [
      "a provenance capture after generation",
      (document) => {
        document.provenance.capturedAt = "2026-08-18T00:00:00.000001Z";
      },
      /captured after document generation/i,
    ],
    [
      "non-DEMO provenance",
      (document) => {
        document.provenance.synthetic = false;
      },
      /unsupported version, mode, or DEMO provenance/i,
    ],
  ])("rejects %s", (_label, mutate, expectedError) => {
    const document = fixtureDocument();
    mutate(document);

    expect(() => mapAnalystDirectoryFixtureDocument(document)).toThrow(expectedError);
  });
});
