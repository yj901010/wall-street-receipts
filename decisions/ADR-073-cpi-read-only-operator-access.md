# ADR-073: CPI-only read access for a local operator process

Date: 2026-09-10 (KST)

## Problem and decision

PR #20 is merged into develop at `0b674db887c23315eac983b88d27a301cdb2d066`.
Its PR CI #52 (`34323664250`) and develop push CI #53 (`34326361773`)
succeeded. The user confirmed that the Ubuntu home server is not ready and
requested the next locally executable development step.

ADR-069 reused the SEC operator bearer for CPI queries. The CPI UI's closed
gateway blocked SEC paths, but that same bearer could still authorize SEC
collection when sent directly to the enabled API. Add an explicit access
setting to the existing local operator process:

```text
OPERATOR_API_ACCESS=CPI_READ_ONLY
```

The API grants an authenticated bearer only `CPI_READ` in this mode. It permits
GET and HEAD for the recent CPI attempt list and one selected attempt. The
existing controller still validates exact UUIDs and rejects query parameters.
Other CPI paths, mutation verbs and every SEC operator path are forbidden,
including direct API calls that bypass the UI. Authentication is still required:
missing or invalid credentials return 401; authenticated forbidden requests
return 403 with the existing sanitized, no-store error contract.

Omitting the setting preserves `FULL`, the prior operator behavior, including
SEC operations. These two exact uppercase strings are the only valid values.
Empty, misspelled or combined values fail startup instead of granting broader
access. The operator remains disabled by default. Selecting an access mode
alone does not enable it or replace the existing digest validation.

## Scope and operation

This is one credential scope per API process, not multiple users or roles
sharing an instance. Provision a distinct credential for a future CPI reader
process; reusing that secret with a separate FULL process would authorize that
process's broader access. The SHA-256 digest and bearer format are unchanged;
no actual credential is provisioned here.

The operator's existing loopback binding remains mandatory. Public API behavior,
the browser UI, gateway, routes, database schema and default deployment are
unchanged. This setting controls protected HTTP authorization only: it is not
a database read-only transaction mode, cannot restrict an unrelated process,
and does not configure providers, fixture import or migrations. A separately
configured reader process still needs SELECT-only database grants, Flyway and
fixture import disabled, and providers/collectors disabled, as exercised by
the disposable browser and packaging rehearsals.

The new mode does not establish remote access, a public admin page, scheduled
collection, observed CPI freshness, Ubuntu startup or backup/reboot recovery.
The home server remains a later step requiring its actual host and access
information. Existing real keys, persisted databases and the user's modified
web declaration are outside this local development step.

## Verification

- Configuration binding checks both valid modes, disabled-by-default behavior,
  required digest, explicit empty/invalid access and digest redaction.
- Real Spring Security tests check the granted authority and credential erasure,
  authenticated CPI list/selection/HEAD, denied SEC reads and commands with zero
  execution/query-service interactions, denied mutation verbs and authentication
  failures. Existing FULL and disabled-mode suites continue to run.
- The real HTTP/PostgreSQL acceptance selects CPI_READ_ONLY through the environment
  alias, checks loopback binding and direct SEC denial, and compares the CPI
  tables before and after the request sequence.
- The production browser rehearsal and packaged API/UI lifecycle rehearsal now
  explicitly select CPI_READ_ONLY. The Linux probe also submits SEC requests
  directly to the API, including after restarts; all must return 403.
- Current-code custody pins the exact changed files and their exact merged
  predecessors. Historical snapshots do not validate new behavior; the current
  Java and explicit runtime tests do.

Measured results, retained local reports and remaining work are recorded in
IMPLEMENTATION_LOG.md. No hosted CI result for this new candidate is implied by
the successful prerequisite PR #20 runs.
