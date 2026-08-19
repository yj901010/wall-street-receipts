# P1 Acceptance Checks — Analyst Calls

Current status: the analyst-call list/detail vertical slice, explicit
correction/cancellation lineage, and versioned outcome contract/fixture slice
are complete. The broader P1 phase remains open until its runtime gates and the
remaining context models pass. Numeric scoring methodology is deferred to P3.

P1 is complete only when the analyst-call list/detail vertical slice satisfies
every check below with deterministic DEMO data and no commercial provider or
growth-phase runtime dependency.

## Contract gate

| ID | Check | Required evidence |
| --- | --- | --- |
| P1-C01 | `contracts/openapi.yaml` parses as OpenAPI 3.1 and exposes `GET /v1/calls` and `GET /v1/calls/{id}`. | CI repository-contract job passes. |
| P1-C02 | The contract defines only `assetId`, `ticker`, `institutionId`, `analystId`, `direction`, `status`, `dataMode`, `from`, `to`, `page`, `size`, `sort`, and `order`. | Contract assertion for the exact names and controller tests for every supported filter. |
| P1-C03 | List success is exactly `{items, page}` and `page.sort` is exactly `{field, order}`. | JSON response assertion with unknown-property checks. |
| P1-C04 | IDs are opaque strings; DEMO identifiers such as `demo-call-002` work without UUID coercion. | Detail endpoint test using a non-UUID ID. |
| P1-C05 | Invalid parameters return `application/problem+json`; an unknown valid ID returns 404 in the same shape. | 400 and 404 contract tests asserting `type`, `title`, `status`, `detail`, `instance`, `code`, `timestamp`, `requestId`, and `violations`. |

## List behavior

| ID | Check | Expected result |
| --- | --- | --- |
| P1-L01 | No query parameters | Page 0, size 25, sorted by `eventTime desc` then `callId asc`. |
| P1-L02 | `page=0&size=1` followed by `page=1&size=1` | No duplicate or skipped record across the stable boundary. |
| P1-L03 | Each scalar identity filter | Every returned record exactly matches the requested canonical ID or normalized ticker. |
| P1-L04 | Each `direction`, `status`, and `dataMode` filter | Every returned record matches the single enum value. |
| P1-L05 | `from` only | `eventTime >= from`; the lower bound is inclusive. |
| P1-L06 | `to` only | `eventTime < to`; the upper bound is exclusive. |
| P1-L07 | Multiple filters | Filters are combined with logical AND. |
| P1-L08 | No record matches | HTTP 200 with `items: []`, `totalElements: 0`, and `totalPages: 0`. |
| P1-L09 | Invalid page, size, enum, timestamp, sort, or `from >= to` | HTTP 400 Problem response; no silent fallback. |

## Detail and data-integrity gate

| ID | Check | Expected result |
| --- | --- | --- |
| P1-D01 | Known call ID | Response contains `call`, `institution`, nullable `analyst`, `asset`, `source`, and nullable `snapshot`. |
| P1-D02 | Analyst attribution is unavailable | `analyst` is JSON null; no placeholder person is invented. |
| P1-D03 | Target, price, macro, or market measure is unavailable | The field is JSON null and the UI renders `NA`, never zero. |
| P1-D04 | Source evidence | `source.document` and `source.reference` identify the title/type/URL or explicit null, publish/capture time, license class, verification state, and provenance. |
| P1-D05 | Market snapshot exists | It is reconstructed for `eventTime`, retains distinct `processingTime`, is marked immutable, and exposes data mode/capture provenance. |
| P1-D06 | Market snapshot does not exist | `snapshot` is JSON null; the server does not synthesize context. |
| P1-D07 | Duplicate provider event | Re-ingesting the same `(provider, providerEventId)` produces one canonical call. |
| P1-D08 | Correction or cancellation | The original call is not overwritten; lifecycle/revision behavior remains auditable. |
| P1-D09 | Nullable source-document metadata | The append-only `demo-call-003` → `source-ref-demo-003` → `source-demo-article-003` DEMO chain carries JSON null for `publisher`, `canonicalUrl`, `publishedAt`, `externalId`, and `contentHash`; fixture validation, provider mapping, PostgreSQL fresh/upgrade persistence and read, and `GET /v1/calls/{id}` preserve those nulls. Existing call/source rows are not rewritten, and `source-demo-video-002` retains the populated source path as the positive case. |

## Revision-lineage gate

| ID | Check | Expected result |
| --- | --- | --- |
| P1-R01 | Deterministic DEMO lineage | The versioned fixture contains a correction followed by a cancellation for a known opaque call ID, and the canonical revision schema is closed and versioned. |
| P1-R02 | Original preservation | Importing the lineage leaves every original analyst-call column and its immutable snapshot unchanged. |
| P1-R03 | Revision identity | Re-ingesting `(provider, providerEventId)` resolves to one canonical event, while a base-call/revision kind collision is rejected atomically by the shared identity registry. |
| P1-R04 | Ordered append-only chain | Sequence numbers are contiguous, each non-root revision supersedes the immediately preceding revision for the same call, and no event follows a cancellation. |
| P1-R05 | Time and evidence | Every revision preserves `eventTime <= processingTime <= capturedAt`, source reference, data mode, and provenance; a correction carries complete replacement terms while a cancellation carries none. |
| P1-R06 | Read-only audit surface | `GET /v1/calls/{id}/revisions` returns sequence-ascending canonical records, returns `[]` for a known call without revisions, returns a closed 404 Problem for an unknown call, and no revision mutation endpoint exists. |

## Versioned outcome gate

P1 establishes an immutable, deterministic calculation boundary and auditable
result envelope. It does not claim that financial scoring has been implemented.

| ID | Check | Expected result |
| --- | --- | --- |
| P1-O01 | Closed canonical contracts | `scoring-methodology` and `call-outcome` schemas are Draft 2020-12, versioned, closed, and accept only their documented exact field sets. Every versioned DEMO instance validates with format checking enabled. |
| P1-O02 | Methodology identity and coexistence | The fixture contains exactly two methodologies. `(methodologyId, methodologyVersion)` identifies an immutable definition hash; a version/hash conflict is rejected while both definitions remain readable. Schema, fixture, methodology, and outcome sequence versions are distinct concepts. |
| P1-O03 | Complete outcome envelope | Every outcome has an opaque ID, call/horizon, nullable snapshot, correction-basis, and cancellation-evidence references, methodology version and definition hash, input fingerprint, append-only sequence/supersession, explicit status/reason, event/processing/capture times, data mode, and provenance. A non-null `basisRevisionId` belongs to the same call and is a correction; a non-null `cancellationRevisionId` belongs to the same call and is a cancellation. |
| P1-O04 | Missing-value and decimal semantics | All P1 DEMO financial metrics and result booleans are JSON null, not zero or false. A missing target, neutral direction, or unavailable benchmark/sector observation does not invent a result; clients render null as `NA`. Future numeric results use decimal precision 38/scale 12 and magnitude strictly below `1e26`; `targetError` is in `[0, 1e26)`. Values requiring silent scale rounding or exceeding these bounds are rejected rather than altered. |
| P1-O05 | Status, reason, and cancellation evidence | The only valid pairs are `PENDING/HORIZON_NOT_REACHED`, `INCOMPLETE/HORIZON_DATA_MISSING`, `EXCLUDED/CALL_CANCELLED`, and `CALCULATED/null`. `dataComplete` is true only for `CALCULATED`. `EXCLUDED/CALL_CANCELLED` requires `cancellationRevisionId`; every other status requires it to be null. The P1 DEMO fixture contains only pending and incomplete model records. |
| P1-O06 | Natural input identity and idempotency | The natural calculation input key is the five-field lineage plus `inputFingerprint` and is unique. Snapshot or cancellation-evidence changes must produce a new fingerprint, not a second payload under the same natural key. Replaying an identical key and payload returns the existing outcome without mutation; the same key with a different payload/hash is rejected atomically. |
| P1-O07 | Append-only recalculation lineage | A lineage is scoped exactly by `(callId, basisRevisionId, horizon, methodologyId, methodologyVersion)`. The version-1 D1 DEMO lineage is sequence 1 followed by sequence 2, where a changed input fingerprint appends sequence 2 and supersedes sequence 1. Every other fixture lineage is a root, and supersession never crosses the five-field lineage scope. |
| P1-O08 | Source preservation and point-in-time safety | Creating or replaying outcomes does not change the original call, snapshot, revision, methodology definition, or a prior outcome. Canonical methodology and outcome timestamps are UTC `Z` instants with zero to six fractional digits; finer precision is rejected rather than rounded. Call and snapshot evidence preserves `eventTime <= processingTime <= capturedAt`, lineage time does not move backwards, and every referenced call, snapshot, correction basis, and cancellation evidence was both processed and captured no later than outcome processing time. Methodology effective/capture times also precede outcome processing, and fixture generation is not earlier than any contained processing/capture time. |
| P1-O09 | Read-only audit endpoint | `GET /v1/calls/{id}/outcomes` returns the closed canonical array, `[]` for a known call without outcomes, a closed 400 Problem for an invalid opaque ID, a closed 404 Problem for an unknown call, and a closed 500 Problem for an unexpected failure. No outcomes-prefix mutation operation exists. |
| P1-O10 | Backward compatibility | Adding outcomes does not add a property to the exact `/v1/calls` list or `/v1/calls/{id}` detail response. Outcome history is available only from the additive subresource. |
| P1-O11 | Deterministic boundary | P1 exposes no calculator or scheduler. Outcome persistence accepts only explicit immutable input identity and a registered methodology definition; it does not fetch current prices or invoke a provider, network, clock, or LLM, and unknown methodology versions are rejected. P3 must preserve that pure-input boundary when calculation is added. |
| P1-O12 | Repository fixture gate | CI verifies exactly two methodology instances and four outcome instances, manifest parity, schema/format validity, closed fields, all references, provenance, natural-key uniqueness, lineage, time order, and status/reason/data-completeness rules in under five minutes. |

Whether a terminal cancellation should produce an
`EXCLUDED/CALL_CANCELLED` outcome is intentionally not inferred by the
repository fixture check. A cancellation is never a valid scoring basis;
effective lifecycle projection and cancellation eligibility remain a later
service rule.

### Deferred scoring acceptance (P3)

P3 owns horizon due-date resolution and trading calendars, corporate-action
adjustment, deterministic return/alpha/sector-alpha calculations, target-hit and
target-error logic, MFE/MAE, directional-win rules, benchmark and sector
selection, numeric golden tests, sample confidence, and leaderboard inclusion.
Introducing a non-null computed DEMO metric makes its versioned input fixture and
corresponding P3 golden test mandatory.

## Architecture and runtime gate

- Provider DTOs stay under the provider adapter and do not appear in controller,
  canonical schema, or domain packages.
- Financial values use decimal-safe types in Java; timestamps use UTC instants
  and time-dependent behavior uses an injected `Clock`.
- `call.status` is the value recorded on the immutable original event. Revision
  history never rewrites it; an effective lifecycle projection is out of scope.
- No call, snapshot, revision, methodology, or outcome mutation endpoint is
  introduced in P1.
- The app boots and tests run with fixture mode and PostgreSQL only; no vendor
  key, Redis, Kafka, ClickHouse, OpenSearch, or object storage is required.
- Source payloads contain metadata/evidence only. Full articles, reports, and
  rehosted media are not stored.

## Local gate

Run from a clean feature branch before integration:

```powershell
docker compose --env-file .env.example config --quiet
pnpm --dir apps/web lint
pnpm --dir apps/web test
pnpm --dir apps/web build
Set-Location apps/api
.\mvnw.cmd -B -ntp verify
```

Record the executed commands, pass/fail counts, routes, and any deferred P1
checks in `IMPLEMENTATION_LOG.md`. A deferred mandatory check keeps P1 open.
