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

ADR-041 establishes zero-network root-relative collection manifests and
occurrence-preserving accession reconciliation.

ADR-042 establishes an operator-controlled bounded collection-attempt ledger
and exact-evidence execution boundary.

ADR-043 establishes a default-disabled, local-only single-operator HTTP
boundary for executing and inspecting those attempts without live SEC traffic.

ADR-052 establishes an anonymous, exact-ID, point-in-time manifest audit read
without live SEC traffic.

SEC submissions metadata adapter는 기본 비활성화다. 로컬에서 명시적으로
활성화하려면 루트 `.env`에 다음 서버 전용 변수가 있어야 한다.

```dotenv
SEC_PROVIDER_ENABLED=true
SEC_BASE_URL=https://data.sec.gov
SEC_CONTACT_EMAIL=operations-contact@example.com
```

SEC는 API key 대신 선언된 연락처 User-Agent를 요구한다. 실제 연락처 값은
`.env.example`, 로그, HTTP 응답, Git에 넣지 않는다. 현재 adapter에는 one-shot
DB persistence/assembly services가 있지만 scheduler, command-line trigger,
startup collector 또는 public web consumer가 없다. ADR-043 local operator
controller는 별도 설정으로 기본 비활성화되므로 SEC provider 설정만으로 외부
요청이나 DB 적재가 발생하지 않는다.

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
browser/UI publication은 이 gate에 없다. ADR-041의 immutable-root-relative
collection과 reconciliation은 아래의 별도 zero-network gate다.

### Root-relative filing-history collection manifest

ADR-041 assembly는 exact durable root `captureId`와 caller가 명시한 captured
descriptor ordinal/exact durable historical-segment `captureId` pair만 읽는다. CIK,
filename, descriptor 또는 현재 시각으로 latest root/segment를 자동 선택하지 않고
SEC를 포함한 외부 네트워크를 전혀 호출하지 않는다. 각 segment는 exact root와
captured descriptor ordinal에 묶여야 하며, local order는 root recent provider
order 다음에 selected descriptor ordinal 순서다. SEC 공식 문서는 이를
`additional JSON files`라고 부른다. `Historical Segment`는 WSR local 용어다.

selected root/segment의 모든 row는 source capture와 row ordinal을 가진 occurrence로
남는다. 같은 exact accession의 반복은 versioned canonical projection으로
`SINGLE_SOURCE_OCCURRENCE`, `MULTIPLE_OCCURRENCES_EXACT_AGREEMENT`,
`MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT`만 계산하고, conflict에서 root, segment,
latest 또는 majority winner를 만들지 않는다. descriptor는 `NOT_SELECTED` 또는
`SELECTED_EXACT_CAPTURE`로 남고 coverage는 `NO_ADVERTISED_DESCRIPTORS`,
`PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED`,
`ALL_ADVERTISED_DESCRIPTORS_SELECTED` 중 하나다. 이는 explicit selected reference
범위뿐이며 source correction/removal, atomic SEC snapshot 또는 complete filing
history를 보증하지 않는다.

각 source `capturedAt`은 그대로 보존한다. `evidenceAvailableAt`은 selected evidence의
가장 늦은 capture time이고 `assembledAt`은 injected-clock assembly time이며, 이 둘을
SEC-authored `asOf`로 표시하거나 filing date로 backdate하지 않는다.

이 서비스는 zero-network이므로 `SEC_CONTACT_EMAIL`을 읽지 않으며 새 API key,
account, paid plan, OAuth/EDGAR token, plugin, secret 또는 environment variable이
필요 없다. 기존 PostgreSQL connection만 재사용한다. 활성화 설정만으로 실행되는
scheduler, startup collector, fetch-all loop, retry, CLI, browser consumer 또는 UI는
없다. ADR-052가 이후 추가한 exact audit controller/OpenAPI는 아래의 별도 read
boundary이며 capture나 assembly를 시작하지 않는다.

### Exact manifest audit API

ADR-052는 이미 저장된 manifest 하나만 다음 네 anonymous GET route로 읽는다.

```text
GET /v1/sec/filing-history/manifests/{manifestId}?evaluationAsOf=...
GET /v1/sec/filing-history/manifests/{manifestId}/descriptors?evaluationAsOf=...
GET /v1/sec/filing-history/manifests/{manifestId}/accessions?evaluationAsOf=...
GET /v1/sec/filing-history/manifests/{manifestId}/occurrences?evaluationAsOf=...
```

`manifestId`는 exact lowercase 64-hex이고 `evaluationAsOf`는 최대 microsecond
precision의 명시적 UTC `Z` instant다. summary에는 다른 query parameter를 허용하지
않고 child route에는 `page`와 `size`만 추가로 허용한다. 기본값은 `0`/`25`, 최대
size는 `100`이며 descriptor/accession/occurrence 순서는 각각
`descriptorOrdinal`/`groupOrdinal`/`occurrenceOrdinal ASC`로 고정된다. unknown,
duplicate, blank, signed, leading-zero parameter는 closed 400이다.

조회는 오직
`findByManifestIdAtOrBefore(manifestId, evaluationAsOf)`를 사용한다. 없는 exact ID와
cutoff 뒤에 assembled된 manifest는 같은 sanitized 404이고 latest, CIK, ticker,
root, provider fallback이 없다. repository가 root와 selected capture, counts,
hashes, descriptor, group, occurrence를 모두 재구성·검증한 뒤 응답 page를 자른다.
따라서 HTTP item 수는 제한되지만 whole-manifest replay 비용은 별도 후속 최적화
대상이다. integrity/reconstruction failure는 sanitized 500이며 empty page나 partial
manifest로 바뀌지 않는다.

응답은 raw body/header, 연락처, User-Agent, secret, operator attempt, DB detail을
노출하지 않는다. descriptor 선택과 모든 source occurrence, exact agreement와
canonical conflict를 그대로 보존하고 winner/current/latest/complete history를
만들지 않는다. GET/HEAD success와 handled 400/404/405/500에는
`X-Request-Id`와 `Cache-Control: no-store`가 붙는다.

이 read API의 로컬 구현/검증에는 API key, SEC account, domain, home server,
operator token, `SEC_CONTACT_EMAIL` 또는 live provider가 필요 없다. 기존
PostgreSQL evidence만 사용하고 테스트에서는 `SEC_PROVIDER_ENABLED=false`를
유지한다. 현재 production Caddy는 Spring을 public origin으로 직접 proxy하지
않는다. ADR-053의 same-origin web consumer가 Next server에서 이 private API를
읽으며 browser는 Spring origin을 직접 호출하지 않는다.

### Exact manifest audit web consumer

ADR-053은 Spring route를 늘리지 않고 다음 Korean-default Next route 하나를
추가한다.

```text
/research/sec/filing-history
```

parameter가 없으면 exact evidence locator만 렌더하고 API를 호출하지 않는다. 완전한
query는 `manifestId`와 `evaluationAsOf`를 요구하고 `view`로 resource를 선택한다.
`manifestId`는 lowercase 64-hex, cutoff은 실제 calendar의 최대 microsecond UTC `Z`
instant이고 `view`는 `summary`, `descriptors`, `accessions`, `occurrences` 중 하나다.
`view` 생략은 `summary`로만 해석한다. summary에는 `page`/`size`가 금지되고 child view에는
canonical unsigned `page`와 1~100 `size`만 허용한다(기본 `0`/`25`). unknown,
duplicate, blank, whitespace-normalized, signed, leading-zero 값은 provider 호출 전에
닫힌 invalid state가 된다.

`SEC_MANIFEST_AUDIT_PROVIDER=api`는 server-only `API_BASE_URL`에 현재 선택된
ADR-052 resource 하나만 no-store GET한다. browser는 Next origin만 사용한다. network,
status, media type, JSON, exact field set, identity, timestamp, count, ordinal 또는 page
검증 실패는 전체 route error이며 fixture, empty page, 다른 manifest로 fallback하지
않는다. API 404는 absent와 future-invisible을 구분하지 않는 동일한 web not-found다.

`SEC_MANIFEST_AUDIT_PROVIDER=fixture`는 별도의 synthetic DEMO mode다. committed JSON은
Java domain assembly와 `SecFilingHistoryManifestAuditResponses`를 거쳐 생성한 tree와
exact equality를 유지하도록 parity test가 잠근다. fixture와 API는 동일한 closed
TypeScript adapter를 사용한다. fixture surface는 모두 synthetic DEMO라고 표시하고,
API mode는 ADR-052가 제공하지 않는 `dataMode`, LIVE/REALTIME, issuer/ticker를 만들지
않는다. descriptor advertised range, selection, exact agreement/conflict, every source
occurrence, null, raw instant와 disclosure를 보존하고 winner/current/latest/complete
history를 만들지 않는다.

web runtime의 비밀이 아닌 설정은 다음과 같다.

```dotenv
SEC_MANIFEST_AUDIT_PROVIDER=fixture
API_BASE_URL=http://localhost:8080
SITE_ORIGIN=http://localhost:3000
```

`API_BASE_URL`은 `api` mode에서만 필요하고 browser에 노출되지 않는다.
`SITE_ORIGIN`은 absolute canonical/social metadata origin이며 credential, path, query,
fragment를 허용하지 않는다. production에서는 실제 HTTPS domain이어야 한다.
이 phase는 API key, SEC account, domain 또는 `SEC_CONTACT_EMAIL`을 요구하지 않는다.
API-backed success에는 이미 PostgreSQL에 저장된 exact manifest와 assembly 이후 cutoff가
필요하다. monitored `SEC_CONTACT_EMAIL`은 나중에 별도 승인된 live collection을
시작할 때만 untracked server secret environment에 둔다.

### Disposable SEC manifest API-mode full-stack acceptance

ADR-055는 ADR-045의 기존 명령을 확장해 위 API-backed success를 외부 SEC 요청 없이
검증한다.

```powershell
pwsh -NoProfile -File ./scripts/verify-local-full-stack.ps1
```

고유 loopback PostgreSQL 17 project가 준비된 뒤
`SecManifestAuditAcceptanceSeedHarness` 하나를 exact `-Dtest`로 지명하고
`-Dwsr.sec-manifest-acceptance-seed=true`를 함께 준다. 이 클래스명은 기본 Surefire
pattern과 일부러 일치하지 않으므로 일반 `test`, `verify`, API package/startup에서는
실행되지 않는다. raw SQL, migration fixture, startup importer, controller 또는 operator
route를 쓰지 않고 production
`FilingCatalogCaptureRepository`,
`HistoricalFilingSegmentCaptureRepository`,
`PersistFilingHistoryCollectionManifestService`를 통해 root 1개, segment capture 2개,
manifest 1개를 적재한다. ADR-053 JSON parity와 같은 Java synthetic fixture를
공유하고, 적재 뒤에는 production audit query service로 exact summary와 pre-assembly
not-found를 다시 읽는다.

context 생성 전 guard와 실제 JDBC metadata 확인은 모두 다음 target만 허용한다.

```text
jdbc:postgresql://127.0.0.1:<1024..65535>/wsr_full_stack_acceptance
user = wsr_full_stack_acceptance
```

datasource/Flyway URL·user·per-run 32-hex password가 서로 일치해야 하고,
`SEC_PROVIDER_ENABLED=false`, `OPERATOR_API_ENABLED=false`, 빈 contact email,
`SEC_BASE_URL=http://127.0.0.1:1`, 빈 root/segment/manifest repository여야 한다.
하나라도 다르면 write 전에 seed를 거부한다. injected fixed clock이 만든 synthetic
identity는 다음과 같다.

```text
manifest = cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd
selection = eadb0c3bf6efb9b3323be1342d0b17e63631b706f088b23fa78e784e1b547acd
root = c9bfc935b27e059397531a4dda1a1a0222e98528c33e85b886c91ca6b74f2fa8
assembledAt = 2026-08-25T03:30:00.123456Z
```

Next build/runtime/browser child는 `SEC_MANIFEST_AUDIT_PROVIDER=api`를 명시한다.
acceptance 전용 server setting이 위 exact manifest ID만 synthetic DEMO로 고정해,
실제 API transport를 통과해도 화면의 `DEMO` badge와 실제 SEC 자료가 아니라는
disclosure를 유지한다. 다른 API manifest에는 mode를 추론하지 않고 ADR-052 response에
`dataMode`를 추가하지도 않는다. browser는 private Spring origin을 호출하지 않는다.

하나의 run은 13 production route, call list/revision/outcome과 SEC 2개를 합친
Chromium 5/5, exact Tomcat line 18개(기존 call 13 + SEC 200 네 개 + cutoff 직전
404 한 개)를 요구한다. ADR-054 KST civil day는 정확히
`2026-08-10T15:00:00.000Z` 이상,
`2026-08-11T15:00:00.000Z` 미만의 API bound여야 한다. database는 다음 전체 tuple과
byte-for-byte 같아야 한다.

```text
3|2|4|3|1|2|2|2|4|1|2|4|6|0|0|0|0|cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd|eadb0c3bf6efb9b3323be1342d0b17e63631b706f088b23fa78e784e1b547acd|c9bfc935b27e059397531a4dda1a1a0222e98528c33e85b886c91ca6b74f2fa8|2026-08-25T03:30:00.123456Z
```

앞의 값은 순서대로 call/revision/outcome `3|2|4`, decoded body/root/recent/
descriptor/segment capture/segment row/manifest/selected descriptor/accession group/
occurrence `3|1|2|2|2|4|1|2|4|6`, operator attempt/action/dispatch/outcome
`0|0|0|0`, 뒤의 네 값은 manifest/selection/root/assembly identity다.

성공·실패 모두 harness가 소유한 process, Compose project/volume, temp build,
secret-free web mirror, report와 log만 검증 후 정리한다. root `.env`, 기본 database,
일반 `.next`, `next-env.d.ts`, `tsconfig.json`은 건드리지 않는다. 도메인, API key,
SEC account, paid plan, home-server access, operator/user token, OAuth 또는 monitored
email은 필요 없다. live SEC와 operator boundary는 계속 꺼져 있고 SEC network call은
허용되지 않는다.

repository CI는 ADR-055 source/marker/expected identity와 historical projection을
parse하고 guard하지만 Docker/Chromium harness 자체는 실행하지 않는다. 2026-08-31
수동 실행의 complete PASS는 `IMPLEMENTATION_LOG.md`에 기록되어 있으며, 다음 release
candidate는 그 결과를 상속하지 않고 같은 명령을 다시 실행해야 한다.

### Operator-controlled collection attempt

ADR-042는 unconditional internal application service만 추가한다. controller, CLI,
scheduler, startup hook, authenticated/public route, OpenAPI operation, browser/UI
consumer는 없다. 따라서 bean 생성이나 설정만으로 SEC 호출 또는 attempt 실행이
시작되지 않는다.

`CAPTURE_ROOT`는 root capture를 최대 한 번 provider에 위임한다.
`COLLECT_EXACT_ROOT`는 caller가 지정한 exact durable root에 zero-or-many
`SELECT_EXACT`와 at-most-one `CAPTURE_NOW` descriptor action만 결합한다.
`SELECT_EXACT`는 exact durable segment를 선택하는 zero-network action이고,
`CAPTURE_NOW`만 captured root descriptor에서 URI를 내부 도출해 segment capture를
시도한다. 한 attempt의 provider invocation 상한은 명령 종류와 무관하게 1이며,
retry, latest lookup, fallback, fetch-all loop가 없다. accepted provider response
이후 새 capture/manifest가 `INSERTED`되면 success terminal과 local atomic
committer 경계에서 함께 적재된다. `IDENTICAL_REPLAY` terminal은 이미 durable한
exact artifact를 참조하며 이번 attempt가 다시 insert했다고 주장하지 않는다.

`operatorRequestId`는 canonical nonzero lowercase UUID다. 같은 UUID와 같은 canonical
command의 replay는 기존 attempt를 그대로 반환하고 provider와 mutex를 포함한 외부
interaction을 0회로 유지한다. 같은 UUID에 변경된 command는 conflict로 닫힌다.
initial claim 시 exact root 또는 selected segment FK가 없으면 sanitized rejection이며
ledger row를 만들지 않는다. 반대로 FK admission을 통과한 plan의 exact evidence가
이후 재구성·검증되지 않으면 `EXACT_EVIDENCE_VALIDATION_FAILED` terminal을 적재하고
provider는 호출하지 않는다.
action-dependent cross-row compatibility도 repository reconstruction 때 다시
검증하고 fail closed한다. 향후 external writer 또는 multi-service ledger를 허용하기
전에는 immutable action summary와 exact FK binding을 별도로 추가해야 한다.

durable dispatch는 provider port를 실행하도록 허가하고 handoff한 local boundary다.
HTTP가 시작됐거나 SEC에 도달했다는 증거가 아니다. dispatch가 있고 terminal이
없으면 `PROVIDER_DISPATCHED_INDETERMINATE`이며 자동 resume, retry, abandon하지 않는다.
single-JVM nonblocking mutex contention은 provider 전 `PROVIDER_GATE_CLOSED`로 닫힐 수
있다. 이 mutex는 기존 shared single-JVM SEC limiter와 결합한다: 8 req/s fixed spacing,
decoded body 8 MiB, connect timeout 5초, read timeout 10초, HTTP `429` 무자동재시도 및
process-local `Retry-After` cooldown. 여러 replica를 합친 global limit, scheduler,
retry owner 또는 distributed lock은 아니다.

구현과 `SELECT_EXACT`-only 실행에는 새 API key, account, token, paid plan, plugin,
secret이 필요 없다. 향후 명시적으로 승인한 provider-bound manual/live 실행에는
루트 `.env`의 다음 서버 전용 설정이 필요하다.

```dotenv
SEC_PROVIDER_ENABLED=true
SEC_BASE_URL=https://data.sec.gov
SEC_CONTACT_EMAIL=monitored-operations-contact@example.com
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=wsr
POSTGRES_USER=wsr
POSTGRES_PASSWORD=local-secret
```

연락처와 DB credential의 실제 값은 채팅, 로그, 문서, Git에 넣지 않는다. ADR-042
검증에서는 SEC live traffic을 발생시키지 않았고 일반 test/verify 및 CI도 계속
offline이다. ADR-043은 local-only authenticated trigger/status를 추가하지만 remote
deployment, browser login, durable actor audit, indeterminate recovery 또는
multi-replica global coordination은 승인하지 않는다.

### Local single-operator attempt API

ADR-043의 세 route는 기본적으로 등록되지 않는다.

```text
POST /internal/v1/sec/collection-attempts/root
POST /internal/v1/sec/collection-attempts/exact-root
GET  /internal/v1/sec/collection-attempts/{attemptId}
```

ADR-044의 권장 배포 전 점검은 저장소 루트에서 실행하는 한 명령이다.

```powershell
pwsh -NoProfile -File ./scripts/verify-local-operator-api.ps1
```

이 명령은 PowerShell 7, Java 21, 로컬 Docker daemon/Compose v2가 필요하며
macOS/Linux에서는 Maven wrapper를 실행할 표준 POSIX `sh`도 사용한다.
검증한 Java 21로 API를 전용 temp build directory에 package하고 고유 Compose
project의 PostgreSQL 17과 임의 loopback port의 실제 JAR을 띄운 뒤, health, `401`,
provider-disabled `200`, exact replay, GET, `409`, `422`, 그리고 database의
attempt 1개 / dispatch 0개 / outcome 1개를 검증한다.
매 실행마다 32-byte random token과 digest, database password를 memory에서 만들며
raw token을 출력하거나 파일에 쓰지 않는다. root `.env`, 기본 Compose project,
기존 `postgres-data` volume은 읽거나 변경하지 않고 성공·실패 모두 자신이 만든
process/project/volume/temp directory만 검증 후 정리한다. SEC provider는 false이고
datasource와 Flyway는 같은 disposable database로 강제하며 base URL도 닫힌
loopback origin으로 덮어쓴다. Spring config lookup은 packaged `classpath:/`로만
고정하고 caller의 `apps/api/application*`, `apps/api/config/`, 외부 logging
destination은 선택하지 않는다. inherited Spring/server/management,
datasource/Hikari, JNDI, direct-provider, logging namespace를 제거한 뒤 exact
acceptance allowlist만 주입한다. 도메인, API key, OAuth client,
`SEC_CONTACT_EMAIL`, 사용자가 제공할 token은 필요 없다. 최초 실행에서 cache가
없으면 Maven dependency 또는 PostgreSQL image의 일반 download가 발생할 수 있지만
SEC 요청은 허용되지 않는다. 원격 Docker context나 `DOCKER_HOST`는 daemon 접촉 전에
거부되고, 검증한 local endpoint는 이후 모든 Docker/Compose 호출에 고정된다.
ADR-044와 ADR-045는 원자적으로 만든 root
`/.wsr-local-acceptance.lock`을 전체 실행 동안 공유하므로 동시에 실행하면 두 번째
명령은 package나 Compose 전에 종료된다. 비정상 강제 종료 뒤 lock이 남으면 관련
process와 Docker resource를 먼저 확인한 다음 그 lock 파일만 제거해야 한다. 두
명령은 순서대로 실행하면 된다.

이미 package가 끝난 동일 source를 재점검할 때만 `-SkipPackage`를 사용할 수 있다.

```powershell
pwsh -NoProfile -File ./scripts/verify-local-operator-api.ps1 -SkipPackage
```

아래 절차는 장시간 띄운 local process를 직접 조사해야 할 때만 사용하는 수동
대안이다.

이 경계는 loopback에서 한 API JVM과 PostgreSQL로 HTTP 계약을 확인할 때만 쓴다.
도메인, DNS, Cloudflare, OAuth client, SEC API key 또는 `SEC_CONTACT_EMAIL`은 필요
없다. SEC network를 확실히 차단한 다음 루트 `.env`에서 다음 값만 설정한다.

```dotenv
OPERATOR_API_ENABLED=true
OPERATOR_API_TOKEN_SHA256=<lowercase SHA-256 digest>
SEC_PROVIDER_ENABLED=false
```

raw Bearer token은 정확히 32 random byte를 standard Base64로 인코딩한 44자
문자열(`=`로 끝남)이어야 하며 `.env`, Git, 채팅, URL, browser storage 또는 로그에
넣지 않는다. PowerShell terminal A에서 token을 만들고 raw
token은 그 shell memory에만 보관한다. 화면에 표시된 digest만 gitignored root
`.env`의 `OPERATOR_API_TOKEN_SHA256`에 복사한다.

```powershell
$operatorRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
$operatorBytes = [byte[]]::new(32)
$operatorRandom.GetBytes($operatorBytes)
$operatorRandom.Dispose()
$operatorToken = [Convert]::ToBase64String($operatorBytes)
$operatorSha = [Security.Cryptography.SHA256]::Create()
$operatorTokenBytes = [Text.Encoding]::UTF8.GetBytes($operatorToken)
$operatorDigestBytes = $operatorSha.ComputeHash($operatorTokenBytes)
$operatorSha.Dispose()
$operatorDigest = -join ($operatorDigestBytes | ForEach-Object { $_.ToString("x2") })
$operatorDigest
```

terminal B에서 기존 local profile 방식으로 API를 시작한다. operator API가
활성화되면 애플리케이션이 다른 server address 설정보다 우선해 embedded server
전체를 loopback에만 bind한다. 같은 컴퓨터에서 두 번째 API process를 띄우지
않는다. API가 시작되면 token을 보관한 terminal A에서 provider-disabled root
command와 exact replay를 확인할 수 있다.

```powershell
$operatorRequestId = [Guid]::NewGuid().ToString().ToLowerInvariant()
$operatorHeaders = @{ Authorization = "Bearer $operatorToken" }
$operatorBody = @{
    operatorRequestId = $operatorRequestId
    cik = "0000320193"
} | ConvertTo-Json

$attempt = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/internal/v1/sec/collection-attempts/root" `
    -Headers $operatorHeaders `
    -ContentType "application/json" `
    -Body $operatorBody
$attempt | ConvertTo-Json -Depth 8

$replay = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/internal/v1/sec/collection-attempts/root" `
    -Headers $operatorHeaders `
    -ContentType "application/json" `
    -Body $operatorBody

Invoke-RestMethod `
    -Method Get `
    -Uri ("http://localhost:8080/internal/v1/sec/collection-attempts/" + $attempt.attemptId) `
    -Headers $operatorHeaders
```

`SEC_PROVIDER_ENABLED=false`이므로 이 root command의 정상적인 local 결과는 SEC
성공이 아니라 ADR-042의 durable provider-gate failure다. 중요한 검증점은 HTTP가
SEC로 나가지 않고, 같은 UUID와 같은 body가 같은 `attemptId`로 수렴하며, GET이
그 immutable 상태를 그대로 재구성하는 것이다. POST와 GET의 `200`은 provider
성공을 뜻하지 않으므로 반드시 `lifecycleState`와 `terminalOutcome`을 확인한다.

작업 후에는 `.env`의 `OPERATOR_API_ENABLED=false`를 복원하고 terminal A에서
token을 담았던 배열을 지운 뒤 관련 변수를 제거한다.

```powershell
[Array]::Clear($operatorBytes, 0, $operatorBytes.Length)
[Array]::Clear($operatorTokenBytes, 0, $operatorTokenBytes.Length)
[Array]::Clear($operatorDigestBytes, 0, $operatorDigestBytes.Length)
Remove-Variable -Name operatorToken, operatorBytes, operatorTokenBytes, `
    operatorDigestBytes, operatorDigest, operatorHeaders -ErrorAction SilentlyContinue
```

remote host나 LAN에 이 static-token API를 노출하지 않는다. TLS, managed identity,
durable actor audit, private origin, secret store와 one-replica deployment가 별도
승인되기 전에는 배포에 사용할 수 없다.

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
