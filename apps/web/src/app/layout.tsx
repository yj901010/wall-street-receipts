import type { Metadata } from "next";
import type { ReactNode } from "react";
import { LocaleProvider } from "@/components/locale-provider";
import { getCommonMessages } from "@/lib/i18n/messages";
import { getLocale } from "@/lib/i18n/server";
import "@/styles/tokens.css";
import "./globals.css";

export function readSiteOrigin(): URL {
  const raw = process.env.SITE_ORIGIN ?? "http://localhost:3000";
  let origin: URL;
  try {
    origin = new URL(raw);
  } catch {
    throw new Error("SITE_ORIGIN must be an absolute HTTP(S) origin.");
  }
  if (
    raw.trim() !== raw ||
    (origin.protocol !== "http:" && origin.protocol !== "https:") ||
    origin.hostname === "" ||
    origin.username !== "" ||
    origin.password !== "" ||
    origin.pathname !== "/" ||
    raw.includes("?") ||
    raw.includes("#") ||
    origin.search !== "" ||
    origin.hash !== ""
  ) {
    throw new Error(
      "SITE_ORIGIN must be an absolute HTTP(S) origin without credentials, path, query, or fragment.",
    );
  }
  return origin;
}

export async function generateMetadata(): Promise<Metadata> {
  const locale = await getLocale();
  const messages = getCommonMessages(locale);
  const title = messages.metadata.title;
  const description = messages.metadata.description;

  return {
    metadataBase: readSiteOrigin(),
    title,
    description,
    openGraph: {
      type: "website",
      locale: locale === "ko" ? "ko_KR" : "en_US",
      siteName: "Wall Street Receipts",
      title,
      description,
      images: [{
        url: "/og.png",
        width: 1731,
        height: 909,
        alt: locale === "ko"
          ? "Wall Street Receipts 시점 기준 금융 데이터 증거"
          : "Wall Street Receipts point-in-time financial evidence",
      }],
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images: ["/og.png"],
    },
  };
}

export default async function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  const locale = await getLocale();

  return (
    <html lang={locale} data-scroll-behavior="smooth">
      <body>
        <LocaleProvider locale={locale}>{children}</LocaleProvider>
      </body>
    </html>
  );
}
