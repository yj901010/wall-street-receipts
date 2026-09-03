python <<'PYTHON'
import base64
import hashlib
import re
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

adr043_replacements = {
    Path(".env.example"): (
        (
            b"\n# ADR-043 local-only single-operator HTTP boundary. This does not enable SEC\n"
            b"# network access. Keep disabled outside an isolated local API process; when\n"
            b"# enabled, provide only the lowercase SHA-256 digest of the canonical standard\n"
            b"# Base64 encoding of exactly 32 random bytes.\n"
            b"# Never put the raw Bearer token in this file.\n"
            b"OPERATOR_API_ENABLED=false\n"
            b"OPERATOR_API_TOKEN_SHA256=\n",
            b"",
            1,
        ),
    ),
    Path("apps/api/pom.xml"): (
        (
            b"        <dependency>\n"
            b"            <groupId>org.springframework.boot</groupId>\n"
            b"            <artifactId>spring-boot-starter-security</artifactId>\n"
            b"        </dependency>\n",
            b"",
            1,
        ),
    ),
    Path("apps/api/src/main/resources/application.yml"): (
        (
            b"app:\n"
            b"  operator-api:\n"
            b"    enabled: ${OPERATOR_API_ENABLED:false}\n"
            b"    token-sha256: ${OPERATOR_API_TOKEN_SHA256:}\n",
            b"app:\n",
            1,
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/RequestIdFilter.java"): (
        (
            b"import org.springframework.core.Ordered;\n"
            b"import org.springframework.core.annotation.Order;\n",
            b"",
            1,
        ),
        (
            b"@Component\n@Order(Ordered.HIGHEST_PRECEDENCE)\n",
            b"@Component\n",
            1,
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcSecFilingHistoryCollectionAttemptRepository.java"): (
        (
            b"import com.wallstreetreceipts.api.application.filinghistory.ExactEvidenceNotAdmittedException;\n"
            b"import com.wallstreetreceipts.api.application.filinghistory.OperatorRequestConflictException;\n",
            b"",
            1,
        ),
        (
            b"            throw new OperatorRequestConflictException();\n",
            b"            throw new IllegalArgumentException(\n"
            b"                    \"operatorRequestId is already bound to another command\");\n",
            1,
        ),
        (
            b"            return new ExactEvidenceNotAdmittedException();\n",
            b"            return new IllegalArgumentException(\n"
            b"                    \"collection attempt exact evidence was not accepted\");\n",
            1,
        ),
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/SecFilingHistoryCollectionAttemptPersistenceTest.java"): (
        (
            b"import com.wallstreetreceipts.api.application.filinghistory.ExactEvidenceNotAdmittedException;\n"
            b"import com.wallstreetreceipts.api.application.filinghistory.OperatorRequestConflictException;\n",
            b"",
            1,
        ),
        (
            b".isExactlyInstanceOf(OperatorRequestConflictException.class)",
            b".isInstanceOf(IllegalArgumentException.class)",
            1,
        ),
        (
            b".isExactlyInstanceOf(ExactEvidenceNotAdmittedException.class)",
            b".isInstanceOf(IllegalArgumentException.class)",
            2,
        ),
    ),
}
adr043_historical_sha256 = {
    Path(".env.example"): "d6687a67a4ad25d7ee2cd8ee5194d15e5b01b880e51d0708c3ce0efb3e4938f7",
    Path("apps/api/pom.xml"): "6db3f998c82f0c399b66075216b0cab22693ff91e8a7c495becf7e3fea7e28a3",
    Path("apps/api/src/main/resources/application.yml"): "fba88168c8ca0ce0c17eeea201fb624b556240ab52297f1bf33ca9388f568a42",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/RequestIdFilter.java"): "1cd35e0c8d728db84486ad626b6de6b7df6afa2777f2c35371946c58309212ec",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcSecFilingHistoryCollectionAttemptRepository.java"): "7d4633fca321607f0ee1f27700161891e3b85d37efeecf85e2d1042a39135566",
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/SecFilingHistoryCollectionAttemptPersistenceTest.java"): "e7baf3de28924f1862d0cb65062e4e21917863be74192291035e0273cf6b1448",
}
adr043_original_read_bytes = Path.read_bytes

def pre_adr043_bytes(path):
    source = adr043_original_read_bytes(path).replace(b"\r\n", b"\n")
    replacements = adr043_replacements.get(path)
    if replacements is None:
        return source
    for current, historical, count in replacements:
        require(
            source.count(current) == count,
            f"ADR-043 authorized later delta changed: {path}",
        )
        source = source.replace(current, historical)
    require(
        hashlib.sha256(source).hexdigest()
            == adr043_historical_sha256[path],
        f"ADR-043 historical reverse projection changed: {path}",
    )
    return source

Path.read_bytes = pre_adr043_bytes

pg_body_race_repository_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcFilingCatalogCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcHistoricalFilingSegmentCaptureRepository.java"),
}
pg_body_race_test_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/"
    "PostgreSqlMigrationTest.java"
)
pg_body_race_historical_sha256 = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcFilingCatalogCaptureRepository.java"): "9ed38f90ffe6fdf045dbe2396db0d7205e64117da36b1cbf4fedab2bcbee9697",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcHistoricalFilingSegmentCaptureRepository.java"): "8824e770072f578d81d1cc312f64439f788a2c11ad3c1f8ef2493c2cac944d3d",
    pg_body_race_test_path: "c9f247b743a492a69fe84ebcd99ad24f14dda77053773f9c75390b4f6beca914",
}
pg_body_race_original_read_bytes = Path.read_bytes

def pre_pg_body_race_bytes(path):
    source = pg_body_race_original_read_bytes(path).replace(b"\r\n", b"\n")
    if path in pg_body_race_repository_paths:
        current = (
            b"            // Cover both body identity constraints; a losing race is verified below.\n"
            b"            sql += \" ON CONFLICT DO NOTHING\";"
        )
        historical = (
            b"            sql += \" ON CONFLICT (decoded_body_sha256) DO NOTHING\";"
        )
        require(
            source.count(current) == 1,
            f"PostgreSQL decoded-body race repository delta changed: {path}",
        )
        source = source.replace(current, historical)
    elif path == pg_body_race_test_path:
        root_capture = (
            b"        var concurrentCapture = SecFilingCatalogCaptureTestFixture.capture(\n"
            b"                firstTime.plusSeconds(120), \"6-K\");\n"
        )
        historical_root_capture = (
            b"        var concurrentCapture =\n"
            b"                SecFilingCatalogCaptureTestFixture.capture(firstTime.plusSeconds(120));\n"
        )
        bodies_before = (
            b"        long bodiesBeforeConcurrentReplay = jdbc.getJdbcOperations().queryForObject(\n"
            b"                \"SELECT COUNT(*) FROM sec_decoded_response_bodies\", Long.class);\n"
        )
        require(
            source.count(root_capture) == 1
            and source.count(bodies_before) == 2,
            "PostgreSQL decoded-body concurrency setup delta changed",
        )
        source = source.replace(root_capture, historical_root_capture)
        source = source.replace(bodies_before, b"")
        for accessor in (b"catalog", b"segment"):
            assertion = (
                b"        assertThat(jdbc.getJdbcOperations().queryForObject(\n"
                b"                \"SELECT COUNT(*) FROM sec_decoded_response_bodies\", Long.class))\n"
                b"                .isEqualTo(bodiesBeforeConcurrentReplay + 1);\n"
                b"        assertThat(jdbc.getJdbcOperations().queryForObject(\n"
                b"                \"\"\"\n"
                b"                        SELECT COUNT(*)\n"
                b"                        FROM sec_decoded_response_bodies\n"
                b"                        WHERE decoded_body_sha256 = ?\n"
                b"                          AND decoded_body_length = ?\n"
                b"                        \"\"\",\n"
                b"                Long.class,\n"
                b"                concurrentCapture." + accessor
                + b"().sourceReceipt().decodedBodySha256(),\n"
                b"                concurrentCapture." + accessor
                + b"().sourceReceipt().decodedBodyLength()))\n"
                b"                .isEqualTo(1);\n"
            )
            require(
                source.count(assertion) == 1,
                f"PostgreSQL decoded-body {accessor.decode()} assertion delta changed",
            )
            source = source.replace(assertion, b"")
    else:
        return source
    require(
        hashlib.sha256(source).hexdigest()
            == pg_body_race_historical_sha256[path],
        f"PostgreSQL decoded-body race reverse projection changed: {path}",
    )
    return source

Path.read_bytes = pre_pg_body_race_bytes

adr037_new_main_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/source/SourceResponseReceipt.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRawResponseCapture.java"),
}
adr037_new_test_paths = {
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/source/SourceResponseReceiptTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRawResponseCaptureTest.java"),
}
require(
    len(adr037_new_main_paths) == 2
    and len(adr037_new_test_paths) == 2
    and all(path.is_file()
            for path in adr037_new_main_paths | adr037_new_test_paths),
    "ADR-036 replay must exclude only the exact ADR-037 2+2 new files",
)
adr043_new_main_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/ExactEvidenceNotAdmittedException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/OperatorRequestConflictException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptNotFoundException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptQueryService.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiProperties.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiSecurityConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorSecCollectionAttemptApiConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptController.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptExceptionHandler.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptRequests.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptResponseMapper.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptResponses.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorApiSecurityProblemWriter.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/ApiRequestRejectedHandler.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorBearerAuthenticationToken.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorBearerTokenAuthenticationFilter.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorBearerTokenAuthenticationProvider.java"),
}
adr043_new_test_paths = {
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptQueryServiceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/OperatorApiPropertiesTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/OperatorSecCollectionAttemptApiConfigurationTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptApiTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptIntegrationTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/security/OperatorApiDisabledSecurityTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/security/OperatorApiLoopbackBindingTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/security/OperatorApiSecurityTest.java"),
}
require(
    len(adr043_new_main_paths) == 17
    and len(adr043_new_test_paths) == 8
    and all(path.is_file()
            for path in adr043_new_main_paths | adr043_new_test_paths),
    "ADR-036 replay must exclude the exact ADR-043 17+8 surface",
)
adr037_new_main_paths |= adr043_new_main_paths
adr037_new_test_paths |= adr043_new_test_paths

adr038_new_main_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingSegmentDescriptor.java"),
}
adr038_new_test_paths = {
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingSegmentDescriptorTest.java"),
}
require(
    len(adr038_new_main_paths) == 1
    and len(adr038_new_test_paths) == 1
    and all(path.is_file()
            for path in adr038_new_main_paths | adr038_new_test_paths),
    "ADR-036 replay must exclude only the exact ADR-038 1+1 new files",
)
adr039_new_main_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filing/PersistFilingCatalogCaptureService.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureAppendResult.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureProvider.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureReplayVerifier.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/SecFilingCatalogPersistenceConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogCapture.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcFilingCatalogCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecFilingCatalogCaptureReplayVerifier.java"),
}
adr039_new_test_paths = {
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filing/PersistFilingCatalogCaptureServiceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogCaptureTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/FilingCatalogCapturePersistenceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/support/SecFilingCatalogCaptureTestFixture.java"),
}
require(
    len(adr039_new_main_paths) == 9
    and len(adr039_new_test_paths) == 4
    and all(path.is_file()
            for path in adr039_new_main_paths | adr039_new_test_paths),
    "Historical replay must exclude the exact ADR-039 main/test persistence delta",
)

adr040_new_main_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filingsegment/PersistHistoricalFilingSegmentCaptureService.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/HistoricalFilingSegmentCaptureAppendResult.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/HistoricalFilingSegmentCaptureProvider.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/HistoricalFilingSegmentCaptureReplayVerifier.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/HistoricalFilingSegmentCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/SecHistoricalFilingSegmentConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingRecord.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingSegment.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingSegmentCapture.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcHistoricalFilingSegmentCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecEdgarHistoricalFilingSegmentProvider.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecHistoricalFilingSegmentCaptureReplayVerifier.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecHistoricalRawResponseCapture.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecHistoricalSubmissionsMapper.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecHistoricalSubmissionsResponse.java"),
}
adr040_new_test_paths = {
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filingsegment/PersistHistoricalFilingSegmentCaptureServiceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingRecordTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingSegmentCaptureTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingSegmentTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecHistoricalFilingSegmentCaptureReplayVerifierTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecHistoricalSubmissionsMapperTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/HistoricalFilingSegmentCapturePersistenceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/support/SecHistoricalFilingSegmentCaptureTestFixture.java"),
}
require(
    len(adr040_new_main_paths) == 15
    and len(adr040_new_test_paths) == 8
    and all(path.is_file()
            for path in adr040_new_main_paths | adr040_new_test_paths),
    "Historical replay must exclude the exact ADR-040 main/test segment delta",
)

adr041_later_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/PersistFilingHistoryCollectionManifestService.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingHistoryCollectionManifestAppendOutcome.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingHistoryCollectionManifestRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/FilingHistoryCollectionConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/FilingHistoryCollectionManifest.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcFilingHistoryCollectionManifestRepository.java"),
    Path("apps/api/src/main/resources/db/migration/V8__sec_filing_history_collection_manifests.sql"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filinghistory/PersistFilingHistoryCollectionManifestServiceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/FilingHistoryCollectionManifestTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingHistoryCollectionManifestPostgreSqlTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/FilingHistoryCollectionManifestPersistenceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/support/FilingHistoryCollectionTestFixture.java"),
}
require(
    len(adr041_later_paths) == 12
    and all(path.is_file() for path in adr041_later_paths),
    "Historical replay must exclude the exact ADR-041 7+5 surface",
)

adr042_later_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/ExecuteSecFilingHistoryCollectionAttemptService.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SingleJvmSecFilingHistoryCollectionAttemptMutex.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/SecFilingHistoryCollectionAttemptClaimOutcome.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/SecFilingHistoryCollectionAttemptCommitter.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/SecFilingHistoryCollectionAttemptRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/SourceCaptureRequestException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/SecFilingHistoryCollectionAttemptConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/SecFilingHistoryCollectionAttempt.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcSecFilingHistoryCollectionAttemptRepository.java"),
    Path("apps/api/src/main/resources/db/migration/V9__sec_filing_collection_attempts.sql"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filinghistory/ExecuteSecFilingHistoryCollectionAttemptServiceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filinghistory/SingleJvmSecFilingHistoryCollectionAttemptMutexTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/SecFilingHistoryCollectionAttemptConfigurationTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/SecFilingHistoryCollectionAttemptTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecProviderExceptionTypedContractTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingCollectionAttemptPostgreSqlTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/SecFilingHistoryCollectionAttemptPersistenceTest.java"),
}
require(
    len(adr042_later_paths) == 17
    and all(path.is_file() for path in adr042_later_paths),
    "Historical replay must exclude the exact ADR-042 10+7 surface",
)
adr041_later_paths |= adr042_later_paths

adr042_provider_exception_path = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/"
    "infrastructure/provider/sec/SecProviderException.java"
)
adr042_postgres_test_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/"
    "migration/PostgreSqlMigrationTest.java"
)
adr042_original_read_bytes = Path.read_bytes

def adr042_historical_read_bytes(path):
    source = adr042_original_read_bytes(path).replace(b"\r\n", b"\n")
    if path == adr042_provider_exception_path:
        require(
            hashlib.sha256(source).hexdigest()
                == "b7600e1523a8ff97e22aa6c2ba6277ee5267459157feb28670f029cc2e5ce4f0",
            "ADR-042 typed SEC provider failure delta changed",
        )
        source = __import__("base64").b64decode(
        "cGFja2FnZSBjb20ud2FsbHN0cmVldHJlY2VpcHRzLmFwaS5pbmZyYXN0cnVjdHVyZS5wcm92aWRlci5zZWM7CgpwdWJsaWMg"
        "ZmluYWwgY2xhc3MgU2VjUHJvdmlkZXJFeGNlcHRpb24gZXh0ZW5kcyBSdW50aW1lRXhjZXB0aW9uIHsKCiAgICBwcml2YXRl"
        "IFNlY1Byb3ZpZGVyRXhjZXB0aW9uKFN0cmluZyBzYWZlTWVzc2FnZSkgewogICAgICAgIHN1cGVyKHNhZmVNZXNzYWdlKTsK"
        "ICAgIH0KCiAgICBzdGF0aWMgU2VjUHJvdmlkZXJFeGNlcHRpb24gaHR0cFN0YXR1cyhpbnQgc3RhdHVzQ29kZSkgewogICAg"
        "ICAgIHJldHVybiBuZXcgU2VjUHJvdmlkZXJFeGNlcHRpb24oIlNFQyBzdWJtaXNzaW9ucyByZXF1ZXN0IGZhaWxlZCB3aXRo"
        "IEhUVFAgIiArIHN0YXR1c0NvZGUpOwogICAgfQoKICAgIHN0YXRpYyBTZWNQcm92aWRlckV4Y2VwdGlvbiB1bnJlYWRhYmxl"
        "UmVzcG9uc2UoKSB7CiAgICAgICAgcmV0dXJuIG5ldyBTZWNQcm92aWRlckV4Y2VwdGlvbigiU0VDIHN1Ym1pc3Npb25zIHJl"
        "c3BvbnNlIGNvdWxkIG5vdCBiZSByZWFkIik7CiAgICB9CgogICAgc3RhdGljIFNlY1Byb3ZpZGVyRXhjZXB0aW9uIGludmFs"
        "aWRSZXNwb25zZSgpIHsKICAgICAgICByZXR1cm4gbmV3IFNlY1Byb3ZpZGVyRXhjZXB0aW9uKCJTRUMgc3VibWlzc2lvbnMg"
        "cmVzcG9uc2Ugd2FzIGludmFsaWQiKTsKICAgIH0KCiAgICBzdGF0aWMgU2VjUHJvdmlkZXJFeGNlcHRpb24gcmVzcG9uc2VU"
        "b29MYXJnZSgpIHsKICAgICAgICByZXR1cm4gbmV3IFNlY1Byb3ZpZGVyRXhjZXB0aW9uKCJTRUMgc3VibWlzc2lvbnMgcmVz"
        "cG9uc2UgZXhjZWVkZWQgdGhlIHNpemUgbGltaXQiKTsKICAgIH0KCiAgICBzdGF0aWMgU2VjUHJvdmlkZXJFeGNlcHRpb24g"
        "cmVxdWVzdE5vdFN0YXJ0ZWQoKSB7CiAgICAgICAgcmV0dXJuIG5ldyBTZWNQcm92aWRlckV4Y2VwdGlvbigKICAgICAgICAg"
        "ICAgICAgICJTRUMgc3VibWlzc2lvbnMgcmVxdWVzdCB3YXMgbm90IHN0YXJ0ZWQgYmVjYXVzZSB0aGUgcHJvdmlkZXIgZ2F0"
        "ZSBpcyBjbG9zZWQiKTsKICAgIH0KfQo="
        )
        require(
            hashlib.sha256(source).hexdigest()
                == "9b8a7687f31d560c5eca072cf1c01e184769a6a70ab5e19baa9b2a80d65a27ee",
            "ADR-042 embedded pre-typed-provider snapshot is invalid",
        )
    elif path == adr042_postgres_test_path:
        require(
            hashlib.sha256(source).hexdigest()
                == "c9f247b743a492a69fe84ebcd99ad24f14dda77053773f9c75390b4f6beca914",
            "ADR-042 PostgreSQL migration-test delta changed",
        )
        current = b"assertThat(flyway.info().applied()).hasSize(9);"
        historical = b"assertThat(flyway.info().applied()).hasSize(8);"
        require(
            source.count(current) == 1,
            "ADR-042 PostgreSQL Flyway-version delta changed",
        )
        source = source.replace(current, historical)
        latest_current = (
            b"assertThat(latest.info().current().getVersion().getVersion())"
            b'.isEqualTo("9");'
        )
        latest_historical = (
            b"assertThat(latest.info().current().getVersion().getVersion())"
            b'.isEqualTo("8");'
        )
        require(
            source.count(latest_current) == 3,
            "ADR-042 PostgreSQL latest-version delta changed",
        )
        source = source.replace(latest_current, latest_historical)
        require(
            hashlib.sha256(source).hexdigest()
                == "013be72bd6c110c22ec87467a000dc5e3bfe574a67e6fec11e48404305891145",
            "ADR-042 PostgreSQL reverse projection changed",
        )
    return source

Path.read_bytes = adr042_historical_read_bytes


adr040_new_main_paths |= adr041_later_paths
adr040_new_test_paths |= adr041_later_paths

adr040_live_path = Path(
    "apps/api/src/sec-live-smoke-test/java/com/wallstreetreceipts/"
    "api/config/SecEdgarLiveSmokeIT.java"
)
adr040_postgres_test_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/"
    "PostgreSqlMigrationTest.java"
)
adr040_modified_snapshots = {
    adr040_live_path: (
        "27b8066e1d47ddf39ee389527c09bdb33b6ae212d2d0580591f562ada4c5bb0e",
        "6686a85e2ad9944182a320ba55ad3fd85316540faf3bfa597162e7bbddc0f533",
    ),
    adr040_postgres_test_path: (
        "013be72bd6c110c22ec87467a000dc5e3bfe574a67e6fec11e48404305891145",
        "e6ee0f96789697414853562e47e698da088debec246f1cfe178661380163458b",
    ),
}
require(
    len(adr040_modified_snapshots) == 2
    and all(path.is_file() for path in adr040_modified_snapshots),
    "ADR-040 replay must preserve the prior live and PostgreSQL tests",
)

def pre_adr040_bytes(path):
    source = path.read_bytes().replace(b"\r\n", b"\n")
    snapshot = adr040_modified_snapshots.get(path)
    if snapshot is None:
        return source
    current_sha256, historical_sha256 = snapshot
    require(
        hashlib.sha256(source).hexdigest() == current_sha256,
        f"ADR-040 normalized modified-test delta changed: {path}",
    )
    if path == adr040_postgres_test_path:
        adr041_version_replacements = (
            (
                b"assertThat(flyway.info().applied()).hasSize(8);",
                b"assertThat(flyway.info().applied()).hasSize(7);",
                1,
            ),
            (
                b"assertThat(latest.info().current().getVersion().getVersion())"
                b'.isEqualTo("8");',
                b"assertThat(latest.info().current().getVersion().getVersion())"
                b'.isEqualTo("7");',
                3,
            ),
        )
        for current, historical_version, count in adr041_version_replacements:
            require(
                source.count(current) == count,
                "ADR-041 PostgreSQL Flyway-version delta changed",
            )
            source = source.replace(current, historical_version)
        require(
            hashlib.sha256(source).hexdigest()
                == "afb2bf1d7f7d864a3f2f2e410d50c58f397af3252041983c2b84e0c321a0b797",
            "ADR-041 PostgreSQL reverse projection changed",
        )
    historical = source.decode("utf-8")
    if path == adr040_live_path:
        added_imports = (
            "import com.wallstreetreceipts.api.domain.filing."
            "HistoricalFilingSegment;\n",
            "import com.wallstreetreceipts.api.domain.filing."
            "HistoricalFilingSegmentCapture;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec."
            "SecEdgarHistoricalFilingSegmentProvider;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec."
            "SecFilingCatalogCaptureReplayVerifier;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec."
            "SecRequestRateLimiter;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec."
            "SecRetryAfterPolicy;\n",
        )
        require(
            all(historical.count(marker) == 1 for marker in added_imports)
            and historical.count(
                "void loadsOneOfficialAppleRootAndOneCapturedDescriptorOnlyAfterBothOptIns() {"
            ) == 1,
            "ADR-040 live-smoke imports or method boundary changed",
        )
        for marker in added_imports:
            historical = historical.replace(marker, "")
        historical = historical.replace(
            "void loadsOneOfficialAppleRootAndOneCapturedDescriptorOnlyAfterBothOptIns() {",
            "void loadsOneOfficialAppleCatalogOnlyAfterBothExplicitOptIns() {",
        )
        historical, segment_count = re.subn(
            r"\n                    FilingCatalogCapture replayCheckedRoot =.*?"
            r"\n                            \"observed Apple count and filing-date extrema changed from its root descriptor\"\);",
            "",
            historical,
            flags=re.DOTALL,
        )
        require(
            segment_count == 1,
            "ADR-040 live-smoke selected-segment block changed",
        )
        historical = historical.replace(
            '"SEC live smoke must not claim fetched or complete history");\n\n'
            "                });",
            '"SEC live smoke must not claim fetched or complete history");\n'
            "                });",
        )
    else:
        added_imports = (
            "import static org.assertj.core.api.Assertions.catchThrowable;\n",
            "import com.wallstreetreceipts.api.application.port.out."
            "HistoricalFilingSegmentCaptureAppendResult;\n",
            "import com.wallstreetreceipts.api.infrastructure.persistence."
            "JdbcHistoricalFilingSegmentCaptureRepository;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec."
            "SecHistoricalFilingSegmentCaptureReplayVerifier;\n",
            "import com.wallstreetreceipts.api.support."
            "SecHistoricalFilingSegmentCaptureTestFixture;\n",
        )
        require(
            all(historical.count(marker) == 1 for marker in added_imports),
            "ADR-040 PostgreSQL imports changed",
        )
        for marker in added_imports:
            historical = historical.replace(marker, "")
        historical, test_count = re.subn(
            r"\n    @Test\n    void v7UpgradesV6AndAppendsHistoricalSegmentsAtomicallyOnPostgreSql\(\).*?"
            r"(?=\n    private static FilingCatalogCaptureAppendResult concurrentCaptureAppend\()",
            "",
            historical,
            flags=re.DOTALL,
        )
        historical, helper_count = re.subn(
            r"\n    private static HistoricalFilingSegmentCaptureAppendResult concurrentSegmentAppend\(.*?"
            r"(?=\n    private static ConcurrentCaptureAppendAttempt concurrentCaptureAppendAttempt\()",
            "",
            historical,
            flags=re.DOTALL,
        )
        historical, record_count = re.subn(
            r"\n    private record ConcurrentSegmentAppendAttempt\(.*?\n    \}\n",
            "\n",
            historical,
            flags=re.DOTALL,
        )
        require(
            (test_count, helper_count, record_count) == (1, 1, 1),
            "ADR-040 PostgreSQL historical-segment test surface changed",
        )
        historical = historical.replace(
            "\n\n    private static String concurrentContextImport(",
            "\n    private static String concurrentContextImport(",
        ).replace(
            "assertThat(flyway.info().applied()).hasSize(7);",
            "assertThat(flyway.info().applied()).hasSize(6);",
        ).replace(
            'assertThat(latest.info().current().getVersion().getVersion())'
            '.isEqualTo("7");',
            'assertThat(latest.info().current().getVersion().getVersion())'
            '.isEqualTo("6");',
        ).replace(
            '.locations("classpath:db/migration")\n'
            '                .target("6")\n'
            '                .load();',
            '.locations("classpath:db/migration")\n'
            '                .load();',
        )
    historical_bytes = historical.encode("utf-8")
    require(
        hashlib.sha256(historical_bytes).hexdigest() == historical_sha256,
        f"ADR-040 reverse projection changed: {path}",
    )
    return historical_bytes


adr037_historical_overrides = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecResponseDecompressionInterceptor.java"): (
        "67315a04ea1b8dde15129155d717a507a39d5fb7bb4bea5f24f2802e088e8bac",
        (
            "eNqlVm1v2jAQ/s6vsPgUqtb9AajTVMpapAqmwr5smiaTHMHD2J7t8LKV/77LK06gULZIJE7u7rm7x+c7NAsXLAYSqiVdMyGsMwD4"
            "C4FrZynTnHI5Mwy/J6FLDFBt1IpHYKiFsNtq8aVWxpGfbMUoV3Qw6m9C0I4r2T2QSZ24MYKzZV2WOC7oswqZgCOC31zTx6+Dz6fN"
            "U62BnAnmwNQ0S1VlYmq14TLGdJawVmZB585p+oS3J2CYku2+S/kFfiVg3XnlUHCQjvayh2fZ30CY1Ci6GGIgMc+UaGX+CcRqJS2y"
            "3bq9uiIPEKoILFFSbImbAzGFnIBECQJaAhsteMgdarBoBcZxCxGZ5vrjfo8UTsjVbUsnU9QlMy6ZwO/MWjKGsHSaeltqdGGRAS8P"
            "gmkIWCKIJafyJX9aLYLXxxGGYbAUs7fC52GOhJemQaZYXh445ps9r2sK062Db9/JVEXbuuDt/USWilUHeTFqbYl3IDDwEsIAniVJ"
            "JKyJR4eMy6CDCojmKwjKGLOAOp1uhrXLqcBNX2HhE+uYaxB/FP4tqgtpQbCPnEMe0Y1AQIwa3UOb45mdwOh4DKWXm3NLSyG583yV"
            "Gru913o5eCVx8iyPkbHE9rD8SQxu/xac2UBvE8uoaAPgsiixXWGE+yAmsHH/FUQOcFkQXitMIymWQee8z0r1Modep04d3mNpvydp"
            "36zsVamtVyO0guvWTAueQ4VdQbp+0d8ahlU26csnbqwLPGpobzSc9IeTH/1hb/QwGD42XPAZCQ7w74hMhCCvr03XlNt7weQi6NRA"
            "ygsN2kicdNxt2xR7ABN2EEtloMfwNDXAOs2t8rbLJ6oesLdTnr5dcxfOD1Kh+FeAa6TGqWe1BpOFkQ9v+jIaTY6GEKISacc4pNvX"
            "pL25yVbk5kPWARvDPfADbVC7x4ogm/UVyJHZfw4IIVgiXIqQVVyOsy+6oP1F2kSnsxVHXTrjqrlYcFLNx3YDfnfZMVgpHmG/VhYO"
            "zlpVloXYB87vu9ZfIQAtgg=="
        ),
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecResponseSizeLimitInterceptorTest.java"): (
        "d6c8eca970facf7305418b8fbc1fd8f6c263b1f5fbdf95577e59f500f2f32615",
        (
            "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"
            "TBS+llc79SR93vsbuJIQig=="
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/FilingCatalog.java"): (
        "72b13bbe1b2ab775c4a0047f02ef81a7db34c2dfdd48adef35b831374364d392",
        (
            "eNq1Vd9P2zAQfu9fcesLKRqBPW78kDrGRrXBELCnaZpc59oeJHawnQIb/d93TpPGaQtoTIsUyT7bd993952dC3ktxghSZ/GtSFPr"
            "DCL/Eil3NhY5xYnOBKl4RCmp8W6nQ1mujYMrMRWxQhd/Ox/stoyOMowHyjqhXHulcJTGx8JOLnDdyhey68xfh1conV2zst6NwTHe"
            "xWfCOTSqAfw8xTM0liGgcgv0ne3NTRhkWeHEMOU0idwVBkGPQEBu9JQSNGDQ5lpZBFLgJrhY2LCQF8OU7AQT0IYtMWxud0qb5FOS"
            "bfCxzOuhcCLV46gD1XfhDJsXrl6vWUgK6Vbskq4bG5cGrC6MxG+GGnPFzvuQaC0fu+SSra5XbJN+EMbXaG+O+bwkcABzZdge/O6U"
            "23JDU+EQ2IljniNSIoWqHHB5dPrzw+DT4PLn4eAz7Nf2mKuTU4pR9/vO1tsfv9/szLq93crfPF+tRHGsGlEIhmt/U5DBQ6G0IinS"
            "S7xz0SKJ0K2H3vlfOSiTXZ73o/B4fYKuI07+M377Q6vTwuGxc/lFXZmoqRF0F+Mwxooya38nJI22HEIlZywosqRVtFTXEnVgeLnj"
            "QBDQbSahQxpBsC0m+x5H2uASpl4vKKD/3MToW1B4C4M0xbFI+2ZcZAzr6E5i7nzw1v76C1BAVlgHSntdc3MnCI/TnnUWw+p2qXmf"
            "anVapGlUiZp5VqPG/RBB8ZbQX71nv+wPFnN+/3VU+wj28YW1N2/Ug0VrH0199hN/2POvrse9gyj0rw1EoZ6qiPAu7L4wMU/SWseK"
            "K+34Elyh1tBreqNswb6RE5riIGH45O6XxF9roToqpC+jL1PUY1H0R9z0z2niX3RRaqPtf1UfVQ4bbMu8Zyt0Xi1XLRZJUpNcWot6"
            "/4VSGGLOiQVZKLopEG7JTapHSM6r9Dil+WjWumOrK3uqKQnvteZtCQmVrU7XsL9fiubxlvbKO9OkuOYNx64/+lRPzVqBXrUejjgT"
            "Tk5YQh5SNbFRDx4ePMhYToThacyv/Ylfi7xBSI4PWwfQTBj5xs7GSy6jBj0jF0xBbf1Co+HNzlZCY3JAZVuMqP3U1Dmfdf4AfnAB"
            "vg=="
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecEdgarFilingCatalogProvider.java"): (
        "22eaf1fe072f5b00ee4f00a6e0669fb7317c35529c3af9311a484fb5538be2cb",
        (
            "eNqdV21v4jgQ/t5f4Y102kRHve3qvpxQT8dyXS260lakvU8rVSYZwK2xs7YD5br89xubhCQlQHuREI49npdnHs84GUue2BRIouZ0"
            "yYQwVgPgLwGeWUNZximXE81wPk9sroFmWi14CpoaSLonJ3yeKW3JI1swKsHS+9Gg25i0fA60L1Ty1DI/kMYyaVtWLLgZJmh/ppVU"
            "95K/ksotF/Rm/AiJNZUfSk+pyTSXU3R6Dkuln+gSxjQRHKSlIzC274fdd++4fE4gs1zJytoB1FiWCZ4wJ0+dLFW5pV+5QDt9ZplQ"
            "09sCyO4btKVqzrikE7+/qQa9yfIx2iITLpkgiWDGkBiSy3TKdKtFggYFzDEmQ9oFXk5OCD4Iy4JZIJgkuzUQWwcWuR3d/DP463L0"
            "cN0bXpILEiAfTsHZDLpHN8f3X4aDOB7cXMcPt727bw93l8Pbq96dV/TJ5OM5NwahM5/6g79/MfTRKIlqG3o3Cqv8EF1L7q4gEpOM"
            "mYF7LdqWPUMRPM/T3WXEcwQ/crQwwskrPucWYdLVeO8mq1e9CQrcKszRCp1sTpRBbVJ4MG2hlyyf1sA7DZFayM2FWrDNhaNhtogf"
            "CTBCMpXydsYNrbzFZBcHGCd/5FzDtZLXuRBhLSISVC9B1G3qKqLbr6gMnwTFaEeFR2G/gg1IJPD/O5tryBwIpgYfRlO97ap7DeYB"
            "gJqSHqbmVKl9vWHYnzcL0Bp5VOdbg2dEKJaOsOxIu5k3YXFcE/5UT2Mxm7E0hbTPHXpS6TkT/F/A19CJV5E5FoJMM8V9xos8OBoo"
            "sYBwbymgE6fTWkjDraWophfJF1eFAo9Dhn/gDsNmcOGpyAFtlOZru4vOQ3yZXUDac775JGPD80thRLHpSazhkN6psOpDdDjoj27i"
            "qDi6Pnt6VcPHPWg71/KVk0PsCtg5reozqSS2B890725ni1Gn5lN9XHN+TdCrZEbCUS5dt9y2JgLlKHrlj0X3l86dspps92C8C0xd"
            "WiIY1g0dZVDJhULrNbbQsG67wKHRLRq8LEvm3nQWOazTqFFTdqB/CzHKikKngIlubC8fmmteMaddZOvdnnUlY+x+uQlbl8vHeBly"
            "+gf5sBlSbj4/P8d5koAxk1yEUeegglBvinZnm4cy6shpfTm42T3uaG5M91XqEHqtx+EUbwXwaCBl8gZV9qqekLCu+oL89vn36A0+"
            "efZUxdLfqFZ9pUSqljJ80/YaCRulkSaFmq9Kv09T+bTh8w0Yzpgwit4Ay/qoxIETO7M2K4hVQXvE6HoPRccqXYXtR4b6u2S9zpUJ"
            "rc4Sln7sR235POB/LjVixcYC2orOLjxFESmtthTCNjPHiiFUF/rdwrp77d+vziHir0Ib92Jsg560A4mMc3uUpgnLDaRfVn7B6QTs"
            "aGGl8p0IllDcKXXF9PQIfv87Feu2Sl1c6IvK3+j87fcFh0/irgkbsuzE+fMn+YDrdO6wBxMG37+nL+ed87N1ELUJO9lkxtxRo/i9"
            "NHTbQjfBEncRw4pXvaDNj2cfo3YGSFiSgRAwZaKnp/m8nu4wwE8PMs+N+zCTFj+/yBjsEkCSc8JkSs7PSMqn3JpgB7AaZ4OzAJOV"
            "AbMhbjj1vguQUzvDOkF+de9lO1yf/AfxlsHE"
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsMapper.java"): (
        "5833a7eabc453d998d51732ef29e15bc0ede60a8df11611b827daee6afec3722",
        (
            "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"
        ),
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/SecEdgarConfigurationTest.java"): (
        "162220c172ded6c8c94a0a8cadea4597b9f9d9c3b70707465d230d1f699b313d",
        (
            "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"
        ),
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogTest.java"): (
        "99859b808cbaf7f4e6e70cd4b3a6a2e4132411b6c4a845307a3dcd27c1210b3b",
        (
            "eNrdV21v6jYU/s6viPgUNGLeCtva3asxSie0215WUm3alys3OYBbx861HWg19b/vxCSUQFhpNbW9swSK4vgcn+c8z/FxTINbOgMn"
            "kBFZUs61UQD4C4DFRhMaMxLKiDJBpowzMTupVFgUS2UcbahhgSPVjFCtQZkbEkgFdknfvmBS6GzOn1Nz8uKV/lzJpfjl/tH5DV1Q"
            "IsCQq8vRSeGlYRGQkUAfwpTMfJIB5afUQHEuMYyTT0ybRxfp9m4SwQz+x8yAsvvzwX4TcNydc2YhGVBDuZylM87flYqDI1ZsgT7y"
            "SKdMUO5km3L6g8Fw7A9Pv/R954P9PB/ZFySmSoNbbTfbPa/5g9du+q3OcevouNUlrXbnqNv7q1o7edrR+PLzYDiZjC5+/eKPzocH"
            "Ouv6zdZxs33c7DzL2aA/9q8unxPVhqNe96jTbllHdunPKZj2aSFZ6Ci4gcDo0yTmLMAdjJVcsBDUcAHCjEL8Y+berSH6uc9VZi4B"
            "eRU6K+Y6H7IHt9rE0Wk3Wz92vHQv6Wivfadjl3oumvc+OsEq125KFSKn7spiPbNcq9UKkaeDML0CIIDPU3fEOcwo76tZEuG2h3cB"
            "xCnbiSVUyeo51eegNUp0IIVBHdoA4iIATpQg967BQbp+TSDP10MpmLECDG4Buh8EaBmd/zEHMUmuI2YMWh9aNMcKpuzulE2noPSZ"
            "klHG8gG7fR7QLUS51e4+CbRbDm2tlhUe7dbIDMwZU9rgI803f5FE16DccuSHXxPKfbl/I/thSh1gBRxQIQWSjl/gLIRjxSKq7k9l"
            "YPM3pmZ+ABwCloUZd2ev5Rus739/VK3vGFnXtt0CUq3VHZFwXt+sPrsWsJqSQAGacKtzY2J93Ggsl0uiISAzuWj0VTBnSJ0GhDOq"
            "GiGmrLESUqO6Yywf321G0e6tYmjcaX6G1eXPZq8xlSo6IncRr9b+E27ExSRdKVZKD5SWLydGWaa+SbT/xr+s5l1IsabguCSuDe7t"
            "LVoH0C+0QkejXl5aPEhrS0bB3WqJ71tN7/dvjYVZIOu8UBpzMjdR9W1LN6R16rAT6LBaUpqwd5FIuKNRzIEYZPu3nckg16UzGQ6c"
            "PJQ06IOUjSfuOQuURFZLEfZFOMK+E4+0aw5jyZAbwseG9RI4te3wnMX6uXLPzuwSiqBTz2Ke5t8pkqN6WCLTZK0lqdP2wZ7HujEY"
            "/fZojNxoKZAuOya3mtP6ZgNZd/JC/5qyDJPArDspTO01p+LWoSJ08IyIIgifK9AD4bcP3gaCHl403kFKSMwTfYEk126r9tb5iR6l"
            "knawAUsDKySk2HolJlFwcOv1auWyDOKJjcqC/E6OwpMXXIQs4K8pV9v/i1laJFeqFdJYakAIeeu9auIh7Jutkrx1lS3otRDbT5vk"
            "+ZjZ1Zt1WAFGLv4/st9Seh7x0/AVbj3uqrF2tu5pm8Btf5FeplCxWyuIgpjTAMP1UnCqG+TcQX6fxLdM1ndfvFDUe6Z7Xvv7MqTf"
            "oustwovS3xX6Q+Wh8g9BwKwd"
        ),
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsMapperTest.java"): (
        "b7e28af6b95a80e0dd188a23feafc82c41ad599e11dad0bc3c26450b4e6968ef",
        (
            "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"
            "rBRcfe8To+np4C9vtQKX"
        ),
    ),
}
require(
    len(adr037_historical_overrides) == 8
    and all(path.is_file() for path in adr037_historical_overrides),
    "ADR-036 replay must reverse-project exactly four production and four test files",
)

adr038_historical_overrides = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsResponse.java"): (
        "ba88ea9c51018cfa42df754b45906c3626f2efef12acd93d9ef14e6cc8a87695",
        "3ef8e9f2664745e0b80c08c3fb801dcd213d9867c4f1e849704c73aa2f589cb7",
        (
            "eNqtVNtu2zAMffdX8DEJCvUDsg7FmhbIEGxD0g17lWUmZWNdQMrtLui/j0rSZu1SNw8zYFgiD3kOKcrJurVdIbjozb1tW8mMqK9D"
            "SlmMTWQoLNmqvXO5YzSJ4x01yEbQjauKfIqc4dbeWdNlas2MJO/tJe9So5F/+NbcKpvEYGwIMdtMuvyo++kqRMYvHBNyJpRxf3Rj"
            "s60pNC/TTFCQybb0C1XA6WgE3zA0kWFy/Rm8ze6GwgryDcLi8gKkqz2JaLAAoyT94hgoQxNRQBOD4yiywdvGJhUBdexCY/mngdFp"
            "dX5I+oA2hq9hHeJ9gDPQvuGwSl3dklMeF7mBBbrFnn2+Ix9UsHvOX1Qz6KQoP9sEZtb1Ba3/8rNxrRUZwtYJjtYnT8k05opaNQss"
            "t98h/K6qJ54jKyj4f6rYJd4r3xHOdX5CfqTlza6wFvfDf+J+xvFcQRnBd9tevAfrHG4a/anzNfLJ68hteyY2Yw+IsUzmG6DCmbIN"
            "Dgvwmnw/OPeJiuz7NeMxhfk3QZTRywH/LBav6JgdcE5DxhWyRsv3D/NZP2IatL/4Cu5RRmLyesMm0XVez/copF4Fx/q/0kPeT9lD"
            "9Qeo8I+d"
        ),
    ),
    Path("apps/api/src/sec-live-smoke-test/java/com/wallstreetreceipts/api/config/SecEdgarLiveSmokeIT.java"): (
        "6686a85e2ad9944182a320ba55ad3fd85316540faf3bfa597162e7bbddc0f533",
        "b651aea211a640a0259dd6b43225f1ee505e336bc9125586b24a133179024a88",
        (
            "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"
        ),
    ),
}
require(
    len(adr038_historical_overrides) == 2
    and all(path.is_file() for path in adr038_historical_overrides),
    "ADR-036 replay must reverse-project exactly one production and one live-smoke file",
)

def pre_adr038_bytes(path):
    source = pre_adr040_bytes(path)
    override = adr038_historical_overrides.get(path)
    if override is None:
        return source
    current_sha256, historical_sha256, compressed = override
    require(
        hashlib.sha256(source).hexdigest() == current_sha256,
        f"ADR-038 normalized delta changed: {path}",
    )
    historical = zlib.decompress(base64.b64decode(compressed))
    require(
        hashlib.sha256(historical).hexdigest() == historical_sha256,
        f"ADR-038 reverse projection does not match ea8e571: {path}",
    )
    return historical
def historical_bytes(path):
    current_bytes = pre_adr038_bytes(path)
    override = adr037_historical_overrides.get(path)
    if override is None:
        return current_bytes
    historical_sha256, compressed = override
    historical = zlib.decompress(base64.b64decode(compressed))
    require(
        hashlib.sha256(historical).hexdigest() == historical_sha256,
        f"Embedded ADR-036 historical snapshot is invalid: {path}",
    )
    return historical

def digest(paths):
    result = hashlib.sha256()
    for path in sorted(paths, key=lambda value: value.as_posix()):
        result.update(path.as_posix().encode("utf-8"))
        result.update(b"\0")
        result.update(historical_bytes(path))
        result.update(b"\0")
    return result.hexdigest()

marker = (
    "ADR-036 establishes the single-process SEC live-operation "
    "safety gate."
)
adr_path = Path(
    "decisions/ADR-036-sec-edgar-live-operation-guardrails.md"
)
marker_paths = (
    adr_path,
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
documents = {}
for path in marker_paths:
    require(path.is_file(), f"Missing ADR-036 contract document: {path}")
    source = path.read_text(encoding="utf-8")
    documents[path] = source
    require(
        source.count(marker) == 1,
        f"ADR-036 marker must occur exactly once: {path}",
    )
adr = documents[adr_path]
require(
    adr.startswith(
        "# ADR-036 — SEC EDGAR Single-Process Live-Operation Guardrails\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    ),
    "ADR-036 title, accepted status, or date changed",
)
required_adr_terms = (
    "10 requests per second in aggregate",
    "at most 8 requests per second with fixed 125 millisecond spacing",
    "8 MiB (8,388,608 bytes) after",
    "HTTP `429` never causes an automatic retry",
    "conservative 10-minute minimum",
    "Request threads are not put to sleep for 10 minutes",
    "Maven profile `sec-live-smoke`",
    "`SEC_LIVE_SMOKE=true`",
    "Apple CIK `0000320193`",
    "exactly one request",
    "Ordinary `test`/`verify`",
    "no database table",
    "scheduler",
    "controller",
    "web consumer",
)
require(
    all(term in adr for term in required_adr_terms),
    "ADR-036 operational, live-smoke, or non-publication semantics changed",
)

main_root = Path("apps/api/src/main/java/com/wallstreetreceipts/api")
test_root = Path("apps/api/src/test/java/com/wallstreetreceipts/api")
expected_main_paths = {
    main_root / "application/port/out/FilingCatalogProvider.java",
    main_root / "config/SecEdgarConfiguration.java",
    main_root / "config/SecEdgarProperties.java",
    main_root / "domain/filing/FilingCatalog.java",
    main_root / "domain/filing/FilingRecord.java",
    main_root / "infrastructure/provider/sec/SecEdgarFilingCatalogProvider.java",
    main_root / "infrastructure/provider/sec/SecProviderConfigurationException.java",
    main_root / "infrastructure/provider/sec/SecProviderException.java",
    main_root / "infrastructure/provider/sec/SecRequestRateLimitInterceptor.java",
    main_root / "infrastructure/provider/sec/SecRequestRateLimiter.java",
    main_root / "infrastructure/provider/sec/SecResponseDecompressionInterceptor.java",
    main_root / "infrastructure/provider/sec/SecResponseSizeLimitInterceptor.java",
    main_root / "infrastructure/provider/sec/SecRetryAfterPolicy.java",
    main_root / "infrastructure/provider/sec/SecStringCikDeserializer.java",
    main_root / "infrastructure/provider/sec/SecSubmissionsMapper.java",
    main_root / "infrastructure/provider/sec/SecSubmissionsResponse.java",
}
actual_main_paths = {
    *(path for path in (main_root / "domain/filing").glob("*.java")
      if path not in adr038_new_main_paths | adr039_new_main_paths
      | adr040_new_main_paths),
    *(path for path in (
        main_root / "infrastructure/provider/sec"
    ).glob("*.java") if path not in adr037_new_main_paths
      | adr038_new_main_paths | adr039_new_main_paths
      | adr040_new_main_paths),
    *(path for path in (
        main_root / "application/port/out/FilingCatalogProvider.java",
        main_root / "config/SecEdgarConfiguration.java",
        main_root / "config/SecEdgarProperties.java",
    ) if path.is_file()),
}
require(
    actual_main_paths == expected_main_paths
    and len(actual_main_paths) == 16
    and digest(actual_main_paths)
        == "ba498fe945ee318417a9115b3a1a3a593eefe4f114d45efe8a7de1da064370cb",
    "ADR-036 exact 16-file SEC production surface changed",
)

expected_test_paths = {
    test_root / "config/SecEdgarConfigurationTest.java",
    test_root / "domain/filing/FilingCatalogTest.java",
    test_root / "infrastructure/provider/sec/SecRequestRateLimitInterceptorTest.java",
    test_root / "infrastructure/provider/sec/SecRequestRateLimiterTest.java",
    test_root / "infrastructure/provider/sec/SecResponseSizeLimitInterceptorTest.java",
    test_root / "infrastructure/provider/sec/SecRetryAfterPolicyTest.java",
    test_root / "infrastructure/provider/sec/SecSubmissionsMapperTest.java",
}
actual_test_paths = {
    *(path for path in (test_root / "domain/filing").glob("*.java")
      if path not in adr038_new_test_paths | adr039_new_test_paths
      | adr040_new_test_paths),
    *(path for path in (
        test_root / "infrastructure/provider/sec"
    ).glob("*.java") if path not in adr037_new_test_paths
      | adr038_new_test_paths | adr039_new_test_paths
      | adr040_new_test_paths),
    test_root / "config/SecEdgarConfigurationTest.java",
}
require(
    actual_test_paths == expected_test_paths
    and len(actual_test_paths) == 7
    and digest(actual_test_paths)
        == "fc6b6f4dfcf25ac1da694595fd1614f1361484e04c7777973a7c9a0171f572c9",
    "ADR-036 exact seven-file SEC focused test surface changed",
)

live_path = Path(
    "apps/api/src/sec-live-smoke-test/java/com/wallstreetreceipts/"
    "api/config/SecEdgarLiveSmokeIT.java"
)
live_paths = {
    path for path in Path("apps/api/src/sec-live-smoke-test").rglob("*")
    if path.is_file()
}
require(
    live_paths == {live_path}
    and digest(live_paths)
        == "2e62a932995ce6d4d31bac7698f1d8a7daf0a067eff3d2c79b81fe00cb03460e",
    "ADR-036 isolated live-smoke source surface changed",
)

pom_path = Path("apps/api/pom.xml")
require(
    pom_path.is_file()
    and digest({pom_path})
        == "faee0805dae9dcf5e090a73d03fa16ead4a79c64fac6f2eb87bd87a1e91d3682",
    "ADR-036 Maven profile bytes changed",
)
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
pom_root = ET.parse(pom_path).getroot()
profiles = pom_root.findall("m:profiles/m:profile", namespace)
require(
    len(profiles) == 1
    and profiles[0].findtext("m:id", namespaces=namespace)
        == "sec-live-smoke"
    and profiles[0].find("m:activation", namespace) is None,
    "SEC live smoke must remain one non-activated Maven profile",
)
profile = profiles[0]
plugins = profile.findall("m:build/m:plugins/m:plugin", namespace)
by_artifact = {
    plugin.findtext("m:artifactId", namespaces=namespace): plugin
    for plugin in plugins
}
require(
    set(by_artifact) == {
        "build-helper-maven-plugin",
        "maven-surefire-plugin",
        "maven-failsafe-plugin",
    },
    "SEC live-smoke profile plugin set changed",
)
helper_sources = by_artifact[
    "build-helper-maven-plugin"
].findall(
    "m:executions/m:execution/m:configuration/m:sources/m:source",
    namespace,
)
require(
    [node.text for node in helper_sources]
        == ["src/sec-live-smoke-test/java"]
    and by_artifact["maven-surefire-plugin"].findtext(
        "m:configuration/m:skipTests", namespaces=namespace
    ) == "true",
    "Default tests and isolated live source must remain separated",
)
failsafe = by_artifact["maven-failsafe-plugin"]
require(
    [node.text for node in failsafe.findall(
        "m:executions/m:execution/m:configuration/m:includes/m:include",
        namespace,
    )] == ["**/SecEdgarLiveSmokeIT.java"]
    and failsafe.findtext(
        "m:executions/m:execution/m:configuration/m:failIfNoTests",
        namespaces=namespace,
    ) == "true"
    and failsafe.findtext(
        "m:executions/m:execution/m:configuration/m:forkCount",
        namespaces=namespace,
    ) == "1"
    and failsafe.findtext(
        "m:executions/m:execution/m:configuration/m:reuseForks",
        namespaces=namespace,
    ) == "false"
    and failsafe.findtext(
        "m:executions/m:execution/m:configuration/"
        "m:systemPropertyVariables/m:sec.live-smoke.profile",
        namespaces=namespace,
    ) == "true"
    and failsafe.findtext(
        "m:executions/m:execution/m:configuration/"
        "m:systemPropertyVariables/m:spring.profiles.active",
        namespaces=namespace,
    ) == "local",
    "SEC live smoke Failsafe double-opt-in execution contract changed",
)

sources = {
    path.name: historical_bytes(path).decode("utf-8")
    for path in expected_main_paths
}
tests = {
    path.name: historical_bytes(path).decode("utf-8")
    for path in expected_test_paths
}
configuration = sources["SecEdgarConfiguration.java"]
provider = sources["SecEdgarFilingCatalogProvider.java"]
limiter = sources["SecRequestRateLimiter.java"]
rate_interceptor = sources["SecRequestRateLimitInterceptor.java"]
size_limit = sources["SecResponseSizeLimitInterceptor.java"]
retry_after = sources["SecRetryAfterPolicy.java"]
configuration_test = tests["SecEdgarConfigurationTest.java"]
limiter_test = tests["SecRequestRateLimiterTest.java"]
size_test = tests["SecResponseSizeLimitInterceptorTest.java"]
retry_test = tests["SecRetryAfterPolicyTest.java"]
main_source = "\n".join(sources.values())
test_source = "\n".join(tests.values())

require(
    'OFFICIAL_BASE_URL = URI.create("https://data.sec.gov")'
        in configuration
    and "loopbackTestBaseUrlAllowed" in configuration
    and "productionConfigurationRejectsLoopbackTestOverride"
        in configuration_test
    and '"/submissions/CIK%s.json"' in provider
    and provider.count("restClient.get()") == 1
    and provider.count(".retrieve()") == 1
    and ".post()" not in provider,
    "SEC official-origin-only one-request transport changed",
)
interceptor_markers = (
    ".requestInterceptor(new SecRequestRateLimitInterceptor(rateLimiter))",
    ".requestInterceptor(new SecResponseSizeLimitInterceptor())",
    ".requestInterceptor(new SecResponseDecompressionInterceptor())",
)
require(
    all(marker in configuration for marker in interceptor_markers)
    and [configuration.index(marker) for marker in interceptor_markers]
        == sorted(configuration.index(marker)
                  for marker in interceptor_markers),
    "SEC rate, decoded-size, and decompression interceptor order changed",
)

size_methods = (
    "acceptsAResponseAtTheExactDecompressedBoundary",
    "rejectsADeclaredResponseLengthBeforeReadingTheBody",
    "rejectsMalformedDeclaredLengthWithoutExposingIt",
    "detectsAnUndeclaredStreamingOverrunWithoutRetainingThePayload",
    "countsSkippedBytesAgainstTheStreamingLimit",
    "reusesOneLimitedBodySoRepeatedAccessCannotResetTheCounter",
    "closesTheLimitedBodyAndResponseDelegate",
    "appliesTheLimitAfterGzipDecompression",
    "appliesTheLimitAfterDeflateDecompression",
)
require(
    "MAX_DECOMPRESSED_RESPONSE_BYTES = 8L * 1024L * 1024L"
        in size_limit
    and size_limit.index("if (!response.getStatusCode().is2xxSuccessful())")
        < size_limit.index("declaredLength =")
    and "declaredLength > maxResponseBytes" in size_limit
    and "response.close();" in size_limit
    and "limitedBody = new SizeLimitedInputStream" in size_limit
    and "limitedBody.close();" in size_limit
    and "finally" in size_limit
    and "delegate.close();" in size_limit
    and "return requireEndOfStream(super.read());" in size_limit
    and all(method in size_test for method in size_methods)
    and "opensFailClosedCooldownAfter429BeforeInspectingAnOversizedErrorBody"
        in configuration_test
    and "rejectsDeclaredIdentityResponseOverDecodedLimitBeforeParsing"
        in configuration_test,
    "Decoded 8 MiB boundary, 429 precedence, or close coverage changed",
)

limiter_methods = (
    "defaultsToEightRequestsPerSecondWithoutAccumulatingAnIdleBurst",
    "serializesConcurrentCallersIntoOneAggregateMinimumInterval",
    "rejectsRatesAboveConservativeCeilingAndNonPositiveRates",
    "blocksImmediatelyDuringCooldownWithoutSleepingOrIssuingAPermit",
    "remainsBlockedImmediatelyBeforeExpiryAndPermitsAtExpiry",
    "shorterCooldownDoesNotReduceExistingLongerRemainingCooldown",
    "rejectsInvalidOrOverflowingCooldownWithoutChangingLimiterState",
    "cooldownExpiryIsSafeAcrossNanoTimeWraparound",
    "cooldownPreemptsACallerAlreadyWaitingForRequestSpacing",
)
require(
    "DEFAULT_MAX_REQUESTS_PER_SECOND = 8" in limiter
    and "ceilDivide(NANOS_PER_SECOND, maximumRequestsPerSecond)"
        in limiter
    and "synchronized (monitor)" in limiter
    and "sleepOrFailClosed(remainingNanos);" in limiter
    and limiter.index("sleepOrFailClosed(remainingNanos);")
        < limiter.index("private static void requireMonotonicProgress")
    and "rejectActiveCooldown(observedNanos);" in limiter
    and "TimeUnit.NANOSECONDS.sleep(durationNanos)" in limiter
    and "Thread.sleep(" not in main_source
    and "DEFAULT_INTERVAL_NANOS = 125_000_000L" in limiter_test
    and "callerCount = 24" in limiter_test
    and all(method in limiter_test for method in limiter_methods),
    "Single-JVM max-8 fixed-spacing, no-burst, or race boundary changed",
)
require(
    main_source.count("new SecRequestRateLimiter()") == 1
    and "new SecRequestRateLimitInterceptor(rateLimiter)"
        in configuration
    and rate_interceptor.count("rateLimiter.acquirePermit();") == 1
    and provider.count("rateLimiter.applyCooldown(") == 1,
    "Every SEC request and cooldown must share one limiter instance",
)

retry_methods = (
    "usesMinimumCooldownWhenHeaderIsMissing",
    "usesMinimumCooldownForInvalidValue",
    "usesMinimumCooldownForNegativeDeltaSeconds",
    "raisesSubMinimumDeltaSecondsToMinimumCooldown",
    "honorsLongerDeltaSeconds",
    "honorsFutureRfc1123DateBeyondMinimum",
    "usesMinimumCooldownForPastRfc1123Date",
    "failsClosedForDeltaSecondsThatOverflowSafeNanosecondRange",
)
require(
    "if (statusCode == 429)" in provider
    and "retryAfterPolicy.cooldownFor(" in provider
    and "throw SecProviderException.httpStatus(statusCode);" in provider
    and "Duration.ofMinutes(10)" in retry_after
    and "DateTimeFormatter.RFC_1123_DATE_TIME" in retry_after
    and "FAIL_CLOSED_MAXIMUM_COOLDOWN" in retry_after
    and all(method in retry_test for method in retry_methods)
    and '"599"' in retry_test
    and '"901"' in retry_test
    and all(value not in main_source for value in (
        "@Retryable", "RetryTemplate", ".retry(", "Thread.sleep("
    ))
    and "while (" not in provider
    and "for (" not in provider,
    "429 cooldown-only, minimum-10-minute, or no-retry policy changed",
)

live_source = live_path.read_text(encoding="utf-8")
require(
    live_source.count("@Test") == 1
    and live_source.count("provider.loadCatalogCapture(APPLE_CIK)") == 1
    and "FilingCatalogCapture capture" in live_source
    and "DECODED_BODY_ATTACHED_PENDING_PERSISTENCE" in live_source
    and "capture.decodedBody().length" in live_source
    and 'APPLE_CIK = "0000320193"' in live_source
    and 'URI.create("https://data.sec.gov/submissions/CIK0000320193.json")'
        in live_source
    and '"app.public-data.sec.base-url=https://data.sec.gov"'
        in live_source
    and 'OPT_IN_ENVIRONMENT_VARIABLE = "SEC_LIVE_SMOKE"'
        in live_source
    and 'System.getProperty(PROFILE_MARKER_PROPERTY)' in live_source
    and live_source.index("requireExplicitOptIn();")
        < live_source.index("new ApplicationContextRunner()")
        < live_source.index("provider.loadCatalogCapture(APPLE_CIK)")
    and re.search(
        r"\blog\.(?:debug|info|warn|error|trace)\s*\(",
        live_source,
    ) is None
    and all(value not in live_source for value in (
        "@RepeatedTest", "@ParameterizedTest", "System.out",
        "System.err", "println(", "printf(", "printStackTrace(",
        "Logger", "Slf4j", "Repository", "DataSource",
        "Jdbc", "EntityManager", ".save(", ".insert(", "Files.",
        "ObjectMapper", "getHeaders(", "USER_AGENT",
        "SEC_CONTACT_EMAIL", "HttpClient.new", "WebClient.create",
    )),
    "Double-opt-in one-call Apple live smoke or no-output boundary changed",
)

forbidden_layer_markers = (
    "@RestController", "@Controller", "@Repository",
    "@RequestMapping", "@GetMapping", "@PostMapping",
    "@Scheduled", "SchedulingConfigurer", "CommandLineRunner",
    "ApplicationRunner", "JdbcTemplate", "EntityManager",
    "Flyway", "OpenAPI", "Kafka",
)
require(
    all(value not in main_source + live_source
        for value in forbidden_layer_markers),
    "ADR-036 must not add controller, persistence, scheduler, or publication wiring",
)
migration_paths = {
    path.name for path in Path(
        "apps/api/src/main/resources/db/migration"
    ).glob("*.sql")
}
require(
    migration_paths == {
        "V1__baseline.sql",
        "V2__analyst_calls.sql",
        "V3__analyst_call_revisions.sql",
        "V4__call_outcomes.sql",
        "V5__call_contexts.sql",
        "V6__sec_filing_catalog_captures.sql",
        "V7__sec_historical_filing_segment_captures.sql",
        "V8__sec_filing_history_collection_manifests.sql",
        "V9__sec_filing_collection_attempts.sql",
    },
    "ADR-036 replay must recognize only the exact ADR-039 through ADR-042 Flyway deltas",
)
publication_markers = (
    "secedgar", "secsubmissions", "filingcatalog", "sec-edgar",
    "edgar-submissions-api", "app.public-data.sec",
    "/submissions/cik", "/archives/edgar/data",
)
publication_paths = {
    Path("contracts/openapi.yaml"),
    *Path("schemas").glob("*.json"),
    *(path for path in Path("fixtures/v1").rglob("*") if path.is_file()),
    *(path for path in main_root.rglob("*.java")
      if path not in expected_main_paths | adr037_new_main_paths
      | adr038_new_main_paths | adr039_new_main_paths
      | adr040_new_main_paths),
    *(path for path in test_root.rglob("*.java")
      if path not in expected_test_paths | adr037_new_test_paths
      | adr038_new_test_paths | adr039_new_test_paths
      | adr040_new_test_paths
      | {test_root / "migration/PostgreSqlMigrationTest.java"}),
    *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
      for path in root.rglob("*") if path.is_file()),
}
for path in publication_paths:
    source = path.read_text(
        encoding="utf-8", errors="ignore"
    ).lower()
    require(
        not any(marker in source for marker in publication_markers),
        f"ADR-036 must not publish SEC data or configuration: {path}",
    )

require(
    "MockRestServiceServer.bindTo(restClientBuilder)"
        in configuration_test
    and all(value not in test_source for value in (
        "HttpClient.newHttpClient(", "RestClient.create(",
        "WebClient.create(", ".openConnection(", "new Socket(",
    )),
    "Ordinary SEC tests must remain deterministic and offline",
)
workflow = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
guard_step = (
    "\n      - name: Guard SEC EDGAR single-process "
    "live-operation safety gate\n"
)
guard_start = workflow.index(guard_step)
guard_end = workflow.index(
    "\n      - name: ", guard_start + len(guard_step)
)
workflow_without_this_guard = workflow[:guard_start] + workflow[guard_end:]
adr037_guard_step = (
    "\n      - name: Guard SEC EDGAR decoded-response "
    "receipt foundation\n"
)
adr037_guard_start = workflow_without_this_guard.index(adr037_guard_step)
adr037_guard_end = workflow_without_this_guard.index(
    "\n      - name: ",
    adr037_guard_start + len(adr037_guard_step),
)
workflow_without_this_guard = (
    workflow_without_this_guard[:adr037_guard_start]
    + workflow_without_this_guard[adr037_guard_end:]
)
adr038_guard_step = (
    "\n      - name: Guard SEC EDGAR historical-segment "
    "descriptor catalog\n"
)
adr038_guard_start = workflow_without_this_guard.index(adr038_guard_step)
adr038_guard_end = workflow_without_this_guard.index(
    "\n      - name: ",
    adr038_guard_start + len(adr038_guard_step),
)
workflow_without_this_guard = (
    workflow_without_this_guard[:adr038_guard_start]
    + workflow_without_this_guard[adr038_guard_end:]
)
adr039_guard_step = (
    "\n      - name: Guard SEC EDGAR append-only "
    "capture persistence\n"
)
adr039_guard_start = workflow_without_this_guard.index(adr039_guard_step)
adr039_guard_end = workflow_without_this_guard.index(
    "\n      - name: ",
    adr039_guard_start + len(adr039_guard_step),
)
workflow_without_this_guard = (
    workflow_without_this_guard[:adr039_guard_start]
    + workflow_without_this_guard[adr039_guard_end:]
)
adr040_guard_step = (
    "\n      - name: Guard SEC EDGAR historical-segment "
    "append-only persistence\n"
)
adr040_guard_start = workflow_without_this_guard.index(adr040_guard_step)
adr040_guard_end = workflow_without_this_guard.index(
    "\n      - name: ",
    adr040_guard_start + len(adr040_guard_step),
)
workflow_without_this_guard = (
    workflow_without_this_guard[:adr040_guard_start]
    + workflow_without_this_guard[adr040_guard_end:]
)
adr041_guard_step = (
    "\n      - name: Guard SEC EDGAR ordered filing-history "
    "collection manifest\n"
)
adr041_guard_start = workflow_without_this_guard.index(adr041_guard_step)
adr041_guard_end = workflow_without_this_guard.index(
    "\n      - name: ",
    adr041_guard_start + len(adr041_guard_step),
)
workflow_without_this_guard = (
    workflow_without_this_guard[:adr041_guard_start]
    + workflow_without_this_guard[adr041_guard_end:]
)
adr042_guard_step = (
    "\n      - name: Guard SEC EDGAR operator-controlled "
    "bounded collection attempt\n"
)
adr042_guard_start = workflow_without_this_guard.index(adr042_guard_step)
adr042_guard_end = workflow_without_this_guard.index(
    "\n      - name: ",
    adr042_guard_start + len(adr042_guard_step),
)
workflow_without_this_guard = (
    workflow_without_this_guard[:adr042_guard_start]
    + workflow_without_this_guard[adr042_guard_end:]
)
require(
    workflow_without_this_guard.count(
        "run: ./mvnw -B -ntp verify"
    ) == 1
    and all(marker not in workflow_without_this_guard for marker in (
        "SEC_LIVE_SMOKE", "-Psec-live-smoke",
        "src/sec-live-smoke-test", "SecEdgarLiveSmokeIT",
        "sec.live-smoke.profile",
    ))
    and re.search(
        r"(?:curl|wget|Invoke-WebRequest)[^\n]*data\.sec\.gov",
        workflow_without_this_guard,
        re.IGNORECASE,
    ) is None,
    "Default Maven/CI must never activate or invoke the SEC live smoke",
)

config_paths = {
    Path(".env.example"),
    Path("apps/api/src/main/resources/application.yml"),
    Path("apps/api/src/main/resources/application-local.yml"),
    Path("apps/api/src/test/resources/application-test.yml"),
}
require(
    len(config_paths) == 4
    and digest(config_paths)
        == "c667d625d56663217470dd9652ae0c6da9225c48427919009717e1148bc5c328",
    "ADR-036 must preserve the disabled server-only SEC configuration",
)
protected_paths = {
    Path("contracts/openapi.yaml"),
    *Path("schemas").glob("*.json"),
    *(path for path in Path("fixtures/v1").rglob("*") if path.is_file()),
    *(path for path in Path("apps/api/src/main").rglob("*")
      if path.is_file() and path not in adr037_new_main_paths
      | adr038_new_main_paths | adr039_new_main_paths
      | adr040_new_main_paths
      and path not in {
          Path(
              "apps/api/src/main/resources/db/migration/"
              "V6__sec_filing_catalog_captures.sql"
          ),
          Path(
              "apps/api/src/main/resources/db/migration/"
              "V7__sec_historical_filing_segment_captures.sql"
          ),
      }),
}
test_web_paths = {
    *(path for path in Path("apps/api/src/test").rglob("*")
      if path.is_file() and path not in adr037_new_test_paths
      | adr038_new_test_paths | adr039_new_test_paths
      | adr040_new_test_paths),
    *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
      for path in root.rglob("*") if path.is_file()),
    *(path for path in Path("apps/web").glob("*")
      if path.is_file() and path.name != "next-env.d.ts"),
}
dependency_runtime_paths = {
    Path("apps/api/pom.xml"),
    Path("apps/api/mvnw"),
    Path("apps/api/mvnw.cmd"),
    Path("apps/api/.mvn/wrapper/maven-wrapper.properties"),
    Path("package.json"),
    Path("pnpm-lock.yaml"),
    Path("pnpm-workspace.yaml"),
    Path("compose.yaml"),
    Path(".env.example"),
}
require(
    len(protected_paths) == 249
    and digest(protected_paths)
        == "f569133da2228f1fc110f53d7c132a47a946a2655f05d0f899213ff30537d0e4",
    "ADR-036 protected production/config baseline changed",
)
require(
    len(test_web_paths) == 212
    and digest(test_web_paths)
        == "4c3735d6816a92fc5f3df28799e8e4a95c628d68bfb3c4b74cb95f69887ccc77",
    "ADR-036 API-test/web baseline changed",
)
require(
    len(dependency_runtime_paths) == 9
    and all(path.is_file() for path in dependency_runtime_paths)
    and digest(dependency_runtime_paths)
        == "8cccb8b3593eddc92713eab6972edaf91b6adf0db63009de20aa799220adf691",
    "ADR-036 dependency/runtime baseline changed",
)

print(
    "Validated ADR-036 exact 16+7+1 SEC surfaces, official-only "
    "single-request transport, decoded 8 MiB bound, shared max-8 "
    "fixed spacing, 429 cooldown-only handling, double-opt-in "
    "one-call Apple smoke, default-offline CI, and publication firewall"
)
PYTHON
