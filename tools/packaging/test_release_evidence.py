import json
import tempfile
import unittest
from pathlib import Path

from release_evidence import ReleaseEvidenceError, create, read_versions, verify


class ReleaseEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.root.joinpath("gradle.properties").write_text(
            "blowhardReleaseVersion=0.1.0\nblowhardInstallerVersion=1.0.0\n",
            encoding="utf-8",
        )
        package_directory = self.root / "apps/desktop/build/compose/binaries/main/dmg"
        package_directory.mkdir(parents=True)
        self.package = package_directory / "Blowhard-1.0.0.dmg"
        self.package.write_bytes(b"synthetic package")

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_creates_deterministic_manifest_and_checksum(self) -> None:
        manifest_file = create(self.root, "macos", "arm64", "a" * 40)
        first_content = manifest_file.read_text(encoding="utf-8")

        second_manifest = create(self.root, "macos", "arm64", "a" * 40)
        self.assertEqual(first_content, second_manifest.read_text(encoding="utf-8"))
        manifest = json.loads(first_content)
        self.assertEqual("0.1.0", manifest["releaseVersion"])
        self.assertEqual("1.0.0", manifest["installerVersion"])
        self.assertEqual("ad-hoc", manifest["target"]["signing"])
        self.assertEqual("Blowhard-1.0.0.dmg", manifest["artifact"]["fileName"])
        self.assertNotIn(str(self.root), first_content)
        verify(self.root, manifest_file)

    def test_verification_detects_package_tampering(self) -> None:
        manifest_file = create(self.root, "macos", "arm64", "b" * 40)
        self.package.write_bytes(b"changed package")

        with self.assertRaisesRegex(ReleaseEvidenceError, "does not match"):
            verify(self.root, manifest_file)

    def test_verification_detects_checksum_tampering(self) -> None:
        manifest_file = create(self.root, "macos", "arm64", "b" * 40)
        manifest_file.with_name("SHA256SUMS").write_text(
            f"{'0' * 64}  {self.package.name}\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(ReleaseEvidenceError, "checksum file"):
            verify(self.root, manifest_file)

    def test_rejects_ambiguous_packages_and_unsupported_targets(self) -> None:
        self.package.with_name("second.dmg").write_bytes(b"second")
        with self.assertRaisesRegex(ReleaseEvidenceError, "expected one dmg"):
            create(self.root, "macos", "arm64", "c" * 40)
        with self.assertRaisesRegex(ReleaseEvidenceError, "unsupported release target"):
            create(self.root, "macos", "x64", "c" * 40)

    def test_rejects_invalid_release_properties_and_revision(self) -> None:
        self.root.joinpath("gradle.properties").write_text(
            "blowhardReleaseVersion=0.1.0-SNAPSHOT\nblowhardInstallerVersion=0.1.0\n",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ReleaseEvidenceError, "stable semantic"):
            read_versions(self.root / "gradle.properties")
        with self.assertRaisesRegex(ReleaseEvidenceError, "source revision"):
            create(self.root, "macos", "arm64", "not-a-revision")


if __name__ == "__main__":
    unittest.main()
