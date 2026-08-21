import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { AnalystDirectorySnapshot } from "@/lib/providers";
import { formatAnalystUtc, getAnalystMessages } from "./messages";

export function AnalystDirectory({ snapshot, locale }: { snapshot: AnalystDirectorySnapshot; locale: Locale }) {
  const messages = getAnalystMessages(locale);
  return (
    <section className="data-section analyst-directory" aria-labelledby="analyst-directory-title">
      <div className="section-heading analyst-directory-heading">
        <div>
          <p className="eyebrow">{messages.directory.eyebrow}</p>
          <h2 id="analyst-directory-title">{messages.directory.title}</h2>
        </div>
        <span>{snapshot.dataMode} {messages.directory.countSuffix}</span>
      </div>

      <div className="analyst-directory-policy" aria-label={messages.directory.policyLabel}>
        <p className="analyst-policy-label">{messages.directory.productPolicy}</p>
        <p>
          <strong>{messages.directory.notRankedTitle}</strong> {messages.directory.notRankedBody}
        </p>
        <p>
          <strong>{messages.directory.recordedTitle}</strong> {messages.directory.recordedBody}
        </p>
        <p>
          <strong>{messages.directory.syntheticTitle}</strong> {messages.directory.syntheticBody}
        </p>
      </div>

      <div className="analyst-source-evidence" aria-label={messages.directory.sourceEvidenceLabel}>
        <div>
          <span>{messages.directory.sourceType}</span>
          <strong>{snapshot.provenance.sourceType}</strong>
        </div>
        <div>
          <span>{messages.directory.license}</span>
          <strong>{snapshot.provenance.licenseClass}</strong>
        </div>
        <div>
          <span>{messages.directory.synthetic}</span>
          <strong>{String(snapshot.provenance.synthetic)}</strong>
        </div>
        <div className="analyst-source-paths">
          <span>{messages.directory.sourcePaths}</span>
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
          aria-label={messages.directory.tableLabel}
          tabIndex={0}
        >
          <table className="calls-table analyst-table">
            <caption className="visually-hidden">
              {messages.directory.caption}
            </caption>
            <thead>
              <tr>
                <th scope="col">{messages.directory.columns.analyst}</th>
                <th scope="col">{messages.directory.columns.recordedActive}</th>
                <th scope="col">{messages.directory.columns.mode}</th>
                <th scope="col">{messages.directory.columns.effective}</th>
                <th scope="col">{messages.directory.columns.captured}</th>
                <th scope="col">{messages.directory.columns.provenance}</th>
                <th scope="col">{messages.directory.columns.callLedger}</th>
              </tr>
            </thead>
            <tbody>
              {snapshot.analysts.map((analyst) => (
                <tr key={analyst.analystId}>
                  <td data-label={messages.directory.columns.analyst}>
                    <strong>{analyst.canonicalName}</strong>
                    <span className="cell-secondary mono">{analyst.analystId}</span>
                  </td>
                  <td className="mono analyst-recorded-state" data-label={messages.directory.columns.recordedActive}>
                    {String(analyst.active)}
                  </td>
                  <td className="mono" data-label={messages.directory.columns.mode}>{analyst.dataMode}</td>
                  <td className="mono" data-label={messages.directory.columns.effective}>{formatAnalystUtc(analyst.effectiveAt)}</td>
                  <td className="mono" data-label={messages.directory.columns.captured}>{formatAnalystUtc(analyst.capturedAt)}</td>
                  <td className="mono" data-label={messages.directory.columns.provenance}>{analyst.provenanceId}</td>
                  <td data-label={messages.directory.columns.callLedger}>
                    <Link
                      className="text-action"
                      href={`/calls?analystId=${encodeURIComponent(analyst.analystId)}`}
                      aria-label={`${messages.directory.filterCallLedgerFor} ${analyst.canonicalName}`}
                    >
                      {messages.directory.filterCallLedger}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state" role="status">
          <h3>{messages.directory.emptyTitle}</h3>
          <p>{messages.directory.emptyBody}</p>
        </div>
      )}
    </section>
  );
}
