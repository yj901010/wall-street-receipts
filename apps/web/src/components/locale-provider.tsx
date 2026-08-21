"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { Locale } from "@/lib/i18n/config";
import { getCommonMessages, type CommonMessages } from "@/lib/i18n/messages";

type LocaleContextValue = {
  locale: Locale;
  messages: CommonMessages;
};

const LocaleContext = createContext<LocaleContextValue | null>(null);

type LocaleProviderProps = Readonly<{
  children: ReactNode;
  locale: Locale;
}>;

export function LocaleProvider({ children, locale }: LocaleProviderProps) {
  const value: LocaleContextValue = {
    locale,
    messages: getCommonMessages(locale),
  };

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useLocale(): LocaleContextValue {
  const value = useContext(LocaleContext);

  if (value === null) {
    throw new Error("useLocale must be used within LocaleProvider.");
  }

  return value;
}
