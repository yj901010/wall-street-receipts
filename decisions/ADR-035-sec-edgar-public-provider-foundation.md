# ADR-035 — SEC EDGAR Public Provider Foundation

- Status: Accepted
- Date: 2026-08-25

ADR-035 establishes the default-disabled SEC EDGAR public-provider foundation.

## Context

P5 needs a first non-DEMO provider whose exact product, point-in-time meaning,
source identity, operating limits, and public-use rights are known before its
data can become product evidence. SEC EDGAR is suitable for a narrow
filing-discovery foundation because the selected public product is keyless,
SEC documents its update and fair-access behavior, and SEC expressly permits
reuse of Government-created content and public EDGAR filing content.

That permission does not turn every object reachable from a filing into an
SEC-authored fact. A submissions response also establishes no analyst call,
market price, fundamental value, or interpretation of filer-authored text.
This slice therefore implements a default-disabled server adapter for a closed
set of SEC filing metadata while leaving historical traversal, persistence,
automation, API/UI publication, and all broader data products absent.

## Decision

ADR-035 selects the public SEC EDGAR Submissions API and implements the first
three foundation gates: typed disabled-by-default configuration, a vendor DTO
and pure canonical adapter, and a server-only one-request transport exercised
through mock HTTP tests. The slice includes no scheduler and makes no live SEC
request in CI or the automated test suite. One manual read-only wire-shape
check confirmed the official JSON-string CIK and nested primary-document path;
it retained no response or product data.

### Exact selected product and runtime origin

The selected product is the public **EDGAR Submissions API**, documented at
[SEC EDGAR Application Programming Interfaces](https://www.sec.gov/search-filings/edgar-application-programming-interfaces).
The only implemented resource is the current per-filer submission history:

```text
GET https://data.sec.gov/submissions/CIK##########.json
```

`##########` is a nonzero CIK normalized to exactly ten decimal digits with
leading zeroes. The official runtime origin is exactly
`https://data.sec.gov`. Ticker lookup, issuer-name search, query parameters,
redirected provider hosts, the authenticated filing-submission service at
`api.edgarfiling.sec.gov`, and browser-origin calls are not selected products.

The configured base URL may differ only for a loopback HTTP(S) test server.
Production and any manual live verification must use the exact official HTTPS
origin. A loopback override is test transport configuration, not another data
source and not a permitted runtime fallback.

### No-key server configuration and identification

`data.sec.gov` requires no authentication and no API key. The implemented
server-only configuration is exactly:

```text
SEC_PROVIDER_ENABLED=false
SEC_BASE_URL=https://data.sec.gov
SEC_CONTACT_EMAIL=
```

`SEC_PROVIDER_ENABLED` defaults to `false`, so the SEC client and adapter do
not exist in the ordinary application context. Enabling requires a nonblank,
header-safe `SEC_CONTACT_EMAIL`. It must be a real monitored operational email
before any live use. It is not an API credential, but it is server-only contact
configuration and must not appear in browser code, a `NEXT_PUBLIC_*` variable,
chat, a committed value, or an error response.

Every enabled request uses the declared header:

```text
User-Agent: WallStreetReceipts/0.1 (<configured contact email>)
```

No API key, bearer token, cookie, filer token, user token, OAuth client, or
browser credential belongs to this public product. `data.sec.gov` does not
support browser CORS for this use; the provider remains behind the Spring
server boundary.

The authoritative access and identification rules are:

- [SEC Developer Resources — Fair Access](https://www.sec.gov/about/developer-resources);
- [Accessing EDGAR Data](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data); and
- [SEC Webmaster Frequently Asked Questions](https://www.sec.gov/about/webmaster-frequently-asked-questions).

### Implemented vendor-response validation

The vendor DTO accepts the response `cik` and the following aligned
`filings.recent` arrays exactly as published by SEC:

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

`filings`, `filings.recent`, and every listed array must be present. All arrays
must have exactly equal cardinality, including arrays whose values are not
promoted into the canonical record. Null array elements fail closed except for
`reportDate`, whose SEC empty/null representation is preserved canonically as
absence. Required canonical text must be nonblank and already trimmed; dates
must be ISO calendar dates; `acceptanceDateTime` must parse as a UTC instant at
persistent microsecond precision. Unknown vendor fields are ignored behind the
vendor DTO boundary and cannot silently expand the canonical contract.

The response CIK must be the SEC wire format: a non-zero, ten-digit JSON string.
Jackson number-to-string coercion is rejected, and the exact value must agree
with the requested CIK path. Malformed or
contradictory source identity, parallel arrays, values, timestamps, or document
segments fail the whole response; rows are not partially accepted.

### Implemented canonical field set

The immutable canonical `FilingCatalog` preserves exactly:

- provider `sec-edgar`;
- product `edgar-submissions-api`;
- normalized ten-digit response CIK;
- the exact requested source URI;
- `processingTime` and `capturedAt`; and
- filing records in the SEC-published `filings.recent` order.

Each immutable canonical `FilingRecord` preserves exactly:

- `providerEventId`, equal to the exact accession number;
- the same hyphenated `accessionNumber`;
- `form`;
- `filingDate`;
- nullable `reportDate`;
- SEC `acceptanceDateTime` as `acceptedAt`; and
- the canonical official `primaryDocumentUri`.

The provider event identity must match the SEC 10-2-6 accession format:

```text
^[0-9]{10}-[0-9]{2}-[0-9]{6}$
```

Hyphens are retained. Form/date, primary-document path, array position, film
number, ticker, issuer name, or an unhyphenated accession is not a replacement
event identity. Provider event IDs must be unique within one catalog capture;
duplicates fail closed rather than being silently deduplicated.

The canonical document URI is constructed only from the validated response CIK,
the accession with hyphens removed, and the validated relative SEC
`primaryDocument` path:

```text
https://www.sec.gov/Archives/edgar/data/{unpadded CIK}/{accession without hyphens}/{primaryDocument}
```

Each document-path segment permits only the closed
alphanumeric/dot/underscore/dash shape. Forward-slash-separated nested paths
such as `xslF345X06/form4.xml` are valid; empty or dot segments, backslashes,
percent encoding, an absolute path, host, traversal, query, fragment, and user
information are forbidden. Construction of the official URI does not fetch,
store, parse, or approve the filing document itself.

`act`, `fileNumber`, `filmNumber`, `items`, `size`, `isXBRL`,
`isInlineXBRL`, and `primaryDocDescription` participate in parallel-array and
null validation but are not retained in `FilingRecord`. Entity name/type,
ticker, exchange, address, former names, SIC, filing contents, and every other
unselected field are absent. No missing or unselected value becomes zero,
empty text, current metadata, or an inferred fact.

### Point-in-time and capture rules

For each filing, `acceptedAt` is the source event time: the exact SEC
`acceptanceDateTime` parsed as an `Instant`. It is never substituted with
`filingDate`, `reportDate`, response time, or the current clock.

After the complete response is received, the provider reads one instant from an
injected `Clock`. The current foundation supplies that same `receivedAt` as both
catalog `processingTime` and `capturedAt`. Thus the in-memory capture satisfies:

```text
acceptedAt <= processingTime == capturedAt
```

An accepted filing from the future, an impossible order, or excess timestamp
precision fails closed; time is not clamped, rounded, or reordered. Catalog
order remains the provider's aligned recent-array order. The immutable catalog
is a canonical in-memory capture, not a claim that response bytes or rows have
been durably persisted.

A future point-in-time store may expose a capture only when `capturedAt <=
evaluationAsOf`. A later amendment, changed provider response, or response
absence must not rewrite an earlier capture. Exact revision, removal, and
historical replay semantics remain part of the persistence gate because this
slice writes no database state.

### Recent versus historical boundary

The implemented port and provider method load **recent filings only**. SEC
documents that `filings.recent` contains at least one year or 1,000 of the most
recent filings, whichever is more. It is not a complete-history claim.

For entities with older filings, the current response may publish a
`filings.files` array naming additional per-filer JSON segments and advertised
date/count ranges. This slice does not model that array, request those files,
union segments, or use the nightly archive at:

```text
https://www.sec.gov/Archives/edgar/daily-index/bulkdata/submissions.zip
```

No caller may label the implemented catalog as full history. Historical
descriptor validation, safe filename resolution, segment completeness,
overlap/duplicate handling, response ordering, and current-plus-history union
remain an explicit future gate. Missing history cannot fall back to recent data
while claiming completeness.

### Transport, failure, and test boundary

The implemented transport issues one typed server-side GET through Spring
`RestClient`, reads a submissions DTO, takes time from the injected `Clock`, and
invokes the pure vendor-to-canonical mapper. It has no scheduler, poller,
historical fan-out, automatic retry loop, alternate host, browser path, or live
smoke test. It disables redirects, applies a five-second connection timeout and
ten-second read timeout, declares JSON plus gzip/deflate support, and decodes
only the advertised response encodings.

Non-2xx HTTP responses, unreadable/empty responses, and invalid mapped responses
become one of these sanitized provider error shapes:

```text
SEC submissions request failed with HTTP <status>
SEC submissions response could not be read
SEC submissions response was invalid
```

The response body, filing contents, contact email, complete `User-Agent`,
request/response headers, stack trace, local network details, and configuration
values are not copied into these errors. There is no fixture, stale-row,
alternate-provider, bulk-ZIP, scraping, zero, empty-catalog, current-value, or
inferred-value fallback.

CI and ordinary verification make no external network request. Configuration,
transport, mapping, timestamp, URI, identity, alignment, and sanitized failure
tests use deterministic objects, an injected fixed `Clock`, and mock HTTP
responses. A mock response proves adapter behavior only; it is not SEC evidence
and is never a canonical production fixture.

### Fair-access operational ceiling

The SEC's documented ceiling is **10 requests per second in aggregate,
regardless of the number of machines**. The current one-request, unscheduled,
default-disabled foundation creates no claim that an aggregate limiter already
exists. Any manual live use must remain below the SEC ceiling; automated or
multi-instance activation is blocked until a reviewed limiter accounts for
all callers, retries, and historical requests, honors `429`/`Retry-After`, and
uses bounded backoff.

A process-local limiter alone cannot establish compliance for multiple
instances. Rate-limit failure never authorizes another host, screen scraping,
fixture substitution, or an unbounded retry loop.

### Rights, attribution, and provenance boundary

The SEC states that all Government-created content on sec.gov and public EDGAR
filing content are free to access and reuse. Its Website Dissemination policy
also states that information presented on sec.gov may be copied or further
distributed without SEC permission and asks users to consider appropriate SEC
citation. The official bases for this decision are:

- [SEC Webmaster FAQ — content access and reuse](https://www.sec.gov/about/webmaster-frequently-asked-questions);
- [SEC Privacy Information — Website Dissemination](https://www.sec.gov/about/privacy-information); and
- [SEC EDGAR API documentation](https://www.sec.gov/search-filings/edgar-application-programming-interfaces).

On that basis, ADR-035 approves future storage/cache, public display, and
redistribution of the exact SEC-generated canonical metadata selected above,
with source provenance and attribution. It also approves deterministic derived
filing counts, filing timelines, form filters, latency measurements, and
filing-presence indicators when they identify their SEC source, as-of/capture
time, and methodology. This rights approval does not claim those persistence,
API, UI, or derived surfaces have been implemented.

Future public attribution is exactly
`Source: U.S. Securities and Exchange Commission, EDGAR`, linked to an official
SEC source and accompanied by the product capture/as-of time. Public output
must not use the SEC seal, EDGAR logo, or wording that implies SEC affiliation,
SEC approval, SEC verification of derived output, or investment advice.

The in-memory `FilingCatalog` currently preserves provider, product, source URI,
CIK, processing/capture time, published order, and selected filing identity and
metadata. It does **not** preserve raw response bytes, a response digest,
`ETag`, `Last-Modified`, headers, HTTP status, parser version, or a durable
source receipt. Those are required decisions and fields at the persistence
gate; they are not inferred from the current canonical catalog.

This approval does not extend to the text or semantic interpretation of a
filer-authored filing, XBRL facts, inline XBRL, exhibits, contracts, press
releases, images, stock art, trademarks, logos, CUSIP content, licensed indices,
or other third-party material merely hosted, attached, linked, or incorporated
by reference through EDGAR. Public accessibility is not an authorship or
third-party-rights determination. Download, storage, excerpting, display,
redistribution, extraction, or AI processing of such content needs a separate
source-by-source copyright, privacy, terms, schema, and product review.

## Implemented gates

The following gates are complete in this foundation:

1. **Configuration gate:** typed server-only `enabled`, `base-url`, and
   `contact-email` properties; exact environment bindings; disabled-by-default
   bean creation; official runtime origin; loopback-only test override; and a
   declared, header-safe contact in the server `User-Agent`.
2. **DTO and adapter gate:** a closed vendor DTO, pure mapper, provider-neutral
   output port, immutable `FilingCatalog`/`FilingRecord`, exact canonical field
   set, aligned recent-array validation, accession identity, official primary
   document URI construction, and no inferred values.
3. **Mock transport gate:** a one-request Spring server adapter, injected
   `Clock`, safe provider exceptions, disabled-provider boot coverage, and
   mock-only success/failure tests with no CI network and no fallback.

These implemented DTO, model, mapper, port, and transport types are the ADR-035
foundation. They do not require another decision merely to exist. Any semantic
expansion or crossing into the remaining boundaries below still requires its
own focused review.

## Remaining exact gates

Passing the implemented foundation does not implicitly pass any of these gates:

4. **Live-operation gate:** add response-size limits, aggregate enforcement
   below the SEC 10-request/second ceiling,
   `429`/`Retry-After` and bounded-backoff policy, observability redaction, and
   a manual opt-in smoke procedure against only `https://data.sec.gov`. Live
   smoke remains outside CI and cannot write production data.
5. **Historical-segment gate:** model and validate `filings.files`, constrain
   SEC-returned filenames, fetch advertised per-filer segments, prove range and
   cardinality coherence, handle overlaps/duplicates deterministically, and
   distinguish recent-only from complete history. Nightly bulk bootstrap needs
   an additional resource/integrity/atomicity review.
6. **Capture and persistence gate:** approve an append-only raw/source receipt,
   exact response-byte SHA-256, returned `ETag`/`Last-Modified` when present,
   HTTP/media metadata, parser version, capture revisions, retention,
   removal/redaction handling, idempotency, Flyway schema, and PIT replay before
   any database write. None is currently persisted.
7. **Ingestion and scheduler gate:** approve issuer/CIK scope, bootstrap
   watermark, polling cadence, freshness and health meanings, retry ownership,
   correction/removal reconciliation, and single-/multi-instance rate-limit
   coordination before automation.
8. **API gate:** approve a closed read contract, source/as-of/capture fields,
   recent-versus-complete-history status, unavailable states, abuse controls,
   cache semantics, OpenAPI, and tests before HTTP publication.
9. **UI/publication gate:** approve evidence-first desktop/mobile, loading,
   error, empty, keyboard-focus and stale states; exact SEC attribution; no SEC
   endorsement; and separation from analyst calls and market data before public
   display.
10. **Scope-expansion gate:** Company Facts/XBRL, filer-authored documents,
    exhibits, full text, additional root or filing fields, bulk archives, BLS,
    BEA, EIA, and every other data product/provider each require their own exact
    fields, PIT, provenance, rights, operations, and publication review.

Production automation is prohibited until gates 4 and 7 pass. Historical
completeness requires gate 5; database ingestion requires gate 6; API use
requires gate 8; and public UI use requires gate 9.

## Production and non-scope boundary

This slice adds only the server configuration, provider-neutral port, canonical
filing records/catalog, SEC vendor DTO and mapper, one-request adapter, safe
configuration/provider exceptions, and focused mock/unit tests described above.
It adds no database table or row, Flyway migration, repository, API/OpenAPI
path, controller, application orchestration, UI, route, scheduler, polling loop,
historical traversal, live smoke, CI network access, or public product behavior.

It also adds no Company Facts or other XBRL integration, filing-document or
exhibit ingestion, full-text search, ticker master, BLS, BEA, EIA, Federal
Reserve, Treasury, CFTC, BOK, KOSIS, Korean equity data, analyst call, market
quote, stock price, option, futures price, return, MFE/MAE, ranking,
recommendation, or AI extraction. SEC filing metadata is not a market-price or
analyst-call fallback and cannot make an existing DEMO value observed.

## Verification

- Default configuration creates no SEC client/provider bean; explicit enable
  requires server contact configuration.
- Mock HTTP tests cover the declared `User-Agent`, exact CIK request path,
  canonical response mapping, fixed-clock processing/capture time, non-2xx and
  unreadable response sanitization, invalid response closure, and no fallback.
- Mapper and domain tests cover CIK normalization, all recent-array alignment,
  nullable report date, accession and primary-document identity, official URI
  construction, immutable published order, duplicate provider-event rejection,
  UTC microsecond precision, and timestamp ordering.
- No test reaches SEC or any other external network service.

## Consequences

- P5 now has a narrow, keyless SEC filing-metadata adapter foundation without
  treating public availability as permission for unrelated content.
- The implemented canonical model preserves only the fields it can currently
  validate and explain; validation-only vendor arrays do not leak across the
  adapter boundary.
- Recent data cannot be mislabeled as complete history, and an official filing
  URI cannot be mistaken for downloaded or licensed document content.
- The provider stays disabled and unscheduled by default. The user needs no SEC
  API key, but must maintain a real monitored contact email before live use.
- Persistence, automation, historical traversal, API/UI publication, live smoke,
  Company Facts, macro providers, and market prices remain explicitly absent.
