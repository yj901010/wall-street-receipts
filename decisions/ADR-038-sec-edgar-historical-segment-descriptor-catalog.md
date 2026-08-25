# ADR-038 — SEC EDGAR Historical-Segment Descriptor Catalog

- Status: Accepted
- Date: 2026-08-25

ADR-038 establishes the in-memory catalog of historical segment descriptors advertised by one SEC EDGAR Submissions root response.

## Context

ADR-035 selected the keyless SEC EDGAR Submissions API and kept
`filings.recent` explicitly recent-only. ADR-036 bounded the live one-JVM
transport, and ADR-037 bound each accepted decoded root response to the exact
bytes supplied to its versioned parser. The root response can also publish a
`filings.files` array that names additional JSON files and advertises the date
range and filing count associated with each file.

SEC documents only that entities with additional filings can receive an array
of additional JSON files and the date range contained by each file. SEC does
not document the local filename allowlist, array ordering, absence of overlap,
or truth of an advertised count as service guarantees. Those distinctions are
preserved by this decision.

Official product and access references:

- [SEC EDGAR Application Programming Interfaces](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)
- [Accessing EDGAR Data](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)

This is a descriptor-catalog sub-gate. It does not fetch a referenced file,
map historical rows, or establish complete filing history.

## Exact wire contract

The strict submissions reader now uses parser identity
`SEC_SUBMISSIONS_CATALOG_V2`. `filings.files` must be present as a JSON array;
an empty array is valid, while a missing or null array fails the complete root
response. Every array member must be a non-null object with these selected
fields:

- `name`: a nonblank, already-trimmed JSON string;
- `filingCount`: a coercion-free positive JSON integer;
- `filingFrom`: an exact, valid `YYYY-MM-DD` calendar-date string; and
- `filingTo`: an exact, valid `YYYY-MM-DD` calendar-date string not before
  `filingFrom`.

Unknown vendor fields remain ignored behind the vendor DTO boundary and cannot
silently expand the canonical contract. Duplicate keys, scalar coercion,
floating-point-to-integer coercion, malformed UTF-8, and trailing JSON tokens
remain prohibited by ADR-037's strict decoded-byte reader.

The adapter applies this closed local filename allowlist:

```text
^CIK([0-9]{10})-submissions-([0-9]{3})\.json$
```

The embedded ten-digit CIK must equal the root response and catalog CIK, and
the three-digit suffix cannot be `000`. This shape admits no slash, backslash,
percent encoding, dot segment, host, query, or fragment. It is an internal
fail-closed resolution policy, not an SEC promise that the provider will never
change its filename convention. Filenames must also be unique within one root
capture. A malformed or duplicate descriptor invalidates the whole response;
the adapter does not drop it and continue with apparently valid recent rows.

The provider-published `filings.files` order is retained without sorting.
Neither array position nor numeric filename suffix is interpreted as
chronological precedence, revision order, or completeness order.

## Canonical descriptor and catalog semantics

The provider-neutral `HistoricalFilingSegmentDescriptor` preserves exactly:

- `fileName`;
- positive `advertisedFilingCount`;
- inclusive `advertisedFilingFrom`; and
- inclusive `advertisedFilingTo`.

The `advertised` prefix is material. These values are claims present in the
captured root response. They are not observed segment rows, a verified response
cardinality, actual minimum or maximum filing dates, or proof that the named
resource exists or remains unchanged.

`FilingCatalog` now keeps `recentFilings` and `historicalSegments` as distinct,
immutable, provider-ordered lists. A descriptor is not a `FilingRecord`, does
not have an accession identity or `acceptedAt`, and does not contribute an
invented filing event or total filing count.

The only historical-segment status values are:

```text
RECENT_ONLY_NO_SEGMENTS_ADVERTISED
RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED
```

An empty descriptor list means only that this root capture advertised no
additional segment. It is not evidence that the recent arrays are the entity's
complete history. A nonempty list means only that references were advertised
and were not fetched by this slice. No complete-history state exists.

Duplicate filenames fail closed. Inclusive advertised range overlap between
descriptors, and a recent filing date falling inside an advertised historical
range, are retained as explicit diagnostic flags. A flag does not reject the
root catalog and does not prove duplicate filings. It never triggers sorting,
range clipping, merging, deduplication, count summation, or a completeness
claim.

## Receipt and point-in-time boundary

The V2 parser reads `filings.recent` and `filings.files` from the same ADR-037
owned decoded byte sequence. The root `SourceResponseReceipt` therefore binds
the exact bytes, source URI, root `capturedAt`, HTTP/media metadata, parser
version, decoded length, and digest from which both catalog lists were mapped.

That receipt does not bind any referenced segment body because no segment is
requested. Its root `ETag`, `Last-Modified`, and SHA-256 say nothing about the
existence, bytes, response metadata, or later mutation of a named segment.
Likewise, a validated filename is only a captured reference, not authenticated
segment content or an SEC digital signature.

Each descriptor becomes known only at the root catalog's `capturedAt`.
`advertisedFilingFrom` and `advertisedFilingTo` are neither event time nor
`availableAt`, processing time, capture time, publication time, or a license to
backdate knowledge. A later root response can advertise changed descriptors;
this in-memory slice neither overwrites nor durably records either capture.

## Failure and no-fallback policy

A missing `filings.files`, null member, unsafe or cross-CIK filename, duplicate
filename, non-positive or non-integer count, malformed date, or reversed date
range invalidates the complete submissions response with a sanitized provider
failure. No value is replaced by zero, empty text, a current date, an inferred
range, or an empty descriptor list. There is no recent-only salvage, stale
catalog, fixture, bulk archive, alternate provider, or scraping fallback.

Advertised overlaps are not malformed wire data by themselves. They remain
unresolved evidence for a later segment-content policy rather than being
silently normalized at the descriptor boundary.

## Credentials and operations

This catalog uses the same public `data.sec.gov` root request selected by
ADR-035 and creates no additional HTTP call. It requires no new API key,
provider account, paid plan, OAuth credential, EDGAR filer/user token,
registration, plugin, or environment variable. The existing monitored
`SEC_CONTACT_EMAIL` remains required only for explicitly enabled or manually
smoke-tested live access and is not catalog or receipt data.

The authenticated filing APIs at `api.edgarfiling.sec.gov` are a different
product and are not selected. Future segment requests must remain server-only,
use the exact approved public origin, and pass the existing response-size,
request-spacing, and cooldown controls. This decision does not authorize a
fan-out request loop, scheduler, multi-replica collector, or production
ingestion.

## Non-scope and next sequence

This decision adds no referenced-segment GET, segment URI publication,
segment-specific body, digest, receipt, parser, or `FilingRecord`; no actual
row-count or date-range verification; no recent-plus-history union, accession
duplicate comparison, overlap resolution, revision/removal reconciliation, or
complete-history claim; and no nightly `submissions.zip` integration.

It also adds no durable raw-body retention, replay reader, append-only
persistence, Flyway migration, database table or row, repository, scheduler,
polling loop, controller, OpenAPI contract, public API, or web publication.
Filing documents, exhibits, XBRL, Company Facts, and filer-authored content
remain outside the selected metadata product.

The next gate is append-only persistence for the root receipt, recent catalog,
and advertised descriptors, including durable raw-body/replay policy,
idempotency, revisions, and point-in-time reconstruction. Referenced-segment
retrieval and actual range/cardinality/completeness proof follow as a separate
historical-content gate. Scheduler/global coordination, read API, and
attributed Korean public UI remain later independent gates.

ADR-038 therefore does not complete ADR-035's broader historical-segment gate;
it makes the advertised manifest explicit and safe enough to be persisted
without mislabeling it as fetched or complete history.

## Consequences

- The current root capture can distinguish no advertised segment from
  advertised-but-unfetched segments without inventing a complete state.
- Unsafe or contradictory descriptor fields cannot be ignored merely to keep
  recent rows available.
- Advertised range overlap remains observable while merge and deduplication
  stay deferred until actual segment evidence exists.
- The new catalog improves source traceability without adding credentials,
  external calls, durable evidence, historical rows, or public behavior.

## Verification

- Focused descriptor/catalog/mapper regression: **PASS** — 70 tests with zero
  failures, errors, or skips.
- API main-source compilation: **PASS**.
- Full API Maven verification: **PASS** — 2,180 tests with zero failures or
  errors; Maven completed with `BUILD SUCCESS`.
- Manual SEC live smoke: **PASS** — one Apple root request, zero referenced
  segment requests, nonempty V2 historical descriptors with the explicit
  advertised-but-not-fetched status, one test, and `BUILD SUCCESS`.
- Web regression: **PASS** — lint, 42 Vitest files / 569 tests, and the Next.js
  production build completed successfully.
- Repository CI guard: **PASS** — all 43 embedded Python bodies compile, all
  36 environment-independent bodies execute, and the ADR-034 through ADR-038
  dedicated replay guards pass.
