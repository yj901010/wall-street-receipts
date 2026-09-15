# ADR-090: Offline raw-feed documentation intake

- Date: 2026-09-15
- Status: Accepted for local preparation; no feed approval or raw-data policy
- Base: develop f16cb642e61b5e0377ba3830c2c3993110fda532 (PR #37)

## Context

The Vunelix reference pilot is merged but has no deployment domain. It does not
provide raw ticks or ADR-034 completeness evidence. The remaining raw-window
resolver and MFE/MAE work cannot skip the selected feed's documented sequence,
correction, condition, auction, halt and rights review. No feed has been approved.

## Decision

Add a disconnected Python standard-library CLI that inventories submitted document
references for an explicitly supplied provider/product/primary venue and 15
technical/rights categories drawn from ADR-034. Add a blank no-provider template
and a Korean runbook. This is process preparation, not a canonical methodology,
provider adapter, data fixture, executable raw-coverage resolver or scoring input.

The tool distinguishes malformed documents, missing references and structurally
submitted references ready for human review. It never validates external documents
or grants: all approval/coverage/ingestion/scoring booleans are false even when
every category has references. No reference content, credential or actual market
data is loaded; no URL is fetched. An irrelevant, expired or invented reference
can pass syntax, so manual verification remains mandatory. Private rights evidence
must be handled outside this public-reference inventory, not uploaded to Git.

Closed bounded input rejects unknown fields, duplicate keys/requirements/citations,
credential-bearing URLs and non-local/linked input paths. Output contains no
submitted strings or paths. Its hash identifies exact input bytes, never truth or
authorization. The CLI writes only stdout/stderr, has no runtime consumer and does
not read environment configuration. Exit 0 is not an activation permission.

## Scope and acceptance

Pin the CLI, its functional tests, blank template and runbook with an independent
four-path custody verifier. Integrate the verifier into the existing bridge and
snapshot; do not change any old product, canonical policy, scoring/Vunelix pins or
frozen workflow body. Run real local CLI and unit/negative/size/path-boundary tests,
the complete CI Python suite, current fixture validation, bridge and workflow limits.

No Java, Next route, database, financial arithmetic or deployment behavior changes,
so a newly executed Maven/Web/browser acceptance is not claimed. Keep the user's
next-env.d.ts and all private files unchanged. Commit/push remains separately
authorized. Local preparation is not completion of P3 or actual provider acceptance.

## Next

Use [RAW_FEED_INTAKE.md](../RAW_FEED_INTAKE.md) to collect candidate references, then
obtain the exact product/protocol/rights review and user approval required by
ADR-034. Do not infer approval from a filled checklist, a widget's free-embed claim,
a successful process exit or supplied high/low aggregates. Actual raw coverage,
MFE/MAE, alpha and canonical lifecycle retain their existing separate boundaries.
