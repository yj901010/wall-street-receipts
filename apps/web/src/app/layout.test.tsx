import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const i18nServer = vi.hoisted(() => ({
  getLocale: vi.fn(),
}));

vi.mock("@/lib/i18n/server", () => ({
  getLocale: i18nServer.getLocale,
}));

import RootLayout, { generateMetadata } from "./layout";

describe("RootLayout locale wiring", () => {
  beforeEach(() => {
    i18nServer.getLocale.mockReset();
  });

  it.each([
    ["ko", "시점 일관성을 지킨 애널리스트 콜 증거와 결과 연구."],
    ["en", "Point-in-time analyst call evidence and outcome research."],
  ] as const)("renders exact %s HTML language and metadata", async (locale, description) => {
    i18nServer.getLocale.mockResolvedValue(locale);

    const markup = renderToStaticMarkup(
      await RootLayout({ children: <main data-testid="content">evidence</main> }),
    );

    expect(markup).toContain(`<html lang="${locale}" data-scroll-behavior="smooth">`);
    expect(markup).toContain("<main data-testid=\"content\">evidence</main>");
    await expect(generateMetadata()).resolves.toEqual({
      title: "Wall Street Receipts",
      description,
    });
  });
});
