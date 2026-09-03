import { describe, expect, it } from "vitest";
import fixture from "./fixtures/sec-manifest-audit-demo.json";
import { adaptSecManifestAuditResource } from "./sec-manifest-audit-adapter";
import type {
  SecManifestAuditQuery,
  SecManifestAuditView,
} from "./sec-manifest-audit-provider";

type MutableJson = Record<string, unknown>;

function copy<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function query(view: SecManifestAuditView): SecManifestAuditQuery {
  return {
    manifestId: fixture.manifestId,
    evaluationAsOf: fixture.evaluationAsOf,
    view,
    page: 0,
    size: 25,
  };
}

function payload(view: SecManifestAuditView): MutableJson {
  return copy(fixture[view]) as MutableJson;
}

function item(document: MutableJson, index = 0): MutableJson {
  if (!Array.isArray(document.items) || typeof document.items[index] !== "object") {
    throw new Error("fixture item expected");
  }
  return document.items[index] as MutableJson;
}

function pageMetadata(document: MutableJson): MutableJson {
  if (typeof document.page !== "object" || document.page === null) {
    throw new Error("fixture page metadata expected");
  }
  return document.page as MutableJson;
}

describe("SEC manifest audit response adapter", () => {
  it("accepts every exact ADR-052 fixture resource without changing canonical tokens", () => {
    const summary = adaptSecManifestAuditResource("summary", payload("summary"), query("summary"));
    const descriptors = adaptSecManifestAuditResource(
      "descriptors",
      payload("descriptors"),
      query("descriptors"),
    );
    const accessions = adaptSecManifestAuditResource(
      "accessions",
      payload("accessions"),
      query("accessions"),
    );
    const occurrences = adaptSecManifestAuditResource(
      "occurrences",
      payload("occurrences"),
      query("occurrences"),
    );

    expect(summary.view).toBe("summary");
    expect(descriptors.view).toBe("descriptors");
    expect(accessions.view).toBe("accessions");
    expect(occurrences.view).toBe("occurrences");
    if (accessions.view !== "accessions") throw new Error("accessions expected");
    expect(accessions.data.items.map((item) => item.comparison)).toEqual([
      "SINGLE_SOURCE_OCCURRENCE",
      "SINGLE_SOURCE_OCCURRENCE",
      "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT",
      "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT",
    ]);
  });

  it("rejects extra fields and an echoed identity that differs from the request", () => {
    const extra = payload("summary");
    extra.latest = true;
    expect(() => adaptSecManifestAuditResource("summary", extra, query("summary")))
      .toThrow("summary field set");

    const wrongIdentity = payload("summary");
    wrongIdentity.manifestId = "b".repeat(64);
    expect(() => adaptSecManifestAuditResource("summary", wrongIdentity, query("summary")))
      .toThrow("exact request identity");
  });

  it("enforces summary totals, domain identities, and microsecond point-in-time ordering", () => {
    const counts = payload("summary");
    counts.distinctAccessionCount = 5;
    expect(() => adaptSecManifestAuditResource("summary", counts, query("summary")))
      .toThrow("summary count invariants");

    const impossibleMinimum = payload("summary");
    impossibleMinimum.sourceOccurrenceCount = 5;
    expect(() => adaptSecManifestAuditResource(
      "summary",
      impossibleMinimum,
      query("summary"),
    )).toThrow("summary count invariants");

    const orphanOccurrence = payload("summary");
    orphanOccurrence.sourceOccurrenceCount = 1;
    orphanOccurrence.distinctAccessionCount = 0;
    orphanOccurrence.singleSourceAccessionCount = 0;
    orphanOccurrence.exactAgreementAccessionCount = 0;
    orphanOccurrence.canonicalConflictAccessionCount = 0;
    expect(() => adaptSecManifestAuditResource(
      "summary",
      orphanOccurrence,
      query("summary"),
    )).toThrow("summary count invariants");

    const zeroCik = payload("summary");
    zeroCik.cik = "0000000000";
    expect(() => adaptSecManifestAuditResource("summary", zeroCik, query("summary")))
      .toThrow("CIK");

    const futureAssembly = payload("summary");
    futureAssembly.assembledAt = "2026-08-25T03:30:00.123457Z";
    expect(() => adaptSecManifestAuditResource("summary", futureAssembly, query("summary")))
      .toThrow("point-in-time ordering");
  });

  it("accepts the exact no-advertised-descriptors coverage and rejects mismatched coverage", () => {
    const noAdvertised = payload("summary");
    noAdvertised.selectionCoverage = "NO_ADVERTISED_DESCRIPTORS";
    noAdvertised.advertisedDescriptorCount = 0;
    noAdvertised.selectedDescriptorCount = 0;
    noAdvertised.omittedDescriptorCount = 0;

    const adapted = adaptSecManifestAuditResource(
      "summary",
      noAdvertised,
      query("summary"),
    );
    expect(adapted.view).toBe("summary");
    if (adapted.view !== "summary") throw new Error("summary expected");
    expect(adapted.data.selectionCoverage).toBe("NO_ADVERTISED_DESCRIPTORS");

    const zeroAdvertisedAsAll = copy(noAdvertised);
    zeroAdvertisedAsAll.selectionCoverage = "ALL_ADVERTISED_DESCRIPTORS_SELECTED";
    expect(() => adaptSecManifestAuditResource(
      "summary",
      zeroAdvertisedAsAll,
      query("summary"),
    )).toThrow("summary count invariants");

    const advertisedAsNone = payload("summary");
    advertisedAsNone.selectionCoverage = "NO_ADVERTISED_DESCRIPTORS";
    expect(() => adaptSecManifestAuditResource(
      "summary",
      advertisedAsNone,
      query("summary"),
    )).toThrow("summary count invariants");
  });

  it("enforces descriptor selection and accession comparison invariants", () => {
    const descriptor = payload("descriptors");
    item(descriptor).selectedSegmentCaptureId = null;
    expect(() => adaptSecManifestAuditResource(
      "descriptors",
      descriptor,
      query("descriptors"),
    )).toThrow("descriptor selection/capture coupling");

    const accession = payload("accessions");
    item(accession, 3).distinctProjectionCount = 1;
    expect(() => adaptSecManifestAuditResource(
      "accessions",
      accession,
      query("accessions"),
    )).toThrow("accession comparison invariants");
  });

  it("enforces bounded descriptor file names, positive counts, and ordered advertised dates", () => {
    const unsafeName = payload("descriptors");
    item(unsafeName).fileName = "../submissions.json";
    expect(() => adaptSecManifestAuditResource(
      "descriptors",
      unsafeName,
      query("descriptors"),
    )).toThrow("descriptor file name");

    const zeroCount = payload("descriptors");
    item(zeroCount).advertisedFilingCount = 0;
    expect(() => adaptSecManifestAuditResource(
      "descriptors",
      zeroCount,
      query("descriptors"),
    )).toThrow("advertised filing count");

    const reversed = payload("descriptors");
    item(reversed).advertisedFilingFrom = "2025-01-01";
    item(reversed).advertisedFilingTo = "2024-01-01";
    expect(() => adaptSecManifestAuditResource(
      "descriptors",
      reversed,
      query("descriptors"),
    )).toThrow("advertised filing date range");
  });

  it("rejects noncanonical SEC document links and occurrences from after the cutoff", () => {
    const wrongHost = payload("occurrences");
    item(wrongHost).primaryDocumentUri = String(item(wrongHost).primaryDocumentUri)
      .replace("https://www.sec.gov/", "https://example.com/");
    expect(() => adaptSecManifestAuditResource(
      "occurrences",
      wrongHost,
      query("occurrences"),
    )).toThrow("canonical SEC document URI");

    for (const uri of [
      "https://www.sec.gov/Archives/edgar/data/not-a-cik/000032019326000001/form10q.htm",
      "https://www.sec.gov/Archives/edgar/data/320193/000032019326000001/form%2F10q.htm",
      "https://www.sec.gov/Archives/edgar/data/320193/000032019326000001/",
    ]) {
      const noncanonical = payload("occurrences");
      item(noncanonical).primaryDocumentUri = uri;
      expect(() => adaptSecManifestAuditResource(
        "occurrences",
        noncanonical,
        query("occurrences"),
      )).toThrow("canonical SEC document URI");
    }

    const future = payload("occurrences");
    item(future).acceptedAt = "2026-08-25T03:30:00.123457Z";
    expect(() => adaptSecManifestAuditResource(
      "occurrences",
      future,
      query("occurrences"),
    )).toThrow("occurrence point-in-time ordering");
  });

  it("rejects page metadata or ordinal order that does not exactly match the request", () => {
    const metadata = payload("descriptors");
    pageMetadata(metadata).number = 1;
    expect(() => adaptSecManifestAuditResource(
      "descriptors",
      metadata,
      query("descriptors"),
    )).toThrow("page identity and bounds");

    const order = payload("descriptors");
    item(order).descriptorOrdinal = 1;
    expect(() => adaptSecManifestAuditResource(
      "descriptors",
      order,
      query("descriptors"),
    )).toThrow("fixed page ordinal ordering");
  });

  it("rejects adapting a payload as a different selected resource", () => {
    expect(() => adaptSecManifestAuditResource(
      "summary",
      payload("summary"),
      query("accessions"),
    )).toThrow("requested view identity");
  });
});
