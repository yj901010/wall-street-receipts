import { getDashboardMessages } from "@/components/dashboard-messages";
import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";

export default async function DashboardLoading() {
  const messages = getDashboardMessages(await getLocale());
  return (
    <main>
      <SiteHeader current="dashboard" dataMode="DEMO" />
      <div className="state-page route-loading" aria-busy="true" aria-live="polite">
        <p className="eyebrow">{messages.loading.eyebrow}</p>
        <h1>{messages.loading.title}</h1>
        <p>{messages.loading.body}</p>
      </div>
    </main>
  );
}
