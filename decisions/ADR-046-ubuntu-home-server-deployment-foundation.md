# ADR-046 — Ubuntu Home-Server Deployment Foundation

- Status: Accepted
- Date: 2026-08-26

## Context

ADR-045 proves the packaged Spring API, PostgreSQL, the production Next.js
server, and the browser-visible audit surface on one developer machine. It does
not define an internet-facing topology. The intended first deployment is a
public site for approximately 100 readers on a separate Ubuntu home server,
with no reader login, advertising, subscriptions, donations, or cloud host.

The development computer is not the future server. Repository work must not
install server packages, change its firewall, reserve TCP 80/443, edit Gabia
DNS, or assume its network describes the future server. The public-IP, CGNAT,
dynamic-DNS, CPU, and RAM facts are not yet known.

The repository's root `compose.yaml` and `.env.example` are explicitly local
development surfaces. Reusing them in production would retain a loopback
PostgreSQL host port and weak local fallback password. A Compose override is
also unsafe because sequence merging can preserve a port that an operator
expected to remove.

## Decision

Add an independent deployment boundary under `deploy/home-server/`. Do not
change the root Compose/env files, API POM/configuration, Next configuration,
application routes, migrations, fixtures, or provider code.

ADR-046 is a public **DEMO fixture** deployment foundation only. It has five
services in one Compose model:

1. PostgreSQL 17;
2. the existing Java 21 Spring Boot API;
3. the existing Node.js 24 Next.js production server;
4. production Caddy behind an explicit `production` profile; and
5. private-TLS loopback Caddy behind an explicit `rehearsal` profile.

The API, web, and Caddy Dockerfiles use deny-all Dockerfile-specific context
lists. They build entirely inside Docker and do not modify caller-owned
Maven/Next outputs or `apps/web/next-env.d.ts`. Final processes run as non-root
users. The derived, patch-pinned Caddy image sets `/data` and `/config`
ownership for numeric UID/GID 65532 so newly created certificate volumes are
writable without a root entrypoint. Application roots are read-only with
bounded tmpfs, PID, memory, CPU, log, health, restart, and graceful-stop
policies.

Next.js keeps its existing `next start` runtime rather than changing the
application to standalone output. The image installs locked production pnpm
dependencies separately from build dependencies.

## Network topology

Production uses four Docker networks:

```text
Internet TCP 80/443
        |
production Caddy ---- public-egress (ACME only)
        |
edge-internal
        |
Next.js
        |
app-internal
        |
Spring API
        |
db-internal
        |
PostgreSQL
```

`edge-internal`, `app-internal`, and `db-internal` are Docker-internal
networks. PostgreSQL, Spring, and Next publish no host port. Caddy is not a
member of the API or database network. Next reaches Spring only through the
server-only `API_BASE_URL=http://api:8080`; no `NEXT_PUBLIC_*` variable carries
that origin to the browser.

Only the two operator-selected Caddy ingress profiles join `public-egress`.
They are intended to be run one at a time; Compose profiles are not themselves
a mechanical mutual-exclusion primitive. Next and Spring therefore cannot
reach a public data provider even if an implementation defect attempts a
request. PostgreSQL has only its data network. Initial publication uses TCP
HTTP/1.1 and HTTP/2; UDP 443/HTTP/3 is deliberately absent.

Rehearsal Caddy also joins `edge-internal` and publishes exactly one numeric
`127.0.0.1` HTTPS port. It runs the production Caddyfile at
`https://127.0.0.1:8443` and creates an ephemeral certificate from Caddy's
local CA, allowing production `Secure` cookie behavior to be tested without a
public issuer. Caddy's `default_sni` is set to `127.0.0.1` only for rehearsal
so clients that correctly omit SNI for an IP literal still receive that local
certificate through Docker NAT; production sets it to the validated public
domain. The loopback admin API is used only for container health. Its
standard bridge attachment is required for host-loopback forwarding while the
application networks remain Docker-internal. It is configured with no public
ACME site or account. Production and rehearsal ingress are separate profiles;
the local command selects only rehearsal and preflights a free host port in the
bounded `18080-18179` range.

## Public HTTP boundary

Caddy accepts GET, HEAD, and POST. POST must remain allowed because the locale
switcher is a Next Server Action. Other methods receive 405. Request bodies
are limited to 1 MiB; header and connection timeouts are bounded; compression,
bounded JSON access logs, frame denial, MIME sniffing protection, a restrained
referrer policy, and camera/microphone/geolocation denial are enabled. Caddy
removes its `Server` header and the upstream powered-by header.

HSTS and a blocking CSP are not enabled in this slice. HSTS is appropriate
only after externally observed HTTPS is stable, and a CSP requires a report
and compatibility pass against the production Next output.

## Data and secret boundary

The deployment forces `DATA_MODE=DEMO`, all current web selectors to fixture,
call audit to the internal API, and Spring's market/analyst providers to
fixture. It forces SEC and the operator API off and points the disabled SEC
origin at closed loopback. The operator API must not be enabled here: its
accepted local-only contract binds the whole Spring listener to loopback and
is not remote administration.

PostgreSQL has one initial application/owner credential because the existing
runtime performs Flyway migration and deterministic fixture import at startup.
The value exists only in an absolute server-local file. Compose mounts it as
`/run/secrets/postgres_password` for PostgreSQL and as
`/run/secrets/spring.datasource.password` for Spring. Spring imports the
configuration tree. Web and Caddy receive no database secret.

No API key, stock-provider account, SEC account, domain credential, router
credential, or user-supplied token is required for this decision or local
rehearsal. At real cutover the operator must create the database secret on the
server, not in chat or Git. Enabling SEC later requires a monitored declared
contact email, not an API key. A commercial market provider requires separate
product, license, redistribution, and credential approval.

## Local rehearsal

Add one developer command:

```powershell
pwsh -NoProfile -File ./scripts/verify-home-server-deployment.ps1
```

It requires only PowerShell 7 and a running local Docker Engine with Compose
2.20.0 or newer. It resolves and pins one local Docker endpoint for every
command, removes inherited Docker/Compose/WSR/proxy overrides, creates a random
project, random loopback port, temporary env file, and temporary random
database secret, then:

1. renders the rehearsal Compose profile;
2. builds the API, web, and Caddy runtime images from exact allowlisted
   contexts and proves fresh Caddy data/config volumes are writable as UID
   65532;
3. starts PostgreSQL, Spring, Next, and the shared-config rehearsal Caddy with
   health waits;
4. proves PostgreSQL/API/web have no host port bindings;
5. proves database cardinality remains `3 calls | 2 revisions | 4 outcomes`;
6. renders all 12 primary routes through Caddy;
7. checks DEMO disclosure, API-backed source/timestamp evidence, proxy security
   headers, safe POST passage, and unsupported-method 405;
   and
8. removes only its random project, named volumes, exact random app images,
   and validated temporary directory.

The optional `-RunBrowserSuite` gate additionally runs the existing 72
Playwright checks at 1440, 1280, and 390 pixels against that exact Caddy
endpoint with retries forced to zero. It verifies the production `Secure`
locale cookie, uses an owned output directory, and does not start a second
Next server or interfere with an existing developer port.

The command records the caller's `apps/web/next-env.d.ts` hash and fails if it
changes. It never reads the root `.env`, starts the production profile, binds
80/443, requests a public certificate, contacts ACME/SEC, or changes
DNS/firewall state. Its ephemeral local certificate and CA state are deleted
with the rehearsal tmpfs/container.

## Ubuntu preflight

`deploy/home-server/preflight.sh` is read-only and has three modes. `host` checks
Ubuntu 24.04/26.04, amd64/arm64, CPU, at least 4 GiB RAM, at least 50 GiB free
storage, a pinned local Docker daemon, and Compose 2.20.0+. `contract` additionally
accepts only the exact untracked `.env.production` syntax and key allowlist,
rejects inherited WSR and Compose overrides, checks a non-placeholder lowercase
domain and monitored ACME email, requires the full image SHA to equal a clean
checked-out Git HEAD, verifies the absolute non-symlink mode-600/400 secret file,
and matches DNS A/AAAA exactly to operator-attested global addresses. `publish`
adds first-deployment ownership checks for TCP 80/443.

The initial direct-ingress policy accepts only an operator-confirmed public
IPv4/global-IPv6 path with a static address. `unknown`, stale or mismatched DNS,
CGNAT, and dynamic DNS fail closed. A dynamic DNS client or tunnel would
introduce an external provider and credential and therefore requires a later
decision. The operator writes public addresses on the server; the preflight
does not print them and they need not be sent in chat.

`deploy/home-server/compose-production.sh` removes inherited WSR, Compose,
proxy, Docker, BuildKit, and Buildx overrides, re-resolves and pins the local
Docker endpoint,
accepts only the fixed `build`, `up`, `ps`, `logs`, `stop`, and non-volume-
deleting `down` actions, reruns `contract` validation at the execution boundary,
and then invokes only the production Compose profile with the exact env file.
It forwards no caller-supplied Compose option. The runbook uses this wrapper for
both build and start so preflighted values cannot silently be replaced by the
calling shell or changed between the initial check and mutation.

Passing preflight means `ready for a deployment attempt`, never “publicly
reachable” or “secure.” The script retains an explicit
`PENDING_EXTERNAL_INGRESS` warning. NAT forwarding, ISP filtering, NAT
hairpinning, external IPv6 firewall behavior, ACME issuance, and the final TLS
path can be proved only from outside the home network after deployment.

## CI contract

`scripts/verify-home-server-deployment.py` renders the independent Compose file
with a throwaway `.invalid` identity and temporary file secret, then validates
the exact services, build contexts, profiles, port publications, network
adjacency, internal networks, mount allowlists, health/dependency chain,
hardening, resources, secrets, complete environment allowlists, provider gates,
and image tags. Its negative self-tests reject public backend ports, backend
egress, Caddy-to-API adjacency, live/operator or public-browser overrides,
password environment values, host networking, privilege, false health checks,
arbitrary bind mounts, missing resource limits, and weakened health
dependencies.

The resource envelope is fixed in Compose rather than operator-configurable:
PostgreSQL and Spring each receive 1 GiB/1 CPU, Next receives 768 MiB/1 CPU,
and Caddy receives 256 MiB/0.5 CPU. The guard verifies these exact values.

CI runs that semantic guard plus Bash and PowerShell parse-only checks. CI does
not start production Caddy, contact ACME, use a real domain or secret, mutate a
host, or claim external ingress. The composed image/runtime behavior remains
the explicit local pre-deployment gate.

## Consequences and remaining gates

- A separate Ubuntu home server can later build and run a constrained public
  DEMO stack without exposing Spring or PostgreSQL.
- The deployment is one machine and one instance per service. It is not HA and
  cannot prevent home-link saturation, ISP outage, power loss, or DDoS.
- Docker-published ports can bypass UFW expectations. The primary control is
  structural: only Caddy has production `ports` entries. Router forwarding is
  limited to TCP 80/443; WAN SSH forwarding is outside this decision.
- Version tags do not prove byte-for-byte reproducibility or vulnerability
  absence. Base-image digest recording, scanning, release transfer, and update
  cadence remain release-hardening work.
- A local successful build does not prove both amd64 and arm64 until the chosen
  server architecture is built and exercised.
- A database password and TLS state are persistent, but same-disk persistence
  is not disaster recovery.

ADR-047 must add logical backup, checksum, off-device retention, fresh-target
restore rehearsal, and release rollback before any non-DEMO or irreplaceable
data is published. Real cutover also remains blocked on the exact Ubuntu/CPU/
RAM facts, domain, monitored ACME email, public IPv4/global IPv6 and CGNAT
result, static-vs-dynamic address policy, router forwarding, and external
reachability test. None of those values should be supplied as secrets in chat.
