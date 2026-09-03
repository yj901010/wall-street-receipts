#!/usr/bin/env python3
"""Regression tests for workflow platform budgets (no network or Git writes)."""

from contextlib import redirect_stderr, redirect_stdout
import io
from pathlib import Path
import tempfile
import unittest

from validate_limits import (
    MAX_RUN_CHARACTERS,
    MAX_WORKFLOW_BYTES,
    load_workflow,
    main,
    validate_file,
    validate_repository,
    validate_workflow_bytes,
)


def workflow(run: str) -> bytes:
    return ("on: push\njobs:\n  checks:\n    steps:\n      - run: '" + run + "'\n").encode("utf-8")


class WorkflowLimitsTests(unittest.TestCase):
    def assert_valid(self, content: bytes):
        result = validate_workflow_bytes(content)
        self.assertEqual(result.issues, ())
        return result

    def assert_invalid(self, content: bytes, text: str):
        result = validate_workflow_bytes(content)
        self.assertTrue(any(text in issue for issue in result.issues), result.issues)
        return result

    def test_exact_file_byte_budget(self):
        content = workflow("echo okay") + b"# "
        content += b"x" * (MAX_WORKFLOW_BYTES - len(content))
        result = self.assert_valid(content)
        self.assertEqual(result.byte_count, MAX_WORKFLOW_BYTES)
        self.assert_invalid(content + b"x", "500,001 UTF-8 bytes")

    def test_multibyte_file_budget_is_bytes_not_characters(self):
        content = workflow("echo okay") + b"# "
        remaining = MAX_WORKFLOW_BYTES - len(content)
        content += "한".encode("utf-8") * (remaining // 3) + b"x" * (remaining % 3)
        self.assert_valid(content)
        self.assertLess(len(content.decode("utf-8")), MAX_WORKFLOW_BYTES)
        self.assert_invalid(content + "글".encode("utf-8"), "UTF-8 bytes")

    def test_exact_run_character_limit(self):
        result = self.assert_valid(workflow("x" * MAX_RUN_CHARACTERS))
        self.assertEqual(result.max_run_characters, MAX_RUN_CHARACTERS)
        self.assert_invalid(workflow("x" * (MAX_RUN_CHARACTERS + 1)), "21,001 Unicode characters")

    def test_run_uses_unicode_code_points(self):
        for character in ("한", "🙂"):
            with self.subTest(character=character):
                result = self.assert_valid(workflow(character * MAX_RUN_CHARACTERS))
                self.assertEqual(result.max_run_characters, MAX_RUN_CHARACTERS)
                self.assert_invalid(workflow(character * (MAX_RUN_CHARACTERS + 1)), "21,001")

    def test_each_step_has_its_own_budget(self):
        source = "jobs:\n  checks:\n    steps:\n" + ("      - run: '" + "x" * 11_000 + "'\n") * 2
        result = self.assert_valid(source.encode())
        self.assertEqual(result.run_count, 2)
        self.assertEqual(result.max_run_characters, 11_000)

    def test_all_jobs_and_steps_are_counted(self):
        source = (
            "jobs:\n  one:\n    steps:\n      - uses: actions/checkout@v6\n"
            "      - run: echo one\n  two:\n    steps:\n      - run: '"
            + "x" * 21_001 + "'\n"
        )
        result = self.assert_invalid(source.encode(), "job 'two', step 1")
        self.assertEqual(result.run_count, 2)

    def test_expressions_are_literal_characters_not_evaluated(self):
        expression = "${{ secrets.NEVER_READ_THIS }}"
        run = expression + "x" * (MAX_RUN_CHARACTERS - len(expression))
        self.assert_valid(workflow(run))
        self.assert_invalid(workflow(run + "x"), "21,001")

    def test_literal_folded_and_chomping_scalars(self):
        prefix = "jobs:\n  checks:\n    steps:\n      - run: "
        for indicator, body, length in (
            ("|", "          aa\n          bb\n", 6),
            ("|-", "          aa\n          bb\n", 5),
            ("|+", "          aa\n          bb\n\n", 7),
            (">", "          aa\n          bb\n", 6),
            (">-", "          aa\n          bb\n", 5),
        ):
            with self.subTest(indicator=indicator):
                result = self.assert_valid((prefix + indicator + "\n" + body).encode())
                self.assertEqual(result.max_run_characters, length)

    def test_decoded_escapes_and_crlf_use_scalar_value(self):
        source = 'jobs:\r\n  checks:\r\n    steps:\r\n      - run: "\\uD55C\\U0001F642"\r\n'
        self.assertEqual(self.assert_valid(source.encode()).max_run_characters, 2)

    def test_duplicate_run_job_and_unrelated_keys_are_rejected(self):
        cases = (
            b"jobs:\n  x:\n    steps:\n      - run: first\n        run: second\n",
            b"jobs:\n  x: {uses: a}\n  x: {uses: b}\n",
            b"on: push\non: pull_request\njobs:\n  x: {uses: a}\n",
        )
        for content in cases:
            with self.subTest(content=content):
                self.assert_invalid(content, "duplicate mapping key")

    def test_aliases_fail_closed_including_recursive_aliases(self):
        cases = (
            b"value: &command echo okay\njobs:\n  x:\n    steps:\n      - run: *command\n",
            b"jobs:\n  x: &job\n    steps: [*job]\n",
        )
        for content in cases:
            with self.subTest(content=content):
                self.assert_invalid(content, "aliases are unsupported")

    def test_merge_keys_fail_closed_without_alias(self):
        self.assert_invalid(
            b"jobs:\n  x:\n    steps:\n      - <<: {run: echo hidden}\n        run: echo visible\n",
            "merge keys are unsupported",
        )

    def test_base_loader_preserves_on_and_boolean_looking_keys(self):
        result = self.assert_valid(
            b"on: push\ntrue: informational\njobs:\n  yes:\n    steps:\n      - run: no\n"
        )
        self.assertEqual(result.max_run_characters, 2)

    def test_reusable_job_and_action_step_need_no_run(self):
        self.assert_valid(
            b"jobs:\n  reuse:\n    uses: owner/repo/.github/workflows/ci.yml@main\n"
            b"  check:\n    steps:\n      - uses: actions/checkout@v6\n"
        )

    def test_invalid_shapes_cannot_hide_run_values(self):
        cases = (
            (b"jobs: []", "jobs must be"),
            (b"jobs:\n  x: []", "must be a mapping"),
            (b"jobs:\n  x: {run: hidden}", "steps sequence"),
            (b"jobs:\n  x: {steps: {run: hidden}}", "steps must be a sequence"),
            (b"jobs:\n  x: {steps: hidden}", "steps must be a sequence"),
            (b"jobs:\n  x: {steps: [[{run: hidden}]]}", "must be a mapping"),
            (b"jobs:\n  x: {steps: [{run: {value: hidden}}]}", "run must be a string"),
            (b"jobs:\n  x: {steps: [{run: [hidden]}]}", "run must be a string"),
            (b"jobs:\n  x: {steps: [{steps: [{run: hidden}]}]}", "nested steps are invalid"),
            (b"jobs:\n  x: {uses: other, steps: [{run: hidden}]}", "cannot also contain steps"),
            (b"jobs:\n  x: {steps: [{uses: other, run: hidden}]}", "cannot both be present"),
            (b"jobs:\n  x: {uses: other, run: hidden}", "run belongs inside a step"),
        )
        for content, message in cases:
            with self.subTest(content=content):
                self.assert_invalid(content, message)

    def test_invalid_utf8_and_yaml_fail_with_bounded_diagnostics(self):
        self.assert_invalid(b"\xff", "valid UTF-8")
        self.assert_invalid(b"jobs: [", "line")
        self.assert_invalid(b"---\njobs: {}\n---\njobs: {}\n", "another document")
        self.assert_invalid(b"[]", "workflow must be a mapping")
        self.assert_invalid(b"jobs: {}", "nonempty mapping")

    def test_public_loader_can_parse_digest_pinned_oversized_baseline(self):
        source = workflow("okay").decode() + "# " + "x" * MAX_WORKFLOW_BYTES
        self.assertEqual(load_workflow(source)["jobs"]["checks"]["steps"][0]["run"], "okay")
        self.assert_invalid(source.encode(), "UTF-8 bytes")

    def test_public_loader_failure_has_source_label(self):
        with self.assertRaisesRegex(ValueError, "historical.yml:.*duplicate mapping key"):
            load_workflow("jobs: {}\njobs: {}", "historical.yml")


class RepositoryLimitsTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wsr-ci-limits-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.directory = self.root / ".github" / "workflows"
        self.directory.mkdir(parents=True)

    def test_all_yml_and_yaml_workflows_are_checked(self):
        (self.directory / "ci.yml").write_bytes(workflow("echo okay"))
        (self.directory / "release.yaml").write_bytes(workflow("x" * 21_001))
        (self.directory / "notes.txt").write_bytes(b"not a workflow")
        results = validate_repository(self.root)
        self.assertEqual([result.path for result in results], [
            ".github/workflows/ci.yml", ".github/workflows/release.yaml",
        ])
        self.assertFalse(results[0].issues)
        self.assertTrue(results[1].issues)

    def test_empty_workflow_directory_fails(self):
        self.assertIn("no workflows found", validate_repository(self.root)[0].issues[0])

    def test_missing_workflow_directory_fails(self):
        self.assertIn("missing", validate_repository(self.root / "missing")[0].issues[0])

    def test_oversized_file_fails_without_parsing_yaml(self):
        (self.directory / "ci.yml").write_bytes(b"[" * (MAX_WORKFLOW_BYTES + 1))
        self.assertIn("UTF-8 bytes", validate_repository(self.root)[0].issues[0])

    def test_public_file_validator_and_non_file_rejection(self):
        path = self.directory / "ci.yml"
        self.assertIn("regular file", validate_file(path).issues[0])
        path.write_bytes(workflow("echo okay"))
        self.assertFalse(validate_file(path).issues)
        self.assertIn("regular file", validate_file(self.directory).issues[0])

    def test_cli_pass_and_fail_exit_codes_and_diagnostics(self):
        target = self.directory / "ci.yml"
        target.write_bytes(workflow("echo okay"))
        stdout, stderr = io.StringIO(), io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            self.assertEqual(main(["--root", str(self.root)]), 0)
        self.assertIn("CI limits PASS", stdout.getvalue())
        self.assertEqual(stderr.getvalue(), "")
        target.write_bytes(workflow("x" * 21_001))
        with redirect_stdout(stdout), redirect_stderr(stderr):
            self.assertEqual(main(["--root", str(self.root)]), 1)
        self.assertIn(".github/workflows/ci.yml", stderr.getvalue())
        self.assertIn("Move this command into a script", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
