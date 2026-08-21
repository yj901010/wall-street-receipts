import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { institutionDirectoryProvider } from "@/lib/providers";
import { InstitutionDirectory } from "./institution-directory";
import { formatInstitutionUtc, getInstitutionMessages } from "./messages";

export default async function InstitutionsPage() {
  const locale = await getLocale();
  const messages = getInstitutionMessages(locale);
  const snapshot = await institutionDirectoryProvider().directory();

  return (
    <main>
      <SiteHeader current="institutions" dataMode={snapshot.dataMode} />

      <div className="page-shell institutions-shell">
        <section className="page-heading institutions-heading" aria-labelledby="institutions-title">
          <div>
            <p className="eyebrow">{messages.page.eyebrow}</p>
            <h1 id="institutions-title">{messages.page.title}</h1>
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
              <dd>{formatInstitutionUtc(snapshot.generatedAt)}</dd>
            </div>
            <div>
              <dt>{messages.page.captured}</dt>
              <dd>{formatInstitutionUtc(snapshot.provenance.capturedAt)}</dd>
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

        <InstitutionDirectory snapshot={snapshot} locale={locale} />
      </div>
    </main>
  );
}
