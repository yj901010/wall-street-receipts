import { LIMITATIONS } from "../lib/operator-cpi";
/** Synthetic DEMO transport only. Never imported by production modules. */
export function operatorFixture() {
  return {
    metadata: { schemaVersion: "1.0.0", dataMode: "UNVERIFIED", evidenceMode: "PERSISTED_ATTEMPT_RECORDS",
      timezone: "Asia/Seoul", observedAtKst: "2026-09-09T00:01:00+09:00", limitations: [...LIMITATIONS] },
    limit: 20, hasMore: false, order: "STARTED_AT_DESC_ATTEMPT_ID_DESC",
    attempts: [{ attemptId: "00000000-0000-0000-0000-000000000001", trigger: "MANUAL", startedAtKst: "2026-09-08T23:59:59.123456+09:00",
      gatePermitted: true, status: "UNKNOWN", terminal: null as null | Record<string, unknown> }],
  };
}
