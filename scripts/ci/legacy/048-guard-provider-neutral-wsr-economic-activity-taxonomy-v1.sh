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

def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"Duplicate canonical JSON key: {key}")
        result[key] = value
    return result

adr_path = Path(
    "decisions/ADR-028-provider-neutral-wsr-economic-activity-"
    "taxonomy-v1.md"
)
marker = (
    "ADR-028 locks WSR Economic Activity V1 and exact point-in-time "
    "provider-node mapping semantics."
)
required_docs = (
    adr_path,
    Path("README.md"),
    Path("quality/P3_ACCEPTANCE.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
docs = {}
for doc_path in required_docs:
    require(doc_path.is_file(), f"Missing ADR-028 contract doc: {doc_path}")
    source = doc_path.read_text(encoding="utf-8")
    docs[doc_path] = source
    require(
        source.count(marker) == 1,
        f"ADR-028 marker must occur exactly once: {doc_path}",
    )

adr = docs[adr_path]
require(
    adr.startswith(
        "# ADR-028 — Provider-Neutral WSR Economic Activity "
        "Taxonomy V1\n"
    )
    and "- Status: Accepted" in adr
    and "- Date: 2026-08-24" in adr,
    "ADR-028 title, accepted status, or date changed",
)
stale_approval_phrases = (
    "provider-neutral sector taxonomy decision comes next",
    "sector assignment code remains blocked until that approval is received",
    "next explicit product-decision boundary",
)
for doc_path, source in docs.items():
    normalized = re.sub(r"\s+", " ", source).lower()
    require(
        not any(phrase in normalized for phrase in stale_approval_phrases),
        f"Stale pre-ADR-028 approval wording remains: {doc_path}",
    )

blocks = re.findall(
    r"```text\r?\n(?P<body>.*?)\r?\n```",
    adr,
    flags=re.DOTALL,
)
require(len(blocks) == 2, "ADR-028 must contain exactly two text blocks")
definitions = {}
for body in blocks:
    require(
        body.startswith("{") and body.endswith("}")
        and "\n" not in body and "\r" not in body
        and body == body.strip() and body.isascii(),
        "ADR-028 canonical definitions must be single-line ASCII JSON",
    )
    definition = json.loads(
        body,
        object_pairs_hook=reject_duplicate_keys,
    )
    require(
        json.dumps(
            definition,
            ensure_ascii=True,
            separators=(",", ":"),
        ) == body,
        "ADR-028 canonical JSON is not minimally serialized",
    )
    first_key = next(iter(definition))
    require(
        first_key in {"taxonomyId", "mappingPolicyVersion"}
        and first_key not in definitions,
        "ADR-028 canonical definition identity is missing or duplicated",
    )
    definitions[first_key] = (body, definition)

taxonomy_body, taxonomy = definitions["taxonomyId"]
mapping_body, mapping = definitions["mappingPolicyVersion"]
taxonomy_hash = (
    "820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae"
)
mapping_hash = (
    "ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77"
)
require(
    len(taxonomy_body.encode("utf-8")) == 3824
    and hashlib.sha256(taxonomy_body.encode("utf-8")).hexdigest()
    == taxonomy_hash
    and adr.count("- Bytes: `3824`") == 1
    and adr.count(f"- SHA-256: `{taxonomy_hash}`") == 1,
    "ADR-028 canonical taxonomy bytes, length, hash, or metadata changed",
)
require(
    len(mapping_body.encode("utf-8")) == 4395
    and hashlib.sha256(mapping_body.encode("utf-8")).hexdigest()
    == mapping_hash
    and adr.count("- Bytes: `4395`") == 1
    and adr.count(f"- SHA-256: `{mapping_hash}`") == 1,
    "ADR-028 canonical mapping-policy bytes, length, hash, or metadata changed",
)

taxonomy_keys = [
    "taxonomyId", "taxonomyVersion", "taxonomyKind",
    "assignmentCriterion", "root", "leafNodes", "closedNodeSet",
    "assignableLeafCount", "unknownNode", "otherNode",
    "unclassifiedNode", "missingConflictAmbiguityRule",
    "diversifiedOperationsRule", "industryHierarchy",
    "gicsEquivalenceClaim", "icbEquivalenceClaim",
    "providerAssignment", "referenceIndexAssignment",
    "p2SyntheticLabels", "changeRule",
]
require(
    list(taxonomy) == taxonomy_keys
    and taxonomy["taxonomyId"] == "wsr-economic-activity"
    and taxonomy["taxonomyVersion"] == "1.0.0"
    and taxonomy["taxonomyKind"]
    == "PROVIDER_NEUTRAL_SINGLE_LEVEL_ECONOMIC_ACTIVITY"
    and taxonomy["assignmentCriterion"]
    == "PRIMARY_OPERATING_ACTIVITY_AS_EXPLICITLY_EVIDENCED"
    and taxonomy["root"] == {
        "nodeId": "wsr-sector-root",
        "label": "WSR Economic Activity",
        "assignable": False,
    },
    "ADR-028 taxonomy identity or root changed",
)
expected_leaf_pairs = [
    ("wsr-sector-digital-systems", "Digital Systems"),
    ("wsr-sector-connectivity-media", "Connectivity and Media"),
    ("wsr-sector-health-bioscience", "Health and Bioscience"),
    ("wsr-sector-financial-risk-services", "Financial and Risk Services"),
    ("wsr-sector-consumer-essentials", "Consumer Essentials"),
    ("wsr-sector-consumer-choice-commerce", "Consumer Choice and Commerce"),
    ("wsr-sector-production-mobility", "Production and Mobility"),
    ("wsr-sector-energy-systems", "Energy Systems"),
    (
        "wsr-sector-materials-resource-processing",
        "Materials and Resource Processing",
    ),
    ("wsr-sector-essential-networks", "Essential Networks"),
    (
        "wsr-sector-property-built-environment",
        "Property and Built Environment",
    ),
    ("wsr-sector-diversified-operations", "Diversified Operations"),
]
leaves = taxonomy["leafNodes"]
require(
    [(leaf["nodeId"], leaf["label"]) for leaf in leaves]
    == expected_leaf_pairs
    and all(list(leaf) == ["nodeId", "label", "definition"] for leaf in leaves)
    and all(leaf["definition"].strip() == leaf["definition"]
            and leaf["definition"] for leaf in leaves)
    and len({leaf["nodeId"] for leaf in leaves}) == 12
    and len({leaf["label"] for leaf in leaves}) == 12,
    "ADR-028 closed leaf IDs, labels, definitions, or order changed",
)
taxonomy_tail = {
    "closedNodeSet": True,
    "assignableLeafCount": 12,
    "unknownNode": "ABSENT",
    "otherNode": "ABSENT",
    "unclassifiedNode": "ABSENT",
    "missingConflictAmbiguityRule": "EVIDENCE_UNAVAILABLE_NOT_A_NODE",
    "diversifiedOperationsRule":
        "EXPLICIT_SOURCE_EVIDENCE_OF_NO_SINGLE_PRIMARY_ACTIVITY_ONLY",
    "industryHierarchy": "ABSENT_V1",
    "gicsEquivalenceClaim": "ABSENT",
    "icbEquivalenceClaim": "ABSENT",
    "providerAssignment": "ABSENT",
    "referenceIndexAssignment": "ABSENT",
    "p2SyntheticLabels":
        "FORBIDDEN_AS_TAXONOMY_OR_MEMBERSHIP_EVIDENCE",
    "changeRule":
        "ANY_SEMANTIC_OR_NODE_CHANGE_REQUIRES_NEW_VERSION_AND_HASH",
}
require(
    all(taxonomy[key] == value for key, value in taxonomy_tail.items()),
    "ADR-028 closed taxonomy, no-fallback, or absence rules changed",
)

mapping_keys = [
    "mappingPolicyVersion", "taxonomyId", "taxonomyVersion",
    "requiredTaxonomyDefinitionHash", "canonicalTargetIdentity",
    "canonicalNodeRule", "mappingEvidenceFields",
    "providerNodeDefinitionVariants", "mappingDispositionVariants",
    "notMappedReasons", "effectiveIntervalFields",
    "effectiveIntervalEndVariants", "mappingIdentity",
    "mappingIdentityComparison", "providerLabelRole",
    "mappedDefinitionRequirement", "mappingCardinality",
    "evidenceTemporalRule", "pitPredicate", "futureEvidenceRule",
    "effectiveIntervalPredicate", "effectiveIntervalBoundary",
    "openEndedRepresentation", "outcomeBasisOwnership",
    "missingMappingResolution", "notMappedResolution",
    "equalDuplicateRule", "differentTargetRule",
    "overlappingVisibleRowsRule", "mappingSetManifestFields",
    "mappingEvidenceIdRule", "mappingSetEntrySort",
    "mappingSetSortComparison", "effectiveStartRepresentation",
    "mappingSetCanonicalization", "mappingSetHashAlgorithm",
    "mappingSetHashInput", "mappingSetEntryManifestCorrelation",
    "mappingSetDefinitionHashRule", "mappingSetChangeRule",
    "forbiddenInference", "providerMappingSet", "sectorAssignment",
    "providerIntegration", "referenceIndexAssignment",
    "lifecycleMapping",
]
evidence_fields = [
    "mappingEvidenceId", "providerEventId", "mappingPolicyVersion",
    "mappingPolicyDefinitionHash", "mappingSetId", "mappingSetVersion",
    "mappingSetDefinitionHash", "taxonomyId", "taxonomyVersion",
    "taxonomyDefinitionHash", "providerId", "providerSchemeId",
    "providerSchemeRevision", "providerNodeId", "providerNodeLabel",
    "providerNodeDefinition", "mappingDisposition", "mappingSourceId",
    "mappingSourceRevision", "provenanceId", "effectiveInterval",
    "availableAt", "capturedAt",
]
manifest_fields = [
    "mappingSetId", "mappingSetVersion", "mappingSetDefinitionHash",
    "mappingPolicyVersion", "mappingPolicyDefinitionHash", "taxonomyId",
    "taxonomyVersion", "taxonomyDefinitionHash", "providerId",
    "providerSchemeId", "providerSchemeRevision", "mappingSourceId",
    "mappingSourceRevision", "provenanceId", "entries",
]
forbidden_inference = [
    "RAW_LABEL_MATCH", "NORMALIZED_LABEL_MATCH", "FUZZY_LABEL_MATCH",
    "TICKER", "ISSUER_NAME", "CURRENT_ROW", "LATEST_REVISION",
    "NEAREST_INTERVAL", "PROVIDER_PREFERENCE", "SILENT_DEDUPLICATION",
    "P2_MAP_OR_TREEMAP_LABEL", "FALLBACK",
]
require(
    list(mapping) == mapping_keys
    and mapping["mappingPolicyVersion"]
    == "POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1"
    and mapping["taxonomyId"] == "wsr-economic-activity"
    and mapping["taxonomyVersion"] == "1.0.0"
    and mapping["requiredTaxonomyDefinitionHash"] == taxonomy_hash
    and mapping["canonicalTargetIdentity"] == [
        "taxonomyId", "taxonomyVersion", "taxonomyDefinitionHash",
        "canonicalNodeId",
    ]
    and mapping["canonicalNodeRule"]
    == "MAPPED_TARGET_MUST_BE_ONE_CLOSED_ASSIGNABLE_LEAF_ID"
    and mapping["mappingEvidenceFields"] == evidence_fields
    and mapping["mappingSetManifestFields"] == manifest_fields
    and mapping["forbiddenInference"] == forbidden_inference,
    "ADR-028 mapping identity, binding, fields, or inference list changed",
)
require(
    mapping["providerNodeDefinitionVariants"]
    == {"Recorded": ["value", "languageTag"], "NotPublished": []}
    and mapping["mappingDispositionVariants"]
    == {"Mapped": ["canonicalNodeId"], "NotMapped": ["reason"]}
    and mapping["notMappedReasons"] == [
        "NO_CANONICAL_EQUIVALENT", "PROVIDER_NODE_TOO_BROAD",
        "PROVIDER_DEFINITION_UNAVAILABLE",
    ]
    and mapping["effectiveIntervalFields"] == ["startsAtInclusive", "end"]
    and mapping["effectiveIntervalEndVariants"]
    == {"OpenEnded": [], "EndsAtExclusive": ["value"]}
    and mapping["mappingIdentity"] == [
        "providerId", "providerSchemeId", "providerSchemeRevision",
        "providerNodeId",
    ],
    "ADR-028 mapping variants, reasons, interval, or identity changed",
)
mapping_scalars = {
    "mappingIdentityComparison":
        "EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_EQUALITY",
    "providerLabelRole":
        "PRESERVED_EVIDENCE_ONLY_NOT_IDENTITY_OR_MATCH_KEY",
    "mappedDefinitionRequirement": "RECORDED_PROVIDER_NODE_DEFINITION",
    "mappingCardinality":
        "MANY_PROVIDER_NODES_TO_ONE_CANONICAL_NODE_ALLOWED_ONE_"
        "DISPOSITION_PER_IDENTITY_AND_EFFECTIVE_INTERVAL",
    "evidenceTemporalRule": "availableAt<=capturedAt",
    "pitPredicate":
        "availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf",
    "futureEvidenceRule":
        "INVISIBLE_TO_OUTPUT_REASON_CONFLICT_AND_CARDINALITY",
    "effectiveIntervalPredicate":
        "startsAtInclusive<=basis.eventTime&&(end==OpenEnded||"
        "basis.eventTime<end.value)",
    "effectiveIntervalBoundary": "START_INCLUSIVE_END_EXCLUSIVE",
    "openEndedRepresentation": "EXPLICIT_OPEN_ENDED_VARIANT",
    "outcomeBasisOwnership": "SECTOR_ASSIGNMENT_REQUEST_AND_RESULT_ONLY",
    "missingMappingResolution": "EVIDENCE_UNAVAILABLE",
    "notMappedResolution": "EVIDENCE_UNAVAILABLE",
    "equalDuplicateRule": "MAPPING_AMBIGUOUS_NO_DEDUPLICATION",
    "differentTargetRule": "MAPPING_CONFLICT",
    "overlappingVisibleRowsRule":
        "MAPPING_AMBIGUOUS_UNLESS_DISPOSITIONS_DISAGREE_THEN_"
        "MAPPING_CONFLICT",
    "mappingEvidenceIdRule": "GLOBALLY_UNIQUE_WITHIN_MAPPING_SET",
    "mappingSetSortComparison":
        "EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_"
        "LEXICOGRAPHIC_ORDER",
    "effectiveStartRepresentation":
        "CANONICAL_UTC_INSTANT_MICROSECOND_PRECISION",
    "mappingSetCanonicalization":
        "SINGLE_LINE_UTF8_JSON_NO_BOM_NO_SURROUNDING_WHITESPACE_NO_"
        "TRAILING_LINE_ENDING_OBJECT_FIELDS_IN_DECLARED_ORDER",
    "mappingSetHashAlgorithm": "SHA-256",
    "mappingSetHashInput":
        "MANIFEST_AND_ENTRIES_WITH_EVERY_MAPPING_SET_DEFINITION_HASH_"
        "FIELD_OMITTED",
    "mappingSetEntryManifestCorrelation":
        "EACH_ENTRY_MAPPING_SET_ID_VERSION_POLICY_VERSION_HASH_TAXONOMY_"
        "ID_VERSION_HASH_PROVIDER_ID_SCHEME_ID_REVISION_MAPPING_SOURCE_"
        "ID_REVISION_AND_PROVENANCE_ID_MUST_EQUAL_MANIFEST",
    "mappingSetDefinitionHashRule":
        "EVERY_POPULATED_OCCURRENCE_MUST_EQUAL_COMPUTED_SHA_256",
    "mappingSetChangeRule":
        "ANY_OTHER_MAPPING_BYTE_CHANGE_REQUIRES_NEW_VERSION_AND_HASH_"
        "NO_SILENT_MIGRATION",
    "providerMappingSet":
        "ABSENT_UNTIL_PROVIDER_SELECTION_AND_RIGHTS_APPROVAL",
    "sectorAssignment": "ABSENT",
    "providerIntegration": "ABSENT",
    "referenceIndexAssignment": "ABSENT",
    "lifecycleMapping": "ABSENT",
}
require(
    all(mapping[key] == value for key, value in mapping_scalars.items())
    and mapping["mappingSetEntrySort"] == [
        "providerId", "providerSchemeId", "providerSchemeRevision",
        "providerNodeId", "effectiveInterval.startsAtInclusive",
        "mappingEvidenceId",
    ],
    "ADR-028 PIT, fail-closed, mapping-set, or absence rules changed",
)

required_official_sources = (
    "https://www.spglobal.com/en/terms-of-use",
    "https://www.msci.com/legal/terms-of-use",
    "https://www.lseg.com/content/dam/ftse-russell/en_us/documents/"
    "other/ftse-icb-attribution.pdf",
    "https://www.sec.gov/search-filings/standard-industrial-"
    "classification-sic-code-list",
    "https://www.sec.gov/search-filings/edgar-application-programming-"
    "interfaces",
    "https://www.census.gov/naics/",
    "https://www.census.gov/naics/concordances/concordances.html",
)
require(
    all(source in adr for source in required_official_sources),
    "ADR-028 official source/licensing boundary changed",
)

canonical_runtime_markers = {
    "wsr-economic-activity",
    mapping["mappingPolicyVersion"],
    *(leaf["nodeId"] for leaf in leaves),
}
sector_assignment_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorassignment"
)
sector_assignment_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorassignment"
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
benchmark_reference_main_files = {
    "BenchmarkReferenceLevelPairPolicyVersion.java",
    "BenchmarkReferenceIndexEvidence.java",
    "BenchmarkReferenceLevelObservation.java",
    "BenchmarkIndexDivisorContinuityEvidence.java",
    "BenchmarkReferenceLevelPairRequest.java",
    "BenchmarkReferenceLevelPairResolution.java",
    "BenchmarkReferenceLevelPairSelector.java",
}
benchmark_reference_test_files = {
    "BenchmarkReferenceLevelPairSelectorGoldenTest.java",
}
sector_reference_main_files = {
    "SectorReferenceLevelPairPolicyVersion.java",
    "SectorReferenceIndexEvidence.java",
    "SectorReferenceLevelObservation.java",
    "SectorIndexDivisorContinuityEvidence.java",
    "SectorReferenceLevelPairRequest.java",
    "SectorReferenceLevelPairResolution.java",
    "SectorReferenceLevelPairSelector.java",
}
sector_reference_test_files = {
    "SectorReferenceLevelPairSelectorGoldenTest.java",
}
require(
    {path.name for path in sector_assignment_main_dir.glob("*.java")}
    == approved_sector_main_files
    and {path.name for path in sector_assignment_test_dir.glob("*.java")}
    == approved_sector_test_files,
    "ADR-028 may be consumed only by the exact ADR-029 source-local slice",
)
require(
    {path.name for path in benchmark_reference_main_dir.glob("*.java")}
    == benchmark_reference_main_files
    and {path.name for path in benchmark_reference_test_dir.glob("*.java")}
    == benchmark_reference_test_files
    and {path.name for path in sector_reference_main_dir.glob("*.java")}
    == sector_reference_main_files
    and {path.name for path in sector_reference_test_dir.glob("*.java")}
    == sector_reference_test_files,
    "ADR-030 reference-level-pair packages must remain exact and source-local",
)
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
benchmark_reference_main_paths = {
    (benchmark_reference_main_dir / name).resolve()
    for name in benchmark_reference_main_files
}
benchmark_reference_test_paths = {
    (benchmark_reference_test_dir / name).resolve()
    for name in benchmark_reference_test_files
}
sector_reference_main_paths = {
    (sector_reference_main_dir / name).resolve()
    for name in sector_reference_main_files
}
sector_reference_test_paths = {
    (sector_reference_test_dir / name).resolve()
    for name in sector_reference_test_files
}
benchmark_return_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
benchmark_return_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
benchmark_return_main_files = {
    "BenchmarkReturnPolicyVersion.java",
    "BenchmarkReturnInput.java",
    "BenchmarkReturnResult.java",
    "BenchmarkReturnCalculator.java",
}
benchmark_return_test_files = {
    "BenchmarkReturnCalculatorGoldenTest.java",
}
benchmark_return_main_paths = {
    (benchmark_return_main_dir / name).resolve()
    for name in benchmark_return_main_files
}
benchmark_return_test_paths = {
    (benchmark_return_test_dir / name).resolve()
    for name in benchmark_return_test_files
}
require(
    {path.name for path in benchmark_return_main_dir.glob("*.java")}
    == benchmark_return_main_files
    and {path.name for path in benchmark_return_test_dir.glob("*.java")}
    == benchmark_return_test_files,
    "ADR-028 future comparative consumer must remain exact ADR-031",
)
sector_return_main_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn"
)
sector_return_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn"
)
sector_return_main_files = {
    "SectorReturnPolicyVersion.java",
    "SectorReturnInput.java",
    "SectorReturnResult.java",
    "SectorReturnCalculator.java",
}
sector_return_test_files = {
    "SectorReturnCalculatorGoldenTest.java",
}
sector_return_main_paths = {
    (sector_return_main_dir / name).resolve()
    for name in sector_return_main_files
}
sector_return_test_paths = {
    (sector_return_test_dir / name).resolve()
    for name in sector_return_test_files
}
require(
    {path.name for path in sector_return_main_dir.glob("*.java")}
    == sector_return_main_files
    and {path.name for path in sector_return_test_dir.glob("*.java")}
    == sector_return_test_files,
    "ADR-028 future comparative consumer must remain exact ADR-032",
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
benchmark_readiness_main_files = {
    "BenchmarkReturnReadinessPolicyVersion.java",
    "BenchmarkReturnReadinessRequest.java",
    "BenchmarkReturnReadinessResolution.java",
    "BenchmarkReturnReadinessResolver.java",
}
sector_readiness_main_files = {
    "SectorReturnReadinessPolicyVersion.java",
    "SectorReturnReadinessRequest.java",
    "SectorReturnReadinessResolution.java",
    "SectorReturnReadinessResolver.java",
}
benchmark_readiness_test_files = {
    "BenchmarkReturnReadinessResolverGoldenTest.java",
}
sector_readiness_test_files = {
    "SectorReturnReadinessResolverGoldenTest.java",
}
benchmark_readiness_main_paths = {
    (benchmark_readiness_main_dir / name).resolve()
    for name in benchmark_readiness_main_files
}
sector_readiness_main_paths = {
    (sector_readiness_main_dir / name).resolve()
    for name in sector_readiness_main_files
}
benchmark_readiness_test_paths = {
    (benchmark_readiness_test_dir / name).resolve()
    for name in benchmark_readiness_test_files
}
sector_readiness_test_paths = {
    (sector_readiness_test_dir / name).resolve()
    for name in sector_readiness_test_files
}
require(
    {path.name for path in benchmark_readiness_main_dir.glob("*.java")}
        == benchmark_readiness_main_files
    and {path.name for path in benchmark_readiness_test_dir.glob("*.java")}
        == benchmark_readiness_test_files
    and {path.name for path in sector_readiness_main_dir.glob("*.java")}
        == sector_readiness_main_files
    and {path.name for path in sector_readiness_test_dir.glob("*.java")}
        == sector_readiness_test_files,
    "ADR-028 future readiness consumers must remain exact independent ADR-033",
)
approved_sector_paths |= (
    sector_reference_main_paths | sector_reference_test_paths
    | sector_return_main_paths | sector_return_test_paths
    | benchmark_readiness_main_paths
    | benchmark_readiness_test_paths
    | sector_readiness_main_paths | sector_readiness_test_paths
)
product_paths = [Path("contracts/openapi.yaml")]
product_paths += list(Path("schemas").glob("*.json"))
product_paths += list(Path("fixtures/v1").glob("*.json"))
product_paths += [
    path for root in (
        Path("apps/api/src"), Path("apps/web/src"), Path("apps/web/e2e"),
    )
    for path in root.rglob("*") if path.is_file()
]
for product_path in product_paths:
    if product_path.resolve() in approved_sector_paths:
        continue
    try:
        source = product_path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    normalized_path = re.sub(
        r"[^a-z0-9]", "", product_path.as_posix().lower()
    )
    require(
        "sectortaxonomy" not in normalized_path
        and "sectorassignment" not in normalized_path
        and not any(runtime_marker in source
                    for runtime_marker in canonical_runtime_markers)
        and re.search(
            r"\b(?:SectorTaxonomy|SectorAssignment|"
            r"EconomicActivityTaxonomy|ProviderNodeMapping)\w*\b",
            source,
        ) is None,
        f"ADR-028 decision must not add runtime taxonomy/mapping: {product_path}",
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
legacy_protected_paths = [
    path for path in protected_paths
    if path.resolve() not in approved_sector_main_paths
    and path.resolve() not in benchmark_reference_main_paths
    and path.resolve() not in sector_reference_main_paths
    and path.resolve() not in benchmark_return_main_paths
    and path.resolve() not in sector_return_main_paths
    and path.resolve() not in benchmark_readiness_main_paths
    and path.resolve() not in sector_readiness_main_paths
]
protected_digest = hashlib.sha256()
for path in legacy_protected_paths:
    protected_digest.update(path.as_posix().encode("utf-8"))
    protected_digest.update(b"\0")
    protected_digest.update(path.read_bytes().replace(b"\r\n", b"\n"))
    protected_digest.update(b"\0")
require(
    len(legacy_protected_paths) == 195
    and protected_digest.hexdigest()
    == "562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640",
    "ADR-028 baseline changed outside the exact ADR-029 production slice",
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
            for path in root.rglob("*") if path.is_file()
        ),
        *(
            path for path in Path("apps/web").glob("*")
            if path.is_file() and path.name != "next-env.d.ts"
        ),
    },
    key=lambda path: path.as_posix(),
)
legacy_test_web_paths = [
    path for path in test_web_paths
    if path.resolve() not in approved_sector_test_paths
    and path.resolve() not in benchmark_reference_test_paths
    and path.resolve() not in sector_reference_test_paths
    and path.resolve() not in benchmark_return_test_paths
    and path.resolve() not in sector_return_test_paths
    and path.resolve() not in benchmark_readiness_test_paths
    and path.resolve() not in sector_readiness_test_paths
]
test_web_digest = hashlib.sha256()
for path in legacy_test_web_paths:
    test_web_digest.update(path.as_posix().encode("utf-8"))
    test_web_digest.update(b"\0")
    test_web_digest.update(path.read_bytes().replace(b"\r\n", b"\n"))
    test_web_digest.update(b"\0")
require(
    len(legacy_test_web_paths) == 198
    and test_web_digest.hexdigest()
    == "b132bd773926ecf57d87f2d6cb055670edb9e5c3aaa17ce050e7ed185c38bbed",
    "ADR-028 baseline changed outside the exact ADR-029 golden",
)

outcomes = json.loads(
    Path("fixtures/v1/call-outcomes.json").read_text(encoding="utf-8")
)
comparative_metrics = (
    "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
)
require(
    len(outcomes["outcomes"]) == 4
    and all(
        outcome[metric] is None
        for outcome in outcomes["outcomes"]
        for metric in comparative_metrics
    ),
    "ADR-028 taxonomy decision must not publish comparative metrics",
)

print(
    "Validated provider-neutral WSR Economic Activity V1, exact canonical "
    "taxonomy and PIT provider-node mapping-policy bytes, closed leaf "
    "targets, deterministic future mapping sets, licensing isolation, "
    "runtime firewall, and no product publication"
)
PYTHON
