import analystCallsFixtureJson from "../../../../../fixtures/v1/analyst-calls.json";
import marketSnapshotsFixtureJson from "../../../../../fixtures/v1/market-snapshots.json";
import masterDataFixtureJson from "../../../../../fixtures/v1/master-data.json";
import { readDataMode } from "@/lib/data-mode";
import {
  CALL_DIRECTIONS,
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
  type InstitutionSummary,
  type MarketSnapshot,
  type SourceDocument,
  type SourceEvidence,
  type SourceReference,
} from "./calls-provider";

type AnalystCallFixture = Omit<AnalystCall, "schemaVersion" | "dataMode"> & { dataMode: string };
type SourceDocumentFixture = Omit<SourceDocument, "schemaVersion" | "dataMode"> & { dataMode: string };
type SourceReferenceFixture = Omit<SourceReference, "schemaVersion" | "dataMode"> & { dataMode: string };
type SnapshotFixture = Omit<MarketSnapshot, "schemaVersion" | "dataMode" | "immutable"> & {
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

type MasterDataFixture = {
  institutions: Array<InstitutionSummary & { country: string; active: boolean }>;
  analysts: Array<AnalystSummary & { active: boolean }>;
  assets: Array<AssetSummary & { primaryCurrency: string; active: boolean }>;
};

const analystCallsFixture = analystCallsFixtureJson as AnalystCallsFixture;
const marketSnapshotsFixture = marketSnapshotsFixtureJson as MarketSnapshotsFixture;
const masterDataFixture = masterDataFixtureJson as MasterDataFixture;

const DEFAULT_PAGE_SIZE = 25;
const MAX_PAGE_SIZE = 100;

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

function instant(value: string | undefined, field: "from" | "to") {
  if (!value) {
    return null;
  }

  if (!Number.isFinite(Date.parse(value))) {
    throw new Error(`Invalid ${field} instant: ${value}`);
  }

  return new Date(value).toISOString();
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

const allCalls = analystCallsFixture.calls.map(callView);

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
    const assetId = normalized(query.assetId);
    const ticker = normalized(query.ticker);
    const institutionId = normalized(query.institutionId);
    const analystId = normalized(query.analystId);
    const from = instant(query.from, "from");
    const to = instant(query.to, "to");

    if (from && to && from >= to) {
      throw new Error("Invalid event-time range: to must be later than from.");
    }

    const filtered = allCalls.filter(({ call, institution, analyst, asset }) => {
      const assetMatches = !assetId || asset.assetId.toLocaleLowerCase("en-US") === assetId;
      const tickerMatches = !ticker || asset.ticker?.toLocaleLowerCase("en-US") === ticker;
      const institutionMatches =
        !institutionId || institution.institutionId.toLocaleLowerCase("en-US") === institutionId;
      const analystMatches =
        !analystId || analyst?.analystId.toLocaleLowerCase("en-US") === analystId;
      const directionMatches = !query.direction || call.direction === query.direction;
      const statusMatches = !query.status || call.status === query.status;
      const dataModeMatches = !query.dataMode || call.dataMode === query.dataMode;
      const fromMatches = !from || call.eventTime >= from;
      const toMatches = !to || call.eventTime < to;

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
    const sorted = [...filtered].sort((left, right) => {
      const primary = left.call[sort].localeCompare(right.call[sort]);

      if (primary !== 0) {
        return order === "asc" ? primary : -primary;
      }

      return left.call.callId.localeCompare(right.call.callId);
    });
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
}

export function callsProvider(): CallsProvider {
  const configuredProvider = process.env.ANALYST_PROVIDER?.toLocaleLowerCase("en-US") ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported analyst provider: ${configuredProvider}`);
  }

  return new FixtureCallsProvider();
}
