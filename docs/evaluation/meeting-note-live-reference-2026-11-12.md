# Meeting-note live evaluation

- Dataset: `meeting-note-synthetic-v1` (1 synthetic cases)
- Template: `meeting-note-v1`
- Model: `mistral-small-latest`
- Execution: live; latency policy: wall-clock-per-case
- Usage: unavailable from current structured-output adapter
- Date: 2026-11-12

| Metric | Passed | Failed | Applicable |
|---|---:|---:|---:|
| Schema validity | 1 | 0 | 1 |
| Field correctness | 0 | 1 | 1 |
| Missing-field handling | 0 | 1 | 1 |
| Source resolution | 1 | 0 | 1 |
| Expected outcome | 1 | 0 | 1 |

Source resolution verifies literal page evidence only; it is not a factuality score.
not reviewed; machine scores are not a factuality judgment
