import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { getSp500HistoryMessages } from "./messages";

export default async function Sp500HistoryLoading() {
  const messages = getSp500HistoryMessages(await getLocale()).states;

  return (
    <>
      <SiteHeader current="market" dataMode="DEMO" />
      <main className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">{messages.loadingEyebrow}</p>
        <h1>{messages.loadingTitle}</h1>
        <p>{messages.loadingDescription}</p>
      </main>
    </>
  );
}
