#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: preflight.sh [--mode host|contract|publish] [--env-file PATH]

host      Check the future Ubuntu host without requiring domain or ingress facts.
contract  Revalidate the production env, source, secret, DNS, and Compose model.
publish   Validate the same contract plus first-deployment ownership of ports 80/443.
USAGE
}

mode="host"
env_file=""
while (($# > 0)); do
  case "$1" in
    --mode)
      (($# >= 2)) || { usage >&2; exit 64; }
      mode="$2"
      shift 2
      ;;
    --env-file)
      (($# >= 2)) || { usage >&2; exit 64; }
      env_file="$2"
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

[[ "$mode" == "host" || "$mode" == "contract" || "$mode" == "publish" ]] || {
  printf 'ERROR: --mode must be host, contract, or publish.\n' >&2
  exit 64
}

failures=0
warnings=0
pass() { printf 'PASS: %s\n' "$1"; }
warn() { printf 'WARN: %s\n' "$1"; warnings=$((warnings + 1)); }
fail() { printf 'FAIL: %s\n' "$1" >&2; failures=$((failures + 1)); }

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/../.." && pwd -P)"
compose_file="$script_dir/compose.yaml"

declare -A env_values=()

validate_env_file() {
  local line key value line_number=0
  local allowed='^(WSR_DOMAIN|WSR_ACME_EMAIL|WSR_IMAGE_TAG|WSR_POSTGRES_PASSWORD_FILE|WSR_INGRESS_MODE|WSR_PUBLIC_IP_POLICY|WSR_PUBLIC_IPV4|WSR_PUBLIC_IPV6|WSR_REHEARSAL_PORT)$'
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ ! "$line" =~ ^([A-Z][A-Z0-9_]*)=([A-Za-z0-9._:/@+-]+)$ ]]; then
      fail "Production env line $line_number must be an unquoted KEY=value with no spaces, comments, interpolation, or export prefix."
      continue
    fi
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    if [[ ! "$key" =~ $allowed ]]; then
      fail "Production env contains an unapproved key at line $line_number: $key."
      continue
    fi
    if [[ -v "env_values[$key]" ]]; then
      fail "Production env contains duplicate key $key."
      continue
    fi
    env_values["$key"]="$value"
  done < "$env_file"

  local required
  for required in \
    WSR_DOMAIN WSR_ACME_EMAIL WSR_IMAGE_TAG WSR_POSTGRES_PASSWORD_FILE \
    WSR_INGRESS_MODE WSR_PUBLIC_IP_POLICY WSR_PUBLIC_IPV4 WSR_PUBLIC_IPV6; do
    [[ -v "env_values[$required]" ]] || fail "Production env is missing required key $required."
  done
}

resolve_docker_endpoint() {
  if [[ -n "${DOCKER_CONTEXT:-}" ]]; then
    docker context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}'
  elif [[ -n "${DOCKER_HOST:-}" ]]; then
    printf '%s\n' "$DOCKER_HOST"
  else
    docker context inspect --format '{{.Endpoints.docker.Host}}'
  fi
}

is_local_docker_endpoint() {
  [[ "$1" =~ ^unix:///.+ || "$1" =~ ^fd://.+ ||
     "$1" =~ ^tcp://127(\.[0-9]{1,3}){3}:[0-9]+$ ||
     "$1" =~ ^tcp://\[::1\]:[0-9]+$ ]]
}

compose_version_supported() {
  local version_core major minor
  version_core="${1#v}"
  version_core="${version_core%%[-+]*}"
  IFS=. read -r major minor _ <<< "$version_core"
  [[ "$major" =~ ^[0-9]+$ && "$minor" =~ ^[0-9]+$ ]] || return 1
  ((major > 2 || (major == 2 && minor >= 20)))
}

if [[ ! -r /etc/os-release ]]; then
  fail "/etc/os-release is missing. This package targets Ubuntu Server."
else
  os_id="$(awk -F= '$1 == "ID" {gsub(/\"/, "", $2); print $2}' /etc/os-release)"
  os_version="$(awk -F= '$1 == "VERSION_ID" {gsub(/\"/, "", $2); print $2}' /etc/os-release)"
  if [[ "$os_id" == "ubuntu" && ("$os_version" == "24.04" || "$os_version" == "26.04") ]]; then
    pass "Ubuntu $os_version is in the supported home-server set."
  else
    fail "Expected Ubuntu 24.04 or 26.04; found ${os_id:-unknown} ${os_version:-unknown}."
  fi
fi

architecture="$(uname -m)"
case "$architecture" in
  x86_64|aarch64|arm64) pass "CPU architecture $architecture is supported by the selected images." ;;
  *) fail "CPU architecture $architecture is not in the supported amd64/arm64 set." ;;
esac

cpu_count="$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf '0')"
if ((cpu_count < 2)); then
  fail "At least 2 logical CPUs are required; detected $cpu_count."
elif ((cpu_count < 4)); then
  warn "Detected $cpu_count logical CPUs; 4 or more are recommended for local image builds."
else
  pass "Detected $cpu_count logical CPUs."
fi

memory_kib="$(awk '/^MemTotal:/ {print $2}' /proc/meminfo 2>/dev/null || printf '0')"
if ((memory_kib < 4 * 1024 * 1024)); then
  fail "At least 4 GiB RAM is required; 8 GiB is recommended for local image builds."
elif ((memory_kib < 8 * 1024 * 1024)); then
  warn "Less than 8 GiB RAM detected; runtime is adequate but image builds may be slow."
else
  pass "At least 8 GiB RAM is available."
fi

available_kib="$(df -Pk "$script_dir" | awk 'NR == 2 {print $4}')"
if ((available_kib < 50 * 1024 * 1024)); then
  fail "At least 50 GiB free storage is required for images, database, and rollback headroom."
else
  pass "At least 50 GiB free storage is available."
fi

docker_local_ready=false
if ! command -v docker >/dev/null 2>&1; then
  fail "Docker Engine is missing. Install it from Docker's official Ubuntu repository."
else
  selected_docker_endpoint="$(resolve_docker_endpoint 2>/dev/null || true)"
  if [[ -z "$selected_docker_endpoint" ]]; then
    fail "The selected Docker endpoint could not be resolved without daemon contact."
  elif ! is_local_docker_endpoint "$selected_docker_endpoint"; then
    fail "The selected Docker endpoint is remote; this runbook permits only a local unix, fd, or numeric-loopback endpoint."
  else
    unset DOCKER_CONTEXT DOCKER_TLS_VERIFY DOCKER_CERT_PATH
    export DOCKER_HOST="$selected_docker_endpoint"
    if ! docker info >/dev/null 2>&1; then
      fail "Docker Engine is installed but the current operator cannot reach the pinned local daemon."
    else
      pass "Docker Engine is reachable through a pinned local endpoint."
      docker_local_ready=true
      docker_security_options="$(docker info --format '{{range .SecurityOptions}}{{println .}}{{end}}' 2>/dev/null || true)"
      if grep -Eq '^name=(rootless|userns)$' <<< "$docker_security_options"; then
        fail "Rootless or daemon-level user-namespace remapping is outside the reviewed numeric secret-ownership contract."
      else
        pass "Docker uses the reviewed rootful, non-userns-remapped ownership boundary."
      fi
    fi
  fi
fi

if [[ "$docker_local_ready" != true ]]; then
  fail "Docker Compose was not queried because no verified local daemon is available."
elif ! docker compose version >/dev/null 2>&1; then
  fail "Docker Compose v2 is missing. Install the docker-compose-plugin package."
else
  compose_version="$(docker compose version --short 2>/dev/null | sed 's/^v//')"
  if compose_version_supported "$compose_version"; then
    pass "Docker Compose v$compose_version is available."
  else
    fail "Docker Compose 2.20.0 or newer is required; found ${compose_version:-unknown}."
  fi
fi

if [[ "$mode" == "contract" || "$mode" == "publish" ]]; then
  mapfile -t inherited_override_names < <(compgen -e | awk '/^(WSR_|COMPOSE_)/ {print}')
  if ((${#inherited_override_names[@]} > 0)); then
    fail "Inherited WSR_* and COMPOSE_* variables are forbidden because they override the reviewed deployment contract. Unset them before preflight."
    for inherited_name in "${inherited_override_names[@]}"; do
      unset "$inherited_name"
    done
  fi

  if [[ -z "$env_file" || ! -f "$env_file" || -L "$env_file" ]]; then
    fail "--env-file must name the regular, non-symlink deploy/home-server/.env.production file."
  else
    resolved_env_file="$(realpath -e -- "$env_file")"
    expected_env_file="$script_dir/.env.production"
    if [[ "$resolved_env_file" != "$expected_env_file" ]]; then
      fail "The production env must be the exact ignored file deploy/home-server/.env.production."
    fi
    validate_env_file

    domain="${env_values[WSR_DOMAIN]:-}"
    acme_email="${env_values[WSR_ACME_EMAIL]:-}"
    image_tag="${env_values[WSR_IMAGE_TAG]:-}"
    secret_path="${env_values[WSR_POSTGRES_PASSWORD_FILE]:-}"
    ingress_mode="${env_values[WSR_INGRESS_MODE]:-}"
    public_ip_policy="${env_values[WSR_PUBLIC_IP_POLICY]:-}"
    public_ipv4="${env_values[WSR_PUBLIC_IPV4]:-}"
    public_ipv6="${env_values[WSR_PUBLIC_IPV6]:-}"

    if [[ "$domain" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$ && "$domain" != "stocks.example.com" ]]; then
      pass "Production domain syntax is valid."
    else
      fail "WSR_DOMAIN must be the exact lowercase ASCII domain (use punycode for an IDN), not a placeholder."
    fi

    if [[ "$acme_email" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ && "$acme_email" != "operator@example.com" ]]; then
      pass "A monitored ACME contact email is configured."
    else
      fail "WSR_ACME_EMAIL must be a monitored non-placeholder address."
    fi

    if ! command -v git >/dev/null 2>&1 || ! git -C "$repo_root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
      fail "A Git checkout is required to bind the release image to reviewed source."
    else
      head_sha="$(git -C "$repo_root" rev-parse HEAD 2>/dev/null || true)"
      if [[ "$image_tag" =~ ^[0-9a-f]{40}$ && "$image_tag" == "$head_sha" ]]; then
        pass "WSR_IMAGE_TAG exactly matches the checked-out 40-character Git HEAD."
      else
        fail "WSR_IMAGE_TAG must exactly equal the checked-out 40-character Git HEAD."
      fi
      if [[ -z "$(git -C "$repo_root" status --porcelain --untracked-files=normal)" ]]; then
        pass "The deployment checkout is clean."
      else
        fail "The deployment checkout has tracked or untracked changes; commit or remove them before building."
      fi
    fi

    if [[ "$secret_path" == "/etc/wall-street-receipts/secrets/postgres_password" &&
          -f "$secret_path" && ! -L "$secret_path" ]]; then
      resolved_secret_path="$(realpath -e -- "$secret_path")"
      secret_mode="$(stat -c '%a' "$secret_path")"
      secret_uid="$(stat -c '%u' "$secret_path")"
      secret_gid="$(stat -c '%g' "$secret_path")"
      secret_links="$(stat -c '%h' "$secret_path")"
      secret_size="$(stat -c '%s' "$secret_path")"
      secret_directory="$(dirname "$secret_path")"
      secret_directory_mode="$(stat -c '%a' "$secret_directory")"
      secret_directory_uid="$(stat -c '%u' "$secret_directory")"
      secret_directory_gid="$(stat -c '%g' "$secret_directory")"
      secret_root_directory="$(dirname "$secret_directory")"
      secret_root_directory_mode="$(stat -c '%a' "$secret_root_directory")"
      secret_root_directory_uid="$(stat -c '%u' "$secret_root_directory")"
      secret_root_directory_gid="$(stat -c '%g' "$secret_root_directory")"
      if [[ "$resolved_secret_path" == "$secret_path" && "$secret_mode" == "400" &&
            "$secret_uid" == "10001" && "$secret_gid" == "10001" &&
            "$secret_links" == "1" && "$secret_size" -ge 32 && "$secret_size" -le 4096 ]]; then
        pass "The PostgreSQL secret has no symlinked parent, one link, bounded non-empty size, mode 400 and owner 10001:10001."
      else
        fail "The PostgreSQL secret must resolve exactly, have one link, contain 32-4096 bytes, use mode 400, and be owned by 10001:10001."
      fi
      if [[ "$secret_directory_mode" == "711" && "$secret_directory_uid" == "0" && "$secret_directory_gid" == "0" ]]; then
        pass "The PostgreSQL secret directory has traversal-only mode 711 and owner root:root."
      else
        fail "The PostgreSQL secret directory must have traversal-only mode 711 and owner root:root; found mode $secret_directory_mode and owner $secret_directory_uid:$secret_directory_gid."
      fi
      if [[ "$secret_root_directory" == "/etc/wall-street-receipts" &&
            "$secret_root_directory_mode" == "711" &&
            "$secret_root_directory_uid" == "0" && "$secret_root_directory_gid" == "0" ]]; then
        pass "The fixed secret root has traversal-only mode 711 and owner root:root."
      else
        fail "The fixed /etc/wall-street-receipts parent must have traversal-only mode 711 and owner root:root."
      fi
    else
      fail "WSR_POSTGRES_PASSWORD_FILE must be the fixed regular non-symlink path /etc/wall-street-receipts/secrets/postgres_password."
    fi

    ingress_mode_valid=false
    case "$ingress_mode" in
      direct-ipv4|direct-ipv6|direct-dual-stack)
        pass "Direct public ingress mode is operator-attested as $ingress_mode."
        ingress_mode_valid=true
        ;;
      cgnat) fail "CGNAT cannot accept direct IPv4 port forwarding. Request a public IP or make a later tunnel decision." ;;
      *) fail "WSR_INGRESS_MODE is unknown. Compare router WAN addressing before public deployment." ;;
    esac

    if [[ "$ingress_mode_valid" == true && -n "$domain" ]]; then
      if ! command -v python3 >/dev/null 2>&1; then
        fail "Python 3 is required for exact public DNS/address-family validation."
      elif python3 - "$domain" "$ingress_mode" "$public_ipv4" "$public_ipv6" >/dev/null 2>&1 <<'PY'
import ipaddress
import socket
import sys

domain, mode, raw_v4, raw_v6 = sys.argv[1:]

def expected(raw: str, version: int):
    if raw == "unknown":
        return None
    value = ipaddress.ip_address(raw)
    if value.version != version or not value.is_global:
        raise ValueError("expected address is not public or has the wrong family")
    return str(value)

def resolved(family: int) -> set[str]:
    try:
        answers = socket.getaddrinfo(domain, None, family, socket.SOCK_STREAM)
    except socket.gaierror:
        return set()
    return {str(ipaddress.ip_address(answer[4][0])) for answer in answers}

v4 = expected(raw_v4, 4)
v6 = expected(raw_v6, 6)
a_records = resolved(socket.AF_INET)
aaaa_records = resolved(socket.AF_INET6)
if any(not ipaddress.ip_address(value).is_global for value in a_records | aaaa_records):
    raise ValueError("DNS contains a non-public address")
if mode == "direct-ipv4":
    valid = v4 is not None and a_records == {v4} and not aaaa_records
elif mode == "direct-ipv6":
    valid = v6 is not None and aaaa_records == {v6} and not a_records
else:
    valid = v4 is not None and v6 is not None and a_records == {v4} and aaaa_records == {v6}
if not valid:
    raise ValueError("DNS does not exactly match the attested direct-ingress addresses")
PY
      then
        pass "DNS address families exactly match the operator-attested public address values."
      else
        fail "DNS must exactly match the configured public address and ingress family; stale A/AAAA records are not allowed."
      fi
    fi

    case "$public_ip_policy" in
      static) pass "A static public address policy is selected." ;;
      dynamic-ddns) fail "Dynamic DNS automation is not implemented in ADR-046; do not publish until a provider and secret flow are approved." ;;
      *) fail "WSR_PUBLIC_IP_POLICY must be static for this initial direct-ingress release." ;;
    esac

    if [[ "$docker_local_ready" == true ]]; then
      if docker compose \
        --env-file "$resolved_env_file" \
        --file "$compose_file" \
        --profile production \
        config --quiet; then
        pass "Docker Compose accepts the exact production env and model."
      else
        fail "Docker Compose rejected the exact production env or model."
      fi
    fi
  fi

  if [[ "$mode" == "publish" ]]; then
    if command -v ss >/dev/null 2>&1; then
      if ss -H -ltn 'sport = :80' | grep -q .; then
        fail "TCP port 80 is already listening. Identify the exact owner before first deployment."
      else
        pass "TCP port 80 is free for the initial Caddy deployment."
      fi
      if ss -H -ltn 'sport = :443' | grep -q .; then
        fail "TCP port 443 is already listening. Identify the exact owner before first deployment."
      else
        pass "TCP port 443 is free for the initial Caddy deployment."
      fi
    else
      fail "The ss command is required to verify first-deployment port ownership."
    fi

    warn "PENDING_EXTERNAL_INGRESS: after deployment, verify HTTPS from a mobile network and confirm 22/3000/5432/8080 stay closed."
  fi
fi

if ((failures > 0)); then
  printf 'RESULT: FAIL (%d failure(s), %d warning(s))\n' "$failures" "$warnings" >&2
  exit 1
fi

printf 'RESULT: PASS (%d warning(s))\n' "$warnings"
