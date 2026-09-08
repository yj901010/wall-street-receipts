"""Offline safety contracts for the opt-in worker and disposable Docker harness."""
import copy
from datetime import datetime, timezone
import importlib.util
import io
from pathlib import Path
from types import SimpleNamespace
import tempfile
import tarfile
import unittest
from unittest.mock import Mock, patch
import yaml

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


worker = load("cpi_worker_acceptance", "scripts/verify-cpi-worker.py")
fixture = load("cpi_worker_fixture", "scripts/cpi-worker-fixture.py")


class WorkerModelTests(unittest.TestCase):
    def test_opt_in_single_worker_never_changes_default_stack(self):
        model = yaml.safe_load((ROOT / "deploy/cpi-worker/compose.yaml").read_text())
        self.assertEqual(set(model), {"services", "secrets", "networks"})
        self.assertEqual(set(model["services"]), {"cpi-worker"})
        service = model["services"]["cpi-worker"]
        self.assertEqual(service["profiles"], ["cpi-worker"])
        self.assertEqual(service["command"], ["--wsr-schedule-bls-cpi"])
        self.assertEqual(service["entrypoint"], ["java", "-jar", "/opt/wsr/application.jar"])
        self.assertEqual(service["pull_policy"], "never")
        self.assertEqual(service["restart"], "no")
        self.assertTrue(service["read_only"])
        self.assertTrue(service["init"])
        self.assertEqual(service["user"], "10001:10001")
        self.assertEqual(service["cap_drop"], ["ALL"])
        self.assertEqual(service["security_opt"], ["no-new-privileges:true"])
        self.assertEqual(service["stop_grace_period"], "40s")
        self.assertEqual((service["mem_limit"], service["cpus"], service["pids_limit"]), ("512m", 0.5, 128))
        for forbidden in ("ports", "build", "privileged", "cap_add", "network_mode", "volumes", "env_file", "depends_on"):
            self.assertNotIn(forbidden, service)
        self.assertEqual(service["networks"], ["database", "provider"])
        self.assertEqual(set(model["networks"]), {"database", "provider"})
        for network in model["networks"].values():
            self.assertEqual(set(network), {"external", "name"})
            self.assertIs(network["external"], True)
            self.assertIn(":?", network["name"])

    def test_keys_are_files_not_values_and_host_permission_remapping_is_not_claimed(self):
        model = yaml.safe_load((ROOT / "deploy/cpi-worker/compose.yaml").read_text())
        service = model["services"]["cpi-worker"]
        self.assertEqual(service["secrets"], [{"source": "bls_key", "target": "BLS_REGISTRATION_KEY"},
                                             {"source": "database_password", "target": "spring.datasource.password"}])
        for secret in model["secrets"].values():
            self.assertEqual(set(secret), {"file"})
            self.assertIn(":?", secret["file"])
        env = service["environment"]
        self.assertEqual(env["SPRING_CONFIG_LOCATION"], "classpath:/")
        self.assertEqual(env["SPRING_CONFIG_IMPORT"], "configtree:/run/secrets/")
        self.assertEqual(env["SPRING_MAIN_WEB_APPLICATION_TYPE"], "none")
        for key in ("BLS_REGISTRATION_KEY", "POSTGRES_PASSWORD", "SPRING_DATASOURCE_PASSWORD", "SPRING_PROFILES_ACTIVE"):
            self.assertNotIn(key, env)
        self.assertNotIn("trustStore", env["JAVA_TOOL_OPTIONS"])
        self.assertEqual(service["logging"], {"driver": "local", "options": {"max-size": "10m", "max-file": "3"}})


class HarnessSafetyTests(unittest.TestCase):
    def test_only_numeric_loopback_or_local_socket_endpoints_are_accepted(self):
        for endpoint in ("unix:///var/run/docker.sock", "npipe:////./pipe/dockerDesktopLinuxEngine",
                         "tcp://127.0.0.1:2375", "tcp://127.1.2.3:12345", "tcp://[::1]:2375"):
            self.assertTrue(worker.local_endpoint(endpoint), endpoint)
        for endpoint in ("tcp://example.com:2375", "ssh://local", "tcp://localhost:2375", "tcp://192.168.1.5:2375",
                         "tcp://127.999.1.1:2375", "tcp://127.0.0.1:0", "tcp://127.0.0.1:65536",
                         "tcp://127.0.0.1:2375/path", "tcp://user@127.0.0.1:2375", "tcp://127.0.0.1:2375?x=y",
                         "npipe://remote/pipe/docker", "unix:///path\ncommand"):
            self.assertFalse(worker.local_endpoint(endpoint), endpoint)

    def test_environment_drops_keys_profiles_proxies_and_compose_docker_overrides(self):
        env = {key: "not forwarded" for key in ("BLS_REGISTRATION_KEY", "POSTGRES_PASSWORD", "DOCKER_HOST", "DOCKER_CONTEXT",
               "DOCKER_CONFIG", "COMPOSE_FILE", "COMPOSE_PROFILES", "WSR_CPI_IMAGE", "SPRING_PROFILES_ACTIVE",
               "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "HTTP_PROXY", "https_proxy", "ALL_PROXY")}
        env.update(PATH="safe-path", SYSTEMROOT="system")
        self.assertEqual(worker.clean_environment(env), {"PATH": "safe-path", "SYSTEMROOT": "system"})

    def test_real_clock_slot_guard_avoids_startup_assertion_race(self):
        self.assertEqual(worker.future_slot(datetime(2026, 9, 8, 13, 54, tzinfo=timezone.utc)).isoformat(), "2026-09-08T14:00:00+00:00")
        with self.assertRaisesRegex(ValueError, "after the 23:00 KST"):
            worker.future_slot(datetime(2026, 9, 8, 13, 59, tzinfo=timezone.utc))
        self.assertEqual(worker.future_slot(datetime(2026, 9, 8, 14, 1, tzinfo=timezone.utc)).day, 9)

    def info(self):
        return {"Image": "test-image-id", "Config": {"User": "10001:10001", "Env": ["POSTGRES_HOST=postgres"]},
                "HostConfig": {"ReadonlyRootfs": True, "Init": True, "CapDrop": ["ALL"], "CapAdd": None,
                               "SecurityOpt": ["no-new-privileges:true"], "Privileged": False, "PortBindings": {},
                               "PublishAllPorts": False, "RestartPolicy": {"Name": "no"}, "Memory": 536870912,
                               "NanoCpus": 500000000, "PidsLimit": 128},
                "NetworkSettings": {"Networks": {"test-db": {}, "test-provider": {}}},
                "Mounts": [{"Destination": target, "RW": False, "Type": "bind"} for target in
                           ("/run/secrets/BLS_REGISTRATION_KEY", "/run/secrets/spring.datasource.password")]}

    def test_runtime_inspection_rejects_weakened_boundaries(self):
        info = self.info()
        worker.inspect_worker(info, "test-image-id", ["test-db", "test-provider"])
        mutations = [("ReadonlyRootfs", False), ("Init", False), ("CapDrop", []), ("CapAdd", ["NET_ADMIN"]),
                     ("Privileged", True), ("SecurityOpt", []), ("PortBindings", {"8080/tcp": [{}]}),
                     ("PublishAllPorts", True), ("RestartPolicy", {"Name": "always"}), ("Memory", 0),
                     ("NanoCpus", 0), ("PidsLimit", 0)]
        for key, value in mutations:
            altered = copy.deepcopy(info)
            altered["HostConfig"][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                worker.inspect_worker(altered, "test-image-id", ["test-db", "test-provider"])
        for section, key, value in [("Config", "User", "0"), ("Config", "Env", ["BLS_REGISTRATION_KEY=secret"]),
                                     ("Config", "Env", ["POSTGRES_PASSWORD=secret"]),
                                     ("NetworkSettings", "Networks", {"bridge": {}})]:
            altered = copy.deepcopy(info)
            altered[section][key] = value
            with self.assertRaises(ValueError):
                worker.inspect_worker(altered, "test-image-id", ["test-db", "test-provider"])
        for altered in ({**info, "Image": "other"}, {**info, "Mounts": []},
                        {**info, "Mounts": [{**item, "RW": True} for item in info["Mounts"]]}):
            with self.assertRaises(ValueError):
                worker.inspect_worker(altered, "test-image-id", ["test-db", "test-provider"])

    def test_logs_scan_both_stdout_and_stderr_without_echoing_secret(self):
        rehearsal = worker.Rehearsal.__new__(worker.Rehearsal)
        for stdout, stderr in [(worker.KEY, ""), ("", worker.KEY), ("", worker.PASSWORD)]:
            rehearsal.docker = Mock(return_value=SimpleNamespace(stdout=stdout, stderr=stderr))
            with self.assertRaisesRegex(ValueError, "Synthetic secret leaked"):
                rehearsal.logs("owned")

    def test_failed_commands_save_redacted_diagnostics(self):
        rehearsal = worker.Rehearsal.__new__(worker.Rehearsal)
        rehearsal.env = {}
        with tempfile.TemporaryDirectory(prefix="wsr-cpi-log-test-") as temp:
            rehearsal.log_path = Path(temp) / "test.log"
            result = SimpleNamespace(returncode=1, stdout="", stderr="failure " + worker.KEY + worker.PASSWORD)
            with patch.object(worker.subprocess, "run", return_value=result), self.assertRaises(ValueError):
                rehearsal.command(["test"])
            output = rehearsal.log_path.read_text()
            self.assertIn("failure", output)
            self.assertNotIn(worker.KEY, output)
            self.assertNotIn(worker.PASSWORD, output)

    def test_missing_docker_does_not_create_a_temporary_directory(self):
        with patch.object(worker.shutil, "which", return_value=None), patch.object(Path, "mkdir") as mkdir:
            with self.assertRaisesRegex(ValueError, "Docker CLI required"):
                worker.Rehearsal()
            mkdir.assert_not_called()

    def test_database_readiness_uses_final_tcp_server_not_temporary_init_socket(self):
        rehearsal = worker.Rehearsal.__new__(worker.Rehearsal)
        rehearsal.db = "owned-postgres"
        rehearsal.docker = Mock(return_value=SimpleNamespace(returncode=1))
        self.assertFalse(rehearsal.database_ready())
        rehearsal.docker.assert_called_once_with("exec", "owned-postgres", "pg_isready", "-h", "127.0.0.1",
                                                "-U", "wsr", "-d", "wsr", check=False)
        rehearsal.docker.return_value.returncode = 0
        self.assertTrue(rehearsal.database_ready())

    def test_negative_collection_cannot_pass_for_an_unrelated_crash(self):
        rehearsal = worker.Rehearsal.__new__(worker.Rehearsal)
        rehearsal.start_worker = Mock(return_value="owned-worker")
        rehearsal.docker = Mock(return_value=SimpleNamespace(stdout="1"))
        rehearsal.requests = Mock(return_value=1)
        with tempfile.TemporaryDirectory(prefix="wsr-cpi-exit-test-") as temp:
            rehearsal.log_path = Path(temp) / "test.log"
            rehearsal.logs = Mock(return_value="unrelated startup error")
            with self.assertRaisesRegex(ValueError, "unexpected reason"):
                rehearsal.manual("cooldown", "wsr", False, 1, "CPI collection cooldown is active")
            rehearsal.logs.return_value = "CPI collection cooldown is active"
            rehearsal.manual("cooldown", "wsr", False, 1, "CPI collection cooldown is active")

    def test_build_context_rejects_traversal_links_and_oversized_entries(self):
        for name, kind, size in [("../outside", tarfile.REGTYPE, 0), ("/absolute", tarfile.REGTYPE, 0),
                                 ("C:/outside", tarfile.REGTYPE, 0), ("a\\b", tarfile.REGTYPE, 0),
                                 ("link", tarfile.SYMTYPE, 0), ("hardlink", tarfile.LNKTYPE, 0),
                                 ("device", tarfile.CHRTYPE, 0), ("large", tarfile.REGTYPE, 8_000_001)]:
            with self.subTest(name=name), tempfile.TemporaryDirectory(prefix="wsr-cpi-archive-test-") as temp:
                root = Path(temp)
                archive = root / "input.tar"
                with tarfile.open(archive, "w") as bundle:
                    item = tarfile.TarInfo(name)
                    item.type, item.size = kind, size
                    # Python 3.13 requires a payload for a nonempty regular
                    # file. Exercise our size guard with a complete archive.
                    with io.BytesIO(b"\0" * size) as content:
                        bundle.addfile(item, content)
                reason = ("Build archive size exceeded" if size else
                          "Linked or special build archive entry" if kind != tarfile.REGTYPE else
                          "Unsafe build archive path")
                with self.assertRaisesRegex(ValueError, reason):
                    worker.export_build_context(archive, root / "context")
                self.assertEqual(list((root / "context").iterdir()), [])

    def test_build_context_exports_exact_git_archive_bytes_not_neighbor_files(self):
        with tempfile.TemporaryDirectory(prefix="wsr-cpi-export-test-") as temp:
            root = Path(temp)
            (root / ".env").write_text("synthetic neighbor must stay outside context")
            archive = root / "input.tar"
            raw = b"committed API source\n"
            with tarfile.open(archive, "w") as bundle:
                item = tarfile.TarInfo("apps/api/source.txt")
                item.size = len(raw)
                bundle.addfile(item, io.BytesIO(raw))
            worker.export_build_context(archive, root / "context")
            self.assertEqual((root / "context/apps/api/source.txt").read_bytes(), raw)
            self.assertFalse((root / "context/.env").exists())

    def test_recursive_cleanup_requires_exact_parent_name_and_marker(self):
        with tempfile.TemporaryDirectory(prefix="wsr-cpi-cleanup-test-") as temp:
            parent = Path(temp)
            target = parent / "wsr-cpi-token"
            target.mkdir()
            (target / ".owner").write_text("token")
            neighbor = parent / "preserve.txt"
            neighbor.write_text("user data")
            with self.assertRaises(ValueError):
                worker.remove_directory(target, parent, "wrong")
            (target / ".owner").write_text("wrong")
            with self.assertRaisesRegex(ValueError, "ownership"):
                worker.remove_directory(target, parent, "token")
            (target / ".owner").write_text("token")
            worker.remove_directory(target, parent, "token")
            self.assertFalse(target.exists())
            self.assertEqual(neighbor.read_text(), "user data")

    def test_docker_cleanup_does_not_remove_an_unowned_named_container(self):
        rehearsal = worker.Rehearsal.__new__(worker.Rehearsal)
        rehearsal.endpoint = "unix:///var/run/docker.sock"
        rehearsal.token = "owner"
        rehearsal.containers, rehearsal.networks, rehearsal.image = ["wsr-cpi-owner-test"], [], ""
        rehearsal.docker = Mock(return_value=SimpleNamespace(returncode=0, stdout=json_text({"Config": {"Labels": {worker.LABEL: "someone-else"}}})))
        with self.assertRaisesRegex(ValueError, "cleanup requires attention"):
            rehearsal.cleanup()
        rehearsal.docker.assert_called_once_with("container", "inspect", "wsr-cpi-owner-test", check=False)


def json_text(value):
    import json
    return json.dumps([value])


class FixtureTests(unittest.TestCase):
    def test_fixture_is_explicit_demo_and_rolls_prior_month_at_new_year(self):
        import json
        response = json.loads(fixture.response_bytes(datetime(2027, 1, 1, tzinfo=timezone.utc)))
        self.assertEqual(response["status"], "REQUEST_SUCCEEDED")
        self.assertEqual([row["seriesID"] for row in response["Results"]["series"]], ["CUUR0000SA0", "CUUR0000SA0L1E"])
        for series in response["Results"]["series"]:
            self.assertEqual(series["data"][0]["year"], "2026")
            self.assertEqual(series["data"][0]["period"], "M12")
            self.assertEqual(series["data"][1]["year"], "2025")
            self.assertIn("DEMO", series["data"][0]["footnotes"][0]["text"])

    def test_fixture_requires_the_real_clients_exact_bounded_request_shape(self):
        request = {"seriesid": ["CUUR0000SA0", "CUUR0000SA0L1E"], "startyear": "2023", "endyear": "2026",
                   "registrationkey": worker.KEY, "catalog": False, "calculations": False, "annualaverage": False, "aspects": False}
        self.assertTrue(fixture.valid_request(request, 2026))
        for key, value in [("registrationkey", "different"), ("seriesid", []), ("startyear", "2024"),
                           ("catalog", True), ("catalog", 0), ("extra", "field")]:
            self.assertFalse(fixture.valid_request({**request, key: value}, 2026))


if __name__ == "__main__":
    unittest.main()
