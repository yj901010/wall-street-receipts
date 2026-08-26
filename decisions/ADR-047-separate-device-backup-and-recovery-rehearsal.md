# ADR-047 — Separate-Device Backup and Recovery Rehearsal

- Status: Accepted
- Date: 2026-08-26

## Context

ADR-046 provides a constrained Ubuntu home-server deployment boundary, but its
PostgreSQL data remains in one Docker volume on the server's primary storage.
That volume is persistence, not recovery evidence. Before non-DEMO or
irreplaceable data is accepted, the project needs a logical backup that can be
verified independently of the live volume, a full restore into an empty target,
and evidence that the restored schema belongs to the release intended for a
rollback.

The likely first backup target is an additional HDD installed in the same home
server. This is a **separate-device local backup**. It can protect against loss
of the primary data device, but it is not off-site, offline, air-gapped, or
write-once storage. It does not protect against theft, fire, a whole-machine
power or controller failure, privileged malware, operator-wide deletion, or a
failure shared by both disks.

The physical HDD, mount path, filesystem UUID, filesystem, capacity, and
encryption choice are not yet known. The development computer is not the home
server. Repository work must not format a disk, create a partition, unlock an
encrypted volume, edit `/etc/fstab`, mount a device, install a timer, or infer
the future storage topology from the development computer.

## Decision

Add a recovery boundary that can create and verify backup artifacts now with
owned temporary resources, while failing closed for live use until an actual
separate device has been identified on the Ubuntu server.

Two unresolved operational states must remain explicit:

- `PENDING_BACKUP_DEVICE` means the configured mount, filesystem identity,
  encryption state, and physical-device separation have not all been proven on
  the future server.
- `PENDING_OFFSITE_COPY` means no independently located or offline copy exists.
  A successful separate-HDD backup does not clear this state.

Neither state may be silently converted into a success claim by CI or a local
Docker rehearsal.

## Availability boundary

Creating a database backup must not depend on public DNS, Caddy health, ACME,
external reachability, a clean Git checkout, or the checked-out `HEAD`. Those
dependencies are especially likely to be unavailable during an incident, when
the database backup is most important.

The recovery tool independently selects and pins one local Docker endpoint. It
locates the database through the exact Compose project and service labels,
requires exactly one running PostgreSQL container with no published host port,
and observes the container and image identities before and after capture. It
does not broaden `compose-production.sh` with arbitrary `exec`, mount,
entrypoint, or volume-deletion options.

Database-backup validity, release-image evidence, and rollback readiness are
separate results. A valid database dump remains valid if a stateless service is
unavailable or a source checkout is dirty. This decision can produce verified
database and release-image evidence, but it never declares a rollback package
ready; the schema-compatibility and promotion gates below remain required.

## Backup-device contract

The live recovery configuration contains no secret. It records these exact
operator-controlled facts:

- `WSR_BACKUP_MOUNT`: the absolute mount point of the backup filesystem;
- `WSR_BACKUP_FILESYSTEM_UUID`: the exact filesystem UUID; and
- `WSR_BACKUP_ENCRYPTION`: the explicit value `luks2` or
  `none-demo-only`.

An empty, placeholder, or undecided encryption value fails. `none-demo-only`
is an explicit reduced-assurance choice for local or DEMO data; it does not
claim encrypted-at-rest protection and cannot satisfy a later non-DEMO data
gate. Irreplaceable or non-DEMO data requires `luks2` unless a later decision
approves another at-rest protection boundary.

The configuration parser accepts only its documented unquoted key/value
allowlist. Inherited WSR, Compose, Docker, proxy, BuildKit, and Buildx overrides
cannot replace the reviewed values or select a remote Docker daemon.

Live backup refuses to run unless all of the following are true:

1. the configured path is the exact active mount point, not a directory on a
   parent filesystem and not a symlink;
2. the mounted filesystem UUID exactly matches the configured UUID;
3. the configured `luks2` state is confirmed by the observed block-device
   ancestry, or the reduced-assurance choice is explicitly
   `none-demo-only`;
4. the backup mount and Docker's data root resolve through `findmnt` and the
   block-device graph to separate allowlisted direct SATA, USB, or NVMe leaf
   identities, compared by bounded transport plus serial evidence;
5. the device is writable, has bounded ownership and permissions, and contains
   the expected versioned store identity marker; and
6. sufficient capacity remains for staging the complete new artifact without
   deleting an existing recovery point first.

Ambiguous device-mapper, LVM, RAID, network-filesystem, virtual-disk, or
containerized mount ancestry fails closed. Transport/serial evidence reduces
device-name ambiguity but cannot prove the absence of a hidden hardware RAID
controller or virtualized backing store. Cutover therefore also requires the
operator to attest a direct physical SATA/USB/NVMe topology from the actual
home server. A later decision may add a specifically tested topology.

The backup HDD is accessible only to the host-side operator tool. It is never
mounted into PostgreSQL, Spring, Next.js, or Caddy. Store initialization may
create only the versioned marker and owned directories on an already mounted
filesystem. It never formats, partitions, mounts, unmounts, or unlocks a
device.

## Operator command boundary

The operator surface accepts exactly one of five fixed actions and supplies all
Docker and filesystem arguments itself:

- `preflight` performs the read-only device, local-Docker, database-container,
  configuration, and capacity checks;
- `create` produces one atomic recovery-point bundle and never prunes an older
  point;
- `status` reports the store identity, latest complete point, evidence state,
  capacity, and pending gates without changing state;
- `rehearse-latest` verifies and fully restores only the latest complete point
  into owned disposable resources; and
- `retention-plan` calculates and records the read-only retention report.

There is no caller-selected restore path, arbitrary recovery-point argument,
retention-apply action, production-restore action, or passthrough Docker
argument. Unknown actions and extra arguments fail before Docker or the backup
filesystem is mutated.

## Logical backup and atomic publication

The backup is produced by the PostgreSQL 17 client tools already present in the
observed database container. `pg_dump` writes a custom-format archive with
owner and ACL restoration disabled. The stream crosses the Docker execution
boundary directly into an owned host file; the HDD is not exposed to the
container and the database password is neither copied into the artifact nor
printed.

`pg_dump` supplies its own transactionally consistent logical snapshot. The
backup manifest records facts about the archive, including tool and server
versions, options, byte length, SHA-256 digest, and the successfully parsed
`pg_restore --list` inventory. It does **not** query production table counts in
a separate transaction and present those counts as evidence of the dump's
snapshot. Missing snapshot evidence remains missing rather than being inferred
from a nearby observation.

The manifest's `database_bytes` value is a bounded, adjacent
`pg_database_size` observation used only to reject insufficient backup and
Docker restore scratch capacity. It is not a dump byte count, row-count proof,
or observation from the dump's logical snapshot.

Each run creates a mode-0700 staging directory with a strict random name beneath
the final recovery-point parent on the backup filesystem. It writes the dump,
archive inventory, release facts, and a versioned canonical manifest; verifies
every expected file and digest; flushes files and directories; and only then
renames the staging directory atomically to its final UTC-based recovery-point
identifier on the same filesystem. A partial directory is never eligible for
restore, rollback evidence, or retention selection. Existing final identifiers
are never overwritten.

Published recovery points are immutable to the ordinary workflow. Integrity
digests detect accidental truncation and bit rot; they are not signatures and
do not prove authenticity against an attacker who can rewrite both an artifact
and its manifest.

## Release identity evidence

Release facts are derived from the observed deployment, not from a mutable tag
or the current working tree. The package records the exact image ID, configured
image reference, and observed OCI revision label for the API, web, Caddy, and
PostgreSQL images. An absent upstream revision label is recorded as absent; it
is never invented.

The API, web, and Caddy revision labels must remain unchanged across capture
and agree on one full Git SHA for **image-evidence-ready** status. A dirty
checkout or a different `HEAD` does not invalidate the database dump because
the capture path never reads the working tree. Missing, changing, or
disagreeing release-image observations are recorded as unavailable; the
database backup may still complete, but no rollback claim is made.

This slice does not archive image contents or committed source. A later
rollback package may archive those artifacts from the exact recorded Git object
and image IDs. It must never use `docker commit`, because a
container filesystem may contain runtime or secret material. The PostgreSQL
password, production env files, Caddy ACME account state, certificate private
keys, and certificate volumes are excluded. A recovered server must provide a
new or retained local database secret and retained or newly issued TLS state.

## Fresh-target restore rehearsal

Restore evidence is generated only after all manifest and payload hashes have
been rechecked. The rehearsal creates fresh random Docker names carrying an
unpredictable ownership marker and exact recovery labels. PostgreSQL uses a new
owned volume, no host port, and `network=none`. The tool proves that the target
contains no application tables before restore and never attaches the production
data volume.

The complete custom archive is restored with fail-fast, single-transaction,
no-owner, and no-ACL behavior. A successful command exit alone is insufficient.
After the full restore, deterministic queries against the restored database
produce:

- the ordered successful `flyway_schema_history` rows and their checksums;
- the exact restored application-table name set; and
- per-table row counts calculated from the restored target.

These facts describe the restored archive. They are not claimed to be a
separately observed production snapshot. They are written to a new immutable
restore-evidence document that binds the recovery-point manifest hash, dump
hash, source Git SHA, observed API/web/Caddy/PostgreSQL image IDs and OCI
revision facts, restored Flyway evidence, restore options, UTC start and
completion times, and the owned temporary-resource identities. The temporary
container and volume are removed only after exact ID/name and both ownership
labels are revalidated. Summary counts in that manifest are copied from and
cross-checked against the hashed restored-database evidence; production does
not require a hard-coded DEMO fixture cardinality. The target uses Docker's
`network=none`, revalidates the live network set immediately before evidence
capture, and creates no secret or additional network.

The rehearsal has no path that accepts a production container, production
volume, host bind mount, host port, remote Docker endpoint, `--clean` restore,
or caller-supplied Docker arguments.

## Schema and application rollback gate

This slice never emits `rollback-ready`. Valid restore evidence plus matching
runtime-image facts produce only `image-evidence-ready` with
`schema-compatibility-still-required`. A future gate must resolve or archive the
exact recorded Git object, derive that release's ordered Flyway versions and
checksums, and compare them with every restored Flyway row before a rollback
can be considered.

Starting an older application image against a database whose successful Flyway
history is newer, missing, failed, or checksum-incompatible is blocked. The
system must not treat an application-container restart as a database rollback.

ADR-047 does not restore in place and does not mutate the production PostgreSQL
volume. A real database rollback requires downtime, restoration into a fresh
owned volume, validation, and a later explicit promotion mechanism that keeps
the previous volume recoverable. The named-volume generation and promotion
contract is intentionally deferred; using `pg_restore --clean`, deleting the
current volume, or starting an old API against a newer schema is forbidden.

## Read-only retention plan

ADR-047 plans retention but deletes nothing. The deterministic planner groups
complete recovery points by UTC time and reports the union of:

- the newest verified point for each of the 14 most recent distinct UTC dates;
- the newest verified point for each of the 8 most recent distinct ISO UTC
  weeks; and
- the newest verified point for each of the 12 most recent distinct UTC
  months.

The newest image-evidence-ready point, partial directories, malformed or
unverified points, and unknown store entries are reported separately and are
never deletion candidates. The plan records the store identity, input manifest
hashes, selected identifiers, unselected identifiers, and estimated bytes. It
does not rename, quarantine, chmod, unlink, or recursively remove anything.

Deletion remains blocked until storage capacity, minimum survivor behavior,
the treatment of unverified artifacts, and at least one off-site or offline-copy
decision have been reviewed. Consequently a full HDD causes new publication to
fail safely; it does not trigger deletion of the only known-good backup.

## CI and local rehearsal boundary

CI validates the manifest and evidence schemas, exact configuration and command
allowlists, safe-path and ownership checks, atomic-publication state machine,
retention bucketing, and negative mutations. Negative cases include an inactive
mount, wrong UUID, same physical leaf device, ambiguous ancestry, symlinks and
path traversal, a partial bundle, truncated or altered payloads, mismatched
runtime revisions, a remote Docker endpoint, a non-empty or production restore
target, and any retention mutation.

A developer-machine rehearsal may use only a harness-owned temporary directory,
random Compose project, random containers, networks, volumes, images, and a
temporary database secret. It proves the real custom dump, interrupted staging,
digest rejection, full empty-target restore, Flyway/table evidence, and exact
cleanup. It never reads the root `.env`, modifies caller-owned Next artifacts,
starts production ingress, binds 80/443, trusts a certificate on the host, or
claims that the temporary directory is a separate physical disk.

CI and local rehearsal always retain `PENDING_BACKUP_DEVICE` and
`PENDING_OFFSITE_COPY`. Only the future Ubuntu host can clear the first state.
This decision does not provide a mechanism that clears the second.

## Operator inputs and secrets

No API key, provider account, cloud account, domain credential, or new secret is
needed to implement or run the local rehearsal.

Before live backup is enabled, the operator must provide on the home server:

1. an already installed, partitioned, formatted, and mounted separate HDD;
2. the exact absolute mount path for `WSR_BACKUP_MOUNT`;
3. the exact filesystem UUID for `WSR_BACKUP_FILESYSTEM_UUID`;
4. the observed filesystem type and capacity, its SATA/USB connection, and a
   simple supported block ancestry (one disk, at most one partition, plus one
   dm-crypt layer only when LUKS2 is selected);
5. the explicit `WSR_BACKUP_ENCRYPTION=luks2` or reduced-assurance
   `WSR_BACKUP_ENCRYPTION=none-demo-only` decision;
6. enough capacity for one complete staged recovery point plus retained points;
   and
7. if LUKS2 is selected, a local boot/unlock and recovery-key procedure.

An encryption passphrase or recovery key must be created and retained locally;
it is never sent in chat, stored in Git, or placed in a backup manifest. The
operator must review filesystem formatting and encryption before any disk-
destructive command is run. This repository does not perform those actions.

Backup scheduling is enabled only after the mounted-device and unlock behavior
has been observed across a real server reboot. The desired schedule and the
future off-site/offline-copy mechanism remain operator decisions; neither needs
to block implementation of the manual backup and rehearsal primitives.

## Consequences and remaining gates

- A complete logical database archive can be published atomically on a proven
  separate physical device without exposing that device to an application
  container.
- Evidence comes from restoring the archive into a fresh isolated PostgreSQL
  target, not from an adjacent query against the live database.
- A recovery point can bind restored schema evidence to exact runtime-image
  evidence without depending on DNS or a clean checkout. It still cannot prove
  rollback compatibility or promotion readiness in this slice.
- The same-host HDD reduces one failure mode but remains reachable by the same
  privileged operator and physical incident. `PENDING_OFFSITE_COPY` remains a
  release-risk disclosure.
- No automatic retention deletion or production restore exists. Capacity
  exhaustion fails closed, and production volume promotion requires a later
  explicit decision and rehearsal.
- PostgreSQL secrets and Caddy private material are outside the artifact. Their
  local recovery or re-issuance procedures remain required for full host-loss
  recovery.
