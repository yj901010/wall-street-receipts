import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { analystDirectoryProvider } from "@/lib/providers";
import { AnalystDirectory } from "./analyst-directory";
import { formatAnalystUtc, getAnalystMessages } from "./messages";

export default async function AnalystsPage() {
  const locale = await getLocale();
  const messages = getAnalystMessages(locale);
  const snapshot = await analystDirectoryProvider().directory();

  return (
    <main>
      <SiteHeader current="analysts" dataMode={snapshot.dataMode} />

      <div className="page-shell analysts-shell">
        <section className="page-heading analysts-heading" aria-labelledby="analysts-title">
          <div>
            <p className="eyebrow">{messages.page.eyebrow}</p>
            <h1 id="analysts-title">{messages.page.title}</h1>
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
              <dt>{messages.page.generated}</dt>
              <dd>{formatAnalystUtc(snapshot.generatedAt)}</dd>
            </div>
            <div>
              <dt>{messages.page.captured}</dt>
              <dd>{formatAnalystUtc(snapshot.provenance.capturedAt)}</dd>
            </div>
            <div>
              <dt>{messages.page.source}</dt>
              <dd>{snapshot.provenance.id}</dd>
            </div>
            <div>
              <dt>{messages.page.mode}</dt>
              <dd>{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        <AnalystDirectory snapshot={snapshot} locale={locale} />
      </div>
    </main>
  );
}
