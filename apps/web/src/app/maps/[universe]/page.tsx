import Link from "next/link";
import { notFound } from "next/navigation";
import { MarketMap, MARKET_MAP_LABELS } from "@/components/market-map";
import { KstTimestamp } from "@/components/kst-timestamp";
import { getMarketMapMessages } from "@/components/market-map-messages";
import { MarketTreemap } from "@/components/market-treemap";
import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import {
  isMarketTreemapUniverse,
  MARKET_TREEMAP_UNIVERSES,
  marketMapProvider,
  marketTreemapProvider,
} from "@/lib/providers";

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
  const locale = await getLocale();
  const messages = getMarketMapMessages(locale);
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
        <nav className="map-mode-nav" aria-label={messages.route.modeNavLabel}>
          <span>{messages.route.metric}</span>
          <Link
            href={`/maps/${snapshot.universe}`}
            aria-current={routeMode === "price-change" ? "page" : undefined}
          >
            {messages.route.priceChange}
          </Link>
          <Link
            href={`/maps/${snapshot.universe}?mode=analyst-consensus`}
            aria-current={routeMode === "analyst-consensus" ? "page" : undefined}
          >
            {messages.route.analystConsensus}
          </Link>
        </nav>

        <nav className="map-universe-nav" aria-label={messages.route.universeNavLabel}>
          <span>{messages.route.universe}</span>
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
            <p className="eyebrow">{messages.route.eyebrow}</p>
            <h1 id="maps-title">{messages.route.title(universeLabel)}</h1>
            <p className="page-summary">
              {routeMode === "price-change"
                ? messages.route.priceChangeSummary
                : messages.route.analystConsensusSummary}
            </p>
          </div>
          <dl
            className="provenance-strip map-provenance"
            aria-label={messages.route.provenanceLabel(universeLabel)}
          >
            <div>
              <dt>{messages.route.asOf}</dt>
              <dd><KstTimestamp value={snapshot.asOf} /></dd>
            </div>
            <div>
              <dt>{messages.route.captured}</dt>
              <dd><KstTimestamp value={capturedAt} /></dd>
            </div>
            <div>
              <dt>{messages.route.generated}</dt>
              <dd><KstTimestamp value={snapshot.generatedAt} /></dd>
            </div>
            <div>
              <dt>{messages.route.source}</dt>
              <dd>{source}</dd>
            </div>
            <div>
              <dt>{messages.route.dataMode}</dt>
              <dd>{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        {priceChangeSnapshot ? (
          <MarketTreemap snapshot={priceChangeSnapshot} locale={locale} />
        ) : (
          <MarketMap snapshot={analystConsensusSnapshot!} locale={locale} />
        )}
      </div>
    </main>
  );
}
