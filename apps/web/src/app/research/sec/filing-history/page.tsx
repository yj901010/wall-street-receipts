import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { secManifestAuditProvider } from "@/lib/providers/sec-manifest-audit-provider.server";
import { parseSecManifestAuditRoute } from "@/lib/providers/sec-manifest-audit-query";
import { getSecManifestAuditMessages } from "./messages";
import { SecManifestAuditLocator } from "./sec-manifest-audit-locator";
import { SecManifestAuditView } from "./sec-manifest-audit-view";
import styles from "./sec-manifest-audit.module.css";

export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const messages = getSecManifestAuditMessages(await getLocale());
  return {
    title: `${messages.page.title} · Wall Street Receipts`,
    description: messages.page.summary,
  };
}

export default async function SecFilingHistoryAuditPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const [raw, locale] = await Promise.all([searchParams, getLocale()]);
  const state = parseSecManifestAuditRoute(raw);
  const messages = getSecManifestAuditMessages(locale);
  const provider = await secManifestAuditProvider();
  const resource = state.kind === "query"
    ? await provider.findExact(state.query)
    : null;
  const syntheticDemo = state.kind === "query" && resource !== null
    && provider.syntheticDemoManifestId === state.query.manifestId;

  if (state.kind === "query" && resource === null) {
    notFound();
  }

  return (
    <main>
      <SiteHeader
        current="secEvidence"
        dataMode={provider.mode === "fixture" || syntheticDemo ? "DEMO" : undefined}
      />
      <div className={`page-shell ${styles.shell}`}>
        <section className={`page-heading ${styles.heading}`} aria-labelledby="sec-audit-title">
          <div>
            <p className="eyebrow">{messages.page.eyebrow}</p>
            <h1 id="sec-audit-title">{messages.page.title}</h1>
            <p className="page-summary">{messages.page.summary}</p>
          </div>
        </section>

        <section className={styles.policy} aria-label={messages.policy.label}>
          <dl className={styles.policyGrid}>
            <div>
              <dt>{messages.policy.exactTitle}</dt>
              <dd>{messages.policy.exactBody}</dd>
            </div>
            <div>
              <dt>{messages.policy.noSelectorTitle}</dt>
              <dd>{messages.policy.noSelectorBody}</dd>
            </div>
            <div>
              <dt>{messages.policy.noNowTitle}</dt>
              <dd>{messages.policy.noNowBody}</dd>
            </div>
          </dl>
        </section>

        {state.kind === "query" && resource ? (
          <SecManifestAuditView
            query={state.query}
            resource={resource}
            providerMode={syntheticDemo ? "fixture" : provider.mode}
            messages={messages}
          />
        ) : (
          <SecManifestAuditLocator
            messages={messages}
            demoQuery={provider.demoQuery}
            invalid={state.kind === "invalid"}
          />
        )}
      </div>
    </main>
  );
}
