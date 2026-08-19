import { SiteHeader } from "@/components/site-header";

export default function MethodologyLoading() {
  return (
    <main>
      <SiteHeader current="methodology" dataMode="DEMO" />
      <div className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">Definition registry</p>
        <h1>Loading methodology evidence…</h1>
        <p>Reading version identity, effective time, hash, and provenance.</p>
      </div>
    </main>
  );
}
