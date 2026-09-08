"""DEMO inputs for read-only worker status; no Docker/DB/provider required."""
from datetime import datetime, timezone
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("cpi_status", ROOT / "scripts/inspect-cpi-worker.py")
status = importlib.util.module_from_spec(spec)
spec.loader.exec_module(status)
NOW = datetime(2026, 9, 8, 12, tzinfo=timezone.utc)
OBSERVED = "2026-09-08T12:00:00Z"
STARTED = "2026-09-08T11:00:00.100000001Z"
IDENTITY = "a" * 64
CAPTURE = "07f1da5c-a08b-4dd9-82dc-3c6eb226a248"
PLAN = "BLS_CPI_SCHEDULE_WAITING next=2026-09-08 23:00:00 KST"


def info(**values):
    return {"id": IDENTITY, "image": "sha256:" + "b" * 64,
            "command": status.COMMAND.copy(), "entrypoint": status.ENTRYPOINT.copy(),
            "status": "running", "running": True, "paused": False, "restarting": False,
            "dead": False, "oomKilled": False, "exitCode": 0, "restartCount": 0,
            "started": STARTED, "finished": "0001-01-01T00:00:00Z", **values}


def line(message, stamp="2026-09-08T11:30:00.123456789Z"):
    return (stamp + " 2026-09-08T11:30:00.123Z  INFO 7 --- [wall-street-receipts-api] "
            "[bls-cpi-daily-1] c.w.a.a.cpi.ScheduleCpiCommand : " + message + "\n")


def report(*messages, observed=OBSERVED, details=None):
    return status.summarize(info() if details is None else details, ("".join(messages), ""), observed)


class StatusEvidenceTests(unittest.TestCase):
    def test_future_plan_is_only_log_evidence_never_database_or_heartbeat_health(self):
        value = report(line(PLAN))
        self.assertEqual(value["schedule"]["state"], "FUTURE_PLAN_LOGGED")
        self.assertEqual(value["schedule"]["recordedNextRunKst"], "2026-09-08 23:00:00 KST")
        self.assertEqual(value["observedAtKst"], "2026-09-08 21:00:00 KST")
        self.assertEqual(value["dataMode"], "UNVERIFIED")
        self.assertIsNone(value["latestResultLogged"])
        self.assertIsNone(value["lastSavedInRetainedLogs"])
        self.assertIsNone(value["container"]["exitCode"])
        self.assertEqual(value["attention"], [])
        self.assertIn("NO_HEARTBEAT_OR_FRESHNESS_PROOF", value["limitations"])
        self.assertIn("DB 미검증", status.render(value))

    def test_missing_logs_never_invent_plan_success_or_zero_measurement(self):
        value = report()
        self.assertEqual(value["schedule"]["state"], "NO_PLAN_IN_RETAINED_LOGS")
        self.assertIsNone(value["schedule"]["recordedNextRunKst"])
        self.assertIsNone(value["latestResultLogged"])
        self.assertEqual(value["attention"], ["PLAN_UNCONFIRMED"])

    def test_deadline_grace_is_not_claimed_as_running_collection(self):
        for observed, expected in [("2026-09-08T14:00:00Z", "DUE_WITHIN_GRACE"),
                                   ("2026-09-08T14:05:00Z", "DUE_WITHIN_GRACE"),
                                   ("2026-09-08T14:05:00.000001Z", "PLAN_OVERDUE")]:
            value = report(line(PLAN), observed=observed)
            self.assertEqual(value["schedule"]["state"], expected)
            self.assertEqual("PLAN_OVERDUE" in value["attention"], expected == "PLAN_OVERDUE")

    def test_last_success_and_latest_failure_are_separate_retained_records(self):
        value = report(line(PLAN),
                       line(f"BLS_CPI_CAPTURE_SAVED id={CAPTURE} captured=2026-09-08 20:45:00 KST", "2026-09-08T11:45:00Z"),
                       line("BLS_CPI_SCHEDULE_FAILED at=2026-09-08 20:50:00 KST automatic_retry=off check=database_provider_and_key", "2026-09-08T11:50:00Z"))
        self.assertEqual(value["lastSavedInRetainedLogs"]["captureId"], CAPTURE)
        self.assertEqual(value["latestResultLogged"]["kind"], "FAILED")
        self.assertIn("FAILED_LOGGED", value["attention"])

    def test_retry_deadline_is_logged_gate_not_next_scheduled_run(self):
        value = report(line(PLAN), line("BLS_CPI_SCHEDULE_RATE_LIMITED retry_not_before=2026-09-10 23:00:00 KST automatic_retry=off", "2026-09-08T11:45:00Z"))
        self.assertEqual(value["schedule"]["recordedNextRunKst"], "2026-09-08 23:00:00 KST")
        self.assertEqual(value["latestResultLogged"]["retryNotBeforeKst"], "2026-09-10 23:00:00 KST")
        self.assertIn("RATE_LIMITED_LOGGED", value["attention"])
        self.assertIn("로그의 재요청 제한 시각: 2026-09-10 23:00:00 KST", status.render(value))

    def test_cooldown_is_not_reported_as_saved(self):
        value = report(line(PLAN), line("BLS_CPI_SCHEDULE_SKIPPED reason=durable_cooldown at=2026-09-08 20:45:00 KST", "2026-09-08T11:45:00Z"))
        self.assertEqual(value["latestResultLogged"]["kind"], "SKIPPED")
        self.assertIsNone(value["lastSavedInRetainedLogs"])

    def test_stopped_paused_and_oom_containers_cannot_have_active_schedule(self):
        for details in (info(status="exited", running=False, exitCode=137, oomKilled=True, finished="2026-09-08T11:55:00Z"),
                        info(status="paused", paused=True), info(status="restarting", restarting=True)):
            value = report(line(PLAN), details=details)
            self.assertEqual(value["schedule"]["state"], "INACTIVE")
            self.assertIn("CONTAINER_NOT_RUNNING", value["attention"])
            if details["oomKilled"]:
                self.assertIn("OOM_KILLED", value["attention"])
                self.assertEqual(value["container"]["exitCode"], 137)

    def test_scheduler_failure_is_not_hidden_by_a_future_plan(self):
        value = report(line("BLS_CPI_SCHEDULER_FAILURE check=worker_health"), line(PLAN, "2026-09-08T11:45:00Z"))
        self.assertIn("SCHEDULER_FAILURE_LOGGED", value["attention"])

    def test_old_incarnation_and_after_snapshot_logs_are_excluded_exactly(self):
        value = report(line(PLAN, "2026-09-08T11:00:00.100000000Z"),
                       line(PLAN, "2026-09-08T12:00:00.000000001Z"))
        self.assertIsNone(value["schedule"]["recordedNextRunKst"])

    def test_stderr_and_stdout_are_sorted_by_nanosecond_not_pipe_order(self):
        saved = line(f"BLS_CPI_CAPTURE_SAVED id={CAPTURE} captured=2026-09-08 20:45:00 KST", "2026-09-08T11:45:00.123456789Z")
        failed = line("BLS_CPI_SCHEDULE_FAILED at=2026-09-08 20:45:00 KST automatic_retry=off check=database_provider_and_key", "2026-09-08T11:45:00.123456788Z")
        value = status.summarize(info(), (saved, failed), OBSERVED)
        self.assertEqual(value["latestResultLogged"]["kind"], "SAVED")
        self.assertNotIn("FAILED_LOGGED", value["attention"])

    def test_conflicting_equal_timestamp_events_are_unknown_not_arbitrarily_ordered(self):
        with self.assertRaisesRegex(status.InspectionError, "AMBIGUOUS_LOG_ORDER"):
            report(line(PLAN), line("BLS_CPI_SCHEDULER_FAILURE check=worker_health"))

    def test_unknown_and_secret_bearing_markers_never_echo_or_invent_success(self):
        secret = "DO_NOT_ECHO_SYNTHETIC_SECRET"
        for content in (line("BLS_CPI_CAPTURE_SAVED " + secret),
                        line(PLAN).replace("ScheduleCpiCommand", "UnrelatedClass") + secret,
                        "BLS_CPI_SCHEDULE_READY " + secret,
                        line(PLAN.replace("23:00:00", "19:00:00")),
                        line(PLAN.replace("2026-09-08", "2026-09-11"))):
            value = report(content)
            self.assertIn("UNSUPPORTED_CPI_LOGS", value["attention"])
            self.assertNotIn(secret, json.dumps(value))
            self.assertNotIn(secret, status.render(value))

    def test_kst_date_rollover_and_locale_independence(self):
        value = report(observed="2026-12-31T16:00:00Z")
        self.assertEqual(value["observedAtKst"], "2027-01-01 01:00:00 KST")

    def test_contradictory_result_dates_do_not_become_success_evidence(self):
        for content in (f"BLS_CPI_CAPTURE_SAVED id={CAPTURE} captured=2026-09-09 20:30:00 KST",
                        f"BLS_CPI_CAPTURE_SAVED id={CAPTURE} captured=2026-09-07 20:30:00 KST",
                        "BLS_CPI_SCHEDULE_FAILED at=2026-09-09 20:30:00 KST automatic_retry=off check=database_provider_and_key",
                        "BLS_CPI_SCHEDULE_RATE_LIMITED retry_not_before=2026-09-07 23:00:00 KST automatic_retry=off"):
            value = report(line(content))
            self.assertIsNone(value["latestResultLogged"])
            self.assertIsNone(value["lastSavedInRetainedLogs"])
            self.assertIn("UNSUPPORTED_CPI_LOGS", value["attention"])


class ReadOnlyTransportTests(unittest.TestCase):
    def test_snapshot_pins_id_filters_current_start_and_detects_any_state_change(self):
        reader = Mock()
        reader.inspect.return_value = info()
        reader.logs.return_value = (line(PLAN), "")
        value = status.inspect_status("worker-name", reader, lambda: NOW)
        self.assertEqual(value["container"]["id"], IDENTITY)
        self.assertEqual(reader.inspect.call_args_list[0].args, ("worker-name",))
        self.assertEqual(reader.inspect.call_args_list[1].args, (IDENTITY,))
        reader.logs.assert_called_once_with(IDENTITY, STARTED, "2026-09-08T12:00:00Z")
        for alteration in ({"started": "2026-09-08T11:59:00Z"}, {"restartCount": 1},
                           {"id": "c" * 64}, {"status": "exited", "running": False}):
            reader.inspect.side_effect = [info(), info(**alteration)]
            with self.assertRaisesRegex(status.InspectionError, "CONTAINER_CHANGED_DURING_INSPECTION"):
                status.inspect_status("worker-name", reader, lambda: NOW)

    def test_never_started_container_does_not_read_prior_logs(self):
        reader = Mock()
        reader.inspect.return_value = info(status="created", running=False, started="0001-01-01T00:00:00Z")
        value = status.inspect_status("new-worker", reader, lambda: NOW)
        reader.logs.assert_not_called()
        self.assertIsNone(value["container"]["startedAtKst"])
        self.assertEqual(value["schedule"]["state"], "INACTIVE")

    def test_future_start_is_unavailable_and_selectors_cannot_be_options(self):
        reader = Mock()
        reader.inspect.return_value = info(started="2026-09-09T00:00:00Z")
        with self.assertRaisesRegex(status.InspectionError, "CONTAINER_CLOCK_IN_FUTURE"):
            status.inspect_status("worker", reader, lambda: NOW)
        reader.logs.assert_not_called()
        reader.reset_mock()
        for selector in ("-all", "worker;command", "../worker", "worker\nother", "a" * 129):
            with self.assertRaisesRegex(status.InspectionError, "INVALID_CONTAINER_SELECTOR"):
                status.inspect_status(selector, reader, lambda: NOW)
        reader.inspect.assert_not_called()

    def test_container_template_never_requests_secrets_and_rejects_wrong_command(self):
        for forbidden in (".Config.Env", ".Mounts", ".Config.Labels", ".State.Error", ".State.Health"):
            self.assertNotIn(forbidden, status.INSPECT_FORMAT)
        with patch.object(status.shutil, "which", return_value="docker"):
            run = Mock(return_value=(json.dumps(info(command=["--wsr-collect-bls-cpi"])), ""))
            reader = status.DockerReader({"DOCKER_HOST": "unix:///var/run/docker.sock"}, run)
            with self.assertRaisesRegex(status.InspectionError, "NOT_A_SCHEDULED_CPI_WORKER"):
                reader.inspect("worker")
            self.assertEqual(run.call_args.args[0][3:5], ["container", "inspect"])

    def test_inspect_rejects_malformed_json_identity_and_states(self):
        with patch.object(status.shutil, "which", return_value="docker"):
            for content in ("not json", "[]", json.dumps(info(id="secret")), json.dumps(info(running="true")),
                            json.dumps(info(exitCode=True)), json.dumps(info(started="invalid")),
                            json.dumps(info(status="running", paused=True)), json.dumps(info(status="exited")),
                            json.dumps(info(status="paused", paused=False)), json.dumps(info(status="restarting", restarting=False))):
                reader = status.DockerReader({"DOCKER_HOST": "unix:///var/run/docker.sock"}, Mock(return_value=(content, "")))
                with self.assertRaises(status.InspectionError):
                    reader.inspect("worker")

    def test_remote_endpoints_are_rejected_before_any_daemon_request(self):
        with patch.object(status.shutil, "which", return_value="docker"):
            for endpoint in ("ssh://server", "tcp://192.168.0.10:2375", "tcp://localhost:2375",
                             "tcp://user@127.0.0.1:2375", "tcp://127.999.0.1:2375", "tcp://127.0.0.1:2375/path"):
                run = Mock()
                with self.assertRaisesRegex(status.InspectionError, "REMOTE_DOCKER_REJECTED"):
                    status.DockerReader({"DOCKER_HOST": endpoint}, run)
                run.assert_not_called()

    def test_selected_context_resolves_once_and_child_environment_is_sanitized(self):
        run = Mock(return_value=("npipe:////./pipe/dockerDesktopLinuxEngine\n", ""))
        original = {"PATH": "path", "DOCKER_CONTEXT": "desktop-linux", "DOCKER_HOST": "ssh://ignored",
                    "BLS_REGISTRATION_KEY": "secret", "JAVA_TOOL_OPTIONS": "secret", "HTTP_PROXY": "secret", "COMPOSE_FILE": "secret"}
        with patch.object(status.shutil, "which", return_value="docker"):
            reader = status.DockerReader(original, run)
        self.assertEqual(run.call_args.args[0], ["docker", "context", "inspect", "desktop-linux", "--format", "{{.Endpoints.docker.Host}}"])
        self.assertEqual(reader.env, {"PATH": "path"})
        self.assertEqual(reader.base, ["docker", "--host", "npipe:////./pipe/dockerDesktopLinuxEngine"])
        reader.logs(IDENTITY, STARTED, OBSERVED)
        self.assertEqual(run.call_args.args[0][3:], ["logs", "--timestamps", "--tail", "500", "--since", STARTED, "--until", OBSERVED, IDENTITY])
        self.assertEqual(run.call_args.kwargs["limit"], 1_048_576)

    def test_missing_cli_and_invalid_arguments_do_not_read_docker(self):
        with patch.object(status.shutil, "which", return_value=None):
            with self.assertRaisesRegex(status.InspectionError, "DOCKER_CLI_REQUIRED"):
                status.DockerReader({})
        with patch.object(status, "DockerReader") as reader, patch("sys.stdout", new_callable=io.StringIO) as output:
            self.assertEqual(status.main(["--key", "DO_NOT_ECHO_SECRET", "--json"]), 2)
            self.assertNotIn("DO_NOT_ECHO_SECRET", output.getvalue())
            reader.assert_not_called()


class BoundedCommandTests(unittest.TestCase):
    def run_python(self, source, **options):
        return status.bounded_run([sys.executable, "-c", source], status.clean_environment(os.environ), **options)

    def test_both_streams_are_read_without_deadlock(self):
        out, err = self.run_python("import sys; sys.stdout.write('a'*20000); sys.stderr.write('b'*20000)", limit=40000)
        self.assertEqual((len(out), len(err)), (20000, 20000))

    def test_combined_limit_is_enforced_during_reading(self):
        with self.assertRaisesRegex(status.InspectionError, "DOCKER_OUTPUT_LIMIT_OR_READ_ERROR"):
            self.run_python("import sys; sys.stdout.write('a'*12000); sys.stderr.write('b'*12000)", limit=16000)

    def test_timeout_failure_and_bad_encoding_never_echo_child_output(self):
        for source, code in [("import time; time.sleep(30)", "DOCKER_COMMAND_TIMEOUT"),
                             ("import sys; print('DO_NOT_ECHO_SECRET'); sys.exit(1)", "DOCKER_READ_FAILED"),
                             ("import sys; sys.stdout.buffer.write(b'\\xff')", "DOCKER_OUTPUT_ENCODING_ERROR")]:
            with self.assertRaisesRegex(status.InspectionError, code):
                self.run_python(source, limit=1000, timeout=0.5 if "sleep" in source else 10)


class CliTests(unittest.TestCase):
    def test_exit_codes_and_json_dont_claim_success_for_missing_or_failed_inspection(self):
        for value, expected in ((report(line(PLAN)), 0), (report(), 1)):
            with patch.object(status, "DockerReader"), patch.object(status, "inspect_status", return_value=value), patch("sys.stdout", new_callable=io.StringIO) as out:
                self.assertEqual(status.main(["--container", "worker", "--json"]), expected)
                self.assertEqual(json.loads(out.getvalue())["dataMode"], "UNVERIFIED")
        with patch.object(status, "DockerReader", side_effect=RuntimeError("DO_NOT_ECHO_SECRET")), patch("sys.stdout", new_callable=io.StringIO) as out:
            self.assertEqual(status.main(["--container", "worker", "--json"]), 2)
            self.assertEqual(json.loads(out.getvalue()), {"schemaVersion": 1, "inspectionComplete": False, "errorCode": "INSPECTION_UNAVAILABLE"})


if __name__ == "__main__":
    unittest.main()
