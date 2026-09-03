ruby <<'RUBY'
require "yaml"

def require_contract(condition, message)
  abort message unless condition
end

spec = YAML.safe_load_file("contracts/openapi.yaml", aliases: false)
paths = spec.fetch("paths")
components = spec.fetch("components")
parameters = components.fetch("parameters")
responses = components.fetch("responses")
schemas = components.fetch("schemas")
prefix = "/v1/sec/filing-history/manifests/{manifestId}"
sec_paths = {
  prefix => {
    "parameters" => %w[manifestId evaluationAsOf],
    "success" => "#/components/responses/SecManifestSummaryOk"
  },
  "#{prefix}/descriptors" => {
    "parameters" => %w[manifestId evaluationAsOf page size],
    "success" => "#/components/responses/SecManifestDescriptorPageOk",
    "order" => "descriptorOrdinal"
  },
  "#{prefix}/accessions" => {
    "parameters" => %w[manifestId evaluationAsOf page size],
    "success" => "#/components/responses/SecManifestAccessionPageOk",
    "order" => "groupOrdinal"
  },
  "#{prefix}/occurrences" => {
    "parameters" => %w[manifestId evaluationAsOf page size],
    "success" => "#/components/responses/SecManifestOccurrencePageOk",
    "order" => "occurrenceOrdinal"
  }
}
actual_sec_paths = paths.keys.select do |path|
  path.start_with?("/v1/sec/filing-history/manifests")
end
require_contract(
  actual_sec_paths == sec_paths.keys,
  "ADR-052 must expose only the exact summary and three child paths"
)
require_contract(
  paths.keys.none? do |path|
    path.start_with?("/v1/sec") &&
      path.match?(%r{/(?:latest|current|list)(?:/|\z)}i)
  end,
  "ADR-052 must not expose latest/current/list SEC selectors"
)

http_methods = %w[get put post delete options head patch trace]
error_references = {
  "400" => "#/components/responses/SecManifestAuditBadRequest",
  "404" => "#/components/responses/SecManifestAuditNotFound",
  "405" => "#/components/responses/SecManifestAuditMethodNotAllowed",
  "500" => "#/components/responses/SecManifestAuditInternalServerError"
}
sec_paths.each do |path, expectation|
  path_item = paths.fetch(path)
  require_contract(
    path_item.keys == ["get"],
    "ADR-052 resources must expose GET only: #{path}"
  )
  operation = path_item.fetch("get")
  actual_parameters = operation.fetch("parameters").map do |entry|
    reference = entry.fetch("$ref")
    parameters.fetch(reference.split("/").last).fetch("name")
  end
  require_contract(
    actual_parameters == expectation.fetch("parameters"),
    "ADR-052 closed query/path parameter set changed: #{path}"
  )
  operation_responses = operation.fetch("responses")
  require_contract(
    operation_responses.keys == %w[200 400 404 405 500],
    "ADR-052 response statuses changed: #{path}"
  )
  require_contract(
    operation_responses.dig("200", "$ref") == expectation.fetch("success"),
    "ADR-052 success response is miswired: #{path}"
  )
  error_references.each do |status, reference|
    require_contract(
      operation_responses.dig(status, "$ref") == reference,
      "ADR-052 #{status} response is miswired: #{path}"
    )
  end
  if expectation.key?("order")
    description = operation.fetch("description")
    require_contract(
      description.include?(expectation.fetch("order")) &&
        description.include?("ASC") &&
        description.include?(
          "caller-controlled sorting is not supported"
        ),
      "ADR-052 fixed response order changed: #{path}"
    )
  end
  require_contract(
    (path_item.keys & (http_methods - ["get"])).empty?,
    "ADR-052 mutation method appeared: #{path}"
  )
end

manifest_parameter = parameters.fetch("SecManifestId")
require_contract(
  manifest_parameter.slice("in", "name", "required") == {
    "in" => "path", "name" => "manifestId", "required" => true
  } &&
    manifest_parameter.dig("schema", "$ref") ==
      "#/components/schemas/LowercaseSha256",
  "ADR-052 manifestId parameter must remain exact lowercase SHA-256"
)
evaluation_parameter = parameters.fetch("SecManifestEvaluationAsOf")
expected_utc_pattern =
  '^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T' \
  '(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,6})?Z$'
require_contract(
  evaluation_parameter.slice("in", "name", "required") == {
    "in" => "query", "name" => "evaluationAsOf", "required" => true
  } &&
    evaluation_parameter.dig("schema", "type") == "string" &&
    evaluation_parameter.dig("schema", "format") == "date-time" &&
    evaluation_parameter.dig("schema", "pattern") == expected_utc_pattern,
  "ADR-052 evaluationAsOf must remain required UTC Z/microsecond input"
)
page_schema = parameters.dig("SecManifestPage", "schema")
size_schema = parameters.dig("SecManifestPageSize", "schema")
require_contract(
  page_schema == {
    "type" => "integer", "minimum" => 0,
    "maximum" => 2_147_483_647, "default" => 0
  } &&
    size_schema == {
      "type" => "integer", "minimum" => 1,
      "maximum" => 100, "default" => 25
    } &&
    parameters.dig("SecManifestPage", "description").include?(
      "canonical unsigned decimal"
    ) &&
    parameters.dig("SecManifestPageSize", "description").include?(
      "canonical unsigned decimal"
    ),
  "ADR-052 page grammar/defaults/bounds changed"
)

success_responses = {
  "SecManifestSummaryOk" =>
    "#/components/schemas/SecFilingHistoryManifestAuditSummary",
  "SecManifestDescriptorPageOk" =>
    "#/components/schemas/SecFilingHistoryManifestDescriptorPage",
  "SecManifestAccessionPageOk" =>
    "#/components/schemas/SecFilingHistoryManifestAccessionPage",
  "SecManifestOccurrencePageOk" =>
    "#/components/schemas/SecFilingHistoryManifestOccurrencePage"
}
success_responses.each do |name, schema_reference|
  response = responses.fetch(name)
  require_contract(
    response.fetch("headers").keys.sort ==
      ["Cache-Control", "X-Request-Id"] &&
      response.dig("headers", "Cache-Control", "$ref") ==
        "#/components/headers/NoStore" &&
      response.dig("headers", "X-Request-Id", "$ref") ==
        "#/components/headers/RequestId" &&
      response.dig(
        "content", "application/json", "schema", "$ref"
      ) == schema_reference,
    "ADR-052 success response headers/schema changed: #{name}"
  )
end
error_components = {
  "SecManifestAuditBadRequest" =>
    "INVALID_SEC_FILING_HISTORY_MANIFEST_AUDIT_QUERY",
  "SecManifestAuditNotFound" =>
    "SEC_FILING_HISTORY_MANIFEST_NOT_FOUND",
  "SecManifestAuditMethodNotAllowed" => "METHOD_NOT_ALLOWED",
  "SecManifestAuditInternalServerError" => "INTERNAL_ERROR"
}
error_components.each do |name, code|
  response = responses.fetch(name)
  expected_headers = name == "SecManifestAuditMethodNotAllowed" ?
    ["Allow", "Cache-Control", "X-Request-Id"] :
    ["Cache-Control", "X-Request-Id"]
  require_contract(
    response["x-problem-code"] == code &&
      response.fetch("headers").keys.sort == expected_headers &&
      response.dig("headers", "Cache-Control", "$ref") ==
        "#/components/headers/NoStore" &&
      response.dig("headers", "X-Request-Id", "$ref") ==
        "#/components/headers/RequestId" &&
      response.dig(
        "content", "application/problem+json", "schema", "$ref"
      ) == "#/components/schemas/Problem",
    "ADR-052 sanitized no-store problem response changed: #{name}"
  )
end

exact_fields = {
  "SecFilingHistoryManifestAuditSummary" => %w[
    auditSchemaVersion auditPolicyVersion evaluationAsOf manifestId
    manifestSchemaVersion provider product policyVersion selectionSha256
    rootCaptureId rootCapturedAt cik evidenceAvailableAt assembledAt
    selectionCoverage advertisedDescriptorCount selectedDescriptorCount
    omittedDescriptorCount sourceOccurrenceCount distinctAccessionCount
    singleSourceAccessionCount exactAgreementAccessionCount
    canonicalConflictAccessionCount immutable disclosure
  ],
  "SecFilingHistoryManifestDisclosure" => %w[
    coverageScope atomicSecSnapshotClaim currentHistoryStatus
    correctionRemovalStatus amendmentLinkageStatus legalAuthorityStatus
  ],
  "SecFilingHistoryManifestDescriptor" => %w[
    descriptorOrdinal fileName advertisedFilingCount advertisedFilingFrom
    advertisedFilingTo selectionState selectedSegmentCaptureId
  ],
  "SecFilingHistoryManifestAccession" => %w[
    groupOrdinal accessionNumber occurrenceCount distinctProjectionCount
    comparison
  ],
  "SecFilingHistoryManifestOccurrence" => %w[
    occurrenceOrdinal groupOrdinal sourceKind sourceCaptureId
    descriptorOrdinal sourceRowOrdinal projectionSha256 providerEventId
    accessionNumber form filingDate reportDate acceptedAt primaryDocumentUri
  ],
  "SecFilingHistoryManifestDescriptorPageMetadata" => %w[
    number size totalElements totalPages first last order
  ],
  "SecFilingHistoryManifestAccessionPageMetadata" => %w[
    number size totalElements totalPages first last order
  ],
  "SecFilingHistoryManifestOccurrencePageMetadata" => %w[
    number size totalElements totalPages first last order
  ]
}
exact_fields.each do |name, fields|
  schema = schemas.fetch(name)
  require_contract(
    schema["type"] == "object" &&
      schema["additionalProperties"] == false &&
      schema.fetch("properties").keys.sort == fields.sort &&
      schema.fetch("required").sort == fields.sort,
    "ADR-052 closed schema fields changed: #{name}"
  )
end
require_contract(
  schemas.dig("LowercaseSha256", "pattern") == '^[0-9a-f]{64}$' &&
    schemas.dig("PersistentUtcInstant", "pattern") ==
      expected_utc_pattern,
  "ADR-052 identity/time primitives changed"
)
summary_properties = schemas.fetch(
  "SecFilingHistoryManifestAuditSummary"
).fetch("properties")
require_contract(
  summary_properties.dig("auditSchemaVersion", "const") == "1.0.0" &&
    summary_properties.dig("auditPolicyVersion", "const") ==
      "SEC_EXACT_MANIFEST_AUDIT_V1" &&
    summary_properties.dig("manifestSchemaVersion", "const") ==
      "1.0.0" &&
    summary_properties.dig("provider", "const") == "sec-edgar" &&
    summary_properties.dig("product", "const") ==
      "edgar-submissions-root-relative-collection-manifest" &&
    summary_properties.dig("policyVersion", "const") ==
      "SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1" &&
    summary_properties.dig("immutable", "const") == true &&
    schemas.dig("SecFilingHistorySelectionCoverage", "enum") == [
      "NO_ADVERTISED_DESCRIPTORS",
      "PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED",
      "ALL_ADVERTISED_DESCRIPTORS_SELECTED"
    ] &&
    schemas.dig("SecFilingHistoryManifestDescriptor", "properties",
                "selectionState", "enum") == [
      "NOT_SELECTED", "SELECTED_EXACT_CAPTURE"
    ] &&
    schemas.dig("SecFilingHistoryManifestAccession", "properties",
                "comparison", "enum") == [
      "SINGLE_SOURCE_OCCURRENCE",
      "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT",
      "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT"
    ] &&
    schemas.dig("SecFilingHistoryManifestOccurrence", "properties",
                "sourceKind", "enum") == [
      "ROOT_RECENT", "HISTORICAL_SEGMENT"
    ],
  "ADR-052 evidence identity/classification constants changed"
)
fixed_page_orders = {
  "SecFilingHistoryManifestDescriptorPageMetadata" =>
    "descriptorOrdinal",
  "SecFilingHistoryManifestAccessionPageMetadata" =>
    "groupOrdinal",
  "SecFilingHistoryManifestOccurrencePageMetadata" =>
    "occurrenceOrdinal"
}
fixed_page_orders.each do |name, field|
  order = schemas.dig(name, "properties", "order")
  require_contract(
    order["type"] == "object" &&
      order["additionalProperties"] == false &&
      order.fetch("required").sort == %w[direction field] &&
      order.fetch("properties").keys.sort == %w[direction field] &&
      order.dig("properties", "field", "const") == field &&
      order.dig("properties", "direction", "const") == "ASC",
    "ADR-052 route-specific fixed order changed: #{name}"
  )
end
disclosure_constants = {
  "coverageScope" => "ROOT_RELATIVE_SELECTED_REFERENCES_ONLY",
  "atomicSecSnapshotClaim" => "NOT_MADE",
  "currentHistoryStatus" => "NOT_RESOLVED",
  "correctionRemovalStatus" => "NOT_RESOLVED",
  "amendmentLinkageStatus" => "NOT_RESOLVED",
  "legalAuthorityStatus" => "NOT_CLAIMED"
}
disclosure_constants.each do |field, value|
  require_contract(
    schemas.dig("SecFilingHistoryManifestDisclosure", "properties",
                field, "const") == value,
    "ADR-052 disclosure semantics changed: #{field}"
  )
end
page_items = {
  "SecFilingHistoryManifestDescriptorPage" => [
    "#/components/schemas/SecFilingHistoryManifestDescriptor",
    "#/components/schemas/SecFilingHistoryManifestDescriptorPageMetadata"
  ],
  "SecFilingHistoryManifestAccessionPage" => [
    "#/components/schemas/SecFilingHistoryManifestAccession",
    "#/components/schemas/SecFilingHistoryManifestAccessionPageMetadata"
  ],
  "SecFilingHistoryManifestOccurrencePage" => [
    "#/components/schemas/SecFilingHistoryManifestOccurrence",
    "#/components/schemas/SecFilingHistoryManifestOccurrencePageMetadata"
  ]
}
page_items.each do |name, references|
  item_reference, page_reference = references
  schema = schemas.fetch(name)
  fields = %w[
    auditSchemaVersion auditPolicyVersion manifestId evaluationAsOf items page
  ]
  require_contract(
    schema["type"] == "object" &&
      schema["additionalProperties"] == false &&
      schema.fetch("properties").keys.sort == fields.sort &&
      schema.fetch("required").sort == fields.sort &&
      schema.dig("properties", "auditSchemaVersion", "const") ==
        "1.0.0" &&
      schema.dig("properties", "auditPolicyVersion", "const") ==
        "SEC_EXACT_MANIFEST_AUDIT_V1" &&
      schema.dig("properties", "items", "maxItems") == 100 &&
      schema.dig("properties", "items", "items", "$ref") ==
        item_reference &&
      schema.dig("properties", "page", "$ref") ==
        page_reference,
    "ADR-052 bounded closed page schema changed: #{name}"
  )
end

forbidden_item_fields = %w[
  dataMode latest current winner issuer ticker rawBody rawHeaders
  contactEmail operatorToken attemptId
]
item_fields = exact_fields.values.flatten
require_contract(
  (item_fields & forbidden_item_fields).empty?,
  "ADR-052 response schema exposes a forbidden inferred/operator field"
)
puts "Validated exact anonymous read-only ADR-052 SEC manifest audit contract"
RUBY
