#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: recovery-preflight.sh --mode host|production

host        Check recovery tools and a local Docker daemon without reading config.
production  Validate the exact config, separate backup device, and live database.
USAGE
}

main() {
  local mode=""
  if (($# == 2)) && [[ "$1" == "--mode" ]]; then
    mode="$2"
  elif (($# == 1)) && [[ "$1" == "--help" || "$1" == "-h" ]]; then
    usage
    return 0
  else
    usage >&2
    return 64
  fi

  local script_dir
  script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
  # shellcheck source=deploy/home-server/recovery-common.sh
  source "$script_dir/recovery-common.sh"
  # shellcheck source=deploy/home-server/generation-state.sh
  source "$script_dir/generation-state.sh"

  case "$mode" in
    host)
      wsr_run_host_preflight
      ;;
    production)
      wsr_generation_acquire_operation_lock shared
      wsr_generation_require_operation_lock shared
      wsr_run_production_preflight
      ;;
    *)
      wsr_error "--mode must be host or production."
      return 64
      ;;
  esac
  printf 'RESULT: PASS\n'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
