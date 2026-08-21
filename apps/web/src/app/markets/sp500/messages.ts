import type { Locale } from "@/lib/i18n/config";

export type Sp500HistoryMessages = {
  page: {
    eyebrow: string;
    title: string;
    summary: string;
    provenanceLabel: string;
    catalogAsOf: string;
    source: string;
    asset: string;
    mode: string;
  };
  history: {
    eyebrow: string;
    title: string;
    countSummary: (shown: number, total: number) => string;
    policyLabel: string;
    policyEyebrow: string;
    originalEventsTitle: string;
    originalEventsDescription: string;
    storedFactsTitle: string;
    storedFactsDescription: string;
    incompleteFixtureTitle: string;
    incompleteFixtureDescription: string;
    queryEvidenceLabel: string;
    canonicalAsset: string;
    assetId: string;
    tickerType: string;
    fixedQuery: string;
    ordering: string;
    orderingValue: string;
    fixtureQueryPage: string;
    emptyTitle: string;
    emptyDescription: string;
    tableRegionLabel: string;
    tableCaption: string;
    eventRecord: string;
    institutionAnalyst: string;
    directionRating: string;
    storedTargets: string;
    targetDate: string;
    recordedStatus: string;
    sourceEvidence: string;
    processingCaptureEvidence: string;
    currency: string;
    verified: string;
    captured: string;
    openFilteredLedger: string;
    returnMarket: string;
  };
  states: {
    loadingEyebrow: string;
    loadingTitle: string;
    loadingDescription: string;
    errorEyebrow: string;
    errorTitle: string;
    errorDescription: string;
    tryAgain: string;
    returnMarket: string;
  };
};

const ko = {
  page: {
    eyebrow: "확정된 DEMO 콜 이벤트 원장",
    title: "기록된 S&P 500 전망 콜 이벤트",
    summary: "원본 애널리스트 콜 기록의 시점 기준 일부입니다. 지수 가격 이력, 현재 전망, 컨센서스, 시장 추세 또는 성과 시계열이 아닙니다.",
    provenanceLabel: "S&P 500 콜 이력 출처 정보",
    catalogAsOf: "콜 카탈로그 기준 시각",
    source: "출처",
    asset: "자산",
    mode: "모드",
  },
  history: {
    eyebrow: "원본 확정 콜 기록",
    title: "S&P 500 콜 이벤트 이력",
    countSummary: (shown, total) =>
      `${shown}개 행 표시 · 일치하는 DEMO 이벤트 ${total}건 · 불완전한 픽스처 범위`,
    policyLabel: "S&P 500 콜 이력 정책",
    policyEyebrow: "표시 정책 · 픽스처 증거 아님",
    originalEventsTitle: "원본 이벤트.",
    originalEventsDescription: "행은 기록된 이벤트 시각 순으로 정렬된 확정 애널리스트 콜 이벤트입니다. 정정이나 개정 내용을 현재 유효 상태로 합치지 않습니다.",
    storedFactsTitle: "저장된 사실만 표시.",
    storedFactsDescription: "방향, 투자의견, 목표가, 상태는 각 이벤트에 저장된 값입니다. 현재 추천, 가격, 컨센서스 또는 성과가 아닙니다.",
    incompleteFixtureTitle: "불완전한 DEMO 픽스처.",
    incompleteFixtureDescription: "행 수는 이 정확한 픽스처 쿼리만 설명합니다. S&P 500 범위, 신뢰도, 완전성 또는 시장 추세를 주장하지 않습니다.",
    queryEvidenceLabel: "S&P 500 이력 쿼리 증거",
    canonicalAsset: "정규 자산",
    assetId: "자산 ID",
    tickerType: "티커 / 유형",
    fixedQuery: "고정 쿼리",
    ordering: "정렬",
    orderingValue: "이벤트 시각 내림차순 · 동일 시각은 콜 ID 오름차순",
    fixtureQueryPage: "픽스처 쿼리 페이지",
    emptyTitle: "이 DEMO 쿼리에 기록된 S&P 500 콜 이벤트가 없습니다.",
    emptyDescription: "대체 전망, 목표가, 상태, 출처, 시장 가격 또는 성과를 만들지 않았습니다.",
    tableRegionLabel: "S&P 500 콜 이벤트 이력 표",
    tableCaption: "원본 확정 S&P 500 DEMO 애널리스트 콜 이벤트",
    eventRecord: "이벤트 기록",
    institutionAnalyst: "기관 / 애널리스트",
    directionRating: "기록된 방향 / 투자의견",
    storedTargets: "저장된 목표가",
    targetDate: "목표 기준일",
    recordedStatus: "기록된 상태",
    sourceEvidence: "출처 증거",
    processingCaptureEvidence: "처리 / 수집 증거",
    currency: "통화",
    verified: "검증 여부",
    captured: "수집",
    openFilteredLedger: "필터링된 콜 원장 열기",
    returnMarket: "시장 게시 상태로 돌아가기",
  },
  states: {
    loadingEyebrow: "기록된 S&P 500 콜 이벤트",
    loadingTitle: "확정된 DEMO 콜 일부를 불러오는 중…",
    loadingDescription: "불러오는 동안 시장 가격, 차트, 목표가, 상태, 성과 또는 대체 행을 채우지 않습니다.",
    errorEyebrow: "S&P 500 콜 이벤트 이력 이용 불가",
    errorTitle: "확정된 DEMO 콜 일부를 읽을 수 없습니다.",
    errorDescription: "일부 콜, 시장 스냅샷, 차트, 성과, 컨센서스 또는 애플리케이션 상수를 대체 값으로 표시하지 않습니다.",
    tryAgain: "다시 시도",
    returnMarket: "시장 게시 상태로 돌아가기",
  },
} satisfies Sp500HistoryMessages;

const en = {
  page: {
    eyebrow: "Committed DEMO call-event ledger",
    title: "Recorded S&P 500 forecast-call events.",
    summary: "This is a point-in-time subset of original analyst-call records, not index-price history, a current forecast, consensus, market trend, or performance series.",
    provenanceLabel: "S&P 500 call-history provenance",
    catalogAsOf: "Call catalog as of",
    source: "Source",
    asset: "Asset",
    mode: "Mode",
  },
  history: {
    eyebrow: "Original committed call records",
    title: "S&P 500 call-event history",
    countSummary: (shown, total) =>
      `${shown} ${shown === 1 ? "row" : "rows"} shown · ${total} matching DEMO ${total === 1 ? "event" : "events"} · incomplete fixture coverage`,
    policyLabel: "S&P 500 call-history policy",
    policyEyebrow: "Presentation policy · not fixture evidence",
    originalEventsTitle: "Original events.",
    originalEventsDescription: "Rows are committed analyst-call events ordered by their recorded event time. No correction or revision is folded into a current effective view.",
    storedFactsTitle: "Stored facts only.",
    storedFactsDescription: "Direction, rating, targets, and status are values stored on each event. They are not current recommendations, prices, consensus, or performance.",
    incompleteFixtureTitle: "Incomplete DEMO fixture.",
    incompleteFixtureDescription: "Row totals describe this exact fixture query; they do not assert S&P 500 coverage, confidence, completeness, or market trend.",
    queryEvidenceLabel: "S&P 500 history query evidence",
    canonicalAsset: "Canonical asset",
    assetId: "Asset ID",
    tickerType: "Ticker / type",
    fixedQuery: "Fixed query",
    ordering: "Ordering",
    orderingValue: "Event time descending · call ID ascending tie break",
    fixtureQueryPage: "Fixture query page",
    emptyTitle: "No S&P 500 call events are recorded in this DEMO query.",
    emptyDescription: "No placeholder forecast, target, status, source, market price, or outcome was created.",
    tableRegionLabel: "S&P 500 call-event history table",
    tableCaption: "Original committed S&P 500 DEMO analyst-call events",
    eventRecord: "Event record",
    institutionAnalyst: "Institution / analyst",
    directionRating: "Recorded direction / rating",
    storedTargets: "Stored targets",
    targetDate: "Target date",
    recordedStatus: "Recorded status",
    sourceEvidence: "Source evidence",
    processingCaptureEvidence: "Processing / capture evidence",
    currency: "Currency",
    verified: "Verified",
    captured: "Captured",
    openFilteredLedger: "Open filtered call ledger",
    returnMarket: "Return to market publication status",
  },
  states: {
    loadingEyebrow: "Recorded S&P 500 call events",
    loadingTitle: "Loading the committed DEMO call subset…",
    loadingDescription: "No market price, chart, target, status, outcome, or placeholder row is filled while it loads.",
    errorEyebrow: "S&P 500 call-event history unavailable",
    errorTitle: "The committed DEMO call subset could not be read.",
    errorDescription: "No partial call, market snapshot, chart, outcome, consensus, or application literal is being displayed as a fallback.",
    tryAgain: "Try again",
    returnMarket: "Return to market publication status",
  },
} satisfies Sp500HistoryMessages;

export const SP500_HISTORY_MESSAGES = { ko, en } as const satisfies Record<Locale, Sp500HistoryMessages>;

export function getSp500HistoryMessages(locale: Locale): Sp500HistoryMessages {
  return SP500_HISTORY_MESSAGES[locale];
}
