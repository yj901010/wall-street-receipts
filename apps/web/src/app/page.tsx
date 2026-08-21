import { DashboardView } from "@/components/dashboard-view";
import { getLocale } from "@/lib/i18n/server";
import { marketProvider } from "@/lib/providers";

export default async function DashboardPage() {
  const locale = await getLocale();
  const snapshot = await marketProvider().dashboard();

  return <DashboardView snapshot={snapshot} locale={locale} />;
}
