"use client";

import Link from "next/link";

export default function CallsError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="state-page route-error" role="alert">
      <p className="eyebrow">Call ledger unavailable</p>
      <h1>The fixture could not be read.</h1>
      <p>No partial or invented records are being displayed.</p>
      <div className="state-actions">
        <button type="button" onClick={reset}>Try again</button>
        <Link className="text-action" href="/">Return to dashboard</Link>
      </div>
    </main>
  );
}
