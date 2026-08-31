# ADR-054: Site-wide KST display-time policy

- Status: Accepted
- Date: 2026-08-31
- Depends on: ADR-005, ADR-046, ADR-053

## Context

Wall Street Receipts stores and transports point-in-time evidence as canonical
UTC instants. The web application nevertheless rendered those instants through
several unrelated UTC formatters, and some audit tables printed raw ISO values.
That created inconsistent precision and made a Korea-first public site require
users to translate ordinary display times mentally. The calls date filters also
interpreted a browser-selected calendar day as a UTC day rather than a Korean
calendar day.

Changing persisted values, API contracts, ordering, or point-in-time comparison
to local time would weaken reproducibility. The requirement is therefore a
presentation and user-input-calendar policy, not a storage-timezone migration.

## Decision

### One explicit display zone

Every user-visible instant is rendered in the IANA zone `Asia/Seoul` with an
explicit `KST` suffix. The shared, locale-neutral financial format is:

```text
YYYY-MM-DD HH:mm:ss[.source-fraction] KST
```

The formatter uses an explicit Gregorian calendar and Latin digits, does not
depend on the browser, host, container, JVM, or operating-system default
timezone, and preserves the source fractional-second digit count through
nanosecond precision. Korean and English pages show the same instant string;
only labels and explanatory copy are localized.

The `YYYY` contract supports Gregorian years 0001 through 9999. Source year
`0000`, a conversion into BCE, or a conversion beyond year 9999 fails closed;
supported early years remain zero-padded to four digits.

All semantic instant markup goes through one `KstTimestamp` component. Its
visible text is KST while the HTML `datetime` attribute retains the exact
source RFC 3339 value. A malformed non-null instant fails closed. Only an
explicit domain null may render as `NA`; invalid text is never hidden as `NA`,
relabelled without conversion, or normalized into an invented fact.

### UTC remains canonical evidence

This decision does not change:

- PostgreSQL or fixture values;
- Java `Instant`, event/processing/capture distinctions, or injected clocks;
- JSON/OpenAPI/provider DTO and adapter contracts;
- point-in-time comparisons, immutable snapshot identity, sorting, or hashes;
- API request instants and exact canonical query identities; or
- machine-readable `<time datetime>` source values.

The exact SEC `evaluationAsOf` query remains the ADR-053 UTC `Z` API key in the
URL and locator input. The form labels that field as an original API lookup key
and states that result times are displayed in KST. Summary, occurrence, and
request-evidence instants render in KST while canonical links continue to carry
the byte-exact UTC cutoff.

### Calendar dates and durations are not instants

Source facts that are calendar dates remain unchanged text. This includes
target, observation, vintage, filing, report, and provider-advertised range
dates. Durations, horizons, processing delay, page media offsets, session
labels, and delayed-data notices also receive no timezone conversion.

Browser `from` and through-date controls on `/calls` are different: they are
user-selected civil-day filters. Each value is interpreted as a Korean
calendar day. Its inclusive start is `00:00 KST`; the through-date upper bound
is the following day's exclusive `00:00 KST`. The server converts those bounds
to canonical UTC instants before calling the existing API. For example:

```text
2026-08-11 KST -> [2026-08-10T15:00:00.000Z,
                    2026-08-11T15:00:00.000Z)
```

The URL retains the civil `YYYY-MM-DD` values and the backend API remains UTC.

### Scope and external requirements

This phase changes only repository code, tests, and documentation. It needs no
API key, provider account, paid plan, domain, home-server access, deployment
machine, operator token, or new secret. Later API-backed SEC success still
requires a genuinely persisted manifest ID and allowed UTC cutoff. Internet
publication still requires the actual domain. Live SEC collection still
requires the separately approved monitored contact email in an untracked
server environment.

## Verification plan

Verification covers:

- UTC-to-KST day rollover and explicit `Asia/Seoul` use;
- source fractional precision from one through nine digits;
- valid RFC 3339 offsets and rejection of malformed dates, times, and offsets;
- four-digit-year padding and fail-closed lower/upper conversion boundaries;
- exact raw `datetime` preservation under KST visible text;
- KST civil-day to inclusive/exclusive UTC API bounds, including leap dates;
- Korean and English parity across dashboard, maps, market, directories,
  methodology, calls, S&P history, and SEC audit routes;
- a production-source guard against page-local UTC formatters or direct
  `<time>` rendering outside the shared component;
- lint, complete web unit and browser suites, production build, responsive
  containment, and the nested historical repository projections.

The implementation log records only checks that actually ran.

## Consequences

- A Korean user sees one unambiguous site-wide time convention without losing
  canonical UTC evidence or point-in-time reproducibility.
- Visible timestamps are slightly denser because seconds and observed
  fractional precision are no longer silently discarded.
- The SEC exact-key field remains an explicitly labelled machine/API exception,
  not a competing display timezone.
- Any new web instant must use the shared component; new civil-date filters
  must state their calendar zone and convert at the server boundary.
