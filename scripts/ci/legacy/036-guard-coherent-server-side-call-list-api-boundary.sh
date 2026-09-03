python <<'PYTHON'
import hashlib
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
    "call-list-provider.ts",
    "call-list-query.ts",
    "call-list-adapter.ts",
    "fixture-call-list-provider.ts",
    "api-call-list-provider.server.ts",
    "call-list-provider.server.ts",
}
actual_provider_files = {
    path.name
    for path in provider_root.glob("*call-list*.ts")
    if not path.name.endswith(".test.ts")
}
require(
    actual_provider_files == expected_provider_files,
    f"Call-list production provider set is not exact: {sorted(actual_provider_files)}",
)

interface_path = provider_root / "call-list-provider.ts"
query_path = provider_root / "call-list-query.ts"
adapter_path = provider_root / "call-list-adapter.ts"
fixture_path = provider_root / "fixture-call-list-provider.ts"
legacy_fixture_path = provider_root / "fixture-calls-provider.ts"
api_path = provider_root / "api-call-list-provider.server.ts"
factory_path = provider_root / "call-list-provider.server.ts"
page_path = web_root / "app/calls/page.tsx"
messages_path = web_root / "app/calls/messages.ts"
styles_path = web_root / "app/globals.css"
required_paths = (
    interface_path, query_path, adapter_path, fixture_path, api_path, factory_path,
    page_path, messages_path, styles_path,
)
require(all(path.is_file() for path in required_paths), "Call-list source boundary is incomplete")

interface_source = interface_path.read_text(encoding="utf-8")
query_source = query_path.read_text(encoding="utf-8")
adapter_source = adapter_path.read_text(encoding="utf-8")
fixture_source = fixture_path.read_text(encoding="utf-8")
api_source = api_path.read_text(encoding="utf-8")
factory_source = factory_path.read_text(encoding="utf-8")
page_source = page_path.read_text(encoding="utf-8")
messages_source = messages_path.read_text(encoding="utf-8")
styles_source = styles_path.read_text(encoding="utf-8")

require(
    all(marker in interface_source for marker in (
        "export type CallListSnapshot", 'dataMode: "DEMO"',
        "returnedPageEvidence", "latestCallCapturedAt", "callProvenanceIds",
        "datasetEvidence", 'availability: "AVAILABLE"',
        'availability: "NOT_EXPOSED"', 'scope: "RETURNED_PAGE"',
        "CALL_LIST_METADATA_NOT_EXPOSED_REASON",
        '"LIST_API_HAS_NO_DATASET_METADATA"',
        "list(query", "Promise<CallListSnapshot>",
    )),
    "CallListProvider must close page, page-only evidence, and dataset-evidence states",
)
require(
    all(marker in query_source for marker in (
        "ALLOWED_PARAMETERS", "duplicate values are not allowed",
        "unsupported parameter", "whitespace is not normalized",
        "only DEMO is available in this phase", "2_147_483_647",
        "nextUtcDate", 'dataMode: "DEMO"',
    )),
    "Call-list raw query parser must reject ambiguity and construct exact DEMO UTC bounds",
)
require(
    all(marker in api_source for marker in (
        "typeof window", "API_BASE_URL", 'method: "GET"',
        'cache: "no-store"', 'redirect: "error"',
        'Accept: "application/json"', 'split(";", 1)',
        'contentType !== "application/json"', "url.searchParams.set",
        '["dataMode", effective.dataMode]', "v1/calls", "adaptCallListResponse",
        "CALL_LIST_METADATA_NOT_EXPOSED_REASON",
    )),
    "API call-list transport lacks exact private one-page GET semantics",
)
require(
    not any(marker in api_source for marker in (
        "fixtures/", "fixture-call-list-provider", "FixtureCallsProvider",
        ".metadata(", "NEXT_PUBLIC_", "localStorage", "sessionStorage",
        "document.cookie", "Authorization", "credentials:",
    )),
    "API call-list transport crosses its server/source boundary",
)
require(
    "catch" not in factory_source
    and "fallback" not in factory_source.lower()
    and 'process.env.CALL_AUDIT_PROVIDER ?? "fixture"' in factory_source
    and 'configuredProvider === "fixture"' in factory_source
    and 'configuredProvider === "api"' in factory_source
    and "process.env.API_BASE_URL" in factory_source
    and ".toLowerCase(" not in factory_source
    and ".toLocaleLowerCase(" not in factory_source,
    "Call-list factory must share the exact raw fixture/api selector without fallback",
)
require(
    "FixtureCallsProvider" in fixture_source
    and ".metadata()" in fixture_source
    and "AVAILABLE" in fixture_source
    and "API_BASE_URL" not in fixture_source
    and "fetch(" not in fixture_source,
    "Fixture call-list mode must own one coherent fixture page and available metadata",
)

adapter_markers = (
    "adaptCallListResponse", "adaptCallViewResponse", "closed",
    "Number.isSafeInteger", "totalElements", "totalPages", "first", "last",
    "providerEvent", "callId", "eventTime", "processingTime", "capturedAt",
    '!== "DEMO"', "returnedPageEvidence", "latestCallCapturedAt",
    "callProvenanceIds", "ticker", "institutionId", "analystId",
    "direction", "status", "from", "to",
)
require(
    all(marker in adapter_source for marker in adapter_markers),
    "Call-list adapter lacks closed shape/page/filter/order/DEMO evidence validation",
)

require(
    "callListProvider" in page_source
    and "callsProvider" not in page_source
    and "FixtureCallsProvider" not in page_source
    and "fixture-calls-provider" not in page_source
    and "fixtures/" not in page_source
    and "catch" not in page_source
    and ".metadata()" not in page_source
    and "datasetEvidence" in page_source
    and "returnedPageEvidence" in page_source,
    "Calls page must use only the page-scoped list provider and honest evidence states",
)
require(
    re.search(r'''from\s+["']@/lib/providers["']''', page_source) is None,
    "Calls page must not bypass the list factory through the provider barrel",
)
for identity_name in ("assetId", "institutionId", "analystId"):
    require(
        re.search(
            rf'messages\.{identity_name}Filter[\s\S]{{0,500}}'
            rf'<input[^>]+name="{identity_name}"',
            page_source,
        ) is not None,
        f"{identity_name} must be an exact opaque-ID text input",
    )
require(
    "metadata.facets" not in page_source
    and "allAssets" not in page_source
    and "allInstitutions" not in page_source
    and "allAnalysts" not in page_source,
    "API list UI must not retain or infer fixture facet catalogs",
)
require(
    all(marker in messages_source for marker in (
        "datasetNotExposed", "latestReturnedCapture", "returnedCallProvenance",
        "returnedPageEvidenceNote",
    )),
    "Korean/English list catalog must explain dataset versus returned-page evidence",
)
require(
    all(marker in messages_source for marker in (
        "자산 ID (대소문자 정확히 일치)", "기관 ID (대소문자 정확히 일치)",
        "애널리스트 ID (대소문자 정확히 일치)", "Asset ID (exact case)",
        "Institution ID (exact case)", "Analyst ID (exact case)",
    )),
    "Identity filter labels must disclose exact case-sensitive ID matching",
)
style_blocks = re.findall(r"([^{}]+)\{([^{}]*)\}", styles_source)
require(
    any(
        all(marker in selectors for marker in (
            ".calls-heading .provenance-strip div",
            ".calls-dataset-evidence .provenance-strip div",
            ".calls-heading .provenance-strip dd",
            ".calls-dataset-evidence .provenance-strip dd",
        ))
        and re.search(r"\bmin-width\s*:\s*0\s*;", body)
        for selectors, body in style_blocks
    )
    and any(
        all(marker in selectors for marker in (
            ".calls-heading .provenance-strip dd",
            ".calls-dataset-evidence .provenance-strip dd",
        ))
        and re.search(r"\boverflow-wrap\s*:\s*anywhere\s*;", body)
        for selectors, body in style_blocks
    )
    and any(
        ".calls-heading > .provenance-strip" in selectors
        and re.search(r"\bwidth\s*:\s*min\(52vw,\s*620px\)\s*;", body)
        and re.search(r"\bmax-width\s*:\s*100%\s*;", body)
        and re.search(r"\bmin-width\s*:\s*0\s*;", body)
        and re.search(r"\bflex-wrap\s*:\s*wrap\s*;", body)
        for selectors, body in style_blocks
    ),
    "Returned-page and dataset evidence must contain long opaque values locally",
)

production_paths = tuple(
    sorted(
        path for path in web_root.rglob("*")
        if path.is_file() and path.suffix in {".ts", ".tsx"}
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
    for variant in (
        candidate, Path(f"{candidate}.ts"), Path(f"{candidate}.tsx"),
        candidate / "index.ts", candidate / "index.tsx",
    ):
        if variant.is_file() and variant.resolve() in production_set:
            return variant.resolve()
    return None

graph = {}
for source_path in production_paths:
    source = source_path.read_text(encoding="utf-8")
    specifiers = (
        import_pattern.findall(source)
        + dynamic_import_pattern.findall(source)
        + commonjs_require_pattern.findall(source)
    )
    graph[source_path.resolve()] = {
        resolved for specifier in specifiers
        if (resolved := resolve_import(source_path, specifier)) is not None
    }
api_importers = {owner for owner, imports in graph.items() if api_path.resolve() in imports}
fixture_importers = {
    owner for owner, imports in graph.items() if fixture_path.resolve() in imports
}
factory_importers = {owner for owner, imports in graph.items() if factory_path.resolve() in imports}
require(
    api_importers == {factory_path.resolve()},
    f"Only the call-list factory may import API list transport: {sorted(map(str, api_importers))}",
)
require(
    fixture_importers == {factory_path.resolve()},
    "Only the call-list factory may import fixture list transport: "
    f"{sorted(map(str, fixture_importers))}",
)
require(
    factory_importers == {page_path.resolve()},
    f"Only the server calls page may import the list factory: {sorted(map(str, factory_importers))}",
)
api_pending = [api_path.resolve()]
api_descendants = set()
while api_pending:
    candidate = api_pending.pop()
    if candidate in api_descendants:
        continue
    api_descendants.add(candidate)
    require(
        candidate not in {fixture_path.resolve(), legacy_fixture_path.resolve()},
        f"API call-list import graph reaches fixture provider code: {candidate}",
    )
    candidate_source = candidate.read_text(encoding="utf-8")
    require(
        "fixtures/" not in candidate_source.replace("\\", "/"),
        f"API call-list import graph reaches raw fixture data: {candidate}",
    )
    api_pending.extend(graph.get(candidate, ()))
server_paths = {api_path.resolve(), factory_path.resolve()}
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
            f"Client import graph reaches private call-list module: {client_root} -> {candidate}",
        )
        pending.extend(graph.get(candidate, ()))

provider_tests = {path.name for path in provider_root.glob("*call-list*.test.ts")}
require(
    provider_tests == {
        "call-list-query.test.ts", "call-list-adapter.test.ts",
        "fixture-call-list-provider.test.ts",
        "api-call-list-provider.server.test.ts", "call-list-provider.server.test.ts",
    },
    f"Call-list provider test set is not exact: {sorted(provider_tests)}",
)
provider_test_source = "\n".join(
    path.read_text(encoding="utf-8") for path in provider_root.glob("*call-list*.test.ts")
)
require(
    all(marker in provider_test_source for marker in (
        "LIST_API_HAS_NO_DATASET_METADATA", "NOT_EXPOSED", "AVAILABLE",
        "redirect", "application/jsonp", "400", "404", "500",
        "REALTIME", "DELAYED", "EOD", "unsafe", "out-of-range",
        "callId", "provider-event", "ticker", "from", "to",
        "eventTime", "processingTime", "capturedAt",
        "accepts only server order", "deterministic requested order",
        "callId ascending as the equal-primary tie break",
        "uses exact case-sensitive", "nanosecond",
        "captured after the fixture dataset asOf",
        "older non-first returned page rather than dataset-latest calls",
        "explicit DEMO publication guard",
        "toHaveBeenCalledTimes(1)", "not.toHaveBeenCalled",
    )),
    "Call-list tests must mutation-sensitively cover one transport read, no fixture "
    "fallback, DEMO, filters, server-order preservation, and page negatives",
)
page_test_path = web_root / "app/calls/page.test.tsx"
require(page_test_path.is_file(), "Missing calls-page tests")
page_test_source = page_test_path.read_text(encoding="utf-8")
require(
    all(marker in page_test_source for marker in (
        "LIST_API_HAS_NO_DATASET_METADATA", "NOT_EXPOSED", "AVAILABLE",
        "애널리스트 콜", "Analyst calls", "NA", "returnedPageEvidence",
        "performs exactly one successful page read",
        "distinguishes an English out-of-range page",
    )),
    "Calls-page tests must lock both locales and honest evidence states",
)
e2e_path = Path("apps/web/e2e/call-list-api.spec.ts")
require(e2e_path.is_file(), "Missing call-list API Playwright source")
e2e_source = e2e_path.read_text(encoding="utf-8")
require(
    all(marker in e2e_source for marker in (
        "/calls", "demo-call-002", "LIST_API_HAS_NO_DATASET_METADATA",
        "browserApiRequests", 'page.on("request"', "collectRuntimeErrors",
        "expectNoPageOverflow", "expectVisibleKeyboardFocus",
        "const ticker", "const koreanButton", "const sourceLink",
    )),
    "Call-list E2E must cover list evidence, server isolation, focus, and overflow",
)
require(
    e2e_source.count('page.keyboard.press("Tab")') >= 3,
    "Call-list E2E must prove three sequential keyboard adjacencies",
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
require({path.name for path in Path("schemas").glob("*.json")} == expected_schemas,
        "Call-list consumer must not change schemas")
require({path.name for path in Path("fixtures/v1").glob("*.json")} == expected_fixtures,
        "Call-list consumer must not change fixtures")
require(
    {path.name for path in Path("apps/api/src/main/resources/db/migration").glob("*.sql")}
    == expected_migrations,
    "Call-list consumer must not change Flyway",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
openapi_paths = set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
require(
    openapi_paths == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Call-list consumer must use the unchanged five-path OpenAPI contract",
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
    "Call-list consumer must preserve the current approved OpenAPI/schema/fixture/API production baseline",
)
workflow_source = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
integration_start = workflow_source.index("\n  call-audit-integration:\n")
integration_end = workflow_source.index("\n  api:\n", integration_start)
integration_source = workflow_source[integration_start:integration_end]
require(
    all(marker in integration_source for marker in (
        "call-audit-integration:", "postgres:17-alpine",
        "CALL_AUDIT_PROVIDER: api", "API_BASE_URL: http://localhost:8080",
        "call-list-api.spec.ts", "SERVER_TOMCAT_ACCESSLOG_BUFFERED: 'false'",
        "SERVER_TOMCAT_ACCESSLOG_PATTERN: '%m %U%q %s'",
        "access_lines = {line.strip()", "required - access_lines",
        "GET /v1/calls?dataMode=DEMO&page=0&size=25&sort=eventTime&order=desc 200",
        "GET /v1/calls?assetId=asset-nvda&ticker=nvda&institutionId=inst-gs&analystId=analyst-demo-b&direction=BULLISH&status=ACTIVE&dataMode=DEMO&from=2026-08-11T00%3A00%3A00.000Z&to=2026-08-12T00%3A00%3A00.000Z&page=0&size=1&sort=capturedAt&order=asc 200",
        "GET /v1/calls?dataMode=DEMO&page=0&size=1&sort=eventTime&order=desc 200",
        "GET /v1/calls?dataMode=DEMO&page=1&size=1&sort=eventTime&order=desc 200",
        "GET /v1/calls?ticker=TSLA&dataMode=DEMO&page=0&size=1&sort=eventTime&order=desc 200",
    )),
    "CI must exercise the real PostgreSQL/Spring/Next API-mode call list",
)

print(
    "Validated one-read DEMO call-list providers, closed page/filter/order semantics, "
    "honest metadata states, reverse client isolation, and unchanged backend contracts"
)
PYTHON
