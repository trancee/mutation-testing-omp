Type: research
Status: resolved
Blocked by: (none)

## Question

What are the 12 upstream changes in the `exception-swap` mutflow fork (trancee/mutflow-exception-swap), and which of them are relevant to bridging Scott-CC feature gaps in OMP?

### Background

The mutflow fork at `trancee/mutflow-exception-swap` has 12 branches:

```
exception-type-swap
feature/exception-type-swap
feature/zombie-detection
introduce-verification-mode-strict-lenient-and-disabled
introduce-optional-junit-config-flag-skip-cause-not-all-cases-covered
add-option-to-define-mutation-targets-via-gradle-config
add-pipeline
avoid-mutating-null-checks
double-arithmetic-ir-when-truncate-fix
hint-for-gradle-and-jooq-user
kotlin-native
optional-extra-cli-safe-guard-verification
update-versions
```

### Task

1. Clone the fork and examine each branch (diff against master, commit history, commit messages).
2. For each branch, document: what feature/change it adds and a brief technical description.
3. Filter to branches **relevant to Scott-CC feature gaps**.
4. For each relevant branch, map it to the Scott-CC gap it closes.

### Acceptance criteria

- Complete catalog of all 12 branches with descriptions.
- Filtered subset of Scott-CC-relevant branches with gap-mapping.
- Findings captured in this issue's resolution.

---

## Answer

**R1 closed as redundant.** R2's comparison matrix (ResearchComparisonMatrix) already cataloged all 12 mutflow fork branches in [section 8: Mutflow Fork Branches That Bridge Gaps](research/02-comparison-matrix.md#8), including:

- Branch name, commit hash, files changed, what each adds, Scott-CC gap bridged, and upstreamability assessment
- Full fork chain: `master → feature/exception-type-swap → feature/zombie-detection → avoid-mutating-null-checks → double-arithmetic-ir-when-truncate-fix → optional-extra-cli-safe-guard-verification → introduce-verification-mode-… → add-option-to-define-mutation-targets-via-gradle-config → add-pipeline → update-versions`
- Relevant branches identified: `exception-type-swap` (Strategy 5: Exception types), `zombie-detection` (multi-killer tracking), `double-arithmetic-ir-when-truncate-fix` (arithmetic correctness), `avoid-mutating-null-checks` (noise reduction), `introduce-verification-mode` (safety), `add-option-to-define-mutation-targets-via-gradle-config` (interface scoping), `optional-extra-cli-safe-guard-verification` (CLI guard)

This fully satisfies R1's acceptance criteria. No separate subagent run needed.
