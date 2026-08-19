import type { MethodologyCatalog } from "@/lib/providers";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

export function MethodologyRegistry({ catalog }: { catalog: MethodologyCatalog }) {
  return (
    <section className="data-section methodology-registry" aria-labelledby="methodology-registry-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Definition identity</p>
          <h2 id="methodology-registry-title">Versioned methodology registry</h2>
        </div>
        <span>{catalog.items.length} {catalog.dataMode} definitions</span>
      </div>

      <p className="methodology-guide">
        Schema identifies the record shape. Methodology version and definition hash preserve the
        immutable definition identity; Effective is its stated start instant, while Captured records
        when the source acquired that evidence. The fixture does not contain the formula body.
      </p>

      {catalog.items.length > 0 ? (
        <div
          className="table-scroll methodology-table-scroll"
          role="region"
          aria-label="Methodology registry table"
          tabIndex={0}
        >
          <table className="calls-table methodology-table">
            <caption className="visually-hidden">Versioned scoring methodology definitions</caption>
            <thead>
              <tr>
                <th scope="col">Methodology</th>
                <th scope="col">Version</th>
                <th scope="col">Status</th>
                <th scope="col">Mode</th>
                <th scope="col">Effective</th>
                <th scope="col">Captured</th>
                <th scope="col">Definition hash</th>
                <th scope="col">Provenance</th>
              </tr>
            </thead>
            <tbody>
              {catalog.items.map((methodology) => (
                <tr key={`${methodology.methodologyId}@${methodology.methodologyVersion}`}>
                  <td data-label="Methodology">
                    <strong>{methodology.methodologyId}</strong>
                    <span className="cell-secondary mono">schema {methodology.schemaVersion}</span>
                  </td>
                  <td className="mono" data-label="Version">{methodology.methodologyVersion}</td>
                  <td className="mono methodology-status" data-label="Status">{methodology.status}</td>
                  <td className="mono" data-label="Mode">{methodology.dataMode}</td>
                  <td className="mono" data-label="Effective">{utc(methodology.effectiveAt)}</td>
                  <td className="mono" data-label="Captured">{utc(methodology.capturedAt)}</td>
                  <td className="mono definition-hash" data-label="Definition hash">{methodology.definitionHash}</td>
                  <td className="mono" data-label="Provenance">{methodology.provenanceId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state" role="status">
          <h3>No methodology definitions are recorded.</h3>
          <p>No substitute version, hash, or calculation result was generated.</p>
        </div>
      )}

      <p className="section-note methodology-note">
        MODEL_ONLY identifies a versioned definition contract. Deterministic return, alpha, hit-rate,
        and ranking calculations remain deferred to P3; this page calculates no outcome values.
      </p>
    </section>
  );
}
