# ADR-056: Disposable offline Git Flow release-source handoff rehearsal

- Status: Accepted
- Date: 2026-08-31
- Depends on: ADR-046, ADR-048, ADR-051, ADR-055

## Context

The Ubuntu deployment boundary requires an exact release checkout whose clean
Git `HEAD` equals the configured 40-character image tag. ADR-048 also requires
the backup-recorded commit and every migration blob to exist in the local Git
object database; it deliberately never fetches a missing object. The repository
does not yet prove how committed source can leave this development computer and
become a complete, independently checked checkout on the future Ubuntu 24.04
home server.

The Codex conversation is not a deployment artifact. Copying a working
directory, a ZIP of visible files, or generated Docker images would lose or
blur the exact Git identity required by the deployment and recovery contracts.
Pushing the current feature branch would instead contact and mutate the remote
repository before repository visibility, authentication, release version, and
the required Git Flow reviews have been decided.

The current development checkout also contains one user-owned generated-file
change at `apps/web/next-env.d.ts`. A source-handoff rehearsal must neither
stage, restore, overwrite, nor silently package those working-tree bytes as a
release. It can prove only the exact committed `HEAD` object graph.

## Decision

### One disposable, offline source-handoff rehearsal

Add `scripts/verify-local-release-handoff.ps1`. The command has no publication
or remote mode and accepts no GitHub credential, remote ref, release version,
server path, Docker image, or user-selected commit. It resolves the repository
root and the exact current committed `HEAD`, then performs the complete
rehearsal in a harness-owned temporary directory.

The harness must print both of these status markers:

```text
NOT_RELEASED
REMOTE_NOT_CONTACTED
```

They are conclusions, not warnings that can be upgraded by an adjacent test.
The command does not call the configured origin, `ls-remote`, a hosting API, or
any network endpoint. It strips Git/hosting/SSH/proxy overrides, disables
credentials and hooks, injects `gc.auto=0`, `maintenance.auto=0`, and
`core.fsmonitor=false`, and permits only the local `file` protocol while
explicitly denying HTTP, HTTPS, SSH, and Git protocols. Local `clone`, bundle
creation/verification/unbundling, and `push` to the harness-owned simulated
bare remote are part of the rehearsal; they never address the source checkout's
configured origin. Every Git child receives `GIT_NO_LAZY_FETCH=1` and
`GIT_OPTIONAL_LOCKS=0` and a 120-second deadline. After a timeout the harness
kills the process tree and requires it to terminate within another five
seconds. It reads each child stream to completion and then rejects captured
stdout or stderr longer than 1,048,576 characters; this accepted-output check
is not a streaming memory cap. A timeout, termination failure, or oversized
completed capture fails closed.
The command does not create, move, merge, delete, or update a source or real
remote branch, tag, symbolic ref, config entry, index entry, or working-tree
file. Harness-owned temporary refs, commits, and tags exist only inside its
isolated repository and are removed with that repository.

The rehearsal:

1. validates the local Git executable, repository identity, exact lowercase
   40-character committed `HEAD`, and full non-shallow/non-promisor/non-partial-
   filter/non-alternate/non-HTTP-alternate/non-graft/non-replacement object
   source with strict no-dangling `fsck`; rejects executable local
   `core.fsmonitor`, `uploadpack.packobjectshook`, and
   `filter.*.(clean|smudge|process)` configuration; requires a canonical symbolic
   `feature/*` branch, coherent local and cached `main`, cached
   `origin/main` ancestry into local `develop`, cached `origin/develop` that can
   fast-forward to local `develop`, and a feature candidate at least one commit
   beyond that local `develop`;
2. records custody of the source commit/ref state, tracked status, and the
   user-owned `apps/web/next-env.d.ts` bytes before creating any artifact;
3. seeds temporary cached `main` and `develop` refs, fast-forwards the temporary
   integration backlog, merges the exact feature through `--no-ff`, creates one
   empty rehearsal-only release commit, merges the temporary release through
   `--no-ff` into both temporary `main` and `develop`, and creates one annotated
   rehearsal tag;
4. proves the tagged temporary main merge and temporary develop merge have the
   exact same source tree as the committed feature `HEAD`, while retaining the
   required reviewable Git Flow parent graph;
5. creates a complete, no-prerequisite, tag-only Git bundle plus one strict JSON
   manifest and a separate SHA-256 receipt;
6. verifies bundle structure, annotated-tag identity, manifest/receipt
   consistency, byte count, and SHA-256, and rejects both a byte-flipped bundle
   with the original metadata and a truncated bundle whose byte count, digest,
   manifest, and receipt were recomputed; each negative must match its exact
   expected digest or structural-import failure class rather than passing for
   an unrelated exception;
7. imports only from the verified local bundle into a new isolated repository
   without contacting a remote;
8. proves the imported annotated tag resolves to the exact rehearsal release
   commit and source tree, with a clean checkout, complete object connectivity
   through `git fsck --full --strict --no-dangling`, detached `HEAD`, zero Git
   remotes, and the required deployment, application, migration, and
   verification paths; and
9. removes only its owned bundle, manifest, receipt, clone, and temporary
   directory, then verifies that source commit/ref/status and
   `apps/web/next-env.d.ts` custody are unchanged.

Alternate, HTTP-alternate, and graft paths are resolved through
`git rev-parse --git-path` rather than assuming a checkout-local `.git`
directory, so linked-worktree common-directory state cannot bypass the source
or imported-repository completeness check.

The temporary root name is fixed to `wsr-release-handoff-<24-hex>` directly
under the resolved system temporary parent. Before mutation it receives a
fresh, flushed `.wsr-release-handoff-owner` file containing an independent
48-hex token plus LF. Cleanup refuses any other parent/name, reparse-point root
or marker, missing/changed marker, or malformed token; only the exact owned root
may be recursively removed.

The bundle contains the committed version of `apps/web/next-env.d.ts` that
belongs to `HEAD`. The user's modified working-tree version is deliberately
excluded from the handoff artifact and preserved byte-for-byte on the source
computer. Every other tracked or Git-visible untracked source change that would
make the committed handoff ambiguous fails closed rather than being copied.

The artifact names are exact:

```text
wall-street-receipts-<mainReleaseCommit>.bundle
wall-street-receipts-<mainReleaseCommit>.bundle.sha256
manifest.json
```

`manifest.json` is compressed, canonical, printable-ASCII JSON with one final
LF, no BOM, and a size from 2 through 8,192 bytes. It contains exactly these 22
ordered, unique fields:

```text
schemaVersion
project
releaseStatus
networkStatus
sourceBranch
sourceCommit
sourceTree
cachedOriginMain
cachedOriginDevelop
localDevelop
featureAheadCount
integrationCommit
releasePreparationCommit
mainReleaseCommit
developReleaseCommit
annotatedTag
tagObject
bundleRef
bundleFile
bundleBytes
bundleSha256
bundlePrerequisiteCount
```

`schemaVersion`, `featureAheadCount`, `bundleBytes`, and
`bundlePrerequisiteCount` are JSON Numbers that must parse exactly as signed
64-bit integers. The other 18 fields are JSON Strings; numeric-looking Git
identities or status values are never accepted under another JSON type.

The fixed values are schema `1`, project `wall-street-receipts`, release status
`NOT_RELEASED`, network status `REMOTE_NOT_CONTACTED`, and prerequisite count
`0`. The random temporary release branch and annotated tag are respectively
`release/0.0.0-rehearsal.<24-hex>` and
`v0.0.0-rehearsal.<24-hex>`. The bundle advertises exactly one ref, the
annotated tag object at `refs/tags/<annotatedTag>`. The adjacent checksum
receipt is exactly lowercase SHA-256, one space, `*<bundleFile>`, and one LF.
The manifest binds the cached/local backlog, all four temporary Git Flow
commits, source tree, tag object, bundle name, byte count, and digest; the
harness separately proves every simulated commit has the same source tree.
Artifact acceptance uses a new empty bare repository to verify and actually
unbundle the bytes, restore only `bundleRef` to `tagObject`, require an annotated
tag that peels to `mainReleaseCommit` and `sourceTree`, and run full strict no-
dangling `fsck`. Structural verification alone is not accepted.

### Integrity boundary

The checksum receipt hashes only the named bundle bytes. It does not hash
`manifest.json` or authenticate the complete three-file set. Canonical parsing
and bundle cross-checks reject malformed metadata and inconsistencies in the
bundle/tag/source fields they actually compare. The later Ubuntu import does
not recompute every manifest-only cached/local/generated Git Flow identity,
their complete parent graph/tree equality, or `featureAheadCount`; a
syntactically valid change to those values can pass. Preserve an independently
reviewed manifest and commit/digest, or add a separately designed signature,
before relying on those fields. A same-media checksum is not an authenticity
proof in any case: an attacker able to rewrite the bundle can rewrite the
manifest and receipt too. ADR-056 adds no signing key, detached signature,
transparency log, trusted timestamp, protected remote ref, or independently
held digest. Those require a separate trust and key-custody decision.

The bundle is source custody only. It does not contain Docker images, database
data, secrets, Caddy certificate state, dependency caches, build output, or the
Codex conversation. A successful rehearsal does not prove reproducible binary
or container bytes, an image scan, base-image digest custody, offline image
restore, multi-architecture output, or rollback readiness.

### Future offline Ubuntu import

The disposable command deletes its artifacts and therefore does not publish a
release package. A later explicitly approved exporter may retain the same three
files on operator-selected removable media, but their manifest still says
`NOT_RELEASED`; importing them proves only candidate source custody and must not
start a production publication.

The complete Ubuntu command block in `deploy/home-server/README.md` strictly
parses all 22 JSON fields with Python 3, verifies the same-media receipt, byte
count, SHA-256, zero-prerequisite single-tag bundle, initializes an empty local
repository, unbundles it, restores only the recorded tag ref, and detaches at
`mainReleaseCommit`. It then requires the annotated tag object, exact source
tree, clean status, no shallow/partial/promisor/alternate/replacement state,
full strict `git fsck`, and every deployment path checked by the PowerShell
harness. Only the read-only server-fact collector and host preflight follow.

Do not copy the development computer's ignored `.env`, root `.env`,
`.env.production`, private keys, tokens, or modified
`apps/web/next-env.d.ts` working-tree bytes onto the server. The bundle contains
only the committed declaration from `sourceCommit`.

### Later GitHub alternative

GitHub can later replace removable-media source transfer, but it is outside the
offline rehearsal. Before any remote operation, the operator must explicitly
authorize network use and provide these non-secret decisions:

1. repository visibility (`public` or `private`);
2. the release version, with `v0.1.0-rc.1` recommended for the first candidate;
3. confirmation that a fresh fetch and review of remote divergence may run; and
4. confirmation that GitHub authentication is configured locally on the
   development computer and, for a private server clone, locally on the future
   Ubuntu server.

Tokens, SSH private keys, recovery codes, passwords, and credential-helper
contents must never be sent in chat or committed to Git. A public repository
can be cloned without a server credential. A private repository requires a
server-local least-privilege credential or deploy key whose storage and
rotation are separately reviewed.

Only after a fresh fetch and hosted CI review may the feature branch be pushed
and merged by pull request into `develop`. The Git Flow release path then creates
`release/0.1.0-rc.1` from the reviewed `develop`, performs release-only
stabilization, merges through review into both `main` and `develop`, and places
the annotated `v0.1.0-rc.1` tag on the exact `main` release merge. None of those
feature push, pull request, release branch, merge, tag, or tag-push actions is
performed or authorized by ADR-056.

A future Ubuntu GitHub checkout must be a full clone, not `--depth`,
`--shallow-*`, `--filter`, sparse promisor, or another partial clone. It must
detach at the reviewed annotated release tag's exact commit, prove a clean
status and complete object graph, and then run the same server-fact and
deployment preflights. GitHub transport does not by itself prove source
signature or reproducible image bytes.

## Verification plan

From the current development repository root, with only local Git and
PowerShell 7 available:

```powershell
pwsh -NoProfile -File ./scripts/verify-local-release-handoff.ps1
```

Acceptance requires all bundle, manifest, receipt, corruption-rejection,
offline-clone, full-object, exact-commit, required-path, no-source-mutation, and
owned-cleanup checks to pass together. The final output must still say
`NOT_RELEASED` and `REMOTE_NOT_CONTACTED`. No PASS is claimed until the observed
run is recorded in `IMPLEMENTATION_LOG.md`.

This rehearsal needs no API key, domain, home-server access, Docker daemon,
GitHub login, network authorization, release version, repository visibility,
SEC contact email, database password, token, or private key.

## Consequences and next work

- The project can prove now, without the future server, that one exact committed
  source graph can survive an offline bundle/import boundary.
- The exact release checkout assumed by ADR-046, ADR-048, and ADR-051 gains a
  tested source-transfer mechanism without treating this Codex conversation as
  deployment state.
- The rehearsal remains deliberately disposable and cannot be used as evidence
  that a release, remote branch, tag, GitHub workflow, or Ubuntu deployment
  exists.
- Actual remote Git Flow work waits for explicit network authorization, a fresh
  fetch, repository visibility and version decisions, and local GitHub
  authentication. Actual offline export waits for an approved release commit
  and a separately reviewed persistent destination.
- Docker image custody, reproducible builds, source authenticity/signing,
  external digest custody, and the real Ubuntu server-fact/deployment rehearsal
  remain later work.
