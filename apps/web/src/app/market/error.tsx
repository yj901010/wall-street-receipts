"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function MarketError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <>
      <SiteHeader current="market" dataMode="DEMO" />
      <main className="state-page route-error" role="alert">
        <p className="eyebrow">Market board publication state unavailable</p>
        <h1>The DEMO publication record could not be read.</h1>
        <p>
          No partial quote, call-event snapshot, synthetic map value, or application literal is
          being displayed as a fallback.
        </p>
        <div className="state-actions">
          <button type="button" onClick={reset}>Try again</button>
          <Link className="text-action" href="/">Return to dashboard evidence</Link>
        </div>
      </main>
    </>
  );
}
