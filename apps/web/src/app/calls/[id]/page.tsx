import Link from "next/link";
import { notFound } from "next/navigation";
import { SiteHeader } from "@/components/site-header";
import { formatMoney } from "@/lib/format-money";
import { getLocale } from "@/lib/i18n/server";
import { callsProvider, type AnalystCallDetail } from "@/lib/providers";
import { getCallsMessages, type CallsMessages } from "../messages";
import { CallContextSections } from "./call-context-sections";

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

function delay(
  eventTime: string,
  processingTime: string,
  messages: CallsMessages["detail"],
) {
  const milliseconds = new Date(processingTime).getTime() - new Date(eventTime).getTime();
  return milliseconds >= 0 ? messages.delayMinutes(Math.round(milliseconds / 60_000)) : "NA";
}

function sourceLocation(
  page: number | null,
  startMs: number | null,
  endMs: number | null,
  messages: CallsMessages["detail"],
) {
  const parts: string[] = [];

  if (page !== null) {
    parts.push(messages.pageLocation(page));
  }
  if (startMs !== null && endMs !== null) {
    parts.push(`${startMs}–${endMs} ms`);
  } else if (startMs !== null) {
    parts.push(messages.fromLocation(startMs));
  } else if (endMs !== null) {
    parts.push(messages.untilLocation(endMs));
  }

  return parts.length > 0 ? parts.join(" · ") : "NA";
}

export default async function CallDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const [{ id }, locale] = await Promise.all([params, getLocale()]);
  const messages = getCallsMessages(locale).detail;
  const provider = callsProvider();
  const [detail, context] = await Promise.all([
    provider.findById(id),
    provider.findContextByCallId(id),
  ]);

  if (!detail) {
    notFound();
  }

  const { call, institution, analyst, asset, source, snapshot } = detail;
  const snapshotMetrics = snapshot
    ? [
        [messages.assetPrice, formatMoney(snapshot.assetPrice, call.currency)],
        ["S&P 500", number(snapshot.spx)],
        ["Nasdaq 100", number(snapshot.ndx)],
        ["VIX", number(snapshot.vix)],
        [messages.treasury2y, number(snapshot.treasury2y, { style: "percent" })],
        [messages.treasury10y, number(snapshot.treasury10y, { style: "percent" })],
        [messages.realYield, number(snapshot.realYield, { style: "percent" })],
        ["DXY", number(snapshot.dxy)],
        ["WTI", number(snapshot.wti)],
        [messages.gold, number(snapshot.gold)],
        [messages.volatility, number(snapshot.volatility)],
        [messages.distance52WeekHigh, number(snapshot.distanceFrom52WeekHigh, { style: "percent" })],
        [messages.distanceAth, number(snapshot.distanceFromAth, { style: "percent" })],
      ]
    : [];

  return (
    <main>
      <SiteHeader current="calls" dataMode={call.dataMode} />
      <div className="page-shell call-detail-shell">
        <Link className="back-link" href="/calls">{messages.back}</Link>

        <section className="detail-heading" aria-labelledby="call-title">
          <div>
            <p className="eyebrow">{messages.canonicalCall(call.callId)}</p>
            <h1 id="call-title">{messages.callTitle(institution.canonicalName, asset.ticker ?? "NA")}</h1>
            <p className="page-summary">{analyst?.canonicalName ?? messages.analystUnavailable} · {asset.canonicalName}</p>
          </div>
          <div className="status-cluster" aria-label={messages.callStatusLabel}>
            <span className="mode-badge">{call.dataMode}</span>
            <span className={`direction direction-${call.direction.toLowerCase()}`}>
              {call.direction.replaceAll("_", " ")}
            </span>
            <span className="record-status">{call.status}</span>
          </div>
        </section>

        <dl className="provenance-strip detail-provenance" aria-label={messages.recordProvenanceLabel}>
          <div>
            <dt>{messages.asOf}</dt>
            <dd>{utc(call.capturedAt)}</dd>
          </div>
          <div>
            <dt>{messages.dataMode}</dt>
            <dd>{call.dataMode}</dd>
          </div>
          <div>
            <dt>{messages.provenance}</dt>
            <dd>{call.provenanceId}</dd>
          </div>
          <div>
            <dt>{messages.providerEvent}</dt>
            <dd>{call.providerEventId}</dd>
          </div>
        </dl>

        <div className="detail-grid">
          <section className="detail-section" aria-labelledby="event-record-title">
            <div className="section-heading">
              <div>
                <p className="eyebrow">{messages.eventRecordEyebrow}</p>
                <h2 id="event-record-title">{messages.callFacts}</h2>
              </div>
            </div>
            <dl className="fact-grid">
              <div><dt>{messages.eventTime}</dt><dd className="mono">{utc(call.eventTime)}</dd></div>
              <div><dt>{messages.processingTime}</dt><dd className="mono">{utc(call.processingTime)}</dd></div>
              <div><dt>{messages.processingDelay}</dt><dd>{delay(call.eventTime, call.processingTime, messages)}</dd></div>
              <div><dt>{messages.originalRating}</dt><dd>{valueOrNa(call.originalRating)}</dd></div>
              <div><dt>{messages.previousTarget}</dt><dd className="mono">{formatMoney(call.previousTarget, call.currency)}</dd></div>
              <div><dt>{messages.newTarget}</dt><dd className="mono">{formatMoney(call.target, call.currency)}</dd></div>
              <div><dt>{messages.targetChange}</dt><dd className="mono positive">{targetDelta(detail)}</dd></div>
              <div><dt>{messages.targetDate}</dt><dd>{valueOrNa(call.targetDate)}</dd></div>
            </dl>
          </section>

          <section className="detail-section" id="source" aria-labelledby="source-title">
            <div className="section-heading">
              <div>
                <p className="eyebrow">{messages.evidenceChain}</p>
                <h2 id="source-title">{messages.sourceProvenance}</h2>
              </div>
              <span>{source.reference.verified ? messages.verified : messages.unverifiedDemo}</span>
            </div>
            <dl className="fact-grid">
              <div><dt>{messages.documentId}</dt><dd className="mono">{source.document.sourceDocumentId}</dd></div>
              <div><dt>{messages.referenceId}</dt><dd className="mono">{source.reference.sourceReferenceId}</dd></div>
              <div><dt>{messages.publisher}</dt><dd>{valueOrNa(source.document.publisher)}</dd></div>
              <div><dt>{messages.sourceType}</dt><dd>{source.document.sourceType}</dd></div>
              <div><dt>{messages.title}</dt><dd>{source.document.title}</dd></div>
              <div><dt>{messages.provider}</dt><dd>{source.document.provider}</dd></div>
              <div><dt>{messages.externalId}</dt><dd className="mono">{valueOrNa(source.document.externalId)}</dd></div>
              <div><dt>{messages.published}</dt><dd className="mono">{source.document.publishedAt ? utc(source.document.publishedAt) : "NA"}</dd></div>
              <div><dt>{messages.documentCaptured}</dt><dd className="mono">{utc(source.document.capturedAt)}</dd></div>
              <div><dt>{messages.referenceCaptured}</dt><dd className="mono">{utc(source.reference.capturedAt)}</dd></div>
              <div><dt>{messages.documentDataMode}</dt><dd>{source.document.dataMode}</dd></div>
              <div><dt>{messages.referenceDataMode}</dt><dd>{source.reference.dataMode}</dd></div>
              <div><dt>{messages.documentProvenance}</dt><dd className="mono">{source.document.provenanceId}</dd></div>
              <div><dt>{messages.referenceProvenance}</dt><dd className="mono">{source.reference.provenanceId}</dd></div>
              <div><dt>{messages.license}</dt><dd>{source.document.licenseClass}</dd></div>
              <div><dt>{messages.contentHash}</dt><dd className="mono">{valueOrNa(source.document.contentHash)}</dd></div>
              <div><dt>{messages.extractedFragment}</dt><dd>{valueOrNa(source.reference.extractedFragment)}</dd></div>
              <div><dt>{messages.pageTimeOffset}</dt><dd>{sourceLocation(
                source.reference.page,
                source.reference.startMs,
                source.reference.endMs,
                messages,
              )}</dd></div>
              <div><dt>{messages.confidence}</dt><dd>{source.reference.extractionConfidence ?? "NA"}</dd></div>
            </dl>
            {source.document.canonicalUrl ? (
              <a className="source-action" href={source.document.canonicalUrl} target="_blank" rel="noreferrer">
                {messages.openCanonicalSource}
              </a>
            ) : (
              <p className="section-note source-note">{messages.canonicalSourceUnavailable}</p>
            )}
          </section>
        </div>

        <section className="detail-section snapshot-section" aria-labelledby="snapshot-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">{messages.pointInTimeContext}</p>
              <h2 id="snapshot-title">{messages.marketSnapshot}</h2>
            </div>
            <span>{snapshot?.immutable ? messages.immutablePointInTime : messages.snapshotUnavailable}</span>
          </div>
          {snapshot ? (
            <>
              <dl className="snapshot-metadata">
                <div><dt>{messages.snapshotId}</dt><dd>{snapshot.snapshotId}</dd></div>
                <div><dt>{messages.snapshotEventTime}</dt><dd className="mono">{utc(snapshot.eventTime)}</dd></div>
                <div><dt>{messages.snapshotProcessingTime}</dt><dd className="mono">{utc(snapshot.processingTime)}</dd></div>
                <div><dt>{messages.captured}</dt><dd className="mono">{utc(snapshot.capturedAt)}</dd></div>
                <div><dt>{messages.dataMode}</dt><dd>{snapshot.dataMode}</dd></div>
                <div><dt>{messages.provenance}</dt><dd className="mono">{snapshot.provenanceId}</dd></div>
                <div><dt>{messages.assetId}</dt><dd className="mono">{snapshot.assetId}</dd></div>
                <div><dt>{messages.mutationPolicy}</dt><dd>{messages.appendOnly}</dd></div>
              </dl>
              <div className="metric-grid" aria-label={messages.snapshotValuesLabel}>
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
              <h3>{messages.snapshotUnavailable}</h3>
              <p>{messages.noInventedMarketValues}</p>
            </div>
          )}
        </section>

        <CallContextSections call={call} context={context} locale={locale} />

        <section className="detail-section outcome-section" aria-labelledby="outcome-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">{messages.deterministicScoring}</p>
              <h2 id="outcome-title">{messages.outcome}</h2>
            </div>
            <span>{messages.methodologyInactive}</span>
          </div>
          <dl className="outcome-grid">
            <div><dt>{messages.directionalWin}</dt><dd>NA</dd></div>
            <div><dt>{messages.targetHit}</dt><dd>NA</dd></div>
            <div><dt>{messages.alpha}</dt><dd>NA</dd></div>
            <div><dt>{messages.methodologyVersion}</dt><dd>NA</dd></div>
          </dl>
          <p className="section-note">{messages.outcomeNote}</p>
        </section>
      </div>
    </main>
  );
}
