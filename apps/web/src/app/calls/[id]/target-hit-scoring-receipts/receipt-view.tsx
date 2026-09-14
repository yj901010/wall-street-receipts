import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { KstTimestamp } from "@/components/kst-timestamp";
import type { Metric, ReceiptState, TargetHitScoringReceipt, ReferenceEvidence, WindowEvidence } from "@/lib/target-hit-scoring-receipts";
import { scoringMessages } from "./messages";
import styles from "./receipts.module.css";

type Messages = ReturnType<typeof scoringMessages>;
function MetricValue({ metric, messages, target = false }: { metric: Metric; messages: Messages; target?: boolean }) {
  const value = metric.state === "AVAILABLE" ? metric.decimalValue ?? (metric.booleanValue ? (target ? messages.hit : messages.yes) : (target ? messages.miss : messages.no))
    : metric.state === "PENDING" ? messages.pending : metric.state === "NOT_APPLICABLE" ? messages.na : messages.unavailableMetric;
  return <><span className={styles.value}>{value}</span>{metric.reasons.length > 0 && <small className={styles.reason}>{metric.reasons.join(" → ")}</small>}</>;
}
function Evidence({ row, messages: m }: { row: TargetHitScoringReceipt; messages: Messages }) {
  return <section aria-labelledby="receipt-evidence"><h2 id="receipt-evidence">{m.evidence}</h2>
    <dl className={styles.evidence}>
      <div><dt>{m.id}</dt><dd>{row.receiptId}</dd></div>
      <div><dt>{m.source}</dt><dd>{m.replay} · {row.source}</dd></div>
      <div><dt>{m.basis}</dt><dd>{row.basisRevisionId ?? m.original}{row.basisRevisionSequence !== null ? ` / #${row.basisRevisionSequence}` : ""}</dd></div>
      <div><dt>{m.event}</dt><dd><KstTimestamp value={row.basisEventTimeUtc} /></dd></div>
      <div><dt>{m.method}</dt><dd>{row.methodologyId} / {row.methodologyVersion}</dd></div>
      <div><dt>{m.methodHash}</dt><dd>{row.methodologyDefinitionHash}</dd></div>
      <div><dt>{m.inputHash}</dt><dd>{row.inputFingerprint}</dd></div>
      <div><dt>{m.ledgerHash}</dt><dd>{row.ledgerFingerprint}</dd></div>
      <div><dt>{m.snapshot}</dt><dd>{row.snapshotId}</dd></div>
      <div><dt>{m.snapshotSource}</dt><dd>{row.snapshotProvenanceId}</dd></div>
      <div><dt>{m.termsSource}</dt><dd>{row.termsProvenanceId}</dd></div>
      <div><dt>{m.asof} / UTC</dt><dd>{row.evaluationAsOfUtc}</dd></div>
      <div><dt>{m.recorded} / UTC</dt><dd>{row.recordedAtUtc}</dd></div>
    </dl><p className={styles.note}>{m.snapshotRole}</p></section>;
}
export function ReceiptView({ callId, selectedId, state, locale }: { callId: string; selectedId?: string; state: ReceiptState; locale: "ko" | "en" }) {
  const m = scoringMessages(locale); const base = `/calls/${encodeURIComponent(callId)}/target-hit-scoring-receipts`;
  const ready = state.kind === "ready";
  return <main><SiteHeader current="calls" dataMode="DEMO" /><div className={`page-shell ${styles.shell}`}>
    <Link href={`/calls/${encodeURIComponent(callId)}`} prefetch={false}>{m.back}</Link>
    <header className={styles.heading}><p className="eyebrow">DEMO / PARTIAL_TARGET_HIT</p><h1>{m.title}</h1><p className={styles.identifier}>{callId}</p></header>
    <aside className={styles.notice}><strong>{m.partial}</strong><p>{m.notice}</p><p>{m.precision}</p></aside>
    <form action={base} method="get" className={styles.form} key={selectedId ?? "recent"}>
      <label htmlFor="receipt-id">{m.query}</label>
      <div className={styles.controls}><input id="receipt-id" name="receiptId" maxLength={36} defaultValue={selectedId ?? ""} autoComplete="off" spellCheck={false}
        aria-describedby="receipt-query-hint" aria-invalid={state.kind === "invalid" || undefined} />
        <button type="submit">{m.submit}</button><Link href={base} prefetch={false}>{m.recent}</Link></div>
      <p id="receipt-query-hint" className={styles.note}>{m.hint}</p>
    </form>
    {!ready ? <section role={state.kind === "invalid" || state.kind === "unavailable" ? "alert" : "status"} className={styles.feedback}>
      <h2>{m[state.kind]}</h2><p>{state.kind === "disabled" ? m.disabledBody : m.failureBody}</p>
    </section> : state.items.length === 0 ? <section role="status" className={styles.feedback}><h2>{m.empty}</h2><p>{m.emptyBody}</p></section> : <>
      <section aria-labelledby="receipt-list"><h2 id="receipt-list">{state.selected ? m.selected : m.list}</h2>
        <p className={styles.note}>{m.order}</p>
        <div role="region" aria-label={m.scroll} tabIndex={0} className={styles.scroll}>
          <table className={styles.table}><thead><tr>{[m.id, m.basis, m.asof, m.recorded, m.asset, m.direction, m.error, m.benchmark, m.sector, m.targetHit].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
            <tbody>{state.items.map(row => <tr key={row.receiptId}>
              <th scope="row"><Link href={`${base}?receiptId=${row.receiptId}`} prefetch={false}>{row.receiptId}</Link></th>
              <td>{row.basisRevisionId ? m.correction : m.original}<small>{row.horizon}{row.basisRevisionSequence !== null ? ` / #${row.basisRevisionSequence}` : ""}</small></td>
              <td><KstTimestamp value={row.evaluationAsOfUtc} /></td><td><KstTimestamp value={row.recordedAtUtc} /></td>
              <td><MetricValue metric={row.assetReturn} messages={m} /></td><td><MetricValue metric={row.directionalWin} messages={m} /></td><td><MetricValue metric={row.targetError} messages={m} /></td><td><MetricValue metric={row.benchmarkReturn} messages={m} /></td><td><MetricValue metric={row.sectorReturn} messages={m} /></td><td><MetricValue metric={row.targetHit} messages={m} target /></td>
            </tr>)}</tbody></table>
        </div>{state.hasMore && <p className={styles.note}>{m.more}</p>}
      </section>{state.selected && <><Evidence row={state.items[0]} messages={m} /><Window value={state.items[0].windowEvidence} messages={m} /><p className={styles.note}>{m.referenceNotice}</p><Reference value={state.items[0].benchmarkEvidence} title={m.benchmarkEvidence} id="benchmark-evidence" messages={m} /><Reference value={state.items[0].sectorEvidence} title={m.sectorEvidence} id="sector-evidence" messages={m} /></>}
    </>}
    <details className={styles.creation}><summary>{m.creation}</summary><p>{m.creationBody}</p></details>
  </div></main>;
}

function Window({ value: r, messages: m }: {value: WindowEvidence | null; messages: Messages}) {
  const times = r ? [[m.target, r.target], [m.windowBinding, r.binding], [m.windowObservation, r.observation]] as const : [];
  return <section aria-labelledby="window-evidence"><h2 id="window-evidence">{m.windowEvidence}</h2><p className={styles.note}>{m.windowNotice}</p>
    {r === null ? <p className={styles.note}>{m.noWindow}</p> : <dl className={styles.evidence}>
      <div><dt>{m.attestation}</dt><dd>{r.attestationScope}</dd></div>
      <div><dt>{m.selectedExtreme}</dt><dd>{r.selectedField} / {r.selectedValue}</dd></div>
      <div><dt>{m.target}</dt><dd>{r.target.value} {r.target.currency}<br />{r.target.evidenceId} / {r.target.provenanceId}<br />{r.target.adjustmentBasis}</dd></div>
      <div><dt>{m.windowBinding}</dt><dd>{r.binding.bindingId} / {r.binding.revision} / {r.binding.provenanceId}<br />{r.binding.assetId} / {r.binding.primaryVenueId} / {r.binding.currency}<br />{r.binding.priceSourceId} / {r.binding.priceSourceRevision}</dd></div>
      <div><dt>{m.windowObservation}</dt><dd>{r.observation.observationId} / {r.observation.providerEventId} / {r.observation.provenanceId}<br />{r.observation.assetId} / {r.observation.venueId} / {r.observation.currency}<br />{r.observation.priceSourceId} / {r.observation.priceSourceRevision}</dd></div>
      <div><dt>{m.highLow}</dt><dd>{r.observation.windowHigh} / {r.observation.windowLow}</dd></div>
      <div><dt>{m.windowBounds}</dt><dd>{r.observation.lowerBoundType} / {r.observation.lowerBoundUtc}<br /><KstTimestamp value={r.observation.lowerBoundUtc} /><br />{r.observation.upperBoundType} / {r.observation.upperBoundUtc}<br /><KstTimestamp value={r.observation.upperBoundUtc} /></dd></div>
      <div><dt>{m.sessions}</dt><dd>{r.observation.calendarId} / {r.observation.catalogRevision}<br />{r.observation.orderedSessionIds.join(" → ")}</dd></div>
      <div><dt>{m.coverage}</dt><dd>{r.observation.priceField}<br />{r.observation.coverageCompleteness}<br />{r.observation.adjustmentBasis}<br />{r.observation.corporateActionContinuity}</dd></div>
      {times.map(([label, value]) => <div key={label}><dt>{label} · {m.availableCaptured}</dt><dd>{value.availableAtUtc}<br /><KstTimestamp value={value.availableAtUtc} /><br />{value.capturedAtUtc}<br /><KstTimestamp value={value.capturedAtUtc} /></dd></div>)}
    </dl>}</section>;
}

function Reference({ value: r, title, id, messages: m }: {value: ReferenceEvidence | null; title: string; id: string; messages: Messages}) {
  return <section aria-labelledby={id}><h2 id={id}>{title}</h2>{r === null ? <p className={styles.note}>{m.noReference}</p> : <dl className={styles.evidence}>
    <div><dt>{m.binding}</dt><dd>{r.bindingId} / {r.bindingProvenanceId}</dd></div>
    <div><dt>{m.sourceBinding}</dt><dd>{r.sourceBindingRole} / {r.sourceBindingId}</dd></div>
    <div><dt>{m.referenceAsset}</dt><dd>{r.referenceAssetId}</dd></div>
    <div><dt>{m.index}</dt><dd>{r.providerId} / {r.indexId} / {r.definitionRevision}</dd></div>
    <div><dt>{m.calendar}</dt><dd>{r.calendarId} / {r.calendarRevision}</dd></div>
    <div><dt>{m.currency}</dt><dd>{r.currency}</dd></div>
    {([["basis", m.basisLevel, r.basisLevel], ["endpoint", m.endpointLevel, r.endpointLevel]] as const).map(([key, label, l]) => {
      return <div key={key}><dt>{label}</dt><dd>{l.value}<br />{l.observedAtUtc}<br /><KstTimestamp value={l.observedAtUtc} /><br />
        {m.observation}: {l.observationId} / {l.providerEventId}<br />{m.levelSource}: {l.sourceId} / {l.sourceRevision} / {l.provenanceId}</dd></div>;
    })}
    <div><dt>{m.continuity}</dt><dd>{r.continuityEvidenceId} / {r.continuityProvenanceId}</dd></div>
  </dl>}</section>;
}
