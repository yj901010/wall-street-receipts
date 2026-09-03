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
    literals = re.findall(r'"(?:\\.|[^"\\])*"', match.group("body"))
    return "".join(json.loads(literal) for literal in literals)

def java_logic(source):
    return re.sub(
        r'//.*?$|/\*.*?\*/|"(?:\\.|[^"\\])*"',
        "",
        source,
        flags=re.MULTILINE | re.DOTALL,
    )

marker = (
    "ADR-031 calculates a signed benchmark price-index return from one "
    "complete ADR-030 benchmark reference-level-pair receipt using the "
    "exact basis-level denominator."
)
adr_path = Path(
    "decisions/ADR-031-signed-benchmark-reference-return.md"
)
marker_paths = (
    adr_path,
    Path("README.md"),
    Path("IMPLEMENTATION_LOG.md"),
    Path("quality/P3_ACCEPTANCE.md"),
)
for path in marker_paths:
    require(path.is_file(), f"Missing ADR-031 contract document: {path}")
    require(
        path.read_text(encoding="utf-8").count(marker) == 1,
        f"ADR-031 marker must occur exactly once: {path}",
    )
adr_source = adr_path.read_text(encoding="utf-8")
require(
    adr_source.startswith(
        "# ADR-031 — Signed Benchmark Reference Return\n\n"
        "- Status: Accepted\n- Date: 2026-08-24\n"
    ),
    "ADR-031 title, accepted status, or date changed",
)

production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn"
)
production_files = {
    "BenchmarkReturnPolicyVersion.java",
    "BenchmarkReturnInput.java",
    "BenchmarkReturnResult.java",
    "BenchmarkReturnCalculator.java",
}
test_files = {"BenchmarkReturnCalculatorGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "ADR-031 production surface must remain exactly four files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "ADR-031 test surface must remain exactly one source-local golden",
)
sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
expected_source_hashes = {
    "BenchmarkReturnCalculator.java":
        "6148f637d61e935436a8d63eb2a1e7e56b7305fb0285c22a079cab964a4f907d",
    "BenchmarkReturnInput.java":
        "1beb17f64d95d07737a70e062c290d0095d181abf7a7ed1ce719ca45f546aa78",
    "BenchmarkReturnPolicyVersion.java":
        "282260c5b32b20a3c094d283f00e12b99685abdedebdbf03fd2966ca1ba58f6e",
    "BenchmarkReturnResult.java":
        "ac27526add5c3d551be66a39c2076e2a5ad5d5bab6b4a0185ee173545e1aa7b8",
}
require(
    set(sources) == set(expected_source_hashes)
    and all(
        normalized_hash(production_dir / name) == expected
        for name, expected in expected_source_hashes.items()
    ),
    "ADR-031 exact normalized production source changed",
)

policy_source = sources["BenchmarkReturnPolicyVersion.java"]
definition = java_definition(policy_source)
expected_policy_hash = (
    "96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79"
)
pair_hash = (
    "2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d"
)
blocks = re.findall(r"```text\n(\{[^\n]*\})\n```", adr_source)
require(
    len(blocks) == 1 and blocks[0] == definition,
    "ADR-031 must preserve its formula and one exact canonical policy block",
)
policy = json.loads(definition, object_pairs_hook=reject_duplicate_keys)
require(
    len(definition.encode("ascii")) == 2832
    and hashlib.sha256(definition.encode("ascii")).hexdigest()
        == expected_policy_hash
    and re.search(
        rf'private static final String DEFINITION_HASH\s*=\s*'
        rf'"{expected_policy_hash}"',
        policy_source,
    )
    and enum_values(
        policy_source, "BenchmarkReturnPolicyVersion"
    ) == [
        "SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1"
    ]
    and definition.count(pair_hash) == 1,
    "ADR-031 canonical 2832 bytes, hash, enum, or required pair hash changed",
)
expected_variants = {
    "Available": ["context", "benchmarkReturn"],
    "NotApplicable": ["context"],
    "AssignmentUnavailable": ["context"],
    "EndpointAnchorUnavailable": ["context"],
    "EvidenceUnavailable": ["context"],
    "OutputUnavailable": ["context", "reason"],
}
require(
    policy["requiredReferenceLevelPairPolicyDefinitionHash"] == pair_hash
    and policy["resultContextFields"] == [
        "policyVersion", "policyDefinitionHash",
        "referenceLevelPairResolution",
    ]
    and policy["resultVariants"] == expected_variants
    and policy["outputUnavailableReasons"]
        == ["OUTPUT_NOT_REPRESENTABLE"]
    and policy["branchMapping"] == {
        "Resolved": "CALCULATE",
        "NotApplicable": "NotApplicable",
        "AssignmentUnavailable": "AssignmentUnavailable",
        "EndpointAnchorUnavailable": "EndpointAnchorUnavailable",
        "EvidenceUnavailable": "EvidenceUnavailable",
    }
    and policy["nestedReasonRule"]
        == "PRESERVE_COMPLETE_PAIR_RECEIPT_NO_MAPPING_DUPLICATION_OR_FLATTENING",
    "ADR-031 exact context, six variants, branch map, or nested reason rule changed",
)
require(
    policy["formula"] == "(endpoint-basis)/basis"
    and policy["operandSources"] == {
        "basis": "RESOLVED_BASIS_LEVEL_OBSERVATION_LEVEL",
        "endpoint": "RESOLVED_ENDPOINT_LEVEL_OBSERVATION_LEVEL",
    }
    and policy["numerator"]
        == "ENDPOINT_MINUS_BASIS_REFERENCE_LEVEL"
    and policy["denominator"] == "BASIS_REFERENCE_LEVEL"
    and policy["subtractionCount"] == 1
    and policy["divisionCount"] == 1
    and policy["divisionScale"] == 12
    and policy["roundingMode"] == "HALF_EVEN"
    and policy["operationOrder"] == [
        "SUBTRACT_BASIS_FROM_ENDPOINT_EXACTLY",
        "DIVIDE_NUMERATOR_BY_BASIS_AT_SCALE_12_HALF_EVEN",
    ]
    and policy["intermediateRounding"] == "ABSENT"
    and policy["secondRounding"] == "ABSENT"
    and policy["inputBoundary"]
        == "POSITIVE_NUMERIC_38_12_PROVIDER_PUBLISHED_PRICE_INDEX_LEVEL_PAIR"
    and policy["outputBoundary"]
        == "SIGNED_NUMERIC_38_12_AT_LEAST_NEGATIVE_ONE"
    and policy["providerReturnFieldUse"] == "ABSENT"
    and policy["assetReturnResultReuse"] == "FORBIDDEN"
    and policy["sectorReturnResultReuse"] == "FORBIDDEN"
    and policy["reflectionOrClassTokenUse"] == "ABSENT",
    "ADR-031 formula, decimal, isolation, or source boundary changed",
)

input_source = sources["BenchmarkReturnInput.java"]
result_source = sources["BenchmarkReturnResult.java"]
calculator_source = sources["BenchmarkReturnCalculator.java"]
require(
    record_fields(input_source, "BenchmarkReturnInput") == [
        "policyVersion", "referenceLevelPairResolution",
    ]
    and record_fields(result_source, "CalculationContext") == [
        "policyVersion", "policyDefinitionHash",
        "referenceLevelPairResolution",
    ]
    and all(
        record_fields(result_source, name) == fields
        for name, fields in expected_variants.items()
    )
    and enum_values(result_source, "OutputUnavailableReason")
        == ["OUTPUT_NOT_REPRESENTABLE"],
    "ADR-031 public record fields or sole output reason changed",
)
permitted = re.search(
    r"sealed interface BenchmarkReturnResult\s+permits(?P<body>.*?)\{",
    result_source,
    flags=re.DOTALL,
)
require(
    permitted is not None
    and re.findall(r"BenchmarkReturnResult\.([A-Z][A-Za-z]+)",
                   permitted.group("body"))
        == list(expected_variants),
    "ADR-031 sealed result variant order changed",
)
require(
    input_source.count(pair_hash) == 1
    and sum(source.count(pair_hash) for source in sources.values()) == 2,
    "ADR-031 required ADR-030 policy identity must remain exact",
)

calculator_logic = re.sub(r"\s+", "", java_logic(calculator_source))
formula_tokens = (
    "BigDecimalbasis=resolved.basisLevelObservation().level();",
    "BigDecimalendpoint=resolved.endpointLevelObservation().level();",
    "BigDecimalnumerator=endpoint.subtract(basis);",
    "benchmarkReturn=numerator.divide(basis,OUTPUT_SCALE,"
    "RoundingMode.HALF_EVEN);",
)
positions = [calculator_logic.find(token) for token in formula_tokens]
require(
    all(position >= 0 for position in positions)
    and positions == sorted(positions)
    and calculator_logic.count("endpoint.subtract(basis)") == 1
    and calculator_logic.count("numerator.divide(") == 1
    and "endpoint.divide(" not in calculator_logic
    and ".subtract(BigDecimal.ONE)" not in calculator_logic
    and "ratio.subtract(" not in calculator_logic
    and "OUTPUT_SCALE=12" in calculator_logic
    and "OUTPUT_PRECISION=38" in calculator_logic
    and "benchmarkReturn.compareTo(BigDecimal.ONE.negate())<0"
        in calculator_logic
    and "benchmarkReturn.scale()!=OUTPUT_SCALE" in calculator_logic
    and "benchmarkReturn.precision()>OUTPUT_PRECISION"
        in calculator_logic,
    "ADR-031 must subtract exactly before its sole scale-12 HALF_EVEN division",
)
branch_positions = [
    calculator_logic.find(
        f"caseBenchmarkReferenceLevelPairResolution.{name}"
    )
    for name in (
        "NotApplicable", "AssignmentUnavailable",
        "EndpointAnchorUnavailable", "EvidenceUnavailable", "Resolved",
    )
]
require(
    all(position >= 0 for position in branch_positions)
    and branch_positions == sorted(branch_positions)
    and calculator_logic.count(
        "input.referenceLevelPairResolution()"
    ) == 2,
    "ADR-031 calculator branch propagation or complete receipt context changed",
)
all_logic = "\n".join(java_logic(source) for source in sources.values())
imported_outcome_packages = {
    match.group(1)
    for match in re.finditer(
        r"com\.wallstreetreceipts\.api\.domain\.outcome\.([a-z]+)",
        all_logic,
    )
}
require(
    imported_outcome_packages
        <= {"benchmarkreferencepair", "benchmarkreturn"}
    and "AssetReturn" not in all_logic
    and "SectorReturn" not in all_logic
    and "BenchmarkAssignment" not in all_logic
    and "EndpointPrice" not in all_logic
    and "OutcomeBasis" not in all_logic
    and ".reason()" not in all_logic
    and "BenchmarkReferenceLevelPairResolution.UnavailableReason"
        not in all_logic
    and "BenchmarkReferenceLevelPairResolution.EndpointAnchorUnavailableReason"
        not in all_logic
    and not re.search(
        r"\b(?:Class|Clock|Random|MathContext|double|float)\b|\.class\b|"
        r"\bisInstance\s*\(|java\.lang\.reflect",
        all_logic,
    ),
    "ADR-031 must remain a no-reflection independently typed pure pair consumer",
)

golden_path = test_dir / "BenchmarkReturnCalculatorGoldenTest.java"
golden = golden_path.read_text(encoding="utf-8")
golden_logic = re.sub(r"\s+", "", java_logic(golden))
golden_compact = re.sub(r"\s+", "", golden)
required_methods = (
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "calculatesSignedPositiveNegativeAndZeroReturnsWithBasisLevelDenominator",
    "scaleEquivalentLevelsProduceEqualExactScaleTwelveResults",
    "roundsTheSingleDivisionAtPositiveAndNegativeExactHalfEvenTies",
    "allowsRoundedNegativeOneForPositiveProviderPublishedLevels",
    "acceptsExactPrecision38OutputAndTypesRoundedPrecision39AsUnavailable",
    "propagatesEveryEvidenceReasonOnlyInsideTheCompletePairReceipt",
    "propagatesEveryEndpointAnchorReasonOnlyInsideTheCompletePairReceipt",
    "propagatesEveryAssignmentReasonOnlyInsideTheCompletePairReceipt",
    "propagatesEveryNotApplicableTruthTableBranchOnlyInsideThePairReceipt",
    "directResultConstructorsEnforceExactPairBranchAndOutputBoundary",
    "rejectsEveryNullInputContextAndResultComponent",
    "closesPublicSurfaceAndReplaysWithoutLocaleOrTimezoneDependence",
)
require(
    normalized_hash(golden_path)
        == "80c8e7dcdf6b4ee3daf980dc3c3d2aa54e4446620af2fc0985173fddf5ab3c90"
    and all(method in golden for method in required_methods)
    and len(re.findall(r"(?m)^\s+@Test\s*$", golden)) == 7
    and len(re.findall(
        r"(?m)^\s+@ParameterizedTest(?:\(|\s*$)", golden
    )) == 6
    and len(re.findall(r"(?m)^\s+@EnumSource\(", golden)) == 3
    and len(re.findall(r"(?m)^\s+@MethodSource\(", golden)) == 3
    and "@EnumSource(BenchmarkReferenceLevelPairResolution.UnavailableReason.class)"
        in golden
    and "@EnumSource(BenchmarkReferenceLevelPairResolution.EndpointAnchorUnavailableReason.class)"
        in golden
    and "@EnumSource(BenchmarkAssignmentResolution.UnavailableReason.class)"
        in golden
    and golden_logic.count("isSameAs(pair)") == 6
    and 'Arguments.of("positive","100","120","0.200000000000")'
        in golden_compact
    and 'Arguments.of("negative","120","100","-0.166666666667")'
        in golden_compact
    and 'Arguments.of("asymmetric-reverse","3","2","-0.333333333333")'
        in golden_compact
    and 'Arguments.of("positive-even-stays-even","2.000000000001",'
        '"0.000000000000")' in golden_compact
    and 'Arguments.of("positive-odd-to-even","2.000000000003",'
        '"0.000000000002")' in golden_compact
    and 'Arguments.of("negative-even-stays-even","1.999999999999",'
        '"0.000000000000")' in golden_compact
    and 'Arguments.of("negative-odd-to-even","1.999999999997",'
        '"-0.000000000002")' in golden_compact
    and 'newBigDecimal("-1.000000000000")' in golden_compact
    and '"99999999999999999999999998.999999999999"'
        in golden_compact
    and "Locale.setDefault" in golden
    and "TimeZone.setDefault" in golden
    and "finally" in golden_logic
    and "newBenchmarkReturnResult.NotApplicable(resolvedContext)"
        in golden_logic
    and "newBenchmarkReturnResult.AssignmentUnavailable(resolvedContext)"
        in golden_logic
    and "newBenchmarkReturnResult.EndpointAnchorUnavailable(resolvedContext)"
        in golden_logic
    and "ObjectMapper" not in golden
    and "ClassPathResource" not in golden,
    "ADR-031 source-local 95-vector golden contract changed",
)

new_production_paths = {
    (production_dir / name).resolve() for name in production_files
}
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
    "ADR-031 downstream consumer must remain exact ADR-032",
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
    "ADR-031 downstream readiness consumers must remain exact ADR-033",
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
type_markers = tuple(
    name.removesuffix(".java") for name in production_files
)
allowed_readiness_edges = {
    (benchmark_readiness_dir
     / "BenchmarkReturnReadinessPolicyVersion.java").resolve(): set(),
    (benchmark_readiness_dir
     / "BenchmarkReturnReadinessRequest.java").resolve(): {
        "BenchmarkReturnPolicyVersion", "BenchmarkReturnResult",
    },
    (benchmark_readiness_dir
     / "BenchmarkReturnReadinessResolution.java").resolve(): {
        "BenchmarkReturnResult",
    },
    (benchmark_readiness_dir
     / "BenchmarkReturnReadinessResolver.java").resolve(): {
        "BenchmarkReturnResult",
    },
}
for path in Path("apps/api/src/main/java").rglob("*.java"):
    if path.resolve() in new_production_paths:
        continue
    source = path.read_text(encoding="utf-8")
    logic = java_logic(source)
    if path.resolve() in benchmark_readiness_paths:
        actual_edges = {
            name for name in type_markers
            if re.search(rf"\b{re.escape(name)}\b", logic)
        }
        require(
            actual_edges == allowed_readiness_edges[path.resolve()]
            and "domain.outcome.sectorreturn" not in source,
            f"ADR-033 benchmark readiness return edge changed: {path}",
        )
        continue
    require(
        "domain.outcome.benchmarkreturn" not in source
        and not any(re.search(rf"\b{re.escape(name)}\b", logic)
                    for name in type_markers),
        f"ADR-031 benchmark-return leaf must not be reverse-wired: {path}",
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
    "ADR-031 current protected production baseline changed",
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
    if path.resolve() not in new_production_paths
]
require(
    {path.resolve() for path in adr031_protected_paths}
    - {path.resolve() for path in adr030_protected_paths}
        == new_production_paths
    and len(adr030_protected_paths) == 216
    and digest(adr030_protected_paths)
        == "45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d",
    "ADR-030 protected baseline changed outside exact ADR-031 production",
)
adr030_production_paths = {
    path.resolve()
    for package_name in (
        "benchmarkreferencepair", "sectorreferencepair",
    )
    for path in Path(
        "apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
        f"outcome/{package_name}"
    ).glob("*.java")
}
adr029_protected_paths = [
    path for path in adr030_protected_paths
    if path.resolve() not in adr030_production_paths
]
require(
    len(adr030_production_paths) == 14
    and len(adr029_protected_paths) == 202
    and digest(adr029_protected_paths)
        == "b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899",
    "ADR-029 protected baseline changed outside ADR-030/031 production",
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
    "ADR-031 current API-test/web baseline changed",
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
    if path.resolve() != golden_path.resolve()
]
require(
    len(adr030_test_web_paths) == 201
    and digest(adr030_test_web_paths)
        == "1b04ecf32448b91c4007f024a660d0bef370ff4f8de97c45d93061c37672348f",
    "ADR-030 test/web baseline changed outside exact ADR-031 golden",
)
adr030_golden_paths = {
    Path(
        "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
        f"outcome/{package_name}/{class_name}.java"
    ).resolve()
    for package_name, class_name in (
        ("benchmarkreferencepair",
         "BenchmarkReferenceLevelPairSelectorGoldenTest"),
        ("sectorreferencepair",
         "SectorReferenceLevelPairSelectorGoldenTest"),
    )
}
adr029_test_web_paths = [
    path for path in adr030_test_web_paths
    if path.resolve() not in adr030_golden_paths
]
require(
    len(adr029_test_web_paths) == 199
    and digest(adr029_test_web_paths)
        == "5c09f8859707bd8fbe59aaa2735d066fe8eedb730068da0f3e52d33a9c5907df",
    "ADR-029 test/web baseline changed outside ADR-030/031 goldens",
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
    "SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1",
    "domain.outcome.benchmarkreturn",
    "BenchmarkReturnCalculator",
    "BenchmarkReturnResult",
)
for path in product_paths:
    try:
        source = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    require(
        not any(marker_name in source for marker_name in runtime_markers),
        f"ADR-031 benchmark return must not be published: {path}",
    )
outcomes = json.loads(
    Path("fixtures/v1/call-outcomes.json").read_text(encoding="utf-8")
)
require(
    all(outcome["benchmarkReturn"] is None
        for outcome in outcomes["outcomes"]),
    "ADR-031 must not publish a benchmark return",
)

print(
    "Validated ADR-031 exact 4+1 surface, 2832 canonical bytes, "
    "subtraction-before-division scale-12 HALF_EVEN formula, exact six-way "
    "complete-receipt propagation, 53/3 nested reason replay, 95-vector "
    "golden, reverse isolation, current baselines, ADR-030/029 replay, "
    "and no product publication"
)
PYTHON
