import Link from "next/link";

export default function LoadingScoringReceipts() {
  return <div className="page-shell" role="status" aria-live="polite">
    <p>DEMO 평가 기록을 불러오는 중… / Loading DEMO scoring receipts…</p>
    <noscript><p>이 스트리밍 감사 화면을 표시하려면 JavaScript를 켜야 합니다. 계산은 API에서만 수행합니다.<br />
      Enable JavaScript to display this streamed audit page. Metrics are calculated only by the API.</p>
      <Link href="/calls" prefetch={false}>콜 기록으로 / Back to calls</Link></noscript>
  </div>;
}
