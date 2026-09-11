import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { KstTimestamp } from "@/components/kst-timestamp";
import type { Metric, ReceiptState, ScoringReceipt } from "@/lib/scoring-receipts";
import { scoringMessages } from "./messages";
import styles from "./receipts.module.css";

type Messages = ReturnType<typeof scoringMessages>;
function MetricValue({ metric, messages }: { metric: Metric; messages: Messages }) {
  const value = metric.state === "AVAILABLE" ? metric.decimalValue ?? (metric.booleanValue ? messages.yes : messages.no)
    : metric.state === "PENDING" ? messages.pending : metric.state === "NOT_APPLICABLE" ? messages.na : messages.unavailableMetric;
  return <><span className={styles.value}>{value}</span>{metric.reasons.length > 0 && <small className={styles.reason}>{metric.reasons.join(" → ")}</small>}</>;
}
function Evidence({ row, messages: m }: { row: ScoringReceipt; messages: Messages }) {
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
  const m = scoringMessages(locale); const base = `/calls/${encodeURIComponent(callId)}/scoring-receipts`;
  const ready = state.kind === "ready";
  return <main><SiteHeader current="calls" dataMode="DEMO" /><div className={`page-shell ${styles.shell}`}>
    <Link href={`/calls/${encodeURIComponent(callId)}`} prefetch={false}>{m.back}</Link>
    <header className={styles.heading}><p className="eyebrow">DEMO / PARTIAL_ENDPOINT</p><h1>{m.title}</h1><p className={styles.identifier}>{callId}</p></header>
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
          <table className={styles.table}><thead><tr>{[m.id, m.basis, m.asof, m.recorded, m.asset, m.direction, m.error].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
            <tbody>{state.items.map(row => <tr key={row.receiptId}>
              <th scope="row"><Link href={`${base}?receiptId=${row.receiptId}`} prefetch={false}>{row.receiptId}</Link></th>
              <td>{row.basisRevisionId ? m.correction : m.original}<small>{row.horizon}{row.basisRevisionSequence !== null ? ` / #${row.basisRevisionSequence}` : ""}</small></td>
              <td><KstTimestamp value={row.evaluationAsOfUtc} /></td><td><KstTimestamp value={row.recordedAtUtc} /></td>
              <td><MetricValue metric={row.assetReturn} messages={m} /></td><td><MetricValue metric={row.directionalWin} messages={m} /></td><td><MetricValue metric={row.targetError} messages={m} /></td>
            </tr>)}</tbody></table>
        </div>{state.hasMore && <p className={styles.note}>{m.more}</p>}
      </section>{state.selected && <Evidence row={state.items[0]} messages={m} />}
    </>}
    <details className={styles.creation}><summary>{m.creation}</summary><p>{m.creationBody}</p></details>
  </div></main>;
}
