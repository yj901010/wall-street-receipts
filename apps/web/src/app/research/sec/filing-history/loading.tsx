import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { getSecManifestAuditMessages } from "./messages";

export default async function SecFilingHistoryAuditLoading() {
  const messages = getSecManifestAuditMessages(await getLocale()).states;
  return (
    <main>
      <SiteHeader />
      <div className="page-shell state-page" aria-busy="true" aria-live="polite">
        <p className="eyebrow">{messages.loadingEyebrow}</p>
        <h1>{messages.loadingTitle}</h1>
        <p>{messages.loadingBody}</p>
      </div>
    </main>
  );
}
