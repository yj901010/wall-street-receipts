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

def digest(paths):
    result = hashlib.sha256()
    for path in sorted(paths, key=lambda value: value.as_posix()):
        result.update(path.as_posix().encode("utf-8"))
        result.update(b"\0")
        result.update(path.read_bytes().replace(b"\r\n", b"\n"))
        result.update(b"\0")
    return result.hexdigest()

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

def record_fields(source, record_name):
    match = re.search(
        rf"(?:public\s+)?record\s+{re.escape(record_name)}\s*\("
        r"(?P<body>.*?)\)\s*(?:implements\s+[^\{]+)?\{",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing record {record_name}")
    return re.findall(
        r"\b([a-z][A-Za-z0-9_]*)\s*(?:,|$)",
        match.group("body"),
        flags=re.MULTILINE,
    )

def java_definition(source):
    match = re.search(
        r"private static final String CANONICAL_DEFINITION\s*=\s*"
        r"(?P<body>.*?)\s*;\s*private static final String DEFINITION_HASH",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, "Missing Java canonical definition")
    literals = re.findall(
        r'"(?:\\.|[^"\\])*"', match.group("body")
    )
    return "".join(json.loads(literal) for literal in literals)

def java_logic(source):
    return re.sub(
        r'//.*?$|/\*.*?\*/|"(?:\\.|[^"\\])*"',
        "",
        source,
        flags=re.MULTILINE | re.DOTALL,
    )

marker = (
    "ADR-030 resolves benchmark and sector reference-level pairs "
    "independently from explicit point-in-time provider-published "
    "price-index evidence over the exact basis-event-to-asset-endpoint "
    "UTC interval."
)
adr_path = Path(
    "decisions/ADR-030-point-in-time-independent-benchmark-sector-"
    "reference-level-pairs-v1.md"
)
marker_paths = (
    adr_path,
    Path("README.md"),
    Path("IMPLEMENTATION_LOG.md"),
    Path("quality/P3_ACCEPTANCE.md"),
)
for path in marker_paths:
    require(path.is_file(), f"Missing ADR-030 contract document: {path}")
    require(
        path.read_text(encoding="utf-8").count(marker) == 1,
        f"ADR-030 marker must occur exactly once: {path}",
    )
adr_source = adr_path.read_text(encoding="utf-8")
require(
    adr_source.startswith(
        "# ADR-030 — Point-in-Time Independent Benchmark/Sector "
        "Reference-Level Pairs V1\n\n- Status: Accepted\n"
        "- Date: 2026-08-24\n"
    ),
    "ADR-030 title, accepted status, or date changed",
)

benchmark_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreferencepair"
)
sector_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreferencepair"
)
benchmark_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreferencepair"
)
sector_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreferencepair"
)
benchmark_files = {
    "BenchmarkReferenceLevelPairPolicyVersion.java",
    "BenchmarkReferenceIndexEvidence.java",
    "BenchmarkReferenceLevelObservation.java",
    "BenchmarkIndexDivisorContinuityEvidence.java",
    "BenchmarkReferenceLevelPairRequest.java",
    "BenchmarkReferenceLevelPairResolution.java",
    "BenchmarkReferenceLevelPairSelector.java",
}
sector_files = {
    "SectorReferenceLevelPairPolicyVersion.java",
    "SectorReferenceIndexEvidence.java",
    "SectorReferenceLevelObservation.java",
    "SectorIndexDivisorContinuityEvidence.java",
    "SectorReferenceLevelPairRequest.java",
    "SectorReferenceLevelPairResolution.java",
    "SectorReferenceLevelPairSelector.java",
}
benchmark_test_files = {
    "BenchmarkReferenceLevelPairSelectorGoldenTest.java"
}
sector_test_files = {
    "SectorReferenceLevelPairSelectorGoldenTest.java"
}
require(
    {path.name for path in benchmark_dir.glob("*.java")}
    == benchmark_files
    and {path.name for path in sector_dir.glob("*.java")}
    == sector_files,
    "ADR-030 production surface must remain exactly seven plus seven",
)
require(
    {path.name for path in benchmark_test_dir.glob("*.java")}
    == benchmark_test_files
    and {path.name for path in sector_test_dir.glob("*.java")}
    == sector_test_files,
    "ADR-030 test surface must remain exactly one golden per leg",
)

benchmark_sources = {
    name: (benchmark_dir / name).read_text(encoding="utf-8")
    for name in benchmark_files
}
sector_sources = {
    name: (sector_dir / name).read_text(encoding="utf-8")
    for name in sector_files
}
expected_source_hashes = {
    "BenchmarkIndexDivisorContinuityEvidence.java":
        "51c60616a589289b1e5222a6383151ec5f1e92af19b1e869312b3bf67f295574",
    "BenchmarkReferenceIndexEvidence.java":
        "5c1eb28b45b5420fc5c1a73148233ed2540b6f95253a98134f60528a300de26b",
    "BenchmarkReferenceLevelObservation.java":
        "54acc7e4741a4c95e2558aa8f51f012aaf03914faeacb9f372cffef8812f938d",
    "BenchmarkReferenceLevelPairPolicyVersion.java":
        "fd05f304fd34f25e3d943364439c9d24d78b70dcce08ffb4094aecfcc06ba44d",
    "BenchmarkReferenceLevelPairRequest.java":
        "66555e8f16a1a38f72ee09341aa8291af0d724d682795084e545e49fd53e7b85",
    "BenchmarkReferenceLevelPairResolution.java":
        "f6bf4fab79e947ff68d7a9ff769c8cf38045f28529da7ac7ce293919e8ae54ce",
    "BenchmarkReferenceLevelPairSelector.java":
        "25d8d77ab2f5dd3ac0073a3142cc79c69c60a2cf7cf131df5c1365da900e5749",
    "SectorIndexDivisorContinuityEvidence.java":
        "0d57acfcaa61c017cab0b052a49a07214a03ebbc52ed8b22d3e9fb22c8b59d1f",
    "SectorReferenceIndexEvidence.java":
        "1288696dd2dc5d113b6321fbbc7eccd4691533c604be8a43d380ae8904117ff3",
    "SectorReferenceLevelObservation.java":
        "4abc204fc04973e07534133743cc2ef1cc1b47bfb741fa40bec67d2bb89a8b85",
    "SectorReferenceLevelPairPolicyVersion.java":
        "f5c52e3499ad5e7019b458093994c8ce8d79a9e88a29836c7fb786aad00a275d",
    "SectorReferenceLevelPairRequest.java":
        "fb7e794bfe0cf9931f797c0c05966a66da149d4c943f57b58d19f42ddac305a2",
    "SectorReferenceLevelPairResolution.java":
        "17ae09df9d92a104eb3b9a5aaa34ac851ef7af93aeb95da88596cf00bf57d8be",
    "SectorReferenceLevelPairSelector.java":
        "f1b45c5e5e795d7a1cad07329a342bdb423263c0c1437003f9ea81b299a089d3",
}
all_sources = benchmark_sources | sector_sources
require(
    set(all_sources) == set(expected_source_hashes)
    and all(
        normalized_hash(
            (benchmark_dir if name.startswith("Benchmark") else sector_dir)
            / name
        ) == expected
        for name, expected in expected_source_hashes.items()
    ),
    "ADR-030 exact normalized production source changed",
)

blocks = re.findall(r"```text\n(\{[^\n]*\})\n```", adr_source)
require(len(blocks) == 2, "ADR-030 must contain exactly two policy blocks")
expected_policy_metadata = (
    (
        "benchmark",
        benchmark_sources[
            "BenchmarkReferenceLevelPairPolicyVersion.java"
        ],
        "POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1",
        9342,
        "2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d",
    ),
    (
        "sector",
        sector_sources["SectorReferenceLevelPairPolicyVersion.java"],
        "POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1",
        9806,
        "4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a",
    ),
)
policies = {}
for block, metadata in zip(blocks, expected_policy_metadata):
    leg, policy_source, policy_version, byte_count, expected_hash = metadata
    encoded = block.encode("ascii")
    policy = json.loads(block, object_pairs_hook=reject_duplicate_keys)
    require(
        len(encoded) == byte_count
        and hashlib.sha256(encoded).hexdigest() == expected_hash
        and policy["policyVersion"] == policy_version
        and json.dumps(
            policy, ensure_ascii=True, separators=(",", ":")
        ) == block,
        f"ADR-030 {leg} canonical policy bytes or identity changed",
    )
    require(
        java_definition(policy_source) == block
        and re.search(
            rf'private static final String DEFINITION_HASH\s*=\s*'
            rf'"{expected_hash}"',
            policy_source,
        )
        and enum_values(policy_source, policy_source.split("public enum ", 1)[1]
                        .split()[0]) == [policy_version],
        f"ADR-030 {leg} Java policy bytes or hash changed",
    )
    policies[leg] = policy

benchmark_reasons = """
ENDPOINT_NOT_REACHED_AS_OF
REFERENCE_INDEX_MISSING_AS_OF
REFERENCE_ASSIGNMENT_EVIDENCE_LINK_MISMATCH
REFERENCE_BENCHMARK_ASSET_ID_MISMATCH
REFERENCE_BENCHMARK_ASSET_TYPE_MISMATCH
REFERENCE_CURRENCY_MISMATCH
REFERENCE_KIND_MISMATCH
REFERENCE_EFFECTIVE_INTERVAL_MISMATCH
REFERENCE_INDEX_AMBIGUOUS
BASIS_LEVEL_MISSING_AS_OF
BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
BASIS_BENCHMARK_ASSET_MISMATCH
BASIS_REFERENCE_PROVIDER_MISMATCH
BASIS_REFERENCE_INDEX_MISMATCH
BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
BASIS_REFERENCE_KIND_MISMATCH
BASIS_CURRENCY_MISMATCH
BASIS_CALCULATION_VENUE_MISMATCH
BASIS_CALENDAR_MISMATCH
BASIS_LEVEL_SOURCE_MISMATCH
BASIS_OBSERVED_AT_MISMATCH
BASIS_LEVEL_FIELD_MISMATCH
BASIS_LEVEL_AMBIGUOUS
ENDPOINT_LEVEL_MISSING_AS_OF
ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
ENDPOINT_BENCHMARK_ASSET_MISMATCH
ENDPOINT_REFERENCE_PROVIDER_MISMATCH
ENDPOINT_REFERENCE_INDEX_MISMATCH
ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
ENDPOINT_REFERENCE_KIND_MISMATCH
ENDPOINT_CURRENCY_MISMATCH
ENDPOINT_CALCULATION_VENUE_MISMATCH
ENDPOINT_CALENDAR_MISMATCH
ENDPOINT_LEVEL_SOURCE_MISMATCH
ENDPOINT_OBSERVED_AT_MISMATCH
ENDPOINT_LEVEL_FIELD_MISMATCH
ENDPOINT_LEVEL_AMBIGUOUS
DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF
DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
DIVISOR_BENCHMARK_ASSET_MISMATCH
DIVISOR_REFERENCE_PROVIDER_MISMATCH
DIVISOR_REFERENCE_INDEX_MISMATCH
DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
DIVISOR_REFERENCE_KIND_MISMATCH
DIVISOR_CURRENCY_MISMATCH
DIVISOR_CALCULATION_VENUE_MISMATCH
DIVISOR_CALENDAR_MISMATCH
DIVISOR_CONTINUITY_SOURCE_MISMATCH
DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH
DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH
DIVISOR_COVERAGE_MISMATCH
DIVISOR_CONTINUITY_UNAVAILABLE
DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS
""".split()
sector_reasons = """
ENDPOINT_NOT_REACHED_AS_OF
REFERENCE_INDEX_MISSING_AS_OF
REFERENCE_MAPPING_EVIDENCE_LINK_MISMATCH
REFERENCE_TAXONOMY_ID_MISMATCH
REFERENCE_TAXONOMY_VERSION_MISMATCH
REFERENCE_TAXONOMY_DEFINITION_HASH_MISMATCH
REFERENCE_CANONICAL_NODE_ID_MISMATCH
REFERENCE_ASSET_TYPE_MISMATCH
REFERENCE_CURRENCY_MISMATCH
REFERENCE_KIND_MISMATCH
REFERENCE_EFFECTIVE_INTERVAL_MISMATCH
REFERENCE_INDEX_AMBIGUOUS
BASIS_LEVEL_MISSING_AS_OF
BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
BASIS_REFERENCE_ASSET_MISMATCH
BASIS_REFERENCE_PROVIDER_MISMATCH
BASIS_REFERENCE_INDEX_MISMATCH
BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
BASIS_REFERENCE_KIND_MISMATCH
BASIS_CURRENCY_MISMATCH
BASIS_CALCULATION_VENUE_MISMATCH
BASIS_CALENDAR_MISMATCH
BASIS_LEVEL_SOURCE_MISMATCH
BASIS_OBSERVED_AT_MISMATCH
BASIS_LEVEL_FIELD_MISMATCH
BASIS_LEVEL_AMBIGUOUS
ENDPOINT_LEVEL_MISSING_AS_OF
ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
ENDPOINT_REFERENCE_ASSET_MISMATCH
ENDPOINT_REFERENCE_PROVIDER_MISMATCH
ENDPOINT_REFERENCE_INDEX_MISMATCH
ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
ENDPOINT_REFERENCE_KIND_MISMATCH
ENDPOINT_CURRENCY_MISMATCH
ENDPOINT_CALCULATION_VENUE_MISMATCH
ENDPOINT_CALENDAR_MISMATCH
ENDPOINT_LEVEL_SOURCE_MISMATCH
ENDPOINT_OBSERVED_AT_MISMATCH
ENDPOINT_LEVEL_FIELD_MISMATCH
ENDPOINT_LEVEL_AMBIGUOUS
DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF
DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
DIVISOR_REFERENCE_ASSET_MISMATCH
DIVISOR_REFERENCE_PROVIDER_MISMATCH
DIVISOR_REFERENCE_INDEX_MISMATCH
DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
DIVISOR_REFERENCE_KIND_MISMATCH
DIVISOR_CURRENCY_MISMATCH
DIVISOR_CALCULATION_VENUE_MISMATCH
DIVISOR_CALENDAR_MISMATCH
DIVISOR_CONTINUITY_SOURCE_MISMATCH
DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH
DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH
DIVISOR_COVERAGE_MISMATCH
DIVISOR_CONTINUITY_UNAVAILABLE
DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS
""".split()
anchor_reasons = [
    "CATALOG_NOT_KNOWN_AS_OF",
    "CATALOG_EVIDENCE_MISMATCH",
    "BINDING_NOT_KNOWN_AS_OF",
]
reference_kinds = [
    "PROVIDER_PUBLISHED_PRICE_INDEX",
    "PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX",
    "NON_PROVIDER_PUBLISHED_PRICE_INDEX",
    "EXCHANGE_TRADED_FUND",
    "CURRENT_CONSTITUENT_BASKET",
    "MARKET_CAP_PROXY",
    "PROVIDER_RETURN_FIELD",
    "UNKNOWN",
]
level_fields = [
    "PROVIDER_PUBLISHED_INDEX_LEVEL",
    "PROVIDER_PUBLISHED_RETURN",
    "EXCHANGE_TRADED_FUND_MARKET_PRICE",
    "EXCHANGE_TRADED_FUND_NAV",
    "DERIVED_PROXY_LEVEL",
    "UNKNOWN",
]
divisor_states = [
    "PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED",
    "DIVISOR_DISCONTINUITY",
    "NOT_ATTESTED",
    "UNKNOWN",
]
result_variants = {
    "Resolved": [
        "context", "referenceIndexEvidence", "basisLevelObservation",
        "endpointLevelObservation", "divisorContinuityEvidence",
    ],
    "NotApplicable": ["context"],
    "AssignmentUnavailable": ["context"],
    "EndpointAnchorUnavailable": ["context", "reason"],
    "EvidenceUnavailable": ["context", "reason"],
}
expected_precedence = [
    "ASSIGNMENT_NOT_APPLICABLE",
    "ASSIGNMENT_UNAVAILABLE",
    "ENDPOINT_ANCHOR_UNAVAILABLE",
    "LOCAL_UNAVAILABLE_REASONS_IN_DECLARED_ORDER",
    "RESOLVE",
]

leg_contracts = (
    {
        "leg": "benchmark",
        "prefix": "Benchmark",
        "sources": benchmark_sources,
        "policy": policies["benchmark"],
        "reasons": benchmark_reasons,
        "reference": "BenchmarkReferenceIndexEvidence.java",
        "level": "BenchmarkReferenceLevelObservation.java",
        "continuity": "BenchmarkIndexDivisorContinuityEvidence.java",
        "request": "BenchmarkReferenceLevelPairRequest.java",
        "resolution": "BenchmarkReferenceLevelPairResolution.java",
        "selector": "BenchmarkReferenceLevelPairSelector.java",
    },
    {
        "leg": "sector",
        "prefix": "Sector",
        "sources": sector_sources,
        "policy": policies["sector"],
        "reasons": sector_reasons,
        "reference": "SectorReferenceIndexEvidence.java",
        "level": "SectorReferenceLevelObservation.java",
        "continuity": "SectorIndexDivisorContinuityEvidence.java",
        "request": "SectorReferenceLevelPairRequest.java",
        "resolution": "SectorReferenceLevelPairResolution.java",
        "selector": "SectorReferenceLevelPairSelector.java",
    },
)
for contract in leg_contracts:
    leg = contract["leg"]
    prefix = contract["prefix"]
    sources = contract["sources"]
    policy = contract["policy"]
    reference_source = sources[contract["reference"]]
    level_source = sources[contract["level"]]
    continuity_source = sources[contract["continuity"]]
    request_source = sources[contract["request"]]
    resolution_source = sources[contract["resolution"]]
    selector_source = sources[contract["selector"]]
    require(
        policy["unavailableReasons"] == contract["reasons"]
        and enum_values(resolution_source, "UnavailableReason")
        == contract["reasons"]
        and policy["endpointAnchorUnavailableReasons"] == anchor_reasons
        and enum_values(
            resolution_source, "EndpointAnchorUnavailableReason"
        ) == anchor_reasons,
        f"ADR-030 {leg} unavailable reason order changed",
    )
    require(
        policy["referenceIndexKinds"] == reference_kinds
        and enum_values(reference_source, "ReferenceIndexKind")
        == reference_kinds
        and policy["referenceLevelFields"] == level_fields
        and enum_values(level_source, "ReferenceLevelField")
        == level_fields
        and policy["divisorContinuityStates"] == divisor_states
        and enum_values(continuity_source, "DivisorContinuity")
        == divisor_states,
        f"ADR-030 {leg} reference kind, field, or continuity enum changed",
    )
    require(
        record_fields(reference_source, f"{prefix}ReferenceIndexEvidence")
        == policy["referenceIndexEvidenceFields"]
        and record_fields(reference_source, "EffectiveInterval")
        == policy["effectiveIntervalFields"]
        and record_fields(reference_source, "OpenEnded") == []
        and record_fields(reference_source, "EndsAtExclusive") == ["value"]
        and record_fields(
            level_source, f"{prefix}ReferenceLevelObservation"
        ) == policy["referenceLevelObservationFields"]
        and record_fields(
            continuity_source,
            f"{prefix}IndexDivisorContinuityEvidence",
        ) == policy["divisorContinuityEvidenceFields"]
        and record_fields(
            request_source, f"{prefix}ReferenceLevelPairRequest"
        ) == policy["requestFields"]
        and record_fields(resolution_source, "ResolutionContext")
        == policy["resolutionContextFields"],
        f"ADR-030 {leg} evidence, request, or context shape changed",
    )
    require(
        policy["resultVariants"] == result_variants
        and all(
            record_fields(resolution_source, variant) == fields
            for variant, fields in result_variants.items()
        ),
        f"ADR-030 {leg} sealed result topology changed",
    )
    reference_end = 9 if leg == "benchmark" else 12
    reason_groups = (
        contract["reasons"][1:reference_end],
        [reason for reason in contract["reasons"]
         if reason.startswith("BASIS_")
         and not reason.endswith("MISSING_AS_OF")
         and not reason.endswith("AMBIGUOUS")],
        [reason for reason in contract["reasons"]
         if reason.startswith("ENDPOINT_")
         and reason != "ENDPOINT_NOT_REACHED_AS_OF"
         and not reason.endswith("MISSING_AS_OF")
         and not reason.endswith("AMBIGUOUS")],
        [reason for reason in contract["reasons"]
         if reason.startswith("DIVISOR_")
         and not reason.endswith("MISSING_AS_OF")
         and not reason.endswith("AMBIGUOUS")],
    )
    require(
        all(
            (positions := [selector_source.find(reason)
                           for reason in group])
            and all(position >= 0 for position in positions)
            and positions == sorted(positions)
            and len(positions) == len(set(positions))
            for group in reason_groups
        ),
        f"ADR-030 {leg} selector reason precedence changed",
    )
    anchor_positions = [
        request_source.find(reason) for reason in anchor_reasons
    ]
    require(
        anchor_positions == sorted(anchor_positions)
        and all(position >= 0 for position in anchor_positions)
        and ".reason()" not in request_source
        and ".reason()" not in selector_source,
        f"ADR-030 {leg} anchor must derive from facts, not a reason label",
    )
    request_compact = re.sub(r"\s+", "", request_source)
    selector_compact = re.sub(r"\s+", "", selector_source)
    require(
        "catalog.availableAt().isAfter(context.evaluationAsOf())"
        in request_compact
        and "catalog.capturedAt().isAfter(context.evaluationAsOf())"
        in request_compact
        and "!catalog.calendarId().equals(horizon.calendarId())"
        in request_compact
        and "!catalog.catalogRevision().equals(horizon.catalogRevision())"
        in request_compact
        and "binding.availableAt().isAfter(context.evaluationAsOf())"
        in request_compact
        and "binding.capturedAt().isAfter(context.evaluationAsOf())"
        in request_compact
        and "List.copyOf(values)" in request_compact,
        f"ADR-030 {leg} anchor facts or defensive request copy changed",
    )
    assignment_name = (
        "BenchmarkAssignmentResolution"
        if leg == "benchmark" else "SectorAssignmentResolution"
    )
    require(
        selector_source.find(
            f"instanceof {assignment_name}.NotApplicable"
        )
        < selector_source.find(
            f"instanceof {assignment_name}.Unavailable"
        )
        < selector_source.find("endpointAnchorUnavailableReason")
        < selector_source.find("ENDPOINT_NOT_REACHED_AS_OF")
        < selector_source.find("REFERENCE_INDEX_MISSING_AS_OF")
        and "return!availableAt.isAfter(evaluationAsOf)&&"
        "!capturedAt.isAfter(evaluationAsOf);" in selector_compact
        and ".effectiveInterval().contains(basis.eventTime())"
        in selector_compact
        and ".effectiveInterval().contains(endpoint)" in selector_compact
        and selector_compact.count(".size()>1") == 4,
        f"ADR-030 {leg} assignment, anchor, PIT, interval, or duplicate precedence changed",
    )
    require(
        policy["requiredReferenceIndexKind"]
        == "PROVIDER_PUBLISHED_PRICE_INDEX"
        and policy["requiredReferenceLevelField"]
        == "PROVIDER_PUBLISHED_INDEX_LEVEL"
        and policy["requiredDivisorContinuity"]
        == "PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED"
        and policy["sameCurrencyRule"]
        == "reference.currency==endpoint.context.binding.currency"
        and policy["fxConversion"] == "ABSENT"
        and policy["evaluationPrecedence"] == expected_precedence
        and policy["endpointAnchorDerivation"]
        == "CONTEXT_FACTS_NOT_UPSTREAM_REASON_LABEL"
        and policy["endpointObservationUnavailableReasonsRole"]
        == "IGNORED_REFERENCE_SELECTION_INDEPENDENT",
        f"ADR-030 {leg} identity, currency, anchor, or continuity policy changed",
    )
    if leg == "sector":
        require(
            policy["requiredReferenceAssetType"] == "INDEX"
            and "reference.referenceAssetType()!=AssetType.INDEX"
            in selector_compact,
            "ADR-030 sector reference asset must remain an explicit INDEX",
        )

benchmark_logic = "\n".join(
    java_logic(source) for source in benchmark_sources.values()
)
sector_logic = "\n".join(
    java_logic(source) for source in sector_sources.values()
)
forbidden_direct_types = (
    "OutcomeBasis",
    "SessionCloseHorizonPolicyVersion",
    "SessionCloseHorizonResolution",
    "AssetReturnPricePairResolution",
    "CorporateActionContinuity",
    "EndpointPriceAdjustmentBasis",
)
require(
    not any(token in benchmark_logic or token in sector_logic
            for token in forbidden_direct_types)
    and "sectorreferencepair" not in benchmark_logic
    and "benchmarkreferencepair" not in sector_logic,
    "ADR-030 must use nested endpoint context and independent leg types only",
)

benchmark_golden_path = (
    benchmark_test_dir
    / "BenchmarkReferenceLevelPairSelectorGoldenTest.java"
)
sector_golden_path = (
    sector_test_dir / "SectorReferenceLevelPairSelectorGoldenTest.java"
)
golden_contracts = (
    (
        benchmark_golden_path,
        "3518b66914656c8225858f8f15fdb60e25576a15b64f148270cda9881e3d8099",
        "87", "44", "Benchmark",
    ),
    (
        sector_golden_path,
        "af9ec3aa0318595027d13eb4748d41bdb587776ef3d2e5c8b3bf477fa7ba439b",
        "90", "47", "Sector",
    ),
)
required_golden_methods = (
    "canonicalDefinitionIsByteStableAndHashed",
    "publicShapesAndEnumsAreClosed",
    "resolvesAndPreservesExactReceipts",
    "evidenceConstructorsRejectInvalidTemporalAndNumericValues",
    "resultConstructorsEnforceTypedTopologyAndFutureEvidence",
    "requestRequiresExactResolvedContextTopology",
    "assignmentUnavailableTopologyUsesOnlyProvableFields",
    "mixedPointInTimePredicateRequiresBothTimestamps",
    "providerIdentityIsRawAndLabelIsNotAKey",
    "selectionReplaysAcrossLocaleTimezoneAndCandidateOrder",
    "originalAndCorrectionBasisReplayIndependently",
    "levelsArePreservedWithoutReturnCalculation",
    "equalDuplicatesAreAmbiguousAtEveryStage",
    "futureFaultsAreInvisibleAndNeverEchoed",
    "endpointMaturityComesFromNestedContextNotUpstreamReason",
    "chainLinksAreExactAndProviderEventsMatter",
    "directRecordsExposeExactComponentOrder",
    "forbiddenReuseTypesAreAbsentFromPublicSurface",
    "assignmentPropagationShortCircuitsBeforeReferenceTraversal",
    "endpointUnavailableLabelsNeverReplaceIndependentFactChecks",
    "candidatePitFilteringUsesAvailableAndCapturedCutoff",
    "localReasonMatrixIsExhaustiveAndFailClosed",
    "adjacentVisibleFaultsUseDeclaredReasonPrecedence",
    "bindingIntervalMustContainBothExactReferenceInstants",
)
for path, expected_hash, local_count, adjacent_count, prefix in golden_contracts:
    golden = path.read_text(encoding="utf-8")
    require(
        normalized_hash(path) == expected_hash
        and all(method in golden for method in required_golden_methods)
        and len(re.findall(r"(?m)^\s+@Test\s*$", golden)) == 20
        and len(re.findall(
            r"(?m)^\s+@ParameterizedTest(?:\(|\s*$)", golden
        )) == 6
        and f"assertThat(vectors).hasSize({local_count})" in golden
        and f"assertThat(result).hasSize({adjacent_count})" in golden
        and "@EnumSource(EndpointPriceResolution.UnavailableReason.class)"
        in golden
        and "TimeZone.setDefault" in golden
        and "Locale.setDefault" in golden
        and "calendarSourceId" in golden
        and "calendarSourceRevision" in golden,
        f"ADR-030 {prefix.lower()} golden matrix or exact source changed",
    )
sector_golden = sector_golden_path.read_text(encoding="utf-8")
leaves = re.search(
    r"CANONICAL_LEAVES\s*=\s*List\.of\((?P<body>.*?)\);",
    sector_golden,
    flags=re.DOTALL,
)
require(
    leaves is not None
    and 1 + len(re.findall(r'"[^"\n]+"', leaves.group("body"))) == 12
    and "allTwelveCanonicalLeavesResolveOnlyByExactMappingIdentity"
    in sector_golden,
    "ADR-030 sector golden must replay all twelve closed ADR-028 leaves",
)

benchmark_return_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
benchmark_return_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
benchmark_return_files = {
    "BenchmarkReturnPolicyVersion.java",
    "BenchmarkReturnInput.java",
    "BenchmarkReturnResult.java",
    "BenchmarkReturnCalculator.java",
}
benchmark_return_test_files = {
    "BenchmarkReturnCalculatorGoldenTest.java",
}
require(
    {path.name for path in benchmark_return_dir.glob("*.java")}
    == benchmark_return_files
    and {path.name for path in benchmark_return_test_dir.glob("*.java")}
    == benchmark_return_test_files,
    "ADR-030 may be consumed only by the exact ADR-031 source-local slice",
)
benchmark_return_paths = {
    (benchmark_return_dir / name).resolve()
    for name in benchmark_return_files
}
benchmark_return_golden_path = (
    benchmark_return_test_dir
    / "BenchmarkReturnCalculatorGoldenTest.java"
).resolve()
sector_return_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn"
)
sector_return_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn"
)
sector_return_files = {
    "SectorReturnPolicyVersion.java",
    "SectorReturnInput.java",
    "SectorReturnResult.java",
    "SectorReturnCalculator.java",
}
sector_return_test_files = {
    "SectorReturnCalculatorGoldenTest.java",
}
require(
    {path.name for path in sector_return_dir.glob("*.java")}
    == sector_return_files
    and {path.name for path in sector_return_test_dir.glob("*.java")}
    == sector_return_test_files,
    "ADR-030 may be consumed only by the exact ADR-032 source-local slice",
)
sector_return_paths = {
    (sector_return_dir / name).resolve()
    for name in sector_return_files
}
sector_return_golden_path = (
    sector_return_test_dir / "SectorReturnCalculatorGoldenTest.java"
).resolve()
benchmark_readiness_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturnreadiness"
)
benchmark_readiness_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturnreadiness"
)
sector_readiness_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturnreadiness"
)
sector_readiness_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturnreadiness"
)
benchmark_readiness_files = {
    "BenchmarkReturnReadinessPolicyVersion.java",
    "BenchmarkReturnReadinessRequest.java",
    "BenchmarkReturnReadinessResolution.java",
    "BenchmarkReturnReadinessResolver.java",
}
sector_readiness_files = {
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
require(
    {path.name for path in benchmark_readiness_dir.glob("*.java")}
        == benchmark_readiness_files
    and {path.name for path in benchmark_readiness_test_dir.glob("*.java")}
        == benchmark_readiness_test_files
    and {path.name for path in sector_readiness_dir.glob("*.java")}
        == sector_readiness_files
    and {path.name for path in sector_readiness_test_dir.glob("*.java")}
        == sector_readiness_test_files,
    "ADR-030 downstream readiness consumers must remain exact ADR-033",
)
benchmark_readiness_paths = {
    (benchmark_readiness_dir / name).resolve()
    for name in benchmark_readiness_files
}
sector_readiness_paths = {
    (sector_readiness_dir / name).resolve()
    for name in sector_readiness_files
}
readiness_paths = benchmark_readiness_paths | sector_readiness_paths
readiness_golden_paths = {
    (benchmark_readiness_test_dir
     / "BenchmarkReturnReadinessResolverGoldenTest.java").resolve(),
    (sector_readiness_test_dir
     / "SectorReturnReadinessResolverGoldenTest.java").resolve(),
}

api_main_dir = Path("apps/api/src/main/java")
new_production_paths = {
    (benchmark_dir / name).resolve() for name in benchmark_files
} | {
    (sector_dir / name).resolve() for name in sector_files
}
type_markers = tuple(
    name.removesuffix(".java")
    for name in benchmark_files | sector_files
)
allowed_benchmark_return_edges = {
    (benchmark_return_dir / "BenchmarkReturnPolicyVersion.java").resolve():
        set(),
    (benchmark_return_dir / "BenchmarkReturnInput.java").resolve(): {
        "BenchmarkReferenceLevelPairPolicyVersion",
        "BenchmarkReferenceLevelPairResolution",
    },
    (benchmark_return_dir / "BenchmarkReturnResult.java").resolve(): {
        "BenchmarkReferenceLevelPairResolution",
    },
    (benchmark_return_dir / "BenchmarkReturnCalculator.java").resolve(): {
        "BenchmarkReferenceLevelPairResolution",
    },
}
allowed_sector_return_edges = {
    (sector_return_dir / "SectorReturnPolicyVersion.java").resolve():
        set(),
    (sector_return_dir / "SectorReturnInput.java").resolve(): {
        "SectorReferenceLevelPairPolicyVersion",
        "SectorReferenceLevelPairResolution",
    },
    (sector_return_dir / "SectorReturnResult.java").resolve(): {
        "SectorReferenceLevelPairResolution",
    },
    (sector_return_dir / "SectorReturnCalculator.java").resolve(): {
        "SectorReferenceLevelPairResolution",
    },
}
allowed_benchmark_readiness_edges = {
    (benchmark_readiness_dir
     / "BenchmarkReturnReadinessPolicyVersion.java").resolve(): set(),
    (benchmark_readiness_dir
     / "BenchmarkReturnReadinessRequest.java").resolve(): set(),
    (benchmark_readiness_dir
     / "BenchmarkReturnReadinessResolution.java").resolve(): set(),
    (benchmark_readiness_dir
     / "BenchmarkReturnReadinessResolver.java").resolve(): {
        "BenchmarkReferenceLevelPairResolution",
    },
}
allowed_sector_readiness_edges = {
    (sector_readiness_dir
     / "SectorReturnReadinessPolicyVersion.java").resolve(): set(),
    (sector_readiness_dir
     / "SectorReturnReadinessRequest.java").resolve(): set(),
    (sector_readiness_dir
     / "SectorReturnReadinessResolution.java").resolve(): set(),
    (sector_readiness_dir
     / "SectorReturnReadinessResolver.java").resolve(): {
        "SectorReferenceLevelPairResolution",
    },
}
for path in api_main_dir.rglob("*.java"):
    if path.resolve() in new_production_paths:
        continue
    source = path.read_text(encoding="utf-8")
    logic = java_logic(source)
    if path.resolve() in benchmark_return_paths:
        actual_edges = {
            marker_name for marker_name in type_markers
            if re.search(rf"\b{re.escape(marker_name)}\b", logic)
        }
        require(
            "domain.outcome.sectorreferencepair" not in source
            and actual_edges
                == allowed_benchmark_return_edges[path.resolve()],
            f"ADR-031 benchmark-return consumer edge changed: {path}",
        )
        continue
    if path.resolve() in sector_return_paths:
        actual_edges = {
            marker_name for marker_name in type_markers
            if re.search(rf"\b{re.escape(marker_name)}\b", logic)
        }
        require(
            "domain.outcome.benchmarkreferencepair" not in source
            and actual_edges
                == allowed_sector_return_edges[path.resolve()],
            f"ADR-032 sector-return consumer edge changed: {path}",
        )
        continue
    if path.resolve() in benchmark_readiness_paths:
        actual_edges = {
            marker_name for marker_name in type_markers
            if re.search(rf"\b{re.escape(marker_name)}\b", logic)
        }
        require(
            "domain.outcome.sectorreferencepair" not in source
            and actual_edges
                == allowed_benchmark_readiness_edges[path.resolve()],
            f"ADR-033 benchmark readiness pair edge changed: {path}",
        )
        continue
    if path.resolve() in sector_readiness_paths:
        actual_edges = {
            marker_name for marker_name in type_markers
            if re.search(rf"\b{re.escape(marker_name)}\b", logic)
        }
        require(
            "domain.outcome.benchmarkreferencepair" not in source
            and actual_edges
                == allowed_sector_readiness_edges[path.resolve()],
            f"ADR-033 sector readiness pair edge changed: {path}",
        )
        continue
    require(
        "domain.outcome.benchmarkreferencepair" not in source
        and "domain.outcome.sectorreferencepair" not in source
        and not any(re.search(rf"\b{re.escape(marker_name)}\b", logic)
                    for marker_name in type_markers),
        f"ADR-030 reference-level-pair leaf must not be reverse-wired: {path}",
    )

protected_paths = sorted(
    {
        Path("contracts/openapi.yaml"),
        *Path("schemas").glob("*.json"),
        *Path("fixtures/v1").glob("*.json"),
        *(path for path in Path("apps/api/src/main").rglob("*")
          if path.is_file()),
    },
    key=lambda path: path.as_posix(),
)
require(
    len(protected_paths) == 232
    and digest(protected_paths)
    == "2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941",
    "ADR-030 current protected production baseline changed",
)
adr032_protected_paths = [
    path for path in protected_paths
    if path.resolve() not in readiness_paths
]
require(
    {path.resolve() for path in protected_paths}
    - {path.resolve() for path in adr032_protected_paths}
        == readiness_paths
    and len(adr032_protected_paths) == 224
    and digest(adr032_protected_paths)
        == "bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055",
    "ADR-032 protected baseline changed outside exact ADR-033 readiness production",
)
adr031_protected_paths = [
    path for path in adr032_protected_paths
    if path.resolve() not in sector_return_paths
]
require(
    {path.resolve() for path in adr032_protected_paths}
    - {path.resolve() for path in adr031_protected_paths}
        == sector_return_paths
    and len(adr031_protected_paths) == 220
    and digest(adr031_protected_paths)
        == "cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9",
    "ADR-031 protected baseline changed outside exact ADR-032 production",
)
adr030_protected_paths = [
    path for path in adr031_protected_paths
    if path.resolve() not in benchmark_return_paths
]
require(
    {path.resolve() for path in adr031_protected_paths}
    - {path.resolve() for path in adr030_protected_paths}
        == benchmark_return_paths
    and len(adr030_protected_paths) == 216
    and digest(adr030_protected_paths)
        == "45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d",
    "ADR-030 protected baseline changed outside exact ADR-031 production",
)
legacy_protected_paths = [
    path for path in adr030_protected_paths
    if path.resolve() not in new_production_paths
]
require(
    {path.resolve() for path in adr030_protected_paths}
    - {path.resolve() for path in legacy_protected_paths}
        == new_production_paths
    and len(legacy_protected_paths) == 202
    and digest(legacy_protected_paths)
    == "b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899",
    "ADR-029 protected baseline changed outside exact ADR-030 production",
)

test_web_paths = sorted(
    {
        *(path for path in Path("apps/api/src/test").rglob("*")
          if path.is_file()),
        *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
          for path in root.rglob("*") if path.is_file()),
        *(path for path in Path("apps/web").glob("*")
          if path.is_file() and path.name != "next-env.d.ts"),
    },
    key=lambda path: path.as_posix(),
)
require(
    len(test_web_paths) == 205
    and digest(test_web_paths)
    == "487c19412c79497ad03e62db92790be768f78c2bb2f7646cf3fed6fc1f970f95",
    "ADR-030 current API-test/web baseline changed",
)
adr032_test_web_paths = [
    path for path in test_web_paths
    if path.resolve() not in readiness_golden_paths
]
require(
    {path.resolve() for path in test_web_paths}
    - {path.resolve() for path in adr032_test_web_paths}
        == readiness_golden_paths
    and len(adr032_test_web_paths) == 203
    and digest(adr032_test_web_paths)
        == "c5bb494a3c26a5886fe24effb1d9a5b9e85930e736a6e3efebcdbd9e3e96fc47",
    "ADR-032 test/web baseline changed outside exact ADR-033 readiness goldens",
)
adr031_test_web_paths = [
    path for path in adr032_test_web_paths
    if path.resolve() != sector_return_golden_path
]
require(
    {path.resolve() for path in adr032_test_web_paths}
    - {path.resolve() for path in adr031_test_web_paths}
        == {sector_return_golden_path}
    and len(adr031_test_web_paths) == 202
    and digest(adr031_test_web_paths)
        == "8142b6b85cd5e2e3fdc2d05ef3a83333277d7e71c4268c639241655eec68135e",
    "ADR-031 test/web baseline changed outside exact ADR-032 golden",
)
adr030_test_web_paths = [
    path for path in adr031_test_web_paths
    if path.resolve() != benchmark_return_golden_path
]
require(
    {path.resolve() for path in adr031_test_web_paths}
    - {path.resolve() for path in adr030_test_web_paths}
        == {benchmark_return_golden_path}
    and len(adr030_test_web_paths) == 201
    and digest(adr030_test_web_paths)
        == "1b04ecf32448b91c4007f024a660d0bef370ff4f8de97c45d93061c37672348f",
    "ADR-030 test/web baseline changed outside exact ADR-031 golden",
)
new_test_paths = {
    benchmark_golden_path.resolve(), sector_golden_path.resolve(),
}
legacy_test_web_paths = [
    path for path in adr030_test_web_paths
    if path.resolve() not in new_test_paths
]
require(
    {path.resolve() for path in adr030_test_web_paths}
    - {path.resolve() for path in legacy_test_web_paths}
    == new_test_paths
    and len(legacy_test_web_paths) == 199
    and digest(legacy_test_web_paths)
    == "5c09f8859707bd8fbe59aaa2735d066fe8eedb730068da0f3e52d33a9c5907df",
    "ADR-029 test/web baseline changed outside exact ADR-030 goldens",
)

product_paths = [Path("contracts/openapi.yaml")]
product_paths += list(Path("schemas").glob("*.json"))
product_paths += list(Path("fixtures/v1").glob("*.json"))
product_paths += [
    path for root in (
        Path("apps/api/src/main/resources"),
        Path("apps/web/src"),
        Path("apps/web/e2e"),
    )
    for path in root.rglob("*") if path.is_file()
]
runtime_markers = (
    "POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1",
    "POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1",
    "domain.outcome.benchmarkreferencepair",
    "domain.outcome.sectorreferencepair",
)
for path in product_paths:
    try:
        source = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    require(
        not any(marker_name in source for marker_name in runtime_markers),
        f"ADR-030 reference evidence must not be published: {path}",
    )
outcomes = json.loads(
    Path("fixtures/v1/call-outcomes.json").read_text(encoding="utf-8")
)
require(
    all(
        outcome[field] is None
        for outcome in outcomes["outcomes"]
        for field in (
            "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
        )
    ),
    "ADR-030 must not publish comparative returns or alpha",
)

print(
    "Validated ADR-030 exact 7+7 production and 1+1 golden surface, "
    "9342/9806 canonical bytes, B53/S56 reason order, factual endpoint "
    "anchor, PIT/interval/identity/currency/divisor gates, 200/220 golden "
    "matrices, reverse isolation, current baselines, ADR-029 replay, and "
    "no product publication"
)
PYTHON
