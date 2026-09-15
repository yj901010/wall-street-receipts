"""Document intake is a closed addition, never a broad provider/metric escape."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import raw_feed_intake_contracts as intake
import run_contracts as bridge
from current_contracts import blob_record

SOURCE = Path(__file__).resolve().parents[2]
PREDECESSOR = "f16cb642e61b5e0377ba3830c2c3993110fda532"


class RawFeedIntakeCustodyTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-intake-custody-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.raw = {p: (SOURCE / p).read_bytes().replace(b"\r\n", b"\n") for p in intake.RAW_FEED_INTAKE_PATHS}
        self.current = {p: blob_record(raw) for p, raw in self.raw.items()}
        for relative, raw in self.raw.items():
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)

    def test_exact_independent_four_path_inventory_pre_and_post_commit(self):
        self.assertEqual(intake.RAW_FEED_INTAKE_PATHS, {
            "RAW_FEED_INTAKE.md", "examples/raw-feed-intake/blank.json",
            "scripts/review_raw_feed_intake.py", "scripts/ci/test_raw_feed_intake.py"})
        previous = bridge.SCORING_PATHS | bridge.CPI_PATHS | bridge.NAVIGATION_PATHS | bridge.REFERENCE_WIDGET_PATHS
        self.assertFalse(intake.RAW_FEED_INTAKE_PATHS & (previous | bridge.FIXED_CI_PATHS))
        self.assertEqual(intake.verify_raw_feed_intake(SOURCE, {}, self.current), self.current)
        self.assertEqual(intake.verify_raw_feed_intake(self.root, {}, {}), {})

    def test_each_working_byte_and_committed_mode_type_blob_is_pinned(self):
        for relative, raw in self.raw.items():
            with self.subTest(path=relative):
                self.assertEqual(hashlib.sha256(raw).hexdigest(), intake.CONTENT_SHA256[relative])
                for wrong in ("100644 blob " + "f" * 40, self.current[relative].replace("100644", "100755"),
                              self.current[relative].replace("100644", "120000"), self.current[relative].replace("blob", "tree")):
                    with self.assertRaisesRegex(ValueError, "Unreviewed committed"):
                        intake.verify_raw_feed_intake(self.root, {}, {**self.current, relative: wrong})
                target = self.root / relative
                target.write_bytes(raw + b"unreviewed\n")
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    intake.verify_raw_feed_intake(self.root, {}, self.current)
                target.unlink()
                with self.assertRaisesRegex(ValueError, "missing or linked"):
                    intake.verify_raw_feed_intake(self.root, {}, self.current)
                target.write_bytes(raw.replace(b"\n", b"\r\n"))
                self.assertEqual(intake.verify_raw_feed_intake(self.root, {}, self.current), self.current)

    def test_existing_baseline_cannot_be_replaced_or_neighbors_added(self):
        for relative, record in self.current.items():
            with self.subTest(path=relative), self.assertRaisesRegex(ValueError, "frozen baseline"):
                intake.verify_raw_feed_intake(self.root, {relative: record}, self.current)
        baseline = {"existing": "100644 blob " + "a" * 40}
        adjusted = intake.verify_raw_feed_intake(self.root, baseline, self.current)
        self.assertEqual(baseline, {"existing": "100644 blob " + "a" * 40})
        for neighbor in ("scripts/ingest_raw_ticks.py", "examples/raw-feed-intake/unreviewed.json"):
            with self.subTest(path=neighbor), self.assertRaisesRegex(ValueError, "Product tree differs"):
                bridge.compare_product_trees(adjusted, {**baseline, **self.current, neighbor: "100644 blob " + "b" * 40}, set())

    def test_bridge_invokes_verifier_and_snapshot_captures_every_path(self):
        manifest = bridge.pinned_manifest(SOURCE)
        with patch.object(bridge, "verify_raw_feed_intake", side_effect=ValueError("intake verifier reached")) as verify:
            with self.assertRaisesRegex(ValueError, "intake verifier reached"):
                bridge.validate_product(SOURCE, manifest)
            verify.assert_called_once()
        snap = bridge.snapshot(SOURCE, manifest)
        for relative in intake.RAW_FEED_INTAKE_PATHS:
            self.assertEqual(snap["files"][relative], bridge.digest((SOURCE / relative).read_bytes()))

    def test_all_previous_product_pins_equal_the_merged_predecessor(self):
        predecessor = bridge.tree_records(bridge.git(SOURCE, "ls-tree", "-rz", PREDECESSOR))
        self.assertFalse(intake.RAW_FEED_INTAKE_PATHS & predecessor.keys())
        previous = bridge.SCORING_PATHS | bridge.CPI_PATHS | bridge.NAVIGATION_PATHS | bridge.REFERENCE_WIDGET_PATHS
        for relative in previous:
            with self.subTest(path=relative):
                self.assertEqual(blob_record((SOURCE / relative).read_bytes().replace(b"\r\n", b"\n")), predecessor[relative])


if __name__ == "__main__":
    unittest.main()
