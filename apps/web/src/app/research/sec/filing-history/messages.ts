import type { Locale } from "@/lib/i18n/config";
import type { SecManifestAuditView } from "@/lib/providers/sec-manifest-audit-provider";

export type SecManifestAuditMessages = {
  page: {
    eyebrow: string;
    title: string;
    summary: string;
    back: string;
  };
  locator: {
    eyebrow: string;
    title: string;
    description: string;
    manifestId: string;
    manifestHint: string;
    evaluationAsOf: string;
    evaluationHint: string;
    submit: string;
    invalidTitle: string;
    invalidBody: string;
    demoEyebrow: string;
    demoTitle: string;
    demoBody: string;
    demoOpen: string;
  };
  policy: {
    label: string;
    exactTitle: string;
    exactBody: string;
    noSelectorTitle: string;
    noSelectorBody: string;
    noNowTitle: string;
    noNowBody: string;
  };
  presentation: {
    fixture: string;
    fixtureBody: string;
    api: string;
    apiBody: string;
  };
  tabs: Record<SecManifestAuditView, string>;
  evidence: {
    label: string;
    manifestId: string;
    evaluationAsOf: string;
    schema: string;
    policy: string;
    fixedOrder: string;
    page: string;
    pageValue: (number: number, totalPages: number) => string;
  };
  summary: {
    eyebrow: string;
    title: string;
    identityTitle: string;
    countsTitle: string;
    disclosureTitle: string;
    manifestSchemaVersion: string;
    provider: string;
    product: string;
    reconciliationPolicy: string;
    selectionSha256: string;
    rootCaptureId: string;
    rootCapturedAt: string;
    cik: string;
    evidenceAvailableAt: string;
    assembledAt: string;
    selectionCoverage: string;
    immutable: string;
    advertisedDescriptors: string;
    selectedDescriptors: string;
    omittedDescriptors: string;
    sourceOccurrences: string;
    distinctAccessions: string;
    singleSource: string;
    exactAgreement: string;
    canonicalConflict: string;
    coverageScope: string;
    atomicSnapshot: string;
    currentHistory: string;
    corrections: string;
    amendments: string;
    legalAuthority: string;
    disclosureBody: string;
  };
  table: {
    descriptorsTitle: string;
    descriptorsCaption: string;
    accessionsTitle: string;
    accessionsCaption: string;
    occurrencesTitle: string;
    occurrencesCaption: string;
    ordinal: string;
    fileName: string;
    advertisedCount: string;
    advertisedRange: string;
    selectionState: string;
    selectedCapture: string;
    accession: string;
    occurrenceCount: string;
    distinctProjections: string;
    comparison: string;
    source: string;
    sourceCapture: string;
    descriptor: string;
    sourceRow: string;
    projection: string;
    form: string;
    filingDate: string;
    reportDate: string;
    acceptedAt: string;
    primaryDocument: string;
    emptyTitle: string;
    emptyBody: string;
    previous: string;
    next: string;
    orderNotice: string;
    advertisedRangeNotice: string;
    conflictNotice: string;
    documentNotice: string;
  };
  states: {
    loadingEyebrow: string;
    loadingTitle: string;
    loadingBody: string;
    errorEyebrow: string;
    errorTitle: string;
    errorBody: string;
    retry: string;
    notFoundEyebrow: string;
    notFoundTitle: string;
    notFoundBody: string;
    returnLocator: string;
  };
};

const ko: SecManifestAuditMessages = {
  page: {
    eyebrow: "정확 ID 기반 SEC 증거",
    title: "SEC 제출 이력 manifest 감사",
    summary:
      "이미 저장된 하나의 immutable manifest를 지정한 시점 기준으로 읽습니다. 회사·최신·현재 이력을 검색하거나 완전성을 추정하지 않습니다.",
    back: "정확 증거 조회로 돌아가기",
  },
  locator: {
    eyebrow: "알려진 증거 식별자",
    title: "정확한 manifest와 기준 시각을 입력하세요.",
    description:
      "두 값은 자동 선택되지 않습니다. API 조회 키에는 저장된 manifest ID와 원본 UTC Z 시각을 그대로 붙여넣으세요. 조회 결과의 사람이 읽는 시각은 KST로 표시됩니다.",
    manifestId: "Manifest ID",
    manifestHint: "소문자 SHA-256 64자리",
    evaluationAsOf: "평가 기준 원본 조회 키(UTC)",
    evaluationHint: "API 계약용 UTC Z 형식 · 결과 표시는 KST · 소수점 이하 최대 6자리",
    submit: "정확 증거 열기",
    invalidTitle: "조회 주소가 닫힌 문법과 맞지 않습니다.",
    invalidBody:
      "중복·알 수 없는 값, 잘못된 SHA-256, UTC가 아닌 시각 또는 비정규 페이지 값은 API를 호출하지 않고 거부합니다.",
    demoEyebrow: "합성 DEMO 예시",
    demoTitle: "Java 조립 경로로 생성한 감사 응답",
    demoBody:
      "실제 SEC 관측이 아닙니다. 충돌과 일치가 보존되는 화면 동작만 재현하는 고정 픽스처입니다.",
    demoOpen: "합성 DEMO 요약 열기",
  },
  policy: {
    label: "정확 증거 조회 정책",
    exactTitle: "정확 ID만 사용.",
    exactBody: "한 manifest ID와 한 evaluationAsOf만 조회하며 다른 기록으로 대체하지 않습니다.",
    noSelectorTitle: "최신·회사 선택 없음.",
    noSelectorBody: "CIK, 티커, 회사명, latest/current manifest를 고르는 기능이 아닙니다.",
    noNowTitle: "현재 시각 자동 입력 없음.",
    noNowBody: "기준 시각은 사용자가 제공한 값이며 서버 시계를 기본 사실로 승격하지 않습니다.",
  },
  presentation: {
    fixture: "합성 DEMO · 실제 SEC 자료 아님",
    fixtureBody:
      "이 응답은 Java domain assembly와 ADR-052 응답 매퍼에서 생성한 고정 테스트 증거입니다.",
    api: "저장된 감사 API 응답",
    apiBody:
      "API 응답에는 dataMode가 없습니다. 이 표시는 전송 경로만 설명하며 LIVE·REALTIME을 주장하지 않습니다.",
  },
  tabs: {
    summary: "요약",
    descriptors: "Descriptor",
    accessions: "Accession 비교",
    occurrences: "원본 occurrence",
  },
  evidence: {
    label: "정확 감사 요청",
    manifestId: "Manifest ID",
    evaluationAsOf: "Evaluation as of",
    schema: "감사 스키마",
    policy: "감사 정책",
    fixedOrder: "고정 정렬",
    page: "응답 페이지",
    pageValue: (number, totalPages) => `요청 ${number + 1} · 전체 ${totalPages}`,
  },
  summary: {
    eyebrow: "Immutable manifest 요약",
    title: "선택·조립·충돌 증거",
    identityTitle: "Manifest 정체성과 시점",
    countsTitle: "검증된 manifest 집계",
    disclosureTitle: "명시적 비주장",
    manifestSchemaVersion: "Manifest 스키마",
    provider: "Provider",
    product: "Product",
    reconciliationPolicy: "조정 정책",
    selectionSha256: "Selection SHA-256",
    rootCaptureId: "Root capture ID",
    rootCapturedAt: "Root captured at",
    cik: "CIK",
    evidenceAvailableAt: "Evidence available at",
    assembledAt: "Assembled at",
    selectionCoverage: "선택 범위",
    immutable: "Immutable",
    advertisedDescriptors: "광고된 descriptor",
    selectedDescriptors: "선택된 descriptor",
    omittedDescriptors: "선택되지 않은 descriptor",
    sourceOccurrences: "원본 occurrence",
    distinctAccessions: "고유 accession",
    singleSource: "단일 출처",
    exactAgreement: "정확 일치",
    canonicalConflict: "Canonical 충돌",
    coverageScope: "Coverage scope",
    atomicSnapshot: "원자적 SEC 스냅샷",
    currentHistory: "현재 이력 상태",
    corrections: "정정·삭제 상태",
    amendments: "수정신고 연결",
    legalAuthority: "법적 권위",
    disclosureBody:
      "Canonical 토큰은 번역하지 않습니다. 아래 항목은 이 응답이 주장하지 않거나 해결하지 않은 범위를 정확히 기록합니다.",
  },
  table: {
    descriptorsTitle: "광고된 historical descriptor",
    descriptorsCaption: "Provider가 광고한 descriptor와 이 manifest의 정확한 선택 상태",
    accessionsTitle: "Accession occurrence 비교",
    accessionsCaption: "승자를 고르지 않은 accession 단위 일치·충돌 분류",
    occurrencesTitle: "Manifest 원본 occurrence",
    occurrencesCaption: "Manifest source order로 보존된 모든 source occurrence",
    ordinal: "Ordinal",
    fileName: "파일명",
    advertisedCount: "광고된 건수",
    advertisedRange: "광고된 날짜 범위",
    selectionState: "선택 상태",
    selectedCapture: "선택된 capture",
    accession: "Accession",
    occurrenceCount: "Occurrence 수",
    distinctProjections: "고유 projection",
    comparison: "비교 결과",
    source: "출처 종류",
    sourceCapture: "출처 capture",
    descriptor: "Descriptor",
    sourceRow: "출처 행",
    projection: "Projection SHA-256",
    form: "Form",
    filingDate: "Filing date",
    reportDate: "Report date",
    acceptedAt: "Accepted at",
    primaryDocument: "Primary document URI",
    emptyTitle: "이 정확한 응답 페이지에 항목이 없습니다.",
    emptyBody: "전체 SEC 이력이 비었다거나 완전하다는 뜻이 아니며 대체 행을 만들지 않았습니다.",
    previous: "이전 페이지",
    next: "다음 페이지",
    orderNotice: "정렬은 manifest ordinal 오름차순이며 SEC 연대순 주장이 아닙니다.",
    advertisedRangeNotice:
      "광고된 날짜 범위는 provider descriptor 값이며 실제 filing 최솟값·최댓값 또는 공백 증거가 아닙니다.",
    conflictNotice:
      "CANONICAL_CONFLICT는 서로 다른 projection이 보존됐다는 뜻입니다. 이 화면은 승자나 canonical filing을 선택하지 않습니다.",
    documentNotice:
      "문서 URI는 검증된 응답 원문을 텍스트로만 표시합니다. NA는 관측된 null이며 다른 링크로 대체하지 않습니다.",
  },
  states: {
    loadingEyebrow: "정확 SEC manifest 감사",
    loadingTitle: "지정한 증거를 검증하는 중…",
    loadingBody: "검증이 끝나기 전에 일부 manifest, 수치, 표 또는 대체 픽스처를 표시하지 않습니다.",
    errorEyebrow: "감사 증거 이용 불가",
    errorTitle: "정확한 manifest 응답을 검증할 수 없습니다.",
    errorBody: "부분 응답, 빈 표, 합성 값 또는 다른 manifest를 대신 표시하지 않습니다.",
    retry: "다시 시도",
    notFoundEyebrow: "해당 시점에 사용할 수 없는 증거",
    notFoundTitle: "이 정확한 manifest를 표시할 수 없습니다.",
    notFoundBody:
      "ID가 없거나 지정한 기준 시각에는 아직 보이지 않습니다. 두 경우를 구분하거나 다른 기록으로 대체하지 않습니다.",
    returnLocator: "정확 증거 조회로 돌아가기",
  },
};

const en: SecManifestAuditMessages = {
  page: {
    eyebrow: "Exact-ID SEC evidence",
    title: "SEC filing-history manifest audit",
    summary:
      "Read one already-persisted immutable manifest at an explicit point in time. This does not search company, latest, or current history or infer completeness.",
    back: "Return to exact evidence lookup",
  },
  locator: {
    eyebrow: "Known evidence identity",
    title: "Enter an exact manifest and evaluation cutoff.",
    description:
      "Neither value is selected automatically. Paste the stored manifest ID and original UTC Z API key. Human-readable result times are displayed in KST.",
    manifestId: "Manifest ID",
    manifestHint: "64 lowercase SHA-256 characters",
    evaluationAsOf: "Original lookup key (UTC)",
    evaluationHint: "UTC Z API contract · results display KST · at most six fractional digits",
    submit: "Open exact evidence",
    invalidTitle: "The lookup URL does not match the closed grammar.",
    invalidBody:
      "Duplicate or unknown values, malformed SHA-256, non-UTC instants, and non-canonical page values are rejected before any API request.",
    demoEyebrow: "Synthetic DEMO example",
    demoTitle: "Audit response generated through the Java assembly path",
    demoBody:
      "This is not an observed SEC record. It is a fixed fixture that exercises preserved agreement and conflict states.",
    demoOpen: "Open synthetic DEMO summary",
  },
  policy: {
    label: "Exact evidence lookup policy",
    exactTitle: "Exact identity only.",
    exactBody: "One manifest ID and one evaluationAsOf are read without substituting another record.",
    noSelectorTitle: "No latest or company selector.",
    noSelectorBody: "This does not choose by CIK, ticker, company, or a latest/current manifest.",
    noNowTitle: "No automatic current time.",
    noNowBody: "The cutoff is supplied by the caller; server time is not promoted to an observed fact.",
  },
  presentation: {
    fixture: "Synthetic DEMO · not observed SEC data",
    fixtureBody:
      "This response is a fixed test artifact generated by the Java domain assembly and ADR-052 response mapper.",
    api: "Persisted audit API response",
    apiBody:
      "The API response has no dataMode. This label describes transport only and does not claim LIVE or REALTIME data.",
  },
  tabs: {
    summary: "Summary",
    descriptors: "Descriptors",
    accessions: "Accession comparison",
    occurrences: "Source occurrences",
  },
  evidence: {
    label: "Exact audit request",
    manifestId: "Manifest ID",
    evaluationAsOf: "Evaluation as of",
    schema: "Audit schema",
    policy: "Audit policy",
    fixedOrder: "Fixed order",
    page: "Response page",
    pageValue: (number, totalPages) => `Requested ${number + 1} · total ${totalPages}`,
  },
  summary: {
    eyebrow: "Immutable manifest summary",
    title: "Selection, assembly, and conflict evidence",
    identityTitle: "Manifest identity and time",
    countsTitle: "Verified manifest totals",
    disclosureTitle: "Explicit non-claims",
    manifestSchemaVersion: "Manifest schema",
    provider: "Provider",
    product: "Product",
    reconciliationPolicy: "Reconciliation policy",
    selectionSha256: "Selection SHA-256",
    rootCaptureId: "Root capture ID",
    rootCapturedAt: "Root captured at",
    cik: "CIK",
    evidenceAvailableAt: "Evidence available at",
    assembledAt: "Assembled at",
    selectionCoverage: "Selection coverage",
    immutable: "Immutable",
    advertisedDescriptors: "Advertised descriptors",
    selectedDescriptors: "Selected descriptors",
    omittedDescriptors: "Unselected descriptors",
    sourceOccurrences: "Source occurrences",
    distinctAccessions: "Distinct accessions",
    singleSource: "Single source",
    exactAgreement: "Exact agreement",
    canonicalConflict: "Canonical conflict",
    coverageScope: "Coverage scope",
    atomicSnapshot: "Atomic SEC snapshot",
    currentHistory: "Current-history status",
    corrections: "Correction/removal status",
    amendments: "Amendment linkage",
    legalAuthority: "Legal authority",
    disclosureBody:
      "Canonical tokens are not translated. These fields state exactly what the response does not claim or resolve.",
  },
  table: {
    descriptorsTitle: "Advertised historical descriptors",
    descriptorsCaption: "Provider-advertised descriptors and their exact selection state in this manifest",
    accessionsTitle: "Accession occurrence comparison",
    accessionsCaption: "Accession agreement and conflict classes without choosing a winner",
    occurrencesTitle: "Manifest source occurrences",
    occurrencesCaption: "Every source occurrence preserved in manifest source order",
    ordinal: "Ordinal",
    fileName: "File name",
    advertisedCount: "Advertised count",
    advertisedRange: "Advertised date range",
    selectionState: "Selection state",
    selectedCapture: "Selected capture",
    accession: "Accession",
    occurrenceCount: "Occurrences",
    distinctProjections: "Distinct projections",
    comparison: "Comparison",
    source: "Source kind",
    sourceCapture: "Source capture",
    descriptor: "Descriptor",
    sourceRow: "Source row",
    projection: "Projection SHA-256",
    form: "Form",
    filingDate: "Filing date",
    reportDate: "Report date",
    acceptedAt: "Accepted at",
    primaryDocument: "Primary document URI",
    emptyTitle: "This exact response page contains no items.",
    emptyBody: "This does not mean all SEC history is empty or complete, and no substitute row was created.",
    previous: "Previous page",
    next: "Next page",
    orderNotice: "Order is manifest ordinal ascending, not a claim of SEC chronology.",
    advertisedRangeNotice:
      "Advertised date ranges are provider descriptor values, not observed filing extrema or gap evidence.",
    conflictNotice:
      "CANONICAL_CONFLICT preserves different projections. This screen does not choose a winner or canonical filing.",
    documentNotice:
      "Document URIs are displayed as validated response text only. NA is an observed null and is not replaced with another link.",
  },
  states: {
    loadingEyebrow: "Exact SEC manifest audit",
    loadingTitle: "Verifying the requested evidence…",
    loadingBody: "No partial manifest, metric, table, or substitute fixture is shown before verification completes.",
    errorEyebrow: "Audit evidence unavailable",
    errorTitle: "The exact manifest response could not be verified.",
    errorBody: "No partial response, empty table, synthetic value, or alternate manifest is shown as fallback.",
    retry: "Try again",
    notFoundEyebrow: "Evidence unavailable at this cutoff",
    notFoundTitle: "This exact manifest cannot be displayed.",
    notFoundBody:
      "The ID is absent or not yet visible at the supplied cutoff. Those cases are not distinguished and no other record is substituted.",
    returnLocator: "Return to exact evidence lookup",
  },
};

const MESSAGES = { ko, en } as const satisfies Record<Locale, SecManifestAuditMessages>;

export function getSecManifestAuditMessages(locale: Locale): SecManifestAuditMessages {
  return MESSAGES[locale];
}
