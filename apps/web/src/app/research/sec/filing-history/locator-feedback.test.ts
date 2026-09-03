import { describe, expect, it } from "vitest";
import { parseSecManifestAuditRoute } from "@/lib/providers/sec-manifest-audit-query";
import { LOCATOR_INPUT_LIMIT, locatorFeedback } from "./locator-feedback";

const ID = "a".repeat(64);
const CUTOFF = "2026-08-25T03:30:00.123456Z";
const valid = { manifestId: ID, evaluationAsOf: CUTOFF };

describe("SEC locator feedback (presentation only)", () => {
  it("leaves an untouched locator empty without reporting missing evidence", () => {
    expect(locatorFeedback(null)).toEqual({
      manifestId: { value: "", error: null },
      evaluationAsOf: { value: "", error: null },
    });
  });

  it.each([undefined, ""])("reports a missing input without selecting a default: %s", (value) => {
    expect(locatorFeedback({ manifestId: value, evaluationAsOf: value })).toEqual({
      manifestId: { value: "", error: "required" },
      evaluationAsOf: { value: "", error: "required" },
    });
  });

  it.each([[], [ID], [ID, ID], [ID, "b".repeat(64)]].map((value) => ({ value })))(
    "does not choose any array value: $value", ({ value }) => {
      const feedback = locatorFeedback({ manifestId: value, evaluationAsOf: value });
      expect(feedback.manifestId).toEqual({ value: "", error: "duplicate" });
      expect(feedback.evaluationAsOf).toEqual({ value: "", error: "duplicate" });
    },
  );

  it.each(["A".repeat(64), " a", "a ", '<script>alert("x")</script>']) (
    "retains printable invalid ID bytes without normalization: %s", (value) => {
      expect(locatorFeedback({ ...valid, manifestId: value }).manifestId)
        .toEqual({ value, error: "format" });
    },
  );

  it.each([
    "2026-02-29T03:30:00Z", "2026-04-31T03:30:00Z", "2026-08-25T24:00:00Z",
    "2026-08-25T03:30:00.1234567Z", "2026-08-25T12:30:00+09:00",
    "2026-08-25T03:30:00Z ", " 2026-08-25T03:30:00Z",
  ])("retains invalid cutoff bytes and uses the existing strict calendar validator: %s", (value) => {
    const raw = { ...valid, evaluationAsOf: value };
    expect(locatorFeedback(raw).evaluationAsOf).toEqual({ value, error: "format" });
    expect(parseSecManifestAuditRoute(raw).kind).toBe("invalid");
  });

  it.each(["x".repeat(65), "line\rbreak", "line\nbreak", "tab\tvalue", "null\0value",
    "delete\u007f", "control\u0085", "한글", "bidi\u202e", "\ud800"])(
    "discards the whole unsupported value instead of stripping or truncating it: %j", (value) => {
      const feedback = locatorFeedback({ manifestId: value, evaluationAsOf: value });
      expect(feedback.manifestId).toEqual({ value: "", error: "notRetained" });
      expect(feedback.evaluationAsOf).toEqual({ value: "", error: "notRetained" });
    },
  );

  it("keeps the explicit 64-character bound inclusive", () => {
    expect(LOCATOR_INPUT_LIMIT).toBe(64);
    expect(locatorFeedback(valid).manifestId).toEqual({ value: ID, error: null });
    expect(locatorFeedback({ ...valid, evaluationAsOf: "x".repeat(64) }).evaluationAsOf)
      .toEqual({ value: "x".repeat(64), error: "format" });
  });

  it.each(["2024-02-29T00:00:00Z", "2026-08-25T03:30:00.100000Z", CUTOFF])(
    "preserves valid time precision without rounding: %s", (evaluationAsOf) => {
      expect(locatorFeedback({ ...valid, evaluationAsOf }).evaluationAsOf)
        .toEqual({ value: evaluationAsOf, error: null });
    },
  );

  it.each([{ ticker: "NVDA" }, { view: "latest" }, { page: "1" }, { size: "025" }])(
    "does not authorize a rejected URL or label valid fields invalid: %j", (extra) => {
      const raw = Object.freeze({ ...valid, ...extra });
      expect(parseSecManifestAuditRoute(raw).kind).toBe("invalid");
      expect(locatorFeedback(raw)).toEqual({
        manifestId: { value: ID, error: null },
        evaluationAsOf: { value: CUTOFF, error: null },
      });
      expect(parseSecManifestAuditRoute(raw).kind).toBe("invalid");
    },
  );
});
