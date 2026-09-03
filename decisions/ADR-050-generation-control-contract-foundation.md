# ADR-050: Generation-control contract foundation

- Status: Accepted
- Date: 2026-08-26
- Depends on: ADR-046, ADR-047, ADR-048, ADR-049

## Context

ADR-049 emits a hash-bound, production-data-read-only promotion plan and
defines a conservative business-state model. It deliberately does not provide
the host coordination, versioned generation documents, or durable transition
evidence that a future volume switch would require.

The existing deployment and recovery wrappers can otherwise overlap. The
ADR-047 recovery lock is stored on the removable backup device and protects
only selected backup and rehearsal operations. It cannot be the one
primary-host lock for deployment, backup, evidence inspection, and a future
generation transition. A file-name-based lock is also unsafe if the lock file
can be replaced: Linux `flock` follows the opened inode, not a mutable path.

The existing recovery durability helper used `sync -f FILE`. On GNU
coreutils, `-f` requests a filesystem-wide `syncfs(2)` operation. The exact
per-path operation corresponding to the required file or directory
`fsync(2)` barrier is `sync FILE` without `-f`.

This development computer is not the future Ubuntu home server. A format and
coordination foundation can be tested here, but inventing a live selector,
candidate volume, server storage layout, or recovery decision would turn
unverified assumptions into production state.

## Decision

### Fixed primary-host operation lock

Reserve exactly this root-owned control directory and permanent lock file:

```text
/var/lib/wall-street-receipts/generation-control
/var/lib/wall-street-receipts/generation-control/operation.lock
```

The directory must be a real, non-symlink, root-owned mode-0700 directory.
`operation.lock` must be a real, non-symlink, root-owned, single-link mode-0600
regular file. It is preprovisioned once and must never be truncated, unlinked,
renamed, or replaced during ordinary operation.

The lock implementation opens the fixed file, compares the path's device and
inode with the opened descriptor through `/proc`, acquires a non-blocking
`flock`, and revalidates the path and descriptor identity. The descriptor stays
open in the parent shell for the complete child operation. The Compose wrapper
waits as that parent while Docker Compose runs; it must not replace itself with
`exec`, because brace-allocated lock descriptors are not reliably inherited
across that boundary. The lock is advisory: it coordinates only entry points
which participate in this contract.

Production entry points use these modes:

- deployment contract/publish preflight, `ps`, and `logs`: shared;
- recovery production preflight, recovery `preflight`, `status`, and
  `retention-plan`: shared;
- deployment `build`, `up`, `stop`, and `down`: exclusive; and
- recovery `create`, `rehearse-latest`, `schema-check-latest`, and
  `promotion-plan-latest`: exclusive.

Host-only discovery remains available before the control root is provisioned.
Where the ADR-047 backup-device lock is also needed, the primary-host lock is
always acquired first. No reverse acquisition order is allowed.

The Compose wrapper's child contract preflight acquires and releases its own
shared lock before the parent acquires the requested action lock; this avoids a
self-deadlock when the action needs exclusivity. No generation selector writer
exists in this slice, so that boundary does not authorize reuse of a live
generation observation. A future transition or generation-aware deployment
must revalidate selector and runtime topology while its exclusive parent lock
is already held.

### Exact durability barrier

Generation-control publication and the existing backup publication contract
use GNU `sync -- PATH` as the per-file or per-directory `fsync(2)` boundary.
The required future publication order is:

1. create one unpredictable, single-link mode-0600 staging file in the exact
   destination directory;
2. write and close the complete canonical bytes;
3. revalidate the staged file's bytes, metadata, inode, and filesystem;
4. run `sync -- STAGED_FILE` and stop before mutation if it fails;
5. atomically rename on the same filesystem, without overwrite for immutable
   documents;
6. prove that the destination is the exact staged inode;
7. run `sync -- DESTINATION_PARENT`; and
8. reread and revalidate the published bytes before reporting success.

An atomic rename provides old-or-new visibility; it does not by itself prove
power-loss durability. A failed file sync, rename, parent-directory sync, or
final reread is never reported as success.

The existing backup and restore-evidence publishers also set their staging
directory to final mode 0500 before syncing that directory and renaming it.
Syncing only before the final chmod would not durably order the sealed
directory metadata before publication.

This ADR defines and tests the contract but exposes no production selector,
manifest, or journal writer action. No production document is provisioned by
the repository or by an existing deployment/recovery command.

### Canonical document envelope

All four document contracts are ordered ASCII `key=value` records. They have:

- fixed schema-specific key order and exact field count;
- LF separators, exactly one final LF, and no CR or UTF-8 BOM;
- no blank lines, comments, spaces, tabs, quoting, interpolation, or extra
  keys;
- a maximum encoded size of 32,768 bytes;
- closed value alphabets and exact identifier, digest, revision, UUID, and UTC
  validators; and
- byte-for-byte equality with their canonical rerendering before a digest is
  trusted.

The files are data and are never passed to `source`, `eval`, or a shell. A
duplicate, missing, unknown, reordered, oversized, non-LF, or otherwise
non-canonical record fails closed.

Every associative-map and indexed-field-array name is identifier- and type-
checked before Bash creates a nameref. The generic ordered parser also accepts
only the four fixed schema/validator/renderer tuples, so a caller-supplied name
cannot redirect nameref resolution or command dispatch.

### Active selector v1

Selector v1 binds one fixed-width revision of an active decision to the
project, active generation ID, exact generation-manifest SHA-256, active Docker
volume name, predecessor selector SHA-256, change kind, transition UUID,
promotion plan SHA-256, and canonical UTC write time. A future selector writer
must additionally prove revision succession against the exact predecessor;
this foundation does not perform that live comparison.

The only change kinds are `legacy-bootstrap`, `promotion`, and `rollback`.
`legacy-bootstrap` is revision one with the all-zero predecessor digest and
literal `bootstrap` transition and plan markers. Promotion and rollback must
bind a nonzero predecessor digest. The active volume is either the exact
reviewed legacy volume or the exact derived generation name; arbitrary paths,
Docker arguments, and arbitrary volume names are invalid.

The reserved selector path is:

```text
/var/lib/wall-street-receipts/generation-control/active.selector
```

ADR-050 does not create that file, make Compose consume it, or declare any
selector revision active. Its relationship validator additionally requires the
selector's generation ID, manifest digest, and volume to match one validated
immutable generation manifest exactly, and the selector write cannot predate
that manifest's seal time.

### Immutable generation manifest v1

Generation manifest v1 describes either one reviewed legacy import or one
restored candidate. It binds the generation and volume identities, volume
creation time and label digest, Git commit, PostgreSQL image identity,
production password-file/SCRAM authentication contract, creation and seal
times, and a kind-specific observed state. A restored candidate additionally
requires the exact source backup and restore-evidence IDs and content digests,
the ADR-049 plan digest, and `sealed-offline`. A legacy import uses
`observed-active-at-import` and records each unavailable historic source or
plan fact as the literal `unavailable`; it never invents those facts or claims
that the active legacy volume was sealed offline.

A restored candidate's generation ID is deterministically derived from its
complete source backup and restore-evidence IDs using the exact ADR-049 naming
rule, and its volume name must equal that generation ID. A legacy import must
use the exact reviewed legacy volume. The contract is immutable and rejects a
mutable or partially sealed generation. ADR-050 does not import the legacy
volume, restore a candidate, inspect a live volume, or publish a generation
manifest.

### Backup generation binding v2

Backup generation binding v2 is a closed adjunct contract for a future backup
manifest v2 integration. It binds one backup ID to the source generation kind,
generation-manifest digest, exact volume identity and label digest, the active
selector revision and digest, and generation lock-contract version observed
during capture.

The backup relationship validator first applies that selector/manifest check,
then requires the binding to agree with both documents exactly. The existing
ADR-047 `create` output remains the legacy backup-manifest contract in this
slice; it does not emit or claim a generation binding v2 document.

### Hash-chained transition journal v1

Journal v1 defines immutable intent/completion record pairs. Every record
binds its transition and operation UUIDs, fixed-width sequence, record kind,
state transition, ADR-049 plan digest, exact source and target generation,
manifest and volume identities, selector-before and selector-after revisions
and digests, previous-record SHA-256, and canonical UTC observation time.
Source and target generations must differ. Only `start-target` and
`restore-source-selector` may change selector evidence, and each must advance
the selector by exactly one revision to a different digest; every other event
must preserve both selector revision and digest.

The first record uses the all-zero predecessor digest. Later records must be
contiguous and bind the exact preceding record bytes. One intent must be
followed by its exact completion before another intent can begin. Transition,
plan, source, and target identities cannot drift within a chain. Replayed,
skipped, concurrent, malformed, or conflicting records fail closed. Operation
UUIDs cannot be reused, observation time cannot move backwards, and each new
intent's selector-before evidence must equal the preceding completion's
selector-after evidence.

The journal classifier reports only a stable state or a conservative manual
recovery directive. A pending intent never proves whether its corresponding
external mutation happened. Recovery must later reconcile the exact selector,
containers, volumes, and database evidence while holding the exclusive lock.
ADR-050 neither creates a journal nor automatically continues, rolls back, or
selects a generation.

### Shell security boundary

This is a root-only cooperative shell boundary. Private mode-0700 directories,
fixed paths, non-symlink checks, single-link files, path-to-descriptor inode
checks, same-filesystem staging, and strict parsers protect against accidental
misconfiguration and unprivileged path substitution.

They do not protect against a malicious or concurrently mutating root process.
Bash and path-based coreutils cannot provide an `openat2`/directory-descriptor
security boundary against that threat. Raw root Docker commands and manual
file edits can also bypass the advisory lock. If protection from malicious
root becomes a requirement, a separately reviewed native helper using
directory descriptors, `openat2`, `renameat2`, and explicit `fsync` is needed.

## Explicitly excluded

ADR-050 provides none of the following:

- live selector provisioning or selection;
- Compose external-volume indirection;
- candidate volume creation, restore, sealing, or production authentication;
- service stop/start, activation, probation, finalization, or rollback action;
- Docker container, network, image, or volume mutation from generation-state
  code;
- automatic crash recovery or automatic database-generation choice; or
- source or target volume deletion.

The current Compose `restart: unless-stopped` behavior is not reconciled with a
nonterminal transition. A Docker-daemon or host restart can therefore bypass a
future transition journal unless restart policy and boot ordering are designed
and rehearsed. That remains an activation blocker, as do exact runtime topology
validation, release-artifact custody, two-generation capacity, write freeze
and RPO, acceptable downtime, and probation policy.

## Future Ubuntu provisioning

Do not provision generation documents on the development computer. On the
future Ubuntu server, and only after confirming the path is on the intended
local persistent filesystem, create the fixed control directory and permanent
lock once:

```bash
sudo install -d -m 0700 -o root -g root \
  /var/lib/wall-street-receipts/generation-control
sudo bash -c 'set -euo pipefail; umask 077; set -o noclobber; \
  : > /var/lib/wall-street-receipts/generation-control/operation.lock'
sudo chmod 0600 \
  /var/lib/wall-street-receipts/generation-control/operation.lock
```

The no-clobber creation is intentionally one-time. If the lock path already
exists, stop and inspect it; never overwrite it to make the command pass. The
control root must be a local persistent filesystem suitable for Linux
`flock` and file/directory `fsync`, not NFS, CIFS, FUSE, a temporary filesystem,
or the removable backup device. Do not create `active.selector`, a generation
manifest, or a journal as part of this provisioning step.

No API key, provider account, domain, ACME email, router credential, or secret
is needed for this contract and its local verification. Before any live
activation design or Ubuntu transition rehearsal, the operator must provide
the actual server filesystem and Docker storage facts, capacity for two full
generations plus restore headroom, acceptable downtime, probation duration,
and a write-freeze/RPO policy. Secret values remain local and must never be
sent in chat or committed.

## Consequences

- Deployment and recovery wrappers can coordinate through one verified
  primary-host inode once the future server is provisioned.
- Recovery publication now uses the exact GNU per-path `fsync` invocation
  rather than describing `syncfs` as file `fsync`.
- Selector, generation, backup-binding, and journal bytes have closed schemas
  that can be mutation-tested without a live server.
- Hashes and journal directives remain evidence and classification only; they
  are not approval or readiness signals.
- Activation and rollback readiness remain blocked.
