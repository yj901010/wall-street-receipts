# P1 Acceptance Checks — Analyst Calls

Current status: the analyst-call list/detail vertical slice and explicit
correction/cancellation lineage are complete, but the broader P1 phase remains
open. The outcome model and remaining context models are deferred to subsequent
P1 feature branches and must pass before P1 is marked complete.

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

## Revision-lineage gate

| ID | Check | Expected result |
| --- | --- | --- |
| P1-R01 | Deterministic DEMO lineage | The versioned fixture contains a correction followed by a cancellation for a known opaque call ID, and the canonical revision schema is closed and versioned. |
| P1-R02 | Original preservation | Importing the lineage leaves every original analyst-call column and its immutable snapshot unchanged. |
| P1-R03 | Revision identity | Re-ingesting `(provider, providerEventId)` resolves to one canonical event, while a base-call/revision kind collision is rejected atomically by the shared identity registry. |
| P1-R04 | Ordered append-only chain | Sequence numbers are contiguous, each non-root revision supersedes the immediately preceding revision for the same call, and no event follows a cancellation. |
| P1-R05 | Time and evidence | Every revision preserves `eventTime <= processingTime <= capturedAt`, source reference, data mode, and provenance; a correction carries complete replacement terms while a cancellation carries none. |
| P1-R06 | Read-only audit surface | `GET /v1/calls/{id}/revisions` returns sequence-ascending canonical records, returns `[]` for a known call without revisions, returns a closed 404 Problem for an unknown call, and no revision mutation endpoint exists. |

## Architecture and runtime gate

- Provider DTOs stay under the provider adapter and do not appear in controller,
  canonical schema, or domain packages.
- Financial values use decimal-safe types in Java; timestamps use UTC instants
  and time-dependent behavior uses an injected `Clock`.
- `call.status` is the value recorded on the immutable original event. Revision
  history never rewrites it; an effective lifecycle projection is out of scope.
- No call or snapshot mutation endpoint is introduced in P1.
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
