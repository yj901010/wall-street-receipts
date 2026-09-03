#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/.." && pwd -P)"
# shellcheck source=deploy/home-server/recovery-production.sh
source "$repo_root/deploy/home-server/recovery-production.sh"

umask 077
test_root="$(mktemp -d "${TMPDIR:-/tmp}/wsr-schema-compatibility.XXXXXXXX")"

cleanup() {
  case "$test_root" in
    "${TMPDIR:-/tmp}"/wsr-schema-compatibility.*)
      rm -rf -- "$test_root"
      ;;
    *)
      printf 'Refusing to clean unexpected test path: %s\n' "$test_root" >&2
      return 1
      ;;
  esac
}
trap cleanup EXIT

assert_failed_reason() {
  local label="$1" expected_reason="$2"
  if [[ "$WSR_SCHEMA_FAILURE_REASON" != "$expected_reason" ]]; then
    printf 'Wrong schema failure for %s: expected %s, got %s\n' \
      "$label" "$expected_reason" "${WSR_SCHEMA_FAILURE_REASON:-none}" >&2
    exit 1
  fi
}

assert_inventory_rejected() {
  local label="$1" candidate="$2"
  wsr_schema_reset
  WSR_SCHEMA_INVENTORY_OUTPUT="$candidate"
  if wsr_parse_schema_image_inventory >/dev/null 2>&1; then
    printf 'Invalid API-image inventory was accepted: %s\n' "$label" >&2
    exit 1
  fi
  assert_failed_reason "$label" "image-inspector-failed"
}

head_sha="$(git -C "$repo_root" rev-parse HEAD)"
WSR_SCHEMA_REPOSITORY_ROOT="$repo_root"
wsr_schema_validate_git_commit "$head_sha"

if wsr_schema_validate_git_commit "${head_sha:0:12}" >/dev/null 2>&1; then
  printf 'A short Git SHA was accepted.\n' >&2
  exit 1
fi
assert_failed_reason "short Git SHA" "git-object-unavailable"

if wsr_schema_validate_git_commit "ffffffffffffffffffffffffffffffffffffffff" >/dev/null 2>&1; then
  printf 'A missing Git object was accepted.\n' >&2
  exit 1
fi
assert_failed_reason "missing Git object" "git-object-unavailable"

blob_sha="$(git -C "$repo_root" rev-parse "$head_sha:apps/api/pom.xml")"
if wsr_schema_validate_git_commit "$blob_sha" >/dev/null 2>&1; then
  printf 'A Git blob was accepted as a release commit.\n' >&2
  exit 1
fi
assert_failed_reason "Git blob identity" "git-object-unavailable"

promisor_repo="$test_root/promisor-repository"
git clone --quiet --no-hardlinks -- "$repo_root" "$promisor_repo"
git -C "$promisor_repo" config --local remote.origin.promisor true
WSR_SCHEMA_REPOSITORY_ROOT="$promisor_repo"
if wsr_schema_validate_git_commit "$head_sha" >/dev/null 2>&1; then
  printf 'A partial/promisor Git repository was accepted.\n' >&2
  exit 1
fi
assert_failed_reason "partial/promisor repository" "git-object-unavailable"
WSR_SCHEMA_REPOSITORY_ROOT="$repo_root"

worktree_main="$test_root/worktree-main"
worktree_linked="$test_root/worktree-linked"
alternate_objects="$test_root/alternate-objects"
mkdir -p -- "$worktree_main" "$alternate_objects"
git -C "$worktree_main" init --quiet
git -C "$worktree_main" config user.name "WSR schema fixture"
git -C "$worktree_main" config user.email "schema-fixture@example.invalid"
printf 'fixture\n' > "$worktree_main/fixture.txt"
git -C "$worktree_main" add -- fixture.txt
git -C "$worktree_main" commit --quiet --message "test: add schema fixture"
git -C "$worktree_main" worktree add --quiet --detach "$worktree_linked" HEAD
mkdir -p -- "$worktree_main/.git/objects/info"
printf '%s\n' "$alternate_objects" > "$worktree_main/.git/objects/info/alternates"
linked_sha="$(git -C "$worktree_linked" rev-parse HEAD)"
WSR_SCHEMA_REPOSITORY_ROOT="$worktree_linked"
if wsr_schema_validate_git_commit "$linked_sha" >/dev/null 2>&1; then
  printf 'A linked worktree backed by an alternate object store was accepted.\n' >&2
  exit 1
fi
assert_failed_reason "linked-worktree alternate object store" "git-object-unavailable"
WSR_SCHEMA_REPOSITORY_ROOT="$repo_root"

declare -A blobs=() filenames=()
mapfile -d '' -t tree_rows < <(
  git -C "$repo_root" --no-replace-objects ls-tree -rz --full-tree \
    "$head_sha" -- "$WSR_SCHEMA_MIGRATION_ROOT"
)
for row in "${tree_rows[@]}"; do
  metadata="${row%%$'\t'*}"
  path="${row#*$'\t'}"
  read -r mode object_type blob <<< "$metadata"
  filename="${path##*/}"
  [[ "$mode" == "100644" && "$object_type" == "blob" &&
     "$filename" =~ ^V([1-9][0-9]*)__([a-z0-9]+(_[a-z0-9]+)*)[.]sql$ ]] || {
    printf 'The repository fixture contains an unsupported migration: %s\n' "$path" >&2
    exit 1
  }
  version="${BASH_REMATCH[1]}"
  blobs["$version"]="$blob"
  filenames["$version"]="$filename"
done

migration_count="${#tree_rows[@]}"
((migration_count > 0))
inventory=$'inventory_version|1\nflyway_version|11.7.2'
for ((index = 1; index <= migration_count; index++)); do
  [[ -n "${blobs[$index]+present}" ]] || {
    printf 'The real Git fixture has a migration-version gap at V%d.\n' "$index" >&2
    exit 1
  }
  bytes="$(git -C "$repo_root" cat-file -s "${blobs[$index]}")"
  raw_sha="$(git -C "$repo_root" cat-file blob "${blobs[$index]}" | sha256sum | awk '{print $1}')"
  inventory+=$'\n'
  inventory+="migration|$index|$index|aa|SQL|aa|$index|${filenames[$index]}|$raw_sha|$bytes"
done

wsr_schema_reset
WSR_SCHEMA_INVENTORY_OUTPUT="$inventory"
wsr_parse_schema_image_inventory
[[ "$WSR_SCHEMA_MIGRATION_COUNT" == "$migration_count" ]]
wsr_compare_git_migration_tree "$head_sha"

first_sha="${WSR_SCHEMA_IMAGE_SHA256[1]}"
mutated_sha="f${first_sha:1}"
[[ "$mutated_sha" != "$first_sha" ]] || mutated_sha="e${first_sha:1}"
WSR_SCHEMA_IMAGE_SHA256[1]="$mutated_sha"
if wsr_compare_git_migration_tree "$head_sha" >/dev/null 2>&1; then
  printf 'A Git/image migration byte mismatch was accepted.\n' >&2
  exit 1
fi
assert_failed_reason "Git/image byte mismatch" "image-git-resource-mismatch"

assert_inventory_rejected \
  "missing inventory version" \
  "${inventory#*$'\n'}"
assert_inventory_rejected \
  "unsupported Flyway engine" \
  "${inventory/flyway_version|11.7.2/flyway_version|11.8.0}"
assert_inventory_rejected \
  "repeatable migration" \
  "${inventory/${filenames[1]}/R__repeatable.sql}"
assert_inventory_rejected \
  "nested migration path" \
  "${inventory/${filenames[1]}/nested\/${filenames[1]}}"

wsr_schema_reset
WSR_SCHEMA_INVENTORY_OUTPUT="$inventory"
wsr_parse_schema_image_inventory
valid_evidence="$test_root/valid-v2.txt"
{
  printf 'evidence_version|2\n'
  for ((index = 1; index <= migration_count; index++)); do
    printf 'flyway|%d|%d|%s|SQL|%s|%s|true\n' \
      "$index" "$index" \
      "${WSR_SCHEMA_IMAGE_DESCRIPTION[$index]}" \
      "${WSR_SCHEMA_IMAGE_SCRIPT[$index]}" \
      "${WSR_SCHEMA_IMAGE_CHECKSUM[$index]}"
  done
} > "$valid_evidence"
WSR_VALIDATED_RESTORE_EVIDENCE_FILE="$valid_evidence"
WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT="$migration_count"
WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK="$migration_count"
wsr_compare_restored_flyway_history

assert_history_rejected() {
  local label="$1" path="$2"
  WSR_SCHEMA_FAILURE_REASON=""
  WSR_VALIDATED_RESTORE_EVIDENCE_FILE="$path"
  if wsr_compare_restored_flyway_history >/dev/null 2>&1; then
    printf 'Incompatible Flyway history was accepted: %s\n' "$label" >&2
    exit 1
  fi
  assert_failed_reason "$label" "flyway-row-order-checksum-mismatch"
}

checksum_mismatch="$test_root/checksum-mismatch.txt"
sed 's/|1|true$/|99|true/' "$valid_evidence" > "$checksum_mismatch"
assert_history_rejected "checksum mismatch" "$checksum_mismatch"

description_mismatch="$test_root/description-mismatch.txt"
sed '0,/|aa|SQL|/s//|bb|SQL|/' "$valid_evidence" > "$description_mismatch"
assert_history_rejected "description mismatch" "$description_mismatch"

script_mismatch="$test_root/script-mismatch.txt"
sed '0,/|SQL|aa|/s//|SQL|bb|/' "$valid_evidence" > "$script_mismatch"
assert_history_rejected "script mismatch" "$script_mismatch"

missing_row="$test_root/missing-row.txt"
sed "/^flyway|$migration_count|/d" "$valid_evidence" > "$missing_row"
assert_history_rejected "missing migration" "$missing_row"

extra_row="$test_root/extra-row.txt"
install -m 0600 -- "$valid_evidence" "$extra_row"
printf 'flyway|%d|%d|aa|SQL|aa|%d|true\n' \
  "$((migration_count + 1))" "$((migration_count + 1))" "$((migration_count + 1))" \
  >> "$extra_row"
assert_history_rejected "extra migration" "$extra_row"

printf 'PASS: exact Git-object, image-resource, inventory-shape, and Flyway-history comparisons reject all tested drift.\n'
