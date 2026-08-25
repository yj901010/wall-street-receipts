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
calculator only for a directional route plus an available return. The
seventeenth contract classifies that complete supplied result as source-local
`Settled`, `AwaitingEndpoint`, or `EvidenceUnavailable` readiness while
preserving the whole source result. The eighteenth contract applies the same
source-local distinction to one complete supplied target-error result:
available target error is `Settled`, only the exact endpoint-not-reached chain
is `AwaitingEndpoint`, and all other unavailable shapes are
`EvidenceUnavailable`. The nineteenth contract classifies one complete
supplied target-hit orchestration result: its one Available and three permanent
NotApplicable shapes are `Settled`, its sole Pending shape is
`AwaitingEndpoint`, and all eligibility or favorable-extreme unavailable
shapes are `EvidenceUnavailable`. None is wired to fixtures, persistence, an
API, a provider, or the web. The broader P3 scoring phase remains open.
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
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

The `local` profile imports the ignored repository-root `.env` file. CI and
production do not activate that profile; deployment credentials must be
injected by the hosting platform instead.

On macOS or Linux, use `cp .env.example .env`, start the web process with
`CALL_AUDIT_PROVIDER=api API_BASE_URL=http://localhost:8080 pnpm --dir apps/web dev`,
then run `cd apps/api` followed by
`SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run`. The default local
endpoints are:

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

The completed seventeenth P3 slice accepts exactly its readiness policy and
one complete supplied ADR-021 result. `Settled` is limited to directional
`Available`, or neutral `NotApplicable` carrying an available asset-return
leaf. `AwaitingEndpoint` is limited to the exact nested chain
`PRICE_PAIR_UNAVAILABLE` -> `ENDPOINT_PRICE_UNAVAILABLE` ->
`ENDPOINT_NOT_REACHED_AS_OF`. Every other unavailable chain is
`EvidenceUnavailable`; in particular,
`BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE` never becomes awaiting merely because
its nested endpoint is not reached. Every branch preserves the exact whole
ADR-021 result without flattening a reason or rerunning a producer or
calculator. The exact 2353-byte policy definition and SHA-256
`1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7`
are locked by ADR-022. These readiness names do not mean
`OutcomeEvaluationStatus`, `dataComplete`, retryability, cancellation,
scheduling, methodology activation, persistence, or publication.

The completed eighteenth P3 slice accepts exactly its readiness policy and one
complete supplied ADR-015 `TargetErrorResult`. `Settled` requires an available
target error. `AwaitingEndpoint` requires the exact nested chain
`ENDPOINT_PRICE_UNAVAILABLE` -> `ENDPOINT_NOT_REACHED_AS_OF`, including the
typed endpoint-unavailable result carrying that reason. All other constructible
shapes are `EvidenceUnavailable`; in particular,
`TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE` never becomes awaiting because the
missing target cannot be repaired by waiting for the close. The complete matrix
is exactly 40 shapes: one settled, one awaiting, and 38 evidence-unavailable.
Every branch preserves the exact whole ADR-015 result without flattening a
reason or rerunning a selector or calculator. The exact 1979-byte policy
definition and SHA-256
`0b8bfb22dccd4a494f568c44d06163f73af36462cf929bc83cf238019811c44a`
are locked by ADR-023. This source-local readiness does not establish the
canonical lifecycle: that later decision must consider completeness across all
10 required metrics.

The completed nineteenth P3 slice accepts exactly its readiness policy and one
complete supplied ADR-020 `TargetHitOrchestrationResolution`. `Settled`
contains the one `Available` shape and all three permanent `NotApplicable`
reasons; it does not turn non-applicability into `false` or claim that another
metric exists. `AwaitingEndpoint` contains only the typed `Pending` branch,
whose preserved eligibility leaf already proves
`HORIZON_NOT_REACHED_AS_OF`. The 14 eligibility-unavailable and 22 favorable-
extreme-unavailable shapes are `EvidenceUnavailable` without retry or
permanence inference. The exact constructible matrix is 41 shapes: four
settled, one awaiting, and 36 evidence-unavailable. Every branch preserves the
exact whole ADR-020 result without flattening a nested reason or replaying an
eligibility resolver, selector, orchestrator, or calculator. The exact
2042-byte policy definition and SHA-256
`8f81dee5227370d82dd91cd2fb8448797c7028eaa485dc64cf4bdc3cbf2f31a3`
are locked by ADR-024. Its source-local names do not establish
`OutcomeEvaluationStatus`, `dataComplete`, retry/freshness, cancellation,
scheduling, methodology activation, persistence, or publication. The golden
contract is exactly 47 invocations: six fixed contract/negative/replay checks
plus all 41 classification shapes.

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

## SEC EDGAR public-data foundation

ADR-035 establishes the default-disabled SEC EDGAR public-provider foundation.

ADR-036 establishes the single-process SEC live-operation safety gate.

ADR-037 establishes the in-memory SEC decoded-response receipt foundation.

ADR-035 introduces the first P5 public-data adapter boundary for SEC EDGAR
submissions metadata. It is disabled by default and remains server-only. When
`SEC_PROVIDER_ENABLED=true`, the adapter requires `SEC_CONTACT_EMAIL` and sends
the declared `WallStreetReceipts/0.1 (operations@example.com)`-shaped User-Agent to
`https://data.sec.gov`; `SEC_BASE_URL` exists only for controlled testing and
defaults to that official origin. Provider errors, malformed parallel arrays,
and missing timestamps fail closed without fixture or empty-result fallback.

This slice maps recent filing metadata into a provider-neutral filing catalog
but adds no scheduler, database writer, HTTP product endpoint, or web
publication. `BLS_REGISTRATION_KEY`, `BEA_USER_ID`, and `EIA_API_KEY` may be
present in the local secret file but are deliberately not consumed until their
own P5 source, revision, canonical-model, and publication decisions are
approved.

The live-operation guard is intentionally narrower than production ingestion.
The official SEC ceiling is 10 requests/second in aggregate across machines;
the internal one-JVM policy instead spaces calls at 8 requests/second (125 ms,
no accumulated burst). Decoded submissions JSON is capped at 8 MiB even when
`Content-Length` is absent or compression expands past the limit. HTTP `429`
is never retried automatically: valid delta-seconds or RFC 1123 `Retry-After`
only lengthens a process-local cooldown, while missing, invalid, expired, or
sub-10-minute values use a 10-minute minimum. Calls during cooldown fail before
network I/O rather than sleeping a request thread.

These are conservative internal controls, not additional SEC service
guarantees. Process-local enforcement does not make multiple replicas or
independent tools aggregate-safe, so schedulers, multi-replica activation,
persistence, API/UI publication, and production collection remain prohibited.
The opt-in manual smoke needs no new API key or account and makes one Apple CIK
request to the exact official origin; see `apps/api/README.md`. Default tests,
`verify`, and CI remain offline.

The receipt foundation binds each accepted HTTP `200 application/json`
submissions catalog to the exact fully read bytes exposed after one advertised
gzip/deflate transport decode. SHA-256 is lowercase and covers those bytes
without charset conversion, JSON normalization, or reserialization; the same
owned bytes are then parsed by `SEC_SUBMISSIONS_RECENT_V1`. UTF-16, UTF-32, and
malformed UTF-8 fail before receipt creation without transforming valid UTF-8
bytes or removing their BOM. Duplicate keys, scalar coercion,
floating-point-to-integer coercion, and trailing JSON tokens also fail closed.
For gzip/x-gzip/deflate, the encoded representation's stale `Content-Length`
is removed from the decoded downstream header view while `Content-Encoding`
is retained for the receipt; the decoded stream cap and captured decoded byte
length remain authoritative.
The receipt carries the source URI, `capturedAt`,
status, media type, transport encoding, optional
`ETag`/`Last-Modified`, parser version, decoded length, and digest. Only that
response metadata allowlist is retained. Request headers, contact email, and
complete `User-Agent` are not receipt data.

`RECEIPT_ONLY_BODY_NOT_RETAINED` is literal: the bounded decoded body is
transient parsing memory and is not durably retained. Its digest is a local
byte-identity check, not an SEC signature or sender authentication. ADR-037
adds no durable raw body, replay, persistence, database, scheduler, controller,
or publication surface. Historical submissions-segment modeling is next;
append-only persistence follows it. Neither step nor ADR-037 requires a new API
key or account.

ADR-022 remains the sole shared receipt for asset-return and directional-win readiness.
ADR-025 makes this an ownership decision without adding a policy, digest,
package, resolver, or test: the exact ADR-022 receipt is consumed once while a
later aggregate accounts for both metric meanings. Directional `Available`
preserves the exact return and Boolean; neutral `NotApplicable` preserves the
exact available return and intentional directional non-applicability; an
unavailable return keeps the shared receipt awaiting or evidence-unavailable
without inventing either metric. A complete future aggregate has 10 metric
meanings and nine readiness ownership inputs. Today only shared ADR-022,
target-error ADR-023, and target-hit ADR-024 readiness contracts exist; the
remaining inputs are deferred. None of the three source-local readiness names
maps directly to a canonical lifecycle state.

ADR-026 locks benchmark and sector returns to explicit point-in-time reference assignments.
The required product approval has been received and recorded as a decision-only
comparative reference-return foundation. Benchmark and sector assignments stay
independently typed and freeze explicit source-revised membership at the exact
original/correction basis event. V1 may map an explicitly evidenced US/USD
equity to `asset-spx` only through a visible assignment; it never infers that
mapping from a ticker, current master row, `MarketSnapshot.spx`, or a DEMO map.
Known out-of-scope assets are intentionally not applicable, while missing or
conflicting expected evidence remains unavailable.

Sector work first requires a versioned provider-neutral WSR taxonomy and
explicit provider mappings; synthetic P2 grouping labels are not evidence and
the product does not claim GICS or ICB semantics. Later benchmark and sector
price-return leaves must use exact source-recorded reference levels over the
same basis-to-endpoint UTC interval, exact currency with no FX, explicit
reference calendar/source/venue identity, and index divisor-continuity proof.
They use separate calculator/result types, must apply ADR-017's exact one-
subtraction/one scale-12 `HALF_EVEN` division, and never reuse the asset share-
basis adjustment types. Current DEMO outcome values remain null. ADR-027
implements benchmark assignment, ADR-028 locks the provider-neutral sector
taxonomy and mapping decision, and ADR-029 implements basis-frozen sector
assignment. ADR-030 now adds the two independent reference-level pairs,
ADR-031 adds the independently typed benchmark return calculator, and ADR-032
adds the independently typed sector return calculator. ADR-033 adds two
independent source-local readiness contracts without correlating the leaves.
Raw-window coverage precedes MFE/MAE, alpha and sector alpha come last, and
lifecycle composition remains separate.

ADR-027 selects benchmark assignment only from explicit point-in-time evidence frozen at the outcome basis event.
The disconnected V1 selector accepts only complete source-identified
classification and assignment candidate lists for one exact `OutcomeBasis`,
asset, and `evaluationAsOf`. It removes future evidence before every identity,
reason, and cardinality decision, applies start-inclusive/end-exclusive
membership with an explicit open-ended variant, and never filters visible bad
evidence toward a convenient valid row. Originals and corrections are
independent bases.

Known non-equity or non-US/USD classifications are typed `NotApplicable` only
when no visible assignment conflicts with that scope. An in-scope US/USD equity
resolves only through exactly one coherent mapping to `asset-spx`,
`AssetType.INDEX`, USD, and `PROVIDER_PUBLISHED_PRICE_INDEX`; missing,
conflicting, invalid, or duplicate evidence is typed `Unavailable`. The exact
4261-byte policy definition has SHA-256
`7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69`.
No ticker, current master state, `MarketSnapshot.spx`, UI universe, map, or
treemap can create an assignment, and no schema, fixture, API, database,
provider, or web runtime consumes this leaf. It requires no API key, account,
license, secret, or network access; non-DEMO use remains blocked on P5 provider
selection and storage/display/derived-data/redistribution rights.

ADR-028 locks WSR Economic Activity V1 and exact point-in-time provider-node mapping semantics.
The decision-only `wsr-economic-activity` taxonomy is version `1.0.0`: one
unassignable root and twelve closed, single-level economic-activity leaves.
There is no `UNKNOWN`, `OTHER`, or unclassified member; missing, conflicting,
ambiguous, future, or unmapped evidence remains unavailable. Diversified
Operations requires explicit evidence of no single represented primary
activity and is never a fallback. The exact 3824-byte taxonomy definition has
SHA-256 `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`.

Provider nodes map only through policy
`POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1`. Its exact
4395-byte definition has SHA-256
`ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77`.
Provider scheme revision and node ID establish identity; labels remain evidence
and cannot drive raw, normalized, or fuzzy matching. Both PIT timestamps must
be visible, the mapping interval contains the exact basis event with start-
inclusive/end-exclusive semantics, and duplicates, overlap, or conflicting
targets fail closed. ADR-028 itself introduced no actual provider mapping set,
membership, reference index, executable assignment policy, or return. This
decision needs no API key or account; real data remains blocked on provider
selection and documented historical, storage, display, derived-crosswalk,
cache, and redistribution rights.

ADR-029 freezes WSR sector assignment to explicit point-in-time membership and mapped provider-node evidence.
The disconnected V1 selector adds exactly seven independently typed production
files and one 134-invocation source-local golden. It consumes one complete
original/correction basis, explicit classification and provider membership,
the caller-attested mapping-set ID/version/hash, and exact ADR-028 mapping rows.
Future evidence is removed before all reasons and cardinality; both membership
and mapping use start-inclusive/end-exclusive intervals at the exact basis
event. Country and currency remain preserved evidence, while sector V1 applies
to equities and uses the sole intentional N/A reason `NON_EQUITY`.

The exact 9307-byte assignment-policy definition has SHA-256
`52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c`
and binds ADR-028's exact taxonomy and mapping-policy hashes, twelve assignable
leaves, 36 unavailable reasons, and fixed fail-closed precedence. The selector
classifies any multiple visible same-disposition mapping rows as ambiguous,
including distinct rows with the same target or not-mapped reason. It
echoes and row-matches the caller-supplied mapping-set identity but does not
calculate a manifest hash or attest entry-to-manifest correlation. It adds no
actual provider set or data, fixture, schema, API, database, reference index,
return, or web behavior. No API key or account is needed; non-DEMO membership,
mapping sets, and adapters remain blocked until provider selection and written
historical, storage, display, derived-crosswalk, cache, and redistribution
rights. Verification passes: focused 134/134 and full API 1248/1248; all 34
workflow Python bodies compile and 33 locally executable bodies pass;
SnakeYAML retains four jobs; Compose validates; marker, policy-byte, and runtime
cardinality mutations each exit nonzero and are restored; current and legacy
baselines plus patch hygiene pass. Current production is exactly 202 files /
`b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`;
current API-test/web is 199 files /
`59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`.
Excluding ADR-029's seven-plus-one surface reproduces ADR-028 production at
195 files / `562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640`
and test/web at 198 files /
`0f6c5358ea2564c562159d375b42985e8aafd603b1673fcc404aab83bcf74a0e`,
while the user-owned `apps/web/next-env.d.ts` remains untouched.

ADR-030 resolves benchmark and sector reference-level pairs independently from explicit point-in-time provider-published price-index evidence over the exact basis-event-to-asset-endpoint UTC interval.
The disconnected slice adds seven independently named production types and
one source-local golden for each leg. The benchmark policy is
`POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1`, with an exact
9342-byte definition and SHA-256
`2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`.
The sector policy is
`POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1`, with an exact
9806-byte definition and SHA-256
`4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`.
The packages share no generic pair, policy, evidence, result, selector, cast,
or fallback.

Each request preserves the complete corresponding ADR-027 or ADR-029
assignment resolution and complete ADR-014 `EndpointPriceResolution`.
Basis and evaluation cutoff always match exactly. The selector derives anchor
validity from catalog PIT visibility, exact catalog-to-horizon
calendar/revision identity, and binding PIT visibility—not from the supplied
endpoint unavailable label. Only after that anchor is usable must asset match;
a resolved or not-applicable assignment then also matches the endpoint
binding's primary venue and currency. The exact source-local anchor reasons
`CATALOG_NOT_KNOWN_AS_OF`, `CATALOG_EVIDENCE_MISMATCH`, and
`BINDING_NOT_KNOWN_AS_OF` produce
`EndpointAnchorUnavailable(context,reason)`. With coherent anchor facts, all
sixteen ADR-014 unavailable labels are ignored and independently available
reference evidence may proceed. Assignment N/A and assignment unavailability
retain their complete upstream receipts and take precedence.

Both legs select one PIT-visible provider-published price-index binding, one
exact source-recorded level at the basis event, one exact level at the asset
endpoint UTC, and one divisor-continuity attestation linked to both
observations over that same interval. Calendar ID/revision and calendar source
ID/revision remain explicit on the binding, both levels, and continuity row.
Benchmark levels and continuity repeat the exact assigned canonical benchmark
asset ID/type. Sector binding, levels, and continuity repeat one explicit
reference asset ID whose type is exactly `INDEX`. Visible mismatches fail
closed before cardinality, future evidence is invisible, and equal duplicates
remain ambiguous. Levels are positive exact `NUMERIC(38,12)`; reference and
asset currencies match with no FX. Prior close, nearest timestamp,
interpolation, shifted sessions, total-return indices, ETFs, current baskets,
market-cap proxies, provider-return fields, deduplication, provider preference,
and fallback cannot resolve.

The five result variants per leg are `Resolved`, `NotApplicable`,
`AssignmentUnavailable`, `EndpointAnchorUnavailable`, and
`EvidenceUnavailable`; the anchor variant carries its independent exact reason.
Benchmark has 53 ordered local unavailable reasons; sector has 56 because its
binding also proves exact taxonomy, canonical-node, and independently named
reference-asset correlation. A resolved WSR node remains economic-activity
assignment evidence only: the sector reference leg separately links its exact
mapping evidence and canonical node to an explicit provider-published sector
price index. Membership provider, index publisher, and level redistributor are
not assumed to be the same.

ADR-030 adds no actual provider identity, binding, index level, calendar,
divisor data, schema, fixture, manifest, OpenAPI, Flyway, database, adapter,
API, or web behavior. It performs no return calculation or readiness/lifecycle
mapping and does not reuse ADR-016 asset-share price-pair or corporate-action
types. No API key, account, plan, license, secret, or network access is needed
for this disconnected contract. Before non-DEMO evidence, P5 must approve the
exact index products/feeds and historical exact-time index-level,
calendar identity/revision/source, divisor-continuity/methodology, sector
binding, storage/cache, display, derived-data, and redistribution rights.
Credentials come only after that approval through untracked
local/CI/deployment secret stores, never chat or Git.

ADR-030 verification passes the exact fourteen-plus-two surface, policy
bytes/hashes, B53/S56 local reasons, independently typed three-reason anchor
enums, reverse isolation, and four-document marker parity. Focused benchmark
golden 200/200 and sector golden 220/220—420 total—and full API Maven
1668/1668 `BUILD SUCCESS` pass with zero failures, errors, or skips, including
Docker/PostgreSQL/Flyway. Normalized golden-source SHA-256 values are benchmark
`3518b66914656c8225858f8f15fdb60e25576a15b64f148270cda9881e3d8099`
and sector
`af9ec3aa0318595027d13eb4748d41bdb587776ef3d2e5c8b3bf477fa7ba439b`.

The dedicated ADR-030 guard independently passes. All 35/35 workflow Python
heredoc bodies syntax-compile; 34/34 locally executable bodies pass, with the
last cross-stack body intentionally syntax-only. SnakeYAML 2.5 parses exactly
four jobs and Compose config passes. Current production is 216 files /
`45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`;
current API-test/web is 201 /
`fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`.
Exact ADR-030 fourteen-plus-two exclusion reproduces ADR-029 production 202 /
`b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`
and test/web 199 /
`59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`.
README-marker, benchmark-policy-byte, and benchmark expected-cardinality 201
mutations all exit 1 and are restored; the cardinality gate observes actual
200. `git diff --check` is clean, and the user-owned
`apps/web/next-env.d.ts` remains preserved and unstaged: **PASS**.

ADR-031 calculates a signed benchmark price-index return from one complete ADR-030 benchmark reference-level-pair receipt using the exact basis-level denominator.
The disconnected leaf adds exactly `BenchmarkReturnPolicyVersion`,
`BenchmarkReturnInput`, `BenchmarkReturnResult`, and
`BenchmarkReturnCalculator` under the source-local `benchmarkreturn` package,
plus one `BenchmarkReturnCalculatorGoldenTest`. Its sole policy is
`SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`, with an exact
2832-byte ASCII/UTF-8 definition and SHA-256
`96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79`.
The input and every result context preserve the complete ADR-030 benchmark
reference-level-pair resolution using hash
`2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`.

The result keeps six exact branches: `Available`, `NotApplicable`,
`AssignmentUnavailable`, `EndpointAnchorUnavailable`, `EvidenceUnavailable`,
and `OutputUnavailable`. Upstream typed receipts and their reasons are retained
without copying, mapping, duplication, or flattening; only a resolved pair can
be calculated. The calculator reads the selected basis and endpoint
provider-published index levels and performs exactly one subtraction followed
by one scale-12 `HALF_EVEN` division as `(endpoint-basis)/basis`. Output is a
signed decimal ratio in `NUMERIC(38,12)`, rounded `-1.000000000000` is valid,
and nonrepresentable output has the sole local reason
`OUTPUT_NOT_REPRESENTABLE`. Provider-return fields, percent conversion,
float/double conversion, intermediate or second rounding, alternate
denominators, asset-return reuse, sector-return reuse, and fallback are absent.

ADR-031 adds no provider data, selector, readiness, lifecycle, methodology,
schema, fixture, manifest, OpenAPI, Flyway, database, controller, repository,
adapter, API, or web behavior. Existing DEMO comparative metrics remain null.
No API key, account, license, secret, or network is needed. Real use remains
blocked on ADR-030's approved benchmark product/feed, exact-time historical
coverage, calendar/divisor evidence, and storage, display, derived-data, and
redistribution rights; credentials may follow those approvals only through
untracked secret stores.

ADR-031 verification passes the exact 2832-byte policy/hash and four-plus-one
surface. Focused golden verification is 95/95 with normalized source SHA-256
`80c8e7dcdf6b4ee3daf980dc3c3d2aa54e4446620af2fc0985173fddf5ab3c90`;
full API Maven verification is 1763/1763 with zero failures, errors, or skips
and `BUILD SUCCESS`, including Testcontainers PostgreSQL 17.10 and Flyway. The
dedicated guard/runtime gate passes; all 36 workflow Python heredocs compile
and all 29 locally runnable bodies pass. Six `jsonschema`-dependent bodies are
syntax-only because the bundled local runtime lacks that module, and the final
cross-stack integration-log body is syntax-only by design. SnakeYAML 2.5
retains exactly four jobs and Compose validates.

Current protected production is 220 files /
`cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`;
API-test/web is 202 /
`12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`.
Exact ADR-031 exclusion replays ADR-030 at production 216 /
`45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`
and test/web 201 /
`fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`;
the downstream replay retains ADR-029 production 202 /
`b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`
and test/web 199 /
`59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`.
Independent reviews have no remaining P0/P1/P2 finding after two golden gaps
were corrected, and `apps/web/next-env.d.ts` remains preserved. Deliberate
README-marker and canonical `percentConversion`-byte mutations make the guard
exit 1; changing expected runtime count from 95 to 94 while actual remains 95
makes its gate exit 1. All mutations are restored, the final dedicated guard
passes, `git diff --check` is clean, and marker parity remains one per document:
**PASS**. ADR-032 now supplies the independent sector reference-return
calculator, ADR-033 supplies both independent comparative-readiness contracts,
and ADR-034 now freezes the decision-only raw-window truth boundary. Executable
raw aggregation remains gated; lifecycle integration, canonical publication,
MFE/MAE, alpha, and sector alpha remain later work.

ADR-032 calculates a signed sector price-index return from one complete ADR-030 sector reference-level-pair receipt using the exact basis-level denominator.
The disconnected leaf adds exactly `SectorReturnPolicyVersion`,
`SectorReturnInput`, `SectorReturnResult`, and `SectorReturnCalculator` under
the independently typed `sectorreturn` package, plus one source-local
`SectorReturnCalculatorGoldenTest`. Its sole policy is
`SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`; the exact
2817-byte ASCII/UTF-8 definition has SHA-256
`5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7`.
Input and result contexts retain the complete ADR-030 sector pair using policy
hash `4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`.

The six result branches are `Available`, `NotApplicable`,
`AssignmentUnavailable`, `EndpointAnchorUnavailable`, `EvidenceUnavailable`,
and `OutputUnavailable`. The same complete typed pair receipt is preserved;
nested reasons are not copied, mapped, duplicated, or flattened. Only a
resolved pair is calculated. The calculator reads its selected basis and
endpoint provider-published price-index levels, performs exactly one exact
subtraction, and divides once by the positive basis at scale 12 with
`HALF_EVEN`. Output is a signed `NUMERIC(38,12)` decimal ratio, rounded exact
`-1.000000000000` is valid, and nonrepresentable output uses only
`OUTPUT_NOT_REPRESENTABLE`. Provider-return fields, percent conversion,
float/double conversion, intermediate or second rounding, alternate
denominators, asset-return reuse, benchmark-return reuse, and fallback are
absent.

ADR-032 adds no provider data, selector, readiness, lifecycle, methodology,
fingerprint, lineage, schema, fixture, manifest, OpenAPI, Flyway, database,
controller, repository, adapter, persistence, API, or web behavior. Existing
DEMO comparative metrics remain null. No API key, account, paid plan, provider
license, named secret, or network access is needed, and this slice has no
credential. Before non-DEMO use, P5 must approve the exact sector-index
product/feed; rights to create and use the exact WSR canonical-node-to-selected
provider-published sector price-index binding; exact-time historical levels and
revisions; reference-calendar identity, revision, and source; divisor and
methodology continuity; and storage, cache, display, derived-return, and
redistribution rights. Publisher and redistributor require separate review when
they differ. Assignment or classification rights alone do not grant index
rights. Only after provider/product and written-rights approval may a scoped
credential enter untracked local, CI, and deployment secret stores; it must
never be placed in chat or Git.

Focused golden verification is 112/112 with zero failures, errors, or skips;
the normalized golden-source SHA-256 is
`6047b29c8c338893bf2fdeaa9a5fef83ec20cb4f5e11acb77d82b48d8752b129`.
The protected production baseline is 224 files / SHA-256
`bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`,
and exact ADR-032 production exclusion reproduces ADR-031 at 220 files /
`cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`.
API-test/web is 203 files /
`5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`;
exact golden exclusion replays ADR-031 at 202 /
`12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`.
Full API Maven verification passes 1875/1875 with zero failures, errors, or
skips and `BUILD SUCCESS`, including Testcontainers PostgreSQL 17.10 and
Flyway. The dedicated guard/runtime gate passes; 37/37 workflow Python bodies
syntax-compile and all 30/30 locally runnable bodies pass. Six
`jsonschema`/`referencing` bodies and the final cross-stack body remain
syntax-only for their documented local dependency/runtime boundaries.
SnakeYAML retains four jobs and Compose validates. README-marker, canonical
policy-byte, and runtime 112-to-111 mutations each fail and are restored. Web
lint, 569/569 Vitest tests, production build, marker parity, and diff hygiene
pass; independent review has no remaining P0/P1/P2 finding. The user-owned
`apps/web/next-env.d.ts` remains preserved.

ADR-033 classifies benchmark and sector return readiness independently from their complete supplied ADR-031 and ADR-032 result receipts without mapping either leaf to canonical lifecycle status.
The disconnected slice adds separate `benchmarkreturnreadiness` and
`sectorreturnreadiness` packages. Each contains exactly one policy, request,
sealed resolution, and resolver plus one source-local exhaustive golden. A
request accepts only its matching complete return result and exact source
policy/hash. `Available` and intentional `NotApplicable` are `Settled`; only a
return `EvidenceUnavailable` that preserves a pair
`EvidenceUnavailable(ENDPOINT_NOT_REACHED_AS_OF)` is `AwaitingEndpoint`.
Assignment, anchor, every other evidence reason, and output unavailability are
`EvidenceUnavailable`. Every result preserves the exact whole supplied source
object and carries no flattened reason.

The benchmark readiness definition is exactly 2622 UTF-8 bytes with SHA-256
`2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf`;
the sector definition is exactly 2592 bytes with SHA-256
`5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4`.
ADR-025's future nine-input ownership stays intact: there is no combined
comparative receipt, cross-return correlation, shared generic helper, cast,
or lifecycle mapping. Existing DEMO comparative values remain null, and no
schema, fixture, manifest, OpenAPI, Flyway, database, controller, repository,
provider, API, or web runtime is added.

No API key, account, paid plan, license, secret, or network access is required
for ADR-033. Real evidence remains blocked until P5 approves the exact
benchmark and sector-index products/feeds, exact-time history/revisions,
calendar and divisor/methodology continuity, sector binding, and
storage/cache/display/derived-return/redistribution rights. Publisher and
redistributor rights are reviewed separately when they differ; only then may
scoped credentials enter untracked local, CI, and deployment secret stores,
never chat or Git.

Focused verification passes benchmark 87/87 and sector 104/104—191 total—with
zero failures, errors, or skips. Normalized golden-source SHA-256 values are
benchmark
`f61e82ba7766effe4954f4c96db745a49bb49a03d06de583c39c32d76e3c1b3d`
and sector
`1b2aa1eea5d5c8efcddd048c54d8b53be87b14cdfd96a6df43e11a7f55bc9f8c`.
Protected production is 232 files /
`2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941`
and API-test/web is 205 files /
`fba2656db6ef5bbf5e15288bebd894639926645e7657ac214ec1cec657cc4d75`.
Excluding exactly ADR-033's eight production files and two goldens reproduces
ADR-032 at 224 /
`bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`
and 203 /
`5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`.
Full API verification passes 2066/2066 with zero failures, errors, or skips,
including Testcontainers PostgreSQL 17.10 and Flyway. The dedicated guard plus
87/87 and 104/104 runtime gates pass; 38/38 workflow Python bodies compile and
31/31 locally runnable bodies pass. SnakeYAML retains four jobs and Compose
validates. Web lint, 569/569 Vitest tests, and production build pass. README
marker, canonical policy-byte, and expected runtime 87-to-86 mutations each
fail and are restored. Independent review found no P0/P1/P2 issue after its
future-endpoint anchor coverage gap was corrected. The user-owned
`apps/web/next-env.d.ts` is restored to its exact pre-build hash.

ADR-034 freezes provider-neutral point-in-time raw-window coverage semantics before any executable raw aggregation or MFE/MAE calculation.
The decision-only foundation fixes V1 to eligible primary-venue regular-session
trade ticks over the exact causal interval
`(basis.eventTime, endpointSession.closesAt]`. It excludes pre-call ticks,
off-hours, alternate venues, quotes, indications, and OHLC/intraday/session
bars, and forbids basis-price, endpoint-close, prior-close, nearest-price,
interpolation, bar-derived, and ADR-019 aggregate fallbacks.

A later executable request must anchor to one complete ADR-016
`AssetReturnPricePairResolution.Resolved`, not target-specific ADR-018 or
ADR-019 output. Raw events and their manifest must preserve exact source and
provider-event revisions, sequence/watermark and correction/bust coverage,
trade conditions, ordered sessions, calendar/catalog identity, adjustment and
continuity, provenance, and both `availableAt` and `capturedAt`. Every item is
visible only when both PIT timestamps are at or before the inherited
`evaluationAsOf`; later corrections create later replay evidence and never
rewrite an earlier receipt.
Raw events additionally require
`eventTime <= availableAt <= capturedAt <= evaluationAsOf`, while manifests
require `upperBound <= availableAt <= capturedAt <= evaluationAsOf`; evidence
cannot become available before its event or attested window exists.

No-trade silence is complete only under an approved source completeness proof.
Halts contribute no price, auction trades require an explicit versioned
condition mapping, unknown conditions and internal sequence gaps fail closed,
and a proven window with zero eligible trades remains
`CompleteWithoutEligibleTrade` rather than invented zero MFE/MAE. One future
coverage receipt must co-identify the population used for both high and low;
it is shared source evidence only, while MFE and MAE calculators and readiness
ownership remain separately reviewed.

ADR-034 adds no Java, test, schema, fixture, manifest member, OpenAPI, Flyway,
database, provider, API, or web runtime surface. Existing DEMO MFE/MAE stay
null, and ADR-019's supplied `EXACT_CAUSAL_WINDOW_SESSION_UNION` attestation is
not promoted into raw proof. No API key, account, plan, license, secret, or
network is needed. Before executable non-DEMO work, the exact historical
trade-tick feed and written history, sequence/correction, auction/halt,
calendar/corporate-action, storage, derived-use, display, and redistribution
rights must be approved. Only then may a scoped credential enter untracked
local, CI, or deployment secret storage—never chat or Git.

The dedicated decision-only guard passes with protected production unchanged
at 232 files / SHA-256
`2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941`
and API-test/web unchanged at 205 files / SHA-256
`fba2656db6ef5bbf5e15288bebd894639926645e7657ac214ec1cec657cc4d75`.
All 39/39 workflow Python bodies compile, 32/32 locally runnable bodies pass,
SnakeYAML retains four jobs, and Compose validates. Full API verification is
2066/2066 with zero failures, errors, or skips including PostgreSQL 17.10
Testcontainers/Flyway; web lint, 569/569 Vitest tests, and the 12-page
production build pass. Exact marker and forbidden-runtime mutations each make
the guard fail; `NotApplicable`, causal-time, and provider-config mutations do
the same. All are restored. `git diff --check` passes, and the user-owned
`apps/web/next-env.d.ts` is restored to its exact pre-build hash. Independent
closure review reports no remaining P0-P3 finding.

The next executable raw-window resolver remains blocked on that exact product's
documented sequence, correction, trade-condition, auction, halt, and rights
evidence. Separate MFE/MAE arithmetic and readiness follow it; lifecycle,
methodology/fingerprint, lineage, persistence, API/UI publication, alpha, and
sector alpha remain later work, with alpha last.

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
