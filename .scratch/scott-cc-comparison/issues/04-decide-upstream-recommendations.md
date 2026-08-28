Type: grilling
Status: resolved
Blocked by: 01, 02

## Question

Which of the mutflow fork's upstream changes should be recommended for contribution back to upstream mutflow (anschnapp/mutflow), and why?

### Background

R1 catalogs all 12 fork branches and identifies which are relevant to Scott-CC gaps. This ticket decides which should be upstreamed to anschnapp/mutflow.

### Task

1. Review the R1 catalog of fork branches.
2. Evaluate each relevant branch for upstreamability:
   - Does it fix a real bug (e.g., arithmetic IR truncate)?
   - Does it add broadly useful functionality (e.g., exception type swap, verification modes)?
   - Does it add OMP-specific features (e.g., Gradle config targets, pipeline)?
3. Decide which to recommend for upstreaming and which to keep as fork-private.

### Acceptance criteria

- List of branches recommended for upstreaming, with rationale.
- List of branches kept fork-private, with rationale.

## Answer

**D2 resolved via grilling session.**

### Decision

**All 12 mutflow fork branches are already upstream-trackable — none are fork-private modifications.**

Key finding from the user: the `exception-swap` fork was created by forking upstream mutflow (`trancee/mutflow-exception-swap` ← `anschnapp/mutflow`), and all branches represent work that either already exists upstream (as merged PRs, open PRs, or upstream branches) or originated from upstream work. When the repo was forked, these branches came along — they are not fork-private.

### Per-branch status (from R2 §8 upstreamability assessment):

| Branch | Status | Notes |
|---|---|---|
| `feature/exception-type-swap` | ✅ Upstream PR #16 open | ExceptionTypeSwapOperator — PR already open at anschnapp/mutflow |
| `feature/zombie-detection` | ✅ Upstream-trackable | Multi-killer tracking — clean enhancement |
| `avoid-mutating-null-checks` | ✅ Upstream-trackable | Bug fix for false-positive null-check mutants |
| `double-arithmetic-ir-when-truncate-fix` | ✅ Upstream-trackable | Correctness bug fix (Double precision) |
| `introduce-verification-mode-…` | ✅ Upstream-trackable | STRICT/LENIENT/DISABLED modes |
| `add-option-to-define-mutation-targets-via-gradle-config` | ✅ Upstream-trackable | Gradle DSL target scoping |
| `optional-extra-cli-safe-guard-verification` | ✅ Upstream-trackable | JAR artifact verification script |
| `add-pipeline` | ⚠️ Check upstream PR status | Basic CI — may need expansion before upstreaming |
| `kotlin-native` | ⚠️ Experimental | Needs stabilization (46 files) before upstreaming |
| `update-versions` | ✅ Routine | Version bumps — upstream via normal release cycle |
| `hint-for-gradle-and-jooq-user` | ✅ Doc | README hint — upstream as doc PR |

### Recommendation

**No upstreaming recommendations needed** — all branches are already upstream-trackable. The recommendation is to **continue the existing upstreaming process**:
1. Verify PR #16 (exception-type-swap) merges upstream.
2. For remaining branches, check upstream PR status and continue contributing as PRs.
3. For `kotlin-native` and `add-pipeline`: stabilize before pushing upstream.
- Decision recorded as a resolution comment.

## Resolution (2026-08-28)

All 12 fork branches have been merged into upstream mutflow v1.1.1 (commit 2f262dc). PR #16 (ExceptionTypeSwapOperator) and PR #17 (multi-killer zombie detection) are live. The fork (`trancee/mutflow-exception-swap`) is retired — OMP now uses upstream mutflow v1.1.1+ directly. No fork maintenance required.
