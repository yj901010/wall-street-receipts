import type { CallAuditProvider } from "./call-audit-provider";
import { ApiCallAuditProvider } from "./api-call-audit-provider.server";
import { FixtureCallAuditProvider } from "./fixture-call-audit-provider";

function assertServerRuntime() {
  if (typeof window !== "undefined") {
    throw new Error("Call audit provider selection is server-only.");
  }
}

export function callAuditProvider(): CallAuditProvider {
  assertServerRuntime();
  const configuredProvider = process.env.CALL_AUDIT_PROVIDER ?? "fixture";
  if (configuredProvider === "fixture") return new FixtureCallAuditProvider();
  if (configuredProvider === "api") {
    const baseUrl = process.env.API_BASE_URL;
    if (!baseUrl || baseUrl.trim() === "") {
      throw new Error("API_BASE_URL is required when CALL_AUDIT_PROVIDER=api.");
    }
    return new ApiCallAuditProvider(baseUrl);
  }
  throw new Error(`Unsupported call audit provider: ${configuredProvider}`);
}
