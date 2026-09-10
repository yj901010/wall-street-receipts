# ADR-078: Packaged CPI transport-fault acceptance

Date: 2026-09-10 (KST)

## Context

PR #25 is merged into develop at `8cfcc74e3acf324579b93790b6743f8290840775`.
PR CI #62 (`34451268232`) and merge CI #63 (`34451847870`) pass all four jobs.
ADR-077 bounds individual PostgreSQL connection/read/cancel operations. Its Java
integration test observes real transport failure, but the prior packaged Linux
rehearsal only exercised process loss and restart. The Ubuntu server is not ready.

## Decision

Add an explicit, disposable local-Linux acceptance tool, reusing the unchanged
ADR-072 committed-source image builder, runtime restrictions, synthetic database,
SELECT-only role, authentication, lifecycle checks and ownership-checked cleanup:

```sh
python scripts/verify-cpi-operator-transport.py --confirm-disposable-demo
```

No other arguments or external targets are accepted. The API, Web and operator
images use the exact committed application source. The report separately pins
all eight current rehearsal inputs by LF-normalized SHA256, so uncommitted tooling
is not represented as committed application source. Existing Dockerfiles, product
Java/TypeScript, routes, schemas, fixtures and deployment defaults are unchanged.

A test-only Node TCP relay is mounted read-only into its own hardened container
on the owned internal Docker network. It forwards only to `postgres:5432` in that
network. Its control listener is loopback-only and has exactly status/silence
operations; it accepts no target, payload or restoration command. The relay stores
only bounded connection identities, discarded byte counts and closure states, not
PostgreSQL messages, credentials or query payloads. New connections are unaffected
by silencing selected established connections. Unit-test injection admits only
the fixed internal hostname or IPv4 loopback. No Docker socket, host port,
persistent volume, privileged capability or new runtime dependency is introduced.

The test API uses a one-connection pool to identify the exact broken connection.
It retains actual ADR-074/075/076/077 read, admission, pool and transport budgets;
the gateway retains its five-second upstream timeout and one-second limiter.
These test-specific topology/pool choices are explicit in the report, not deployed.

## Acceptance

First repeat the original packaged boot/read/auth/KST/duplicate-bind/SIGTERM/API-loss/
ordered-restart acceptance. Then, for each API GET/HEAD list/selection and both UI
GET list/selection (six fault cases):

1. Hold a real table lock in an identified, time-bounded, test-owned PostgreSQL
   session. Observe the actual SELECT-only reader's SQL blocked by that exact PID.
2. Silence the existing reader connection and explicitly cancel only the owned
   lock session. Observe actual PostgreSQL response bytes being discarded.
3. Require the existing sanitized no-store 503 and confirm invalid-query 400 and
   unauthenticated 401 remain available during the fault. HEAD has no response body.
4. Observe broken relay connection closure; the Java test in ADR-077 separately
   inspects the pool lease and root SocketTimeoutException. This black-box packaged
   tool does not invent those internal measurements or add a diagnostic endpoint.
5. Recover all six API/UI reads without restarting the API or UI between faults.
   Compare exact attempt evidence hashes and all four persisted CPI table snapshots.

Finally require clean API/UI/relay shutdown, unchanged tables and no synthetic
credential leakage in bounded runtime log tails. Capture sanitized diagnostics
and write a DEMO JSON report even on failure, and clean up only owned resources.
The inherited build safety rejects working-tree secrets and ambient provider keys.
The ordinary Python CI suite runs offline safety tests plus Node's real loopback
relay tests. The Docker acceptance remains explicit, not silently enabled in CI.

## Limits and next work

Measured per-request durations describe these fault cases on this machine only.
They are not a total HTTP deadline, SLA, concurrency/load benchmark, DNS/TLS/connect
failure matrix or validation of a real Ubuntu host. A UI 503 may precede completion
of its underlying JDBC request; this test waits for the broken connection to close
before claiming recovery. The exact cause of ADR-077's isolated Chromium body-capture
failure remains unknown; this tool does not claim to fix it or replace responsive
browser tests. Measured results and any failed attempts belong in IMPLEMENTATION_LOG.md.

After this transport acceptance, review the remaining whole-request deadline gap
as the final bounded operations work package before moving to scoring integration.
Real-host boot/recovery, backup restoration and operator access still await a host.
No real provider, user database, production activation or public exposure is authorized.
