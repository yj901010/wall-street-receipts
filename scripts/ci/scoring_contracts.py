"""ADR-080/081: exact scoring integration and receipt paths; old calculators stay frozen."""
from __future__ import annotations

import hashlib
from pathlib import Path
import stat
from current_contracts import blob_record

CONTENT_SHA256 = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/ScoringReceiptRepository.java": "1d5ec9dff8cd4b0a95e8193810eacbb595378842d8fd0393f29564348a87efaf",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringInputCodec.java": "e784b6eafa4c6f3768138f5f10b67cdd37d1f79f9a0add6400b74c19fdbf1af1",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/ScoringLedgerVerifier.java": "76471bd19dfe2dc159716b367ff6e31f351bc53f8e0a46f2e39740cea126ebcb",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/ScoringReceiptService.java": "6c291c169c94f2fefa5d4aed9f31bca382ccb2215b84f93aa7064f816a0fe6b8",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcScoringReceiptRepository.java": "badadac6e6b68c44e3895523b1fb7500ae5d9631a1964bc819a921c3f749a14f",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/scoring/ScoringReceiptController.java": "ad9fe83b246163bdb67fe04139f2657841869355794316d0d2d11365263eaf48",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/scoring/ScoringReceiptResponse.java": "02cae1128d2c22d97f105817af1b2f6f61d32a29e9cac4eb8fc092c3cab218b9",
    "apps/api/src/main/resources/db/migration/V12__demo_scoring_receipts.sql": "a2aa377a582b041f9bd2542b81c86cdd734fc14c4b588521761f8dc04b23d81b",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/EndpointScoringInputCodecTest.java": "181c0eb2d0c48c03a60c3d8d2c4fad8b7189a601410433df8c8a06e4c6176cd7",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ScoringReceiptApiTest.java": "ff4433b4d6e872dd4e3431306cad12da8477de11641d098a37b173305b163ac7",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ScoringReceiptFixture.java": "b94418a4efb9935a8d22328a09654716ed369ba97f4d8567218e8255e097da95",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ScoringReceiptPostgreSqlTest.java": "b900f80c1f628e5d8c0fea3cca5638482998182eb92ccfe321e77fa595c96a8e",
    "contracts/scoring-receipts.openapi.yaml": "c60c94a98d2836257f7c37db0a009ac6e24ff359edf5b97accbdaf23bd1d5a0a",
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
