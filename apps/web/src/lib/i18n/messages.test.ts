import { describe, expect, it } from "vitest";
import { COMMON_MESSAGES, NAVIGATION_ITEMS, getCommonMessages } from "./messages";

function keyShape(value: unknown): unknown {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return typeof value;
  }

  return Object.fromEntries(
    Object.entries(value)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, nested]) => [key, keyShape(nested)]),
  );
}

describe("common locale messages", () => {
  it("keeps Korean and English catalogs in exact recursive parity", () => {
    expect(keyShape(COMMON_MESSAGES.ko)).toEqual(keyShape(COMMON_MESSAGES.en));
    expect(Object.keys(COMMON_MESSAGES)).toEqual(["ko", "en"]);
  });

  it("covers the exact primary navigation order in both locales", () => {
    expect(NAVIGATION_ITEMS).toEqual([
      "dashboard",
      "market",
      "calls",
      "institutions",
      "analysts",
      "maps",
      "screener",
      "methodology",
      "secEvidence",
    ]);
    expect(Object.keys(COMMON_MESSAGES.ko.navigation)).toEqual(NAVIGATION_ITEMS);
    expect(Object.keys(COMMON_MESSAGES.en.navigation)).toEqual(NAVIGATION_ITEMS);
  });

  it("returns typed common messages without translating product evidence tokens", () => {
    expect(getCommonMessages("ko").navigation.dashboard).toBe("대시보드");
    expect(getCommonMessages("en").navigation.dashboard).toBe("Dashboard");
    expect(JSON.stringify(COMMON_MESSAGES)).not.toMatch(/DEMO|provenance|source path|NA/);
  });
});
