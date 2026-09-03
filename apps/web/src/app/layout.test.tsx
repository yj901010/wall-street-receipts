import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const i18nServer = vi.hoisted(() => ({
  getLocale: vi.fn(),
}));

vi.mock("@/lib/i18n/server", () => ({
  getLocale: i18nServer.getLocale,
}));

import RootLayout, { generateMetadata, readSiteOrigin } from "./layout";

describe("RootLayout locale wiring", () => {
  beforeEach(() => {
    i18nServer.getLocale.mockReset();
    delete process.env.SITE_ORIGIN;
  });

  afterEach(() => {
    delete process.env.SITE_ORIGIN;
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
    const metadata = await generateMetadata();
    expect(metadata).toMatchObject({
      title: "Wall Street Receipts",
      description,
      metadataBase: new URL("http://localhost:3000"),
      openGraph: {
        type: "website",
        locale: locale === "ko" ? "ko_KR" : "en_US",
        siteName: "Wall Street Receipts",
        title: "Wall Street Receipts",
        description,
        images: [{
          url: "/og.png",
          width: 1731,
          height: 909,
        }],
      },
      twitter: {
        card: "summary_large_image",
        title: "Wall Street Receipts",
        description,
        images: ["/og.png"],
      },
    });
  });

  it("accepts only an exact public HTTP(S) origin for absolute social metadata", () => {
    process.env.SITE_ORIGIN = "https://stocks.example.kr";
    expect(readSiteOrigin()).toEqual(new URL("https://stocks.example.kr"));

    for (const invalid of [
      "ftp://stocks.example.kr",
      "https://user:secret@stocks.example.kr",
      "https://stocks.example.kr/path",
      "https://stocks.example.kr?",
      "https://stocks.example.kr?preview=true",
      "https://stocks.example.kr#",
      " https://stocks.example.kr",
    ]) {
      process.env.SITE_ORIGIN = invalid;
      expect(() => readSiteOrigin()).toThrow("SITE_ORIGIN");
    }
  });
});
