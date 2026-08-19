import masterDataFixture from "../../../../../fixtures/v1/master-data.json";
import { describe, expect, it } from "vitest";
import {
  compareInstitutionDirectoryIdentities,
  compareInstitutionIdentityCodePoints,
  FixtureInstitutionDirectoryProvider,
  mapInstitutionDirectoryFixtureDocument,
} from "./fixture-institution-directory-provider";

function fixtureDocument() {
  return structuredClone(masterDataFixture);
}

type FixtureDocument = ReturnType<typeof fixtureDocument>;

describe("FixtureInstitutionDirectoryProvider", () => {
  it("preserves the exact identity evidence and sorts by code point rather than performance", async () => {
    const snapshot = await new FixtureInstitutionDirectoryProvider().directory();

    expect(Object.keys(snapshot)).toEqual([
      "schemaVersion",
      "fixtureVersion",
      "dataMode",
      "generatedAt",
      "provenance",
      "institutions",
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
    expect(snapshot.institutions).toEqual([
      {
        institutionId: "inst-gs",
        canonicalName: "Goldman Sachs",
        slug: "goldman-sachs",
        country: "US",
        active: true,
        dataMode: "DEMO",
        effectiveAt: "2026-08-10T00:00:00Z",
        capturedAt: "2026-08-18T00:00:00Z",
        provenanceId: "fixture-master-data-v1",
      },
      {
        institutionId: "inst-jpm",
        canonicalName: "JPMorgan",
        slug: "jpmorgan",
        country: "US",
        active: true,
        dataMode: "DEMO",
        effectiveAt: "2026-08-10T00:00:00Z",
        capturedAt: "2026-08-18T00:00:00Z",
        provenanceId: "fixture-master-data-v1",
      },
    ]);
    expect(snapshot.institutions.every((institution) =>
      !("score" in institution) && !("accuracy" in institution) && !("rank" in institution)
    )).toBe(true);
  });

  it("compares full Unicode code points rather than UTF-16 code units", () => {
    const bmpPrivateUse = "\uE000";
    const astral = "\u{10000}";

    expect(bmpPrivateUse < astral).toBe(false);
    expect(compareInstitutionIdentityCodePoints(bmpPrivateUse, astral)).toBeLessThan(0);
    expect(compareInstitutionIdentityCodePoints(`${astral}b`, `${astral}a`)).toBeGreaterThan(0);
    expect(compareInstitutionIdentityCodePoints("same", "same")).toBe(0);
    expect(compareInstitutionDirectoryIdentities(
      { canonicalName: "Same", institutionId: "inst-b" },
      { canonicalName: "Same", institutionId: "inst-a" },
    )).toBeGreaterThan(0);
  });

  it("accepts an explicit empty identity collection without placeholders", () => {
    const document = fixtureDocument();
    document.institutions = [];

    expect(mapInstitutionDirectoryFixtureDocument(document).institutions).toEqual([]);
  });

  it("sorts a reversed input without mutating the source document", () => {
    const document = fixtureDocument();
    document.institutions.reverse();
    const before = structuredClone(document);

    const snapshot = mapInstitutionDirectoryFixtureDocument(document);

    expect(snapshot.institutions.map(({ institutionId }) => institutionId)).toEqual([
      "inst-gs",
      "inst-jpm",
    ]);
    expect(document).toEqual(before);
  });

  it("accepts and deterministically places a later valid identity", () => {
    const document = fixtureDocument();
    document.institutions.push({
      ...structuredClone(document.institutions[0]),
      institutionId: "inst-apex",
      canonicalName: "Apex Research",
      slug: "apex-research",
      country: "GB",
    });

    const snapshot = mapInstitutionDirectoryFixtureDocument(document);

    expect(snapshot.institutions.map(({ institutionId }) => institutionId)).toEqual([
      "inst-apex",
      "inst-gs",
      "inst-jpm",
    ]);
    expect(snapshot.institutions[0]).toEqual({
      institutionId: "inst-apex",
      canonicalName: "Apex Research",
      slug: "apex-research",
      country: "GB",
      active: true,
      dataMode: "DEMO",
      effectiveAt: "2026-08-10T00:00:00Z",
      capturedAt: "2026-08-18T00:00:00Z",
      provenanceId: "fixture-master-data-v1",
    });
  });

  it.each<[string, (document: FixtureDocument) => void, RegExp]>([
    [
      "an extra envelope field",
      (document) => Object.assign(document, { ranking: [] }),
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
        (document as unknown as { analysts: unknown }).analysts = {};
      },
      /invalid envelope/i,
    ],
    [
      "an extra institution field",
      (document) => Object.assign(document.institutions[0], { accuracy: 1 }),
      /closed fixture shape/i,
    ],
    [
      "an invalid institution ID",
      (document) => {
        document.institutions[0].institutionId = "INST JPM";
      },
      /invalid institutionId/i,
    ],
    [
      "an invalid institution slug",
      (document) => {
        document.institutions[0].slug = "JPMorgan Bank";
      },
      /invalid slug/i,
    ],
    [
      "a non-boolean active marker",
      (document) => {
        (document.institutions[0] as unknown as { active: unknown }).active = "true";
      },
      /invalid active marker/i,
    ],
    [
      "a padded canonical name",
      (document) => {
        document.institutions[0].canonicalName = " JPMorgan";
      },
      /invalid canonicalName/i,
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
      "a duplicate institution ID",
      (document) => {
        document.institutions[1].institutionId = document.institutions[0].institutionId;
      },
      /duplicate institution ID/i,
    ],
    [
      "a duplicate institution slug",
      (document) => {
        document.institutions[1].slug = document.institutions[0].slug;
      },
      /duplicate institution slug/i,
    ],
    [
      "a duplicate institution canonical name",
      (document) => {
        document.institutions[1].canonicalName = document.institutions[0].canonicalName;
      },
      /duplicate institution canonical name/i,
    ],
    [
      "a mismatched row mode",
      (document) => {
        document.institutions[0].dataMode = "EOD";
      },
      /does not match the fixture mode and provenance/i,
    ],
    [
      "a mismatched row provenance",
      (document) => {
        document.institutions[0].provenanceId = "fixture-other-v1";
      },
      /does not match the fixture mode and provenance/i,
    ],
    [
      "a noncanonical UTC instant",
      (document) => {
        document.institutions[0].effectiveAt = "2026-08-10T00:00:00.0000001Z";
      },
      /invalid UTC instant/i,
    ],
    [
      "a row captured before its effective time",
      (document) => {
        document.institutions[0].effectiveAt = "2026-08-18T00:00:00.000001Z";
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
    [
      "an invalid country",
      (document) => {
        document.institutions[0].country = "USA";
      },
      /invalid country/i,
    ],
  ])("rejects %s", (_label, mutate, expectedError) => {
    const document = fixtureDocument();
    mutate(document);

    expect(() => mapInstitutionDirectoryFixtureDocument(document)).toThrow(expectedError);
  });
});
