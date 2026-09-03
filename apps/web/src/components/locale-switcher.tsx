"use client";

import { useEffect, useRef } from "react";
import { useFormStatus } from "react-dom";
import { setLocaleAction } from "@/app/actions/locale";
import { useLocale } from "@/components/locale-provider";
import type { Locale } from "@/lib/i18n/config";

type LocaleChoiceProps = Readonly<{
  activeLocale: Locale;
  locale: Locale;
  label: string;
  shortLabel: string;
}>;

function LocaleChoice({ activeLocale, locale, label, shortLabel }: LocaleChoiceProps) {
  const { pending } = useFormStatus();

  return (
    <button
      aria-label={label}
      aria-pressed={activeLocale === locale}
      className="locale-switcher-option"
      disabled={pending}
      lang={locale}
      name="locale"
      type="submit"
      value={locale}
    >
      {shortLabel}
    </button>
  );
}

function LocalePendingStatus() {
  const { pending } = useFormStatus();
  const { messages } = useLocale();

  return (
    <span aria-live="polite" className="visually-hidden">
      {pending ? messages.siteHeader.localeChangePending : ""}
    </span>
  );
}

export function LocaleSwitcher() {
  const { locale, messages } = useLocale();
  const formRef = useRef<HTMLFormElement>(null);
  const previousLocale = useRef(locale);

  useEffect(() => {
    if (previousLocale.current === locale) return;

    formRef.current
      ?.querySelector<HTMLButtonElement>(`button[name="locale"][value="${locale}"]`)
      ?.focus();
    previousLocale.current = locale;
  }, [locale]);

  return (
    <form
      action={setLocaleAction}
      aria-label={messages.siteHeader.localeSwitcherLabel}
      className="locale-switcher"
      ref={formRef}
    >
      <LocaleChoice
        activeLocale={locale}
        label={messages.siteHeader.koreanOptionLabel}
        locale="ko"
        shortLabel="KO"
      />
      <LocaleChoice
        activeLocale={locale}
        label={messages.siteHeader.englishOptionLabel}
        locale="en"
        shortLabel="EN"
      />
      <LocalePendingStatus />
    </form>
  );
}
