import marketBoardFixtureJson from "../../../../../fixtures/v1/market-board.json";
import type {
  MarketBoardProvider,
  MarketBoardSnapshot,
} from "./market-board-provider";

const documentFields = [
  "schemaVersion",
  "fixtureVersion",
  "dataMode",
  "generatedAt",
  "provenance",
  "scope",
  "publicationStatus",
  "publicationReasonCode",
  "marketAsOf",
  "missingDisplay",
  "quotes",
  "disclaimer",
] as const;
const provenanceFields = [
  "id",
  "sourceType",
  "sourcePaths",
  "capturedAt",
  "synthetic",
  "licenseClass",
] as const;
const expectedSourcePaths = [
  "schemas/market-board.schema.json",
  "quality/P2_ACCEPTANCE.md",
] as const;
const expectedProvenanceId = "fixture-market-board-v1";
const expectedDisclaimer =
  "Known-unavailable DEMO publication state only; no canonical global quote catalog or current, latest, delayed, or end-of-day market board is published. No price, change, session status, freshness, or coverage was observed, derived, inferred, or promoted from call-event snapshots, treemaps, or application literals. Not investment advice.";
const identifierPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const utcInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;

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

/** Maps the closed known-unavailable fixture without creating quote evidence. */
export function mapMarketBoardFixtureDocument(document: unknown): MarketBoardSnapshot {
  if (!isRecord(document)) {
    throw new Error("Market board fixture document must be an object.");
  }
  assertExactFields(document, documentFields, "Market board fixture document");

  if (!isRecord(document.provenance) || !Array.isArray(document.quotes)) {
    throw new Error("Market board fixture document has an invalid envelope.");
  }
  assertExactFields(document.provenance, provenanceFields, "Market board fixture provenance");

  const schemaVersion = fixtureString(document, "schemaVersion", "Market board fixture document");
  const fixtureVersion = fixtureString(document, "fixtureVersion", "Market board fixture document");
  const dataMode = fixtureString(document, "dataMode", "Market board fixture document");
  const generatedAt = fixtureString(document, "generatedAt", "Market board fixture document");
  const scope = fixtureString(document, "scope", "Market board fixture document");
  const publicationStatus = fixtureString(
    document,
    "publicationStatus",
    "Market board fixture document",
  );
  const publicationReasonCode = fixtureString(
    document,
    "publicationReasonCode",
    "Market board fixture document",
  );
  const missingDisplay = fixtureString(
    document,
    "missingDisplay",
    "Market board fixture document",
  );
  const disclaimer = fixtureString(
    document,
    "disclaimer",
    "Market board fixture document",
    768,
  );

  if (
    schemaVersion !== "1.0.0" ||
    fixtureVersion !== "v1" ||
    dataMode !== "DEMO" ||
    scope !== "GLOBAL_MARKET_OVERVIEW" ||
    publicationStatus !== "NOT_PUBLISHED" ||
    publicationReasonCode !== "NO_CANONICAL_GLOBAL_QUOTE_CATALOG" ||
    document.marketAsOf !== null ||
    missingDisplay !== "NA" ||
    document.quotes.length !== 0 ||
    disclaimer !== expectedDisclaimer
  ) {
    throw new Error("Market board fixture is not the supported known-unavailable publication state.");
  }

  const provenanceId = fixtureIdentifier(
    document.provenance,
    "id",
    "Market board fixture provenance",
  );
  const sourceType = fixtureString(
    document.provenance,
    "sourceType",
    "Market board fixture provenance",
  );
  const capturedAt = fixtureString(
    document.provenance,
    "capturedAt",
    "Market board fixture provenance",
  );
  const licenseClass = fixtureString(
    document.provenance,
    "licenseClass",
    "Market board fixture provenance",
  );

  if (
    provenanceId !== expectedProvenanceId ||
    sourceType !== "LOCAL_SPECIFICATION" ||
    document.provenance.synthetic !== true ||
    licenseClass !== "INTERNAL_DEMO"
  ) {
    throw new Error("Market board fixture has unsupported DEMO provenance.");
  }

  if (
    !Array.isArray(document.provenance.sourcePaths) ||
    document.provenance.sourcePaths.length !== expectedSourcePaths.length
  ) {
    throw new Error("Market board fixture provenance has invalid source paths.");
  }
  const sourcePaths = document.provenance.sourcePaths.map((path, index) => {
    if (
      typeof path !== "string" ||
      path.length === 0 ||
      path.length > 256 ||
      path.trim() !== path ||
      path !== expectedSourcePaths[index]
    ) {
      throw new Error(`Market board fixture provenance has an invalid source path at index ${index}.`);
    }
    return path;
  }) as [string, string];

  const generatedInstant = instant(generatedAt, "Market board fixture document");
  const capturedInstant = instant(capturedAt, "Market board fixture provenance");
  if (capturedInstant > generatedInstant) {
    throw new Error("Market board fixture provenance was captured after document generation.");
  }

  return {
    schemaVersion: "1.0.0",
    fixtureVersion: "v1",
    dataMode: "DEMO",
    generatedAt,
    provenance: {
      id: provenanceId,
      sourceType: "LOCAL_SPECIFICATION",
      sourcePaths,
      capturedAt,
      synthetic: true,
      licenseClass: "INTERNAL_DEMO",
    },
    scope: "GLOBAL_MARKET_OVERVIEW",
    publicationStatus: "NOT_PUBLISHED",
    publicationReasonCode: "NO_CANONICAL_GLOBAL_QUOTE_CATALOG",
    marketAsOf: null,
    missingDisplay: "NA",
    quotes: [],
    disclaimer,
  };
}

export class FixtureMarketBoardProvider implements MarketBoardProvider {
  async snapshot(): Promise<MarketBoardSnapshot> {
    return mapMarketBoardFixtureDocument(marketBoardFixtureJson);
  }
}
