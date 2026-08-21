import analystCallRevisionsFixtureJson from "../../../../../fixtures/v1/analyst-call-revisions.json";
import analystCallsFixtureJson from "../../../../../fixtures/v1/analyst-calls.json";
import {
  adaptCallRevisionsResponse,
  callAuditInstant,
  compareCallAuditInstants,
  validateCallAuditSnapshot,
} from "./call-audit-adapter";
import type { CallAuditProvider, CallRevision } from "./call-audit-provider";
import type { CallsProvider } from "./calls-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";

type RevisionFixtureEnvelope = {
  schemaVersion: "1.0.0";
  fixtureVersion: string;
  dataMode: "DEMO";
  generatedAt: string;
  provenance: {
    id: string;
    sourceType: string;
    sourcePaths: string[];
    capturedAt: string;
    synthetic: true;
    licenseClass: string;
  };
  revisions: unknown[];
  disclaimer: string;
};

const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const REVISION_PROVENANCE_ID = "fixture-analyst-call-revisions-v1";
const REVISION_SOURCE_PATHS = ["docs/contracts/events.md", "docs/docs/DOMAIN_MODEL.md"] as const;
const REVISION_DISCLAIMER = "Synthetic DEMO revisions only; they do not represent an actual analyst statement, correction, or cancellation.";

type CallsFixtureIdentity = {
  schemaVersion: unknown;
  calls: Array<{ callId?: unknown }>;
};

function exactKeys(value: Record<string, unknown>, expected: readonly string[], owner: string) {
  const actual = Object.keys(value).sort();
  const sortedExpected = [...expected].sort();
  if (actual.length !== sortedExpected.length || actual.some((key, index) => key !== sortedExpected[index])) {
    throw new Error(`${owner} must preserve its exact closed fixture shape.`);
  }
}

function revisionFixtureEnvelope(value: unknown): RevisionFixtureEnvelope {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Call-revision fixture must be an object.");
  }
  const envelope = value as Record<string, unknown>;
  exactKeys(
    envelope,
    ["schemaVersion", "fixtureVersion", "dataMode", "generatedAt", "provenance", "revisions", "disclaimer"],
    "Call-revision fixture",
  );
  if (
    envelope.schemaVersion !== "1.0.0" ||
    envelope.fixtureVersion !== "v1" ||
    envelope.dataMode !== "DEMO" ||
    !Array.isArray(envelope.revisions) ||
    envelope.disclaimer !== REVISION_DISCLAIMER
  ) {
    throw new Error("Call-revision fixture has invalid envelope metadata.");
  }
  callAuditInstant(envelope.generatedAt, "Call-revision fixture generatedAt");
  if (envelope.provenance === null || typeof envelope.provenance !== "object" || Array.isArray(envelope.provenance)) {
    throw new Error("Call-revision fixture provenance must be an object.");
  }
  const provenance = envelope.provenance as Record<string, unknown>;
  exactKeys(
    provenance,
    ["id", "sourceType", "sourcePaths", "capturedAt", "synthetic", "licenseClass"],
    "Call-revision fixture provenance",
  );
  if (
    provenance.id !== REVISION_PROVENANCE_ID ||
    provenance.sourceType !== "LOCAL_SPECIFICATION" ||
    !Array.isArray(provenance.sourcePaths) ||
    provenance.sourcePaths.length !== REVISION_SOURCE_PATHS.length ||
    provenance.sourcePaths.some((path, index) => path !== REVISION_SOURCE_PATHS[index]) ||
    provenance.synthetic !== true ||
    provenance.licenseClass !== "INTERNAL_DEMO"
  ) {
    throw new Error("Call-revision fixture has invalid provenance metadata.");
  }
  callAuditInstant(provenance.capturedAt, "Call-revision fixture provenance capturedAt");
  if (compareCallAuditInstants(provenance.capturedAt as string, envelope.generatedAt as string) > 0) {
    throw new Error("Call-revision fixture provenance must not postdate generation.");
  }
  return value as RevisionFixtureEnvelope;
}

export class FixtureCallAuditProvider implements CallAuditProvider {
  constructor(
    private readonly calls: CallsProvider = new FixtureCallsProvider(),
    private readonly revisionFixture: unknown = analystCallRevisionsFixtureJson,
  ) {}

  async findById(callId: string) {
    const detail = await this.calls.findById(callId);
    if (!detail) return null;

    const context = await this.calls.findContextByCallId(callId);
    if (!context) {
      throw new Error(`Fixture call ${callId} has no explicit context response.`);
    }
    const envelope = revisionFixtureEnvelope(this.revisionFixture);
    const callsFixture = analystCallsFixtureJson as CallsFixtureIdentity;
    if (callsFixture.schemaVersion !== "1.0.0" || !Array.isArray(callsFixture.calls)) {
      throw new Error("Analyst-call fixture identity catalog is invalid.");
    }
    const knownCallIds = new Set<string>();
    for (const [index, fixtureCall] of callsFixture.calls.entries()) {
      if (typeof fixtureCall.callId !== "string" || !IDENTIFIER.test(fixtureCall.callId)) {
        throw new Error(`Analyst-call fixture calls[${index}] has an invalid call ID.`);
      }
      if (knownCallIds.has(fixtureCall.callId)) {
        throw new Error(`Analyst-call fixture duplicates call ${fixtureCall.callId}.`);
      }
      knownCallIds.add(fixtureCall.callId);
    }
    const grouped = new Map<string, unknown[]>();
    for (const [index, value] of envelope.revisions.entries()) {
      if (value === null || typeof value !== "object" || Array.isArray(value)) {
        throw new Error(`Call-revision fixture revisions[${index}] must be an object.`);
      }
      const candidateCallId = (value as Record<string, unknown>).callId;
      if (typeof candidateCallId !== "string" || !knownCallIds.has(candidateCallId)) {
        throw new Error(`Call-revision fixture revisions[${index}] references an unknown call.`);
      }
      grouped.set(candidateCallId, [...(grouped.get(candidateCallId) ?? []), value]);
    }

    const catalog = new Map<string, readonly CallRevision[]>();
    for (const knownCallId of knownCallIds) {
      const revisions = adaptCallRevisionsResponse(grouped.get(knownCallId) ?? [], knownCallId);
      for (const revision of revisions) {
        if (revision.dataMode !== envelope.dataMode || revision.provenanceId !== envelope.provenance.id) {
          throw new Error(`Fixture revision ${revision.revisionId} does not match its envelope evidence.`);
        }
        if (compareCallAuditInstants(revision.capturedAt, envelope.provenance.capturedAt) > 0) {
          throw new Error(`Fixture revision ${revision.revisionId} postdates fixture provenance capture.`);
        }
      }
      catalog.set(knownCallId, revisions);
    }

    return validateCallAuditSnapshot({
      detail,
      context,
      revisions: catalog.get(callId) ?? [],
    });
  }
}
