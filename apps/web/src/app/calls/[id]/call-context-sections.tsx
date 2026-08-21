import type { Locale } from "@/lib/i18n/config";
import type { AnalystCall, CallContext, EventContext, MacroSnapshot } from "@/lib/providers";
import { getCallsMessages, type CallsMessages } from "../messages";

const utcFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

const decimalFormatter = new Intl.NumberFormat("en-US", {
  maximumFractionDigits: 12,
});

type CallCutoff = Pick<AnalystCall, "eventTime" | "dataMode">;
type ContextMessages = CallsMessages["context"];

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
  messages,
  subject,
}: {
  call: CallCutoff;
  contextKnown: boolean;
  messages: ContextMessages;
  subject: string;
}) {
  return (
    <>
      <dl className="snapshot-metadata context-metadata" aria-label={messages.availabilityEvidence(subject)}>
        <div><dt>{messages.asOfCallEvent}</dt><dd className="mono">{utc(call.eventTime)}</dd></div>
        <div><dt>{messages.dataMode}</dt><dd>{call.dataMode}</dd></div>
        <div><dt>{messages.source}</dt><dd>NA</dd></div>
        <div><dt>{messages.provenance}</dt><dd>NA</dd></div>
      </dl>
      <div className="empty-state context-empty" role="status">
        <h3>{contextKnown ? messages.knownEmptyContext : messages.contextUnavailable}</h3>
        <p>{messages.missingEvidence(subject)}</p>
      </div>
    </>
  );
}

function MacroContext({
  messages,
  snapshot,
}: {
  messages: ContextMessages;
  snapshot: MacroSnapshot;
}) {
  return (
    <>
      <dl className="snapshot-metadata context-metadata" aria-label={messages.macroProvenanceLabel}>
        <div><dt>{messages.snapshotId}</dt><dd className="mono">{snapshot.macroSnapshotId}</dd></div>
        <div><dt>{messages.asOf}</dt><dd className="mono">{utc(snapshot.eventTime)}</dd></div>
        <div><dt>{messages.processingTime}</dt><dd className="mono">{utc(snapshot.processingTime)}</dd></div>
        <div><dt>{messages.captured}</dt><dd className="mono">{utc(snapshot.capturedAt)}</dd></div>
        <div><dt>{messages.dataMode}</dt><dd>{snapshot.dataMode}</dd></div>
        <div><dt>{messages.provenance}</dt><dd className="mono">{snapshot.provenanceId}</dd></div>
        <div><dt>{messages.sources}</dt><dd>{messages.perObservationSources}</dd></div>
        <div><dt>{messages.mutationPolicy}</dt><dd>{messages.appendOnly}</dd></div>
      </dl>
      <div
        className="table-scroll context-table-scroll"
        role="region"
        aria-label={messages.macroTableRegionLabel}
        tabIndex={0}
      >
        <table className="calls-table context-table">
          <caption className="visually-hidden">{messages.macroTableCaption}</caption>
          <thead>
            <tr>
              <th scope="col">{messages.series}</th>
              <th className="numeric" scope="col">{messages.value}</th>
              <th scope="col">{messages.unit}</th>
              <th scope="col">{messages.observationDate}</th>
              <th scope="col">{messages.released}</th>
              <th scope="col">{messages.processing}</th>
              <th scope="col">{messages.captured}</th>
              <th scope="col">{messages.vintageStart}</th>
              <th scope="col">{messages.vintageEnd}</th>
              <th scope="col">{messages.source}</th>
              <th scope="col">{messages.provenance}</th>
            </tr>
          </thead>
          <tbody>
            {snapshot.observations.map((observation) => (
              <tr key={observation.macroObservationId}>
                <td data-field="series" data-label={messages.series}>
                  <strong className="mono">{observation.series}</strong>
                  {" "}
                  <span className="cell-secondary mono">{observation.macroObservationId}</span>
                </td>
                <td className="numeric mono" data-field="value" data-label={messages.value}>{valueOrNa(observation.value)}</td>
                <td data-field="unit" data-label={messages.unit}>{observation.unit}</td>
                <td className="mono" data-field="observation-date" data-label={messages.observationDate}>{observation.observationDate}</td>
                <td className="mono" data-field="released" data-label={messages.released}>{utc(observation.releasedAt)}</td>
                <td className="mono" data-field="processing" data-label={messages.processing}>{utc(observation.processingTime)}</td>
                <td className="mono" data-field="captured" data-label={messages.captured}>{utc(observation.capturedAt)}</td>
                <td className="mono" data-field="vintage-start" data-label={messages.vintageStart}>{valueOrNa(observation.vintageStart)}</td>
                <td className="mono" data-field="vintage-end" data-label={messages.vintageEnd}>{valueOrNa(observation.vintageEnd)}</td>
                <td className="mono" data-field="source" data-label={messages.source}>{observation.sourceReferenceId}</td>
                <td className="mono" data-field="provenance" data-label={messages.provenance}>{observation.provenanceId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="section-note context-note">{messages.macroNote}</p>
    </>
  );
}

function ScheduledEventContext({
  context,
  messages,
}: {
  context: EventContext;
  messages: ContextMessages;
}) {
  return (
    <>
      <dl className="snapshot-metadata context-metadata" aria-label={messages.scheduledProvenanceLabel}>
        <div><dt>{messages.contextId}</dt><dd className="mono">{context.eventContextId}</dd></div>
        <div><dt>{messages.asOf}</dt><dd className="mono">{utc(context.eventTime)}</dd></div>
        <div><dt>{messages.processingTime}</dt><dd className="mono">{utc(context.processingTime)}</dd></div>
        <div><dt>{messages.captured}</dt><dd className="mono">{utc(context.capturedAt)}</dd></div>
        <div><dt>{messages.dataMode}</dt><dd>{context.dataMode}</dd></div>
        <div><dt>{messages.provenance}</dt><dd className="mono">{context.provenanceId}</dd></div>
        <div><dt>{messages.source}</dt><dd className="mono">{context.sourceReferenceId}</dd></div>
        <div><dt>{messages.mutationPolicy}</dt><dd>{messages.appendOnly}</dd></div>
      </dl>
      <dl className="fact-grid schedule-grid" aria-label={messages.scheduleValuesLabel}>
        <div><dt>{messages.earnings}</dt><dd className="mono">{instantOrNa(context.earningsAt)}</dd></div>
        <div><dt>{messages.nextCpi}</dt><dd className="mono">{instantOrNa(context.nextCpiAt)}</dd></div>
        <div><dt>{messages.nextFomc}</dt><dd className="mono">{instantOrNa(context.nextFomcAt)}</dd></div>
        <div><dt>{messages.nextNfp}</dt><dd className="mono">{instantOrNa(context.nextNfpAt)}</dd></div>
        <div><dt>{messages.optionsExpiration}</dt><dd className="mono">{instantOrNa(context.optionsExpirationAt)}</dd></div>
      </dl>
      <p className="section-note context-note">{messages.scheduledNote}</p>
    </>
  );
}

export function CallContextSections({
  call,
  context,
  locale,
}: {
  call: CallCutoff;
  context: CallContext | null;
  locale: Locale;
}) {
  const messages = getCallsMessages(locale).context;
  const macroSnapshot = context?.macroSnapshot ?? null;
  const eventContext = context?.eventContext ?? null;
  const contextKnown = context !== null;

  return (
    <div className="context-sections">
      <section className="detail-section context-section" aria-labelledby="macro-context-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">{messages.pointInTimeEvidence}</p>
            <h2 id="macro-context-title">{messages.macroContext}</h2>
          </div>
          <span>{macroSnapshot
            ? `${macroSnapshot.dataMode} · ${messages.immutable}`
            : `${contextKnown ? messages.knownEmpty : messages.unavailable} · ${call.dataMode}`}</span>
        </div>
        {macroSnapshot ? (
          <MacroContext messages={messages} snapshot={macroSnapshot} />
        ) : (
          <EmptyContextEvidence
            call={call}
            contextKnown={contextKnown}
            messages={messages}
            subject={messages.macroSubject}
          />
        )}
      </section>

      <section className="detail-section context-section" aria-labelledby="scheduled-event-context-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">{messages.observedScheduleEvidence}</p>
            <h2 id="scheduled-event-context-title">{messages.scheduledEventContext}</h2>
          </div>
          <span>{eventContext
            ? `${eventContext.dataMode} · ${messages.immutable}`
            : `${contextKnown ? messages.knownEmpty : messages.unavailable} · ${call.dataMode}`}</span>
        </div>
        {eventContext ? (
          <ScheduledEventContext context={eventContext} messages={messages} />
        ) : (
          <EmptyContextEvidence
            call={call}
            contextKnown={contextKnown}
            messages={messages}
            subject={messages.scheduledSubject}
          />
        )}
      </section>
    </div>
  );
}
