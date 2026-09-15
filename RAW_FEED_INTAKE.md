# 원시 체결 데이터 도입 전 자료 점검

현재 단계는 **문서 목록 점검**이다. 공급원·이용 권리·체결 데이터의 정확성이나
누락 여부를 검증하거나 승인하지 않는다. Vunelix 참고 위젯과 독립적이며,
점검 결과로 위젯·수집기·채점 기능을 켜지 않는다.

## 로컬에서 실행

Python 3.12 이상 표준 라이브러리만 사용한다. API 키, 계정, 도메인, DB,
시세 데이터, 네트워크 연결이 필요 없다.

```text
python scripts/review_raw_feed_intake.py examples/raw-feed-intake/blank.json
```

빈 예제는 특정 공급원을 선택하지 않는다. 결과는 `DOCUMENTS_MISSING`, 종료 코드는
`2`이며 공급자·상품·주거래소 식별 정보와 문서 15종이 부족하다고 표시한다.
이 결과는 프로그램 장애가 아니라 의도한 준비 전 상태다.

실제 후보를 조사할 때에는 예제를 **추적되지 않는 로컬 파일**로 복사해 작성한다.
비밀키, 로그인 정보, 비공개 계약 원문, 원시 체결 데이터는 넣지 않는다.
실제 작성한 파일이나 출력 보고서를 별도 승인 없이 공개 저장소에 올리지 않는다.

## 입력 형식

- `format`: 정확히 `wsr-raw-feed-intake-v1`.
- `candidate`: `provider`, `product`, `primaryVenue` 세 필드. 미정은 `null`,
  입력 시 각각 1~128자 문자열. 공백 문자열이나 자동 추정은 허용하지 않는다.
- `evidence`: 아래 식별자를 사용하는 목록. 각 항목은 `requirement`와
  `references`만 가진다. 빠진 항목이나 빈 references는 자료 미제출이다.
- reference: `url`, `section`, `revision` 세 문자열만 가진다. 각각 최대
  2048/256/128자. 주소에는 HTTPS 문서 URL만 사용하고 사용자 정보·포트·query·fragment를
  넣지 않는다. 문서 내 위치는 `section`으로 표시한다. 같은 항목에 동일한
  세 필드 조합을 중복 제출할 수 없고 항목별 최대 5개까지 허용한다.

문서 하나가 여러 요구사항을 설명하면 다른 항목에서 같은 문서를 참조할 수 있다.
입력된 문서의 존재·버전·관련성·내용·서명·유효기간은 도구가 확인하지 않는다.
공개 주소가 없거나 계약 자료가 비공개라면 빈 항목을 유지하고 사람이 따로 검토한다.
승인 여부를 적는 필드는 없으며, 승인 플래그·가격·ticks·API 키 같은 추가 필드는 거부한다.

## 필요한 문서

이 목록은 기존 [ADR-034](decisions/ADR-034-point-in-time-raw-window-coverage-foundation.md)의
검토 항목을 준비하기 위한 인벤토리다. 해당 ADR의 선행 조건을 완화하지 않는다.

| 식별자 | 사람이 확인할 내용 |
| --- | --- |
| `FEED_SCOPE_AND_HISTORY` | 정확한 상품·주거래소·정규장 체결 범위와 필요한 과거 기간 |
| `PROVIDER_EVENT_IDENTITY` | 관측·공급자 이벤트·수정 버전의 식별 규칙 |
| `SEQUENCE_AND_GAP_RECOVERY` | 시퀀스·watermark·누락 탐지 및 복구 규칙 |
| `CORRECTIONS_BUSTS_AND_FINALITY` | 정정·체결 취소 연결과 완료 시점의 증명 |
| `TRADE_CONDITIONS_AND_AUCTIONS` | 거래 조건 코드·경매 체결의 포함/제외 규칙 |
| `HALTS_AND_SILENT_INTERVALS` | 거래 정지·무거래 구간과 데이터 누락의 구분 |
| `EXCHANGE_CALENDAR` | 거래소 일정·세션·일정 수정 이력 |
| `CORPORATE_ACTIONS_AND_PRICE_BASIS` | 기업행동과 가격 조정 기준의 연속성 |
| `POINT_IN_TIME_AVAILABILITY` | 이벤트 발생·공개·수집 시각과 수정 정보의 당시 가시성 |
| `HISTORICAL_ACCESS_RIGHTS` | 필요한 과거 기간 데이터의 접근 권한 |
| `STORAGE_AND_CACHE_RIGHTS` | 저장·캐시 범위와 조건 |
| `DERIVED_CALCULATION_RIGHTS` | 수익률 등 파생 계산 사용 범위와 조건 |
| `PUBLIC_DISPLAY_RIGHTS` | 공개 웹 화면 표시 범위와 조건 |
| `REDISTRIBUTION_RIGHTS` | 데이터·결과 재배포 범위와 조건 |
| `PUBLISHER_AND_RESELLER_GRANTS` | 원 제공자와 재판매자의 권한·조건 차이 |

## 출력의 의미

| 종료 코드 | 상태 | 의미 |
| --- | --- | --- |
| `0` | `READY_FOR_HUMAN_REVIEW` | 필수 문자열과 각 항목의 참조가 제출됨. 내용 검증·승인 아님 |
| `2` | `DOCUMENTS_MISSING` | 식별 정보 또는 문서 참조가 빠짐 |
| `1` | stderr의 고정 오류 코드 | 읽기·크기·형식 오류. 부분 보고서를 출력하지 않음 |

두 정상 보고서 모두 `providerApproved`, `rawCoverageVerified`, `ingestionAllowed`,
`scoringEnabled`는 **항상 false**다. 종료 코드 0도 배포·수집 승인 조건으로 사용하지 않는다.
최종 제품 검토나 법적 판단을 대신하는 도구가 아니다.

보고서는 입력 원문·공급자 이름·문서 주소·경로를 되풀이하지 않는다. 항목별 참조 수,
누락 식별자와 정확한 입력 바이트의 SHA-256을 출력한다. 이 해시는 파일 동일성만
나타내며 문서의 진위·내용이나 데이터 출처를 증명하지 않는다. JSON 공백이나 목록
순서가 달라지면 해시도 달라진다. 요구사항 출력 순서는 고정이다.

입력은 UTF-8 JSON, 최대 64 KiB이며 중복 JSON 키·잘못된 타입·미지 필드·제어 문자·
고립 surrogate를 거부한다. 한 로컬 일반 파일만 읽고 링크/reparse 경로·UNC 경로를
거부한다. 파일을 수정하거나 보고서 파일을 자동 생성하지 않는다. URL 요청,
DNS 조회, `.env` 로딩, 공급자 자동 선택, 비밀값 탐색, DB·프로세스 실행은 없다.
파일시스템 자체의 네트워크 매핑이나 적대적인 동시 경로 변경까지 격리하는 샌드박스는
아니므로 통제하는 로컬 경로에서 실행한다.

## 다음 단계와 검증

실제 후보의 문서를 준비한 뒤 사람이 상품 범위·프로토콜·권리 내용을 검토하고
사용자 승인을 받아야 한다. 그 다음에야 별도 ADR로 원시 체결 coverage 계약·매핑·
golden 테스트를 만들 수 있다. MFE/MAE, alpha, 전체 평가 lifecycle, 순위는 아직
완성되지 않았다. 빈 예제나 합성 테스트를 시장 데이터로 간주하지 않는다.

```text
python -m unittest discover -s scripts/ci -p test_raw_feed_intake.py
python -m unittest discover -s scripts/ci -p test_raw_feed_intake_contracts.py
python scripts/ci/run_contracts.py validate
```

뒤의 저장소 검사는 기존 `scripts/ci/requirements.txt` 환경을 사용한다.
웹·Java·DB·Vunelix 활성화 설정은 이 단계에서 바꾸지 않는다.
