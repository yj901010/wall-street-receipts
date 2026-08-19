import Link from "next/link";
import type { InstitutionDirectorySnapshot } from "@/lib/providers";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

export function InstitutionDirectory({ snapshot }: { snapshot: InstitutionDirectorySnapshot }) {
  return (
    <section
      className="data-section institution-directory"
      aria-labelledby="institution-directory-title"
    >
      <div className="section-heading institution-directory-heading">
        <div>
          <p className="eyebrow">Canonical identity records</p>
          <h2 id="institution-directory-title">Institution directory</h2>
        </div>
        <span>
          {snapshot.institutions.length} {snapshot.dataMode} fixture records · coverage not asserted
        </span>
      </div>

      <div className="institution-directory-policy" aria-label="Institution directory policy">
        <p className="institution-policy-label">Product policy · not fixture evidence</p>
        <p>
          <strong>Not ranked.</strong> Rows use canonical-name order, never performance, accuracy,
          score, call volume, or recommendation order.
        </p>
        <p>
          <strong>Recorded state.</strong> The active field is preserved as captured fixture evidence
          at its stated effective and capture times; it is not a live operating-status claim.
        </p>
        <p>
          <strong>Limited DEMO catalog.</strong> The fixture record count does not assert market,
          industry, or provider coverage. Identity inclusion is not an endorsement or investment
          advice.
        </p>
      </div>

      <div className="institution-source-evidence" aria-label="Institution source evidence">
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
        <div className="institution-source-paths">
          <span>Source paths</span>
          <ul>
            {snapshot.provenance.sourcePaths.map((path) => (
              <li className="mono" key={path}>{path}</li>
            ))}
          </ul>
        </div>
      </div>

      {snapshot.institutions.length > 0 ? (
        <div
          className="table-scroll institution-table-scroll"
          role="region"
          aria-label="Institution identity table"
          tabIndex={0}
        >
          <table className="calls-table institution-table">
            <caption className="visually-hidden">
              Canonical institution identities and their captured evidence
            </caption>
            <thead>
              <tr>
                <th scope="col">Institution</th>
                <th scope="col">Slug</th>
                <th scope="col">Country</th>
                <th scope="col">Recorded active</th>
                <th scope="col">Mode</th>
                <th scope="col">Effective</th>
                <th scope="col">Captured</th>
                <th scope="col">Provenance</th>
                <th scope="col">Call ledger</th>
              </tr>
            </thead>
            <tbody>
              {snapshot.institutions.map((institution) => (
                <tr key={institution.institutionId}>
                  <td data-label="Institution">
                    <strong>{institution.canonicalName}</strong>
                    <span className="cell-secondary mono">{institution.institutionId}</span>
                  </td>
                  <td className="mono" data-label="Slug">{institution.slug}</td>
                  <td className="mono" data-label="Country">{institution.country}</td>
                  <td className="mono institution-recorded-state" data-label="Recorded active">
                    {String(institution.active)}
                  </td>
                  <td className="mono" data-label="Mode">{institution.dataMode}</td>
                  <td className="mono" data-label="Effective">{utc(institution.effectiveAt)}</td>
                  <td className="mono" data-label="Captured">{utc(institution.capturedAt)}</td>
                  <td className="mono" data-label="Provenance">{institution.provenanceId}</td>
                  <td data-label="Call ledger">
                    <Link
                      className="text-action"
                      href={`/calls?institutionId=${encodeURIComponent(institution.institutionId)}`}
                      aria-label={`Filter call ledger for ${institution.canonicalName}`}
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
          <h3>No institution identities are recorded.</h3>
          <p>No placeholder identity, coverage claim, score, accuracy, or rank was generated.</p>
        </div>
      )}
    </section>
  );
}
