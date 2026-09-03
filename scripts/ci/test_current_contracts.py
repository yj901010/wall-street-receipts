"""Mutation coverage for the exact test-only current-tree migration."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

import current_contracts as current
import run_contracts as bridge


class CurrentTestMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = Path(__file__).resolve().parents[2]
        cls.original = bridge.git(cls.source, "show", f"{current.BASELINE}:{current.TEST_PATH}")
        cls.expected = current.isolated_test_bytes(cls.original)

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="wsr-current-contract-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.path = self.root / current.TEST_PATH
        self.path.parent.mkdir(parents=True)
        self.path.write_bytes(self.expected)
        self.base = {current.TEST_PATH: current.blob_record(self.original)}
        self.head = {current.TEST_PATH: current.blob_record(self.expected)}
        self.git = Mock(return_value=self.original)

    def test_exact_migration_before_and_after_commit_preserves_other_tree_records(self):
        for head in (self.base, self.head):
            self.assertEqual(current.verify_current_test(self.root, self.git, self.base, head), head)
        self.assertNotIn(current.TEST_PATH, bridge.FIXED_CI_PATHS)
        self.assertEqual(self.expected.replace(current.ISOLATED_ANNOTATION, b"@SpringBootTest\n"), self.original)

    def test_real_current_test_contains_only_the_reviewed_change(self):
        self.assertEqual((self.source / current.TEST_PATH).read_bytes().replace(b"\r\n", b"\n"), self.expected)

    def test_old_database_assertion_relaxation_and_transactional_shortcuts_fail(self):
        for raw in (self.original, self.expected.replace(b"wsr-sec-collection-attempt-persistence", b"wsr-test"),
                    self.expected + b"// unreviewed\n", self.expected.replace(b"@ActiveProfiles", b"@Transactional\n@ActiveProfiles")):
            with self.subTest(raw=raw[-30:]):
                self.path.write_bytes(raw)
                with self.assertRaisesRegex(ValueError, "exact isolated-database migration"):
                    current.verify_current_test(self.root, self.git, self.base, self.head)

    def test_committed_blob_mode_type_or_deletion_cannot_enter_exception(self):
        for record in (None, "100755 blob " + "0" * 40, "120000 blob " + "0" * 40,
                       current.blob_record(self.expected + b"// bypass\n")):
            with self.subTest(record=record), self.assertRaisesRegex(ValueError, "Unreviewed committed"):
                current.verify_current_test(self.root, self.git, self.base, {current.TEST_PATH: record})

    def test_missing_current_test_and_wrong_pinned_annotation_fail(self):
        self.path.unlink()
        with self.assertRaisesRegex(ValueError, "exact isolated-database migration"):
            current.verify_current_test(self.root, self.git, self.base, self.head)
        with self.assertRaisesRegex(ValueError, "Pinned attempt test annotation"):
            current.isolated_test_bytes(b"@SpringBootTest\n@SpringBootTest\n")

    def test_other_source_changes_remain_rejected_after_exact_migration(self):
        adjusted = current.verify_current_test(self.root, self.git, self.base, self.head)
        with self.assertRaisesRegex(ValueError, "Product tree differs"):
            bridge.compare_product_trees(adjusted, {**self.head, "apps/api/src/main/New.java": "100644 blob " + "1" * 40}, bridge.FIXED_CI_PATHS)


if __name__ == "__main__":
    unittest.main()
