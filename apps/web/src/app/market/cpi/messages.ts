export function cpiMessages(locale: string) {
  return locale === "en" ? {
    title: "US consumer prices", summary: "CPI-U · US city average · Monthly, not seasonally adjusted (NSA)",
    back: "Back to market", names: ["All items CPI", "Core CPI · excluding food and energy"],
    retrieved: "Retrieved", latest: "Latest available observation", index: "Index", yoy: "Year over year", month: "Reference month",
    missing: "Not available", history: "Monthly history · 24 calendar months", source: "Source and retrieval evidence",
    caution: "Stored BLS retrieval, not real-time data. This is not an original-release vintage and must not be used to reconstruct what was known at a historical call time. Release time is not provided. Collection is manual; visiting this page does not refresh BLS.",
    method: "Index base: 1982–84 = 100. YoY = (index / index from the same month one year earlier − 1) × 100, calculated with decimal arithmetic and rounded once to 1 decimal. Missing months and unavailable comparisons remain blank.",
    disabled: "CPI collection is not enabled", disabledBody: "No observed data is displayed. An operator must collect BLS data and enable the stored-data connection.",
    empty: "No stored CPI retrieval is available", emptyBody: "Collection may not have run, or the read endpoint may be disabled. No DEMO values have been substituted.",
    loading: "Loading stored CPI data…", error: "Stored CPI data could not be loaded", retry: "Try again",
    errorBody: "The connection or validation failed. No substitute values were used.",
    stale: "Retrieval is more than 7 days old. This is an age warning, not proof that a newer release exists.",
    notes: "BLS footnotes", demo: "DEMO · Synthetic test data, not observed BLS values", capture: "Retrieval ID", hash: "Response SHA-256",
  } : {
    title: "미국 소비자물가", summary: "CPI-U · 미국 도시 평균 · 월간 비계절조정 지표 (NSA)",
    back: "시장으로 돌아가기", names: ["전체 CPI", "근원 CPI · 식품·에너지 제외"],
    retrieved: "수집 시각", latest: "가장 최근에 확보한 기준월", index: "지수", yoy: "전년 동월 대비", month: "기준월",
    missing: "자료 없음", history: "월별 이력 · 최근 24개 기준월", source: "출처 및 수집 증거",
    caution: "BLS에서 수집해 저장한 월간 자료이며 실시간 시세가 아닙니다. 최초 발표 당시의 빈티지가 아니므로 과거 콜 시점에 알려진 값을 재구성하는 데 사용하지 않습니다. 발표 시각은 제공되지 않습니다. 수집은 수동으로 실행하며 페이지 방문으로 BLS를 갱신하지 않습니다.",
    method: "지수 기준: 1982–84 = 100. 전년 동월 대비 = (당월 지수 / 전년 같은 달 지수 − 1) × 100. 십진 연산 후 소수점 한 자리로 한 번 반올림합니다. 누락된 월과 비교할 수 없는 값은 채우지 않습니다.",
    disabled: "CPI 수집 연결이 설정되지 않았습니다", disabledBody: "관측값을 표시하지 않습니다. 운영자가 BLS 데이터를 수집한 뒤 저장 자료 연결을 활성화해야 합니다.",
    empty: "저장된 CPI 수집 자료가 없습니다", emptyBody: "아직 수집하지 않았거나 조회 기능이 비활성 상태일 수 있습니다. DEMO 값으로 대체하지 않습니다.",
    loading: "저장된 CPI 자료를 불러오는 중…", error: "저장된 CPI 자료를 불러오지 못했습니다", retry: "다시 시도",
    errorBody: "연결 또는 데이터 검증에 실패했습니다. 다른 값으로 대체하지 않았습니다.",
    stale: "수집 후 7일이 지났습니다. 수집 자료의 나이에 대한 경고이며 새 발표가 있다는 의미는 아닙니다.",
    notes: "BLS 주석", demo: "DEMO · 합성 테스트 자료이며 BLS 관측값이 아닙니다", capture: "수집 ID", hash: "응답 SHA-256",
  };
}
