# ADR-037 — SEC EDGAR Decoded-Response Receipt Foundation

- Status: Accepted
- Date: 2026-08-25

ADR-037 establishes the in-memory SEC decoded-response receipt foundation.

## Context

ADR-035 introduced the default-disabled SEC EDGAR Submissions adapter, and
ADR-036 bounded its one-JVM live request path. The adapter could identify the
source URI and capture time of a mapped filing catalog, but it did not bind the
catalog to the exact response bytes that the parser observed. Parsing directly
from an HTTP message converter would also leave room for an accidental gap
between bytes hashed for provenance and bytes parsed into the canonical model.

This decision closes only that transient byte-to-parser provenance gap. It does
not claim durable source capture or replay and does not pass the historical,
persistence, orchestration, API, or publication gates.

## Decoded-byte identity

Only a fully read HTTP `200` response with one unambiguous
`application/json` media type can produce a receipt. If the transport advertises
gzip or deflate, the entity is decompressed exactly once; the receipt then owns
the exact decoded bytes exposed after that transport step and the ADR-036 8 MiB
decoded-size boundary. Identity responses use their exact entity bytes at the
same boundary.

An encoded `Content-Length` describes the compressed representation, not the
decoded entity. The decompression boundary therefore preserves the original
`Content-Encoding` for the receipt but removes `Content-Length` from the
decoded downstream header view for gzip, x-gzip, and deflate. It does not
mutate the provider response headers. The decoded streaming cap, complete read,
and captured decoded length remain the only accepted decoded-size facts.

The implementation calculates SHA-256 over those exact decoded bytes and
stores the digest as 64 lowercase hexadecimal characters. It does not decode
and re-encode text, normalize a charset, normalize whitespace or line endings,
canonicalize JSON, reorder fields, or serialize a parsed object to obtain the
digest. An absent charset or an explicit UTF-8 charset is accepted for
`application/json`; any other declared charset fails closed.

The same owned defensive byte copy is used for both SHA-256 and JSON parsing.
The parser therefore cannot observe bytes different from the digest input.
Parsing rejects trailing JSON tokens and does not change the already calculated
receipt identity. The versioned reader also rejects duplicate object keys,
scalar coercion, and floating-point-to-integer coercion so its meaning does not
inherit those permissive application-mapper defaults.

The decoded entity must itself be a well-formed UTF-8 byte sequence. UTF-16,
UTF-32, and malformed UTF-8 fail before receipt creation. Validation neither
removes a valid UTF-8 BOM nor otherwise transforms the bytes hashed and parsed.

## Receipt fields and metadata allowlist

`SourceResponseReceipt` records the provider and product identity together
with these capture facts:

- exact source URI;
- `capturedAt`, taken at UTC microsecond precision after the complete entity
  body has been read;
- HTTP status `200` and the accepted media type;
- normalized transport content encoding: `IDENTITY`, `GZIP`, or `DEFLATE`;
- optional opaque `ETag` and optional parsed `Last-Modified`;
- parser version `SEC_SUBMISSIONS_RECENT_V1`;
- lowercase SHA-256 of the exact decoded body and its positive decoded byte
  length; and
- body representation `DECODED_HTTP_ENTITY_BODY` and retention state
  `RECEIPT_ONLY_BODY_NOT_RETAINED`.

The filing catalog retains the complete receipt and must match its provider,
product, source URI, and capture timestamp. These invariants reject
cross-provider, cross-product, cross-URI, and cross-time attachment. The
byte-to-DTO link itself is established by the package-private capture path that
decodes and maps the same owned bytes; it is a trusted adapter boundary, not a
cryptographic attestation against arbitrary in-process object construction.

Response metadata is deny-by-default. Only status, accepted media type,
normalized transport content encoding, `ETag`, and `Last-Modified` enter the
receipt; all other response headers are discarded. Ambiguous, malformed, or
unsupported allowlisted values fail closed with a sanitized provider error.
Request headers are never copied into the receipt. In particular, the contact
email, complete `User-Agent`, and other request identity headers are not
preserved, logged, or surfaced by this foundation.

## Retention and trust boundary

`RECEIPT_ONLY_BODY_NOT_RETAINED` means the decoded body is held in memory only
long enough to hash and parse the bounded response. The receipt retains its
digest and metadata, not the body. This is an explicit present limitation, not
an assertion that durable raw bytes exist elsewhere.

The SHA-256 value is a local equality and integrity identifier for the parser
input. It is not an SEC digital signature, does not authenticate SEC as the
sender, does not prove publication time or legal provenance, and does not make
the response tamper-evident without an independently trusted durable record.
HTTPS transport and the configured exact-origin rules remain separate controls.

## Remaining boundaries and sequence

This decision adds no durable raw-body retention, replay reader, persistence,
Flyway migration, database table or row, repository, scheduler, polling loop,
multi-instance coordination, controller, public API, OpenAPI contract, or web
publication. It also does not make the recent submissions arrays complete
history and does not authorize using a digest as a substitute for retained
source evidence.

The next SEC gate is historical submissions-segment modeling and validation,
including `filings.files`, constrained filenames, range/cardinality coherence,
and deterministic overlap or revision handling. Append-only receipt and filing
persistence follows that historical-segment gate, with durable raw-body and
replay policy reviewed explicitly before any database write. Scheduling and
publication remain later gates.

No new API key, provider account, paid plan, OAuth credential, registration, or
plugin is required for this foundation. The existing monitored
`SEC_CONTACT_EMAIL` remains necessary only when an operator explicitly enables
or manually smoke-tests the live SEC adapter; it is not receipt content.

## Consequences

- Every accepted recent-submissions catalog is bound in memory to the exact
  decoded bytes supplied to its versioned parser.
- Digest identity is deterministic across identical decoded bodies and remains
  sensitive to every byte-level difference, including insignificant JSON
  formatting differences.
- Metadata collection remains narrow enough to avoid retaining request identity
  or arbitrary response headers.
- The receipt improves transient traceability but cannot provide durable replay,
  persistence, SEC sender authentication, or public evidence by itself.

## Verification

- Focused SEC configuration, transport, receipt, domain, and mapper regression:
  **PASS** — 91 tests with zero failures, errors, or skips.
- Complete API Maven verification: **PASS** — 2,157 tests with zero failures,
  errors, or skips, including PostgreSQL 17 Testcontainers/Flyway and Spring
  Boot packaging.
- Manual opt-in live verification: **PASS** after correcting the stale encoded
  `Content-Length` downstream view. The final smoke made exactly one read-only
  Apple submissions request and validated the complete strict parse and catalog
  mapping. No body, response value, contact address, or full User-Agent was
  logged or retained.
- Web regression: **PASS** — ESLint, 42 Vitest files / 569 tests, and the Next.js
  production build with 12 static-generation steps. Compose validation passed.
- Repository guard: **PASS** — all 42 embedded Python bodies compile, all 35
  environment-independent bodies execute successfully, and ADR-034/035/036
  reverse projections plus the ADR-037 exact-manifest/semantic guard pass.
