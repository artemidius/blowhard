#!/usr/bin/env python3
"""Create and verify deterministic metadata for one native installer."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path

RELEASE_VERSION_PROPERTY = "blowhardReleaseVersion"
INSTALLER_VERSION_PROPERTY = "blowhardInstallerVersion"
VERSION_PATTERN = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
REVISION_PATTERN = re.compile(r"^[0-9a-f]{40}$")


class ReleaseEvidenceError(RuntimeError):
    """Raised when release evidence is ambiguous, invalid, or stale."""


@dataclass(frozen=True)
class ReleaseTarget:
    operating_system: str
    architecture: str
    package_format: str
    signing: str


TARGETS = {
    ("linux", "amd64"): ReleaseTarget("linux", "amd64", "deb", "unsigned"),
    ("windows", "x64"): ReleaseTarget("windows", "x64", "msi", "unsigned"),
    ("macos", "arm64"): ReleaseTarget("macos", "arm64", "dmg", "ad-hoc"),
}


def read_versions(properties_file: Path) -> tuple[str, str]:
    values: dict[str, str] = {}
    for raw_line in properties_file.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if key in values:
            raise ReleaseEvidenceError(f"duplicate property: {key}")
        values[key] = value

    try:
        release_version = values[RELEASE_VERSION_PROPERTY]
        installer_version = values[INSTALLER_VERSION_PROPERTY]
    except KeyError as error:
        raise ReleaseEvidenceError(f"missing release property: {error.args[0]}") from error
    if VERSION_PATTERN.fullmatch(release_version) is None:
        raise ReleaseEvidenceError("release version must be stable semantic versioning")
    installer_match = VERSION_PATTERN.fullmatch(installer_version)
    if installer_match is None or installer_match.group(1) == "0":
        raise ReleaseEvidenceError("installer version must have a positive major component")
    return release_version, installer_version


def locate_package(repository_root: Path, target: ReleaseTarget) -> Path:
    directory = repository_root / "apps/desktop/build/compose/binaries/main" / target.package_format
    candidates = sorted(directory.glob(f"*.{target.package_format}"))
    if len(candidates) != 1:
        raise ReleaseEvidenceError(
            f"expected one {target.package_format} package, found {len(candidates)}"
        )
    return candidates[0]


def digest(file: Path) -> str:
    checksum = hashlib.sha256()
    with file.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            checksum.update(chunk)
    return checksum.hexdigest()


def create(
    repository_root: Path,
    operating_system: str,
    architecture: str,
    source_revision: str,
) -> Path:
    target = target_for(operating_system, architecture)
    if REVISION_PATTERN.fullmatch(source_revision) is None:
        raise ReleaseEvidenceError("source revision must be a lowercase 40-character Git SHA")
    release_version, installer_version = read_versions(repository_root / "gradle.properties")
    package = locate_package(repository_root, target)
    checksum = digest(package)
    manifest = {
        "schemaVersion": 1,
        "project": "Blowhard",
        "releaseVersion": release_version,
        "installerVersion": installer_version,
        "sourceRevision": source_revision,
        "target": {
            "operatingSystem": target.operating_system,
            "architecture": target.architecture,
            "format": target.package_format,
            "signing": target.signing,
        },
        "artifact": {
            "fileName": package.name,
            "sizeBytes": package.stat().st_size,
            "sha256": checksum,
        },
    }
    output = (
        repository_root
        / "apps/desktop/build/release-evidence"
        / f"{target.operating_system}-{target.architecture}"
    )
    output.mkdir(parents=True, exist_ok=True)
    manifest_file = output / "manifest.json"
    manifest_file.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "SHA256SUMS").write_text(f"{checksum}  {package.name}\n", encoding="utf-8")
    return manifest_file


def verify(repository_root: Path, manifest_file: Path) -> None:
    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    release_version, installer_version = read_versions(repository_root / "gradle.properties")
    target_data = manifest.get("target", {})
    target = target_for(target_data.get("operatingSystem"), target_data.get("architecture"))
    package = locate_package(repository_root, target)
    artifact = manifest.get("artifact", {})
    expected = {
        "schemaVersion": 1,
        "project": "Blowhard",
        "releaseVersion": release_version,
        "installerVersion": installer_version,
        "sourceRevision": manifest.get("sourceRevision"),
        "target": {
            "operatingSystem": target.operating_system,
            "architecture": target.architecture,
            "format": target.package_format,
            "signing": target.signing,
        },
        "artifact": {
            "fileName": package.name,
            "sizeBytes": package.stat().st_size,
            "sha256": digest(package),
        },
    }
    if REVISION_PATTERN.fullmatch(str(expected["sourceRevision"])) is None:
        raise ReleaseEvidenceError("manifest source revision is invalid")
    if manifest != expected:
        raise ReleaseEvidenceError("manifest does not match the packaged artifact")
    checksum_file = manifest_file.parent / "SHA256SUMS"
    expected_checksum = f"{expected['artifact']['sha256']}  {package.name}\n"
    if checksum_file.read_text(encoding="utf-8") != expected_checksum:
        raise ReleaseEvidenceError("checksum file does not match the packaged artifact")


def target_for(operating_system: object, architecture: object) -> ReleaseTarget:
    try:
        return TARGETS[(operating_system, architecture)]
    except KeyError as error:
        raise ReleaseEvidenceError(
            f"unsupported release target: {operating_system}/{architecture}"
        ) from error


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    create_parser = subparsers.add_parser("create")
    create_parser.add_argument("repository_root", type=Path)
    create_parser.add_argument("--platform", required=True)
    create_parser.add_argument("--architecture", required=True)
    create_parser.add_argument("--source-revision", required=True)
    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("repository_root", type=Path)
    verify_parser.add_argument("manifest", type=Path)
    arguments = parser.parse_args()

    if arguments.command == "create":
        manifest = create(
            arguments.repository_root.resolve(),
            arguments.platform,
            arguments.architecture,
            arguments.source_revision,
        )
        print(f"Created release evidence: {manifest}")
    else:
        verify(arguments.repository_root.resolve(), arguments.manifest.resolve())
        print(f"Verified release evidence: {arguments.manifest}")


if __name__ == "__main__":
    main()
