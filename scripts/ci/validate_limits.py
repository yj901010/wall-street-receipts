#!/usr/bin/env python3
"""Check GitHub workflow size limits without executing workflow content.

PyYAML is a pinned CI-only dependency in scripts/ci/requirements.txt. The
500,000-byte budget deliberately uses the conservative decimal interpretation
of GitHub's 500 KB limit. A run value has a separate 21,000 Unicode-character
budget after YAML scalar decoding, folding, and chomping.

This is not a replacement for GitHub's workflow schema validation. Unsupported
aliases and merge keys are rejected rather than potentially hiding run values.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import sys

try:
    import yaml
except ImportError as error:
    raise SystemExit(
        "CI limits dependency missing: run python -m pip install -r "
        "scripts/ci/requirements.txt"
    ) from error


MAX_WORKFLOW_BYTES = 500_000
MAX_RUN_CHARACTERS = 21_000


def _short(value: str) -> str:
    """Keep diagnostics bounded, including hostile workflow key names."""
    return repr(value[:80] + ("..." if len(value) > 80 else ""))


class WorkflowLoader(yaml.BaseLoader):
    """Retain string keys such as `on`; reject ambiguous YAML constructs."""

    def compose_node(self, parent, index):
        if self.check_event(yaml.events.AliasEvent):
            event = self.peek_event()
            raise yaml.composer.ComposerError(
                None,
                None,
                "YAML aliases are unsupported by the CI limits guard; "
                "expand the value explicitly",
                event.start_mark,
            )
        return super().compose_node(parent, index)

    def construct_mapping(self, node, deep=False):
        if not isinstance(node, yaml.nodes.MappingNode):
            raise yaml.constructor.ConstructorError(
                None, None, "expected a mapping", node.start_mark
            )
        result = {}
        for key_node, value_node in node.value:
            if not isinstance(key_node, yaml.nodes.ScalarNode):
                raise yaml.constructor.ConstructorError(
                    None, None, "mapping keys must be scalars", key_node.start_mark
                )
            key = key_node.value
            if key == "<<":
                raise yaml.constructor.ConstructorError(
                    None,
                    None,
                    "YAML merge keys are unsupported by the CI limits guard; "
                    "expand the mapping explicitly",
                    key_node.start_mark,
                )
            if key in result:
                raise yaml.constructor.ConstructorError(
                    None,
                    None,
                    f"duplicate mapping key {_short(key)}; keep only one value",
                    key_node.start_mark,
                )
            result[key] = self.construct_object(value_node, deep=deep)
        return result


@dataclass(frozen=True)
class WorkflowResult:
    path: str
    byte_count: int
    run_count: int = 0
    max_run_characters: int = 0
    issues: tuple[str, ...] = ()


def load_workflow(text: str, source: str = "<workflow>") -> dict:
    """Parse workflow YAML without a size budget; never execute its content.

    The extraction runner also uses this for its digest-pinned historical
    baseline, which intentionally exceeds today's platform file budget.
    Failures raise ValueError with bounded, source-labelled diagnostics.
    """
    try:
        workflow = yaml.load(text, Loader=WorkflowLoader)
    except yaml.YAMLError as error:
        problem = str(getattr(error, "problem", None) or "invalid YAML")
        mark = getattr(error, "problem_mark", None)
        location = f"line {mark.line + 1}, column {mark.column + 1}: " if mark else ""
        raise ValueError(f"{source}: {location}{problem[:240]}") from error
    except RecursionError as error:
        raise ValueError(f"{source}: workflow nesting is too deep.") from error
    if not isinstance(workflow, dict):
        raise ValueError(f"{source}: workflow must be a mapping.")
    return workflow


def validate_workflow_bytes(content: bytes, path: str = "<memory>") -> WorkflowResult:
    """Validate one raw UTF-8 workflow; never evaluate GitHub expressions."""
    size = len(content)
    if size > MAX_WORKFLOW_BYTES:
        return WorkflowResult(path, size, issues=(
            f"workflow is {size:,} UTF-8 bytes; budget is {MAX_WORKFLOW_BYTES:,} "
            "bytes. Move inline validation code into scripts/ci/.",
        ))
    try:
        source = content.decode("utf-8")
    except UnicodeDecodeError:
        return WorkflowResult(path, size, issues=("workflow must be valid UTF-8.",))
    try:
        workflow = load_workflow(source, path)
    except ValueError as error:
        # WorkflowResult.path is printed separately by the CLI.
        return WorkflowResult(path, size, issues=(str(error).removeprefix(f"{path}: "),))
    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict) or not jobs:
        return WorkflowResult(path, size, issues=("jobs must be a nonempty mapping.",))

    issues = []
    run_count = 0
    max_run_characters = 0
    for job_id, job in jobs.items():
        job_label = f"job {_short(job_id)}"
        if not isinstance(job, dict):
            issues.append(f"{job_label} must be a mapping.")
            continue
        if "steps" not in job:
            if not isinstance(job.get("uses"), str) or not job["uses"].strip():
                issues.append(f"{job_label} must have a steps sequence or reusable-workflow uses.")
            if "run" in job:
                issues.append(f"{job_label}: run belongs inside a step, not on a job.")
            continue
        steps = job["steps"]
        if not isinstance(steps, list):
            issues.append(f"{job_label}: steps must be a sequence; run values cannot be skipped.")
            continue
        if "uses" in job:
            issues.append(f"{job_label}: reusable-workflow uses cannot also contain steps.")
        for index, step in enumerate(steps, start=1):
            step_label = f"{job_label}, step {index}"
            if not isinstance(step, dict):
                issues.append(f"{step_label} must be a mapping.")
                continue
            if "steps" in step:
                issues.append(f"{step_label}: nested steps are invalid; flatten the step sequence.")
            if "run" not in step:
                if not isinstance(step.get("uses"), str) or not step["uses"].strip():
                    issues.append(f"{step_label} must have a string run or uses value.")
                continue
            run = step["run"]
            if not isinstance(run, str):
                issues.append(f"{step_label}: run must be a string, not a mapping or sequence.")
                continue
            if "uses" in step:
                issues.append(f"{step_label}: run and uses cannot both be present.")
            run_count += 1
            characters = len(run)
            max_run_characters = max(max_run_characters, characters)
            if characters > MAX_RUN_CHARACTERS:
                issues.append(
                    f"{step_label}: run has {characters:,} Unicode characters; limit is "
                    f"{MAX_RUN_CHARACTERS:,}. Move this command into a script and invoke it."
                )
    return WorkflowResult(path, size, run_count, max_run_characters, tuple(issues))


def validate_file(path: Path, *, display_path: str | None = None) -> WorkflowResult:
    """Validate a regular local workflow file with a bounded content read."""
    label = display_path if display_path is not None else str(path)
    if path.is_symlink() or not path.is_file():
        return WorkflowResult(label, 0, issues=("workflow must be a regular file.",))
    try:
        # One byte past the budget suffices to reject an oversized file;
        # stat supplies an actionable full size without parsing its content.
        size = path.stat().st_size
        if size > MAX_WORKFLOW_BYTES:
            return WorkflowResult(label, size, issues=(
                f"workflow is {size:,} UTF-8 bytes; budget is {MAX_WORKFLOW_BYTES:,} "
                "bytes. Move inline validation code into scripts/ci/.",
            ))
        with path.open("rb") as stream:
            content = stream.read(MAX_WORKFLOW_BYTES + 1)
        return validate_workflow_bytes(content, label)
    except OSError:
        return WorkflowResult(label, 0, issues=("workflow could not be read.",))


def validate_repository(root: Path) -> list[WorkflowResult]:
    """Inspect every direct .yml/.yaml file GitHub treats as a workflow."""
    directory = root / ".github" / "workflows"
    if not directory.is_dir() or directory.is_symlink():
        return [WorkflowResult(".github/workflows", 0, issues=(
            "workflow directory is missing or is a symlink.",
        ))]
    results = []
    paths = sorted(
        path for path in directory.iterdir() if path.suffix.lower() in {".yml", ".yaml"}
    )
    if not paths:
        return [WorkflowResult(".github/workflows", 0, issues=("no workflows found.",))]
    for path in paths:
        label = path.relative_to(root).as_posix()
        results.append(validate_file(path, display_path=label))
    return results


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args(argv)
    results = validate_repository(args.root.resolve())
    failed = False
    for result in results:
        for issue in result.issues:
            print(f"{result.path}: {issue}", file=sys.stderr)
            failed = True
    if failed:
        print("CI limits FAIL: fix the workflow diagnostics above.", file=sys.stderr)
        return 1
    print(
        f"CI limits PASS: {len(results)} workflows; largest file "
        f"{max(result.byte_count for result in results):,}/{MAX_WORKFLOW_BYTES:,} bytes; "
        f"largest run {max(result.max_run_characters for result in results):,}/"
        f"{MAX_RUN_CHARACTERS:,} characters."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
