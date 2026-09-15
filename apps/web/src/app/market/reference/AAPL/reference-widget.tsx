"use client";

import { createElement, useEffect, useRef, useState } from "react";
import type { Locale } from "@/lib/i18n/config";
import { PILOT_PATH, SCRIPT_TIMEOUT_MS, WIDGET_SCRIPT, type WidgetMode, widgetAttributes } from "./config";
import { referenceMessages } from "./messages";
import styles from "./reference.module.css";

type LoadState = "idle" | "requesting" | "loaded" | "failed";

export function ReferenceWidget({ mode, locale }: { mode: WidgetMode; locale: Locale }) {
  const text = referenceMessages(locale);
  const [state, setState] = useState<LoadState>("idle");
  const [requested, setRequested] = useState(false);
  const scriptStarted = useRef(false);

  useEffect(() => {
    if (!requested || mode !== "enabled" || scriptStarted.current) return;
    scriptStarted.current = true;
    const script = document.createElement("script");
    script.type = "module";
    script.src = WIDGET_SCRIPT;
    script.referrerPolicy = "strict-origin";
    script.dataset.wsrReference = "vunelix";
    let settled = false;
    function settle(next: "loaded" | "failed") {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      setState(next);
    }
    const timer = setTimeout(() => settle("failed"), SCRIPT_TIMEOUT_MS);
    script.onload = () => settle("loaded");
    script.onerror = () => settle("failed");
    document.body.append(script);
    // Removing a script does not stop executed vendor code. This isolated pilot
    // uses full-document navigation for exit/retry; never promise SPA cleanup.
    // The effect starts only after a click (not during StrictMode's mount probe).
    return () => {
      settled = true;
      clearTimeout(timer);
      script.onload = null;
      script.onerror = null;
    };
  }, [requested, mode]);

  if (mode !== "enabled") {
    return <section role="status" className={styles.state}>
      <h2>{mode === "disabled" ? text.disabled : text.invalid}</h2>
      <p>{mode === "disabled" ? text.disabledBody : text.invalidBody}</p>
    </section>;
  }

  return <section aria-label={text.region}>
    <p className={styles.privacy}>{text.privacy}</p>
    {!requested && <button className={styles.load} onClick={() => {
      setState("requesting");
      setRequested(true);
    }}>{text.load}</button>}
    <p role="status" aria-live="polite">{text[state]}</p>
    {requested && <>
      <p>{state === "failed" ? text.failedBody : text.loadedBody}</p>
      <div className={styles.widget}>
        {createElement("vunelix-symbol-overview", widgetAttributes)}
      </div>
      {/* A plain anchor intentionally unloads the whole document and its vendor code. */}
      <a href={PILOT_PATH} className={styles.reset}>{text.reset}</a>
    </>}
    <noscript>{text.noscript}</noscript>
  </section>;
}
