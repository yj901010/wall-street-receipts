"""Offline ADR-078 safety and actual loopback relay checks; never a Docker/provider connection."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
with patch.object(sys, "path", [str(ROOT / "scripts"), *sys.path]):
    spec = importlib.util.spec_from_file_location("operator_transport_test", ROOT / "scripts/verify-cpi-operator-transport.py")
    transport = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(transport)
    import cpi_operator_lifecycle as safety


class TransportSafetyTests(unittest.TestCase):
    def test_mounted_assets_copy_to_files_not_the_parent_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = transport.Rehearsal.__new__(transport.Rehearsal)
            runtime.directory = Path(directory)
            with patch.object(transport.lifecycle.Rehearsal, "build_images") as build:
                runtime.build_images()
            build.assert_called_once()
            for path in transport.INPUTS[1:]:
                self.assertEqual((runtime.directory / "transport" / Path(path).name).read_bytes(), (ROOT / path).read_bytes())
            self.assertIn("target=/adr078,readonly", runtime.transport_mount[1])

    def test_only_disposable_reader_is_routed_through_owned_relay(self):
        runtime = transport.Rehearsal.__new__(transport.Rehearsal)
        runtime.images = {"operator": "sha256:operator"}
        runtime.transport_mount = ["--mount", "test-only,readonly"]
        runtime.await_ready = Mock()
        environment = {"POSTGRES_HOST": "postgres", "OPERATOR_API_ACCESS": "CPI_READ_ONLY"}
        with patch.object(transport.lifecycle.Rehearsal, "run_app", side_effect=["owned-relay", "owned-api"]) as run:
            self.assertEqual(runtime.run_app("api", "sha256:api", "owned-network", "10001:10001", [], environment), "owned-api")
        self.assertEqual(run.call_args_list[0].args[:4], ("relay", "sha256:operator", "owned-network", "node"))
        applied = run.call_args_list[1].args[5]
        self.assertEqual(applied["POSTGRES_HOST"], "owned-relay")
        self.assertEqual(applied["POSTGRES_PORT"], "15432")
        self.assertEqual(applied["SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"], "1")
        self.assertEqual(environment["POSTGRES_HOST"], "postgres")
        with patch.object(transport.lifecycle.Rehearsal, "run_app", return_value="migration") as run:
            runtime.run_app("migrate", "sha256:api", "owned-network", "10001:10001", [], environment)
        self.assertIs(run.call_args.args[5], environment)

    def test_confirmation_precedes_resources_and_external_targets_are_rejected(self):
        for args in ([], ["--db", "production"], ["--confirm-disposable-demo", "--host", "external"]):
            with patch.object(sys, "argv", ["test", *args]), patch.object(transport, "Rehearsal") as runtime:
                with self.assertRaises(ValueError):
                    transport.main()
                runtime.assert_not_called()

    def test_evidence_requires_one_live_silenced_link_real_bytes_and_closure(self):
        before = {"links": [{"id": 1, "silenced": True, "closed": False, "discardedBytes": 0}]}
        after = {"links": [{"id": 1, "silenced": True, "closed": True, "discardedBytes": 20}]}
        self.assertEqual(transport.fault_evidence(before, after), {"connectionId": 1, "discardedBytes": 20, "closed": True})
        for broken in ({"links": []}, {"links": [{**after["links"][0], "id": 2}]},
                       {"links": [{**after["links"][0], "silenced": False}]},
                       {"links": [{**after["links"][0], "closed": False}]},
                       {"links": [{**after["links"][0], "discardedBytes": 0}]}):
            with self.assertRaises(ValueError):
                transport.fault_evidence(before, broken)
        for broken in ({"links": []}, {"links": [{**before["links"][0], "silenced": False}]},
                       {"links": before["links"] + [{**before["links"][0], "id": 2}]}):
            with self.assertRaises(ValueError):
                transport.fault_evidence(broken, after)

    def test_blocker_release_is_scoped_and_refuses_interpolated_input(self):
        runtime = transport.Rehearsal.__new__(transport.Rehearsal)
        runtime.sql = Mock(return_value="t")
        for invalid in (None, "1 OR TRUE", "-1", "1; DROP TABLE x"):
            with self.assertRaises(ValueError):
                runtime.release_blocker(invalid)
        runtime.sql.assert_not_called()
        runtime.release_blocker("123")
        query = runtime.sql.call_args.args[0]
        for bound in ("pid=123", "application_name='wsr_adr078_owned_blocker'", "usename='wsr'", "datname='wsr'"):
            self.assertIn(bound, query)

    def test_probe_and_control_accept_only_fixed_operations(self):
        runtime = transport.Rehearsal.__new__(transport.Rehearsal)
        runtime.docker = Mock()
        for method, value in ((runtime.control, "reset"), (runtime.control, "https://external"), (runtime.request, "/external")):
            with self.assertRaises(ValueError):
                method(value)
        runtime.docker.assert_not_called()

    def test_all_inherited_and_new_inputs_are_frozen_with_lf_normalization(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for path in (*safety.INPUTS, *transport.INPUTS):
                file = root / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(b"DEMO\r\n")
            runtime = transport.Rehearsal.__new__(transport.Rehearsal)
            runtime.inputs = transport.transport_inputs(root)
            self.assertEqual(len(runtime.inputs), 8)
            with patch.object(transport, "ROOT", root):
                for path in (*safety.INPUTS, *transport.INPUTS):
                    (root / path).write_bytes(b"DEMO\n")
                runtime.verify_inputs()
                (root / transport.INPUTS[1]).write_bytes(b"changed")
                with self.assertRaisesRegex(ValueError, "input changed"):
                    runtime.verify_inputs()

    def test_failure_still_captures_cleans_and_records_no_activation(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Mock()
            runtime.exercise.side_effect = ValueError("injected failure")
            runtime.report = Path(directory) / "report.json"
            runtime.checks, runtime.faults, runtime.inputs, runtime.images = [], [], {}, {}
            runtime.revision, runtime.recipe_hash = "a" * 40, "b" * 64
            with patch.object(sys, "argv", ["test", "--confirm-disposable-demo"]), patch.object(transport, "Rehearsal", return_value=runtime):
                with self.assertRaisesRegex(ValueError, "injected failure"):
                    transport.main()
            runtime.capture_logs.assert_called_once()
            runtime.cleanup.assert_called_once()
            report = json.loads(runtime.report.read_text())
            self.assertFalse(report["passed"])
            self.assertFalse(report["productionActivated"])
            self.assertFalse(report["endToEndDeadlineClaimed"])

    def test_real_loopback_relay_suite(self):
        node = shutil.which("node")
        self.assertIsNotNone(node, "Node is required for the actual loopback relay regression")
        result = subprocess.run([node, "--test", "scripts/ci/cpi-transport-relay.test.mjs"], cwd=ROOT,
            env=safety.archive.clean_environment(os.environ), encoding="utf-8", capture_output=True, timeout=35)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_cli_rejects_extra_arguments_without_echoing_them(self):
        node = shutil.which("node")
        self.assertIsNotNone(node)
        result = subprocess.run([node, "scripts/cpi-transport-relay.mjs", "--confirm-disposable-demo", "private-input"], cwd=ROOT,
            env=safety.archive.clean_environment(os.environ), encoding="utf-8", capture_output=True, timeout=5)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("private-input", result.stdout + result.stderr)
