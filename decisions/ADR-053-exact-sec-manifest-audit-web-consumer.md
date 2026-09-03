# ADR-053: Exact SEC manifest audit web consumer

- Status: Accepted
- Date: 2026-08-31
- Depends on: ADR-041, ADR-046, ADR-052

## Context

ADR-041 persists an immutable, root-relative SEC filing-history manifest.
ADR-052 exposes four anonymous, exact-ID, point-in-time audit resources over
that already-persisted evidence. The public Caddy boundary still routes browser
traffic only to Next, and the browser must not learn or call the private Spring
origin directly.

The API has no manifest collection, CIK/ticker search, or latest/current
selector. It also exposes no issuer name or data mode. A web page therefore
cannot choose a useful manifest, default the cutoff to the current time, infer a
company identity from CIK, or relabel an API response as live data without
inventing facts. A deterministic local fixture is still useful for layout and
accessibility checks, but it must be visibly synthetic and must never become an
automatic fallback for an API failure.

## Decision

### One same-origin exact-evidence route

Add one Korean-default, bilingual Next route:

```text
/research/sec/filing-history
```

With no query parameters, the route is only a locator form. It does not read a
provider or select a manifest. A complete evidence request carries the exact
identity in the same-origin URL:

```text
/research/sec/filing-history?manifestId={lowercase-sha256}
  &evaluationAsOf={utc-z-instant}[&view={view}][&page={page}&size={size}]
```

The line break above is illustrative only. The actual URL is one query string.
The closed UI query grammar permits only `manifestId`, `evaluationAsOf`,
`view`, `page`, and `size`:

- `manifestId` is exactly 64 lowercase SHA-256 hexadecimal characters;
- `evaluationAsOf` is a real-calendar UTC `Z` instant with at most six
  fractional-second digits and is never replaced with server time;
- `view` is `summary`, `descriptors`, `accessions`, or `occurrences` and
  defaults to `summary` only when omitted;
- `summary` forbids `page` and `size`;
- child views default to `page=0` and `size=25`, accept a canonical unsigned
  page through `2147483647`, and bound size to 1 through 100; and
- unknown, duplicate, blank, whitespace-normalized, signed, leading-zero, or
  otherwise malformed values produce a local invalid-request state before any
  provider call.

Canonical links always preserve the exact manifest identity and cutoff, reset a
new view to page zero, and never add a caller-controlled sort. Each navigation
reads only the selected ADR-052 resource. Summary, descriptor, accession, and
occurrence navigation therefore do not combine resources from different
requests or present a partial multi-resource aggregate.

### Server-only provider selection with no fallback

Introduce a server-only provider selected by the exact lowercase value of
`SEC_MANIFEST_AUDIT_PROVIDER`:

- `fixture` reads one committed synthetic DEMO artifact;
- `api` sends one private server-side GET to the matching ADR-052 resource
  beneath `API_BASE_URL`; and
- a missing selector defaults to `fixture` for isolated local development, but
  an unknown, blank, differently cased, or padded selector fails closed.

API transport accepts only an absolute HTTP(S) `API_BASE_URL` without
credentials, query, or fragment. It uses `GET`, `Accept: application/json`,
`cache: no-store`, and redirect rejection. A 404 becomes the same localized
not-found boundary used for absent and future-invisible exact evidence. Network,
status, content-type, JSON, closed-schema, identity, time, count, or ordering
failure reaches the route error boundary. API mode never reads the fixture,
returns a fabricated empty page, or substitutes another manifest.

The browser talks only to the Next origin. There is no client-visible Spring
URL, browser API fetch, new public Caddy proxy, credential, or operator-token
surface. The Next route is dynamically rendered, while the Spring request
retains the ADR-052 no-store policy.

### One adapter and Java-generated DEMO parity

API and fixture payloads pass through the same closed TypeScript adapter. It
preserves the exact audit identity, UTC microseconds, nullable values, all three
selection-coverage states, descriptor selection, accession agreement/conflict,
source occurrences, and fixed page order. It rejects unknown or missing fields,
unsafe integers, invalid dates and SEC document identities, inconsistent
counts, out-of-order point-in-time evidence, page-identity drift, and nonexact
ordinal order instead of repairing the response.

The committed DEMO JSON is not hand-authored market evidence. A Java parity
test reconstructs its root and historical-segment captures through the real
domain assembly and serializes summary and child resources through the ADR-052
response mapper. The committed JSON must equal that generated tree exactly.
The fixture provider then applies only the caller's valid cutoff and bounded
page slice. A cutoff before assembly or another manifest ID returns not found.

Every fixture surface is labeled `Synthetic DEMO` and explicitly states that
it is not observed SEC data. API mode shows only a transport description:
ADR-052 has no `dataMode`, so the page does not call the response `LIVE`,
`REALTIME`, `DELAYED`, or `DEMO`.

### Evidence-first presentation

The page keeps canonical IDs, enum values, counts, dates, and instants
untranslated while localizing explanatory labels. It shows the requested
manifest and cutoff on every resource and preserves the API's separate
`rootCapturedAt`, `evidenceAvailableAt`, and `assembledAt` meanings.

The summary renders all disclosure fields and does not infer an atomic SEC
snapshot, current history, correction/removal resolution, amendment linkage,
legal authority, issuer name, or ticker. Descriptor ranges remain
provider-advertised ranges, not observed filing extrema or gap proof.
Accession conflicts remain visible without a winning or canonical filing.
Occurrence order remains manifest source order, not SEC chronology. Nullable
values render as `NA` without substitution, and primary-document URIs are shown
as validated text rather than promoted into an unreviewed external link.

Known-empty and out-of-range pages describe only the exact response page and
make no complete-history claim. Loading shows no evidence before verification.
An invalid URL makes no provider request. Not found does not distinguish an
absent manifest from one assembled after the cutoff. Any other failure hides
the resource rather than publishing partial, empty, alternate, or synthetic
evidence. Desktop and mobile tables use keyboard-focusable bounded scroll
regions and must contain long identifiers without page overflow.

### Runtime settings and deployment boundary

The web runtime owns three non-secret settings:

```dotenv
SEC_MANIFEST_AUDIT_PROVIDER=fixture
API_BASE_URL=http://localhost:8080
SITE_ORIGIN=http://localhost:3000
```

`API_BASE_URL` is consulted only in `api` mode and remains server-only.
`SITE_ORIGIN` is the exact absolute HTTP(S) origin used for canonical social
metadata; it must contain no credentials, path, query, or fragment. Its local
default is `http://localhost:3000`. At Internet cutover it must be replaced by
the operator's actual HTTPS domain.

The home-server Compose contract pins `SEC_MANIFEST_AUDIT_PROVIDER=api`, keeps
`API_BASE_URL=http://api:8080` on the internal application network, derives
`SITE_ORIGIN` from the public domain, and leaves `SEC_PROVIDER_ENABLED=false`.
It never permits a production API failure to expose the fixture. The locator
can render without stored SEC evidence, but a successful API-backed audit needs
an already-persisted exact manifest and a cutoff at or after its assembly.

No API key, SEC account, paid plan, domain, home-server access,
`SEC_CONTACT_EMAIL`, or new secret is required to implement and test this
slice. A production domain is required only for real publication metadata. A
monitored `SEC_CONTACT_EMAIL` becomes necessary only if a later, separately
approved operation enables live SEC collection; it is not a read-UI setting.

## Verification plan

Verification for this slice covers:

- closed URL grammar, exact link generation, invalid-request no-call behavior,
  and real-calendar/microsecond boundaries;
- fixture and API provider selection, server-only transport, no-store GETs,
  no fallback, 404 behavior, malformed/error responses, and exact base-URL
  validation;
- closed adapter field sets, point-in-time/count/page invariants, explicit
  nulls, all coverage states, agreement/conflict, source order, and safe SEC
  document identities;
- exact Java-domain/ADR-052-mapper parity for the committed DEMO artifact;
- Korean and English locator, summary, tables, loading, invalid, not-found,
  error, empty, and pagination states;
- keyboard focus, long-identifier containment, and 1440, 1280, and 390 pixel
  responsive behavior with no browser call to the private API origin;
- web lint, unit tests, production build, focused API parity, deployment-source
  verification, and repository contract checks.

The implementation log records only results actually observed when these checks
run. This ADR does not assign successful counts in advance.

## Explicitly excluded

ADR-053 does not:

- add or change a Spring endpoint, OpenAPI operation, Flyway migration, or
  stored manifest;
- fetch SEC, enable a provider, schedule collection, or expose the operator API;
- add a manifest list, CIK/ticker/company selector, latest/current choice,
  freshness calculation, amendment/correction resolution, or completeness
  claim;
- expose Spring directly through Caddy or perform browser-to-Spring requests;
- add authentication, rate limiting, CDN/public caching, analytics, ads,
  subscriptions, donations, or commercial data;
- claim that the future Ubuntu server, DNS, router, TLS, backup, or production
  database is ready; or
- make the synthetic DEMO artifact observed SEC evidence.

## Consequences and next work

- A known exact manifest can be inspected through one stable same-origin Korean
  UI without expanding ADR-052's selector or evidence claims.
- Local visual and contract testing is deterministic and keyless, while API
  mode remains an honest no-fallback view of persisted evidence.
- A useful company-filings product still needs a separate point-in-time
  identity/selection decision, freshness and attribution policy, bounded
  concurrency/rate controls, and public-operational review.
- Before API-backed production success, the operator must identify a genuinely
  stored manifest and an allowed exact cutoff. Before Internet publication,
  the operator must provide the actual domain and complete the existing server
  fact, DNS, TLS, router, capacity, and backup gates. Before live collection,
  the operator must place a monitored SEC contact email in the approved
  untracked server secret environment rather than chat or Git.
