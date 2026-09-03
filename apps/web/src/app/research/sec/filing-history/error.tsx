"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { useLocale } from "@/components/locale-provider";
import { SEC_MANIFEST_AUDIT_ROUTE } from "@/lib/providers/sec-manifest-audit-query";
import { getSecManifestAuditMessages } from "./messages";

export default function SecFilingHistoryAuditError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const { locale } = useLocale();
  const messages = getSecManifestAuditMessages(locale).states;
  return (
    <main>
      <SiteHeader current="secEvidence" />
      <div className="page-shell state-page route-error" role="alert">
        <p className="eyebrow">{messages.errorEyebrow}</p>
        <h1>{messages.errorTitle}</h1>
        <p>{messages.errorBody}</p>
        <div className="state-actions">
          <button type="button" onClick={reset}>{messages.retry}</button>
          <Link className="text-action" href={SEC_MANIFEST_AUDIT_ROUTE}>
            {messages.returnLocator}
          </Link>
        </div>
      </div>
    </main>
  );
}
