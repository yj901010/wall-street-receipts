import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { formatMoney } from "@/lib/format-money";
import { presentMarketTreemapCell } from "@/lib/market-treemap-engine";
import type {
  DashboardDeferredSection,
  DashboardSnapshot,
  DashboardUnavailableSection,
  MarketTreemapSnapshot,
  MarketTreemapUniverse,
} from "@/lib/providers";

const universeLabels: Record<MarketTreemapUniverse, string> = {
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

function directionLabel(value: string) {
  return value.replaceAll("_", " ");
}

function MarketMapPreview({ snapshot }: { snapshot: MarketTreemapSnapshot }) {
  const universeLabel = universeLabels[snapshot.universe];
  const titleId = `dashboard-map-preview-${snapshot.universe}-title`;
  const sectorCount = new Set(
    snapshot.cells.map((cell) => cell.sector ?? snapshot.geometry.unclassifiedDisplay),
  ).size;
  const industryCount = new Set(
    snapshot.cells.map((cell) =>
      JSON.stringify([
        cell.sector ?? snapshot.geometry.unclassifiedDisplay,
        cell.industry ?? snapshot.geometry.unclassifiedDisplay,
      ])
    ),
  ).size;

  return (
    <article className="dashboard-map-preview" aria-labelledby={titleId}>
      <header>
        <div>
          <p className="eyebrow">{snapshot.mode} · {snapshot.dataMode}</p>
          <h3 id={titleId}>{universeLabel} map preview</h3>
        </div>
        <Link className="text-action" href={`/maps/${snapshot.universe}`}>
          Open {universeLabel} map
        </Link>
      </header>

      <dl
        className="dashboard-map-provenance"
        aria-label={`${universeLabel} dashboard map preview provenance`}
      >
        <div>
          <dt>As of</dt>
          <dd>{utc(snapshot.asOf)}</dd>
        </div>
        <div>
          <dt>Generated</dt>
          <dd>{utc(snapshot.generatedAt)}</dd>
        </div>
        <div>
          <dt>Captured</dt>
          <dd>{utc(snapshot.provenance.capturedAt)}</dd>
        </div>
        <div>
          <dt>Provenance</dt>
          <dd className="mono">{snapshot.provenance.id}</dd>
        </div>
        <div>
          <dt>Coverage</dt>
          <dd className="mono">{snapshot.coverage.kind} · {snapshot.coverage.cellCount} cells</dd>
        </div>
        <div>
          <dt>Complete universe</dt>
          <dd className="mono">{String(snapshot.coverage.completeUniverse)}</dd>
        </div>
        <div>
          <dt>Stored grouping</dt>
          <dd className="mono">
            {sectorCount} outer {sectorCount === 1 ? "sector" : "sectors"} · {industryCount} industries
          </dd>
        </div>
        <div>
          <dt>Weight basis</dt>
          <dd className="mono">{snapshot.coverage.weightBasis}</dd>
        </div>
        <div>
          <dt>Area unit</dt>
          <dd className="mono">{snapshot.geometry.areaUnit}</dd>
        </div>
      </dl>

      {snapshot.cells.length === 0 ? (
        <div className="empty-state dashboard-map-empty" role="status">
          <h4>No {universeLabel} preview cells are recorded.</h4>
          <p>No cell from another universe was substituted.</p>
        </div>
      ) : (
        <ol
          className="dashboard-map-cells"
          aria-label={`${universeLabel} dashboard PRICE_CHANGE preview cells`}
        >
          {snapshot.cells.map((cell) => {
            const presentation = presentMarketTreemapCell(cell, snapshot.metric);

            return (
              <li key={cell.assetId}>
                <div>
                  <strong>{cell.ticker}</strong>
                  <span>{cell.industry ?? snapshot.geometry.unclassifiedDisplay}</span>
                </div>
                <dl>
                  <div>
                    <dt>Stored change</dt>
                    <dd className={`mono dashboard-map-metric dashboard-map-metric-${presentation.metricTone}`}>
                      {presentation.metricDisplay}
                    </dd>
                  </div>
                  <div>
                    <dt>Synthetic proxy</dt>
                    <dd className="mono">
                      {cell.syntheticMarketCapProxy} {snapshot.geometry.areaUnit} units
                    </dd>
                  </div>
                  <div>
                    <dt>Timestamp</dt>
                    <dd>{utc(cell.timestamp)}</dd>
                  </div>
                </dl>
              </li>
            );
          })}
        </ol>
      )}

      <p className="dataset-disclaimer dashboard-map-disclaimer">{snapshot.disclaimer}</p>
    </article>
  );
}

function UnavailableSection({
  id,
  eyebrow,
  title,
  state,
  children,
}: {
  id: string;
  eyebrow: string;
  title: string;
  state: DashboardUnavailableSection | DashboardDeferredSection;
  children: React.ReactNode;
}) {
  const titleId = `${id}-title`;
  const statusLabel = state.status === "NOT_PUBLISHED" ? "Not published" : "P3 deferred";

  return (
    <section className="data-section dashboard-unavailable" id={id} aria-labelledby={titleId}>
      <div className="section-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2 id={titleId}>{title}</h2>
        </div>
        <span>{statusLabel}</span>
      </div>
      <div className="dashboard-unavailable-body">
        <div role="status">
          <dl aria-label={`${title} availability`}>
            <div>
              <dt>Status</dt>
              <dd className="mono">{state.status}</dd>
            </div>
            <div>
              <dt>Display</dt>
              <dd className="mono na-value">{state.missingDisplay}</dd>
            </div>
          </dl>
        </div>
        <div className="dashboard-unavailable-copy">{children}</div>
      </div>
    </section>
  );
}

export function DashboardView({ snapshot }: { snapshot: DashboardSnapshot }) {
  const [sp500, nasdaq100] = snapshot.mapPreviews;
  const sharedTickerCount = sp500.cells.filter((cell) =>
    nasdaq100.cells.some((candidate) => candidate.assetId === cell.assetId)
  ).length;

  return (
    <main>
      <SiteHeader current="dashboard" dataMode={snapshot.dataMode} />

      <div className="page-shell dashboard-shell" id="top">
        <section className="page-heading dashboard-heading" aria-labelledby="page-title">
          <div>
            <p className="eyebrow">Point-in-time analyst intelligence</p>
            <h1 id="page-title">Market evidence, without inferred gaps.</h1>
            <p className="page-summary">
              Each populated section retains its own timestamp and provenance. This dashboard does
              not synthesize one global as-of time or source across independent fixtures.
            </p>
          </div>
        </section>

        <UnavailableSection
          id="market-board"
          eyebrow="Global market strip"
          title="Market board"
          state={snapshot.marketBoard}
        >
          <p>
            A canonical latest-market-board read model is not published. Call-event snapshots are
            immutable historical context and are not promoted to current quotes.
          </p>
        </UnavailableSection>

        <section className="data-section dashboard-calls" id="calls" aria-labelledby="dashboard-calls-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Committed DEMO event ledger</p>
              <h2 id="dashboard-calls-title">Latest calls within this fixture</h2>
            </div>
            <span>{snapshot.latestCalls.items.length} DEMO events</span>
          </div>

          <dl className="dashboard-section-provenance" aria-label="Dashboard call section provenance">
            <div>
              <dt>As of</dt>
              <dd>{utc(snapshot.latestCalls.asOf)}</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd className="mono">{snapshot.latestCalls.source}</dd>
            </div>
            <div>
              <dt>Data mode</dt>
              <dd className="mono">{snapshot.latestCalls.dataMode}</dd>
            </div>
            <div>
              <dt>Ordering</dt>
              <dd>Original event time, descending</dd>
            </div>
          </dl>

          <p className="section-note dashboard-calls-note">
            “Latest” means latest within the committed DEMO fixture. It does not mean current or
            live, and no revision-folded ranking or performance result is produced.
          </p>

          {snapshot.latestCalls.items.length === 0 ? (
            <div className="empty-state" role="status">
              <h3>No call events are recorded.</h3>
              <p>No placeholder event was created for the dashboard.</p>
            </div>
          ) : (
            <div
              className="table-scroll calls-table-scroll dashboard-calls-scroll"
              tabIndex={0}
              aria-label="Scrollable dashboard latest calls table"
            >
              <table className="calls-table dashboard-calls-table">
                <caption className="visually-hidden">
                  Latest analyst calls within the committed DEMO fixture
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Event time</th>
                    <th scope="col">Institution / analyst</th>
                    <th scope="col">Asset</th>
                    <th scope="col">Direction</th>
                    <th scope="col" className="numeric">Target change</th>
                    <th scope="col">Evidence</th>
                  </tr>
                </thead>
                <tbody>
                  {snapshot.latestCalls.items.map(({ call, institution, analyst, asset, source }) => (
                    <tr key={call.callId}>
                      <td data-label="Event time" className="mono">
                        <Link className="row-link" href={`/calls/${call.callId}`}>
                          {utc(call.eventTime)}
                        </Link>
                      </td>
                      <td data-label="Institution / analyst">
                        <strong>{institution.canonicalName}</strong>
                        <span className="cell-secondary">{analyst?.canonicalName ?? "NA"}</span>
                      </td>
                      <td data-label="Asset">
                        <strong>{asset.ticker ?? "NA"}</strong>
                        <span className="cell-secondary">{asset.canonicalName}</span>
                      </td>
                      <td data-label="Direction">
                        <span className={`direction direction-${call.direction.toLowerCase()}`}>
                          {directionLabel(call.direction)}
                        </span>
                      </td>
                      <td data-label="Target change" className="numeric mono">
                        {formatMoney(call.previousTarget, call.currency)} → {formatMoney(call.target, call.currency)}
                      </td>
                      <td data-label="Evidence">
                        <Link className="source-link" href={`/calls/${call.callId}#source`}>
                          {source.document.title}
                        </Link>
                        <span className="cell-secondary">
                          {source.document.publisher ?? "NA"} · {call.status} · {call.dataMode}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="dataset-disclaimer dashboard-calls-disclaimer">
            {snapshot.latestCalls.disclaimer}
          </p>
        </section>

        <section className="data-section dashboard-maps" aria-labelledby="dashboard-maps-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Independent fixture evidence</p>
              <h2 id="dashboard-maps-title">PRICE_CHANGE map previews</h2>
            </div>
            <span>2 DEMO map fixtures</span>
          </div>
          <p className="section-note dashboard-map-reuse-note" role="note">
            The two previews reuse {sharedTickerCount} stored synthetic ticker cells. Cross-universe
            overlap is demonstration evidence only and does not assert official membership in
            either index.
          </p>
          <div className="dashboard-map-grid">
            {snapshot.mapPreviews.map((map) => (
              <MarketMapPreview key={map.universe} snapshot={map} />
            ))}
          </div>
        </section>

        <div className="dashboard-deferred-grid">
          <UnavailableSection
            id="event-calendar"
            eyebrow="Global event calendar"
            title="Scheduled events"
            state={snapshot.eventCalendar}
          >
            <p>
              No global event-calendar read model is published. Call-linked scheduled context stays
              attached to its historical call and is not presented as a current calendar.
            </p>
          </UnavailableSection>

          <UnavailableSection
            id="ranking-preview"
            eyebrow="Deterministic outcomes"
            title="Ranking preview"
            state={snapshot.ranking}
          >
            <p>
              No accuracy, return, alpha, hit-rate, score, rank, sample count, or ordering is
              calculated. Deterministic ranking work remains deferred to P3.
            </p>
            <Link className="text-action" href="/methodology">Review methodology evidence</Link>
          </UnavailableSection>
        </div>
      </div>
    </main>
  );
}
