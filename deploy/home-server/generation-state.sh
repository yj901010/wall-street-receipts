#!/usr/bin/env bash
# Source-only ADR-050 generation-control foundation.
#
# It defines canonical documents, a fixed preprovisioned host lock, durable
# publication helpers, and pure crash classification. It exposes no operator
# action and never invokes Docker or selects, creates, starts, stops, or removes
# a production volume.
if [[ -n "${WSR_GENERATION_STATE_LOADED:-}" ]]; then
  # shellcheck disable=SC2317
  return 0 2>/dev/null || exit 0
fi
WSR_GENERATION_STATE_LOADED=1

readonly WSR_GENERATION_CONTROL_ROOT="/var/lib/wall-street-receipts/generation-control"
readonly WSR_GENERATION_OPERATION_LOCK_PATH="$WSR_GENERATION_CONTROL_ROOT/operation.lock"
readonly WSR_GENERATION_PROJECT="wall-street-receipts-home"
readonly WSR_GENERATION_LEGACY_ID="legacy-v1"
readonly WSR_GENERATION_LEGACY_VOLUME="wall-street-receipts-home_postgres-data"
readonly WSR_GENERATION_CANDIDATE_PREFIX="wall-street-receipts-generation-"
readonly WSR_GENERATION_ZERO_SHA256="0000000000000000000000000000000000000000000000000000000000000000"
readonly WSR_GENERATION_DOCUMENT_MAX_BYTES=32768
readonly WSR_GENERATION_SELECTOR_SCHEMA_VERSION="1"
readonly WSR_GENERATION_MANIFEST_SCHEMA_VERSION="1"
readonly WSR_GENERATION_BACKUP_BINDING_SCHEMA_VERSION="2"
readonly WSR_GENERATION_JOURNAL_SCHEMA_VERSION="1"
readonly WSR_GENERATION_LOCK_CONTRACT_VERSION="1"

WSR_GENERATION_FAILURE_REASON=""
WSR_GENERATION_FAILURE_MESSAGE=""
WSR_GENERATION_OPERATION_LOCK_FD=""
WSR_GENERATION_OPERATION_LOCK_MODE=""
WSR_GENERATION_OPERATION_LOCK_OPEN_PATH=""
WSR_GENERATION_OPERATION_LOCK_IDENTITY=""
WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID=""
WSR_GENERATION_JOURNAL_STATE=""
WSR_GENERATION_JOURNAL_STATUS=""
WSR_GENERATION_JOURNAL_RECOVERY_DIRECTIVE=""
WSR_GENERATION_JOURNAL_LAST_RECORD_SHA256=""

declare -Ag WSR_GENERATION_SELECTOR=()
declare -Ag WSR_GENERATION_MANIFEST=()
declare -Ag WSR_GENERATION_BACKUP_BINDING=()
declare -Ag WSR_GENERATION_JOURNAL_RECORD=()

readonly -a WSR_GENERATION_SELECTOR_FIELDS=(
  schema_version project revision active_generation_id
  active_generation_manifest_sha256 active_volume_name
  previous_selector_sha256 change_kind transition_uuid plan_sha256 written_utc
)
readonly -a WSR_GENERATION_MANIFEST_FIELDS=(
  schema_version project generation_id generation_kind postgres_volume_name
  volume_driver volume_created_utc volume_labels_sha256 source_backup_id
  source_backup_manifest_sha256 source_archive_sha256 source_restore_evidence_id
  source_restore_manifest_sha256 source_database_evidence_sha256
  promotion_plan_sha256 git_sha postgres_image_reference postgres_image_id
  postgres_image_revision authentication_contract created_utc sealed_utc state
)
readonly -a WSR_GENERATION_BACKUP_BINDING_FIELDS=(
  schema_version backup_id source_generation_contract_version
  source_generation_id source_generation_kind source_generation_manifest_sha256
  source_volume_name source_volume_created_utc source_volume_labels_sha256
  active_selector_schema_version active_selector_revision active_selector_sha256
  capture_lock_contract_version
)
readonly -a WSR_GENERATION_JOURNAL_FIELDS=(
  schema_version transition_uuid operation_uuid record_sequence record_kind
  state_before event state_after plan_sha256 source_generation_id
  source_generation_manifest_sha256 source_volume_name target_generation_id
  target_generation_manifest_sha256 target_volume_name selector_before_revision
  selector_before_sha256 selector_after_revision selector_after_sha256
  previous_record_sha256 written_utc
)

wsr_generation_fail() {
  WSR_GENERATION_FAILURE_REASON="$1"
  WSR_GENERATION_FAILURE_MESSAGE="$2"
  return 1
}

wsr_generation_associative_map_name_valid() {
  local name="$1" declaration
  [[ "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || return 1
  declaration="$(declare -p "$name" 2>/dev/null)" || return 1
  [[ "$declaration" =~ ^declare[[:space:]]+-[^[:space:]]*A[^[:space:]]*[[:space:]] ]]
}

wsr_generation_indexed_array_name_valid() {
  local name="$1" declaration
  [[ "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || return 1
  declaration="$(declare -p "$name" 2>/dev/null)" || return 1
  [[ "$declaration" =~ ^declare[[:space:]]+-[^[:space:]]*a[^[:space:]]*[[:space:]] ]]
}

wsr_generation_parser_adapter_valid() {
  case "$1|$2|$3" in
    WSR_GENERATION_SELECTOR_FIELDS\|wsr_generation_validate_selector_map\|wsr_generation_render_selector_v1|\
    WSR_GENERATION_MANIFEST_FIELDS\|wsr_generation_validate_manifest_map\|wsr_generation_render_manifest_v1|\
    WSR_GENERATION_BACKUP_BINDING_FIELDS\|wsr_generation_validate_backup_binding_map\|wsr_generation_render_backup_binding_v2|\
    WSR_GENERATION_JOURNAL_FIELDS\|wsr_generation_validate_journal_record_map\|wsr_generation_render_journal_record_v1)
      return 0
      ;;
    *) return 1 ;;
  esac
}

wsr_generation_sha256_valid() {
  [[ "$1" =~ ^[0-9a-f]{64}$ ]]
}

wsr_generation_nonzero_sha256_valid() {
  wsr_generation_sha256_valid "$1" && [[ "$1" != "$WSR_GENERATION_ZERO_SHA256" ]]
}

wsr_generation_uuid_valid() {
  [[ "$1" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$ ]]
}

wsr_generation_revision_valid() {
  [[ "$1" =~ ^[0-9]{16}$ && "$1" != "0000000000000000" ]]
}

wsr_generation_backup_id_valid() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z-[A-Za-z0-9]{8}$ ]]
}

wsr_generation_id_valid() {
  [[ "$1" == "$WSR_GENERATION_LEGACY_ID" ||
     "$1" =~ ^wall-street-receipts-generation-[0-9]{8}t[0-9]{6}z-[a-z0-9]{8}-[0-9]{8}t[0-9]{6}z-[a-z0-9]{8}$ ]]
}

wsr_generation_managed_id_valid() {
  wsr_generation_id_valid "$1" && [[ "$1" != "$WSR_GENERATION_LEGACY_ID" ]]
}

wsr_generation_candidate_id_for_sources() {
  local backup_id="$1" evidence_id="$2"
  wsr_generation_backup_id_valid "$backup_id" &&
    wsr_generation_backup_id_valid "$evidence_id" || return 1
  printf '%s%s-%s\n' "$WSR_GENERATION_CANDIDATE_PREFIX" "${backup_id,,}" "${evidence_id,,}"
}

wsr_generation_volume_matches_id() {
  if [[ "$1" == "$WSR_GENERATION_LEGACY_ID" ]]; then
    [[ "$2" == "$WSR_GENERATION_LEGACY_VOLUME" ]]
  else
    wsr_generation_managed_id_valid "$1" && [[ "$2" == "$1" ]]
  fi
}

wsr_generation_utc_valid() {
  local value="$1" normalized
  [[ "$value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || return 1
  normalized="$(date -u --date="$value" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)" || return 1
  [[ "$normalized" == "$value" ]]
}

wsr_generation_require_exact_map() {
  local map_name="$1" fields_name="$2" field key found
  wsr_generation_associative_map_name_valid "$map_name" &&
    wsr_generation_indexed_array_name_valid "$fields_name" || {
    wsr_generation_fail "invalid-map-reference" "A generation map reference is invalid."
    return 1
  }
  local -n document_ref="$map_name"
  local -n fields_ref="$fields_name"
  ((${#document_ref[@]} == ${#fields_ref[@]})) || {
    wsr_generation_fail "document-field-set-invalid" "The generation document field set is not exact."
    return 1
  }
  for field in "${fields_ref[@]}"; do
    [[ -n "${document_ref[$field]+present}" ]] || {
      wsr_generation_fail "document-field-set-invalid" "The generation document is missing $field."
      return 1
    }
  done
  for key in "${!document_ref[@]}"; do
    found=false
    for field in "${fields_ref[@]}"; do
      [[ "$key" == "$field" ]] && found=true
    done
    [[ "$found" == true ]] || {
      wsr_generation_fail "document-field-set-invalid" "The generation document contains unknown field $key."
      return 1
    }
  done
}

wsr_generation_render_exact_map() {
  local map_name="$1" fields_name="$2" field
  wsr_generation_require_exact_map "$map_name" "$fields_name" || return 1
  local -n document_ref="$map_name"
  local -n fields_ref="$fields_name"
  for field in "${fields_ref[@]}"; do
    printf '%s=%s\n' "$field" "${document_ref[$field]}"
  done
}

wsr_generation_parse_ordered_file() {
  local path="$1" target_name="$2" fields_name="$3" validator="$4" renderer="$5"
  local size last_byte first_three line key value expected rendered actual_sha expected_sha
  local line_number=0
  if ! wsr_generation_associative_map_name_valid "$target_name" ||
     ! wsr_generation_indexed_array_name_valid "$fields_name" ||
     ! wsr_generation_parser_adapter_valid "$fields_name" "$validator" "$renderer"; then
    wsr_generation_fail "invalid-parser-reference" "A generation parser reference is invalid."
    return 1
  fi
  local -n target_ref="$target_name"
  local -n fields_ref="$fields_name"
  target_ref=()
  if [[ ! -f "$path" || -L "$path" ]]; then
    wsr_generation_fail "document-not-regular" "The generation document must be a regular non-symlink file."
    return 1
  fi
  size="$(stat -c '%s' -- "$path" 2>/dev/null)" || return 1
  if [[ ! "$size" =~ ^[1-9][0-9]*$ ]] || ((size > WSR_GENERATION_DOCUMENT_MAX_BYTES)); then
    wsr_generation_fail "document-size-invalid" "The generation document is empty or oversized."
    return 1
  fi
  last_byte="$(tail -c 1 -- "$path" | od -An -t u1 | tr -d '[:space:]')"
  first_three="$(head -c 3 -- "$path" | od -An -t u1 | tr -s '[:space:]' ' ' | sed 's/^ //;s/ $//')"
  if [[ "$last_byte" != "10" || "$first_three" == "239 187 191" ]] ||
     LC_ALL=C grep -q $'\r' -- "$path"; then
    wsr_generation_fail "document-byte-contract-invalid" "Generation documents are BOM-free LF-only records with one final LF."
    return 1
  fi
  while IFS= read -r line; do
    line_number=$((line_number + 1))
    if [[ -z "$line" || "$line" == \#* ||
          ! "$line" =~ ^([a-z][a-z0-9_]*)=([A-Za-z0-9._:/+-]+)$ ]] ||
       ((line_number > ${#fields_ref[@]})); then
      target_ref=()
      wsr_generation_fail "document-line-invalid" "Generation document line $line_number is non-canonical."
      return 1
    fi
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    expected="${fields_ref[line_number - 1]}"
    if [[ "$key" != "$expected" ]]; then
      target_ref=()
      wsr_generation_fail "document-field-order-invalid" "Expected $expected at line $line_number."
      return 1
    fi
    target_ref["$key"]="$value"
  done < "$path"
  if ((line_number != ${#fields_ref[@]})) || ! "$validator" "$target_name"; then
    target_ref=()
    [[ -n "$WSR_GENERATION_FAILURE_REASON" ]] ||
      wsr_generation_fail "document-field-count-invalid" "The generation document field count is not exact."
    return 1
  fi
  rendered="$("$renderer" "$target_name")" || return 1
  actual_sha="$(sha256sum -- "$path" | awk '{print $1}')"
  expected_sha="$(printf '%s\n' "$rendered" | sha256sum | awk '{print $1}')"
  if [[ "$actual_sha" != "$expected_sha" ]]; then
    target_ref=()
    wsr_generation_fail "document-canonical-reread-mismatch" "The source bytes differ from the canonical rendering."
    return 1
  fi
}

wsr_generation_validate_selector_map() {
  local map_name="$1"
  wsr_generation_require_exact_map "$map_name" WSR_GENERATION_SELECTOR_FIELDS || return 1
  local -n selector_ref="$map_name"
  if [[ "${selector_ref[schema_version]}" != "$WSR_GENERATION_SELECTOR_SCHEMA_VERSION" ||
        "${selector_ref[project]}" != "$WSR_GENERATION_PROJECT" ]] ||
     ! wsr_generation_revision_valid "${selector_ref[revision]}" ||
     ! wsr_generation_id_valid "${selector_ref[active_generation_id]}" ||
     ! wsr_generation_nonzero_sha256_valid "${selector_ref[active_generation_manifest_sha256]}" ||
     ! wsr_generation_volume_matches_id "${selector_ref[active_generation_id]}" "${selector_ref[active_volume_name]}" ||
     ! wsr_generation_sha256_valid "${selector_ref[previous_selector_sha256]}" ||
     ! wsr_generation_utc_valid "${selector_ref[written_utc]}"; then
    wsr_generation_fail "selector-value-invalid" "The selector v1 value contract is invalid."
    return 1
  fi
  case "${selector_ref[change_kind]}" in
    legacy-bootstrap)
      [[ "${selector_ref[revision]}" == "0000000000000001" &&
         "${selector_ref[active_generation_id]}" == "$WSR_GENERATION_LEGACY_ID" &&
         "${selector_ref[previous_selector_sha256]}" == "$WSR_GENERATION_ZERO_SHA256" &&
         "${selector_ref[transition_uuid]}" == "bootstrap" &&
         "${selector_ref[plan_sha256]}" == "bootstrap" ]] || {
        wsr_generation_fail "selector-bootstrap-invalid" "The legacy bootstrap selector is not explicit and canonical."
        return 1
      }
      ;;
    promotion|rollback)
      if [[ "${selector_ref[change_kind]}" == "promotion" &&
            "${selector_ref[active_generation_id]}" == "$WSR_GENERATION_LEGACY_ID" ]]; then
        wsr_generation_fail "selector-promotion-target-invalid" "A promotion selector must name a managed generation."
        return 1
      fi
      wsr_generation_uuid_valid "${selector_ref[transition_uuid]}" &&
        wsr_generation_nonzero_sha256_valid "${selector_ref[plan_sha256]}" &&
        [[ "${selector_ref[previous_selector_sha256]}" != "$WSR_GENERATION_ZERO_SHA256" ]] || {
          wsr_generation_fail "selector-transition-invalid" "Promotion and rollback selectors require exact predecessor, transition, and plan evidence."
          return 1
        }
      ;;
    *)
      wsr_generation_fail "selector-change-kind-invalid" "The selector change kind is not allowlisted."
      return 1
      ;;
  esac
}

wsr_generation_render_selector_v1() {
  wsr_generation_validate_selector_map "$1" || return 1
  wsr_generation_render_exact_map "$1" WSR_GENERATION_SELECTOR_FIELDS
}

wsr_generation_parse_selector_v1() {
  wsr_generation_parse_ordered_file "$1" "${2:-WSR_GENERATION_SELECTOR}" \
    WSR_GENERATION_SELECTOR_FIELDS wsr_generation_validate_selector_map \
    wsr_generation_render_selector_v1
}

wsr_generation_validate_manifest_map() {
  local map_name="$1" expected_candidate
  wsr_generation_require_exact_map "$map_name" WSR_GENERATION_MANIFEST_FIELDS || return 1
  local -n manifest_ref="$map_name"
  if [[ "${manifest_ref[schema_version]}" != "$WSR_GENERATION_MANIFEST_SCHEMA_VERSION" ||
        "${manifest_ref[project]}" != "$WSR_GENERATION_PROJECT" ||
        "${manifest_ref[volume_driver]}" != "local" ||
        "${manifest_ref[postgres_image_reference]}" != "postgres:17-alpine" ||
        "${manifest_ref[authentication_contract]}" != "production-password-file-scram-sha-256" ||
        ! "${manifest_ref[git_sha]}" =~ ^[0-9a-f]{40}$ ||
        ! "${manifest_ref[postgres_image_id]}" =~ ^sha256:[0-9a-f]{64}$ ]] ||
     ! wsr_generation_id_valid "${manifest_ref[generation_id]}" ||
     ! wsr_generation_volume_matches_id "${manifest_ref[generation_id]}" "${manifest_ref[postgres_volume_name]}" ||
     ! wsr_generation_utc_valid "${manifest_ref[volume_created_utc]}" ||
     ! wsr_generation_nonzero_sha256_valid "${manifest_ref[volume_labels_sha256]}" ||
     ! wsr_generation_utc_valid "${manifest_ref[created_utc]}" ||
     ! wsr_generation_utc_valid "${manifest_ref[sealed_utc]}" ||
     [[ "${manifest_ref[postgres_image_revision]}" != "unavailable" &&
        ! "${manifest_ref[postgres_image_revision]}" =~ ^[0-9a-f]{40}$ ]]; then
    wsr_generation_fail "generation-manifest-value-invalid" "The generation manifest v1 value contract is invalid."
    return 1
  fi
  if [[ "${manifest_ref[created_utc]}" < "${manifest_ref[volume_created_utc]}" ||
        "${manifest_ref[sealed_utc]}" < "${manifest_ref[created_utc]}" ]]; then
    wsr_generation_fail "generation-manifest-time-invalid" "Generation timestamps are not ordered."
    return 1
  fi
  case "${manifest_ref[generation_kind]}" in
    legacy-import)
      [[ "${manifest_ref[generation_id]}" == "$WSR_GENERATION_LEGACY_ID" &&
         "${manifest_ref[source_backup_id]}" == "unavailable" &&
         "${manifest_ref[source_backup_manifest_sha256]}" == "unavailable" &&
         "${manifest_ref[source_archive_sha256]}" == "unavailable" &&
         "${manifest_ref[source_restore_evidence_id]}" == "unavailable" &&
         "${manifest_ref[source_restore_manifest_sha256]}" == "unavailable" &&
         "${manifest_ref[source_database_evidence_sha256]}" == "unavailable" &&
         "${manifest_ref[promotion_plan_sha256]}" == "unavailable" &&
         "${manifest_ref[state]}" == "observed-active-at-import" ]] || {
        wsr_generation_fail "legacy-generation-evidence-invalid" "Legacy import must record observed-active-at-import and preserve missing evidence as unavailable."
        return 1
      }
      ;;
    restored-candidate)
      expected_candidate="$(wsr_generation_candidate_id_for_sources \
        "${manifest_ref[source_backup_id]}" \
        "${manifest_ref[source_restore_evidence_id]}")" &&
        [[ "${manifest_ref[generation_id]}" == "$expected_candidate" ]] &&
        wsr_generation_managed_id_valid "${manifest_ref[generation_id]}" &&
        wsr_generation_backup_id_valid "${manifest_ref[source_backup_id]}" &&
        wsr_generation_backup_id_valid "${manifest_ref[source_restore_evidence_id]}" &&
        wsr_generation_nonzero_sha256_valid "${manifest_ref[source_backup_manifest_sha256]}" &&
        wsr_generation_nonzero_sha256_valid "${manifest_ref[source_archive_sha256]}" &&
        wsr_generation_nonzero_sha256_valid "${manifest_ref[source_restore_manifest_sha256]}" &&
        wsr_generation_nonzero_sha256_valid "${manifest_ref[source_database_evidence_sha256]}" &&
        wsr_generation_nonzero_sha256_valid "${manifest_ref[promotion_plan_sha256]}" &&
        [[ "${manifest_ref[state]}" == "sealed-offline" ]] || {
          wsr_generation_fail "candidate-generation-evidence-invalid" "A candidate requires every exact backup, restore, and plan identity and must be sealed offline."
          return 1
        }
      ;;
    *) return 1 ;;
  esac
}

wsr_generation_render_manifest_v1() {
  wsr_generation_validate_manifest_map "$1" || return 1
  wsr_generation_render_exact_map "$1" WSR_GENERATION_MANIFEST_FIELDS
}

wsr_generation_parse_manifest_v1() {
  wsr_generation_parse_ordered_file "$1" "${2:-WSR_GENERATION_MANIFEST}" \
    WSR_GENERATION_MANIFEST_FIELDS wsr_generation_validate_manifest_map \
    wsr_generation_render_manifest_v1
}

wsr_generation_validate_selector_manifest_relationship() {
  local selector_name="$1" manifest_name="$2" manifest_sha="$3"
  wsr_generation_validate_selector_map "$selector_name" || return 1
  wsr_generation_validate_manifest_map "$manifest_name" || return 1
  local -n selector_ref="$selector_name"
  local -n manifest_ref="$manifest_name"
  if ! wsr_generation_nonzero_sha256_valid "$manifest_sha" ||
     [[ "${selector_ref[active_generation_id]}" != "${manifest_ref[generation_id]}" ||
        "${selector_ref[active_generation_manifest_sha256]}" != "$manifest_sha" ||
        "${selector_ref[active_volume_name]}" != "${manifest_ref[postgres_volume_name]}" ||
        "${selector_ref[written_utc]}" < "${manifest_ref[sealed_utc]}" ]]; then
    wsr_generation_fail "selector-manifest-relationship-mismatch" "Selector and immutable generation manifest do not bind one active generation."
    return 1
  fi
}

wsr_generation_validate_backup_binding_map() {
  local map_name="$1"
  wsr_generation_require_exact_map "$map_name" WSR_GENERATION_BACKUP_BINDING_FIELDS || return 1
  local -n binding_ref="$map_name"
  if [[ "${binding_ref[schema_version]}" != "$WSR_GENERATION_BACKUP_BINDING_SCHEMA_VERSION" ||
        "${binding_ref[source_generation_contract_version]}" != "$WSR_GENERATION_MANIFEST_SCHEMA_VERSION" ||
        "${binding_ref[active_selector_schema_version]}" != "$WSR_GENERATION_SELECTOR_SCHEMA_VERSION" ||
        "${binding_ref[capture_lock_contract_version]}" != "$WSR_GENERATION_LOCK_CONTRACT_VERSION" ]] ||
     ! wsr_generation_backup_id_valid "${binding_ref[backup_id]}" ||
     ! wsr_generation_id_valid "${binding_ref[source_generation_id]}" ||
     ! wsr_generation_nonzero_sha256_valid "${binding_ref[source_generation_manifest_sha256]}" ||
     ! wsr_generation_volume_matches_id "${binding_ref[source_generation_id]}" "${binding_ref[source_volume_name]}" ||
     ! wsr_generation_utc_valid "${binding_ref[source_volume_created_utc]}" ||
     ! wsr_generation_nonzero_sha256_valid "${binding_ref[source_volume_labels_sha256]}" ||
     ! wsr_generation_revision_valid "${binding_ref[active_selector_revision]}" ||
     ! wsr_generation_nonzero_sha256_valid "${binding_ref[active_selector_sha256]}" ||
     [[ ! "${binding_ref[source_generation_kind]}" =~ ^(legacy-import|restored-candidate)$ ]]; then
    wsr_generation_fail "backup-binding-value-invalid" "The backup generation-binding v2 value contract is invalid."
    return 1
  fi
  case "${binding_ref[source_generation_kind]}" in
    legacy-import)
      [[ "${binding_ref[source_generation_id]}" == "$WSR_GENERATION_LEGACY_ID" ]] || {
        wsr_generation_fail "backup-binding-kind-mismatch" "Legacy binding kind requires the exact legacy generation."
        return 1
      }
      ;;
    restored-candidate)
      wsr_generation_managed_id_valid "${binding_ref[source_generation_id]}" || {
        wsr_generation_fail "backup-binding-kind-mismatch" "Candidate binding kind requires a managed generation."
        return 1
      }
      ;;
  esac
}

wsr_generation_render_backup_binding_v2() {
  wsr_generation_validate_backup_binding_map "$1" || return 1
  wsr_generation_render_exact_map "$1" WSR_GENERATION_BACKUP_BINDING_FIELDS
}

wsr_generation_parse_backup_binding_v2() {
  wsr_generation_parse_ordered_file "$1" "${2:-WSR_GENERATION_BACKUP_BINDING}" \
    WSR_GENERATION_BACKUP_BINDING_FIELDS wsr_generation_validate_backup_binding_map \
    wsr_generation_render_backup_binding_v2
}

wsr_generation_validate_backup_binding_relationship() {
  local binding_name="$1" selector_name="$2" manifest_name="$3" selector_sha="$4" manifest_sha="$5"
  wsr_generation_validate_backup_binding_map "$binding_name" || return 1
  wsr_generation_validate_selector_manifest_relationship \
    "$selector_name" "$manifest_name" "$manifest_sha" || return 1
  local -n binding_ref="$binding_name"
  local -n selector_ref="$selector_name"
  local -n manifest_ref="$manifest_name"
  if ! wsr_generation_nonzero_sha256_valid "$selector_sha" ||
     [[ "${binding_ref[source_generation_id]}" != "${manifest_ref[generation_id]}" ||
        "${binding_ref[source_generation_kind]}" != "${manifest_ref[generation_kind]}" ||
        "${binding_ref[source_generation_manifest_sha256]}" != "$manifest_sha" ||
        "${binding_ref[source_volume_name]}" != "${manifest_ref[postgres_volume_name]}" ||
        "${binding_ref[source_volume_created_utc]}" != "${manifest_ref[volume_created_utc]}" ||
        "${binding_ref[source_volume_labels_sha256]}" != "${manifest_ref[volume_labels_sha256]}" ||
        "${binding_ref[active_selector_revision]}" != "${selector_ref[revision]}" ||
        "${binding_ref[active_selector_sha256]}" != "$selector_sha" ]]; then
    wsr_generation_fail "backup-binding-relationship-mismatch" "Backup, selector, and generation manifest do not bind one source."
    return 1
  fi
}

# Exact ADR-049 business-state graph. No function performs a transition.
wsr_generation_next_state() {
  case "$1|$2" in
    steady\|begin-candidate-preparation) printf 'candidate-preparing\n' ;;
    candidate-preparing\|seal-candidate-offline) printf 'candidate-sealed-offline\n' ;;
    candidate-preparing\|abandon-candidate-after-review) printf 'steady\n' ;;
    candidate-sealed-offline\|record-explicit-approval) printf 'approval-recorded\n' ;;
    candidate-sealed-offline\|abort-before-downtime) printf 'steady\n' ;;
    approval-recorded\|persist-quiesce-intent) printf 'quiesce-intent\n' ;;
    approval-recorded\|abort-before-downtime) printf 'steady\n' ;;
    quiesce-intent\|stop-source) printf 'source-stopped\n' ;;
    quiesce-intent\|abort-before-downtime) printf 'steady\n' ;;
    source-stopped\|persist-selector-switch-intent) printf 'selector-switch-intent\n' ;;
    selector-switch-intent\|start-target) printf 'target-starting\n' ;;
    target-starting\|verify-target-health) printf 'target-health-verified\n' ;;
    target-health-verified\|begin-probation) printf 'probation\n' ;;
    probation\|finalize) printf 'finalized\n' ;;
    source-stopped\|persist-rollback-intent) printf 'rollback-intent\n' ;;
    selector-switch-intent\|persist-rollback-intent) printf 'rollback-intent\n' ;;
    target-starting\|persist-rollback-intent) printf 'rollback-intent\n' ;;
    target-health-verified\|persist-rollback-intent) printf 'rollback-intent\n' ;;
    probation\|persist-rollback-intent) printf 'rollback-intent\n' ;;
    rollback-intent\|stop-target) printf 'target-stopped-for-rollback\n' ;;
    target-stopped-for-rollback\|restore-source-selector) printf 'source-selector-restored\n' ;;
    source-selector-restored\|start-source) printf 'source-restarting\n' ;;
    source-restarting\|complete-rollback) printf 'rolled-back\n' ;;
    *) return 1 ;;
  esac
}

wsr_generation_recovery_directive() {
  case "$1" in
    steady|finalized|rolled-back) printf 'stable-no-recovery\n' ;;
    candidate-preparing) printf 'source-authoritative-manual-candidate-review\n' ;;
    candidate-sealed-offline|approval-recorded|quiesce-intent)
      printf 'source-authoritative-abort-before-downtime\n' ;;
    source-stopped|selector-switch-intent|target-starting|target-health-verified)
      printf 'operator-recovery-required-no-auto-selection\n' ;;
    probation) printf 'operator-choice-exact-target-or-explicit-rollback\n' ;;
    rollback-intent|target-stopped-for-rollback|source-selector-restored|source-restarting)
      printf 'operator-recovery-required-continue-exact-rollback\n' ;;
    *) return 1 ;;
  esac
}

wsr_generation_pending_intent_directive() {
  case "$1" in
    steady|candidate-preparing|candidate-sealed-offline|approval-recorded|quiesce-intent)
      printf 'source-authoritative-pending-intent-operator-review\n' ;;
    source-stopped|selector-switch-intent|target-starting|target-health-verified|probation)
      printf 'operator-recovery-required-no-auto-selection\n' ;;
    rollback-intent|target-stopped-for-rollback|source-selector-restored|source-restarting)
      printf 'operator-recovery-required-continue-exact-rollback\n' ;;
    *) return 1 ;;
  esac
}

wsr_generation_validate_journal_record_map() {
  local map_name="$1" expected_after before_number after_number
  wsr_generation_require_exact_map "$map_name" WSR_GENERATION_JOURNAL_FIELDS || return 1
  local -n record_ref="$map_name"
  expected_after="$(wsr_generation_next_state "${record_ref[state_before]}" "${record_ref[event]}")" || {
    wsr_generation_fail "journal-transition-invalid" "The journal contains an unknown, skipped, or replayed transition."
    return 1
  }
  if [[ "${record_ref[schema_version]}" != "$WSR_GENERATION_JOURNAL_SCHEMA_VERSION" ||
        ! "${record_ref[record_kind]}" =~ ^(intent|completion)$ ||
        "$expected_after" != "${record_ref[state_after]}" ]] ||
     ! wsr_generation_uuid_valid "${record_ref[transition_uuid]}" ||
     ! wsr_generation_uuid_valid "${record_ref[operation_uuid]}" ||
     ! wsr_generation_revision_valid "${record_ref[record_sequence]}" ||
     ! wsr_generation_nonzero_sha256_valid "${record_ref[plan_sha256]}" ||
     ! wsr_generation_id_valid "${record_ref[source_generation_id]}" ||
     ! wsr_generation_nonzero_sha256_valid "${record_ref[source_generation_manifest_sha256]}" ||
     ! wsr_generation_volume_matches_id "${record_ref[source_generation_id]}" "${record_ref[source_volume_name]}" ||
     ! wsr_generation_managed_id_valid "${record_ref[target_generation_id]}" ||
     ! wsr_generation_nonzero_sha256_valid "${record_ref[target_generation_manifest_sha256]}" ||
     ! wsr_generation_volume_matches_id "${record_ref[target_generation_id]}" "${record_ref[target_volume_name]}" ||
     ! wsr_generation_revision_valid "${record_ref[selector_before_revision]}" ||
     ! wsr_generation_nonzero_sha256_valid "${record_ref[selector_before_sha256]}" ||
     ! wsr_generation_revision_valid "${record_ref[selector_after_revision]}" ||
     ! wsr_generation_nonzero_sha256_valid "${record_ref[selector_after_sha256]}" ||
     ! wsr_generation_sha256_valid "${record_ref[previous_record_sha256]}" ||
     ! wsr_generation_utc_valid "${record_ref[written_utc]}" ||
     [[ "${record_ref[source_generation_id]}" == "${record_ref[target_generation_id]}" ]]; then
    wsr_generation_fail "journal-record-value-invalid" "The journal record v1 value contract is invalid."
    return 1
  fi
  before_number=$((10#${record_ref[selector_before_revision]}))
  after_number=$((10#${record_ref[selector_after_revision]}))
  if [[ "${record_ref[event]}" =~ ^(start-target|restore-source-selector)$ ]]; then
    if ((after_number != before_number + 1)) ||
       [[ "${record_ref[selector_after_sha256]}" == "${record_ref[selector_before_sha256]}" ]]; then
      wsr_generation_fail "journal-selector-evidence-invalid" "A selector-changing event requires exactly one new revision and digest."
      return 1
    fi
  elif ((after_number != before_number)) ||
       [[ "${record_ref[selector_after_sha256]}" != "${record_ref[selector_before_sha256]}" ]]; then
    wsr_generation_fail "journal-selector-evidence-invalid" "Selector revision and digest evidence are inconsistent."
    return 1
  fi
}

wsr_generation_render_journal_record_v1() {
  wsr_generation_validate_journal_record_map "$1" || return 1
  wsr_generation_render_exact_map "$1" WSR_GENERATION_JOURNAL_FIELDS
}

wsr_generation_parse_journal_record_v1() {
  wsr_generation_parse_ordered_file "$1" "${2:-WSR_GENERATION_JOURNAL_RECORD}" \
    WSR_GENERATION_JOURNAL_FIELDS wsr_generation_validate_journal_record_map \
    wsr_generation_render_journal_record_v1
}

wsr_generation_journal_pair_matches() {
  local intent_name="$1" completion_name="$2" field
  wsr_generation_associative_map_name_valid "$intent_name" &&
    wsr_generation_associative_map_name_valid "$completion_name" || return 1
  local -n intent_ref="$intent_name"
  local -n completion_ref="$completion_name"
  for field in "${WSR_GENERATION_JOURNAL_FIELDS[@]}"; do
    case "$field" in record_sequence|record_kind|previous_record_sha256|written_utc) continue ;; esac
    [[ "${intent_ref[$field]}" == "${completion_ref[$field]}" ]] || return 1
  done
  [[ "${intent_ref[record_kind]}" == "intent" && "${completion_ref[record_kind]}" == "completion" ]]
}

wsr_generation_validate_journal_chain() {
  local path actual_sha expected_previous="$WSR_GENERATION_ZERO_SHA256"
  local expected_sequence current_state="steady" index=0 pending=false
  local previous_written_utc="" current_selector_revision="" current_selector_sha=""
  local transition_uuid="" plan_sha="" source_id="" source_manifest="" source_volume=""
  local target_id="" target_manifest="" target_volume=""
  local -A record=() intent=() seen_operations=()
  WSR_GENERATION_JOURNAL_STATE="steady"
  WSR_GENERATION_JOURNAL_STATUS="empty"
  WSR_GENERATION_JOURNAL_RECOVERY_DIRECTIVE="stable-no-recovery"
  WSR_GENERATION_JOURNAL_LAST_RECORD_SHA256=""
  (($# > 0)) || return 0
  for path in "$@"; do
    index=$((index + 1))
    printf -v expected_sequence '%016d' "$index"
    wsr_generation_parse_journal_record_v1 "$path" record || return 1
    if [[ "${record[record_sequence]}" != "$expected_sequence" ||
          "${record[previous_record_sha256]}" != "$expected_previous" ]]; then
      wsr_generation_fail "journal-chain-discontinuous" "Journal sequence or previous hash is discontinuous."
      return 1
    fi
    if [[ -n "$previous_written_utc" && "${record[written_utc]}" < "$previous_written_utc" ]]; then
      wsr_generation_fail "journal-time-regressed" "Journal observation time moved backwards."
      return 1
    fi
    actual_sha="$(sha256sum -- "$path" | awk '{print $1}')"
    if ((index == 1)); then
      transition_uuid="${record[transition_uuid]}"
      plan_sha="${record[plan_sha256]}"
      source_id="${record[source_generation_id]}"
      source_manifest="${record[source_generation_manifest_sha256]}"
      source_volume="${record[source_volume_name]}"
      target_id="${record[target_generation_id]}"
      target_manifest="${record[target_generation_manifest_sha256]}"
      target_volume="${record[target_volume_name]}"
    elif [[ "${record[transition_uuid]}" != "$transition_uuid" ||
            "${record[plan_sha256]}" != "$plan_sha" ||
            "${record[source_generation_id]}" != "$source_id" ||
            "${record[source_generation_manifest_sha256]}" != "$source_manifest" ||
            "${record[source_volume_name]}" != "$source_volume" ||
            "${record[target_generation_id]}" != "$target_id" ||
            "${record[target_generation_manifest_sha256]}" != "$target_manifest" ||
            "${record[target_volume_name]}" != "$target_volume" ]]; then
      wsr_generation_fail "journal-chain-identity-drift" "Journal transition, plan, source, or target identity drifted."
      return 1
    fi
    if [[ "${record[record_kind]}" == "intent" ]]; then
      if [[ "$pending" == true || "${record[state_before]}" != "$current_state" ||
            "$current_state" =~ ^(finalized|rolled-back)$ ]]; then
        wsr_generation_fail "journal-intent-invalid" "The journal contains concurrent, replayed, or terminal intent."
        return 1
      fi
      if [[ -n "${seen_operations[${record[operation_uuid]}]+present}" ]]; then
        wsr_generation_fail "journal-operation-replayed" "A journal operation UUID was reused."
        return 1
      fi
      if [[ -n "$current_selector_revision" &&
            ( "${record[selector_before_revision]}" != "$current_selector_revision" ||
              "${record[selector_before_sha256]}" != "$current_selector_sha" ) ]]; then
        wsr_generation_fail "journal-selector-chain-discontinuous" "Selector evidence drifted between completed operations."
        return 1
      fi
      seen_operations["${record[operation_uuid]}"]=1
      pending=true
      intent=()
      for field in "${WSR_GENERATION_JOURNAL_FIELDS[@]}"; do intent["$field"]="${record[$field]}"; done
    else
      if [[ "$pending" != true ]] || ! wsr_generation_journal_pair_matches intent record; then
        wsr_generation_fail "journal-completion-invalid" "A completion has no exact preceding intent."
        return 1
      fi
      pending=false
      current_state="${record[state_after]}"
      current_selector_revision="${record[selector_after_revision]}"
      current_selector_sha="${record[selector_after_sha256]}"
    fi
    expected_previous="$actual_sha"
    previous_written_utc="${record[written_utc]}"
  done
  WSR_GENERATION_JOURNAL_STATE="$current_state"
  WSR_GENERATION_JOURNAL_LAST_RECORD_SHA256="$expected_previous"
  if [[ "$pending" == true ]]; then
    WSR_GENERATION_JOURNAL_STATUS="pending-intent"
    WSR_GENERATION_JOURNAL_RECOVERY_DIRECTIVE="$(wsr_generation_pending_intent_directive "${intent[state_before]}")" || return 1
  else
    WSR_GENERATION_JOURNAL_STATUS="complete"
    WSR_GENERATION_JOURNAL_RECOVERY_DIRECTIVE="$(wsr_generation_recovery_directive "$current_state")" || return 1
  fi
}

wsr_generation_secure_directory() {
  local path="$1" expected_uid="$2" resolved owner mode
  [[ -d "$path" && ! -L "$path" ]] || return 1
  resolved="$(realpath -e -- "$path" 2>/dev/null)" || return 1
  owner="$(stat -c '%u' -- "$path" 2>/dev/null)" || return 1
  mode="$(stat -c '%a' -- "$path" 2>/dev/null)" || return 1
  [[ "$resolved" == "$path" && "$owner" == "$expected_uid" && "$mode" == "700" ]]
}

wsr_generation_secure_lock_file() {
  local path="$1" expected_uid="$2" resolved owner mode links
  [[ -f "$path" && ! -L "$path" ]] || return 1
  resolved="$(realpath -e -- "$path" 2>/dev/null)" || return 1
  owner="$(stat -c '%u' -- "$path" 2>/dev/null)" || return 1
  mode="$(stat -c '%a' -- "$path" 2>/dev/null)" || return 1
  links="$(stat -c '%h' -- "$path" 2>/dev/null)" || return 1
  [[ "$resolved" == "$path" && "$owner" == "$expected_uid" && "$mode" == "600" && "$links" == "1" ]]
}

wsr_generation_secure_document_file() {
  local path="$1" expected_uid="$2" resolved owner mode links
  [[ -f "$path" && ! -L "$path" ]] || return 1
  resolved="$(realpath -e -- "$path" 2>/dev/null)" || return 1
  owner="$(stat -c '%u' -- "$path" 2>/dev/null)" || return 1
  mode="$(stat -c '%a' -- "$path" 2>/dev/null)" || return 1
  links="$(stat -c '%h' -- "$path" 2>/dev/null)" || return 1
  [[ "$resolved" == "$path" && "$owner" == "$expected_uid" && "$mode" == "400" && "$links" == "1" ]]
}

wsr_generation_close_lock_fd() {
  if [[ "$WSR_GENERATION_OPERATION_LOCK_FD" =~ ^[0-9]+$ ]]; then
    flock -u "$WSR_GENERATION_OPERATION_LOCK_FD" 2>/dev/null || true
    exec {WSR_GENERATION_OPERATION_LOCK_FD}>&-
  fi
  WSR_GENERATION_OPERATION_LOCK_FD=""
  WSR_GENERATION_OPERATION_LOCK_MODE=""
  WSR_GENERATION_OPERATION_LOCK_OPEN_PATH=""
  WSR_GENERATION_OPERATION_LOCK_IDENTITY=""
  WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID=""
}

wsr_generation_acquire_operation_lock_at() {
  local path="$1" expected_uid="$2" mode="$3" parent path_identity fd_identity fd=""
  if [[ -n "$WSR_GENERATION_OPERATION_LOCK_FD" || ! "$path" =~ ^/.*/operation[.]lock$ ||
        ! "$expected_uid" =~ ^[0-9]+$ || ! "$mode" =~ ^(shared|exclusive)$ ]]; then
    wsr_generation_fail "operation-lock-request-invalid" "The operation lock request is invalid."
    return 1
  fi
  parent="${path%/*}"
  if ! wsr_generation_secure_directory "$parent" "$expected_uid" ||
     ! wsr_generation_secure_lock_file "$path" "$expected_uid"; then
    wsr_generation_fail "operation-lock-metadata-invalid" "The lock must be preprovisioned in a secure owned directory."
    return 1
  fi
  path_identity="$(stat -Lc '%d:%i' -- "$path")" || return 1
  exec {fd}<>"$path" || return 1
  fd_identity="$(stat -Lc '%d:%i' -- "/proc/$$/fd/$fd" 2>/dev/null)" || true
  if [[ "$fd_identity" != "$path_identity" ]]; then
    exec {fd}>&-
    wsr_generation_fail "operation-lock-inode-mismatch" "The lock path and opened FD are different inodes."
    return 1
  fi
  if [[ "$mode" == "shared" ]]; then
    flock -n -s "$fd" || { exec {fd}>&-; wsr_generation_fail "operation-lock-busy" "The operation lock is busy."; return 1; }
  else
    flock -n -x "$fd" || { exec {fd}>&-; wsr_generation_fail "operation-lock-busy" "The operation lock is busy."; return 1; }
  fi
  if ! wsr_generation_secure_lock_file "$path" "$expected_uid" ||
     [[ "$(stat -Lc '%d:%i' -- "$path" 2>/dev/null)" != "$path_identity" ||
        "$(stat -Lc '%d:%i' -- "/proc/$$/fd/$fd" 2>/dev/null)" != "$path_identity" ]]; then
    flock -u "$fd" 2>/dev/null || true
    exec {fd}>&-
    wsr_generation_fail "operation-lock-changed" "The lock changed across validation, open, and flock."
    return 1
  fi
  WSR_GENERATION_OPERATION_LOCK_FD="$fd"
  WSR_GENERATION_OPERATION_LOCK_MODE="$mode"
  WSR_GENERATION_OPERATION_LOCK_OPEN_PATH="$path"
  WSR_GENERATION_OPERATION_LOCK_IDENTITY="$path_identity"
  WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID="$expected_uid"
}

wsr_generation_acquire_operation_lock() {
  local mode="$1"
  if ((EUID != 0)) || ! wsr_generation_secure_directory "$WSR_GENERATION_CONTROL_ROOT" 0; then
    wsr_generation_fail "generation-control-root-invalid" "The fixed control root must be preprovisioned root:root mode 0700."
    return 1
  fi
  wsr_generation_acquire_operation_lock_at "$WSR_GENERATION_OPERATION_LOCK_PATH" 0 "$mode"
}

wsr_generation_require_operation_lock() {
  local required_mode="$1" path_identity fd_identity
  if [[ ! "$required_mode" =~ ^(shared|exclusive)$ ||
        ! "$WSR_GENERATION_OPERATION_LOCK_FD" =~ ^[0-9]+$ ||
        ! "$WSR_GENERATION_OPERATION_LOCK_MODE" =~ ^(shared|exclusive)$ ]] ||
     [[ "$required_mode" == "exclusive" && "$WSR_GENERATION_OPERATION_LOCK_MODE" != "exclusive" ]]; then
    wsr_generation_fail "operation-lock-required" "The required operation lock is not held."
    return 1
  fi
  path_identity="$(stat -Lc '%d:%i' -- "$WSR_GENERATION_OPERATION_LOCK_OPEN_PATH" 2>/dev/null)" || true
  fd_identity="$(stat -Lc '%d:%i' -- "/proc/$$/fd/$WSR_GENERATION_OPERATION_LOCK_FD" 2>/dev/null)" || true
  if ! wsr_generation_secure_lock_file "$WSR_GENERATION_OPERATION_LOCK_OPEN_PATH" "$WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID" ||
     [[ "$path_identity" != "$WSR_GENERATION_OPERATION_LOCK_IDENTITY" ||
        "$fd_identity" != "$WSR_GENERATION_OPERATION_LOCK_IDENTITY" ]]; then
    wsr_generation_fail "operation-lock-lost" "The held operation.lock inode no longer matches its path."
    return 1
  fi
}

# Writers are low-level primitives only. No production entry point calls them.
# They require an already-held exclusive lock, write in the destination parent,
# fsync the file, rename on the same filesystem, fsync the parent, and reread.
wsr_generation_publish_text_at() {
  local destination="$1" text="$2" replace="$3" parent base lock_parent
  local parent_resolved lock_parent_resolved temporary stage_id parent_device stage_device
  local expected_sha staged_sha actual_sha
  wsr_generation_require_operation_lock exclusive || return 1
  [[ "$destination" =~ ^/.*/[A-Za-z0-9][A-Za-z0-9._-]*$ && "$replace" =~ ^(replace|no-clobber)$ ]] || return 1
  parent="${destination%/*}"
  base="${destination##*/}"
  wsr_generation_secure_directory "$parent" "$WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID" || return 1
  lock_parent="${WSR_GENERATION_OPERATION_LOCK_OPEN_PATH%/*}"
  parent_resolved="$(realpath -e -- "$parent")" || return 1
  lock_parent_resolved="$(realpath -e -- "$lock_parent")" || return 1
  if [[ "$parent_resolved" != "$lock_parent_resolved" &&
        "$parent_resolved" != "$lock_parent_resolved/"* ]]; then
    wsr_generation_fail "document-destination-outside-control-root" "Generation documents must remain below the held lock's control root."
    return 1
  fi
  if [[ "$replace" == "no-clobber" && (-e "$destination" || -L "$destination") ]]; then return 1; fi
  if [[ "$replace" == "replace" && (-e "$destination" || -L "$destination") ]]; then
    wsr_generation_secure_document_file "$destination" "$WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID" || return 1
  fi
  temporary="$(mktemp -- "$parent/.${base}.tmp.XXXXXXXX")" || return 1
  chmod 0600 -- "$temporary" || { rm -f -- "$temporary"; return 1; }
  if ! printf '%s\n' "$text" > "$temporary" || ! chmod 0400 -- "$temporary"; then
    rm -f -- "$temporary"
    return 1
  fi
  expected_sha="$(printf '%s\n' "$text" | sha256sum | awk '{print $1}')"
  staged_sha="$(sha256sum -- "$temporary" | awk '{print $1}')"
  stage_id="$(stat -Lc '%d:%i' -- "$temporary")"
  parent_device="$(stat -Lc '%d' -- "$parent")"
  stage_device="$(stat -Lc '%d' -- "$temporary")"
  if ! wsr_generation_secure_document_file "$temporary" "$WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID" ||
     [[ "$staged_sha" != "$expected_sha" || "$stage_device" != "$parent_device" ]] ||
     ! sync -- "$temporary"; then
    rm -f -- "$temporary"
    return 1
  fi
  if [[ "$replace" == "replace" ]]; then
    if ! mv --force --no-target-directory -- "$temporary" "$destination"; then
      rm -f -- "$temporary"
      return 1
    fi
  else
    if ! mv --no-clobber --no-target-directory -- "$temporary" "$destination"; then
      rm -f -- "$temporary"
      return 1
    fi
    [[ ! -e "$temporary" && ! -L "$temporary" ]] || { rm -f -- "$temporary"; return 1; }
  fi
  wsr_generation_secure_document_file "$destination" "$WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID" || return 1
  [[ "$(stat -Lc '%d:%i' -- "$destination" 2>/dev/null)" == "$stage_id" ]] || return 1
  sync -- "$parent" || return 1
  actual_sha="$(sha256sum -- "$destination" | awk '{print $1}')"
  wsr_generation_secure_document_file "$destination" "$WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID" &&
    [[ "$actual_sha" == "$expected_sha" ]]
}

wsr_generation_replace_selector_v1_at() {
  local text
  text="$(wsr_generation_render_selector_v1 "$2")" || return 1
  wsr_generation_publish_text_at "$1" "$text" replace
}

wsr_generation_publish_manifest_v1_at() {
  local text
  text="$(wsr_generation_render_manifest_v1 "$2")" || return 1
  wsr_generation_publish_text_at "$1" "$text" no-clobber
}

wsr_generation_publish_journal_record_v1_at() {
  local text
  text="$(wsr_generation_render_journal_record_v1 "$2")" || return 1
  wsr_generation_publish_text_at "$1" "$text" no-clobber
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  printf 'ERROR: generation-state.sh is source-only and exposes no operator action.\n' >&2
  exit 64
fi
