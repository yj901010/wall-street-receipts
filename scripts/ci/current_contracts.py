"""Closed current-tree migration for the one test-only ADR-058 source delta.

This is not a product-path allowlist. Every byte except the reviewed test
database annotation/comment must still equal the pinned baseline; the current
API job exercises that exact test together with the rest of the current suite.
"""
from __future__ import annotations

import hashlib
from pathlib import Path

BASELINE = "3792100f49c496d751d1dd54a7fbdc1b7c2fd275"
TEST_PATH = "apps/api/src/test/java/com/wallstreetreceipts/api/persistence/SecFilingHistoryCollectionAttemptPersistenceTest.java"
ISOLATED_ANNOTATION = (
    b"// Cross-thread claim checks require real commits in a database isolated from rollback-based tests.\n"
    b"@SpringBootTest(properties =\n"
    b'        "spring.datasource.url=jdbc:h2:mem:wsr-sec-collection-attempt-persistence"\n'
    b'                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")\n'
)


def isolated_test_bytes(original: bytes) -> bytes:
    if original.count(b"@SpringBootTest\n") != 1:
        raise ValueError("Pinned attempt test annotation changed")
    return original.replace(b"@SpringBootTest\n", ISOLATED_ANNOTATION, 1)


def blob_record(raw: bytes) -> str:
    # SHA-1 here is Git's object identifier, not a signature/security claim.
    oid = hashlib.sha1(b"blob " + str(len(raw)).encode("ascii") + b"\0" + raw).hexdigest()
    return f"100644 blob {oid}"


def verify_current_test(root: Path, git_read, baseline: dict, current: dict) -> dict:
    original = git_read(root, "show", f"{BASELINE}:{TEST_PATH}")
    expected = isolated_test_bytes(original)
    if baseline.get(TEST_PATH) != blob_record(original):
        raise ValueError("Pinned attempt test mode/type/object changed")
    if current.get(TEST_PATH) not in {blob_record(original), blob_record(expected)}:
        raise ValueError("Unreviewed committed attempt-test change")
    path = root / TEST_PATH
    if (not path.is_file() or path.is_symlink() or path.stat().st_mode & 0o111
            or path.read_bytes().replace(b"\r\n", b"\n") != expected):
        raise ValueError("Current attempt test differs from exact isolated-database migration")
    adjusted = dict(baseline)
    adjusted[TEST_PATH] = current[TEST_PATH]
    return adjusted
