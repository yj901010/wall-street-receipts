import { SiteHeader } from "@/components/site-header";
import { KstTimestamp } from "@/components/kst-timestamp";
import { getLocale } from "@/lib/i18n/server";
import { methodologyProvider } from "@/lib/providers";
import { getMethodologyMessages } from "./messages";
import { MethodologyRegistry } from "./methodology-registry";

export default async function MethodologyPage() {
  const locale = await getLocale();
  const messages = getMethodologyMessages(locale);
  const catalog = await methodologyProvider().catalog();

  return (
    <main>
      <SiteHeader current="methodology" dataMode={catalog.dataMode} />

      <div className="page-shell methodology-shell">
        <section className="page-heading methodology-heading" aria-labelledby="methodology-title">
          <div>
            <p className="eyebrow">{messages.page.eyebrow}</p>
            <h1 id="methodology-title">{messages.page.title}</h1>
            <p className="page-summary">{messages.page.summary}</p>
          </div>
          <dl className="provenance-strip" aria-label={messages.page.provenanceLabel}>
            <div>
              <dt>{messages.page.asOf}</dt>
              <dd><KstTimestamp value={catalog.asOf} /></dd>
            </div>
            <div>
              <dt>{messages.page.source}</dt>
              <dd>{catalog.source}</dd>
            </div>
            <div>
              <dt>{messages.page.mode}</dt>
              <dd>{catalog.dataMode}</dd>
            </div>
          </dl>
        </section>

        <MethodologyRegistry catalog={catalog} locale={locale} />
        <p className="dataset-disclaimer">{catalog.disclaimer}</p>
      </div>
    </main>
  );
}
