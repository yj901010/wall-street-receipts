# ADR-042 — SEC Operator-Controlled Collection Attempt

- Status: Accepted
- Date: 2026-08-25

ADR-042 gives an operator two bounded application commands for creating one SEC
root capture or assembling one exact-root collection. Each new attempt performs
at most one provider invocation, records its durable intent before dispatch,
and never retries or silently resumes an indeterminate invocation.

## Context

ADR-039 durably appends one exact SEC submissions root response. ADR-040 can
append one explicitly selected additional-file response under one exact root
descriptor. ADR-041 assembles already durable root and segment captures with
zero network access. Those decisions deliberately provide no operation that
coordinates capture and assembly, and configuration alone still initiates no
SEC traffic.

The SEC's
[EDGAR API documentation](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)
documents the keyless current submissions root at
`https://data.sec.gov/submissions/CIK##########.json`. It says that the root
contains at least one year or 1,000 recent filings, whichever is more; older
filings are referenced through an array of additional JSON files and their
date ranges. The JSON structures are updated throughout the day as filings are
disseminated. The documentation does not provide a common root/file revision,
atomic snapshot token, stable descriptor order, range continuity or
disjointness rule, immutable-content promise, or duplicate-resolution rule.

SEC's
[Developer Resources](https://www.sec.gov/about/developer-resources) state that
one user's total traffic must not exceed ten requests per second regardless of
the number of machines used. The
[Internet Security Policy](https://www.sec.gov/about/privacy-information#internet-security-policy)
states that excessive traffic may cause IP addresses to be limited and that
access may resume after the request rate has remained below the threshold for
ten minutes. SEC also requires automated clients to declare a descriptive
`User-Agent` with operational contact information. The official guidance does
not promise that every limit response is HTTP `429`, that `Retry-After` is
present, or that one process-local limiter proves aggregate compliance across
replicas.

[Accessing EDGAR Data](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)
documents post-acceptance corrections and removals. SEC's
[filing-correction guidance](https://www.sec.gov/submit-filings/filer-support-resources/how-do-i-guides/correct-or-delete-filing)
also explains that an original and a corrective filing typically both remain
public and that SEC staff cannot retroactively change copies already
disseminated or extracted by third parties. A later observation may therefore
differ legitimately from an earlier immutable WSR capture.

ADR-042 closes only the smallest single-JVM operator-command gate. It records
whether WSR planned and may have dispatched one request, but it does not claim
exactly-once delivery to SEC or complete SEC history.

## Decision

The contract is versioned exactly as follows:

```text
schema: 1.0.0
provider: sec-edgar
product: edgar-submissions-operator-collection-attempt
policy: SEC_OPERATOR_CONTROLLED_COLLECTION_ATTEMPT_V1
```

The application accepts exactly two command kinds:

```text
CAPTURE_ROOT
COLLECT_EXACT_ROOT
```

For a previously unseen operator-request UUID, either command performs zero or
one provider invocation. No code path may perform two provider invocations
under one attempt.

### Common command envelope and immutable identity

Every command supplies a caller-generated canonical, nonzero, lowercase UUID
`operatorRequestId`. A versioned, length-prefixed canonical command projection
derives one lowercase SHA-256 `commandSha256`. It binds its command-identity
version, schema, provider, product, policy, command kind, exact canonical CIK or
root capture ID, and every descriptor action. For `COLLECT_EXACT_ROOT`, actions
are sorted by their actual root descriptor ordinal before hashing. A separate
versioned, length-prefixed attempt identity binds its attempt-identity version,
`operatorRequestId`, and `commandSha256` as lowercase SHA-256 `attemptId`. The
current clock, `requestedAt`, provider configuration, contact email, and any
later artifact or outcome are excluded from both identities.

The first accepted use of an `operatorRequestId` immutably binds that UUID to
its exact `commandSha256` and `attemptId`. A later call with the same UUID and
the same command SHA returns the existing durable attempt state and original
timestamps without invoking SEC or appending another ledger row. This
zero-network replay applies whether the existing attempt is planned,
dispatched without a terminal, succeeded, or failed. Reusing the UUID with a
different command projection is a conflict and also performs zero provider
requests.

Idempotency is local command replay, not exactly-once HTTP delivery. A new UUID
is a new operator decision and does not inherit or repair an older attempt.

### `CAPTURE_ROOT`

`CAPTURE_ROOT` accepts only:

- the common `operatorRequestId`; and
- one nonzero CIK normalized to exactly ten decimal digits.

After zero-network command validation, the operation may cross the durable
dispatch boundary and enter the provider gate once. If the gate permits the
invocation, it calls the existing ADR-039 root-capture path once for exactly:

```text
GET https://data.sec.gov/submissions/CIK##########.json
```

Its provider operation is exactly `CAPTURE_ROOT`. A successful response is
committed as one exact ADR-039 root capture and one successful attempt terminal
in the post-response local transaction. `INSERTED` and `IDENTICAL_REPLAY` are
both successful append outcomes; both return the exact durable root capture
identity. This command does not select or capture a descriptor, assemble a
manifest, or issue a follow-up request.

### `COLLECT_EXACT_ROOT`

`COLLECT_EXACT_ROOT` accepts:

- the common `operatorRequestId`;
- one exact durable ADR-039 `rootCaptureId`; and
- a finite explicit action list over that root's descriptor ordinals.

Each selected ordinal appears exactly once and has exactly one action kind:

```text
SELECT_EXACT(exact historicalSegmentCaptureId)
CAPTURE_NOW
```

Any number of distinct ordinals may use `SELECT_EXACT`, including zero. At most
one ordinal may use `CAPTURE_NOW`. The list may also be empty, which requests
an ADR-041 root-only manifest. Descriptor ordinals determine only the exact
root-relative reference; their numeric order does not assert SEC chronology.

`SELECT_EXACT` performs no provider access. Before any possible dispatch, WSR
must reconstruct the selected root and every exact selected ADR-040 segment
and verify all ADR-041 root, descriptor, parser, body-retention, capture-ID,
and uniqueness bindings. A missing, latest-by-filename, equal-looking,
same-CIK, or caller-reconstructed substitute is rejected.

The V9 foreign keys are part of command admission. If the referenced root,
descriptor, or selected segment is absent while the plan and action rows are
claimed, the whole claim transaction is rejected with a sanitized
exact-evidence error: no attempt, dispatch, or terminal row is retained. The
closed `EXACT_EVIDENCE_VALIDATION_FAILED` terminal is reserved for an admitted
plan whose foreign-key-existing evidence nevertheless cannot be reconstructed
or verified by the application after claim. Both paths are zero-network.

`CAPTURE_NOW`, when present, identifies only the exact persisted root and one
captured descriptor ordinal. It accepts no caller-provided URL, filename, host,
query, or fragment. After all zero-network evidence and plan validation have
succeeded, WSR may cross the durable dispatch boundary and enter the provider
gate once. If the gate permits the invocation, it calls the existing ADR-040
operation `CAPTURE_HISTORICAL_SEGMENT` once. There is no descriptor loop. The
resulting exact durable segment capture joins the `SELECT_EXACT` captures only
after its response passes the existing strict parser, receipt, root binding,
and local append rules.

The final ADR-041 manifest selection consists exactly of the valid
`SELECT_EXACT` pairs plus the successful `CAPTURE_NOW` result, ordered under
ADR-041's local deterministic rule. Omitted root descriptors remain
`NOT_SELECTED`. A failed `CAPTURE_NOW` creates no manifest and does not fall
back to a prior capture. With no `CAPTURE_NOW`, the entire command remains
zero-network and does not require the SEC provider to be enabled.

### One-provider-invocation boundary

For each new attempt:

- `CAPTURE_ROOT` requires zero or one root provider invocation;
- `COLLECT_EXACT_ROOT` with no `CAPTURE_NOW` requires zero provider
  invocations; and
- `COLLECT_EXACT_ROOT` with one `CAPTURE_NOW` requires zero or one historical
  segment provider invocation.

Invalid input, missing exact evidence, duplicate action ordinals, multiple
`CAPTURE_NOW` actions, provider configuration failure, active cooldown, or
local pre-network failure performs no SEC request. Exact-evidence failures have
no dispatch; a provider-gate refusal may have a durable dispatch but records
`PROVIDER_INVOCATION_NOT_STARTED`. Provider HTTP failure, timeout, unreadable
response, parser failure, or post-response local failure never triggers
another request. There is no retry, root revalidation request, conditional
request, descriptor fan-out, alternate host, nightly ZIP fallback, screen
scrape, or another provider.

### V9 immutable attempt ledger

Flyway V9 adds four application append-only tables:

- `sec_filing_collection_attempts` retains one immutable plan header per
  attempt and makes the operator request UUID unique;
- `sec_filing_collection_attempt_descriptor_actions` retains zero or more
  canonical descriptor actions;
- `sec_filing_collection_attempt_provider_dispatches` retains zero or one
  provider dispatch per attempt; and
- `sec_filing_collection_attempt_outcomes` retains zero or one terminal
  outcome per attempt.

The dispatch and outcome use the attempt identity as their primary key; they
do not invent separate domain identities. The ledger has no application update
or delete method. Exact root, descriptor, selected segment, produced root,
produced segment, and produced manifest references use foreign keys to their
existing durable source rows with `ON DELETE RESTRICT`.

The plan records at least:

- schema, provider, product, and policy identity;
- the exact `attemptId`, `operatorRequestId`, and `commandSha256`;
- command kind and normalized command inputs;
- caller-supplied action kind, exact descriptor ordinal, and exact selected
  segment capture ID when applicable; and
- the exact descriptor action count and fixed `maxProviderInvocations = 1`;
  and
- injected-clock `requestedAt` in UTC at PostgreSQL-compatible microsecond
  precision.

Action rows are keyed by the exact root descriptor ordinal and are replayed in
ascending ordinal order. A selected subset need not have contiguous descriptor
ordinals. Database constraints close action-kind and nullable-ID combinations,
prohibit duplicate descriptor ordinals, allow no more than one `CAPTURE_NOW`,
and bind exact selected captures back to their root and descriptor identities.
Repository reconstruction requires the header's descriptor action count to
equal the exact child-row count and rechecks their order and content.

Outcome checks close every command/stage/disposition combination that can be
decided from the outcome row and its exact dispatch foreign keys. The remaining
compatibility questions that depend on inspecting child actions—whether an
undispatched collection really is selection-only and whether a provider-gate
failure really had one `CAPTURE_NOW` action—are revalidated during repository
reconstruction and fail closed. V9 exposes no independent raw-SQL writer; any
future external writer or multi-service ledger must first add an immutable
action summary with an exact foreign-key binding rather than bypassing that
reconstruction boundary.

When a provider invocation may begin, WSR first commits exactly one dispatch
row before handing control to the provider port. The dispatch records the
closed operation kind `CAPTURE_ROOT` or `CAPTURE_HISTORICAL_SEGMENT`, its exact
approved source identity, and `dispatchedAt`, with:

```text
requestedAt <= dispatchedAt
```

The row means only that WSR durably crossed its local provider-port dispatch
boundary. The provider's mutex, cooldown, disabled/configuration gate, or
transport may still refuse before HTTP starts. The row does not prove that an
HTTP request left the process, reached SEC, was accepted, or received a
response.

The separate single-JVM attempt mutex is acquired nonblockingly before this
dispatch. Mutex contention can therefore close the provider gate with no
dispatch row. After a dispatch exists, the provider port's process-local
cooldown or request limiter can still close before HTTP starts. Both outcomes
use `PROVIDER_INVOCATION_NOT_STARTED`; the optional dispatch distinguishes
which local boundary had already been crossed.

A terminal row has exactly one status:

```text
SUCCEEDED
FAILED_KNOWN
```

It records `completedAt`, a closed stage, provider disposition, artifact
identities and append outcomes, and a closed sanitized failure code when
applicable. Timestamps obey:

```text
requestedAt <= completedAt
dispatchedAt <= completedAt, when a dispatch exists
```

The closed stage vocabulary is:

```text
EXACT_EVIDENCE_VALIDATION
PROVIDER_GATE
ROOT_CAPTURE
SEGMENT_CAPTURE
MANIFEST_ASSEMBLY
LOCAL_COMMIT
```

Provider disposition is exactly:

```text
NO_PROVIDER_INVOCATION
PROVIDER_INVOCATION_NOT_STARTED
PROVIDER_RESPONSE_RECEIVED
PROVIDER_START_OR_RESPONSE_UNKNOWN
```

`NO_PROVIDER_INVOCATION` requires no dispatch.
`PROVIDER_INVOCATION_NOT_STARTED` permits either no dispatch or the one durable
provider-port dispatch. `PROVIDER_RESPONSE_RECEIVED` and
`PROVIDER_START_OR_RESPONSE_UNKNOWN` require that dispatch.

`FAILED_KNOWN` means that WSR knows the local command did not produce its
declared successful artifact set. It does not imply knowledge of whether SEC
received a request. In particular, an I/O failure may be terminal locally with
`PROVIDER_START_OR_RESPONSE_UNKNOWN`.

The closed failure-code vocabulary is:

```text
EXACT_EVIDENCE_VALIDATION_FAILED
PROVIDER_GATE_CLOSED
PROVIDER_REQUEST_FAILED
PROVIDER_HTTP_STATUS
PROVIDER_RESPONSE_UNREADABLE
PROVIDER_RESPONSE_TOO_LARGE
PROVIDER_RESPONSE_INVALID
SOURCE_CAPTURE_PERSISTENCE_FAILED
MANIFEST_ASSEMBLY_FAILED
LOCAL_PERSISTENCE_FAILED
```

Each failure code is compatible only with its declared stage and disposition.
The three artifact slots are `rootArtifact`, `segmentArtifact`, and
`manifestArtifact`. Each uses one closed append state:

```text
NOT_APPLICABLE
INSERTED
IDENTICAL_REPLAY
```

An applicable successful slot also carries its exact durable artifact ID.
Command-kind checks prohibit impossible combinations. No terminal stores raw
provider bodies, contact email, full `User-Agent`, arbitrary headers, database
credentials, or unsanitized exception text.

The lifecycle is derived without mutating the plan:

```text
PLANNED
PROVIDER_DISPATCHED_INDETERMINATE
TERMINAL_SUCCEEDED
TERMINAL_FAILED_KNOWN
```

`PLANNED` has a plan but neither dispatch nor terminal.
`PROVIDER_DISPATCHED_INDETERMINATE` has a durable dispatch but no terminal.
The two terminal states follow the immutable terminal status.

### Dispatch uncertainty and no automatic recovery

A process, host, JVM, or database interruption after dispatch and before the
terminal commit can leave `PROVIDER_DISPATCHED_INDETERMINATE`. WSR must not
infer whether the provider invocation started or whether a response existed.
The same `operatorRequestId` returns that durable state with zero network
activity. It is never automatically resumed, retried, converted to success,
failed, expired, or superseded.

ADR-042 intentionally defines no `ABANDON`, `CANCEL`, `RESET`, `RESUME`, or
terminal-repair method. It also defines no timeout that changes lifecycle
state. Operator investigation and any future explicit resolution contract are
separate gates. A process restart must not use a missing terminal as permission
to contact SEC again.

### Post-response local atomic committer

No database transaction remains open across provider I/O. After a provider
response has been fully received and validated, one local transaction commits
the complete successful artifact set and the successful terminal together:

- `CAPTURE_ROOT`: exact root capture plus attempt terminal;
- zero-network `COLLECT_EXACT_ROOT`: exact manifest plus attempt terminal; or
- `COLLECT_EXACT_ROOT` with `CAPTURE_NOW`: exact segment capture, exact
  root-relative manifest, and attempt terminal.

Existing ADR-039, ADR-040, and ADR-041 replay verification, exact identity,
concurrency convergence, and append-conflict rules remain mandatory inside the
committer. A failure rolls back that transaction instead of exposing an
artifact set without its terminal. WSR may then append one separate
`FAILED_KNOWN` terminal describing the known local failure; it does not repeat
the provider invocation. If interruption prevents either terminal from being
committed after dispatch, the durable state remains indeterminate.

For failures before any provider dispatch, plan and failure terminal may be
committed locally without a dispatch row. A zero-network command commits its
manifest and successful terminal atomically. No partial root, partial segment,
partial manifest, success-with-missing-artifact, or last-write-wins repair is
allowed.

### Point-in-time meaning

The attempt ledger preserves command-processing times; it does not create an
SEC-authored event time or atomic source `asOf`. `requestedAt` is when WSR
accepted the new plan, `dispatchedAt` is its local provider handoff boundary,
and `completedAt` is when the immutable terminal became knowable. The capture
and manifest artifacts retain their existing source and evidence-availability
times.

A successful collection attempt is visible no earlier than its durable
terminal `completedAt`. It proves only that the exact declared local artifacts
committed under this policy. It does not prove that root and segment bytes came
from one SEC snapshot, that all descriptors were selected, that ranges are
continuous or disjoint, that no correction or removal exists, or that the
manifest is current, complete, or legally authoritative history.

### Single-JVM fair-access boundary

Every permitted provider invocation continues through ADR-036's one shared
process-local limiter: at most eight requests per second with fixed 125 ms
spacing and no accumulated burst. The existing 8 MiB decoded-response bound
and shared process-local `429`/`Retry-After` cooldown also remain in force.
ADR-042 adds no retry path and does not reinterpret a non-`429` denial as a
documented SEC status contract.

This gate is approved for one application JVM only. The eight-request local
limit is deliberately below SEC's aggregate ten-request ceiling but does not
coordinate another JVM, container, host, shell, or tool. Multi-replica live
activation remains prohibited until one reviewed distributed coordinator owns
the aggregate budget and cooldown for every SEC caller. Operators must not use
parallel processes or restarts to evade spacing or cooldown.

### Credentials and explicit operator requirements

No SEC API key, provider account, paid plan, OAuth credential, EDGAR filer or
user token, registration, browser secret, or plugin is required.

Before requesting a new `CAPTURE_ROOT` attempt or a `COLLECT_EXACT_ROOT`
attempt containing `CAPTURE_NOW`, the operator must provide:

```text
SEC_PROVIDER_ENABLED=true
SEC_BASE_URL=https://data.sec.gov
SEC_CONTACT_EMAIL=<real monitored operational email>
```

`SEC_CONTACT_EMAIL` is server-only identification for the existing declared
`User-Agent`, not an authentication credential. The operator places the local
value only in the repository root `.env`; a deployed value comes from the
deployment secret store. It must not be pasted into chat, committed, printed,
returned to the browser, or included in terminal failure detail. A loopback
base URL is permitted only by the existing test boundary and is not a runtime
fallback.

A same-UUID replay and a `COLLECT_EXACT_ROOT` command containing only
`SELECT_EXACT` actions require no SEC provider configuration and perform zero
network requests. All commands require the existing PostgreSQL connection.
Local PostgreSQL settings remain `POSTGRES_HOST`, `POSTGRES_PORT`,
`POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`; local values belong in
the root `.env`, and deployed secrets belong in the deployment secret store.

Configuration never triggers a command. ADR-042 adds only an application
service boundary. There is no scheduler, startup hook, polling cadence,
controller, OpenAPI operation, command-line runner, browser call, background
worker, queue consumer, webhook, or public UI.

## Failure and no-fallback policy

Invalid UUID or CIK, command-digest disagreement, invalid root or action list,
missing or unverifiable exact evidence, provider disabled or misconfigured,
active cooldown, unsafe URI, non-200 response, timeout, unknown provider start,
unreadable or oversized body, parser failure, append conflict, manifest
conflict, ledger conflict, timestamp violation, or atomic local commit failure
cannot produce a successful terminal.

There is no prior-capture fallback, latest-capture lookup, stale manifest,
fixture substitution, empty-list repair, omitted-descriptor inference,
root-only fallback after requested segment failure, automatic retry,
descriptor loop, alternate provider, nightly ZIP, partial artifact salvage, or
conflict winner. Known failures use closed sanitized codes; indeterminate
dispatch has no invented terminal failure.

## Official-documentation boundary

The following are WSR V1 choices, not SEC guarantees:

- the two command names and action vocabulary;
- one-invocation attempt budgeting;
- caller-supplied UUID idempotency and command digest;
- canonical action ordering and exact-root selection;
- plan-before-dispatch and immutable terminal ledger semantics;
- the meaning of dispatch, terminal status, stage, disposition, and artifact
  append state;
- no-resume handling of indeterminate dispatch;
- post-response local artifact/terminal atomicity;
- eight-request single-JVM spacing and local cooldown behavior; and
- every collection or reconciliation conclusion across separately received
  root and additional-file responses.

SEC does not document `429` or `Retry-After` as universal response contracts,
does not provide an exactly-once request token, and does not provide a common
snapshot identity for submissions root and additional files. Its silence on
descriptor order, overlap, continuity, stability, correction propagation, and
atomicity must not be converted into a positive guarantee. A current live
observation tests only the observed response, not a perpetual provider
contract.

## Non-scope and next sequence

ADR-042 adds no scheduler, automatic retry, multi-replica coordinator,
distributed lock, queue, outbox dispatcher, abandonment workflow, terminal
repair, polling frequency, recurring collection, fetch-all loop, root
revalidation, bulk ZIP ingestion, correction/removal causality, filing text,
exhibit, XBRL, Company Facts join, controller, public read API, authentication,
Korean UI, notification, or public freshness SLA.

A later gate may expose operator invocation, inspect unresolved attempts, or
add a globally coordinated collector, but it must define authority and recovery
for indeterminate dispatch without weakening exact evidence, provider fairness,
or point-in-time disclosure. Public API and UI work must separately define
authentication, source attribution, conflict presentation, freshness, and the
meaning of incomplete root-relative coverage.

## Consequences

- An operator can request one exact root capture or one exact-root collection
  without authorizing an unbounded historical crawl.
- A durable dispatch boundary makes crash uncertainty visible instead of
  causing an implicit retry or false exactly-once claim.
- Same-UUID replays are deterministic and zero-network, while new UUIDs remain
  explicit new operator decisions.
- An artifact newly inserted by this attempt commits atomically with its
  successful terminal; an `IDENTICAL_REPLAY` terminal instead references the
  already-durable exact artifact without claiming that this attempt inserted it.
- This gate remains deliberately unsuitable for multi-replica, scheduled, or
  autonomous production collection.

## Verification contract

Focused coverage must prove command canonicalization, nonzero lowercase UUID
validation, same-UUID zero-network replay, changed-command conflict, exact
action sorting, duplicate-ordinal and multiple-`CAPTURE_NOW` rejection,
zero-provider `SELECT_EXACT` assembly, exact evidence binding, root-only
manifest assembly, one root or one segment invocation, pre-dispatch failures,
durable dispatch before provider handoff, no retry, indeterminate recovery,
post-response atomic artifact/terminal commit, rollback, closed terminal
combinations, UTC microsecond timestamp checks, concurrent idempotent replay,
V9 foreign keys and append-only constraints, shared ADR-036 limiter use, and
absence of SEC traffic from default tests and CI.
