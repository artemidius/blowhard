import tempfile
import unittest
from pathlib import Path

from verify_distribution import DistributionError, verify


class VerifyDistributionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        runtime = self.root / "runtime" / "bin"
        runtime.mkdir(parents=True)
        (runtime / "java").write_bytes(b"runtime")

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_accepts_self_contained_distribution(self) -> None:
        self.assertEqual((1, 7), verify(self.root, ()))

    def test_accepts_native_runtime_library_without_java_launcher(self) -> None:
        (self.root / "runtime" / "bin" / "java").unlink()
        runtime_library = self.root / "runtime" / "lib" / "server"
        runtime_library.mkdir(parents=True)
        (runtime_library / "libjvm.dylib").write_bytes(b"runtime")

        self.assertEqual((1, 7), verify(self.root, ()))

    def test_rejects_sensitive_file_type(self) -> None:
        (self.root / "release.key").write_text("not a real key")
        with self.assertRaisesRegex(DistributionError, "sensitive file type"):
            verify(self.root, ())

    def test_rejects_dynamic_build_path(self) -> None:
        (self.root / "app.jar").write_bytes(b"prefix/build/private/workspace/suffix")
        with self.assertRaisesRegex(DistributionError, "forbidden text"):
            verify(self.root, ("/build/private/workspace",))


if __name__ == "__main__":
    unittest.main()
