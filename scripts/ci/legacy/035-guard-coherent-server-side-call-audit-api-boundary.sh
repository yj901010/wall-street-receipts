python <<'PYTHON'
import hashlib
import json
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

# Later ADR-035/036/037/038/039 files are proven by their own exact guards. Replay
# this historical boundary against its pre-ADR-035 filesystem/config view.
adr035_later_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogProvider.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/SecEdgarConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/SecEdgarProperties.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/FilingCatalog.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/FilingRecord.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecEdgarFilingCatalogProvider.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecProviderConfigurationException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecProviderException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRequestRateLimitInterceptor.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRequestRateLimiter.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecResponseDecompressionInterceptor.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecResponseSizeLimitInterceptor.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRetryAfterPolicy.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecStringCikDeserializer.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsMapper.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsResponse.java"),
    Path("apps/api/src/main/resources/application-local.yml"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/SecEdgarConfigurationTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRequestRateLimitInterceptorTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRequestRateLimiterTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecResponseSizeLimitInterceptorTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRetryAfterPolicyTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecSubmissionsMapperTest.java"),
}
adr037_new_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/source/SourceResponseReceipt.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRawResponseCapture.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/source/SourceResponseReceiptTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecRawResponseCaptureTest.java"),
}
adr038_new_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingSegmentDescriptor.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/HistoricalFilingSegmentDescriptorTest.java"),
}
require(
    len(adr035_later_paths) == 24
    and all(path.is_file() for path in adr035_later_paths),
    "SEC historical replay exclusion must remain the exact 17+7 files through ADR-036",
)
require(
    len(adr037_new_paths) == 4
    and all(path.is_file() for path in adr037_new_paths),
    "SEC historical replay exclusion must add only the exact ADR-037 2+2 new files",
)
require(
    len(adr038_new_paths) == 2
    and all(path.is_file() for path in adr038_new_paths),
    "SEC historical replay exclusion must add only the exact ADR-038 1+1 new files",
)
adr039_later_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filing/PersistFilingCatalogCaptureService.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureAppendResult.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureProvider.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureReplayVerifier.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/SecFilingCatalogPersistenceConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogCapture.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcFilingCatalogCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecFilingCatalogCaptureReplayVerifier.java"),
    Path("apps/api/src/main/resources/db/migration/V6__sec_filing_catalog_captures.sql"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filing/PersistFilingCatalogCaptureServiceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogCaptureTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/FilingCatalogCapturePersistenceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/support/SecFilingCatalogCaptureTestFixture.java"),
}
require(
    len(adr039_later_paths) == 14
    and all(path.is_file() for path in adr039_later_paths),
    "SEC historical replay exclusion must add the exact ADR-039 persistence delta",
)
adr040_later_paths = {
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
    Path("apps/api/src/main/resources/db/migration/V7__sec_historical_filing_segment_captures.sql"),
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
    len(adr040_later_paths) == 24
    and all(path.is_file() for path in adr040_later_paths),
    "SEC historical replay exclusion must add the exact ADR-040 segment delta",
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
    "SEC historical replay exclusion must add the exact ADR-041 collection delta",
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
adr035_application_config_additions = (
    b"    configprops:\n      show-values: never\n"
    b"    env:\n      show-values: never\n",
    b"  public-data:\n    sec:\n"
    b"      enabled: ${SEC_PROVIDER_ENABLED:false}\n"
    b"      base-url: ${SEC_BASE_URL:https://data.sec.gov}\n"
    b"      contact-email: ${SEC_CONTACT_EMAIL:}\n",
)
original_rglob = Path.rglob
original_read_bytes = Path.read_bytes

def historical_rglob(path, pattern):
    return (
        candidate for candidate in original_rglob(path, pattern)
        if candidate not in (
            adr035_later_paths | adr037_new_paths | adr038_new_paths
            | adr039_later_paths | adr040_later_paths
            | adr041_later_paths
        )
    )

def historical_read_bytes(path):
    source = original_read_bytes(path).replace(b"\r\n", b"\n")
    if path.as_posix() == "apps/api/src/main/resources/application.yml":
        operator_api_addition = (
            b"app:\n"
            b"  operator-api:\n"
            b"    enabled: ${OPERATOR_API_ENABLED:false}\n"
            b"    token-sha256: ${OPERATOR_API_TOKEN_SHA256:}\n"
        )
        require(
            source.count(operator_api_addition) == 1,
            "ADR-043 application.yml authorized later delta changed",
        )
        source = source.replace(operator_api_addition, b"app:\n")
        require(
            hashlib.sha256(source).hexdigest()
                == "fba88168c8ca0ce0c17eeea201fb624b556240ab52297f1bf33ca9388f568a42",
            "ADR-043 application.yml historical reverse projection changed",
        )
        for addition in adr035_application_config_additions:
            require(
                source.count(addition) == 1,
                "ADR-035 application.yml historical delta changed",
            )
            source = source.replace(addition, b"")
    if path.as_posix() == (
        "apps/api/src/main/java/com/wallstreetreceipts/api/web/"
        "RequestIdFilter.java"
    ):
        request_id_replacements = (
            (
                b"import org.springframework.core.Ordered;\n"
                b"import org.springframework.core.annotation.Order;\n",
                b"",
            ),
            (
                b"@Component\n@Order(Ordered.HIGHEST_PRECEDENCE)\n",
                b"@Component\n",
            ),
        )
        for current, historical in request_id_replacements:
            require(
                source.count(current) == 1,
                "ADR-043 request-id filter authorized later delta changed",
            )
            source = source.replace(current, historical)
        require(
            hashlib.sha256(source).hexdigest()
                == "1cd35e0c8d728db84486ad626b6de6b7df6afa2777f2c35371946c58309212ec",
            "ADR-043 request-id filter historical reverse projection changed",
        )
    if path.as_posix() == (
        "apps/api/src/test/java/com/wallstreetreceipts/api/migration/"
        "PostgreSqlMigrationTest.java"
    ):
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
        for current, historical, count in adr041_version_replacements:
            require(
                source.count(current) == count,
                "ADR-041 PostgreSQL Flyway-version delta changed",
            )
            source = source.replace(current, historical)
        require(
            hashlib.sha256(source).hexdigest()
                == "afb2bf1d7f7d864a3f2f2e410d50c58f397af3252041983c2b84e0c321a0b797",
            "ADR-041 PostgreSQL reverse projection changed",
        )
        added_imports = (
            b"import static org.assertj.core.api.Assertions.catchThrowable;\n",
            b"import com.wallstreetreceipts.api.application.port.out."
            b"HistoricalFilingSegmentCaptureAppendResult;\n",
            b"import com.wallstreetreceipts.api.infrastructure.persistence."
            b"JdbcHistoricalFilingSegmentCaptureRepository;\n",
            b"import com.wallstreetreceipts.api.infrastructure.provider.sec."
            b"SecHistoricalFilingSegmentCaptureReplayVerifier;\n",
            b"import com.wallstreetreceipts.api.support."
            b"SecHistoricalFilingSegmentCaptureTestFixture;\n",
        )
        require(
            all(source.count(marker) == 1 for marker in added_imports),
            "ADR-040 PostgreSQL migration-test imports changed",
        )
        for marker in added_imports:
            source = source.replace(marker, b"")
        for start, end in (
            (
                b"\n    @Test\n    void "
                b"v7UpgradesV6AndAppendsHistoricalSegmentsAtomicallyOnPostgreSql()",
                b"\n    private static FilingCatalogCaptureAppendResult "
                b"concurrentCaptureAppend(",
            ),
            (
                b"\n    private static "
                b"HistoricalFilingSegmentCaptureAppendResult "
                b"concurrentSegmentAppend(",
                b"\n    private static ConcurrentCaptureAppendAttempt "
                b"concurrentCaptureAppendAttempt(",
            ),
            (
                b"\n    private record ConcurrentSegmentAppendAttempt(",
                b"\n    private static String concurrentContextImport(",
            ),
        ):
            require(
                source.count(start) == 1 and source.count(end) == 1,
                "ADR-040 PostgreSQL migration-test block changed",
            )
            start_index = source.index(start)
            end_index = source.index(end, start_index)
            source = source[:start_index] + source[end_index:]
        version_replacements = (
            (
                b"assertThat(flyway.info().applied()).hasSize(7);",
                b"assertThat(flyway.info().applied()).hasSize(6);",
                1,
            ),
            (
                b"assertThat(latest.info().current().getVersion().getVersion())"
                b'.isEqualTo("7");',
                b"assertThat(latest.info().current().getVersion().getVersion())"
                b'.isEqualTo("6");',
                2,
            ),
            (
                b'.locations("classpath:db/migration")\n'
                b'                .target("6")\n'
                b"                .load();",
                b'.locations("classpath:db/migration")\n'
                b"                .load();",
                1,
            ),
        )
        for current, historical, count in version_replacements:
            require(
                source.count(current) == count,
                "ADR-040 PostgreSQL Flyway-version delta changed",
            )
            source = source.replace(current, historical)
        require(
            hashlib.sha256(source).hexdigest()
                == "e6ee0f96789697414853562e47e698da088debec246f1cfe178661380163458b",
            "ADR-040 PostgreSQL reverse projection changed",
        )
    return source

Path.rglob = historical_rglob
Path.read_bytes = historical_read_bytes

web_root = Path("apps/web/src")
provider_root = web_root / "lib/providers"
expected_provider_files = {
    "call-audit-provider.ts",
    "call-audit-adapter.ts",
    "fixture-call-audit-provider.ts",
    "api-call-audit-provider.server.ts",
    "call-audit-provider.server.ts",
}
actual_provider_files = {
    path.name
    for path in provider_root.glob("*call-audit*.ts")
    if not path.name.endswith(".test.ts")
}
require(
    actual_provider_files == expected_provider_files,
    f"Call-audit production provider set is not exact: {sorted(actual_provider_files)}",
)

interface_path = provider_root / "call-audit-provider.ts"
adapter_path = provider_root / "call-audit-adapter.ts"
fixture_path = provider_root / "fixture-call-audit-provider.ts"
api_path = provider_root / "api-call-audit-provider.server.ts"
factory_path = provider_root / "call-audit-provider.server.ts"
page_path = web_root / "app/calls/[id]/page.tsx"
messages_path = web_root / "app/calls/messages.ts"
styles_path = web_root / "app/globals.css"
required_paths = (
    interface_path, adapter_path, fixture_path, api_path, factory_path,
    page_path, messages_path, styles_path,
)
require(all(path.is_file() for path in required_paths), "Call-audit source boundary is incomplete")

interface_source = interface_path.read_text(encoding="utf-8")
adapter_source = adapter_path.read_text(encoding="utf-8")
fixture_source = fixture_path.read_text(encoding="utf-8")
api_source = api_path.read_text(encoding="utf-8")
factory_source = factory_path.read_text(encoding="utf-8")
page_source = page_path.read_text(encoding="utf-8")
messages_source = messages_path.read_text(encoding="utf-8")
styles_source = styles_path.read_text(encoding="utf-8")

require(
    all(marker in interface_source for marker in (
        "export type CallAuditSnapshot", "detail: AnalystCallDetail",
        "context: CallContext", "revisions: readonly CallRevision[]",
        "outcomes: readonly CallOutcome[]",
        "findById(callId: string): Promise<CallAuditSnapshot | null>",
    )),
    "CallAuditProvider must expose one closed detail/context/revision/outcome aggregate",
)
revision_fields = {
    "revisionId", "schemaVersion", "callId", "supersedesRevisionId",
    "sequenceNumber", "provider", "providerEventId", "revisionType",
    "eventTime", "processingTime", "correctedTerms", "reason",
    "sourceReferenceId", "dataMode", "capturedAt", "provenanceId",
}
corrected_fields = {
    "direction", "originalRating", "previousTarget", "target", "currency", "targetDate",
}
require(
    all(re.search(rf"\b{re.escape(field)}\b", interface_source) for field in revision_fields),
    "CallRevision is missing canonical fields",
)
require(
    all(re.search(rf"\b{re.escape(field)}\b", interface_source) for field in corrected_fields),
    "CorrectedCallTerms is missing canonical fields",
)
outcome_fields = {
    "outcomeId", "schemaVersion", "callId", "horizon", "basisRevisionId",
    "cancellationRevisionId", "snapshotId", "methodologyId", "methodologyVersion",
    "methodologyDefinitionHash", "inputFingerprint", "sequenceNumber",
    "supersedesOutcomeId", "evaluationStatus", "reasonCode", "eventTime",
    "processingTime", "assetReturn", "benchmarkReturn", "sectorReturn", "alpha",
    "sectorAlpha", "mfe", "mae", "targetHit", "directionalWin", "targetError",
    "dataComplete", "dataMode", "capturedAt", "provenanceId",
}
require(
    all(re.search(rf"\b{re.escape(field)}\b", interface_source) for field in outcome_fields),
    "CallOutcome is missing canonical fields",
)
outcome_type_match = re.search(
    r"export type CallOutcome\s*=\s*\{(?P<body>[\s\S]*?)\n\};",
    interface_source,
)
require(outcome_type_match is not None, "CallOutcome type declaration is missing")
declared_outcome_fields = set(re.findall(
    r"^\s{2}([A-Za-z][A-Za-z0-9]*):", outcome_type_match.group("body"), re.MULTILINE
))
require(
    declared_outcome_fields == outcome_fields,
    f"CallOutcome field set is not exact: {sorted(declared_outcome_fields)}",
)
snapshot_type_match = re.search(
    r"export type CallAuditSnapshot\s*=\s*\{(?P<body>[\s\S]*?)\n\};",
    interface_source,
)
require(snapshot_type_match is not None, "CallAuditSnapshot type declaration is missing")
declared_snapshot_fields = set(re.findall(
    r"^\s{2}([A-Za-z][A-Za-z0-9]*):", snapshot_type_match.group("body"), re.MULTILINE
))
require(
    declared_snapshot_fields == {"detail", "context", "revisions", "outcomes"},
    f"CallAuditSnapshot field set is not exact: {sorted(declared_snapshot_fields)}",
)
require(
    all(marker in interface_source for marker in (
        "CALL_OUTCOME_HORIZONS", "CALL_OUTCOME_EVALUATION_STATUSES",
        "CALL_OUTCOME_REASON_CODES", "export type CallOutcome",
    )),
    "CallOutcome must retain closed horizon/status/reason/type declarations",
)
require(
    'CALL_OUTCOME_HORIZONS = ["D1", "W1", "M1", "M3", "M6", "Y1"] as const'
    in interface_source
    and 'CALL_OUTCOME_EVALUATION_STATUSES = ["PENDING", "INCOMPLETE"] as const'
    in interface_source
    and 'CALL_OUTCOME_REASON_CODES = ["HORIZON_NOT_REACHED", "HORIZON_DATA_MISSING"] as const'
    in interface_source
    and "cancellationRevisionId: null;" in outcome_type_match.group("body")
    and "dataComplete: false;" in outcome_type_match.group("body")
    and 'dataMode: "DEMO";' in outcome_type_match.group("body")
    and all(
        f"{field}: null;" in outcome_type_match.group("body")
        for field in (
            "assetReturn", "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
            "mfe", "mae", "targetHit", "directionalWin", "targetError",
        )
    ),
    "CallOutcome type must remain exact PENDING/INCOMPLETE DEMO with null metrics/results",
)

require(
    all(marker in api_source for marker in (
        "typeof window", 'method: "GET"', 'cache: "no-store"',
        'redirect: "error"', 'Accept: "application/json"',
        'split(";", 1)', 'contentType !== "application/json"',
        "encodeURIComponent(callId)", "Promise.all([",
        "v1/calls/${encodedCallId}", "v1/calls/${encodedCallId}/context",
        "v1/calls/${encodedCallId}/revisions", "v1/calls/${encodedCallId}/outcomes",
        "response.status === 404",
    )),
    "API call-audit transport is missing exact four-read GET/no-store/redirect/media/path semantics",
)
detail_request = api_source.find('"detail"')
dependent_requests = api_source.find("Promise.all([")
require(
    0 <= detail_request < dependent_requests,
    "API call-audit transport must establish detail existence before dependent reads",
)
require(
    not any(marker in api_source for marker in (
        "fixtures/", "fixture-call-audit-provider", "NEXT_PUBLIC_",
        "localStorage", "sessionStorage", "document.cookie", "Authorization",
    )),
    "API call-audit transport crosses its server/source boundary",
)

require("typeof window" in factory_source, "Call-audit factory lacks the server runtime guard")
require(
    'process.env.CALL_AUDIT_PROVIDER ?? "fixture"' in factory_source
    and 'configuredProvider === "fixture"' in factory_source
    and 'configuredProvider === "api"' in factory_source
    and "process.env.API_BASE_URL" in factory_source,
    "Call-audit factory must select exact fixture/api modes and private API_BASE_URL",
)
require(
    ".toLowerCase(" not in factory_source and ".toLocaleLowerCase(" not in factory_source,
    "Call-audit provider selector must not normalize unsupported values",
)
require(
    "catch" not in factory_source and "fallback" not in factory_source.lower(),
    "Call-audit factory must not implement runtime fallback",
)

require(
    "fixtures/v1/analyst-call-revisions.json" in fixture_source
    and "fixtures/v1/call-outcomes.json" in fixture_source
    and "FixtureCallsProvider" in fixture_source
    and "validateCallAuditSnapshot" in fixture_source
    and "normalizeFixtureOutcomeOrder" in fixture_source
    and "compareCallAuditInstants" in fixture_source,
    "Fixture call-audit mode must assemble and validate one canonical four-surface aggregate",
)
require(
    "Date.parse(" not in fixture_source
    and "API_BASE_URL" not in fixture_source
    and "fetch(" not in fixture_source,
    "Fixture call-audit mode must remain microsecond-safe and transport-free",
)

adapter_markers = (
    "closedRecord", "adaptCallDetailResponse", "adaptCallContextResponse",
    "adaptCallRevisionsResponse", "adaptCallOutcomesResponse", "validateCallAuditSnapshot",
    'detail.call.dataMode !== "DEMO"', "nullablePositiveNumber(record.assetPrice",
    "absolute magnitude below 1e26",
    "vintageStart must not follow vintageEnd", "must be active on the snapshot event date",
    "must not precede eventTime", "providerEventIdentity",
    r"${revision.provider}\u0000${revision.providerEventId}",
    "has an eventTime earlier than the prior revision",
    "appears after a terminal cancellation", "predates the original call event",
    "validateOutcomeResponse", "validateOutcomeJoins",
    "must remain JSON null in the P2 audit-only boundary",
    "must remain PENDING or INCOMPLETE in the P2 audit-only boundary",
    "must remain DEMO in the P2 audit-only boundary",
    "const OUTCOME_NULL_FIELDS", "for (const field of OUTCOME_NULL_FIELDS)",
    "exactNull(record[field]",
    "is not in deterministic server order",
    "duplicates a natural outcome identity",
    "does not supersede its immediate lineage predecessor",
    "moves a lineage timestamp backwards",
)
require(
    all(marker in adapter_source for marker in adapter_markers),
    "Call-audit adapter lacks closed DEMO/chronology/vintage/lineage validation",
)
outcome_adapter_start = adapter_source.index("function adaptOutcome(")
outcome_adapter_end = adapter_source.index("function outcomeOrder(", outcome_adapter_start)
outcome_adapter_source = adapter_source[outcome_adapter_start:outcome_adapter_end]
require(
    "finiteNumber(" not in outcome_adapter_source
    and "nullableNumber(" not in outcome_adapter_source
    and "parseFloat(" not in outcome_adapter_source
    and "Number(" not in outcome_adapter_source,
    "P2 outcome adaptation must reject metrics/results as non-null rather than parse numbers",
)
require(
    "return [...values].sort(rawOutcomeOrder);" in fixture_source,
    "Only fixture normalization may sort a copied outcome array into API order",
)
require(
    not re.search(r"\.provenanceId\s*!==\s*[^\n;]*\.provenanceId", adapter_source),
    "Call-audit adapter must not invent cross-record provenance equality",
)

require(
    "callAuditProvider" in page_source
    and "const { detail, context, revisions, outcomes } = audit" in page_source
    and "revision.revisionId" in page_source
    and "revision.correctedTerms" in page_source
    and "outcomes" in page_source
    and re.search(
        r'className="detail-section outcome-section"\s+'
        r'aria-labelledby="outcome-title"\s+tabIndex=\{0\}',
        page_source,
    ),
    "Call-detail page must render one-provider revisions and outcome audit evidence",
)
require(
    re.search(
        r"\.outcome-evidence-grid\s*>\s*div\s*\{[^}]*\bmin-width\s*:\s*0\s*;",
        styles_source,
    )
    and re.search(
        r"\.outcome-evidence-grid\s+dd\s*\{[^}]*\bmin-width\s*:\s*0\s*;"
        r"[^}]*\boverflow-wrap\s*:\s*anywhere\s*;",
        styles_source,
    ),
    "Outcome evidence must contain long canonical hashes without page overflow",
)
require(
    "callsProvider" not in page_source
    and "FixtureCallAuditProvider" not in page_source
    and "ApiCallAuditProvider" not in page_source,
    "Call-detail page must not bypass the whole-audit factory",
)
require(
    "utc(revision." not in page_source
    and "formatMoney(revision.correctedTerms" not in page_source,
    "Revision ISO instants and corrected targets must render without formatter rounding",
)
require(
    all(marker in messages_source for marker in (
        "revisionHistory", "revisionAppendOnly", "noRevisionsTitle",
        "noRevisionsDescription", "cancellationTermsUnavailable",
    ))
    and "불변 이벤트 계보" in messages_source
    and "Immutable event lineage" in messages_source
    and "current or effective stance" in messages_source,
    "Revision audit needs Korean and English presentation copy",
)
require(
    all(marker in messages_source for marker in (
        'errorTitle: "콜 증거를 읽을 수 없습니다."',
        'errorTitle: "Call evidence could not be read."',
        'notFoundTitle: "이 이벤트는 정규 콜 원장에 없습니다."',
        'notFoundTitle: "This event is not in the canonical call ledger."',
    ))
    and "픽스처를 읽을 수 없습니다." not in messages_source
    and "The fixture could not be read." not in messages_source
    and "픽스처 원장에 없습니다." not in messages_source
    and "not in the fixture ledger" not in messages_source,
    "Call error and not-found copy must remain provider-neutral in Korean and English",
)

# Build a production import graph and prove no Client Component can
# reach either private `.server.ts` module. The explicit window guards
# remain defense in depth; no new package/ambient alias is required.
production_paths = tuple(
    sorted(
        path for path in web_root.rglob("*")
        if path.is_file()
        and path.suffix in {".ts", ".tsx"}
        and ".test." not in path.name
    )
)
production_set = {path.resolve() for path in production_paths}
import_pattern = re.compile(
    r"(?:import|export)\s+(?:(?!;)[\s\S])*?[\"']([^\"']+)[\"']"
)
dynamic_import_pattern = re.compile(r"import\s*\(\s*[\"']([^\"']+)[\"']\s*\)")
commonjs_require_pattern = re.compile(r"require\s*\(\s*[\"']([^\"']+)[\"']\s*\)")

def resolve_import(owner, specifier):
    if specifier.startswith("@/"):
        candidate = web_root / specifier[2:]
    elif specifier.startswith("."):
        candidate = owner.parent / specifier
    else:
        return None
    variants = (
        candidate,
        Path(f"{candidate}.ts"),
        Path(f"{candidate}.tsx"),
        candidate / "index.ts",
        candidate / "index.tsx",
    )
    for variant in variants:
        if variant.is_file() and variant.resolve() in production_set:
            return variant.resolve()
    return None

graph = {}
production_sources = {}
for source_path in production_paths:
    source = source_path.read_text(encoding="utf-8")
    production_sources[source_path.resolve()] = source
    module_specifiers = (
        import_pattern.findall(source)
        + dynamic_import_pattern.findall(source)
        + commonjs_require_pattern.findall(source)
    )
    graph[source_path.resolve()] = {
        resolved for specifier in module_specifiers
        if (resolved := resolve_import(source_path, specifier)) is not None
    }
server_paths = {api_path.resolve(), factory_path.resolve()}
api_importers = {owner for owner, imports in graph.items() if api_path.resolve() in imports}
factory_importers = {owner for owner, imports in graph.items() if factory_path.resolve() in imports}
require(
    api_importers == {factory_path.resolve()},
    f"Only the whole-audit factory may import API transport: {sorted(map(str, api_importers))}",
)
require(
    factory_importers == {page_path.resolve()},
    f"Only the call-detail server page may import the audit factory: {sorted(map(str, factory_importers))}",
)

def reachable_from(root):
    pending = [root]
    visited = set()
    while pending:
        candidate = pending.pop()
        if candidate in visited:
            continue
        visited.add(candidate)
        pending.extend(graph.get(candidate, ()))
    return visited

fixture_calls_path = (provider_root / "fixture-calls-provider.ts").resolve()
api_descendants = reachable_from(api_path.resolve())
require(
    fixture_path.resolve() not in api_descendants
    and fixture_calls_path not in api_descendants,
    "API call-audit descendant graph must not reach fixture aggregate or legacy fixture calls",
)
require(
    all(
        "fixtures/" not in production_sources.get(candidate, "").replace("\\", "/")
        for candidate in api_descendants
    ),
    "API call-audit descendant graph must not reach raw canonical fixture imports",
)

fixture_descendants = reachable_from(fixture_path.resolve())
require(
    api_path.resolve() not in fixture_descendants
    and factory_path.resolve() not in fixture_descendants,
    "Fixture call-audit descendant graph must not reach API transport or source factory",
)
fixture_descendant_source = "\n".join(
    production_sources.get(candidate, "") for candidate in fixture_descendants
)
require(
    "API_BASE_URL" not in fixture_descendant_source
    and "fetch(" not in fixture_descendant_source,
    "Fixture call-audit descendant graph must remain private-transport free",
)
client_roots = {
    path.resolve() for path in production_paths
    if re.match(r"^\s*[\"']use client[\"'];", path.read_text(encoding="utf-8"))
}
for client_root in client_roots:
    pending = [client_root]
    visited = set()
    while pending:
        candidate = pending.pop()
        if candidate in visited:
            continue
        visited.add(candidate)
        require(
            candidate not in server_paths,
            f"Client import graph reaches private call-audit server module: {client_root} -> {candidate}",
        )
        pending.extend(graph.get(candidate, ()))

env_entries = {}
for raw_line in Path(".env.example").read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    env_entries[key] = value
require(env_entries.get("API_BASE_URL") == "http://localhost:8080", "Private API_BASE_URL default changed")
require(env_entries.get("CALL_AUDIT_PROVIDER") == "api", "Documented local call audit must use api mode")
require("NEXT_PUBLIC_API_BASE_URL" not in env_entries, "Private API origin must not be public")
readme_source = Path("README.md").read_text(encoding="utf-8")
require(
    '$env:CALL_AUDIT_PROVIDER = "api"' in readme_source
    and '$env:API_BASE_URL = "http://localhost:8080"' in readme_source
    and "CALL_AUDIT_PROVIDER=api API_BASE_URL=http://localhost:8080 pnpm --dir apps/web dev" in readme_source,
    "Local web startup docs must pass the private API selector/origin into the Next process",
)

expected_schemas = {
    "analyst-call-revision.schema.json", "analyst-call.schema.json",
    "call-context.schema.json", "call-outcome.schema.json", "event-context.schema.json",
    "macro-observation.schema.json", "macro-snapshot.schema.json", "market-board.schema.json",
    "market-map.schema.json", "market-snapshot.schema.json", "market-treemap.schema.json",
    "scoring-methodology.schema.json", "source-document.schema.json", "source-reference.schema.json",
}
expected_fixtures = {
    "analyst-call-revisions.json", "analyst-calls.json", "call-contexts.json",
    "call-outcomes.json", "manifest.json", "market-board.json",
    "market-map-nasdaq100.json", "market-map.json", "market-snapshots.json",
    "market-treemap-nasdaq100.json", "market-treemap-sp500.json",
    "master-data.json", "timeline-nvda.json",
}
expected_migrations = {
    "V1__baseline.sql", "V2__analyst_calls.sql", "V3__analyst_call_revisions.sql",
    "V4__call_outcomes.sql", "V5__call_contexts.sql",
    "V6__sec_filing_catalog_captures.sql",
    "V7__sec_historical_filing_segment_captures.sql",
    "V8__sec_filing_history_collection_manifests.sql",
    "V9__sec_filing_collection_attempts.sql",
}
require(
    {path.name for path in Path("schemas").glob("*.json")} == expected_schemas,
    "Call-audit web transport must not change the canonical schema set",
)
require(
    {path.name for path in Path("fixtures/v1").glob("*.json")} == expected_fixtures,
    "Call-audit web transport must not change the canonical fixture set",
)
require(
    {path.name for path in Path("apps/api/src/main/resources/db/migration").glob("*.sql")}
    == expected_migrations,
    "Call-audit web transport must not change Flyway",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
openapi_paths = set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
require(
    openapi_paths == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Call-audit web transport must consume the unchanged five-path OpenAPI surface",
)
protected_paths = sorted(
    {
        Path("contracts/openapi.yaml"),
        *Path("schemas").glob("*.json"),
        *Path("fixtures/v1").glob("*.json"),
        *(
            path for path in Path("apps/api/src/main").rglob("*")
            if path.is_file()
        ),
    },
    key=lambda path: path.as_posix(),
)
protected_digest = hashlib.sha256()
for protected_path in protected_paths:
    protected_digest.update(protected_path.as_posix().encode("utf-8"))
    protected_digest.update(b"\0")
    protected_digest.update(
        protected_path.read_bytes().replace(b"\r\n", b"\n")
    )
    protected_digest.update(b"\0")
require(
    len(protected_paths) == 232
    and protected_digest.hexdigest()
    == "2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941",
    "Call-outcome audit consumer must preserve the current approved OpenAPI/schema/fixture/API production baseline",
)
api_main_source = "\n".join(
    path.read_text(encoding="utf-8")
    for path in Path("apps/api/src/main").rglob("*")
    if path.is_file() and path.suffix.lower() in {".java", ".sql", ".yml", ".yaml"}
)
require(
    "CallAudit" not in api_main_source and "/call-audit" not in api_main_source,
    "P2 call-audit consumer must not add a Spring audit endpoint or model",
)

provider_test_paths = {
    *provider_root.glob("*call-audit*.test.ts"),
    *provider_root.glob("*call-outcome*.test.ts"),
}
provider_tests = {path.name for path in provider_test_paths}
require(
    any("adapter" in name for name in provider_tests)
    and any("api" in name for name in provider_tests)
    and any("fixture" in name for name in provider_tests)
    and any("provider.server" in name for name in provider_tests)
    and "call-outcome-adapter.test.ts" in provider_tests,
    f"Missing adapter/API/fixture/factory call-audit unit tests: {sorted(provider_tests)}",
)
provider_test_source = "\n".join(
    path.read_text(encoding="utf-8") for path in sorted(provider_test_paths)
)
require(
    all(marker in provider_test_source for marker in (
        "redirect", "application/jsonp", "REALTIME", "DELAYED", "EOD",
        "microsecond", "provider-event identity tuple", "vintage", "assetPrice",
        "rejects unsupported selector", "1e26",
        "returns null after the detail 404 without requesting dependent resources",
        "rejects an exact dependent %s 404 while only detail 404 maps to not found",
        'it.each(["context", "revisions", "outcomes"] as const)',
    )),
    "Call-audit unit tests must cover transport and canonical negative boundaries",
)
require(
    all(marker in provider_test_source for marker in (
        "outcome-demo-call-001-d1-v1-001",
        "outcome-demo-call-001-d1-v1-002",
        "outcome-demo-call-001-d1-v2-001",
        "outcome-demo-call-001-m1-v1-001",
        "PENDING", "HORIZON_NOT_REACHED", "INCOMPLETE",
        "HORIZON_DATA_MISSING", "CALCULATED", "EXCLUDED",
        "natural outcome identity", "methodology",
        "deterministic server order", "immediate lineage predecessor",
        "lineage timestamp backwards",
        "locks methodologyId final tie order with a mutation-sensitive response pair",
        "locks methodologyVersion as raw lexical text rather than semantic-version order",
        "locks outcomeId final tie order across independent basis lineages",
        "accepts a valid distinct-basis lineage interleave without folding either lineage",
        "rejects each snapshot availability timestamp independently when unavailable by outcome processing",
        'for (const field of ["processingTime", "capturedAt"] as const)',
        'methodologyVersion: "10.0.0"', 'methodologyVersion: "2.0.0"',
    ))
    and all(field in provider_test_source for field in (
        "assetReturn", "benchmarkReturn", "sectorReturn", "alpha",
        "sectorAlpha", "mfe", "mae", "targetHit", "directionalWin",
        "targetError",
    )),
    "Outcome tests must lock populated order, exact null phase guard, identity, and lineage",
)

page_test_path = web_root / "app/calls/[id]/page.test.tsx"
require(page_test_path.is_file(), "Missing call-detail page contract tests")
page_test_source = page_test_path.read_text(encoding="utf-8")
calls_page_test_path = web_root / "app/calls/page.test.tsx"
require(calls_page_test_path.is_file(), "Missing call-ledger state tests")
calls_page_test_source = calls_page_test_path.read_text(encoding="utf-8")
require(
    all(marker in page_test_source for marker in (
        "ACTIVE", "BULLISH", "235", "상단 상태는 변경 불가 원본 이벤트 필드",
    )),
    "Call-detail tests must lock original status/direction/target and raw microsecond revisions",
)
microsecond_instants = set(re.findall(
    r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z", page_test_source
))
high_precision_targets = re.findall(r"\b\d+\.\d{3,}\b", page_test_source)
require(
    len(microsecond_instants) >= 3 and high_precision_targets,
    "Call-detail synthetic test must retain three raw microsecond instants and a precise target",
)
require(
    "콜 증거를 읽을 수 없습니다." in calls_page_test_source
    and "이 이벤트는 정규 콜 원장에 없습니다." in page_test_source,
    "Call route-state tests must exercise provider-neutral error and not-found copy",
)
require(
    all(marker in page_test_source for marker in (
        "outcome-demo-call-001-d1-v1-001",
        "outcome-demo-call-001-d1-v1-002",
        "outcome-demo-call-001-d1-v2-001",
        "outcome-demo-call-001-m1-v1-001",
        "HORIZON_DATA_MISSING", "HORIZON_NOT_REACHED",
        "방법론 정의 해시", "입력 지문",
        "Methodology definition hash", "Input fingerprint",
        "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
        "b359ec47c7a5b17bc6a7ee18e82f1fe92eb100f9e2abee23a8e3c9aa7b94acd6",
    )),
    "Call-detail tests must render every outcome lineage and label distinct hash evidence",
)
require(
    all(marker in page_test_source for marker in (
        'querySelectorAll("dt")', 'querySelectorAll("dd")',
        "expect(firstOutcomeLabels).toEqual([",
        "expect(firstOutcomeValues).toEqual([",
    )),
    "Call-detail tests must compare exact 31-label and 31-value outcome evidence arrays",
)
label_array = re.search(
    r"expect\(firstOutcomeLabels\)\.toEqual\(\[(?P<body>[\s\S]*?)\]\);",
    page_test_source,
)
value_array = re.search(
    r"expect\(firstOutcomeValues\)\.toEqual\(\[(?P<body>[\s\S]*?)\]\);",
    page_test_source,
)
require(
    label_array is not None and value_array is not None
    and len(re.findall(r'^\s*"(?:[^"\\]|\\.)*",?\s*$', label_array.group("body"), re.MULTILINE)) == 31
    and len(re.findall(r'^\s*"(?:[^"\\]|\\.)*",?\s*$', value_array.group("body"), re.MULTILINE)) == 31,
    "Call-detail outcome evidence arrays must each remain exactly 31 entries",
)

e2e_path = Path("apps/web/e2e/call-revisions.spec.ts")
require(e2e_path.is_file(), "Missing call-revisions Playwright integration source")
e2e_source = e2e_path.read_text(encoding="utf-8")
require(
    all(marker in e2e_source for marker in (
        "demo-call-002", "demo-call-001", "demo-call-revision-001",
        "demo-call-revision-002", "CORRECTION", "CANCELLATION",
        "ACTIVE", "BULLISH", "235", "current or effective stance",
        "collectRuntimeErrors", 'page.on("request"', "browserApiRequests",
    )),
    "Call-revisions E2E must cover populated/empty/server-only/runtime boundaries",
)
outcome_e2e_path = Path("apps/web/e2e/call-outcomes.spec.ts")
require(outcome_e2e_path.is_file(), "Missing call-outcomes Playwright integration source")
outcome_e2e_source = outcome_e2e_path.read_text(encoding="utf-8")
require(
    all(marker in outcome_e2e_source for marker in (
        "demo-call-001", "demo-call-002",
        "outcome-demo-call-001-d1-v1-001",
        "outcome-demo-call-001-d1-v1-002",
        "outcome-demo-call-001-d1-v2-001",
        "outcome-demo-call-001-m1-v1-001",
        "HORIZON_DATA_MISSING", "HORIZON_NOT_REACHED",
        "collectRuntimeErrors", 'page.on("request"', "browserApiRequests",
        "expectNoPageOverflow", "expectVisibleKeyboardFocus",
        'name: "거시 관측 증거 표"',
        'section[aria-labelledby="outcome-title"]',
        "await macroRegion.focus();", 'await page.keyboard.press("Tab");',
        "await expectVisibleKeyboardFocus(koreanOutcome);",
        "outcomeSectionMetrics", "firstRecordMetrics", "scrollWidth", "clientWidth",
        "methodologyHashEvidence", "inputFingerprintEvidence", "evidenceMetrics",
        "methodologyHashBox", "inputFingerprintBox", "boundingBox()",
        "expect(evidenceMetrics.scrollWidth).toBeLessThanOrEqual(evidenceMetrics.clientWidth + 1)",
    )),
    "Call-outcomes E2E must cover populated/empty/server-only/runtime boundaries",
)
require(
    re.search(
        r"await macroRegion\.focus\(\);\s*"
        r"await page\.keyboard\.press\(\"Tab\"\);\s*"
        r"await expectVisibleKeyboardFocus\(koreanOutcome\);",
        outcome_e2e_source,
    )
    and not re.search(r"\b(?:koreanOutcome|englishOutcome|emptyOutcome)\.focus\(", outcome_e2e_source),
    "Call-outcomes keyboard gate must Tab from context into outcome evidence without direct focus",
)
playwright_source = Path("apps/web/playwright.config.ts").read_text(encoding="utf-8")
require(
    all(marker in playwright_source for marker in (
        'name: "chromium-1440"', 'viewport: { width: 1440, height: 1000 }',
        'name: "chromium-1280"', 'viewport: { width: 1280, height: 900 }',
        'name: "chromium-390"', 'viewport: { width: 390, height: 844 }',
    )),
    "Call-outcomes overflow gate must retain the shared 1440/1280/390 project matrix",
)
workflow_source = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
integration_start = workflow_source.index("\n  call-audit-integration:\n")
integration_end = workflow_source.index("\n  api:\n", integration_start)
integration_source = workflow_source[integration_start:integration_end]
require(
    all(marker in integration_source for marker in (
        "call-audit-integration:", "postgres:17-alpine", "CALL_AUDIT_PROVIDER: api",
        "API_BASE_URL: http://localhost:8080", "call-revisions.spec.ts",
        "call-outcomes.spec.ts",
        "SERVER_TOMCAT_ACCESSLOG_BUFFERED: 'false'", "call_audit_access",
        "Verify Next requested the real list and all audit resources",
        "GET /v1/calls/demo-call-002- 200",
        "GET /v1/calls/demo-call-002/context- 200",
        "GET /v1/calls/demo-call-002/revisions- 200",
        "GET /v1/calls/demo-call-001- 200",
        "GET /v1/calls/demo-call-001/context- 200",
        "GET /v1/calls/demo-call-001/revisions- 200",
        "GET /v1/calls/demo-call-002/outcomes- 200",
        "GET /v1/calls/demo-call-001/outcomes- 200",
    )),
    "CI must retain the real PostgreSQL/Spring/Next call-audit integration gate",
)

print(
    "Validated coherent DEMO-only detail/context/revision/outcome providers, private exact "
    "transport, reverse client isolation, and unchanged canonical/backend surfaces"
)
PYTHON
