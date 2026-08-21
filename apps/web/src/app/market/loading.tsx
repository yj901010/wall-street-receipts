import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { getMarketMessages } from "./messages";

export default async function MarketLoading() {
  const messages = getMarketMessages(await getLocale());
  return (
    <>
      <SiteHeader current="market" dataMode="DEMO" />
      <main className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">{messages.loading.eyebrow}</p>
        <h1>{messages.loading.title}</h1>
        <p>{messages.loading.body}</p>
      </main>
    </>
  );
}
