import copy
import unittest

from verify_license_inventory import REQUIRED_RUNTIME_MODULES, validate_inventory


def dependency(name: str, license_name: str = "Apache-2.0") -> dict:
    return {
        "moduleName": name,
        "moduleVersion": "1.0.0",
        "moduleUrls": [],
        "moduleLicenses": [{"moduleLicense": license_name}],
    }


class LicenseInventoryTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = {
            "allowedLicenses": [
                {"moduleLicense": "Apache-2.0"},
                {"moduleLicense": "MIT"},
            ]
        }
        self.inventory = {
            "dependencies": [dependency(name) for name in sorted(REQUIRED_RUNTIME_MODULES)]
        }

    def test_accepts_complete_sorted_inventory(self) -> None:
        self.assertEqual([], validate_inventory(self.inventory, self.policy))

    def test_rejects_empty_inventory(self) -> None:
        self.assertIn(
            "inventory must contain a non-empty dependencies array",
            validate_inventory({"dependencies": []}, self.policy),
        )

    def test_rejects_internal_module(self) -> None:
        inventory = copy.deepcopy(self.inventory)
        inventory["dependencies"].append(dependency("Blowhard.core:domain"))
        errors = validate_inventory(inventory, self.policy)
        self.assertTrue(any("internal project module" in error for error in errors))

    def test_rejects_unknown_license(self) -> None:
        inventory = copy.deepcopy(self.inventory)
        inventory["dependencies"][0]["moduleLicenses"] = []
        errors = validate_inventory(inventory, self.policy)
        self.assertTrue(any("no identified license" in error for error in errors))

    def test_rejects_disallowed_license(self) -> None:
        inventory = copy.deepcopy(self.inventory)
        inventory["dependencies"][0]["moduleLicenses"] = [
            {"moduleLicense": "GPL-3.0-only"}
        ]
        errors = validate_inventory(inventory, self.policy)
        self.assertTrue(any("no allowed license" in error for error in errors))

    def test_rejects_missing_required_runtime_module(self) -> None:
        inventory = copy.deepcopy(self.inventory)
        inventory["dependencies"].pop()
        errors = validate_inventory(inventory, self.policy)
        self.assertTrue(any("required runtime modules are missing" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
