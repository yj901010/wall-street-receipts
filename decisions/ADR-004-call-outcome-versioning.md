# ADR-004 — Call Outcome Versioning and Audit Lineage

- Status: Accepted
- Date: 2026-08-18

## Context

Call outcomes are derived facts whose inputs and methodology can change after an initial evaluation. The product must remain reproducible without presenting unavailable market data as a calculated result. The P1 roadmap requires the canonical outcome model and fixture boundary, while P3 owns scoring behavior and golden calculation tests.

## Decision

### Phase boundary

P1 defines and validates the canonical `ScoringMethodology` and `CallOutcome` records, their model-only fixtures, and append-only audit lineage. P1 does not calculate returns, alpha, target hit, directional win, MFE, MAE, or target error.

P3 implements deterministic calculations, trading-calendar-aware horizons, corporate-action treatment, completeness rules, and golden tests. LLMs never calculate outcome metrics.

### Append-only outcome lineage

An outcome lineage is scoped by `(callId, basisRevisionId, horizon, methodologyId, methodologyVersion)`.

- Sequence numbering starts at `1` with `supersedesOutcomeId = null`.
- Every later record increments `sequenceNumber` and references the immediately preceding outcome.
- A changed input set appends a new outcome with a new `inputFingerprint`; it never updates an earlier record.
- A new methodology version starts an independent sequence at `1` and carries its own `methodologyDefinitionHash`.
- Existing call list/detail response shapes remain closed. Outcome history is exposed only through an additive, read-only audit endpoint, `GET /v1/calls/{id}/outcomes`; no outcome mutation endpoint is introduced.

The application persistence boundary exposes insert-if-absent and read
operations only. Flyway constraints protect inserted identities, references,
states, and lineage, while privileged direct SQL remains an administrative trust
boundary shared with the existing revision and snapshot models. Before any
external writer receives database access, its role must be insert/select-only or
PostgreSQL-specific update/delete guards must be added.

`basisRevisionId = null` means the immutable original analyst-call event is the
scoring basis. A non-null value identifies a same-call `CORRECTION` whose
replacement forecast terms form the basis; a cancellation is never itself a
forecast basis.

`cancellationRevisionId` is separate audit evidence. It is required only for an
`EXCLUDED/CALL_CANCELLED` outcome and must identify a same-call `CANCELLATION`
that was captured by the outcome's processing time. Other outcome states require
it to be `null`. P3 still owns the policy decision about whether a particular
cancellation should produce an excluded outcome; P1 only prevents an exclusion
record from claiming a cancellation without evidence.

### Units and fingerprints

All return, alpha, MFE, MAE, and target-error numbers use decimal ratio units:
`0.05` means five percent. Storage and deterministic calculations use decimal
arithmetic rather than binary floating point, with a canonical maximum precision
of 38 digits and scale of 12. Values that require silent database rounding are
rejected at the domain boundary. `targetError` cannot be negative.

`eventTime` is the domain time at which the recorded evaluation state applies;
`processingTime` is when that state was produced, and `capturedAt` is when it was
persisted as evidence. P3 will define the trading-calendar-aware horizon
observation and retry schedule without changing these three timestamp meanings.
Persistent instants are canonical UTC values with at most microsecond precision,
matching PostgreSQL `TIMESTAMP(6) WITH TIME ZONE`; finer input is rejected rather
than silently rounded and later misidentified during replay.

`definitionHash`, `methodologyDefinitionHash`, and `inputFingerprint` are lowercase SHA-256 hexadecimal strings. The methodology hash identifies the canonical methodology definition, while the input fingerprint identifies the exact point-in-time inputs used by one evaluation attempt.

### Missing data and evaluation state

Every metric key is present but nullable. Missing inputs never produce invented numeric values, zeroes, or boolean results.

- `CALCULATED` requires `dataComplete = true` and `reasonCode = null`.
- `PENDING` requires `dataComplete = false` and `HORIZON_NOT_REACHED`.
- `INCOMPLETE` requires `dataComplete = false` and `HORIZON_DATA_MISSING`.
- `EXCLUDED` requires `dataComplete = false`, `CALL_CANCELLED`, and all metrics set to `null`.

The P1 fixtures contain only `PENDING` and `INCOMPLETE` model records with null metrics. They are contract examples, not scoring claims.

## Consequences

- Historical evaluations remain reproducible across changed inputs and methodology versions.
- Consumers can audit why an outcome is pending, incomplete, or excluded without treating missing values as results.
- P1 can establish contracts and persistence boundaries without prematurely encoding P3 scoring policy.
- Storage and API implementations must validate lineage across records because a single-record JSON Schema cannot prove that a parent is the immediately preceding record in the same lineage.
