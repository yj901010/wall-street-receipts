"""ADR-080 through 083: exact scoring custody; old calculators and link predecessor stay frozen."""
from __future__ import annotations

import hashlib
from pathlib import Path
import stat
from current_contracts import blob_record

CONTENT_SHA256 = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringEvaluator.java": "5468dc0b1cf29a7669c84856a8c530b8f6f90f2a5a491f47e39661a297b087e1",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringInput.java": "4ffd5a7c27fa83e92d4c396be052a01556b38a302e63f5c7ced28bbf81ec59f5",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringInputCodec.java": "f10b1bffc8fc93de2e43086e9cef01f7245c9972e744365a48a0ff4d6b9ec583",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringMethodology.java": "514e09a4fd1bba085796f740538bc06c3c96e6833796abc34607fe9f71585c8e",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringEvaluatorTest.java": "f9055fe20020335f58eaaa94fd203752532900f8e9714c65034a1b64c21e48bd",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringFixture.java": "8c0652d19f4dd2e7f0873231b6b0b9eec52512d127575a0faae9d15ceb0ca9d3",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringInputCodecTest.java": "54e1e178833d4d3553f2e81092e0fab188f860c0a03eb896fc2c5cd23de153fa",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringInputTest.java": "03a53ad7a2b12869f93079d77cc3ffe938658e7c859879e03cb1dd53f910932b",
    "apps/web/scoring/public.config.ts": "58e18c0799db38fe3d185e3d22b89f46c11889b60470294e038bfd589dc27d7f",
    "SCORING_RECEIPTS.md": "1f1089c892e6ab946c4504f47319944811bb226225936392324dd7dd980c4559",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/DemoScoringReceiptCommand.java": "f18745ba189bfb7b179d24433aa5a3d03f730f823fecce0b68035001519f0bfb",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/DemoScoringReceiptCommandTest.java": "becc2951de7bcd38e4bf21cfbe3a1e99ef6f463c5951ca8745603f956122bd6c",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ScoringReceiptBrowserIT.java": "6f14d7e46425e6b56977f9c18a663c020bc6f3ceab7837a805d0d2f2f68daefa",
    "apps/web/e2e/scoring-receipts.spec.ts": "d978b8e0a4a96f241b34504fed23ab4adc1fd5c9589b31e3aa8e2eb61e2d5b20",
    "apps/web/scoring/full-stack.config.ts": "65d181f1a31712ec1da80a001bb954fbc07567eeabaae29d8daafb0151e90258",
    "apps/web/scoring/tests/receipts.spec.ts": "9f385a0cfab0380cc2c2a11dd55e9dd6420c9bbb87ff0b0122d9b546f91a20b4",
    "apps/web/src/app/calls/[id]/scoring-receipts/loading.tsx": "2f4e4f39ebfd97b385858d3dbd8f7cb6faa73410176904e4667b2c29857b0276",
    "apps/web/src/app/calls/[id]/scoring-receipts/messages.ts": "558df1133f3b916c8196c598acd35ea93d151a076a2973569e8e049f85fbf94b",
    "apps/web/src/app/calls/[id]/scoring-receipts/page.tsx": "f0021f0c622fa105b7fda6edc70b0178ab8e6f150f3c6e89b174f2e3e78fa65f",
    "apps/web/src/app/calls/[id]/scoring-receipts/receipt-view.test.tsx": "ab5b8d1025f0356a6859f794cc812cec4c1483900765afdc924c0714f523e354",
    "apps/web/src/app/calls/[id]/scoring-receipts/receipt-view.tsx": "5b01c233bd150a425708a1a1f764b444951bea46efdb4d20c58d94728f096df8",
    "apps/web/src/app/calls/[id]/scoring-receipts/receipts.module.css": "3df44fdc7ee0ab1104f1fa51ece1f9155ea1fde5e4771ec1481066b7b112adbe",
    "apps/web/src/lib/scoring-receipts.fixture.ts": "d5dfff0158a98228038f1eff4bb6b7c1e377caa27780c700715acd30a1fd3abe",
    "apps/web/src/lib/scoring-receipts.server.test.ts": "2bf620d594ad1deb1767c893d29975d496e090efad8dc4187a889b12794e83e9",
    "apps/web/src/lib/scoring-receipts.server.ts": "37182bbbb59caaf55e4555f52674f4dfbdbe99c5ade7d2eca708395ecc060144",
    "apps/web/src/lib/scoring-receipts.test.ts": "537d575db16170f188cb04989108b3cd5df7b65609f559a2e6c4aa76109ef8d5",
    "apps/web/src/lib/scoring-receipts.ts": "8e33991db3005599b86601d773c98f24b523f49a212e107f04eca7a91e52dbe2",
    "apps/web/src/app/calls/[id]/page.tsx": "f581509ef4010c03e1ca5399eca11bbc35cb4db2eba0fb5b8734d2bbd62e4cf5",
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
EDITED_BASELINE_RECORDS = {
    "apps/web/src/app/calls/[id]/page.tsx": "100644 blob dd81025d965f7b779dbb789c1d65e1683781be02",
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
        original = EDITED_BASELINE_RECORDS.get(relative)
        if baseline.get(relative) != original:
            raise ValueError("Unexpected scoring path in frozen baseline: " + relative)
        raw = current_bytes(root, relative)
        if hashlib.sha256(raw).hexdigest() != expected:
            raise ValueError("Unreviewed current scoring source: " + relative)
        if current.get(relative) not in {original, blob_record(raw)} or (original is not None and relative not in current):
            raise ValueError("Unreviewed committed scoring source: " + relative)
        if relative in current:
            adjusted[relative] = current[relative]
    return adjusted
