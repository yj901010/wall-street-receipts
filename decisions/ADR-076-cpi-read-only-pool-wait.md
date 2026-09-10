# ADR-076: Bound the read-only CPI operator pool wait

Date: 2026-09-10 (KST)

## Problem and decision

PR #23 is merged into develop at `0c747940d9c8e603b6a30c325470d6442e015dcb`.
PR CI #58 (`34438801906`) and merge push CI #59 (`34439195811`) succeeded.
Continue local development; the Ubuntu home server is still unavailable.

ADR-074 bounds individual CPI read statements and ADR-075 admits at most four
reader invocations, but an admitted read can wait for the shared Hikari pool.
In the explicitly enabled CPI_READ_ONLY API process, cap the normal datasource's
connection-wait setting at 1,000ms. This fixed local-operator policy is not a
measured throughput capacity or end-to-end availability guarantee.

`config/CpiReadOnlyPoolConfiguration` supplies a static BeanPostProcessor only
when both `app.operator-api.enabled=true` and `app.operator-api.access=CPI_READ_ONLY`.
After datasource property binding, reduce `connectionTimeout` to the smaller of
its current value and 1,000ms. This preserves stricter settings, including Hikari's
250ms minimum; Hikari's zero/unlimited representation is capped as well. Binding
a larger timeout cannot override the cap. Configuration does not open a connection.

Only the normal bean named `dataSource`, when it is a HikariDataSource, is changed.
FULL access and disabled/default operator modes retain their existing settings.
Auxiliary pools, non-Hikari datasources, capacity, validationTimeout, JDBC driver
properties and the existing three-second SQL/ten-second write limits are untouched.
No new pool, dependency, schema, environment variable or deployment input is added.

## Scope and limits

The setting covers all borrowers of this named pool in the selected process,
including any public queries, health checks or explicitly enabled Flyway use.
It is not per-route admission, database authorization or protection for another
pool/runtime. CPI_READ_ONLY remains an HTTP privilege restriction, not a DB role;
the separate packaged reader still uses its existing SELECT-only database role
with Flyway, collectors and fixture import disabled.

The exhausted-pool waiting budget is not a total connection-acquisition or HTTP
deadline. Connection validation, driver/socket operations, initial connection
creation, retries elsewhere and network failures can take additional time.
No new network or validation timeout is claimed. Existing CPI query failures
return the sanitized no-store 503 contract without pool details, inferred retry
times, automatic retries, fabricated evidence or empty-success fallback.

Routes remain GET/HEAD `/internal/v1/cpi/collection-attempts` and
`/internal/v1/cpi/collection-attempts/{attemptId}`. Controller, security, evidence
model, UTC/source provenance, Web source and lifecycle recipes are unchanged.
Real keys, provider calls and existing databases remain outside this local work.

## Verification

- Configuration tests cover default, disabled and FULL modes; enabled read-only
  timeout values 0/250/500/1,000/9,000/30,000ms; untouched capacity, validation and
  driver settings; normal application.yml environment aliases; unrelated pools
  and non-Hikari datasources. Lazy configuration must not open a connection.
- Extend the actual HTTP/SELECT-only PostgreSQL test with a deliberately larger
  configured 9,000ms timeout and assert the resulting pool is capped at 1,000ms.
  Borrow all four real API pool connections. GET/HEAD list/selection each return
  sanitized no-store 503 while those connections remain held; no connection
  waiters remain after each response. Timing tolerances are test checks, not SLAs.
- Authentication and malformed-ID rejection stay usable during exhaustion.
  Release the leases, read normally, then exercise the original full four-call
  lock/release, SQL-cancel and recovery waves, proving admission slots recover.
  Compare all four CPI tables against the initial disposable DEMO snapshot.
- Ordinary full API verification includes new tests. Explicit production browser
  regression covers empty/seeded/unavailable/recovered desktop and mobile states.
  Explicit packaged lifecycle acceptance checks boot/read/restart compatibility;
  it does not establish pool-exhaustion timing inside the Linux container.
- Pin both new configuration files and changed HTTP test, with only its exact
  merged ADR-075 predecessor accepted before commit and mandatory current bytes.
  Historical scripts and hosted workflow remain unchanged.

Measured results and next work belong in IMPLEMENTATION_LOG.md. Prerequisite CI
success does not imply hosted CI success for this unpushed candidate.
