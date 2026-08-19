import { SiteHeader } from "@/components/site-header";
import { marketBoardProvider } from "@/lib/providers";
import { MarketBoard } from "./market-board";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

export default async function MarketPage() {
  const snapshot = await marketBoardProvider().snapshot();

  return (
    <main>
      <SiteHeader current="market" dataMode={snapshot.dataMode} />

      <div className="page-shell market-board-shell">
        <section className="page-heading market-board-heading" aria-labelledby="market-title">
          <div>
            <p className="eyebrow">Known-unavailable DEMO publication</p>
            <h1 id="market-title">A global market board is not published.</h1>
            <p className="page-summary">
              This route preserves the publication boundary instead of converting historical call
              context, synthetic map samples, or application literals into current market facts.
            </p>
          </div>
          <dl className="provenance-strip" aria-label="Market board fixture provenance">
            <div>
              <dt>Schema</dt>
              <dd>{snapshot.schemaVersion}</dd>
            </div>
            <div>
              <dt>Fixture</dt>
              <dd>{snapshot.fixtureVersion}</dd>
            </div>
            <div>
              <dt>Policy generated</dt>
              <dd>{utc(snapshot.generatedAt)}</dd>
            </div>
            <div>
              <dt>Policy captured</dt>
              <dd>{utc(snapshot.provenance.capturedAt)}</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd className="mono">{snapshot.provenance.id}</dd>
            </div>
            <div>
              <dt>Mode</dt>
              <dd className="mono">{snapshot.dataMode}</dd>
            </div>
          </dl>
        </section>

        <MarketBoard snapshot={snapshot} />
      </div>
    </main>
  );
}
