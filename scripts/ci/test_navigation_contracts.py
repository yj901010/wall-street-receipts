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
                        for relative in navigation.NAVIGATION_PATHS - navigation.ADDED_PATHS}
        cls.expected = {
            relative: navigation.migrated_source(relative, raw) if relative in navigation.SOURCE_EDITS
            else (SOURCE / relative).read_bytes().replace(b"\r\n", b"\n")
            for relative in navigation.NAVIGATION_PATHS
            for raw in [cls.original.get(relative)]
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
        self.assertEqual(len(navigation.FEEDBACK_SOURCE_SHA256), 3)
        self.assertEqual(len(navigation.ADDED_PATHS), 4)
        self.assertEqual(len(navigation.NAVIGATION_PATHS), 23)
        self.assertEqual(set(navigation.PREVIOUS_RECORDS), {
            navigation.SEC_DIRECTORY + "page.tsx", navigation.SEC_DIRECTORY + "page.test.tsx",
            "apps/web/e2e/sec-manifest-audit.spec.ts"})
        self.assertEqual(set(navigation.REFINEMENT_PREVIOUS_RECORDS), {
            navigation.SEC_DIRECTORY + name for name in (
                "page.tsx", "page.test.tsx", "sec-manifest-audit-locator.tsx",
                "messages.ts", "sec-manifest-audit.module.css")
        } | {"apps/web/e2e/sec-manifest-audit.spec.ts"})
        self.assertFalse(navigation.NAVIGATION_PATHS & bridge.FIXED_CI_PATHS)
        self.assertEqual(set(navigation.FAILURE_PREVIOUS_RECORDS), {
            navigation.SEC_DIRECTORY + name for name in (
                "error.tsx", "not-found.tsx", "messages.ts", "sec-manifest-audit.module.css")
        } | {"apps/web/e2e/sec-manifest-audit.spec.ts"})
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
            if name == "page.tsx":
                before, after = navigation.SOURCE_EDITS[relative][-1]
                self.assertEqual(expected.count(after.encode()), 1)
                expected = expected.replace(after.encode(), before.encode(), 1)
            if name in ("error.tsx", "not-found.tsx"):
                for before, after in reversed(navigation.SOURCE_EDITS[relative][1:]):
                    self.assertEqual(expected.count(after.encode()), 1)
                    expected = expected.replace(after.encode(), before.encode(), 1)
            self.assertEqual(expected.replace(b'        current="secEvidence"\n', b'')
                             .replace(b' current="secEvidence"', b'')
                             .replace(b'import { locatorFeedback } from "./locator-feedback";\n', b'')
                             .replace(b'            feedback={locatorFeedback(state.kind === "invalid" ? raw : null)}\n', b''),
                             self.original[relative])

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
                for candidate in (raw + b"\n// unrelated change\n", self.original.get(relative, b"")):
                    path.write_bytes(candidate)
                    with self.assertRaisesRegex(ValueError, "Unreviewed current|behavioral tests changed"):
                        self.verify()
                path.write_bytes(raw)

    def test_forged_mode_type_blob_deletion_or_unrelated_committed_product_fails(self):
        for relative in navigation.NAVIGATION_PATHS:
            for record in (None, "100755 blob " + "0" * 40, "120000 blob " + "0" * 40,
                           "160000 commit " + "0" * 40, blob_record(b"unreviewed")):
                with self.subTest(relative=relative, record=record):
                    if relative in navigation.ADDED_PATHS and record is None:
                        continue  # A pre-commit HEAD lacks the exact new file; its working bytes are still mandatory.
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

    def test_only_exact_three_adr060_predecessors_are_admitted_with_current_working_bytes(self):
        head = {**self.current, **navigation.PREVIOUS_RECORDS}
        self.assertEqual(self.verify(head), head)
        for relative, record in navigation.PREVIOUS_RECORDS.items():
            with self.subTest(relative=relative):
                with self.assertRaisesRegex(ValueError, "Unreviewed committed"):
                    self.verify(head={**head, relative: record[:-1] + ("0" if record[-1] != "0" else "1")})
                path = self.root / relative
                prior = bridge.git(SOURCE, "show", f"99d90aff2c19744f763fa816b3ee7d5c4cc03358:{relative}")
                self.assertEqual(blob_record(prior), record)
                path.write_bytes(prior)
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    self.verify(head)
                path.write_bytes(self.expected[relative])

    def test_added_sources_must_be_absent_from_baseline_and_present_in_working_tree(self):
        for relative in navigation.ADDED_PATHS:
            with self.subTest(relative=relative):
                with self.assertRaisesRegex(ValueError, "already exists in baseline"):
                    self.verify(baseline={**self.baseline, relative: blob_record(self.expected[relative])})
                path = self.root / relative
                path.unlink()
                with self.assertRaisesRegex(ValueError, "missing or linked"):
                    self.verify()
                path.write_bytes(self.expected[relative])
        self.verify()
        for call in self.git.call_args_list:
            self.assertNotIn(call.args[2].split(":", 1)[1], navigation.ADDED_PATHS)

    def test_exact_adr061_predecessors_never_admit_stale_working_bytes(self):
        head = {**self.current, **navigation.REFINEMENT_PREVIOUS_RECORDS}
        self.assertEqual(self.verify(head), head)
        for relative, record in navigation.REFINEMENT_PREVIOUS_RECORDS.items():
            with self.subTest(relative=relative):
                prior = bridge.git(SOURCE, "show", f"0462faacb60cceccaec3ee361eca678842b14a2a:{relative}")
                self.assertEqual(blob_record(prior), record)
                (self.root / relative).write_bytes(prior)
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    self.verify(head)
                (self.root / relative).write_bytes(self.expected[relative])
                with self.assertRaisesRegex(ValueError, "Unreviewed committed"):
                    self.verify({**head, relative: blob_record(b"forged predecessor")})

    def test_exact_adr062_predecessors_never_admit_stale_working_bytes(self):
        head = {**self.current, **navigation.FAILURE_PREVIOUS_RECORDS}
        self.assertEqual(self.verify(head), head)
        for relative, record in navigation.FAILURE_PREVIOUS_RECORDS.items():
            with self.subTest(relative=relative):
                prior = bridge.git(SOURCE, "show", f"6d122dd307d76c32b3bf6ab925d2b3996907b298:{relative}")
                self.assertEqual(blob_record(prior), record)
                (self.root / relative).write_bytes(prior)
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    self.verify(head)
                (self.root / relative).write_bytes(self.expected[relative])
                with self.assertRaisesRegex(ValueError, "Unreviewed committed"):
                    self.verify({**head, relative: blob_record(b"forged predecessor")})

    def test_failure_recovery_cannot_select_duplicates_default_time_or_assert_evidence(self):
        relative = navigation.SEC_DIRECTORY + "failed-query-recovery.tsx"
        cases = (
            (b'values.length === 1 ? values[0] : values', b'values[0]'),
            (b'if (state.kind !== "query") return null;', b'// validation removed'),
            (b'key={JSON.stringify(state.query)}', b'key="stale-query"'),
            (b'evaluationAsOf: state.query.evaluationAsOf,', b'evaluationAsOf: new Date().toISOString(),'),
            (b'demoQuery={null}', b'demoQuery={state.query}'),
            (b'{messages.locator.recoverFailedBody}', b'Verified evidence'),
            (b'  const search = useSearchParams();', b'  fetch("/api/auto-retry");\n  const search = useSearchParams();'),
        )
        for before, after in cases:
            with self.subTest(token=before):
                self.assertEqual(self.expected[relative].count(before), 1)
                (self.root / relative).write_bytes(self.expected[relative].replace(before, after, 1))
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    self.verify()
                (self.root / relative).write_bytes(self.expected[relative])

    def test_refinement_cannot_auto_select_normalize_or_leak_stale_form_state(self):
        cases = (
            ("page.tsx", b'<details className={styles.refinement}', b'<details open className={styles.refinement}'),
            ("page.tsx", b'key={JSON.stringify(state.query)}', b'key="stale-query"'),
            ("page.tsx", b'evaluationAsOf: state.query.evaluationAsOf,', b'evaluationAsOf: new Date().toISOString(),'),
            ("page.tsx", b'                demoQuery={null}', b'                demoQuery={provider.demoQuery}'),
            ("sec-manifest-audit-locator.tsx", b'key={JSON.stringify([invalid, feedback])}', b'key="stale-inputs"'),
            ("sec-manifest-audit.module.css", b'outline: 2px solid var(--color-accent);', b'outline: none;'),
        )
        for name, before, after in cases:
            relative = navigation.SEC_DIRECTORY + name
            with self.subTest(relative=relative, token=before):
                self.assertEqual(self.expected[relative].count(before), 1)
                (self.root / relative).write_bytes(self.expected[relative].replace(before, after, 1))
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    self.verify()
                (self.root / relative).write_bytes(self.expected[relative])

    def test_locator_normalization_validation_or_provider_activation_mutations_fail(self):
        cases = (
            ("locator-feedback.ts", b"value.length > LOCATOR_INPUT_LIMIT", b"value.length >= LOCATOR_INPUT_LIMIT"),
            ("locator-feedback.ts", b"return { value, error:", b"return { value: value.trim(), error:"),
            ("sec-manifest-audit-locator.tsx", b'method="get"', b'method="post"'),
            ("sec-manifest-audit-locator.tsx", b'aria-invalid={manifestError ? true : undefined}', b'aria-invalid={undefined}'),
            ("sec-manifest-audit-locator.tsx", b'name="view" value="summary"', b'name="view" value="latest"'),
            ("page.tsx", b'state.kind === "query"\n    ? await provider.findExact', b'state.kind !== "query"\n    ? await provider.findExact'),
        )
        for name, before, after in cases:
            relative = navigation.SEC_DIRECTORY + name
            with self.subTest(relative=relative, token=before):
                self.assertEqual(self.expected[relative].count(before), 1)
                (self.root / relative).write_bytes(self.expected[relative].replace(before, after, 1))
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    self.verify()
                (self.root / relative).write_bytes(self.expected[relative])

    def test_new_sources_and_all_migrated_paths_remain_in_custody(self):
        with patch.object(bridge, "git", return_value=b""):
            before = bridge.snapshot(self.root, {"steps": []})
            self.assertTrue(navigation.NAVIGATION_PATHS <= before["files"].keys())
            for relative in navigation.ADDED_PATHS:
                self.assertEqual(before["files"][relative], hashlib.sha256(self.expected[relative]).hexdigest())
            relative = next(iter(navigation.ADDED_PATHS))
            (self.root / relative).write_bytes(b"changed during historical run")
            self.assertNotEqual(bridge.snapshot(self.root, {"steps": []}), before)

    def test_untracked_exception_is_exact_and_only_checked_after_required_migration(self):
        manifest = bridge.expected_manifest(bridge.baseline_workflow(SOURCE))
        for relative in navigation.ADDED_PATHS:
            with patch.object(bridge, "git", side_effect=[b"", b"", b"", relative.encode() + b"\0", b""]), \
                    patch.object(bridge, "verify_current_test", return_value={}), \
                    patch.object(bridge, "verify_cpi", return_value={}), \
                    patch.object(bridge, "verify_scoring", return_value={}), \
                    patch.object(bridge, "verify_navigation", return_value={}) as verify:
                bridge.validate_product(SOURCE, manifest)
                verify.assert_called_once()
        for relative in (navigation.SEC_DIRECTORY + "unreviewed.ts", "apps/web/src/lib/providers/sec-manifest-audit-query.ts"):
            with patch.object(bridge, "git", side_effect=[b"", b"", b"", relative.encode() + b"\0"]), \
                    patch.object(bridge, "verify_current_test", return_value={}), \
                    patch.object(bridge, "verify_cpi", return_value={}), \
                    patch.object(bridge, "verify_scoring", return_value={}), \
                    patch.object(bridge, "verify_navigation", return_value={}):
                with self.assertRaisesRegex(ValueError, "Unexpected uncommitted"):
                    bridge.validate_product(SOURCE, manifest)

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
