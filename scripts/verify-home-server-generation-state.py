#!/usr/bin/env python3
"""Static and mutation checks for the ADR-050 generation-control boundary."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence


ROOT = Path(__file__).resolve().parents[1]
DEPLOY = ROOT / "deploy" / "home-server"

PATHS = {
    "state": DEPLOY / "generation-state.sh",
    "compose": DEPLOY / "compose-production.sh",
    "recovery": DEPLOY / "recovery-production.sh",
    "recovery_preflight": DEPLOY / "recovery-preflight.sh",
    "deployment_preflight": DEPLOY / "preflight.sh",
    "recovery_common": DEPLOY / "recovery-common.sh",
    "compose_model": DEPLOY / "compose.yaml",
    "ci": ROOT / ".github" / "workflows" / "ci.yml",
}

EXPECTED_SELECTOR_FIELDS = (
    "schema_version",
    "project",
    "revision",
    "active_generation_id",
    "active_generation_manifest_sha256",
    "active_volume_name",
    "previous_selector_sha256",
    "change_kind",
    "transition_uuid",
    "plan_sha256",
    "written_utc",
)
EXPECTED_MANIFEST_FIELDS = (
    "schema_version",
    "project",
    "generation_id",
    "generation_kind",
    "postgres_volume_name",
    "volume_driver",
    "volume_created_utc",
    "volume_labels_sha256",
    "source_backup_id",
    "source_backup_manifest_sha256",
    "source_archive_sha256",
    "source_restore_evidence_id",
    "source_restore_manifest_sha256",
    "source_database_evidence_sha256",
    "promotion_plan_sha256",
    "git_sha",
    "postgres_image_reference",
    "postgres_image_id",
    "postgres_image_revision",
    "authentication_contract",
    "created_utc",
    "sealed_utc",
    "state",
)
EXPECTED_BACKUP_BINDING_FIELDS = (
    "schema_version",
    "backup_id",
    "source_generation_contract_version",
    "source_generation_id",
    "source_generation_kind",
    "source_generation_manifest_sha256",
    "source_volume_name",
    "source_volume_created_utc",
    "source_volume_labels_sha256",
    "active_selector_schema_version",
    "active_selector_revision",
    "active_selector_sha256",
    "capture_lock_contract_version",
)
EXPECTED_JOURNAL_FIELDS = (
    "schema_version",
    "transition_uuid",
    "operation_uuid",
    "record_sequence",
    "record_kind",
    "state_before",
    "event",
    "state_after",
    "plan_sha256",
    "source_generation_id",
    "source_generation_manifest_sha256",
    "source_volume_name",
    "target_generation_id",
    "target_generation_manifest_sha256",
    "target_volume_name",
    "selector_before_revision",
    "selector_before_sha256",
    "selector_after_revision",
    "selector_after_sha256",
    "previous_record_sha256",
    "written_utc",
)

EXPECTED_TRANSITIONS = {
    ("steady", "begin-candidate-preparation"): "candidate-preparing",
    ("candidate-preparing", "seal-candidate-offline"): "candidate-sealed-offline",
    ("candidate-preparing", "abandon-candidate-after-review"): "steady",
    ("candidate-sealed-offline", "record-explicit-approval"): "approval-recorded",
    ("candidate-sealed-offline", "abort-before-downtime"): "steady",
    ("approval-recorded", "persist-quiesce-intent"): "quiesce-intent",
    ("approval-recorded", "abort-before-downtime"): "steady",
    ("quiesce-intent", "stop-source"): "source-stopped",
    ("quiesce-intent", "abort-before-downtime"): "steady",
    ("source-stopped", "persist-selector-switch-intent"): "selector-switch-intent",
    ("selector-switch-intent", "start-target"): "target-starting",
    ("target-starting", "verify-target-health"): "target-health-verified",
    ("target-health-verified", "begin-probation"): "probation",
    ("probation", "finalize"): "finalized",
    ("source-stopped", "persist-rollback-intent"): "rollback-intent",
    ("selector-switch-intent", "persist-rollback-intent"): "rollback-intent",
    ("target-starting", "persist-rollback-intent"): "rollback-intent",
    ("target-health-verified", "persist-rollback-intent"): "rollback-intent",
    ("probation", "persist-rollback-intent"): "rollback-intent",
    ("rollback-intent", "stop-target"): "target-stopped-for-rollback",
    ("target-stopped-for-rollback", "restore-source-selector"): "source-selector-restored",
    ("source-selector-restored", "start-source"): "source-restarting",
    ("source-restarting", "complete-rollback"): "rolled-back",
}

RECOVERY_ACTIONS = {
    "preflight": ("shared", "wsr_action_preflight"),
    "create": ("exclusive", "wsr_action_create"),
    "status": ("shared", "wsr_action_status"),
    "rehearse-latest": ("exclusive", "wsr_action_rehearse_latest"),
    "retention-plan": ("shared", "wsr_action_retention_plan"),
    "schema-check-latest": ("exclusive", "wsr_action_schema_check_latest"),
    "promotion-plan-latest": ("exclusive", "wsr_action_promotion_plan_latest"),
}

COMPOSE_ACTIONS = {
    "build": ("exclusive", "compose_arguments=(build --pull api web caddy-production)"),
    "up": ("exclusive", "compose_arguments=(up --detach --wait)"),
    "ps": ("shared", "compose_arguments=(ps)"),
    "logs": ("shared", "compose_arguments=(logs --tail 200)"),
    "stop": ("exclusive", "compose_arguments=(stop --timeout 30)"),
    "down": ("exclusive", "compose_arguments=(down --timeout 30)"),
}


class ContractError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def require_count(source: str, needle: str, count: int, message: str) -> None:
    require(source.count(needle) == count, message)


def require_order(source: str, needles: Sequence[str], message: str) -> None:
    position = -1
    for needle in needles:
        next_position = source.find(needle, position + 1)
        require(next_position >= 0, f"{message}: missing {needle!r}")
        require(next_position > position, message)
        position = next_position


def function_body(source: str, name: str) -> str:
    pattern = re.compile(
        rf"(?ms)^{re.escape(name)}\(\)\s*\{{\n(?P<body>.*?)^\}}\s*$"
    )
    matches = list(pattern.finditer(source))
    require(len(matches) == 1, f"Expected exactly one shell function {name}")
    return matches[0].group("body")


def array_fields(source: str, name: str) -> tuple[str, ...]:
    pattern = re.compile(
        rf"(?ms)^readonly -a {re.escape(name)}=\(\n(?P<body>.*?)^\)\s*$"
    )
    matches = list(pattern.finditer(source))
    require(len(matches) == 1, f"Expected exactly one readonly field array {name}")
    body = matches[0].group("body")
    require(not re.search(r"[#'\"]", body), f"{name} must contain plain canonical field names")
    return tuple(body.split())


def case_arms(scope: str, expression: str) -> dict[str, str]:
    pattern = re.compile(
        rf"(?ms)^\s*case\s+{re.escape(expression)}\s+in\s*\n(?P<body>.*?)^\s*esac\s*$"
    )
    matches = list(pattern.finditer(scope))
    require(len(matches) == 1, f"Expected exactly one case {expression} in the selected scope")
    body = matches[0].group("body")
    labels = list(re.finditer(r"(?m)^\s+([a-z][a-z0-9-]*|\*)\)\s*", body))
    require(labels, f"No case arms found for {expression}")
    result: dict[str, str] = {}
    for index, match in enumerate(labels):
        label = match.group(1)
        require(label not in result, f"Duplicate case arm {label}")
        end = labels[index + 1].start() if index + 1 < len(labels) else len(body)
        result[label] = body[match.end() : end]
    return result


def declared_recovery_actions(source: str) -> set[str]:
    match = re.search(r"(?m)^Allowed actions: ([^\n]+)\.\s*$", source)
    require(match is not None, "Recovery usage must declare one fixed action list")
    return {item.strip() for item in match.group(1).split(",")}


def validate_generation_documents(state: str) -> None:
    expected_arrays = {
        "WSR_GENERATION_SELECTOR_FIELDS": EXPECTED_SELECTOR_FIELDS,
        "WSR_GENERATION_MANIFEST_FIELDS": EXPECTED_MANIFEST_FIELDS,
        "WSR_GENERATION_BACKUP_BINDING_FIELDS": EXPECTED_BACKUP_BINDING_FIELDS,
        "WSR_GENERATION_JOURNAL_FIELDS": EXPECTED_JOURNAL_FIELDS,
    }
    for name, expected in expected_arrays.items():
        require(array_fields(state, name) == expected, f"{name} changed from the ADR-050 canonical order")

    associative_guard = function_body(state, "wsr_generation_associative_map_name_valid")
    indexed_guard = function_body(state, "wsr_generation_indexed_array_name_valid")
    for body, flag, label in (
        (associative_guard, "A", "associative map"),
        (indexed_guard, "a", "indexed array"),
    ):
        require('[[ "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]' in body, f"{label} name guard is missing")
        require('declaration="$(declare -p "$name"' in body, f"{label} declaration guard is missing")
        require(f"*{flag}[^[:space:]]*" in body, f"{label} type flag is not checked")

    parser_adapter = function_body(state, "wsr_generation_parser_adapter_valid")
    for fields_name, validator_name, renderer_name in (
        ("WSR_GENERATION_SELECTOR_FIELDS", "wsr_generation_validate_selector_map", "wsr_generation_render_selector_v1"),
        ("WSR_GENERATION_MANIFEST_FIELDS", "wsr_generation_validate_manifest_map", "wsr_generation_render_manifest_v1"),
        ("WSR_GENERATION_BACKUP_BINDING_FIELDS", "wsr_generation_validate_backup_binding_map", "wsr_generation_render_backup_binding_v2"),
        ("WSR_GENERATION_JOURNAL_FIELDS", "wsr_generation_validate_journal_record_map", "wsr_generation_render_journal_record_v1"),
    ):
        require(
            f"{fields_name}\\|{validator_name}\\|{renderer_name}" in parser_adapter,
            "Generic parser adapter allowlist changed",
        )

    exact_map = function_body(state, "wsr_generation_require_exact_map")
    require_order(
        exact_map,
        (
            'wsr_generation_associative_map_name_valid "$map_name"',
            'wsr_generation_indexed_array_name_valid "$fields_name"',
            'local -n document_ref="$map_name"',
        ),
        "Map and field names must be type-checked before nameref creation",
    )
    for needle in (
        "((${#document_ref[@]} == ${#fields_ref[@]}))",
        'for field in "${fields_ref[@]}"',
        '[[ -n "${document_ref[$field]+present}" ]]',
        'for key in "${!document_ref[@]}"',
        '[[ "$key" == "$field" ]]',
    ):
        require(needle in exact_map, f"Exact document map guard is missing {needle!r}")

    parser = function_body(state, "wsr_generation_parse_ordered_file")
    require_order(
        parser,
        (
            'wsr_generation_associative_map_name_valid "$target_name"',
            'wsr_generation_indexed_array_name_valid "$fields_name"',
            'wsr_generation_parser_adapter_valid "$fields_name" "$validator" "$renderer"',
            'local -n target_ref="$target_name"',
        ),
        "Parser references must be allowlisted before nameref or command dispatch",
    )
    require_order(
        parser,
        (
            '[[ ! -f "$path" || -L "$path" ]]',
            "size=\"$(stat -c '%s' -- \"$path\" 2>/dev/null)\"",
            'last_byte="$(tail -c 1 -- "$path"',
            'first_three="$(head -c 3 -- "$path"',
            'LC_ALL=C grep -q $\'\\r\' -- "$path"',
            'expected="${fields_ref[line_number - 1]}"',
            '[[ "$key" != "$expected" ]]',
            '((line_number != ${#fields_ref[@]}))',
            'rendered="$("$renderer" "$target_name")"',
            'actual_sha="$(sha256sum -- "$path"',
            'expected_sha="$(printf \'%s\\n\' "$rendered"',
            '[[ "$actual_sha" != "$expected_sha" ]]',
        ),
        "Canonical ordered document parser was weakened",
    )

    adapters = {
        "selector": (
            "wsr_generation_render_selector_v1",
            "wsr_generation_parse_selector_v1",
            "WSR_GENERATION_SELECTOR_FIELDS",
            "wsr_generation_validate_selector_map",
        ),
        "manifest": (
            "wsr_generation_render_manifest_v1",
            "wsr_generation_parse_manifest_v1",
            "WSR_GENERATION_MANIFEST_FIELDS",
            "wsr_generation_validate_manifest_map",
        ),
        "backup binding": (
            "wsr_generation_render_backup_binding_v2",
            "wsr_generation_parse_backup_binding_v2",
            "WSR_GENERATION_BACKUP_BINDING_FIELDS",
            "wsr_generation_validate_backup_binding_map",
        ),
        "journal": (
            "wsr_generation_render_journal_record_v1",
            "wsr_generation_parse_journal_record_v1",
            "WSR_GENERATION_JOURNAL_FIELDS",
            "wsr_generation_validate_journal_record_map",
        ),
    }
    for label, (renderer_name, parser_name, fields_name, validator_name) in adapters.items():
        renderer = function_body(state, renderer_name)
        parser_adapter = function_body(state, parser_name)
        require(validator_name in renderer and fields_name in renderer, f"{label} renderer is not exact")
        require(
            fields_name in parser_adapter
            and validator_name in parser_adapter
            and renderer_name in parser_adapter,
            f"{label} parser adapter is not closed over its exact schema",
        )

    selector = function_body(state, "wsr_generation_validate_selector_map")
    for needle in (
        '"${selector_ref[change_kind]}"',
        "legacy-bootstrap)",
        "promotion|rollback)",
        '"${selector_ref[previous_selector_sha256]}" == "$WSR_GENERATION_ZERO_SHA256"',
        '"${selector_ref[transition_uuid]}" == "bootstrap"',
        '"${selector_ref[plan_sha256]}" == "bootstrap"',
        "wsr_generation_volume_matches_id",
    ):
        require(needle in selector, f"Selector validation lost {needle!r}")

    manifest = function_body(state, "wsr_generation_validate_manifest_map")
    for needle in (
        '"${manifest_ref[volume_driver]}" != "local"',
        '"${manifest_ref[authentication_contract]}" != "production-password-file-scram-sha-256"',
        "legacy-import)",
        "restored-candidate)",
        "wsr_generation_candidate_id_for_sources",
        '"${manifest_ref[generation_id]}" == "$expected_candidate"',
        '"${manifest_ref[state]}" == "observed-active-at-import"',
        '"${manifest_ref[state]}" == "sealed-offline"',
        "wsr_generation_volume_matches_id",
        '"${manifest_ref[sealed_utc]}" < "${manifest_ref[created_utc]}"',
    ):
        require(needle in manifest, f"Generation manifest validation lost {needle!r}")

    binding = function_body(state, "wsr_generation_validate_backup_binding_map")
    for needle in (
        '"${binding_ref[schema_version]}" != "$WSR_GENERATION_BACKUP_BINDING_SCHEMA_VERSION"',
        '"${binding_ref[capture_lock_contract_version]}" != "$WSR_GENERATION_LOCK_CONTRACT_VERSION"',
        "wsr_generation_volume_matches_id",
        "wsr_generation_nonzero_sha256_valid",
        "legacy-import)",
        "restored-candidate)",
        '"${binding_ref[source_generation_id]}" == "$WSR_GENERATION_LEGACY_ID"',
        'wsr_generation_managed_id_valid "${binding_ref[source_generation_id]}"',
    ):
        require(needle in binding, f"Backup manifest-v2 binding validation lost {needle!r}")

    selector_manifest = function_body(
        state, "wsr_generation_validate_selector_manifest_relationship"
    )
    for field in (
        "active_generation_id",
        "active_generation_manifest_sha256",
        "active_volume_name",
        "generation_id",
        "postgres_volume_name",
        "written_utc",
        "sealed_utc",
    ):
        require(field in selector_manifest, f"Selector/generation relationship lost {field}")
    require(
        'wsr_generation_nonzero_sha256_valid "$manifest_sha"' in selector_manifest,
        "Selector/generation relationship accepts an invalid manifest digest",
    )

    relationship = function_body(state, "wsr_generation_validate_backup_binding_relationship")
    require(
        "wsr_generation_validate_selector_manifest_relationship" in relationship,
        "Backup relationship bypasses the selector/generation relationship",
    )
    relationship_fields = (
        "source_generation_id",
        "source_generation_kind",
        "source_generation_manifest_sha256",
        "source_volume_name",
        "source_volume_created_utc",
        "source_volume_labels_sha256",
        "active_selector_revision",
        "active_selector_sha256",
    )
    for field in relationship_fields:
        require(field in relationship, f"Backup/selector/generation relationship lost {field}")


def validate_state_graph_and_journal(state: str) -> None:
    graph = function_body(state, "wsr_generation_next_state")
    case_match = re.search(r'(?ms)^\s*case "\$1\|\$2" in\s*\n(?P<body>.*?)^\s*esac\s*$', graph)
    require(case_match is not None, "Generation transition graph must be one exact case table")
    case_body = case_match.group("body")
    transition_pattern = re.compile(
        r"^\s*([a-z0-9-]+)\\\|([a-z0-9-]+)\)\s+printf '([a-z0-9-]+)\\n'\s*;;\s*$"
    )
    actual: dict[tuple[str, str], str] = {}
    arm_lines = []
    for line in case_body.splitlines():
        if re.match(r"^\s*\S+\)\s+", line):
            arm_lines.append(line)
            match = transition_pattern.match(line)
            if match:
                key = (match.group(1), match.group(2))
                require(key not in actual, f"Duplicate generation transition {key}")
                actual[key] = match.group(3)
    require(actual == EXPECTED_TRANSITIONS, "ADR-049 exact generation transition table changed")
    require(len(arm_lines) == len(EXPECTED_TRANSITIONS) + 1, "Generation graph contains an extra case arm")
    require(re.search(r"(?m)^\s*\*\) return 1 ;;\s*$", case_body) is not None, "Unknown transitions must fail closed")

    journal_record = function_body(state, "wsr_generation_validate_journal_record_map")
    for needle in (
        'expected_after="$(wsr_generation_next_state',
        '"$expected_after" != "${record_ref[state_after]}"',
        "wsr_generation_uuid_valid",
        "wsr_generation_revision_valid",
        '"${record_ref[event]}" =~ ^(start-target|restore-source-selector)$',
        '"${record_ref[source_generation_id]}" == "${record_ref[target_generation_id]}"',
        "after_number != before_number + 1",
        "after_number != before_number",
        '"${record_ref[selector_after_sha256]}" == "${record_ref[selector_before_sha256]}"',
        '"${record_ref[selector_after_sha256]}" != "${record_ref[selector_before_sha256]}"',
    ):
        require(needle in journal_record, f"Journal record validation lost {needle!r}")

    chain = function_body(state, "wsr_generation_validate_journal_chain")
    require_order(
        chain,
        (
            'expected_previous="$WSR_GENERATION_ZERO_SHA256"',
            "printf -v expected_sequence '%016d' \"$index\"",
            'wsr_generation_parse_journal_record_v1 "$path" record',
            '"${record[previous_record_sha256]}" != "$expected_previous"',
            'actual_sha="$(sha256sum -- "$path"',
            'if [[ "${record[record_kind]}" == "intent" ]]',
            'pending=true',
            "wsr_generation_journal_pair_matches intent record",
            'current_state="${record[state_after]}"',
            'expected_previous="$actual_sha"',
        ),
        "Journal hash-chain and intent/completion ordering changed",
    )
    for identity in (
        "transition_uuid",
        "plan_sha256",
        "source_generation_id",
        "source_generation_manifest_sha256",
        "source_volume_name",
        "target_generation_id",
        "target_generation_manifest_sha256",
        "target_volume_name",
    ):
        require(identity in chain, f"Journal chain no longer pins {identity}")
    for needle in (
        '"${record[written_utc]}" < "$previous_written_utc"',
        '"${seen_operations[${record[operation_uuid]}]+present}"',
        'seen_operations["${record[operation_uuid]}"]=1',
        '"${record[selector_before_revision]}" != "$current_selector_revision"',
        '"${record[selector_before_sha256]}" != "$current_selector_sha"',
        'current_selector_revision="${record[selector_after_revision]}"',
        'current_selector_sha="${record[selector_after_sha256]}"',
    ):
        require(needle in chain, f"Journal chain continuity lost {needle!r}")
    require("wsr_generation_pending_intent_directive" in chain, "Pending journal intent is not fail-closed")
    require("wsr_generation_recovery_directive" in chain, "Completed journal state lacks recovery classification")


def validate_lock_and_durable_writer(state: str, recovery_common: str) -> None:
    require_count(
        state,
        'readonly WSR_GENERATION_CONTROL_ROOT="/var/lib/wall-street-receipts/generation-control"',
        1,
        "Generation control root is not fixed",
    )
    require_count(
        state,
        'readonly WSR_GENERATION_OPERATION_LOCK_PATH="$WSR_GENERATION_CONTROL_ROOT/operation.lock"',
        1,
        "Generation operation lock path is not fixed",
    )

    secure_directory = function_body(state, "wsr_generation_secure_directory")
    for needle in (
        '[[ -d "$path" && ! -L "$path" ]]',
        'resolved="$(realpath -e -- "$path"',
        "owner=\"$(stat -c '%u' -- \"$path\"",
        "mode=\"$(stat -c '%a' -- \"$path\"",
        '"$resolved" == "$path"',
        '"$owner" == "$expected_uid"',
        '"$mode" == "700"',
    ):
        require(needle in secure_directory, f"Secure control-directory metadata guard lost {needle!r}")

    secure_lock = function_body(state, "wsr_generation_secure_lock_file")
    secure_document = function_body(state, "wsr_generation_secure_document_file")
    for body, mode, label in ((secure_lock, "600", "lock"), (secure_document, "400", "document")):
        for needle in (
            '[[ -f "$path" && ! -L "$path" ]]',
            'resolved="$(realpath -e -- "$path"',
            "owner=\"$(stat -c '%u' -- \"$path\"",
            "mode=\"$(stat -c '%a' -- \"$path\"",
            "links=\"$(stat -c '%h' -- \"$path\"",
            f'"$mode" == "{mode}"',
            '"$links" == "1"',
        ):
            require(needle in body, f"Secure {label} metadata guard lost {needle!r}")

    acquire_at = function_body(state, "wsr_generation_acquire_operation_lock_at")
    require_count(acquire_at, 'wsr_generation_secure_lock_file "$path" "$expected_uid"', 2, "Lock metadata must be checked before and after flock")
    require_count(acquire_at, '"/proc/$$/fd/$fd"', 2, "Lock FD inode must be checked through procfs before and after flock")
    for needle in (
        'path_identity="$(stat -Lc \'%d:%i\' -- "$path")"',
        'exec {fd}<>"$path"',
        '"$fd_identity" != "$path_identity"',
        'flock -n -s "$fd"',
        'flock -n -x "$fd"',
        'WSR_GENERATION_OPERATION_LOCK_IDENTITY="$path_identity"',
    ):
        require(needle in acquire_at, f"Operation lock acquisition lost {needle!r}")
    require_order(
        acquire_at,
        (
            'wsr_generation_secure_directory "$parent" "$expected_uid"',
            'path_identity="$(stat -Lc \'%d:%i\' -- "$path")"',
            'exec {fd}<>"$path"',
            'fd_identity="$(stat -Lc \'%d:%i\' -- "/proc/$$/fd/$fd"',
            'if [[ "$fd_identity" != "$path_identity" ]]',
            'if [[ "$mode" == "shared" ]]',
            'if ! wsr_generation_secure_lock_file "$path" "$expected_uid"',
            'WSR_GENERATION_OPERATION_LOCK_FD="$fd"',
        ),
        "Operation lock validation/open/flock ordering changed",
    )

    acquire_fixed = function_body(state, "wsr_generation_acquire_operation_lock")
    require("((EUID != 0))" in acquire_fixed, "Fixed operation lock must require root")
    require(
        'wsr_generation_secure_directory "$WSR_GENERATION_CONTROL_ROOT" 0' in acquire_fixed,
        "Fixed operation lock must validate the preprovisioned root",
    )
    require(
        'wsr_generation_acquire_operation_lock_at "$WSR_GENERATION_OPERATION_LOCK_PATH" 0 "$mode"'
        in acquire_fixed,
        "Fixed operation lock must not accept a caller path",
    )

    require_lock = function_body(state, "wsr_generation_require_operation_lock")
    for needle in (
        '"$required_mode" == "exclusive"',
        '"$WSR_GENERATION_OPERATION_LOCK_MODE" != "exclusive"',
        '"/proc/$$/fd/$WSR_GENERATION_OPERATION_LOCK_FD"',
        'wsr_generation_secure_lock_file "$WSR_GENERATION_OPERATION_LOCK_OPEN_PATH"',
        '"$path_identity" != "$WSR_GENERATION_OPERATION_LOCK_IDENTITY"',
        '"$fd_identity" != "$WSR_GENERATION_OPERATION_LOCK_IDENTITY"',
    ):
        require(needle in require_lock, f"Held-lock validation lost {needle!r}")

    writer = function_body(state, "wsr_generation_publish_text_at")
    require_count(writer, 'wsr_generation_secure_document_file "$destination" "$WSR_GENERATION_OPERATION_LOCK_EXPECTED_UID"', 3, "Destination metadata must be checked before replacement and twice after publication")
    for needle in (
        "document-destination-outside-control-root",
        'mktemp -- "$parent/.${base}.tmp.XXXXXXXX"',
        'chmod 0600 -- "$temporary"',
        'chmod 0400 -- "$temporary"',
        'expected_sha="$(printf \'%s\\n\' "$text"',
        'staged_sha="$(sha256sum -- "$temporary"',
        "stage_id=\"$(stat -Lc '%d:%i' -- \"$temporary\")\"",
        "parent_device=\"$(stat -Lc '%d' -- \"$parent\")\"",
        "stage_device=\"$(stat -Lc '%d' -- \"$temporary\")\"",
        '"$staged_sha" != "$expected_sha"',
        '"$stage_device" != "$parent_device"',
        'sync -- "$temporary"',
        'mv --force --no-target-directory -- "$temporary" "$destination"',
        'mv --no-clobber --no-target-directory -- "$temporary" "$destination"',
        '"$(stat -Lc \'%d:%i\' -- "$destination" 2>/dev/null)" == "$stage_id"',
        'sync -- "$parent"',
        'actual_sha="$(sha256sum -- "$destination"',
        '[[ "$actual_sha" == "$expected_sha" ]]',
    ):
        require(needle in writer, f"Durable document writer lost {needle!r}")
    require_order(
        writer,
        (
            "wsr_generation_require_operation_lock exclusive",
            'wsr_generation_secure_directory "$parent"',
            "document-destination-outside-control-root",
            'mktemp -- "$parent/.${base}.tmp.XXXXXXXX"',
            'chmod 0600 -- "$temporary"',
            'printf \'%s\\n\' "$text" > "$temporary"',
            'chmod 0400 -- "$temporary"',
            'staged_sha="$(sha256sum -- "$temporary"',
            'sync -- "$temporary"',
            'mv --force --no-target-directory -- "$temporary" "$destination"',
            'sync -- "$parent"',
            'actual_sha="$(sha256sum -- "$destination"',
        ),
        "Durable writer no longer fsyncs before rename and parent after rename",
    )

    for adapter, render, publication in (
        ("wsr_generation_replace_selector_v1_at", "wsr_generation_render_selector_v1", "replace"),
        ("wsr_generation_publish_manifest_v1_at", "wsr_generation_render_manifest_v1", "no-clobber"),
        ("wsr_generation_publish_journal_record_v1_at", "wsr_generation_render_journal_record_v1", "no-clobber"),
    ):
        body = function_body(state, adapter)
        require(render in body, f"{adapter} lost canonical rendering")
        require(
            f'wsr_generation_publish_text_at "$1" "$text" {publication}' in body,
            f"{adapter} changed publication semantics",
        )

    fsync_helper = function_body(recovery_common, "wsr_fsync_path").strip()
    require(fsync_helper == 'sync -- "$1"', "Recovery fsync helper must sync the exact file, not its whole filesystem")
    require("sync -f" not in state and "sync -f" not in recovery_common, "Filesystem-wide syncfs is forbidden")


def validate_entrypoint_integration(sources: Mapping[str, str]) -> None:
    state = sources["state"]
    compose = sources["compose"]
    recovery = sources["recovery"]
    recovery_preflight = sources["recovery_preflight"]
    deployment_preflight = sources["deployment_preflight"]
    compose_model = sources["compose_model"]

    require(
        'if [[ -n "${WSR_GENERATION_STATE_LOADED:-}" ]]; then' in state,
        "Generation state lacks a source guard",
    )
    source_only_footer = """if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  printf 'ERROR: generation-state.sh is source-only and exposes no operator action.\\n' >&2
  exit 64
fi"""
    require(state.rstrip().endswith(source_only_footer), "Generation state must reject direct execution with status 64")
    executable_state = "\n".join(
        line for line in state.splitlines() if not line.lstrip().startswith("#")
    )
    require(re.search(r"(?i)(?<![A-Za-z0-9_])(?:docker|wsr_docker)(?![A-Za-z0-9_])", executable_state) is None, "Generation state must never invoke Docker")
    require("wsr_action_" not in state and "main()" not in state, "Generation state exposed an operator action")

    generation_source = 'source "$script_dir/generation-state.sh"'
    for name in ("compose", "recovery", "recovery_preflight", "deployment_preflight"):
        require_count(sources[name], generation_source, 1, f"{name} must source generation state from fixed script_dir")
        require(
            re.search(r"wsr_generation_(?:replace_selector|publish_manifest|publish_journal|publish_text)", sources[name]) is None,
            f"{name} must not call a generation writer",
        )

    compose_arms = case_arms(compose, '"$1"')
    require(set(compose_arms) == set(COMPOSE_ACTIONS) | {"*"}, "Compose action allowlist changed")
    for action, (mode, arguments) in COMPOSE_ACTIONS.items():
        arm = compose_arms[action]
        require(arguments in arm, f"Compose {action} arguments changed")
        require_count(arm, f'operation_lock_mode="{mode}"', 1, f"Compose {action} lock mode changed")
    require_order(
        compose,
        (
            '"${clean_environment[@]}" bash "$script_dir/preflight.sh"',
            'wsr_generation_acquire_operation_lock "$operation_lock_mode"',
            'wsr_generation_require_operation_lock "$operation_lock_mode"',
        ),
        "Compose must preflight before acquiring and requiring its action lock",
    )
    compose_child = re.search(
        r'(?m)^"\$\{clean_environment\[@\]\}" docker compose \\$',
        compose,
    )
    require(compose_child is not None, "Compose must run as a child so the parent shell retains the lock FD")
    require(
        compose_child.start() > compose.index('wsr_generation_require_operation_lock "$operation_lock_mode"'),
        "Compose child started before the held-lock check",
    )
    require(
        'exec "${clean_environment[@]}" docker compose' not in compose,
        "Dynamic Bash lock FDs are not inherited reliably through exec",
    )
    require("wsr_generation_close_lock_fd" not in compose, "Compose must retain its lock until the child exits")

    recovery_main = function_body(recovery, "main")
    recovery_arms = case_arms(recovery_main, '"$action"')
    require(set(recovery_arms) == set(RECOVERY_ACTIONS) | {"*"}, "Recovery action allowlist changed")
    require(declared_recovery_actions(recovery) == set(RECOVERY_ACTIONS), "Recovery usage and implementation differ")
    for action, (mode, action_function) in RECOVERY_ACTIONS.items():
        arm = recovery_arms[action]
        require_count(arm, f"wsr_generation_acquire_operation_lock {mode}", 1, f"Recovery {action} lock acquisition changed")
        require_count(arm, f"wsr_generation_require_operation_lock {mode}", 1, f"Recovery {action} held-lock check changed")
        require_count(arm, action_function, 1, f"Recovery {action} dispatch changed")
        require_order(
            arm,
            (
                f"wsr_generation_acquire_operation_lock {mode}",
                f"wsr_generation_require_operation_lock {mode}",
                action_function,
            ),
            f"Recovery {action} must acquire the global lock before dispatch",
        )
    require(
        re.search(r"(?m)^\s*(activate|rollback|promote)(?:-[a-z0-9-]+)?\)", recovery) is None,
        "A public live generation action was exposed",
    )

    create_body = function_body(recovery, "wsr_action_create")
    rehearse_body = function_body(recovery, "wsr_action_rehearse_latest")
    require_count(create_body, "wsr_acquire_recovery_lock", 1, "Create must retain its HDD lock")
    require_count(rehearse_body, "wsr_acquire_recovery_lock", 1, "Rehearsal must retain its HDD lock")
    require_count(recovery, "wsr_acquire_recovery_lock", 3, "HDD lock must remain limited to its definition, create, and rehearsal")

    recovery_preflight_main = function_body(recovery_preflight, "main")
    recovery_preflight_arms = case_arms(recovery_preflight_main, '"$mode"')
    require(set(recovery_preflight_arms) == {"host", "production", "*"}, "Recovery preflight modes changed")
    require("wsr_generation_" not in recovery_preflight_arms["host"], "Recovery host preflight must not require generation-control provisioning")
    require_order(
        recovery_preflight_arms["production"],
        (
            "wsr_generation_acquire_operation_lock shared",
            "wsr_generation_require_operation_lock shared",
            "wsr_run_production_preflight",
        ),
        "Recovery production preflight must hold a shared global lock",
    )
    require_count(recovery_preflight, "wsr_generation_acquire_operation_lock shared", 1, "Recovery preflight shared lock mapping changed")

    deployment_lock_block = '''if [[ "$mode" == "contract" || "$mode" == "publish" ]]; then
  wsr_generation_acquire_operation_lock shared
  wsr_generation_require_operation_lock shared
fi'''
    require_count(
        deployment_preflight,
        deployment_lock_block,
        1,
        "Deployment lock must be limited to contract/publish modes",
    )
    require_count(deployment_preflight, "wsr_generation_acquire_operation_lock shared", 1, "Deployment preflight must not lock host mode")

    require_count(compose_model, "- postgres-data:/var/lib/postgresql/data", 1, "Compose must retain the legacy PostgreSQL volume mount")
    require("volumes:\n  postgres-data:\n  caddy-data:" in compose_model, "Compose legacy named-volume declaration changed")
    require(re.search(r"(?m)^\s*external:\s*true\s*$", compose_model) is None, "External generation volume was activated before server bootstrap")
    require("WSR_ACTIVE_POSTGRES_VOLUME" not in compose_model, "Compose accepts an unprovisioned active-volume override")


def validate_ci(ci: str) -> None:
    for needle in (
        "run: python scripts/verify-home-server-generation-state.py",
        "deploy/home-server/generation-state.sh \\",
        "scripts/verify-home-server-generation-state.sh \\",
        "run: bash scripts/verify-home-server-generation-state.sh",
    ):
        require_count(ci, needle, 1, f"CI is missing the ADR-050 check {needle!r}")


def validate_contract(sources: Mapping[str, str]) -> None:
    state = sources["state"]
    validate_generation_documents(state)
    validate_state_graph_and_journal(state)
    validate_lock_and_durable_writer(state, sources["recovery_common"])
    validate_entrypoint_integration(sources)
    validate_ci(sources["ci"])


@dataclass(frozen=True)
class Mutation:
    label: str
    source_name: str
    old: str
    new: str


def mutations() -> tuple[Mutation, ...]:
    return (
        Mutation("control root redirected", "state", 'readonly WSR_GENERATION_CONTROL_ROOT="/var/lib/wall-street-receipts/generation-control"', 'readonly WSR_GENERATION_CONTROL_ROOT="/tmp/generation-control"'),
        Mutation("operation lock made caller-selectable", "state", 'readonly WSR_GENERATION_OPERATION_LOCK_PATH="$WSR_GENERATION_CONTROL_ROOT/operation.lock"', 'readonly WSR_GENERATION_OPERATION_LOCK_PATH="${WSR_LOCK_PATH:-$WSR_GENERATION_CONTROL_ROOT/operation.lock}"'),
        Mutation("selector field removed", "state", "  previous_selector_sha256 change_kind transition_uuid plan_sha256 written_utc", "  previous_selector_sha256 change_kind transition_uuid written_utc"),
        Mutation("manifest field reordered", "state", "  promotion_plan_sha256 git_sha postgres_image_reference postgres_image_id", "  git_sha promotion_plan_sha256 postgres_image_reference postgres_image_id"),
        Mutation("backup lock binding removed", "state", "  capture_lock_contract_version\n)", ")"),
        Mutation("journal predecessor removed", "state", "  previous_record_sha256 written_utc\n)", "  written_utc\n)"),
        Mutation("nameref map type guard removed", "state", '  wsr_generation_associative_map_name_valid "$map_name" &&\n', "  true &&\n"),
        Mutation("parser adapter allowlist removed", "state", '     ! wsr_generation_parser_adapter_valid "$fields_name" "$validator" "$renderer"; then', "     false; then"),
        Mutation("canonical field order check removed", "state", '    if [[ "$key" != "$expected" ]]; then', "    if false; then"),
        Mutation("canonical reread hash removed", "state", '  if [[ "$actual_sha" != "$expected_sha" ]]; then', "  if false; then"),
        Mutation("selector volume binding removed", "state", '     ! wsr_generation_volume_matches_id "${selector_ref[active_generation_id]}" "${selector_ref[active_volume_name]}" ||\n', ""),
        Mutation("manifest SCRAM contract weakened", "state", '"${manifest_ref[authentication_contract]}" != "production-password-file-scram-sha-256"', '"${manifest_ref[authentication_contract]}" != "trust"'),
        Mutation("candidate source derivation removed", "state", '        [[ "${manifest_ref[generation_id]}" == "$expected_candidate" ]] &&\n', ""),
        Mutation("selector manifest digest relationship removed", "state", '        "${selector_ref[active_generation_manifest_sha256]}" != "$manifest_sha" ||\n', ""),
        Mutation("selector predates sealed manifest", "state", '        "${selector_ref[written_utc]}" < "${manifest_ref[sealed_utc]}" ]]; then', '        false ]]; then'),
        Mutation("backup relationship volume labels removed", "state", '        "${binding_ref[source_volume_labels_sha256]}" != "${manifest_ref[volume_labels_sha256]}" ||\n', ""),
        Mutation("backup binding kind identity check removed", "state", '      [[ "${binding_ref[source_generation_id]}" == "$WSR_GENERATION_LEGACY_ID" ]] || {', "      true || {"),
        Mutation("unauthorized state transition added", "state", "    source-restarting\\|complete-rollback) printf 'rolled-back\\n' ;;\n    *) return 1 ;;\n", "    source-restarting\\|complete-rollback) printf 'rolled-back\\n' ;;\n    steady\\|finalize) printf 'finalized\\n' ;;\n    *) return 1 ;;\n"),
        Mutation("state transition target changed", "state", "    probation\\|finalize) printf 'finalized\\n' ;;", "    probation\\|finalize) printf 'rolled-back\\n' ;;"),
        Mutation("unknown transition accepted", "state", "    source-restarting\\|complete-rollback) printf 'rolled-back\\n' ;;\n    *) return 1 ;;", "    source-restarting\\|complete-rollback) printf 'rolled-back\\n' ;;\n    *) printf 'steady\\n' ;;"),
        Mutation("journal state graph detached", "state", '  expected_after="$(wsr_generation_next_state "${record_ref[state_before]}" "${record_ref[event]}")"', '  expected_after="${record_ref[state_after]}"'),
        Mutation("journal accepts identical source and target", "state", '     [[ "${record_ref[source_generation_id]}" == "${record_ref[target_generation_id]}" ]]; then', '     false; then'),
        Mutation("journal predecessor check removed", "state", '          "${record[previous_record_sha256]}" != "$expected_previous" ]]; then', '          false ]]; then'),
        Mutation("journal time regression accepted", "state", '    if [[ -n "$previous_written_utc" && "${record[written_utc]}" < "$previous_written_utc" ]]; then', '    if false; then'),
        Mutation("journal operation replay accepted", "state", '      if [[ -n "${seen_operations[${record[operation_uuid]}]+present}" ]]; then', '      if false; then'),
        Mutation("journal selector chain drift accepted", "state", '      if [[ -n "$current_selector_revision" &&\n            ( "${record[selector_before_revision]}" != "$current_selector_revision" ||', '      if [[ -n "$current_selector_revision" &&\n            ( false ||'),
        Mutation("control directory mode weakened", "state", '"$mode" == "700"', '"$mode" == "755"'),
        Mutation("lock mode weakened", "state", '"$mode" == "600"', '"$mode" == "644"'),
        Mutation("document mode weakened", "state", '"$mode" == "400"', '"$mode" == "600"'),
        Mutation("lock hardlink guard removed", "state", ' && "$links" == "1" ]]\n}\n\nwsr_generation_secure_document_file', ' ]]\n}\n\nwsr_generation_secure_document_file'),
        Mutation("procfs pre-flock identity removed", "state", '  fd_identity="$(stat -Lc \'%d:%i\' -- "/proc/$$/fd/$fd" 2>/dev/null)" || true\n', ""),
        Mutation("shared flock became exclusive", "state", 'flock -n -s "$fd"', 'flock -n -x "$fd"'),
        Mutation("exclusive flock became shared", "state", 'flock -n -x "$fd"', 'flock -n -s "$fd"'),
        Mutation("post-flock metadata recheck removed", "state", '  if ! wsr_generation_secure_lock_file "$path" "$expected_uid" ||\n', "  if false ||\n"),
        Mutation("fixed root no longer requires root", "state", "  if ((EUID != 0)) ||", "  if false ||"),
        Mutation("writer accepts shared lock", "state", "  wsr_generation_require_operation_lock exclusive || return 1", "  wsr_generation_require_operation_lock shared || return 1"),
        Mutation("writer control-root containment removed", "state", '    wsr_generation_fail "document-destination-outside-control-root" "Generation documents must remain below the held lock\'s control root."\n', ""),
        Mutation("writer stages in tmp", "state", 'mktemp -- "$parent/.${base}.tmp.XXXXXXXX"', 'mktemp -- "/tmp/.${base}.tmp.XXXXXXXX"'),
        Mutation("writer immutable mode removed", "state", ' || ! chmod 0400 -- "$temporary"', ""),
        Mutation("writer staged digest removed", "state", '  staged_sha="$(sha256sum -- "$temporary" | awk \'{print $1}\')"\n', ""),
        Mutation("writer same-filesystem check removed", "state", '     [[ "$staged_sha" != "$expected_sha" || "$stage_device" != "$parent_device" ]] ||\n', '     [[ "$staged_sha" != "$expected_sha" ]] ||\n'),
        Mutation("writer file fsync removed", "state", '     ! sync -- "$temporary"; then', "     false; then"),
        Mutation("writer parent fsync removed", "state", '  sync -- "$parent" || return 1\n', ""),
        Mutation("writer inode publication check removed", "state", '  [[ "$(stat -Lc \'%d:%i\' -- "$destination" 2>/dev/null)" == "$stage_id" ]] || return 1\n', ""),
        Mutation("generation writer invokes Docker", "state", "\nif [[ \"${BASH_SOURCE[0]}\" == \"$0\" ]]; then", "\nwsr_docker volume rm production-data\n\nif [[ \"${BASH_SOURCE[0]}\" == \"$0\" ]]; then"),
        Mutation("source-only rejection removed", "state", "  exit 64\nfi", "  exit 0\nfi"),
        Mutation("recovery syncfs restored", "recovery_common", 'sync -- "$1"', 'sync -f -- "$1"'),
        Mutation("Compose ps takes exclusive lock", "compose", '  ps)\n    compose_arguments=(ps)\n    operation_lock_mode="shared"', '  ps)\n    compose_arguments=(ps)\n    operation_lock_mode="exclusive"'),
        Mutation("Compose up takes shared lock", "compose", '  up)\n    compose_arguments=(up --detach --wait)\n    operation_lock_mode="exclusive"', '  up)\n    compose_arguments=(up --detach --wait)\n    operation_lock_mode="shared"'),
        Mutation("Compose action lock removed", "compose", 'wsr_generation_acquire_operation_lock "$operation_lock_mode"\n', ""),
        Mutation("Compose exec loses dynamic lock FD", "compose", '\n"${clean_environment[@]}" docker compose \\\n', '\nexec "${clean_environment[@]}" docker compose \\\n'),
        Mutation("recovery status takes exclusive lock", "recovery", '    status)\n      wsr_generation_acquire_operation_lock shared', '    status)\n      wsr_generation_acquire_operation_lock exclusive'),
        Mutation("recovery create dispatches before global lock", "recovery", '    create)\n      wsr_generation_acquire_operation_lock exclusive\n      wsr_generation_require_operation_lock exclusive\n      wsr_action_create', '    create)\n      wsr_action_create\n      wsr_generation_acquire_operation_lock exclusive\n      wsr_generation_require_operation_lock exclusive'),
        Mutation("live recovery action declared", "recovery", "Allowed actions: preflight, create, status, rehearse-latest, retention-plan, schema-check-latest, promotion-plan-latest.", "Allowed actions: preflight, create, status, rehearse-latest, retention-plan, schema-check-latest, promotion-plan-latest, activate."),
        Mutation("recovery host preflight requires control root", "recovery_preflight", "    host)\n      wsr_run_host_preflight", "    host)\n      wsr_generation_acquire_operation_lock shared\n      wsr_run_host_preflight"),
        Mutation("recovery production shared lock removed", "recovery_preflight", "      wsr_generation_acquire_operation_lock shared\n", ""),
        Mutation("deployment host mode locked", "deployment_preflight", 'if [[ "$mode" == "contract" || "$mode" == "publish" ]]; then\n  wsr_generation_acquire_operation_lock shared\n  wsr_generation_require_operation_lock shared\nfi', 'if [[ "$mode" == "host" || "$mode" == "contract" || "$mode" == "publish" ]]; then\n  wsr_generation_acquire_operation_lock shared\n  wsr_generation_require_operation_lock shared\nfi'),
        Mutation("Compose legacy volume changed", "compose_model", "- postgres-data:/var/lib/postgresql/data", "- replacement-data:/var/lib/postgresql/data"),
        Mutation("Compose external volume enabled", "compose_model", "  postgres-data:\n", "  postgres-data:\n    external: true\n"),
        Mutation("Python verifier removed from CI", "ci", "      - name: Validate ADR-050 generation-control contract\n        run: python scripts/verify-home-server-generation-state.py\n\n", ""),
        Mutation("Bash verifier removed from CI", "ci", "      - name: Verify generation-control documents, journal, and host lock\n        shell: bash\n        run: bash scripts/verify-home-server-generation-state.sh\n\n", ""),
    )


def read_sources() -> dict[str, str]:
    sources: dict[str, str] = {}
    for name, path in PATHS.items():
        require(path.is_file(), f"Required contract source is missing: {path.relative_to(ROOT)}")
        sources[name] = path.read_text(encoding="utf-8")
    return sources


def run_mutation_self_test(baseline: Mapping[str, str]) -> int:
    total = 0
    for mutation in mutations():
        original = baseline[mutation.source_name]
        require(
            original.count(mutation.old) == 1,
            f"Mutation fixture {mutation.label!r} no longer identifies exactly one source fragment",
        )
        changed = dict(baseline)
        changed[mutation.source_name] = original.replace(mutation.old, mutation.new, 1)
        try:
            validate_contract(changed)
        except ContractError:
            total += 1
            continue
        raise ContractError(f"Mutation unexpectedly passed: {mutation.label}")
    return total


def main() -> int:
    try:
        sources = read_sources()
        validate_contract(sources)
        mutation_count = run_mutation_self_test(sources)
    except (ContractError, OSError, UnicodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print(
        "PASS: ADR-050 generation control static contract and "
        f"{mutation_count}-case mutation self-test passed."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
