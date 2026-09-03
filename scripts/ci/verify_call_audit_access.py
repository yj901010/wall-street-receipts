#!/usr/bin/env python3
"""Require real Spring access-log evidence for the Next call-audit integration.

The browser's inclusive 2026-08-11 date filter is a Korean calendar day. Its
transport interval is [2026-08-10T15:00Z, 2026-08-11T15:00Z), not UTC midnight.
KST is explicitly UTC+09:00 for this fixed modern test date; no host timezone,
timezone database, fixture file, or provider fallback is consulted.
"""

from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
import os
from pathlib import Path
import sys
from urllib.parse import quote


FILTER_DAY = date(2026, 8, 11)
KST = timezone(timedelta(hours=9), name="KST")


def kst_filter_window(day: date) -> tuple[str, str]:
    """Return the UTC half-open interval for one inclusive KST filter day."""
    start = datetime.combine(day, time.min, tzinfo=KST)
    end = start + timedelta(days=1)
    return tuple(
        value.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
        for value in (start, end)
    )


def required_access_lines() -> frozenset[str]:
    """Keep every original request parameter and Tomcat no-query '-' marker."""
    start, end = (quote(value, safe="-TZ.") for value in kst_filter_window(FILTER_DAY))
    return frozenset({
        "GET /v1/calls?dataMode=DEMO&page=0&size=25&sort=eventTime&order=desc 200",
        "GET /v1/calls?assetId=asset-nvda&ticker=nvda&institutionId=inst-gs"
        "&analystId=analyst-demo-b&direction=BULLISH&status=ACTIVE&dataMode=DEMO"
        f"&from={start}&to={end}&page=0&size=1&sort=capturedAt&order=asc 200",
        "GET /v1/calls?dataMode=DEMO&page=0&size=1&sort=eventTime&order=desc 200",
        "GET /v1/calls?dataMode=DEMO&page=1&size=1&sort=eventTime&order=desc 200",
        "GET /v1/calls?ticker=TSLA&dataMode=DEMO&page=0&size=1&sort=eventTime&order=desc 200",
        "GET /v1/calls/demo-call-002- 200",
        "GET /v1/calls/demo-call-002/context- 200",
        "GET /v1/calls/demo-call-002/revisions- 200",
        "GET /v1/calls/demo-call-002/outcomes- 200",
        "GET /v1/calls/demo-call-001- 200",
        "GET /v1/calls/demo-call-001/context- 200",
        "GET /v1/calls/demo-call-001/revisions- 200",
        "GET /v1/calls/demo-call-001/outcomes- 200",
    })


def verify_access(access: str) -> None:
    """Only exact observed GET 200 lines can satisfy the transport evidence."""
    access_lines = {line.strip() for line in access.splitlines() if line.strip()}
    missing = sorted(required_access_lines() - access_lines)
    if missing:
        raise ValueError(f"Next did not exercise the complete Spring audit: {missing}")


def verify_log_directory(log_root: Path) -> None:
    logs = sorted(log_root.glob("call_audit_access*.log"))
    if not logs:
        raise ValueError(f"Spring access log is missing under {log_root}")
    access = "\n".join(path.read_text(encoding="utf-8") for path in logs)
    verify_access(access)


def main() -> int:
    runner_temp = os.environ.get("RUNNER_TEMP")
    if not runner_temp:
        print("RUNNER_TEMP is required for real Spring access-log evidence", file=sys.stderr)
        return 1
    log_root = Path(runner_temp) / "call-audit-tomcat" / "logs"
    try:
        verify_log_directory(log_root)
    except (OSError, UnicodeError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1
    print("Verified list plus populated and known-empty detail/context/revision/outcome reads through Spring")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
