#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
state_source="$repo_root/deploy/home-server/generation-state.sh"
compose_wrapper="$repo_root/deploy/home-server/compose-production.sh"
deployment_preflight="$repo_root/deploy/home-server/preflight.sh"
recovery_common="$repo_root/deploy/home-server/recovery-common.sh"
recovery_preflight="$repo_root/deploy/home-server/recovery-preflight.sh"
recovery_production="$repo_root/deploy/home-server/recovery-production.sh"
compose_file="$repo_root/deploy/home-server/compose.yaml"

# shellcheck source=deploy/home-server/generation-state.sh
source "$state_source"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

assert_true() {
  "$@" || fail "expected success: $*"
}

assert_false() {
  if "$@"; then fail "expected rejection: $*"; fi
}

assert_equal() {
  [[ "$1" == "$2" ]] || fail "expected [$2], got [$1]"
}

checks=0
check() { checks=$((checks + 1)); }

temporary_root="$(mktemp -d)"
cleanup() {
  wsr_generation_close_lock_fd
  rm -rf -- "$temporary_root"
}
trap cleanup EXIT

H1="$(printf 'a%.0s' {1..64})"
H2="$(printf 'b%.0s' {1..64})"
H3="$(printf 'c%.0s' {1..64})"
H4="$(printf 'd%.0s' {1..64})"
H5="$(printf 'e%.0s' {1..64})"
GIT_SHA="$(printf 'f%.0s' {1..40})"
IMAGE_ID="sha256:$H5"
TRANSITION_UUID="123e4567-e89b-42d3-a456-426614174000"
OPERATION_UUID="123e4567-e89b-42d3-a456-426614174001"
SECOND_OPERATION_UUID="123e4567-e89b-42d3-a456-426614174002"
BACKUP_ID="20260826T010203Z-Ab12Cd34"
EVIDENCE_ID="20260826T020304Z-Ef56Gh78"
CANDIDATE="$(wsr_generation_candidate_id_for_sources "$BACKUP_ID" "$EVIDENCE_ID")"

declare -A legacy_manifest=(
  [schema_version]="1"
  [project]="$WSR_GENERATION_PROJECT"
  [generation_id]="$WSR_GENERATION_LEGACY_ID"
  [generation_kind]="legacy-import"
  [postgres_volume_name]="$WSR_GENERATION_LEGACY_VOLUME"
  [volume_driver]="local"
  [volume_created_utc]="2026-08-01T00:00:00Z"
  [volume_labels_sha256]="$H1"
  [source_backup_id]="unavailable"
  [source_backup_manifest_sha256]="unavailable"
  [source_archive_sha256]="unavailable"
  [source_restore_evidence_id]="unavailable"
  [source_restore_manifest_sha256]="unavailable"
  [source_database_evidence_sha256]="unavailable"
  [promotion_plan_sha256]="unavailable"
  [git_sha]="$GIT_SHA"
  [postgres_image_reference]="postgres:17-alpine"
  [postgres_image_id]="$IMAGE_ID"
  [postgres_image_revision]="unavailable"
  [authentication_contract]="production-password-file-scram-sha-256"
  [created_utc]="2026-08-26T00:00:00Z"
  [sealed_utc]="2026-08-26T00:00:01Z"
  [state]="observed-active-at-import"
)

declare -A candidate_manifest=(
  [schema_version]="1"
  [project]="$WSR_GENERATION_PROJECT"
  [generation_id]="$CANDIDATE"
  [generation_kind]="restored-candidate"
  [postgres_volume_name]="$CANDIDATE"
  [volume_driver]="local"
  [volume_created_utc]="2026-08-26T02:00:00Z"
  [volume_labels_sha256]="$H2"
  [source_backup_id]="$BACKUP_ID"
  [source_backup_manifest_sha256]="$H1"
  [source_archive_sha256]="$H2"
  [source_restore_evidence_id]="$EVIDENCE_ID"
  [source_restore_manifest_sha256]="$H3"
  [source_database_evidence_sha256]="$H4"
  [promotion_plan_sha256]="$H5"
  [git_sha]="$GIT_SHA"
  [postgres_image_reference]="postgres:17-alpine"
  [postgres_image_id]="$IMAGE_ID"
  [postgres_image_revision]="unavailable"
  [authentication_contract]="production-password-file-scram-sha-256"
  [created_utc]="2026-08-26T02:00:01Z"
  [sealed_utc]="2026-08-26T02:00:02Z"
  [state]="sealed-offline"
)

legacy_text="$(wsr_generation_render_manifest_v1 legacy_manifest)"
candidate_text="$(wsr_generation_render_manifest_v1 candidate_manifest)"
legacy_path="$temporary_root/legacy.manifest"
candidate_path="$temporary_root/candidate.manifest"
printf '%s\n' "$legacy_text" > "$legacy_path"
printf '%s\n' "$candidate_text" > "$candidate_path"
declare -A parsed_legacy=() parsed_candidate=()
assert_true wsr_generation_parse_manifest_v1 "$legacy_path" parsed_legacy
assert_true wsr_generation_parse_manifest_v1 "$candidate_path" parsed_candidate
assert_false wsr_generation_parse_manifest_v1 "$candidate_path" 'invalid-map-name'
declare -a wrong_map_type=()
assert_false wsr_generation_parse_manifest_v1 "$candidate_path" wrong_map_type
assert_false wsr_generation_parse_ordered_file "$candidate_path" parsed_candidate \
  WSR_GENERATION_MANIFEST_FIELDS wsr_generation_validate_manifest_map printf
assert_equal "${parsed_legacy[source_backup_id]}" "unavailable"
assert_equal "${parsed_candidate[source_backup_id]}" "$BACKUP_ID"
check

declare -A legacy_bad=()
for key in "${!legacy_manifest[@]}"; do legacy_bad["$key"]="${legacy_manifest[$key]}"; done
legacy_bad[source_backup_id]="$BACKUP_ID"
assert_false wsr_generation_validate_manifest_map legacy_bad
legacy_bad[source_backup_id]="unavailable"
legacy_bad[state]="sealed-offline"
assert_false wsr_generation_validate_manifest_map legacy_bad
declare -A candidate_bad=()
for key in "${!candidate_manifest[@]}"; do candidate_bad["$key"]="${candidate_manifest[$key]}"; done
candidate_bad[source_database_evidence_sha256]="unavailable"
assert_false wsr_generation_validate_manifest_map candidate_bad
candidate_bad[source_database_evidence_sha256]="$H4"
candidate_bad[generation_id]="wall-street-receipts-generation-20260826t010203z-00000000-20260826t020304z-11111111"
candidate_bad[postgres_volume_name]="${candidate_bad[generation_id]}"
assert_false wsr_generation_validate_manifest_map candidate_bad
check

candidate_manifest_sha="$(sha256sum -- "$candidate_path" | awk '{print $1}')"
declare -A selector=(
  [schema_version]="1"
  [project]="$WSR_GENERATION_PROJECT"
  [revision]="0000000000000002"
  [active_generation_id]="$CANDIDATE"
  [active_generation_manifest_sha256]="$candidate_manifest_sha"
  [active_volume_name]="$CANDIDATE"
  [previous_selector_sha256]="$H1"
  [change_kind]="promotion"
  [transition_uuid]="$TRANSITION_UUID"
  [plan_sha256]="$H5"
  [written_utc]="2026-08-26T02:01:00Z"
)
selector_text="$(wsr_generation_render_selector_v1 selector)"
selector_path="$temporary_root/active.selector"
printf '%s\n' "$selector_text" > "$selector_path"
selector_sha="$(sha256sum -- "$selector_path" | awk '{print $1}')"
declare -A parsed_selector=()
assert_true wsr_generation_parse_selector_v1 "$selector_path" parsed_selector
assert_equal "${parsed_selector[active_generation_id]}" "$CANDIDATE"
assert_true wsr_generation_validate_selector_manifest_relationship \
  parsed_selector parsed_candidate "$candidate_manifest_sha"
parsed_selector[active_generation_manifest_sha256]="$H4"
assert_false wsr_generation_validate_selector_manifest_relationship \
  parsed_selector parsed_candidate "$candidate_manifest_sha"
parsed_selector[active_generation_manifest_sha256]="$candidate_manifest_sha"
parsed_selector[written_utc]="2026-08-26T01:59:59Z"
assert_false wsr_generation_validate_selector_manifest_relationship \
  parsed_selector parsed_candidate "$candidate_manifest_sha"
parsed_selector[written_utc]="2026-08-26T02:01:00Z"
check

declare -A bootstrap_selector=(
  [schema_version]="1"
  [project]="$WSR_GENERATION_PROJECT"
  [revision]="0000000000000001"
  [active_generation_id]="$WSR_GENERATION_LEGACY_ID"
  [active_generation_manifest_sha256]="$H1"
  [active_volume_name]="$WSR_GENERATION_LEGACY_VOLUME"
  [previous_selector_sha256]="$WSR_GENERATION_ZERO_SHA256"
  [change_kind]="legacy-bootstrap"
  [transition_uuid]="bootstrap"
  [plan_sha256]="bootstrap"
  [written_utc]="2026-08-26T00:00:02Z"
)
assert_true wsr_generation_validate_selector_map bootstrap_selector
bootstrap_selector[plan_sha256]="$H1"
assert_false wsr_generation_validate_selector_map bootstrap_selector
bootstrap_selector[plan_sha256]="bootstrap"
bootstrap_selector[change_kind]="promotion"
bootstrap_selector[previous_selector_sha256]="$H1"
bootstrap_selector[transition_uuid]="$TRANSITION_UUID"
bootstrap_selector[plan_sha256]="$H5"
assert_false wsr_generation_validate_selector_map bootstrap_selector
assert_equal "$WSR_GENERATION_FAILURE_REASON" "selector-promotion-target-invalid"
check

declare -A binding=(
  [schema_version]="2"
  [backup_id]="$BACKUP_ID"
  [source_generation_contract_version]="1"
  [source_generation_id]="$CANDIDATE"
  [source_generation_kind]="restored-candidate"
  [source_generation_manifest_sha256]="$candidate_manifest_sha"
  [source_volume_name]="$CANDIDATE"
  [source_volume_created_utc]="2026-08-26T02:00:00Z"
  [source_volume_labels_sha256]="$H2"
  [active_selector_schema_version]="1"
  [active_selector_revision]="0000000000000002"
  [active_selector_sha256]="$selector_sha"
  [capture_lock_contract_version]="1"
)
binding_text="$(wsr_generation_render_backup_binding_v2 binding)"
binding_path="$temporary_root/backup.binding"
printf '%s\n' "$binding_text" > "$binding_path"
declare -A parsed_binding=()
assert_true wsr_generation_parse_backup_binding_v2 "$binding_path" parsed_binding
assert_true wsr_generation_validate_backup_binding_relationship \
  parsed_binding parsed_selector parsed_candidate "$selector_sha" "$candidate_manifest_sha"
parsed_binding[source_volume_labels_sha256]="$H3"
assert_false wsr_generation_validate_backup_binding_relationship \
  parsed_binding parsed_selector parsed_candidate "$selector_sha" "$candidate_manifest_sha"
parsed_binding[source_volume_labels_sha256]="$H2"
parsed_binding[source_generation_kind]="legacy-import"
assert_false wsr_generation_validate_backup_binding_map parsed_binding
check

canonical_reject() {
  local label="$1" path="$2"
  declare -gA rejected_selector=()
  if wsr_generation_parse_selector_v1 "$path" rejected_selector; then
    fail "canonical selector mutation passed: $label"
  fi
  check
}

printf '%s' "$selector_text" > "$temporary_root/no-final-lf"
canonical_reject "no final LF" "$temporary_root/no-final-lf"
printf '\357\273\277%s\n' "$selector_text" > "$temporary_root/bom"
canonical_reject "BOM" "$temporary_root/bom"
while IFS= read -r line; do printf '%s\r\n' "$line"; done <<< "$selector_text" > "$temporary_root/crlf"
canonical_reject "CRLF" "$temporary_root/crlf"
printf '%s\n\n' "$selector_text" > "$temporary_root/blank"
canonical_reject "blank line" "$temporary_root/blank"
printf '%s\nunknown=x\n' "$selector_text" > "$temporary_root/unknown"
canonical_reject "unknown field" "$temporary_root/unknown"
mapfile -t selector_lines <<< "$selector_text"
swapped="${selector_lines[0]}"
selector_lines[0]="${selector_lines[1]}"
selector_lines[1]="$swapped"
printf '%s\n' "${selector_lines[@]}" > "$temporary_root/reordered"
canonical_reject "reordered fields" "$temporary_root/reordered"
printf '%s\n%s\n' "$selector_text" "${selector_lines[1]}" > "$temporary_root/duplicate"
canonical_reject "duplicate field" "$temporary_root/duplicate"

declare -A intent=(
  [schema_version]="1"
  [transition_uuid]="$TRANSITION_UUID"
  [operation_uuid]="$OPERATION_UUID"
  [record_sequence]="0000000000000001"
  [record_kind]="intent"
  [state_before]="steady"
  [event]="begin-candidate-preparation"
  [state_after]="candidate-preparing"
  [plan_sha256]="$H5"
  [source_generation_id]="$WSR_GENERATION_LEGACY_ID"
  [source_generation_manifest_sha256]="$H1"
  [source_volume_name]="$WSR_GENERATION_LEGACY_VOLUME"
  [target_generation_id]="$CANDIDATE"
  [target_generation_manifest_sha256]="$candidate_manifest_sha"
  [target_volume_name]="$CANDIDATE"
  [selector_before_revision]="0000000000000001"
  [selector_before_sha256]="$H2"
  [selector_after_revision]="0000000000000001"
  [selector_after_sha256]="$H2"
  [previous_record_sha256]="$WSR_GENERATION_ZERO_SHA256"
  [written_utc]="2026-08-26T03:00:00Z"
)
intent_path="$temporary_root/0001-intent"
printf '%s\n' "$(wsr_generation_render_journal_record_v1 intent)" > "$intent_path"
intent_sha="$(sha256sum -- "$intent_path" | awk '{print $1}')"
declare -A completion=()
for key in "${!intent[@]}"; do completion["$key"]="${intent[$key]}"; done
completion[record_sequence]="0000000000000002"
completion[record_kind]="completion"
completion[previous_record_sha256]="$intent_sha"
completion[written_utc]="2026-08-26T03:00:01Z"
completion_path="$temporary_root/0002-completion"
printf '%s\n' "$(wsr_generation_render_journal_record_v1 completion)" > "$completion_path"
assert_true wsr_generation_validate_journal_chain "$intent_path"
assert_equal "$WSR_GENERATION_JOURNAL_STATUS" "pending-intent"
assert_equal "$WSR_GENERATION_JOURNAL_RECOVERY_DIRECTIVE" "source-authoritative-pending-intent-operator-review"
assert_true wsr_generation_validate_journal_chain "$intent_path" "$completion_path"
assert_equal "$WSR_GENERATION_JOURNAL_STATUS" "complete"
assert_equal "$WSR_GENERATION_JOURNAL_STATE" "candidate-preparing"
check

completion_sha="$(sha256sum -- "$completion_path" | awk '{print $1}')"
declare -A second_intent=()
for key in "${!intent[@]}"; do second_intent["$key"]="${intent[$key]}"; done
second_intent[operation_uuid]="$SECOND_OPERATION_UUID"
second_intent[record_sequence]="0000000000000003"
second_intent[state_before]="candidate-preparing"
second_intent[event]="seal-candidate-offline"
second_intent[state_after]="candidate-sealed-offline"
second_intent[previous_record_sha256]="$completion_sha"
second_intent[written_utc]="2026-08-26T03:00:02Z"
second_intent_path="$temporary_root/0003-intent"
printf '%s\n' "$(wsr_generation_render_journal_record_v1 second_intent)" > "$second_intent_path"
assert_true wsr_generation_validate_journal_chain "$intent_path" "$completion_path" "$second_intent_path"
assert_equal "$WSR_GENERATION_JOURNAL_STATUS" "pending-intent"
second_intent[record_sequence]="0000000000000004"
gap_path="$temporary_root/gap"
printf '%s\n' "$(wsr_generation_render_journal_record_v1 second_intent)" > "$gap_path"
assert_false wsr_generation_validate_journal_chain "$intent_path" "$completion_path" "$gap_path"
second_intent[record_sequence]="0000000000000003"
second_intent[target_generation_manifest_sha256]="$H4"
drift_path="$temporary_root/drift"
printf '%s\n' "$(wsr_generation_render_journal_record_v1 second_intent)" > "$drift_path"
assert_false wsr_generation_validate_journal_chain "$intent_path" "$completion_path" "$drift_path"
second_intent[target_generation_manifest_sha256]="$candidate_manifest_sha"
second_intent[operation_uuid]="$OPERATION_UUID"
replay_path="$temporary_root/replay"
printf '%s\n' "$(wsr_generation_render_journal_record_v1 second_intent)" > "$replay_path"
assert_false wsr_generation_validate_journal_chain "$intent_path" "$completion_path" "$replay_path"
second_intent[operation_uuid]="$SECOND_OPERATION_UUID"
second_intent[selector_before_sha256]="$H3"
second_intent[selector_after_sha256]="$H3"
selector_drift_path="$temporary_root/selector-drift"
printf '%s\n' "$(wsr_generation_render_journal_record_v1 second_intent)" > "$selector_drift_path"
assert_false wsr_generation_validate_journal_chain "$intent_path" "$completion_path" "$selector_drift_path"
second_intent[selector_before_sha256]="$H2"
second_intent[selector_after_sha256]="$H2"
second_intent[written_utc]="2026-08-26T02:59:59Z"
time_drift_path="$temporary_root/time-drift"
printf '%s\n' "$(wsr_generation_render_journal_record_v1 second_intent)" > "$time_drift_path"
assert_false wsr_generation_validate_journal_chain "$intent_path" "$completion_path" "$time_drift_path"
check

declare -A selector_change_record=()
for key in "${!intent[@]}"; do selector_change_record["$key"]="${intent[$key]}"; done
selector_change_record[state_before]="selector-switch-intent"
selector_change_record[event]="start-target"
selector_change_record[state_after]="target-starting"
selector_change_record[selector_after_revision]="0000000000000002"
selector_change_record[selector_after_sha256]="$H3"
assert_true wsr_generation_validate_journal_record_map selector_change_record
selector_change_record[selector_after_revision]="0000000000000001"
assert_false wsr_generation_validate_journal_record_map selector_change_record
selector_change_record[selector_after_revision]="0000000000000002"
selector_change_record[event]="verify-target-health"
selector_change_record[state_before]="target-starting"
selector_change_record[state_after]="target-health-verified"
assert_false wsr_generation_validate_journal_record_map selector_change_record
selector_change_record[event]="start-target"
selector_change_record[state_before]="selector-switch-intent"
selector_change_record[state_after]="target-starting"
selector_change_record[source_generation_id]="$CANDIDATE"
selector_change_record[source_volume_name]="$CANDIDATE"
assert_false wsr_generation_validate_journal_record_map selector_change_record
check

states=(steady candidate-preparing candidate-sealed-offline approval-recorded quiesce-intent source-stopped selector-switch-intent target-starting target-health-verified probation finalized rollback-intent target-stopped-for-rollback source-selector-restored source-restarting rolled-back)
events=(begin-candidate-preparation seal-candidate-offline abandon-candidate-after-review record-explicit-approval abort-before-downtime persist-quiesce-intent stop-source persist-selector-switch-intent start-target verify-target-health begin-probation finalize persist-rollback-intent stop-target restore-source-selector start-source complete-rollback)
declare -A expected_transitions=(
  [steady\|begin-candidate-preparation]=candidate-preparing
  [candidate-preparing\|seal-candidate-offline]=candidate-sealed-offline
  [candidate-preparing\|abandon-candidate-after-review]=steady
  [candidate-sealed-offline\|record-explicit-approval]=approval-recorded
  [candidate-sealed-offline\|abort-before-downtime]=steady
  [approval-recorded\|persist-quiesce-intent]=quiesce-intent
  [approval-recorded\|abort-before-downtime]=steady
  [quiesce-intent\|stop-source]=source-stopped
  [quiesce-intent\|abort-before-downtime]=steady
  [source-stopped\|persist-selector-switch-intent]=selector-switch-intent
  [selector-switch-intent\|start-target]=target-starting
  [target-starting\|verify-target-health]=target-health-verified
  [target-health-verified\|begin-probation]=probation
  [probation\|finalize]=finalized
  [source-stopped\|persist-rollback-intent]=rollback-intent
  [selector-switch-intent\|persist-rollback-intent]=rollback-intent
  [target-starting\|persist-rollback-intent]=rollback-intent
  [target-health-verified\|persist-rollback-intent]=rollback-intent
  [probation\|persist-rollback-intent]=rollback-intent
  [rollback-intent\|stop-target]=target-stopped-for-rollback
  [target-stopped-for-rollback\|restore-source-selector]=source-selector-restored
  [source-selector-restored\|start-source]=source-restarting
  [source-restarting\|complete-rollback]=rolled-back
)
for state in "${states[@]}"; do
  for event in "${events[@]}"; do
    key="$state|$event"
    if [[ -n "${expected_transitions[$key]+present}" ]]; then
      assert_equal "$(wsr_generation_next_state "$state" "$event")" "${expected_transitions[$key]}"
    elif wsr_generation_next_state "$state" "$event" >/dev/null; then
      fail "unauthorized transition accepted: $key"
    fi
  done
done
check

# Linux owns the real flock/procfs contract. Git Bash runs all pure checks but
# lacks util-linux flock, so it reports an explicit local skip; CI executes it.
if [[ "$(uname -s)" == "Linux" && -x /usr/bin/flock ]]; then
  control_root="$temporary_root/control"
  document_root="$control_root/documents"
  mkdir -p -- "$document_root"
  chmod 0700 -- "$control_root" "$document_root"
  : > "$control_root/operation.lock"
  chmod 0600 -- "$control_root/operation.lock"
  uid="$(id -u)"
  assert_true wsr_generation_acquire_operation_lock_at "$control_root/operation.lock" "$uid" shared
  assert_true wsr_generation_require_operation_lock shared
  assert_false wsr_generation_require_operation_lock exclusive
  assert_true flock -n -s "$control_root/operation.lock" -c true
  assert_false flock -n -x "$control_root/operation.lock" -c true
  wsr_generation_close_lock_fd
  assert_true wsr_generation_acquire_operation_lock_at "$control_root/operation.lock" "$uid" exclusive
  assert_false flock -n -s "$control_root/operation.lock" -c true
  assert_true wsr_generation_publish_manifest_v1_at "$document_root/candidate.manifest" candidate_manifest
  assert_false wsr_generation_publish_manifest_v1_at "$document_root/candidate.manifest" candidate_manifest
  assert_true wsr_generation_replace_selector_v1_at "$document_root/active.selector" selector
  assert_equal "$(stat -c '%a:%h' -- "$document_root/active.selector")" "400:1"
  outside_root="$temporary_root/outside"
  mkdir -p -- "$outside_root"
  chmod 0700 -- "$outside_root"
  assert_false wsr_generation_publish_manifest_v1_at "$outside_root/candidate.manifest" candidate_manifest
  chmod 0600 -- "$document_root/active.selector"
  assert_false wsr_generation_replace_selector_v1_at "$document_root/active.selector" selector
  chmod 0400 -- "$document_root/active.selector"
  ln -- "$document_root/active.selector" "$document_root/active.selector.link"
  assert_false wsr_generation_replace_selector_v1_at "$document_root/active.selector" selector
  rm -f -- "$document_root/active.selector.link"
  wsr_generation_close_lock_fd
  chmod 0644 -- "$control_root/operation.lock"
  assert_false wsr_generation_acquire_operation_lock_at "$control_root/operation.lock" "$uid" shared
  check
else
  printf 'SKIP: util-linux flock/procfs runtime checks require Linux; pure ADR-050 checks still ran.\n'
fi

grep -Fq 'source "$script_dir/generation-state.sh"' "$compose_wrapper" || fail "Compose wrapper does not source generation state"
grep -Fq 'wsr_generation_acquire_operation_lock "$operation_lock_mode"' "$compose_wrapper" || fail "Compose action lock missing"
grep -Fq '"${clean_environment[@]}" docker compose \' "$compose_wrapper" || fail "Compose invocation is missing"
if grep -Fq 'exec "${clean_environment[@]}" docker compose' "$compose_wrapper"; then fail "Compose exec would drop the brace-allocated lock FD"; fi
grep -Fq 'wsr_generation_acquire_operation_lock shared' "$deployment_preflight" || fail "Deployment preflight shared lock missing"
grep -Fq 'wsr_generation_acquire_operation_lock shared' "$recovery_preflight" || fail "Recovery preflight shared lock missing"
[[ "$(grep -c 'wsr_generation_acquire_operation_lock shared' "$recovery_production")" == "3" ]] || fail "Recovery shared action mapping changed"
[[ "$(grep -c 'wsr_generation_acquire_operation_lock exclusive' "$recovery_production")" == "4" ]] || fail "Recovery exclusive action mapping changed"
grep -Fq 'sync -- "$1"' "$recovery_common" || fail "Recovery fsync helper does not use sync FILE"
if grep -Fq 'sync -f -- "$1"' "$recovery_common"; then fail "Filesystem-wide syncfs returned"; fi
grep -Fq 'wsr_generation_secure_document_file' "$state_source" || fail "Published document metadata guard missing"
grep -Fq 'document-destination-outside-control-root' "$state_source" || fail "Document publication escaped the held control root"
grep -Fq 'staged_sha="$(sha256sum -- "$temporary"' "$state_source" || fail "Staged bytes are not hashed before publication"
if grep -Eq '^[[:space:]]*(docker|wsr_docker)[[:space:]]' "$state_source"; then fail "Generation state module invokes Docker"; fi
if grep -Eq '^[[:space:]]*(activate|rollback|promote)[)]' "$recovery_production"; then fail "A live generation action was exposed"; fi
grep -Fq 'postgres-data:/var/lib/postgresql/data' "$compose_file" || fail "Legacy Compose volume changed before selector provisioning"
if grep -Fq 'external: true' "$compose_file"; then fail "External volume activated before server bootstrap"; fi
check

set +e
"$state_source" >/dev/null 2>&1
direct_status=$?
set -e
assert_equal "$direct_status" "64"
check

printf 'PASS: ADR-050 canonical selector/manifest/binding/journal, exact state graph, shared lock integration, and blocked live activation passed %d grouped checks.\n' "$checks"
