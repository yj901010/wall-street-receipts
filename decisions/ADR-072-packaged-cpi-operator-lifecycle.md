# ADR-072: Packaged CPI operator lifecycle rehearsal

Date: 2026-09-09 (KST)

## Scope and prerequisites

Start from user-merged PR #19 / develop `825369a8d75c2f5608d6782620ebc06b072ffb42`.
Its head `8288ded` passed all four jobs of PR CI #50 (`34318957553`). ADR-071
covered real Spring/DB/browser behavior with an in-process test Clock. This step
checks the actual Linux runtime images, real system Clock and process lifecycles.
It does not activate the future home server or expose an operator to the internet.

The user started Docker Desktop when asked. Require a local Linux Docker engine,
Python 3.12+, Git and Docker CLI. The public base-image/package build stage may
download dependencies; runtime containers have only an owned internal network.
No BLS key, real operator token, domain, router rule or existing DB is required.
No business/provider licensing decision is made or bypassed by this test.

## Packaging

Keep the existing public Web/API Dockerfiles and production Compose unchanged.
`deploy/cpi-operator/Dockerfile` extends an explicitly selected, locally built Web
runtime with only the production operator launcher, gateway and closed CPI DTO
adapter. The default command is `node operator/start.mjs`, non-root, SIGTERM.
There is no new authentication scheme, operator endpoint or public bind switch.

The actual Linux duplicate-bind rehearsal found a launcher defect: Next's
installed exception handlers logged EADDRINUSE but the process exited 0. Catch
startup failure explicitly, set exit code 1, emit only a fixed sanitized marker
(plus the allowlisted EADDRINUSE reason), and bound renderer cleanup to five
seconds. Allow natural exit/output flushing after cleanup; the unreferenced
deadline remains a backstop for leftover handles. No success marker or loopback
readiness flag is set on failure. Four isolated Node process tests also exercise
prepare failure, rejected cleanup and hung cleanup under a test-only Next stub
that reproduces the framework's swallowing exception handlers. That stub is not
part of the operator runtime image.

The rehearsal exports committed-only API and Web input trees into a new owned
`.cache/wsr-cpi-lifecycle-<random>` context. It never gives Docker the repository
root, `.env`, host `node_modules`, developer `.next` or dirty generated declaration.
Uncommitted/untracked application input is rejected. The new packaging recipe is
tested as current working bytes; the report distinguishes its normalized SHA-256
from the embedded application's Git commit and records all built image IDs.

The operator image is a packaging building block, not a ready-to-publish service.
Its inherited image metadata may list the public Web port, but neither the
launcher nor this rehearsal publishes that port. Ordinary public Next stays
default-denied for `/operator/cpi`. Actual remote operator access still requires
a separately reviewed host/access design; do not relax the loopback guard.

## Disposable acceptance and ownership

- `scripts/verify-cpi-operator-lifecycle.py --confirm-disposable-demo` is explicit
  local acceptance, not an automatic CI job or long-running daemon. No arbitrary
  image, database, endpoint or target argument is accepted.
- Reuse the existing reviewed archive, endpoint and environment safety helpers.
  A private Docker CLI configuration contains no registry credential store.
  New owned resources have a random name and exact lifecycle ownership label;
  record ownership before creation, verify it before logging/removal. Commands
  have deadlines/output limits; sanitize synthetic credentials in diagnostics.
- A new PostgreSQL 17 container uses tmpfs, no persistent volume and no host port.
  A temporary owner process applies the actual packaged Flyway migrations with
  fixture bootstrap and providers disabled. Stop it before creating the real
  operator API process with SELECT-only access to the two attempt ledger tables.
  Assert the API's database session identity and actual denial of DELETE/raw reads.
- The API container is attached only to the owned internal network. The operator
  joins the API's network namespace so both retain their production literal
  loopback listeners. The test-only Node probe runs via `docker exec` inside that
  namespace; it is not baked into either production runtime image. No host port,
  Docker socket mount, privileged container, host network or proxy is introduced.
- API and operator use non-root users, read-only root filesystems, dropped
  capabilities, no-new-privileges, init, resource/log limits and no automatic
  restart. Disposable credentials are read-only files; the random bearer only
  reaches the test probe via stdin, never container environment or command args.
- The synthetic three-row ledger includes UNKNOWN, FAILED and RATE_LIMITED.
  Compare complete ordered snapshots of attempts/results/captures/gate across
  every lifecycle. No seeded row or retry timestamp is represented as live data.

## Required scenarios

1. Actual packaged shell and authenticated query succeed. Reuse the production
   DTO validator; verify KST microseconds, real authentication, SEC proxy denial,
   no-store and no token reflection. Inspect `/proc/net/tcp*` for actual loopback
   listeners, not just intended configuration.
2. A second operator on the same port exits with EADDRINUSE and cannot adopt or
   replace the original listener. Check the specific reason, not any failed exit.
3. SIGTERM stops the operator without timeout/SIGKILL/OOM, releases the port and
   leaves the API intact. Start that same container again; require a distinct
   process start timestamp and identical returned evidence.
4. Stop the API while the operator remains alive: the operator must return a
   sanitized 503, not old or substitute rows. Then stop the UI, start the API,
   wait for readiness and start the UI in the API's new namespace. Do not assume
   a running namespace-sharing sidecar automatically joins a recreated namespace.
5. Verify identical data after restart and final normal shutdown. Capture bounded
   sanitized logs even on failure, then remove only verified owned containers,
   network, tagged images and scratch context. Retain ignored logs/JSON report.

Docker's [stop command](https://docs.docker.com/reference/cli/docker/container/stop/)
has a grace timeout; merely seeing an exited container does not prove graceful
shutdown. Reject forced-kill exit 137, OOM and the wrong process incarnation.
The API may finish with 0 or SIGTERM's 143; require the Node operator's normal 0.

## Running and interpreting the result

From repository root:

```sh
python -B scripts/verify-cpi-operator-lifecycle.py --confirm-disposable-demo
```

Have `postgres:17-alpine` locally available. The other public base images and
dependencies can be fetched by the isolated builders. Do not supply real secrets
or run production Compose. A failed report is not partial deployment success;
inspect the owned sanitized log. If cleanup cannot establish ownership, stop and
report the exact remaining owned names rather than deleting broad resources.

The probe is HTTP/process acceptance, not another Playwright rendering pass or
proof of an Ubuntu laptop, reboot recovery, backup restore, scheduled collection,
provider reachability or CPI freshness. ADR-071's three-width browser coverage
remains separate. Record measured results and corrections in IMPLEMENTATION_LOG.
After push/review/CI, decide the real operator access/process model before any
host activation; request the necessary host and credential details at that time.
