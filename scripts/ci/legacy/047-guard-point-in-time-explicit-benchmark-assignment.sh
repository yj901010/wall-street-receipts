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

def normalized_hash(path):
    return hashlib.sha256(
        path.read_bytes().replace(b"\r\n", b"\n")
    ).hexdigest()

def enum_values(source, enum_name):
    match = re.search(
        rf"\benum\s+{re.escape(enum_name)}\s*\{{(?P<body>.*?)\}}",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing enum {enum_name}")
    return re.findall(
        r"\b[A-Z][A-Z0-9_]+\b",
        match.group("body").split(";", 1)[0],
    )

production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkassignment"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkassignment"
)
sector_assignment_production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorassignment"
)
sector_assignment_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorassignment"
)
sector_assignment_production_files = {
    "SectorAssignmentPolicyVersion.java",
    "SectorAssetClassificationEvidence.java",
    "SectorMembershipEvidence.java",
    "SectorMappingEvidence.java",
    "SectorAssignmentRequest.java",
    "SectorAssignmentResolution.java",
    "SectorAssignmentSelector.java",
}
sector_assignment_production_paths = {
    (sector_assignment_production_dir / name).resolve()
    for name in sector_assignment_production_files
}
sector_assignment_golden_resolved = (
    sector_assignment_test_dir / "SectorAssignmentSelectorGoldenTest.java"
).resolve()
benchmark_reference_production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreferencepair"
)
benchmark_reference_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreferencepair"
)
sector_reference_production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreferencepair"
)
sector_reference_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreferencepair"
)
benchmark_reference_production_files = {
    "BenchmarkReferenceLevelPairPolicyVersion.java",
    "BenchmarkReferenceIndexEvidence.java",
    "BenchmarkReferenceLevelObservation.java",
    "BenchmarkIndexDivisorContinuityEvidence.java",
    "BenchmarkReferenceLevelPairRequest.java",
    "BenchmarkReferenceLevelPairResolution.java",
    "BenchmarkReferenceLevelPairSelector.java",
}
sector_reference_production_files = {
    "SectorReferenceLevelPairPolicyVersion.java",
    "SectorReferenceIndexEvidence.java",
    "SectorReferenceLevelObservation.java",
    "SectorIndexDivisorContinuityEvidence.java",
    "SectorReferenceLevelPairRequest.java",
    "SectorReferenceLevelPairResolution.java",
    "SectorReferenceLevelPairSelector.java",
}
benchmark_reference_production_paths = {
    (benchmark_reference_production_dir / name).resolve()
    for name in benchmark_reference_production_files
}
sector_reference_production_paths = {
    (sector_reference_production_dir / name).resolve()
    for name in sector_reference_production_files
}
benchmark_reference_golden_resolved = (
    benchmark_reference_test_dir
    / "BenchmarkReferenceLevelPairSelectorGoldenTest.java"
).resolve()
sector_reference_golden_resolved = (
    sector_reference_test_dir
    / "SectorReferenceLevelPairSelectorGoldenTest.java"
).resolve()
benchmark_return_production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
benchmark_return_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
benchmark_return_production_files = {
    "BenchmarkReturnPolicyVersion.java",
    "BenchmarkReturnInput.java",
    "BenchmarkReturnResult.java",
    "BenchmarkReturnCalculator.java",
}
benchmark_return_production_paths = {
    (benchmark_return_production_dir / name).resolve()
    for name in benchmark_return_production_files
}
benchmark_return_golden_resolved = (
    benchmark_return_test_dir
    / "BenchmarkReturnCalculatorGoldenTest.java"
).resolve()
sector_return_production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn"
)
sector_return_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn"
)
sector_return_production_files = {
    "SectorReturnPolicyVersion.java",
    "SectorReturnInput.java",
    "SectorReturnResult.java",
    "SectorReturnCalculator.java",
}
sector_return_production_paths = {
    (sector_return_production_dir / name).resolve()
    for name in sector_return_production_files
}
sector_return_golden_resolved = (
    sector_return_test_dir / "SectorReturnCalculatorGoldenTest.java"
).resolve()
benchmark_readiness_production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturnreadiness"
)
benchmark_readiness_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturnreadiness"
)
benchmark_readiness_production_files = {
    "BenchmarkReturnReadinessPolicyVersion.java",
    "BenchmarkReturnReadinessRequest.java",
    "BenchmarkReturnReadinessResolution.java",
    "BenchmarkReturnReadinessResolver.java",
}
benchmark_readiness_production_paths = {
    (benchmark_readiness_production_dir / name).resolve()
    for name in benchmark_readiness_production_files
}
benchmark_readiness_golden_resolved = (
    benchmark_readiness_test_dir
    / "BenchmarkReturnReadinessResolverGoldenTest.java"
).resolve()
sector_readiness_production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturnreadiness"
)
sector_readiness_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturnreadiness"
)
sector_readiness_production_files = {
    "SectorReturnReadinessPolicyVersion.java",
    "SectorReturnReadinessRequest.java",
    "SectorReturnReadinessResolution.java",
    "SectorReturnReadinessResolver.java",
}
sector_readiness_production_paths = {
    (sector_readiness_production_dir / name).resolve()
    for name in sector_readiness_production_files
}
sector_readiness_golden_resolved = (
    sector_readiness_test_dir
    / "SectorReturnReadinessResolverGoldenTest.java"
).resolve()
production_files = {
    "BenchmarkAssignmentPolicyVersion.java",
    "BenchmarkAssetClassificationEvidence.java",
    "BenchmarkAssignmentEvidence.java",
    "BenchmarkAssignmentRequest.java",
    "BenchmarkAssignmentResolution.java",
    "BenchmarkAssignmentSelector.java",
}
test_files = {"BenchmarkAssignmentSelectorGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Benchmark-assignment production package must contain exactly six files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Benchmark-assignment test package must contain exactly one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
expected_source_hashes = {
    "BenchmarkAssetClassificationEvidence.java":
        "77e575f4af7863802f27feeaa29161745dca6213edde8827f2040d994852e23e",
    "BenchmarkAssignmentEvidence.java":
        "698c1b3cded8496d6c15409f07aeec26e0fe8398c6fe81c4beafabfa2d65e725",
    "BenchmarkAssignmentPolicyVersion.java":
        "26d524e98b4658245140cc85bc2a1a038953df16f0a00224a43ce772c06cfa8f",
    "BenchmarkAssignmentRequest.java":
        "86316a345372010c85c028adf0db513588b7480d34b7035e414877fe675b02bf",
    "BenchmarkAssignmentResolution.java":
        "982ed0dfc15fdec280d483551579d0c936ae49b290afa186ad566d9628828192",
    "BenchmarkAssignmentSelector.java":
        "1fa9715c93d03e3c3121ee29bcfe6dd20c28127aba7ac750fdc7c38f0ab84034",
}
for name, expected_hash in expected_source_hashes.items():
    require(
        normalized_hash(production_dir / name) == expected_hash,
        f"Benchmark-assignment exact source changed: {name}",
    )

adr_path = Path(
    "decisions/ADR-027-point-in-time-explicit-benchmark-assignment-v1.md"
)
marker = (
    "ADR-027 selects benchmark assignment only from explicit point-in-time "
    "evidence frozen at the outcome basis event."
)
required_docs = (
    adr_path,
    Path("README.md"),
    Path("quality/P3_ACCEPTANCE.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
docs = {}
for doc_path in required_docs:
    require(doc_path.is_file(), f"Missing ADR-027 contract doc: {doc_path}")
    doc_source = doc_path.read_text(encoding="utf-8")
    docs[doc_path] = doc_source
    require(
        doc_source.count(marker) == 1,
        f"ADR-027 marker must occur exactly once: {doc_path}",
    )
adr = docs[adr_path]
require(
    adr.startswith(
        "# ADR-027 — Point-in-Time Explicit Benchmark Assignment V1\n"
    )
    and "- Status: Accepted" in adr
    and "- Date: 2026-08-24" in adr,
    "ADR-027 title, accepted status, or date changed",
)

definition_matches = re.findall(
    r"```text\r?\n(?P<body>.*?)\r?\n```", adr, flags=re.DOTALL
)
definitions = [body for body in definition_matches if body.startswith("{")]
require(
    len(definitions) == 1,
    "ADR-027 must contain one canonical JSON definition block",
)
definition = definitions[0]
policy_hash = (
    "7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69"
)
require(
    len(definition.encode("ascii")) == 4261
    and hashlib.sha256(definition.encode("utf-8")).hexdigest()
    == policy_hash
    and "\n" not in definition
    and "\r" not in definition
    and definition == definition.strip(),
    "ADR-027 canonical definition bytes, length, or hash changed",
)
policy_source = sources["BenchmarkAssignmentPolicyVersion.java"]
require(
    definition in policy_source
    and policy_source.count(definition) == 1
    and policy_source.count(policy_hash) == 1
    and "canonicalDefinitionUtf8()" in policy_source
    and "StandardCharsets.UTF_8" in policy_source,
    "Benchmark-assignment Java policy bytes/hash boundary changed",
)
require(
    enum_values(policy_source, "BenchmarkAssignmentPolicyVersion")
    == ["POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1"],
    "Benchmark-assignment policy enum changed",
)

assignment_source = sources["BenchmarkAssignmentEvidence.java"]
resolution_source = sources["BenchmarkAssignmentResolution.java"]
selector_source = sources["BenchmarkAssignmentSelector.java"]
classification_source = sources[
    "BenchmarkAssetClassificationEvidence.java"
]
request_source = sources["BenchmarkAssignmentRequest.java"]
require(
    enum_values(assignment_source, "BenchmarkReferenceKind") == [
        "PROVIDER_PUBLISHED_PRICE_INDEX",
        "PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX",
        "NON_PROVIDER_PUBLISHED_PRICE_INDEX",
        "UNKNOWN",
    ],
    "Benchmark reference-kind vocabulary changed",
)
not_applicable_reasons = [
    "NON_EQUITY",
    "NON_US_PRIMARY_VENUE",
    "NON_USD_CURRENCY",
    "NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY",
]
unavailable_reasons = [
    "CLASSIFICATION_MISSING_AS_OF",
    "CLASSIFICATION_BASIS_MISMATCH",
    "CLASSIFICATION_ASSET_MISMATCH",
    "CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH",
    "CLASSIFICATION_AMBIGUOUS",
    "ASSIGNMENT_MISSING_AS_OF",
    "ASSIGNMENT_BASIS_MISMATCH",
    "ASSIGNMENT_ASSET_MISMATCH",
    "ASSIGNMENT_ASSET_TYPE_MISMATCH",
    "ASSIGNMENT_PRIMARY_VENUE_MISMATCH",
    "ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH",
    "ASSIGNMENT_CURRENCY_MISMATCH",
    "ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH",
    "OUT_OF_SCOPE_ASSIGNMENT_CONFLICT",
    "BENCHMARK_ASSET_ID_MISMATCH",
    "BENCHMARK_ASSET_TYPE_MISMATCH",
    "BENCHMARK_CURRENCY_MISMATCH",
    "BENCHMARK_REFERENCE_KIND_MISMATCH",
    "ASSIGNMENT_AMBIGUOUS",
]
require(
    enum_values(resolution_source, "NotApplicableReason")
    == not_applicable_reasons,
    "Benchmark-assignment non-applicable reason order changed",
)
require(
    enum_values(resolution_source, "UnavailableReason")
    == unavailable_reasons,
    "Benchmark-assignment unavailable reason order changed",
)

compact_sources = {
    name: re.sub(r"\s+", "", source)
    for name, source in sources.items()
}
require(
    "publicsealedinterfaceEffectiveIntervalEnd"
    "permitsOpenEnded,EndsAtExclusive" in compact_sources[
        "BenchmarkAssetClassificationEvidence.java"
    ]
    and "publicrecordOpenEnded()implementsEffectiveIntervalEnd"
    in compact_sources["BenchmarkAssetClassificationEvidence.java"]
    and "publicrecordEndsAtExclusive(Instantvalue)"
    "implementsEffectiveIntervalEnd" in compact_sources[
        "BenchmarkAssetClassificationEvidence.java"
    ]
    and "!instant.isBefore(startsAtInclusive)" in classification_source
    and "instant.isBefore(((EndsAtExclusive)end).value())"
    in compact_sources["BenchmarkAssetClassificationEvidence.java"],
    "Explicit start-inclusive/end-exclusive interval surface changed",
)
require(
    "evaluationAsOf.isBefore(basis.eventTime())" in request_source
    and "List.copyOf(classificationCandidates)" in request_source
    and "List.copyOf(assignmentCandidates)" in request_source
    and "candidate.availableAt(),candidate.capturedAt(),"
    "request.evaluationAsOf()" in compact_sources[
        "BenchmarkAssignmentSelector.java"
    ]
    and "!availableAt.isAfter(evaluationAsOf)"
    in compact_sources["BenchmarkAssignmentSelector.java"]
    and "!capturedAt.isAfter(evaluationAsOf)"
    in compact_sources["BenchmarkAssignmentSelector.java"],
    "Benchmark-assignment request immutability or PIT filter changed",
)
classification_gate_order = [
    "CLASSIFICATION_MISSING_AS_OF",
    "CLASSIFICATION_BASIS_MISMATCH",
    "CLASSIFICATION_ASSET_MISMATCH",
    "CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH",
    "CLASSIFICATION_AMBIGUOUS",
]
assignment_gate_order = unavailable_reasons[5:]
for label, order in (
    ("classification", classification_gate_order),
    ("assignment", assignment_gate_order),
):
    positions = [
        selector_source.find(reason)
        for reason in order
    ]
    require(
        all(position >= 0 for position in positions)
        and positions == sorted(positions),
        f"Benchmark-assignment {label} gate precedence changed",
    )
require(
    'REQUIRED_BENCHMARK_ASSET_ID = "asset-spx"' in selector_source
    and 'Currency.getInstance("USD")' in selector_source
    and "candidate.benchmarkAssetType() != AssetType.INDEX"
    in selector_source
    and "BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX"
    in compact_sources["BenchmarkAssignmentSelector.java"]
    and "assignments.size() > 1" in selector_source
    and "classifications.size() > 1" in selector_source,
    "Exact asset-spx scope, price-index kind, or ambiguity boundary changed",
)

forbidden_source_tokens = (
    "org.springframework", "jakarta.", "com.fasterxml.jackson",
    "java.sql", "java.net", ".infrastructure.", ".application.",
    "Repository", "Provider", "HttpClient", "WebClient", "Clock",
    "Random", "double", "float", "OutcomeEvaluationStatus",
    "CallOutcome", "AssetReturnCalculator", "BenchmarkReturn",
    "SectorReturn", "MarketSnapshot",
)
for name, source in sources.items():
    require(
        not any(token in source for token in forbidden_source_tokens),
        f"Benchmark-assignment source crosses its pure boundary: {name}",
    )
basis_consumers = {
    name for name, source in sources.items() if "OutcomeBasis" in source
}
require(
    basis_consumers == {
        "BenchmarkAssetClassificationEvidence.java",
        "BenchmarkAssignmentEvidence.java",
        "BenchmarkAssignmentRequest.java",
        "BenchmarkAssignmentResolution.java",
    }
    and all(
        imported
        == "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis"
        for source in sources.values()
        for imported in re.findall(
            r"^import\s+([^;]*\.domain\.outcome\.horizon\.[^;]+);",
            source,
            flags=re.MULTILINE,
        )
    ),
    "Only four exact ADR-027 types may consume OutcomeBasis",
)

api_main_dir = Path("apps/api/src/main/java")
approved_paths = {
    (production_dir / name).resolve() for name in production_files
}
approved_reference_consumers = {
    (benchmark_reference_production_dir
     / "BenchmarkReferenceLevelPairRequest.java").resolve(): {
        "BenchmarkAssignmentPolicyVersion",
        "BenchmarkAssetClassificationEvidence",
        "BenchmarkAssignmentResolution",
    },
    (benchmark_reference_production_dir
     / "BenchmarkReferenceLevelPairResolution.java").resolve(): {
        "BenchmarkAssignmentResolution",
    },
    (benchmark_reference_production_dir
     / "BenchmarkReferenceLevelPairSelector.java").resolve(): {
        "BenchmarkAssignmentResolution",
    },
}
type_markers = tuple(name.removesuffix(".java") for name in production_files)
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in approved_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    actual_references = {
        marker_name for marker_name in type_markers
        if re.search(rf"\b{re.escape(marker_name)}\b", other_source)
    }
    if other_path.resolve() in approved_reference_consumers:
        require(
            actual_references
            == approved_reference_consumers[other_path.resolve()]
            and "domain.outcome.benchmarkassignment" in other_source,
            f"ADR-030 benchmark-assignment consumer edge changed: {other_path}",
        )
        continue
    require(
        "domain.outcome.benchmarkassignment" not in other_source
        and not actual_references,
        f"Benchmark-assignment leaf must not be reverse-wired: {other_path}",
    )

golden_path = test_dir / "BenchmarkAssignmentSelectorGoldenTest.java"
golden = golden_path.read_text(encoding="utf-8")
require(
    normalized_hash(golden_path)
    == "cc04d1ecbb16fa9a49cf57d0b968305d528d50fe1f23e7e43f9fad28c43912e1",
    "Benchmark-assignment golden exact source changed",
)
required_golden_methods = (
    "canonicalPolicyDefinitionHasExactUtf8BytesIndependentHashAndDefensiveReads",
    "publicPolicyEvidenceRequestAndResultSurfacesRemainExactlyClosed",
    "resolvesOneExactOriginalAssignmentAtInclusivePitAndIntervalBoundaries",
    "correctionBasisIsAnIndependentCompleteIdentityNotAnEventTimeShortcut",
    "effectiveIntervalsAreStartInclusiveEndExclusiveAndExplicitlyOpenEnded",
    "futureClassificationCandidatesAreIdenticalToAbsentAndInvisibleToReasoning",
    "futureAssignmentCandidatesAreIdenticalToAbsentAndInvisibleToReasoning",
    "exactPitTimestampEqualityIsVisibleForBothEvidenceKinds",
    "everyVisibleClassificationMismatchUsesItsExactReason",
    "classificationMismatchPrecedenceIsInputOrderIndependent",
    "exactVisibleClassificationDuplicatesAreAmbiguousWithoutDeduplication",
    "everyNonEquityAssetTypeIsNotApplicableWithoutAnAssignment",
    "equityOutsideV1ScopeUsesTheExactThreeWayNotApplicableTruthTable",
    "everyOutOfScopeStateWithAVisibleCoherentAssignmentFailsClosed",
    "missingVisibleAssignmentDistinguishesExpectedEvidenceFromIntentionalScope",
    "everyVisibleAssignmentCoherenceMismatchUsesItsExactReason",
    "everyVisibleBenchmarkTargetMismatchUsesItsExactReason",
    "assignmentMismatchPrecedenceIsInputOrderIndependent",
    "exactVisibleAssignmentDuplicatesAreAmbiguousWithoutDeduplication",
    "everyClassificationMismatchPoisonsValidDuplicatesBeforeAmbiguity",
    "everyAssignmentMismatchPoisonsValidDuplicatesBeforeAmbiguity",
    "requestDefensivelyCopiesListsAndRejectsEveryMissingPublicInput",
    "directResultConstructorsRejectMissingFutureAndContradictoryComponents",
    "replayIsIndependentOfInputOrderJvmDefaultsAndPriorCalls",
)
require(
    all(method in golden for method in required_golden_methods)
    and len(re.findall(r"(?m)^\s*@Test\s*$", golden)) == 18
    and len(re.findall(r"(?m)^\s*@ParameterizedTest", golden)) == 13
    and len(re.findall(r"(?m)^\s*@MethodSource", golden)) == 5
    and "MessageDigest.getInstance(\"SHA-256\")" in golden
    and "Locale.setDefault" in golden
    and "TimeZone.setDefault" in golden
    and "finally" in golden
    and "@Disabled" not in golden
    and "Assumptions" not in golden,
    "Benchmark-assignment golden coverage or anti-disable boundary changed",
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
    "Benchmark-assignment slice must preserve schemas and fixtures",
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
    "Benchmark-assignment slice must preserve manifest membership/order",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    }
    and {
        path.name
        for path in Path(
            "apps/api/src/main/resources/db/migration"
        ).glob("*.sql")
    } == {
        "V1__baseline.sql", "V2__analyst_calls.sql",
        "V3__analyst_call_revisions.sql", "V4__call_outcomes.sql",
        "V5__call_contexts.sql",
        "V6__sec_filing_catalog_captures.sql",
        "V7__sec_historical_filing_segment_captures.sql",
        "V8__sec_filing_history_collection_manifests.sql",
        "V9__sec_filing_collection_attempts.sql",
    },
    "Benchmark-assignment slice must preserve OpenAPI and Flyway",
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
    and all(
        methodology["status"] == "MODEL_ONLY"
        for methodology in outcomes["methodologies"]
    )
    and len(outcomes["outcomes"]) == 4
    and all(
        outcome[metric] is None
        for outcome in outcomes["outcomes"]
        for metric in metrics
    ),
    "Benchmark-assignment evidence must not publish outcomes",
)

for web_root in (Path("apps/web/src"), Path("apps/web/e2e")):
    for web_path in web_root.rglob("*"):
        if not web_path.is_file():
            continue
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "benchmarkassignment" not in web_path.as_posix().lower()
            and "domain.outcome.benchmarkassignment" not in web_source
            and not any(marker_name in web_source for marker_name in type_markers),
            f"Benchmark-assignment leaf must not expand web: {web_path}",
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
for path in protected_paths:
    protected_digest.update(path.as_posix().encode("utf-8"))
    protected_digest.update(b"\0")
    protected_digest.update(path.read_bytes().replace(b"\r\n", b"\n"))
    protected_digest.update(b"\0")
require(
    len(protected_paths) == 232
    and protected_digest.hexdigest()
    == "2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941",
    "Benchmark-assignment protected production baseline changed",
)
legacy_protected_paths = [
    path for path in protected_paths
    if path.resolve() not in approved_paths
    and path.resolve() not in sector_assignment_production_paths
    and path.resolve() not in benchmark_reference_production_paths
    and path.resolve() not in sector_reference_production_paths
    and path.resolve() not in benchmark_return_production_paths
    and path.resolve() not in sector_return_production_paths
    and path.resolve() not in benchmark_readiness_production_paths
    and path.resolve() not in sector_readiness_production_paths
]
legacy_protected_digest = hashlib.sha256()
for path in legacy_protected_paths:
    legacy_protected_digest.update(path.as_posix().encode("utf-8"))
    legacy_protected_digest.update(b"\0")
    legacy_protected_digest.update(
        path.read_bytes().replace(b"\r\n", b"\n")
    )
    legacy_protected_digest.update(b"\0")
require(
    len(legacy_protected_paths) == 189
    and legacy_protected_digest.hexdigest()
    == "bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721",
    "ADR-026 protected baseline changed outside exact ADR-027 files",
)

test_web_paths = sorted(
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
test_web_digest = hashlib.sha256()
for path in test_web_paths:
    test_web_digest.update(path.as_posix().encode("utf-8"))
    test_web_digest.update(b"\0")
    test_web_digest.update(path.read_bytes().replace(b"\r\n", b"\n"))
    test_web_digest.update(b"\0")
require(
    len(test_web_paths) == 205
    and test_web_digest.hexdigest()
    == "487c19412c79497ad03e62db92790be768f78c2bb2f7646cf3fed6fc1f970f95",
    "Benchmark-assignment API-test/web baseline changed",
)
golden_resolved = golden_path.resolve()
legacy_test_web_paths = [
    path for path in test_web_paths
    if path.resolve() != golden_resolved
    and path.resolve() != sector_assignment_golden_resolved
    and path.resolve() != benchmark_reference_golden_resolved
    and path.resolve() != sector_reference_golden_resolved
    and path.resolve() != benchmark_return_golden_resolved
    and path.resolve() != sector_return_golden_resolved
    and path.resolve() != benchmark_readiness_golden_resolved
    and path.resolve() != sector_readiness_golden_resolved
]
legacy_test_web_digest = hashlib.sha256()
for path in legacy_test_web_paths:
    legacy_test_web_digest.update(path.as_posix().encode("utf-8"))
    legacy_test_web_digest.update(b"\0")
    legacy_test_web_digest.update(
        path.read_bytes().replace(b"\r\n", b"\n")
    )
    legacy_test_web_digest.update(b"\0")
require(
    len(legacy_test_web_paths) == 197
    and legacy_test_web_digest.hexdigest()
    == "d1bf378553fbc2e8f19808d849456b91ab83bac66ca090af7229ac78051fc329",
    "ADR-026 test/web baseline changed outside exact ADR-027 golden",
)

print(
    "Validated exact point-in-time explicit benchmark assignment, PIT-first "
    "basis-frozen evidence, closed applicability and unavailable precedence, "
    "asset-spx price-index scope, reverse isolation, and no product publication"
)
PYTHON
