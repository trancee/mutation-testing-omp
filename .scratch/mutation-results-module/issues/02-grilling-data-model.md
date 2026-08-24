---
Type: grilling
Status: resolved
Blocked by: 01
---

## Question

What is the exact typed data model for the mutation-results module, and how do we guarantee kotlinx.serialization output matches the current string-template JSON byte-for-byte?

### Background

The current JSON output (`mutation-results.gradle.kts` lines 130–144) uses string templates with no escaping. The output format is consumed by `test-auditor.md` and documented in `docs/reference/mutation-results-format.md`. The typed model must produce the same keys, nesting, and types.

### Specific decisions:

1. **MutationResult** — a sealed interface with three subtypes:
   - `Killed(sourceLocation, originalOperator, variantOperator, killedByTests: List<String>)`
   - `Survived(sourceLocation, originalOperator, variantOperator)`
   - `TimedOut(sourceLocation, originalOperator, variantOperator)`
   - Each subtype must serialize to the same flat JSON object: `{"sourceLocation":"...","originalOperator":"...","variantOperator":"...","result":"Killed","killedByTest":"...","killedByTests":["..."]}`

2. **Dual killer fields**: `killedByTest` (single first killer, nullable) AND `killedByTests` (all killers, array) — both fields must appear on every mutation's JSON, not just Killed ones.

3. **MutationResults (top level)**: `generatedAt`, `mutationScore`, `qualityBand`, `confidence`, `totalMutations`, `killed`, `survived`, `timedOut`, `testMethods`, `testKillerMatrix`, `mutations`.

4. **Quality bands and confidence**: Excellent >80%, Good >60%, Fair >30%, Poor ≤30%; Low <10, Medium 10-50, High 50+.

## Answer

**Decision**: Use flat `@Serializable` data classes (not sealed interfaces) to match the exact JSON shape produced by the current string templates. See [issue 02](issues/02-grilling-data-model.md) for the full rationale and data model.

## Assets
