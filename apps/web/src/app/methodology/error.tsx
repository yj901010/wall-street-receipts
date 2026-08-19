"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function MethodologyError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main>
      <SiteHeader current="methodology" dataMode="DEMO" />
      <div className="state-page route-error" role="alert">
        <p className="eyebrow">Methodology registry unavailable</p>
        <h1>The definition evidence could not be read.</h1>
        <p>No partial definition or calculated value is being displayed.</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>Try again</button>
          <Link className="text-action" href="/">Return to dashboard</Link>
        </div>
      </div>
    </main>
  );
}
