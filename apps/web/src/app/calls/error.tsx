"use client";

import Link from "next/link";
import { useLocale } from "@/components/locale-provider";
import { getCallsMessages } from "./messages";

export default function CallsError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const { locale } = useLocale();
  const messages = getCallsMessages(locale).states;

  return (
    <main className="state-page route-error" role="alert">
      <p className="eyebrow">{messages.errorEyebrow}</p>
      <h1>{messages.errorTitle}</h1>
      <p>{messages.errorDescription}</p>
      <div className="state-actions">
        <button type="button" onClick={reset}>{messages.tryAgain}</button>
        <Link className="text-action" href="/">{messages.returnDashboard}</Link>
      </div>
    </main>
  );
}
