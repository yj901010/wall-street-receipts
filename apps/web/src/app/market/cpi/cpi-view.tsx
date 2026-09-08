import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { KstTimestamp } from "@/components/kst-timestamp";
import { cpiMonths, type CpiObservation } from "@/lib/cpi";
import type { CpiState } from "@/lib/cpi-provider.server";
import { cpiMessages } from "./messages";
import styles from "./cpi.module.css";

export function CpiView({ state, locale }: { state: CpiState; locale: string }) {
  const text = cpiMessages(locale);
  const snapshot = state.kind === "ready" ? state.snapshot : null;
  function value(row: CpiObservation | undefined, key: "index" | "yearOverYearPct") {
    const number = row?.[key];
    return number == null ? text.missing : `${number}${key === "yearOverYearPct" ? "%" : ""}`;
  }
  return <main>
    <SiteHeader current="market" dataMode={snapshot?.dataMode} />
    <div className={`page-shell ${styles.shell}`}>
      <Link href="/market" prefetch={false}>{text.back} →</Link>
      <header className={styles.heading}><h1>{text.title}</h1><p>{text.summary}</p></header>
      <p className={styles.notice}>{text.caution}</p>
      {!snapshot ? <section role="status"><h2>{state.kind === "disabled" ? text.disabled : text.empty}</h2><p>{state.kind === "disabled" ? text.disabledBody : text.emptyBody}</p></section> : <>
        {snapshot.dataMode === "DEMO" && <p className={styles.notice}>{text.demo}</p>}
        <p>{text.retrieved}: <KstTimestamp value={snapshot.capturedAt} /></p>
        {Date.parse(snapshot.servedAt) - Date.parse(snapshot.capturedAt) > 7 * 86400_000 && <p role="status" className={styles.notice}>{text.stale}</p>}
        <div className={styles.headlines}>{snapshot.series.map((series, i) => {
          const latest = series.observations[0];
          return <section key={series.id}><h2>{text.names[i]}</h2><p>{text.latest}: <span>{latest.month}</span></p>
            <strong className={styles.number}>{value(latest, "yearOverYearPct")}</strong><span> {text.yoy}</span>
            <p>{text.index}: {value(latest, "index")} · <code>{series.id}</code></p>
          </section>;
        })}</div>
        <h2>{text.history}</h2>
        <p>{text.method}</p>
        <div className={styles.tableWrap} role="region" aria-label={text.history} tabIndex={0}>
          <table><caption className="visually-hidden">{text.history}</caption><thead>
            <tr><th rowSpan={2} scope="col">{text.month}</th>{text.names.map(name => <th key={name} scope="colgroup" colSpan={2}>{name}</th>)}</tr>
            <tr>{[0, 1].map(i => <FragmentColumns key={i} index={text.index} yoy={text.yoy} />)}</tr>
          </thead><tbody>{cpiMonths(snapshot).map(month => <tr key={month}><th scope="row">{month}</th>
            {snapshot.series.map(series => { const row = series.observations.find(row => row.month === month); return <DataColumns key={series.id} index={value(row, "index")} yoy={value(row, "yearOverYearPct")} />; })}
          </tr>)}</tbody></table>
        </div>
        <details className={styles.evidence}><summary>{text.notes}</summary>{snapshot.series.map((series, i) => <section key={series.id}><h3>{text.names[i]}</h3>
          {series.observations.filter(row => row.footnotes.length).map(row => <p key={row.month}>{row.month}: {row.footnotes.join(" · ")}</p>)}
          {!series.observations.some(row => row.footnotes.length) && <p>{text.missing}</p>}
        </section>)}</details>
        <section className={styles.evidence}><h2>{text.source}</h2>
          <p><a href="https://www.bls.gov/cpi/">U.S. Bureau of Labor Statistics · CPI</a> · <a href={snapshot.sourceUrl}>BLS API v2</a></p>
          <dl><dt>{text.capture}</dt><dd><code>{snapshot.captureId}</code></dd><dt>{text.hash}</dt><dd><code>{snapshot.responseSha256}</code></dd></dl>
          <p><code>{snapshot.policyVersion} · {snapshot.calculationVersion}</code></p>
          <p lang="en">BLS.gov cannot vouch for the data or analyses derived from these data after the data have been retrieved from BLS.gov.</p>
        </section>
      </>}
    </div>
  </main>;
}
function FragmentColumns({ index, yoy }: { index: string; yoy: string }) { return <><th scope="col">{index}</th><th scope="col">{yoy}</th></>; }
function DataColumns({ index, yoy }: { index: string; yoy: string }) { return <><td>{index}</td><td>{yoy}</td></>; }
