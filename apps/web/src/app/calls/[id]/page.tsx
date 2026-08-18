import Link from "next/link";
import { notFound } from "next/navigation";
import { SiteHeader } from "@/components/site-header";
import { formatMoney } from "@/lib/format-money";
import { callsProvider, type AnalystCallDetail } from "@/lib/providers";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

function valueOrNa(value: string | number | null) {
  return value ?? "NA";
}

function number(value: number | null, options: Intl.NumberFormatOptions = {}) {
  if (value === null) {
    return "NA";
  }

  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 2, ...options }).format(value);
}

function targetDelta(detail: AnalystCallDetail) {
  const { previousTarget, target } = detail.call;

  if (previousTarget === null || target === null || previousTarget === 0 || detail.call.currency === null) {
    return "NA";
  }

  const difference = target - previousTarget;
  const percent = difference / previousTarget;
  const sign = difference > 0 ? "+" : "";

  return `${sign}${formatMoney(difference, detail.call.currency)} (${new Intl.NumberFormat("en-US", {
    style: "percent",
    maximumFractionDigits: 2,
    signDisplay: "exceptZero",
  }).format(percent)})`;
}

function delay(eventTime: string, processingTime: string) {
  const milliseconds = new Date(processingTime).getTime() - new Date(eventTime).getTime();
  return milliseconds >= 0 ? `${Math.round(milliseconds / 60_000)} minutes` : "NA";
}

function sourceLocation(page: number | null, startMs: number | null, endMs: number | null) {
  const parts: string[] = [];

  if (page !== null) {
    parts.push(`Page ${page}`);
  }
  if (startMs !== null && endMs !== null) {
    parts.push(`${startMs}–${endMs} ms`);
  } else if (startMs !== null) {
    parts.push(`From ${startMs} ms`);
  } else if (endMs !== null) {
    parts.push(`Until ${endMs} ms`);
  }

  return parts.length > 0 ? parts.join(" · ") : "NA";
}

export default async function CallDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const detail = await callsProvider().findById(id);

  if (!detail) {
    notFound();
  }

  const { call, institution, analyst, asset, source, snapshot } = detail;
  const snapshotMetrics = snapshot
    ? [
        ["Asset price", formatMoney(snapshot.assetPrice, call.currency)],
        ["S&P 500", number(snapshot.spx)],
        ["Nasdaq 100", number(snapshot.ndx)],
        ["VIX", number(snapshot.vix)],
        ["Treasury 2Y", number(snapshot.treasury2y, { style: "percent" })],
        ["Treasury 10Y", number(snapshot.treasury10y, { style: "percent" })],
        ["Real yield", number(snapshot.realYield, { style: "percent" })],
        ["DXY", number(snapshot.dxy)],
        ["WTI", number(snapshot.wti)],
        ["Gold", number(snapshot.gold)],
        ["Volatility", number(snapshot.volatility)],
        ["Distance from 52W high", number(snapshot.distanceFrom52WeekHigh, { style: "percent" })],
        ["Distance from ATH", number(snapshot.distanceFromAth, { style: "percent" })],
      ]
    : [];

  return (
    <main>
      <SiteHeader current="calls" dataMode={call.dataMode} />
      <div className="page-shell call-detail-shell">
        <Link className="back-link" href="/calls">← Back to analyst calls</Link>

        <section className="detail-heading" aria-labelledby="call-title">
          <div>
            <p className="eyebrow">Canonical analyst call · {call.callId}</p>
            <h1 id="call-title">{institution.canonicalName} on {asset.ticker ?? "NA"}</h1>
            <p className="page-summary">{analyst?.canonicalName ?? "Analyst unavailable"} · {asset.canonicalName}</p>
          </div>
          <div className="status-cluster" aria-label="Call status">
            <span className="mode-badge">{call.dataMode}</span>
            <span className={`direction direction-${call.direction.toLowerCase()}`}>
              {call.direction.replaceAll("_", " ")}
            </span>
            <span className="record-status">{call.status}</span>
          </div>
        </section>

        <dl className="provenance-strip detail-provenance" aria-label="Call record provenance">
          <div>
            <dt>As of</dt>
            <dd>{utc(call.capturedAt)}</dd>
          </div>
          <div>
            <dt>Data mode</dt>
            <dd>{call.dataMode}</dd>
          </div>
          <div>
            <dt>Provenance</dt>
            <dd>{call.provenanceId}</dd>
          </div>
          <div>
            <dt>Provider event</dt>
            <dd>{call.providerEventId}</dd>
          </div>
        </dl>

        <div className="detail-grid">
          <section className="detail-section" aria-labelledby="event-record-title">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Event record</p>
                <h2 id="event-record-title">Call facts</h2>
              </div>
            </div>
            <dl className="fact-grid">
              <div><dt>Event time</dt><dd className="mono">{utc(call.eventTime)}</dd></div>
              <div><dt>Processing time</dt><dd className="mono">{utc(call.processingTime)}</dd></div>
              <div><dt>Processing delay</dt><dd>{delay(call.eventTime, call.processingTime)}</dd></div>
              <div><dt>Original rating</dt><dd>{valueOrNa(call.originalRating)}</dd></div>
              <div><dt>Previous target</dt><dd className="mono">{formatMoney(call.previousTarget, call.currency)}</dd></div>
              <div><dt>New target</dt><dd className="mono">{formatMoney(call.target, call.currency)}</dd></div>
              <div><dt>Target change</dt><dd className="mono positive">{targetDelta(detail)}</dd></div>
              <div><dt>Target date</dt><dd>{valueOrNa(call.targetDate)}</dd></div>
            </dl>
          </section>

          <section className="detail-section" id="source" aria-labelledby="source-title">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Evidence chain</p>
                <h2 id="source-title">Source provenance</h2>
              </div>
              <span>{source.reference.verified ? "Verified" : "Unverified DEMO"}</span>
            </div>
            <dl className="fact-grid">
              <div><dt>Document ID</dt><dd className="mono">{source.document.sourceDocumentId}</dd></div>
              <div><dt>Reference ID</dt><dd className="mono">{source.reference.sourceReferenceId}</dd></div>
              <div><dt>Publisher</dt><dd>{valueOrNa(source.document.publisher)}</dd></div>
              <div><dt>Source type</dt><dd>{source.document.sourceType}</dd></div>
              <div><dt>Title</dt><dd>{source.document.title}</dd></div>
              <div><dt>Provider</dt><dd>{source.document.provider}</dd></div>
              <div><dt>External ID</dt><dd className="mono">{valueOrNa(source.document.externalId)}</dd></div>
              <div><dt>Published</dt><dd className="mono">{source.document.publishedAt ? utc(source.document.publishedAt) : "NA"}</dd></div>
              <div><dt>Document captured</dt><dd className="mono">{utc(source.document.capturedAt)}</dd></div>
              <div><dt>Reference captured</dt><dd className="mono">{utc(source.reference.capturedAt)}</dd></div>
              <div><dt>Document data mode</dt><dd>{source.document.dataMode}</dd></div>
              <div><dt>Reference data mode</dt><dd>{source.reference.dataMode}</dd></div>
              <div><dt>Document provenance</dt><dd className="mono">{source.document.provenanceId}</dd></div>
              <div><dt>Reference provenance</dt><dd className="mono">{source.reference.provenanceId}</dd></div>
              <div><dt>License</dt><dd>{source.document.licenseClass}</dd></div>
              <div><dt>Content hash</dt><dd className="mono">{valueOrNa(source.document.contentHash)}</dd></div>
              <div><dt>Extracted fragment</dt><dd>{valueOrNa(source.reference.extractedFragment)}</dd></div>
              <div><dt>Page / time offset</dt><dd>{sourceLocation(source.reference.page, source.reference.startMs, source.reference.endMs)}</dd></div>
              <div><dt>Confidence</dt><dd>{source.reference.extractionConfidence ?? "NA"}</dd></div>
            </dl>
            {source.document.canonicalUrl ? (
              <a className="source-action" href={source.document.canonicalUrl} target="_blank" rel="noreferrer">
                Open canonical source
              </a>
            ) : (
              <p className="section-note source-note">Canonical source URL: NA</p>
            )}
          </section>
        </div>

        <section className="detail-section snapshot-section" aria-labelledby="snapshot-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Point-in-time context</p>
              <h2 id="snapshot-title">Market snapshot</h2>
            </div>
            <span>{snapshot?.immutable ? "Immutable point-in-time record" : "Snapshot unavailable"}</span>
          </div>
          {snapshot ? (
            <>
              <dl className="snapshot-metadata">
                <div><dt>Snapshot ID</dt><dd>{snapshot.snapshotId}</dd></div>
                <div><dt>Snapshot event time</dt><dd className="mono">{utc(snapshot.eventTime)}</dd></div>
                <div><dt>Snapshot processing time</dt><dd className="mono">{utc(snapshot.processingTime)}</dd></div>
                <div><dt>Captured</dt><dd className="mono">{utc(snapshot.capturedAt)}</dd></div>
                <div><dt>Data mode</dt><dd>{snapshot.dataMode}</dd></div>
                <div><dt>Provenance</dt><dd className="mono">{snapshot.provenanceId}</dd></div>
                <div><dt>Asset ID</dt><dd className="mono">{snapshot.assetId}</dd></div>
                <div><dt>Mutation policy</dt><dd>Append-only; no update surface</dd></div>
              </dl>
              <div className="metric-grid" aria-label="Snapshot market values">
                {snapshotMetrics.map(([label, value]) => (
                  <div key={label}>
                    <span>{label}</span>
                    <strong className={value === "NA" ? "na-value" : "mono"}>{value}</strong>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <div className="empty-state" role="status">
              <h3>Snapshot unavailable</h3>
              <p>No market values were invented for this call.</p>
            </div>
          )}
        </section>

        <section className="detail-section outcome-section" aria-labelledby="outcome-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Deterministic scoring</p>
              <h2 id="outcome-title">Outcome</h2>
            </div>
            <span>Methodology not active</span>
          </div>
          <dl className="outcome-grid">
            <div><dt>Directional win</dt><dd>NA</dd></div>
            <div><dt>Target hit</dt><dd>NA</dd></div>
            <div><dt>Alpha</dt><dd>NA</dd></div>
            <div><dt>Methodology version</dt><dd>NA</dd></div>
          </dl>
          <p className="section-note">Outcome values remain NA until a versioned methodology is calculated. The UI never infers a score.</p>
        </section>
      </div>
    </main>
  );
}
