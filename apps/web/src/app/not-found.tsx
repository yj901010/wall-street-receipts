import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { getCommonMessages } from "@/lib/i18n/messages";
import { getLocale } from "@/lib/i18n/server";

export default async function NotFound() {
  const messages = getCommonMessages(await getLocale()).notFound;

  return (
    <main>
      <SiteHeader />
      <div className="state-page route-not-found">
        <p className="eyebrow">{messages.eyebrow}</p>
        <h1>{messages.title}</h1>
        <p>{messages.body}</p>
        <div className="state-actions">
          <Link className="text-action" href="/">{messages.dashboard}</Link>
          <Link className="text-action" href="/calls">{messages.calls}</Link>
        </div>
      </div>
    </main>
  );
}
