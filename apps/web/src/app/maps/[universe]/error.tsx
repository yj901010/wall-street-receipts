"use client";

import Link from "next/link";
import { useLocale } from "@/components/locale-provider";
import { getMarketMapMessages } from "@/components/market-map-messages";
import { SiteHeader } from "@/components/site-header";

export default function MarketMapError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const { locale } = useLocale();
  const messages = getMarketMapMessages(locale).error;
  return (
    <main>
      <SiteHeader current="maps" dataMode="DEMO" />
      <div className="state-page route-error" role="alert">
        <p className="eyebrow">{messages.eyebrow}</p>
        <h1>{messages.title}</h1>
        <p>{messages.body}</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>{messages.retry}</button>
          <Link className="text-action" href="/">{messages.dashboard}</Link>
        </div>
      </div>
    </main>
  );
}
