import { marketProvider } from "@/lib/providers";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function display(value: string | null) {
  return value ?? "NA";
}

export default async function DashboardPage() {
  const snapshot = await marketProvider().dashboard();

  return (
    <main>
      <header className="site-header">
        <a className="wordmark" href="#top" aria-label="Wall Street Receipts home">
          WALL STREET <span>RECEIPTS</span>
        </a>
        <nav aria-label="Primary navigation">
          <a aria-current="page" href="#market">Market</a>
          <a href="#calls">Calls</a>
          <a href="#methodology">Methodology</a>
        </nav>
        <span className="mode-badge">{snapshot.dataMode}</span>
      </header>

      <div className="page-shell" id="top">
        <section className="page-heading" aria-labelledby="page-title">
          <div>
            <p className="eyebrow">Point-in-time analyst intelligence</p>
            <h1 id="page-title">Market evidence, frozen at the call.</h1>
          </div>
          <dl className="provenance-strip" aria-label="Dataset provenance">
            <div>
              <dt>As of</dt>
              <dd>{utcFormatter.format(new Date(snapshot.asOf))} UTC</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>{snapshot.source}</dd>
            </div>
            <div>
              <dt>Mode</dt>
              <dd>{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        <section className="market-strip" id="market" aria-label="Market board">
          {snapshot.instruments.map((instrument) => (
            <article key={instrument.symbol}>
              <div>
                <strong>{instrument.symbol}</strong>
                <span>{instrument.name}</span>
              </div>
              <div className="quote">
                <b>{display(instrument.price)}</b>
                <span className={instrument.changePercent?.startsWith("-") ? "negative" : "positive"}>
                  {display(instrument.changePercent)}
                </span>
              </div>
            </article>
          ))}
        </section>

        <div className="content-grid">
          <section className="data-section" id="calls" aria-labelledby="calls-title">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Event ledger</p>
                <h2 id="calls-title">Latest analyst calls</h2>
              </div>
              <span>{snapshot.calls.length} DEMO events</span>
            </div>
            <div className="table-scroll" tabIndex={0} aria-label="Scrollable analyst calls table">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Event time</th>
                    <th scope="col">Institution</th>
                    <th scope="col">Asset</th>
                    <th scope="col">Direction</th>
                    <th scope="col" className="numeric">Target</th>
                    <th scope="col">Evidence</th>
                  </tr>
                </thead>
                <tbody>
                  {snapshot.calls.map((call) => (
                    <tr key={call.id}>
                      <td className="mono">{call.eventTime.replace("T", " ").replace("Z", " UTC")}</td>
                      <td>{call.institution}</td>
                      <td><strong>{call.asset}</strong></td>
                      <td><span className="direction">{call.direction}</span></td>
                      <td className="numeric mono">{display(call.previousTarget)} → {display(call.target)}</td>
                      <td>{call.sourceTitle}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <aside className="integrity-panel" id="methodology" aria-labelledby="integrity-title">
            <p className="eyebrow">Integrity status</p>
            <h2 id="integrity-title">Evidence before inference</h2>
            <dl>
              <div><dt>Fixture mode</dt><dd className="positive">Active</dd></div>
              <div><dt>Vendor keys</dt><dd>Not required</dd></div>
              <div><dt>Missing values</dt><dd>Rendered as NA</dd></div>
              <div><dt>Timestamp basis</dt><dd>UTC</dd></div>
            </dl>
            <p>
              P0 verifies the runtime and provider boundaries. Performance and ranking values remain unavailable until the deterministic methodology is implemented.
            </p>
          </aside>
        </div>
      </div>
    </main>
  );
}
