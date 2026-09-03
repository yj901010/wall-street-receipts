#!/usr/bin/env python3
"""Validate current DEMO revision/outcome evidence, independently of legacy Git.

The CLI root is the checkout containing this script, not the caller's working
directory or a historical checkout. This command does not unfreeze product paths.
"""
from __future__ import annotations

import json
from pathlib import Path
import sys

from jsonschema.exceptions import SchemaError, ValidationError
from referencing.exceptions import Unresolvable

from fixture_outcomes import validate_outcomes
from fixture_revisions import validate_revisions


def validate_current(root: Path) -> dict:
    return {"dataMode": "DEMO", "revisions": validate_revisions(root),
            "outcomes": validate_outcomes(root)}


def main() -> int:
    if len(sys.argv) != 1:
        print("Usage: python scripts/ci/validate_current_fixtures.py", file=sys.stderr)
        return 2
    try:
        result = validate_current(Path(__file__).resolve().parents[2])
    except (ValueError, OSError, KeyError, TypeError, IndexError, RecursionError,
            SchemaError, ValidationError, Unresolvable):
        # Never dump a failed document/instance, even if a local fixture is corrupt.
        print("Current DEMO fixture contracts failed; run the focused mutation tests for diagnostics.", file=sys.stderr)
        return 1
    print("Current-checkout fixture contracts PASS: " + json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
