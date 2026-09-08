# ADR-068: Durable CPI collection attempt evidence

Date: 2026-09-08

## Scope and prerequisites

PR #15 was user-merged at `7f2d5422315bfaf292fac972a90b5afcb43d5252`.
Its head `17d4c9c` passed hosted CI #42 (`34200020088`). The user requested
the next implementation. Add durable collector attempt records before a
protected operator view or notification delivery. No additional key, account,
real provider request, server details or permanent worker is needed to build
and test this slice. Development uses disposable Docker PostgreSQL databases;
the existing CPI preview/database and root `.env` are not migrated or edited.

## What the records establish

V11 adds `bls_cpi_collection_attempts` and `bls_cpi_collection_results`.
An attempt is a local collector processing event, **not** a BLS publication
event, point-in-time macro snapshot, heartbeat or CPI freshness measurement.
Store UTC microsecond instants; future operator presentation must format KST.
The existing daily 23:00 Asia/Seoul schedule and log protocol do not change.

Each new invocation has a UUID, explicit MANUAL/SCHEDULED origin, start time
and durable gate permission. Permission means the invocation was allowed to
call the provider; it does not prove that HTTP started or reached BLS. The
request year range remains derived from the start's UTC year (year-3 to year).
No terminal record means **result unknown**, including crashes, an unavailable
DB, ambiguous commit acknowledgement or a call still executing. Do not label
such a row RUNNING or FAILED solely because it is old. If the initial DB write
fails, even a start record may be absent. This is not complete process history.

Terminal outcomes:

- SAVED: an exact replay-validated receipt and the result commit together.
  A foreign key binds capture ID and captured-at, and a capture can belong to
  at most one attempt result. This proves a persisted retrieval, not new data.
- SKIPPED: the durable gate denied permission; no HTTP or receipt. A missing
  gate row is an error, never a cooldown skip.
- FAILED: a fetch or parse stage failed, with only closed FETCH/PARSE codes.
  No raw exception, response body, key, arbitrary message or endpoint is stored
  in the ledger. The normal receipt table still stores successful raw evidence.
- RATE_LIMITED: the client's 429-derived lower bound was recorded and the
  shared gate was extended atomically. An already later gate is not shortened.
  The lower bound is not the next scheduled run, and no immediate retry occurs.

Completed-at is the collector's terminal processing marker, not an independent
DB commit timestamp. Validate chronology and reject sub-microsecond input rather
than silently rounding persisted evidence. A failed/uncertain save transaction
does not trigger a second FAILED write: that could contradict a committed SAVED.

## Persistence and boundary

`CpiCollectionJob` keeps the existing single bounded fetch and parser. Its manual
entry point delegates to the explicit MANUAL origin; the scheduler supplies
SCHEDULED. `CpiRepository` adds begin, non-success finish, atomic save and a
read by explicit attempt UUID. `CpiCollectionAttempt` validates closed result
shapes. No history/list endpoint or public operational data route is added.

`JdbcCpiRepository` uses short `TransactionTemplate` REQUIRES_NEW units against
the same data source, with a ten-second transaction statement deadline. This
does not promise to bound connection acquisition/commit or the entire command.
The repository-owned transaction does not span HTTP. Start/gate writes survive
caller rollback; a terminal failure cannot roll back that earlier rate gate.
Lock the singleton gate for admission and rate-limit completion. Concurrent
starts share the gate; an insert collision rolls its gate change back.

Starts/results are insert-only through this repository; no update, deletion,
catch-up, repair, expiration or retention operation is introduced. The result
primary key arbitrates concurrent completion and prevents a second terminal
record. This is **not DB-owner tamper protection**: no privilege separation or
immutable-row trigger is claimed. Existing DBA permissions remain unchanged.
Future deployment must provision and review operator/worker privileges.

V11 preserves V1-V10 bytes, original receipts and the current gate. Existing
captures receive no invented start/result/backfill. Downgrading after V11 is
not implemented; release schema inventory and affected upgrade assertions now
explicitly include V11. Real deployment still requires the release, backup,
schema inventory and host acceptance workflow.

The ADR-067 log inspector intentionally remains log-only UNVERIFIED; it does
not query these new tables or silently upgrade its evidence label. Existing
routes remain `/market/cpi` and `/v1/macro/cpi`. No web, Compose, credentials,
provider endpoint, notification, heartbeat or home-server activation changes.

## Verification

- Unit tests cover typed shapes, origin, precise clock, start-before-fetch,
  saved-after-commit-return, skip/no HTTP, closed failure codes, no automatic
  retries and uncertain persistence without fabricated terminal records.
- Testcontainers checks V10 upgrade with preserved receipt/gate and no backfill,
  concurrent admission, caller rollback, duplicate starts/results, transactional
  receipt rollback, exact capture FK, invalid shapes/times, missing gate,
  incomplete evidence across repository restart and later unrelated success.
- The explicit ADR-066 disposable Docker rehearsal now checks actual packaged
  manual command outcomes in V11, including 429, malformed JSON and cooldown.
  All provider traffic targets its isolated DEMO HTTPS fixture. No scheduled
  attempt is invented at startup/restart; no real BLS request is made.
- CPI custody expands to exactly 47 paths (six baseline replacements, 41
  additions). Four new runtime/migration/test files are pinned, and twelve
  changed paths admit only exact merged ADR-067 predecessors while working
  bytes must match this review. Mutation tests reject stale working bytes and
  forged predecessors. Historical baseline, 84 scripts and workflow unchanged.

Actual test counts, Docker acceptance and handoff are in IMPLEMENTATION_LOG.md.
Next: review/PR/hosted CI, then a separately scoped protected read-only operator
query/presentation for these records. Heartbeat semantics, retention policy,
alert channel and actual host supervision remain undecided, not completed.
Ask for user-selected delivery credentials and real host details before those
steps require them; do not use this development PC as the home server.

## References

- [Spring programmatic transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)
- [PostgreSQL 17 constraints](https://www.postgresql.org/docs/17/ddl-constraints.html)
