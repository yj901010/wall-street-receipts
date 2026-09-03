#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=deploy/home-server/recovery-common.sh
source "$script_dir/recovery-common.sh"
# shellcheck source=deploy/home-server/generation-state.sh
source "$script_dir/generation-state.sh"

WSR_RECOVERY_LOCK_FD=""
WSR_PARTIAL_PATH=""
WSR_RESTORE_CONTAINER_NAME=""
WSR_RESTORE_CONTAINER_ID=""
WSR_RESTORE_VOLUME_NAME=""
WSR_RESTORE_OWNER_TOKEN=""
WSR_RESTORE_STARTED_UTC=""
WSR_RESTORE_COMPLETED_UTC=""
WSR_RESTORE_PRE_PUBLIC_TABLE_COUNT=""
WSR_RESTORE_EVIDENCE_DISCOVERY="missing"
WSR_VALIDATED_RESTORE_EVIDENCE_ID=""
WSR_VALIDATED_RESTORE_EVIDENCE_PATH=""
WSR_VALIDATED_RESTORE_EVIDENCE_FILE=""
WSR_BACKUP_AVAILABLE_BYTES=""
WSR_BACKUP_REQUIRED_BYTES=""
WSR_DOCKER_AVAILABLE_BYTES=""
WSR_DOCKER_RESTORE_REQUIRED_BYTES=""
WSR_ALLOCATED_UTC=""
WSR_ALLOCATED_PATH=""
WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT=""
WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK=""
WSR_RESTORED_ANALYST_CALLS=""
WSR_RESTORED_ANALYST_CALL_REVISIONS=""
WSR_RESTORED_CALL_OUTCOMES=""
WSR_RESTORED_DATABASE_EVIDENCE_VERSION=""
declare -Ag WSR_RESTORE_EVIDENCE_MANIFEST=()

# shellcheck source=deploy/home-server/schema-compatibility.sh
source "$script_dir/schema-compatibility.sh"
# shellcheck source=deploy/home-server/generation-promotion.sh
source "$script_dir/generation-promotion.sh"

usage() {
  cat <<'USAGE'
Usage: recovery-production.sh -- ACTION

Allowed actions: preflight, create, status, rehearse-latest, retention-plan, schema-check-latest, promotion-plan-latest.

The command accepts no config path, backup path, Compose option, or Docker
argument. Production config is always /etc/wall-street-receipts/backup.conf.
No action restores, promotes, or deletes production data, and retention-plan never deletes.
USAGE
}

wsr_acquire_recovery_lock() {
  local lock_file="$WSR_BACKUP_ROOT/.locks/recovery.lock"
  local owner mode links
  if [[ -L "$lock_file" || (-e "$lock_file" && ! -f "$lock_file") ]]; then
    wsr_error "The recovery lock path must be a regular non-symlink file."
    return 1
  fi
  if [[ -f "$lock_file" ]]; then
    owner="$(stat -c '%u' -- "$lock_file")"
    mode="$(stat -c '%a' -- "$lock_file")"
    links="$(stat -c '%h' -- "$lock_file")"
    if [[ "$owner" != "0" || "$mode" != "600" || "$links" != "1" ]]; then
      wsr_error "The recovery lock file must be root-owned, single-linked, and mode 0600."
      return 1
    fi
  else
    (set -o noclobber; : > "$lock_file") 2>/dev/null || true
    if [[ ! -f "$lock_file" || -L "$lock_file" ]]; then
      wsr_error "The recovery lock file could not be created safely."
      return 1
    fi
    chmod 0600 -- "$lock_file"
  fi
  owner="$(stat -c '%u' -- "$lock_file")"
  mode="$(stat -c '%a' -- "$lock_file")"
  links="$(stat -c '%h' -- "$lock_file")"
  if [[ ! -f "$lock_file" || -L "$lock_file" ||
        "$owner" != "0" || "$mode" != "600" || "$links" != "1" ]]; then
    wsr_error "The recovery lock changed before it could be opened safely."
    return 1
  fi
  exec {WSR_RECOVERY_LOCK_FD}<>"$lock_file"
  if ! flock -n "$WSR_RECOVERY_LOCK_FD"; then
    wsr_error "Another recovery create or rehearsal action owns the backup-device lock."
    return 1
  fi
}

wsr_allocate_unique_utc_staging_directory() {
  local parent="$1" attempt compact_utc candidate
  local -a collisions=()

  WSR_ALLOCATED_UTC=""
  WSR_ALLOCATED_PATH=""
  for attempt in 1 2 3; do
    WSR_ALLOCATED_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    compact_utc="${WSR_ALLOCATED_UTC//-/}"
    compact_utc="${compact_utc//:/}"
    mapfile -t collisions < <(
      find "$parent" -mindepth 1 -maxdepth 1 \
        \( -name "$compact_utc-????????" -o -name ".partial-$compact_utc-????????" \) \
        -printf '%p\n'
    )
    if ((${#collisions[@]} > 0)); then
      sleep 1
      continue
    fi
    candidate="$(mktemp -d -- "$parent/.partial-${compact_utc}-XXXXXXXX")"
    mapfile -t collisions < <(
      find "$parent" -mindepth 1 -maxdepth 1 \
        \( -name "$compact_utc-????????" -o -name ".partial-$compact_utc-????????" \) \
        -printf '%p\n'
    )
    if ((${#collisions[@]} != 1)) || [[ "${collisions[0]}" != "$candidate" ]]; then
      WSR_PARTIAL_PATH="$candidate"
      wsr_error "A same-second recovery identifier appeared outside the held recovery lock."
      return 1
    fi
    WSR_ALLOCATED_PATH="$candidate"
    return 0
  done
  wsr_error "A unique UTC-second recovery identifier could not be allocated."
  return 1
}

wsr_measure_database_bytes() {
  WSR_DATABASE_BYTES="$(
    wsr_docker exec "$WSR_POSTGRES_CONTAINER_ID" \
      psql -X -q -A -t --no-password --username="$WSR_RECOVERY_DATABASE_USER" \
        --dbname="$WSR_RECOVERY_DATABASE" \
        --command="SELECT pg_database_size(current_database());"
  )"
  WSR_DATABASE_BYTES="${WSR_DATABASE_BYTES//$'\r'/}"
  WSR_DATABASE_BYTES="${WSR_DATABASE_BYTES//$'\n'/}"
  if [[ ! "$WSR_DATABASE_BYTES" =~ ^[1-9][0-9]{0,14}$ ]]; then
    wsr_error "PostgreSQL did not return a valid database-size estimate."
    return 1
  fi
}

wsr_verify_space_for_backup() {
  local database_bytes="$1" available_kib
  if [[ ! "$database_bytes" =~ ^[1-9][0-9]{0,14}$ ]]; then
    wsr_error "Backup-capacity planning requires a bounded observed database byte count."
    return 1
  fi
  available_kib="$(df -Pk -- "$WSR_BACKUP_MOUNT" | awk 'NR == 2 {print $4}')"
  [[ "$available_kib" =~ ^[1-9][0-9]*$ ]] || {
    wsr_error "The backup filesystem free-space value is invalid."
    return 1
  }
  WSR_BACKUP_AVAILABLE_BYTES=$((available_kib * 1024))
  WSR_BACKUP_REQUIRED_BYTES=$((database_bytes * 2 + 512 * 1024 * 1024))
  if ((WSR_BACKUP_REQUIRED_BYTES < 1024 * 1024 * 1024)); then
    WSR_BACKUP_REQUIRED_BYTES=$((1024 * 1024 * 1024))
  fi
  if ((WSR_BACKUP_AVAILABLE_BYTES < WSR_BACKUP_REQUIRED_BYTES)); then
    wsr_error "The separate backup device lacks dump and atomic staging headroom."
    return 1
  fi
  wsr_pass "The backup device has bounded dump and atomic staging headroom."
}

wsr_verify_space_for_dump() {
  wsr_measure_database_bytes
  wsr_verify_space_for_backup "$WSR_DATABASE_BYTES"
}

wsr_verify_space_for_restore() {
  local database_bytes="$1" available_kib
  if [[ ! "$database_bytes" =~ ^[1-9][0-9]{0,14}$ ]]; then
    wsr_error "Restore-capacity planning requires a bounded observed database byte count."
    return 1
  fi
  available_kib="$(df -Pk -- "$WSR_DOCKER_ROOT" | awk 'NR == 2 {print $4}')"
  if [[ ! "$available_kib" =~ ^[1-9][0-9]*$ ]]; then
    wsr_error "DockerRootDir free-space evidence is invalid."
    return 1
  fi
  WSR_DOCKER_AVAILABLE_BYTES=$((available_kib * 1024))
  WSR_DOCKER_RESTORE_REQUIRED_BYTES=$((database_bytes * 3 + 1024 * 1024 * 1024))
  if ((WSR_DOCKER_RESTORE_REQUIRED_BYTES < 2 * 1024 * 1024 * 1024)); then
    WSR_DOCKER_RESTORE_REQUIRED_BYTES=$((2 * 1024 * 1024 * 1024))
  fi
  if ((WSR_DOCKER_AVAILABLE_BYTES < WSR_DOCKER_RESTORE_REQUIRED_BYTES)); then
    wsr_error "DockerRootDir lacks bounded fresh-volume restore and transient index/WAL headroom."
    return 1
  fi
  wsr_pass "DockerRootDir has bounded fresh-volume restore headroom."
}

wsr_print_capacity_evidence() {
  printf 'CAPACITY_DATABASE_BYTES|%s\n' "${WSR_DATABASE_BYTES:-unavailable}"
  printf 'CAPACITY_BACKUP_AVAILABLE_BYTES|%s\n' "${WSR_BACKUP_AVAILABLE_BYTES:-unavailable}"
  printf 'CAPACITY_BACKUP_REQUIRED_BYTES|%s\n' "${WSR_BACKUP_REQUIRED_BYTES:-unavailable}"
  printf 'CAPACITY_DOCKER_AVAILABLE_BYTES|%s\n' "${WSR_DOCKER_AVAILABLE_BYTES:-unavailable}"
  printf 'CAPACITY_DOCKER_RESTORE_REQUIRED_BYTES|%s\n' "${WSR_DOCKER_RESTORE_REQUIRED_BYTES:-unavailable}"
}

wsr_partial_notice_on_exit() {
  if [[ -n "$WSR_PARTIAL_PATH" && -d "$WSR_PARTIAL_PATH" ]]; then
    wsr_warn "An incomplete, non-retained partial artifact remains for operator inspection; no completed backup was deleted."
  fi
}

wsr_cleanup_restore_resources() {
  local cleanup_failed=0 actual_id owner_label scope_label

  if [[ -n "$WSR_RESTORE_CONTAINER_NAME" ]]; then
    if [[ ! "$WSR_RESTORE_CONTAINER_NAME" =~ ^wsr-restore-[0-9]{8}t[0-9]{6}z-[a-z0-9]{8}$ ]]; then
      wsr_error "Refusing cleanup because the rehearsal container name is outside the owned namespace."
      cleanup_failed=1
    elif wsr_docker container inspect "$WSR_RESTORE_CONTAINER_NAME" >/dev/null 2>&1; then
      actual_id="$(wsr_docker container inspect --format '{{.Id}}' "$WSR_RESTORE_CONTAINER_NAME")"
      owner_label="$(wsr_docker container inspect --format "{{index .Config.Labels \"$WSR_RECOVERY_OWNER_LABEL\"}}" "$WSR_RESTORE_CONTAINER_NAME")"
      scope_label="$(wsr_docker container inspect --format "{{index .Config.Labels \"$WSR_RECOVERY_SCOPE_LABEL\"}}" "$WSR_RESTORE_CONTAINER_NAME")"
      if [[ "$actual_id" != "$WSR_RESTORE_CONTAINER_ID" ||
            "$owner_label" != "$WSR_RESTORE_OWNER_TOKEN" ||
            "$scope_label" != "$WSR_RECOVERY_SCOPE_VALUE" ]]; then
        wsr_error "Refusing cleanup because rehearsal container ownership evidence changed."
        cleanup_failed=1
      elif ! wsr_docker container rm --force --volumes -- "$WSR_RESTORE_CONTAINER_ID" >/dev/null; then
        wsr_error "The exact label-owned rehearsal container could not be removed."
        cleanup_failed=1
      fi
    fi
  fi

  if [[ -n "$WSR_RESTORE_VOLUME_NAME" ]]; then
    if [[ ! "$WSR_RESTORE_VOLUME_NAME" =~ ^wsr-restore-[0-9]{8}t[0-9]{6}z-[a-z0-9]{8}$ ]]; then
      wsr_error "Refusing cleanup because the rehearsal volume name is outside the owned namespace."
      cleanup_failed=1
    elif wsr_docker volume inspect "$WSR_RESTORE_VOLUME_NAME" >/dev/null 2>&1; then
      owner_label="$(wsr_docker volume inspect --format "{{index .Labels \"$WSR_RECOVERY_OWNER_LABEL\"}}" "$WSR_RESTORE_VOLUME_NAME")"
      scope_label="$(wsr_docker volume inspect --format "{{index .Labels \"$WSR_RECOVERY_SCOPE_LABEL\"}}" "$WSR_RESTORE_VOLUME_NAME")"
      if [[ "$owner_label" != "$WSR_RESTORE_OWNER_TOKEN" ||
            "$scope_label" != "$WSR_RECOVERY_SCOPE_VALUE" ]]; then
        wsr_error "Refusing cleanup because rehearsal volume ownership evidence changed."
        cleanup_failed=1
      elif ! wsr_docker volume rm -- "$WSR_RESTORE_VOLUME_NAME" >/dev/null; then
        wsr_error "The exact label-owned rehearsal volume could not be removed."
        cleanup_failed=1
      fi
    fi
  fi

  if ((cleanup_failed == 0)); then
    WSR_RESTORE_CONTAINER_NAME=""
    WSR_RESTORE_CONTAINER_ID=""
    WSR_RESTORE_VOLUME_NAME=""
    WSR_RESTORE_OWNER_TOKEN=""
    return 0
  fi
  return 1
}

wsr_exit_cleanup() {
  local original_status=$?
  if [[ -n "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID" || -n "$WSR_SCHEMA_INSPECTOR_CONTAINER_NAME" ]]; then
    wsr_cleanup_schema_inspector || true
  fi
  if [[ -n "$WSR_RESTORE_CONTAINER_NAME" || -n "$WSR_RESTORE_VOLUME_NAME" ]]; then
    wsr_cleanup_restore_resources || true
  fi
  wsr_partial_notice_on_exit
  return "$original_status"
}

wsr_action_preflight() {
  wsr_run_production_preflight
  wsr_verify_space_for_dump
  wsr_verify_space_for_restore "$WSR_DATABASE_BYTES"
  printf 'STORE_IDENTITY_SHA256|%s\n' "$WSR_BACKUP_STORE_IDENTITY_SHA256"
  wsr_print_capacity_evidence
  printf 'RESULT: PASS\n'
}

wsr_action_create() {
  local started_utc completed_utc partial_name backup_id final_path
  local dump inventory checksum_file manifest source_container_id partial_resolved
  local archive_bytes archive_sha256 inventory_bytes inventory_entries inventory_sha256
  local pre_api_reference pre_api_id pre_api_revision
  local pre_web_reference pre_web_id pre_web_revision
  local pre_caddy_reference pre_caddy_id pre_caddy_revision

  wsr_run_production_preflight
  wsr_prepare_storage_layout
  wsr_acquire_recovery_lock
  wsr_validate_backup_mount
  wsr_validate_production_postgres
  wsr_verify_space_for_dump
  wsr_query_postgres_metadata
  wsr_capture_release_image_metadata
  pre_api_reference="$WSR_API_IMAGE_REFERENCE"
  pre_api_id="$WSR_API_IMAGE_ID"
  pre_api_revision="$WSR_API_IMAGE_REVISION"
  pre_web_reference="$WSR_WEB_IMAGE_REFERENCE"
  pre_web_id="$WSR_WEB_IMAGE_ID"
  pre_web_revision="$WSR_WEB_IMAGE_REVISION"
  pre_caddy_reference="$WSR_CADDY_PRODUCTION_IMAGE_REFERENCE"
  pre_caddy_id="$WSR_CADDY_PRODUCTION_IMAGE_ID"
  pre_caddy_revision="$WSR_CADDY_PRODUCTION_IMAGE_REVISION"
  source_container_id="$WSR_POSTGRES_CONTAINER_ID"

  wsr_allocate_unique_utc_staging_directory "$WSR_BACKUPS_ROOT"
  started_utc="$WSR_ALLOCATED_UTC"
  WSR_PARTIAL_PATH="$WSR_ALLOCATED_PATH"
  chmod 0700 -- "$WSR_PARTIAL_PATH"
  wsr_validate_storage_directory "$WSR_PARTIAL_PATH"
  partial_name="${WSR_PARTIAL_PATH##*/}"
  backup_id="${partial_name#.partial-}"
  if ! wsr_backup_id_valid "$backup_id";
  then
    wsr_error "The owned temporary directory did not produce a valid backup ID."
    return 1
  fi
  final_path="$WSR_BACKUPS_ROOT/$backup_id"
  if [[ -e "$final_path" || -L "$final_path" ]]; then
    wsr_error "The generated completed backup path already exists."
    return 1
  fi

  dump="$WSR_PARTIAL_PATH/database.dump"
  inventory="$WSR_PARTIAL_PATH/database.inventory"
  checksum_file="$WSR_PARTIAL_PATH/database.dump.sha256"
  manifest="$WSR_PARTIAL_PATH/manifest"

  # pg_dump uses the running container's local Unix socket. No database
  # password is placed in an argument or environment variable, and the HDD is
  # never mounted into PostgreSQL or any other container.
  wsr_docker exec "$source_container_id" \
    pg_dump \
      --username="$WSR_RECOVERY_DATABASE_USER" \
      --dbname="$WSR_RECOVERY_DATABASE" \
      --format=custom \
      --compress=6 \
      --no-owner \
      --no-privileges \
      --no-password \
      > "$dump"
  [[ -s "$dump" ]] || {
    wsr_error "pg_dump produced an empty archive."
    return 1
  }

  wsr_validate_production_postgres
  if [[ "$WSR_POSTGRES_CONTAINER_ID" != "$source_container_id" ]]; then
    wsr_error "The production PostgreSQL container changed during pg_dump."
    return 1
  fi
  wsr_docker exec -i "$source_container_id" pg_restore --list < "$dump" > "$inventory"
  [[ -s "$inventory" ]] || {
    wsr_error "pg_restore --list produced no archive inventory."
    return 1
  }

  archive_bytes="$(stat -c '%s' -- "$dump")"
  archive_sha256="$(sha256sum -- "$dump" | awk '{print $1}')"
  inventory_bytes="$(stat -c '%s' -- "$inventory")"
  inventory_entries="$(awk '!/^[;[:space:]]*$/ {count++} END {print count + 0}' "$inventory")"
  inventory_sha256="$(sha256sum -- "$inventory" | awk '{print $1}')"
  if [[ ! "$inventory_entries" =~ ^[1-9][0-9]*$ ]]; then
    wsr_error "The parsed archive inventory contains no restore entries."
    return 1
  fi
  wsr_capture_release_image_metadata
  if [[ "$WSR_API_IMAGE_REFERENCE" != "$pre_api_reference" ||
        "$WSR_API_IMAGE_ID" != "$pre_api_id" ||
        "$WSR_API_IMAGE_REVISION" != "$pre_api_revision" ]]; then
    WSR_API_IMAGE_REFERENCE="unavailable"
    WSR_API_IMAGE_ID="unavailable"
    WSR_API_IMAGE_REVISION="unavailable"
    wsr_warn "API image identity changed during capture; this database backup remains valid but release-image evidence is unavailable."
  fi
  if [[ "$WSR_WEB_IMAGE_REFERENCE" != "$pre_web_reference" ||
        "$WSR_WEB_IMAGE_ID" != "$pre_web_id" ||
        "$WSR_WEB_IMAGE_REVISION" != "$pre_web_revision" ]]; then
    WSR_WEB_IMAGE_REFERENCE="unavailable"
    WSR_WEB_IMAGE_ID="unavailable"
    WSR_WEB_IMAGE_REVISION="unavailable"
    wsr_warn "Web image identity changed during capture; this database backup remains valid but release-image evidence is unavailable."
  fi
  if [[ "$WSR_CADDY_PRODUCTION_IMAGE_REFERENCE" != "$pre_caddy_reference" ||
        "$WSR_CADDY_PRODUCTION_IMAGE_ID" != "$pre_caddy_id" ||
        "$WSR_CADDY_PRODUCTION_IMAGE_REVISION" != "$pre_caddy_revision" ]]; then
    WSR_CADDY_PRODUCTION_IMAGE_REFERENCE="unavailable"
    WSR_CADDY_PRODUCTION_IMAGE_ID="unavailable"
    WSR_CADDY_PRODUCTION_IMAGE_REVISION="unavailable"
    wsr_warn "Caddy image identity changed during capture; this database backup remains valid but release-image evidence is unavailable."
  fi
  printf '%s  database.dump\n' "$archive_sha256" > "$checksum_file"
  completed_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  wsr_write_backup_manifest "$manifest" "$backup_id" "$started_utc" "$completed_utc" \
    "$archive_bytes" "$archive_sha256" "$inventory_bytes" "$inventory_entries" "$inventory_sha256"
  wsr_load_backup_manifest "$manifest"
  if [[ "${WSR_BACKUP_MANIFEST[backup_id]}" != "$backup_id" ]]; then
    wsr_error "The newly written manifest does not bind the generated backup ID."
    return 1
  fi

  chmod 0400 -- "$dump" "$inventory" "$checksum_file" "$manifest"
  wsr_fsync_path "$dump"
  wsr_fsync_path "$inventory"
  wsr_fsync_path "$checksum_file"
  wsr_fsync_path "$manifest"
  chmod 0500 -- "$WSR_PARTIAL_PATH"
  wsr_fsync_path "$WSR_PARTIAL_PATH"

  # Recheck the device identity immediately before the same-filesystem atomic
  # rename so an unplugged HDD can never redirect the completed artifact.
  wsr_validate_backup_mount
  partial_resolved="$(realpath -e -- "$WSR_PARTIAL_PATH")"
  if [[ "$partial_resolved" != "$WSR_BACKUPS_ROOT"/.partial-* ]]; then
    wsr_error "The partial artifact left the verified same-filesystem staging boundary."
    return 1
  fi
  wsr_publish_directory_no_clobber "$WSR_PARTIAL_PATH" "$final_path"
  WSR_PARTIAL_PATH=""
  wsr_fsync_path "$WSR_BACKUPS_ROOT"
  wsr_validate_completed_backup "$backup_id"
  printf 'BACKUP_CREATED|%s\n' "$backup_id"
  printf 'PENDING_OFFSITE_COPY|A same-server HDD is not an off-site or offline copy.\n'
}

wsr_load_restore_evidence_manifest() {
  local path="$1" backup_id="$2" evidence_id="$3"
  local line key value line_number=0 required normalized_started normalized_completed
  local compact_started manifest_sha expected_owner_token
  local -a required_keys=(
    schema_version backup_id rehearsal_id restore_started_utc restore_completed_utc
    backup_manifest_sha256
    archive_sha256 evidence_file evidence_bytes evidence_sha256 git_sha
    restore_owner_label restore_scope_label restore_scope_value restore_owner_token
    restore_container_name restore_container_id restore_volume_name
    restore_data_destination restore_data_mount_read_write network_mode
    published_port_count pre_restore_public_table_count restore_options
    postgres_image_reference postgres_image_id postgres_image_revision
    api_image_reference api_image_id api_image_revision web_image_reference
    web_image_id web_image_revision caddy_production_image_reference
    caddy_production_image_id caddy_production_image_revision
    restored_flyway_successful_count restored_flyway_max_installed_rank
    restored_analyst_calls restored_analyst_call_revisions restored_call_outcomes
  )
  local allowed='^(schema_version|backup_id|rehearsal_id|restore_started_utc|restore_completed_utc|backup_manifest_sha256|archive_sha256|evidence_file|evidence_bytes|evidence_sha256|git_sha|restore_owner_label|restore_scope_label|restore_scope_value|restore_owner_token|restore_container_name|restore_container_id|restore_volume_name|restore_data_destination|restore_data_mount_read_write|network_mode|published_port_count|pre_restore_public_table_count|restore_options|postgres_image_reference|postgres_image_id|postgres_image_revision|api_image_reference|api_image_id|api_image_revision|web_image_reference|web_image_id|web_image_revision|caddy_production_image_reference|caddy_production_image_id|caddy_production_image_revision|restored_flyway_successful_count|restored_flyway_max_installed_rank|restored_analyst_calls|restored_analyst_call_revisions|restored_call_outcomes)$'

  WSR_RESTORE_EVIDENCE_MANIFEST=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    if [[ ! "$line" =~ ^([a-z][a-z0-9_]*)=([A-Za-z0-9._:/+-]+)$ ]]; then
      wsr_error "Restore-evidence manifest line $line_number has invalid syntax."
      return 1
    fi
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    if [[ ! "$key" =~ $allowed || -n "${WSR_RESTORE_EVIDENCE_MANIFEST[$key]+present}" ]]; then
      wsr_error "Restore-evidence manifest contains an unknown or duplicate field."
      return 1
    fi
    WSR_RESTORE_EVIDENCE_MANIFEST["$key"]="$value"
  done < "$path"
  for required in "${required_keys[@]}"; do
    [[ -n "${WSR_RESTORE_EVIDENCE_MANIFEST[$required]+present}" ]] || {
      wsr_error "Restore-evidence manifest is missing field $required."
      return 1
    }
  done

  manifest_sha="$(sha256sum -- "$WSR_VALIDATED_BACKUP_PATH/manifest" | awk '{print $1}')"
  if [[ "${WSR_RESTORE_EVIDENCE_MANIFEST[schema_version]}" != "$WSR_RECOVERY_EVIDENCE_SCHEMA_VERSION" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[backup_id]}" != "$backup_id" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[rehearsal_id]}" != "$evidence_id" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[backup_manifest_sha256]}" != "$manifest_sha" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[archive_sha256]}" != "${WSR_BACKUP_MANIFEST[archive_sha256]}" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[evidence_file]}" != "database-evidence.txt" ]]; then
    wsr_error "Restore evidence is not bound to the exact backup manifest and archive."
    return 1
  fi
  if [[ ! "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_started_utc]}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ||
        ! "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_completed_utc]}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ||
        ! "${WSR_RESTORE_EVIDENCE_MANIFEST[evidence_bytes]}" =~ ^[1-9][0-9]*$ ||
        ! "${WSR_RESTORE_EVIDENCE_MANIFEST[evidence_sha256]}" =~ ^[0-9a-f]{64}$ ]]; then
    wsr_error "Restore-evidence timestamp, byte count, or SHA-256 has invalid syntax."
    return 1
  fi
  normalized_started="$(date -u --date="${WSR_RESTORE_EVIDENCE_MANIFEST[restore_started_utc]}" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)" || {
    wsr_error "Restore-evidence restore_started_utc is not a real UTC timestamp."
    return 1
  }
  normalized_completed="$(date -u --date="${WSR_RESTORE_EVIDENCE_MANIFEST[restore_completed_utc]}" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)" || {
    wsr_error "Restore-evidence restore_completed_utc is not a real UTC timestamp."
    return 1
  }
  compact_started="${normalized_started//-/}"
  compact_started="${compact_started//:/}"
  if [[ "$normalized_started" != "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_started_utc]}" ||
        "$normalized_completed" != "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_completed_utc]}" ||
        "$normalized_completed" < "$normalized_started" ||
        "$normalized_started" < "${WSR_BACKUP_MANIFEST[completed_utc]}" ||
        "$evidence_id" != "$compact_started"-* ]]; then
    wsr_error "Restore-evidence identity is not bound to one ordered canonical UTC interval."
    return 1
  fi

  expected_owner_token="wsr-restore-${evidence_id,,}"
  if [[ "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_owner_label]}" != "$WSR_RECOVERY_OWNER_LABEL" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_scope_label]}" != "$WSR_RECOVERY_SCOPE_LABEL" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_scope_value]}" != "$WSR_RECOVERY_SCOPE_VALUE" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_owner_token]}" != "$expected_owner_token" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_container_name]}" != "$expected_owner_token" ||
        ! "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_container_id]}" =~ ^[0-9a-f]{64}$ ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_volume_name]}" != "$expected_owner_token" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_data_destination]}" != "$WSR_RECOVERY_DATA_DESTINATION" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_data_mount_read_write]}" != "true" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[network_mode]}" != "none" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[published_port_count]}" != "0" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[pre_restore_public_table_count]}" != "0" ||
        "${WSR_RESTORE_EVIDENCE_MANIFEST[restore_options]}" != "single-transaction+exit-on-error+no-owner+no-privileges+no-password" ]]; then
    wsr_error "Restore-evidence runtime isolation, ownership, empty-target, or canonical restore-option proof is invalid."
    return 1
  fi

  for key in git_sha postgres_image_reference postgres_image_id postgres_image_revision \
    api_image_reference api_image_id api_image_revision web_image_reference \
    web_image_id web_image_revision caddy_production_image_reference \
    caddy_production_image_id caddy_production_image_revision; do
    if [[ "${WSR_RESTORE_EVIDENCE_MANIFEST[$key]}" != "${WSR_BACKUP_MANIFEST[$key]}" ]]; then
      wsr_error "Restore evidence image/Git facts differ from the strict backup manifest."
      return 1
    fi
  done
  for key in restored_flyway_successful_count restored_flyway_max_installed_rank \
    restored_analyst_calls restored_analyst_call_revisions restored_call_outcomes; do
    if [[ ! "${WSR_RESTORE_EVIDENCE_MANIFEST[$key]}" =~ ^[0-9]+$ ]]; then
      wsr_error "Restore-evidence database summary field $key is not a bounded observed count."
      return 1
    fi
  done
}

wsr_validate_restore_evidence() {
  local backup_id="$1" evidence_id="$2" evidence_dir evidence_file evidence_manifest
  local owner mode links actual_bytes actual_sha file key manifest_key observed_name
  local -a entries=()
  if ! wsr_backup_id_valid "$backup_id" || ! wsr_backup_id_valid "$evidence_id"; then
    wsr_error "Restore-evidence identifiers are invalid."
    return 1
  fi
  evidence_dir="$WSR_RESTORE_EVIDENCE_ROOT/$backup_id/$evidence_id"
  if [[ ! -d "$evidence_dir" || -L "$evidence_dir" ||
        "$(realpath -e -- "$evidence_dir")" != "$evidence_dir" ]]; then
    wsr_error "Restore evidence is not an exact non-symlink directory."
    return 1
  fi
  wsr_require_backup_filesystem_path "$evidence_dir" || return 1
  owner="$(stat -c '%u' -- "$evidence_dir")"
  mode="$(stat -c '%a' -- "$evidence_dir")"
  if [[ "$owner" != "0" || "$mode" != "500" ]]; then
    wsr_error "Completed restore-evidence directories must be root-owned and mode 0500."
    return 1
  fi
  mapfile -t entries < <(find "$evidence_dir" -mindepth 1 -maxdepth 1 -printf '%f\n' | sort)
  if ((${#entries[@]} != 2)) || [[ "${entries[*]}" != "database-evidence.txt manifest" ]]; then
    wsr_error "Restore evidence must contain exactly database-evidence.txt and manifest."
    return 1
  fi
  evidence_file="$evidence_dir/database-evidence.txt"
  evidence_manifest="$evidence_dir/manifest"
  for file in "$evidence_file" "$evidence_manifest"; do
    if [[ ! -f "$file" || -L "$file" ]]; then
      wsr_error "Restore-evidence members must be regular non-symlink files."
      return 1
    fi
    owner="$(stat -c '%u' -- "$file")"
    mode="$(stat -c '%a' -- "$file")"
    links="$(stat -c '%h' -- "$file")"
    if [[ "$owner" != "0" || "$mode" != "400" || "$links" != "1" ]]; then
      wsr_error "Restore-evidence files must be root-owned, single-linked, and mode 0400."
      return 1
    fi
    wsr_require_backup_filesystem_path "$file" || return 1
  done
  wsr_load_restore_evidence_manifest "$evidence_manifest" "$backup_id" "$evidence_id" || return 1
  actual_bytes="$(stat -c '%s' -- "$evidence_file")"
  actual_sha="$(sha256sum -- "$evidence_file" | awk '{print $1}')"
  if [[ "$actual_bytes" != "${WSR_RESTORE_EVIDENCE_MANIFEST[evidence_bytes]}" ||
        "$actual_sha" != "${WSR_RESTORE_EVIDENCE_MANIFEST[evidence_sha256]}" ]]; then
    wsr_error "Restore-evidence content length or SHA-256 does not match its manifest."
    return 1
  fi
  wsr_parse_database_evidence "$evidence_file" || return 1
  for key in \
    restored_flyway_successful_count:WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT \
    restored_flyway_max_installed_rank:WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK \
    restored_analyst_calls:WSR_RESTORED_ANALYST_CALLS \
    restored_analyst_call_revisions:WSR_RESTORED_ANALYST_CALL_REVISIONS \
    restored_call_outcomes:WSR_RESTORED_CALL_OUTCOMES; do
    manifest_key="${key%%:*}"
    observed_name="${key#*:}"
    if [[ "${WSR_RESTORE_EVIDENCE_MANIFEST[$manifest_key]}" != "${!observed_name}" ]]; then
      wsr_error "Restore-evidence manifest $manifest_key differs from its hashed database evidence."
      return 1
    fi
  done
  wsr_pass "Restore evidence $evidence_id is immutable and bound to backup $backup_id."
}

wsr_find_restore_evidence() {
  local backup_id="$1"
  local evidence_parent="$WSR_RESTORE_EVIDENCE_ROOT/$backup_id"
  local candidate latest="" entry entry_type
  local -a raw_entries=()
  WSR_RESTORE_EVIDENCE_DISCOVERY="missing"
  WSR_VALIDATED_RESTORE_EVIDENCE_ID=""
  WSR_VALIDATED_RESTORE_EVIDENCE_PATH=""
  WSR_VALIDATED_RESTORE_EVIDENCE_FILE=""
  [[ -d "$evidence_parent" && ! -L "$evidence_parent" ]] || return 1
  wsr_validate_storage_directory "$evidence_parent" || {
    WSR_RESTORE_EVIDENCE_DISCOVERY="unverified"
    return 1
  }
  mapfile -t raw_entries < <(find "$evidence_parent" -mindepth 1 -maxdepth 1 -printf '%f|%y\n')
  ((${#raw_entries[@]} > 0)) || return 1
  for entry in "${raw_entries[@]}"; do
    entry_type="${entry##*|}"
    candidate="${entry%|"$entry_type"}"
    if [[ "$entry_type" != "d" ]] || ! wsr_backup_id_valid "$candidate"; then
      WSR_RESTORE_EVIDENCE_DISCOVERY="unverified"
      return 1
    fi
    if [[ -z "$latest" || "$candidate" > "$latest" ]]; then
      latest="$candidate"
    fi
  done
  if wsr_validate_restore_evidence "$backup_id" "$latest"; then
    WSR_RESTORE_EVIDENCE_DISCOVERY="verified"
    WSR_VALIDATED_RESTORE_EVIDENCE_ID="$latest"
    WSR_VALIDATED_RESTORE_EVIDENCE_PATH="$evidence_parent/$latest"
    WSR_VALIDATED_RESTORE_EVIDENCE_FILE="$evidence_parent/$latest/database-evidence.txt"
    return 0
  fi
  WSR_RESTORE_EVIDENCE_DISCOVERY="unverified"
  return 1
}

wsr_action_status() {
  local database_status="healthy" capacity_status="unavailable" backup_id=""
  local evidence_status="missing" candidate entry entry_name entry_type
  local verified_count=0 unverified_count=0 partial_count=0
  local image_evidence_status="blocked"
  local -a raw_entries=() verified_ids=()

  wsr_run_storage_preflight
  if ! wsr_validate_production_postgres; then
    database_status="invalid-or-unavailable"
  elif wsr_measure_database_bytes &&
       wsr_verify_space_for_backup "$WSR_DATABASE_BYTES" &&
       wsr_verify_space_for_restore "$WSR_DATABASE_BYTES"; then
    capacity_status="pass"
  else
    capacity_status="blocked"
  fi
  printf 'DATABASE_STATUS|%s\n' "$database_status"
  printf 'STORE_IDENTITY_SHA256|%s\n' "$WSR_BACKUP_STORE_IDENTITY_SHA256"

  if [[ -d "$WSR_BACKUPS_ROOT" && ! -L "$WSR_BACKUPS_ROOT" ]]; then
    wsr_validate_storage_directory "$WSR_BACKUPS_ROOT"
    mapfile -t raw_entries < <(find "$WSR_BACKUPS_ROOT" -mindepth 1 -maxdepth 1 -printf '%f|%y\n')
    for entry in "${raw_entries[@]}"; do
      entry_type="${entry##*|}"
      entry_name="${entry%|"$entry_type"}"
      if [[ "$entry_type" == "d" && "$entry_name" == .partial-* ]]; then
        partial_count=$((partial_count + 1))
      elif [[ "$entry_type" == "d" ]] && wsr_backup_id_valid "$entry_name"; then
        if wsr_validate_completed_backup "$entry_name" >/dev/null 2>&1; then
          verified_ids+=("$entry_name")
        else
          unverified_count=$((unverified_count + 1))
        fi
      else
        unverified_count=$((unverified_count + 1))
      fi
    done
  fi
  if ((${#verified_ids[@]} > 0)); then
    mapfile -t verified_ids < <(printf '%s\n' "${verified_ids[@]}" | sort -r)
  fi
  verified_count="${#verified_ids[@]}"
  printf 'COMPLETED_BACKUP_COUNT|%d\n' "$verified_count"
  printf 'VERIFIED_BACKUP_COUNT|%d\n' "$verified_count"
  printf 'UNVERIFIED_BACKUP_ENTRIES|%d\n' "$unverified_count"
  printf 'INCOMPLETE_PARTIALS|%d\n' "$partial_count"
  if ((verified_count == 0)); then
    printf 'LATEST_BACKUP|none\n'
    printf 'SCHEMA_COMPATIBILITY|blocked-no-restored-backup\n'
    printf 'ROLLBACK_READINESS|blocked-no-restored-backup\n'
    printf 'IMAGE_EVIDENCE_READINESS|blocked-no-restored-backup\n'
    printf 'CAPACITY_STATUS|%s\n' "$capacity_status"
    wsr_print_capacity_evidence
    printf 'PENDING_OFFSITE_COPY|A same-server HDD is not an off-site or offline copy.\n'
    [[ "$database_status" == "healthy" && "$capacity_status" == "pass" &&
       "$unverified_count" == "0" && "$partial_count" == "0" ]]
    return
  fi

  backup_id="${verified_ids[0]}"
  wsr_validate_completed_backup "$backup_id"
  if [[ "$capacity_status" == "unavailable" ]]; then
    WSR_DATABASE_BYTES="${WSR_BACKUP_MANIFEST[database_bytes]}"
    if wsr_verify_space_for_backup "$WSR_DATABASE_BYTES" &&
       wsr_verify_space_for_restore "$WSR_DATABASE_BYTES"; then
      capacity_status="pass-from-latest-backup"
    else
      capacity_status="blocked"
    fi
  fi
  if wsr_find_restore_evidence "$backup_id"; then
    evidence_status="verified"
  else
    evidence_status="$WSR_RESTORE_EVIDENCE_DISCOVERY"
  fi
  if [[ "$evidence_status" == "verified" && "${WSR_BACKUP_MANIFEST[git_sha]}" =~ ^[0-9a-f]{40}$ &&
        "${WSR_BACKUP_MANIFEST[api_image_id]}" =~ ^sha256:[0-9a-f]{64}$ &&
        "${WSR_BACKUP_MANIFEST[web_image_id]}" =~ ^sha256:[0-9a-f]{64}$ &&
        "${WSR_BACKUP_MANIFEST[caddy_production_image_id]}" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    image_evidence_status="ready-schema-compatibility-still-required"
  fi
  printf 'LATEST_BACKUP|%s\n' "$backup_id"
  printf 'LATEST_RESTORE_EVIDENCE|%s\n' "$evidence_status"
  printf 'SCHEMA_COMPATIBILITY|not-evaluated-run-schema-check-latest\n'
  printf 'ROLLBACK_READINESS|blocked-schema-not-evaluated-and-promotion-gates-not-implemented\n'
  printf 'IMAGE_EVIDENCE_READINESS|%s\n' "$image_evidence_status"
  printf 'CAPACITY_STATUS|%s\n' "$capacity_status"
  wsr_print_capacity_evidence
  printf 'ROLLBACK_POLICY|git sha and image id evidence never authorize production database restore; restored Flyway compatibility is required.\n'
  printf 'PENDING_OFFSITE_COPY|A same-server HDD is not an off-site or offline copy.\n'
  [[ "$database_status" == "healthy" && "$capacity_status" == "pass" &&
     "$unverified_count" == "0" && "$partial_count" == "0" ]]
}

wsr_wait_for_restore_postgres() {
  local attempt status running
  for ((attempt = 1; attempt <= 60; attempt++)); do
    running="$(wsr_docker inspect --format '{{.State.Running}}' "$WSR_RESTORE_CONTAINER_ID" 2>/dev/null || true)"
    status="$(wsr_docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$WSR_RESTORE_CONTAINER_ID" 2>/dev/null || true)"
    if [[ "$running" == "true" && "$status" == "healthy" ]]; then
      return 0
    fi
    if [[ "$running" == "false" ]]; then
      break
    fi
    sleep 1
  done
  wsr_error "The isolated restore PostgreSQL container did not become healthy."
  return 1
}

wsr_validate_restore_volume() {
  local actual_name driver owner_label scope_label options_count mountpoint

  if [[ ! "$WSR_RESTORE_VOLUME_NAME" =~ ^wsr-restore-[0-9]{8}t[0-9]{6}z-[a-z0-9]{8}$ ||
        "$WSR_RESTORE_VOLUME_NAME" == "$WSR_RECOVERY_POSTGRES_VOLUME" ]]; then
    wsr_error "The rehearsal volume name is outside the random recovery namespace."
    return 1
  fi

  actual_name="$(wsr_docker volume inspect --format '{{.Name}}' "$WSR_RESTORE_VOLUME_NAME")"
  driver="$(wsr_docker volume inspect --format '{{.Driver}}' "$WSR_RESTORE_VOLUME_NAME")"
  owner_label="$(wsr_docker volume inspect --format "{{index .Labels \"$WSR_RECOVERY_OWNER_LABEL\"}}" "$WSR_RESTORE_VOLUME_NAME")"
  scope_label="$(wsr_docker volume inspect --format "{{index .Labels \"$WSR_RECOVERY_SCOPE_LABEL\"}}" "$WSR_RESTORE_VOLUME_NAME")"
  options_count="$(wsr_docker volume inspect --format '{{len .Options}}' "$WSR_RESTORE_VOLUME_NAME")"
  mountpoint="$(wsr_docker volume inspect --format '{{.Mountpoint}}' "$WSR_RESTORE_VOLUME_NAME")"
  mountpoint="$(realpath -e -- "$mountpoint")" || {
    wsr_error "The rehearsal volume mountpoint cannot be resolved."
    return 1
  }
  wsr_require_docker_root_filesystem_path "$mountpoint" || return 1

  if [[ "$actual_name" != "$WSR_RESTORE_VOLUME_NAME" ||
        "$driver" != "local" ||
        "$owner_label" != "$WSR_RESTORE_OWNER_TOKEN" ||
        "$scope_label" != "$WSR_RECOVERY_SCOPE_VALUE" ||
        "$options_count" != "0" ||
        "$mountpoint" != "$WSR_DOCKER_ROOT"/volumes/*/_data ]]; then
    wsr_error "The rehearsal volume failed its exact name, labels, driver, options, or DockerRootDir contract."
    return 1
  fi
  return 0
}

wsr_validate_restore_runtime() {
  local actual_id actual_name owner_label scope_label network_mode port_binding_count image_id
  local -a mounts=() networks=()

  wsr_validate_restore_volume || return 1
  actual_id="$(wsr_docker container inspect --format '{{.Id}}' "$WSR_RESTORE_CONTAINER_ID")"
  actual_name="$(wsr_docker container inspect --format '{{.Name}}' "$WSR_RESTORE_CONTAINER_ID")"
  owner_label="$(wsr_docker container inspect --format "{{index .Config.Labels \"$WSR_RECOVERY_OWNER_LABEL\"}}" "$WSR_RESTORE_CONTAINER_ID")"
  scope_label="$(wsr_docker container inspect --format "{{index .Config.Labels \"$WSR_RECOVERY_SCOPE_LABEL\"}}" "$WSR_RESTORE_CONTAINER_ID")"
  network_mode="$(wsr_docker container inspect --format '{{.HostConfig.NetworkMode}}' "$WSR_RESTORE_CONTAINER_ID")"
  port_binding_count="$(wsr_docker container inspect --format '{{len .HostConfig.PortBindings}}' "$WSR_RESTORE_CONTAINER_ID")"
  image_id="$(wsr_docker container inspect --format '{{.Image}}' "$WSR_RESTORE_CONTAINER_ID")"
  mapfile -t mounts < <(
    wsr_docker container inspect --format \
      '{{range .Mounts}}{{printf "%s|%s|%s|%t\n" .Type .Name .Destination .RW}}{{end}}' \
      "$WSR_RESTORE_CONTAINER_ID"
  )
  # The dollar-prefixed names below belong to Docker's Go template, not Bash.
  # shellcheck disable=SC2016
  mapfile -t networks < <(
    wsr_docker container inspect --format \
      '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' \
      "$WSR_RESTORE_CONTAINER_ID"
  )
  if ((${#networks[@]} > 1)) || {
    ((${#networks[@]} == 1)) && [[ "${networks[0]}" != "none" ]]
  }; then
    wsr_error "The rehearsal runtime has an unexpected live Docker network endpoint."
    return 1
  fi
  if [[ "$actual_id" != "$WSR_RESTORE_CONTAINER_ID" ||
        "$actual_name" != "/$WSR_RESTORE_CONTAINER_NAME" ||
        "$owner_label" != "$WSR_RESTORE_OWNER_TOKEN" ||
        "$scope_label" != "$WSR_RECOVERY_SCOPE_VALUE" ||
        "$network_mode" != "none" || "$port_binding_count" != "0" ||
        "$image_id" != "${WSR_BACKUP_MANIFEST[postgres_image_id]}" ||
        ${#mounts[@]} -ne 1 ||
        "${mounts[0]:-}" != "volume|$WSR_RESTORE_VOLUME_NAME|$WSR_RECOVERY_DATA_DESTINATION|true" ]]; then
    wsr_error "The rehearsal runtime failed its exact ID, name, labels, image, volume, no-network, or no-port contract."
    return 1
  fi
  return 0
}

wsr_database_evidence_single_value() {
  local path="$1" key="$2"
  local -a values=()
  mapfile -t values < <(awk -F '|' -v expected_key="$key" \
    '$1 == expected_key && NF == 2 {print $2}' "$path")
  if ((${#values[@]} != 1)) || [[ ! "${values[0]}" =~ ^[0-9]+$ ]]; then
    wsr_error "Restored database evidence must contain one numeric $key value."
    return 1
  fi
  printf '%s\n' "${values[0]}"
}

wsr_database_evidence_table_rows() {
  local path="$1" table_name="$2"
  local -a values=()
  mapfile -t values < <(awk -F '|' -v expected_table="public.$table_name" \
    '$1 == "table_rows" && $2 == expected_table && NF == 3 {print $3}' "$path")
  if ((${#values[@]} != 1)) || [[ ! "${values[0]}" =~ ^[0-9]+$ ]]; then
    wsr_error "Restored database evidence must contain one numeric public.$table_name table inventory row."
    return 1
  fi
  printf '%s\n' "${values[0]}"
}

wsr_parse_database_evidence() {
  local path="$1" flyway_row_count flyway_max table_row_count
  local analyst_calls_rows analyst_call_revisions_rows call_outcomes_rows

  if ! awk -F '|' '
    $1 == "evidence_version" {
      if (NR != 1 || seen_singleton[$1]++ || NF != 2 || ($2 != "1" && $2 != "2")) exit 1
      evidence_version = $2
      next
    }
    $1 == "database_name" {
      if (seen_singleton[$1]++ || NF != 2 || $2 != "wsr") exit 1
      next
    }
    $1 == "database_encoding" {
      if (seen_singleton[$1]++ || NF != 2 || $2 !~ /^[A-Z0-9_-]+$/) exit 1
      next
    }
    $1 == "flyway_successful_count" || $1 == "flyway_max_installed_rank" ||
    $1 == "analyst_calls" || $1 == "analyst_call_revisions" ||
    $1 == "call_outcomes" {
      if (seen_singleton[$1]++ || NF != 2 || $2 !~ /^[0-9]+$/) exit 1
      next
    }
    $1 == "flyway" {
      if ($2 !~ /^[1-9][0-9]*$/ || seen_flyway[$2]++) exit 1
      flyway_count++
      if (evidence_version == "1") {
        if (NF != 5 || $5 != "true") exit 1
      } else if (evidence_version == "2") {
        if (NF != 8 || $2 != flyway_count || $3 != flyway_count ||
            $4 !~ /^([0-9a-f][0-9a-f])+$/ || $5 != "SQL" ||
            $6 !~ /^([0-9a-f][0-9a-f])+$/ ||
            $7 !~ /^-?(0|[1-9][0-9]*)$/ || $7 < -2147483648 || $7 > 2147483647 ||
            $8 != "true" || seen_version[$3]++) exit 1
      } else {
        exit 1
      }
      next
    }
    $1 == "platform_metadata" {
      if (NF != 3 || $2 == "" || seen_metadata[$2]++) exit 1
      metadata_count++
      next
    }
    $1 == "table_rows" {
      if (NF != 3 || $2 !~ /^public[.][a-z][a-z0-9_]*$/ || $3 !~ /^[0-9]+$/ || seen[$2]++) exit 1
      next
    }
    { exit 1 }
    END {
      required[1] = "evidence_version"
      required[2] = "database_name"
      required[3] = "database_encoding"
      required[4] = "flyway_successful_count"
      required[5] = "flyway_max_installed_rank"
      required[6] = "analyst_calls"
      required[7] = "analyst_call_revisions"
      required[8] = "call_outcomes"
      if (NR == 0) exit 1
      for (i = 1; i <= 8; i++) if (seen_singleton[required[i]] != 1) exit 1
      if (metadata_count < 1) exit 1
    }
  ' "$path"; then
    wsr_error "Restored database evidence contains an unknown, malformed, duplicate-table, or failed row."
    return 1
  fi

  WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT="$(
    wsr_database_evidence_single_value "$path" flyway_successful_count
  )" || return 1
  WSR_RESTORED_DATABASE_EVIDENCE_VERSION="$(
    wsr_database_evidence_single_value "$path" evidence_version
  )" || return 1
  WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK="$(
    wsr_database_evidence_single_value "$path" flyway_max_installed_rank
  )" || return 1
  WSR_RESTORED_ANALYST_CALLS="$(
    wsr_database_evidence_single_value "$path" analyst_calls
  )" || return 1
  WSR_RESTORED_ANALYST_CALL_REVISIONS="$(
    wsr_database_evidence_single_value "$path" analyst_call_revisions
  )" || return 1
  WSR_RESTORED_CALL_OUTCOMES="$(
    wsr_database_evidence_single_value "$path" call_outcomes
  )" || return 1

  flyway_row_count="$(awk -F '|' '$1 == "flyway" {count++} END {print count + 0}' "$path")"
  flyway_max="$(awk -F '|' '$1 == "flyway" && $2 > max {max=$2} END {print max + 0}' "$path")"
  table_row_count="$(awk -F '|' '$1 == "table_rows" {count++} END {print count + 0}' "$path")"
  analyst_calls_rows="$(
    wsr_database_evidence_table_rows "$path" analyst_calls
  )" || return 1
  analyst_call_revisions_rows="$(
    wsr_database_evidence_table_rows "$path" analyst_call_revisions
  )" || return 1
  call_outcomes_rows="$(
    wsr_database_evidence_table_rows "$path" call_outcomes
  )" || return 1
  if [[ "$flyway_row_count" != "$WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT" ||
        "$flyway_max" != "$WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK" ||
        "$analyst_calls_rows" != "$WSR_RESTORED_ANALYST_CALLS" ||
        "$analyst_call_revisions_rows" != "$WSR_RESTORED_ANALYST_CALL_REVISIONS" ||
        "$call_outcomes_rows" != "$WSR_RESTORED_CALL_OUTCOMES" ||
        ! "$table_row_count" =~ ^[1-9][0-9]*$ ]]; then
    wsr_error "Restored Flyway summary or complete public-table inventory is internally inconsistent."
    return 1
  fi
}

wsr_write_restore_evidence_manifest() {
  local path="$1" backup_id="$2" rehearsal_id="$3" restore_started_utc="$4"
  local restore_completed_utc="$5" manifest_sha="$6" evidence_bytes="$7"
  local evidence_sha="$8"
  {
    printf 'schema_version=%s\n' "$WSR_RECOVERY_EVIDENCE_SCHEMA_VERSION"
    printf 'backup_id=%s\n' "$backup_id"
    printf 'rehearsal_id=%s\n' "$rehearsal_id"
    printf 'restore_started_utc=%s\n' "$restore_started_utc"
    printf 'restore_completed_utc=%s\n' "$restore_completed_utc"
    printf 'backup_manifest_sha256=%s\n' "$manifest_sha"
    printf 'archive_sha256=%s\n' "${WSR_BACKUP_MANIFEST[archive_sha256]}"
    printf 'evidence_file=database-evidence.txt\n'
    printf 'evidence_bytes=%s\n' "$evidence_bytes"
    printf 'evidence_sha256=%s\n' "$evidence_sha"
    printf 'git_sha=%s\n' "${WSR_BACKUP_MANIFEST[git_sha]}"
    printf 'restore_owner_label=%s\n' "$WSR_RECOVERY_OWNER_LABEL"
    printf 'restore_scope_label=%s\n' "$WSR_RECOVERY_SCOPE_LABEL"
    printf 'restore_scope_value=%s\n' "$WSR_RECOVERY_SCOPE_VALUE"
    printf 'restore_owner_token=%s\n' "$WSR_RESTORE_OWNER_TOKEN"
    printf 'restore_container_name=%s\n' "$WSR_RESTORE_CONTAINER_NAME"
    printf 'restore_container_id=%s\n' "$WSR_RESTORE_CONTAINER_ID"
    printf 'restore_volume_name=%s\n' "$WSR_RESTORE_VOLUME_NAME"
    printf 'restore_data_destination=%s\n' "$WSR_RECOVERY_DATA_DESTINATION"
    printf 'restore_data_mount_read_write=true\n'
    printf 'network_mode=none\n'
    printf 'published_port_count=0\n'
    printf 'pre_restore_public_table_count=%s\n' "$WSR_RESTORE_PRE_PUBLIC_TABLE_COUNT"
    printf 'restore_options=single-transaction+exit-on-error+no-owner+no-privileges+no-password\n'
    printf 'postgres_image_reference=%s\n' "${WSR_BACKUP_MANIFEST[postgres_image_reference]}"
    printf 'postgres_image_id=%s\n' "${WSR_BACKUP_MANIFEST[postgres_image_id]}"
    printf 'postgres_image_revision=%s\n' "${WSR_BACKUP_MANIFEST[postgres_image_revision]}"
    printf 'api_image_reference=%s\n' "${WSR_BACKUP_MANIFEST[api_image_reference]}"
    printf 'api_image_id=%s\n' "${WSR_BACKUP_MANIFEST[api_image_id]}"
    printf 'api_image_revision=%s\n' "${WSR_BACKUP_MANIFEST[api_image_revision]}"
    printf 'web_image_reference=%s\n' "${WSR_BACKUP_MANIFEST[web_image_reference]}"
    printf 'web_image_id=%s\n' "${WSR_BACKUP_MANIFEST[web_image_id]}"
    printf 'web_image_revision=%s\n' "${WSR_BACKUP_MANIFEST[web_image_revision]}"
    printf 'caddy_production_image_reference=%s\n' "${WSR_BACKUP_MANIFEST[caddy_production_image_reference]}"
    printf 'caddy_production_image_id=%s\n' "${WSR_BACKUP_MANIFEST[caddy_production_image_id]}"
    printf 'caddy_production_image_revision=%s\n' "${WSR_BACKUP_MANIFEST[caddy_production_image_revision]}"
    printf 'restored_flyway_successful_count=%s\n' "$WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT"
    printf 'restored_flyway_max_installed_rank=%s\n' "$WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK"
    printf 'restored_analyst_calls=%s\n' "$WSR_RESTORED_ANALYST_CALLS"
    printf 'restored_analyst_call_revisions=%s\n' "$WSR_RESTORED_ANALYST_CALL_REVISIONS"
    printf 'restored_call_outcomes=%s\n' "$WSR_RESTORED_CALL_OUTCOMES"
  } > "$path"
}

wsr_action_rehearse_latest() {
  local backup_id artifact dump original_inventory evidence_parent evidence_partial evidence_name
  local evidence_final evidence_file evidence_manifest evidence_partial_resolved
  local actual_inventory_sha actual_inventory_entries manifest_sha evidence_bytes evidence_sha
  local created_volume created_container restored_version empty_public_tables

  wsr_run_storage_preflight
  wsr_prepare_storage_layout
  wsr_acquire_recovery_lock
  backup_id="$(wsr_latest_backup_id)" || {
    wsr_error "No completed backup exists for restore rehearsal."
    return 1
  }
  wsr_validate_completed_backup "$backup_id"
  WSR_DATABASE_BYTES="${WSR_BACKUP_MANIFEST[database_bytes]}"
  wsr_verify_space_for_restore "$WSR_DATABASE_BYTES"
  artifact="$WSR_VALIDATED_BACKUP_PATH"
  dump="$artifact/database.dump"
  original_inventory="$artifact/database.inventory"

  if ! wsr_docker image inspect "${WSR_BACKUP_MANIFEST[postgres_image_id]}" >/dev/null 2>&1; then
    wsr_error "The exact PostgreSQL image ID recorded by the backup is not available locally."
    return 1
  fi

  evidence_parent="$WSR_RESTORE_EVIDENCE_ROOT/$backup_id"
  wsr_ensure_storage_directory "$evidence_parent"
  wsr_allocate_unique_utc_staging_directory "$evidence_parent"
  WSR_RESTORE_STARTED_UTC="$WSR_ALLOCATED_UTC"
  evidence_partial="$WSR_ALLOCATED_PATH"
  chmod 0700 -- "$evidence_partial"
  wsr_validate_storage_directory "$evidence_partial"
  WSR_PARTIAL_PATH="$evidence_partial"
  evidence_name="${evidence_partial##*/}"
  evidence_name="${evidence_name#.partial-}"
  if ! wsr_backup_id_valid "$evidence_name"; then
    wsr_error "The owned restore-evidence directory name is invalid."
    return 1
  fi
  evidence_final="$evidence_parent/$evidence_name"
  WSR_RESTORE_OWNER_TOKEN="wsr-restore-${evidence_name,,}"
  WSR_RESTORE_CONTAINER_NAME="$WSR_RESTORE_OWNER_TOKEN"
  WSR_RESTORE_VOLUME_NAME="$WSR_RESTORE_OWNER_TOKEN"

  if wsr_docker container inspect "$WSR_RESTORE_CONTAINER_NAME" >/dev/null 2>&1 ||
     wsr_docker volume inspect "$WSR_RESTORE_VOLUME_NAME" >/dev/null 2>&1; then
    wsr_error "The random rehearsal resource name unexpectedly already exists."
    return 1
  fi
  created_volume="$(
    wsr_docker volume create \
      --label "$WSR_RECOVERY_OWNER_LABEL=$WSR_RESTORE_OWNER_TOKEN" \
      --label "$WSR_RECOVERY_SCOPE_LABEL=$WSR_RECOVERY_SCOPE_VALUE" \
      "$WSR_RESTORE_VOLUME_NAME"
  )"
  [[ "$created_volume" == "$WSR_RESTORE_VOLUME_NAME" ]] || {
    wsr_error "Docker did not create the exact random rehearsal volume."
    return 1
  }
  # Docker volume create is idempotent for an existing name. Inspect every
  # ownership and storage invariant before the volume may be attached.
  wsr_validate_restore_volume

  # The selected backup HDD is never mounted into this container. The archive
  # crosses stdin only. The fresh random volume has no ports and network none.
  created_container="$(
    wsr_docker container create --pull=never \
      --name "$WSR_RESTORE_CONTAINER_NAME" \
      --label "$WSR_RECOVERY_OWNER_LABEL=$WSR_RESTORE_OWNER_TOKEN" \
      --label "$WSR_RECOVERY_SCOPE_LABEL=$WSR_RECOVERY_SCOPE_VALUE" \
      --network none \
      --pids-limit 128 \
      --memory 1g \
      --cpus 1 \
      --shm-size 256m \
      --health-cmd 'pg_isready -U wsr -d wsr' \
      --health-interval 2s \
      --health-timeout 2s \
      --health-retries 30 \
      --env POSTGRES_DB=wsr \
      --env POSTGRES_USER=wsr \
      --env POSTGRES_HOST_AUTH_METHOD=trust \
      --env POSTGRES_INITDB_ARGS=--data-checksums \
      --env PGTZ=UTC \
      --env TZ=UTC \
      --mount "type=volume,source=$WSR_RESTORE_VOLUME_NAME,target=$WSR_RECOVERY_DATA_DESTINATION" \
      "${WSR_BACKUP_MANIFEST[postgres_image_id]}"
  )"
  if [[ ! "$created_container" =~ ^[0-9a-f]{64}$ ]]; then
    wsr_error "Docker did not return an exact rehearsal container ID."
    return 1
  fi
  WSR_RESTORE_CONTAINER_ID="$created_container"
  # Validate the stopped container by its exact daemon-issued ID before it can
  # execute any image entrypoint against the new volume.
  wsr_validate_restore_runtime
  wsr_docker container start "$WSR_RESTORE_CONTAINER_ID" >/dev/null
  wsr_validate_restore_runtime
  wsr_wait_for_restore_postgres

  empty_public_tables="$(
    wsr_docker exec "$WSR_RESTORE_CONTAINER_ID" \
      psql -X -q -A -t --no-password --username="$WSR_RECOVERY_DATABASE_USER" \
        --dbname="$WSR_RECOVERY_DATABASE" \
        --command="SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname = 'public';"
  )"
  empty_public_tables="${empty_public_tables//$'\r'/}"
  empty_public_tables="${empty_public_tables//$'\n'/}"
  if [[ "$empty_public_tables" != "0" ]]; then
    wsr_error "Fresh-target proof failed: the isolated database already contains public application tables before pg_restore."
    return 1
  fi
  WSR_RESTORE_PRE_PUBLIC_TABLE_COUNT="$empty_public_tables"
  wsr_pass "The random isolated restore target contains zero public tables before pg_restore."

  actual_inventory_sha="$(
    wsr_docker exec -i "$WSR_RESTORE_CONTAINER_ID" pg_restore --list < "$dump" | sha256sum | awk '{print $1}'
  )"
  actual_inventory_entries="$(
    wsr_docker exec -i "$WSR_RESTORE_CONTAINER_ID" pg_restore --list < "$dump" |
      awk '!/^[;[:space:]]*$/ {count++} END {print count + 0}'
  )"
  if [[ "$actual_inventory_sha" != "${WSR_BACKUP_MANIFEST[archive_inventory_sha256]}" ||
        "$actual_inventory_entries" != "${WSR_BACKUP_MANIFEST[archive_inventory_entries]}" ||
        "$(sha256sum -- "$original_inventory" | awk '{print $1}')" != "$actual_inventory_sha" ]]; then
    wsr_error "The exact-image pg_restore inventory does not match the backup manifest."
    return 1
  fi

  wsr_docker exec -i "$WSR_RESTORE_CONTAINER_ID" \
    pg_restore \
      --username="$WSR_RECOVERY_DATABASE_USER" \
      --dbname="$WSR_RECOVERY_DATABASE" \
      --no-password \
      --single-transaction \
      --exit-on-error \
      --no-owner \
      --no-privileges \
      < "$dump"

  restored_version="$(
    wsr_docker exec "$WSR_RESTORE_CONTAINER_ID" \
      psql -X -q -A -t --no-password --username="$WSR_RECOVERY_DATABASE_USER" \
        --dbname="$WSR_RECOVERY_DATABASE" --command='SHOW server_version_num;'
  )"
  restored_version="${restored_version//$'\r'/}"
  restored_version="${restored_version//$'\n'/}"
  if [[ "$restored_version" != "${WSR_BACKUP_MANIFEST[postgres_server_version_num]}" ]]; then
    wsr_error "The restored PostgreSQL server version differs from the backup manifest."
    return 1
  fi

  evidence_file="$evidence_partial/database-evidence.txt"
  evidence_manifest="$evidence_partial/manifest"
  # A running container can be attached to a network after creation. Recheck
  # the live endpoint, port, image, label, and exact-mount set at the moment
  # evidence is captured rather than relying on its pre-restore state.
  wsr_validate_restore_runtime
  wsr_docker exec -i "$WSR_RESTORE_CONTAINER_ID" \
    psql -X -q -A -t --no-password --username="$WSR_RECOVERY_DATABASE_USER" \
      --dbname="$WSR_RECOVERY_DATABASE" \
      < "$script_dir/database-evidence.sql" > "$evidence_file"
  wsr_parse_database_evidence "$evidence_file"

  WSR_RESTORE_COMPLETED_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  manifest_sha="$(sha256sum -- "$artifact/manifest" | awk '{print $1}')"
  evidence_bytes="$(stat -c '%s' -- "$evidence_file")"
  evidence_sha="$(sha256sum -- "$evidence_file" | awk '{print $1}')"
  wsr_write_restore_evidence_manifest "$evidence_manifest" "$backup_id" "$evidence_name" \
    "$WSR_RESTORE_STARTED_UTC" "$WSR_RESTORE_COMPLETED_UTC" "$manifest_sha" \
    "$evidence_bytes" "$evidence_sha"
  chmod 0400 -- "$evidence_file" "$evidence_manifest"
  wsr_fsync_path "$evidence_file"
  wsr_fsync_path "$evidence_manifest"
  chmod 0500 -- "$evidence_partial"
  wsr_fsync_path "$evidence_partial"

  if ! wsr_cleanup_restore_resources; then
    wsr_error "Restore succeeded, but owned disposable Docker cleanup did not complete; evidence remains partial."
    return 1
  fi
  wsr_validate_backup_mount
  evidence_partial_resolved="$(realpath -e -- "$evidence_partial")"
  if [[ "$evidence_partial_resolved" != "$evidence_parent"/.partial-* ]]; then
    wsr_error "Restore evidence left the verified same-filesystem staging boundary."
    return 1
  fi
  wsr_validate_storage_directory "$evidence_parent"
  wsr_publish_directory_no_clobber "$evidence_partial" "$evidence_final"
  WSR_PARTIAL_PATH=""
  wsr_fsync_path "$evidence_parent"
  wsr_validate_restore_evidence "$backup_id" "$evidence_name"
  printf 'RESTORE_REHEARSAL_PASSED|%s|%s\n' "$backup_id" "$evidence_name"
  printf 'SCHEMA_COMPATIBILITY|not-evaluated-run-schema-check-latest\n'
  printf 'PRODUCTION_RESTORE|forbidden-by-this-command-surface\n'
}

wsr_sort_retention_ids() {
  (($# > 0)) || return 0
  printf '%s\n' "$@" | LC_ALL=C sort -r
}

wsr_emit_retention_selection() {
  local backup_bytes_name="$1" image_evidence_ready_id="$2"
  shift 2
  local -n backup_bytes_ref="$backup_bytes_name"
  local candidate stamp iso_date day_key week_key month_key reason selected_bytes
  local daily_count=0 weekly_count=0 monthly_count=0 keep_bytes=0 candidate_bytes=0
  local -A daily_seen=() weekly_seen=() monthly_seen=()

  for candidate in "$@"; do
    stamp="${candidate%%-*}"
    iso_date="${stamp:0:4}-${stamp:4:2}-${stamp:6:2} ${stamp:9:2}:${stamp:11:2}:${stamp:13:2} UTC"
    day_key="$(date -u --date="$iso_date" +%Y-%m-%d)" || {
      wsr_error "A completed backup ID contains an invalid UTC timestamp."
      return 1
    }
    week_key="$(date -u --date="$iso_date" +%G-W%V)"
    month_key="$(date -u --date="$iso_date" +%Y-%m)"
    reason=""
    if ((daily_count < WSR_RETENTION_DAILY)) && [[ -z "${daily_seen[$day_key]+present}" ]]; then
      daily_seen["$day_key"]=1
      daily_count=$((daily_count + 1))
      reason="daily"
    fi
    if ((weekly_count < WSR_RETENTION_WEEKLY)) && [[ -z "${weekly_seen[$week_key]+present}" ]]; then
      weekly_seen["$week_key"]=1
      weekly_count=$((weekly_count + 1))
      reason="${reason:+$reason,}weekly"
    fi
    if ((monthly_count < WSR_RETENTION_MONTHLY)) && [[ -z "${monthly_seen[$month_key]+present}" ]]; then
      monthly_seen["$month_key"]=1
      monthly_count=$((monthly_count + 1))
      reason="${reason:+$reason,}monthly"
    fi
    if [[ "$candidate" == "$image_evidence_ready_id" ]]; then
      reason="${reason:+$reason,}image-evidence-ready"
    fi
    selected_bytes="${backup_bytes_ref[$candidate]}"
    if [[ -n "$reason" ]]; then
      keep_bytes=$((keep_bytes + 10#$selected_bytes))
      printf 'KEEP|%s|%s\n' "$candidate" "$reason"
    else
      candidate_bytes=$((candidate_bytes + 10#$selected_bytes))
      printf 'CANDIDATE_ONLY|%s|outside-policy-windows\n' "$candidate"
    fi
  done
  printf 'RETENTION_ESTIMATED_KEEP_BYTES|%d\n' "$keep_bytes"
  printf 'RETENTION_ESTIMATED_CANDIDATE_BYTES|%d\n' "$candidate_bytes"
}

wsr_action_retention_plan() {
  local candidate entry entry_name entry_type
  local archive_bytes manifest_sha image_evidence_ready_id=""
  local unverified_count=0 incomplete_count=0 verified_bytes=0
  local -a backup_ids=()
  local -a raw_entries=()
  local -A backup_bytes=() backup_manifest_sha=()

  wsr_run_storage_preflight
  printf 'RETENTION_POLICY|daily|%d\n' "$WSR_RETENTION_DAILY"
  printf 'RETENTION_POLICY|weekly|%d\n' "$WSR_RETENTION_WEEKLY"
  printf 'RETENTION_POLICY|monthly|%d\n' "$WSR_RETENTION_MONTHLY"
  printf 'RETENTION_ACTION|read-only-no-delete\n'
  printf 'RETENTION_STORE_IDENTITY_SHA256|%s\n' "$WSR_BACKUP_STORE_IDENTITY_SHA256"
  if [[ ! -d "$WSR_BACKUPS_ROOT" || -L "$WSR_BACKUPS_ROOT" ]]; then
    printf 'RETENTION_BACKUPS|0\n'
    printf 'RETENTION_VERIFIED_BACKUPS|0\n'
    printf 'RETENTION_VERIFIED_ARCHIVE_BYTES|0\n'
    printf 'RETENTION_ESTIMATED_KEEP_BYTES|0\n'
    printf 'RETENTION_ESTIMATED_CANDIDATE_BYTES|0\n'
    printf 'RETENTION_UNVERIFIED_ENTRIES|0\n'
    printf 'RETENTION_INCOMPLETE_PARTIALS|0\n'
    printf 'PENDING_OFFSITE_COPY|This plan never deletes and does not create an off-site copy.\n'
    return 0
  fi
  wsr_validate_storage_directory "$WSR_BACKUPS_ROOT"
  mapfile -t raw_entries < <(find "$WSR_BACKUPS_ROOT" -mindepth 1 -maxdepth 1 -printf '%f|%y\n')
  for entry in "${raw_entries[@]}"; do
    entry_type="${entry##*|}"
    entry_name="${entry%|"$entry_type"}"
    if [[ "$entry_type" == "d" && "$entry_name" == .partial-* ]]; then
      incomplete_count=$((incomplete_count + 1))
    elif [[ "$entry_type" == "d" ]] && wsr_backup_id_valid "$entry_name"; then
      if wsr_validate_completed_backup "$entry_name"; then
        backup_ids+=("$entry_name")
        archive_bytes="${WSR_BACKUP_MANIFEST[archive_bytes]}"
        manifest_sha="$(sha256sum -- "$WSR_VALIDATED_BACKUP_PATH/manifest" | awk '{print $1}')"
        # The map is consumed through the explicit nameref in wsr_emit_retention_selection.
        # shellcheck disable=SC2034
        backup_bytes["$entry_name"]="$archive_bytes"
        backup_manifest_sha["$entry_name"]="$manifest_sha"
        verified_bytes=$((verified_bytes + 10#$archive_bytes))
      else
        unverified_count=$((unverified_count + 1))
      fi
    else
      unverified_count=$((unverified_count + 1))
    fi
  done
  if ((${#backup_ids[@]} > 0)); then
    mapfile -t backup_ids < <(wsr_sort_retention_ids "${backup_ids[@]}")
  fi
  printf 'RETENTION_BACKUPS|%d\n' "${#backup_ids[@]}"
  printf 'RETENTION_VERIFIED_BACKUPS|%d\n' "${#backup_ids[@]}"
  printf 'RETENTION_VERIFIED_ARCHIVE_BYTES|%d\n' "$verified_bytes"
  printf 'RETENTION_UNVERIFIED_ENTRIES|%d\n' "$unverified_count"
  printf 'RETENTION_INCOMPLETE_PARTIALS|%d\n' "$incomplete_count"
  for candidate in "${backup_ids[@]}"; do
    printf 'RETENTION_INPUT_MANIFEST|%s|%s\n' "$candidate" "${backup_manifest_sha[$candidate]}"
  done

  for candidate in "${backup_ids[@]}"; do
    if ! wsr_validate_completed_backup "$candidate" >/dev/null; then
      wsr_error "A previously verified recovery point changed during retention planning."
      return 1
    fi
    if wsr_find_restore_evidence "$candidate" >/dev/null 2>&1 &&
       [[ "${WSR_BACKUP_MANIFEST[git_sha]}" =~ ^[0-9a-f]{40}$ &&
          "${WSR_BACKUP_MANIFEST[api_image_id]}" =~ ^sha256:[0-9a-f]{64}$ &&
          "${WSR_BACKUP_MANIFEST[web_image_id]}" =~ ^sha256:[0-9a-f]{64}$ &&
          "${WSR_BACKUP_MANIFEST[caddy_production_image_id]}" =~ ^sha256:[0-9a-f]{64}$ ]]; then
      image_evidence_ready_id="$candidate"
      break
    fi
  done
  printf 'RETENTION_NEWEST_IMAGE_EVIDENCE_READY|%s\n' "${image_evidence_ready_id:-none}"

  wsr_emit_retention_selection backup_bytes "$image_evidence_ready_id" "${backup_ids[@]}"
  if ((incomplete_count > 0)); then
    printf 'PENDING_PARTIAL_ARTIFACTS|%d|operator-review-no-automatic-delete\n' "$incomplete_count"
  fi
  printf 'PENDING_OFFSITE_COPY|This plan never deletes and does not create an off-site copy.\n'
  if ((unverified_count > 0)); then
    wsr_error "Retention planning excluded unverified or unknown backup-root entries."
    return 1
  fi
}

main() {
  local action
  if (($# != 2)) || [[ "$1" != "--" ]]; then
    usage >&2
    return 64
  fi
  action="$2"
  case "$action" in
    preflight)
      wsr_generation_acquire_operation_lock shared
      wsr_generation_require_operation_lock shared
      wsr_action_preflight
      ;;
    create)
      wsr_generation_acquire_operation_lock exclusive
      wsr_generation_require_operation_lock exclusive
      wsr_action_create
      ;;
    status)
      wsr_generation_acquire_operation_lock shared
      wsr_generation_require_operation_lock shared
      wsr_action_status
      ;;
    rehearse-latest)
      wsr_generation_acquire_operation_lock exclusive
      wsr_generation_require_operation_lock exclusive
      wsr_action_rehearse_latest
      ;;
    retention-plan)
      wsr_generation_acquire_operation_lock shared
      wsr_generation_require_operation_lock shared
      wsr_action_retention_plan
      ;;
    schema-check-latest)
      wsr_generation_acquire_operation_lock exclusive
      wsr_generation_require_operation_lock exclusive
      wsr_action_schema_check_latest
      ;;
    promotion-plan-latest)
      wsr_generation_acquire_operation_lock exclusive
      wsr_generation_require_operation_lock exclusive
      wsr_action_promotion_plan_latest
      ;;
    *)
      wsr_error "Action is not allowlisted."
      usage >&2
      return 64
      ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap wsr_exit_cleanup EXIT
  main "$@"
fi
