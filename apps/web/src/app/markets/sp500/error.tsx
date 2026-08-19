"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function Sp500HistoryError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <>
      <SiteHeader current="market" dataMode="DEMO" />
      <main className="state-page route-error" role="alert">
        <p className="eyebrow">S&amp;P 500 call-event history unavailable</p>
        <h1>The committed DEMO call subset could not be read.</h1>
        <p>
          No partial call, market snapshot, chart, outcome, consensus, or application literal is
          being displayed as a fallback.
        </p>
        <div className="state-actions">
          <button type="button" onClick={reset}>Try again</button>
          <Link className="text-action" href="/market">Return to market publication status</Link>
        </div>
      </main>
    </>
  );
}
