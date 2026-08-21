import Link from "next/link";
import { getMarketMapMessages } from "@/components/market-map-messages";
import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";

export default async function MarketMapNotFound() {
  const messages = getMarketMapMessages(await getLocale()).notFound;
  return (
    <main>
      <SiteHeader current="maps" dataMode="DEMO" />
      <div className="state-page route-error">
        <p className="eyebrow">{messages.eyebrow}</p>
        <h1>{messages.title}</h1>
        <p>{messages.body}</p>
        <div className="state-actions">
          <Link className="text-action" href="/maps/sp500">{messages.sp500}</Link>
          <Link className="text-action" href="/maps/nasdaq100">{messages.nasdaq100}</Link>
        </div>
      </div>
    </main>
  );
}
