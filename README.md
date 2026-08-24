# Wall Street Receipts

Wall Street Receipts is a point-in-time financial research product that records
public analyst calls, preserves the market context that was available when each
call was made, and evaluates later outcomes with a reproducible methodology.

The P0 foundation and P1 domain/fixture phase are complete, broader P2 work
remains open, and the first eight isolated P3 slices are complete: the target-hit
comparison core, schedule-only session-offset mechanics, the policy-neutral
event/session relation classifier, and the pure directional-win comparison
core, plus the strict session-close named-horizon and independent
original/correction schedule-lineage policy and the approved five-direction
polarity reduction that preserves neutral as non-directional rather than a
loss, and the disconnected mechanical adapter that translates the two common
directional sides into the existing target-hit and directional-win side enums
without invoking either calculator, followed by closed full-polarity routing
that preserves either directional calculator-side evidence or explicit
non-directional evidence. The ninth and tenth P3 contracts are complete as
disconnected source-local leaves: point-in-time selection of one
official primary-venue endpoint close, followed by exact target error using the
actual endpoint price as denominator with scale-12 half-even rounding. Neither
is wired to fixtures, persistence, an API, a provider, or the web. The broader
P3 scoring phase remains open.
Delivered P2 work includes
the completed coherent analyst-call list/detail consumers, evidence directories,
maps, market publication state, recorded S&P call history, and the honest
known-deferred Screener shell; broader P2 remains open and actual screening
remains P8 work. P1 provides a
canonical analyst-call ledger,
source evidence, immutable point-in-time market and macro/event context,
list/detail APIs, and responsive web routes. The analyst-call ledger and each
call-detail audit can now read through private Spring API transports; each
detail-page aggregate keeps its call, context, and append-only correction/
cancellation lineage together without mixing API and fixture facts.
Outcome records preserve audit, methodology, and input lineage without claiming
P3 scoring results. Kafka, Redis,
ClickHouse, OpenSearch, object storage, and commercial data providers remain
later-phase extension points rather than runtime dependencies.

> All bundled records use `DATA_MODE=DEMO`. They are synthetic examples, not
> investment advice or representations of real analyst statements.

## Prerequisites

- Node.js 24 LTS and Corepack/pnpm
- Java 21
- Docker with Docker Compose v2

## Start locally

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
corepack enable
pnpm install --frozen-lockfile
$env:CALL_AUDIT_PROVIDER = "api"
$env:API_BASE_URL = "http://localhost:8080"
pnpm --dir apps/web dev
```

In a second PowerShell terminal, start the API:

```powershell
Set-Location apps/api
.\mvnw.cmd spring-boot:run
```

On macOS or Linux, use `cp .env.example .env`, start the web process with
`CALL_AUDIT_PROVIDER=api API_BASE_URL=http://localhost:8080 pnpm --dir apps/web dev`,
and use `./apps/api/mvnw spring-boot:run` instead. The default local endpoints
are:

- Web: <http://localhost:3000>
- API: <http://localhost:8080>
- PostgreSQL: `localhost:5432`
- Analyst calls: <http://localhost:3000/calls>
- Coherent call-detail audit: <http://localhost:3000/calls/demo-call-002>
- Analyst identities: <http://localhost:3000/analysts>
- Institution identities: <http://localhost:3000/institutions>
- Methodology registry: <http://localhost:3000/methodology>
- S&P map: <http://localhost:3000/maps/sp500>
- Nasdaq map: <http://localhost:3000/maps/nasdaq100>
- Screener availability shell: <http://localhost:3000/screener>
- Analyst-call API: <http://localhost:8080/v1/calls>
- Revision audit API: <http://localhost:8080/v1/calls/demo-call-002/revisions>
- Outcome audit API: <http://localhost:8080/v1/calls/demo-call-001/outcomes>

The explicit web-process environment above selects `CALL_AUDIT_PROVIDER=api`.
Consequently, `/calls` reads one canonical list page and `/calls/[id]` reads the
canonical detail, context, and revision resources from Spring through the
server-only `API_BASE_URL`. The browser never calls that origin directly. The
existing list API does not expose fixture dataset as-of, provenance, disclaimer,
or complete facets, so API list mode marks those dataset-level values as not
exposed instead of mixing in fixture metadata or inferring coverage from one
page. Set `CALL_AUDIT_PROVIDER=fixture` only when deliberately running the call
ledger and complete detail audit offline; an API error never falls back to
fixture data, an empty page, or an empty lineage.

The completed list-consumer slice adds no product route or backend contract.
`/calls` uses a page-scoped provider selected by the same exact
`CALL_AUDIT_PROVIDER` value as detail. API mode performs one private list read;
fixture mode is an explicit whole-page alternative, never an automatic fallback.
Returned-page capture/provenance remains separate from unavailable dataset-wide
metadata, and opaque identity filters keep the existing API's exact-case rules.

This is the first real web-to-application-API connection, but it is not yet a
commercial market-data connection: Spring still imports the repository's
synthetic DEMO fixtures into PostgreSQL. Licensed provider ingestion remains a
separately reviewed P5 boundary.

The completed `feature/p2-call-outcome-audit-api` slice extends the same
whole-page call audit with the existing read-only
`GET /v1/calls/{id}/outcomes` resource. Its delivered contract is deliberately an
audit consumer, not a scoring release: the current canonical records remain
ordered, append-only `PENDING`/`INCOMPLETE` DEMO audit records whose ten metric/result
fields are JSON null. The page must show each record or an explicit known-empty
array without choosing a latest/effective result, inferring an exclusion from a
cancellation, or combining API evidence with fixture data. Calculated outcomes,
endpoint observations and returns, cancellation eligibility, lifecycle
projection, aggregation, ranking, and real/licensed publication remain later
P3/P5 work. This paragraph
records a verified synthetic DEMO boundary and does not infer methodology
activation status or claim observed/live performance.

Stop the database without deleting its volume:

```powershell
docker compose stop postgres
```

## Verify changes

Run the same checks used by CI:

```powershell
pnpm --dir apps/web lint
pnpm --dir apps/web test
pnpm --dir apps/web build
Set-Location apps/api
.\mvnw.cmd -B verify
```

Validate the Compose configuration separately with:

```powershell
docker compose --env-file .env.example config --quiet
```

## Fixture contract

Canonical demo data lives under [`fixtures/v1`](fixtures/v1). Every fixture has
a schema version, fixture version, generation timestamp, `DEMO` data mode, and
provenance. Missing values remain JSON `null`; presentation layers render them
as `NA` and must never coerce them to zero or invent a value.

The P1 outcome fixtures are model-only audit records. Their financial metrics
and result booleans are deliberately `null`; deterministic return, alpha,
target, and MFE/MAE calculations belong to P3 and require golden tests.

The completed first P3 slice is a pure target-hit comparison core over explicit
test inputs. It is not connected to canonical fixtures, stored outcomes, APIs,
or a runtime scoring schedule, and it does not reinterpret either model-only
methodology as an implemented formula.

The completed P3 session-offset slice accepts only a caller-selected anchor,
positive count, explicit ordered test-session catalog, and evaluation as-of.
It does not define D1/W1/M1/M3/M6/Y1, infer calendars, fetch observations, or
publish an outcome, schema, fixture, API, database, provider, or web surface.

The completed P3 relation slice classifies one caller-supplied event instant
against an explicit test-session catalog. It preserves open, interior, close,
gap, touching-boundary, and coverage states without choosing an anchor, call or
correction basis, horizon, observation, or product surface.

The completed fourth P3 directional-win slice compares only an already
interpreted bullish/bearish side with one caller-supplied signed asset return.
Bullish uses strict `> 0`, bearish uses strict `< 0`, and zero is false for both;
a null return remains explicitly unavailable. This leaf does not calculate a
return, select a horizon or observation, activate a methodology, modify the
canonical outcome archive's null metric/result fields, or publish a result.

The fifth P3 slice uses an explicit original or correction basis and
the supplied session catalog only. It maps D1/W1/M1/M3/M6/Y1 to exactly
1/5/21/63/126/252 session closes strictly after that basis event, with each
validated correction starting an independent clock while the original lineage
is preserved. Its exact 633-byte policy definition and SHA-256 are versioned;
the result identifies a schedule endpoint only and does not claim a licensed
calendar, observed price, calculated return, complete outcome, or methodology
activation. It requires no provider credential or network access.

The completed sixth P3 slice maps the canonical five call directions through
one versioned policy: strong and ordinary bullish directions become directional
`BULLISH`, strong and ordinary bearish directions become directional `BEARISH`,
and `NEUTRAL` becomes explicit `NonDirectional(NEUTRAL_DIRECTION)`. The original
source direction remains in the policy context. Neutral is not false, a loss,
missing evidence, or an excluded outcome. This disconnected leaf invokes no
calculator or runtime surface and requires no provider credential or network
access.

The completed seventh P3 slice is one mechanical adapter with exactly two
static methods. Common `BULLISH` and `BEARISH` map one-to-one into the existing
target-hit and directional-win side enums. It accepts no neutral/full polarity
result, invokes no calculator, and owns no policy version, hash, market input,
provider, or runtime connection. Null is rejected rather than treated as
neutral. No API key, account, provider, paid plan, market calendar, price feed,
data license, or network access is required.

The completed eighth P3 slice routes one complete polarity resolution without
reinterpreting it. Directional results preserve their original policy result
and add both exact calculator-side enum values through the completed adapter;
neutral preserves only the original `NonDirectional(NEUTRAL_DIRECTION)` record
and gains no side or Boolean. The route constructs no calculator input, invokes
no calculator, owns no new version/hash, and has no runtime or product
connection. No API key, account, provider, market calendar, price feed, data
license, or network access is required.

The completed ninth P3 slice selects one endpoint close only from explicit
point-in-time evidence. V1 requires a resolved ADR-010 session endpoint, catalog
and asset/primary-venue/source binding known by evaluation-as-of, and exactly
one known observation matching the official regular-session close. Currency is
exact with no FX or fallback; the price is positive `NUMERIC(38,12)`, adjusted
for splits/reverse splits to the endpoint-share basis, and dividend-unadjusted.
Future candidates are invisible before identity checks, zero known candidates
are explicitly missing, and multiple valid known candidates are ambiguous.
The exact 2259-byte policy definition and SHA-256
`37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76`
are locked by ADR-014. This is not a claim that any bundled value is a real
observed close.

The completed tenth P3 slice consumes that complete endpoint resolution plus
nullable point-in-time target evidence. Its only formula is
`abs(target-actual)/actual`, with the actual endpoint price as denominator and
exactly one scale-12 `HALF_EVEN` division. Missing target and endpoint evidence
compose explicitly while preserving the exact nested endpoint reason; identity
mismatches and rounded output overflow are never converted to zero. The exact
1942-byte policy definition and SHA-256
`31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074`
are locked by ADR-015. No target error is added to the canonical outcome
fixtures or published by a product route.

The fixtures are deterministic and require no vendor credentials or network
access. Production provider payloads must be translated through provider
adapters before they reach the canonical domain.

These two disconnected leaves require no API key, provider account, paid plan,
domain, vendor license, or network access. Real-data integration remains the P5
boundary: before any non-DEMO value enters them, a provider must be selected and
credentials plus contractual rights for primary-venue closes, calendars,
corporate actions, reference data, target evidence, storage, display, and
derived outputs must be established. No vendor or environment-variable name is
invented by this slice.

## Repository layout

```text
apps/web/        Next.js user interface
apps/api/        Spring Boot API and Flyway migrations
fixtures/v1/     Versioned canonical DEMO fixtures
contracts/       OpenAPI contracts
schemas/         Canonical JSON Schemas
quality/         Phase acceptance checks
decisions/       Versioned architecture decisions
.github/         Continuous integration workflows
compose.yaml     PostgreSQL service
```

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the Git Flow, Conventional Commits,
review gates, and data-integrity rules.
