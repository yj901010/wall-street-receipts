#!/usr/bin/env python3
"""Offline full-guard fixtures and fail-closed tests for the step-83 migration."""
from __future__ import annotations

from functools import lru_cache
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import historical_guard_migrations as migration


SOURCE = Path(__file__).resolve().parents[2]
BASELINE = "3792100f49c496d751d1dd54a7fbdc1b7c2fd275"
SCRIPT = "scripts/ci/legacy/083-guard-adr-055-offline-sec-manifest-api-mode-full-stack-acceptance.sh"
WORKFLOW = ".github/workflows/ci.yml"
RESTORE_MARKER = "      - name: Restore exact ADR-055 repository view\n"
GUARD_MARKER = "      - name: Guard ADR-055 offline SEC manifest API-mode full-stack acceptance\n"
NEXT_STEP = "\n      - name:"
REQUIRED_FILES = (
    "apps/api/src/test/java/com/wallstreetreceipts/api/acceptance/SecManifestAuditAcceptanceSeedHarness.java",
    "apps/api/src/test/java/com/wallstreetreceipts/api/support/SecManifestAuditDemoFixture.java",
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/filinghistory/SecAuditDemoFixtureParityTest.java",
    "scripts/verify-local-full-stack.ps1",
    "apps/web/e2e/sec-manifest-audit.spec.ts",
    "apps/web/src/app/research/sec/filing-history/page.tsx",
    "apps/web/src/lib/providers/api-sec-manifest-audit-provider.server.ts",
    WORKFLOW,
    "README.md",
    "apps/api/README.md",
    "quality/P2_ACCEPTANCE.md",
    "IMPLEMENTATION_LOG.md",
    "decisions/ADR-055-disposable-offline-sec-manifest-audit-api-mode-full-stack-acceptance.md",
)


@lru_cache(maxsize=1)
def script_bytes():
    return (SOURCE / SCRIPT).read_bytes()


@lru_cache(maxsize=1)
def baseline_files():
    environment = {key: value for key, value in os.environ.items()
                   if not key.upper().startswith(("GIT_", "GITHUB_", "GH_"))}
    environment.update(GIT_OPTIONAL_LOCKS="0", GIT_NO_LAZY_FETCH="1", GIT_ALLOW_PROTOCOL="file",
                       GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                       GIT_TERMINAL_PROMPT="0", GIT_ASKPASS=os.devnull)
    files = {}
    for path in REQUIRED_FILES:
        result = subprocess.run(
            ["git", "-c", "core.fsmonitor=false", "-c", f"core.hooksPath={os.devnull}",
             "show", f"{BASELINE}:{path}"], cwd=SOURCE, env=environment,
            capture_output=True, check=True, timeout=30,
        )
        files[path] = result.stdout
    return files


def original_body():
    return script_bytes()[len(migration.HEREDOC_PREFIX):-len(migration.HEREDOC_SUFFIX)].decode("utf-8")


class MigrationShapeTests(unittest.TestCase):
    def test_other_steps_are_noops_even_for_non_python_bytes(self):
        for index in (-1, 1, 12, 82, 84, 85):
            with self.subTest(index=index):
                self.assertIsNone(migration.migrated_python_body(index, b"not a script\xff"))

    def test_only_one_closing_delimiter_changes_and_all_assertions_are_preserved(self):
        raw = script_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(), migration.SCRIPT_SHA256)
        corrected = migration.migrated_python_body(83, raw)
        self.assertIsInstance(corrected, str)
        self.assertEqual(original_body().count(migration.ORIGINAL_DELIMITER), 1)
        self.assertEqual(corrected.count(migration.CORRECTED_DELIMITER), 1)
        self.assertEqual(corrected.replace(migration.CORRECTED_DELIMITER, migration.ORIGINAL_DELIMITER, 1), original_body())
        self.assertEqual(corrected.count("require("), original_body().count("require("))
        self.assertIn("and '\"--force\"' not in adr055_restore", corrected)
        compile(corrected, "<test-step-83-migration>", "exec")

    def test_body_mutation_extra_bytes_crlf_and_malformed_framing_fail_digest(self):
        raw = script_bytes()
        for candidate in (
            raw + b"# extra\n",
            raw.replace(b"--force", b"--other", 1),
            raw.replace(b"\n", b"\r\n"),
            b"python - <<'PYTHON'\n" + raw[len(migration.HEREDOC_PREFIX):],
            raw[:-1],
        ):
            with self.subTest(digest=hashlib.sha256(candidate).hexdigest()):
                with self.assertRaisesRegex(ValueError, "differs from pinned SHA-256"):
                    migration.migrated_python_body(83, candidate)

    def test_framing_is_independently_checked_after_digest(self):
        raw = script_bytes()
        for candidate in (
            b"python - <<'PYTHON'\n" + raw[len(migration.HEREDOC_PREFIX):],
            raw[:-len(migration.HEREDOC_SUFFIX)] + b"OTHER\n",
        ):
            with patch.object(migration, "SCRIPT_SHA256", hashlib.sha256(candidate).hexdigest()):
                with self.assertRaisesRegex(ValueError, "exact Python heredoc framing"):
                    migration.migrated_python_body(83, candidate)

    def test_missing_or_duplicate_delimiter_is_rejected_even_after_digest_check(self):
        raw = script_bytes()
        delimiter = migration.ORIGINAL_DELIMITER.encode()
        for candidate in (raw.replace(delimiter, b"removed", 1),
                          raw.replace(delimiter, delimiter + b"\n# " + delimiter, 1)):
            with patch.object(migration, "SCRIPT_SHA256", hashlib.sha256(candidate).hexdigest()):
                with self.assertRaisesRegex(ValueError, "exactly once"):
                    migration.migrated_python_body(83, candidate)

    def test_compilation_is_mandatory_before_body_is_returned(self):
        raw = script_bytes().replace(b"import re\n", b"not valid python syntax\n", 1)
        with patch.object(migration, "SCRIPT_SHA256", hashlib.sha256(raw).hexdigest()):
            with self.assertRaises(SyntaxError):
                migration.migrated_python_body(83, raw)


class FullHistoricalGuardTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-step83-guard-test-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name).resolve()
        for relative, raw in baseline_files().items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        self.workflow = (self.root / WORKFLOW).read_text(encoding="utf-8")
        self.corrected = migration.migrated_python_body(83, script_bytes())

    def execute(self, body):
        return subprocess.run([sys.executable, "-c", body], cwd=self.root,
                              capture_output=True, text=True, timeout=30)

    def mutate_restore(self, old, new):
        prefix, rest = self.workflow.split(RESTORE_MARKER, 1)
        actual_restore, suffix = rest.split(NEXT_STEP, 1)
        self.assertEqual(actual_restore.count(old), 1)
        modified = actual_restore.replace(old, new, 1)
        (self.root / WORKFLOW).write_text(prefix + RESTORE_MARKER + modified + NEXT_STEP + suffix,
                                         encoding="utf-8")

    def assert_restore_rejected(self):
        result = self.execute(self.corrected)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ADR-055 outer restoration lost partial-failure HEAD recovery", result.stderr)

    def test_original_baseline_failure_is_only_neighboring_guard_token_false_positive(self):
        old_range = self.workflow.split(RESTORE_MARKER, 1)[1].split(GUARD_MARKER, 1)[0]
        actual_restore = self.workflow.split(RESTORE_MARKER, 1)[1].split(NEXT_STEP, 1)[0]
        self.assertNotIn('"--force"', actual_restore)
        self.assertIn('"--force"', old_range[len(actual_restore):])
        self.assertIn("Guard ADR-056 disposable offline Git Flow", old_range)
        original = self.execute(original_body())
        self.assertNotEqual(original.returncode, 0)
        self.assertIn("ADR-055 outer restoration lost partial-failure HEAD recovery", original.stderr)
        corrected = self.execute(self.corrected)
        self.assertEqual(corrected.returncode, 0, corrected.stderr)
        self.assertIn("Validated ADR-055 opt-in synthetic seeding", corrected.stdout)

    def test_corrected_entire_guard_passes_exact_pinned_source_fixture(self):
        result = self.execute(self.corrected)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("13-route/5-check/18-read acceptance", result.stdout)
        self.assertEqual((self.root / WORKFLOW).read_text(encoding="utf-8"), self.workflow)

    def test_real_force_token_inside_adr055_restore_still_fails(self):
        self.mutate_restore("head_state_path.unlink()", 'head_state_path.unlink()\n          # "--force"')
        self.assert_restore_rejected()

    def test_missing_actual_restore_head_check_is_not_satisfied_by_neighbor(self):
        self.mutate_restore("observed_head == original_head", "observed_head != original_head")
        self.assert_restore_rejected()

    def test_missing_actual_symbolic_head_recovery_still_fails(self):
        self.mutate_restore("restored_symbolic_ref == original_symbolic_ref", "restored_symbolic_ref != original_symbolic_ref")
        self.assert_restore_rejected()

    def test_unrelated_seed_assertions_are_still_enforced(self):
        seed = self.root / REQUIRED_FILES[0]
        raw = seed.read_bytes()
        self.assertIn(b"@Test", raw)
        seed.write_bytes(raw.replace(b"@Test", b"@NotATest"))
        result = self.execute(self.corrected)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ADR-055 seed must remain outside default Surefire discovery", result.stderr)


if __name__ == "__main__":
    unittest.main()
