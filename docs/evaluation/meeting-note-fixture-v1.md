# Meeting-note fixture evaluation

- Dataset: `meeting-note-synthetic-v1` (12 synthetic cases)
- Template: `meeting-note-v1`
- Model: `fixture-meeting-note-v1`
- Execution: fixture; latency is deterministic zero and usage is unavailable
- Date: 2026-11-11

| Metric | Passed | Failed | Applicable |
|---|---:|---:|---:|
| Schema validity | 12 | 0 | 12 |
| Field correctness | 8 | 0 | 8 |
| Missing-field handling | 8 | 0 | 8 |
| Source resolution | 8 | 0 | 8 |
| Expected outcome | 12 | 0 | 12 |

Source resolution verifies literal page evidence only; it is not a factuality score. These are
deterministic fixture results, not live provider results.
