#!/usr/bin/env python3
"""Regression tests for the real Spring transport proof and KST date window."""

from contextlib import redirect_stderr, redirect_stdout
from datetime import date
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from verify_call_audit_access import (
    kst_filter_window,
    main,
    required_access_lines,
    verify_access,
    verify_log_directory,
)


EXPECTED = frozenset({
    "GET /v1/calls?dataMode=DEMO&page=0&size=25&sort=eventTime&order=desc 200",
    "GET /v1/calls?assetId=asset-nvda&ticker=nvda&institutionId=inst-gs&analystId=analyst-demo-b&direction=BULLISH&status=ACTIVE&dataMode=DEMO&from=2026-08-10T15%3A00%3A00.000Z&to=2026-08-11T15%3A00%3A00.000Z&page=0&size=1&sort=capturedAt&order=asc 200",
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


def log_text(lines=EXPECTED) -> str:
    return "\n".join(sorted(lines)) + "\n"


class CallAuditAccessTests(unittest.TestCase):
    def test_exact_thirteen_original_resources_with_only_kst_window_changed(self):
        self.assertEqual(len(EXPECTED), 13)
        self.assertEqual(required_access_lines(), EXPECTED)

    def test_kst_window_is_previous_day_1500_to_current_day_1500_utc(self):
        self.assertEqual(kst_filter_window(date(2026, 8, 11)), (
            "2026-08-10T15:00:00.000Z", "2026-08-11T15:00:00.000Z",
        ))

    def test_kst_window_crosses_year_boundary(self):
        self.assertEqual(kst_filter_window(date(2026, 1, 1)), (
            "2025-12-31T15:00:00.000Z", "2026-01-01T15:00:00.000Z",
        ))

    def test_host_timezone_does_not_change_expectations(self):
        for host_timezone in ("UTC", "America/New_York", "Pacific/Honolulu", "Asia/Seoul"):
            with self.subTest(host_timezone=host_timezone):
                with patch.dict(os.environ, {"TZ": host_timezone}):
                    self.assertEqual(required_access_lines(), EXPECTED)

    def test_complete_kst_evidence_passes(self):
        verify_access(log_text())

    def test_old_utc_midnight_filter_fails(self):
        old = log_text().replace(
            "from=2026-08-10T15%3A00%3A00.000Z&to=2026-08-11T15%3A00%3A00.000Z",
            "from=2026-08-11T00%3A00%3A00.000Z&to=2026-08-12T00%3A00%3A00.000Z",
        )
        with self.assertRaisesRegex(ValueError, "complete Spring audit"):
            verify_access(old)

    def test_all_missing_fails(self):
        with self.assertRaisesRegex(ValueError, "complete Spring audit"):
            verify_access("")

    def test_every_endpoint_is_individually_required(self):
        for endpoint in EXPECTED:
            with self.subTest(endpoint=endpoint):
                with self.assertRaises(ValueError) as raised:
                    verify_access(log_text(EXPECTED - {endpoint}))
                self.assertIn(endpoint, str(raised.exception))

    def test_non_200_cannot_replace_any_required_endpoint(self):
        for endpoint in EXPECTED:
            for status in ("204", "301", "400", "404", "429", "500", "503"):
                with self.subTest(endpoint=endpoint, status=status):
                    evidence = (EXPECTED - {endpoint}) | {endpoint.removesuffix("200") + status}
                    with self.assertRaises(ValueError):
                        verify_access(log_text(evidence))

    def test_all_error_responses_fail(self):
        with self.assertRaises(ValueError):
            verify_access(log_text().replace(" 200", " 500"))

    def test_other_query_parameters_are_not_relaxed(self):
        for original, replacement in (
            ("ticker=nvda", "ticker=NVDA"),
            ("institutionId=inst-gs", "institutionId=inst-jpm"),
            ("analystId=analyst-demo-b", "analystId=analyst-demo-a"),
            ("status=ACTIVE", "status=WITHDRAWN"),
            ("dataMode=DEMO", "dataMode=LIVE"),
            ("sort=capturedAt", "sort=eventTime"),
            ("order=asc", "order=desc"),
        ):
            with self.subTest(parameter=original):
                with self.assertRaises(ValueError):
                    verify_access(log_text().replace(original, replacement))

    def test_fixture_marker_is_not_transport_evidence(self):
        with self.assertRaises(ValueError):
            verify_access("\n".join(f"fixture: {line}" for line in EXPECTED))

    def test_blank_lines_duplicate_reads_and_rotated_logs_are_supported(self):
        with tempfile.TemporaryDirectory() as temporary:
            log_root = Path(temporary)
            lines = sorted(EXPECTED)
            (log_root / "call_audit_access.2026-08-11.log").write_text(
                log_text(lines[:7]) + "\n", encoding="utf-8",
            )
            (log_root / "call_audit_access.2026-08-12.log").write_text(
                log_text(lines[7:]) + log_text(lines[:1]), encoding="utf-8",
            )
            verify_log_directory(log_root)

    def test_missing_log_directory_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "Spring access log is missing"):
                verify_log_directory(Path(temporary) / "missing")

    def test_other_file_names_cannot_supply_fallback_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            log_root = Path(temporary)
            (log_root / "fixture.json").write_text(log_text(), encoding="utf-8")
            (log_root / "next.log").write_text(log_text(), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Spring access log is missing"):
                verify_log_directory(log_root)

    def test_empty_log_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            log_root = Path(temporary)
            (log_root / "call_audit_access.log").write_text("", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "complete Spring audit"):
                verify_log_directory(log_root)

    def test_main_requires_runner_temp(self):
        with patch.dict(os.environ, {}, clear=True):
            with redirect_stderr(io.StringIO()) as errors:
                self.assertEqual(main(), 1)
            self.assertIn("RUNNER_TEMP is required", errors.getvalue())

    def test_main_reads_only_scoped_spring_logs(self):
        with tempfile.TemporaryDirectory() as temporary:
            log_root = Path(temporary) / "call-audit-tomcat" / "logs"
            log_root.mkdir(parents=True)
            (log_root / "call_audit_access.log").write_text(log_text(), encoding="utf-8")
            with patch.dict(os.environ, {"RUNNER_TEMP": temporary}):
                with redirect_stdout(io.StringIO()) as output:
                    self.assertEqual(main(), 0)
            self.assertIn("through Spring", output.getvalue())

    def test_fixture_provider_does_not_bypass_missing_real_logs(self):
        with tempfile.TemporaryDirectory() as temporary:
            with patch.dict(os.environ, {
                "RUNNER_TEMP": temporary, "CALL_AUDIT_PROVIDER": "fixture",
                "NEXT_PUBLIC_DATA_MODE": "DEMO",
            }):
                with redirect_stderr(io.StringIO()):
                    self.assertEqual(main(), 1)

    def test_invalid_utf8_logs_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            log_root = Path(temporary) / "call-audit-tomcat" / "logs"
            log_root.mkdir(parents=True)
            (log_root / "call_audit_access.log").write_bytes(b"\xff")
            with patch.dict(os.environ, {"RUNNER_TEMP": temporary}):
                with redirect_stderr(io.StringIO()):
                    self.assertEqual(main(), 1)


if __name__ == "__main__":
    unittest.main()
