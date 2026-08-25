# ADR-044 — Disposable Offline Local Operator API Acceptance Harness

- Status: Accepted
- Date: 2026-08-26

## Context

ADR-043 added a default-disabled, loopback-only operator HTTP boundary. Its
automated tests separately prove request mapping, authentication, a real
loopback Tomcat listener, H2 integration, and PostgreSQL 17 persistence. Before
deployment, a developer also needs one repeatable command that composes the
packaged application, an actual TCP client, and an actual PostgreSQL service
without editing local secrets or risking SEC traffic.

The existing manual procedure is intentionally explicit, but it spans multiple
terminals and asks the operator to update and later restore the ignored root
`.env`. It is useful for investigation, not as the preferred acceptance gate.
The repository therefore needs a disposable orchestration harness rather than
another product endpoint, test-only runtime profile, or long-lived local
database.

## Decision

Add one repository-owned PowerShell 7 script:

```text
scripts/verify-local-operator-api.ps1
```

From the repository root, the complete acceptance check is:

```powershell
pwsh -NoProfile -File ./scripts/verify-local-operator-api.ps1
```

The harness requires Java 21, Docker with Compose v2, and PowerShell 7. On
macOS/Linux it invokes the non-executable Maven wrapper checkout through the
platform's POSIX `sh`; Windows uses `mvnw.cmd`. It uses the existing wrapper and
`compose.yaml`; it adds no dependency, runtime profile file, application route,
database migration, provider, public contract, or web surface.

### Disposable topology

Every invocation:

1. validates Java, Docker, Compose, Maven-wrapper, and repository prerequisites;
2. packages the Spring Boot JAR unless `-SkipPackage` is explicitly supplied;
3. reserves distinct loopback host ports for PostgreSQL and the API;
4. creates a unique, tightly validated Compose project name;
5. starts only that project's PostgreSQL 17 service and named volume;
6. starts the packaged JAR as one directly owned child process;
7. waits for the real loopback health endpoint to report `UP`;
8. exercises the HTTP and persistence acceptance contract; and
9. stops only its exact child PID, removes only its exact Compose project and
   volume, clears mutable credential bytes, and removes only its validated
   operating-system temporary directory.

The root `.env`, the default Compose project, its `postgres-data` volume, any
already-running developer services, and all user data remain untouched. The
script passes `.env.example` only as safe Compose interpolation input. The
Spring application does not activate `local` and does not import the root
`.env`.

### Offline and credential boundary

Each run creates these values only in process memory:

- a standard-Base64 token encoding exactly 32 random bytes;
- the token's lowercase SHA-256 digest; and
- a disposable PostgreSQL password.

The raw token is used only in the in-process HTTP client's Authorization
header. Neither the token nor its digest is written to a file, command line,
response, repository, or normal output. Failure-log tails are defensively
redacted, mutable byte arrays are cleared, and related variables are released
during cleanup.

The child API receives explicit high-precedence acceptance-process
configuration that overrides inherited Spring data-source, Flyway, profile,
application JSON, and Java-option settings. It forces both the application and
Flyway to the same disposable database and also forces:

```dotenv
OPERATOR_API_ENABLED=true
SEC_PROVIDER_ENABLED=false
SEC_BASE_URL=http://127.0.0.1:1
SEC_CONTACT_EMAIL=
SEC_LIVE_SMOKE=false
```

The operator API itself continues to force the entire embedded server to the
loopback interface. The disabled provider gate is the primary no-network
control; the closed loopback provider origin is a second control. A normal
acceptance result is therefore a durable known provider-gate failure, never a
claim that SEC collection succeeded.

No domain, DNS change, API key, SEC account, paid plan, OAuth client, provider
contact email, or user-supplied operator token is needed. The first invocation
may download ordinary Maven dependencies or the PostgreSQL image if they are
not already cached, but it is not authorized to contact SEC.

### Acceptance contract

The harness checks all of the following through real TCP HTTP calls:

- the health endpoint reports `UP`;
- a request without a Bearer token returns sanitized non-cacheable
  `401 OPERATOR_AUTHENTICATION_REQUIRED`;
- an authenticated root command returns `200`, `Location`, and `no-store`;
- the provider-disabled result is `TERMINAL_FAILED_KNOWN` with
  `PROVIDER_GATE_CLOSED` and `PROVIDER_INVOCATION_NOT_STARTED`;
- `providerDispatch` is `null` and `automaticRetryAllowed` is false;
- the same canonical UUID and command returns byte-identical immutable JSON and
  the same `Location`;
- exact GET reconstructs the same immutable JSON without a `Location` header;
- the same UUID with a changed CIK returns non-cacheable
  `409 OPERATOR_REQUEST_CONFLICT`;
- a valid exact-root command referencing absent evidence returns non-cacheable
  `422 EXACT_EVIDENCE_NOT_ADMITTED`; and
- direct inspection of the disposable PostgreSQL ledger finds exactly one
  attempt, zero provider dispatches, and one terminal outcome.

HTTP `200` remains a representation status. The lifecycle and terminal fields,
not transport success alone, carry the provider outcome.

### Failure and cleanup behavior

The script exits nonzero on a missing prerequisite, startup timeout, unexpected
HTTP contract, unexpected database cardinality, or cleanup failure. It never
falls back to the developer's existing database or `.env`.

Cleanup targets are resolved and validated before any recursive removal. The
Compose project name must match the harness-owned random format before
`down --volumes` is allowed. A cleanup failure is reported with the exact
non-secret project identifier so the operator can inspect it; unrelated
containers and volumes are never selected.

## CI boundary

CI parses the PowerShell source and statically guards the isolation, offline,
credential, expected-status, ledger-cardinality, and cleanup markers. It does
not execute this additional composed harness because the existing API suite
already owns deterministic behavioral and PostgreSQL Testcontainers coverage.
The composed acceptance command is a pre-deployment local gate and its result is
recorded in `IMPLEMENTATION_LOG.md`.

## Consequences

- A developer now has one command for the missing packaged-JAR/real-HTTP/
  Compose-PostgreSQL evidence.
- Each run is slower than a focused unit test because it packages and starts
  actual processes.
- Port reservation and later binding cannot be perfectly atomic. A collision
  fails closed and cleanup removes only harness-owned resources; rerunning
  selects new ports.
- This decision neither approves a remotely reachable static-token boundary nor
  changes the future deployment prerequisites in ADR-043.
