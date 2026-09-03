import { describe, expect, it } from "vitest";
import {
  formatKstInstant,
  kstCalendarDateStartUtc,
  nextKstCalendarDateStartUtc,
  SITE_TIME_ZONE,
} from "./kst-time";

describe("KST site time", () => {
  it("uses the explicit IANA zone and crosses UTC day boundaries deterministically", () => {
    expect(SITE_TIME_ZONE).toBe("Asia/Seoul");
    expect(formatKstInstant("2026-08-18T00:00:00Z"))
      .toBe("2026-08-18 09:00:00 KST");
    expect(formatKstInstant("2026-08-18T20:30:00Z"))
      .toBe("2026-08-19 05:30:00 KST");
    expect(formatKstInstant("2026-12-31T18:00:00Z"))
      .toBe("2027-01-01 03:00:00 KST");
  });

  it.each([
    ["2026-08-25T03:30:00.1Z", "2026-08-25 12:30:00.1 KST"],
    ["2026-08-25T03:30:00.123Z", "2026-08-25 12:30:00.123 KST"],
    ["2026-08-25T03:30:00.123456Z", "2026-08-25 12:30:00.123456 KST"],
    ["2026-08-25T12:30:00.000000001+09:00", "2026-08-25 12:30:00.000000001 KST"],
  ])("preserves observed fractional precision for %s", (value, expected) => {
    expect(formatKstInstant(value)).toBe(expected);
  });

  it("honors an observed RFC 3339 offset instead of relabeling local clock text", () => {
    expect(formatKstInstant("2026-08-25T03:30:00-04:00"))
      .toBe("2026-08-25 16:30:00 KST");
  });

  it("keeps supported early years zero-padded to the four-digit display contract", () => {
    expect(formatKstInstant("0999-01-01T00:00:00Z")).toMatch(/^0999-/);
  });

  it.each([
    "0000-01-01T00:00:00Z",
    "0001-01-01T00:00:00+18:00",
    "2026-02-29T00:00:00Z",
    "2026-08-25 03:30:00Z",
    "2026-08-25T03:30:00+19:00",
    "9999-12-31T23:59:59Z",
    "not-a-time",
  ])("rejects an invalid observed instant: %s", (value) => {
    expect(() => formatKstInstant(value)).toThrow(/noncanonical RFC 3339 instant/i);
  });

  it("maps a selected Korean day to inclusive/exclusive UTC API bounds", () => {
    expect(kstCalendarDateStartUtc("2026-08-11"))
      .toBe("2026-08-10T15:00:00.000Z");
    expect(nextKstCalendarDateStartUtc("2026-08-11"))
      .toBe("2026-08-11T15:00:00.000Z");
    expect(nextKstCalendarDateStartUtc("2028-02-28"))
      .toBe("2028-02-28T15:00:00.000Z");
    expect(nextKstCalendarDateStartUtc("2026-12-31"))
      .toBe("2026-12-31T15:00:00.000Z");
  });

  it.each(["2026-02-29", "0000-01-01", "2026-8-1"])(
    "rejects an invalid Korean calendar date: %s",
    (value) => {
      expect(() => kstCalendarDateStartUtc(value)).toThrow(/invalid KST calendar date/i);
    },
  );
});
