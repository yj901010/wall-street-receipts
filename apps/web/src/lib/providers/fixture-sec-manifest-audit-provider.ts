import demoFixtureJson from "./fixtures/sec-manifest-audit-demo.json";
import { adaptSecManifestAuditResource } from "./sec-manifest-audit-adapter";
import type {
  SecManifestAuditAccession,
  SecManifestAuditDemoQuery,
  SecManifestAuditDescriptor,
  SecManifestAuditOccurrence,
  SecManifestAuditPage,
  SecManifestAuditPageMetadata,
  SecManifestAuditProvider,
  SecManifestAuditQuery,
  SecManifestAuditResource,
  SecManifestAuditSummary,
} from "./sec-manifest-audit-provider";
import {
  compareSecManifestAuditInstants,
  isSecManifestAuditInstant,
  isSecManifestAuditManifestId,
} from "./sec-manifest-audit-query";

type FixtureEnvelope = {
  fixtureSchemaVersion: unknown;
  generatedBy: unknown;
  manifestId: unknown;
  evaluationAsOf: unknown;
  summary: unknown;
  descriptors: unknown;
  accessions: unknown;
  occurrences: unknown;
};

function fixtureEnvelope(value: unknown): FixtureEnvelope {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error("SEC manifest audit DEMO fixture must be an object.");
  }
  const result = value as Record<string, unknown>;
  const expected = [
    "fixtureSchemaVersion",
    "generatedBy",
    "manifestId",
    "evaluationAsOf",
    "summary",
    "descriptors",
    "accessions",
    "occurrences",
  ].sort();
  const actual = Object.keys(result).sort();
  if (
    actual.length !== expected.length ||
    actual.some((key, index) => key !== expected[index]) ||
    result.fixtureSchemaVersion !== "1.0.0" ||
    result.generatedBy !== "java-domain-and-adr-052-response-mapper" ||
    typeof result.manifestId !== "string" ||
    !isSecManifestAuditManifestId(result.manifestId) ||
    typeof result.evaluationAsOf !== "string" ||
    !isSecManifestAuditInstant(result.evaluationAsOf)
  ) {
    throw new Error("SEC manifest audit DEMO fixture envelope is invalid.");
  }
  return result as FixtureEnvelope;
}

const FIXTURE = fixtureEnvelope(demoFixtureJson);

export const SEC_MANIFEST_AUDIT_DEMO_QUERY = {
  manifestId: FIXTURE.manifestId as string,
  evaluationAsOf: FIXTURE.evaluationAsOf as string,
} as const satisfies SecManifestAuditDemoQuery;

const BASE_QUERY = {
  ...SEC_MANIFEST_AUDIT_DEMO_QUERY,
  view: "summary",
  page: 0,
  size: 25,
} as const satisfies SecManifestAuditQuery;

function generatedSummary(): SecManifestAuditSummary {
  const resource = adaptSecManifestAuditResource("summary", FIXTURE.summary, BASE_QUERY);
  if (resource.view !== "summary") {
    throw new Error("SEC manifest audit DEMO summary identity changed.");
  }
  return resource.data;
}

function generatedDescriptors(): SecManifestAuditPage<SecManifestAuditDescriptor> {
  const resource = adaptSecManifestAuditResource(
    "descriptors",
    FIXTURE.descriptors,
    { ...BASE_QUERY, view: "descriptors" },
  );
  if (resource.view !== "descriptors") {
    throw new Error("SEC manifest audit DEMO descriptor identity changed.");
  }
  return resource.data;
}

function generatedAccessions(): SecManifestAuditPage<SecManifestAuditAccession> {
  const resource = adaptSecManifestAuditResource(
    "accessions",
    FIXTURE.accessions,
    { ...BASE_QUERY, view: "accessions" },
  );
  if (resource.view !== "accessions") {
    throw new Error("SEC manifest audit DEMO accession identity changed.");
  }
  return resource.data;
}

function generatedOccurrences(): SecManifestAuditPage<SecManifestAuditOccurrence> {
  const resource = adaptSecManifestAuditResource(
    "occurrences",
    FIXTURE.occurrences,
    { ...BASE_QUERY, view: "occurrences" },
  );
  if (resource.view !== "occurrences") {
    throw new Error("SEC manifest audit DEMO occurrence identity changed.");
  }
  return resource.data;
}

const GENERATED_SUMMARY = generatedSummary();
const GENERATED_DESCRIPTORS = generatedDescriptors();
const GENERATED_ACCESSIONS = generatedAccessions();
const GENERATED_OCCURRENCES = generatedOccurrences();

function requireInternalQuery(query: SecManifestAuditQuery) {
  if (
    !isSecManifestAuditManifestId(query.manifestId) ||
    !isSecManifestAuditInstant(query.evaluationAsOf) ||
    !Number.isSafeInteger(query.page) ||
    query.page < 0 ||
    query.page > 2_147_483_647 ||
    !Number.isSafeInteger(query.size) ||
    query.size < 1 ||
    query.size > 100
  ) {
    throw new Error("SEC manifest audit provider received an invalid internal query.");
  }
}

function page<T>(
  source: SecManifestAuditPage<T>,
  query: SecManifestAuditQuery,
  field: SecManifestAuditPageMetadata["order"]["field"],
): SecManifestAuditPage<T> {
  const totalElements = source.page.totalElements;
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / query.size);
  const start = query.page * query.size;
  const items = start >= totalElements
    ? []
    : source.items.slice(start, Math.min(start + query.size, totalElements));
  return {
    ...source,
    evaluationAsOf: query.evaluationAsOf,
    items,
    page: {
      number: query.page,
      size: query.size,
      totalElements,
      totalPages,
      first: query.page === 0,
      last: totalPages === 0 || query.page >= totalPages - 1,
      order: { field, direction: "ASC" },
    },
  };
}

export class FixtureSecManifestAuditProvider implements SecManifestAuditProvider {
  readonly mode = "fixture" as const;
  readonly demoQuery = SEC_MANIFEST_AUDIT_DEMO_QUERY;

  async findExact(query: SecManifestAuditQuery): Promise<SecManifestAuditResource | null> {
    requireInternalQuery(query);
    if (
      query.manifestId !== SEC_MANIFEST_AUDIT_DEMO_QUERY.manifestId ||
      compareSecManifestAuditInstants(
        query.evaluationAsOf,
        SEC_MANIFEST_AUDIT_DEMO_QUERY.evaluationAsOf,
      ) < 0
    ) {
      return null;
    }
    switch (query.view) {
      case "summary":
        return {
          view: "summary",
          data: { ...GENERATED_SUMMARY, evaluationAsOf: query.evaluationAsOf },
        };
      case "descriptors":
        return {
          view: "descriptors",
          data: page<SecManifestAuditDescriptor>(
            GENERATED_DESCRIPTORS,
            query,
            "descriptorOrdinal",
          ),
        };
      case "accessions":
        return {
          view: "accessions",
          data: page<SecManifestAuditAccession>(
            GENERATED_ACCESSIONS,
            query,
            "groupOrdinal",
          ),
        };
      case "occurrences":
        return {
          view: "occurrences",
          data: page<SecManifestAuditOccurrence>(
            GENERATED_OCCURRENCES,
            query,
            "occurrenceOrdinal",
          ),
        };
    }
  }
}
