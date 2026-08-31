import type { SecManifestAuditProvider } from "./sec-manifest-audit-provider";

function assertServerRuntime() {
  if (typeof window !== "undefined") {
    throw new Error("SEC manifest audit provider selection is server-only.");
  }
}

export async function secManifestAuditProvider(): Promise<SecManifestAuditProvider> {
  assertServerRuntime();
  const configuredProvider = process.env.SEC_MANIFEST_AUDIT_PROVIDER ?? "fixture";
  if (configuredProvider === "fixture") {
    const { FixtureSecManifestAuditProvider } = await import(
      "./fixture-sec-manifest-audit-provider"
    );
    return new FixtureSecManifestAuditProvider();
  }
  if (configuredProvider === "api") {
    const baseUrl = process.env.API_BASE_URL;
    if (!baseUrl || baseUrl.trim() === "") {
      throw new Error(
        "API_BASE_URL is required when SEC_MANIFEST_AUDIT_PROVIDER=api.",
      );
    }
    const { ApiSecManifestAuditProvider } = await import(
      "./api-sec-manifest-audit-provider.server"
    );
    return new ApiSecManifestAuditProvider(baseUrl);
  }
  throw new Error(`Unsupported SEC manifest audit provider: ${configuredProvider}`);
}
