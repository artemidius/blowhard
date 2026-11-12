# Meeting-note evaluation methodology

Blowhard evaluates the versioned `meeting-note-v1` contract through the same
`DefaultMeetingNotes` application service used by the desktop workflow. The checked-in synthetic
dataset contains twelve small, redistributable cases: eight accepted records and four deliberate
validation failures. It covers explicit abstention, missing owner/date values, multi-page evidence,
malformed JSON, unknown fields, an invalid date, and an unsupported citation.

Run the deterministic suite with:

```shell
./gradlew :apps:evaluation:run --args="--output build/reports/evaluation"
```

The runner writes JSON and Markdown reports. Fixture execution uses a fixed local provider result,
reports zero latency by policy, and has no token usage. It is regression evidence for parsing,
validation, scoring, and orchestration—not a claim about Mistral model quality.

Live evaluation is deliberately opt-in and reads the API key only from the desktop secure store:

```shell
./gradlew :apps:evaluation:run --args="--live --model MODEL_ID --max-cases 3 --output build/reports/live-evaluation"
```

The CLI accepts at most six cases, applies the adapter's per-request timeout and a seven-minute
whole-run timeout, and uses the template's fixed 4,096-token output ceiling. It never accepts an API
key on the command line. Live reports record wall-clock latency and explicitly mark provider usage
unavailable until the structured-output adapter exposes trustworthy usage metadata.

## Scoring dimensions

- **Schema validity** records whether accepted and deliberately invalid outputs cross the strict
  structured-output boundary as expected.
- **Field correctness** compares the complete typed value with a hand-reviewed expectation.
- **Missing-field handling** compares the null shape independently, so invented owners, dates, or
  titles remain visible even when other text is correct.
- **Source resolution** checks that every exact excerpt exists on the cited zero-based page.
- **Expected outcome** verifies the typed success or rejection category.

`NOT_APPLICABLE` results are excluded from a metric denominator. A resolvable literal excerpt does
not prove semantic support or factual correctness; that requires separate human review. The small
synthetic dataset is intentionally unsuitable for broad model comparisons.

## Reproducibility metadata

The dataset, template, fixture model, runner configuration, report schema, sample count, and report
date are versioned in the generated report. The checked-in report is regenerated only when the
dataset or scoring policy changes. An opt-in live evaluation must publish its actual model ID,
configuration, latency, usage, failures, and explicit request ceilings separately; fixture results
must never be presented as live results.
