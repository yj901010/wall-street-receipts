# ADR-079: CPI read-wait deadline and bounded background work

Date: 2026-09-10 (KST)

## Context

PR #26 is merged into develop at `7fa51c29bd60cc975465ce31c805c915a6578884`.
PR CI #64 (`34454468870`) and merge CI #65 (`34456925180`) pass all four jobs.
ADR-078 verifies packaged PostgreSQL silence/recovery. Individual pool/SQL/socket
timeouts do not bound the complete reader invocation if a driver keeps making
partial progress, stalls outside a socket read, or ignores interruption.

## Decision

Only in an explicitly enabled CPI_READ_ONLY process, configure a primary
`CpiDeadlineReader` for the `CpiAttemptReader` port. It delegates to the existing
`CpiRepository`, leaving repository consumers (public reads/collection) untouched.
FULL/disabled/default modes do not create this bean or its executor. The existing
query service, its four-reader admission, evidence validation and injected business
Clock remain unchanged. No schema, provider, endpoint, environment variable or
dependency is added. Existing SQL/pool/JDBC timeouts are preserved.

The complete reader invocation (connection acquisition, driver operations and
result consumption) has a fixed four-second caller-wait budget. Start monotonic
elapsed-time accounting before submission, subtract submission time from the
remaining wait, and reject a completed result observed at or beyond the deadline.
Monotonic nanosecond subtraction tolerates counter wraparound and does not use
wall-clock/event time. A package-local constructor permits deterministic tests;
the deployed budget is not an externally configurable knob.

Use four platform workers and a `SynchronousQueue` with AbortPolicy: no buffered
queue, common pool, unbounded virtual-thread spawning, CallerRuns or automatic
query retry. A worker is available only after its actual task finishes, not when
the caller stops waiting. Thus even four reads that ignore interruption can only
occupy four workers; subsequent reads fail closed immediately. Workers do not
inherit request thread locals or receive HTTP objects/credentials. They are daemon
threads and are interrupted via shutdownNow on bean destruction without an
unbounded executor close/join. A stuck driver may retain its bounded worker and
connection until driver/DB recovery or process termination; do not claim otherwise.

Timeout, execution failure, saturation and caller interruption use the existing
sanitized Unavailable/503 response. Preserve the caller's interrupt flag. Attempt
best-effort cancellation on exit, never expose the worker exception or a late
result, and never treat cancellation as proof that the database operation ended.
Java's [Future cancellation contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html#cancel(boolean))
is cooperative; [ThreadPoolExecutor's bounded direct handoff](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
is the independent resource bound. Existing UI upstream waiting remains five seconds.

## Verified boundaries

- Unit tests cover success/failure, late results at the monotonic deadline including
  wraparound, interruption, request-thread-local isolation, close and exact worker
  retention while delegate reads ignore cancellation. Repeated overflow never
  executes later; recovery happens after the real workers exit.
- Configuration tests prove mode isolation, primary query-port wiring, unchanged
  repository identity and lifecycle close. No background task starts on configuration.
- Real Spring HTTP tests deliberately use a stubborn synthetic reader (not claimed
  PostgreSQL evidence). Four concurrent GET/HEAD list/selected requests time out,
  keep exactly four underlying reads occupied, reject overflow without queuing,
  preserve 400/401 handling, then recover all shapes after the owned gate releases.
- Existing real PostgreSQL transport acceptance still proves actual discarded
  bytes, connection closure, zero active leases/waiters and unchanged tables. It
  now waits separately for JDBC cleanup after the earlier HTTP 503; the resource
  assertions remain mandatory rather than incorrectly equating HTTP completion
  with driver termination. Ordinary concurrency/pool/SQL regressions remain.
- Reuse unchanged ADR-078 packaged lifecycle/transport tooling and existing
  responsive browser acceptance. Record timings and failures in IMPLEMENTATION_LOG.md.

## Completion scope

This closes the local MVP's application read-wait containment work package, not a
hard real-time HTTP SLA. Request parsing/authentication, servlet scheduling, the
small bounded evidence validation/serialization, response writes, client receipt,
OS/GC pauses and whole-process resource failure are outside this reader timer.
Background DB cancellation is best effort, not guaranteed termination. General
transport/load matrices and real-host operation are not implicit new prerequisites
for every future feature. No frontend deadline claim or inferred financial value
is introduced. After acceptance, move to scoring input/methodology integration;
Ubuntu boot/backup/access work waits for the future host and explicit configuration.
