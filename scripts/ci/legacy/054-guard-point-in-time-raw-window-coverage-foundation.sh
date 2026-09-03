python <<'PYTHON'
import base64
import hashlib
import json
import re
import zlib
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

adr036_new_main_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRequestRateLimitInterceptor.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRequestRateLimiter.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecResponseSizeLimitInterceptor.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRetryAfterPolicy.java"),
}
adr036_new_test_paths = {
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRequestRateLimitInterceptorTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRequestRateLimiterTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecResponseSizeLimitInterceptorTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRetryAfterPolicyTest.java"),
}
require(
    len(adr036_new_main_paths) == 4
    and len(adr036_new_test_paths) == 4
    and all(path.is_file()
            for path in adr036_new_main_paths | adr036_new_test_paths),
    "ADR-034 historical replay must normalize the exact ADR-036 4+4 delta",
)
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
    "ADR-034 historical replay must exclude only the exact ADR-037 2+2 new files",
)
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
    "ADR-034 historical replay must exclude only the exact ADR-038 1+1 new files",
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
adr043_later_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/ExactEvidenceNotAdmittedException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/OperatorRequestConflictException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptNotFoundException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptQueryService.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptQueryServiceTest.java"),
    *Path("apps/api/src/main/java/com/wallstreetreceipts/api/config").glob("Operator*.java"),
    *Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator").glob("*.java"),
    *Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security").glob("*.java"),
    *Path("apps/api/src/test/java/com/wallstreetreceipts/api/config").glob("Operator*.java"),
    *Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/operator").glob("*.java"),
    *Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/security").glob("*.java"),
}
require(
    len(adr043_later_paths) == 25
    and all(path.is_file() for path in adr043_later_paths),
    "Historical replay must exclude the exact ADR-043 17+8 surface",
)
adr041_later_paths |= adr043_later_paths

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
                == "1c96ed547d518d97be37a3dd914de1d2d305dd89074df86dfc1c910cd9f92534",
            "PostgreSQL decoded-body race current delta changed",
        )
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
            "PostgreSQL decoded-body race setup delta changed",
        )
        source = source.replace(root_capture, historical_root_capture)
        source = source.replace(bodies_before, b"")
        assertion_start = (
            b"        assertThat(jdbc.getJdbcOperations().queryForObject(\n"
        )
        assertion_end = b"                .isEqualTo(1);\n"
        for accessor in (b"catalog", b"segment"):
            marker = (
                b"                concurrentCapture." + accessor
                + b"().sourceReceipt().decodedBodySha256(),\n"
            )
            marker_index = source.find(marker)
            second_start = source.rfind(assertion_start, 0, marker_index)
            first_start = source.rfind(assertion_start, 0, second_start)
            end = source.find(assertion_end, marker_index)
            require(
                marker_index >= 0 and first_start >= 0
                and second_start > first_start and end >= marker_index,
                f"PostgreSQL decoded-body {accessor.decode()} assertion delta changed",
            )
            end += len(assertion_end)
            removed = source[first_start:end]
            require(
                removed.count(assertion_start) == 2
                and b"bodiesBeforeConcurrentReplay + 1" in removed
                and b"decoded_body_sha256 = ?" in removed
                and b"decoded_body_length = ?" in removed,
                f"PostgreSQL decoded-body {accessor.decode()} assertion shape changed",
            )
            source = source[:first_start] + source[end:]
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

adr043_replacements = {
    Path(".env.example"): (
        b"\n# ADR-043 local-only single-operator HTTP boundary. This does not enable SEC\n"
        b"# network access. Keep disabled outside an isolated local API process; when\n"
        b"# enabled, provide only the lowercase SHA-256 digest of the canonical standard\n"
        b"# Base64 encoding of exactly 32 random bytes.\n"
        b"# Never put the raw Bearer token in this file.\n"
        b"OPERATOR_API_ENABLED=false\n"
        b"OPERATOR_API_TOKEN_SHA256=\n",
        b"",
    ),
    Path("apps/api/pom.xml"): (
        b"        <dependency>\n"
        b"            <groupId>org.springframework.boot</groupId>\n"
        b"            <artifactId>spring-boot-starter-security</artifactId>\n"
        b"        </dependency>\n",
        b"",
    ),
    Path("apps/api/src/main/resources/application.yml"): (
        b"app:\n"
        b"  operator-api:\n"
        b"    enabled: ${OPERATOR_API_ENABLED:false}\n"
        b"    token-sha256: ${OPERATOR_API_TOKEN_SHA256:}\n",
        b"app:\n",
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/RequestIdFilter.java"): (
        (
            b"import org.springframework.core.Ordered;\n"
            b"import org.springframework.core.annotation.Order;\n",
            b"",
        ),
        (
            b"@Component\n@Order(Ordered.HIGHEST_PRECEDENCE)\n",
            b"@Component\n",
        ),
    ),
}
adr043_historical_sha256 = {
    Path(".env.example"): "d6687a67a4ad25d7ee2cd8ee5194d15e5b01b880e51d0708c3ce0efb3e4938f7",
    Path("apps/api/pom.xml"): "6db3f998c82f0c399b66075216b0cab22693ff91e8a7c495becf7e3fea7e28a3",
    Path("apps/api/src/main/resources/application.yml"): "fba88168c8ca0ce0c17eeea201fb624b556240ab52297f1bf33ca9388f568a42",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/RequestIdFilter.java"): "1cd35e0c8d728db84486ad626b6de6b7df6afa2777f2c35371946c58309212ec",
}
adr043_original_read_bytes = Path.read_bytes

def pre_adr043_bytes(path):
    source = adr043_original_read_bytes(path).replace(b"\r\n", b"\n")
    replacements = adr043_replacements.get(path)
    if replacements is None:
        return source
    if path.name == "RequestIdFilter.java":
        for current, historical in replacements:
            require(
                source.count(current) == 1,
                f"ADR-043 authorized later delta changed: {path}",
            )
            source = source.replace(current, historical)
    else:
        current, historical = replacements
        require(
            source.count(current) == 1,
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

adr040_new_main_paths |= adr041_later_paths
adr040_new_test_paths |= adr041_later_paths


adr040_postgres_test_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/"
    "PostgreSqlMigrationTest.java"
)
require(
    adr040_postgres_test_path.is_file(),
    "ADR-040 replay must preserve the prior PostgreSQL migration test",
)

def pre_adr040_bytes(path):
    source = path.read_bytes().replace(b"\r\n", b"\n")
    if path != adr040_postgres_test_path:
        return source
    require(
        hashlib.sha256(source).hexdigest()
            == "013be72bd6c110c22ec87467a000dc5e3bfe574a67e6fec11e48404305891145",
        "ADR-041 PostgreSQL migration-test delta changed",
    )
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
    )
    require(
        historical.count(
            "assertThat(flyway.info().applied()).hasSize(7);"
        ) == 1
        and historical.count(
            'assertThat(latest.info().current().getVersion().getVersion())'
            '.isEqualTo("7");'
        ) == 2
        and historical.count(
            '.locations("classpath:db/migration")\n'
            '                .target("6")\n'
            '                .load();'
        ) == 1,
        "ADR-040 Flyway-version migration-test delta changed",
    )
    historical = historical.replace(
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
        hashlib.sha256(historical_bytes).hexdigest()
            == "e6ee0f96789697414853562e47e698da088debec246f1cfe178661380163458b",
        "ADR-040 PostgreSQL reverse projection changed",
    )
    return historical_bytes


historical_overrides = {
    Path("apps/api/pom.xml"): (
        "6db3f998c82f0c399b66075216b0cab22693ff91e8a7c495becf7e3fea7e28a3",
        "d4037b95885ca135a794c9cfe2287b8a8ac4a7cd4e1297705b2a39afad1c3206",
        (
            "eNrVV91v2zYQf/dfIRh9GVCRsdIVRaCo6MuwDs0aNF03YE80Rct0JVIhKX+g6P/eo2jJki1LclB0XZAA0X3+7nh3PIavt1nq"
            "rZnSXIrb6QxdTT0mqIy5SG6nf338zX81fR1NwlzJFaPGA2mhb6dLY/IbjDOyZgKRnNAlQ1Il+P79HX6BrsDKxKt+SpWbrea1"
            "2mazQZvrUiG4uprhf+7ePYCFjPhcaEMEZU11zW90yX0nKTElzEH3npXQXSJbHTuiXwoi+J5GpbcwkzFLP7lURCU3xC3axMnl"
            "RDFhohpimChZ5G/jCOwjnStI3UKRjG2k+ozmUpoQVxIHHaIMXxBqgOhUfCvpQ/jKMOU7HyFuiB1098cVXaNf0exliKvvg4Ri"
            "KaRqze6JWeJ9eLjCPWmBpjJDG5Km2ijG4I8ynht9BLkJ1wr7TtqvxH2S81OwNVCb6Jn/8Oeb+4ff3388AhwKyFWP1ZLtJGOm"
            "qQKO1f0bFLyHUsH7sFfwHgumdt6b+7ceEbFHiZCCU5J6scwIF95cFiImahfipqXqWJXMGUTAdCORK7ImqIIbzELcIhzkDNOG"
            "SmHAC/BqgRkKZug6xGfY1cE0PFeB5kzE0Ii7OyJIwrJ2wdXsFtgjzTbjpFDbkDpKtKtU20pQsll3iZ6U6rMv3Sn42lG9h6Tu"
            "chbl1kf536mAppC4iGe5VNAr7qudDtyVjwa1TmBTspnzSU/Czyb7KRNhzFTYsPmZkdAd54/GB5yCGKl+apCreE6/P8BFutuQ"
            "XTwfAcqJ+lQq9jPgiIkhc6KZn0ttEsX0Y/r9YTVtDwIbAtLofVUIwzN20vxtrBeAtdfhMqhyMgLsMhgEaUffAML/rBscth8d"
            "wKjLpwl9VQhu/FWRcwD9P8B7QQmPBHt8aTnivOBp3Fr8tCwUPVkLKnrHJRpz2LdgaO8irSi2exKurYDTmnt0sXZbHOfo2Zf9"
            "ewLZJgP6V4yQ/V3wrSnABF7Pzrp2qwFRCTN2vY1aOg36qRIXNC3i49wcs6OMaNsZdgaglZYixBWnXw+2hnSnjU/tNv1kTdh+"
            "19wuRJeZyIj6DKuzFiTXS2kuUy79ygJKPGNP0LSdwbbDPmuWHi6mA625cOVpkcAb8UjdUQf23QtmZt/cdG9H57GntXEXpnFA"
            "949V93LdBzwSqAPHxEJC3tQgyqPH5EsU9G3jbMtoYR9M5/qnFujmu7qJoz08376koCLiHulEklSf59cylU1Ik/06bxAPWAyh"
            "lBc8KRTpj8ONuiJlA+j2I/GxgFH2B8T76Vx2OxWrs/g3mD0Pgl96Duc00EudgkZ/OCEekZsQ9xRBg6mH2qWi1K+z/UVXPpbt"
            "zRFNvgG9OV/C"
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/SecEdgarConfiguration.java"): (
        "f6c3782ce133a471ee94048e036c54c4405b67d8faf1a96aa77e7ce6dd7dbd55",
        "7c20a233a7c0b9fa90caf4873da6006cb235fa14a330d3e305f972868df26810",
        (
            "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"
            "PKw+QXluPDQeGv8DiDqoeQ=="
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecEdgarFilingCatalogProvider.java"): (
        "22eaf1fe072f5b00ee4f00a6e0669fb7317c35529c3af9311a484fb5538be2cb",
        "d03297372b7794c4349f8607849580da3c3c4dcaa7938eec184f85bbd856f652",
        (
            "eNqdVm1v2zYQ/p5fwQoYSqEOm+yr0aGe52HGZjuwkn0qENDS2WZCkRpJ2WlT//cdZcmSbNnpRkAQRfLennvuxIzHz3wFJNYp"
            "23IprTMA+MQgMmcZzwQTamk4ruexyw2wzOiNSMAwC3H/6kqkmTaOPPENZwoce5iP+61FJ1JgQ6nj5471sbKOK9ex48CvcMmG"
            "a6OVflDi6FTuhGSzxRPEztZ+aLNiNjNCrdDpFLbaPLMtLFgsBSjH5mDdsJj2/7PE6CWGzAmtamsXUONZJkXM/XnmzzKdO/a7"
            "kGhnyB2XenVXAtn/AW2JTrlQbFnIt9WgN1m+QFtkKRSXJJbcWhJBPEpW3HRaJGhQQooxWdJ94PXqiuBAWDbcAcEkuYOByHmw"
            "yN189vf4t9H8cTqYjMgnEiAfrsHbDPpvCkcPv07GUTSeTaPHu8H9H4/3o8ndX4P7QtFHmy9SYS1CZz8Ox3/+ZNmT1QrVtvTu"
            "Fdb5IaaR3NODSEyy4BYejOzaLhiK4BU83e/vUb2IJO0032sa6zVVh4gsKYdbC8tqIYy8ZDMu/pMLA1OtprmUtKk4qD+CsN/W"
            "VRo8r+jgUVDOTlQUTp5XUGyjePGuhHd7uD7PNmAMgtIErwUakZonc6S1cvt1S0s6xKKFTLma8SSBZCi8Q0qblEvxDfCT+uO1"
            "4x5rUEmmRQFiGZpHVssN0LNUY0uv0zlI6MFS2NCLiY9qImKiM3yBT/N+8gmn6Cigjcp8Q7rsbKQo4w0kA+9bgRs21GKLhgyb"
            "qsIeAcm9pnWfY5PxcD6LwpKHRXLM1wY+fqDt3KgjJyfYdbAzOz3kSitsPwV5Cnd7B4x6DZ+a84bzO4JexWtC57ny3fjQ+ghU"
            "s/DIH4fub707VWkcZDDeDaYuqRCkTUNvMqjiQql1ii2aNm2XOLS6UYuXVZmfTWeZwyaNWmV6Av2PEKMqUrYCTHRLvBosN6Jm"
            "TveRg3dn9rWKsLvmlnZuV8MWZ8j1L+TdfsqE/fnlJcrjGKxd5pKGvYsKqO8DGFPvkIcq6tBrfb0o/AY71s5lZRDHyj14+62h"
            "ThADhjzK8d0gUNfYnQFroZOvtDt5rPhrNivOD7EktM4qNiHsgWFHtBeiy5UBnvCFhC761yVwROfKakdJdpl5qyyhvrqclvjp"
            "Bed/VfnlSHddJVneDMoSb7X47h+DT0js/wf7XJwk4vt38g73WepDA0uDL1+S19ve7c0uCLsO+7PxmhuL3MKL18SLUb/AY4e3"
            "IKR2/YE239+8D7sRUbAlYylhxeXArPK0iSYN8A5D0tz6G55yeI8jC3BbAEVuCVcJub0hiVgJZ4MTwBqUCG4CbAcZcEdR4Lrw"
            "XYJauTUWBPngv6u+t7v6FzZCuKM="
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecProviderException.java"): (
        "9b8a7687f31d560c5eca072cf1c01e184769a6a70ab5e19baa9b2a80d65a27ee",
        "0c32bfe660c07cab9dbd8ba5828c3d5949d26580732116f6fc7446eab7439626",
        (
            "eNqtkcFqwzAMhu95CtFTxiAv0GMp7DIoTV9AsZVWzHVcS04Ko+8+re1oBz0UNoEP1i/9/2ec0H3glsAN+2bCEEQzkR1HnFQa"
            "TNxw7DNavzgtmZqUh5E95UbIzasqlS6wg54jBnABRaAlt7oOLY+OkvIQgY5K0QusS1Te0034rCqwSplHVHq4XLeaOW5BsKd3"
            "EjHgF9uDa0lJlOt7cX7WThdnUVQjfEi1U02t6UVqjnoeLbIY/C//TPbwCJGmx3SzdrkwiG7PInYXWzgUEoUeOZCHiXUHb5vN"
            "Cmbwep/xNGaJmdBjF2hNkiyC6j8CXlzs20vwEAeFjuA7Y/Y8FMcRA/t/JppQfpxvLKfqCyOx6ks="
        ),
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/SecEdgarConfigurationTest.java"): (
        "162220c172ded6c8c94a0a8cadea4597b9f9d9c3b70707465d230d1f699b313d",
        "6962541db5d5c5cfbe667fb0eb4b7fb54424a96e186afb2b6ab7cbac05a241a5",
        (
            "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"
            "n3Wcc9eQVpV4v3G/8V9826DI"
        ),
    ),
}

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
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecEdgarFilingCatalogProvider.java"):
        historical_overrides[Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecEdgarFilingCatalogProvider.java")][1:],
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/SecEdgarConfigurationTest.java"):
        historical_overrides[Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/SecEdgarConfigurationTest.java")][1:],
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
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsMapper.java"): (
        "5833a7eabc453d998d51732ef29e15bc0ede60a8df11611b827daee6afec3722",
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
    len(adr037_historical_overrides) == 7
    and all(path.is_file() for path in adr037_historical_overrides),
    "Pre-ADR-037 replay must reverse-project exactly four production and three test files",
)

adr038_historical_overrides = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsResponse.java"): (
        "ba88ea9c51018cfa42df754b45906c3626f2efef12acd93d9ef14e6cc8a87695",
        "3ef8e9f2664745e0b80c08c3fb801dcd213d9867c4f1e849704c73aa2f589cb7",
        (
            "eNqtVNtu2zAMffdX8DEJCvUDsg7FmhbIEGxD0g17lWUmZWNdQMrtLui/j0rSZu1SNw8zYFgiD3kOKcrJurVdIbjozb1tW8mMqK9DSlmMTWQoLNmqvXO5YzSJ4x01yEbQjauKfIqc4dbeWdNlas2MJO/tJe9So5F/+NbcKpvEYGwIMdtMuvyo++kqRMYvHBNyJpRxf3Rjs60pNC/TTFCQybb0C1XA6WgE3zA0kWFy/Rm8ze6GwgryDcLi8gKkqz2JaLAAoyT94hgoQxNRQBOD4yiywdvGJhUBdexCY/mngdFpdX5I+oA2hq9hHeJ9gDPQvuGwSl3dklMeF7mBBbrFnn2+Ix9UsHvOX1Qz6KQoP9sEZtb1Ba3/8rNxrRUZwtYJjtYnT8k05opaNQsst98h/K6qJ54jKyj4f6rYJd4r3xHOdX5CfqTlza6wFvfDf+J+xvFcQRnBd9tevAfrHG4a/anzNfLJ68hteyY2Yw+IsUzmG6DCmbINDgvwmnw/OPeJiuz7NeMxhfk3QZTRywH/LBav6JgdcE5DxhWyRsv3D/NZP2IatL/4Cu5RRmLyesMm0XVez/copF4Fx/q/0kPeT9lD9Qeo8I+d"
        ),
    ),
}
require(
    len(adr038_historical_overrides) == 1
    and all(path.is_file() for path in adr038_historical_overrides),
    "Pre-ADR-038 replay must reverse-project exactly one additional production file",
)

def pre_adr038_bytes(path):
    current_bytes = pre_adr040_bytes(path)
    override = adr038_historical_overrides.get(path)
    if override is None:
        return current_bytes
    current_sha256, historical_sha256, compressed = override
    require(
        hashlib.sha256(current_bytes).hexdigest() == current_sha256,
        f"ADR-038 normalized delta changed: {path}",
    )
    historical = zlib.decompress(base64.b64decode(compressed))
    require(
        hashlib.sha256(historical).hexdigest() == historical_sha256,
        f"Embedded pre-ADR-038 historical snapshot is invalid: {path}",
    )
    return historical

def historical_bytes(path):
    current_bytes = pre_adr038_bytes(path)
    adr037_override = adr037_historical_overrides.get(path)
    if adr037_override is not None:
        historical_sha256, compressed = adr037_override
        historical = zlib.decompress(base64.b64decode(compressed))
        require(
            hashlib.sha256(historical).hexdigest() == historical_sha256,
            f"Embedded pre-ADR-037 historical snapshot is invalid: {path}",
        )
        return historical
    override = historical_overrides.get(path)
    if override is None:
        return current_bytes
    current_sha256, historical_sha256, compressed = override
    require(
        hashlib.sha256(current_bytes).hexdigest() == current_sha256,
        f"ADR-036 normalized delta changed: {path}",
    )
    historical = zlib.decompress(base64.b64decode(compressed))
    require(
        hashlib.sha256(historical).hexdigest() == historical_sha256,
        f"ADR-034 embedded b620 historical snapshot is invalid: {path}",
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
    "ADR-034 freezes provider-neutral point-in-time raw-window coverage "
    "semantics before any executable raw aggregation or MFE/MAE "
    "calculation."
)
adr_path = Path(
    "decisions/ADR-034-point-in-time-raw-window-coverage-foundation.md"
)
marker_paths = (
    adr_path,
    Path("README.md"),
    Path("quality/P3_ACCEPTANCE.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
documents = {}
for path in marker_paths:
    require(path.is_file(), f"Missing ADR-034 contract document: {path}")
    source = path.read_text(encoding="utf-8")
    documents[path] = source
    require(
        source.count(marker) == 1,
        f"ADR-034 marker must occur exactly once: {path}",
    )

adr = documents[adr_path]
require(
    adr.startswith(
        "# ADR-034 — Point-in-Time Raw-Window Coverage Foundation\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    ),
    "ADR-034 title, accepted status, or date changed",
)
exact_terms = (
    "TRADE_TICK_ONLY_V1",
    "(basis.eventTime, endpointSession.closesAt]",
    "AssetReturnPricePairResolution.Resolved",
    "Covered",
    "CompleteWithoutEligibleTrade",
    "EvidenceUnavailable",
    "availableAt",
    "capturedAt",
    "evaluationAsOf",
    "eventTime <= availableAt <= capturedAt <= evaluationAsOf",
    "upperBound <= availableAt <= capturedAt <= evaluationAsOf",
    "EXACT_CAUSAL_WINDOW_SESSION_UNION",
    "ADR-019",
    "MFE",
    "MAE",
)
require(
    all(term in adr for term in exact_terms),
    "ADR-034 exact reserved vocabulary or source boundary changed",
)
words = re.sub(r"[^a-z0-9]+", " ", adr.lower())
required_groups = (
    ("decision only", "provider neutral", "point in time"),
    ("primary venue", "regular session", "ordered session"),
    ("lower bound", "exclusive", "upper bound", "inclusive"),
    ("trade tick", "provider event", "event time", "trade condition"),
    ("source revision", "provenance", "calendar", "catalog"),
    ("correction", "bust", "predecessor", "watermark"),
    ("future", "invisible", "identity", "cardinality"),
    ("no trade", "halt", "auction", "bar", "straddle"),
    ("bars", "quotes", "fallback"),
    ("co identify", "high", "low", "shared", "evidence"),
    ("separate", "calculator", "readiness"),
    ("lifecycle", "methodology", "publication"),
    ("historical", "trade tick", "product", "feed", "written rights"),
    ("storage", "derived", "display", "redistribution"),
    ("api key", "account", "license", "network", "secret"),
    ("local", "ci", "deployment", "chat", "git"),
)
for group in required_groups:
    require(
        all(term in words for term in group),
        f"ADR-034 required semantic boundary is missing: {group}",
    )
require(
    re.search(r"\b(?:reject|exclude|excluded|absent|no|never|not)\w*\b.{0,120}\bbars?\b",
              words) is not None
    or re.search(r"\bbars?\b.{0,120}\b(?:reject|exclude|excluded|absent|outside|never|not)\w*\b",
                 words) is not None,
    "ADR-034 must explicitly reject bar input or inference",
)
require(
    re.search(r"\b(?:reject|exclude|excluded|absent|no|never|not)\w*\b.{0,120}\bquotes?\b",
              words) is not None
    or re.search(r"\bquotes?\b.{0,120}\b(?:reject|exclude|excluded|absent|outside|never|not)\w*\b",
                 words) is not None,
    "ADR-034 must explicitly reject quotes",
)
require(
    "no `awaitingendpoint`" in adr.lower()
    or "`awaitingendpoint` is absent" in adr.lower()
    or "does not reserve `awaitingendpoint`" in adr.lower(),
    "ADR-034 mature endpoint anchor must not reserve AwaitingEndpoint",
)
require(
    "no `notapplicable`" in adr.lower()
    or "`notapplicable` is absent" in adr.lower()
    or "does not reserve `notapplicable`" in adr.lower(),
    "ADR-034 evidence state must not reserve NotApplicable",
)
require(
    "completewithouteligibletrade" in adr.lower()
    and (
        "zero mfe/mae" in adr.lower()
        or ("zero mfe" in adr.lower() and "zero mae" in adr.lower())
    ),
    "ADR-034 complete-without-trade must never become zero MFE/MAE",
)
require(
    "datacomplete" in words or "data complete" in words,
    "ADR-034 must explicitly keep dataComplete outside the foundation",
)

fence = chr(96) * 3
require(
    fence + "json" not in adr.lower()
    and '"policyVersion"' not in adr
    and "CANONICAL_DEFINITION" not in adr
    and "DEFINITION_HASH" not in adr
    and "## Canonical policy definition" not in adr
    and re.search(r"\b[a-f0-9]{64}\b", adr.lower()) is None,
    "Decision-only ADR-034 must not define canonical policy bytes or hash",
)

raw_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "rawwindowcoverage"
)
raw_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "rawwindowcoverage"
)
require(
    not raw_main_dir.exists() and not raw_test_dir.exists(),
    "Decision-only ADR-034 must not add a rawwindowcoverage Java/test package",
)
raw_type = re.compile(
    r"\bRawWindow(?:Coverage|PriceEvent|Manifest|Request|Resolution|"
    r"Resolver|Aggregator|Calculator|Readiness)\w*\b"
)
raw_policy = re.compile(
    r"\b[A-Z0-9_]*RAW_WINDOW_(?:COVERAGE|AGGREGATION|READINESS)"
    r"[A-Z0-9_]*\b|\bTRADE_TICK_ONLY_V1\b"
)
for root in (
    Path("apps/api/src/main/java"),
    Path("apps/api/src/test/java"),
):
    for path in root.rglob("*.java"):
        normalized_path = re.sub(
            r"[^a-z0-9]", "", path.as_posix().lower()
        )
        source = path.read_text(encoding="utf-8")
        require(
            "rawwindowcoverage" not in normalized_path
            and raw_type.search(source) is None
            and raw_policy.search(source) is None,
            f"Decision-only ADR-034 must not add executable raw-window code: {path}",
        )

protected_paths = sorted(
    {
        Path("contracts/openapi.yaml"),
        *Path("schemas").glob("*.json"),
        *(path for path in Path("fixtures/v1").rglob("*")
          if path.is_file()),
        *(path for path in Path("apps/api/src/main").rglob("*")
          if path.is_file()
          and path not in adr036_new_main_paths | adr037_new_main_paths
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
    },
    key=lambda path: path.as_posix(),
)
require(
    len(protected_paths) == 245
    and digest(protected_paths)
        == "fa101da208d7c56f4625b1504d5fcaefc90ea2503328148641dcad844d5fd543",
    "ADR-034 decision-only protected production baseline changed",
)
test_web_paths = sorted(
    {
        *(path for path in Path("apps/api/src/test").rglob("*")
          if path.is_file()
          and path not in adr036_new_test_paths | adr037_new_test_paths
          | adr038_new_test_paths | adr039_new_test_paths
          | adr040_new_test_paths),
        *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
          for path in root.rglob("*") if path.is_file()),
        *(path for path in Path("apps/web").glob("*")
          if path.is_file() and path.name != "next-env.d.ts"),
    },
    key=lambda path: path.as_posix(),
)
require(
    len(test_web_paths) == 208
    and digest(test_web_paths)
        == "0f1eeec4b75f66e140e7a44cc2c4095bf596a5160e88a8d84c97563f7050425a",
    "ADR-034 decision-only API-test/web baseline changed",
)
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
wrapper_paths = {
    path for path in Path("apps/api/.mvn").rglob("*")
    if path.is_file()
}
require(
    all(path.is_file() for path in dependency_runtime_paths)
    and wrapper_paths == {
        Path("apps/api/.mvn/wrapper/maven-wrapper.properties")
    }
    and len(dependency_runtime_paths) == 9
    and digest(dependency_runtime_paths)
        == "eed3772bb0f0edde1c7b8721c05295165de2c6a7e607ad4b35f7d41ca8620d50",
    "ADR-034 must preserve exact dependency/runtime/provider configuration",
)

workflow = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
guard_step = (
    "\n      - name: Guard point-in-time raw-window coverage foundation\n"
)
guard_start = workflow.index(guard_step)
guard_end = workflow.index("\n      - name: ", guard_start + len(guard_step))
workflow_without_this_guard = (
    workflow[:guard_start] + workflow[guard_end:]
)
forbidden_workflow_markers = (
    "raw-window coverage golden runtime cardinality",
    "rawwindowcoverage",
    "TRADE_TICK_ONLY_V1",
    "RawWindowCoveragePolicyVersion",
    "RawWindowPriceEvent",
    "RawWindowCoverageManifest",
    "RawWindowCoverageRequest",
    "RawWindowCoverageResolution",
    "RawWindowCoverageResolver",
)
require(
    all(
        marker.lower() not in workflow_without_this_guard.lower()
        for marker in forbidden_workflow_markers
    ),
    "Decision-only ADR-034 must not add an API runtime-cardinality gate",
)
outcomes = json.loads(
    Path("fixtures/v1/call-outcomes.json").read_text(encoding="utf-8")
)
require(
    all(
        outcome[metric] is None
        for outcome in outcomes["outcomes"]
        for metric in ("mfe", "mae")
    ),
    "ADR-034 must not publish MFE or MAE",
)

print(
    "Validated ADR-034 decision-only raw-window coverage semantics, "
    "four-document parity, mature endpoint anchor, reserved conceptual "
    "branches, raw/provider/runtime firewalls, unchanged ADR-033 "
    "baselines, and null MFE/MAE publication"
)
PYTHON
