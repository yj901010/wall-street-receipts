#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: compose-production.sh --env-file deploy/home-server/.env.production -- ACTION

Runs the production Compose model against one verified local Docker endpoint.
It removes inherited overrides and revalidates the production contract before execution.
Allowed actions: build, up, ps, logs, stop, down.
USAGE
}

env_file=""
if (($# >= 3)) && [[ "$1" == "--env-file" ]]; then
  env_file="$2"
  shift 2
fi
if (($# == 0)) || [[ "$1" != "--" ]]; then
  usage >&2
  exit 64
fi
shift
if (($# != 1)); then
  printf 'ERROR: exactly one allowlisted action is required; arbitrary Compose arguments are forbidden.\n' >&2
  usage >&2
  exit 64
fi

case "$1" in
  build) compose_arguments=(build --pull api web caddy-production) ;;
  up) compose_arguments=(up --detach --wait) ;;
  ps) compose_arguments=(ps) ;;
  logs) compose_arguments=(logs --tail 200) ;;
  stop) compose_arguments=(stop --timeout 30) ;;
  down) compose_arguments=(down --timeout 30) ;;
  *)
    printf 'ERROR: action must be one of: build, up, ps, logs, stop, down.\n' >&2
    exit 64
    ;;
esac

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
compose_file="$script_dir/compose.yaml"
expected_env_file="$script_dir/.env.production"
if [[ -z "$env_file" || ! -f "$env_file" || -L "$env_file" ]]; then
  printf 'ERROR: --env-file must name the regular, non-symlink .env.production file.\n' >&2
  exit 1
fi
resolved_env_file="$(realpath -e -- "$env_file")"
if [[ "$resolved_env_file" != "$expected_env_file" ]]; then
  printf 'ERROR: use the exact ignored file deploy/home-server/.env.production.\n' >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  printf 'ERROR: Docker is not installed.\n' >&2
  exit 1
fi

if [[ -n "${DOCKER_CONTEXT:-}" ]]; then
  selected_endpoint="$(docker context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}')"
elif [[ -n "${DOCKER_HOST:-}" ]]; then
  selected_endpoint="$DOCKER_HOST"
else
  selected_endpoint="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
fi
if [[ ! "$selected_endpoint" =~ ^unix:///.+ &&
      ! "$selected_endpoint" =~ ^fd://.+ &&
      ! "$selected_endpoint" =~ ^tcp://127(\.[0-9]{1,3}){3}:[0-9]+$ &&
      ! "$selected_endpoint" =~ ^tcp://\[::1\]:[0-9]+$ ]]; then
  printf 'ERROR: refusing to run production Compose against a remote Docker endpoint.\n' >&2
  exit 1
fi

unset_arguments=()
while IFS='=' read -r name _; do
  uppercase_name="${name^^}"
  case "$uppercase_name" in
    WSR_*|COMPOSE_*|DOCKER_*|BUILDKIT_*|BUILDX_*|HTTP_PROXY|HTTPS_PROXY|ALL_PROXY|NO_PROXY)
      unset_arguments+=(--unset="$name")
      ;;
  esac
done < <(env)

clean_environment=(
  env
  "${unset_arguments[@]}"
  DOCKER_HOST="$selected_endpoint"
)
compose_version="$("${clean_environment[@]}" docker compose version --short)"
version_core="${compose_version#v}"
version_core="${version_core%%[-+]*}"
IFS=. read -r compose_major compose_minor _ <<< "$version_core"
if [[ ! "$compose_major" =~ ^[0-9]+$ || ! "$compose_minor" =~ ^[0-9]+$ ]] ||
   ((compose_major < 2 || (compose_major == 2 && compose_minor < 20))); then
  printf 'ERROR: Docker Compose 2.20.0 or newer is required.\n' >&2
  exit 1
fi

"${clean_environment[@]}" bash "$script_dir/preflight.sh" \
  --mode contract \
  --env-file "$resolved_env_file"

exec "${clean_environment[@]}" docker compose \
    --env-file "$resolved_env_file" \
    --file "$compose_file" \
    --profile production \
    "${compose_arguments[@]}"
