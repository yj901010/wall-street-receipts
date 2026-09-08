import { adaptCpi, type CpiSnapshot } from "./cpi";

export type CpiState = { kind: "disabled" | "empty" } | { kind: "ready"; snapshot: CpiSnapshot };
export async function loadCpi(env: NodeJS.ProcessEnv = process.env, fetcher: typeof fetch = fetch): Promise<CpiState> {
  if (typeof window !== "undefined") throw new Error("CPI transport is server-only");
  const mode = env.CPI_PROVIDER ?? "disabled";
  if (mode === "disabled") return { kind: "disabled" };
  if (mode !== "api") throw new Error("CPI_PROVIDER must be disabled or api");
  try {
    const base = new URL(env.API_BASE_URL ?? "");
    if (!["http:", "https:"].includes(base.protocol) || base.username || base.password || base.search || base.hash) throw new Error();
    base.pathname = `${base.pathname.replace(/\/+$/, "")}/`;
    const response = await fetcher(new URL("v1/macro/cpi", base), {
      method: "GET", cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5_000), headers: { Accept: "application/json" },
    });
    if (response.status === 404) { await response.body?.cancel(); return { kind: "empty" }; }
    if (response.status !== 200 || response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") {
      await response.body?.cancel(); throw new Error();
    }
    const reader = response.body?.getReader();
    if (!reader) throw new Error();
    const chunks: Uint8Array[] = [];
    let length = 0;
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        length += value.byteLength;
        if (length > 262_144) throw new Error();
        chunks.push(value);
      }
    } finally { await reader.cancel(); reader.releaseLock(); }
    const bytes = new Uint8Array(length);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    return { kind: "ready", snapshot: adaptCpi(JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes))) };
  } catch { throw new Error("Stored CPI data could not be loaded. No substitute data was used."); }
}
