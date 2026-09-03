import { SiteHeader } from "@/components/site-header";
import { KstTimestamp } from "@/components/kst-timestamp";
import { getLocale } from "@/lib/i18n/server";
import { marketBoardProvider } from "@/lib/providers";
import { MarketBoard } from "./market-board";
import { getMarketMessages } from "./messages";

export default async function MarketPage() {
  const locale = await getLocale();
  const messages = getMarketMessages(locale);
  const snapshot = await marketBoardProvider().snapshot();

  return (
    <main>
      <SiteHeader current="market" dataMode={snapshot.dataMode} />

      <div className="page-shell market-board-shell">
        <section className="page-heading market-board-heading" aria-labelledby="market-title">
          <div>
            <p className="eyebrow">{messages.page.eyebrow}</p>
            <h1 id="market-title">{messages.page.title}</h1>
            <p className="page-summary">{messages.page.summary}</p>
          </div>
          <dl className="provenance-strip" aria-label={messages.page.provenanceLabel}>
            <div>
              <dt>{messages.page.schema}</dt>
              <dd>{snapshot.schemaVersion}</dd>
            </div>
            <div>
              <dt>{messages.page.fixture}</dt>
              <dd>{snapshot.fixtureVersion}</dd>
            </div>
            <div>
              <dt>{messages.page.policyGenerated}</dt>
              <dd><KstTimestamp value={snapshot.generatedAt} /></dd>
            </div>
            <div>
              <dt>{messages.page.policyCaptured}</dt>
              <dd><KstTimestamp value={snapshot.provenance.capturedAt} /></dd>
            </div>
            <div>
              <dt>{messages.page.source}</dt>
              <dd className="mono">{snapshot.provenance.id}</dd>
            </div>
            <div>
              <dt>{messages.page.mode}</dt>
              <dd className="mono">{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        <MarketBoard snapshot={snapshot} locale={locale} />
      </div>
    </main>
  );
}
