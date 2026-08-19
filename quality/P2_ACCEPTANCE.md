# P2 Acceptance Checks — Methodology Registry

Current status: the methodology-registry vertical slice is complete. This
document closes the first P2 slice only; the remaining dashboard, market,
leaderboard, screener, and map work stays open.

The methodology registry is a read-only, fixture-backed explanation surface. It
publishes the immutable definition identities already present in
`fixtures/v1/call-outcomes.json`; it does not calculate, activate, rank, or
recommend anything.

## Slice boundary

- The public web route is `GET /methodology`.
- The canonical source is the `methodologies` array in
  `fixtures/v1/call-outcomes.json`, validated by
  `schemas/scoring-methodology.schema.json`.
- The web provider reads the versioned root fixture through a typed adapter. It
  does not duplicate methodology records in application source.
- This slice adds no API endpoint, database migration, persistence write,
  provider network call, scoring calculator, scheduler, or ranking aggregate.
- `GET /v1/calls/{id}/outcomes` and every existing call response remain
  unchanged.

## Contract and fixture gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-M01 | Closed canonical record | `scoring-methodology.schema.json` is Draft 2020-12, uses `additionalProperties: false`, requires every documented field, and accepts only `methodologyId`, `methodologyVersion`, `schemaVersion`, `definitionHash`, `status`, `effectiveAt`, `dataMode`, `capturedAt`, and `provenanceId`. |
| P2-M02 | Exact registry evidence | The fixture contains exactly `standard-call-outcome@1.0.0` followed by `standard-call-outcome@2.0.0`; no third, missing, or reordered definition is silently accepted. |
| P2-M03 | Immutable identity | `(methodologyId, methodologyVersion)` is unique. Both definitions have distinct immutable hashes; a version is never inferred from `schemaVersion`, `fixtureVersion`, or an outcome sequence. |
| P2-M04 | Definition hash | Every `definitionHash` is exactly 64 lowercase hexadecimal characters. The registry renders it as evidence and never labels it as a performance or quality score. |
| P2-M05 | Model-only status | Both records are exactly `MODEL_ONLY`. The UI must not translate that state to active, production, approved, current, scored, or recommended. |
| P2-M06 | Time and provenance bounds | Every record uses canonical UTC with at most microsecond precision and preserves `effectiveAt <= capturedAt <= fixture.provenance.capturedAt <= fixture.generatedAt`. Record `dataMode` equals the DEMO envelope, `provenanceId` equals the envelope provenance ID, and the provenance is synthetic `INTERNAL_DEMO` local-specification evidence. |
| P2-M07 | Deterministic presentation order | Provider and route output use the fixture's canonical identity order, `1.0.0` then `2.0.0`. Client locale, object-key enumeration, or status text must not change the order. |
| P2-M08 | No calculation claim | The fixture disclaimer remains explicit that no outcome metric was calculated or invented. Every DEMO outcome metric/result is JSON null, no outcome is `CALCULATED`, and no registry copy claims return, alpha, hit rate, accuracy, sample confidence, or ranking. |
| P2-M09 | Outcome linkage evidence | Every existing outcome references one of the two exact methodology identities and carries the matching `methodologyDefinitionHash`; presenting the registry does not project or mutate outcome state. |

## Web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-W01 | Route and navigation | `/methodology` is server rendered and the primary Methodology navigation target resolves to that route rather than an in-page placeholder. |
| P2-W02 | Exact evidence | Both versions render their methodology ID, version, full definition hash, `MODEL_ONLY` status, effective time, captured time, DEMO mode, and provenance ID. Missing values are never invented. |
| P2-W03 | Meaningful explanation | The page explains schema version, methodology version, definition hash, effective time, and capture time without claiming the underlying formula is present in the fixture. |
| P2-W04 | Honest state | A prominent DEMO/model-only notice and the fixture disclaimer make clear that scoring is deferred to P3. There is no accuracy, return, leaderboard, active-version, or investment-performance claim. |
| P2-W05 | Source traceability | The page exposes the fixture source and capture/as-of evidence. It does not link to a fabricated external methodology document or imply live-provider data. |
| P2-W06 | Loading, error, and empty behavior | Route boundaries provide an explicit loading state, a recoverable error state, and an honest empty state if the registry has no records. Empty does not become a placeholder version. |
| P2-W07 | Accessibility and responsive layout | Evidence uses semantic headings/table or description lists, copyable hashes remain keyboard reachable, focus is visible, and any dense table scrolls locally without page-level overflow at 1440, 1280, and 390 pixels. |
| P2-W08 | Regression boundary | Existing `/`, `/calls`, and `/calls/{id}` behavior and exact API response contracts remain unchanged. |

## Required tests

- Repository-contract CI validates the exact two-record projection, canonical
  order, unique identity and hash, schema/format validity, time bounds, DEMO
  provenance, model-only state, outcome hash linkage, and explicit absence of
  calculated metrics.
- Provider tests prove the exact two-version mapping and order, reject malformed
  or duplicate fixture records, and preserve all evidence fields without
  fallback values.
- Route tests cover exact evidence, explanatory/model-only copy, navigation,
  empty/error boundaries, and absence of accuracy, return, rank, or active
  claims.
- Responsive browser checks cover 1440, 1280, and 390 pixels, keyboard focus,
  local overflow containment, and zero console errors or warnings.

## Deferred work

P3 owns deterministic formulas, trading-calendar horizons, corporate-action
adjustment, return/alpha/target/MFE/MAE calculations, golden tests, status
activation policy, sample confidence, and leaderboard aggregates. A future API
registry, methodology body/document store, lifecycle mutation, or production
provider requires a separate additive contract and is not implied by this P2
surface.

## Local gate

Run from a clean feature branch before integration:

```powershell
docker compose --env-file .env.example config --quiet
pnpm --dir apps/web lint
pnpm --dir apps/web test
pnpm --dir apps/web build
pnpm --dir apps/web exec playwright install --with-deps chromium
pnpm --dir apps/web test:e2e
Set-Location apps/api
.\mvnw.cmd -B -ntp verify
```

Record only commands that were actually executed, their results, the delivered
route, and remaining P2 work in `IMPLEMENTATION_LOG.md`.
