import { describe, expect, it } from "vitest";
import {
  adaptCallListResponse,
  effectiveCallListQuery,
} from "./call-list-adapter";
import {
  CALL_LIST_METADATA_NOT_EXPOSED_REASON,
  type AvailableCallListDatasetEvidence,
} from "./call-list-provider";
import type { AnalystCallPage, CallsQuery } from "./calls-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";

const delegate = new FixtureCallsProvider();

function clone<T>(value: T): T {
  return structuredClone(value);
}

async function availableEvidence(): Promise<AvailableCallListDatasetEvidence> {
  const metadata = await delegate.metadata();
  return {
    availability: "AVAILABLE",
    asOf: metadata.asOf,
    source: metadata.source,
    disclaimer: metadata.disclaimer,
  };
}

async function fixturePage(query: CallsQuery = {}) {
  const effective = effectiveCallListQuery(query);
  return {
    effective,
    page: await delegate.list(effective),
    evidence: await availableEvidence(),
  };
}

describe("call list adapter", () => {
  it("adapts the exact closed list response and derives only RETURNED_PAGE evidence", async () => {
    const { page, effective, evidence } = await fixturePage();
    const before = JSON.stringify({ page, evidence });
    const snapshot = adaptCallListResponse(page, effective, evidence);

    expect(snapshot).toMatchObject({
      dataMode: "DEMO",
      page: {
        number: 0,
        size: 25,
        totalElements: 3,
        totalPages: 1,
        first: true,
        last: true,
        sort: { field: "eventTime", order: "desc" },
      },
      returnedPageEvidence: {
        scope: "RETURNED_PAGE",
        latestCallCapturedAt: "2026-08-11T14:22:00Z",
        callProvenanceIds: ["fixture-analyst-calls-v1"],
      },
      datasetEvidence: evidence,
    });
    expect(snapshot.items.map(({ call }) => call.callId)).toEqual([
      "demo-call-002",
      "demo-call-001",
      "demo-call-003",
    ]);
    expect(snapshot.items.find(({ call }) => call.callId === "demo-call-003")).toMatchObject({
      analyst: null,
      call: {
        analystId: null,
        originalRating: null,
        previousTarget: null,
        target: null,
        currency: null,
        targetDate: null,
      },
      source: {
        document: {
          publisher: null,
          canonicalUrl: null,
          publishedAt: null,
          externalId: null,
          contentHash: null,
        },
      },
    });
    expect(snapshot.items).not.toBe(page.items);
    expect(Object.isFrozen(snapshot.returnedPageEvidence.callProvenanceIds)).toBe(true);
    expect(JSON.stringify({ page, evidence })).toBe(before);
    page.items[0]!.call.provenanceId = "mutated-input-provenance";
    expect(snapshot.returnedPageEvidence.callProvenanceIds).toEqual(["fixture-analyst-calls-v1"]);
    expect(() => (snapshot.returnedPageEvidence.callProvenanceIds as string[]).push("mutated-output"))
      .toThrow();
  });

  it("derives a distinct ASCII-sorted provenance set and latest capture only from returned rows", async () => {
    const { page, effective, evidence } = await fixturePage();
    const changed = clone(page);
    changed.items[0]!.call.provenanceId = "z-provenance";
    changed.items[0]!.call.capturedAt = "2026-08-11T14:22:00.000001Z";
    changed.items[1]!.call.provenanceId = "a-provenance";
    changed.items[1]!.call.capturedAt = "2026-08-11T15:20:45.000006Z";
    changed.items[2]!.call.provenanceId = "z-provenance";
    changed.items[2]!.call.capturedAt = "2026-08-10T10:02:00.000005Z";
    const before = JSON.stringify(changed);

    const snapshot = adaptCallListResponse(changed, effective, evidence);

    expect(snapshot.returnedPageEvidence).toEqual({
      scope: "RETURNED_PAGE",
      latestCallCapturedAt: "2026-08-11T15:20:45.000006Z",
      callProvenanceIds: ["a-provenance", "z-provenance"],
    });
    expect(JSON.stringify(changed)).toBe(before);
  });

  it("keeps a valid empty page response-bounded without invented evidence", async () => {
    const { page, effective, evidence } = await fixturePage({ ticker: "TSLA" });
    const snapshot = adaptCallListResponse(page, effective, evidence);

    expect(snapshot.items).toEqual([]);
    expect(snapshot.page).toMatchObject({ totalElements: 0, totalPages: 0, first: true, last: true });
    expect(snapshot.returnedPageEvidence).toEqual({
      scope: "RETURNED_PAGE",
      latestCallCapturedAt: null,
      callProvenanceIds: [],
    });
  });

  it("derives evidence from an older non-first returned page rather than dataset-latest calls", async () => {
    const { page, effective, evidence } = await fixturePage({ page: 1, size: 1 });
    expect(page.items.map(({ call }) => call.callId)).toEqual(["demo-call-001"]);

    const snapshot = adaptCallListResponse(page, effective, evidence);

    expect(snapshot.returnedPageEvidence).toEqual({
      scope: "RETURNED_PAGE",
      latestCallCapturedAt: "2026-08-10T12:03:00Z",
      callProvenanceIds: ["fixture-analyst-calls-v1"],
    });
    expect(snapshot.returnedPageEvidence.latestCallCapturedAt)
      .not.toBe("2026-08-11T14:22:00Z");
  });

  it("preserves the exact API dataset metadata-not-exposed state", async () => {
    const { page, effective } = await fixturePage();
    const snapshot = adaptCallListResponse(page, effective, {
      availability: "NOT_EXPOSED",
      reason: CALL_LIST_METADATA_NOT_EXPOSED_REASON,
      asOf: null,
      source: null,
      disclaimer: null,
    });

    expect(snapshot.datasetEvidence).toEqual({
      availability: "NOT_EXPOSED",
      reason: "LIST_API_HAS_NO_DATASET_METADATA",
      asOf: null,
      source: null,
      disclaimer: null,
    });
  });

  it("rejects additive list, item, page, and sort fields", async () => {
    const { page, effective, evidence } = await fixturePage();
    const root = { ...clone(page), extra: true };
    expect(() => adaptCallListResponse(root, effective, evidence)).toThrow(/must contain exactly/);

    const item = clone(page) as AnalystCallPage & { items: Array<Record<string, unknown>> };
    item.items[0]!.extra = true;
    expect(() => adaptCallListResponse(item, effective, evidence)).toThrow(/must contain exactly/);

    const pageExtra = clone(page) as AnalystCallPage & { page: Record<string, unknown> };
    pageExtra.page.extra = true;
    expect(() => adaptCallListResponse(pageExtra, effective, evidence)).toThrow(/must contain exactly/);

    const sortExtra = clone(page) as AnalystCallPage & { page: { sort: Record<string, unknown> } };
    sortExtra.page.sort.extra = true;
    expect(() => adaptCallListResponse(sortExtra, effective, evidence)).toThrow(/must contain exactly/);
  });

  it.each([
    ["missing nested field", (page: AnalystCallPage) => {
      delete (page.items[0]!.call as unknown as Record<string, unknown>).originalRating;
    }, /must contain exactly/],
    ["wrong nested boolean type", (page: AnalystCallPage) => {
      (page.items[0]!.source.reference as unknown as Record<string, unknown>).verified = "true";
    }, /must be a boolean/],
    ["non-positive target", (page: AnalystCallPage) => {
      page.items[0]!.call.target = 0;
    }, /must be positive/],
    ["relative source URL", (page: AnalystCallPage) => {
      page.items[0]!.source.document.canonicalUrl = "relative/source";
    }, /absolute URI/],
    ["impossible target date", (page: AnalystCallPage) => {
      page.items[0]!.call.targetDate = "2026-02-30";
    }, /real calendar date/],
    ["unknown direction", (page: AnalystCallPage) => {
      page.items[0]!.call.direction = "UP" as never;
    }, /must be one of/],
    ["reversed chronology", (page: AnalystCallPage) => {
      page.items[0]!.call.processingTime = "2026-08-11T14:19:59.999999Z";
    }, /must not precede eventTime/],
  ] satisfies Array<[string, (page: AnalystCallPage) => void, RegExp]>) (
    "rejects an entire list page for a %s in one returned item",
    async (_label, mutate, message) => {
      const { page, effective, evidence } = await fixturePage();
      const changed = clone(page);
      mutate(changed);
      expect(() => adaptCallListResponse(changed, effective, evidence)).toThrow(message);
    },
  );

  it.each([
    ["institution", (page: AnalystCallPage) => {
      page.items[0]!.institution.institutionId = "inst-jpm";
    }, /institution join mismatch/],
    ["analyst", (page: AnalystCallPage) => {
      page.items[0]!.analyst!.analystId = "analyst-demo-a";
    }, /analyst join mismatch/],
    ["asset", (page: AnalystCallPage) => {
      page.items[0]!.asset.assetId = "asset-spx";
    }, /asset join mismatch/],
    ["source reference", (page: AnalystCallPage) => {
      page.items[0]!.source.reference.sourceReferenceId = "source-ref-demo-001";
    }, /source-reference join mismatch/],
    ["source document", (page: AnalystCallPage) => {
      page.items[0]!.source.reference.sourceDocumentId = "source-demo-article-003";
    }, /source-document join mismatch/],
  ] satisfies Array<[string, (page: AnalystCallPage) => void, RegExp]>) (
    "rejects the complete page for a broken canonical %s join",
    async (_label, mutate, message) => {
      const { page, effective, evidence } = await fixturePage();
      const changed = clone(page);
      mutate(changed);
      expect(() => adaptCallListResponse(changed, effective, evidence)).toThrow(message);
    },
  );

  it.each([
    ["number", (page: AnalystCallPage) => { page.page.number = 1; }],
    ["size", (page: AnalystCallPage) => { page.page.size = 50; }],
    ["sort field", (page: AnalystCallPage) => { page.page.sort.field = "capturedAt"; }],
    ["sort order", (page: AnalystCallPage) => { page.page.sort.order = "asc"; }],
  ])("rejects a response that does not echo the effective %s", async (_label, mutate) => {
    const { page, effective, evidence } = await fixturePage();
    const changed = clone(page);
    mutate(changed);
    expect(() => adaptCallListResponse(changed, effective, evidence)).toThrow(/does not echo/);
  });

  it.each([
    ["total pages", (page: AnalystCallPage) => { page.page.totalPages = 2; }],
    ["first", (page: AnalystCallPage) => { page.page.first = false; }],
    ["last", (page: AnalystCallPage) => { page.page.last = false; }],
    ["cardinality", (page: AnalystCallPage) => { page.items.pop(); }],
  ])("rejects inconsistent page %s", async (_label, mutate) => {
    const { page, effective, evidence } = await fixturePage();
    const changed = clone(page);
    mutate(changed);
    expect(() => adaptCallListResponse(changed, effective, evidence)).toThrow(/inconsistent totals/);
  });

  it("rejects duplicate identities and nondeterministic row order", async () => {
    const { page, effective, evidence } = await fixturePage();
    const duplicate = clone(page);
    duplicate.items[1] = clone(duplicate.items[0]!);
    expect(() => adaptCallListResponse(duplicate, effective, evidence)).toThrow(/duplicates a call ID/);

    const reordered = clone(page);
    [reordered.items[0], reordered.items[1]] = [reordered.items[1]!, reordered.items[0]!];
    expect(() => adaptCallListResponse(reordered, effective, evidence)).toThrow(/deterministic requested order/);
  });

  it("rejects a duplicate provider-event tuple even when call IDs differ", async () => {
    const { page, effective, evidence } = await fixturePage();
    const duplicate = clone(page);
    duplicate.items[1]!.call.provider = duplicate.items[0]!.call.provider;
    duplicate.items[1]!.call.providerEventId = duplicate.items[0]!.call.providerEventId;
    expect(duplicate.items[1]!.call.callId).not.toBe(duplicate.items[0]!.call.callId);
    expect(() => adaptCallListResponse(duplicate, effective, evidence))
      .toThrow(/duplicates a provider event identity/);

    const delimiterSafe = clone(page);
    delimiterSafe.items[0]!.call.provider = "provider\u0000one";
    delimiterSafe.items[0]!.call.providerEventId = "event";
    delimiterSafe.items[1]!.call.provider = "provider";
    delimiterSafe.items[1]!.call.providerEventId = "one\u0000event";
    expect(() => adaptCallListResponse(delimiterSafe, effective, evidence)).not.toThrow();
  });

  it.each([
    ["eventTime", "asc"],
    ["eventTime", "desc"],
    ["processingTime", "asc"],
    ["processingTime", "desc"],
    ["capturedAt", "asc"],
    ["capturedAt", "desc"],
  ] as const)("accepts only server order for %s %s, preserves server order, and rejects a misordered response", async (sort, order) => {
    const { page, effective, evidence } = await fixturePage({ sort, order });
    expect(adaptCallListResponse(page, effective, evidence).items).toEqual(page.items);

    const swapped = clone(page);
    [swapped.items[0], swapped.items[1]] = [swapped.items[1]!, swapped.items[0]!];
    expect(() => adaptCallListResponse(swapped, effective, evidence))
      .toThrow(/deterministic requested order/);
  });

  it("uses callId ascending as the equal-primary tie break for either sort direction", async () => {
    const { page, evidence } = await fixturePage({ size: 2 });
    const tied = clone(page);
    tied.items[0]!.call.eventTime = "2026-08-10T10:00:00Z";
    tied.items[1]!.call.eventTime = "2026-08-10T10:00:00Z";
    tied.items = [tied.items[1]!, tied.items[0]!];
    expect(tied.items.map(({ call }) => call.callId)).toEqual(["demo-call-001", "demo-call-002"]);
    expect(() => adaptCallListResponse(tied, { size: 2 }, evidence)).not.toThrow();

    const ascending = clone(tied);
    ascending.page.sort.order = "asc";
    expect(() => adaptCallListResponse(ascending, { size: 2, order: "asc" }, evidence)).not.toThrow();

    tied.items.reverse();
    expect(() => adaptCallListResponse(tied, { size: 2 }, evidence))
      .toThrow(/deterministic requested order/);
  });

  it("accepts a valid out-of-range echoed page without inventing rows", async () => {
    const { page, effective, evidence } = await fixturePage({ page: 99, size: 25 });
    const snapshot = adaptCallListResponse(page, effective, evidence);
    expect(snapshot.items).toEqual([]);
    expect(snapshot.page).toMatchObject({ number: 99, totalPages: 1, first: false, last: true });
  });

  it("rejects unsafe response and direct-query integers", async () => {
    const { page, effective, evidence } = await fixturePage();
    const unsafeTotal = clone(page);
    unsafeTotal.page.totalElements = Number.MAX_SAFE_INTEGER + 1;
    expect(() => adaptCallListResponse(unsafeTotal, effective, evidence)).toThrow(/safe integer/);
    const unsafePage = clone(page);
    unsafePage.page.number = Number.MAX_SAFE_INTEGER + 1;
    expect(() => adaptCallListResponse(unsafePage, effective, evidence)).toThrow(/safe integer/);
    expect(() => effectiveCallListQuery({ page: Number.MAX_SAFE_INTEGER + 1 })).toThrow(/safe integer/);
  });

  it.each([
    ["assetId", { assetId: "asset-spx" }, /assetId filter/],
    ["ticker", { ticker: "SPX" }, /ticker filter/],
    ["institutionId", { institutionId: "inst-jpm" }, /institutionId filter/],
    ["analystId", { analystId: "analyst-demo-a" }, /analystId filter/],
    ["direction", { direction: "NEUTRAL" }, /direction filter/],
    ["status", { status: "CANCELLED" }, /status filter/],
    ["from", { from: "2026-08-11T14:20:00.000000001Z" }, /inclusive from filter/],
    ["to", { to: "2026-08-11T14:20:00Z" }, /exclusive to filter/],
  ] satisfies Array<[string, CallsQuery, RegExp]>) (
    "rejects a returned row that violates the effective AND %s filter",
    async (_label, filter, message) => {
      const { page, evidence } = await fixturePage({ size: 1 });
      expect(() => adaptCallListResponse(page, { ...filter, size: 1 }, evidence)).toThrow(message);
    },
  );

  it.each(["call", "document", "reference"] as const)(
    "rejects non-DEMO mode independently on the returned %s surface",
    async (surface) => {
      const { page, effective, evidence } = await fixturePage();
      const changed = clone(page);
      if (surface === "call") changed.items[0]!.call.dataMode = "REALTIME";
      if (surface === "document") changed.items[0]!.source.document.dataMode = "DELAYED";
      if (surface === "reference") changed.items[0]!.source.reference.dataMode = "EOD";
      expect(() => adaptCallListResponse(changed, effective, evidence)).toThrow(/DEMO|data-mode mismatch/);
    },
  );

  it.each(["REALTIME", "DELAYED", "EOD"] as const)(
    "rejects a direct %s provider query instead of overwriting it with DEMO",
    (dataMode) => {
      expect(() => effectiveCallListQuery({ dataMode })).toThrow(/must equal DEMO/);
    },
  );

  it.each(["REALTIME", "DELAYED", "EOD"] as const)(
    "rejects an otherwise coherent %s returned item at the explicit DEMO publication guard",
    async (dataMode) => {
      const { page, effective, evidence } = await fixturePage();
      const changed = clone(page);
      changed.items[0]!.call.dataMode = dataMode;
      changed.items[0]!.source.document.dataMode = dataMode;
      changed.items[0]!.source.reference.dataMode = dataMode;
      expect(() => adaptCallListResponse(changed, effective, evidence))
        .toThrow(/must remain DEMO on every returned surface/);
    },
  );

  it("matches Spring request instant precision and offset limits with nanosecond-safe ordering", () => {
    expect(effectiveCallListQuery({
      from: "2026-08-11T14:20:00.000000001Z",
      to: "2026-08-11T14:20:00.000000002Z",
    })).toMatchObject({
      from: "2026-08-11T14:20:00.000000001Z",
      to: "2026-08-11T14:20:00.000000002Z",
    });
    expect(effectiveCallListQuery({ from: "2026-08-11T00:00:00+18:00" }).from)
      .toBe("2026-08-11T00:00:00+18:00");
    expect(() => effectiveCallListQuery({ from: "2026-08-11T00:00:00+18:01" }))
      .toThrow(/Java-compatible UTC offset/);
    expect(() => effectiveCallListQuery({ from: "2026-08-11T00:00:00.0000000001Z" }))
      .toThrow(/nanosecond precision/);
  });

  it("requires AVAILABLE dataset asOf to bound every returned call capture", async () => {
    const { page, effective, evidence } = await fixturePage();
    expect(() => adaptCallListResponse(page, effective, {
      ...evidence,
      asOf: "2026-08-11T14:21:59.999999Z",
    })).toThrow(/captured after the fixture dataset asOf/);
  });

  it("rejects malformed dataset evidence without weakening the NOT_EXPOSED null contract", async () => {
    const { page, effective } = await fixturePage();
    expect(() => adaptCallListResponse(page, effective, {
      availability: "NOT_EXPOSED",
      reason: CALL_LIST_METADATA_NOT_EXPOSED_REASON,
      asOf: null,
      source: null,
      disclaimer: "invented",
    } as never)).toThrow(/exact API metadata-not-exposed state/);
  });
});
