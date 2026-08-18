import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function CallNotFound() {
  return (
    <main>
      <SiteHeader current="calls" dataMode="DEMO" />
      <div className="page-shell state-page">
        <p className="eyebrow">Call not found</p>
        <h1>This event is not in the fixture ledger.</h1>
        <p>The requested identifier has no canonical call record. No substitute record was shown.</p>
        <Link className="text-action" href="/calls">Return to analyst calls</Link>
      </div>
    </main>
  );
}
