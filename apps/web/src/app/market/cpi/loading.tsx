import { getLocale } from "@/lib/i18n/server";
import { SiteHeader } from "@/components/site-header";
import { cpiMessages } from "./messages";
export default async function Loading() {
  return <main><SiteHeader current="market" /><div className="page-shell" role="status">{cpiMessages(await getLocale()).loading}</div></main>;
}
