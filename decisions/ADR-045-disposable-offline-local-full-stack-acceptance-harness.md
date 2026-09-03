# ADR-045 — Disposable Offline Local Full-Stack Acceptance Harness

- Status: Accepted
- Date: 2026-08-26

## Context

The repository already proves PostgreSQL-to-Spring-to-Next call-audit wiring in
CI. That job uses a fixed service port and a Playwright-managed development
server, however, so it is not a safe one-command rehearsal on a developer
machine that may already have a database, API, web process, or ignored root
`.env` in use. ADR-044 closes the equivalent gap for the specialized operator
API, but it does not start the production Next.js server or exercise public
product routes.

Before deployment work begins, a developer needs repeatable evidence that the
packaged API, a real PostgreSQL 17 service, the production Next.js build, the
server-only API provider, and a real browser compose correctly without using a
commercial provider or modifying local data.

## Decision

Add one repository-owned PowerShell 7 command:

```powershell
pwsh -NoProfile -File ./scripts/verify-local-full-stack.ps1
```

The script uses the checked-in Maven wrapper, installed Next.js and Playwright
CLIs, the existing `compose.yaml`, the existing three focused call-audit browser
specifications, and the existing canonical DEMO fixtures. It adds no product
route, backend endpoint, database migration, provider, public contract, or
runtime dependency.

### Disposable topology

Every invocation:

1. atomically creates and exclusively holds the same root
   `/.wsr-local-acceptance.lock` as ADR-044, then validates PowerShell 7, the
   Maven wrapper's checked Java 21 runtime, Node.js 24, a local-only Docker
   endpoint with Compose v2, and the installed workspace CLI files;
2. packages the Spring Boot JAR into the harness-owned temporary directory
   unless `-SkipPackage` is supplied;
3. selects three distinct loopback ports, copies only the required web source,
   config, package manifest, and canonical DEMO fixtures into a secret-free
   `apps/web/.wsr-local-full-stack-<run-id>/apps/web` mirror, and builds Next.js
   there in explicit `CALL_AUDIT_PROVIDER=api`,
   `NEXT_PUBLIC_DATA_MODE=DEMO` mode unless `-SkipWebBuild` is supplied;
4. starts a uniquely named Compose project and PostgreSQL 17 volume;
5. starts the packaged JAR on loopback with Flyway and the application bound to
   that same disposable database;
6. starts the production Next.js server on loopback, smoke-checks all 12 primary
   product routes, and runs the existing list/revision/outcome Playwright specs
   once at 1280 pixels; and
7. verifies exact Spring access-log evidence and database cardinality before
   stopping the exact web PID, exact API PID, exact Compose project and volume,
   and removing the validated source mirror plus validated operating-system
   temporary directory before removing the repository lock.

The root `.env`, default Compose project, existing `postgres-data` volume,
developer processes, and developer data are never selected. `.env.example` is
used only as safe Compose interpolation input. The API does not activate the
`local` profile; `SPRING_CONFIG_LOCATION` is fixed to `classpath:/`, so Spring
does not search caller-owned `apps/api/application*`, `apps/api/config/`, or
the root `.env`. It points any application-owned local-env import at a
nonexistent harness path. Inherited Spring, server, management,
datasource/Hikari, JNDI, direct-provider, and logging namespace variables are
removed before the exact acceptance allowlist is applied.

The normal Next build can rewrite `next-env.d.ts` and `tsconfig.json`. A default
harness run therefore never builds from the caller's web directory: only the
explicit allowlist above is copied, `.env` and `.env.local` are never copied,
and Next may mutate only files inside the ignored source mirror. The mirror is
validated as a non-reparse-point direct child of `apps/web` before recursive
cleanup. The original `apps/web/next-env.d.ts`, `apps/web/tsconfig.json`, and
standard ignored `.next` remain untouched. `apps/web/tsconfig.json` excludes
the mirror name so an editor or manual type-check does not ingest its duplicate
source. An explicit `-SkipWebBuild` run instead opts into read-only reuse of the
caller's existing standard `.next` output.

Default packaging likewise uses a harness-selected Maven build directory under
the validated operating-system temporary root. The checked-in POM retains
`apps/api/target` as its normal default. The two acceptance scripts share one
atomic root lock across package/build/run/cleanup, so participating ADR-044 and
ADR-045 invocations fail fast instead of racing, including across path aliases
and operating-system login sessions. Arbitrary manual Maven or Next commands do
not participate in that lock; default harness-owned build outputs and the web
source mirror keep their artifacts untouched. A hard-terminated owner
deliberately leaves the ignored lock file behind so a later run fails closed
until the operator has inspected orphaned processes and Docker resources.

### Offline and source boundary

The Spring process uses deterministic fixture providers to import synthetic
DEMO records into the disposable PostgreSQL database. The web process is a
different boundary: it must use the private server-only Spring origin selected
by `CALL_AUDIT_PROVIDER=api`; it may not use its own call fixture provider or
fall back to it. Build, production runtime, and browser-test child environments
explicitly force every other current web selector—market, analyst, S&P history,
market board, methodology, institution directory, analyst directory, market
map, and market treemap—to `fixture`, so caller variables and `.env.local`
cannot change the offline route sources. Build, web runtime, and Playwright
also clear inherited Node module injection and every observed case variant of
the HTTP, HTTPS, all-proxy, and no-proxy variables.

The harness forces the SEC provider, SEC live-smoke flag, and operator API off.
The disabled SEC origin is additionally set to closed loopback. It clears any
browser-public API-origin variable so the browser cannot receive the Spring
origin. No domain, DNS change, API key, SEC account, contact email, OAuth
client, operator token, paid plan, or user secret is required or accepted.

The production app marks its locale preference cookie `Secure`. Because this
acceptance listener is deliberately local HTTP rather than TLS, the focused
browser process uses an explicit test-only flag to inject only the non-secret
locale preference into its isolated browser context. Normal Playwright runs do
not set that flag and continue to verify the real locale server action. The
focused browser navigates the exact numeric `127.0.0.1` origin, clears inherited
proxy variables, and launches Chromium with `--no-proxy-server` so loopback
navigation cannot leave the host through a corporate proxy.

### Acceptance contract

The command succeeds only when all of the following hold:

- PostgreSQL reports healthy and Spring `/actuator/health` reports `UP`;
- the production Next.js server returns `200 text/html` with the shared product
  shell for 12 primary routes;
- the three focused Chromium scenarios pass against the externally managed
  production server;
- API-only list metadata renders as `NOT_EXPOSED` rather than fixture metadata;
- the browser sends zero requests to the configured private API origin;
- Tomcat records the required five list reads and eight
  detail/context/revision/outcome reads with HTTP 200; and
- PostgreSQL contains exactly three calls, two revisions, and four outcomes.

Access-log verification is set membership, not an order or total-request-count
claim, because navigation and prefetch timing may vary.

### Failure and cleanup behavior

Another participating acceptance run, a stale lock from a hard-terminated
owner, missing prerequisites, a remote Docker endpoint, build failure, startup
timeout, unexpected route or browser behavior, missing access evidence,
unexpected database counts, or cleanup failure makes the command exit nonzero.
Failure logs are limited to sanitized tails and redact the disposable database
password.

Process cleanup targets only the directly owned child PIDs. Compose cleanup is
allowed only for a project name matching the harness-owned random format, and
recursive filesystem cleanup is allowed only for the validated harness
directory beneath the operating-system temporary root and the validated source
mirror directly beneath `apps/web`. No broad process, container, volume, or
filesystem search is used for deletion. Each directory is removed only after
this run has successfully created it; an exact-name pre-existing path is never
adopted or deleted.

## CI boundary

CI statically guards the shared atomic lock, harness-owned build outputs,
secret-free source mirror, exact web provider selectors, topology,
loopback/offline settings, API-mode source selection, focused browser matrix,
exact access
evidence, ownership checks, and cleanup markers, then parses the PowerShell AST. CI does not execute this extra
local composition command because the existing call-audit integration job
already owns deterministic PostgreSQL/Spring/Next/browser behavior.

## Operator requirements

The command needs PowerShell 7, Java 21, Node.js 24, a running Docker daemon,
installed workspace dependencies, and Playwright Chromium. If dependencies or
Chromium are missing, install them before retrying:

```powershell
pnpm install --frozen-lockfile
pnpm --dir apps/web exec playwright install chromium
```

The selected Docker context or `DOCKER_HOST` must use a local unix socket,
Windows named pipe, file-descriptor transport, or numeric loopback TCP endpoint.
Remote Docker daemons are intentionally rejected before contact. Once
validated, that exact endpoint is captured in `DOCKER_HOST` and used for
`docker info` plus every Compose version/start/inspection/cleanup call; later
context changes cannot redirect the run.

The first run may download ordinary Maven dependencies or the PostgreSQL image
when they are not cached. It is not authorized to contact SEC or another market
data provider.

## Consequences

- A developer now has one command for the production-build public full-stack
  acceptance gap while ADR-044 remains the specialized operator-boundary gate.
- The default command is intentionally slower than unit tests because it
  packages, builds, starts real processes, launches Chromium, and inspects a
  real database.
- Port reservation and later binding cannot be atomic. A collision fails
  closed, cleans only owned resources, and a retry selects new ports.
- ADR-044 and ADR-045 cannot run concurrently in the same repository checkout;
  the second command fails before package/build/Compose work and can be retried
  after the first removes the shared lock.
- This decision verifies local DEMO composition. It does not approve hosting,
  TLS, authentication, licensed ingestion, live publication, or deployment.
