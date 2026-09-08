import { getLocale } from "@/lib/i18n/server";
import { loadCpi } from "@/lib/cpi-provider.server";
import { CpiView } from "./cpi-view";
export const dynamic = "force-dynamic";
export default async function CpiPage() {
  const [locale, state] = await Promise.all([getLocale(), loadCpi()]);
  return <CpiView locale={locale} state={state} />;
}
