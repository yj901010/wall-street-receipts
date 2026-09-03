#!/usr/bin/env python3
"""Offline tests for the size-bounded historical CI compatibility bridge.

Only the pinned workflow is read from Git. Mutable fixtures and custody roots
live in owned temporary directories; no source refs, index, or files are edited.
"""
from __future__ import annotations

from contextlib import redirect_stdout
import copy
from functools import lru_cache
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

import yaml

import run_contracts as bridge


SOURCE = Path(__file__).resolve().parents[2]


@lru_cache(maxsize=1)
def pinned():
    return bridge.baseline_workflow(SOURCE)


def entries():
    return bridge.expected_manifest(pinned())["steps"]


def run_entries():
    return [entry for entry in entries() if "script" in entry]


def successful_results():
    return [{"index": entry["index"], "code": 0} for entry in run_entries()]


class TemporaryTestCase(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-ci-contracts-test-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name).resolve()

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path


class PinnedWorkflowTests(unittest.TestCase):
    def test_full_pinned_workflow_identity_and_inventory(self):
        manifest = bridge.expected_manifest(pinned())
        self.assertEqual(manifest["baselineCommit"], "3792100f49c496d751d1dd54a7fbdc1b7c2fd275")
        self.assertEqual(manifest["baselineWorkflowSha256"], bridge.BASELINE_WORKFLOW_SHA256)
        self.assertEqual(len(manifest["steps"]), 86)
        self.assertEqual(len(run_entries()), 84)
        self.assertEqual(len([entry for entry in entries() if entry.get("if") == "always()"]), 7)
        self.assertEqual([entry["index"] for entry in entries() if "uses" in entry], [0, 2])

    def test_baseline_bytes_are_checked_before_yaml_parse(self):
        with patch.object(bridge, "git", return_value=b"not the pinned workflow"), \
                patch.object(bridge, "parse_workflow") as parse:
            with self.assertRaisesRegex(ValueError, "Pinned historical workflow bytes changed"):
                bridge.baseline_workflow(SOURCE)
            parse.assert_not_called()

    def test_missing_baseline_is_not_replaced_with_current_head(self):
        with patch.object(bridge, "git", side_effect=ValueError("Local git show failed (128)")) as git:
            with self.assertRaisesRegex(ValueError, "show failed"):
                bridge.baseline_workflow(SOURCE)
            git.assert_called_once_with(SOURCE, "show", f"{bridge.BASELINE}:{bridge.WORKFLOW}")

    def test_extraction_manifest_is_deterministic_and_preserves_raw_run_digest(self):
        baseline = copy.deepcopy(pinned())
        first = bridge.expected_manifest(baseline)
        self.assertEqual(first, bridge.expected_manifest(copy.deepcopy(baseline)))
        for entry in first["steps"]:
            if "script" not in entry:
                continue
            original = baseline["jobs"][bridge.JOB]["steps"][entry["index"]]
            with self.subTest(index=entry["index"]):
                self.assertEqual(entry["sha256"], bridge.digest(bridge.canonical_run(original["run"])))
                self.assertEqual(entry["originalRunSha256"], bridge.digest(original["run"].encode("utf-8")))
                self.assertEqual(entry["characters"], len(original["run"]))
                self.assertEqual(entry["script"], bridge.script_path(entry["index"], original))
                self.assertEqual({key: entry[key] for key in original if key != "run"},
                                 {key: value for key, value in original.items() if key != "run"})

    def test_canonical_body_preserves_content_and_normalizes_only_line_endings(self):
        for source, expected in (("echo x", b"echo x\n"), ("echo x\n", b"echo x\n"),
                                 ("echo x\r\n", b"echo x\n"), ("echo x\n\n", b"echo x\n\n"),
                                 ("한글", "한글\n".encode())):
            with self.subTest(source=source):
                self.assertEqual(bridge.canonical_run(source), expected)

    def test_yaml_scalar_folding_and_typed_metadata(self):
        workflow = bridge.parse_workflow(
            b"on: push\nconcurrency: {cancel-in-progress: true}\njobs:\n"
            b"  checks:\n    timeout-minutes: 5\n    steps:\n"
            b"      - run: >-\n          echo one\n          two\n"
            b"      - run: |\n          echo three\n          four\n"
        )
        self.assertEqual(workflow["on"], "push")
        self.assertIs(workflow["concurrency"]["cancel-in-progress"], True)
        self.assertEqual(workflow["jobs"]["checks"]["timeout-minutes"], 5)
        self.assertEqual(workflow["jobs"]["checks"]["steps"][0]["run"], "echo one two")
        self.assertEqual(workflow["jobs"]["checks"]["steps"][1]["run"], "echo three\nfour\n")

    def test_unsupported_historical_execution_metadata_fails_closed(self):
        for key, value in (("shell", "python"), ("if", "success()"),
                           ("env", {"UNREVIEWED": "value"}), ("working-directory", "elsewhere")):
            with self.subTest(key=key):
                baseline = copy.deepcopy(pinned())
                baseline["jobs"][bridge.JOB]["steps"][1][key] = value
                with self.assertRaisesRegex(ValueError, "Unexpected historical"):
                    bridge.expected_manifest(baseline)

    def test_checked_in_extraction_matches_pinned_workflow(self):
        self.assertEqual(bridge.validate_extraction(SOURCE, pinned()), bridge.expected_manifest(pinned()))

    def test_pinned_runtime_manifest_matches_full_baseline_extraction(self):
        self.assertEqual(bridge.pinned_manifest(SOURCE), bridge.expected_manifest(pinned()))


class ExtractionTamperTests(TemporaryTestCase):
    def setUp(self):
        super().setUp()
        self.manifest = bridge.expected_manifest(pinned())
        self.write_manifest(self.manifest)
        original = pinned()["jobs"][bridge.JOB]["steps"]
        for entry in self.manifest["steps"]:
            if "script" in entry:
                self.write(entry["script"], bridge.canonical_run(original[entry["index"]]["run"]))

    def write_manifest(self, manifest):
        self.write(bridge.MANIFEST, json.dumps(manifest).encode())

    def test_exact_temporary_extraction_passes(self):
        self.assertEqual(bridge.validate_extraction(self.root, pinned()), self.manifest)

    def test_one_byte_body_change_is_rejected(self):
        path = self.root / run_entries()[0]["script"]
        path.write_bytes(path.read_bytes() + b"# tampered\n")
        with self.assertRaisesRegex(ValueError, "Extracted run body changed"):
            bridge.validate_extraction(self.root, pinned())

    def test_crlf_checkout_is_supported_without_relaxing_body_digest(self):
        path = self.root / run_entries()[0]["script"]
        path.write_bytes(path.read_bytes().replace(b"\n", b"\r\n"))
        bridge.validate_extraction(self.root, pinned())

    def test_missing_and_extra_scripts_are_rejected(self):
        path = self.root / run_entries()[0]["script"]
        original = path.read_bytes()
        path.unlink()
        with self.assertRaisesRegex(ValueError, "Extracted script missing or linked"):
            bridge.validate_extraction(self.root, pinned())
        path.write_bytes(original)
        self.write("scripts/ci/legacy/unreviewed.sh", b"echo extra\n")
        with self.assertRaisesRegex(ValueError, "Unexpected or missing extracted script"):
            bridge.validate_extraction(self.root, pinned())

    def test_manifest_body_metadata_shell_condition_and_order_tampering_are_rejected(self):
        mutations = {
            "metadata": lambda manifest: manifest["steps"][1].update(name="renamed"),
            "shell": lambda manifest: manifest["steps"][1].update(shell="bash"),
            "condition": lambda manifest: manifest["steps"][1].update({"if": "always()"}),
            "digest": lambda manifest: manifest["steps"][1].update(sha256="0" * 64),
            "order": lambda manifest: manifest["steps"].reverse(),
            "missing": lambda manifest: manifest["steps"].pop(),
            "duplicate": lambda manifest: manifest["steps"].append(copy.deepcopy(manifest["steps"][-1])),
            "job metadata": lambda manifest: manifest["jobMetadata"].update({"timeout-minutes": 50}),
            "baseline": lambda manifest: manifest.update(baselineCommit="0" * 40),
        }
        for name, mutate in mutations.items():
            with self.subTest(mutation=name):
                candidate = copy.deepcopy(self.manifest)
                mutate(candidate)
                self.write_manifest(candidate)
                with self.assertRaisesRegex(ValueError, "metadata/order differs"):
                    bridge.validate_extraction(self.root, pinned())


class WorkflowParityTests(TemporaryTestCase):
    def setUp(self):
        super().setUp()
        self.expected = bridge.expected_workflow(pinned())

    def write_workflow(self, value):
        self.write(bridge.WORKFLOW, yaml.safe_dump(value, sort_keys=False).encode())

    def test_application_jobs_allow_only_explicit_hosted_ci_corrections(self):
        baseline = pinned()
        for key, value in baseline.items():
            if key != "jobs":
                self.assertEqual(self.expected[key], value)
        self.assertEqual(set(self.expected["jobs"]), set(baseline["jobs"]))
        for job in ("web", "api", "call-audit-integration"):
            with self.subTest(job=job):
                restored = copy.deepcopy(self.expected["jobs"][job])
                if job == "api":
                    gates = [step for step in restored["steps"] if step.get("name") == "Verify SEC persistence test isolation"]
                    self.assertEqual(len(gates), 1)
                    self.assertIn("-Dsurefire.runOrder=reversealphabetical", gates[0]["run"])
                    restored["steps"].remove(gates[0])
                if job == "call-audit-integration":
                    original = next(step for step in baseline["jobs"][job]["steps"] if step.get("name") == "Verify Next requested the real list and all audit resources")
                    changed = next(step for step in restored["steps"] if step.get("name") == original["name"])
                    self.assertEqual(changed["run"], "python scripts/ci/verify_call_audit_access.py")
                    changed["run"] = original["run"]
                self.assertEqual(restored, baseline["jobs"][job])
        self.assertEqual({key: value for key, value in self.expected["jobs"][bridge.JOB].items() if key != "steps"},
                         {key: value for key, value in baseline["jobs"][bridge.JOB].items() if key != "steps"})
        self.write_workflow(self.expected)
        bridge.validate_workflow_parity(self.root, baseline)

    def test_all_historical_run_steps_keep_metadata_and_exact_order(self):
        steps = self.expected["jobs"][bridge.JOB]["steps"]
        historical = steps[6:-1]
        self.assertEqual(len(historical), 84)
        for original, current in zip(run_entries(), historical):
            with self.subTest(index=original["index"]):
                self.assertEqual(current["run"], f"python scripts/ci/run_contracts.py run {original['index']}")
                baseline_step = pinned()["jobs"][bridge.JOB]["steps"][original["index"]]
                self.assertEqual({key: value for key, value in current.items() if key != "run"},
                                 {key: value for key, value in baseline_step.items() if key != "run"})
        self.assertEqual(steps[-1]["if"], "always()")
        self.assertEqual(steps[-1]["run"], "python scripts/ci/run_contracts.py finish")

    def test_application_command_shell_condition_environment_and_order_changes_fail(self):
        mutations = {
            "web command": lambda value: value["jobs"]["web"]["steps"][-1].update(run="echo skipped"),
            "api metadata": lambda value: value["jobs"]["api"].update({"timeout-minutes": 1}),
            "shell": lambda value: value["jobs"][bridge.JOB]["steps"][6].update(shell="pwsh"),
            "condition": lambda value: value["jobs"][bridge.JOB]["steps"][-1].pop("if"),
            "environment": lambda value: value["jobs"]["call-audit-integration"]["steps"][-2].update(env={}),
            "order": lambda value: value["jobs"][bridge.JOB]["steps"].reverse(),
            "checkout": lambda value: value["jobs"][bridge.JOB]["steps"][0]["with"].update({"fetch-depth": 1}),
        }
        for name, mutate in mutations.items():
            with self.subTest(mutation=name):
                candidate = copy.deepcopy(self.expected)
                mutate(candidate)
                self.write_workflow(candidate)
                with self.assertRaisesRegex(ValueError, "CI metadata, current app jobs"):
                    bridge.validate_workflow_parity(self.root, pinned())


class ProductTreeTests(unittest.TestCase):
    def setUp(self):
        self.base = {"apps/web/src/page.ts": "100644 blob " + "1" * 40,
                     "apps/api/src/Main.java": "100644 blob " + "2" * 40}
        self.allowed = bridge.permitted_paths(bridge.expected_manifest(pinned()))

    def test_tree_records_preserve_paths_modes_types_and_object_identity(self):
        raw = (b"100755 blob " + b"1" * 40 + b"\tpath with spaces.sh\0"
               b"120000 blob " + b"2" * 40 + b"\tlinked\0"
               b"160000 commit " + b"3" * 40 + b"\tsubmodule\0")
        records = bridge.tree_records(raw)
        self.assertEqual(records["path with spaces.sh"], "100755 blob " + "1" * 40)
        self.assertEqual(records["linked"], "120000 blob " + "2" * 40)
        self.assertEqual(records["submodule"], "160000 commit " + "3" * 40)

    def test_identical_product_and_exact_ci_paths_are_accepted(self):
        bridge.compare_product_trees(self.base, copy.deepcopy(self.base), self.allowed)
        current = {**self.base, bridge.WORKFLOW: "100644 blob " + "9" * 40,
                   run_entries()[0]["script"]: "100644 blob " + "8" * 40}
        bridge.compare_product_trees(self.base, current, self.allowed)
        self.assertNotIn(bridge.NEXT_ENV, self.allowed)

    def test_product_body_add_delete_mode_type_and_rename_changes_fail(self):
        changed = {
            "blob": {**self.base, "apps/web/src/page.ts": "100644 blob " + "3" * 40},
            "add": {**self.base, "apps/web/src/new.ts": "100644 blob " + "3" * 40},
            "delete": {"apps/web/src/page.ts": self.base["apps/web/src/page.ts"]},
            "mode": {**self.base, "apps/web/src/page.ts": "100755 blob " + "1" * 40},
            "symlink": {**self.base, "apps/web/src/page.ts": "120000 blob " + "1" * 40},
            "rename": {"apps/web/src/renamed.ts": self.base["apps/web/src/page.ts"],
                       "apps/api/src/Main.java": self.base["apps/api/src/Main.java"]},
        }
        for name, current in changed.items():
            with self.subTest(change=name), self.assertRaisesRegex(ValueError, "Product tree differs"):
                bridge.compare_product_trees(self.base, current, self.allowed)

    def test_nearby_ci_and_document_paths_are_not_broadly_allowed(self):
        for path in ("scripts/ci/unreviewed.py", "scripts/ci/legacy/new.sh", "scripts/verify-other.py",
                     "decisions/ADR-058-unreviewed.md", "README.md", bridge.NEXT_ENV):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "Product tree differs"):
                bridge.compare_product_trees(self.base, {**self.base, path: "100644 blob " + "4" * 40}, self.allowed)

    def test_uncommitted_product_and_untracked_changes_are_rejected(self):
        manifest = bridge.expected_manifest(pinned())
        for changed, untracked in ((b"apps/web/src/page.ts\0", b""),
                                   (b"", b"scripts/ci/unreviewed.py\0")):
            with self.subTest(changed=changed, untracked=untracked):
                with patch.object(bridge, "git", side_effect=[b"", b"", changed, untracked]), \
                        patch.object(bridge, "verify_current_test", return_value={}):
                    with self.assertRaisesRegex(ValueError, "Unexpected uncommitted"):
                        bridge.validate_product(SOURCE, manifest)

    def test_user_next_declaration_may_be_unstaged_but_never_staged(self):
        manifest = bridge.expected_manifest(pinned())
        changed = (bridge.NEXT_ENV + "\0").encode()
        with patch.object(bridge, "git", side_effect=[b"", b"", changed, b"", b""]), \
                patch.object(bridge, "verify_current_test", return_value={}):
            bridge.validate_product(SOURCE, manifest)
        with patch.object(bridge, "git", side_effect=[b"", b"", changed, b"", changed]), \
                patch.object(bridge, "verify_current_test", return_value={}):
            with self.assertRaisesRegex(ValueError, "must not be staged"):
                bridge.validate_product(SOURCE, manifest)


class SequenceTests(unittest.TestCase):
    def test_every_one_of_84_run_steps_must_execute_in_order(self):
        results = []
        for expected in run_entries():
            selected = bridge.next_allowed_step(entries(), results, False, expected["index"])
            self.assertEqual(selected, expected)
            results.append({"index": selected["index"], "code": 0})
        bridge.assert_complete_run(bridge.expected_manifest(pinned()), {"results": results, "failed": False})
        with self.assertRaisesRegex(ValueError, "skipped, repeated, reordered"):
            bridge.next_allowed_step(entries(), results, False, results[-1]["index"])

    def test_missing_repeated_and_reordered_steps_are_rejected(self):
        cases = (([], 3), ([{"index": 1, "code": 0}], 1), ([{"index": 1, "code": 0}], 4))
        for results, index in cases:
            with self.subTest(results=results, index=index):
                with self.assertRaisesRegex(ValueError, "skipped, repeated, reordered"):
                    bridge.next_allowed_step(entries(), results, False, index)

    def test_failure_at_every_step_requires_every_subsequent_always_step(self):
        runs = run_entries()
        for position, failed_entry in enumerate(runs):
            with self.subTest(failed_index=failed_entry["index"]):
                results = [{"index": entry["index"], "code": 0} for entry in runs[:position]]
                results.append({"index": failed_entry["index"], "code": 1})
                pending = [entry for entry in runs[position + 1:] if entry.get("if") == "always()"]
                if len(pending) > 1:
                    with self.assertRaisesRegex(ValueError, "skipped, repeated, reordered"):
                        bridge.next_allowed_step(entries(), results, True, pending[1]["index"])
                for restore in pending:
                    self.assertEqual(bridge.next_allowed_step(entries(), results, True, restore["index"]), restore)
                    results.append({"index": restore["index"], "code": 0})
                with self.assertRaisesRegex(ValueError, "Historical contract execution failed"):
                    bridge.assert_complete_run(bridge.expected_manifest(pinned()), {"results": results, "failed": True})

    def test_failure_does_not_allow_normal_commands_to_resume(self):
        with self.assertRaisesRegex(ValueError, "ran after failure"):
            bridge.next_allowed_step(entries(), [{"index": 1, "code": 1}], True, 3)

    def test_completion_rejects_skipped_restore_failed_restore_and_missing_normal_step(self):
        manifest = bridge.expected_manifest(pinned())
        results = successful_results()
        restore_index = next(entry["index"] for entry in run_entries() if entry.get("if") == "always()")
        with self.assertRaisesRegex(ValueError, "mandatory always-restore"):
            bridge.assert_complete_run(manifest, {"results": [result for result in results if result["index"] != restore_index], "failed": False})
        with self.assertRaisesRegex(ValueError, "Historical contract execution failed"):
            bridge.assert_complete_run(manifest, {"results": [{**result, "code": 1} if result["index"] == restore_index else result for result in results], "failed": False})
        with self.assertRaisesRegex(ValueError, "historical run step was skipped"):
            bridge.assert_complete_run(manifest, {"results": results[1:], "failed": False})

    def test_bash_and_powershell_runner_semantics_are_preserved(self):
        path = Path("path with spaces") / "quote's-script.ps1"
        self.assertEqual(bridge.shell_command({}, path), ["bash", "--noprofile", "--norc", "-e", str(path)])
        self.assertEqual(bridge.shell_command({"shell": "bash"}, path),
                         ["bash", "--noprofile", "--norc", "-e", "-o", "pipefail", str(path)])
        command = bridge.shell_command({"shell": "pwsh"}, path)
        self.assertEqual(command[:5], ["pwsh", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command"])
        self.assertIn("$ErrorActionPreference = 'Stop'", command[-1])
        self.assertIn("quote''s-script.ps1", command[-1])
        self.assertIn("exit $LASTEXITCODE", command[-1])


class ProcessLifecycleTests(unittest.TestCase):
    def test_success_uses_bounded_wait_and_does_not_terminate_process(self):
        process = Mock()
        process.wait.return_value = 0
        with patch.object(bridge.subprocess, "Popen", return_value=process) as create, \
                patch.object(bridge, "stop_process_tree") as stop:
            self.assertEqual(bridge.execute(["synthetic"], SOURCE, {"TEST": "only"}), 0)
            process.wait.assert_called_once_with(timeout=120)
            self.assertEqual(create.call_args.kwargs["cwd"], SOURCE)
            self.assertEqual(create.call_args.kwargs["env"], {"TEST": "only"})
            self.assertTrue("start_new_session" in create.call_args.kwargs or "creationflags" in create.call_args.kwargs)
            stop.assert_not_called()

    def test_timeout_terminates_process_tree_and_returns_nonzero(self):
        process = Mock()
        process.wait.side_effect = subprocess.TimeoutExpired("synthetic", 120)
        with patch.object(bridge.subprocess, "Popen", return_value=process), \
                patch.object(bridge, "stop_process_tree") as stop:
            self.assertEqual(bridge.execute(["synthetic"], SOURCE, {}), 124)
            stop.assert_called_once_with(process)

    def test_keyboard_interrupt_terminates_process_tree_and_is_reraised(self):
        process = Mock()
        process.wait.side_effect = KeyboardInterrupt("synthetic cancellation")
        with patch.object(bridge.subprocess, "Popen", return_value=process), \
                patch.object(bridge, "stop_process_tree") as stop:
            with self.assertRaisesRegex(KeyboardInterrupt, "synthetic cancellation"):
                bridge.execute(["synthetic"], SOURCE, {})
            stop.assert_called_once_with(process)


class CustodyTests(TemporaryTestCase):
    def setUp(self):
        super().setUp()
        self.environment = patch.dict(os.environ, {"RUNNER_TEMP": str(self.root)})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.owned = self.root / bridge.OWNED_NAME
        self.token = "a" * 48

    def create_owned(self):
        self.owned.mkdir()
        (self.owned / ".owner").write_text(self.token)
        (self.owned / "legacy").mkdir()
        (self.owned / "runner-temp").mkdir()
        return {"version": 1, "source": str(SOURCE), "baseline": bridge.BASELINE,
                "token": self.token, "snapshot": {"test": "custody"},
                "results": successful_results(), "failed": False}

    def test_owned_root_and_state_round_trip(self):
        state = self.create_owned()
        self.assertEqual(bridge.owned_root(), self.owned)
        bridge.assert_owned(self.owned, self.token)
        bridge.write_state(self.owned, state)
        self.assertEqual(bridge.read_state(self.owned, SOURCE), state)
        self.assertFalse((self.owned / "state.pending").exists())

    def test_missing_foreign_or_invalid_marker_never_authorizes_cleanup(self):
        self.create_owned()
        for token in ("b" * 48, "a" * 47, "A" * 48, "../outside"):
            with self.subTest(token=token), self.assertRaises(ValueError):
                bridge.assert_owned(self.owned, token)
        (self.owned / ".owner").unlink()
        with self.assertRaisesRegex(ValueError, "Owner marker mismatch"):
            bridge.assert_owned(self.owned, self.token)
        outside = self.root / "not-owned"
        outside.mkdir()
        (outside / ".owner").write_text(self.token)
        with self.assertRaisesRegex(ValueError, "escaped RUNNER_TEMP"):
            bridge.assert_owned(outside, self.token)

    def test_state_identity_changes_fail_closed(self):
        original = self.create_owned()
        for key, value in (("source", str(self.root)), ("baseline", "0" * 40),
                           ("version", 2), ("unreviewed", "field")):
            with self.subTest(key=key):
                candidate = {**original, key: value}
                bridge.write_state(self.owned, candidate)
                with self.assertRaisesRegex(ValueError, "Invalid state fields|State identity mismatch"):
                    bridge.read_state(self.owned, SOURCE)

    def test_link_inside_owned_checkout_is_rejected_without_following_it(self):
        self.create_owned()
        target = self.root / "outside.txt"
        target.write_text("preserve")
        link = self.owned / "legacy" / "linked.txt"
        try:
            link.symlink_to(target)
        except OSError as error:
            self.skipTest(f"Platform does not permit unprivileged symlink creation: {error.__class__.__name__}")
        with self.assertRaisesRegex(ValueError, "link/reparse point"):
            bridge.assert_owned(self.owned, self.token)
        self.assertEqual(target.read_text(), "preserve")

    def finish_with_mocks(self, state, *, current_snapshot=None, git_results=None):
        for number in ("045", "048", "052", "053", "054", "055", "056"):
            projection = self.owned / "runner-temp" / f"wsr-adr{number}-current-view"
            projection.mkdir(exist_ok=True)
            (projection / ".restored").write_text("restored")
        bridge.write_state(self.owned, state)
        with patch.object(bridge, "validate_source", return_value=bridge.expected_manifest(pinned())), \
                patch.object(bridge, "snapshot", return_value=current_snapshot or state["snapshot"]), \
                patch.object(bridge, "git", side_effect=git_results or [(bridge.BASELINE + "\n").encode(), b""]), \
                redirect_stdout(io.StringIO()):
            bridge.finish(SOURCE)

    def test_finish_removes_only_validated_owned_root(self):
        state = self.create_owned()
        sentinel = self.root / "unrelated.txt"
        sentinel.write_text("preserve")
        self.finish_with_mocks(state)
        self.assertFalse(self.owned.exists())
        self.assertEqual(sentinel.read_text(), "preserve")

    def test_finish_failure_still_cleans_owned_checkout_and_preserves_failure(self):
        state = self.create_owned()
        state["failed"] = True
        with self.assertRaisesRegex(ValueError, "Historical contract execution failed"):
            self.finish_with_mocks(state)
        self.assertFalse(self.owned.exists())

    def test_source_custody_failure_is_not_reported_as_success(self):
        state = self.create_owned()
        with self.assertRaisesRegex(ValueError, "Current source custody changed"):
            self.finish_with_mocks(state, current_snapshot={"test": "changed"})
        self.assertFalse(self.owned.exists())

    def test_historical_head_and_pending_projection_custody_must_be_restored(self):
        state = self.create_owned()
        with self.assertRaisesRegex(ValueError, "Historical HEAD was not restored"):
            self.finish_with_mocks(state, git_results=[b"0" * 40 + b"\n"])
        self.assertFalse(self.owned.exists())
        state = self.create_owned()
        (self.owned / "runner-temp" / "unfinished").mkdir()
        with self.assertRaisesRegex(ValueError, "Historical projection inventory changed"):
            self.finish_with_mocks(state)
        self.assertFalse(self.owned.exists())

    def test_restored_projection_inventory_and_markers_are_exact(self):
        self.create_owned()
        directory = self.owned / "runner-temp"
        with self.assertRaisesRegex(ValueError, "Historical projection inventory changed"):
            bridge.require_restored_projections(directory)
        for number in ("045", "048", "052", "053", "054", "055", "056"):
            projection = directory / f"wsr-adr{number}-current-view"
            projection.mkdir()
            (projection / ".restored").write_text("restored")
        bridge.require_restored_projections(directory)
        pending = directory / "wsr-adr056-current-view" / ".prepared"
        pending.write_text("incomplete")
        with self.assertRaisesRegex(ValueError, "Historical projection not restored"):
            bridge.require_restored_projections(directory)
        pending.unlink()
        (directory / "wsr-adr056-current-view" / ".restored").unlink()
        with self.assertRaisesRegex(ValueError, "Historical projection not restored"):
            bridge.require_restored_projections(directory)

    def test_readonly_git_pack_is_removed_only_inside_owned_root(self):
        self.create_owned()
        pack = self.owned / "legacy" / "objects" / "pack" / "synthetic.pack"
        pack.parent.mkdir(parents=True)
        pack.write_bytes(b"synthetic pack")
        pack.chmod(stat.S_IREAD)
        sentinel = self.root / "unrelated.txt"
        sentinel.write_text("preserve")
        bridge.remove_owned(self.owned, self.token)
        self.assertFalse(self.owned.exists())
        self.assertEqual(sentinel.read_text(), "preserve")

    def test_foreign_owner_prevents_any_cleanup(self):
        state = self.create_owned()
        bridge.write_state(self.owned, state)
        (self.owned / ".owner").write_text("b" * 48)
        with patch.object(bridge, "remove_owned") as remove:
            with self.assertRaisesRegex(ValueError, "Owner marker mismatch"):
                bridge.finish(SOURCE)
            remove.assert_not_called()
        self.assertTrue(self.owned.exists())

    def test_unprepared_restore_is_noop_but_normal_step_is_rejected(self):
        restore = next(entry for entry in run_entries() if entry.get("if") == "always()")
        with patch.object(bridge, "pinned_manifest", return_value=bridge.expected_manifest(pinned())), redirect_stdout(io.StringIO()):
            self.assertEqual(bridge.run_step(SOURCE, restore["index"]), 0)
            with self.assertRaisesRegex(ValueError, "Contract checkout was not prepared"):
                bridge.run_step(SOURCE, run_entries()[0]["index"])

    def test_execution_failure_is_recorded_and_all_later_restores_remain_selectable(self):
        state = self.create_owned()
        state["results"] = []
        bridge.write_state(self.owned, state)
        first = run_entries()[0]
        with patch.object(bridge, "pinned_manifest", return_value=bridge.expected_manifest(pinned())), \
                patch.object(bridge, "execute", return_value=17) as execute, redirect_stdout(io.StringIO()):
            self.assertEqual(bridge.run_step(SOURCE, first["index"]), 17)
            self.assertEqual(execute.call_args.args[1], self.owned / "legacy")
            self.assertEqual(execute.call_args.args[2]["RUNNER_TEMP"], str(self.owned / "runner-temp"))
        recorded = bridge.read_state(self.owned, SOURCE)
        self.assertTrue(recorded["failed"])
        self.assertEqual(recorded["results"], [{"index": first["index"], "code": 17}])
        for restore in [entry for entry in run_entries() if entry.get("if") == "always()"]:
            selected = bridge.next_allowed_step(entries(), recorded["results"], recorded["failed"], restore["index"])
            recorded["results"].append({"index": selected["index"], "code": 0})

    def test_process_exception_records_failure_before_allowing_restoration(self):
        state = self.create_owned()
        state["results"] = []
        bridge.write_state(self.owned, state)
        with patch.object(bridge, "pinned_manifest", return_value=bridge.expected_manifest(pinned())), \
                patch.object(bridge, "execute", side_effect=OSError("synthetic process failure")), \
                redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(OSError, "synthetic process failure"):
                bridge.run_step(SOURCE, run_entries()[0]["index"])
        recorded = bridge.read_state(self.owned, SOURCE)
        self.assertTrue(recorded["failed"])
        self.assertEqual(recorded["results"], [{"index": run_entries()[0]["index"], "code": 125}])

    def test_post_prepare_body_tamper_records_failure_before_restore_steps(self):
        state = self.create_owned()
        state["results"] = []
        bridge.write_state(self.owned, state)
        with patch.object(bridge, "pinned_manifest", return_value=bridge.expected_manifest(pinned())), \
                patch.object(bridge, "digest", return_value="0" * 64), \
                patch.object(bridge, "execute") as execute, redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(ValueError, "Run body changed after preparation"):
                bridge.run_step(SOURCE, run_entries()[0]["index"])
            execute.assert_not_called()
        recorded = bridge.read_state(self.owned, SOURCE)
        self.assertTrue(recorded["failed"])
        self.assertEqual(recorded["results"], [{"index": run_entries()[0]["index"], "code": 125}])
        restore = next(entry for entry in run_entries() if entry.get("if") == "always()")
        self.assertEqual(bridge.next_allowed_step(entries(), recorded["results"], True, restore["index"]), restore)


if __name__ == "__main__":
    unittest.main()
