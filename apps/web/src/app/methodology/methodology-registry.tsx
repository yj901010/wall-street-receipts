import { KstTimestamp } from "@/components/kst-timestamp";
import type { MethodologyCatalog } from "@/lib/providers";
import type { Locale } from "@/lib/i18n/config";
import { getMethodologyMessages } from "./messages";

export function MethodologyRegistry({ catalog, locale }: { catalog: MethodologyCatalog; locale: Locale }) {
  const messages = getMethodologyMessages(locale);
  return (
    <section className="data-section methodology-registry" aria-labelledby="methodology-registry-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{messages.registry.eyebrow}</p>
          <h2 id="methodology-registry-title">{messages.registry.title}</h2>
        </div>
        <span>{catalog.items.length} {catalog.dataMode} {messages.registry.definitionCount}</span>
      </div>

      <p className="methodology-guide">{messages.registry.guide}</p>

      {catalog.items.length > 0 ? (
        <div
          className="table-scroll methodology-table-scroll"
          role="region"
          aria-label={messages.registry.tableLabel}
          tabIndex={0}
        >
          <table className="calls-table methodology-table">
            <caption className="visually-hidden">{messages.registry.caption}</caption>
            <thead>
              <tr>
                <th scope="col">{messages.registry.columns.methodology}</th>
                <th scope="col">{messages.registry.columns.version}</th>
                <th scope="col">{messages.registry.columns.status}</th>
                <th scope="col">{messages.registry.columns.mode}</th>
                <th scope="col">{messages.registry.columns.effective}</th>
                <th scope="col">{messages.registry.columns.captured}</th>
                <th scope="col">{messages.registry.columns.definitionHash}</th>
                <th scope="col">{messages.registry.columns.provenance}</th>
              </tr>
            </thead>
            <tbody>
              {catalog.items.map((methodology) => (
                <tr key={`${methodology.methodologyId}@${methodology.methodologyVersion}`}>
                  <td data-label={messages.registry.columns.methodology}>
                    <strong>{methodology.methodologyId}</strong>
                    <span className="cell-secondary mono">{messages.registry.schemaPrefix} {methodology.schemaVersion}</span>
                  </td>
                  <td className="mono" data-label={messages.registry.columns.version}>{methodology.methodologyVersion}</td>
                  <td className="mono methodology-status" data-label={messages.registry.columns.status}>{methodology.status}</td>
                  <td className="mono" data-label={messages.registry.columns.mode}>{methodology.dataMode}</td>
                  <td className="mono" data-label={messages.registry.columns.effective}><KstTimestamp value={methodology.effectiveAt} /></td>
                  <td className="mono" data-label={messages.registry.columns.captured}><KstTimestamp value={methodology.capturedAt} /></td>
                  <td className="mono definition-hash" data-label={messages.registry.columns.definitionHash}>{methodology.definitionHash}</td>
                  <td className="mono" data-label={messages.registry.columns.provenance}>{methodology.provenanceId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state" role="status">
          <h3>{messages.registry.emptyTitle}</h3>
          <p>{messages.registry.emptyBody}</p>
        </div>
      )}

      <p className="section-note methodology-note">{messages.registry.note}</p>
    </section>
  );
}
