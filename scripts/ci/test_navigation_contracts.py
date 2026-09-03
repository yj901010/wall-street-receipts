"""Mutation tests for the closed current-source SEC navigation migration."""
import hashlib
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import Mock, patch

from current_contracts import blob_record
import navigation_contracts as navigation
import run_contracts as bridge


SOURCE = Path(__file__).resolve().parents[2]


class NavigationMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = {relative: bridge.git(SOURCE, "show", f"{navigation.BASELINE}:{relative}")
                        for relative in navigation.NAVIGATION_PATHS}
        cls.expected = {
            relative: navigation.migrated_source(relative, raw) if relative in navigation.SOURCE_EDITS
            else (SOURCE / relative).read_bytes().replace(b"\r\n", b"\n")
            for relative, raw in cls.original.items()
        }

    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-navigation-contract-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for relative, raw in self.expected.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        self.baseline = {relative: blob_record(raw) for relative, raw in self.original.items()}
        self.current = {relative: blob_record(raw) for relative, raw in self.expected.items()}
        self.git = Mock(side_effect=lambda root, command, spec: self.original[spec.split(":", 1)[1]])

    def verify(self, head=None, baseline=None):
        return navigation.verify_navigation(self.root, self.git, baseline or self.baseline,
                                            head or self.current)

    def test_closed_inventory_and_no_general_product_path_exemption(self):
        self.assertEqual(len(navigation.SOURCE_EDITS), 7)
        self.assertEqual(len(navigation.TEST_SHA256), 9)
        self.assertEqual(len(navigation.NAVIGATION_PATHS), 16)
        self.assertFalse(navigation.NAVIGATION_PATHS & bridge.FIXED_CI_PATHS)
        self.assertFalse(any(path.startswith(("fixtures/", "schemas/", "apps/api/"))
                             for path in navigation.NAVIGATION_PATHS))

    def test_exact_source_is_accepted_before_and_after_commit_without_mutation(self):
        for head in (self.baseline, self.current):
            with self.subTest(head=head is self.current):
                self.assertEqual(self.verify(head), head)
        for relative, expected in self.expected.items():
            self.assertEqual((self.root / relative).read_bytes(), expected)

    def test_actual_checkout_matches_exact_runtime_edits_and_reviewed_test_bytes(self):
        navigation.verify_navigation(SOURCE, self.git, self.baseline, self.current)
        for relative, digest in navigation.TEST_SHA256.items():
            self.assertEqual(hashlib.sha256(self.expected[relative]).hexdigest(), digest)

    def test_ninth_camel_case_key_labels_and_bare_destination_are_not_silently_omitted(self):
        messages = self.expected["apps/web/src/lib/i18n/messages.ts"].decode()
        declaration = messages.split("export const NAVIGATION_ITEMS = [", 1)[1].split("] as const;", 1)[0]
        self.assertEqual(re.findall(r'"([^"\n]+)"', declaration), [
            "dashboard", "market", "calls", "institutions", "analysts", "maps", "screener",
            "methodology", "secEvidence"])
        self.assertEqual(messages.count('secEvidence: "SEC 증거"'), 1)
        self.assertEqual(messages.count('secEvidence: "SEC evidence"'), 1)
        header = self.expected["apps/web/src/components/site-header.tsx"].decode()
        self.assertEqual(re.findall(r'href="([^"]+)"', header), [
            "/", "/", "/market", "/calls", "/institutions", "/analysts", "/maps/sp500", "/screener",
            "/methodology", "/research/sec/filing-history"])
        self.assertNotIn('href="/markets/sp500"', header)
        self.assertEqual(header.count('current === "secEvidence" ? "page" : undefined'), 1)
        self.assertEqual(header.count("prefetch={false}"), 1)

    def test_every_sec_route_state_is_active_without_changing_data_mode_or_evidence(self):
        for name in ("page.tsx", "loading.tsx", "error.tsx", "not-found.tsx"):
            relative = navigation.SEC_DIRECTORY + name
            expected = self.expected[relative]
            self.assertEqual(expected.count(b'current="secEvidence"'), 1)
            self.assertEqual(expected.replace(b'        current="secEvidence"\n', b'')
                             .replace(b' current="secEvidence"', b''), self.original[relative])

    def test_missing_duplicate_reordered_or_forged_menu_fields_are_rejected(self):
        header = "apps/web/src/components/site-header.tsx"
        messages = "apps/web/src/lib/i18n/messages.ts"
        cases = (
            (header, b'current === "secEvidence"', b'current === "methodology"'),
            (header, b'/research/sec/filing-history"', b'/research/sec/filing-history?latest=true"'),
            (header, b'/research/sec/filing-history"', b'https://www.sec.gov/"'),
            (header, b'prefetch={false}', b'prefetch={true}'),
            (header, b'{messages.navigation.secEvidence}', b'{messages.navigation.methodology}'),
            (header, b'href="/screener"', b'href="/markets/sp500"'),
            (messages, b'  "secEvidence",\n', b''),
            (messages, b'  "secEvidence",\n', b'  "secEvidence",\n  "secEvidence",\n'),
            (messages, b'  "methodology",\n  "secEvidence",', b'  "secEvidence",\n  "methodology",'),
            (messages, 'SEC 증거'.encode(), 'SEC 실시간'.encode()),
            (messages, b'SEC evidence', b'SEC latest'),
        )
        for relative, before, after in cases:
            with self.subTest(relative=relative, after=after):
                path = self.root / relative
                path.write_bytes(self.expected[relative].replace(before, after, 1))
                with self.assertRaisesRegex(ValueError, "Unreviewed current navigation"):
                    self.verify()
                path.write_bytes(self.expected[relative])

    def test_any_other_runtime_byte_or_test_assertion_change_is_rejected(self):
        for relative, raw in self.expected.items():
            with self.subTest(relative=relative):
                path = self.root / relative
                for candidate in (raw + b"\n// unrelated change\n", self.original[relative]):
                    path.write_bytes(candidate)
                    with self.assertRaisesRegex(ValueError, "Unreviewed current|behavioral tests changed"):
                        self.verify()
                path.write_bytes(raw)

    def test_forged_mode_type_blob_deletion_or_unrelated_committed_product_fails(self):
        for relative in navigation.NAVIGATION_PATHS:
            for record in (None, "100755 blob " + "0" * 40, "120000 blob " + "0" * 40,
                           "160000 commit " + "0" * 40, blob_record(b"unreviewed")):
                with self.subTest(relative=relative, record=record):
                    head = {**self.current, relative: record}
                    with self.assertRaisesRegex(ValueError, "Unreviewed committed navigation"):
                        self.verify(head)
        head = {**self.current, "apps/api/src/main/Unrelated.java": blob_record(b"unreviewed")}
        adjusted = self.verify(head)
        with self.assertRaisesRegex(ValueError, "Product tree differs"):
            bridge.compare_product_trees(adjusted, head, bridge.FIXED_CI_PATHS)

    def test_wrong_baseline_or_ambiguous_insertion_point_fails(self):
        relative = "apps/web/src/components/site-header.tsx"
        with self.assertRaisesRegex(ValueError, "Pinned navigation mode/type/object"):
            self.verify(baseline={**self.baseline, relative: blob_record(b"wrong baseline")})
        for raw in (b"no insertion point", self.original[relative] * 2):
            with self.assertRaisesRegex(ValueError, "insertion point changed"):
                navigation.migrated_source(relative, raw)

    def test_crlf_checkout_is_supported_without_changing_git_blob_identity(self):
        for relative, raw in self.expected.items():
            (self.root / relative).write_bytes(raw.replace(b"\n", b"\r\n"))
        self.verify()

    def test_missing_file_and_symlink_parent_are_rejected(self):
        relative = "apps/web/src/components/site-header.tsx"
        path = self.root / relative
        path.unlink()
        with self.assertRaisesRegex(ValueError, "missing or linked"):
            self.verify()
        path.write_bytes(self.expected[relative])
        original_is_symlink = Path.is_symlink
        with patch.object(Path, "is_symlink", lambda candidate: candidate == path.parent or original_is_symlink(candidate)):
            with self.assertRaisesRegex(ValueError, "missing or linked"):
                self.verify()

    def test_physical_executable_source_is_rejected_on_posix(self):
        import os
        if os.name == "nt":
            self.skipTest("Windows does not expose the POSIX executable bit")
        path = self.root / "apps/web/src/components/site-header.tsx"
        path.chmod(0o755)
        with self.assertRaisesRegex(ValueError, "nonexecutable"):
            self.verify()

    def test_current_migration_is_mandatory_before_frozen_tree_comparison(self):
        manifest = bridge.expected_manifest(bridge.baseline_workflow(SOURCE))
        with patch.object(bridge, "git", side_effect=[b"", b""]), \
                patch.object(bridge, "verify_current_test", return_value={}), \
                patch.object(bridge, "verify_navigation", side_effect=ValueError("navigation rejected")) as verify:
            with self.assertRaisesRegex(ValueError, "navigation rejected"):
                bridge.validate_product(SOURCE, manifest)
            verify.assert_called_once()


if __name__ == "__main__":
    unittest.main()
