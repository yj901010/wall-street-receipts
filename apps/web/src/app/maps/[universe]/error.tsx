"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function MarketMapError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main>
      <SiteHeader current="maps" dataMode="DEMO" />
      <div className="state-page route-error" role="alert">
        <p className="eyebrow">Market map unavailable</p>
        <h1>The map evidence could not be read.</h1>
        <p>No cell, geometry, weight, metric, or universe membership was inferred.</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>Try again</button>
          <Link className="text-action" href="/">Return to dashboard</Link>
        </div>
      </div>
    </main>
  );
}
