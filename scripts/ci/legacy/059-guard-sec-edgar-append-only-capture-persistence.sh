python <<'PYTHON'
import hashlib
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

pg_body_race_repository_path = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/"
    "infrastructure/persistence/JdbcFilingCatalogCaptureRepository.java"
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
                "PostgreSQL decoded-body root-repository race delta changed")
        source = source.replace(current, historical)
        expected = "9ed38f90ffe6fdf045dbe2396db0d7205e64117da36b1cbf4fedab2bcbee9697"
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
    "decisions/ADR-039-sec-edgar-append-only-capture-persistence.md"
)
document_paths = {
    adr_path,
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
}
require(
    all(path.is_file() for path in document_paths),
    "Missing ADR-039 decision, README, or implementation-log surface",
)
documents = {
    path: path.read_text(encoding="utf-8") for path in document_paths
}
adr = documents[adr_path]
require(
    adr.startswith(
        "# ADR-039 — SEC EDGAR Append-Only Capture Persistence\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    )
    and all("ADR-039" in documents[path] for path in document_paths),
    "ADR-039 title, accepted status, date, or documentation parity changed",
)
compact_adr = re.sub(r"\s+", " ", adr)
required_adr_terms = (
    "exact decoded HTTP entity body",
    "`DECODED_BODY_ATTACHED_PENDING_PERSISTENCE`",
    "`DURABLE_DECODED_BODY_RETAINED`",
    "`IDENTICAL_REPLAY`",
    "`SEC_SUBMISSIONS_CATALOG_V2`",
    "`capturedAt <= evaluationAsOf`",
    "one natural capture",
    "One repository transaction",
    "no update or delete method",
    "no referenced-segment GET",
    "no scheduler, controller, command-line trigger, retry loop",
    "No new SEC API key",
    "not logged",
    "not a public product endpoint",
    "not a WORM database",
)
require(
    all(term in compact_adr for term in required_adr_terms),
    "ADR-039 exact-byte, append, PIT, retention, or non-scope semantics changed",
)

main_root = Path("apps/api/src/main/java/com/wallstreetreceipts/api")
test_root = Path("apps/api/src/test/java/com/wallstreetreceipts/api")
sec_root = main_root / "infrastructure/provider/sec"
new_main_paths = {
    main_root / "application/filing/PersistFilingCatalogCaptureService.java",
    main_root / "application/port/out/FilingCatalogCaptureAppendResult.java",
    main_root / "application/port/out/FilingCatalogCaptureProvider.java",
    main_root / "application/port/out/FilingCatalogCaptureReplayVerifier.java",
    main_root / "application/port/out/FilingCatalogCaptureRepository.java",
    main_root / "config/SecFilingCatalogPersistenceConfiguration.java",
    main_root / "domain/filing/FilingCatalogCapture.java",
    main_root / "infrastructure/persistence/JdbcFilingCatalogCaptureRepository.java",
    sec_root / "SecFilingCatalogCaptureReplayVerifier.java",
}
new_test_paths = {
    test_root / "application/filing/PersistFilingCatalogCaptureServiceTest.java",
    test_root / "domain/filing/FilingCatalogCaptureTest.java",
    test_root / "persistence/FilingCatalogCapturePersistenceTest.java",
    test_root / "support/SecFilingCatalogCaptureTestFixture.java",
}
modified_paths = {
    main_root / "domain/source/SourceResponseReceipt.java",
    sec_root / "SecEdgarFilingCatalogProvider.java",
    sec_root / "SecRawResponseCapture.java",
    test_root / "config/SecEdgarConfigurationTest.java",
    test_root / "migration/PostgreSqlMigrationTest.java",
    Path(
        "apps/api/src/sec-live-smoke-test/java/com/wallstreetreceipts/"
        "api/config/SecEdgarLiveSmokeIT.java"
    ),
}
migration_path = Path(
    "apps/api/src/main/resources/db/migration/"
    "V6__sec_filing_catalog_captures.sql"
)
require(
    len(new_main_paths) == 9
    and len(new_test_paths) == 4
    and len(modified_paths) == 6
    and all(path.is_file()
            for path in new_main_paths | new_test_paths | modified_paths)
    and migration_path.is_file(),
    "ADR-039 exact 9+4 new and six modified source/test surfaces changed",
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
    "ADR-039 replay must exclude the exact ADR-041 7+5 surface",
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


postgres_test_path = test_root / "migration/PostgreSqlMigrationTest.java"

def pre_adr040_text(path):
    source = path.read_bytes().replace(b"\r\n", b"\n")
    if path != postgres_test_path:
        return source.decode("utf-8")
    require(
        hashlib.sha256(source).hexdigest()
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
    for current, historical_version, count in adr041_version_replacements:
        require(
            source.count(current) == count,
            "ADR-041 PostgreSQL Flyway-version delta changed",
        )
        source = source.replace(current, historical_version)
    require(
        hashlib.sha256(source).hexdigest()
            == "afb2bf1d7f7d864a3f2f2e410d50c58f397af3252041983c2b84e0c321a0b797",
        "ADR-041 PostgreSQL reverse projection changed",
    )
    historical = source.decode("utf-8")
    for marker in (
        "import static org.assertj.core.api.Assertions.catchThrowable;\n",
        "import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;\n",
        "import com.wallstreetreceipts.api.infrastructure.persistence.JdbcHistoricalFilingSegmentCaptureRepository;\n",
        "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecHistoricalFilingSegmentCaptureReplayVerifier;\n",
        "import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;\n",
    ):
        historical = historical.replace(marker, "")
    historical = re.sub(
        r"\n    @Test\n    void v7UpgradesV6AndAppendsHistoricalSegmentsAtomicallyOnPostgreSql\(\).*?"
        r"(?=\n    private static FilingCatalogCaptureAppendResult concurrentCaptureAppend\()",
        "",
        historical,
        flags=re.DOTALL,
    )
    historical = re.sub(
        r"\n    private static HistoricalFilingSegmentCaptureAppendResult concurrentSegmentAppend\(.*?"
        r"(?=\n    private static ConcurrentCaptureAppendAttempt concurrentCaptureAppendAttempt\()",
        "",
        historical,
        flags=re.DOTALL,
    )
    historical = re.sub(
        r"\n    private record ConcurrentSegmentAppendAttempt\(.*?\n    \}\n",
        "\n",
        historical,
        flags=re.DOTALL,
    ).replace(
        "\n\n    private static String concurrentContextImport(",
        "\n    private static String concurrentContextImport(",
    ).replace(
        "assertThat(flyway.info().applied()).hasSize(7);",
        "assertThat(flyway.info().applied()).hasSize(6);",
    ).replace(
        'assertThat(latest.info().current().getVersion().getVersion())'
        '.isEqualTo("7");',
        'assertThat(latest.info().current().getVersion().getVersion())'
        '.isEqualTo("6");',
    ).replace(
        '.locations("classpath:db/migration")\n'
        '                .target("6")\n'
        '                .load();',
        '.locations("classpath:db/migration")\n'
        '                .load();',
    )
    require(
        hashlib.sha256(historical.encode("utf-8")).hexdigest()
            == "e6ee0f96789697414853562e47e698da088debec246f1cfe178661380163458b",
        "ADR-039 replay of the pre-ADR-040 PostgreSQL test changed",
    )
    return historical
require(
    {
        path for path in (
            main_root / "application/port/out"
        ).glob("FilingCatalogCapture*.java")
    } == {
        main_root / "application/port/out/FilingCatalogCaptureAppendResult.java",
        main_root / "application/port/out/FilingCatalogCaptureProvider.java",
        main_root / "application/port/out/FilingCatalogCaptureReplayVerifier.java",
        main_root / "application/port/out/FilingCatalogCaptureRepository.java",
    }
    and {
        path for path in (
            main_root / "application/filing"
        ).glob("*.java")
    } == {
        main_root / "application/filing/PersistFilingCatalogCaptureService.java"
    },
    "ADR-039 capture port or one-shot application surface expanded",
)

sources = {
    path.name: path.read_bytes().decode("utf-8")
    for path in new_main_paths | modified_paths
    if path.suffix == ".java"
}
tests = {
    path.name: pre_adr040_text(path)
    for path in new_test_paths
    | {test_root / "migration/PostgreSqlMigrationTest.java"}
}
receipt = sources["SourceResponseReceipt.java"]
capture = sources["FilingCatalogCapture.java"]
raw_capture = sources["SecRawResponseCapture.java"]
provider = sources["SecEdgarFilingCatalogProvider.java"]
repository_port = sources["FilingCatalogCaptureRepository.java"]
append_result = sources["FilingCatalogCaptureAppendResult.java"]
repository = sources["JdbcFilingCatalogCaptureRepository.java"]
replay_verifier = sources["SecFilingCatalogCaptureReplayVerifier.java"]
service = sources["PersistFilingCatalogCaptureService.java"]
persistence_configuration = sources[
    "SecFilingCatalogPersistenceConfiguration.java"
]
migration = migration_path.read_text(encoding="utf-8")

retention_match = re.search(
    r"public enum BodyRetention\s*\{(?P<body>.*?)\}",
    receipt,
    flags=re.DOTALL,
)
require(retention_match is not None, "Missing body-retention vocabulary")
require(
    re.findall(
        r"\b[A-Z][A-Z0-9_]+\b", retention_match.group("body")
    ) == [
        "RECEIPT_ONLY_BODY_NOT_RETAINED",
        "DECODED_BODY_ATTACHED_PENDING_PERSISTENCE",
        "DURABLE_DECODED_BODY_RETAINED",
    ]
    and "SourceResponseReceipt withBodyRetention" in receipt
    and "etag=<redacted>" in receipt,
    "Receipt-only, pending, durable, or validator-redaction semantics changed",
)

capture_markers = (
    "public final class FilingCatalogCapture",
    'SCHEMA_VERSION = "1.0.0"',
    'IDENTITY_VERSION = "SEC_FILING_CATALOG_CAPTURE_ID_V1"',
    "private final String captureId;",
    "private final FilingCatalog catalog;",
    "private final byte[] decodedBody;",
    "this.decodedBody = decodedBody.clone();",
    "return decodedBody.clone();",
    "decodedBody length must match sourceReceipt",
    "decodedBody digest must match sourceReceipt",
    "DECODED_BODY_ATTACHED_PENDING_PERSISTENCE",
    "DURABLE_DECODED_BODY_RETAINED",
    "decodedBody=<redacted>",
    "lengthPrefixed(IDENTITY_VERSION)",
    "catalog.capturedAt().toString()",
    "catalog.sourceReceipt().decodedBodySha256()",
    "MessageDigest.getInstance(\"SHA-256\")",
)
require(
    all(marker in capture for marker in capture_markers)
    and "RECEIPT_ONLY_BODY_NOT_RETAINED" not in capture[
        capture.index("public FilingCatalogCapture("):
        capture.index("public String captureId()")
    ],
    "Capture identity, defensive ownership, digest, or redaction changed",
)

require(
    "toCatalogCapture(Instant processingTime)" in raw_capture
    and "receipt.withBodyRetention(" in raw_capture
    and "BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE"
        in raw_capture
    and "static FilingCatalog replay(" in raw_capture
    and "SUBMISSIONS_READER.readValue(decodedBody)" in raw_capture
    and raw_capture.count(
        "BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED"
    ) == 1
    and "implements FilingCatalogProvider, FilingCatalogCaptureProvider"
        in provider
    and "FilingCatalogCapture loadCatalogCapture(String cik)" in provider
    and "return capture.toCatalogCapture(receivedAt);" in provider
    and provider.count("restClient.get()") == 1
    and provider.count(".retrieve()") == 1
    and 'SUBMISSIONS_PATH_TEMPLATE = "/submissions/CIK%s.json"'
        in provider
    and "submissions-001.json" not in provider
    and "submissions-002.json" not in provider,
    "Legacy receipt-only path, exact-body capture, or one-root-request boundary changed",
)

require(
    all(marker in replay_verifier for marker in (
        "implements FilingCatalogCaptureReplayVerifier",
        'OFFICIAL_SOURCE_ORIGIN = "https://data.sec.gov"',
        "SecSubmissionsMapper.PROVIDER_NAME.equals",
        "SecSubmissionsMapper.PRODUCT_NAME.equals",
        "SecSubmissionsMapper.PARSER_VERSION.equals",
        "requireOfficialSourceEnvelope(catalog);",
        '"/submissions/CIK" + catalog.cik() + ".json"',
        '"application".equalsIgnoreCase(mediaType.getType())',
        '"json".equalsIgnoreCase(mediaType.getSubtype())',
        "StandardCharsets.UTF_8.equals(mediaType.getCharset())",
        "SecResponseSizeLimitInterceptor.MAX_DECOMPRESSED_RESPONSE_BYTES",
        "SecRawResponseCapture.replay(",
        "if (!catalog.equals(replayed))",
        "filing catalog capture does not match exact-body replay",
    ))
    and "capture.decodedBody()" in replay_verifier
    and "unsupported filing catalog capture parser contract"
        in replay_verifier,
    "Exact parser-contract replay verification changed",
)

repository_methods = (
    "append(FilingCatalogCapture capture)",
    "findByCaptureId(String captureId)",
    "findLatestAtOrBefore(",
    "long count()",
)
require(
    all(method in repository_port for method in repository_methods)
    and not re.search(r"\b(?:update|delete|remove|purge)\w*\s*\(",
                      repository_port, re.IGNORECASE)
    and re.findall(r"\b[A-Z][A-Z0-9_]+\b", append_result)
        == ["INSERTED", "IDENTICAL_REPLAY"],
    "Append-only repository port or replay result vocabulary changed",
)

repository_markers = (
    "@Transactional",
    "append requires a decoded body pending durable persistence",
    "replayVerifier.verify(capture);",
    "BodyRetention.DURABLE_DECODED_BODY_RETAINED",
    "replayVerifier.verify(durable);",
    "findByNaturalIdentity(durable)",
    '"natural capture identity"',
    "ON CONFLICT (decoded_body_sha256) DO NOTHING",
    "ON CONFLICT DO NOTHING",
    "Arrays.equals(stored.body(), capture.decodedBody())",
    "insertRecentFilings(durable);",
    "insertHistoricalSegments(durable);",
    "ORDER BY ordinal ASC",
    "c.captured_at <= :evaluationAsOf",
    "ORDER BY c.captured_at DESC, c.capture_id DESC",
    "child count does not match its root receipt",
    "capture status does not match its children",
    "filing catalog capture identity could not be reproduced",
    "replayVerifier.verify(capture)",
)
sql_without_restrict = migration.replace("ON DELETE RESTRICT", "")
require(
    all(marker in repository for marker in repository_markers)
    and "ON CONFLICT DO UPDATE" not in repository.upper()
    and re.search(r"\bUPDATE\s+[A-Za-z_]", repository, re.IGNORECASE)
        is None
    and re.search(r"\bDELETE\s+FROM\b", repository, re.IGNORECASE)
        is None
    and re.search(r"\bUPDATE\s+[A-Za-z_]", sql_without_restrict,
                  re.IGNORECASE) is None
    and re.search(r"\bDELETE\s+FROM\b", sql_without_restrict,
                  re.IGNORECASE) is None,
    "Atomic append, exact reread, PIT query, or no-mutation SQL changed",
)

require(
    "repository.append(provider.loadCatalogCapture(cik))" in service
    and all(marker not in service for marker in (
        "while (", "for (", "@Scheduled", "Thread.sleep", "Retry",
    ))
    and "@ConditionalOnProperty(" in persistence_configuration
    and 'prefix = "app.public-data.sec"' in persistence_configuration
    and 'havingValue = "true"' in persistence_configuration
    and "PersistFilingCatalogCaptureService" in persistence_configuration
    and "matchIfMissing" not in persistence_configuration,
    "One-shot explicit-opt-in persistence wiring expanded into autonomous ingestion",
)
require(
    "SecResponseSizeLimitInterceptor.MAX_DECOMPRESSED_RESPONSE_BYTES"
        in raw_capture
    and raw_capture.count("requireValidUtf8(") >= 3,
    "Capture/replay must preserve the shared decoded-size and strict UTF-8 boundary",
)

tables = re.findall(
    r"^CREATE TABLE ([a-z0-9_]+) \(", migration, re.MULTILINE
)
require(
    tables == [
        "sec_decoded_response_bodies",
        "sec_filing_catalog_captures",
        "sec_filing_catalog_recent_filings",
        "sec_filing_catalog_historical_segments",
    ]
    and migration.count("ON DELETE RESTRICT") == 3
    and "ON DELETE CASCADE" not in migration
    and "CURRENT_TIMESTAMP" not in migration
    and "CREATE TRIGGER" not in migration
    and all(marker in migration for marker in (
        "BYTEA NOT NULL",
        "OCTET_LENGTH(decoded_body) = decoded_body_length",
        "decoded_body_length <= 8388608",
        "UNIQUE (provider, product, source_uri, captured_at)",
        "captured_at >= processing_time",
        "accepted_at <= catalog_processing_time",
        "body_representation = 'DECODED_HTTP_ENTITY_BODY'",
        "body_retention = 'DURABLE_DECODED_BODY_RETAINED'",
        "recent_filing_count >= 0",
        "historical_segment_count >= 0",
        "RECENT_ONLY_NO_SEGMENTS_ADVERTISED",
        "RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED",
        "advertised_filing_count > 0",
        "advertised_filing_from <= advertised_filing_to",
        "immutable = TRUE",
        "idx_sec_filing_catalog_capture_point_in_time",
    )),
    "Flyway V6 exact-body, FK, PIT, count/status, or immutable constraints changed",
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
    "ADR-039 replay must recognize only ADR-040 through ADR-042 as later Flyway migrations",
)

capture_test = tests["FilingCatalogCaptureTest.java"]
persistence_test = tests["FilingCatalogCapturePersistenceTest.java"]
service_test = tests["PersistFilingCatalogCaptureServiceTest.java"]
postgres_test = tests["PostgreSqlMigrationTest.java"]
require(
    all(method in capture_test for method in (
        "ownsExactBytesAndRedactsThemFromItsStringRepresentation",
        "changesIdentityForAnotherObservationButNotForRetentionPromotion",
        "rejectsAReceiptOnlyDeclarationWhenTheBodyIsAttached",
        "rejectsBodyLengthAndDigestMismatches",
    ))
    and all(method in persistence_test for method in (
        "appendsAndReconstructsExactBodyReceiptAndProviderOrder",
        "exactReplayIsANoOpAndLaterObservationOfSameBodyIsAppended",
        "callerCannotClaimDurabilityBeforeTheRepositoryCommits",
        "sameNaturalCaptureWithDifferentExactBodyFailsClosed",
        "pointInTimeLookupNeverUsesAFutureCaptureOrAnotherParser",
        "rawBodyReplayRejectsAProjectionThatWasNotParsedFromThoseBytes",
        "replayRejectsInvalidSourceEnvelopesAndNonUtf8Bodies",
        "appendRejectsAnOversizedBodyBeforeAnyDatabaseWrite",
        "readFailsClosedWhenStoredBodyBytesAreTamperedWithoutChangingLength",
        "readFailsClosedWhenStoredChildCountIsTampered",
        "failedChildInsertRollsBackBodyAndRootAtomically",
        "repositoryAndCaptureExposeNoMutationSurface",
    ))
    and "performsExactlyOneProviderLoadAndOneAtomicRepositoryAppend"
        in service_test
    and "v6AppendsAndReplaysExactSecCatalogCapturesOnPostgreSql"
        in postgres_test
    and "containsExactlyInAnyOrder(" in postgres_test
    and "ConcurrentCaptureAppendAttempt" in postgres_test
    and "conflicting filing catalog capture for natural capture identity"
        in postgres_test
    and "bodiesBeforeConflict + 1" in postgres_test
    and "WHERE c.capture_id IS NULL" in postgres_test
    and '"DELETE FROM sec_decoded_response_bodies"' in postgres_test
    and "flyway.info().applied()).hasSize(6)" in postgres_test,
    "ADR-039 domain, H2, transaction, or PostgreSQL replay coverage changed",
)

production_source = "\n".join(
    path.read_text(encoding="utf-8") for path in new_main_paths
    | {
        main_root / "domain/source/SourceResponseReceipt.java",
        sec_root / "SecEdgarFilingCatalogProvider.java",
        sec_root / "SecRawResponseCapture.java",
    }
)
require(
    all(marker not in production_source for marker in (
        "@RestController", "@Controller", "@RequestMapping",
        "@GetMapping", "@PostMapping", "@Scheduled",
        "SchedulingConfigurer", "CommandLineRunner", "ApplicationRunner",
        "System.out", "System.err", "Logger", "Slf4j",
        "new String(decodedBody", "new String(capture.decodedBody",
    )),
    "ADR-039 must not publish, schedule, trigger, or log retained body evidence",
)
publication_markers = (
    "filingcatalogcapture",
    "filing_catalog_capture",
    "decoded_body_attached_pending_persistence",
    "durable_decoded_body_retained",
    "sec_decoded_response_bodies",
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
        f"ADR-039 private capture/body must not reach API, fixture, or web: {path}",
    )

print(
    "Validated ADR-039 exact 9+4 new and six modified source/test surfaces, "
    "receipt-to-pending-to-durable retention, exact-body replay, append-only "
    "idempotency/conflict/atomicity, parser-specific PIT reads, Flyway V6 "
    "constraints, one root request with no segment GET, and no scheduler/API/UI/logging"
)
PYTHON
