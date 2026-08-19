import Link from "next/link";
import { notFound } from "next/navigation";
import { MarketMap, MARKET_MAP_LABELS } from "@/components/market-map";
import { SiteHeader } from "@/components/site-header";
import {
  isMarketMapUniverse,
  MARKET_MAP_UNIVERSES,
  marketMapProvider,
} from "@/lib/providers";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

export const dynamicParams = false;

export function generateStaticParams() {
  return MARKET_MAP_UNIVERSES.map((universe) => ({ universe }));
}

export default async function MarketMapPage({
  params,
}: {
  params: Promise<{ universe: string }>;
}) {
  const { universe: rawUniverse } = await params;

  if (!isMarketMapUniverse(rawUniverse)) {
    notFound();
  }

  const snapshot = await marketMapProvider().findByUniverse(rawUniverse);
  const universeLabel = MARKET_MAP_LABELS[snapshot.universe];

  return (
    <main>
      <SiteHeader current="maps" dataMode={snapshot.dataMode} />

      <div className="page-shell maps-shell">
        <nav className="map-universe-nav" aria-label="Market map universes">
          <span>Universe</span>
          {MARKET_MAP_UNIVERSES.map((universe) => (
            <Link
              key={universe}
              href={`/maps/${universe}`}
              aria-current={universe === snapshot.universe ? "page" : undefined}
            >
              {MARKET_MAP_LABELS[universe]}
            </Link>
          ))}
        </nav>

        <section className="page-heading maps-heading" aria-labelledby="maps-title">
          <div>
            <p className="eyebrow">Read-only fixture map</p>
            <h1 id="maps-title">{universeLabel} map evidence.</h1>
            <p className="page-summary">
              This surface displays standalone synthetic fixture values. It does not claim full
              index composition, live membership, official weights, or metrics derived from the
              canonical call ledger.
            </p>
          </div>
          <dl className="provenance-strip map-provenance" aria-label={`${universeLabel} map provenance`}>
            <div>
              <dt>As of</dt>
              <dd>{utc(snapshot.asOf)}</dd>
            </div>
            <div>
              <dt>Captured</dt>
              <dd>{utc(snapshot.capturedAt)}</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{utc(snapshot.generatedAt)}</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>{snapshot.source}</dd>
            </div>
            <div>
              <dt>Data mode</dt>
              <dd>{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        <MarketMap snapshot={snapshot} />
      </div>
    </main>
  );
}
