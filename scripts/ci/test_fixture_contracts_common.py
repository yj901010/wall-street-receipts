"""Mutation checks for offline JSON, UTC, and exact-decimal contract inputs."""
from decimal import Decimal, localcontext
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from jsonschema.exceptions import SchemaError, ValidationError
from referencing.exceptions import NoSuchResource

import fixture_contracts_common as common


class FixtureInputTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-fixture-json-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.path = self.root / "fixtures/v1/test.json"
        self.path.parent.mkdir(parents=True)
        self.path.write_text('{"number": 0.123456789012}', encoding="utf-8")

    def load(self):
        return common.load_json(self.root, "fixtures/v1/test.json")

    def test_json_decimal_is_preserved_without_binary_float(self):
        self.assertEqual(self.load(), {"number": Decimal("0.123456789012")})
        self.assertIsInstance(self.load()["number"], Decimal)

    def test_duplicate_keys_nonfinite_nonobject_and_invalid_json_are_rejected(self):
        for raw in ('{"n":1,"n":2}', '{"nested":{"n":1,"n":2}}', '{"n":NaN}',
                    '{"n":Infinity}', '{"n":-Infinity}', '[]', 'null', '{} {}',
                    '{"n": 1e1001}', '{"n": 1e-1001}', '\ufeff{}'):
            with self.subTest(raw=raw):
                self.path.write_text(raw, encoding="utf-8")
                with self.assertRaises(ValueError):
                    self.load()

    def test_reads_are_bounded_and_missing_files_fail_without_fallback(self):
        with patch.object(common, "MAX_JSON_BYTES", 4), self.assertRaisesRegex(ValueError, "byte limit"):
            self.load()
        self.path.unlink()
        with self.assertRaisesRegex(ValueError, "missing or linked"):
            self.load()

    def test_unrelated_secret_absolute_traversal_and_noncanonical_paths_fail(self):
        paths = (".env", "../.env", "fixtures/v1/../../../.env", "/fixtures/v1/test.json",
                 "C:/test.json", "fixtures\\v1\\test.json", "fixtures//v1/test.json",
                 "fixtures/v1/./test.json", "fixtures/v2/test.json", "schemas/test.txt",
                 "schemas/elsewhere/test.json", "fixtures/v1/test.json:alternate", "")
        for relative in paths:
            with self.subTest(relative=relative), self.assertRaises(ValueError):
                common.load_json(self.root, relative)

    def test_each_read_observes_the_supplied_current_root_not_a_cache(self):
        self.load()
        self.path.write_text('{"changed":true}', encoding="utf-8")
        self.assertEqual(self.load(), {"changed": True})

    def test_remote_and_external_local_refs_are_rejected_at_any_depth(self):
        for key in ("$ref", "$dynamicRef", "$recursiveRef"):
            for value in ("https://example.invalid/schema", "file:///tmp/schema", "other.json#/$defs/a", 42):
                with self.subTest(key=key, value=value):
                    self.path.write_text(json.dumps({"nested": [{key: value}]}), encoding="utf-8")
                    with self.assertRaisesRegex(ValueError, "document-local"):
                        self.load()
        self.path.write_text('{"$ref":"#/$defs/local"}', encoding="utf-8")
        self.assertEqual(self.load()["$ref"], "#/$defs/local")

    def test_linked_input_is_rejected_when_symlinks_are_available(self):
        target = self.root / "ordinary.json"
        target.write_text("{}", encoding="utf-8")
        self.path.unlink()
        try:
            self.path.symlink_to(target)
        except OSError:
            self.skipTest("Host cannot create symlinks")
        with self.assertRaisesRegex(ValueError, "missing or linked"):
            self.load()

    def test_reparse_link_detection_also_guards_root_and_parent(self):
        for relative in ("", "fixtures", "fixtures/v1", "fixtures/v1/test.json"):
            target = self.root / relative
            with self.subTest(relative=relative), patch.object(common, "_linked", side_effect=lambda path: path == target):
                with self.assertRaisesRegex(ValueError, "ordinary directory|missing or linked"):
                    self.load()


class ExactSchemaTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-fixture-schema-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.path = self.root / "schemas/test.json"
        self.path.parent.mkdir()
        self.path.write_text(
            '{"$schema":"https://json-schema.org/draft/2020-12/schema",'
            '"type":"number","multipleOf":0.000000000001,'
            '"exclusiveMinimum":-100000000000000000000000000,'
            '"exclusiveMaximum":100000000000000000000000000}', encoding="utf-8")

    def test_38_digit_multiple_of_is_exact_under_every_decimal_context(self):
        validator = common.validator(self.root, "schemas/test.json")
        allowed = (Decimal("99999999999999999999999999.999999999999"),
                   Decimal("-99999999999999999999999999.999999999999"),
                   Decimal("0.000000000001"), Decimal("0.10"), 0, 1)
        rejected = (Decimal("99999999999999999999999999.9999999999991"),
                    Decimal("-99999999999999999999999999.9999999999991"),
                    Decimal("0.0000000000001"), Decimal("1e26"), Decimal("-1e26"), 0.1, True)
        for precision in (6, 28, 50):
            with localcontext() as context:
                context.prec = precision
                for value in allowed:
                    with self.subTest(precision=precision, allowed=value):
                        validator.validate(value)
                for value in rejected:
                    with self.subTest(precision=precision, rejected=value), self.assertRaises(ValidationError):
                        validator.validate(value)

    def test_invalid_schema_and_wrong_draft_fail(self):
        for raw in ('{"$schema":"https://json-schema.org/draft/2020-12/schema","multipleOf":0}',
                    '{"$schema":"http://json-schema.org/draft-07/schema#","type":"number"}'):
            self.path.write_text(raw, encoding="utf-8")
            with self.assertRaises((ValueError, SchemaError)):
                common.validator(self.root, "schemas/test.json")

    def test_local_refs_work_and_retrieval_is_closed(self):
        self.path.write_text(
            '{"$schema":"https://json-schema.org/draft/2020-12/schema",'
            '"$ref":"#/$defs/id","$defs":{"id":{"type":"integer"}}}', encoding="utf-8")
        validator = common.validator(self.root, "schemas/test.json")
        validator.validate(1)
        with self.assertRaises(ValidationError):
            validator.validate(True)
        with self.assertRaises(NoSuchResource):
            common._no_retrieval("https://example.invalid/no-network")


class UtcInstantTests(unittest.TestCase):
    def test_utc_microseconds_are_preserved(self):
        value = common.instant("2026-08-20T00:00:00.123456Z")
        self.assertEqual(value.microsecond, 123456)
        self.assertEqual(value.utcoffset().total_seconds(), 0)

    def test_offsets_local_times_invalid_dates_and_precision_loss_fail(self):
        for value in (None, "2026-08-20", "2026-08-20T00:00:00", "2026-08-20T09:00:00+09:00",
                      "2026-08-20T00:00:00+00:00", "2026-08-20T00:00:00.1234567Z",
                      "2026-02-30T00:00:00Z", "2026-08-20T00:00:00Z\n", "２０２６-08-20T00:00:00Z"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                common.instant(value)


if __name__ == "__main__":
    unittest.main()
