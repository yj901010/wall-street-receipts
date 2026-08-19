import { SiteHeader } from "@/components/site-header";

export default function AnalystsLoading() {
  return (
    <>
      <SiteHeader current="analysts" dataMode="DEMO" />
      <main className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">Canonical analyst identities</p>
        <h1>Loading analyst evidence…</h1>
        <p>Reading the committed DEMO master-data fixture and its provenance.</p>
      </main>
    </>
  );
}
