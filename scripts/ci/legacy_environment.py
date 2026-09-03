"""Explicit, reversible execution fixture for one frozen Linux contract test.

The pinned step 12 directly executes a source-only module whose Git mode is
100644. On POSIX that returns 126 before the module can reject direct use with
64. This adapter changes no script bytes or Git metadata: for that step alone,
it verifies the detached baseline, exact module blob/mode and clean checkout,
temporarily adds owner-execute permission, then restores the original physical
mode and verifies bytes, identity and Git custody even when execution fails.

Windows needs no permission adaptation. Other step indexes are strict no-ops.
Unexpected replacement/linking is rejected without chmod'ing the replacement;
the original execution exception remains chained if restoration also fails.
"""
from __future__ import annotations

from contextlib import contextmanager
import hashlib
import os
from pathlib import Path
import stat
from typing import Callable, Iterator


BASELINE = "3792100f49c496d751d1dd54a7fbdc1b7c2fd275"
STEP_INDEX = 12
MODULE_PATH = "deploy/home-server/generation-state.sh"
MODULE_BLOB = "91098cde8c147ec6138566200d98c6ab8a8047a7"
MODULE_TREE_RECORD = f"100644 blob {MODULE_BLOB}\t{MODULE_PATH}\0".encode("ascii")
_WINDOWS = os.name == "nt"


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(f"Legacy step 12 execution fixture: {message}")


def _regular_info(path: Path):
    info = path.lstat()
    _require(stat.S_ISREG(info.st_mode) and not path.is_symlink(), "module is not a regular nonlinked file")
    _require(info.st_nlink == 1, "hard-linked module rejected")
    _require(not (getattr(info, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0)),
             "module reparse point rejected")
    return info


def _set_mode(path: Path, mode: int) -> None:
    path.chmod(mode)


def _validate_module_path(root: Path, target: Path) -> None:
    _require(root.is_dir() and not root.is_symlink() and root.resolve(strict=True) == root,
             "legacy root is missing, linked, or changed")
    for parent in (target.parent, target.parent.parent):
        _require(parent.is_dir() and not parent.is_symlink(), "module parent is missing or linked")
    _require(target.resolve(strict=True).is_relative_to(root), "module path escaped legacy checkout")


def _validate_git_view(legacy: Path, git_read: Callable[..., bytes]) -> None:
    _require(git_read(legacy, "rev-parse", "HEAD").strip() == BASELINE.encode("ascii"),
             "historical HEAD differs from pinned baseline")
    _require(git_read(legacy, "rev-parse", "--abbrev-ref", "HEAD").strip() == b"HEAD",
             "historical HEAD must be detached")
    _require(git_read(legacy, "ls-tree", "-z", "HEAD", "--", MODULE_PATH) == MODULE_TREE_RECORD,
             "pinned module path, blob, or Git mode differs")
    _require(not git_read(legacy, "status", "--porcelain=v1", "--untracked-files=all"),
             "historical checkout is not clean")


def _blob_identity(raw: bytes) -> str:
    # This is Git's existing object identity, not a new signature/trust scheme.
    return hashlib.sha1(f"blob {len(raw)}\0".encode("ascii") + raw).hexdigest()


@contextmanager
def legacy_step_environment(index: int, legacy: Path, git_read: Callable[..., bytes]) -> Iterator[None]:
    """Run an unchanged legacy step with the sole approved permission fixture.

    ``git_read`` must be the caller's bounded, read-only/local Git wrapper. A
    failing body propagates unchanged when custody restoration succeeds. A
    restoration failure is fatal; no byte repair, index write, repository-wide
    chmod, fileMode override, or acceptance of exit 126 is performed.
    """
    if index != STEP_INDEX:
        yield
        return

    _require(legacy.is_dir() and not legacy.is_symlink(), "legacy root is missing or linked")
    root = legacy.resolve(strict=True)
    target = root / MODULE_PATH
    _validate_module_path(root, target)
    before = _regular_info(target)
    _validate_git_view(root, git_read)
    original_bytes = target.read_bytes()
    _require(_blob_identity(original_bytes) == MODULE_BLOB, "physical module bytes differ from pinned blob")
    original_mode = stat.S_IMODE(before.st_mode)
    if not _WINDOWS:
        _require(not (original_mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)),
                 "physical module was already executable despite pinned 100644 mode")
    original_identity = (before.st_dev, before.st_ino)

    try:
        if not _WINDOWS:
            _set_mode(target, original_mode | stat.S_IXUSR)
        yield
    finally:
        _validate_module_path(root, target)
        current = _regular_info(target)
        _require((current.st_dev, current.st_ino) == original_identity,
                 "module was replaced; refusing permission changes to replacement")
        if not _WINDOWS and stat.S_IMODE(current.st_mode) != original_mode:
            _set_mode(target, original_mode)
        _require(stat.S_IMODE(_regular_info(target).st_mode) == original_mode,
                 "physical module mode was not restored")
        _require(target.read_bytes() == original_bytes, "module bytes changed during historical execution")
        _validate_git_view(root, git_read)
