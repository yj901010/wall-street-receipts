"use client";

import Link from "next/link";
import { getDashboardMessages } from "@/components/dashboard-messages";
import { useLocale } from "@/components/locale-provider";
import { SiteHeader } from "@/components/site-header";

export default function DashboardError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const { locale } = useLocale();
  const messages = getDashboardMessages(locale);
  return (
    <main>
      <SiteHeader current="dashboard" dataMode="DEMO" />
      <div className="state-page route-error" role="alert">
        <p className="eyebrow">{messages.error.eyebrow}</p>
        <h1>{messages.error.title}</h1>
        <p>{messages.error.body}</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>{messages.error.retry}</button>
          <Link className="text-action" href="/calls">{messages.error.calls}</Link>
        </div>
      </div>
    </main>
  );
}
