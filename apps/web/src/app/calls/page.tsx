import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { DATA_MODES, type DataMode } from "@/lib/data-mode";
import { formatMoney } from "@/lib/format-money";
import { callsProvider } from "@/lib/providers";
import {
  CALL_DIRECTIONS,
  CALL_SORT_FIELDS,
  CALL_STATUSES,
  type CallDirection,
  type CallStatus,
  type CallsQuery,
} from "@/lib/providers/calls-provider";

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
  const raw = await searchParams;
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
            <p className="eyebrow">Canonical event ledger</p>
            <h1 id="calls-page-title">Analyst calls</h1>
            <p className="page-summary">
              Search point-in-time call events with their canonical identities and source evidence.
            </p>
          </div>
          <dl className="provenance-strip" aria-label="Call dataset provenance">
            <div>
              <dt>As of</dt>
              <dd>{utc(metadata.asOf)}</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>{metadata.source}</dd>
            </div>
            <div>
              <dt>Mode</dt>
              <dd>{metadata.dataMode}</dd>
            </div>
          </dl>
        </section>

        <form className="calls-filters" action="/calls" method="get" aria-label="Filter analyst calls">
          <label>
            <span>Ticker</span>
            <input name="ticker" defaultValue={values.ticker} placeholder="e.g. NVDA" />
          </label>
          <label>
            <span>Asset</span>
            <select name="assetId" defaultValue={values.assetId}>
              <option value="">All assets</option>
              {metadata.facets.assets.map((asset) => (
                <option key={asset.assetId} value={asset.assetId}>
                  {asset.ticker ?? "NA"} — {asset.canonicalName}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Institution</span>
            <select name="institutionId" defaultValue={values.institutionId}>
              <option value="">All institutions</option>
              {metadata.facets.institutions.map((institution) => (
                <option key={institution.institutionId} value={institution.institutionId}>
                  {institution.canonicalName}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Analyst</span>
            <select name="analystId" defaultValue={values.analystId}>
              <option value="">All analysts</option>
              {metadata.facets.analysts.map((analyst) => (
                <option key={analyst.analystId} value={analyst.analystId}>
                  {analyst.canonicalName}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Direction</span>
            <select name="direction" defaultValue={values.direction}>
              <option value="">All directions</option>
              {metadata.facets.directions.map((candidate) => (
                <option key={candidate} value={candidate}>
                  {directionLabel(candidate)}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Status</span>
            <select name="status" defaultValue={values.status}>
              <option value="">All statuses</option>
              {metadata.facets.statuses.map((candidate) => (
                <option key={candidate} value={candidate}>{candidate}</option>
              ))}
            </select>
          </label>
          <label>
            <span>From</span>
            <input type="date" name="from" defaultValue={values.from} />
          </label>
          <label>
            <span>Through date (UTC)</span>
            <input type="date" name="to" defaultValue={values.to} />
            <small>Applied as the next day&apos;s exclusive UTC bound.</small>
          </label>
          <label>
            <span>Data mode</span>
            <select name="dataMode" defaultValue={values.dataMode}>
              <option value="">All modes</option>
              <option value="DEMO">DEMO</option>
            </select>
          </label>
          <label>
            <span>Sort by</span>
            <select name="sort" defaultValue={values.sort || "eventTime"}>
              <option value="eventTime">Event time</option>
              <option value="processingTime">Processing time</option>
              <option value="capturedAt">Captured at</option>
            </select>
          </label>
          <label>
            <span>Order</span>
            <select name="order" defaultValue={values.order || "desc"}>
              <option value="desc">Descending</option>
              <option value="asc">Ascending</option>
            </select>
          </label>
          <label>
            <span>Rows</span>
            <select name="size" defaultValue={values.size || "25"}>
              <option value="25">25</option>
              <option value="50">50</option>
              <option value="100">100</option>
            </select>
          </label>
          <div className="filter-actions">
            <button type="submit">Apply filters</button>
            <Link href="/calls">Clear</Link>
          </div>
        </form>

        <section className="data-section calls-results" aria-labelledby="results-title">
          <div className="section-heading results-heading">
            <div>
              <p className="eyebrow">Results</p>
              <h2 id="results-title">
                {result.page.totalElements} {result.page.totalElements === 1 ? "event" : "events"}
              </h2>
            </div>
            <span>
              Page {result.page.number + 1} of {Math.max(result.page.totalPages, 1)} · {result.page.sort.field},{result.page.sort.order}
            </span>
          </div>

          {result.items.length === 0 ? (
            <div className="empty-state" role="status">
              <p className="eyebrow">No matching events</p>
              <h3>Nothing matches these filters.</h3>
              <p>Clear one or more filters. Missing records are never replaced with synthetic values.</p>
              <Link className="text-action" href="/calls">Clear all filters</Link>
            </div>
          ) : (
            <div className="table-scroll calls-table-scroll" tabIndex={0} aria-label="Scrollable analyst calls results">
              <table className="calls-table">
                <caption className="visually-hidden">Filtered analyst call events</caption>
                <thead>
                  <tr>
                    <th scope="col">Event time</th>
                    <th scope="col">Institution / analyst</th>
                    <th scope="col">Asset</th>
                    <th scope="col">Direction</th>
                    <th scope="col" className="numeric">Target change</th>
                    <th scope="col">Source</th>
                  </tr>
                </thead>
                <tbody>
                  {result.items.map(({ call, institution, analyst, asset, source }) => (
                    <tr key={call.callId}>
                      <td data-label="Event time" className="mono">
                        <Link className="row-link" href={`/calls/${call.callId}`}>{utc(call.eventTime)}</Link>
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
                      <td data-label="Source">
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
            <nav className="pagination" aria-label="Calls pages">
              {result.page.first ? (
                <span aria-disabled="true">Previous</span>
              ) : (
                <Link href={pageHref(values, result.page.number - 1)}>Previous</Link>
              )}
              <span aria-current="page">{result.page.number + 1}</span>
              {result.page.last ? (
                <span aria-disabled="true">Next</span>
              ) : (
                <Link href={pageHref(values, result.page.number + 1)}>Next</Link>
              )}
            </nav>
          ) : null}
        </section>

        <p className="dataset-disclaimer">{metadata.disclaimer}</p>
      </div>
    </main>
  );
}
