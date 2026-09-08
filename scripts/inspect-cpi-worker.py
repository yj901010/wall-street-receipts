#!/usr/bin/env python3
"""One-shot, read-only Docker evidence inspection. Never a health/DB probe."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
import ipaddress
import json
import os
import re
import shutil
import subprocess
import sys
import threading
from urllib.parse import urlsplit
from uuid import UUID

UTC = timezone.utc
KST = timezone(timedelta(hours=9))
MAX_LOG_BYTES = 1_048_576
TAIL_LINES = 500
GRACE_SECONDS = 300
COMMAND = ["--wsr-schedule-bls-cpi"]
ENTRYPOINT = ["java", "-jar", "/opt/wsr/application.jar"]
# Select fields at the daemon: never request the environment, mounts, labels,
# health output, State.Error, Docker endpoint credentials or a full inspect dump.
INSPECT_FORMAT = ('{"id":{{json .Id}},"image":{{json .Image}},'
                  '"command":{{json .Config.Cmd}},"entrypoint":{{json .Config.Entrypoint}},'
                  '"status":{{json .State.Status}},"running":{{json .State.Running}},'
                  '"paused":{{json .State.Paused}},"restarting":{{json .State.Restarting}},'
                  '"dead":{{json .State.Dead}},"oomKilled":{{json .State.OOMKilled}},'
                  '"exitCode":{{json .State.ExitCode}},"restartCount":{{json .RestartCount}},'
                  '"started":{{json .State.StartedAt}},"finished":{{json .State.FinishedAt}}}')
RFC3339 = r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z"
KST_TEXT = r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} KST"
PREFIX = re.compile(rf"^({RFC3339}) \s*\S+\s+(?:INFO|WARN|ERROR)\s+\d+\s+---\s+"
                    r"\[[^\]\r\n]{1,100}\]\s+\[[^\]\r\n]{1,100}\]\s+"
                    r"[A-Za-z0-9.$]*ScheduleCpiCommand\s+:\s+(BLS_CPI_.*)$")
PATTERNS = {
    "READY": r"BLS_CPI_SCHEDULE_READY daily=23:00_KST catch_up=off retention=append_only",
    "WAITING": rf"BLS_CPI_SCHEDULE_WAITING next=({KST_TEXT})",
    "SAVED": rf"BLS_CPI_CAPTURE_SAVED id=([0-9a-f-]{{36}}) captured=({KST_TEXT})",
    "SKIPPED": rf"BLS_CPI_SCHEDULE_SKIPPED reason=durable_cooldown at=({KST_TEXT})",
    "RATE_LIMITED": rf"BLS_CPI_SCHEDULE_RATE_LIMITED retry_not_before=({KST_TEXT}) automatic_retry=off",
    "FAILED": rf"BLS_CPI_SCHEDULE_FAILED at=({KST_TEXT}) automatic_retry=off check=database_provider_and_key",
    "SCHEDULER_FAILURE": r"BLS_CPI_SCHEDULER_FAILURE check=worker_health",
}


class InspectionError(Exception):
    """Only application-owned codes are displayed; never underlying exceptions."""


def require(condition, code):
    if not condition:
        raise InspectionError(code)


def clean_environment(original):
    allowed = {"PATH", "SYSTEMROOT", "WINDIR", "PATHEXT", "TEMP", "TMP", "HOME",
               "USERPROFILE", "LOCALAPPDATA", "APPDATA", "LANG", "LC_ALL"}
    return {key: value for key, value in original.items() if key.upper() in allowed}


def local_endpoint(value):
    if re.fullmatch(r"(?:unix:///[^\r\n]+|npipe:////\./pipe/[^\r\n]+)", value):
        return True
    try:
        parsed = urlsplit(value)
        return (parsed.scheme == "tcp" and not parsed.username and not parsed.password
                and not parsed.path and not parsed.query and not parsed.fragment
                and parsed.port is not None and 1 <= parsed.port <= 65535
                and ipaddress.ip_address(parsed.hostname).is_loopback)
    except (ValueError, TypeError):
        return False


def bounded_run(args, env, *, limit, timeout=10):
    """Drain both pipes concurrently; kill on total byte overflow or deadline."""
    try:
        process = subprocess.Popen(args, env=env, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except OSError:
        raise InspectionError("DOCKER_COMMAND_UNAVAILABLE") from None
    buffers = [bytearray(), bytearray()]
    lock = threading.Lock()
    failed = threading.Event()
    total = 0

    def drain(stream, index):
        nonlocal total
        try:
            while chunk := stream.read(4096):
                with lock:
                    total += len(chunk)
                    if total > limit:
                        failed.set()
                        process.kill()
                        return
                    buffers[index].extend(chunk)
        except OSError:
            failed.set()
        finally:
            stream.close()

    threads = [threading.Thread(target=drain, args=(stream, index), daemon=True)
               for index, stream in enumerate((process.stdout, process.stderr))]
    for thread in threads:
        thread.start()
    timed_out = False
    try:
        process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        timed_out = True
        process.kill()
        process.wait(timeout=2)
    finally:
        for thread in threads:
            thread.join(timeout=2)
    require(not timed_out, "DOCKER_COMMAND_TIMEOUT")
    require(not failed.is_set() and not any(thread.is_alive() for thread in threads), "DOCKER_OUTPUT_LIMIT_OR_READ_ERROR")
    require(process.returncode == 0, "DOCKER_READ_FAILED")
    try:
        return tuple(bytes(value).decode("utf-8", errors="strict") for value in buffers)
    except UnicodeError:
        raise InspectionError("DOCKER_OUTPUT_ENCODING_ERROR") from None


class DockerReader:
    def __init__(self, original=None, run=bounded_run):
        original = os.environ if original is None else original
        self.env = clean_environment(original)
        self.run = run
        self.executable = shutil.which("docker", path=self.env.get("PATH"))
        require(self.executable is not None, "DOCKER_CLI_REQUIRED")
        if original.get("DOCKER_CONTEXT"):
            args = [self.executable, "context", "inspect", original["DOCKER_CONTEXT"],
                    "--format", "{{.Endpoints.docker.Host}}"]
            endpoint = run(args, self.env, limit=4096)[0].strip()
        elif original.get("DOCKER_HOST"):
            endpoint = original["DOCKER_HOST"]
        else:
            endpoint = run([self.executable, "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"],
                           self.env, limit=4096)[0].strip()
        require(local_endpoint(endpoint), "REMOTE_DOCKER_REJECTED")
        self.base = [self.executable, "--host", endpoint]

    def inspect(self, target):
        stdout, _ = self.run([*self.base, "container", "inspect", "--format", INSPECT_FORMAT, target],
                             self.env, limit=16_384)
        try:
            value = json.loads(stdout)
            validate_inspection(value)
            return value
        except (ValueError, TypeError, KeyError):
            raise InspectionError("INVALID_CONTAINER_INSPECTION") from None

    def logs(self, identity, started, until):
        return self.run([*self.base, "logs", "--timestamps", "--tail", str(TAIL_LINES),
                         "--since", started, "--until", until, identity], self.env, limit=MAX_LOG_BYTES)


def instant(value):
    """Exact nanosecond ordering for Docker RFC3339Nano UTC records."""
    require(isinstance(value, str) and re.fullmatch(RFC3339, value), "INVALID_TIMESTAMP")
    whole, _, fraction = value[:-1].partition(".")
    try:
        date = datetime.strptime(whole, "%Y-%m-%dT%H:%M:%S").replace(tzinfo=UTC)
        elapsed = date - datetime(1970, 1, 1, tzinfo=UTC)
        return (elapsed.days * 86400 + elapsed.seconds) * 1_000_000_000 + int(fraction.ljust(9, "0"))
    except ValueError:
        raise InspectionError("INVALID_TIMESTAMP") from None


def display(value):
    return value.astimezone(KST).strftime("%Y-%m-%d %H:%M:%S KST")


def docker_display(value):
    if value.startswith("0001-01-01T00:00:00"):
        return None
    return display(datetime.fromisoformat(value.replace("Z", "+00:00")))


def kst_date(value):
    return datetime.strptime(value, "%Y-%m-%d %H:%M:%S KST").replace(tzinfo=KST)


def validate_inspection(value):
    require(isinstance(value, dict), "INVALID_CONTAINER_INSPECTION")
    require(re.fullmatch(r"[0-9a-f]{64}", value["id"]) is not None
            and re.fullmatch(r"sha256:[0-9a-f]{64}", value["image"]) is not None, "INVALID_CONTAINER_IDENTITY")
    require(value["command"] == COMMAND and value["entrypoint"] == ENTRYPOINT, "NOT_A_SCHEDULED_CPI_WORKER")
    require(value["status"] in {"created", "running", "paused", "restarting", "removing", "exited", "dead"}, "INVALID_CONTAINER_STATE")
    for key in ("running", "paused", "restarting", "dead", "oomKilled"):
        require(type(value[key]) is bool, "INVALID_CONTAINER_STATE")
    for key in ("exitCode", "restartCount"):
        require(type(value[key]) is int and 0 <= value[key] <= 2**31 - 1, "INVALID_CONTAINER_STATE")
    for key in ("started", "finished"):
        instant(value[key])
    require(value["status"] != "running" or (value["running"] and not value["paused"]
            and not value["restarting"] and not value["dead"]), "INVALID_CONTAINER_STATE")
    require(value["status"] not in {"created", "exited", "dead"} or not value["running"], "INVALID_CONTAINER_STATE")
    require(not value["paused"] or (value["status"] == "paused" and value["running"]), "INVALID_CONTAINER_STATE")
    require(value["status"] != "paused" or value["paused"], "INVALID_CONTAINER_STATE")
    require(value["status"] != "restarting" or value["restarting"], "INVALID_CONTAINER_STATE")
    require(not value["running"] or not value["started"].startswith("0001-"), "INVALID_CONTAINER_STATE")


def events_from_logs(streams, started, observed):
    events, unsupported = [], False
    start_ns, until_ns = instant(started), instant(observed)
    for stream in streams:
        for line in stream.splitlines():
            if "BLS_CPI_" not in line:
                continue
            # Even unrecognized lines are never echoed; they may contain keys.
            envelope = re.match(rf"^({RFC3339}) ", line)
            if not envelope:
                unsupported = True
                continue
            stamp = instant(envelope[1])
            if not start_ns <= stamp <= until_ns:
                continue
            match = PREFIX.fullmatch(line) if len(line) <= 16_384 else None
            if not match:
                unsupported = True
                continue
            found = False
            for kind, pattern in PATTERNS.items():
                fields = re.fullmatch(pattern, match[2])
                if not fields:
                    continue
                try:
                    event = {"kind": kind, "loggedAtKst": docker_display(match[1])}
                    if kind == "WAITING":
                        plan = kst_date(fields[1])
                        require((plan.hour, plan.minute, plan.second) == (23, 0, 0), "INVALID_PLAN")
                        delta = instant(plan.astimezone(UTC).isoformat().replace("+00:00", "Z")) - stamp
                        require(0 <= delta <= 86400 * 1_000_000_000, "INVALID_PLAN")
                        event["recordedNextRunKst"] = fields[1]
                    elif kind == "SAVED":
                        require(str(UUID(fields[1])) == fields[1], "INVALID_CAPTURE_ID")
                        recorded = instant(kst_date(fields[2]).astimezone(UTC).isoformat().replace("+00:00", "Z"))
                        require(start_ns - 1_000_000_000 <= recorded <= stamp + 1_000_000_000, "INVALID_RESULT_TIME")
                        event.update(captureId=fields[1], capturedAtKst=fields[2])
                    elif kind in {"SKIPPED", "FAILED", "RATE_LIMITED"}:
                        recorded = instant(kst_date(fields[1]).astimezone(UTC).isoformat().replace("+00:00", "Z"))
                        if kind == "RATE_LIMITED":
                            require(recorded >= stamp - 1_000_000_000, "INVALID_RETRY_TIME")
                        else:
                            require(start_ns - 1_000_000_000 <= recorded <= stamp + 1_000_000_000, "INVALID_RESULT_TIME")
                        event["retryNotBeforeKst" if kind == "RATE_LIMITED" else "atKst"] = fields[1]
                    events.append((stamp, event))
                    found = True
                except (ValueError, InspectionError):
                    unsupported = True
                break
            if not found:
                unsupported = True
    events.sort(key=lambda item: item[0])
    for previous, current in zip(events, events[1:]):
        require(previous[0] != current[0] or previous[1] == current[1], "AMBIGUOUS_LOG_ORDER")
    return [event for _, event in events], unsupported


def summarize(info, streams, observed):
    events, unsupported = events_from_logs(streams, info["started"], observed)
    latest = {}
    for event in events:
        latest[event["kind"]] = event
    results = [event for event in events if event["kind"] in {"SAVED", "SKIPPED", "FAILED", "RATE_LIMITED"}]
    result = results[-1] if results else None
    plan = latest.get("WAITING")
    active = info["status"] == "running" and info["running"]
    attention = []
    if not active:
        state = "INACTIVE"
        attention.append("CONTAINER_NOT_RUNNING")
    elif not plan:
        state = "NO_PLAN_IN_RETAINED_LOGS"
        attention.append("PLAN_UNCONFIRMED")
    else:
        elapsed = datetime.fromisoformat(observed.replace("Z", "+00:00")) - kst_date(plan["recordedNextRunKst"])
        state = "FUTURE_PLAN_LOGGED" if elapsed.total_seconds() < 0 else "DUE_WITHIN_GRACE"
        if elapsed.total_seconds() > GRACE_SECONDS:
            state = "PLAN_OVERDUE"
            attention.append("PLAN_OVERDUE")
    if "SCHEDULER_FAILURE" in latest:
        attention.append("SCHEDULER_FAILURE_LOGGED")
    if result and result["kind"] in {"FAILED", "RATE_LIMITED"}:
        attention.append(result["kind"] + "_LOGGED")
    if info["oomKilled"]:
        attention.append("OOM_KILLED")
    if unsupported:
        attention.append("UNSUPPORTED_CPI_LOGS")
    return {
        "schemaVersion": 1, "evidenceMode": "RETAINED_LOGS_NOT_DB_VERIFIED", "dataMode": "UNVERIFIED",
        "observedAtKst": docker_display(observed),
        "container": {"id": info["id"], "image": info["image"], "state": info["status"],
                      "startedAtKst": docker_display(info["started"]), "finishedAtKst": docker_display(info["finished"]),
                      "exitCode": None if info["running"] else info["exitCode"], "oomKilled": info["oomKilled"]},
        "schedule": {"state": state, "recordedNextRunKst": plan["recordedNextRunKst"] if plan else None,
                     "loggedAtKst": plan["loggedAtKst"] if plan else None},
        "latestResultLogged": result, "lastSavedInRetainedLogs": latest.get("SAVED"),
        "attention": attention,
        "limitations": ["LATEST_500_LINES_OF_CURRENT_START_ONLY", "NOT_COMPLETE_ATTEMPT_HISTORY",
                        "NO_HEARTBEAT_OR_FRESHNESS_PROOF", "DATABASE_AND_PROVIDER_NOT_QUERIED"],
    }


def inspect_status(target, reader, clock=lambda: datetime.now(UTC)):
    require(isinstance(target, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}", target), "INVALID_CONTAINER_SELECTOR")
    before = reader.inspect(target)
    observed = clock().astimezone(UTC).isoformat().replace("+00:00", "Z")
    require(instant(before["started"]) <= instant(observed), "CONTAINER_CLOCK_IN_FUTURE")
    streams = reader.logs(before["id"], before["started"], observed) if not before["started"].startswith("0001-") else ("", "")
    after = reader.inspect(before["id"])
    require(before == after, "CONTAINER_CHANGED_DURING_INSPECTION")
    return summarize(before, streams, observed)


def render(report):
    container, schedule = report["container"], report["schedule"]
    result = report["latestResultLogged"]
    saved = report["lastSavedInRetainedLogs"]
    return "\n".join([
        "CPI 워커 점검 · 로그 기준 / DB 미검증",
        "조회 시각: " + report["observedAtKst"],
        "컨테이너: " + container["id"][:12] + " · " + container["state"],
        "예약 기록: " + schedule["state"],
        "로그에 기록된 다음 시각: " + (schedule["recordedNextRunKst"] or "확인 불가"),
        "최근 결과 로그: " + ((result["kind"] + " · " + result["loggedAtKst"]) if result else "확인 불가 (보존 로그에 없음)"),
        "보존된 마지막 성공 기록: " + ((saved["capturedAtKst"] + " · " + saved["captureId"]) if saved else "확인 불가"),
        "로그의 재요청 제한 시각: " + (result.get("retryNotBeforeKst", "해당 기록 없음") if result else "확인 불가"),
        "주의 항목: " + (", ".join(report["attention"]) or "관측된 경고 없음 — 정상·최신 데이터 보장은 아님"),
        "범위: 이번 시작 이후 최근 500줄만 확인. DB 저장·CPI 최신성·워커 생존 신호는 검증하지 않음.",
    ])


def main(args=None):
    args = sys.argv[1:] if args is None else list(args)
    if args == ["--help"]:
        print("Usage: python scripts/inspect-cpi-worker.py --container NAME_OR_ID [--json]\n"
              "읽기 전용 점검. 0=관측 경고 없음(정상 보장 아님), 1=주의 항목, 2=점검 불가.")
        return 0
    as_json = "--json" in args
    try:
        require(len(args) in (2, 3) and args[0] == "--container"
                and (len(args) == 2 or args[2] == "--json"), "USAGE_CONTAINER_REQUIRED")
        require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}", args[1]), "INVALID_CONTAINER_SELECTOR")
        report = inspect_status(args[1], DockerReader())
        print(json.dumps(report, ensure_ascii=False, indent=2) if as_json else render(report))
        return 1 if report["attention"] else 0
    except InspectionError as error:
        code = str(error)
    except Exception:
        code = "INSPECTION_UNAVAILABLE"
    print(json.dumps({"schemaVersion": 1, "inspectionComplete": False, "errorCode": code}, ensure_ascii=False)
          if as_json else "CPI 점검 불가: " + code)
    return 2


if __name__ == "__main__":
    sys.exit(main())
