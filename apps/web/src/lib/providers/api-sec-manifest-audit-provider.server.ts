import { adaptSecManifestAuditResource } from "./sec-manifest-audit-adapter";
import type {
  SecManifestAuditProvider,
  SecManifestAuditQuery,
  SecManifestAuditResource,
} from "./sec-manifest-audit-provider";
import {
  isSecManifestAuditInstant,
  isSecManifestAuditManifestId,
} from "./sec-manifest-audit-query";

type FetchImplementation = (
  input: string | URL | globalThis.Request,
  init?: RequestInit,
) => Promise<Response>;

function assertServerRuntime() {
  if (typeof window !== "undefined") {
    throw new Error("SEC manifest audit API transport is server-only.");
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
    throw new Error(
      "API_BASE_URL must be an absolute HTTP(S) URL without credentials, query, or fragment.",
    );
  }
  url.pathname = `${url.pathname.replace(/\/+$/, "")}/`;
  return url;
}

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
    throw new Error("SEC manifest audit API transport received an invalid internal query.");
  }
}

function endpoint(baseUrl: URL, query: SecManifestAuditQuery): URL {
  const child = query.view === "summary" ? "" : `/${query.view}`;
  const url = new URL(
    `v1/sec/filing-history/manifests/${encodeURIComponent(query.manifestId)}${child}`,
    baseUrl,
  );
  url.searchParams.set("evaluationAsOf", query.evaluationAsOf);
  if (query.view !== "summary") {
    url.searchParams.set("page", String(query.page));
    url.searchParams.set("size", String(query.size));
  }
  return url;
}

async function jsonResponse(
  fetcher: FetchImplementation,
  url: URL,
  resource: string,
): Promise<unknown | null> {
  let response: Response;
  try {
    response = await fetcher(url, {
      method: "GET",
      cache: "no-store",
      redirect: "error",
      headers: { Accept: "application/json" },
    });
  } catch (error) {
    throw new Error(`SEC manifest audit API ${resource} request failed.`, {
      cause: error,
    });
  }
  if (response.status === 404) return null;
  if (response.status !== 200) {
    throw new Error(
      `SEC manifest audit API ${resource} returned HTTP ${response.status}.`,
    );
  }
  const contentType =
    response.headers
      .get("content-type")
      ?.split(";", 1)[0]
      ?.trim()
      .toLocaleLowerCase("en-US") ?? "";
  if (contentType !== "application/json") {
    throw new Error(
      `SEC manifest audit API ${resource} did not return application/json.`,
    );
  }
  try {
    return await response.json();
  } catch (error) {
    throw new Error(
      `SEC manifest audit API ${resource} returned malformed JSON.`,
      { cause: error },
    );
  }
}

export class ApiSecManifestAuditProvider implements SecManifestAuditProvider {
  readonly mode = "api" as const;
  readonly demoQuery = null;
  private readonly baseUrl: URL;

  constructor(
    baseUrl: string,
    private readonly fetcher: FetchImplementation = fetch,
    readonly syntheticDemoManifestId: string | null = null,
  ) {
    assertServerRuntime();
    if (
      syntheticDemoManifestId !== null &&
      !isSecManifestAuditManifestId(syntheticDemoManifestId)
    ) {
      throw new Error(
        "SEC_MANIFEST_AUDIT_SYNTHETIC_DEMO_MANIFEST_ID must be lowercase SHA-256 hex.",
      );
    }
    this.baseUrl = canonicalBaseUrl(baseUrl);
  }

  async findExact(query: SecManifestAuditQuery): Promise<SecManifestAuditResource | null> {
    requireInternalQuery(query);
    const payload = await jsonResponse(
      this.fetcher,
      endpoint(this.baseUrl, query),
      query.view,
    );
    return payload === null
      ? null
      : adaptSecManifestAuditResource(query.view, payload, query);
  }
}
