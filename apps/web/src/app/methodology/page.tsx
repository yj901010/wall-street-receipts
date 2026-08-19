import { SiteHeader } from "@/components/site-header";
import { methodologyProvider } from "@/lib/providers";
import { MethodologyRegistry } from "./methodology-registry";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

export default async function MethodologyPage() {
  const catalog = await methodologyProvider().catalog();

  return (
    <main>
      <SiteHeader current="methodology" dataMode={catalog.dataMode} />

      <div className="page-shell methodology-shell">
        <section className="page-heading methodology-heading" aria-labelledby="methodology-title">
          <div>
            <p className="eyebrow">Deterministic model governance</p>
            <h1 id="methodology-title">Methodology definitions, before performance claims.</h1>
            <p className="page-summary">
              Inspect immutable definition identities and their evidence. MODEL_ONLY records do not
              imply that a return, hit rate, alpha, or ranking has been calculated.
            </p>
          </div>
          <dl className="provenance-strip" aria-label="Methodology dataset provenance">
            <div>
              <dt>As of</dt>
              <dd>{utcFormatter.format(new Date(catalog.asOf))} UTC</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>{catalog.source}</dd>
            </div>
            <div>
              <dt>Mode</dt>
              <dd>{catalog.dataMode}</dd>
            </div>
          </dl>
        </section>

        <MethodologyRegistry catalog={catalog} />
        <p className="dataset-disclaimer">{catalog.disclaimer}</p>
      </div>
    </main>
  );
}
