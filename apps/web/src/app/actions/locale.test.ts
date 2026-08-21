import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const headers = vi.hoisted(() => ({
  cookies: vi.fn(),
  set: vi.fn(),
}));

vi.mock("next/headers", () => ({
  cookies: headers.cookies,
}));

import { setLocaleAction } from "./locale";

function formData(value?: FormDataEntryValue): FormData {
  const data = new FormData();
  if (value !== undefined) {
    data.set("locale", value);
  }
  return data;
}

describe("setLocaleAction", () => {
  beforeEach(() => {
    headers.cookies.mockReset();
    headers.set.mockReset();
    headers.cookies.mockResolvedValue({ set: headers.set });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it.each(["ko", "en"])("persists %s with the exact non-production cookie contract", async (locale) => {
    vi.stubEnv("NODE_ENV", "test");

    await setLocaleAction(formData(locale));

    expect(headers.set).toHaveBeenCalledOnce();
    expect(headers.set).toHaveBeenCalledWith("wsr_locale", locale, {
      httpOnly: true,
      maxAge: 31_536_000,
      path: "/",
      sameSite: "lax",
      secure: false,
    });
  });

  it("marks the persisted preference Secure in production", async () => {
    vi.stubEnv("NODE_ENV", "production");

    await setLocaleAction(formData("en"));

    expect(headers.set).toHaveBeenCalledWith(
      "wsr_locale",
      "en",
      expect.objectContaining({ secure: true }),
    );
  });

  it("rejects a missing locale field before accessing the cookie store", async () => {
    await expect(setLocaleAction(formData())).rejects.toThrow(/exactly one locale/i);
    expect(headers.cookies).not.toHaveBeenCalled();
    expect(headers.set).not.toHaveBeenCalled();
  });

  it.each(["", "fr", "KO", new File([], "locale")])(
    "rejects unsupported input %p before accessing the cookie store",
    async (value) => {
      await expect(setLocaleAction(formData(value))).rejects.toThrow(/exactly ko or en/i);
      expect(headers.cookies).not.toHaveBeenCalled();
      expect(headers.set).not.toHaveBeenCalled();
    },
  );

  it("rejects duplicate locale fields before accessing the cookie store", async () => {
    const duplicate = formData("en");
    duplicate.append("locale", "ko");

    await expect(setLocaleAction(duplicate)).rejects.toThrow(/exactly one locale/i);
    expect(headers.cookies).not.toHaveBeenCalled();
    expect(headers.set).not.toHaveBeenCalled();
  });
});
