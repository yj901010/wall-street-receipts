"""Exact ADR-089 additions only; no baseline edits or scoring exceptions."""
import hashlib
from pathlib import Path

from current_contracts import blob_record
from scoring_contracts import current_bytes

CONTENT_SHA256 = {
    "apps/web/e2e/reference-widget.spec.ts": "9cbcd5b0d9867bec27ee7e70d60295050f368f92fc6bddca74b524e8eee04e70",
    "apps/web/reference-widget/playwright.config.ts": "bfd018c6eb3ba180837ecd395d03180cb719739e64bb5b3b4b275cf97d332ec8",
    "apps/web/reference-widget/public.config.ts": "b205ee2ddc653a19637e9c6107c46e7196b313486b639ef52ae60010e1728482",
    "apps/web/reference-widget/tests/widget.spec.ts": "5536267e9a77029fa7fbcbdaab0e544057c8f655d851c58d2c820130e7065b67",
    "apps/web/src/app/market/reference/AAPL/config.test.ts": "6dc9c45d341fbba28526880323aa3c073a0e5a698d0aa0b9716b4c8c07180581",
    "apps/web/src/app/market/reference/AAPL/config.ts": "8a07ad7d9fa956fdedcf3ec2994fdc18255745f0e0f1efca7f6405f35de6289e",
    "apps/web/src/app/market/reference/AAPL/messages.ts": "d894bb3bce5872584d8b613f56865fa054b4f330f8c30af0798e3a4f58cf957b",
    "apps/web/src/app/market/reference/AAPL/page.test.tsx": "3320a5cc9df9a619d83729d5c49560803e0d014c4e71e743f67a85d8b4f6b249",
    "apps/web/src/app/market/reference/AAPL/page.tsx": "84d6a11848eaf0aca65bad0faf2363a9ea565a6fcfc40ee59b8539344c179611",
    "apps/web/src/app/market/reference/AAPL/reference-widget.test.tsx": "921d57cf71ee3c72d99851bb2b242c89555b06cd86e63eb8e3e36a604b3e2401",
    "apps/web/src/app/market/reference/AAPL/reference-widget.tsx": "70658fd1ac83973aaa7e434def8e985a5dceba8fc48f721c6acf02484b95771b",
    "apps/web/src/app/market/reference/AAPL/reference.module.css": "db359f41e2f6ac8e6c0d19fb347fe32a95d032a9bb76829f527795f447a0965a",
    "VUNELIX_REFERENCE_WIDGET.md": "e130ffe590424cefda5e09ba7fa2f7a6e93de9cef30dc68d94e607e74c5faa9e",
}
REFERENCE_WIDGET_PATHS = frozenset(CONTENT_SHA256)


def verify_reference_widget(root: Path, baseline: dict, current: dict) -> dict:
    adjusted = dict(baseline)
    for relative, expected in CONTENT_SHA256.items():
        if relative in baseline:
            raise ValueError("Unexpected reference path in frozen baseline: " + relative)
        raw = current_bytes(root, relative)
        if hashlib.sha256(raw).hexdigest() != expected:
            raise ValueError("Unreviewed current reference source: " + relative)
        if current.get(relative) not in {None, blob_record(raw)}:
            raise ValueError("Unreviewed committed reference source: " + relative)
        if relative in current:
            adjusted[relative] = current[relative]
    return adjusted
