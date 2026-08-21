import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { DATA_MODES, type DataMode } from "@/lib/data-mode";
import { formatMoney } from "@/lib/format-money";
import { getLocale } from "@/lib/i18n/server";
import { callsProvider } from "@/lib/providers";
import {
  CALL_DIRECTIONS,
  CALL_SORT_FIELDS,
  CALL_STATUSES,
  type CallDirection,
  type CallStatus,
  type CallsQuery,
} from "@/lib/providers/calls-provider";
import { getCallsMessages } from "./messages";

type SearchValue = string | string[] | undefined;
type CallsSearchParams = Record<string, SearchValue>;

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function first(value: SearchValue) {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

function nonNegativeNumber(value: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : undefined;
}

function positiveNumber(value: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

function direction(value: string): CallDirection | undefined {
  return CALL_DIRECTIONS.find((candidate) => candidate === value);
}

function dataMode(value: string): DataMode | undefined {
  return DATA_MODES.find((candidate) => candidate === value);
}

function sort(value: string): CallsQuery["sort"] {
  return CALL_SORT_FIELDS.find((candidate) => candidate === value);
}

function order(value: string): CallsQuery["order"] {
  return value === "asc" || value === "desc" ? value : undefined;
}

function status(value: string): CallStatus | undefined {
  return CALL_STATUSES.find((candidate) => candidate === value);
}

function startOfDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00.000Z` : undefined;
}

function dayAfter(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return undefined;
  }

  const date = new Date(`${value}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString();
}

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

function directionLabel(value: CallDirection) {
  return value.replaceAll("_", " ");
}

function pageHref(values: Record<string, string>, page: number) {
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
  searchParams: Promise<CallsSearchParams>;
}) {
  const [raw, locale] = await Promise.all([searchParams, getLocale()]);
  const messages = getCallsMessages(locale).list;
  const values = {
    assetId: first(raw.assetId),
    ticker: first(raw.ticker),
    institutionId: first(raw.institutionId),
    analystId: first(raw.analystId),
    direction: first(raw.direction),
    status: first(raw.status),
    dataMode: first(raw.dataMode),
    from: first(raw.from),
    to: first(raw.to),
    size: first(raw.size),
    sort: first(raw.sort),
    order: first(raw.order),
  };
  const provider = callsProvider();
  const query: CallsQuery = {
    assetId: values.assetId || undefined,
    ticker: values.ticker || undefined,
    institutionId: values.institutionId || undefined,
    analystId: values.analystId || undefined,
    direction: direction(values.direction),
    status: status(values.status),
    dataMode: dataMode(values.dataMode),
    from: startOfDate(values.from),
    to: dayAfter(values.to),
    page: nonNegativeNumber(first(raw.page)),
    size: positiveNumber(values.size),
    sort: sort(values.sort),
    order: order(values.order),
  };
  const [result, metadata] = await Promise.all([provider.list(query), provider.metadata()]);

  return (
    <main>
      <SiteHeader current="calls" dataMode={metadata.dataMode} />
      <div className="page-shell calls-shell">
        <section className="page-heading calls-heading" aria-labelledby="calls-page-title">
          <div>
            <p className="eyebrow">{messages.eyebrow}</p>
            <h1 id="calls-page-title">{messages.title}</h1>
            <p className="page-summary">{messages.summary}</p>
          </div>
          <dl className="provenance-strip" aria-label={messages.provenanceLabel}>
            <div>
              <dt>{messages.asOf}</dt>
              <dd>{utc(metadata.asOf)}</dd>
            </div>
            <div>
              <dt>{messages.source}</dt>
              <dd>{metadata.source}</dd>
            </div>
            <div>
              <dt>{messages.mode}</dt>
              <dd>{metadata.dataMode}</dd>
            </div>
          </dl>
        </section>

        <form className="calls-filters" action="/calls" method="get" aria-label={messages.filterLabel}>
          <label>
            <span>{messages.ticker}</span>
            <input name="ticker" defaultValue={values.ticker} placeholder={messages.tickerPlaceholder} />
          </label>
          <label>
            <span>{messages.asset}</span>
            <select name="assetId" defaultValue={values.assetId}>
              <option value="">{messages.allAssets}</option>
              {metadata.facets.assets.map((asset) => (
                <option key={asset.assetId} value={asset.assetId}>
                  {asset.ticker ?? "NA"} — {asset.canonicalName}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>{messages.institution}</span>
            <select name="institutionId" defaultValue={values.institutionId}>
              <option value="">{messages.allInstitutions}</option>
              {metadata.facets.institutions.map((institution) => (
                <option key={institution.institutionId} value={institution.institutionId}>
                  {institution.canonicalName}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>{messages.analyst}</span>
            <select name="analystId" defaultValue={values.analystId}>
              <option value="">{messages.allAnalysts}</option>
              {metadata.facets.analysts.map((analyst) => (
                <option key={analyst.analystId} value={analyst.analystId}>
                  {analyst.canonicalName}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>{messages.direction}</span>
            <select name="direction" defaultValue={values.direction}>
              <option value="">{messages.allDirections}</option>
              {metadata.facets.directions.map((candidate) => (
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
              {metadata.facets.statuses.map((candidate) => (
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
            <span>{messages.dataMode}</span>
            <select name="dataMode" defaultValue={values.dataMode}>
              <option value="">{messages.allModes}</option>
              <option value="DEMO">DEMO</option>
            </select>
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
            <select name="size" defaultValue={values.size || "25"}>
              <option value="25">25</option>
              <option value="50">50</option>
              <option value="100">100</option>
            </select>
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
              result.page.number + 1,
              Math.max(result.page.totalPages, 1),
              result.page.sort.field,
              result.page.sort.order,
            )}</span>
          </div>

          {result.items.length === 0 ? (
            <div className="empty-state" role="status">
              <p className="eyebrow">{messages.emptyEyebrow}</p>
              <h3>{messages.emptyTitle}</h3>
              <p>{messages.emptyDescription}</p>
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
                        <Link className="row-link" href={`/calls/${call.callId}`}>{utc(call.eventTime)}</Link>
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

        <p className="dataset-disclaimer">{metadata.disclaimer}</p>
      </div>
    </main>
  );
}
