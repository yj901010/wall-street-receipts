"""ADR-080: exact new scoring integration sources; legacy calculators remain frozen."""
from __future__ import annotations

import hashlib
from pathlib import Path
import stat
from current_contracts import blob_record

CONTENT_SHA256 = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringInput.java": "11ace610cbce855d28f9dcc3c7d8f0777dbf49ecacf202cd165d15505d3c1121",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringMethodology.java": "20cd6ad2642cf4b8075e5218a87fe410cb02b6c66153817b7c9c3a20168bd99d",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringFingerprint.java": "1aa99f3bb127d1bf78989bc13f4343b387fe0e182100b58d453f9cd410fc9e85",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringEvaluator.java": "5320107e85f29b2acd99c5505b639c28ab2909946615ffa766740d9345fba9da",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringFixture.java": "18bd1d73e600424a1574f38bf6336b20f610037e122d3b584a1b253bb7318a38",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringEvaluatorTest.java": "c2b8d0892f29eb059d707395c0b468cfe3e4511d3ebc2faa616e4ddec02893f9",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringFingerprintTest.java": "8cff739a3bd89b39ae8e6240fc4a4106743dbc8e3a665130bb80af32d2a4a1bc",
}
SCORING_PATHS = frozenset(CONTENT_SHA256)


def current_bytes(root: Path, relative: str) -> bytes:
    path = root
    for part in Path(relative).parts:
        path /= part
        if not path.exists() or path.is_symlink():
            raise ValueError("Scoring custody path missing or linked: " + relative)
        if getattr(path.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400):
            raise ValueError("Scoring custody reparse point: " + relative)
    if not path.is_file() or path.stat().st_mode & 0o111:
        raise ValueError("Scoring source must be a nonexecutable regular file: " + relative)
    return path.read_bytes().replace(b"\r\n", b"\n")


def verify_scoring(root: Path, baseline: dict, current: dict) -> dict:
    adjusted = dict(baseline)
    for relative, expected in CONTENT_SHA256.items():
        if relative in baseline:
            raise ValueError("Added scoring path exists in frozen baseline: " + relative)
        raw = current_bytes(root, relative)
        if hashlib.sha256(raw).hexdigest() != expected:
            raise ValueError("Unreviewed current scoring source: " + relative)
        if current.get(relative) not in {None, blob_record(raw)}:
            raise ValueError("Unreviewed committed scoring source: " + relative)
        if relative in current:
            adjusted[relative] = current[relative]
    return adjusted
