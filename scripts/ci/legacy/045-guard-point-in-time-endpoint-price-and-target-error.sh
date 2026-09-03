python <<'PYTHON'
import hashlib
import json
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def scrub_java(source, strip_literals):
    output = []
    index = 0
    state = "CODE"
    quote = None
    while index < len(source):
        current = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "CODE":
            if current in {'"', "'"}:
                state = "STRING" if current == '"' else "CHAR"
                quote = current
                output.append(
                    ('""' if current == '"' else "''")
                    if strip_literals else current
                )
                index += 1
            elif current == "/" and following == "/":
                state = "LINE_COMMENT"
                index += 2
            elif current == "/" and following == "*":
                state = "BLOCK_COMMENT"
                index += 2
            else:
                output.append(current)
                index += 1
        elif state in {"STRING", "CHAR"}:
            if current == "\\" and following:
                if not strip_literals:
                    output.extend((current, following))
                index += 2
            elif current == quote:
                if not strip_literals:
                    output.append(current)
                state = "CODE"
                quote = None
                index += 1
            else:
                if not strip_literals:
                    output.append(current)
                index += 1
        elif state == "LINE_COMMENT":
            if current in "\r\n":
                output.append(current)
                state = "CODE"
            index += 1
        elif current == "*" and following == "/":
            state = "CODE"
            index += 2
        else:
            if current in "\r\n":
                output.append(current)
            index += 1
    require(
        state not in {"STRING", "CHAR", "BLOCK_COMMENT"},
        "Java source contains an unterminated lexical token",
    )
    return "".join(output)

def without_comments(source):
    return scrub_java(source, False)

def without_comments_or_strings(source):
    return scrub_java(source, True)

def compact(source):
    return re.sub(r"\s+", "", source)

def java_string_constant(source, name):
    match = re.search(
        rf"private\s+static\s+final\s+String\s+{name}\s*=\s*(?P<body>.*?);",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing Java string constant {name}")
    tokens = re.findall(r'"(?:\\.|[^"\\])*"', match.group("body"))
    require(tokens, f"Java string constant {name} has no literal bytes")
    return "".join(json.loads(token) for token in tokens)

def enum_values(source, enum_name):
    match = re.search(
        rf"enum\s+{enum_name}\s*\{{(?P<body>.*?)\}}",
        without_comments(source),
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing enum {enum_name}")
    declaration_body = match.group("body").split(";", 1)[0]
    return re.findall(r"\b[A-Z][A-Z0-9_]+\b", declaration_body)

observation_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/observation"
)
target_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/targeterror"
)
observation_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/observation"
)
target_test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/targeterror"
)
observation_files = {
    "CatalogPointInTimeEvidence.java",
    "CorporateActionContinuity.java",
    "EndpointPriceAdjustmentBasis.java",
    "EndpointPriceBinding.java",
    "EndpointPriceField.java",
    "EndpointPriceObservation.java",
    "EndpointPricePolicyVersion.java",
    "EndpointPriceRequest.java",
    "EndpointPriceResolution.java",
    "EndpointPriceSelector.java",
}
target_files = {
    "TargetErrorCalculator.java",
    "TargetErrorInput.java",
    "TargetErrorPolicyVersion.java",
    "TargetErrorResult.java",
    "TargetPriceEvidence.java",
}
require(
    {path.name for path in observation_dir.glob("*.java")}
    == observation_files,
    "Endpoint-price production package must contain exactly ten files",
)
require(
    {path.name for path in target_dir.glob("*.java")} == target_files,
    "Target-error production package must contain exactly five files",
)
require(
    {path.name for path in observation_test_dir.glob("*.java")}
    == {"EndpointPriceSelectorGoldenTest.java"},
    "Endpoint-price test package must contain exactly one golden",
)
require(
    {path.name for path in target_test_dir.glob("*.java")}
    == {"TargetErrorCalculatorGoldenTest.java"},
    "Target-error test package must contain exactly one golden",
)

observation_sources = {
    name: (observation_dir / name).read_text(encoding="utf-8")
    for name in observation_files
}
target_sources = {
    name: (target_dir / name).read_text(encoding="utf-8")
    for name in target_files
}
endpoint_policy_source = observation_sources["EndpointPricePolicyVersion.java"]
target_policy_source = target_sources["TargetErrorPolicyVersion.java"]

expected_endpoint_definition = (
    '{"policyVersion":"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1",'
    '"horizonInput":"STRICT_SESSION_CLOSE_RESOLVED_WINDOW",'
    '"requiredHorizonPolicyVersion":"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1",'
    '"requiredHorizonPolicyDefinitionHash":"550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1",'
    '"catalogWindowIdentity":"catalog.calendarId==horizon.context.calendarId&&catalog.catalogRevision==horizon.context.catalogRevision",'
    '"catalogPitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf",'
    '"bindingPitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf",'
    '"endpointMaturityPredicate":"endpointSession.closesAt<=evaluationAsOf",'
    '"candidateScope":"ALL_REQUEST_CANDIDATES",'
    '"knownCandidatePredicate":"candidate.availableAt<=evaluationAsOf&&candidate.capturedAt<=evaluationAsOf",'
    '"futureCandidateRule":"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING",'
    '"noKnownReason":"OBSERVATION_MISSING_AS_OF",'
    '"priceField":"OFFICIAL_REGULAR_SESSION_CLOSE",'
    '"venueRule":"PRIMARY_VENUE_EXACT_MATCH",'
    '"currencyRule":"EXACT_MATCH_NO_FX",'
    '"bindingCurrencyRole":"REQUIRED_SCORING_AND_TARGET_CURRENCY",'
    '"sourceRule":"PRICE_SOURCE_ID_AND_REVISION_EXACT_MATCH",'
    '"observationTimeRule":"observedAt==endpointSession.closesAt",'
    '"provenanceRule":"CATALOG_BINDING_OBSERVATION_PROVENANCE_PRESERVED_INDEPENDENTLY",'
    '"selectedObservationIdentity":["observationId","priceSourceId","providerEventId"],'
    '"selectedObservationEvidence":["priceSourceRevision","provenanceId"],'
    '"deduplication":"ABSENT",'
    '"candidateCardinality":"EXACTLY_ONE_KNOWN_AS_OF",'
    '"priceBoundary":"POSITIVE_NUMERIC_38_12",'
    '"adjustmentBasis":"SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED",'
    '"continuityRule":"SPLIT_REVERSE_SPLIT_CONTINUOUS_ONLY",'
    '"gatePrecedence":["CATALOG_NOT_KNOWN_AS_OF","CATALOG_EVIDENCE_MISMATCH","BINDING_NOT_KNOWN_AS_OF","ENDPOINT_NOT_REACHED_AS_OF","OBSERVATION_MISSING_AS_OF"],'
    '"knownCandidateMismatchPrecedence":["ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","SOURCE_MISMATCH","CATALOG_MISMATCH","SESSION_MISMATCH","OBSERVED_AT_MISMATCH","PRICE_FIELD_MISMATCH","ADJUSTMENT_BASIS_MISMATCH","CORPORATE_ACTION_CONTINUITY_UNAVAILABLE"],'
    '"ambiguityRule":"AFTER_ALL_KNOWN_CANDIDATES_PASS_MISMATCH_GATES",'
    '"resolvedCardinality":1,"fallbackBehavior":"ABSENT"}'
)
endpoint_hash = "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76"
actual_endpoint_definition = java_string_constant(
    endpoint_policy_source, "CANONICAL_DEFINITION"
)
require(
    actual_endpoint_definition == expected_endpoint_definition
    and len(actual_endpoint_definition.encode("utf-8")) == 2259
    and hashlib.sha256(
        actual_endpoint_definition.encode("utf-8")
    ).hexdigest() == endpoint_hash
    and java_string_constant(endpoint_policy_source, "DEFINITION_HASH")
    == endpoint_hash,
    "Endpoint-price canonical bytes, order, length, or hash changed",
)

expected_target_definition = (
    '{"policyVersion":"ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1",'
    '"requiredEndpointPolicyVersion":"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1",'
    '"requiredEndpointPolicyDefinitionHash":"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76",'
    '"endpointInput":"ENDPOINT_PRICE_RESOLUTION",'
    '"targetEvidenceFields":["targetEvidenceId","basis","assetId","primaryVenueId","currency","adjustmentBasis","target","availableAt","capturedAt","provenanceId"],'
    '"targetPitPredicate":"availableAt<=endpoint.evaluationAsOf&&capturedAt<=endpoint.evaluationAsOf",'
    '"targetEvidenceTemporalRule":"basis.eventTime<=availableAt<=capturedAt",'
    '"futureTargetRule":"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT",'
    '"targetMissingReason":"TARGET_MISSING_AS_OF",'
    '"missingEndpointTruthTable":{"targetMissingAsOf&&endpointUnavailable":"TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON","targetMissingAsOfOnly":"TARGET_MISSING_AS_OF","endpointUnavailableOnly":"ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON"},'
    '"basisRule":"target.basis==endpoint.horizon.context.basis",'
    '"identityMatchPrecedence":["BASIS_MISMATCH","ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","ADJUSTMENT_BASIS_MISMATCH"],'
    '"evaluationPrecedence":["TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE","TARGET_MISSING_AS_OF","ENDPOINT_PRICE_UNAVAILABLE","BASIS_MISMATCH","ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","ADJUSTMENT_BASIS_MISMATCH","CALCULATE","OUTPUT_NOT_REPRESENTABLE"],'
    '"currencyRule":"EXACT_MATCH_NO_FX",'
    '"formula":"abs(target-actual)/actual",'
    '"denominator":"ACTUAL_ENDPOINT_PRICE",'
    '"inputBoundary":"POSITIVE_NUMERIC_38_12",'
    '"divisionScale":12,"roundingMode":"HALF_EVEN","divisionCount":1,'
    '"outputUnits":"DECIMAL_RATIO",'
    '"outputBoundary":"NONNEGATIVE_NUMERIC_38_12",'
    '"outputOverflowReason":"OUTPUT_NOT_REPRESENTABLE",'
    '"endpointUnavailableRule":"PRESERVE_EXACT_ENDPOINT_REASON",'
    '"resultContext":"POLICY_IDENTITY_AND_ENDPOINT_RESOLUTION_ONLY",'
    '"fallbackBehavior":"ABSENT"}'
)
target_hash = "31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074"
actual_target_definition = java_string_constant(
    target_policy_source, "CANONICAL_DEFINITION"
)
require(
    actual_target_definition == expected_target_definition
    and len(actual_target_definition.encode("utf-8")) == 1942
    and hashlib.sha256(
        actual_target_definition.encode("utf-8")
    ).hexdigest() == target_hash
    and java_string_constant(target_policy_source, "DEFINITION_HASH")
    == target_hash,
    "Target-error canonical bytes, order, length, or hash changed",
)

require(
    enum_values(endpoint_policy_source, "EndpointPricePolicyVersion")
    == ["OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1"],
    "Endpoint-price policy enum changed",
)
require(
    enum_values(target_policy_source, "TargetErrorPolicyVersion")
    == ["ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1"],
    "Target-error policy enum changed",
)
require(
    enum_values(
        observation_sources["EndpointPriceField.java"],
        "EndpointPriceField",
    ) == ["OFFICIAL_REGULAR_SESSION_CLOSE", "INDICATIVE_OR_OTHER"],
    "Endpoint price-field vocabulary changed",
)
require(
    enum_values(
        observation_sources["EndpointPriceAdjustmentBasis.java"],
        "EndpointPriceAdjustmentBasis",
    ) == [
        "SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED",
        "UNADJUSTED_OR_OTHER",
        "DIVIDEND_OR_TOTAL_RETURN_ADJUSTED",
    ],
    "Endpoint adjustment-basis vocabulary changed",
)
require(
    enum_values(
        observation_sources["CorporateActionContinuity.java"],
        "CorporateActionContinuity",
    ) == [
        "SPLIT_REVERSE_SPLIT_CONTINUOUS", "MERGER", "SPIN_OFF",
        "DELISTING", "SPECIAL_DISTRIBUTION", "UNKNOWN",
    ],
    "Corporate-action continuity vocabulary changed",
)

endpoint_result = observation_sources["EndpointPriceResolution.java"]
endpoint_reasons = enum_values(endpoint_result, "UnavailableReason")
require(
    endpoint_reasons == [
        "CATALOG_NOT_KNOWN_AS_OF", "CATALOG_EVIDENCE_MISMATCH",
        "BINDING_NOT_KNOWN_AS_OF", "ENDPOINT_NOT_REACHED_AS_OF",
        "OBSERVATION_MISSING_AS_OF", "ASSET_MISMATCH",
        "PRIMARY_VENUE_MISMATCH", "CURRENCY_MISMATCH",
        "SOURCE_MISMATCH", "CATALOG_MISMATCH", "SESSION_MISMATCH",
        "OBSERVED_AT_MISMATCH", "PRICE_FIELD_MISMATCH",
        "ADJUSTMENT_BASIS_MISMATCH",
        "CORPORATE_ACTION_CONTINUITY_UNAVAILABLE",
        "OBSERVATION_AMBIGUOUS",
    ],
    f"Endpoint unavailable reasons changed: {endpoint_reasons}",
)
target_result = target_sources["TargetErrorResult.java"]
target_reasons = enum_values(target_result, "UnavailableReason")
require(
    target_reasons == [
        "TARGET_MISSING_AS_OF", "ENDPOINT_PRICE_UNAVAILABLE",
        "TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE", "BASIS_MISMATCH",
        "ASSET_MISMATCH", "PRIMARY_VENUE_MISMATCH",
        "CURRENCY_MISMATCH", "ADJUSTMENT_BASIS_MISMATCH",
        "OUTPUT_NOT_REPRESENTABLE",
    ],
    f"Target-error unavailable reasons changed: {target_reasons}",
)

compact_observation = {
    name: compact(without_comments(source))
    for name, source in observation_sources.items()
}
compact_target = {
    name: compact(without_comments(source))
    for name, source in target_sources.items()
}
for name, marker in {
    "CatalogPointInTimeEvidence.java":
        "publicrecordCatalogPointInTimeEvidence(StringcalendarId,"
        "StringcatalogRevision,StringsourceId,StringsourceRevision,"
        "InstantavailableAt,InstantcapturedAt,StringprovenanceId)",
    "EndpointPriceBinding.java":
        "publicrecordEndpointPriceBinding(StringbindingId,"
        "StringbindingRevision,StringassetId,StringprimaryVenueId,"
        "Currencycurrency,StringpriceSourceId,StringpriceSourceRevision,"
        "InstantavailableAt,InstantcapturedAt,StringprovenanceId)",
    "EndpointPriceObservation.java":
        "publicrecordEndpointPriceObservation(StringobservationId,"
        "StringproviderEventId,StringassetId,StringvenueId,Currencycurrency,"
        "StringpriceSourceId,StringpriceSourceRevision,StringprovenanceId,"
        "StringcalendarId,StringcatalogRevision,StringsessionId,"
        "EndpointPriceFieldpriceField,EndpointPriceAdjustmentBasisadjustmentBasis,"
        "CorporateActionContinuitycorporateActionContinuity,InstantobservedAt,"
        "InstantavailableAt,InstantcapturedAt,BigDecimalprice)",
    "EndpointPriceRequest.java":
        "publicrecordEndpointPriceRequest(EndpointPricePolicyVersionpolicyVersion,"
        "ResolvedhorizonResolution,CatalogPointInTimeEvidencecatalogEvidence,"
        "EndpointPriceBindingbinding,InstantevaluationAsOf,"
        "List<EndpointPriceObservation>candidates)",
}.items():
    require(marker in compact_observation[name], f"Record surface changed: {name}")
for name, marker in {
    "TargetErrorInput.java":
        "publicrecordTargetErrorInput(TargetErrorPolicyVersionpolicyVersion,"
        "EndpointPriceResolutionendpointPriceResolution,"
        "TargetPriceEvidencetargetEvidence)",
    "TargetPriceEvidence.java":
        "publicrecordTargetPriceEvidence(StringtargetEvidenceId,"
        "OutcomeBasisbasis,StringassetId,StringprimaryVenueId,"
        "Currencycurrency,EndpointPriceAdjustmentBasisadjustmentBasis,"
        "BigDecimaltarget,InstantavailableAt,InstantcapturedAt,"
        "StringprovenanceId)",
}.items():
    require(marker in compact_target[name], f"Record surface changed: {name}")
require(
    "permitsEndpointPriceResolution.Resolved,"
    "EndpointPriceResolution.Unavailable" in compact_observation[
        "EndpointPriceResolution.java"
    ]
    and "recordResolved(ResolutionContextcontext,"
    "EndpointPriceObservationobservation)implementsEndpointPriceResolution"
    in compact_observation["EndpointPriceResolution.java"]
    and "recordUnavailable(ResolutionContextcontext,"
    "UnavailableReasonreason)implementsEndpointPriceResolution"
    in compact_observation["EndpointPriceResolution.java"],
    "Endpoint resolution variants changed",
)
require(
    "permitsTargetErrorResult.Available,TargetErrorResult.Unavailable"
    in compact_target["TargetErrorResult.java"]
    and "recordCalculationContext(TargetErrorPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash,"
    "EndpointPriceResolutionendpointPriceResolution)"
    in compact_target["TargetErrorResult.java"]
    and "recordAvailable(CalculationContextcontext,"
    "BigDecimaltargetError)implementsTargetErrorResult"
    in compact_target["TargetErrorResult.java"]
    and "recordUnavailable(CalculationContextcontext,"
    "UnavailableReasonreason,EndpointPriceResolution."
    "UnavailableReasonendpointReason)implementsTargetErrorResult"
    in compact_target["TargetErrorResult.java"],
    "Target-error result/context variants changed",
)

expected_imports = {
    "CatalogPointInTimeEvidence.java": {
        "java.time.Instant",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
    },
    "CorporateActionContinuity.java": set(),
    "EndpointPriceAdjustmentBasis.java": set(),
    "EndpointPriceBinding.java": {
        "java.time.Instant", "java.util.Currency", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
    },
    "EndpointPriceField.java": set(),
    "EndpointPriceObservation.java": {
        "java.math.BigDecimal", "java.math.RoundingMode",
        "java.time.Instant", "java.util.Currency", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
    },
    "EndpointPricePolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "EndpointPriceRequest.java": {
        "java.time.Instant", "java.util.List", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution.Resolved",
    },
    "EndpointPriceResolution.java": {
        "java.time.Instant", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution.ResolvedSessionWindow",
    },
    "EndpointPriceSelector.java": {
        "java.time.Instant", "java.util.List", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution.ResolutionContext",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution.UnavailableReason",
    },
    "TargetErrorCalculator.java": {
        "java.math.BigDecimal", "java.math.RoundingMode",
        "java.time.Instant", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetErrorResult.Available",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetErrorResult.CalculationContext",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetErrorResult.Unavailable",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetErrorResult.UnavailableReason",
    },
    "TargetErrorInput.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPricePolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
    },
    "TargetErrorPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "TargetErrorResult.java": {
        "java.math.BigDecimal", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPricePolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
    },
    "TargetPriceEvidence.java": {
        "java.math.BigDecimal", "java.math.RoundingMode",
        "java.time.Instant", "java.util.Currency", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceAdjustmentBasis",
    },
}
all_sources = observation_sources | target_sources
expected_body_qualified_types = {
    name: set() for name in observation_files | target_files
}
expected_body_qualified_types["EndpointPriceResolution.java"] = {
    "com.wallstreetreceipts.api.domain.outcome.horizon."
    "SessionCloseHorizonResolution.Resolved"
}
for name, source in all_sources.items():
    code = without_comments(source)
    logic = without_comments_or_strings(source)
    imports = set(re.findall(
        r"^import\s+([^;]+);", code, flags=re.MULTILINE
    ))
    require(imports == expected_imports[name], f"Unexpected imports: {name} {imports}")
    body_logic = re.sub(
        r"^\s*(?:package|import)\s+[^;]+;\s*$",
        "",
        logic,
        flags=re.MULTILINE,
    )
    body_qualified_types = set(re.findall(
        r"\b(?:[a-z_$][\w$]*\.)+[A-Z_$][\w$]*"
        r"(?:\.[A-Z_$][\w$]*)*",
        body_logic,
    ))
    require(
        body_qualified_types == expected_body_qualified_types[name],
        f"Unexpected fully-qualified production type: "
        f"{name} {sorted(body_qualified_types)}",
    )
    require(
        "org.springframework" not in code
        and "com.fasterxml" not in code
        and "jakarta." not in code
        and "java.net." not in code
        and "java.sql." not in code
        and "java.util.concurrent." not in code
        and not any(token in code for token in (
            "Clock", "LocalDate", "ZoneId", "Locale", "TimeZone",
            "System.", "Runtime.", "Thread.", "ProcessBuilder",
            "Math.random", "Random", "double", "float", "@Component",
            "@Service", "ObjectMapper", "Repository", "HttpClient",
            "JdbcTemplate", "Files.", "Path.of", "Class.forName",
        ))
        and re.search(r"\.\s*now\s*\(", logic) is None,
        f"Endpoint/target source crosses pure domain boundary: {name}",
    )

observation_type_markers = observation_files
observation_type_markers = {
    name.removesuffix(".java") for name in observation_type_markers
}
target_type_markers = {
    name.removesuffix(".java") for name in target_files
}
expected_observation_references = {
    "CatalogPointInTimeEvidence.java": {"CatalogPointInTimeEvidence"},
    "CorporateActionContinuity.java": {"CorporateActionContinuity"},
    "EndpointPriceAdjustmentBasis.java": {"EndpointPriceAdjustmentBasis"},
    "EndpointPriceBinding.java": {"EndpointPriceBinding"},
    "EndpointPriceField.java": {"EndpointPriceField"},
    "EndpointPriceObservation.java": {
        "EndpointPriceObservation", "EndpointPriceField",
        "EndpointPriceAdjustmentBasis", "CorporateActionContinuity",
    },
    "EndpointPricePolicyVersion.java": {"EndpointPricePolicyVersion"},
    "EndpointPriceRequest.java": {
        "EndpointPriceRequest", "EndpointPricePolicyVersion",
        "CatalogPointInTimeEvidence", "EndpointPriceBinding",
        "EndpointPriceObservation",
    },
    "EndpointPriceResolution.java": {
        "EndpointPriceResolution", "EndpointPricePolicyVersion",
        "CatalogPointInTimeEvidence", "EndpointPriceBinding",
        "EndpointPriceObservation", "EndpointPriceField",
        "EndpointPriceAdjustmentBasis", "CorporateActionContinuity",
    },
    "EndpointPriceSelector.java": {
        "EndpointPriceSelector", "EndpointPriceRequest",
        "EndpointPriceResolution", "EndpointPriceObservation",
        "EndpointPriceBinding", "CatalogPointInTimeEvidence",
        "EndpointPriceField", "EndpointPriceAdjustmentBasis",
        "CorporateActionContinuity",
    },
}
expected_target_observation_references = {
    "TargetErrorCalculator.java": {"EndpointPriceResolution"},
    "TargetErrorInput.java": {
        "EndpointPricePolicyVersion", "EndpointPriceResolution",
    },
    "TargetErrorPolicyVersion.java": set(),
    "TargetErrorResult.java": {
        "EndpointPricePolicyVersion", "EndpointPriceResolution",
    },
    "TargetPriceEvidence.java": {"EndpointPriceAdjustmentBasis"},
}
expected_target_internal_references = {
    "TargetErrorCalculator.java": {
        "TargetErrorCalculator", "TargetErrorInput", "TargetErrorResult",
        "TargetPriceEvidence",
    },
    "TargetErrorInput.java": {
        "TargetErrorInput", "TargetErrorPolicyVersion", "TargetPriceEvidence",
    },
    "TargetErrorPolicyVersion.java": {"TargetErrorPolicyVersion"},
    "TargetErrorResult.java": {"TargetErrorPolicyVersion", "TargetErrorResult"},
    "TargetPriceEvidence.java": {"TargetPriceEvidence"},
}
for name, source in observation_sources.items():
    logic = without_comments_or_strings(source)
    actual_observation_references = {
        marker for marker in observation_type_markers
        if re.search(rf"\b{re.escape(marker)}\b", logic)
    }
    require(
        actual_observation_references
        == expected_observation_references[name],
        f"Observation internal reference graph changed: "
        f"{name} {sorted(actual_observation_references)}",
    )
    require(
        not any(re.search(rf"\b{re.escape(marker)}\b", logic)
                for marker in target_type_markers)
        and (
            name == "EndpointPriceSelector.java"
            or ".candidates()" not in logic
        )
        and (
            name == "EndpointPriceSelector.java"
            or "EndpointPriceSelector" not in logic
        ),
        f"Only EndpointPriceSelector may attest request candidates: {name}",
    )
for name, source in target_sources.items():
    logic = without_comments_or_strings(source)
    actual_observation_references = {
        marker for marker in observation_type_markers
        if re.search(rf"\b{re.escape(marker)}\b", logic)
    }
    actual_target_references = {
        marker for marker in target_type_markers
        if re.search(rf"\b{re.escape(marker)}\b", logic)
    }
    require(
        actual_observation_references
        == expected_target_observation_references[name]
        and actual_target_references
        == expected_target_internal_references[name]
        and "EndpointPriceSelector" not in logic
        and "EndpointPriceRequest" not in logic
        and "EndpointPriceObservation" not in logic
        and ".select(" not in logic,
        f"Target-error reverse/reference graph changed: {name}",
    )

selector_source = observation_sources["EndpointPriceSelector.java"]
selector_logic = compact(without_comments_or_strings(selector_source))
endpoint_gate_order = [
    "CATALOG_NOT_KNOWN_AS_OF", "CATALOG_EVIDENCE_MISMATCH",
    "BINDING_NOT_KNOWN_AS_OF", "ENDPOINT_NOT_REACHED_AS_OF",
    "OBSERVATION_MISSING_AS_OF", "ASSET_MISMATCH",
    "PRIMARY_VENUE_MISMATCH", "CURRENCY_MISMATCH", "SOURCE_MISMATCH",
    "CATALOG_MISMATCH", "SESSION_MISMATCH", "OBSERVED_AT_MISMATCH",
    "PRICE_FIELD_MISMATCH", "ADJUSTMENT_BASIS_MISMATCH",
    "CORPORATE_ACTION_CONTINUITY_UNAVAILABLE", "OBSERVATION_AMBIGUOUS",
]
selector_positions = [
    selector_logic.index(f"UnavailableReason.{reason}")
    for reason in endpoint_gate_order
]
require(
    selector_positions == sorted(selector_positions)
    and selector_logic.count(".filter(") == 2
    and selector_logic.count("knownCandidates.stream().anyMatch(") == 10
    and "candidate.availableAt(),evaluationAsOf" in selector_logic
    and "candidate.capturedAt(),evaluationAsOf" in selector_logic
    and "if(knownCandidates.size()>1)" in selector_logic
    and "knownCandidates.getFirst()" in selector_logic
    and ".distinct(" not in selector_logic
    and ".sorted(" not in selector_logic
    and ".sort(" not in selector_logic,
    "Endpoint selector PIT filtering, precedence, or cardinality changed",
)

endpoint_observation = observation_sources["EndpointPriceObservation.java"]
target_evidence = target_sources["TargetPriceEvidence.java"]
require(
    endpoint_observation.count("setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY)")
    == 1
    and target_evidence.count(
        "setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY)"
    ) == 1
    and "STORAGE_SCALE = 12" in endpoint_observation
    and "STORAGE_PRECISION = 38" in endpoint_observation
    and "STORAGE_SCALE = 12" in target_evidence
    and "STORAGE_PRECISION = 38" in target_evidence
    and "availableAt.isBefore(basis.eventTime())" in target_evidence,
    "Positive NUMERIC(38,12) or target temporal boundary changed",
)

calculator_source = target_sources["TargetErrorCalculator.java"]
calculator_logic = compact(without_comments_or_strings(calculator_source))
calculator_reason_order = [
    "TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE", "TARGET_MISSING_AS_OF",
    "ENDPOINT_PRICE_UNAVAILABLE", "BASIS_MISMATCH", "ASSET_MISMATCH",
    "PRIMARY_VENUE_MISMATCH", "CURRENCY_MISMATCH",
    "ADJUSTMENT_BASIS_MISMATCH", "OUTPUT_NOT_REPRESENTABLE",
]
calculator_positions = [
    calculator_logic.index(f"UnavailableReason.{reason}")
    for reason in calculator_reason_order
]
require(
    calculator_positions == sorted(calculator_positions)
    and calculator_logic.count(".divide(") == 1
    and "targetEvidence.target().subtract(actual).abs()" in calculator_logic
    and "numerator.divide(actual,OUTPUT_SCALE,RoundingMode.HALF_EVEN)"
    in calculator_logic
    and "privatestaticfinalintOUTPUT_SCALE=12;" in calculator_logic
    and "privatestaticfinalintOUTPUT_PRECISION=38;" in calculator_logic
    and "targetEvidence.availableAt().isAfter(evaluationAsOf)"
    in calculator_logic
    and "targetEvidence.capturedAt().isAfter(evaluationAsOf)"
    in calculator_logic
    and "targetError.precision()>OUTPUT_PRECISION" in calculator_logic
    and "multiply(newBigDecimal(100" not in calculator_logic
    and "movePointRight(" not in calculator_logic
    and "doubleValue(" not in calculator_logic
    and "floatValue(" not in calculator_logic,
    "Target-error truth table, formula, scale, or output boundary changed",
)

api_main_dir = Path("apps/api/src/main/java")
new_paths = {
    (observation_dir / name).resolve() for name in observation_files
} | {
    (target_dir / name).resolve() for name in target_files
}
new_markers = tuple(
    name.removesuffix(".java") for name in observation_files | target_files
)
approved_later_observation_references = {
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/pricepair/BasisPriceObservation.java")).resolve(): {
        "CorporateActionContinuity", "EndpointPriceAdjustmentBasis",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/pricepair/PricePairAdjustmentEvidence.java")).resolve(): {
        "CorporateActionContinuity", "EndpointPriceAdjustmentBasis",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/pricepair/AssetReturnPricePairPolicyVersion.java")).resolve(): set(),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/pricepair/AssetReturnPricePairRequest.java")).resolve(): {
        "EndpointPricePolicyVersion", "EndpointPriceResolution",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/pricepair/AssetReturnPricePairResolution.java")).resolve(): {
        "CorporateActionContinuity", "EndpointPriceAdjustmentBasis",
        "EndpointPricePolicyVersion", "EndpointPriceResolution",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/pricepair/AssetReturnPricePairSelector.java")).resolve(): {
        "CorporateActionContinuity", "EndpointPriceAdjustmentBasis",
        "EndpointPriceResolution",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/pricepair/BasisPriceField.java")).resolve(): set(),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/assetreturn/AssetReturnPolicyVersion.java")).resolve(): set(),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/assetreturn/AssetReturnInput.java")).resolve(): set(),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/assetreturn/AssetReturnResult.java")).resolve(): set(),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/assetreturn/AssetReturnCalculator.java")).resolve(): {
        "EndpointPriceResolution",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/benchmarkreferencepair/"
          "BenchmarkReferenceLevelPairPolicyVersion.java")).resolve(): set(),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/benchmarkreferencepair/"
          "BenchmarkReferenceLevelPairRequest.java")).resolve(): {
        "EndpointPricePolicyVersion", "EndpointPriceResolution",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/benchmarkreferencepair/"
          "BenchmarkReferenceLevelPairResolution.java")).resolve(): {
        "EndpointPriceResolution",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/benchmarkreferencepair/"
          "BenchmarkReferenceLevelPairSelector.java")).resolve(): set(),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/sectorreferencepair/"
          "SectorReferenceLevelPairPolicyVersion.java")).resolve(): set(),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/sectorreferencepair/"
          "SectorReferenceLevelPairRequest.java")).resolve(): {
        "EndpointPricePolicyVersion", "EndpointPriceResolution",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/sectorreferencepair/"
          "SectorReferenceLevelPairResolution.java")).resolve(): {
        "EndpointPriceResolution",
    },
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/sectorreferencepair/"
          "SectorReferenceLevelPairSelector.java")).resolve(): set(),
}
approved_target_eligibility_references = {
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeteligibility/"
          "TargetEligibilityPolicyVersion.java")).resolve(): (set(), set()),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeteligibility/"
          "BasisForecastTermsEvidence.java")).resolve(): (set(), set()),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeteligibility/"
          "TargetEligibilityRequest.java")).resolve(): (
              {"CatalogPointInTimeEvidence"}, {"TargetPriceEvidence"},
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeteligibility/"
          "TargetEligibilityResolution.java")).resolve(): (
              {"CatalogPointInTimeEvidence"}, {"TargetPriceEvidence"},
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeteligibility/"
          "TargetEligibilityResolver.java")).resolve(): (
              {"CatalogPointInTimeEvidence"}, {"TargetPriceEvidence"},
    ),
}
approved_target_eligibility_references.update({
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/favorableextreme/"
          "FavorableExtremePolicyVersion.java")).resolve(): (set(), set()),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/favorableextreme/"
          "WindowPriceBinding.java")).resolve(): (set(), set()),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/favorableextreme/"
          "FullWindowHighLowObservation.java")).resolve(): (
              {"CorporateActionContinuity",
               "EndpointPriceAdjustmentBasis"}, set(),
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/favorableextreme/"
          "FavorableExtremeRequest.java")).resolve(): (set(), set()),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/favorableextreme/"
          "FavorableExtremeResolution.java")).resolve(): (
              {"CorporateActionContinuity",
               "EndpointPriceAdjustmentBasis"}, set(),
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/favorableextreme/"
          "FavorableExtremeSelector.java")).resolve(): (
              {"CorporateActionContinuity",
               "EndpointPriceAdjustmentBasis"}, set(),
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/directionalwinorchestration/"
          "DirectionalWinOrchestrationRequest.java")).resolve(): (
              {"EndpointPriceResolution"}, set(),
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/directionalwinreadiness/"
          "DirectionalWinReadinessResolver.java")).resolve(): (
              {"EndpointPriceResolution"}, set(),
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeterrorreadiness/"
          "TargetErrorReadinessPolicyVersion.java")).resolve(): (
              set(), set(),
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeterrorreadiness/"
          "TargetErrorReadinessRequest.java")).resolve(): (
              set(), {"TargetErrorPolicyVersion", "TargetErrorResult"},
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeterrorreadiness/"
          "TargetErrorReadinessResolution.java")).resolve(): (
              set(), {"TargetErrorResult"},
    ),
    (Path("apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
          "outcome/targeterrorreadiness/"
          "TargetErrorReadinessResolver.java")).resolve(): (
              {"EndpointPriceResolution"}, {"TargetErrorResult"},
    ),
})
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in new_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    if other_path.resolve() in approved_target_eligibility_references:
        other_logic = without_comments_or_strings(other_source)
        actual_observation_references = {
            marker for marker in observation_type_markers
            if re.search(rf"\b{re.escape(marker)}\b", other_logic)
        }
        actual_target_references = {
            marker for marker in target_type_markers
            if re.search(rf"\b{re.escape(marker)}\b", other_logic)
        }
        expected_observation, expected_target = (
            approved_target_eligibility_references[
                other_path.resolve()
            ]
        )
        require(
            actual_observation_references == expected_observation
            and actual_target_references == expected_target,
            "Approved later endpoint/target reference graph "
            f"changed: {other_path}",
        )
        continue
    if other_path.resolve() in approved_later_observation_references:
        other_logic = without_comments_or_strings(other_source)
        actual_observation_references = {
            marker for marker in observation_type_markers
            if re.search(rf"\b{re.escape(marker)}\b", other_logic)
        }
        require(
            actual_observation_references
            == approved_later_observation_references[other_path.resolve()]
            and not any(re.search(rf"\b{re.escape(marker)}\b", other_logic)
                        for marker in target_type_markers)
            and "domain.outcome.targeterror" not in other_source,
            f"ADR-016/017 endpoint-consumer edge changed: {other_path}",
        )
        continue
    require(
        "domain.outcome.observation" not in other_source
        and "domain.outcome.targeterror" not in other_source
        and not any(marker in other_source for marker in new_markers),
        f"Endpoint/target leaf must not be wired into product runtime: {other_path}",
    )

observation_golden = (
    observation_test_dir / "EndpointPriceSelectorGoldenTest.java"
).read_text(encoding="utf-8")
target_golden = (
    target_test_dir / "TargetErrorCalculatorGoldenTest.java"
).read_text(encoding="utf-8")
observation_golden_logic = compact(
    without_comments_or_strings(observation_golden)
)
target_golden_logic = compact(without_comments_or_strings(target_golden))
for marker in (
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "selectsExactlyOneKnownOfficialPrimaryVenueCloseAtInclusivePitBoundaries",
    "futureCandidatesAreInvisibleToEveryOutputAndReasoningPath",
    "knownCandidateMismatchVectors",
    "rejectsEveryKnownCandidateMismatchWithoutFxFallbackOrInference",
    "appliesFixedMismatchPrecedenceAcrossTheWholeKnownSetBeforeAmbiguity",
    "enforcesCatalogBindingAndEndpointPitGatePrecedence",
    "preservesIndependentCatalogBindingAndObservationProvenance",
    "directResolvedConstructionEnforcesLocalContextConsistencyOnly",
    "validatesImmutableRequestsLocalTimesAndPositiveExactDecimals",
    "acceptsTheExactNumericMaximumAndPreservesOriginalDecimalRepresentation",
    "evidenceConstructorsRejectRepresentativeNullBlankAndTimeOrderMutations",
    "resultAndEvidenceSurfacesRemainClosedAndReplayIgnoresJvmDefaults",
):
    require(
        marker in observation_golden_logic,
        f"Missing executable endpoint-price golden coverage: {marker}",
    )
compact_observation_golden = compact(without_comments(observation_golden))
require(
    compact_observation_golden.count("isEqualTo(withoutFuture)") == 3
    and compact_observation_golden.count("isEqualTo(empty)") == 3
    and "List.of(exact,exact)" in compact_observation_golden
    and "UnavailableReason.OBSERVATION_AMBIGUOUS" in observation_golden
    and "AS_OF.plusNanos(1_000)" in observation_golden
    and "capturedOnlyFuture" in observation_golden_logic
    and all(f"UnavailableReason.{reason}" in observation_golden
            for reason in endpoint_reasons)
    and "Locale.setDefault" in observation_golden_logic
    and "TimeZone.setDefault" in observation_golden_logic
    and "finally" in observation_golden_logic,
    "Endpoint goldens must lock full-result future equality, ambiguity, reasons, and replay",
)

for marker in (
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "calculatesAbsoluteTargetErrorWithActualDenominatorAndScaleTwelveHalfEven",
    "formulaVectors", "futureTargetEvidenceIsIdenticalToNullAndNeverEchoed",
    "preservesTheExactMissingAndEndpointUnavailableTruthTable",
    "propagatesEveryExactEndpointReasonForEndpointOnlyAndCombinedMissing",
    "targetMismatchVectors",
    "rejectsEveryKnownTargetMismatchWithoutFxOrBasisFallback",
    "knownMismatchPrecedenceStartsWithBasisThenAssetVenueCurrencyAndAdjustment",
    "outputOverflowIsUnavailableInsteadOfRoundedIntoStorage",
    "targetEvidenceCannotPredateEitherOriginalOrCorrectionBasis",
    "calculationRequiresTheCompleteCorrectionBasisIdentity",
    "scaleEquivalentTargetAndActualInputsProduceTheSameCanonicalOutput",
    "rejectsMalformedTargetsAndContradictoryDirectResults",
    "targetConstructorsRejectRepresentativeNullBlankAndTimeOrderMutations",
    "resultPolicyAndEvidenceSurfacesStayClosedAndReplayIgnoresJvmDefaults",
):
    require(
        marker in target_golden_logic,
        f"Missing executable target-error golden coverage: {marker}",
    )
compact_target_golden = compact(without_comments(target_golden))
require(
    compact_target_golden.count("isEqualTo(nullResult)") == 2
    and compact_target_golden.count("isEqualTo(nullCombined)") == 1
    and 'Arguments.of("110","100","0.100000000000")'
    in compact_target_golden
    and 'Arguments.of("90","100","0.100000000000")'
    in compact_target_golden
    and 'Arguments.of("2.000000000001","2","0.000000000000")'
    in compact_target_golden
    and 'Arguments.of("2.000000000003","2","0.000000000002")'
    in compact_target_golden
    and 'targetEvidence(basis,"100000000000000.000000000000")'
    in compact_target_golden
    and 'newBigDecimal("99999999999999999999999999.000000000000")'
    in compact_target_golden
    and "adjacentResult.targetError().precision()).isEqualTo(38)"
    in compact_target_golden
    and 'targetEvidence(basis,"100000000000000.000000000001")'
    in compact_target_golden
    and "TargetErrorCalculator.calculate(input(endpoint,precisionThirtyNine))"
    in compact_target_golden
    and "@EnumSource(EndpointPriceResolution.UnavailableReason.class)"
    in target_golden
    and all(f"UnavailableReason.{reason}" in target_golden
            for reason in target_reasons)
    and "Locale.setDefault" in target_golden_logic
    and "TimeZone.setDefault" in target_golden_logic
    and "finally" in target_golden_logic,
    "Target goldens must lock full-result PIT equality, truth table, formula ties, reasons, and replay",
)
require(
    "ObjectMapper" not in observation_golden
    and "ClassPathResource" not in observation_golden
    and "ObjectMapper" not in target_golden
    and "ClassPathResource" not in target_golden,
    "Endpoint/target goldens must remain source-local",
)

expected_schemas = {
    "analyst-call-revision.schema.json", "analyst-call.schema.json",
    "call-context.schema.json", "call-outcome.schema.json",
    "event-context.schema.json", "macro-observation.schema.json",
    "macro-snapshot.schema.json", "market-board.schema.json",
    "market-map.schema.json", "market-snapshot.schema.json",
    "market-treemap.schema.json", "scoring-methodology.schema.json",
    "source-document.schema.json", "source-reference.schema.json",
}
require(
    {path.name for path in Path("schemas").glob("*.json")} == expected_schemas,
    "Endpoint/target slice must preserve the exact 14 schemas",
)
fixture_dir = Path("fixtures/v1")
expected_fixture_files = {
    "analyst-call-revisions.json", "analyst-calls.json",
    "call-contexts.json", "call-outcomes.json", "manifest.json",
    "market-board.json", "market-map-nasdaq100.json", "market-map.json",
    "market-snapshots.json", "market-treemap-nasdaq100.json",
    "market-treemap-sp500.json", "master-data.json", "timeline-nvda.json",
}
require(
    {path.name for path in fixture_dir.glob("*.json")}
    == expected_fixture_files,
    "Endpoint/target slice must preserve the exact 13 fixtures",
)
manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))
require(
    [entry["path"] for entry in manifest["files"]] == [
        "master-data.json", "analyst-calls.json",
        "analyst-call-revisions.json", "call-outcomes.json",
        "call-contexts.json", "market-snapshots.json", "market-map.json",
        "market-map-nasdaq100.json", "market-treemap-sp500.json",
        "market-treemap-nasdaq100.json", "timeline-nvda.json",
        "market-board.json",
    ],
    "Endpoint/target slice must preserve fixture manifest order",
)
outcomes = json.loads(
    (fixture_dir / "call-outcomes.json").read_text(encoding="utf-8")
)
metrics = (
    "assetReturn", "benchmarkReturn", "sectorReturn", "alpha",
    "sectorAlpha", "mfe", "mae", "targetHit", "directionalWin",
    "targetError",
)
require(
    len(outcomes["methodologies"]) == 2
    and all(item["status"] == "MODEL_ONLY"
            for item in outcomes["methodologies"])
    and len(outcomes["outcomes"]) == 4
    and {item["evaluationStatus"] for item in outcomes["outcomes"]}
    == {"PENDING", "INCOMPLETE"}
    and all(item[field] is None
            for item in outcomes["outcomes"] for field in metrics),
    "Endpoint/target leaves must not publish or activate outcomes",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Endpoint/target leaves must preserve the exact five OpenAPI paths",
)
require(
    {path.name for path in Path(
        "apps/api/src/main/resources/db/migration"
    ).glob("*.sql")} == {
        "V1__baseline.sql", "V2__analyst_calls.sql",
        "V3__analyst_call_revisions.sql", "V4__call_outcomes.sql",
        "V5__call_contexts.sql",
        "V6__sec_filing_catalog_captures.sql",
        "V7__sec_historical_filing_segment_captures.sql",
        "V8__sec_filing_history_collection_manifests.sql",
        "V9__sec_filing_collection_attempts.sql",
    },
    "Endpoint/target leaves must preserve nine Flyway migrations",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource_source = resource_path.read_text(encoding="utf-8")
    require(
        not any(marker in resource_source for marker in new_markers),
        f"Endpoint/target leaves must not add JSON goldens: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "domain.outcome.observation" not in web_source
            and "domain.outcome.targeterror" not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Endpoint/target leaves must not expand web: {web_path}",
        )

print(
    "Validated exact point-in-time official endpoint-close selection, future "
    "evidence invisibility, target missing-state composition, actual-denominator "
    "scale-12 HALF_EVEN calculation, closed unavailability, and no provider or "
    "product publication"
)
PYTHON
