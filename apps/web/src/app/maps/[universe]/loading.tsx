import { SiteHeader } from "@/components/site-header";

export default function MarketMapLoading() {
  return (
    <main>
      <SiteHeader current="maps" dataMode="DEMO" />
      <div className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">Market map evidence</p>
        <h1>Loading the DEMO map evidence…</h1>
        <p>Reading the selected mode, coverage, timestamps, and provenance without filling missing cells.</p>
      </div>
    </main>
  );
}
