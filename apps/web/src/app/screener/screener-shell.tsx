import Link from "next/link";
import type { ScreenerShellState } from "@/lib/screener-shell-state";

export function ScreenerShell({ state }: { state: ScreenerShellState }) {
  return (
    <section
      className="data-section screener-policy-section"
      aria-labelledby="screener-policy-title"
      role="region"
      tabIndex={0}
    >
      <div className="section-heading screener-policy-heading">
        <div>
          <p className="eyebrow">Known-deferred application state</p>
          <h2 id="screener-policy-title">Historical screening publication state</h2>
        </div>
        <span>Deferred to P8</span>
      </div>

      <div className="screener-product-policy" aria-label="Screener product availability policy">
        <p className="screener-product-policy-label">
          Product availability policy · not fixture evidence
        </p>
        <p>
          <strong>No feature catalog.</strong> Historical equity screening remains deferred until P8
          supplies historical bars, a point-in-time feature catalog, and a materialized screening
          read model.
        </p>
        <p>
          <strong>Not an empty query.</strong> This state is distinct from a completed screen with no
          matches, a loading state, and a route error. No screening query is executed here.
        </p>
        <p>
          <strong>No substitute output.</strong> The application does not promote call evidence,
          methodology definitions, fixture literals, or missing values into filters, results,
          ordering, charts, or numeric metrics. Performance outcomes and rankings remain P3 work;
          licensed observed-provider integration remains P5 work. Neither is substituted here.
        </p>
      </div>

      <div className="screener-state" role="status" aria-label="Deferred screener state">
        <dl>
          <div>
            <dt>Data mode</dt>
            <dd className="mono">{state.dataMode}</dd>
          </div>
          <div>
            <dt>Scope</dt>
            <dd className="mono">{state.scope}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd className="mono">{state.status}</dd>
          </div>
          <div>
            <dt>Reason</dt>
            <dd className="mono">{state.reasonCode}</dd>
          </div>
          <div>
            <dt>Missing display</dt>
            <dd className="mono na-value">{state.missingDisplay}</dd>
          </div>
        </dl>
      </div>

      <p className="screener-boundary-note" role="note">
        <span className="mono">NA</span> records an unpublished capability state; it never means
        zero matches, a zero numeric value, completeness, or a successful empty query. This policy
        has no schema version, fixture version, timestamp, source, provenance, or disclaimer
        because it is an application phase boundary rather than observed or fixture evidence.
      </p>

      <nav className="screener-evidence-links" aria-label="Adjacent evidence routes">
        <span>Separate evidence surfaces · not screener output</span>
        <Link className="text-action" href="/calls">
          Open recorded call evidence
        </Link>
        <Link className="text-action" href="/methodology">
          Open methodology definitions
        </Link>
      </nav>
    </section>
  );
}
