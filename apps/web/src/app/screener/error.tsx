"use client";

import Link from "next/link";
import { useLocale } from "@/components/locale-provider";
import { SiteHeader } from "@/components/site-header";
import { getScreenerMessages } from "./messages";

export default function ScreenerError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const { locale } = useLocale();
  const messages = getScreenerMessages(locale);
  return (
    <main>
      <SiteHeader current="screener" dataMode="DEMO" />
      <div className="state-page route-error" role="alert">
        <p className="eyebrow">{messages.error.eyebrow}</p>
        <h1>{messages.error.title}</h1>
        <p>{messages.error.body}</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>{messages.error.retry}</button>
          <Link className="text-action" href="/calls">{messages.error.calls}</Link>
          <Link className="text-action" href="/methodology">{messages.error.methodology}</Link>
        </div>
      </div>
    </main>
  );
}
