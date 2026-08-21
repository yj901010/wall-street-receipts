import Link from "next/link";
import { getLocale } from "@/lib/i18n/server";
import { getScreenerMessages } from "./messages";

export default async function ScreenerNotFound() {
  const messages = getScreenerMessages(await getLocale());
  return (
    <main className="state-page route-error">
      <p className="eyebrow">{messages.notFound.eyebrow}</p>
      <h1>{messages.notFound.title}</h1>
      <p>{messages.notFound.body}</p>
      <div className="state-actions">
        <Link className="text-action" href="/calls">{messages.notFound.calls}</Link>
        <Link className="text-action" href="/methodology">{messages.notFound.methodology}</Link>
      </div>
    </main>
  );
}
