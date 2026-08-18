# Contributing

Wall Street Receipts treats accuracy, point-in-time consistency, provenance,
and reproducibility as product requirements. A change is not complete when it
only looks correct; its data origin, timestamp semantics, and missing-value
behavior must also be reviewable.

## Git Flow

Use the following branch names exactly. Do not add a `codex/` prefix.

| Branch | Created from | Merges into | Purpose |
| --- | --- | --- | --- |
| `main` | n/a | n/a | Release-ready history and version tags |
| `develop` | `main` | n/a | Integration branch for the next release |
| `feature/<scope>` | `develop` | `develop` | Product, infrastructure, documentation, or test work |
| `release/<version>` | `develop` | `main` and `develop` | Release stabilization only |
| `hotfix/<scope>` | `main` | `main` and `develop` | Urgent production correction |

Examples: `feature/p0-foundation`, `feature/call-list`, `release/0.1.0`, and
`hotfix/source-attribution`.

Start normal work from an up-to-date `develop`:

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feature/<scope>
```

Open a pull request into `develop`. Do not commit directly to `main` or
`develop`. Feature branches should be short-lived and are squash-merged after
approval. Delete the branch after merging.

For a release, create `release/<version>` from `develop`, allow only release
notes, versioning, and stabilization fixes, then merge it into both `main` and
`develop`. Tag the `main` merge with an annotated semantic version such as
`v0.1.0`. Create a `hotfix/<scope>` from `main`; after verification, merge the
same correction into both `main` and `develop` and tag the fixed release.

## Conventional Commits

Every commit must follow Conventional Commits:

```text
<type>(optional-scope): <imperative summary>
```

Common types are `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`,
`ci`, and `chore`.

Examples:

```text
feat(api): expose fixture-backed analyst calls
fix(web): render missing target as NA
test(api): cover duplicate provider events
ci: verify web and api independently
```

Keep commits focused and buildable. Use `!` and a `BREAKING CHANGE:` footer for
an intentional incompatible change.

## Pull request gate

Before requesting review:

1. Rebase or merge the latest `develop` as agreed by the team and resolve all
   conflicts locally.
2. Run web lint, tests, and build plus `apps/api/mvnw verify`.
3. Confirm the app starts without vendor credentials.
4. Mark every synthetic record and screen as `DEMO`.
5. Preserve source provenance, event time, processing time, and UTC timestamps.
6. Keep unknown numbers as `null` and display them as `NA`, never `0`.
7. Do not commit secrets, provider payloads with restricted rights, article
   bodies, research reports, or rehosted media.
8. Update tests and relevant implementation notes for behavior changes.

Pull requests should explain the phase/scope, user-visible behavior, data or
schema impact, verification performed, and follow-up work. Reviewers should
reject provider DTO leakage into domain/scoring code, mutable historical
snapshots, non-deterministic financial calculations, and untraceable claims.
