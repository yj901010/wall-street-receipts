import type { Locale } from "@/lib/i18n/config";

const en = {
  route: {
    modeNavLabel: "Market map modes",
    metric: "Metric",
    priceChange: "Price change",
    analystConsensus: "Analyst consensus",
    universeNavLabel: "Market map universes",
    universe: "Universe",
    eyebrow: "Read-only fixture map",
    title: (universe: string) => `${universe} map evidence.`,
    priceChangeSummary:
      "This default surface displays a nested PRICE_CHANGE DEMO fixture. Area uses a synthetic market-cap proxy; color uses stored synthetic percent values. Neither is live or official market data.",
    analystConsensusSummary:
      "This alternate surface preserves the standalone ANALYST_CONSENSUS DEMO fixture. It does not claim full index composition, live membership, official weights, or metrics derived from the canonical call ledger.",
    provenanceLabel: (universe: string) => `${universe} map provenance`,
    asOf: "As of",
    captured: "Captured",
    generated: "Generated",
    source: "Source",
    dataMode: "Data mode",
  },
  analystMap: {
    eyebrow: "Synthetic map evidence",
    title: (universe: string) => `${universe} analyst-consensus sample`,
    sample: (count: number, dataMode: string) => `${count}-cell ${dataMode} sample`,
    coverageStrong: "Limited DEMO sample — not a complete index map.",
    coverageLead: (kind: string, complete: boolean) =>
      `Coverage is ${kind}; completeUniverse is ${String(complete)}.`,
    coverageWide: (weightBasis: string) =>
      `On wide layouts, tile area uses ${weightBasis} fixture weights, not official index or market-cap weights. Small screens stack the same cells for readability while preserving each recorded weight.`,
    coverageEmpty: (weightBasis: string) =>
      `The declared weight basis is ${weightBasis}, but no tile geometry or weight is rendered or inferred for this known-empty fixture.`,
    definitionLabel: (universe: string) => `${universe} map definition`,
    mapMode: "Map mode",
    storedMetric: "Stored metric",
    metricUnit: "Metric unit",
    weightBasis: "Weight basis",
    legendLabel: (metric: string) => `${metric} legend`,
    storedDemoScore: "Stored DEMO score",
    unavailableLegend: "NA = unavailable, not zero or negative",
    cellsLabel: (universe: string) => `${universe} limited DEMO sample cells`,
    cellLabel: (ticker: string) => `${ticker} map evidence`,
    storedDemoMetric: "Stored DEMO metric",
    fixtureWeight: "Fixture weight",
    fixtureCallCount: "Fixture call count",
    timestamp: "Timestamp",
    mode: "Mode",
    provenance: "Provenance",
    emptyTitle: (universe: string) => `No ${universe} map cells are available.`,
    emptyBody:
      "No membership, weight, metric, or call count was inferred, and no cells from another universe were substituted.",
    readonly:
      "Cells are read-only. Stock detail evidence is not published in this phase, so no drilldown link is shown.",
  },
  treemap: {
    eyebrow: "Nested synthetic map evidence",
    title: (universe: string) => `${universe} price-change treemap`,
    sample: (count: number, dataMode: string) => `${count}-cell ${dataMode} sample`,
    coverageStrong: "Limited DEMO sample — not a complete index treemap.",
    coverageGrouping: (sectors: number, industries: number) =>
      `This committed fixture demonstrates ${sectors} outer sector and ${industries} nested industries. The engine supports multiple sectors, but this sample does not assert broader sector coverage, official membership, or composition.`,
    coverageArea: (areaField: string, areaUnit: string) =>
      `Rectangle area uses only each stored ${areaField} in ${areaUnit} units. It is a synthetic proxy, never an official or current market-cap value.`,
    definitionLabel: (universe: string) => `${universe} treemap definition`,
    mapMode: "Map mode",
    storedMetric: "Stored metric",
    metricUnit: "Metric unit",
    grouping: "Grouping",
    weightBasis: "Weight basis",
    coverage: "Coverage",
    legendLabel: (minimum: number, maximum: number) =>
      `Price-change percent color legend; palette saturates at ${minimum}% and +${maximum}%`,
    storedPercent: "Stored price-change percent",
    saturation: "Color saturates at the declared endpoints; displayed values are never clamped.",
    unavailable: "NA is unavailable, not zero or negative.",
    scrollLabel: (universe: string) => `${universe} treemap scroll region`,
    cellsLabel: (universe: string) => `${universe} nested DEMO treemap cells`,
    cellLabel: (ticker: string, metric: string) => `${ticker} treemap evidence: ${metric}`,
    proxy: (value: number) => `Proxy ${value}`,
    tooltip: {
      ticker: "Ticker",
      sector: "Sector",
      industry: "Industry",
      storedChange: "Stored change",
      syntheticProxy: "Synthetic proxy",
      relativeUnits: (value: number) => `${value} relative units`,
      timestamp: "Timestamp",
      dataMode: "Data mode",
      provenance: "Provenance",
    },
    indexSummary: (count: number) => `Accessible evidence index · ${count} cells`,
    indexDescription:
      "This non-geometric index preserves every stored field when a proportional tile is too small for visible content. It does not change or impose a minimum tile area.",
    indexScrollLabel: (universe: string) =>
      `${universe} accessible treemap evidence scroll region`,
    indexTableLabel: (universe: string) => `${universe} accessible treemap evidence index`,
    columns: {
      assetId: "Asset ID",
      ticker: "Ticker",
      sector: "Sector",
      industry: "Industry",
      storedChange: "Stored change",
      syntheticProxy: "Synthetic proxy",
      timestamp: "Timestamp",
      dataMode: "Data mode",
      provenance: "Provenance",
    },
    relativeUnits: (value: number) => `${value} relative units`,
    emptyTitle: (universe: string) => `No ${universe} treemap cells are available.`,
    emptyBody:
      "No sector, industry, ticker, proxy area, or price-change value was inferred, and no cells from another universe were substituted.",
    readonly:
      "Canonical ticker cells are read-only and keyboard focusable. The accessible evidence index preserves inspection when proportional geometry becomes subpixel. Stock detail evidence is not published in this phase, so no drilldown link is shown.",
  },
  loading: {
    eyebrow: "Market map evidence",
    title: "Loading the DEMO map evidence…",
    body:
      "Reading the selected mode, coverage, timestamps, and provenance without filling missing cells.",
  },
  error: {
    eyebrow: "Market map unavailable",
    title: "The map evidence could not be read.",
    body: "No cell, geometry, weight, metric, or universe membership was inferred.",
    retry: "Try again",
    dashboard: "Return to dashboard",
  },
  notFound: {
    eyebrow: "Unsupported map request",
    title: "This market map is not published.",
    body: "No data from another universe or mode was substituted.",
    sp500: "Open S&P 500 sample",
    nasdaq100: "Open Nasdaq 100 sample",
  },
} as const;

type LocalizedShape<T> = T extends (...args: infer Args) => unknown
  ? (...args: Args) => string
  : T extends string
    ? string
    : { [Key in keyof T]: LocalizedShape<T[Key]> };

export type MarketMapMessages = LocalizedShape<typeof en>;

const ko = {
  route: {
    modeNavLabel: "시장 지도 모드",
    metric: "지표",
    priceChange: "가격 변동",
    analystConsensus: "애널리스트 컨센서스",
    universeNavLabel: "시장 지도 유니버스",
    universe: "유니버스",
    eyebrow: "읽기 전용 픽스처 지도",
    title: (universe: string) => `${universe} 지도 증거.`,
    priceChangeSummary:
      "기본 화면은 중첩된 PRICE_CHANGE DEMO 픽스처를 표시합니다. 면적에는 합성 시가총액 프록시를, 색상에는 저장된 합성 퍼센트 값을 사용합니다. 어느 값도 실시간 또는 공식 시장 데이터가 아닙니다.",
    analystConsensusSummary:
      "대체 화면은 독립된 ANALYST_CONSENSUS DEMO 픽스처를 그대로 보존합니다. 전체 지수 구성, 실시간 편입, 공식 가중치 또는 정식 콜 원장에서 파생한 지표를 주장하지 않습니다.",
    provenanceLabel: (universe: string) => `${universe} 지도 출처`,
    asOf: "기준 시각",
    captured: "수집 시각",
    generated: "생성 시각",
    source: "소스",
    dataMode: "데이터 모드",
  },
  analystMap: {
    eyebrow: "합성 지도 증거",
    title: (universe: string) => `${universe} 애널리스트 컨센서스 표본`,
    sample: (count: number, dataMode: string) => `${dataMode} 셀 표본 ${count}개`,
    coverageStrong: "제한된 DEMO 표본 — 전체 지수 지도가 아닙니다.",
    coverageLead: (kind: string, complete: boolean) =>
      `커버리지는 ${kind}이며 completeUniverse는 ${String(complete)}입니다.`,
    coverageWide: (weightBasis: string) =>
      `넓은 화면에서 타일 면적은 공식 지수 또는 시가총액 가중치가 아닌 ${weightBasis} 픽스처 가중치를 사용합니다. 작은 화면에서는 기록된 가중치를 보존한 채 같은 셀을 세로로 쌓아 읽기 쉽게 표시합니다.`,
    coverageEmpty: (weightBasis: string) =>
      `선언된 가중치 기준은 ${weightBasis}이지만, 비어 있음이 확인된 이 픽스처에는 타일 도형이나 가중치를 표시하거나 추론하지 않습니다.`,
    definitionLabel: (universe: string) => `${universe} 지도 정의`,
    mapMode: "지도 모드",
    storedMetric: "저장된 지표",
    metricUnit: "지표 단위",
    weightBasis: "가중치 기준",
    legendLabel: (metric: string) => `${metric} 범례`,
    storedDemoScore: "저장된 DEMO 점수",
    unavailableLegend: "NA = 사용 불가, 0이나 음수가 아님",
    cellsLabel: (universe: string) => `${universe} 제한 DEMO 표본 셀`,
    cellLabel: (ticker: string) => `${ticker} 지도 증거`,
    storedDemoMetric: "저장된 DEMO 지표",
    fixtureWeight: "픽스처 가중치",
    fixtureCallCount: "픽스처 콜 수",
    timestamp: "시각",
    mode: "모드",
    provenance: "출처 식별자",
    emptyTitle: (universe: string) => `사용 가능한 ${universe} 지도 셀이 없습니다.`,
    emptyBody:
      "편입, 가중치, 지표 또는 콜 수를 추론하지 않았으며 다른 유니버스의 셀로 대체하지 않았습니다.",
    readonly:
      "셀은 읽기 전용입니다. 이 단계에는 종목 상세 증거가 게시되지 않아 상세 이동 링크를 표시하지 않습니다.",
  },
  treemap: {
    eyebrow: "중첩 합성 지도 증거",
    title: (universe: string) => `${universe} 가격 변동 트리맵`,
    sample: (count: number, dataMode: string) => `${dataMode} 셀 표본 ${count}개`,
    coverageStrong: "제한된 DEMO 표본 — 전체 지수 트리맵이 아닙니다.",
    coverageGrouping: (sectors: number, industries: number) =>
      `커밋된 이 픽스처는 외부 섹터 ${sectors}개와 중첩 산업 ${industries}개를 시연합니다. 엔진은 여러 섹터를 지원하지만 이 표본은 더 넓은 섹터 커버리지, 공식 편입 또는 구성을 주장하지 않습니다.`,
    coverageArea: (areaField: string, areaUnit: string) =>
      `사각형 면적에는 저장된 ${areaField} 값만 ${areaUnit} 단위로 사용합니다. 이는 합성 프록시이며 공식 또는 현재 시가총액 값이 아닙니다.`,
    definitionLabel: (universe: string) => `${universe} 트리맵 정의`,
    mapMode: "지도 모드",
    storedMetric: "저장된 지표",
    metricUnit: "지표 단위",
    grouping: "그룹 기준",
    weightBasis: "가중치 기준",
    coverage: "커버리지",
    legendLabel: (minimum: number, maximum: number) =>
      `가격 변동 퍼센트 색상 범례; 팔레트는 ${minimum}% 및 +${maximum}%에서 포화`,
    storedPercent: "저장된 가격 변동 퍼센트",
    saturation: "색상은 선언된 끝값에서 포화되지만 표시값은 제한하지 않습니다.",
    unavailable: "NA는 사용 불가이며 0이나 음수가 아닙니다.",
    scrollLabel: (universe: string) => `${universe} 트리맵 스크롤 영역`,
    cellsLabel: (universe: string) => `${universe} 중첩 DEMO 트리맵 셀`,
    cellLabel: (ticker: string, metric: string) => `${ticker} 트리맵 증거: ${metric}`,
    proxy: (value: number) => `프록시 ${value}`,
    tooltip: {
      ticker: "티커",
      sector: "섹터",
      industry: "산업",
      storedChange: "저장된 변동",
      syntheticProxy: "합성 프록시",
      relativeUnits: (value: number) => `${value} 상대 단위`,
      timestamp: "시각",
      dataMode: "데이터 모드",
      provenance: "출처 식별자",
    },
    indexSummary: (count: number) => `접근 가능한 증거 인덱스 · 셀 ${count}개`,
    indexDescription:
      "이 비기하학 인덱스는 비례 타일이 화면에 내용을 표시하기에 너무 작을 때도 저장된 모든 필드를 보존합니다. 타일 면적을 바꾸거나 최솟값을 강제하지 않습니다.",
    indexScrollLabel: (universe: string) => `${universe} 접근 가능한 트리맵 증거 스크롤 영역`,
    indexTableLabel: (universe: string) => `${universe} 접근 가능한 트리맵 증거 인덱스`,
    columns: {
      assetId: "자산 ID",
      ticker: "티커",
      sector: "섹터",
      industry: "산업",
      storedChange: "저장된 변동",
      syntheticProxy: "합성 프록시",
      timestamp: "시각",
      dataMode: "데이터 모드",
      provenance: "출처 식별자",
    },
    relativeUnits: (value: number) => `${value} 상대 단위`,
    emptyTitle: (universe: string) => `사용 가능한 ${universe} 트리맵 셀이 없습니다.`,
    emptyBody:
      "섹터, 산업, 티커, 프록시 면적 또는 가격 변동 값을 추론하지 않았으며 다른 유니버스의 셀로 대체하지 않았습니다.",
    readonly:
      "정식 티커 셀은 읽기 전용이며 키보드로 포커스할 수 있습니다. 접근 가능한 증거 인덱스는 비례 도형이 서브픽셀 크기가 되어도 검사를 지원합니다. 이 단계에는 종목 상세 증거가 게시되지 않아 상세 이동 링크를 표시하지 않습니다.",
  },
  loading: {
    eyebrow: "시장 지도 증거",
    title: "DEMO 지도 증거를 불러오는 중…",
    body: "누락 셀을 채우지 않고 선택한 모드, 커버리지, 시각과 출처를 읽고 있습니다.",
  },
  error: {
    eyebrow: "시장 지도를 사용할 수 없음",
    title: "지도 증거를 읽을 수 없습니다.",
    body: "셀, 도형, 가중치, 지표 또는 유니버스 편입을 추론하지 않습니다.",
    retry: "다시 시도",
    dashboard: "대시보드로 돌아가기",
  },
  notFound: {
    eyebrow: "지원하지 않는 지도 요청",
    title: "이 시장 지도는 게시되지 않았습니다.",
    body: "다른 유니버스나 모드의 데이터로 대체하지 않았습니다.",
    sp500: "S&P 500 표본 열기",
    nasdaq100: "Nasdaq 100 표본 열기",
  },
} as const satisfies MarketMapMessages;

const MESSAGES = { ko, en } as const satisfies Record<Locale, MarketMapMessages>;

export function getMarketMapMessages(locale: Locale): MarketMapMessages {
  return MESSAGES[locale];
}
