import { getLocale } from "@/lib/i18n/server";
import { getCallsMessages } from "../messages";

export default async function CallDetailLoading() {
  const messages = getCallsMessages(await getLocale()).states;

  return (
    <main className="state-page route-loading" aria-busy="true" aria-live="polite">
      <p className="eyebrow">{messages.detailLoadingEyebrow}</p>
      <h1>{messages.detailLoadingTitle}</h1>
      <p>{messages.detailLoadingDescription}</p>
    </main>
  );
}
