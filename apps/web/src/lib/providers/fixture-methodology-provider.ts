import callOutcomesFixtureJson from "../../../../../fixtures/v1/call-outcomes.json";
import { DATA_MODES, readDataMode } from "@/lib/data-mode";
import {
  METHODOLOGY_STATUSES,
  type MethodologyCatalog,
  type MethodologyProvider,
  type MethodologyStatus,
  type ScoringMethodology,
} from "./methodology-provider";

type MethodologyFixture = Omit<ScoringMethodology, "dataMode" | "status"> & {
  dataMode: string;
  status: string;
};

type CallOutcomesFixture = {
  schemaVersion: string;
  dataMode: string;
  generatedAt: string;
  provenance: {
    id: string;
    sourceType: string;
    capturedAt: string;
    synthetic: boolean;
    licenseClass: string;
  };
  methodologies: MethodologyFixture[];
  disclaimer: string;
};

const sha256Pattern = /^[0-9a-f]{64}$/;
const utcInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;
const methodologyFields = [
  "methodologyId",
  "methodologyVersion",
  "schemaVersion",
  "definitionHash",
  "status",
  "effectiveAt",
  "dataMode",
  "capturedAt",
  "provenanceId",
] as const;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function fixtureString(record: Record<string, unknown>, field: string, ownerId: string) {
  const value = record[field];

  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`Fixture ${ownerId} has a missing or invalid ${field}.`);
  }

  return value;
}

function methodologyFixture(value: unknown, index: number): MethodologyFixture {
  const ownerId = `methodology at index ${index}`;

  if (!isRecord(value)) {
    throw new Error(`Fixture ${ownerId} must be an object.`);
  }

  const unexpectedFields = Object.keys(value).filter(
    (field) => !methodologyFields.some((expected) => expected === field),
  );

  if (unexpectedFields.length > 0 || Object.keys(value).length !== methodologyFields.length) {
    throw new Error(`Fixture ${ownerId} does not match the methodology record shape.`);
  }

  return {
    methodologyId: fixtureString(value, "methodologyId", ownerId),
    methodologyVersion: fixtureString(value, "methodologyVersion", ownerId),
    schemaVersion: fixtureString(value, "schemaVersion", ownerId) as "1.0.0",
    definitionHash: fixtureString(value, "definitionHash", ownerId),
    status: fixtureString(value, "status", ownerId),
    effectiveAt: fixtureString(value, "effectiveAt", ownerId),
    dataMode: fixtureString(value, "dataMode", ownerId),
    capturedAt: fixtureString(value, "capturedAt", ownerId),
    provenanceId: fixtureString(value, "provenanceId", ownerId),
  };
}

function callOutcomesFixture(value: unknown): CallOutcomesFixture {
  if (!isRecord(value) || !isRecord(value.provenance) || !Array.isArray(value.methodologies)) {
    throw new Error("Methodology fixture document has an invalid envelope.");
  }

  if (typeof value.provenance.synthetic !== "boolean") {
    throw new Error("Methodology fixture provenance has an invalid synthetic marker.");
  }

  return {
    schemaVersion: fixtureString(value, "schemaVersion", "document"),
    dataMode: fixtureString(value, "dataMode", "document"),
    generatedAt: fixtureString(value, "generatedAt", "document"),
    provenance: {
      id: fixtureString(value.provenance, "id", "document provenance"),
      sourceType: fixtureString(value.provenance, "sourceType", "document provenance"),
      capturedAt: fixtureString(value.provenance, "capturedAt", "document provenance"),
      synthetic: value.provenance.synthetic,
      licenseClass: fixtureString(value.provenance, "licenseClass", "document provenance"),
    },
    methodologies: value.methodologies.map(methodologyFixture),
    disclaimer: fixtureString(value, "disclaimer", "document"),
  };
}

function methodologyStatus(value: string): MethodologyStatus {
  const status = METHODOLOGY_STATUSES.find((candidate) => candidate === value);

  if (!status) {
    throw new Error(`Unsupported methodology status: ${value}.`);
  }

  return status;
}

function fixtureInstant(value: string, ownerId: string) {
  const match = utcInstantPattern.exec(value);

  if (!match) {
    throw new Error(`Fixture ${ownerId} has an invalid UTC instant: ${value}.`);
  }

  const secondPrecision = `${match[1]}Z`;
  const parsed = Date.parse(secondPrecision);

  if (!Number.isFinite(parsed) || new Date(parsed).toISOString() !== `${match[1]}.000Z`) {
    throw new Error(`Fixture ${ownerId} has an invalid UTC instant: ${value}.`);
  }

  const microseconds = (match[2] ?? "").padEnd(6, "0");
  return BigInt(parsed) * 1_000n + BigInt(microseconds || "0");
}

function canonicalMethodology(
  methodology: MethodologyFixture,
  fixture: CallOutcomesFixture,
  provenanceCapturedAt: bigint,
): ScoringMethodology {
  const ownerId = `${methodology.methodologyId}@${methodology.methodologyVersion}`;

  if (methodology.schemaVersion !== fixture.schemaVersion || methodology.schemaVersion !== "1.0.0") {
    throw new Error(`Fixture methodology ${ownerId} has an unsupported schema version.`);
  }
  if (
    methodology.dataMode !== fixture.dataMode ||
    methodology.provenanceId !== fixture.provenance.id
  ) {
    throw new Error(`Fixture methodology ${ownerId} has inconsistent provenance.`);
  }
  if (!sha256Pattern.test(methodology.definitionHash)) {
    throw new Error(`Fixture methodology ${ownerId} has an invalid definition hash.`);
  }

  const effectiveAt = fixtureInstant(methodology.effectiveAt, `methodology ${ownerId}`);
  const capturedAt = fixtureInstant(methodology.capturedAt, `methodology ${ownerId}`);

  if (effectiveAt > capturedAt) {
    throw new Error(`Fixture methodology ${ownerId} was captured before becoming effective.`);
  }
  if (capturedAt > provenanceCapturedAt) {
    throw new Error(`Fixture methodology ${ownerId} was captured after its provenance envelope.`);
  }

  return {
    ...methodology,
    status: methodologyStatus(methodology.status),
    dataMode: readDataMode(methodology.dataMode),
  };
}

/** Maps the fixture adapter document into the canonical read-only provider contract. */
export function mapMethodologyFixtureDocument(document: unknown): MethodologyCatalog {
  const fixture = callOutcomesFixture(document);

  if (!DATA_MODES.some((mode) => mode === fixture.dataMode) || fixture.dataMode !== "DEMO") {
    throw new Error(`Methodology fixture has an unsupported data mode: ${fixture.dataMode}.`);
  }
  if (
    fixture.provenance.sourceType !== "LOCAL_SPECIFICATION" ||
    !fixture.provenance.synthetic ||
    fixture.provenance.licenseClass !== "INTERNAL_DEMO"
  ) {
    throw new Error("Methodology fixture has inconsistent DEMO provenance.");
  }

  const generatedAt = fixtureInstant(fixture.generatedAt, "document");
  const provenanceCapturedAt = fixtureInstant(fixture.provenance.capturedAt, "document provenance");

  if (provenanceCapturedAt > generatedAt) {
    throw new Error("Methodology fixture provenance was captured after document generation.");
  }

  const items = fixture.methodologies.map((methodology) =>
    canonicalMethodology(methodology, fixture, provenanceCapturedAt),
  );
  const identities = new Set<string>();

  for (const methodology of items) {
    const identity = `${methodology.methodologyId}@${methodology.methodologyVersion}`;

    if (identities.has(identity)) {
      throw new Error(`Duplicate fixture methodology identity: ${identity}.`);
    }
    identities.add(identity);
  }

  return {
    asOf: fixture.generatedAt,
    dataMode: readDataMode(fixture.dataMode),
    source: fixture.provenance.id,
    disclaimer: fixture.disclaimer,
    items,
  };
}

export class FixtureMethodologyProvider implements MethodologyProvider {
  async catalog(): Promise<MethodologyCatalog> {
    return mapMethodologyFixtureDocument(callOutcomesFixtureJson);
  }
}
