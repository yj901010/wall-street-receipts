# Wall Street Receipts

Wall Street Receipts is a point-in-time financial research product that records
public analyst calls, preserves the market context that was available when each
call was made, and evaluates later outcomes with a reproducible methodology.

The P0 foundation and P1 domain/fixture phase are complete, P2 core UI work
remains in progress, and the first isolated P3 calculation-core slice is
complete together with the schedule-only session-offset mechanics slice.
Delivered P2 routes include the evidence directories, maps,
market publication state, recorded S&P call history, and the honest known-
deferred Screener shell; actual screening remains P8 work. P1 provides a
canonical analyst-call ledger,
source evidence, immutable point-in-time market and macro/event context,
list/detail APIs, and responsive web routes. Append-only correction,
cancellation, and outcome records preserve audit, methodology, and input
lineage without claiming P3 scoring results. Kafka, Redis,
ClickHouse, OpenSearch, object storage, and commercial data providers remain
later-phase extension points rather than runtime dependencies.

> All bundled records use `DATA_MODE=DEMO`. They are synthetic examples, not
> investment advice or representations of real analyst statements.

## Prerequisites

- Node.js 24 LTS and Corepack/pnpm
- Java 21
- Docker with Docker Compose v2

## Start locally

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
corepack enable
pnpm install --frozen-lockfile
pnpm --dir apps/web dev
```

In a second PowerShell terminal, start the API:

```powershell
Set-Location apps/api
.\mvnw.cmd spring-boot:run
```

On macOS or Linux, use `cp .env.example .env` and
`./apps/api/mvnw spring-boot:run` instead. The default local endpoints are:

- Web: <http://localhost:3000>
- API: <http://localhost:8080>
- PostgreSQL: `localhost:5432`
- Analyst calls: <http://localhost:3000/calls>
- Analyst identities: <http://localhost:3000/analysts>
- Institution identities: <http://localhost:3000/institutions>
- Methodology registry: <http://localhost:3000/methodology>
- S&P map: <http://localhost:3000/maps/sp500>
- Nasdaq map: <http://localhost:3000/maps/nasdaq100>
- Screener availability shell: <http://localhost:3000/screener>
- Analyst-call API: <http://localhost:8080/v1/calls>
- Revision audit API: <http://localhost:8080/v1/calls/demo-call-002/revisions>
- Outcome audit API: <http://localhost:8080/v1/calls/demo-call-001/outcomes>

Stop the database without deleting its volume:

```powershell
docker compose stop postgres
```

## Verify changes

Run the same checks used by CI:

```powershell
pnpm --dir apps/web lint
pnpm --dir apps/web test
pnpm --dir apps/web build
Set-Location apps/api
.\mvnw.cmd -B verify
```

Validate the Compose configuration separately with:

```powershell
docker compose --env-file .env.example config --quiet
```

## Fixture contract

Canonical demo data lives under [`fixtures/v1`](fixtures/v1). Every fixture has
a schema version, fixture version, generation timestamp, `DEMO` data mode, and
provenance. Missing values remain JSON `null`; presentation layers render them
as `NA` and must never coerce them to zero or invent a value.

The P1 outcome fixtures are model-only audit records. Their financial metrics
and result booleans are deliberately `null`; deterministic return, alpha,
target, and MFE/MAE calculations belong to P3 and require golden tests.

The completed first P3 slice is a pure target-hit comparison core over explicit
test inputs. It is not connected to canonical fixtures, stored outcomes, APIs,
or a runtime scoring schedule, and it does not reinterpret either model-only
methodology as an implemented formula.

The completed P3 session-offset slice accepts only a caller-selected anchor,
positive count, explicit ordered test-session catalog, and evaluation as-of.
It does not define D1/W1/M1/M3/M6/Y1, infer calendars, fetch observations, or
publish an outcome, schema, fixture, API, database, provider, or web surface.

The fixtures are deterministic and require no vendor credentials or network
access. Production provider payloads must be translated through provider
adapters before they reach the canonical domain.

## Repository layout

```text
apps/web/        Next.js user interface
apps/api/        Spring Boot API and Flyway migrations
fixtures/v1/     Versioned canonical DEMO fixtures
contracts/       OpenAPI contracts
schemas/         Canonical JSON Schemas
quality/         Phase acceptance checks
decisions/       Versioned architecture decisions
.github/         Continuous integration workflows
compose.yaml     PostgreSQL service
```

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the Git Flow, Conventional Commits,
review gates, and data-integrity rules.
