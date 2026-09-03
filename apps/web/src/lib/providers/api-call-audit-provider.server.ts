import {
  adaptCallContextResponse,
  adaptCallDetailResponse,
  adaptCallOutcomesResponse,
  adaptCallRevisionsResponse,
  validateCallAuditSnapshot,
} from "./call-audit-adapter";
import type { CallAuditProvider } from "./call-audit-provider";

const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;

type FetchImplementation = (
  input: string | URL | globalThis.Request,
  init?: RequestInit,
) => Promise<Response>;

function assertServerRuntime() {
  if (typeof window !== "undefined") {
    throw new Error("Call audit API transport is server-only.");
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

async function jsonResponse(
  fetcher: FetchImplementation,
  url: URL,
  resource: string,
  allowNotFound: boolean,
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
    throw new Error(`Call audit API ${resource} request failed.`, { cause: error });
  }
  if (allowNotFound && response.status === 404) return null;
  if (response.status !== 200) {
    throw new Error(`Call audit API ${resource} returned HTTP ${response.status}.`);
  }
  const contentType = response.headers.get("content-type")?.split(";", 1)[0]?.trim().toLocaleLowerCase("en-US") ?? "";
  if (contentType !== "application/json") {
    throw new Error(`Call audit API ${resource} did not return application/json.`);
  }
  try {
    return await response.json();
  } catch (error) {
    throw new Error(`Call audit API ${resource} returned malformed JSON.`, { cause: error });
  }
}

export class ApiCallAuditProvider implements CallAuditProvider {
  private readonly baseUrl: URL;

  constructor(
    baseUrl: string,
    private readonly fetcher: FetchImplementation = fetch,
  ) {
    assertServerRuntime();
    this.baseUrl = canonicalBaseUrl(baseUrl);
  }

  async findById(callId: string) {
    if (!IDENTIFIER.test(callId)) return null;
    const encodedCallId = encodeURIComponent(callId);
    const detailPayload = await jsonResponse(
      this.fetcher,
      new URL(`v1/calls/${encodedCallId}`, this.baseUrl),
      "detail",
      true,
    );
    if (detailPayload === null) return null;
    const detail = adaptCallDetailResponse(detailPayload);
    if (detail.call.callId !== callId) {
      throw new Error("Call audit API detail did not match the requested call ID.");
    }

    const [contextPayload, revisionsPayload, outcomesPayload] = await Promise.all([
      jsonResponse(
        this.fetcher,
        new URL(`v1/calls/${encodedCallId}/context`, this.baseUrl),
        "context",
        false,
      ),
      jsonResponse(
        this.fetcher,
        new URL(`v1/calls/${encodedCallId}/revisions`, this.baseUrl),
        "revisions",
        false,
      ),
      jsonResponse(
        this.fetcher,
        new URL(`v1/calls/${encodedCallId}/outcomes`, this.baseUrl),
        "outcomes",
        false,
      ),
    ]);

    return validateCallAuditSnapshot({
      detail,
      context: adaptCallContextResponse(contextPayload),
      revisions: adaptCallRevisionsResponse(revisionsPayload, callId),
      outcomes: adaptCallOutcomesResponse(outcomesPayload, callId),
    });
  }
}
