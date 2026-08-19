# Wall Street Receipts API

Java 21과 Spring Boot 3.5.16 기반의 초기 API 애플리케이션이다. 기본 provider는 fixture이며 외부 vendor key 없이 실행된다.

## Run

```shell
./mvnw spring-boot:run
```

기본 연결은 `localhost:5432/wsr`의 PostgreSQL이다. `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`로 변경할 수 있다.

상태 확인은 `GET /actuator/health`를 사용한다.

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
