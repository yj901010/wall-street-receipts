import type { Locale } from "@/lib/i18n/config";

export type CallsMessages = {
  list: {
    eyebrow: string;
    title: string;
    summary: string;
    provenanceLabel: string;
    asOf: string;
    source: string;
    mode: string;
    filterLabel: string;
    ticker: string;
    tickerPlaceholder: string;
    asset: string;
    allAssets: string;
    institution: string;
    allInstitutions: string;
    analyst: string;
    allAnalysts: string;
    direction: string;
    allDirections: string;
    status: string;
    allStatuses: string;
    from: string;
    throughDate: string;
    throughDateNote: string;
    dataMode: string;
    allModes: string;
    sortBy: string;
    eventTime: string;
    processingTime: string;
    capturedAt: string;
    order: string;
    descending: string;
    ascending: string;
    rows: string;
    applyFilters: string;
    clear: string;
    results: string;
    eventCount: (count: number) => string;
    pageStatus: (page: number, totalPages: number, field: string, order: string) => string;
    emptyEyebrow: string;
    emptyTitle: string;
    emptyDescription: string;
    clearAll: string;
    resultsRegionLabel: string;
    tableCaption: string;
    institutionAnalyst: string;
    targetChange: string;
    callsPagesLabel: string;
    previous: string;
    next: string;
  };
  detail: {
    back: string;
    canonicalCall: (callId: string) => string;
    callTitle: (institution: string, ticker: string) => string;
    analystUnavailable: string;
    callStatusLabel: string;
    recordProvenanceLabel: string;
    asOf: string;
    dataMode: string;
    provenance: string;
    providerEvent: string;
    eventRecordEyebrow: string;
    callFacts: string;
    eventTime: string;
    processingTime: string;
    processingDelay: string;
    delayMinutes: (minutes: number) => string;
    originalRating: string;
    previousTarget: string;
    newTarget: string;
    targetChange: string;
    targetDate: string;
    evidenceChain: string;
    sourceProvenance: string;
    verified: string;
    unverifiedDemo: string;
    documentId: string;
    referenceId: string;
    publisher: string;
    sourceType: string;
    title: string;
    provider: string;
    externalId: string;
    published: string;
    documentCaptured: string;
    referenceCaptured: string;
    documentDataMode: string;
    referenceDataMode: string;
    documentProvenance: string;
    referenceProvenance: string;
    license: string;
    contentHash: string;
    extractedFragment: string;
    pageTimeOffset: string;
    confidence: string;
    openCanonicalSource: string;
    canonicalSourceUnavailable: string;
    pageLocation: (page: number) => string;
    fromLocation: (milliseconds: number) => string;
    untilLocation: (milliseconds: number) => string;
    pointInTimeContext: string;
    marketSnapshot: string;
    immutablePointInTime: string;
    snapshotUnavailable: string;
    snapshotId: string;
    snapshotEventTime: string;
    snapshotProcessingTime: string;
    captured: string;
    assetId: string;
    mutationPolicy: string;
    appendOnly: string;
    snapshotValuesLabel: string;
    assetPrice: string;
    treasury2y: string;
    treasury10y: string;
    realYield: string;
    gold: string;
    volatility: string;
    distance52WeekHigh: string;
    distanceAth: string;
    noInventedMarketValues: string;
    deterministicScoring: string;
    outcome: string;
    methodologyInactive: string;
    directionalWin: string;
    targetHit: string;
    alpha: string;
    methodologyVersion: string;
    outcomeNote: string;
  };
  context: {
    macroSubject: string;
    scheduledSubject: string;
    availabilityEvidence: (subject: string) => string;
    asOfCallEvent: string;
    dataMode: string;
    source: string;
    provenance: string;
    knownEmptyContext: string;
    contextUnavailable: string;
    missingEvidence: (subject: string) => string;
    macroProvenanceLabel: string;
    snapshotId: string;
    asOf: string;
    processingTime: string;
    captured: string;
    sources: string;
    perObservationSources: string;
    mutationPolicy: string;
    appendOnly: string;
    macroTableRegionLabel: string;
    macroTableCaption: string;
    series: string;
    value: string;
    unit: string;
    observationDate: string;
    released: string;
    processing: string;
    vintageStart: string;
    vintageEnd: string;
    macroNote: string;
    scheduledProvenanceLabel: string;
    contextId: string;
    scheduleValuesLabel: string;
    earnings: string;
    nextCpi: string;
    nextFomc: string;
    nextNfp: string;
    optionsExpiration: string;
    scheduledNote: string;
    pointInTimeEvidence: string;
    macroContext: string;
    observedScheduleEvidence: string;
    scheduledEventContext: string;
    immutable: string;
    knownEmpty: string;
    unavailable: string;
  };
  states: {
    listLoadingEyebrow: string;
    listLoadingTitle: string;
    listLoadingDescription: string;
    errorEyebrow: string;
    errorTitle: string;
    errorDescription: string;
    tryAgain: string;
    returnDashboard: string;
    detailLoadingEyebrow: string;
    detailLoadingTitle: string;
    detailLoadingDescription: string;
    notFoundEyebrow: string;
    notFoundTitle: string;
    notFoundDescription: string;
    returnCalls: string;
  };
};

const ko = {
  list: {
    eyebrow: "정규 이벤트 원장",
    title: "애널리스트 콜",
    summary: "정규 식별자와 출처 증거를 함께 보존한 시점 기준 콜 이벤트를 검색합니다.",
    provenanceLabel: "콜 데이터셋 출처 정보",
    asOf: "기준 시각",
    source: "출처",
    mode: "모드",
    filterLabel: "애널리스트 콜 필터",
    ticker: "티커",
    tickerPlaceholder: "예: NVDA",
    asset: "자산",
    allAssets: "모든 자산",
    institution: "기관",
    allInstitutions: "모든 기관",
    analyst: "애널리스트",
    allAnalysts: "모든 애널리스트",
    direction: "방향",
    allDirections: "모든 방향",
    status: "상태",
    allStatuses: "모든 상태",
    from: "시작일",
    throughDate: "종료일(UTC)",
    throughDateNote: "선택한 다음 날 00:00 UTC를 제외 상한으로 적용합니다.",
    dataMode: "데이터 모드",
    allModes: "모든 모드",
    sortBy: "정렬 기준",
    eventTime: "이벤트 시각",
    processingTime: "처리 시각",
    capturedAt: "수집 시각",
    order: "정렬 순서",
    descending: "내림차순",
    ascending: "오름차순",
    rows: "행 수",
    applyFilters: "필터 적용",
    clear: "초기화",
    results: "결과",
    eventCount: (count) => `이벤트 ${count}건`,
    pageStatus: (page, totalPages, field, order) =>
      `${page}/${totalPages}페이지 · ${field},${order}`,
    emptyEyebrow: "일치하는 이벤트 없음",
    emptyTitle: "이 필터와 일치하는 항목이 없습니다.",
    emptyDescription: "필터를 하나 이상 해제하세요. 누락된 기록을 합성 값으로 대체하지 않습니다.",
    clearAll: "모든 필터 초기화",
    resultsRegionLabel: "스크롤 가능한 애널리스트 콜 결과",
    tableCaption: "필터링된 애널리스트 콜 이벤트",
    institutionAnalyst: "기관 / 애널리스트",
    targetChange: "목표가 변경",
    callsPagesLabel: "콜 목록 페이지",
    previous: "이전",
    next: "다음",
  },
  detail: {
    back: "← 애널리스트 콜로 돌아가기",
    canonicalCall: (callId) => `정규 애널리스트 콜 · ${callId}`,
    callTitle: (institution, ticker) => `${institution} · ${ticker} 콜`,
    analystUnavailable: "애널리스트 정보 없음",
    callStatusLabel: "콜 상태",
    recordProvenanceLabel: "콜 기록 출처 정보",
    asOf: "기준 시각",
    dataMode: "데이터 모드",
    provenance: "출처 계보",
    providerEvent: "제공자 이벤트",
    eventRecordEyebrow: "이벤트 기록",
    callFacts: "콜 사실",
    eventTime: "이벤트 시각",
    processingTime: "처리 시각",
    processingDelay: "처리 지연",
    delayMinutes: (minutes) => `${minutes}분`,
    originalRating: "원문 투자의견",
    previousTarget: "이전 목표가",
    newTarget: "새 목표가",
    targetChange: "목표가 변경",
    targetDate: "목표 기준일",
    evidenceChain: "증거 연결",
    sourceProvenance: "출처 추적 정보",
    verified: "검증됨",
    unverifiedDemo: "검증되지 않은 DEMO",
    documentId: "문서 ID",
    referenceId: "참조 ID",
    publisher: "발행처",
    sourceType: "출처 유형",
    title: "제목",
    provider: "제공자",
    externalId: "외부 ID",
    published: "발행 시각",
    documentCaptured: "문서 수집 시각",
    referenceCaptured: "참조 수집 시각",
    documentDataMode: "문서 데이터 모드",
    referenceDataMode: "참조 데이터 모드",
    documentProvenance: "문서 출처 계보",
    referenceProvenance: "참조 출처 계보",
    license: "라이선스",
    contentHash: "콘텐츠 해시",
    extractedFragment: "추출 구간",
    pageTimeOffset: "페이지 / 시간 오프셋",
    confidence: "신뢰도",
    openCanonicalSource: "정규 출처 열기",
    canonicalSourceUnavailable: "정규 출처 URL: NA",
    pageLocation: (page) => `${page}페이지`,
    fromLocation: (milliseconds) => `${milliseconds} ms부터`,
    untilLocation: (milliseconds) => `${milliseconds} ms까지`,
    pointInTimeContext: "시점 기준 컨텍스트",
    marketSnapshot: "시장 스냅샷",
    immutablePointInTime: "변경 불가 시점 기준 기록",
    snapshotUnavailable: "스냅샷 이용 불가",
    snapshotId: "스냅샷 ID",
    snapshotEventTime: "스냅샷 이벤트 시각",
    snapshotProcessingTime: "스냅샷 처리 시각",
    captured: "수집 시각",
    assetId: "자산 ID",
    mutationPolicy: "변경 정책",
    appendOnly: "추가 전용, 수정 경로 없음",
    snapshotValuesLabel: "스냅샷 시장 값",
    assetPrice: "자산 가격",
    treasury2y: "미국 국채 2년물",
    treasury10y: "미국 국채 10년물",
    realYield: "실질 금리",
    gold: "금",
    volatility: "변동성",
    distance52WeekHigh: "52주 최고가 대비 거리",
    distanceAth: "역대 최고가 대비 거리",
    noInventedMarketValues: "이 콜에 없는 시장 값을 임의로 만들지 않았습니다.",
    deterministicScoring: "결정론적 평가",
    outcome: "성과",
    methodologyInactive: "방법론 미적용",
    directionalWin: "방향 적중",
    targetHit: "목표가 도달",
    alpha: "알파",
    methodologyVersion: "방법론 버전",
    outcomeNote: "버전이 있는 방법론으로 계산되기 전까지 성과 값은 NA로 유지됩니다. UI는 점수를 추론하지 않습니다.",
  },
  context: {
    macroSubject: "거시 스냅샷",
    scheduledSubject: "예정 이벤트 컨텍스트",
    availabilityEvidence: (subject) => `${subject} 가용성 증거`,
    asOfCallEvent: "콜 이벤트 기준",
    dataMode: "데이터 모드",
    source: "출처",
    provenance: "출처 계보",
    knownEmptyContext: "확인된 빈 컨텍스트",
    contextUnavailable: "컨텍스트 이용 불가",
    missingEvidence: (subject) => `이 콜에는 ${subject}이(가) 기록되지 않았습니다. 누락 값은 NA로 유지됩니다.`,
    macroProvenanceLabel: "거시 컨텍스트 출처 정보",
    snapshotId: "스냅샷 ID",
    asOf: "기준 시각",
    processingTime: "처리 시각",
    captured: "수집 시각",
    sources: "출처",
    perObservationSources: "아래 관측값별 참조",
    mutationPolicy: "변경 정책",
    appendOnly: "추가 전용, 수정 경로 없음",
    macroTableRegionLabel: "거시 관측 증거 표",
    macroTableCaption: "애널리스트 콜 이벤트 시점의 거시 관측값",
    series: "시계열",
    value: "값",
    unit: "단위",
    observationDate: "관측일",
    released: "발표 시각",
    processing: "처리 시각",
    vintageStart: "빈티지 시작",
    vintageEnd: "빈티지 종료",
    macroNote: "애널리스트 콜 이벤트 마감 시점에 이용 가능했던 관측 빈티지만 순서대로 표시합니다.",
    scheduledProvenanceLabel: "예정 이벤트 컨텍스트 출처 정보",
    contextId: "컨텍스트 ID",
    scheduleValuesLabel: "관측된 예정 이벤트 시각",
    earnings: "실적 발표",
    nextCpi: "다음 CPI",
    nextFomc: "다음 FOMC",
    nextNfp: "다음 NFP",
    optionsExpiration: "옵션 만기",
    scheduledNote: "콜 이벤트 마감 시점에 출처가 기록한 일정 시각입니다.",
    pointInTimeEvidence: "시점 기준 증거",
    macroContext: "거시 컨텍스트",
    observedScheduleEvidence: "관측 일정 증거",
    scheduledEventContext: "예정 이벤트 컨텍스트",
    immutable: "변경 불가",
    knownEmpty: "확인된 빈 상태",
    unavailable: "이용 불가",
  },
  states: {
    listLoadingEyebrow: "정규 이벤트 원장",
    listLoadingTitle: "애널리스트 콜을 불러오는 중…",
    listLoadingDescription: "버전이 있는 픽스처와 출처 정보를 읽고 있습니다.",
    errorEyebrow: "콜 원장 이용 불가",
    errorTitle: "픽스처를 읽을 수 없습니다.",
    errorDescription: "일부 기록이나 임의로 만든 기록은 표시하지 않습니다.",
    tryAgain: "다시 시도",
    returnDashboard: "대시보드로 돌아가기",
    detailLoadingEyebrow: "정규 이벤트 원장",
    detailLoadingTitle: "콜 증거를 불러오는 중…",
    detailLoadingDescription: "식별자, 출처 정보, 변경 불가 시점 기준 스냅샷을 확인하고 있습니다.",
    notFoundEyebrow: "콜을 찾을 수 없음",
    notFoundTitle: "이 이벤트는 픽스처 원장에 없습니다.",
    notFoundDescription: "요청한 식별자에 해당하는 정규 콜 기록이 없습니다. 대체 기록은 표시하지 않았습니다.",
    returnCalls: "애널리스트 콜로 돌아가기",
  },
} satisfies CallsMessages;

const en = {
  list: {
    eyebrow: "Canonical event ledger",
    title: "Analyst calls",
    summary: "Search point-in-time call events with their canonical identities and source evidence.",
    provenanceLabel: "Call dataset provenance",
    asOf: "As of",
    source: "Source",
    mode: "Mode",
    filterLabel: "Filter analyst calls",
    ticker: "Ticker",
    tickerPlaceholder: "e.g. NVDA",
    asset: "Asset",
    allAssets: "All assets",
    institution: "Institution",
    allInstitutions: "All institutions",
    analyst: "Analyst",
    allAnalysts: "All analysts",
    direction: "Direction",
    allDirections: "All directions",
    status: "Status",
    allStatuses: "All statuses",
    from: "From",
    throughDate: "Through date (UTC)",
    throughDateNote: "Applied as the next day's exclusive UTC bound.",
    dataMode: "Data mode",
    allModes: "All modes",
    sortBy: "Sort by",
    eventTime: "Event time",
    processingTime: "Processing time",
    capturedAt: "Captured at",
    order: "Order",
    descending: "Descending",
    ascending: "Ascending",
    rows: "Rows",
    applyFilters: "Apply filters",
    clear: "Clear",
    results: "Results",
    eventCount: (count) => `${count} ${count === 1 ? "event" : "events"}`,
    pageStatus: (page, totalPages, field, order) =>
      `Page ${page} of ${totalPages} · ${field},${order}`,
    emptyEyebrow: "No matching events",
    emptyTitle: "Nothing matches these filters.",
    emptyDescription: "Clear one or more filters. Missing records are never replaced with synthetic values.",
    clearAll: "Clear all filters",
    resultsRegionLabel: "Scrollable analyst calls results",
    tableCaption: "Filtered analyst call events",
    institutionAnalyst: "Institution / analyst",
    targetChange: "Target change",
    callsPagesLabel: "Calls pages",
    previous: "Previous",
    next: "Next",
  },
  detail: {
    back: "← Back to analyst calls",
    canonicalCall: (callId) => `Canonical analyst call · ${callId}`,
    callTitle: (institution, ticker) => `${institution} on ${ticker}`,
    analystUnavailable: "Analyst unavailable",
    callStatusLabel: "Call status",
    recordProvenanceLabel: "Call record provenance",
    asOf: "As of",
    dataMode: "Data mode",
    provenance: "Provenance",
    providerEvent: "Provider event",
    eventRecordEyebrow: "Event record",
    callFacts: "Call facts",
    eventTime: "Event time",
    processingTime: "Processing time",
    processingDelay: "Processing delay",
    delayMinutes: (minutes) => `${minutes} minutes`,
    originalRating: "Original rating",
    previousTarget: "Previous target",
    newTarget: "New target",
    targetChange: "Target change",
    targetDate: "Target date",
    evidenceChain: "Evidence chain",
    sourceProvenance: "Source provenance",
    verified: "Verified",
    unverifiedDemo: "Unverified DEMO",
    documentId: "Document ID",
    referenceId: "Reference ID",
    publisher: "Publisher",
    sourceType: "Source type",
    title: "Title",
    provider: "Provider",
    externalId: "External ID",
    published: "Published",
    documentCaptured: "Document captured",
    referenceCaptured: "Reference captured",
    documentDataMode: "Document data mode",
    referenceDataMode: "Reference data mode",
    documentProvenance: "Document provenance",
    referenceProvenance: "Reference provenance",
    license: "License",
    contentHash: "Content hash",
    extractedFragment: "Extracted fragment",
    pageTimeOffset: "Page / time offset",
    confidence: "Confidence",
    openCanonicalSource: "Open canonical source",
    canonicalSourceUnavailable: "Canonical source URL: NA",
    pageLocation: (page) => `Page ${page}`,
    fromLocation: (milliseconds) => `From ${milliseconds} ms`,
    untilLocation: (milliseconds) => `Until ${milliseconds} ms`,
    pointInTimeContext: "Point-in-time context",
    marketSnapshot: "Market snapshot",
    immutablePointInTime: "Immutable point-in-time record",
    snapshotUnavailable: "Snapshot unavailable",
    snapshotId: "Snapshot ID",
    snapshotEventTime: "Snapshot event time",
    snapshotProcessingTime: "Snapshot processing time",
    captured: "Captured",
    assetId: "Asset ID",
    mutationPolicy: "Mutation policy",
    appendOnly: "Append-only; no update surface",
    snapshotValuesLabel: "Snapshot market values",
    assetPrice: "Asset price",
    treasury2y: "Treasury 2Y",
    treasury10y: "Treasury 10Y",
    realYield: "Real yield",
    gold: "Gold",
    volatility: "Volatility",
    distance52WeekHigh: "Distance from 52W high",
    distanceAth: "Distance from ATH",
    noInventedMarketValues: "No market values were invented for this call.",
    deterministicScoring: "Deterministic scoring",
    outcome: "Outcome",
    methodologyInactive: "Methodology not active",
    directionalWin: "Directional win",
    targetHit: "Target hit",
    alpha: "Alpha",
    methodologyVersion: "Methodology version",
    outcomeNote: "Outcome values remain NA until a versioned methodology is calculated. The UI never infers a score.",
  },
  context: {
    macroSubject: "macro snapshot",
    scheduledSubject: "scheduled-event context",
    availabilityEvidence: (subject) => `${subject} availability evidence`,
    asOfCallEvent: "As of call event",
    dataMode: "Data mode",
    source: "Source",
    provenance: "Provenance",
    knownEmptyContext: "Known-empty context",
    contextUnavailable: "Context unavailable",
    missingEvidence: (subject) => `No ${subject} was recorded for this call. Missing values remain NA.`,
    macroProvenanceLabel: "Macro context provenance",
    snapshotId: "Snapshot ID",
    asOf: "As of",
    processingTime: "Processing time",
    captured: "Captured",
    sources: "Sources",
    perObservationSources: "Per-observation references below",
    mutationPolicy: "Mutation policy",
    appendOnly: "Append-only; no update surface",
    macroTableRegionLabel: "Macro observation evidence table",
    macroTableCaption: "Macro observations at analyst-call event time",
    series: "Series",
    value: "Value",
    unit: "Unit",
    observationDate: "Observation date",
    released: "Released",
    processing: "Processing",
    vintageStart: "Vintage start",
    vintageEnd: "Vintage end",
    macroNote: "Only the ordered observation vintages available at the analyst-call event cutoff are shown.",
    scheduledProvenanceLabel: "Scheduled event context provenance",
    contextId: "Context ID",
    scheduleValuesLabel: "Observed scheduled event timestamps",
    earnings: "Earnings",
    nextCpi: "Next CPI",
    nextFomc: "Next FOMC",
    nextNfp: "Next NFP",
    optionsExpiration: "Options expiration",
    scheduledNote: "These are source-recorded schedule timestamps at the call event cutoff.",
    pointInTimeEvidence: "Point-in-time evidence",
    macroContext: "Macro context",
    observedScheduleEvidence: "Observed schedule evidence",
    scheduledEventContext: "Scheduled event context",
    immutable: "Immutable",
    knownEmpty: "Known empty",
    unavailable: "Unavailable",
  },
  states: {
    listLoadingEyebrow: "Canonical event ledger",
    listLoadingTitle: "Loading analyst calls…",
    listLoadingDescription: "Reading the versioned fixture and source provenance.",
    errorEyebrow: "Call ledger unavailable",
    errorTitle: "The fixture could not be read.",
    errorDescription: "No partial or invented records are being displayed.",
    tryAgain: "Try again",
    returnDashboard: "Return to dashboard",
    detailLoadingEyebrow: "Canonical event ledger",
    detailLoadingTitle: "Loading call evidence…",
    detailLoadingDescription: "Resolving identities, provenance, and the immutable point-in-time snapshot.",
    notFoundEyebrow: "Call not found",
    notFoundTitle: "This event is not in the fixture ledger.",
    notFoundDescription: "The requested identifier has no canonical call record. No substitute record was shown.",
    returnCalls: "Return to analyst calls",
  },
} satisfies CallsMessages;

export const CALLS_MESSAGES = { ko, en } as const satisfies Record<Locale, CallsMessages>;

export function getCallsMessages(locale: Locale): CallsMessages {
  return CALLS_MESSAGES[locale];
}
