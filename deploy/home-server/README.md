# Ubuntu home-server deployment foundation

This directory is the ADR-046 deployment boundary for the public **DEMO**
site. It is independent from the root development `compose.yaml` and never
loads the ignored root `.env`.

## What is ready now

- PostgreSQL 17, the Java 21 API, the Node.js 24 web server, and Caddy have
  separate images and health checks. The small derived Caddy image pre-owns
  `/data` and `/config` for its numeric non-root user so fresh certificate
  volumes are writable without a root startup process.
- Only production Caddy can publish TCP 80 and 443. Web, API, and PostgreSQL
  publish no host ports.
- The application path is Caddy to web, web to API, and API to PostgreSQL;
  Caddy is not a member of the API or database network. All application and
  data networks are `internal: true`.
- Web and API have no internet egress. SEC, the operator API, and all live
  providers remain off. Public pages are explicitly DEMO/fixture-backed.
- PostgreSQL receives its password as a Compose file secret. Spring reads the
  same file through a configuration tree; the value is not placed in Compose
  environment variables or source control.
- A rehearsal profile preflights one available numeric-loopback HTTPS port in
  `18080-18179`. It exercises the same Caddyfile with an ephemeral Caddy local
  CA certificate for `127.0.0.1`, so production `Secure` cookies are tested. It
  never contacts public ACME, starts production ingress, or binds 80/443.

This is not evidence of public reachability, successful ACME issuance, high
availability, DDoS protection, or disaster recovery.

## What you need right now

Nothing from an external account. No stock API key, SEC account, domain
password, router password, public IP address, or database password is needed
to build and run the local rehearsal.

From the repository root on the current development computer:

```powershell
pwsh -NoProfile -File ./scripts/verify-home-server-deployment.ps1
```

Docker must be running. The command builds inside Docker, binds only an
available `127.0.0.1` port in `18080-18179`, accepts the ephemeral local
certificate only inside the harness HTTP client and optional Playwright
context, checks all 12 public routes and database evidence, and then removes
its own containers, volumes, images, and temporary secret. The CA is never
installed into host trust.

For the final local pre-deployment pass, add `-RunBrowserSuite`. This requires
the already installed workspace pnpm/Playwright dependencies and runs all 72
checks at 1440, 1280, and 390 pixels through the same loopback Caddy endpoint;
retries are forced to zero, and its output stays inside the harness-owned
temporary directory:

```powershell
pwsh -NoProfile -File ./scripts/verify-home-server-deployment.ps1 -RunBrowserSuite
```

## Future server baseline

Recommended for the first approximately 100 readers:

- Ubuntu Server 24.04 LTS (recommended) or 26.04 LTS
- `amd64` or `arm64`
- 4 logical CPU cores and 8 GiB RAM recommended
- 2 cores and 4 GiB RAM are the practical minimum; local image builds have
  less headroom at that size
- at least 50 GiB free for the initial stack, images, and rollback headroom
- Docker Engine from Docker's official Ubuntu repository and Compose 2.20.0+

The planned 1 TB disk is ample for the initial DEMO instance. Capacity alone
does not make a same-disk copy a backup.

Run the read-only host check after Docker is installed on the future server:

```bash
bash deploy/home-server/preflight.sh --mode host
```

The script reports facts only. It does not install packages, change firewall
rules, start containers, edit DNS, or open ports.

## Values needed only at real cutover

Do not send any password, private key, router credential, or raw IP address in
chat. Set the following directly on the future server:

1. Exact public domain or subdomain, such as `stocks.your-domain.kr`.
2. A monitored email address for ACME certificate notices.
3. The Git SHA being deployed.
4. A locally generated PostgreSQL password file.
5. A confirmed direct-ingress mode: public IPv4, global IPv6, or both.
6. The exact public IPv4/global IPv6 value already assigned by the ISP.
7. Confirmation that the public address is static for ADR-046.

Create the database secret on the server; the command does not print it:

```bash
sudo install -d -m 0700 -o "$USER" -g "$USER" /etc/wall-street-receipts/secrets
umask 077
openssl rand -hex 32 > /etc/wall-street-receipts/secrets/postgres_password
chmod 600 /etc/wall-street-receipts/secrets/postgres_password
```

Copy the non-secret template to the ignored deployment env file and edit only
that copy:

```bash
cp deploy/home-server/.env.example deploy/home-server/.env.production
```

The exact locations are therefore:

- settings: `deploy/home-server/.env.production`
- database secret:
  `/etc/wall-street-receipts/secrets/postgres_password`

Never put the password itself in `.env.production`. The env file contains only
its absolute path.

For the first direct-ingress release, set:

```dotenv
WSR_DOMAIN=stocks.your-domain.kr
WSR_ACME_EMAIL=monitored-address@example.com
WSR_IMAGE_TAG=<40-character-git-sha>
WSR_POSTGRES_PASSWORD_FILE=/etc/wall-street-receipts/secrets/postgres_password
WSR_INGRESS_MODE=direct-ipv4
WSR_PUBLIC_IP_POLICY=static
WSR_PUBLIC_IPV4=<your-public-ipv4>
WSR_PUBLIC_IPV6=unknown
```

Use `direct-ipv6` or `direct-dual-stack` only after the router and host IPv6
firewalls have been checked. Use the lowercase ASCII/punycode form for an
internationalized domain.

Then run the fail-closed publication preflight:

```bash
bash deploy/home-server/preflight.sh \
  --mode publish \
  --env-file deploy/home-server/.env.production
```

Do not `export` any `WSR_*` or `COMPOSE_*` variable in that shell. The env
parser accepts only the template's unquoted `KEY=value` allowlist, rejects
duplicates and interpolation, requires a clean Git checkout, and requires
`WSR_IMAGE_TAG` to equal the full 40-character `git rev-parse HEAD`. It also
checks that DNS A/AAAA records exactly match the configured public address
family without printing the address value.

Passing means only that the local deployment attempt is configured. It still
prints `PENDING_EXTERNAL_INGRESS`; only a test from a different network can
prove router forwarding, ISP reachability, TLS issuance, and IPv6 behavior.

## Direct-publication runbook

Do not run these commands until the publish preflight passes and Gabia DNS is
pointing at the intended public address.

Validate the complete Compose model without starting a container:

```bash
python3 scripts/verify-home-server-deployment.py
```

Build the two application images and the non-root Caddy image from the checked-
out Git SHA through the environment-sanitizing local-Docker wrapper:

```bash
bash deploy/home-server/compose-production.sh \
  --env-file deploy/home-server/.env.production \
  -- build
```

Start the production profile:

```bash
bash deploy/home-server/compose-production.sh \
  --env-file deploy/home-server/.env.production \
  -- up
```

The wrapper removes inherited `WSR_*`, `COMPOSE_*`, proxy, Docker, BuildKit,
and Buildx override variables, then pins the exact local Docker endpoint before
every Compose operation. It reruns the strict env/source/secret/DNS contract immediately
before Compose and refuses remote daemons. Resource limits are fixed in the
Compose model and cannot be changed through `.env.production`. Run it from the
same checked-out release that passed `preflight.sh`.

The wrapper accepts only one action name and supplies every Compose option
itself: `build`, `up`, `ps`, `logs`, `stop`, or `down`. `down` never adds
`--volumes`, so the three named data/certificate volumes remain. Arbitrary
Compose arguments, `run`/`exec`, published backend ports, environment/mount/
entrypoint overrides, custom build arguments, privilege, and volume deletion
are outside this wrapper. A broader update/rollback command surface belongs to
ADR-047 after backup and restore evidence exists.

Caddy persists certificates and account state in named volumes. Do not delete
`caddy-data`, `caddy-config`, or `postgres-data` during routine updates.

After start, use a phone with Wi-Fi disabled or another external connection to
verify:

- `http://<domain>` redirects to HTTPS;
- `https://<domain>` renders the DEMO banner and timestamp;
- only TCP 80/443 are reachable;
- 22, 3000, 5432, and 8080 are not reachable from the internet;
- browser traffic never calls the Spring origin directly.

Enable HSTS only in a later change after external HTTPS has been stable. A bad
AAAA record can break access for IPv6 users, so do not publish one before IPv6
firewall and reachability checks.

## Router and CGNAT decision

Give the server a fixed DHCP lease. Forward only router TCP 80 and 443 to that
LAN address. Do not forward SSH 22, and disable UPnP if it is not needed.

If the router WAN address is private, is in `100.64.0.0/10`, or differs from
the ISP-facing address, direct IPv4 forwarding may be blocked by CGNAT or
double NAT. The cloud-free recommendation is to ask the ISP for a public IP.
A tunnel would require a later architecture decision plus an external account
and token; it is intentionally not enabled here.

Dynamic public DNS is also not implemented in ADR-046. If the public address
is dynamic, stop before cutover so a Gabia-compatible update mechanism and its
credential storage can be reviewed.

## Security and data boundaries

- Docker-published ports can bypass expectations based only on UFW. The primary
  control here is that only Caddy has `ports` entries at all.
- Do not enable `OPERATOR_API_ENABLED` in this stack. Its current single-operator
  contract binds the entire Spring server to loopback and is not a remote admin
  design.
- Do not enable SEC or live market providers here. SEC would first require a
  monitored contact email; commercial feeds additionally require approved
  product rights and server-side credentials.
- Do not use `latest`, automated container updaters, or an unreviewed PostgreSQL
  major-tag change. Rehearse every image update first.

Logical backup, off-device retention, empty-target restore testing, and a
release rollback package are intentionally the next ADR-047 slice. Do not put
non-DEMO or irreplaceable data into this deployment before that slice passes.
