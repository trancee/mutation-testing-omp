# Diataxis Audit: Existing Documentation

**Date**: 2026-08-26  
**Scope**: All documents under `docs/` plus top-level `README.md` and `CONTEXT.md`  
**Framework**: [Diataxis](https://diataxis.fr) — four documentation types serving four user needs

## Compass Summary

| Type | Orientation | Serves | User is... |
|------|-------------|--------|------------|
| Tutorial | Action | Acquisition of skill | Studying |
| How-to guide | Action | Application of skill | Working |
| Reference | Cognition | Information lookup | Looking up |
| Explanation | Cognition | Deeper understanding | Studying |

## Audit of Each Document

### docs/index.md — Documentation Index
**Classification**: Meta-index/landing page (hub) — _not one of the four types, but a legitimate navigation aid_
**Status**: PASS — Correctly groups by user goal (Getting started, Working with results, Architecture, Domain) and links to the right type for each item. Links carry type prefixes ("Tutorial:", "How-to:", "Reference:", "Explanation:") so users can self-select.
**Issues**: None.

### docs/tutorials/first-mutation-test.md — Tutorial
**Classification**: Tutorial (Action + Acquisition of skill) ✓
**Status**: PASS (minor)
**Checklist highlights**:
- Uses "we" language, concrete steps, expected output shown ✓
- Shows destination up front: "By the end we'll have 100% mutation coverage" ✓
- Step 6 explanation of boundary values is slightly more than one sentence — tighten to one sentence + link to `fix-surviving-mutations.md`
**Issues**: Minor — Step 6 inline explanation exceeds the "minimal explanation" guideline. Could be reduced to one sentence; the full reasoning is already covered in the how-to guides.

### docs/tutorials/bootstrap-existing-project.md — Tutorial
**Classification**: Tutorial (Action + Acquisition of skill) ✓
**Status**: PASS (minor)
**Checklist highlights**:
- Uses "we" language, expected output, concrete steps ✓
- `@MutationTarget` description includes reference-like detail ("comparisons, arithmetic, boolean logic, return values") — exceeds one-sentence minimal explanation
- `MutFlow.underTest` explanation is two sentences — could be one sentence + link to `explanation/mutflow-architecture.md`
**Issues**: Minor — tighten inline explanations to one sentence each, link to explanation docs.

### docs/how-to/interpret-results.md — How-to Guide
**Classification**: How-to guide (Action + Application of skill) ✓
**Status**: PASS (needs fix)
**Checklist highlights**:
- Addresses a real-world goal: "interpret and act on mutation testing results" ✓
- Assumes competence (prerequisites listed) ✓
- Provides executable, actionable steps ✓
- Steps have logical flow ✓
**Issues**: **DUPLICATION** — The "Quality bands" table (lines 36-42) and "Confidence levels" table (lines 101-110) duplicate content that already exists in `docs/reference/mutation-results-format.md` (lines 124-136 and 133-136). Per the how-to rules ("It does NOT list every possible option — links to reference instead"), these should be replaced with links: `[Quality bands](reference/mutation-results-format.md#quality-bands)` and `[Confidence levels](reference/mutation-results-format.md#confidence-levels)`.

### docs/how-to/fix-surviving-mutations.md — How-to Guide
**Classification**: How-to guide (Action + Application of skill) ✓
**Status**: PASS (minor)
**Checklist highlights**:
- Addresses a real-world goal: "fix surviving mutations" ✓
- Assumes competence (prerequisites) ✓
- Executable instructions ✓
- References `interpret-results.md` without duplicating its content ✓
**Issues**: Minor — The "Common patterns for each operator type" section (lines 62-83) borders on reference. It lists patterns by operator type, which is reference-like. However, it's actionable (e.g., "Test the exact boundary value") rather than an exhaustive option list, so it stays in scope for a how-to. If more operator types are added later, consider extracting to a reference doc. For now, acceptable.

### docs/how-to/manual-setup.md — How-to Guide
**Classification**: How-to guide (Action + Application of skill) ✓
**Status**: PASS
**Checklist highlights**:
- Addresses a real-world goal: "set up mutation testing manually" ✓
- Assumes competence ✓
- Executable, step-by-step instructions ✓
- Handles real-world complexity (fork note, buildSrc setup) ✓
- Cross-references bootstrap tutorial instead of re-teaching ✓
**Issues**: None. Exemplary how-to.

### docs/explanation/mutflow-architecture.md — Explanation
**Classification**: Explanation (Cognition + Acquisition of skill) ✓
**Status**: PASS
**Checklist highlights**:
- Title works with "About..." prefix ✓
- Provides context, background, "why" ✓
- Compares traditional approach vs. mutflow approach ✓
- Considers multiple perspectives ✓
- Bounded — stays focused on the compile-once meta-mutant architecture ✓
- Does NOT include step-by-step instructions ✓
**Issues**: None. The diagram and "Key implications" section are explanatory, not reference.

### docs/explanation/agent-system.md — Explanation
**Classification**: Explanation (Cognition + Acquisition of skill) ✓
**Status**: PASS
**Checklist highlights**:
- Title works with "About..." prefix ✓
- Provides context about how the 5 agents work together ✓
- Explains WHY the ordering is fixed ✓
- Bounded — focused on agent roles and orchestration ✓
**Issues**: The dispatch key examples at the end are illustrative, not procedural. Acceptable within explanation. The agent capability table (agents, tools, models) is factual but serves the explanation of system structure rather than standing as standalone reference. Acceptable.

### docs/reference/mutation-results-format.md — Reference
**Classification**: Reference (Cognition + Application of skill) ✓
**Status**: PASS — Exemplary.
**Checklist highlights**:
- Describes the machinery (JSON schema) — nothing more ✓
- Austere, neutral, factual ✓
- Structure mirrors the JSON output ✓
- Table format is consistent ✓
- Includes examples to illustrate, not to teach ✓
- No opinions, no "how-to" steps, no "why" explanations ✓
**Issues**: None.

### CONTEXT.md — Domain Context
**Classification**: MIXED (Reference + Explanation)
**Status**: WARNING — mixed document
**Issues**:
- Contains reference-style tables (Key concepts glossary, Agent architecture table, Mutation strategies mapping) alongside explanation-style text (Overview, Data contracts with rationale).
- Per Diataxis: "If a single document mixes types, it needs to be split."
- However, `CONTEXT.md` is a recognized OMP project convention (one `CONTEXT.md` at repo root per `docs/agents/domain.md`).
- The glossary/concept definitions could be moved to a reference doc; the rationale sections could link to existing explanation docs.
- **Risk**: This is intentionally a lightweight domain overview for AI agents, not primarily for end users. The mixing may be acceptable for its purpose.

### README.md — Project Landing Page
**Classification**: Meta-landing/intro page (hub) — _not one of the four types, but a standard README_
**Status**: PASS
**Issues**: The "Quick start" code block has executable commands. This is standard README convention. The README links to the documentation index rather than providing full tutorials. Acceptable.

### ADRs (docs/adr/*.md)
**Classification**: ADRs = Explanation (design rationale) — closest Diataxis type
**Status**: PASS
**Issues**: None. ADRs document decisions, context, and rationale — all explanation concerns. They don't masquerade as other types.

## Boundary Violations Summary

| Violation | Severity | File | Fix | Status |
|-----------|----------|------|-----|--------|
| Reference content duplicated in how-to (quality bands + confidence tables) | P2 (fix recommended) | `docs/how-to/interpret-results.md` | Replaced inline tables with links to `reference/mutation-results-format.md` anchors | FIXED |
| Inline explanation slightly too verbose in tutorial | P3 (minor) | `docs/tutorials/first-mutation-test.md` (Step 6) | Tightened to one sentence + link to `fix-surviving-mutations.md` | FIXED |
| Inline explanation slightly too verbose in tutorial | P3 (minor) | `docs/tutorials/bootstrap-existing-project.md` | Tightened `@MutationTarget` and `MutFlow.underTest` to one sentence each + links | FIXED |
| "Common patterns" section edges toward reference | P3 (acceptable) | `docs/how-to/fix-surviving-mutations.md` | Keep — actionable, not exhaustive | NO CHANGE (acceptable) |
| Mixed reference + explanation | P3 (convention exception) | `CONTEXT.md` | Document as intentional domain overview for AI agents; don't split unless user-facing docs expand | NO CHANGE (intentional convention) |
| Dead link to non-existent doc | P2 (bonus) | `scripts/check-markdown.sh` | Created `docs/how-to/run-checks.md`, resolving the dead link | FIXED |

## Missing Types

Before this audit, the `docs/` structure had all four types represented but lacked a **contribution guide** — users who want to write new documentation had no single source for classification rules, naming conventions, and quality checklists.

## New Documentation Created

| Document | Type | Purpose |
|----------|------|---------|
| `docs/how-to/contribute-documentation.md` | How-to guide | Walks contributors through the Diataxi classification compass, folder conventions, type-specific writing rules, cross-reference patterns, and quality checklist |
| `docs/how-to/run-checks.md` | How-to guide | Documents how to install Node.js + lychee and run `check-markdown.sh` for style + link validation |

