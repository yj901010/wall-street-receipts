import { ApiCallListProvider } from "./api-call-list-provider.server";
import type { CallListProvider } from "./call-list-provider";
import { FixtureCallListProvider } from "./fixture-call-list-provider";

function assertServerRuntime() {
  if (typeof window !== "undefined") {
    throw new Error("Call list provider selection is server-only.");
  }
}

export function callListProvider(): CallListProvider {
  assertServerRuntime();
  const configuredProvider = process.env.CALL_AUDIT_PROVIDER ?? "fixture";
  if (configuredProvider === "fixture") return new FixtureCallListProvider();
  if (configuredProvider === "api") {
    const baseUrl = process.env.API_BASE_URL;
    if (!baseUrl || baseUrl.trim() === "") {
      throw new Error("API_BASE_URL is required when CALL_AUDIT_PROVIDER=api.");
    }
    return new ApiCallListProvider(baseUrl);
  }
  throw new Error(`Unsupported call list provider: ${configuredProvider}`);
}
