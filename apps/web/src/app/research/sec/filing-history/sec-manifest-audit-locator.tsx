import Link from "next/link";
import type { SecManifestAuditDemoQuery } from "@/lib/providers/sec-manifest-audit-provider";
import {
  SEC_MANIFEST_AUDIT_DEFAULT_PAGE_SIZE,
  SEC_MANIFEST_AUDIT_ROUTE,
  secManifestAuditHref,
} from "@/lib/providers/sec-manifest-audit-query";
import type { SecManifestAuditMessages } from "./messages";
import { LOCATOR_INPUT_LIMIT, type LocatorFeedback, type LocatorInputError } from "./locator-feedback";
import styles from "./sec-manifest-audit.module.css";

export function SecManifestAuditLocator({
  messages,
  demoQuery,
  invalid,
  feedback,
}: {
  messages: SecManifestAuditMessages;
  demoQuery: SecManifestAuditDemoQuery | null;
  invalid: boolean;
  feedback: LocatorFeedback;
}) {
  function errorText(error: LocatorInputError | null, format: string) {
    return error === "format" ? format : error === null ? null : messages.locator.fieldErrors[error];
  }
  const manifestError = errorText(feedback.manifestId.error, messages.locator.manifestInvalid);
  const evaluationError = errorText(feedback.evaluationAsOf.error, messages.locator.evaluationInvalid);
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
          <span className={styles.recovery}>{messages.locator.recoveryBody}</span>
          <Link className={styles.textAction} href={SEC_MANIFEST_AUDIT_ROUTE} prefetch={false}>
            {messages.locator.clear}
          </Link>
        </p>
      ) : null}
      <p className={styles.notice}>{messages.locator.description}</p>
      <div className={styles.locatorBody}>
        <form
          key={JSON.stringify([invalid, feedback])}
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
              defaultValue={feedback.manifestId.value}
              aria-invalid={manifestError ? true : undefined}
              aria-describedby={manifestError
                ? "sec-manifest-id-hint sec-manifest-id-error" : "sec-manifest-id-hint"}
            />
            <small id="sec-manifest-id-hint">{messages.locator.manifestHint}</small>
            {manifestError ? (
              <small id="sec-manifest-id-error" className={styles.fieldError}>{manifestError}</small>
            ) : null}
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
              maxLength={LOCATOR_INPUT_LIMIT}
              placeholder="2026-08-25T03:30:00.123456Z"
              pattern="[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\.[0-9]{1,6})?Z"
              defaultValue={feedback.evaluationAsOf.value}
              aria-invalid={evaluationError ? true : undefined}
              aria-describedby={evaluationError
                ? "sec-evaluation-as-of-hint sec-evaluation-as-of-error" : "sec-evaluation-as-of-hint"}
            />
            <small id="sec-evaluation-as-of-hint">{messages.locator.evaluationHint}</small>
            {evaluationError ? (
              <small id="sec-evaluation-as-of-error" className={styles.fieldError}>{evaluationError}</small>
            ) : null}
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
