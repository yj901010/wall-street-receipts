#!/usr/bin/env bash
# Source-only ADR-048 exact release-schema compatibility policy.
#
# The policy never checks out a ref, reads HEAD, fetches an object, connects an
# application to a database, or changes a production volume. It compares the
# latest hash-bound restore evidence with the exact API image and exact commit
# object recorded when that backup was created.
if [[ -n "${WSR_SCHEMA_COMPATIBILITY_LOADED:-}" ]]; then
  # shellcheck disable=SC2317
  return 0 2>/dev/null || exit 0
fi
WSR_SCHEMA_COMPATIBILITY_LOADED=1

readonly WSR_SCHEMA_INVENTORY_VERSION="1"
readonly WSR_SCHEMA_FLYWAY_VERSION="11.7.2"
readonly WSR_SCHEMA_MIGRATION_ROOT="apps/api/src/main/resources/db/migration"
readonly WSR_SCHEMA_INSPECTOR_SCOPE="schema-compatibility"
readonly WSR_SCHEMA_MAX_MIGRATIONS=512
readonly WSR_SCHEMA_MAX_INVENTORY_BYTES=1048576
readonly WSR_SCHEMA_INSPECTOR_MEMORY_BYTES=402653184
readonly WSR_SCHEMA_INSPECTOR_PIDS=128
readonly WSR_SCHEMA_INSPECTOR_NANO_CPUS=1000000000
readonly WSR_SCHEMA_INSPECTOR_TIMEOUT_SECONDS=30

WSR_SCHEMA_REPOSITORY_ROOT="$(cd -- "$script_dir/../.." && pwd -P)"
WSR_SCHEMA_INSPECTOR_CONTAINER_NAME=""
WSR_SCHEMA_INSPECTOR_CONTAINER_ID=""
WSR_SCHEMA_INSPECTOR_OWNER_TOKEN=""
WSR_SCHEMA_INVENTORY_OUTPUT=""
WSR_SCHEMA_FAILURE_REASON=""
WSR_SCHEMA_FAILURE_MESSAGE=""
WSR_SCHEMA_BACKUP_ID=""
WSR_SCHEMA_GIT_SHA=""
WSR_SCHEMA_MIGRATION_COUNT=""
declare -ag WSR_SCHEMA_GIT_ENV=()
declare -Ag WSR_SCHEMA_IMAGE_DESCRIPTION=()
declare -Ag WSR_SCHEMA_IMAGE_SCRIPT=()
declare -Ag WSR_SCHEMA_IMAGE_CHECKSUM=()
declare -Ag WSR_SCHEMA_IMAGE_FILENAME=()
declare -Ag WSR_SCHEMA_IMAGE_SHA256=()
declare -Ag WSR_SCHEMA_IMAGE_BYTES=()

wsr_schema_fail() {
  WSR_SCHEMA_FAILURE_REASON="$1"
  WSR_SCHEMA_FAILURE_MESSAGE="$2"
  return 1
}

wsr_schema_reset() {
  WSR_SCHEMA_FAILURE_REASON=""
  WSR_SCHEMA_FAILURE_MESSAGE=""
  WSR_SCHEMA_BACKUP_ID=""
  WSR_SCHEMA_GIT_SHA=""
  WSR_SCHEMA_MIGRATION_COUNT=""
  WSR_SCHEMA_INVENTORY_OUTPUT=""
  WSR_SCHEMA_IMAGE_DESCRIPTION=()
  WSR_SCHEMA_IMAGE_SCRIPT=()
  WSR_SCHEMA_IMAGE_CHECKSUM=()
  WSR_SCHEMA_IMAGE_FILENAME=()
  WSR_SCHEMA_IMAGE_SHA256=()
  WSR_SCHEMA_IMAGE_BYTES=()
}

wsr_schema_emit_blocked() {
  local reason="${WSR_SCHEMA_FAILURE_REASON:-internal-gate-error}"
  local message="${WSR_SCHEMA_FAILURE_MESSAGE:-The schema gate failed without a classified result.}"
  wsr_error "$message"
  printf 'SCHEMA_COMPATIBILITY|blocked|%s\n' "$reason"
  printf 'ROLLBACK_READINESS|blocked-promotion-and-artifact-gates-not-implemented\n'
  printf 'PENDING_OFFSITE_COPY|A same-server HDD is not an off-site or offline copy.\n'
}

wsr_schema_initialize_git() {
  local name
  local -a unset_arguments=()

  if ! command -v git >/dev/null 2>&1; then
    wsr_schema_fail "git-tool-unavailable" "The fixed release-schema action requires the local Git executable."
    return 1
  fi
  while IFS='=' read -r name _; do
    if [[ "$name" == GIT_* ]]; then
      unset_arguments+=(--unset="$name")
    fi
  done < <(env)
  WSR_SCHEMA_GIT_ENV=(
    env "${unset_arguments[@]}"
    LC_ALL=C
    GIT_CONFIG_NOSYSTEM=1
    GIT_CONFIG_GLOBAL=/dev/null
    GIT_OPTIONAL_LOCKS=0
    GIT_NO_LAZY_FETCH=1
    GIT_NO_REPLACE_OBJECTS=1
    GIT_TERMINAL_PROMPT=0
  )
}

wsr_schema_git() {
  "${WSR_SCHEMA_GIT_ENV[@]}" git --no-replace-objects \
    -C "$WSR_SCHEMA_REPOSITORY_ROOT" "$@"
}

wsr_schema_validate_git_commit() {
  local git_sha="$1" top_level top_level_canonical repository_root_canonical
  local object_format object_type resolved git_common_dir
  local alternates http_alternates
  local partial_clone_config config_status

  if [[ ! "$git_sha" =~ ^[0-9a-f]{40}$ ]]; then
    wsr_schema_fail "git-object-unavailable" "Schema checks accept only the full lowercase backup-recorded Git commit identity."
    return 1
  fi
  wsr_schema_initialize_git || return 1
  top_level="$(wsr_schema_git rev-parse --show-toplevel 2>/dev/null)" || {
    wsr_schema_fail "git-object-unavailable" "The fixed deployment repository is not a readable Git worktree."
    return 1
  }
  top_level_canonical="$(cd -- "$top_level" 2>/dev/null && pwd -P)" || {
    wsr_schema_fail "git-object-unavailable" "The Git worktree root could not be resolved without following an unavailable path."
    return 1
  }
  repository_root_canonical="$(cd -- "$WSR_SCHEMA_REPOSITORY_ROOT" 2>/dev/null && pwd -P)" || {
    wsr_schema_fail "git-object-unavailable" "The fixed deployment checkout could not be resolved."
    return 1
  }
  if [[ "$top_level_canonical" != "$repository_root_canonical" ]]; then
    wsr_schema_fail "git-object-unavailable" "Git resolved a repository outside the fixed deployment checkout."
    return 1
  fi
  object_format="$(wsr_schema_git rev-parse --show-object-format 2>/dev/null)" || {
    wsr_schema_fail "git-object-unavailable" "The repository object format could not be resolved."
    return 1
  }
  if [[ "$object_format" != "sha1" ]]; then
    wsr_schema_fail "git-object-unavailable" "The 40-character backup identity requires a SHA-1 Git object store."
    return 1
  fi
  if partial_clone_config="$(
    wsr_schema_git config --local --no-includes --get-regexp \
      '^(extensions[.]partialclone|remote[.].*[.]promisor|include[.]|include[Ii]f[.])' 2>/dev/null
  )"; then
    config_status=0
  else
    config_status=$?
  fi
  if [[ "$config_status" != "0" && "$config_status" != "1" ]]; then
    wsr_schema_fail "git-object-unavailable" "The repository partial/promisor configuration could not be inspected safely."
    return 1
  fi
  if [[ -n "$partial_clone_config" ]]; then
    wsr_schema_fail "git-object-unavailable" "Partial/promisor or included repository configuration is refused because schema checks never fetch or import object-store settings."
    return 1
  fi
  git_common_dir="$(
    wsr_schema_git rev-parse --path-format=absolute --git-common-dir 2>/dev/null
  )" || {
    wsr_schema_fail "git-object-unavailable" "The exact Git common object directory could not be resolved."
    return 1
  }
  if [[ ( "$git_common_dir" != /* && ! "$git_common_dir" =~ ^[A-Za-z]:/ ) ||
        ! -d "$git_common_dir" || -L "$git_common_dir" ]]; then
    wsr_schema_fail "git-object-unavailable" "The Git common directory is not one exact local non-symlink directory."
    return 1
  fi
  git_common_dir="$(cd -- "$git_common_dir" 2>/dev/null && pwd -P)" || {
    wsr_schema_fail "git-object-unavailable" "The Git common directory could not be resolved exactly."
    return 1
  }
  alternates="$git_common_dir/objects/info/alternates"
  http_alternates="$git_common_dir/objects/info/http-alternates"
  if [[ -e "$alternates" || -L "$alternates" ||
        -e "$http_alternates" || -L "$http_alternates" ]]; then
    wsr_schema_fail "git-object-unavailable" "Alternate Git object stores are outside the fixed repository trust boundary."
    return 1
  fi
  object_type="$(wsr_schema_git cat-file -t "$git_sha" 2>/dev/null)" || {
    wsr_schema_fail "git-object-unavailable" "The backup-recorded Git commit object is not available locally; no fetch was attempted."
    return 1
  }
  resolved="$(wsr_schema_git rev-parse --verify "$git_sha^{commit}" 2>/dev/null)" || {
    wsr_schema_fail "git-object-unavailable" "The backup-recorded Git identity is not a commit object."
    return 1
  }
  if [[ "$object_type" != "commit" || "$resolved" != "$git_sha" ]]; then
    wsr_schema_fail "git-object-unavailable" "The exact backup-recorded object did not resolve to itself as a commit."
    return 1
  fi
}

wsr_validate_schema_inspector() {
  local expected_state="$1" evidence
  evidence="$(
    wsr_docker container inspect --format \
      '{{.Id}}|{{.Name}}|{{.Image}}|{{.State.Status}}|{{.HostConfig.NetworkMode}}|{{.HostConfig.ReadonlyRootfs}}|{{len .Mounts}}|{{len .HostConfig.PortBindings}}|{{.Config.OpenStdin}}|{{.Config.Tty}}|{{json .HostConfig.CapDrop}}|{{json .HostConfig.SecurityOpt}}|{{.HostConfig.Memory}}|{{.HostConfig.MemorySwap}}|{{.HostConfig.PidsLimit}}|{{.HostConfig.NanoCpus}}|{{.HostConfig.LogConfig.Type}}|{{.Config.StopTimeout}}|{{json .Config.Cmd}}|{{index .Config.Labels "com.wallstreetreceipts.recovery.owner"}}|{{index .Config.Labels "com.wallstreetreceipts.recovery.scope"}}' \
      "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID" 2>/dev/null
  )" || {
    wsr_schema_fail "image-inspector-failed" "The exact API-image inspector container could not be inspected."
    return 1
  }
  if [[ "$evidence" != "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID|/$WSR_SCHEMA_INSPECTOR_CONTAINER_NAME|${WSR_BACKUP_MANIFEST[api_image_id]}|$expected_state|none|true|0|0|false|false|[\"ALL\"]|[\"no-new-privileges\"]|$WSR_SCHEMA_INSPECTOR_MEMORY_BYTES|$WSR_SCHEMA_INSPECTOR_MEMORY_BYTES|$WSR_SCHEMA_INSPECTOR_PIDS|$WSR_SCHEMA_INSPECTOR_NANO_CPUS|none|5|[\"--wsr-release-schema-inventory\"]|$WSR_SCHEMA_INSPECTOR_OWNER_TOKEN|$WSR_SCHEMA_INSPECTOR_SCOPE" ]]; then
    wsr_schema_fail "image-inspector-failed" "The API-image inspector lost its exact image, ownership, isolation, or command contract."
    return 1
  fi
}

wsr_cleanup_schema_inspector() {
  local actual_name owner_label scope_label cleanup_failed=0
  if [[ -z "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID" ]]; then
    WSR_SCHEMA_INSPECTOR_CONTAINER_NAME=""
    WSR_SCHEMA_INSPECTOR_OWNER_TOKEN=""
    return 0
  fi
  if wsr_docker container inspect "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID" >/dev/null 2>&1; then
    actual_name="$(wsr_docker container inspect --format '{{.Name}}' "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID")"
    owner_label="$(wsr_docker container inspect --format '{{index .Config.Labels "com.wallstreetreceipts.recovery.owner"}}' "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID")"
    scope_label="$(wsr_docker container inspect --format '{{index .Config.Labels "com.wallstreetreceipts.recovery.scope"}}' "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID")"
    if [[ "$actual_name" != "/$WSR_SCHEMA_INSPECTOR_CONTAINER_NAME" ||
          "$owner_label" != "$WSR_SCHEMA_INSPECTOR_OWNER_TOKEN" ||
          "$scope_label" != "$WSR_SCHEMA_INSPECTOR_SCOPE" ]]; then
      wsr_error "Refusing cleanup because API-image inspector ownership evidence changed."
      cleanup_failed=1
    elif ! wsr_docker container rm --force --volumes -- "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID" >/dev/null; then
      wsr_error "The exact label-owned API-image inspector could not be removed."
      cleanup_failed=1
    fi
  fi
  if ((cleanup_failed == 0)); then
    WSR_SCHEMA_INSPECTOR_CONTAINER_NAME=""
    WSR_SCHEMA_INSPECTOR_CONTAINER_ID=""
    WSR_SCHEMA_INSPECTOR_OWNER_TOKEN=""
    return 0
  fi
  return 1
}

wsr_run_schema_image_inventory() {
  local attempt candidate_name created_id="" exit_code image_id

  if ! command -v timeout >/dev/null 2>&1 || ! command -v head >/dev/null 2>&1; then
    wsr_schema_fail "image-inspector-failed" "The bounded API-image inspector requires GNU timeout and head."
    return 1
  fi
  image_id="${WSR_BACKUP_MANIFEST[api_image_id]}"
  if ! wsr_docker image inspect "$image_id" >/dev/null 2>&1 ||
     [[ "$(wsr_docker image inspect --format '{{.Id}}' "$image_id" 2>/dev/null || true)" != "$image_id" ]]; then
    wsr_schema_fail "api-image-object-unavailable" "The exact API image ID recorded by the backup is not available locally; no pull was attempted."
    return 1
  fi

  for attempt in 1 2 3; do
    candidate_name="wsr-schema-${WSR_SCHEMA_BACKUP_ID,,}-$$-${RANDOM}${RANDOM}"
    if created_id="$(
      wsr_docker container create \
        --name "$candidate_name" \
        --pull never \
        --network none \
        --read-only \
        --cap-drop ALL \
        --security-opt no-new-privileges \
        --memory 384m \
        --memory-swap 384m \
        --pids-limit "$WSR_SCHEMA_INSPECTOR_PIDS" \
        --cpus 1.0 \
        --stop-timeout 5 \
        --log-driver none \
        --label "com.wallstreetreceipts.recovery.owner=$candidate_name" \
        --label "com.wallstreetreceipts.recovery.scope=$WSR_SCHEMA_INSPECTOR_SCOPE" \
        -- "$image_id" --wsr-release-schema-inventory 2>/dev/null
    )"; then
      WSR_SCHEMA_INSPECTOR_CONTAINER_NAME="$candidate_name"
      WSR_SCHEMA_INSPECTOR_CONTAINER_ID="$created_id"
      WSR_SCHEMA_INSPECTOR_OWNER_TOKEN="$candidate_name"
      break
    fi
  done
  if [[ ! "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID" =~ ^[0-9a-f]{64}$ ]]; then
    wsr_schema_fail "image-inspector-failed" "A unique isolated inspector could not be created from the exact API image."
    return 1
  fi
  wsr_validate_schema_inspector "created" || return 1
  if ! WSR_SCHEMA_INVENTORY_OUTPUT="$(
    set -o pipefail
    timeout --signal=TERM --kill-after=5s "$WSR_SCHEMA_INSPECTOR_TIMEOUT_SECONDS" \
      "${WSR_DOCKER_ENV[@]}" docker container start --attach -- \
        "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID" 2>&1 |
      head --bytes="$((WSR_SCHEMA_MAX_INVENTORY_BYTES + 1))"
  )"; then
    wsr_schema_fail "image-inspector-failed" "The exact API image inventory failed, exceeded its output/time bound, or does not support the reserved command."
    return 1
  fi
  if ((${#WSR_SCHEMA_INVENTORY_OUTPUT} > WSR_SCHEMA_MAX_INVENTORY_BYTES)); then
    wsr_schema_fail "image-inspector-failed" "The exact API image inventory exceeded its output bound."
    return 1
  fi
  exit_code="$(wsr_docker container inspect --format '{{.State.ExitCode}}' "$WSR_SCHEMA_INSPECTOR_CONTAINER_ID" 2>/dev/null || true)"
  if [[ "$exit_code" != "0" ]]; then
    wsr_schema_fail "image-inspector-failed" "The exact API-image schema inventory command exited nonzero."
    return 1
  fi
  wsr_validate_schema_inspector "exited" || return 1
  if ! wsr_cleanup_schema_inspector; then
    wsr_schema_fail "cleanup-failed" "Schema comparison is blocked because exact inspector cleanup did not complete."
    return 1
  fi
}

wsr_parse_schema_image_inventory() {
  local line line_number=0 field_count rank version description type script checksum
  local filename raw_sha bytes filename_version migration_count=0
  local -a fields=()

  if ((${#WSR_SCHEMA_INVENTORY_OUTPUT} == 0 || ${#WSR_SCHEMA_INVENTORY_OUTPUT} > WSR_SCHEMA_MAX_INVENTORY_BYTES)) ||
     [[ "$WSR_SCHEMA_INVENTORY_OUTPUT" == *$'\r'* ]]; then
    wsr_schema_fail "image-inspector-failed" "The API-image inventory is empty, oversized, or not canonical LF text."
    return 1
  fi
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    if [[ "$line_number" == "1" ]]; then
      [[ "$line" == "inventory_version|$WSR_SCHEMA_INVENTORY_VERSION" ]] || {
        wsr_schema_fail "image-inspector-failed" "The API-image inventory version is unsupported."
        return 1
      }
      continue
    fi
    if [[ "$line_number" == "2" ]]; then
      [[ "$line" == "flyway_version|$WSR_SCHEMA_FLYWAY_VERSION" ]] || {
        wsr_schema_fail "image-inspector-failed" "The exact API image carries an unsupported Flyway engine version."
        return 1
      }
      continue
    fi
    IFS='|' read -r -a fields <<< "$line"
    field_count="${#fields[@]}"
    if [[ "$field_count" != "10" || "${fields[0]}" != "migration" ]]; then
      wsr_schema_fail "image-inspector-failed" "The API-image inventory contains an unknown or malformed row."
      return 1
    fi
    rank="${fields[1]}"
    version="${fields[2]}"
    description="${fields[3]}"
    type="${fields[4]}"
    script="${fields[5]}"
    checksum="${fields[6]}"
    filename="${fields[7]}"
    raw_sha="${fields[8]}"
    bytes="${fields[9]}"
    migration_count=$((migration_count + 1))
    if [[ ! "$filename" =~ ^V([1-9][0-9]{0,2})__([a-z0-9]+(_[a-z0-9]+)*)[.]sql$ ]]; then
      wsr_schema_fail "image-inspector-failed" "The API-image inventory contains a noncanonical migration filename."
      return 1
    fi
    filename_version="${BASH_REMATCH[1]}"
    if [[ ! "$rank" =~ ^[1-9][0-9]{0,2}$ || "$rank" != "$migration_count" ||
          "$version" != "$rank" || ! "$description" =~ ^([0-9a-f]{2})+$ ||
          "$type" != "SQL" || ! "$script" =~ ^([0-9a-f]{2})+$ ||
          ! "$checksum" =~ ^-?(0|[1-9][0-9]{0,9})$ || "$checksum" == "-0" ||
          ! "$raw_sha" =~ ^[0-9a-f]{64}$ ||
          ! "$bytes" =~ ^[1-9][0-9]{0,8}$ ]]; then
      wsr_schema_fail "image-inspector-failed" "The API-image inventory violates the versioned-SQL canonical boundary."
      return 1
    fi
    if [[ "$filename_version" != "$version" ]] ||
       ((checksum < -2147483648 || checksum > 2147483647 || bytes > 16777216 || migration_count > WSR_SCHEMA_MAX_MIGRATIONS)); then
      wsr_schema_fail "image-inspector-failed" "The API-image inventory contains a noncanonical version, checksum, size, or count."
      return 1
    fi
    WSR_SCHEMA_IMAGE_DESCRIPTION["$rank"]="$description"
    WSR_SCHEMA_IMAGE_SCRIPT["$rank"]="$script"
    WSR_SCHEMA_IMAGE_CHECKSUM["$rank"]="$checksum"
    WSR_SCHEMA_IMAGE_FILENAME["$rank"]="$filename"
    WSR_SCHEMA_IMAGE_SHA256["$rank"]="$raw_sha"
    WSR_SCHEMA_IMAGE_BYTES["$rank"]="$bytes"
  done <<< "$WSR_SCHEMA_INVENTORY_OUTPUT"
  if ((line_number < 3 || migration_count < 1)); then
    wsr_schema_fail "image-inspector-failed" "The API image contains no supported migration inventory."
    return 1
  fi
  WSR_SCHEMA_MIGRATION_COUNT="$migration_count"
}

wsr_compare_git_migration_tree() {
  local git_sha="$1" row metadata path mode object_type blob relative filename version
  local index git_sha256 git_bytes
  local -a tree_rows=()
  local -A git_blob=() git_filename=()

  mapfile -d '' -t tree_rows < <(
    wsr_schema_git ls-tree -rz --full-tree "$git_sha" -- "$WSR_SCHEMA_MIGRATION_ROOT"
  )
  if ((${#tree_rows[@]} == 0 || ${#tree_rows[@]} > WSR_SCHEMA_MAX_MIGRATIONS)); then
    wsr_schema_fail "unsupported-migration-kind" "The exact Git commit has no supported bounded migration tree."
    return 1
  fi
  for row in "${tree_rows[@]}"; do
    if [[ "$row" != *$'\t'* ]]; then
      wsr_schema_fail "unsupported-migration-kind" "Git returned a malformed migration-tree entry."
      return 1
    fi
    metadata="${row%%$'\t'*}"
    path="${row#*$'\t'}"
    read -r mode object_type blob <<< "$metadata"
    if [[ "$path" != "$WSR_SCHEMA_MIGRATION_ROOT/"* ]]; then
      wsr_schema_fail "unsupported-migration-kind" "A migration escaped the fixed Git tree root."
      return 1
    fi
    relative="${path#"$WSR_SCHEMA_MIGRATION_ROOT/"}"
    filename="$relative"
    if [[ "$mode" != "100644" || "$object_type" != "blob" || ! "$blob" =~ ^[0-9a-f]{40}$ ||
          ! "$filename" =~ ^V([1-9][0-9]{0,2})__([a-z0-9]+(_[a-z0-9]+)*)[.]sql$ ]]; then
      wsr_schema_fail "unsupported-migration-kind" "Only flat, regular, canonical Vn__name.sql Git blobs are supported."
      return 1
    fi
    version="${BASH_REMATCH[1]}"
    if [[ -n "${git_blob[$version]+present}" ]]; then
      wsr_schema_fail "unsupported-migration-kind" "The exact Git commit contains a duplicate migration version."
      return 1
    fi
    git_blob["$version"]="$blob"
    git_filename["$version"]="$filename"
  done
  if ((${#tree_rows[@]} != WSR_SCHEMA_MIGRATION_COUNT)); then
    wsr_schema_fail "image-git-resource-mismatch" "The exact Git tree and API image contain different migration counts."
    return 1
  fi
  for ((index = 1; index <= WSR_SCHEMA_MIGRATION_COUNT; index++)); do
    if [[ -z "${git_blob[$index]+present}" ||
          "${git_filename[$index]}" != "${WSR_SCHEMA_IMAGE_FILENAME[$index]}" ]]; then
      wsr_schema_fail "image-git-resource-mismatch" "The exact Git tree and API image disagree on migration ordering or script identity."
      return 1
    fi
    git_bytes="$(wsr_schema_git cat-file -s "${git_blob[$index]}" 2>/dev/null)" || {
      wsr_schema_fail "git-object-unavailable" "A required migration blob is incomplete; no fetch was attempted."
      return 1
    }
    git_sha256="$(
      set -o pipefail
      wsr_schema_git cat-file blob "${git_blob[$index]}" | sha256sum | awk '{print $1}'
    )" || {
      wsr_schema_fail "git-object-unavailable" "A required migration blob could not be read exactly; no fetch was attempted."
      return 1
    }
    if [[ "$git_bytes" != "${WSR_SCHEMA_IMAGE_BYTES[$index]}" ||
          "$git_sha256" != "${WSR_SCHEMA_IMAGE_SHA256[$index]}" ]]; then
      wsr_schema_fail "image-git-resource-mismatch" "A packaged API migration differs byte-for-byte from the backup-recorded Git commit."
      return 1
    fi
  done
}

wsr_compare_restored_flyway_history() {
  local kind rank version description type script checksum success extra count=0

  while IFS='|' read -r kind rank version description type script checksum success extra; do
    [[ "$kind" == "flyway" ]] || continue
    count=$((count + 1))
    if [[ -n "$extra" || "$rank" != "$count" || "$version" != "$rank" ||
          "$description" != "${WSR_SCHEMA_IMAGE_DESCRIPTION[$rank]:-missing}" ||
          "$type" != "SQL" || "$script" != "${WSR_SCHEMA_IMAGE_SCRIPT[$rank]:-missing}" ||
          "$checksum" != "${WSR_SCHEMA_IMAGE_CHECKSUM[$rank]:-missing}" ||
          "$success" != "true" ]]; then
      wsr_schema_fail "flyway-row-order-checksum-mismatch" "Restored Flyway history differs from the exact API image in rank, version, description, type, script, or checksum."
      return 1
    fi
  done < "$WSR_VALIDATED_RESTORE_EVIDENCE_FILE"
  if [[ "$count" != "$WSR_SCHEMA_MIGRATION_COUNT" ||
        "$count" != "$WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT" ||
        "$count" != "$WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK" ]]; then
    wsr_schema_fail "flyway-row-order-checksum-mismatch" "Restored Flyway history and the exact release contain different migration counts."
    return 1
  fi
}

wsr_evaluate_latest_schema_compatibility() {
  local backup_id git_sha

  wsr_run_storage_preflight || {
    wsr_schema_fail "host-or-store-preflight-failed" "Schema compatibility requires the verified local Docker daemon and backup mount."
    return 1
  }
  backup_id="$(wsr_latest_backup_id)" || {
    wsr_schema_fail "backup-unavailable" "No completed backup is available for schema comparison."
    return 1
  }
  wsr_validate_completed_backup "$backup_id" || {
    wsr_schema_fail "backup-unavailable" "The latest backup failed strict immutable validation."
    return 1
  }
  WSR_SCHEMA_BACKUP_ID="$backup_id"
  if ! wsr_find_restore_evidence "$backup_id"; then
    wsr_schema_fail "restore-evidence-v2-unavailable" "The latest backup has no strictly verified restore evidence."
    return 1
  fi
  if [[ "$WSR_RESTORED_DATABASE_EVIDENCE_VERSION" != "2" ]]; then
    wsr_schema_fail "restore-evidence-v2-unavailable" "Legacy database evidence remains valid as a restore proof but is insufficient for exact schema compatibility. Run a new rehearsal."
    return 1
  fi
  git_sha="${WSR_BACKUP_MANIFEST[git_sha]}"
  if [[ ! "$git_sha" =~ ^[0-9a-f]{40}$ ||
        ! "${WSR_BACKUP_MANIFEST[api_image_id]}" =~ ^sha256:[0-9a-f]{64}$ ||
        ! "${WSR_BACKUP_MANIFEST[web_image_id]}" =~ ^sha256:[0-9a-f]{64}$ ||
        ! "${WSR_BACKUP_MANIFEST[caddy_production_image_id]}" =~ ^sha256:[0-9a-f]{64}$ ||
        "${WSR_BACKUP_MANIFEST[api_image_revision]}" != "$git_sha" ||
        "${WSR_BACKUP_MANIFEST[web_image_revision]}" != "$git_sha" ||
        "${WSR_BACKUP_MANIFEST[caddy_production_image_revision]}" != "$git_sha" ]]; then
    wsr_schema_fail "release-image-evidence-incomplete" "The backup does not bind all release images to one exact Git commit."
    return 1
  fi
  WSR_SCHEMA_GIT_SHA="$git_sha"
  wsr_schema_validate_git_commit "$git_sha" || return 1
  wsr_run_schema_image_inventory || return 1
  wsr_parse_schema_image_inventory || return 1
  wsr_compare_git_migration_tree "$git_sha" || return 1
  wsr_compare_restored_flyway_history || return 1
}

wsr_action_schema_check_latest() {
  wsr_schema_reset
  if ! wsr_evaluate_latest_schema_compatibility; then
    wsr_schema_emit_blocked
    return 1
  fi
  printf 'SCHEMA_COMPATIBILITY|compatible-exact-recorded-release\n'
  printf 'SCHEMA_BACKUP|%s\n' "$WSR_SCHEMA_BACKUP_ID"
  printf 'SCHEMA_RESTORE_EVIDENCE|%s\n' "$WSR_VALIDATED_RESTORE_EVIDENCE_ID"
  printf 'SCHEMA_GIT_COMMIT|%s\n' "$WSR_SCHEMA_GIT_SHA"
  printf 'SCHEMA_FLYWAY_VERSION|%s\n' "$WSR_SCHEMA_FLYWAY_VERSION"
  printf 'SCHEMA_MIGRATION_COUNT|%s\n' "$WSR_SCHEMA_MIGRATION_COUNT"
  printf 'ROLLBACK_READINESS|blocked-promotion-and-artifact-gates-not-implemented\n'
  printf 'PENDING_OFFSITE_COPY|A same-server HDD is not an off-site or offline copy.\n'
}
