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
    "ADR-037 replay must exclude only the exact ADR-038 1+1 new files",
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
    "ADR-037 replay must exclude the exact ADR-043 17+8 surface",
)
adr038_new_main_paths |= adr043_new_main_paths
adr038_new_test_paths |= adr043_new_test_paths

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
        for marker in (
            "import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;\n",
            "import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecEdgarHistoricalFilingSegmentProvider;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecFilingCatalogCaptureReplayVerifier;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRequestRateLimiter;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRetryAfterPolicy;\n",
        ):
            historical = historical.replace(marker, "")
        historical = historical.replace(
            "void loadsOneOfficialAppleRootAndOneCapturedDescriptorOnlyAfterBothOptIns() {",
            "void loadsOneOfficialAppleCatalogOnlyAfterBothExplicitOptIns() {",
        )
        historical = re.sub(
            r"\n                    FilingCatalogCapture replayCheckedRoot =.*?"
            r"\n                            \"observed Apple count and filing-date extrema changed from its root descriptor\"\);",
            "",
            historical,
            flags=re.DOTALL,
        ).replace(
            '"SEC live smoke must not claim fetched or complete history");\n\n'
            "                });",
            '"SEC live smoke must not claim fetched or complete history");\n'
            "                });",
        )
    else:
        for marker in (
            "import static org.assertj.core.api.Assertions.catchThrowable;\n",
            "import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;\n",
            "import com.wallstreetreceipts.api.infrastructure.persistence.JdbcHistoricalFilingSegmentCaptureRepository;\n",
            "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecHistoricalFilingSegmentCaptureReplayVerifier;\n",
            "import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;\n",
        ):
            historical = historical.replace(marker, "")
        historical = re.sub(
            r"\n    @Test\n    void v7UpgradesV6AndAppendsHistoricalSegmentsAtomicallyOnPostgreSql\(\).*?"
            r"(?=\n    private static FilingCatalogCaptureAppendResult concurrentCaptureAppend\()",
            "",
            historical,
            flags=re.DOTALL,
        )
        historical = re.sub(
            r"\n    private static HistoricalFilingSegmentCaptureAppendResult concurrentSegmentAppend\(.*?"
            r"(?=\n    private static ConcurrentCaptureAppendAttempt concurrentCaptureAppendAttempt\()",
            "",
            historical,
            flags=re.DOTALL,
        )
        historical = re.sub(
            r"\n    private record ConcurrentSegmentAppendAttempt\(.*?\n    \}\n",
            "\n",
            historical,
            flags=re.DOTALL,
        ).replace(
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


adr038_historical_overrides = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/FilingCatalog.java"): (
        "64b745b1c69b3bfefea042b9bdd2b54d5bbc73b0f0d5e5e68e387f64d59213e1",
        "bb8a9ffbc3817ce387ab96224561eaa6472890df9632c115153398dbbfce0068",
        (
            "eNqtVt1v2zYQf/dfcfFL5KBh08c1H4CXZauxNQ2S9KkYCpo622xkUiUpJ1nr/31HiZIoW46xrgIMk3e8798dmXPxwOcIQi/ZI88y"
            "6wwi/QTK3FnGc8lSveRSsZnMpJqfDgZymWvj4AtfcabQsY+3k9MO0cklsomyjivX5RROZuwdt4s77OP8JW0f+cP0Cwpnezj9agzO"
            "8YndcOfQqNbh/SHeoLHkAiq36f1+WasLI5DdlX+3aHOtLP2XB8mH10dHMFkuC8enGWWb564wCHoGHHKjVzJFAyZIgVTgFtgwDi3k"
            "xTSTdoEpaEMUBkevByVNkJQgGvxelueSO57peTKA8N05Q+RG1aseRloIt0UX8qGlUYWhiu+jkS05JMnrEGgtid1T5bf5Idp0HJvp"
            "y1MwEnbtYY+LsyrA2zLaC6jQaEfwbVAey41ccYdAFh0lZSYVzyBAAO6vrj//Nvljcv/5cvInnNd0RlXNZYbJ8NPJ8S9/f3tzsh6O"
            "ToO+KrmdrJKt2qPYGcLb10IavORKKyl4do9PLmkyDsN66ZX/JwVlZUp5v4rFawn5kFCl9ugdT63OCofvnMvv6jImbUFh2KxjG1vd"
            "UOt7L4XRlkyo9IbQJ63UKtkAQel1RPhxxRF6YNhuYoVyBtExJu2vONMGN3wajaIC+s8tjH4EhY8wyTKc82xs5sWS3Lp6Epg7b7xz"
            "vv4iL2BZWAdK+yYg1KYIu8NeN6sw0Oqwr7W6LrIs6aK/rkrdG42lKYKi45sJOKhhxkgrz2xXHau5yWi0FdT373AQQLZT2DN3yTbw"
            "6Zdu2Lvko9r1Kmj5pOGnVbEnvZQg5eTsmZRKC/jEhZ9eVfMHJ7oV3VfSMKaomGH1YhnrM+flxKPxlD9/mNU6onN07Z1Vc/qimexX"
            "K99PqRf2uQiX7NlFEuvXBpJ4QgSL8Daep3GSXgyrLyrqXUfX4VZobXjttCvzOjZiIVc4KTPvnjfGWQ3uIMqFL2kFBGrz8YzG+L4u"
            "/z8YKXHS1b/d8SGHrW+bca+3wjnYrBrjaVoHucHrgfzPCCk2UcVEgCyU/FogPErCf/UGCejfHVK1WnduzXAJr7RM45uqfVrEAZXD"
            "Wz7A+XkJmt3t7ZF3o6WimrcxDr3oSz217g7JzlOALbkTC4KQdylsbDLyM4koTCy4oS2jd997z0s8gUYCPdWOL6DdkOeHJ4c/Mpha"
            "78lzTiGo43/QaHhzcpzKuWwGkuw+Huqcrwf/Ajlivdw="
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsMapper.java"): (
        "3a97358b7662aa21921997e2c595482f66ffaaa49150780865ec0c676801c922",
        "73df526930ed1098c38da53a9c80eda275a61e5726208684800cb7d30e9a54c9",
        (
            "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"
        ),
    ),
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
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogTest.java"): (
        "2222303a3307f0985a014d1ec7c4e84481c214c93474b702c61c475b8ec8db7e",
        "711fe463c91be1c6c634dad2bde56e0ae5dd41cdf868c71ecaf93297a0ea04de",
        (
            "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"
        ),
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsMapperTest.java"): (
        "b0cc54fa04d9767e1107cfa9b1334abd94b3ba476c048cce703f6245daf9dd83",
        "36c35d9fbef83046d2dac3748b29d78f213a2592f55d6e6f183cd5aa78c033ae",
        (
            "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"
        ),
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/SecEdgarConfigurationTest.java"): (
        "ecc13d17794442bc7751fbdcfc45f3b6e029797e0fbb90e28c938ce88fed7dc8",
        "a558138197318707d9175ffecdb93b222212d1070cd5b024380005406f05cb36",
        (
            "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"
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
    len(adr038_historical_overrides) == 7
    and all(path.is_file() for path in adr038_historical_overrides),
    "ADR-037 replay must reverse-project exactly three production and four test/live files",
)

def historical_bytes(path):
    current = pre_adr040_bytes(path)
    override = adr038_historical_overrides.get(path)
    if override is None:
        return current
    current_sha256, historical_sha256, compressed = override
    require(
        hashlib.sha256(current).hexdigest() == current_sha256,
        f"ADR-038 normalized delta changed: {path}",
    )
    historical = zlib.decompress(base64.b64decode(compressed))
    require(
        hashlib.sha256(historical).hexdigest() == historical_sha256,
        f"ADR-037 replay snapshot does not match ea8e571: {path}",
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
    "ADR-037 establishes the in-memory SEC decoded-response "
    "receipt foundation."
)
adr_path = Path(
    "decisions/ADR-037-sec-edgar-decoded-response-receipt-foundation.md"
)
marker_paths = (
    adr_path,
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
documents = {}
for path in marker_paths:
    require(path.is_file(), f"Missing ADR-037 contract document: {path}")
    source = path.read_text(encoding="utf-8")
    documents[path] = source
    require(
        source.count(marker) == 1,
        f"ADR-037 marker must occur exactly once: {path}",
    )
adr = documents[adr_path]
require(
    adr.startswith(
        "# ADR-037 — SEC EDGAR Decoded-Response Receipt Foundation\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    ),
    "ADR-037 title, accepted status, or date changed",
)
compact_adr = re.sub(r"\s+", " ", adr)
required_adr_terms = (
    "fully read HTTP `200` response",
    "SHA-256 over those exact decoded bytes",
    "does not decode and re-encode text",
    "same owned defensive byte copy",
    "Only status, accepted media type, normalized transport content encoding, `ETag`, and `Last-Modified`",
    "Request headers are never copied into the receipt",
    "RECEIPT_ONLY_BODY_NOT_RETAINED",
    "not an SEC digital signature",
    "no durable raw-body retention, replay reader, persistence, Flyway migration",
    "No new API key, provider account, paid plan, OAuth credential, registration, or plugin",
)
require(
    all(term in compact_adr for term in required_adr_terms),
    "ADR-037 byte identity, retention, trust, or non-scope semantics changed",
)

main_root = Path("apps/api/src/main/java/com/wallstreetreceipts/api")
test_root = Path("apps/api/src/test/java/com/wallstreetreceipts/api")
sec_main_root = main_root / "infrastructure/provider/sec"
sec_test_root = test_root / "infrastructure/provider/sec"

delta_main_paths = {
    main_root / "domain/source/SourceResponseReceipt.java",
    sec_main_root / "SecRawResponseCapture.java",
    main_root / "domain/filing/FilingCatalog.java",
    sec_main_root / "SecSubmissionsMapper.java",
    sec_main_root / "SecEdgarFilingCatalogProvider.java",
    sec_main_root / "SecResponseDecompressionInterceptor.java",
}
delta_test_paths = {
    test_root / "domain/source/SourceResponseReceiptTest.java",
    sec_test_root / "SecRawResponseCaptureTest.java",
    test_root / "domain/filing/FilingCatalogTest.java",
    sec_test_root / "SecSubmissionsMapperTest.java",
    sec_test_root / "SecResponseSizeLimitInterceptorTest.java",
    test_root / "config/SecEdgarConfigurationTest.java",
}
require(
    len(delta_main_paths) == 6
    and len(delta_test_paths) == 6
    and all(path.is_file() for path in delta_main_paths | delta_test_paths),
    "ADR-037 must remain the exact six-production/six-test delta",
)

expected_main_paths = {
    main_root / "application/port/out/FilingCatalogProvider.java",
    main_root / "config/SecEdgarConfiguration.java",
    main_root / "config/SecEdgarProperties.java",
    main_root / "domain/filing/FilingCatalog.java",
    main_root / "domain/filing/FilingRecord.java",
    main_root / "domain/source/SourceResponseReceipt.java",
    sec_main_root / "SecEdgarFilingCatalogProvider.java",
    sec_main_root / "SecProviderConfigurationException.java",
    sec_main_root / "SecProviderException.java",
    sec_main_root / "SecRawResponseCapture.java",
    sec_main_root / "SecRequestRateLimitInterceptor.java",
    sec_main_root / "SecRequestRateLimiter.java",
    sec_main_root / "SecResponseDecompressionInterceptor.java",
    sec_main_root / "SecResponseSizeLimitInterceptor.java",
    sec_main_root / "SecRetryAfterPolicy.java",
    sec_main_root / "SecStringCikDeserializer.java",
    sec_main_root / "SecSubmissionsMapper.java",
    sec_main_root / "SecSubmissionsResponse.java",
}
actual_main_paths = {
    *(path for path in (main_root / "domain/filing").glob("*.java")
      if path not in adr038_new_main_paths | adr039_new_main_paths
      | adr040_new_main_paths),
    *(path for path in sec_main_root.glob("*.java")
      if path not in adr039_new_main_paths | adr040_new_main_paths),
    main_root / "domain/source/SourceResponseReceipt.java",
    *(path for path in (
        main_root / "application/port/out/FilingCatalogProvider.java",
        main_root / "config/SecEdgarConfiguration.java",
        main_root / "config/SecEdgarProperties.java",
    ) if path.is_file()),
}
require(
    actual_main_paths == expected_main_paths
    and len(actual_main_paths) == 18,
    "ADR-037 exact 18-file SEC production surface changed",
)

expected_test_paths = {
    test_root / "config/SecEdgarConfigurationTest.java",
    test_root / "domain/filing/FilingCatalogTest.java",
    test_root / "domain/source/SourceResponseReceiptTest.java",
    sec_test_root / "SecRawResponseCaptureTest.java",
    sec_test_root / "SecRequestRateLimitInterceptorTest.java",
    sec_test_root / "SecRequestRateLimiterTest.java",
    sec_test_root / "SecResponseSizeLimitInterceptorTest.java",
    sec_test_root / "SecRetryAfterPolicyTest.java",
    sec_test_root / "SecSubmissionsMapperTest.java",
}
actual_test_paths = {
    *(path for path in (test_root / "domain/filing").glob("*.java")
      if path not in adr038_new_test_paths | adr039_new_test_paths
      | adr040_new_test_paths),
    *(path for path in sec_test_root.glob("*.java")
      if path not in adr039_new_test_paths | adr040_new_test_paths),
    test_root / "domain/source/SourceResponseReceiptTest.java",
    test_root / "config/SecEdgarConfigurationTest.java",
}
require(
    actual_test_paths == expected_test_paths
    and len(actual_test_paths) == 9,
    "ADR-037 exact nine-file SEC focused test surface changed",
)

sources = {
    path.name: historical_bytes(path).decode("utf-8")
    for path in expected_main_paths
}
tests = {
    path.name: historical_bytes(path).decode("utf-8")
    for path in expected_test_paths
}
receipt = sources["SourceResponseReceipt.java"]
capture = sources["SecRawResponseCapture.java"]
catalog = sources["FilingCatalog.java"]
mapper = sources["SecSubmissionsMapper.java"]
provider = sources["SecEdgarFilingCatalogProvider.java"]
configuration = sources["SecEdgarConfiguration.java"]
decompression = sources["SecResponseDecompressionInterceptor.java"]
receipt_test = tests["SourceResponseReceiptTest.java"]
capture_test = tests["SecRawResponseCaptureTest.java"]
catalog_test = tests["FilingCatalogTest.java"]
mapper_test = tests["SecSubmissionsMapperTest.java"]
configuration_test = tests["SecEdgarConfigurationTest.java"]
size_limit_test = tests["SecResponseSizeLimitInterceptorTest.java"]
delta_source = "\n".join(
    historical_bytes(path).decode("utf-8") for path in delta_main_paths
)
focused_test_source = "\n".join(tests.values())

record_match = re.search(
    r"public record SourceResponseReceipt\((?P<fields>.*?)\) \{",
    receipt,
    flags=re.DOTALL,
)
require(record_match is not None, "Missing SourceResponseReceipt record")
record_fields = re.sub(r"\s+", " ", record_match.group("fields")).strip()
require(
    record_fields == (
        "String provider, String product, URI sourceUri, int httpStatus, "
        "String mediaType, TransportContentEncoding transportContentEncoding, "
        "String etag, Instant lastModified, String parserVersion, "
        "String decodedBodySha256, long decodedBodyLength, Instant capturedAt, "
        "BodyRepresentation bodyRepresentation, BodyRetention bodyRetention"
    ),
    "ADR-037 receipt field order or response-metadata allowlist changed",
)

def enum_values(source, name):
    match = re.search(
        rf"public enum {name}\s*\{{(?P<body>.*?)\}}",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing receipt enum {name}")
    return re.findall(r"\b[A-Z][A-Z0-9_]+\b", match.group("body"))

require(
    enum_values(receipt, "TransportContentEncoding")
        == ["IDENTITY", "GZIP", "DEFLATE"]
    and enum_values(receipt, "BodyRepresentation")
        == ["DECODED_HTTP_ENTITY_BODY"]
    and enum_values(receipt, "BodyRetention")
        == [
            "RECEIPT_ONLY_BODY_NOT_RETAINED",
            "DECODED_BODY_ATTACHED_PENDING_PERSISTENCE",
            "DURABLE_DECODED_BODY_RETAINED",
        ],
    "ADR-037 receipt-only state plus ADR-039 retention extension changed",
)
receipt_markers = (
    'Pattern.compile("[0-9a-f]{64}")',
    "if (httpStatus != 200)",
    "if (decodedBodyLength <= 0)",
    'requireMicrosecondPrecision(capturedAt, "capturedAt")',
    "value.getUserInfo() != null",
    "value.getQuery() != null",
    "value.getFragment() != null",
    'etag=<redacted>',
)
require(
    all(marker in receipt for marker in receipt_markers)
    and "byte[]" not in receipt
    and "ByteBuffer" not in receipt
    and "InputStream" not in receipt
    and all(method in receipt_test for method in (
        "rejectsNonCanonicalDigestStatusAndLength",
        "rejectsUnsafeSourceUriAndSubMicrosecondCaptureTime",
        "rejectsUnsafeOpaqueValidator",
    )),
    "Receipt canonical digest/status/time invariants or receipt-only body boundary changed",
)

header_constants = set(re.findall(r"HttpHeaders\.([A-Z_]+)", capture))
require(
    header_constants == {
        "CONTENT_TYPE", "CONTENT_ENCODING", "ETAG", "LAST_MODIFIED",
    }
    and all(marker not in receipt + capture for marker in (
        "HttpHeaders.USER_AGENT", "HttpHeaders.AUTHORIZATION",
        "HttpHeaders.COOKIE", "SEC_CONTACT_EMAIL", "contactEmail",
        "Authorization", "User-Agent",
    ))
    and 'etag=<redacted>' in receipt
    and 'headers.set("X-Secret", "do-not-retain")' in capture_test
    and '.doesNotContain("revision-1", "X-Secret", "do-not-retain")'
        in capture_test,
    "Response metadata must remain exact allowlist-only with no request identity",
)

capture_markers = (
    "final class SecRawResponseCapture",
    "private static final ObjectReader SUBMISSIONS_READER = strictSubmissionsReader()",
    "private static ObjectReader strictSubmissionsReader()",
    "JsonMapper mapper = JsonMapper.builder()",
    ".enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)",
    ".disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)",
    ".disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)",
    "mapper.coercionConfigFor(LogicalType.Textual)",
    ".setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)",
    ".setCoercion(CoercionInputShape.Float, CoercionAction.Fail)",
    ".setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)",
    "private final byte[] decodedBody;",
    "private final SourceResponseReceipt receipt;",
    "byte[] ownedBody = decodedBody.clone();",
    "requireValidUtf8(ownedBody);",
    "LOWERCASE_HEX.formatHex(sha256(ownedBody))",
    'MessageDigest.getInstance("SHA-256").digest(content)',
    "new SecRawResponseCapture(ownedBody, receipt)",
    ".with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)",
    "SUBMISSIONS_READER.readValue(decodedBody)",
    "SecSubmissionsMapper.toCanonical(",
    "decode(), receipt, processingTime",
)
require(
    all(marker in capture for marker in capture_markers)
    and capture.index("if (httpStatus != 200)")
        < capture.index("byte[] ownedBody = decodedBody.clone();")
    and capture.index("MediaType contentType = requireJsonContentType(headers);")
        < capture.index("byte[] ownedBody = decodedBody.clone();")
    and capture.index("LOWERCASE_HEX.formatHex(sha256(ownedBody))")
        < capture.index("new SecRawResponseCapture(ownedBody, receipt)")
    and "public class SecRawResponseCapture" not in capture
    and "public final class SecRawResponseCapture" not in capture
    and "decodedBody()" not in capture
    and "new String(" not in capture
    and ".getBytes(" not in capture
    and ".writeValue" not in capture
    and "readTree(" not in capture
    and "convertValue(" not in capture
    and "ObjectMapper" not in capture,
    "Exact decoded bytes must be defensively owned, hashed once, and parsed unchanged",
)

capture_test_methods = (
    "hashesOwnedDecodedBytesAndCannotBeChangedThroughTheInputArray",
    "preservesOnlyWhitelistedTransportValidators",
    "rejectsEmptyBodyMissingOrNonJsonMediaTypeWithSanitizedFailure",
    "rejectsAmbiguousOrMalformedReceiptHeaders",
    "rejectsNonUtf8BytesBeforeCreatingTheReceipt",
    "rejectsNonUtf8DeclarationAndTrailingJsonWithoutChangingTheReceiptDigest",
    "hashesWhitespaceAndUtf8BomAsExactBytesWithoutNormalization",
    "acceptsTheExactDecodedLimitAndRejectsOneByteMoreBeforeHashing",
    "rejectsNon200BeforeCreatingAReceipt",
)
require(
    all(method in capture_test for method in capture_test_methods)
    and "compactCapture.receipt().decodedBodySha256()" in capture_test
    and "spacedCapture.receipt().decodedBodySha256()" in capture_test
    and "bomCapture.receipt().decodedBodySha256()" in capture_test
    and "MAX_DECOMPRESSED_RESPONSE_BYTES" in capture
    and "MAX_DECOMPRESSED_RESPONSE_BYTES" in capture_test
    and "STRICT_DUPLICATE_DETECTION" in capture
    and "ALLOW_COERCION_OF_SCALARS" in capture
    and "ACCEPT_FLOAT_AS_INT" in capture
    and "CodingErrorAction.REPORT" in capture
    and "if (value == 0)" in capture
    and "JSON_CONTENT_TYPE" in capture
    and "ENTITY_TAG" in capture,
    "Exact-byte, JSON/status, defensive-copy, or decoded-limit coverage changed",
)

require(
    "HttpHeaders decodedHeaders = new HttpHeaders();" in decompression
    and "decodedHeaders.addAll(delegate.getHeaders());" in decompression
    and "decodedHeaders.getFirst(HttpHeaders.CONTENT_ENCODING)"
        in decompression
    and "if (isSupportedCompression(contentEncoding))" in decompression
    and "case \"gzip\", \"x-gzip\", \"deflate\" -> true"
        in decompression
    and decompression.count(
        "decodedHeaders.remove(HttpHeaders.CONTENT_LENGTH);"
    ) == 1
    and "decodedHeaders.remove(HttpHeaders.CONTENT_ENCODING)"
        not in decompression
    and "HttpHeaders.readOnlyHttpHeaders(decodedHeaders)"
        in decompression
    and decompression.count(
        "delegate.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING)"
    ) == 1
    and size_limit_test.count(
        "assertThat(response.getHeaders().getContentLength()).isEqualTo(-1);"
    ) == 2
    and size_limit_test.count(
        "response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING)"
    ) == 2
    and '.isEqualTo("gzip")' in size_limit_test
    and '.isEqualTo("deflate")' in size_limit_test,
    "Decoded gzip/deflate headers must retain encoding and remove stale encoded length",
)

provider_markers = (
    "ResponseEntity<byte[]> response = restClient.get()",
    ".toEntity(byte[].class)",
    "status -> status.value() != 200",
    "byte[] decodedBody = response.getBody();",
    "clock.instant().truncatedTo(ChronoUnit.MICROS)",
    "SecRawResponseCapture.capture(",
    "capture.receipt()",
    "return capture.toCanonical(receivedAt);",
)
require(
    all(marker in provider for marker in provider_markers)
    and provider.count("restClient.get()") == 1
    and provider.count(".retrieve()") == 1
    and provider.count(".toEntity(byte[].class)") == 1
    and provider.index("byte[] decodedBody = response.getBody();")
        < provider.index("SecRawResponseCapture.capture(")
    and provider.index("SecRawResponseCapture capture = retrieve(endpoint);")
        < provider.index("capture.toCanonical(receivedAt)")
    and "objectMapper.read" not in provider
    and "ObjectMapper" not in provider
    and "String responseBody" not in provider,
    "Provider must fully read one decoded byte entity before one receipt/hash/parse path",
)

require(
    "SourceResponseReceipt sourceReceipt" in catalog
    and "provider.equals(sourceReceipt.provider())" in catalog
    and "product.equals(sourceReceipt.product())" in catalog
    and "sourceUri.equals(sourceReceipt.sourceUri())" in catalog
    and "capturedAt.equals(sourceReceipt.capturedAt())" in catalog
    and "SourceResponseReceipt sourceReceipt" in mapper
    and "sourceReceipt.sourceUri()" in mapper
    and "sourceReceipt.capturedAt()" in mapper
    and 'PROVIDER_NAME = "sec-edgar"' in mapper
    and 'PRODUCT_NAME = "edgar-submissions-api"' in mapper
    and 'PARSER_VERSION = "SEC_SUBMISSIONS_RECENT_V1"' in mapper
    and "sourceReceipt.parserVersion()" in mapper
    and "sourceReceipt," in mapper
    and "rejectsAReceiptForADifferentCatalogCapture" in catalog_test
    and "rejectsAReceiptFromAnotherParserIdentity" in mapper_test,
    "Receipt must remain attached to the same provider/product/source/capture/parser identity",
)

require(
    "ObjectMapper" not in configuration
    and "ObjectMapper" not in provider
    and '@ConditionalOnProperty(' in configuration
    and 'prefix = "app.public-data.sec"' in configuration
    and 'havingValue = "true"' in configuration
    and "matchIfMissing" not in configuration
    and "remainsDisabledByDefault" in configuration_test
    and "requestsPaddedCikWithDeclaredUserAgentAndMapsSuccessfulResponse"
        in configuration_test
    and "decodesAdvertisedGzipResponses" in configuration_test
    and "decodesAdvertisedDeflateResponsesAndHashesTheSameDecodedBytes"
        in configuration_test
    and "rejectsDuplicateKeysAndNumericCoercionWithSanitizedFailure"
        in configuration_test
    and "rejectsBomlessUtf16PayloadEvenWhenTheMediaTypeOmitsACharset"
        in configuration_test
    and "turnsEveryNonExactSuccessStatusIntoASanitizedExceptionWithoutRetry"
        in configuration_test,
    "Default-disabled bean, exact-200 JSON, or decoded transport integration changed",
)

forbidden_layer_markers = (
    "@Entity", "@Table", "@Repository", "@RestController",
    "@Controller", "@RequestMapping", "@GetMapping", "@PostMapping",
    "@Scheduled", "SchedulingConfigurer", "CommandLineRunner",
    "ApplicationRunner", "jakarta.persistence", "org.springframework.data",
    "JdbcTemplate", "EntityManager", "DataSource", "Flyway", "OpenAPI",
    "Kafka", ".save(", ".insert(", "Files.write", "System.out",
    "System.err", "Logger", "Slf4j",
)
require(
    all(marker not in delta_source for marker in forbidden_layer_markers)
    and "SecRawResponseCapture" not in configuration,
    "ADR-037 must not add persistence, orchestration, controller, logging, or a capture bean",
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
    "ADR-037 replay must recognize only the exact ADR-039 through ADR-042 Flyway deltas",
)

publication_markers = (
    "sourceresponsereceipt",
    "source response receipt",
    "secrawresponsecapture",
    "decodedbodysha256",
    "receipt_only_body_not_retained",
)
publication_paths = {
    Path("contracts/openapi.yaml"),
    *Path("schemas").glob("*.json"),
    *(path for path in Path("fixtures/v1").rglob("*") if path.is_file()),
    *(path for path in main_root.rglob("*.java")
      if path not in delta_main_paths | adr038_new_main_paths
      | adr039_new_main_paths | adr040_new_main_paths),
    *(path for path in test_root.rglob("*.java")
      if path not in delta_test_paths | adr038_new_test_paths
      | adr039_new_test_paths | adr040_new_test_paths
      | {test_root / "migration/PostgreSqlMigrationTest.java"}),
    *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
      for path in root.rglob("*") if path.is_file()),
}
for path in publication_paths:
    source = path.read_text(encoding="utf-8", errors="ignore").lower()
    require(
        not any(marker in source for marker in publication_markers),
        f"ADR-037 receipt must not reach unrelated/API/web/publication code: {path}",
    )

require(
    "MockRestServiceServer.bindTo(restClientBuilder)" in configuration_test
    and all(marker not in focused_test_source for marker in (
        "HttpClient.newHttpClient(", "RestClient.create(",
        "WebClient.create(", ".openConnection(", "new Socket(",
    )),
    "ADR-037 tests must remain deterministic and offline",
)

config_paths = {
    Path(".env.example"),
    Path("apps/api/src/main/resources/application.yml"),
    Path("apps/api/src/main/resources/application-local.yml"),
    Path("apps/api/src/test/resources/application-test.yml"),
}
pom_path = Path("apps/api/pom.xml")
live_path = Path(
    "apps/api/src/sec-live-smoke-test/java/com/wallstreetreceipts/"
    "api/config/SecEdgarLiveSmokeIT.java"
)
dependency_runtime_paths = {
    pom_path,
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
    len(config_paths) == 4
    and digest(config_paths)
        == "c667d625d56663217470dd9652ae0c6da9225c48427919009717e1148bc5c328"
    and digest({pom_path})
        == "faee0805dae9dcf5e090a73d03fa16ead4a79c64fac6f2eb87bd87a1e91d3682"
    and digest({live_path})
        == "2e62a932995ce6d4d31bac7698f1d8a7daf0a067eff3d2c79b81fe00cb03460e"
    and len(dependency_runtime_paths) == 9
    and all(path.is_file() for path in dependency_runtime_paths)
    and digest(dependency_runtime_paths)
        == "8cccb8b3593eddc92713eab6972edaf91b6adf0db63009de20aa799220adf691",
    "ADR-037 must not change POM, config/env, dependency runtime, or live smoke",
)
live_source = historical_bytes(live_path).decode("utf-8")
require(
    all(marker not in live_source for marker in (
        "SourceResponseReceipt", "SecRawResponseCapture",
        "decodedBodySha256", "sourceReceipt()", "SEC_CONTACT_EMAIL",
    )),
    "ADR-037 receipt/body/contact must not enter the isolated live smoke",
)

namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
pom_root = ET.parse(pom_path).getroot()
profiles = pom_root.findall("m:profiles/m:profile", namespace)
require(
    len(profiles) == 1
    and profiles[0].findtext("m:id", namespaces=namespace)
        == "sec-live-smoke"
    and profiles[0].find("m:activation", namespace) is None,
    "ADR-037 must preserve the isolated non-activated live profile",
)

workflow = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
def without_step(source, name):
    step = f"\n      - name: {name}\n"
    start = source.index(step)
    end = source.index("\n      - name: ", start + len(step))
    return source[:start] + source[end:]

workflow_without_sec_guards = without_step(
    workflow,
    "Guard SEC EDGAR decoded-response receipt foundation",
)
workflow_without_sec_guards = without_step(
    workflow_without_sec_guards,
    "Guard SEC EDGAR single-process live-operation safety gate",
)
workflow_without_sec_guards = without_step(
    workflow_without_sec_guards,
    "Guard SEC EDGAR historical-segment descriptor catalog",
)
workflow_without_sec_guards = without_step(
    workflow_without_sec_guards,
    "Guard SEC EDGAR append-only capture persistence",
)
workflow_without_sec_guards = without_step(
    workflow_without_sec_guards,
    "Guard SEC EDGAR historical-segment append-only persistence",
)
workflow_without_sec_guards = without_step(
    workflow_without_sec_guards,
    "Guard SEC EDGAR ordered filing-history collection manifest",
)
workflow_without_sec_guards = without_step(
    workflow_without_sec_guards,
    "Guard SEC EDGAR operator-controlled bounded collection attempt",
)
workflow_without_sec_guards = without_step(
    workflow_without_sec_guards,
    "Guard default-disabled local single-operator SEC attempt API",
)
require(
    workflow_without_sec_guards.count(
        "run: ./mvnw -B -ntp verify"
    ) == 1
    and all(marker not in workflow_without_sec_guards for marker in (
        "SEC_LIVE_SMOKE", "-Psec-live-smoke",
        "src/sec-live-smoke-test", "SecEdgarLiveSmokeIT",
        "sec.live-smoke.profile",
    )),
    "Default Maven/CI must not activate the isolated SEC live profile",
)

print(
    "Validated ADR-037 exact 18+9 SEC surfaces and exact 6+6 delta, "
    "decoded-byte SHA-256/parser identity, exact-200 JSON receipt, "
    "metadata allowlist, receipt-only retention, historical replay, "
    "default-disabled profile isolation, and no persistence/publication"
)
PYTHON
