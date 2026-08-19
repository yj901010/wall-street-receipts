import Link from "next/link";
import type { AnalystDirectorySnapshot } from "@/lib/providers";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

export function AnalystDirectory({ snapshot }: { snapshot: AnalystDirectorySnapshot }) {
  return (
    <section className="data-section analyst-directory" aria-labelledby="analyst-directory-title">
      <div className="section-heading analyst-directory-heading">
        <div>
          <p className="eyebrow">Canonical identity records</p>
          <h2 id="analyst-directory-title">Analyst directory</h2>
        </div>
        <span>{snapshot.dataMode} identity fixture · coverage not asserted</span>
      </div>

      <div className="analyst-directory-policy" aria-label="Analyst directory policy">
        <p className="analyst-policy-label">Product policy · not fixture evidence</p>
        <p>
          <strong>Not ranked.</strong> Rows use canonical-name order, never performance, accuracy,
          score, call volume, confidence, or recommendation order.
        </p>
        <p>
          <strong>Recorded state.</strong> The active field is preserved as captured fixture evidence
          at its stated effective and capture times; it is not a live activity claim.
        </p>
        <p>
          <strong>Synthetic DEMO identity only.</strong> Names and recorded status do not establish
          verified coverage, employer or affiliation, endorsement, performance, or investment advice.
        </p>
      </div>

      <div className="analyst-source-evidence" aria-label="Analyst source evidence">
        <div>
          <span>Source type</span>
          <strong>{snapshot.provenance.sourceType}</strong>
        </div>
        <div>
          <span>License</span>
          <strong>{snapshot.provenance.licenseClass}</strong>
        </div>
        <div>
          <span>Synthetic</span>
          <strong>{String(snapshot.provenance.synthetic)}</strong>
        </div>
        <div className="analyst-source-paths">
          <span>Source paths</span>
          <ul>
            {snapshot.provenance.sourcePaths.map((path) => (
              <li className="mono" key={path}>{path}</li>
            ))}
          </ul>
        </div>
      </div>

      {snapshot.analysts.length > 0 ? (
        <div
          className="table-scroll analyst-table-scroll"
          role="region"
          aria-label="Analyst identity table"
          tabIndex={0}
        >
          <table className="calls-table analyst-table">
            <caption className="visually-hidden">
              Canonical analyst identities and their captured evidence
            </caption>
            <thead>
              <tr>
                <th scope="col">Analyst</th>
                <th scope="col">Recorded active</th>
                <th scope="col">Mode</th>
                <th scope="col">Effective</th>
                <th scope="col">Captured</th>
                <th scope="col">Provenance</th>
                <th scope="col">Call ledger</th>
              </tr>
            </thead>
            <tbody>
              {snapshot.analysts.map((analyst) => (
                <tr key={analyst.analystId}>
                  <td data-label="Analyst">
                    <strong>{analyst.canonicalName}</strong>
                    <span className="cell-secondary mono">{analyst.analystId}</span>
                  </td>
                  <td className="mono analyst-recorded-state" data-label="Recorded active">
                    {String(analyst.active)}
                  </td>
                  <td className="mono" data-label="Mode">{analyst.dataMode}</td>
                  <td className="mono" data-label="Effective">{utc(analyst.effectiveAt)}</td>
                  <td className="mono" data-label="Captured">{utc(analyst.capturedAt)}</td>
                  <td className="mono" data-label="Provenance">{analyst.provenanceId}</td>
                  <td data-label="Call ledger">
                    <Link
                      className="text-action"
                      href={`/calls?analystId=${encodeURIComponent(analyst.analystId)}`}
                      aria-label={`Filter call ledger for ${analyst.canonicalName}`}
                    >
                      Filter call ledger
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state" role="status">
          <h3>No analyst identities are recorded.</h3>
          <p>No placeholder identity, affiliation, call data, metric, score, or rank was generated.</p>
        </div>
      )}
    </section>
  );
}
