import { notFound } from "next/navigation";
import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { SCREENER_SHELL_STATE } from "@/lib/screener-shell-state";
import { getScreenerMessages } from "./messages";
import { ScreenerShell } from "./screener-shell";

type ScreenerSearchParams = Record<string, string | string[] | undefined>;

export function isQueryFreeScreenerRequest(searchParams: ScreenerSearchParams) {
  return Object.keys(searchParams).length === 0;
}

export default async function ScreenerPage({
  searchParams,
}: {
  searchParams: Promise<ScreenerSearchParams>;
}) {
  if (!isQueryFreeScreenerRequest(await searchParams)) notFound();
  const locale = await getLocale();
  const messages = getScreenerMessages(locale);

  return (
    <main>
      <SiteHeader current="screener" dataMode={SCREENER_SHELL_STATE.dataMode} />

      <div className="page-shell screener-shell">
        <section className="page-heading screener-heading" aria-labelledby="screener-title">
          <div>
            <p className="eyebrow">{messages.page.eyebrow}</p>
            <h1 id="screener-title">{messages.page.title}</h1>
            <p className="page-summary">{messages.page.summary}</p>
          </div>
        </section>

        <ScreenerShell state={SCREENER_SHELL_STATE} locale={locale} />
      </div>
    </main>
  );
}
