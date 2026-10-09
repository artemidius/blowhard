#!/usr/bin/env python3
"""Validate release inputs, assemble native assets, and verify a merged release set."""

from __future__ import annotations

import argparse
import json
import shutil
import zipfile
from pathlib import Path, PurePosixPath

from release_evidence import (
    REVISION_PATTERN,
    TARGETS,
    digest,
    locate_package,
    read_versions,
    target_for,
    verify,
)
from verify_license_inventory import _load_json, validate_inventory


class ReleaseGateError(RuntimeError):
    """Raised when a release input or assembled asset set is unsafe or incomplete."""


def validate_project_license(repository_root: Path) -> None:
    license_file = repository_root / "LICENSE"
    try:
        content = license_file.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        raise ReleaseGateError("LICENSE must be readable UTF-8") from error
    required_text = (
        "MIT License",
        "Copyright (c) 2026 Blowhard contributors",
        "Permission is hereby granted, free of charge",
        'THE SOFTWARE IS PROVIDED "AS IS"',
    )
    if not all(text in content for text in required_text):
        raise ReleaseGateError("LICENSE is not the reviewed Blowhard MIT license")


def validate_source(repository_root: Path, tag: str, revision: str, git_ref: str) -> Path:
    """Validate immutable source inputs and return the reviewed release-notes path."""
    release_version, _ = read_versions(repository_root / "gradle.properties")
    if tag != f"v{release_version}":
        raise ReleaseGateError(f"release tag must be v{release_version}")
    if REVISION_PATTERN.fullmatch(revision) is None:
        raise ReleaseGateError("source revision must be a lowercase 40-character Git SHA")
    if git_ref != "refs/heads/main":
        raise ReleaseGateError("release workflow must be dispatched from main")
    validate_project_license(repository_root)

    notes = repository_root / "docs/releases" / f"v{release_version}.md"
    try:
        content = notes.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        raise ReleaseGateError(f"release notes are missing: {notes}") from error
    if len(content.strip()) < 100:
        raise ReleaseGateError("release notes are unexpectedly short")
    return notes


def _write_deterministic_zip(source: Path, destination: Path, prefix: str) -> None:
    files = sorted(path for path in source.rglob("*") if path.is_file())
    if not files:
        raise ReleaseGateError(f"cannot archive an empty directory: {source}")
    for path in source.rglob("*"):
        if path.is_symlink():
            raise ReleaseGateError(f"archive source contains a symbolic link: {path}")

    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            relative = PurePosixPath(prefix) / PurePosixPath(path.relative_to(source).as_posix())
            information = zipfile.ZipInfo(str(relative), date_time=(1980, 1, 1, 0, 0, 0))
            information.compress_type = zipfile.ZIP_DEFLATED
            information.external_attr = 0o100644 << 16
            archive.writestr(information, path.read_bytes())


def _validate_inventory(inventory_file: Path, policy_file: Path) -> None:
    errors = validate_inventory(_load_json(inventory_file), _load_json(policy_file))
    if errors:
        raise ReleaseGateError("invalid dependency inventory: " + "; ".join(errors))


def prepare_native_assets(
    repository_root: Path,
    manifest_file: Path,
    report_directory: Path,
    output_directory: Path,
) -> list[Path]:
    """Copy and deterministically name one verified native candidate set."""
    verify(repository_root, manifest_file)
    policy_file = repository_root / "config/allowed-runtime-licenses.json"
    inventory_file = report_directory / "dependencies.json"
    markdown_file = report_directory / "THIRD_PARTY_LICENSES.md"
    _validate_inventory(inventory_file, policy_file)
    if not markdown_file.is_file():
        raise ReleaseGateError("third-party Markdown license report is missing")

    manifest = _load_json(manifest_file)
    target_data = manifest["target"]
    target = target_for(target_data["operatingSystem"], target_data["architecture"])
    package = locate_package(repository_root, target)
    prefix = f"Blowhard-{manifest['releaseVersion']}-{target.operating_system}-{target.architecture}"
    output_directory.mkdir(parents=True, exist_ok=True)
    if any(output_directory.iterdir()):
        raise ReleaseGateError(f"release output directory is not empty: {output_directory}")

    destinations = [
        output_directory / package.name,
        output_directory / f"{prefix}.manifest.json",
        output_directory / f"{prefix}.SHA256SUMS",
        output_directory / f"{prefix}.third-party-licenses.zip",
    ]
    shutil.copyfile(package, destinations[0])
    shutil.copyfile(manifest_file, destinations[1])
    shutil.copyfile(manifest_file.with_name("SHA256SUMS"), destinations[2])
    _write_deterministic_zip(report_directory, destinations[3], "third-party-licenses")
    return destinations


def prepare_documentation_assets(repository_root: Path, output_directory: Path) -> list[Path]:
    """Package the reviewed project license and generated public KDoc."""
    validate_project_license(repository_root)
    release_version, _ = read_versions(repository_root / "gradle.properties")
    output_directory.mkdir(parents=True, exist_ok=True)
    if any(output_directory.iterdir()):
        raise ReleaseGateError(f"release output directory is not empty: {output_directory}")

    license_asset = output_directory / f"Blowhard-{release_version}-LICENSE.txt"
    kdoc_asset = output_directory / f"Blowhard-{release_version}-kdoc.zip"
    shutil.copyfile(repository_root / "LICENSE", license_asset)
    _write_deterministic_zip(repository_root / "build/dokka/html", kdoc_asset, "kdoc")
    return [license_asset, kdoc_asset]


def _safe_zip_names(archive_file: Path) -> list[str]:
    try:
        with zipfile.ZipFile(archive_file) as archive:
            names = archive.namelist()
    except zipfile.BadZipFile as error:
        raise ReleaseGateError(f"invalid ZIP archive: {archive_file.name}") from error
    if len(names) != len(set(names)) or names != sorted(names):
        raise ReleaseGateError(f"archive entries are duplicate or unsorted: {archive_file.name}")
    for name in names:
        path = PurePosixPath(name)
        if path.is_absolute() or ".." in path.parts or "\\" in name:
            raise ReleaseGateError(f"unsafe archive entry in {archive_file.name}: {name}")
    return names


def verify_release_assets(repository_root: Path, assets_directory: Path, revision: str) -> None:
    """Re-verify a flat, merged three-platform asset set before draft publication."""
    if REVISION_PATTERN.fullmatch(revision) is None:
        raise ReleaseGateError("source revision must be a lowercase 40-character Git SHA")
    validate_project_license(repository_root)
    release_version, installer_version = read_versions(repository_root / "gradle.properties")
    policy_file = repository_root / "config/allowed-runtime-licenses.json"

    expected_files = {
        f"Blowhard-{release_version}-LICENSE.txt",
        f"Blowhard-{release_version}-kdoc.zip",
    }
    observed_targets: set[tuple[str, str]] = set()
    manifests = sorted(assets_directory.glob(f"Blowhard-{release_version}-*.manifest.json"))
    if len(manifests) != len(TARGETS):
        raise ReleaseGateError(f"expected {len(TARGETS)} native manifests, found {len(manifests)}")

    for manifest_file in manifests:
        manifest = _load_json(manifest_file)
        target_data = manifest.get("target", {})
        key = (target_data.get("operatingSystem"), target_data.get("architecture"))
        target = target_for(*key)
        if key in observed_targets:
            raise ReleaseGateError(f"duplicate release target: {key[0]}/{key[1]}")
        observed_targets.add(key)

        prefix = f"Blowhard-{release_version}-{target.operating_system}-{target.architecture}"
        artifact = manifest.get("artifact", {})
        artifact_name = artifact.get("fileName")
        if (
            not isinstance(artifact_name, str)
            or PurePosixPath(artifact_name).name != artifact_name
            or "\\" in artifact_name
            or not artifact_name.endswith(f".{target.package_format}")
        ):
            raise ReleaseGateError(f"unsafe package filename in {manifest_file.name}")
        package = assets_directory / artifact_name
        checksum_file = assets_directory / f"{prefix}.SHA256SUMS"
        license_archive = assets_directory / f"{prefix}.third-party-licenses.zip"
        expected_manifest = {
            "schemaVersion": 1,
            "project": "Blowhard",
            "releaseVersion": release_version,
            "installerVersion": installer_version,
            "sourceRevision": revision,
            "target": {
                "operatingSystem": target.operating_system,
                "architecture": target.architecture,
                "format": target.package_format,
                "signing": target.signing,
            },
            "artifact": {
                "fileName": package.name,
                "sizeBytes": package.stat().st_size if package.is_file() else -1,
                "sha256": digest(package) if package.is_file() else "",
            },
        }
        if manifest != expected_manifest:
            raise ReleaseGateError(f"manifest or package mismatch: {manifest_file.name}")
        expected_checksum = f"{expected_manifest['artifact']['sha256']}  {package.name}\n"
        try:
            checksum_content = checksum_file.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as error:
            raise ReleaseGateError(f"checksum file is missing: {checksum_file.name}") from error
        if checksum_content != expected_checksum:
            raise ReleaseGateError(f"checksum file mismatch: {checksum_file.name}")

        names = _safe_zip_names(license_archive)
        required_entries = {
            "third-party-licenses/dependencies.json",
            "third-party-licenses/THIRD_PARTY_LICENSES.md",
        }
        if not required_entries.issubset(names):
            raise ReleaseGateError(f"license archive is incomplete: {license_archive.name}")
        with zipfile.ZipFile(license_archive) as archive:
            inventory = json.loads(archive.read("third-party-licenses/dependencies.json"))
        errors = validate_inventory(inventory, _load_json(policy_file))
        if errors:
            raise ReleaseGateError("invalid archived dependency inventory: " + "; ".join(errors))

        expected_files.update(
            {
                package.name,
                manifest_file.name,
                checksum_file.name,
                license_archive.name,
            }
        )

    if observed_targets != set(TARGETS):
        raise ReleaseGateError("release target matrix is incomplete")

    license_asset = assets_directory / f"Blowhard-{release_version}-LICENSE.txt"
    if license_asset.read_bytes() != (repository_root / "LICENSE").read_bytes():
        raise ReleaseGateError("release license asset does not match the repository license")
    kdoc_names = _safe_zip_names(assets_directory / f"Blowhard-{release_version}-kdoc.zip")
    if "kdoc/index.html" not in kdoc_names:
        raise ReleaseGateError("KDoc archive does not contain kdoc/index.html")

    actual_files = {path.name for path in assets_directory.iterdir() if path.is_file()}
    if actual_files != expected_files:
        unexpected = sorted(actual_files.difference(expected_files))
        missing = sorted(expected_files.difference(actual_files))
        raise ReleaseGateError(f"release asset set differs; missing={missing}, unexpected={unexpected}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    source_parser = subparsers.add_parser("validate-source")
    source_parser.add_argument("repository_root", type=Path)
    source_parser.add_argument("--tag", required=True)
    source_parser.add_argument("--revision", required=True)
    source_parser.add_argument("--git-ref", required=True)

    native_parser = subparsers.add_parser("prepare-native")
    native_parser.add_argument("repository_root", type=Path)
    native_parser.add_argument("manifest", type=Path)
    native_parser.add_argument("report_directory", type=Path)
    native_parser.add_argument("output_directory", type=Path)

    docs_parser = subparsers.add_parser("prepare-docs")
    docs_parser.add_argument("repository_root", type=Path)
    docs_parser.add_argument("output_directory", type=Path)

    verify_parser = subparsers.add_parser("verify-assets")
    verify_parser.add_argument("repository_root", type=Path)
    verify_parser.add_argument("assets_directory", type=Path)
    verify_parser.add_argument("--revision", required=True)

    arguments = parser.parse_args()
    try:
        if arguments.command == "validate-source":
            notes = validate_source(
                arguments.repository_root.resolve(),
                arguments.tag,
                arguments.revision,
                arguments.git_ref,
            )
            print(f"Validated release source and notes: {notes}")
        elif arguments.command == "prepare-native":
            assets = prepare_native_assets(
                arguments.repository_root.resolve(),
                arguments.manifest.resolve(),
                arguments.report_directory.resolve(),
                arguments.output_directory.resolve(),
            )
            print(f"Prepared {len(assets)} native release assets")
        elif arguments.command == "prepare-docs":
            assets = prepare_documentation_assets(
                arguments.repository_root.resolve(),
                arguments.output_directory.resolve(),
            )
            print(f"Prepared {len(assets)} documentation release assets")
        else:
            verify_release_assets(
                arguments.repository_root.resolve(),
                arguments.assets_directory.resolve(),
                arguments.revision,
            )
            print("Verified complete release asset set")
    except (OSError, UnicodeError, json.JSONDecodeError, KeyError, ValueError, ReleaseGateError) as error:
        print(f"Release gate failed: {error}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
