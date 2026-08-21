import Link from "next/link";
import { SiteHeader } from "@/components/site-header";
import { getLocale } from "@/lib/i18n/server";
import { getCallsMessages } from "../messages";

export default async function CallNotFound() {
  const messages = getCallsMessages(await getLocale()).states;

  return (
    <main>
      <SiteHeader current="calls" dataMode="DEMO" />
      <div className="page-shell state-page">
        <p className="eyebrow">{messages.notFoundEyebrow}</p>
        <h1>{messages.notFoundTitle}</h1>
        <p>{messages.notFoundDescription}</p>
        <Link className="text-action" href="/calls">{messages.returnCalls}</Link>
      </div>
    </main>
  );
}
