import Link from "next/link";
import type { SecManifestAuditDemoQuery } from "@/lib/providers/sec-manifest-audit-provider";
import {
  SEC_MANIFEST_AUDIT_DEFAULT_PAGE_SIZE,
  SEC_MANIFEST_AUDIT_ROUTE,
  secManifestAuditHref,
} from "@/lib/providers/sec-manifest-audit-query";
import type { SecManifestAuditMessages } from "./messages";
import styles from "./sec-manifest-audit.module.css";

export function SecManifestAuditLocator({
  messages,
  demoQuery,
  invalid,
}: {
  messages: SecManifestAuditMessages;
  demoQuery: SecManifestAuditDemoQuery | null;
  invalid: boolean;
}) {
  return (
    <section className={styles.locator} aria-labelledby="sec-manifest-locator-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{messages.locator.eyebrow}</p>
          <h2 id="sec-manifest-locator-title">{messages.locator.title}</h2>
        </div>
      </div>
      {invalid ? (
        <p className={styles.invalid} role="alert">
          <strong>{messages.locator.invalidTitle}</strong>
          {messages.locator.invalidBody}
        </p>
      ) : null}
      <p className={styles.notice}>{messages.locator.description}</p>
      <div className={styles.locatorBody}>
        <form
          className={styles.locatorForm}
          action={SEC_MANIFEST_AUDIT_ROUTE}
          method="get"
          aria-label={messages.locator.title}
        >
          <div className={styles.field}>
            <label htmlFor="sec-manifest-id">{messages.locator.manifestId}</label>
            <input
              id="sec-manifest-id"
              name="manifestId"
              type="text"
              inputMode="text"
              autoComplete="off"
              spellCheck={false}
              required
              minLength={64}
              maxLength={64}
              pattern="[0-9a-f]{64}"
              aria-describedby="sec-manifest-id-hint"
            />
            <small id="sec-manifest-id-hint">{messages.locator.manifestHint}</small>
          </div>
          <div className={styles.field}>
            <label htmlFor="sec-evaluation-as-of">{messages.locator.evaluationAsOf}</label>
            <input
              id="sec-evaluation-as-of"
              name="evaluationAsOf"
              type="text"
              inputMode="text"
              autoComplete="off"
              spellCheck={false}
              required
              placeholder="2026-08-25T03:30:00.123456Z"
              pattern="[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\.[0-9]{1,6})?Z"
              aria-describedby="sec-evaluation-as-of-hint"
            />
            <small id="sec-evaluation-as-of-hint">{messages.locator.evaluationHint}</small>
          </div>
          <input type="hidden" name="view" value="summary" />
          <button className={styles.submit} type="submit">
            {messages.locator.submit}
          </button>
        </form>
        {demoQuery ? (
          <aside className={styles.demoPanel} aria-labelledby="sec-demo-title">
            <p className="eyebrow">{messages.locator.demoEyebrow}</p>
            <h3 id="sec-demo-title">{messages.locator.demoTitle}</h3>
            <p>{messages.locator.demoBody}</p>
            <Link
              className={styles.textAction}
              href={secManifestAuditHref({
                ...demoQuery,
                view: "summary",
                page: 0,
                size: SEC_MANIFEST_AUDIT_DEFAULT_PAGE_SIZE,
              })}
            >
              {messages.locator.demoOpen}
            </Link>
          </aside>
        ) : null}
      </div>
    </section>
  );
}
