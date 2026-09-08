# ADR-065: Opt-in daily CPI worker at 23:00 KST

Date: 2026-09-08

## Decision and source timing

The user approved preparing automatic CPI collection and delegated the choice
of time. Choose **daily 23:00 Asia/Seoul** (14:00 UTC), including weekends.
Do not base this on the host's timezone or a fixed US timezone offset.
The website remains KST; stored instants and source request years remain UTC.

The official BLS calendar currently schedules CPI at 08:30 US Eastern, which
converts to 21:30 KST during US daylight saving time and 22:30 otherwise.
23:00 KST leaves 90/30 minutes respectively. This is a polling policy, not a
release-time subscription or a freshness guarantee. Check timing again if BLS
changes its publication policy; do not hardcode monthly release dates.

BLS's API features page retains a historical one-day availability-lag notice
under its version-1 addendum. That is a reason **not to promise immediate v2
availability**, not measured evidence that every current v2 release is delayed
exactly 24 hours. If the response still contains the previous reference month,
save it truthfully with its retrieval timestamp. Never infer a new monthly value
or label a successful request as proof a new CPI release was ingested.

Official sources checked 2026-09-08:

- [2026 BLS calendar and Eastern-time note](https://www.bls.gov/schedule/2026/home.htm)
- [CPI release schedule](https://www.bls.gov/schedule/news_release/cpi.htm)
- [BLS API features and historical lag notice](https://www.bls.gov/bls/api_features.htm)
- [Spring CronTrigger completion-based, non-overlapping semantics](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html)

## Executable boundary

Add `--wsr-schedule-bls-cpi` to the packaged Java application. Like the manual
`--wsr-collect-bls-cpi`, it must be the sole argument. Mixed modes, unknown
suffixes, values and extra arguments fail before Spring starts. This is an
explicit long-lived headless worker, **not a scheduler inside the API server**.
Normal web/API startup, enabled CPI reads and page visits do not schedule work.
Changing a Spring profile alone does not register a recurring task.

`ScheduleCpiCommand` starts a separate context and one `ThreadPoolTaskScheduler`
with the injected Clock. The exact cron is `0 0 23 * * *` with `Asia/Seoul`.
There is no startup retrieval: the first attempt is the next future slot. Use
completion-based lenient cron, not fixed execution/replay. Missed slots during
downtime are skipped; restarting at/after 23:00 waits for the next future slot.
Shutdown cancels queued future work and allows up to 30 seconds for an in-flight
attempt. No daemon/service/task is installed or enabled by building this code.

`CpiCollectionJob` is the shared manual/scheduled one-attempt implementation.
Both modes use the existing fixed BLS client, response validation, DB gate,
UTC microsecond timestamps and append-only repository. Scheduler exceptions
are contained so a failed attempt does not cancel tomorrow's task. Log only
fixed status codes, generated capture IDs and KST timestamps; never exception
messages/causes, keys, source payloads or JDBC connection strings.

The 15-minute autocommitted DB gate remains authoritative across manual and
scheduled processes using the same DB. A 429 persists Retry-After, with the
existing 24-hour minimum. No immediate retry or cooldown reset is added: at the
next daily slot, an active gate means SKIPPED, not SAVED. Scheduling jitter can
make a next-day attempt just earlier than the stored deadline, in which case
it is intentionally skipped until a later daily slot. A longer backoff can skip
multiple days. Separate databases still do not share a provider budget.

Successful repeated responses each append a distinct retrieval receipt. No
automatic deduplication, overwrite, deletion, retention purge or vacuum tool is
added. Disk usage grows and must be measured during server acceptance. Existing
backup retention policy is separate from CPI receipt retention.

## Operator handoff — not active on this development PC

No new account or key is needed. The existing ignored root `.env` contains the
BLS key for explicitly approved local work. This change does not read it or
send a real BLS request. Do not paste credentials in chat or command arguments.

For a later explicitly approved run, build with Java 21:

```text
./mvnw -B -ntp verify
java -jar target/wall-street-receipts-api-0.0.1-SNAPSHOT.jar --wsr-schedule-bls-cpi
```

Run these from `apps/api`; Windows uses `.\mvnw.cmd`. Supply the same **explicitly
verified** database connection as the read API and `BLS_REGISTRATION_KEY` through
the server environment or Spring configuration-tree file secret. For local-only
use, `SPRING_PROFILES_ACTIVE=local` loads `../../.env`, as documented in ADR-064.
Starting the worker initializes Flyway on that selected database before waiting;
it is not a read-only preview. Back up a non-test DB first. Stop the foreground
worker with Ctrl+C; do not launch it merely to see the schedule.

Startup logs `BLS_CPI_SCHEDULE_READY` with the no-catch-up/append-only policy.
`BLS_CPI_SCHEDULE_WAITING` logs the executor's next planned time in KST at
registration and after attempts, without a separate clock-boundary estimate.
This is only a plan while that process remains
healthy. The scheduled attempt logs one of `BLS_CPI_CAPTURE_SAVED`,
`BLS_CPI_SCHEDULE_SKIPPED`, `BLS_CPI_SCHEDULE_RATE_LIMITED` or
`BLS_CPI_SCHEDULE_FAILED`. Inspect stored receipts to verify actual ingestion.
No persistent attempt history, alarm delivery or worker health endpoint is
claimed. The page keeps its actual receipt time and retrieval-age warning.

The future Ubuntu 24.04 home server is not this PC. Production Compose and its
API/web egress restrictions are unchanged. A separate worker container, secret
file provisioning, DB/network selection, single-worker supervision, failure
alerts, disk monitoring and restart/backup acceptance require a later server
integration step. Do not grant the web/API internet access to run this worker.
No systemd/cron/Windows Task Scheduler entry, recurring Codex automation,
public ingress, release, remote upload or deployment is created here.

## Verification

Current Java tests cover both CLI guards, startup isolation, exact cron/zone,
US DST transition dates, leap/year boundaries, injected Clock independent of
host timezone, restart/no-catch-up behavior, future-task cancellation, distinct
status logs without secret-bearing exceptions, and the unchanged one-shot
command's context closure. PostgreSQL tests exercise headless wiring without
a live request, immutable successive receipts and durable backoff after restart.

The closed CPI custody inventory grows from 31 to 38 exact paths. Only two
pinned ADR-064 committed predecessor objects are accepted before committing;
current reviewed working bytes remain mandatory. The baseline, historical
workflow, 84 historical script bodies and web sources remain unchanged.
Actual results and remaining verification boundaries belong in IMPLEMENTATION_LOG.md.

Follow-up: [ADR-066](ADR-066-isolated-cpi-worker-container.md) adds the standalone
opt-in Docker model and a disposable synthetic HTTPS/PostgreSQL rehearsal.
It does not activate the worker on this PC or the future home server.
