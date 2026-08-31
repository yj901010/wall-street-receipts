import type { Locale } from "@/lib/i18n/config";

const en = {
  page: {
    eyebrow: "Identity before performance",
    title: "Institutions as recorded evidence, not a leaderboard.",
    summary:
      "Inspect committed DEMO identity fields and provenance. This route publishes no institution score, accuracy, performance metric, or rank.",
    provenanceLabel: "Institution identity fixture provenance",
    schema: "Schema",
    fixture: "Fixture",
    generated: "Generated",
    captured: "Captured",
    source: "Source",
    mode: "Mode",
  },
  directory: {
    eyebrow: "Canonical identity records",
    title: "Institution directory",
    countSuffix: "fixture records · coverage not asserted",
    policyLabel: "Institution directory policy",
    productPolicy: "Product policy · not fixture evidence",
    notRankedTitle: "Not ranked.",
    notRankedBody:
      "Rows use canonical-name order, never performance, accuracy, score, call volume, or recommendation order.",
    recordedTitle: "Recorded state.",
    recordedBody:
      "The active field is preserved as captured fixture evidence at its stated effective and capture times; it is not a live operating-status claim.",
    limitedTitle: "Limited DEMO catalog.",
    limitedBody:
      "The fixture record count does not assert market, industry, or provider coverage. Identity inclusion is not an endorsement or investment advice.",
    sourceEvidenceLabel: "Institution source evidence",
    sourceType: "Source type",
    license: "License",
    synthetic: "Synthetic",
    sourcePaths: "Source paths",
    tableLabel: "Institution identity table",
    caption: "Canonical institution identities and their captured evidence",
    columns: {
      institution: "Institution",
      slug: "Slug",
      country: "Country",
      recordedActive: "Recorded active",
      mode: "Mode",
      effective: "Effective",
      captured: "Captured",
      provenance: "Provenance",
      callLedger: "Call ledger",
    },
    filterCallLedger: "Filter call ledger",
    filterCallLedgerFor: "Filter call ledger for",
    emptyTitle: "No institution identities are recorded.",
    emptyBody: "No placeholder identity, coverage claim, score, accuracy, or rank was generated.",
  },
  loading: {
    eyebrow: "Canonical institution identities",
    title: "Loading institution evidence…",
    body: "Reading the committed DEMO master-data fixture and its provenance.",
  },
  error: {
    eyebrow: "Institution directory unavailable",
    title: "The identity fixture could not be read.",
    body:
      "No partial identity, placeholder institution, score, accuracy, or rank is being displayed.",
    retry: "Try again",
    callLedger: "Open the call ledger",
  },
} as const;

type LocalizedShape<T> = {
  [Key in keyof T]: T[Key] extends string ? string : LocalizedShape<T[Key]>;
};

export type InstitutionMessages = LocalizedShape<typeof en>;

const ko = {
  page: {
    eyebrow: "성과보다 먼저 보는 식별 정보",
    title: "기관을 순위표가 아닌 기록된 증거로 봅니다.",
    summary:
      "커밋된 DEMO 식별 필드와 출처를 확인하세요. 이 경로는 기관 점수, 정확도, 성과 지표 또는 순위를 게시하지 않습니다.",
    provenanceLabel: "기관 식별 픽스처 출처",
    schema: "스키마",
    fixture: "픽스처",
    generated: "생성 시각",
    captured: "수집 시각",
    source: "소스",
    mode: "모드",
  },
  directory: {
    eyebrow: "정규 식별 레코드",
    title: "기관 디렉터리",
    countSuffix: "픽스처 레코드 · 범위를 주장하지 않음",
    policyLabel: "기관 디렉터리 정책",
    productPolicy: "제품 정책 · 픽스처 증거 아님",
    notRankedTitle: "순위가 아닙니다.",
    notRankedBody:
      "행은 정규 이름 순서만 사용하며 성과, 정확도, 점수, 콜 수 또는 추천 순서를 사용하지 않습니다.",
    recordedTitle: "기록된 상태입니다.",
    recordedBody:
      "active 필드는 명시된 효력 및 수집 시각의 픽스처 증거 그대로 보존됩니다. 현재 운영 상태를 주장하지 않습니다.",
    limitedTitle: "제한된 DEMO 카탈로그입니다.",
    limitedBody:
      "픽스처 레코드 수는 시장, 산업 또는 제공자 범위를 주장하지 않습니다. 식별 정보의 포함은 보증이나 투자 조언이 아닙니다.",
    sourceEvidenceLabel: "기관 소스 증거",
    sourceType: "소스 유형",
    license: "라이선스",
    synthetic: "합성 여부",
    sourcePaths: "소스 경로",
    tableLabel: "기관 식별 정보 표",
    caption: "정규 기관 식별 정보와 수집된 증거",
    columns: {
      institution: "기관",
      slug: "슬러그",
      country: "국가",
      recordedActive: "기록된 active",
      mode: "모드",
      effective: "효력 시각",
      captured: "수집 시각",
      provenance: "출처 식별자",
      callLedger: "콜 원장",
    },
    filterCallLedger: "콜 원장 필터링",
    filterCallLedgerFor: "다음 기관으로 콜 원장 필터링:",
    emptyTitle: "기록된 기관 식별 정보가 없습니다.",
    emptyBody: "대체 식별 정보, 범위 주장, 점수, 정확도 또는 순위를 생성하지 않았습니다.",
  },
  loading: {
    eyebrow: "정규 기관 식별 정보",
    title: "기관 증거를 불러오는 중…",
    body: "커밋된 DEMO 마스터 데이터 픽스처와 그 출처를 읽고 있습니다.",
  },
  error: {
    eyebrow: "기관 디렉터리를 사용할 수 없음",
    title: "식별 픽스처를 읽을 수 없습니다.",
    body: "부분 식별 정보, 대체 기관, 점수, 정확도 또는 순위를 대신 표시하지 않습니다.",
    retry: "다시 시도",
    callLedger: "콜 원장 열기",
  },
} as const satisfies InstitutionMessages;

const MESSAGES = { ko, en } as const satisfies Record<Locale, InstitutionMessages>;

export function getInstitutionMessages(locale: Locale): InstitutionMessages {
  return MESSAGES[locale];
}
