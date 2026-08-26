#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/.." && pwd -P)"
# shellcheck source=deploy/home-server/recovery-production.sh
source "$repo_root/deploy/home-server/recovery-production.sh"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

readonly image_evidence_id="20260826T110000Z-a0000002"
readonly -a expected_sorted=(
  20260826T120000Z-a0000001
  20260826T110000Z-a0000002
  20260825T120000Z-a0000003
  20260824T120000Z-a0000004
  20260823T120000Z-a0000005
  20260822T120000Z-a0000006
  20260821T120000Z-a0000007
  20260820T120000Z-a0000008
  20260819T120000Z-a0000009
  20260818T120000Z-a0000010
  20260817T120000Z-a0000011
  20260816T120000Z-a0000012
  20260815T120000Z-a0000013
  20260814T120000Z-a0000014
  20260813T120000Z-a0000015
  20260802T120000Z-a0000016
  20260726T120000Z-a0000017
  20260719T120000Z-a0000018
  20260712T120000Z-a0000019
  20260705T120000Z-a0000020
  20260628T120000Z-a0000021
  20260621T120000Z-a0000022
  20260614T120000Z-a0000023
  20260531T120000Z-a0000024
  20260430T120000Z-a0000025
  20260331T120000Z-a0000026
  20260228T120000Z-a0000027
  20260131T120000Z-a0000028
  20251231T120000Z-a0000029
  20251130T120000Z-a0000030
  20251031T120000Z-a0000031
  20250930T120000Z-a0000032
  20250831T120000Z-a0000033
)

# Feed the sorter a deterministic non-chronological permutation so an
# ascending or input-order regression cannot pass the policy fixture.
fixture_ids=()
for ((index=${#expected_sorted[@]} - 1; index >= 0; index -= 2)); do
  fixture_ids+=("${expected_sorted[$index]}")
done
for ((index=1; index < ${#expected_sorted[@]}; index += 2)); do
  fixture_ids+=("${expected_sorted[$index]}")
done

mapfile -t actual_sorted < <(wsr_sort_retention_ids "${fixture_ids[@]}")
[[ "$(printf '%s\n' "${actual_sorted[@]}")" == "$(printf '%s\n' "${expected_sorted[@]}")" ]] ||
  fail "Retention inputs are not sorted newest-first by exact UTC backup ID."

declare -A fixture_bytes=()
for backup_id in "${expected_sorted[@]}"; do
  # The map is consumed through the planner's explicit nameref parameter.
  # shellcheck disable=SC2034
  fixture_bytes["$backup_id"]=10
done

actual_selection="$(
  wsr_emit_retention_selection \
    fixture_bytes \
    "$image_evidence_id" \
    "${actual_sorted[@]}"
)"
expected_selection="$(cat <<'EXPECTED'
KEEP|20260826T120000Z-a0000001|daily,weekly,monthly
KEEP|20260826T110000Z-a0000002|image-evidence-ready
KEEP|20260825T120000Z-a0000003|daily
KEEP|20260824T120000Z-a0000004|daily
KEEP|20260823T120000Z-a0000005|daily,weekly
KEEP|20260822T120000Z-a0000006|daily
KEEP|20260821T120000Z-a0000007|daily
KEEP|20260820T120000Z-a0000008|daily
KEEP|20260819T120000Z-a0000009|daily
KEEP|20260818T120000Z-a0000010|daily
KEEP|20260817T120000Z-a0000011|daily
KEEP|20260816T120000Z-a0000012|daily,weekly
KEEP|20260815T120000Z-a0000013|daily
KEEP|20260814T120000Z-a0000014|daily
KEEP|20260813T120000Z-a0000015|daily
KEEP|20260802T120000Z-a0000016|weekly
KEEP|20260726T120000Z-a0000017|weekly,monthly
KEEP|20260719T120000Z-a0000018|weekly
KEEP|20260712T120000Z-a0000019|weekly
KEEP|20260705T120000Z-a0000020|weekly
KEEP|20260628T120000Z-a0000021|monthly
CANDIDATE_ONLY|20260621T120000Z-a0000022|outside-policy-windows
CANDIDATE_ONLY|20260614T120000Z-a0000023|outside-policy-windows
KEEP|20260531T120000Z-a0000024|monthly
KEEP|20260430T120000Z-a0000025|monthly
KEEP|20260331T120000Z-a0000026|monthly
KEEP|20260228T120000Z-a0000027|monthly
KEEP|20260131T120000Z-a0000028|monthly
KEEP|20251231T120000Z-a0000029|monthly
KEEP|20251130T120000Z-a0000030|monthly
KEEP|20251031T120000Z-a0000031|monthly
KEEP|20250930T120000Z-a0000032|monthly
CANDIDATE_ONLY|20250831T120000Z-a0000033|outside-policy-windows
RETENTION_ESTIMATED_KEEP_BYTES|300
RETENTION_ESTIMATED_CANDIDATE_BYTES|30
EXPECTED
)"

[[ "$actual_selection" == "$expected_selection" ]] || {
  printf 'EXPECTED:\n%s\nACTUAL:\n%s\n' "$expected_selection" "$actual_selection" >&2
  fail "Fixed UTC retention fixture changed its exact KEEP/CANDIDATE union."
}

printf 'PASS: fixed UTC retention fixture preserves exact 14 daily, 8 weekly, 12 monthly, and image-evidence selections.\n'
