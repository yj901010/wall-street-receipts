"use client";

import Link from "next/link";
import { useLocale } from "@/components/locale-provider";
import { SiteHeader } from "@/components/site-header";
import { getSp500HistoryMessages } from "./messages";

export default function Sp500HistoryError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const { locale } = useLocale();
  const messages = getSp500HistoryMessages(locale).states;

  return (
    <>
      <SiteHeader current="market" dataMode="DEMO" />
      <main className="state-page route-error" role="alert">
        <p className="eyebrow">{messages.errorEyebrow}</p>
        <h1>{messages.errorTitle}</h1>
        <p>{messages.errorDescription}</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>{messages.tryAgain}</button>
          <Link className="text-action" href="/market">{messages.returnMarket}</Link>
        </div>
      </main>
    </>
  );
}
