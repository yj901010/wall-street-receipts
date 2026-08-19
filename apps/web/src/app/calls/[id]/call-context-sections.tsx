import type { AnalystCall, CallContext, EventContext, MacroSnapshot } from "@/lib/providers";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

const decimalFormatter = new Intl.NumberFormat("en-US", {
  maximumFractionDigits: 12,
});

type CallCutoff = Pick<AnalystCall, "eventTime" | "dataMode">;

function utc(value: string) {
  return `${utcFormatter.format(new Date(value))} UTC`;
}

function instantOrNa(value: string | null) {
  return value === null ? "NA" : utc(value);
}

function valueOrNa(value: string | number | null) {
  if (value === null) {
    return "NA";
  }

  return typeof value === "number" ? decimalFormatter.format(value) : value;
}

function EmptyContextEvidence({
  call,
  contextKnown,
  subject,
}: {
  call: CallCutoff;
  contextKnown: boolean;
  subject: "macro snapshot" | "scheduled-event context";
}) {
  return (
    <>
      <dl className="snapshot-metadata context-metadata" aria-label={`${subject} availability evidence`}>
        <div><dt>As of call event</dt><dd className="mono">{utc(call.eventTime)}</dd></div>
        <div><dt>Data mode</dt><dd>{call.dataMode}</dd></div>
        <div><dt>Source</dt><dd>NA</dd></div>
        <div><dt>Provenance</dt><dd>NA</dd></div>
      </dl>
      <div className="empty-state context-empty" role="status">
        <h3>{contextKnown ? "Known-empty context" : "Context unavailable"}</h3>
        <p>No {subject} was recorded for this call. Missing values remain NA.</p>
      </div>
    </>
  );
}

function MacroContext({ snapshot }: { snapshot: MacroSnapshot }) {
  return (
    <>
      <dl className="snapshot-metadata context-metadata" aria-label="Macro context provenance">
        <div><dt>Snapshot ID</dt><dd className="mono">{snapshot.macroSnapshotId}</dd></div>
        <div><dt>As of</dt><dd className="mono">{utc(snapshot.eventTime)}</dd></div>
        <div><dt>Processing time</dt><dd className="mono">{utc(snapshot.processingTime)}</dd></div>
        <div><dt>Captured</dt><dd className="mono">{utc(snapshot.capturedAt)}</dd></div>
        <div><dt>Data mode</dt><dd>{snapshot.dataMode}</dd></div>
        <div><dt>Provenance</dt><dd className="mono">{snapshot.provenanceId}</dd></div>
        <div><dt>Sources</dt><dd>Per-observation references below</dd></div>
        <div><dt>Mutation policy</dt><dd>Append-only; no update surface</dd></div>
      </dl>
      <div
        className="table-scroll context-table-scroll"
        role="region"
        aria-label="Macro observation evidence table"
        tabIndex={0}
      >
        <table className="calls-table context-table">
          <caption className="visually-hidden">Macro observations at analyst-call event time</caption>
          <thead>
            <tr>
              <th scope="col">Series</th>
              <th className="numeric" scope="col">Value</th>
              <th scope="col">Unit</th>
              <th scope="col">Observation date</th>
              <th scope="col">Released</th>
              <th scope="col">Processing</th>
              <th scope="col">Captured</th>
              <th scope="col">Vintage start</th>
              <th scope="col">Vintage end</th>
              <th scope="col">Source</th>
              <th scope="col">Provenance</th>
            </tr>
          </thead>
          <tbody>
            {snapshot.observations.map((observation) => (
              <tr key={observation.macroObservationId}>
                <td data-label="Series">
                  <strong className="mono">{observation.series}</strong>
                  {" "}
                  <span className="cell-secondary mono">{observation.macroObservationId}</span>
                </td>
                <td className="numeric mono" data-label="Value">{valueOrNa(observation.value)}</td>
                <td data-label="Unit">{observation.unit}</td>
                <td className="mono" data-label="Observation date">{observation.observationDate}</td>
                <td className="mono" data-label="Released">{utc(observation.releasedAt)}</td>
                <td className="mono" data-label="Processing">{utc(observation.processingTime)}</td>
                <td className="mono" data-label="Captured">{utc(observation.capturedAt)}</td>
                <td className="mono" data-label="Vintage start">{valueOrNa(observation.vintageStart)}</td>
                <td className="mono" data-label="Vintage end">{valueOrNa(observation.vintageEnd)}</td>
                <td className="mono" data-label="Source">{observation.sourceReferenceId}</td>
                <td className="mono" data-label="Provenance">{observation.provenanceId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="section-note context-note">
        Only the ordered observation vintages available at the analyst-call event cutoff are shown.
      </p>
    </>
  );
}

function ScheduledEventContext({ context }: { context: EventContext }) {
  return (
    <>
      <dl className="snapshot-metadata context-metadata" aria-label="Scheduled event context provenance">
        <div><dt>Context ID</dt><dd className="mono">{context.eventContextId}</dd></div>
        <div><dt>As of</dt><dd className="mono">{utc(context.eventTime)}</dd></div>
        <div><dt>Processing time</dt><dd className="mono">{utc(context.processingTime)}</dd></div>
        <div><dt>Captured</dt><dd className="mono">{utc(context.capturedAt)}</dd></div>
        <div><dt>Data mode</dt><dd>{context.dataMode}</dd></div>
        <div><dt>Provenance</dt><dd className="mono">{context.provenanceId}</dd></div>
        <div><dt>Source</dt><dd className="mono">{context.sourceReferenceId}</dd></div>
        <div><dt>Mutation policy</dt><dd>Append-only; no update surface</dd></div>
      </dl>
      <dl className="fact-grid schedule-grid" aria-label="Observed scheduled event timestamps">
        <div><dt>Earnings</dt><dd className="mono">{instantOrNa(context.earningsAt)}</dd></div>
        <div><dt>Next CPI</dt><dd className="mono">{instantOrNa(context.nextCpiAt)}</dd></div>
        <div><dt>Next FOMC</dt><dd className="mono">{instantOrNa(context.nextFomcAt)}</dd></div>
        <div><dt>Next NFP</dt><dd className="mono">{instantOrNa(context.nextNfpAt)}</dd></div>
        <div><dt>Options expiration</dt><dd className="mono">{instantOrNa(context.optionsExpirationAt)}</dd></div>
      </dl>
      <p className="section-note context-note">
        These are source-recorded schedule timestamps at the call event cutoff.
      </p>
    </>
  );
}

export function CallContextSections({ call, context }: { call: CallCutoff; context: CallContext | null }) {
  const macroSnapshot = context?.macroSnapshot ?? null;
  const eventContext = context?.eventContext ?? null;
  const contextKnown = context !== null;

  return (
    <div className="context-sections">
      <section className="detail-section context-section" aria-labelledby="macro-context-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Point-in-time evidence</p>
            <h2 id="macro-context-title">Macro context</h2>
          </div>
          <span>{macroSnapshot ? `${macroSnapshot.dataMode} · Immutable` : `${contextKnown ? "Known empty" : "Unavailable"} · ${call.dataMode}`}</span>
        </div>
        {macroSnapshot ? (
          <MacroContext snapshot={macroSnapshot} />
        ) : (
          <EmptyContextEvidence call={call} contextKnown={contextKnown} subject="macro snapshot" />
        )}
      </section>

      <section className="detail-section context-section" aria-labelledby="scheduled-event-context-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Observed schedule evidence</p>
            <h2 id="scheduled-event-context-title">Scheduled event context</h2>
          </div>
          <span>{eventContext ? `${eventContext.dataMode} · Immutable` : `${contextKnown ? "Known empty" : "Unavailable"} · ${call.dataMode}`}</span>
        </div>
        {eventContext ? (
          <ScheduledEventContext context={eventContext} />
        ) : (
          <EmptyContextEvidence call={call} contextKnown={contextKnown} subject="scheduled-event context" />
        )}
      </section>
    </div>
  );
}
