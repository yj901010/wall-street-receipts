import { render, type RenderOptions, type RenderResult } from "@testing-library/react";
import type { ReactNode } from "react";
import { LocaleProvider } from "@/components/locale-provider";
import type { Locale } from "@/lib/i18n/config";

export function renderWithLocale(
  ui: ReactNode,
  locale: Locale = "ko",
  options?: Omit<RenderOptions, "wrapper">,
): RenderResult {
  return render(
    <LocaleProvider locale={locale}>{ui}</LocaleProvider>,
    options,
  );
}
