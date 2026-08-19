import { SiteHeader } from "@/components/site-header";

export default function MarketLoading() {
  return (
    <>
      <SiteHeader current="market" dataMode="DEMO" />
      <main className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">Market board publication state</p>
        <h1>Loading the DEMO publication record…</h1>
        <p>No quote, change, session status, freshness, or coverage is filled while it loads.</p>
      </main>
    </>
  );
}
