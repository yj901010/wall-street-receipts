# Ubuntu home-server deployment foundation

This directory contains the ADR-046 deployment boundary, ADR-047 recovery
boundary, ADR-048 exact release-schema gate, ADR-049 read-only promotion plan,
ADR-050 generation-control contract foundation, and ADR-051 read-only server-
fact collector for the public **DEMO** site. ADR-056 adds the disposable
offline release-source handoff rehearsal that feeds this runbook's future exact
checkout without contacting GitHub. The directory also carries ADR-053's same-
origin exact SEC manifest audit consumer without exposing Spring directly.
These files are independent from the root development `compose.yaml` and never
load the ignored root `.env`.

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
  providers remain off. Existing market/call surfaces remain explicitly
  DEMO/fixture-backed. The SEC manifest locator pins its web provider to the
  private API without claiming that a stored manifest, live data mode, or
  provider collection is available.
- `/research/sec/filing-history` reaches Next through Caddy. In production
  `SEC_MANIFEST_AUDIT_PROVIDER=api` makes Next read one exact ADR-052 resource
  over `http://api:8080`; the browser never receives that private origin, and
  an API error never falls back to the synthetic fixture.
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
context, checks the allowlisted public routes and database evidence, and then
removes its own containers, volumes, images, and temporary secret. The CA is
never installed into host trust.

For the final local pre-deployment pass, add `-RunBrowserSuite`. This requires
the already installed workspace pnpm/Playwright dependencies and runs the full
configured suite at 1440, 1280, and 390 pixels through the same loopback Caddy
endpoint; retries are forced to zero, and its output stays inside the harness-
owned temporary directory:

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

## Exact SEC audit web runtime

ADR-053 adds one same-origin public route without changing the ingress or
network topology:

```text
/research/sec/filing-history
```

The production/rehearsal web container has these non-secret settings:

```dotenv
SEC_MANIFEST_AUDIT_PROVIDER=api
API_BASE_URL=http://api:8080
SITE_ORIGIN=https://<exact-public-domain>
```

`API_BASE_URL` is reachable only from the web container on `app-internal` and
is never sent to the browser. The production Compose contract rejects a switch
to `fixture`; API failure, malformed evidence, or 404 never displays the
synthetic DEMO artifact. `SITE_ORIGIN` is used for absolute canonical/social
metadata and is derived from the exact `WSR_DOMAIN` at publication. It is not a
provider URL, API credential, or evidence timestamp.

The locator needs no stored manifest. A successful API-backed evidence view
does require an immutable manifest already present in PostgreSQL, its exact
lowercase SHA-256 `manifestId`, and an `evaluationAsOf` cutoff at or after
assembly. This stack keeps `SEC_PROVIDER_ENABLED=false`, so it neither creates
that evidence nor contacts SEC. Do not enable collection merely to make the
page nonempty and do not seed a synthetic manifest into production as if it
were observed evidence.

No API key, SEC account, paid plan, or `SEC_CONTACT_EMAIL` is required for this
read-only web slice. At actual publication the operator must provide the real
domain so `WSR_DOMAIN`/`SITE_ORIGIN` are truthful. Only a separately approved
future live-collection operation requires a monitored SEC contact email in an
untracked server secret environment.

## Release-source handoff before server work

[`ADR-056`](../../decisions/ADR-056-disposable-offline-git-flow-release-source-handoff-rehearsal.md)
fills only the source-transfer gap between this development computer and a
future exact release checkout. Its current command is a disposable, offline
rehearsal:

```powershell
pwsh -NoProfile -File ./scripts/verify-local-release-handoff.ps1
```

It simulates the required Git Flow entirely inside an owned temporary
repository: cached `main`/`develop`, backlog fast-forward, feature `--no-ff`
merge, one empty rehearsal release commit, release `--no-ff` merges into both
temporary branches, and one annotated rehearsal tag. The tagged release tree
must equal the exact committed feature `HEAD` tree. The harness then creates a
complete no-prerequisite tag-only bundle, strict JSON identity manifest, and
adjacent SHA-256 receipt, rejects corrupt or inconsistent artifacts, performs
an isolated offline import, verifies tag/commit/tree identity, clean status,
detached `HEAD`, zero remotes, complete Git object connectivity, and required
paths, and cleans up. Each Git child disables lazy fetch and optional locks and
injects `gc.auto=0`, `maintenance.auto=0`, and `core.fsmonitor=false`. Source
or imported repositories with local executable `core.fsmonitor`,
`uploadpack.packobjectshook`, or `filter.*.(clean|smudge|process)` configuration
are rejected. Each child has a two-minute deadline; after a
timeout its killed process tree must terminate within five more seconds.
Captured stdout and stderr are read to completion and then individually
rejected above 1,048,576 characters, so this is an accepted-output limit rather
than a streaming memory cap. Cleanup additionally requires the exact temporary
parent/name and its flushed unpredictable owner marker. It must report:

```text
NOT_RELEASED
REMOTE_NOT_CONTACTED
```

The rehearsal does not publish a reusable artifact and does not contact the
configured origin or any network endpoint. Its local `file`-protocol pushes
target only the owned simulated bare remote. It creates or changes no branch or
tag in the source checkout or actual configured remote, pull request, real
release branch/merge, Docker image, or Ubuntu checkout. Its temporary Git Flow
refs exist only inside the owned rehearsal root. The source working-tree
version of `apps/web/next-env.d.ts` is excluded and preserved; only its
committed `HEAD` version can appear in the temporary bundle and clone.

No API key, domain, server, Docker daemon, GitHub login, network authorization,
release version, repository-visibility decision, token, or private key is
needed now. The adjacent checksum receipt covers only the named bundle bytes;
it does not cover `manifest.json` or authenticate the complete three-file set.
Canonical parsing and selected bundle/tag/source cross-checks reject malformed
or inconsistent metadata, but they do not recompute every manifest-only Git
Flow identity. Preserve an independently reviewed manifest, commit/digest
record, or signature before relying on those fields. The artifacts supply
neither a trusted source signature nor Docker image custody, reproducible
binary/image proof, image scanning, dependency archival, or rollback readiness.

The byte-flip case is accepted as a negative test only when it fails at the
exact bundle SHA-256 comparison. The truncated-and-rehashed case must reach and
fail exact structural verification or full unbundle with a nonzero Git exit;
an unrelated exception cannot make either negative test pass.

### Later approved offline import on Ubuntu

The disposable harness deletes its artifacts. The commands below apply only if
a later explicitly approved exporter retains the same candidate bundle,
`manifest.json`, and checksum receipt on reviewed removable media. The manifest
still says `NOT_RELEASED`; import is source-custody evidence only and must not
start production publication. Replace only the two angle-bracket path
components, then keep the three artifact files together:

```bash
set -euo pipefail

handoff_dir='/media/<operator>/<media>/wall-street-receipts-handoff'
cd -- "$handoff_dir"

test -f manifest.json
manifest_output="$(python3 - manifest.json <<'PY'
import json
import re
import sys
from pathlib import Path

keys = [
    "schemaVersion", "project", "releaseStatus", "networkStatus",
    "sourceBranch", "sourceCommit", "sourceTree", "cachedOriginMain",
    "cachedOriginDevelop", "localDevelop", "featureAheadCount",
    "integrationCommit", "releasePreparationCommit", "mainReleaseCommit",
    "developReleaseCommit", "annotatedTag", "tagObject", "bundleRef",
    "bundleFile", "bundleBytes", "bundleSha256", "bundlePrerequisiteCount",
]

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate field: {key}")
        result[key] = value
    return result

raw = Path(sys.argv[1]).read_bytes()
if not 2 <= len(raw) <= 8192 or not raw.endswith(b"\n"):
    raise SystemExit("manifest size/final-LF mismatch")
if any(value < 32 or value > 126 for value in raw[:-1]):
    raise SystemExit("manifest is not printable ASCII")
if raw[:-1].find(b"\n") != -1:
    raise SystemExit("manifest is not one canonical JSON line")

document = json.loads(raw[:-1].decode("ascii"), object_pairs_hook=unique_object)
if list(document) != keys:
    raise SystemExit("manifest field order/schema mismatch")
canonical = json.dumps(document, ensure_ascii=True, separators=(",", ":"))
if canonical.encode("ascii") + b"\n" != raw:
    raise SystemExit("manifest is not canonical JSON")

numeric_keys = {
    "schemaVersion", "featureAheadCount", "bundleBytes",
    "bundlePrerequisiteCount",
}
string_keys = set(keys) - numeric_keys
int64_min = -(2**63)
int64_max = 2**63 - 1
if any(
    type(document[key]) is not int
    or not int64_min <= document[key] <= int64_max
    for key in numeric_keys
):
    raise SystemExit("manifest numeric field type/int64 mismatch")
if any(type(document[key]) is not str for key in string_keys):
    raise SystemExit("manifest string field type mismatch")

if type(document["schemaVersion"]) is not int or document["schemaVersion"] != 1:
    raise SystemExit("schemaVersion mismatch")
if document["project"] != "wall-street-receipts":
    raise SystemExit("project mismatch")
if document["releaseStatus"] != "NOT_RELEASED":
    raise SystemExit("release status mismatch")
if document["networkStatus"] != "REMOTE_NOT_CONTACTED":
    raise SystemExit("network status mismatch")
if (
    not re.fullmatch(r"feature/[a-z0-9][a-z0-9._/-]*", document["sourceBranch"])
    or ".." in document["sourceBranch"]
    or "@{" in document["sourceBranch"]
):
    raise SystemExit("source branch mismatch")

commit_fields = [
    "sourceCommit", "sourceTree", "cachedOriginMain", "cachedOriginDevelop",
    "localDevelop", "integrationCommit", "releasePreparationCommit",
    "mainReleaseCommit", "developReleaseCommit", "tagObject",
]
if any(not re.fullmatch(r"[0-9a-f]{40}", document[key]) for key in commit_fields):
    raise SystemExit("non-canonical Git object")
if type(document["featureAheadCount"]) is not int or document["featureAheadCount"] <= 0:
    raise SystemExit("featureAheadCount mismatch")
if type(document["bundleBytes"]) is not int or document["bundleBytes"] <= 0:
    raise SystemExit("bundleBytes mismatch")
if type(document["bundlePrerequisiteCount"]) is not int or document["bundlePrerequisiteCount"] != 0:
    raise SystemExit("bundle prerequisite mismatch")

tag = document["annotatedTag"]
if not re.fullmatch(r"v0\.0\.0-rehearsal\.[0-9a-f]{24}", tag):
    raise SystemExit("annotated rehearsal tag mismatch")
if document["bundleRef"] != f"refs/tags/{tag}":
    raise SystemExit("bundle ref mismatch")
expected_bundle = f"wall-street-receipts-{document['mainReleaseCommit']}.bundle"
if document["bundleFile"] != expected_bundle:
    raise SystemExit("bundle file mismatch")
if not re.fullmatch(r"[0-9a-f]{64}", document["bundleSha256"]):
    raise SystemExit("bundle digest mismatch")

for value in (
    document["bundleFile"], document["bundleBytes"], document["bundleSha256"],
    document["annotatedTag"], document["tagObject"], document["bundleRef"],
    document["mainReleaseCommit"], document["sourceCommit"],
    document["sourceTree"], document["sourceBranch"],
):
    print(value)
PY
)"
mapfile -t manifest_values <<< "$manifest_output"
test "${#manifest_values[@]}" -eq 10

bundle_file="${manifest_values[0]}"
bundle_bytes="${manifest_values[1]}"
bundle_sha256="${manifest_values[2]}"
annotated_tag="${manifest_values[3]}"
tag_object="${manifest_values[4]}"
bundle_ref="${manifest_values[5]}"
main_release_commit="${manifest_values[6]}"
source_commit="${manifest_values[7]}"
source_tree="${manifest_values[8]}"
source_branch="${manifest_values[9]}"
receipt_file="${bundle_file}.sha256"
bundle_path="${handoff_dir}/${bundle_file}"

git check-ref-format --branch "$source_branch" >/dev/null
test -f "$bundle_file"
test -f "$receipt_file"
test "$(wc -l < "$receipt_file")" -eq 1
test "$(cat "$receipt_file")" = "$bundle_sha256 *$bundle_file"
sha256sum --strict --check "$receipt_file"
test "$(sha256sum "$bundle_file" | awk '{print $1}')" = "$bundle_sha256"
test "$(wc -c < "$bundle_file" | tr -d ' ')" = "$bundle_bytes"

checkout=/srv/wall-street-receipts/repository
test ! -e "$checkout"
sudo install -d -m 0755 -o root -g root /srv/wall-street-receipts
sudo git init "$checkout"
sudo git -C "$checkout" bundle verify "$bundle_path"
test "$(sudo git -C "$checkout" bundle list-heads "$bundle_path")" \
  = "$tag_object $bundle_ref"
sudo git -C "$checkout" bundle unbundle "$bundle_path"
sudo git -C "$checkout" update-ref "$bundle_ref" "$tag_object"

test "$(sudo git -C "$checkout" cat-file -t "$tag_object")" = tag
test "$(sudo git -C "$checkout" rev-parse "${bundle_ref}^{tag}")" \
  = "$tag_object"
test "$(sudo git -C "$checkout" rev-parse "${bundle_ref}^{commit}")" \
  = "$main_release_commit"
test "$(sudo git -C "$checkout" cat-file -t "$source_commit")" = commit
test "$(sudo git -C "$checkout" rev-parse "${source_commit}^{tree}")" \
  = "$source_tree"
sudo git -C "$checkout" checkout --detach "$main_release_commit"
test "$(sudo git -C "$checkout" rev-parse 'HEAD^{tree}')" = "$source_tree"
test "$(sudo git -C "$checkout" \
  rev-parse 'HEAD:apps/web/next-env.d.ts')" = \
  "$(sudo git -C "$checkout" \
  rev-parse "${source_commit}:apps/web/next-env.d.ts")"
test -z "$(sudo git -C "$checkout" \
  status --porcelain=v1 --untracked-files=all)"
sudo git -C "$checkout" fsck --full --strict --no-dangling
sudo git -C "$checkout" rev-parse --is-shallow-repository | grep -Fx false
test -z "$(sudo git -C "$checkout" config --local \
  --get extensions.partialClone || true)"
test -z "$(sudo git -C "$checkout" config --local --get-regexp \
  '^remote\..*\.promisor$' || true)"
test -z "$(sudo git -C "$checkout" config --local --get-regexp \
  '^remote\..*\.partialclonefilter$' || true)"
for forbidden_git_path in \
  objects/info/alternates objects/info/http-alternates info/grafts
do
  resolved_git_path="$(sudo git -C "$checkout" \
    rev-parse --path-format=absolute --git-path "$forbidden_git_path")"
  test ! -e "$resolved_git_path"
done
test -z "$(sudo git -C "$checkout" for-each-ref \
  --format='%(refname)' refs/replace)"
test -z "$(sudo git -C "$checkout" symbolic-ref --quiet HEAD || true)"
test -z "$(sudo git -C "$checkout" remote)"

for required_path in \
  AGENTS.md README.md .env.example apps/api/pom.xml \
  apps/api/src/main/resources/db/migration/V1__baseline.sql \
  apps/web/package.json deploy/home-server/compose.yaml \
  deploy/home-server/preflight.sh deploy/home-server/compose-production.sh \
  deploy/home-server/server-facts.sh \
  scripts/verify-home-server-deployment.py \
  scripts/verify-local-release-handoff.ps1 \
  decisions/ADR-056-disposable-offline-git-flow-release-source-handoff-rehearsal.md
do
  test -f "$checkout/$required_path"
done

printf '%s\n' NOT_RELEASED REMOTE_NOT_CONTACTED
```

The import parser enforces the harness's exact 22-field JSON order, canonical
one-line encoding, four JSON Number/int64 fields, 18 JSON String fields, fixed
values, object syntax, byte count, and bundle digest. It also proves a zero-
prerequisite single-tag bundle against a new empty repository and cross-checks
the recorded bundle and advertised tag before unbundling, then cross-checks the
main release, source commit, and source tree after import. The one-line receipt
hashes only the bundle. This later import does
not recompute `cachedOriginMain`, `cachedOriginDevelop`, `localDevelop`,
`integrationCommit`, `releasePreparationCommit`, `developReleaseCommit`, their
parent graph/tree equality, or the observed `featureAheadCount`; a syntactically
valid manifest-only change to those values can pass. Retain an independently
reviewed manifest and commit/digest, or add a separately designed signature,
before relying on those fields or making an authenticity claim.

From the verified checkout, collect the ADR-051 facts before installing a
secret or starting a container:

```bash
cd /srv/wall-street-receipts/repository
sudo env -i PATH=/usr/sbin:/usr/bin:/sbin:/bin \
  bash deploy/home-server/server-facts.sh --output stdout
bash deploy/home-server/preflight.sh --mode host
```

Stop after those read-only facts. The imported manifest is still
`NOT_RELEASED`; do not create `.env.production`, run publish preflight, build a
production image, or start the production profile from this rehearsal tag.

Do not copy this development computer's ignored root `.env`, deployment
`.env.production`, database password, tokens, private keys, generated build
output, or modified `apps/web/next-env.d.ts` onto the server.

### Later GitHub alternative

GitHub is an alternative source transport only after explicit network
authorization. Before any `fetch` or `push`, decide whether the repository will
be public or private, perform a fresh remote fetch and divergence review,
choose a version (`v0.1.0-rc.1` is recommended for the first candidate), and
configure GitHub authentication locally on the development computer. Never put
a token, SSH private key, password, recovery code, or credential-helper content
in chat, `.env`, or Git.

The later Git Flow sequence is feature-branch push and hosted pull request into
`develop`, reviewed CI, `release/0.1.0-rc.1` creation from the reviewed
`develop`, release-only stabilization, reviewed merges into both `main` and
`develop`, then an annotated `v0.1.0-rc.1` tag on the exact `main` release
merge. ADR-056 performs none of those operations.

After those steps, a public repository needs no server credential. A private
repository needs a least-privilege credential or deploy key stored only on the
server. The server clone must be complete—never `--depth`, `--shallow-*`,
`--filter`, sparse promisor, or partial—and must detach at the reviewed tag's
exact commit:

```bash
repository_url='https://github.com/<owner>/<repository>.git'
expected_release_commit='<reviewed-40-character-main-release-sha>'
printf '%s\n' "$expected_release_commit" | grep -Eq '^[0-9a-f]{40}$'

git clone "$repository_url" wall-street-receipts
cd wall-street-receipts
git fetch --prune --force --tags origin
test "$(git cat-file -t refs/tags/v0.1.0-rc.1)" = tag
release_commit="$(git rev-list -n 1 v0.1.0-rc.1)"
test "$release_commit" = "$expected_release_commit"
git switch --detach "$release_commit"
test -z "$(git status --porcelain=v1 --untracked-files=all)"
git fsck --full --strict
git rev-parse --is-shallow-repository | grep -Fx false
test -z "$(git config --local --get-regexp \
  '^(extensions\.partialclone|remote\..*\.promisor)$' || true)"
```

The fresh fetch is a later explicitly authorized network action, not part of
the offline rehearsal. GitHub transport and an annotated but unsigned tag do
not by themselves prove source authenticity or reproducible image bytes.

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

The Compose web service derives `SITE_ORIGIN=https://${WSR_DOMAIN}` from that
same reviewed value. Do not point social metadata at a placeholder, loopback,
different host, path, query, or fragment during public cutover.

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
- Keep `SEC_MANIFEST_AUDIT_PROVIDER=api` in the production web container. The
  committed fixture is only a visibly synthetic local test source and is never
  a production fallback for absent, future, corrupt, or unavailable evidence.
- Do not use `latest`, automated container updaters, or an unreviewed PostgreSQL
  major-tag change. Rehearse every image update first.

Do not put non-DEMO or irreplaceable data into this deployment until the real
backup device passes production preflight, at-rest encryption is approved, a
successful backup has immutable fresh-target restore evidence, and an offline
or off-site copy policy is implemented.
