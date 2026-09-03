import Link from "next/link";
import { KstTimestamp } from "@/components/kst-timestamp";
import { formatMoney } from "@/lib/format-money";
import type { Locale } from "@/lib/i18n/config";
import type { Sp500HistorySnapshot } from "@/lib/providers";
import { KeyboardScrollRegion } from "./keyboard-scroll-region";
import { getSp500HistoryMessages } from "./messages";

function directionLabel(value: string) {
  return value.replaceAll("_", " ");
}

export function Sp500CallHistory({
  locale,
  snapshot,
}: {
  locale: Locale;
  snapshot: Sp500HistorySnapshot;
}) {
  const messages = getSp500HistoryMessages(locale).history;

  return (
    <section
      className="data-section sp500-history"
      aria-labelledby="sp500-history-title"
    >
      <div className="section-heading sp500-history-heading">
        <div>
          <p className="eyebrow">{messages.eyebrow}</p>
          <h2 id="sp500-history-title">{messages.title}</h2>
        </div>
        <span>{messages.countSummary(snapshot.items.length, snapshot.page.totalElements)}</span>
      </div>

      <div className="sp500-history-policy" aria-label={messages.policyLabel}>
        <p className="sp500-history-policy-label">{messages.policyEyebrow}</p>
        <p>
          <strong>{messages.originalEventsTitle}</strong> {messages.originalEventsDescription}
        </p>
        <p>
          <strong>{messages.storedFactsTitle}</strong> {messages.storedFactsDescription}
        </p>
        <p>
          <strong>{messages.incompleteFixtureTitle}</strong> {messages.incompleteFixtureDescription}
        </p>
      </div>

      <dl className="sp500-history-query-evidence" aria-label={messages.queryEvidenceLabel}>
        <div>
          <dt>{messages.canonicalAsset}</dt>
          <dd>{snapshot.asset.canonicalName}</dd>
        </div>
        <div>
          <dt>{messages.assetId}</dt>
          <dd className="mono">{snapshot.asset.assetId}</dd>
        </div>
        <div>
          <dt>{messages.tickerType}</dt>
          <dd className="mono">{snapshot.asset.ticker ?? "NA"} · {snapshot.asset.assetType}</dd>
        </div>
        <div>
          <dt>{messages.fixedQuery}</dt>
          <dd className="mono">asset-spx · page 0 · size 25</dd>
        </div>
        <div>
          <dt>{messages.ordering}</dt>
          <dd>{messages.orderingValue}</dd>
        </div>
        <div>
          <dt>{messages.fixtureQueryPage}</dt>
          <dd className="mono">
            {snapshot.page.totalPages === 0
              ? "0 / 0"
              : `${snapshot.page.number + 1} / ${snapshot.page.totalPages}`}
          </dd>
        </div>
      </dl>

      <p className="dataset-disclaimer sp500-history-disclaimer">{snapshot.disclaimer}</p>

      {snapshot.items.length === 0 ? (
        <div className="empty-state sp500-history-empty" role="status">
          <h3>{messages.emptyTitle}</h3>
          <p>{messages.emptyDescription}</p>
        </div>
      ) : (
        <KeyboardScrollRegion
          className="table-scroll calls-table-scroll sp500-history-table-scroll"
          ariaLabel={messages.tableRegionLabel}
        >
          <table className="calls-table sp500-history-table">
            <caption className="visually-hidden">{messages.tableCaption}</caption>
            <thead>
              <tr>
                <th scope="col">{messages.eventRecord}</th>
                <th scope="col">{messages.institutionAnalyst}</th>
                <th scope="col">{messages.directionRating}</th>
                <th scope="col" className="numeric">{messages.storedTargets}</th>
                <th scope="col">{messages.targetDate}</th>
                <th scope="col">{messages.recordedStatus}</th>
                <th scope="col">{messages.sourceEvidence}</th>
                <th scope="col">{messages.processingCaptureEvidence}</th>
              </tr>
            </thead>
            <tbody>
              {snapshot.items.map(({ call, institution, analyst, source }) => (
                <tr key={call.callId}>
                  <td data-field="event-record" data-label={messages.eventRecord} className="mono">
                    <Link className="row-link" href={`/calls/${call.callId}`}>
                      <KstTimestamp value={call.eventTime} />
                    </Link>
                    <span className="cell-secondary">{call.callId}</span>
                  </td>
                  <td data-field="institution-analyst" data-label={messages.institutionAnalyst}>
                    <strong>{institution.canonicalName}</strong>
                    <span className="cell-secondary">{analyst?.canonicalName ?? "NA"}</span>
                  </td>
                  <td data-field="direction-rating" data-label={messages.directionRating}>
                    <span className={`direction direction-${call.direction.toLowerCase()}`}>
                      {directionLabel(call.direction)}
                    </span>
                    <span className="cell-secondary">{call.originalRating ?? "NA"}</span>
                  </td>
                  <td data-field="stored-targets" data-label={messages.storedTargets} className="numeric mono">
                    <span className="sp500-history-target-range">
                      {formatMoney(call.previousTarget, call.currency)} → {formatMoney(call.target, call.currency)}
                    </span>
                    <span className="cell-secondary">{messages.currency}: {call.currency ?? "NA"}</span>
                  </td>
                  <td data-field="target-date" data-label={messages.targetDate} className="mono">
                    {call.targetDate ?? "NA"}
                  </td>
                  <td data-field="recorded-status" data-label={messages.recordedStatus} className="mono sp500-history-recorded-status">
                    {call.status}
                  </td>
                  <td data-field="source-evidence" data-label={messages.sourceEvidence}>
                    <Link className="source-link" href={`/calls/${call.callId}#source`}>
                      {source.document.title}
                    </Link>
                    <span className="cell-secondary">
                      {source.document.publisher ?? "NA"} · {messages.verified}: {String(source.reference.verified)}
                    </span>
                  </td>
                  <td data-field="processing-capture" data-label={messages.processingCaptureEvidence} className="mono">
                    <KstTimestamp value={call.processingTime} />
                    <span className="cell-secondary">
                      {messages.captured} <KstTimestamp value={call.capturedAt} />
                    </span>
                    <span className="cell-secondary">{call.dataMode} · {call.provenanceId}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </KeyboardScrollRegion>
      )}

      <div className="sp500-history-actions">
        <Link className="text-action" href="/calls?assetId=asset-spx">
          {messages.openFilteredLedger}
        </Link>
        <Link className="text-action" href="/market">{messages.returnMarket}</Link>
      </div>
    </section>
  );
}
