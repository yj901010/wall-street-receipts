"""Current-checkout scoring custody; API tests execute these exact reviewed bytes."""
import hashlib
from pathlib import Path
import tempfile
import unittest
import scoring_contracts as scoring
import run_contracts as bridge
from current_contracts import blob_record

SOURCE = Path(__file__).resolve().parents[2]


class ScoringCustodyTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-scoring-custody-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.raw = {p: (SOURCE / p).read_bytes().replace(b"\r\n", b"\n") for p in scoring.SCORING_PATHS}
        self.current = {p: blob_record(raw) for p, raw in self.raw.items()}
        for relative, raw in self.raw.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)

    def test_exact_seven_additions_no_legacy_or_general_exception(self):
        self.assertEqual(len(scoring.SCORING_PATHS), 7)
        self.assertFalse(scoring.SCORING_PATHS & (bridge.FIXED_CI_PATHS | bridge.CPI_PATHS | bridge.NAVIGATION_PATHS))
        for path in scoring.SCORING_PATHS:
            self.assertIn("/application/scoring/", path)
        self.assertEqual(scoring.verify_scoring(SOURCE, {}, self.current), self.current)
        self.assertEqual(scoring.verify_scoring(self.root, {}, {}), {})

    def test_each_current_byte_and_committed_mode_type_object_is_required(self):
        for relative, raw in self.raw.items():
            with self.subTest(path=relative):
                self.assertEqual(hashlib.sha256(raw).hexdigest(), scoring.CONTENT_SHA256[relative])
                for wrong in ("100644 blob " + "f" * 40, self.current[relative].replace("100644", "100755"),
                              self.current[relative].replace("100644", "120000")):
                    with self.assertRaisesRegex(ValueError, "Unreviewed committed"):
                        scoring.verify_scoring(self.root, {}, {**self.current, relative: wrong})
                path = self.root / relative
                path.write_bytes(raw + b"// altered\n")
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    scoring.verify_scoring(self.root, {}, self.current)
                path.unlink()
                with self.assertRaisesRegex(ValueError, "missing or linked"):
                    scoring.verify_scoring(self.root, {}, self.current)
                path.write_bytes(raw)

    def test_additions_cannot_hide_existing_or_neighboring_product_paths(self):
        relative = next(iter(scoring.SCORING_PATHS))
        with self.assertRaisesRegex(ValueError, "frozen baseline"):
            scoring.verify_scoring(self.root, {relative: self.current[relative]}, self.current)
        for neighbor in ("apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/Unreviewed.java",
                         "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/CallOutcome.java"):
            current = {**self.current, neighbor: "100644 blob " + "a" * 40}
            adjusted = scoring.verify_scoring(self.root, {}, current)
            with self.assertRaisesRegex(ValueError, "Product tree differs"):
                bridge.compare_product_trees(adjusted, current, frozenset())

    def test_crlf_is_normalized_but_bom_is_not_accepted(self):
        for relative, raw in self.raw.items():
            (self.root / relative).write_bytes(raw.replace(b"\n", b"\r\n"))
        self.assertEqual(scoring.verify_scoring(self.root, {}, self.current), self.current)
        relative = next(iter(self.raw))
        (self.root / relative).write_bytes(b"\xef\xbb\xbf" + self.raw[relative])
        with self.assertRaisesRegex(ValueError, "Unreviewed current"):
            scoring.verify_scoring(self.root, {}, self.current)
