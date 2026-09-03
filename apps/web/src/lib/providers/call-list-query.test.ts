import { describe, expect, it } from "vitest";
import { effectiveCallListQuery } from "./call-list-adapter";
import { parseCallListSearchParams } from "./call-list-query";

describe("call list raw search parser", () => {
  it("keeps absent URL controls absent and resolves exact effective defaults once", () => {
    const parsed = parseCallListSearchParams({});
    expect(parsed.values).toEqual({
      assetId: "",
      ticker: "",
      institutionId: "",
      analystId: "",
      direction: "",
      status: "",
      from: "",
      to: "",
      size: "",
      sort: "",
      order: "",
    });
    expect(effectiveCallListQuery(parsed.query)).toMatchObject({
      dataMode: "DEMO",
      page: 0,
      size: 25,
      sort: "eventTime",
      order: "desc",
    });
  });

  it("preserves valid URL filters and converts the selected KST dates to UTC API bounds", () => {
    expect(parseCallListSearchParams({
      assetId: "asset-nvda",
      ticker: "nvda",
      institutionId: "inst-gs",
      analystId: "analyst-demo-b",
      direction: "BULLISH",
      status: "ACTIVE",
      dataMode: "DEMO",
      from: "2026-08-10",
      to: "2026-08-11",
      page: "0",
      size: "1",
      sort: "capturedAt",
      order: "asc",
    })).toMatchObject({
      query: {
        assetId: "asset-nvda",
        ticker: "nvda",
        institutionId: "inst-gs",
        analystId: "analyst-demo-b",
        direction: "BULLISH",
        status: "ACTIVE",
        dataMode: "DEMO",
        from: "2026-08-09T15:00:00.000Z",
        to: "2026-08-11T15:00:00.000Z",
        page: 0,
        size: 1,
        sort: "capturedAt",
        order: "asc",
      },
      values: { to: "2026-08-11", size: "1" },
    });
  });

  it("accepts a real leap day and rejects an inverted exclusive date range", () => {
    expect(parseCallListSearchParams({ from: "2028-02-29" }).query.from)
      .toBe("2028-02-28T15:00:00.000Z");
    expect(() => parseCallListSearchParams({ from: "2026-08-12", to: "2026-08-11" }))
      .toThrow(/exclusive upper bound must follow/);
    expect(parseCallListSearchParams({ from: "2026-08-11", to: "2026-08-11" }).query)
      .toMatchObject({
        from: "2026-08-10T15:00:00.000Z",
        to: "2026-08-11T15:00:00.000Z",
      });
  });

  it("accepts exact page and size boundaries and rejects overflow or non-integers", () => {
    expect(parseCallListSearchParams({ page: "2147483647", size: "1" }).query)
      .toMatchObject({ page: 2_147_483_647, size: 1 });
    expect(parseCallListSearchParams({ size: "100" }).query.size).toBe(100);
    for (const raw of [
      { page: "2147483648" },
      { page: "-1" },
      { page: "1.5" },
      { size: "0" },
      { size: "1.5" },
      { size: "101" },
    ]) {
      expect(() => parseCallListSearchParams(raw)).toThrow(/Invalid calls query parameter/);
    }
  });

  it("omits exact empty optional filters but rejects present-empty controls", () => {
    expect(parseCallListSearchParams({
      assetId: "",
      ticker: "",
      institutionId: "",
      analystId: "",
      direction: "",
      status: "",
      dataMode: "",
      from: "",
      to: "",
    }).query).toMatchObject({
      assetId: undefined,
      ticker: undefined,
      institutionId: undefined,
      analystId: undefined,
      direction: undefined,
      status: undefined,
      from: undefined,
      to: undefined,
      dataMode: "DEMO",
    });
    for (const field of ["page", "size", "sort", "order"]) {
      expect(() => parseCallListSearchParams({ [field]: "" })).toThrow(/empty value/);
    }
  });

  it.each([
    ["duplicate", { assetId: ["asset-spx", "asset-nvda"] }],
    ["whitespace", { assetId: " asset-spx" }],
    ["unsupported", { unknown: "value" }],
    ["mode", { dataMode: "REALTIME" }],
    ["identifier", { institutionId: "invalid/id" }],
    ["ticker", { ticker: "NVDA?" }],
    ["direction enum case", { direction: "bullish" }],
    ["status enum case", { status: "active" }],
    ["sort enum case", { sort: "eventtime" }],
    ["order enum case", { order: "DESC" }],
    ["date", { from: "2026-02-30" }],
    ["year zero", { from: "0000-01-01" }],
    ["exclusive date overflow", { to: "9999-12-31" }],
    ["page", { page: "01" }],
    ["size", { size: "101" }],
  ])("rejects invalid raw %s input without silently broadening the query", (_label, raw) => {
    expect(() => parseCallListSearchParams(raw)).toThrow(/Invalid calls query parameter/);
  });
});
