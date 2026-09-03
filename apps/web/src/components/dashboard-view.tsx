import Link from "next/link";
import { KstTimestamp } from "@/components/kst-timestamp";
import { SiteHeader } from "@/components/site-header";
import {
  getDashboardMessages,
  type DashboardMessages,
} from "@/components/dashboard-messages";
import { formatMoney } from "@/lib/format-money";
import type { Locale } from "@/lib/i18n/config";
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

function directionLabel(value: string) {
  return value.replaceAll("_", " ");
}

function MarketMapPreview({
  snapshot,
  messages,
}: {
  snapshot: MarketTreemapSnapshot;
  messages: DashboardMessages;
}) {
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
          <h3 id={titleId}>{messages.mapPreview.title(universeLabel)}</h3>
        </div>
        <Link className="text-action" href={`/maps/${snapshot.universe}`}>
          {messages.mapPreview.open(universeLabel)}
        </Link>
      </header>

      <dl
        className="dashboard-map-provenance"
        aria-label={messages.mapPreview.provenanceLabel(universeLabel)}
      >
        <div>
          <dt>{messages.mapPreview.asOf}</dt>
          <dd><KstTimestamp value={snapshot.asOf} /></dd>
        </div>
        <div>
          <dt>{messages.mapPreview.generated}</dt>
          <dd><KstTimestamp value={snapshot.generatedAt} /></dd>
        </div>
        <div>
          <dt>{messages.mapPreview.captured}</dt>
          <dd><KstTimestamp value={snapshot.provenance.capturedAt} /></dd>
        </div>
        <div>
          <dt>{messages.mapPreview.provenance}</dt>
          <dd className="mono">{snapshot.provenance.id}</dd>
        </div>
        <div>
          <dt>{messages.mapPreview.coverage}</dt>
          <dd className="mono">
            {messages.mapPreview.coverageValue(snapshot.coverage.kind, snapshot.coverage.cellCount)}
          </dd>
        </div>
        <div>
          <dt>{messages.mapPreview.completeUniverse}</dt>
          <dd className="mono">{String(snapshot.coverage.completeUniverse)}</dd>
        </div>
        <div>
          <dt>{messages.mapPreview.storedGrouping}</dt>
          <dd className="mono">
            {messages.mapPreview.storedGroupingValue(sectorCount, industryCount)}
          </dd>
        </div>
        <div>
          <dt>{messages.mapPreview.weightBasis}</dt>
          <dd className="mono">{snapshot.coverage.weightBasis}</dd>
        </div>
        <div>
          <dt>{messages.mapPreview.areaUnit}</dt>
          <dd className="mono">{snapshot.geometry.areaUnit}</dd>
        </div>
      </dl>

      {snapshot.cells.length === 0 ? (
        <div className="empty-state dashboard-map-empty" role="status">
          <h4>{messages.mapPreview.emptyTitle(universeLabel)}</h4>
          <p>{messages.mapPreview.emptyBody}</p>
        </div>
      ) : (
        <ol
          className="dashboard-map-cells"
          aria-label={messages.mapPreview.cellsLabel(universeLabel)}
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
                    <dt>{messages.mapPreview.storedChange}</dt>
                    <dd className={`mono dashboard-map-metric dashboard-map-metric-${presentation.metricTone}`}>
                      {presentation.metricDisplay}
                    </dd>
                  </div>
                  <div>
                    <dt>{messages.mapPreview.syntheticProxy}</dt>
                    <dd className="mono">
                      {messages.mapPreview.syntheticProxyValue(
                        cell.syntheticMarketCapProxy,
                        snapshot.geometry.areaUnit,
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt>{messages.mapPreview.timestamp}</dt>
                    <dd><KstTimestamp value={cell.timestamp} /></dd>
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
  messages,
  children,
}: {
  id: string;
  eyebrow: string;
  title: string;
  state: DashboardUnavailableSection | DashboardDeferredSection;
  messages: DashboardMessages;
  children: React.ReactNode;
}) {
  const titleId = `${id}-title`;
  const statusLabel = state.status === "NOT_PUBLISHED"
    ? messages.availability.notPublished
    : messages.availability.p3Deferred;

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
          <dl aria-label={messages.availability.label(title)}>
            <div>
              <dt>{messages.availability.status}</dt>
              <dd className="mono">{state.status}</dd>
            </div>
            <div>
              <dt>{messages.availability.display}</dt>
              <dd className="mono na-value">{state.missingDisplay}</dd>
            </div>
          </dl>
        </div>
        <div className="dashboard-unavailable-copy">{children}</div>
      </div>
    </section>
  );
}

export function DashboardView({ snapshot, locale }: { snapshot: DashboardSnapshot; locale: Locale }) {
  const messages = getDashboardMessages(locale);
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
            <p className="eyebrow">{messages.page.eyebrow}</p>
            <h1 id="page-title">{messages.page.title}</h1>
            <p className="page-summary">{messages.page.summary}</p>
          </div>
        </section>

        <UnavailableSection
          id="market-board"
          eyebrow={messages.marketBoard.eyebrow}
          title={messages.marketBoard.title}
          state={snapshot.marketBoard}
          messages={messages}
        >
          <p>{messages.marketBoard.body}</p>
        </UnavailableSection>

        <section className="data-section dashboard-calls" id="calls" aria-labelledby="dashboard-calls-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">{messages.calls.eyebrow}</p>
              <h2 id="dashboard-calls-title">{messages.calls.title}</h2>
            </div>
            <span>{messages.calls.count(snapshot.latestCalls.items.length)}</span>
          </div>

          <dl className="dashboard-section-provenance" aria-label={messages.calls.provenanceLabel}>
            <div>
              <dt>{messages.calls.asOf}</dt>
              <dd><KstTimestamp value={snapshot.latestCalls.asOf} /></dd>
            </div>
            <div>
              <dt>{messages.calls.source}</dt>
              <dd className="mono">{snapshot.latestCalls.source}</dd>
            </div>
            <div>
              <dt>{messages.calls.dataMode}</dt>
              <dd className="mono">{snapshot.latestCalls.dataMode}</dd>
            </div>
            <div>
              <dt>{messages.calls.ordering}</dt>
              <dd>{messages.calls.orderingValue}</dd>
            </div>
          </dl>

          <p className="section-note dashboard-calls-note">
            {messages.calls.note}
          </p>

          {snapshot.latestCalls.items.length === 0 ? (
            <div className="empty-state" role="status">
              <h3>{messages.calls.emptyTitle}</h3>
              <p>{messages.calls.emptyBody}</p>
            </div>
          ) : (
            <div
              className="table-scroll calls-table-scroll dashboard-calls-scroll"
              tabIndex={0}
              aria-label={messages.calls.scrollLabel}
            >
              <table className="calls-table dashboard-calls-table">
                <caption className="visually-hidden">
                  {messages.calls.caption}
                </caption>
                <thead>
                  <tr>
                    <th scope="col">{messages.calls.columns.eventTime}</th>
                    <th scope="col">{messages.calls.columns.institutionAnalyst}</th>
                    <th scope="col">{messages.calls.columns.asset}</th>
                    <th scope="col">{messages.calls.columns.direction}</th>
                    <th scope="col" className="numeric">{messages.calls.columns.targetChange}</th>
                    <th scope="col">{messages.calls.columns.evidence}</th>
                  </tr>
                </thead>
                <tbody>
                  {snapshot.latestCalls.items.map(({ call, institution, analyst, asset, source }) => (
                    <tr key={call.callId}>
                      <td
                        data-field="event-time"
                        data-label={messages.calls.columns.eventTime}
                        className="mono"
                      >
                        <Link className="row-link" href={`/calls/${call.callId}`}>
                          <KstTimestamp value={call.eventTime} />
                        </Link>
                      </td>
                      <td data-field="institution-analyst" data-label={messages.calls.columns.institutionAnalyst}>
                        <strong>{institution.canonicalName}</strong>
                        <span className="cell-secondary">{analyst?.canonicalName ?? "NA"}</span>
                      </td>
                      <td data-field="asset" data-label={messages.calls.columns.asset}>
                        <strong>{asset.ticker ?? "NA"}</strong>
                        <span className="cell-secondary">{asset.canonicalName}</span>
                      </td>
                      <td data-field="direction" data-label={messages.calls.columns.direction}>
                        <span className={`direction direction-${call.direction.toLowerCase()}`}>
                          {directionLabel(call.direction)}
                        </span>
                      </td>
                      <td data-field="target-change" data-label={messages.calls.columns.targetChange} className="numeric mono">
                        {formatMoney(call.previousTarget, call.currency)} → {formatMoney(call.target, call.currency)}
                      </td>
                      <td data-field="evidence" data-label={messages.calls.columns.evidence}>
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
              <p className="eyebrow">{messages.maps.eyebrow}</p>
              <h2 id="dashboard-maps-title">{messages.maps.title}</h2>
            </div>
            <span>{messages.maps.fixtureCount(snapshot.mapPreviews.length)}</span>
          </div>
          <p className="section-note dashboard-map-reuse-note" role="note">
            {messages.maps.overlap(sharedTickerCount)}
          </p>
          <div className="dashboard-map-grid">
            {snapshot.mapPreviews.map((map) => (
              <MarketMapPreview
                key={map.universe}
                snapshot={map}
                messages={messages}
              />
            ))}
          </div>
        </section>

        <div className="dashboard-deferred-grid">
          <UnavailableSection
            id="event-calendar"
            eyebrow={messages.calendar.eyebrow}
            title={messages.calendar.title}
            state={snapshot.eventCalendar}
            messages={messages}
          >
            <p>{messages.calendar.body}</p>
          </UnavailableSection>

          <UnavailableSection
            id="ranking-preview"
            eyebrow={messages.ranking.eyebrow}
            title={messages.ranking.title}
            state={snapshot.ranking}
            messages={messages}
          >
            <p>{messages.ranking.body}</p>
            <Link className="text-action" href="/methodology">{messages.ranking.methodology}</Link>
          </UnavailableSection>
        </div>
      </div>
    </main>
  );
}
