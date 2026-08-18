import { describe, expect, it } from "vitest";
import { formatMoney } from "./format-money";

describe("formatMoney", () => {
  it("does not invent a currency for a numeric value", () => {
    expect(formatMoney(235, null)).toBe("NA");
    expect(formatMoney(null, "USD")).toBe("NA");
    expect(formatMoney(235, "USD")).toBe("$235.00");
  });
});
