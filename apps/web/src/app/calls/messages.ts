import type { Locale } from "@/lib/i18n/config";

export type CallsMessages = {
  list: {
    eyebrow: string;
    title: string;
    summary: string;
    provenanceLabel: string;
    returnedPageEvidenceLabel: string;
    latestReturnedCapture: string;
    returnedCallProvenance: string;
    returnedPageEvidenceNote: string;
    asOf: string;
    source: string;
    mode: string;
    datasetMetadata: string;
    available: string;
    notExposed: string;
    datasetNotExposed: (reason: string) => string;
    filterLabel: string;
    ticker: string;
    tickerFilter: string;
    tickerPlaceholder: string;
    asset: string;
    assetIdFilter: string;
    allAssets: string;
    assetIdPlaceholder: string;
    institution: string;
    institutionIdFilter: string;
    allInstitutions: string;
    institutionIdPlaceholder: string;
    analyst: string;
    analystIdFilter: string;
    allAnalysts: string;
    analystIdPlaceholder: string;
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
    outOfRangeEyebrow: string;
    outOfRangeTitle: string;
    outOfRangeDescription: (count: number) => string;
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
    revisionHistoryEyebrow: string;
    revisionHistory: string;
    revisionCount: (count: number) => string;
    revisionAppendOnly: string;
    noRevisionsTitle: string;
    noRevisionsDescription: string;
    revisionItemLabel: (sequence: number, type: string) => string;
    revisionId: string;
    revisionSchema: string;
    revisionCallId: string;
    revisionSequence: string;
    revisionType: string;
    supersedesRevision: string;
    revisionEventTime: string;
    revisionProcessingTime: string;
    revisionCapturedAt: string;
    revisionReason: string;
    revisionProvider: string;
    revisionProviderEvent: string;
    revisionSourceReference: string;
    revisionDataMode: string;
    revisionProvenance: string;
    correctedTerms: string;
    correctionTermsLabel: string;
    cancellationTermsUnavailable: string;
    correctedDirection: string;
    correctedRating: string;
    correctedPreviousTarget: string;
    correctedTarget: string;
    correctedCurrency: string;
    correctedTargetDate: string;
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
    outcomeAuditEyebrow: string;
    outcome: string;
    outcomeBoundary: string;
    outcomeCount: (count: number) => string;
    outcomeAppendOnly: string;
    outcomeNullPolicy: string;
    outcomeNoCancellationInference: string;
    noOutcomesTitle: string;
    noOutcomesDescription: string;
    outcomeItemLabel: (sequence: number, horizon: string, methodologyVersion: string, status: string) => string;
    outcomeId: string;
    outcomeSchemaVersion: string;
    outcomeCallId: string;
    outcomeHorizon: string;
    outcomeBasisRevision: string;
    outcomeCancellationRevision: string;
    outcomeSnapshotId: string;
    methodologyId: string;
    directionalWin: string;
    targetHit: string;
    assetReturn: string;
    benchmarkReturn: string;
    sectorReturn: string;
    alpha: string;
    sectorAlpha: string;
    mfe: string;
    mae: string;
    targetError: string;
    methodologyVersion: string;
    methodologyDefinitionHash: string;
    inputFingerprint: string;
    outcomeSequence: string;
    supersedesOutcome: string;
    evaluationStatus: string;
    reasonCode: string;
    outcomeEventTime: string;
    outcomeProcessingTime: string;
    outcomeCapturedAt: string;
    outcomeDataComplete: string;
    outcomeDataMode: string;
    outcomeProvenance: string;
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
    returnedPageEvidenceLabel: "반환된 페이지 범위의 콜 증거",
    latestReturnedCapture: "반환된 콜의 최신 수집 시각",
    returnedCallProvenance: "반환된 콜 출처 계보",
    returnedPageEvidenceNote: "이 값은 현재 응답 페이지에 반환된 콜만 요약하며 데이터셋 기준 시각, 최신성 또는 범위를 뜻하지 않습니다.",
    asOf: "기준 시각",
    source: "출처",
    mode: "모드",
    datasetMetadata: "데이터셋 메타데이터",
    available: "제공됨",
    notExposed: "NOT_EXPOSED",
    datasetNotExposed: (reason) => `목록 API는 데이터셋 기준 시각, 전체 출처, 전체 범위 또는 원문 고지문을 제공하지 않습니다. 현재 페이지에서 이를 추론하지 않습니다. 사유: ${reason}`,
    filterLabel: "애널리스트 콜 필터",
    ticker: "티커",
    tickerFilter: "티커 (대소문자 구분 없음)",
    tickerPlaceholder: "예: NVDA",
    asset: "자산",
    assetIdFilter: "자산 ID (대소문자 정확히 일치)",
    allAssets: "모든 자산",
    assetIdPlaceholder: "예: asset-nvda",
    institution: "기관",
    institutionIdFilter: "기관 ID (대소문자 정확히 일치)",
    allInstitutions: "모든 기관",
    institutionIdPlaceholder: "예: inst-gs",
    analyst: "애널리스트",
    analystIdFilter: "애널리스트 ID (대소문자 정확히 일치)",
    allAnalysts: "모든 애널리스트",
    analystIdPlaceholder: "예: analyst-demo-b",
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
    pageStatus: (page, totalPages, field, order) => totalPages === 0
      ? `결과 페이지 0개 · 요청 페이지 ${page + 1} · ${field},${order}`
      : page >= totalPages
        ? `요청 페이지 ${page + 1} · 전체 ${totalPages}페이지 · ${field},${order}`
        : `${page + 1}/${totalPages}페이지 · ${field},${order}`,
    emptyEyebrow: "일치하는 이벤트 없음",
    emptyTitle: "이 응답에는 필터와 일치하는 항목이 없습니다.",
    emptyDescription: "현재 응답의 빈 행을 다른 기록이나 합성 값으로 대체하지 않았으며 데이터셋 완전성을 주장하지 않습니다.",
    outOfRangeEyebrow: "요청 페이지 범위 초과",
    outOfRangeTitle: "요청한 응답 페이지에는 항목이 없습니다.",
    outOfRangeDescription: (count) => `필터와 일치하는 이벤트는 ${count}건이지만 요청한 페이지는 응답 범위를 벗어났습니다. 대체 행을 표시하지 않았습니다.`,
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
    revisionHistoryEyebrow: "불변 이벤트 계보",
    revisionHistory: "콜 변경 이력",
    revisionCount: (count) => `변경 이벤트 ${count}건`,
    revisionAppendOnly: "상단 상태는 변경 불가 원본 이벤트 필드이며 현재 또는 유효 의견을 뜻하지 않습니다. 원본 콜은 덮어쓰지 않고 정정과 취소 이벤트를 발생 순서대로 추가 전용 기록으로 표시합니다.",
    noRevisionsTitle: "기록된 변경 이벤트 없음",
    noRevisionsDescription: "이 조회 응답에는 기록된 정정 또는 취소 이벤트가 없습니다. 빈 이력을 다른 기록으로 대체하지 않았습니다.",
    revisionItemLabel: (sequence, type) => `변경 ${sequence} · ${type}`,
    revisionId: "변경 ID",
    revisionSchema: "스키마 버전",
    revisionCallId: "콜 ID",
    revisionSequence: "순서",
    revisionType: "변경 유형",
    supersedesRevision: "대체한 변경",
    revisionEventTime: "변경 이벤트 시각",
    revisionProcessingTime: "변경 처리 시각",
    revisionCapturedAt: "변경 수집 시각",
    revisionReason: "변경 사유",
    revisionProvider: "제공자",
    revisionProviderEvent: "제공자 이벤트",
    revisionSourceReference: "출처 참조 ID",
    revisionDataMode: "데이터 모드",
    revisionProvenance: "출처 계보",
    correctedTerms: "정정 조건",
    correctionTermsLabel: "정정된 콜 조건",
    cancellationTermsUnavailable: "취소 이벤트에는 정정 조건이 없습니다.",
    correctedDirection: "정정 방향",
    correctedRating: "정정 원문 투자의견",
    correctedPreviousTarget: "정정 이전 목표가",
    correctedTarget: "정정 목표가",
    correctedCurrency: "정정 통화",
    correctedTargetDate: "정정 목표 기준일",
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
    outcomeAuditEyebrow: "성과 감사 증거",
    outcome: "성과 감사 이력",
    outcomeBoundary: "감사 전용 · DEMO",
    outcomeCount: (count) => `성과 기록 ${count}건`,
    outcomeAppendOnly: "응답 순서를 그대로 보존한 추가 전용 이력입니다. 최신·현재·유효 성과로 접거나 대체하지 않습니다.",
    outcomeNullPolicy: "P2 감사 경계에서는 계산되지 않은 결과 필드를 JSON null로만 허용하며 UI는 이를 NA로 표시합니다.",
    outcomeNoCancellationInference: "콜 변경 이력의 취소를 EXCLUDED 성과나 다른 결과로 추론하지 않습니다.",
    noOutcomesTitle: "이 감사 응답에 기록된 성과 이력 없음",
    noOutcomesDescription: "이 감사 응답에는 기록된 성과 이벤트가 없습니다. 다른 이력이나 대체 결과를 표시하지 않았습니다.",
    outcomeItemLabel: (sequence, horizon, methodologyVersion, status) => `성과 기록 ${sequence} · ${horizon} · 방법론 ${methodologyVersion} · ${status}`,
    outcomeId: "성과 ID",
    outcomeSchemaVersion: "스키마 버전",
    outcomeCallId: "콜 ID",
    outcomeHorizon: "평가 구간",
    outcomeBasisRevision: "기준 변경 ID",
    outcomeCancellationRevision: "취소 증거 ID",
    outcomeSnapshotId: "스냅샷 ID",
    methodologyId: "방법론 ID",
    directionalWin: "방향 적중",
    targetHit: "목표가 도달",
    assetReturn: "자산 수익률",
    benchmarkReturn: "벤치마크 수익률",
    sectorReturn: "섹터 수익률",
    alpha: "알파",
    sectorAlpha: "섹터 알파",
    mfe: "최대 유리 변동(MFE)",
    mae: "최대 불리 변동(MAE)",
    targetError: "목표 오차",
    methodologyVersion: "방법론 버전",
    methodologyDefinitionHash: "방법론 정의 해시",
    inputFingerprint: "입력 지문",
    outcomeSequence: "계보 순번",
    supersedesOutcome: "직전 성과 ID",
    evaluationStatus: "평가 상태",
    reasonCode: "사유 코드",
    outcomeEventTime: "성과 이벤트 시각",
    outcomeProcessingTime: "성과 처리 시각",
    outcomeCapturedAt: "성과 수집 시각",
    outcomeDataComplete: "데이터 완결",
    outcomeDataMode: "데이터 모드",
    outcomeProvenance: "성과 출처 계보",
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
    listLoadingDescription: "버전이 있는 콜 증거와 출처 정보를 읽고 있습니다.",
    errorEyebrow: "콜 원장 이용 불가",
    errorTitle: "콜 증거를 읽을 수 없습니다.",
    errorDescription: "일부 기록이나 임의로 만든 기록은 표시하지 않습니다.",
    tryAgain: "다시 시도",
    returnDashboard: "대시보드로 돌아가기",
    detailLoadingEyebrow: "정규 이벤트 원장",
    detailLoadingTitle: "콜 증거를 불러오는 중…",
    detailLoadingDescription: "식별자, 출처 정보, 변경 불가 시점 기준 스냅샷을 확인하고 있습니다.",
    notFoundEyebrow: "콜을 찾을 수 없음",
    notFoundTitle: "이 이벤트는 정규 콜 원장에 없습니다.",
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
    returnedPageEvidenceLabel: "Returned-page call evidence",
    latestReturnedCapture: "Latest returned call capture",
    returnedCallProvenance: "Returned call provenance",
    returnedPageEvidenceNote: "These values summarize only calls in the returned response page. They are not dataset as-of, freshness, or coverage claims.",
    asOf: "As of",
    source: "Source",
    mode: "Mode",
    datasetMetadata: "Dataset metadata",
    available: "AVAILABLE",
    notExposed: "NOT_EXPOSED",
    datasetNotExposed: (reason) => `The list API does not expose dataset-wide as-of, full provenance, coverage, or a source-supplied disclaimer. This page does not infer them from returned rows. Reason: ${reason}`,
    filterLabel: "Filter analyst calls",
    ticker: "Ticker",
    tickerFilter: "Ticker (case-insensitive)",
    tickerPlaceholder: "e.g. NVDA",
    asset: "Asset",
    assetIdFilter: "Asset ID (exact case)",
    allAssets: "All assets",
    assetIdPlaceholder: "e.g. asset-nvda",
    institution: "Institution",
    institutionIdFilter: "Institution ID (exact case)",
    allInstitutions: "All institutions",
    institutionIdPlaceholder: "e.g. inst-gs",
    analyst: "Analyst",
    analystIdFilter: "Analyst ID (exact case)",
    allAnalysts: "All analysts",
    analystIdPlaceholder: "e.g. analyst-demo-b",
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
    pageStatus: (page, totalPages, field, order) => totalPages === 0
      ? `0 result pages · requested page ${page + 1} · ${field},${order}`
      : page >= totalPages
        ? `Requested page ${page + 1} · ${totalPages} total pages · ${field},${order}`
        : `Page ${page + 1} of ${totalPages} · ${field},${order}`,
    emptyEyebrow: "No matching events",
    emptyTitle: "This response contains no items matching these filters.",
    emptyDescription: "No substitute or synthetic rows were shown, and this empty response is not a dataset-completeness claim.",
    outOfRangeEyebrow: "Requested page out of range",
    outOfRangeTitle: "The requested response page contains no items.",
    outOfRangeDescription: (count) => `${count} events match the filters, but the requested page is outside the response range. No substitute rows were shown.`,
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
    revisionHistoryEyebrow: "Immutable event lineage",
    revisionHistory: "Call revision history",
    revisionCount: (count) => `${count} revision ${count === 1 ? "event" : "events"}`,
    revisionAppendOnly: "The status above is the immutable original-event field, not a current or effective stance. The original call is never overwritten; correction and cancellation events appear in append-only event order.",
    noRevisionsTitle: "No revision events recorded",
    noRevisionsDescription: "This response contains no recorded correction or cancellation events. No substitute history was shown.",
    revisionItemLabel: (sequence, type) => `Revision ${sequence} · ${type}`,
    revisionId: "Revision ID",
    revisionSchema: "Schema version",
    revisionCallId: "Call ID",
    revisionSequence: "Sequence",
    revisionType: "Revision type",
    supersedesRevision: "Supersedes revision",
    revisionEventTime: "Revision event time",
    revisionProcessingTime: "Revision processing time",
    revisionCapturedAt: "Revision captured at",
    revisionReason: "Revision reason",
    revisionProvider: "Provider",
    revisionProviderEvent: "Provider event",
    revisionSourceReference: "Source reference ID",
    revisionDataMode: "Data mode",
    revisionProvenance: "Provenance",
    correctedTerms: "Corrected terms",
    correctionTermsLabel: "Corrected call terms",
    cancellationTermsUnavailable: "Cancellation events do not carry corrected terms.",
    correctedDirection: "Corrected direction",
    correctedRating: "Corrected original rating",
    correctedPreviousTarget: "Corrected previous target",
    correctedTarget: "Corrected target",
    correctedCurrency: "Corrected currency",
    correctedTargetDate: "Corrected target date",
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
    outcomeAuditEyebrow: "Outcome audit evidence",
    outcome: "Outcome audit history",
    outcomeBoundary: "AUDIT ONLY · DEMO",
    outcomeCount: (count) => `${count} outcome record${count === 1 ? "" : "s"}`,
    outcomeAppendOnly: "This append-only history preserves response order. It is never folded or substituted as a latest, current, or effective outcome.",
    outcomeNullPolicy: "At the P2 audit boundary, uncalculated result fields are accepted only as JSON null and displayed as NA.",
    outcomeNoCancellationInference: "A cancellation in call revision history is not inferred as an EXCLUDED outcome or any other result.",
    noOutcomesTitle: "No outcome history recorded in this audit response",
    noOutcomesDescription: "No outcome event is recorded in this audit response. No other history or substitute result was shown.",
    outcomeItemLabel: (sequence, horizon, methodologyVersion, status) => `Outcome record ${sequence} · ${horizon} · methodology ${methodologyVersion} · ${status}`,
    outcomeId: "Outcome ID",
    outcomeSchemaVersion: "Schema version",
    outcomeCallId: "Call ID",
    outcomeHorizon: "Horizon",
    outcomeBasisRevision: "Basis revision ID",
    outcomeCancellationRevision: "Cancellation evidence ID",
    outcomeSnapshotId: "Snapshot ID",
    methodologyId: "Methodology ID",
    directionalWin: "Directional win",
    targetHit: "Target hit",
    assetReturn: "Asset return",
    benchmarkReturn: "Benchmark return",
    sectorReturn: "Sector return",
    alpha: "Alpha",
    sectorAlpha: "Sector alpha",
    mfe: "Maximum favorable excursion (MFE)",
    mae: "Maximum adverse excursion (MAE)",
    targetError: "Target error",
    methodologyVersion: "Methodology version",
    methodologyDefinitionHash: "Methodology definition hash",
    inputFingerprint: "Input fingerprint",
    outcomeSequence: "Lineage sequence",
    supersedesOutcome: "Superseded outcome ID",
    evaluationStatus: "Evaluation status",
    reasonCode: "Reason code",
    outcomeEventTime: "Outcome event time",
    outcomeProcessingTime: "Outcome processing time",
    outcomeCapturedAt: "Outcome captured at",
    outcomeDataComplete: "Data complete",
    outcomeDataMode: "Data mode",
    outcomeProvenance: "Outcome provenance",
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
    listLoadingDescription: "Reading versioned call evidence and source provenance.",
    errorEyebrow: "Call ledger unavailable",
    errorTitle: "Call evidence could not be read.",
    errorDescription: "No partial or invented records are being displayed.",
    tryAgain: "Try again",
    returnDashboard: "Return to dashboard",
    detailLoadingEyebrow: "Canonical event ledger",
    detailLoadingTitle: "Loading call evidence…",
    detailLoadingDescription: "Resolving identities, provenance, and the immutable point-in-time snapshot.",
    notFoundEyebrow: "Call not found",
    notFoundTitle: "This event is not in the canonical call ledger.",
    notFoundDescription: "The requested identifier has no canonical call record. No substitute record was shown.",
    returnCalls: "Return to analyst calls",
  },
} satisfies CallsMessages;

export const CALLS_MESSAGES = { ko, en } as const satisfies Record<Locale, CallsMessages>;

export function getCallsMessages(locale: Locale): CallsMessages {
  return CALLS_MESSAGES[locale];
}
