"""Tests for bounded Android startup evidence."""

from __future__ import annotations

import unittest

from verify_android_startup import create_evidence, parse_activity_start


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
            parse_activity_start("Status: Error\nTotalTime: 1\nWaitTime: 1")
        with self.assertRaisesRegex(ValueError, "omitted: WaitTime"):
            parse_activity_start("Status: ok\nTotalTime: 1")

    def test_budget_regression_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "budget is 500 ms"):
            create_evidence(
                LAUNCH_OUTPUT,
                "io.github.artemidius.blowhard",
                "io.github.artemidius.blowhard/io.github.artemidius.blowhard.android.MainActivity",
                500,
            )


if __name__ == "__main__":
    unittest.main()
