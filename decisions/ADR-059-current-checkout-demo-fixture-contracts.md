# ADR-059: Current-checkout DEMO fixture contracts

- Status: Accepted for this CI-only migration slice
- Date: 2026-09-03 (KST)
- Prerequisites: ADR-057 and ADR-058

## Context

PR #7 merged into `develop` at `d625f18` after all four CI jobs passed;
the merged `develop` run also passed. ADR-057 restored executable hosted CI
by retaining the old contracts in an isolated historical checkout. That bridge
intentionally refuses unrelated product-tree changes. Passing historical guards
alone must not be described as validating a future product implementation.

The analyst-call revision and outcome checks (historical steps 84 and 85)
read fixture documents and schemas without relying on Git projections or
workflow source text. They are the first suitable contracts to move to an
independently testable current-source implementation.

## Decision

Add a mandatory `validate_current_fixtures.py` invocation before historical
checkout preparation. Its root is the checkout containing that script, never
the current working directory, a CI environment variable, a Git revision, or
an inferred fallback. The importable validators accept an explicit root so tests
can mutate disposable fixture copies without changing repository evidence.

The module boundaries are:

- `fixture_contracts_common.py`: bounded strict JSON, local schema loading,
  exact decimal validation, and canonical UTC instant parsing.
- `fixture_revisions.py`: manifest membership, source/provider identities,
  DEMO provenance, correction/cancellation terms, event ordering, and contiguous
  append-only revision chains.
- `fixture_outcomes.py`: closed schemas, version/hash evidence, point-in-time
  references, outcome lineage, and the existing model-only/no-calculated-metric
  boundary.
- `validate_current_fixtures.py`: read-only orchestration and a sanitized CLI
  result containing only DEMO status and validated record counts.

Preserve all substantive checks from steps 84 and 85. Strengthen ambiguous-input
handling: reject duplicate JSON keys and referenced identities instead of silently
overwriting them, non-finite numbers, external schema references, and linked or
missing input files. Accept only JSON documents directly under `schemas/` or
`fixtures/v1/`; never read `.env`. Input is bounded at 2 MiB per document and
1,000 decimal coefficient digits/exponent magnitude. These are CI parser resource
limits, not provider limits or a change to the product's `NUMERIC(38,12)` policy.

Parse JSON fractions as `Decimal`, not `float`. Check `multipleOf` by integer
coefficient ratios, independent of the process's Decimal precision. This avoids
rounding a 38-digit valid boundary or admitting a 13th decimal place. No financial
metric is calculated by these validators. Stored timestamps remain strict UTC Z
with at most microsecond precision; the product's KST display policy is unchanged.

## Compatibility and limits

Keep the pinned baseline, extracted manifest, all 84 historical run bodies and
their order, all seven restorations, and source custody checks unchanged. The new
gate supplements those historical checks; it does not delete or silently replace
them. Add only exact CI module/test paths and this ADR to the CI path set.
No fixture, schema, application, provider, migration, or API path is exempted.

This is the first current-tree contract migration, **not completion of the frozen
bridge's retirement**. Many historical checks overlap these documents. Future
product changes still require explicit migration of all affected contracts with
negative tests. Do not treat this gate as permission to publish metrics, make a
methodology ACTIVE, change a DEMO record into observed data, or allow arbitrary
fixture changes.

## Verification

Tests operate on disposable copies or in-memory values. They cover normal
evidence, malformed and duplicate identities, broken revision/outcome lineage,
future evidence, missing provenance, schema weakening, invented zero/Boolean
metrics, high-precision decimal boundaries, UTC precision, and external-reference
rejection. A real subprocess runs a copied CLI from an unrelated directory with
misleading CI environment paths and no Git repository; corrupting its current
fixture must fail, not fall back to the baseline. Workflow mutation tests reject
removal, skipping, root redirection, and `continue-on-error` for the new gate.

Run `python scripts/ci/validate_current_fixtures.py`, the complete CI Python
suite, the size guard, and `run_contracts.py validate`. Record actual results in
`IMPLEMENTATION_LOG.md`. Hosted CI and all 84 historical executions are separate
evidence; a local current-fixture pass is not a claim that those ran.

## External inputs and next work

No additional API key, paid service, provider request, home-server fact, domain,
or deployment permission is required. Actual home-server deployment stays deferred.
No runtime route or UI changes in this slice.

A useful subsequent user-visible slice is to expose the already implemented
exact SEC evidence-audit page through the Korean/English navigation, after moving
its affected navigation contracts to current-source verification. It must retain
the exact-identity audit and DEMO disclosures, not imply live SEC collection.
