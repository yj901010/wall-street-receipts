#!/usr/bin/env python3
"""Disposable DEMO Docker acceptance; no real keys, host ports or BLS traffic.

Image preparation can download public images/Maven dependencies. All running
test containers use only two owned internal networks. Never use production
Compose, the repository .env, an existing database, or the host trust store.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
import hashlib
import importlib.util
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tarfile
import time
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
LABEL = "com.wallstreetreceipts.cpi-rehearsal"
KEY = "SyntheticCpiContainerKeyOnly"
PASSWORD = "SyntheticCpiDatabasePasswordOnly"
TRUST_PASSWORD = "demo-trust-only"
PYTHON_IMAGE = "python:3.13-alpine"
POSTGRES_IMAGE = "postgres:17-alpine"


def require(condition, message):
    if not condition:
        raise ValueError(message)


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


def regular_tree(root):
    for path in [root, *root.rglob("*")]:
        info = path.lstat()
        require(not path.is_symlink() and not (getattr(info, "st_file_attributes", 0)
                & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0)), "Linked rehearsal path rejected")


def remove_directory(root, parent, token):
    require(root.parent == parent and root.name == "wsr-cpi-" + token,
            "Rehearsal directory escaped its owned parent")
    regular_tree(root)
    require((root / ".owner").read_text() == token, "Rehearsal directory ownership mismatch")
    shutil.rmtree(root)


def future_slot(now):
    candidate = now.astimezone(timezone.utc).replace(hour=14, minute=0, second=0, microsecond=0)
    if candidate <= now:
        candidate += timedelta(days=1)
    # The actual worker is not given a fake Clock or altered cron. Abort near
    # the slot instead of making startup/no-HTTP assertions race the real timer.
    require(candidate - now >= timedelta(minutes=5), "Re-run the rehearsal after the 23:00 KST slot")
    return candidate


def export_build_context(archive, destination):
    """Extract only ordinary relative Git archive entries, never links/devices."""
    destination.mkdir()
    total = 0
    with tarfile.open(archive, "r:") as bundle:
        for item in bundle:
            name = item.name.rstrip("/")
            require(name and not name.startswith("/") and "\\" not in name and ":" not in name
                    and all(part not in ("", ".", "..") for part in name.split("/")), "Unsafe build archive path")
            require(item.isdir() or item.isfile(), "Linked or special build archive entry")
            total += item.size
            require(0 <= item.size <= 8_000_000 and total <= 64_000_000, "Build archive size exceeded")
            target = destination / name
            require(target.resolve().is_relative_to(destination.resolve()), "Build archive escaped context")
            if item.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.extractfile(item) as source, target.open("xb") as output:
                    shutil.copyfileobj(source, output)


def inspect_worker(info, image_id, networks):
    config, host = info["Config"], info["HostConfig"]
    require(info["Image"] == image_id, "Worker image identity changed")
    require(config["User"] == "10001:10001" and host["ReadonlyRootfs"], "Worker user/read-only boundary failed")
    require(host["Init"] is True and host["CapDrop"] == ["ALL"] and not host.get("CapAdd"), "Worker capabilities changed")
    require("no-new-privileges:true" in host["SecurityOpt"] and not host["Privileged"], "Worker privilege boundary failed")
    require(not host.get("PortBindings") and not host.get("PublishAllPorts"), "Worker published a host port")
    require(host["RestartPolicy"]["Name"] == "no", "Worker restart policy changed")
    require(host["Memory"] == 512 * 1024 * 1024 and host["NanoCpus"] == 500000000
            and host["PidsLimit"] == 128, "Worker resource bounds changed")
    require(set(info["NetworkSettings"]["Networks"]) == set(networks), "Worker network isolation changed")
    require(not any(line.startswith(("BLS_REGISTRATION_KEY=", "POSTGRES_PASSWORD=", "SPRING_DATASOURCE_PASSWORD="))
                    for line in config["Env"]), "Secret value configured as container environment")
    mounted = {item["Destination"]: item for item in info["Mounts"]}
    for target in ("/run/secrets/BLS_REGISTRATION_KEY", "/run/secrets/spring.datasource.password"):
        require(target in mounted and not mounted[target]["RW"], "Missing or writable worker secret")
    require(not any(item["Type"] == "volume" for item in info["Mounts"]), "Worker acquired persistent volume")


class Rehearsal:
    def __init__(self):
        self.docker_path = shutil.which("docker")
        require(self.docker_path is not None, "Docker CLI required")
        self.token = secrets.token_hex(12)
        self.prefix = "wsr-cpi-" + self.token
        self.cache = ROOT / ".cache"
        self.cache.mkdir(exist_ok=True)
        require(self.cache.resolve() == self.cache and not self.cache.is_symlink(), "Linked cache root rejected")
        self.directory = self.cache / self.prefix
        self.directory.mkdir()
        (self.directory / ".owner").write_text(self.token)
        self.log_path = self.cache / ("adr066-" + self.token + ".log")
        self.report_path = self.cache / ("adr066-" + self.token + ".json")
        self.env = clean_environment(os.environ)
        self.endpoint = ""
        self.containers = []
        self.networks = []
        self.image = ""
        self.image_id = ""
        self.results = []

    def command(self, args, timeout=60, check=True, diagnostic=False, input_text=None):
        try:
            result = subprocess.run(args, cwd=ROOT, env=self.env, input=input_text,
                                    text=True, encoding="utf-8", errors="replace",
                                    capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise ValueError("Bounded rehearsal command timed out") from None
        require(len(result.stdout) + len(result.stderr) <= 8_000_000, "Rehearsal output bound exceeded")
        if diagnostic or (check and result.returncode):
            with self.log_path.open("a", encoding="utf-8") as handle:
                output = result.stdout + result.stderr
                for value in (KEY, PASSWORD, TRUST_PASSWORD):
                    output = output.replace(value, "[synthetic secret redacted]")
                handle.write(output)
        if check:
            require(result.returncode == 0, "Rehearsal command failed; inspect the owned diagnostic log")
        return result

    def docker(self, *args, **options):
        return self.command([self.docker_path, "--host", self.endpoint, *args], **options)

    def inspect(self, kind, name):
        return json.loads(self.docker(kind, "inspect", name).stdout)[0]

    def own_container(self, name):
        require(name.startswith(self.prefix + "-"), "Unexpected container name")
        self.containers.append(name)
        return name

    def stage(self, name):
        self.results.append(name)
        print("DEMO CPI rehearsal: " + name, flush=True)

    def image_identity(self, image):
        existing = self.docker("image", "inspect", image, check=False)
        if existing.returncode:
            self.docker("pull", image, timeout=600, diagnostic=True)
        identity = self.inspect("image", image)["Id"]
        require(re.fullmatch(r"sha256:[0-9a-f]{64}", identity), "Invalid local image identity")
        return identity

    def prepare(self):
        if os.environ.get("DOCKER_CONTEXT"):
            endpoint = self.command([self.docker_path, "context", "inspect", os.environ["DOCKER_CONTEXT"],
                                     "--format", "{{.Endpoints.docker.Host}}"] ).stdout.strip()
        elif os.environ.get("DOCKER_HOST"):
            endpoint = os.environ["DOCKER_HOST"]
        else:
            endpoint = self.command([self.docker_path, "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"] ).stdout.strip()
        require(local_endpoint(endpoint), "Remote Docker endpoint rejected")
        self.endpoint = endpoint
        # A private CLI config has no stored registry credentials or contexts.
        # Docker Desktop's plugin path otherwise depends on host configuration.
        config = self.directory / "docker-config"
        config.mkdir()
        plugin_dir = Path(self.docker_path).parent.parent / "cli-plugins"
        settings = {"cliPluginsExtraDirs": [str(plugin_dir)]} if plugin_dir.is_dir() else {}
        (config / "config.json").write_text(json.dumps(settings))
        self.env["DOCKER_CONFIG"] = str(config)
        require(self.docker("info", "--format", "{{.OSType}}").stdout.strip() == "linux", "Linux Docker engine required")
        self.docker("compose", "version")
        self.stage("preparing public images (this build phase may use network)")
        self.python_id = self.image_identity(PYTHON_IMAGE)
        self.postgres_id = self.image_identity(POSTGRES_IMAGE)
        revision = self.command(["git", "rev-parse", "HEAD"]).stdout.strip()
        require(re.fullmatch(r"[0-9a-f]{40}", revision), "Invalid source revision")
        image_paths = ["apps/api/src", "apps/api/.mvn", "apps/api/pom.xml", "apps/api/mvnw",
                       "fixtures/v1", "deploy/home-server/api.Dockerfile", "deploy/home-server/api.Dockerfile.dockerignore"]
        for relative in image_paths:
            regular_tree(ROOT / relative)
        require(not self.command(["git", "diff", "--no-ext-diff", "--no-textconv", "--name-only", "HEAD", "--", *image_paths]).stdout,
                "Commit reviewed API image sources before container acceptance")
        require(not self.command(["git", "ls-files", "--others", "--", *image_paths]).stdout,
                "Untracked files in API build input rejected, including ignored files")
        archive = self.directory / "api-input.tar"
        self.command(["git", "archive", "--format=tar", "--output", str(archive), revision, "--", *image_paths])
        context = self.directory / "build-context"
        export_build_context(archive, context)
        # Also support legacy Docker builders that ignore Dockerfile-specific
        # ignore files: the repository root is NEVER a build context.
        shutil.copyfile(context / "deploy/home-server/api.Dockerfile.dockerignore", context / ".dockerignore")
        require(not (context / ".env").exists(), "Unexpected environment file in build context")
        self.image = self.prefix + ":api"
        self.docker("build", "--pull=false", "--file", str(context / "deploy/home-server/api.Dockerfile"),
                    "--build-arg", "WSR_GIT_SHA=" + revision, "--label", LABEL + "=" + self.token,
                    "--tag", self.image, str(context), timeout=900, diagnostic=True)
        built = self.inspect("image", self.image)
        require(built["Config"]["Labels"][LABEL] == self.token, "Built image ownership mismatch")
        require(built["Config"]["Labels"]["org.opencontainers.image.revision"] == revision, "Built source revision mismatch")
        self.image_id = built["Id"]
        self.stage("actual API runtime image built from committed-only isolated context")

    def fixtures(self):
        self.fixture = self.directory / "fixture"
        self.fixture.mkdir()
        self.secrets_dir = self.directory / "secrets"
        self.secrets_dir.mkdir()
        (self.secrets_dir / "bls").write_text(KEY)
        (self.secrets_dir / "database").write_text(PASSWORD)
        openssl = shutil.which("openssl")
        if not openssl and os.name == "nt":
            candidate = Path("C:/Program Files/Git/usr/bin/openssl.exe")
            if candidate.is_file():
                openssl = str(candidate)
        keytool = shutil.which("keytool")
        require(openssl and keytool, "OpenSSL and Java keytool required for temporary test TLS")
        self.command([openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "2",
                      "-subj", "/CN=api.bls.gov", "-addext", "subjectAltName=DNS:api.bls.gov",
                      "-keyout", str(self.fixture / "key.pem"), "-out", str(self.fixture / "cert.pem")])
        self.command([keytool, "-importcert", "-noprompt", "-alias", "demo-cpi-only", "-file", str(self.fixture / "cert.pem"),
                      "-keystore", str(self.directory / "trust.p12"), "-storetype", "PKCS12", "-storepass", TRUST_PASSWORD])
        (self.fixture / "mode").write_text("success")
        # All values here are synthetic. Production secret files require host
        # ownership for UID 10001; Compose file secrets do not remap permissions.
        for path in [*self.secrets_dir.iterdir(), *self.fixture.iterdir(), self.directory / "trust.p12"]:
            path.chmod(0o444)
        for suffix in ("database", "provider"):
            name = self.prefix + "-" + suffix
            self.networks.append(name)
            self.docker("network", "create", "--internal", "--label", LABEL + "=" + self.token, name)
            info = self.inspect("network", name)
            require(info["Internal"] and info["Labels"][LABEL] == self.token, "Runtime network is not owned/internal")
        self.db_network, self.provider_network = self.networks
        self.db = self.own_container(self.prefix + "-postgres")
        self.docker("run", "-d", "--name", self.db, "--label", LABEL + "=" + self.token,
                    "--network", self.db_network, "--network-alias", "postgres", "--restart=no",
                    "--memory", "512m", "--pids-limit", "128", "--tmpfs", "/var/lib/postgresql/data:rw,size=256m",
                    "--mount", f"type=bind,source={self.secrets_dir / 'database'},target=/run/secrets/db,readonly",
                    "-e", "POSTGRES_USER=wsr", "-e", "POSTGRES_DB=wsr",
                    "-e", "POSTGRES_PASSWORD_FILE=/run/secrets/db", self.postgres_id)
        self.await_ready(self.database_ready)
        for database in ("schedule_test", "limited_test", "malformed_test"):
            self.sql("wsr", "CREATE DATABASE " + database)
        self.provider = self.own_container(self.prefix + "-fixture")
        self.docker("run", "-d", "--name", self.provider, "--label", LABEL + "=" + self.token,
                    "--network", self.provider_network, "--network-alias", "api.bls.gov", "--restart=no",
                    "--read-only", "--user", "10001:10001", "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
                    "--sysctl", "net.ipv4.ip_unprivileged_port_start=0", "--memory", "128m", "--pids-limit", "64",
                    "--mount", f"type=bind,source={self.fixture},target=/fixture,readonly",
                    "--mount", f"type=bind,source={ROOT / 'scripts/cpi-worker-fixture.py'},target=/fixture-server.py,readonly",
                    self.python_id, "python", "-B", "/fixture-server.py")
        self.await_ready(lambda: "DEMO_CPI_FIXTURE_READY" in self.logs(self.provider))
        self.stage("two internal networks, disposable PostgreSQL and private HTTPS fixture ready")

    def await_ready(self, predicate, seconds=90):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            if predicate():
                return
            time.sleep(0.5)
        raise ValueError("Rehearsal readiness deadline exceeded")

    def database_ready(self):
        # The official image briefly starts a Unix-socket-only init server.
        # TCP readiness identifies the final server, not that transient phase.
        return self.docker("exec", self.db, "pg_isready", "-h", "127.0.0.1", "-U", "wsr", "-d", "wsr", check=False).returncode == 0

    def logs(self, name):
        result = self.docker("logs", "--tail", "500", name)
        output = result.stdout + result.stderr
        require(KEY not in output and PASSWORD not in output, "Synthetic secret leaked in container logs")
        return output

    def sql(self, database, query):
        return self.docker("exec", "-i", self.db, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                           "-U", "wsr", "-d", database, input_text=query + ";\n").stdout.strip()

    def compose(self, database, *args):
        env_file = self.directory / "worker.env"
        values = {"WSR_CPI_IMAGE": self.image, "WSR_CPI_DB_HOST": "postgres", "WSR_CPI_DB_NAME": database,
                  "WSR_CPI_DB_USER": "wsr", "WSR_CPI_BLS_KEY_FILE": (self.secrets_dir / "bls").as_posix(),
                  "WSR_CPI_DB_PASSWORD_FILE": (self.secrets_dir / "database").as_posix(),
                  "WSR_CPI_DB_NETWORK": self.db_network, "WSR_CPI_PROVIDER_NETWORK": self.provider_network}
        env_file.write_text("\n".join(key + "=" + value for key, value in values.items()) + "\n")
        override = self.directory / "test-only.json"
        override.write_text(json.dumps({"services": {"cpi-worker": {
            "labels": {LABEL: self.token}, "environment": {"JAVA_TOOL_OPTIONS":
                "-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=UTC "
                "-Djavax.net.ssl.trustStore=/test-trust.p12 -Djavax.net.ssl.trustStorePassword=" + TRUST_PASSWORD},
            "volumes": [{"type": "bind", "source": str(self.directory / "trust.p12"),
                         "target": "/test-trust.p12", "read_only": True}]}}}))
        return self.docker("compose", "--project-name", self.prefix, "--env-file", str(env_file),
                           "--file", str(ROOT / "deploy/cpi-worker/compose.yaml"), "--file", str(override),
                           "--profile", "cpi-worker", *args, timeout=120, diagnostic=True)

    def start_worker(self, suffix, database, manual=False):
        name = self.own_container(self.prefix + "-" + suffix)
        args = ["run", "--detach", "--no-deps", "--name", name, "cpi-worker"]
        if manual:
            args.append("--wsr-collect-bls-cpi")
        self.compose(database, *args)
        inspect_worker(self.inspect("container", name), self.image_id, self.networks)
        return name

    def requests(self):
        return self.logs(self.provider).count("DEMO_CPI_FIXTURE_REQUEST ")

    def mode(self, value):
        path = self.fixture / "mode"
        path.chmod(0o644)
        path.write_text(value)
        path.chmod(0o444)

    def manual(self, suffix, database, success, expected_requests, failure_marker=None):
        name = self.start_worker(suffix, database, manual=True)
        result = self.docker("wait", name, timeout=90).stdout.strip()
        require((result == "0") == success, "Manual collector exit status mismatch")
        logs = self.logs(name)
        with self.log_path.open("a", encoding="utf-8") as handle:
            handle.write(logs)
        require(("BLS_CPI_CAPTURE_SAVED " in logs) == success, "False/missing successful capture marker")
        if not success:
            require(failure_marker and failure_marker in logs, "Collector failed for an unexpected reason")
        require(self.requests() == expected_requests, "Unexpected HTTP request count or retry")

    def exercise(self):
        expected = future_slot(datetime.now(timezone.utc))
        worker = self.start_worker("scheduled", "schedule_test")
        self.await_ready(lambda: "BLS_CPI_SCHEDULE_READY" in self.logs(worker))
        expected_kst = (expected + timedelta(hours=9)).strftime("%Y-%m-%d 23:00:00 KST")
        require("next=" + expected_kst in self.logs(worker), "Unexpected next KST slot")
        require(self.requests() == 0, "Scheduler fetched immediately at startup")
        require(self.sql("schedule_test", "SELECT count(*) FROM bls_cpi_captures") == "0", "Startup wrote a receipt")
        self.docker("stop", "--time", "40", worker)
        require(self.inspect("container", worker)["State"]["ExitCode"] in (0, 143), "Worker needed a forced kill")
        future_slot(datetime.now(timezone.utc))
        self.docker("start", worker)
        self.await_ready(lambda: self.logs(worker).count("BLS_CPI_SCHEDULE_READY") == 2)
        require(self.requests() == 0, "Restart replayed a missed/startup collection")
        self.docker("stop", "--time", "40", worker)
        require(self.inspect("container", worker)["State"]["ExitCode"] in (0, 143), "Restarted worker needed forced kill")
        self.stage("23:00 KST plan, no startup/restart HTTP, graceful shutdown verified")

        before = datetime.now(timezone.utc)
        self.manual("success", "wsr", True, 1)
        after = datetime.now(timezone.utc)
        raw = self.sql("wsr", "SELECT response_json FROM bls_cpi_captures")
        spec = importlib.util.spec_from_file_location("cpi_demo_fixture", ROOT / "scripts/cpi-worker-fixture.py")
        fixture = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(fixture)
        require(raw.encode() in (fixture.response_bytes(before), fixture.response_bytes(after)),
                "Stored bytes differ from the known synthetic response")
        require("DEMO offline CPI worker test; not BLS data" in raw, "Synthetic provenance missing")
        digest = hashlib.sha256(raw.encode()).hexdigest()
        require(self.sql("wsr", "SELECT response_sha256 FROM bls_cpi_captures") == digest, "Stored raw hash mismatch")
        require(self.sql("wsr", "SELECT count(*) FROM bls_cpi_captures") == "1", "Receipt count mismatch")
        cooldown = "CPI collection cooldown is active"
        self.manual("cooldown", "wsr", False, 1, cooldown)
        require(self.sql("wsr", "SELECT response_sha256 FROM bls_cpi_captures") == digest, "Cooldown mutated prior evidence")
        self.stage("real bounded Java HTTPS client -> synthetic receipt -> PostgreSQL hash replay verified")

        self.mode("rate-limit")
        self.manual("limited", "limited_test", False, 2, "BLS CPI rate limit reached; collection paused")
        require(self.sql("limited_test", "SELECT count(*) FROM bls_cpi_captures") == "0", "429 saved a false receipt")
        require(self.sql("limited_test", "SELECT next_allowed_at > now() + interval '47 hours' AND next_allowed_at < now() + interval '49 hours' FROM bls_cpi_collection_gate") == "t", "Retry-After not persisted")
        self.manual("limited-again", "limited_test", False, 2, cooldown)
        self.mode("malformed")
        self.manual("malformed", "malformed_test", False, 3, "BLS CPI response failed validation")
        require(self.sql("malformed_test", "SELECT count(*) FROM bls_cpi_captures") == "0", "Malformed response saved a false receipt")
        self.manual("malformed-again", "malformed_test", False, 3, cooldown)
        self.stage("429/48-hour gate, malformed response and no immediate retry verified")
        for name in self.containers:
            info = self.inspect("container", name)
            require(not info["HostConfig"].get("PortBindings"), "Test published a host port")
            require(set(info["NetworkSettings"]["Networks"]) <= set(self.networks), "Runtime acquired external network")
        for name in self.networks:
            require(self.inspect("network", name)["Internal"], "Runtime isolation lost")

    def cleanup(self):
        failures = []
        if self.endpoint:
            for kind, names in (("container", reversed(self.containers)), ("network", reversed(self.networks)), ("image", [self.image] if self.image else [])):
                for name in names:
                    try:
                        probe = self.docker(kind, "inspect", name, check=False)
                        if probe.returncode:
                            continue
                        info = json.loads(probe.stdout)[0]
                        labels = info.get("Config", {}).get("Labels") if kind != "network" else info.get("Labels")
                        require(labels and labels.get(LABEL) == self.token, "Cleanup ownership mismatch")
                        if kind == "container":
                            self.docker("container", "rm", "--force", name)
                        else:
                            self.docker(kind, "rm", name)
                    except (ValueError, OSError):
                        failures.append(kind + ":" + name)
        if not failures:
            regular_tree(self.directory)
            for path in self.directory.rglob("*"):
                if path.is_file():
                    path.chmod(0o600)
            remove_directory(self.directory, self.cache, self.token)
        require(not failures, "Owned cleanup requires attention: " + ", ".join(failures))


def main():
    require(len(sys.argv) == 1, "This disposable rehearsal accepts no external configuration or arguments")
    rehearsal = Rehearsal()
    passed = False
    try:
        rehearsal.prepare()
        rehearsal.fixtures()
        rehearsal.exercise()
        passed = True
    finally:
        try:
            rehearsal.cleanup()
        except (ValueError, OSError):
            passed = False
            raise
        finally:
            report = {"dataMode": "DEMO", "passed": passed, "realProviderRequests": 0 if passed else None,
                      "productionActivated": False, "checks": rehearsal.results,
                      "runtimeImage": rehearsal.image_id, "note": "Synthetic offline acceptance, not observed BLS evidence"}
            rehearsal.report_path.write_text(json.dumps(report, indent=2) + "\n")
            print("DEMO report: " + str(rehearsal.report_path), flush=True)
    print("PASS: DEMO CPI worker Docker acceptance; owned test resources removed", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
