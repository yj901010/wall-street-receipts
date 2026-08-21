import type { Locale } from "@/lib/i18n/config";

const en = {
  page: {
    eyebrow: "Identity before performance",
    title: "Analysts as recorded evidence, not a leaderboard.",
    summary:
      "Inspect committed DEMO identity fields and provenance. This route publishes no affiliation, call data, score, accuracy, performance metric, or rank.",
    provenanceLabel: "Analyst identity fixture provenance",
    schema: "Schema",
    fixture: "Fixture",
    generated: "Generated",
    captured: "Captured",
    source: "Source",
    mode: "Mode",
  },
  directory: {
    eyebrow: "Canonical identity records",
    title: "Analyst directory",
    countSuffix: "identity fixture · coverage not asserted",
    policyLabel: "Analyst directory policy",
    productPolicy: "Product policy · not fixture evidence",
    notRankedTitle: "Not ranked.",
    notRankedBody:
      "Rows use canonical-name order, never performance, accuracy, score, call volume, confidence, or recommendation order.",
    recordedTitle: "Recorded state.",
    recordedBody:
      "The active field is preserved as captured fixture evidence at its stated effective and capture times; it is not a live activity claim.",
    syntheticTitle: "Synthetic DEMO identity only.",
    syntheticBody:
      "Names and recorded status do not establish verified coverage, employer or affiliation, endorsement, performance, or investment advice.",
    sourceEvidenceLabel: "Analyst source evidence",
    sourceType: "Source type",
    license: "License",
    synthetic: "Synthetic",
    sourcePaths: "Source paths",
    tableLabel: "Analyst identity table",
    caption: "Canonical analyst identities and their captured evidence",
    columns: {
      analyst: "Analyst",
      recordedActive: "Recorded active",
      mode: "Mode",
      effective: "Effective",
      captured: "Captured",
      provenance: "Provenance",
      callLedger: "Call ledger",
    },
    filterCallLedger: "Filter call ledger",
    filterCallLedgerFor: "Filter call ledger for",
    emptyTitle: "No analyst identities are recorded.",
    emptyBody:
      "No placeholder identity, affiliation, call data, metric, score, or rank was generated.",
  },
  loading: {
    eyebrow: "Canonical analyst identities",
    title: "Loading analyst evidence…",
    body: "Reading the committed DEMO master-data fixture and its provenance.",
  },
  error: {
    eyebrow: "Analyst directory unavailable",
    title: "The identity fixture could not be read.",
    body: "No partial identity, affiliation, call data, metric, score, or rank is being displayed.",
    retry: "Try again",
    callLedger: "Open the call ledger",
  },
} as const;

type LocalizedShape<T> = {
  [Key in keyof T]: T[Key] extends string ? string : LocalizedShape<T[Key]>;
};

export type AnalystMessages = LocalizedShape<typeof en>;

const ko = {
  page: {
    eyebrow: "성과보다 먼저 보는 식별 정보",
    title: "애널리스트를 순위표가 아닌 기록된 증거로 봅니다.",
    summary:
      "커밋된 DEMO 식별 필드와 출처를 확인하세요. 이 경로는 소속, 콜 데이터, 점수, 정확도, 성과 지표 또는 순위를 게시하지 않습니다.",
    provenanceLabel: "애널리스트 식별 픽스처 출처",
    schema: "스키마",
    fixture: "픽스처",
    generated: "생성 시각",
    captured: "수집 시각",
    source: "소스",
    mode: "모드",
  },
  directory: {
    eyebrow: "정규 식별 레코드",
    title: "애널리스트 디렉터리",
    countSuffix: "식별 픽스처 · 범위를 주장하지 않음",
    policyLabel: "애널리스트 디렉터리 정책",
    productPolicy: "제품 정책 · 픽스처 증거 아님",
    notRankedTitle: "순위가 아닙니다.",
    notRankedBody:
      "행은 정규 이름 순서만 사용하며 성과, 정확도, 점수, 콜 수, 신뢰도 또는 추천 순서를 사용하지 않습니다.",
    recordedTitle: "기록된 상태입니다.",
    recordedBody:
      "active 필드는 명시된 효력 및 수집 시각의 픽스처 증거 그대로 보존됩니다. 현재 활동 상태를 주장하지 않습니다.",
    syntheticTitle: "합성 DEMO 식별 정보만 제공합니다.",
    syntheticBody:
      "이름과 기록된 상태는 검증된 범위, 고용주나 소속, 보증, 성과 또는 투자 조언을 뜻하지 않습니다.",
    sourceEvidenceLabel: "애널리스트 소스 증거",
    sourceType: "소스 유형",
    license: "라이선스",
    synthetic: "합성 여부",
    sourcePaths: "소스 경로",
    tableLabel: "애널리스트 식별 정보 표",
    caption: "정규 애널리스트 식별 정보와 수집된 증거",
    columns: {
      analyst: "애널리스트",
      recordedActive: "기록된 active",
      mode: "모드",
      effective: "효력 시각",
      captured: "수집 시각",
      provenance: "출처 식별자",
      callLedger: "콜 원장",
    },
    filterCallLedger: "콜 원장 필터링",
    filterCallLedgerFor: "다음 애널리스트로 콜 원장 필터링:",
    emptyTitle: "기록된 애널리스트 식별 정보가 없습니다.",
    emptyBody:
      "대체 식별 정보, 소속, 콜 데이터, 지표, 점수 또는 순위를 생성하지 않았습니다.",
  },
  loading: {
    eyebrow: "정규 애널리스트 식별 정보",
    title: "애널리스트 증거를 불러오는 중…",
    body: "커밋된 DEMO 마스터 데이터 픽스처와 그 출처를 읽고 있습니다.",
  },
  error: {
    eyebrow: "애널리스트 디렉터리를 사용할 수 없음",
    title: "식별 픽스처를 읽을 수 없습니다.",
    body: "부분 식별 정보, 소속, 콜 데이터, 지표, 점수 또는 순위를 대신 표시하지 않습니다.",
    retry: "다시 시도",
    callLedger: "콜 원장 열기",
  },
} as const satisfies AnalystMessages;

const MESSAGES = { ko, en } as const satisfies Record<Locale, AnalystMessages>;

export function getAnalystMessages(locale: Locale): AnalystMessages {
  return MESSAGES[locale];
}

export function formatAnalystUtc(value: string): string {
  const formatter = new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  });
  return `${formatter.format(new Date(value))} UTC`;
}
