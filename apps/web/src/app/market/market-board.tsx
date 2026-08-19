import Link from "next/link";
import type { MarketBoardSnapshot } from "@/lib/providers";

export function MarketBoard({ snapshot }: { snapshot: MarketBoardSnapshot }) {
  return (
    <section
      className="data-section market-board-publication"
      aria-labelledby="market-board-publication-title"
      role="region"
      tabIndex={0}
    >
      <div className="section-heading market-board-publication-heading">
        <div>
          <p className="eyebrow">Closed publication state</p>
          <h2 id="market-board-publication-title">Market board publication state</h2>
        </div>
        <span>Not published</span>
      </div>

      <div className="market-board-policy" aria-label="Market board publication policy">
        <p className="market-board-policy-label">Publication policy · not market evidence</p>
        <p>
          <strong>No quote catalog.</strong> This fixture records that a canonical global market
          board has not been published. It is not a delayed, end-of-day, or current quote surface.
        </p>
        <p>
          <strong>No promoted context.</strong> Call-event snapshots and synthetic map samples stay
          in their owning evidence views; neither is substituted here.
        </p>
        <p>
          <strong>No inferred values.</strong> Price, change, market status, freshness, and coverage
          remain unavailable. Missing values are never replaced with zero.
        </p>
      </div>

      <div className="market-board-state-grid">
        <div
          className="market-board-availability"
          role="status"
          aria-label="Known-unavailable market board status"
        >
          <dl>
            <div>
              <dt>Publication status</dt>
              <dd className="mono">{snapshot.publicationStatus}</dd>
            </div>
            <div>
              <dt>Scope</dt>
              <dd className="mono">{snapshot.scope}</dd>
            </div>
            <div>
              <dt>Reason</dt>
              <dd className="mono">{snapshot.publicationReasonCode}</dd>
            </div>
            <div>
              <dt>Market as of</dt>
              <dd className="mono na-value">{snapshot.marketAsOf ?? snapshot.missingDisplay}</dd>
            </div>
            <div>
              <dt>Quote publication</dt>
              <dd>None published</dd>
            </div>
            <div>
              <dt>Missing display</dt>
              <dd className="mono na-value">{snapshot.missingDisplay}</dd>
            </div>
          </dl>
        </div>

        <div className="market-board-policy-metadata" aria-label="Market board policy metadata">
          <h3>Policy-record timestamps</h3>
          <p>
            Generated and captured timestamps describe this fixture publication-policy record.
            They are not a market as-of time, quote timestamp, freshness marker, or trading-session
            status.
          </p>
          <dl>
            <div>
              <dt>Source type</dt>
              <dd className="mono">{snapshot.provenance.sourceType}</dd>
            </div>
            <div>
              <dt>License</dt>
              <dd className="mono">{snapshot.provenance.licenseClass}</dd>
            </div>
            <div>
              <dt>Synthetic policy record</dt>
              <dd className="mono">{String(snapshot.provenance.synthetic)}</dd>
            </div>
          </dl>
        </div>
      </div>

      <div className="market-board-source-paths" aria-label="Market board source paths">
        <span>Contract sources</span>
        <ul>
          {snapshot.provenance.sourcePaths.map((path) => (
            <li className="mono" key={path}>{path}</li>
          ))}
        </ul>
      </div>

      <p className="dataset-disclaimer market-board-disclaimer">{snapshot.disclaimer}</p>

      <div className="market-board-actions">
        <Link className="text-action" href="/">Return to dashboard evidence</Link>
        <Link className="text-action" href="/markets/sp500">
          Open recorded S&amp;P 500 call-event history
        </Link>
      </div>
    </section>
  );
}
