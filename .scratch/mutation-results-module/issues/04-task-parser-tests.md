---
Type: task
Status: resolved
Blocked by: 03
---

## Question

What are the unit tests for the typed parser, and what sample mutflow stdout cases do they cover?

The current parser has zero tests — the multi-killer regex logic (collecting ALL consecutive "killed by:" lines, lines 196–208) is untested and was written by hand after the fork changes. A regression would silently break zombie detection.

### Required tests

1. **Multi-killer parsing**: A mutation with 2+ "killed by:" lines → `killedByTests` has all entries
2. **Survived mutation**: Status icon `✗` → `Survived`, `killedByTests` empty, `killedByTest` null
3. **Timed out mutation**: Status icon `⏱` → `TimedOut`, `killedByTests` empty
4. **Empty stdout**: No mutations, zero total, score 0.0, band "Poor"
5. **Mixed results**: A realistic sample with killed (2 killers), survived, and timed-out mutations on one summary screen
6. **Malformed lines**: Lines that don't match the mutation pattern are skipped without error

Each test feeds raw mutflow summary text to the pure `parseMutflowSummary` function and asserts on the typed result. Sample stdout can be constructed from the format documented in the parser's KDoc (lines 154–167).

## Answer

**Resolved**: 17 tests written and passing across three test files in `.omp/mutation-results-src/test/kotlin/io/omp/mutation/`:

- `MutationResultsParserTest` (6 tests): multi-killer, survived, timed-out, empty stdout, mixed results, malformed lines, unicode box-drawing character stripping
- `MutationStatsTest` (6 tests): all-killed (Excellent/Low), all-survived (Poor/Low), medium count (Excellent/Medium), 50+ count (Excellent/High), empty list (Poor/Low), timed-out separation (Fair/Medium)
- `MutationResultsSerializerTest` (4 tests): killed mutation JSON fields, survived null killer fields, testKillerMatrix mapping, quality band serialization

Run via `gradle :buildSrc:test` in the sample project — 17 tests, 0 failures.

## Assets
