#!/usr/bin/env python3
"""ADR-078: opt-in packaged transport faults against an owned synthetic Linux stack."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
import shutil
import sys
import time

from cpi_operator_lifecycle import ROOT, archive, input_hashes, require

spec = importlib.util.spec_from_file_location("cpi_transport_lifecycle", ROOT / "scripts/verify-cpi-operator-lifecycle.py")
lifecycle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lifecycle)
INPUTS = ("scripts/verify-cpi-operator-transport.py", "scripts/cpi-transport-relay.mjs", "scripts/cpi-transport-probe.mjs")
ROUTES = ("api-list", "api-selected", "api-head-list", "api-head-selected", "ui-list", "ui-selected")
LOCK_NAME = "wsr_adr078_owned_blocker"


def transport_inputs(root):
    for path in INPUTS:
        archive.regular_tree(root / path)
    return {**input_hashes(root), **{path: hashlib.sha256((root / path).read_bytes().replace(b"\r\n", b"\n")).hexdigest()
                                   for path in INPUTS}}


def fault_evidence(before, after):
    selected = {link["id"] for link in before["links"] if link["silenced"] and not link["closed"]}
    require(len(selected) == 1, "Fault must target the single established reader connection")
    rows = [link for link in after["links"] if link["id"] in selected]
    require(len(rows) == 1 and rows[0]["silenced"] and rows[0]["closed"] and rows[0]["discardedBytes"] > 0,
            "Real downstream bytes and broken connection closure required")
    return {"connectionId": rows[0]["id"], "discardedBytes": rows[0]["discardedBytes"], "closed": True}


class Rehearsal(lifecycle.Rehearsal):
    def __init__(self):
        super().__init__()
        self.inputs = transport_inputs(ROOT)
        self.log = self.cache / ("adr078-" + self.token + ".log")
        self.report = self.cache / ("adr078-" + self.token + ".json")
        self.faults = []

    def verify_inputs(self):
        require(self.inputs == transport_inputs(ROOT), "Transport rehearsal input changed during execution")

    def build_images(self):
        super().build_images()
        directory = self.directory / "transport"
        directory.mkdir()
        for path in INPUTS[1:]:
            shutil.copyfile(ROOT / path, directory / (ROOT / path).name)
        self.transport_mount = ["--mount", f"type=bind,source={directory},target=/adr078,readonly"]

    def control(self, mode="status"):
        require(mode in ("status", "silence"), "Closed relay operation required")
        return json.loads(self.docker("exec", self.relay, "node", "/adr078/cpi-transport-relay.mjs", mode, timeout=5).stdout)

    def run_app(self, suffix, image, network, user, arguments, environment, mounts=()):
        if suffix == "api":
            self.relay = super().run_app("relay", self.images["operator"], network, "node",
                ["node", "/adr078/cpi-transport-relay.mjs", "--confirm-disposable-demo"], {}, self.transport_mount)
            self.await_ready(lambda: self.docker("exec", self.relay, "node", "/adr078/cpi-transport-relay.mjs", "status",
                                                 check=False, timeout=5).returncode == 0)
            environment = {**environment, "POSTGRES_HOST": self.relay, "POSTGRES_PORT": "15432",
                "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE": "1", "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE": "1"}
        return super().run_app(suffix, image, network, user, arguments, environment, [*mounts, *self.transport_mount])

    def request(self, route, expected=200):
        require(route in (*ROUTES, "invalid", "unauthorized"), "Closed probe route required")
        return json.loads(self.docker("exec", "-i", self.ui, "node", "/adr078/cpi-transport-probe.mjs", timeout=15,
            input_text=json.dumps({"route": route, "expected": expected, "token": self.bearer})).stdout)

    def read_all(self):
        results = {}
        for route in ROUTES:
            if route.startswith("ui-"):
                time.sleep(1.1)  # Respect the unchanged real gateway's one-second limiter.
            results[route] = self.request(route)["evidenceHash"]
        return results

    def blocker_pid(self):
        return self.sql(f"SELECT pid FROM pg_stat_activity WHERE application_name='{LOCK_NAME}' "
                        "AND usename='wsr' AND datname='wsr' AND wait_event='PgSleep'")

    def release_blocker(self, pid):
        require(str(pid).isdecimal(), "Owned blocker PID required")
        # This psql runs exclusively inside our labeled disposable database container.
        return self.sql(f"SELECT pg_cancel_backend(pid) FROM pg_stat_activity WHERE pid={pid} "
                        f"AND application_name='{LOCK_NAME}' AND usename='wsr' AND datname='wsr'")

    def fault(self, route, baseline, evidence):
        if route.startswith("ui-"):
            time.sleep(1.1)  # Do not rely on Docker/SQL setup latency to clear the real UI limiter.
        lock_sql = (f"SET application_name='{LOCK_NAME}'; SET lock_timeout='2s'; SET statement_timeout='20s'; "
                    "BEGIN; LOCK TABLE bls_cpi_collection_results IN ACCESS EXCLUSIVE MODE; SELECT pg_sleep(18); COMMIT")
        pid = None
        with ThreadPoolExecutor(max_workers=2) as executor:
            held = executor.submit(self.sql, lock_sql, False)
            try:
                self.await_ready(lambda: bool(self.blocker_pid()), seconds=5)
                pid = self.blocker_pid()
                require(pid.isdecimal(), "Exactly one owned blocker required")
                pending = executor.submit(self.request, route, 503)
                self.await_ready(lambda: self.sql("SELECT count(*) FROM pg_stat_activity WHERE usename='cpi_lifecycle_reader' "
                    "AND state='active' AND wait_event_type='Lock' AND query LIKE '%bls_cpi_collection_results%' "
                    f"AND {pid}=ANY(pg_blocking_pids(pid))") == "1", seconds=2)
                silenced = self.control("silence")
                require(self.release_blocker(pid) == "t", "Owned query lock was not released explicitly")
                held.result(timeout=5)
                self.request("invalid", 400)
                self.request("unauthorized", 401)
                result = pending.result(timeout=15)
                selected = {link["id"] for link in silenced["links"] if link["silenced"] and not link["closed"]}
                self.await_ready(lambda: all(link["closed"] for link in self.control()["links"] if link["id"] in selected), seconds=12)
                fault = fault_evidence(silenced, self.control())
            finally:
                if pid is not None:
                    self.release_blocker(pid)
                held.result(timeout=25)
        require(self.read_all() == evidence, "Fault recovery changed any API/UI evidence")
        require(self.inventory() == baseline, "Transport fault changed persisted CPI data")
        self.faults.append({**result, **fault, "allSixReadsRecovered": True, "tablesUnchanged": True})
        self.stage(f"{route}: actual blocked SQL, discarded response bytes, sanitized 503, connection closure and six-read recovery")

    def exercise(self):
        super().exercise()  # Preserve the original boot/auth/KST/bind/shutdown/restart acceptance.
        baseline = self.inventory()
        for name in (self.api, self.ui):
            self.restart(name, self.inspect("container", name)["State"]["StartedAt"])
            if name == self.api:
                self.api_ready()
        self.await_ready(lambda: self.probe("ready", check=False))
        evidence = self.read_all()
        for route in ROUTES:
            self.fault(route, baseline, evidence)
        self.stop(self.ui, {0})
        self.stop(self.api, {0, 143})
        self.stop(self.relay, {0})
        require(self.inventory() == baseline, "Final transport shutdown changed CPI tables")
        for name in self.owned["container"]:
            logs = self.docker("logs", "--tail", "500", name).stdout
            require(not any(secret in logs for secret in self.redactions), "Synthetic credential leaked in runtime log")
        self.stage("all six real Linux transport faults passed; no runtime source or deployment defaults changed")


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
                "faults": runtime.faults, "images": runtime.images, "sourceCommit": getattr(runtime, "revision", None),
                "operatorRecipeSha256": getattr(runtime, "recipe_hash", None), "rehearsalInputs": runtime.inputs,
                "singleConnectionTestPool": True, "endToEndDeadlineClaimed": False, "productionActivated": False}, indent=2) + "\n")
            print("DEMO report: " + str(runtime.report), flush=True)
    print("PASS: packaged CPI operator transport; owned resources removed", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
