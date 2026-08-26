#!/usr/bin/env python3
"""Static, mutation-sensitive guard for the ADR-047/ADR-048 recovery boundary.

The guard deliberately does not execute the Ubuntu recovery scripts.  It is
safe to run on Windows and in CI: it reads only committed recovery sources,
validates their security-critical vocabulary and ordering, then proves the
pure semantic validator rejects independent contract mutations.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from pathlib import Path
import re
import sys
from typing import Callable, FrozenSet, Iterable


ROOT = Path(__file__).resolve().parents[1]
DEPLOY = ROOT / "deploy" / "home-server"

CONFIG_KEYS = frozenset(
    {
        "WSR_BACKUP_MOUNT",
        "WSR_BACKUP_FILESYSTEM_UUID",
        "WSR_BACKUP_ENCRYPTION",
    }
)
PLACEHOLDER_KEYS = frozenset({"WSR_BACKUP_FILESYSTEM_UUID"})
ACTIONS = frozenset(
    {
        "preflight",
        "create",
        "status",
        "rehearse-latest",
        "retention-plan",
        "schema-check-latest",
    }
)
FILESYSTEMS = frozenset({"ext4", "xfs"})
MOUNT_OPTIONS = frozenset({"rw", "nodev", "nosuid", "noexec"})
ENCRYPTION_MODES = frozenset({"luks2", "none-demo-only"})
RETENTION = (14, 8, 12)

CONFIG = DEPLOY / "backup.conf.example"
EVIDENCE_SQL = DEPLOY / "database-evidence.sql"
COMMON = DEPLOY / "recovery-common.sh"
PREFLIGHT = DEPLOY / "recovery-preflight.sh"
PRODUCTION = DEPLOY / "recovery-production.sh"
SCHEMA_COMPATIBILITY = DEPLOY / "schema-compatibility.sh"
LOCAL_REHEARSAL = ROOT / "scripts" / "verify-home-server-deployment.ps1"


class ContractError(RuntimeError):
    """Raised when a recovery invariant is missing or weakened."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def has_all(source: str, values: Iterable[str], *, ignore_case: bool = True) -> bool:
    candidate = source.casefold() if ignore_case else source
    expected = (value.casefold() for value in values) if ignore_case else values
    return all(value in candidate for value in expected)


def strip_full_line_comments(source: str) -> str:
    """Remove shell/SQL comment-only lines while retaining commands and strings."""

    return "\n".join(
        line for line in source.splitlines() if not re.match(r"^\s*(?:#|--)(?:\s|$)", line)
    )


def shell_function_body(source: str, name: str) -> str:
    match = re.search(
        rf"(?ms)^{re.escape(name)}\(\)\s*\{{(?P<body>.*?)^\}}",
        source,
    )
    return match.group("body") if match else ""


def powershell_function_body(source: str, name: str) -> str:
    match = re.search(
        rf"(?ms)^function\s+{re.escape(name)}\s*\{{(?P<body>.*?)(?=^function\s+|\Z)",
        source,
    )
    return match.group("body") if match else ""


def placeholder_value(key: str, value: str) -> bool:
    lowered = value.casefold()
    generic_placeholder = any(
        marker in lowered
        for marker in ("replace", "placeholder", "example", "unknown", "unconfigured", "pending")
    )
    if key == "WSR_BACKUP_MOUNT":
        return value.startswith("/") and generic_placeholder
    if key == "WSR_BACKUP_FILESYSTEM_UUID":
        return generic_placeholder
    if key == "WSR_BACKUP_ENCRYPTION":
        return generic_placeholder
    return False


def parse_config_example(source: str) -> tuple[FrozenSet[str], FrozenSet[str]]:
    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(source.splitlines(), start=1):
        line = raw_line.rstrip("\r")
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([A-Z][A-Z0-9_]*)=([A-Za-z0-9._:/@+-]+)", line)
        require(
            match is not None,
            f"backup.conf.example line {line_number} must be an unquoted KEY=value without spaces",
        )
        key, value = match.groups()
        require(key not in values, f"backup.conf.example repeats {key}")
        values[key] = value
    if "WSR_BACKUP_MOUNT" in values:
        require(
            values["WSR_BACKUP_MOUNT"].startswith("/")
            and values["WSR_BACKUP_MOUNT"] != "/",
            "The example backup mount must be an absolute non-root path",
        )
    if "WSR_BACKUP_ENCRYPTION" in values:
        require(
            values["WSR_BACKUP_ENCRYPTION"] in ENCRYPTION_MODES,
            "The example encryption decision must be luks2 or none-demo-only",
        )
    return frozenset(values), frozenset(
        key for key, value in values.items() if placeholder_value(key, value)
    )


def parse_declared_actions(source: str) -> FrozenSet[str]:
    usage_match = re.search(r"(?im)^\s*Allowed actions:\s*([^\n]+?)\.?\s*$", source)
    require(usage_match is not None, "Recovery wrapper must declare one exact Allowed actions line")
    declared = frozenset(
        part.strip()
        for part in usage_match.group(1).split(",")
        if part.strip()
    )
    main_body = shell_function_body(source, "main")
    action_case = re.search(r'(?ms)case\s+"\$action"\s+in(?P<body>.*?)^\s*esac\s*$', main_body)
    require(action_case is not None, "Recovery wrapper must dispatch one explicit action case")
    implemented = frozenset(
        match.group(1)
        for match in re.finditer(
            r"(?m)^\s*([a-z][a-z0-9-]*)\)",
            action_case.group("body"),
        )
    )
    require(declared == implemented, "Declared and implemented recovery actions differ")
    return declared


@dataclass(frozen=True)
class RecoveryContract:
    config_keys: FrozenSet[str]
    placeholder_keys: FrozenSet[str]
    actions: FrozenSet[str]
    config_allowlist_enforced: bool
    config_path_fixed: bool
    encryption_modes: FrozenSet[str]
    arbitrary_arguments_rejected: bool
    exact_mount_required: bool
    missing_mount_fails: bool
    filesystem_uuid_required: bool
    filesystems: FrozenSet[str]
    mount_options: FrozenSet[str]
    physical_leaf_disjoint: bool
    nested_mounts_rejected: bool
    store_identity_bound: bool
    source_fixed_project: bool
    source_compose_labels: bool
    source_healthy: bool
    source_single_postgres: bool
    source_named_volume: bool
    source_no_ports: bool
    dump_custom: bool
    dump_no_owner: bool
    dump_no_privileges: bool
    dump_host_partial: bool
    archive_checksum: bool
    archive_list_validation: bool
    strict_manifest: bool
    fsync_before_publish: bool
    atomic_no_clobber_publish: bool
    no_backup_bind_mount: bool
    restore_checksum_first: bool
    restore_label_owned: bool
    restore_fresh_volume: bool
    restore_fresh_container: bool
    restore_network_none: bool
    restore_no_ports: bool
    restore_single_transaction: bool
    restore_exit_on_error: bool
    restore_no_owner: bool
    restore_no_privileges: bool
    evidence_read_only: bool
    evidence_flyway: bool
    evidence_table_inventory: bool
    evidence_restored_only: bool
    evidence_dynamic_summary: bool
    cleanup_exact_ownership: bool
    restore_capacity_checked: bool
    retention: tuple[int, int, int]
    retention_read_only: bool
    no_production_restore: bool
    no_destructive_commands: bool
    no_device_mutation: bool
    no_clean_checkout_dependency: bool
    rollback_git_sha: bool
    rollback_image_ids: bool
    rollback_revisions: bool
    rollback_flyway: bool
    rollback_excludes_secrets: bool
    no_dns_acme_dependency: bool
    pending_backup_device: bool
    pending_offsite_copy: bool


def validate_contract(contract: RecoveryContract) -> None:
    require(contract.config_keys == CONFIG_KEYS, "Recovery config allowlist changed")
    require(
        contract.placeholder_keys == PLACEHOLDER_KEYS,
        "Only the unknown filesystem UUID may remain a committed placeholder",
    )
    require(contract.actions == ACTIONS, "Recovery action allowlist changed")
    require(contract.config_allowlist_enforced, "Runtime config allowlist enforcement disappeared")
    require(contract.config_path_fixed, "Production recovery config path became caller-selectable")
    require(
        contract.encryption_modes == ENCRYPTION_MODES,
        "Backup encryption modes must be exactly luks2 or none-demo-only",
    )
    require(contract.arbitrary_arguments_rejected, "Recovery wrapper accepts arbitrary arguments, paths, or IDs")

    require(contract.exact_mount_required, "Backup destination must be an exact mount point")
    require(contract.missing_mount_fails, "A missing backup mount must fail closed")
    require(contract.filesystem_uuid_required, "Mounted filesystem UUID binding disappeared")
    require(contract.filesystems == FILESYSTEMS, "Backup filesystem allowlist must be exactly ext4/xfs")
    require(contract.mount_options == MOUNT_OPTIONS, "Required backup mount options changed")
    require(contract.physical_leaf_disjoint, "Backup and Docker storage physical leaves must be disjoint")
    require(contract.nested_mounts_rejected, "Nested mounts may not bypass backup/Docker storage identity")
    require(contract.store_identity_bound, "Versioned backup-store identity binding disappeared")

    require(contract.source_fixed_project, "Production source project binding changed")
    require(contract.source_compose_labels, "Compose project/service label verification disappeared")
    require(contract.source_healthy, "Production PostgreSQL health verification disappeared")
    require(contract.source_single_postgres, "Exactly one source PostgreSQL container is required")
    require(contract.source_named_volume, "Exact postgres-data named-volume binding disappeared")
    require(contract.source_no_ports, "Source PostgreSQL must have no published ports")

    require(contract.dump_custom, "pg_dump must use custom archive format")
    require(contract.dump_no_owner, "pg_dump must omit ownership")
    require(contract.dump_no_privileges, "pg_dump must omit privileges")
    require(contract.dump_host_partial, "Dump bytes must first land in a host-side partial artifact")
    require(contract.archive_checksum, "Backup archive checksum verification disappeared")
    require(contract.archive_list_validation, "pg_restore archive-list validation disappeared")
    require(contract.strict_manifest, "Strict backup manifest evidence disappeared")
    require(contract.fsync_before_publish, "Backup files and directory must be synced before publication")
    require(contract.atomic_no_clobber_publish, "Backup completion must be atomic and no-clobber")
    require(contract.no_backup_bind_mount, "The backup filesystem may not be mounted into containers")

    require(contract.restore_checksum_first, "Restore must verify checksum before creating target resources")
    require(contract.restore_label_owned, "Restore resources must carry and verify an ownership label")
    require(contract.restore_fresh_volume, "Restore must use a fresh isolated volume")
    require(contract.restore_fresh_container, "Restore must use a fresh isolated container")
    require(contract.restore_network_none, "Restore container must use network none")
    require(contract.restore_no_ports, "Restore container must publish no ports")
    require(contract.restore_single_transaction, "pg_restore must be single-transaction")
    require(contract.restore_exit_on_error, "pg_restore must exit on the first error")
    require(contract.restore_no_owner, "pg_restore must not restore owners")
    require(contract.restore_no_privileges, "pg_restore must not restore privileges")

    require(contract.evidence_read_only, "Database evidence SQL must be transactionally read-only")
    require(contract.evidence_flyway, "Restored Flyway evidence disappeared")
    require(contract.evidence_table_inventory, "Restored table inventory evidence disappeared")
    require(contract.evidence_restored_only, "Database evidence may be queried only from the restored target")
    require(
        contract.evidence_dynamic_summary,
        "Restore evidence must bind observed database counts instead of fixture constants",
    )
    require(contract.cleanup_exact_ownership, "Cleanup no longer proves exact resource ownership")
    require(contract.restore_capacity_checked, "Fresh-volume restore capacity is not checked before allocation")

    require(contract.retention == RETENTION, "Retention plan must remain 14 daily, 8 weekly, 12 monthly")
    require(contract.retention_read_only, "Retention must remain a read-only plan")
    require(contract.no_production_restore, "Production or in-place restore became reachable")
    require(contract.no_destructive_commands, "Broad or destructive recovery command appeared")
    require(contract.no_device_mutation, "Recovery tooling may not format, mount, unlock, or repartition devices")
    require(contract.no_clean_checkout_dependency, "Database backup may not depend on a clean checkout or HEAD")

    require(contract.rollback_git_sha, "Rollback evidence lost the exact Git SHA")
    require(contract.rollback_image_ids, "Rollback evidence lost exact image IDs")
    require(contract.rollback_revisions, "Rollback evidence lost OCI image revisions")
    require(contract.rollback_flyway, "Rollback evidence lost restored Flyway state")
    require(contract.rollback_excludes_secrets, "Rollback evidence can include secrets or Caddy key material")
    require(contract.no_dns_acme_dependency, "Recovery scripts gained a DNS or ACME dependency")
    require(contract.pending_backup_device, "Unverified hardware must remain PENDING_BACKUP_DEVICE")
    require(contract.pending_offsite_copy, "Missing off-site redundancy must remain PENDING_OFFSITE_COPY")


def source_contract(
    config_source: str,
    evidence_source: str,
    common_source: str,
    preflight_source: str,
    production_source: str,
    schema_source: str,
) -> RecoveryContract:
    config_keys, placeholders = parse_config_example(config_source)
    actions = parse_declared_actions(production_source)
    common_code = strip_full_line_comments(common_source)
    preflight_code = strip_full_line_comments(preflight_source)
    production_code = strip_full_line_comments(production_source)
    schema_code = strip_full_line_comments(schema_source)
    scripts_code = "\n".join((common_code, preflight_code, production_code, schema_code))
    evidence_upper = evidence_source.upper()
    preflight_policy = "\n".join((common_code, preflight_code))

    config_loader = shell_function_body(common_code, "wsr_load_backup_config")
    config_allowlist_match = re.search(
        r"local\s+allowed='\^\((?P<keys>WSR_[A-Z0-9_|]+)\)\$'",
        config_loader,
    )
    runtime_config_keys = (
        frozenset(config_allowlist_match.group("keys").split("|"))
        if config_allowlist_match
        else frozenset()
    )
    config_allowlist_enforced = (
        runtime_config_keys == CONFIG_KEYS
        and has_all(scripts_code, ("unapproved", "duplicate"))
        and re.search(
            r'if\s+\[\[\s*!\s*"\$key"\s*=~\s*\$allowed\s*\]\];\s*then',
            config_loader,
        ) is not None
        and re.search(
            r'if\s+\[\[\s*-n\s+"\$\{WSR_BACKUP_CONFIG\[\$key\]\+present\}"\s*\]\];\s*then',
            config_loader,
        ) is not None
    )
    config_path_fixed = (
        "/etc/wall-street-receipts/backup.conf" in scripts_code
        and "--config" not in production_code
    )
    encryption_modes = frozenset(
        mode for mode in ENCRYPTION_MODES if re.search(rf"\b{re.escape(mode)}\b", scripts_code)
    )
    arbitrary_arguments_rejected = (
        re.search(r"\(\(\$#\s*!=\s*2\)\)", production_code) is not None
        and re.search(r"\[\[\s*\"\$1\"\s*!=\s*\"--\"\s*\]\]", production_code)
        is not None
        and has_all(production_code, ("no config path", "Docker", "argument"))
        and re.search(r"(?m)^\s*(?:exec\s+)?(?:docker|wsr_docker)\b[^\n]*\"\$@\"", production_code)
        is None
    )

    mount_body = shell_function_body(common_code, "wsr_validate_backup_mount")
    backup_path_body = shell_function_body(common_code, "wsr_require_backup_filesystem_path")
    docker_path_body = shell_function_body(common_code, "wsr_require_docker_root_filesystem_path")
    exact_mount_required = has_all(mount_body, ("findmnt", "--mountpoint", "TARGET"))
    missing_mount_fails = has_all(preflight_policy, ("mount", "fail")) and (
        "if !" in preflight_policy or "||" in preflight_policy
    )
    filesystem_uuid_required = has_all(
        preflight_policy, ("WSR_BACKUP_FILESYSTEM_UUID", "UUID", "findmnt")
    )
    filesystem_type_guard = re.search(
        r'if\s+\[\[\s*"\$filesystem_type"\s*!=\s*"ext4"\s*&&\s*'
        r'"\$filesystem_type"\s*!=\s*"xfs"\s*\]\];\s*then',
        mount_body,
    )
    discovered_filesystems = FILESYSTEMS if filesystem_type_guard else frozenset()
    mount_option_loop = re.search(
        r"for\s+required_option\s+in\s+(?P<options>[a-z ]+);\s*do",
        mount_body,
    )
    discovered_options = (
        frozenset(mount_option_loop.group("options").split())
        if mount_option_loop
        else frozenset()
    )
    physical_leaf_collection_body = shell_function_body(
        common_code,
        "wsr_collect_physical_leaf_disks",
    )
    physical_leaf_disjoint = (
        has_all(preflight_policy, ("lsblk", "physical", "leaf", "DockerRootDir", "disjoint"))
        and re.search(
            r'if\s+\[\[\s*"\$backup_disk"\s*==\s*"\$docker_disk"\s*\]\];\s*then',
            mount_body,
        )
        is not None
        and has_all(
            shell_function_body(common_code, "wsr_validate_block_topology_rows"),
            (
                'case "$type" in',
                "disk)",
                "part)",
                "crypt)",
                "unsupported LVM, RAID, mapper, or virtual type",
                "disk_count != 1",
                "part_count > 1",
                "crypt_count != 1",
            ),
            ignore_case=False,
        )
        and re.search(
            r'(?m)^\s*wsr_validate_block_topology_rows\s+"\$path"\s+"\$topology"\s+rows\s+\|\|\s+return\s+1\s*$',
            physical_leaf_collection_body,
        ) is not None
        and re.search(
            r'(?m)^\s*lsblk\s+--nodeps\s+--noheadings\s+--raw\s+--output\s+SERIAL\s+"\$name"\s*$',
            physical_leaf_collection_body,
        ) is not None
        and re.search(
            r'(?m)^\s*lsblk\s+--nodeps\s+--noheadings\s+--raw\s+--output\s+TRAN\s+"\$name"\s*$',
            physical_leaf_collection_body,
        ) is not None
        and has_all(
            physical_leaf_collection_body,
            (
                'case "$transport" in',
                "sata|usb)",
                "nvme)",
                "transport=%s\\nserial=%s\\n",
                "sha256sum",
            ),
            ignore_case=False,
        )
    )
    nested_mounts_rejected = (
        has_all(
            backup_path_body,
            (
                "findmnt",
                "ID,TARGET,SOURCE,FSTYPE,UUID",
                "WSR_BACKUP_MOUNT_ID",
                "WSR_BACKUP_MOUNT_DEVICE_ID",
            ),
            ignore_case=False,
        )
        and has_all(
            docker_path_body,
            (
                "findmnt",
                "ID,TARGET,SOURCE",
                "WSR_DOCKER_MOUNT_ID",
                "WSR_DOCKER_MOUNT_DEVICE_ID",
            ),
            ignore_case=False,
        )
        and common_code.count("wsr_require_backup_filesystem_path") >= 8
        and common_code.count("wsr_require_docker_root_filesystem_path") >= 2
    )
    store_identity_bound = has_all(
        preflight_policy,
        (
            ".store-identity",
            "schema_version",
            "namespace",
            "filesystem_uuid",
            "mode 0400",
            "single-linked",
            "store_identity_sha256",
        ),
    )

    source_fixed_project = "wall-street-receipts-home" in scripts_code
    source_compose_labels = has_all(
        scripts_code,
        (
            "com.docker.compose.project",
            "com.docker.compose.service",
            "com.wallstreetreceipts.role",
            "com.wallstreetreceipts.release-sha",
            "production-primary-database",
            "postgres",
        ),
        ignore_case=False,
    )
    postgres_validation_body = shell_function_body(common_code, "wsr_validate_production_postgres")
    source_healthy = re.search(
        r'if\s+\[\[\s*"\$running"\s*!=\s*"true"\s*\|\|\s*'
        r'"\$health"\s*!=\s*"healthy"\s*\]\];\s*then',
        postgres_validation_body,
    ) is not None
    source_single_postgres = re.search(
        r'if\s+\(\(\$\{#container_ids\[@\]\}\s*!=\s*1\)\);\s*then',
        postgres_validation_body,
    ) is not None
    source_named_volume = re.search(
        r'if\s+\(\(\$\{#data_mounts\[@\]\}\s*!=\s*2\)\)\s*\|\|\s*'
        r'\[\[\s*"\$\{data_mounts\[0\]\}"\s*!=\s*"bind\|\|/run/secrets/postgres_password\|false"\s*\|\|\s*'
        r'"\$\{data_mounts\[1\]\}"\s*!=\s*"volume\|\$WSR_RECOVERY_POSTGRES_VOLUME\|\$WSR_RECOVERY_DATA_DESTINATION\|true"\s*\]\];\s*then',
        postgres_validation_body,
    ) is not None
    source_no_ports = re.search(
        r'if\s+\[\[\s*"\$port_binding_count"\s*!=\s*"0"\s*\|\|\s*'
        r'"\$network_mode"\s*==\s*"host"\s*\]\];\s*then',
        postgres_validation_body,
    ) is not None

    create_body = shell_function_body(production_code, "wsr_action_create")
    rehearse_body = shell_function_body(production_code, "wsr_action_rehearse_latest")
    dump_match = re.search(
        r'(?ms)^\s*pg_dump\s+\\(?P<body>.*?)^\s*>\s*"\$dump"',
        create_body,
    )
    dump_command = dump_match.group(0) if dump_match else ""
    dump_custom = "--format=custom" in dump_command or "-Fc" in dump_command
    dump_no_owner = "--no-owner" in dump_command
    dump_no_privileges = "--no-privileges" in dump_command
    dump_host_partial = bool(dump_command) and ".partial" in create_body and '> "$dump"' in dump_command
    completed_backup_validation_body = shell_function_body(
        common_code,
        "wsr_validate_completed_backup",
    )
    archive_checksum = (
        'if ! (cd -- "$artifact" && sha256sum --check --strict --status database.dump.sha256); then'
        in completed_backup_validation_body
    )
    create_inventory_command = re.search(
        r'(?m)^\s*wsr_docker\s+exec\s+-i\s+"\$source_container_id"\s+pg_restore\s+--list\s+<\s+"\$dump"\s+>\s+"\$inventory"\s*$',
        create_body,
    )
    backup_manifest_writer_body = shell_function_body(common_code, "wsr_write_backup_manifest")
    backup_manifest_loader_body = shell_function_body(common_code, "wsr_load_backup_manifest")
    strict_manifest = has_all(
        scripts_code,
        (
            "manifest",
            "schema_version",
            "backup_id",
            "pg_dump_options",
            "format-custom+compress-6+no-owner+no-privileges+no-password",
            "database.inventory",
            "archive_inventory_bytes",
            "archive_inventory_entries",
            "archive_inventory_sha256",
        ),
    ) and has_all(
        backup_manifest_writer_body,
        (
            "printf 'pg_dump_options=format-custom+compress-6+no-owner+no-privileges+no-password",
            "printf 'archive_file=database.dump",
        ),
        ignore_case=False,
    ) and has_all(
        backup_manifest_loader_body,
        (
            "WSR_BACKUP_MANIFEST[pg_dump_options]",
            "format-custom+compress-6+no-owner+no-privileges+no-password",
        ),
        ignore_case=False,
    )
    fsync_helper_body = shell_function_body(common_code, "wsr_fsync_path")
    backup_fsync_block = "\n".join(
        (
            'wsr_fsync_path "$dump"',
            '  wsr_fsync_path "$inventory"',
            '  wsr_fsync_path "$checksum_file"',
            '  wsr_fsync_path "$manifest"',
            '  wsr_fsync_path "$WSR_PARTIAL_PATH"',
        )
    )
    backup_fsync_position = create_body.find(backup_fsync_block)
    backup_publish_position = create_body.find("wsr_publish_directory_no_clobber")
    backup_parent_fsync_match = re.search(
        r'(?m)^\s*wsr_fsync_path\s+"\$WSR_BACKUPS_ROOT"\s*$',
        create_body,
    )
    backup_parent_fsync_position = (
        backup_parent_fsync_match.start() if backup_parent_fsync_match else -1
    )
    evidence_fsync_block = "\n".join(
        (
            'wsr_fsync_path "$evidence_file"',
            '  wsr_fsync_path "$evidence_manifest"',
            '  wsr_fsync_path "$evidence_partial"',
        )
    )
    evidence_fsync_position = rehearse_body.find(evidence_fsync_block)
    evidence_publish_position = rehearse_body.rfind("wsr_publish_directory_no_clobber")
    evidence_parent_fsync_match = re.search(
        r'(?m)^\s*wsr_fsync_path\s+"\$evidence_parent"\s*$',
        rehearse_body,
    )
    evidence_parent_fsync_position = (
        evidence_parent_fsync_match.start() if evidence_parent_fsync_match else -1
    )
    fsync_before_publish = (
        re.search(r'(?m)^\s*sync\s+-f\s+--\s+"\$1"\s*$', fsync_helper_body) is not None
        and 0 <= backup_fsync_position < backup_publish_position < backup_parent_fsync_position
        and 0 <= evidence_fsync_position < evidence_publish_position < evidence_parent_fsync_position
    )
    publication_body = shell_function_body(common_code, "wsr_publish_directory_no_clobber")
    allocation_body = shell_function_body(
        production_code,
        "wsr_allocate_unique_utc_staging_directory",
    )
    unique_utc_staging = (
        has_all(
            allocation_body,
            (
                "date -u +%Y-%m-%dT%H:%M:%SZ",
                'name "$compact_utc-????????"',
                'name ".partial-$compact_utc-????????"',
                "sleep 1",
                "mktemp -d",
                "collisions[@]} != 1",
                "WSR_ALLOCATED_UTC",
                "WSR_ALLOCATED_PATH",
            ),
            ignore_case=False,
        )
        and re.search(
            r'(?m)^\s*wsr_allocate_unique_utc_staging_directory\s+"\$WSR_BACKUPS_ROOT"\s*$',
            create_body,
        ) is not None
        and re.search(
            r'(?m)^\s*wsr_allocate_unique_utc_staging_directory\s+"\$evidence_parent"\s*$',
            rehearse_body,
        ) is not None
    )
    atomic_no_clobber_publish = (
        production_code.count("wsr_publish_directory_no_clobber") == 2
        and unique_utc_staging
        and re.search(r"\bmv\b[^\n]*(?:--no-clobber|\s-n(?:\s|$))", publication_body)
        is not None
        and "--no-target-directory" in publication_body
        and has_all(publication_body, ("atomic", "destination", "stat", "inode"))
    )
    no_backup_bind_mount = not (
        re.search(r"(?i)type\s*=\s*bind", production_code)
        or re.search(r"(?is)(?:--volume|-v\s+)[^\n]*(?:WSR_BACKUP_MOUNT|backup_mount)", production_code)
        or re.search(r"(?is)(?:WSR_BACKUP_MOUNT|backup_mount)[^\n]*(?:--volume|-v\s+)", production_code)
    )

    checksum_positions = [
        match.start()
        for match in re.finditer(r"wsr_validate_completed_backup", rehearse_body, re.I)
    ]
    target_positions = [
        match.start()
        for match in re.finditer(
            r"(?:wsr_docker|docker)\s+(?:volume\s+create|container\s+create|create|run)",
            rehearse_body,
            re.I,
        )
    ]
    restore_checksum_first = (
        bool(checksum_positions and target_positions)
        and max(checksum_positions) < min(target_positions)
    )
    restore_volume_validation_body = shell_function_body(
        production_code,
        "wsr_validate_restore_volume",
    )
    restore_runtime_validation_body = shell_function_body(
        production_code,
        "wsr_validate_restore_runtime",
    )
    restore_label_owned = (
        has_all(
            rehearse_body,
            ("--label", "WSR_RECOVERY_OWNER_LABEL", "WSR_RESTORE_OWNER_TOKEN"),
            ignore_case=False,
        )
        and has_all(
            restore_volume_validation_body,
            (
                "WSR_RESTORE_VOLUME_NAME",
                "WSR_RESTORE_OWNER_TOKEN",
                "WSR_RECOVERY_SCOPE_VALUE",
                "WSR_RECOVERY_POSTGRES_VOLUME",
                "wsr_require_docker_root_filesystem_path",
            ),
            ignore_case=False,
        )
        and has_all(
            restore_runtime_validation_body,
            (
                "WSR_RESTORE_CONTAINER_ID",
                "WSR_RESTORE_CONTAINER_NAME",
                "WSR_RESTORE_OWNER_TOKEN",
                "WSR_RECOVERY_SCOPE_VALUE",
                "network_mode",
                "port_binding_count",
                "WSR_RESTORE_VOLUME_NAME",
            ),
            ignore_case=False,
        )
    )
    volume_create_match = re.search(
        r"(?m)^\s*(?:wsr_docker|docker)\s+volume\s+create\b",
        rehearse_body,
    )
    volume_validation_position = rehearse_body.find("wsr_validate_restore_volume")
    restore_fresh_volume = (
        has_all(rehearse_body, ("volume inspect", "unexpectedly", "WSR_RESTORE_VOLUME_NAME"), ignore_case=False)
        and volume_create_match is not None
        and volume_validation_position > volume_create_match.start()
    )
    container_create_match = re.search(
        r"(?m)^\s*(?:wsr_docker|docker)\s+container\s+create\b",
        rehearse_body,
    )
    container_id_position = rehearse_body.find('WSR_RESTORE_CONTAINER_ID="$created_container"')
    container_validation_position = rehearse_body.find(
        "wsr_validate_restore_runtime",
        container_id_position,
    )
    container_start_match = re.search(
        r"(?m)^\s*(?:wsr_docker|docker)\s+container\s+start\b",
        rehearse_body,
    )
    restore_fresh_container = (
        has_all(rehearse_body, ("container inspect", "unexpectedly", "WSR_RESTORE_CONTAINER_NAME"), ignore_case=False)
        and container_create_match is not None
        and "^[0-9a-f]{64}$" in rehearse_body
        and container_id_position > container_create_match.start()
        and container_validation_position > container_id_position
        and container_start_match is not None
        and container_start_match.start() > container_validation_position
    )
    restore_network_none = (
        re.search(r'(?m)^\s*--network\s+none\s+\\\s*$', rehearse_body) is not None
        and re.search(
            r'"\$network_mode"\s*!=\s*"none"\s*\|\|\s*"\$port_binding_count"\s*!=\s*"0"',
            restore_runtime_validation_body,
        ) is not None
    )
    restore_no_ports = (
        "--publish" not in rehearse_body
        and "--publish-all" not in rehearse_body
        and re.search(r"(?:^|\s)-(?:p|P)(?:\s|=|\d|\\|$)", rehearse_body) is None
        and has_all(scripts_code, ("PortBindings", "port"), ignore_case=False)
    )
    restore_match = re.search(
        r'(?ms)^\s*wsr_docker\s+exec\s+-i\s+"\$WSR_RESTORE_CONTAINER_ID"\s+\\\s*$'
        r'(?P<body>.*?^\s*pg_restore\s+\\.*?^\s*<\s*"\$dump"\s*$)',
        rehearse_body,
    )
    restore_command = restore_match.group(0) if restore_match else ""
    restore_single_transaction = "--single-transaction" in restore_command
    restore_exit_on_error = "--exit-on-error" in restore_command
    restore_no_owner = "--no-owner" in restore_command
    restore_no_privileges = "--no-privileges" in restore_command

    evidence_read_only = (
        re.search(
            r"(?m)^BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;\s*$",
            evidence_upper,
        ) is not None
        and re.search(r"(?m)^COMMIT;\s*$", evidence_upper) is not None
        and re.search(r"\b(?:INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE)\b", evidence_upper)
        is None
    )
    evidence_flyway = has_all(
        evidence_source,
        (
            "WSR_DATABASE_EVIDENCE_VERSION=2",
            "flyway_schema_history",
            "installed_rank",
            "description",
            "type",
            "script",
            "checksum",
            "success",
            "convert_to",
        ),
    )
    evidence_table_inventory = (
        (
            has_all(evidence_source, ("information_schema.tables", "table_schema"))
            or has_all(evidence_source, ("pg_catalog.pg_tables", "schemaname", "tablename"))
        )
        and has_all(evidence_source, ("TABLE_ROWS", "count(*)", "ORDER BY"))
    )
    restore_inventory_commands = re.findall(
        r'(?m)^\s*wsr_docker\s+exec\s+-i\s+"\$WSR_RESTORE_CONTAINER_ID"\s+pg_restore\s+--list\s+<\s+"\$dump"',
        rehearse_body,
    )
    archive_list_validation = (
        create_inventory_command is not None and len(restore_inventory_commands) == 2
    )
    evidence_command_match = re.search(
        r'(?ms)^\s*wsr_docker\s+exec\s+-i\s+"\$WSR_RESTORE_CONTAINER_ID"\s+\\\s*$'
        r'\s*psql\s+-X\s+-q\s+-A\s+-t\s+--no-password\s+--username="\$WSR_RECOVERY_DATABASE_USER"\s+\\\s*$'
        r'\s*--dbname="\$WSR_RECOVERY_DATABASE"\s+\\\s*$'
        r'\s*<\s+"\$script_dir/database-evidence.sql"\s+>\s+"\$evidence_file"\s*$',
        rehearse_body,
    )
    pg_restore_position = restore_match.start() if restore_match else -1
    evidence_position = evidence_command_match.start() if evidence_command_match else -1
    evidence_restored_only = (
        rehearse_body.count("database-evidence.sql") == 1
        and evidence_command_match is not None
        and pg_restore_position >= 0
        and evidence_position > pg_restore_position
    )
    runtime_validation_positions = [
        match.start()
        for match in re.finditer(
            r'(?m)^\s*wsr_validate_restore_runtime\s*$',
            rehearse_body,
        )
    ]
    restore_network_none = (
        restore_network_none
        and has_all(
            restore_runtime_validation_body,
            (".NetworkSettings.Networks", "networks[@]", '!= "none"'),
            ignore_case=False,
        )
        and len(runtime_validation_positions) == 3
        and runtime_validation_positions[-1] > pg_restore_position
        and runtime_validation_positions[-1] < evidence_position
    )

    database_evidence_parser_body = shell_function_body(
        production_code,
        "wsr_parse_database_evidence",
    )
    database_table_rows_body = shell_function_body(
        production_code,
        "wsr_database_evidence_table_rows",
    )
    restore_evidence_validation_body = shell_function_body(
        production_code,
        "wsr_validate_restore_evidence",
    )
    restore_manifest_body = shell_function_body(
        production_code,
        "wsr_write_restore_evidence_manifest",
    )
    dynamic_summary_bindings = {
        "restored_flyway_successful_count": "WSR_RESTORED_FLYWAY_SUCCESSFUL_COUNT",
        "restored_flyway_max_installed_rank": "WSR_RESTORED_FLYWAY_MAX_INSTALLED_RANK",
        "restored_analyst_calls": "WSR_RESTORED_ANALYST_CALLS",
        "restored_analyst_call_revisions": "WSR_RESTORED_ANALYST_CALL_REVISIONS",
        "restored_call_outcomes": "WSR_RESTORED_CALL_OUTCOMES",
    }
    manifest_uses_observed_summary = all(
        re.search(
            rf"printf\s+'{re.escape(field)}=%s\\n'\s+\"\${variable}\"",
            restore_manifest_body,
        )
        is not None
        for field, variable in dynamic_summary_bindings.items()
    )
    parser_binds_observed_summary = has_all(
        database_evidence_parser_body,
        (
            *dynamic_summary_bindings.values(),
            "flyway_row_count",
            "flyway_max",
            "analyst_calls_rows",
            "analyst_call_revisions_rows",
            "call_outcomes_rows",
            "wsr_database_evidence_table_rows",
            "analyst_calls",
            "analyst_call_revisions",
            "call_outcomes",
            "seen_singleton",
            "seen_flyway",
            "seen_metadata",
        ),
        ignore_case=False,
    )
    parser_requires_exact_table_rows = has_all(
        database_table_rows_body,
        ("public.$table_name", "NF == 3", "${#values[@]} != 1", "^[0-9]+$"),
        ignore_case=False,
    )
    rehearsal_parse_position = rehearse_body.find(
        'wsr_parse_database_evidence "$evidence_file"',
    )
    rehearsal_manifest_position = rehearse_body.find(
        "wsr_write_restore_evidence_manifest",
    )
    validation_parse_position = restore_evidence_validation_body.find(
        'wsr_parse_database_evidence "$evidence_file"',
    )
    validation_manifest_compare_position = restore_evidence_validation_body.find(
        "${!observed_name}",
    )
    evidence_dynamic_summary = (
        manifest_uses_observed_summary
        and parser_binds_observed_summary
        and parser_requires_exact_table_rows
        and evidence_position >= 0
        and rehearsal_parse_position > evidence_position
        and rehearsal_manifest_position > rehearsal_parse_position
        and validation_parse_position >= 0
        and validation_manifest_compare_position > validation_parse_position
        and has_all(
            restore_evidence_validation_body,
            (*dynamic_summary_bindings.keys(), "manifest_key", "observed_name"),
            ignore_case=False,
        )
    )

    cleanup_body = shell_function_body(production_code, "wsr_cleanup_restore_resources")
    cleanup_container_guard = re.search(
        r'if\s+\[\[\s*"\$actual_id"\s*!=\s*"\$WSR_RESTORE_CONTAINER_ID"\s*\|\|\s*'
        r'"\$owner_label"\s*!=\s*"\$WSR_RESTORE_OWNER_TOKEN"\s*\|\|\s*'
        r'"\$scope_label"\s*!=\s*"\$WSR_RECOVERY_SCOPE_VALUE"\s*\]\];\s*then',
        cleanup_body,
    )
    cleanup_volume_guard = re.search(
        r'if\s+\[\[\s*"\$owner_label"\s*!=\s*"\$WSR_RESTORE_OWNER_TOKEN"\s*\|\|\s*'
        r'"\$scope_label"\s*!=\s*"\$WSR_RECOVERY_SCOPE_VALUE"\s*\]\];\s*then',
        cleanup_body,
    )
    cleanup_exact_ownership = (
        cleanup_container_guard is not None
        and cleanup_volume_guard is not None
        and has_all(
            cleanup_body,
            ("container rm --force --volumes --", "volume rm --"),
            ignore_case=False,
        )
    )
    restore_capacity_body = shell_function_body(production_code, "wsr_verify_space_for_restore")
    preflight_body = shell_function_body(production_code, "wsr_action_preflight")
    status_body = shell_function_body(production_code, "wsr_action_status")
    restore_capacity_position = rehearse_body.find("wsr_verify_space_for_restore")
    volume_create_position = volume_create_match.start() if volume_create_match else -1
    restore_capacity_checked = (
        has_all(
            restore_capacity_body,
            ("DockerRootDir", "df -Pk", "database_bytes * 3", "2 * 1024 * 1024 * 1024"),
            ignore_case=False,
        )
        and restore_capacity_position >= 0
        and volume_create_position > restore_capacity_position
        and "wsr_verify_space_for_restore" in preflight_body
        and has_all(
            status_body,
            ("STORE_IDENTITY_SHA256", "CAPACITY_STATUS", "wsr_print_capacity_evidence"),
            ignore_case=False,
        )
        and "database_bytes" in shell_function_body(common_code, "wsr_write_backup_manifest")
        and "database_bytes" in shell_function_body(common_code, "wsr_load_backup_manifest")
    )
    retention_assignments = {
        match.group("period").lower(): int(match.group("count"))
        for match in re.finditer(
            r"(?m)^readonly\s+WSR_RETENTION_(?P<period>[A-Z]+)=(?P<count>[0-9]+)$",
            common_code,
        )
    }
    retention = (
        retention_assignments.get("daily", -1),
        retention_assignments.get("weekly", -1),
        retention_assignments.get("monthly", -1),
    )
    if set(retention_assignments) != {"daily", "weekly", "monthly"}:
        retention = (-1, -1, -1)
    destructive_pattern = re.compile(
        r"(?im)(?:docker\s+(?:system|volume|image|container)\s+prune|"
        r"docker\s+compose[^\n]*(?:down\s+--volumes|down\s+-v)|"
        r"(?<!container\s)(?<!volume\s)\brm\b|"
        r"\brm\s+(?:--recursive\b|-[A-Za-z]*[rR][A-Za-z]*\b)|"
        r"\brm\b[^\n]*(?:\*|\?)|\bfind\b[^\n]*-delete\b|"
        r"(?:^|[;&|]\s*)(?:unlink|shred|truncate)\b)"
    )
    no_destructive_commands = destructive_pattern.search(scripts_code) is None
    device_mutation_pattern = re.compile(
        r"(?im)^\s*(?:sudo\s+)?(?:mkfs(?:\.[a-z0-9]+)?|mount|umount|cryptsetup|"
        r"parted|fdisk|sfdisk|wipefs)\b"
    )
    no_device_mutation = device_mutation_pattern.search(scripts_code) is None
    clean_checkout_pattern = re.compile(
        r"(?im)(?:git\s+(?:status|diff)\b|git\s+rev-parse\s+HEAD)"
    )
    no_clean_checkout_dependency = clean_checkout_pattern.search(scripts_code) is None
    retention_sort_body = shell_function_body(production_code, "wsr_sort_retention_ids")
    retention_selection_body = shell_function_body(
        production_code,
        "wsr_emit_retention_selection",
    )
    retention_action_body = shell_function_body(production_code, "wsr_action_retention_plan")
    retention_live_policy = (
        "LC_ALL=C sort -r" in retention_sort_body
        and "wsr_sort_retention_ids" in retention_action_body
        and "wsr_emit_retention_selection" in retention_action_body
        and has_all(
            retention_selection_body,
            (
                "daily_count < WSR_RETENTION_DAILY",
                "weekly_count < WSR_RETENTION_WEEKLY",
                "monthly_count < WSR_RETENTION_MONTHLY",
                'candidate" == "$image_evidence_ready_id',
                "CANDIDATE_ONLY",
                "RETENTION_ESTIMATED_KEEP_BYTES",
                "RETENTION_ESTIMATED_CANDIDATE_BYTES",
            ),
            ignore_case=False,
        )
    )
    retention_mutation_pattern = re.compile(
        r"(?im)^\s*(?:(?:command|env)\s+)*(?:rm|mv|chmod|install|unlink|shred|truncate)\b|"
        r"\bfind\b[^\n]*-delete\b|"
        r"\b(?:wsr_docker|docker)\s+(?:container|volume)\s+rm\b"
    )
    retention_read_only = (
        "retention-plan" in production_code
        and retention_live_policy
        and retention_mutation_pattern.search(
            "\n".join((retention_sort_body, retention_selection_body, retention_action_body))
        ) is None
        and has_all(
            production_code,
            (
                "RETENTION_STORE_IDENTITY_SHA256",
                "RETENTION_INPUT_MANIFEST",
                "RETENTION_NEWEST_IMAGE_EVIDENCE_READY",
                "image_evidence_ready_id",
                "image-evidence-ready",
            ),
            ignore_case=False,
        )
        and (
            has_all(production_code, ("read-only", "plan"))
            or has_all(production_code, ("KEEP", "CANDIDATE"), ignore_case=False)
        )
        and no_destructive_commands
        and not re.search(
            r"(?i)\b(?:retention[-_ ]?(?:apply|delete)|apply[-_ ]?retention|--apply)\b",
            production_code,
        )
    )
    production_restore_pattern = re.compile(
        r"(?is)pg_restore.{0,1000}(?:wall-street-receipts-home_postgres-data|postgres-data)"
    )
    no_production_restore = (
        "restore" not in actions
        and "rollback" not in actions
        and production_restore_pattern.search(production_code) is None
    )

    git_derivation = shell_function_body(common_code, "wsr_derive_git_sha_from_images")
    rollback_git_sha = has_all(
        git_derivation,
        (
            "WSR_API_IMAGE_REVISION",
            "WSR_WEB_IMAGE_REVISION",
            "WSR_CADDY_PRODUCTION_IMAGE_REVISION",
        ),
        ignore_case=False,
    )
    rollback_image_ids = has_all(
        scripts_code, ("api", "web", "caddy-production", "postgres", "image", "id")
    )
    rollback_revisions = (
        "org.opencontainers.image.revision" in scripts_code
        and production_code.count("changed during capture") == 3
        and "expected_git_sha" in common_code
    )
    rollback_flyway = has_all(scripts_code, ("rollback", "flyway"))
    backup_manifest_body = shell_function_body(common_code, "wsr_write_backup_manifest")
    manifest_bodies = backup_manifest_body + restore_manifest_body
    risky_manifest_capture = re.search(
        r"(?im)(?:docker\s+inspect[^\n]*\.Config\.Env|cat\s+[^\n]*(?:\.env\.production|postgres_password)|"
        r"cp\s+[^\n]*(?:\.env\.production|/data/caddy))",
        manifest_bodies,
    )
    rollback_excludes_secrets = (
        bool(backup_manifest_body and restore_manifest_body)
        and risky_manifest_capture is None
        and not re.search(
            r"(?i)(?:(?<!no-)password|secret|private_key|caddy[-_](?:data|config))",
            manifest_bodies,
        )
    )

    forbidden_dependency = re.compile(
        r"(?im)(?:WSR_DOMAIN|WSR_ACME_EMAIL|^\s*(?:dig|nslookup)\s+|"
        r"getaddrinfo|(?<!recovery-)preflight\.sh\s+--mode\s+(?:contract|publish))"
    )
    no_dns_acme_dependency = forbidden_dependency.search(scripts_code) is None

    return RecoveryContract(
        config_keys=config_keys,
        placeholder_keys=placeholders,
        actions=actions,
        config_allowlist_enforced=config_allowlist_enforced,
        config_path_fixed=config_path_fixed,
        encryption_modes=encryption_modes,
        arbitrary_arguments_rejected=arbitrary_arguments_rejected,
        exact_mount_required=exact_mount_required,
        missing_mount_fails=missing_mount_fails,
        filesystem_uuid_required=filesystem_uuid_required,
        filesystems=discovered_filesystems,
        mount_options=discovered_options,
        physical_leaf_disjoint=physical_leaf_disjoint,
        nested_mounts_rejected=nested_mounts_rejected,
        store_identity_bound=store_identity_bound,
        source_fixed_project=source_fixed_project,
        source_compose_labels=source_compose_labels,
        source_healthy=source_healthy,
        source_single_postgres=source_single_postgres,
        source_named_volume=source_named_volume,
        source_no_ports=source_no_ports,
        dump_custom=dump_custom,
        dump_no_owner=dump_no_owner,
        dump_no_privileges=dump_no_privileges,
        dump_host_partial=dump_host_partial,
        archive_checksum=archive_checksum,
        archive_list_validation=archive_list_validation,
        strict_manifest=strict_manifest,
        fsync_before_publish=fsync_before_publish,
        atomic_no_clobber_publish=atomic_no_clobber_publish,
        no_backup_bind_mount=no_backup_bind_mount,
        restore_checksum_first=restore_checksum_first,
        restore_label_owned=restore_label_owned,
        restore_fresh_volume=restore_fresh_volume,
        restore_fresh_container=restore_fresh_container,
        restore_network_none=restore_network_none,
        restore_no_ports=restore_no_ports,
        restore_single_transaction=restore_single_transaction,
        restore_exit_on_error=restore_exit_on_error,
        restore_no_owner=restore_no_owner,
        restore_no_privileges=restore_no_privileges,
        evidence_read_only=evidence_read_only,
        evidence_flyway=evidence_flyway,
        evidence_table_inventory=evidence_table_inventory,
        evidence_restored_only=evidence_restored_only,
        evidence_dynamic_summary=evidence_dynamic_summary,
        cleanup_exact_ownership=cleanup_exact_ownership,
        restore_capacity_checked=restore_capacity_checked,
        retention=retention,
        retention_read_only=retention_read_only,
        no_production_restore=no_production_restore,
        no_destructive_commands=no_destructive_commands,
        no_device_mutation=no_device_mutation,
        no_clean_checkout_dependency=no_clean_checkout_dependency,
        rollback_git_sha=rollback_git_sha,
        rollback_image_ids=rollback_image_ids,
        rollback_revisions=rollback_revisions,
        rollback_flyway=rollback_flyway,
        rollback_excludes_secrets=rollback_excludes_secrets,
        no_dns_acme_dependency=no_dns_acme_dependency,
        pending_backup_device="PENDING_BACKUP_DEVICE" in scripts_code,
        pending_offsite_copy="PENDING_OFFSITE_COPY" in scripts_code,
    )


def valid_fixture() -> RecoveryContract:
    """Return a tool-independent valid contract used only for mutation self-tests."""

    return RecoveryContract(
        config_keys=CONFIG_KEYS,
        placeholder_keys=PLACEHOLDER_KEYS,
        actions=ACTIONS,
        config_allowlist_enforced=True,
        config_path_fixed=True,
        encryption_modes=ENCRYPTION_MODES,
        arbitrary_arguments_rejected=True,
        exact_mount_required=True,
        missing_mount_fails=True,
        filesystem_uuid_required=True,
        filesystems=FILESYSTEMS,
        mount_options=MOUNT_OPTIONS,
        physical_leaf_disjoint=True,
        nested_mounts_rejected=True,
        store_identity_bound=True,
        source_fixed_project=True,
        source_compose_labels=True,
        source_healthy=True,
        source_single_postgres=True,
        source_named_volume=True,
        source_no_ports=True,
        dump_custom=True,
        dump_no_owner=True,
        dump_no_privileges=True,
        dump_host_partial=True,
        archive_checksum=True,
        archive_list_validation=True,
        strict_manifest=True,
        fsync_before_publish=True,
        atomic_no_clobber_publish=True,
        no_backup_bind_mount=True,
        restore_checksum_first=True,
        restore_label_owned=True,
        restore_fresh_volume=True,
        restore_fresh_container=True,
        restore_network_none=True,
        restore_no_ports=True,
        restore_single_transaction=True,
        restore_exit_on_error=True,
        restore_no_owner=True,
        restore_no_privileges=True,
        evidence_read_only=True,
        evidence_flyway=True,
        evidence_table_inventory=True,
        evidence_restored_only=True,
        evidence_dynamic_summary=True,
        cleanup_exact_ownership=True,
        restore_capacity_checked=True,
        retention=RETENTION,
        retention_read_only=True,
        no_production_restore=True,
        no_destructive_commands=True,
        no_device_mutation=True,
        no_clean_checkout_dependency=True,
        rollback_git_sha=True,
        rollback_image_ids=True,
        rollback_revisions=True,
        rollback_flyway=True,
        rollback_excludes_secrets=True,
        no_dns_acme_dependency=True,
        pending_backup_device=True,
        pending_offsite_copy=True,
    )


def mutation_matrix() -> list[tuple[str, Callable[[RecoveryContract], RecoveryContract]]]:
    return [
        ("extra config key", lambda value: replace(value, config_keys=value.config_keys | {"WSR_DOMAIN"})),
        ("missing UUID placeholder", lambda value: replace(value, placeholder_keys=frozenset())),
        ("placeholder encryption choice", lambda value: replace(value, placeholder_keys=value.placeholder_keys | {"WSR_BACKUP_ENCRYPTION"})),
        ("extra production action", lambda value: replace(value, actions=value.actions | {"restore"})),
        ("config allowlist bypass", lambda value: replace(value, config_allowlist_enforced=False)),
        ("caller-selected config path", lambda value: replace(value, config_path_fixed=False)),
        ("unapproved encryption mode", lambda value: replace(value, encryption_modes=value.encryption_modes | {"plain"})),
        ("arbitrary action arguments", lambda value: replace(value, arbitrary_arguments_rejected=False)),
        ("non-exact mount", lambda value: replace(value, exact_mount_required=False)),
        ("missing mount accepted", lambda value: replace(value, missing_mount_fails=False)),
        ("UUID binding removed", lambda value: replace(value, filesystem_uuid_required=False)),
        ("filesystem allowlist widened", lambda value: replace(value, filesystems=value.filesystems | {"btrfs"})),
        ("noexec removed", lambda value: replace(value, mount_options=value.mount_options - {"noexec"})),
        ("same physical disk accepted", lambda value: replace(value, physical_leaf_disjoint=False)),
        ("nested mount escape", lambda value: replace(value, nested_mounts_rejected=False)),
        ("store identity unbound", lambda value: replace(value, store_identity_bound=False)),
        ("production project changed", lambda value: replace(value, source_fixed_project=False)),
        ("Compose label binding removed", lambda value: replace(value, source_compose_labels=False)),
        ("unhealthy source accepted", lambda value: replace(value, source_healthy=False)),
        ("multiple source databases accepted", lambda value: replace(value, source_single_postgres=False)),
        ("source volume changed", lambda value: replace(value, source_named_volume=False)),
        ("source port published", lambda value: replace(value, source_no_ports=False)),
        ("plain SQL dump", lambda value: replace(value, dump_custom=False)),
        ("dump owner retained", lambda value: replace(value, dump_no_owner=False)),
        ("dump privileges retained", lambda value: replace(value, dump_no_privileges=False)),
        ("direct final dump write", lambda value: replace(value, dump_host_partial=False)),
        ("container-mounted backup path", lambda value: replace(value, no_backup_bind_mount=False)),
        ("checksum removed", lambda value: replace(value, archive_checksum=False)),
        ("archive listing removed", lambda value: replace(value, archive_list_validation=False)),
        ("manifest field checks removed", lambda value: replace(value, strict_manifest=False)),
        ("publication before fsync", lambda value: replace(value, fsync_before_publish=False)),
        ("clobbering publication", lambda value: replace(value, atomic_no_clobber_publish=False)),
        ("target before checksum", lambda value: replace(value, restore_checksum_first=False)),
        ("unlabelled restore resources", lambda value: replace(value, restore_label_owned=False)),
        ("production volume restore", lambda value: replace(value, restore_fresh_volume=False)),
        ("existing restore container reused", lambda value: replace(value, restore_fresh_container=False)),
        ("restore network enabled", lambda value: replace(value, restore_network_none=False)),
        ("restore port published", lambda value: replace(value, restore_no_ports=False)),
        ("non-atomic restore", lambda value: replace(value, restore_single_transaction=False)),
        ("restore continues after error", lambda value: replace(value, restore_exit_on_error=False)),
        ("restore owner applied", lambda value: replace(value, restore_no_owner=False)),
        ("restore ACL applied", lambda value: replace(value, restore_no_privileges=False)),
        ("writable evidence SQL", lambda value: replace(value, evidence_read_only=False)),
        ("Flyway evidence removed", lambda value: replace(value, evidence_flyway=False)),
        ("table inventory removed", lambda value: replace(value, evidence_table_inventory=False)),
        ("source-side evidence", lambda value: replace(value, evidence_restored_only=False)),
        ("fixture-count restore evidence", lambda value: replace(value, evidence_dynamic_summary=False)),
        ("broad cleanup", lambda value: replace(value, cleanup_exact_ownership=False)),
        ("restore capacity unchecked", lambda value: replace(value, restore_capacity_checked=False)),
        ("retention weakened", lambda value: replace(value, retention=(7, 4, 6))),
        ("retention deletion", lambda value: replace(value, retention_read_only=False)),
        ("production restore exposed", lambda value: replace(value, no_production_restore=False)),
        ("volume prune", lambda value: replace(value, no_destructive_commands=False)),
        ("device formatting", lambda value: replace(value, no_device_mutation=False)),
        ("clean HEAD dependency", lambda value: replace(value, no_clean_checkout_dependency=False)),
        ("rollback Git SHA removed", lambda value: replace(value, rollback_git_sha=False)),
        ("rollback image ID removed", lambda value: replace(value, rollback_image_ids=False)),
        ("rollback OCI revision removed", lambda value: replace(value, rollback_revisions=False)),
        ("rollback Flyway evidence removed", lambda value: replace(value, rollback_flyway=False)),
        ("rollback secret leak", lambda value: replace(value, rollback_excludes_secrets=False)),
        ("DNS dependency", lambda value: replace(value, no_dns_acme_dependency=False)),
        ("hardware readiness overclaim", lambda value: replace(value, pending_backup_device=False)),
        ("off-site readiness overclaim", lambda value: replace(value, pending_offsite_copy=False)),
    ]


def validate_mutation_matrix() -> int:
    baseline = valid_fixture()
    validate_contract(baseline)
    mutations = mutation_matrix()
    for label, mutate in mutations:
        candidate = mutate(baseline)
        try:
            validate_contract(candidate)
        except ContractError:
            continue
        raise ContractError(f"Negative self-test was accepted: {label}")
    return len(mutations)


def replace_once(source: str, old: str, new: str, label: str) -> str:
    require(old in source, f"Source mutation fixture drifted: {label}")
    return source.replace(old, new, 1)


def validate_source_mutations(
    config_source: str,
    evidence_source: str,
    common_source: str,
    preflight_source: str,
    production_source: str,
    schema_source: str,
) -> int:
    """Mutate the real sources and prove token-preserving safety bypasses fail."""

    mutations: list[tuple[str, str, str]] = []
    mutations.append(
        (
            "mount options loop bypassed",
            replace_once(
                common_source,
                "for required_option in rw nodev nosuid noexec; do",
                "for required_option in; do\n    : 'rw nodev nosuid noexec'",
                "mount options loop",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "physical leaf equality bypassed",
            replace_once(
                common_source,
                'if [[ "$backup_disk" == "$docker_disk" ]]; then',
                'if false; then\n        : "$backup_disk $docker_disk physical leaf disjoint"',
                "physical leaf equality",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "nested mount guard disabled",
            replace_once(
                common_source,
                "wsr_require_backup_filesystem_path() {",
                "wsr_disabled_backup_filesystem_path() {",
                "nested mount helper",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "restore capacity guard disabled",
            common_source,
            replace_once(
                production_source,
                "wsr_verify_space_for_restore() {",
                "wsr_disabled_space_for_restore() {",
                "restore capacity helper",
            ),
        )
    )
    mutations.append(
        (
            "recursive backup deletion added",
            common_source,
            production_source
            + '\nwsr_forbidden_delete() { rm -r -- "$WSR_BACKUPS_ROOT"; }\n',
        )
    )
    dump_flags_removed = replace_once(
        production_source,
        "      --no-owner \\\n",
        "",
        "live pg_dump no-owner",
    )
    dump_flags_removed = replace_once(
        dump_flags_removed,
        "      --no-privileges \\\n",
        "",
        "live pg_dump no-privileges",
    )
    dump_flags_removed += "\nwsr_dead_dump_tokens() { : '--no-owner --no-privileges'; }\n"
    mutations.append(("live pg_dump owner and ACL flags removed", common_source, dump_flags_removed))
    mutations.append(
        (
            "filesystem type guard disabled",
            replace_once(
                common_source,
                'if [[ "$filesystem_type" != "ext4" && "$filesystem_type" != "xfs" ]]; then',
                'if false; then\n    : "$filesystem_type ext4 xfs"',
                "filesystem type guard",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "source health guard disabled",
            replace_once(
                common_source,
                'if [[ "$running" != "true" || "$health" != "healthy" ]]; then',
                'if false; then\n    : "$running $health true healthy"',
                "source health guard",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "live archive checksum guard disabled",
            replace_once(
                common_source,
                'if ! (cd -- "$artifact" && sha256sum --check --strict --status database.dump.sha256); then',
                'if false; then\n    : \'cd "$artifact" sha256sum --check --strict --status database.dump.sha256\'',
                "archive checksum guard",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "cleanup ownership comparison disabled",
            common_source,
            replace_once(
                production_source,
                'if [[ "$actual_id" != "$WSR_RESTORE_CONTAINER_ID" ||',
                'if false && [[ "$actual_id" != "$WSR_RESTORE_CONTAINER_ID" ||',
                "cleanup ownership comparison",
            ),
        )
    )
    mutations.append(
        (
            "retention sort inverted",
            common_source,
            replace_once(
                production_source,
                "printf '%s\\n' \"$@\" | LC_ALL=C sort -r",
                "printf '%s\\n' \"$@\" | LC_ALL=C sort",
                "retention newest-first sort",
            ),
        )
    )
    mutations.append(
        (
            "retention selector disabled",
            common_source,
            replace_once(
                production_source,
                "wsr_emit_retention_selection() {",
                "wsr_disabled_retention_selection() {",
                "retention selection helper",
            ),
        )
    )
    mutations.append(
        (
            "restore volume ownership validator disabled",
            common_source,
            replace_once(
                production_source,
                "wsr_validate_restore_volume() {",
                "wsr_disabled_restore_volume() {",
                "restore volume validator",
            ),
        )
    )
    mutations.append(
        (
            "restore runtime ownership validator disabled",
            common_source,
            replace_once(
                production_source,
                "wsr_validate_restore_runtime() {",
                "wsr_disabled_restore_runtime() {",
                "restore runtime validator",
            ),
        )
    )
    mutations.append(
        (
            "live restore network-set observation disabled",
            common_source,
            replace_once(
                production_source,
                ".NetworkSettings.Networks",
                ".HostConfig.NetworkMode",
                "live restore network-set observation",
            ),
        )
    )
    mutations.append(
        (
            "anonymous restore volumes excluded from cleanup",
            common_source,
            replace_once(
                production_source,
                "container rm --force --volumes --",
                "container rm --force --",
                "anonymous restore-volume cleanup",
            ),
        )
    )
    mutations.append(
        (
            "block topology allowlist disabled",
            replace_once(
                common_source,
                "wsr_validate_block_topology_rows() {",
                "wsr_disabled_block_topology_rows() {",
                "block topology validator",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "block topology validator call disabled",
            replace_once(
                common_source,
                '  wsr_validate_block_topology_rows "$path" "$topology" rows || return 1',
                '  : \'wsr_validate_block_topology_rows "$path" "$topology" rows || return 1\'',
                "block topology validator call",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "leaf serial identity observation disabled",
            replace_once(
                common_source,
                '        lsblk --nodeps --noheadings --raw --output SERIAL "$name"',
                '        : \'lsblk --nodeps --noheadings --raw --output SERIAL "$name"\'',
                "leaf serial identity observation",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "leaf transport identity observation disabled",
            replace_once(
                common_source,
                '        lsblk --nodeps --noheadings --raw --output TRAN "$name"',
                '        : \'lsblk --nodeps --noheadings --raw --output TRAN "$name"\'',
                "leaf transport identity observation",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "retention exact manifest deletion added",
            common_source,
            replace_once(
                production_source,
                'for candidate in "$@"; do\n    stamp="${candidate%%-*}"',
                'for candidate in "$@"; do\n    rm -- "$WSR_BACKUPS_ROOT/$candidate/manifest"\n    stamp="${candidate%%-*}"',
                "retention exact-file deletion",
            ),
        )
    )
    mutations.append(
        (
            "live dump options omitted from manifest",
            replace_once(
                common_source,
                "    printf 'pg_dump_options=format-custom+compress-6+no-owner+no-privileges+no-password\\n'\n",
                "    : 'pg_dump_options=format-custom+compress-6+no-owner+no-privileges+no-password'\n",
                "pg_dump options manifest field",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "fsync helper disabled",
            replace_once(
                common_source,
                '  sync -f -- "$1"',
                '  : \'sync -f -- "$1"\'',
                "fsync helper command",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "backup staging directory fsync disabled",
            common_source,
            replace_once(
                production_source,
                '  wsr_fsync_path "$WSR_PARTIAL_PATH"',
                '  : \'wsr_fsync_path "$WSR_PARTIAL_PATH"\'',
                "backup staging directory fsync",
            ),
        )
    )
    mutations.append(
        (
            "backup publication parent fsync disabled",
            common_source,
            replace_once(
                production_source,
                '  wsr_fsync_path "$WSR_BACKUPS_ROOT"',
                '  : \'wsr_fsync_path "$WSR_BACKUPS_ROOT"\'',
                "backup publication parent fsync",
            ),
        )
    )
    mutations.append(
        (
            "unique UTC-second allocation disabled",
            common_source,
            replace_once(
                production_source,
                "wsr_allocate_unique_utc_staging_directory() {",
                "wsr_disabled_unique_utc_staging_directory() {",
                "unique UTC-second allocation helper",
            ),
        )
    )
    mutations.append(
        (
            "backup unique UTC-second call disabled",
            common_source,
            replace_once(
                production_source,
                '  wsr_allocate_unique_utc_staging_directory "$WSR_BACKUPS_ROOT"',
                '  : \'wsr_allocate_unique_utc_staging_directory "$WSR_BACKUPS_ROOT"\'',
                "backup unique UTC-second allocation call",
            ),
        )
    )
    mutations.append(
        (
            "runtime config allowlist guard disabled",
            replace_once(
                common_source,
                'if [[ ! "$key" =~ $allowed ]]; then',
                'if false; then\n      : "$key $allowed unapproved"',
                "runtime config allowlist guard",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "source PostgreSQL port guard disabled",
            replace_once(
                common_source,
                'if [[ "$port_binding_count" != "0" || "$network_mode" == "host" ]]; then',
                'if false; then\n    : "$port_binding_count $network_mode PortBindings host"',
                "source PostgreSQL port guard",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "source PostgreSQL exact mount set guard disabled",
            replace_once(
                common_source,
                '  if ((${#data_mounts[@]} != 2)) ||',
                '  if false && ((${#data_mounts[@]} != 2)) ||',
                "source PostgreSQL exact mount set",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "source PostgreSQL exact count guard disabled",
            replace_once(
                common_source,
                '  if ((${#container_ids[@]} != 1)); then',
                '  if false; then\n    : "${#container_ids[@]} exactly one postgres"',
                "source PostgreSQL exact count guard",
            ),
            production_source,
        )
    )
    mutations.append(
        (
            "restore resource allocated before bundle validation",
            common_source,
            replace_once(
                production_source,
                '  }\n  wsr_validate_completed_backup "$backup_id"\n  WSR_DATABASE_BYTES=',
                '  }\n  wsr_docker container create forbidden-before-checksum\n  wsr_validate_completed_backup "$backup_id"\n  WSR_DATABASE_BYTES=',
                "restore checksum-before-allocation order",
            ),
        )
    )
    mutations.append(
        (
            "pg_restore redirected to production container",
            common_source,
            replace_once(
                production_source,
                '  wsr_docker exec -i "$WSR_RESTORE_CONTAINER_ID" \\\n    pg_restore \\',
                '  : \'$WSR_RESTORE_CONTAINER_ID restore target\'\n  wsr_docker exec -i "$WSR_PRODUCTION_POSTGRES_ID" \\\n    pg_restore \\',
                "pg_restore exact rehearsal target",
            ),
        )
    )
    mutations.append(
        (
            "restore publishes all exposed ports",
            common_source,
            replace_once(
                production_source,
                '      --network none \\\n',
                '      --network none \\\n      -P \\\n',
                "restore publish-all-ports flag",
            ),
        )
    )
    list_validation_disabled = production_source.replace(
        "pg_restore --list",
        "pg_restore --help",
    )
    require(list_validation_disabled != production_source, "Source mutation fixture drifted: pg_restore list")
    list_validation_disabled += "\nwsr_dead_list_marker() { : 'pg_restore --list'; }\n"
    mutations.append(
        (
            "live pg_restore inventory validation disabled",
            common_source,
            list_validation_disabled,
        )
    )
    mutations.append(
        (
            "database evidence redirected to production container",
            common_source,
            replace_once(
                production_source,
                '  wsr_docker exec -i "$WSR_RESTORE_CONTAINER_ID" \\\n    psql -X -q -A -t --no-password --username="$WSR_RECOVERY_DATABASE_USER" \\\n      --dbname="$WSR_RECOVERY_DATABASE" \\\n      < "$script_dir/database-evidence.sql" > "$evidence_file"',
                '  : \'$WSR_RESTORE_CONTAINER_ID restore container\'\n  wsr_docker exec -i "$WSR_PRODUCTION_POSTGRES_ID" \\\n    psql -X -q -A -t --no-password --username="$WSR_RECOVERY_DATABASE_USER" \\\n      --dbname="$WSR_RECOVERY_DATABASE" \\\n      < "$script_dir/database-evidence.sql" > "$evidence_file"',
                "database evidence exact rehearsal target",
            ),
        )
    )
    mutations.append(
        (
            "observed database evidence parser disabled",
            common_source,
            replace_once(
                production_source,
                "wsr_parse_database_evidence() {",
                "wsr_disabled_parse_database_evidence() {",
                "observed database evidence parser",
            ),
        )
    )
    mutations.append(
        (
            "restore manifest uses fixture analyst-call count",
            common_source,
            replace_once(
                production_source,
                "    printf 'restored_analyst_calls=%s\\n' \"$WSR_RESTORED_ANALYST_CALLS\"",
                "    printf 'restored_analyst_calls=3\\n'",
                "dynamic restored analyst-call count",
            ),
        )
    )
    network_guard_disabled = replace_once(
        production_source,
        '      --network none \\\n',
        '      --network bridge \\\n      : \'--network none\' \\\n',
        "restore network mode",
    )
    network_guard_disabled = replace_once(
        network_guard_disabled,
        '"$network_mode" != "none" || "$port_binding_count" != "0"',
        '"$network_mode" != "bridge" || "$port_binding_count" != "0" || -n "--network none"',
        "restore runtime network guard",
    )
    mutations.append(("restore network isolation disabled", common_source, network_guard_disabled))

    for label, mutated_common, mutated_production in mutations:
        try:
            candidate = source_contract(
                config_source,
                evidence_source,
                mutated_common,
                preflight_source,
                mutated_production,
                schema_source,
            )
            validate_contract(candidate)
        except ContractError:
            continue
        raise ContractError(f"Real-source negative mutation was accepted: {label}")
    evidence_mutation = replace_once(
        evidence_source,
        "BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;",
        "BEGIN;\nSELECT 'READ ONLY';",
        "database evidence read-only transaction",
    )
    try:
        candidate = source_contract(
            config_source,
            evidence_mutation,
            common_source,
            preflight_source,
            production_source,
            schema_source,
        )
        validate_contract(candidate)
    except ContractError:
        pass
    else:
        raise ContractError("Real-source negative mutation was accepted: evidence transaction writable")
    return len(mutations) + 1


def validate_local_rehearsal_contract(source: str) -> None:
    code = strip_full_line_comments(source)
    keys_body = powershell_function_body(code, "Get-RecoveryManifestKeys")
    writer_body = powershell_function_body(code, "Write-RecoveryKeyValueManifest")
    validator_body = powershell_function_body(code, "Assert-ExactRecoveryPointBundle")
    rejection_body = powershell_function_body(code, "Assert-RecoveryBundleRejected")
    rehearsal_body = powershell_function_body(code, "Invoke-RecoveryDatabaseRehearsal")
    required_members = (
        '"database.dump"',
        '"database.dump.sha256"',
        '"database.inventory"',
        '"manifest"',
    )
    require(
        all(member in validator_body for member in required_members)
        and "manifest.json" not in code
        and "archive-inventory.txt" not in code,
        "Local Docker rehearsal must use the production exact four-member bundle",
    )
    require(
        has_all(
            keys_body,
            (
                '"pg_dump_options"',
                '"archive_inventory_entries"',
                '"store_identity_sha256"',
                '"caddy_production_image_revision"',
            ),
            ignore_case=False,
        )
        and "Get-RecoveryManifestKeys" in writer_body
        and '($lines -join "`n") + "`n"' in writer_body
        and "UTF8Encoding]::new($false)" in writer_body,
        "Local Docker rehearsal lost the canonical production K/V manifest writer",
    )
    require(
        has_all(
            validator_body,
            (
                "Compare-Object",
                "ReparsePoint",
                '$member.LinkType -cne "HardLink"',
                "hasUtf8Bom",
                "manifestLines.Count -eq $requiredKeys.Count",
                "requiredKeys -ccontains $key",
                "-not $manifest.ContainsKey($key)",
                "ExpectedManifest.Keys",
                "ParseExact",
                "completedUtc -ge $startedUtc",
                "archive_bytes",
                "archive_sha256",
                "archive_inventory_bytes",
                "archive_inventory_entries",
                "archive_inventory_sha256",
                "StructuralEqualityComparer",
                "database.dump`n",
                "format-custom+compress-6+no-owner+no-privileges+no-password",
            ),
            ignore_case=False,
        )
        and "^[a-z][a-z0-9_]*$" not in validator_body
        and "(?<key>[a-z][a-z0-9_]*)=(?<value>[A-Za-z0-9._:/+-]+)" in validator_body,
        "Local Docker rehearsal lost strict manifest/member/checksum/inventory validation",
    )
    require(
        bool(rejection_body)
        and rehearsal_body.count("Assert-RecoveryBundleRejected") == 9
        and has_all(
            rehearsal_body,
            (
                'CaseName "missing exact member"',
                'CaseName "extra exact member"',
                'CaseName "hardlinked exact member"',
                '$hardlinkMember.LinkType -ceq "HardLink"',
                'CaseName "missing required manifest key"',
                'CaseName "unknown manifest key"',
                'CaseName "duplicate manifest key"',
                'CaseName "checksum member mismatch"',
                'CaseName "inventory manifest mismatch"',
                'CaseName "dump manifest mismatch"',
            ),
            ignore_case=False,
        ),
        "Local Docker rehearsal lost exact bundle corruption rejection cases",
    )
    require(
        rehearsal_body.count("Assert-ExactRecoveryPointBundle") == 2
        and "$validatedManifest = Assert-ExactRecoveryPointBundle" in rehearsal_body
        and '$dumpPath = Join-Path $finalPath $validatedManifest["archive_file"]' in rehearsal_body
        and '$dumpSha256 = $validatedManifest["archive_sha256"]' in rehearsal_body
        and '$restorePostgresImageId = $validatedManifest["postgres_image_id"]' in rehearsal_body
        and rehearsal_body.count("$restorePostgresImageId") >= 3
        and '"--compress=6"' in rehearsal_body,
        "Local restore must consume the revalidated published manifest and exact dump options",
    )
    require(
        "rollback_ready" not in code
        and code.count("image_evidence_ready") == 1
        and code.count("compatible-exact-api-image-flyway-local-only") == 1
        and code.count("--wsr-release-schema-inventory") == 1
        and "inventory_version|1" in rehearsal_body
        and "flyway_version|11.7.2" in rehearsal_body
        and "evidence_version|2" in rehearsal_body
        and rehearsal_body.count('"psql", "-X", "-q", "-A", "-t",') == 1
        and "database_evidence_version = 2" in rehearsal_body
        and "PENDING_BACKUP_DEVICE" in rehearsal_body
        and "PENDING_OFFSITE_COPY" in rehearsal_body,
        "Local rehearsal may emit image evidence only after restore and must retain pending gates",
    )


def validate_local_rehearsal_mutations(source: str) -> int:
    mutations = (
        (
            "four-member validator removed",
            replace_once(
                source,
                "function Assert-ExactRecoveryPointBundle {",
                "function Assert-DisabledRecoveryPointBundle {",
                "local exact bundle validator",
            ),
        ),
        (
            "checksum member renamed",
            source.replace("database.dump.sha256", "database.dump.checksum"),
        ),
        (
            "published manifest dump hash bypassed",
            replace_once(
                source,
                '$dumpSha256 = $validatedManifest["archive_sha256"]',
                '$dumpSha256 = $manifest["archive_sha256"]',
                "validated manifest dump hash",
            ),
        ),
        (
            "published manifest image bypassed",
            replace_once(
                source,
                '$restorePostgresImageId = $validatedManifest["postgres_image_id"]',
                '$restorePostgresImageId = $manifest["postgres_image_id"]',
                "validated manifest image ID",
            ),
        ),
        (
            "corruption rejection helper removed",
            replace_once(
                source,
                "function Assert-RecoveryBundleRejected {",
                "function Assert-DisabledRecoveryBundleRejected {",
                "local bundle rejection helper",
            ),
        ),
        (
            "hardlink member accepted",
            replace_once(
                source,
                '$member.LinkType -cne "HardLink"',
                '$member.LinkType -ceq "HardLink"',
                "local hardlink member rejection",
            ),
        ),
        (
            "canonical database evidence psql mode removed",
            replace_once(
                source,
                '"psql", "-X", "-q", "-A", "-t",',
                '"psql",',
                "local canonical database evidence psql mode",
            ),
        ),
    )
    validate_local_rehearsal_contract(source)
    for label, candidate in mutations:
        try:
            validate_local_rehearsal_contract(candidate)
        except ContractError:
            continue
        raise ContractError(f"Local-rehearsal negative mutation was accepted: {label}")
    return len(mutations)


def read_required(path: Path) -> str:
    require(path.is_file(), f"Required recovery source is missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def validate_schema_compatibility_source(source: str, production_source: str) -> None:
    code = strip_full_line_comments(source)
    require(
        'source "$script_dir/schema-compatibility.sh"' in production_source,
        "Production recovery does not source the fixed schema-compatibility policy",
    )
    require(
        has_all(
            code,
            (
                "wsr_action_schema_check_latest",
                "compatible-exact-recorded-release",
                "restore-evidence-v2-unavailable",
                "image-git-resource-mismatch",
                "flyway-row-order-checksum-mismatch",
                "GIT_NO_LAZY_FETCH=1",
                "GIT_NO_REPLACE_OBJECTS=1",
                "--no-replace-objects",
                "cat-file",
                "ls-tree",
                "--network none",
                "--read-only",
                "--cap-drop ALL",
                "--pull never",
                "--memory 384m",
                "--memory-swap 384m",
                "--pids-limit",
                "--cpus 1.0",
                "--log-driver none",
                "timeout --signal=TERM --kill-after=5s",
                "head --bytes=",
                "no-new-privileges",
                "--wsr-release-schema-inventory",
                "PENDING_OFFSITE_COPY",
                "blocked-promotion-and-artifact-gates-not-implemented",
            ),
            ignore_case=False,
        ),
        "Exact Git/API-image/Flyway schema gate invariants are incomplete",
    )
    require(
        re.search(
            r"(?im)^\s*(?:git|wsr_schema_git)\s+(?:fetch|pull|checkout|switch|reset|worktree)\b",
            code,
        )
        is None,
        "Schema compatibility may not fetch or mutate a Git worktree",
    )
    require(
        re.search(r"(?i)\brollback[-_ ]?ready\b", code) is None,
        "Schema compatibility must not claim rollback readiness",
    )


def main() -> int:
    try:
        config_source = read_required(CONFIG)
        evidence_source = read_required(EVIDENCE_SQL)
        common_source = read_required(COMMON)
        preflight_source = read_required(PREFLIGHT)
        production_source = read_required(PRODUCTION)
        schema_source = read_required(SCHEMA_COMPATIBILITY)
        local_rehearsal_source = read_required(LOCAL_REHEARSAL)
        validate_schema_compatibility_source(schema_source, production_source)
        local_mutation_count = validate_local_rehearsal_mutations(local_rehearsal_source)
        contract = source_contract(
            config_source,
            evidence_source,
            common_source,
            preflight_source,
            production_source,
            schema_source,
        )
        validate_contract(contract)
        synthetic_mutation_count = validate_mutation_matrix()
        source_mutation_count = validate_source_mutations(
            config_source,
            evidence_source,
            common_source,
            preflight_source,
            production_source,
            schema_source,
        )
    except (ContractError, OSError, UnicodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(
        "PASS: ADR-047/ADR-048 backup, restore, retention, release-image evidence, "
        "exact Git/API-image/Flyway schema blocking, and "
        f"{synthetic_mutation_count + source_mutation_count + local_mutation_count}-case negative matrix are exact "
        f"({source_mutation_count} shell-source and {local_mutation_count} local-rehearsal mutations)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
