"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale } from "@/components/locale-provider";
import { parseSecManifestAuditRoute } from "@/lib/providers/sec-manifest-audit-query";
import { locatorFeedback } from "./locator-feedback";
import { getSecManifestAuditMessages } from "./messages";
import { SecManifestAuditLocator } from "./sec-manifest-audit-locator";
import styles from "./sec-manifest-audit.module.css";

function RecoveryFromUrl() {
  const search = useSearchParams();
  const { locale } = useLocale();
  if (search === null) return null;
  // Keep duplicates as arrays so the existing strict parser rejects them.
  // These are attempted URL keys, never a verified response or a new read.
  const raw = Object.fromEntries([...new Set(search.keys())].map((key) => {
    const values = search.getAll(key);
    return [key, values.length === 1 ? values[0] : values];
  }));
  const state = parseSecManifestAuditRoute(raw);
  if (state.kind !== "query") return null;
  const messages = getSecManifestAuditMessages(locale);
  return (
    <details className={`${styles.refinement} ${styles.failedRecovery}`} key={JSON.stringify(state.query)}>
      <summary>{messages.locator.recoverFailed}</summary>
      <p className={styles.notice}>{messages.locator.recoverFailedBody}</p>
      <SecManifestAuditLocator
        messages={{ ...messages, locator: { ...messages.locator, eyebrow: messages.locator.attemptedKeys } }}
        demoQuery={null}
        invalid={false}
        feedback={locatorFeedback({
          manifestId: state.query.manifestId,
          evaluationAsOf: state.query.evaluationAsOf,
        })}
      />
    </details>
  );
}

export function FailedQueryRecovery() {
  // Error/not-found boundaries do not receive page searchParams. Reading the
  // current URL is the only client work; the existing GET form owns submission.
  return <Suspense fallback={null}><RecoveryFromUrl /></Suspense>;
}
