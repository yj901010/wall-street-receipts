import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { secManifestAuditProvider } from "./sec-manifest-audit-provider.server";

describe("secManifestAuditProvider", () => {
  beforeEach(() => {
    vi.stubGlobal("window", undefined);
    delete process.env.SEC_MANIFEST_AUDIT_PROVIDER;
    delete process.env.API_BASE_URL;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.SEC_MANIFEST_AUDIT_PROVIDER;
    delete process.env.API_BASE_URL;
  });

  it("defaults only an absent selector to the exact Java-generated DEMO provider", async () => {
    await expect(secManifestAuditProvider()).resolves.toMatchObject({
      mode: "fixture",
    });
  });

  it("selects API mode without evaluating the fixture module", async () => {
    vi.resetModules();
    vi.doMock("./fixture-sec-manifest-audit-provider", () => {
      throw new Error("fixture module must not be evaluated in API mode");
    });
    process.env.SEC_MANIFEST_AUDIT_PROVIDER = "api";
    process.env.API_BASE_URL = "http://api.example.test";
    try {
      const isolatedFactory = await import("./sec-manifest-audit-provider.server");
      await expect(isolatedFactory.secManifestAuditProvider()).resolves.toMatchObject({
        mode: "api",
      });
    } finally {
      vi.doUnmock("./fixture-sec-manifest-audit-provider");
      vi.resetModules();
    }
  });

  it.each(["", "Fixture", " fixture", "fixture ", "latest", "demo"])(
    "rejects unsupported selector %j without fallback",
    async (selector) => {
      process.env.SEC_MANIFEST_AUDIT_PROVIDER = selector;
      await expect(secManifestAuditProvider()).rejects.toThrow(
        `Unsupported SEC manifest audit provider: ${selector}`,
      );
    },
  );

  it("requires the private API base URL only in exact API mode", async () => {
    process.env.SEC_MANIFEST_AUDIT_PROVIDER = "api";
    await expect(secManifestAuditProvider()).rejects.toThrow("API_BASE_URL is required");
    process.env.API_BASE_URL = "   ";
    await expect(secManifestAuditProvider()).rejects.toThrow("API_BASE_URL is required");
  });

  it("rejects provider selection in a browser runtime", async () => {
    vi.stubGlobal("window", {});
    await expect(secManifestAuditProvider()).rejects.toThrow("server-only");
  });
});
