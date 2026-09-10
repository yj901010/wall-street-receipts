# ADR-074: Bounded CPI attempt read statements

Date: 2026-09-10 (KST)

## Problem and decision

PR #21 is merged into develop at `f98d491331b8335a16fbcffa6648353e6ab381ba`.
Its PR CI #54 (`34421801190`) and merge push CI #55 (`34422104579`) succeeded.
The Ubuntu home server is not ready; continue locally without real credentials
or the existing database.

ADR-069 capped the attempt list's output, not its database execution time. The
operator gateway's five-second fetch timeout does not itself cancel SQL already
executing in the API. A lock on either ledger table could retain a JDBC
connection after the browser has received an unavailable response.

Apply a fixed three-second JDBC statement query timeout to the two external
attempt reads in `JdbcCpiRepository`: recent attempts and exact-ID selection.
Apply the cap in the prepared-statement setter, after Spring has applied its
template settings and remaining transaction timeout. Preserve a shorter positive
timeout; a longer ambient transaction must not extend the read limit. Keep UUID
binding, the single-statement snapshot, ordering and 21-row lookahead unchanged.

The shared JdbcTemplate and PostgreSQL session settings are not mutated. There
is no `SET`, role/schema change, new connection pool, query retry or fallback to
fixtures/empty evidence. Failure to apply the timeout prevents execution.
Existing query-service handling converts database exceptions into the existing
sanitized `503 CPI_ATTEMPT_QUERY_UNAVAILABLE`, with `Cache-Control: no-store`.

Internal receipt/result validation now uses an explicitly separate private
lookup. It retains the existing ten-second REQUIRES_NEW write transaction
budget. Collection, raw receipt reads, gate updates and other repositories are
not subject to this new three-second read cap.

## Boundary

This is a driver-enforced statement cancellation deadline, not an end-to-end
three-second HTTP guarantee. Pool acquisition, connection establishment, socket
failure and cancellation delivery can take additional time. The integration
test's elapsed-time tolerance is not a production SLA. Transport/pool bounds,
concurrency admission, real host operation and remote access remain separate
work. A future deployment must validate the selected driver's timeout behavior
and its network conditions.

No route, authentication scope, schema, deployment default, UI or observed data
changes. Existing GET/HEAD endpoints remain `/internal/v1/cpi/collection-attempts`
and `/internal/v1/cpi/collection-attempts/{attemptId}`. No provider call or
home-server activation is performed.

## Verification

- Current Java unit tests exercise both reads with unlimited, shorter, equal and
  longer template limits; short/long ambient transactions; unchanged shared
  settings; both internal write lookups; fail-closed timeout setup and cleanup.
- Actual loopback HTTP/PostgreSQL acceptance holds ACCESS EXCLUSIVE locks on
  each ledger table and queries both endpoints. Require sanitized no-store 503s
  within a bounded test window, no remaining server-side lock waiters and reuse
  of a single-connection API pool while the blocking lock is still held.
- Release each dedicated disposable lock connection, require both endpoints to
  return 200, and compare all four CPI tables with their pre-fault contents.
  Authentication and invalid-ID handling still work during a lock.
- A SELECT-only role must time out both read statements under a real lock and
  recover after release, without access to raw receipts or any additional grant.
- Pin exact current source/test bytes and exact merged predecessors in current
  CPI custody. Existing historical checks are not new behavior validation.

Measured results and next work are recorded in IMPLEMENTATION_LOG.md. Successful
prerequisite hosted runs do not imply hosted CI success for this candidate.
