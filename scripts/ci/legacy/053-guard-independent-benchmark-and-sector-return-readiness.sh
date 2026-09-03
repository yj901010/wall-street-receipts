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
    "ADR-033 classifies benchmark and sector return readiness "
    "independently from their complete supplied ADR-031 and ADR-032 "
    "result receipts without mapping either leaf to canonical "
    "lifecycle status."
)
adr_path = Path(
    "decisions/ADR-033-independent-benchmark-sector-return-readiness.md"
)
marker_paths = (
    adr_path,
    Path("README.md"),
    Path("IMPLEMENTATION_LOG.md"),
    Path("quality/P3_ACCEPTANCE.md"),
)
for path in marker_paths:
    require(path.is_file(), f"Missing ADR-033 contract document: {path}")
    require(
        path.read_text(encoding="utf-8").count(marker) == 1,
        f"ADR-033 marker must occur exactly once: {path}",
    )
adr_source = adr_path.read_text(encoding="utf-8")
require(
    adr_source.startswith(
        "# ADR-033 — Independent Benchmark and Sector Return Readiness\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    ),
    "ADR-033 title, accepted status, or date changed",
)
fence = chr(96) * 3
blocks = re.findall(
    re.escape(fence) + r"json\n(\{[^\n]*\})\n" + re.escape(fence),
    adr_source,
)
require(
    len(blocks) == 2,
    "ADR-033 must contain exactly two single-line canonical JSON blocks",
)
adr_policies = {
    json.loads(block, object_pairs_hook=reject_duplicate_keys)[
        "policyVersion"
    ]: block
    for block in blocks
}

benchmark_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturnreadiness"
)
benchmark_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturnreadiness"
)
sector_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturnreadiness"
)
sector_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturnreadiness"
)
contracts = (
    {
        "leg": "Benchmark",
        "lower": "benchmark",
        "directory": benchmark_dir,
        "test_directory": benchmark_test_dir,
        "policy_name": "BenchmarkReturnReadinessPolicyVersion",
        "request_name": "BenchmarkReturnReadinessRequest",
        "resolution_name": "BenchmarkReturnReadinessResolution",
        "resolver_name": "BenchmarkReturnReadinessResolver",
        "golden_name":
            "BenchmarkReturnReadinessResolverGoldenTest.java",
        "policy_value":
            "SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1",
        "policy_length": 2622,
        "policy_hash":
            "2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf",
        "required_policy_key":
            "requiredBenchmarkReturnPolicyVersion",
        "required_hash_key":
            "requiredBenchmarkReturnPolicyDefinitionHash",
        "required_policy":
            "SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1",
        "required_hash":
            "96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79",
        "source_input":
            "COMPLETE_SUPPLIED_BENCHMARK_RETURN_RESULT",
        "ownership": "BENCHMARK_RETURN_ONLY",
        "settled_rule":
            "BENCHMARK_RETURN_AVAILABLE_OR_INTENTIONALLY_NOT_APPLICABLE",
        "source_preservation":
            "PRESERVE_EXACT_WHOLE_BENCHMARK_RETURN_RESULT",
        "reason_inspection":
            "ONLY_IN_BENCHMARK_RETURN_READINESS_RESOLVER",
        "return_package": "benchmarkreturn",
        "pair_package": "benchmarkreferencepair",
        "pair_type": "BenchmarkReferenceLevelPairResolution",
        "source_hashes": {
            "BenchmarkReturnReadinessPolicyVersion.java":
                "33d78427d5c7baff2ac029cf645d8965a5fc3973bc058ab7044369597110735e",
            "BenchmarkReturnReadinessRequest.java":
                "e5ffc5a3e650cb47910144cf7429a4261e499503f2f5fcf86f516e07467aa856",
            "BenchmarkReturnReadinessResolution.java":
                "8fee4c697aa37965d4fdac76f0932dd4a4bded7e3e5e85eb19376ba5bdcffc67",
            "BenchmarkReturnReadinessResolver.java":
                "f29220fe8a31bdd0fafe9b7cbdb677569cee2a0bb369c2408b990af27c84435a",
        },
        "golden_hash":
            "f61e82ba7766effe4954f4c96db745a49bb49a03d06de583c39c32d76e3c1b3d",
        "vector_size": "81",
        "settled_size": "5",
        "awaiting_size": "1",
        "unavailable_size": "75",
        "future_anchor_markers": (
            "caseCATALOG_NOT_KNOWN_AS_OF->EndpointState.FUTURE_CATALOG",
            "caseBINDING_NOT_KNOWN_AS_OF->EndpointState.FUTURE_BINDING",
            "endpoint(state,true)",
        ),
    },
    {
        "leg": "Sector",
        "lower": "sector",
        "directory": sector_dir,
        "test_directory": sector_test_dir,
        "policy_name": "SectorReturnReadinessPolicyVersion",
        "request_name": "SectorReturnReadinessRequest",
        "resolution_name": "SectorReturnReadinessResolution",
        "resolver_name": "SectorReturnReadinessResolver",
        "golden_name":
            "SectorReturnReadinessResolverGoldenTest.java",
        "policy_value":
            "SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1",
        "policy_length": 2592,
        "policy_hash":
            "5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4",
        "required_policy_key":
            "requiredSectorReturnPolicyVersion",
        "required_hash_key":
            "requiredSectorReturnPolicyDefinitionHash",
        "required_policy":
            "SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1",
        "required_hash":
            "5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7",
        "source_input":
            "COMPLETE_SUPPLIED_SECTOR_RETURN_RESULT",
        "ownership": "SECTOR_RETURN_ONLY",
        "settled_rule":
            "SECTOR_RETURN_AVAILABLE_OR_INTENTIONALLY_NOT_APPLICABLE",
        "source_preservation":
            "PRESERVE_EXACT_WHOLE_SECTOR_RETURN_RESULT",
        "reason_inspection":
            "ONLY_IN_SECTOR_RETURN_READINESS_RESOLVER",
        "return_package": "sectorreturn",
        "pair_package": "sectorreferencepair",
        "pair_type": "SectorReferenceLevelPairResolution",
        "source_hashes": {
            "SectorReturnReadinessPolicyVersion.java":
                "fbacd8305ec8782b13151e5f70ad9a5854e6cc39002cf4ef172ed7f8ffba6f26",
            "SectorReturnReadinessRequest.java":
                "2194801a39baf0815641369e5fd490afbf5c5458f20eb74f63b324781c9a633f",
            "SectorReturnReadinessResolution.java":
                "e1aa0dce34de1c2cbbaaa90a53315e70696423a080305346b68ba9be9354e5b3",
            "SectorReturnReadinessResolver.java":
                "d314dea999984f790a029644300778f72a3ca7129acbe384032495044a046353",
        },
        "golden_hash":
            "1b2aa1eea5d5c8efcddd048c54d8b53be87b14cdfd96a6df43e11a7f55bc9f8c",
        "vector_size": "98",
        "settled_size": "2",
        "awaiting_size": "1",
        "unavailable_size": "95",
        "future_anchor_markers": (
            "resolvedAssignment(AWAITING_AS_OF)",
            "endpointUnavailable(endpointReason(reason),AWAITING_AS_OF,"
            "anchorFault(reason))",
            "caseCATALOG_NOT_KNOWN_AS_OF->AnchorFault.CATALOG_FUTURE",
            "caseBINDING_NOT_KNOWN_AS_OF->AnchorFault.BINDING_FUTURE",
        ),
    },
)

result_variants = {
    "Settled": ["context", "sourceResult"],
    "AwaitingEndpoint": ["context", "sourceResult"],
    "EvidenceUnavailable": ["context", "sourceResult"],
}
branch_precedence = [
    "Available", "NotApplicable", "EXACT_AWAITING_ENDPOINT_CHAIN",
    "AssignmentUnavailable", "EndpointAnchorUnavailable",
    "OTHER_EVIDENCE_UNAVAILABLE", "OutputUnavailable",
]
branch_mapping = {
    "Available": "SETTLED",
    "NotApplicable": "SETTLED",
    "ExactAwaitingEndpointChain": "AWAITING_ENDPOINT",
    "AssignmentUnavailable": "EVIDENCE_UNAVAILABLE",
    "EndpointAnchorUnavailable": "EVIDENCE_UNAVAILABLE",
    "OtherEvidenceUnavailable": "EVIDENCE_UNAVAILABLE",
    "OutputUnavailable": "EVIDENCE_UNAVAILABLE",
}
all_production_paths = set()
all_golden_paths = set()
all_type_markers = set()
all_production_logic = []

for contract in contracts:
    directory = contract["directory"]
    test_directory = contract["test_directory"]
    production_files = set(contract["source_hashes"])
    test_files = {contract["golden_name"]}
    require(
        {path.name for path in directory.glob("*.java")}
            == production_files,
        f"ADR-033 {contract['lower']} production surface must be exact 4",
    )
    require(
        {path.name for path in test_directory.glob("*.java")}
            == test_files,
        f"ADR-033 {contract['lower']} test surface must be exact 1",
    )
    sources = {
        name: (directory / name).read_text(encoding="utf-8")
        for name in production_files
    }
    require(
        all(
            normalized_hash(directory / name) == expected
            for name, expected in contract["source_hashes"].items()
        ),
        f"ADR-033 exact {contract['lower']} production source changed",
    )

    policy_source = sources[contract["policy_name"] + ".java"]
    definition = java_definition(policy_source)
    require(
        definition == adr_policies[contract["policy_value"]]
        and len(definition.encode("ascii"))
            == contract["policy_length"]
        and hashlib.sha256(definition.encode("ascii")).hexdigest()
            == contract["policy_hash"]
        and re.search(
            rf'private static final String DEFINITION_HASH\s*=\s*'
            rf'"{contract["policy_hash"]}"',
            policy_source,
        )
        and enum_values(
            policy_source, contract["policy_name"]
        ) == [contract["policy_value"]],
        f"ADR-033 {contract['lower']} canonical bytes/hash changed",
    )
    policy = json.loads(
        definition, object_pairs_hook=reject_duplicate_keys
    )
    require(
        policy["policyVersion"] == contract["policy_value"]
        and policy[contract["required_policy_key"]]
            == contract["required_policy"]
        and policy[contract["required_hash_key"]]
            == contract["required_hash"]
        and policy["requestFields"]
            == ["policyVersion", "sourceResult"]
        and policy["requestPresence"] == "ALL_FIELDS_NON_NULL"
        and policy["resolutionContextFields"]
            == ["policyVersion", "policyDefinitionHash"]
        and policy["resultVariants"] == result_variants
        and policy["sourceInput"] == contract["source_input"]
        and policy["readinessOwnership"] == contract["ownership"]
        and policy["otherComparativeReturnInput"] == "ABSENT"
        and policy["crossReturnCorrelation"]
            == "ABSENT_DEFERRED_TO_FUTURE_AGGREGATE"
        and policy["sharedGenericReadiness"] == "ABSENT"
        and policy["branchPrecedence"] == branch_precedence
        and policy["branchMapping"] == branch_mapping
        and policy["settledRule"] == contract["settled_rule"]
        and policy["sourcePreservation"]
            == contract["source_preservation"]
        and policy["reasonInspection"]
            == contract["reason_inspection"],
        f"ADR-033 {contract['lower']} ownership/branch policy changed",
    )
    awaiting_key = contract["lower"] + "ReturnVariant"
    require(
        policy["awaitingEndpointChain"] == {
            awaiting_key: "EvidenceUnavailable",
            "referenceLevelPairVariant": "EvidenceUnavailable",
            "referenceLevelPairReason":
                "ENDPOINT_NOT_REACHED_AS_OF",
        }
        and policy["notApplicableEndpointRule"]
            == "SETTLED_WITHOUT_ENDPOINT_WAIT_OR_REASON_INSPECTION"
        and policy["assignmentOrAnchorUnavailableRule"]
            == "EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED"
        and policy["sourceAttestationBoundary"]
            == "LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_SELECTOR_OR_CALCULATOR_INVOCATION_CLAIM"
        and policy["classificationValidation"]
            == "RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION"
        and policy["resolverInvocationAttestation"]
            == "ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION",
        f"ADR-033 {contract['lower']} exact temporal chain changed",
    )
    for key in (
        "reasonFlattening", "canonicalOutcomeStatus",
        "dataCompleteClaim", "retry", "freshness", "cancellation",
        "scheduling", "producerReplay", "selectorInvocation",
        "calculatorInvocation", "methodologyActivation",
        "inputFingerprint", "persistence", "aggregation", "ranking",
        "publication",
    ):
        require(
            policy[key] == "ABSENT",
            f"ADR-033 {contract['lower']} forbidden {key} changed",
        )

    request_source = sources[contract["request_name"] + ".java"]
    resolution_source = sources[
        contract["resolution_name"] + ".java"
    ]
    resolver_source = sources[contract["resolver_name"] + ".java"]
    require(
        record_fields(request_source, contract["request_name"])
            == ["policyVersion", "sourceResult"]
        and record_fields(resolution_source, "ResolutionContext")
            == ["policyVersion", "policyDefinitionHash"]
        and all(
            record_fields(resolution_source, name) == fields
            for name, fields in result_variants.items()
        ),
        f"ADR-033 {contract['lower']} public record fields changed",
    )
    permitted = re.search(
        rf"sealed interface {contract['resolution_name']}\s+permits"
        r"(?P<body>.*?)\{",
        resolution_source,
        flags=re.DOTALL,
    )
    require(
        permitted is not None
        and re.findall(
            rf"{contract['resolution_name']}\.([A-Z][A-Za-z]+)",
            permitted.group("body"),
        ) == list(result_variants),
        f"ADR-033 {contract['lower']} sealed variant order changed",
    )
    resolver_logic = java_logic(resolver_source)
    request_resolution_logic = java_logic(
        request_source + "\n" + resolution_source
    )
    source_variants = (
        "Available", "NotApplicable", "EvidenceUnavailable",
        "AssignmentUnavailable", "EndpointAnchorUnavailable",
        "OutputUnavailable",
    )
    require(
        all(
            re.search(
                rf"case\s+{contract['leg']}ReturnResult\.{variant}\b",
                resolver_logic,
            )
            for variant in source_variants
        )
        and resolver_logic.count(".reason()") == 1
        and "ENDPOINT_NOT_REACHED_AS_OF" in resolver_logic
        and contract["pair_type"] in resolver_logic
        and ".reason()" not in request_resolution_logic
        and "UnavailableReason" not in request_resolution_logic
        and "new " + contract["request_name"] in resolution_source
        and "requireClassification" in resolution_source,
        f"ADR-033 {contract['lower']} exact classification changed",
    )

    expected_edges = {
        contract["policy_name"] + ".java": {
            contract["lower"] + "returnreadiness",
        },
        contract["request_name"] + ".java": {
            contract["lower"] + "returnreadiness",
            contract["return_package"],
        },
        contract["resolution_name"] + ".java": {
            contract["lower"] + "returnreadiness",
            contract["return_package"],
        },
        contract["resolver_name"] + ".java": {
            contract["lower"] + "returnreadiness",
            contract["return_package"],
            contract["pair_package"],
        },
    }
    for name, source in sources.items():
        actual_edges = {
            match.group(1)
            for match in re.finditer(
                r"com\.wallstreetreceipts\.api\.domain\.outcome\.([a-z]+)",
                java_logic(source),
            )
        }
        require(
            actual_edges == expected_edges[name],
            f"ADR-033 exact import edge changed: {name}",
        )

    golden_path = test_directory / contract["golden_name"]
    golden = golden_path.read_text(encoding="utf-8")
    golden_logic = re.sub(r"\s+", "", java_logic(golden))
    anchor_loop_start = golden_logic.find(
        "for(" + contract["pair_type"]
        + ".EndpointAnchorUnavailableReasonreason"
    )
    anchor_loop_end = golden_logic.find(
        "for(" + contract["pair_type"] + ".UnavailableReasonreason",
        anchor_loop_start,
    )
    anchor_loop = golden_logic[
        anchor_loop_start:anchor_loop_end
    ] if anchor_loop_start >= 0 and anchor_loop_end > anchor_loop_start else ""
    require(
        normalized_hash(golden_path) == contract["golden_hash"]
        and len(re.findall(r"(?m)^\s+@Test\s*$", golden)) == 6
        and len(re.findall(
            r"(?m)^\s+@ParameterizedTest(?:\(|\s*$)", golden
        )) == 1
        and len(re.findall(
            r"(?m)^\s+@MethodSource\(", golden
        )) == 1
        and f"hasSize({contract['vector_size']})" in golden
        and f"hasSize({contract['settled_size']})" in golden
        and f"hasSize({contract['awaiting_size']})" in golden
        and f"hasSize({contract['unavailable_size']})" in golden
        and "directResultConstructorsShareExactFailClosedClassification"
            in golden
        and "equalButDistinctWholeSourceRecordsReplayEqually" in golden
        and "classificationIsIndependentOfLocaleTimezoneAndPriorCalls"
            in golden
        and "Locale.setDefault" in golden
        and "TimeZone.setDefault" in golden
        and "finally" in golden
        and "ObjectMapper" not in golden
        and "ClassPathResource" not in golden,
        f"ADR-033 {contract['lower']} exhaustive golden changed",
    )
    require(
        anchor_loop
        and "endpointAnchorUnavailableSource(reason)" in anchor_loop
        and contract["resolution_name"]
            + ".EvidenceUnavailable.class" in anchor_loop
        and contract["resolution_name"]
            + ".AwaitingEndpoint.class" not in anchor_loop
        and all(
            marker_name in golden_logic
            for marker_name in contract["future_anchor_markers"]
        ),
        f"ADR-033 {contract['lower']} future-endpoint anchor must remain "
        "EvidenceUnavailable",
    )

    production_paths = {
        (directory / name).resolve() for name in production_files
    }
    all_production_paths |= production_paths
    all_golden_paths.add(golden_path.resolve())
    all_type_markers |= {
        name.removesuffix(".java") for name in production_files
    }
    all_production_logic.extend(
        java_logic(source) for source in sources.values()
    )

combined_logic = "\n".join(all_production_logic)
require(
    "BenchmarkReturn" not in "\n".join(
        java_logic(path.read_text(encoding="utf-8"))
        for path in sector_dir.glob("*.java")
    )
    and "SectorReturn" not in "\n".join(
        java_logic(path.read_text(encoding="utf-8"))
        for path in benchmark_dir.glob("*.java")
    )
    and not any(
        token in combined_logic
        for token in (
            "ComparativeReturnReadiness", "SharedReturnReadiness",
            "OutcomeEvaluationStatus", "CallOutcome", "dataComplete",
            "BenchmarkReturnCalculator", "SectorReturnCalculator",
            "BenchmarkReferenceLevelPairSelector",
            "SectorReferenceLevelPairSelector", "Class<", ".class",
            "java.lang.reflect", "@Service", "@Repository",
            "@Controller",
        )
    )
    and not Path(
        "apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
        "outcome/comparativereturnreadiness"
    ).exists()
    and not Path(
        "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
        "outcome/comparativereturnreadiness"
    ).exists(),
    "ADR-033 independent ownership or lifecycle firewall changed",
)

for path in Path("apps/api/src/main/java").rglob("*.java"):
    if path.resolve() in all_production_paths:
        continue
    source = path.read_text(encoding="utf-8")
    logic = java_logic(source)
    require(
        "domain.outcome.benchmarkreturnreadiness" not in source
        and "domain.outcome.sectorreturnreadiness" not in source
        and not any(
            re.search(rf"\b{re.escape(name)}\b", logic)
            for name in all_type_markers
        ),
        f"ADR-033 readiness leaves must not be reverse-wired: {path}",
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
    "ADR-033 current protected production baseline changed",
)
adr032_protected_paths = [
    path for path in protected_paths
    if path.resolve() not in all_production_paths
]
require(
    {path.resolve() for path in protected_paths}
    - {path.resolve() for path in adr032_protected_paths}
        == all_production_paths
    and len(adr032_protected_paths) == 224
    and digest(adr032_protected_paths)
        == "bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055",
    "ADR-032 baseline changed outside exact ADR-033 8-file production",
)
sector_return_paths = {
    path.resolve()
    for path in Path(
        "apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
        "outcome/sectorreturn"
    ).glob("*.java")
}
adr031_protected_paths = [
    path for path in adr032_protected_paths
    if path.resolve() not in sector_return_paths
]
require(
    len(sector_return_paths) == 4
    and len(adr031_protected_paths) == 220
    and digest(adr031_protected_paths)
        == "cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9",
    "ADR-031 baseline changed outside ADR-032/033 production",
)
benchmark_return_paths = {
    path.resolve()
    for path in Path(
        "apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
        "outcome/benchmarkreturn"
    ).glob("*.java")
}
adr030_protected_paths = [
    path for path in adr031_protected_paths
    if path.resolve() not in benchmark_return_paths
]
require(
    len(benchmark_return_paths) == 4
    and len(adr030_protected_paths) == 216
    and digest(adr030_protected_paths)
        == "45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d",
    "ADR-030 baseline changed outside ADR-031/032/033 production",
)
reference_pair_paths = {
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
    if path.resolve() not in reference_pair_paths
]
require(
    len(reference_pair_paths) == 14
    and len(adr029_protected_paths) == 202
    and digest(adr029_protected_paths)
        == "b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899",
    "ADR-029 baseline changed outside ADR-030/031/032/033 production",
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
    "ADR-033 current API-test/web baseline changed",
)
adr032_test_web_paths = [
    path for path in test_web_paths
    if path.resolve() not in all_golden_paths
]
require(
    {path.resolve() for path in test_web_paths}
    - {path.resolve() for path in adr032_test_web_paths}
        == all_golden_paths
    and len(adr032_test_web_paths) == 203
    and digest(adr032_test_web_paths)
        == "c5bb494a3c26a5886fe24effb1d9a5b9e85930e736a6e3efebcdbd9e3e96fc47",
    "ADR-032 baseline changed outside exact ADR-033 two goldens",
)
sector_return_golden = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "sectorreturn/SectorReturnCalculatorGoldenTest.java"
).resolve()
adr031_test_web_paths = [
    path for path in adr032_test_web_paths
    if path.resolve() != sector_return_golden
]
require(
    len(adr031_test_web_paths) == 202
    and digest(adr031_test_web_paths)
        == "8142b6b85cd5e2e3fdc2d05ef3a83333277d7e71c4268c639241655eec68135e",
    "ADR-031 test/web baseline changed outside ADR-032/033 goldens",
)
benchmark_return_golden = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/"
    "benchmarkreturn/BenchmarkReturnCalculatorGoldenTest.java"
).resolve()
adr030_test_web_paths = [
    path for path in adr031_test_web_paths
    if path.resolve() != benchmark_return_golden
]
require(
    len(adr030_test_web_paths) == 201
    and digest(adr030_test_web_paths)
        == "1b04ecf32448b91c4007f024a660d0bef370ff4f8de97c45d93061c37672348f",
    "ADR-030 test/web baseline changed outside ADR-031/032/033 goldens",
)
reference_pair_goldens = {
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
    if path.resolve() not in reference_pair_goldens
]
require(
    len(adr029_test_web_paths) == 199
    and digest(adr029_test_web_paths)
        == "5c09f8859707bd8fbe59aaa2735d066fe8eedb730068da0f3e52d33a9c5907df",
    "ADR-029 test/web baseline changed outside ADR-030/031/032/033 goldens",
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
    "SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1",
    "SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1",
    "domain.outcome.benchmarkreturnreadiness",
    "domain.outcome.sectorreturnreadiness",
    "BenchmarkReturnReadinessResolution",
    "SectorReturnReadinessResolution",
)
for path in product_paths:
    try:
        source = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    require(
        not any(marker_name in source for marker_name in runtime_markers),
        f"ADR-033 readiness must not be published: {path}",
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
    "ADR-033 must not publish comparative returns or alpha",
)

print(
    "Validated ADR-033 exact independent 8+2 readiness surface, two "
    "canonical policies, 81/98 exhaustive source shapes, exact temporal "
    "chains, source preservation, import/reverse/lifecycle firewalls, "
    "current baselines, ADR-032/031/030/029 replay, and no publication"
)
PYTHON
