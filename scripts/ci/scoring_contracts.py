"""ADR-080 through 088: exact scoring custody; old calculators and link predecessors stay frozen."""
from __future__ import annotations

import hashlib
from pathlib import Path
import stat
from current_contracts import blob_record

CONTENT_SHA256 = {
    "TARGET_HIT_SCORING_RECEIPTS.md": "7893ee9a473b97f27581df6e785452e48b28f91858023108202af74c6c0ed869",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/DemoTargetHitScoringReceiptCommand.java": "2861dc10b71f97fc650cea89085e1837031d9d11732cd4c28e83999db48e86a8",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptBrowserIT.java": "5dae071c9a4d6a71c6d5b98286470d950e0e544749985ab957bf4cd0e68ad903",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/DemoTargetHitScoringReceiptCommandTest.java": "22935d17e050e88ed6750dde73983786e6c2099e3c6fc1ad34ca317078aa52ba",
    "apps/web/target-hit-scoring/full-stack.config.ts": "29cd538ed46b8f547754c205c672e6e1496a32111308422c436929ca5409ef88",
    "apps/web/target-hit-scoring/public.config.ts": "58767b32c2579e5fc486e0b79f9bdec1f96c05b0a677bd99fdc3f5386927307c",
    "apps/web/target-hit-scoring/tests/receipts.spec.ts": "be4ba4e6a93bf38da424813d2e8f090745c3548c2482a03fbb0d5bfe2faabf07",
    "apps/web/e2e/target-hit-scoring-receipts.spec.ts": "f3a1ba9ab5f83c7e31d8c9768ca5b1bd7e62a7bc6b90e15f03235f15dd60914f",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/loading.tsx": "15c4d3dde69cc9e25bc69c9ee003d1fba2e66889032d7934fdea3223b3fba4f5",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/messages.ts": "61c33d3907664fd3320b37a0f8ff869dc3f071c2e5956e3d1c9abfc75fc1b2c9",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/page.test.tsx": "336f6e59f44540159f2af6bd6db9d4570d89ad56baef845b6d97d62f62ece4b8",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/page.tsx": "0000a3ebd21e808a6480276a31ddb9a417a3ac0dd1f4da990b5cc4a2429c97ec",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/receipt-view.test.tsx": "5bf0afbf69d57a4832e45a37f056944c0870a4f50a3dfaa7c4dcb1989d17700e",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/receipt-view.tsx": "ad6783e7c4402316c39c72d591317ae8c1684a131888daba02ad35e1d628cd2b",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/receipts.module.css": "ac5d615e1aae5d858abf574cfa6485cf146db1970c48829a2f9401062f0b3e6f",
    "apps/web/src/lib/target-hit-scoring-receipts.fixture.ts": "5193ae3edcb4ac9d09d570b9e5aeaf54b2b55de746edffa011db9d287f2a1dd4",
    "apps/web/src/lib/target-hit-scoring-receipts.server.test.ts": "3676226abfe96d39fe13798a7a6beb33ef68561899fd857710818ab3d325e10a",
    "apps/web/src/lib/target-hit-scoring-receipts.server.ts": "4012adb0a3ff39b746cf3a40d4de38ecbc75124ca95ed45407a327911ff655d7",
    "apps/web/src/lib/target-hit-scoring-receipts.test.ts": "73589ed18964283a179d74f6e9066bc401f9b6e1aa19f9201a7511bc61741550",
    "apps/web/src/lib/target-hit-scoring-receipts.ts": "ae3c257a39289dce25d4c30aadfe5b791d8d7851fa19d33b2ac543a35dd7ce31",
    "contracts/target-hit-scoring-receipts.openapi.yaml": "5b8aa986ea4457a048ea99fff8bc427981b16e7d7e65abc19e0647c71d51d6a9",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptResponseTest.java": "f51ee11c8e0e42feb81c4e0ebc49837e4f185c60bea7b0186bddd725da82db4f",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptPostgreSqlTest.java": "364f15d57326518a7978fec46167a9bae762d35d51a5d0d528eb751c8945503d",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptBoundaryTest.java": "a40f70de0c11a060ba5d95dde41fdf5fdeb53e7566d062c3d3251c682e64554f",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptApiTest.java": "7da400323ae123bd21cce2c6d0b2d75f62e03ebf3b38de454ee2387b9a35b139",
    "apps/api/src/main/resources/db/migration/V14__demo_target_hit_scoring_receipts.sql": "6a9ed80b22e1baedb98adadb7191b24364ed8ecbee61efe49290c8831aae5d96",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/scoring/TargetHitScoringReceiptResponse.java": "296e8a0ae876800bf3afc07ccc0cafbe2046fca864d892ddfb59fb4532cdb1ae",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/scoring/TargetHitScoringReceiptController.java": "77de7286f7c75d18069a817c559af9687e93fc7ffeeba1949bac194ec3bf82fc",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptService.java": "edf0f2e59c7d24b75525b22c4a94ebca77e52ea1feba6cd7e3c60a92092f4e23",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcTargetHitScoringReceiptRepository.java": "6d6c1521d419ef535700b57fa9cff832a9abaf520fa3fd949ddfc89ef464d2cb",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/TargetHitScoringReceiptRepository.java": "c280103543ccdfb87e6be1fa693b390db98d1dacd7dcb0ed7e874594a25271bf",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringInput.java": "77968b6fbc1634c42796eed653efe7d31f5186cfcd0342086f1e4e51e394a330",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringEvaluator.java": "e910480b2d0a0f21e857d38583ff9236c4946491275d1dbad1aa4796cc0205f1",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringMethodology.java": "ebfb6ba070bd31ecd7cdaa623eea9b75fe40760cecd6136c5d8c65abdc0798e6",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringInputCodec.java": "ed32a0e1534b2a6992d1982bc41190af3e67aeaaf386cd4815a89ba783c252dd",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringFixture.java": "46e124f252e8115d8fe88c656ed0f8e9747770a172c1a3f9bd8b7cf375198c92",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringInputTest.java": "106559ad7e759fc0b5f933d49a86ca711aabbea46ffe8dc9d5cc3cc82ff0e7cd",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringEvaluatorTest.java": "535754e483c3345582e9fa2ebffc3f68d84498f73b8b3f556833e40f4a89cc16",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringInputCodecTest.java": "06651de5a45214f4d532887cb59efd97c41ee17196d7da72b89150920b7c3f4f",
    "apps/web/src/lib/comparative-scoring-receipts.ts": "116addb59a0ce9fe856873adee988962ebf8a3caa2ecc7175ac6ef79a8214c88",
    "apps/web/src/lib/comparative-scoring-receipts.test.ts": "8ed0aa14e8f5161a14ba4c9737de89dfe9c067d3beb4ef0ab78adb774965ccb3",
    "apps/web/src/lib/comparative-scoring-receipts.server.ts": "9e21c3020b7b4e278124e97bfbd5658abf98ddf6f5f6adac6209373b00f3e62d",
    "apps/web/src/lib/comparative-scoring-receipts.server.test.ts": "3c143e3321e4dfc0707d61ae914eaeb88748f17c61e1450c170fe6a469206b40",
    "apps/web/src/lib/comparative-scoring-receipts.fixture.ts": "3686a489039f0b10dcb2a917cb288ccf2985811c9c8aa607723c66ff40df295c",
    "apps/web/src/app/calls/[id]/comparative-scoring-receipts/receipts.module.css": "d6e9d10258e44168568f944084c64882bfadd6b061d89c36a47af31ee22d2038",
    "apps/web/src/app/calls/[id]/comparative-scoring-receipts/receipt-view.tsx": "e6fecf33ce7fa1c35881b01b849bed446b061ae4ba76c14b924a74e242d2f4fa",
    "apps/web/src/app/calls/[id]/comparative-scoring-receipts/receipt-view.test.tsx": "46d3cfa2028afc47b21e85b70b32fc7f0eaf4c555edfc6139f66b6896ea37062",
    "apps/web/src/app/calls/[id]/comparative-scoring-receipts/page.tsx": "3b66321665c2547515070b6f64d8e97f5a2ca9a782beb36300db63a3dfacfc6f",
    "apps/web/src/app/calls/[id]/comparative-scoring-receipts/page.test.tsx": "147c614e1267f3c4190f3c86eef23638537c3df137f6fef35ede2c9d9883d266",
    "apps/web/src/app/calls/[id]/comparative-scoring-receipts/messages.ts": "2278d7ff1620df9f0deab84b999b6a97f1259e7777408ef0f1fd28cd4626ff63",
    "apps/web/src/app/calls/[id]/comparative-scoring-receipts/loading.tsx": "a65a0b68062672981caf1a38f6225662d94950f3159b3ee97dcde71c59a09d15",
    "apps/web/e2e/comparative-scoring-receipts.spec.ts": "992d02f31bde60861661e6825f2f2b1d08525bdc176f09327930e3010da5d9eb",
    "apps/web/comparative-scoring/tests/receipts.spec.ts": "7d90ec0e883646f8023d100bf33e0c02368988c37da033b2bc02bca2fd7aef92",
    "apps/web/comparative-scoring/public.config.ts": "1b4729d870f7f2b5f95fe7b5e73db831618fd2e2ce13c628ad0bc04d01e546ab",
    "apps/web/comparative-scoring/full-stack.config.ts": "024ff7fe449558ae639593a0deea0351570ed234ea880330c86cae2457a9f6b5",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/DemoComparativeScoringReceiptCommandTest.java": "4635e3ed9762866fc4bdc2b834e26e6682c6725c6a43ec8b74927b5ce4baaf5f",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringReceiptBrowserIT.java": "4e859dd554c2df5ad9f11ad02d658510d4d8d5ec5a083aedc362a5171f6d5e05",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/DemoComparativeScoringReceiptCommand.java": "818b96be013330a0072d9864da71b848baf65230b1e32ac56c61a2350e9d5d00",
    "COMPARATIVE_SCORING_RECEIPTS.md": "4eaf48ddd621697647d9f737275f4db0635481f92ced53924c42a26495daf86e",
    "contracts/comparative-scoring-receipts.openapi.yaml": "3ba5f76e502372db9a00ae76a48f80dc3049a0706217bbd25e14b06dbe5b99db",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringReceiptResponseTest.java": "0751add4b0e915097641ca838c540a29ee37f593be521a2c8f18fdd4038ad52c",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringReceiptPostgreSqlTest.java": "48e0457c617371251b10e719da7cfe8583af25b75502fa1a97c6543953c72613",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringReceiptBoundaryTest.java": "74a72759f860ea32b3e1fa84b272e67a1ded4ca7aaf2a275a5e4775d4db5e15f",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringReceiptApiTest.java": "f065f57089dea2172606fa752f5294b3c9d213ffdab3f019bddbcb91622e16f5",
    "apps/api/src/main/resources/db/migration/V13__demo_comparative_scoring_receipts.sql": "672d655fb7e224dc3d7c5d1462ee1d6fd669be810ec5acfacdb13abbb6637fce",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/scoring/ComparativeScoringReceiptResponse.java": "f9a2529c3343308ab2b7afcbaac42f40472e5476862f4f6524b2f299f6cb9e9b",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/scoring/ComparativeScoringReceiptController.java": "0b988590f0fbc79b82e1e3590942bb622130782029add4f961fc3844cb161e65",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcComparativeScoringReceiptRepository.java": "d37f3a946432dde492501ee11ed6c014a657e0bae43476f3d0e7f25bda31a5d3",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringReceiptService.java": "09d63369faf041a5e71d51a28caabab79b5949ca14a8ebd2eae48349357d4bf8",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/ComparativeScoringReceiptRepository.java": "652043d3358d5c3c73aaaae771db12d6e4d17aada73bab02f82baba78ab6843e",
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
    "apps/web/src/app/calls/[id]/page.tsx": "325bb336b4c1f6c71a53b5b4f19a716e359b828fec2d143def41a5633a09814c",
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
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ScoringReceiptPostgreSqlTest.java": "e2215d74aab9fab8e330e63bab735bd3095c05bf36102b8df16aa72fde7689ef",
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

# Only the old upgrade acceptance is pinned to its explicit V11 -> V12 target.
# New V12 -> V13 acceptance executes separately; no old product or golden edit.
COMPARATIVE_RECEIPT_BASE = "a68bae277391bc99950564642fddef6a166fa62d"
COMPARATIVE_RECEIPT_PREVIOUS_RECORDS = {
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ScoringReceiptPostgreSqlTest.java": "100644 blob f20be94fdfd322819f2ba54e99c469177a10e3b3",
}

COMPARATIVE_AUDIT_BASE = "a2deca96386f5c8d0e38e263a8a493db4af16e77"
COMPARATIVE_AUDIT_PREVIOUS_RECORDS = {
    "apps/web/src/app/calls/[id]/page.tsx": "100644 blob 91c5471f9279c9cdda9721d018ff465726b5e4b0",
}


TARGET_HIT_RECEIPT_BASE = "718b03209ee19c69c665f795a2598c8702d07ab2"
TARGET_HIT_RECEIPT_PREVIOUS_RECORDS = {
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/ComparativeScoringReceiptPostgreSqlTest.java": "100644 blob f6612f2896743bfcb1b024113f784c1aa43ee2f3",
}
TARGET_HIT_RECEIPT_ADDITIONS = frozenset({
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/TargetHitScoringReceiptRepository.java",
    "apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcTargetHitScoringReceiptRepository.java",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptService.java",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/scoring/TargetHitScoringReceiptController.java",
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/scoring/TargetHitScoringReceiptResponse.java",
    "apps/api/src/main/resources/db/migration/V14__demo_target_hit_scoring_receipts.sql",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptApiTest.java",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptBoundaryTest.java",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptPostgreSqlTest.java",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptResponseTest.java",
    "contracts/target-hit-scoring-receipts.openapi.yaml",
})


TARGET_HIT_AUDIT_BASE = "9b80fe0985933e3752687074ae2ca1f2b6707fb5"
TARGET_HIT_AUDIT_PREVIOUS_RECORDS = {
    "apps/web/src/app/calls/[id]/page.tsx": "100644 blob 7ddf31756ac3f57ee6aeaade527827edace04aea",
}
TARGET_HIT_AUDIT_ADDITIONS = frozenset({
    "TARGET_HIT_SCORING_RECEIPTS.md",
    "apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/DemoTargetHitScoringReceiptCommand.java",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/TargetHitScoringReceiptBrowserIT.java",
    "apps/api/src/test/java/com/wallstreetreceipts/api/application/scoring/DemoTargetHitScoringReceiptCommandTest.java",
    "apps/web/target-hit-scoring/full-stack.config.ts",
    "apps/web/target-hit-scoring/public.config.ts",
    "apps/web/target-hit-scoring/tests/receipts.spec.ts",
    "apps/web/e2e/target-hit-scoring-receipts.spec.ts",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/loading.tsx",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/messages.ts",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/page.test.tsx",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/page.tsx",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/receipt-view.test.tsx",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/receipt-view.tsx",
    "apps/web/src/app/calls/[id]/target-hit-scoring-receipts/receipts.module.css",
    "apps/web/src/lib/target-hit-scoring-receipts.fixture.ts",
    "apps/web/src/lib/target-hit-scoring-receipts.server.test.ts",
    "apps/web/src/lib/target-hit-scoring-receipts.server.ts",
    "apps/web/src/lib/target-hit-scoring-receipts.test.ts",
    "apps/web/src/lib/target-hit-scoring-receipts.ts",
})


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
        accepted_records = {original, blob_record(raw)}
        if relative in COMPARATIVE_RECEIPT_PREVIOUS_RECORDS:
            accepted_records.add(COMPARATIVE_RECEIPT_PREVIOUS_RECORDS[relative])
        if relative in COMPARATIVE_AUDIT_PREVIOUS_RECORDS:
            accepted_records.add(COMPARATIVE_AUDIT_PREVIOUS_RECORDS[relative])
        if relative in TARGET_HIT_AUDIT_PREVIOUS_RECORDS:
            accepted_records.add(TARGET_HIT_AUDIT_PREVIOUS_RECORDS[relative])
        if relative in TARGET_HIT_RECEIPT_PREVIOUS_RECORDS:
            accepted_records.add(TARGET_HIT_RECEIPT_PREVIOUS_RECORDS[relative])
        if current.get(relative) not in accepted_records or (original is not None and relative not in current):
            raise ValueError("Unreviewed committed scoring source: " + relative)
        if relative in current:
            adjusted[relative] = current[relative]
    return adjusted
