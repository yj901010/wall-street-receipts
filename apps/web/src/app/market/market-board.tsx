import Link from "next/link";
import { KstTimestamp } from "@/components/kst-timestamp";
import type { Locale } from "@/lib/i18n/config";
import type { MarketBoardSnapshot } from "@/lib/providers";
import { getMarketMessages } from "./messages";

export function MarketBoard({ snapshot, locale }: { snapshot: MarketBoardSnapshot; locale: Locale }) {
  const messages = getMarketMessages(locale);
  return (
    <section
      className="data-section market-board-publication"
      aria-labelledby="market-board-publication-title"
      role="region"
      tabIndex={0}
    >
      <div className="section-heading market-board-publication-heading">
        <div>
          <p className="eyebrow">{messages.board.eyebrow}</p>
          <h2 id="market-board-publication-title">{messages.board.title}</h2>
        </div>
        <span>{messages.board.state}</span>
      </div>

      <div className="market-board-policy" aria-label={messages.board.policyLabel}>
        <p className="market-board-policy-label">{messages.board.policyNotice}</p>
        <p>
          <strong>{messages.board.noCatalogTitle}</strong> {messages.board.noCatalogBody}
        </p>
        <p>
          <strong>{messages.board.noContextTitle}</strong> {messages.board.noContextBody}
        </p>
        <p>
          <strong>{messages.board.noValuesTitle}</strong> {messages.board.noValuesBody}
        </p>
      </div>

      <div className="market-board-state-grid">
        <div
          className="market-board-availability"
          role="status"
          aria-label={messages.board.unavailableLabel}
        >
          <dl>
            <div>
              <dt>{messages.board.publicationStatus}</dt>
              <dd className="mono">{snapshot.publicationStatus}</dd>
            </div>
            <div>
              <dt>{messages.board.scope}</dt>
              <dd className="mono">{snapshot.scope}</dd>
            </div>
            <div>
              <dt>{messages.board.reason}</dt>
              <dd className="mono">{snapshot.publicationReasonCode}</dd>
            </div>
            <div>
              <dt>{messages.board.marketAsOf}</dt>
              <dd className={`mono${snapshot.marketAsOf === null ? " na-value" : ""}`}>
                {snapshot.marketAsOf === null
                  ? snapshot.missingDisplay
                  : <KstTimestamp value={snapshot.marketAsOf} />}
              </dd>
            </div>
            <div>
              <dt>{messages.board.quotePublication}</dt>
              <dd>{messages.board.nonePublished}</dd>
            </div>
            <div>
              <dt>{messages.board.missingDisplay}</dt>
              <dd className="mono na-value">{snapshot.missingDisplay}</dd>
            </div>
          </dl>
        </div>

        <div className="market-board-policy-metadata" aria-label={messages.board.metadataLabel}>
          <h3>{messages.board.timestampTitle}</h3>
          <p>{messages.board.timestampBody}</p>
          <dl>
            <div>
              <dt>{messages.board.sourceType}</dt>
              <dd className="mono">{snapshot.provenance.sourceType}</dd>
            </div>
            <div>
              <dt>{messages.board.license}</dt>
              <dd className="mono">{snapshot.provenance.licenseClass}</dd>
            </div>
            <div>
              <dt>{messages.board.synthetic}</dt>
              <dd className="mono">{String(snapshot.provenance.synthetic)}</dd>
            </div>
          </dl>
        </div>
      </div>

      <div className="market-board-source-paths" aria-label={messages.board.sourcePathsLabel}>
        <span>{messages.board.contractSources}</span>
        <ul>
          {snapshot.provenance.sourcePaths.map((path) => (
            <li className="mono" key={path}>{path}</li>
          ))}
        </ul>
      </div>

      <p className="dataset-disclaimer market-board-disclaimer">{snapshot.disclaimer}</p>

      <div className="market-board-actions">
        <Link className="text-action" href="/">{messages.board.dashboard}</Link>
        <Link className="text-action" href="/markets/sp500">
          {messages.board.history}
        </Link>
      </div>
    </section>
  );
}
