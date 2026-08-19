import nasdaq100FixtureJson from "../../../../../fixtures/v1/market-treemap-nasdaq100.json";
import sp500FixtureJson from "../../../../../fixtures/v1/market-treemap-sp500.json";
import { readDataMode } from "@/lib/data-mode";
import {
  isMarketTreemapUniverse,
  MARKET_TREEMAP_UNIVERSES,
  type MarketTreemapCell,
  type MarketTreemapProvider,
  type MarketTreemapSnapshot,
} from "./market-treemap-provider";

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
  "geometry",
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
const metricFields = ["name", "unit", "scaleMinimum", "scaleMaximum", "missingDisplay"] as const;
const geometryFields = ["type", "groupBy", "unclassifiedDisplay", "areaField", "areaUnit"] as const;
const coverageFields = ["kind", "completeUniverse", "cellCount", "weightBasis"] as const;
const cellFields = [
  "assetId",
  "ticker",
  "sector",
  "industry",
  "syntheticMarketCapProxy",
  "priceChangePercent",
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

function exactFields(record: Record<string, unknown>, expected: readonly string[], owner: string) {
  const actual = Object.keys(record);
  if (actual.length !== expected.length || actual.some((field) => !expected.includes(field))) {
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
  if (!match) throw new Error(`Fixture ${owner} has an invalid UTC instant: ${value}.`);

  const parsed = Date.parse(`${match[1]}Z`);
  if (!Number.isFinite(parsed) || new Date(parsed).toISOString() !== `${match[1]}.000Z`) {
    throw new Error(`Fixture ${owner} has an invalid UTC instant: ${value}.`);
  }

  const microseconds = (match[2] ?? "").padEnd(6, "0");
  return BigInt(parsed) * 1_000n + BigInt(microseconds || "0");
}

function classification(value: unknown, field: string, owner: string): string | null {
  if (value === null) return null;
  if (
    typeof value !== "string" ||
    value.length === 0 ||
    value.length > 128 ||
    value === "Unclassified"
  ) {
    throw new Error(`Fixture ${owner} has an invalid ${field}.`);
  }
  return value;
}

function compareLabels(left: string, right: string): number {
  if (left < right) return -1;
  if (left > right) return 1;
  return 0;
}

function displayClassification(value: string | null): string {
  return value ?? "Unclassified";
}

function validateCanonicalOrder(cells: readonly MarketTreemapCell[]): void {
  const sectorTotals = new Map<string, number>();
  const industryTotals = new Map<string, number>();

  for (const cell of cells) {
    const sector = displayClassification(cell.sector);
    const industry = displayClassification(cell.industry);
    sectorTotals.set(sector, (sectorTotals.get(sector) ?? 0) + cell.syntheticMarketCapProxy);
    const industryKey = `${sector}\u0000${industry}`;
    industryTotals.set(
      industryKey,
      (industryTotals.get(industryKey) ?? 0) + cell.syntheticMarketCapProxy,
    );
  }

  const expected = [...cells].sort((left, right) => {
    const leftSector = displayClassification(left.sector);
    const rightSector = displayClassification(right.sector);
    const sectorOrder =
      sectorTotals.get(rightSector)! - sectorTotals.get(leftSector)! ||
      compareLabels(leftSector, rightSector);
    if (sectorOrder !== 0) return sectorOrder;

    const leftIndustry = displayClassification(left.industry);
    const rightIndustry = displayClassification(right.industry);
    const leftIndustryKey = `${leftSector}\u0000${leftIndustry}`;
    const rightIndustryKey = `${rightSector}\u0000${rightIndustry}`;
    const industryOrder =
      industryTotals.get(rightIndustryKey)! - industryTotals.get(leftIndustryKey)! ||
      compareLabels(leftIndustry, rightIndustry);
    if (industryOrder !== 0) return industryOrder;

    return (
      right.syntheticMarketCapProxy - left.syntheticMarketCapProxy ||
      compareLabels(left.assetId, right.assetId)
    );
  });

  if (expected.some((cell, index) => cell.assetId !== cells[index]?.assetId)) {
    throw new Error("Market-treemap fixture cells are not in canonical hierarchy order.");
  }
}

function mapCell(
  value: unknown,
  index: number,
  source: string,
  asOf: bigint,
): MarketTreemapCell {
  const owner = `market-treemap cell at index ${index}`;
  if (!isRecord(value)) throw new Error(`Fixture ${owner} must be an object.`);
  exactFields(value, cellFields, owner);

  const assetId = fixtureString(value, "assetId", owner);
  const ticker = fixtureString(value, "ticker", owner);
  const sector = classification(value.sector, "sector", owner);
  const industry = classification(value.industry, "industry", owner);
  const syntheticMarketCapProxy = fixtureNumber(value, "syntheticMarketCapProxy", owner);
  const priceChangePercent = value.priceChangePercent;
  const timestamp = fixtureString(value, "timestamp", owner);
  const dataMode = fixtureString(value, "dataMode", owner);
  const provenanceId = fixtureString(value, "provenanceId", owner);

  if (!identifierPattern.test(assetId) || !tickerPattern.test(ticker)) {
    throw new Error(`Fixture ${owner} has an invalid asset identity.`);
  }
  if (sector === null && industry !== null) {
    throw new Error(`Fixture ${owner} cannot classify an industry without a sector.`);
  }
  if (
    !Number.isSafeInteger(syntheticMarketCapProxy) ||
    syntheticMarketCapProxy <= 0 ||
    syntheticMarketCapProxy > 1_000_000_000_000
  ) {
    throw new Error(`Fixture ${owner} has an invalid synthetic market-cap proxy.`);
  }
  if (
    priceChangePercent !== null &&
    (typeof priceChangePercent !== "number" ||
      !Number.isFinite(priceChangePercent) ||
      priceChangePercent < -100 ||
      priceChangePercent > 1_000_000)
  ) {
    throw new Error(`Fixture ${owner} has an out-of-range price-change metric.`);
  }
  if (dataMode !== "DEMO" || provenanceId !== source) {
    throw new Error(`Fixture ${owner} has inconsistent DEMO provenance.`);
  }
  if (fixtureInstant(timestamp, owner) > asOf) {
    throw new Error(`Fixture ${owner} is timestamped after the treemap as-of instant.`);
  }

  return {
    assetId,
    ticker,
    sector,
    industry,
    syntheticMarketCapProxy,
    priceChangePercent,
    timestamp,
    dataMode: readDataMode(dataMode),
    provenanceId,
  };
}

export function mapMarketTreemapFixtureDocument(document: unknown): MarketTreemapSnapshot {
  if (!isRecord(document)) throw new Error("Market-treemap fixture document must be an object.");
  exactFields(document, rootFields, "market-treemap document");

  if (
    !isRecord(document.provenance) ||
    !isRecord(document.metric) ||
    !isRecord(document.geometry) ||
    !isRecord(document.coverage) ||
    !Array.isArray(document.cells)
  ) {
    throw new Error("Market-treemap fixture document has an invalid evidence envelope.");
  }
  if (document.cells.length > 1_000) {
    throw new Error("Market-treemap fixture exceeds the supported cell count.");
  }
  exactFields(document.provenance, provenanceFields, "market-treemap provenance");
  exactFields(document.metric, metricFields, "market-treemap metric");
  exactFields(document.geometry, geometryFields, "market-treemap geometry");
  exactFields(document.coverage, coverageFields, "market-treemap coverage");

  const schemaVersion = fixtureString(document, "schemaVersion", "market-treemap document");
  const fixtureVersion = fixtureString(document, "fixtureVersion", "market-treemap document");
  const dataMode = fixtureString(document, "dataMode", "market-treemap document");
  const generatedAt = fixtureString(document, "generatedAt", "market-treemap document");
  const universe = fixtureString(document, "universe", "market-treemap document");
  const mode = fixtureString(document, "mode", "market-treemap document");
  const asOfValue = fixtureString(document, "asOf", "market-treemap document");
  const disclaimer = fixtureString(document, "disclaimer", "market-treemap document");

  if (schemaVersion !== "1.0.0" || fixtureVersion !== "v1") {
    throw new Error("Market-treemap fixture has an unsupported contract version.");
  }
  if (!isMarketTreemapUniverse(universe)) {
    throw new Error(`Market-treemap fixture has an unsupported universe: ${universe}.`);
  }
  if (dataMode !== "DEMO" || mode !== "PRICE_CHANGE") {
    throw new Error("Market-treemap fixture has an unsupported DEMO mode.");
  }
  if (disclaimer.length > 768) {
    throw new Error("Market-treemap fixture has an invalid disclaimer.");
  }

  const source = fixtureString(document.provenance, "id", "market-treemap provenance");
  const sourceType = fixtureString(document.provenance, "sourceType", "market-treemap provenance");
  const capturedAt = fixtureString(document.provenance, "capturedAt", "market-treemap provenance");
  const licenseClass = fixtureString(document.provenance, "licenseClass", "market-treemap provenance");
  const sourcePaths = document.provenance.sourcePaths;
  if (
    !identifierPattern.test(source) ||
    sourceType !== "LOCAL_SPECIFICATION" ||
    document.provenance.synthetic !== true ||
    licenseClass !== "INTERNAL_DEMO" ||
    !Array.isArray(sourcePaths) ||
    sourcePaths.length === 0 ||
    sourcePaths.some((path) => typeof path !== "string" || path.length === 0 || path.length > 256) ||
    new Set(sourcePaths).size !== sourcePaths.length
  ) {
    throw new Error("Market-treemap fixture has inconsistent DEMO provenance.");
  }

  const asOf = fixtureInstant(asOfValue, "market-treemap document");
  const capturedAtInstant = fixtureInstant(capturedAt, "market-treemap provenance");
  const generatedAtInstant = fixtureInstant(generatedAt, "market-treemap document");
  if (asOf > capturedAtInstant || capturedAtInstant > generatedAtInstant) {
    throw new Error("Market-treemap fixture violates as-of, capture, or generation time order.");
  }

  const metricName = fixtureString(document.metric, "name", "market-treemap metric");
  const metricUnit = fixtureString(document.metric, "unit", "market-treemap metric");
  const scaleMinimum = fixtureNumber(document.metric, "scaleMinimum", "market-treemap metric");
  const scaleMaximum = fixtureNumber(document.metric, "scaleMaximum", "market-treemap metric");
  const missingDisplay = fixtureString(document.metric, "missingDisplay", "market-treemap metric");
  if (
    metricName !== "priceChangePercent" ||
    metricUnit !== "percent" ||
    scaleMinimum !== -5 ||
    scaleMaximum !== 5 ||
    missingDisplay !== "NA"
  ) {
    throw new Error("Market-treemap fixture has an unsupported metric definition.");
  }

  const geometryType = fixtureString(document.geometry, "type", "market-treemap geometry");
  const unclassifiedDisplay = fixtureString(
    document.geometry,
    "unclassifiedDisplay",
    "market-treemap geometry",
  );
  const areaField = fixtureString(document.geometry, "areaField", "market-treemap geometry");
  const areaUnit = fixtureString(document.geometry, "areaUnit", "market-treemap geometry");
  const groupBy = document.geometry.groupBy;
  if (
    geometryType !== "NESTED_TREEMAP" ||
    !Array.isArray(groupBy) ||
    groupBy.length !== 2 ||
    groupBy[0] !== "sector" ||
    groupBy[1] !== "industry" ||
    unclassifiedDisplay !== "Unclassified" ||
    areaField !== "syntheticMarketCapProxy" ||
    areaUnit !== "relative"
  ) {
    throw new Error("Market-treemap fixture has an unsupported geometry definition.");
  }

  const coverageKind = fixtureString(document.coverage, "kind", "market-treemap coverage");
  const completeUniverse = document.coverage.completeUniverse;
  const cellCount = fixtureNumber(document.coverage, "cellCount", "market-treemap coverage");
  const weightBasis = fixtureString(document.coverage, "weightBasis", "market-treemap coverage");
  if (
    coverageKind !== "SAMPLE" ||
    completeUniverse !== false ||
    !Number.isInteger(cellCount) ||
    cellCount < 0 ||
    weightBasis !== "SYNTHETIC_MARKET_CAP_PROXY" ||
    cellCount !== document.cells.length
  ) {
    throw new Error("Market-treemap fixture has inconsistent sample coverage.");
  }

  const cells = document.cells.map((cell, index) => mapCell(cell, index, source, asOf));
  const assetIds = new Set<string>();
  const tickers = new Set<string>();
  for (const cell of cells) {
    if (assetIds.has(cell.assetId) || tickers.has(cell.ticker)) {
      throw new Error(`Market-treemap fixture has a duplicate cell identity: ${cell.assetId}.`);
    }
    assetIds.add(cell.assetId);
    tickers.add(cell.ticker);
  }
  const aggregateProxy = cells.reduce((sum, cell) => sum + cell.syntheticMarketCapProxy, 0);
  if (!Number.isSafeInteger(aggregateProxy)) {
    throw new Error("Market-treemap fixture has an unsafe aggregate proxy.");
  }
  validateCanonicalOrder(cells);

  return {
    schemaVersion: "1.0.0",
    fixtureVersion: "v1",
    dataMode: readDataMode(dataMode),
    generatedAt,
    provenance: {
      id: source,
      sourceType: "LOCAL_SPECIFICATION",
      sourcePaths: [...sourcePaths] as string[],
      capturedAt,
      synthetic: true,
      licenseClass: "INTERNAL_DEMO",
    },
    universe,
    mode: "PRICE_CHANGE",
    asOf: asOfValue,
    metric: {
      name: "priceChangePercent",
      unit: "percent",
      scaleMinimum,
      scaleMaximum,
      missingDisplay: "NA",
    },
    geometry: {
      type: "NESTED_TREEMAP",
      groupBy: ["sector", "industry"],
      unclassifiedDisplay: "Unclassified",
      areaField: "syntheticMarketCapProxy",
      areaUnit: "relative",
    },
    coverage: {
      kind: "SAMPLE",
      completeUniverse: false,
      cellCount,
      weightBasis: "SYNTHETIC_MARKET_CAP_PROXY",
    },
    cells,
    disclaimer,
  };
}

export function mapMarketTreemapFixtureDocuments(documents: readonly unknown[]) {
  const snapshots = documents.map(mapMarketTreemapFixtureDocument);

  if (snapshots.length !== MARKET_TREEMAP_UNIVERSES.length) {
    throw new Error("Market-treemap fixture registry must contain exactly two universes.");
  }

  const byUniverse = new Map(snapshots.map((snapshot) => [snapshot.universe, snapshot]));
  const provenanceIds = new Set(snapshots.map((snapshot) => snapshot.provenance.id));
  const naturalKeys = new Set(
    snapshots.map((snapshot) => `${snapshot.universe}|${snapshot.mode}|${snapshot.asOf}`),
  );

  if (byUniverse.size !== snapshots.length) {
    throw new Error("Market-treemap fixture registry has a duplicate universe.");
  }
  if (provenanceIds.size !== snapshots.length) {
    throw new Error("Market-treemap fixture registry has a duplicate provenance identity.");
  }
  if (naturalKeys.size !== snapshots.length) {
    throw new Error("Market-treemap fixture registry has a duplicate natural identity.");
  }
  for (const universe of MARKET_TREEMAP_UNIVERSES) {
    if (!byUniverse.has(universe)) {
      throw new Error(`Market-treemap fixture registry is missing the ${universe} universe.`);
    }
  }

  for (const [index, left] of snapshots.entries()) {
    for (const right of snapshots.slice(index + 1)) {
      if (left.asOf !== right.asOf) continue;

      const rightByAsset = new Map(right.cells.map((cell) => [cell.assetId, cell]));
      for (const leftCell of left.cells) {
        const rightCell = rightByAsset.get(leftCell.assetId);
        if (!rightCell) continue;

        if (
          leftCell.ticker !== rightCell.ticker ||
          leftCell.sector !== rightCell.sector ||
          leftCell.industry !== rightCell.industry ||
          leftCell.syntheticMarketCapProxy !== rightCell.syntheticMarketCapProxy ||
          !Object.is(leftCell.priceChangePercent, rightCell.priceChangePercent) ||
          leftCell.timestamp !== rightCell.timestamp ||
          leftCell.dataMode !== rightCell.dataMode
        ) {
          throw new Error(
            `Market-treemap fixture registry has divergent shared-cell evidence: ${leftCell.assetId}.`,
          );
        }
      }
    }
  }

  return MARKET_TREEMAP_UNIVERSES.map((universe) => byUniverse.get(universe)!);
}

const fixtureSnapshots = mapMarketTreemapFixtureDocuments([sp500FixtureJson, nasdaq100FixtureJson]);

export class FixtureMarketTreemapProvider implements MarketTreemapProvider {
  async findByUniverse(universe: MarketTreemapSnapshot["universe"]): Promise<MarketTreemapSnapshot> {
    const snapshot = fixtureSnapshots.find((candidate) => candidate.universe === universe);
    if (!snapshot) {
      throw new Error(`No canonical fixture is registered for treemap universe: ${universe}.`);
    }
    return snapshot;
  }
}
