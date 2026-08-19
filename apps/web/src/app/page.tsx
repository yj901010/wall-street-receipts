import { DashboardView } from "@/components/dashboard-view";
import { marketProvider } from "@/lib/providers";

export default async function DashboardPage() {
  const snapshot = await marketProvider().dashboard();

  return <DashboardView snapshot={snapshot} />;
}
