import Link from "next/link";
import { formatMoney } from "@/lib/format-money";
import type { Sp500HistorySnapshot } from "@/lib/providers";
import { KeyboardScrollRegion } from "./keyboard-scroll-region";

function UtcTimestamp({ value }: { value: string }) {
  return <time dateTime={value}>{value}</time>;
}

function directionLabel(value: string) {
  return value.replaceAll("_", " ");
}

export function Sp500CallHistory({ snapshot }: { snapshot: Sp500HistorySnapshot }) {
  const eventLabel = snapshot.page.totalElements === 1 ? "event" : "events";
  const shownLabel = snapshot.items.length === 1 ? "row" : "rows";

  return (
    <section
      className="data-section sp500-history"
      aria-labelledby="sp500-history-title"
    >
      <div className="section-heading sp500-history-heading">
        <div>
          <p className="eyebrow">Original committed call records</p>
          <h2 id="sp500-history-title">S&amp;P 500 call-event history</h2>
        </div>
        <span>
          {snapshot.items.length} {shownLabel} shown · {snapshot.page.totalElements} matching DEMO {eventLabel}
          {" · "}incomplete fixture coverage
        </span>
      </div>

      <div className="sp500-history-policy" aria-label="S&P 500 call-history policy">
        <p className="sp500-history-policy-label">Presentation policy · not fixture evidence</p>
        <p>
          <strong>Original events.</strong> Rows are committed analyst-call events ordered by their
          recorded event time. No correction or revision is folded into a current effective view.
        </p>
        <p>
          <strong>Stored facts only.</strong> Direction, rating, targets, and status are values stored
          on each event. They are not current recommendations, prices, consensus, or performance.
        </p>
        <p>
          <strong>Incomplete DEMO fixture.</strong> Row totals describe this exact fixture query;
          they do not assert S&amp;P 500 coverage, confidence, completeness, or market trend.
        </p>
      </div>

      <dl className="sp500-history-query-evidence" aria-label="S&P 500 history query evidence">
        <div>
          <dt>Canonical asset</dt>
          <dd>{snapshot.asset.canonicalName}</dd>
        </div>
        <div>
          <dt>Asset ID</dt>
          <dd className="mono">{snapshot.asset.assetId}</dd>
        </div>
        <div>
          <dt>Ticker / type</dt>
          <dd className="mono">{snapshot.asset.ticker ?? "NA"} · {snapshot.asset.assetType}</dd>
        </div>
        <div>
          <dt>Fixed query</dt>
          <dd className="mono">asset-spx · page 0 · size 25</dd>
        </div>
        <div>
          <dt>Ordering</dt>
          <dd>Event time descending · call ID ascending tie break</dd>
        </div>
        <div>
          <dt>Fixture query page</dt>
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
          <h3>No S&amp;P 500 call events are recorded in this DEMO query.</h3>
          <p>No placeholder forecast, target, status, source, market price, or outcome was created.</p>
        </div>
      ) : (
        <KeyboardScrollRegion
          className="table-scroll calls-table-scroll sp500-history-table-scroll"
          ariaLabel="S&P 500 call-event history table"
        >
          <table className="calls-table sp500-history-table">
            <caption className="visually-hidden">
              Original committed S&amp;P 500 DEMO analyst-call events
            </caption>
            <thead>
              <tr>
                <th scope="col">Event record</th>
                <th scope="col">Institution / analyst</th>
                <th scope="col">Recorded direction / rating</th>
                <th scope="col" className="numeric">Stored targets</th>
                <th scope="col">Target date</th>
                <th scope="col">Recorded status</th>
                <th scope="col">Source evidence</th>
                <th scope="col">Processing / capture evidence</th>
              </tr>
            </thead>
            <tbody>
              {snapshot.items.map(({ call, institution, analyst, source }) => (
                <tr key={call.callId}>
                  <td data-label="Event record" className="mono">
                    <Link className="row-link" href={`/calls/${call.callId}`}>
                      <UtcTimestamp value={call.eventTime} />
                    </Link>
                    <span className="cell-secondary">{call.callId}</span>
                  </td>
                  <td data-label="Institution / analyst">
                    <strong>{institution.canonicalName}</strong>
                    <span className="cell-secondary">{analyst?.canonicalName ?? "NA"}</span>
                  </td>
                  <td data-label="Recorded direction / rating">
                    <span className={`direction direction-${call.direction.toLowerCase()}`}>
                      {directionLabel(call.direction)}
                    </span>
                    <span className="cell-secondary">{call.originalRating ?? "NA"}</span>
                  </td>
                  <td data-label="Stored targets" className="numeric mono">
                    <span className="sp500-history-target-range">
                      {formatMoney(call.previousTarget, call.currency)} → {formatMoney(call.target, call.currency)}
                    </span>
                    <span className="cell-secondary">Currency: {call.currency ?? "NA"}</span>
                  </td>
                  <td data-label="Target date" className="mono">
                    {call.targetDate ?? "NA"}
                  </td>
                  <td data-label="Recorded status" className="mono sp500-history-recorded-status">
                    {call.status}
                  </td>
                  <td data-label="Source evidence">
                    <Link className="source-link" href={`/calls/${call.callId}#source`}>
                      {source.document.title}
                    </Link>
                    <span className="cell-secondary">
                      {source.document.publisher ?? "NA"} · Verified: {String(source.reference.verified)}
                    </span>
                  </td>
                  <td data-label="Processing / capture evidence" className="mono">
                    <UtcTimestamp value={call.processingTime} />
                    <span className="cell-secondary">
                      Captured <UtcTimestamp value={call.capturedAt} />
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
          Open filtered call ledger
        </Link>
        <Link className="text-action" href="/market">Return to market publication status</Link>
      </div>
    </section>
  );
}
