# ADR-066: Separate CPI worker container and isolated Docker acceptance

Date: 2026-09-08

## Scope

PR #13 merged ADR-065 into develop at
`f583d3ec7d8d2568deb2fb146ed9d47119b399c4`; PR CI #37 passed all four jobs.
The user approved the next step: Docker configuration and local integration
tests without a home server, new key, or actual scheduled collection.

Add the standalone `deploy/cpi-worker/compose.yaml`. It is not an override of
the default development or production model. It uses the reviewed API runtime
image and starts only the explicit `cpi-worker` profile, with
`--wsr-schedule-bls-cpi`, no web server and no published ports. Image pulling is
disabled in this model. The operator must explicitly name an existing image,
database, and two existing networks; there are no production connection defaults.

The worker is UID/GID 10001, read-only, capability-dropped, no-new-privileges,
with init, bounded logs and temporary storage, 512 MiB RAM, 0.5 CPU and 128 PIDs.
Its 40-second stop allowance covers the scheduler's 30-second graceful window.
`restart: "no"` is deliberate while supervision/health/alerting and the actual
home-server resource envelope remain unaccepted. A successful container start
is not proof of a successful collection or current CPI availability.

The worker alone joins the selected private DB network and the separately
selected provider network. The file creates neither network and grants no new
network membership to the web/API. No Docker socket, host directory, DB volume,
provider key environment variable or public ingress is exposed to the worker.
The existing source client still fixes the HTTPS BLS origin; it has no endpoint,
TLS-verification or cron override added for this test.

## Secrets and later server integration

Only individual files are granted as Compose secrets. The BLS file is mounted
as `/run/secrets/BLS_REGISTRATION_KEY`, and the DB password as
`/run/secrets/spring.datasource.password`. Spring uses a configuration tree;
it does not load the repository `.env` or a local profile in this model.

Docker Compose file-backed secrets are bind mounts: setting `uid`, `gid` or
`mode` in the model would not change host-file ownership. On the future Linux
host, provision narrowly readable files for UID 10001 (for example owner 10001,
mode 0400, with protected parent directories). Do not make actual keys
world-readable. This is file delivery, not a managed/encrypted secret vault.

Before actual server activation, the operator must supply and verify:

- `WSR_CPI_IMAGE`: the locally built reviewed API image for the exact release.
- `WSR_CPI_DB_HOST`, `WSR_CPI_DB_NAME`, `WSR_CPI_DB_USER`: the intended DB.
- `WSR_CPI_DB_NETWORK`: the existing private network for that DB.
- `WSR_CPI_PROVIDER_NETWORK`: a separately approved provider-egress network.
- `WSR_CPI_BLS_KEY_FILE`, `WSR_CPI_DB_PASSWORD_FILE`: absolute secret-file paths.

These values belong in an ignored operator file on the actual server, not in
chat or Git. Do not choose a production network merely to make a local test
pass. The existing home-server generation/recovery contract is not changed;
release compatibility, its operation lock, DB backup, egress and secret-file
permissions must be integrated and verified before enabling this standalone
worker against that database. Startup runs Flyway, even before the first 23:00
KST attempt. This change does not install or activate that production path.

## Local disposable rehearsal

Run from the repository root with Docker Desktop's Linux engine ready:

```text
python scripts/verify-cpi-worker.py
```

Requires Python 3.12+, Docker with Compose, Git, OpenSSL and Java keytool. On this
Windows development PC the bundled Python may be used, and the harness locates
Git for Windows' OpenSSL if it is not on PATH. No key or account input is needed.

Image preparation can download public Python/PostgreSQL/Temurin images and
Maven dependencies. **Offline describes the test-container runtime, not those
downloads.** Use the existing secret-excluding API Dockerfile, require its
source inputs to be committed and free of untracked files. Export only those
paths from Git into an owned build context, rejecting archive links, special
files, path traversal and oversized entries. Copy the reviewed ignore rules to
the context's root `.dockerignore` as well, so legacy builders are safe too.
The repository root, `.env`, `.cache` and Git metadata are never build inputs
in the final harness. Label the result with the exact source revision and run
owner. Resolve runtime image IDs
before starting containers. No API source, Maven configuration or Dockerfile
change is required for this slice.

The harness selects and fixes one local Docker endpoint, rejecting remote
hosts. It drops inherited keys, Spring settings, Compose/Docker overrides,
Java options and proxy variables from child processes, and creates a private
Docker CLI configuration without registry credentials. Docker Desktop plugins
are resolved from its installation directory. It creates two uniquely
labelled `internal` networks, a disposable tmpfs PostgreSQL and a test-only
HTTPS fixture with Docker DNS alias `api.bls.gov`. All containers have no host
port bindings and join only those verified networks. The fixture cannot reach
the database; the database cannot reach the fixture's network.
Database readiness waits for the final TCP server, not PostgreSQL's temporary
Unix-socket-only initialization server.

The fixture's ephemeral self-signed certificate is trusted only through a
test-only worker mount/override. Host/browser/system certificate trust is not
changed, hostname verification is not disabled, and the production Compose
model contains no test truststore. The dummy key/password and TLS key are
test artifacts, never copies of the actual root `.env`. The fake server is a
test script, not a provider shipped in the runtime image.

Acceptance exercises the actual packaged client, parser and PostgreSQL:

- Scheduled worker logs its next 23:00 KST slot, sends no startup request,
  stops gracefully, restarts without a catch-up fetch, and stops again.
- Explicit manual mode makes one fixed-origin HTTPS request to the fixture,
  stores exactly the known synthetic response and matching SHA-256, then a
  second manual process is blocked by the persisted cooldown without HTTP.
- An isolated 429 case persists the two-day Retry-After and denies a new
  process without another request; no receipt is created.
- An isolated malformed-response case fails without a receipt and still
  consumes the gate. Separate test databases avoid resetting any rate gate.
  Negative cases require the specific cooldown/rate-limit/validation failure;
  an unrelated startup crash cannot satisfy the acceptance test.
- Actual container image, UID, mounts, resource limits, privileges and network
  membership are inspected. Both stdout and stderr are checked for dummy-key
  or dummy-password leakage. No browser is opened to present fixtures as real.

The fixture payload and final report explicitly say DEMO/not BLS data. The
report does not claim observed CPI or production/public connectivity. Exact
real-time scheduling uses simulated Clock unit tests from ADR-065; this
rehearsal does not accelerate the production cron. To keep startup assertions
honest, it refuses to start the worker within five minutes before its next
23:00 KST slot. Run again after that slot if necessary; there is no long wait
or live collection workaround.

On success/failure, cleanup addresses only enumerated run-owned containers,
networks and the owned runtime image, checking ownership labels first. It does
not prune Docker, delete shared base images, remove existing DBs/volumes, or
touch `.env`, `next-env.d.ts`, the CPI preview or production settings. Temporary
key/trust files are removed only after their containers are gone, with strict
directory ownership and no-link checks. A cleanup failure is a failed result
requiring attention, never silently reported as a pass. Sanitized diagnostics
and a DEMO report remain under ignored `.cache/adr066-<run-id>.log/.json`.

## Contracts and references

Current Python tests validate the worker model, endpoint allowlist, environment
isolation, actual-inspection rejection paths, cleanup ownership, log checks,
the calendar guard and the explicit DEMO response. Current-source CPI custody
adds exactly the Compose file, harness and fixture, not a product-path wildcard.
The existing workflow and all 84 historical script bodies remain unchanged.
Docker acceptance is explicit local work, not silently added to each CI run.
Actual execution results and limits are recorded in IMPLEMENTATION_LOG.md.

- [Docker Compose services and file-backed secret permissions](https://docs.docker.com/reference/compose-file/services/)
- [Compose external/internal network definitions](https://docs.docker.com/reference/compose-file/networks/)
- [Compose secret file delivery](https://docs.docker.com/compose/how-tos/use-secrets/)
