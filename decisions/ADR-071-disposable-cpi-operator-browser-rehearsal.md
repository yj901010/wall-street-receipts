# ADR-071: Disposable CPI operator browser rehearsal

Date: 2026-09-09 (KST)

## Scope

Start from user-merged PR #18, develop `39a0ae4db395afb804b6306645d25832bc0c8d3d`.
Its head passed all four jobs in PR CI #48 (`34312743649`). This slice adds an
explicit test of the unchanged production operator launcher and Next build,
real Spring/Tomcat security/query code and a disposable PostgreSQL database.
It does not activate a real operator, collector, account or home-server service.
No provider key is needed and root `.env` is neither loaded nor copied.

The Spring process runs compiled main classes with a test-only fixed Clock,
not the packaged application JAR. The database rows and bearer are synthetic
DEMO evidence. This is not proof of a deployed host, real CPI delivery, worker
health, production credentials or source replay. The API correctly continues to
label stored ledger evidence UNVERIFIED / PERSISTED_ATTEMPT_RECORDS.

## Ownership and isolation

- `CpiOperatorBrowserIT` is explicitly selected, not an ordinary skipped test.
  Require `wsr.cpi.browser.confirm=DISPOSABLE_DEMO_ONLY` and a matching secret-free
  source mirror beneath this repository's `.cache`. Verify source/configuration
  bytes, source tree inventories and fixtures, reject environment files, and
  rebuild Next before starting the database. Preserve the developer's `.next`
  and `next-env.d.ts`. The dependency junction is only a build dependency link.
- Create a non-reused `postgres:17-alpine` Testcontainer labelled ADR-071, mapped
  to a random port on literal `127.0.0.1`; assert the inspected binding. No
  existing database URL or container name is accepted. Flyway and seeding use
  the disposable owner; Spring uses a separate role with SELECT on only the two
  ledger tables. Verify this role cannot delete attempts or read raw captures.
- Disable CPI collection, SEC public-data access and the fixture analyst startup
  importer. The latter normally writes fixtures during application startup and
  must not run under this deliberately read-only account. Do not grant it writes.
  Assert no collector/importer bean and the reader's actual PostgreSQL identity.
- Assert the real operator API overrides wildcard configuration to loopback.
  Use a random in-memory 32-byte bearer, SHA-256 digest only in Spring settings,
  and separate ephemeral API/UI ports. Start the unchanged `operator/start.mjs`.
  The shared operator API switch still enables existing SEC endpoints; the
  production gateway exposes no SEC routes and this test invokes no SEC mutation.
- Child Node processes receive an allowlisted OS environment, not ambient DB,
  provider, operator credentials, proxy settings or NODE_OPTIONS. Bound build
  and browser phases, watch log size, and retain exact child/descendant ownership
  for cleanup. Never kill by process name or port. Verify the UI port is released.
  Spring and Testcontainers close on success or assertion failure.

## Evidence matrix

Run each phase at 1440, 1280 and 390 pixels, one worker, no retry:

1. Empty persisted history: no automatic fetch; explicit read returns an honest
   empty result, no invented values, unchanged database contents.
2. Seeded history: 24 attempts, six terminal/unknown cases, tied microsecond start
   instants, recent 20 plus hasMore, exact UUID outside the recent window, saved
   capture reference and absent UUID. Check real Spring 401 after a successful
   read removes previous evidence; reject POST and non-CPI gateway paths.
3. Unavailable: revoke only the disposable reader's results SELECT permission.
   Verify actual PostgreSQL failure becomes sanitized 503 and an empty error UI,
   not stale, fixture or raw database error content.
4. Recovered: restore that permission and verify an explicit browser read works
   again without data repair or restart.

Compare complete ordered JSONB snapshots of attempts, results, captures and gate
before/after browser reads; counts alone are insufficient. KST checks separately
retain fixed Clock nanoseconds, stored microseconds, day rollover and retry
lower-bound meaning. Check keyboard outline, contained mobile overflow, no-store,
empty password/storage/cookies, no external browser requests and no page errors.
Only capture DEMO screenshots after password clearing; trace/video are disabled.
The test's 1.1-second pacing respects the actual gateway limiter, not a product
retry, test bypass or production delay change.

## Explicit local invocation (validated Windows development workflow)

Prerequisites: running Docker Desktop, Java 21, installed web dependencies and
Playwright Chromium, Node supporting the existing TypeScript launcher. This
development PC is not the future Ubuntu home server. Do not use real API keys,
copy `.env`, open router ports or activate the actual worker for this test.

From the repository root, prepare a new source mirror without copying secrets:

```powershell
$wsrRoot = (Get-Location).Path
$wsrMirror = Join-Path $wsrRoot ('.cache/cpi-browser-' + [guid]::NewGuid().ToString('N'))
$wsrWeb = Join-Path $wsrMirror 'apps/web'
New-Item -ItemType Directory -Path $wsrWeb -Force | Out-Null
foreach ($wsrPart in @('src', 'operator', 'public')) {
    Copy-Item -LiteralPath (Join-Path $wsrRoot "apps/web/$wsrPart") -Destination $wsrWeb -Recurse
}
foreach ($wsrFile in @('next.config.ts', 'tsconfig.json', 'package.json', 'next-env.d.ts')) {
    Copy-Item -LiteralPath (Join-Path $wsrRoot "apps/web/$wsrFile") -Destination $wsrWeb
}
Copy-Item -LiteralPath (Join-Path $wsrRoot 'fixtures') -Destination $wsrMirror -Recurse
New-Item -ItemType Junction -Path (Join-Path $wsrWeb 'node_modules') -Target (Join-Path $wsrRoot 'apps/web/node_modules') | Out-Null
$wsrNode = (Get-Command node).Source
Push-Location (Join-Path $wsrRoot 'apps/api')
try {
    .\mvnw.cmd '-Dtest=CpiOperatorBrowserIT,CpiBrowserProcessTest' '-Dwsr.cpi.browser.confirm=DISPOSABLE_DEMO_ONLY' "-Dwsr.cpi.browser.web=$wsrWeb" "-Dwsr.cpi.browser.node=$wsrNode" test
    if ($LASTEXITCODE -ne 0) { throw 'Disposable CPI browser rehearsal failed; inspect its owned logs.' }
} finally { Pop-Location }
```

Set `JAVA_HOME` to your installed Java 21 beforehand. The test writes bounded
phase logs to `.cache/adr071-evidence-<unique>/`; Playwright DEMO output stays
under the mirror's `apps/web/.cache/operator-full-stack/`. These are ignored,
not PR artifacts. Do not recursively delete a mirror with a dependency junction;
retain it or remove a separately verified junction before any owned-directory
cleanup. Testcontainers removes its disposable DB; the existing dev DB remains.

## CI and follow-up

The six helper guard tests run in ordinary Maven tests. The explicitly selected
browser IT does not silently join the frozen workflow or require Docker/browser
activation from a new CI job. Pin its four Java and two Web files in the existing
closed CPI custody inventory (82 paths, nine baseline replacements, 73 additions).
No new predecessor exemption, product byte edit or historical body change.

Record measured results and corrections in `IMPLEMENTATION_LOG.md`. Hosted CI
for this candidate still follows push/PR. Actual operator activation, packaged
host rehearsal, scheduling/heartbeat and backup remain separately scoped; ask
for host/process/credential decisions before taking those steps.
