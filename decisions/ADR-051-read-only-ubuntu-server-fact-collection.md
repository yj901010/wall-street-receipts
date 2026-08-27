# ADR-051: Read-only Ubuntu server fact collection

- Status: Accepted
- Date: 2026-08-27
- Depends on: ADR-046, ADR-047, ADR-048, ADR-049, ADR-050

## Context

The development computer is not the future home server. The only currently
observed operator fact is that the target is expected to run Ubuntu 24.04 LTS;
its architecture, memory, storage topology, Docker data root, existing
production volume, backup device, port ownership, and boot behavior have not
been observed. Those values affect capacity, recovery, and generation-switch
design and must not be inferred from the development computer.

The existing ADR-046 host preflight evaluates a minimum deployment baseline,
and the ADR-047 recovery preflight validates an already configured backup
device. Neither emits a bounded, reviewable handoff record for a server that
has not yet been configured. Asking the operator to paste broad command output
would risk disclosing addresses, host names, device serials, environment
variables, Docker credentials, or secret material.

## Decision

### Fixed read-only command surface

Add `deploy/home-server/server-facts.sh` with this closed interface:

```text
server-facts.sh [--backup-mount ABSOLUTE_PATH] [--output stdout]
```

No argument is valid and records that a backup mount has not been selected.
`--backup-mount` observes only the named path; it does not create, resolve by
guessing, mount, format, unlock, label, or repair a device. `stdout` is the
only output destination. The command never writes a report, changes the host,
installs a package, invokes `sudo`, starts a container, or contacts an external
service.

Exact-mount observation prevents an ordinary unmounted directory from being
reported as the selected mount. It does not prove separate physical media: a
bind mount or a separate partition on the same disk can still be an exact
mount. ADR-047's later block-ancestry validation and the operator's physical-
topology attestation remain required.

The process ignores inherited Docker, Compose, WSR, proxy, and credential
overrides. Docker discovery is pinned to the local rootful Unix socket at
`/var/run/docker.sock`; a caller cannot redirect the collector to a remote
daemon. The only Docker volume queried is the fixed legacy production name
`wall-street-receipts-home_postgres-data`. Absence is an observed state, not an
instruction to create the volume.

### Canonical bounded report

The collector emits one fixed-order ASCII `key=value` report with exactly one
final LF and a 32,768-byte maximum. Values use closed alphabets and bounded
lengths. Missing commands, permissions, paths, and resources are represented
explicitly as `not-installed`, `not-observed`, `not-present`,
`permission-denied`, or another schema-defined state; they are never replaced
with zero or a guessed value.

The report covers only facts needed to plan the next gate:

- Ubuntu release, kernel class, machine architecture, logical CPU count, and
  total memory;
- Docker CLI/daemon/Compose availability, local-daemon reachability,
  DockerRootDir, storage driver, cgroup mode, and rootless/user-namespace
  status;
- filesystem type, device relationship, selected security mount options, and
  free/total capacity for the host root, fixed generation-control root,
  DockerRootDir, fixed legacy volume, and optional backup mount;
- presence and bounded identity evidence for the fixed legacy volume without
  reading database contents or arbitrary volume metadata;
- occupied/free observations for TCP 80 and 443 without printing listening
  addresses, PIDs, or arbitrary process command lines; and
- boot/restart-policy facts required to evaluate the unresolved ADR-050
  automatic-restart boundary.

Device sources, filesystem UUIDs, block-device serials, and other stable host
identifiers are either omitted or represented only by a SHA-256 digest when a
relationship comparison requires them. The report never queries or separately
emits a host name or current account, and never includes a LAN or public
address, MAC address, raw serial/UUID, environment value, Docker
registry/config content, secret content, or application data. Exact local
Docker, volume, and operator-supplied backup paths are planning facts and may
contain operator-chosen path components; the operator reviews them before
sharing the report. The collector does not enumerate arbitrary containers,
images, volumes, networks, users, sockets, or files.

### Review gate, not readiness

A successfully rendered report means only that the bounded observation
completed. It never emits `READY`, authorizes provisioning, or changes an
ADR-050 generation state. Its bootstrap and restart-policy results remain
`REVIEW_REQUIRED` until the report is reviewed together with the real server
and the operator has separately decided:

1. acceptable maintenance downtime;
2. probation duration;
3. write-freeze behavior and recovery-point objective; and
4. the selected, independently mounted backup device and capacity for two
   database generations plus restore/build headroom.

Collection may succeed while individual facts are unavailable. Invalid CLI
usage returns 64 and a report-contract or safety invariant failure returns 70.
Missing host prerequisites are recorded in the report rather than converted
into invented facts or a mutation attempt.

### Verification boundary

CI statically guards the closed command surface, field order, output bound,
environment scrubbing, fixed local Docker socket and fixed volume identity.
The verifier also pins the collector's raw-byte SHA-256 as an exact review
lock: any collector-byte change requires an explicit verifier and security-
contract review rather than relying on a partial Bash parser.
Mutation and hostile-command fixtures prove that missing tools, denied Docker
access, malformed or oversized command output, hostile values, absent mounts,
and listener variations cannot escape the canonical schema or enable the
bootstrap gate. Linux runtime checks exercise only disposable fixture command
doubles and read-only observations; CI does not inspect or modify a real home
server.

## Explicitly excluded

ADR-051 does not:

- install Docker, Compose, Git, firewall software, or system packages;
- create the generation-control root, operation lock, selector, manifest, or
  journal;
- create, mount, format, encrypt, resize, repair, or benchmark storage;
- read `.env`, API keys, database passwords, router credentials, or provider
  configuration;
- discover the public address through an external echo service;
- determine CGNAT, router forwarding, DNS, TLS, or external reachability;
- create or start a Docker resource; or
- approve a live candidate, activation, rollback, or retirement action.

## Operator handoff

No API key, account, domain, credential, or secret is required to implement or
verify this decision. Once the actual Ubuntu server is available, run the
collector there. If a separate backup filesystem has already been mounted,
pass its exact local mount path. Share only the bounded report; keep every
secret and raw network/device credential on the server.

The report remains point-in-time evidence. A future mutating bootstrap or
generation transition must revalidate all safety-critical facts while holding
the ADR-050 exclusive operation lock immediately before acting.

## Consequences

- Development can continue without pretending that the current workstation is
  the target server.
- The operator gets one reproducible command instead of an open-ended list of
  potentially sensitive shell commands.
- Unknown hardware or an undecided backup disk remains visible and blocks live
  design without blocking safe local implementation.
- Hashed and redacted facts support remote review but do not replace local
  exact-value validation at the eventual execution boundary.
