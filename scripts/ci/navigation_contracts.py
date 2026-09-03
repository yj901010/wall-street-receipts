"""ADR-060: closed current-source migration for SEC evidence navigation.

Only the seven explicit runtime edits below may differ from the frozen baseline.
Nine reviewed current-tree test updates are separately content-pinned; existing
Vitest and Playwright jobs execute them. No product path is a general exemption.
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import stat

from current_contracts import BASELINE, blob_record


SEC_DIRECTORY = "apps/web/src/app/research/sec/filing-history/"
SOURCE_EDITS = {
    "apps/web/src/lib/i18n/messages.ts": (
        ('  "methodology",\n', '  "methodology",\n  "secEvidence",\n'),
        ('    methodology: "방법론",\n', '    methodology: "방법론",\n    secEvidence: "SEC 증거",\n'),
        ('    methodology: "Methodology",\n', '    methodology: "Methodology",\n    secEvidence: "SEC evidence",\n'),
    ),
    "apps/web/src/components/site-header.tsx": (
        ('        </Link>\n      </nav>', '''        </Link>
        <Link
          aria-current={current === "secEvidence" ? "page" : undefined}
          href="/research/sec/filing-history"
          prefetch={false}
        >
          {messages.navigation.secEvidence}
        </Link>
      </nav>'''),
    ),
    "apps/web/src/app/globals.css": (
        ('\n.mode-badge {', '\n.site-header nav a:focus-visible {\n  outline-offset: -3px;\n}\n\n.mode-badge {'),
        ('@media (max-width: 1120px) {', '@media (max-width: 1280px) {'),
    ),
    SEC_DIRECTORY + "page.tsx": (
        ('      <SiteHeader\n', '      <SiteHeader\n        current="secEvidence"\n'),
    ),
    **{SEC_DIRECTORY + filename: (('<SiteHeader />', '<SiteHeader current="secEvidence" />'),)
       for filename in ("loading.tsx", "error.tsx", "not-found.tsx")},
}

# These are test-source custody hashes, not financial methodology identities.
TEST_SHA256 = {
    "apps/web/src/app/markets/sp500/page.test.tsx": "d5e627513167cd355bba9b08a7769992d00c68b76c8aea6aee5fa3c4ada3a30d",
    "apps/web/src/app/screener/page.test.tsx": "27ae6a6872efd67eb20b23b0e17c849492b16548662438700b0b2117ce8bd184",
    "apps/web/src/lib/i18n/messages.test.ts": "149451cfb1fd45e322dfba00c62b81f46e252c618dc22292c48df305aaea1c12",
    "apps/web/src/components/site-header.test.tsx": "2ba20856c7e71aedb6a08e551e8616492aa473064cae529c1f879823ae8d0b2a",
    SEC_DIRECTORY + "page.test.tsx": "d81dd53813e3b27d8509ecefaa17f48ec798d42f796fe7742f7fe0af2dd13af0",
    "apps/web/e2e/i18n.spec.ts": "4c9b96faf8617b91aa0ec43b42efcda1daa93adf45b0a01b82e25d287d61705d",
    "apps/web/e2e/screener.spec.ts": "02dcd45d5f9fdb5569c13ea781710306dbd7e2ae0889ccd727c500a5807c6702",
    "apps/web/e2e/sp500-history.spec.ts": "2227749f35ea3763f96e25db60cf59ce11cd42eeea244eebfb75679e0a1e5888",
    "apps/web/e2e/sec-manifest-audit.spec.ts": "1b08595e4d9f90dbe8d1092031617dbe6c6c31c47d99d5e2bceff376a4bdf97c",
}
NAVIGATION_PATHS = frozenset(SOURCE_EDITS) | frozenset(TEST_SHA256)


def migrated_source(relative: str, original: bytes) -> bytes:
    result = original
    for before, after in SOURCE_EDITS[relative]:
        before_bytes, after_bytes = before.encode("utf-8"), after.encode("utf-8")
        if result.count(before_bytes) != 1:
            raise ValueError("Pinned navigation insertion point changed: " + relative)
        result = result.replace(before_bytes, after_bytes, 1)
    return result


def _current_bytes(root: Path, relative: str) -> bytes:
    path = root
    for part in Path(relative).parts:
        path /= part
        if not path.exists() or path.is_symlink():
            raise ValueError("Navigation custody path is missing or linked: " + relative)
        attributes = getattr(path.lstat(), "st_file_attributes", 0)
        if attributes & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400):
            raise ValueError("Navigation custody path is a reparse point: " + relative)
    if not path.is_file() or path.stat().st_mode & 0o111:
        raise ValueError("Navigation source must be a nonexecutable regular file: " + relative)
    return path.read_bytes().replace(b"\r\n", b"\n")


def verify_navigation(root: Path, git_read, baseline: dict, current: dict) -> dict:
    adjusted = dict(baseline)
    for relative in sorted(NAVIGATION_PATHS):
        original = git_read(root, "show", f"{BASELINE}:{relative}")
        if baseline.get(relative) != blob_record(original):
            raise ValueError("Pinned navigation mode/type/object changed: " + relative)
        actual = _current_bytes(root, relative)
        if relative in SOURCE_EDITS:
            expected = migrated_source(relative, original)
            if actual != expected:
                raise ValueError("Unreviewed current navigation source change: " + relative)
        else:
            if hashlib.sha256(actual).hexdigest() != TEST_SHA256[relative]:
                raise ValueError("Reviewed navigation behavioral tests changed: " + relative)
            expected = actual
        if current.get(relative) not in {blob_record(original), blob_record(expected)}:
            raise ValueError("Unreviewed committed navigation change: " + relative)
        adjusted[relative] = current[relative]
    return adjusted
