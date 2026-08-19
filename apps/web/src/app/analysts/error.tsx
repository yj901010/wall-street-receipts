"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function AnalystsError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <>
      <SiteHeader current="analysts" dataMode="DEMO" />
      <main className="state-page route-error" role="alert">
        <p className="eyebrow">Analyst directory unavailable</p>
        <h1>The identity fixture could not be read.</h1>
        <p>No partial identity, affiliation, call data, metric, score, or rank is being displayed.</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>Try again</button>
          <Link className="text-action" href="/calls">Open the call ledger</Link>
        </div>
      </main>
    </>
  );
}
