#!/usr/bin/env python3
"""Reject sensitive inputs and machine-specific paths in a packaged app image."""

from __future__ import annotations

import argparse
from pathlib import Path

FORBIDDEN_NAMES = {".env", ".git", "implementation-plan.md"}
FORBIDDEN_SUFFIXES = {
    ".jks",
    ".key",
    ".keystore",
    ".mobileprovision",
    ".p12",
    ".pem",
    ".pfx",
    ".provisionprofile",
}
FORBIDDEN_TEXT = ("Blowhard-internal", "implementation-plan.md")
CHUNK_SIZE = 1024 * 1024
RUNTIME_MARKERS = {
    "java",
    "java.exe",
    "jvm.dll",
    "libjli.dylib",
    "libjvm.dylib",
    "libjvm.so",
}


class DistributionError(RuntimeError):
    """Raised when a packaged distribution violates an inspection rule."""


def _contains(file: Path, needles: tuple[bytes, ...]) -> bytes | None:
    if not needles:
        return None
    overlap = max(map(len, needles)) - 1
    previous = b""
    with file.open("rb") as source:
        while chunk := source.read(CHUNK_SIZE):
            content = previous + chunk
            for needle in needles:
                if needle in content:
                    return needle
            previous = content[-overlap:] if overlap > 0 else b""
    return None


def verify(root: Path, forbidden_text: tuple[str, ...]) -> tuple[int, int]:
    root = root.resolve(strict=True)
    if not root.is_dir():
        raise DistributionError(f"distribution root is not a directory: {root}")

    needles = tuple(text.encode() for text in (*FORBIDDEN_TEXT, *forbidden_text) if text)
    file_count = 0
    byte_count = 0
    bundled_java = False

    for entry in root.rglob("*"):
        relative = entry.relative_to(root)
        lower_parts = tuple(part.lower() for part in relative.parts)
        if any(part in FORBIDDEN_NAMES or part.startswith(".env.") for part in lower_parts):
            raise DistributionError(f"forbidden packaged path: {relative}")
        if entry.suffix.lower() in FORBIDDEN_SUFFIXES:
            raise DistributionError(f"sensitive file type in package: {relative}")
        if entry.is_symlink():
            try:
                entry.resolve(strict=True).relative_to(root)
            except (FileNotFoundError, ValueError) as error:
                raise DistributionError(f"external or broken symlink: {relative}") from error
            continue
        if not entry.is_file():
            continue

        file_count += 1
        byte_count += entry.stat().st_size
        bundled_java |= entry.name.lower() in RUNTIME_MARKERS and "runtime" in lower_parts
        matched = _contains(entry, needles)
        if matched is not None:
            raise DistributionError(
                f"forbidden text {matched.decode(errors='replace')!r} in packaged file: {relative}"
            )

    if file_count == 0:
        raise DistributionError("distribution is empty")
    if not bundled_java:
        raise DistributionError("bundled Java runtime was not found")
    return file_count, byte_count


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--forbid-text", action="append", default=[])
    arguments = parser.parse_args()
    files, bytes_scanned = verify(arguments.root, tuple(arguments.forbid_text))
    print(f"Verified {files} packaged files ({bytes_scanned} bytes): {arguments.root}")


if __name__ == "__main__":
    main()
