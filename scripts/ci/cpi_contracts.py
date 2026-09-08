"""ADR-064/065/066/067: exact CPI retrieval, display and worker tooling custody."""
from __future__ import annotations

import hashlib
from pathlib import Path
from current_contracts import BASELINE, blob_record
from navigation_contracts import _current_bytes

CONTENT_SHA256 = {
    "scripts/inspect-cpi-worker.py": "bbe92ea777d621455b38844a428353392c62069df338af0035e51c3e5d477f91",
    "scripts/verify-cpi-worker-status.py": "b8fb3faade256cbb975dbd0ce4d825b35a52e3f6b77c6ebae2c75b505b14c430",
    "deploy/cpi-worker/compose.yaml": "05ca8d239100456b3c28e4dc58926818a22543627923b779307fbc76d20d58b9",
    "scripts/verify-cpi-worker.py": "51df921714635cddfa898f1edf3f7ba6ead58ac235a195e66de976edad6f52f4",
    "scripts/cpi-worker-fixture.py": "80a06bb134dfd662ac491cad74f064cc7a2c149e819034ff166938ecec11c3a7",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CpiSchedulePostgreSqlTest.java": "1ab4ca44bbb5d44e59d95a6300280a4f966408f446a4a6122fdfb3a156fbb726",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CpiCollectionJobTest.java": "54b772c75fe62186c9c0283f07bba83d6078ce3f0460d1916d9bcb33ac5f5a98",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/ScheduleCpiCommand.java": "4913224ce82ebb6269781f1216566d42c611c260a7bfd5da4245096ab42e4d75",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiCollectionJob.java": "c58e137e86419a014604b815375347635a6b43e6a3ab5420106c392982530e14",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CollectCpiCommandTest.java": "79c5794cc201e74bf6e8a4485f05ca41233af368ed790c97b476c75f8ae6b40c",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/ScheduleCpiCommandTest.java": "a5351b8f872712a9f332652b2a0dbe7e68afcd039367757be5bc8f48e836dc80",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiCollectorConfiguration.java": "9d289269ec9dc5b2dbed7cc248d7557b8836fc3eef9677201f767fc860a4b6cc",
    "apps/web/src/test/cpi-fixture.ts": "ce6bc6b7c3869b93d7d0196088d2dfc8ffa213c49dccb3a7cb8edbf11fcf9ddf",
    "apps/api/src/test/java/com/wallstreetreceipts/api/support/CpiTestFixture.java": "dd9a195758e60b476d78d3351ff0319567196fa74badc0d711ac6eaaf9f05876",
    "apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/bls/BlsCpiTest.java": "2d049cdc09d3343e5c7989aabb97dafca49519aafe65c92bff9ee158da095b1c",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/PostgreSqlMigrationTest.java": "ff84b4f8f905b1fd240451970eb7a7ce7c24d2699189f2d94d270332c001e6da",
    "apps/web/src/app/market/cpi/loading.tsx": "58e1a58af910d03ace582c0441a5888e26d34a9c2e6f7b9e71cc41e37aadccb2",
    "apps/api/src/main/java/com/wallstreetreceipts/api/WallStreetReceiptsApiApplication.java": "e3ac71f74d49b0ae78b55cdc59907046900d4f72178c7ff2d2e329bf84a0b73d",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/bls/BlsCpiParser.java": "edadea691205d92127c868abe5f388bf249763974425f12ff4ae717c6f1e9b83",
    "apps/web/src/app/market/cpi/cpi-view.tsx": "5d2d1155a8a4326248d9a2bbcd386db90b3d6b8d9217403b0842c85b3db369e7",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/cpi/CpiControllerTest.java": "c08eec286bbda485820eef9e20546253d9d264156f3b1773d5adc8feff7185d8",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/bls/BlsCpiClient.java": "1bb9b432fcb6c9176140ded8b648c526ee5d9fcfc3a025b15a2d25c4f4dcfbce",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcCpiRepository.java": "0e58a85afc8c681550e9502fc8538a66d070c324e2cb0fe142d215646da65d41",
    "apps/web/src/app/market/page.tsx": "ec4b868b3e97b42170bc22bf894bd2ce8f693e210d364767af4b8c2b2e05e12c",
    "apps/web/src/app/market/cpi/cpi.module.css": "33053a4c3904f0abe0fd01ff2b9987ccbd41e09338a3129c1a8fa6c05bbc1040",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiRepository.java": "1cd5459fbadbef87692dda83d141f2111a143b2c34308cbc98a16f88d008128a",
    "apps/web/src/app/market/cpi/page.test.tsx": "678ad1752b1ea652eb73c20a07719bf34fa6069e3c7c770a6c8e1d824d6fc671",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingCollectionAttemptPostgreSqlTest.java": "902b8e1fbf361413d4c30d2899ae853df4f2c36e2974a9060652c61cf64d98c2",
    "apps/web/src/app/market/cpi/page.tsx": "21263fab6344925e3bd901de600d583239ce11ef4ae630bbab2dda8e065938c9",
    "apps/api/src/test/java/com/wallstreetreceipts/api/release/ReleaseSchemaInventoryCommandTest.java": "13bc54442b2a28b859980ed0f18e8a9669a94fd0a760103bc11931abf48b603c",
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/cpi/CpiSnapshot.java": "fbba4b7dfe5b14ea2cfa5d9c757b1b2cb3be788405c3fde6c7cf1b7104678d80",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/CpiPostgreSqlTest.java": "56a3e67ee74b4aa24909d07e640a1a05233e6824a6ad358860f9a04d12fde952",
    "apps/web/src/lib/cpi.ts": "c5dbb153175b7f43f2757c52ee37df588f049aab69cd28bc5c172b0125b41d64",
    "apps/web/src/lib/cpi-provider.server.ts": "884fb91667b8e0ca8645ce632a6354bef5c8561ce0bacde11dcf2f74d0c82838",
    "apps/web/e2e/cpi.spec.ts": "2534b44098351d87dc0c28caaeb63ab909efb65769f9b8934b25dc31a5e2e0ec",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CollectCpiCommand.java": "029ab0904a3ab08722d43ca9f029261977e8dfff307315737dad276f6dca4709",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/cpi/CpiController.java": "c5f94855827bcb2889cafb52b6cbb3d8ba4e2fb081cfbb026f804405dc2e3754",
    "apps/web/src/app/market/cpi/error.tsx": "443e85f673643fac242901a6115196d70cb776c102a5c4dd96f7f4a02e19af87",
    "apps/web/src/app/market/cpi/messages.ts": "06d7ea6c94f5978170624d5c521f503667cbb2128ce73fdb41d6ab5fb965aee3",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingHistoryCollectionManifestPostgreSqlTest.java": "79fb123a71eddec0ff187fc137d806cc45113a456f56dac5332d0be4c6e0ca43",
    "apps/web/src/lib/cpi-provider.server.test.ts": "a8b8cb448fdfea50ccaa4f81c050427e2d0d74bf80825c55a281c2143dccb62f",
    "apps/web/src/lib/cpi.test.ts": "1b6d416fa3529127e18d7ef360a812b0aceab8a4090a9fac2f0ea3a9bb8545e7",
    "apps/api/src/main/resources/db/migration/V10__bls_cpi_retrievals.sql": "4c69f27eb5aab03de0be526f1718261f36e5f3f9e535a9bebf8bb814d7540664"
}
BASELINE_RECORDS = {
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingHistoryCollectionManifestPostgreSqlTest.java": "100644 blob bdff5c9dac08513b5c1b15d6cbdeda7c2a956dae",
    "apps/web/src/app/market/page.tsx": "100644 blob 4692b2fff592e9886cc872ab4600d76134bf0b7f",
    "apps/api/src/test/java/com/wallstreetreceipts/api/release/ReleaseSchemaInventoryCommandTest.java": "100644 blob 53c3b748f283cf5f6d3cf4b332bbb80079ffba62",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingCollectionAttemptPostgreSqlTest.java": "100644 blob 54598e2899d58fc26a549aef7854144581be6b7b",
    "apps/api/src/main/java/com/wallstreetreceipts/api/WallStreetReceiptsApiApplication.java": "100644 blob 8bf05ee60e0693e1347410f634f618c8298cc6ea",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/PostgreSqlMigrationTest.java": "100644 blob 99a55ef6032e00f537a83ad339b9aa31096373a3"
}
# Only these exact ADR-064 committed objects may precede the mandatory new
# working bytes during implementation; no stale working-source fallback.
PREVIOUS_RECORDS = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/WallStreetReceiptsApiApplication.java": "100644 blob 4385a929ab2187a70d71bda6562bc4f704545425",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CollectCpiCommand.java": "100644 blob 596b667ecb545a348dc3cab69f8f404f33574fbd",
}
CPI_PATHS = frozenset(CONTENT_SHA256)
CPI_ADDED_PATHS = CPI_PATHS - frozenset(BASELINE_RECORDS)


def verify_cpi(root: Path, git_read, baseline: dict, current: dict) -> dict:
    adjusted = dict(baseline)
    for relative in sorted(CPI_PATHS):
        original_record = BASELINE_RECORDS.get(relative)
        if baseline.get(relative) != original_record:
            raise ValueError("CPI baseline mode/type/object changed: " + relative)
        if original_record is not None:
            original = git_read(root, "show", f"{BASELINE}:{relative}")
            if blob_record(original) != original_record:
                raise ValueError("CPI pinned baseline bytes changed: " + relative)
        actual = _current_bytes(root, relative)
        if hashlib.sha256(actual).hexdigest() != CONTENT_SHA256[relative]:
            raise ValueError("Unreviewed current CPI source or test change: " + relative)
        accepted_records = {original_record, blob_record(actual)}
        if relative in PREVIOUS_RECORDS:
            accepted_records.add(PREVIOUS_RECORDS[relative])
        if current.get(relative) not in accepted_records:
            raise ValueError("Unreviewed committed CPI source change: " + relative)
        if relative in current:
            adjusted[relative] = current[relative]
        else:
            adjusted.pop(relative, None)
    return adjusted
