"""Independent display-only delta, never a scoring or broad product exception."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import reference_widget_contracts as widget
import run_contracts as bridge
from current_contracts import blob_record

SOURCE = Path(__file__).resolve().parents[2]


class ReferenceWidgetCustodyTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-reference-custody-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.raw = {p: (SOURCE / p).read_bytes().replace(b"\r\n", b"\n") for p in widget.REFERENCE_WIDGET_PATHS}
        self.current = {p: blob_record(raw) for p, raw in self.raw.items()}
        for relative, raw in self.raw.items():
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)

    def test_exact_thirteen_additions_independent_of_old_custody(self):
        self.assertEqual(len(widget.REFERENCE_WIDGET_PATHS), 13)
        self.assertEqual(len(bridge.SCORING_PATHS), 118)
        self.assertFalse(widget.REFERENCE_WIDGET_PATHS & (bridge.SCORING_PATHS | bridge.CPI_PATHS | bridge.NAVIGATION_PATHS | bridge.FIXED_CI_PATHS))
        expected = {"VUNELIX_REFERENCE_WIDGET.md", "apps/web/e2e/reference-widget.spec.ts"}
        expected |= {"apps/web/reference-widget/" + p for p in ("playwright.config.ts", "public.config.ts", "tests/widget.spec.ts")}
        expected |= {"apps/web/src/app/market/reference/AAPL/" + p for p in (
            "config.ts", "config.test.ts", "messages.ts", "page.tsx", "page.test.tsx",
            "reference-widget.tsx", "reference-widget.test.tsx", "reference.module.css")}
        self.assertEqual(widget.REFERENCE_WIDGET_PATHS, expected)
        self.assertEqual(widget.verify_reference_widget(SOURCE, {}, self.current), self.current)
        self.assertEqual(widget.verify_reference_widget(self.root, {}, {}), {})

    def test_each_byte_is_required_and_crlf_is_normalized(self):
        for relative, raw in self.raw.items():
            with self.subTest(path=relative):
                self.assertEqual(hashlib.sha256(raw).hexdigest(), widget.CONTENT_SHA256[relative])
                target = self.root / relative
                target.write_bytes(raw + b"unreviewed\n")
                with self.assertRaisesRegex(ValueError, "Unreviewed current reference"):
                    widget.verify_reference_widget(self.root, {}, self.current)
                target.unlink()
                with self.assertRaisesRegex(ValueError, "missing or linked"):
                    widget.verify_reference_widget(self.root, {}, self.current)
                target.write_bytes(raw.replace(b"\n", b"\r\n"))
                self.assertEqual(widget.verify_reference_widget(self.root, {}, self.current), self.current)

    def test_each_committed_mode_type_and_object_is_required(self):
        for relative, record in self.current.items():
            for wrong in ("100644 blob " + "f" * 40, record.replace("100644", "100755"),
                          record.replace("100644", "120000"), record.replace("blob", "tree")):
                with self.subTest(path=relative, record=wrong), self.assertRaisesRegex(ValueError, "Unreviewed committed reference"):
                    widget.verify_reference_widget(self.root, {}, {**self.current, relative: wrong})

    def test_additions_cannot_replace_existing_baseline_paths(self):
        for relative, record in self.current.items():
            with self.subTest(path=relative), self.assertRaisesRegex(ValueError, "frozen baseline"):
                widget.verify_reference_widget(self.root, {relative: record}, self.current)

    def test_neighbors_are_not_allowed_and_baseline_is_not_mutated(self):
        original = {"apps/web/src/app/market/page.tsx": "100644 blob " + "a" * 40}
        saved = dict(original)
        adjusted = widget.verify_reference_widget(self.root, original, self.current)
        self.assertEqual(original, saved)
        for neighbor in ("apps/web/src/app/market/reference/TSLA/page.tsx", "apps/web/src/lib/providers/unreviewed.ts"):
            with self.subTest(path=neighbor), self.assertRaisesRegex(ValueError, "Product tree differs"):
                bridge.compare_product_trees(adjusted, {**original, **self.current, neighbor: "100644 blob " + "b" * 40}, set())

    def test_snapshot_captures_all_reviewed_source_bytes(self):
        snap = bridge.snapshot(SOURCE, bridge.pinned_manifest(SOURCE))
        for relative in widget.REFERENCE_WIDGET_PATHS:
            self.assertEqual(snap["files"][relative], bridge.digest((SOURCE / relative).read_bytes()))

    def test_bridge_actually_invokes_verifier(self):
        with patch.object(bridge, "verify_reference_widget", side_effect=ValueError("reference verifier reached")) as verify:
            with self.assertRaisesRegex(ValueError, "reference verifier reached"):
                bridge.validate_product(SOURCE, bridge.pinned_manifest(SOURCE))
            verify.assert_called_once()

    def test_new_paths_absent_from_frozen_baseline(self):
        records = bridge.tree_records(bridge.git(SOURCE, "ls-tree", "-rz", bridge.BASELINE))
        self.assertFalse(widget.REFERENCE_WIDGET_PATHS & records.keys())


if __name__ == "__main__":
    unittest.main()
