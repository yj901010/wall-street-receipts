#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "${BASH_SOURCE[0]%/*}/.." && pwd -P)"
collector="$repo_root/deploy/home-server/server-facts.sh"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

assert_equal() {
  [[ "$1" == "$2" ]] || fail "expected [$2], got [$1]"
}

assert_contains() {
  local path="$1" pattern="$2"
  if ! "$real_grep" -Eq -- "$pattern" "$path"; then
    "$real_tail" -n 40 -- "$path" >&2 || true
    fail "expected $path to contain pattern: $pattern"
  fi
}

assert_not_contains() {
  local path="$1" pattern="$2"
  if "$real_grep" -Eq -- "$pattern" "$path"; then
    "$real_grep" -En -- "$pattern" "$path" >&2 || true
    fail "expected $path not to contain pattern: $pattern"
  fi
}

[[ -f "$collector" ]] || fail "collector is missing: $collector"

real_bash="$(command -v bash)"
real_mktemp="$(command -v mktemp)"
real_rm="$(command -v rm)"
real_mkdir="$(command -v mkdir)"
real_chmod="$(command -v chmod)"
real_grep="$(command -v grep)"
real_cmp="$(command -v cmp)"
real_wc="$(command -v wc)"
real_tail="$(command -v tail)"
real_uname="$(command -v uname)"

host_kernel="$($real_uname -s)"
case "$host_kernel" in
  Linux) platform_scope=linux-pure-command-doubles ;;
  MINGW*|MSYS*|CYGWIN*) platform_scope=windows-git-bash-pure-command-doubles ;;
  *) platform_scope=portable-bash-pure-command-doubles ;;
esac

temporary_root="$($real_mktemp -d)"
cleanup() {
  "$real_rm" -rf -- "$temporary_root"
}
trap cleanup EXIT

stub_dir="$temporary_root/bin"
"$real_mkdir" -- "$stub_dir"
dispatcher="$temporary_root/command-double"

# Every external command visible to the collector is a closed command double.
# The doubles deliberately reject unexpected argv instead of behaving like the
# host.  They write only their owned invocation log.
{
  printf '%s\n' '#!/bin/bash'
  printf '%s\n' 'set -euo pipefail'
  printf '%s\n' 'name="${0##*/}"'
  printf '%s\n' 'scenario=__FACT_TEST_SCENARIO__'
  printf '%s\n' 'log=__FACT_TEST_LOG__'
  printf '%s\n' 'printf '\''CALL|%s'\'' "$name" >> "$log"'
  printf '%s\n' 'printf '\''|%s'\'' "$@" >> "$log"'
  printf '%s\n' 'printf '\''\n'\'' >> "$log"'
  printf '%s\n' 'bad() { printf '\''BADARGV|%s|%s\n'\'' "$name" "$*" >> "$log"; exit 97; }'
  printf '%s\n' 'if [[ "$scenario" == "unknown" && "$name" != "timeout" && "$name" != "head" ]]; then exit 42; fi'
  printf '%s\n' 'case "$name" in'
  printf '%s\n' '  timeout)'
  printf '%s\n' '    [[ "${1:-}" == "--signal=TERM" && "${2:-}" == "--kill-after=1s" && "${3:-}" == "4s" && $# -ge 4 ]] || bad "$@"'
  printf '%s\n' '    shift 3'
  printf '%s\n' '    exec "$@"'
  printf '%s\n' '    ;;'
  printf '%s\n' '  env) bad "collector must invoke /usr/bin/env by absolute path" ;;'
  printf '%s\n' '  head)'
  printf '%s\n' '    [[ "${1:-}" == "-c" && "${2:-}" =~ ^[0-9]+$ && $# -eq 2 ]] || bad "$@"'
  printf '%s\n' '    payload=""'
  printf '%s\n' '    IFS= read -r -N "$2" payload || true'
  printf '%s\n' '    printf '\''%s'\'' "$payload"'
  printf '%s\n' '    ;;'
  printf '%s\n' '  cat)'
  printf '%s\n' '    [[ $# -eq 1 ]] || bad "$@"'
  printf '%s\n' '    case "$1" in'
  printf '%s\n' '      /etc/os-release|/usr/lib/os-release)'
  printf '%s\n' '        if [[ "$scenario" == "hostile" ]]; then'
  printf '%s\n' '          printf '\''ID=ubuntu\nVERSION_ID="24.04"\nEVIL_KEY=%s\n'\'' "${TOP_SECRET_CANARY}"'
  printf '%s\n' '          printf '\''%140000s\n'\'' x'
  printf '%s\n' '        else printf '\''ID=ubuntu\nVERSION_ID="24.04"\n'\''; fi'
  printf '%s\n' '        ;;'
  printf '%s\n' '      /proc/cpuinfo) printf '\''model name : Fixture CPU\nprocessor : 0\nprocessor : 1\nprocessor : 2\nprocessor : 3\n'\'' ;;'
  printf '%s\n' '      /proc/meminfo)'
  printf '%s\n' '        if [[ "$scenario" == "memory-partial" ]]; then printf '\''MemTotal:       16777216 kB\n'\''; else printf '\''MemTotal:       16777216 kB\nMemAvailable:   12582912 kB\n'\''; fi'
  printf '%s\n' '        ;;'
  printf '%s\n' '      *) bad "$@" ;;'
  printf '%s\n' '    esac'
  printf '%s\n' '    ;;'
  printf '%s\n' '  date) [[ "$*" == "-u +%Y-%m-%dT%H:%M:%SZ" ]] || bad "$@"; printf '\''2026-08-27T01:02:03Z\n'\'' ;;'
  printf '%s\n' '  uname)'
  printf '%s\n' '    [[ $# -eq 1 ]] || bad "$@"'
  printf '%s\n' '    case "$1" in -m) printf '\''x86_64\n'\'' ;; -r) printf '\''6.8.0-fixture\n'\'' ;; *) bad "$@" ;; esac'
  printf '%s\n' '    ;;'
  printf '%s\n' '  getconf) [[ "$*" == "_NPROCESSORS_ONLN" ]] || bad "$@"; [[ "$scenario" != "cpu-partial" ]] || exit 1; printf '\''4\n'\'' ;;'
  printf '%s\n' '  stat)'
  printf '%s\n' '    [[ "${1:-}" == "-c" && "${2:-}" == "%F" && "${3:-}" == "--" && $# -eq 4 ]] || bad "$@"'
  printf '%s\n' '    case "$4" in'
  printf '%s\n' '      /var/run/docker.sock) printf '\''socket\n'\'' ;;'
  printf '%s\n' '      /etc/os-release) [[ "$scenario" == "os-symlink" ]] && printf '\''symbolic link\n'\'' || printf '\''regular file\n'\'' ;;'
  printf '%s\n' '      /usr/lib/os-release) printf '\''regular file\n'\'' ;;'
  printf '%s\n' '      /run/reboot-required) exit 1 ;;'
  printf '%s\n' '      *) printf '\''directory\n'\'' ;;'
  printf '%s\n' '    esac'
  printf '%s\n' '    ;;'
  printf '%s\n' '  findmnt)'
  printf '%s\n' '    [[ "${1:-}" == "-rn" && $# -eq 5 && "${4:-}" == "-o" ]] || bad "$@"'
  printf '%s\n' '    mode="$2"; path="$3"; field="$5"'
  printf '%s\n' '    [[ "$mode" == "--target" || "$mode" == "--mountpoint" ]] || bad "$@"'
  printf '%s\n' '    if [[ "$mode" == "--mountpoint" ]]; then'
  printf '%s\n' '      [[ "$field" == "TARGET" && "$path" == "/srv/wsr-backup" ]] || bad "$@"'
  printf '%s\n' '      [[ "$scenario" != "backup-nested" ]] || { printf '\''/srv\n'\''; exit 0; }'
  printf '%s\n' '      printf '\''%s\n'\'' "$path"; exit 0'
  printf '%s\n' '    fi'
  printf '%s\n' '    case "$field" in'
  printf '%s\n' '      TARGET) [[ "$path" == "/srv/wsr-backup" ]] && printf '\''/srv/wsr-backup\n'\'' || printf '\''/\n'\'' ;;'
  printf '%s\n' '      FSTYPE) printf '\''ext4\n'\'' ;;'
  printf '%s\n' '      OPTIONS) printf '\''rw,nodev,nosuid,noexec,relatime,errors=remount-ro\n'\'' ;;'
  printf '%s\n' '      MAJ:MIN) [[ "$path" == "/srv/wsr-backup" ]] && printf '\''8:17\n'\'' || printf '\''8:2\n'\'' ;;'
  printf '%s\n' '      *) bad "$@" ;;'
  printf '%s\n' '    esac'
  printf '%s\n' '    ;;'
  printf '%s\n' '  df)'
  printf '%s\n' '    [[ $# -eq 4 && "$1" == "-B1" && "$2" == "--output=size,avail" && "$3" == "--" && "$4" == /* ]] || bad "$@"'
  printf '%s\n' '    printf '\''1B-blocks Avail\n107374182400 85899345920\n'\'''
  printf '%s\n' '    ;;'
  printf '%s\n' '  docker)'
  printf '%s\n' '    case "${1:-}" in'
  printf '%s\n' '      --version) [[ $# -eq 1 ]] || bad "$@"; printf '\''Docker version 27.5.1, build fixture\n'\'' ;;'
  printf '%s\n' '      compose) [[ "$*" == "compose version --short" ]] || bad "$@"; printf '\''v2.32.4\n'\'' ;;'
  printf '%s\n' '      info)'
  printf '%s\n' '        [[ $# -eq 3 && "$2" == "--format" && "$3" == *".DockerRootDir"* ]] || bad "$@"'
  printf '%s\n' '        printf '\''27.5.1|/var/lib/docker|overlay2|Ubuntu 24.04|x86_64|2|name=seccomp,name=apparmor,\n'\'''
  printf '%s\n' '        ;;'
  printf '%s\n' '      volume)'
  printf '%s\n' '        [[ $# -eq 5 && "$2" == "inspect" && "$3" == "--format" && "$5" == "wall-street-receipts-home_postgres-data" ]] || bad "$@"'
  printf '%s\n' '        printf '\''wall-street-receipts-home_postgres-data|local|local|/var/lib/docker/volumes/wall-street-receipts-home_postgres-data/_data|wall-street-receipts-home|postgres-data\n'\'''
  printf '%s\n' '        ;;'
  printf '%s\n' '      ps)'
  printf '%s\n' '        [[ $# -eq 9 && "$2" == "-a" && "$3" == "--no-trunc" && "$4" == "--filter" && "$6" == "--filter" && "$8" == "--format" && "$9" == "{{.ID}}" ]] || bad "$@"'
  printf '%s\n' '        service="${7##*=}"'
  printf '%s\n' '        case "$service" in postgres) char=a ;; api) char=b ;; web) char=c ;; caddy-production) char=d ;; *) bad "$@" ;; esac'
  printf '%s\n' '        printf '\''%064d\n'\'' 0 | while IFS= read -r zeros; do printf '\''%s\n'\'' "${zeros//0/$char}"; done'
  printf '%s\n' '        ;;'
  printf '%s\n' '      inspect)'
  printf '%s\n' '        [[ $# -eq 4 && "$2" == "--format" && "$4" =~ ^[a-d]{64}$ ]] || bad "$@"'
  printf '%s\n' '        case "${4:0:1}" in a) service=postgres ;; b) service=api ;; c) service=web ;; d) service=caddy-production ;; esac'
  printf '%s\n' '        if [[ "$scenario" == "closed-values" && "$service" == "postgres" ]]; then printf '\''running|always<script>|wall-street-receipts-home|postgres\n'\''; else printf '\''running|unless-stopped|wall-street-receipts-home|%s\n'\'' "$service"; fi'
  printf '%s\n' '        ;;'
  printf '%s\n' '      *) bad "$@" ;;'
  printf '%s\n' '    esac'
  printf '%s\n' '    ;;'
  printf '%s\n' '  ss)'
  printf '%s\n' '    [[ $# -eq 3 && "$1" == "-H" && "$2" == "-ltnp" ]] || bad "$@"'
  printf '%s\n' '    case "$3" in'
  printf '%s\n' '      "sport = :80")'
  printf '%s\n' '        if [[ "$scenario" == "owner-unavailable" ]]; then'
  printf '%s\n' '          printf '\''LISTEN 0 4096 0.0.0.0:80 0.0.0.0:*\n'\'''
  printf '%s\n' '        elif [[ "$scenario" == "owners-forward" || "$scenario" == "owners-reverse" ]]; then'
  printf '%s\n' '          owner_row_one='\''LISTEN 0 4096 0.0.0.0:80 0.0.0.0:* users:(("nginx",pid=901,fd=4),("caddy",pid=902,fd=5),("mysteryd",pid=903,fd=6))'\'''
  printf '%s\n' '          owner_row_two='\''LISTEN 0 4096 [::]:80 [::]:* users:(("docker-proxy",pid=904,fd=7),("apache2",pid=905,fd=8))'\'''
  printf '%s\n' '          if [[ "$scenario" == "owners-forward" ]]; then printf '\''%s\n%s\n'\'' "$owner_row_one" "$owner_row_two"; else printf '\''%s\n%s\n'\'' "$owner_row_two" "$owner_row_one"; fi'
  printf '%s\n' '        else'
  printf '%s\n' '          printf '\''LISTEN 0 4096 0.0.0.0:80 0.0.0.0:* users:(("docker-proxy",pid=123,fd=4))\n'\'''
  printf '%s\n' '        fi'
  printf '%s\n' '        ;;'
  printf '%s\n' '      "sport = :443") : ;;'
  printf '%s\n' '      *) bad "$@" ;;'
  printf '%s\n' '    esac'
  printf '%s\n' '    ;;'
  printf '%s\n' '  systemctl)'
  printf '%s\n' '    case "$*" in'
  printf '%s\n' '      is-system-running) printf '\''running\n'\'' ;;'
  printf '%s\n' '      "is-enabled docker.service"|"is-enabled docker.socket") printf '\''enabled\n'\'' ;;'
  printf '%s\n' '      "is-active docker.service"|"is-active docker.socket") printf '\''active\n'\'' ;;'
  printf '%s\n' '      *) bad "$@" ;;'
  printf '%s\n' '    esac'
  printf '%s\n' '    ;;'
  printf '%s\n' '  curl|wget|ftp|sftp|ssh|scp|rsync|nc|ncat|netcat|telnet|ping|dig|host|nslookup|getent|ip|ifconfig|hostname|sudo|doas|su|apt|apt-get|dpkg|snap|dnf|yum|pacman|apk|mount|umount|mkfs|fdisk|parted|cryptsetup|mkdir|rmdir|touch|truncate|unlink|rm|cp|mv|install|ln|tee|dd|chmod|chown|chgrp|sync|kill|pkill|reboot|shutdown|poweroff)'
  printf '%s\n' '    printf '\''FORBIDDEN|%s\n'\'' "$name" >> "$log"'
  printf '%s\n' '    exit 98'
  printf '%s\n' '    ;;'
  printf '%s\n' '  *) printf '\''unexpected-command|%s\n'\'' "$name" >> "$log"; exit 99 ;;'
  printf '%s\n' 'esac'
} > "$dispatcher"
allowed_commands=(env timeout head cat date uname getconf stat findmnt df docker ss systemctl)
forbidden_commands=(
  curl wget ftp sftp ssh scp rsync nc ncat netcat telnet ping dig host nslookup getent ip
  ifconfig hostname sudo doas su apt apt-get dpkg snap dnf yum pacman apk mount umount mkfs
  fdisk parted cryptsetup mkdir rmdir touch truncate unlink rm cp mv install ln tee dd chmod
  chown chgrp sync kill pkill reboot shutdown poweroff
)

install_command_doubles() {
  local scenario="$1" log="$2" command_name stub_source
  [[ "$scenario" =~ ^[a-z-]+$ && "$log" == "$temporary_root"/* && "$log" != *"'"* ]] || {
    fail "unsafe fixture literal"
  }
  stub_source="$(< "$dispatcher")"
  stub_source="${stub_source/__FACT_TEST_SCENARIO__/$scenario}"
  stub_source="${stub_source/__FACT_TEST_LOG__/$log}"
  [[ "$stub_source" != *"__FACT_TEST_"* ]] || fail "fixture literal replacement was incomplete"
  for command_name in "${allowed_commands[@]}" "${forbidden_commands[@]}"; do
    printf '%s\n' "$stub_source" > "$stub_dir/$command_name"
    "$real_chmod" 0700 "$stub_dir/$command_name"
  done
}

trusted_path_anchor='readonly WSR_FACTS_TRUSTED_PATH=/usr/sbin:/usr/bin:/sbin:/bin'
collector_source="$(< "$collector")"
collector_prefix="${collector_source%%"$trusted_path_anchor"*}"
[[ "$collector_prefix" != "$collector_source" ]] || fail "collector trusted-PATH anchor is missing"
collector_suffix="${collector_source#*"$trusted_path_anchor"}"
[[ "$collector_suffix" != *"$trusted_path_anchor"* ]] || fail "collector trusted-PATH anchor is not unique"
fixture_path_declaration="readonly WSR_FACTS_TRUSTED_PATH=$stub_dir"
fixture_collector="$temporary_root/server-facts-fixture.sh"
printf '%s%s%s\n' "$collector_prefix" "$fixture_path_declaration" "$collector_suffix" > "$fixture_collector"
"$real_chmod" 0700 "$fixture_collector"

checks=0
last_status=0
last_stdout=""
last_stderr=""
last_log=""
current_keys=""

run_collector() {
  local scenario="$1"
  shift
  checks=$((checks + 1))
  last_stdout="$temporary_root/stdout-$checks"
  last_stderr="$temporary_root/stderr-$checks"
  last_log="$temporary_root/calls-$checks"
  : > "$last_log"
  install_command_doubles "$scenario" "$last_log"
  set +e
  (
    export PATH="/caller-controlled-path"
    export TOP_SECRET_CANARY="never-print-this-secret-value"
    export WSR_SECRET_CANARY="never-print-this-wsr-value"
    export DOCKER_HOST="tcp://203.0.113.77:2375"
    export DOCKER_CONTEXT="hostile-context"
    export COMPOSE_FILE="/tmp/hostile-compose.yaml"
    export HTTP_PROXY="http://198.51.100.9:8888"
    export HTTPS_PROXY="http://198.51.100.9:8888"
    export ALL_PROXY="socks5://198.51.100.9:1080"
    export NO_PROXY="secret.internal"
    "$real_bash" "$fixture_collector" "$@"
  ) > "$last_stdout" 2> "$last_stderr"
  last_status=$?
  set -e
}

validate_canonical_output() {
  local path="$1" previous="" key value bytes line_count=0
  current_keys="$temporary_root/keys-$checks"
  : > "$current_keys"
  bytes="$($real_wc -c < "$path")"
  ((bytes > 0 && bytes <= 32768)) || fail "collector output size must be 1..32768 bytes, got $bytes"
  assert_equal "$($real_tail -c 1 -- "$path")" ""
  assert_not_contains "$path" $'\r'
  assert_not_contains "$path" 'never-print-this|203\.0\.113\.77|198\.51\.100\.9|secret\.internal|hostile-context|hostile-compose|0\.0\.0\.0|\[::\]'
  while IFS='=' read -r key value; do
    line_count=$((line_count + 1))
    [[ "$key" =~ ^[a-z][a-z0-9_]*$ ]] || fail "invalid fact key: $key"
    [[ -n "$value" ]] || fail "empty value for fact key: $key"
    ((${#value} <= 256)) || fail "fact value exceeds 256 bytes: $key"
    printf '%s\n' "$key" >> "$current_keys"
    if [[ -n "$previous" ]]; then
      ! "$real_grep" -Eq "^${key}=" "$previous" || fail "duplicate fact key: $key"
    fi
    printf '%s=%s\n' "$key" "$value" >> "$temporary_root/seen-$checks"
    previous="$temporary_root/seen-$checks"
  done < "$path"
  ((line_count > 0)) || fail "collector emitted no facts"
  assert_contains "$path" 'bootstrap.*gate=REVIEW_REQUIRED'
  assert_contains "$path" 'restart.*gate=REVIEW_REQUIRED'
}

run_collector normal
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'
baseline_stdout="$last_stdout"
baseline_keys="$current_keys"
assert_contains "$baseline_stdout" '^os_status=observed$'
assert_contains "$baseline_stdout" '^cpu_status=observed$'
assert_contains "$baseline_stdout" '^memory_status=observed$'
assert_contains "$baseline_stdout" '^docker_daemon_status=observed-local$'
assert_contains "$baseline_stdout" '^port_80_bind_scope=wildcard$'
assert_contains "$baseline_stdout" '^port_80_owner_metadata=docker-proxy$'

run_collector normal
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
"$real_cmp" -s -- "$baseline_stdout" "$last_stdout" || fail "canonical fixture output is not deterministic"
"$real_cmp" -s -- "$baseline_keys" "$current_keys" || fail "canonical fact key order changed"
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector owners-forward
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^port_80_owner_metadata=caddy,nginx,apache,docker-proxy,other$'
assert_contains "$last_stdout" '^port_80_owner_metadata_truncated=false$'
assert_not_contains "$last_stdout" 'mysteryd|apache2|pid=|0\.0\.0\.0|\[::\]'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'
owners_forward_stdout="$last_stdout"

run_collector owners-reverse
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^port_80_owner_metadata=caddy,nginx,apache,docker-proxy,other$'
assert_contains "$last_stdout" '^port_80_owner_metadata_truncated=false$'
assert_not_contains "$last_stdout" 'mysteryd|apache2|pid=|0\.0\.0\.0|\[::\]'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'
"$real_cmp" -s -- "$owners_forward_stdout" "$last_stdout" || fail "port owner output changed when listener rows were reversed"

run_collector owner-unavailable
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^collection_status=partial$'
assert_contains "$last_stdout" '^port_80_status=listening$'
assert_contains "$last_stdout" '^port_80_owner_metadata=unavailable$'
assert_not_contains "$last_stdout" 'users|pid=|0\.0\.0\.0|\[::\]'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector unknown
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '=unknown$|=missing$|=not-provided$'
"$real_cmp" -s -- "$baseline_keys" "$current_keys" || fail "unknown state changed the fact schema"
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector hostile
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_not_contains "$last_stdout" 'EVIL_KEY|evil_key|never-print-this'
"$real_cmp" -s -- "$baseline_keys" "$current_keys" || fail "hostile values changed the fact schema"
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector os-symlink
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^os_status=observed$'
assert_not_contains "$last_log" 'CALL\|cat\|/etc/os-release'
assert_contains "$last_log" 'CALL\|stat\|-c\|%F\|--\|/usr/lib/os-release'
assert_contains "$last_log" 'CALL\|cat\|/usr/lib/os-release'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector cpu-partial
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^cpu_status=partial$'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector memory-partial
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^memory_status=partial$'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector closed-values
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^postgres_container_status=invalid-observation$'
assert_not_contains "$last_stdout" 'always<script>|<|>'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector normal --backup-mount /srv/wsr-backup --output stdout
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^backup_.*='
assert_contains "$last_stdout" '^backup_exact_mountpoint=true$'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

run_collector backup-nested --backup-mount /srv/wsr-backup --output stdout
assert_equal "$last_status" "0"
validate_canonical_output "$last_stdout"
assert_contains "$last_stdout" '^backup_exact_mountpoint=false$'
assert_not_contains "$last_log" 'CALL\|findmnt\|-rn\|--target\|/srv/wsr-backup|CALL\|df\|.*\|/srv/wsr-backup$'
assert_not_contains "$last_log" 'FORBIDDEN|BADARGV|unexpected-command'

for invalid_arguments in \
  '--unknown' \
  '--backup-mount relative/path' \
  '--output /tmp/facts' \
  '--output stdout --output stdout' \
  '--backup-mount /srv/a --backup-mount /srv/b'; do
  # shellcheck disable=SC2086 -- intentional fixture argv splitting
  run_collector normal $invalid_arguments
  assert_equal "$last_status" "64"
  [[ ! -s "$last_stdout" ]] || fail "invalid CLI emitted fact output: $invalid_arguments"
done

printf 'PASS: ADR-051 pure server-fact fixtures passed %d executions (%s); no live host, Docker daemon, network, environment value, public IP, secret, or mutable command was used.\n' \
  "$checks" "$platform_scope"
