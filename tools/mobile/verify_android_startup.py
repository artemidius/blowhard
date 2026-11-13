#!/usr/bin/env python3
"""Cold-launch an Android app and emit bounded, machine-readable startup evidence."""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from typing import Any


REQUIRED_FIELDS = ("Status", "LaunchState", "TotalTime", "WaitTime")


def parse_activity_start(output: str) -> dict[str, str]:
    """Parse the stable key/value surface emitted by `am start -W`."""
    fields: dict[str, str] = {}
    for raw_line in output.splitlines():
        key, separator, value = raw_line.partition(":")
        if separator:
            fields[key.strip()] = value.strip()
    missing = [field for field in REQUIRED_FIELDS if field not in fields]
    if missing:
        raise ValueError(f"Android launch output omitted: {', '.join(missing)}")
    if fields["Status"] != "ok":
        raise ValueError(f"Android launch failed with status {fields['Status']!r}")
    return fields


def create_evidence(
    output: str,
    package_name: str,
    component_name: str,
    maximum_total_time_ms: int,
) -> dict[str, Any]:
    """Validate launch output and return the allowlisted evidence model."""
    fields = parse_activity_start(output)
    if fields["LaunchState"] != "COLD":
        raise ValueError(
            f"Android launch was {fields['LaunchState']!r}; expected a cold launch",
        )
    total_time = parse_non_negative_milliseconds(fields, "TotalTime")
    if total_time > maximum_total_time_ms:
        raise ValueError(
            f"Cold launch took {total_time} ms; budget is {maximum_total_time_ms} ms",
        )
    evidence: dict[str, Any] = {
        "schema_version": 1,
        "package": package_name,
        "component": component_name,
        "launch_state": fields["LaunchState"],
        "status": fields["Status"],
        "total_time_ms": total_time,
        "wait_time_ms": parse_non_negative_milliseconds(fields, "WaitTime"),
        "maximum_total_time_ms": maximum_total_time_ms,
    }
    if "ThisTime" in fields:
        evidence["this_time_ms"] = parse_non_negative_milliseconds(fields, "ThisTime")
    return evidence


def parse_non_negative_milliseconds(fields: dict[str, str], name: str) -> int:
    value = int(fields[name])
    if value < 0:
        raise ValueError(f"{name} must be non-negative")
    return value


def run_adb(adb: str, *arguments: str) -> str:
    completed = subprocess.run(
        [adb, *arguments],
        check=True,
        capture_output=True,
        text=True,
    )
    return completed.stdout


def verify_process(adb: str, package_name: str) -> None:
    process_ids = run_adb(adb, "shell", "pidof", package_name).strip().split()
    if not process_ids or not all(process_id.isdigit() for process_id in process_ids):
        raise ValueError("Android application process is not alive after launch")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True)
    parser.add_argument("--component", required=True)
    parser.add_argument("--max-total-time-ms", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--adb", default="adb")
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    if arguments.max_total_time_ms <= 0:
        raise ValueError("Startup budget must be positive")
    run_adb(arguments.adb, "shell", "am", "force-stop", arguments.package)
    launch_output = run_adb(
        arguments.adb,
        "shell",
        "am",
        "start",
        "-W",
        "-n",
        arguments.component,
    )
    evidence = create_evidence(
        launch_output,
        arguments.package,
        arguments.component,
        arguments.max_total_time_ms,
    )
    verify_process(arguments.adb, arguments.package)
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(evidence, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
