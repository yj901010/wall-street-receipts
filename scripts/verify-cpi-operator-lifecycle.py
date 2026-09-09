#!/usr/bin/env python3
"""Explicit ADR-072 disposable packaged Linux process acceptance; no real keys or host ports."""
from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
import secrets
import shutil
import sys

from cpi_operator_lifecycle import ROOT, LABEL, Runtime, require, inspect_runtime, verify_stopped

PASSWORD = "DisposableCpiLifecycleDatabaseOnly"
READER_PASSWORD = "DisposableCpiLifecycleReaderOnly"
API_PATHS = ["apps/api/src", "apps/api/.mvn", "apps/api/mvnw", "apps/api/pom.xml", "fixtures/v1",
             "deploy/home-server/api.Dockerfile", "deploy/home-server/api.Dockerfile.dockerignore"]
WEB_PATHS = ["package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml", "apps/web/src", "apps/web/operator",
             "apps/web/public", "apps/web/package.json", "apps/web/next.config.ts", "apps/web/tsconfig.json",
             "fixtures/v1", "deploy/home-server/web.Dockerfile", "deploy/home-server/web.Dockerfile.dockerignore"]


class Rehearsal(Runtime):
    def build_images(self):
        for kind, paths in (("api", API_PATHS), ("web", WEB_PATHS)):
            context = self.context(kind, paths)
            recipe = context / f"deploy/home-server/{kind}.Dockerfile"
            shutil.copyfile(recipe.with_suffix(".Dockerfile.dockerignore"), context / ".dockerignore")
            self.build(kind, context, recipe)
        # The new packaging recipe is tested before commit; embedded application
        # files come exclusively from the same committed Web build context.
        context = self.directory / "operator"
        context.mkdir()
        for relative in ("apps/web/operator/start.mjs", "apps/web/operator/gateway.ts", "apps/web/src/lib/operator-cpi.ts"):
            target = context / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(self.directory / "web" / relative, target)
        recipe = ROOT / "deploy/cpi-operator/Dockerfile"
        shutil.copyfile(recipe, context / "Dockerfile")
        shutil.copyfile(recipe.with_name("Dockerfile.dockerignore"), context / ".dockerignore")
        self.recipe_hash = hashlib.sha256(recipe.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
        self.build("operator", context, context / "Dockerfile", "--build-arg", "WSR_WEB_IMAGE=" + self.prefix + ":web")
        self.postgres = self.inspect("image", "postgres:17-alpine")["Id"]
        self.stage("packaged API JAR and operator image built; no working tree secrets used")

    def secret(self, name, value):
        directory = self.directory / name
        directory.mkdir()
        for key, content in value.items():
            target = directory / key
            target.write_text(content)
            target.chmod(0o444)  # DEMO only, readable to the container's non-root UID.
            self.redactions.append(content)
        return ["--mount", f"type=bind,source={directory},target=/run/secrets,readonly"]

    def sql(self, query, check=True):
        return self.docker("exec", "-i", self.db, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "wsr", "-d", "wsr",
                           input_text=query + ";\n", check=check).stdout.strip()

    def inventory(self):
        return [self.sql(f"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY {key}), '[]'::jsonb)::text FROM {table} t")
                for table, key in (("bls_cpi_collection_attempts", "attempt_id"), ("bls_cpi_collection_results", "attempt_id"),
                                   ("bls_cpi_captures", "capture_id"), ("bls_cpi_collection_gate", "singleton_id"))]

    def run_app(self, suffix, image, network, user, arguments, environment, mounts=()):
        name = self.own("container", suffix)
        self.docker("run", "-d", "--name", name, "--label", LABEL + "=" + self.token, "--network", network,
                    "--user", user, "--init", "--read-only", "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
                    "--restart=no", "--memory", "1g", "--cpus", "2", "--pids-limit", "256",
                    "--tmpfs", "/tmp:rw,size=128m,mode=1777", "--log-driver", "local",
                    "--log-opt", "max-size=10m", "--log-opt", "max-file=2", *mounts,
                    *[item for key, value in environment.items() for item in ("-e", key + "=" + value)], image, *arguments)
        inspect_runtime(self.inspect("container", name), image, network, user)
        return name

    def prepare_database(self):
        self.redactions.extend([PASSWORD, READER_PASSWORD])
        self.network = self.own("network", "internal")
        self.docker("network", "create", "--internal", "--label", LABEL + "=" + self.token, self.network)
        require(self.inspect("network", self.network)["Internal"], "Internal network required")
        self.db = self.own("container", "postgres")
        self.docker("run", "-d", "--name", self.db, "--label", LABEL + "=" + self.token, "--network", self.network,
                    "--network-alias", "postgres", "--restart=no", "--memory", "512m", "--pids-limit", "128",
                    "--tmpfs", "/var/lib/postgresql/data:rw,size=256m", *self.secret("owner", {"password": PASSWORD}),
                    "-e", "POSTGRES_PASSWORD_FILE=/run/secrets/password", "-e", "POSTGRES_USER=wsr", "-e", "POSTGRES_DB=wsr", self.postgres)
        self.await_ready(lambda: self.docker("exec", self.db, "pg_isready", "-h", "127.0.0.1", "-U", "wsr", check=False).returncode == 0)
        common = {"SPRING_CONFIG_LOCATION": "classpath:/", "SPRING_CONFIG_IMPORT": "configtree:/run/secrets/",
                  "POSTGRES_HOST": "postgres", "POSTGRES_DB": "wsr", "POSTGRES_USER": "wsr",
                  "MARKET_PROVIDER": "fixture", "ANALYST_PROVIDER": "disabled", "SEC_PROVIDER_ENABLED": "false",
                  "APP_CPI_ENABLED": "false", "TZ": "UTC", "JAVA_TOOL_OPTIONS": "-XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"}
        migration = self.run_app("migrate", self.images["api"], self.network, "10001:10001", [],
                                 {**common, "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": "8080", "OPERATOR_API_ENABLED": "false"},
                                 self.secret("migration", {"spring.datasource.password": PASSWORD}))
        # The normal application is a servlet API, not a headless migration CLI.
        # Use its real startup/migration path, with no published port or collector.
        self.await_ready(lambda: self.docker("exec", migration, "curl", "--fail", "--silent", "--max-time", "2",
                                            "http://127.0.0.1:8080/actuator/health", check=False).returncode == 0)
        self.stop(migration, {0, 143})
        self.sql("CREATE ROLE cpi_lifecycle_reader LOGIN PASSWORD '" + READER_PASSWORD + "'")
        self.sql("GRANT USAGE ON SCHEMA public TO cpi_lifecycle_reader")
        self.sql("GRANT SELECT ON bls_cpi_collection_attempts, bls_cpi_collection_results TO cpi_lifecycle_reader")
        self.sql("""INSERT INTO bls_cpi_collection_attempts VALUES
          ('00000000-0000-0000-0000-000000000001','MANUAL','2020-01-01T14:59:59.123456Z',TRUE),
          ('00000000-0000-0000-0000-000000000002','MANUAL','2020-01-01T14:59:58.123456Z',TRUE),
          ('00000000-0000-0000-0000-000000000003','SCHEDULED','2020-01-01T14:59:57.123456Z',TRUE)""")
        self.sql("""INSERT INTO bls_cpi_collection_results (attempt_id,started_at,permitted,completed_at,status,failure_code,retry_not_before) VALUES
          ('00000000-0000-0000-0000-000000000002','2020-01-01T14:59:58.123456Z',TRUE,'2020-01-01T15:00:00Z','FAILED','FETCH',NULL),
          ('00000000-0000-0000-0000-000000000003','2020-01-01T14:59:57.123456Z',TRUE,'2020-01-01T15:00:00Z','RATE_LIMITED',NULL,'2020-01-02T15:00:00Z')""")
        self.bearer = base64.b64encode(secrets.token_bytes(32)).decode()
        self.redactions.append(self.bearer)
        digest = hashlib.sha256(self.bearer.encode()).hexdigest()
        self.api = self.run_app("api", self.images["api"], self.network, "10001:10001", [],
                                {**common, "POSTGRES_USER": "cpi_lifecycle_reader", "SPRING_FLYWAY_ENABLED": "false",
                                 "OPERATOR_API_ENABLED": "true", "SERVER_ADDRESS": "0.0.0.0", "SERVER_PORT": "8080"},
                                self.secret("reader", {"spring.datasource.password": READER_PASSWORD, "app.operator-api.token-sha256": digest}))
        self.api_ready()
        require(self.sql("SELECT DISTINCT usename FROM pg_stat_activity WHERE application_name='PostgreSQL JDBC Driver'")
                == "cpi_lifecycle_reader", "Packaged API is not using the SELECT-only role")
        for statement in ("DELETE FROM bls_cpi_collection_attempts WHERE FALSE", "SELECT * FROM bls_cpi_captures"):
            denied = self.docker("exec", "-i", self.db, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "wsr", "-d", "wsr",
                                 input_text="SET ROLE cpi_lifecycle_reader; " + statement + ";\n", check=False)
            require(denied.returncode != 0 and "permission denied" in denied.stdout, "Read-only role boundary failed")
        self.shared = "container:" + self.inspect("container", self.api)["Id"]
        probe = self.directory / "probe.mjs"
        shutil.copyfile(ROOT / "scripts/cpi-operator-lifecycle-probe.mjs", probe)
        self.probe_mount = ["--mount", f"type=bind,source={probe},target=/probe.mjs,readonly"]
        self.ui_env = {"CPI_OPERATOR_UI_PORT": "3000", "CPI_OPERATOR_API_PORT": "8080"}

    def api_ready(self):
        self.await_ready(lambda: self.docker("exec", self.api, "curl", "--fail", "--silent", "--max-time", "2",
                                            "http://127.0.0.1:8080/actuator/health", check=False).returncode == 0)

    def probe(self, mode, target=None, check=True):
        result = self.docker("exec", "-i", target or self.ui, "node", "/probe.mjs", timeout=20, check=check, diagnostic=not check,
                             input_text=json.dumps({"mode": mode, "token": self.bearer}))
        if check:
            return json.loads(result.stdout)
        return result.returncode == 0

    def stop(self, name, allowed):
        started = self.inspect("container", name)["State"]["StartedAt"]
        self.docker("stop", "--time", "30", name, timeout=40)
        verify_stopped(self.inspect("container", name), started, allowed)
        return started

    def restart(self, name, before):
        self.docker("start", name)
        info = self.inspect("container", name)
        require(info["State"]["Running"] and info["State"]["StartedAt"] != before, "New process incarnation not observed")

    def exercise(self):
        baseline = self.inventory()
        self.ui = self.run_app("ui", self.images["operator"], self.shared, "node", [], self.ui_env, self.probe_mount)
        self.await_ready(lambda: self.probe("ready", check=False))
        first = self.probe("read")
        require(self.inventory() == baseline, "Startup/read changed persisted CPI data")
        self.stage("actual packaged reads, KST precision, loopback-only sockets and authentication verified")
        duplicate = self.run_app("duplicate", self.images["operator"], self.shared, "node", [], self.ui_env)
        duplicate_exit = self.docker("wait", duplicate, timeout=45).stdout.strip()
        require(duplicate_exit.isdecimal() and 0 < int(duplicate_exit) <= 255 and duplicate_exit != "137"
                and not self.inspect("container", duplicate)["State"]["OOMKilled"],
                "Duplicate listener did not fail closed; exit=" + duplicate_exit)
        require("EADDRINUSE" in self.docker("logs", "--tail", "80", duplicate).stdout, "Duplicate startup failed for another reason")
        self.stage("duplicate bind rejected without adopting/replacing existing listener")
        before = self.stop(self.ui, {0})
        closed = self.run_app("closed-probe", self.images["operator"], self.shared, "node", ["node", "-e", "setInterval(()=>{},1000)"], {}, self.probe_mount)
        self.probe("closed", closed)
        self.stop(closed, {143})
        self.restart(self.ui, before)
        self.await_ready(lambda: self.probe("ready", check=False))
        require(self.probe("read")["evidenceHash"] == first["evidenceHash"], "UI restart changed evidence")
        self.stage("UI SIGTERM exited normally, released its port and restarted with identical evidence")
        api_before = self.stop(self.api, {0, 143})
        self.probe("unavailable")
        ui_before = self.stop(self.ui, {0})
        # Join the API's new namespace only after its new process is ready.
        self.restart(self.api, api_before)
        self.api_ready()
        self.restart(self.ui, ui_before)
        self.await_ready(lambda: self.probe("ready", check=False))
        require(self.probe("read")["evidenceHash"] == first["evidenceHash"], "Stack restart changed evidence")
        require(self.inventory() == baseline, "Lifecycle changed persisted CPI tables")
        self.stop(self.ui, {0})
        self.stop(self.api, {0, 143})
        require(self.inventory() == baseline, "Final shutdown changed persisted CPI tables")
        for name in self.owned["container"]:
            info = self.inspect("container", name)
            require(not info["HostConfig"].get("PortBindings") and not any(m["Type"] == "volume" for m in info["Mounts"]),
                    "Runtime acquired host port/persistent volume")
            logs = self.docker("logs", "--tail", "500", name).stdout
            require(not any(value in logs for value in self.redactions), "Synthetic secret leaked in runtime log")
        self.stage("API loss returns sanitized 503; ordered restart and final shutdown preserve all CPI tables")


def main():
    require(sys.argv[1:] == ["--confirm-disposable-demo"], "Explicit --confirm-disposable-demo required; no external targets accepted")
    runtime = Rehearsal()
    passed = False
    try:
        runtime.prepare()
        runtime.build_images()
        runtime.prepare_database()
        runtime.exercise()
        runtime.verify_inputs()
        passed = True
    finally:
        try:
            try:
                runtime.capture_logs()
            finally:
                runtime.cleanup()
        except Exception:
            passed = False
            raise
        finally:
            runtime.report.write_text(json.dumps({"dataMode": "DEMO", "passed": passed, "checks": runtime.checks,
                "images": runtime.images, "sourceCommit": getattr(runtime, "revision", None),
                "operatorRecipeSha256": getattr(runtime, "recipe_hash", None), "rehearsalInputs": runtime.inputs,
                "productionActivated": False}, indent=2) + "\n")
            print("DEMO report: " + str(runtime.report), flush=True)
    print("PASS: packaged CPI operator lifecycle; owned resources removed", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
