import { describe, expect, it } from "vitest";
import {
  FixtureSecManifestAuditProvider,
  SEC_MANIFEST_AUDIT_DEMO_QUERY,
} from "./fixture-sec-manifest-audit-provider";
import type { SecManifestAuditQuery, SecManifestAuditView } from "./sec-manifest-audit-provider";

function query(
  view: SecManifestAuditView = "summary",
  overrides: Partial<SecManifestAuditQuery> = {},
): SecManifestAuditQuery {
  return {
    ...SEC_MANIFEST_AUDIT_DEMO_QUERY,
    view,
    page: 0,
    size: 25,
    ...overrides,
  };
}

describe("FixtureSecManifestAuditProvider", () => {
  it("exposes only the Java-generated fixture identity as an explicit DEMO", async () => {
    const provider = new FixtureSecManifestAuditProvider();
    expect(provider.mode).toBe("fixture");
    expect(provider.demoQuery).toEqual(SEC_MANIFEST_AUDIT_DEMO_QUERY);

    const resource = await provider.findExact(query());
    expect(resource?.view).toBe("summary");
    if (!resource || resource.view !== "summary") throw new Error("summary expected");
    expect(resource.data.manifestId).toBe(SEC_MANIFEST_AUDIT_DEMO_QUERY.manifestId);
    expect(resource.data.canonicalConflictAccessionCount).toBe(1);
    expect(resource.data.exactAgreementAccessionCount).toBe(1);
    expect(resource.data.disclosure.atomicSecSnapshotClaim).toBe("NOT_MADE");
  });

  it("fails closed immediately before the microsecond cutoff and echoes later cutoffs", async () => {
    const provider = new FixtureSecManifestAuditProvider();
    await expect(provider.findExact(query("summary", {
      evaluationAsOf: "2026-08-25T03:30:00.123455Z",
    }))).resolves.toBeNull();
    await expect(provider.findExact(query("summary", {
      evaluationAsOf: "2026-08-25T03:30:00Z",
    }))).resolves.toBeNull();

    const later = await provider.findExact(query("summary", {
      evaluationAsOf: "2026-08-25T03:30:00.123457Z",
    }));
    expect(later?.data.evaluationAsOf).toBe("2026-08-25T03:30:00.123457Z");
  });

  it("does not substitute another record for an unknown exact ID", async () => {
    const provider = new FixtureSecManifestAuditProvider();
    await expect(provider.findExact(query("summary", {
      manifestId: "b".repeat(64),
    }))).resolves.toBeNull();
  });

  it("preserves fixed child order and honest out-of-range pagination", async () => {
    const provider = new FixtureSecManifestAuditProvider();
    const first = await provider.findExact(query("occurrences", { size: 2 }));
    expect(first?.view).toBe("occurrences");
    if (!first || first.view !== "occurrences") throw new Error("occurrences expected");
    expect(first.data.items.map((item) => item.occurrenceOrdinal)).toEqual([0, 1]);
    expect(first.data.page).toMatchObject({
      number: 0,
      size: 2,
      totalElements: 6,
      totalPages: 3,
      first: true,
      last: false,
      order: { field: "occurrenceOrdinal", direction: "ASC" },
    });

    const empty = await provider.findExact(query("occurrences", { page: 99, size: 2 }));
    if (!empty || empty.view !== "occurrences") throw new Error("occurrences expected");
    expect(empty.data.items).toEqual([]);
    expect(empty.data.page).toMatchObject({ number: 99, totalElements: 6, last: true });
  });

  it("rejects malformed internal pagination rather than coercing it", async () => {
    const provider = new FixtureSecManifestAuditProvider();
    await expect(provider.findExact(query("descriptors", { page: -1 })))
      .rejects.toThrow("invalid internal query");
    await expect(provider.findExact(query("descriptors", { size: 101 })))
      .rejects.toThrow("invalid internal query");
  });
});
