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
non-directional evidence. The ninth through twelfth P3 contracts are complete as
disconnected source-local leaves: point-in-time selection of one
official primary-venue endpoint close, followed by exact target error using the
actual endpoint price as denominator with scale-12 half-even rounding; then a
point-in-time pair of the exact source-recorded basis-event price and that
official endpoint close, followed by signed asset return using the basis price
as denominator. The thirteenth disconnected contract proves point-in-time
target-hit input readiness across exact basis terms, closed direction routing,
normalized target evidence, the strict horizon, and catalog evidence without
selecting a window extreme or invoking a calculator. The fourteenth contract
now selects one side-favorable value from a single PIT-visible, upstream-
attested exact causal-window high/low pair. The fifteenth contract now composes
the complete supplied eligibility and favorable-extreme branches, preserving
pending, non-applicable, and both unavailable families exactly while invoking
the pure target-hit calculator only for Ready plus Resolved. The sixteenth
contract composes complete supplied forecast terms, calculator-side routing,
and signed asset-return evidence. It validates exact direction, basis, asset,
and point-in-time correlation before branch selection, preserves neutral and
all unavailable return evidence, and invokes the pure directional-win
calculator only for a directional route plus an available return. None is
wired to fixtures, persistence, an API, a provider, or the web. The broader P3
scoring phase remains open.
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

The completed eleventh P3 slice pairs that complete endpoint resolution with
exactly one source-recorded price at the original or correction basis event.
Both basis and independent corporate-action adjustment evidence must be known
by the endpoint `evaluationAsOf`; future exact, wrong, or duplicate candidates
are invisible. Asset, primary venue, currency, price source/revision, selected
observation/provider-event links, coverage endpoints, and the
split/reverse-split endpoint-share/dividend-unadjusted basis must match exactly.
There is no prior-close substitution, nearest-price choice, interpolation,
deduplication, FX, or fallback. The exact 4655-byte policy definition and
SHA-256
`895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887`
are locked by ADR-016.

The completed twelfth P3 slice calculates a signed decimal price-return ratio
from one complete pair as exactly `(endpoint-basis)/basis`, with one subtraction
and one scale-12 `HALF_EVEN` division. Pair unavailability preserves its exact
nested reason; rounded output overflow is explicitly unavailable. Exact -1 is
valid, output below -1 is invalid, and no percent conversion or additional
rounding occurs. The exact 1011-byte policy definition and SHA-256
`e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486`
are locked by ADR-017. No asset return is written into the canonical outcome
fixtures or exposed by a product route.

The completed thirteenth P3 slice makes target-hit input applicability and
readiness explicit. One `BasisForecastTermsEvidence` record binds the exact
original/correction basis to its source direction and an explicit present or
absent target disposition. Known target absence is not missing evidence;
neutral direction is not a miss or loss. Terms, normalized target, route,
strict horizon, and calendar evidence must be point-in-time visible and match
exactly. Source-attested absence plus a visible normalized target is explicit
`TARGET_STATE_CONFLICT` before not-applicable; a future target stays invisible
and null-equivalent. An unreached resolved endpoint is
pending, incomplete horizon reasons are preserved, and a directional present
target with a non-null target date fails closed because V1 defines no expiry
semantics. `ReadyForWindowEvidence` means only that the completed ADR-019
selector may seek a full-window high or low. It is not a target hit and invokes
no calculator,
orchestrator, persistence, or product surface. No latest-correction or
cancellation eligibility is inferred. The
exact 3862-byte policy definition and SHA-256
`a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465`
are locked by ADR-018.

The completed fourteenth P3 slice consumes only complete ADR-018 readiness,
one nullable PIT asset/primary-venue/currency/source binding, and caller-
supplied full-window high/low candidates. Its economic interval is exactly the
primary-venue regular-session observations in the ordered strict-horizon
sessions with `observation.time > basis.eventTime` and
`observation.time <= endpointSession.closesAt`. This excludes a first
session's pre-call prices and includes the endpoint-close boundary without
reinterpreting that close as the favorable extreme. Future binding and
candidate evidence is invisible before every identity and cardinality gate;
known invalid candidates poison selection, and multiple fully valid candidates
are ambiguous even when equal. Exactly one attested pair resolves to its
stored high for bullish or stored low for bearish, preserving the original
decimal without rounding or rescaling. `EXACT_CAUSAL_WINDOW_SESSION_UNION` is
an upstream completeness attestation: this slice does not aggregate per-
session bars, verify raw ticks, resolve no-trade/halts/auctions/bar-straddles/correction
sequences, invoke a calculator, or use the ADR-014 endpoint close as a fallback.
The exact 4633-byte policy definition and SHA-256
`e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463`
are locked by ADR-019.

The completed fifteenth P3 slice accepts only the complete supplied ADR-018
eligibility result and, for Ready, one complete supplied ADR-019 favorable-
extreme result with whole-record-equal readiness. Non-ready branches reject
stale downstream evidence; Ready rejects an omitted selector result because
ADR-019 already represents missing evidence through an explicit Unavailable
branch. Pending, all three non-applicable reasons, all 14 eligibility
unavailable reasons (including the nested horizon reason), and all 22 favorable-
extreme unavailable reasons remain their original typed leaf records and never
become false or a loss. Only Ready plus matching Resolved builds the primitive
input from the preserved routed side, normalized target evidence, and selected
extreme, then invokes `TargetHitCalculator` exactly once. Source target terms,
direction reinterpretation, high/low reselection, endpoint-close fallback,
rounding, and rescaling are absent. The exact 3082-byte policy definition and
SHA-256
`b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348`
are locked by ADR-020. Its `Available` is a disconnected target-hit metric only,
not a canonical calculated/data-complete outcome or active methodology.

The completed sixteenth P3 slice accepts exactly four non-null request fields:
its policy, complete basis/forecast terms evidence, complete calculator-side
routing, and a complete signed asset-return result. The return leaf remains
mandatory for neutral so another basis, asset, or evaluation time cannot be
silently paired later. Before choosing a branch it verifies the exact canonical
source direction, whole basis record, asset identity, and terms visibility at
the return endpoint's `evaluationAsOf`. Neutral takes precedence and preserves
all three supplied evidence leaves without a Boolean. A directional unavailable
return preserves the exact typed return, price-pair, endpoint, and nested reason
chain without interpretation; all 55 unavailable combinations remain distinct.
Only a directional route plus an available return builds the primitive input
from the preserved side and exact signed return, then invokes
`DirectionalWinCalculator` once. The exact 3699-byte policy definition and
SHA-256
`51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb`
are locked by ADR-021. Its three results are `Available`, `NotApplicable`, and
`AssetReturnUnavailable`; none is a canonical lifecycle or publication state.

The fixtures are deterministic and require no vendor credentials or network
access. Production provider payloads must be translated through provider
adapters before they reach the canonical domain.

These disconnected leaves require no API key, provider account, paid plan,
domain, vendor license, or network access. Real-data integration remains the P5
boundary: before any non-DEMO value enters them, a provider must be selected and
credentials plus contractual rights for historical event-time/intraday or tick
prices,
primary-venue closes, calendars, corporate actions, asset/venue reference data,
target evidence, storage, display, derived outputs, and redistribution must be
established. Only after that selection may a reviewed adapter introduce a named,
scoped secret through approved local/CI secret stores; secrets must never be
supplied in chat or committed to Git. This repository does not invent a vendor
or environment-variable name in P3.

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
