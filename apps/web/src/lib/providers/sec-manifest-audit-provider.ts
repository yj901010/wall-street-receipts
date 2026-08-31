export const SEC_MANIFEST_AUDIT_SCHEMA_VERSION = "1.0.0" as const;
export const SEC_MANIFEST_AUDIT_POLICY_VERSION =
  "SEC_EXACT_MANIFEST_AUDIT_V1" as const;

export const SEC_MANIFEST_AUDIT_VIEWS = [
  "summary",
  "descriptors",
  "accessions",
  "occurrences",
] as const;

export type SecManifestAuditView = (typeof SEC_MANIFEST_AUDIT_VIEWS)[number];

export type SecManifestAuditQuery = {
  manifestId: string;
  evaluationAsOf: string;
  view: SecManifestAuditView;
  page: number;
  size: number;
};

export type SecManifestAuditDisclosure = {
  coverageScope: "ROOT_RELATIVE_SELECTED_REFERENCES_ONLY";
  atomicSecSnapshotClaim: "NOT_MADE";
  currentHistoryStatus: "NOT_RESOLVED";
  correctionRemovalStatus: "NOT_RESOLVED";
  amendmentLinkageStatus: "NOT_RESOLVED";
  legalAuthorityStatus: "NOT_CLAIMED";
};

export type SecManifestAuditSummary = {
  auditSchemaVersion: "1.0.0";
  auditPolicyVersion: "SEC_EXACT_MANIFEST_AUDIT_V1";
  evaluationAsOf: string;
  manifestId: string;
  manifestSchemaVersion: string;
  provider: string;
  product: string;
  policyVersion: string;
  selectionSha256: string;
  rootCaptureId: string;
  rootCapturedAt: string;
  cik: string;
  evidenceAvailableAt: string;
  assembledAt: string;
  selectionCoverage:
    | "NO_ADVERTISED_DESCRIPTORS"
    | "ALL_ADVERTISED_DESCRIPTORS_SELECTED"
    | "PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED";
  advertisedDescriptorCount: number;
  selectedDescriptorCount: number;
  omittedDescriptorCount: number;
  sourceOccurrenceCount: number;
  distinctAccessionCount: number;
  singleSourceAccessionCount: number;
  exactAgreementAccessionCount: number;
  canonicalConflictAccessionCount: number;
  immutable: true;
  disclosure: SecManifestAuditDisclosure;
};

export type SecManifestAuditDescriptor = {
  descriptorOrdinal: number;
  fileName: string;
  advertisedFilingCount: number;
  advertisedFilingFrom: string;
  advertisedFilingTo: string;
  selectionState: "SELECTED_EXACT_CAPTURE" | "NOT_SELECTED";
  selectedSegmentCaptureId: string | null;
};

export type SecManifestAuditAccession = {
  groupOrdinal: number;
  accessionNumber: string;
  occurrenceCount: number;
  distinctProjectionCount: number;
  comparison:
    | "SINGLE_SOURCE_OCCURRENCE"
    | "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT"
    | "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT";
};

export type SecManifestAuditOccurrence = {
  occurrenceOrdinal: number;
  groupOrdinal: number;
  sourceKind: "ROOT_RECENT" | "HISTORICAL_SEGMENT";
  sourceCaptureId: string;
  descriptorOrdinal: number | null;
  sourceRowOrdinal: number;
  projectionSha256: string;
  providerEventId: string;
  accessionNumber: string;
  form: string;
  filingDate: string;
  reportDate: string | null;
  acceptedAt: string;
  primaryDocumentUri: string | null;
};

export type SecManifestAuditPageMetadata = {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  order: {
    field: "descriptorOrdinal" | "groupOrdinal" | "occurrenceOrdinal";
    direction: "ASC";
  };
};

export type SecManifestAuditPage<T> = {
  auditSchemaVersion: "1.0.0";
  auditPolicyVersion: "SEC_EXACT_MANIFEST_AUDIT_V1";
  manifestId: string;
  evaluationAsOf: string;
  items: readonly T[];
  page: SecManifestAuditPageMetadata;
};

export type SecManifestAuditResource =
  | { view: "summary"; data: SecManifestAuditSummary }
  | { view: "descriptors"; data: SecManifestAuditPage<SecManifestAuditDescriptor> }
  | { view: "accessions"; data: SecManifestAuditPage<SecManifestAuditAccession> }
  | { view: "occurrences"; data: SecManifestAuditPage<SecManifestAuditOccurrence> };

export type SecManifestAuditDemoQuery = Pick<
  SecManifestAuditQuery,
  "manifestId" | "evaluationAsOf"
>;

export interface SecManifestAuditProvider {
  readonly mode: "fixture" | "api";
  readonly demoQuery: SecManifestAuditDemoQuery | null;
  findExact(query: SecManifestAuditQuery): Promise<SecManifestAuditResource | null>;
}
