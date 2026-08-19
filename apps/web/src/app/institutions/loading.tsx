import { SiteHeader } from "@/components/site-header";

export default function InstitutionsLoading() {
  return (
    <>
      <SiteHeader current="institutions" dataMode="DEMO" />
      <main className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">Canonical institution identities</p>
        <h1>Loading institution evidence…</h1>
        <p>Reading the committed DEMO master-data fixture and its provenance.</p>
      </main>
    </>
  );
}
