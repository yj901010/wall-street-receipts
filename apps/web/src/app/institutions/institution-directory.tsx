import Link from "next/link";
import { KstTimestamp } from "@/components/kst-timestamp";
import type { Locale } from "@/lib/i18n/config";
import type { InstitutionDirectorySnapshot } from "@/lib/providers";
import { getInstitutionMessages } from "./messages";

export function InstitutionDirectory({ snapshot, locale }: { snapshot: InstitutionDirectorySnapshot; locale: Locale }) {
  const messages = getInstitutionMessages(locale);
  return (
    <section
      className="data-section institution-directory"
      aria-labelledby="institution-directory-title"
    >
      <div className="section-heading institution-directory-heading">
        <div>
          <p className="eyebrow">{messages.directory.eyebrow}</p>
          <h2 id="institution-directory-title">{messages.directory.title}</h2>
        </div>
        <span>
          {snapshot.institutions.length} {snapshot.dataMode} {messages.directory.countSuffix}
        </span>
      </div>

      <div className="institution-directory-policy" aria-label={messages.directory.policyLabel}>
        <p className="institution-policy-label">{messages.directory.productPolicy}</p>
        <p>
          <strong>{messages.directory.notRankedTitle}</strong> {messages.directory.notRankedBody}
        </p>
        <p>
          <strong>{messages.directory.recordedTitle}</strong> {messages.directory.recordedBody}
        </p>
        <p>
          <strong>{messages.directory.limitedTitle}</strong> {messages.directory.limitedBody}
        </p>
      </div>

      <div className="institution-source-evidence" aria-label={messages.directory.sourceEvidenceLabel}>
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
        <div className="institution-source-paths">
          <span>{messages.directory.sourcePaths}</span>
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
          aria-label={messages.directory.tableLabel}
          tabIndex={0}
        >
          <table className="calls-table institution-table">
            <caption className="visually-hidden">
              {messages.directory.caption}
            </caption>
            <thead>
              <tr>
                <th scope="col">{messages.directory.columns.institution}</th>
                <th scope="col">{messages.directory.columns.slug}</th>
                <th scope="col">{messages.directory.columns.country}</th>
                <th scope="col">{messages.directory.columns.recordedActive}</th>
                <th scope="col">{messages.directory.columns.mode}</th>
                <th scope="col">{messages.directory.columns.effective}</th>
                <th scope="col">{messages.directory.columns.captured}</th>
                <th scope="col">{messages.directory.columns.provenance}</th>
                <th scope="col">{messages.directory.columns.callLedger}</th>
              </tr>
            </thead>
            <tbody>
              {snapshot.institutions.map((institution) => (
                <tr key={institution.institutionId}>
                  <td data-label={messages.directory.columns.institution}>
                    <strong>{institution.canonicalName}</strong>
                    <span className="cell-secondary mono">{institution.institutionId}</span>
                  </td>
                  <td className="mono" data-label={messages.directory.columns.slug}>{institution.slug}</td>
                  <td className="mono" data-label={messages.directory.columns.country}>{institution.country}</td>
                  <td className="mono institution-recorded-state" data-label={messages.directory.columns.recordedActive}>
                    {String(institution.active)}
                  </td>
                  <td className="mono" data-label={messages.directory.columns.mode}>{institution.dataMode}</td>
                  <td className="mono" data-label={messages.directory.columns.effective}><KstTimestamp value={institution.effectiveAt} /></td>
                  <td className="mono" data-label={messages.directory.columns.captured}><KstTimestamp value={institution.capturedAt} /></td>
                  <td className="mono" data-label={messages.directory.columns.provenance}>{institution.provenanceId}</td>
                  <td data-label={messages.directory.columns.callLedger}>
                    <Link
                      className="text-action"
                      href={`/calls?institutionId=${encodeURIComponent(institution.institutionId)}`}
                      aria-label={`${messages.directory.filterCallLedgerFor} ${institution.canonicalName}`}
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
