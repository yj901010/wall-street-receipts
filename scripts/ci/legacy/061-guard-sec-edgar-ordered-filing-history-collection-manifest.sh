python <<'PYTHON'
import hashlib
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def compact(source):
    return re.sub(r"\s+", " ", source).strip()

def record_fields(source, name):
    match = re.search(
        rf"public record {name}\((?P<fields>.*?)\) \{{",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing {name} record")
    return compact(match.group("fields"))

def enum_values(source, name):
    match = re.search(
        rf"public enum {name}\s*\{{(?P<body>.*?)\n    \}}",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing {name} enum")
    return re.findall(r"\b[A-Z][A-Z0-9_]+\b", match.group("body"))

adr_path = Path(
    "decisions/ADR-041-sec-root-relative-filing-history-collection-manifest.md"
)
document_paths = {
    adr_path,
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
}
require(
    all(path.is_file() for path in document_paths),
    "Missing ADR-041 decision, README, or implementation-log surface",
)
documents = {
    path: path.read_text(encoding="utf-8") for path in document_paths
}
adr = documents[adr_path]
require(
    adr.startswith(
        "# ADR-041 — SEC Root-Relative Filing-History Collection Manifest\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    )
    and all("ADR-041" in documents[path] for path in document_paths),
    "ADR-041 title, accepted status, date, or documentation parity changed",
)
compact_adr = compact(adr)
required_adr_terms = (
    "exact durable rootCaptureId",
    "zero provider requests",
    "SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1",
    "The collection order is deterministic and local",
    "Every filing row in the selected root recent evidence",
    "exact canonical accession number is the only ADR-041 grouping identity",
    "`MULTIPLE_OCCURRENCES_EXACT_AGREEMENT`",
    "`MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT`",
    "No status, Boolean, API field, or display label may call it `COMPLETE_HISTORY`",
    "evidenceAvailableAt = max(root capturedAt, every selected segment capturedAt)",
    "content-derived identity deliberately excludes `assembledAt`",
    "returns the first durable manifest and its original `assembledAt`",
    "performs zero external requests",
    "does not consult `SEC_CONTACT_EMAIL`",
    "no new SEC API key",
    "existing PostgreSQL connection",
    "There is no scheduler, startup collector",
    "There is no latest-capture substitution",
)
require(
    all(term in compact_adr for term in required_adr_terms),
    "ADR-041 exact selection, ordering, reconciliation, PIT, replay, or zero-network semantics changed",
)

main_root = Path("apps/api/src/main/java/com/wallstreetreceipts/api")
test_root = Path("apps/api/src/test/java/com/wallstreetreceipts/api")
new_java_paths = {
    main_root / "application/filinghistory/PersistFilingHistoryCollectionManifestService.java",
    main_root / "application/port/out/FilingHistoryCollectionManifestAppendOutcome.java",
    main_root / "application/port/out/FilingHistoryCollectionManifestRepository.java",
    main_root / "config/FilingHistoryCollectionConfiguration.java",
    main_root / "domain/filing/FilingHistoryCollectionManifest.java",
    main_root / "infrastructure/persistence/JdbcFilingHistoryCollectionManifestRepository.java",
}
migration_path = Path(
    "apps/api/src/main/resources/db/migration/"
    "V8__sec_filing_history_collection_manifests.sql"
)
new_main_paths = new_java_paths | {migration_path}
new_test_paths = {
    test_root / "application/filinghistory/PersistFilingHistoryCollectionManifestServiceTest.java",
    test_root / "domain/filing/FilingHistoryCollectionManifestTest.java",
    test_root / "migration/FilingHistoryCollectionManifestPostgreSqlTest.java",
    test_root / "persistence/FilingHistoryCollectionManifestPersistenceTest.java",
    test_root / "support/FilingHistoryCollectionTestFixture.java",
}
postgres_test_path = test_root / "migration/PostgreSqlMigrationTest.java"
adr042_application_paths = {
    main_root / "application/filinghistory/ExecuteSecFilingHistoryCollectionAttemptService.java",
    main_root / "application/filinghistory/SingleJvmSecFilingHistoryCollectionAttemptMutex.java",
}
adr042_application_test_paths = {
    test_root / "application/filinghistory/ExecuteSecFilingHistoryCollectionAttemptServiceTest.java",
    test_root / "application/filinghistory/SingleJvmSecFilingHistoryCollectionAttemptMutexTest.java",
}
adr043_application_paths = {
    main_root / "application/filinghistory/ExactEvidenceNotAdmittedException.java",
    main_root / "application/filinghistory/OperatorRequestConflictException.java",
    main_root / "application/filinghistory/SecFilingHistoryCollectionAttemptNotFoundException.java",
    main_root / "application/filinghistory/SecFilingHistoryCollectionAttemptQueryService.java",
}
adr043_application_test_paths = {
    test_root / "application/filinghistory/SecFilingHistoryCollectionAttemptQueryServiceTest.java",
}
require(
    len(new_main_paths) == 7
    and len(new_test_paths) == 5
    and all(path.is_file() for path in new_main_paths | new_test_paths)
    and all(path.is_file() for path in (
        adr042_application_paths | adr042_application_test_paths
        | adr043_application_paths | adr043_application_test_paths
    ))
    and postgres_test_path.is_file(),
    "ADR-041 exact 7 main and 5 test surfaces changed",
)
require(
    set((main_root / "application/filinghistory").glob("*.java"))
        - adr042_application_paths - adr043_application_paths
        == {
            main_root / "application/filinghistory/PersistFilingHistoryCollectionManifestService.java"
        }
    and set((main_root / "application/port/out").glob(
        "FilingHistoryCollectionManifest*.java"
    )) == {
        main_root / "application/port/out/FilingHistoryCollectionManifestAppendOutcome.java",
        main_root / "application/port/out/FilingHistoryCollectionManifestRepository.java",
    }
    and set((main_root / "domain/filing").glob(
        "FilingHistoryCollectionManifest*.java"
    )) == {
        main_root / "domain/filing/FilingHistoryCollectionManifest.java"
    }
    and set((main_root / "infrastructure/persistence").glob(
        "*FilingHistoryCollectionManifest*.java"
    )) == {
        main_root / "infrastructure/persistence/JdbcFilingHistoryCollectionManifestRepository.java"
    },
    "ADR-041 application, port, domain, or persistence surface expanded",
)
require(
    set((test_root / "application/filinghistory").glob("*.java"))
        - adr042_application_test_paths - adr043_application_test_paths
        == {
            test_root / "application/filinghistory/PersistFilingHistoryCollectionManifestServiceTest.java"
        }
    and set((test_root / "domain/filing").glob(
        "FilingHistoryCollectionManifest*.java"
    )) == {
        test_root / "domain/filing/FilingHistoryCollectionManifestTest.java"
    }
    and set((test_root / "migration").glob(
        "FilingHistoryCollectionManifest*.java"
    )) == {
        test_root / "migration/FilingHistoryCollectionManifestPostgreSqlTest.java"
    }
    and set((test_root / "persistence").glob(
        "FilingHistoryCollectionManifest*.java"
    )) == {
        test_root / "persistence/FilingHistoryCollectionManifestPersistenceTest.java"
    }
    and set((test_root / "support").glob(
        "FilingHistoryCollection*.java"
    )) == {
        test_root / "support/FilingHistoryCollectionTestFixture.java"
    },
    "ADR-041 focused test surface expanded",
)

manifest_postgres_test_path = (
    test_root / "migration/FilingHistoryCollectionManifestPostgreSqlTest.java"
)

def pre_adr042_test_text(path):
    source = path.read_bytes().replace(b"\r\n", b"\n")
    if path != manifest_postgres_test_path:
        return source.decode("utf-8")
    require(
        hashlib.sha256(source).hexdigest()
            == "247e47a164e148311931117cc77662b1ea12ae43bb69a07912eeaf8ec6fbc3e4",
        "ADR-042 V8 PostgreSQL focused-test delta changed",
    )
    current_scope = (
        b'Flyway throughV8 = flyway(schema, "8");\n'
        b"        throughV8.migrate();\n\n"
        b"        assertThat(throughV8.info().current().getVersion().getVersion())"
    )
    historical_scope = (
        b"Flyway latest = flyway(schema, null);\n"
        b"        latest.migrate();\n\n"
        b"        assertThat(latest.info().current().getVersion().getVersion())"
    )
    require(
        source.count(current_scope) == 1
        and source.count(b'.isEqualTo("9");') == 1,
        "ADR-042 V8 isolated/current Flyway scopes changed",
    )
    source = source.replace(current_scope, historical_scope).replace(
        b'.isEqualTo("9");', b'.isEqualTo("8");'
    )
    require(
        hashlib.sha256(source).hexdigest()
            == "8a0b6e9622e59680fffb549270be4b6061871fbf2c6774ad843a98c48509102f",
        "ADR-042 V8 PostgreSQL focused-test reverse projection changed",
    )
    return source.decode("utf-8")

sources = {
    path.name: path.read_text(encoding="utf-8")
    for path in new_java_paths
}
tests = {
    path.name: pre_adr042_test_text(path)
    for path in new_test_paths
}
domain = sources["FilingHistoryCollectionManifest.java"]
service = sources[
    "PersistFilingHistoryCollectionManifestService.java"
]
outcome = sources[
    "FilingHistoryCollectionManifestAppendOutcome.java"
]
repository_port = sources[
    "FilingHistoryCollectionManifestRepository.java"
]
configuration = sources["FilingHistoryCollectionConfiguration.java"]
repository = sources[
    "JdbcFilingHistoryCollectionManifestRepository.java"
]
migration = migration_path.read_text(encoding="utf-8")

require(
    all(marker in domain for marker in (
        'SCHEMA_VERSION = "1.0.0"',
        'PROVIDER = "sec-edgar"',
        '"edgar-submissions-root-relative-collection-manifest"',
        '"SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1"',
        '"SEC_FILING_HISTORY_COLLECTION_MANIFEST_ID_V1"',
        '"SEC_FILING_HISTORY_COLLECTION_SELECTION_V1"',
        '"SEC_FILING_HISTORY_OCCURRENCE_PROJECTION_V1"',
        'ROOT_PARSER_VERSION = "SEC_SUBMISSIONS_CATALOG_V2"',
        '"SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1"',
        "BodyRetention.DURABLE_DECODED_BODY_RETAINED",
        "new TreeMap<>()",
        "new LinkedHashMap<>()",
        "new LinkedHashSet<>(entry.getValue()).size()",
        "root.recentFilings().get(sourceOrdinal)",
        "selectedByOrdinal.entrySet()",
        "occurrences.add(new FilingOccurrence(",
        "assembledAt must not precede evidenceAvailableAt",
        "selected segment captureId must be unique",
        "selected descriptorOrdinal must be unique",
        "selected capture must identify the exact durable root",
        "selected capture must match the exact root descriptor",
    )),
    "Manifest contract, exact durable bindings, order, occurrence preservation, or PIT changed",
)
require(
    record_fields(domain, "DescriptorMember") == (
        "int descriptorOrdinal, HistoricalFilingSegmentDescriptor descriptor, "
        "DescriptorSelectionState selectionState, String selectedSegmentCaptureId"
    )
    and record_fields(domain, "OccurrenceProjection") == (
        "String providerEventId, String accessionNumber, String form, "
        "LocalDate filingDate, LocalDate reportDate, Instant acceptedAt, "
        "URI primaryDocumentUri"
    )
    and record_fields(domain, "FilingOccurrence") == (
        "int occurrenceOrdinal, OccurrenceSourceKind sourceKind, "
        "Integer descriptorOrdinal, String segmentCaptureId, "
        "int sourceOrdinal, OccurrenceProjection projection, "
        "String projectionSha256"
    )
    and record_fields(domain, "AccessionGroup") == (
        "int groupOrdinal, String accessionNumber, long occurrenceCount, "
        "long distinctProjectionCount, AccessionComparison comparison"
    ),
    "Descriptor, occurrence projection, provenance, or accession-group fields changed",
)
require(
    enum_values(domain, "DescriptorSelectionState")
        == ["NOT_SELECTED", "SELECTED_EXACT_CAPTURE"]
    and enum_values(domain, "SelectionCoverage") == [
        "NO_ADVERTISED_DESCRIPTORS",
        "PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED",
        "ALL_ADVERTISED_DESCRIPTORS_SELECTED",
    ]
    and enum_values(domain, "OccurrenceSourceKind")
        == ["ROOT_RECENT", "HISTORICAL_SEGMENT"]
    and enum_values(domain, "AccessionComparison") == [
        "SINGLE_SOURCE_OCCURRENCE",
        "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT",
        "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT",
    ],
    "Closed descriptor, coverage, source-kind, or reconciliation vocabulary changed",
)
selection_identity = domain[
    domain.index("private static String selectionSha256("):
    domain.index("private static String manifestId(")
]
manifest_identity = domain[
    domain.index("private static String manifestId("):
    domain.index("private static String projectionSha256(")
]
projection_identity = domain[
    domain.index("private static String projectionSha256("):
    domain.index("private static void appendLengthPrefixed(")
]
require(
    "assembledAt" not in selection_identity
    and "assembledAt" not in manifest_identity
    and all(marker in selection_identity for marker in (
        "rootCaptureId",
        "descriptors.size()",
        "member.descriptorOrdinal()",
        "descriptor.fileName()",
        "descriptor.advertisedFilingCount()",
        "descriptor.advertisedFilingFrom()",
        "descriptor.advertisedFilingTo()",
        "member.selectionState().name()",
        "member.selectedSegmentCaptureId()",
    ))
    and all(marker in manifest_identity for marker in (
        "SCHEMA_VERSION", "PROVIDER", "PRODUCT", "POLICY_VERSION",
        "rootCaptureId", "selectionSha256",
    ))
    and all(marker in projection_identity for marker in (
        "projection.providerEventId()",
        "projection.accessionNumber()",
        "projection.form()",
        "projection.filingDate()",
        "projection.reportDate()",
        "projection.acceptedAt()",
        "projection.primaryDocumentUri()",
    )),
    "Selection/manifest identity, assembly-time exclusion, or canonical projection changed",
)
require(
    "Compares immutable selection-derived content while ignoring assembly time"
        in domain
    and "sameContentAs(that) && assembledAt.equals(that.assembledAt)"
        in domain
    and all(marker in domain for marker in (
        "occurrenceCount == 1 && distinctProjectionCount == 1",
        "occurrenceCount > 1 && distinctProjectionCount == 1",
        "occurrenceCount > 1 && distinctProjectionCount > 1",
        "primaryDocumentUri",
    )),
    "First-observation replay content or exact reconciliation classification changed",
)

require(
    all(marker in service for marker in (
        "FilingCatalogCaptureRepository rootRepository",
        "HistoricalFilingSegmentCaptureRepository segmentRepository",
        "FilingHistoryCollectionManifestRepository manifestRepository",
        "Clock clock",
        "rootRepository.findByCaptureId(rootCaptureId)",
        "segmentRepository",
        ".findByCaptureId(selection.segmentCaptureId())",
        "rootCaptureId.equals(root.captureId())",
        "selection.segmentCaptureId().equals(capture.captureId())",
        "requireUniqueSelections(ownedSelections)",
        "clock.instant().truncatedTo(ChronoUnit.MICROS)",
        "manifestRepository.append(manifest)",
    ))
    and "findLatestAtOrBefore(" not in service
    and "FilingCatalogCaptureProvider" not in service
    and "HistoricalFilingSegmentCaptureProvider" not in service,
    "Explicit exact-ID orchestration, injected clock, or no-latest/no-provider boundary changed",
)
require(
    record_fields(service, "DescriptorCaptureSelection")
        == "int descriptorOrdinal, String segmentCaptureId"
    and enum_values(outcome, "Status")
        == ["INSERTED", "IDENTICAL_REPLAY"]
    and all(marker in repository_port for marker in (
        "append(",
        "findByManifestId(String manifestId)",
        "findByManifestIdAtOrBefore(",
        "String manifestId",
        "Instant evaluationAsOf",
        "long count()",
    ))
    and "findLatest" not in repository_port
    and not re.search(
        r"\b(?:update|delete|remove|purge)\w*\s*\(",
        repository_port,
        re.IGNORECASE,
    ),
    "Append result, explicit manifest-ID PIT read, or append-only port changed",
)
require(
    "@Configuration(proxyBeanMethods = false)" in configuration
    and "PersistFilingHistoryCollectionManifestService" in configuration
    and "Clock clock" in configuration
    and all(marker not in configuration for marker in (
        "@ConditionalOnProperty", "@Scheduled", "CommandLineRunner",
        "ApplicationRunner", "System.getenv", "System.getProperty",
    )),
    "ADR-041 configuration must wire only an inert service with no new trigger or setting",
)

repository_markers = (
    "@Transactional",
    "reassemble(",
    "if (!verified.equals(manifest))",
    "findByNaturalIdentity(manifest)",
    'WHERE root_capture_id = :rootCaptureId',
    'AND policy_version = :policyVersion',
    'AND selection_sha256 = :selectionSha256',
    "findByManifestId(manifest.manifestId())",
    'sql += " ON CONFLICT DO NOTHING"',
    "insertDescriptors(manifest, selected)",
    "insertAccessionGroups(manifest)",
    "insertOccurrences(manifest)",
    "Status.IDENTICAL_REPLAY, existing",
    "assembled_at <= :evaluationAsOf",
    "ORDER BY descriptor_ordinal ASC",
    "ORDER BY group_ordinal ASC",
    "ORDER BY occurrence_ordinal ASC",
    "verifyManifestRow(row, reconstructed)",
    "verifyDescriptors(descriptors, reconstructed, selected)",
    "verifyAccessionGroups(readAccessionGroups(row.manifestId()), reconstructed)",
    "verifyOccurrences(readOccurrences(row.manifestId()), reconstructed)",
    "requireContiguousOrdinals(",
    "primaryDocumentUri() == null",
)
require(
    all(marker in repository for marker in repository_markers)
    and "ON CONFLICT DO UPDATE" not in repository.upper()
    and "DELETE FROM" not in repository.upper()
    and "findLatest" not in repository,
    "Atomic append, first-winner replay, exact reconstruction, PIT, or no-mutation SQL changed",
)
same_content = repository[
    repository.index("private static boolean sameContent("):
    repository.index("private boolean isPostgreSql()")
]
require(
    "assembledAt" not in same_content
    and all(marker in same_content for marker in (
        "manifestId()", "selectionSha256()", "rootCaptureId()",
        "rootCapturedAt()", "cik()", "evidenceAvailableAt()",
        "selectionCoverage()", "descriptors()", "occurrences()",
        "accessionGroups()",
    )),
    "Idempotent content comparison must exclude assembledAt and preserve all selected evidence",
)

tables = re.findall(
    r"^CREATE TABLE ([a-z0-9_]+) \(", migration, re.MULTILINE
)
sql_without_restrict = migration.replace("ON DELETE RESTRICT", "")
compact_migration = compact(migration)
require(
    tables == [
        "sec_filing_history_collection_manifests",
        "sec_filing_history_collection_descriptors",
        "sec_filing_history_collection_accession_groups",
        "sec_filing_history_collection_occurrences",
    ]
    and migration.count("ON DELETE RESTRICT") == 11
    and "ON DELETE CASCADE" not in migration
    and "CURRENT_TIMESTAMP" not in migration
    and "CREATE TRIGGER" not in migration
    and re.search(
        r"\bUPDATE\s+[A-Za-z_]", sql_without_restrict, re.IGNORECASE
    ) is None
    and re.search(
        r"\bDELETE\s+FROM\b", sql_without_restrict, re.IGNORECASE
    ) is None,
    "V8 must remain four append-only tables with exact restrictive source retention",
)
required_sql_markers = (
    "ADD CONSTRAINT uq_sec_catalog_manifest_root",
    "ADD CONSTRAINT uq_sec_catalog_recent_manifest_source",
    "ADD CONSTRAINT uq_sec_history_segment_manifest_member",
    "ADD CONSTRAINT uq_sec_history_filing_manifest_source",
    "UNIQUE (root_capture_id, policy_version, selection_sha256)",
    "FOREIGN KEY (root_capture_id, cik, root_captured_at)",
    "FOREIGN KEY ( manifest_id, root_capture_id, cik, root_captured_at )",
    "FOREIGN KEY ( root_capture_id, descriptor_ordinal, cik, root_captured_at, file_name, advertised_filing_count, advertised_filing_from, advertised_filing_to )",
    "FOREIGN KEY ( selected_segment_capture_id, root_capture_id, descriptor_ordinal, cik, root_captured_at, file_name, advertised_filing_count, advertised_filing_from, advertised_filing_to, selected_segment_captured_at )",
    "FOREIGN KEY (manifest_id, accession_number)",
    "FOREIGN KEY (manifest_id, root_source_capture_id)",
    "FOREIGN KEY ( manifest_id, descriptor_ordinal, segment_source_capture_id )",
    "FOREIGN KEY ( root_source_capture_id, source_row_ordinal, provider_event_id, accession_number )",
    "FOREIGN KEY ( segment_source_capture_id, source_row_ordinal, provider_event_id, accession_number )",
    "root_captured_at <= evidence_available_at",
    "evidence_available_at <= assembled_at",
    "PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED",
    "ALL_ADVERTISED_DESCRIPTORS_SELECTED",
    "NO_ADVERTISED_DESCRIPTORS",
    "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT",
    "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT",
    "SINGLE_SOURCE_OCCURRENCE",
    "source_kind = 'ROOT_RECENT'",
    "source_kind = 'HISTORICAL_SEGMENT'",
    "provider_event_id = accession_number",
    "primary_document_uri VARCHAR(2048)",
    "idx_sec_history_manifest_point_in_time",
)
require(
    all(marker in compact_migration for marker in required_sql_markers)
    and "assembled_at" not in compact(
        re.search(
            r"CONSTRAINT uq_sec_history_manifest_natural\s+UNIQUE\s*\((.*?)\)",
            migration,
            flags=re.DOTALL,
        ).group(1)
    ),
    "V8 exact source FKs, closed states/counts, nullable projection, or content identity changed",
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
    "ADR-041 must remain the current exact nine-migration surface",
)

domain_test = tests["FilingHistoryCollectionManifestTest.java"]
service_test = tests[
    "PersistFilingHistoryCollectionManifestServiceTest.java"
]
persistence_test = tests[
    "FilingHistoryCollectionManifestPersistenceTest.java"
]
postgres_collection_test = tests[
    "FilingHistoryCollectionManifestPostgreSqlTest.java"
]
fixture = tests["FilingHistoryCollectionTestFixture.java"]
require(
    all(method in domain_test for method in (
        "assemblesAllSelectedCapturesInRootDescriptorAndProviderRowOrder",
        "exposesPartialAndNoAdvertisedDescriptorCoverageWithoutRewritingRootState",
        "retainsEveryOccurrenceAndClassifiesExactAgreementAndNullableUriConflict",
        "identityIsStableAcrossInputAndAssemblyOrderButChangesWithSelection",
        "rejectsPendingAndUnsupportedRootContracts",
        "rejectsPendingAndUnsupportedSegmentContracts",
        "rejectsAnotherRootAndMismatchedRootDescriptorBindings",
        "rejectsDuplicateSelectedCaptureAndDuplicateDescriptorOrdinal",
        "rejectsAssemblyTimeBeforeEvidenceOrBeyondMicrosecondPrecision",
        "defensivelyOwnsSelectedInputAndPublishedLists",
    )),
    "ADR-041 deterministic domain ordering, reconciliation, identity, or rejection coverage changed",
)
require(
    all(method in service_test for method in (
        "readsOnlyExplicitCaptureIdsAndAppendsOneCanonicalManifest",
        "missingRootStopsBeforeSegmentLookupOrAppend",
        "missingExactSegmentStopsBeforeAppendWithoutLatestFallback",
        "duplicateSelectionsFailBeforeRepositoryReads",
        "explicitOrdinalMismatchAndClockBeforeEvidenceFailClosed",
        "repositoryIdentityMismatchFailsClosedBeforeAppend",
    ))
    and "never()).findLatestAtOrBefore(" in service_test
    and "verifyNoInteractions" in service_test,
    "ADR-041 exact-ID/no-latest orchestration and fail-closed coverage changed",
)
require(
    all(method in persistence_test for method in (
        "appendsAndReconstructsExactOrderedEvidenceWithNullableAgreementAndConflict",
        "keepsEveryDescriptorInRootOrderAndPersistsAnotherExactSelectionSeparately",
        "identicalSelectionReplayReturnsTheFirstAssembledObservation",
        "exactManifestIdPointInTimeReadClosesBeforeAtAndAfterAssembly",
        "validLookingSummaryAndSelectionHashTamperingFailClosedOnRead",
        "repositoryAndManifestExposeNoUpdateOrDeleteSurface",
    ))
    and "Status.IDENTICAL_REPLAY" in persistence_test
    and "findByManifestIdAtOrBefore" in persistence_test,
    "ADR-041 H2 exact replay, PIT, tamper, or append-only coverage changed",
)
require(
    all(method in postgres_collection_test for method in (
        "upgradesAnIsolatedV7SchemaToV8WithFourCollectionTables",
        "concurrentIdenticalSelectionReturnsOneWinnerObservationToBothCallers",
        "concurrentDifferentSelectionsAppendAsSeparateManifests",
        "childConstraintFailureRollsBackManifestAndExactSourceDeletesAreRestricted",
    ))
    and 'new PostgreSQLContainer<>("postgres:17-alpine")'
        in postgres_collection_test
    and "containsExactlyInAnyOrder(Status.INSERTED, Status.IDENTICAL_REPLAY)"
        in postgres_collection_test
    and "containsOnly(winner)" in postgres_collection_test
    and "assertNoCollectionOrphans" in postgres_collection_test
    and 'assertSqlState(appendFailure, "23514")'
        in postgres_collection_test
    and 'assertSqlState(segmentDeleteFailure, "23503")'
        in postgres_collection_test
    and 'assertSqlState(rootDeleteFailure, "23503")'
        in postgres_collection_test,
    "ADR-041 PostgreSQL V8, concurrent first-winner, atomicity, or restrict coverage changed",
)
require(
    "Exact replay-compatible source captures" in fixture
    and "BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE"
        in fixture
    and "new HistoricalFilingSegmentCapture(segment, decodedBody)"
        in fixture,
    "ADR-041 exact durable test evidence fixture changed",
)

postgres_test_bytes = postgres_test_path.read_bytes().replace(b"\r\n", b"\n")
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
    postgres_test_bytes.count(root_capture) == 1
    and postgres_test_bytes.count(bodies_before) == 2,
    "PostgreSQL decoded-body concurrency setup delta changed",
)
postgres_test_bytes = postgres_test_bytes.replace(
    root_capture, historical_root_capture
).replace(bodies_before, b"")
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
    require(
        postgres_test_bytes.count(assertion) == 1,
        f"PostgreSQL decoded-body {accessor.decode()} assertion delta changed",
    )
    postgres_test_bytes = postgres_test_bytes.replace(assertion, b"")
require(
    hashlib.sha256(postgres_test_bytes).hexdigest()
        == "c9f247b743a492a69fe84ebcd99ad24f14dda77053773f9c75390b4f6beca914",
    "ADR-042 PostgreSQL migration-test delta changed",
)
adr042_version_replacements = (
    (
        b"assertThat(flyway.info().applied()).hasSize(9);",
        b"assertThat(flyway.info().applied()).hasSize(8);",
        1,
    ),
    (
        b"assertThat(latest.info().current().getVersion().getVersion())"
        b'.isEqualTo("9");',
        b"assertThat(latest.info().current().getVersion().getVersion())"
        b'.isEqualTo("8");',
        3,
    ),
)
for current, historical, count in adr042_version_replacements:
    require(
        postgres_test_bytes.count(current) == count,
        "ADR-042 PostgreSQL Flyway-version delta changed",
    )
    postgres_test_bytes = postgres_test_bytes.replace(current, historical)
require(
    hashlib.sha256(postgres_test_bytes).hexdigest()
        == "013be72bd6c110c22ec87467a000dc5e3bfe574a67e6fec11e48404305891145",
    "ADR-042 PostgreSQL reverse projection changed",
)
version_replacements = (
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
for current, historical, count in version_replacements:
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

production_source = "\n".join(sources.values())
require(
    all(marker not in production_source for marker in (
        "@RestController", "@Controller", "@RequestMapping",
        "@GetMapping", "@PostMapping", "@Scheduled",
        "SchedulingConfigurer", "CommandLineRunner", "ApplicationRunner",
        "System.getenv", "System.getProperty", "System.out", "System.err",
        "Logger", "Slf4j", "SEC_CONTACT_EMAIL", "API_KEY", "APIKEY",
        "OAUTH", "ACCESS_TOKEN", "SECRET_KEY", "RestClient",
        "WebClient", "HttpClient", "URLConnection", "new Socket(",
    )),
    "ADR-041 must not publish, trigger, log, fetch, or request credentials",
)
focused_test_source = "\n".join(tests.values())
require(
    all(marker not in focused_test_source for marker in (
        "HttpClient.newHttpClient(", "RestClient.create(",
        "WebClient.create(", ".openConnection(", "new Socket(",
    )),
    "ADR-041 focused tests must remain deterministic and offline",
)
config_paths = {
    Path(".env.example"),
    Path("compose.yaml"),
    Path("apps/api/src/main/resources/application.yml"),
    Path("apps/api/src/main/resources/application-local.yml"),
}
for path in config_paths:
    source = path.read_text(encoding="utf-8", errors="ignore").lower()
    require(
        all(marker not in source for marker in (
            "filing_history_collection", "filing-history-collection",
            "sec_root_relative_accession_reconciliation",
        )),
        f"ADR-041 must not add an environment/configuration contract: {path}",
    )

publication_markers = (
    "filinghistorycollectionmanifest",
    "filing_history_collection_manifest",
    "sec_root_relative_accession_reconciliation_v1",
    "edgar-submissions-root-relative-collection-manifest",
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
        f"ADR-041 private manifest must not reach API, fixture, or web: {path}",
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
    "Default Maven/CI must never activate or invoke SEC collection",
)

print(
    "Validated ADR-041 exact 7 main and 5 test surfaces, exact durable-ID "
    "selection, root-first/provider order, occurrence-preserving accession "
    "reconciliation, content-derived first-winner replay, exact manifest-ID PIT, "
    "four-table append-only V8 persistence, and no API/UI/key/network/default live CI"
)
PYTHON
