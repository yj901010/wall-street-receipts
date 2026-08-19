import { SiteHeader } from "@/components/site-header";
import { institutionDirectoryProvider } from "@/lib/providers";
import { InstitutionDirectory } from "./institution-directory";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

export default async function InstitutionsPage() {
  const snapshot = await institutionDirectoryProvider().directory();

  return (
    <main>
      <SiteHeader current="institutions" dataMode={snapshot.dataMode} />

      <div className="page-shell institutions-shell">
        <section className="page-heading institutions-heading" aria-labelledby="institutions-title">
          <div>
            <p className="eyebrow">Identity before performance</p>
            <h1 id="institutions-title">Institutions as recorded evidence, not a leaderboard.</h1>
            <p className="page-summary">
              Inspect committed DEMO identity fields and provenance. This route publishes no
              institution score, accuracy, performance metric, or rank.
            </p>
          </div>
          <dl className="provenance-strip" aria-label="Institution identity fixture provenance">
            <div>
              <dt>Schema</dt>
              <dd>{snapshot.schemaVersion}</dd>
            </div>
            <div>
              <dt>Fixture</dt>
              <dd>{snapshot.fixtureVersion}</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{utc(snapshot.generatedAt)}</dd>
            </div>
            <div>
              <dt>Captured</dt>
              <dd>{utc(snapshot.provenance.capturedAt)}</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>{snapshot.provenance.id}</dd>
            </div>
            <div>
              <dt>Mode</dt>
              <dd>{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        <InstitutionDirectory snapshot={snapshot} />
      </div>
    </main>
  );
}
