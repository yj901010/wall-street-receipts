import Link from "next/link";
import type { ReactNode } from "react";
import { KstTimestamp } from "@/components/kst-timestamp";
import type {
  SecManifestAuditAccession,
  SecManifestAuditPage,
  SecManifestAuditQuery,
  SecManifestAuditResource,
  SecManifestAuditSummary,
  SecManifestAuditView,
} from "@/lib/providers/sec-manifest-audit-provider";
import { secManifestAuditHref } from "@/lib/providers/sec-manifest-audit-query";
import type { SecManifestAuditMessages } from "./messages";
import styles from "./sec-manifest-audit.module.css";

const VIEWS: readonly SecManifestAuditView[] = [
  "summary",
  "descriptors",
  "accessions",
  "occurrences",
];

function na(value: string | number | null): string {
  return value === null ? "NA" : String(value);
}

function exactHref(
  query: SecManifestAuditQuery,
  view: SecManifestAuditView,
  page = 0,
): string {
  return secManifestAuditHref({ ...query, view, page });
}

function comparisonClass(comparison: SecManifestAuditAccession["comparison"]): string {
  if (comparison === "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT") {
    return styles.conflict;
  }
  if (comparison === "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT") {
    return styles.agreement;
  }
  return "";
}

function SummaryView({
  summary,
  messages,
}: {
  summary: SecManifestAuditSummary;
  messages: SecManifestAuditMessages;
}) {
  const facts: readonly (readonly [string, ReactNode])[] = [
    [messages.summary.manifestSchemaVersion, summary.manifestSchemaVersion],
    [messages.summary.provider, summary.provider],
    [messages.summary.product, summary.product],
    [messages.summary.reconciliationPolicy, summary.policyVersion],
    [messages.summary.selectionSha256, summary.selectionSha256],
    [messages.summary.rootCaptureId, summary.rootCaptureId],
    [messages.summary.rootCapturedAt, <KstTimestamp key="root-captured-at" value={summary.rootCapturedAt} />],
    [messages.summary.cik, summary.cik],
    [messages.summary.evidenceAvailableAt, <KstTimestamp key="evidence-available-at" value={summary.evidenceAvailableAt} />],
    [messages.summary.assembledAt, <KstTimestamp key="assembled-at" value={summary.assembledAt} />],
    [messages.summary.selectionCoverage, summary.selectionCoverage],
    [messages.summary.immutable, String(summary.immutable)],
  ] as const;
  const counts = [
    [messages.summary.advertisedDescriptors, summary.advertisedDescriptorCount],
    [messages.summary.selectedDescriptors, summary.selectedDescriptorCount],
    [messages.summary.omittedDescriptors, summary.omittedDescriptorCount],
    [messages.summary.sourceOccurrences, summary.sourceOccurrenceCount],
    [messages.summary.distinctAccessions, summary.distinctAccessionCount],
    [messages.summary.singleSource, summary.singleSourceAccessionCount],
    [messages.summary.exactAgreement, summary.exactAgreementAccessionCount],
    [messages.summary.canonicalConflict, summary.canonicalConflictAccessionCount],
  ] as const;
  const disclosures = [
    [messages.summary.coverageScope, summary.disclosure.coverageScope],
    [messages.summary.atomicSnapshot, summary.disclosure.atomicSecSnapshotClaim],
    [messages.summary.currentHistory, summary.disclosure.currentHistoryStatus],
    [messages.summary.corrections, summary.disclosure.correctionRemovalStatus],
    [messages.summary.amendments, summary.disclosure.amendmentLinkageStatus],
    [messages.summary.legalAuthority, summary.disclosure.legalAuthorityStatus],
  ] as const;

  return (
    <>
      <div className={styles.resourceHeading}>
        <div>
          <p className="eyebrow">{messages.summary.eyebrow}</p>
          <h2>{messages.summary.title}</h2>
        </div>
        <span>{summary.selectionCoverage}</span>
      </div>
      <div className={styles.summaryGrid}>
        <section className={styles.subsection} aria-labelledby="sec-manifest-identity-title">
          <h3 id="sec-manifest-identity-title">{messages.summary.identityTitle}</h3>
          <dl className={styles.facts}>
            {facts.map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd className="mono">{value}</dd>
              </div>
            ))}
          </dl>
        </section>
        <section className={styles.subsection} aria-labelledby="sec-manifest-counts-title">
          <h3 id="sec-manifest-counts-title">{messages.summary.countsTitle}</h3>
          <dl className={styles.counts}>
            {counts.map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        </section>
      </div>
      <section aria-labelledby="sec-manifest-disclosure-title">
        <div className={styles.resourceHeading}>
          <h2 id="sec-manifest-disclosure-title">{messages.summary.disclosureTitle}</h2>
        </div>
        <p className={styles.disclosureIntro}>{messages.summary.disclosureBody}</p>
        <dl className={styles.disclosures}>
          {disclosures.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd className="mono">{value}</dd>
            </div>
          ))}
        </dl>
      </section>
    </>
  );
}

function EmptyPage({ messages }: { messages: SecManifestAuditMessages }) {
  return (
    <div className={styles.empty} role="status">
      <h3>{messages.table.emptyTitle}</h3>
      <p>{messages.table.emptyBody}</p>
    </div>
  );
}

function Pagination<T>({
  page,
  query,
  messages,
}: {
  page: SecManifestAuditPage<T>;
  query: SecManifestAuditQuery;
  messages: SecManifestAuditMessages;
}) {
  return (
    <div className={styles.pagination}>
      <span>
        {messages.evidence.pageValue(page.page.number, page.page.totalPages)} · {page.page.totalElements}
      </span>
      <div className={styles.paginationLinks}>
        {!page.page.first ? (
          <Link href={exactHref(query, query.view, page.page.number - 1)}>
            {messages.table.previous}
          </Link>
        ) : null}
        {!page.page.last ? (
          <Link href={exactHref(query, query.view, page.page.number + 1)}>
            {messages.table.next}
          </Link>
        ) : null}
      </div>
    </div>
  );
}

function DescriptorTable({
  resource,
  query,
  messages,
}: {
  resource: Extract<SecManifestAuditResource, { view: "descriptors" }>;
  query: SecManifestAuditQuery;
  messages: SecManifestAuditMessages;
}) {
  const page = resource.data;
  return (
    <>
      <div className={styles.resourceHeading}>
        <h2>{messages.table.descriptorsTitle}</h2>
        <span>{messages.table.advertisedRangeNotice}</span>
      </div>
      {page.items.length === 0 ? <EmptyPage messages={messages} /> : (
        <div
          className={styles.tableScroll}
          tabIndex={0}
          role="region"
          aria-label={messages.table.descriptorsTitle}
        >
          <table className={styles.table}>
            <caption className="visually-hidden">{messages.table.descriptorsCaption}</caption>
            <thead>
              <tr>
                <th>{messages.table.ordinal}</th>
                <th>{messages.table.fileName}</th>
                <th>{messages.table.advertisedCount}</th>
                <th>{messages.table.advertisedRange}</th>
                <th>{messages.table.selectionState}</th>
                <th>{messages.table.selectedCapture}</th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((item) => (
                <tr key={item.descriptorOrdinal}>
                  <td className="mono">{item.descriptorOrdinal}</td>
                  <td className={styles.wrap}>{item.fileName}</td>
                  <td className="mono">{item.advertisedFilingCount}</td>
                  <td className="mono">{item.advertisedFilingFrom} — {item.advertisedFilingTo}</td>
                  <td className="mono">{item.selectionState}</td>
                  <td className={`${styles.wrap} mono`}>{na(item.selectedSegmentCaptureId)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className={styles.notice}>{messages.table.advertisedRangeNotice}</p>
      <Pagination page={page} query={query} messages={messages} />
    </>
  );
}

function AccessionTable({
  resource,
  query,
  messages,
}: {
  resource: Extract<SecManifestAuditResource, { view: "accessions" }>;
  query: SecManifestAuditQuery;
  messages: SecManifestAuditMessages;
}) {
  const page = resource.data;
  return (
    <>
      <div className={styles.resourceHeading}>
        <h2>{messages.table.accessionsTitle}</h2>
        <span>{messages.table.orderNotice}</span>
      </div>
      {page.items.length === 0 ? <EmptyPage messages={messages} /> : (
        <div
          className={styles.tableScroll}
          tabIndex={0}
          role="region"
          aria-label={messages.table.accessionsTitle}
        >
          <table className={styles.table}>
            <caption className="visually-hidden">{messages.table.accessionsCaption}</caption>
            <thead>
              <tr>
                <th>{messages.table.ordinal}</th>
                <th>{messages.table.accession}</th>
                <th>{messages.table.occurrenceCount}</th>
                <th>{messages.table.distinctProjections}</th>
                <th>{messages.table.comparison}</th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((item) => (
                <tr key={item.groupOrdinal}>
                  <td className="mono">{item.groupOrdinal}</td>
                  <td className="mono">{item.accessionNumber}</td>
                  <td className="mono">{item.occurrenceCount}</td>
                  <td className="mono">{item.distinctProjectionCount}</td>
                  <td className={`${comparisonClass(item.comparison)} mono`}>
                    {item.comparison}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className={styles.notice}>{messages.table.conflictNotice}</p>
      <Pagination page={page} query={query} messages={messages} />
    </>
  );
}

function OccurrenceTable({
  resource,
  query,
  messages,
}: {
  resource: Extract<SecManifestAuditResource, { view: "occurrences" }>;
  query: SecManifestAuditQuery;
  messages: SecManifestAuditMessages;
}) {
  const page = resource.data;
  return (
    <>
      <div className={styles.resourceHeading}>
        <h2>{messages.table.occurrencesTitle}</h2>
        <span>{messages.table.orderNotice}</span>
      </div>
      {page.items.length === 0 ? <EmptyPage messages={messages} /> : (
        <div
          className={styles.tableScroll}
          tabIndex={0}
          role="region"
          aria-label={messages.table.occurrencesTitle}
        >
          <table className={`${styles.table} ${styles.occurrenceTable}`}>
            <caption className="visually-hidden">{messages.table.occurrencesCaption}</caption>
            <thead>
              <tr>
                <th>{messages.table.ordinal}</th>
                <th>{messages.table.accession}</th>
                <th>{messages.table.form}</th>
                <th>{messages.table.source}</th>
                <th>{messages.table.sourceCapture}</th>
                <th>{messages.table.descriptor}</th>
                <th>{messages.table.sourceRow}</th>
                <th>{messages.table.projection}</th>
                <th>{messages.table.filingDate}</th>
                <th>{messages.table.reportDate}</th>
                <th>{messages.table.acceptedAt}</th>
                <th>{messages.table.primaryDocument}</th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((item) => (
                <tr key={item.occurrenceOrdinal}>
                  <td className="mono">{item.occurrenceOrdinal}</td>
                  <td className="mono">{item.accessionNumber}</td>
                  <td className="mono">{item.form}</td>
                  <td className="mono">{item.sourceKind}</td>
                  <td className={`${styles.wrap} mono`}>{item.sourceCaptureId}</td>
                  <td className="mono">{na(item.descriptorOrdinal)}</td>
                  <td className="mono">{item.sourceRowOrdinal}</td>
                  <td className={`${styles.wrap} mono`}>{item.projectionSha256}</td>
                  <td className="mono">{item.filingDate}</td>
                  <td className="mono">{na(item.reportDate)}</td>
                  <td className="mono"><KstTimestamp value={item.acceptedAt} /></td>
                  <td className={styles.wrap}>{na(item.primaryDocumentUri)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className={styles.notice}>{messages.table.documentNotice}</p>
      <Pagination page={page} query={query} messages={messages} />
    </>
  );
}

export function SecManifestAuditView({
  query,
  resource,
  providerMode,
  messages,
}: {
  query: SecManifestAuditQuery;
  resource: SecManifestAuditResource;
  providerMode: "fixture" | "api";
  messages: SecManifestAuditMessages;
}) {
  const data = resource.data;
  const page = resource.view === "summary" ? null : resource.data.page;
  return (
    <>
      <Link className={styles.backLink} href="/research/sec/filing-history">
        {messages.page.back}
      </Link>
      <section className={styles.presentation} aria-label={messages.presentation[providerMode]}>
        <strong>{messages.presentation[providerMode]}</strong>
        <p>
          {providerMode === "fixture"
            ? messages.presentation.fixtureBody
            : messages.presentation.apiBody}
        </p>
      </section>
      <section className={styles.resource} aria-labelledby="sec-manifest-resource-title">
        <h2 id="sec-manifest-resource-title" className="visually-hidden">
          {messages.evidence.label}
        </h2>
        <dl className={styles.requestEvidence} aria-label={messages.evidence.label}>
          <div>
            <dt>{messages.evidence.manifestId}</dt>
            <dd className="mono">{data.manifestId}</dd>
          </div>
          <div>
            <dt>{messages.evidence.evaluationAsOf}</dt>
            <dd className="mono"><KstTimestamp value={data.evaluationAsOf} /></dd>
          </div>
          <div>
            <dt>{messages.evidence.schema}</dt>
            <dd className="mono">{data.auditSchemaVersion}</dd>
          </div>
          <div>
            <dt>{messages.evidence.policy}</dt>
            <dd className="mono">{data.auditPolicyVersion}</dd>
          </div>
          {page ? (
            <>
              <div>
                <dt>{messages.evidence.fixedOrder}</dt>
                <dd className="mono">{page.order.field} {page.order.direction}</dd>
              </div>
              <div>
                <dt>{messages.evidence.page}</dt>
                <dd className="mono">{messages.evidence.pageValue(page.number, page.totalPages)}</dd>
              </div>
            </>
          ) : null}
        </dl>
        <nav className={styles.tabs} aria-label={messages.evidence.label}>
          {VIEWS.map((view) => (
            <Link
              key={view}
              aria-current={resource.view === view ? "page" : undefined}
              href={exactHref(query, view)}
            >
              {messages.tabs[view]}
            </Link>
          ))}
        </nav>
        {resource.view === "summary" ? (
          <SummaryView summary={resource.data} messages={messages} />
        ) : resource.view === "descriptors" ? (
          <DescriptorTable resource={resource} query={query} messages={messages} />
        ) : resource.view === "accessions" ? (
          <AccessionTable resource={resource} query={query} messages={messages} />
        ) : (
          <OccurrenceTable resource={resource} query={query} messages={messages} />
        )}
      </section>
    </>
  );
}
