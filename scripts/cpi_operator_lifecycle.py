"""Owned Docker/build safety for the explicit ADR-072 DEMO acceptance."""
from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
LABEL = "com.wallstreetreceipts.cpi-operator-lifecycle"
INPUTS = ("deploy/cpi-operator/Dockerfile", "deploy/cpi-operator/Dockerfile.dockerignore",
          "scripts/cpi_operator_lifecycle.py", "scripts/verify-cpi-operator-lifecycle.py",
          "scripts/cpi-operator-lifecycle-probe.mjs")
spec = importlib.util.spec_from_file_location("cpi_lifecycle_archive", ROOT / "scripts/verify-cpi-worker.py")
archive = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive)
require = archive.require


def input_hashes(root):
    import hashlib
    return {path: hashlib.sha256((root / path).read_bytes().replace(b"\r\n", b"\n")).hexdigest() for path in INPUTS}


def inspect_runtime(info, image, network, user):
    config, host = info["Config"], info["HostConfig"]
    require(info["Image"] == image and config["User"] == user, "Runtime identity/user changed")
    require(host["NetworkMode"] == network and not host.get("PortBindings")
            and not host.get("PublishAllPorts"), "Runtime network/port isolation changed")
    require(host["ReadonlyRootfs"] and host["Init"] and host["CapDrop"] == ["ALL"]
            and not host.get("CapAdd") and not host["Privileged"]
            and "no-new-privileges:true" in host["SecurityOpt"], "Runtime hardening changed")
    require(host["RestartPolicy"]["Name"] == "no" and host["Memory"] == 1024 ** 3
            and host["NanoCpus"] == 2_000_000_000 and host["PidsLimit"] == 256, "Runtime limits changed")
    require(not any(item["Type"] == "volume" or item["Type"] == "bind" and item["RW"]
                    for item in info["Mounts"]), "Unexpected persistent/writable mount")
    forbidden = ("BLS_REGISTRATION_KEY=", "POSTGRES_PASSWORD=", "SPRING_DATASOURCE_PASSWORD=",
                 "OPERATOR_API_TOKEN_SHA256=", "WSR_CPI_BROWSER_TOKEN=")
    require(not any(value.startswith(forbidden) for value in config["Env"]), "Secret configured as runtime environment")


def verify_stopped(info, previous_start, allowed):
    state = info["State"]
    require(state["Status"] == "exited" and not state["Running"] and not state["OOMKilled"]
            and state["ExitCode"] in allowed and state["StartedAt"] == previous_start
            and state["FinishedAt"] != "0001-01-01T00:00:00Z", "Process did not stop cleanly")


class Runtime:
    def __init__(self):
        self.docker_path = shutil.which("docker")
        require(self.docker_path, "Docker CLI required")
        self.token = secrets.token_hex(12)
        self.prefix = "wsr-cpi-lifecycle-" + self.token
        self.cache = ROOT / ".cache"
        self.cache.mkdir(exist_ok=True)
        require(not self.cache.is_symlink() and self.cache.resolve() == self.cache, "Linked cache root rejected")
        self.directory = self.cache / self.prefix
        self.directory.mkdir()
        (self.directory / ".owner").write_text(self.token)
        self.log = self.cache / ("adr072-" + self.token + ".log")
        self.report = self.cache / ("adr072-" + self.token + ".json")
        self.env = archive.clean_environment(os.environ)
        self.endpoint = ""
        self.owned = {"container": [], "network": [], "image": []}
        self.redactions = []
        self.checks = []
        self.images = {}
        self.inputs = input_hashes(ROOT)

    def command(self, args, *, timeout=60, check=True, input_text=None, diagnostic=False):
        require(input_text is None or len(input_text.encode()) <= 65536, "Command input limit exceeded")
        with tempfile.TemporaryFile() as output:
            process = subprocess.Popen(args, cwd=ROOT, env=self.env, stdin=subprocess.PIPE if input_text is not None
                                       else subprocess.DEVNULL, stdout=output, stderr=output)
            deadline = time.monotonic() + timeout
            try:
                if input_text is not None:
                    process.stdin.write(input_text.encode())
                    process.stdin.close()
                while process.poll() is None:
                    require(time.monotonic() < deadline, "Bounded lifecycle command timed out")
                    require(os.fstat(output.fileno()).st_size <= 8_000_000, "Lifecycle output limit exceeded")
                    time.sleep(0.05)
                require(os.fstat(output.fileno()).st_size <= 8_000_000, "Lifecycle output limit exceeded")
                output.seek(0)
                text = output.read().decode("utf-8", errors="replace")
            finally:
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=5)
                if process.stdin and not process.stdin.closed:
                    process.stdin.close()
        if diagnostic or check and process.returncode:
            sanitized = text
            for secret in self.redactions:
                sanitized = sanitized.replace(secret, "[DEMO secret redacted]")
            with self.log.open("a", encoding="utf-8") as handle:
                handle.write(sanitized)
        require(not check or process.returncode == 0, "Lifecycle command failed; inspect owned diagnostic log")
        return subprocess.CompletedProcess(args, process.returncode, text)

    def docker(self, *args, **kwargs):
        return self.command([self.docker_path, "--host", self.endpoint, *args], **kwargs)

    def inspect(self, kind, name):
        return json.loads(self.docker(kind, "inspect", name).stdout)[0]

    def own(self, kind, suffix):
        name = self.prefix + (":" if kind == "image" else "-") + suffix
        self.owned[kind].append(name)
        return name

    def stage(self, text):
        self.checks.append(text)
        print("DEMO lifecycle: " + text, flush=True)

    def verify_inputs(self):
        require(self.inputs == input_hashes(ROOT), "Rehearsal input changed during execution")

    def prepare(self):
        if os.environ.get("DOCKER_CONTEXT"):
            endpoint = self.command([self.docker_path, "context", "inspect", os.environ["DOCKER_CONTEXT"],
                                     "--format", "{{.Endpoints.docker.Host}}"] ).stdout.strip()
        elif os.environ.get("DOCKER_HOST"):
            endpoint = os.environ["DOCKER_HOST"]
        else:
            endpoint = self.command([self.docker_path, "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"] ).stdout.strip()
        require(archive.local_endpoint(endpoint), "Remote Docker endpoint rejected")
        self.endpoint = endpoint
        config = self.directory / "docker-config"
        config.mkdir()
        plugins = Path(self.docker_path).parent.parent / "cli-plugins"
        (config / "config.json").write_text(json.dumps({"cliPluginsExtraDirs": [str(plugins)]} if plugins.is_dir() else {}))
        self.env["DOCKER_CONFIG"] = str(config)
        require(self.docker("info", "--format", "{{.OSType}}").stdout.strip() == "linux", "Local Linux Docker required")
        self.revision = self.command(["git", "rev-parse", "HEAD"]).stdout.strip()
        require(re.fullmatch("[0-9a-f]{40}", self.revision), "Invalid revision")

    def context(self, name, paths):
        for relative in paths:
            archive.regular_tree(ROOT / relative)
        require(not self.command(["git", "diff", "--name-only", "HEAD", "--", *paths]).stdout,
                "Commit reviewed runtime input before packaged acceptance")
        require(not self.command(["git", "ls-files", "--others", "--", *paths]).stdout,
                "Untracked runtime input rejected, including ignored files")
        tar = self.directory / (name + ".tar")
        self.command(["git", "archive", "--format=tar", "--output", str(tar), self.revision, "--", *paths])
        context = self.directory / name
        archive.export_build_context(tar, context)
        return context

    def build(self, name, context, dockerfile, *extra):
        image = self.own("image", name)
        self.stage("building " + name + " from isolated inputs (public dependencies may download)")
        self.docker("build", "--pull=false", "--file", str(dockerfile), "--build-arg", "WSR_GIT_SHA=" + self.revision,
                    "--label", LABEL + "=" + self.token, "--tag", image, *extra, str(context), timeout=1200, diagnostic=True)
        info = self.inspect("image", image)
        require(info["Config"]["Labels"].get(LABEL) == self.token and
                info["Config"]["Labels"].get("org.opencontainers.image.revision") == self.revision, "Image provenance changed")
        self.images[name] = info["Id"]
        return image

    def await_ready(self, predicate, seconds=90):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(0.5)
        raise ValueError("Lifecycle readiness deadline exceeded")

    def cleanup(self):
        failures = []
        if self.endpoint:
            for kind in ("container", "network", "image"):
                for name in reversed(self.owned[kind]):
                    try:
                        result = self.docker(kind, "inspect", name, check=False)
                        if result.returncode:
                            require("No such" in result.stdout, "Cannot verify cleanup target")
                            continue
                        info = json.loads(result.stdout)[0]
                        labels = info.get("Labels") if kind == "network" else info.get("Config", {}).get("Labels")
                        require(labels and labels.get(LABEL) == self.token, "Cleanup ownership mismatch")
                        self.docker(kind, "rm", *( ["--force"] if kind == "container" else []), name)
                    except (ValueError, OSError, subprocess.SubprocessError):
                        failures.append(kind + ":" + name)
        require(not failures, "Owned cleanup requires attention: " + ", ".join(failures))
        require(self.directory.parent == self.cache and self.directory.name == self.prefix
                and (self.directory / ".owner").read_text() == self.token, "Directory ownership mismatch")
        archive.regular_tree(self.directory)
        for path in self.directory.rglob("*"):
            if path.is_file():
                path.chmod(0o600)
        shutil.rmtree(self.directory)

    def capture_logs(self):
        for name in self.owned["container"]:
            probe = self.docker("container", "inspect", name, check=False)
            if probe.returncode:
                continue
            require(json.loads(probe.stdout)[0]["Config"]["Labels"].get(LABEL) == self.token, "Log ownership mismatch")
            state = json.loads(probe.stdout)[0]["State"]
            with self.log.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps({"container": name, "status": state["Status"], "exitCode": state["ExitCode"],
                                         "oomKilled": state["OOMKilled"]}) + "\n")
            self.docker("logs", "--tail", "500", name, check=False, diagnostic=True)
