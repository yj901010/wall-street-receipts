import { notFound } from "next/navigation";
import { getLocale } from "@/lib/i18n/server";
import { CALL_ID, RECEIPT_ID, type ReceiptState } from "@/lib/scoring-receipts";
import { loadScoringReceipts } from "@/lib/scoring-receipts.server";
import { ReceiptView } from "./receipt-view";

export const dynamic = "force-dynamic";
export default async function ScoringReceiptPage({ params, searchParams }: {
  params: Promise<{id: string}>; searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const [{ id }, query, locale] = await Promise.all([params, searchParams, getLocale()]);
  if (!CALL_ID.test(id)) notFound();
  const selectedId = typeof query.receiptId === "string" ? query.receiptId.slice(0, 128) : undefined;
  const valid = Object.keys(query).every(key => key === "receiptId") && !Array.isArray(query.receiptId)
    && (query.receiptId === undefined || query.receiptId === "" || (typeof query.receiptId === "string" && RECEIPT_ID.test(query.receiptId)));
  const state: ReceiptState = valid ? await loadScoringReceipts(id, selectedId || undefined) : {kind: "invalid"};
  return <ReceiptView callId={id} selectedId={selectedId} state={state} locale={locale} />;
}
