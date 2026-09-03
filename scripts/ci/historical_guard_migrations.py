"""One byte-pinned correction to a historical guard's inspected text range.

Step 83's ADR-055 restoration range originally ended at the later ADR-055
guard. ADR-056 subsequently inserted its restoration and guards in between;
the unrelated ADR-056 guard contains the forbidden-token assertion itself.
The old range therefore falsely sees '"--force"' in the ADR-055 restoration.

This migration accepts only the exact original step-83 shell bytes and changes
one closing delimiter to the immediately following workflow step. Every guard
assertion remains intact. It returns compiled Python source for execution and
never rewrites the extracted script, historical workflow, or product files.
"""
from __future__ import annotations

import hashlib


STEP_INDEX = 83
SCRIPT_SHA256 = "7575cc2800580833373248d2334c2d5c3e078cbe23592264abfbb25792036380"
HEREDOC_PREFIX = b"python <<'PYTHON'\n"
HEREDOC_SUFFIX = b"PYTHON\n"
ORIGINAL_DELIMITER = ')[1].split(adr055_guard_marker, 1)[0]'
CORRECTED_DELIMITER = ')[1].split("\\n      - name:", 1)[0]'


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(f"Historical step 83 guard migration: {message}")


def migrated_python_body(index: int, rawscript: bytes) -> str | None:
    """Return the sole reviewed migration, or None for every other step.

    Exact digest, exact single quoted Python heredoc framing, one delimiter
    occurrence and successful Python compilation are all required. Modified
    assertions, malformed framing, newline changes and other script variants
    are rejected rather than adapted heuristically.
    """
    if index != STEP_INDEX:
        return None
    _require(hashlib.sha256(rawscript).hexdigest() == SCRIPT_SHA256,
             "original script differs from pinned SHA-256")
    _require(rawscript.startswith(HEREDOC_PREFIX) and rawscript.endswith(b"\n" + HEREDOC_SUFFIX),
             "expected exact Python heredoc framing")
    body = rawscript[len(HEREDOC_PREFIX):-len(HEREDOC_SUFFIX)].decode("utf-8")
    _require(body.count(ORIGINAL_DELIMITER) == 1, "restore closing delimiter must occur exactly once")
    migrated = body.replace(ORIGINAL_DELIMITER, CORRECTED_DELIMITER, 1)
    compile(migrated, "<wsr-historical-step-83-range-migration>", "exec")
    return migrated
