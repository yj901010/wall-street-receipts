import { notFound } from "next/navigation";
import { SiteHeader } from "@/components/site-header";
import { SCREENER_SHELL_STATE } from "@/lib/screener-shell-state";
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

  return (
    <main>
      <SiteHeader current="screener" dataMode={SCREENER_SHELL_STATE.dataMode} />

      <div className="page-shell screener-shell">
        <section className="page-heading screener-heading" aria-labelledby="screener-title">
          <div>
            <p className="eyebrow">Application-owned release boundary</p>
            <h1 id="screener-title">Historical equity screening is deferred.</h1>
            <p className="page-summary">
              This query-free route publishes only the product phase decision. It does not expose
              executable filters, results, or a synthetic preview while the canonical P8 feature
              catalog is unavailable.
            </p>
          </div>
        </section>

        <ScreenerShell state={SCREENER_SHELL_STATE} />
      </div>
    </main>
  );
}
