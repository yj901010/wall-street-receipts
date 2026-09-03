import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiCallListProvider } from "./api-call-list-provider.server";
import { callListProvider } from "./call-list-provider.server";
import { FixtureCallListProvider } from "./fixture-call-list-provider";

describe("callListProvider", () => {
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

  it("defaults only a missing shared selector to fixture mode", () => {
    expect(callListProvider()).toBeInstanceOf(FixtureCallListProvider);
  });

  it("selects fixture mode for the exact shared fixture selector", () => {
    process.env.CALL_AUDIT_PROVIDER = "fixture";
    expect(callListProvider()).toBeInstanceOf(FixtureCallListProvider);
  });

  it("selects API mode from the same exact selector used by call detail", () => {
    process.env.CALL_AUDIT_PROVIDER = "api";
    process.env.API_BASE_URL = "http://api.example.test";
    expect(callListProvider()).toBeInstanceOf(ApiCallListProvider);
  });

  it.each(["API", "FiXtUrE", " fixture", "fixture ", "", "list"])(
    "rejects unsupported selector %j without trimming, normalization, or fallback",
    (selector) => {
      process.env.CALL_AUDIT_PROVIDER = selector;
      expect(() => callListProvider()).toThrow(`Unsupported call list provider: ${selector}`);
    },
  );

  it("requires the private API base URL in api mode", () => {
    process.env.CALL_AUDIT_PROVIDER = "api";
    expect(() => callListProvider()).toThrow(/API_BASE_URL is required/);
    process.env.API_BASE_URL = "   ";
    expect(() => callListProvider()).toThrow(/API_BASE_URL is required/);
  });

  it("rejects server provider selection in a browser runtime", () => {
    vi.stubGlobal("window", {});
    expect(() => callListProvider()).toThrow(/server-only/);
  });
});
