import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { getInstitutionMessages } from "./messages";

export default async function InstitutionsLoading() {
  const messages = getInstitutionMessages(await getLocale());
  return (
    <>
      <SiteHeader current="institutions" dataMode="DEMO" />
      <main className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">{messages.loading.eyebrow}</p>
        <h1>{messages.loading.title}</h1>
        <p>{messages.loading.body}</p>
      </main>
    </>
  );
}
