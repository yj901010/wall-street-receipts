#!/usr/bin/env bash
# shellcheck disable=SC2034  # This source-only policy exports state to its entry points.

# Shared, source-only ADR-047 recovery policy. Production entry points set
# strict shell options themselves so tests may source individual functions.
# Database backups intentionally exclude every password/secret and exclude
# Caddy key state (`caddy-data` and `caddy-config`). Actual api/web/
# caddy-production/postgres image IDs and OCI revisions provide release and
# Git SHA evidence without reading Git or making rollback a backup side effect.
if [[ -n "${WSR_RECOVERY_COMMON_LOADED:-}" ]]; then
  # shellcheck disable=SC2317  # Reached when a caller sources this policy twice.
  return 0 2>/dev/null || exit 0
fi
WSR_RECOVERY_COMMON_LOADED=1

readonly WSR_RECOVERY_CONFIG_PATH="/etc/wall-street-receipts/backup.conf"
readonly WSR_RECOVERY_PROJECT="wall-street-receipts-home"
readonly WSR_RECOVERY_POSTGRES_SERVICE="postgres"
readonly WSR_RECOVERY_POSTGRES_VOLUME="wall-street-receipts-home_postgres-data"
readonly WSR_RECOVERY_POSTGRES_NETWORK="wall-street-receipts-home_db-internal"
readonly WSR_RECOVERY_DATABASE="wsr"
readonly WSR_RECOVERY_DATABASE_USER="wsr"
readonly WSR_RECOVERY_DATA_DESTINATION="/var/lib/postgresql/data"
readonly WSR_RECOVERY_NAMESPACE="wall-street-receipts"
readonly WSR_RECOVERY_MANIFEST_SCHEMA_VERSION="1"
readonly WSR_RECOVERY_EVIDENCE_SCHEMA_VERSION="1"
readonly WSR_RECOVERY_OWNER_LABEL="com.wallstreetreceipts.recovery.owner"
readonly WSR_RECOVERY_SCOPE_LABEL="com.wallstreetreceipts.recovery.scope"
readonly WSR_RECOVERY_SCOPE_VALUE="restore-rehearsal"
readonly WSR_RETENTION_DAILY=14
readonly WSR_RETENTION_WEEKLY=8
readonly WSR_RETENTION_MONTHLY=12

declare -Ag WSR_BACKUP_CONFIG=()
declare -Ag WSR_BACKUP_MANIFEST=()
declare -ag WSR_DOCKER_ENV=()

WSR_BACKUP_MOUNT=""
WSR_BACKUP_MOUNT_ID=""
WSR_BACKUP_MOUNT_SOURCE=""
WSR_BACKUP_FILESYSTEM_TYPE=""
WSR_BACKUP_MOUNT_DEVICE_ID=""
WSR_BACKUP_ROOT=""
WSR_BACKUPS_ROOT=""
WSR_RESTORE_EVIDENCE_ROOT=""
WSR_DOCKER_HOST_PIN=""
WSR_DOCKER_ROOT=""
WSR_DOCKER_MOUNT_ID=""
WSR_DOCKER_MOUNT_TARGET=""
WSR_DOCKER_MOUNT_SOURCE=""
WSR_DOCKER_MOUNT_DEVICE_ID=""
WSR_POSTGRES_CONTAINER_ID=""
WSR_POSTGRES_IMAGE_REFERENCE="postgres:17-alpine"
WSR_POSTGRES_IMAGE_ID=""
WSR_POSTGRES_IMAGE_REVISION="unavailable"
WSR_POSTGRES_SERVER_VERSION_NUM=""
WSR_PG_DUMP_VERSION=""
WSR_DATABASE_BYTES=""
WSR_API_IMAGE_ID="unavailable"
WSR_API_IMAGE_REVISION="unavailable"
WSR_API_IMAGE_REFERENCE="unavailable"
WSR_WEB_IMAGE_ID="unavailable"
WSR_WEB_IMAGE_REVISION="unavailable"
WSR_WEB_IMAGE_REFERENCE="unavailable"
WSR_CADDY_PRODUCTION_IMAGE_ID="unavailable"
WSR_CADDY_PRODUCTION_IMAGE_REVISION="unavailable"
WSR_CADDY_PRODUCTION_IMAGE_REFERENCE="unavailable"
WSR_VALIDATED_BACKUP_PATH=""
WSR_BACKUP_STORE_IDENTITY_SHA256=""

wsr_error() {
  printf 'ERROR: %s\n' "$*" >&2
}

wsr_pass() {
  printf 'PASS: %s\n' "$*"
}

wsr_warn() {
  printf 'WARN: %s\n' "$*" >&2
}

wsr_require_tools() {
  local tool
  local -a missing=()
  local -a required=(
    awk bash chmod date df docker env find findmnt flock grep install lsblk
    mktemp mv realpath sed sha256sum sleep sort stat sync tail uname wc
  )

  if ((BASH_VERSINFO[0] < 5)); then
    wsr_error "Bash 5 or newer is required."
    return 1
  fi
  if [[ "$(uname -s)" != "Linux" ]]; then
    wsr_error "Production recovery commands require a Linux home server."
    return 1
  fi
  for tool in "${required[@]}"; do
    command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
  done
  if ((${#missing[@]} > 0)); then
    wsr_error "Missing required recovery tools: ${missing[*]}."
    return 1
  fi
  wsr_pass "Bash, coreutils, util-linux, findutils, and Docker command prerequisites are present."
}

wsr_is_local_docker_endpoint() {
  local endpoint="$1"
  [[ "$endpoint" =~ ^unix:///.+ ||
     "$endpoint" =~ ^fd://.+ ||
     "$endpoint" =~ ^tcp://127(\.[0-9]{1,3}){3}:[0-9]+$ ||
     "$endpoint" =~ ^tcp://\[::1\]:[0-9]+$ ]]
}

wsr_initialize_local_docker() {
  local selected_endpoint name uppercase_name security_option docker_mount_row
  local -a unset_arguments=()
  local -a security_options=()

  if [[ -n "${DOCKER_CONTEXT:-}" ]]; then
    selected_endpoint="$(docker context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}' 2>/dev/null)" || {
      wsr_error "The selected Docker context could not be inspected."
      return 1
    }
  elif [[ -n "${DOCKER_HOST:-}" ]]; then
    selected_endpoint="$DOCKER_HOST"
  else
    selected_endpoint="$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null)" || {
      wsr_error "The current Docker endpoint could not be inspected."
      return 1
    }
  fi

  if ! wsr_is_local_docker_endpoint "$selected_endpoint"; then
    wsr_error "Recovery operations refuse a remote Docker endpoint."
    return 1
  fi

  while IFS='=' read -r name _; do
    uppercase_name="${name^^}"
    case "$uppercase_name" in
      WSR_*|COMPOSE_*|DOCKER_*|BUILDKIT_*|BUILDX_*|HTTP_PROXY|HTTPS_PROXY|ALL_PROXY|NO_PROXY)
        unset_arguments+=(--unset="$name")
        ;;
    esac
  done < <(env)

  WSR_DOCKER_HOST_PIN="$selected_endpoint"
  WSR_DOCKER_ENV=(env "${unset_arguments[@]}" DOCKER_HOST="$WSR_DOCKER_HOST_PIN")
  if ! wsr_docker info >/dev/null 2>&1; then
    wsr_error "The pinned local Docker daemon is not reachable by this operator."
    return 1
  fi
  mapfile -t security_options < <(wsr_docker info --format '{{range .SecurityOptions}}{{println .}}{{end}}')
  for security_option in "${security_options[@]}"; do
    if [[ "$security_option" == name=rootless* || "$security_option" == name=userns* ]]; then
      wsr_error "Recovery requires the same rootful, non-userns-remapped local Docker daemon as the production secret/volume contract."
      return 1
    fi
  done
  WSR_DOCKER_ROOT="$(wsr_docker info --format '{{.DockerRootDir}}' 2>/dev/null)"
  if [[ "$WSR_DOCKER_ROOT" != /* || ! -d "$WSR_DOCKER_ROOT" ]]; then
    wsr_error "DockerRootDir is not an existing absolute directory."
    return 1
  fi
  WSR_DOCKER_ROOT="$(realpath -e -- "$WSR_DOCKER_ROOT")"
  docker_mount_row="$(findmnt -rn -T "$WSR_DOCKER_ROOT" --output ID,TARGET,SOURCE)" || {
    wsr_error "DockerRootDir mount identity cannot be observed."
    return 1
  }
  read -r WSR_DOCKER_MOUNT_ID WSR_DOCKER_MOUNT_TARGET WSR_DOCKER_MOUNT_SOURCE <<< "$docker_mount_row"
  if [[ ! "$WSR_DOCKER_MOUNT_ID" =~ ^[0-9]+$ ||
        "$WSR_DOCKER_MOUNT_TARGET" != /* ||
        "$WSR_DOCKER_MOUNT_SOURCE" != /dev/* ]]; then
    wsr_error "DockerRootDir must resolve through one directly identifiable host block mount."
    return 1
  fi
  WSR_DOCKER_MOUNT_DEVICE_ID="$(stat -c '%d' -- "$WSR_DOCKER_ROOT")"
  wsr_pass "Docker is pinned to one verified local endpoint."
}

wsr_docker() {
  if ((${#WSR_DOCKER_ENV[@]} == 0)); then
    wsr_error "The local Docker boundary has not been initialized."
    return 1
  fi
  "${WSR_DOCKER_ENV[@]}" docker "$@"
}

wsr_load_backup_config() {
  local path="$WSR_RECOVERY_CONFIG_PATH"
  local line key value
  local line_number=0
  local mode owner links resolved
  local allowed='^(WSR_BACKUP_MOUNT|WSR_BACKUP_FILESYSTEM_UUID|WSR_BACKUP_ENCRYPTION)$'
  local required

  if [[ ! -f "$path" || -L "$path" ]]; then
    wsr_error "Production recovery requires the regular, non-symlink file $WSR_RECOVERY_CONFIG_PATH."
    return 1
  fi
  resolved="$(realpath -e -- "$path")"
  if [[ "$resolved" != "$WSR_RECOVERY_CONFIG_PATH" ]]; then
    wsr_error "The production backup config path or one of its parent components is a symlink."
    return 1
  fi
  owner="$(stat -c '%u' -- "$path")"
  mode="$(stat -c '%a' -- "$path")"
  links="$(stat -c '%h' -- "$path")"
  if [[ "$owner" != "0" || ("$mode" != "600" && "$mode" != "640") || "$links" != "1" ]]; then
    wsr_error "The production backup config must be root-owned, single-linked, and mode 0600 or 0640."
    return 1
  fi

  WSR_BACKUP_CONFIG=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ ! "$line" =~ ^([A-Z][A-Z0-9_]*)=([A-Za-z0-9._/:+-]+)$ ]]; then
      wsr_error "Backup config line $line_number must be an unquoted KEY=value with no spaces, interpolation, or inline comment."
      return 1
    fi
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    if [[ ! "$key" =~ $allowed ]]; then
      wsr_error "Backup config contains an unapproved key at line $line_number: $key."
      return 1
    fi
    if [[ -n "${WSR_BACKUP_CONFIG[$key]+present}" ]]; then
      wsr_error "Backup config contains duplicate key $key."
      return 1
    fi
    WSR_BACKUP_CONFIG["$key"]="$value"
  done < "$path"

  for required in WSR_BACKUP_MOUNT WSR_BACKUP_FILESYSTEM_UUID WSR_BACKUP_ENCRYPTION; do
    if [[ -z "${WSR_BACKUP_CONFIG[$required]+present}" ]]; then
      wsr_error "Backup config is missing required key $required."
      return 1
    fi
  done

  WSR_BACKUP_MOUNT="${WSR_BACKUP_CONFIG[WSR_BACKUP_MOUNT]}"
  local wrapped_mount="/${WSR_BACKUP_MOUNT#/}/"
  if [[ ! "$WSR_BACKUP_MOUNT" =~ ^/(mnt|media)/[A-Za-z0-9._+-]+(/[A-Za-z0-9._+-]+)*$ ||
        "$wrapped_mount" == *"/../"* || "$wrapped_mount" == *"/./"* ]]; then
    wsr_error "WSR_BACKUP_MOUNT must be a specific absolute path below /mnt or /media."
    return 1
  fi
  if [[ ! "${WSR_BACKUP_CONFIG[WSR_BACKUP_FILESYSTEM_UUID]}" =~ ^[0-9A-Fa-f][0-9A-Fa-f-]{3,63}$ ||
        "${WSR_BACKUP_CONFIG[WSR_BACKUP_FILESYSTEM_UUID]}" == "replace-with-filesystem-uuid" ]]; then
    wsr_error "WSR_BACKUP_FILESYSTEM_UUID must be the real filesystem UUID, not a placeholder."
    return 1
  fi
  case "${WSR_BACKUP_CONFIG[WSR_BACKUP_ENCRYPTION]}" in
    none-demo-only|luks2) ;;
    *)
      wsr_error "WSR_BACKUP_ENCRYPTION must be none-demo-only or luks2."
      return 1
      ;;
  esac
  wsr_pass "The exact root-owned backup config has only the approved three-key contract."
}

wsr_collect_block_rows() {
  local path="$1"
  local mount_source resolved_source
  local -n destination_rows="$2"
  local -a sources=()

  mapfile -t sources < <(findmnt -rn -T "$path" --output SOURCE)
  if ((${#sources[@]} != 1)); then
    wsr_error "Exactly one block-backed mount source must contain $path."
    return 1
  fi
  mount_source="${sources[0]}"
  mount_source="${mount_source%%\[*}"
  if [[ "$mount_source" != /dev/* ]]; then
    wsr_error "The path $path is not backed by a directly identifiable block device."
    return 1
  fi
  resolved_source="$(realpath -e -- "$mount_source")" || {
    wsr_error "The block device behind $path cannot be resolved."
    return 1
  }
  mapfile -t destination_rows < <(
    lsblk --inverse --paths --noheadings --raw \
      --output NAME,TYPE,FSTYPE,FSVER "$resolved_source"
  )
  if ((${#destination_rows[@]} == 0)); then
    wsr_error "lsblk returned no physical dependency rows for $path."
    return 1
  fi
}

wsr_validate_block_topology_rows() {
  local path="$1" topology="$2" rows_name="$3"
  local -n topology_rows="$rows_name"
  local row name type _fstype _fsver
  local disk_count=0 part_count=0 crypt_count=0
  local -A seen_names=()

  for row in "${topology_rows[@]}"; do
    read -r name type _fstype _fsver <<< "$row"
    if [[ ! "$name" =~ ^/dev/[^[:space:]]+$ || -z "$type" ||
          -n "${seen_names[$name]+present}" ]]; then
      wsr_error "The block ancestry for $path is malformed or duplicates a device."
      return 1
    fi
    seen_names["$name"]=1
    case "$type" in
      disk) disk_count=$((disk_count + 1)) ;;
      part) part_count=$((part_count + 1)) ;;
      crypt)
        if [[ "$topology" != "luks2" ]]; then
          wsr_error "The block ancestry for $path contains unconfigured dm-crypt."
          return 1
        fi
        crypt_count=$((crypt_count + 1))
        ;;
      *)
        wsr_error "The block ancestry for $path contains unsupported LVM, RAID, mapper, or virtual type: $type."
        return 1
        ;;
    esac
  done

  if ((disk_count != 1 || part_count > 1)); then
    wsr_error "The block ancestry for $path must resolve to one physical disk and at most one partition."
    return 1
  fi
  if [[ "$topology" == "luks2" ]]; then
    if ((crypt_count != 1)); then
      wsr_error "The configured LUKS2 ancestry must contain exactly one dm-crypt mapping."
      return 1
    fi
  elif ((crypt_count != 0)); then
    wsr_error "A direct block ancestry cannot contain a dm-crypt mapping."
    return 1
  fi
}

wsr_collect_physical_leaf_disks() {
  local path="$1" topology="$2" row name type _fstype _fsver serial transport identity
  local -n destination_disks="$3"
  local -a rows=() serial_rows=() transport_rows=()

  wsr_collect_block_rows "$path" rows || return 1
  wsr_validate_block_topology_rows "$path" "$topology" rows || return 1
  destination_disks=()
  for row in "${rows[@]}"; do
    read -r name type _fstype _fsver <<< "$row"
    if [[ "$type" == "disk" ]]; then
      mapfile -t serial_rows < <(
        lsblk --nodeps --noheadings --raw --output SERIAL "$name"
      )
      mapfile -t transport_rows < <(
        lsblk --nodeps --noheadings --raw --output TRAN "$name"
      )
      if ((${#serial_rows[@]} != 1 || ${#transport_rows[@]} != 1)); then
        wsr_error "The leaf disk behind $path lacks one stable transport/serial observation."
        return 1
      fi
      serial="${serial_rows[0]}"
      transport="${transport_rows[0],,}"
      serial="${serial#"${serial%%[![:space:]]*}"}"
      serial="${serial%"${serial##*[![:space:]]}"}"
      transport="${transport#"${transport%%[![:space:]]*}"}"
      transport="${transport%"${transport##*[![:space:]]}"}"
      if [[ ! "$serial" =~ ^[A-Za-z0-9._:+-]{4,128}$ ]]; then
        wsr_error "The leaf disk behind $path has no bounded stable serial identity."
        return 1
      fi
      case "$transport" in
        sata|usb)
          [[ "$name" =~ ^/dev/sd[a-z]+$ ]] || {
            wsr_error "The $transport leaf behind $path is not a direct SCSI-disk namespace."
            return 1
          }
          ;;
        nvme)
          [[ "$name" =~ ^/dev/nvme[0-9]+n[0-9]+$ ]] || {
            wsr_error "The NVMe leaf behind $path is not a direct namespace."
            return 1
          }
          ;;
        *)
          wsr_error "The leaf behind $path is not an allowlisted direct SATA, USB, or NVMe device."
          return 1
          ;;
      esac
      identity="$(printf 'transport=%s\nserial=%s\n' "$transport" "$serial" | sha256sum | awk '{print $1}')"
      if [[ ! "$identity" =~ ^[0-9a-f]{64}$ ]]; then
        wsr_error "The leaf identity behind $path could not be hashed deterministically."
        return 1
      fi
      destination_disks+=("$identity")
    fi
  done
  if ((${#destination_disks[@]} == 0)); then
    wsr_error "No physical leaf disk was found behind $path."
    return 1
  fi
  mapfile -t destination_disks < <(printf '%s\n' "${destination_disks[@]}" | sort -u)
}

wsr_require_docker_root_filesystem_path() {
  local path="$1" resolved row mount_id mount_target mount_source path_device_id
  local -a rows=()
  resolved="$(realpath -e -- "$path")" || {
    wsr_error "Docker storage path cannot be resolved: $path."
    return 1
  }
  mapfile -t rows < <(findmnt -rn -T "$resolved" --output ID,TARGET,SOURCE)
  if ((${#rows[@]} != 1)); then
    wsr_error "Docker storage paths must resolve through exactly one DockerRootDir mount."
    return 1
  fi
  row="${rows[0]}"
  read -r mount_id mount_target mount_source <<< "$row"
  path_device_id="$(stat -c '%d' -- "$resolved")"
  if [[ "$mount_id" != "$WSR_DOCKER_MOUNT_ID" ||
        "$mount_target" != "$WSR_DOCKER_MOUNT_TARGET" ||
        "$mount_source" != "$WSR_DOCKER_MOUNT_SOURCE" ||
        "$path_device_id" != "$WSR_DOCKER_MOUNT_DEVICE_ID" ]]; then
    wsr_error "Docker volume path escaped the verified DockerRootDir mount boundary."
    return 1
  fi
}

wsr_require_backup_filesystem_path() {
  local path="$1" resolved row mount_id mount_target mount_source filesystem_type filesystem_uuid
  local path_device_id
  local -a rows=()

  if [[ ! -e "$path" && ! -L "$path" ]]; then
    wsr_error "Recovery-store path is absent: $path."
    return 1
  fi
  resolved="$(realpath -e -- "$path")" || {
    wsr_error "Recovery-store path cannot be resolved: $path."
    return 1
  }
  mapfile -t rows < <(
    findmnt -rn -T "$resolved" --output ID,TARGET,SOURCE,FSTYPE,UUID
  )
  if ((${#rows[@]} != 1)); then
    wsr_error "Recovery-store paths must resolve through exactly one verified backup mount."
    return 1
  fi
  row="${rows[0]}"
  read -r mount_id mount_target mount_source filesystem_type filesystem_uuid <<< "$row"
  path_device_id="$(stat -c '%d' -- "$resolved")"
  if [[ "$mount_id" != "$WSR_BACKUP_MOUNT_ID" ||
        "$mount_target" != "$WSR_BACKUP_MOUNT" ||
        "$mount_source" != "$WSR_BACKUP_MOUNT_SOURCE" ||
        "$filesystem_type" != "$WSR_BACKUP_FILESYSTEM_TYPE" ||
        "${filesystem_uuid,,}" != "${WSR_BACKUP_CONFIG[WSR_BACKUP_FILESYSTEM_UUID],,}" ||
        "$path_device_id" != "$WSR_BACKUP_MOUNT_DEVICE_ID" ]]; then
    wsr_error "Recovery-store path escaped the verified mount ID, source, UUID, filesystem, or device boundary."
    return 1
  fi
}

wsr_validate_backup_mount() {
  local mount_target mount_source filesystem_type mount_options filesystem_uuid mount_id
  local expected_uuid encryption row _name type fstype fsver
  local backup_root_owner backup_root_mode backup_root_resolved identity_file
  local identity_owner identity_mode identity_links identity_resolved
  local identity_schema="" identity_namespace="" identity_uuid="" identity_line
  local identity_line_number=0 identity_key identity_value
  local -a ids=() targets=() sources=() types=() options=() uuids=()
  local -a backup_rows=() backup_disks=() docker_disks=()
  local backup_disk docker_disk

  if [[ ! -d "$WSR_BACKUP_MOUNT" || -L "$WSR_BACKUP_MOUNT" ]]; then
    wsr_error "PENDING_BACKUP_DEVICE: the configured backup mount is absent or is a symlink."
    return 1
  fi
  mount_target="$(realpath -e -- "$WSR_BACKUP_MOUNT")"
  if [[ "$mount_target" != "$WSR_BACKUP_MOUNT" ]]; then
    wsr_error "The configured backup mount path or one of its parent components is a symlink."
    return 1
  fi

  mapfile -t ids < <(findmnt -rn --mountpoint "$WSR_BACKUP_MOUNT" --output ID)
  mapfile -t targets < <(findmnt -rn --mountpoint "$WSR_BACKUP_MOUNT" --output TARGET)
  mapfile -t sources < <(findmnt -rn --mountpoint "$WSR_BACKUP_MOUNT" --output SOURCE)
  mapfile -t types < <(findmnt -rn --mountpoint "$WSR_BACKUP_MOUNT" --output FSTYPE)
  mapfile -t options < <(findmnt -rn --mountpoint "$WSR_BACKUP_MOUNT" --output OPTIONS)
  mapfile -t uuids < <(findmnt -rn --mountpoint "$WSR_BACKUP_MOUNT" --output UUID)
  if ((${#ids[@]} != 1 || ${#targets[@]} != 1 || ${#sources[@]} != 1 || ${#types[@]} != 1 ||
       ${#options[@]} != 1 || ${#uuids[@]} != 1)) ||
     [[ "${targets[0]:-}" != "$WSR_BACKUP_MOUNT" ]]; then
    wsr_error "PENDING_BACKUP_DEVICE: the configured path must be the exact active mount point."
    return 1
  fi
  mount_source="${sources[0]}"
  filesystem_type="${types[0]}"
  mount_options="${options[0]}"
  filesystem_uuid="${uuids[0]}"
  mount_id="${ids[0]}"
  expected_uuid="${WSR_BACKUP_CONFIG[WSR_BACKUP_FILESYSTEM_UUID]}"

  if [[ "$mount_source" != /dev/* || "$mount_source" == /dev/loop* ]]; then
    wsr_error "The production backup mount must use a non-loop local block device."
    return 1
  fi
  if [[ "$filesystem_type" != "ext4" && "$filesystem_type" != "xfs" ]]; then
    wsr_error "The production backup filesystem must be ext4 or xfs."
    return 1
  fi
  if [[ "${filesystem_uuid,,}" != "${expected_uuid,,}" ]]; then
    wsr_error "PENDING_BACKUP_DEVICE: the mounted filesystem UUID does not match backup.conf."
    return 1
  fi
  for required_option in rw nodev nosuid noexec; do
    if [[ ",$mount_options," != *",$required_option,"* ]]; then
      wsr_error "The backup mount must include rw,nodev,nosuid,noexec; $required_option is missing."
      return 1
    fi
  done

  WSR_BACKUP_MOUNT_ID="$mount_id"
  WSR_BACKUP_MOUNT_SOURCE="$mount_source"
  WSR_BACKUP_FILESYSTEM_TYPE="$filesystem_type"
  WSR_BACKUP_MOUNT_DEVICE_ID="$(stat -c '%d' -- "$WSR_BACKUP_MOUNT")"
  wsr_require_backup_filesystem_path "$WSR_BACKUP_MOUNT" || return 1

  wsr_collect_block_rows "$WSR_BACKUP_MOUNT" backup_rows || return 1
  encryption="${WSR_BACKUP_CONFIG[WSR_BACKUP_ENCRYPTION]}"
  if [[ "$encryption" == "luks2" ]]; then
    local found_crypt=false found_luks2=false
    for row in "${backup_rows[@]}"; do
      read -r _name type fstype fsver <<< "$row"
      [[ "$type" == "crypt" ]] && found_crypt=true
      [[ "$fstype" == "crypto_LUKS" && "$fsver" == "2" ]] && found_luks2=true
    done
    if [[ "$found_crypt" != true || "$found_luks2" != true ]]; then
      wsr_error "WSR_BACKUP_ENCRYPTION=luks2 requires a dm-crypt mapping backed by a LUKS2 device."
      return 1
    fi
  else
    wsr_warn "PENDING_OFFSITE_COPY: none-demo-only protects no confidential or irreplaceable data at rest."
  fi

  wsr_collect_physical_leaf_disks "$WSR_BACKUP_MOUNT" "$encryption" backup_disks || return 1
  wsr_collect_physical_leaf_disks "$WSR_DOCKER_ROOT" direct docker_disks || return 1
  for backup_disk in "${backup_disks[@]}"; do
    for docker_disk in "${docker_disks[@]}"; do
      if [[ "$backup_disk" == "$docker_disk" ]]; then
        wsr_error "The backup mount and DockerRootDir share a physical leaf disk; separate-device backup requires disjoint leaf sets."
        return 1
      fi
    done
  done

  WSR_BACKUP_ROOT="$WSR_BACKUP_MOUNT/$WSR_RECOVERY_NAMESPACE"
  if [[ ! -d "$WSR_BACKUP_ROOT" || -L "$WSR_BACKUP_ROOT" ]]; then
    wsr_error "The fixed backup root $WSR_BACKUP_ROOT must be provisioned before recovery operations."
    return 1
  fi
  backup_root_resolved="$(realpath -e -- "$WSR_BACKUP_ROOT")"
  backup_root_owner="$(stat -c '%u' -- "$WSR_BACKUP_ROOT")"
  backup_root_mode="$(stat -c '%a' -- "$WSR_BACKUP_ROOT")"
  if [[ "$backup_root_resolved" != "$WSR_BACKUP_ROOT" || "$backup_root_owner" != "0" || "$backup_root_mode" != "700" ]]; then
    wsr_error "The fixed backup root must be a root-owned, non-symlink mode-0700 directory."
    return 1
  fi
  wsr_require_backup_filesystem_path "$WSR_BACKUP_ROOT" || return 1

  identity_file="$WSR_BACKUP_ROOT/.store-identity"
  if [[ ! -f "$identity_file" || -L "$identity_file" ]]; then
    wsr_error "PENDING_BACKUP_DEVICE: the versioned backup store identity marker is missing."
    return 1
  fi
  identity_resolved="$(realpath -e -- "$identity_file")"
  identity_owner="$(stat -c '%u' -- "$identity_file")"
  identity_mode="$(stat -c '%a' -- "$identity_file")"
  identity_links="$(stat -c '%h' -- "$identity_file")"
  if [[ "$identity_resolved" != "$identity_file" || "$identity_owner" != "0" ||
        "$identity_mode" != "400" || "$identity_links" != "1" ]]; then
    wsr_error "The backup store identity must be root-owned, single-linked, non-symlink, and mode 0400."
    return 1
  fi
  wsr_require_backup_filesystem_path "$identity_file" || return 1
  while IFS= read -r identity_line || [[ -n "$identity_line" ]]; do
    identity_line_number=$((identity_line_number + 1))
    identity_line="${identity_line%$'\r'}"
    if [[ ! "$identity_line" =~ ^([a-z][a-z_]*)=([A-Za-z0-9._-]+)$ ]]; then
      wsr_error "The backup store identity marker has invalid syntax."
      return 1
    fi
    identity_key="${BASH_REMATCH[1]}"
    identity_value="${BASH_REMATCH[2]}"
    case "$identity_key" in
      schema_version)
        [[ -z "$identity_schema" ]] || { wsr_error "Duplicate store schema_version."; return 1; }
        identity_schema="$identity_value"
        ;;
      namespace)
        [[ -z "$identity_namespace" ]] || { wsr_error "Duplicate store namespace."; return 1; }
        identity_namespace="$identity_value"
        ;;
      filesystem_uuid)
        [[ -z "$identity_uuid" ]] || { wsr_error "Duplicate store filesystem_uuid."; return 1; }
        identity_uuid="$identity_value"
        ;;
      *)
        wsr_error "The backup store identity marker contains an unknown field."
        return 1
        ;;
    esac
  done < "$identity_file"
  if ((identity_line_number != 3)) || [[ "$identity_schema" != "1" ||
       "$identity_namespace" != "$WSR_RECOVERY_NAMESPACE" ||
       "${identity_uuid,,}" != "${expected_uuid,,}" ]]; then
    wsr_error "The versioned store identity is not bound to this namespace and filesystem UUID."
    return 1
  fi
  WSR_BACKUP_STORE_IDENTITY_SHA256="$(sha256sum -- "$identity_file" | awk '{print $1}')" || {
    wsr_error "The backup store identity could not be hashed from the verified device."
    return 1
  }
  if [[ ! "$WSR_BACKUP_STORE_IDENTITY_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
    wsr_error "The backup store identity SHA-256 result is invalid."
    return 1
  fi

  WSR_BACKUPS_ROOT="$WSR_BACKUP_ROOT/backups"
  WSR_RESTORE_EVIDENCE_ROOT="$WSR_BACKUP_ROOT/restore-evidence"
  wsr_pass "The mounted backup filesystem has the exact UUID, hardened options, and physical leaf disks disjoint from DockerRootDir."
}

wsr_validate_storage_directory() {
  local path="$1" owner mode resolved
  if [[ ! -d "$path" || -L "$path" ]]; then
    wsr_error "Expected a regular recovery storage directory: $path."
    return 1
  fi
  resolved="$(realpath -e -- "$path")"
  owner="$(stat -c '%u' -- "$path")"
  mode="$(stat -c '%a' -- "$path")"
  if [[ "$resolved" != "$path" || "$owner" != "0" || "$mode" != "700" ]]; then
    wsr_error "Recovery storage directories must be root-owned, non-symlink, and mode 0700."
    return 1
  fi
  wsr_require_backup_filesystem_path "$path" || return 1
}

wsr_ensure_storage_directory() {
  local path="$1"
  if [[ -e "$path" || -L "$path" ]]; then
    wsr_validate_storage_directory "$path" || return 1
  else
    wsr_require_backup_filesystem_path "${path%/*}" || return 1
    install -d -m 0700 -o root -g root -- "$path" || return 1
    wsr_validate_storage_directory "$path" || return 1
  fi
}

wsr_prepare_storage_layout() {
  wsr_ensure_storage_directory "$WSR_BACKUPS_ROOT" || return 1
  wsr_ensure_storage_directory "$WSR_RESTORE_EVIDENCE_ROOT" || return 1
  wsr_ensure_storage_directory "$WSR_BACKUP_ROOT/.locks" || return 1
}

wsr_validate_production_postgres() {
  local running health image_reference image_id revision port_binding_count network_mode
  local role_label release_label secret_source
  local volume_driver volume_project volume_key volume_options_count volume_mountpoint
  local -a container_ids=() data_mounts=() network_names=()

  mapfile -t container_ids < <(
    wsr_docker container ls --all --quiet \
      --filter "label=com.docker.compose.project=$WSR_RECOVERY_PROJECT" \
      --filter "label=com.docker.compose.service=$WSR_RECOVERY_POSTGRES_SERVICE"
  )
  if ((${#container_ids[@]} != 1)); then
    wsr_error "Production recovery requires exactly one Compose-labeled postgres container."
    return 1
  fi
  WSR_POSTGRES_CONTAINER_ID="${container_ids[0]}"
  role_label="$(wsr_docker inspect --format '{{index .Config.Labels "com.wallstreetreceipts.role"}}' "$WSR_POSTGRES_CONTAINER_ID" 2>/dev/null || true)"
  release_label="$(wsr_docker inspect --format '{{index .Config.Labels "com.wallstreetreceipts.release-sha"}}' "$WSR_POSTGRES_CONTAINER_ID" 2>/dev/null || true)"
  if [[ "$role_label" != "production-primary-database" || ! "$release_label" =~ ^[0-9a-f]{40}$ ]]; then
    wsr_error "The Compose-labeled PostgreSQL source lacks the exact production role and release SHA labels."
    return 1
  fi
  running="$(wsr_docker inspect --format '{{.State.Running}}' "$WSR_POSTGRES_CONTAINER_ID")"
  health="$(wsr_docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$WSR_POSTGRES_CONTAINER_ID")"
  if [[ "$running" != "true" || "$health" != "healthy" ]]; then
    wsr_error "The exact production postgres container must be running and healthy."
    return 1
  fi
  image_reference="$(wsr_docker inspect --format '{{.Config.Image}}' "$WSR_POSTGRES_CONTAINER_ID")"
  image_id="$(wsr_docker inspect --format '{{.Image}}' "$WSR_POSTGRES_CONTAINER_ID")"
  if [[ "$image_reference" != "postgres:17-alpine" || ! "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    wsr_error "The production postgres container does not use the reviewed PostgreSQL 17 image contract."
    return 1
  fi
  WSR_POSTGRES_IMAGE_ID="$image_id"
  revision="$(wsr_docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$WSR_POSTGRES_CONTAINER_ID" 2>/dev/null || true)"
  if [[ "$revision" =~ ^[0-9a-f]{40}$ ]]; then
    WSR_POSTGRES_IMAGE_REVISION="$revision"
  else
    WSR_POSTGRES_IMAGE_REVISION="unavailable"
  fi

  port_binding_count="$(wsr_docker inspect --format '{{len .HostConfig.PortBindings}}' "$WSR_POSTGRES_CONTAINER_ID")"
  network_mode="$(wsr_docker inspect --format '{{.HostConfig.NetworkMode}}' "$WSR_POSTGRES_CONTAINER_ID")"
  if [[ "$port_binding_count" != "0" || "$network_mode" == "host" ]]; then
    wsr_error "The production postgres PortBindings must be empty and host networking is forbidden."
    return 1
  fi

  mapfile -t data_mounts < <(
    wsr_docker inspect --format \
      '{{range .Mounts}}{{printf "%s|%s|%s|%t\n" .Type .Name .Destination .RW}}{{end}}' \
      "$WSR_POSTGRES_CONTAINER_ID" | sort
  )
  if ((${#data_mounts[@]} != 2)) ||
     [[ "${data_mounts[0]}" != "bind||/run/secrets/postgres_password|false" ||
        "${data_mounts[1]}" != "volume|$WSR_RECOVERY_POSTGRES_VOLUME|$WSR_RECOVERY_DATA_DESTINATION|true" ]]; then
    wsr_error "PostgreSQL must have only the reviewed writable data volume and read-only password-file bind; the backup HDD is never mounted into a container."
    return 1
  fi
  secret_source="$(
    wsr_docker inspect --format \
      '{{range .Mounts}}{{if eq .Destination "/run/secrets/postgres_password"}}{{printf "%s" .Source}}{{end}}{{end}}' \
      "$WSR_POSTGRES_CONTAINER_ID"
  )"
  if [[ "$secret_source" != "/etc/wall-street-receipts/secrets/postgres_password" ]]; then
    wsr_error "The PostgreSQL password-file bind source differs from the fixed reviewed host path."
    return 1
  fi

  volume_driver="$(wsr_docker volume inspect --format '{{.Driver}}' "$WSR_RECOVERY_POSTGRES_VOLUME")"
  volume_project="$(wsr_docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$WSR_RECOVERY_POSTGRES_VOLUME")"
  volume_key="$(wsr_docker volume inspect --format '{{index .Labels "com.docker.compose.volume"}}' "$WSR_RECOVERY_POSTGRES_VOLUME")"
  volume_options_count="$(wsr_docker volume inspect --format '{{len .Options}}' "$WSR_RECOVERY_POSTGRES_VOLUME")"
  volume_mountpoint="$(wsr_docker volume inspect --format '{{.Mountpoint}}' "$WSR_RECOVERY_POSTGRES_VOLUME")"
  volume_mountpoint="$(realpath -e -- "$volume_mountpoint")" || {
    wsr_error "The PostgreSQL data-volume mountpoint cannot be resolved."
    return 1
  }
  wsr_require_docker_root_filesystem_path "$volume_mountpoint" || return 1
  if [[ "$volume_driver" != "local" || "$volume_project" != "$WSR_RECOVERY_PROJECT" ||
        "$volume_key" != "postgres-data" || "$volume_options_count" != "0" ||
        "$volume_mountpoint" != "$WSR_DOCKER_ROOT"/volumes/*/_data ]]; then
    wsr_error "The PostgreSQL data volume driver, options, DockerRootDir placement, or Compose ownership labels changed."
    return 1
  fi

  mapfile -t network_names < <(
    # shellcheck disable=SC2016  # Docker Go-template variables are not shell variables.
    wsr_docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' \
      "$WSR_POSTGRES_CONTAINER_ID" | sort
  )
  if ((${#network_names[@]} != 1)) || [[ "${network_names[0]}" != "$WSR_RECOVERY_POSTGRES_NETWORK" ]]; then
    wsr_error "The production postgres container must remain only on the fixed internal database network."
    return 1
  fi
  wsr_pass "Exactly one healthy Compose-labeled PostgreSQL container has the expected private volume and no host ports."
}

wsr_capture_optional_service_image() {
  local service="$1" variable_prefix="$2"
  local image_id revision reference
  local -a ids=()

  mapfile -t ids < <(
    wsr_docker container ls --quiet \
      --filter "label=com.docker.compose.project=$WSR_RECOVERY_PROJECT" \
      --filter "label=com.docker.compose.service=$service"
  )
  image_id="unavailable"
  revision="unavailable"
  reference="unavailable"
  if ((${#ids[@]} == 1)); then
    image_id="$(wsr_docker inspect --format '{{.Image}}' "${ids[0]}")"
    revision="$(wsr_docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "${ids[0]}" 2>/dev/null || true)"
    reference="$(wsr_docker inspect --format '{{.Config.Image}}' "${ids[0]}" 2>/dev/null || true)"
    if [[ ! "$image_id" =~ ^sha256:[0-9a-f]{64}$ ||
          ! "$revision" =~ ^[0-9a-f]{40}$ ||
          ! "$reference" =~ ^[A-Za-z0-9._:/+-]+$ ]]; then
      image_id="unavailable"
      revision="unavailable"
      reference="unavailable"
    fi
  fi
  printf -v "${variable_prefix}_IMAGE_REFERENCE" '%s' "$reference"
  printf -v "${variable_prefix}_IMAGE_ID" '%s' "$image_id"
  printf -v "${variable_prefix}_IMAGE_REVISION" '%s' "$revision"
}

wsr_capture_release_image_metadata() {
  wsr_capture_optional_service_image api WSR_API
  wsr_capture_optional_service_image web WSR_WEB
  wsr_capture_optional_service_image caddy-production WSR_CADDY_PRODUCTION
}

wsr_query_postgres_metadata() {
  WSR_POSTGRES_SERVER_VERSION_NUM="$(
    wsr_docker exec "$WSR_POSTGRES_CONTAINER_ID" \
      psql -X -q -A -t --no-password --username="$WSR_RECOVERY_DATABASE_USER" \
        --dbname="$WSR_RECOVERY_DATABASE" --command='SHOW server_version_num;'
  )"
  WSR_POSTGRES_SERVER_VERSION_NUM="${WSR_POSTGRES_SERVER_VERSION_NUM//$'\r'/}"
  WSR_POSTGRES_SERVER_VERSION_NUM="${WSR_POSTGRES_SERVER_VERSION_NUM//$'\n'/}"
  if [[ ! "$WSR_POSTGRES_SERVER_VERSION_NUM" =~ ^17[0-9]{4}$ ]]; then
    wsr_error "The running database did not report a PostgreSQL 17 server_version_num."
    return 1
  fi
  WSR_PG_DUMP_VERSION="$(wsr_docker exec "$WSR_POSTGRES_CONTAINER_ID" pg_dump --version | awk '{print $NF}')"
  if [[ ! "$WSR_PG_DUMP_VERSION" =~ ^17([.][0-9]+)+$ ]]; then
    wsr_error "The production container did not report a PostgreSQL 17 pg_dump version."
    return 1
  fi
}

wsr_derive_git_sha_from_images() {
  if [[ "$WSR_API_IMAGE_REVISION" =~ ^[0-9a-f]{40}$ &&
        "$WSR_WEB_IMAGE_REVISION" =~ ^[0-9a-f]{40}$ &&
        "$WSR_CADDY_PRODUCTION_IMAGE_REVISION" =~ ^[0-9a-f]{40}$ &&
        "$WSR_API_IMAGE_REVISION" == "$WSR_WEB_IMAGE_REVISION" &&
        "$WSR_API_IMAGE_REVISION" == "$WSR_CADDY_PRODUCTION_IMAGE_REVISION" ]]; then
    printf '%s\n' "$WSR_API_IMAGE_REVISION"
  else
    printf 'unavailable\n'
  fi
}

wsr_write_backup_manifest() {
  local path="$1" backup_id="$2" started_utc="$3" completed_utc="$4"
  local archive_bytes="$5" archive_sha256="$6" inventory_bytes="$7"
  local inventory_entries="$8" inventory_sha256="$9" git_sha
  git_sha="$(wsr_derive_git_sha_from_images)"

  {
    printf 'schema_version=%s\n' "$WSR_RECOVERY_MANIFEST_SCHEMA_VERSION"
    printf 'backup_id=%s\n' "$backup_id"
    printf 'started_utc=%s\n' "$started_utc"
    printf 'completed_utc=%s\n' "$completed_utc"
    printf 'project=%s\n' "$WSR_RECOVERY_PROJECT"
    printf 'database_name=%s\n' "$WSR_RECOVERY_DATABASE"
    printf 'database_bytes=%s\n' "$WSR_DATABASE_BYTES"
    printf 'archive_file=database.dump\n'
    printf 'pg_dump_options=format-custom+compress-6+no-owner+no-privileges+no-password\n'
    printf 'archive_bytes=%s\n' "$archive_bytes"
    printf 'archive_sha256=%s\n' "$archive_sha256"
    printf 'archive_inventory_file=database.inventory\n'
    printf 'archive_inventory_bytes=%s\n' "$inventory_bytes"
    printf 'archive_inventory_entries=%s\n' "$inventory_entries"
    printf 'archive_inventory_sha256=%s\n' "$inventory_sha256"
    printf 'encryption=%s\n' "${WSR_BACKUP_CONFIG[WSR_BACKUP_ENCRYPTION]}"
    printf 'store_identity_sha256=%s\n' "$WSR_BACKUP_STORE_IDENTITY_SHA256"
    printf 'git_sha=%s\n' "$git_sha"
    printf 'postgres_server_version_num=%s\n' "$WSR_POSTGRES_SERVER_VERSION_NUM"
    printf 'pg_dump_version=%s\n' "$WSR_PG_DUMP_VERSION"
    printf 'postgres_volume_name=%s\n' "$WSR_RECOVERY_POSTGRES_VOLUME"
    printf 'postgres_image_reference=%s\n' "$WSR_POSTGRES_IMAGE_REFERENCE"
    printf 'postgres_image_id=%s\n' "$WSR_POSTGRES_IMAGE_ID"
    printf 'postgres_image_revision=%s\n' "$WSR_POSTGRES_IMAGE_REVISION"
    printf 'api_image_reference=%s\n' "$WSR_API_IMAGE_REFERENCE"
    printf 'api_image_id=%s\n' "$WSR_API_IMAGE_ID"
    printf 'api_image_revision=%s\n' "$WSR_API_IMAGE_REVISION"
    printf 'web_image_reference=%s\n' "$WSR_WEB_IMAGE_REFERENCE"
    printf 'web_image_id=%s\n' "$WSR_WEB_IMAGE_ID"
    printf 'web_image_revision=%s\n' "$WSR_WEB_IMAGE_REVISION"
    printf 'caddy_production_image_reference=%s\n' "$WSR_CADDY_PRODUCTION_IMAGE_REFERENCE"
    printf 'caddy_production_image_id=%s\n' "$WSR_CADDY_PRODUCTION_IMAGE_ID"
    printf 'caddy_production_image_revision=%s\n' "$WSR_CADDY_PRODUCTION_IMAGE_REVISION"
  } > "$path"
}

wsr_load_backup_manifest() {
  local path="$1" line key value line_number=0 required
  local service expected_prefix reference image_id revision observed_release_count=0
  local expected_git_sha="unavailable"
  local -a release_services=(api web caddy_production)
  local allowed='^(schema_version|backup_id|started_utc|completed_utc|project|database_name|database_bytes|archive_file|pg_dump_options|archive_bytes|archive_sha256|archive_inventory_file|archive_inventory_bytes|archive_inventory_entries|archive_inventory_sha256|encryption|store_identity_sha256|git_sha|postgres_server_version_num|pg_dump_version|postgres_volume_name|postgres_image_reference|postgres_image_id|postgres_image_revision|api_image_reference|api_image_id|api_image_revision|web_image_reference|web_image_id|web_image_revision|caddy_production_image_reference|caddy_production_image_id|caddy_production_image_revision)$'
  local -a required_keys=(
    schema_version backup_id started_utc completed_utc project database_name database_bytes
    archive_file pg_dump_options archive_bytes archive_sha256 archive_inventory_file
    archive_inventory_bytes archive_inventory_entries archive_inventory_sha256
    encryption store_identity_sha256 git_sha
    postgres_server_version_num pg_dump_version postgres_volume_name
    postgres_image_reference postgres_image_id postgres_image_revision
    api_image_reference api_image_id api_image_revision web_image_reference
    web_image_id web_image_revision caddy_production_image_reference
    caddy_production_image_id caddy_production_image_revision
  )

  WSR_BACKUP_MANIFEST=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    if [[ ! "$line" =~ ^([a-z][a-z0-9_]*)=([A-Za-z0-9._:/+-]+)$ ]]; then
      wsr_error "Backup manifest line $line_number has invalid syntax."
      return 1
    fi
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    if [[ ! "$key" =~ $allowed || -n "${WSR_BACKUP_MANIFEST[$key]+present}" ]]; then
      wsr_error "Backup manifest contains an unknown or duplicate field at line $line_number."
      return 1
    fi
    WSR_BACKUP_MANIFEST["$key"]="$value"
  done < "$path"
  for required in "${required_keys[@]}"; do
    [[ -n "${WSR_BACKUP_MANIFEST[$required]+present}" ]] || {
      wsr_error "Backup manifest is missing field $required."
      return 1
    }
  done

  if [[ "${WSR_BACKUP_MANIFEST[schema_version]}" != "$WSR_RECOVERY_MANIFEST_SCHEMA_VERSION" ||
        "${WSR_BACKUP_MANIFEST[project]}" != "$WSR_RECOVERY_PROJECT" ||
        "${WSR_BACKUP_MANIFEST[database_name]}" != "$WSR_RECOVERY_DATABASE" ||
        "${WSR_BACKUP_MANIFEST[archive_file]}" != "database.dump" ||
        "${WSR_BACKUP_MANIFEST[pg_dump_options]}" != "format-custom+compress-6+no-owner+no-privileges+no-password" ||
        "${WSR_BACKUP_MANIFEST[archive_inventory_file]}" != "database.inventory" ||
        "${WSR_BACKUP_MANIFEST[postgres_volume_name]}" != "$WSR_RECOVERY_POSTGRES_VOLUME" ]]; then
    wsr_error "The backup manifest identity does not match the reviewed recovery contract."
    return 1
  fi
  if [[ ! "${WSR_BACKUP_MANIFEST[database_bytes]}" =~ ^[1-9][0-9]{0,14}$ ||
        ! "${WSR_BACKUP_MANIFEST[archive_bytes]}" =~ ^[1-9][0-9]*$ ||
        ! "${WSR_BACKUP_MANIFEST[archive_sha256]}" =~ ^[0-9a-f]{64}$ ||
        ! "${WSR_BACKUP_MANIFEST[archive_inventory_bytes]}" =~ ^[1-9][0-9]*$ ||
        ! "${WSR_BACKUP_MANIFEST[archive_inventory_entries]}" =~ ^[1-9][0-9]*$ ||
        ! "${WSR_BACKUP_MANIFEST[archive_inventory_sha256]}" =~ ^[0-9a-f]{64}$ ||
        ! "${WSR_BACKUP_MANIFEST[store_identity_sha256]}" =~ ^[0-9a-f]{64}$ ||
        "${WSR_BACKUP_MANIFEST[store_identity_sha256]}" != "$WSR_BACKUP_STORE_IDENTITY_SHA256" ||
        ! "${WSR_BACKUP_MANIFEST[postgres_server_version_num]}" =~ ^17[0-9]{4}$ ||
        ! "${WSR_BACKUP_MANIFEST[pg_dump_version]}" =~ ^17([.][0-9]+)+$ ||
        "${WSR_BACKUP_MANIFEST[postgres_image_reference]}" != "postgres:17-alpine" ||
        ! "${WSR_BACKUP_MANIFEST[postgres_image_id]}" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    wsr_error "The backup manifest numeric, checksum, or PostgreSQL image evidence is invalid."
    return 1
  fi
  if ! wsr_backup_id_valid "${WSR_BACKUP_MANIFEST[backup_id]}"; then
    wsr_error "The backup manifest backup_id is invalid."
    return 1
  fi
  if [[ ! "${WSR_BACKUP_MANIFEST[started_utc]}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ||
        ! "${WSR_BACKUP_MANIFEST[completed_utc]}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ||
        ("${WSR_BACKUP_MANIFEST[encryption]}" != "none-demo-only" &&
         "${WSR_BACKUP_MANIFEST[encryption]}" != "luks2") ]]; then
    wsr_error "The backup manifest timestamp or encryption evidence is invalid."
    return 1
  fi
  local normalized_started normalized_completed compact_started
  normalized_started="$(date -u --date="${WSR_BACKUP_MANIFEST[started_utc]}" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)" || {
    wsr_error "The backup manifest started_utc is not a real UTC timestamp."
    return 1
  }
  normalized_completed="$(date -u --date="${WSR_BACKUP_MANIFEST[completed_utc]}" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)" || {
    wsr_error "The backup manifest completed_utc is not a real UTC timestamp."
    return 1
  }
  compact_started="${normalized_started//-/}"
  compact_started="${compact_started//:/}"
  if [[ "$normalized_started" != "${WSR_BACKUP_MANIFEST[started_utc]}" ||
        "$normalized_completed" != "${WSR_BACKUP_MANIFEST[completed_utc]}" ||
        "$normalized_completed" < "$normalized_started" ||
        "${WSR_BACKUP_MANIFEST[backup_id]}" != "$compact_started"-* ]]; then
    wsr_error "The backup ID and completion time are not bound to one ordered UTC creation interval."
    return 1
  fi
  if [[ ! "${WSR_BACKUP_MANIFEST[postgres_image_revision]}" =~ ^[0-9a-f]{40}$ &&
        "${WSR_BACKUP_MANIFEST[postgres_image_revision]}" != "unavailable" ]]; then
    wsr_error "The backup manifest PostgreSQL OCI revision is invalid."
    return 1
  fi

  for service in "${release_services[@]}"; do
    case "$service" in
      api) expected_prefix="wall-street-receipts-api:" ;;
      web) expected_prefix="wall-street-receipts-web:" ;;
      caddy_production) expected_prefix="wall-street-receipts-caddy:" ;;
    esac
    reference="${WSR_BACKUP_MANIFEST[${service}_image_reference]}"
    image_id="${WSR_BACKUP_MANIFEST[${service}_image_id]}"
    revision="${WSR_BACKUP_MANIFEST[${service}_image_revision]}"
    if [[ "$reference" == "unavailable" || "$image_id" == "unavailable" || "$revision" == "unavailable" ]]; then
      if [[ "$reference" != "unavailable" || "$image_id" != "unavailable" || "$revision" != "unavailable" ]]; then
        wsr_error "Optional release image evidence must be wholly observed or wholly unavailable."
        return 1
      fi
      continue
    fi
    if [[ "$reference" != "$expected_prefix"* ||
          ! "$image_id" =~ ^sha256:[0-9a-f]{64}$ ||
          ! "$revision" =~ ^[0-9a-f]{40}$ ||
          "${reference#"$expected_prefix"}" != "$revision" ]]; then
      wsr_error "Observed release image reference, image ID, and OCI revision do not form one exact identity."
      return 1
    fi
    observed_release_count=$((observed_release_count + 1))
  done

  if ((observed_release_count == 3)) &&
     [[ "${WSR_BACKUP_MANIFEST[api_image_revision]}" == "${WSR_BACKUP_MANIFEST[web_image_revision]}" &&
        "${WSR_BACKUP_MANIFEST[api_image_revision]}" == "${WSR_BACKUP_MANIFEST[caddy_production_image_revision]}" ]]; then
    expected_git_sha="${WSR_BACKUP_MANIFEST[api_image_revision]}"
  fi
  if [[ "${WSR_BACKUP_MANIFEST[git_sha]}" != "$expected_git_sha" ]]; then
    wsr_error "Manifest git_sha must be the one revision shared by all three observed release images, or unavailable."
    return 1
  fi
}

wsr_backup_id_valid() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z-[A-Za-z0-9]{8}$ ]]
}

wsr_latest_backup_id() {
  local candidate latest=""
  [[ -d "$WSR_BACKUPS_ROOT" && ! -L "$WSR_BACKUPS_ROOT" ]] || return 1
  while IFS= read -r candidate; do
    if wsr_backup_id_valid "$candidate" && [[ -z "$latest" || "$candidate" > "$latest" ]]; then
      latest="$candidate"
    fi
  done < <(find "$WSR_BACKUPS_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n')
  [[ -n "$latest" ]] || return 1
  printf '%s\n' "$latest"
}

wsr_validate_completed_backup() {
  local backup_id="$1" artifact manifest dump inventory checksum_file
  local owner mode links actual_bytes actual_inventory_bytes actual_inventory_entries
  local actual_inventory_sha expected_checksum_line
  local -a entries=()

  WSR_VALIDATED_BACKUP_PATH=""
  wsr_backup_id_valid "$backup_id" || {
    wsr_error "Backup identifiers must be generated UTC basenames, never caller paths."
    return 1
  }
  artifact="$WSR_BACKUPS_ROOT/$backup_id"
  if [[ ! -d "$artifact" || -L "$artifact" || "$(realpath -e -- "$artifact")" != "$artifact" ]]; then
    wsr_error "The selected backup artifact is not a regular directory below the verified backup root."
    return 1
  fi
  wsr_require_backup_filesystem_path "$artifact" || return 1
  owner="$(stat -c '%u' -- "$artifact")"
  mode="$(stat -c '%a' -- "$artifact")"
  if [[ "$owner" != "0" || "$mode" != "500" ]]; then
    wsr_error "Completed backup directories must be root-owned and immutable to routine actions (mode 0500)."
    return 1
  fi
  mapfile -t entries < <(find "$artifact" -mindepth 1 -maxdepth 1 -printf '%f\n' | sort)
  if ((${#entries[@]} != 4)) ||
     [[ "${entries[*]}" != "database.dump database.dump.sha256 database.inventory manifest" ]]; then
    wsr_error "A completed backup must contain exactly the dump, checksum, parsed inventory, and manifest."
    return 1
  fi

  dump="$artifact/database.dump"
  checksum_file="$artifact/database.dump.sha256"
  inventory="$artifact/database.inventory"
  manifest="$artifact/manifest"
  for file in "$dump" "$checksum_file" "$inventory" "$manifest"; do
    if [[ ! -f "$file" || -L "$file" ]]; then
      wsr_error "Completed backup members must be regular non-symlink files."
      return 1
    fi
    owner="$(stat -c '%u' -- "$file")"
    mode="$(stat -c '%a' -- "$file")"
    links="$(stat -c '%h' -- "$file")"
    if [[ "$owner" != "0" || "$mode" != "400" || "$links" != "1" ]]; then
      wsr_error "Completed backup files must be root-owned, single-linked, and mode 0400."
      return 1
    fi
    wsr_require_backup_filesystem_path "$file" || return 1
  done

  wsr_load_backup_manifest "$manifest" || return 1
  if [[ "${WSR_BACKUP_MANIFEST[backup_id]}" != "$backup_id" ]]; then
    wsr_error "The manifest backup_id does not match its artifact directory."
    return 1
  fi
  actual_bytes="$(stat -c '%s' -- "$dump")"
  if [[ "$actual_bytes" != "${WSR_BACKUP_MANIFEST[archive_bytes]}" ]]; then
    wsr_error "The backup archive byte count does not match its manifest."
    return 1
  fi
  actual_inventory_bytes="$(stat -c '%s' -- "$inventory")"
  actual_inventory_entries="$(awk '!/^[;[:space:]]*$/ {count++} END {print count + 0}' "$inventory")"
  actual_inventory_sha="$(sha256sum -- "$inventory" | awk '{print $1}')"
  if [[ "$actual_inventory_bytes" != "${WSR_BACKUP_MANIFEST[archive_inventory_bytes]}" ||
        "$actual_inventory_entries" != "${WSR_BACKUP_MANIFEST[archive_inventory_entries]}" ||
        "$actual_inventory_sha" != "${WSR_BACKUP_MANIFEST[archive_inventory_sha256]}" ]]; then
    wsr_error "The parsed pg_restore inventory does not match its manifest evidence."
    return 1
  fi
  expected_checksum_line="${WSR_BACKUP_MANIFEST[archive_sha256]}  database.dump"
  if [[ "$(<"$checksum_file")" != "$expected_checksum_line" ]]; then
    wsr_error "The checksum file is not the exact manifest-bound database.dump checksum."
    return 1
  fi
  if ! (cd -- "$artifact" && sha256sum --check --strict --status database.dump.sha256); then
    wsr_error "The database archive SHA-256 verification failed."
    return 1
  fi
  WSR_VALIDATED_BACKUP_PATH="$artifact"
  wsr_pass "Backup $backup_id has exact members, permissions, byte length, and SHA-256 evidence."
}

wsr_fsync_path() {
  sync -- "$1"
}

wsr_publish_directory_no_clobber() {
  local source="$1" destination="$2" source_identity destination_identity
  local source_device destination_parent_device
  if [[ ! -d "$source" || -L "$source" || -e "$destination" || -L "$destination" ]]; then
    wsr_error "Atomic publication requires one owned source directory and an absent destination."
    return 1
  fi
  wsr_require_backup_filesystem_path "$source" || return 1
  wsr_require_backup_filesystem_path "${destination%/*}" || return 1
  source_identity="$(stat -c '%d:%i' -- "$source")" || {
    wsr_error "The staged directory identity could not be read before publication."
    return 1
  }
  source_device="$(stat -c '%d' -- "$source")" || {
    wsr_error "The staged directory device could not be read before publication."
    return 1
  }
  destination_parent_device="$(stat -c '%d' -- "${destination%/*}")" || {
    wsr_error "The publication parent device could not be read."
    return 1
  }
  if [[ "$source_device" != "$destination_parent_device" ]]; then
    wsr_error "Atomic publication source and destination are not on the same filesystem."
    return 1
  fi

  # GNU mv uses a no-replace rename for --no-clobber on Linux. Its zero exit
  # status can also mean "skipped", so prove publication by source disappearance
  # and the destination's exact pre-recorded device/inode identity.
  if ! mv --no-clobber --no-target-directory -- "$source" "$destination"; then
    wsr_error "The no-clobber atomic directory rename failed."
    return 1
  fi
  if [[ -e "$source" || -L "$source" || ! -d "$destination" || -L "$destination" ]]; then
    wsr_error "The no-clobber rename was skipped or produced an invalid destination."
    return 1
  fi
  destination_identity="$(stat -c '%d:%i' -- "$destination")" || {
    wsr_error "The published directory identity could not be read."
    return 1
  }
  if [[ "$destination_identity" != "$source_identity" ]]; then
    wsr_error "The published directory is not the exact staged inode; refusing ambiguous success."
    return 1
  fi
}

wsr_require_root_operator() {
  if ((EUID != 0)); then
    wsr_error "Production recovery operations require an explicit root operator (run with sudo)."
    return 1
  fi
}

wsr_run_host_preflight() {
  wsr_require_tools || return 1
  wsr_initialize_local_docker || return 1
  wsr_pass "Recovery host prerequisites pass without reading production backup configuration."
}

wsr_run_storage_preflight() {
  wsr_require_root_operator || return 1
  wsr_require_tools || return 1
  wsr_initialize_local_docker || return 1
  wsr_load_backup_config || return 1
  wsr_validate_backup_mount || return 1
}

wsr_run_production_preflight() {
  wsr_run_storage_preflight || return 1
  wsr_validate_production_postgres || return 1
  wsr_pass "Recovery production preflight passed without DNS, Caddy, Git, or secret inspection."
}
