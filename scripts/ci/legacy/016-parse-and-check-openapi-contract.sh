ruby <<'RUBY'
require "json"
require "yaml"

path = "contracts/openapi.yaml"
spec = YAML.safe_load_file(path, aliases: false)
abort "OpenAPI document must be an object" unless spec.is_a?(Hash)
abort "OpenAPI 3.1.0 is required" unless spec["openapi"] == "3.1.0"
abort "OpenAPI contract version 0.5.0 is required" unless spec.dig("info", "version") == "0.5.0"

required_paths = [
  "/v1/calls",
  "/v1/calls/{id}",
  "/v1/calls/{id}/revisions",
  "/v1/calls/{id}/outcomes",
  "/v1/calls/{id}/context",
  "/v1/sec/filing-history/manifests/{manifestId}",
  "/v1/sec/filing-history/manifests/{manifestId}/descriptors",
  "/v1/sec/filing-history/manifests/{manifestId}/accessions",
  "/v1/sec/filing-history/manifests/{manifestId}/occurrences"
]
abort "OpenAPI paths must be the exact approved nine-path surface" unless spec.fetch("paths").keys == required_paths
required_paths.each do |required_path|
  operation = spec.dig("paths", required_path, "get")
  abort "Missing GET #{required_path}" unless operation.is_a?(Hash)
end

revisions_path = spec.dig("paths", "/v1/calls/{id}/revisions")
abort "Revision subresource must expose GET only" unless revisions_path.keys == ["get"]
http_methods = %w[get put post delete options head patch trace]
mutation_operations = spec.fetch("paths").flat_map do |candidate_path, path_item|
  next [] unless candidate_path.start_with?("/v1/calls/{id}/revisions")

  path_item.keys & (http_methods - ["get"])
end
abort "Revision resources must be read-only" unless mutation_operations.empty?
revision_get = revisions_path.fetch("get")
expected_responses = %w[200 400 404 500]
abort "Unexpected revision responses" unless revision_get.fetch("responses").keys.sort == expected_responses
revision_success = revision_get.dig("responses", "200", "content", "application/json", "schema", "$ref")
abort "Revision success must use the canonical list schema" unless revision_success == "#/components/schemas/AnalystCallRevisionList"
expected_error_responses = {
  "400" => "#/components/responses/BadRequest",
  "404" => "#/components/responses/NotFound",
  "500" => "#/components/responses/InternalServerError"
}
expected_error_responses.each do |status, expected_reference|
  response_reference = revision_get.dig("responses", status, "$ref")
  abort "Revision #{status} response is miswired" unless response_reference == expected_reference
end

revision_list_schema = spec.dig("components", "schemas", "AnalystCallRevisionList")
abort "Revision list must be an array" unless revision_list_schema["type"] == "array"
abort "Revision list item schema is miswired" unless revision_list_schema.dig("items", "$ref") == "#/components/schemas/AnalystCallRevision"

outcome_prefix = "/v1/calls/{id}/outcomes"
outcome_paths = spec.fetch("paths").keys.select { |candidate| candidate.start_with?(outcome_prefix) }
abort "Outcome contract must expose exactly #{outcome_prefix}" unless outcome_paths == [outcome_prefix]
outcomes_path = spec.dig("paths", outcome_prefix)
abort "Outcome subresource must expose GET only" unless outcomes_path.keys == ["get"]
outcome_mutations = spec.fetch("paths").flat_map do |candidate_path, path_item|
  next [] unless candidate_path.start_with?(outcome_prefix)

  path_item.keys & (http_methods - ["get"])
end
abort "Outcome resources must be read-only" unless outcome_mutations.empty?

outcome_get = outcomes_path.fetch("get")
expected_outcome_responses = %w[200 400 404 500]
abort "Unexpected outcome responses" unless outcome_get.fetch("responses").keys.sort == expected_outcome_responses
outcome_success = outcome_get.dig("responses", "200", "content", "application/json", "schema", "$ref")
abort "Outcome success must use CallOutcomeList" unless outcome_success == "#/components/schemas/CallOutcomeList"
expected_error_responses.each do |status, expected_reference|
  response_reference = outcome_get.dig("responses", status, "$ref")
  abort "Outcome #{status} response is miswired" unless response_reference == expected_reference
end

outcome_schema_reference = "../schemas/call-outcome.schema.json"
outcome_component = spec.dig("components", "schemas", "CallOutcome", "$ref")
abort "CallOutcome must reference the external canonical schema" unless outcome_component == outcome_schema_reference
outcome_list_schema = spec.dig("components", "schemas", "CallOutcomeList")
abort "Outcome list must be an array" unless outcome_list_schema["type"] == "array"
abort "Outcome list items must use the canonical component" unless outcome_list_schema.dig("items", "$ref") == "#/components/schemas/CallOutcome"

outcome_schema_path = File.expand_path(outcome_schema_reference, File.dirname(path))
outcome_schema = JSON.parse(File.read(outcome_schema_path))
expected_outcome_fields = %w[
  outcomeId schemaVersion callId horizon basisRevisionId cancellationRevisionId snapshotId
  methodologyId methodologyVersion methodologyDefinitionHash inputFingerprint
  sequenceNumber supersedesOutcomeId evaluationStatus reasonCode eventTime
  processingTime assetReturn benchmarkReturn sectorReturn alpha sectorAlpha mfe mae
  targetHit directionalWin targetError dataComplete dataMode capturedAt provenanceId
].sort
abort "CallOutcome schema must be closed" unless outcome_schema["additionalProperties"] == false
abort "CallOutcome properties are not exact" unless outcome_schema.fetch("properties").keys.sort == expected_outcome_fields
abort "Every CallOutcome field must be required" unless outcome_schema.fetch("required").sort == expected_outcome_fields

context_prefix = "/v1/calls/{id}/context"
context_paths = spec.fetch("paths").keys.select { |candidate| candidate.start_with?(context_prefix) }
abort "Context contract must expose exactly #{context_prefix}" unless context_paths == [context_prefix]
context_path = spec.dig("paths", context_prefix)
abort "Context subresource must expose GET only" unless context_path.keys == ["get"]
context_mutations = spec.fetch("paths").flat_map do |candidate_path, path_item|
  next [] unless candidate_path.start_with?(context_prefix)

  path_item.keys & (http_methods - ["get"])
end
abort "Context resources must be read-only" unless context_mutations.empty?

context_get = context_path.fetch("get")
abort "Unexpected context responses" unless context_get.fetch("responses").keys.sort == expected_responses
context_success = context_get.dig("responses", "200", "content", "application/json", "schema", "$ref")
abort "Context success must use CallContext" unless context_success == "#/components/schemas/CallContext"
expected_error_responses.each do |status, expected_reference|
  response_reference = context_get.dig("responses", status, "$ref")
  abort "Context #{status} response is miswired" unless response_reference == expected_reference
end

context_schema_references = {
  "MacroObservation" => "../schemas/macro-observation.schema.json",
  "MacroSnapshot" => "../schemas/macro-snapshot.schema.json",
  "EventContext" => "../schemas/event-context.schema.json",
  "CallContext" => "../schemas/call-context.schema.json"
}
context_schema_references.each do |component_name, expected_reference|
  actual_reference = spec.dig("components", "schemas", component_name, "$ref")
  abort "#{component_name} component is miswired" unless actual_reference == expected_reference
end

expected_context_fields = {
  "macro-observation.schema.json" => %w[
    schemaVersion macroObservationId series value unit observationDate releasedAt
    processingTime vintageStart vintageEnd sourceReferenceId dataMode capturedAt provenanceId
  ],
  "macro-snapshot.schema.json" => %w[
    schemaVersion macroSnapshotId callId eventTime processingTime observations
    immutable dataMode capturedAt provenanceId
  ],
  "event-context.schema.json" => %w[
    schemaVersion eventContextId callId eventTime processingTime earningsAt nextCpiAt
    nextFomcAt nextNfpAt optionsExpirationAt sourceReferenceId immutable dataMode capturedAt provenanceId
  ],
  "call-context.schema.json" => %w[macroSnapshot eventContext]
}
expected_context_fields.each do |schema_name, expected_fields|
  schema_path = File.expand_path("../schemas/#{schema_name}", File.dirname(path))
  schema = JSON.parse(File.read(schema_path))
  abort "#{schema_name} must be closed" unless schema["additionalProperties"] == false
  abort "#{schema_name} properties are not exact" unless schema.fetch("properties").keys.sort == expected_fields.sort
  abort "Every #{schema_name} field must be required" unless schema.fetch("required").sort == expected_fields.sort
end

list_parameters = spec.dig("paths", "/v1/calls", "get", "parameters")
abort "List parameters must be an array" unless list_parameters.is_a?(Array)
actual_filters = list_parameters.map do |entry|
  reference = entry["$ref"]
  parameter = reference ? spec.dig("components", "parameters", reference.split("/").last) : entry
  parameter && parameter["name"]
end.compact.sort
expected_filters = %w[
  analystId assetId dataMode direction from institutionId order page size sort status ticker to
].sort
abort "Unexpected list filters: #{actual_filters.inspect}" unless actual_filters == expected_filters

page_schema = spec.dig("components", "schemas", "AnalystCallPage")
page_keys = page_schema.fetch("properties").keys.sort
page_required = page_schema.fetch("required").sort
abort "List response must be exactly items/page" unless page_keys == %w[items page] && page_required == %w[items page]

sort_schema = spec.dig("components", "schemas", "SortMetadata")
sort_keys = sort_schema.fetch("properties").keys.sort
abort "page.sort must be exactly field/order" unless sort_keys == %w[field order]

detail_schema = spec.dig("components", "schemas", "AnalystCallDetail")
detail_fields = %w[analyst asset call institution snapshot source]
abort "Detail response must remain closed" unless detail_schema["additionalProperties"] == false
abort "Detail response shape changed" unless detail_schema.fetch("properties").keys.sort == detail_fields
abort "Detail required fields changed" unless detail_schema.fetch("required").sort == detail_fields

references = []
walk = lambda do |value|
  case value
  when Hash
    references << value["$ref"] if value["$ref"].is_a?(String)
    value.each_value { |child| walk.call(child) }
  when Array
    value.each { |child| walk.call(child) }
  end
end
walk.call(spec)
references.grep(%r{^\.\./schemas/}).each do |reference|
  relative_path = reference.split("#", 2).first
  resolved = File.expand_path(relative_path, File.dirname(path))
  abort "Missing external schema #{reference}" unless File.file?(resolved)
end

puts "OpenAPI contract parsed and exact current nine-path surface is present"
RUBY
