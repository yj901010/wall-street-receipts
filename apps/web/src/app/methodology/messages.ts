import type { Locale } from "@/lib/i18n/config";

const en = {
  page: {
    eyebrow: "Deterministic model governance",
    title: "Methodology definitions, before performance claims.",
    summary:
      "Inspect immutable definition identities and their evidence. MODEL_ONLY records do not imply that a return, hit rate, alpha, or ranking has been calculated.",
    provenanceLabel: "Methodology dataset provenance",
    asOf: "As of",
    source: "Source",
    mode: "Mode",
  },
  registry: {
    eyebrow: "Definition identity",
    title: "Versioned methodology registry",
    definitionCount: "definitions",
    guide:
      "Schema identifies the record shape. Methodology version and definition hash preserve the immutable definition identity; Effective is its stated start instant, while Captured records when the source acquired that evidence. The fixture does not contain the formula body.",
    tableLabel: "Methodology registry table",
    caption: "Versioned scoring methodology definitions",
    columns: {
      methodology: "Methodology",
      version: "Version",
      status: "Status",
      mode: "Mode",
      effective: "Effective",
      captured: "Captured",
      definitionHash: "Definition hash",
      provenance: "Provenance",
    },
    schemaPrefix: "schema",
    emptyTitle: "No methodology definitions are recorded.",
    emptyBody: "No substitute version, hash, or calculation result was generated.",
    note:
      "MODEL_ONLY identifies a versioned definition contract. Deterministic return, alpha, hit-rate, and ranking calculations remain deferred to P3; this page calculates no outcome values.",
  },
  loading: {
    eyebrow: "Definition registry",
    title: "Loading methodology evidence…",
    body: "Reading version identity, effective time, hash, and provenance.",
  },
  error: {
    eyebrow: "Methodology registry unavailable",
    title: "The definition evidence could not be read.",
    body: "No partial definition or calculated value is being displayed.",
    retry: "Try again",
    dashboard: "Return to dashboard",
  },
} as const;

type LocalizedShape<T> = {
  [Key in keyof T]: T[Key] extends string ? string : LocalizedShape<T[Key]>;
};

export type MethodologyMessages = LocalizedShape<typeof en>;

const ko = {
  page: {
    eyebrow: "결정론적 모델 거버넌스",
    title: "성과 주장에 앞서 방법론 정의를 확인합니다.",
    summary:
      "변경할 수 없는 정의 식별자와 그 증거를 확인하세요. MODEL_ONLY 레코드는 수익률, 적중률, 알파 또는 순위가 계산됐음을 뜻하지 않습니다.",
    provenanceLabel: "방법론 데이터셋 출처",
    asOf: "기준 시각",
    source: "소스",
    mode: "모드",
  },
  registry: {
    eyebrow: "정의 식별자",
    title: "버전별 방법론 레지스트리",
    definitionCount: "개 정의",
    guide:
      "스키마는 레코드 형태를 식별합니다. 방법론 버전과 정의 해시는 변경할 수 없는 정의 식별자를 보존합니다. 효력 시각은 명시된 시작 시각이고 수집 시각은 소스가 그 증거를 확보한 시각입니다. 이 픽스처에는 수식 본문이 없습니다.",
    tableLabel: "방법론 레지스트리 표",
    caption: "버전별 점수 산정 방법론 정의",
    columns: {
      methodology: "방법론",
      version: "버전",
      status: "상태",
      mode: "모드",
      effective: "효력 시각",
      captured: "수집 시각",
      definitionHash: "정의 해시",
      provenance: "출처 식별자",
    },
    schemaPrefix: "스키마",
    emptyTitle: "기록된 방법론 정의가 없습니다.",
    emptyBody: "대체 버전, 해시 또는 계산 결과를 생성하지 않았습니다.",
    note:
      "MODEL_ONLY는 버전이 부여된 정의 계약을 식별합니다. 결정론적 수익률, 알파, 적중률 및 순위 계산은 P3까지 연기됐으며 이 페이지는 결과값을 계산하지 않습니다.",
  },
  loading: {
    eyebrow: "정의 레지스트리",
    title: "방법론 증거를 불러오는 중…",
    body: "버전 식별자, 효력 시각, 해시와 출처를 읽고 있습니다.",
  },
  error: {
    eyebrow: "방법론 레지스트리를 사용할 수 없음",
    title: "정의 증거를 읽을 수 없습니다.",
    body: "부분 정의나 계산값을 대신 표시하지 않습니다.",
    retry: "다시 시도",
    dashboard: "대시보드로 돌아가기",
  },
} as const satisfies MethodologyMessages;

const MESSAGES = { ko, en } as const satisfies Record<Locale, MethodologyMessages>;

export function getMethodologyMessages(locale: Locale): MethodologyMessages {
  return MESSAGES[locale];
}
