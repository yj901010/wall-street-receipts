import { getMarketMapMessages } from "@/components/market-map-messages";
import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";

export default async function MarketMapLoading() {
  const messages = getMarketMapMessages(await getLocale()).loading;
  return (
    <main>
      <SiteHeader current="maps" dataMode="DEMO" />
      <div className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">{messages.eyebrow}</p>
        <h1>{messages.title}</h1>
        <p>{messages.body}</p>
      </div>
    </main>
  );
}
