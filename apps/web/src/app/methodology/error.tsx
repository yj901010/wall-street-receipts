"use client";

import Link from "next/link";
import { useLocale } from "@/components/locale-provider";
import { SiteHeader } from "@/components/site-header";
import { getMethodologyMessages } from "./messages";

export default function MethodologyError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const { locale } = useLocale();
  const messages = getMethodologyMessages(locale);
  return (
    <main>
      <SiteHeader current="methodology" dataMode="DEMO" />
      <div className="state-page route-error" role="alert">
        <p className="eyebrow">{messages.error.eyebrow}</p>
        <h1>{messages.error.title}</h1>
        <p>{messages.error.body}</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>{messages.error.retry}</button>
          <Link className="text-action" href="/">{messages.error.dashboard}</Link>
        </div>
      </div>
    </main>
  );
}
