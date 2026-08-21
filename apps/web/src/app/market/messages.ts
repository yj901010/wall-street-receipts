import type { Locale } from "@/lib/i18n/config";

const en = {
  page: {
    eyebrow: "Known-unavailable DEMO publication",
    title: "A global market board is not published.",
    summary:
      "This route preserves the publication boundary instead of converting historical call context, synthetic map samples, or application literals into current market facts.",
    provenanceLabel: "Market board fixture provenance",
    schema: "Schema",
    fixture: "Fixture",
    policyGenerated: "Policy generated",
    policyCaptured: "Policy captured",
    source: "Source",
    mode: "Mode",
  },
  board: {
    eyebrow: "Closed publication state",
    title: "Market board publication state",
    state: "Not published",
    policyLabel: "Market board publication policy",
    policyNotice: "Publication policy · not market evidence",
    noCatalogTitle: "No quote catalog.",
    noCatalogBody:
      "This fixture records that a canonical global market board has not been published. It is not a delayed, end-of-day, or current quote surface.",
    noContextTitle: "No promoted context.",
    noContextBody:
      "Call-event snapshots and synthetic map samples stay in their owning evidence views; neither is substituted here.",
    noValuesTitle: "No inferred values.",
    noValuesBody:
      "Price, change, market status, freshness, and coverage remain unavailable. Missing values are never replaced with zero.",
    unavailableLabel: "Known-unavailable market board status",
    publicationStatus: "Publication status",
    scope: "Scope",
    reason: "Reason",
    marketAsOf: "Market as of",
    quotePublication: "Quote publication",
    nonePublished: "None published",
    missingDisplay: "Missing display",
    metadataLabel: "Market board policy metadata",
    timestampTitle: "Policy-record timestamps",
    timestampBody:
      "Generated and captured timestamps describe this fixture publication-policy record. They are not a market as-of time, quote timestamp, freshness marker, or trading-session status.",
    sourceType: "Source type",
    license: "License",
    synthetic: "Synthetic policy record",
    sourcePathsLabel: "Market board source paths",
    contractSources: "Contract sources",
    dashboard: "Return to dashboard evidence",
    history: "Open recorded S&P 500 call-event history",
  },
  loading: {
    eyebrow: "Market board publication state",
    title: "Loading the DEMO publication record…",
    body: "No quote, change, session status, freshness, or coverage is filled while it loads.",
  },
  error: {
    eyebrow: "Market board publication state unavailable",
    title: "The DEMO publication record could not be read.",
    body:
      "No partial quote, call-event snapshot, synthetic map value, or application literal is being displayed as a fallback.",
    retry: "Try again",
    dashboard: "Return to dashboard evidence",
  },
} as const;

type LocalizedShape<T> = {
  [Key in keyof T]: T[Key] extends string ? string : LocalizedShape<T[Key]>;
};

export type MarketMessages = LocalizedShape<typeof en>;

const ko = {
  page: {
    eyebrow: "게시되지 않은 것으로 확인된 DEMO 상태",
    title: "글로벌 시장 보드는 게시되지 않았습니다.",
    summary:
      "과거 콜 맥락, 합성 지도 표본 또는 애플리케이션 리터럴을 현재 시장 사실로 바꾸지 않고 게시 경계를 그대로 보존합니다.",
    provenanceLabel: "시장 보드 픽스처 출처",
    schema: "스키마",
    fixture: "픽스처",
    policyGenerated: "정책 생성 시각",
    policyCaptured: "정책 수집 시각",
    source: "소스",
    mode: "모드",
  },
  board: {
    eyebrow: "닫힌 게시 상태",
    title: "시장 보드 게시 상태",
    state: "게시되지 않음",
    policyLabel: "시장 보드 게시 정책",
    policyNotice: "게시 정책 · 시장 증거 아님",
    noCatalogTitle: "호가 카탈로그가 없습니다.",
    noCatalogBody:
      "이 픽스처는 정규 글로벌 시장 보드가 게시되지 않았음을 기록합니다. 지연, 장마감 또는 현재 호가 화면이 아닙니다.",
    noContextTitle: "맥락을 승격하지 않습니다.",
    noContextBody:
      "콜 이벤트 스냅샷과 합성 지도 표본은 각 증거 화면에만 남으며 어느 것도 여기서 대체 데이터로 사용하지 않습니다.",
    noValuesTitle: "값을 추론하지 않습니다.",
    noValuesBody:
      "가격, 변동, 시장 상태, 최신성과 범위는 사용할 수 없습니다. 누락값을 0으로 바꾸지 않습니다.",
    unavailableLabel: "게시되지 않은 시장 보드 상태",
    publicationStatus: "게시 상태",
    scope: "범위",
    reason: "사유",
    marketAsOf: "시장 기준 시각",
    quotePublication: "호가 게시",
    nonePublished: "게시된 항목 없음",
    missingDisplay: "누락 표시",
    metadataLabel: "시장 보드 정책 메타데이터",
    timestampTitle: "정책 레코드 시각",
    timestampBody:
      "생성 및 수집 시각은 이 픽스처 게시 정책 레코드를 설명합니다. 시장 기준 시각, 호가 시각, 최신성 표식 또는 거래 세션 상태가 아닙니다.",
    sourceType: "소스 유형",
    license: "라이선스",
    synthetic: "합성 정책 레코드",
    sourcePathsLabel: "시장 보드 소스 경로",
    contractSources: "계약 소스",
    dashboard: "대시보드 증거로 돌아가기",
    history: "기록된 S&P 500 콜 이벤트 이력 열기",
  },
  loading: {
    eyebrow: "시장 보드 게시 상태",
    title: "DEMO 게시 레코드를 불러오는 중…",
    body: "불러오는 동안 호가, 변동, 세션 상태, 최신성 또는 범위를 채우지 않습니다.",
  },
  error: {
    eyebrow: "시장 보드 게시 상태를 사용할 수 없음",
    title: "DEMO 게시 레코드를 읽을 수 없습니다.",
    body:
      "부분 호가, 콜 이벤트 스냅샷, 합성 지도 값 또는 애플리케이션 리터럴을 대신 표시하지 않습니다.",
    retry: "다시 시도",
    dashboard: "대시보드 증거로 돌아가기",
  },
} as const satisfies MarketMessages;

const MESSAGES = { ko, en } as const satisfies Record<Locale, MarketMessages>;

export function getMarketMessages(locale: Locale): MarketMessages {
  return MESSAGES[locale];
}

export function formatMarketUtc(value: string): string {
  const formatter = new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  });
  return `${formatter.format(new Date(value))} UTC`;
}
