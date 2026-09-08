# ADR-069: Default-disabled, protected CPI attempt queries

Date: 2026-09-08

## Scope and prerequisites

PR #16 was user-merged at `e66669d27cf120c9b79d737abb91ca37cace8d32`.
Its head `0faf0d3` passed CI #44 (`34203090304`). The next user-approved
implementation exposes the ADR-068 ledger to an authenticated local operator.
This slice is API-only; no operator browser UI or public activity feed is added.

No new BLS key, third-party account, actual provider request or home server is
needed for implementation. Tests use synthetic credentials and disposable DBs.
Do not activate the operator API on the user's running preview, migrate the
existing development databases, edit `.env`, or start a collector.

## Routes and authentication

With the existing `OPERATOR_API_ENABLED` setting enabled in a separately
approved local operator process:

```text
GET /internal/v1/cpi/collection-attempts
GET /internal/v1/cpi/collection-attempts/{canonical-lowercase-UUID}
```

Reuse ADR-043's opaque 32-byte Base64 bearer credential and server-side
`OPERATOR_API_TOKEN_SHA256` digest. Do not put the token in a URL, browser
storage, repository, screenshot or chat. Existing credential provisioning
instructions are in ADR-043. No real token is created or requested in this
slice because only synthetic local tests run.

The shared operator switch still defaults to false, and when enabled forces
the **entire API process** onto loopback. It also enables the pre-existing SEC
operator routes, which are not read-only. This is not a CPI-only credential or
a new per-user authorization model. Leave normal public API deployment settings
unchanged. Ask the user for the intended operator process and protected secret
handling before actual activation; this development PC is not the home server.

Extend the existing stateless bearer filter/security chain to `/internal/v1/cpi/**`.
Only GET/HEAD with OPERATOR authority are accepted for that namespace. Deny
other verbs even with a valid token; do not add collection/retry mutations.
No cookies, sessions, trusted forwarded identity, query-string credentials or
CORS access are introduced. Disabled controllers/services are absent and return
ordinary 404 without an authentication challenge. Existing SEC/public route
behavior is preserved.

Successful/error responses from the enabled CPI endpoints use `no-store`.
Authentication/access errors use a fixed CPI instance URI, not the supplied
path. Firewall errors use the existing `/invalid-request` instance and now
apply CPI no-store/KST handling. Malformed selectors and all query parameters
are rejected without DB reads; there is no arbitrary limit, offset, sort,
as-of, automatic pagination or token query option. UUID parsing is strict,
not Java's permissive shortened UUID parsing.

## Read model and evidence boundary

`CpiAttemptReader` is a narrow read-only port. `CpiRepository` inherits it;
`JdbcCpiRepository` selects explicit ledger columns only. One query returns at
most 21 rows ordered by `started_at DESC, attempt_id DESC`; the API returns
20 and uses the extra row only for `hasMore`. No total count is inferred and
no older row is automatically fetched. Exact UUID lookup can select a known
record outside this window. The API does not access raw receipts, the mutable
collection gate or a provider. Row count is bounded; this is not a new HTTP
request-rate limiter or an end-to-end database deadline.

`CpiAttemptQueryService` rejects duplicate/out-of-order/oversized results,
future start/terminal timestamps and mismatched selected IDs. Database errors
and invalid persisted shapes become sanitized 503, never stale cached success
or DEMO fallback. Empty recent results are 200 with an empty list; missing
selected records are 404. There is no list-wide summary that conceals a failure
behind an older success.

Each response carries `metadata`:

- `schemaVersion: 1.0.0`, `dataMode: UNVERIFIED`, and
  `evidenceMode: PERSISTED_ATTEMPT_RECORDS`.
- `timezone: Asia/Seoul` and `observedAtKst` from the injected Clock after the
  database read. This is the application observation marker, not a DB commit
  time or transaction-consistent historical as-of snapshot.
- Explicit limitations: no heartbeat, CPI freshness, point-in-time history,
  receipt replay, proof that HTTP reached the provider, or complete history
  for pre-V11/missing-start invocations.

Rows expose the attempt UUID, MANUAL/SCHEDULED origin, KST start, gate permission,
status and nullable terminal fields. Missing terminal data is **UNKNOWN**,
never RUNNING/FAILED/SAVED inferred from age. Saved receipt IDs/timestamps are
stored references; their raw content/hash is not replay-verified by this API.
FAILED exposes only FETCH/PARSE. RATE_LIMITED exposes its KST lower bound, not
a promised next run. SKIPPED never becomes a capture. Permission is not proof
that a request began. The ledger has no observed-vs-DEMO provenance flag, so
merely residing in PostgreSQL does not justify labeling it live provider data.

All new success/error timestamp values are explicitly `+09:00` KST, including
the CPI-specific branch of the shared security/firewall problem writers. Other
existing endpoints retain their established wire contracts. Null timestamps,
capture IDs and failure fields remain null. UTC storage is unchanged.

## Verification and custody

- Query unit tests cover bounds, stable ordering/ties, duplicates, unknown vs
  absent, future evidence, strict UUIDs and sanitized storage failures.
- MVC tests cover enabled/disabled wiring, all terminal states, KST rollover,
  no-store, invalid arguments, missing/wrong/duplicate bearer tokens, forwarded
  identity refusal, denied mutation verbs, HEAD, firewall rejection and no
  fallback after a later failed read.
- The PostgreSQL integration starts a real loopback Tomcat server, seeds only
  DEMO records, confirms 20-of-24 results and exact selection outside the window,
  sends real HTTP authentication/verb/HEAD checks and compares all CPI tables
  before/after reads. A disposable SELECT-only role can run the reader without
  access to raw receipts or the mutable gate. No actual role is provisioned.
- Full API `verify` covers existing routes, security, migrations and packaging.
  Web/responsive checks are not repeated locally because no web/layout changes.
- Closed CPI custody expands from 47 to 58 exact paths: nine baseline replacements
  and 49 additions. Three shared security files keep exact baseline identities;
  eight new source/test files are pinned. Only two exact merged ADR-068 repository
  predecessors are admitted during pre-commit development, never stale working
  bytes. No workflow, historical body, permissive wildcard or migration changes.

Actual verification results and initial test corrections are recorded in
IMPLEMENTATION_LOG.md. Next: review/PR/hosted CI, then a separately scoped
protected operator presentation. Keep token handling out of public web pages.
Heartbeat, notification channel, retention and real server activation remain
separate decisions requiring user details before enabling them.

## References

- [Spring Security request authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- ADR-043: default-disabled local single-operator API
- ADR-068: durable CPI attempt evidence
