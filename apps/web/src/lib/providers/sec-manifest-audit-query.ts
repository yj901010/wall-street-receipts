import {
  SEC_MANIFEST_AUDIT_VIEWS,
  type SecManifestAuditQuery,
  type SecManifestAuditView,
} from "./sec-manifest-audit-provider";

export const SEC_MANIFEST_AUDIT_ROUTE = "/research/sec/filing-history";
export const SEC_MANIFEST_AUDIT_DEFAULT_PAGE_SIZE = 25;

const MANIFEST_ID = /^[0-9a-f]{64}$/;
const UTC_INSTANT =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?Z$/;
const CANONICAL_UNSIGNED_DECIMAL = /^(0|[1-9][0-9]*)$/;
const ALLOWED_KEYS = new Set([
  "manifestId",
  "evaluationAsOf",
  "view",
  "page",
  "size",
]);

export type SecManifestAuditRouteState =
  | { kind: "locator" }
  | { kind: "invalid" }
  | { kind: "query"; query: SecManifestAuditQuery };

function daysInMonth(year: number, month: number): number {
  if (month === 2) {
    const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    return leap ? 29 : 28;
  }
  return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

export function isSecManifestAuditManifestId(value: string): boolean {
  return MANIFEST_ID.test(value);
}

export function isSecManifestAuditInstant(value: string): boolean {
  const match = UTC_INSTANT.exec(value);
  if (!match) return false;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = Number(match[6]);
  return (
    month >= 1 &&
    month <= 12 &&
    day >= 1 &&
    day <= daysInMonth(year, month) &&
    hour <= 23 &&
    minute <= 59 &&
    second <= 59
  );
}

function sortableUtcMicros(value: string): string {
  const match = UTC_INSTANT.exec(value);
  if (!match || !isSecManifestAuditInstant(value)) {
    throw new Error("SEC manifest audit instant comparison requires canonical UTC input.");
  }
  const fraction = (match[7] ?? "").padEnd(6, "0");
  return `${value.slice(0, 19)}.${fraction}Z`;
}

export function compareSecManifestAuditInstants(left: string, right: string): number {
  return sortableUtcMicros(left).localeCompare(sortableUtcMicros(right), "en-US");
}

function exactString(value: string | string[] | undefined): string | null {
  return typeof value === "string" && value !== "" && value.trim() === value
    ? value
    : null;
}

function pageNumber(value: string | string[] | undefined): number | null {
  const text = exactString(value);
  if (text === null || !CANONICAL_UNSIGNED_DECIMAL.test(text)) return null;
  const parsed = Number(text);
  return Number.isSafeInteger(parsed) && parsed <= 2_147_483_647 ? parsed : null;
}

function pageSize(value: string | string[] | undefined): number | null {
  const parsed = pageNumber(value);
  return parsed !== null && parsed >= 1 && parsed <= 100 ? parsed : null;
}

function view(value: string | string[] | undefined): SecManifestAuditView | null {
  if (value === undefined) return "summary";
  const text = exactString(value);
  return text !== null && SEC_MANIFEST_AUDIT_VIEWS.some((candidate) => candidate === text)
    ? (text as SecManifestAuditView)
    : null;
}

export function parseSecManifestAuditRoute(
  values: Record<string, string | string[] | undefined>,
): SecManifestAuditRouteState {
  const keys = Object.keys(values);
  if (keys.length === 0) return { kind: "locator" };
  if (keys.some((key) => !ALLOWED_KEYS.has(key))) return { kind: "invalid" };

  const manifestId = exactString(values.manifestId);
  const evaluationAsOf = exactString(values.evaluationAsOf);
  const requestedView = view(values.view);
  if (
    manifestId === null ||
    evaluationAsOf === null ||
    requestedView === null ||
    !isSecManifestAuditManifestId(manifestId) ||
    !isSecManifestAuditInstant(evaluationAsOf)
  ) {
    return { kind: "invalid" };
  }

  if (requestedView === "summary") {
    if (values.page !== undefined || values.size !== undefined) {
      return { kind: "invalid" };
    }
    return {
      kind: "query",
      query: {
        manifestId,
        evaluationAsOf,
        view: requestedView,
        page: 0,
        size: SEC_MANIFEST_AUDIT_DEFAULT_PAGE_SIZE,
      },
    };
  }

  const number = values.page === undefined ? 0 : pageNumber(values.page);
  const size = values.size === undefined
    ? SEC_MANIFEST_AUDIT_DEFAULT_PAGE_SIZE
    : pageSize(values.size);
  if (number === null || size === null) return { kind: "invalid" };
  return {
    kind: "query",
    query: {
      manifestId,
      evaluationAsOf,
      view: requestedView,
      page: number,
      size,
    },
  };
}

export function secManifestAuditHref(query: SecManifestAuditQuery): string {
  const parameters = new URLSearchParams({
    manifestId: query.manifestId,
    evaluationAsOf: query.evaluationAsOf,
    view: query.view,
  });
  if (query.view !== "summary") {
    parameters.set("page", String(query.page));
    parameters.set("size", String(query.size));
  }
  return `${SEC_MANIFEST_AUDIT_ROUTE}?${parameters.toString()}`;
}
