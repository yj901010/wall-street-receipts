import type { MarketMapSnapshot, MarketMapUniverse } from "@/lib/providers";
import { presentMarketMapCell } from "@/lib/market-map-engine";

export const MARKET_MAP_LABELS: Record<MarketMapUniverse, string> = {
  sp500: "S&P 500",
  nasdaq100: "Nasdaq 100",
};

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

export function MarketMap({ snapshot }: { snapshot: MarketMapSnapshot }) {
  const universeLabel = MARKET_MAP_LABELS[snapshot.universe];
  const hasCells = snapshot.cells.length > 0;

  return (
    <section className="data-section market-map" aria-labelledby="market-map-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Synthetic map evidence</p>
          <h2 id="market-map-title">{universeLabel} analyst-consensus sample</h2>
        </div>
        <span>{snapshot.coverage.cellCount}-cell {snapshot.dataMode} sample</span>
      </div>

      <div className="market-map-coverage" role="note">
        <strong>Limited DEMO sample — not a complete index map.</strong>
        <span>
          Coverage is {snapshot.coverage.kind}; completeUniverse is {String(snapshot.coverage.completeUniverse)}.
          {hasCells ? (
            <>
              {" "}On wide layouts, tile area uses <span className="mono">{snapshot.coverage.weightBasis}</span>
              {" "}fixture weights, not official index or market-cap weights. Small screens stack the
              same cells for readability while preserving each recorded weight.
            </>
          ) : (
            <>
              {" "}The declared weight basis is <span className="mono">{snapshot.coverage.weightBasis}</span>,
              but no tile geometry or weight is rendered or inferred for this known-empty fixture.
            </>
          )}
        </span>
      </div>

      <dl className="map-evidence-grid" aria-label={`${universeLabel} map definition`}>
        <div>
          <dt>Map mode</dt>
          <dd className="mono">{snapshot.mode}</dd>
        </div>
        <div>
          <dt>Stored metric</dt>
          <dd className="mono">{snapshot.metric.name}</dd>
        </div>
        <div>
          <dt>Metric unit</dt>
          <dd className="mono">{snapshot.metric.unit}</dd>
        </div>
        <div>
          <dt>Weight basis</dt>
          <dd className="mono">{snapshot.coverage.weightBasis}</dd>
        </div>
      </dl>

      {hasCells ? (
        <>
          <div className="market-map-legend" aria-label={`${snapshot.metric.name} legend`}>
            <span>Stored DEMO score</span>
            <span className="map-legend-negative mono">{snapshot.metric.minimum}</span>
            <span className="mono">0</span>
            <span className="map-legend-positive mono">{snapshot.metric.maximum}</span>
            <span className="na-value">NA = unavailable, not zero or negative</span>
          </div>

          <ol className="market-map-cells" aria-label={`${universeLabel} limited DEMO sample cells`}>
            {snapshot.cells.map((cell) => {
              const presentation = presentMarketMapCell(cell, snapshot.metric);

              return (
                <li key={cell.assetId} style={{ flexGrow: cell.weight }}>
                  <article
                    className={`market-map-cell map-metric-${presentation.metricTone}`}
                    aria-label={`${cell.ticker} map evidence`}
                  >
                    <p className="eyebrow">{presentation.sectorDisplay}</p>
                    <h3>{cell.ticker}</h3>
                    <div className="map-cell-metric">
                      <span>Stored DEMO metric</span>
                      <strong className="mono">{presentation.metricDisplay}</strong>
                      {cell.metric === null ? null : <small>{snapshot.metric.unit}</small>}
                    </div>
                    <dl>
                      <div>
                        <dt>Fixture weight</dt>
                        <dd className="mono">{cell.weight}</dd>
                      </div>
                      <div>
                        <dt>Fixture call count</dt>
                        <dd className="mono">{cell.callCount}</dd>
                      </div>
                      <div>
                        <dt>Timestamp</dt>
                        <dd className="mono">{utc(cell.timestamp)}</dd>
                      </div>
                      <div>
                        <dt>Mode</dt>
                        <dd className="mono">{cell.dataMode}</dd>
                      </div>
                      <div>
                        <dt>Provenance</dt>
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
          <h3>No {universeLabel} map cells are available.</h3>
          <p>
            No membership, weight, metric, or call count was inferred, and no cells from another
            universe were substituted.
          </p>
        </div>
      )}

      <p className="section-note map-readonly-note">
        Cells are read-only. Stock detail evidence is not published in this phase, so no drilldown
        link is shown.
      </p>
      <p className="dataset-disclaimer map-disclaimer">{snapshot.disclaimer}</p>
    </section>
  );
}
