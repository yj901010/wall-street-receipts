import { SiteHeader } from "@/components/site-header";
import { KstTimestamp } from "@/components/kst-timestamp";
import { getLocale } from "@/lib/i18n/server";
import { sp500HistoryProvider } from "@/lib/providers";
import { getSp500HistoryMessages } from "./messages";
import { Sp500CallHistory } from "./sp500-call-history";

export default async function Sp500HistoryPage() {
  const [snapshot, locale] = await Promise.all([sp500HistoryProvider().history(), getLocale()]);
  const messages = getSp500HistoryMessages(locale).page;

  return (
    <main>
      <SiteHeader current="market" dataMode={snapshot.dataMode} />

      <div className="page-shell sp500-history-shell">
        <section className="page-heading sp500-history-page-heading" aria-labelledby="sp500-page-title">
          <div>
            <p className="eyebrow">{messages.eyebrow}</p>
            <h1 id="sp500-page-title">{messages.title}</h1>
            <p className="page-summary">{messages.summary}</p>
          </div>
          <dl className="provenance-strip" aria-label={messages.provenanceLabel}>
            <div>
              <dt>{messages.catalogAsOf}</dt>
              <dd className="mono">
                <KstTimestamp value={snapshot.asOf} />
              </dd>
            </div>
            <div>
              <dt>{messages.source}</dt>
              <dd className="mono">{snapshot.source}</dd>
            </div>
            <div>
              <dt>{messages.asset}</dt>
              <dd className="mono">{snapshot.asset.ticker ?? "NA"}</dd>
            </div>
            <div>
              <dt>{messages.mode}</dt>
              <dd className="mono">{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        <Sp500CallHistory locale={locale} snapshot={snapshot} />
      </div>
    </main>
  );
}
