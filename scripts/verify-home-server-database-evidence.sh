#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/.." && pwd -P)"
# shellcheck source=deploy/home-server/recovery-production.sh
source "$repo_root/deploy/home-server/recovery-production.sh"

umask 077
test_root="$(mktemp -d "${TMPDIR:-/tmp}/wsr-database-evidence.XXXXXXXX")"

cleanup() {
  case "$test_root" in
    "${TMPDIR:-/tmp}"/wsr-database-evidence.*)
      rm -rf -- "$test_root"
      ;;
    *)
      printf 'Refusing to clean unexpected test path: %s\n' "$test_root" >&2
      return 1
      ;;
  esac
}
trap cleanup EXIT

valid="$test_root/valid.txt"
printf '%s\n' \
  'evidence_version|2' \
  'database_name|wsr' \
  'database_encoding|UTF8' \
  'flyway_successful_count|2' \
  'flyway_max_installed_rank|2' \
  'flyway|1|1|6669727374|SQL|56315f5f66697273742e73716c|101|true' \
  'flyway|2|2|7365636f6e64|SQL|56325f5f7365636f6e642e73716c|-202|true' \
  'platform_metadata|schema_baseline|P0' \
  'analyst_calls|17' \
  'analyst_call_revisions|11' \
  'call_outcomes|23' \
  'table_rows|public.analyst_call_revisions|11' \
  'table_rows|public.analyst_calls|17' \
  'table_rows|public.call_outcomes|23' \
  'table_rows|public.flyway_schema_history|2' \
  'table_rows|public.platform_metadata|1' \
  > "$valid"

wsr_parse_database_evidence "$valid"
[[ "$WSR_RESTORED_DATABASE_EVIDENCE_VERSION" == "2" &&
   "$WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT" == "2" &&
   "$WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK" == "2" &&
   "$WSR_RESTORED_ANALYST_CALLS" == "17" &&
   "$WSR_RESTORED_ANALYST_CALL_REVISIONS" == "11" &&
   "$WSR_RESTORED_CALL_OUTCOMES" == "23" ]] || {
  printf 'Valid dynamic evidence was not captured exactly.\n' >&2
  exit 1
}

assert_rejected() {
  local label="$1" path="$2"
  if wsr_parse_database_evidence "$path" >/dev/null 2>&1; then
    printf 'Invalid database evidence was accepted: %s\n' "$label" >&2
    exit 1
  fi
}

mismatched_rows="$test_root/mismatched-rows.txt"
sed 's/table_rows|public.analyst_calls|17/table_rows|public.analyst_calls|18/' \
  "$valid" > "$mismatched_rows"
assert_rejected "summary/table row mismatch" "$mismatched_rows"

duplicate_summary="$test_root/duplicate-summary.txt"
install -m 0600 -- "$valid" "$duplicate_summary"
printf 'analyst_calls|17\n' >> "$duplicate_summary"
assert_rejected "duplicate singleton summary" "$duplicate_summary"

duplicate_flyway="$test_root/duplicate-flyway.txt"
install -m 0600 -- "$valid" "$duplicate_flyway"
printf 'flyway|2|2|7365636f6e64|SQL|56325f5f7365636f6e642e73716c|-202|true\n' >> "$duplicate_flyway"
assert_rejected "duplicate Flyway installed rank" "$duplicate_flyway"

missing_metadata="$test_root/missing-metadata.txt"
sed '/^platform_metadata|/d' "$valid" > "$missing_metadata"
assert_rejected "missing platform metadata" "$missing_metadata"

unknown_row="$test_root/unknown-row.txt"
install -m 0600 -- "$valid" "$unknown_row"
printf 'invented_metric|0\n' >> "$unknown_row"
assert_rejected "unknown evidence row" "$unknown_row"

repeatable="$test_root/repeatable.txt"
sed 's/flyway|2|2|/flyway|2|null|/' "$valid" > "$repeatable"
assert_rejected "repeatable migration in v2 compatibility evidence" "$repeatable"

non_contiguous="$test_root/non-contiguous.txt"
sed 's/flyway|2|2|/flyway|3|3|/' "$valid" > "$non_contiguous"
assert_rejected "non-contiguous Flyway rank and version" "$non_contiguous"

legacy="$test_root/legacy-v1.txt"
sed \
  -e 's/evidence_version|2/evidence_version|1/' \
  -e 's/flyway|1|1|6669727374|SQL|56315f5f66697273742e73716c|101|true/flyway|1|1|101|true/' \
  -e 's/flyway|2|2|7365636f6e64|SQL|56325f5f7365636f6e642e73716c|-202|true/flyway|2|2|-202|true/' \
  "$valid" > "$legacy"
wsr_parse_database_evidence "$legacy"
[[ "$WSR_RESTORED_DATABASE_EVIDENCE_VERSION" == "1" ]] || {
  printf 'Legacy restore evidence did not remain distinguishable from v2.\n' >&2
  exit 1
}

printf 'PASS: v2 database evidence is strict, legacy v1 remains identifiable, and seven malformed variants are rejected.\n'
