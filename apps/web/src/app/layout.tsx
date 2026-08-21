import type { Metadata } from "next";
import type { ReactNode } from "react";
import { LocaleProvider } from "@/components/locale-provider";
import { getCommonMessages } from "@/lib/i18n/messages";
import { getLocale } from "@/lib/i18n/server";
import "@/styles/tokens.css";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const messages = getCommonMessages(await getLocale());

  return {
    title: messages.metadata.title,
    description: messages.metadata.description,
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
