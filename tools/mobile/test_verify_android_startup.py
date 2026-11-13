"""Tests for bounded Android startup evidence."""

from __future__ import annotations

import unittest
from subprocess import CalledProcessError
from unittest.mock import patch

from verify_android_startup import create_evidence, parse_activity_start, run_adb


LAUNCH_OUTPUT = """\
Starting: Intent { cmp=io.github.artemidius.blowhard/io.github.artemidius.blowhard.android.MainActivity }
Status: ok
LaunchState: COLD
Activity: io.github.artemidius.blowhard/io.github.artemidius.blowhard.android.MainActivity
TotalTime: 712
WaitTime: 719
Complete
"""


class AndroidStartupEvidenceTest(unittest.TestCase):
    def test_allowlisted_cold_launch_is_machine_readable(self) -> None:
        evidence = create_evidence(
            LAUNCH_OUTPUT,
            "io.github.artemidius.blowhard",
            "io.github.artemidius.blowhard/io.github.artemidius.blowhard.android.MainActivity",
            10_000,
        )

        self.assertEqual(712, evidence["total_time_ms"])
        self.assertEqual("COLD", evidence["launch_state"])
        self.assertNotIn("Activity", evidence)

    def test_failed_or_incomplete_launch_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "status 'Error'"):
            parse_activity_start("Status: Error\nLaunchState: COLD\nTotalTime: 1\nWaitTime: 1")
        with self.assertRaisesRegex(ValueError, "omitted: WaitTime"):
            parse_activity_start("Status: ok\nLaunchState: COLD\nTotalTime: 1")

    def test_budget_regression_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "budget is 500 ms"):
            create_evidence(
                LAUNCH_OUTPUT,
                "io.github.artemidius.blowhard",
                "io.github.artemidius.blowhard/io.github.artemidius.blowhard.android.MainActivity",
                500,
            )

    def test_warm_launch_is_rejected(self) -> None:
        warm_output = LAUNCH_OUTPUT.replace("LaunchState: COLD", "LaunchState: WARM")

        with self.assertRaisesRegex(ValueError, "expected a cold launch"):
            create_evidence(
                warm_output,
                "io.github.artemidius.blowhard",
                "io.github.artemidius.blowhard/io.github.artemidius.blowhard.android.MainActivity",
                10_000,
            )

    @patch("verify_android_startup.subprocess.run")
    def test_adb_failure_includes_platform_diagnostic(self, run: unittest.mock.Mock) -> None:
        run.side_effect = CalledProcessError(
            1,
            ["adb", "shell", "am", "start"],
            output="Error type 3",
            stderr="Activity class does not exist",
        )

        with self.assertRaisesRegex(
            ValueError,
            "Error type 3[\\s\\S]*Activity class does not exist",
        ):
            run_adb("adb", "shell", "am", "start")


if __name__ == "__main__":
    unittest.main()
