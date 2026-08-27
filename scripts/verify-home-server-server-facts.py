#!/usr/bin/env python3
"""Static and mutation guard for the ADR-051 server-fact collector.

This verifier never executes the collector and is therefore safe to run on
Windows as well as Linux.  The companion Bash verifier owns execution against
pure command doubles; neither verifier contacts a Docker daemon or a network.
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path
from typing import Iterable, Sequence


ROOT = Path(__file__).resolve().parents[1]
COLLECTOR = ROOT / "deploy" / "home-server" / "server-facts.sh"
FIXTURE_VERIFIER = ROOT / "scripts" / "verify-home-server-server-facts.sh"

# This is an exact-source review lock, not a substitute for the semantic
# diagnostics below. Bash permits too many equivalent command-position
# spellings to make a partial parser a security boundary. Any collector byte
# change therefore requires an explicit verifier review and digest update.
EXPECTED_COLLECTOR_SHA256 = "b03a1bc0ce114d09f38cb5ebb4bd9d6babf9eb3400583d3a89e0460eb6dbca30"
COLLECTOR_SOURCE_LIMIT = 64 * 1024

EXPECTED_EXTERNAL_COMMANDS = frozenset(
    {
        "timeout",
        "head",
        "env",
        "cat",
        "date",
        "uname",
        "getconf",
        "stat",
        "findmnt",
        "df",
        "docker",
        "ss",
        "systemctl",
    }
)

EXPECTED_INTERNAL_FUNCTIONS = frozenset(
    {
        "usage",
        "main",
        "validate_absolute_path",
        "sanitize_value",
        "set_fact",
        "tool_status",
        "bounded_capture",
        "trim_ascii_space",
        "single_line_value",
        "path_type",
        "kib_to_bytes",
        "filter_mount_options",
        "collect_path_facts",
        "collect_port_facts",
        "systemctl_fact",
        "collect_container_facts",
        "emit_report",
    }
)

ALLOWED_DIRECT_COMMAND_HEADS = frozenset(
    {
        # Shell grammar and special builtins.
        "case",
        "do",
        "done",
        "elif",
        "else",
        "esac",
        "fi",
        "for",
        "if",
        "then",
        "while",
        "break",
        "builtin",
        "command",
        "compgen",
        "continue",
        "declare",
        "exit",
        "export",
        "local",
        "printf",
        "read",
        "readonly",
        "return",
        "set",
        "shift",
        "true",
        "unset",
        # The sole absolute executable is the environment isolator. All other
        # external tools are exact argv passed to bounded_capture.
        "/usr/bin/env",
    }
    | EXPECTED_INTERNAL_FUNCTIONS
)

EXPECTED_BOUNDED_CALLS = frozenset(
    {
        "date -u +%Y-%m-%dT%H:%M:%SZ",
        'stat -c %F -- "$path"',
        'cat "$os_release_path"',
        "uname -r",
        "uname -m",
        "cat /proc/cpuinfo",
        "getconf _NPROCESSORS_ONLN",
        "cat /proc/meminfo",
        "docker --version",
        "docker compose version --short",
        'docker info --format "$docker_info_template"',
        'docker volume inspect --format "$volume_template" "$WSR_FACTS_COMPOSE_VOLUME"',
        'findmnt -rn --target "$path" -o TARGET',
        'findmnt -rn --target "$path" -o FSTYPE',
        'findmnt -rn --target "$path" -o OPTIONS',
        'findmnt -rn --target "$path" -o MAJ:MIN',
        'df -B1 --output=size,avail -- "$path"',
        'findmnt -rn --mountpoint "$backup_mount" -o TARGET',
        'ss -H -ltnp "sport = :$port"',
        'systemctl "$action" "$unit"',
        "systemctl is-system-running",
        "stat -c %F -- /run/reboot-required",
        (
            "docker ps -a --no-trunc --filter "
            '"label=com.docker.compose.project=$WSR_FACTS_COMPOSE_PROJECT" --filter '
            '"label=com.docker.compose.service=$compose_service" --format \'{{.ID}}\''
        ),
        'docker inspect --format "$inspect_template" "$container_id"',
    }
)

EXPECTED_FACT_KEYS: tuple[str, ...] = (
    "schema_version",
    "collector_name",
    "collection_mode",
    "collection_status",
    "collected_at_utc",
    "external_network_calls",
    "environment_files_read",
    "secret_contents_read",
    "address_values_reported",
    "sanitized_value_count",
    "tool_env_status",
    "tool_timeout_status",
    "tool_findmnt_status",
    "tool_df_status",
    "tool_docker_status",
    "tool_ss_status",
    "tool_systemctl_status",
    "os_status",
    "os_id",
    "os_version_id",
    "kernel_release",
    "architecture",
    "cpu_status",
    "cpu_model",
    "cpu_logical_count",
    "memory_status",
    "memory_total_bytes",
    "memory_available_bytes",
    "docker_socket_path",
    "docker_socket_status",
    "docker_cli_status",
    "docker_client_version",
    "docker_daemon_status",
    "docker_server_version",
    "docker_server_architecture",
    "docker_root_dir",
    "docker_storage_driver",
    "docker_cgroup_version",
    "docker_ownership_boundary",
    "docker_compose_status",
    "docker_compose_version",
    "compose_project_expected",
    "compose_volume_expected",
    "compose_volume_status",
    "compose_volume_name",
    "compose_volume_driver",
    "compose_volume_scope",
    "compose_volume_label_match",
    "compose_volume_mountpoint",
    "root_path",
    "root_path_status",
    "root_mount_status",
    "root_filesystem",
    "root_mount_options_safe",
    "root_device_major_minor",
    "root_capacity_bytes",
    "root_free_bytes",
    "control_path",
    "control_path_status",
    "control_mount_status",
    "control_filesystem",
    "control_mount_options_safe",
    "control_device_major_minor",
    "control_capacity_bytes",
    "control_free_bytes",
    "docker_root_path",
    "docker_root_path_status",
    "docker_root_mount_status",
    "docker_root_filesystem",
    "docker_root_mount_options_safe",
    "docker_root_device_major_minor",
    "docker_root_capacity_bytes",
    "docker_root_free_bytes",
    "compose_volume_path",
    "compose_volume_path_status",
    "compose_volume_mount_status",
    "compose_volume_filesystem",
    "compose_volume_mount_options_safe",
    "compose_volume_device_major_minor",
    "compose_volume_capacity_bytes",
    "compose_volume_free_bytes",
    "backup_input_status",
    "backup_path",
    "backup_path_status",
    "backup_mount_status",
    "backup_filesystem",
    "backup_mount_options_safe",
    "backup_mount_options_omitted_count",
    "backup_device_major_minor",
    "backup_capacity_bytes",
    "backup_free_bytes",
    "backup_exact_mountpoint",
    "port_80_status",
    "port_80_listener_count",
    "port_80_records_truncated",
    "port_80_bind_scope",
    "port_80_owner_metadata",
    "port_80_owner_metadata_truncated",
    "port_443_status",
    "port_443_listener_count",
    "port_443_records_truncated",
    "port_443_bind_scope",
    "port_443_owner_metadata",
    "port_443_owner_metadata_truncated",
    "init_system",
    "systemd_state",
    "docker_service_enabled",
    "docker_service_active",
    "docker_socket_enabled",
    "docker_socket_active",
    "reboot_required",
    "postgres_container_status",
    "postgres_container_state",
    "postgres_restart_policy",
    "api_container_status",
    "api_container_state",
    "api_restart_policy",
    "web_container_status",
    "web_container_state",
    "web_restart_policy",
    "caddy_production_container_status",
    "caddy_production_container_state",
    "caddy_production_restart_policy",
    "restart_policy_gate",
    "bootstrap_gate",
)

FORBIDDEN_COMMANDS = (
    "curl",
    "wget",
    "ftp",
    "sftp",
    "ssh",
    "scp",
    "rsync",
    "nc",
    "ncat",
    "netcat",
    "telnet",
    "ping",
    "dig",
    "host",
    "nslookup",
    "getent",
    "ip",
    "ifconfig",
    "hostname",
    "sudo",
    "doas",
    "su",
    "apt",
    "apt-get",
    "dpkg",
    "snap",
    "dnf",
    "yum",
    "pacman",
    "apk",
    "mount",
    "umount",
    "mkfs",
    "fdisk",
    "parted",
    "cryptsetup",
    "mkdir",
    "rmdir",
    "touch",
    "truncate",
    "unlink",
    "rm",
    "cp",
    "mv",
    "install",
    "ln",
    "tee",
    "dd",
    "chmod",
    "chown",
    "chgrp",
    "sync",
    "kill",
    "pkill",
    "reboot",
    "shutdown",
    "poweroff",
)

FORBIDDEN_PATH_FRAGMENTS = (
    "/etc/shadow",
    "/etc/gshadow",
    "/etc/sudoers",
    "/proc/self/environ",
    "/proc/1/environ",
    ".env",
    ".ssh",
    "id_rsa",
    "id_ed25519",
    "authorized_keys",
    "credentials",
    "docker/config.json",
    "acme",
    "private_key",
)

FORBIDDEN_NETWORK_FRAGMENTS = (
    "http://",
    "https://",
    "tcp://0.0.0.0",
    "tcp://[::]",
    "ifconfig.me",
    "icanhazip",
    "ipify",
    "checkip",
    "public-ip",
    "public_ip",
    "external-ip",
    "external_ip",
)


class ContractError(RuntimeError):
    """Raised when the collector or verifier weakens an ADR-051 invariant."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def require_all(source: str, needles: Iterable[str], message: str) -> None:
    missing = [needle for needle in needles if needle not in source]
    require(not missing, f"{message}: missing {missing!r}")


def require_order(source: str, needles: Sequence[str], message: str) -> None:
    position = -1
    for needle in needles:
        next_position = source.find(needle, position + 1)
        require(next_position >= 0, f"{message}: missing {needle!r}")
        require(next_position > position, message)
        position = next_position


def strip_full_line_comments(source: str) -> str:
    return "\n".join(
        line for line in source.splitlines() if not re.match(r"^\s*#(?:\s|$)", line)
    )


def shell_function_body(source: str, name: str) -> str:
    pattern = re.compile(rf"(?ms)^{re.escape(name)}\(\)\s*\{{\n(?P<body>.*?)^\}}\s*$")
    matches = list(pattern.finditer(source))
    require(len(matches) == 1, f"Expected exactly one shell function {name}")
    return matches[0].group("body")


def parse_readonly_array(source: str, name: str) -> tuple[str, ...]:
    patterns = (
        rf"(?ms)^readonly\s+-a\s+{re.escape(name)}=\(\n(?P<body>.*?)^\)\s*$",
        rf"(?ms)^declare\s+-r?a?r?\s+{re.escape(name)}=\(\n(?P<body>.*?)^\)\s*$",
    )
    matches = [match for pattern in patterns for match in re.finditer(pattern, source)]
    require(len(matches) == 1, f"Expected exactly one readonly array {name}")
    body = matches[0].group("body")
    keys: list[str] = []
    for raw_line in body.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        match = re.fullmatch(r"['\"]?([a-z][a-z0-9_]*)['\"]?", line)
        require(match is not None, f"{name} contains a noncanonical entry: {line!r}")
        keys.append(match.group(1))
    require(keys, f"{name} may not be empty")
    require(len(keys) == len(set(keys)), f"{name} repeats a fact key")
    return tuple(keys)


def command_at_line_start_pattern(command: str) -> re.Pattern[str]:
    return re.compile(
        rf"(?m)^\s*(?:(?:command|builtin|exec|env)\s+)*(?:[A-Za-z_][A-Za-z0-9_]*=[^\s]+\s+)*{re.escape(command)}(?:\s|$)"
    )


def bounded_capture_calls(code: str) -> frozenset[str]:
    joined = re.sub(r"\\\n\s*", " ", code)
    calls: set[str] = set()
    for match in re.finditer(r"(?m)^\s*bounded_capture\s+(.+?)\s*$", joined):
        calls.add(re.sub(r"\s+", " ", match.group(1)).strip())
    return frozenset(calls)


def direct_command_heads(source: str) -> tuple[tuple[int, str], ...]:
    """Extract conservative command-position heads without executing Bash.

    Continuation lines are joined, the one canonical data-only array is
    removed, assignments are skipped, and control-condition prefixes are
    unwrapped. This intentionally rejects every unknown bare or absolute
    executable while leaving Bash grammar, builtins, and reviewed functions
    explicit in ALLOWED_DIRECT_COMMAND_HEADS.
    """

    masked = re.sub(
        r"(?ms)^readonly\s+-a\s+FACT_KEYS=\(\n.*?^\)\s*$",
        "readonly -a FACT_KEYS=()",
        source,
    )
    continued = re.sub(r"\\\n\s*", " ", masked)
    logical_lines: list[str] = []
    logical_buffer = ""
    test_depth = 0
    for physical_line in continued.splitlines():
        logical_buffer = (
            f"{logical_buffer} {physical_line.strip()}"
            if logical_buffer
            else physical_line
        )
        test_depth += physical_line.count("[[") - physical_line.count("]]" )
        if test_depth <= 0:
            logical_lines.append(logical_buffer)
            logical_buffer = ""
            test_depth = 0
    if logical_buffer:
        logical_lines.append(logical_buffer)
    joined = "\n".join(logical_lines)
    result: list[tuple[int, str]] = []
    command_pattern = re.compile(r"^(/[A-Za-z0-9_./+-]+|[A-Za-z_][A-Za-z0-9_.-]*)(?=\s|$)")
    assignment_start_pattern = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
    function_pattern = re.compile(r"^[a-z_][a-z0-9_]*\(\)\s*\{")

    def consume_single_quoted_word(text: str, index: int) -> int:
        closing = text.find("'", index + 1)
        return len(text) if closing < 0 else closing + 1

    def consume_braced_expansion(text: str, index: int) -> int:
        depth = 1
        index += 2
        while index < len(text) and depth:
            if text[index] == "\\":
                index += 2
            elif text[index] == "'":
                index = consume_single_quoted_word(text, index)
            elif text[index] == '"':
                index = consume_double_quoted_word(text, index)
            elif text.startswith("${", index):
                depth += 1
                index += 2
            elif text[index] == "}":
                depth -= 1
                index += 1
            else:
                index += 1
        return index

    def consume_command_substitution(text: str, index: int) -> int:
        depth = 1
        index += 2
        while index < len(text) and depth:
            if text[index] == "\\":
                index += 2
            elif text[index] == "'":
                index = consume_single_quoted_word(text, index)
            elif text[index] == '"':
                index = consume_double_quoted_word(text, index)
            elif text[index] == "`":
                index = consume_backtick_word(text, index)
            elif text.startswith("$(", index):
                depth += 1
                index += 2
            elif text[index] == ")":
                depth -= 1
                index += 1
            else:
                index += 1
        return index

    def consume_backtick_word(text: str, index: int) -> int:
        index += 1
        while index < len(text):
            if text[index] == "\\":
                index += 2
            elif text[index] == "`":
                return index + 1
            else:
                index += 1
        return index

    def consume_double_quoted_word(text: str, index: int) -> int:
        index += 1
        while index < len(text):
            if text[index] == "\\":
                index += 2
            elif text.startswith("$(", index):
                index = consume_command_substitution(text, index)
            elif text.startswith("${", index):
                index = consume_braced_expansion(text, index)
            elif text[index] == "`":
                index = consume_backtick_word(text, index)
            elif text[index] == '"':
                return index + 1
            else:
                index += 1
        return index

    def consume_assignment_word(text: str) -> int | None:
        match = assignment_start_pattern.match(text)
        if match is None:
            return None
        index = match.end()
        while index < len(text) and not text[index].isspace():
            if text.startswith("$'", index):
                index = consume_single_quoted_word(text, index + 1)
            elif text[index] == "'":
                index = consume_single_quoted_word(text, index)
            elif text[index] == '"':
                index = consume_double_quoted_word(text, index)
            elif text.startswith("$(", index):
                index = consume_command_substitution(text, index)
            elif text.startswith("${", index):
                index = consume_braced_expansion(text, index)
            elif text[index] == "`":
                index = consume_backtick_word(text, index)
            elif text[index] == "\\":
                index += 2
            else:
                index += 1
        return index

    def segments(line: str) -> tuple[str, ...]:
        parts: list[str] = []
        start = 0
        index = 0
        quote = ""
        escaped = False
        test_depth = 0
        arithmetic_depth = 0
        while index < len(line):
            char = line[index]
            pair = line[index : index + 2]
            if escaped:
                escaped = False
                index += 1
                continue
            if char == "\\" and quote != "'":
                escaped = True
                index += 1
                continue
            if quote:
                if char == quote:
                    quote = ""
                index += 1
                continue
            if char in ("'", '"'):
                quote = char
                index += 1
                continue
            if pair == "[[":
                test_depth += 1
                index += 2
                continue
            if pair == "]]" and test_depth:
                test_depth -= 1
                index += 2
                continue
            if pair == "((":
                arithmetic_depth += 1
                index += 2
                continue
            if pair == "))" and arithmetic_depth:
                arithmetic_depth -= 1
                index += 2
                continue
            if test_depth == 0 and arithmetic_depth == 0:
                operator_length = 0
                if pair in ("&&", "||", ";;"):
                    operator_length = 2
                elif char in (";", "|"):
                    operator_length = 1
                if operator_length:
                    parts.append(line[start:index])
                    start = index + operator_length
                    index += operator_length
                    continue
            index += 1
        parts.append(line[start:])
        return tuple(parts)

    for line_number, raw_line in enumerate(joined.splitlines(), start=1):
        text = raw_line.strip()
        if not text or text.startswith("#") or function_pattern.match(text):
            continue
        if text.startswith(("}", ")", ";;", "[[", "((")):
            continue
        # A case arm label is data up to ')'; inspect only an inline action.
        if re.match(r"^(?:[^'\"()]|'[^']*'|\"[^\"]*\")+\)", text):
            text = text.split(")", 1)[1].strip()
            if not text:
                continue
        for segment in segments(text):
            candidate = segment.strip().lstrip("{}(").strip()
            if not candidate:
                continue
            for keyword in ("then", "do", "if", "elif", "while", "until"):
                prefix = keyword + " "
                if candidate.startswith(prefix):
                    candidate = candidate[len(prefix) :].lstrip()
                    break
            if candidate.startswith("!"):
                candidate = candidate[1:].lstrip()
            if not candidate or candidate.startswith(("[[", "((", "for ", "case ")):
                continue
            while True:
                assignment_end = consume_assignment_word(candidate)
                if assignment_end is None:
                    break
                candidate = candidate[assignment_end:].lstrip()
            if not candidate:
                continue
            match = command_pattern.match(candidate)
            if match is not None:
                result.append((line_number, match.group(1)))
            elif candidate.startswith(('"', "'", "$", "`", "\\")):
                result.append((line_number, "<quoted-or-expanded-command-head>"))

        for substitution in re.finditer(
            r"\$\(\s*(/[A-Za-z0-9_./+-]+|[A-Za-z_][A-Za-z0-9_.-]*)(?=\s|\))",
            text,
        ):
            head = substitution.group(1)
            if head != "((":
                result.append((line_number, head))
        if re.search(r"(?:\$\(|`)\s*[\"'$\\]", text):
            result.append((line_number, "<quoted-or-expanded-substitution-head>"))
    return tuple(result)


def validate_direct_invocation_surface(source: str) -> None:
    functions = frozenset(
        re.findall(r"(?m)^([a-z_][a-z0-9_]*)\(\)\s*\{", source)
    )
    require(
        functions == EXPECTED_INTERNAL_FUNCTIONS,
        "Collector function surface changed: "
        f"missing={sorted(EXPECTED_INTERNAL_FUNCTIONS - functions)!r}, "
        f"extra={sorted(functions - EXPECTED_INTERNAL_FUNCTIONS)!r}",
    )
    heads = direct_command_heads(source)
    unknown = [(line, head) for line, head in heads if head not in ALLOWED_DIRECT_COMMAND_HEADS]
    require(not unknown, f"Unlisted direct executable invocation(s): {unknown!r}")
    absolute = [(line, head) for line, head in heads if head.startswith("/")]
    require(
        absolute and {head for _, head in absolute} == {"/usr/bin/env"} and len(absolute) == 2,
        f"Absolute executable surface changed: {absolute!r}",
    )


def executable_wrapper_tokens(source: str) -> tuple[tuple[int, str], ...]:
    """Find command/builtin tokens in every executable shell context.

    Full-line and inline comments plus ordinary single/double-quoted data are
    ignored. Command substitutions inside double quotes and legacy backtick
    substitutions are recursively scanned because their contents execute.
    Grouping parentheses remain in the normal executable stream.
    """

    wrappers: list[tuple[int, str]] = []
    length = len(source)
    shell_delimiters = frozenset(" \t\r\n;|&(){}<>")

    def record_at(index: int) -> int | None:
        for wrapper in ("command", "builtin"):
            if not source.startswith(wrapper, index):
                continue
            before_ok = index == 0 or source[index - 1] in shell_delimiters or source[index - 1] == "`"
            end = index + len(wrapper)
            after_ok = end == length or source[end] in shell_delimiters or source[end] == "`"
            if before_ok and after_ok:
                wrappers.append((source.count("\n", 0, index) + 1, wrapper))
                return end
        return None

    def scan_double(index: int) -> int:
        while index < length:
            char = source[index]
            if char == "\\":
                index += 2
            elif char == '"':
                return index + 1
            elif source.startswith("$(", index):
                index = scan_normal(index + 2, ")")
            elif char == "`":
                index = scan_normal(index + 1, "`")
            else:
                index += 1
        return index

    def scan_normal(index: int, terminator: str | None = None) -> int:
        while index < length:
            char = source[index]
            if terminator is not None and char == terminator:
                return index + 1
            if char == "\\":
                index += 2
                continue
            if char == "'":
                closing = source.find("'", index + 1)
                index = length if closing < 0 else closing + 1
                continue
            if char == '"':
                index = scan_double(index + 1)
                continue
            if source.startswith("$(", index):
                index = scan_normal(index + 2, ")")
                continue
            if char == "`":
                index = scan_normal(index + 1, "`")
                continue
            if char == "#" and (
                index == 0 or source[index - 1] in shell_delimiters
            ):
                newline = source.find("\n", index + 1)
                index = length if newline < 0 else newline + 1
                continue
            recorded_end = record_at(index)
            if recorded_end is not None:
                index = recorded_end
                continue
            index += 1
        return index

    scan_normal(0)
    return tuple(wrappers)


def validate_shell_wrapper_contract(source: str) -> None:
    """Pin the only reviewed command/builtin wrapper argv.

    Merely allowlisting the wrapper head is unsafe: ``command python3`` and
    ``builtin eval`` would otherwise hide an arbitrary final executable from
    the direct-head guard. The collector needs exactly one lookup and one
    inherited-function removal; every other wrapper call fails closed.
    """

    tokens = executable_wrapper_tokens(source)
    require(
        tuple(wrapper for _, wrapper in tokens) == ("builtin", "command"),
        f"Executable command/builtin token surface changed: {tokens!r}",
    )

    joined = re.sub(r"\\\n\s*", " ", source)
    wrapper_pattern = re.compile(
        r"(?:^|[;&|{}]\s*)"
        r"\s*(?:(?:if|elif|while|until|then|do)\s+)?!?\s*"
        r"(?:[A-Za-z_][A-Za-z0-9_]*=(?:'[^']*'|\"[^\"]*\"|[^\s]+)\s+)*"
        r"(?P<wrapper>command|builtin)\b[^\n]*",
        re.MULTILINE,
    )
    actual: list[str] = []
    for match in wrapper_pattern.finditer(joined):
        relative_wrapper_start = match.start("wrapper") - match.start()
        line = match.group(0)[relative_wrapper_start:].strip()
        actual.append(line)

    expected = [
        'builtin unset -f "$inherited_function" 2>/dev/null || true',
        'command -v "$command_name" >/dev/null 2>&1; then',
    ]
    require(
        sorted(actual) == sorted(expected),
        f"Shell command/builtin wrapper surface changed: {actual!r}",
    )


def reject_forbidden_vocabulary(code: str) -> None:
    for command in FORBIDDEN_COMMANDS:
        require(
            command_at_line_start_pattern(command).search(code) is None,
            f"Forbidden mutating, privileged, package, or network command appeared: {command}",
        )
    lowered = code.casefold()
    for fragment in FORBIDDEN_PATH_FRAGMENTS:
        require(fragment.casefold() not in lowered, f"Forbidden secret/config path appeared: {fragment}")
    for fragment in FORBIDDEN_NETWORK_FRAGMENTS:
        require(fragment.casefold() not in lowered, f"Forbidden network/public-IP content appeared: {fragment}")

    require(
        re.search(r"(?m)^\s*(?:printenv|export\s+-p|declare\s+-p)(?:\s|$)", code) is None
        and re.search(r"(?m)^\s*env\b(?!\s+-i(?:\s|$))", code) is None
        and re.search(r"(?m)^\s*set\s*(?:$|[;|&])", code) is None,
        "Collector may not enumerate environment values or shell state",
    )
    require("/environ" not in lowered, "Collector may not read a process environment file")
    require(
        re.search(r"\$\{![A-Za-z_]", code) is None,
        "Collector may not indirectly expand caller-selected environment values",
    )

    without_suppression = (
        code.replace("2>/dev/null", "")
        .replace(">/dev/null", "")
        .replace("2>&1", "")
        .replace(">&2", "")
    )
    for line in without_suppression.splitlines():
        stripped = line.lstrip()
        if "((" in line and stripped.startswith(("((", "if ", "elif ", "while ")):
            continue
        require(
            re.search(r"(?:^|[ \t])(?:[0-9]*>{1,2}|&>)\s*[^&\s]", line) is None,
            "Collector may not write through shell output redirection",
        )


def validate_cli_contract(code: str) -> None:
    usage_line = "Usage: server-facts.sh [--backup-mount ABS_PATH] [--output stdout]"
    require(code.count(usage_line) == 1, "Collector usage/CLI surface changed")
    require_all(
        code,
        (
            "--backup-mount",
            "--output",
            '"stdout"',
            "exit 64",
        ),
        "Collector CLI is not fail-closed",
    )
    require(
        re.search(r"(?m)^\s*\*\)\s*$", code) is not None,
        "Unknown collector arguments need an explicit rejecting case arm",
    )
    require_all(
        code,
        (
            "validate_absolute_path",
            '[[ "$value" == /*',
            '[[ "$value" != *"//"*',
            '"$component" != "."',
            '"$component" != ".."',
        ),
        "Backup mount must be one canonical absolute path",
    )


def validate_command_boundary(code: str) -> None:
    require_all(
        code,
        (
            "timeout --signal=TERM --kill-after=1s 4s",
            "readonly WSR_FACTS_COMMAND_TIMEOUT_SECONDS=4",
            "head -c 131073",
            "131072",
            "unix:///var/run/docker.sock",
            "wall-street-receipts-home_postgres-data",
            "readonly WSR_FACTS_TRUSTED_PATH=/usr/sbin:/usr/bin:/sbin:/bin",
            'export PATH="$WSR_FACTS_TRUSTED_PATH"',
            "readonly WSR_FACTS_DOCKER_CONFIG=/var/empty/wall-street-receipts-server-facts",
        ),
        "Bounded command/fixed Docker identity contract changed",
    )
    for command in EXPECTED_EXTERNAL_COMMANDS:
        require(re.search(rf"\b{re.escape(command)}\b", code) is not None, f"Expected command disappeared: {command}")

    actual_calls = bounded_capture_calls(code)
    require(
        actual_calls == EXPECTED_BOUNDED_CALLS,
        "Bounded command argv allowlist changed: "
        f"missing={sorted(EXPECTED_BOUNDED_CALLS - actual_calls)!r}, "
        f"extra={sorted(actual_calls - EXPECTED_BOUNDED_CALLS)!r}",
    )
    require_all(
        code,
        (
            "docker_info_template='{{.ServerVersion}}|{{.DockerRootDir}}|{{.Driver}}",
            "volume_template='{{.Name}}|{{.Driver}}|{{.Scope}}|{{.Mountpoint}}",
            "inspect_template='{{.State.Status}}|{{.HostConfig.RestartPolicy.Name}}",
            "collect_port_facts 80",
            "collect_port_facts 443",
            "systemctl_fact docker_service_enabled is-enabled docker.service",
            "systemctl_fact docker_service_active is-active docker.service",
            "systemctl_fact docker_socket_enabled is-enabled docker.socket",
            "systemctl_fact docker_socket_active is-active docker.socket",
            "collect_container_facts postgres postgres",
            "collect_container_facts api api",
            "collect_container_facts web web",
            "collect_container_facts caddy_production caddy-production",
            "os_release_path=/etc/os-release",
            "os_release_path=/usr/lib/os-release",
            'bounded_capture cat "$os_release_path"',
        ),
        "Dynamic helper inputs or Docker templates changed",
    )
    require(
        code.count("readonly WSR_FACTS_TRUSTED_PATH=/usr/sbin:/usr/bin:/sbin:/bin") == 1,
        "The fixed production command PATH must occur exactly once",
    )
    require(
        code.count("/usr/bin/env -i") == 2
        and 'PATH="$WSR_FACTS_TRUSTED_PATH" LC_ALL=C LANG=C TZ=UTC' in code
        and 'DOCKER_HOST="$WSR_FACTS_DOCKER_HOST" DOCKER_CONFIG="$WSR_FACTS_DOCKER_CONFIG"'
        in code,
        "Every captured child command must run in the exact isolated environment",
    )
    require_order(
        code,
        (
            'bounded_capture findmnt -rn --mountpoint "$backup_mount" -o TARGET',
            'if [[ "$CAPTURE_OUTPUT" == "$backup_mount" ]]',
            'set_fact backup_exact_mountpoint "true"',
            'collect_path_facts backup "$backup_mount"',
        ),
        "Backup filesystem facts may be collected only after exact-mount proof",
    )
    require_order(
        code,
        (
            "path_type /etc/os-release",
            'os_release_path=/etc/os-release',
            "path_type /usr/lib/os-release",
            "os_release_path=/usr/lib/os-release",
            'bounded_capture cat "$os_release_path"',
        ),
        "OS release fallback must never follow a caller-selected symlink target",
    )

    require(
        re.search(r"(?m)^\s*docker\s+(?!(?:--version|compose\s+version|info|volume\s+inspect|ps\s|inspect\s))", code)
        is None,
        "Docker calls must remain in the read-only inspection allowlist",
    )
    require(
        re.search(r"(?m)^\s*systemctl\s+(?!(?:is-enabled|is-active)\s)", code) is None,
        "systemctl calls must remain read-only is-enabled/is-active queries",
    )
    require(
        re.search(r"(?m)^\s*findmnt\s+(?!(?:-rn\s+--(?:target|mountpoint)\s))", code) is None,
        "findmnt calls must remain fixed read-only target/mountpoint queries",
    )
    require(
        re.search(r"(?m)^\s*ss\s+(?!-H\s+-ltnp\s+sport\s+=\s+:(?:80|443)\b)", code) is None,
        "Socket inspection may query only listening TCP ports 80 and 443",
    )

    require(
        re.search(r"done\s+<\s+<\(\s*compgen\s+-e\s*\)", code) is not None,
        "Inherited override names must be enumerated without reading their values",
    )
    require_all(
        code,
        ("DOCKER_", "COMPOSE_", "WSR_", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "unset"),
        "Inherited Docker/Compose/WSR/proxy overrides are not cleared",
    )


def validate_output_contract(source: str, code: str) -> tuple[str, ...]:
    keys = parse_readonly_array(source, "FACT_KEYS")
    require(EXPECTED_FACT_KEYS, "Verifier EXPECTED_FACT_KEYS has not been pinned")
    require(keys == EXPECTED_FACT_KEYS, "Canonical fact key order changed")
    require(len(keys) <= 160, "Fact schema is too large for the bounded handoff surface")

    require_all(
        code,
        (
            "256",
            "32768",
            "unknown",
            "missing",
            "not-provided",
            "REVIEW_REQUIRED",
            "printf '%s=%s\\n'",
        ),
        "Canonical bounded output contract changed",
    )
    for declaration in (
        "readonly WSR_FACTS_VALUE_LIMIT=256",
        "readonly WSR_FACTS_REPORT_LIMIT=32768",
        "readonly WSR_FACTS_CAPTURE_LIMIT=131072",
        "readonly WSR_FACTS_COMMAND_TIMEOUT_SECONDS=4",
    ):
        require(code.count(declaration) == 1, f"Bound declaration changed: {declaration}")
    require(
        code.count("REVIEW_REQUIRED") >= 2,
        "Bootstrap and restart gates must independently remain REVIEW_REQUIRED",
    )
    require(
        re.search(r"(?m)^\s*(?:for|while)\s+.*FACT_KEYS", code) is not None
        or '"${FACT_KEYS[@]}"' in code,
        "Facts must be emitted from the exact ordered key list",
    )
    require(
        re.search(r"\b(?:echo|printf)\b[^\n]*(?:DOCKER_HOST|COMPOSE_|PROXY|SECRET|TOKEN|PASSWORD)", code)
        is None,
        "Output may not interpolate environment, secret, or override values",
    )
    for required_suffix in (
        "_status",
        "_mount_status",
        "_mount_options_safe",
        "_mount_options_omitted_count",
        "_capacity_bytes",
        "_free_bytes",
    ):
        require(any(key.endswith(required_suffix) for key in keys), f"Missing required fact state {required_suffix}")
    require(
        any("bootstrap" in key and "gate" in key for key in keys)
        and any("restart" in key and "gate" in key for key in keys),
        "Required operator-review gates are missing",
    )
    return keys


def validate_source(source: str) -> tuple[str, ...]:
    require(source.startswith("#!/usr/bin/env bash\nset -euo pipefail\n"), "Collector must start with strict Bash")
    require("\r" not in source, "Collector must use LF line endings")
    require("\x00" not in source, "Collector may not contain NUL bytes")
    code = strip_full_line_comments(source)
    reject_forbidden_vocabulary(code)
    validate_direct_invocation_surface(code)
    validate_shell_wrapper_contract(code)
    validate_cli_contract(code)
    validate_command_boundary(code)
    keys = validate_output_contract(source, code)
    require_order(
        code,
        ("emit_report", 'main "$@"'),
        "Collector must emit exactly once through its explicit main entry point",
    )
    return keys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    require(source.count(old) == 1, f"Mutation anchor changed for {label}: {old!r}")
    return source.replace(old, new, 1)


def validate_mutations(source: str) -> int:
    # Insertions prove broad classes fail even if function internals are later
    # refactored.  Exact replacements pin limits and fixed identities.
    main_anchor = 'main "$@"'
    require(source.count(main_anchor) == 1, "Mutation main anchor changed")
    insertions = (
        ("network fetch", "curl https://ifconfig.me"),
        ("privilege escalation", "sudo stat /etc/shadow"),
        ("package installation", "apt-get install docker-ce"),
        ("host write", "touch /tmp/wsr-server-facts-owned"),
        ("host delete", "rm -rf /tmp/wsr-server-facts-owned"),
        ("environment value enumeration", "printenv"),
        ("process environment read", "cat /proc/self/environ"),
        ("secret path read", "cat /etc/shadow"),
        ("public IP disclosure", "printf 'public_ip=203.0.113.9\\n'"),
        ("Docker mutation", "docker volume rm wall-street-receipts-home_postgres-data"),
        ("systemd mutation", "systemctl restart docker.service"),
        ("wrapped Docker mutation", "bounded_capture docker run --rm alpine true"),
        ("wrapped systemd mutation", "bounded_capture systemctl restart docker.service"),
        ("unlisted Python executable", "python3 --version"),
        ("unlisted Perl executable", "perl -e 'print qq(pwned)'"),
        ("unlisted absolute executable", "/bin/rm -- /tmp/wsr-server-facts-owned"),
        ("command wrapper Python executable", "command python3 --version"),
        (
            "command wrapper absolute executable",
            "command /bin/rm -- /tmp/wsr-server-facts-owned",
        ),
        ("builtin eval executable", 'builtin eval "python3 --version"'),
        (
            "grouped command wrapper executable",
            "(command /bin/rm -- /tmp/wsr-server-facts-owned)",
        ),
        (
            "grouped builtin eval executable",
            '(builtin eval "python3 --version")',
        ),
        (
            "command-substitution command wrapper",
            'wrapper_probe="$(command python3 --version)"',
        ),
        (
            "command-substitution builtin eval",
            'wrapper_probe="$(builtin eval \'python3 --version\')"',
        ),
        (
            "backtick command wrapper",
            "wrapper_probe=`command python3 --version`",
        ),
        (
            "backtick builtin eval",
            "wrapper_probe=`builtin eval 'python3 --version'`",
        ),
        (
            "quoted absolute executable head",
            '"/bin/rm" -- /tmp/wsr-server-facts-owned',
        ),
        (
            "expanded variable executable head",
            'runner=/bin/rm\n"$runner" -- /tmp/wsr-server-facts-owned',
        ),
        (
            "default-expanded executable head",
            "${runner:-/bin/rm} -- /tmp/wsr-server-facts-owned",
        ),
        (
            "quoted command wrapper head",
            '"command" /bin/rm -- /tmp/wsr-server-facts-owned',
        ),
        (
            "quoted builtin wrapper head",
            '"builtin" eval "python3 --version"',
        ),
        ("arbitrary output file", "printf pwned > /tmp/wsr-server-facts-owned"),
    )
    mutations: list[tuple[str, str]] = [
        (label, source.replace(main_anchor, f"{payload}\n{main_anchor}", 1))
        for label, payload in insertions
    ]
    for label, old, new in (
        ("remote Docker endpoint", "unix:///var/run/docker.sock", "tcp://192.0.2.10:2375"),
        (
            "caller-selected volume",
            "readonly WSR_FACTS_COMPOSE_VOLUME=wall-street-receipts-home_postgres-data",
            "${CALLER_VOLUME}",
        ),
        (
            "unbounded command capture",
            "head -c 131073",
            'head -c 1048577',
        ),
        (
            "weakened capture ceiling",
            "readonly WSR_FACTS_CAPTURE_LIMIT=131072",
            "readonly WSR_FACTS_CAPTURE_LIMIT=1048576",
        ),
        (
            "weakened scalar ceiling",
            "readonly WSR_FACTS_VALUE_LIMIT=256",
            "readonly WSR_FACTS_VALUE_LIMIT=4096",
        ),
        (
            "weakened document ceiling",
            "readonly WSR_FACTS_REPORT_LIMIT=32768",
            "readonly WSR_FACTS_REPORT_LIMIT=1048576",
        ),
        (
            "false bootstrap readiness",
            'set_fact bootstrap_gate "REVIEW_REQUIRED"',
            'set_fact bootstrap_gate "READY"',
        ),
        (
            "caller-controlled command PATH",
            "readonly WSR_FACTS_TRUSTED_PATH=/usr/sbin:/usr/bin:/sbin:/bin",
            'readonly WSR_FACTS_TRUSTED_PATH="$PATH"',
        ),
        (
            "non-isolated child environment",
            "/usr/bin/env -i \\\n      PATH=",
            "/usr/bin/env \\\n      PATH=",
        ),
        (
            "arbitrary OS release path",
            "os_release_path=/usr/lib/os-release",
            'os_release_path="$HOME/.env"',
        ),
        (
            "mutable systemd helper action",
            "systemctl_fact docker_service_active is-active docker.service",
            "systemctl_fact docker_service_active restart docker.service",
        ),
        (
            "backup observation before exact proof",
            'set_fact backup_exact_mountpoint "true"\n        collect_path_facts backup "$backup_mount"',
            'collect_path_facts backup "$backup_mount"\n        set_fact backup_exact_mountpoint "true"',
        ),
        ("missing final newline", "printf '%s=%s\\n'", "printf '%s=%s'"),
    ):
        mutations.append((label, replace_once(source, old, new, label)))

    validate_source(source)
    for label, mutation in mutations:
        try:
            validate_source(mutation)
        except ContractError:
            continue
        raise ContractError(f"Collector source mutation was accepted: {label}")
    return len(mutations)


def validate_fixture_verifier(source: str) -> None:
    require(source.startswith("#!/usr/bin/env bash\nset -euo pipefail\n"), "Fixture verifier must use strict Bash")
    require_all(
        source,
        (
            "FACT_TEST_SCENARIO",
            "FACT_TEST_LOG",
            "TOP_SECRET_CANARY",
            "--backup-mount",
            "--output",
            "unexpected-command",
            "REVIEW_REQUIRED",
            "32768",
            "256",
        ),
        "Pure fixture verifier lost an adversarial boundary",
    )
    require(
        'export PATH="/caller-controlled-path"' in source
        and "trusted_path_anchor='readonly WSR_FACTS_TRUSTED_PATH=" in source,
        "Fixture verifier must execute only its temporary trusted-PATH replacement",
    )


def read_required(path: Path) -> str:
    require(path.is_file(), f"Required file is missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def read_required_bytes(path: Path) -> bytes:
    require(path.is_file(), f"Required file is missing: {path.relative_to(ROOT)}")
    with path.open("rb") as source_file:
        source_bytes = source_file.read(COLLECTOR_SOURCE_LIMIT + 1)
    require(
        len(source_bytes) <= COLLECTOR_SOURCE_LIMIT,
        f"Collector exceeds the {COLLECTOR_SOURCE_LIMIT}-byte verifier input limit",
    )
    return source_bytes


def validate_collector_digest(source_bytes: bytes) -> None:
    actual = hashlib.sha256(source_bytes).hexdigest()
    require(
        actual == EXPECTED_COLLECTOR_SHA256,
        "Collector exact-source review lock changed: "
        f"expected SHA-256 {EXPECTED_COLLECTOR_SHA256}, got {actual}",
    )


def validate_digest_mutation(source_bytes: bytes) -> int:
    """Prove the exact-byte review lock rejects an otherwise harmless edit."""

    validate_collector_digest(source_bytes)
    try:
        validate_collector_digest(source_bytes + b"# unreviewed byte change\n")
    except ContractError:
        return 1
    raise ContractError("Collector exact-source digest mutation was accepted")


def main() -> int:
    try:
        collector_bytes = read_required_bytes(COLLECTOR)
        collector_source = collector_bytes.decode("utf-8")
        fixture_source = read_required(FIXTURE_VERIFIER)
        mutation_count = validate_mutations(collector_source)
        validate_collector_digest(collector_bytes)
        mutation_count += validate_digest_mutation(collector_bytes)
        validate_fixture_verifier(fixture_source)
    except (ContractError, OSError, UnicodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    platform_scope = "cross-platform static/mutation"
    print(
        "PASS: ADR-051 server facts remain read-only, secret-free, network-free, "
        f"canonical, and bounded; {mutation_count} source mutations rejected "
        f"({platform_scope}; Linux/Git-Bash execution is owned by the pure fixture verifier)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
