import type { Locale } from "@/lib/i18n/config";

const en = {
  page: {
    eyebrow: "Point-in-time analyst intelligence",
    title: "Market evidence, without inferred gaps.",
    summary:
      "Each populated section retains its own timestamp and provenance. This dashboard does not synthesize one global as-of time or source across independent fixtures.",
  },
  availability: {
    notPublished: "Not published",
    p3Deferred: "P3 deferred",
    status: "Status",
    display: "Display",
    label: (title: string) => `${title} availability`,
  },
  marketBoard: {
    eyebrow: "Global market strip",
    title: "Market board",
    body:
      "A canonical latest-market-board read model is not published. Call-event snapshots are immutable historical context and are not promoted to current quotes.",
  },
  calls: {
    eyebrow: "Committed DEMO event ledger",
    title: "Latest calls within this fixture",
    count: (count: number) => `${count} DEMO events`,
    provenanceLabel: "Dashboard call section provenance",
    asOf: "As of",
    source: "Source",
    dataMode: "Data mode",
    ordering: "Ordering",
    orderingValue: "Original event time, descending",
    note:
      "“Latest” means latest within the committed DEMO fixture. It does not mean current or live, and no revision-folded ranking or performance result is produced.",
    emptyTitle: "No call events are recorded.",
    emptyBody: "No placeholder event was created for the dashboard.",
    scrollLabel: "Scrollable dashboard latest calls table",
    caption: "Latest analyst calls within the committed DEMO fixture",
    columns: {
      eventTime: "Event time",
      institutionAnalyst: "Institution / analyst",
      asset: "Asset",
      direction: "Direction",
      targetChange: "Target change",
      evidence: "Evidence",
    },
  },
  maps: {
    eyebrow: "Independent fixture evidence",
    title: "PRICE_CHANGE map previews",
    fixtureCount: (count: number) => `${count} DEMO map fixtures`,
    overlap: (count: number) =>
      `The two previews reuse ${count} stored synthetic ticker cells. Cross-universe overlap is demonstration evidence only and does not assert official membership in either index.`,
  },
  mapPreview: {
    title: (universe: string) => `${universe} map preview`,
    open: (universe: string) => `Open ${universe} map`,
    provenanceLabel: (universe: string) => `${universe} dashboard map preview provenance`,
    asOf: "As of",
    generated: "Generated",
    captured: "Captured",
    provenance: "Provenance",
    coverage: "Coverage",
    coverageValue: (kind: string, count: number) => `${kind} · ${count} cells`,
    completeUniverse: "Complete universe",
    storedGrouping: "Stored grouping",
    storedGroupingValue: (sectors: number, industries: number) =>
      `${sectors} outer ${sectors === 1 ? "sector" : "sectors"} · ${industries} industries`,
    weightBasis: "Weight basis",
    areaUnit: "Area unit",
    emptyTitle: (universe: string) => `No ${universe} preview cells are recorded.`,
    emptyBody: "No cell from another universe was substituted.",
    cellsLabel: (universe: string) => `${universe} dashboard PRICE_CHANGE preview cells`,
    storedChange: "Stored change",
    syntheticProxy: "Synthetic proxy",
    syntheticProxyValue: (value: number, unit: string) => `${value} ${unit} units`,
    timestamp: "Timestamp",
  },
  calendar: {
    eyebrow: "Global event calendar",
    title: "Scheduled events",
    body:
      "No global event-calendar read model is published. Call-linked scheduled context stays attached to its historical call and is not presented as a current calendar.",
  },
  ranking: {
    eyebrow: "Deterministic outcomes",
    title: "Ranking preview",
    body:
      "No accuracy, return, alpha, hit-rate, score, rank, sample count, or ordering is calculated. Deterministic ranking work remains deferred to P3.",
    methodology: "Review methodology evidence",
  },
  loading: {
    eyebrow: "Dashboard evidence",
    title: "Loading independently sourced DEMO sections…",
    body:
      "No global timestamp, source, quote, event, or ranking is being filled while evidence loads.",
  },
  error: {
    eyebrow: "Dashboard evidence unavailable",
    title: "The fixture sections could not be composed.",
    body: "No partial quote, calendar event, ranking, or fallback universe is being displayed.",
    retry: "Try again",
    calls: "Open the call ledger",
  },
} as const;

type LocalizedShape<T> = T extends (...args: infer Args) => unknown
  ? (...args: Args) => string
  : T extends string
    ? string
    : { [Key in keyof T]: LocalizedShape<T[Key]> };

export type DashboardMessages = LocalizedShape<typeof en>;

const ko = {
  page: {
    eyebrow: "시점 기준 애널리스트 인텔리전스",
    title: "추론으로 빈칸을 채우지 않은 시장 증거.",
    summary:
      "값이 있는 각 섹션은 고유한 시각과 출처를 유지합니다. 이 대시보드는 서로 독립적인 픽스처에 하나의 공통 기준 시각이나 소스를 합성하지 않습니다.",
  },
  availability: {
    notPublished: "게시되지 않음",
    p3Deferred: "P3로 연기",
    status: "상태",
    display: "표시값",
    label: (title: string) => `${title} 제공 상태`,
  },
  marketBoard: {
    eyebrow: "글로벌 시장 스트립",
    title: "시장 보드",
    body:
      "정식 최신 시장 보드 읽기 모델은 게시되지 않았습니다. 콜 이벤트 스냅샷은 변경할 수 없는 과거 맥락이며 현재 시세로 승격하지 않습니다.",
  },
  calls: {
    eyebrow: "커밋된 DEMO 이벤트 원장",
    title: "이 픽스처의 최신 콜",
    count: (count: number) => `DEMO 이벤트 ${count}개`,
    provenanceLabel: "대시보드 콜 섹션 출처",
    asOf: "기준 시각",
    source: "소스",
    dataMode: "데이터 모드",
    ordering: "정렬",
    orderingValue: "원본 이벤트 시각 내림차순",
    note:
      "‘최신’은 커밋된 DEMO 픽스처 안에서 가장 최근이라는 뜻입니다. 현재 또는 실시간을 뜻하지 않으며, 수정 이력을 접은 순위나 성과 결과를 만들지 않습니다.",
    emptyTitle: "기록된 콜 이벤트가 없습니다.",
    emptyBody: "대시보드용 대체 이벤트를 만들지 않았습니다.",
    scrollLabel: "스크롤 가능한 대시보드 최신 콜 표",
    caption: "커밋된 DEMO 픽스처의 최신 애널리스트 콜",
    columns: {
      eventTime: "이벤트 시각",
      institutionAnalyst: "기관 / 애널리스트",
      asset: "자산",
      direction: "방향",
      targetChange: "목표가 변경",
      evidence: "증거",
    },
  },
  maps: {
    eyebrow: "독립 픽스처 증거",
    title: "PRICE_CHANGE 지도 미리보기",
    fixtureCount: (count: number) => `DEMO 지도 픽스처 ${count}개`,
    overlap: (count: number) =>
      `두 미리보기는 저장된 합성 티커 셀 ${count}개를 함께 사용합니다. 유니버스 간 중복은 시연용 증거일 뿐이며 어느 지수의 공식 편입도 주장하지 않습니다.`,
  },
  mapPreview: {
    title: (universe: string) => `${universe} 지도 미리보기`,
    open: (universe: string) => `${universe} 지도 열기`,
    provenanceLabel: (universe: string) => `${universe} 대시보드 지도 미리보기 출처`,
    asOf: "기준 시각",
    generated: "생성 시각",
    captured: "수집 시각",
    provenance: "출처 식별자",
    coverage: "커버리지",
    coverageValue: (kind: string, count: number) => `${kind} · 셀 ${count}개`,
    completeUniverse: "전체 유니버스 여부",
    storedGrouping: "저장된 그룹",
    storedGroupingValue: (sectors: number, industries: number) =>
      `외부 섹터 ${sectors}개 · 산업 ${industries}개`,
    weightBasis: "가중치 기준",
    areaUnit: "면적 단위",
    emptyTitle: (universe: string) => `기록된 ${universe} 미리보기 셀이 없습니다.`,
    emptyBody: "다른 유니버스의 셀로 대체하지 않았습니다.",
    cellsLabel: (universe: string) => `${universe} 대시보드 PRICE_CHANGE 미리보기 셀`,
    storedChange: "저장된 변동",
    syntheticProxy: "합성 프록시",
    syntheticProxyValue: (value: number, unit: string) => `${value} ${unit}`,
    timestamp: "시각",
  },
  calendar: {
    eyebrow: "글로벌 이벤트 캘린더",
    title: "예정 이벤트",
    body:
      "글로벌 이벤트 캘린더 읽기 모델은 게시되지 않았습니다. 콜에 연결된 예정 맥락은 과거 콜에 붙은 상태로 유지되며 현재 캘린더로 표시하지 않습니다.",
  },
  ranking: {
    eyebrow: "결정론적 결과",
    title: "순위 미리보기",
    body:
      "정확도, 수익률, 알파, 적중률, 점수, 순위, 표본 수 또는 정렬을 계산하지 않습니다. 결정론적 순위 작업은 P3로 연기된 상태입니다.",
    methodology: "방법론 증거 검토",
  },
  loading: {
    eyebrow: "대시보드 증거",
    title: "독립 소스의 DEMO 섹션을 불러오는 중…",
    body:
      "증거를 불러오는 동안 공통 시각, 소스, 시세, 이벤트 또는 순위를 임의로 채우지 않습니다.",
  },
  error: {
    eyebrow: "대시보드 증거를 사용할 수 없음",
    title: "픽스처 섹션을 구성할 수 없습니다.",
    body: "부분 시세, 캘린더 이벤트, 순위 또는 대체 유니버스를 표시하지 않습니다.",
    retry: "다시 시도",
    calls: "콜 원장 열기",
  },
} as const satisfies DashboardMessages;

const MESSAGES = { ko, en } as const satisfies Record<Locale, DashboardMessages>;

export function getDashboardMessages(locale: Locale): DashboardMessages {
  return MESSAGES[locale];
}
