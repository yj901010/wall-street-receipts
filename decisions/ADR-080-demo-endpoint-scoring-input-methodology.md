# ADR-080: DEMO endpoint scoring input and methodology integration

Date: 2026-09-11 (KST)

## Context and scope

PR #27 is merged at `8206c3467fa87f3a026af4ddde60c4e120a67b33`; PR CI #66
and merge CI #67 passed. The CPI operations package is complete for local MVP
development. Resume scoring integration without assuming an Ubuntu host or
commercial historical-data rights.

Existing P3 selectors, calculators and readiness receipts are disconnected.
Connect a useful first vertical application path: explicit schedule and raw
synthetic price evidence -> asset return, directional win and target error ->
replayable partial receipt. This is not a complete canonical CallOutcome, a new
ACTIVE registry entry, an observed financial fact, or a live-data endpoint.
The seven other metric meanings and aggregate lifecycle remain outside this
profile. In particular ADR-034's raw-window evidence prerequisite is not waived.

## Input and execution

`application/scoring/EndpointScoringInput` is an immutable, explicit DEMO-only
envelope. It retains the original/correction basis, horizon, full ordered trading
catalog, catalog provenance, asset/venue/currency/source binding, evaluation-as-of,
source forecast terms, endpoint candidates, basis candidates, adjustment evidence
and optional target evidence. Lists are defensively copied; empty means missing,
not zero. Bound each candidate list/catalog to 4096 entries. All existing UTC
microsecond precision and BigDecimal constraints remain in force.

Require PIT-visible terms correlated to the same basis and asset. Non-null target
evidence must match the source target's basis, asset, numeric value and currency.
This profile supports only caller-attested identity normalization: no implicit
FX or split conversion is introduced. A genuinely converted target needs a later
explicit transformation receipt. Existing endpoint/price-pair/target-error checks
still decide price binding, currency, adjustment continuity, ambiguity and PIT
availability. A missing target stays typed unavailable under ADR-015/023, including
when source terms explicitly contain no target; do not invent a new applicability
state or false Boolean. Invalid envelopes throw rather than publish results.

`EndpointScoringEvaluator` resolves the strict-close horizon from the supplied
catalog itself, rather than accepting a forged resolved schedule. An incomplete
schedule is preserved exactly and no price/metric leaf executes. Otherwise one
endpoint resolution is shared by the price-pair/return and target-error branches.
The direction route comes from the same source terms. Call existing deterministic
calculators, then preserve whole readiness objects. ADR-022 is the sole shared
asset-return/directional-win receipt per ADR-025; neutral preserves its available
return and intentional non-directional branch, never a manufactured loss.

Receipt construction is private to the evaluator. Every receipt retains input,
methodology identity, input fingerprint, resolved/incomplete schedule, and either
both readiness receipts or neither when the schedule is incomplete. It always
reports `dataComplete=false` and has no canonical evaluation status. It cannot be
passed to CallOutcomeRepository as a CallOutcome. There is no Spring registration,
clock read, DB writer, provider, scheduling, CLI activation, route or UI addition.
Caller-supplied correction membership and provenance are synthetic attestations,
not authenticated ledger/provider facts; future persistence integration must bind
them to actual immutable call/snapshot records before making stronger claims.

## Version and fingerprint

Profile `wsr-demo-endpoint-preview/1.0.0` has a canonical UTF-8/LF definition and
pins nine existing leaf policy versions/digests. The profile specifies strict
directional comparison, inherited scale-12 HALF_EVEN decimal ratio calculations,
typed missing evidence, execution order, scope and canonical input encoding.
Definition SHA-256:
`91abc0fcbc986b47e505bbddba346977911c9977664621e4c88cb8a2cbf8ea27`.
Replay can explicitly supply id/version/definition hash; unsupported identities
are rejected before calculation. Existing model-only registry rows are untouched.

Fingerprint v1 is SHA-256 of a closed binary input representation, prefixed with
the fingerprint format id and profile id/version/hash. Type tags are bytes:
0 null; 1 string; 2 enum; 3 Instant; 4 LocalDate; 5 Currency; 6 BigDecimal; 7 list;
8 allowlisted record. Text is a big-endian four-byte UTF-8 byte length followed by
bytes. Enum text contains declaring class then name. Instant/date use ISO text;
currency uses its code; decimal uses stripTrailingZeros().toPlainString(). Lists
carry a four-byte size then values in supplied order. Records carry exact class
name, four-byte component count, then each declared component name and value.
Only the fifteen explicit input record types are allowed, not arbitrary reflection
over application objects, Java serialization or mutable ObjectMapper settings.

This includes every supplied candidate, even candidates later rejected as future,
ambiguous or mismatched. Candidate order is intentionally part of input identity;
it need not change the resulting metrics. Decimal presentation scale is not an
economic input difference. Null, empty, absent-target variants, class identity,
source revisions, timestamps, method identity and sequence boundaries cannot be
flattened into delimited strings. Malformed Unicode is rejected, not replacement-
encoded; text is limited to 65536 UTF-16 units and total encoding to 1048576 bytes.
The fingerprint identifies supplied evidence, not its truth or external origin.

The fixed synthetic golden input fingerprint is
`c538a04a0eb74074c0d3e50af94aa4ca965b025ab20ec1df182fdc22e4df760b`.
Changing the profile/encoder/input shape requires explicit version and golden
review, never silently adopting a changed dependency under the same identity.

## Verification and next work

Golden tests execute the whole application path, not pre-built leaf results:
positive/negative/flat/rounding, all directions including neutral, future endpoint,
missing/ambiguous evidence, incomplete schedule, original/correction separation,
wrong terms/target/data mode, private receipt construction, copied inputs, concurrent
replay, locale/timezone independence, metadata mutation, ordered rejected-candidate
preservation, malformed Unicode, size limits and exact methodology selection.

Current CI pins the seven new Java source/test files separately from the frozen
historical product tree. No old calculator, fixture, schema, migration, endpoint,
web source or historical contract is edited. Record full results in
IMPLEMENTATION_LOG.md; no new hosted CI result is claimed before a PR exists.

Next: bind this partial receipt to immutable DEMO call/snapshot evidence and add
append-only persistence/read API, then a clearly marked audit UI. Decide a separate
explicit lifecycle/completeness policy before publishing any full CALCULATED
outcome; do not repurpose this partial receipt or activate model-only methodology
rows. Other metrics and aggregates follow their existing evidence prerequisites.
