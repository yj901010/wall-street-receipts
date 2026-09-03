# ADR-049: Hash-bound read-only generation promotion plan

- Status: Accepted
- Date: 2026-08-26
- Depends on: ADR-046, ADR-047, ADR-048

## Context

ADR-048 proves exact equality between the backup-recorded Git migration tree,
the migration resources and Flyway checksums packaged in the exact API image,
and the immutable restored database evidence. It does not prove that the same
release is still running, define a crash-consistent volume transition, preserve
the previous generation, or authorize promotion.

Docker named volumes have no atomic rename or swap. The current Compose and
recovery contracts also intentionally bind the production database to the
legacy `wall-street-receipts-home_postgres-data` volume. The ADR-047 rehearsal
volume is disposable and initialized with `POSTGRES_HOST_AUTH_METHOD=trust`; it
must never be attached to a production network or reclassified as a candidate.

Implementing a live switch before generation-aware backup manifests, protected
volume indirection, a host-persistent transition journal, and a deployment-wide
lock would make backup and recovery ambiguous. This development computer is
not the future Ubuntu home server, so no real service or storage transition can
be responsibly exercised here.

## Decision

### Fixed read-only plan action

Add exactly one production-data-read-only action:

```bash
sudo bash deploy/home-server/recovery-production.sh -- promotion-plan-latest
```

The action accepts no backup ID, generation, volume, image, Git ref, path,
Compose option, Docker option, or approval token. It reruns the ADR-048 gate for
the latest complete backup and latest immutable evidence v2. It then requires:

1. exactly one healthy Compose-labeled PostgreSQL, API, web, and production
   Caddy container;
2. the current PostgreSQL release label to equal the backup-recorded Git SHA;
3. every running image reference, full image ID, and OCI revision to equal the
   backup manifest; and
4. the current PostgreSQL image and legacy source volume to remain exactly the
   reviewed ADR-047 identities.

Any missing, stopped, unhealthy, duplicate, truncated, changed, or ambiguous
resource fails closed with a stable blocked reason. The action only inspects
production resources. ADR-048 may create and remove its isolated no-network
image inspector, but neither action writes production data or changes a
production container, service, network, volume, selector, or transition state.

Because the shared mutation lock is intentionally not implemented yet, the
action performs a second observation before success. It reselects and strictly
revalidates the latest backup and restore evidence, rehashes their content, and
reinspects all four full container identities. Any change visible between the
two observations blocks the plan. The canonical observation start and
completion UTC timestamps bound what was seen; they do not create a lock,
eliminate the residual post-read race, or authorize later reuse without
revalidation.

### Canonical plan

On exact agreement, the action emits an ordered canonical LF record and its
SHA-256. The record binds the backup and restore-evidence IDs, backup manifest,
archive, restore manifest and database-evidence SHA-256 values, exact Git
commit, Flyway version/migration count, observation interval, current full
container IDs, exact image IDs, legacy source volume, and a deterministic
planned generation name derived from the complete backup and evidence IDs. It
explicitly records that the candidate was not created and that activation is
blocked.

The plan also records every prerequisite that a later live implementation must
satisfy:

- backup manifest v2 with generation and authoritative active-volume binding;
- strict legacy-v1 and generation-v2 validation branches;
- protected external-volume indirection instead of an arbitrary volume name;
- one primary-host deployment/recovery lock shared by all mutating operations;
- a root-owned durable journal with fsynced intent and completion records;
- production password-file/SCRAM initialization for a new candidate;
- offline custody and verification of exact release artifacts;
- capacity for two complete generations plus restore headroom;
- exact API/web/Caddy environment, network, mount, and port validation;
- explicit downtime, probation, and write/RPO decisions; and
- continued source-volume preservation through probation.

The hash is evidence of one observed plan, not an approval. A future approval
must bind the exact transition UUID and plan hash after all prerequisites are
implemented and revalidated immediately before downtime.

### Future crash contract

ADR-049 defines a pure fail-closed state table for future implementation:

```text
steady
  -> candidate-preparing
  -> candidate-sealed-offline
  -> approval-recorded
  -> quiesce-intent
  -> source-stopped
  -> selector-switch-intent
  -> target-starting
  -> target-health-verified
  -> probation
  -> finalized
```

Before downtime, reviewed abandonment or abort returns to `steady` without
deleting either generation. The explicit rollback branch can start from
`source-stopped`, `selector-switch-intent`, `target-starting`,
`target-health-verified`, or `probation`:

```text
<post-source-stop state>
  -> rollback-intent
  -> target-stopped-for-rollback
  -> source-selector-restored
  -> source-restarting
  -> rolled-back
```

Unknown, replayed, or skipped transitions are invalid. Target start cannot enter
probation until target health is explicitly verified. Before source stop, the
source remains authoritative and an abort path is explicit. Between source
stop and probation, ambiguity requires an operator and never auto-selects a
generation, but every such state has an exact source-rollback path which does
not pass through probation. During probation the operator must explicitly
continue the exact target or start the exact rollback branch. Interrupted
rollback also requires operator recovery. Neither volume may be automatically
deleted in any interruption state.

This is a crash-consistent controlled-downtime model, not an atomic Docker
volume swap and not zero downtime. A later writer must fsync every intent before
its mutation and fsync completion after it.

## Consequences

- Exact ADR-048 evidence is no longer confused with the currently running
  release. Drift blocks the plan.
- A reproducible plan exists without creating a long-lived candidate or
  touching this development computer's Docker production state.
- `PROMOTION_ACTIVATION` and `ROLLBACK_READINESS` remain blocked. The action
  never emits `promotion-ready` or `rollback-ready`.
- The legacy backup manifest remains valid while the legacy volume is active.
  Manifest v2 is deliberately deferred, but it is a mandatory prerequisite in
  the plan and must ship in the same slice as the first real generation switch.
- The existing trust-auth restore rehearsal remains disposable-only and cannot
  be promoted.
- API/web/Caddy runtime topology is not certified in this slice; exact
  environment/network/mount/port validation is hash-bound as a mandatory live
  activation prerequisite rather than inferred from image identity and health.
- A same-server HDD is still neither off-site nor offline; every result keeps
  `PENDING_OFFSITE_COPY` explicit.

No API key, provider account, domain, ACME email, router credential, HDD fact,
or new secret is needed for this read-only contract and local test. Before any
live transition implementation or Ubuntu rehearsal, the operator must provide
the actual server paths and storage facts, available capacity for two complete
generations, an acceptable downtime window, a probation duration, and a write
freeze/RPO policy. Secret values remain local to the server and must never be
sent in chat or committed.
