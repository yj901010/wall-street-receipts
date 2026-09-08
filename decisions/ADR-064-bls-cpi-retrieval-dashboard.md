# ADR-064: Stored BLS CPI retrievals and a monthly dashboard

Date: 2026-09-08

## Scope and accuracy boundary

Implement `/market/cpi` (Korean and English SSR) and read-only
`GET /v1/macro/cpi`. The existing `/market` links to it; the global market board
remains its separate DEMO/unpublished surface. No quotation, option, futures,
analyst outcome, historical point-in-time scoring, or deployment claim is added.

Only BLS CPI-U US city average NSA series `CUUR0000SA0` and `CUUR0000SA0L1E`
are collected. Index base is 1982–84 = 100. Request the current UTC year and
three preceding calendar years. A retrieval is not the original release vintage;
publication time is not supplied. Preserve exact source bytes and footnotes,
UTC retrieval time, requested years, UUID and SHA-256. Display timestamps in KST;
reference months are calendar labels, not instants converted between time zones.

BigDecimal calculates `(current - priorYearSameMonth) * 100 / priorYearSameMonth`
with one final HALF_UP rounding to one decimal place. Missing source indices
(`-`), absent months and absent same-month year comparisons stay unavailable.
The web does not recalculate returns, interpolate gaps, or invent numeric defaults.
The table enumerates 24 calendar months. A seven-day retrieval-age warning is
not a prediction of the release calendar or proof that newer data exists.

## Collection and persistence

`java -jar … --wsr-collect-bls-cpi` is an explicit headless one-shot command.
Ordinary API startup and browser visits cannot trigger it. No scheduler or public
write endpoint is added. The BLS key is server environment only, never a URL,
browser variable, persisted request body, receipt field, or log message.

The fixed HTTPS endpoint is `https://api.bls.gov/publicAPI/v2/timeseries/data/`.
One POST, no redirect or automatic retry, 5-second connect and 20-second request/
body deadline, 1 MiB streaming response limit. Reject duplicate JSON keys, invalid
UTF-8, trailing content, unknown fields/series, BLS warnings/failures, annual rows,
duplicate/future months and malformed values. Errors never echo vendor bodies.
429 headers are handled before reading the body; the durable cooldown honors
Retry-After with a conservative minimum of 24 hours (invalid/missing: 24 hours).

Flyway V10 adds `bls_cpi_captures` and `bls_cpi_collection_gate`. Earlier migrations
remain byte-identical. The autocommitted atomic gate permits at most one request
per 15 minutes across processes sharing a database, including after failures.
Do not call the collector inside an outer transaction or reset the gate to retry.
Separate databases do not share a rate budget. The repository has append/read
operations only; a database administrator could still alter records. Every read
replays the strict parser and checks the raw-byte SHA-256. This is integrity
checking, not cryptographic proof against a privileged database administrator.

Latest means the newest stored capture no later than the injected Clock, with
UUID as a stable tie-breaker. All responses carry explicit `OBSERVED_MONTHLY`,
`RETRIEVAL_VINTAGE_ONLY`, `NOT_PROVIDED` release-time status and calculation policy.
No query parameters are accepted. Replies are no-store. The server-only web
transport has a 5-second deadline and 256 KiB bound, rejects redirects and invalid
metadata, and never substitutes a fixture. Production supports only `disabled`
(default) and `api`; synthetic data exists solely in tests and renders as DEMO.

## Local operator instructions (not deployment)

No additional paid service or key is required beyond the existing
`BLS_REGISTRATION_KEY` in the ignored repository-root `.env`. Do not put it in
`NEXT_PUBLIC_*`, commit it, paste it in chat, or pass it as a command argument.

1. Start Docker Desktop and the project's local PostgreSQL (or an explicitly
   selected test database). Confirm the intended `POSTGRES_HOST`, `POSTGRES_PORT`,
   `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` before running a migration.
   This command applies V10 to the selected database; back up a non-test DB first.
2. From `apps/api`, build with Java 21: `./mvnw -B -ntp verify` (Windows:
   `.\mvnw.cmd -B -ntp verify`). The verification requires Docker for PostgreSQL.
3. From `apps/api`, set `SPRING_PROFILES_ACTIVE=local` in the **collector terminal**
   so `application-local.yml` loads `../../.env`. Run:
   `java -jar target/wall-street-receipts-api-0.0.1-SNAPSHOT.jar --wsr-collect-bls-cpi`.
   A successful run prints only the saved receipt ID and UTC receipt time.
   Cooldown, provider warnings and malformed responses fail without replacement.
4. In the API process environment set `APP_CPI_ENABLED=true` and the same DB
   connection. Bind it to loopback for local testing. It does not need the BLS key
   or the `local` profile when DB connection values are supplied directly.
5. In the web process environment set `CPI_PROVIDER=api` and
   `API_BASE_URL=http://127.0.0.1:8080` (adjust to the actual local API port).
   Restart the web and visit `/market/cpi`. There is no browser-side key setup.
   Disabled: no connection configured; empty: no receipt or API flag off;
   error: transport/validation failed. None is presented as observed data.

These settings are not committed defaults. Existing deployment containers,
network egress policy, home-server configuration and backup/release flows are
not enabled or modified. A recurring collector, release-vintage sources,
retention policy and home-server scheduling remain later operator decisions.

## Official references checked

- [BLS API v2 signature](https://www.bls.gov/developers/api_signature_v2.htm)
- [BLS API FAQ](https://www.bls.gov/developers/api_faqs.htm)
- [API terms](https://www.bls.gov/developers/termsOfService.htm)
- [BLS reuse policy and attribution](https://www.bls.gov/bls/linksite.htm)
- [CPI seasonal adjustment](https://www.bls.gov/cpi/seasonal-adjustment/questions-and-answers.htm)

Retain the required BLS disclaimer on the page; no BLS logo or endorsement.
The API has registration/rate limits and registration renewal requirements;
free access does not mean unlimited requests. BLS API success is not evidence
that BEA/EIA or stock/derivatives sources are connected.

## Verification and custody

The exact source inventory in `scripts/ci/cpi_contracts.py` covers runtime files,
V10, focused tests and the specific V9→V10 expectation updates. Original V1–V9
inventory identities are retained and V10 is pinned separately. Current-source
mutation tests run before the frozen tree comparison; all CPI paths are included
in before/after custody. No general application-path exemption is added. The
84 historical bodies, pinned baseline and workflow are unchanged; current Java,
Vitest and Playwright jobs exercise the changed checkout.

Actual local results and any limitations are recorded in `IMPLEMENTATION_LOG.md`.

Follow-up: [ADR-065](ADR-065-opt-in-kst-cpi-schedule.md) adds an explicitly
started, separate daily 23:00 KST worker. It does not enable collection in the
ordinary API/web process, install a scheduler, change the existing deployment
network, or make a release-time availability guarantee. This ADR's one-shot
command and source/persistence boundaries remain applicable.
