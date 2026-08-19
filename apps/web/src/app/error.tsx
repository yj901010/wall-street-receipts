"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function DashboardError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main>
      <SiteHeader current="dashboard" dataMode="DEMO" />
      <div className="state-page route-error" role="alert">
        <p className="eyebrow">Dashboard evidence unavailable</p>
        <h1>The fixture sections could not be composed.</h1>
        <p>No partial quote, calendar event, ranking, or fallback universe is being displayed.</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>Try again</button>
          <Link className="text-action" href="/calls">Open the call ledger</Link>
        </div>
      </div>
    </main>
  );
}
