import { SiteHeader } from "@/components/site-header";

export default function Sp500HistoryLoading() {
  return (
    <>
      <SiteHeader current="market" dataMode="DEMO" />
      <main className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">Recorded S&amp;P 500 call events</p>
        <h1>Loading the committed DEMO call subset…</h1>
        <p>No market price, chart, target, status, outcome, or placeholder row is filled while it loads.</p>
      </main>
    </>
  );
}
