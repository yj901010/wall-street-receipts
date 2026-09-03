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
    "decisions/ADR-042-sec-operator-controlled-collection-attempt.md"
)
document_paths = {
    adr_path,
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
}
require(
    all(path.is_file() for path in document_paths),
    "Missing ADR-042 decision, README, or implementation-log surface",
)
documents = {
    path: path.read_text(encoding="utf-8")
    for path in document_paths
}
adr = documents[adr_path]
require(
    adr.startswith(
        "# ADR-042 — SEC Operator-Controlled Collection Attempt\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    )
    and all("ADR-042" in documents[path] for path in document_paths),
    "ADR-042 title, accepted status, date, or documentation parity changed",
)
compact_adr = compact(adr)
required_adr_terms = (
    "CAPTURE_ROOT",
    "COLLECT_EXACT_ROOT",
    "canonical, nonzero, lowercase UUID",
    "maxProviderInvocations = 1",
    "At most one ordinal may use",
    "performs no provider access",
    "There is no descriptor loop",
    "PROVIDER_INVOCATION_NOT_STARTED",
    "PROVIDER_DISPATCHED_INDETERMINATE",
    "V9 adds four application append-only tables",
    "single-JVM attempt mutex is acquired nonblockingly",
    "No SEC API key, provider account, paid plan, OAuth credential",
    "SEC_CONTACT_EMAIL=<real monitored operational email>",
    "There is no scheduler, startup hook, polling cadence",
    "controller, OpenAPI operation, command-line runner, browser call",
    "does not provide an exactly-once request token",
)
require(
    all(term in compact_adr for term in required_adr_terms),
    "ADR-042 bounded command, replay, dispatch, credential, or non-scope semantics changed",
)

main_root = Path("apps/api/src/main/java/com/wallstreetreceipts/api")
test_root = Path("apps/api/src/test/java/com/wallstreetreceipts/api")
migration_path = Path(
    "apps/api/src/main/resources/db/migration/"
    "V9__sec_filing_collection_attempts.sql"
)
new_java_paths = {
    main_root / "application/filinghistory/ExecuteSecFilingHistoryCollectionAttemptService.java",
    main_root / "application/filinghistory/SingleJvmSecFilingHistoryCollectionAttemptMutex.java",
    main_root / "application/port/out/SecFilingHistoryCollectionAttemptClaimOutcome.java",
    main_root / "application/port/out/SecFilingHistoryCollectionAttemptCommitter.java",
    main_root / "application/port/out/SecFilingHistoryCollectionAttemptRepository.java",
    main_root / "application/port/out/SourceCaptureRequestException.java",
    main_root / "config/SecFilingHistoryCollectionAttemptConfiguration.java",
    main_root / "domain/filing/SecFilingHistoryCollectionAttempt.java",
    main_root / "infrastructure/persistence/JdbcSecFilingHistoryCollectionAttemptRepository.java",
}
new_main_paths = new_java_paths | {migration_path}
new_test_paths = {
    test_root / "application/filinghistory/ExecuteSecFilingHistoryCollectionAttemptServiceTest.java",
    test_root / "application/filinghistory/SingleJvmSecFilingHistoryCollectionAttemptMutexTest.java",
    test_root / "config/SecFilingHistoryCollectionAttemptConfigurationTest.java",
    test_root / "domain/filing/SecFilingHistoryCollectionAttemptTest.java",
    test_root / "infrastructure/provider/sec/SecProviderExceptionTypedContractTest.java",
    test_root / "migration/FilingCollectionAttemptPostgreSqlTest.java",
    test_root / "persistence/SecFilingHistoryCollectionAttemptPersistenceTest.java",
}
provider_exception_path = (
    main_root / "infrastructure/provider/sec/SecProviderException.java"
)
postgres_test_path = test_root / "migration/PostgreSqlMigrationTest.java"
manifest_postgres_test_path = (
    test_root / "migration/FilingHistoryCollectionManifestPostgreSqlTest.java"
)
modified_paths = {
    provider_exception_path,
    postgres_test_path,
    manifest_postgres_test_path,
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
    len(new_main_paths) == 10
    and len(new_test_paths) == 7
    and len(modified_paths) == 3
    and all(path.is_file() for path in (
        new_main_paths | new_test_paths | modified_paths
        | adr043_application_paths | adr043_application_test_paths
    )),
    "ADR-042 exact 10 main, 7 test, and 3 modified surfaces changed",
)

require(
    set((main_root / "application/filinghistory").glob(
        "*SecFilingHistoryCollectionAttempt*.java"
    )) - adr043_application_paths == {
        main_root / "application/filinghistory/ExecuteSecFilingHistoryCollectionAttemptService.java",
        main_root / "application/filinghistory/SingleJvmSecFilingHistoryCollectionAttemptMutex.java",
    }
    and set((main_root / "application/port/out").glob(
        "SecFilingHistoryCollectionAttempt*.java"
    )) == {
        main_root / "application/port/out/SecFilingHistoryCollectionAttemptClaimOutcome.java",
        main_root / "application/port/out/SecFilingHistoryCollectionAttemptCommitter.java",
        main_root / "application/port/out/SecFilingHistoryCollectionAttemptRepository.java",
    }
    and set((main_root / "application/port/out").glob(
        "SourceCaptureRequestException.java"
    )) == {
        main_root / "application/port/out/SourceCaptureRequestException.java"
    }
    and set((main_root / "config").glob(
        "SecFilingHistoryCollectionAttempt*.java"
    )) == {
        main_root / "config/SecFilingHistoryCollectionAttemptConfiguration.java"
    }
    and set((main_root / "domain/filing").glob(
        "SecFilingHistoryCollectionAttempt*.java"
    )) == {
        main_root / "domain/filing/SecFilingHistoryCollectionAttempt.java"
    }
    and set((main_root / "infrastructure/persistence").glob(
        "*SecFilingHistoryCollectionAttempt*.java"
    )) == {
        main_root / "infrastructure/persistence/JdbcSecFilingHistoryCollectionAttemptRepository.java"
    },
    "ADR-042 application, port, config, domain, or persistence surface expanded",
)
require(
    set((test_root / "application/filinghistory").glob(
        "*SecFilingHistoryCollectionAttempt*.java"
    )) - adr043_application_test_paths == {
        test_root / "application/filinghistory/ExecuteSecFilingHistoryCollectionAttemptServiceTest.java",
        test_root / "application/filinghistory/SingleJvmSecFilingHistoryCollectionAttemptMutexTest.java",
    }
    and set((test_root / "config").glob(
        "SecFilingHistoryCollectionAttempt*.java"
    )) == {
        test_root / "config/SecFilingHistoryCollectionAttemptConfigurationTest.java"
    }
    and set((test_root / "domain/filing").glob(
        "SecFilingHistoryCollectionAttempt*.java"
    )) == {
        test_root / "domain/filing/SecFilingHistoryCollectionAttemptTest.java"
    }
    and set((test_root / "infrastructure/provider/sec").glob(
        "SecProviderExceptionTypedContractTest.java"
    )) == {
        test_root / "infrastructure/provider/sec/SecProviderExceptionTypedContractTest.java"
    }
    and set((test_root / "migration").glob(
        "FilingCollectionAttemptPostgreSqlTest.java"
    )) == {
        test_root / "migration/FilingCollectionAttemptPostgreSqlTest.java"
    }
    and set((test_root / "persistence").glob(
        "SecFilingHistoryCollectionAttempt*.java"
    )) == {
        test_root / "persistence/SecFilingHistoryCollectionAttemptPersistenceTest.java"
    },
    "ADR-042 focused test surface expanded",
)

adr043_repository_path = (
    main_root / "infrastructure/persistence/"
    "JdbcSecFilingHistoryCollectionAttemptRepository.java"
)
adr043_persistence_test_path = (
    test_root / "persistence/"
    "SecFilingHistoryCollectionAttemptPersistenceTest.java"
)

def pre_adr043_text(path):
    source = path.read_bytes().replace(b"\r\n", b"\n")
    if path == adr043_repository_path:
        replacements = (
            (
                b"import com.wallstreetreceipts.api.application.filinghistory.ExactEvidenceNotAdmittedException;\n"
                b"import com.wallstreetreceipts.api.application.filinghistory.OperatorRequestConflictException;\n",
                b"",
                1,
            ),
            (
                b"            throw new OperatorRequestConflictException();\n",
                b"            throw new IllegalArgumentException(\n"
                b"                    \"operatorRequestId is already bound to another command\");\n",
                1,
            ),
            (
                b"            return new ExactEvidenceNotAdmittedException();\n",
                b"            return new IllegalArgumentException(\n"
                b"                    \"collection attempt exact evidence was not accepted\");\n",
                1,
            ),
        )
        expected = "7d4633fca321607f0ee1f27700161891e3b85d37efeecf85e2d1042a39135566"
    elif path == adr043_persistence_test_path:
        replacements = (
            (
                b"import com.wallstreetreceipts.api.application.filinghistory.ExactEvidenceNotAdmittedException;\n"
                b"import com.wallstreetreceipts.api.application.filinghistory.OperatorRequestConflictException;\n",
                b"",
                1,
            ),
            (
                b".isExactlyInstanceOf(OperatorRequestConflictException.class)",
                b".isInstanceOf(IllegalArgumentException.class)",
                1,
            ),
            (
                b".isExactlyInstanceOf(ExactEvidenceNotAdmittedException.class)",
                b".isInstanceOf(IllegalArgumentException.class)",
                2,
            ),
        )
        expected = "e7baf3de28924f1862d0cb65062e4e21917863be74192291035e0273cf6b1448"
    else:
        return source.decode("utf-8")
    for current, historical, count in replacements:
        require(
            source.count(current) == count,
            f"ADR-043 typed HTTP exception delta changed: {path}",
        )
        source = source.replace(current, historical)
    require(
        hashlib.sha256(source).hexdigest() == expected,
        f"ADR-043 historical ADR-042 reverse projection changed: {path}",
    )
    return source.decode("utf-8")

sources = {
    path.name: pre_adr043_text(path)
    for path in new_java_paths
}
tests = {
    path.name: pre_adr043_text(path)
    for path in new_test_paths
}
domain = sources["SecFilingHistoryCollectionAttempt.java"]
service = sources["ExecuteSecFilingHistoryCollectionAttemptService.java"]
mutex = sources["SingleJvmSecFilingHistoryCollectionAttemptMutex.java"]
claim_outcome = sources[
    "SecFilingHistoryCollectionAttemptClaimOutcome.java"
]
committer = sources["SecFilingHistoryCollectionAttemptCommitter.java"]
repository_port = sources[
    "SecFilingHistoryCollectionAttemptRepository.java"
]
request_exception = sources["SourceCaptureRequestException.java"]
configuration = sources[
    "SecFilingHistoryCollectionAttemptConfiguration.java"
]
repository = sources[
    "JdbcSecFilingHistoryCollectionAttemptRepository.java"
]
provider_exception = provider_exception_path.read_text(encoding="utf-8")
migration = migration_path.read_text(encoding="utf-8")

require(
    record_fields(domain, "SecFilingHistoryCollectionAttempt") == (
        "String attemptId, String commandSha256, String operatorRequestId, "
        "CommandKind commandKind, String cik, String rootCaptureId, "
        "List<DescriptorAction> descriptorActions, Instant requestedAt, "
        "ProviderDispatch providerDispatch, TerminalOutcome terminalOutcome"
    )
    and record_fields(domain, "DescriptorAction") == (
        "int descriptorOrdinal, DescriptorActionKind actionKind, "
        "String selectedSegmentCaptureId"
    )
    and record_fields(domain, "ProviderDispatch") == (
        "ProviderOperation operation, Integer descriptorOrdinal, "
        "Instant dispatchedAt"
    )
    and record_fields(domain, "ArtifactAppend") == (
        "String artifactId, ArtifactAppendStatus status"
    )
    and record_fields(domain, "TerminalOutcome") == (
        "TerminalStatus status, TerminalStage stage, "
        "RequestDisposition requestDisposition, FailureCode failureCode, "
        "Integer httpStatus, ArtifactAppend rootArtifact, "
        "ArtifactAppend segmentArtifact, ArtifactAppend manifestArtifact, "
        "Instant completedAt"
    ),
    "ADR-042 immutable command, dispatch, artifact, or terminal fields changed",
)
require(
    enum_values(domain, "CommandKind")
        == ["CAPTURE_ROOT", "COLLECT_EXACT_ROOT"]
    and enum_values(domain, "DescriptorActionKind")
        == ["SELECT_EXACT", "CAPTURE_NOW"]
    and enum_values(domain, "ProviderOperation")
        == ["CAPTURE_ROOT", "CAPTURE_HISTORICAL_SEGMENT"]
    and enum_values(domain, "LifecycleState") == [
        "PLANNED",
        "PROVIDER_DISPATCHED_INDETERMINATE",
        "TERMINAL_SUCCEEDED",
        "TERMINAL_FAILED_KNOWN",
    ]
    and enum_values(domain, "TerminalStatus")
        == ["SUCCEEDED", "FAILED_KNOWN"]
    and enum_values(domain, "TerminalStage") == [
        "EXACT_EVIDENCE_VALIDATION",
        "PROVIDER_GATE",
        "ROOT_CAPTURE",
        "SEGMENT_CAPTURE",
        "MANIFEST_ASSEMBLY",
        "LOCAL_COMMIT",
    ]
    and enum_values(domain, "RequestDisposition") == [
        "NO_PROVIDER_INVOCATION",
        "PROVIDER_INVOCATION_NOT_STARTED",
        "PROVIDER_RESPONSE_RECEIVED",
        "PROVIDER_START_OR_RESPONSE_UNKNOWN",
    ]
    and enum_values(domain, "FailureCode") == [
        "EXACT_EVIDENCE_VALIDATION_FAILED",
        "PROVIDER_GATE_CLOSED",
        "PROVIDER_REQUEST_FAILED",
        "PROVIDER_HTTP_STATUS",
        "PROVIDER_RESPONSE_UNREADABLE",
        "PROVIDER_RESPONSE_TOO_LARGE",
        "PROVIDER_RESPONSE_INVALID",
        "SOURCE_CAPTURE_PERSISTENCE_FAILED",
        "MANIFEST_ASSEMBLY_FAILED",
        "LOCAL_PERSISTENCE_FAILED",
    ]
    and enum_values(domain, "ArtifactAppendStatus")
        == ["NOT_APPLICABLE", "INSERTED", "IDENTICAL_REPLAY"],
    "ADR-042 command, lifecycle, failure, disposition, or artifact vocabulary changed",
)
require(
    all(marker in domain for marker in (
        'SCHEMA_VERSION = "1.0.0"',
        'PROVIDER = "sec-edgar"',
        'PRODUCT = "edgar-submissions-operator-collection-attempt"',
        '"SEC_OPERATOR_CONTROLLED_COLLECTION_ATTEMPT_V1"',
        "MAX_PROVIDER_INVOCATIONS = 1",
        '"SEC_FILING_COLLECTION_ATTEMPT_COMMAND_V1"',
        '"SEC_FILING_COLLECTION_ATTEMPT_ID_V1"',
        "owned.sort(Comparator.comparingInt(DescriptorAction::descriptorOrdinal))",
        "descriptorOrdinal must be unique within one attempt",
        "selectedSegmentCaptureId must be unique within one attempt",
        "one attempt may contain at most one CAPTURE_NOW action",
        "provider dispatch is append-only and already exists",
        "terminal outcome is append-only and already exists",
        "PROVIDER_DISPATCHED_INDETERMINATE",
        "knownAt(Instant evaluationAsOf)",
        "httpStatus == 200",
        "successful CAPTURE_ROOT must persist exactly one root artifact",
        "successful COLLECT_EXACT_ROOT must persist its exact planned artifacts",
        "a known failed attempt cannot claim atomically committed artifacts",
    ))
    and "requestedAt" not in domain[
        domain.index("private static String commandSha256("):
        domain.index("private static String attemptId(")
    ],
    "ADR-042 content identity, canonical plan, one-dispatch, terminal, or PIT invariant changed",
)

require(
    enum_values(claim_outcome, "Status") == [
        "CLAIMED", "IDENTICAL_REPLAY"
    ]
    and enum_values(request_exception, "FailureKind") == [
        "PROVIDER_GATE_CLOSED",
        "REQUEST_FAILED",
        "HTTP_STATUS",
        "RESPONSE_UNREADABLE",
        "RESPONSE_TOO_LARGE",
        "RESPONSE_INVALID",
    ]
    and "httpStatus == 200" in request_exception
    and "response bodies, request headers, credentials" in request_exception
    and all(marker in repository_port for marker in (
        "claim(",
        "appendProviderDispatch(",
        "appendTerminalOutcome(",
        "findByAttemptId(",
        "findByAttemptIdAtOrBefore(",
        "long count()",
    ))
    and all(marker in committer for marker in (
        "commitRootCaptureSuccess(",
        "commitSelectionOnlyCollectionSuccess(",
        "commitCapturedSegmentCollectionSuccess(",
    )),
    "ADR-042 claim, typed failure, repository, or atomic committer port changed",
)
require(
    "extends SourceCaptureRequestException" in provider_exception
    and all(marker in provider_exception for marker in (
        "FailureKind.HTTP_STATUS",
        "FailureKind.RESPONSE_UNREADABLE",
        "FailureKind.RESPONSE_INVALID",
        "FailureKind.RESPONSE_TOO_LARGE",
        "FailureKind.PROVIDER_GATE_CLOSED",
    ))
    and "Throwable" not in provider_exception
    and "initCause" not in provider_exception,
    "SEC provider failures must remain closed, typed, and sanitized",
)

require(
    service.count("provider.loadCatalogCapture(") == 1
    and service.count("provider.loadHistoricalSegmentCapture(") == 1
    and service.count("mutex.tryAcquire()") == 2
    and all(marker in service for marker in (
        "if (claim.replay())",
        "return claim.attempt();",
        "findByCaptureId(attempt.rootCaptureId())",
        "findByCaptureId(action.selectedSegmentCaptureId())",
        "validateExactEvidence(claim.attempt())",
        "commitSelectionOnlyCollectionSuccess(",
        "commitRootCaptureSuccess(",
        "commitCapturedSegmentCollectionSuccess(",
        "RequestDisposition.NO_PROVIDER_INVOCATION",
        "RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED",
        "RequestDisposition.PROVIDER_RESPONSE_RECEIVED",
        "RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN",
        "BodyRetention.DURABLE_DECODED_BODY_RETAINED",
        "BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE",
    ))
    and all(marker not in service for marker in (
        "findLatest",
        "Thread.sleep",
        "@Scheduled",
        "CommandLineRunner",
        "ApplicationRunner",
    ))
    and "compareAndSet(false, true)" in mutex
    and "implements AutoCloseable" in mutex,
    "ADR-042 zero-retry replay, exact-evidence preflight, mutex, or one-provider path changed",
)
require(
    "@Configuration(proxyBeanMethods = false)" in configuration
    and "@ConditionalOnBean" in configuration
    and "ObjectProvider<FilingCatalogCaptureProvider>" in configuration
    and "ObjectProvider<HistoricalFilingSegmentCaptureProvider>"
        in configuration
    and "Optional.ofNullable(rootProvider.getIfUnique())"
        in configuration
    and "Optional.ofNullable(segmentProvider.getIfUnique())"
        in configuration
    and all(marker not in configuration for marker in (
        "@ConditionalOnProperty",
        "@Scheduled",
        "CommandLineRunner",
        "ApplicationRunner",
        "System.getenv",
        "System.getProperty",
    )),
    "ADR-042 configuration must wire an inert optional-provider service only",
)

require(
    "implements SecFilingHistoryCollectionAttemptRepository,"
        in repository
    and "SecFilingHistoryCollectionAttemptCommitter" in repository
    and all(marker in repository for marker in (
        "INSERT INTO sec_filing_collection_attempts",
        "INSERT INTO sec_filing_collection_attempt_descriptor_actions",
        "INSERT INTO sec_filing_collection_attempt_provider_dispatches",
        "INSERT INTO sec_filing_collection_attempt_outcomes",
        "ON CONFLICT DO NOTHING",
        "ORDER BY descriptor_ordinal ASC",
        "requested_at <= :evaluationAsOf",
        "dispatched_at <= :evaluationAsOf",
        "completed_at <= :evaluationAsOf",
        "descriptor_action_count",
        "provider_dispatched_at",
        "verifyDispatchSummary(",
        "verifyOutcomeSummary(",
        "verifyTerminalArtifacts(",
        "sameCommandAs(proposed)",
    ))
    and re.search(r"\bUPDATE\s+[A-Za-z_]", repository, re.IGNORECASE)
        is None
    and re.search(r"\bDELETE\s+FROM\b", repository, re.IGNORECASE)
        is None
    and "ON CONFLICT DO UPDATE" not in repository.upper(),
    "ADR-042 append-only claim/replay, PIT reconstruction, or exact artifact validation changed",
)

tables = re.findall(
    r"^CREATE TABLE ([a-z0-9_]+) \(",
    migration,
    flags=re.MULTILINE,
)
sql_without_restrict = migration.replace("ON DELETE RESTRICT", "")
compact_migration = compact(migration)
require(
    tables == [
        "sec_filing_collection_attempts",
        "sec_filing_collection_attempt_descriptor_actions",
        "sec_filing_collection_attempt_provider_dispatches",
        "sec_filing_collection_attempt_outcomes",
    ]
    and migration.count("ON DELETE RESTRICT") == 17
    and "ON DELETE CASCADE" not in migration
    and "CURRENT_TIMESTAMP" not in migration
    and "CREATE TRIGGER" not in migration
    and re.search(
        r"\bUPDATE\s+[A-Za-z_]", sql_without_restrict, re.IGNORECASE
    ) is None
    and re.search(
        r"\bDELETE\s+FROM\b", sql_without_restrict, re.IGNORECASE
    ) is None,
    "V9 must remain four append-only tables with restrictive source retention",
)
required_sql_markers = (
    "UNIQUE (operator_request_id)",
    "max_provider_invocations = 1",
    "descriptor_action_count >= 0",
    "command_kind = 'CAPTURE_ROOT'",
    "command_kind = 'COLLECT_EXACT_ROOT'",
    "action_kind = 'SELECT_EXACT'",
    "action_kind = 'CAPTURE_NOW'",
    "UNIQUE (attempt_id, capture_now_slot)",
    "provider_operation = 'CAPTURE_HISTORICAL_SEGMENT'",
    "attempt_id CHAR(64) PRIMARY KEY",
    "provider_dispatched_at",
    "PROVIDER_INVOCATION_NOT_STARTED",
    "PROVIDER_RESPONSE_RECEIVED",
    "PROVIDER_START_OR_RESPONSE_UNKNOWN",
    "http_status <> 200",
    "terminal_status = 'SUCCEEDED'",
    "terminal_status = 'FAILED_KNOWN'",
    "terminal_stage = 'ROOT_CAPTURE'",
    "terminal_stage = 'MANIFEST_ASSEMBLY'",
    "root_append_status IN ('INSERTED', 'IDENTICAL_REPLAY')",
    "segment_append_status = 'NOT_APPLICABLE'",
    "manifest_append_status = 'NOT_APPLICABLE'",
    "FOREIGN KEY (root_capture_id)",
    "FOREIGN KEY (manifest_artifact_id, root_capture_id)",
    "attempt_requested_at <= completed_at",
    "provider_dispatched_at <= completed_at",
    "idx_sec_collection_attempt_point_in_time",
)
require(
    all(marker in compact_migration for marker in required_sql_markers),
    "V9 exact plan/action/dispatch/outcome XOR, FK, status, or PIT constraint changed",
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
    "ADR-042 must remain the current exact nine-migration surface",
)

domain_test = tests["SecFilingHistoryCollectionAttemptTest.java"]
service_test = tests[
    "ExecuteSecFilingHistoryCollectionAttemptServiceTest.java"
]
mutex_test = tests[
    "SingleJvmSecFilingHistoryCollectionAttemptMutexTest.java"
]
config_test = tests[
    "SecFilingHistoryCollectionAttemptConfigurationTest.java"
]
typed_test = tests["SecProviderExceptionTypedContractTest.java"]
postgres_test = tests["FilingCollectionAttemptPostgreSqlTest.java"]
persistence_test = tests[
    "SecFilingHistoryCollectionAttemptPersistenceTest.java"
]
require(
    all(method in domain_test for method in (
        "plansCanonicalRootCommandWithStableContentIdentities",
        "canonicalizesCollectionActionsByExactDescriptorOrdinal",
        "rejectsNoncanonicalOperatorRequestAndInvalidCommandShapes",
        "rejectsDuplicateActionsAndMoreThanOneProviderInvocation",
        "dispatchIsAppendOnlyAndAloneMeansIndeterminate",
        "successfulRootCaptureProducesOnlyExactRootArtifact",
        "successfulSelectionOnlyCollectionUsesNoProviderAndProducesManifest",
        "successfulCapturedSegmentCollectionProducesSegmentAndManifest",
        "knownFailureHasClosedShapeAndCannotClaimArtifacts",
        "providerGateCanRejectBeforeAProviderDispatchIsRecorded",
        "failureCodesRejectIncompatibleCommandStageAndDispositionShapes",
        "pointInTimeViewNeverBackdatesHeaderDispatchOrTerminal",
        "rejectsTamperedIdentityAndNonMicrosecondLedgerTimes",
    )),
    "ADR-042 domain identity, lifecycle, closed-failure, or PIT coverage changed",
)
require(
    all(method in service_test for method in (
        "captureRootClaimsDispatchesOnceAndUsesOnlyTheAtomicSuccessCommitter",
        "identicalReplayReturnsExistingStateWithoutEvidenceProviderMutexOrCommitterWork",
        "busySingleJvmMutexTerminatesKnownNotStartedWithoutDispatchOrProviderCall",
        "typedHttpFailurePersistsExactKnownStatusWithoutRetryOrCommitterCall",
        "providerPortGateClosedAfterDispatchRetainsDispatchAndMarksNotStarted",
        "unexpectedProviderFailureIsUnknownAndNeverRetried",
        "selectionOnlyValidatesEveryExactCaptureThenCommitsWithZeroProviderCalls",
        "unreconstructableClaimedExactSelectionTerminatesBeforeDispatchOrManifestCommit",
        "captureNowValidatesAllSelectionsThenDispatchesOnlyOneExactSegment",
        "missingProviderBeanTerminatesAtGateWithoutMutexDispatchOrNetwork",
        "committerFailureDoesNotInventTerminalOrRepeatTheProviderInvocation",
        "mismatchedCommitterArtifactIsRejectedInsteadOfBeingTrusted",
    ))
    and "ownershipIsNonblockingExclusiveReleasedAndIdempotentlyClosed"
        in mutex_test
    and "createsSelectionCapableServiceWithoutSecProviderBeansOrEnabledFlag"
        in config_test
    and "secFailuresExposeOnlyTheClosedProviderNeutralClassification"
        in typed_test,
    "ADR-042 replay, one-invocation, mutex, optional-provider, or typed-failure coverage changed",
)
require(
    all(method in persistence_test for method in (
        "claimsOriginalCommandOnceAndReconstructsDispatchAndFailurePointInTime",
        "concurrentClaimConvergesOnOneHeaderAndOneCanonicalActionSet",
        "rejectsMissingRootEvidenceWithoutRetainingAnAttemptOrLeakingSqlDetails",
        "rejectsMissingSelectedSegmentAndRollsBackItsAlreadyInsertedHeader",
        "atomicallyCommitsInsertedAndIdenticalReplayRootArtifacts",
        "atomicallyAssemblesSelectionOnlyManifestFromExactStoredCapture",
        "atomicallyCommitsCapturedSegmentManifestAndCanonicalActionOrder",
        "rollsBackPendingSegmentWhenManifestCannotBeAssembled",
        "failsClosedOnStoredActionCountTamperingAndExposesNoMutationSurface",
    ))
    and all(method in postgres_test for method in (
        "upgradesV8AndAppliesFreshV9WithFourAttemptTables",
        "enforcesClosedPlanOutcomeAndExactArtifactForeignKeys",
        "concurrentIdenticalOperatorRequestClaimConvergesToOneAttempt",
        "childFailureRollsBackAttemptWithoutOrphans",
    ))
    and '@Testcontainers(disabledWithoutDocker = true)' in postgres_test
    and 'new PostgreSQLContainer<>("postgres:17-alpine")'
        in postgres_test
    and "assertNoAttemptOrphans" in postgres_test
    and postgres_test.count("assertRestrictDelete(") == 5
    and postgres_test.count(
        'assertSqlState(failure, "23503")'
    ) >= 2
    and 'assertSqlState(invalidProviderBudget, "23514")'
        in postgres_test,
    "ADR-042 H2/PostgreSQL claim, atomicity, rollback, FK, or restrict coverage changed",
)

provider_bytes = provider_exception_path.read_bytes().replace(
    b"\r\n", b"\n"
)
require(
    hashlib.sha256(provider_bytes).hexdigest()
        == "b7600e1523a8ff97e22aa6c2ba6277ee5267459157feb28670f029cc2e5ce4f0",
    "ADR-042 modified SEC provider exception bytes changed",
)
postgres_bytes = postgres_test_path.read_bytes().replace(b"\r\n", b"\n")
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
    postgres_bytes.count(root_capture) == 1
    and postgres_bytes.count(bodies_before) == 2,
    "PostgreSQL decoded-body concurrency setup delta changed",
)
postgres_bytes = postgres_bytes.replace(
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
        postgres_bytes.count(assertion) == 1,
        f"PostgreSQL decoded-body {accessor.decode()} assertion delta changed",
    )
    postgres_bytes = postgres_bytes.replace(assertion, b"")
require(
    hashlib.sha256(postgres_bytes).hexdigest()
        == "c9f247b743a492a69fe84ebcd99ad24f14dda77053773f9c75390b4f6beca914",
    "ADR-042 modified PostgreSQL migration-test bytes changed",
)
postgres_replacements = (
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
for current, historical, count in postgres_replacements:
    require(
        postgres_bytes.count(current) == count,
        "ADR-042 PostgreSQL current-version delta changed",
    )
    postgres_bytes = postgres_bytes.replace(current, historical)
require(
    hashlib.sha256(postgres_bytes).hexdigest()
        == "013be72bd6c110c22ec87467a000dc5e3bfe574a67e6fec11e48404305891145",
    "ADR-042 PostgreSQL migration-test reverse projection changed",
)

manifest_postgres_bytes = (
    manifest_postgres_test_path.read_bytes().replace(b"\r\n", b"\n")
)
require(
    hashlib.sha256(manifest_postgres_bytes).hexdigest()
        == "247e47a164e148311931117cc77662b1ea12ae43bb69a07912eeaf8ec6fbc3e4",
    "ADR-042 modified V8 PostgreSQL focused-test bytes changed",
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
    manifest_postgres_bytes.count(current_scope) == 1
    and manifest_postgres_bytes.count(b'.isEqualTo("9");') == 1,
    "ADR-042 V8 isolated/current migration-test delta changed",
)
manifest_postgres_bytes = manifest_postgres_bytes.replace(
    current_scope, historical_scope
).replace(b'.isEqualTo("9");', b'.isEqualTo("8");')
require(
    hashlib.sha256(manifest_postgres_bytes).hexdigest()
        == "8a0b6e9622e59680fffb549270be4b6061871fbf2c6774ad843a98c48509102f",
    "ADR-042 V8 PostgreSQL focused-test reverse projection changed",
)

production_source = "\n".join(
    [*sources.values(), provider_exception]
)
require(
    all(marker not in production_source for marker in (
        "@RestController",
        "@Controller",
        "@RequestMapping",
        "@GetMapping",
        "@PostMapping",
        "@Scheduled",
        "SchedulingConfigurer",
        "CommandLineRunner",
        "ApplicationRunner",
        "System.getenv",
        "System.getProperty",
        "System.out",
        "System.err",
        "Logger",
        "Slf4j",
        "SEC_CONTACT_EMAIL",
        "API_KEY",
        "APIKEY",
        "OAUTH",
        "ACCESS_TOKEN",
        "SECRET_KEY",
        "RestClient",
        "WebClient",
        "HttpClient",
        "URLConnection",
        "new Socket(",
    )),
    "ADR-042 must not publish, trigger, log, fetch, or request credentials",
)
focused_test_source = "\n".join(tests.values())
require(
    all(marker not in focused_test_source for marker in (
        "HttpClient.newHttpClient(",
        "RestClient.create(",
        "WebClient.create(",
        ".openConnection(",
        "new Socket(",
    )),
    "ADR-042 focused tests must remain deterministic without SEC network",
)
config_paths = {
    Path(".env.example"),
    Path("compose.yaml"),
    Path("apps/api/src/main/resources/application.yml"),
    Path("apps/api/src/main/resources/application-local.yml"),
    Path("apps/api/src/test/resources/application-test.yml"),
}
for path in config_paths:
    source = path.read_text(
        encoding="utf-8", errors="ignore"
    ).lower()
    require(
        all(marker not in source for marker in (
            "sec_filing_collection_attempt",
            "sec-filing-collection-attempt",
            "operator-controlled-collection",
            "sec_operator_controlled_collection_attempt",
        )),
        f"ADR-042 must not add an environment/configuration contract: {path}",
    )

publication_markers = (
    "secfilinghistorycollectionattempt",
    "sec_filing_collection_attempt",
    "sec_operator_controlled_collection_attempt_v1",
    "edgar-submissions-operator-collection-attempt",
)
publication_paths = {
    Path("contracts/openapi.yaml"),
    *Path("schemas").glob("*.json"),
    *(path for path in Path("fixtures/v1").rglob("*")
      if path.is_file()),
    *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
      for path in root.rglob("*") if path.is_file()),
}
for path in publication_paths:
    source = path.read_text(
        encoding="utf-8", errors="ignore"
    ).lower()
    require(
        not any(marker in source for marker in publication_markers),
        f"ADR-042 private attempt ledger must not reach API, fixture, or web: {path}",
    )

workflow = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")

def without_step(source, name):
    step = f"\n      - name: {name}\n"
    start = source.index(step)
    end = source.index("\n      - name: ", start + len(step))
    return source[:start] + source[end:]

workflow_without_sec_guards = workflow
for name in (
    "Guard SEC EDGAR public-provider foundation",
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
compact_workflow = compact(workflow_without_sec_guards)
focused_command = (
    "./mvnw -B -ntp "
    "-Dtest=SecFilingHistoryCollectionAttemptTest,"
    "ExecuteSecFilingHistoryCollectionAttemptServiceTest,"
    "SingleJvmSecFilingHistoryCollectionAttemptMutexTest,"
    "SecFilingHistoryCollectionAttemptConfigurationTest,"
    "SecProviderExceptionTypedContractTest,"
    "SecFilingHistoryCollectionAttemptPersistenceTest,"
    "FilingCollectionAttemptPostgreSqlTest test"
)
require(
    workflow_without_sec_guards.count(
        "run: ./mvnw -B -ntp verify"
    ) == 1
    and compact_workflow.count(focused_command) == 1
    and all(marker not in workflow_without_sec_guards for marker in (
        "SEC_LIVE_SMOKE",
        "-Psec-live-smoke",
        "src/sec-live-smoke-test",
        "SecEdgarLiveSmokeIT",
        "sec.live-smoke.profile",
    ))
    and re.search(
        r"(?:curl|wget|Invoke-WebRequest)[^\n]*data\.sec\.gov",
        workflow_without_sec_guards,
        re.IGNORECASE,
    ) is None,
    "Default Maven/CI must run the exact offline ADR-042 suite without SEC live collection",
)

print(
    "Validated ADR-042 exact 10+7 new and three modified surfaces, "
    "content-derived command identity, one optional provider dispatch, "
    "closed terminal/artifact facts, atomic V9 persistence, historical "
    "reverse projections, and no API/UI/key/trigger/SEC-network/default live CI"
)
PYTHON
