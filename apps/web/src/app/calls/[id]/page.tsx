import Link from "next/link";
import { notFound } from "next/navigation";
import { KstTimestamp } from "@/components/kst-timestamp";
import { SiteHeader } from "@/components/site-header";
import { formatMoney } from "@/lib/format-money";
import { getLocale } from "@/lib/i18n/server";
import { callAuditProvider } from "@/lib/providers/call-audit-provider.server";
import type { AnalystCallDetail } from "@/lib/providers";
import { getCallsMessages, type CallsMessages } from "../messages";
import { CallContextSections } from "./call-context-sections";

function valueOrNa(value: string | number | null) {
  return value ?? "NA";
}

function rawNumberOrNa(value: number | null) {
  return value === null ? "NA" : String(value);
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
  const audit = await callAuditProvider().findById(id);

  if (!audit) {
    notFound();
  }

  const { detail, context, revisions, outcomes } = audit;
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
            <dd><KstTimestamp value={call.capturedAt} /></dd>
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
              <div><dt>{messages.eventTime}</dt><dd className="mono"><KstTimestamp value={call.eventTime} /></dd></div>
              <div><dt>{messages.processingTime}</dt><dd className="mono"><KstTimestamp value={call.processingTime} /></dd></div>
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
              <div><dt>{messages.published}</dt><dd className="mono">{source.document.publishedAt ? <KstTimestamp value={source.document.publishedAt} /> : "NA"}</dd></div>
              <div><dt>{messages.documentCaptured}</dt><dd className="mono"><KstTimestamp value={source.document.capturedAt} /></dd></div>
              <div><dt>{messages.referenceCaptured}</dt><dd className="mono"><KstTimestamp value={source.reference.capturedAt} /></dd></div>
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

        <section className="detail-section revision-section" aria-labelledby="revision-history-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">{messages.revisionHistoryEyebrow}</p>
              <h2 id="revision-history-title">{messages.revisionHistory}</h2>
            </div>
            <span className="mono">{messages.revisionCount(revisions.length)}</span>
          </div>
          <p className="section-note revision-policy">{messages.revisionAppendOnly}</p>
          {revisions.length === 0 ? (
            <div className="empty-state revision-empty" role="status">
              <h3>{messages.noRevisionsTitle}</h3>
              <p>{messages.noRevisionsDescription}</p>
            </div>
          ) : (
            <ol className="revision-timeline">
              {revisions.map((revision) => (
                <li key={revision.revisionId}>
                  <article aria-label={messages.revisionItemLabel(revision.sequenceNumber, revision.revisionType)}>
                    <div className="revision-heading">
                      <div>
                        <p className="eyebrow">#{revision.sequenceNumber}</p>
                        <h3>{revision.revisionType}</h3>
                      </div>
                      <span className="mode-badge">{revision.dataMode}</span>
                    </div>
                    <dl className="revision-evidence-grid">
                      <div><dt>{messages.revisionId}</dt><dd className="mono">{revision.revisionId}</dd></div>
                      <div><dt>{messages.revisionSchema}</dt><dd className="mono">{revision.schemaVersion}</dd></div>
                      <div><dt>{messages.revisionCallId}</dt><dd className="mono">{revision.callId}</dd></div>
                      <div><dt>{messages.revisionSequence}</dt><dd className="mono">{revision.sequenceNumber}</dd></div>
                      <div><dt>{messages.revisionType}</dt><dd>{revision.revisionType}</dd></div>
                      <div><dt>{messages.supersedesRevision}</dt><dd className="mono">{valueOrNa(revision.supersedesRevisionId)}</dd></div>
                      <div><dt>{messages.revisionEventTime}</dt><dd className="mono"><KstTimestamp value={revision.eventTime} /></dd></div>
                      <div><dt>{messages.revisionProcessingTime}</dt><dd className="mono"><KstTimestamp value={revision.processingTime} /></dd></div>
                      <div><dt>{messages.revisionCapturedAt}</dt><dd className="mono"><KstTimestamp value={revision.capturedAt} /></dd></div>
                      <div><dt>{messages.revisionProvider}</dt><dd>{revision.provider}</dd></div>
                      <div><dt>{messages.revisionProviderEvent}</dt><dd className="mono">{revision.providerEventId}</dd></div>
                      <div><dt>{messages.revisionSourceReference}</dt><dd className="mono">{revision.sourceReferenceId}</dd></div>
                      <div><dt>{messages.revisionDataMode}</dt><dd>{revision.dataMode}</dd></div>
                      <div><dt>{messages.revisionProvenance}</dt><dd className="mono">{revision.provenanceId}</dd></div>
                      <div className="revision-reason"><dt>{messages.revisionReason}</dt><dd>{revision.reason}</dd></div>
                    </dl>
                    {revision.correctedTerms ? (
                      <div className="revision-terms">
                        <h4>{messages.correctedTerms}</h4>
                        <dl className="revision-evidence-grid" aria-label={messages.correctionTermsLabel}>
                          <div><dt>{messages.correctedDirection}</dt><dd>{revision.correctedTerms.direction}</dd></div>
                          <div><dt>{messages.correctedRating}</dt><dd>{valueOrNa(revision.correctedTerms.originalRating)}</dd></div>
                          <div><dt>{messages.correctedPreviousTarget}</dt><dd className="mono">{rawNumberOrNa(revision.correctedTerms.previousTarget)}</dd></div>
                          <div><dt>{messages.correctedTarget}</dt><dd className="mono">{rawNumberOrNa(revision.correctedTerms.target)}</dd></div>
                          <div><dt>{messages.correctedCurrency}</dt><dd className="mono">{valueOrNa(revision.correctedTerms.currency)}</dd></div>
                          <div><dt>{messages.correctedTargetDate}</dt><dd className="mono">{valueOrNa(revision.correctedTerms.targetDate)}</dd></div>
                        </dl>
                      </div>
                    ) : (
                      <p className="section-note revision-cancellation-note">{messages.cancellationTermsUnavailable}</p>
                    )}
                  </article>
                </li>
              ))}
            </ol>
          )}
        </section>

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
                <div><dt>{messages.snapshotEventTime}</dt><dd className="mono"><KstTimestamp value={snapshot.eventTime} /></dd></div>
                <div><dt>{messages.snapshotProcessingTime}</dt><dd className="mono"><KstTimestamp value={snapshot.processingTime} /></dd></div>
                <div><dt>{messages.captured}</dt><dd className="mono"><KstTimestamp value={snapshot.capturedAt} /></dd></div>
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

        <section className="detail-section outcome-section" aria-labelledby="outcome-title" tabIndex={0}>
          <div className="section-heading">
            <div>
              <p className="eyebrow">{messages.outcomeAuditEyebrow}</p>
              <h2 id="outcome-title">{messages.outcome}</h2>
            </div>
            <span>{messages.outcomeBoundary}</span>
          </div>
          <p className="section-note outcome-policy">{messages.outcomeAppendOnly}</p>
          <p className="section-note outcome-policy">{messages.outcomeNullPolicy}</p>
          <p className="section-note outcome-policy">{messages.outcomeNoCancellationInference}</p>
          <p className="mono outcome-count">{messages.outcomeCount(outcomes.length)}</p>
          {outcomes.length === 0 ? (
            <div className="empty-state outcome-empty" role="status">
              <h3>{messages.noOutcomesTitle}</h3>
              <p>{messages.noOutcomesDescription}</p>
            </div>
          ) : (
            <ol className="outcome-timeline">
              {outcomes.map((outcome) => (
                <li key={outcome.outcomeId}>
                  <article aria-label={messages.outcomeItemLabel(
                    outcome.sequenceNumber,
                    outcome.horizon,
                    outcome.methodologyVersion,
                    outcome.evaluationStatus,
                  )}>
                    <div className="outcome-heading">
                      <div>
                        <p className="eyebrow">{outcome.horizon} · #{outcome.sequenceNumber}</p>
                        <h3>{outcome.evaluationStatus}</h3>
                      </div>
                      <span className="mode-badge">{outcome.dataMode}</span>
                    </div>
                    <dl className="outcome-evidence-grid">
                      <div><dt>{messages.outcomeId}</dt><dd className="mono">{outcome.outcomeId}</dd></div>
                      <div><dt>{messages.outcomeSchemaVersion}</dt><dd className="mono">{outcome.schemaVersion}</dd></div>
                      <div><dt>{messages.outcomeCallId}</dt><dd className="mono">{outcome.callId}</dd></div>
                      <div><dt>{messages.outcomeHorizon}</dt><dd>{outcome.horizon}</dd></div>
                      <div><dt>{messages.outcomeBasisRevision}</dt><dd className="mono">{valueOrNa(outcome.basisRevisionId)}</dd></div>
                      <div><dt>{messages.outcomeCancellationRevision}</dt><dd className="mono">{valueOrNa(outcome.cancellationRevisionId)}</dd></div>
                      <div><dt>{messages.outcomeSnapshotId}</dt><dd className="mono">{valueOrNa(outcome.snapshotId)}</dd></div>
                      <div><dt>{messages.methodologyId}</dt><dd className="mono">{outcome.methodologyId}</dd></div>
                      <div><dt>{messages.methodologyVersion}</dt><dd className="mono">{outcome.methodologyVersion}</dd></div>
                      <div className="outcome-wide"><dt>{messages.methodologyDefinitionHash}</dt><dd className="mono">{outcome.methodologyDefinitionHash}</dd></div>
                      <div className="outcome-wide"><dt>{messages.inputFingerprint}</dt><dd className="mono">{outcome.inputFingerprint}</dd></div>
                      <div><dt>{messages.outcomeSequence}</dt><dd className="mono">{outcome.sequenceNumber}</dd></div>
                      <div><dt>{messages.supersedesOutcome}</dt><dd className="mono">{valueOrNa(outcome.supersedesOutcomeId)}</dd></div>
                      <div><dt>{messages.evaluationStatus}</dt><dd>{outcome.evaluationStatus}</dd></div>
                      <div><dt>{messages.reasonCode}</dt><dd>{outcome.reasonCode}</dd></div>
                      <div><dt>{messages.outcomeEventTime}</dt><dd className="mono"><KstTimestamp value={outcome.eventTime} /></dd></div>
                      <div><dt>{messages.outcomeProcessingTime}</dt><dd className="mono"><KstTimestamp value={outcome.processingTime} /></dd></div>
                      <div><dt>{messages.outcomeCapturedAt}</dt><dd className="mono"><KstTimestamp value={outcome.capturedAt} /></dd></div>
                      <div><dt>{messages.assetReturn}</dt><dd>NA</dd></div>
                      <div><dt>{messages.benchmarkReturn}</dt><dd>NA</dd></div>
                      <div><dt>{messages.sectorReturn}</dt><dd>NA</dd></div>
                      <div><dt>{messages.alpha}</dt><dd>NA</dd></div>
                      <div><dt>{messages.sectorAlpha}</dt><dd>NA</dd></div>
                      <div><dt>{messages.mfe}</dt><dd>NA</dd></div>
                      <div><dt>{messages.mae}</dt><dd>NA</dd></div>
                      <div><dt>{messages.targetHit}</dt><dd>NA</dd></div>
                      <div><dt>{messages.directionalWin}</dt><dd>NA</dd></div>
                      <div><dt>{messages.targetError}</dt><dd>NA</dd></div>
                      <div><dt>{messages.outcomeDataComplete}</dt><dd className="mono">{String(outcome.dataComplete)}</dd></div>
                      <div><dt>{messages.outcomeDataMode}</dt><dd>{outcome.dataMode}</dd></div>
                      <div><dt>{messages.outcomeProvenance}</dt><dd className="mono">{outcome.provenanceId}</dd></div>
                    </dl>
                  </article>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </main>
  );
}
