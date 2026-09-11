import { CALL_ID, RECEIPT_ID, receipt, receiptPage, type ReceiptState } from "./scoring-receipts";

type Options = { mode?: string; baseUrl?: string; fetcher?: typeof fetch };
const MAX_BYTES = 262144;
export async function loadScoringReceipts(callId: string, selectedId: string | undefined, options: Options = {}): Promise<ReceiptState> {
  if (typeof window !== "undefined") throw new Error("Scoring transport is server-only");
  if (!CALL_ID.test(callId) || (selectedId !== undefined && !RECEIPT_ID.test(selectedId))) return { kind: "invalid" };
  const mode = options.mode ?? process.env.SCORING_RECEIPTS_PROVIDER ?? "disabled";
  if (mode === "disabled") return { kind: "disabled" };
  if (mode !== "api") return { kind: "unavailable" };
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), 5000);
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
  try {
    const base = new URL(options.baseUrl ?? process.env.API_BASE_URL ?? "");
    if (!["http:", "https:"].includes(base.protocol) || base.username || base.password || base.search || base.hash) throw new Error();
    base.pathname = base.pathname.replace(/\/+$/, "") + "/";
    const route = `v1/calls/${encodeURIComponent(callId)}/scoring-receipts${selectedId ? `/${selectedId}` : ""}`;
    const response = await (options.fetcher ?? fetch)(new URL(route, base), { method: "GET", cache: "no-store", redirect: "error", signal: controller.signal, headers: { Accept: "application/json" } });
    if (response.status === 404) { await response.body?.cancel(); return { kind: "missing" }; }
    if (response.status !== 200 || response.headers.get("content-type")?.split(";", 1)[0].trim().toLowerCase() !== "application/json") {
      await response.body?.cancel(); throw new Error();
    }
    const length = response.headers.get("content-length");
    if (length !== null && (!/^\d+$/.test(length) || Number(length) > MAX_BYTES)) { await response.body?.cancel(); throw new Error(); }
    reader = response.body?.getReader(); if (!reader) throw new Error();
    const chunks: Uint8Array[] = []; let size = 0;
    while (true) { const next = await reader.read(); if (next.done) break; size += next.value.byteLength; if (size > MAX_BYTES) throw new Error(); chunks.push(next.value); }
    const bytes = new Uint8Array(size); let offset = 0; for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    const payload: unknown = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
    return selectedId ? { kind: "ready", items: [receipt(payload, callId, selectedId)], hasMore: false, selected: true } : receiptPage(payload, callId);
  } catch { return { kind: "unavailable" }; }
  finally { clearTimeout(timer); await reader?.cancel().catch(() => {}); reader?.releaseLock(); }
}
