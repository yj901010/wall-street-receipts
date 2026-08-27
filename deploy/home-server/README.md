# Ubuntu home-server deployment foundation

This directory contains the ADR-046 deployment boundary, ADR-047 recovery
boundary, ADR-048 exact release-schema gate, ADR-049 read-only promotion plan,
ADR-050 generation-control contract foundation, and ADR-051 read-only server-
fact collector for the public **DEMO** site. They are independent from the
root development `compose.yaml` and never load the ignored root `.env`.

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
availability, DDoS protection, or full disaster recovery. A same-server
additional HDD is a separate-device local copy, not an offline or off-site
copy.

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

Run the ADR-047 database recovery rehearsal separately when Docker is running:

```powershell
pwsh -NoProfile -File ./scripts/verify-home-server-recovery.ps1
```

It reuses the isolated loopback deployment only as a disposable source. The
test streams a PostgreSQL custom archive to a harness-owned temporary staging
directory, verifies `pg_restore --list`, keeps interrupted `.partial` staging
ineligible, checks digest and full-restore corruption failures, then restores
into a new label-owned PostgreSQL volume with `network=none` and no published
port. Flyway history, table inventory, and row counts come from that restored
database, not from a nearby query against the source. The command never reads
the root `.env`, mounts a backup HDD into a container, binds 80/443, or claims
the development computer has a separate backup device. The two honest local
results remain `PENDING_BACKUP_DEVICE` and `PENDING_OFFSITE_COPY`.

## Future server baseline

Recommended for the first approximately 100 readers:

- Ubuntu Server 24.04 LTS (recommended) or 26.04 LTS
- `amd64` or `arm64`
- 4 logical CPU cores and 8 GiB RAM recommended
- 2 cores and 4 GiB RAM are the practical minimum; local image builds have
  less headroom at that size
- at least 50 GiB free for the initial stack, images, and rollback headroom
- Docker Engine from Docker's official Ubuntu repository and Compose 2.20.0+
- rootful Docker without daemon-level user-namespace remapping; the fixed
  numeric ownership of the file-backed application secret depends on that
  reviewed boundary

The planned 1 TB disk is ample for the initial DEMO instance. Capacity alone
does not make a same-disk copy a backup.

Before changing the future server, capture the bounded ADR-051 facts from the
exact release checkout. This command clears the calling environment, pins the
collector to trusted Ubuntu command paths and the local Docker socket, prints
only to stdout, and does not install or change anything:

```bash
sudo env -i PATH=/usr/sbin:/usr/bin:/sbin:/bin \
  bash deploy/home-server/server-facts.sh --output stdout
```

If and only if a separate backup filesystem is already mounted at a reviewed
path, collect that exact mount too:

```bash
sudo env -i PATH=/usr/sbin:/usr/bin:/sbin:/bin \
  bash deploy/home-server/server-facts.sh \
  --backup-mount /mnt/<exact-mounted-device-directory> \
  --output stdout
```

The fixed-order report does not query the host name or current account and
omits IP and MAC addresses, process IDs, raw UUIDs and disk serials,
environment values, and secret contents. It does include the exact local
Docker, volume, and operator-supplied backup paths needed for planning; review
those non-secret paths before sharing the report.
`collection_status=complete` means only that the allowlisted facts were
observed. `bootstrap_gate` and `restart_policy_gate` remain
`REVIEW_REQUIRED`; the collector never claims that a live deployment is safe.

Then run the older read-only baseline evaluator after Docker is installed:

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
sudo install -d -m 0711 -o root -g root /etc/wall-street-receipts
sudo install -d -m 0711 -o root -g root /etc/wall-street-receipts/secrets
sudo install -m 0400 -o 10001 -g 10001 /dev/null \
  /etc/wall-street-receipts/secrets/postgres_password
openssl rand -hex 32 | sudo tee \
  /etc/wall-street-receipts/secrets/postgres_password >/dev/null
```

Compose implements file-backed secrets as bind mounts, so host ownership is
preserved rather than remapped. The API runtime uses numeric UID/GID
`10001:10001`; the PostgreSQL entrypoint starts as root. Keep the containing
directory root-owned with traversal-only mode `0711`, and the shared secret
file owned by `10001:10001` with mode `0400`. An unprivileged operator can
validate the exact known path but cannot list the directory or read the secret.
Never print or commit its contents.

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

Provision the fixed ADR-050 operation lock as documented under
[Generation-control contract foundation](#generation-control-contract-foundation)
before this production-mode check. It and every production wrapper run as root
so the permanent root-owned lock cannot be bypassed by a less-privileged
operator.

```bash
sudo bash deploy/home-server/preflight.sh \
  --mode publish \
  --env-file deploy/home-server/.env.production
```

Do not `export` any `WSR_*` or `COMPOSE_*` variable in that shell. The env
parser accepts only the template's unquoted `KEY=value` allowlist, rejects
duplicates and interpolation, requires a clean Git checkout, and requires
`WSR_IMAGE_TAG` to equal the full 40-character `git rev-parse HEAD`. It also
checks that DNS A/AAAA records exactly match the configured public address
family without printing the address value. Root execution supplies an exact
per-command `safe.directory` value for this checkout only; it does not modify
global Git configuration when the checkout belongs to the non-root operator.

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
sudo bash deploy/home-server/compose-production.sh \
  --env-file deploy/home-server/.env.production \
  -- build
```

Start the production profile:

```bash
sudo bash deploy/home-server/compose-production.sh \
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
a future ADR after ADR-047 backup/restore evidence, schema compatibility, and
fresh-volume promotion have all been reviewed.

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

## Separate-device backup and recovery

ADR-047 needs no API key, provider account, cloud account, domain credential,
or new secret. It intentionally does not format, partition, encrypt, unlock,
mount, or edit `/etc/fstab` for a disk. Because the additional HDD is not yet
selected, stop after the local rehearsal for now.

Before live backup is enabled, provide these facts from the future Ubuntu
server:

1. the already mounted HDD's exact mount path;
2. its filesystem UUID, filesystem type (`ext4` or `xfs`), capacity, and
   whether it is internal SATA or USB;
3. `WSR_BACKUP_ENCRYPTION=luks2` (recommended before irreplaceable/non-DEMO
   data) or the explicit reduced-assurance `none-demo-only` choice;
4. the local LUKS2 boot/unlock and recovery-key procedure, if selected; and
5. the desired manual/scheduled recovery-point interval after one reboot has
   proven the mount and unlock behavior.

Do not send an encryption passphrase, LUKS recovery key, PostgreSQL password,
or any other secret in chat. Those values are created and retained locally.
The exact non-secret live configuration path is
`/etc/wall-street-receipts/backup.conf`; it accepts only:

```dotenv
WSR_BACKUP_MOUNT=/mnt/<exact-mounted-device-directory>
WSR_BACKUP_FILESYSTEM_UUID=<exact-filesystem-uuid>
WSR_BACKUP_ENCRYPTION=luks2
```

The example is [`backup.conf.example`](backup.conf.example). On the real
server, first run the non-mutating host check:

```bash
bash deploy/home-server/recovery-preflight.sh --mode host
```

Only after the disk choices above are reviewed, install and edit the config
locally:

```bash
sudo install -d -m 0711 -o root -g root /etc/wall-street-receipts
sudo install -m 0600 -o root -g root \
  deploy/home-server/backup.conf.example \
  /etc/wall-street-receipts/backup.conf
sudoedit /etc/wall-street-receipts/backup.conf
```

The exact mount must already be a distinct active `ext4`/`xfs` filesystem with
`rw,nodev,nosuid,noexec`. This first implementation accepts only one physical
disk, at most one partition, and, for `luks2`, exactly one dm-crypt layer. It
fails closed on OS-visible LVM, MD/software RAID, multi-disk, loop, and other
mapper/virtual ancestry for both the backup mount and Docker's data root. A
default Ubuntu LVM layout therefore needs a later reviewed topology or Docker
data on a simple partition before production recovery can pass. Create a
root-owned mode-0700
`<mount>/wall-street-receipts` directory and a root-owned, single-link,
mode-0400 `<mount>/wall-street-receipts/.store-identity` file containing exactly
these three lines, substituting the same UUID used in `backup.conf`:

```text
schema_version=1
namespace=wall-street-receipts
filesystem_uuid=<exact-filesystem-uuid>
```

The scripts never create or change that identity marker. They verify that the
configured path is the exact mount, the UUID matches, the mount options are
hardened, and its physical block-device leaves are disjoint from Docker's data
root. The preflight hashes allowlisted direct SATA/USB/NVMe transport and
serial evidence instead of trusting `/dev` names alone. This still cannot see
through a hidden hardware RAID controller or VM backing store, so at cutover
the operator must also confirm the server is physical and both leaves are
direct devices. Missing or ambiguous evidence fails as
`PENDING_BACKUP_DEVICE` rather than falling back to the primary disk.

Then run the read-only production check:

```bash
sudo bash deploy/home-server/recovery-preflight.sh --mode production
```

The fixed operator actions are:

```bash
sudo bash deploy/home-server/recovery-production.sh -- preflight
sudo bash deploy/home-server/recovery-production.sh -- create
sudo bash deploy/home-server/recovery-production.sh -- status
sudo bash deploy/home-server/recovery-production.sh -- rehearse-latest
sudo bash deploy/home-server/recovery-production.sh -- retention-plan
sudo bash deploy/home-server/recovery-production.sh -- schema-check-latest
sudo bash deploy/home-server/recovery-production.sh -- promotion-plan-latest
```

`create` streams `pg_dump` from exactly one healthy Compose-labeled PostgreSQL
container into same-filesystem `.partial` staging on the host, validates its
checksum and parsed archive inventory, and publishes without overwrite only
after flushing. A completed point contains exactly `database.dump`,
`database.dump.sha256`, `database.inventory`, and the strict K/V `manifest`;
the manifest records the fixed dump options. Its `database_bytes` is only an
adjacent observation for capacity planning, not evidence from the dump's
logical snapshot. `rehearse-latest` verifies the complete bundle, checks
DockerRootDir scratch capacity, restores it with the recorded PostgreSQL image
into a fresh label-owned volume with
`network=none` and no port, derives Flyway/table evidence from that restored
target, binds the dynamically observed Flyway and key table counts to the
hashed restore-evidence manifest, and removes only its exact owned resources.
Production success never depends on the current DEMO fixture row counts. The backup HDD is never
mounted into PostgreSQL, API, web, or Caddy.

New rehearsals write database-evidence v2, including each Flyway migration's
ordered rank/version, UTF-8-hex description and script, exact SQL type, signed
checksum, and success state. Existing v1 evidence remains a valid historical
restore observation but is intentionally insufficient for ADR-048; run a new
`rehearse-latest` before `schema-check-latest`.

`schema-check-latest` is a fixed production-data-read-only gate. It accepts no
SHA, ref, image, path, backup ID, or Docker option. It resolves only the exact
commit recorded in the latest backup without reading HEAD, the working tree,
or the network; partial/promisor, alternate, replacement, short, missing, and
non-commit Git objects fail closed. It runs the exact recorded API image's
offline inventory command in one 30-second, 1 MiB-output, 384 MiB, 1-CPU,
128-PID, `network=none`, read-only, no-mount/no-port inspector with
`--pull never`, then removes that exact label-owned container. Git migration
blob SHA-256/bytes, packaged API migration/Flyway tuples, and restored v2
Flyway tuples must all match exactly. The current checkout may be dirty without
changing this object-based result.

`promotion-plan-latest` is also production-data-read-only and accepts no
caller-selected backup, generation, volume, SHA, image, path, or Docker option.
It reruns `schema-check-latest`, then requires exactly one healthy current
PostgreSQL, API, web, and production Caddy container. The PostgreSQL release
label and all running image references, full image IDs, and OCI revisions must
equal the latest backup manifest. Success emits the complete ordered plan as
`PROMOTION_PLAN_RECORD` rows plus a SHA-256 which changes with any bound source
identity. It does not create a candidate, stop or restart a service, change a
volume or network, write a selector or journal, or authorize activation.

The canonical rows include the complete backup and restore-evidence IDs, actual
backup-manifest/archive/restore-manifest/database-evidence SHA-256 values,
Git/Flyway facts, current full container and image IDs, and an observation UTC
interval. Before success the action reselects and strictly revalidates the
latest recovery point, rehashes its evidence, and reinspects every current
container ID. A backup/evidence or service change visible between those two
snapshots blocks instead of producing a mixed plan. This double observation is
not a lock, cannot eliminate the residual race after the final read, and must
be replaced by shared locking plus immediate revalidation in a future live
action.

ADR-050 now defines a fixed primary-host lock and strict generation document
contracts, but the plan still keeps activation blocked. Protected external-
volume indirection, production-auth candidate creation, offline image custody,
and explicit downtime/probation/write-RPO decisions are not implemented.
Capacity for two generations plus restore headroom and exact API/web/Caddy
environment, network, mount, and port validation are also mandatory activation
gates; this read-only action proves release-image identity and health but does
not infer full runtime topology. The ADR-047 rehearsal volume uses trust
authentication and is always disposable; it is never an eligible candidate.
Docker volumes cannot be atomically renamed or swapped, so a later
implementation will be a reviewed crash-consistent downtime transition with
the previous volume preserved, not a zero-downtime or atomic switch.

## Generation-control contract foundation

ADR-050 reserves one primary-host coordination boundary:

```text
/var/lib/wall-street-receipts/generation-control
/var/lib/wall-street-receipts/generation-control/operation.lock
```

The directory and permanent lock are not created by repository commands. On
the future Ubuntu server, after confirming the control root is on the intended
local persistent filesystem, provision them exactly once:

```bash
sudo install -d -m 0700 -o root -g root \
  /var/lib/wall-street-receipts/generation-control
sudo bash -c 'set -euo pipefail; umask 077; set -o noclobber; \
  : > /var/lib/wall-street-receipts/generation-control/operation.lock'
sudo chmod 0600 \
  /var/lib/wall-street-receipts/generation-control/operation.lock
```

If `operation.lock` already exists, stop and inspect it. Never truncate,
unlink, rename, or replace it. The root must be root-owned mode 0700 on local
persistent storage suitable for Linux `flock` and file/directory `fsync`, not
NFS, CIFS, FUSE, a temporary filesystem, or the removable backup device. The
lock file must be root-owned, single-linked, non-symlink, and mode 0600. The
wrappers compare the path and opened descriptor's device/inode before holding
the descriptor across the complete operation. The Compose wrapper remains the
parent lock owner while its Docker Compose child runs; it must not replace
itself with `exec`, because brace-allocated lock descriptors are not reliably
inherited across that boundary.

Contract/publish preflight, `ps`, `logs`, recovery production preflight,
recovery `preflight`, `status`, and `retention-plan` take a shared lock.
Deployment `build`, `up`, `stop`, and `down`, plus recovery `create`,
`rehearse-latest`, `schema-check-latest`, and `promotion-plan-latest`, take an
exclusive lock. Host-only discovery remains available before provisioning.
The older backup-device lock is nested only after the primary-host lock.

The source-only foundation defines four strict documents: active selector v1,
immutable generation manifest v1, backup generation binding v2, and immutable
hash-chained journal v1 intent/completion records. Each has an exact ordered
field set, LF-only canonical bytes with one final LF, a 32,768-byte limit,
closed value syntax, and byte-for-byte rerender validation. The files are never
sourced as shell. Malformed, reordered, duplicate, unknown, replayed, skipped,
or conflicting records fail closed. The journal classifier can report only a
stable state or a conservative manual-recovery directive; it executes no
recovery.

The generation-control contract requires staged bytes to be validated and
synced before an atomic same-filesystem rename, the destination directory to
be synced after rename, and the published inode and bytes to be reread. GNU
`sync -- PATH` invokes the per-path `fsync(2)` behavior used here; `sync -f`
would instead request filesystem-wide `syncfs(2)`. Existing backup and restore-
evidence staging directories are changed to final mode 0500 before their final
pre-rename directory sync, so sealed metadata is ordered before publication.
The shell boundary assumes a trusted root operator and root-only directories. It protects against
accidental or unprivileged path substitution, not a malicious concurrent root
process or raw root Docker commands.

Do not create `active.selector`, generation manifests, or journals during lock
provisioning. ADR-050 does not make Compose use an external volume and exposes
no candidate, activation, probation, finalization, rollback, selector-change,
or volume-deletion action. Current `restart: unless-stopped` behavior and
automatic boot recovery are not reconciled with a nonterminal journal and
remain blockers. No API key, account, domain, router credential, or new secret
is needed now. Before a live transition design, provide the real server
filesystem and Docker storage facts, two-generation capacity, acceptable
downtime, probation duration, and write-freeze/RPO policy. The complete
decision is
[`ADR-050`](../../decisions/ADR-050-generation-control-contract-foundation.md).

`retention-plan` is read-only: it reports the union of 14 daily, 8 weekly, and
12 monthly recovery points but deletes nothing. There is no production restore
or arbitrary backup/path/Docker argument. Actual fresh-volume promotion,
automatic pruning, scheduling, and off-site/offline copying remain blocked.
Every same-server result therefore continues to report
`PENDING_OFFSITE_COPY`. Successful database and release-image evidence never
emits `rollback-ready`. Exact schema compatibility can only move the remaining
status to blocked promotion/artifact gates; fresh-volume promotion,
previous-volume preservation, durable release-image custody, and off-site
copying remain future work.

## Read-only server-fact handoff

ADR-051 adds `server-facts.sh` because this development computer is not the
future home server and the target hardware is not known yet. Its command
surface is closed to optional `--backup-mount ABSOLUTE_PATH` and
`--output stdout`; duplicate, unknown, relative-path, and file-output arguments
are rejected. Without a backup path it honestly records `not-provided`.

Every child command has a four-second timeout and a 128 KiB capture ceiling.
The final 125-field report has a fixed order, printable ASCII values of at most
256 bytes, exactly one LF per record, and a 32 KiB total ceiling. Missing tools,
Docker access, volumes, containers, and optional facts remain explicit and
make `collection_status=partial`; they are never changed to zero or silently
replaced with development-computer facts.

The collector uses fixed Ubuntu command paths and an empty child environment,
pins Docker to `unix:///var/run/docker.sock`, isolates Docker CLI configuration,
and queries only the fixed Compose project and legacy PostgreSQL volume. It
does not enumerate arbitrary Docker resources. A supplied backup path must be
an exact active mount before filesystem or capacity facts are collected, so an
ordinary unmounted directory cannot be reported as an exact backup mount. That
check does not prove separate physical media: a bind mount or another
partition on the same disk can still be an exact mount. Listener addresses are
reduced to scope classes and process owners to a small allowlist; addresses and
PIDs are never printed.

This report is point-in-time planning evidence only. It does not prove physical
disk ancestry, CGNAT, router/DNS/TLS behavior, `flock`/`fsync` suitability,
off-site custody, or two-generation capacity. The existing production
preflights must revalidate exact local values later. Acceptable downtime,
probation duration, write-freeze/RPO, boot recovery, backup-device topology,
and offline/off-site copy remain operator decisions. The complete decision is
[`ADR-051`](../../decisions/ADR-051-read-only-ubuntu-server-fact-collection.md).

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

Do not put non-DEMO or irreplaceable data into this deployment until the real
backup device passes production preflight, at-rest encryption is approved, a
successful backup has immutable fresh-target restore evidence, and an offline
or off-site copy policy is implemented.
