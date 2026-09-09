"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { adaptAttempts, ATTEMPT_ID, QUERY_ERRORS, readAttemptJson, type AttemptView } from "@/lib/operator-cpi";
import { KstTimestamp } from "@/components/kst-timestamp";
import styles from "./view.module.css";

const labels = { UNKNOWN: "상태 미확인", SAVED: "저장 기록", FAILED: "실패 기록", SKIPPED: "건너뜀", RATE_LIMITED: "요청 제한" };
const kst = (value: string | null) => value === null ? "기록 없음" : <KstTimestamp value={value} />;
export function OperatorCpiView() {
  const form = useRef<HTMLFormElement>(null);
  const pending = useRef<AbortController | null>(null);
  const [view, setView] = useState<AttemptView | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const reset = () => {
    pending.current?.abort(); pending.current = null;
    form.current?.reset(); setView(null); setError(""); setBusy(false);
  };
  useEffect(() => {
    // A returned bfcache page must not retain a credential or evidence view.
    const clear = () => { pending.current?.abort(); pending.current = null; form.current?.reset(); setView(null); setError(""); setBusy(false); };
    window.addEventListener("pagehide", clear);
    return () => { pending.current?.abort(); window.removeEventListener("pagehide", clear); };
  }, []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    setView(null); setError("");
    const data = new FormData(event.currentTarget);
    const token = String(data.get("token") ?? "");
    const selection = String(data.get("selection") ?? "");
    // Clear the password immediately; every manual query requires a fresh entry.
    const input = form.current?.elements.namedItem("token") as HTMLInputElement | null;
    if (input) input.value = "";
    if (!/^[A-Za-z0-9+/]{43}=$/.test(token) || selection && !ATTEMPT_ID.test(selection)) {
      setError("32바이트 Base64 운영자 토큰과 소문자 UUID 형식을 확인해 주세요."); return;
    }
    const controller = new AbortController(); pending.current = controller; setBusy(true);
    try {
      const response = await fetch(`/operator/cpi/query${selection ? "/" + selection : ""}`, {
        method: "GET", credentials: "omit", cache: "no-store", redirect: "error",
        signal: AbortSignal.any([controller.signal, AbortSignal.timeout(7_000)]),
        headers: { Authorization: `Bearer ${token}`, "X-WSR-Operator": "cpi-read-v1", Accept: "application/json" },
      });
      if (response.status !== 200) { await response.body?.cancel(); throw new Error(QUERY_ERRORS[response.status] ?? "수집 이력을 조회할 수 없습니다. 대체 데이터는 사용하지 않았습니다."); }
      const result = adaptAttempts(await readAttemptJson(response), selection);
      if (pending.current === controller) setView(result);
    } catch (failure) {
      if (pending.current === controller) setError(failure instanceof Error && Object.values(QUERY_ERRORS).includes(failure.message)
        ? failure.message : "수집 이력을 조회할 수 없습니다. 대체 데이터는 사용하지 않았습니다.");
    } finally {
      if (pending.current === controller) { pending.current = null; setBusy(false); }
    }
  }
  return <main className={styles.page} lang="ko">
    <header className={styles.header}><strong>WALL STREET RECEIPTS</strong><span>LOCAL OPERATOR · 읽기 전용 CPI 조회</span></header>
    <h1>CPI 수집 이력</h1>
    <p>공개 사이트와 분리된 로컬 운영자 화면입니다. 자동 조회·수집·재시도는 하지 않습니다.</p>
    <section aria-labelledby="query-title" className={styles.query}>
      <h2 id="query-title">수동 조회</h2>
      <form ref={form} onSubmit={submit} autoComplete="off">
        <label>운영자 토큰<input name="token" type="password" autoComplete="off" spellCheck={false} maxLength={44} required aria-describedby="token-help" /></label>
        <label>수집 시도 UUID (선택)<input name="selection" autoComplete="off" spellCheck={false} maxLength={36} placeholder="비우면 최근 20건" /></label>
        <div className={styles.actions}><button type="submit" disabled={busy}>{busy ? "조회 중…" : "이력 조회"}</button><button type="button" onClick={reset}>입력·결과 지우기</button></div>
      </form>
      <p id="token-help">토큰은 조회 시 전송 후 입력란에서 지웁니다. URL·쿠키·브라우저 저장소에 저장하지 않습니다. 조회할 때마다 다시 입력해 주세요.</p>
    </section>
    <aside className={styles.notice}>
      <strong>UNVERIFIED · 출처: 저장된 CPI 수집 시도 기록</strong>
      <p>DB 기록만 표시하며 실제 공급자 데이터인지 검증하지 않았습니다. 테스트 환경의 기록은 DEMO입니다.</p>
      <p>이 화면은 heartbeat(실행 생존 신호), CPI 최신성, 과거 시점 복원 또는 공급자 요청 도달의 증거가 아닙니다. 시작 기록이 없거나 기록 기능 도입 전의 수집은 포함되지 않을 수 있습니다.</p>
      <p>‘상태 미확인’은 완료 기록이 없다는 뜻이며 실행 중·실패로 추정하지 않습니다. 저장 영수증의 원문은 재검증하지 않습니다.</p>
    </aside>
    <div role="status" aria-live="polite">{busy ? "수집 이력 조회 중입니다. 이전 조회 결과는 지웠습니다." : !view && !error ? "아직 조회하지 않았습니다." : ""}</div>
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {view && <section aria-labelledby="results-title">
      <h2 id="results-title">{view.selected ? "선택한 수집 시도" : "최근 수집 시도"}</h2>
      <p>API 관측 시각: {kst(view.observedAtKst)} · DB 커밋 시각이나 역사적 조회 기준 시각이 아닙니다.</p>
      {!view.attempts.length ? <p>저장된 수집 시도 기록이 없습니다. 수집이 없었다는 증명은 아닙니다.</p> : <div className={styles.scroll} tabIndex={0} role="region" aria-label="수집 이력 표, 좁은 화면에서 가로 스크롤">
        <table><caption>{view.selected ? "UUID 일치 기록 1건" : `시작 시각 내림차순 · 동률 시 UUID 내림차순 · ${view.attempts.length}건 표시`}</caption>
          <thead><tr><th>시작 / UUID</th><th>실행 경로 / 게이트</th><th>결과</th><th>완료 / 부가 기록</th></tr></thead>
          <tbody>{view.attempts.map(row => <tr key={row.attemptId}>
            <td>{kst(row.startedAtKst)}<code>{row.attemptId}</code></td>
            <td>{row.trigger === "MANUAL" ? "수동" : "예약"}<small>게이트 {row.gatePermitted ? "허용" : "거부"} · 요청 도달 증거 아님</small></td>
            <td>{labels[row.status]}<small>{row.status}</small></td>
            <td>{row.terminal ? <>{kst(row.terminal.completedAtKst)}
              {row.terminal.captureId && <><small>영수증 참조 · 원문 재검증 안 함</small><code>{row.terminal.captureId}</code><small>수신 기록: {kst(row.terminal.capturedAtKst)}</small></>}
              {row.terminal.failureCode && <small>실패 단계: {row.terminal.failureCode === "FETCH" ? "수신" : "파싱"}</small>}
              {row.terminal.retryNotBeforeKst && <small>재요청 가능 하한: {kst(row.terminal.retryNotBeforeKst)} · 다음 실행 보장 아님</small>}
            </> : "완료 기록 없음"}</td>
          </tr>)}</tbody>
        </table>
      </div>}
      {view.hasMore && <p>더 오래된 기록이 있습니다. 전체 건수는 조회하지 않았습니다. 알고 있는 UUID를 입력해 개별 조회할 수 있습니다.</p>}
    </section>}
  </main>;
}
