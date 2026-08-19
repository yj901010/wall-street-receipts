import Link from "next/link";
import { SiteHeader } from "@/components/site-header";

export default function MarketMapNotFound() {
  return (
    <main>
      <SiteHeader current="maps" dataMode="DEMO" />
      <div className="state-page route-error">
        <p className="eyebrow">Unsupported map universe</p>
        <h1>This market map is not published.</h1>
        <p>No data from another universe was substituted.</p>
        <div className="state-actions">
          <Link className="text-action" href="/maps/sp500">Open S&amp;P 500 sample</Link>
          <Link className="text-action" href="/maps/nasdaq100">Open Nasdaq 100 state</Link>
        </div>
      </div>
    </main>
  );
}
