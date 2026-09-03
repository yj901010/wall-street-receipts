# ADR-048: Exact release Git-object and Flyway schema compatibility gate

- Status: Accepted
- Date: 2026-08-26
- Depends on: ADR-046, ADR-047

## Context

ADR-047 proves that one logical PostgreSQL backup can be restored into a fresh,
isolated target and binds that observation to release-image identities. It does
not prove that the restored Flyway history is exactly the history expected by
the recorded application image and recorded source commit.

The v1 database evidence contained only Flyway installed rank, version,
checksum, and success. That cannot detect a renamed script, a changed
description, a different migration type, or a repeatable migration. A Git SHA
or OCI revision label alone is also not sufficient: the exact local object,
packaged resource bytes, Flyway checksum, and restored history must agree.

## Decision

### Evidence v2

New restore rehearsals emit database-evidence v2. Every Flyway row binds:

1. contiguous installed rank;
2. contiguous positive integer version;
3. UTF-8-hex description;
4. exact `SQL` type;
5. UTF-8-hex script name;
6. signed 32-bit checksum; and
7. successful execution.

Only flat, regular, versioned `Vn__lower_snake_name.sql` migrations are admitted
in this initial gate. Repeatable, undo, callback, Java, nested, symlink,
duplicate, failed, missing-middle, and noncanonical migrations fail closed.
Legacy v1 evidence remains distinguishable as prior restore proof but can never
authorize exact schema compatibility; the operator must run a new rehearsal.

### Exact API-image inventory

The API JAR exposes one reserved offline command:

```text
--wsr-release-schema-inventory
```

It runs before Spring Boot starts, scans the migration resources actually
packaged in that runtime, requires strict UTF-8 nonempty bytes, and uses the
same pinned Flyway 11.7.2 `ChecksumCalculator` shipped in the image. Its
canonical LF output includes rank, version, description, type, script,
checksum, raw SHA-256, and byte count. Unsupported content exits nonzero; there
is no fallback checksum implementation.

### Fixed production action

`recovery-production.sh` adds one fixed action:

```bash
sudo bash deploy/home-server/recovery-production.sh -- schema-check-latest
```

The action accepts no commit, ref, path, image, Docker option, or backup ID. It
always revalidates the latest complete backup and its latest immutable restore
evidence before comparison.

It resolves only the backup-recorded lowercase 40-character commit from the
fixed deployment repository. It does not read `HEAD` or the working tree and
never invokes checkout, reset, switch, worktree mutation, fetch, or pull.
Inherited `GIT_*` overrides are removed, replacement objects and optional Git
locks are disabled, partial/promisor and alternate object stores are rejected,
and missing objects remain missing.

The exact recorded API image ID must already exist locally. The action creates
one random label-owned inspector with `network=none`, a read-only root,
all capabilities dropped, no-new-privileges, no mounts, and no published ports.
It runs only the inventory command and must remove that exact container before
success is possible. It never connects the API to PostgreSQL and never starts
or changes a production volume.

The gate then requires all three views to match exactly:

1. every migration blob in the exact Git commit tree;
2. every migration resource and Flyway tuple in the exact API image; and
3. every ordered Flyway tuple in restored database-evidence v2.

Only byte-for-byte resources and tuple-for-tuple history equality exit zero.
An older/newer database, older/newer application, checksum drift, rename,
description/type change, missing resource, unsupported migration, absent Git
object, absent image, legacy evidence, or cleanup failure is blocked with a
stable reason.

## Consequences

- A dirty current checkout cannot change a result; the recorded commit tree is
  the only source view.
- A missing local commit or image is an availability failure, never permission
  to fetch or substitute current state.
- Existing v1 restore evidence is not silently upgraded or reinterpreted.
- Future Flyway upgrades must deliberately update the pinned producer and
  consumer version contract and rerun the mutation matrix.
- Repeatable migrations require a separate append-only historical design and
  remain unsupported.

Even exact compatibility is not rollback readiness. The command continues to
emit `ROLLBACK_READINESS|blocked-promotion-and-artifact-gates-not-implemented`.
Fresh-volume promotion, previous-volume preservation, release-image archival or
signature verification, secret/TLS recovery, off-site copying, and a real
Ubuntu/HDD rehearsal remain separate gates.

No API key, provider account, domain, ACME email, router credential, HDD fact,
or new secret is required to implement or test ADR-048.
