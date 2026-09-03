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

def normalized_hash(path):
    return hashlib.sha256(
        path.read_bytes().replace(b"\r\n", b"\n")
    ).hexdigest()

def enum_values(source, enum_name):
    match = re.search(
        rf"(?:public\s+)?enum\s+{re.escape(enum_name)}\s*\{{"
        r"(?P<body>.*?)\}",
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
    "sectorassignment"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorassignment"
)
production_files = {
    "SectorAssignmentPolicyVersion.java",
    "SectorAssetClassificationEvidence.java",
    "SectorMembershipEvidence.java",
    "SectorMappingEvidence.java",
    "SectorAssignmentRequest.java",
    "SectorAssignmentResolution.java",
    "SectorAssignmentSelector.java",
}
test_files = {"SectorAssignmentSelectorGoldenTest.java"}
sector_reference_production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreferencepair"
)
sector_reference_production_files = {
    "SectorReferenceLevelPairPolicyVersion.java",
    "SectorReferenceIndexEvidence.java",
    "SectorReferenceLevelObservation.java",
    "SectorIndexDivisorContinuityEvidence.java",
    "SectorReferenceLevelPairRequest.java",
    "SectorReferenceLevelPairResolution.java",
    "SectorReferenceLevelPairSelector.java",
}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Sector-assignment production package must contain exactly seven files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Sector-assignment test package must contain exactly one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
expected_source_hashes = {
    "SectorAssetClassificationEvidence.java":
        "4f31638bcd53986dd2eaa9c04e284125173658424699112579e9c269538a1527",
    "SectorAssignmentPolicyVersion.java":
        "b436360680bdbe4f5436d345fe97cfcb4276b4aa339d1d39ea98580f556b9fdf",
    "SectorAssignmentRequest.java":
        "5816e95e2bd3ef4cec65f50923cbf086e2bae6519e660a8a6fe9ffa0b833852a",
    "SectorAssignmentResolution.java":
        "18bc7ccf4c8431fcbe8f3f92c4984a83c24891607cac19cdcbbf6b94f3fb4718",
    "SectorAssignmentSelector.java":
        "0f67b9568c8407697b2f38c7edf00296085627fd361b01c95921d34d0157f8db",
    "SectorMappingEvidence.java":
        "32de66811e0e3da88f5457cc5cfdb4978b57a13bca759abfee74aa71deba79a2",
    "SectorMembershipEvidence.java":
        "ff466993e1c5d2a3b9a0536a9764ec1877499ce1230f00be1add6a9a569f2e4b",
}
for name, expected_hash in expected_source_hashes.items():
    require(
        normalized_hash(production_dir / name) == expected_hash,
        f"Sector-assignment exact source changed: {name}",
    )

adr_path = Path(
    "decisions/ADR-029-point-in-time-explicit-wsr-sector-assignment-v1.md"
)
marker = (
    "ADR-029 freezes WSR sector assignment to explicit point-in-time "
    "membership and mapped provider-node evidence."
)
required_docs = (
    adr_path,
    Path("README.md"),
    Path("quality/P3_ACCEPTANCE.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
docs = {}
for doc_path in required_docs:
    require(doc_path.is_file(), f"Missing ADR-029 contract doc: {doc_path}")
    doc_source = doc_path.read_text(encoding="utf-8")
    docs[doc_path] = doc_source
    require(
        doc_source.count(marker) == 1,
        f"ADR-029 marker must occur exactly once: {doc_path}",
    )
adr = docs[adr_path]
require(
    adr.startswith(
        "# ADR-029 — Point-in-Time Explicit WSR Sector Assignment V1\n"
    )
    and "- Status: Accepted" in adr
    and "- Date: 2026-08-24" in adr,
    "ADR-029 title, accepted status, or date changed",
)

definition_matches = re.findall(
    r"```text\r?\n(?P<body>.*?)\r?\n```",
    adr,
    flags=re.DOTALL,
)
definitions = [body for body in definition_matches if body.startswith("{")]
require(
    len(definitions) == 1,
    "ADR-029 must contain exactly one canonical JSON definition",
)
definition = definitions[0]
policy_hash = (
    "52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c"
)
require(
    definition.isascii()
    and "\n" not in definition
    and "\r" not in definition
    and definition == definition.strip()
    and len(definition.encode("utf-8")) == 9307
    and hashlib.sha256(definition.encode("utf-8")).hexdigest()
    == policy_hash
    and adr.count("- Bytes: `9307`") == 1
    and adr.count(f"- SHA-256: `{policy_hash}`") == 1,
    "ADR-029 canonical definition bytes, length, hash, or metadata changed",
)
policy = json.loads(
    definition,
    object_pairs_hook=reject_duplicate_keys,
)
require(
    json.dumps(policy, ensure_ascii=True, separators=(",", ":"))
    == definition,
    "ADR-029 canonical JSON is not minimally serialized",
)
policy_source = sources["SectorAssignmentPolicyVersion.java"]
require(
    policy_source.count(definition) == 1
    and policy_source.count(policy_hash) == 1
    and "canonicalDefinitionUtf8()" in policy_source
    and "StandardCharsets.UTF_8" in policy_source,
    "Sector-assignment Java policy bytes/hash boundary changed",
)
require(
    enum_values(policy_source, "SectorAssignmentPolicyVersion") == [
        "POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1"
    ],
    "Sector-assignment policy enum changed",
)

taxonomy_hash = (
    "820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae"
)
mapping_policy_hash = (
    "ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77"
)
closed_leaf_ids = [
    "wsr-sector-digital-systems",
    "wsr-sector-connectivity-media",
    "wsr-sector-health-bioscience",
    "wsr-sector-financial-risk-services",
    "wsr-sector-consumer-essentials",
    "wsr-sector-consumer-choice-commerce",
    "wsr-sector-production-mobility",
    "wsr-sector-energy-systems",
    "wsr-sector-materials-resource-processing",
    "wsr-sector-essential-networks",
    "wsr-sector-property-built-environment",
    "wsr-sector-diversified-operations",
]
classification_fields = [
    "classificationEvidenceId", "providerEventId", "basis", "assetId",
    "assetType", "primaryVenueId", "primaryVenueCountryCode", "currency",
    "classificationSourceId", "classificationSourceRevision",
    "provenanceId", "effectiveInterval", "availableAt", "capturedAt",
]
membership_fields = [
    "membershipEvidenceId", "providerEventId", "basis", "assetId",
    "assetType", "primaryVenueId", "primaryVenueCountryCode", "currency",
    "providerId", "providerSchemeId", "providerSchemeRevision",
    "providerNodeId", "providerNodeLabel", "membershipSourceId",
    "membershipSourceRevision", "provenanceId", "effectiveInterval",
    "availableAt", "capturedAt",
]
mapping_fields = [
    "mappingEvidenceId", "providerEventId", "mappingPolicyVersion",
    "mappingPolicyDefinitionHash", "mappingSetId", "mappingSetVersion",
    "mappingSetDefinitionHash", "taxonomyId", "taxonomyVersion",
    "taxonomyDefinitionHash", "providerId", "providerSchemeId",
    "providerSchemeRevision", "providerNodeId", "providerNodeLabel",
    "providerNodeDefinition", "mappingDisposition", "mappingSourceId",
    "mappingSourceRevision", "provenanceId", "effectiveInterval",
    "availableAt", "capturedAt",
]
request_fields = [
    "policyVersion", "basis", "assetId", "evaluationAsOf",
    "mappingSetId", "mappingSetVersion", "mappingSetDefinitionHash",
    "classificationCandidates", "membershipCandidates",
    "mappingCandidates",
]
context_fields = [
    "policyVersion", "policyDefinitionHash", "basis", "assetId",
    "evaluationAsOf", "mappingSetId", "mappingSetVersion",
    "mappingSetDefinitionHash",
]
unavailable_reasons = [
    "CLASSIFICATION_MISSING_AS_OF",
    "CLASSIFICATION_BASIS_MISMATCH",
    "CLASSIFICATION_ASSET_MISMATCH",
    "CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH",
    "CLASSIFICATION_AMBIGUOUS",
    "MEMBERSHIP_MISSING_AS_OF",
    "MEMBERSHIP_BASIS_MISMATCH",
    "MEMBERSHIP_ASSET_MISMATCH",
    "MEMBERSHIP_ASSET_TYPE_MISMATCH",
    "MEMBERSHIP_PRIMARY_VENUE_MISMATCH",
    "MEMBERSHIP_PRIMARY_VENUE_COUNTRY_MISMATCH",
    "MEMBERSHIP_CURRENCY_MISMATCH",
    "MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH",
    "OUT_OF_SCOPE_MEMBERSHIP_CONFLICT",
    "MEMBERSHIP_AMBIGUOUS",
    "MAPPING_MISSING_AS_OF",
    "MAPPING_SET_ID_MISMATCH",
    "MAPPING_SET_VERSION_MISMATCH",
    "MAPPING_SET_DEFINITION_HASH_MISMATCH",
    "MAPPING_POLICY_VERSION_MISMATCH",
    "MAPPING_POLICY_DEFINITION_HASH_MISMATCH",
    "MAPPING_TAXONOMY_ID_MISMATCH",
    "MAPPING_TAXONOMY_VERSION_MISMATCH",
    "MAPPING_TAXONOMY_DEFINITION_HASH_MISMATCH",
    "MAPPING_PROVIDER_ID_MISMATCH",
    "MAPPING_PROVIDER_SCHEME_ID_MISMATCH",
    "MAPPING_PROVIDER_SCHEME_REVISION_MISMATCH",
    "MAPPING_PROVIDER_NODE_ID_MISMATCH",
    "MAPPING_EFFECTIVE_INTERVAL_MISMATCH",
    "MAPPING_MAPPED_DEFINITION_REQUIRED",
    "MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE",
    "MAPPING_CONFLICT",
    "MAPPING_AMBIGUOUS",
    "MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT",
    "MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD",
    "MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE",
]
require(
    policy["policyVersion"]
    == "POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1"
    and policy["requiredTaxonomyId"] == "wsr-economic-activity"
    and policy["requiredTaxonomyVersion"] == "1.0.0"
    and policy["requiredTaxonomyDefinitionHash"] == taxonomy_hash
    and policy["closedAssignableCanonicalNodeIds"] == closed_leaf_ids
    and policy["requiredMappingPolicyVersion"]
    == "POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1"
    and policy["requiredMappingPolicyDefinitionHash"]
    == mapping_policy_hash,
    "Sector-assignment ADR-028 taxonomy or mapping-policy binding changed",
)
require(
    policy["classificationEvidenceFields"] == classification_fields
    and policy["membershipEvidenceFields"] == membership_fields
    and policy["mappingEvidenceFields"] == mapping_fields
    and policy["requestFields"] == request_fields
    and policy["resolutionContextFields"] == context_fields
    and policy["resultVariants"] == {
        "Resolved": [
            "context", "classificationEvidence", "membershipEvidence",
            "mappingEvidence",
        ],
        "NotApplicable": [
            "context", "classificationEvidence", "reason",
        ],
        "Unavailable": ["context", "reason"],
    },
    "Sector-assignment evidence, request, context, or result shape changed",
)
require(
    policy["providerNodeDefinitionVariants"]
    == {"Recorded": ["value", "languageTag"], "NotPublished": []}
    and policy["mappingDispositionVariants"]
    == {"Mapped": ["canonicalNodeId"], "NotMapped": ["reason"]}
    and policy["notMappedReasons"] == [
        "NO_CANONICAL_EQUIVALENT", "PROVIDER_NODE_TOO_BROAD",
        "PROVIDER_DEFINITION_UNAVAILABLE",
    ]
    and policy["notApplicableReasons"] == ["NON_EQUITY"]
    and policy["unavailableReasons"] == unavailable_reasons,
    "Sector-assignment variants or reason order changed",
)
required_policy_scalars = {
    "pitPredicate":
        "availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf",
    "futureEvidenceRule":
        "INVISIBLE_TO_ALL_OUTPUT_REASON_CONFLICT_AND_CARDINALITY",
    "effectiveIntervalBoundary": "START_INCLUSIVE_END_EXCLUSIVE",
    "providerIdentityComparison":
        "EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_EQUALITY",
    "providerIdentityValidation":
        "NON_NULL_NON_EMPTY_NO_STRIP_NORMALIZATION_OR_CASE_FOLD",
    "inScopePredicate": "selectedClassification.assetType==EQUITY",
    "countryCurrencyApplicabilityRole":
        "PRESERVED_EVIDENCE_ONLY_NOT_SCOPE",
    "mappingSetIdentityBoundary":
        "CALLER_SUPPLIED_IDENTITY_ECHOED_AND_ROW_MATCHED_SELECTOR_DOES_"
        "NOT_COMPUTE_MANIFEST_HASH_OR_ATTEST_ENTRY_MANIFEST_CORRELATION",
    "mappedDefinitionRequirement":
        "MAPPED_REQUIRES_RECORDED_PROVIDER_NODE_DEFINITION",
    "equalDuplicateRule": "AMBIGUOUS_NO_DEDUPLICATION",
    "candidateOrderRule": "ORDER_INDEPENDENT",
    "mappingSetManifestVerification":
        "ABSENT_CALLER_ATTESTED_SEPARATE_BOUNDARY",
    "lifecycleMapping": "ABSENT",
    "calculatorInvocation": "ABSENT",
    "providerIntegration": "ABSENT",
    "actualProviderMappingSet": "ABSENT",
    "fallbackBehavior": "ABSENT",
}
require(
    all(policy[key] == value
        for key, value in required_policy_scalars.items())
    and policy["providerIdentity"] == [
        "providerId", "providerSchemeId", "providerSchemeRevision",
        "providerNodeId",
    ]
    and policy["evaluationPrecedence"][-1] == "RESOLVE",
    "Sector-assignment PIT, identity, attestation, or firewall changed",
)

mapping_source = sources["SectorMappingEvidence.java"]
resolution_source = sources["SectorAssignmentResolution.java"]
selector_source = sources["SectorAssignmentSelector.java"]
classification_source = sources[
    "SectorAssetClassificationEvidence.java"
]
membership_source = sources["SectorMembershipEvidence.java"]
request_source = sources["SectorAssignmentRequest.java"]
require(
    enum_values(mapping_source, "NotMappedReason") == [
        "NO_CANONICAL_EQUIVALENT", "PROVIDER_NODE_TOO_BROAD",
        "PROVIDER_DEFINITION_UNAVAILABLE",
    ]
    and enum_values(resolution_source, "NotApplicableReason")
    == ["NON_EQUITY"]
    and enum_values(resolution_source, "UnavailableReason")
    == unavailable_reasons,
    "Sector-assignment Java reason vocabularies changed",
)

compact_sources = {
    name: re.sub(r"\s+", "", source)
    for name, source in sources.items()
}
require(
    "publicsealedinterfaceEffectiveIntervalEnd"
    "permitsOpenEnded,EndsAtExclusive"
    in compact_sources["SectorAssetClassificationEvidence.java"]
    and "publicrecordOpenEnded()implementsEffectiveIntervalEnd"
    in compact_sources["SectorAssetClassificationEvidence.java"]
    and "publicrecordEndsAtExclusive(Instantvalue)"
    "implementsEffectiveIntervalEnd"
    in compact_sources["SectorAssetClassificationEvidence.java"]
    and "publicsealedinterfaceProviderNodeDefinition"
    "permitsRecorded,NotPublished"
    in compact_sources["SectorMappingEvidence.java"]
    and "publicsealedinterfaceMappingDispositionpermitsMapped,NotMapped"
    in compact_sources["SectorMappingEvidence.java"]
    and "publicsealedinterfaceSectorAssignmentResolution"
    "permitsSectorAssignmentResolution.Resolved,"
    "SectorAssignmentResolution.NotApplicable,"
    "SectorAssignmentResolution.Unavailable"
    in compact_sources["SectorAssignmentResolution.java"],
    "Sector-assignment sealed interval, mapping, or result surface changed",
)
require(
    "evaluationAsOf.isBefore(basis.eventTime())" in request_source
    and "List.copyOf(classificationCandidates)" in request_source
    and "List.copyOf(membershipCandidates)" in request_source
    and "List.copyOf(mappingCandidates)" in request_source
    and "!availableAt.isAfter(evaluationAsOf)"
    in compact_sources["SectorAssignmentSelector.java"]
    and "!capturedAt.isAfter(evaluationAsOf)"
    in compact_sources["SectorAssignmentSelector.java"]
    and "!instant.isBefore(startsAtInclusive)" in classification_source
    and "instant.isBefore(((EndsAtExclusive)end).value())"
    in compact_sources["SectorAssetClassificationEvidence.java"],
    "Sector-assignment request immutability, PIT filter, or interval changed",
)
gate_positions = [selector_source.find(reason)
                  for reason in unavailable_reasons]
require(
    all(position >= 0 for position in gate_positions)
    and gate_positions == sorted(gate_positions)
    and len(set(gate_positions)) == len(gate_positions),
    "Sector-assignment selector gate precedence changed",
)
selector_compact = compact_sources["SectorAssignmentSelector.java"]
require(
    "mappings.stream().skip(1).anyMatch(candidate->"
    "!candidate.mappingDisposition().equals("
    "first.mappingDisposition()))" in selector_compact
    and "mappings.size()>1" in selector_compact
    and "first.mappingDisposition()instanceofNotMappednotMapped"
    in selector_compact
    and selector_source.find("MAPPING_CONFLICT")
    < selector_source.find("MAPPING_AMBIGUOUS")
    < selector_source.find(
        "MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT"
    )
    and "providerNodeLabel()" not in selector_source,
    "Sector-assignment conflict, ambiguity, not-mapped, or label rule changed",
)
provider_identity_helper = re.search(
    r"static void requireProviderIdentityText\(.*?\n    \}",
    classification_source,
    flags=re.DOTALL,
)
require(
    provider_identity_helper is not None
    and ".isEmpty()" in provider_identity_helper.group(0)
    and not any(token in provider_identity_helper.group(0) for token in (
        ".strip", "Normalizer", "toLowerCase", "toUpperCase", ".isBlank",
    ))
    and all(
        f"candidate.{field}().equals(membership.{field}())"
        in selector_compact
        for field in (
            "providerId", "providerSchemeId", "providerSchemeRevision",
            "providerNodeId",
        )
    ),
    "Provider identity must remain exact, unnormalized, and label-free",
)

basis_consumers = {
    name for name, source in sources.items() if "OutcomeBasis" in source
}
require(
    basis_consumers == {
        "SectorAssetClassificationEvidence.java",
        "SectorMembershipEvidence.java",
        "SectorAssignmentRequest.java",
        "SectorAssignmentResolution.java",
    }
    and "OutcomeBasis" not in mapping_source
    and all(
        "domain.outcome.benchmarkassignment" not in source
        for source in sources.values()
    ),
    "Sector assignment must own exactly four basis consumers and no benchmark type",
)
forbidden_source_tokens = (
    "org.springframework", "jakarta.", "com.fasterxml.jackson",
    "java.sql", "java.net", ".infrastructure.", ".application.",
    "Repository", "HttpClient", "WebClient", "Clock", "Random",
    "double", "float", "OutcomeEvaluationStatus", "CallOutcome",
    "AssetReturn", "BenchmarkAssignment", "BenchmarkReturn",
    "SectorReturn", "MarketSnapshot",
)
for name, source in sources.items():
    require(
        not any(token in source for token in forbidden_source_tokens),
        f"Sector-assignment source crosses its pure boundary: {name}",
    )

api_main_dir = Path("apps/api/src/main/java")
approved_paths = {
    (production_dir / name).resolve() for name in production_files
}
approved_reference_consumers = {
    (sector_reference_production_dir
     / "SectorReferenceLevelPairRequest.java").resolve(): {
        "SectorAssignmentPolicyVersion",
        "SectorAssetClassificationEvidence",
        "SectorAssignmentResolution",
    },
    (sector_reference_production_dir
     / "SectorReferenceLevelPairResolution.java").resolve(): {
        "SectorMappingEvidence", "SectorAssignmentResolution",
    },
    (sector_reference_production_dir
     / "SectorReferenceLevelPairSelector.java").resolve(): {
        "SectorMappingEvidence", "SectorAssignmentResolution",
    },
}
type_markers = tuple(name.removesuffix(".java") for name in production_files)
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in approved_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    actual_references = {
        type_name for type_name in type_markers
        if re.search(rf"\b{re.escape(type_name)}\b", other_source)
    }
    if other_path.resolve() in approved_reference_consumers:
        require(
            actual_references
            == approved_reference_consumers[other_path.resolve()]
            and "domain.outcome.sectorassignment" in other_source,
            f"ADR-030 sector-assignment consumer edge changed: {other_path}",
        )
        continue
    require(
        "domain.outcome.sectorassignment" not in other_source
        and not actual_references,
        f"Sector-assignment leaf must not be reverse-wired: {other_path}",
    )

golden_path = test_dir / "SectorAssignmentSelectorGoldenTest.java"
golden = golden_path.read_text(encoding="utf-8")
require(
    normalized_hash(golden_path)
    == "3ee8bc735a4c49183a12fb77ff6d0b8cdfd0af6edc493c7b584921fd851e29f7",
    "Sector-assignment golden exact source changed",
)
required_golden_methods = (
    "canonicalPolicyDefinitionHasExactUtf8BytesIndependentHashAndDefensiveReads",
    "publicPolicyEvidenceRequestAndResultSurfacesRemainExactlyClosed",
    "resolvesEveryClosedCanonicalLeaf",
    "resolvesOneExactOriginalAssignmentAtInclusivePitAndIntervalBoundaries",
    "correctionBasisIsAnIndependentCompleteIdentityNotAnEventTimeShortcut",
    "effectiveIntervalsAreStartInclusiveEndExclusiveAndExplicitlyOpenEnded",
    "futureClassificationCandidatesAreIdenticalToAbsentAndInvisibleToReasoning",
    "futureMembershipCandidatesAreIdenticalToAbsentAndInvisibleToReasoning",
    "futureMappingCandidatesAreIdenticalToAbsentAndInvisibleToReasoning",
    "everyVisibleClassificationMismatchUsesItsExactReason",
    "everyVisibleMembershipMismatchUsesItsExactReason",
    "everyVisibleCommonMappingMismatchUsesItsExactReason",
    "unequalVisibleMappingDispositionsAreConflictsBeforeAmbiguity",
    "providerIdentityIsExactRawUnicodeWhileLabelsRemainPreservedNonKeys",
    "callerAttestedMappingSetIdentityIsEchoedButItsDigestIsNotRecomputed",
    "requestDefensivelyCopiesListsAndRejectsEveryMissingPublicInput",
    "directResultConstructorsRejectMissingFutureAndContradictoryComponents",
    "replayIsIndependentOfInputOrderJvmDefaultsAndPriorCalls",
)
require(
    all(method in golden for method in required_golden_methods)
    and len(re.findall(r"(?m)^\s+@Test\s*$", golden)) == 25
    and len(re.findall(r"(?m)^\s+@ParameterizedTest(?:\(|\s*$)", golden))
    == 16
    and "CANONICAL_LEAVES" in golden
    and "MAPPING_SET_HASH" in golden
    and "TimeZone.setDefault" in golden
    and "Locale.setDefault" in golden,
    "Sector-assignment 134-vector golden contract changed",
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
    "Sector-assignment protected production baseline changed",
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
    "Sector-assignment API-test/web baseline changed",
)

product_paths = [Path("contracts/openapi.yaml")]
product_paths += list(Path("schemas").glob("*.json"))
product_paths += list(Path("fixtures/v1").glob("*.json"))
product_paths += [
    path for root in (
        Path("apps/api/src/main/resources"),
        Path("apps/web/src"), Path("apps/web/e2e"),
    )
    for path in root.rglob("*") if path.is_file()
]
runtime_markers = (
    policy["policyVersion"], "domain.outcome.sectorassignment",
)
for product_path in product_paths:
    try:
        product_source = product_path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    require(
        not any(runtime_marker in product_source
                for runtime_marker in runtime_markers),
        f"Sector assignment must not be published in product data: {product_path}",
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
    "Sector assignment must not publish comparative metrics",
)

print(
    "Validated exact point-in-time WSR sector assignment, independent "
    "classification/membership/mapping evidence, closed ADR-028 taxonomy "
    "binding, 36-reason precedence, caller-attested mapping-set isolation, "
    "134-vector golden, reverse firewall, and no product publication"
)
PYTHON
