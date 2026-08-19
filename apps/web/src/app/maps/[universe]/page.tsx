import Link from "next/link";
import { notFound } from "next/navigation";
import { MarketMap, MARKET_MAP_LABELS } from "@/components/market-map";
import { MarketTreemap } from "@/components/market-treemap";
import { SiteHeader } from "@/components/site-header";
import {
  isMarketTreemapUniverse,
  MARKET_TREEMAP_UNIVERSES,
  marketMapProvider,
  marketTreemapProvider,
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

export type MarketMapRouteMode = "price-change" | "analyst-consensus";

export function readMarketMapRouteMode(
  rawMode: string | string[] | undefined,
): MarketMapRouteMode | null {
  if (rawMode === undefined || rawMode === "price-change") return "price-change";
  if (rawMode === "analyst-consensus") return "analyst-consensus";
  return null;
}

function mapHref(universe: string, mode: MarketMapRouteMode) {
  return mode === "price-change"
    ? `/maps/${universe}`
    : `/maps/${universe}?mode=analyst-consensus`;
}

export function generateStaticParams() {
  return MARKET_TREEMAP_UNIVERSES.map((universe) => ({ universe }));
}

export default async function MarketMapPage({
  params,
  searchParams,
}: {
  params: Promise<{ universe: string }>;
  searchParams: Promise<{ mode?: string | string[] }>;
}) {
  const { universe: rawUniverse } = await params;
  const { mode: rawMode } = await searchParams;
  const routeMode = readMarketMapRouteMode(rawMode);

  if (!isMarketTreemapUniverse(rawUniverse) || routeMode === null) {
    notFound();
  }

  const priceChangeSnapshot = routeMode === "price-change"
    ? await marketTreemapProvider().findByUniverse(rawUniverse)
    : null;
  const analystConsensusSnapshot = routeMode === "analyst-consensus"
    ? await marketMapProvider().findByUniverse(rawUniverse)
    : null;
  const snapshot = priceChangeSnapshot ?? analystConsensusSnapshot;

  if (!snapshot) notFound();

  const universeLabel = MARKET_MAP_LABELS[snapshot.universe];
  const capturedAt = priceChangeSnapshot
    ? priceChangeSnapshot.provenance.capturedAt
    : analystConsensusSnapshot!.capturedAt;
  const source = priceChangeSnapshot
    ? priceChangeSnapshot.provenance.id
    : analystConsensusSnapshot!.source;

  return (
    <main>
      <SiteHeader current="maps" dataMode={snapshot.dataMode} />

      <div className="page-shell maps-shell">
        <nav className="map-mode-nav" aria-label="Market map modes">
          <span>Metric</span>
          <Link
            href={`/maps/${snapshot.universe}`}
            aria-current={routeMode === "price-change" ? "page" : undefined}
          >
            Price change
          </Link>
          <Link
            href={`/maps/${snapshot.universe}?mode=analyst-consensus`}
            aria-current={routeMode === "analyst-consensus" ? "page" : undefined}
          >
            Analyst consensus
          </Link>
        </nav>

        <nav className="map-universe-nav" aria-label="Market map universes">
          <span>Universe</span>
          {MARKET_TREEMAP_UNIVERSES.map((universe) => (
            <Link
              key={universe}
              href={mapHref(universe, routeMode)}
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
              {routeMode === "price-change"
                ? "This default surface displays a nested PRICE_CHANGE DEMO fixture. Area uses a synthetic market-cap proxy; color uses stored synthetic percent values. Neither is live or official market data."
                : "This alternate surface preserves the standalone ANALYST_CONSENSUS DEMO fixture. It does not claim full index composition, live membership, official weights, or metrics derived from the canonical call ledger."}
            </p>
          </div>
          <dl className="provenance-strip map-provenance" aria-label={`${universeLabel} map provenance`}>
            <div>
              <dt>As of</dt>
              <dd>{utc(snapshot.asOf)}</dd>
            </div>
            <div>
              <dt>Captured</dt>
              <dd>{utc(capturedAt)}</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{utc(snapshot.generatedAt)}</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>{source}</dd>
            </div>
            <div>
              <dt>Data mode</dt>
              <dd>{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        {priceChangeSnapshot ? (
          <MarketTreemap snapshot={priceChangeSnapshot} />
        ) : (
          <MarketMap snapshot={analystConsensusSnapshot!} />
        )}
      </div>
    </main>
  );
}
