import {
  SEC_MANIFEST_AUDIT_POLICY_VERSION,
  SEC_MANIFEST_AUDIT_SCHEMA_VERSION,
  type SecManifestAuditAccession,
  type SecManifestAuditDescriptor,
  type SecManifestAuditDisclosure,
  type SecManifestAuditOccurrence,
  type SecManifestAuditPage,
  type SecManifestAuditPageMetadata,
  type SecManifestAuditQuery,
  type SecManifestAuditResource,
  type SecManifestAuditSummary,
  type SecManifestAuditView,
} from "./sec-manifest-audit-provider";
import { compareSecManifestAuditInstants } from "./sec-manifest-audit-query";

const LOWERCASE_SHA_256 = /^[0-9a-f]{64}$/;
const CIK = /^[0-9]{10}$/;
const ACCESSION = /^[0-9]{10}-[0-9]{2}-[0-9]{6}$/;
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const SAFE_DESCRIPTOR_FILE_NAME = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;
const SAFE_SEC_DOCUMENT_SEGMENT = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;
const UTC_INSTANT =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?Z$/;

const SUMMARY_KEYS = [
  "auditSchemaVersion",
  "auditPolicyVersion",
  "evaluationAsOf",
  "manifestId",
  "manifestSchemaVersion",
  "provider",
  "product",
  "policyVersion",
  "selectionSha256",
  "rootCaptureId",
  "rootCapturedAt",
  "cik",
  "evidenceAvailableAt",
  "assembledAt",
  "selectionCoverage",
  "advertisedDescriptorCount",
  "selectedDescriptorCount",
  "omittedDescriptorCount",
  "sourceOccurrenceCount",
  "distinctAccessionCount",
  "singleSourceAccessionCount",
  "exactAgreementAccessionCount",
  "canonicalConflictAccessionCount",
  "immutable",
  "disclosure",
] as const;

const DISCLOSURE_KEYS = [
  "coverageScope",
  "atomicSecSnapshotClaim",
  "currentHistoryStatus",
  "correctionRemovalStatus",
  "amendmentLinkageStatus",
  "legalAuthorityStatus",
] as const;

const PAGE_KEYS = [
  "auditSchemaVersion",
  "auditPolicyVersion",
  "manifestId",
  "evaluationAsOf",
  "items",
  "page",
] as const;

const PAGE_METADATA_KEYS = [
  "number",
  "size",
  "totalElements",
  "totalPages",
  "first",
  "last",
  "order",
] as const;

function fail(context: string): never {
  throw new Error(`SEC manifest audit response violated ${context}.`);
}

function object(value: unknown, context: string): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    fail(context);
  }
  return value as Record<string, unknown>;
}

function exactObject<const Keys extends readonly string[]>(
  value: unknown,
  keys: Keys,
  context: string,
): Record<Keys[number], unknown> {
  const result = object(value, context);
  const actual = Object.keys(result).sort();
  const expected = [...keys].sort();
  if (
    actual.length !== expected.length ||
    actual.some((key, index) => key !== expected[index])
  ) {
    fail(`${context} field set`);
  }
  return result as Record<Keys[number], unknown>;
}

function string(value: unknown, context: string): string {
  if (typeof value !== "string" || value === "" || value.trim() !== value) {
    fail(context);
  }
  return value;
}

function literal<const Value extends string | boolean>(
  value: unknown,
  expected: Value,
  context: string,
): Value {
  if (value !== expected) fail(context);
  return expected;
}

function oneOf<const Values extends readonly string[]>(
  value: unknown,
  expected: Values,
  context: string,
): Values[number] {
  if (typeof value !== "string" || !expected.includes(value)) fail(context);
  return value as Values[number];
}

function safeInteger(value: unknown, context: string, minimum = 0): number {
  if (!Number.isSafeInteger(value) || (value as number) < minimum) fail(context);
  return value as number;
}

function nullable<T>(
  value: unknown,
  adapter: (candidate: unknown) => T,
): T | null {
  return value === null ? null : adapter(value);
}

function sha256(value: unknown, context: string): string {
  const result = string(value, context);
  if (!LOWERCASE_SHA_256.test(result)) fail(context);
  return result;
}

function daysInMonth(year: number, month: number): number {
  if (month === 2) {
    const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    return leap ? 29 : 28;
  }
  return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

function date(value: unknown, context: string): string {
  const result = string(value, context);
  const match = DATE.exec(result);
  if (!match) fail(context);
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)) {
    fail(context);
  }
  return result;
}

function instant(value: unknown, context: string): string {
  const result = string(value, context);
  const match = UTC_INSTANT.exec(result);
  if (!match) fail(context);
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = Number(match[6]);
  if (
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > daysInMonth(year, month) ||
    hour > 23 ||
    minute > 59 ||
    second > 59
  ) {
    fail(context);
  }
  return result;
}

function canonicalText(value: unknown, context: string): string {
  const result = string(value, context);
  if (/\s{2}|[\u0000-\u001f\u007f]/.test(result)) fail(context);
  return result;
}

function disclosure(value: unknown): SecManifestAuditDisclosure {
  const result = exactObject(value, DISCLOSURE_KEYS, "disclosure");
  return {
    coverageScope: literal(
      result.coverageScope,
      "ROOT_RELATIVE_SELECTED_REFERENCES_ONLY",
      "disclosure coverage scope",
    ),
    atomicSecSnapshotClaim: literal(
      result.atomicSecSnapshotClaim,
      "NOT_MADE",
      "atomic SEC snapshot disclosure",
    ),
    currentHistoryStatus: literal(
      result.currentHistoryStatus,
      "NOT_RESOLVED",
      "current-history disclosure",
    ),
    correctionRemovalStatus: literal(
      result.correctionRemovalStatus,
      "NOT_RESOLVED",
      "correction/removal disclosure",
    ),
    amendmentLinkageStatus: literal(
      result.amendmentLinkageStatus,
      "NOT_RESOLVED",
      "amendment-linkage disclosure",
    ),
    legalAuthorityStatus: literal(
      result.legalAuthorityStatus,
      "NOT_CLAIMED",
      "legal-authority disclosure",
    ),
  };
}

function identity(
  value: Record<string, unknown>,
  query: SecManifestAuditQuery,
): {
  auditSchemaVersion: "1.0.0";
  auditPolicyVersion: "SEC_EXACT_MANIFEST_AUDIT_V1";
  manifestId: string;
  evaluationAsOf: string;
} {
  const manifestId = sha256(value.manifestId, "manifest ID");
  const evaluationAsOf = instant(value.evaluationAsOf, "evaluation cutoff");
  if (
    manifestId !== query.manifestId ||
    compareSecManifestAuditInstants(evaluationAsOf, query.evaluationAsOf) !== 0
  ) {
    fail("exact request identity");
  }
  return {
    auditSchemaVersion: literal(
      value.auditSchemaVersion,
      SEC_MANIFEST_AUDIT_SCHEMA_VERSION,
      "audit schema version",
    ),
    auditPolicyVersion: literal(
      value.auditPolicyVersion,
      SEC_MANIFEST_AUDIT_POLICY_VERSION,
      "audit policy version",
    ),
    manifestId,
    evaluationAsOf,
  };
}

function summary(value: unknown, query: SecManifestAuditQuery): SecManifestAuditSummary {
  const result = exactObject(value, SUMMARY_KEYS, "summary");
  const exactIdentity = identity(result, query);
  const selectionCoverage = oneOf(
    result.selectionCoverage,
    [
      "NO_ADVERTISED_DESCRIPTORS",
      "ALL_ADVERTISED_DESCRIPTORS_SELECTED",
      "PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED",
    ] as const,
    "selection coverage",
  );
  const advertisedDescriptorCount = safeInteger(
    result.advertisedDescriptorCount,
    "advertised descriptor count",
  );
  const selectedDescriptorCount = safeInteger(
    result.selectedDescriptorCount,
    "selected descriptor count",
  );
  const omittedDescriptorCount = safeInteger(
    result.omittedDescriptorCount,
    "omitted descriptor count",
  );
  const sourceOccurrenceCount = safeInteger(
    result.sourceOccurrenceCount,
    "source occurrence count",
  );
  const distinctAccessionCount = safeInteger(
    result.distinctAccessionCount,
    "distinct accession count",
  );
  const singleSourceAccessionCount = safeInteger(
    result.singleSourceAccessionCount,
    "single-source accession count",
  );
  const exactAgreementAccessionCount = safeInteger(
    result.exactAgreementAccessionCount,
    "exact-agreement accession count",
  );
  const canonicalConflictAccessionCount = safeInteger(
    result.canonicalConflictAccessionCount,
    "canonical-conflict accession count",
  );
  if (
    advertisedDescriptorCount !== selectedDescriptorCount + omittedDescriptorCount ||
    distinctAccessionCount !==
      singleSourceAccessionCount +
        exactAgreementAccessionCount +
        canonicalConflictAccessionCount ||
    sourceOccurrenceCount <
      singleSourceAccessionCount +
        2 * (exactAgreementAccessionCount + canonicalConflictAccessionCount) ||
    (sourceOccurrenceCount === 0) !== (distinctAccessionCount === 0) ||
    (selectionCoverage === "NO_ADVERTISED_DESCRIPTORS" &&
      (advertisedDescriptorCount !== 0 ||
        selectedDescriptorCount !== 0 ||
        omittedDescriptorCount !== 0)) ||
    (selectionCoverage === "ALL_ADVERTISED_DESCRIPTORS_SELECTED" &&
      (advertisedDescriptorCount === 0 ||
        selectedDescriptorCount !== advertisedDescriptorCount ||
        omittedDescriptorCount !== 0)) ||
    (selectionCoverage === "PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED" &&
      (advertisedDescriptorCount === 0 ||
        omittedDescriptorCount === 0 ||
        selectedDescriptorCount >= advertisedDescriptorCount))
  ) {
    fail("summary count invariants");
  }
  const rootCapturedAt = instant(result.rootCapturedAt, "root captured time");
  const evidenceAvailableAt = instant(
    result.evidenceAvailableAt,
    "evidence available time",
  );
  const assembledAt = instant(result.assembledAt, "assembled time");
  if (
    compareSecManifestAuditInstants(rootCapturedAt, evidenceAvailableAt) > 0 ||
    compareSecManifestAuditInstants(evidenceAvailableAt, assembledAt) > 0 ||
    compareSecManifestAuditInstants(assembledAt, exactIdentity.evaluationAsOf) > 0
  ) {
    fail("point-in-time ordering");
  }
  const cik = string(result.cik, "CIK");
  if (!CIK.test(cik) || cik === "0000000000") fail("CIK");

  return {
    ...exactIdentity,
    manifestSchemaVersion: literal(
      result.manifestSchemaVersion,
      "1.0.0",
      "manifest schema version",
    ),
    provider: literal(result.provider, "sec-edgar", "provider"),
    product: literal(
      result.product,
      "edgar-submissions-root-relative-collection-manifest",
      "product",
    ),
    policyVersion: literal(
      result.policyVersion,
      "SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1",
      "selection policy version",
    ),
    selectionSha256: sha256(result.selectionSha256, "selection SHA-256"),
    rootCaptureId: sha256(result.rootCaptureId, "root capture ID"),
    rootCapturedAt,
    cik,
    evidenceAvailableAt,
    assembledAt,
    selectionCoverage,
    advertisedDescriptorCount,
    selectedDescriptorCount,
    omittedDescriptorCount,
    sourceOccurrenceCount,
    distinctAccessionCount,
    singleSourceAccessionCount,
    exactAgreementAccessionCount,
    canonicalConflictAccessionCount,
    immutable: literal(result.immutable, true, "immutable marker"),
    disclosure: disclosure(result.disclosure),
  };
}

function descriptor(value: unknown): SecManifestAuditDescriptor {
  const result = exactObject(
    value,
    [
      "descriptorOrdinal",
      "fileName",
      "advertisedFilingCount",
      "advertisedFilingFrom",
      "advertisedFilingTo",
      "selectionState",
      "selectedSegmentCaptureId",
    ] as const,
    "descriptor",
  );
  const selectionState = oneOf(
    result.selectionState,
    ["SELECTED_EXACT_CAPTURE", "NOT_SELECTED"] as const,
    "descriptor selection state",
  );
  const selectedSegmentCaptureId = nullable(result.selectedSegmentCaptureId, (candidate) =>
    sha256(candidate, "selected segment capture ID"),
  );
  if (
    (selectionState === "SELECTED_EXACT_CAPTURE") !==
    (selectedSegmentCaptureId !== null)
  ) {
    fail("descriptor selection/capture coupling");
  }
  const fileName = canonicalText(result.fileName, "descriptor file name");
  if (fileName.length > 128 || !SAFE_DESCRIPTOR_FILE_NAME.test(fileName)) {
    fail("descriptor file name");
  }
  const advertisedFilingFrom = date(
    result.advertisedFilingFrom,
    "advertised filing-from date",
  );
  const advertisedFilingTo = date(
    result.advertisedFilingTo,
    "advertised filing-to date",
  );
  if (advertisedFilingFrom > advertisedFilingTo) {
    fail("advertised filing date range");
  }
  return {
    descriptorOrdinal: safeInteger(result.descriptorOrdinal, "descriptor ordinal"),
    fileName,
    advertisedFilingCount: safeInteger(
      result.advertisedFilingCount,
      "advertised filing count",
      1,
    ),
    advertisedFilingFrom,
    advertisedFilingTo,
    selectionState,
    selectedSegmentCaptureId,
  };
}

function accession(value: unknown): SecManifestAuditAccession {
  const result = exactObject(
    value,
    [
      "groupOrdinal",
      "accessionNumber",
      "occurrenceCount",
      "distinctProjectionCount",
      "comparison",
    ] as const,
    "accession group",
  );
  const accessionNumber = string(result.accessionNumber, "accession number");
  if (!ACCESSION.test(accessionNumber)) fail("accession number");
  const occurrenceCount = safeInteger(result.occurrenceCount, "occurrence count", 1);
  const distinctProjectionCount = safeInteger(
    result.distinctProjectionCount,
    "distinct projection count",
    1,
  );
  const comparison = oneOf(
    result.comparison,
    [
      "SINGLE_SOURCE_OCCURRENCE",
      "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT",
      "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT",
    ] as const,
    "accession comparison",
  );
  if (
    distinctProjectionCount > occurrenceCount ||
    (comparison === "SINGLE_SOURCE_OCCURRENCE" &&
      (occurrenceCount !== 1 || distinctProjectionCount !== 1)) ||
    (comparison === "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT" &&
      (occurrenceCount < 2 || distinctProjectionCount !== 1)) ||
    (comparison === "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT" &&
      (occurrenceCount < 2 || distinctProjectionCount < 2))
  ) {
    fail("accession comparison invariants");
  }
  return {
    groupOrdinal: safeInteger(result.groupOrdinal, "group ordinal"),
    accessionNumber,
    occurrenceCount,
    distinctProjectionCount,
    comparison,
  };
}

function canonicalSecDocument(value: unknown, accessionNumber: string): string {
  const result = string(value, "primary document URI");
  let parsed: URL;
  try {
    parsed = new URL(result);
  } catch {
    fail("primary document URI");
  }
  const accessionPath = accessionNumber.replaceAll("-", "");
  const archivesPrefix = "/Archives/edgar/data/";
  const rawPath = parsed.pathname;
  const pathSegments = rawPath.startsWith(archivesPrefix)
    ? rawPath.slice(archivesPrefix.length).split("/")
    : [];
  const archiveCik = pathSegments[0] ?? "";
  const documentSegments = pathSegments.slice(2);
  if (
    result !== parsed.href ||
    parsed.protocol !== "https:" ||
    parsed.hostname !== "www.sec.gov" ||
    parsed.port !== "" ||
    parsed.username !== "" ||
    parsed.password !== "" ||
    parsed.search !== "" ||
    parsed.hash !== "" ||
    !/^[1-9][0-9]*$/.test(archiveCik) ||
    pathSegments[1] !== accessionPath ||
    documentSegments.length === 0 ||
    documentSegments.some((segment) => !SAFE_SEC_DOCUMENT_SEGMENT.test(segment)) ||
    rawPath.includes("%") ||
    rawPath.includes("\\")
  ) {
    fail("canonical SEC document URI");
  }
  return result;
}

function occurrence(value: unknown, evaluationAsOf: string): SecManifestAuditOccurrence {
  const result = exactObject(
    value,
    [
      "occurrenceOrdinal",
      "groupOrdinal",
      "sourceKind",
      "sourceCaptureId",
      "descriptorOrdinal",
      "sourceRowOrdinal",
      "projectionSha256",
      "providerEventId",
      "accessionNumber",
      "form",
      "filingDate",
      "reportDate",
      "acceptedAt",
      "primaryDocumentUri",
    ] as const,
    "filing occurrence",
  );
  const sourceKind = oneOf(
    result.sourceKind,
    ["ROOT_RECENT", "HISTORICAL_SEGMENT"] as const,
    "occurrence source kind",
  );
  const descriptorOrdinal = nullable(result.descriptorOrdinal, (candidate) =>
    safeInteger(candidate, "descriptor ordinal"),
  );
  if ((sourceKind === "ROOT_RECENT") !== (descriptorOrdinal === null)) {
    fail("occurrence source/descriptor coupling");
  }
  const accessionNumber = string(result.accessionNumber, "accession number");
  const providerEventId = string(result.providerEventId, "provider event ID");
  if (!ACCESSION.test(accessionNumber) || providerEventId !== accessionNumber) {
    fail("occurrence accession identity");
  }
  const acceptedAt = instant(result.acceptedAt, "acceptance time");
  if (compareSecManifestAuditInstants(acceptedAt, evaluationAsOf) > 0) {
    fail("occurrence point-in-time ordering");
  }
  return {
    occurrenceOrdinal: safeInteger(result.occurrenceOrdinal, "occurrence ordinal"),
    groupOrdinal: safeInteger(result.groupOrdinal, "group ordinal"),
    sourceKind,
    sourceCaptureId: sha256(result.sourceCaptureId, "source capture ID"),
    descriptorOrdinal,
    sourceRowOrdinal: safeInteger(result.sourceRowOrdinal, "source row ordinal"),
    projectionSha256: sha256(result.projectionSha256, "projection SHA-256"),
    providerEventId,
    accessionNumber,
    form: canonicalText(result.form, "filing form"),
    filingDate: date(result.filingDate, "filing date"),
    reportDate: nullable(result.reportDate, (candidate) => date(candidate, "report date")),
    acceptedAt,
    primaryDocumentUri: nullable(result.primaryDocumentUri, (candidate) =>
      canonicalSecDocument(candidate, accessionNumber),
    ),
  };
}

function pageMetadata(
  value: unknown,
  query: SecManifestAuditQuery,
  orderField: SecManifestAuditPageMetadata["order"]["field"],
  itemCount: number,
): SecManifestAuditPageMetadata {
  const result = exactObject(value, PAGE_METADATA_KEYS, "page metadata");
  const number = safeInteger(result.number, "page number");
  const size = safeInteger(result.size, "page size", 1);
  const totalElements = safeInteger(result.totalElements, "total elements");
  const totalPages = safeInteger(result.totalPages, "total pages");
  const order = exactObject(result.order, ["field", "direction"] as const, "page order");
  const expectedTotalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size);
  const expectedItems = Math.min(size, Math.max(totalElements - number * size, 0));
  const first = number === 0;
  const last = totalPages === 0 || number >= totalPages - 1;
  if (
    number !== query.page ||
    size !== query.size ||
    totalPages !== expectedTotalPages ||
    itemCount !== expectedItems ||
    result.first !== first ||
    result.last !== last ||
    order.field !== orderField ||
    order.direction !== "ASC"
  ) {
    fail("page identity and bounds");
  }
  return {
    number,
    size,
    totalElements,
    totalPages,
    first,
    last,
    order: { field: orderField, direction: "ASC" },
  };
}

function page<T>(
  value: unknown,
  query: SecManifestAuditQuery,
  itemAdapter: (candidate: unknown) => T,
  orderField: SecManifestAuditPageMetadata["order"]["field"],
  ordinal: (item: T) => number,
): SecManifestAuditPage<T> {
  const result = exactObject(value, PAGE_KEYS, "page");
  const exactIdentity = identity(result, query);
  if (!Array.isArray(result.items)) fail("page items");
  const items = result.items.map(itemAdapter);
  const metadata = pageMetadata(result.page, query, orderField, items.length);
  const firstOrdinal = metadata.number * metadata.size;
  if (items.some((item, index) => ordinal(item) !== firstOrdinal + index)) {
    fail("fixed page ordinal ordering");
  }
  return { ...exactIdentity, items, page: metadata };
}

export function adaptSecManifestAuditResource(
  view: SecManifestAuditView,
  payload: unknown,
  query: SecManifestAuditQuery,
): SecManifestAuditResource {
  if (view !== query.view) fail("requested view identity");
  switch (view) {
    case "summary":
      return { view, data: summary(payload, query) };
    case "descriptors":
      return {
        view,
        data: page(payload, query, descriptor, "descriptorOrdinal", (item) =>
          item.descriptorOrdinal),
      };
    case "accessions":
      return {
        view,
        data: page(payload, query, accession, "groupOrdinal", (item) => item.groupOrdinal),
      };
    case "occurrences":
      return {
        view,
        data: page(
          payload,
          query,
          (candidate) => occurrence(candidate, query.evaluationAsOf),
          "occurrenceOrdinal",
          (item) => item.occurrenceOrdinal,
        ),
      };
  }
}
