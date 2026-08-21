# ADR-008 — Event/Session Relation Without Anchor Selection

- Status: Accepted
- Date: 2026-08-20

## Context

ADR-007 proves next-N mechanics only after a caller supplies an anchor session.
The repository does not yet define how an analyst-call or correction event maps
to that anchor. It contains no venue/calendar association on an asset, no rule
for pre-open, intraday, exact-close, after-close, or gap events, and no decision
about whether corrected forecast terms retain the original call time or use the
correction event time.

Selecting an anchor now would silently create scoring methodology. A smaller
deterministic fact is available: classify one caller-supplied UTC event instant
against one explicit ordered session catalog while preserving every boundary
and gap without choosing an anchor.

## Decision

The third P3 slice implements only a pure event/session relation classifier.

- `EventSessionRelationPolicyVersion` contains exactly
  `EXPLICIT_SESSION_BOUNDARY_RELATION_V1`. This code-only version identifies the
  closed interval-classification behavior below; it is not a scoring
  methodology and is not attached to an existing `MODEL_ONLY` hash.
- `EventSessionRelationRequest` contains exactly `policyVersion`, `eventTime`,
  and `catalog`. The event instant is supplied by the caller and has at most
  microsecond precision. The request contains no call, analyst-call revision,
  basis selection, anchor, horizon, session count, evaluation as-of,
  observation, price, provenance, capture time, provider, or clock.
- `EventSessionRelation` nests exact
  `RelationContext(policyVersion, calendarId, catalogRevision, eventTime)` and
  these variants:
  - `EmptyCatalog(context)` when the explicit list has no entries;
  - `BeforeCatalog(context, firstSession)` when event time precedes the first
    supplied open;
  - `AtOpen(context, session)` when event time equals a session open;
  - `InsideSession(context, session)` when open < event time < close;
  - `AtClose(context, session)` when event time equals a session close;
  - `AtTouchingBoundary(context, closingSession, openingSession)` when one
    session closes exactly as the next opens and event time equals both;
  - `BetweenSessions(context, previousSession, nextSession)` when previous close
    < event time < next open; and
  - `AfterCatalog(context, lastSession)` when event time follows the last close.
- Touching boundaries are never forced into open or close precedence. Exact
  open, strict interior, exact close, strict gaps, and out-of-coverage states
  remain distinguishable. The classifier returns no anchor, recommendation,
  horizon endpoint, readiness, or outcome state.
- Public relation-record constructors reject only the temporal inequalities and
  null/identity contradictions decidable from their own components. Catalog
  membership, first/last position, adjacency, and touching-boundary precedence
  are guarantees of `EventSessionRelationClassifier`, not facts attested by
  direct record construction. Neither construction path attests catalog
  provenance or authorizes runtime use.
- The supplied catalog order and intervals are authoritative. A Friday-to-
  Monday gap is only `BetweenSessions`; it is not inferred or labelled as a
  weekend or holiday. Explicit Saturday and early-close sessions are ordinary
  supplied intervals. No session is sorted, inserted, removed, or derived.
- The classifier is a deterministic domain leaf. It uses no `Clock`, locale,
  default timezone, local date, weekday, duration/calendar arithmetic, call or
  revision aggregate, provider, network, JSON, Spring, persistence, controller,
  scheduler, or LLM. Source-local Java schedules are tests, not canonical
  calendar or market facts.

The caller remains responsible for choosing whether `eventTime` is an original
call time, correction time, or another already-reviewed basis. This slice does
not import either call type and cannot validate that choice.

## Point-in-time and version boundary

`TradingSessionCatalog` currently carries only a caller label and revision. It
does not contain a publication/capture time, source reference, provenance, or
license evidence. Therefore a relation result proves only mechanics against the
supplied schedule; it does not prove that catalog revision was knowable at the
event or evaluation time.

Before a relation can feed an anchor or outcome, a canonical methodology must
lock original-versus-correction basis time, relation-to-anchor mapping, calendar
selection, revision point-in-time eligibility, emergency closure treatment,
named horizon mapping, and canonical definition serialization/hash. The exact
calendar revision and resolved sessions then belong in the later input
fingerprint. Existing `MODEL_ONLY` hashes are not reinterpreted.

## Consequences

- Pre-open, exact-open, intraday, exact-close, after-close, touching-boundary,
  and gap mechanics can be golden-tested without selecting a scoring anchor.
- The classifier composes with the explicit catalog model but does not call the
  session-offset resolver or weaken its caller-selected-anchor boundary.
- The current schemas, fixtures, manifest, OpenAPI, Flyway migrations, database,
  API behavior, providers, web routes, methodologies, and outcomes remain
  unchanged.
- Anchor selection, original/correction policy, named horizons, observations,
  target-hit orchestration, and outcome publication remain later P3 work.
