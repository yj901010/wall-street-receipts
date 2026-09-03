python <<'PYTHON'
import hashlib
import re
import xml.etree.ElementTree as ET
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

pg_body_race_repository_path = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/"
    "infrastructure/persistence/JdbcHistoricalFilingSegmentCaptureRepository.java"
)
pg_body_race_test_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/"
    "PostgreSqlMigrationTest.java"
)
pg_body_race_original_read_bytes = Path.read_bytes

def pre_pg_body_race_bytes(path):
    source = pg_body_race_original_read_bytes(path).replace(b"\r\n", b"\n")
    if path == pg_body_race_repository_path:
        current = (
            b"            // Cover both body identity constraints; a losing race is verified below.\n"
            b"            sql += \" ON CONFLICT DO NOTHING\";"
        )
        historical = (
            b"            sql += \" ON CONFLICT (decoded_body_sha256) DO NOTHING\";"
        )
        require(source.count(current) == 1,
                "PostgreSQL decoded-body segment-repository race delta changed")
        source = source.replace(current, historical)
        expected = "8824e770072f578d81d1cc312f64439f788a2c11ad3c1f8ef2493c2cac944d3d"
    elif path == pg_body_race_test_path:
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
        require(source.count(root_capture) == 1
                and source.count(bodies_before) == 2,
                "PostgreSQL decoded-body concurrency setup delta changed")
        source = source.replace(root_capture, historical_root_capture)
        source = source.replace(bodies_before, b"")
        for accessor in (b"catalog", b"segment"):
            assertion = (
                b"        assertThat(jdbc.getJdbcOperations().queryForObject(\n"
                b"                \"SELECT COUNT(*) FROM sec_decoded_response_bodies\", Long.class))\n"
                b"                .isEqualTo(bodiesBeforeConcurrentReplay + 1);\n"
                b"        assertThat(jdbc.getJdbcOperations().queryForObject(\n"
                b"                \"\"\"\n"
                b"                        SELECT COUNT(*)\n"
                b"                        FROM sec_decoded_response_bodies\n"
                b"                        WHERE decoded_body_sha256 = ?\n"
                b"                          AND decoded_body_length = ?\n"
                b"                        \"\"\",\n"
                b"                Long.class,\n"
                b"                concurrentCapture." + accessor
                + b"().sourceReceipt().decodedBodySha256(),\n"
                b"                concurrentCapture." + accessor
                + b"().sourceReceipt().decodedBodyLength()))\n"
                b"                .isEqualTo(1);\n"
            )
            require(source.count(assertion) == 1,
                    f"PostgreSQL decoded-body {accessor.decode()} assertion delta changed")
            source = source.replace(assertion, b"")
        expected = "c9f247b743a492a69fe84ebcd99ad24f14dda77053773f9c75390b4f6beca914"
    else:
        return source
    require(hashlib.sha256(source).hexdigest() == expected,
            f"PostgreSQL decoded-body race reverse projection changed: {path}")
    return source

Path.read_bytes = pre_pg_body_race_bytes

adr_path = Path(
    "decisions/ADR-040-sec-edgar-historical-segment-capture-persistence.md"
)
document_paths = {
    adr_path,
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
}
require(
    all(path.is_file() for path in document_paths),
    "Missing ADR-040 decision, README, or implementation-log surface",
)
documents = {
    path: path.read_text(encoding="utf-8") for path in document_paths
}
adr = documents[adr_path]
require(
    adr.startswith(
        "# ADR-040 — SEC EDGAR Historical Segment Capture Persistence\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    )
    and all("ADR-040" in documents[path] for path in document_paths),
    "ADR-040 title, accepted status, date, or documentation parity changed",
)
compact_adr = re.sub(r"\s+", " ", adr)
required_adr_terms = (
    "exact persisted rootCaptureId + descriptor ordinal",
    "at most one GET",
    "no descriptor loop",
    "`SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1`",
    "nullable `primaryDocumentUri`",
    "`DECODED_BODY_ATTACHED_PENDING_PERSISTENCE`",
    "`DURABLE_DECODED_BODY_RETAINED`",
    "`capturedAt <= evaluationAsOf`",
    "inclusive advertised interval",
    "`IDENTICAL_REPLAY`",
    "There is no repository update, delete",
    "no scheduler, startup collector",
    "no SEC API key",
    "or new environment variable",
    "no combined catalog `asOf`",
    "no union, completeness",
)
require(
    all(term in compact_adr for term in required_adr_terms),
    "ADR-040 root binding, one-GET, PIT, retention, comparison, or non-scope semantics changed",
)

main_root = Path("apps/api/src/main/java/com/wallstreetreceipts/api")
test_root = Path("apps/api/src/test/java/com/wallstreetreceipts/api")
sec_root = main_root / "infrastructure/provider/sec"
new_main_paths = {
    main_root / "application/filingsegment/PersistHistoricalFilingSegmentCaptureService.java",
    main_root / "application/port/out/HistoricalFilingSegmentCaptureAppendResult.java",
    main_root / "application/port/out/HistoricalFilingSegmentCaptureProvider.java",
    main_root / "application/port/out/HistoricalFilingSegmentCaptureReplayVerifier.java",
    main_root / "application/port/out/HistoricalFilingSegmentCaptureRepository.java",
    main_root / "config/SecHistoricalFilingSegmentConfiguration.java",
    main_root / "domain/filing/HistoricalFilingRecord.java",
    main_root / "domain/filing/HistoricalFilingSegment.java",
    main_root / "domain/filing/HistoricalFilingSegmentCapture.java",
    main_root / "infrastructure/persistence/JdbcHistoricalFilingSegmentCaptureRepository.java",
    sec_root / "SecEdgarHistoricalFilingSegmentProvider.java",
    sec_root / "SecHistoricalFilingSegmentCaptureReplayVerifier.java",
    sec_root / "SecHistoricalRawResponseCapture.java",
    sec_root / "SecHistoricalSubmissionsMapper.java",
    sec_root / "SecHistoricalSubmissionsResponse.java",
}
new_test_paths = {
    test_root / "application/filingsegment/PersistHistoricalFilingSegmentCaptureServiceTest.java",
    test_root / "domain/filing/HistoricalFilingRecordTest.java",
    test_root / "domain/filing/HistoricalFilingSegmentCaptureTest.java",
    test_root / "domain/filing/HistoricalFilingSegmentTest.java",
    test_root / "infrastructure/provider/sec/SecHistoricalFilingSegmentCaptureReplayVerifierTest.java",
    test_root / "infrastructure/provider/sec/SecHistoricalSubmissionsMapperTest.java",
    test_root / "persistence/HistoricalFilingSegmentCapturePersistenceTest.java",
    test_root / "support/SecHistoricalFilingSegmentCaptureTestFixture.java",
}
modified_paths = {
    test_root / "migration/PostgreSqlMigrationTest.java",
    Path(
        "apps/api/src/sec-live-smoke-test/java/com/wallstreetreceipts/"
        "api/config/SecEdgarLiveSmokeIT.java"
    ),
}
migration_path = Path(
    "apps/api/src/main/resources/db/migration/"
    "V7__sec_historical_filing_segment_captures.sql"
)
require(
    len(new_main_paths) == 15
    and len(new_test_paths) == 8
    and len(modified_paths) == 2
    and all(path.is_file()
            for path in new_main_paths | new_test_paths | modified_paths)
    and migration_path.is_file(),
    "ADR-040 exact 15+8 new and two modified source/test surfaces changed",
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
    "ADR-040 replay must exclude the exact ADR-041 7+5 surface",
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


expected_port_paths = {
    main_root / "application/port/out/HistoricalFilingSegmentCaptureAppendResult.java",
    main_root / "application/port/out/HistoricalFilingSegmentCaptureProvider.java",
    main_root / "application/port/out/HistoricalFilingSegmentCaptureReplayVerifier.java",
    main_root / "application/port/out/HistoricalFilingSegmentCaptureRepository.java",
}
expected_domain_paths = {
    main_root / "domain/filing/HistoricalFilingRecord.java",
    main_root / "domain/filing/HistoricalFilingSegment.java",
    main_root / "domain/filing/HistoricalFilingSegmentCapture.java",
    main_root / "domain/filing/HistoricalFilingSegmentDescriptor.java",
}
expected_sec_paths = {
    sec_root / "SecEdgarHistoricalFilingSegmentProvider.java",
    sec_root / "SecHistoricalFilingSegmentCaptureReplayVerifier.java",
    sec_root / "SecHistoricalRawResponseCapture.java",
    sec_root / "SecHistoricalSubmissionsMapper.java",
    sec_root / "SecHistoricalSubmissionsResponse.java",
}
require(
    set((main_root / "application/filingsegment").glob("*.java"))
        == {main_root / "application/filingsegment/PersistHistoricalFilingSegmentCaptureService.java"}
    and set((main_root / "application/port/out").glob(
        "HistoricalFilingSegmentCapture*.java"
    )) == expected_port_paths
    and set((main_root / "domain/filing").glob("HistoricalFiling*.java"))
        == expected_domain_paths
    and set(sec_root.glob("*Historical*.java")) == expected_sec_paths,
    "ADR-040 application, port, domain, or SEC adapter surface expanded",
)

sources = {
    path.name: path.read_bytes().decode("utf-8")
    for path in new_main_paths
}
tests = {
    path.name: path.read_text(encoding="utf-8")
    for path in new_test_paths
}
record_source = sources["HistoricalFilingRecord.java"]
segment = sources["HistoricalFilingSegment.java"]
capture = sources["HistoricalFilingSegmentCapture.java"]
provider_port = sources["HistoricalFilingSegmentCaptureProvider.java"]
replay_port = sources["HistoricalFilingSegmentCaptureReplayVerifier.java"]
repository_port = sources["HistoricalFilingSegmentCaptureRepository.java"]
append_result = sources["HistoricalFilingSegmentCaptureAppendResult.java"]
service = sources["PersistHistoricalFilingSegmentCaptureService.java"]
configuration = sources["SecHistoricalFilingSegmentConfiguration.java"]
provider = sources["SecEdgarHistoricalFilingSegmentProvider.java"]
raw_capture = sources["SecHistoricalRawResponseCapture.java"]
response = sources["SecHistoricalSubmissionsResponse.java"]
mapper = sources["SecHistoricalSubmissionsMapper.java"]
replay_verifier = sources[
    "SecHistoricalFilingSegmentCaptureReplayVerifier.java"
]
repository = sources[
    "JdbcHistoricalFilingSegmentCaptureRepository.java"
]
migration = migration_path.read_text(encoding="utf-8")

record_match = re.search(
    r"public record HistoricalFilingRecord\((?P<fields>.*?)\) \{",
    record_source,
    flags=re.DOTALL,
)
segment_match = re.search(
    r"public record HistoricalFilingSegment\((?P<fields>.*?)\) \{",
    segment,
    flags=re.DOTALL,
)
require(
    record_match is not None
    and re.sub(r"\s+", " ", record_match.group("fields")).strip()
        == (
            "String providerEventId, String accessionNumber, String form, "
            "LocalDate filingDate, LocalDate reportDate, Instant acceptedAt, "
            "URI primaryDocumentUri"
        )
    and "if (primaryDocumentUri != null)" in record_source
    and "return;" in record_source
    and "new FilingRecord(" in record_source
    and ".requireCatalogArchiveIdentity(cik);" in record_source
    and segment_match is not None
    and re.sub(r"\s+", " ", segment_match.group("fields")).strip()
        == (
            "String provider, String product, String rootCaptureId, "
            "Instant rootCapturedAt, int descriptorOrdinal, String cik, "
            "HistoricalFilingSegmentDescriptor descriptor, URI sourceUri, "
            "Instant processingTime, Instant capturedAt, "
            "SourceResponseReceipt sourceReceipt, "
            "List<HistoricalFilingRecord> filings"
        ),
    "Historical nullable filing record or segment field order changed",
)
comparison_match = re.search(
    r"public enum AdvertisedComparison\s*\{(?P<body>.*?)\}",
    segment,
    flags=re.DOTALL,
)
require(
    comparison_match is not None
    and re.findall(
        r"\b[A-Z][A-Z0-9_]+\b", comparison_match.group("body")
    ) == [
        "MATCHES_ADVERTISED",
        "COUNT_MISMATCH",
        "RANGE_MISMATCH",
        "COUNT_AND_RANGE_MISMATCH",
    ]
    and all(marker in segment for marker in (
        "observedFilingCount()",
        "observedFilingFrom()",
        "observedFilingTo()",
        "advertisedComparison()",
        "boolean rangeMatches = observedFrom == null",
        "!observedFrom.isBefore(descriptor.advertisedFilingFrom())",
        "!observedTo.isAfter(descriptor.advertisedFilingTo())",
        "processingTime must not precede rootCapturedAt",
        "capturedAt must not precede processingTime",
        "providerEventId must be unique within the historical segment",
    )),
    "Observed facts, inclusive advertised range, PIT, or duplicate semantics changed",
)

require(
    all(marker in capture for marker in (
        "public final class HistoricalFilingSegmentCapture",
        'SCHEMA_VERSION = "1.0.0"',
        '"SEC_HISTORICAL_FILING_SEGMENT_CAPTURE_ID_V1"',
        "this.decodedBody = decodedBody.clone();",
        "return decodedBody.clone();",
        "decodedBody length must match sourceReceipt",
        "decodedBody digest must match sourceReceipt",
        "DECODED_BODY_ATTACHED_PENDING_PERSISTENCE",
        "DURABLE_DECODED_BODY_RETAINED",
        "decodedBody=<redacted>",
        "lengthPrefixed(segment.rootCaptureId())",
        "lengthPrefixed(Integer.toString(segment.descriptorOrdinal()))",
        "lengthPrefixed(segment.sourceReceipt().decodedBodySha256())",
    ))
    and "RECEIPT_ONLY_BODY_NOT_RETAINED" not in capture[
        capture.index("public HistoricalFilingSegmentCapture("):
        capture.index("public String captureId()")
    ],
    "Segment capture identity, byte ownership, retention, or redaction changed",
)

require(
    "loadHistoricalSegmentCapture(" in provider_port
    and "FilingCatalogCapture durableRoot" in provider_port
    and "int descriptorOrdinal" in provider_port
    and "verify(" in replay_port
    and "HistoricalFilingSegmentCapture capture" in replay_port
    and "FilingCatalogCapture rootCapture" in replay_port
    and all(marker in repository_port for marker in (
        "append(",
        "findByCaptureId(String captureId)",
        "findLatestAtOrBefore(",
        "String rootCaptureId",
        "int descriptorOrdinal",
        "Instant evaluationAsOf",
        "String parserVersion",
        "long count()",
    ))
    and not re.search(
        r"\b(?:update|delete|remove|purge)\w*\s*\(",
        repository_port,
        re.IGNORECASE,
    )
    and re.findall(r"\b[A-Z][A-Z0-9_]+\b", append_result)
        == ["INSERTED", "IDENTICAL_REPLAY"],
    "One-segment provider, replay, append-only repository, or result vocabulary changed",
)

response_match = re.search(
    r"record SecHistoricalSubmissionsResponse\((?P<fields>.*?)\) \{",
    response,
    flags=re.DOTALL,
)
require(
    response_match is not None
    and re.sub(r"\s+", " ", response_match.group("fields")).strip()
        == (
            "List<String> accessionNumber, List<String> filingDate, "
            "List<String> reportDate, List<String> acceptanceDateTime, "
            "List<String> act, List<String> form, List<String> fileNumber, "
            "List<String> filmNumber, List<String> items, List<Long> size, "
            "List<Integer> isXBRL, List<Integer> isInlineXBRL, "
            "List<String> primaryDocument, "
            "List<String> primaryDocDescription"
        )
    and '@JsonIgnoreProperties(ignoreUnknown = true)' in response
    and all(marker in mapper for marker in (
        'PROVIDER_NAME = "sec-edgar"',
        'PRODUCT_NAME = "edgar-submissions-historical-segment-api"',
        'PARSER_VERSION = "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1"',
        "SEC historical filing arrays must have identical lengths",
        '&& !"reportDate".equals(fields[arrayIndex])',
        '&& !"primaryDocument".equals(fields[arrayIndex])',
        "nullablePrimaryDocument(",
        "primaryDocument == null",
        "? null",
        "new HistoricalFilingRecord(",
        "rootCapture.captureId()",
        "root.historicalSegments().get(descriptorOrdinal)",
        '"/submissions/" + descriptor.fileName()',
    )),
    "Strict 14-array DTO, nullable historical document, order, or root binding changed",
)

require(
    all(marker in raw_capture for marker in (
        "STRICT_DUPLICATE_DETECTION",
        "ALLOW_COERCION_OF_SCALARS",
        "ACCEPT_FLOAT_AS_INT",
        "FAIL_ON_TRAILING_TOKENS",
        "requireValidUtf8(ownedBody);",
        "requireValidUtf8(decodedBody);",
        "MAX_DECOMPRESSED_RESPONSE_BYTES",
        "decodedBody.clone()",
        "DECODED_BODY_ATTACHED_PENDING_PERSISTENCE",
        "RECEIPT_ONLY_BODY_NOT_RETAINED",
        "DECODED_HTTP_ENTITY_BODY",
        'JSON_CONTENT_TYPE = Pattern.compile(',
    ))
    and all(marker in replay_verifier for marker in (
        "implements HistoricalFilingSegmentCaptureReplayVerifier",
        'OFFICIAL_ORIGIN = "https://data.sec.gov"',
        'OFFICIAL_ORIGIN + "/submissions/"',
        "SecHistoricalRawResponseCapture.replay(",
        "if (!segment.equals(replayed))",
        "unsupported historical segment capture source envelope",
        "StandardCharsets.UTF_8.equals(mediaType.getCharset())",
        "MAX_DECOMPRESSED_RESPONSE_BYTES",
    )),
    "Exact decoded JSON/UTF-8 body capture or official replay envelope changed",
)

require(
    provider.count("restClient.get()") == 1
    and provider.count(".retrieve()") == 1
    and all(marker in provider for marker in (
        'SUBMISSIONS_PATH_PREFIX = "/submissions/"',
        "baseUrl.resolve(SUBMISSIONS_PATH_PREFIX + descriptor.fileName())",
        "exactDescriptor(",
        "durableRoot, descriptorOrdinal);",
        "BodyRetention.DURABLE_DECODED_BODY_RETAINED",
        '"CIK" + root.cik() + "-submissions-"',
        "status == 429",
        "rateLimiter.applyCooldown(",
        "retryAfterPolicy.cooldownFor(",
        "SecResponseSizeLimitInterceptor.causedByLimitExceeded",
    ))
    and all(marker not in provider for marker in (
        "while (", "for (", "Thread.sleep", "@Scheduled",
    ))
    and "rootRepository" in service
    and ".findByCaptureId(rootCaptureId)" in service
    and "segmentRepository.append(provider.loadHistoricalSegmentCapture("
        in service
    and all(marker not in service for marker in (
        "while (", "for (", "@Scheduled", "Thread.sleep", "Retry",
    )),
    "Exactly one selected GET, 429 cooldown-only, or one append orchestration changed",
)
require(
    '@ConditionalOnProperty(' in configuration
    and 'prefix = "app.public-data.sec"' in configuration
    and 'name = "enabled"' in configuration
    and 'havingValue = "true"' in configuration
    and "matchIfMissing" not in configuration
    and "PersistHistoricalFilingSegmentCaptureService" in configuration
    and all(marker not in configuration for marker in (
        "CommandLineRunner", "ApplicationRunner", "@Scheduled",
    )),
    "Explicit-opt-in bean wiring expanded into autonomous collection",
)

repository_markers = (
    "@Transactional",
    "append requires a decoded body pending durable persistence",
    "FilingCatalogCapture root = exactRoot(",
    "replayVerifier.verify(capture, root);",
    "BodyRetention.DURABLE_DECODED_BODY_RETAINED",
    "replayVerifier.verify(durable, root);",
    "findByNaturalIdentity(durable)",
    '"natural capture identity"',
    "ON CONFLICT (decoded_body_sha256) DO NOTHING",
    "ON CONFLICT DO NOTHING",
    "Arrays.equals(stored.body(), capture.decodedBody())",
    "insertFilings(durable);",
    "ORDER BY ordinal ASC",
    "requireContiguousOrdinals(orderedFilings.stream()",
    "c.captured_at <= :evaluationAsOf",
    "ORDER BY c.captured_at DESC, c.segment_capture_id DESC",
    "historical segment capture identity could not be reproduced",
    "historical segment observed comparison does not match its rows",
    "filing.primaryDocumentUri() == null",
)
require(
    all(marker in repository for marker in repository_markers)
    and "ON CONFLICT DO UPDATE" not in repository.upper()
    and re.search(r"\bUPDATE\s+[A-Za-z_]", repository, re.IGNORECASE)
        is None
    and re.search(r"\bDELETE\s+FROM\b", repository, re.IGNORECASE)
        is None,
    "Atomic append, exact reread, nullable row, PIT, or no-mutation SQL changed",
)

tables = re.findall(
    r"^CREATE TABLE ([a-z0-9_]+) \(", migration, re.MULTILINE
)
sql_without_restrict = migration.replace("ON DELETE RESTRICT", "")
require(
    tables == [
        "sec_historical_filing_segment_captures",
        "sec_historical_filing_segment_filings",
    ]
    and migration.count("ON DELETE RESTRICT") == 3
    and "ON DELETE CASCADE" not in migration
    and "CURRENT_TIMESTAMP" not in migration
    and "CREATE TRIGGER" not in migration
    and re.search(
        r"\bUPDATE\s+[A-Za-z_]", sql_without_restrict, re.IGNORECASE
    ) is None
    and re.search(
        r"\bDELETE\s+FROM\b", sql_without_restrict, re.IGNORECASE
    ) is None
    and all(marker in migration for marker in (
        "ADD CONSTRAINT uq_sec_catalog_segment_exact",
        "FOREIGN KEY (",
        "root_capture_id,",
        "descriptor_ordinal,",
        "catalog_captured_at,",
        "ON DELETE RESTRICT",
        "UNIQUE (",
        "source_uri,",
        "captured_at",
        "decoded_body_length <= 8388608",
        "body_retention = 'DURABLE_DECODED_BODY_RETAINED'",
        "parser_version = 'SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1'",
        "observed_filing_from >= advertised_filing_from",
        "observed_filing_to <= advertised_filing_to",
        "observed_filing_from < advertised_filing_from",
        "observed_filing_to > advertised_filing_to",
        "primary_document_uri VARCHAR(2048),",
        "accepted_at <= segment_processing_time",
        "immutable = TRUE",
        "idx_sec_historical_segment_point_in_time",
    )),
    "Flyway V7 root FK, append-only, nullable document, comparison, or PIT constraints changed",
)
migration_names = {
    path.name for path in Path(
        "apps/api/src/main/resources/db/migration"
    ).glob("*.sql")
}
require(
    migration_names == {
        "V1__baseline.sql",
        "V2__analyst_calls.sql",
        "V3__analyst_call_revisions.sql",
        "V4__call_outcomes.sql",
        "V5__call_contexts.sql",
        "V6__sec_filing_catalog_captures.sql",
        "V7__sec_historical_filing_segment_captures.sql",
        "V8__sec_filing_history_collection_manifests.sql",
        "V9__sec_filing_collection_attempts.sql",
    },
    "ADR-040 replay plus the exact ADR-041 V8 and ADR-042 V9 deltas must remain the current nine-migration surface",
)

record_test = tests["HistoricalFilingRecordTest.java"]
segment_test = tests["HistoricalFilingSegmentTest.java"]
capture_test = tests["HistoricalFilingSegmentCaptureTest.java"]
mapper_test = tests["SecHistoricalSubmissionsMapperTest.java"]
replay_test = tests[
    "SecHistoricalFilingSegmentCaptureReplayVerifierTest.java"
]
service_test = tests[
    "PersistHistoricalFilingSegmentCaptureServiceTest.java"
]
persistence_test = tests[
    "HistoricalFilingSegmentCapturePersistenceTest.java"
]
postgres_test_path = test_root / "migration/PostgreSqlMigrationTest.java"
postgres_test_bytes = postgres_test_path.read_bytes().replace(b"\r\n", b"\n")
require(
    hashlib.sha256(postgres_test_bytes).hexdigest()
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
for current, historical, count in adr041_version_replacements:
    require(
        postgres_test_bytes.count(current) == count,
        "ADR-041 PostgreSQL Flyway-version delta changed",
    )
    postgres_test_bytes = postgres_test_bytes.replace(current, historical)
require(
    hashlib.sha256(postgres_test_bytes).hexdigest()
        == "afb2bf1d7f7d864a3f2f2e410d50c58f397af3252041983c2b84e0c321a0b797",
    "ADR-041 PostgreSQL reverse projection changed",
)
postgres_test = postgres_test_bytes.decode("utf-8")
require(
    all(method in record_test for method in (
        "preservesAnObservedMissingPrimaryDocumentWithoutInventingAUri",
        "acceptsAndValidatesAnExactCikBoundSecArchiveDocument",
        "rejectsInventedNonCanonicalDocumentsAndInvalidEventIdentity",
        "rejectsMissingFieldsAndSubMicrosecondAcceptanceTimesEvenWithoutADocument",
    ))
    and all(method in segment_test for method in (
        "preservesProviderOrderAndCalculatesObservedMetadataDeterministically",
        "comparesCountAndInclusiveAdvertisedRangeContainmentIndependently",
        "rejectsDuplicateNullWrongCatalogAndFutureFilingRows",
        "rejectsInvalidRootDescriptorSourceAndReceiptBindings",
        "rejectsImpossibleOrImpreciseKnowledgeTimesAndNegativeOrdinal",
    ))
    and all(method in capture_test for method in (
        "ownsExactBytesAndRedactsThemFromItsStringRepresentation",
        "changesIdentityForAnotherObservationButNotForRetentionPromotion",
        "rejectsReceiptOnlyLengthDigestAndDetachedRetention",
    )),
    "ADR-040 nullable record, observed comparison, capture, or PIT domain coverage changed",
)
require(
    all(method in mapper_test for method in (
        "mapsExactBodyAndPreservesProviderOrderWithExplicitCountMismatch",
        "preservesObservedMissingPrimaryDocumentsAsNullWithoutInventingAUri",
        "rejectsMissingUnequalAndNullRequiredParallelArraysWithoutPartialSalvage",
        "strictReaderRejectsDuplicateKeysScalarCoercionFloatIntegersAndTrailingTokens",
        "rawCaptureRejectsMalformedUtf8BeforeReceiptOrParserCreation",
        "providerPerformsExactlyOneGetForTheCapturedDescriptorPath",
        "providerApplies429CooldownAndDoesNotRetryOrStartASecondRequest",
        "providerRejectsUnknownDescriptorBeforeStartingNetworkIo",
    ))
    and all(method in replay_test for method in (
        "verifiesTheExactOfficialBodyProjectionAgainstItsDurableRoot",
        "rejectsAnotherRootAndAProjectionNotParsedFromTheExactBody",
        "rejectsForgedOriginAndNonUtf8ExactBodies",
    ))
    and all(method in service_test for method in (
        "resolvesOneExactDurableRootThenPerformsOneProviderLoadAndOneAppend",
        "missingRootFailsBeforeProviderOrSegmentRepositoryInteraction",
    )),
    "ADR-040 strict mapper, one-GET provider, replay, or one-shot service coverage changed",
)
require(
    all(method in persistence_test for method in (
        "appendsAndReconstructsExactBodyReceiptOrderAndMismatchEvidence",
        "exactReplayIsNoOpAndLaterObservationSharesOnlyTheBodyRow",
        "naturalIdentityConflictFailsClosed",
        "pointInTimeReadUsesSegmentKnowledgeTimeAndExactParser",
        "missingDurableRootRejectsAppendBeforeAnySegmentWrite",
        "readFailsClosedWhenStoredProjectionIsTampered",
        "repositoryAndCaptureExposeNoMutationSurface",
    ))
    and "v7UpgradesV6AndAppendsHistoricalSegmentsAtomicallyOnPostgreSql"
        in postgres_test
    and "containsExactlyInAnyOrder(" in postgres_test
    and "ConcurrentSegmentAppendAttempt" in postgres_test
    and "conflicting historical segment capture for natural capture identity"
        in postgres_test
    and "bodiesBeforeConflict + 1" in postgres_test
    and "WHERE r.capture_id IS NULL" in postgres_test
    and "AND s.segment_capture_id IS NULL" in postgres_test
    and '"DELETE FROM sec_decoded_response_bodies"' in postgres_test
    and "primary_document_uri IS NULL" in postgres_test
    and '"X".repeat(129)' in postgres_test
    and "catchThrowable(() -> transactions.executeWithoutResult("
        in postgres_test
    and 'getSQLState()).isEqualTo("22001")' in postgres_test
    and "capturesBeforeChildFailure" in postgres_test
    and "bodiesBeforeChildFailure" in postgres_test
    and "filingsBeforeChildFailure" in postgres_test
    and "oversizedChild.segment().sourceReceipt().decodedBodySha256()"
        in postgres_test
    and "flyway.info().applied()).hasSize(7)" in postgres_test,
    "ADR-040 H2 transaction, PostgreSQL race, orphan-body, or V7 coverage changed",
)

live_path = Path(
    "apps/api/src/sec-live-smoke-test/java/com/wallstreetreceipts/"
    "api/config/SecEdgarLiveSmokeIT.java"
)
live_source = live_path.read_text(encoding="utf-8")
require(
    "loadsOneOfficialAppleRootAndOneCapturedDescriptorOnlyAfterBothOptIns"
        in live_source
    and 'APPLE_CIK = "0000320193"' in live_source
    and live_source.count("provider.loadCatalogCapture(APPLE_CIK)") == 1
    and live_source.count("loadHistoricalSegmentCapture(") == 1
    and "replayCheckedRoot, 0" in live_source
    and 'OPT_IN_ENVIRONMENT_VARIABLE = "SEC_LIVE_SMOKE"' in live_source
    and 'PROFILE_MARKER_PROPERTY = "sec.live-smoke.profile"' in live_source
    and "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1" in live_source
    and "DECODED_BODY_ATTACHED_PENDING_PERSISTENCE" in live_source
    and "AdvertisedComparison.MATCHES_ADVERTISED" in live_source
    and all(marker not in live_source for marker in (
        "while (", "for (", "@Scheduled", "Thread.sleep",
    )),
    "Opt-in live smoke must remain exactly one root plus ordinal-zero segment request",
)

production_source = "\n".join(sources.values())
require(
    all(marker not in production_source for marker in (
        "@RestController", "@Controller", "@RequestMapping",
        "@GetMapping", "@PostMapping", "@Scheduled",
        "SchedulingConfigurer", "CommandLineRunner", "ApplicationRunner",
        "System.getenv", "System.getProperty", "System.out", "System.err",
        "Logger", "Slf4j", "new String(decodedBody",
        "new String(capture.decodedBody", "API_KEY", "APIKEY",
        "OAUTH", "ACCESS_TOKEN", "SECRET_KEY",
    )),
    "ADR-040 must not publish, schedule, trigger, log, decode, or request credentials",
)
focused_test_source = "\n".join(tests.values())
require(
    "MockRestServiceServer.bindTo(builder)" in mapper_test
    and all(marker not in focused_test_source for marker in (
        "HttpClient.newHttpClient(", "RestClient.create(",
        "WebClient.create(", ".openConnection(", "new Socket(",
    )),
    "ADR-040 standard tests must remain deterministic and offline",
)

publication_markers = (
    "historicalfilingsegmentcapture",
    "historical_filing_segment_capture",
    "sec_historical_filing_segment",
    "sec_submissions_historical_segment_v1",
    "durable_decoded_body_retained",
    "decoded_body_attached_pending_persistence",
)
publication_paths = {
    Path("contracts/openapi.yaml"),
    *Path("schemas").glob("*.json"),
    *(path for path in Path("fixtures/v1").rglob("*") if path.is_file()),
    *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
      for path in root.rglob("*") if path.is_file()),
}
for path in publication_paths:
    source = path.read_text(encoding="utf-8", errors="ignore").lower()
    require(
        not any(marker in source for marker in publication_markers),
        f"ADR-040 private segment/body must not reach API, fixture, or web: {path}",
    )

namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
pom_root = ET.parse(Path("apps/api/pom.xml")).getroot()
profiles = pom_root.findall("m:profiles/m:profile", namespace)
require(
    len(profiles) == 1
    and profiles[0].findtext("m:id", namespaces=namespace)
        == "sec-live-smoke"
    and profiles[0].find("m:activation", namespace) is None,
    "ADR-040 must preserve the isolated non-activated live-smoke profile",
)

workflow = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
def without_step(source, name):
    step = f"\n      - name: {name}\n"
    start = source.index(step)
    end = source.index("\n      - name: ", start + len(step))
    return source[:start] + source[end:]

workflow_without_sec_guards = workflow
for name in (
    "Guard SEC EDGAR single-process live-operation safety gate",
    "Guard SEC EDGAR decoded-response receipt foundation",
    "Guard SEC EDGAR historical-segment descriptor catalog",
    "Guard SEC EDGAR append-only capture persistence",
    "Guard SEC EDGAR historical-segment append-only persistence",
    "Guard SEC EDGAR ordered filing-history collection manifest",
    "Guard SEC EDGAR operator-controlled bounded collection attempt",
    "Guard default-disabled local single-operator SEC attempt API",
):
    workflow_without_sec_guards = without_step(
        workflow_without_sec_guards, name
    )
require(
    workflow_without_sec_guards.count("run: ./mvnw -B -ntp verify") == 1
    and all(marker not in workflow_without_sec_guards for marker in (
        "SEC_LIVE_SMOKE", "-Psec-live-smoke",
        "src/sec-live-smoke-test", "SecEdgarLiveSmokeIT",
        "sec.live-smoke.profile",
    ))
    and re.search(
        r"(?:curl|wget|Invoke-WebRequest)[^\n]*data\.sec\.gov",
        workflow_without_sec_guards,
        re.IGNORECASE,
    ) is None,
    "Default Maven/CI must never activate or invoke SEC live collection",
)

print(
    "Validated ADR-040 exact 15+8 new and two modified source/test surfaces, "
    "durable-root ordinal binding, one bounded segment GET, strict exact-body "
    "replay, nullable historical document evidence, inclusive advertised-range "
    "comparison, append-only V7 atomic/PIT persistence, and no fan-out/API/UI/key/live CI"
)
PYTHON
