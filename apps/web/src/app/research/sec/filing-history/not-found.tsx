import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { SEC_MANIFEST_AUDIT_ROUTE } from "@/lib/providers/sec-manifest-audit-query";
import { getSecManifestAuditMessages } from "./messages";

export default async function SecFilingHistoryAuditNotFound() {
  const messages = getSecManifestAuditMessages(await getLocale()).states;
  return (
    <main>
      <SiteHeader current="secEvidence" />
      <div className="page-shell state-page">
        <p className="eyebrow">{messages.notFoundEyebrow}</p>
        <h1>{messages.notFoundTitle}</h1>
        <p>{messages.notFoundBody}</p>
        <Link className="text-action" href={SEC_MANIFEST_AUDIT_ROUTE}>
          {messages.returnLocator}
        </Link>
      </div>
    </main>
  );
}
