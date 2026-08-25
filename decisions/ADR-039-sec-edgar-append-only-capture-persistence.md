# ADR-039 — SEC EDGAR Append-Only Capture Persistence

- Status: Accepted
- Date: 2026-08-25

ADR-039 makes one accepted SEC EDGAR Submissions root capture durably replayable
without turning advertised historical-segment metadata into observed history.

## Context

ADR-037 bound an in-memory receipt to the exact decoded HTTP entity bytes read
by a versioned parser. ADR-038 mapped both `filings.recent` and the advertised
`filings.files` descriptors from those same bytes, but deliberately retained
neither the body nor the catalog after the process ended. A digest without the
bytes cannot reproduce the projection, and overwriting one root response with
a later response would erase what was knowable at the earlier capture time.

This decision adds an append-only PostgreSQL boundary for the root receipt,
exact decoded body, provider-ordered recent filings, and provider-ordered
historical descriptors. It does not fetch a referenced segment or claim that
the root catalog is complete filing history.

## Exact durable representation

Flyway V6 adds four related tables:

- `sec_decoded_response_bodies` stores the exact decoded HTTP entity body as
  PostgreSQL `BYTEA`, keyed by its lowercase SHA-256. The stored length must be
  positive, no greater than the existing 8 MiB decoded-response limit, and
  equal `OCTET_LENGTH(decoded_body)`;
- `sec_filing_catalog_captures` stores schema version `1.0.0`, capture identity,
  catalog identity and times, the complete ADR-037 response-metadata allowlist,
  parser version, body digest and length, child counts, and the exact ADR-038
  historical-segment status;
- `sec_filing_catalog_recent_filings` stores each canonical recent filing under
  its root capture with a zero-based ordinal; and
- `sec_filing_catalog_historical_segments` stores each advertised descriptor
  under its root capture with a zero-based ordinal.

The body table is content-addressed. Separate observations of identical bytes
share one body row, while each observation keeps its own root capture. A root
references both the digest and length with `ON DELETE RESTRICT`; SHA-256 remains
a local byte-identity key, not an SEC signature or proof of sender identity.

The child tables repeat the root CIK, processing time, and capture time in
composite foreign keys. Database checks preserve accession identity, accepted
time, descriptor filename/CIK binding, advertised positive count and ordered
date range, root status/count coherence, and UTC microsecond-compatible
timestamp columns. Provider order is never inferred from dates or filenames;
it is reconstructed only from contiguous ordinals.

## Retention-state transition

The existing non-persistence read path remains literal
`RECEIPT_ONLY_BODY_NOT_RETAINED`: it returns a catalog after transient parsing
and does not claim that its body is durable.

The persistence path returns a `FilingCatalogCapture` containing a defensive
copy of the exact decoded bytes and marks its receipt
`DECODED_BODY_ATTACHED_PENDING_PERSISTENCE`. This state means that the bytes are
attached to the in-process aggregate but have not yet crossed the transaction
boundary. It must not be presented as durable evidence.

The repository verifies that pending aggregate, promotes the receipt to
`DURABLE_DECODED_BODY_RETAINED`, verifies it again, and writes the durable form.
Only persisted rows and reconstructed reads carry the durable state. A failed
write leaves no durable-state object in the database.

## Capture identity, revisions, and idempotency

`FilingCatalogCapture` derives `captureId` as lowercase SHA-256 over a
versioned, length-prefixed identity containing provider, product, CIK, source
URI, `capturedAt`, decoded-body digest, and decoded-body length. Body-retention
state is deliberately not part of the identity, so pending and durable forms
identify the same observation. Length prefixes prevent ambiguous string
concatenation.

The database also enforces one natural capture at
`(provider, product, source_uri, captured_at)`. Append behavior is closed:

- the first valid observation returns `INSERTED`;
- an exact replay of the complete durable aggregate returns
  `IDENTICAL_REPLAY` and writes no second root or child set;
- the same bytes observed at a later `capturedAt` create a new root capture but
  reuse the content-addressed body row; and
- a reused natural identity or `captureId` with different bytes, receipt, root
  projection, or children fails as a conflict.

A later SEC response is therefore a new immutable observation, not an update
or implicit correction of an earlier capture. The repository exposes no
update or delete method. The schema's keys, checks, immutable markers, and
restricting foreign key support that application contract, but this is not a
WORM database: privileged database administration remains outside the
repository boundary and must be separately governed.

## Atomic append and concurrency

One repository transaction verifies the candidate, ensures the
content-addressed body, inserts the root, inserts all recent rows, inserts all
descriptor rows, reconstructs the stored aggregate, and checks exact
round-trip equality. A child failure rolls back the new body and root together;
partial catalogs are not committed.

On PostgreSQL, body and root inserts use conflict-aware append semantics and
re-read the winning row. Concurrent identical appends converge to one
`INSERTED` and one `IDENTICAL_REPLAY`; a non-identical winner fails closed as a
conflict. No last-write-wins update or partial-child salvage exists.

## Exact-byte replay verification

The SEC replay verifier accepts only the selected provider, product, and parser
contract `SEC_SUBMISSIONS_CATALOG_V2`. It parses the retained decoded bytes with
the same strict reader, using the stored receipt and processing time, and
requires the complete replayed `FilingCatalog` to equal the stored canonical
projection.

Before parsing, the verifier also revalidates the exact official
`https://data.sec.gov/submissions/CIK##########.json` source, an
`application/json` media type with no charset or UTF-8 only, the 8 MiB decoded
limit, and strict UTF-8 bytes. A forged origin, incompatible media envelope, or
oversized/non-UTF-8 body cannot enter persistence through a directly
constructed aggregate even though the normal provider already enforces the
same constraints.

Append verifies before and after the retention promotion. Read reconstruction
also verifies root counts and status, contiguous child order, reproducible
`captureId`, decoded-body digest and length, and exact parser replay. A digest
match alone is insufficient. Unsupported parser versions, altered projections,
missing children, reordered ordinals, or body/catalog disagreement fail closed
instead of returning unverifiable evidence.

## Point-in-time read boundary

The repository can resolve the latest capture for an exact provider, product,
CIK, and parser version only when `capturedAt <= evaluationAsOf`. Selection is
ordered by `capturedAt DESC, captureId DESC`; a future capture and a capture
produced by another parser are invisible. The result retains its exact root,
ordered children, receipt, and decoded bytes.

This is a repository query, not a public product endpoint. `capturedAt` remains
the root knowledge boundary. Filing dates, report dates, and advertised
historical ranges cannot backdate catalog availability, and a later response
cannot revise the result of an earlier point-in-time query.

## Retention, access, and disposal policy

Durable decoded bodies are currently retained without an automatic TTL. There
is no repository delete operation, purge job, expiry column, or body-compaction
workflow. Content-addressing removes duplicate byte storage but does not erase
an observation or shorten its retention. Any future disposal, legal hold,
backup expiry, or storage-tier policy requires a separate reviewed decision and
must preserve capture/replay semantics.

The body is private server-side evidence in the application PostgreSQL
database. It is not logged, returned by an HTTP controller, exposed to the web
application, or published as a downloadable SEC mirror. This decision adds no
field-level encryption, object store, backup policy, database role model, or
public redistribution approval; deployment operators remain responsible for
database transport, access, backup, and secret controls.

## Credentials and operations

No new SEC API key, provider account, paid plan, OAuth credential, EDGAR
filer/user token, plugin, or environment variable is required. Live capture
continues to use the existing monitored `SEC_CONTACT_EMAIL` in the server-only
User-Agent and the existing ADR-036 transport controls.

Persistence uses the application's existing PostgreSQL connection. Local
configuration defaults to `localhost:5432/wsr` and can be changed with
`POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and
`POSTGRES_PASSWORD`. Local values belong only in the root `.env`; deployed
values must be injected by the deployment secret store. Credentials must not
be supplied in chat, printed in logs, or committed to Git.

When the SEC provider is explicitly enabled and the repository is present,
Spring exposes a one-shot `PersistFilingCatalogCaptureService`. It performs one
provider capture followed by one atomic append. There is still no scheduler,
controller, command-line trigger, retry loop, or autonomous network call, so
configuration alone does not start ingestion.

## Failure and no-fallback policy

Body/digest/length disagreement, unsupported parser identity, failed exact
replay, identity conflict, database constraint failure, noncontiguous children,
count/status disagreement, or incomplete transaction fails the append or read.
No missing value is replaced with zero or an empty list, and no prior capture,
fixture, alternate parser, reserialized JSON, or partial recent-only projection
is substituted.

## Non-scope and next gate

ADR-039 adds no referenced-segment GET, segment URI publication,
segment-specific receipt/body/parser, observed historical `FilingRecord`,
recent-plus-history union, accession reconciliation, actual row-count or range
verification, overlap resolution, revision/removal interpretation, or
complete-history claim. It also adds no `submissions.zip`, filing document,
exhibit, XBRL, or Company Facts ingestion.

There is no polling cadence, global multi-replica rate coordinator, automated
retry, production collector, public read API, OpenAPI contract, authentication,
Korean UI, or raw-body publication. Retention deletion, encryption, object
storage, and archival tiering remain separate operational decisions.

The next SEC gate is controlled retrieval of referenced historical segments:
resolve only captured CIK-bound filenames, apply the existing transport limits,
retain and replay each segment's exact decoded body under its own versioned
receipt/parser contract, and compare observed rows and ranges with the root's
advertised metadata without inventing completeness. Accession reconciliation
and any complete-history status require explicit evidence rules. Scheduler and
global coordination, read API, and attributed public UI remain later
independent gates.

## Consequences

- A historical point-in-time catalog can be reproduced from retained source
  bytes instead of trusting a digest or mutable current provider response.
- Identical observations are idempotent, while later observations remain
  append-only captures that do not overwrite earlier knowledge or claim an
  SEC-authored revision relationship.
- Root receipt, ordered projections, and exact body commit or roll back as one
  evidence unit.
- Exact bodies consume durable private database storage with no automatic TTL;
  capacity, backup, access, and future disposal need explicit operations.
- This gate improves reproducibility without adding credentials, autonomous
  collection, historical segment facts, or public product behavior.

## Verification contract

Automated coverage exercises defensive byte ownership, receipt-state
promotion, stable capture identity, exact replay rejection, idempotent and
conflicting appends, later observations sharing one body, atomic rollback,
provider-order reconstruction, parser-specific point-in-time selection,
PostgreSQL concurrent append convergence, Flyway V6 constraints, and restricted
body deletion. Test execution totals and phase-wide verification results are
recorded separately after the verification commands complete.
