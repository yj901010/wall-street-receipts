# ADR-077: Read-only CPI JDBC transport budgets

Date: 2026-09-10 (KST)

## Context and decision

PR #24 is merged into develop at `d8a08f908fa8f1ba80b741c97e30f19407f51ecd`.
PR CI #60 (`34448162395`) and merge push CI #61 (`34448597617`) pass all four
jobs. Continue local development while the Ubuntu home server is unavailable.

SQL cancellation, four-reader admission and exhausted-pool waits are bounded by
ADR-074/075/076, but a silent database connection can still occupy an admitted
reader. Add `config/CpiReadOnlyJdbcConfiguration`, enabled only by both
`app.operator-api.enabled=true` and `app.operator-api.access=CPI_READ_ONLY`.
After property binding, apply to the normal Hikari `dataSource` using a PostgreSQL
JDBC URL, not an explicitly supplied datasource instance/class:

| Setting | Maximum | Unit |
| --- | --- | --- |
| pgJDBC connectTimeout | 2 | seconds |
| pgJDBC socketTimeout | 5 | seconds |
| pgJDBC cancelSignalTimeout | 1 | seconds |
| Hikari validationTimeout | 750 | milliseconds |

Keep shorter positive effective values. Missing or zero/unlimited driver settings
receive the cap. Invalid negative, non-integral, overflowing or non-string timeout
settings fail startup with a fixed setting-specific error, not their supplied
values or credentials. Existing mode validation still applies independently.

Use the already installed pgJDBC parser, not a separate interpretation of JDBC
URL syntax. URL values take precedence over properties; the final duplicate URL
key wins. Append only the three fixed bounded keys, preserving the original URL
bytes and unrelated settings, and set matching datasource properties. A temporary
empty parser password prevents implicit .pgpass lookup; it is never applied to the
pool. Explicit service-file parsing follows the driver's existing behavior.
The existing PostgreSQL dependency changes from runtime to compile scope solely
for this configuration adapter; its managed version and packaged runtime library
remain unchanged. No new dependency or connection is opened to configure budgets.

The behavior follows the pinned pgJDBC 42.7.11
[URL parser](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.11/pgjdbc/src/main/java/org/postgresql/Driver.java),
[timeout properties](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.11/pgjdbc/src/main/java/org/postgresql/PGProperty.java)
and [socket setup](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.11/pgjdbc/src/main/java/org/postgresql/core/v3/ConnectionFactoryImpl.java).

## Boundaries

FULL/disabled/default modes, H2, other named pools and custom datasource-instance/
class configurations remain untouched. In the selected process, all borrowers of
the normal pool share the policy, including any public queries, health checks or
explicitly enabled Flyway. It is not a per-route or distributed deadline, DB
authorization, throughput measurement or performance SLA. Operators choosing a
different driver/datasource runtime need their own verified transport policy.

Socket timeout applies to individual blocking reads; responses that keep delivering
bytes, DNS resolution, TLS/OS behavior, blocked writes, multi-host attempts and
initialization/retry loops are not bounded by one end-to-end timer. Hikari may
temporarily change the socket timeout for validation; cap that separate setting
too. Network failure can cause Hikari to evict and replace a connection; no product
query retry, guessed retry time, evidence fallback or new HTTP payload is added.

Keep existing SQL/write timeouts and reader admission. Routes remain GET/HEAD
`/internal/v1/cpi/collection-attempts` and `/{attemptId}`. No schema, controller,
security, evidence/provenance, Web, lifecycle-recipe or deployment-default changes.
The packaged reader still disables Flyway/import/collection and uses its existing
SELECT-only role. This phase does not use real credentials/providers or existing DBs.

## Verification

- Real property-binding tests cover mode isolation, positive/zero/invalid values,
  duplicate and encoded URL values, URL-over-property precedence, unchanged TLS/
  application-name/password settings, environment aliases and alternate pools.
  No pool connection is opened by configuration. Existing pool-wait tests remain.
- A test-only Java byte relay accepts and forwards only loopback connections,
  selectively discards downstream bytes on established connections, and records
  byte counts only. Unit checks cover transparent forwarding, targeted blackholing,
  remote-target rejection and closure of owned listener/sockets/virtual threads.
- An actual loopback Spring API uses a SELECT-only role on disposable PostgreSQL.
  A direct query without statement cancellation proves the driver's real socket
  timeout closes a silent connection. For each GET/HEAD list/selection request,
  first observe its real SQL statement blocked by a test-owned table lock, then
  blackhole that established connection and release the lock. Genuine database
  response bytes are discarded; existing sanitized no-store 503s return with no
  active pool lease or waiter. Auth/invalid-ID rejection remains available.
- Each broken connection closes and is replaced; all four read shapes recover
  after every fault. Full snapshots of all four CPI tables remain equal. No mocked
  SQL data, provider requests, product fault route or production activation.
- Full API, explicit production browser, custody/fixture and packaged lifecycle
  regression results are recorded in IMPLEMENTATION_LOG.md. Browser/lifecycle
  checks are ordinary boot/read/restart regression, not separate transport-fault
  timing evidence. Hosted success of this new candidate requires a later PR.
