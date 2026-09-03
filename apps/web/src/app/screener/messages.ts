import type { Locale } from "@/lib/i18n/config";

const en = {
  page: {
    eyebrow: "Application-owned release boundary",
    title: "Historical equity screening is deferred.",
    summary:
      "This query-free route publishes only the product phase decision. It does not expose executable filters, results, or a synthetic preview while the canonical P8 feature catalog is unavailable.",
  },
  shell: {
    eyebrow: "Known-deferred application state",
    title: "Historical screening publication state",
    state: "Deferred to P8",
    policyLabel: "Screener product availability policy",
    policyNotice: "Product availability policy · not fixture evidence",
    noCatalogTitle: "No feature catalog.",
    noCatalogBody:
      "Historical equity screening remains deferred until P8 supplies historical bars, a point-in-time feature catalog, and a materialized screening read model.",
    notEmptyTitle: "Not an empty query.",
    notEmptyBody:
      "This state is distinct from a completed screen with no matches, a loading state, and a route error. No screening query is executed here.",
    noSubstituteTitle: "No substitute output.",
    noSubstituteBody:
      "The application does not promote call evidence, methodology definitions, fixture literals, or missing values into filters, results, ordering, charts, or numeric metrics. Performance outcomes and rankings remain P3 work; licensed observed-provider integration remains P5 work. Neither is substituted here.",
    stateLabel: "Deferred screener state",
    labels: {
      dataMode: "Data mode",
      scope: "Scope",
      status: "Status",
      reason: "Reason",
      missingDisplay: "Missing display",
    },
    boundaryNote:
      "records an unpublished capability state; it never means zero matches, a zero numeric value, completeness, or a successful empty query. This policy has no schema version, fixture version, timestamp, source, provenance, or disclaimer because it is an application phase boundary rather than observed or fixture evidence.",
    adjacentLabel: "Adjacent evidence routes",
    adjacentNotice: "Separate evidence surfaces · not screener output",
    calls: "Open recorded call evidence",
    methodology: "Open methodology definitions",
  },
  loading: {
    eyebrow: "Screener product phase policy",
    title: "Loading the DEMO application policy…",
    body: "No filter, result, ordering, chart, count, or numeric metric is filled while it loads.",
  },
  error: {
    eyebrow: "Screener policy unavailable",
    title: "The application phase policy could not be read.",
    body: "No fixture, source, filter, result, chart, or numeric value is displayed as a fallback.",
    retry: "Try again",
    calls: "Open recorded call evidence",
    methodology: "Open methodology definitions",
  },
  notFound: {
    eyebrow: "Unsupported screener request",
    title: "This screener request is not published.",
    body:
      "The shell accepts no query parameters. No query was executed and no filter, result, or alternate screening state was substituted.",
    calls: "Open recorded call evidence",
    methodology: "Open methodology definitions",
  },
} as const;

type LocalizedShape<T> = {
  [Key in keyof T]: T[Key] extends string ? string : LocalizedShape<T[Key]>;
};

export type ScreenerMessages = LocalizedShape<typeof en>;

const ko = {
  page: {
    eyebrow: "애플리케이션 소유 출시 경계",
    title: "과거 주식 스크리닝은 연기됐습니다.",
    summary:
      "쿼리가 없는 이 경로는 제품 단계 결정만 게시합니다. 정규 P8 기능 카탈로그를 사용할 수 없는 동안 실행 가능한 필터, 결과 또는 합성 미리보기를 노출하지 않습니다.",
  },
  shell: {
    eyebrow: "연기가 확정된 애플리케이션 상태",
    title: "과거 스크리닝 게시 상태",
    state: "P8로 연기",
    policyLabel: "스크리너 제품 제공 정책",
    policyNotice: "제품 제공 정책 · 픽스처 증거 아님",
    noCatalogTitle: "기능 카탈로그가 없습니다.",
    noCatalogBody:
      "P8에서 과거 가격 바, 시점 기준 기능 카탈로그와 구체화된 스크리닝 읽기 모델을 제공할 때까지 과거 주식 스크리닝은 연기됩니다.",
    notEmptyTitle: "빈 쿼리가 아닙니다.",
    notEmptyBody:
      "이 상태는 일치 항목이 없는 완료된 스크리닝 결과, 로딩 상태 및 경로 오류와 구분됩니다. 여기서는 스크리닝 쿼리를 실행하지 않습니다.",
    noSubstituteTitle: "대체 출력을 만들지 않습니다.",
    noSubstituteBody:
      "애플리케이션은 콜 증거, 방법론 정의, 픽스처 리터럴 또는 누락값을 필터, 결과, 정렬, 차트 또는 수치 지표로 승격하지 않습니다. 성과 결과와 순위는 P3 작업이며, 라이선스가 있는 관측 제공자 통합은 P5 작업입니다. 어느 것도 여기서 대체하지 않습니다.",
    stateLabel: "연기된 스크리너 상태",
    labels: {
      dataMode: "데이터 모드",
      scope: "범위",
      status: "상태",
      reason: "사유",
      missingDisplay: "누락 표시",
    },
    boundaryNote:
      "는 게시되지 않은 기능 상태를 기록합니다. 일치 항목 0개, 수치 0, 완전성 또는 성공한 빈 쿼리를 뜻하지 않습니다. 이 정책은 관측 또는 픽스처 증거가 아닌 애플리케이션 단계 경계이므로 스키마 버전, 픽스처 버전, 타임스탬프, 소스, 출처 또는 면책 문구가 없습니다.",
    adjacentLabel: "인접 증거 경로",
    adjacentNotice: "별도 증거 화면 · 스크리너 출력 아님",
    calls: "기록된 콜 증거 열기",
    methodology: "방법론 정의 열기",
  },
  loading: {
    eyebrow: "스크리너 제품 단계 정책",
    title: "DEMO 애플리케이션 정책을 불러오는 중…",
    body: "불러오는 동안 필터, 결과, 정렬, 차트, 개수 또는 수치 지표를 채우지 않습니다.",
  },
  error: {
    eyebrow: "스크리너 정책을 사용할 수 없음",
    title: "애플리케이션 단계 정책을 읽을 수 없습니다.",
    body: "픽스처, 소스, 필터, 결과, 차트 또는 수치 값을 대신 표시하지 않습니다.",
    retry: "다시 시도",
    calls: "기록된 콜 증거 열기",
    methodology: "방법론 정의 열기",
  },
  notFound: {
    eyebrow: "지원하지 않는 스크리너 요청",
    title: "이 스크리너 요청은 게시되지 않았습니다.",
    body:
      "이 화면은 쿼리 매개변수를 받지 않습니다. 쿼리를 실행하지 않았으며 필터, 결과 또는 다른 스크리닝 상태를 대신 표시하지 않습니다.",
    calls: "기록된 콜 증거 열기",
    methodology: "방법론 정의 열기",
  },
} as const satisfies ScreenerMessages;

const MESSAGES = { ko, en } as const satisfies Record<Locale, ScreenerMessages>;

export function getScreenerMessages(locale: Locale): ScreenerMessages {
  return MESSAGES[locale];
}
