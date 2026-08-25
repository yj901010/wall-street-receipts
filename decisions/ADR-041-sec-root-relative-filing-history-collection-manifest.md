# ADR-041 — SEC Root-Relative Filing-History Collection Manifest

- Status: Accepted
- Date: 2026-08-25

ADR-041 assembles one immutable, root-relative filing-history collection from
already durable SEC evidence. It preserves every selected source occurrence
and compares repeated accession identities without choosing a winner or
claiming complete SEC history.

## Context

ADR-039 durably retains one exact SEC submissions root response, including its
provider-ordered recent filings and advertised `filings.files` descriptors.
ADR-040 can durably retain one explicitly selected additional JSON file under
the exact root capture and descriptor ordinal that advertised it. Neither
decision defines a collection across those resources or compares an accession
that appears more than once.

The SEC's
[EDGAR API documentation](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)
documents the per-entity root at
`https://data.sec.gov/submissions/CIK##########.json`. It says the root contains
at least one year or 1,000 recent filings, whichever is more, and that, when
additional filings exist, `files` contains additional JSON files and the date
range for the filings in each file. The same page says the JSON structures are
updated throughout the day as submissions are disseminated. It does not define
a common root/file generation identifier, atomic snapshot token, array-order
meaning, range disjointness or continuity rule, total union count, union hash,
or cross-resource duplicate-resolution algorithm.

The product-level statement that an entity's current filing history is
available does not provide a mechanically testable guarantee that one root
captured at one instant and separately requested files captured later form one
atomic, complete `asOf` state. The SEC also documents post-acceptance
corrections and removals in
[Accessing EDGAR Data](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)
and explains that some paper filings are not accessible through EDGAR.
[Correct or Delete a Filing](https://www.sec.gov/submit-filings/filer-support-resources/how-do-i-guides/correct-or-delete-filing)
further explains that a filer correction normally leaves both the original and
corrective filing public, while limited SEC staff corrections to public EDGAR
cannot retroactively change copies already disseminated or extracted by third
parties.

The SEC's
[EDGAR Glossary](https://www.sec.gov/submit-filings/filer-support-resources/edgar-glossary)
describes an accession number as a unique reference number generated for each
submission. It also notes that issuance does not itself mean a submission was
accepted. The glossary is an EDGAR Business Office staff explanation and
expressly is not a Commission rule or statement. The SEC's
[login-CIK guidance](https://www.sec.gov/submit-filings/filer-support-resources/how-do-i-guides/understand-select-set-default-login-cik)
states that the first ten accession digits identify the login CIK, which may
belong to the filer or a third-party filing agent. The accession prefix is
therefore not an entity-CIK validator.

The SEC public submissions documentation calls the `files` resources
"additional JSON files." It does not call them historical segments. The EDGAR
Glossary uses "Segment" for a different filing-construction concept: a portion
of a filing submitted separately for later combination. `Historical Segment`,
`segment capture`, and related names in ADR-038 through ADR-041 are explicit
WSR local terminology for the additional submissions JSON resources, not SEC
terminology.

ADR-041 therefore defines a narrow WSR collection and reconciliation contract
over exact prior captures. A local contract may preserve and classify observed
agreement or disagreement, but it cannot turn undocumented behavior into an
SEC guarantee.

## Decision

ADR-041 implements one deterministic operation:

```text
exact durable rootCaptureId
    + explicit (descriptorOrdinal, exact historicalSegmentCaptureId) selections
    -> zero provider requests
    -> one immutable root-relative collection manifest
```

The operation reads PostgreSQL evidence created by ADR-039 and ADR-040. It
performs no SEC GET, root refresh, descriptor request, conditional request,
retry, alternate-provider read, nightly ZIP read, or browser request.

The manifest contract is explicitly versioned:

```text
schema: 1.0.0
provider: sec-edgar
product: edgar-submissions-root-relative-collection-manifest
policy: SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1
```

### Exact evidence selection

The caller must supply:

- one exact durable ADR-039 root `captureId`; and
- an explicit finite list of descriptor ordinal and exact durable ADR-040
  segment `captureId` pairs.

The application never selects a latest root by CIK, a latest segment by
descriptor, a segment by filename, or a capture by the current clock. It does
not infer a missing segment ID from root metadata. Every selected segment must:

- have provider `sec-edgar`;
- have product `edgar-submissions-historical-segment-api`;
- use parser `SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1`;
- retain its exact decoded body durably;
- bind by persisted identity to the selected root capture;
- bind to exactly one descriptor ordinal and its complete descriptor tuple;
  and
- appear only once in the explicit selection.

The selected root must retain the ADR-039 provider, product, parser, exact body,
receipt, recent rows, and provider-order descriptor rows. A segment belonging
to a later same-CIK root, an equal-looking caller-reconstructed descriptor, or
another parser version cannot be substituted.

The explicit segment selection may cover some, all, or none of the root's
advertised descriptors. Omitted descriptors are recorded as omitted relative
to this manifest; they are not fetched, inferred, or represented by an empty
segment. If the operation contract requires a particular selected ordinal, a
missing or invalid capture fails the operation rather than creating a partial
placeholder.

### WSR-local collection order

The collection order is deterministic and local:

1. root recent rows, in their exact persisted provider row order; then
2. selected additional-file members, in ascending captured root descriptor
   ordinal, with each member's rows in exact persisted provider row order.

The manifest preserves each source kind, capture ID, descriptor ordinal when
applicable, and source row ordinal. This order is a WSR reproducibility rule.
It is not evidence that SEC promises chronological or reverse-chronological
ordering for `files`, root recent rows, or additional-file rows. Advertised
date ranges cannot reorder members, break ties, fill gaps, or choose one
source over another.

### Every source occurrence is evidence

Every filing row in the selected root recent evidence and every explicitly
selected segment is retained as a distinct collection occurrence. Each
occurrence preserves at least:

- its exact root-relative collection identity;
- source kind;
- source capture identity;
- descriptor ordinal when applicable;
- source row ordinal;
- exact canonical accession identity;
- the selected canonical filing-evidence projection; and
- a deterministic fingerprint of that projection under the reconciliation
  methodology version.

The occurrence kind is exactly `ROOT_RECENT` or `HISTORICAL_SEGMENT`.

A repeated accession does not delete, overwrite, hide, or coalesce any
occurrence. Occurrence cardinality and unique-accession cardinality are
separate facts. Root/segment range overlap and matching filing dates are
diagnostics only and do not prove duplicate identity.

### Accession reconciliation without a winner

The exact canonical accession number is the only ADR-041 grouping identity.
The first ten accession digits are not compared with the catalog CIK because
they may identify a submitting agent. Two different accessions are never
merged because they share a form, filing date, report date, document path, or
other metadata. A corrective or amended filing with its own accession remains
a separate submission identity.

The versioned reconciliation projection compares the same selected canonical
fields for every occurrence of one accession. It never invents an absent
historical document URI, substitutes a root value, normalizes a date to a
descriptor endpoint, or fetches filing text to break a tie. Each accession
group has one closed outcome:

```text
SINGLE_SOURCE_OCCURRENCE
MULTIPLE_OCCURRENCES_EXACT_AGREEMENT
MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT
```

- `SINGLE_SOURCE_OCCURRENCE` means the selected evidence contains the accession
  once.
- `MULTIPLE_OCCURRENCES_EXACT_AGREEMENT` means it occurs more than once and
  every compared canonical field agrees exactly under this methodology version.
- `MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT` means it occurs more than once and
  at least one compared canonical field differs.

`MULTIPLE_OCCURRENCES_EXACT_AGREEMENT` is local equality of selected captured
projections. It is not an SEC truth warranty, content signature, immutability
guarantee, or proof that the filing has not later been corrected or removed.
`MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT` preserves every candidate and its
provenance. It does not identify a correct, newest, authoritative, preferred,
root-winning, segment-winning, or majority value. There is no silent winner,
last-write-wins projection, field-by-field merge, or public canonical filing
row in this decision.

### Root-relative selected-reference coverage

Coverage is defined only over the evidence references explicitly selected for
this manifest. The manifest records the root's advertised descriptor count,
selected descriptor ordinals and capture IDs, omitted descriptor count,
occurrence count, unique-accession count, and reconciliation outcome counts.
It also preserves every selected segment's ADR-040 advertised comparison state
without upgrading or correcting that state.

Every captured root descriptor remains present in manifest order with exactly
one state:

```text
NOT_SELECTED
SELECTED_EXACT_CAPTURE
```

The root-relative descriptor coverage is exactly:

```text
NO_ADVERTISED_DESCRIPTORS
PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED
ALL_ADVERTISED_DESCRIPTORS_SELECTED
```

- `NO_ADVERTISED_DESCRIPTORS` means the captured root advertised zero
  descriptors and the explicit segment selection is empty.
- `PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED` means the captured root advertised
  at least one descriptor and fewer than all were selected; selecting zero is
  an explicit partial selection.
- `ALL_ADVERTISED_DESCRIPTORS_SELECTED` means exactly one valid capture was
  explicitly selected for every descriptor advertised by that captured root.

A successful manifest means:

```text
the exact root recent evidence and every explicitly selected segment capture
were included once under the declared WSR ordering and reconciliation version
```

It does not mean:

- every root-advertised additional file was selected;
- every current additional file was advertised by that earlier root;
- descriptor ranges are continuous, disjoint, ordered, or exact extrema;
- root recent and additional-file rows do not overlap;
- no later correction, removal, or changed source observation exists;
- one atomic SEC snapshot was reconstructed;
- all electronic or paper filings for the entity are present; or
- current, all-time, complete, or legally authoritative SEC history exists.

Even when the explicit selection happens to include one capture for every
descriptor advertised by the root, the strongest status remains selected
root-relative reference coverage. No status, Boolean, API field, or display
label may call it `COMPLETE_HISTORY`, `FULL_HISTORY`, or an equivalent claim.
A date-range gap does not prove missing data because an entity may have made no
filing in that interval. A range overlap does not authorize deduplication.

### Non-atomic availability and point-in-time meaning

Each source capture retains its own `capturedAt`. The manifest derives:

```text
evidenceAvailableAt = max(root capturedAt, every selected segment capturedAt)
```

For a root-only manifest, `evidenceAvailableAt` equals the root `capturedAt`.
`assembledAt` is the injected-clock processing time at which WSR completed the
deterministic collection and reconciliation. The invariant is:

```text
root capturedAt <= evidenceAvailableAt <= assembledAt
```

Every selected segment also already proves its own
`rootCapturedAt <= segmentCapturedAt` relationship. The manifest retains the
individual member times rather than rewriting them to `evidenceAvailableAt` or
`assembledAt`.

`evidenceAvailableAt` means only that all explicitly selected durable source
captures were available to WSR by that instant. `assembledAt` means the derived
manifest itself was available. Neither is an SEC-authored `asOf`, filing event
time, correction-effective time, atomic snapshot time, or completeness time.
The manifest cannot be visible in a point-in-time read before `assembledAt` and
cannot backdate knowledge to a filing date, report date, acceptance time,
advertised date, root time alone, or current source state.

### Immutable persistence and replay

The durable manifest binds the exact root capture, ordered exact selected
segment captures, reconciliation methodology version, occurrence projections,
group outcomes, selected-reference coverage facts, and `evidenceAvailableAt`.
Its content-derived identity deliberately excludes `assembledAt`. The first
successful append retains the winning `assembledAt` together with
`manifestId`, `selectionSha256`, `rootCaptureId`, `rootCapturedAt`, CIK,
descriptor states, occurrences, and accession groups. Repository reconstruction
must validate all bindings, local order, counts, fingerprints, reconciliation
outcomes, and availability invariants before returning the aggregate.

`selectionSha256` binds the exact root capture ID and every captured root
descriptor in ordinal order, including its exact advertised tuple, selection
state, and selected segment capture ID or explicit absence. `manifestId` binds
the manifest schema, provider, product, reconciliation policy, root capture ID,
and `selectionSha256` under versioned local identity rules. These lowercase
SHA-256 values are deterministic local identities, not SEC signatures or source
authenticity proofs.

A later assembly attempt with the same exact selection-derived content is an
idempotent replay: it returns the first durable manifest and its original
`assembledAt` instead of creating another observation. Reusing the identity
with another root, segment selection, order, projection, methodology, outcome,
or count fails closed. There is no repository update, delete, occurrence
salvage, conflict winner, or reserialization fallback. This is an application
append-only contract, not a claim that a privileged database administrator is
cryptographically unable to mutate PostgreSQL. Database-role restriction,
audit, backup retention, and a true WORM storage tier remain separate
operational controls.

### Zero-network operations and credentials

ADR-041 performs zero external requests and therefore consumes no SEC request
budget and does not consult `SEC_CONTACT_EMAIL`. It needs no new SEC API key,
provider account, paid plan, OAuth credential, EDGAR filer/user token,
registration, environment variable, secret, or plugin. It reuses only the
application's existing PostgreSQL connection to read the exact ADR-039/040
evidence and append the derived manifest.

Creating the prerequisite live captures remains governed by ADR-035 through
ADR-040 and SEC's official
[fair-access guidance](https://www.sec.gov/about/developer-resources). Manifest
assembly does not weaken, bypass, or coordinate those earlier request limits.

Configuration does not trigger assembly. There is no scheduler, startup
collector, descriptor fan-out, poller, automatic retry, command-line runner,
controller, OpenAPI endpoint, public read API, browser consumer, or UI in this
decision.

## Failure and no-fallback policy

A missing or non-durable root, unsupported provider/product/parser, duplicate
segment selection, unknown descriptor ordinal, segment bound to another root,
descriptor-tuple mismatch, non-durable segment body, invalid stored order,
replay disagreement, invalid accession grouping, inconsistent derived count,
clock before `evidenceAvailableAt`, manifest identity conflict, or atomic
append failure stops the operation.

There is no latest-capture substitution, same-CIK substitution, filename
lookup, stale manifest, fixture, empty-list replacement, omitted-reference
inference, root-only fallback for a requested segment, network refresh, nightly
ZIP, alternate provider, partial manifest, conflict winner, or public result.

## Official-documentation boundary

The following are WSR V1 choices, not SEC guarantees:

- `Historical Segment` terminology for submissions additional JSON files;
- exact root-relative capture selection and member ordering;
- preservation of captured descriptor ordinal as order evidence;
- the selected canonical reconciliation projection and fingerprint;
- accession grouping outcomes and exact-agreement semantics;
- selected-reference coverage counters and status meaning;
- `evidenceAvailableAt` and `assembledAt` derivation;
- idempotent manifest identity and append-only storage; and
- every conclusion drawn across separately captured root and file resources.

The SEC API documentation, Accessing EDGAR Data guidance, EDGAR Glossary,
login-CIK guidance, and filing-correction guidance linked above are the primary
references. Their silence on root/file atomicity, order, overlap, immutable
content, and formal union completeness must not be converted into a positive
guarantee. A current live observation can test today's wire behavior but cannot
establish a perpetual SEC contract.

## Non-scope and next sequence

ADR-041 adds no SEC request, fetch-all loop, scheduler, global multi-replica
coordination, polling cadence, retry owner, root revalidation, current-history
resolver, correction/removal causality, amendment linkage, legal filing status,
filing document or exhibit ingestion, XBRL/Company Facts join, nightly bulk ZIP,
OpenAPI contract, controller, public read API, raw-body download, browser
access, Korean UI, cache, search index, or notification.

A later gate may define an operator-controlled collection attempt that creates
the required exact captures and then selects them, but it must preserve the
non-atomic source window and aggregate SEC fair-access ownership across
replicas. Public API/UI work must separately define point-in-time visibility,
conflict presentation, correction/removal disclosure, freshness labels, and
source attribution before exposing any manifest-derived result.
