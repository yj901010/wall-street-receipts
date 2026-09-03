# ADR-040 — SEC EDGAR Historical Segment Capture Persistence

- Status: Accepted
- Date: 2026-08-25

ADR-040 makes one explicitly selected SEC historical submissions segment an
append-only, exactly replayable evidence capture without treating it as a
complete filing history or merging it with any other capture.

## Context

ADR-038 preserves each `filings.files` member as advertised metadata under an
exact root catalog capture. ADR-039 durably retains that root's decoded bytes,
receipt, recent filings, and provider-ordered descriptors. Neither decision
requests a referenced file, observes its rows, verifies its advertised count or
date endpoints, or authorizes historical fan-out.

The SEC's
[EDGAR API documentation](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)
documents the current per-entity root at
`https://data.sec.gov/submissions/CIK##########.json` and says that, when an
entity has additional filings, `files` contains additional JSON files and the
date range each contains. It does not publish a normative historical-segment
URL template, filename grammar, exact top-level JSON schema, field types,
cardinality rule, date-range containment rule, ordering rule, overlap rule,
immutability guarantee, or completeness algorithm.

This decision therefore separates SEC-documented facts from a deliberately
narrow WSR V1 local contract. The local contract can fail closed or record a
mismatch when current source evidence disagrees with it, but it must not be
described as an SEC guarantee.

## Decision

ADR-040 implements one explicit operation:

```text
exact persisted rootCaptureId + descriptor ordinal
    -> zero or one SEC GET
    -> one exact historical-segment capture append attempt
```

Invalid or missing input performs no provider request. Valid input performs at
most one GET. There is no descriptor loop, automatic retry, conditional
request, alternate source, or fallback.

### Exact selected product and parser

The segment capture uses:

```text
provider: sec-edgar
product: edgar-submissions-historical-segment-api
parser: SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1
```

The segment parser is intentionally distinct from root parser
`SEC_SUBMISSIONS_CATALOG_V2`. A segment has a separate wire envelope and a
separate receipt; the root receipt cannot authenticate, hash, replay, or
describe bytes returned by the later segment request.

WSR V1 reads these required top-level parallel arrays:

- `accessionNumber`;
- `filingDate`;
- `reportDate`;
- `acceptanceDateTime`;
- `act`;
- `form`;
- `fileNumber`;
- `filmNumber`;
- `items`;
- `size`;
- `isXBRL`;
- `isInlineXBRL`;
- `primaryDocument`; and
- `primaryDocDescription`.

Every array must be present and all arrays must have identical cardinality.
Scalar coercion, floating-point-to-integer coercion, duplicate JSON keys,
trailing JSON tokens, malformed dates or timestamps, invalid nonempty
primary-document paths, null required elements other than the selected absence
cases, and duplicate accession identity within one segment fail the whole
parse. `reportDate` retains the existing explicit absence representation.
Unknown vendor fields remain behind the DTO boundary and do not silently
expand the canonical model.

A manual opt-in Apple segment observation established that a historical row
can contain an empty `primaryDocument`. WSR V1 preserves that exact observed
absence as a nullable `primaryDocumentUri` in the segment-only
`HistoricalFilingRecord`. A nonempty value must still be trimmed, path-safe,
and mapped to the canonical SEC Archives URI. The root catalog's
`FilingRecord` contract is unchanged: root recent rows still require a
nonempty canonical primary-document URI. Historical absence is not converted
to an empty URI, synthesized document name, root value, or zero-value
substitute.

Historical canonical rows preserve provider order and the selected filing
identity, form, dates, acceptance time, and nullable document evidence. They do
not retain or invent unselected vendor values. The catalog CIK, not the first
ten digits of an accession, binds a nonnull canonical SEC Archives entity
directory. SEC documents that an accession prefix may identify a third-party
submitting agent rather than the filing entity in
[Accessing EDGAR Data](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data).

### Exact root-descriptor binding and local URI resolution

The application accepts only a lowercase-SHA-256 `rootCaptureId` and a
nonnegative descriptor ordinal. It reconstructs the exact durable ADR-039 root
capture from PostgreSQL and requires:

- provider `sec-edgar`;
- root product `edgar-submissions-api`;
- root parser `SEC_SUBMISSIONS_CATALOG_V2`;
- root body retention `DURABLE_DECODED_BODY_RETAINED`; and
- an ordinal that exists in that root's immutable provider-ordered descriptor
  list.

The caller cannot submit a CIK, filename, URL, host, query, or fragment. The
selected descriptor must still match the ADR-038 CIK-bound filename allowlist.
WSR then applies this exact local URI resolution rule:

```text
https://data.sec.gov/submissions/{captured descriptor fileName}
```

For example:

```text
https://data.sec.gov/submissions/CIK0000320193-submissions-001.json
```

This exact resolution and the
`CIK##########-submissions-###.json` grammar are WSR V1 safety contracts, not
normative URL or filename guarantees stated by the SEC narrative
documentation. A segment capture stores the exact root capture ID, root capture
time, descriptor ordinal, CIK, filename, advertised count, and advertised date
endpoints. Flyway V7 binds that complete tuple back to the persisted root
descriptor with a foreign key. A later root capture, a same-CIK latest root, or
a caller-reconstructed descriptor cannot replace that identity.

### Exact decoded-body receipt

Only an HTTP `200` response with one accepted `application/json` media type and
a nonempty decoded body no larger than 8 MiB can become a segment capture. The
existing transport accepts identity, gzip/x-gzip, or deflate and removes the
declared transport encoding once. The resulting exact decoded HTTP entity bytes
are then:

1. validated as strict UTF-8 without NUL bytes;
2. defensively owned without charset, whitespace, key-order, or JSON
   normalization;
3. hashed as lowercase SHA-256;
4. parsed by `SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1`; and
5. retained as the bytes used for exact replay.

The independent `SourceResponseReceipt` preserves only the approved response
metadata: provider, product, exact source URI, status `200`, media type,
transport content encoding, optional bounded opaque `ETag`, optional
`Last-Modified`, parser version, decoded-body SHA-256, decoded length,
`capturedAt`, body representation, and body-retention state. Request headers,
the configured contact email, complete `User-Agent`, cookies, tokens, response
body text, and arbitrary headers are not receipt fields or log fields.

The receipt transitions from
`DECODED_BODY_ATTACHED_PENDING_PERSISTENCE` to
`DURABLE_DECODED_BODY_RETAINED` only inside the verified repository append.
SHA-256 identifies exact decoded bytes locally. It is not an SEC signature,
sender authentication, filing truth guarantee, or proof that a named segment
will remain unchanged.

### Point-in-time meaning

The persisted root descriptor became knowable at `rootCapturedAt`; its segment
rows become knowable only when the later segment response has been completely
read. The live adapter supplies that received instant as both segment
`processingTime` and `capturedAt`, at database-compatible microsecond
precision. The durable contract requires:

```text
rootCapturedAt <= processingTime <= capturedAt
acceptedAt <= processingTime
```

No filing date, report date, acceptance time, advertised date, root capture
time, or current clock value may backdate segment availability. A point-in-time
read selects the latest exact capture for one root capture ID, descriptor
ordinal, and parser version only when `capturedAt <= evaluationAsOf`, ordered
by `capturedAt DESC, segmentCaptureId DESC`.

This is a segment-specific repository read. A root and its later segment are
not an SEC-provided atomic snapshot, and this decision defines no combined
catalog `asOf` or current-history view.

### Observed count and inclusive date-range comparison

The capture derives from parsed provider-order rows:

- `observedFilingCount`, equal to row cardinality;
- `observedFilingFrom`, equal to the minimum observed `filingDate`; and
- `observedFilingTo`, equal to the maximum observed `filingDate`.

An empty segment has count zero and null observed endpoints. WSR V1 compares
the observed count for exact equality and asks whether every observed
`filingDate` is inside the descriptor's inclusive advertised interval:

```text
advertisedFilingFrom <= filingDate <= advertisedFilingTo
```

For a nonempty segment, this containment is equivalently checked by requiring
the separately preserved observed minimum to be no earlier than
`advertisedFilingFrom` and the observed maximum to be no later than
`advertisedFilingTo`. The observed extrema are evidence; they are not required
to equal the advertised endpoints. An empty set has no out-of-range filing
date, so its range comparison is contained, while its zero count mismatches the
descriptor's required positive advertised count. The comparison records one
of four closed states:

```text
MATCHES_ADVERTISED
COUNT_MISMATCH
RANGE_MISMATCH
COUNT_AND_RANGE_MISMATCH
```

`MATCHES_ADVERTISED` means only that observed row count equals the captured
advertised count and every observed filing date falls inside the captured
inclusive interval under this parser version. `COUNT_MISMATCH` includes an
empty observed segment because its range is vacuously contained but its count
cannot equal a positive descriptor count. `RANGE_MISMATCH` means at least one
observed date falls outside the interval while the count agrees;
`COUNT_AND_RANGE_MISMATCH` means both checks disagree.

The manual Apple segment observation also established that advertised
`filingFrom`/`filingTo` need not equal actual observed minimum/maximum filing
dates. The inclusive-containment comparison is therefore the live-observed WSR
V1 local diagnostic. The SEC narrative documentation does not guarantee this
containment rule, advertised count equality, or any meaning for the endpoints
beyond describing a date range. None of the four states is a source-authored
warranty.

A mismatch does not discard otherwise structurally valid source evidence. The
exact body, receipt, root binding, provider-order rows, observed values, and
mismatch state are appended and replayed. A mismatch cannot be relabeled as an
empty success, corrected from the descriptor, normalized to the advertised
values, or used as a completeness claim. Structurally invalid JSON or invalid
canonical rows still fail before append; this decision adds no rejected-body
quarantine.

### Append-only V7 persistence and replay

Flyway V7 reuses the content-addressed `sec_decoded_response_bodies` table and
adds:

- `sec_historical_filing_segment_captures` for exact root binding, receipt,
  observed comparison, and immutable capture identity; and
- `sec_historical_filing_segment_filings` for provider-order canonical rows
  under contiguous zero-based ordinals.

`HistoricalFilingSegmentCapture` derives `segmentCaptureId` from a versioned,
length-prefixed identity including provider, product, exact root identity and
time, descriptor identity and advertised values, segment URI and times, body
digest, and body length. Its natural database identity is:

```text
(root_capture_id, descriptor_ordinal, source_uri, captured_at)
```

Append behavior is closed:

- the first complete valid observation returns `INSERTED`;
- an exact complete replay returns `IDENTICAL_REPLAY` and writes no duplicate;
- the same bytes observed later create another immutable capture while sharing
  the content-addressed body; and
- a reused natural identity or capture ID with different bytes, receipt,
  descriptor, comparison, or rows fails as a conflict.

One transaction verifies the pending capture against its exact durable root,
promotes body retention, verifies exact-body parser replay, ensures the body,
inserts the segment root and ordered rows, reconstructs the stored aggregate,
and requires exact round-trip equality. A failure rolls back the new body,
segment root, and children together. Read reconstruction rechecks child count,
contiguous provider order, observed extrema, four-state comparison, capture ID,
root foreign-key identity, decoded bytes, digest, length, and parser replay.
There is no repository update, delete, last-write-wins, reserialization
fallback, or partial-row salvage path. As in ADR-039, this is an application
append-only contract, not a claim that Flyway V7 makes a database administrator
or an independently privileged SQL principal cryptographically incapable of
mutation. Database-role restriction, audited administration, and any true WORM
storage tier remain separate operational controls.

### No union and no completeness

One successful segment capture proves only what was observed from one exact URL
at one capture time and how that observation compares with one exact persisted
root descriptor. Even `MATCHES_ADVERTISED` does not prove:

- that another advertised segment was fetched or is valid;
- absence of accession duplicates or conflicts across recent and historical
  captures;
- descriptor range continuity, non-overlap, chronology, or suffix meaning;
- absence of later SEC corrections, removals, or changed bytes;
- root/segment atomicity;
- all-time, current, or complete entity filing history; or
- coverage of filings not available through this selected API product.

ADR-038 root status remains immutable. Fetching one segment does not update
`RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED`, mutate the root projection, merge
rows, deduplicate accessions, sum counts, or create a new complete-history
status. An empty descriptor list still means only that the captured root
advertised no additional segment.

## Operations and credentials

This public read product needs no SEC API key, provider account, paid plan,
OAuth credential, EDGAR filer/user token, registration, plugin, or new
environment variable. Enabled live use continues to require the existing
monitored `SEC_CONTACT_EMAIL` only for the server-side declared `User-Agent`.
It is operational contact configuration, not a data credential. Persistence
continues to use the application's existing PostgreSQL connection and secret
handling.

The one GET passes through the existing response-size, 8-request-per-second
process-local spacing, and `429`/`Retry-After` cooldown controls. SEC's official
[fair-access guidance](https://www.sec.gov/about/developer-resources) limits a
user to no more than 10 requests per second in aggregate regardless of machine
count and asks automated users to download only what they need. A process-local
limiter does not prove multi-instance aggregate compliance.

This operation performs no automatic retry. Configuration does not trigger a
request: there is no scheduler, startup collector, poller, descriptor fan-out,
controller, command-line runner, or browser invocation.

## Failure and no-fallback policy

A missing root, non-durable or unsupported root, invalid ordinal, mismatched
descriptor, unsafe URI, non-200 response, unreadable or oversized body,
unsupported media/encoding, invalid UTF-8/JSON, parser or canonical failure,
duplicate accession within the segment, exact-replay disagreement, database
conflict, or atomic append failure stops the operation. There is no stale
segment, root-recent substitution, fixture, empty list, nightly ZIP, alternate
provider, screen scraping, inferred value, automatic retry, or previous-capture
fallback.

Sanitized provider failures must not include body content, the contact email,
full `User-Agent`, arbitrary headers, database credentials, local transport
details, or source filing text.

## Official-documentation boundary

The following remain explicit WSR V1 assumptions or diagnostics rather than
claims about what SEC guarantees:

- exact descriptor filename regex and suffix meaning;
- exact `/submissions/{fileName}` resolution;
- the selected top-level 14-array JSON shape and types;
- parallel-array equality and provider-order meaning;
- advertised count equality;
- advertised inclusive containment of every observed filing date;
- range continuity, disjointness, or ordering;
- segment stability, revision identity, or validator semantics;
- absence of duplicate accessions across resources; and
- root-relative or global historical completeness.

The SEC API page and
[Accessing EDGAR Data](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)
are the primary operating references. A current live response can test today's
wire shape but cannot convert an undocumented behavior into a perpetual SEC
contract.

## Non-scope and next sequence

ADR-040 adds no request loop over all descriptors, collection manifest, root
revalidation after segment fetch, recent-plus-history union, cross-segment
accession comparison, duplicate reconciliation, amendment/correction linkage,
complete-history state, derived filing total, or public freshness meaning. It
also adds no nightly `submissions.zip`, filing document, exhibit, full text,
XBRL, or Company Facts ingestion.

There is no scheduler, polling cadence, global multi-replica coordinator,
automatic retry owner, production collector, controller, OpenAPI contract,
public read API, browser access, Korean UI, raw-body download, cache mirror,
retention deletion, encryption, object storage, or archival tiering.

The next SEC gate may define a collection manifest that explicitly selects one
capture per descriptor, preserves every source occurrence, compares accessions
across root and segments, and names only a root-relative non-atomic verification
state. It still must not call that state complete SEC history without a separate
evidence and correction policy. Scheduler/global coordination, read API, and
attributed public UI remain later independent gates.

## Consequences

- One durable root descriptor can now lead to one bounded, attributable,
  exactly replayable historical response without allowing caller-controlled
  URLs or autonomous fan-out.
- Mismatch evidence remains observable instead of being discarded or repaired
  into agreement with advertised metadata.
- Exact body retention and ordered rows consume private PostgreSQL storage and
  inherit ADR-039's retention, backup, access, and disposal obligations.
- Point-in-time reads cannot backdate a later segment observation to the root's
  earlier capture time.
- The gate improves historical source evidence while deliberately adding no
  union, completeness, credential, scheduler, API, or UI behavior.

## Verification contract

Focused coverage must exercise exact persisted-root lookup, invalid-input
zero-request behavior, one-request path resolution, strict segment wire
parsing, empty `primaryDocument` to nullable historical URI preservation,
unchanged nonnull root `FilingRecord`, independent receipt/body ownership, all
four advertised comparison states, inclusive containment with independently
preserved observed extrema, empty-segment `COUNT_MISMATCH` evidence,
pending-to-durable promotion, exact replay, idempotent and conflicting append,
later observation, atomic rollback, provider-order reconstruction, nullable V7
historical document URI and other foreign-key/check constraints, and
parser-specific point-in-time selection. External SEC access remains absent
from CI; any opt-in live smoke is bounded to one root and at most one selected
segment and is not completeness evidence. Final verification totals are
recorded separately after the relevant commands complete.
