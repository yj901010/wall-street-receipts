#!/usr/bin/env python3
"""Offline identity, exception and custody tests for the step-12-only fixture."""
from __future__ import annotations

from contextlib import contextmanager
import os
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import legacy_environment as fixture


SOURCE = Path(__file__).resolve().parents[2]


class LegacyEnvironmentTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="wsr-ci-executable-fixture-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.legacy = self.root / "legacy"
        self.target = self.legacy / fixture.MODULE_PATH
        self.target.parent.mkdir(parents=True)
        self.original_bytes = (SOURCE / fixture.MODULE_PATH).read_bytes()
        self.assertEqual(fixture._blob_identity(self.original_bytes), fixture.MODULE_BLOB)
        self.target.write_bytes(self.original_bytes)
        self.original_mode = stat.S_IMODE(self.target.stat().st_mode)
        self.responses = {
            ("rev-parse", "HEAD"): (fixture.BASELINE + "\n").encode(),
            ("rev-parse", "--abbrev-ref", "HEAD"): b"HEAD\n",
            ("ls-tree", "-z", "HEAD", "--", fixture.MODULE_PATH): fixture.MODULE_TREE_RECORD,
            ("status", "--porcelain=v1", "--untracked-files=all"): b"",
        }

        def read(legacy, *args):
            self.assertEqual(legacy, self.legacy)
            self.assertIn(args, self.responses)
            return self.responses[args]

        self.git_read = Mock(side_effect=read)

    @contextmanager
    def posix_modes(self):
        """Emulate only permission bits so POSIX failure paths run on Windows too."""
        state = {"mode": 0o644}
        actual_regular_info = fixture._regular_info

        def info(path):
            actual = actual_regular_info(path)
            return SimpleNamespace(st_dev=actual.st_dev, st_ino=actual.st_ino,
                                   st_mode=(actual.st_mode & ~0o7777) | state["mode"])

        def chmod(path, mode):
            self.assertEqual(path, self.target)
            state["mode"] = mode

        with patch.object(fixture, "_WINDOWS", False), \
                patch.object(fixture, "_regular_info", side_effect=info), \
                patch.object(fixture, "_set_mode", side_effect=chmod) as change:
            yield state, change

    def test_other_step_indexes_are_noops_without_git_or_filesystem_access(self):
        for index in (1, 11, 13, 85, -1):
            with self.subTest(index=index), patch.object(fixture, "_set_mode") as change:
                with fixture.legacy_step_environment(index, self.root / "missing", self.git_read):
                    pass
                self.git_read.assert_not_called()
                change.assert_not_called()

    def test_posix_adds_owner_execute_only_then_restores_exact_mode_and_bytes(self):
        with self.posix_modes() as (state, change):
            with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                self.assertEqual(state["mode"], 0o744)
                self.assertEqual(self.target.read_bytes(), self.original_bytes)
            self.assertEqual(state["mode"], 0o644)
            self.assertEqual([call.args[1] for call in change.call_args_list], [0o744, 0o644])
        self.assertEqual(self.git_read.call_count, 8)
        self.assertEqual(self.target.read_bytes(), self.original_bytes)

    @unittest.skipUnless(os.name == "posix", "Real POSIX permission bits require Linux/macOS")
    def test_real_posix_mode_is_reversible(self):
        with fixture.legacy_step_environment(12, self.legacy, self.git_read):
            self.assertEqual(stat.S_IMODE(self.target.stat().st_mode), self.original_mode | stat.S_IXUSR)
        self.assertEqual(stat.S_IMODE(self.target.stat().st_mode), self.original_mode)

    def test_windows_never_changes_physical_mode(self):
        with patch.object(fixture, "_WINDOWS", True), patch.object(fixture, "_set_mode") as change:
            with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                self.assertEqual(stat.S_IMODE(self.target.stat().st_mode), self.original_mode)
            change.assert_not_called()
        self.assertEqual(self.git_read.call_count, 8)

    def test_body_exceptions_restore_mode_and_propagate_original_exception(self):
        for failure in (RuntimeError("execution failed"), KeyboardInterrupt("cancelled")):
            with self.subTest(kind=type(failure).__name__), self.posix_modes() as (state, _):
                with self.assertRaises(type(failure)) as observed:
                    with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                        raise failure
                self.assertIs(observed.exception, failure)
                self.assertEqual(state["mode"], 0o644)

    def test_chmod_that_partially_succeeds_then_fails_is_reversed(self):
        with self.posix_modes() as (state, change):
            first = True

            def partial_failure(path, mode):
                nonlocal first
                state["mode"] = mode
                if first:
                    first = False
                    raise OSError("synthetic chmod failure")

            change.side_effect = partial_failure
            with self.assertRaisesRegex(OSError, "synthetic chmod failure"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.fail("body must not execute after chmod failure")
            self.assertEqual(state["mode"], 0o644)

    def test_each_git_identity_or_cleanliness_failure_rejects_before_chmod(self):
        for key, incorrect in (
            (("rev-parse", "HEAD"), b"0" * 40 + b"\n"),
            (("rev-parse", "--abbrev-ref", "HEAD"), b"feature/current\n"),
            (("ls-tree", "-z", "HEAD", "--", fixture.MODULE_PATH), fixture.MODULE_TREE_RECORD.replace(b"100644", b"100755")),
            (("ls-tree", "-z", "HEAD", "--", fixture.MODULE_PATH), fixture.MODULE_TREE_RECORD.replace(fixture.MODULE_BLOB.encode(), b"0" * 40)),
            (("ls-tree", "-z", "HEAD", "--", fixture.MODULE_PATH), b""),
            (("status", "--porcelain=v1", "--untracked-files=all"), b" M unexpected\n"),
        ):
            with self.subTest(key=key, incorrect=incorrect):
                original = self.responses[key]
                self.responses[key] = incorrect
                try:
                    with patch.object(fixture, "_set_mode") as change:
                        with self.assertRaisesRegex(ValueError, "Legacy step 12 execution fixture"):
                            with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                                self.fail("body must not execute")
                        change.assert_not_called()
                finally:
                    self.responses[key] = original

    def test_preexisting_byte_tamper_is_rejected_before_mode_change(self):
        self.target.write_bytes(self.original_bytes + b"# unexpected\n")
        with patch.object(fixture, "_set_mode") as change:
            with self.assertRaisesRegex(ValueError, "physical module bytes differ"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.fail("body must not execute")
            change.assert_not_called()

    def test_already_executable_physical_module_is_rejected_on_posix(self):
        with self.posix_modes() as (state, change):
            state["mode"] = 0o744
            with self.assertRaisesRegex(ValueError, "already executable"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.fail("body must not execute")
            change.assert_not_called()

    def test_body_byte_tamper_restores_mode_but_fails_without_repairing_bytes(self):
        changed = self.original_bytes + b"# changed during execution\n"
        with self.posix_modes() as (state, _):
            with self.assertRaisesRegex(ValueError, "module bytes changed"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.target.write_bytes(changed)
            self.assertEqual(state["mode"], 0o644)
        self.assertEqual(self.target.read_bytes(), changed)

    def test_post_execution_git_failure_is_fatal_after_mode_restoration(self):
        with self.posix_modes() as (state, _):
            with self.assertRaisesRegex(ValueError, "historical checkout is not clean"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.responses[("status", "--porcelain=v1", "--untracked-files=all")] = b" M other-file\n"
            self.assertEqual(state["mode"], 0o644)

    def test_failed_mode_restoration_does_not_report_success(self):
        with self.posix_modes() as (state, change):
            def only_add(path, mode):
                if mode == 0o744:
                    state["mode"] = mode

            change.side_effect = only_add
            with self.assertRaisesRegex(ValueError, "physical module mode was not restored"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    pass

    def test_restoration_error_retains_original_execution_error_as_context(self):
        original = RuntimeError("original body failure")
        with self.posix_modes() as (_, change):
            calls = 0
            real_change = change.side_effect

            def fail_restore(path, mode):
                nonlocal calls
                calls += 1
                if calls == 2:
                    raise OSError("restoration failed")
                real_change(path, mode)

            change.side_effect = fail_restore
            with self.assertRaisesRegex(OSError, "restoration failed") as observed:
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    raise original
            self.assertIs(observed.exception.__context__, original)

    def test_replaced_module_is_not_chmoded_after_execution(self):
        with self.posix_modes() as (_, change):
            with self.assertRaisesRegex(ValueError, "module was replaced"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    replacement = self.root / "replacement"
                    replacement.write_bytes(self.original_bytes)
                    replacement.replace(self.target)
            self.assertEqual(change.call_count, 1)

    def test_missing_module_is_rejected_without_permission_changes(self):
        self.target.unlink()
        with patch.object(fixture, "_set_mode") as change:
            with self.assertRaises(FileNotFoundError):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.fail("body must not execute")
            change.assert_not_called()

    def test_nonregular_module_is_rejected_without_permission_changes(self):
        self.target.unlink()
        self.target.mkdir()
        with patch.object(fixture, "_set_mode") as change:
            with self.assertRaisesRegex(ValueError, "regular nonlinked file"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.fail("body must not execute")
            change.assert_not_called()

    def test_hard_link_is_rejected_without_chmoding_outside_file(self):
        outside = self.root / "outside"
        self.target.replace(outside)
        try:
            self.target.hardlink_to(outside)
        except OSError as error:
            self.skipTest(f"Hard links unavailable: {type(error).__name__}")
        before = stat.S_IMODE(outside.stat().st_mode)
        with patch.object(fixture, "_set_mode") as change:
            with self.assertRaisesRegex(ValueError, "hard-linked module rejected"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.fail("body must not execute")
            change.assert_not_called()
        self.assertEqual(stat.S_IMODE(outside.stat().st_mode), before)

    def test_symlink_is_rejected_without_chmoding_outside_file(self):
        outside = self.root / "outside"
        self.target.replace(outside)
        try:
            self.target.symlink_to(outside)
        except OSError as error:
            self.skipTest(f"Symlinks unavailable: {type(error).__name__}")
        with patch.object(fixture, "_set_mode") as change:
            with self.assertRaisesRegex(ValueError, "escaped|nonlinked"):
                with fixture.legacy_step_environment(12, self.legacy, self.git_read):
                    self.fail("body must not execute")
            change.assert_not_called()


if __name__ == "__main__":
    unittest.main()
