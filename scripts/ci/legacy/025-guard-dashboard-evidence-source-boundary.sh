python <<'PYTHON'
from pathlib import Path

provider_path = Path("apps/web/src/lib/providers/fixture-market-provider.ts")
if not provider_path.is_file():
    raise SystemExit(f"Missing dashboard fixture adapter: {provider_path}")

provider_source = provider_path.read_text(encoding="utf-8")
required_projection_tokens = (
    "CallsProvider",
    "MarketTreemapProvider",
    "MarketBoardProvider",
    "sp500",
    "nasdaq100",
    "PRICE_CHANGE",
    "P3_DEFERRED",
)
for required_token in required_projection_tokens:
    if required_token not in provider_source:
        raise SystemExit(
            f"Dashboard adapter is missing locked projection token: {required_token}"
        )

forbidden_adapter_fragments = (
    ".json",
    "fixtures/v1",
    "market-snapshots",
    "call-contexts",
    "market-board.json",
    "demo-call-",
    "JPMorgan",
    "Goldman Sachs",
    "DEMO index outlook",
    "DEMO equity interview",
)
for fragment in forbidden_adapter_fragments:
    if fragment in provider_source:
        raise SystemExit(
            f"Dashboard adapter duplicates or crosses canonical evidence: {fragment}"
        )

production_sources = [
    path
    for path in Path("apps/web/src").rglob("*")
    if path.suffix in {".ts", ".tsx"} and ".test." not in path.name
]
obsolete_market_literals = (
    "5,278.52",
    "18,752.34",
    "13.72",
    "+0.63%",
    "+0.78%",
    "-2.01%",
)
for path in production_sources:
    source = path.read_text(encoding="utf-8")
    for literal in obsolete_market_literals:
        if literal in source:
            raise SystemExit(
                f"Obsolete hard-coded dashboard market literal {literal!r} remains in {path}"
            )

print(
    "Validated dashboard provider composition boundaries and removed obsolete "
    "hard-coded market display literals"
)
PYTHON
