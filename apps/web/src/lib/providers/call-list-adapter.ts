import {
  adaptCallViewResponse,
  callAuditInstant,
  compareCallAuditInstants,
} from "./call-audit-adapter";
import {
  CALL_LIST_METADATA_NOT_EXPOSED_REASON,
  type AvailableCallListDatasetEvidence,
  type CallListDatasetEvidence,
  type CallListSnapshot,
} from "./call-list-provider";
import {
  CALL_DIRECTIONS,
  CALL_SORT_FIELDS,
  CALL_STATUSES,
  type AnalystCallPage,
  type AnalystCallView,
  type CallsQuery,
} from "./calls-provider";

type JsonRecord = Record<string, unknown>;

export type EffectiveCallListQuery = CallsQuery & {
  dataMode: "DEMO";
  page: number;
  size: number;
  sort: "eventTime" | "processingTime" | "capturedAt";
  order: "asc" | "desc";
};

const QUERY_KEYS = [
  "assetId",
  "ticker",
  "institutionId",
  "analystId",
  "direction",
  "status",
  "dataMode",
  "from",
  "to",
  "page",
  "size",
  "sort",
  "order",
] as const;
const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const TICKER = /^[A-Za-z0-9.^/-]{1,24}$/;
const RFC3339_INSTANT = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|([+-])(\d{2}):(\d{2}))$/;

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

function safeInteger(value: unknown, owner: string, minimum: number, maximum = Number.MAX_SAFE_INTEGER): number {
  if (!Number.isSafeInteger(value) || (value as number) < minimum || (value as number) > maximum) {
    fail(owner, `must be a safe integer from ${minimum} through ${maximum}`);
  }
  return value as number;
}

function exactString(value: unknown, owner: string, pattern: RegExp): string {
  if (typeof value !== "string" || !pattern.test(value)) fail(owner, "has invalid syntax");
  return value;
}

function enumValue<const T extends readonly string[]>(value: unknown, allowed: T, owner: string): T[number] {
  if (typeof value !== "string" || !allowed.includes(value)) {
    fail(owner, `must be one of ${allowed.join(", ")}`);
  }
  return value as T[number];
}

function instantOrder(value: string, owner: string): bigint {
  const match = RFC3339_INSTANT.exec(value);
  if (!match) fail(owner, "must be an RFC 3339 instant with at most nanosecond precision");
  const localMilliseconds = Date.parse(`${match[1]}Z`);
  if (!Number.isFinite(localMilliseconds) || new Date(localMilliseconds).toISOString() !== `${match[1]}.000Z`) {
    fail(owner, "must be a real RFC 3339 instant");
  }
  const offsetHours = match[3] === "Z" ? 0 : Number(match[5]);
  const offsetMinutes = match[3] === "Z" ? 0 : Number(match[6]);
  if (offsetHours > 18 || offsetMinutes > 59 || (offsetHours === 18 && offsetMinutes !== 0)) {
    fail(owner, "has an invalid Java-compatible UTC offset");
  }
  const direction = match[4] === "+" ? 1 : match[4] === "-" ? -1 : 0;
  const utcMilliseconds = localMilliseconds - direction * (offsetHours * 60 + offsetMinutes) * 60_000;
  const nanoseconds = (match[2] ?? "").padEnd(9, "0");
  return BigInt(utcMilliseconds) * 1_000_000n + BigInt(nanoseconds || "0");
}

function optionalIdentifier(value: unknown, owner: string): string | undefined {
  if (value === undefined) return undefined;
  return exactString(value, owner, IDENTIFIER);
}

function optionalInstant(value: unknown, owner: string): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== "string") fail(owner, "must be a string");
  instantOrder(value, owner);
  return value;
}

export function effectiveCallListQuery(query: CallsQuery = {}): EffectiveCallListQuery {
  const record = query as Record<string, unknown>;
  const unexpected = Object.keys(record).filter((key) => !QUERY_KEYS.includes(key as (typeof QUERY_KEYS)[number]));
  if (unexpected.length > 0) fail("Call list query", `contains unsupported fields: ${unexpected.join(", ")}`);

  const requestedMode = record.dataMode;
  if (requestedMode !== undefined && requestedMode !== "DEMO") {
    fail("Call list query.dataMode", "must equal DEMO in this phase");
  }
  const from = optionalInstant(record.from, "Call list query.from");
  const to = optionalInstant(record.to, "Call list query.to");
  if (from !== undefined && to !== undefined && instantOrder(from, "Call list query.from") >= instantOrder(to, "Call list query.to")) {
    fail("Call list query", "requires to later than from");
  }

  const ticker = record.ticker === undefined
    ? undefined
    : exactString(record.ticker, "Call list query.ticker", TICKER);
  const direction = record.direction === undefined
    ? undefined
    : enumValue(record.direction, CALL_DIRECTIONS, "Call list query.direction");
  const status = record.status === undefined
    ? undefined
    : enumValue(record.status, CALL_STATUSES, "Call list query.status");
  const sort = record.sort === undefined
    ? "eventTime"
    : enumValue(record.sort, CALL_SORT_FIELDS, "Call list query.sort");
  const order = record.order === undefined
    ? "desc"
    : enumValue(record.order, ["asc", "desc"] as const, "Call list query.order");

  return {
    assetId: optionalIdentifier(record.assetId, "Call list query.assetId"),
    ticker,
    institutionId: optionalIdentifier(record.institutionId, "Call list query.institutionId"),
    analystId: optionalIdentifier(record.analystId, "Call list query.analystId"),
    direction,
    status,
    dataMode: "DEMO",
    from,
    to,
    page: record.page === undefined
      ? 0
      : safeInteger(record.page, "Call list query.page", 0, 2_147_483_647),
    size: record.size === undefined
      ? 25
      : safeInteger(record.size, "Call list query.size", 1, 100),
    sort,
    order,
  };
}

function adaptPageMetadata(value: unknown) {
  const record = closedRecord(
    value,
    ["number", "size", "totalElements", "totalPages", "first", "last", "sort"],
    "Call list response.page",
  );
  const sort = closedRecord(record.sort, ["field", "order"], "Call list response.page.sort");
  if (typeof record.first !== "boolean" || typeof record.last !== "boolean") {
    fail("Call list response.page", "first and last must be booleans");
  }
  return {
    number: safeInteger(record.number, "Call list response.page.number", 0, 2_147_483_647),
    size: safeInteger(record.size, "Call list response.page.size", 1, 100),
    totalElements: safeInteger(record.totalElements, "Call list response.page.totalElements", 0),
    totalPages: safeInteger(record.totalPages, "Call list response.page.totalPages", 0, 2_147_483_647),
    first: record.first,
    last: record.last,
    sort: {
      field: enumValue(sort.field, CALL_SORT_FIELDS, "Call list response.page.sort.field"),
      order: enumValue(sort.order, ["asc", "desc"] as const, "Call list response.page.sort.order"),
    },
  };
}

function validatePageContract(page: AnalystCallPage, query: EffectiveCallListQuery) {
  const metadata = page.page;
  if (
    metadata.number !== query.page ||
    metadata.size !== query.size ||
    metadata.sort.field !== query.sort ||
    metadata.sort.order !== query.order
  ) {
    fail("Call list response", "does not echo the effective page, size, sort, and order contract");
  }
  const expectedTotalPages = Math.ceil(metadata.totalElements / metadata.size);
  const expectedFirst = metadata.number === 0;
  const expectedLast = expectedTotalPages === 0 || metadata.number >= expectedTotalPages - 1;
  const expectedItems = Math.min(
    metadata.size,
    Math.max(metadata.totalElements - metadata.number * metadata.size, 0),
  );
  if (
    metadata.totalPages !== expectedTotalPages ||
    metadata.first !== expectedFirst ||
    metadata.last !== expectedLast ||
    page.items.length !== expectedItems
  ) {
    fail("Call list response", "has inconsistent totals, boundary flags, or item cardinality");
  }
}

function validateItemModeAndFilters(item: AnalystCallView, query: EffectiveCallListQuery) {
  const { call, institution, analyst, asset, source } = item;
  if (
    call.dataMode !== "DEMO" ||
    source.document.dataMode !== "DEMO" ||
    source.reference.dataMode !== "DEMO"
  ) {
    fail(`Call list item ${call.callId}`, "must remain DEMO on every returned surface");
  }
  if (query.assetId !== undefined && asset.assetId !== query.assetId) {
    fail(`Call list item ${call.callId}`, "does not match the assetId filter");
  }
  if (query.ticker !== undefined && asset.ticker?.toLocaleLowerCase("en-US") !== query.ticker.toLocaleLowerCase("en-US")) {
    fail(`Call list item ${call.callId}`, "does not match the ticker filter");
  }
  if (query.institutionId !== undefined && institution.institutionId !== query.institutionId) {
    fail(`Call list item ${call.callId}`, "does not match the institutionId filter");
  }
  if (query.analystId !== undefined && analyst?.analystId !== query.analystId) {
    fail(`Call list item ${call.callId}`, "does not match the analystId filter");
  }
  if (query.direction !== undefined && call.direction !== query.direction) {
    fail(`Call list item ${call.callId}`, "does not match the direction filter");
  }
  if (query.status !== undefined && call.status !== query.status) {
    fail(`Call list item ${call.callId}`, "does not match the status filter");
  }
  const eventTime = instantOrder(call.eventTime, `Call list item ${call.callId}.eventTime`);
  if (query.from !== undefined && eventTime < instantOrder(query.from, "Call list query.from")) {
    fail(`Call list item ${call.callId}`, "precedes the inclusive from filter");
  }
  if (query.to !== undefined && eventTime >= instantOrder(query.to, "Call list query.to")) {
    fail(`Call list item ${call.callId}`, "does not precede the exclusive to filter");
  }
}

function validateItems(items: AnalystCallView[], query: EffectiveCallListQuery) {
  const callIds = new Set<string>();
  const providerEvents = new Set<string>();
  for (const item of items) {
    validateItemModeAndFilters(item, query);
    if (callIds.has(item.call.callId)) fail(`Call list item ${item.call.callId}`, "duplicates a call ID");
    const providerEvent = JSON.stringify([item.call.provider, item.call.providerEventId]);
    if (providerEvents.has(providerEvent)) fail(`Call list item ${item.call.callId}`, "duplicates a provider event identity");
    callIds.add(item.call.callId);
    providerEvents.add(providerEvent);
  }
  for (let index = 1; index < items.length; index += 1) {
    const left = items[index - 1]!.call;
    const right = items[index]!.call;
    const primary = compareCallAuditInstants(left[query.sort], right[query.sort]);
    const orderedPrimary = query.order === "asc" ? primary : -primary;
    if (orderedPrimary > 0 || (primary === 0 && left.callId > right.callId)) {
      fail("Call list response.items", "is not in deterministic requested order");
    }
  }
}

function adaptDatasetEvidence(value: unknown): CallListDatasetEvidence {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    fail("Call list datasetEvidence", "must be an object");
  }
  const availability = (value as JsonRecord).availability;
  if (availability === "AVAILABLE") {
    const record = closedRecord(value, ["availability", "asOf", "source", "disclaimer"], "Call list datasetEvidence");
    const asOf = callAuditInstant(record.asOf, "Call list datasetEvidence.asOf");
    if (typeof record.source !== "string" || record.source.trim() === "") {
      fail("Call list datasetEvidence.source", "must be non-blank");
    }
    if (typeof record.disclaimer !== "string" || record.disclaimer.trim() === "") {
      fail("Call list datasetEvidence.disclaimer", "must be non-blank");
    }
    return {
      availability,
      asOf,
      source: record.source,
      disclaimer: record.disclaimer,
    } satisfies AvailableCallListDatasetEvidence;
  }
  const record = closedRecord(
    value,
    ["availability", "reason", "asOf", "source", "disclaimer"],
    "Call list datasetEvidence",
  );
  if (
    record.availability !== "NOT_EXPOSED" ||
    record.reason !== CALL_LIST_METADATA_NOT_EXPOSED_REASON ||
    record.asOf !== null ||
    record.source !== null ||
    record.disclaimer !== null
  ) {
    fail("Call list datasetEvidence", "must use the exact API metadata-not-exposed state");
  }
  return {
    availability: "NOT_EXPOSED",
    reason: CALL_LIST_METADATA_NOT_EXPOSED_REASON,
    asOf: null,
    source: null,
    disclaimer: null,
  };
}

export function adaptCallListResponse(
  value: unknown,
  query: CallsQuery = {},
  datasetEvidence: CallListDatasetEvidence,
): CallListSnapshot {
  const effectiveQuery = effectiveCallListQuery(query);
  const record = closedRecord(value, ["items", "page"], "Call list response");
  if (!Array.isArray(record.items)) fail("Call list response.items", "must be an array");
  const items = record.items.map((item) => adaptCallViewResponse(item));
  const page: AnalystCallPage = { items, page: adaptPageMetadata(record.page) };
  validatePageContract(page, effectiveQuery);
  validateItems(items, effectiveQuery);

  let latestCallCapturedAt: string | null = null;
  for (const item of items) {
    if (latestCallCapturedAt === null || compareCallAuditInstants(item.call.capturedAt, latestCallCapturedAt) > 0) {
      latestCallCapturedAt = item.call.capturedAt;
    }
  }
  const callProvenanceIds = [...new Set(items.map(({ call }) => call.provenanceId))].sort();
  const adaptedDatasetEvidence = adaptDatasetEvidence(datasetEvidence);
  if (
    adaptedDatasetEvidence.availability === "AVAILABLE" &&
    latestCallCapturedAt !== null &&
    compareCallAuditInstants(latestCallCapturedAt, adaptedDatasetEvidence.asOf) > 0
  ) {
    fail("Call list snapshot", "contains returned call evidence captured after the fixture dataset asOf");
  }

  return {
    items: [...items],
    page: { ...page.page, sort: { ...page.page.sort } },
    dataMode: "DEMO",
    returnedPageEvidence: {
      scope: "RETURNED_PAGE",
      latestCallCapturedAt,
      callProvenanceIds: Object.freeze([...callProvenanceIds]),
    },
    datasetEvidence: adaptedDatasetEvidence,
  };
}
