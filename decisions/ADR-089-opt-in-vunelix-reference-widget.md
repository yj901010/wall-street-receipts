# ADR-089: Opt-in Vunelix reference-display pilot

- Date: 2026-09-15
- Status: Accepted for local implementation and mocked acceptance only
- Base: develop merge 3742e23 (ADR-088)

## Context

The user selected Vunelix for a possible free public reference-price surface but
has no deployment domain. Actual US source feed, latency and distribution rights
have not been independently established. This cannot replace ADR-034 raw-trade
coverage or the reproducible DEMO scoring pipeline.

## Decision

Add one separate `/market/reference/AAPL` route and client island. Server opt-in
and a separate visitor click are both required. Default/invalid configuration
causes no vendor request. No current market page/navigation, financial domain,
API, database, dependency or fixture is changed. Label the display external and
unverified; keep source, identifier, timestamp limitations and usage boundaries
visible. Do not infer a price or market timestamp from a page/module load.

Use the documented native component and fixed public module URL, not a guessed
iframe endpoint. This runs third-party code in the page; Shadow DOM does not
provide security isolation. Version-query pinning does not freeze remote bytes.
Only full-document navigation is offered after opt-in. A 15-second wait bounds
wrapper status, not vendor execution or quote freshness. A sensitive origin needs
separate-origin design/security review before activation.

The provider owns its internal price/timezone display; we do not rewrite it or
ingest its values. This explicitly differs from first-party KST evidence views.
Wrapper messages support KO/EN; provider widget language is English. An empty or
failed component never becomes a DEMO price, zero, LIVE state or scoring input.

## Acceptance and consequences

Use unit tests, normal default-off public E2E and a separate production browser
suite at three viewports with intercepted transport-only modules. Real vendor
code is not executed in automation. Add independent exact-path/source custody;
do not expand the 118 scoring pins or loosen frozen workflow/product boundaries.
Preserve the user's Next declaration and private environment through a clean
source mirror. Public production activation remains a separate user decision.

Follow [the pilot runbook](../VUNELIX_REFERENCE_WIDGET.md) for domain registration,
public-display rights, privacy/security, source/delay checks and actual market-hour
acceptance. A free embed claim is not proof of a free raw data license or complete
SIP coverage. This phase does not finish P3, select a raw provider, publish a site,
register an account/domain, create a PR or merge. Source commit/push requires
separate user approval; it does not authorize public widget activation.
