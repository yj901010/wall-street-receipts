#!/usr/bin/env bash
# Source-only ADR-049 production-data-read-only generation promotion policy.
#
# This policy can prove that the latest immutable recovery point belongs to the
# exact release which is currently running and can produce a hash-bound future
# transition plan.  It cannot create a candidate volume, stop a service, change
# a selector, write a transition journal, promote data, or perform rollback.
if [[ -n "${WSR_GENERATION_PROMOTION_LOADED:-}" ]]; then
  # shellcheck disable=SC2317
  return 0 2>/dev/null || exit 0
fi
WSR_GENERATION_PROMOTION_LOADED=1

readonly WSR_PROMOTION_PLAN_VERSION="1"
readonly WSR_PROMOTION_STATE_CONTRACT_VERSION="1"
readonly WSR_PROMOTION_TRANSITION_MODEL="crash-consistent-controlled-downtime"
readonly WSR_PROMOTION_CANDIDATE_PREFIX="wall-street-receipts-generation-"

WSR_PROMOTION_FAILURE_REASON=""
WSR_PROMOTION_FAILURE_MESSAGE=""
WSR_PROMOTION_PLANNED_CANDIDATE=""
WSR_PROMOTION_PLAN_TEXT=""
WSR_PROMOTION_PLAN_SHA256=""
WSR_PROMOTION_API_CONTAINER_ID=""
WSR_PROMOTION_WEB_CONTAINER_ID=""
WSR_PROMOTION_CADDY_CONTAINER_ID=""
WSR_PROMOTION_BACKUP_MANIFEST_SHA256=""
WSR_PROMOTION_ARCHIVE_SHA256=""
WSR_PROMOTION_RESTORE_MANIFEST_SHA256=""
WSR_PROMOTION_DATABASE_EVIDENCE_SHA256=""
WSR_PROMOTION_OBSERVATION_STARTED_UTC=""
WSR_PROMOTION_OBSERVATION_COMPLETED_UTC=""
WSR_PROMOTION_SNAPSHOT_BACKUP_ID=""
WSR_PROMOTION_SNAPSHOT_EVIDENCE_ID=""
WSR_PROMOTION_SNAPSHOT_POSTGRES_CONTAINER_ID=""
WSR_PROMOTION_SNAPSHOT_API_CONTAINER_ID=""
WSR_PROMOTION_SNAPSHOT_WEB_CONTAINER_ID=""
WSR_PROMOTION_SNAPSHOT_CADDY_CONTAINER_ID=""
WSR_PROMOTION_SNAPSHOT_BACKUP_MANIFEST_SHA256=""
WSR_PROMOTION_SNAPSHOT_ARCHIVE_SHA256=""
WSR_PROMOTION_SNAPSHOT_RESTORE_MANIFEST_SHA256=""
WSR_PROMOTION_SNAPSHOT_DATABASE_EVIDENCE_SHA256=""

wsr_promotion_reset() {
  WSR_PROMOTION_FAILURE_REASON=""
  WSR_PROMOTION_FAILURE_MESSAGE=""
  WSR_PROMOTION_PLANNED_CANDIDATE=""
  WSR_PROMOTION_PLAN_TEXT=""
  WSR_PROMOTION_PLAN_SHA256=""
  WSR_PROMOTION_API_CONTAINER_ID=""
  WSR_PROMOTION_WEB_CONTAINER_ID=""
  WSR_PROMOTION_CADDY_CONTAINER_ID=""
  WSR_PROMOTION_BACKUP_MANIFEST_SHA256=""
  WSR_PROMOTION_ARCHIVE_SHA256=""
  WSR_PROMOTION_RESTORE_MANIFEST_SHA256=""
  WSR_PROMOTION_DATABASE_EVIDENCE_SHA256=""
  WSR_PROMOTION_OBSERVATION_STARTED_UTC=""
  WSR_PROMOTION_OBSERVATION_COMPLETED_UTC=""
  WSR_PROMOTION_SNAPSHOT_BACKUP_ID=""
  WSR_PROMOTION_SNAPSHOT_EVIDENCE_ID=""
  WSR_PROMOTION_SNAPSHOT_POSTGRES_CONTAINER_ID=""
  WSR_PROMOTION_SNAPSHOT_API_CONTAINER_ID=""
  WSR_PROMOTION_SNAPSHOT_WEB_CONTAINER_ID=""
  WSR_PROMOTION_SNAPSHOT_CADDY_CONTAINER_ID=""
  WSR_PROMOTION_SNAPSHOT_BACKUP_MANIFEST_SHA256=""
  WSR_PROMOTION_SNAPSHOT_ARCHIVE_SHA256=""
  WSR_PROMOTION_SNAPSHOT_RESTORE_MANIFEST_SHA256=""
  WSR_PROMOTION_SNAPSHOT_DATABASE_EVIDENCE_SHA256=""
}

wsr_promotion_fail() {
  WSR_PROMOTION_FAILURE_REASON="$1"
  WSR_PROMOTION_FAILURE_MESSAGE="$2"
  return 1
}

# Pure transition table for a future, separately approved live implementation.
# A journaled implementation must persist and fsync each intent before the
# corresponding mutation and persist and fsync completion afterwards.
wsr_promotion_next_state() {
  local current="$1" event="$2"
  case "$current|$event" in
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

# Pure fail-closed crash classification.  Ambiguous post-stop states never pick
# a database generation automatically and never authorize deleting either one.
wsr_promotion_recovery_directive() {
  local state="$1"
  case "$state" in
    steady|finalized|rolled-back)
      printf 'stable-no-recovery\n'
      ;;
    candidate-preparing)
      printf 'source-authoritative-manual-candidate-review\n'
      ;;
    candidate-sealed-offline|approval-recorded|quiesce-intent)
      printf 'source-authoritative-abort-before-downtime\n'
      ;;
    source-stopped|selector-switch-intent|target-starting|target-health-verified)
      printf 'operator-recovery-required-no-auto-selection\n'
      ;;
    probation)
      printf 'operator-choice-exact-target-or-explicit-rollback\n'
      ;;
    rollback-intent|target-stopped-for-rollback|source-selector-restored|source-restarting)
      printf 'operator-recovery-required-continue-exact-rollback\n'
      ;;
    *) return 1 ;;
  esac
}

wsr_promotion_validate_release_service() {
  local service="$1" variable_prefix="$2" expected_reference="$3"
  local expected_image_id="$4" expected_revision="$5"
  local container_id running health project_label service_label
  local image_reference image_id revision
  local -a container_ids=()

  if [[ ! "$service" =~ ^(api|web|caddy-production)$ ||
        ! "$variable_prefix" =~ ^WSR_PROMOTION_(API|WEB|CADDY)$ ||
        ! "$expected_reference" =~ ^[A-Za-z0-9._:/+-]+$ ||
        ! "$expected_image_id" =~ ^sha256:[0-9a-f]{64}$ ||
        ! "$expected_revision" =~ ^[0-9a-f]{40}$ ]]; then
    wsr_promotion_fail "invalid-live-release-expectation" "The recorded release identity is not canonical."
    return 1
  fi
  mapfile -t container_ids < <(
    wsr_docker container ls --all --quiet --no-trunc \
      --filter "label=com.docker.compose.project=$WSR_RECOVERY_PROJECT" \
      --filter "label=com.docker.compose.service=$service"
  )
  if ((${#container_ids[@]} != 1)) || [[ ! "${container_ids[0]}" =~ ^[0-9a-f]{64}$ ]]; then
    wsr_promotion_fail "live-release-service-ambiguous" "Exactly one Compose-labeled $service container is required."
    return 1
  fi
  container_id="${container_ids[0]}"
  running="$(wsr_docker inspect --format '{{.State.Running}}' "$container_id")"
  health="$(wsr_docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container_id")"
  project_label="$(wsr_docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$container_id")"
  service_label="$(wsr_docker inspect --format '{{index .Config.Labels "com.docker.compose.service"}}' "$container_id")"
  image_reference="$(wsr_docker inspect --format '{{.Config.Image}}' "$container_id")"
  image_id="$(wsr_docker inspect --format '{{.Image}}' "$container_id")"
  revision="$(wsr_docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$container_id" 2>/dev/null || true)"
  if [[ "$running" != "true" || "$health" != "healthy" ||
        "$project_label" != "$WSR_RECOVERY_PROJECT" || "$service_label" != "$service" ||
        "$image_reference" != "$expected_reference" || "$image_id" != "$expected_image_id" ||
        "$revision" != "$expected_revision" ]]; then
    wsr_promotion_fail "live-release-resource-mismatch" "The running $service resource differs from the backup-recorded release identity."
    return 1
  fi
  printf -v "${variable_prefix}_CONTAINER_ID" '%s' "$container_id"
}

wsr_promotion_validate_live_release() {
  local postgres_full_id postgres_release_label
  local git_sha="${WSR_BACKUP_MANIFEST[git_sha]:-}"

  wsr_validate_production_postgres || {
    wsr_promotion_fail "live-postgres-contract-mismatch" "The active PostgreSQL source failed the fixed production contract."
    return 1
  }
  postgres_full_id="$(wsr_docker inspect --format '{{.Id}}' "$WSR_POSTGRES_CONTAINER_ID" 2>/dev/null || true)"
  if [[ ! "$postgres_full_id" =~ ^[0-9a-f]{64}$ ]]; then
    wsr_promotion_fail "live-postgres-identity-invalid" "The active PostgreSQL container has no exact full Docker identity."
    return 1
  fi
  WSR_POSTGRES_CONTAINER_ID="$postgres_full_id"
  postgres_release_label="$(wsr_docker inspect --format '{{index .Config.Labels "com.wallstreetreceipts.release-sha"}}' "$WSR_POSTGRES_CONTAINER_ID" 2>/dev/null || true)"
  if [[ ! "$git_sha" =~ ^[0-9a-f]{40}$ || "$postgres_release_label" != "$git_sha" ||
        "$WSR_POSTGRES_IMAGE_ID" != "${WSR_BACKUP_MANIFEST[postgres_image_id]:-}" ||
        "$WSR_POSTGRES_IMAGE_REFERENCE" != "${WSR_BACKUP_MANIFEST[postgres_image_reference]:-}" ||
        "$WSR_POSTGRES_IMAGE_REVISION" != "${WSR_BACKUP_MANIFEST[postgres_image_revision]:-}" ||
        "${WSR_BACKUP_MANIFEST[postgres_volume_name]:-}" != "$WSR_RECOVERY_POSTGRES_VOLUME" ]]; then
    wsr_promotion_fail "live-postgres-release-mismatch" "The active PostgreSQL release, image, or legacy source volume differs from the latest backup."
    return 1
  fi
  wsr_promotion_validate_release_service \
    api WSR_PROMOTION_API \
    "${WSR_BACKUP_MANIFEST[api_image_reference]:-}" \
    "${WSR_BACKUP_MANIFEST[api_image_id]:-}" \
    "${WSR_BACKUP_MANIFEST[api_image_revision]:-}" || return 1
  wsr_promotion_validate_release_service \
    web WSR_PROMOTION_WEB \
    "${WSR_BACKUP_MANIFEST[web_image_reference]:-}" \
    "${WSR_BACKUP_MANIFEST[web_image_id]:-}" \
    "${WSR_BACKUP_MANIFEST[web_image_revision]:-}" || return 1
  wsr_promotion_validate_release_service \
    caddy-production WSR_PROMOTION_CADDY \
    "${WSR_BACKUP_MANIFEST[caddy_production_image_reference]:-}" \
    "${WSR_BACKUP_MANIFEST[caddy_production_image_id]:-}" \
    "${WSR_BACKUP_MANIFEST[caddy_production_image_revision]:-}" || return 1
}

wsr_promotion_bind_validated_evidence_digests() {
  local backup_manifest_path restore_manifest_path evidence_file
  local backup_manifest_sha restore_manifest_sha evidence_sha archive_sha

  backup_manifest_path="${WSR_VALIDATED_BACKUP_PATH:-}/manifest"
  restore_manifest_path="${WSR_VALIDATED_RESTORE_EVIDENCE_PATH:-}/manifest"
  evidence_file="${WSR_VALIDATED_RESTORE_EVIDENCE_FILE:-}"
  if [[ ! -f "$backup_manifest_path" || -L "$backup_manifest_path" ||
        ! -f "$restore_manifest_path" || -L "$restore_manifest_path" ||
        ! -f "$evidence_file" || -L "$evidence_file" ]]; then
    wsr_promotion_fail "validated-evidence-unavailable" "The validated backup or restore-evidence members are no longer regular files."
    return 1
  fi
  backup_manifest_sha="$(sha256sum -- "$backup_manifest_path" 2>/dev/null | awk '{print $1}')" || {
    wsr_promotion_fail "validated-evidence-unavailable" "The backup manifest could not be rehashed."
    return 1
  }
  restore_manifest_sha="$(sha256sum -- "$restore_manifest_path" 2>/dev/null | awk '{print $1}')" || {
    wsr_promotion_fail "validated-evidence-unavailable" "The restore-evidence manifest could not be hashed."
    return 1
  }
  evidence_sha="$(sha256sum -- "$evidence_file" 2>/dev/null | awk '{print $1}')" || {
    wsr_promotion_fail "validated-evidence-unavailable" "The restored database evidence could not be rehashed."
    return 1
  }
  archive_sha="${WSR_BACKUP_MANIFEST[archive_sha256]:-}"
  if [[ ! "$backup_manifest_sha" =~ ^[0-9a-f]{64}$ ||
        ! "$restore_manifest_sha" =~ ^[0-9a-f]{64}$ ||
        ! "$evidence_sha" =~ ^[0-9a-f]{64}$ ||
        ! "$archive_sha" =~ ^[0-9a-f]{64}$ ||
        "$backup_manifest_sha" != "${WSR_RESTORE_EVIDENCE_MANIFEST[backup_manifest_sha256]:-}" ||
        "$archive_sha" != "${WSR_RESTORE_EVIDENCE_MANIFEST[archive_sha256]:-}" ||
        "$evidence_sha" != "${WSR_RESTORE_EVIDENCE_MANIFEST[evidence_sha256]:-}" ]]; then
    wsr_promotion_fail "validated-evidence-digest-mismatch" "The backup, archive, or restore-evidence content digest changed after validation."
    return 1
  fi
  WSR_PROMOTION_BACKUP_MANIFEST_SHA256="$backup_manifest_sha"
  WSR_PROMOTION_ARCHIVE_SHA256="$archive_sha"
  WSR_PROMOTION_RESTORE_MANIFEST_SHA256="$restore_manifest_sha"
  WSR_PROMOTION_DATABASE_EVIDENCE_SHA256="$evidence_sha"
}

wsr_promotion_capture_observation_snapshot() {
  WSR_PROMOTION_SNAPSHOT_BACKUP_ID="$WSR_SCHEMA_BACKUP_ID"
  WSR_PROMOTION_SNAPSHOT_EVIDENCE_ID="$WSR_VALIDATED_RESTORE_EVIDENCE_ID"
  WSR_PROMOTION_SNAPSHOT_POSTGRES_CONTAINER_ID="$WSR_POSTGRES_CONTAINER_ID"
  WSR_PROMOTION_SNAPSHOT_API_CONTAINER_ID="$WSR_PROMOTION_API_CONTAINER_ID"
  WSR_PROMOTION_SNAPSHOT_WEB_CONTAINER_ID="$WSR_PROMOTION_WEB_CONTAINER_ID"
  WSR_PROMOTION_SNAPSHOT_CADDY_CONTAINER_ID="$WSR_PROMOTION_CADDY_CONTAINER_ID"
  WSR_PROMOTION_SNAPSHOT_BACKUP_MANIFEST_SHA256="$WSR_PROMOTION_BACKUP_MANIFEST_SHA256"
  WSR_PROMOTION_SNAPSHOT_ARCHIVE_SHA256="$WSR_PROMOTION_ARCHIVE_SHA256"
  WSR_PROMOTION_SNAPSHOT_RESTORE_MANIFEST_SHA256="$WSR_PROMOTION_RESTORE_MANIFEST_SHA256"
  WSR_PROMOTION_SNAPSHOT_DATABASE_EVIDENCE_SHA256="$WSR_PROMOTION_DATABASE_EVIDENCE_SHA256"
}

wsr_promotion_revalidate_observation_snapshot() {
  local latest_backup

  latest_backup="$(wsr_latest_backup_id 2>/dev/null || true)"
  if [[ "$latest_backup" != "$WSR_PROMOTION_SNAPSHOT_BACKUP_ID" ]] ||
     ! wsr_validate_completed_backup "$WSR_PROMOTION_SNAPSHOT_BACKUP_ID" >/dev/null ||
     ! wsr_find_restore_evidence "$WSR_PROMOTION_SNAPSHOT_BACKUP_ID" >/dev/null ||
     [[ "$WSR_VALIDATED_RESTORE_EVIDENCE_ID" != "$WSR_PROMOTION_SNAPSHOT_EVIDENCE_ID" ]] ||
     ! wsr_promotion_bind_validated_evidence_digests ||
     ! wsr_promotion_validate_live_release; then
    wsr_promotion_fail "observation-changed" "Backup, restore evidence, or live release changed during promotion planning."
    return 1
  fi
  if [[ "$WSR_POSTGRES_CONTAINER_ID" != "$WSR_PROMOTION_SNAPSHOT_POSTGRES_CONTAINER_ID" ||
        "$WSR_PROMOTION_API_CONTAINER_ID" != "$WSR_PROMOTION_SNAPSHOT_API_CONTAINER_ID" ||
        "$WSR_PROMOTION_WEB_CONTAINER_ID" != "$WSR_PROMOTION_SNAPSHOT_WEB_CONTAINER_ID" ||
        "$WSR_PROMOTION_CADDY_CONTAINER_ID" != "$WSR_PROMOTION_SNAPSHOT_CADDY_CONTAINER_ID" ||
        "$WSR_PROMOTION_BACKUP_MANIFEST_SHA256" != "$WSR_PROMOTION_SNAPSHOT_BACKUP_MANIFEST_SHA256" ||
        "$WSR_PROMOTION_ARCHIVE_SHA256" != "$WSR_PROMOTION_SNAPSHOT_ARCHIVE_SHA256" ||
        "$WSR_PROMOTION_RESTORE_MANIFEST_SHA256" != "$WSR_PROMOTION_SNAPSHOT_RESTORE_MANIFEST_SHA256" ||
        "$WSR_PROMOTION_DATABASE_EVIDENCE_SHA256" != "$WSR_PROMOTION_SNAPSHOT_DATABASE_EVIDENCE_SHA256" ]]; then
    wsr_promotion_fail "observation-changed" "The final promotion observation differs from its initial exact identities."
    return 1
  fi
}

wsr_promotion_complete_observation() {
  WSR_PROMOTION_OBSERVATION_COMPLETED_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if [[ ! "$WSR_PROMOTION_OBSERVATION_STARTED_UTC" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ||
        ! "$WSR_PROMOTION_OBSERVATION_COMPLETED_UTC" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ||
        "$WSR_PROMOTION_OBSERVATION_COMPLETED_UTC" < "$WSR_PROMOTION_OBSERVATION_STARTED_UTC" ]]; then
    wsr_promotion_fail "observation-time-invalid" "The promotion observation interval is not one ordered canonical UTC interval."
    return 1
  fi
}

wsr_promotion_build_plan() {
  local backup_id="${WSR_SCHEMA_BACKUP_ID:-}"
  local evidence_id="${WSR_VALIDATED_RESTORE_EVIDENCE_ID:-}"
  local git_sha="${WSR_SCHEMA_GIT_SHA:-}"
  local candidate postgres_image_id api_image_id web_image_id caddy_image_id
  local flyway_version="${WSR_SCHEMA_FLYWAY_VERSION:-}"
  local migration_count="${WSR_SCHEMA_MIGRATION_COUNT:-}"

  postgres_image_id="${WSR_BACKUP_MANIFEST[postgres_image_id]:-}"
  api_image_id="${WSR_BACKUP_MANIFEST[api_image_id]:-}"
  web_image_id="${WSR_BACKUP_MANIFEST[web_image_id]:-}"
  caddy_image_id="${WSR_BACKUP_MANIFEST[caddy_production_image_id]:-}"

  if ! wsr_backup_id_valid "$backup_id" || ! wsr_backup_id_valid "$evidence_id" ||
     [[ ! "$git_sha" =~ ^[0-9a-f]{40}$ ||
        ! "$WSR_PROMOTION_OBSERVATION_STARTED_UTC" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ||
        ! "$WSR_PROMOTION_OBSERVATION_COMPLETED_UTC" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ||
        "$WSR_PROMOTION_OBSERVATION_COMPLETED_UTC" < "$WSR_PROMOTION_OBSERVATION_STARTED_UTC" ||
        ! "$WSR_PROMOTION_BACKUP_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ ||
        ! "$WSR_PROMOTION_ARCHIVE_SHA256" =~ ^[0-9a-f]{64}$ ||
        ! "$WSR_PROMOTION_RESTORE_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ ||
        ! "$WSR_PROMOTION_DATABASE_EVIDENCE_SHA256" =~ ^[0-9a-f]{64}$ ||
        ! "$flyway_version" =~ ^[0-9]+([.][0-9]+)+$ ||
        ! "$migration_count" =~ ^[1-9][0-9]{0,3}$ ||
        ! "$postgres_image_id" =~ ^sha256:[0-9a-f]{64}$ ||
        ! "$api_image_id" =~ ^sha256:[0-9a-f]{64}$ ||
        ! "$web_image_id" =~ ^sha256:[0-9a-f]{64}$ ||
        ! "$caddy_image_id" =~ ^sha256:[0-9a-f]{64}$ ||
        ! "$WSR_POSTGRES_CONTAINER_ID" =~ ^[0-9a-f]{64}$ ||
        ! "$WSR_PROMOTION_API_CONTAINER_ID" =~ ^[0-9a-f]{64}$ ||
        ! "$WSR_PROMOTION_WEB_CONTAINER_ID" =~ ^[0-9a-f]{64}$ ||
        ! "$WSR_PROMOTION_CADDY_CONTAINER_ID" =~ ^[0-9a-f]{64}$ ]]; then
    wsr_promotion_fail "incomplete-plan-evidence" "The promotion plan cannot be bound to exact backup and live resource identities."
    return 1
  fi
  candidate="$WSR_PROMOTION_CANDIDATE_PREFIX${backup_id,,}-${evidence_id,,}"
  if [[ ! "$candidate" =~ ^wall-street-receipts-generation-[0-9]{8}t[0-9]{6}z-[a-z0-9]{8}-[0-9]{8}t[0-9]{6}z-[a-z0-9]{8}$ ]]; then
    wsr_promotion_fail "invalid-candidate-generation" "The derived candidate generation identity is invalid."
    return 1
  fi
  WSR_PROMOTION_PLANNED_CANDIDATE="$candidate"
  WSR_PROMOTION_PLAN_TEXT="$(printf '%s\n' \
    "plan_version=$WSR_PROMOTION_PLAN_VERSION" \
    "state_contract_version=$WSR_PROMOTION_STATE_CONTRACT_VERSION" \
    "mode=production-data-read-only" \
    "transition_model=$WSR_PROMOTION_TRANSITION_MODEL" \
    "observation_started_utc=$WSR_PROMOTION_OBSERVATION_STARTED_UTC" \
    "observation_completed_utc=$WSR_PROMOTION_OBSERVATION_COMPLETED_UTC" \
    "backup_id=$backup_id" \
    "backup_manifest_sha256=$WSR_PROMOTION_BACKUP_MANIFEST_SHA256" \
    "archive_sha256=$WSR_PROMOTION_ARCHIVE_SHA256" \
    "restore_evidence_id=$evidence_id" \
    "restore_evidence_manifest_sha256=$WSR_PROMOTION_RESTORE_MANIFEST_SHA256" \
    "database_evidence_sha256=$WSR_PROMOTION_DATABASE_EVIDENCE_SHA256" \
    "git_sha=$git_sha" \
    "source_postgres_container_id=$WSR_POSTGRES_CONTAINER_ID" \
    "source_postgres_volume=$WSR_RECOVERY_POSTGRES_VOLUME" \
    "source_postgres_image_id=$postgres_image_id" \
    "source_api_container_id=$WSR_PROMOTION_API_CONTAINER_ID" \
    "source_api_image_id=$api_image_id" \
    "source_web_container_id=$WSR_PROMOTION_WEB_CONTAINER_ID" \
    "source_web_image_id=$web_image_id" \
    "source_caddy_container_id=$WSR_PROMOTION_CADDY_CONTAINER_ID" \
    "source_caddy_image_id=$caddy_image_id" \
    "planned_candidate_generation=$candidate" \
    "candidate_state=not-created-by-this-command" \
    "schema_compatibility=compatible-exact-recorded-release" \
    "schema_flyway_version=$flyway_version" \
    "schema_migration_count=$migration_count" \
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
  WSR_PROMOTION_PLAN_SHA256="$(printf '%s\n' "$WSR_PROMOTION_PLAN_TEXT" | sha256sum | awk '{print $1}')"
  if [[ ! "$WSR_PROMOTION_PLAN_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
    wsr_promotion_fail "plan-hash-failed" "The canonical promotion plan could not be hashed."
    return 1
  fi
}

wsr_promotion_emit_blocked() {
  local reason="${WSR_PROMOTION_FAILURE_REASON:-internal-gate-error}"
  local message="${WSR_PROMOTION_FAILURE_MESSAGE:-The promotion plan failed without a classified result.}"
  wsr_error "$message"
  printf 'PROMOTION_PLAN|blocked|%s\n' "$reason"
  printf 'PROMOTION_ACTIVATION|forbidden-by-this-command-surface\n'
  printf 'ROLLBACK_READINESS|blocked-live-transition-and-artifact-custody-not-implemented\n'
  printf 'PENDING_OFFSITE_COPY|A same-server HDD is not an off-site or offline copy.\n'
}

wsr_promotion_emit_plan() {
  local line
  printf 'PROMOTION_PLAN|complete-read-only-contract\n'
  printf 'PROMOTION_PLAN_SHA256|%s\n' "$WSR_PROMOTION_PLAN_SHA256"
  while IFS= read -r line; do
    printf 'PROMOTION_PLAN_RECORD|%s\n' "$line"
  done <<< "$WSR_PROMOTION_PLAN_TEXT"
  printf 'PROMOTION_ACTIVATION|blocked-design-prerequisites-and-operator-decisions\n'
  printf 'ROLLBACK_READINESS|blocked-live-transition-and-artifact-custody-not-implemented\n'
  printf 'PENDING_OFFSITE_COPY|A same-server HDD is not an off-site or offline copy.\n'
}

wsr_action_promotion_plan_latest() {
  wsr_promotion_reset
  WSR_PROMOTION_OBSERVATION_STARTED_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  wsr_schema_reset
  if ! wsr_evaluate_latest_schema_compatibility; then
    wsr_promotion_fail \
      "schema-${WSR_SCHEMA_FAILURE_REASON:-internal-gate-error}" \
      "${WSR_SCHEMA_FAILURE_MESSAGE:-The exact release-schema prerequisite failed.}"
    printf 'SCHEMA_COMPATIBILITY|blocked|%s\n' "${WSR_SCHEMA_FAILURE_REASON:-internal-gate-error}"
    wsr_promotion_emit_blocked
    return 1
  fi
  if ! wsr_promotion_bind_validated_evidence_digests; then
    printf 'SCHEMA_COMPATIBILITY|compatible-exact-recorded-release\n'
    wsr_promotion_emit_blocked
    return 1
  fi
  if ! wsr_promotion_validate_live_release; then
    printf 'SCHEMA_COMPATIBILITY|compatible-exact-recorded-release\n'
    wsr_promotion_emit_blocked
    return 1
  fi
  wsr_promotion_capture_observation_snapshot
  if ! wsr_promotion_revalidate_observation_snapshot ||
     ! wsr_promotion_complete_observation; then
    printf 'SCHEMA_COMPATIBILITY|compatible-exact-recorded-release\n'
    wsr_promotion_emit_blocked
    return 1
  fi
  if ! wsr_promotion_build_plan; then
    printf 'SCHEMA_COMPATIBILITY|compatible-exact-recorded-release\n'
    wsr_promotion_emit_blocked
    return 1
  fi
  printf 'SCHEMA_COMPATIBILITY|compatible-exact-recorded-release\n'
  wsr_promotion_emit_plan
}
