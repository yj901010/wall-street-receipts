import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { getMethodologyMessages } from "./messages";

export default async function MethodologyLoading() {
  const messages = getMethodologyMessages(await getLocale());
  return (
    <main>
      <SiteHeader current="methodology" dataMode="DEMO" />
      <div className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">{messages.loading.eyebrow}</p>
        <h1>{messages.loading.title}</h1>
        <p>{messages.loading.body}</p>
      </div>
    </main>
  );
}
