import Link from "next/link";
import { KstTimestamp } from "@/components/kst-timestamp";
import { SiteHeader } from "@/components/site-header";
import { formatMoney } from "@/lib/format-money";
import { getLocale } from "@/lib/i18n/server";
import { callListProvider } from "@/lib/providers/call-list-provider.server";
import {
  parseCallListSearchParams,
  type CallListFilterValues,
  type CallListSearchParams,
} from "@/lib/providers/call-list-query";
import {
  CALL_DIRECTIONS,
  CALL_STATUSES,
  type CallDirection,
} from "@/lib/providers/calls-provider";
import { getCallsMessages } from "./messages";

function directionLabel(value: CallDirection) {
  return value.replaceAll("_", " ");
}

function pageHref(values: CallListFilterValues, page: number) {
  const params = new URLSearchParams();

  Object.entries(values).forEach(([key, value]) => {
    if (value) {
      params.set(key, value);
    }
  });
  params.set("page", String(page));

  return `/calls?${params.toString()}`;
}

export default async function CallsPage({
  searchParams,
}: {
  searchParams: Promise<CallListSearchParams>;
}) {
  const [raw, locale] = await Promise.all([searchParams, getLocale()]);
  const messages = getCallsMessages(locale).list;
  const { query, values } = parseCallListSearchParams(raw);
  const result = await callListProvider().list(query);
  const isOutOfRangePage = result.items.length === 0 && result.page.totalElements > 0;

  return (
    <main>
      <SiteHeader current="calls" dataMode={result.dataMode} />
      <div className="page-shell calls-shell">
        <section className="page-heading calls-heading" aria-labelledby="calls-page-title">
          <div>
            <p className="eyebrow">{messages.eyebrow}</p>
            <h1 id="calls-page-title">{messages.title}</h1>
            <p className="page-summary">{messages.summary}</p>
          </div>
          <dl className="provenance-strip" aria-label={messages.returnedPageEvidenceLabel}>
            <div>
              <dt>{messages.latestReturnedCapture}</dt>
              <dd>{result.returnedPageEvidence.latestCallCapturedAt
                ? <KstTimestamp value={result.returnedPageEvidence.latestCallCapturedAt} />
                : "NA"}</dd>
            </div>
            <div>
              <dt>{messages.returnedCallProvenance}</dt>
              <dd>{result.returnedPageEvidence.callProvenanceIds.join(", ") || "NA"}</dd>
            </div>
            <div>
              <dt>{messages.mode}</dt>
              <dd>{result.dataMode}</dd>
            </div>
          </dl>
          <p className="section-note">{messages.returnedPageEvidenceNote}</p>
        </section>

        <section className="data-section calls-dataset-evidence" aria-label={messages.provenanceLabel}>
          <dl className="provenance-strip">
            <div>
              <dt>{messages.asOf}</dt>
              <dd>{result.datasetEvidence.availability === "AVAILABLE"
                ? <KstTimestamp value={result.datasetEvidence.asOf} />
                : "NA"}</dd>
            </div>
            <div>
              <dt>{messages.source}</dt>
              <dd>{result.datasetEvidence.availability === "AVAILABLE"
                ? result.datasetEvidence.source
                : "NA"}</dd>
            </div>
            <div>
              <dt>{messages.datasetMetadata}</dt>
              <dd>{result.datasetEvidence.availability === "AVAILABLE"
                ? messages.available
                : messages.notExposed}</dd>
            </div>
          </dl>
          <p className="dataset-disclaimer">
            {result.datasetEvidence.availability === "AVAILABLE"
              ? result.datasetEvidence.disclaimer
              : messages.datasetNotExposed(result.datasetEvidence.reason)}
          </p>
        </section>

        <form className="calls-filters" action="/calls" method="get" aria-label={messages.filterLabel}>
          <label>
            <span>{messages.tickerFilter}</span>
            <input name="ticker" defaultValue={values.ticker} placeholder={messages.tickerPlaceholder} />
          </label>
          <label>
            <span>{messages.assetIdFilter}</span>
            <input name="assetId" defaultValue={values.assetId} placeholder={messages.assetIdPlaceholder} />
          </label>
          <label>
            <span>{messages.institutionIdFilter}</span>
            <input
              name="institutionId"
              defaultValue={values.institutionId}
              placeholder={messages.institutionIdPlaceholder}
            />
          </label>
          <label>
            <span>{messages.analystIdFilter}</span>
            <input name="analystId" defaultValue={values.analystId} placeholder={messages.analystIdPlaceholder} />
          </label>
          <label>
            <span>{messages.direction}</span>
            <select name="direction" defaultValue={values.direction}>
              <option value="">{messages.allDirections}</option>
              {CALL_DIRECTIONS.map((candidate) => (
                <option key={candidate} value={candidate}>
                  {directionLabel(candidate)}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>{messages.status}</span>
            <select name="status" defaultValue={values.status}>
              <option value="">{messages.allStatuses}</option>
              {CALL_STATUSES.map((candidate) => (
                <option key={candidate} value={candidate}>{candidate}</option>
              ))}
            </select>
          </label>
          <label>
            <span>{messages.from}</span>
            <input type="date" name="from" defaultValue={values.from} />
          </label>
          <label>
            <span>{messages.throughDate}</span>
            <input type="date" name="to" defaultValue={values.to} />
            <small>{messages.throughDateNote}</small>
          </label>
          <label>
            <span>{messages.sortBy}</span>
            <select name="sort" defaultValue={values.sort || "eventTime"}>
              <option value="eventTime">{messages.eventTime}</option>
              <option value="processingTime">{messages.processingTime}</option>
              <option value="capturedAt">{messages.capturedAt}</option>
            </select>
          </label>
          <label>
            <span>{messages.order}</span>
            <select name="order" defaultValue={values.order || "desc"}>
              <option value="desc">{messages.descending}</option>
              <option value="asc">{messages.ascending}</option>
            </select>
          </label>
          <label>
            <span>{messages.rows}</span>
            <input name="size" type="number" min="1" max="100" defaultValue={values.size || "25"} />
          </label>
          <div className="filter-actions">
            <button type="submit">{messages.applyFilters}</button>
            <Link href="/calls">{messages.clear}</Link>
          </div>
        </form>

        <section className="data-section calls-results" aria-labelledby="results-title">
          <div className="section-heading results-heading">
            <div>
              <p className="eyebrow">{messages.results}</p>
              <h2 id="results-title">{messages.eventCount(result.page.totalElements)}</h2>
            </div>
            <span>{messages.pageStatus(
              result.page.number,
              result.page.totalPages,
              result.page.sort.field,
              result.page.sort.order,
            )}</span>
          </div>

          {result.items.length === 0 ? (
            <div className="empty-state" role="status">
              <p className="eyebrow">
                {isOutOfRangePage ? messages.outOfRangeEyebrow : messages.emptyEyebrow}
              </p>
              <h3>{isOutOfRangePage ? messages.outOfRangeTitle : messages.emptyTitle}</h3>
              <p>
                {isOutOfRangePage
                  ? messages.outOfRangeDescription(result.page.totalElements)
                  : messages.emptyDescription}
              </p>
              <Link className="text-action" href="/calls">{messages.clearAll}</Link>
            </div>
          ) : (
            <div className="table-scroll calls-table-scroll" tabIndex={0} aria-label={messages.resultsRegionLabel}>
              <table className="calls-table">
                <caption className="visually-hidden">{messages.tableCaption}</caption>
                <thead>
                  <tr>
                    <th scope="col">{messages.eventTime}</th>
                    <th scope="col">{messages.institutionAnalyst}</th>
                    <th scope="col">{messages.asset}</th>
                    <th scope="col">{messages.direction}</th>
                    <th scope="col" className="numeric">{messages.targetChange}</th>
                    <th scope="col">{messages.source}</th>
                  </tr>
                </thead>
                <tbody>
                  {result.items.map(({ call, institution, analyst, asset, source }) => (
                    <tr key={call.callId}>
                      <td data-field="event-time" data-label={messages.eventTime} className="mono">
                        <Link className="row-link" href={`/calls/${call.callId}`}>
                          <KstTimestamp value={call.eventTime} />
                        </Link>
                      </td>
                      <td data-field="institution-analyst" data-label={messages.institutionAnalyst}>
                        <strong>{institution.canonicalName}</strong>
                        <span className="cell-secondary">{analyst?.canonicalName ?? "NA"}</span>
                      </td>
                      <td data-field="asset" data-label={messages.asset}>
                        <strong>{asset.ticker ?? "NA"}</strong>
                        <span className="cell-secondary">{asset.canonicalName}</span>
                      </td>
                      <td data-field="direction" data-label={messages.direction}>
                        <span className={`direction direction-${call.direction.toLowerCase()}`}>
                          {directionLabel(call.direction)}
                        </span>
                      </td>
                      <td data-field="target-change" data-label={messages.targetChange} className="numeric mono">
                        {formatMoney(call.previousTarget, call.currency)} → {formatMoney(call.target, call.currency)}
                      </td>
                      <td data-field="source" data-label={messages.source}>
                        <Link className="source-link" href={`/calls/${call.callId}#source`}>
                          {source.document.title}
                        </Link>
                        <span className="cell-secondary">{source.document.publisher ?? "NA"} · {call.status}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {result.page.totalPages > 1 ? (
            <nav className="pagination" aria-label={messages.callsPagesLabel}>
              {result.page.first ? (
                <span aria-disabled="true">{messages.previous}</span>
              ) : (
                <Link href={pageHref(values, result.page.number - 1)}>{messages.previous}</Link>
              )}
              <span aria-current="page">{result.page.number + 1}</span>
              {result.page.last ? (
                <span aria-disabled="true">{messages.next}</span>
              ) : (
                <Link href={pageHref(values, result.page.number + 1)}>{messages.next}</Link>
              )}
            </nav>
          ) : null}
        </section>

      </div>
    </main>
  );
}
