#!/usr/bin/env python3
"""Offline document-inventory preflight, never feed approval or raw coverage."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
from urllib.parse import urlsplit

FORMAT = "wsr-raw-feed-intake-v1"
MAX_BYTES = 65_536
MAX_REFERENCES = 5
REQUIREMENTS = (
    "FEED_SCOPE_AND_HISTORY",
    "PROVIDER_EVENT_IDENTITY",
    "SEQUENCE_AND_GAP_RECOVERY",
    "CORRECTIONS_BUSTS_AND_FINALITY",
    "TRADE_CONDITIONS_AND_AUCTIONS",
    "HALTS_AND_SILENT_INTERVALS",
    "EXCHANGE_CALENDAR",
    "CORPORATE_ACTIONS_AND_PRICE_BASIS",
    "POINT_IN_TIME_AVAILABILITY",
    "HISTORICAL_ACCESS_RIGHTS",
    "STORAGE_AND_CACHE_RIGHTS",
    "DERIVED_CALCULATION_RIGHTS",
    "PUBLIC_DISPLAY_RIGHTS",
    "REDISTRIBUTION_RIGHTS",
    "PUBLISHER_AND_RESELLER_GRANTS",
)


class IntakeError(ValueError):
    """Only static codes cross the CLI boundary, never user content or paths."""


def require(condition: bool, code: str = "INVALID_DOCUMENT") -> None:
    if not condition:
        raise IntakeError(code)


def exact_fields(value: object, fields: set[str]) -> None:
    require(type(value) is dict and set(value) == fields)


def bounded_text(value: object, limit: int) -> None:
    require(type(value) is str)
    require(0 < len(value) <= limit and value.strip() == value)
    require(not any(ord(c) < 32 or 0x7F <= ord(c) <= 0x9F or 0xD800 <= ord(c) <= 0xDFFF for c in value))


def reference(value: object) -> tuple[str, str, str]:
    exact_fields(value, {"url", "section", "revision"})
    bounded_text(value["url"], 2048)
    bounded_text(value["section"], 256)
    bounded_text(value["revision"], 128)
    url = value["url"]
    # These are citations only: never request, resolve DNS, or follow a link.
    # No signed query, credentials, local file path, or fragment-carried secret.
    require(url.startswith("https://") and url.isascii() and not re.search(r"[\s\\?#]", url))
    try:
        parsed = urlsplit(url)
        require(parsed.username is None and parsed.password is None and parsed.port is None)
        require(parsed.hostname is not None and "." in parsed.hostname)
        require(re.fullmatch(r"[A-Za-z0-9.-]+", parsed.hostname) is not None)
        require(not parsed.hostname.startswith(".") and not parsed.hostname.endswith("."))
        require(all(re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?", label)
                    for label in parsed.hostname.split(".")))
    except ValueError:
        raise IntakeError("INVALID_DOCUMENT") from None
    return url, value["section"], value["revision"]


def unique_object(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def reject_constant(value: str) -> None:
    raise IntakeError("INVALID_JSON")


def inspect_bytes(raw: bytes) -> dict:
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, "INPUT_SIZE_LIMIT")
    try:
        document = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object,
                              parse_constant=reject_constant)
    except IntakeError:
        raise
    except (UnicodeError, ValueError, RecursionError):
        raise IntakeError("INVALID_JSON") from None
    exact_fields(document, {"format", "candidate", "evidence"})
    require(document["format"] == FORMAT)
    candidate = document["candidate"]
    exact_fields(candidate, {"provider", "product", "primaryVenue"})
    for value in candidate.values():
        if value is not None:
            bounded_text(value, 128)
    identified = all(value is not None for value in candidate.values())
    evidence = document["evidence"]
    require(type(evidence) is list and len(evidence) <= len(REQUIREMENTS))
    submitted = {}
    for item in evidence:
        exact_fields(item, {"requirement", "references"})
        key = item["requirement"]
        require(type(key) is str and key in REQUIREMENTS and key not in submitted)
        references = item["references"]
        require(type(references) is list and len(references) <= MAX_REFERENCES)
        citations = [reference(value) for value in references]
        require(len(citations) == len(set(citations)))
        submitted[key] = len(citations)
    missing = (["CANDIDATE_IDENTITY"] if not identified else [])
    missing += [key for key in REQUIREMENTS if not submitted.get(key)]
    return {
        "format": "wsr-raw-feed-intake-report-v1",
        "scope": "DOCUMENT_INVENTORY_ONLY",
        "status": "DOCUMENTS_MISSING" if missing else "READY_FOR_HUMAN_REVIEW",
        # Exact input-byte identity, not authenticity, rights or source evidence.
        "inputSha256": hashlib.sha256(raw).hexdigest(),
        "candidateIdentified": identified,
        "missingRequirements": missing,
        "requirements": [{"id": key,
                          "status": "REFERENCES_SUPPLIED" if submitted.get(key) else "NOT_SUPPLIED",
                          "referenceCount": submitted.get(key, 0)} for key in REQUIREMENTS],
        "providerApproved": False,
        "rawCoverageVerified": False,
        "ingestionAllowed": False,
        "scoringEnabled": False,
    }


def read_local(path: Path) -> bytes:
    require(not str(path).startswith(("\\\\", "//")), "LOCAL_REGULAR_FILE_REQUIRED")
    absolute = path.absolute()
    require(not absolute.drive.startswith("\\\\"), "LOCAL_REGULAR_FILE_REQUIRED")
    # Reject links/reparse points in every existing path component. No stdin,
    # directory scan, remote URL, archive, configuration discovery, or .env load.
    for part in (*reversed(absolute.parents), absolute):
        info = part.lstat()
        require(not stat.S_ISLNK(info.st_mode)
                and not getattr(info, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400),
                "LOCAL_REGULAR_FILE_REQUIRED")
    require(stat.S_ISREG(info.st_mode), "LOCAL_REGULAR_FILE_REQUIRED")
    require(0 < info.st_size <= MAX_BYTES, "INPUT_SIZE_LIMIT")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
    descriptor = os.open(absolute, flags)
    with os.fdopen(descriptor, "rb") as stream:
        current = os.fstat(stream.fileno())
        require(stat.S_ISREG(current.st_mode) and (current.st_dev, current.st_ino) == (info.st_dev, info.st_ino),
                "LOCAL_REGULAR_FILE_REQUIRED")
        raw = stream.read(MAX_BYTES + 1)
        require(0 < len(raw) <= MAX_BYTES, "INPUT_SIZE_LIMIT")
        return raw


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", help="One local UTF-8 intake JSON; no credentials or market data")
    # Avoid argparse echoing unknown options/arguments that may contain secrets.
    args = list(sys.argv[1:] if argv is None else argv)
    if args in (["--help"], ["-h"]):
        parser.print_help()
        return 0
    if len(args) != 1 or args[0].startswith("-"):
        print("raw-feed-intake: USAGE_ERROR (use --help)", file=sys.stderr)
        return 1
    try:
        report = inspect_bytes(read_local(Path(args[0])))
    except IntakeError as error:
        print("raw-feed-intake: " + str(error), file=sys.stderr)
        return 1
    except (OSError, ValueError):
        print("raw-feed-intake: INPUT_UNREADABLE", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return 2 if report["missingRequirements"] else 0


if __name__ == "__main__":
    sys.exit(main())
