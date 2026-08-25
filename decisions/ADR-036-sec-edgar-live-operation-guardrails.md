# ADR-036 — SEC EDGAR Single-Process Live-Operation Guardrails

- Status: Accepted
- Date: 2026-08-25

ADR-036 establishes the single-process SEC live-operation safety gate.

## Context

ADR-035 selected the keyless, server-only SEC EDGAR Submissions API and left
live operation behind a separate gate. A correctly identified client is not by
itself safe to run against an external public service: callers must bound their
request rate and decoded response memory, react conservatively to rate-limit
responses, and prove the real wire path without making ordinary verification
depend on the network.

This decision closes that gate only for one JVM and one explicitly invoked
read-only smoke request. It does not authorize scheduling, polling, persistence,
multi-replica deployment, or product publication.

## Official SEC rules and documented behavior

The following are SEC-published constraints, not locally chosen product policy:

- SEC's fair-access ceiling is **10 requests per second in aggregate**, even
  when requests originate from multiple machines. The ceiling is not a burst
  allowance for each host or process.
- SEC's Internet Security Policy states that, after traffic exceeds the limit,
  access may resume only after the request rate has remained below the
  threshold for 10 minutes.
- Automated clients must declare a descriptive `User-Agent` with operational
  contact information. The existing ADR-035 server adapter does this and also
  requests gzip/deflate transport encoding.
- The selected public API is keyless and server-side at the exact origin
  `https://data.sec.gov`; browser CORS is not supported for this integration.

The SEC public documentation does **not** promise that every limiting event
will use HTTP `429`, that `Retry-After` will be present, or that submissions
responses have a fixed byte maximum or trustworthy `Content-Length`. The
implementation therefore must not infer those guarantees.

Official sources:

- [SEC Developer Resources — Fair Access](https://www.sec.gov/about/developer-resources)
- [Accessing EDGAR Data](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)
- [SEC Privacy Information — Internet Security Policy](https://www.sec.gov/about/privacy-information#internet-security-policy)
- [SEC Webmaster Frequently Asked Questions](https://www.sec.gov/about/webmaster-frequently-asked-questions)
- [SEC EDGAR Application Programming Interfaces](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)

## Internal conservative policy

The following limits are Wall Street Receipts engineering decisions inferred
from the official constraints. They are not SEC-published service guarantees.

### One-JVM request spacing

All SEC HTTP calls in one application JVM share one process-local limiter. The
limiter grants at most 8 requests per second with fixed 125 millisecond spacing.
It does not accumulate unused capacity, so an idle interval cannot produce a
token-bucket burst. Every current and future SEC request path, including any
future reviewed retry path, must pass through that same instance.

The 8-request internal ceiling is deliberately below the SEC's aggregate
10-request ceiling. It is not proof of aggregate compliance across multiple
JVMs, containers, hosts, or separately run tools. Multi-replica activation and
scheduled collection remain prohibited until one reviewed distributed/global
coordinator covers every SEC caller.

### Decoded response-size limit

A submissions response may expose at most **8 MiB (8,388,608 bytes) after
decompression** to JSON mapping. An identity response whose declared
`Content-Length` already exceeds the limit is rejected and closed before body
mapping. Missing, incorrect, chunked, gzip, or deflate length metadata does not
bypass the limit: the decoded stream reads at most the configured maximum plus
one probe byte and fails closed if more data exists. Exactly 8 MiB is allowed.

The decoded boundary is intentional. It bounds both responses without a useful
length and compressed payloads whose small wire representation expands beyond
the safe in-process budget. Exceeding it produces a sanitized provider failure;
no response body or header value is copied into an error.

### HTTP 429 and `Retry-After`

HTTP `429` never causes an automatic retry. The response opens a shared
process-local cooldown, and the triggering request fails with the sanitized
HTTP status error.

- A valid `Retry-After` delta-seconds value or RFC 1123 date is used only to
  determine the cooldown duration.
- A missing, malformed, expired, or shorter-than-10-minutes value becomes the
  conservative 10-minute minimum.
- A valid value longer than 10 minutes is honored. A numeric value too large
  for the monotonic representation becomes the maximum representable
  fail-closed cooldown rather than wrapping or shortening.
- A shorter later cooldown cannot replace a longer remaining cooldown.
- While a cooldown is active, new calls fail immediately before network I/O.
  Request threads are not put to sleep for 10 minutes.

No response header content appears in exceptions or logs. This policy does not
claim the SEC always returns `429` or `Retry-After`; other denied or unreadable
responses continue to fail closed without fallback.

## Manual live smoke boundary

The live smoke test is an explicit operator action, not a recurring health
check. It requires both Maven profile `sec-live-smoke` and environment flag
`SEC_LIVE_SMOKE=true`, uses the existing root `.env` `SEC_CONTACT_EMAIL`, and
makes exactly one request for Apple CIK `0000320193` at the exact official
origin. It verifies the live transport and canonical mapping boundary only.

The smoke test needs no API key, provider account, paid plan, OAuth credential,
or new registration. The operator must maintain the real monitored contact
email already required by ADR-035 and must not paste it into chat or commit it.
The test does not persist or log the response body, contact email, complete
`User-Agent`, or request/response headers.

Ordinary `test`/`verify`, the default Maven profile, and CI never run the live
smoke and never contact SEC. A mock test remains mock evidence; a successful
manual smoke is transient operational evidence and does not authorize storing
or publishing the returned filings.

## Remaining boundaries

This decision adds no database table, raw receipt, scheduler, polling loop,
historical-segment traversal, controller, public API, web consumer, or UI. It
also does not approve multi-instance collection. Those changes still require
the historical, capture/persistence, orchestration, API, and publication gates
defined by ADR-035, including durable provenance and source/as-of/capture
semantics where applicable.

Process restart loses process-local spacing and cooldown state. Operators must
not use restarts, parallel shells, or another host to evade a cooldown. A
production collector must persist or globally coordinate the relevant state
before it can be approved.

## Consequences

- A single JVM now has bounded, no-burst SEC traffic and bounded decoded-body
  consumption.
- Rate-limit handling favors provider protection and transparent failure over
  availability; it never substitutes fixtures, stale rows, empty results, or
  immediate retries.
- Manual live verification is possible without acquiring another credential,
  while default builds remain deterministic and offline.
- The adapter remains unsuitable for multi-replica or scheduled production use
  until aggregate coordination and the remaining ADR-035 gates are completed.
