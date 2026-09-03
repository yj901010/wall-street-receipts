import type { CSSProperties } from "react";
import { KstTimestamp } from "@/components/kst-timestamp";
import { getMarketMapMessages } from "@/components/market-map-messages";
import type { Locale } from "@/lib/i18n/config";
import type { MarketTreemapSnapshot, MarketTreemapUniverse } from "@/lib/providers";
import {
  layoutMarketTreemap,
  MARKET_TREEMAP_CANVAS,
  marketTreemapLabelDensity,
  marketTreemapPaletteStops,
  presentMarketTreemapCell,
  type MarketTreemapNodeValue,
} from "@/lib/market-treemap-engine";
import type { TreemapLayoutNode, TreemapRect } from "@/lib/treemap-layout";

export const MARKET_TREEMAP_LABELS: Record<MarketTreemapUniverse, string> = {
  sp500: "S&P 500",
  nasdaq100: "Nasdaq 100",
};

function position(rect: TreemapRect): CSSProperties {
  const { width, height } = MARKET_TREEMAP_CANVAS;
  return {
    left: `${(rect.x / width) * 100}%`,
    top: `${(rect.y / height) * 100}%`,
    width: `${(rect.width / width) * 100}%`,
    height: `${(rect.height / height) * 100}%`,
  };
}

function childrenOfKind(
  nodes: readonly TreemapLayoutNode<MarketTreemapNodeValue>[],
  kind: MarketTreemapNodeValue["kind"],
): TreemapLayoutNode<MarketTreemapNodeValue>[] {
  return nodes.flatMap((node) => [
    ...(node.value.kind === kind ? [node] : []),
    ...childrenOfKind(node.children, kind),
  ]);
}

export function MarketTreemap({
  snapshot,
  locale,
}: {
  snapshot: MarketTreemapSnapshot;
  locale: Locale;
}) {
  const messages = getMarketMapMessages(locale).treemap;
  const universeLabel = MARKET_TREEMAP_LABELS[snapshot.universe];
  const layout = layoutMarketTreemap(snapshot);
  const sectors = childrenOfKind(layout, "sector");
  const industries = childrenOfKind(layout, "industry");
  const cells = childrenOfKind(layout, "cell");
  const paletteStops = marketTreemapPaletteStops(snapshot.metric);

  return (
    <section className="data-section market-treemap" aria-labelledby="market-treemap-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{messages.eyebrow}</p>
          <h2 id="market-treemap-title">{messages.title(universeLabel)}</h2>
        </div>
        <span>{messages.sample(snapshot.coverage.cellCount, snapshot.dataMode)}</span>
      </div>

      <div className="treemap-coverage" role="note">
        <strong>{messages.coverageStrong}</strong>
        <span>{messages.coverageGrouping(sectors.length, industries.length)}</span>
        <span>{messages.coverageArea(snapshot.geometry.areaField, snapshot.geometry.areaUnit)}</span>
      </div>

      <dl className="treemap-evidence-grid" aria-label={messages.definitionLabel(universeLabel)}>
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
          <dt>{messages.grouping}</dt>
          <dd className="mono">{snapshot.geometry.groupBy.join(" → ")}</dd>
        </div>
        <div>
          <dt>{messages.weightBasis}</dt>
          <dd className="mono">{snapshot.coverage.weightBasis}</dd>
        </div>
        <div>
          <dt>{messages.coverage}</dt>
          <dd className="mono">
            {snapshot.coverage.kind} / completeUniverse={String(snapshot.coverage.completeUniverse)}
          </dd>
        </div>
      </dl>

      <div
        className="treemap-percent-legend"
        aria-label={messages.legendLabel(
          snapshot.metric.scaleMinimum,
          snapshot.metric.scaleMaximum,
        )}
      >
        <div>
          <strong>{messages.storedPercent}</strong>
          <span>{messages.saturation}</span>
        </div>
        <ol>
          {paletteStops.map((stop) => (
            <li key={stop.value}>
              <span className="treemap-legend-swatch" style={{ backgroundColor: stop.backgroundColor }} />
              <span className="mono">{stop.metricDisplay}</span>
            </li>
          ))}
          <li>
            <span className="treemap-legend-swatch treemap-legend-na" />
            <span className="mono">NA</span>
          </li>
        </ol>
        <span className="na-value">{messages.unavailable}</span>
      </div>

      {cells.length > 0 ? (
        <>
          <div className="treemap-scroll" aria-label={messages.scrollLabel(universeLabel)} tabIndex={0}>
            <div
              className="treemap-canvas"
              data-canvas-width={MARKET_TREEMAP_CANVAS.width}
              data-canvas-height={MARKET_TREEMAP_CANVAS.height}
            >
              <ol className="treemap-cell-layer" aria-label={messages.cellsLabel(universeLabel)}>
                {cells.map((node) => {
                  if (node.value.kind !== "cell") return null;
                  const { cell, sectorLabel, industryLabel } = node.value;
                  const presentation = presentMarketTreemapCell(cell, snapshot.metric);
                  const labelDensity = marketTreemapLabelDensity(node.rect);
                  const tooltipId = `treemap-tooltip-${cell.assetId}`;

                  return (
                    <li
                      key={cell.assetId}
                      className="treemap-cell-position"
                      style={position(node.rect)}
                      data-proxy={cell.syntheticMarketCapProxy}
                      data-rect-x={node.rect.x}
                      data-rect-y={node.rect.y}
                      data-rect-width={node.rect.width}
                      data-rect-height={node.rect.height}
                    >
                      <article
                        className={`treemap-cell treemap-metric-${presentation.metricTone} treemap-label-${labelDensity}`}
                        style={{ backgroundColor: presentation.backgroundColor }}
                        tabIndex={0}
                        aria-label={messages.cellLabel(cell.ticker, presentation.metricDisplay)}
                        aria-describedby={tooltipId}
                      >
                        <div className="treemap-cell-copy">
                          <strong>{cell.ticker}</strong>
                          <span className="mono">{presentation.metricDisplay}</span>
                          <small>{messages.proxy(cell.syntheticMarketCapProxy)}</small>
                        </div>
                        <dl className="treemap-tooltip" id={tooltipId} role="tooltip">
                          <div>
                            <dt>{messages.tooltip.ticker}</dt>
                            <dd>{cell.ticker}</dd>
                          </div>
                          <div>
                            <dt>{messages.tooltip.sector}</dt>
                            <dd>{sectorLabel}</dd>
                          </div>
                          <div>
                            <dt>{messages.tooltip.industry}</dt>
                            <dd>{industryLabel}</dd>
                          </div>
                          <div>
                            <dt>{messages.tooltip.storedChange}</dt>
                            <dd className="mono">{presentation.metricDisplay}</dd>
                          </div>
                          <div>
                            <dt>{messages.tooltip.syntheticProxy}</dt>
                            <dd className="mono">
                              {messages.tooltip.relativeUnits(cell.syntheticMarketCapProxy)}
                            </dd>
                          </div>
                          <div>
                            <dt>{messages.tooltip.timestamp}</dt>
                            <dd><KstTimestamp value={cell.timestamp} /></dd>
                          </div>
                          <div>
                            <dt>{messages.tooltip.dataMode}</dt>
                            <dd className="mono">{cell.dataMode}</dd>
                          </div>
                          <div>
                            <dt>{messages.tooltip.provenance}</dt>
                            <dd className="mono">{cell.provenanceId}</dd>
                          </div>
                        </dl>
                      </article>
                    </li>
                  );
                })}
              </ol>

              <div className="treemap-industry-layer" aria-hidden="true">
                {industries.map((node) => (
                  <div
                    key={node.id}
                    className="treemap-industry-outline"
                    style={position(node.rect)}
                    data-group-weight={node.weight}
                  >
                    <span>{node.value.label}</span>
                  </div>
                ))}
              </div>
              <div className="treemap-sector-layer" aria-hidden="true">
                {sectors.map((node) => (
                  <div
                    key={node.id}
                    className="treemap-sector-outline"
                    style={position(node.rect)}
                    data-group-weight={node.weight}
                  >
                    <span>{node.value.label}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <details className="treemap-evidence-index">
            <summary tabIndex={0}>{messages.indexSummary(snapshot.cells.length)}</summary>
            <p>{messages.indexDescription}</p>
            <div
              className="table-scroll treemap-index-scroll"
              aria-label={messages.indexScrollLabel(universeLabel)}
              tabIndex={0}
            >
              <table
                className="treemap-index-table"
                aria-label={messages.indexTableLabel(universeLabel)}
              >
                <thead>
                  <tr>
                    <th scope="col">{messages.columns.assetId}</th>
                    <th scope="col">{messages.columns.ticker}</th>
                    <th scope="col">{messages.columns.sector}</th>
                    <th scope="col">{messages.columns.industry}</th>
                    <th scope="col">{messages.columns.storedChange}</th>
                    <th scope="col">{messages.columns.syntheticProxy}</th>
                    <th scope="col">{messages.columns.timestamp}</th>
                    <th scope="col">{messages.columns.dataMode}</th>
                    <th scope="col">{messages.columns.provenance}</th>
                  </tr>
                </thead>
                <tbody>
                  {snapshot.cells.map((cell) => {
                    const presentation = presentMarketTreemapCell(cell, snapshot.metric);
                    return (
                      <tr key={cell.assetId}>
                        <td data-field="asset-id" data-label={messages.columns.assetId} className="mono">
                          {cell.assetId}
                        </td>
                        <td data-field="ticker" data-label={messages.columns.ticker}>
                          <strong>{cell.ticker}</strong>
                        </td>
                        <td data-field="sector" data-label={messages.columns.sector}>
                          {cell.sector ?? snapshot.geometry.unclassifiedDisplay}
                        </td>
                        <td data-field="industry" data-label={messages.columns.industry}>
                          {cell.industry ?? snapshot.geometry.unclassifiedDisplay}
                        </td>
                        <td data-field="stored-change" data-label={messages.columns.storedChange} className="mono">
                          {presentation.metricDisplay}
                        </td>
                        <td data-field="synthetic-proxy" data-label={messages.columns.syntheticProxy} className="mono">
                          {messages.relativeUnits(cell.syntheticMarketCapProxy)}
                        </td>
                        <td data-field="timestamp" data-label={messages.columns.timestamp}>
                          <KstTimestamp value={cell.timestamp} />
                        </td>
                        <td data-field="data-mode" data-label={messages.columns.dataMode} className="mono">
                          {cell.dataMode}
                        </td>
                        <td data-field="provenance" data-label={messages.columns.provenance} className="mono">
                          {cell.provenanceId}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </details>
        </>
      ) : (
        <div className="empty-state treemap-empty" role="status">
          <h3>{messages.emptyTitle(universeLabel)}</h3>
          <p>{messages.emptyBody}</p>
        </div>
      )}

      <p className="section-note treemap-readonly-note">
        {messages.readonly}
      </p>
      <p className="dataset-disclaimer treemap-disclaimer">{snapshot.disclaimer}</p>
    </section>
  );
}
