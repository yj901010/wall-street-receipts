import { cookies } from "next/headers";
import { LOCALE_COOKIE_NAME, parseLocale, type Locale } from "./config";
import { getCommonMessages, type CommonMessages } from "./messages";

export async function getLocale(): Promise<Locale> {
  const cookieStore = await cookies();
  return parseLocale(cookieStore.get(LOCALE_COOKIE_NAME)?.value);
}

export async function getServerCommonMessages(): Promise<CommonMessages> {
  return getCommonMessages(await getLocale());
}
