# ADR-043 — Default-Disabled Local Single-Operator SEC Attempt API

- Status: Accepted
- Date: 2026-08-26

ADR-043 exposes the immutable ADR-042 collection-attempt ledger through one
default-disabled, single-operator HTTP boundary for local verification. It does
not approve remote deployment, a public operator surface, a browser login, or
live SEC traffic.

## Context

ADR-042 deliberately stopped at an internal application service. It added no
controller, authentication boundary, CLI, scheduler, or browser consumer. A
developer therefore cannot exercise the complete HTTP authorization, command,
idempotent replay, or status-reconstruction path before choosing a production
identity and deployment topology.

The product's read-only community pages are intended to remain publicly
viewable. SEC capture commands are different: an anonymous caller could spend
the shared fair-access budget, create durable operational records, or leave a
provider dispatch indeterminate. A domain name alone does not authenticate an
operator, provide TLS, protect the API origin, or create durable actor audit.

The immediate requirement is narrower than deployment. One developer wants to
test locally, with one API JVM and PostgreSQL, before choosing the production
hosting and managed identity boundary. SEC does not issue an API key for this
data. This local transport instead needs one application-owned high-entropy
Bearer secret, while `SEC_PROVIDER_ENABLED=false` keeps all ADR-043 verification
disconnected from SEC.

## Decision

### Local-only security boundary

The operator API is absent unless the server-only setting below is exactly
enabled:

```dotenv
OPERATOR_API_ENABLED=true
OPERATOR_API_TOKEN_SHA256=<lowercase SHA-256 of one random local Bearer token>
```

`OPERATOR_API_ENABLED` defaults to `false`. When it is false, the three operator
paths are not registered and ordinary route handling returns `404`; there is no
disabled-feature oracle. Enabling the boundary with a missing, malformed, or
all-zero digest fails closed during application startup. The configured value
is exactly 64 lowercase hexadecimal characters and is the SHA-256 digest of
the token, never the raw token.

When enabled, every operator request requires exactly one opaque token in the
HTTP `Authorization: Bearer ...` header. The application hashes the presented
token and uses constant-time byte comparison against the configured digest.
Missing, malformed, and incorrect credentials are intentionally
indistinguishable. The filter authenticates exactly one local authority; it
does not claim a human identity, organization, role hierarchy, MFA session, or
durable actor audit.

Spring Security's strict HTTP firewall remains enabled. Requests it rejects
before MVC receive a sanitized `400 INVALID_QUERY` problem with the constant
instance `/invalid-request`; rejected URI, query, exception, and credential
content are never reflected. Rejections under the operator prefix are also
non-cacheable.

This boundary is approved only when the API is reachable from the same
developer machine, such as a loopback-bound process or an equivalently isolated
local development network. When enabled, V1 programmatically forces the whole
embedded API server to `InetAddress.getLoopbackAddress()` before startup,
overriding any broader configured bind address. This protects the operator
route and the otherwise public application/actuator routes together during the
local run. The raw token must not cross plaintext HTTP over a LAN or the
Internet. There is no cookie, browser storage, query-parameter
credential, CORS expansion, public Next.js consumer, login page, or token
management endpoint.

### Exact HTTP surface

The only routes are:

```text
POST /internal/v1/sec/collection-attempts/root
POST /internal/v1/sec/collection-attempts/exact-root
GET  /internal/v1/sec/collection-attempts/{attemptId}
```

The root command body contains exactly `operatorRequestId` and `cik`. The
exact-root command body contains exactly `operatorRequestId`, `rootCaptureId`,
and `descriptorActions`. Each descriptor action contains exactly
`descriptorOrdinal`, `actionKind`, and the conditionally required
`selectedSegmentCaptureId`: `SELECT_EXACT` requires the exact lowercase
SHA-256 capture ID, while `CAPTURE_NOW` forbids it. An empty action array is the
explicit ADR-042 root-only, zero-network collection. Unknown, duplicate,
command-kind, URL, host, filename, query, retry, or scheduling fields are not
accepted.

The path fixes the command kind. Both POST routes delegate once to the existing
ADR-042 service and preserve its canonical nonzero lowercase UUID,
normalization, descriptor ordering, at-most-one `CAPTURE_NOW`, one-provider-
invocation, replay, dispatch, terminal, and atomic-commit rules. The controller
does not calculate an attempt identity independently, select latest evidence,
loop over descriptors, retry, resume, cancel, abandon, resolve, or repair.

The GET path accepts one lowercase 64-hex `attemptId` and performs only an exact
ledger lookup. There is no list, search, latest-by-CIK, lookup-by-operator-UUID,
mutation, retry, or recovery route. In particular,
`PROVIDER_DISPATCHED_INDETERMINATE` remains an observation, never an instruction
to contact SEC again.

### Response projection

Successful command and status responses use one explicit immutable allowlist:

```text
schemaVersion
provider
product
policyVersion
attemptId
commandSha256
operatorRequestId
commandKind
cik
rootCaptureId
descriptorActions[] {
  descriptorOrdinal
  actionKind
  selectedSegmentCaptureId
}
requestedAt
maxProviderInvocations
lifecycleState
attemptIndeterminate
providerStartOrResponseUnknown
automaticRetryAllowed
providerDispatch {
  operation
  descriptorOrdinal
  dispatchedAt
}
terminalOutcome {
  status
  stage
  requestDisposition
  failureCode
  providerHttpStatus
  rootArtifact { artifactId, status }
  segmentArtifact { artifactId, status }
  manifestArtifact { artifactId, status }
  completedAt
}
```

Nullable facts remain JSON `null`; they are not omitted, replaced with zero, or
inferred. The representation contains no raw provider body, decoded filing
payload, arbitrary header, exception text, database detail, Bearer token or
digest, User-Agent, contact email, actor claim, or replay flag. A client learns
replay convergence from the same immutable attempt identity and facts, not from
a claim that another SEC request occurred.

`attemptIndeterminate` is true exactly for
`PROVIDER_DISPATCHED_INDETERMINATE`. `providerStartOrResponseUnknown` is true
for that lifecycle or a terminal whose disposition is
`PROVIDER_START_OR_RESPONSE_UNKNOWN`. `automaticRetryAllowed` is always false
in V1. These redundant safety fields make the dangerous interpretation explicit
without changing the underlying ADR-042 facts or granting a retry command.

Every `200` operator representation sends `Cache-Control: no-store`. Each POST
also returns `Location` pointing to the exact GET path for its returned
`attemptId`.

### Closed HTTP status semantics

The local boundary maps outcomes as follows:

| Condition | HTTP result | Meaning |
| --- | --- | --- |
| New valid POST or canonically equivalent replay | `200` plus `Location` | Returns the durable representation; `200` does not assert provider or business success. |
| Exact GET finds an attempt | `200` | Returns only currently durable immutable facts. |
| Feature disabled | ordinary `404` | The operator routes are absent. |
| Missing, malformed, or incorrect Bearer credential | `401` plus `WWW-Authenticate: Bearer` and `OPERATOR_AUTHENTICATION_REQUIRED` | Credentials are not distinguished. |
| Authenticated principal lacks the required authority | `403` and `OPERATOR_ACCESS_DENIED` | Defensive closed mapping; V1 issues only the one local operator authority. |
| Malformed JSON, unknown or conflicting fields, or invalid identifier/action shape | `400` and `INVALID_OPERATOR_COMMAND` | No SEC request is made. |
| GET uses a valid but unknown `attemptId` | `404` and `SEC_COLLECTION_ATTEMPT_NOT_FOUND` | No latest or substitute attempt is selected. |
| Existing `operatorRequestId` is reused for a different canonical command | `409` and `OPERATOR_REQUEST_CONFLICT` | Existing intent is immutable and no provider request is made. |
| Initial exact root/segment FK admission is missing or incompatible | `422` and `EXACT_EVIDENCE_NOT_ADMITTED` | The claim transaction rolls back; no attempt, dispatch, terminal, or provider request exists. |
| Admitted evidence later fails exact reconstruction or compatibility | `200` durable `EXACT_EVIDENCE_VALIDATION_FAILED` terminal | The attempt exists and no provider request is made. |
| Provider gate/failure, provider-disabled result, or indeterminate dispatch | `200` durable attempt representation | The lifecycle and terminal fields carry truth; transport status does not rewrite evidence. |

Problem details are sanitized, request-correlated, non-cacheable, and never
echo credentials or arbitrary exception text. Unexpected server failures use
the existing generic `500` boundary. They are not permission for a new
`operatorRequestId`; a caller may repeat only the exact same command with the
same UUID and must inspect the returned or subsequently readable immutable
state.

### Offline local verification and one-replica rule

ADR-043 verification keeps these settings together:

```dotenv
OPERATOR_API_ENABLED=true
OPERATOR_API_TOKEN_SHA256=<local digest>
SEC_PROVIDER_ENABLED=false
```

No SEC API key, SEC account, paid plan, domain, DNS change, Cloudflare account,
OAuth client, or `SEC_CONTACT_EMAIL` is needed for this offline local boundary.
With the provider disabled, a root or `CAPTURE_NOW` command can close with the
durable provider-gate failure defined by ADR-042 but cannot make an SEC network
request. Selection-only execution remains zero-network and succeeds only when
its exact durable evidence already exists.

Both local testing and any future provider-enabled operation are restricted to
exactly one API JVM/container/replica. ADR-036 and ADR-042 rate limiting,
cooldown, and mutex state are process-local. Starting a second provider-enabled
process would bypass aggregate coordination and is prohibited. Restarting the
one process does not turn an indeterminate dispatch into retry permission.

Default Maven tests, `verify`, and CI leave both operator and SEC live gates
closed and must not contact SEC. Focused HTTP tests use local fakes or the
disabled-provider branch, deterministic token digests, injected clocks, and a
local test database. A manual live smoke remains the separate double-opt-in
ADR-036 procedure and is not part of ADR-043 verification.

### Secret handling

The raw Bearer token must be the canonical standard-Base64 encoding of exactly
32 random bytes (44 characters ending in `=`). It is generated locally,
retained only in the invoking shell or a password manager, and never sent in
chat, committed, placed in `.env`, passed as a query parameter, stored in
browser storage, or printed in application or CI logs. Only its SHA-256 digest
may be placed in the gitignored root `.env` for local startup. The digest is
still server-only configuration and must use a deployment secret store if a
future ADR permits remote use.

`OPERATOR_API_TOKEN_SHA256` is not an SEC credential and must not be reused as
`POSTGRES_PASSWORD`, an OAuth secret, or any other token. Rotation creates a new
random token and replaces its digest; V1 has no overlap window or remote
rotation endpoint. Authentication failures and successful requests log neither
the presented token nor its digest.

## Deployment prerequisites

ADR-043 explicitly prohibits enabling this opaque-token boundary in a deployed
or remotely reachable environment. Before remote operator execution, a later
ADR must choose and verify all of the following:

- HTTPS/TLS for every browser, proxy, and API hop, with the API origin private
  or otherwise unable to bypass the identity proxy;
- managed identity such as Cloudflare Access/OIDC, an exact issuer and audience,
  MFA/allowlist policy, session and revocation behavior, and no trust in
  unsigned forwarded identity headers;
- durable append-only actor audit containing a stable provider subject,
  authorization decision, action, operator-request UUID, command digest,
  attempt ID, request ID, and coarse result without secrets or raw payloads;
- CSRF and exact-origin protection if a cookie-authenticated browser or Next.js
  BFF is introduced;
- private actuator/management endpoints, deployment secret storage, token or
  client-credential rotation, ingress limits, and sanitized security logs; and
- exactly one live API replica until a reviewed distributed SEC limiter,
  cooldown, and mutual-exclusion owner protects every caller.

The existing domain can be used in that future design, but this local static
token does not become a production credential by being copied to a host.

## Consequences

- The developer can verify authentication, exact POST execution, idempotent
  replay, conflict handling, and immutable GET reconstruction entirely locally.
- The public community site remains unchanged and login-free.
- Enabling the operator API does not enable SEC or create background work.
- The transport preserves ADR-042 evidence semantics and exposes indeterminate
  state without offering a dangerous retry control.
- There is intentionally no per-human attribution, multi-operator management,
  remote deployment approval, web UI, OpenAPI publication, or live SEC test in
  this phase.
