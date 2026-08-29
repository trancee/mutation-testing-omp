# Comparison of Scott-CC mutation-testing plugin vs OMP implementation vs mutflow fork upstream changes

Labels: wayfinder:map

## Destination

**A three-way feature gap analysis (Scott-CC ↔ OMP ↔ mutflow fork) producing a comparison matrix and decisions on which gaps OMP should close and which mutflow fork changes to recommend for upstreaming.**

Reaches from here when: a side-by-side matrix documents every difference across engine, isolation, strategies, quality analysis, refactoring, interface, and safety; the mutflow fork's 12 branches are mapped to Scott-CC feature gaps; and decisions are made on which gaps to prioritize for OMP porting and which fork changes to upstream.

## Notes

**Comparison framework** (see `domain-model.md` for details):

| Dimension | Scott-CC | OMP | mutflow fork |
|---|---|---|---|
| Engine | LLM-guided semantic mutations | mutflow compiler plugin (compile-once) | mutflow + 12 upstream branches |
| Isolation | Git worktree per mutation | Compile-once meta-mutant (IR branches) | Same as OMP |
| Parallelization | 15 parallel executor agents | Serialized (global lock) | Same as OMP |
| Strategies | 5 (boundary, return, boolean, arithmetic, exception) | 4 via mutflow ops + exception via fork | Additional operators/features |
| Quality analysis | Score, zombies, redundant groups, over-mocked, missing coverage | Score, zombie candidates, over-mocked, bands + confidence | Enhanced detection |
| Refactoring | Auto-generated code | Suggestions only | N/A |
| Interface | Slash command + NL triggers, Beads | /mutation-test skill + setup | CI pipeline |

**Relevant mutflow fork branches** (of 12 total): `exception-type-swap`, `zombie-detection`, `introduce-verification-mode-strict-lenient-and-disabled`, `introduce-optional-junit-config-flag-skip-cause`, `add-option-to-define-mutation-targets-via-gradle-config`, `add-pipeline`, `avoid-mutating-null-checks`, `double-arithmetic-ir-when-truncate-fix`, `optional-extra-cli-safe-guard-verification`

**Skills to consult:** research (fact-finding on fork branches + producing comparison matrix), grilling (prioritization + upstream decisions)

**Issue tracker:** local markdown — `.scratch/scott-cc-comparison/`

**Prior wayfinder maps:** `.scratch/omp-mutation-testing/` (all 6 tickets resolved — port is complete), `.scratch/mutation-results-module/` (all 5 tickets resolved — typed module complete). This effort builds on those decisions; it identifies NEW gaps and reconciliation points.

## Decisions so far

- [Research R1: mutflow fork catalog](issues/01-research-mutflow-fork-branches.md): R1 resolved as redundant — R2 section 8 already cataloged all 12 fork branches with descriptions, gap mappings, and upstreamability assessments.
- [Research R2: comparison matrix](research/02-comparison-matrix.md): Full 51-row matrix across 8 sections. Scott-CC lacks in OMP: redundant test detection, auto-refactoring, NL triggers, mode abstraction, `--focus`, diff/rollback, confidence intervals, gap reporting. OMP lacks in Scott-CC: confidence levels, typed JSON module (18 tests), verification modes, CLI safe-guard, setup subcommand. 4 fork branches bridge gaps. Parallelization gap is inherent to mutflow architecture.
- [D1: Prioritize Scott-CC gaps](issues/03-decide-prioritize-gaps.md): **Re-resolved.** 9 of 10 gaps selected for implementation (L: redundant test detection, auto-refactoring; M: auto-approve, mode abstraction; S: --focus, diff generation, confidence intervals, gap reporting; trivial: rollback instructions). NL triggers declined (XL). All 5 fork cherry-picks declined.
- [D2: Upstream recommendations](issues/04-decide-upstream-recommendations.md): **All 12 fork branches now merged upstream** — all branches are upstream-trackable; none are fork-private modifications (fork was created from upstream; branches came along). exception-type-swap (PR #16) and zombie-detection (PR #17) are merged in upstream v1.1.1. The fork (`trancee/mutflow-exception-swap`) is retired — OMP now uses upstream mutflow v1.1.1+ directly.

## Not yet specified

(none — destination reached)

## Out of scope

- Python/JS/TS mutation testing — OMP is Kotlin/JVM-first by ADR-001.
- Beads integration — OMP uses its own task tool and Gradle, not Beads.
- Building a new mutation engine — OMP leverages mutflow by ADR-001.
- Kotlin Native support (mutflow fork `kotlin-native` branch) — not a Scott-CC feature gap; Scott-CC doesn't do Kotlin at all.

## Status: implementation planning pending

All 4 wayfinder tickets resolved. Destination (comparison + decisions) reached. User has now selected 9 of 10 Scott-CC→OMP gaps for implementation — this is a new implementation effort beyond the original destination. A new wayfinder map is recommended to plan the 9-feature implementation.


## Resolution: fork features now upstream (2026-08-28)

mutflow v1.1.1 includes all 12 fork branches merged upstream — ExceptionTypeSwapOperator (PR #16), multi-killer zombie detection (PR #17), VerificationMode, includeTargets/excludeTargets, EqualitySwapOperator null-check suppression, ArithmeticOperator IR truncate fix, CLI safe-guard, and more. The fork (`trancee/mutflow-exception-swap`) is no longer needed. All "via fork" references in this analysis are historical; use upstream mutflow v1.1.1+ instead.
