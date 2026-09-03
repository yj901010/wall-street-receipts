import { describe, expect, it } from "vitest";
import {
  compareSecManifestAuditInstants,
  isSecManifestAuditInstant,
  parseSecManifestAuditRoute,
  secManifestAuditHref,
} from "./sec-manifest-audit-query";

const MANIFEST_ID = "a".repeat(64);
const CUTOFF = "2026-08-25T03:30:00.123456Z";

describe("SEC manifest audit URL contract", () => {
  it("keeps the keyless locator separate from a complete exact query", () => {
    expect(parseSecManifestAuditRoute({})).toEqual({ kind: "locator" });
    expect(parseSecManifestAuditRoute({ manifestId: MANIFEST_ID, evaluationAsOf: CUTOFF }))
      .toEqual({
        kind: "query",
        query: {
          manifestId: MANIFEST_ID,
          evaluationAsOf: CUTOFF,
          view: "summary",
          page: 0,
          size: 25,
        },
      });
  });

  it("accepts only the closed child-view pagination grammar", () => {
    expect(parseSecManifestAuditRoute({
      manifestId: MANIFEST_ID,
      evaluationAsOf: CUTOFF,
      view: "occurrences",
      page: "2",
      size: "100",
    })).toEqual({
      kind: "query",
      query: {
        manifestId: MANIFEST_ID,
        evaluationAsOf: CUTOFF,
        view: "occurrences",
        page: 2,
        size: 100,
      },
    });

    for (const values of [
      { manifestId: MANIFEST_ID, evaluationAsOf: CUTOFF, page: "0" },
      { manifestId: MANIFEST_ID, evaluationAsOf: CUTOFF, size: "25" },
      { manifestId: MANIFEST_ID, evaluationAsOf: CUTOFF, view: "latest" },
      { manifestId: MANIFEST_ID, evaluationAsOf: CUTOFF, view: "descriptors", page: "01" },
      { manifestId: MANIFEST_ID, evaluationAsOf: CUTOFF, view: "descriptors", size: "0" },
      { manifestId: MANIFEST_ID, evaluationAsOf: CUTOFF, view: "descriptors", size: "101" },
      { manifestId: MANIFEST_ID, evaluationAsOf: CUTOFF, ticker: "NVDA" },
      { manifestId: [MANIFEST_ID, MANIFEST_ID], evaluationAsOf: CUTOFF },
    ]) {
      expect(parseSecManifestAuditRoute(values)).toEqual({ kind: "invalid" });
    }
  });

  it("rejects noncanonical identifiers and timestamps without inventing now", () => {
    for (const value of [
      "2026-02-29T00:00:00Z",
      "2024-02-30T00:00:00Z",
      "2026-08-25T03:30:00.1234567Z",
      "2026-08-25T03:30:00+00:00",
      "2026-08-25 03:30:00Z",
      " 2026-08-25T03:30:00Z",
    ]) {
      expect(isSecManifestAuditInstant(value)).toBe(false);
    }
    expect(isSecManifestAuditInstant("2024-02-29T23:59:59.1Z")).toBe(true);
    expect(parseSecManifestAuditRoute({
      manifestId: MANIFEST_ID.toUpperCase(),
      evaluationAsOf: CUTOFF,
    })).toEqual({ kind: "invalid" });
  });

  it("compares canonical instants without dropping microseconds", () => {
    expect(compareSecManifestAuditInstants(
      "2026-08-25T03:30:00.123455Z",
      "2026-08-25T03:30:00.123456Z",
    )).toBeLessThan(0);
    expect(compareSecManifestAuditInstants(
      "2026-08-25T03:30:00.1Z",
      "2026-08-25T03:30:00.100000Z",
    )).toBe(0);
    expect(compareSecManifestAuditInstants(
      "2026-08-25T03:30:01Z",
      "2026-08-25T03:30:00.999999Z",
    )).toBeGreaterThan(0);
  });

  it("serializes only canonical query state", () => {
    expect(secManifestAuditHref({
      manifestId: MANIFEST_ID,
      evaluationAsOf: CUTOFF,
      view: "summary",
      page: 9,
      size: 99,
    })).toBe(
      `/research/sec/filing-history?manifestId=${MANIFEST_ID}`
      + "&evaluationAsOf=2026-08-25T03%3A30%3A00.123456Z&view=summary",
    );
    expect(secManifestAuditHref({
      manifestId: MANIFEST_ID,
      evaluationAsOf: CUTOFF,
      view: "accessions",
      page: 2,
      size: 10,
    })).toContain("&view=accessions&page=2&size=10");
  });
});
