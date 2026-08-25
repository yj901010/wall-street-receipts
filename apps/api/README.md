# Wall Street Receipts API

Java 21과 Spring Boot 3.5.16 기반의 초기 API 애플리케이션이다. 기본 provider는 fixture이며 외부 vendor key 없이 실행된다.

## Run

```shell
./mvnw spring-boot:run
```

로컬 루트 `.env`를 읽어야 할 때는 `apps/api`에서 `local` 프로필을 명시한다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

`local` 프로필만 `../../.env`를 optional properties source로 불러온다. 테스트와
운영에서는 이 프로필을 사용하지 않고 실행 환경의 secret store가 환경변수를
직접 주입해야 한다.

기본 연결은 `localhost:5432/wsr`의 PostgreSQL이다. `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`로 변경할 수 있다.

상태 확인은 `GET /actuator/health`를 사용한다.

## SEC EDGAR provider foundation

ADR-035 establishes the default-disabled SEC EDGAR public-provider foundation.

ADR-036 establishes the single-process SEC live-operation safety gate.

ADR-037 establishes the in-memory SEC decoded-response receipt foundation.

ADR-038 establishes the SEC historical-segment descriptor catalog.

ADR-039 establishes append-only SEC root-capture persistence and exact-byte
replay.

ADR-040 establishes controlled single-descriptor historical-segment capture,
exact-byte replay, and append-only persistence.

SEC submissions metadata adapter는 기본 비활성화다. 로컬에서 명시적으로
활성화하려면 루트 `.env`에 다음 서버 전용 변수가 있어야 한다.

```dotenv
SEC_PROVIDER_ENABLED=true
SEC_BASE_URL=https://data.sec.gov
SEC_CONTACT_EMAIL=operations-contact@example.com
```

SEC는 API key 대신 선언된 연락처 User-Agent를 요구한다. 실제 연락처 값은
`.env.example`, 로그, HTTP 응답, Git에 넣지 않는다. 현재 adapter에는 one-shot
DB persistence services가 있지만 scheduler, controller, command-line trigger 또는
web consumer가 없으므로 활성화만으로 외부 요청이나 DB 적재가 발생하지 않는다.

SEC 공식 fair-access 상한은 여러 머신을 합쳐 초당 10회다. 애플리케이션 내부
정책은 단일 JVM에서 모든 SEC 요청을 하나의 limiter로 묶어 초당 8회, 요청 사이
최소 125ms로 고정하고 유휴 permit을 모아 burst하지 않는다. 이 제한은
process-local이므로 여러 replica, 독립 실행 도구, scheduler를 함께 실행할
근거가 아니다.

JSON mapper가 읽을 수 있는 decoded response는 8 MiB로 제한된다.
`Content-Length`가 없거나 chunked인 응답, gzip/deflate 압축이 8 MiB보다 크게
풀리는 응답도 stream 경계에서 fail closed한다. HTTP `429`는 자동 재시도하지
않는다. 유효한 delta-seconds 또는 RFC 1123 `Retry-After`는 cooldown 기간에만
사용하고, 누락·오류·과거 시각·10분 미만이면 최소 10분을 적용한다. cooldown
중인 호출은 요청 스레드를 10분간 재우지 않고 네트워크 전에 즉시 실패한다.

이는 SEC가 보장한 응답 크기나 `429`/`Retry-After` 계약이 아니라, SEC의 공식
aggregate 상한과 차단 후 10분 정책을 바탕으로 정한 내부 보수 정책이다.

### In-memory decoded response receipt

HTTP `200 application/json` 응답을 끝까지 읽고, 전송 계층이 광고한 gzip 또는
deflate를 정확히 한 번 해제한 뒤의 exact bytes에 SHA-256을 계산한다. digest는
64자리 lowercase hex다. charset 변환, 공백·줄바꿈·필드 순서 등 JSON 정규화,
파싱 후 재직렬화를 하지 않으며, digest에 사용한 동일한 owned bytes를 parser
version `SEC_SUBMISSIONS_CATALOG_V2`가 읽는다. charset이 없거나 UTF-8로 선언된
`application/json`만 허용한다. decoded entity 자체도 strict UTF-8이어야 하며
UTF-16/UTF-32와 malformed UTF-8은 receipt 생성 전에 거부한다. 이 검증은 valid
UTF-8 BOM을 제거하거나 digest input을 변환하지 않는다. versioned reader는
duplicate key, scalar coercion, float-to-integer coercion, trailing token도 거부한다.
gzip/x-gzip/deflate의 원본 `Content-Length`는 압축된 표현의 길이이므로 decoded
downstream header view에서는 제거하고, receipt용 `Content-Encoding`은 유지한다.
decoded stream cap과 완독 후 기록한 decoded byte length만 decoded 크기 사실로
사용한다.

receipt에는 source URI, 완독 뒤 UTC microsecond precision으로 기록한
`capturedAt`, HTTP status, media type, transport encoding, optional `ETag` 및
`Last-Modified`, parser version, decoded byte length와 lowercase SHA-256만
허용된 응답 metadata로 보존한다. 그 밖의 response header는 버리며 request
header, `SEC_CONTACT_EMAIL`, 전체 `User-Agent`는 보존하지 않는다.

catalog-only read path의 body retention은
`RECEIPT_ONLY_BODY_NOT_RETAINED`다. decoded body는 bounded response를 hash하고
parse하는 동안만 메모리에 있고 receipt에는 남지 않는다. digest는 동일 bytes
확인용 local identifier일 뿐 SEC 서명이나 SEC 발신 인증이 아니다. ADR-037
자체에는 durable raw body, replay, persistence, Flyway/DB, scheduler, controller,
public API/UI publication이 없다. 이 foundation에는 새 API key, 계정, 유료 플랜
또는 plugin이 필요 없다.

### Historical segment descriptor catalog

같은 root receipt와 V2 parser가 required `filings.files` array를 읽는다. 각
원소는 non-null object여야 하며 `name`, positive integer `filingCount`, exact
`YYYY-MM-DD` `filingFrom`/`filingTo`가 필요하다. `filingTo`는 `filingFrom`보다
앞설 수 없다. 파일명은 exact catalog CIK에 묶인
`CIK##########-submissions-NNN.json` 형태이고 `NNN=000`, slash, percent
encoding, URI suffix를 허용하지 않는다. duplicate filename이나 malformed
descriptor 하나라도 있으면 recent rows만 살리지 않고 root response 전체가 fail
closed한다.

canonical `HistoricalFilingSegmentDescriptor`는 `fileName`,
`advertisedFilingCount`, inclusive `advertisedFilingFrom`/
`advertisedFilingTo`만 SEC order로 보존한다. `FilingCatalog.recentFilings`와
`historicalSegments`는 분리되며 status는 정확히
`RECENT_ONLY_NO_SEGMENTS_ADVERTISED` 또는
`RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED`다. 빈 `files`도 complete history를
뜻하지 않는다. advertised historical ranges끼리 또는 recent filing date와
겹치는지는 flag만 하며 merge, dedupe, count 합산, complete 판정을 하지 않는다.

root receipt는 descriptor가 알려진 root `capturedAt`과 root bytes만 묶는다.
advertised dates는 event/available/capture time이 아니며 knowledge를 backdate하지
않는다. referenced segment body, 존재, actual row count/range, ETag, digest는 아직
관찰하지 않았다. segment GET/parse/union, durable raw body/replay, persistence/DB,
scheduler, controller, public API/UI는 없다. 새 API key나 계정도 필요 없고 live
root request에는 기존 `SEC_CONTACT_EMAIL`만 사용한다.

### Append-only root capture persistence

ADR-039의 persistence path는 provider가 방금 hash와 parse에 사용한 exact decoded
bytes를 `FilingCatalogCapture`에 defensive copy로 붙인다. transaction 전 receipt
state는 `DECODED_BODY_ATTACHED_PENDING_PERSISTENCE`이고, repository가 exact replay
검증 후 성공적으로 적재한 row와 read-back aggregate만
`DURABLE_DECODED_BODY_RETAINED`를 사용한다. catalog-only path의
`RECEIPT_ONLY_BODY_NOT_RETAINED` 의미는 바뀌지 않는다.

Flyway V6는 exact bytes를 SHA-256 content address의 PostgreSQL `BYTEA`로 저장하고,
root receipt/catalog row와 provider-order ordinal을 가진 recent filing 및 historical
descriptor child row를 분리해 저장한다. 동일 bytes를 나중에 다시 관찰하면 body
row는 공유하지만 다른 `capturedAt`의 root observation은 새로 append한다. body
digest 형식과 body FK identity, length와 8 MiB 상한, root status/count, accession,
CIK-bound descriptor, child point-in-time 관계는 DB constraint로도 검증한다.
실제 `BYTEA`의 SHA-256 재계산과 exact-byte 비교는 append/read의 deterministic
Java 검증이 담당하며 새 PostgreSQL crypto extension을 요구하지 않는다.

versioned `captureId`는 provider, product, CIK, source URI, `capturedAt`, body
digest와 decoded length를 묶는다. 완전히 같은 durable aggregate replay는
`IDENTICAL_REPLAY`이고, 같은 natural capture identity나 `captureId`에 다른
bytes/projection이 오면 fail closed한다. body, root, recent children, descriptor
children은 한 transaction으로 commit되며 child insert가 실패하면 새 body와 root도
rollback된다. PostgreSQL concurrent identical append는 conflict row를 다시 읽어
한 번만 insert한다. repository에는 update/delete method가 없지만, 이는 DB
관리자까지 차단하는 WORM 보장은 아니다.

repository read는 stored child count와 contiguous order, 재생성한 `captureId`, body
digest/length를 확인한 뒤 exact retained bytes를
`SEC_SUBMISSIONS_CATALOG_V2`로 다시 parse하고 전체 catalog equality를 요구한다.
point-in-time lookup은 exact provider/product/CIK/parser version에 대해
`capturedAt <= evaluationAsOf`인 최신 capture만 선택한다. future capture와 다른
parser projection은 보이지 않는다.

decoded body는 private server-side evidence이며 현재 TTL, purge job, delete API,
controller, web 노출이 없다. content addressing은 중복 bytes만 줄이고 capture를
삭제하지 않는다. disposal, backup expiry, encryption, object storage, public
redistribution은 별도 결정이 필요하다.

새 SEC API key, 계정, 유료 플랜, OAuth 또는 plugin은 필요 없다. live capture는
기존 `SEC_CONTACT_EMAIL`만 사용한다. DB는 기존 `POSTGRES_HOST`, `POSTGRES_PORT`,
`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` 설정을 사용한다. 로컬 값은 루트
`.env`에만 두고, 배포 값은 deployment secret store에서 주입해야 하며 채팅이나
Git에 넣지 않는다. persistence bean은 SEC provider가 명시적으로 enabled일 때만
one-shot service로 연결되지만 scheduler/controller가 없어 자동 실행되지 않는다.

### Controlled historical segment capture persistence

ADR-040의 one-shot service는 caller가 지정한 exact durable root `captureId`와
nonnegative descriptor ordinal만 받는다. PostgreSQL에서 ADR-039 root를 다시 읽고
해당 provider-order descriptor를 선택한 뒤
`https://data.sec.gov/submissions/{capturedFileName}`을 내부에서 구성해 최대 한 번
GET한다. caller는 CIK, filename, URI, host, query 또는 fragment를 넣을 수 없다.
invalid input에는 provider request가 없고 descriptor loop, automatic retry,
conditional request, alternate source 또는 fallback도 없다.

segment는 root와 별도인 product/parser
`edgar-submissions-historical-segment-api` /
`SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1`을 사용한다. WSR V1 parser는 현재 관찰한
top-level 14개 parallel array가 모두 존재하고 cardinality가 같으며, 각 값이 strict
type/date/timestamp/accession/path contract를 만족하는지 fail closed로 검증한다. 이
wire shape와 filename/URL 규칙은 SEC narrative documentation의 보장이 아니라
versioned local contract다.

historical row는 segment 전용 `HistoricalFilingRecord`로 보존한다. 오래된 Apple
segment에서 실제 관찰된 empty/null `primaryDocument`는 nullable
`primaryDocumentUri`로 남기고 빈 URI, 합성 파일명 또는 root 값을 만들지 않는다.
nonempty document path에는 기존 SEC Archives/catalog CIK 검증을 그대로 적용한다.
root recent row의 `FilingRecord` non-null contract는 변경하지 않는다.

accepted segment는 독립 receipt와 hash/parse에 사용한 exact decoded bytes를
`DECODED_BODY_ATTACHED_PENDING_PERSISTENCE`로 소유한다. Flyway V7은 exact root
descriptor tuple을 foreign key로 고정하고 content-addressed body store를 재사용해
segment receipt, provider-order rows, observed count와 actual filing-date extrema를 한
transaction으로 append한다. verified round-trip 이후에만
`DURABLE_DECODED_BODY_RETAINED`가 된다. exact replay는 idempotent이고 같은 bytes의
later observation은 새 capture를 만들면서 body만 공유한다. conflicting identity,
partial child insert 또는 replay disagreement는 전체 transaction을 rollback한다.
repository에는 segment update/delete 경로가 없다. 이는 application append-only
계약이며, 별도 SQL 권한을 가진 DB administrator까지 막는 cryptographic WORM
보장은 아니다. database role/audit와 WORM storage는 별도 운영 결정이다.

`MATCHES_ADVERTISED`는 observed count가 advertised count와 같고 모든 observed
`filingDate`가 captured inclusive advertised range 안에 있다는 뜻뿐이다. advertised
endpoints가 observed minimum/maximum과 같을 필요는 없다. count와 range escape의
네 상태를 그대로 저장하며 structurally valid mismatch evidence를 버리거나 광고값에
맞춰 고치지 않는다. empty segment는 count 0, null extrema,
`COUNT_MISMATCH`로 남는다.

descriptor는 root `capturedAt`에 알 수 있지만 segment rows는 later segment
`capturedAt` 전에는 알 수 없다. PIT read는 exact root capture, descriptor ordinal,
parser와 `capturedAt <= evaluationAsOf`만 사용한다. root와 segment는 SEC가 제공한
atomic snapshot이 아니며 recent/history union, cross-segment dedupe, correction/removal
reconciliation 또는 complete-history claim을 만들지 않는다.

새 API key, 계정, 유료 플랜, OAuth, plugin 또는 environment variable은 필요 없다.
live GET은 기존 `SEC_CONTACT_EMAIL`, persistence는 기존 PostgreSQL connection
설정을 재사용한다. scheduler, poller, startup collector, CLI, controller, public API,
browser/UI publication은 이 gate에 없다. 다음 SEC gate는 immutable root에 상대적인
ordered collection manifest와 cross-segment accession reconciliation이며,
complete-history claim은 별도 evidence/correction policy 전까지 금지한다.

### Manual SEC live smoke

수동 점검에는 새 API key, 계정, 유료 플랜이 필요 없다. 루트 `.env`에 이미 둔
실제 모니터링 가능한 `SEC_CONTACT_EMAIL`만 사용하며, 값을 채팅·명령 출력·Git에
노출하지 않는다. 점검은 `https://data.sec.gov`에 Apple CIK `0000320193` root를
정확히 한 번 요청하고, 그 응답에서 capture한 첫 descriptor의 segment를 정확히 한
번 요청한다. 총 두 request이며 body, 연락처, 전체 User-Agent 또는 arbitrary
header를 저장하거나 로그하지 않는다. 이 shape 점검은 persistence나 complete-history
evidence가 아니다.

저장소 루트의 PowerShell에서 다음처럼 두 개의 opt-in gate를 모두 명시한다.

```powershell
Set-Location apps/api
$env:SEC_LIVE_SMOKE = "true"
$secSmokeExit = 0
try {
    & .\mvnw.cmd -B -ntp -Psec-live-smoke verify
    $secSmokeExit = $LASTEXITCODE
} finally {
    Remove-Item Env:SEC_LIVE_SMOKE -ErrorAction SilentlyContinue
}
if ($secSmokeExit -ne 0) {
    throw "SEC live smoke failed with Maven exit code $secSmokeExit"
}
```

`sec-live-smoke` profile과 `SEC_LIVE_SMOKE=true` 중 하나라도 빠지면 live
request를 시작하지 않는다. profile이 `local` Spring profile을 사용해 루트
`../../.env`의 `SEC_CONTACT_EMAIL`을 서버 설정으로 읽는다. smoke 전용 test가
provider 활성화와 exact official origin을 강제하므로 `.env`에서
`SEC_PROVIDER_ENABLED`를 `true`로 바꿀 필요는 없다. 연락처가 없거나 유효하지
않으면 네트워크 요청 전에 실패하므로 루트 `.env`에만 값을 추가한 뒤 다시
실행한다.

일반 `test`/`verify`, 기본 Maven profile, CI는 이 점검을 실행하지 않으며 외부
SEC 네트워크를 사용하지 않는다. 성공한 수동 점검도 advertised count equality,
advertised endpoint equality, segment immutability, 완전한 filing history,
스케줄러, 다중 replica, 운영 DB 적재 자동화 또는 API/UI 공개를 승인하지 않는다.

## Test

```shell
./mvnw test
```

일반 테스트는 인메모리 데이터베이스를 사용한다. Docker가 사용 가능하면 Testcontainers 테스트가 PostgreSQL 17에서 Flyway baseline도 검증한다.

## Analyst call fixtures

Maven은 루트 `fixtures/v1`의 master data, analyst calls, analyst call revisions, market snapshots, scoring methodologies, call outcomes, call contexts를 `fixtures/v1` classpath resource로 패키징한다. Fixture adapter가 provider DTO를 읽어 canonical model로 변환하고, `(provider, provider_event_id)` 기준으로 원본 call과 revision을 멱등 적재한다.

Correction과 cancellation은 `analyst_call_revisions`에 순서대로 append하며 원본 `analyst_calls` row를 갱신하지 않는다. 각 revision은 최신 revision만 supersede할 수 있고 cancellation은 terminal event다. Revision과 원 call은 공용 provider-event identity registry를 원자적으로 claim하므로 동시 적재에서도 같은 identity를 다른 event kind로 저장할 수 없다. Read-only lineage는 `GET /v1/calls/{id}/revisions`에서 sequence 오름차순으로 제공하며 revision mutation HTTP API는 없다.

동시 provider-event claim 보장은 운영 저장소인 PostgreSQL을 기준으로 한다. H2는 단일 스레드 repository/context 테스트용 compatibility profile이며 운영 runtime으로 지원하지 않는다.

Market snapshot은 Java record와 insert-only repository로 노출한다. 데이터베이스는 call당 snapshot 하나와 `immutable = true`를 강제하며 애플리케이션에는 snapshot 수정·삭제 메서드나 HTTP API가 없다. PostgreSQL 전용 trigger는 H2와 동일한 migration을 유지하기 위해 이 단계에서 추가하지 않는다.

## Call outcome audit

P1의 scoring methodology와 call outcome은 계산 결과를 만들기 위한 모델·감사 경계다. DEMO outcome의 금융 지표와 결과 boolean은 의도적으로 `null`이며, 수익률·alpha·target·MFE/MAE 계산과 scheduler는 P3에서 golden test와 함께 구현한다.

Outcome은 methodology version/hash, input fingerprint, correction basis, cancellation evidence, snapshot, event/processing/capture time을 보존하며 append-only sequence로 적재한다. PostgreSQL writer는 methodology row lock과 `ON CONFLICT` 후 canonical reread를 사용해 replay와 경합을 결정적으로 처리한다. 저장 숫자는 `NUMERIC(38,12)`, 영속 timestamp는 UTC microsecond 정밀도이고 자동 반올림은 허용하지 않는다.

Read-only history는 `GET /v1/calls/{id}/outcomes`에서 제공한다. 알려진 call에 outcome이 없으면 `[]`, 알 수 없는 call은 기존 closed 404 Problem을 반환한다. Outcome을 생성하거나 수정·삭제하는 HTTP endpoint는 없다.

애플리케이션 persistence port도 insert-if-absent와 read만 노출한다. Privileged direct SQL은 기존 revision/snapshot과 같은 관리 trust boundary이며, 외부 writer에 DB 접근을 허용하기 전에는 insert/select-only role 또는 PostgreSQL update/delete guard가 필요하다. H2는 단일 프로세스 compatibility test profile이고 운영 동시성 보장은 PostgreSQL 17을 기준으로 한다.

## Point-in-time call context

P1 context archive는 macro observation을 standalone vintage evidence로 먼저 저장한 뒤, call event 시점에 release·processing·capture·inclusive vintage gate를 통과한 고정 6개 series만 ordinal link로 immutable macro snapshot에 묶는다. 따라서 fixture의 2026-08-15 CPI revision은 archive에는 남지만 2026-08-10 call snapshot과 응답에는 포함되지 않으며, 제공되지 않은 PPI 값은 `null`을 유지한다. Event context도 call의 event time과 정확히 결합하고 event 시점까지 확보된 source evidence만 허용한다.

`GET /v1/calls/{id}/context`는 기존 call list/detail shape를 바꾸지 않는 read-only endpoint다. `demo-call-001`에는 embedded macro snapshot과 event context를 반환하고, 명시적으로 known-empty인 `demo-call-002`와 `demo-call-003`에는 두 required key를 모두 `null`로 반환한다. 알 수 없는 call은 closed 404 Problem이며 context 생성·수정·삭제 endpoint는 없다.

Context persistence port는 source evidence, standalone observations, call당 최대 한 snapshot/event context를 insert-if-absent와 canonical reread로 적재한다. PostgreSQL은 exact replay와 동일 call 경합을 `ON CONFLICT` 및 reread로 판정하고, UTC event date·release/vintage·series ordinal·128자 source ID 경계를 raw constraint로 검증한다. H2는 migration/domain/repository compatibility와 단일 프로세스 검증용이며 운영 동시성 보장은 PostgreSQL 17 Testcontainers 결과를 기준으로 한다. Privileged direct SQL은 관리 trust boundary이므로 외부 writer를 허용하기 전 insert/select-only role 또는 update/delete guard가 필요하다.
