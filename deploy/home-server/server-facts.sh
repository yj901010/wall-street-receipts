#!/usr/bin/env bash
set -euo pipefail

export LC_ALL=C
export LANG=C
export TZ=UTC
IFS=$' \t\n'
readonly WSR_FACTS_TRUSTED_PATH=/usr/sbin:/usr/bin:/sbin:/bin
export PATH="$WSR_FACTS_TRUSTED_PATH"

readonly WSR_FACTS_VALUE_LIMIT=256
readonly WSR_FACTS_REPORT_LIMIT=32768
readonly WSR_FACTS_CAPTURE_LIMIT=131072
readonly WSR_FACTS_COMMAND_TIMEOUT_SECONDS=4
readonly WSR_FACTS_DOCKER_SOCKET=/var/run/docker.sock
readonly WSR_FACTS_DOCKER_HOST=unix:///var/run/docker.sock
readonly WSR_FACTS_DOCKER_CONFIG=/var/empty/wall-street-receipts-server-facts
readonly WSR_FACTS_COMPOSE_PROJECT=wall-street-receipts-home
readonly WSR_FACTS_COMPOSE_VOLUME=wall-street-receipts-home_postgres-data

for inherited_function in timeout head cat date uname getconf stat findmnt df docker ss systemctl env printf; do
  builtin unset -f "$inherited_function" 2>/dev/null || true
done

usage() {
  printf '%s\n' \
    'Usage: server-facts.sh [--backup-mount ABS_PATH] [--output stdout]' \
    '' \
    'Collect a bounded, read-only report from the future Ubuntu home server.' \
    'No argument is required. Without --backup-mount, backup facts are reported as' \
    'not-provided. The command never reads an env file or secret and never performs' \
    'an external network request, package installation, mount, or other mutation.'
}

main() {
backup_mount=""
output_target="stdout"
backup_mount_seen=false
output_target_seen=false
while (($# > 0)); do
  case "$1" in
    --backup-mount)
      (($# >= 2)) || { usage >&2; exit 64; }
      [[ "$backup_mount_seen" == false ]] || {
        printf 'ERROR: --backup-mount may be supplied only once.\n' >&2
        exit 64
      }
      backup_mount_seen=true
      backup_mount="$2"
      shift 2
      ;;
    --output)
      (($# >= 2)) || { usage >&2; exit 64; }
      [[ "$output_target_seen" == false ]] || {
        printf 'ERROR: --output may be supplied only once.\n' >&2
        exit 64
      }
      output_target_seen=true
      output_target="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 64
      ;;
  esac
done

[[ "$output_target" == "stdout" ]] || {
  printf 'ERROR: --output accepts only stdout.\n' >&2
  exit 64
}

validate_absolute_path() {
  local value="$1" component
  [[ -n "$value" && ${#value} -le "$WSR_FACTS_VALUE_LIMIT" ]] || return 1
  [[ "$value" == /* && "$value" =~ ^/[A-Za-z0-9._/-]+$ ]] || return 1
  [[ "$value" != *"//"* && "$value" != */ && "$value" != "/" ]] || return 1
  IFS='/' read -r -a path_components <<< "$value"
  for component in "${path_components[@]}"; do
    [[ -z "$component" ]] && continue
    [[ "$component" != "." && "$component" != ".." ]] || return 1
  done
}

if [[ -n "$backup_mount" ]] && ! validate_absolute_path "$backup_mount"; then
  printf 'ERROR: --backup-mount must be a canonical absolute ASCII path without dot components.\n' >&2
  exit 64
fi

# Environment values are never inspected or printed. Names in override and
# network-related namespaces are removed before any child command can observe
# them. Docker is then pinned to the one reviewed local Unix socket.
while IFS= read -r inherited_name; do
  case "$inherited_name" in
    DOCKER_*|COMPOSE_*|WSR_*|BUILDKIT_*|BUILDX_*|HTTP_PROXY|HTTPS_PROXY|ALL_PROXY|NO_PROXY|http_proxy|https_proxy|all_proxy|no_proxy)
      unset "$inherited_name" 2>/dev/null || true
      ;;
  esac
done < <(
  compgen -e
)

readonly -a FACT_KEYS=(
  schema_version
  collector_name
  collection_mode
  collection_status
  collected_at_utc
  external_network_calls
  environment_files_read
  secret_contents_read
  address_values_reported
  sanitized_value_count
  tool_env_status
  tool_timeout_status
  tool_findmnt_status
  tool_df_status
  tool_docker_status
  tool_ss_status
  tool_systemctl_status
  os_status
  os_id
  os_version_id
  kernel_release
  architecture
  cpu_status
  cpu_model
  cpu_logical_count
  memory_status
  memory_total_bytes
  memory_available_bytes
  docker_socket_path
  docker_socket_status
  docker_cli_status
  docker_client_version
  docker_daemon_status
  docker_server_version
  docker_server_architecture
  docker_root_dir
  docker_storage_driver
  docker_cgroup_version
  docker_ownership_boundary
  docker_compose_status
  docker_compose_version
  compose_project_expected
  compose_volume_expected
  compose_volume_status
  compose_volume_name
  compose_volume_driver
  compose_volume_scope
  compose_volume_label_match
  compose_volume_mountpoint
  root_path
  root_path_status
  root_mount_status
  root_filesystem
  root_mount_options_safe
  root_device_major_minor
  root_capacity_bytes
  root_free_bytes
  control_path
  control_path_status
  control_mount_status
  control_filesystem
  control_mount_options_safe
  control_device_major_minor
  control_capacity_bytes
  control_free_bytes
  docker_root_path
  docker_root_path_status
  docker_root_mount_status
  docker_root_filesystem
  docker_root_mount_options_safe
  docker_root_device_major_minor
  docker_root_capacity_bytes
  docker_root_free_bytes
  compose_volume_path
  compose_volume_path_status
  compose_volume_mount_status
  compose_volume_filesystem
  compose_volume_mount_options_safe
  compose_volume_device_major_minor
  compose_volume_capacity_bytes
  compose_volume_free_bytes
  backup_input_status
  backup_path
  backup_path_status
  backup_mount_status
  backup_filesystem
  backup_mount_options_safe
  backup_mount_options_omitted_count
  backup_device_major_minor
  backup_capacity_bytes
  backup_free_bytes
  backup_exact_mountpoint
  port_80_status
  port_80_listener_count
  port_80_records_truncated
  port_80_bind_scope
  port_80_owner_metadata
  port_80_owner_metadata_truncated
  port_443_status
  port_443_listener_count
  port_443_records_truncated
  port_443_bind_scope
  port_443_owner_metadata
  port_443_owner_metadata_truncated
  init_system
  systemd_state
  docker_service_enabled
  docker_service_active
  docker_socket_enabled
  docker_socket_active
  reboot_required
  postgres_container_status
  postgres_container_state
  postgres_restart_policy
  api_container_status
  api_container_state
  api_restart_policy
  web_container_status
  web_container_state
  web_restart_policy
  caddy_production_container_status
  caddy_production_container_state
  caddy_production_restart_policy
  restart_policy_gate
  bootstrap_gate
)

declare -A FACTS=()
for fact_key in "${FACT_KEYS[@]}"; do
  FACTS["$fact_key"]="unknown"
done

sanitized_value_count=0
SAFE_VALUE=""
sanitize_value() {
  local candidate="$1"
  SAFE_VALUE="$candidate"
  if [[ -z "$candidate" || ${#candidate} -gt "$WSR_FACTS_VALUE_LIMIT" ||
        ! "$candidate" =~ ^[[:print:]]+$ || "$candidate" == *"="* ]]; then
    SAFE_VALUE="unknown"
    sanitized_value_count=$((sanitized_value_count + 1))
  fi
}

set_fact() {
  local key="$1" value="$2"
  if [[ ${FACTS[$key]+present} != "present" ]]; then
    printf 'ERROR: internal unknown fact key: %s\n' "$key" >&2
    exit 70
  fi
  sanitize_value "$value"
  FACTS["$key"]="$SAFE_VALUE"
}

declare -A TOOL_AVAILABLE=()
tool_status() {
  local key="$1" command_name="$2"
  if command -v "$command_name" >/dev/null 2>&1; then
    TOOL_AVAILABLE["$command_name"]=true
    [[ -z "$key" ]] || set_fact "$key" "available"
  else
    TOOL_AVAILABLE["$command_name"]=false
    [[ -z "$key" ]] || set_fact "$key" "missing"
  fi
}

tool_status tool_env_status env
tool_status tool_timeout_status timeout
tool_status "" head
tool_status "" cat
tool_status "" date
tool_status "" uname
tool_status "" getconf
tool_status "" stat
tool_status tool_findmnt_status findmnt
tool_status tool_df_status df
tool_status tool_docker_status docker
tool_status tool_ss_status ss
tool_status tool_systemctl_status systemctl

CAPTURE_OUTPUT=""
CAPTURE_EXIT=127
bounded_capture() {
  local captured="" status=127
  CAPTURE_OUTPUT=""
  CAPTURE_EXIT=127
  [[ "${TOOL_AVAILABLE[env]}" == true && "${TOOL_AVAILABLE[timeout]}" == true && "${TOOL_AVAILABLE[head]}" == true ]] || return 0

  set +e
  captured="$(
    set -o pipefail
    /usr/bin/env -i \
      PATH="$WSR_FACTS_TRUSTED_PATH" LC_ALL=C LANG=C TZ=UTC \
      DOCKER_HOST="$WSR_FACTS_DOCKER_HOST" DOCKER_CONFIG="$WSR_FACTS_DOCKER_CONFIG" \
      timeout --signal=TERM --kill-after=1s 4s "$@" 2>/dev/null |
      /usr/bin/env -i PATH="$WSR_FACTS_TRUSTED_PATH" LC_ALL=C LANG=C TZ=UTC \
        head -c 131073
  )"
  status=$?
  set -e

  if ((${#captured} > WSR_FACTS_CAPTURE_LIMIT)); then
    CAPTURE_OUTPUT=""
    CAPTURE_EXIT=125
    return 0
  fi
  CAPTURE_OUTPUT="$captured"
  CAPTURE_EXIT="$status"
}

trim_ascii_space() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

single_line_value() {
  local value="$1"
  [[ -n "$value" && "$value" != *$'\n'* && "$value" != *$'\r'* ]]
}

set_fact schema_version "1"
set_fact collector_name "wsr-server-facts"
set_fact collection_mode "read-only-local"
set_fact collection_status "pending"
set_fact external_network_calls "disabled-by-contract"
set_fact environment_files_read "none"
set_fact secret_contents_read "none"
set_fact address_values_reported "none"

if [[ "${TOOL_AVAILABLE[date]}" == true ]]; then
  bounded_capture date -u +%Y-%m-%dT%H:%M:%SZ
  if [[ "$CAPTURE_EXIT" == "0" && "$CAPTURE_OUTPUT" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]; then
    set_fact collected_at_utc "$CAPTURE_OUTPUT"
  fi
fi

path_type() {
  local path="$1"
  PATH_TYPE_RESULT="unknown"
  [[ "${TOOL_AVAILABLE[stat]}" == true ]] || return 0
  bounded_capture stat -c %F -- "$path"
  if [[ "$CAPTURE_EXIT" != "0" ]]; then
    if [[ "$CAPTURE_EXIT" == "1" ]]; then
      PATH_TYPE_RESULT="missing-or-inaccessible"
    else
      PATH_TYPE_RESULT="not-observed"
    fi
    return 0
  fi
  case "$CAPTURE_OUTPUT" in
    directory) PATH_TYPE_RESULT="present-directory" ;;
    "regular file") PATH_TYPE_RESULT="present-file" ;;
    "symbolic link") PATH_TYPE_RESULT="symlink" ;;
    socket) PATH_TYPE_RESULT="socket" ;;
    *) PATH_TYPE_RESULT="present-other" ;;
  esac
}

# OS release is parsed as data. It is not sourced and no general environment
# expansion, quote evaluation, or command substitution is performed.
if [[ "${TOOL_AVAILABLE[cat]}" == true ]]; then
  os_release_path=""
  path_type /etc/os-release
  etc_os_release_type="$PATH_TYPE_RESULT"
  if [[ "$etc_os_release_type" == "present-file" ]]; then
    os_release_path=/etc/os-release
  elif [[ "$etc_os_release_type" == "symlink" || "$etc_os_release_type" == "missing-or-inaccessible" ]]; then
    # Do not follow an arbitrary link target. Ubuntu's standard fallback path
    # is read only when it is independently observed as a regular file.
    path_type /usr/lib/os-release
    if [[ "$PATH_TYPE_RESULT" == "present-file" ]]; then
      os_release_path=/usr/lib/os-release
    fi
  fi
  if [[ -n "$os_release_path" ]]; then
    bounded_capture cat "$os_release_path"
    if [[ "$CAPTURE_EXIT" == "0" ]]; then
      os_id=""
      os_version_id=""
      os_line_count=0
      while IFS= read -r os_line || [[ -n "$os_line" ]]; do
        os_line_count=$((os_line_count + 1))
        ((os_line_count <= 128)) || { os_id=""; os_version_id=""; break; }
        case "$os_line" in
          ID=*) os_id="${os_line#ID=}" ;;
          VERSION_ID=*) os_version_id="${os_line#VERSION_ID=}" ;;
        esac
      done <<< "$CAPTURE_OUTPUT"
      if [[ "$os_id" == \"*\" && "$os_id" == *\" ]]; then
        os_id="${os_id:1:${#os_id}-2}"
      fi
      if [[ "$os_version_id" == \"*\" && "$os_version_id" == *\" ]]; then
        os_version_id="${os_version_id:1:${#os_version_id}-2}"
      fi
      if [[ "$os_id" =~ ^[a-z0-9._-]{1,64}$ && "$os_version_id" =~ ^[0-9A-Za-z._-]{1,64}$ ]]; then
        set_fact os_status "observed"
        set_fact os_id "$os_id"
        set_fact os_version_id "$os_version_id"
      else
        set_fact os_status "unknown"
      fi
    fi
  elif [[ "$etc_os_release_type" == "missing-or-inaccessible" ]]; then
    set_fact os_status "missing-or-inaccessible"
  else
    set_fact os_status "unapproved-or-unavailable-path"
  fi
fi

if [[ "${TOOL_AVAILABLE[uname]}" == true ]]; then
  bounded_capture uname -r
  if [[ "$CAPTURE_EXIT" == "0" ]] && single_line_value "$CAPTURE_OUTPUT"; then
    set_fact kernel_release "$CAPTURE_OUTPUT"
  fi
  bounded_capture uname -m
  if [[ "$CAPTURE_EXIT" == "0" && "$CAPTURE_OUTPUT" =~ ^[A-Za-z0-9._-]{1,64}$ ]]; then
    set_fact architecture "$CAPTURE_OUTPUT"
  fi
fi

cpu_model=""
cpu_logical=""
if [[ "${TOOL_AVAILABLE[cat]}" == true ]]; then
  bounded_capture cat /proc/cpuinfo
  if [[ "$CAPTURE_EXIT" == "0" ]]; then
    cpu_line_count=0
    while IFS= read -r cpu_line || [[ -n "$cpu_line" ]]; do
      cpu_line_count=$((cpu_line_count + 1))
      ((cpu_line_count <= 8192)) || break
      if [[ -z "$cpu_model" && "$cpu_line" =~ ^(model[[:space:]]name|Hardware|Processor)[[:space:]]*:[[:space:]]*(.+)$ ]]; then
        cpu_model="$(trim_ascii_space "${BASH_REMATCH[2]}")"
      fi
    done <<< "$CAPTURE_OUTPUT"
  fi
fi
if [[ "${TOOL_AVAILABLE[getconf]}" == true ]]; then
  bounded_capture getconf _NPROCESSORS_ONLN
  if [[ "$CAPTURE_EXIT" == "0" && "$CAPTURE_OUTPUT" =~ ^[1-9][0-9]{0,5}$ ]]; then
    cpu_logical="$CAPTURE_OUTPUT"
  fi
fi
if [[ -n "$cpu_model" ]]; then
  set_fact cpu_model "$cpu_model"
fi
if [[ -n "$cpu_logical" ]]; then
  set_fact cpu_logical_count "$cpu_logical"
fi
if [[ -n "$cpu_model" && -n "$cpu_logical" ]]; then
  set_fact cpu_status "observed"
elif [[ -n "$cpu_model" || -n "$cpu_logical" ]]; then
  set_fact cpu_status "partial"
fi

kib_to_bytes() {
  local kib="$1" kib_number bytes
  KIB_BYTES_RESULT=""
  [[ "$kib" =~ ^[0-9]{1,15}$ ]] || return 0
  kib_number="$((10#$kib))"
  ((kib_number <= 9007199254740991)) || return 0
  bytes="$((kib_number * 1024))"
  ((bytes >= 0 && bytes / 1024 == kib_number)) || return 0
  KIB_BYTES_RESULT="$bytes"
}

memory_total_kib=""
memory_available_kib=""
if [[ "${TOOL_AVAILABLE[cat]}" == true ]]; then
  bounded_capture cat /proc/meminfo
  if [[ "$CAPTURE_EXIT" == "0" ]]; then
    memory_line_count=0
    while IFS= read -r memory_line || [[ -n "$memory_line" ]]; do
      memory_line_count=$((memory_line_count + 1))
      ((memory_line_count <= 256)) || { memory_total_kib=""; memory_available_kib=""; break; }
      if [[ "$memory_line" =~ ^MemTotal:[[:space:]]+([0-9]{1,15})[[:space:]]+kB$ ]]; then
        memory_total_kib="${BASH_REMATCH[1]}"
      elif [[ "$memory_line" =~ ^MemAvailable:[[:space:]]+([0-9]{1,15})[[:space:]]+kB$ ]]; then
        memory_available_kib="${BASH_REMATCH[1]}"
      fi
    done <<< "$CAPTURE_OUTPUT"
  fi
fi
if [[ -n "$memory_total_kib" ]]; then
  kib_to_bytes "$memory_total_kib"
  [[ -n "$KIB_BYTES_RESULT" ]] && set_fact memory_total_bytes "$KIB_BYTES_RESULT"
fi
if [[ -n "$memory_available_kib" ]]; then
  kib_to_bytes "$memory_available_kib"
  [[ -n "$KIB_BYTES_RESULT" ]] && set_fact memory_available_bytes "$KIB_BYTES_RESULT"
fi
if [[ "${FACTS[memory_total_bytes]}" != "unknown" && "${FACTS[memory_available_bytes]}" != "unknown" ]]; then
  set_fact memory_status "observed"
elif [[ "${FACTS[memory_total_bytes]}" != "unknown" || "${FACTS[memory_available_bytes]}" != "unknown" ]]; then
  set_fact memory_status "partial"
fi

set_fact docker_socket_path "$WSR_FACTS_DOCKER_SOCKET"
path_type "$WSR_FACTS_DOCKER_SOCKET"
case "$PATH_TYPE_RESULT" in
  socket) set_fact docker_socket_status "observed-socket" ;;
  missing-or-inaccessible) set_fact docker_socket_status "missing-or-inaccessible" ;;
  *) set_fact docker_socket_status "$PATH_TYPE_RESULT" ;;
esac

if [[ "${FACTS[tool_docker_status]}" == "available" ]]; then
  bounded_capture docker --version
  if [[ "$CAPTURE_EXIT" == "0" && "$CAPTURE_OUTPUT" =~ ^Docker[[:space:]]version[[:space:]]([^,[:space:]]{1,64}) ]]; then
    docker_client_version="${BASH_REMATCH[1]}"
    set_fact docker_cli_status "observed"
    set_fact docker_client_version "$docker_client_version"
  else
    set_fact docker_cli_status "unknown"
  fi

  bounded_capture docker compose version --short
  if [[ "$CAPTURE_EXIT" == "0" && "$CAPTURE_OUTPUT" =~ ^v?[0-9][0-9A-Za-z.+_-]{0,63}$ ]]; then
    set_fact docker_compose_status "observed"
    set_fact docker_compose_version "${CAPTURE_OUTPUT#v}"
  else
    set_fact docker_compose_status "missing-or-unknown"
  fi
else
  set_fact docker_cli_status "missing"
  set_fact docker_compose_status "missing"
fi

docker_daemon_ready=false
if [[ "${FACTS[docker_socket_status]}" == "observed-socket" && "${FACTS[tool_docker_status]}" == "available" ]]; then
  docker_info_template='{{.ServerVersion}}|{{.DockerRootDir}}|{{.Driver}}|{{.OperatingSystem}}|{{.Architecture}}|{{.CgroupVersion}}|{{range .SecurityOptions}}{{printf "%s," .}}{{end}}'
  bounded_capture docker info --format "$docker_info_template"
  if [[ "$CAPTURE_EXIT" == "0" ]] && single_line_value "$CAPTURE_OUTPUT"; then
    IFS='|' read -r docker_server_version docker_root_dir docker_storage_driver docker_server_os docker_server_architecture docker_cgroup_version docker_security_options docker_extra <<< "$CAPTURE_OUTPUT" || true
    if [[ -z "${docker_extra:-}" && "$docker_server_version" =~ ^[0-9A-Za-z.+_-]{1,64}$ &&
          "$docker_root_dir" == /* && "$docker_storage_driver" =~ ^[A-Za-z0-9._+-]{1,64}$ &&
          "$docker_server_architecture" =~ ^[A-Za-z0-9._-]{1,64}$ && "$docker_cgroup_version" =~ ^[12]$ ]]; then
      set_fact docker_daemon_status "observed-local"
      set_fact docker_server_version "$docker_server_version"
      set_fact docker_server_architecture "$docker_server_architecture"
      set_fact docker_root_dir "$docker_root_dir"
      set_fact docker_storage_driver "$docker_storage_driver"
      set_fact docker_cgroup_version "v$docker_cgroup_version"
      if [[ ",$docker_security_options," == *",name=rootless,"* ||
            ",$docker_security_options," == *",name=userns,"* ]]; then
        set_fact docker_ownership_boundary "rootless-or-userns"
      else
        set_fact docker_ownership_boundary "rootful-no-userns"
      fi
      docker_daemon_ready=true
    fi
  fi
  if [[ "$docker_daemon_ready" != true ]]; then
    set_fact docker_daemon_status "unreachable-or-unknown"
  fi
else
  set_fact docker_daemon_status "not-queried"
fi

set_fact compose_project_expected "$WSR_FACTS_COMPOSE_PROJECT"
set_fact compose_volume_expected "$WSR_FACTS_COMPOSE_VOLUME"
compose_volume_mountpoint=""
if [[ "$docker_daemon_ready" == true ]]; then
  volume_template='{{.Name}}|{{.Driver}}|{{.Scope}}|{{.Mountpoint}}|{{with .Labels}}{{index . "com.docker.compose.project"}}{{end}}|{{with .Labels}}{{index . "com.docker.compose.volume"}}{{end}}'
  bounded_capture docker volume inspect --format "$volume_template" "$WSR_FACTS_COMPOSE_VOLUME"
  if [[ "$CAPTURE_EXIT" == "0" ]] && single_line_value "$CAPTURE_OUTPUT"; then
    IFS='|' read -r volume_name volume_driver volume_scope volume_mountpoint volume_project_label volume_logical_label volume_extra <<< "$CAPTURE_OUTPUT" || true
    if [[ -z "${volume_extra:-}" && "$volume_name" == "$WSR_FACTS_COMPOSE_VOLUME" &&
          "$volume_driver" =~ ^[A-Za-z0-9._+-]{1,64}$ && "$volume_scope" =~ ^[A-Za-z0-9._+-]{1,64}$ &&
          "$volume_mountpoint" == /* ]]; then
      set_fact compose_volume_status "observed"
      set_fact compose_volume_name "$volume_name"
      set_fact compose_volume_driver "$volume_driver"
      set_fact compose_volume_scope "$volume_scope"
      set_fact compose_volume_mountpoint "$volume_mountpoint"
      compose_volume_mountpoint="$volume_mountpoint"
      if [[ "$volume_project_label" == "$WSR_FACTS_COMPOSE_PROJECT" && "$volume_logical_label" == "postgres-data" ]]; then
        set_fact compose_volume_label_match "true"
      else
        set_fact compose_volume_label_match "false"
        set_fact compose_volume_status "observed-label-mismatch"
      fi
    else
      set_fact compose_volume_status "invalid-observation"
    fi
  elif [[ "$CAPTURE_EXIT" == "1" ]]; then
    set_fact compose_volume_status "not-present"
  else
    set_fact compose_volume_status "not-observed"
  fi
else
  set_fact compose_volume_status "not-queried"
fi

filter_mount_options() {
  local raw="$1" option joined="" count=0 omitted=0
  local -A seen_options=()
  local -a safe_option_order=(rw ro nodev dev nosuid suid noexec exec relatime noatime strictatime lazytime sync async dirsync discard nodiscard seclabel)
  MOUNT_OPTIONS_SAFE="unknown"
  MOUNT_OPTIONS_OMITTED="unknown"
  [[ -n "$raw" && "$raw" != *$'\n'* && "$raw" != *$'\r'* ]] || return 0
  IFS=',' read -r -a mount_option_parts <<< "$raw"
  ((${#mount_option_parts[@]} <= 128)) || return 0
  for option in "${mount_option_parts[@]}"; do
    count=$((count + 1))
    case "$option" in
      rw|ro|nodev|dev|nosuid|suid|noexec|exec|relatime|noatime|strictatime|lazytime|sync|async|dirsync|discard|nodiscard|seclabel)
        seen_options["$option"]=1
        ;;
      *) omitted=$((omitted + 1)) ;;
    esac
  done
  ((count > 0)) || return 0
  for option in "${safe_option_order[@]}"; do
    if [[ ${seen_options[$option]+present} == "present" ]]; then
      [[ -z "$joined" ]] || joined+=","
      joined+="$option"
    fi
  done
  [[ -n "$joined" ]] || joined="none"
  MOUNT_OPTIONS_SAFE="$joined"
  MOUNT_OPTIONS_OMITTED="$omitted"
}

collect_path_facts() {
  local prefix="$1" path="$2" type_result mount_target mount_fstype mount_options mount_major_minor
  local df_line="" df_size="" df_available=""
  set_fact "${prefix}_path" "$path"
  path_type "$path"
  type_result="$PATH_TYPE_RESULT"
  set_fact "${prefix}_path_status" "$type_result"
  [[ "$type_result" == "present-directory" || "$type_result" == "present-file" ]] || return 0

  [[ "${FACTS[tool_findmnt_status]}" == "available" ]] || return 0
  bounded_capture findmnt -rn --target "$path" -o TARGET
  [[ "$CAPTURE_EXIT" == "0" ]] || return 0
  mount_target="$CAPTURE_OUTPUT"
  bounded_capture findmnt -rn --target "$path" -o FSTYPE
  [[ "$CAPTURE_EXIT" == "0" ]] || return 0
  mount_fstype="$CAPTURE_OUTPUT"
  bounded_capture findmnt -rn --target "$path" -o OPTIONS
  [[ "$CAPTURE_EXIT" == "0" ]] || return 0
  mount_options="$CAPTURE_OUTPUT"
  bounded_capture findmnt -rn --target "$path" -o MAJ:MIN
  [[ "$CAPTURE_EXIT" == "0" ]] || return 0
  mount_major_minor="$CAPTURE_OUTPUT"

  if ! single_line_value "$mount_target" || ! [[ "$mount_fstype" =~ ^[A-Za-z0-9._+-]{1,64}$ ]] ||
     ! [[ "$mount_major_minor" =~ ^[0-9]{1,7}:[0-9]{1,7}$ ]]; then
    return 0
  fi
  filter_mount_options "$mount_options"
  [[ "$MOUNT_OPTIONS_SAFE" != "unknown" ]] || return 0
  set_fact "${prefix}_mount_status" "observed"
  set_fact "${prefix}_filesystem" "$mount_fstype"
  set_fact "${prefix}_mount_options_safe" "$MOUNT_OPTIONS_SAFE"
  if [[ ${FACTS[${prefix}_mount_options_omitted_count]+present} == "present" ]]; then
    set_fact "${prefix}_mount_options_omitted_count" "$MOUNT_OPTIONS_OMITTED"
  fi
  set_fact "${prefix}_device_major_minor" "$mount_major_minor"

  [[ "${FACTS[tool_df_status]}" == "available" ]] || return 0
  bounded_capture df -B1 --output=size,avail -- "$path"
  [[ "$CAPTURE_EXIT" == "0" ]] || return 0
  while IFS= read -r df_candidate || [[ -n "$df_candidate" ]]; do
    if [[ "$df_candidate" =~ ^[[:space:]]*([0-9]{1,18})[[:space:]]+([0-9]{1,18})[[:space:]]*$ ]]; then
      df_line="$df_candidate"
      df_size="${BASH_REMATCH[1]}"
      df_available="${BASH_REMATCH[2]}"
    fi
  done <<< "$CAPTURE_OUTPUT"
  if [[ -n "$df_line" ]]; then
    set_fact "${prefix}_capacity_bytes" "$df_size"
    set_fact "${prefix}_free_bytes" "$df_available"
  fi
}

collect_path_facts root "/"
collect_path_facts control "/var/lib/wall-street-receipts/generation-control"

if [[ "${FACTS[docker_root_dir]}" != "unknown" ]]; then
  collect_path_facts docker_root "${FACTS[docker_root_dir]}"
else
  set_fact docker_root_path "unknown"
fi

if [[ -n "$compose_volume_mountpoint" ]]; then
  collect_path_facts compose_volume "$compose_volume_mountpoint"
else
  set_fact compose_volume_path "unknown"
fi

if [[ -z "$backup_mount" ]]; then
  set_fact backup_input_status "not-provided"
  set_fact backup_path "not-provided"
  set_fact backup_path_status "not-provided"
  set_fact backup_mount_status "not-provided"
  set_fact backup_filesystem "not-provided"
  set_fact backup_mount_options_safe "not-provided"
  set_fact backup_mount_options_omitted_count "not-provided"
  set_fact backup_device_major_minor "not-provided"
  set_fact backup_capacity_bytes "not-provided"
  set_fact backup_free_bytes "not-provided"
  set_fact backup_exact_mountpoint "not-provided"
else
  set_fact backup_input_status "provided"
  set_fact backup_path "$backup_mount"
  if [[ "${FACTS[tool_findmnt_status]}" == "available" ]]; then
    bounded_capture findmnt -rn --mountpoint "$backup_mount" -o TARGET
    if [[ "$CAPTURE_EXIT" == "0" ]] && single_line_value "$CAPTURE_OUTPUT"; then
      if [[ "$CAPTURE_OUTPUT" == "$backup_mount" ]]; then
        set_fact backup_exact_mountpoint "true"
        collect_path_facts backup "$backup_mount"
      else
        set_fact backup_exact_mountpoint "false"
        set_fact backup_path_status "not-exact-mountpoint"
      fi
    elif [[ "$CAPTURE_EXIT" == "1" ]]; then
      set_fact backup_exact_mountpoint "false"
      set_fact backup_path_status "not-exact-mountpoint"
    else
      set_fact backup_exact_mountpoint "unknown"
      set_fact backup_path_status "not-observed"
    fi
  else
    set_fact backup_path_status "not-observed"
  fi
fi

collect_port_facts() {
  local port="$1" line endpoint host scope_count=0 listener_count=0
  local has_wildcard=false has_loopback=false has_specific=false records_truncated=false
  local owners="" owner_class owner_scan owner_name_re
  local owner_caddy=false owner_nginx=false owner_apache=false owner_docker_proxy=false owner_other=false
  [[ "${FACTS[tool_ss_status]}" == "available" ]] || return 0
  bounded_capture ss -H -ltnp "sport = :$port"
  [[ "$CAPTURE_EXIT" == "0" ]] || return 0

  # Classify the complete bounded capture independently of row order. Only
  # closed process-name classes are retained; addresses, PIDs, and raw names
  # never enter the report.
  [[ "$CAPTURE_OUTPUT" == *'"caddy"'* ]] && owner_caddy=true
  [[ "$CAPTURE_OUTPUT" == *'"nginx"'* ]] && owner_nginx=true
  if [[ "$CAPTURE_OUTPUT" == *'"apache2"'* || "$CAPTURE_OUTPUT" == *'"httpd"'* ]]; then
    owner_apache=true
  fi
  [[ "$CAPTURE_OUTPUT" == *'"docker-proxy"'* ]] && owner_docker_proxy=true
  owner_scan="$CAPTURE_OUTPUT"
  owner_scan="${owner_scan//\"caddy\"/}"
  owner_scan="${owner_scan//\"nginx\"/}"
  owner_scan="${owner_scan//\"apache2\"/}"
  owner_scan="${owner_scan//\"httpd\"/}"
  owner_scan="${owner_scan//\"docker-proxy\"/}"
  owner_name_re='"[A-Za-z0-9._+-]{1,64}"'
  [[ "$owner_scan" =~ $owner_name_re ]] && owner_other=true

  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -n "$line" ]] || continue
    listener_count=$((listener_count + 1))
    if ((listener_count > 16)); then
      records_truncated=true
      break
    fi
    read -r -a socket_columns <<< "$line"
    if ((${#socket_columns[@]} >= 4)); then
      endpoint="${socket_columns[3]}"
      host="${endpoint%:*}"
      case "$host" in
        0.0.0.0|\*|\[::\]|::) has_wildcard=true ;;
        127.*|\[::1\]|::1) has_loopback=true ;;
        *) has_specific=true ;;
      esac
    else
      has_specific=true
    fi
  done <<< "$CAPTURE_OUTPUT"

  if ((listener_count == 0)); then
    set_fact "port_${port}_status" "free"
    set_fact "port_${port}_listener_count" "0"
    set_fact "port_${port}_records_truncated" "false"
    set_fact "port_${port}_bind_scope" "none"
    set_fact "port_${port}_owner_metadata" "none"
    set_fact "port_${port}_owner_metadata_truncated" "false"
    return 0
  fi

  set_fact "port_${port}_status" "listening"
  if [[ "$records_truncated" == true ]]; then
    set_fact "port_${port}_listener_count" "16-plus"
  else
    set_fact "port_${port}_listener_count" "$listener_count"
  fi
  set_fact "port_${port}_records_truncated" "$records_truncated"
  [[ "$has_wildcard" == true ]] && scope_count=$((scope_count + 1))
  [[ "$has_loopback" == true ]] && scope_count=$((scope_count + 1))
  [[ "$has_specific" == true ]] && scope_count=$((scope_count + 1))
  if ((scope_count != 1)); then
    set_fact "port_${port}_bind_scope" "mixed-or-unknown"
  elif [[ "$has_wildcard" == true ]]; then
    set_fact "port_${port}_bind_scope" "wildcard"
  elif [[ "$has_loopback" == true ]]; then
    set_fact "port_${port}_bind_scope" "loopback"
  else
    set_fact "port_${port}_bind_scope" "specific-address-redacted"
  fi
  for owner_class in caddy nginx apache docker-proxy other; do
    case "$owner_class" in
      caddy) [[ "$owner_caddy" == true ]] || continue ;;
      nginx) [[ "$owner_nginx" == true ]] || continue ;;
      apache) [[ "$owner_apache" == true ]] || continue ;;
      docker-proxy) [[ "$owner_docker_proxy" == true ]] || continue ;;
      other) [[ "$owner_other" == true ]] || continue ;;
    esac
    [[ -z "$owners" ]] || owners+=","
    owners+="$owner_class"
  done
  if [[ -n "$owners" ]]; then
    set_fact "port_${port}_owner_metadata" "$owners"
  else
    set_fact "port_${port}_owner_metadata" "unavailable"
  fi
  set_fact "port_${port}_owner_metadata_truncated" "false"
}

collect_port_facts 80
collect_port_facts 443

systemctl_fact() {
  local fact_key="$1" action="$2" unit="$3" observed=""
  [[ "${FACTS[tool_systemctl_status]}" == "available" ]] || return 0
  bounded_capture systemctl "$action" "$unit"
  if single_line_value "$CAPTURE_OUTPUT"; then
    case "$action:$CAPTURE_EXIT:$CAPTURE_OUTPUT" in
      is-enabled:0:enabled|is-enabled:0:enabled-runtime|is-enabled:0:linked|is-enabled:0:linked-runtime|is-enabled:0:alias)
        observed="$CAPTURE_OUTPUT"
        ;;
      is-enabled:1:disabled|is-enabled:1:static|is-enabled:1:indirect|is-enabled:1:generated|is-enabled:1:transient|is-enabled:1:masked)
        observed="$CAPTURE_OUTPUT"
        ;;
      is-enabled:4:not-found)
        observed="not-found"
        ;;
      is-active:0:active)
        observed="active"
        ;;
      is-active:3:inactive|is-active:3:failed|is-active:3:activating|is-active:3:deactivating|is-active:3:reloading)
        observed="$CAPTURE_OUTPUT"
        ;;
      is-active:4:unknown)
        observed="not-found"
        ;;
    esac
  fi
  if [[ -n "$observed" ]]; then
    set_fact "$fact_key" "$observed"
  else
    set_fact "$fact_key" "not-observed"
  fi
}

if [[ "${FACTS[tool_systemctl_status]}" == "available" ]]; then
  set_fact init_system "systemd-command-available"
  bounded_capture systemctl is-system-running
  case "$CAPTURE_EXIT:$CAPTURE_OUTPUT" in
    0:running) set_fact systemd_state "running" ;;
    1:degraded|1:maintenance|1:offline) set_fact systemd_state "$CAPTURE_OUTPUT" ;;
    2:initializing|2:starting|2:stopping) set_fact systemd_state "$CAPTURE_OUTPUT" ;;
    *) set_fact systemd_state "not-observed" ;;
  esac
  systemctl_fact docker_service_enabled is-enabled docker.service
  systemctl_fact docker_service_active is-active docker.service
  systemctl_fact docker_socket_enabled is-enabled docker.socket
  systemctl_fact docker_socket_active is-active docker.socket
else
  set_fact init_system "unknown"
fi

if [[ "${TOOL_AVAILABLE[stat]}" == true ]]; then
  bounded_capture stat -c %F -- /run/reboot-required
  if [[ "$CAPTURE_EXIT" == "0" && "$CAPTURE_OUTPUT" == "regular file" ]]; then
    set_fact reboot_required "yes"
  else
    set_fact reboot_required "not-observed"
  fi
fi

collect_container_facts() {
  local fact_prefix="$1" compose_service="$2" line container_count=0 container_id=""
  local inspect_template state restart_policy project_label service_label inspect_extra
  [[ "$docker_daemon_ready" == true ]] || return 0
  bounded_capture docker ps -a --no-trunc \
    --filter "label=com.docker.compose.project=$WSR_FACTS_COMPOSE_PROJECT" \
    --filter "label=com.docker.compose.service=$compose_service" \
    --format '{{.ID}}'
  [[ "$CAPTURE_EXIT" == "0" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -n "$line" ]] || continue
    container_count=$((container_count + 1))
    if ((container_count == 1)); then
      container_id="$line"
    fi
    ((container_count <= 2)) || break
  done <<< "$CAPTURE_OUTPUT"
  if ((container_count == 0)); then
    set_fact "${fact_prefix}_container_status" "missing"
    return 0
  elif ((container_count != 1)) || [[ ! "$container_id" =~ ^[0-9a-f]{64}$ ]]; then
    set_fact "${fact_prefix}_container_status" "ambiguous-or-invalid"
    return 0
  fi

  inspect_template='{{.State.Status}}|{{.HostConfig.RestartPolicy.Name}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}'
  bounded_capture docker inspect --format "$inspect_template" "$container_id"
  [[ "$CAPTURE_EXIT" == "0" ]] || return 0
  IFS='|' read -r state restart_policy project_label service_label inspect_extra <<< "$CAPTURE_OUTPUT" || true
  if [[ -z "${inspect_extra:-}" && "$state" =~ ^[a-z][a-z-]{0,31}$ &&
        "$restart_policy" =~ ^[a-z][a-z-]{0,31}$ && "$project_label" == "$WSR_FACTS_COMPOSE_PROJECT" &&
        "$service_label" == "$compose_service" ]]; then
    set_fact "${fact_prefix}_container_status" "observed"
    set_fact "${fact_prefix}_container_state" "$state"
    set_fact "${fact_prefix}_restart_policy" "$restart_policy"
  else
    set_fact "${fact_prefix}_container_status" "invalid-observation"
  fi
}

collect_container_facts postgres postgres
collect_container_facts api api
collect_container_facts web web
collect_container_facts caddy_production caddy-production

# Observed facts are inputs to a later operator decision, never proof that a
# server can safely bootstrap or survive a transition/restart. ADR-050 leaves
# that boot-order and generation-restart contract unresolved.
set_fact restart_policy_gate "REVIEW_REQUIRED"
set_fact bootstrap_gate "REVIEW_REQUIRED"

collection_partial=false
for fact_key in "${FACT_KEYS[@]}"; do
  [[ "$fact_key" == "collection_status" || "$fact_key" == "sanitized_value_count" ]] && continue
  if [[ ("$fact_key" == "port_80_owner_metadata" || "$fact_key" == "port_443_owner_metadata") &&
        "${FACTS[$fact_key]}" == "unavailable" ]]; then
    collection_partial=true
  fi
  case "${FACTS[$fact_key]}" in
    unknown|partial|pending|missing|missing-*|not-observed|not-present|not-queried|not-exact-mountpoint|unreachable-*|invalid-*|ambiguous-*|unapproved-*|symlink|present-other)
      collection_partial=true
      ;;
  esac
done
if ((sanitized_value_count > 0)); then
  collection_partial=true
fi
if [[ "$collection_partial" == true ]]; then
  set_fact collection_status "partial"
else
  set_fact collection_status "complete"
fi
set_fact sanitized_value_count "$sanitized_value_count"

emit_report() {
  local key value report_size=0 line_size
  for key in "${FACT_KEYS[@]}"; do
    value="${FACTS[$key]}"
    [[ "$key" =~ ^[a-z][a-z0-9_]*$ ]] || {
      printf 'ERROR: internal invalid fact key.\n' >&2
      exit 70
    }
    [[ -n "$value" && ${#value} -le WSR_FACTS_VALUE_LIMIT &&
       "$value" =~ ^[[:print:]]+$ && "$value" != *"="* ]] || {
      printf 'ERROR: internal unsafe fact value for %s.\n' "$key" >&2
      exit 70
    }
    line_size=$((${#key} + ${#value} + 2))
    report_size=$((report_size + line_size))
  done
  ((report_size <= WSR_FACTS_REPORT_LIMIT)) || {
    printf 'ERROR: bounded report exceeds %d bytes.\n' "$WSR_FACTS_REPORT_LIMIT" >&2
    exit 70
  }
  for key in "${FACT_KEYS[@]}"; do
    printf '%s=%s\n' "$key" "${FACTS[$key]}"
  done
}

emit_report
}

main "$@"
