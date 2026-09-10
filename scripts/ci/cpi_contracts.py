"""ADR-064 through 076: exact CPI retrieval, bounded reads and tooling custody."""
from __future__ import annotations

import hashlib
from pathlib import Path
from current_contracts import BASELINE, blob_record
from navigation_contracts import _current_bytes

CONTENT_SHA256 = {
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CpiAttemptQueryConcurrencyTest.java": "c94b9ccfb32bc0d06a6ff2cbe8dbd6c42b862d2397d76765bbbd8a6ad0499310",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptConcurrencyPostgreSqlTest.java": "6f3a9ba78933be8568c6754c013dd69790709ae461eea58bf71be62783251f41",
    "apps/api/src/main/java/com/wallstreetreceipts/api/config/CpiReadOnlyPoolConfiguration.java": "424026b6389b9125e117375793e8ab76995a06da4719a6803e93d31090ed1399",
    "apps/api/src/test/java/com/wallstreetreceipts/api/config/CpiReadOnlyPoolConfigurationTest.java": "2aa4b62b882a82abfa2c385e6031db64be143dc182857058135ba24d31924725",
    "apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcCpiAttemptReadTimeoutTest.java": "8f251eb30396195a0b39f790f004bb8d481c98265e4a7015785aa60d05bfdc14",
    "apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiProperties.java": "2ba3da4ed709d18db9838fdc562c7b60c0023fccb3fef85ebcddb4e94a302ca0",
    "apps/api/src/main/resources/application.yml": "46741de69b3997a43179f502d1b4c7ef671ba146a590c9635d64d66aa41e08d7",
    "apps/api/src/test/java/com/wallstreetreceipts/api/config/OperatorApiPropertiesTest.java": "4371b7906a5a9bf694adf1e7082e83d6827e761f1bea70541ec6edf66a2c1d5d",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/security/CpiReadOnlyOperatorSecurityTest.java": "26e5a4db537bd44a796be6f41e7814ab91d60b6ebd72cab10c62c8f18649f50a",
    "deploy/cpi-operator/Dockerfile": "fb311640bfea8c95b052a30a2b378b8276f21ffc3bbe988bd9ffa9b8ab22f413",
    "deploy/cpi-operator/Dockerfile.dockerignore": "5161cae2d5ecc88632a65011da1805858dfc9153ec60cfac6dbc453e30aab81f",
    "scripts/cpi_operator_lifecycle.py": "2b88eaefbb2a497a405d3f5af4439db522f2ef8f9da4a567d58e9060795756cc",
    "scripts/verify-cpi-operator-lifecycle.py": "66e922c3a8897bda8248d7840028268920cbbc510114a020ff6fd88c0d552423",
    "scripts/cpi-operator-lifecycle-probe.mjs": "220e69cf3d782b0b08da60cc3149d6d765f546cb22d90a2ab7ca31fa7654316e",
    "apps/web/operator/tests/next-start-fixture.mjs": "1e6d8303443e1fd242f5bda8516c2edc7ed7f3657e902f846d54d0c01e664795",
    "apps/web/src/lib/operator-start.test.ts": "c1a79469f9e01d5c23e4f1479d2b83cf49026b1c399d021b9688083d338e8109",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/CpiBrowserEvidence.java": "3423fee8295d01c8479657aa01f572e572b87defda7f67184bdc608ef1ffd0b7",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/CpiBrowserProcess.java": "f4d1bc10513ba32ba6eff5c77d5eb2d05870ba84603808193bfbef57b0702eba",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/CpiBrowserProcessTest.java": "b61338d959384a8e57c1f93fe845c844469fe6ab2df80ff966d4d3184cb297b3",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/CpiOperatorBrowserIT.java": "91cacb28ce6d985757125cdd71a22e0a7f0937cc2e7cca2919c82d5aab0f876b",
    "apps/web/operator/full-stack.config.ts": "0b624c17791dc1cc71d609fe73e8a77401aa428a7e179923d44e04afee95b165",
    "apps/web/operator/full-stack-tests/evidence.spec.ts": "605af71f7b23ac97f50592a9564651c765e735e00fd52aae21bbbe0b4f0b564c",
    "apps/web/src/proxy.ts": "24901bd8556156ba4c24409068716edac6b1ab60533f83e56971b37c75535c2b",
    "apps/web/src/lib/operator-proxy.test.ts": "08e18e947beaa8ef80c73f6ed100b4c021050aefc69a793aef020375df4f87b9",
    "apps/web/e2e/operator-disabled.spec.ts": "1117cd38e87f8c40ee1e8b5e68ac6e5c4e0034b5a3709a4b1108b5e53a68f8e0",
    "apps/web/operator/demo-server.mjs": "5fc82b6d2feb6eb099841c2e3df8f1efcd1503dac1cf669cf605807f86b096e5",
    "apps/web/operator/gateway.ts": "16c92fbae9b0cbfd1f5744c0c52f36bff1fe4a4365d4a60ebe23dff602fd8de1",
    "apps/web/operator/playwright.config.ts": "a9071fa03c0f432f751321dfa88f6300516d3e4225eccf8ce5bf6aeabc98d4fd",
    "apps/web/operator/start.mjs": "8884f71d145a9e7d18ecf8d67ef21c8f1a9c481a4b17d43e552ddfdcfe4a8d06",
    "apps/web/operator/tests/view.spec.ts": "c74159ef927766715bdfb918e07e7ca35fe790a939c3461be01838f378c91338",
    "apps/web/src/app/operator/cpi/page.test.tsx": "d4ddd25473b040f375ab828a9a6dc7760676d9999ccf216b70dad11896c99d72",
    "apps/web/src/app/operator/cpi/page.tsx": "6a1432bb3b7cfd14d310f7879549aade7ac764ed917fff91f62f81030840779f",
    "apps/web/src/app/operator/cpi/view.module.css": "106c269089016da115b2e241b0f7fa02b07584e6ca8994250e3d64a394f499a8",
    "apps/web/src/app/operator/cpi/view.test.tsx": "3802e14f4cdf6870b18a593ea34aa9f19a80644a07bd8e134bf2573e7bb63950",
    "apps/web/src/app/operator/cpi/view.tsx": "6bded4a55c968092a26ae829f4220fd519303f09017db39b66b5befb53e4e3bc",
    "apps/web/src/lib/operator-cpi.test.ts": "a4526e8335c6838541ce168623a0c44b9a2a468f5414be002e3d9e8013e950a8",
    "apps/web/src/lib/operator-cpi.ts": "a842f5b76414ac9a7552e4415a825d78f5e87d6f98708a03a4e86063d0dc1bd7",
    "apps/web/src/lib/operator-gateway.test.ts": "3107aed995bae78f88f6abc91146863a7a5bfd7ab3f35b82c0af4c3aeb74b253",
    "apps/web/src/lib/operator-mode.server.ts": "90778d96c4cf6f114d61427c109f2d6a2ce6deafc407a83e87cc01fae6ab9c5d",
    "apps/web/src/test/operator-cpi-fixture.ts": "badd52b69b1b0c9f58db04a3b2e6141d62c38d4e25954a5563b3877a98b40171",
    "apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiSecurityConfiguration.java": "01958a8d168b4519f1be03f5ef6809f981d6946f191015660c39991a18d7b08b",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/security/ApiRequestRejectedHandler.java": "31ba52b87762a892b5ae6f229d1fa870a10333ca122e4e2f15334e3134112857",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorApiSecurityProblemWriter.java": "94ea76e5705a9a08100afcc42c7ef7984bf06422771c95cee59188409ed25e36",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiAttemptQueryService.java": "95b1610dae04e8c94fa3323edf252d92756af2533c01a3ecba9d75ee44121d78",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiAttemptReader.java": "cf3e76e9272d0368cf3c03ccbc523227cafade7823e2447d76e21b73003f0d88",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptController.java": "1a8c5e0cb7bf94b74822986905bb79c6f0eebbc9dacd7ec48d5a244ae7c39fd8",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptExceptionHandler.java": "2c68df1218eb90c1df56c983d0e7681fd36a8f26d79ced59fa0857800b886ebd",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CpiAttemptQueryServiceTest.java": "85cdd6b82125cd26ccf7a3fc6a37143d4df1d1a832323fb86ff6579e147af6bb",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptApiTest.java": "2b902ea7540d52f353478102d80b680d857e882a34a5ca6d2e2ea77a0f38162e",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptDisabledTest.java": "7bbf58c6cefd92ba676cbbd9d8398d8a9769fbbd9ad5981fb6bda87fefd8bd8f",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptPostgreSqlTest.java": "13e2b7e64b1523986443a0abf95d33071160e88ca3a92efb1db92c0814885423",
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/cpi/CpiCollectionAttempt.java": "9fc23740760a1f4bf3a46b5fcb7dd40a6ea57acdca76df89e1a52fa3653e80d6",
    "apps/api/src/main/resources/db/migration/V11__bls_cpi_collection_attempts.sql": "1fdf642b5f474a4a8181916de482f54d418b512abcf4d319c1d904c604085ed6",
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/cpi/CpiCollectionAttemptTest.java": "67a372d593255cd59cf955d64b5d9c8f99e169b0277d5c12ab7f9577b15c1418",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/CpiAttemptPostgreSqlTest.java": "dc33b05479226be83677dc48de0a401cbbc1a79880f598eb7403ed1e7ff34fde",
    "scripts/inspect-cpi-worker.py": "bbe92ea777d621455b38844a428353392c62069df338af0035e51c3e5d477f91",
    "scripts/verify-cpi-worker-status.py": "b8fb3faade256cbb975dbd0ce4d825b35a52e3f6b77c6ebae2c75b505b14c430",
    "deploy/cpi-worker/compose.yaml": "05ca8d239100456b3c28e4dc58926818a22543627923b779307fbc76d20d58b9",
    "scripts/verify-cpi-worker.py": "14a1785c7f1709b4cf36330b6e69bb6b5bdc79d85032eecb9e069f9344f06e3a",
    "scripts/cpi-worker-fixture.py": "80a06bb134dfd662ac491cad74f064cc7a2c149e819034ff166938ecec11c3a7",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CpiSchedulePostgreSqlTest.java": "1ab4ca44bbb5d44e59d95a6300280a4f966408f446a4a6122fdfb3a156fbb726",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CpiCollectionJobTest.java": "fc7fc6d94fbcfb510fd18b1caa935cbc47cf73ce0825554b3e14cd3c39497776",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/ScheduleCpiCommand.java": "97b81ddc53cb36eba5a839ecf05e9a894b6d30dc891d8232e902ea28ffb94594",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiCollectionJob.java": "97aef34b529b9c2a6b6fc5c72bbd4c56ded2e888747d99cff1878287d7dc70ed",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CollectCpiCommandTest.java": "79c5794cc201e74bf6e8a4485f05ca41233af368ed790c97b476c75f8ae6b40c",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/ScheduleCpiCommandTest.java": "59e4532008dc825e3eeef690e2a24625f916a844988eaf9c41524990bf108afe",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiCollectorConfiguration.java": "9d289269ec9dc5b2dbed7cc248d7557b8836fc3eef9677201f767fc860a4b6cc",
    "apps/web/src/test/cpi-fixture.ts": "ce6bc6b7c3869b93d7d0196088d2dfc8ffa213c49dccb3a7cb8edbf11fcf9ddf",
    "apps/api/src/test/java/com/wallstreetreceipts/api/support/CpiTestFixture.java": "dd9a195758e60b476d78d3351ff0319567196fa74badc0d711ac6eaaf9f05876",
    "apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/bls/BlsCpiTest.java": "2d049cdc09d3343e5c7989aabb97dafca49519aafe65c92bff9ee158da095b1c",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/PostgreSqlMigrationTest.java": "6b3c474268869a9167ae1eca7db84085106b43053202b440ec5196eef657940d",
    "apps/web/src/app/market/cpi/loading.tsx": "58e1a58af910d03ace582c0441a5888e26d34a9c2e6f7b9e71cc41e37aadccb2",
    "apps/api/src/main/java/com/wallstreetreceipts/api/WallStreetReceiptsApiApplication.java": "e3ac71f74d49b0ae78b55cdc59907046900d4f72178c7ff2d2e329bf84a0b73d",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/bls/BlsCpiParser.java": "edadea691205d92127c868abe5f388bf249763974425f12ff4ae717c6f1e9b83",
    "apps/web/src/app/market/cpi/cpi-view.tsx": "5d2d1155a8a4326248d9a2bbcd386db90b3d6b8d9217403b0842c85b3db369e7",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/cpi/CpiControllerTest.java": "c08eec286bbda485820eef9e20546253d9d264156f3b1773d5adc8feff7185d8",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/bls/BlsCpiClient.java": "1bb9b432fcb6c9176140ded8b648c526ee5d9fcfc3a025b15a2d25c4f4dcfbce",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcCpiRepository.java": "9e973bfde5e46c10893c96150004e67095b5b1cf0787fc4e9aa3d4b64a06e7c6",
    "apps/web/src/app/market/page.tsx": "ec4b868b3e97b42170bc22bf894bd2ce8f693e210d364767af4b8c2b2e05e12c",
    "apps/web/src/app/market/cpi/cpi.module.css": "33053a4c3904f0abe0fd01ff2b9987ccbd41e09338a3129c1a8fa6c05bbc1040",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiRepository.java": "fb8a86dfa18989ad4acc21140b22bbdbf141d614e45a9c3c8b20b65ad0b0f048",
    "apps/web/src/app/market/cpi/page.test.tsx": "678ad1752b1ea652eb73c20a07719bf34fa6069e3c7c770a6c8e1d824d6fc671",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingCollectionAttemptPostgreSqlTest.java": "f663f7f595b699f3712915d00f4ed0aa44f142f72b0ae34020d631a807601353",
    "apps/web/src/app/market/cpi/page.tsx": "21263fab6344925e3bd901de600d583239ce11ef4ae630bbab2dda8e065938c9",
    "apps/api/src/test/java/com/wallstreetreceipts/api/release/ReleaseSchemaInventoryCommandTest.java": "d97bbed37dffb81d04881c24923a3273279545c178e4297b662f41f92fd4c5aa",
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/cpi/CpiSnapshot.java": "fbba4b7dfe5b14ea2cfa5d9c757b1b2cb3be788405c3fde6c7cf1b7104678d80",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/CpiPostgreSqlTest.java": "b9140b4c49bf2b2474d8734cbde75e3643f4b56a50a71d207e84512d250d4d23",
    "apps/web/src/lib/cpi.ts": "c5dbb153175b7f43f2757c52ee37df588f049aab69cd28bc5c172b0125b41d64",
    "apps/web/src/lib/cpi-provider.server.ts": "884fb91667b8e0ca8645ce632a6354bef5c8561ce0bacde11dcf2f74d0c82838",
    "apps/web/e2e/cpi.spec.ts": "2534b44098351d87dc0c28caaeb63ab909efb65769f9b8934b25dc31a5e2e0ec",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CollectCpiCommand.java": "029ab0904a3ab08722d43ca9f029261977e8dfff307315737dad276f6dca4709",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/cpi/CpiController.java": "c5f94855827bcb2889cafb52b6cbb3d8ba4e2fb081cfbb026f804405dc2e3754",
    "apps/web/src/app/market/cpi/error.tsx": "443e85f673643fac242901a6115196d70cb776c102a5c4dd96f7f4a02e19af87",
    "apps/web/src/app/market/cpi/messages.ts": "06d7ea6c94f5978170624d5c521f503667cbb2128ce73fdb41d6ab5fb965aee3",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingHistoryCollectionManifestPostgreSqlTest.java": "226b04692662d8fdf5df7994efa65f2e3dccbb06a07fe8aed027a09d78f2219c",
    "apps/web/src/lib/cpi-provider.server.test.ts": "a8b8cb448fdfea50ccaa4f81c050427e2d0d74bf80825c55a281c2143dccb62f",
    "apps/web/src/lib/cpi.test.ts": "1b6d416fa3529127e18d7ef360a812b0aceab8a4090a9fac2f0ea3a9bb8545e7",
    "apps/api/src/main/resources/db/migration/V10__bls_cpi_retrievals.sql": "4c69f27eb5aab03de0be526f1718261f36e5f3f9e535a9bebf8bb814d7540664"
}
BASELINE_RECORDS = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiProperties.java": "100644 blob 199eadfbdf3ed747a72a7b98c872c75f368e577a",
    "apps/api/src/main/resources/application.yml": "100644 blob a196ec5dbddbea9e9ad9dfd4d2ab5900f4d716b3",
    "apps/api/src/test/java/com/wallstreetreceipts/api/config/OperatorApiPropertiesTest.java": "100644 blob 8e47fd15df136ce70130f3a7f81e9e16fc3d1cd7",
    "apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiSecurityConfiguration.java": "100644 blob b25bc959179c613b8ab28f248080318a61478a66",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/security/ApiRequestRejectedHandler.java": "100644 blob 7bd5f457963bf95c4b75f0da079f746d0f579260",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorApiSecurityProblemWriter.java": "100644 blob e5b65e300d85834ca3d503dc0ddd42ef5ab794e7",
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
# Exact merged ADR-067 predecessors for pre-commit development only.
LEDGER_BASE = "7f2d5422315bfaf292fac972a90b5afcb43d5252"
LEDGER_PREVIOUS_RECORDS = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiCollectionJob.java": "100644 blob 8219fd8d5bf3d97ae97d4327300c6b8d06c4123e",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiRepository.java": "100644 blob 0b6708a1a86f23d89fa9156d60e10e87dd471014",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/ScheduleCpiCommand.java": "100644 blob 3fd44b3593505ed34752aadc0400e27bf7623164",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcCpiRepository.java": "100644 blob 99c4be446a398db3a4c7a0683aa26ef07e306f2d",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/CpiCollectionJobTest.java": "100644 blob a322a9e6fd4a74a0befe2b28dffb10e730af609e",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/cpi/ScheduleCpiCommandTest.java": "100644 blob 3cd69b7b4ea7add004ce3b394c522c9d87557a3f",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/CpiPostgreSqlTest.java": "100644 blob cb48e141290f4fc948419581ecbe274cb913cbc4",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingCollectionAttemptPostgreSqlTest.java": "100644 blob de350ff9bc336e23c3312901077402ad6c2a2d02",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingHistoryCollectionManifestPostgreSqlTest.java": "100644 blob f5790c3def614c43d4ba7849513ff3429805aa15",
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/PostgreSqlMigrationTest.java": "100644 blob 51b80fd6830809ccbd9b1586bf546f3993a792c2",
    "apps/api/src/test/java/com/wallstreetreceipts/api/release/ReleaseSchemaInventoryCommandTest.java": "100644 blob b7140828ac0b7b40132a22eb3ecdfadee47a69fb",
    "scripts/verify-cpi-worker.py": "100644 blob 19a55400aea4c35a9ff7180e67824a1c1d3c33b8",
}
# Exact merged ADR-068 overlapping repository objects, never stale working bytes.
QUERY_BASE = "e66669d27cf120c9b79d737abb91ca37cace8d32"
QUERY_PREVIOUS_RECORDS = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiRepository.java": "100644 blob f30f82838f301607523f34e10798b7bd3d21c353",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcCpiRepository.java": "100644 blob 199be96b95af36129e6951fa011ed30b9217fa2f",
}
# Exact merged ADR-071 launcher predecessor; current failure-exit fix is mandatory.
LIFECYCLE_BASE = "825369a8d75c2f5608d6782620ebc06b072ffb42"
LIFECYCLE_PREVIOUS_RECORDS = {
    "apps/web/operator/start.mjs": "100644 blob 37c4e835fc92b7b54acff5b1bfff0a9197f4a5e9",
}
# Exact merged ADR-072 objects allow pre-commit work, never stale working bytes.
READ_ONLY_BASE = "0b674db887c23315eac983b88d27a301cdb2d066"
READ_ONLY_PREVIOUS_RECORDS = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiSecurityConfiguration.java": "100644 blob 15f56127067591ee3fbaa56f5c489d63b89232fa",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/CpiOperatorBrowserIT.java": "100644 blob 1815b1cdf0fd7f9b7e8ce2bed679bf366e3745cb",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptPostgreSqlTest.java": "100644 blob 85ab3b4ff1d8b2cb56d59bef40455e292e9b8a3b",
    "scripts/cpi-operator-lifecycle-probe.mjs": "100644 blob 483291cf4e1d99b1540f7bc3598c4deacd625f4f",
    "scripts/verify-cpi-operator-lifecycle.py": "100644 blob b2462884931e9a7e5ecbe6ca3baa9c7a2bfb227a",
}
# Exact merged ADR-073 objects permit pre-commit work with mandatory bounded reads.
READ_TIMEOUT_BASE = "f98d491331b8335a16fbcffa6648353e6ab381ba"
READ_TIMEOUT_PREVIOUS_RECORDS = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcCpiRepository.java": "100644 blob a296a82293e696f7731eea2652f9db482fe429ec",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptPostgreSqlTest.java": "100644 blob 80ef20bf848a1d1e013e6a0cb8fe71dd2e8bdcc2",
}
# Exact merged ADR-074 service predecessor; working bytes must enforce admission.
READ_CONCURRENCY_BASE = "fbc53893f027b0212460f6e3382440e7ad732b08"
READ_CONCURRENCY_PREVIOUS_RECORDS = {
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/cpi/CpiAttemptQueryService.java": "100644 blob c5e70556393ef32f8460c33fe1d76ff9d09e7a7f",
}
# Exact merged ADR-075 HTTP test; working bytes must verify pool exhaustion too.
POOL_WAIT_BASE = "0c747940d9c8e603b6a30c325470d6442e015dcb"
POOL_WAIT_PREVIOUS_RECORDS = {
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorCpiAttemptConcurrencyPostgreSqlTest.java": "100644 blob b954b7a10d7d17800e2849a9f45ba3e963a1cc53",
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
        if relative in LEDGER_PREVIOUS_RECORDS:
            accepted_records.add(LEDGER_PREVIOUS_RECORDS[relative])
        if relative in QUERY_PREVIOUS_RECORDS:
            accepted_records.add(QUERY_PREVIOUS_RECORDS[relative])
        if relative in LIFECYCLE_PREVIOUS_RECORDS:
            accepted_records.add(LIFECYCLE_PREVIOUS_RECORDS[relative])
        if relative in READ_ONLY_PREVIOUS_RECORDS:
            accepted_records.add(READ_ONLY_PREVIOUS_RECORDS[relative])
        if relative in READ_TIMEOUT_PREVIOUS_RECORDS:
            accepted_records.add(READ_TIMEOUT_PREVIOUS_RECORDS[relative])
        if relative in READ_CONCURRENCY_PREVIOUS_RECORDS:
            accepted_records.add(READ_CONCURRENCY_PREVIOUS_RECORDS[relative])
        if relative in POOL_WAIT_PREVIOUS_RECORDS:
            accepted_records.add(POOL_WAIT_PREVIOUS_RECORDS[relative])
        if current.get(relative) not in accepted_records:
            raise ValueError("Unreviewed committed CPI source change: " + relative)
        if relative in current:
            adjusted[relative] = current[relative]
        else:
            adjusted.pop(relative, None)
    return adjusted
