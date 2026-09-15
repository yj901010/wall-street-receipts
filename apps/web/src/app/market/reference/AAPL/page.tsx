import type { Metadata } from "next";
import { getLocale } from "@/lib/i18n/server";
import { widgetMode, WIDGET_DOCUMENTATION } from "./config";
import { referenceMessages } from "./messages";
import { ReferenceWidget } from "./reference-widget";
import styles from "./reference.module.css";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default async function ReferenceQuotePage() {
  const locale = await getLocale();
  const text = referenceMessages(locale);
  const mode = widgetMode(process.env.VUNELIX_REFERENCE_WIDGET);
  return <main className={`page-shell ${styles.shell}`}>
    {/* No SPA navigation after loading third-party code: a document exit is required. */}
    <a href="/market">← {text.back}</a>
    <header className={styles.heading}>
      <p className="eyebrow">WALL STREET RECEIPTS · PILOT</p>
      <h1>{text.title}</h1>
      <p>{text.summary}</p>
    </header>
    <p className={styles.mode}>{text.mode}</p>
    <p>{text.source}</p>
    <p>{text.symbol}</p>
    <p className={styles.notice}>{text.caution}</p>
    <p>{text.time}</p>
    <ReferenceWidget mode={mode} locale={locale} />
    <footer className={styles.footer}>
      <a href={WIDGET_DOCUMENTATION} target="_blank" rel="noopener noreferrer">{text.docs}</a>
      <a href="https://vunelix.com/terms" target="_blank" rel="noopener noreferrer">{text.terms}</a>
    </footer>
  </main>;
}
