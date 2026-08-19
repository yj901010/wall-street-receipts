"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function InstitutionsError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <>
      <SiteHeader current="institutions" dataMode="DEMO" />
      <main className="state-page route-error" role="alert">
        <p className="eyebrow">Institution directory unavailable</p>
        <h1>The identity fixture could not be read.</h1>
        <p>No partial identity, placeholder institution, score, accuracy, or rank is being displayed.</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>Try again</button>
          <Link className="text-action" href="/calls">Open the call ledger</Link>
        </div>
      </main>
    </>
  );
}
