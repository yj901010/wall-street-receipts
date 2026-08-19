import { SiteHeader } from "@/components/site-header";
import { sp500HistoryProvider } from "@/lib/providers";
import { Sp500CallHistory } from "./sp500-call-history";

export default async function Sp500HistoryPage() {
  const snapshot = await sp500HistoryProvider().history();

  return (
    <main>
      <SiteHeader current="market" dataMode={snapshot.dataMode} />

      <div className="page-shell sp500-history-shell">
        <section className="page-heading sp500-history-page-heading" aria-labelledby="sp500-page-title">
          <div>
            <p className="eyebrow">Committed DEMO call-event ledger</p>
            <h1 id="sp500-page-title">Recorded S&amp;P 500 forecast-call events.</h1>
            <p className="page-summary">
              This is a point-in-time subset of original analyst-call records, not index-price
              history, a current forecast, consensus, market trend, or performance series.
            </p>
          </div>
          <dl className="provenance-strip" aria-label="S&P 500 call-history provenance">
            <div>
              <dt>Call catalog as of</dt>
              <dd className="mono">
                <time dateTime={snapshot.asOf}>{snapshot.asOf}</time>
              </dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd className="mono">{snapshot.source}</dd>
            </div>
            <div>
              <dt>Asset</dt>
              <dd className="mono">{snapshot.asset.ticker ?? "NA"}</dd>
            </div>
            <div>
              <dt>Mode</dt>
              <dd className="mono">{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        <Sp500CallHistory snapshot={snapshot} />
      </div>
    </main>
  );
}
