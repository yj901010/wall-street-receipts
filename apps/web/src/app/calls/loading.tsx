import { getLocale } from "@/lib/i18n/server";
import { getCallsMessages } from "./messages";

export default async function CallsLoading() {
  const messages = getCallsMessages(await getLocale()).states;

  return (
    <main className="state-page route-loading" aria-busy="true" aria-live="polite">
      <p className="eyebrow">{messages.listLoadingEyebrow}</p>
      <h1>{messages.listLoadingTitle}</h1>
      <p>{messages.listLoadingDescription}</p>
    </main>
  );
}
