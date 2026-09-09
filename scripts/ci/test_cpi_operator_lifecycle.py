"""Offline safety tests for ADR-072; no daemon, network, provider or real secret."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
with patch.object(sys, "path", [str(ROOT / "scripts"), *sys.path]):
    import cpi_operator_lifecycle as safety
    spec = importlib.util.spec_from_file_location("operator_lifecycle_test", ROOT / "scripts/verify-cpi-operator-lifecycle.py")
    rehearsal = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rehearsal)


def runtime_info():
    return {"Image": "sha256:demo", "Config": {"User": "node", "Env": ["CPI_OPERATOR_UI_PORT=3000"]},
            "HostConfig": {"NetworkMode": "container:owned", "PortBindings": {}, "PublishAllPorts": False,
                           "ReadonlyRootfs": True, "Init": True, "CapDrop": ["ALL"], "CapAdd": None, "Privileged": False,
                           "SecurityOpt": ["no-new-privileges:true"], "RestartPolicy": {"Name": "no"},
                           "Memory": 1024 ** 3, "NanoCpus": 2_000_000_000, "PidsLimit": 256},
            "Mounts": [{"Type": "bind", "RW": False}, {"Type": "tmpfs", "RW": True}]}


class LifecycleSafetyTests(unittest.TestCase):
    def test_actual_runtime_requires_closed_network_and_hardening(self):
        safety.inspect_runtime(runtime_info(), "sha256:demo", "container:owned", "node")
        mutations = {"NetworkMode": "host", "PortBindings": {"3000/tcp": [{}]}, "PublishAllPorts": True,
                     "ReadonlyRootfs": False, "Init": False, "CapDrop": [], "CapAdd": ["SYS_ADMIN"], "Privileged": True,
                     "SecurityOpt": [], "RestartPolicy": {"Name": "always"}, "Memory": 0, "NanoCpus": 0, "PidsLimit": 0}
        for key, value in mutations.items():
            with self.subTest(key=key):
                info = runtime_info()
                info["HostConfig"][key] = value
                with self.assertRaises(ValueError):
                    safety.inspect_runtime(info, "sha256:demo", "container:owned", "node")

    def test_secrets_mounts_and_runtime_identity_cannot_drift(self):
        for field, value in (("Image", "other"), ("User", "root"), ("Env", ["BLS_REGISTRATION_KEY=demo"]),
                             ("Env", ["OPERATOR_API_TOKEN_SHA256=demo"]), ("Env", ["WSR_CPI_BROWSER_TOKEN=demo"]),
                             ("Mounts", [{"Type": "volume", "RW": False}]), ("Mounts", [{"Type": "bind", "RW": True}])):
            info = runtime_info()
            target = info["Config"] if field in ("User", "Env") else info
            target[field] = value
            with self.assertRaises(ValueError):
                safety.inspect_runtime(info, "sha256:demo", "container:owned", "node")

    def test_forced_kill_oom_and_wrong_incarnation_are_not_clean_shutdown(self):
        info = {"State": {"Status": "exited", "Running": False, "OOMKilled": False, "ExitCode": 0,
                          "StartedAt": "before", "FinishedAt": "after"}}
        safety.verify_stopped(info, "before", {0})
        for key, value in (("Status", "running"), ("Running", True), ("OOMKilled", True), ("ExitCode", 137),
                           ("StartedAt", "different"), ("FinishedAt", "0001-01-01T00:00:00Z")):
            broken = copy.deepcopy(info)
            broken["State"][key] = value
            with self.assertRaises(ValueError):
                safety.verify_stopped(broken, "before", {0})

    def test_cleanup_refuses_foreign_label_without_removing_anything(self):
        runtime = safety.Runtime.__new__(safety.Runtime)
        runtime.endpoint = "local"
        runtime.token = "owned"
        runtime.owned = {"container": ["owned-name"], "network": [], "image": []}
        runtime.docker = Mock(return_value=subprocess.CompletedProcess([], 0, json.dumps([{"Config": {"Labels": {safety.LABEL: "foreign"}}}])))
        with self.assertRaisesRegex(ValueError, "cleanup requires attention"):
            runtime.cleanup()
        self.assertEqual(runtime.docker.call_count, 1)
        self.assertEqual(runtime.docker.call_args.args[:2], ("container", "inspect"))

    def test_committed_build_inputs_exclude_environment_and_generated_declaration(self):
        self.assertNotIn("apps/web", rehearsal.WEB_PATHS)
        self.assertNotIn("apps/web/next-env.d.ts", rehearsal.WEB_PATHS)
        for path in rehearsal.API_PATHS + rehearsal.WEB_PATHS:
            self.assertNotIn(".env", path)
        recipe = (ROOT / "deploy/cpi-operator/Dockerfile").read_text()
        self.assertIn('CMD ["node", "operator/start.mjs"]', recipe)
        self.assertIn("STOPSIGNAL SIGTERM", recipe)
        self.assertNotIn("EXPOSE", recipe)
        self.assertNotIn("0.0.0.0", recipe)

    def test_confirmation_precedes_any_runtime_resource_creation(self):
        for args in ([], ["--db", "production"], ["--confirm-disposable-demo", "--anything"]):
            with patch.object(sys, "argv", ["test", *args]), patch.object(rehearsal, "Rehearsal") as runtime:
                with self.assertRaises(ValueError):
                    rehearsal.main()
                runtime.assert_not_called()

    def test_ambient_credentials_and_injection_are_not_inherited(self):
        values = {name: "secret" for name in ("NODE_OPTIONS", "_JAVA_OPTIONS", "BLS_REGISTRATION_KEY", "POSTGRES_PASSWORD",
                  "OPERATOR_API_TOKEN_SHA256", "DOCKER_HOST", "COMPOSE_FILE", "HTTP_PROXY")}
        values["PATH"] = "runtime"
        self.assertEqual(safety.archive.clean_environment(values), {"PATH": "runtime"})

    def test_rehearsal_inputs_are_frozen_and_lf_normalized(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in safety.INPUTS:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"DEMO\r\n")
            runtime = safety.Runtime.__new__(safety.Runtime)
            runtime.inputs = safety.input_hashes(root)
            for name in safety.INPUTS:
                (root / name).write_bytes(b"DEMO\n")
            with patch.object(safety, "ROOT", root):
                runtime.verify_inputs()
                (root / safety.INPUTS[0]).write_bytes(b"changed")
                with self.assertRaisesRegex(ValueError, "input changed"):
                    runtime.verify_inputs()

    def test_bounded_command_redacts_logs_and_transports_small_stdin(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = safety.Runtime.__new__(safety.Runtime)
            runtime.env = safety.archive.clean_environment(os.environ)
            runtime.redactions = ["demo-private-test"]
            runtime.log = Path(directory) / "output.log"
            result = runtime.command([sys.executable, "-c", "import sys; print(sys.stdin.read())"],
                                     input_text="demo-private-test", diagnostic=True)
            self.assertEqual(result.returncode, 0)
            self.assertNotIn("demo-private-test", runtime.log.read_text())
            with self.assertRaisesRegex(ValueError, "input limit"):
                runtime.command([], input_text="x" * 65537)
            with self.assertRaisesRegex(ValueError, "output limit"):
                runtime.command([sys.executable, "-c", "print('x' * 8000001)"])
            with self.assertRaisesRegex(ValueError, "timed out"):
                runtime.command([sys.executable, "-c", "import time; time.sleep(5)"], timeout=0.2)
