import type { CSSProperties } from "react";
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

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

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

export function MarketTreemap({ snapshot }: { snapshot: MarketTreemapSnapshot }) {
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
          <p className="eyebrow">Nested synthetic map evidence</p>
          <h2 id="market-treemap-title">{universeLabel} price-change treemap</h2>
        </div>
        <span>{snapshot.coverage.cellCount}-cell {snapshot.dataMode} sample</span>
      </div>

      <div className="treemap-coverage" role="note">
        <strong>Limited DEMO sample — not a complete index treemap.</strong>
        <span>
          This committed fixture demonstrates {sectors.length} outer sector and {industries.length}
          {" "}nested industries. The engine supports multiple sectors, but this sample does not
          assert broader sector coverage, official membership, or composition.
        </span>
        <span>
          Rectangle area uses only each stored <span className="mono">{snapshot.geometry.areaField}</span>
          {" "}in {snapshot.geometry.areaUnit} units. It is a synthetic proxy, never an official or
          current market-cap value.
        </span>
      </div>

      <dl className="treemap-evidence-grid" aria-label={`${universeLabel} treemap definition`}>
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
          <dt>Grouping</dt>
          <dd className="mono">{snapshot.geometry.groupBy.join(" → ")}</dd>
        </div>
        <div>
          <dt>Weight basis</dt>
          <dd className="mono">{snapshot.coverage.weightBasis}</dd>
        </div>
        <div>
          <dt>Coverage</dt>
          <dd className="mono">
            {snapshot.coverage.kind} / completeUniverse={String(snapshot.coverage.completeUniverse)}
          </dd>
        </div>
      </dl>

      <div
        className="treemap-percent-legend"
        aria-label={`Price-change percent color legend; palette saturates at ${snapshot.metric.scaleMinimum}% and +${snapshot.metric.scaleMaximum}%`}
      >
        <div>
          <strong>Stored price-change percent</strong>
          <span>Color saturates at the declared endpoints; displayed values are never clamped.</span>
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
        <span className="na-value">NA is unavailable, not zero or negative.</span>
      </div>

      {cells.length > 0 ? (
        <>
          <div className="treemap-scroll" aria-label={`${universeLabel} treemap scroll region`} tabIndex={0}>
            <div
              className="treemap-canvas"
              data-canvas-width={MARKET_TREEMAP_CANVAS.width}
              data-canvas-height={MARKET_TREEMAP_CANVAS.height}
            >
              <ol className="treemap-cell-layer" aria-label={`${universeLabel} nested DEMO treemap cells`}>
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
                        aria-label={`${cell.ticker} treemap evidence: ${presentation.metricDisplay}`}
                        aria-describedby={tooltipId}
                      >
                        <div className="treemap-cell-copy">
                          <strong>{cell.ticker}</strong>
                          <span className="mono">{presentation.metricDisplay}</span>
                          <small>Proxy {cell.syntheticMarketCapProxy}</small>
                        </div>
                        <dl className="treemap-tooltip" id={tooltipId} role="tooltip">
                          <div>
                            <dt>Ticker</dt>
                            <dd>{cell.ticker}</dd>
                          </div>
                          <div>
                            <dt>Sector</dt>
                            <dd>{sectorLabel}</dd>
                          </div>
                          <div>
                            <dt>Industry</dt>
                            <dd>{industryLabel}</dd>
                          </div>
                          <div>
                            <dt>Stored change</dt>
                            <dd className="mono">{presentation.metricDisplay}</dd>
                          </div>
                          <div>
                            <dt>Synthetic proxy</dt>
                            <dd className="mono">{cell.syntheticMarketCapProxy} relative units</dd>
                          </div>
                          <div>
                            <dt>Timestamp</dt>
                            <dd>{utc(cell.timestamp)}</dd>
                          </div>
                          <div>
                            <dt>Data mode</dt>
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
            <summary tabIndex={0}>Accessible evidence index · {snapshot.cells.length} cells</summary>
            <p>
              This non-geometric index preserves every stored field when a proportional tile is too
              small for visible content. It does not change or impose a minimum tile area.
            </p>
            <div
              className="table-scroll treemap-index-scroll"
              aria-label={`${universeLabel} accessible treemap evidence scroll region`}
              tabIndex={0}
            >
              <table
                className="treemap-index-table"
                aria-label={`${universeLabel} accessible treemap evidence index`}
              >
                <thead>
                  <tr>
                    <th scope="col">Asset ID</th>
                    <th scope="col">Ticker</th>
                    <th scope="col">Sector</th>
                    <th scope="col">Industry</th>
                    <th scope="col">Stored change</th>
                    <th scope="col">Synthetic proxy</th>
                    <th scope="col">Timestamp</th>
                    <th scope="col">Data mode</th>
                    <th scope="col">Provenance</th>
                  </tr>
                </thead>
                <tbody>
                  {snapshot.cells.map((cell) => {
                    const presentation = presentMarketTreemapCell(cell, snapshot.metric);
                    return (
                      <tr key={cell.assetId}>
                        <td className="mono">{cell.assetId}</td>
                        <td><strong>{cell.ticker}</strong></td>
                        <td>{cell.sector ?? snapshot.geometry.unclassifiedDisplay}</td>
                        <td>{cell.industry ?? snapshot.geometry.unclassifiedDisplay}</td>
                        <td className="mono">{presentation.metricDisplay}</td>
                        <td className="mono">{cell.syntheticMarketCapProxy} relative units</td>
                        <td>{utc(cell.timestamp)}</td>
                        <td className="mono">{cell.dataMode}</td>
                        <td className="mono">{cell.provenanceId}</td>
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
          <h3>No {universeLabel} treemap cells are available.</h3>
          <p>
            No sector, industry, ticker, proxy area, or price-change value was inferred, and no
            cells from another universe were substituted.
          </p>
        </div>
      )}

      <p className="section-note treemap-readonly-note">
        Canonical ticker cells are read-only and keyboard focusable. The accessible evidence index
        preserves inspection when proportional geometry becomes subpixel. Stock detail evidence is
        not published in this phase, so no drilldown link is shown.
      </p>
      <p className="dataset-disclaimer treemap-disclaimer">{snapshot.disclaimer}</p>
    </section>
  );
}
