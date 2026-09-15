"""Exact ADR-090 documentation-intake additions, never raw coverage approval."""
import hashlib
from pathlib import Path

from current_contracts import blob_record
from scoring_contracts import current_bytes

CONTENT_SHA256 = {
    "RAW_FEED_INTAKE.md": "936e6b63eb96e0311090d8f45630020ce152ea78c0885a6b338550283963da39",
    "examples/raw-feed-intake/blank.json": "975d0df49acf9e18d6a4fa783a004999de5ebf49a41a240f0f98e3f69d841c23",
    "scripts/review_raw_feed_intake.py": "a5539f0f182a6dea85a45d9eabf5b2901297da7991be8d1869f9b1f9ff8b0ada",
    "scripts/ci/test_raw_feed_intake.py": "288ac87831fafa85eec9f49ab072145afeca099947fc408ff7cc57f124c43f77",
}
RAW_FEED_INTAKE_PATHS = frozenset(CONTENT_SHA256)


def verify_raw_feed_intake(root: Path, baseline: dict, current: dict) -> dict:
    adjusted = dict(baseline)
    for relative, expected in CONTENT_SHA256.items():
        if relative in baseline:
            raise ValueError("Unexpected raw-feed intake path in frozen baseline: " + relative)
        raw = current_bytes(root, relative)
        if hashlib.sha256(raw).hexdigest() != expected:
            raise ValueError("Unreviewed current raw-feed intake source: " + relative)
        if current.get(relative) not in {None, blob_record(raw)}:
            raise ValueError("Unreviewed committed raw-feed intake source: " + relative)
        if relative in current:
            adjusted[relative] = current[relative]
    return adjusted
