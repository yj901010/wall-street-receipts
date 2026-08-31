# ADR-052: Exact SEC filing-history manifest audit API

- Status: Accepted
- Date: 2026-08-27
- Depends on: ADR-039, ADR-040, ADR-041, ADR-042, ADR-043

## Context

ADR-039 and ADR-040 persist immutable SEC root and historical-segment
captures. ADR-041 assembles those exact captures into an immutable,
root-relative filing-history manifest and preserves every selected descriptor,
source occurrence, exact agreement, and canonical conflict. ADR-042 and
ADR-043 provide a default-disabled local operator command boundary, but the
public read API still exposes none of the stored manifest evidence.

A general company-filings or "latest filings" API would require separate
selection, freshness, completeness, correction, amendment, and publication
decisions. Selecting by CIK, choosing a latest root, merging conflicting
occurrences, or treating an unselected descriptor as an empty historical
period would invent facts that the stored manifest does not prove.

The future Ubuntu server is not available. That does not block a deterministic
local read contract over already-persisted evidence. This decision adds that
small contract without contacting SEC, adding a provider, changing storage,
or claiming that the route is already reachable through the production Caddy
topology.

## Decision

### Exact point-in-time identity only

Add four anonymous, read-only Spring endpoints:

```text
GET /v1/sec/filing-history/manifests/{manifestId}
GET /v1/sec/filing-history/manifests/{manifestId}/descriptors
GET /v1/sec/filing-history/manifests/{manifestId}/accessions
GET /v1/sec/filing-history/manifests/{manifestId}/occurrences
```

Every evidence GET/HEAD request requires an explicit `evaluationAsOf` UTC `Z`
instant with at most microsecond precision. `manifestId` is exactly 64
lowercase SHA-256 hex characters. The application calls only
`findByManifestIdAtOrBefore(manifestId, evaluationAsOf)`. It never falls back
to an unrestricted lookup, another manifest, a root capture, a CIK search, or
a provider request.

A valid identifier that is absent and a manifest assembled after the supplied
cutoff produce the same sanitized 404. The API therefore does not disclose
future evidence to an earlier point-in-time query. Malformed identifiers,
timestamps, pages, duplicate parameters, missing parameters, and unknown
parameters produce a closed 400 response.

There is no manifest collection route, CIK route, latest/current selector, or
write method in this decision.

### Bounded deterministic child pages

The summary route accepts only `evaluationAsOf`. Each child route additionally
accepts one `page` and one `size`; defaults are zero and 25, and size is bounded
to 1 through 100. Pages use fixed order only:

- descriptors by `descriptorOrdinal ASC`;
- accession groups by `groupOrdinal ASC`; and
- source occurrences by `occurrenceOrdinal ASC`.

The caller cannot select another sort. A page beyond the end returns an empty
`items` array with truthful total metadata. Every page identifies the exact
manifest, evaluation cutoff, audit schema/policy, fixed order field, and
direction.

Accuracy takes precedence over query cost in this first read slice. The
existing repository reconstructs the complete manifest and verifies its root,
selected captures, counts, hashes, descriptors, groups, and occurrences before
the response is sliced. The 100-item limit bounds the HTTP response, not the
whole-manifest replay cost. A later cache, concurrency, or direct-page read
model must preserve equivalent integrity and receives its own review.

### Evidence-first response

Every successful evidence response uses audit schema `1.0.0` and policy
`SEC_EXACT_MANIFEST_AUDIT_V1`.

The summary exposes only immutable manifest evidence:

- manifest, selection, root-capture, provider, product, reconciliation-policy,
  CIK, and point-in-time identities;
- `rootCapturedAt`, `evidenceAvailableAt`, `assembledAt`, and the caller's
  `evaluationAsOf`;
- exact descriptor selection coverage and advertised, selected, and omitted
  descriptor counts;
- exact source-occurrence and distinct-accession counts; and
- single-source, exact-agreement, and canonical-conflict group counts.

It also emits a closed disclosure statement:

```text
coverageScope = ROOT_RELATIVE_SELECTED_REFERENCES_ONLY
atomicSecSnapshotClaim = NOT_MADE
currentHistoryStatus = NOT_RESOLVED
correctionRemovalStatus = NOT_RESOLVED
amendmentLinkageStatus = NOT_RESOLVED
legalAuthorityStatus = NOT_CLAIMED
```

Descriptor pages preserve the provider-advertised file member, advertised
count/date range, exact selection state, and nullable selected capture ID.
Advertised ranges are not relabeled as observed filing extrema or gap proof.

Accession pages preserve the stored `SINGLE_SOURCE_OCCURRENCE`,
`MULTIPLE_OCCURRENCES_EXACT_AGREEMENT`, or
`MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT` classification. No winner or
canonical filing is selected.

Occurrence pages preserve every occurrence ordinal, its accession group,
source kind and exact source capture, optional descriptor ordinal, source row
ordinal, projection digest, provider-event/accession identity, form, filing
and report dates, acceptance instant, and nullable primary-document URI. The
ordering is manifest source order, not a claim of SEC chronology.

The API omits decoded bodies, raw submissions JSON, response/request headers,
contact email, User-Agent, credentials, environment values, operator request
or attempt state, and database details. It does not invent `dataMode`, issuer
name, ticker, document title, a missing document URI, correction/removal
relationships, amendment linkage, currentness, staleness, continuity, or a
complete-history result.

### HTTP, security, and failure boundary

The paths are outside `/internal/v1/sec/**`, so the existing public Spring
security chain permits anonymous reads even when the operator API is enabled.
The operator bearer token is neither accepted nor required by this contract.
GET/HEAD success and handled 400/404/405/500 problem responses carry
`X-Request-Id` and `Cache-Control: no-store` until a publication/freshness
cache policy is separately approved.

Only GET/HEAD returns evidence. Framework-generated OPTIONS may advertise the
read methods, while unsupported mutation methods use the existing read-only 405
problem contract. Repository reconstruction or integrity failure returns a
sanitized 500; it must never be converted to a 400, empty page, partial
manifest, or fallback result.

Production Caddy currently routes public traffic to Next rather than directly
to Spring. ADR-052 is a backend public contract for a later same-origin web
consumer; it does not claim current Internet reachability and does not change
Caddy, Compose, ports, DNS, TLS, or the home server.

## Verification

The implementation must prove:

- exact ID and point-in-time visibility before, at, and after assembly;
- identical 404 shape for absent and future-invisible manifests;
- strict UTC/microsecond and closed query-parameter validation;
- fixed ordinal ordering, default/max/out-of-range pagination, explicit nulls,
  partial selection, agreement, and conflict preservation;
- closed response field sets without raw-body, header, contact, operator, or
  invented data-mode fields;
- request-ID propagation, no-store responses, anonymous access, and absence of
  mutation routes;
- full repository replay before paging and sanitized failure on corrupt
  evidence; and
- provider-disabled local tests with no SEC or other external network call.

The OpenAPI document owns the four operations and closed response schemas. CI
continues to project and lock the original five analyst-call paths for their
historical P1/P2/P3 contracts, while a new ADR-052 guard owns the exact current
nine-path surface and rejects list/latest/write expansion.

## Explicitly excluded

ADR-052 does not:

- fetch SEC or require `SEC_CONTACT_EMAIL`;
- create, update, delete, reconcile, or schedule a capture or manifest;
- expose the operator command API or make it public;
- add a manifest/CIK search, latest selection, current-company history, or
  freshness policy;
- infer amendment, correction, withdrawal, deletion, or legal relationships;
- add a Korean web page, Caddy route, public cache, CDN, rate limit, or search
  index;
- change Flyway, PostgreSQL tables, canonical fixtures, or provider DTOs; or
- claim deployment, publication, complete SEC history, or investment advice.

## Operator and secret requirements

No API key, SEC account, paid plan, domain, server login, home-server fact,
`SEC_CONTACT_EMAIL`, operator token, or new secret is required for this local
implementation and test. Tests use deterministic persisted fixtures with the
SEC provider disabled.

A future live collection still requires the monitored SEC contact email in an
untracked secret environment. A future public company-filings view requires a
separate selector, freshness, source-attribution presentation, Korean UI, and
operational cost-control decision.

## Consequences

- Stored ADR-041 evidence gains a reproducible audit read without a latest or
  completeness claim.
- Conflicts and partial descriptor selection remain visible instead of being
  collapsed into a convenient filing list.
- HTTP pages are bounded, but complete replay cost remains explicit future
  operational work.
- The next product slice can build a Korean evidence UI against a stable
  backend contract without exposing the private Spring origin to the browser.
