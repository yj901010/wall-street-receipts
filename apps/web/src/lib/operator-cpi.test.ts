import { describe, expect, it } from "vitest";
import { adaptAttempts, readAttemptJson } from "./operator-cpi";
import { operatorFixture } from "../test/operator-cpi-fixture";

describe("closed CPI operator evidence", () => {
  it("keeps UNKNOWN, KST rollover and explicit selected identity", () => {
    const raw = operatorFixture();
    expect(adaptAttempts(raw).attempts[0].terminal).toBeNull();
    expect(adaptAttempts({ metadata: raw.metadata, attempt: raw.attempts[0] }, raw.attempts[0].attemptId).selected).toBe(true);
    expect(() => adaptAttempts({ metadata: raw.metadata, attempt: raw.attempts[0] }, "00000000-0000-0000-0000-000000000002")).toThrow();
  });
  it("supports empty but never invents hasMore, total or DEMO fallback", () => {
    const raw = operatorFixture(); raw.attempts = [];
    expect(adaptAttempts(raw).attempts).toEqual([]);
    raw.hasMore = true; expect(() => adaptAttempts(raw)).toThrow();
  });
  it("accepts API observation nanoseconds but requires microsecond persisted evidence", () => {
    const raw = operatorFixture();
    raw.metadata.observedAtKst = "2026-09-09T00:01:00.123456789+09:00";
    expect(adaptAttempts(raw).observedAtKst).toBe(raw.metadata.observedAtKst);
    raw.attempts[0].startedAtKst = "2026-09-08T23:59:59.123456789+09:00";
    expect(() => adaptAttempts(raw)).toThrow();
  });
  it.each([
    (r: ReturnType<typeof operatorFixture>) => { r.metadata.dataMode = "OBSERVED"; },
    (r: ReturnType<typeof operatorFixture>) => { r.metadata.limitations.pop(); },
    (r: ReturnType<typeof operatorFixture>) => { r.attempts[0].startedAtKst = "2026-02-30T23:59:59+09:00"; },
    (r: ReturnType<typeof operatorFixture>) => { r.attempts[0].startedAtKst = "2026-09-10T00:00:00+09:00"; },
    (r: ReturnType<typeof operatorFixture>) => { r.attempts[0].startedAtKst = "2026-09-08T12:00:00Z"; },
    (r: ReturnType<typeof operatorFixture>) => { r.attempts[0].status = "RUNNING"; },
    (r: ReturnType<typeof operatorFixture>) => { r.attempts.push(r.attempts[0]); },
    (r: ReturnType<typeof operatorFixture>) => { r.attempts = Array(21).fill(r.attempts[0]); },
  ])("rejects invalid/unbounded/inferred evidence %#", mutate => {
    const raw = operatorFixture(); mutate(raw); expect(() => adaptAttempts(raw)).toThrow();
  });
  it("validates all terminal state shapes and gate/receipt chronology", () => {
    for (const status of ["SAVED", "SKIPPED", "FAILED", "RATE_LIMITED"]) {
      const raw = operatorFixture(); const row = raw.attempts[0];
      row.status = status; row.gatePermitted = status !== "SKIPPED";
      row.terminal = { completedAtKst: "2026-09-09T00:00:00+09:00", captureId: status === "SAVED" ? row.attemptId : null,
        capturedAtKst: status === "SAVED" ? "2026-09-08T23:59:59.999999+09:00" : null,
        failureCode: status === "FAILED" ? "FETCH" : null,
        retryNotBeforeKst: status === "RATE_LIMITED" ? "2026-09-09T00:05:00+09:00" : null };
      expect(adaptAttempts(raw).attempts[0].status).toBe(status);
      row.gatePermitted = !row.gatePermitted; expect(() => adaptAttempts(raw)).toThrow();
    }
  });
  it("preserves sub-millisecond ordering and rejects unknown fields", () => {
    const raw = operatorFixture();
    raw.attempts.push({ ...raw.attempts[0], attemptId: "00000000-0000-0000-0000-000000000002", startedAtKst: "2026-09-08T23:59:59.123455+09:00" });
    expect(adaptAttempts(raw).attempts).toHaveLength(2);
    raw.attempts.reverse(); expect(() => adaptAttempts(raw)).toThrow();
    expect(() => adaptAttempts({ ...operatorFixture(), token: "should-not-pass" })).toThrow();
  });
  it("bounds decoded body and refuses bad encoding/type", async () => {
    await expect(readAttemptJson(Response.json(operatorFixture()))).resolves.toHaveProperty("limit", 20);
    await expect(readAttemptJson(new Response("x".repeat(65_537), { headers: { "Content-Type": "application/json" } }))).rejects.toThrow();
    await expect(readAttemptJson(new Response("{}"))).rejects.toThrow();
    await expect(readAttemptJson(new Response(new Uint8Array([255]), { headers: { "Content-Type": "application/json" } }))).rejects.toThrow();
  });
});
