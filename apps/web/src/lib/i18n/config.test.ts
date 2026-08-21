import { describe, expect, it } from "vitest";
import {
  DEFAULT_LOCALE,
  LOCALE_COOKIE_MAX_AGE_SECONDS,
  LOCALE_COOKIE_NAME,
  LOCALE_COOKIE_OPTIONS,
  SUPPORTED_LOCALES,
  isLocale,
  parseLocale,
  requireLocale,
} from "./config";

describe("locale configuration", () => {
  it("locks the supported locale surface and Korean default", () => {
    expect(SUPPORTED_LOCALES).toEqual(["ko", "en"]);
    expect(DEFAULT_LOCALE).toBe("ko");
    expect(LOCALE_COOKIE_NAME).toBe("wsr_locale");
    expect(LOCALE_COOKIE_MAX_AGE_SECONDS).toBe(31_536_000);
    expect(LOCALE_COOKIE_OPTIONS).toEqual({
      httpOnly: true,
      maxAge: 31_536_000,
      path: "/",
      sameSite: "lax",
    });
  });

  it.each([
    ["ko", true],
    ["en", true],
    ["KO", false],
    ["en-US", false],
    [" ko", false],
    ["", false],
    [null, false],
    [undefined, false],
  ])("validates %p without coercion", (value, expected) => {
    expect(isLocale(value)).toBe(expected);
  });

  it.each([undefined, null, "", "fr", "KO", " en ", new File([], "locale")])(
    "falls back to Korean for an invalid persisted value %p",
    (value) => {
      expect(parseLocale(value)).toBe("ko");
    },
  );

  it("rejects invalid action input instead of silently changing it to Korean", () => {
    expect(requireLocale("en")).toBe("en");
    expect(() => requireLocale("fr")).toThrow(/exactly ko or en/i);
  });
});
