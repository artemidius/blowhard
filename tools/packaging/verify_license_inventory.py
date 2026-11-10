#!/usr/bin/env python3
"""Validate the generated desktop runtime dependency and license inventory."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


REQUIRED_RUNTIME_MODULES = frozenset(
    {
        "app.cash.sqldelight:sqlite-driver",
        "io.ktor:ktor-client-core-jvm",
        "net.java.dev.jna:jna-platform",
        "org.jetbrains.compose.desktop:desktop-jvm",
        "org.jetbrains.kotlin:kotlin-stdlib",
    }
)


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot read JSON from {path}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"expected a JSON object in {path}")
    return value


def _allowed_licenses(policy: dict[str, Any]) -> frozenset[str]:
    rules = policy.get("allowedLicenses")
    if not isinstance(rules, list) or not rules:
        raise ValueError("license policy must contain a non-empty allowedLicenses array")

    allowed = {
        rule.get("moduleLicense")
        for rule in rules
        if isinstance(rule, dict) and isinstance(rule.get("moduleLicense"), str)
    }
    allowed.discard("")
    if not allowed:
        raise ValueError("license policy does not name an allowed license")
    return frozenset(allowed)


def validate_inventory(inventory: dict[str, Any], policy: dict[str, Any]) -> list[str]:
    """Return human-readable validation errors for a generated inventory."""
    errors: list[str] = []
    dependencies = inventory.get("dependencies")
    if not isinstance(dependencies, list) or not dependencies:
        return ["inventory must contain a non-empty dependencies array"]

    try:
        allowed = _allowed_licenses(policy)
    except ValueError as error:
        return [str(error)]

    module_names: list[str] = []
    for index, dependency in enumerate(dependencies):
        if not isinstance(dependency, dict):
            errors.append(f"dependency {index} must be an object")
            continue

        module_name = dependency.get("moduleName")
        module_version = dependency.get("moduleVersion")
        if not isinstance(module_name, str) or not module_name or ":" not in module_name:
            errors.append(f"dependency {index} has an invalid moduleName")
            continue
        module_names.append(module_name)

        if module_name.startswith("Blowhard"):
            errors.append(f"internal project module leaked into inventory: {module_name}")
        if not isinstance(module_version, str) or not module_version:
            errors.append(f"dependency {module_name} has no version")

        license_entries = dependency.get("moduleLicenses")
        if not isinstance(license_entries, list):
            errors.append(f"dependency {module_name} has no moduleLicenses array")
            continue
        licenses = {
            entry.get("moduleLicense")
            for entry in license_entries
            if isinstance(entry, dict)
            and isinstance(entry.get("moduleLicense"), str)
            and entry.get("moduleLicense")
        }
        if not licenses:
            errors.append(f"dependency {module_name} has no identified license")
        elif licenses.isdisjoint(allowed):
            errors.append(
                f"dependency {module_name} has no allowed license: {', '.join(sorted(licenses))}"
            )

    if module_names != sorted(module_names):
        errors.append("dependencies must be sorted by moduleName")
    if len(module_names) != len(set(module_names)):
        errors.append("dependencies must not contain duplicate moduleName values")

    missing = REQUIRED_RUNTIME_MODULES.difference(module_names)
    if missing:
        errors.append(f"required runtime modules are missing: {', '.join(sorted(missing))}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("policy", type=Path)
    arguments = parser.parse_args()

    try:
        inventory = _load_json(arguments.inventory)
        errors = validate_inventory(
            inventory,
            _load_json(arguments.policy),
        )
    except ValueError as error:
        print(f"License inventory verification failed: {error}")
        return 1

    if errors:
        print("License inventory verification failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    dependency_count = len(inventory["dependencies"])
    print(f"Verified {dependency_count} licensed desktop runtime dependencies")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
