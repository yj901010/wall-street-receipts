import nasdaq100FixtureJson from "../../../../../fixtures/v1/market-map-nasdaq100.json";
import sp500FixtureJson from "../../../../../fixtures/v1/market-map.json";
import { readDataMode } from "@/lib/data-mode";
import {
  isMarketMapUniverse,
  MARKET_MAP_UNIVERSES,
  type MarketMapCell,
  type MarketMapProvider,
  type MarketMapSnapshot,
} from "./market-map-provider";

const rootFields = [
  "schemaVersion",
  "fixtureVersion",
  "dataMode",
  "generatedAt",
  "provenance",
  "universe",
  "mode",
  "asOf",
  "metric",
  "coverage",
  "cells",
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
const metricFields = ["name", "unit", "minimum", "maximum", "missingDisplay"] as const;
const coverageFields = ["kind", "completeUniverse", "cellCount", "weightBasis"] as const;
const cellFields = [
  "assetId",
  "ticker",
  "sector",
  "weight",
  "metric",
  "callCount",
  "timestamp",
  "dataMode",
  "provenanceId",
] as const;
const utcInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;
const identifierPattern = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const tickerPattern = /^[A-Z0-9][A-Z0-9.-]{0,23}$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function exactFields(
  record: Record<string, unknown>,
  expectedFields: readonly string[],
  owner: string,
) {
  const fields = Object.keys(record);
  const unexpected = fields.filter((field) => !expectedFields.includes(field));

  if (unexpected.length > 0 || fields.length !== expectedFields.length) {
    throw new Error(`Fixture ${owner} does not match its closed record shape.`);
  }
}

function fixtureString(record: Record<string, unknown>, field: string, owner: string) {
  const value = record[field];

  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`Fixture ${owner} has a missing or invalid ${field}.`);
  }

  return value;
}

function fixtureNumber(record: Record<string, unknown>, field: string, owner: string) {
  const value = record[field];

  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new Error(`Fixture ${owner} has a missing or invalid ${field}.`);
  }

  return value;
}

function fixtureInstant(value: string, owner: string) {
  const match = utcInstantPattern.exec(value);

  if (!match) {
    throw new Error(`Fixture ${owner} has an invalid UTC instant: ${value}.`);
  }

  const parsed = Date.parse(`${match[1]}Z`);

  if (!Number.isFinite(parsed) || new Date(parsed).toISOString() !== `${match[1]}.000Z`) {
    throw new Error(`Fixture ${owner} has an invalid UTC instant: ${value}.`);
  }

  const microseconds = (match[2] ?? "").padEnd(6, "0");
  return BigInt(parsed) * 1_000n + BigInt(microseconds || "0");
}

function mapCell(
  value: unknown,
  index: number,
  source: string,
  asOf: bigint,
): MarketMapCell {
  const owner = `market-map cell at index ${index}`;

  if (!isRecord(value)) {
    throw new Error(`Fixture ${owner} must be an object.`);
  }
  exactFields(value, cellFields, owner);

  const assetId = fixtureString(value, "assetId", owner);
  const ticker = fixtureString(value, "ticker", owner);
  const sector = value.sector;
  const weight = fixtureNumber(value, "weight", owner);
  const metric = value.metric;
  const callCount = fixtureNumber(value, "callCount", owner);
  const timestamp = fixtureString(value, "timestamp", owner);
  const dataMode = fixtureString(value, "dataMode", owner);
  const provenanceId = fixtureString(value, "provenanceId", owner);

  if (!identifierPattern.test(assetId) || !tickerPattern.test(ticker)) {
    throw new Error(`Fixture ${owner} has an invalid asset identity.`);
  }
  if (sector !== null && (typeof sector !== "string" || sector.length === 0 || sector.length > 128)) {
    throw new Error(`Fixture ${owner} has an invalid sector.`);
  }
  if (weight <= 0 || weight > 100) {
    throw new Error(`Fixture ${owner} has an invalid synthetic relative weight.`);
  }
  if (metric !== null && (typeof metric !== "number" || !Number.isFinite(metric) || metric < -1 || metric > 1)) {
    throw new Error(`Fixture ${owner} has an out-of-range metric.`);
  }
  if (!Number.isInteger(callCount) || callCount < 0) {
    throw new Error(`Fixture ${owner} has an invalid call count.`);
  }
  if (dataMode !== "DEMO" || provenanceId !== source) {
    throw new Error(`Fixture ${owner} has inconsistent DEMO provenance.`);
  }
  if (fixtureInstant(timestamp, owner) > asOf) {
    throw new Error(`Fixture ${owner} is timestamped after the map as-of instant.`);
  }

  return {
    assetId,
    ticker,
    sector,
    weight,
    metric,
    callCount,
    timestamp,
    dataMode: readDataMode(dataMode),
    provenanceId,
  };
}

export function mapMarketMapFixtureDocument(document: unknown): MarketMapSnapshot {
  if (!isRecord(document)) {
    throw new Error("Market-map fixture document must be an object.");
  }
  exactFields(document, rootFields, "market-map document");

  if (!isRecord(document.provenance) || !isRecord(document.metric) || !isRecord(document.coverage)) {
    throw new Error("Market-map fixture document has an invalid evidence envelope.");
  }
  if (!Array.isArray(document.cells)) {
    throw new Error("Market-map fixture document has an invalid cells collection.");
  }

  exactFields(document.provenance, provenanceFields, "market-map provenance");
  exactFields(document.metric, metricFields, "market-map metric");
  exactFields(document.coverage, coverageFields, "market-map coverage");

  const schemaVersion = fixtureString(document, "schemaVersion", "market-map document");
  const fixtureVersion = fixtureString(document, "fixtureVersion", "market-map document");
  const dataMode = fixtureString(document, "dataMode", "market-map document");
  const generatedAt = fixtureString(document, "generatedAt", "market-map document");
  const universe = fixtureString(document, "universe", "market-map document");
  const mode = fixtureString(document, "mode", "market-map document");
  const asOfValue = fixtureString(document, "asOf", "market-map document");
  const disclaimer = fixtureString(document, "disclaimer", "market-map document");
  const source = fixtureString(document.provenance, "id", "market-map provenance");
  const sourceType = fixtureString(document.provenance, "sourceType", "market-map provenance");
  const capturedAt = fixtureString(document.provenance, "capturedAt", "market-map provenance");
  const licenseClass = fixtureString(document.provenance, "licenseClass", "market-map provenance");
  const sourcePaths = document.provenance.sourcePaths;

  if (schemaVersion !== "1.0.0" || fixtureVersion !== "v1") {
    throw new Error("Market-map fixture has an unsupported contract version.");
  }
  if (!isMarketMapUniverse(universe)) {
    throw new Error(`Market-map fixture has an unsupported universe: ${universe}.`);
  }
  if (dataMode !== "DEMO" || mode !== "ANALYST_CONSENSUS") {
    throw new Error("Market-map fixture has an unsupported DEMO mode.");
  }
  if (
    !identifierPattern.test(source) ||
    sourceType !== "LOCAL_SPECIFICATION" ||
    document.provenance.synthetic !== true ||
    licenseClass !== "INTERNAL_DEMO" ||
    !Array.isArray(sourcePaths) ||
    sourcePaths.length === 0 ||
    sourcePaths.some(
      (path) => typeof path !== "string" || path.length === 0 || path.length > 256,
    ) ||
    new Set(sourcePaths).size !== sourcePaths.length
  ) {
    throw new Error("Market-map fixture has inconsistent DEMO provenance.");
  }
  if (disclaimer.length > 512) {
    throw new Error("Market-map fixture has an invalid disclaimer.");
  }

  const generatedInstant = fixtureInstant(generatedAt, "market-map document");
  const capturedInstant = fixtureInstant(capturedAt, "market-map provenance");
  const asOf = fixtureInstant(asOfValue, "market-map document");

  if (asOf > capturedInstant || capturedInstant > generatedInstant) {
    throw new Error("Market-map fixture violates as-of, capture, or generation time order.");
  }

  const metricName = fixtureString(document.metric, "name", "market-map metric");
  const metricUnit = fixtureString(document.metric, "unit", "market-map metric");
  const minimum = fixtureNumber(document.metric, "minimum", "market-map metric");
  const maximum = fixtureNumber(document.metric, "maximum", "market-map metric");
  const missingDisplay = fixtureString(document.metric, "missingDisplay", "market-map metric");

  if (
    metricName !== "analystConsensus" ||
    metricUnit !== "score" ||
    minimum !== -1 ||
    maximum !== 1 ||
    missingDisplay !== "NA"
  ) {
    throw new Error("Market-map fixture has an unsupported metric definition.");
  }

  const coverageKind = fixtureString(document.coverage, "kind", "market-map coverage");
  const completeUniverse = document.coverage.completeUniverse;
  const cellCount = fixtureNumber(document.coverage, "cellCount", "market-map coverage");
  const weightBasis = fixtureString(document.coverage, "weightBasis", "market-map coverage");

  if (
    coverageKind !== "SAMPLE" ||
    completeUniverse !== false ||
    !Number.isInteger(cellCount) ||
    cellCount < 0 ||
    weightBasis !== "SYNTHETIC_RELATIVE" ||
    cellCount !== document.cells.length
  ) {
    throw new Error("Market-map fixture has inconsistent sample coverage.");
  }

  const cells = document.cells.map((cell, index) => mapCell(cell, index, source, asOf));
  const assetIds = new Set<string>();
  const tickers = new Set<string>();

  for (const cell of cells) {
    if (assetIds.has(cell.assetId) || tickers.has(cell.ticker)) {
      throw new Error(`Market-map fixture has a duplicate cell identity: ${cell.assetId}.`);
    }
    assetIds.add(cell.assetId);
    tickers.add(cell.ticker);
  }

  return {
    schemaVersion: "1.0.0",
    fixtureVersion: "v1",
    universe,
    mode: "ANALYST_CONSENSUS",
    asOf: asOfValue,
    generatedAt,
    capturedAt,
    dataMode: readDataMode(dataMode),
    source,
    coverage: {
      kind: "SAMPLE",
      completeUniverse: false,
      cellCount,
      weightBasis: "SYNTHETIC_RELATIVE",
    },
    metric: {
      name: "analystConsensus",
      unit: "score",
      minimum,
      maximum,
      missingDisplay: "NA",
    },
    cells,
    disclaimer,
  };
}

export function mapMarketMapFixtureDocuments(documents: readonly unknown[]) {
  const snapshots = documents.map(mapMarketMapFixtureDocument);
  const byUniverse = new Map(snapshots.map((snapshot) => [snapshot.universe, snapshot]));
  const provenanceIds = new Set(snapshots.map((snapshot) => snapshot.source));
  const naturalKeys = new Set(
    snapshots.map((snapshot) => `${snapshot.universe}|${snapshot.mode}|${snapshot.asOf}`),
  );

  if (byUniverse.size !== snapshots.length) {
    throw new Error("Market-map fixture registry has a duplicate universe.");
  }
  if (provenanceIds.size !== snapshots.length) {
    throw new Error("Market-map fixture registry has a duplicate provenance identity.");
  }
  if (naturalKeys.size !== snapshots.length) {
    throw new Error("Market-map fixture registry has a duplicate natural identity.");
  }
  for (const universe of MARKET_MAP_UNIVERSES) {
    if (!byUniverse.has(universe)) {
      throw new Error(`Market-map fixture registry is missing the ${universe} universe.`);
    }
  }

  return MARKET_MAP_UNIVERSES.map((universe) => byUniverse.get(universe)!);
}

const fixtureSnapshots = mapMarketMapFixtureDocuments([sp500FixtureJson, nasdaq100FixtureJson]);

export class FixtureMarketMapProvider implements MarketMapProvider {
  async findByUniverse(universe: MarketMapSnapshot["universe"]): Promise<MarketMapSnapshot> {
    const snapshot = fixtureSnapshots.find((candidate) => candidate.universe === universe);

    if (!snapshot) {
      throw new Error(`No canonical fixture is registered for map universe: ${universe}.`);
    }

    return snapshot;
  }
}
