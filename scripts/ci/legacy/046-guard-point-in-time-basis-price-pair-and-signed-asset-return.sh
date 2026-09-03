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

def scrub_java(source, strip_literals):
    output = []
    index = 0
    state = "CODE"
    quote = None
    while index < len(source):
        current = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "CODE":
            if current in {'"', "'"}:
                state = "STRING" if current == '"' else "CHAR"
                quote = current
                output.append(
                    ('""' if current == '"' else "''")
                    if strip_literals else current
                )
                index += 1
            elif current == "/" and following == "/":
                state = "LINE_COMMENT"
                index += 2
            elif current == "/" and following == "*":
                state = "BLOCK_COMMENT"
                index += 2
            else:
                output.append(current)
                index += 1
        elif state in {"STRING", "CHAR"}:
            if current == "\\" and following:
                if not strip_literals:
                    output.extend((current, following))
                index += 2
            elif current == quote:
                if not strip_literals:
                    output.append(current)
                state = "CODE"
                quote = None
                index += 1
            else:
                if not strip_literals:
                    output.append(current)
                index += 1
        elif state == "LINE_COMMENT":
            if current in "\r\n":
                output.append(current)
                state = "CODE"
            index += 1
        elif current == "*" and following == "/":
            state = "CODE"
            index += 2
        else:
            if current in "\r\n":
                output.append(current)
            index += 1
    require(
        state not in {"STRING", "CHAR", "BLOCK_COMMENT"},
        "Java source contains an unterminated lexical token",
    )
    return "".join(output)

def without_comments(source):
    return scrub_java(source, False)

def without_comments_or_strings(source):
    return scrub_java(source, True)

def compact(source):
    return re.sub(r"\s+", "", source)

def validate_java_lexical_safety(source, label):
    require(
        re.search(r"\\u+[0-9a-fA-F]{4}", source) is None,
        f"{label} must not contain Java Unicode escapes",
    )
    require('"""' not in source, f"{label} must not contain a text block")
    state = "code"
    index = 0
    while index < len(source):
        current = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if current == "/" and following == "/":
                state = "line_comment"
                index += 2
                continue
            if current == "/" and following == "*":
                state = "block_comment"
                index += 2
                continue
            if current == '"':
                state = "string"
            elif current == "'":
                state = "character"
        elif state == "line_comment":
            if source.startswith(("/*", "*/"), index):
                raise ValueError(
                    f"{label} line comment contains a block delimiter"
                )
            if current in "\r\n":
                state = "code"
        elif state == "block_comment":
            if current == "*" and following == "/":
                state = "code"
                index += 2
                continue
        elif state == "string":
            if current == "\\":
                index += 2
                continue
            if source.startswith(("//", "/*", "*/"), index):
                raise ValueError(
                    f"{label} string contains a comment delimiter"
                )
            if current == '"':
                state = "code"
            elif current in "\r\n":
                raise ValueError(f"{label} contains an unterminated string")
        elif state == "character":
            if current == "\\":
                index += 2
                continue
            if current == "'":
                state = "code"
            elif current in "\r\n":
                raise ValueError(
                    f"{label} contains an unterminated character literal"
                )
        index += 1
    require(
        state in {"code", "line_comment"},
        f"{label} contains an unterminated lexical construct",
    )

def java_string_constant(source, name):
    match = re.search(
        rf"private\s+static\s+final\s+String\s+{name}\s*=\s*(?P<body>.*?);",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing Java string constant {name}")
    tokens = re.findall(r'"(?:\\.|[^"\\])*"', match.group("body"))
    require(tokens, f"Java string constant {name} has no literal bytes")
    return "".join(json.loads(token) for token in tokens)

def enum_values(source, enum_name):
    match = re.search(
        rf"enum\s+{enum_name}\s*\{{(?P<body>.*?)\}}",
        without_comments(source),
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing enum {enum_name}")
    return re.findall(
        r"\b[A-Z][A-Z0-9_]+\b",
        match.group("body").split(";", 1)[0],
    )

pair_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/pricepair"
)
asset_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/assetreturn"
)
pair_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/pricepair"
)
asset_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/assetreturn"
)
pair_files = {
    "BasisPriceField.java", "BasisPriceObservation.java",
    "PricePairAdjustmentEvidence.java",
    "AssetReturnPricePairPolicyVersion.java",
    "AssetReturnPricePairRequest.java",
    "AssetReturnPricePairResolution.java",
    "AssetReturnPricePairSelector.java",
}
asset_files = {
    "AssetReturnPolicyVersion.java", "AssetReturnInput.java",
    "AssetReturnResult.java", "AssetReturnCalculator.java",
}
require(
    {path.name for path in pair_dir.glob("*.java")} == pair_files,
    "Price-pair production package must contain exactly seven files",
)
require(
    {path.name for path in asset_dir.glob("*.java")} == asset_files,
    "Asset-return production package must contain exactly four files",
)
require(
    {path.name for path in pair_test_dir.glob("*.java")}
    == {"AssetReturnPricePairSelectorGoldenTest.java"},
    "Price-pair test package must contain exactly one golden",
)
require(
    {path.name for path in asset_test_dir.glob("*.java")}
    == {"AssetReturnCalculatorGoldenTest.java"},
    "Asset-return test package must contain exactly one golden",
)

pair_sources = {
    name: (pair_dir / name).read_text(encoding="utf-8")
    for name in pair_files
}
asset_sources = {
    name: (asset_dir / name).read_text(encoding="utf-8")
    for name in asset_files
}
all_sources = pair_sources | asset_sources
for name, source in all_sources.items():
    validate_java_lexical_safety(source, name)
expected_structural_hashes = {
    "AssetReturnPricePairPolicyVersion.java":
        "9a4368c44e8754ce2abd1229258fdc234ccaf3cc63a8b3cca682d0957826882f",
    "AssetReturnPricePairRequest.java":
        "ebbe8fff7d50343526b4be4b1b2930c9182d6b1f768009947a8dd225ae303b70",
    "AssetReturnPricePairResolution.java":
        "781bb643425ffe718b18f809d172144ce023cc32a74af01756304d0c463eb857",
    "AssetReturnPricePairSelector.java":
        "c8504aa12b92254666a94755a844ef21a59fb098765feadc6aec680aa10be37f",
    "BasisPriceField.java":
        "900727efbbf6d9ea0075030cccd26e96c95449f2d96322d54baa50542cb6c8e3",
    "BasisPriceObservation.java":
        "70aec4f8862d446a955929571b41ababa5a108895e8de176ee8d3af67a9c5fe6",
    "PricePairAdjustmentEvidence.java":
        "6a12208fb6c4434e6ab5247557504523a1f22ae78a6cc4cabb5b97102d0e2220",
    "AssetReturnPolicyVersion.java":
        "de47170c78824a92bca50d1000aa8ca49e4f507eea391261f659857ddc1f6472",
    "AssetReturnInput.java":
        "0f2fdfdcbd8417e5383e13856ace179f6154ed7230912bc07d72af252172b20d",
    "AssetReturnResult.java":
        "80a07a7a606464ca7993e53a8aa1f90a7463d8678f1b3a0f06e8f825c95bfb5a",
    "AssetReturnCalculator.java":
        "49d6fc9bd42fcf576ca4beeae2c654aa7c9f1613d13176ff56007a0005e4b5e1",
}
forbidden_surface_escape = re.compile(
    r"\.class\b|\bgetClass\s*\(|\bgetDeclared\w*\s*\(|"
    r"\bget(?:Method|Methods|Field|Fields|Constructor|Constructors)\s*\(|"
    r"\bjava\.lang\.reflect\b|\breflection\b|\bassert\b|"
    r"\bsynchronized\b|\.\s*(?:wait|notify|notifyAll)\s*\("
)
explicit_field = re.compile(
    r"\b(?:public|protected|private)\s+"
    r"(?:(?:static|final|volatile|transient)\s+)*[^;{}()]+;"
)
for name, source in all_sources.items():
    structural_source = compact(without_comments_or_strings(source))
    require(
        hashlib.sha256(structural_source.encode("utf-8")).hexdigest()
        == expected_structural_hashes[name],
        f"Executable declaration/field/method surface changed: {name}",
    )
    logic = without_comments_or_strings(source)
    field_declarations = explicit_field.findall(logic)
    require(
        forbidden_surface_escape.search(logic) is None
        and all(
            re.match(r"private\s+static\s+final\s+", declaration)
            is not None
            for declaration in field_declarations
        )
        and re.search(
            r"(?m)^\s*static\s+(?!final\b)[^(){};]+(?:=|;)", logic
        ) is None
        and " volatile " not in f" {compact(logic)} "
        and " transient " not in f" {compact(logic)} ",
        f"Mutable/static state or reflection/monitor escape added: {name}",
    )

pair_policy = pair_sources["AssetReturnPricePairPolicyVersion.java"]
pair_definition = java_string_constant(pair_policy, "CANONICAL_DEFINITION")
pair_hash = "895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887"
endpoint_hash = (
    "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76"
)
require(
    len(pair_definition.encode("utf-8")) == 4655
    and hashlib.sha256(pair_definition.encode("utf-8")).hexdigest()
    == pair_hash
    and java_string_constant(pair_policy, "DEFINITION_HASH") == pair_hash
    and pair_definition.count(endpoint_hash) == 1
    and java_string_constant(
        pair_sources["AssetReturnPricePairRequest.java"],
        "REQUIRED_ENDPOINT_POLICY_HASH",
    ) == endpoint_hash
    and pair_sources[
        "AssetReturnPricePairResolution.java"
    ].count(endpoint_hash) == 1
    and sum(source.count(endpoint_hash) for source in pair_sources.values())
        == 3
    and '"basisObservationFields"' in pair_definition
    and '"adjustmentEvidenceFields"' in pair_definition
    and '"selectedEvidenceRule":'
        '"PRESERVE_COMPLETE_BASIS_OBSERVATION_AND_ADJUSTMENT_EVIDENCE_RECORDS"'
        in pair_definition,
    "Price-pair canonical bytes, complete field identity, length, or hash changed",
)
asset_policy = asset_sources["AssetReturnPolicyVersion.java"]
asset_definition = java_string_constant(asset_policy, "CANONICAL_DEFINITION")
asset_hash = "e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486"
require(
    len(asset_definition.encode("utf-8")) == 1011
    and hashlib.sha256(asset_definition.encode("utf-8")).hexdigest()
    == asset_hash
    and java_string_constant(asset_policy, "DEFINITION_HASH") == asset_hash
    and asset_definition.count(pair_hash) == 1
    and java_string_constant(
        asset_sources["AssetReturnInput.java"],
        "REQUIRED_PRICE_PAIR_POLICY_HASH",
    ) == pair_hash
    and asset_sources["AssetReturnResult.java"].count(pair_hash) == 1
    and sum(source.count(pair_hash) for source in asset_sources.values())
        == 3,
    "Asset-return canonical bytes, required pair hash, length, or hash changed",
)
require(
    enum_values(pair_policy, "AssetReturnPricePairPolicyVersion")
    == ["SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1"],
    "Price-pair policy enum changed",
)
require(
    enum_values(asset_policy, "AssetReturnPolicyVersion")
    == ["SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1"],
    "Asset-return policy enum changed",
)
require(
    enum_values(pair_sources["BasisPriceField.java"], "BasisPriceField")
    == ["SOURCE_RECORDED_BASIS_EVENT_PRICE", "INDICATIVE_OR_OTHER"],
    "Basis-price field vocabulary changed",
)
pair_reasons = enum_values(
    pair_sources["AssetReturnPricePairResolution.java"],
    "UnavailableReason",
)
require(
    pair_reasons == [
        "BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE",
        "BASIS_PRICE_MISSING_AS_OF", "ENDPOINT_PRICE_UNAVAILABLE",
        "BASIS_MISMATCH", "ASSET_MISMATCH", "PRIMARY_VENUE_MISMATCH",
        "CURRENCY_MISMATCH", "PRICE_SOURCE_MISMATCH",
        "OBSERVED_AT_MISMATCH", "PRICE_FIELD_MISMATCH",
        "BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH",
        "BASIS_PRICE_CONTINUITY_UNAVAILABLE", "BASIS_PRICE_AMBIGUOUS",
        "ADJUSTMENT_EVIDENCE_MISSING_AS_OF",
        "ADJUSTMENT_OUTCOME_BASIS_MISMATCH", "ADJUSTMENT_ASSET_MISMATCH",
        "ADJUSTMENT_PRIMARY_VENUE_MISMATCH",
        "ADJUSTMENT_CURRENCY_MISMATCH",
        "BASIS_OBSERVATION_LINK_MISMATCH",
        "ENDPOINT_OBSERVATION_LINK_MISMATCH",
        "ADJUSTMENT_COVERAGE_MISMATCH",
        "ADJUSTMENT_PRICE_BASIS_MISMATCH",
        "ADJUSTMENT_CONTINUITY_UNAVAILABLE",
        "ADJUSTMENT_EVIDENCE_AMBIGUOUS",
    ],
    f"Price-pair unavailable reasons changed: {pair_reasons}",
)
asset_reasons = enum_values(
    asset_sources["AssetReturnResult.java"], "UnavailableReason"
)
require(
    asset_reasons == ["PRICE_PAIR_UNAVAILABLE", "OUTPUT_NOT_REPRESENTABLE"],
    f"Asset-return unavailable reasons changed: {asset_reasons}",
)

compact_pair = {
    name: compact(without_comments_or_strings(source))
    for name, source in pair_sources.items()
}
compact_asset = {
    name: compact(without_comments_or_strings(source))
    for name, source in asset_sources.items()
}
for name, marker in {
    "BasisPriceObservation.java":
        "publicrecordBasisPriceObservation(StringobservationId,"
        "StringproviderEventId,OutcomeBasisbasis,StringassetId,"
        "StringvenueId,Currencycurrency,StringpriceSourceId,"
        "StringpriceSourceRevision,StringprovenanceId,"
        "BasisPriceFieldpriceField,EndpointPriceAdjustmentBasisadjustmentBasis,"
        "CorporateActionContinuitycorporateActionContinuity,InstantobservedAt,"
        "InstantavailableAt,InstantcapturedAt,BigDecimalprice)",
    "PricePairAdjustmentEvidence.java":
        "publicrecordPricePairAdjustmentEvidence(StringadjustmentEvidenceId,"
        "StringproviderEventId,OutcomeBasisbasis,StringassetId,"
        "StringprimaryVenueId,Currencycurrency,StringadjustmentSourceId,"
        "StringadjustmentSourceRevision,StringprovenanceId,"
        "StringbasisObservationId,StringbasisProviderEventId,"
        "StringendpointObservationId,StringendpointProviderEventId,"
        "InstantcoverageStartsAt,InstantcoverageEndsAt,"
        "EndpointPriceAdjustmentBasisadjustmentBasis,"
        "CorporateActionContinuitycorporateActionContinuity,"
        "InstantavailableAt,InstantcapturedAt)",
    "AssetReturnPricePairRequest.java":
        "publicrecordAssetReturnPricePairRequest("
        "AssetReturnPricePairPolicyVersionpolicyVersion,"
        "EndpointPriceResolutionendpointPriceResolution,"
        "List<BasisPriceObservation>basisCandidates,"
        "List<PricePairAdjustmentEvidence>adjustmentCandidates)",
}.items():
    require(marker in compact_pair[name], f"Price-pair record changed: {name}")
pair_result = compact_pair["AssetReturnPricePairResolution.java"]
require(
    "permitsAssetReturnPricePairResolution.Resolved,"
    "AssetReturnPricePairResolution.Unavailable" in pair_result
    and "recordResolutionContext(AssetReturnPricePairPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash,EndpointPriceResolutionendpointPriceResolution)"
    in pair_result
    and "recordResolved(ResolutionContextcontext,"
    "BasisPriceObservationbasisObservation,"
    "PricePairAdjustmentEvidenceadjustmentEvidence)"
    "implementsAssetReturnPricePairResolution" in pair_result
    and "recordUnavailable(ResolutionContextcontext,UnavailableReasonreason,"
    "EndpointPriceResolution.UnavailableReasonendpointReason)"
    "implementsAssetReturnPricePairResolution" in pair_result,
    "Price-pair result/context surface changed",
)
require(
    "publicfinalclassAssetReturnPricePairSelector" in compact_pair[
        "AssetReturnPricePairSelector.java"
    ]
    and "privateAssetReturnPricePairSelector(){}" in compact_pair[
        "AssetReturnPricePairSelector.java"
    ]
    and "publicstaticAssetReturnPricePairResolutionselect("
    "AssetReturnPricePairRequestrequest)" in compact_pair[
        "AssetReturnPricePairSelector.java"
    ],
    "Price-pair selector surface changed",
)
require(
    "publicrecordAssetReturnInput(AssetReturnPolicyVersionpolicyVersion,"
    "AssetReturnPricePairResolutionpricePairResolution)"
    in compact_asset["AssetReturnInput.java"],
    "Asset-return input surface changed",
)
asset_result = compact_asset["AssetReturnResult.java"]
require(
    "permitsAssetReturnResult.Available,AssetReturnResult.Unavailable"
    in asset_result
    and "recordCalculationContext(AssetReturnPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash,"
    "AssetReturnPricePairResolutionpricePairResolution)" in asset_result
    and "recordAvailable(CalculationContextcontext,BigDecimalassetReturn)"
    "implementsAssetReturnResult" in asset_result
    and "recordUnavailable(CalculationContextcontext,UnavailableReasonreason,"
    "AssetReturnPricePairResolution.UnavailableReasonpricePairReason)"
    "implementsAssetReturnResult" in asset_result,
    "Asset-return result/context surface changed",
)
require(
    "publicfinalclassAssetReturnCalculator" in compact_asset[
        "AssetReturnCalculator.java"
    ]
    and "privateAssetReturnCalculator(){}" in compact_asset[
        "AssetReturnCalculator.java"
    ]
    and "publicstaticAssetReturnResultcalculate(AssetReturnInputinput)"
    in compact_asset["AssetReturnCalculator.java"],
    "Asset-return calculator surface changed",
)

expected_imports = {
    "BasisPriceField.java": set(),
    "BasisPriceObservation.java": {
        "java.math.BigDecimal", "java.math.RoundingMode", "java.time.Instant",
        "java.util.Currency", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CorporateActionContinuity",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceAdjustmentBasis",
    },
    "PricePairAdjustmentEvidence.java": {
        "java.time.Instant", "java.util.Currency", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CorporateActionContinuity",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceAdjustmentBasis",
    },
    "AssetReturnPricePairPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "AssetReturnPricePairRequest.java": {
        "java.util.List", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPricePolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
    },
    "AssetReturnPricePairResolution.java": {
        "java.time.Instant", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CorporateActionContinuity",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceAdjustmentBasis",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPricePolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
    },
    "AssetReturnPricePairSelector.java": {
        "java.time.Instant", "java.util.List", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CorporateActionContinuity",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceAdjustmentBasis",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairResolution.ResolutionContext",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairResolution.UnavailableReason",
    },
    "AssetReturnPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "AssetReturnInput.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairResolution",
    },
    "AssetReturnResult.java": {
        "java.math.BigDecimal", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairResolution",
    },
    "AssetReturnCalculator.java": {
        "java.math.BigDecimal", "java.math.RoundingMode", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnResult.Available",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnResult.CalculationContext",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnResult.Unavailable",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnResult.UnavailableReason",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairResolution",
    },
}
forbidden_runtime = re.compile(
    r"\b(?:Clock|Locale|TimeZone|ZoneId|LocalDate|LocalDateTime|"
    r"OffsetDateTime|ZonedDateTime|UUID|Random|SecureRandom|System|Runtime|"
    r"Thread|Process|ProcessBuilder|StackWalker|ProcessHandle|ClassLoader)\b|"
    r"\bClass\s*\.\s*forName\s*\(|\.\s*now\s*\(|Math\s*\.\s*random\s*\(|"
    r"\b(?:getenv|getProperty|setProperty)\s*\(|@(?:Component|Service|Repository|Controller)\b"
)
for name, source in all_sources.items():
    code = without_comments(source)
    logic = without_comments_or_strings(source)
    imports = set(re.findall(r"^import\s+([^;]+);", code, flags=re.MULTILINE))
    require(imports == expected_imports[name], f"Imports changed: {name} {imports}")
    body = re.sub(
        r"^(?:package|import)\s+[^;]+;\s*", "", logic, flags=re.MULTILINE
    )
    qualified_refs = set(re.findall(
        r"\b(?:com|org|net|io|java|javax|jakarta|jdk|sun)"
        r"(?:\.[A-Za-z_$][\w$]*){2,}", body
    ))
    require(
        not qualified_refs
        and forbidden_runtime.search(logic) is None
        and "double" not in logic
        and "float" not in logic
        and "ObjectMapper" not in logic
        and "HttpClient" not in logic
        and "DataSource" not in logic,
        f"Source crosses deterministic/domain boundary: {name} {qualified_refs}",
    )

basis_source = compact_pair["BasisPriceObservation.java"]
adjustment_source = compact_pair["PricePairAdjustmentEvidence.java"]
for component in (
    "observationId", "providerEventId", "assetId", "venueId",
    "priceSourceId", "priceSourceRevision", "provenanceId",
):
    require(
        f"requireCanonicalText({component}," in basis_source,
        f"Basis observation must validate canonical {component}",
    )
for component in (
    "adjustmentEvidenceId", "providerEventId", "assetId", "primaryVenueId",
    "adjustmentSourceId", "adjustmentSourceRevision", "provenanceId",
    "basisObservationId", "basisProviderEventId", "endpointObservationId",
    "endpointProviderEventId",
):
    require(
        f"requireCanonicalText({component}," in adjustment_source,
        f"Adjustment evidence must validate canonical {component}",
    )
require(
    basis_source.count("PersistentInstant.requireMicrosecondPrecision(") == 3
    and adjustment_source.count(
        "PersistentInstant.requireMicrosecondPrecision("
    ) == 4
    and basis_source.count(
        "setScale(STORAGE_SCALE,RoundingMode.UNNECESSARY)"
    ) == 1
    and "privatestaticfinalintSTORAGE_SCALE=12;" in basis_source
    and "privatestaticfinalintSTORAGE_PRECISION=38;" in basis_source
    and "if(availableAt.isBefore(observedAt))" in basis_source
    and "if(capturedAt.isBefore(availableAt))" in basis_source
    and "if(coverageEndsAt.isBefore(coverageStartsAt))" in adjustment_source
    and "if(availableAt.isBefore(coverageEndsAt))" in adjustment_source
    and "if(capturedAt.isBefore(availableAt))" in adjustment_source,
    "Evidence decimal, precision, microsecond, or temporal validation changed",
)

selector_logic = compact_pair["AssetReturnPricePairSelector.java"]
selector_reason_order = [
    "BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE",
    "BASIS_PRICE_MISSING_AS_OF", "ENDPOINT_PRICE_UNAVAILABLE",
    "BASIS_MISMATCH", "ASSET_MISMATCH", "PRIMARY_VENUE_MISMATCH",
    "CURRENCY_MISMATCH", "PRICE_SOURCE_MISMATCH",
    "OBSERVED_AT_MISMATCH", "PRICE_FIELD_MISMATCH",
    "BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH",
    "BASIS_PRICE_CONTINUITY_UNAVAILABLE", "BASIS_PRICE_AMBIGUOUS",
    "ADJUSTMENT_EVIDENCE_MISSING_AS_OF",
    "ADJUSTMENT_OUTCOME_BASIS_MISMATCH", "ADJUSTMENT_ASSET_MISMATCH",
    "ADJUSTMENT_PRIMARY_VENUE_MISMATCH",
    "ADJUSTMENT_CURRENCY_MISMATCH", "BASIS_OBSERVATION_LINK_MISMATCH",
    "ENDPOINT_OBSERVATION_LINK_MISMATCH",
    "ADJUSTMENT_COVERAGE_MISMATCH",
    "ADJUSTMENT_PRICE_BASIS_MISMATCH",
    "ADJUSTMENT_CONTINUITY_UNAVAILABLE",
    "ADJUSTMENT_EVIDENCE_AMBIGUOUS",
]
selector_positions = [
    selector_logic.index(f"UnavailableReason.{reason}")
    for reason in selector_reason_order
]
require(
    selector_positions == sorted(selector_positions)
    and selector_logic.count(".filter(candidate->known(") == 2
    and selector_logic.count(".stream().anyMatch(") == 18
    and selector_logic.count(".size()>1") == 2
    and selector_logic.count(".getFirst()") == 2
    and "return!availableAt.isAfter(evaluationAsOf)"
        "&&!capturedAt.isAfter(evaluationAsOf);" in selector_logic
    and "candidate.observedAt().equals(basis.eventTime())" in selector_logic
    and "candidate.priceField()!=BasisPriceField."
        "SOURCE_RECORDED_BASIS_EVENT_PRICE" in selector_logic
    and "candidate.basisObservationId().equals("
        "basisObservation.observationId())" in selector_logic
    and "candidate.basisProviderEventId().equals("
        "basisObservation.providerEventId())" in selector_logic
    and "candidate.endpointObservationId().equals("
        "endpointObservation.observationId())" in selector_logic
    and "candidate.endpointProviderEventId().equals("
        "endpointObservation.providerEventId())" in selector_logic
    and "candidate.coverageStartsAt().equals("
        "basisObservation.observedAt())" in selector_logic
    and "candidate.coverageEndsAt().equals("
        "endpointObservation.observedAt())" in selector_logic
    and ".distinct(" not in selector_logic
    and ".sorted(" not in selector_logic
    and ".sort(" not in selector_logic,
    "Price-pair PIT filtering, precedence, links, coverage, or cardinality changed",
)

calculator_logic = compact_asset["AssetReturnCalculator.java"]
require(
    calculator_logic.count(".subtract(") == 1
    and calculator_logic.count(".divide(") == 1
    and "BigDecimalnumerator=endpoint.subtract(basis);" in calculator_logic
    and "numerator.divide(basis,OUTPUT_SCALE,RoundingMode.HALF_EVEN)"
        in calculator_logic
    and "privatestaticfinalintOUTPUT_SCALE=12;" in calculator_logic
    and "privatestaticfinalintOUTPUT_PRECISION=38;" in calculator_logic
    and "assetReturn.precision()>OUTPUT_PRECISION" in calculator_logic
    and "UnavailableReason.PRICE_PAIR_UNAVAILABLE" in calculator_logic
    and "UnavailableReason.OUTPUT_NOT_REPRESENTABLE" in calculator_logic
    and ".abs(" not in calculator_logic
    and ".multiply(" not in calculator_logic
    and ".setScale(" not in calculator_logic
    and "movePointRight(" not in calculator_logic
    and "doubleValue(" not in calculator_logic
    and "floatValue(" not in calculator_logic,
    "Signed basis-denominator formula, one rounding, or overflow changed",
)
require(
    "assetReturn.compareTo(BigDecimal.ONE.negate())<0" in asset_result
    and "assetReturn.scale()!=12" in asset_result
    and "assetReturn.precision()>38" in asset_result,
    "Asset-return exact -1/scale/precision constructor boundary changed",
)

api_main_dir = Path("apps/api/src/main/java")
new_paths = {
    (pair_dir / name).resolve() for name in pair_files
} | {
    (asset_dir / name).resolve() for name in asset_files
}
new_markers = tuple(
    name.removesuffix(".java") for name in pair_files | asset_files
)
directional_win_orchestration_consumers = {
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationPolicyVersion.java").resolve(): (
         set(), set(),
    ),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationRequest.java").resolve(): (
         {"AssetReturnPricePairResolution"},
         {"AssetReturnPolicyVersion", "AssetReturnResult"},
    ),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationResolution.java").resolve(): (
         set(), {"AssetReturnResult"},
    ),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrator.java").resolve(): (
         set(), {"AssetReturnResult"},
    ),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinreadiness/"
     "DirectionalWinReadinessResolver.java").resolve(): (
         {"AssetReturnPricePairResolution"}, {"AssetReturnResult"},
    ),
}
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in new_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    if other_path.resolve() in directional_win_orchestration_consumers:
        other_logic = without_comments_or_strings(other_source)
        actual_pair_references = {
            marker
            for marker in {
                name.removesuffix(".java") for name in pair_files
            }
            if re.search(rf"\b{re.escape(marker)}\b", other_logic)
        }
        actual_asset_references = {
            marker
            for marker in {
                name.removesuffix(".java") for name in asset_files
            }
            if re.search(rf"\b{re.escape(marker)}\b", other_logic)
        }
        expected_pair, expected_asset = (
            directional_win_orchestration_consumers[
                other_path.resolve()
            ]
        )
        require(
            actual_pair_references == expected_pair
            and actual_asset_references == expected_asset
            and "AssetReturnCalculator" not in other_logic
            and "AssetReturnInput" not in other_logic
            and "AssetReturnPricePairSelector" not in other_logic
            and "AssetReturnPricePairRequest" not in other_logic
            and "BasisPriceObservation" not in other_logic
            and "PricePairAdjustmentEvidence" not in other_logic,
            "Directional-win orchestration may consume only exact "
            f"supplied pair/return leaves: {other_path}",
        )
        continue
    require(
        re.search(r"\\u+[0-9a-fA-F]{4}", other_source) is None
        and "domain.outcome.pricepair" not in other_source
        and "domain.outcome.assetreturn" not in other_source
        and not any(marker in other_source for marker in new_markers),
        f"Price-pair/asset-return leaf must not be wired into production: {other_path}",
    )

pair_golden = (
    pair_test_dir / "AssetReturnPricePairSelectorGoldenTest.java"
).read_text(encoding="utf-8")
asset_golden = (
    asset_test_dir / "AssetReturnCalculatorGoldenTest.java"
).read_text(encoding="utf-8")
validate_java_lexical_safety(pair_golden, "Price-pair golden")
validate_java_lexical_safety(asset_golden, "Asset-return golden")
pair_golden_code = without_comments(pair_golden)
asset_golden_code = without_comments(asset_golden)
pair_golden_executable = without_comments_or_strings(pair_golden)
asset_golden_executable = without_comments_or_strings(asset_golden)
pair_golden_logic = compact(pair_golden_executable)
asset_golden_logic = compact(asset_golden_executable)
require(
    hashlib.sha256(pair_golden_logic.encode("utf-8")).hexdigest()
    == "322a291d47cb8efc70609ce121be30ce23fb87b4ef21239639f5f05ce7e0c2e7"
    and hashlib.sha256(asset_golden_logic.encode("utf-8")).hexdigest()
    == "d50c25fac98a1c87ba7d7e89560a8632dd8dfbb98e237b3f547433fdc8635b28",
    "Golden executable structure changed outside the locked V1 test surface",
)
compact_pair_golden = compact(pair_golden_code)
compact_asset_golden = compact(asset_golden_code)
pair_methods = set(re.findall(
    r"\b(?:void|Stream<[^>]+>|Stream<Arguments>)\s+"
    r"([A-Za-z_$][\w$]*)\s*\(", pair_golden_code
))
asset_methods = set(re.findall(
    r"\b(?:void|Stream<[^>]+>|Stream<Arguments>)\s+"
    r"([A-Za-z_$][\w$]*)\s*\(", asset_golden_code
))
required_pair_methods = {
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "resolvesOneExactKnownPairAtInclusivePitBoundariesAndPreservesEvidence",
    "basisCandidatesArePitFilteredBeforeAllIdentityAndCardinalityChecks",
    "adjustmentCandidatesArePitFilteredBeforeAllIdentityAndCardinalityChecks",
    "composesEveryNestedEndpointReasonForBasisMissingAndEndpointOnlyStates",
    "matchesCompleteOriginalAndCorrectionBasisIdentityWithoutEventTimeShortcut",
    "basisMismatchVectors", "reportsEveryBasisMismatchWithoutFallback",
    "basisPrecedenceVectors",
    "appliesEveryBasisMismatchGateBeforeTheNextGateRegardlessOfInputOrder",
    "basisMismatchGatesPrecedeAmbiguityAndKnownDuplicatesAreNeverDeduplicated",
    "adjustmentMismatchVectors",
    "reportsEveryAdjustmentMismatchAndExactObservationLinks",
    "adjustmentPrecedenceVectors",
    "appliesEveryAdjustmentMismatchGateBeforeTheNextGateRegardlessOfInputOrder",
    "adjustmentMismatchGatesPrecedeAmbiguityAndKnownDuplicatesAreNeverDeduplicated",
    "directResultsEnforceLocalEvidenceAndNestedReasonConsistencyOnly",
    "requestDefensivelyCopiesBothCandidateListsAndRejectsNulls",
    "resultConstructorsRejectEveryNullPublicComponent",
    "basisObservationRejectsEveryNullBlankAndUntrimmedCanonicalText",
    "adjustmentEvidenceRejectsEveryNullBlankAndUntrimmedCanonicalText",
    "basisObservationRejectsEveryNullableTimeOrderAndDecimalMutation",
    "adjustmentEvidenceRejectsEveryNullableTimeAndOrderMutation",
    "acceptsExactMaximumNumericBasisWithoutChangingItsRepresentation",
    "closesPublicRecordsEnumsAndReplayAgainstJvmDefaultsAndInputOrder",
}
pair_parameterized_methods = {
    "composesEveryNestedEndpointReasonForBasisMissingAndEndpointOnlyStates":
        "endpointReasons",
    "reportsEveryBasisMismatchWithoutFallback": "basisMismatchVectors",
    "appliesEveryBasisMismatchGateBeforeTheNextGateRegardlessOfInputOrder":
        "basisPrecedenceVectors",
    "reportsEveryAdjustmentMismatchAndExactObservationLinks":
        "adjustmentMismatchVectors",
    "appliesEveryAdjustmentMismatchGateBeforeTheNextGateRegardlessOfInputOrder":
        "adjustmentPrecedenceVectors",
    "basisObservationRejectsEveryNullBlankAndUntrimmedCanonicalText":
        "basisTextMutations",
    "adjustmentEvidenceRejectsEveryNullBlankAndUntrimmedCanonicalText":
        "adjustmentTextMutations",
    "basisObservationRejectsEveryNullableTimeOrderAndDecimalMutation":
        "basisConstructorMutations",
    "adjustmentEvidenceRejectsEveryNullableTimeAndOrderMutation":
        "adjustmentConstructorMutations",
}
pair_test_methods = {
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "resolvesOneExactKnownPairAtInclusivePitBoundariesAndPreservesEvidence",
    "basisCandidatesArePitFilteredBeforeAllIdentityAndCardinalityChecks",
    "adjustmentCandidatesArePitFilteredBeforeAllIdentityAndCardinalityChecks",
    "matchesCompleteOriginalAndCorrectionBasisIdentityWithoutEventTimeShortcut",
    "basisMismatchGatesPrecedeAmbiguityAndKnownDuplicatesAreNeverDeduplicated",
    "adjustmentMismatchGatesPrecedeAmbiguityAndKnownDuplicatesAreNeverDeduplicated",
    "directResultsEnforceLocalEvidenceAndNestedReasonConsistencyOnly",
    "requestDefensivelyCopiesBothCandidateListsAndRejectsNulls",
    "resultConstructorsRejectEveryNullPublicComponent",
    "acceptsExactMaximumNumericBasisWithoutChangingItsRepresentation",
    "closesPublicRecordsEnumsAndReplayAgainstJvmDefaultsAndInputOrder",
}
require(
    required_pair_methods <= pair_methods
    and len(re.findall(
        r"@(?:[\w$]+\.)*Test\b", pair_golden_executable
    )) == 12
    and len(re.findall(
        r"@(?:[\w$]+\.)*ParameterizedTest\b", pair_golden_executable
    )) == 9
    and len(re.findall(
        r"@(?:[\w$]+\.)*MethodSource\b", pair_golden_executable
    )) == 9
    and all(
        re.search(
            r"@(?:[\w$]+\.)*ParameterizedTest\s*\([^)]*\)\s*"
            rf"@(?:[\w$]+\.)*MethodSource\s*\(\s*\"{re.escape(source)}\"\s*\)\s*"
            rf"void\s+{re.escape(method)}\s*\(",
            pair_golden_code,
            flags=re.DOTALL,
        ) is not None
        for method, source in pair_parameterized_methods.items()
    )
    and all(
        f"@Testvoid{method}(" in pair_golden_logic
        for method in pair_test_methods
    )
    and pair_golden_logic.count("isEqualTo(baseline)") == 4
    and pair_golden_logic.count("isEqualTo(empty)") == 6
    and pair_golden_logic.count(
        "List.of(fixture.basis(),fixture.basis())"
    ) == 1
    and pair_golden_logic.count(
        "List.of(fixture.adjustment(),fixture.adjustment())"
    ) == 1
    and "AS_OF.plusNanos(1_000)" in pair_golden_code
    and "assertRecordComponents(BasisPriceObservation.class" in pair_golden_code
    and "assertRecordComponents(PricePairAdjustmentEvidence.class"
        in pair_golden_code
    and all(f"UnavailableReason.{reason}" in pair_golden_code
            for reason in pair_reasons)
    and "Locale.setDefault" in pair_golden_code
    and "TimeZone.setDefault" in pair_golden_code
    and "finally" in pair_golden_logic,
    "Price-pair golden must lock PIT equality, full evidence, precedence, ambiguity, and replay",
)
required_asset_methods = {
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "primaryFormulaVectors",
    "calculatesSignedPositiveNegativeAndZeroReturnsWithBasisDenominator",
    "scaleEquivalentPricesProduceEqualExactScaleTwelveResults",
    "halfEvenTieVectors",
    "roundsPositiveAndNegativeExactTiesHalfEvenWithOneDivision",
    "allowsRoundedNegativeOneForPositiveInputsWhoseExactReturnExceedsNegativeOne",
    "acceptsExactPrecision38OutputAndReturnsUnavailableForRoundedPrecision39",
    "preservesEveryExactPricePairUnavailableReason",
    "directResultConstructorsEnforceLocalPolicyPairAndReasonConsistency",
    "rejectsEveryNullInputContextAndResultComponent",
    "closesPublicSurfaceReasonOrderAndReplayAgainstJvmDefaults",
}
asset_parameterized_methods = {
    "calculatesSignedPositiveNegativeAndZeroReturnsWithBasisDenominator":
        "primaryFormulaVectors",
    "roundsPositiveAndNegativeExactTiesHalfEvenWithOneDivision":
        "halfEvenTieVectors",
    "preservesEveryExactPricePairUnavailableReason": "pairReasons",
}
asset_test_methods = {
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "scaleEquivalentPricesProduceEqualExactScaleTwelveResults",
    "allowsRoundedNegativeOneForPositiveInputsWhoseExactReturnExceedsNegativeOne",
    "acceptsExactPrecision38OutputAndReturnsUnavailableForRoundedPrecision39",
    "directResultConstructorsEnforceLocalPolicyPairAndReasonConsistency",
    "rejectsEveryNullInputContextAndResultComponent",
    "closesPublicSurfaceReasonOrderAndReplayAgainstJvmDefaults",
}
require(
    required_asset_methods <= asset_methods
    and len(re.findall(
        r"@(?:[\w$]+\.)*Test\b", asset_golden_executable
    )) == 7
    and len(re.findall(
        r"@(?:[\w$]+\.)*ParameterizedTest\b", asset_golden_executable
    )) == 3
    and len(re.findall(
        r"@(?:[\w$]+\.)*MethodSource\b", asset_golden_executable
    )) == 3
    and all(
        re.search(
            r"@(?:[\w$]+\.)*ParameterizedTest\s*\([^)]*\)\s*"
            rf"@(?:[\w$]+\.)*MethodSource\s*\(\s*\"{re.escape(source)}\"\s*\)\s*"
            rf"void\s+{re.escape(method)}\s*\(",
            asset_golden_code,
            flags=re.DOTALL,
        ) is not None
        for method, source in asset_parameterized_methods.items()
    )
    and all(
        f"@Testvoid{method}(" in asset_golden_logic
        for method in asset_test_methods
    )
    and 'Arguments.of("positive","100","120","0.200000000000")'
        in compact_asset_golden
    and 'Arguments.of("negative","120","100","-0.166666666667")'
        in compact_asset_golden
    and 'Arguments.of("asymmetric-forward","2","3","0.500000000000")'
        in compact_asset_golden
    and 'Arguments.of("asymmetric-reverse","3","2","-0.333333333333")'
        in compact_asset_golden
    and 'Arguments.of("positive-odd-to-even","2.000000000003",'
        '"0.000000000002")' in compact_asset_golden
    and 'Arguments.of("negative-odd-to-even","1.999999999997",'
        '"-0.000000000002")' in compact_asset_golden
    and 'newBigDecimal("-1.000000000000")' in compact_asset_golden
    and '"99999999999999999999999998.999999999999"'
        in compact_asset_golden
    and "maximumOutput.assetReturn().precision()).isEqualTo(38)"
        in asset_golden_logic
    and "Stream.of(AssetReturnPricePairResolution.UnavailableReason.values())"
        in asset_golden_logic
    and all(f"UnavailableReason.{reason}" in asset_golden_code
            for reason in asset_reasons)
    and "Locale.setDefault" in asset_golden_code
    and "TimeZone.setDefault" in asset_golden_code
    and "finally" in asset_golden_logic,
    "Asset-return golden must lock sign, denominator, ties, -1, overflow, reasons, and replay",
)
for label, golden_logic, golden_executable in (
    ("Price-pair", pair_golden_logic, pair_golden_executable),
    ("Asset-return", asset_golden_logic, asset_golden_executable),
):
    expected_control_counts = {
        "Price-pair": {
            "if": 0, "for": 4, "while": 0, "do": 0,
            "switch": 2, "try": 1, "catch": 0, "finally": 1,
            "break": 0, "continue": 0,
        },
        "Asset-return": {
            "if": 1, "for": 1, "while": 0, "do": 0,
            "switch": 0, "try": 1, "catch": 0, "finally": 1,
            "break": 0, "continue": 0,
        },
    }[label]
    require(
        re.search(
            r"@(?:[\w$]+\.)*(?:Disabled|Enabled\w*|Tag)\b",
            golden_executable,
        )
        is None
        and "allowZeroInvocations" not in golden_logic
        and not any(
            token in golden_logic
            for token in (
                "Assumptions", "assumeTrue", "assumeFalse",
                "assumingThat", "TestAbortedException",
            )
        )
        and re.search(r"\breturn\s*;", golden_logic) is None
        and all(
            len(re.findall(rf"\b{keyword}\b", golden_executable)) == count
            for keyword, count in expected_control_counts.items()
        ),
        f"{label} golden must not disable, condition, assume, early-return, or swallow tests",
    )
require(
    "ObjectMapper" not in pair_golden
    and "ClassPathResource" not in pair_golden
    and "ObjectMapper" not in asset_golden
    and "ClassPathResource" not in asset_golden,
    "Price-pair/asset-return goldens must remain source-local",
)

expected_schemas = {
    "analyst-call-revision.schema.json", "analyst-call.schema.json",
    "call-context.schema.json", "call-outcome.schema.json",
    "event-context.schema.json", "macro-observation.schema.json",
    "macro-snapshot.schema.json", "market-board.schema.json",
    "market-map.schema.json", "market-snapshot.schema.json",
    "market-treemap.schema.json", "scoring-methodology.schema.json",
    "source-document.schema.json", "source-reference.schema.json",
}
expected_fixtures = {
    "analyst-call-revisions.json", "analyst-calls.json",
    "call-contexts.json", "call-outcomes.json", "manifest.json",
    "market-board.json", "market-map-nasdaq100.json", "market-map.json",
    "market-snapshots.json", "market-treemap-nasdaq100.json",
    "market-treemap-sp500.json", "master-data.json", "timeline-nvda.json",
}
require(
    {path.name for path in Path("schemas").glob("*.json")}
    == expected_schemas
    and {path.name for path in Path("fixtures/v1").glob("*.json")}
    == expected_fixtures,
    "Price-pair/asset-return slice must preserve schemas and fixtures",
)
manifest = json.loads(
    Path("fixtures/v1/manifest.json").read_text(encoding="utf-8")
)
require(
    [entry["path"] for entry in manifest["files"]] == [
        "master-data.json", "analyst-calls.json",
        "analyst-call-revisions.json", "call-outcomes.json",
        "call-contexts.json", "market-snapshots.json", "market-map.json",
        "market-map-nasdaq100.json", "market-treemap-sp500.json",
        "market-treemap-nasdaq100.json", "timeline-nvda.json",
        "market-board.json",
    ],
    "Price-pair/asset-return slice must preserve fixture manifest order",
)
outcomes = json.loads(
    Path("fixtures/v1/call-outcomes.json").read_text(encoding="utf-8")
)
metrics = (
    "assetReturn", "benchmarkReturn", "sectorReturn", "alpha",
    "sectorAlpha", "mfe", "mae", "targetHit", "directionalWin",
    "targetError",
)
require(
    len(outcomes["methodologies"]) == 2
    and all(item["status"] == "MODEL_ONLY"
            for item in outcomes["methodologies"])
    and len(outcomes["outcomes"]) == 4
    and all(item[field] is None
            for item in outcomes["outcomes"] for field in metrics),
    "Price-pair/asset-return leaves must not publish or activate outcomes",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    }
    and {path.name for path in Path(
        "apps/api/src/main/resources/db/migration"
    ).glob("*.sql")} == {
        "V1__baseline.sql", "V2__analyst_calls.sql",
        "V3__analyst_call_revisions.sql", "V4__call_outcomes.sql",
        "V5__call_contexts.sql",
        "V6__sec_filing_catalog_captures.sql",
        "V7__sec_historical_filing_segment_captures.sql",
        "V8__sec_filing_history_collection_manifests.sql",
        "V9__sec_filing_collection_attempts.sql",
    },
    "Price-pair/asset-return slice must preserve OpenAPI and Flyway",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource_source = resource_path.read_text(encoding="utf-8")
    require(
        not any(marker in resource_source for marker in new_markers),
        f"Price-pair/asset-return leaves must not add JSON goldens: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "domain.outcome.pricepair" not in web_source
            and "domain.outcome.assetreturn" not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Price-pair/asset-return leaves must not expand web: {web_path}",
        )

comparative_adr_path = Path(
    "decisions/ADR-026-point-in-time-comparative-reference-return-"
    "foundation.md"
)
comparative_marker = (
    "ADR-026 locks benchmark and sector returns to explicit point-in-time "
    "reference assignments."
)
comparative_doc_paths = (
    comparative_adr_path,
    Path("README.md"),
    Path("quality/P3_ACCEPTANCE.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
comparative_docs = {}
for doc_path in comparative_doc_paths:
    require(doc_path.is_file(), f"Missing ADR-026 foundation doc: {doc_path}")
    doc_source = doc_path.read_text(encoding="utf-8")
    comparative_docs[doc_path] = doc_source
    require(
        doc_source.count(comparative_marker) == 1
        and "ADR-026" in doc_source,
        f"ADR-026 foundation marker must occur exactly once: {doc_path}",
    )

comparative_adr = comparative_docs[comparative_adr_path]
require(
    comparative_adr.startswith(
        "# ADR-026 — Point-in-Time Comparative Reference-Return Foundation\n"
    )
    and "- Status: Accepted" in comparative_adr
    and "- Date: 2026-08-24" in comparative_adr,
    "ADR-026 title, accepted status, or date changed",
)
comparative_words = re.sub(
    r"[^a-z0-9]+", " ", comparative_adr.lower()
)
required_comparative_terms = (
    ("decision only",),
    (
        "asset spx", "explicit", "assignment", "assettype equity",
        "iso 3166 1 alpha 2 us", "iso 4217 usd", "assettype index",
    ),
    ("satisfies only adr 025", "does not relax"),
    ("ticker", "infer"),
    ("non applicability", "evidence unavailability"),
    ("point in time", "basis event", "frozen"),
    (
        "wall street receipts", "sector taxonomy", "provider",
        "mapping", "membership",
    ),
    ("treemap", "gics", "icb"),
    ("provider published", "price index", "etf", "basket"),
    ("price return", "total return"),
    ("same currency", "no fx"),
    ("shared utc interval", "calendar", "venue", "source"),
    ("source recorded", "reference level", "basis event", "endpoint"),
    ("prior", "nearest", "interpolation"),
    ("divisor continuity",),
    (
        "assetreturnresult", "assetreturnpricepairresolution",
        "corporateactioncontinuity", "endpointpriceadjustmentbasis",
    ),
    (
        "separate semantic types", "own inputs and results", "must use",
        "exactly one subtraction", "exactly one scale 12 half even division",
        "no intermediate or second rounding",
    ),
    ("demo", "outcome", "null"),
    ("api key", "network"),
)
for required_terms in required_comparative_terms:
    require(
        all(term in comparative_words for term in required_terms),
        "Missing ADR-026 decision-only foundation terms: "
        f"{required_terms}",
    )
require(
    re.search(r"\b[a-f0-9]{64}\b", comparative_adr) is None
    and "## Canonical policy definition" not in comparative_adr
    and '"policyVersion"' not in comparative_adr
    and "DEFINITION_HASH" not in comparative_adr,
    "Decision-only ADR-026 must not create canonical policy bytes or a digest",
)

normalized_docs = {
    path: re.sub(r"\s+", " ", source).lower()
    for path, source in comparative_docs.items()
}
stale_blockers = {
    Path("README.md"): (
        "benchmark and sector return work must next pause for explicit "
        "product approval",
    ),
    Path("quality/P3_ACCEPTANCE.md"): (
        "before benchmark or sector return contracts are designed, obtain "
        "explicit product approval",
    ),
    Path("IMPLEMENTATION_LOG.md"): (
        "before benchmark or sector return contracts are implemented, request "
        "and receive explicit product approval",
        "benchmark and sector return work still requires explicit product "
        "approval",
    ),
}
for doc_path, stale_phrases in stale_blockers.items():
    require(
        not any(
            phrase in normalized_docs[doc_path]
            for phrase in stale_phrases
        ),
        f"Stale pre-ADR-026 approval blocker remains: {doc_path}",
    )

premature_path_pattern = re.compile(
    r"(?:benchmark|sector|comparativereference)"
    r"[^/]*(?:assignment|pricepair|referencelevelpair|return)"
)
premature_type_pattern = re.compile(
    r"\b(?:Benchmark|Sector)(?:(?:Reference)?"
    r"(?:Assignment|PricePair|Return)|ReferenceLevelPair|"
    r"ReferenceIndex|ReferenceLevelObservation|"
    r"IndexDivisorContinuity)\w*\b|"
    r"\bComparativeReference(?:Assignment|PricePair|Return)\w*\b"
)
benchmark_assignment_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkassignment"
)
benchmark_assignment_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkassignment"
)
sector_assignment_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorassignment"
)
sector_assignment_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorassignment"
)
benchmark_reference_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreferencepair"
)
benchmark_reference_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreferencepair"
)
sector_reference_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreferencepair"
)
sector_reference_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreferencepair"
)
benchmark_return_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
benchmark_return_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
sector_return_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn"
)
sector_return_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn"
)
benchmark_readiness_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturnreadiness"
)
benchmark_readiness_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturnreadiness"
)
sector_readiness_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturnreadiness"
)
sector_readiness_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturnreadiness"
)
approved_benchmark_main_files = {
    "BenchmarkAssignmentPolicyVersion.java",
    "BenchmarkAssetClassificationEvidence.java",
    "BenchmarkAssignmentEvidence.java",
    "BenchmarkAssignmentRequest.java",
    "BenchmarkAssignmentResolution.java",
    "BenchmarkAssignmentSelector.java",
}
approved_benchmark_test_files = {
    "BenchmarkAssignmentSelectorGoldenTest.java",
}
approved_benchmark_main_paths = {
    (benchmark_assignment_main_dir / name).resolve()
    for name in approved_benchmark_main_files
}
approved_benchmark_test_paths = {
    (benchmark_assignment_test_dir / name).resolve()
    for name in approved_benchmark_test_files
}
approved_benchmark_paths = (
    approved_benchmark_main_paths | approved_benchmark_test_paths
)
approved_sector_main_files = {
    "SectorAssignmentPolicyVersion.java",
    "SectorAssetClassificationEvidence.java",
    "SectorMembershipEvidence.java",
    "SectorMappingEvidence.java",
    "SectorAssignmentRequest.java",
    "SectorAssignmentResolution.java",
    "SectorAssignmentSelector.java",
}
approved_sector_test_files = {
    "SectorAssignmentSelectorGoldenTest.java",
}
approved_sector_main_paths = {
    (sector_assignment_main_dir / name).resolve()
    for name in approved_sector_main_files
}
approved_sector_test_paths = {
    (sector_assignment_test_dir / name).resolve()
    for name in approved_sector_test_files
}
approved_sector_paths = (
    approved_sector_main_paths | approved_sector_test_paths
)
approved_benchmark_reference_main_files = {
    "BenchmarkReferenceLevelPairPolicyVersion.java",
    "BenchmarkReferenceIndexEvidence.java",
    "BenchmarkReferenceLevelObservation.java",
    "BenchmarkIndexDivisorContinuityEvidence.java",
    "BenchmarkReferenceLevelPairRequest.java",
    "BenchmarkReferenceLevelPairResolution.java",
    "BenchmarkReferenceLevelPairSelector.java",
}
approved_benchmark_reference_test_files = {
    "BenchmarkReferenceLevelPairSelectorGoldenTest.java",
}
approved_benchmark_reference_main_paths = {
    (benchmark_reference_main_dir / name).resolve()
    for name in approved_benchmark_reference_main_files
}
approved_benchmark_reference_test_paths = {
    (benchmark_reference_test_dir / name).resolve()
    for name in approved_benchmark_reference_test_files
}
approved_sector_reference_main_files = {
    "SectorReferenceLevelPairPolicyVersion.java",
    "SectorReferenceIndexEvidence.java",
    "SectorReferenceLevelObservation.java",
    "SectorIndexDivisorContinuityEvidence.java",
    "SectorReferenceLevelPairRequest.java",
    "SectorReferenceLevelPairResolution.java",
    "SectorReferenceLevelPairSelector.java",
}
approved_sector_reference_test_files = {
    "SectorReferenceLevelPairSelectorGoldenTest.java",
}
approved_sector_reference_main_paths = {
    (sector_reference_main_dir / name).resolve()
    for name in approved_sector_reference_main_files
}
approved_sector_reference_test_paths = {
    (sector_reference_test_dir / name).resolve()
    for name in approved_sector_reference_test_files
}
approved_benchmark_return_main_files = {
    "BenchmarkReturnPolicyVersion.java",
    "BenchmarkReturnInput.java",
    "BenchmarkReturnResult.java",
    "BenchmarkReturnCalculator.java",
}
approved_benchmark_return_test_files = {
    "BenchmarkReturnCalculatorGoldenTest.java",
}
approved_benchmark_return_main_paths = {
    (benchmark_return_main_dir / name).resolve()
    for name in approved_benchmark_return_main_files
}
approved_benchmark_return_test_paths = {
    (benchmark_return_test_dir / name).resolve()
    for name in approved_benchmark_return_test_files
}
require(
    {path.name for path in benchmark_return_main_dir.glob("*.java")}
    == approved_benchmark_return_main_files
    and {path.name for path in benchmark_return_test_dir.glob("*.java")}
    == approved_benchmark_return_test_files,
    "ADR-026 benchmark-return consumer must remain the exact ADR-031 slice",
)
approved_sector_return_main_files = {
    "SectorReturnPolicyVersion.java",
    "SectorReturnInput.java",
    "SectorReturnResult.java",
    "SectorReturnCalculator.java",
}
approved_sector_return_test_files = {
    "SectorReturnCalculatorGoldenTest.java",
}
approved_sector_return_main_paths = {
    (sector_return_main_dir / name).resolve()
    for name in approved_sector_return_main_files
}
approved_sector_return_test_paths = {
    (sector_return_test_dir / name).resolve()
    for name in approved_sector_return_test_files
}
require(
    {path.name for path in sector_return_main_dir.glob("*.java")}
    == approved_sector_return_main_files
    and {path.name for path in sector_return_test_dir.glob("*.java")}
    == approved_sector_return_test_files,
    "ADR-026 sector-return consumer must remain the exact ADR-032 slice",
)
approved_benchmark_readiness_main_files = {
    "BenchmarkReturnReadinessPolicyVersion.java",
    "BenchmarkReturnReadinessRequest.java",
    "BenchmarkReturnReadinessResolution.java",
    "BenchmarkReturnReadinessResolver.java",
}
approved_benchmark_readiness_test_files = {
    "BenchmarkReturnReadinessResolverGoldenTest.java",
}
approved_benchmark_readiness_main_paths = {
    (benchmark_readiness_main_dir / name).resolve()
    for name in approved_benchmark_readiness_main_files
}
approved_benchmark_readiness_test_paths = {
    (benchmark_readiness_test_dir / name).resolve()
    for name in approved_benchmark_readiness_test_files
}
approved_sector_readiness_main_files = {
    "SectorReturnReadinessPolicyVersion.java",
    "SectorReturnReadinessRequest.java",
    "SectorReturnReadinessResolution.java",
    "SectorReturnReadinessResolver.java",
}
approved_sector_readiness_test_files = {
    "SectorReturnReadinessResolverGoldenTest.java",
}
approved_sector_readiness_main_paths = {
    (sector_readiness_main_dir / name).resolve()
    for name in approved_sector_readiness_main_files
}
approved_sector_readiness_test_paths = {
    (sector_readiness_test_dir / name).resolve()
    for name in approved_sector_readiness_test_files
}
require(
    {path.name for path in benchmark_readiness_main_dir.glob("*.java")}
        == approved_benchmark_readiness_main_files
    and {path.name for path in benchmark_readiness_test_dir.glob("*.java")}
        == approved_benchmark_readiness_test_files
    and {path.name for path in sector_readiness_main_dir.glob("*.java")}
        == approved_sector_readiness_main_files
    and {path.name for path in sector_readiness_test_dir.glob("*.java")}
        == approved_sector_readiness_test_files,
    "ADR-026 readiness consumers must remain the exact independent ADR-033 slices",
)
approved_comparative_paths = (
    approved_benchmark_paths | approved_sector_paths
    | approved_benchmark_reference_main_paths
    | approved_benchmark_reference_test_paths
    | approved_sector_reference_main_paths
    | approved_sector_reference_test_paths
    | approved_benchmark_return_main_paths
    | approved_benchmark_return_test_paths
    | approved_sector_return_main_paths
    | approved_sector_return_test_paths
    | approved_benchmark_readiness_main_paths
    | approved_benchmark_readiness_test_paths
    | approved_sector_readiness_main_paths
    | approved_sector_readiness_test_paths
)
for java_root in (
    Path("apps/api/src/main/java"),
    Path("apps/api/src/test/java"),
):
    for java_path in java_root.rglob("*.java"):
        if java_path.resolve() in approved_comparative_paths:
            continue
        normalized_path = re.sub(
            r"[^a-z0-9]", "", java_path.as_posix().lower()
        )
        java_source = java_path.read_text(encoding="utf-8")
        java_logic = without_comments_or_strings(java_source)
        require(
            premature_path_pattern.search(normalized_path) is None,
            "ADR-026 must not add a premature comparative runtime path: "
            f"{java_path}",
        )
        require(
            premature_type_pattern.search(java_logic) is None,
            "ADR-026 must not add a premature benchmark/sector runtime type: "
            f"{java_path}",
        )

comparative_web_paths = [
    path
    for root in (Path("apps/web/src"), Path("apps/web/e2e"))
    for path in root.rglob("*")
    if path.is_file()
] + [
    path for path in Path("apps/web").glob("*")
    if path.is_file()
]
for web_path in comparative_web_paths:
    normalized_path = re.sub(
        r"[^a-z0-9]", "", web_path.as_posix().lower()
    )
    require(
        premature_path_pattern.search(normalized_path) is None,
        "ADR-026 must not add a premature comparative web path: "
        f"{web_path}",
    )
    if web_path.suffix.lower() in {
        ".cjs", ".js", ".jsx", ".mjs", ".ts", ".tsx",
    }:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            premature_type_pattern.search(web_source) is None,
            "ADR-026 must not add a premature benchmark/sector web type: "
            f"{web_path}",
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
comparative_foundation_protected_paths = [
    path
    for path in protected_paths
    if path.resolve() not in (
        approved_benchmark_main_paths | approved_sector_main_paths
        | approved_benchmark_reference_main_paths
        | approved_sector_reference_main_paths
        | approved_benchmark_return_main_paths
        | approved_sector_return_main_paths
        | approved_benchmark_readiness_main_paths
        | approved_sector_readiness_main_paths
    )
]
protected_digest = hashlib.sha256()
for protected_path in comparative_foundation_protected_paths:
    protected_digest.update(protected_path.as_posix().encode("utf-8"))
    protected_digest.update(b"\0")
    protected_digest.update(
        protected_path.read_bytes().replace(b"\r\n", b"\n")
    )
    protected_digest.update(b"\0")
require(
    len(comparative_foundation_protected_paths) == 189
    and protected_digest.hexdigest()
    == "bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721",
    "ADR-026 decision-only foundation must preserve the protected "
    "OpenAPI/schema/fixture/API production baseline",
)

decision_only_paths = sorted(
    {
        *(
            path for path in Path("apps/api/src/test").rglob("*")
            if path.is_file()
        ),
        *(
            path
            for root in (Path("apps/web/src"), Path("apps/web/e2e"))
            for path in root.rglob("*")
            if path.is_file()
        ),
        *(
            path for path in Path("apps/web").glob("*")
            if path.is_file() and path.name != "next-env.d.ts"
        ),
    },
    key=lambda path: path.as_posix(),
)
comparative_foundation_decision_only_paths = [
    path
    for path in decision_only_paths
    if path.resolve() not in (
        approved_benchmark_test_paths | approved_sector_test_paths
        | approved_benchmark_reference_test_paths
        | approved_sector_reference_test_paths
        | approved_benchmark_return_test_paths
        | approved_sector_return_test_paths
        | approved_benchmark_readiness_test_paths
        | approved_sector_readiness_test_paths
    )
]
decision_only_digest = hashlib.sha256()
for decision_only_path in comparative_foundation_decision_only_paths:
    decision_only_digest.update(
        decision_only_path.as_posix().encode("utf-8")
    )
    decision_only_digest.update(b"\0")
    decision_only_digest.update(
        decision_only_path.read_bytes().replace(b"\r\n", b"\n")
    )
    decision_only_digest.update(b"\0")
require(
    len(comparative_foundation_decision_only_paths) == 197
    and decision_only_digest.hexdigest()
    == "d1bf378553fbc2e8f19808d849456b91ab83bac66ca090af7229ac78051fc329",
    "ADR-026 decision-only foundation must preserve the exact API test "
    "and web source/config baseline",
)

print(
    "Validated exact point-in-time basis-event/endpoint price-pair policy, "
    "signed basis-denominator scale-12 HALF_EVEN asset return, closed "
    "unavailability, decision-only comparative reference-return foundations, "
    "and no provider or product publication"
)
PYTHON
