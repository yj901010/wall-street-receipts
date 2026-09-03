import analystCallRevisionsFixtureJson from "../../../../../fixtures/v1/analyst-call-revisions.json";
import analystCallsFixtureJson from "../../../../../fixtures/v1/analyst-calls.json";
import callOutcomesFixtureJson from "../../../../../fixtures/v1/call-outcomes.json";
import {
  adaptCallOutcomesResponse,
  adaptCallRevisionsResponse,
  callAuditInstant,
  compareCallAuditInstants,
  validateCallAuditSnapshot,
} from "./call-audit-adapter";
import {
  CALL_OUTCOME_HORIZONS,
  type CallAuditProvider,
  type CallOutcome,
  type CallRevision,
} from "./call-audit-provider";
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

type OutcomeFixtureEnvelope = {
  schemaVersion: "1.0.0";
  fixtureVersion: "v1";
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
  methodologies: unknown[];
  outcomes: unknown[];
  disclaimer: string;
};

type OutcomeMethodology = {
  methodologyId: string;
  methodologyVersion: string;
  schemaVersion: "1.0.0";
  definitionHash: string;
  status: "MODEL_ONLY";
  effectiveAt: string;
  dataMode: "DEMO";
  capturedAt: string;
  provenanceId: string;
};

const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const REVISION_PROVENANCE_ID = "fixture-analyst-call-revisions-v1";
const REVISION_SOURCE_PATHS = ["docs/contracts/events.md", "docs/docs/DOMAIN_MODEL.md"] as const;
const REVISION_DISCLAIMER = "Synthetic DEMO revisions only; they do not represent an actual analyst statement, correction, or cancellation.";
const OUTCOME_PROVENANCE_ID = "fixture-call-outcomes-v1";
const OUTCOME_SOURCE_PATHS = [
  "decisions/ADR-004-call-outcome-versioning.md",
  "docs/docs/DOMAIN_MODEL.md",
  "docs/docs/SCORING_AND_ANALYTICS.md",
  "schemas/scoring-methodology.schema.json",
  "schemas/call-outcome.schema.json",
] as const;
const OUTCOME_DISCLAIMER = "Synthetic DEMO model records only; no outcome metric has been calculated or invented.";
const METHODOLOGY_VERSION = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$/;
const LOWERCASE_SHA256 = /^[0-9a-f]{64}$/;

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

function fixtureIdentifier(value: unknown, owner: string) {
  if (typeof value !== "string" || !IDENTIFIER.test(value)) {
    throw new Error(`${owner} must be a canonical opaque identifier.`);
  }
  return value;
}

function outcomeFixtureEnvelope(value: unknown): OutcomeFixtureEnvelope {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Call-outcome fixture must be an object.");
  }
  const envelope = value as Record<string, unknown>;
  exactKeys(
    envelope,
    [
      "schemaVersion", "fixtureVersion", "dataMode", "generatedAt", "provenance", "methodologies",
      "outcomes", "disclaimer",
    ],
    "Call-outcome fixture",
  );
  if (
    envelope.schemaVersion !== "1.0.0" ||
    envelope.fixtureVersion !== "v1" ||
    envelope.dataMode !== "DEMO" ||
    !Array.isArray(envelope.methodologies) ||
    !Array.isArray(envelope.outcomes) ||
    envelope.disclaimer !== OUTCOME_DISCLAIMER
  ) {
    throw new Error("Call-outcome fixture has invalid envelope metadata.");
  }
  callAuditInstant(envelope.generatedAt, "Call-outcome fixture generatedAt");
  if (envelope.provenance === null || typeof envelope.provenance !== "object" || Array.isArray(envelope.provenance)) {
    throw new Error("Call-outcome fixture provenance must be an object.");
  }
  const provenance = envelope.provenance as Record<string, unknown>;
  exactKeys(
    provenance,
    ["id", "sourceType", "sourcePaths", "capturedAt", "synthetic", "licenseClass"],
    "Call-outcome fixture provenance",
  );
  if (
    provenance.id !== OUTCOME_PROVENANCE_ID ||
    provenance.sourceType !== "LOCAL_SPECIFICATION" ||
    !Array.isArray(provenance.sourcePaths) ||
    provenance.sourcePaths.length !== OUTCOME_SOURCE_PATHS.length ||
    provenance.sourcePaths.some((path, index) => path !== OUTCOME_SOURCE_PATHS[index]) ||
    provenance.synthetic !== true ||
    provenance.licenseClass !== "INTERNAL_DEMO"
  ) {
    throw new Error("Call-outcome fixture has invalid provenance metadata.");
  }
  callAuditInstant(provenance.capturedAt, "Call-outcome fixture provenance capturedAt");
  if (compareCallAuditInstants(provenance.capturedAt as string, envelope.generatedAt as string) > 0) {
    throw new Error("Call-outcome fixture provenance must not postdate generation.");
  }
  return value as OutcomeFixtureEnvelope;
}

function outcomeMethodologies(envelope: OutcomeFixtureEnvelope): Map<string, OutcomeMethodology> {
  const methodologies = new Map<string, OutcomeMethodology>();
  for (const [index, value] of envelope.methodologies.entries()) {
    if (value === null || typeof value !== "object" || Array.isArray(value)) {
      throw new Error(`Call-outcome fixture methodologies[${index}] must be an object.`);
    }
    const record = value as Record<string, unknown>;
    exactKeys(
      record,
      [
        "methodologyId", "methodologyVersion", "schemaVersion", "definitionHash", "status", "effectiveAt",
        "dataMode", "capturedAt", "provenanceId",
      ],
      `Call-outcome fixture methodologies[${index}]`,
    );
    const methodologyId = fixtureIdentifier(record.methodologyId, `Methodology ${index}.methodologyId`);
    if (typeof record.methodologyVersion !== "string" || !METHODOLOGY_VERSION.test(record.methodologyVersion)) {
      throw new Error(`Methodology ${index}.methodologyVersion must be canonical.`);
    }
    if (typeof record.definitionHash !== "string" || !LOWERCASE_SHA256.test(record.definitionHash)) {
      throw new Error(`Methodology ${index}.definitionHash must be a lowercase SHA-256 hash.`);
    }
    if (
      record.schemaVersion !== "1.0.0" ||
      record.status !== "MODEL_ONLY" ||
      record.dataMode !== envelope.dataMode ||
      record.provenanceId !== envelope.provenance.id
    ) {
      throw new Error(`Methodology ${methodologyId}@${record.methodologyVersion} has invalid fixture evidence.`);
    }
    const effectiveAt = callAuditInstant(record.effectiveAt, `Methodology ${methodologyId} effectiveAt`);
    const capturedAt = callAuditInstant(record.capturedAt, `Methodology ${methodologyId} capturedAt`);
    if (
      compareCallAuditInstants(effectiveAt, capturedAt) > 0 ||
      compareCallAuditInstants(capturedAt, envelope.generatedAt) > 0
    ) {
      throw new Error(`Methodology ${methodologyId}@${record.methodologyVersion} has invalid chronology.`);
    }
    const methodology: OutcomeMethodology = {
      methodologyId,
      methodologyVersion: record.methodologyVersion,
      schemaVersion: "1.0.0",
      definitionHash: record.definitionHash,
      status: "MODEL_ONLY",
      effectiveAt,
      dataMode: "DEMO",
      capturedAt,
      provenanceId: envelope.provenance.id,
    };
    const identity = JSON.stringify([methodology.methodologyId, methodology.methodologyVersion]);
    if (methodologies.has(identity)) {
      throw new Error(`Call-outcome fixture duplicates methodology ${methodologyId}@${methodology.methodologyVersion}.`);
    }
    methodologies.set(identity, methodology);
  }
  return methodologies;
}

function rawOutcomeOrder(left: unknown, right: unknown): number {
  const leftRecord = left as Record<string, unknown>;
  const rightRecord = right as Record<string, unknown>;
  const leftHorizon = CALL_OUTCOME_HORIZONS.indexOf(leftRecord.horizon as never);
  const rightHorizon = CALL_OUTCOME_HORIZONS.indexOf(rightRecord.horizon as never);
  if (leftHorizon !== rightHorizon) return leftHorizon - rightHorizon;
  for (const field of ["methodologyId", "methodologyVersion"] as const) {
    const leftText = typeof leftRecord[field] === "string" ? leftRecord[field] : "";
    const rightText = typeof rightRecord[field] === "string" ? rightRecord[field] : "";
    if (leftText !== rightText) return leftText < rightText ? -1 : 1;
  }
  const leftSequence = typeof leftRecord.sequenceNumber === "number" ? leftRecord.sequenceNumber : 0;
  const rightSequence = typeof rightRecord.sequenceNumber === "number" ? rightRecord.sequenceNumber : 0;
  if (leftSequence !== rightSequence) return leftSequence - rightSequence;
  const leftId = typeof leftRecord.outcomeId === "string" ? leftRecord.outcomeId : "";
  const rightId = typeof rightRecord.outcomeId === "string" ? rightRecord.outcomeId : "";
  return leftId < rightId ? -1 : leftId > rightId ? 1 : 0;
}

function normalizeFixtureOutcomeOrder(values: readonly unknown[]) {
  return [...values].sort(rawOutcomeOrder);
}

export class FixtureCallAuditProvider implements CallAuditProvider {
  constructor(
    private readonly calls: CallsProvider = new FixtureCallsProvider(),
    private readonly revisionFixture: unknown = analystCallRevisionsFixtureJson,
    private readonly outcomeFixture: unknown = callOutcomesFixtureJson,
  ) {}

  async findById(callId: string) {
    const detail = await this.calls.findById(callId);
    if (!detail) return null;

    const context = await this.calls.findContextByCallId(callId);
    if (!context) {
      throw new Error(`Fixture call ${callId} has no explicit context response.`);
    }
    const envelope = revisionFixtureEnvelope(this.revisionFixture);
    const outcomeEnvelope = outcomeFixtureEnvelope(this.outcomeFixture);
    const methodologies = outcomeMethodologies(outcomeEnvelope);
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
    const groupedOutcomes = new Map<string, unknown[]>();
    for (const [index, value] of outcomeEnvelope.outcomes.entries()) {
      if (value === null || typeof value !== "object" || Array.isArray(value)) {
        throw new Error(`Call-outcome fixture outcomes[${index}] must be an object.`);
      }
      const candidateCallId = (value as Record<string, unknown>).callId;
      if (typeof candidateCallId !== "string" || !knownCallIds.has(candidateCallId)) {
        throw new Error(`Call-outcome fixture outcomes[${index}] references an unknown call.`);
      }
      groupedOutcomes.set(candidateCallId, [...(groupedOutcomes.get(candidateCallId) ?? []), value]);
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

    const outcomeCatalog = new Map<string, readonly CallOutcome[]>();
    for (const knownCallId of knownCallIds) {
      const normalized = normalizeFixtureOutcomeOrder(groupedOutcomes.get(knownCallId) ?? []);
      const outcomes = adaptCallOutcomesResponse(normalized, knownCallId);
      for (const outcome of outcomes) {
        if (outcome.dataMode !== outcomeEnvelope.dataMode || outcome.provenanceId !== outcomeEnvelope.provenance.id) {
          throw new Error(`Fixture outcome ${outcome.outcomeId} does not match its envelope evidence.`);
        }
        if (compareCallAuditInstants(outcome.capturedAt, outcomeEnvelope.generatedAt) > 0) {
          throw new Error(`Fixture outcome ${outcome.outcomeId} postdates fixture generation.`);
        }
        const methodology = methodologies.get(JSON.stringify([outcome.methodologyId, outcome.methodologyVersion]));
        if (!methodology || methodology.definitionHash !== outcome.methodologyDefinitionHash) {
          throw new Error(`Fixture outcome ${outcome.outcomeId} has no matching methodology definition.`);
        }
        if (
          compareCallAuditInstants(methodology.effectiveAt, outcome.processingTime) > 0 ||
          compareCallAuditInstants(methodology.capturedAt, outcome.processingTime) > 0
        ) {
          throw new Error(`Fixture outcome ${outcome.outcomeId} predates its methodology evidence.`);
        }
      }
      outcomeCatalog.set(knownCallId, outcomes);
    }

    return validateCallAuditSnapshot({
      detail,
      context,
      revisions: catalog.get(callId) ?? [],
      outcomes: outcomeCatalog.get(callId) ?? [],
    });
  }
}
