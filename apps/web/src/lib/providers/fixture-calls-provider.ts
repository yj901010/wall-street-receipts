import analystCallsFixtureJson from "../../../../../fixtures/v1/analyst-calls.json";
import callContextsFixtureJson from "../../../../../fixtures/v1/call-contexts.json";
import marketSnapshotsFixtureJson from "../../../../../fixtures/v1/market-snapshots.json";
import masterDataFixtureJson from "../../../../../fixtures/v1/master-data.json";
import { readDataMode } from "@/lib/data-mode";
import {
  CALL_DIRECTIONS,
  MACRO_SERIES,
  CALL_STATUSES,
  type AnalystCall,
  type AnalystCallDetail,
  type AnalystCallPage,
  type AnalystCallView,
  type AnalystSummary,
  type AssetSummary,
  type CallsMetadata,
  type CallsProvider,
  type CallsQuery,
  type CallContext,
  type CallSortField,
  type EventContext,
  type InstitutionSummary,
  type MacroObservation,
  type MacroSnapshot,
  type MarketSnapshot,
  type SourceDocument,
  type SourceEvidence,
  type SourceReference,
  type SortOrder,
} from "./calls-provider";

type AnalystCallFixture = Omit<AnalystCall, "schemaVersion" | "dataMode"> & { dataMode: string };
type SourceDocumentFixture = Omit<SourceDocument, "schemaVersion" | "dataMode"> & { dataMode: string };
type SourceReferenceFixture = Omit<SourceReference, "schemaVersion" | "dataMode"> & { dataMode: string };
type SnapshotFixture = Omit<MarketSnapshot, "schemaVersion" | "dataMode" | "immutable"> & {
  dataMode: string;
  immutable: boolean;
};
type MacroObservationFixture = Omit<MacroObservation, "schemaVersion" | "dataMode"> & {
  dataMode: string;
};
type MacroSnapshotFixture = Omit<
  MacroSnapshot,
  "schemaVersion" | "dataMode" | "immutable" | "observations"
> & {
  observationIds: string[];
  dataMode: string;
  immutable: boolean;
};
type EventContextFixture = Omit<EventContext, "schemaVersion" | "dataMode" | "immutable"> & {
  dataMode: string;
  immutable: boolean;
};

type AnalystCallsFixture = {
  schemaVersion: "1.0.0";
  generatedAt: string;
  dataMode: string;
  provenance: { id: string };
  calls: AnalystCallFixture[];
  sourceDocuments: SourceDocumentFixture[];
  sourceReferences: SourceReferenceFixture[];
  disclaimer: string;
};

type MarketSnapshotsFixture = {
  schemaVersion: "1.0.0";
  snapshots: SnapshotFixture[];
};

type CallContextsFixture = {
  schemaVersion: "1.0.0";
  fixtureVersion: string;
  dataMode: string;
  generatedAt: string;
  provenance: { id: string };
  sourceDocuments: SourceDocumentFixture[];
  sourceReferences: SourceReferenceFixture[];
  macroObservations: MacroObservationFixture[];
  macroSnapshots: MacroSnapshotFixture[];
  eventContexts: EventContextFixture[];
  knownEmptyCallIds: string[];
  disclaimer: string;
};

type MasterDataFixture = {
  institutions: Array<InstitutionSummary & { country: string; active: boolean }>;
  analysts: Array<AnalystSummary & { active: boolean }>;
  assets: Array<AssetSummary & { primaryCurrency: string; active: boolean }>;
};

const analystCallsFixture = analystCallsFixtureJson as AnalystCallsFixture;
const callContextsFixture = callContextsFixtureJson as CallContextsFixture;
const marketSnapshotsFixture = marketSnapshotsFixtureJson as MarketSnapshotsFixture;
const masterDataFixture = masterDataFixtureJson as MasterDataFixture;

const DEFAULT_PAGE_SIZE = 25;
const MAX_PAGE_SIZE = 100;
const utcInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;
const queryUtcInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/;
const offsetInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?([+-])(\d{2}):(\d{2})$/;

function normalized(value: string | undefined) {
  return value?.trim().toLocaleLowerCase("en-US") ?? "";
}

function positiveInteger(value: number | undefined, fallback: number, maximum: number) {
  if (!Number.isFinite(value) || !value || value < 1) {
    return fallback;
  }

  return Math.min(Math.trunc(value), maximum);
}

function nonNegativeInteger(value: number | undefined, fallback: number) {
  if (!Number.isFinite(value) || value === undefined || value < 0) {
    return fallback;
  }

  return Math.trunc(value);
}

function canonicalInstant(value: string, owner: string) {
  const match = utcInstantPattern.exec(value);

  if (!match) {
    throw new Error(`${owner} has an invalid UTC instant: ${value}.`);
  }

  const parsed = Date.parse(`${match[1]}Z`);
  if (!Number.isFinite(parsed) || new Date(parsed).toISOString() !== `${match[1]}.000Z`) {
    throw new Error(`${owner} has an invalid UTC instant: ${value}.`);
  }

  const nanoseconds = (match[2] ?? "").padEnd(9, "0");
  return BigInt(parsed) * 1_000_000n + BigInt(nanoseconds || "0");
}

function instant(value: string | undefined, field: "from" | "to") {
  if (!value) {
    return null;
  }

  const canonicalMatch = queryUtcInstantPattern.exec(value);
  if (canonicalMatch) {
    const parsed = Date.parse(`${canonicalMatch[1]}Z`);
    if (!Number.isFinite(parsed) || new Date(parsed).toISOString() !== `${canonicalMatch[1]}.000Z`) {
      throw new Error(`Invalid ${field} instant: ${value}`);
    }
    const nanoseconds = (canonicalMatch[2] ?? "").padEnd(9, "0");
    return BigInt(parsed) * 1_000_000n + BigInt(nanoseconds || "0");
  }

  const offsetMatch = offsetInstantPattern.exec(value);
  if (!offsetMatch) {
    throw new Error(`Invalid ${field} instant: ${value}`);
  }

  const localSecond = Date.parse(`${offsetMatch[1]}Z`);
  const offsetHours = Number(offsetMatch[4]);
  const offsetMinutes = Number(offsetMatch[5]);
  if (
    !Number.isFinite(localSecond) ||
    new Date(localSecond).toISOString() !== `${offsetMatch[1]}.000Z` ||
    offsetHours > 18 ||
    offsetMinutes > 59 ||
    (offsetHours === 18 && offsetMinutes !== 0)
  ) {
    throw new Error(`Invalid ${field} instant: ${value}`);
  }

  const direction = offsetMatch[3] === "+" ? 1 : -1;
  const offsetMilliseconds = direction * (offsetHours * 60 + offsetMinutes) * 60_000;
  const utcSecond = localSecond - offsetMilliseconds;
  if (!Number.isSafeInteger(utcSecond)) {
    throw new Error(`Invalid ${field} instant: ${value}`);
  }

  const nanoseconds = (offsetMatch[2] ?? "").padEnd(9, "0");
  return BigInt(utcSecond) * 1_000_000n + BigInt(nanoseconds || "0");
}

export function compareFixtureCallInstants(left: string, right: string) {
  const leftInstant = canonicalInstant(left, "Fixture call");
  const rightInstant = canonicalInstant(right, "Fixture call");
  return leftInstant < rightInstant ? -1 : leftInstant > rightInstant ? 1 : 0;
}

type FixtureCallSortRecord = Pick<
  AnalystCall,
  "callId" | "eventTime" | "processingTime" | "capturedAt"
>;

export function compareFixtureCallRecords(
  left: FixtureCallSortRecord,
  right: FixtureCallSortRecord,
  sort: CallSortField,
  order: SortOrder,
) {
  const primary = compareFixtureCallInstants(left[sort], right[sort]);
  if (primary !== 0) return order === "asc" ? primary : -primary;
  return left.callId < right.callId ? -1 : left.callId > right.callId ? 1 : 0;
}

export function fixtureCallMatchesEventRange(
  eventTime: string,
  from?: string,
  to?: string,
) {
  const eventInstant = canonicalInstant(eventTime, "Fixture call");
  const fromInstant = instant(from, "from");
  const toInstant = instant(to, "to");

  if (fromInstant !== null && toInstant !== null && fromInstant >= toInstant) {
    throw new Error("Invalid event-time range: to must be later than from.");
  }

  return (
    (fromInstant === null || eventInstant >= fromInstant) &&
    (toInstant === null || eventInstant < toInstant)
  );
}

function canonicalCall(call: AnalystCallFixture): AnalystCall {
  return {
    ...call,
    schemaVersion: analystCallsFixture.schemaVersion,
    dataMode: readDataMode(call.dataMode),
  };
}

function evidenceFor(call: AnalystCall): SourceEvidence {
  const reference = analystCallsFixture.sourceReferences.find(
    (candidate) => candidate.sourceReferenceId === call.sourceReferenceId,
  );
  const document = analystCallsFixture.sourceDocuments.find(
    (candidate) => candidate.sourceDocumentId === reference?.sourceDocumentId,
  );

  if (!reference || !document) {
    throw new Error(`Fixture call ${call.callId} has incomplete source provenance.`);
  }

  return {
    document: {
      ...document,
      schemaVersion: analystCallsFixture.schemaVersion,
      dataMode: readDataMode(document.dataMode),
    },
    reference: {
      ...reference,
      schemaVersion: analystCallsFixture.schemaVersion,
      dataMode: readDataMode(reference.dataMode),
    },
  };
}

function callView(callFixture: AnalystCallFixture): AnalystCallView {
  const call = canonicalCall(callFixture);
  const institution = masterDataFixture.institutions.find(
    (candidate) => candidate.institutionId === call.institutionId,
  );
  const analyst = masterDataFixture.analysts.find(
    (candidate) => candidate.analystId === call.analystId,
  );
  const asset = masterDataFixture.assets.find((candidate) => candidate.assetId === call.assetId);

  if (!institution || !asset) {
    throw new Error(`Fixture call ${call.callId} has unresolved canonical master data.`);
  }

  return {
    call,
    institution: {
      institutionId: institution.institutionId,
      canonicalName: institution.canonicalName,
      slug: institution.slug,
    },
    analyst: analyst
      ? { analystId: analyst.analystId, canonicalName: analyst.canonicalName }
      : null,
    asset: {
      assetId: asset.assetId,
      assetType: asset.assetType,
      canonicalName: asset.canonicalName,
      ticker: asset.ticker,
    },
    source: evidenceFor(call),
  };
}

function canonicalSnapshot(snapshot: SnapshotFixture): MarketSnapshot {
  if (!snapshot.immutable) {
    throw new Error(`Fixture snapshot ${snapshot.snapshotId} violates the immutable contract.`);
  }

  return {
    ...snapshot,
    schemaVersion: marketSnapshotsFixture.schemaVersion,
    immutable: true,
    dataMode: readDataMode(snapshot.dataMode),
  };
}

function fixtureInstant(value: string) {
  return canonicalInstant(value, "Fixture context");
}

function occursAfter(candidate: string, cutoff: string) {
  return fixtureInstant(candidate) > fixtureInstant(cutoff);
}

function assertContextIdentity(dataMode: string, provenanceId: string, ownerId: string) {
  if (
    dataMode !== callContextsFixture.dataMode ||
    provenanceId !== callContextsFixture.provenance.id
  ) {
    throw new Error(`Context record ${ownerId} does not match fixture provenance.`);
  }
}

function assertContextSource(
  sourceReferenceId: string,
  ownerId: string,
  dataMode: string,
  provenanceId: string,
  ownerCapturedAt: string,
) {
  const reference = callContextsFixture.sourceReferences.find(
    (candidate) => candidate.sourceReferenceId === sourceReferenceId,
  );
  const document = callContextsFixture.sourceDocuments.find(
    (candidate) => candidate.sourceDocumentId === reference?.sourceDocumentId,
  );

  if (!reference || !document) {
    throw new Error(`Context record ${ownerId} has incomplete source provenance.`);
  }
  if (
    reference.dataMode !== dataMode ||
    document.dataMode !== dataMode ||
    reference.provenanceId !== provenanceId ||
    document.provenanceId !== provenanceId
  ) {
    throw new Error(`Context record ${ownerId} has inconsistent source provenance.`);
  }
  if (
    occursAfter(reference.capturedAt, ownerCapturedAt) ||
    occursAfter(document.capturedAt, ownerCapturedAt)
  ) {
    throw new Error(`Context record ${ownerId} was captured before its source evidence.`);
  }
}

function canonicalMacroObservation(observation: MacroObservationFixture): MacroObservation {
  assertContextIdentity(observation.dataMode, observation.provenanceId, observation.macroObservationId);
  assertContextSource(
    observation.sourceReferenceId,
    observation.macroObservationId,
    observation.dataMode,
    observation.provenanceId,
    observation.capturedAt,
  );

  if (
    occursAfter(observation.releasedAt, observation.processingTime) ||
    occursAfter(observation.processingTime, observation.capturedAt)
  ) {
    throw new Error(`Fixture macro observation ${observation.macroObservationId} has invalid timing.`);
  }

  return {
    ...observation,
    schemaVersion: callContextsFixture.schemaVersion,
    dataMode: readDataMode(observation.dataMode),
  };
}

function canonicalMacroSnapshot(snapshot: MacroSnapshotFixture): MacroSnapshot {
  if (!snapshot.immutable) {
    throw new Error(`Fixture macro snapshot ${snapshot.macroSnapshotId} violates the immutable contract.`);
  }
  assertContextIdentity(snapshot.dataMode, snapshot.provenanceId, snapshot.macroSnapshotId);

  if (
    occursAfter(snapshot.eventTime, snapshot.processingTime) ||
    occursAfter(snapshot.processingTime, snapshot.capturedAt)
  ) {
    throw new Error(`Fixture macro snapshot ${snapshot.macroSnapshotId} has invalid timing.`);
  }

  const activeDate = new Date(snapshot.eventTime).toISOString().slice(0, 10);

  const observations = snapshot.observationIds.map((observationId) => {
    const observation = callContextsFixture.macroObservations.find(
      (candidate) => candidate.macroObservationId === observationId,
    );

    if (!observation) {
      throw new Error(`Fixture macro snapshot ${snapshot.macroSnapshotId} has an unknown observation.`);
    }

    if (
      occursAfter(observation.releasedAt, snapshot.eventTime) ||
      occursAfter(observation.processingTime, snapshot.processingTime) ||
      occursAfter(observation.capturedAt, snapshot.capturedAt)
    ) {
      throw new Error(`Fixture macro snapshot ${snapshot.macroSnapshotId} includes a future observation.`);
    }
    if (
      observation.dataMode !== snapshot.dataMode ||
      observation.provenanceId !== snapshot.provenanceId
    ) {
      throw new Error(`Fixture macro snapshot ${snapshot.macroSnapshotId} mixes observation provenance.`);
    }
    if (
      (observation.vintageStart !== null && activeDate < observation.vintageStart) ||
      (observation.vintageEnd !== null && activeDate > observation.vintageEnd)
    ) {
      throw new Error(`Fixture macro snapshot ${snapshot.macroSnapshotId} includes an inactive vintage.`);
    }

    return canonicalMacroObservation(observation);
  });

  if (
    observations.length !== MACRO_SERIES.length ||
    observations.some((observation, index) => observation.series !== MACRO_SERIES[index])
  ) {
    throw new Error(`Fixture macro snapshot ${snapshot.macroSnapshotId} violates series ordering.`);
  }

  return {
    schemaVersion: callContextsFixture.schemaVersion,
    macroSnapshotId: snapshot.macroSnapshotId,
    callId: snapshot.callId,
    eventTime: snapshot.eventTime,
    processingTime: snapshot.processingTime,
    observations,
    immutable: true,
    dataMode: readDataMode(snapshot.dataMode),
    capturedAt: snapshot.capturedAt,
    provenanceId: snapshot.provenanceId,
  };
}

function canonicalEventContext(context: EventContextFixture): EventContext {
  if (!context.immutable) {
    throw new Error(`Fixture event context ${context.eventContextId} violates the immutable contract.`);
  }

  assertContextIdentity(context.dataMode, context.provenanceId, context.eventContextId);
  assertContextSource(
    context.sourceReferenceId,
    context.eventContextId,
    context.dataMode,
    context.provenanceId,
    context.capturedAt,
  );

  if (
    occursAfter(context.eventTime, context.processingTime) ||
    occursAfter(context.processingTime, context.capturedAt)
  ) {
    throw new Error(`Fixture event context ${context.eventContextId} has invalid timing.`);
  }

  const futureSchedule = [
    context.nextCpiAt,
    context.nextFomcAt,
    context.nextNfpAt,
    context.optionsExpirationAt,
  ];
  if (futureSchedule.some((scheduledAt) => scheduledAt !== null && occursAfter(context.eventTime, scheduledAt))) {
    throw new Error(`Fixture event context ${context.eventContextId} includes a past next-event timestamp.`);
  }

  return {
    schemaVersion: callContextsFixture.schemaVersion,
    eventContextId: context.eventContextId,
    callId: context.callId,
    eventTime: context.eventTime,
    processingTime: context.processingTime,
    earningsAt: context.earningsAt,
    nextCpiAt: context.nextCpiAt,
    nextFomcAt: context.nextFomcAt,
    nextNfpAt: context.nextNfpAt,
    optionsExpirationAt: context.optionsExpirationAt,
    sourceReferenceId: context.sourceReferenceId,
    immutable: true,
    dataMode: readDataMode(context.dataMode),
    capturedAt: context.capturedAt,
    provenanceId: context.provenanceId,
  };
}

const allCalls = analystCallsFixture.calls.map(callView);

function assertCallContextCoverage() {
  const callIds = new Set(allCalls.map(({ call }) => call.callId));

  for (const knownEmptyCallId of callContextsFixture.knownEmptyCallIds) {
    if (!callIds.has(knownEmptyCallId)) {
      throw new Error(`Fixture context classifies unknown call ${knownEmptyCallId} as empty.`);
    }
  }

  for (const contextRecord of [
    ...callContextsFixture.macroSnapshots,
    ...callContextsFixture.eventContexts,
  ]) {
    if (!callIds.has(contextRecord.callId)) {
      throw new Error(`Fixture context references unknown call ${contextRecord.callId}.`);
    }
  }

  for (const callId of callIds) {
    const macroSnapshots = callContextsFixture.macroSnapshots.filter(
      (candidate) => candidate.callId === callId,
    );
    const eventContexts = callContextsFixture.eventContexts.filter(
      (candidate) => candidate.callId === callId,
    );
    const knownEmpty = callContextsFixture.knownEmptyCallIds.includes(callId);

    if (macroSnapshots.length > 1 || eventContexts.length > 1) {
      throw new Error(`Fixture call ${callId} has duplicate context records.`);
    }
    if (knownEmpty && (macroSnapshots.length > 0 || eventContexts.length > 0)) {
      throw new Error(`Fixture call ${callId} is both known-empty and populated.`);
    }
    if (!knownEmpty && macroSnapshots.length === 0 && eventContexts.length === 0) {
      throw new Error(`Fixture call ${callId} has no explicit context classification.`);
    }
  }
}

assertCallContextCoverage();

const metadata: CallsMetadata = {
  asOf: analystCallsFixture.generatedAt,
  dataMode: readDataMode(analystCallsFixture.dataMode),
  source: analystCallsFixture.provenance.id,
  disclaimer: analystCallsFixture.disclaimer,
  facets: {
    institutions: masterDataFixture.institutions
      .map(({ institutionId, canonicalName, slug }) => ({ institutionId, canonicalName, slug }))
      .sort((left, right) => left.canonicalName.localeCompare(right.canonicalName)),
    analysts: masterDataFixture.analysts
      .map(({ analystId, canonicalName }) => ({ analystId, canonicalName }))
      .sort((left, right) => left.canonicalName.localeCompare(right.canonicalName)),
    assets: masterDataFixture.assets
      .map(({ assetId, assetType, canonicalName, ticker }) => ({
        assetId,
        assetType,
        canonicalName,
        ticker,
      }))
      .sort((left, right) => (left.ticker ?? "").localeCompare(right.ticker ?? "")),
    directions: [...CALL_DIRECTIONS],
    statuses: [...CALL_STATUSES],
  },
};

export class FixtureCallsProvider implements CallsProvider {
  async list(query: CallsQuery = {}): Promise<AnalystCallPage> {
    const assetId = query.assetId ?? "";
    const ticker = normalized(query.ticker);
    const institutionId = query.institutionId ?? "";
    const analystId = query.analystId ?? "";
    const from = instant(query.from, "from");
    const to = instant(query.to, "to");

    if (from !== null && to !== null && from >= to) {
      throw new Error("Invalid event-time range: to must be later than from.");
    }

    const filtered = allCalls.filter(({ call, institution, analyst, asset }) => {
      const assetMatches = !assetId || asset.assetId === assetId;
      const tickerMatches = !ticker || asset.ticker?.toLocaleLowerCase("en-US") === ticker;
      const institutionMatches =
        !institutionId || institution.institutionId === institutionId;
      const analystMatches =
        !analystId || analyst?.analystId === analystId;
      const directionMatches = !query.direction || call.direction === query.direction;
      const statusMatches = !query.status || call.status === query.status;
      const dataModeMatches = !query.dataMode || call.dataMode === query.dataMode;
      const callEventTime = canonicalInstant(call.eventTime, `Fixture call ${call.callId}`);
      const fromMatches = from === null || callEventTime >= from;
      const toMatches = to === null || callEventTime < to;

      return (
        assetMatches &&
        tickerMatches &&
        institutionMatches &&
        analystMatches &&
        directionMatches &&
        statusMatches &&
        dataModeMatches &&
        fromMatches &&
        toMatches
      );
    });

    const sort = query.sort ?? "eventTime";
    const order = query.order ?? "desc";
    const sorted = [...filtered].sort((left, right) =>
      compareFixtureCallRecords(left.call, right.call, sort, order)
    );
    const size = positiveInteger(query.size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    const totalPages = Math.ceil(sorted.length / size);
    const page = nonNegativeInteger(query.page, 0);
    const start = page * size;

    return {
      items: sorted.slice(start, start + size),
      page: {
        number: page,
        size,
        totalElements: sorted.length,
        totalPages,
        first: page === 0,
        last: totalPages === 0 || page >= totalPages - 1,
        sort: { field: sort, order },
      },
    };
  }

  async metadata(): Promise<CallsMetadata> {
    return metadata;
  }

  async findById(id: string): Promise<AnalystCallDetail | null> {
    const call = allCalls.find((candidate) => candidate.call.callId === id);

    if (!call) {
      return null;
    }

    const fixtureSnapshot = marketSnapshotsFixture.snapshots.find(
      (candidate) => candidate.callId === call.call.callId,
    );

    return {
      ...call,
      snapshot: fixtureSnapshot ? canonicalSnapshot(fixtureSnapshot) : null,
    };
  }

  async findContextByCallId(id: string): Promise<CallContext | null> {
    const call = allCalls.find((candidate) => candidate.call.callId === id);

    if (!call) {
      return null;
    }

    const macroSnapshot = callContextsFixture.macroSnapshots.find(
      (candidate) => candidate.callId === id,
    );
    const eventContext = callContextsFixture.eventContexts.find(
      (candidate) => candidate.callId === id,
    );
    const knownEmpty = callContextsFixture.knownEmptyCallIds.includes(id);

    if (!macroSnapshot && !eventContext && !knownEmpty) {
      throw new Error(`Fixture call ${id} has no explicit context classification.`);
    }

    if (
      macroSnapshot &&
      fixtureInstant(macroSnapshot.eventTime) !== fixtureInstant(call.call.eventTime)
    ) {
      throw new Error(`Fixture macro snapshot for ${id} does not match the call event time.`);
    }
    if (
      eventContext &&
      fixtureInstant(eventContext.eventTime) !== fixtureInstant(call.call.eventTime)
    ) {
      throw new Error(`Fixture event context for ${id} does not match the call event time.`);
    }

    return {
      macroSnapshot: macroSnapshot ? canonicalMacroSnapshot(macroSnapshot) : null,
      eventContext: eventContext ? canonicalEventContext(eventContext) : null,
    };
  }
}

export function callsProvider(): CallsProvider {
  const configuredProvider = process.env.ANALYST_PROVIDER?.toLocaleLowerCase("en-US") ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported analyst provider: ${configuredProvider}`);
  }

  return new FixtureCallsProvider();
}
