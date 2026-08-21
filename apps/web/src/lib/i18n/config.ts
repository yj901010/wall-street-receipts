export const SUPPORTED_LOCALES = ["ko", "en"] as const;

export type Locale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "ko";
export const LOCALE_COOKIE_NAME = "wsr_locale";
export const LOCALE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

export const LOCALE_COOKIE_OPTIONS = {
  httpOnly: true,
  maxAge: LOCALE_COOKIE_MAX_AGE_SECONDS,
  path: "/",
  sameSite: "lax",
} as const;

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && SUPPORTED_LOCALES.some((locale) => locale === value);
}

export function parseLocale(value: unknown): Locale {
  return isLocale(value) ? value : DEFAULT_LOCALE;
}

export function requireLocale(value: unknown): Locale {
  if (!isLocale(value)) {
    throw new Error("Unsupported locale. Expected exactly ko or en.");
  }

  return value;
}
