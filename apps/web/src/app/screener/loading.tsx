import { SiteHeader } from "@/components/site-header";

export default function ScreenerLoading() {
  return (
    <main>
      <SiteHeader current="screener" dataMode="DEMO" />
      <div className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">Screener product phase policy</p>
        <h1>Loading the DEMO application policy…</h1>
        <p>
          No filter, result, ordering, chart, count, or numeric metric is filled while it loads.
        </p>
      </div>
    </main>
  );
}
