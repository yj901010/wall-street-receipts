import { SiteHeader } from "@/components/site-header";

export default function DashboardLoading() {
  return (
    <main>
      <SiteHeader current="dashboard" dataMode="DEMO" />
      <div className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">Dashboard evidence</p>
        <h1>Loading independently sourced DEMO sections…</h1>
        <p>No global timestamp, source, quote, event, or ranking is being filled while evidence loads.</p>
      </div>
    </main>
  );
}
