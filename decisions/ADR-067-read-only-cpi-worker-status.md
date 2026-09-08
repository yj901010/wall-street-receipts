# ADR-067: One-shot, read-only CPI worker evidence inspection

Date: 2026-09-08

## Scope and prerequisites

PR #14 merged into develop at `276db2263146ec29966401786c829814c453bbe8`.
Its corrected head `87bcc23587c7e687d29235b16242f1f229402358` passed CI #40
(`34197096507`), including Web, API, Call audit integration and Repository
contracts. The next user-approved implementation is passive worker status,
without production activation, new keys or a home server.

Add `scripts/inspect-cpi-worker.py`, a Python 3.12+ standard-library-only CLI.
An actual inspection needs Docker CLI read access and an explicitly selected
local container name/ID running the scheduled CPI command. It does not need a
BLS key or DB credentials. A future remote home server must run the tool there;
the tool refuses remote Docker endpoints. No server address needs to be supplied
for development of this slice.

```text
python scripts/inspect-cpi-worker.py --container SELECTED_CONTAINER
python scripts/inspect-cpi-worker.py --container SELECTED_CONTAINER --json
```

This is not a starter, installer, daemon, heartbeat, automatic healthcheck or
notification delivery mechanism. It does not create/stop/restart containers,
read `.env`, invoke `docker exec`, query a database, call a provider, register a
schedule, or change Compose. Existing runtime, schema, web/API routes, KST
schedule and API/web egress rules stay unchanged.

## Evidence boundary

Container state is observed through selected Docker inspect fields. A recorded
SAVED marker is **log evidence only**, not a fresh query proving that the DB
still contains that capture or that the CPI data is current. All reports use
`dataMode: UNVERIFIED` and `evidenceMode: RETAINED_LOGS_NOT_DB_VERIFIED`, with
explicit no-heartbeat/no-freshness/no-DB-check limitations. DEMO verification
reports wrap synthetic snapshots in their own `dataMode: DEMO` envelope.

Separate these facts:

- Container state (running, exited, paused, restarting, etc.), start/finish
  times, exit code when not running, image identity and observed OOM flag.
  Docker may retain the previous finish time after a new start; a non-null
  finish field alone does not mean the current process has stopped.
- Last recorded next execution time and its log time, never a newly calculated
  promise. A stopped worker's record is INACTIVE even if that time is future.
- Latest retained result (SAVED, SKIPPED, RATE_LIMITED or FAILED), and the last
  retained SAVED marker separately. A later failure never turns into success
  merely because an earlier SAVED record exists.
- Retry-After's logged lower bound is not the next scheduled execution. A
  cooldown skip is not a capture. Missing records remain null/unknown.

Human-readable timestamps and JSON timestamp fields use explicit KST. Docker
query boundaries retain RFC3339 UTC internally. A plan at/past its logged time
has a five-minute observation grace, named DUE_WITHIN_GRACE, **not** an assertion
that collection started. Beyond that, PLAN_OVERDUE requires attention. This is
an operator interpretation threshold, not a changed cron, timeout or retry rule.

Exit codes:

- `0`: a bounded inspection completed with no observed attention flags. This
  does not establish overall health, current CPI, a DB connection or heartbeat.
- `1`: inspection completed but an attention condition is present (inactive
  container, missing/overdue plan, logged failure/rate limit, OOM or unsupported
  CPI log format). A historical rate-limit marker is not live DB gate evidence.
- `2`: inspection unavailable/incomplete, such as access failure, wrong command,
  changed container incarnation, output bound/deadline, clock inconsistency or
  ambiguous event ordering. Do not substitute an older successful report.

## Read-only collection and bounded parsing

Resolve only one local Docker endpoint. A selected context is read once from
local Docker configuration; an explicit DOCKER_HOST is validated before daemon
access. Reject SSH/remote TCP, credentials, DNS hostnames, path/query fragments,
invalid ports and non-loopback IPs. Child processes omit inherited provider
keys, Java/Spring options, Compose/Docker overrides and proxy variables. No
registry credentials are copied or registry/network operations requested.

Validate the selector before running Docker, inspect the chosen container and
require its exact scheduled command and Java entrypoint. Select identity and
state fields at the daemon instead of fetching a full inspect document. Do not
request Env, mounts, labels, health output or State.Error; do not echo rejected
commands, underlying CLI errors, raw log lines or caller arguments.

Pin subsequent calls to the inspected full container ID. Query logs with
`--timestamps --tail 500 --since STARTED_AT --until OBSERVED_AT`. This is only
the retained tail of the **current start**, not a complete attempt history.
Apply the same time bounds defensively in the parser. The tool never falls
back to pre-restart evidence when new-start logs are missing. Inspect the same
ID again after reading: any selected state/image/start/restart-count change
makes the sample unavailable. This closes the common restart/replacement race,
but is not a transactional snapshot of Docker and PostgreSQL.

Each command has a 10-second process deadline with bounded termination/drain
cleanup. Drain stdout and stderr concurrently; terminate the owned CLI process
on total output overflow. Bounds: 4 KiB context, 16 KiB selected inspect, 1 MiB
logs (both streams together). No `--follow`, `--details`, unbounded log query or
raw diagnostic echo. UTF-8 decoding failures make inspection unavailable.

Recognize only the existing ScheduleCpiCommand logger prefix and exact fixed
message shapes. Merge stdout/stderr events by exact RFC3339Nano order; conflicting
same-timestamp events are ambiguous, not arbitrarily ordered. Reject malformed
markers, wrong schedule times, impossible result dates and malformed capture
IDs. Known messages copied under an unrelated logger do not become evidence.
Only closed codes, validated IDs and validated KST timestamps enter output.
The one-second result timestamp tolerance covers the logger's second-precision
KST payload, not arbitrary clock drift. Unrecognized CPI logs are an attention
condition; other application logs are ignored without echoing their content.

## Validation

`scripts/ci/test_cpi_worker_status.py` runs without Docker and tests evidence
states, timestamps, restart races, unknown/secret-bearing lines, exact commands,
local endpoint selection, argument refusal, both-pipe output limits and timeout
handling. Current-source custody adds exactly two pinned files: the inspector
and the disposable acceptance script (43 total CPI paths, 37 additions).
No historical script, workflow or runtime source hash is loosened or replaced.

```text
python scripts/verify-cpi-worker-status.py
```

This explicitly invoked DEMO acceptance reuses ADR-066's owned runtime builder,
isolated networks, tmpfs DB, dummy secret files and HTTPS fixture. It starts one
owned scheduled worker away from the real 23:00 slot; runs the **actual status
CLI** for running/stopped/restarted states; rejects the unrelated fixture
container; and verifies zero HTTP requests and zero captures. It stops the
worker and removes only labelled/owned test resources, preserving existing
development DBs, preview processes and `.env`. Test image preparation may use
public downloads; test-container runtime remains isolated. No worker is left
installed or scheduled on this development PC.

Actual verification results are recorded in IMPLEMENTATION_LOG.md. Remaining
work is separately selected: durable attempt/heartbeat evidence, a protected
operator surface, alert delivery and eventual host supervision. These require
careful DB/liveness semantics and, for delivery/activation, user-selected channel
or real server details. Do not treat this log inspector as having completed them.

## References

- [Docker container logs: timestamps, tail and time bounds](https://docs.docker.com/reference/cli/docker/container/logs/)
- [Docker inspect: selected Go-template fields](https://docs.docker.com/reference/cli/docker/inspect/)
