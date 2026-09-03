import {
  CALL_DIRECTIONS,
  CALL_SORT_FIELDS,
  CALL_STATUSES,
  type CallsQuery,
} from "./calls-provider";
import {
  kstCalendarDateStartUtc,
  nextKstCalendarDateStartUtc,
} from "@/lib/kst-time";

export type CallListSearchValue = string | string[] | undefined;
export type CallListSearchParams = Record<string, CallListSearchValue>;

export type CallListFilterValues = {
  assetId: string;
  ticker: string;
  institutionId: string;
  analystId: string;
  direction: string;
  status: string;
  from: string;
  to: string;
  size: string;
  sort: string;
  order: string;
};

export type ParsedCallListSearch = {
  query: CallsQuery;
  values: CallListFilterValues;
};

const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const TICKER = /^[A-Za-z0-9.^/-]{1,24}$/;
const CALENDAR_DATE = /^\d{4}-\d{2}-\d{2}$/;
const REQUIRED_WHEN_PRESENT = new Set(["page", "size", "sort", "order"]);
const ALLOWED_PARAMETERS = new Set([
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
]);

function invalid(field: string, detail: string): never {
  throw new Error(`Invalid calls query parameter ${field}: ${detail}.`);
}

function single(raw: CallListSearchParams, field: string): string | undefined {
  const value = raw[field];
  if (Array.isArray(value)) invalid(field, "duplicate values are not allowed");
  if (value === undefined) return undefined;
  if (value === "") {
    if (REQUIRED_WHEN_PRESENT.has(field)) invalid(field, "an empty value is not allowed");
    return undefined;
  }
  if (value.trim() !== value || value.trim() === "") {
    invalid(field, "whitespace is not normalized");
  }
  return value;
}

function opaqueId(value: string | undefined, field: string): string | undefined {
  if (value !== undefined && !IDENTIFIER.test(value)) invalid(field, "expected an exact opaque identifier");
  return value;
}

function ticker(value: string | undefined): string | undefined {
  if (value !== undefined && !TICKER.test(value)) invalid("ticker", "unsupported ticker syntax");
  return value;
}

function enumValue<const T extends readonly string[]>(
  value: string | undefined,
  allowed: T,
  field: string,
): T[number] | undefined {
  if (value !== undefined && !allowed.includes(value)) {
    invalid(field, `expected one of ${allowed.join(", ")}`);
  }
  return value as T[number] | undefined;
}

function integer(
  value: string | undefined,
  field: string,
  minimum: number,
  maximum: number,
): number | undefined {
  if (value === undefined) return undefined;
  if (!/^(?:0|[1-9]\d*)$/.test(value)) invalid(field, "expected a canonical integer");
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
    invalid(field, `expected an integer from ${minimum} through ${maximum}`);
  }
  return parsed;
}

function date(value: string | undefined, field: "from" | "to"): string | undefined {
  if (value === undefined) return undefined;
  if (!CALENDAR_DATE.test(value)) invalid(field, "expected YYYY-MM-DD");
  if (value.startsWith("0000-")) invalid(field, "year 0000 is not supported by the date control");
  const parsed = new Date(`${value}T00:00:00.000Z`);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) {
    invalid(field, "expected a real calendar date");
  }
  return value;
}

function kstBoundary(value: string, field: "from" | "to", exclusive: boolean): string {
  try {
    return exclusive
      ? nextKstCalendarDateStartUtc(value)
      : kstCalendarDateStartUtc(value);
  } catch {
    return invalid(field, "date cannot be converted to a four-digit KST boundary");
  }
}

export function parseCallListSearchParams(raw: CallListSearchParams): ParsedCallListSearch {
  for (const field of Object.keys(raw)) {
    if (!ALLOWED_PARAMETERS.has(field)) invalid(field, "unsupported parameter");
  }

  const assetId = opaqueId(single(raw, "assetId"), "assetId");
  const tickerValue = ticker(single(raw, "ticker"));
  const institutionId = opaqueId(single(raw, "institutionId"), "institutionId");
  const analystId = opaqueId(single(raw, "analystId"), "analystId");
  const direction = enumValue(single(raw, "direction"), CALL_DIRECTIONS, "direction");
  const status = enumValue(single(raw, "status"), CALL_STATUSES, "status");
  const requestedMode = single(raw, "dataMode");
  if (requestedMode !== undefined && requestedMode !== "DEMO") {
    invalid("dataMode", "only DEMO is available in this phase");
  }
  const from = date(single(raw, "from"), "from");
  const to = date(single(raw, "to"), "to");
  const page = integer(single(raw, "page"), "page", 0, 2_147_483_647);
  const size = integer(single(raw, "size"), "size", 1, 100);
  const sort = enumValue(single(raw, "sort"), CALL_SORT_FIELDS, "sort");
  const order = enumValue(single(raw, "order"), ["asc", "desc"] as const, "order");

  const fromInstant = from ? kstBoundary(from, "from", false) : undefined;
  const toInstant = to ? kstBoundary(to, "to", true) : undefined;
  if (fromInstant && toInstant && Date.parse(fromInstant) >= Date.parse(toInstant)) {
    invalid("to", "exclusive upper bound must follow from");
  }

  return {
    query: {
      assetId,
      ticker: tickerValue,
      institutionId,
      analystId,
      direction,
      status,
      dataMode: "DEMO",
      from: fromInstant,
      to: toInstant,
      page,
      size,
      sort,
      order,
    },
    values: {
      assetId: assetId ?? "",
      ticker: tickerValue ?? "",
      institutionId: institutionId ?? "",
      analystId: analystId ?? "",
      direction: direction ?? "",
      status: status ?? "",
      from: from ?? "",
      to: to ?? "",
      size: size === undefined ? "" : String(size),
      sort: sort ?? "",
      order: order ?? "",
    },
  };
}
