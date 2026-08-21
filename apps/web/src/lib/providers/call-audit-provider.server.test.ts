import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiCallAuditProvider } from "./api-call-audit-provider.server";
import { callAuditProvider } from "./call-audit-provider.server";
import { FixtureCallAuditProvider } from "./fixture-call-audit-provider";

describe("callAuditProvider", () => {
  beforeEach(() => {
    vi.stubGlobal("window", undefined);
    delete process.env.CALL_AUDIT_PROVIDER;
    delete process.env.API_BASE_URL;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.CALL_AUDIT_PROVIDER;
    delete process.env.API_BASE_URL;
  });

  it("defaults only a missing selector to the coherent fixture aggregate", () => {
    expect(callAuditProvider()).toBeInstanceOf(FixtureCallAuditProvider);
  });

  it("selects the coherent fixture aggregate for the exact fixture selector", () => {
    process.env.CALL_AUDIT_PROVIDER = "fixture";
    expect(callAuditProvider()).toBeInstanceOf(FixtureCallAuditProvider);
  });

  it.each(["API", "FiXtUrE", " fixture", "fixture ", "", "revisions"])(
    "rejects unsupported selector %j without normalization or fallback",
    (selector) => {
      process.env.CALL_AUDIT_PROVIDER = selector;
      expect(() => callAuditProvider()).toThrow(`Unsupported call audit provider: ${selector}`);
    },
  );

  it("requires the private API base URL exactly in api mode", () => {
    process.env.CALL_AUDIT_PROVIDER = "api";
    expect(() => callAuditProvider()).toThrow(/API_BASE_URL is required/);
    process.env.API_BASE_URL = "   ";
    expect(() => callAuditProvider()).toThrow(/API_BASE_URL is required/);
  });

  it("selects the API aggregate without calling the fixture provider", () => {
    const fixture = vi.spyOn(FixtureCallAuditProvider.prototype, "findById");
    process.env.CALL_AUDIT_PROVIDER = "api";
    process.env.API_BASE_URL = "http://api.example.test";
    expect(callAuditProvider()).toBeInstanceOf(ApiCallAuditProvider);
    expect(fixture).not.toHaveBeenCalled();
    fixture.mockRestore();
  });

  it("rejects server-provider selection in a browser runtime", () => {
    vi.stubGlobal("window", {});
    expect(() => callAuditProvider()).toThrow(/server-only/);
  });
});
