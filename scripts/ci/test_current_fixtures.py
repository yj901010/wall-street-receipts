"""Current-root execution and workflow wiring tests, without product edits."""
from contextlib import redirect_stderr, redirect_stdout
import copy
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from jsonschema.exceptions import ValidationError

import run_contracts as bridge
import validate_current_fixtures as current


SOURCE = Path(__file__).resolve().parents[2]
STEP_NAME = "Validate current-checkout DEMO revision and outcome contracts"
COMMAND = "python scripts/ci/validate_current_fixtures.py"


class CurrentFixtureWorkflowTests(unittest.TestCase):
    def test_current_gate_is_mandatory_before_historical_preparation(self):
        workflow = bridge.expected_workflow(bridge.baseline_workflow(SOURCE))
        actual = bridge.parse_workflow((SOURCE / bridge.WORKFLOW).read_bytes())
        self.assertEqual(actual, workflow)
        steps = actual["jobs"][bridge.JOB]["steps"]
        selected = [(index, step) for index, step in enumerate(steps) if step.get("name") == STEP_NAME]
        self.assertEqual(selected, [(4, {"name": STEP_NAME, "run": COMMAND})])
        self.assertEqual(steps[5]["run"], "python scripts/ci/run_contracts.py prepare")

    def test_gate_skip_condition_fallback_or_root_redirection_fails_parity(self):
        baseline = bridge.baseline_workflow(SOURCE)
        expected = bridge.expected_workflow(baseline)
        mutations = (
            lambda steps: steps.pop(4),
            lambda steps: steps[4].update({"if": "false"}),
            lambda steps: steps[4].update({"continue-on-error": True}),
            lambda steps: steps[4].update(run=COMMAND + " || true"),
            lambda steps: steps[4].update(run=COMMAND + " --root historical"),
            lambda steps: steps[4].update({"working-directory": "historical"}),
        )
        for mutate in mutations:
            candidate = copy.deepcopy(expected)
            mutate(candidate["jobs"][bridge.JOB]["steps"])
            with patch.object(bridge, "parse_workflow", return_value=candidate):
                with self.assertRaisesRegex(ValueError, "CI metadata"):
                    bridge.validate_workflow_parity(SOURCE, baseline)

    def test_no_fixture_schema_or_application_path_is_unfrozen(self):
        allowed = bridge.permitted_paths(bridge.expected_manifest(bridge.baseline_workflow(SOURCE)))
        self.assertFalse(any(path.startswith(("fixtures/", "schemas/", "apps/")) for path in allowed))
        for path in ("fixtures/v1/call-outcomes.json", "schemas/call-outcome.schema.json", "apps/web/src/new.ts"):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "Product tree differs"):
                bridge.compare_product_trees({}, {path: "100644 blob " + "1" * 40}, allowed)


class CurrentFixtureExecutionTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-current-fixtures-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for directory in ("fixtures/v1", "schemas"):
            shutil.copytree(SOURCE / directory, self.root / directory)

    def test_real_current_records_pass_without_git_and_are_not_modified(self):
        before = {path.relative_to(self.root): path.read_bytes() for path in self.root.rglob("*.json")}
        self.assertFalse((self.root / ".git").exists())
        self.assertEqual(current.validate_current(self.root), {
            "dataMode": "DEMO", "revisions": {"revisionCount": 2, "lineageCount": 1},
            "outcomes": {"methodologyCount": 2, "outcomeCount": 4, "lineageCount": 3}})
        self.assertEqual(before, {path.relative_to(self.root): path.read_bytes() for path in self.root.rglob("*.json")})

    def test_real_current_mutation_is_seen_instead_of_pinned_or_cached_data(self):
        current.validate_current(self.root)
        path = self.root / "fixtures/v1/call-outcomes.json"
        candidate = json.loads(path.read_text(encoding="utf-8"))
        candidate["outcomes"][0]["assetReturn"] = 0
        path.write_text(json.dumps(candidate), encoding="utf-8")
        with self.assertRaises((ValueError, ValidationError)):
            current.validate_current(self.root)

    def test_cli_reads_its_checkout_even_from_unrelated_cwd_with_misleading_env(self):
        scripts = self.root / "scripts/ci"
        scripts.mkdir(parents=True)
        for name in ("fixture_contracts_common.py", "fixture_outcomes.py", "fixture_revisions.py", "validate_current_fixtures.py"):
            shutil.copyfile(SOURCE / "scripts/ci" / name, scripts / name)
        elsewhere = self.root / "unrelated"
        elsewhere.mkdir()
        env = dict(os.environ, GITHUB_WORKSPACE=str(elsewhere), RUNNER_TEMP=str(elsewhere))
        command = [sys.executable, "-B", str(scripts / "validate_current_fixtures.py")]
        result = subprocess.run(command, cwd=elsewhere, env=env, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Current-checkout fixture contracts PASS", result.stdout)
        schema_path = self.root / "schemas/analyst-call-revision.schema.json"
        original_schema = schema_path.read_text(encoding="utf-8")
        schema = json.loads(original_schema)
        schema["properties"]["revisionId"]["$ref"] = "#/$defs/missing"
        schema["description"] = "PRIVATE_SCHEMA_SENTINEL"
        schema_path.write_text(json.dumps(schema), encoding="utf-8")
        broken_ref = subprocess.run(command, cwd=elsewhere, env=env, capture_output=True, text=True, timeout=30)
        self.assertEqual(broken_ref.returncode, 1)
        self.assertIn("Current DEMO fixture contracts failed", broken_ref.stderr)
        self.assertNotIn("Traceback", broken_ref.stderr)
        self.assertNotIn("PRIVATE_SCHEMA_SENTINEL", broken_ref.stderr + broken_ref.stdout)
        schema_path.write_text(original_schema, encoding="utf-8")
        (self.root / "fixtures/v1/call-outcomes.json").write_text("{}", encoding="utf-8")
        failed = subprocess.run(command, cwd=elsewhere, env=env, capture_output=True, text=True, timeout=30)
        self.assertEqual(failed.returncode, 1)
        self.assertNotIn("PASS", failed.stdout)
        self.assertIn("Current DEMO fixture contracts failed", failed.stderr)

    def test_cli_rejects_root_override_and_sanitizes_invalid_evidence(self):
        output = io.StringIO()
        with patch.object(sys, "argv", ["validate_current_fixtures.py", "--root", "historical"]), redirect_stderr(output):
            self.assertEqual(current.main(), 2)
        with patch.object(sys, "argv", ["validate_current_fixtures.py"]), \
                patch.object(current, "validate_current", side_effect=ValueError("PRIVATE_TEST_CONTENT")), \
                redirect_stderr(output), redirect_stdout(output):
            self.assertEqual(current.main(), 1)
        self.assertNotIn("PRIVATE_TEST_CONTENT", output.getvalue())


if __name__ == "__main__":
    unittest.main()
