import { DATA_MODES, type DataMode } from "@/lib/data-mode";
import {
  CALL_DIRECTIONS,
  CALL_STATUSES,
  MACRO_SERIES,
  type AnalystCall,
  type AnalystCallDetail,
  type AnalystCallView,
  type AnalystSummary,
  type AssetSummary,
  type CallContext,
  type EventContext,
  type InstitutionSummary,
  type MacroObservation,
  type MacroSnapshot,
  type MarketSnapshot,
  type SourceDocument,
  type SourceEvidence,
  type SourceReference,
} from "./calls-provider";
import {
  CALL_REVISION_TYPES,
  type CallAuditSnapshot,
  type CallRevision,
  type CorrectedCallTerms,
} from "./call-audit-provider";

const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const UTC_INSTANT = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;
const CALENDAR_DATE = /^\d{4}-\d{2}-\d{2}$/;
const CURRENCY = /^[A-Z]{3}$/;
const CONTENT_HASH = /^[A-Fa-f0-9]{64}$/;
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

const ASSET_TYPES = ["INDEX", "EQUITY", "ETF", "BOND", "COMMODITY", "FX"] as const;
const SOURCE_TYPES = ["VIDEO", "ARTICLE", "RESEARCH", "PODCAST", "FILING", "TRANSCRIPT"] as const;
const MACRO_UNITS = ["PERCENT", "PERCENTAGE_POINTS", "INDEX"] as const;

type JsonRecord = Record<string, unknown>;

function fail(owner: string, detail: string): never {
  throw new Error(`${owner} ${detail}.`);
}

function closedRecord(value: unknown, keys: readonly string[], owner: string): JsonRecord {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    fail(owner, "must be an object");
  }

  const record = value as JsonRecord;
  const actual = Object.keys(record).sort();
  const expected = [...keys].sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) {
    fail(owner, `must contain exactly ${expected.join(", ")}`);
  }
  return record;
}

function text(value: unknown, owner: string, maximum?: number): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    fail(owner, "must be a non-blank string");
  }
  if (maximum !== undefined && value.length > maximum) {
    fail(owner, `must not exceed ${maximum} characters`);
  }
  return value;
}

function stringValue(value: unknown, owner: string, maximum?: number): string {
  if (typeof value !== "string") {
    fail(owner, "must be a string");
  }
  if (maximum !== undefined && value.length > maximum) {
    fail(owner, `must not exceed ${maximum} characters`);
  }
  return value;
}

function nullableString(value: unknown, owner: string, maximum?: number): string | null {
  return value === null ? null : stringValue(value, owner, maximum);
}

function identifier(value: unknown, owner: string): string {
  if (typeof value !== "string" || !IDENTIFIER.test(value)) {
    fail(owner, "must be a canonical opaque identifier");
  }
  return value;
}

function nullableIdentifier(value: unknown, owner: string): string | null {
  return value === null ? null : identifier(value, owner);
}

function enumValue<const T extends readonly string[]>(
  value: unknown,
  values: T,
  owner: string,
): T[number] {
  if (typeof value !== "string" || !values.includes(value)) {
    fail(owner, `must be one of ${values.join(", ")}`);
  }
  return value as T[number];
}

function finiteNumber(value: unknown, owner: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    fail(owner, "must be a finite number");
  }
  return value;
}

function nullableNumber(value: unknown, owner: string): number | null {
  return value === null ? null : finiteNumber(value, owner);
}

function integer(value: unknown, owner: string, minimum = Number.MIN_SAFE_INTEGER): number {
  if (!Number.isSafeInteger(value) || (value as number) < minimum) {
    fail(owner, `must be an integer greater than or equal to ${minimum}`);
  }
  return value as number;
}

function nullableInteger(value: unknown, owner: string, minimum: number): number | null {
  return value === null ? null : integer(value, owner, minimum);
}

function booleanValue(value: unknown, owner: string): boolean {
  if (typeof value !== "boolean") {
    fail(owner, "must be a boolean");
  }
  return value;
}

function schemaVersion(value: unknown, owner: string): "1.0.0" {
  if (value !== "1.0.0") {
    fail(owner, "must equal 1.0.0");
  }
  return "1.0.0";
}

function dataMode(value: unknown, owner: string): DataMode {
  return enumValue(value, DATA_MODES, owner);
}

export function callAuditInstant(value: unknown, owner: string): string {
  if (typeof value !== "string") {
    fail(owner, "must be a canonical UTC instant");
  }
  const match = UTC_INSTANT.exec(value);
  if (!match) {
    fail(owner, "must be a canonical UTC instant with at most microsecond precision");
  }
  const parsed = Date.parse(`${match[1]}Z`);
  if (!Number.isFinite(parsed) || new Date(parsed).toISOString() !== `${match[1]}.000Z`) {
    fail(owner, "must be a real UTC instant");
  }
  return value;
}

function instantOrder(value: string): bigint {
  const match = UTC_INSTANT.exec(value);
  if (!match) {
    fail("Instant", "must already be canonical");
  }
  const milliseconds = Date.parse(`${match[1]}Z`);
  return BigInt(milliseconds) * 1_000n + BigInt((match[2] ?? "").padEnd(6, "0"));
}

export function compareCallAuditInstants(left: string, right: string): number {
  const leftOrder = instantOrder(callAuditInstant(left, "Left audit instant"));
  const rightOrder = instantOrder(callAuditInstant(right, "Right audit instant"));
  return leftOrder < rightOrder ? -1 : leftOrder > rightOrder ? 1 : 0;
}

function calendarDate(value: unknown, owner: string): string {
  if (typeof value !== "string" || !CALENDAR_DATE.test(value)) {
    fail(owner, "must be a calendar date");
  }
  const parsed = new Date(`${value}T00:00:00Z`);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) {
    fail(owner, "must be a real calendar date");
  }
  return value;
}

function nullableCalendarDate(value: unknown, owner: string): string | null {
  return value === null ? null : calendarDate(value, owner);
}

function nullableInstant(value: unknown, owner: string): string | null {
  return value === null ? null : callAuditInstant(value, owner);
}

function assertChronology(eventTime: string, processingTime: string, capturedAt: string, owner: string) {
  if (instantOrder(eventTime) > instantOrder(processingTime)) {
    fail(owner, "processingTime must not precede eventTime");
  }
  if (instantOrder(processingTime) > instantOrder(capturedAt)) {
    fail(owner, "capturedAt must not precede processingTime");
  }
}

function nullablePositiveNumber(value: unknown, owner: string): number | null {
  const result = nullableNumber(value, owner);
  if (result !== null && result <= 0) {
    fail(owner, "must be positive when present");
  }
  return result;
}

function nullableMacroNumber(value: unknown, owner: string): number | null {
  const result = nullableNumber(value, owner);
  if (result !== null && Math.abs(result) >= 1e26) {
    fail(owner, "must have absolute magnitude below 1e26");
  }
  return result;
}

function adaptCall(value: unknown): AnalystCall {
  const owner = "Call detail.call";
  const record = closedRecord(value, [
    "schemaVersion", "callId", "provider", "providerEventId", "institutionId", "analystId",
    "assetId", "eventTime", "processingTime", "direction", "originalRating", "previousTarget",
    "target", "currency", "targetDate", "sourceReferenceId", "status", "dataMode", "capturedAt",
    "provenanceId",
  ], owner);
  const eventTime = callAuditInstant(record.eventTime, `${owner}.eventTime`);
  const processingTime = callAuditInstant(record.processingTime, `${owner}.processingTime`);
  const capturedAt = callAuditInstant(record.capturedAt, `${owner}.capturedAt`);
  const previousTarget = nullablePositiveNumber(record.previousTarget, `${owner}.previousTarget`);
  const target = nullablePositiveNumber(record.target, `${owner}.target`);
  const currency = nullableString(record.currency, `${owner}.currency`, 3);
  if (currency !== null && !CURRENCY.test(currency)) fail(`${owner}.currency`, "must be an ISO currency code");
  if ((previousTarget !== null || target !== null) && currency === null) {
    fail(owner, "currency is required when a target is present");
  }
  assertChronology(eventTime, processingTime, capturedAt, owner);
  return {
    schemaVersion: schemaVersion(record.schemaVersion, `${owner}.schemaVersion`),
    callId: identifier(record.callId, `${owner}.callId`),
    provider: text(record.provider, `${owner}.provider`, 100),
    providerEventId: text(record.providerEventId, `${owner}.providerEventId`, 256),
    institutionId: identifier(record.institutionId, `${owner}.institutionId`),
    analystId: nullableIdentifier(record.analystId, `${owner}.analystId`),
    assetId: identifier(record.assetId, `${owner}.assetId`),
    eventTime,
    processingTime,
    direction: enumValue(record.direction, CALL_DIRECTIONS, `${owner}.direction`),
    originalRating: nullableString(record.originalRating, `${owner}.originalRating`, 200),
    previousTarget,
    target,
    currency,
    targetDate: nullableCalendarDate(record.targetDate, `${owner}.targetDate`),
    sourceReferenceId: identifier(record.sourceReferenceId, `${owner}.sourceReferenceId`),
    status: enumValue(record.status, CALL_STATUSES, `${owner}.status`),
    dataMode: dataMode(record.dataMode, `${owner}.dataMode`),
    capturedAt,
    provenanceId: identifier(record.provenanceId, `${owner}.provenanceId`),
  };
}

function adaptInstitution(value: unknown): InstitutionSummary {
  const owner = "Call detail.institution";
  const record = closedRecord(value, ["institutionId", "canonicalName", "slug"], owner);
  const slug = text(record.slug, `${owner}.slug`, 200);
  if (!SLUG.test(slug)) fail(`${owner}.slug`, "must be canonical");
  return {
    institutionId: identifier(record.institutionId, `${owner}.institutionId`),
    canonicalName: text(record.canonicalName, `${owner}.canonicalName`, 200),
    slug,
  };
}

function adaptAnalyst(value: unknown): AnalystSummary | null {
  if (value === null) return null;
  const owner = "Call detail.analyst";
  const record = closedRecord(value, ["analystId", "canonicalName"], owner);
  return {
    analystId: identifier(record.analystId, `${owner}.analystId`),
    canonicalName: text(record.canonicalName, `${owner}.canonicalName`, 200),
  };
}

function adaptAsset(value: unknown): AssetSummary {
  const owner = "Call detail.asset";
  const record = closedRecord(value, ["assetId", "assetType", "canonicalName", "ticker"], owner);
  return {
    assetId: identifier(record.assetId, `${owner}.assetId`),
    assetType: enumValue(record.assetType, ASSET_TYPES, `${owner}.assetType`),
    canonicalName: text(record.canonicalName, `${owner}.canonicalName`, 200),
    ticker: nullableString(record.ticker, `${owner}.ticker`, 24),
  };
}

function adaptSourceDocument(value: unknown): SourceDocument {
  const owner = "Call detail.source.document";
  const record = closedRecord(value, [
    "schemaVersion", "sourceDocumentId", "sourceType", "publisher", "title", "canonicalUrl",
    "publishedAt", "provider", "externalId", "contentHash", "licenseClass", "dataMode",
    "capturedAt", "provenanceId",
  ], owner);
  const canonicalUrl = nullableString(record.canonicalUrl, `${owner}.canonicalUrl`, 2048);
  if (canonicalUrl !== null) {
    try {
      new URL(canonicalUrl);
    } catch {
      fail(`${owner}.canonicalUrl`, "must be an absolute URI");
    }
  }
  const contentHash = nullableString(record.contentHash, `${owner}.contentHash`, 64);
  if (contentHash !== null && !CONTENT_HASH.test(contentHash)) {
    fail(`${owner}.contentHash`, "must be a SHA-256 hex value");
  }
  return {
    schemaVersion: schemaVersion(record.schemaVersion, `${owner}.schemaVersion`),
    sourceDocumentId: identifier(record.sourceDocumentId, `${owner}.sourceDocumentId`),
    sourceType: enumValue(record.sourceType, SOURCE_TYPES, `${owner}.sourceType`),
    publisher: nullableString(record.publisher, `${owner}.publisher`, 200),
    title: text(record.title, `${owner}.title`, 500),
    canonicalUrl,
    publishedAt: nullableInstant(record.publishedAt, `${owner}.publishedAt`),
    provider: text(record.provider, `${owner}.provider`, 100),
    externalId: nullableString(record.externalId, `${owner}.externalId`, 256),
    contentHash,
    licenseClass: text(record.licenseClass, `${owner}.licenseClass`, 100),
    dataMode: dataMode(record.dataMode, `${owner}.dataMode`),
    capturedAt: callAuditInstant(record.capturedAt, `${owner}.capturedAt`),
    provenanceId: identifier(record.provenanceId, `${owner}.provenanceId`),
  };
}

function adaptSourceReference(value: unknown): SourceReference {
  const owner = "Call detail.source.reference";
  const record = closedRecord(value, [
    "schemaVersion", "sourceReferenceId", "sourceDocumentId", "page", "startMs", "endMs",
    "extractedFragment", "extractionConfidence", "verified", "dataMode", "capturedAt", "provenanceId",
  ], owner);
  const startMs = nullableInteger(record.startMs, `${owner}.startMs`, 0);
  const endMs = nullableInteger(record.endMs, `${owner}.endMs`, 0);
  if ((startMs === null) !== (endMs === null)) {
    fail(owner, "startMs and endMs must be supplied together");
  }
  if (endMs !== null && startMs !== null && endMs <= startMs) {
    fail(owner, "endMs must follow startMs");
  }
  const extractionConfidence = nullableNumber(record.extractionConfidence, `${owner}.extractionConfidence`);
  if (extractionConfidence !== null && (extractionConfidence < 0 || extractionConfidence > 1)) {
    fail(`${owner}.extractionConfidence`, "must be between 0 and 1");
  }
  return {
    schemaVersion: schemaVersion(record.schemaVersion, `${owner}.schemaVersion`),
    sourceReferenceId: identifier(record.sourceReferenceId, `${owner}.sourceReferenceId`),
    sourceDocumentId: identifier(record.sourceDocumentId, `${owner}.sourceDocumentId`),
    page: nullableInteger(record.page, `${owner}.page`, 1),
    startMs,
    endMs,
    extractedFragment: nullableString(record.extractedFragment, `${owner}.extractedFragment`, 4000),
    extractionConfidence,
    verified: booleanValue(record.verified, `${owner}.verified`),
    dataMode: dataMode(record.dataMode, `${owner}.dataMode`),
    capturedAt: callAuditInstant(record.capturedAt, `${owner}.capturedAt`),
    provenanceId: identifier(record.provenanceId, `${owner}.provenanceId`),
  };
}

function adaptSource(value: unknown): SourceEvidence {
  const record = closedRecord(value, ["document", "reference"], "Call detail.source");
  return {
    document: adaptSourceDocument(record.document),
    reference: adaptSourceReference(record.reference),
  };
}

function adaptMarketSnapshot(value: unknown): MarketSnapshot | null {
  if (value === null) return null;
  const owner = "Call detail.snapshot";
  const record = closedRecord(value, [
    "schemaVersion", "snapshotId", "callId", "assetId", "eventTime", "processingTime", "assetPrice",
    "spx", "ndx", "vix", "treasury2y", "treasury10y", "realYield", "dxy", "wti", "gold",
    "volatility", "distanceFrom52WeekHigh", "distanceFromAth", "immutable", "dataMode", "capturedAt",
    "provenanceId",
  ], owner);
  const eventTime = callAuditInstant(record.eventTime, `${owner}.eventTime`);
  const processingTime = callAuditInstant(record.processingTime, `${owner}.processingTime`);
  const capturedAt = callAuditInstant(record.capturedAt, `${owner}.capturedAt`);
  assertChronology(eventTime, processingTime, capturedAt, owner);
  if (record.immutable !== true) fail(`${owner}.immutable`, "must equal true");
  return {
    schemaVersion: schemaVersion(record.schemaVersion, `${owner}.schemaVersion`),
    snapshotId: identifier(record.snapshotId, `${owner}.snapshotId`),
    callId: identifier(record.callId, `${owner}.callId`),
    assetId: identifier(record.assetId, `${owner}.assetId`),
    eventTime,
    processingTime,
    assetPrice: nullablePositiveNumber(record.assetPrice, `${owner}.assetPrice`),
    spx: nullableNumber(record.spx, `${owner}.spx`),
    ndx: nullableNumber(record.ndx, `${owner}.ndx`),
    vix: nullableNumber(record.vix, `${owner}.vix`),
    treasury2y: nullableNumber(record.treasury2y, `${owner}.treasury2y`),
    treasury10y: nullableNumber(record.treasury10y, `${owner}.treasury10y`),
    realYield: nullableNumber(record.realYield, `${owner}.realYield`),
    dxy: nullableNumber(record.dxy, `${owner}.dxy`),
    wti: nullableNumber(record.wti, `${owner}.wti`),
    gold: nullableNumber(record.gold, `${owner}.gold`),
    volatility: nullableNumber(record.volatility, `${owner}.volatility`),
    distanceFrom52WeekHigh: nullableNumber(record.distanceFrom52WeekHigh, `${owner}.distanceFrom52WeekHigh`),
    distanceFromAth: nullableNumber(record.distanceFromAth, `${owner}.distanceFromAth`),
    immutable: true,
    dataMode: dataMode(record.dataMode, `${owner}.dataMode`),
    capturedAt,
    provenanceId: identifier(record.provenanceId, `${owner}.provenanceId`),
  };
}

function adaptCallViewRecord(record: JsonRecord): AnalystCallView {
  const view: AnalystCallView = {
    call: adaptCall(record.call),
    institution: adaptInstitution(record.institution),
    analyst: adaptAnalyst(record.analyst),
    asset: adaptAsset(record.asset),
    source: adaptSource(record.source),
  };
  validateDetailJoins({ ...view, snapshot: null });
  return view;
}

export function adaptCallViewResponse(value: unknown): AnalystCallView {
  const record = closedRecord(
    value,
    ["call", "institution", "analyst", "asset", "source"],
    "Call view response",
  );
  return adaptCallViewRecord(record);
}

export function adaptCallDetailResponse(value: unknown): AnalystCallDetail {
  const record = closedRecord(
    value,
    ["call", "institution", "analyst", "asset", "source", "snapshot"],
    "Call detail response",
  );
  const detail: AnalystCallDetail = {
    ...adaptCallViewRecord(record),
    snapshot: adaptMarketSnapshot(record.snapshot),
  };
  validateDetailJoins(detail);
  return detail;
}

function adaptMacroObservation(value: unknown, index: number): MacroObservation {
  const owner = `Call context.macroSnapshot.observations[${index}]`;
  const record = closedRecord(value, [
    "schemaVersion", "macroObservationId", "series", "value", "unit", "observationDate", "releasedAt",
    "processingTime", "vintageStart", "vintageEnd", "sourceReferenceId", "dataMode", "capturedAt",
    "provenanceId",
  ], owner);
  const releasedAt = callAuditInstant(record.releasedAt, `${owner}.releasedAt`);
  const processingTime = callAuditInstant(record.processingTime, `${owner}.processingTime`);
  const capturedAt = callAuditInstant(record.capturedAt, `${owner}.capturedAt`);
  assertChronology(releasedAt, processingTime, capturedAt, owner);
  const vintageStart = nullableCalendarDate(record.vintageStart, `${owner}.vintageStart`);
  const vintageEnd = nullableCalendarDate(record.vintageEnd, `${owner}.vintageEnd`);
  if (vintageStart !== null && vintageEnd !== null && vintageStart > vintageEnd) {
    fail(owner, "vintageStart must not follow vintageEnd");
  }
  return {
    schemaVersion: schemaVersion(record.schemaVersion, `${owner}.schemaVersion`),
    macroObservationId: identifier(record.macroObservationId, `${owner}.macroObservationId`),
    series: enumValue(record.series, MACRO_SERIES, `${owner}.series`),
    value: nullableMacroNumber(record.value, `${owner}.value`),
    unit: enumValue(record.unit, MACRO_UNITS, `${owner}.unit`),
    observationDate: calendarDate(record.observationDate, `${owner}.observationDate`),
    releasedAt,
    processingTime,
    vintageStart,
    vintageEnd,
    sourceReferenceId: identifier(record.sourceReferenceId, `${owner}.sourceReferenceId`),
    dataMode: dataMode(record.dataMode, `${owner}.dataMode`),
    capturedAt,
    provenanceId: identifier(record.provenanceId, `${owner}.provenanceId`),
  };
}

function adaptMacroSnapshot(value: unknown): MacroSnapshot | null {
  if (value === null) return null;
  const owner = "Call context.macroSnapshot";
  const record = closedRecord(value, [
    "schemaVersion", "macroSnapshotId", "callId", "eventTime", "processingTime", "observations",
    "immutable", "dataMode", "capturedAt", "provenanceId",
  ], owner);
  if (!Array.isArray(record.observations) || record.observations.length !== MACRO_SERIES.length) {
    fail(`${owner}.observations`, `must contain exactly ${MACRO_SERIES.length} records`);
  }
  const eventTime = callAuditInstant(record.eventTime, `${owner}.eventTime`);
  const processingTime = callAuditInstant(record.processingTime, `${owner}.processingTime`);
  const capturedAt = callAuditInstant(record.capturedAt, `${owner}.capturedAt`);
  assertChronology(eventTime, processingTime, capturedAt, owner);
  if (record.immutable !== true) fail(`${owner}.immutable`, "must equal true");
  const observations = record.observations.map(adaptMacroObservation);
  const eventDate = eventTime.slice(0, 10);
  observations.forEach((observation, index) => {
    if (observation.series !== MACRO_SERIES[index]) {
      fail(`${owner}.observations[${index}]`, `must be ${MACRO_SERIES[index]}`);
    }
    if (observation.dataMode !== record.dataMode) {
      fail(`${owner}.observations[${index}]`, "must preserve snapshot data mode");
    }
    if (
      instantOrder(observation.releasedAt) > instantOrder(eventTime) ||
      instantOrder(observation.processingTime) > instantOrder(processingTime) ||
      instantOrder(observation.capturedAt) > instantOrder(capturedAt)
    ) {
      fail(`${owner}.observations[${index}]`, "must not contain future evidence");
    }
    if (
      (observation.vintageStart !== null && eventDate < observation.vintageStart) ||
      (observation.vintageEnd !== null && eventDate > observation.vintageEnd)
    ) {
      fail(`${owner}.observations[${index}]`, "must be active on the snapshot event date");
    }
  });
  return {
    schemaVersion: schemaVersion(record.schemaVersion, `${owner}.schemaVersion`),
    macroSnapshotId: identifier(record.macroSnapshotId, `${owner}.macroSnapshotId`),
    callId: identifier(record.callId, `${owner}.callId`),
    eventTime,
    processingTime,
    observations,
    immutable: true,
    dataMode: dataMode(record.dataMode, `${owner}.dataMode`),
    capturedAt,
    provenanceId: identifier(record.provenanceId, `${owner}.provenanceId`),
  };
}

function adaptEventContext(value: unknown): EventContext | null {
  if (value === null) return null;
  const owner = "Call context.eventContext";
  const record = closedRecord(value, [
    "schemaVersion", "eventContextId", "callId", "eventTime", "processingTime", "earningsAt", "nextCpiAt",
    "nextFomcAt", "nextNfpAt", "optionsExpirationAt", "sourceReferenceId", "immutable", "dataMode",
    "capturedAt", "provenanceId",
  ], owner);
  const eventTime = callAuditInstant(record.eventTime, `${owner}.eventTime`);
  const processingTime = callAuditInstant(record.processingTime, `${owner}.processingTime`);
  const capturedAt = callAuditInstant(record.capturedAt, `${owner}.capturedAt`);
  assertChronology(eventTime, processingTime, capturedAt, owner);
  if (record.immutable !== true) fail(`${owner}.immutable`, "must equal true");
  const context: EventContext = {
    schemaVersion: schemaVersion(record.schemaVersion, `${owner}.schemaVersion`),
    eventContextId: identifier(record.eventContextId, `${owner}.eventContextId`),
    callId: identifier(record.callId, `${owner}.callId`),
    eventTime,
    processingTime,
    earningsAt: nullableInstant(record.earningsAt, `${owner}.earningsAt`),
    nextCpiAt: nullableInstant(record.nextCpiAt, `${owner}.nextCpiAt`),
    nextFomcAt: nullableInstant(record.nextFomcAt, `${owner}.nextFomcAt`),
    nextNfpAt: nullableInstant(record.nextNfpAt, `${owner}.nextNfpAt`),
    optionsExpirationAt: nullableInstant(record.optionsExpirationAt, `${owner}.optionsExpirationAt`),
    sourceReferenceId: identifier(record.sourceReferenceId, `${owner}.sourceReferenceId`),
    immutable: true,
    dataMode: dataMode(record.dataMode, `${owner}.dataMode`),
    capturedAt,
    provenanceId: identifier(record.provenanceId, `${owner}.provenanceId`),
  };
  for (const [field, scheduledAt] of [
    ["nextCpiAt", context.nextCpiAt],
    ["nextFomcAt", context.nextFomcAt],
    ["nextNfpAt", context.nextNfpAt],
    ["optionsExpirationAt", context.optionsExpirationAt],
  ] as const) {
    if (scheduledAt !== null && instantOrder(scheduledAt) < instantOrder(eventTime)) {
      fail(`${owner}.${field}`, "must not precede eventTime");
    }
  }
  return context;
}

export function adaptCallContextResponse(value: unknown): CallContext {
  const record = closedRecord(value, ["macroSnapshot", "eventContext"], "Call context response");
  return {
    macroSnapshot: adaptMacroSnapshot(record.macroSnapshot),
    eventContext: adaptEventContext(record.eventContext),
  };
}

function adaptCorrectedTerms(value: unknown, owner: string): CorrectedCallTerms {
  const record = closedRecord(value, [
    "direction", "originalRating", "previousTarget", "target", "currency", "targetDate",
  ], owner);
  const previousTarget = nullablePositiveNumber(record.previousTarget, `${owner}.previousTarget`);
  const target = nullablePositiveNumber(record.target, `${owner}.target`);
  const currency = nullableString(record.currency, `${owner}.currency`, 3);
  if (currency !== null && !CURRENCY.test(currency)) fail(`${owner}.currency`, "must be an ISO currency code");
  if ((previousTarget !== null || target !== null) && currency === null) {
    fail(owner, "currency is required when a target is present");
  }
  return {
    direction: enumValue(record.direction, CALL_DIRECTIONS, `${owner}.direction`),
    originalRating: nullableString(record.originalRating, `${owner}.originalRating`, 200),
    previousTarget,
    target,
    currency,
    targetDate: nullableCalendarDate(record.targetDate, `${owner}.targetDate`),
  };
}

function adaptRevision(value: unknown, index: number, expectedCallId: string): CallRevision {
  const owner = `Call revisions[${index}]`;
  const record = closedRecord(value, [
    "revisionId", "schemaVersion", "callId", "supersedesRevisionId", "sequenceNumber", "provider",
    "providerEventId", "revisionType", "eventTime", "processingTime", "correctedTerms", "reason",
    "sourceReferenceId", "dataMode", "capturedAt", "provenanceId",
  ], owner);
  const callId = identifier(record.callId, `${owner}.callId`);
  if (callId !== expectedCallId) fail(`${owner}.callId`, `must equal ${expectedCallId}`);
  const revisionType = enumValue(record.revisionType, CALL_REVISION_TYPES, `${owner}.revisionType`);
  const correctedTerms = record.correctedTerms === null
    ? null
    : adaptCorrectedTerms(record.correctedTerms, `${owner}.correctedTerms`);
  if (revisionType === "CORRECTION" && correctedTerms === null) {
    fail(owner, "must carry correctedTerms for a CORRECTION");
  }
  if (revisionType === "CANCELLATION" && correctedTerms !== null) {
    fail(owner, "must not carry correctedTerms for a CANCELLATION");
  }
  const eventTime = callAuditInstant(record.eventTime, `${owner}.eventTime`);
  const processingTime = callAuditInstant(record.processingTime, `${owner}.processingTime`);
  const capturedAt = callAuditInstant(record.capturedAt, `${owner}.capturedAt`);
  assertChronology(eventTime, processingTime, capturedAt, owner);
  return {
    revisionId: identifier(record.revisionId, `${owner}.revisionId`),
    schemaVersion: schemaVersion(record.schemaVersion, `${owner}.schemaVersion`),
    callId,
    supersedesRevisionId: nullableIdentifier(record.supersedesRevisionId, `${owner}.supersedesRevisionId`),
    sequenceNumber: integer(record.sequenceNumber, `${owner}.sequenceNumber`, 1),
    provider: text(record.provider, `${owner}.provider`, 100),
    providerEventId: text(record.providerEventId, `${owner}.providerEventId`, 256),
    revisionType,
    eventTime,
    processingTime,
    correctedTerms,
    reason: text(record.reason, `${owner}.reason`, 2000),
    sourceReferenceId: identifier(record.sourceReferenceId, `${owner}.sourceReferenceId`),
    dataMode: dataMode(record.dataMode, `${owner}.dataMode`),
    capturedAt,
    provenanceId: identifier(record.provenanceId, `${owner}.provenanceId`),
  };
}

export function adaptCallRevisionsResponse(value: unknown, expectedCallId: string): readonly CallRevision[] {
  identifier(expectedCallId, "Expected call ID");
  if (!Array.isArray(value)) fail("Call revisions response", "must be an array");
  const revisions = value.map((revision, index) => adaptRevision(revision, index, expectedCallId));
  validateRevisionLineage(revisions, expectedCallId);
  return revisions;
}

function validateDetailJoins(detail: AnalystCallDetail) {
  const { call, institution, analyst, asset, source, snapshot } = detail;
  if (institution.institutionId !== call.institutionId) fail("Call detail", "has an institution join mismatch");
  if ((analyst?.analystId ?? null) !== call.analystId) fail("Call detail", "has an analyst join mismatch");
  if (asset.assetId !== call.assetId) fail("Call detail", "has an asset join mismatch");
  if (source.reference.sourceReferenceId !== call.sourceReferenceId) {
    fail("Call detail", "has a source-reference join mismatch");
  }
  if (source.reference.sourceDocumentId !== source.document.sourceDocumentId) {
    fail("Call detail", "has a source-document join mismatch");
  }
  for (const [owner, mode] of [
    ["source document", source.document.dataMode],
    ["source reference", source.reference.dataMode],
  ] as const) {
    if (mode !== call.dataMode) fail("Call detail", `has a ${owner} data-mode mismatch`);
  }
  if (snapshot) {
    if (
      snapshot.callId !== call.callId ||
      snapshot.assetId !== call.assetId ||
      snapshot.eventTime !== call.eventTime ||
      snapshot.dataMode !== call.dataMode
    ) {
      fail("Call detail", "has a snapshot identity, event-time, or data-mode mismatch");
    }
  }
}

function validateContextJoins(detail: AnalystCallDetail, context: CallContext) {
  const { call } = detail;
  for (const [owner, item] of [
    ["macro snapshot", context.macroSnapshot],
    ["event context", context.eventContext],
  ] as const) {
    if (!item) continue;
    if (item.callId !== call.callId || item.eventTime !== call.eventTime || item.dataMode !== call.dataMode) {
      fail("Call audit snapshot", `has a ${owner} identity, event-time, or data-mode mismatch`);
    }
  }
  if (context.macroSnapshot) {
    for (const observation of context.macroSnapshot.observations) {
      if (observation.dataMode !== call.dataMode) {
        fail("Call audit snapshot", "has a macro observation data-mode mismatch");
      }
    }
  }
}

function validateRevisionLineage(revisions: readonly CallRevision[], expectedCallId: string) {
  const revisionIds = new Set<string>();
  const providerEvents = new Set<string>();
  let previous: CallRevision | null = null;
  let cancellationSeen = false;
  revisions.forEach((revision, index) => {
    if (revision.callId !== expectedCallId) fail(`Revision ${revision.revisionId}`, "has a call join mismatch");
    if (revision.sequenceNumber !== index + 1) fail(`Revision ${revision.revisionId}`, "has a sequence gap or reordering");
    if ((index === 0 ? null : previous?.revisionId) !== revision.supersedesRevisionId) {
      fail(`Revision ${revision.revisionId}`, "does not supersede the immediately preceding revision");
    }
    if (revisionIds.has(revision.revisionId)) fail(`Revision ${revision.revisionId}`, "duplicates a revision ID");
    const providerEventIdentity = `${revision.provider}\u0000${revision.providerEventId}`;
    if (providerEvents.has(providerEventIdentity)) {
      fail(`Revision ${revision.revisionId}`, "duplicates a provider event ID");
    }
    if (previous && instantOrder(revision.eventTime) < instantOrder(previous.eventTime)) {
      fail(`Revision ${revision.revisionId}`, "has an eventTime earlier than the prior revision");
    }
    if (cancellationSeen) fail(`Revision ${revision.revisionId}`, "appears after a terminal cancellation");
    revisionIds.add(revision.revisionId);
    providerEvents.add(providerEventIdentity);
    cancellationSeen = revision.revisionType === "CANCELLATION";
    previous = revision;
  });
}

export function validateCallAuditSnapshot(snapshot: CallAuditSnapshot): CallAuditSnapshot {
  const { detail, context, revisions } = snapshot;
  validateDetailJoins(detail);
  validateContextJoins(detail, context);
  validateRevisionLineage(revisions, detail.call.callId);
  if (detail.call.dataMode !== "DEMO") {
    fail("Call audit snapshot", "must remain DEMO until a later provider-publication phase");
  }
  const originalEventTime = instantOrder(callAuditInstant(detail.call.eventTime, "Call eventTime"));
  for (const revision of revisions) {
    if (revision.dataMode !== detail.call.dataMode) {
      fail(`Revision ${revision.revisionId}`, "has a data-mode mismatch with the call");
    }
    if (instantOrder(callAuditInstant(revision.eventTime, `Revision ${revision.revisionId} eventTime`)) < originalEventTime) {
      fail(`Revision ${revision.revisionId}`, "predates the original call event");
    }
  }
  return snapshot;
}
