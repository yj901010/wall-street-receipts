import revisionsFixtureJson from "../../../../../fixtures/v1/analyst-call-revisions.json";
import outcomesFixtureJson from "../../../../../fixtures/v1/call-outcomes.json";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiCallAuditProvider } from "./api-call-audit-provider.server";
import { FixtureCallAuditProvider } from "./fixture-call-audit-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";

type FetchCall = { url: URL; init: RequestInit | undefined };
type ApiStage = "detail" | "context" | "revisions" | "outcomes";

function json(value: unknown, status = 200, contentType = "application/json; charset=utf-8") {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": contentType },
  });
}

async function payloads(callId: string) {
  const calls = new FixtureCallsProvider();
  const detail = await calls.findById(callId);
  const context = await calls.findContextByCallId(callId);
  if (!detail || !context) throw new Error("Missing test payload.");
  return {
    detail: structuredClone(detail),
    context: structuredClone(context),
    revisions: structuredClone(revisionsFixtureJson.revisions.filter((revision) => revision.callId === callId)),
    outcomes: structuredClone([
      ...outcomesFixtureJson.outcomes.filter((outcome) => outcome.callId === callId),
    ].sort((left, right) => {
      const horizons = ["D1", "W1", "M1", "M3", "M6", "Y1"];
      return horizons.indexOf(left.horizon) - horizons.indexOf(right.horizon)
        || (left.methodologyId < right.methodologyId ? -1 : left.methodologyId > right.methodologyId ? 1 : 0)
        || (left.methodologyVersion < right.methodologyVersion ? -1 : left.methodologyVersion > right.methodologyVersion ? 1 : 0)
        || left.sequenceNumber - right.sequenceNumber
        || (left.outcomeId < right.outcomeId ? -1 : left.outcomeId > right.outcomeId ? 1 : 0);
    })),
  };
}

function apiStage(input: string | URL | Request): ApiStage {
  const pathname = new URL(input instanceof Request ? input.url : input.toString()).pathname;
  if (pathname.endsWith("/context")) return "context";
  if (pathname.endsWith("/revisions")) return "revisions";
  if (pathname.endsWith("/outcomes")) return "outcomes";
  return "detail";
}

function validStageResponse(
  payload: Awaited<ReturnType<typeof payloads>>,
  stage: ApiStage,
) {
  return json(payload[stage]);
}

function failingStageTransport(
  payload: Awaited<ReturnType<typeof payloads>>,
  failingStage: ApiStage,
  failure: () => Promise<Response> | Response,
) {
  return vi.fn(async (input: string | URL | Request) => {
    const stage = apiStage(input);
    return stage === failingStage ? failure() : validStageResponse(payload, stage);
  });
}

function transport(payload: Awaited<ReturnType<typeof payloads>>) {
  const calls: FetchCall[] = [];
  const fetcher = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = new URL(input instanceof Request ? input.url : input.toString());
    calls.push({ url, init });
    if (url.pathname.endsWith("/context")) return json(payload.context);
    if (url.pathname.endsWith("/revisions")) return json(payload.revisions);
    if (url.pathname.endsWith("/outcomes")) return json(payload.outcomes);
    return json(payload.detail);
  });
  return { calls, fetcher };
}

describe("ApiCallAuditProvider", () => {
  beforeEach(() => {
    vi.stubGlobal("window", undefined);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fetches detail first, then context, revisions, and outcomes as one fail-closed aggregate", async () => {
    const fixturePayload = await payloads("demo-call-002");
    const { calls, fetcher } = transport(fixturePayload);
    const provider = new ApiCallAuditProvider("http://api.example.test/base/", fetcher);

    const audit = await provider.findById("demo-call-002");

    expect(audit?.revisions).toHaveLength(2);
    expect(audit?.outcomes).toEqual([]);
    expect(calls.map(({ url }) => url.pathname)).toEqual([
      "/base/v1/calls/demo-call-002",
      "/base/v1/calls/demo-call-002/context",
      "/base/v1/calls/demo-call-002/revisions",
      "/base/v1/calls/demo-call-002/outcomes",
    ]);
    for (const { init } of calls) {
      expect(init).toMatchObject({ method: "GET", cache: "no-store", redirect: "error" });
      expect(init?.headers).toEqual({ Accept: "application/json" });
      expect(init?.credentials).toBeUndefined();
    }
  });

  it("returns null after the detail 404 without requesting dependent resources", async () => {
    const fetcher = vi.fn(async () => json({ code: "CALL_NOT_FOUND" }, 404, "application/problem+json"));
    const provider = new ApiCallAuditProvider("http://api.example.test", fetcher);

    await expect(provider.findById("missing-call")).resolves.toBeNull();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it.each(["context", "revisions", "outcomes"] as const)(
    "rejects an exact dependent %s 404 while only detail 404 maps to not found",
    async (stage) => {
      const fixturePayload = await payloads("demo-call-002");
      const fetcher = failingStageTransport(
        fixturePayload,
        stage,
        () => json({ code: "CALL_NOT_FOUND" }, 404, "application/problem+json"),
      );
      await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("demo-call-002"))
        .rejects.toThrow(new RegExp(`${stage} returned HTTP 404`));
      expect(fetcher).toHaveBeenCalledTimes(4);
    },
  );

  it("percent-encodes every path segment from the validated opaque call ID", async () => {
    const callId = "demo-call-002:archive";
    const fixturePayload = await payloads("demo-call-002");
    fixturePayload.detail.call.callId = callId;
    if (fixturePayload.detail.snapshot) fixturePayload.detail.snapshot.callId = callId;
    for (const revision of fixturePayload.revisions) revision.callId = callId;
    for (const outcome of fixturePayload.outcomes) outcome.callId = callId;
    const { calls, fetcher } = transport(fixturePayload);

    await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById(callId))
      .resolves.toMatchObject({ detail: { call: { callId } } });
    expect(calls.map(({ url }) => url.href)).toEqual([
      "http://api.example.test/v1/calls/demo-call-002%3Aarchive",
      "http://api.example.test/v1/calls/demo-call-002%3Aarchive/context",
      "http://api.example.test/v1/calls/demo-call-002%3Aarchive/revisions",
      "http://api.example.test/v1/calls/demo-call-002%3Aarchive/outcomes",
    ]);
  });

  it("preserves known-empty revisions beside a populated outcome response", async () => {
    const fixturePayload = await payloads("demo-call-001");
    const { fetcher } = transport(fixturePayload);
    const provider = new ApiCallAuditProvider("http://api.example.test", fetcher);

    await expect(provider.findById("demo-call-001")).resolves.toMatchObject({ revisions: [], outcomes: { length: 4 } });
  });

  it.each(["detail", "context", "revisions", "outcomes"] as const)(
    "fails closed on a %s network failure",
    async (stage) => {
      const fixturePayload = await payloads("demo-call-002");
      const fetcher = failingStageTransport(fixturePayload, stage, () => {
        throw new TypeError("network unavailable");
      });
      await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("demo-call-002"))
        .rejects.toThrow(new RegExp(`${stage} request failed`));
      expect(fetcher).toHaveBeenCalledTimes(stage === "detail" ? 1 : 4);
    },
  );

  it.each(["detail", "context", "revisions", "outcomes"] as const)(
    "fails closed on a %s non-2xx response",
    async (stage) => {
      const fixturePayload = await payloads("demo-call-002");
      const fetcher = failingStageTransport(fixturePayload, stage, () => json({ code: "UPSTREAM" }, 503));
      await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("demo-call-002"))
        .rejects.toThrow(new RegExp(`${stage} returned HTTP 503`));
      expect(fetcher).toHaveBeenCalledTimes(stage === "detail" ? 1 : 4);
    },
  );

  it.each(["detail", "context", "revisions", "outcomes"] as const)(
    "fails closed on a %s non-JSON content type",
    async (stage) => {
      const fixturePayload = await payloads("demo-call-002");
      const fetcher = failingStageTransport(fixturePayload, stage, () => json({}, 200, "text/html"));
      await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("demo-call-002"))
        .rejects.toThrow(new RegExp(`${stage} did not return application/json`));
      expect(fetcher).toHaveBeenCalledTimes(stage === "detail" ? 1 : 4);
    },
  );

  it.each(["detail", "context", "revisions", "outcomes"] as const)(
    "fails closed on malformed %s JSON",
    async (stage) => {
      const fixturePayload = await payloads("demo-call-002");
      const fetcher = failingStageTransport(fixturePayload, stage, () => new Response("{", {
        status: 200,
        headers: { "content-type": "application/json" },
      }));
      await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("demo-call-002"))
        .rejects.toThrow(new RegExp(`${stage} returned malformed JSON`));
      expect(fetcher).toHaveBeenCalledTimes(stage === "detail" ? 1 : 4);
    },
  );

  it.each([
    ["detail", {}],
    ["context", null],
    ["revisions", { revisions: [] }],
    ["outcomes", { outcomes: [] }],
  ] as const)("fails closed on a malformed %s shape", async (stage, malformedShape) => {
    const fixturePayload = await payloads("demo-call-002");
    const fetcher = failingStageTransport(fixturePayload, stage, () => json(malformedShape));
    await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("demo-call-002"))
      .rejects.toThrow();
    expect(fetcher).toHaveBeenCalledTimes(stage === "detail" ? 1 : 4);
  });

  it("rejects call-ID divergence at detail, context, revision, and outcome stages", async () => {
    const detailDivergence = await payloads("demo-call-002");
    detailDivergence.detail.call.callId = "other-call";
    if (detailDivergence.detail.snapshot) detailDivergence.detail.snapshot.callId = "other-call";
    const detailFetch = transport(detailDivergence).fetcher;
    await expect(new ApiCallAuditProvider("http://api.example.test", detailFetch).findById("demo-call-002"))
      .rejects.toThrow(/detail did not match/);

    const contextDivergence = await payloads("demo-call-001");
    if (!contextDivergence.context.macroSnapshot) throw new Error("Expected populated context.");
    contextDivergence.context.macroSnapshot.callId = "other-call";
    const contextFetch = transport(contextDivergence).fetcher;
    await expect(new ApiCallAuditProvider("http://api.example.test", contextFetch).findById("demo-call-001"))
      .rejects.toThrow(/macro snapshot identity/);

    const revisionDivergence = await payloads("demo-call-002");
    revisionDivergence.revisions[0]!.callId = "other-call";
    const revisionFetch = transport(revisionDivergence).fetcher;
    await expect(new ApiCallAuditProvider("http://api.example.test", revisionFetch).findById("demo-call-002"))
      .rejects.toThrow(/must equal demo-call-002/);

    const outcomeDivergence = await payloads("demo-call-001");
    outcomeDivergence.outcomes[0]!.callId = "other-call";
    const outcomeFetch = transport(outcomeDivergence).fetcher;
    await expect(new ApiCallAuditProvider("http://api.example.test", outcomeFetch).findById("demo-call-001"))
      .rejects.toThrow(/must equal demo-call-001/);
  });

  it("fails the whole aggregate on any dependent status without a fixture fallback", async () => {
    const fixturePayload = await payloads("demo-call-002");
    const fixtureFallback = vi.spyOn(FixtureCallAuditProvider.prototype, "findById");
    const fetcher = vi.fn(async (input: string | URL | Request) => {
      const pathname = new URL(input instanceof Request ? input.url : input.toString()).pathname;
      if (pathname.endsWith("/context")) return json({ code: "CALL_NOT_FOUND" }, 404, "application/problem+json");
      if (pathname.endsWith("/revisions")) return json(fixturePayload.revisions);
      if (pathname.endsWith("/outcomes")) return json(fixturePayload.outcomes);
      return json(fixturePayload.detail);
    });

    await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("demo-call-002"))
      .rejects.toThrow(/context returned HTTP 404/);
    expect(fixtureFallback).not.toHaveBeenCalled();
    fixtureFallback.mockRestore();
  });

  it("rejects malformed dependent payloads after a valid detail without partial publication", async () => {
    const fixturePayload = await payloads("demo-call-002");
    const fetcher = vi.fn(async (input: string | URL | Request) => {
      const pathname = new URL(input instanceof Request ? input.url : input.toString()).pathname;
      if (pathname.endsWith("/context")) return json(fixturePayload.context);
      if (pathname.endsWith("/revisions")) return json({ revisions: fixturePayload.revisions });
      if (pathname.endsWith("/outcomes")) return json(fixturePayload.outcomes);
      return json(fixturePayload.detail);
    });

    await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("demo-call-002"))
      .rejects.toThrow(/revisions response must be an array/i);
  });

  it("rejects JSONP, malformed JSON, redirects, and additive payload fields", async () => {
    const fixturePayload = await payloads("demo-call-002");

    const jsonp = vi.fn(async () => json(fixturePayload.detail, 200, "application/jsonp"));
    await expect(new ApiCallAuditProvider("http://api.example.test", jsonp).findById("demo-call-002"))
      .rejects.toThrow(/did not return application\/json/);

    const malformed = vi.fn(async () => new Response("{", {
      status: 200,
      headers: { "content-type": "application/json" },
    }));
    await expect(new ApiCallAuditProvider("http://api.example.test", malformed).findById("demo-call-002"))
      .rejects.toThrow(/malformed JSON/);

    const redirect = vi.fn(async (_input: string | URL | Request, init?: RequestInit) => {
      expect(init?.redirect).toBe("error");
      throw new TypeError("redirect mode is error");
    });
    await expect(new ApiCallAuditProvider("http://api.example.test", redirect).findById("demo-call-002"))
      .rejects.toThrow(/detail request failed/);

    const additiveDetail = { ...fixturePayload.detail, extra: true };
    const additive = vi.fn(async () => json(additiveDetail));
    await expect(new ApiCallAuditProvider("http://api.example.test", additive).findById("demo-call-002"))
      .rejects.toThrow(/exactly/);
  });

  it("rejects unsafe base URLs and invalid route identifiers before transport", async () => {
    expect(() => new ApiCallAuditProvider("ftp://api.example.test", vi.fn())).toThrow(/HTTP\(S\)/);
    expect(() => new ApiCallAuditProvider("http://user:secret@api.example.test", vi.fn())).toThrow(
      /without credentials/,
    );
    const fetcher = vi.fn();
    await expect(new ApiCallAuditProvider("http://api.example.test", fetcher).findById("invalid id"))
      .resolves.toBeNull();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("fails closed when a browser runtime constructs the server transport", () => {
    vi.stubGlobal("window", {});
    expect(() => new ApiCallAuditProvider("http://api.example.test", vi.fn())).toThrow(/server-only/);
  });
});
