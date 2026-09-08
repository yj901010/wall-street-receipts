#!/usr/bin/env python3
"""Disposable DEMO acceptance for the read-only inspector, never a live worker."""
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("cpi_worker_rehearsal", ROOT / "scripts/verify-cpi-worker.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


def inspect(rehearsal, name, expected_exit):
    original = rehearsal.env
    try:
        rehearsal.env = {**original, "DOCKER_HOST": rehearsal.endpoint}
        result = rehearsal.command([sys.executable, str(ROOT / "scripts/inspect-cpi-worker.py"),
                                    "--container", name, "--json"], check=False)
    finally:
        rehearsal.env = original
    runtime.require(result.returncode == expected_exit, "Status CLI exit did not match the expected evidence state")
    runtime.require(not result.stderr, "Status CLI emitted unexpected diagnostics")
    runtime.require(runtime.KEY not in result.stdout and runtime.PASSWORD not in result.stdout,
                    "Status CLI exposed a synthetic secret")
    return json.loads(result.stdout)


def main():
    runtime.require(len(sys.argv) == 1, "This DEMO acceptance accepts no external configuration")
    rehearsal = runtime.Rehearsal()
    rehearsal.log_path = rehearsal.cache / ("adr067-" + rehearsal.token + ".log")
    rehearsal.report_path = rehearsal.cache / ("adr067-" + rehearsal.token + ".json")
    snapshots = {}
    passed = False
    try:
        rehearsal.prepare()
        rehearsal.fixtures()
        runtime.future_slot(datetime.now(timezone.utc))
        worker = rehearsal.start_worker("status-scheduled", "schedule_test")
        rehearsal.await_ready(lambda: "BLS_CPI_SCHEDULE_READY" in rehearsal.logs(worker))
        running = inspect(rehearsal, worker, 0)
        runtime.require(running["schedule"]["state"] == "FUTURE_PLAN_LOGGED", "Actual schedule log was not recognized")
        runtime.require(running["latestResultLogged"] is None and running["lastSavedInRetainedLogs"] is None,
                        "Inspector invented a successful collection")
        runtime.require(running["dataMode"] == "UNVERIFIED" and "NO_HEARTBEAT_OR_FRESHNESS_PROOF" in running["limitations"],
                        "Inspector overstated its evidence scope")
        snapshots["running"] = running
        rehearsal.docker("stop", "--time", "40", worker)
        runtime.require(rehearsal.inspect("container", worker)["State"]["ExitCode"] in (0, 143), "Worker needed a forced kill")
        stopped = inspect(rehearsal, worker, 1)
        runtime.require(stopped["schedule"]["state"] == "INACTIVE" and stopped["container"]["state"] == "exited",
                        "Stopped container had a false active schedule")
        snapshots["stopped"] = stopped
        rehearsal.stage("read-only actual CLI distinguishes running plan evidence and stopped process")

        runtime.future_slot(datetime.now(timezone.utc))
        rehearsal.docker("start", worker)
        rehearsal.await_ready(lambda: rehearsal.logs(worker).count("BLS_CPI_SCHEDULE_READY") == 2)
        restarted = inspect(rehearsal, worker, 0)
        runtime.require(restarted["container"]["startedAtKst"] != running["container"]["startedAtKst"],
                        "Restart incarnation was not refreshed")
        runtime.require(restarted["schedule"]["state"] == "FUTURE_PLAN_LOGGED" and restarted["latestResultLogged"] is None,
                        "Restart status reused an old result")
        snapshots["restarted"] = restarted
        rejected = inspect(rehearsal, rehearsal.provider, 2)
        runtime.require(rejected == {"schemaVersion": 1, "inspectionComplete": False,
                                    "errorCode": "NOT_A_SCHEDULED_CPI_WORKER"}, "Unrelated target was not rejected")
        snapshots["wrongTarget"] = rejected
        runtime.require(rehearsal.requests() == 0, "Read-only inspection triggered a provider request")
        runtime.require(rehearsal.sql("schedule_test", "SELECT count(*) FROM bls_cpi_captures") == "0",
                        "Read-only inspection invented or created a capture")
        rehearsal.docker("stop", "--time", "40", worker)
        runtime.require(rehearsal.inspect("container", worker)["State"]["ExitCode"] in (0, 143), "Worker needed a forced kill")
        rehearsal.stage("restart scoped logs, wrong-target refusal, zero HTTP and zero captures verified")
        passed = True
    finally:
        try:
            rehearsal.cleanup()
        except (ValueError, OSError):
            passed = False
            raise
        finally:
            rehearsal.report_path.write_text(json.dumps({
                "dataMode": "DEMO", "passed": passed, "productionActivated": False,
                "realProviderRequests": 0 if passed else None, "checks": rehearsal.results,
                "snapshots": snapshots, "note": "Synthetic disposable acceptance; not observed CPI data",
            }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print("DEMO report: " + str(rehearsal.report_path), flush=True)
    print("PASS: read-only CPI worker status acceptance; owned test resources removed", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
