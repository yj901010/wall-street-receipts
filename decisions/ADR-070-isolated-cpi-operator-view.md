# ADR-070: Isolated local CPI operator presentation

Date: 2026-09-09 (KST)

## Scope and starting point

PR #17 was user-merged into develop at `821e4071a8de06eaa1f83f7216e20d5d65e66979`.
Its head `3ef22a8` passed all four jobs in PR CI #46 (`34300798172`). Branch
`feature/p5-cpi-operator-view` starts there. This slice adds a Korean operator
presentation of ADR-069, not a public activity feed, account system, collector,
provider integration, heartbeat, retention policy or deployment.

No new provider key or home server is needed to implement or run synthetic tests.
Do not enable the actual operator API, edit `.env`, create a real token, migrate
existing DBs or start a real collector. The development PC is not the home server.

## Separate listener and route boundary

Keep the existing Next.js/TypeScript app and its shared KST component. A new
dynamic `/operator/cpi` route renders only an initial credential-free form;
there is no initial protected data until the operator explicitly submits it.
The client island is required for the password input, manual query and clearing.

An operator-only `src/proxy.ts` matcher denies before SSR streams headers; a
page-level notFound can otherwise become a soft 404 with HTTP 200. It uses the
same process-local marker and leaves all non-operator routes outside its matcher.
Ordinary `next dev` / `next start` return 404 for the page, even if an operator
environment setting or forged header is supplied. There is no Next route handler
for `/operator/cpi/query`. No public navigation entry or server-side token is added.
The page requires a process-local symbol that only the dedicated launcher sets
after its listener has bound to literal IPv4 `127.0.0.1`. It is not an environment
flag that could accidentally enable the screen on an ordinary public listener.

`apps/web/operator/start.mjs` runs an existing production Next build with a custom
Node listener. Unlike the public Next router, this listener can enforce the actual
socket address and a closed request path set before Next executes. It accepts only
the exact page and static build assets, plus the two read-only bridge routes:

```text
GET /operator/cpi/query
GET /operator/cpi/query/{canonical-lowercase-UUID}
```

It refuses arbitrary query parameters, origins, Host aliases, cross-site fetches,
request bodies, methods, paths and duplicate bearer headers. Host must exactly
match `127.0.0.1:<listener-port>`; `localhost`, forwarded identity and reverse
proxy headers do not grant access. It strips cookies, credentials and Next routing
headers before page rendering. There is no WebSocket upgrade or proxy to SEC.

The bridge uses only `GET http://127.0.0.1:<explicit-api-port>/internal/v1/cpi/collection-attempts`
or its exact UUID child. It forwards only Accept and the manually supplied bearer.
It never reads API_BASE_URL, an ambient provider key or a server-stored token.
Redirects are errors, responses are capped at 64 KiB of decoded bytes, the complete
upstream read has a five-second deadline, and disconnect aborts it. One active
read and a one-second process-wide start interval return local 429 / Retry-After: 1
without a queue or retry. This is a local bridge bound, not a distributed API limit.
Non-200 bodies and headers are discarded; invalid evidence is a fixed 503.

No CORS or sessions are introduced. A custom same-origin request header is required
for queries. Responses use no-store; rendering uses a fresh nonce CSP, self-only
connections, no framing/referrer and no form navigation. The CSP also prevents a
pre-hydration/native GET form from putting a password in a URL. Static Next build
assets may retain Next's cache headers; they contain no credentials or attempt data.

## Credential and evidence handling

Use the existing ADR-043 opaque 32-byte Base64 bearer and digest mechanism, but
never provision a real credential in this implementation. The form uses a password
field, disables autocomplete, clears it at submission and requires re-entry for
each query. The only temporary retention is in the in-flight request closure.
No token in URL, cookies, localStorage, sessionStorage, SSR, logs or saved results.
Reset, navigation/pagehide and unmount abort requests; reset/pagehide clear the form
and results. Late responses cannot restore a cleared view. No polling or automatic
retry. Browser password managers/extensions and developer tools remain outside this
application's control; do not enable recording/tracing with real credentials.

The closed adapter checks exact fields/metadata, six required limitations, KST
calendar validity, microsecond ordering, uniqueness, limit 20, hasMore consistency,
exact UUID identity, terminal shapes, gate consistency and chronology. It never
sorts, repairs or creates evidence. The bridge and browser both validate.

The shared KstTimestamp renders all visible instants, including the API observation
marker and retry lower bound, preserving fractional precision. UTC DB storage is
unchanged. UNKNOWN is not RUNNING or FAILED. SAVED is a stored receipt reference,
not a replay verification. Gate permission does not prove HTTP delivery. Retry
lower bound does not promise the next run. The observation marker is not DB commit
time or a historical snapshot. UNVERIFIED/PERSISTED_ATTEMPT_RECORDS never becomes
observed provider data merely because the record is stored in PostgreSQL.

Empty, initial, loading, error, exact selection and more-records states are distinct.
New queries and failures remove old results. Missing UUID and a disabled upstream
API share an honest 404 explanation. Mobile tables scroll within a focusable,
labelled region without document overflow; inputs/buttons have visible focus.

## Future activation (not performed)

Ask the user for the intended separate operator API process/port and protected
credential handling first. ADR-069's shared API switch also enables pre-existing
SEC mutation routes and forces the entire API process to loopback; do not reuse or
reconfigure a public API process casually. The UI proxy does not grant access to
those SEC routes, but the bearer itself is not CPI-only.

After a separately approved operator API is running and a production Web build
exists, set only `CPI_OPERATOR_UI_PORT` and `CPI_OPERATOR_API_PORT` in the process
environment (both 1024–65535, different), then from `apps/web` run:

```text
node operator/start.mjs
```

Open the printed literal 127.0.0.1 URL. Do not expose it with port forwarding,
reverse proxying or a public domain. This local single-operator model is not a
remote admin authentication system. The launcher reads its ports before Next's
standard app environment loading; it does not consume provider secrets or activate
any backend. Existing public launch commands and deployment configuration stay intact.

## Tests and custody

- Vitest: adapter, real Node HTTP boundary, body/error sanitation, duplicate bearer,
  host/origin/path/verb guards, concurrency, public default denial and React states.
- Normal Playwright suite includes public page/bridge 404 checks at three widths.
- Separate offline production-browser rehearsal: build Web, then from `apps/web`
  run `node node_modules/@playwright/test/cli.js test --config operator/playwright.config.ts`.
  It owns a temporary DEMO HTTP API and separate UI on 3470; a pre-existing listener
  must not be reused. It needs no Docker, live API, provider key or DB. Its fixture
  helper is not imported by the production launcher. The fixed synthetic bearer
  must never be configured on a real API. Trace/video are off; explicit screenshots
  are only of DEMO records after credential clearing. Child/server lifecycle closes
  on completion. This rehearsal is not proof of a live Spring/DB/browser deployment.
- The separate production operator rehearsal is explicit/manual, not automatically
  part of the historical CI workflow. Current unit/default-route tests run in
  ordinary Web CI. Do not claim new hosted CI before the candidate PR runs.
- CPI custody adds exactly 18 source/test/tool paths (76 total, nine baseline
  replacements and 67 additions). No existing product file or predecessor exception
  changes. Historical workflow/bodies stay pinned. Results are in IMPLEMENTATION_LOG.

Next: review/commit/PR/CI; then an explicitly approved disposable full-stack
operator rehearsal against Spring/PostgreSQL, before any real activation.

Reference: [Next.js custom server guidance](https://nextjs.org/docs/app/guides/custom-server).
The custom listener is limited to the operator process; no standalone-output or
public-server optimization change is proposed.
