set -euo pipefail
for file in fixtures/v1/*.json; do
  jq empty "$file"
  jq -e '
    .schemaVersion == "1.0.0"
    and .fixtureVersion == "v1"
    and .dataMode == "DEMO"
    and (.generatedAt | type == "string")
    and (.provenance | type == "object")
  ' "$file" >/dev/null
done
