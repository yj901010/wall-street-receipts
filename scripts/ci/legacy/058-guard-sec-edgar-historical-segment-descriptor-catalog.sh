python <<'PYTHON'
import hashlib
import re
import xml.etree.ElementTree as ET
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

adr043_replacements = {
    Path(".env.example"): (
        (
            b"\n# ADR-043 local-only single-operator HTTP boundary. This does not enable SEC\n"
            b"# network access. Keep disabled outside an isolated local API process; when\n"
            b"# enabled, provide only the lowercase SHA-256 digest of the canonical standard\n"
            b"# Base64 encoding of exactly 32 random bytes.\n"
            b"# Never put the raw Bearer token in this file.\n"
            b"OPERATOR_API_ENABLED=false\n"
            b"OPERATOR_API_TOKEN_SHA256=\n",
            b"",
            1,
        ),
    ),
    Path("apps/api/pom.xml"): (
        (
            b"        <dependency>\n"
            b"            <groupId>org.springframework.boot</groupId>\n"
            b"            <artifactId>spring-boot-starter-security</artifactId>\n"
            b"        </dependency>\n",
            b"",
            1,
        ),
    ),
    Path("apps/api/src/main/resources/application.yml"): (
        (
            b"app:\n"
            b"  operator-api:\n"
            b"    enabled: ${OPERATOR_API_ENABLED:false}\n"
            b"    token-sha256: ${OPERATOR_API_TOKEN_SHA256:}\n",
            b"app:\n",
            1,
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/RequestIdFilter.java"): (
        (
            b"import org.springframework.core.Ordered;\n"
            b"import org.springframework.core.annotation.Order;\n",
            b"",
            1,
        ),
        (
            b"@Component\n@Order(Ordered.HIGHEST_PRECEDENCE)\n",
            b"@Component\n",
            1,
        ),
    ),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcSecFilingHistoryCollectionAttemptRepository.java"): (
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
    ),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/SecFilingHistoryCollectionAttemptPersistenceTest.java"): (
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
    ),
}
adr043_historical_sha256 = {
    Path(".env.example"): "d6687a67a4ad25d7ee2cd8ee5194d15e5b01b880e51d0708c3ce0efb3e4938f7",
    Path("apps/api/pom.xml"): "6db3f998c82f0c399b66075216b0cab22693ff91e8a7c495becf7e3fea7e28a3",
    Path("apps/api/src/main/resources/application.yml"): "fba88168c8ca0ce0c17eeea201fb624b556240ab52297f1bf33ca9388f568a42",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/RequestIdFilter.java"): "1cd35e0c8d728db84486ad626b6de6b7df6afa2777f2c35371946c58309212ec",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcSecFilingHistoryCollectionAttemptRepository.java"): "7d4633fca321607f0ee1f27700161891e3b85d37efeecf85e2d1042a39135566",
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/SecFilingHistoryCollectionAttemptPersistenceTest.java"): "e7baf3de28924f1862d0cb65062e4e21917863be74192291035e0273cf6b1448",
}
adr043_original_read_bytes = Path.read_bytes

def pre_adr043_bytes(path):
    source = adr043_original_read_bytes(path).replace(b"\r\n", b"\n")
    replacements = adr043_replacements.get(path)
    if replacements is None:
        return source
    for current, historical, count in replacements:
        require(
            source.count(current) == count,
            f"ADR-043 authorized later delta changed: {path}",
        )
        source = source.replace(current, historical)
    require(
        hashlib.sha256(source).hexdigest()
            == adr043_historical_sha256[path],
        f"ADR-043 historical reverse projection changed: {path}",
    )
    return source

Path.read_bytes = pre_adr043_bytes

def digest(paths):
    result = hashlib.sha256()
    for path in sorted(paths, key=lambda value: value.as_posix()):
        result.update(path.as_posix().encode("utf-8"))
        result.update(b"\0")
        result.update(pre_adr040_bytes(path))
        result.update(b"\0")
    return result.hexdigest()

marker = (
    "ADR-038 establishes the in-memory catalog of historical segment "
    "descriptors advertised by one SEC EDGAR Submissions root response."
)
adr_path = Path(
    "decisions/ADR-038-sec-edgar-historical-segment-descriptor-catalog.md"
)
marker_paths = (
    adr_path,
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
documents = {}
for path in marker_paths:
    require(path.is_file(), f"Missing ADR-038 contract document: {path}")
    source = path.read_text(encoding="utf-8")
    documents[path] = source
require(
    documents[adr_path].count(marker) == 1,
    "ADR-038 decision marker must occur exactly once",
)
readme_marker = (
    "ADR-038 establishes the SEC historical-segment descriptor catalog."
)
for path in (Path("README.md"), Path("apps/api/README.md")):
    require(
        documents[path].count(readme_marker) == 1,
        f"ADR-038 README marker must occur exactly once: {path}",
    )
require(
    re.sub(r"\s+", " ", documents[Path("IMPLEMENTATION_LOG.md")])
        .count(marker) == 1,
    "ADR-038 implementation-log marker must occur exactly once",
)
adr = documents[adr_path]
require(
    adr.startswith(
        "# ADR-038 — SEC EDGAR Historical-Segment Descriptor Catalog\n\n"
        "- Status: Accepted\n- Date: 2026-08-25\n"
    ),
    "ADR-038 title, accepted status, or date changed",
)
compact_adr = re.sub(r"\s+", " ", adr)
required_adr_terms = (
    "`SEC_SUBMISSIONS_CATALOG_V2`",
    "`filings.files` must be present as a JSON array",
    "coercion-free positive JSON integer",
    "^CIK([0-9]{10})-submissions-([0-9]{3})\\.json$",
    "provider-published `filings.files` order is retained without sorting",
    "RECENT_ONLY_NO_SEGMENTS_ADVERTISED",
    "RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED",
    "No complete-history state exists",
    "does not fetch a referenced file",
    "no referenced-segment GET",
    "no durable raw-body retention",
    "no additional HTTP call",
    "no new API key",
)
require(
    all(term in compact_adr for term in required_adr_terms),
    "ADR-038 wire, ordering, recent-only, or no-fetch semantics changed",
)

main_root = Path("apps/api/src/main/java/com/wallstreetreceipts/api")
test_root = Path("apps/api/src/test/java/com/wallstreetreceipts/api")
sec_main_root = main_root / "infrastructure/provider/sec"
sec_test_root = test_root / "infrastructure/provider/sec"
adr039_new_main_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filing/PersistFilingCatalogCaptureService.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureAppendResult.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureProvider.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureReplayVerifier.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/port/out/FilingCatalogCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/SecFilingCatalogPersistenceConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogCapture.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/persistence/JdbcFilingCatalogCaptureRepository.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/infrastructure/provider/sec/SecFilingCatalogCaptureReplayVerifier.java"),
}
adr039_new_test_paths = {
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filing/PersistFilingCatalogCaptureServiceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/domain/filing/FilingCatalogCaptureTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/persistence/FilingCatalogCapturePersistenceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/support/SecFilingCatalogCaptureTestFixture.java"),
}
require(
    len(adr039_new_main_paths) == 9
    and len(adr039_new_test_paths) == 4
    and all(path.is_file()
            for path in adr039_new_main_paths | adr039_new_test_paths),
    "ADR-038 replay must exclude the exact ADR-039 main/test persistence delta",
)

adr043_new_main_paths = {
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/ExactEvidenceNotAdmittedException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/OperatorRequestConflictException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptNotFoundException.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptQueryService.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiProperties.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorApiSecurityConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/OperatorSecCollectionAttemptApiConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptController.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptExceptionHandler.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptRequests.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptResponseMapper.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptResponses.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorApiSecurityProblemWriter.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/ApiRequestRejectedHandler.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorBearerAuthenticationToken.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorBearerTokenAuthenticationFilter.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/OperatorBearerTokenAuthenticationProvider.java"),
}
adr043_new_test_paths = {
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryCollectionAttemptQueryServiceTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/OperatorApiPropertiesTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/config/OperatorSecCollectionAttemptApiConfigurationTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptApiTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/operator/OperatorSecCollectionAttemptIntegrationTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/security/OperatorApiDisabledSecurityTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/security/OperatorApiLoopbackBindingTest.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/security/OperatorApiSecurityTest.java"),
}
require(
    len(adr043_new_main_paths) == 17
    and len(adr043_new_test_paths) == 8
    and all(path.is_file()
            for path in adr043_new_main_paths | adr043_new_test_paths),
    "ADR-038 replay must exclude the exact ADR-043 17+8 surface",
)
adr039_new_main_paths |= adr043_new_main_paths
adr039_new_test_paths |= adr043_new_test_paths

adr040_new_main_paths = {
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
}
adr040_new_test_paths = {
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
    len(adr040_new_main_paths) == 15
    and len(adr040_new_test_paths) == 8
    and all(path.is_file()
            for path in adr040_new_main_paths | adr040_new_test_paths),
    "ADR-038 replay must exclude the exact ADR-040 main/test segment delta",
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
    "ADR-038 replay must exclude the exact ADR-041 7+5 surface",
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


adr040_new_main_paths |= adr041_later_paths
adr040_new_test_paths |= adr041_later_paths

live_path = Path(
    "apps/api/src/sec-live-smoke-test/java/com/wallstreetreceipts/"
    "api/config/SecEdgarLiveSmokeIT.java"
)

def pre_adr040_bytes(path):
    source = path.read_bytes().replace(b"\r\n", b"\n")
    if path != live_path:
        return source
    require(
        hashlib.sha256(source).hexdigest()
            == "27b8066e1d47ddf39ee389527c09bdb33b6ae212d2d0580591f562ada4c5bb0e",
        "ADR-040 live-smoke delta changed",
    )
    historical = source.decode("utf-8")
    for marker in (
        "import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;\n",
        "import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;\n",
        "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecEdgarHistoricalFilingSegmentProvider;\n",
        "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecFilingCatalogCaptureReplayVerifier;\n",
        "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRequestRateLimiter;\n",
        "import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRetryAfterPolicy;\n",
    ):
        historical = historical.replace(marker, "")
    historical = historical.replace(
        "void loadsOneOfficialAppleRootAndOneCapturedDescriptorOnlyAfterBothOptIns() {",
        "void loadsOneOfficialAppleCatalogOnlyAfterBothExplicitOptIns() {",
    )
    historical = re.sub(
        r"\n                    FilingCatalogCapture replayCheckedRoot =.*?"
        r"\n                            \"observed Apple count and filing-date extrema changed from its root descriptor\"\);",
        "",
        historical,
        flags=re.DOTALL,
    ).replace(
        '"SEC live smoke must not claim fetched or complete history");\n\n'
        "                });",
        '"SEC live smoke must not claim fetched or complete history");\n'
        "                });",
    )
    historical_bytes = historical.encode("utf-8")
    require(
        hashlib.sha256(historical_bytes).hexdigest()
            == "6686a85e2ad9944182a320ba55ad3fd85316540faf3bfa597162e7bbddc0f533",
        "ADR-040 live-smoke reverse projection changed",
    )
    return historical_bytes

delta_main_paths = {
    main_root / "domain/filing/FilingCatalog.java",
    main_root / "domain/filing/HistoricalFilingSegmentDescriptor.java",
    sec_main_root / "SecSubmissionsMapper.java",
    sec_main_root / "SecSubmissionsResponse.java",
}
delta_test_paths = {
    test_root / "config/SecEdgarConfigurationTest.java",
    test_root / "domain/filing/FilingCatalogTest.java",
    test_root / "domain/filing/HistoricalFilingSegmentDescriptorTest.java",
    sec_test_root / "SecSubmissionsMapperTest.java",
}
require(
    len(delta_main_paths) == 4
    and len(delta_test_paths) == 4
    and all(path.is_file() for path in delta_main_paths | delta_test_paths)
    and live_path.is_file(),
    "ADR-038 must remain the exact 4+4+1 production/test/live delta",
)
require(
    digest(delta_main_paths)
        == "8e508382539fef5d1caa9bf355c442b590c2c816c9f1a3eae5d8e25f0e78d406"
    and digest(delta_test_paths)
        == "bd7312b0c38c6e417845fe59e1f9b367e3fb2b8a61f1c1b3841889a094579290"
    and digest({live_path})
        == "17e71eb3b18c2671399d03ea4319337b1420fba7d8806f41daff372542851957",
    "ADR-038 exact normalized source/test/live delta changed",
)

expected_main_paths = {
    main_root / "application/port/out/FilingCatalogProvider.java",
    main_root / "config/SecEdgarConfiguration.java",
    main_root / "config/SecEdgarProperties.java",
    main_root / "domain/filing/FilingCatalog.java",
    main_root / "domain/filing/FilingRecord.java",
    main_root / "domain/filing/HistoricalFilingSegmentDescriptor.java",
    main_root / "domain/source/SourceResponseReceipt.java",
    sec_main_root / "SecEdgarFilingCatalogProvider.java",
    sec_main_root / "SecProviderConfigurationException.java",
    sec_main_root / "SecProviderException.java",
    sec_main_root / "SecRawResponseCapture.java",
    sec_main_root / "SecRequestRateLimitInterceptor.java",
    sec_main_root / "SecRequestRateLimiter.java",
    sec_main_root / "SecResponseDecompressionInterceptor.java",
    sec_main_root / "SecResponseSizeLimitInterceptor.java",
    sec_main_root / "SecRetryAfterPolicy.java",
    sec_main_root / "SecStringCikDeserializer.java",
    sec_main_root / "SecSubmissionsMapper.java",
    sec_main_root / "SecSubmissionsResponse.java",
}
actual_main_paths = {
    *(path for path in (main_root / "domain/filing").glob("*.java")
      if path not in adr039_new_main_paths | adr040_new_main_paths),
    *(path for path in sec_main_root.glob("*.java")
      if path not in adr039_new_main_paths | adr040_new_main_paths),
    main_root / "domain/source/SourceResponseReceipt.java",
    *(path for path in (
        main_root / "application/port/out/FilingCatalogProvider.java",
        main_root / "config/SecEdgarConfiguration.java",
        main_root / "config/SecEdgarProperties.java",
    ) if path.is_file()),
}
require(
    actual_main_paths == expected_main_paths
    and len(actual_main_paths) == 19,
    "ADR-038 exact 19-file SEC production surface changed",
)

expected_test_paths = {
    test_root / "config/SecEdgarConfigurationTest.java",
    test_root / "domain/filing/FilingCatalogTest.java",
    test_root / "domain/filing/HistoricalFilingSegmentDescriptorTest.java",
    test_root / "domain/source/SourceResponseReceiptTest.java",
    sec_test_root / "SecRawResponseCaptureTest.java",
    sec_test_root / "SecRequestRateLimitInterceptorTest.java",
    sec_test_root / "SecRequestRateLimiterTest.java",
    sec_test_root / "SecResponseSizeLimitInterceptorTest.java",
    sec_test_root / "SecRetryAfterPolicyTest.java",
    sec_test_root / "SecSubmissionsMapperTest.java",
}
actual_test_paths = {
    *(path for path in (test_root / "domain/filing").glob("*.java")
      if path not in adr039_new_test_paths | adr040_new_test_paths),
    *(path for path in sec_test_root.glob("*.java")
      if path not in adr039_new_test_paths | adr040_new_test_paths),
    test_root / "domain/source/SourceResponseReceiptTest.java",
    test_root / "config/SecEdgarConfigurationTest.java",
}
require(
    actual_test_paths == expected_test_paths
    and len(actual_test_paths) == 10,
    "ADR-038 exact ten-file SEC focused test surface changed",
)
live_paths = {
    path for path in Path("apps/api/src/sec-live-smoke-test").rglob("*")
    if path.is_file()
}
require(
    live_paths == {live_path},
    "ADR-038 must preserve the isolated one-file live-smoke surface",
)

sources = {
    path.name: path.read_text(encoding="utf-8")
    for path in expected_main_paths
}
tests = {
    path.name: path.read_text(encoding="utf-8")
    for path in expected_test_paths
}
descriptor = sources["HistoricalFilingSegmentDescriptor.java"]
catalog = sources["FilingCatalog.java"]
response = sources["SecSubmissionsResponse.java"]
mapper = sources["SecSubmissionsMapper.java"]
capture = sources["SecRawResponseCapture.java"]
provider = sources["SecEdgarFilingCatalogProvider.java"]
configuration = sources["SecEdgarConfiguration.java"]
descriptor_test = tests["HistoricalFilingSegmentDescriptorTest.java"]
catalog_test = tests["FilingCatalogTest.java"]
mapper_test = tests["SecSubmissionsMapperTest.java"]
configuration_test = tests["SecEdgarConfigurationTest.java"]
live_source = pre_adr040_bytes(live_path).decode("utf-8")

descriptor_record = re.search(
    r"public record HistoricalFilingSegmentDescriptor\((?P<fields>.*?)\) \{",
    descriptor,
    flags=re.DOTALL,
)
require(descriptor_record is not None, "Missing historical descriptor record")
require(
    re.sub(r"\s+", " ", descriptor_record.group("fields")).strip()
        == (
            "String fileName, long advertisedFilingCount, "
            "LocalDate advertisedFilingFrom, LocalDate advertisedFilingTo"
        )
    and 'MAX_FILE_NAME_LENGTH = 128' in descriptor
    and 'Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*")' in descriptor
    and "if (advertisedFilingCount <= 0)" in descriptor
    and "advertisedFilingTo.isBefore(advertisedFilingFrom)" in descriptor
    and "containsAdvertisedDate" in descriptor
    and "overlapsAdvertisedDateRange" in descriptor
    and all(method in descriptor_test for method in (
        "preservesAdvertisedMetadataAndTreatsDateBoundariesAsInclusive",
        "detectsInclusiveRangeOverlapWithoutInferringSegmentContents",
        "acceptsASingleDayAdvertisedRange",
        "rejectsMissingBlankUntrimmedUnsafeAndUnboundedFileNames",
        "rejectsNonPositiveCountsMissingDatesAndReversedRanges",
    )),
    "Historical descriptor fields, bounds, or inclusive date semantics changed",
)

catalog_record = re.search(
    r"public record FilingCatalog\((?P<fields>.*?)\) \{",
    catalog,
    flags=re.DOTALL,
)
require(catalog_record is not None, "Missing FilingCatalog record")
require(
    re.sub(r"\s+", " ", catalog_record.group("fields")).strip()
        == (
            "String provider, String product, String cik, URI sourceUri, "
            "Instant processingTime, Instant capturedAt, "
            "SourceResponseReceipt sourceReceipt, "
            "List<FilingRecord> recentFilings, "
            "List<HistoricalFilingSegmentDescriptor> historicalSegments"
        )
    and "recentFilings = List.copyOf(recentFilings);" in catalog
    and "historicalSegments = List.copyOf(historicalSegments);" in catalog
    and "Set<String> historicalFileNames = new HashSet<>();" in catalog
    and "historicalFileNames.add(segment.fileName())" in catalog
    and "historicalSegmentStatus()" in catalog
    and "hasAdvertisedHistoricalDateRangeOverlap()" in catalog
    and "hasAdvertisedRecentHistoricalDateOverlap()" in catalog
    and all(method in catalog_test for method in (
        "preservesProviderOrderAndDefensivelyCopiesBothCatalogSections",
        "reportsAdvertisedHistoricalStateAndPreservesInclusiveOverlaps",
        "rejectsMissingNullAndDuplicateCatalogSections",
    )),
    "Catalog separation, immutable provider order, duplicate, or overlap flags changed",
)
status_match = re.search(
    r"public enum HistoricalSegmentStatus\s*\{(?P<body>.*?)\}",
    catalog,
    flags=re.DOTALL,
)
require(status_match is not None, "Missing historical segment status enum")
require(
    re.findall(r"\b[A-Z][A-Z0-9_]+\b", status_match.group("body"))
        == [
            "RECENT_ONLY_NO_SEGMENTS_ADVERTISED",
            "RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED",
        ],
    "Historical catalog must remain recent-only with exactly two states",
)

filings_record = re.search(
    r"public record SecFilings\((?P<fields>.*?)\) \{",
    response,
    flags=re.DOTALL,
)
file_record = re.search(
    r"public record SecHistoricalFilingFile\((?P<fields>.*?)\) \{",
    response,
    flags=re.DOTALL,
)
require(
    filings_record is not None
    and re.sub(r"\s+", " ", filings_record.group("fields")).strip()
        == "SecRecentFilings recent, List<SecHistoricalFilingFile> files"
    and file_record is not None
    and re.sub(r"\s+", " ", file_record.group("fields")).strip()
        == (
            "String name, Long filingCount, String filingFrom, String filingTo"
        ),
    "SEC filings.files vendor DTO or positive-Long boundary changed",
)

mapper_markers = (
    'PARSER_VERSION = "SEC_SUBMISSIONS_CATALOG_V2"',
    "mapHistoricalSegments(source.filings().files(), cik)",
    'throw new IllegalArgumentException("filings.files must be present")',
    "private static final Pattern HISTORICAL_SUBMISSIONS_FILE = Pattern.compile(",
    '"CIK([0-9]{10})-submissions-([0-9]{3})\\\\.json"',
    "cik.equals(fileNameMatcher.group(1))",
    '"000".equals(fileNameMatcher.group(2))',
    "Set<String> fileNames = new HashSet<>();",
    "if (!fileNames.add(fileName))",
    "if (filingCount == null || filingCount <= 0)",
    "parseHistoricalDate(",
    "filingTo.isBefore(filingFrom)",
    "return List.copyOf(canonical);",
)
require(
    all(marker in mapper for marker in mapper_markers)
    and ".sort(" not in mapper
    and "Collections.sort" not in mapper
    and ".distinct(" not in mapper
    and ".sum(" not in mapper
    and all(method in mapper_test for method in (
        "deserializesVendorShapeAndPreservesRecentAndHistoricalProviderOrder",
        "rejectsMissingTopLevelSectionsAndNullHistoricalEntries",
        "rejectsHistoricalFileNamesWithWrongCikOrdinalOrPathSyntax",
        "rejectsMissingAndNonPositiveHistoricalCounts",
        "rejectsMissingNonCanonicalImpossibleAndReversedHistoricalDates",
        "rejectsDuplicateHistoricalFileNameButPreservesOverlapAndProviderOrder",
        "rejectsV1ReceiptAndAcceptsV2ReceiptWithoutHistoricalFiles",
    ))
    and all(value in mapper_test for value in (
        "../CIK0000320193-submissions-001.json",
        "dir/CIK0000320193-submissions-001.json",
        "dir\\\\CIK0000320193-submissions-001.json",
        "%2e%2e-CIK0000320193-submissions-001.json",
        "CIK0000789019-submissions-001.json",
        "CIK0000320193-submissions-000.json",
    )),
    "CIK-bound filename, count/date validation, order, or duplicate policy changed",
)

require(
    "SUBMISSIONS_READER.readValue(decodedBody)" in capture
    and "SecSubmissionsMapper.toCanonical(" in capture
    and "decode(), receipt, processingTime" in capture
    and 'PARSER_VERSION = "SEC_SUBMISSIONS_CATALOG_V2"' in mapper
    and "rejectsNonIntegralOrOverflowingHistoricalFilingCountsWithoutCoercion"
        in configuration_test
    and all(value in configuration_test for value in (
        '"\\\"2000\\\"", "2000.5", "true", "2e3", '
        '"9223372036854775808"',
        "rejectsNullOrMissingHistoricalFilesWithoutRecentOnlySalvage",
        "rejectsDuplicateHistoricalKeysAtTheStrictReaderBoundary",
        "rejectsInvalidHistoricalFilingToAtTheProviderBoundary",
    )),
    "V2 must parse recent and descriptor data from the same strict decoded bytes",
)

success_start = configuration_test.index(
    "void requestsRootSubmissionsExactlyOnceAndDoesNotFetchAdvertisedHistoricalFiles()"
)
success_end = configuration_test.index("\n    @Test", success_start)
success_test = configuration_test[success_start:success_end]
require(
    provider.count("restClient.get()") == 1
    and provider.count(".retrieve()") == 1
    and 'SUBMISSIONS_PATH_TEMPLATE = "/submissions/CIK%s.json"' in provider
    and "HistoricalFilingSegmentDescriptor" not in provider
    and "SecHistoricalFilingFile" not in provider
    and success_test.count(
        "server.expect(once(), requestTo(EXPECTED_ENDPOINT))"
    ) == 1
    and success_test.count('provider.loadRecentFilings("320193")') == 1
    and success_test.count("server.verify();") == 1
    and "historicalSegmentStatus()" in success_test
    and live_source.count("provider.loadCatalogCapture(APPLE_CIK)") == 1
    and 'catalog.historicalSegments().isEmpty()' in live_source
    and '"SEC_SUBMISSIONS_CATALOG_V2"' in live_source
    and "RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED" in live_source
    and "DECODED_BODY_ATTACHED_PENDING_PERSISTENCE" in live_source
    and "capture.decodedBody().length" in live_source
    and "submissions-001.json" not in live_source
    and "submissions-002.json" not in live_source,
    "ADR-038 must issue exactly one root request and zero historical-file requests",
)

delta_main_source = "\n".join(
    path.read_text(encoding="utf-8") for path in delta_main_paths
)
forbidden_layer_markers = (
    "@Entity", "@Table", "@Repository", "@RestController",
    "@Controller", "@RequestMapping", "@Scheduled",
    "SchedulingConfigurer", "CommandLineRunner", "ApplicationRunner",
    "jakarta.persistence", "org.springframework.data", "JdbcTemplate",
    "EntityManager", "DataSource", "Flyway", ".save(", ".insert(",
    "Files.write", "Logger", "Slf4j",
)
require(
    all(marker not in delta_main_source for marker in forbidden_layer_markers)
    and "RestClient" not in delta_main_source
    and "WebClient" not in delta_main_source
    and "HttpClient" not in delta_main_source
    and "FilingRecord(" not in mapper[mapper.index("mapHistoricalSegments"):],
    "ADR-038 must not fetch, persist, schedule, publish, or invent historical filings",
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
    "ADR-038 replay must recognize only the exact ADR-039 through ADR-042 Flyway deltas",
)

publication_markers = (
    "historicalfilingsegmentdescriptor",
    "historical segment descriptor",
    "historicalsegments",
    "filings.files",
    "sec_submissions_catalog_v2",
)
publication_paths = {
    Path("contracts/openapi.yaml"),
    *Path("schemas").glob("*.json"),
    *(path for path in Path("fixtures/v1").rglob("*") if path.is_file()),
    *(path for path in main_root.rglob("*.java")
      if path not in delta_main_paths | adr039_new_main_paths
      | adr040_new_main_paths),
    *(path for path in test_root.rglob("*.java")
      if path not in delta_test_paths | adr039_new_test_paths
      | adr040_new_test_paths
      | {test_root / "migration/PostgreSqlMigrationTest.java"}),
    *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
      for path in root.rglob("*") if path.is_file()),
}
for path in publication_paths:
    source = path.read_text(encoding="utf-8", errors="ignore").lower()
    require(
        not any(marker in source for marker in publication_markers),
        f"ADR-038 descriptor catalog must not reach API/UI/publication code: {path}",
    )

focused_test_source = "\n".join(tests.values())
require(
    "MockRestServiceServer.bindTo(restClientBuilder)" in configuration_test
    and all(marker not in focused_test_source for marker in (
        "HttpClient.newHttpClient(", "RestClient.create(",
        "WebClient.create(", ".openConnection(", "new Socket(",
    )),
    "ADR-038 standard tests must remain deterministic and offline",
)

config_paths = {
    Path(".env.example"),
    Path("apps/api/src/main/resources/application.yml"),
    Path("apps/api/src/main/resources/application-local.yml"),
    Path("apps/api/src/test/resources/application-test.yml"),
}
pom_path = Path("apps/api/pom.xml")
require(
    digest(config_paths)
        == "c667d625d56663217470dd9652ae0c6da9225c48427919009717e1148bc5c328"
    and digest({pom_path})
        == "faee0805dae9dcf5e090a73d03fa16ead4a79c64fac6f2eb87bd87a1e91d3682"
    and '@ConditionalOnProperty(' in configuration
    and 'prefix = "app.public-data.sec"' in configuration
    and 'havingValue = "true"' in configuration
    and "matchIfMissing" not in configuration
    and "remainsDisabledByDefault" in configuration_test,
    "ADR-038 must preserve dependencies and default-disabled server-only config",
)
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
pom_root = ET.parse(pom_path).getroot()
profiles = pom_root.findall("m:profiles/m:profile", namespace)
require(
    len(profiles) == 1
    and profiles[0].findtext("m:id", namespaces=namespace)
        == "sec-live-smoke"
    and profiles[0].find("m:activation", namespace) is None,
    "ADR-038 must preserve isolated non-activated live-smoke profile",
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
    "Default Maven/CI must not activate live smoke or call SEC",
)

print(
    "Validated ADR-038 exact 19+10+1 SEC surfaces and 4+4+1 delta, "
    "V2 same-byte recent/descriptor mapping, strict CIK/count/date/order/duplicate "
    "semantics, recent-only statuses, one root request with zero segment fetches, "
    "default-offline isolation, and no persistence/API/UI/publication expansion"
)
PYTHON
