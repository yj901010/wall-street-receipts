import { beforeEach, describe, expect, it, vi } from "vitest";

const headers = vi.hoisted(() => ({
  cookies: vi.fn(),
}));

vi.mock("next/headers", () => ({
  cookies: headers.cookies,
}));

import { LOCALE_COOKIE_NAME } from "./config";
import { getLocale, getServerCommonMessages } from "./server";

describe("server locale reader", () => {
  beforeEach(() => {
    headers.cookies.mockReset();
  });

  it.each([
    [undefined, "ko"],
    ["fr", "ko"],
    ["KO", "ko"],
    ["ko", "ko"],
    ["en", "en"],
  ])("maps cookie value %p to %s", async (cookieValue, expected) => {
    const get = vi.fn((name: string) =>
      name === LOCALE_COOKIE_NAME && cookieValue !== undefined
        ? { name, value: cookieValue }
        : undefined,
    );
    headers.cookies.mockResolvedValue({ get });

    await expect(getLocale()).resolves.toBe(expected);
    expect(get).toHaveBeenCalledWith("wsr_locale");
  });

  it("returns the common catalog selected by the request cookie", async () => {
    headers.cookies.mockResolvedValue({
      get: () => ({ name: LOCALE_COOKIE_NAME, value: "en" }),
    });

    await expect(getServerCommonMessages()).resolves.toMatchObject({
      navigation: { dashboard: "Dashboard" },
    });
  });
});
