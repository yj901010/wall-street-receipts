import masterDataFixtureJson from "../../../../../fixtures/v1/master-data.json";
import { readDataMode } from "@/lib/data-mode";
import type {
  InstitutionDirectoryIdentity,
  InstitutionDirectoryProvider,
  InstitutionDirectorySnapshot,
} from "./institution-directory-provider";

const documentFields = [
  "schemaVersion",
  "fixtureVersion",
  "dataMode",
  "generatedAt",
  "provenance",
  "institutions",
  "analysts",
  "analystEmployments",
  "assets",
] as const;
const provenanceFields = [
  "id",
  "sourceType",
  "sourcePaths",
  "capturedAt",
  "synthetic",
  "licenseClass",
] as const;
const institutionFields = [
  "institutionId",
  "canonicalName",
  "slug",
  "country",
  "active",
  "dataMode",
  "effectiveAt",
  "capturedAt",
  "provenanceId",
] as const;
const identifierPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const countryPattern = /^[A-Z]{2}$/;
const utcInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;
const expectedSourcePaths = [
  "docs/fixtures/institutions.json",
  "docs/docs/DOMAIN_MODEL.md",
] as const;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function assertExactFields(
  value: Record<string, unknown>,
  expected: readonly string[],
  owner: string,
) {
  const actual = Object.keys(value);
  if (
    actual.length !== expected.length ||
    actual.some((field) => !expected.some((candidate) => candidate === field))
  ) {
    throw new Error(`${owner} does not match the closed fixture shape.`);
  }
}

function fixtureString(
  value: Record<string, unknown>,
  field: string,
  owner: string,
  maximum = 256,
) {
  const candidate = value[field];
  if (
    typeof candidate !== "string" ||
    candidate.length === 0 ||
    candidate.length > maximum ||
    candidate.trim() !== candidate
  ) {
    throw new Error(`${owner} has an invalid ${field}.`);
  }
  return candidate;
}

function fixtureIdentifier(value: Record<string, unknown>, field: string, owner: string) {
  const candidate = fixtureString(value, field, owner, 128);
  if (!identifierPattern.test(candidate)) {
    throw new Error(`${owner} has an invalid ${field}.`);
  }
  return candidate;
}

function instant(value: string, owner: string) {
  const match = utcInstantPattern.exec(value);
  if (!match) {
    throw new Error(`${owner} has an invalid UTC instant: ${value}.`);
  }

  const parsed = Date.parse(`${match[1]}Z`);
  if (!Number.isFinite(parsed) || new Date(parsed).toISOString() !== `${match[1]}.000Z`) {
    throw new Error(`${owner} has an invalid UTC instant: ${value}.`);
  }

  const microseconds = (match[2] ?? "").padEnd(6, "0");
  return BigInt(parsed) * 1_000n + BigInt(microseconds || "0");
}

export function compareInstitutionIdentityCodePoints(left: string, right: string) {
  const leftCharacters = Array.from(left);
  const rightCharacters = Array.from(right);
  const comparedLength = Math.min(leftCharacters.length, rightCharacters.length);

  for (let index = 0; index < comparedLength; index += 1) {
    const leftPoint = leftCharacters[index].codePointAt(0)!;
    const rightPoint = rightCharacters[index].codePointAt(0)!;
    if (leftPoint !== rightPoint) return leftPoint < rightPoint ? -1 : 1;
  }

  return leftCharacters.length < rightCharacters.length
    ? -1
    : leftCharacters.length > rightCharacters.length
      ? 1
      : 0;
}

export function compareInstitutionDirectoryIdentities(
  left: Pick<InstitutionDirectoryIdentity, "canonicalName" | "institutionId">,
  right: Pick<InstitutionDirectoryIdentity, "canonicalName" | "institutionId">,
) {
  const nameOrder = compareInstitutionIdentityCodePoints(left.canonicalName, right.canonicalName);
  return nameOrder !== 0
    ? nameOrder
    : compareInstitutionIdentityCodePoints(left.institutionId, right.institutionId);
}

function fixtureInstitution(
  value: unknown,
  index: number,
  dataMode: string,
  provenanceId: string,
  provenanceCapturedAt: bigint,
): InstitutionDirectoryIdentity {
  const owner = `Institution fixture row ${index}`;
  if (!isRecord(value)) {
    throw new Error(`${owner} must be an object.`);
  }
  assertExactFields(value, institutionFields, owner);

  const institutionId = fixtureIdentifier(value, "institutionId", owner);
  const canonicalName = fixtureString(value, "canonicalName", owner);
  const slug = fixtureIdentifier(value, "slug", owner);
  const country = fixtureString(value, "country", owner, 2);
  const rowDataMode = fixtureString(value, "dataMode", owner);
  const effectiveAt = fixtureString(value, "effectiveAt", owner);
  const capturedAt = fixtureString(value, "capturedAt", owner);
  const rowProvenanceId = fixtureIdentifier(value, "provenanceId", owner);

  if (!countryPattern.test(country)) {
    throw new Error(`${owner} has an invalid country.`);
  }
  if (typeof value.active !== "boolean") {
    throw new Error(`${owner} has an invalid active marker.`);
  }
  if (rowDataMode !== dataMode || rowProvenanceId !== provenanceId) {
    throw new Error(`${owner} does not match the fixture mode and provenance.`);
  }

  const effectiveInstant = instant(effectiveAt, owner);
  const capturedInstant = instant(capturedAt, owner);
  if (effectiveInstant > capturedInstant || capturedInstant > provenanceCapturedAt) {
    throw new Error(`${owner} violates effective/captured chronology.`);
  }

  return {
    institutionId,
    canonicalName,
    slug,
    country,
    active: value.active,
    dataMode: readDataMode(rowDataMode),
    effectiveAt,
    capturedAt,
    provenanceId: rowProvenanceId,
  };
}

/** Maps the closed master-data fixture into the institution-only canonical read model. */
export function mapInstitutionDirectoryFixtureDocument(
  document: unknown,
): InstitutionDirectorySnapshot {
  if (!isRecord(document)) {
    throw new Error("Institution fixture document must be an object.");
  }
  assertExactFields(document, documentFields, "Institution fixture document");

  if (
    !isRecord(document.provenance) ||
    !Array.isArray(document.institutions) ||
    !Array.isArray(document.analysts) ||
    !Array.isArray(document.analystEmployments) ||
    !Array.isArray(document.assets)
  ) {
    throw new Error("Institution fixture document has an invalid envelope.");
  }
  assertExactFields(document.provenance, provenanceFields, "Institution fixture provenance");

  const schemaVersion = fixtureString(document, "schemaVersion", "Institution fixture document");
  const fixtureVersion = fixtureString(document, "fixtureVersion", "Institution fixture document");
  const dataMode = fixtureString(document, "dataMode", "Institution fixture document");
  const generatedAt = fixtureString(document, "generatedAt", "Institution fixture document");
  const provenanceId = fixtureIdentifier(document.provenance, "id", "Institution fixture provenance");
  const sourceType = fixtureString(document.provenance, "sourceType", "Institution fixture provenance");
  const capturedAt = fixtureString(document.provenance, "capturedAt", "Institution fixture provenance");
  const licenseClass = fixtureString(document.provenance, "licenseClass", "Institution fixture provenance");

  if (
    schemaVersion !== "1.0.0" ||
    fixtureVersion !== "v1" ||
    dataMode !== "DEMO" ||
    sourceType !== "LOCAL_SPECIFICATION" ||
    document.provenance.synthetic !== true ||
    licenseClass !== "INTERNAL_DEMO"
  ) {
    throw new Error("Institution fixture has unsupported version, mode, or DEMO provenance.");
  }

  if (!Array.isArray(document.provenance.sourcePaths) || document.provenance.sourcePaths.length === 0) {
    throw new Error("Institution fixture provenance has invalid source paths.");
  }
  const sourcePaths = document.provenance.sourcePaths.map((path, index) => {
    if (
      typeof path !== "string" ||
      path.length === 0 ||
      path.length > 256 ||
      path.trim() !== path
    ) {
      throw new Error(`Institution fixture provenance has an invalid source path at index ${index}.`);
    }
    return path;
  });
  if (new Set(sourcePaths).size !== sourcePaths.length) {
    throw new Error("Institution fixture provenance has duplicate source paths.");
  }
  if (
    sourcePaths.length !== expectedSourcePaths.length ||
    sourcePaths.some((path, index) => path !== expectedSourcePaths[index])
  ) {
    throw new Error("Institution fixture provenance does not match the committed source paths.");
  }

  const generatedInstant = instant(generatedAt, "Institution fixture document");
  const provenanceCapturedAt = instant(capturedAt, "Institution fixture provenance");
  if (provenanceCapturedAt > generatedInstant) {
    throw new Error("Institution fixture provenance was captured after document generation.");
  }

  const institutions = document.institutions.map((institution, index) =>
    fixtureInstitution(institution, index, dataMode, provenanceId, provenanceCapturedAt),
  );
  const ids = new Set<string>();
  const slugs = new Set<string>();
  const names = new Set<string>();
  for (const institution of institutions) {
    if (ids.has(institution.institutionId)) {
      throw new Error(`Duplicate institution ID: ${institution.institutionId}.`);
    }
    if (slugs.has(institution.slug)) {
      throw new Error(`Duplicate institution slug: ${institution.slug}.`);
    }
    if (names.has(institution.canonicalName)) {
      throw new Error(`Duplicate institution canonical name: ${institution.canonicalName}.`);
    }
    ids.add(institution.institutionId);
    slugs.add(institution.slug);
    names.add(institution.canonicalName);
  }
  institutions.sort(compareInstitutionDirectoryIdentities);

  return {
    schemaVersion: "1.0.0",
    fixtureVersion: "v1",
    dataMode: readDataMode(dataMode),
    generatedAt,
    provenance: {
      id: provenanceId,
      sourceType: "LOCAL_SPECIFICATION",
      sourcePaths,
      capturedAt,
      synthetic: true,
      licenseClass: "INTERNAL_DEMO",
    },
    institutions,
  };
}

export class FixtureInstitutionDirectoryProvider implements InstitutionDirectoryProvider {
  async directory(): Promise<InstitutionDirectorySnapshot> {
    return mapInstitutionDirectoryFixtureDocument(masterDataFixtureJson);
  }
}
