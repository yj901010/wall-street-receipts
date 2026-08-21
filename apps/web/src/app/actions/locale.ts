"use server";

import { cookies } from "next/headers";
import {
  LOCALE_COOKIE_NAME,
  LOCALE_COOKIE_OPTIONS,
  requireLocale,
} from "@/lib/i18n/config";

export async function setLocaleAction(formData: FormData): Promise<void> {
  const localeEntries = formData.getAll("locale");
  if (localeEntries.length !== 1) {
    throw new Error("Expected exactly one locale form value.");
  }

  const locale = requireLocale(localeEntries[0]);
  const cookieStore = await cookies();

  cookieStore.set(LOCALE_COOKIE_NAME, locale, {
    ...LOCALE_COOKIE_OPTIONS,
    secure: process.env.NODE_ENV === "production",
  });
}
