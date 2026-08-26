Type: grilling
Status: resolved
Blocked by: 01, 02

## Question

Given the three-way comparison (Scott-CC ↔ OMP ↔ mutflow fork), which Scott-CC feature gaps should OMP prioritize closing, and in what order?

### Background

R2 will produce a feature matrix showing Scott-CC features OMP lacks. R1 will show which mutflow fork changes already bridge those gaps. This ticket decides the priority order for closing remaining gaps.

### Task

1. Review the R2 comparison matrix (Scott-CC features OMP lacks).
2. Review the R1 fork-branch catalog (which gaps are already closed by fork changes).
3. Rank remaining gaps by impact and effort.
4. Decide which to prioritize for the next sprint/iteration.

### Gaps to evaluate

- Redundant test group detection (tests that always fail together → consolidate)
- Auto-refactoring (production-ready code generation vs OMP's suggestions)
- Conversational auto-detection triggers ("mutation test my Stripe logic")
- Quality bands with confidence levels (OMP has this; Scott-CC has only bands)
- Any gap where the mutflow fork already provides a bridge (R1 findings)

### Acceptance criteria

- Ranked list of gaps to close, with rationale for each.

## Resolution


### Re-prioritization (second grilling session)

**D1 re-resolved:** User re-opened prioritization and selected 9 of 10 gaps to implement, declining only NL triggers (XL).

| Priority | Gap | Effort | Rationale |
|---|---|---|---|
| 1 | Redundant test group detection | L | High impact — catches redundant test clusters for consolidation |
| 2 | Auto-generated refactored test code | L | High impact — moves from suggestions to production-ready code |
| 3 | Auto-approve for refactoring | M | UX improvement — skips confirmation on apply |
| 4 | Quick/standard/deep mode abstraction | M | Maps --quick/--standard/--deep to mutflow maxRuns; improves UX |
| 5 | `--focus=<area>` parameter | S | Limits mutations to code area via Gradle DSL |
| 6 | Diff generation before refactoring | S | Safety — show git diff before applying |
| 7 | Confidence intervals | S | Statistical CI around mutation score |
| 8 | Execution gap reporting | S | Track ERROR/INVALID_MUTATION as coverage gaps |
| 9 | Explicit rollback instructions | trivial | Document rollback steps in report |

| Declined | NL auto-detection triggers | XL | Requires harness-level NL detection — too expensive |

| Declined | All 5 fork cherry-picks | various | User declined: arithmetic IR fix, null-check suppression, verification modes, Gradle target config, CLI safe-guard |
