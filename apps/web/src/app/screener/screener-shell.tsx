import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { ScreenerShellState } from "@/lib/screener-shell-state";
import { getScreenerMessages } from "./messages";

export function ScreenerShell({ state, locale }: { state: ScreenerShellState; locale: Locale }) {
  const messages = getScreenerMessages(locale);
  return (
    <section
      className="data-section screener-policy-section"
      aria-labelledby="screener-policy-title"
      role="region"
      tabIndex={0}
    >
      <div className="section-heading screener-policy-heading">
        <div>
          <p className="eyebrow">{messages.shell.eyebrow}</p>
          <h2 id="screener-policy-title">{messages.shell.title}</h2>
        </div>
        <span>{messages.shell.state}</span>
      </div>

      <div className="screener-product-policy" aria-label={messages.shell.policyLabel}>
        <p className="screener-product-policy-label">{messages.shell.policyNotice}</p>
        <p>
          <strong>{messages.shell.noCatalogTitle}</strong> {messages.shell.noCatalogBody}
        </p>
        <p>
          <strong>{messages.shell.notEmptyTitle}</strong> {messages.shell.notEmptyBody}
        </p>
        <p>
          <strong>{messages.shell.noSubstituteTitle}</strong> {messages.shell.noSubstituteBody}
        </p>
      </div>

      <div className="screener-state" role="status" aria-label={messages.shell.stateLabel}>
        <dl>
          <div>
            <dt>{messages.shell.labels.dataMode}</dt>
            <dd className="mono">{state.dataMode}</dd>
          </div>
          <div>
            <dt>{messages.shell.labels.scope}</dt>
            <dd className="mono">{state.scope}</dd>
          </div>
          <div>
            <dt>{messages.shell.labels.status}</dt>
            <dd className="mono">{state.status}</dd>
          </div>
          <div>
            <dt>{messages.shell.labels.reason}</dt>
            <dd className="mono">{state.reasonCode}</dd>
          </div>
          <div>
            <dt>{messages.shell.labels.missingDisplay}</dt>
            <dd className="mono na-value">{state.missingDisplay}</dd>
          </div>
        </dl>
      </div>

      <p className="screener-boundary-note" role="note">
        <span className="mono">NA</span>{messages.shell.boundaryNote}
      </p>

      <nav className="screener-evidence-links" aria-label={messages.shell.adjacentLabel}>
        <span>{messages.shell.adjacentNotice}</span>
        <Link className="text-action" href="/calls">
          {messages.shell.calls}
        </Link>
        <Link className="text-action" href="/methodology">
          {messages.shell.methodology}
        </Link>
      </nav>
    </section>
  );
}
