import type { MarketMapSnapshot, MarketMapUniverse } from "@/lib/providers";
import { presentMarketMapCell } from "@/lib/market-map-engine";
import type { Locale } from "@/lib/i18n/config";
import { formatMarketMapUtc, getMarketMapMessages } from "@/components/market-map-messages";

export const MARKET_MAP_LABELS: Record<MarketMapUniverse, string> = {
  sp500: "S&P 500",
  nasdaq100: "Nasdaq 100",
};

export function MarketMap({ snapshot, locale }: { snapshot: MarketMapSnapshot; locale: Locale }) {
  const messages = getMarketMapMessages(locale).analystMap;
  const universeLabel = MARKET_MAP_LABELS[snapshot.universe];
  const hasCells = snapshot.cells.length > 0;

  return (
    <section className="data-section market-map" aria-labelledby="market-map-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{messages.eyebrow}</p>
          <h2 id="market-map-title">{messages.title(universeLabel)}</h2>
        </div>
        <span>{messages.sample(snapshot.coverage.cellCount, snapshot.dataMode)}</span>
      </div>

      <div className="market-map-coverage" role="note">
        <strong>{messages.coverageStrong}</strong>
        <span>
          {messages.coverageLead(snapshot.coverage.kind, snapshot.coverage.completeUniverse)}
          {hasCells ? (
            <>
              {" "}{messages.coverageWide(snapshot.coverage.weightBasis)}
            </>
          ) : (
            <>
              {" "}{messages.coverageEmpty(snapshot.coverage.weightBasis)}
            </>
          )}
        </span>
      </div>

      <dl className="map-evidence-grid" aria-label={messages.definitionLabel(universeLabel)}>
        <div>
          <dt>{messages.mapMode}</dt>
          <dd className="mono">{snapshot.mode}</dd>
        </div>
        <div>
          <dt>{messages.storedMetric}</dt>
          <dd className="mono">{snapshot.metric.name}</dd>
        </div>
        <div>
          <dt>{messages.metricUnit}</dt>
          <dd className="mono">{snapshot.metric.unit}</dd>
        </div>
        <div>
          <dt>{messages.weightBasis}</dt>
          <dd className="mono">{snapshot.coverage.weightBasis}</dd>
        </div>
      </dl>

      {hasCells ? (
        <>
          <div className="market-map-legend" aria-label={messages.legendLabel(snapshot.metric.name)}>
            <span>{messages.storedDemoScore}</span>
            <span className="map-legend-negative mono">{snapshot.metric.minimum}</span>
            <span className="mono">0</span>
            <span className="map-legend-positive mono">{snapshot.metric.maximum}</span>
            <span className="na-value">{messages.unavailableLegend}</span>
          </div>

          <ol className="market-map-cells" aria-label={messages.cellsLabel(universeLabel)}>
            {snapshot.cells.map((cell) => {
              const presentation = presentMarketMapCell(cell, snapshot.metric);

              return (
                <li key={cell.assetId} style={{ flexGrow: cell.weight }}>
                  <article
                    className={`market-map-cell map-metric-${presentation.metricTone}`}
                    aria-label={messages.cellLabel(cell.ticker)}
                  >
                    <p className="eyebrow">{presentation.sectorDisplay}</p>
                    <h3>{cell.ticker}</h3>
                    <div className="map-cell-metric">
                      <span>{messages.storedDemoMetric}</span>
                      <strong className="mono">{presentation.metricDisplay}</strong>
                      {cell.metric === null ? null : <small>{snapshot.metric.unit}</small>}
                    </div>
                    <dl>
                      <div>
                        <dt>{messages.fixtureWeight}</dt>
                        <dd className="mono">{cell.weight}</dd>
                      </div>
                      <div>
                        <dt>{messages.fixtureCallCount}</dt>
                        <dd className="mono">{cell.callCount}</dd>
                      </div>
                      <div>
                        <dt>{messages.timestamp}</dt>
                        <dd className="mono">{formatMarketMapUtc(cell.timestamp)}</dd>
                      </div>
                      <div>
                        <dt>{messages.mode}</dt>
                        <dd className="mono">{cell.dataMode}</dd>
                      </div>
                      <div>
                        <dt>{messages.provenance}</dt>
                        <dd className="mono">{cell.provenanceId}</dd>
                      </div>
                    </dl>
                  </article>
                </li>
              );
            })}
          </ol>
        </>
      ) : (
        <div className="empty-state map-empty" role="status">
          <h3>{messages.emptyTitle(universeLabel)}</h3>
          <p>{messages.emptyBody}</p>
        </div>
      )}

      <p className="section-note map-readonly-note">
        {messages.readonly}
      </p>
      <p className="dataset-disclaimer map-disclaimer">{snapshot.disclaimer}</p>
    </section>
  );
}
