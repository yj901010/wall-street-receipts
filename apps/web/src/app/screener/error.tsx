"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function ScreenerError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main>
      <SiteHeader current="screener" dataMode="DEMO" />
      <div className="state-page route-error" role="alert">
        <p className="eyebrow">Screener policy unavailable</p>
        <h1>The application phase policy could not be read.</h1>
        <p>
          No fixture, source, filter, result, chart, or numeric value is displayed as a fallback.
        </p>
        <div className="state-actions">
          <button type="button" onClick={reset}>Try again</button>
          <Link className="text-action" href="/calls">Open recorded call evidence</Link>
          <Link className="text-action" href="/methodology">Open methodology definitions</Link>
        </div>
      </div>
    </main>
  );
}
