#!/usr/bin/env bash
set -euo pipefail

# Pure ADR-049 contract test. It exercises transformation helpers directly and
# the production action only after every external prerequisite and emitter has
# been replaced by an in-memory double. It never calls Docker, inspects the
# host, creates a file, or mutates a production resource.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/.." && pwd -P)"
# shellcheck source=deploy/home-server/recovery-common.sh
source "$repo_root/deploy/home-server/recovery-common.sh"
# shellcheck source=deploy/home-server/generation-promotion.sh
source "$repo_root/deploy/home-server/generation-promotion.sh"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_exact_transition() {
  local current="$1" event="$2" expected="$3" actual
  actual="$(wsr_promotion_next_state "$current" "$event")" ||
    fail "valid transition was rejected: $current + $event"
  [[ "$actual" == "$expected" ]] ||
    fail "wrong transition: $current + $event expected $expected, got ${actual:-empty}"
}

assert_transition_rejected() {
  local current="$1" event="$2" label="$3" actual=""
  if actual="$(wsr_promotion_next_state "$current" "$event")"; then
    fail "invalid transition was accepted ($label): $current + $event -> ${actual:-empty}"
  fi
  [[ -z "$actual" ]] ||
    fail "rejected transition emitted ambiguous state ($label): $actual"
}

assert_exact_directive() {
  local state="$1" expected="$2" actual
  actual="$(wsr_promotion_recovery_directive "$state")" ||
    fail "recovery directive was missing for state $state"
  [[ "$actual" == "$expected" ]] ||
    fail "wrong recovery directive for $state: expected $expected, got ${actual:-empty}"
}

assert_line_once() {
  local document="$1" expected="$2" label="$3" count
  count="$(grep -Fxc -- "$expected" <<< "$document" || true)"
  [[ "$count" == "1" ]] ||
    fail "$label must appear exactly once; observed $count"
}

declare -a states=(
  steady
  candidate-preparing
  candidate-sealed-offline
  approval-recorded
  quiesce-intent
  source-stopped
  selector-switch-intent
  target-starting
  target-health-verified
  probation
  finalized
  rollback-intent
  target-stopped-for-rollback
  source-selector-restored
  source-restarting
  rolled-back
)
declare -a events=(
  begin-candidate-preparation
  seal-candidate-offline
  abandon-candidate-after-review
  record-explicit-approval
  abort-before-downtime
  persist-quiesce-intent
  stop-source
  persist-selector-switch-intent
  start-target
  verify-target-health
  begin-probation
  finalize
  persist-rollback-intent
  stop-target
  restore-source-selector
  start-source
  complete-rollback
)
declare -A expected_transitions=(
  ["steady|begin-candidate-preparation"]="candidate-preparing"
  ["candidate-preparing|seal-candidate-offline"]="candidate-sealed-offline"
  ["candidate-preparing|abandon-candidate-after-review"]="steady"
  ["candidate-sealed-offline|record-explicit-approval"]="approval-recorded"
  ["candidate-sealed-offline|abort-before-downtime"]="steady"
  ["approval-recorded|persist-quiesce-intent"]="quiesce-intent"
  ["approval-recorded|abort-before-downtime"]="steady"
  ["quiesce-intent|stop-source"]="source-stopped"
  ["quiesce-intent|abort-before-downtime"]="steady"
  ["source-stopped|persist-selector-switch-intent"]="selector-switch-intent"
  ["selector-switch-intent|start-target"]="target-starting"
  ["target-starting|verify-target-health"]="target-health-verified"
  ["target-health-verified|begin-probation"]="probation"
  ["probation|finalize"]="finalized"
  ["source-stopped|persist-rollback-intent"]="rollback-intent"
  ["selector-switch-intent|persist-rollback-intent"]="rollback-intent"
  ["target-starting|persist-rollback-intent"]="rollback-intent"
  ["target-health-verified|persist-rollback-intent"]="rollback-intent"
  ["probation|persist-rollback-intent"]="rollback-intent"
  ["rollback-intent|stop-target"]="target-stopped-for-rollback"
  ["target-stopped-for-rollback|restore-source-selector"]="source-selector-restored"
  ["source-selector-restored|start-source"]="source-restarting"
  ["source-restarting|complete-rollback"]="rolled-back"
)

# The complete Cartesian table makes every skip, replay, wrong-branch event,
# and terminal-state mutation fail closed instead of checking a few examples.
for state in "${states[@]}"; do
  for event in "${events[@]}"; do
    transition_key="$state|$event"
    if [[ -n "${expected_transitions[$transition_key]+present}" ]]; then
      assert_exact_transition "$state" "$event" "${expected_transitions[$transition_key]}"
    else
      assert_transition_rejected "$state" "$event" "skip/replay/wrong branch"
    fi
  done
done
assert_transition_rejected "unknown-state" "begin-candidate-preparation" "unknown state"
assert_transition_rejected "steady" "unknown-event" "unknown event"
assert_transition_rejected "" "" "empty state and event"

# Every state after source stop and before probation has an explicit operator
# rollback path which never needs to claim target health or enter probation.
for interrupted_state in source-stopped selector-switch-intent target-starting target-health-verified; do
  rollback_state="$(wsr_promotion_next_state "$interrupted_state" persist-rollback-intent)" ||
    fail "interrupted state $interrupted_state has no explicit rollback entry"
  [[ "$rollback_state" == "rollback-intent" ]] ||
    fail "interrupted state $interrupted_state entered an unexpected rollback state"
  for rollback_event in stop-target restore-source-selector start-source complete-rollback; do
    rollback_state="$(wsr_promotion_next_state "$rollback_state" "$rollback_event")" ||
      fail "rollback from $interrupted_state failed at $rollback_event"
    [[ "$rollback_state" != "probation" ]] ||
      fail "rollback from $interrupted_state falsely entered probation"
  done
  [[ "$rollback_state" == "rolled-back" ]] ||
    fail "rollback from $interrupted_state did not restore the source"
done

declare -A expected_directives=(
  [steady]="stable-no-recovery"
  [candidate-preparing]="source-authoritative-manual-candidate-review"
  [candidate-sealed-offline]="source-authoritative-abort-before-downtime"
  [approval-recorded]="source-authoritative-abort-before-downtime"
  [quiesce-intent]="source-authoritative-abort-before-downtime"
  [source-stopped]="operator-recovery-required-no-auto-selection"
  [selector-switch-intent]="operator-recovery-required-no-auto-selection"
  [target-starting]="operator-recovery-required-no-auto-selection"
  [target-health-verified]="operator-recovery-required-no-auto-selection"
  [probation]="operator-choice-exact-target-or-explicit-rollback"
  [finalized]="stable-no-recovery"
  [rollback-intent]="operator-recovery-required-continue-exact-rollback"
  [target-stopped-for-rollback]="operator-recovery-required-continue-exact-rollback"
  [source-selector-restored]="operator-recovery-required-continue-exact-rollback"
  [source-restarting]="operator-recovery-required-continue-exact-rollback"
  [rolled-back]="stable-no-recovery"
)
for state in "${states[@]}"; do
  assert_exact_directive "$state" "${expected_directives[$state]}"
done
unknown_directive=""
if unknown_directive="$(wsr_promotion_recovery_directive "unknown-state")"; then
  fail "an unknown interrupted state received a recovery directive"
fi
[[ -z "$unknown_directive" ]] || fail "unknown-state rejection emitted an ambiguous directive"

# Canonical, synthetic evidence only. These are not observed production facts
# and none of the pure plan helpers contact Docker or the filesystem.
WSR_SCHEMA_BACKUP_ID="20260826T120000Z-Ab12Cd34"
WSR_VALIDATED_RESTORE_EVIDENCE_ID="20260826T121500Z-Ef56Gh78"
WSR_SCHEMA_GIT_SHA="0123456789abcdef0123456789abcdef01234567"
WSR_SCHEMA_FLYWAY_VERSION="11.7.2"
WSR_SCHEMA_MIGRATION_COUNT="9"
WSR_PROMOTION_OBSERVATION_STARTED_UTC="2026-08-26T12:20:00Z"
WSR_PROMOTION_OBSERVATION_COMPLETED_UTC="2026-08-26T12:20:04Z"
WSR_PROMOTION_BACKUP_MANIFEST_SHA256="eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
WSR_PROMOTION_ARCHIVE_SHA256="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
WSR_PROMOTION_RESTORE_MANIFEST_SHA256="abababababababababababababababababababababababababababababababab"
WSR_PROMOTION_DATABASE_EVIDENCE_SHA256="1111111111111111111111111111111111111111111111111111111111111111"
WSR_POSTGRES_CONTAINER_ID="1111111111111111111111111111111111111111111111111111111111111111"
WSR_PROMOTION_API_CONTAINER_ID="2222222222222222222222222222222222222222222222222222222222222222"
WSR_PROMOTION_WEB_CONTAINER_ID="3333333333333333333333333333333333333333333333333333333333333333"
WSR_PROMOTION_CADDY_CONTAINER_ID="4444444444444444444444444444444444444444444444444444444444444444"
WSR_BACKUP_MANIFEST=()
WSR_BACKUP_MANIFEST[postgres_image_id]="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
WSR_BACKUP_MANIFEST[api_image_id]="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
WSR_BACKUP_MANIFEST[web_image_id]="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
WSR_BACKUP_MANIFEST[caddy_production_image_id]="sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"

expected_candidate="wall-street-receipts-generation-20260826t120000z-ab12cd34-20260826t121500z-ef56gh78"
expected_plan="$(printf '%s\n' \
  "plan_version=1" \
  "state_contract_version=1" \
  "mode=production-data-read-only" \
  "transition_model=crash-consistent-controlled-downtime" \
  "observation_started_utc=$WSR_PROMOTION_OBSERVATION_STARTED_UTC" \
  "observation_completed_utc=$WSR_PROMOTION_OBSERVATION_COMPLETED_UTC" \
  "backup_id=$WSR_SCHEMA_BACKUP_ID" \
  "backup_manifest_sha256=$WSR_PROMOTION_BACKUP_MANIFEST_SHA256" \
  "archive_sha256=$WSR_PROMOTION_ARCHIVE_SHA256" \
  "restore_evidence_id=$WSR_VALIDATED_RESTORE_EVIDENCE_ID" \
  "restore_evidence_manifest_sha256=$WSR_PROMOTION_RESTORE_MANIFEST_SHA256" \
  "database_evidence_sha256=$WSR_PROMOTION_DATABASE_EVIDENCE_SHA256" \
  "git_sha=$WSR_SCHEMA_GIT_SHA" \
  "source_postgres_container_id=$WSR_POSTGRES_CONTAINER_ID" \
  "source_postgres_volume=$WSR_RECOVERY_POSTGRES_VOLUME" \
  "source_postgres_image_id=${WSR_BACKUP_MANIFEST[postgres_image_id]}" \
  "source_api_container_id=$WSR_PROMOTION_API_CONTAINER_ID" \
  "source_api_image_id=${WSR_BACKUP_MANIFEST[api_image_id]}" \
  "source_web_container_id=$WSR_PROMOTION_WEB_CONTAINER_ID" \
  "source_web_image_id=${WSR_BACKUP_MANIFEST[web_image_id]}" \
  "source_caddy_container_id=$WSR_PROMOTION_CADDY_CONTAINER_ID" \
  "source_caddy_image_id=${WSR_BACKUP_MANIFEST[caddy_production_image_id]}" \
  "planned_candidate_generation=$expected_candidate" \
  "candidate_state=not-created-by-this-command" \
  "schema_compatibility=compatible-exact-recorded-release" \
  "schema_flyway_version=$WSR_SCHEMA_FLYWAY_VERSION" \
  "schema_migration_count=$WSR_SCHEMA_MIGRATION_COUNT" \
  "source_preservation=required-through-probation" \
  "rehearsal_volume_eligibility=forbidden-trust-auth-disposable-only" \
  "activation_prerequisite_manifest=v2-generation-binding" \
  "activation_prerequisite_selector=protected-external-volume-indirection" \
  "activation_prerequisite_lock=shared-deployment-recovery-lock" \
  "activation_prerequisite_journal=root-owned-fsync-intent-completion" \
  "activation_prerequisite_artifacts=offline-image-custody-and-verification" \
  "activation_prerequisite_capacity=two-generations-plus-restore-headroom" \
  "activation_prerequisite_runtime=exact-env-network-mount-port-contract" \
  "operator_decision_downtime=required" \
  "operator_decision_probation=required" \
  "operator_decision_write_rpo=required" \
  "offsite_copy=required" \
  "activation=blocked-by-this-contract")"
expected_plan_sha="$(printf '%s\n' "$expected_plan" | sha256sum | awk '{print $1}')"

wsr_promotion_build_plan
[[ "$WSR_PROMOTION_PLANNED_CANDIDATE" == "$expected_candidate" ]] ||
  fail "candidate identity was not derived canonically from the exact backup ID"
[[ "$WSR_PROMOTION_PLAN_TEXT" == "$expected_plan" ]] ||
  fail "canonical plan records or record ordering changed"
[[ "$WSR_PROMOTION_PLAN_SHA256" == "$expected_plan_sha" ]] ||
  fail "plan SHA-256 is not the hash of canonical LF plan bytes"
[[ "$WSR_PROMOTION_PLAN_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
  fail "plan SHA-256 is not canonical lowercase hexadecimal"
[[ "$WSR_PROMOTION_PLAN_TEXT" != *$'\r'* ]] || fail "canonical plan contains CR bytes"
[[ "$(wc -l <<< "$WSR_PROMOTION_PLAN_TEXT" | tr -d ' ')" == "41" ]] ||
  fail "canonical plan must contain exactly 41 versioned records"

first_plan_text="$WSR_PROMOTION_PLAN_TEXT"
first_plan_sha="$WSR_PROMOTION_PLAN_SHA256"
WSR_PROMOTION_FAILURE_REASON="irrelevant-prior-failure"
WSR_PROMOTION_FAILURE_MESSAGE="irrelevant prior failure text"
wsr_promotion_build_plan
[[ "$WSR_PROMOTION_PLAN_TEXT" == "$first_plan_text" &&
   "$WSR_PROMOTION_PLAN_SHA256" == "$first_plan_sha" ]] ||
  fail "identical evidence did not produce an identical plan and hash"

original_api_container_id="$WSR_PROMOTION_API_CONTAINER_ID"
WSR_PROMOTION_API_CONTAINER_ID="5555555555555555555555555555555555555555555555555555555555555555"
wsr_promotion_build_plan
[[ "$WSR_PROMOTION_PLAN_SHA256" != "$first_plan_sha" ]] ||
  fail "changing an exact source identity did not change the plan hash"
WSR_PROMOTION_API_CONTAINER_ID="$original_api_container_id"
wsr_promotion_build_plan
[[ "$WSR_PROMOTION_PLAN_SHA256" == "$first_plan_sha" ]] ||
  fail "restoring exact evidence did not restore the canonical plan hash"

original_database_evidence_sha="$WSR_PROMOTION_DATABASE_EVIDENCE_SHA256"
WSR_PROMOTION_DATABASE_EVIDENCE_SHA256="dededededededededededededededededededededededededededededededede"
wsr_promotion_build_plan
[[ "$WSR_PROMOTION_PLAN_SHA256" != "$first_plan_sha" ]] ||
  fail "changing restored database evidence bytes did not change the plan hash"
WSR_PROMOTION_DATABASE_EVIDENCE_SHA256="$original_database_evidence_sha"
wsr_promotion_build_plan
[[ "$WSR_PROMOTION_PLAN_SHA256" == "$first_plan_sha" ]] ||
  fail "restoring the evidence digest did not restore the canonical plan hash"

# Every still-unimplemented safety gate must remain both hash-bound inside the
# plan and visible in the operator output. No readiness synonym is permitted.
declare -a required_plan_blockers=(
  "candidate_state=not-created-by-this-command"
  "source_preservation=required-through-probation"
  "rehearsal_volume_eligibility=forbidden-trust-auth-disposable-only"
  "activation_prerequisite_manifest=v2-generation-binding"
  "activation_prerequisite_selector=protected-external-volume-indirection"
  "activation_prerequisite_lock=shared-deployment-recovery-lock"
  "activation_prerequisite_journal=root-owned-fsync-intent-completion"
  "activation_prerequisite_artifacts=offline-image-custody-and-verification"
  "activation_prerequisite_capacity=two-generations-plus-restore-headroom"
  "activation_prerequisite_runtime=exact-env-network-mount-port-contract"
  "operator_decision_downtime=required"
  "operator_decision_probation=required"
  "operator_decision_write_rpo=required"
  "offsite_copy=required"
  "activation=blocked-by-this-contract"
)
for blocker in "${required_plan_blockers[@]}"; do
  assert_line_once "$WSR_PROMOTION_PLAN_TEXT" "$blocker" "required plan blocker $blocker"
done

emitted_plan="$(wsr_promotion_emit_plan)"
assert_line_once "$emitted_plan" \
  "PROMOTION_PLAN|complete-read-only-contract" "read-only plan result"
assert_line_once "$emitted_plan" \
  "PROMOTION_PLAN_SHA256|$WSR_PROMOTION_PLAN_SHA256" "plan hash surface"
while IFS= read -r plan_record; do
  assert_line_once "$emitted_plan" "PROMOTION_PLAN_RECORD|$plan_record" \
    "emitted canonical plan record $plan_record"
done <<< "$WSR_PROMOTION_PLAN_TEXT"
for blocker in "${required_plan_blockers[@]}"; do
  assert_line_once "$emitted_plan" "PROMOTION_PLAN_RECORD|$blocker" \
    "emitted required blocker $blocker"
done
assert_line_once "$emitted_plan" \
  "PROMOTION_ACTIVATION|blocked-design-prerequisites-and-operator-decisions" \
  "activation blocker"
assert_line_once "$emitted_plan" \
  "ROLLBACK_READINESS|blocked-live-transition-and-artifact-custody-not-implemented" \
  "rollback-readiness blocker"
assert_line_once "$emitted_plan" \
  "PENDING_OFFSITE_COPY|A same-server HDD is not an off-site or offline copy." \
  "off-site-copy blocker"
if grep -Eq '(^|[|=])(ready|rollback-ready|activation-ready)([|=]|$)' <<< "$emitted_plan"; then
  fail "read-only promotion output contained a readiness claim"
fi

saved_evidence_id="$WSR_VALIDATED_RESTORE_EVIDENCE_ID"
WSR_VALIDATED_RESTORE_EVIDENCE_ID=""
if wsr_promotion_build_plan >/dev/null 2>&1; then
  fail "a plan with missing immutable restore evidence was accepted"
fi
[[ "$WSR_PROMOTION_FAILURE_REASON" == "incomplete-plan-evidence" ]] ||
  fail "missing plan evidence did not surface the stable blocker reason"
WSR_VALIDATED_RESTORE_EVIDENCE_ID="$saved_evidence_id"

saved_api_image_id="${WSR_BACKUP_MANIFEST[api_image_id]}"
unset 'WSR_BACKUP_MANIFEST[api_image_id]'
if wsr_promotion_build_plan >/dev/null 2>&1; then
  fail "a plan with missing exact API image evidence was accepted"
fi
[[ "$WSR_PROMOTION_FAILURE_REASON" == "incomplete-plan-evidence" ]] ||
  fail "missing API image evidence did not surface the stable blocker reason"
WSR_BACKUP_MANIFEST[api_image_id]="$saved_api_image_id"

# Execute the live identity comparator against a deterministic in-memory Docker
# double. No executable named docker is called; the common production validator
# is also replaced with a fact-only double for this pure contract fixture.
WSR_TEST_LIVE_MODE="exact"
WSR_TEST_POSTGRES_SHORT_ID="eeeeeeeeeeee"
WSR_TEST_POSTGRES_FULL_ID="eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
WSR_TEST_API_ID="2222222222222222222222222222222222222222222222222222222222222222"
WSR_TEST_WEB_ID="3333333333333333333333333333333333333333333333333333333333333333"
WSR_TEST_CADDY_ID="4444444444444444444444444444444444444444444444444444444444444444"
WSR_BACKUP_MANIFEST[git_sha]="$WSR_SCHEMA_GIT_SHA"
WSR_BACKUP_MANIFEST[postgres_volume_name]="$WSR_RECOVERY_POSTGRES_VOLUME"
WSR_BACKUP_MANIFEST[postgres_image_reference]="postgres:17-alpine"
WSR_BACKUP_MANIFEST[postgres_image_revision]="unavailable"
WSR_BACKUP_MANIFEST[api_image_reference]="wall-street-receipts-api:$WSR_SCHEMA_GIT_SHA"
WSR_BACKUP_MANIFEST[api_image_revision]="$WSR_SCHEMA_GIT_SHA"
WSR_BACKUP_MANIFEST[web_image_reference]="wall-street-receipts-web:$WSR_SCHEMA_GIT_SHA"
WSR_BACKUP_MANIFEST[web_image_revision]="$WSR_SCHEMA_GIT_SHA"
WSR_BACKUP_MANIFEST[caddy_production_image_reference]="wall-street-receipts-caddy:$WSR_SCHEMA_GIT_SHA"
WSR_BACKUP_MANIFEST[caddy_production_image_revision]="$WSR_SCHEMA_GIT_SHA"

wsr_validate_production_postgres() {
  WSR_POSTGRES_CONTAINER_ID="$WSR_TEST_POSTGRES_SHORT_ID"
  WSR_POSTGRES_IMAGE_REFERENCE="postgres:17-alpine"
  WSR_POSTGRES_IMAGE_ID="${WSR_BACKUP_MANIFEST[postgres_image_id]}"
  if [[ "$WSR_TEST_LIVE_MODE" == "postgres-revision-mismatch" ]]; then
    WSR_POSTGRES_IMAGE_REVISION="$WSR_SCHEMA_GIT_SHA"
  else
    WSR_POSTGRES_IMAGE_REVISION="unavailable"
  fi
}

wsr_docker() {
  local operation="$1"
  shift
  if [[ "$operation" == "container" && "${1:-}" == "ls" ]]; then
    local arguments="$*"
    [[ "$arguments" == *"--all --quiet --no-trunc"* ]] || return 1
    case "$arguments" in
      *com.docker.compose.service=api*)
        printf '%s\n' "$WSR_TEST_API_ID"
        if [[ "$WSR_TEST_LIVE_MODE" == "duplicate-api" ]]; then
          printf '%064d\n' 9
        fi
        ;;
      *com.docker.compose.service=web*) printf '%s\n' "$WSR_TEST_WEB_ID" ;;
      *com.docker.compose.service=caddy-production*) printf '%s\n' "$WSR_TEST_CADDY_ID" ;;
      *) return 1 ;;
    esac
    return 0
  fi
  if [[ "$operation" != "inspect" || "${1:-}" != "--format" || $# != 3 ]]; then
    return 1
  fi
  local format="$2" container_id="$3" service=""
  case "$container_id" in
    "$WSR_TEST_POSTGRES_SHORT_ID"|"$WSR_TEST_POSTGRES_FULL_ID") service="postgres" ;;
    "$WSR_TEST_API_ID") service="api" ;;
    "$WSR_TEST_WEB_ID") service="web" ;;
    "$WSR_TEST_CADDY_ID") service="caddy-production" ;;
    *) return 1 ;;
  esac
  case "$format" in
    '{{.Id}}') printf '%s\n' "$WSR_TEST_POSTGRES_FULL_ID" ;;
    '{{index .Config.Labels "com.wallstreetreceipts.release-sha"}}')
      if [[ "$WSR_TEST_LIVE_MODE" == "postgres-release-mismatch" ]]; then
        printf 'ffffffffffffffffffffffffffffffffffffffff\n'
      else
        printf '%s\n' "$WSR_SCHEMA_GIT_SHA"
      fi
      ;;
    '{{.State.Running}}')
      if [[ "$WSR_TEST_LIVE_MODE" == "api-stopped" && "$service" == "api" ]]; then
        printf 'false\n'
      else
        printf 'true\n'
      fi
      ;;
    '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}')
      if [[ "$WSR_TEST_LIVE_MODE" == "api-unhealthy" && "$service" == "api" ]]; then
        printf 'unhealthy\n'
      else
        printf 'healthy\n'
      fi
      ;;
    '{{index .Config.Labels "com.docker.compose.project"}}') printf '%s\n' "$WSR_RECOVERY_PROJECT" ;;
    '{{index .Config.Labels "com.docker.compose.service"}}') printf '%s\n' "$service" ;;
    '{{.Config.Image}}')
      case "$service" in
        api) printf '%s\n' "${WSR_BACKUP_MANIFEST[api_image_reference]}" ;;
        web) printf '%s\n' "${WSR_BACKUP_MANIFEST[web_image_reference]}" ;;
        caddy-production) printf '%s\n' "${WSR_BACKUP_MANIFEST[caddy_production_image_reference]}" ;;
        *) return 1 ;;
      esac
      ;;
    '{{.Image}}')
      case "$service" in
        api)
          if [[ "$WSR_TEST_LIVE_MODE" == "api-image-mismatch" ]]; then
            printf 'sha256:%064d\n' 9
          else
            printf '%s\n' "${WSR_BACKUP_MANIFEST[api_image_id]}"
          fi
          ;;
        web) printf '%s\n' "${WSR_BACKUP_MANIFEST[web_image_id]}" ;;
        caddy-production) printf '%s\n' "${WSR_BACKUP_MANIFEST[caddy_production_image_id]}" ;;
        *) return 1 ;;
      esac
      ;;
    '{{index .Config.Labels "org.opencontainers.image.revision"}}')
      case "$service" in
        api) printf '%s\n' "${WSR_BACKUP_MANIFEST[api_image_revision]}" ;;
        web) printf '%s\n' "${WSR_BACKUP_MANIFEST[web_image_revision]}" ;;
        caddy-production) printf '%s\n' "${WSR_BACKUP_MANIFEST[caddy_production_image_revision]}" ;;
        *) return 1 ;;
      esac
      ;;
    *) return 1 ;;
  esac
}

WSR_TEST_LIVE_MODE="exact"
wsr_promotion_validate_live_release || fail "exact synthetic live release was rejected"
[[ "$WSR_POSTGRES_CONTAINER_ID" == "$WSR_TEST_POSTGRES_FULL_ID" &&
   "$WSR_PROMOTION_API_CONTAINER_ID" == "$WSR_TEST_API_ID" &&
   "$WSR_PROMOTION_WEB_CONTAINER_ID" == "$WSR_TEST_WEB_ID" &&
   "$WSR_PROMOTION_CADDY_CONTAINER_ID" == "$WSR_TEST_CADDY_ID" ]] ||
  fail "live release comparator did not retain exact full container identities"

WSR_TEST_LIVE_MODE="duplicate-api"
if wsr_promotion_validate_live_release >/dev/null 2>&1; then
  fail "duplicate Compose API containers were accepted"
fi
[[ "$WSR_PROMOTION_FAILURE_REASON" == "live-release-service-ambiguous" ]] ||
  fail "duplicate API containers did not produce the stable ambiguity blocker"

WSR_TEST_LIVE_MODE="api-image-mismatch"
if wsr_promotion_validate_live_release >/dev/null 2>&1; then
  fail "a changed current API image ID was accepted"
fi
[[ "$WSR_PROMOTION_FAILURE_REASON" == "live-release-resource-mismatch" ]] ||
  fail "changed API image did not produce the stable resource-mismatch blocker"

WSR_TEST_LIVE_MODE="postgres-revision-mismatch"
if wsr_promotion_validate_live_release >/dev/null 2>&1; then
  fail "a changed current PostgreSQL image revision was accepted"
fi
[[ "$WSR_PROMOTION_FAILURE_REASON" == "live-postgres-release-mismatch" ]] ||
  fail "changed PostgreSQL revision did not produce the stable release blocker"

for unhealthy_mode in api-stopped api-unhealthy; do
  WSR_TEST_LIVE_MODE="$unhealthy_mode"
  if wsr_promotion_validate_live_release >/dev/null 2>&1; then
    fail "$unhealthy_mode current API service was accepted"
  fi
  [[ "$WSR_PROMOTION_FAILURE_REASON" == "live-release-resource-mismatch" ]] ||
    fail "$unhealthy_mode did not produce the stable resource-mismatch blocker"
done

WSR_TEST_LIVE_MODE="postgres-release-mismatch"
if wsr_promotion_validate_live_release >/dev/null 2>&1; then
  fail "a changed current PostgreSQL release label was accepted"
fi
[[ "$WSR_PROMOTION_FAILURE_REASON" == "live-postgres-release-mismatch" ]] ||
  fail "changed PostgreSQL release label did not produce the stable release blocker"

# Action-level doubles prove no prerequisite failure can fall through to the
# successful plan emitter. They replace every external/read-only prerequisite;
# the action dispatcher itself is the production implementation under test.
WSR_TEST_ACTION_MODE=""
WSR_TEST_ACTION_PLAN_EMITS=0
WSR_TEST_ACTION_BLOCKED_EMITS=0
wsr_schema_reset() {
  WSR_SCHEMA_FAILURE_REASON=""
  WSR_SCHEMA_FAILURE_MESSAGE=""
}
wsr_evaluate_latest_schema_compatibility() {
  if [[ "$WSR_TEST_ACTION_MODE" == "schema-fail" ]]; then
    WSR_SCHEMA_FAILURE_REASON="synthetic-schema-failure"
    WSR_SCHEMA_FAILURE_MESSAGE="Synthetic schema failure."
    return 1
  fi
  WSR_SCHEMA_BACKUP_ID="20260826T120000Z-Ab12Cd34"
  WSR_VALIDATED_RESTORE_EVIDENCE_ID="20260826T121500Z-Ef56Gh78"
  WSR_SCHEMA_GIT_SHA="0123456789abcdef0123456789abcdef01234567"
  WSR_SCHEMA_FLYWAY_VERSION="11.7.2"
  WSR_SCHEMA_MIGRATION_COUNT="9"
}
wsr_promotion_bind_validated_evidence_digests() {
  if [[ "$WSR_TEST_ACTION_MODE" == "digest-fail" ]]; then
    wsr_promotion_fail "synthetic-digest-failure" "Synthetic digest failure."
    return 1
  fi
  WSR_PROMOTION_BACKUP_MANIFEST_SHA256="eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
  WSR_PROMOTION_ARCHIVE_SHA256="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
  WSR_PROMOTION_RESTORE_MANIFEST_SHA256="abababababababababababababababababababababababababababababababab"
  WSR_PROMOTION_DATABASE_EVIDENCE_SHA256="1111111111111111111111111111111111111111111111111111111111111111"
}
wsr_promotion_validate_live_release() {
  if [[ "$WSR_TEST_ACTION_MODE" == "live-fail" ]]; then
    wsr_promotion_fail "synthetic-live-failure" "Synthetic live release failure."
    return 1
  fi
  WSR_POSTGRES_CONTAINER_ID="1111111111111111111111111111111111111111111111111111111111111111"
  WSR_PROMOTION_API_CONTAINER_ID="2222222222222222222222222222222222222222222222222222222222222222"
  WSR_PROMOTION_WEB_CONTAINER_ID="3333333333333333333333333333333333333333333333333333333333333333"
  WSR_PROMOTION_CADDY_CONTAINER_ID="4444444444444444444444444444444444444444444444444444444444444444"
}
wsr_promotion_revalidate_observation_snapshot() {
  if [[ "$WSR_TEST_ACTION_MODE" == "revalidation-fail" ]]; then
    wsr_promotion_fail "synthetic-revalidation-failure" "Synthetic revalidation failure."
    return 1
  fi
}
wsr_promotion_build_plan() {
  if [[ "$WSR_TEST_ACTION_MODE" == "build-fail" ]]; then
    wsr_promotion_fail "synthetic-build-failure" "Synthetic plan build failure."
    return 1
  fi
}
wsr_promotion_emit_plan() {
  WSR_TEST_ACTION_PLAN_EMITS=$((WSR_TEST_ACTION_PLAN_EMITS + 1))
}
wsr_promotion_emit_blocked() {
  WSR_TEST_ACTION_BLOCKED_EMITS=$((WSR_TEST_ACTION_BLOCKED_EMITS + 1))
}
assert_action_blocked_before_plan() {
  local mode="$1"
  WSR_TEST_ACTION_MODE="$mode"
  WSR_TEST_ACTION_PLAN_EMITS=0
  WSR_TEST_ACTION_BLOCKED_EMITS=0
  if wsr_action_promotion_plan_latest >/dev/null 2>&1; then
    fail "promotion action succeeded after $mode"
  fi
  [[ "$WSR_TEST_ACTION_PLAN_EMITS" == "0" &&
     "$WSR_TEST_ACTION_BLOCKED_EMITS" == "1" ]] ||
    fail "promotion action emitted a successful plan after $mode"
}
for failure_mode in schema-fail digest-fail live-fail revalidation-fail build-fail; do
  assert_action_blocked_before_plan "$failure_mode"
done
WSR_TEST_ACTION_MODE="success"
WSR_TEST_ACTION_PLAN_EMITS=0
WSR_TEST_ACTION_BLOCKED_EMITS=0
wsr_action_promotion_plan_latest >/dev/null 2>&1 ||
  fail "promotion action rejected the fully satisfied synthetic gate sequence"
[[ "$WSR_TEST_ACTION_PLAN_EMITS" == "1" &&
   "$WSR_TEST_ACTION_BLOCKED_EMITS" == "0" ]] ||
  fail "fully satisfied action did not emit exactly one successful plan"

printf 'PASS: generation transition, conservative recovery, canonical plan hashing, and read-only blockers are exact.\n'
