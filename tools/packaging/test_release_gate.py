import json
import shutil
import tempfile
import unittest
from pathlib import Path

from release_evidence import TARGETS, create, digest
from release_gate import (
    ReleaseGateError,
    prepare_documentation_assets,
    prepare_native_assets,
    validate_project_license,
    validate_source,
    verify_release_assets,
)
from verify_license_inventory import REQUIRED_RUNTIME_MODULES


class ReleaseGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.root.joinpath("gradle.properties").write_text(
            "blowhardReleaseVersion=0.1.0\nblowhardInstallerVersion=1.0.0\n",
            encoding="utf-8",
        )
        repository_license = Path(__file__).resolve().parents[2] / "LICENSE"
        shutil.copyfile(repository_license, self.root / "LICENSE")
        notes = self.root / "docs/releases/v0.1.0.md"
        notes.parent.mkdir(parents=True)
        notes.write_text("# Release\n\n" + "Reviewed release notes. " * 10, encoding="utf-8")
        kdoc = self.root / "build/dokka/html"
        kdoc.mkdir(parents=True)
        kdoc.joinpath("index.html").write_text("<html>API</html>\n", encoding="utf-8")
        self.policy = self.root / "config/allowed-runtime-licenses.json"
        self.policy.parent.mkdir(parents=True)
        self.policy.write_text(
            json.dumps({"allowedLicenses": [{"moduleLicense": "MIT"}]}) + "\n",
            encoding="utf-8",
        )
        self.revision = "a" * 40

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def _report(self, slug: str) -> Path:
        report = self.root / "reports" / slug
        report.mkdir(parents=True)
        dependencies = [
            {
                "moduleName": name,
                "moduleVersion": "1.0.0",
                "moduleUrls": [],
                "moduleLicenses": [{"moduleLicense": "MIT"}],
            }
            for name in sorted(REQUIRED_RUNTIME_MODULES)
        ]
        report.joinpath("dependencies.json").write_text(
            json.dumps({"dependencies": dependencies, "importedModules": []}, indent=2) + "\n",
            encoding="utf-8",
        )
        report.joinpath("THIRD_PARTY_LICENSES.md").write_text(
            "# Synthetic third-party licenses\n",
            encoding="utf-8",
        )
        return report

    def _package(self, operating_system: str, architecture: str) -> Path:
        target = TARGETS[(operating_system, architecture)]
        names = {
            "dmg": "Blowhard-1.0.0.dmg",
            "msi": "Blowhard-1.0.0.msi",
            "deb": "blowhard_1.0.0_amd64.deb",
        }
        package = (
            self.root
            / "apps/desktop/build/compose/binaries/main"
            / target.package_format
            / names[target.package_format]
        )
        package.parent.mkdir(parents=True)
        package.write_bytes(f"{operating_system}-{architecture}".encode())
        return package

    def _complete_assets(self) -> Path:
        merged = self.root / "merged"
        merged.mkdir()
        for operating_system, architecture in TARGETS:
            self._package(operating_system, architecture)
            manifest = create(self.root, operating_system, architecture, self.revision)
            output = self.root / "staged" / f"{operating_system}-{architecture}"
            assets = prepare_native_assets(
                self.root,
                manifest,
                self._report(f"{operating_system}-{architecture}"),
                output,
            )
            for asset in assets:
                shutil.copyfile(asset, merged / asset.name)

        docs_output = self.root / "staged/docs"
        for asset in prepare_documentation_assets(self.root, docs_output):
            shutil.copyfile(asset, merged / asset.name)
        return merged

    def test_validates_main_source_tag_license_and_notes(self) -> None:
        notes = validate_source(self.root, "v0.1.0", self.revision, "refs/heads/main")
        self.assertEqual(self.root / "docs/releases/v0.1.0.md", notes)

    def test_rejects_wrong_tag_ref_and_license(self) -> None:
        with self.assertRaisesRegex(ReleaseGateError, "tag must be"):
            validate_source(self.root, "v1.0.0", self.revision, "refs/heads/main")
        with self.assertRaisesRegex(ReleaseGateError, "dispatched from main"):
            validate_source(self.root, "v0.1.0", self.revision, "refs/heads/feature")
        self.root.joinpath("LICENSE").write_text("not a license\n", encoding="utf-8")
        with self.assertRaisesRegex(ReleaseGateError, "reviewed Blowhard MIT"):
            validate_project_license(self.root)

    def test_native_license_archive_is_deterministic(self) -> None:
        self._package("macos", "arm64")
        manifest = create(self.root, "macos", "arm64", self.revision)
        report = self._report("macos-arm64")
        first = prepare_native_assets(self.root, manifest, report, self.root / "first")[-1]
        second = prepare_native_assets(self.root, manifest, report, self.root / "second")[-1]
        self.assertEqual(digest(first), digest(second))

    def test_verifies_complete_three_platform_asset_set(self) -> None:
        verify_release_assets(self.root, self._complete_assets(), self.revision)

    def test_detects_package_tampering_after_cross_runner_merge(self) -> None:
        assets = self._complete_assets()
        package = assets / "Blowhard-1.0.0.msi"
        package.write_bytes(package.read_bytes() + b"tampered")
        with self.assertRaisesRegex(ReleaseGateError, "manifest or package mismatch"):
            verify_release_assets(self.root, assets, self.revision)

    def test_rejects_incomplete_target_matrix(self) -> None:
        assets = self._complete_assets()
        next(assets.glob("Blowhard-0.1.0-linux-*.manifest.json")).unlink()
        with self.assertRaisesRegex(ReleaseGateError, "expected 3 native manifests"):
            verify_release_assets(self.root, assets, self.revision)

    def test_rejects_unsafe_package_filename(self) -> None:
        assets = self._complete_assets()
        manifest_file = next(assets.glob("Blowhard-0.1.0-macos-*.manifest.json"))
        manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
        manifest["artifact"]["fileName"] = "../Blowhard-1.0.0.dmg"
        manifest_file.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        with self.assertRaisesRegex(ReleaseGateError, "unsafe package filename"):
            verify_release_assets(self.root, assets, self.revision)

    def test_rejects_corrupt_documentation_archive(self) -> None:
        assets = self._complete_assets()
        assets.joinpath("Blowhard-0.1.0-kdoc.zip").write_bytes(b"not a ZIP archive")
        with self.assertRaisesRegex(ReleaseGateError, "invalid ZIP archive"):
            verify_release_assets(self.root, assets, self.revision)


if __name__ == "__main__":
    unittest.main()
