"use client";
import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { useLocale } from "@/components/locale-provider";
import { cpiMessages } from "./messages";
export default function CpiError({ reset }: { error: Error; reset: () => void }) {
  const { locale } = useLocale();
  const text = cpiMessages(locale);
  return <main><SiteHeader current="market" /><div className="page-shell"><h1>{text.error}</h1><p role="alert">{text.errorBody}</p><button type="button" onClick={reset}>{text.retry}</button><p><Link href="/market" prefetch={false}>{text.back}</Link></p></div></main>;
}
