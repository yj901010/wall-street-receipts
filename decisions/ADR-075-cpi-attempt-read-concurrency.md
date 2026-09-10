# ADR-075: Non-queuing CPI attempt read admission

Date: 2026-09-10 (KST)

## Problem and decision

PR #22 is merged into develop at `fbc53893f027b0212460f6e3382440e7ad732b08`.
PR CI #56 (`34428596127`) and merge push CI #57 (`34428884350`) succeeded.
The user requested the next step while the Ubuntu home server remains unavailable.

ADR-074 cancels individual JDBC read statements, but a burst of protected CPI
requests could still occupy many request threads or wait for database pool
connections. Bound entry to the existing `CpiAttemptReader` invocation in the
singleton `CpiAttemptQueryService` with a four-permit semaphore. Four is a fixed,
conservative local-operator policy, not a measured database capacity or SLA.

Recent and exact-ID reads share the same budget, including GET and HEAD HTTP
requests. `tryAcquire()` never waits or creates an admission queue. Saturation
throws the existing `Unavailable` exception before invoking the reader, yielding
the existing sanitized no-store 503 contract. No overload detail, SQL content,
invented retry deadline, automatic retry, fixture fallback or empty-success
response is introduced.

Only a successful acquisition enters the try/finally block, so rejected requests
cannot add permits. Release the permit when the reader invocation ends, including
runtime exceptions, null results and Java errors. Existing missing-row and
evidence validation occur after release. Canonical UUID validation still precedes
admission, and HTTP authentication, authorization and query-parameter validation
retain their existing order and error responses.

## Scope and limits

The budget belongs to this singleton query-service instance. It is not a global
database cap, distributed limiter, per-token quota, requests-per-second limit,
servlet admission policy or guarantee of fairness. Multiple API instances each
have their own budget. Direct repository users, raw receipt queries, collection
commands, SEC operations and the rest of the shared pool are outside it.

This prevents additional CPI reader calls from queueing behind four admitted
calls; it does not remove pool/transport waits within those admitted calls or
establish an end-to-end HTTP deadline. Keep ADR-074's three-second statement
limit and the existing write transaction budget unchanged. No new pool,
configuration setting, dependency, thread executor, schema or deployment default
is added to application code.

Routes remain GET/HEAD `/internal/v1/cpi/collection-attempts` and
`/internal/v1/cpi/collection-attempts/{attemptId}`. The controller, security,
query result model, source provenance and UI are unchanged. No real credential,
provider call, persisted database change or Ubuntu activation is involved.

## Verification

- Deterministic latch-based unit tests hold four list, selection or mixed reads,
  then reject repeated requests to both methods without reader interaction,
  waiting or later execution. Invalid IDs still fail validation while saturated.
  A second full wave proves rejections neither leak nor over-release permits.
- Refill all four slots after successful, empty, null, database-failure,
  invalid-evidence and fatal-error outcomes. Domain errors and sanitization keep
  their existing meanings; a fatal Java error is not converted into evidence.
- A real loopback Spring API uses a SELECT-only role against an owned disposable
  PostgreSQL database. Hold four actual SQL statements (GET/HEAD, list/selection)
  behind a table lock. Overflow must return sanitized no-store 503s while all
  four statements are still blocked. Authentication and validation remain usable.
- Exercise successful lock release, actual SQL timeout, and a new full-capacity
  wave after timeout. Compare all four CPI tables with the initial DEMO snapshot.
  No mocked SQL result or product fault endpoint establishes this evidence.
- Pin the new unit/integration tests and changed service's exact current bytes,
  plus its exact merged predecessor. Historical bodies and hosted jobs stay
  unchanged; ordinary current Maven verification includes these new tests.

Measured results and remaining work are recorded in IMPLEMENTATION_LOG.md.
Prerequisite CI success does not imply hosted CI success for this candidate.
