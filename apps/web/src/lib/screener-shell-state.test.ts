import { describe, expect, it } from "vitest";
import { SCREENER_SHELL_STATE } from "./screener-shell-state";

describe("SCREENER_SHELL_STATE", () => {
  it("is the frozen exact five-field application policy without evidence or output fields", () => {
    expect(Object.keys(SCREENER_SHELL_STATE)).toEqual([
      "dataMode",
      "scope",
      "status",
      "reasonCode",
      "missingDisplay",
    ]);
    expect(SCREENER_SHELL_STATE).toEqual({
      dataMode: "DEMO",
      scope: "HISTORICAL_EQUITY_SCREENING",
      status: "P8_DEFERRED",
      reasonCode: "NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG",
      missingDisplay: "NA",
    });
    expect(Object.isFrozen(SCREENER_SHELL_STATE)).toBe(true);
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("schemaVersion");
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("generatedAt");
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("source");
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("query");
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("filters");
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("results");
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("count");
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("sort");
    expect(SCREENER_SHELL_STATE).not.toHaveProperty("metric");
  });
});
