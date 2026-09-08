import { describe, expect, it } from "vitest";
import { adaptCpi, cpiMonths } from "./cpi";
import { cpiFixture } from "@/test/cpi-fixture";
describe("CPI contract", () => {
  it("preserves missing observations and enumerates calendar gaps", () => {
    const fixture = { ...cpiFixture(), dataMode: "OBSERVED_MONTHLY" };
    expect(adaptCpi(fixture)).toEqual(fixture);
    expect(cpiMonths(adaptCpi(fixture))).toHaveLength(24);
    expect(cpiMonths(adaptCpi(fixture))).toContain("2025-10");
    expect(fixture.series[0].observations[1].index).toBeNull();
  });
  it.each([
    { dataMode: "DEMO" }, { dataMode: "REALTIME" }, { source: "OTHER" }, { sourceUrl: "https://evil.example" },
    { capturedAt: "2026-09-08" }, { servedAt: "2026-09-01T00:00:00Z" }, { seasonality: "SA" },
    { responseSha256: "invalid" }, { releaseTimeStatus: "KNOWN" }, { extra: 1 }, { series: [] },
  ])("rejects unrecognized metadata %j", mutation => {
    expect(() => adaptCpi({ ...cpiFixture(), dataMode: "OBSERVED_MONTHLY", ...mutation })).toThrow("failed validation");
  });
  it.each([{ month: "2026-13" }, { month: "2026-09" }, { index: "0" }, { index: 103.05 }, { index: "NaN" },
    { index: null, yearOverYearPct: "3.1" }, { yearOverYearPct: 3.1 }, { footnotes: ["x".repeat(2067)] }])("rejects invalid rows %j", mutation => {
    const fixture = { ...cpiFixture(), dataMode: "OBSERVED_MONTHLY" };
    Object.assign(fixture.series[0].observations[0], mutation);
    expect(() => adaptCpi(fixture)).toThrow("failed validation");
  });
  it("rejects duplicate months and invented missing-year comparisons", () => {
    const fixture = { ...cpiFixture(), dataMode: "OBSERVED_MONTHLY" };
    fixture.series[0].observations.push(fixture.series[0].observations[0]);
    expect(() => adaptCpi(fixture)).toThrow();
    fixture.series[0].observations = [fixture.series[0].observations[0]];
    expect(() => adaptCpi(fixture)).toThrow();
  });
});
