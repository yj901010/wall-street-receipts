/** Closed ADR-069 wire contract. No repair, inferred terminal state or fixture fallback. */
export const ATTEMPT_ID = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
export const LIMITATIONS = ["NOT_A_HEARTBEAT", "NOT_CPI_FRESHNESS", "NOT_POINT_IN_TIME_HISTORY",
  "RECEIPTS_NOT_REPLAYED", "NO_PROVIDER_REQUEST_PROOF", "NO_PRE_V11_OR_MISSING_START_HISTORY"] as const;
export type Attempt = {
  attemptId: string; trigger: "MANUAL" | "SCHEDULED"; startedAtKst: string; gatePermitted: boolean;
  status: "UNKNOWN" | "SAVED" | "SKIPPED" | "FAILED" | "RATE_LIMITED";
  terminal: null | { completedAtKst: string; captureId: string | null; capturedAtKst: string | null;
    failureCode: "FETCH" | "PARSE" | null; retryNotBeforeKst: string | null };
};
export type AttemptView = { observedAtKst: string; attempts: Attempt[]; hasMore: boolean; selected: boolean };
function requireValue(condition: unknown): asserts condition {
  if (!condition) throw new Error("Invalid CPI attempt evidence");
}
function object(value: unknown, keys: string[]): Record<string, unknown> {
  requireValue(value !== null && typeof value === "object" && !Array.isArray(value));
  const row = value as Record<string, unknown>;
  requireValue(Object.keys(row).length === keys.length && keys.every(key => Object.hasOwn(row, key)));
  return row;
}
/** Preserve API Clock nanoseconds and DB microseconds; reject normalized invalid dates. */
function time(value: unknown): bigint {
  requireValue(typeof value === "string" && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?\+09:00$/.test(value));
  const local = value.slice(0, 19);
  requireValue(!local.startsWith("0000-"));
  const milliseconds = Date.parse(local + "Z");
  requireValue(Number.isFinite(milliseconds) && new Date(milliseconds).toISOString().slice(0, 19) === local);
  const fraction = value.slice(19, -6).replace(".", "").padEnd(9, "0");
  return BigInt(milliseconds) * 1_000_000n + BigInt(fraction);
}
function persistedTime(value: unknown): bigint {
  const result = time(value);
  requireValue(result % 1000n === 0n);
  return result;
}
function attempt(value: unknown, observed: bigint): Attempt {
  const row = object(value, ["attemptId", "trigger", "startedAtKst", "gatePermitted", "status", "terminal"]);
  requireValue(typeof row.attemptId === "string" && ATTEMPT_ID.test(row.attemptId));
  requireValue(row.trigger === "MANUAL" || row.trigger === "SCHEDULED");
  requireValue(typeof row.gatePermitted === "boolean");
  const started = persistedTime(row.startedAtKst);
  requireValue(started <= observed);
  if (row.status === "UNKNOWN") requireValue(row.terminal === null);
  else {
    const end = object(row.terminal, ["completedAtKst", "captureId", "capturedAtKst", "failureCode", "retryNotBeforeKst"]);
    const completed = persistedTime(end.completedAtKst);
    requireValue(completed >= started && completed <= observed && row.gatePermitted === (row.status !== "SKIPPED"));
    if (row.status === "SAVED") {
      requireValue(typeof end.captureId === "string" && ATTEMPT_ID.test(end.captureId));
      const captured = persistedTime(end.capturedAtKst);
      requireValue(captured >= started && captured <= completed && end.failureCode === null && end.retryNotBeforeKst === null);
    } else {
      requireValue(end.captureId === null && end.capturedAtKst === null);
      if (row.status === "FAILED") requireValue(["FETCH", "PARSE"].includes(end.failureCode as string) && end.retryNotBeforeKst === null);
      else if (row.status === "RATE_LIMITED") requireValue(end.failureCode === null && persistedTime(end.retryNotBeforeKst) >= started);
      else requireValue(row.status === "SKIPPED" && end.failureCode === null && end.retryNotBeforeKst === null);
    }
  }
  return row as Attempt;
}
export function adaptAttempts(value: unknown, selection = ""): AttemptView {
  requireValue(selection === "" || ATTEMPT_ID.test(selection));
  const root = object(value, selection ? ["metadata", "attempt"] : ["metadata", "limit", "hasMore", "order", "attempts"]);
  const metadata = object(root.metadata, ["schemaVersion", "dataMode", "evidenceMode", "timezone", "observedAtKst", "limitations"]);
  requireValue(metadata.schemaVersion === "1.0.0" && metadata.dataMode === "UNVERIFIED"
    && metadata.evidenceMode === "PERSISTED_ATTEMPT_RECORDS" && metadata.timezone === "Asia/Seoul");
  requireValue(Array.isArray(metadata.limitations) && JSON.stringify(metadata.limitations) === JSON.stringify(LIMITATIONS));
  const observed = time(metadata.observedAtKst);
  let rows: Attempt[];
  if (selection) {
    rows = [attempt(root.attempt, observed)];
    requireValue(rows[0].attemptId === selection);
  } else {
    requireValue(root.limit === 20 && typeof root.hasMore === "boolean" && root.order === "STARTED_AT_DESC_ATTEMPT_ID_DESC");
    requireValue(Array.isArray(root.attempts) && root.attempts.length <= 20 && (!root.hasMore || root.attempts.length === 20));
    rows = root.attempts.map(row => attempt(row, observed));
    requireValue(new Set(rows.map(row => row.attemptId)).size === rows.length);
    rows.forEach((row, i) => {
      if (!i) return;
      const previous = rows[i - 1];
      requireValue(time(previous.startedAtKst) > time(row.startedAtKst)
        || time(previous.startedAtKst) === time(row.startedAtKst) && previous.attemptId > row.attemptId);
    });
  }
  return { observedAtKst: metadata.observedAtKst as string, attempts: rows, hasMore: root.hasMore === true, selected: !!selection };
}

export const QUERY_ERRORS: Record<number, string> = {
  400: "조회 입력을 확인해 주세요.", 401: "운영자 토큰을 확인해 주세요.", 403: "이 조회는 허용되지 않습니다.",
  404: "기록을 찾을 수 없거나 운영자 API가 비활성화되어 있습니다.",
  429: "조회 요청이 너무 잦습니다. 잠시 후 수동으로 다시 조회해 주세요.",
};
export async function readAttemptJson(response: Response): Promise<unknown> {
  if (response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") {
    await response.body?.cancel(); throw new Error("Invalid CPI response");
  }
  const reader = response.body?.getReader();
  if (!reader) throw new Error("Missing CPI response");
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > 65_536) throw new Error("Oversized CPI response");
      chunks.push(value);
    }
  } finally { await reader.cancel(); reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
}
