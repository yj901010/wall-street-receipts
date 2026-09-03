"""Offline, read-only helpers for current-checkout fixture contracts.

JSON decimals never pass through binary floats. References are document-local;
these CI validators have no provider, Git, network, or environment-file input.
"""
from __future__ import annotations

from datetime import datetime
from decimal import Decimal
import json
from pathlib import Path, PurePosixPath
import re
import stat

from jsonschema import Draft202012Validator, FormatChecker, ValidationError, validators
from referencing import Registry
from referencing.exceptions import NoSuchResource


MAX_JSON_BYTES = 2 * 1024 * 1024
UTC_INSTANT = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?Z")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def _linked(path: Path) -> bool:
    attributes = getattr(path.lstat(), "st_file_attributes", 0)
    return path.is_symlink() or bool(attributes & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400))


def document_path(root: Path, relative: str) -> Path:
    """Accept only ordinary JSON files within this checkout's data directories."""
    require(isinstance(relative, str), "Contract path must be a relative JSON path")
    parts = PurePosixPath(relative).parts
    require("\\" not in relative and bool(parts) and ":" not in relative
            and not relative.startswith("/") and all(part not in {".", ".."} for part in parts)
            and "/".join(parts) == relative, "Contract path must be canonical and relative")
    require((len(parts) == 2 and parts[0] == "schemas")
            or (len(parts) == 3 and parts[:2] == ("fixtures", "v1")),
            "Only current fixture and schema documents may be read")
    require(parts[-1].endswith(".json"), "Contract document must be JSON")
    root = Path(root).absolute()
    require(root.is_dir() and not _linked(root), "Contract root must be an ordinary directory")
    cursor = root
    for index, part in enumerate(parts):
        cursor /= part
        require(cursor.exists() and not _linked(cursor), "Contract document is missing or linked")
        if index < len(parts) - 1:
            require(cursor.is_dir(), "Contract document parent must be a directory")
    require(cursor.is_file(), "Contract document must be an ordinary file")
    return cursor


def _object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON object key")
        result[key] = value
    return result


def _decimal(value):
    number = Decimal(value)
    # Bound pathological exponents before any exact coefficient arithmetic.
    require(number.is_finite() and abs(number.as_tuple().exponent) <= 1000
            and len(number.as_tuple().digits) <= 1000, "JSON decimal exceeds contract parsing bounds")
    return number


def _nonfinite(_value):
    raise ValueError("Non-finite JSON number")


def _local_references(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in {"$ref", "$dynamicRef", "$recursiveRef"}:
                require(isinstance(child, str) and child.startswith("#"),
                        "Only document-local schema references are allowed")
            _local_references(child)
    elif isinstance(value, list):
        for child in value:
            _local_references(child)


def load_json(root: Path, relative: str) -> dict:
    path = document_path(root, relative)
    require(path.stat().st_size <= MAX_JSON_BYTES, "Contract JSON document exceeds byte limit")
    with path.open("rb") as stream:
        raw = stream.read(MAX_JSON_BYTES + 1)
    require(len(raw) <= MAX_JSON_BYTES, "Contract JSON document exceeds byte limit")
    value = json.loads(raw.decode("utf-8"), object_pairs_hook=_object,
                       parse_float=_decimal, parse_constant=_nonfinite)
    require(isinstance(value, dict), "Contract JSON document must be an object")
    _local_references(value)
    return value


def instant(value: str) -> datetime:
    require(isinstance(value, str) and UTC_INSTANT.fullmatch(value) is not None,
            "Contract instant must be UTC Z with at most microsecond precision")
    return datetime.fromisoformat(value[:-1] + "+00:00")


def _exact_multiple_of(checker, divisor, value, _schema):
    if not checker.is_type(value, "number"):
        return
    if not isinstance(value, (int, Decimal)) or not isinstance(divisor, (int, Decimal)):
        yield ValidationError("Contract numbers must use exact JSON decimals, not binary floats")
        return
    numerator, denominator = value.as_integer_ratio()
    divisor_numerator, divisor_denominator = divisor.as_integer_ratio()
    if divisor_numerator <= 0 or (numerator * divisor_denominator) % (denominator * divisor_numerator):
        yield ValidationError("Number is not an exact multiple of the schema quantum")


ExactValidator = validators.extend(Draft202012Validator, {"multipleOf": _exact_multiple_of})


def _no_retrieval(uri):
    raise NoSuchResource(ref=uri)


def validator(root: Path, relative: str):
    schema = load_json(root, relative)
    require(schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema",
            "Contract schema must use Draft 2020-12")
    Draft202012Validator.check_schema(schema)
    return ExactValidator(schema, format_checker=FormatChecker(), registry=Registry(retrieve=_no_retrieval))
