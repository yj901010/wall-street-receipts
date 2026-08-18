# Wall Street Receipts Engineering Rules

## Product priority

Accuracy > point-in-time consistency > source traceability > reproducibility > performance > visual effects.

This is a financial data verification product. Do not present inferred, fixture, stale, or missing values as observed facts.

## Git Flow

- `main` contains deployable releases only.
- `develop` is the integration branch for the next release.
- Branch from `develop` with `feature/<phase>-<topic>`.
- Branch releases from `develop` with `release/<version>` and merge them into both `main` and `develop`.
- Branch production fixes from `main` with `hotfix/<topic>` and merge them into both `main` and `develop`.
- Use Conventional Commits and keep commits focused.
- Do not commit directly to `main` or `develop` after repository bootstrap.

## Initial implementation boundary

- Web: Next.js + TypeScript, server-rendered initial data, client islands only where interaction requires them, Vitest, and Playwright.
- API: Java 21 + Spring Boot 3.x, Flyway, JUnit 5, and Testcontainers.
- Data: PostgreSQL and explicit DEMO fixtures.
- Preserve extension boundaries for Redis, Kafka, ClickHouse, object storage, OpenSearch, and Flink without adding them as initial runtime dependencies.
- Do not connect paid or production data providers during P0-P2.

## Domain invariants

- An analyst call is an event.
- Persist timestamps in UTC and distinguish `event_time` from `processing_time`.
- Snapshots are immutable and reconstructed from the state available at event time.
- Preserve source provenance and provider event identity.
- Outcomes carry a `methodology_version` and remain reproducible.
- Use `BigDecimal` for prices and money, and inject `Clock` into time-dependent Java code.
- Keep provider DTOs behind `Vendor DTO -> Adapter -> Canonical Model -> Domain` boundaries.
- Never invent a missing numeric value or replace it with zero.

## AI boundary

AI may assist extraction, transcript tagging, structured query generation, and evidence-linked explanation. Deterministic code must calculate returns, alpha, hit rate, target hits, and ranking metrics.

## UI boundary

- Mark every fixture surface as `DEMO` and display timestamp, source, and data mode where relevant.
- Support loading, error, empty, keyboard-focus, desktop, and mobile states.
- Prefer restrained, dense, evidence-first financial UI with aligned tables and tabular numbers.
- Do not use gradient page backgrounds, glassmorphism, neon glow, giant heroes, excessive pills/cards/rounding, fake avatars, testimonials, or decorative sparkles.

## Completion

Before finishing a phase, run the relevant lint, unit, integration, build, and responsive checks. Record routes, module structure, results, technical decisions, and next-phase work in `IMPLEMENTATION_LOG.md`.
