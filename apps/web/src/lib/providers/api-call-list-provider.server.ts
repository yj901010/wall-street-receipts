import { adaptCallListResponse, effectiveCallListQuery } from "./call-list-adapter";
import {
  CALL_LIST_METADATA_NOT_EXPOSED_REASON,
  type CallListProvider,
} from "./call-list-provider";
import type { CallsQuery } from "./calls-provider";

type FetchImplementation = (
  input: string | URL | globalThis.Request,
  init?: RequestInit,
) => Promise<Response>;

function assertServerRuntime() {
  if (typeof window !== "undefined") {
    throw new Error("Call list API transport is server-only.");
  }
}

function canonicalBaseUrl(value: string): URL {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error("API_BASE_URL must be an absolute HTTP(S) URL.");
  }
  if (
    (url.protocol !== "http:" && url.protocol !== "https:") ||
    url.username !== "" ||
    url.password !== "" ||
    url.search !== "" ||
    url.hash !== ""
  ) {
    throw new Error("API_BASE_URL must be an absolute HTTP(S) URL without credentials, query, or fragment.");
  }
  url.pathname = `${url.pathname.replace(/\/+$/, "")}/`;
  return url;
}

function listUrl(baseUrl: URL, query: CallsQuery): URL {
  const effective = effectiveCallListQuery(query);
  const url = new URL("v1/calls", baseUrl);
  const parameters: Array<[string, string | number | undefined]> = [
    ["assetId", effective.assetId],
    ["ticker", effective.ticker],
    ["institutionId", effective.institutionId],
    ["analystId", effective.analystId],
    ["direction", effective.direction],
    ["status", effective.status],
    ["dataMode", effective.dataMode],
    ["from", effective.from],
    ["to", effective.to],
    ["page", effective.page],
    ["size", effective.size],
    ["sort", effective.sort],
    ["order", effective.order],
  ];
  for (const [name, value] of parameters) {
    if (value !== undefined) url.searchParams.set(name, String(value));
  }
  return url;
}

async function jsonResponse(fetcher: FetchImplementation, url: URL): Promise<unknown> {
  let response: Response;
  try {
    response = await fetcher(url, {
      method: "GET",
      cache: "no-store",
      redirect: "error",
      headers: { Accept: "application/json" },
    });
  } catch (error) {
    throw new Error("Call list API request failed.", { cause: error });
  }
  if (response.status !== 200) {
    throw new Error(`Call list API returned HTTP ${response.status}.`);
  }
  const contentType = response.headers.get("content-type")?.split(";", 1)[0]?.trim().toLocaleLowerCase("en-US") ?? "";
  if (contentType !== "application/json") {
    throw new Error("Call list API did not return application/json.");
  }
  try {
    return await response.json();
  } catch (error) {
    throw new Error("Call list API returned malformed JSON.", { cause: error });
  }
}

export class ApiCallListProvider implements CallListProvider {
  private readonly baseUrl: URL;

  constructor(
    baseUrl: string,
    private readonly fetcher: FetchImplementation = fetch,
  ) {
    assertServerRuntime();
    this.baseUrl = canonicalBaseUrl(baseUrl);
  }

  async list(query: CallsQuery = {}) {
    const effectiveQuery = effectiveCallListQuery(query);
    const payload = await jsonResponse(this.fetcher, listUrl(this.baseUrl, effectiveQuery));
    return adaptCallListResponse(payload, effectiveQuery, {
      availability: "NOT_EXPOSED",
      reason: CALL_LIST_METADATA_NOT_EXPOSED_REASON,
      asOf: null,
      source: null,
      disclaimer: null,
    });
  }
}
