# Design OMP agent structure for the mutation-testing system

Labels: wayfinder:task
Type: task
Status: resolved (grilling session 2026-08-22)
Blocked by: (resolved — R1 research complete)

## Question

How should the 5 Scott-CC agents (test-quality-reviewer, test-saboteur, test-executor×N, test-auditor, test-refactor-specialist) be expressed in OMP's agent/task system?

Depends on the findings from "Research OMP agent and task dispatch system." Specifically:

1. If OMP supports domain-specific subagent namespaces (like `mutation-testing:test-saboteur`), replicate Scott-CC's pattern directly.
2. If OMP only supports the built-in agent types (scout, reviewer, task, etc.), design how to express the 5 roles using the `task` tool with custom prompts — what's the dispatch mechanism?
3. How does the orchestrating agent (test-quality-reviewer) launch subagents and aggregate their results? What's the OMP equivalent of Scott-CC's single-message parallel `Task` dispatch?
4. How does the `/mutation-test` slash command (OMP skill) hand off to the orchestrating agent, and what's the equivalent of `allowed-tools: Task(...)`?
5. What tools are available to each subagent (particularly test-saboteur needing bash for git worktrees, and test-executor needing test-running tools)?

## Resolution

### Decisions (from grilling 2026-08-22)
1. **Agent granularity**: 5 separate agent files — matches Scott-CC's architecture exactly, enables per-role tool restrictions
2. **Agent location**: Project `.omp/agents/` — version-controlled, portable, follows OMP discovery precedence (project > user)
3. **Orchestration flow**: Sequential handshake — saboteur creates mutations → dispatch executors with mutation list → auditor processes results
4. **Tool permissions**: Restricted per agent — principle of least privilege (e.g., executor can't edit, saboteur can't spawn subagents)
5. **Skill entry point**: Thin skill → orchestrator agent — SKILL.md wraps test-quality-reviewer dispatch

### Agent structure design

| Agent | File | Tools | Role |
|-------|------|-------|------|
| test-quality-reviewer | `.omp/agents/test-quality-reviewer.md` | task, hub, read, grep, glob, bash | Orchestrator — dispatches subagents |
| test-saboteur | `.omp/agents/test-saboteur.md` | bash, read, write, edit, grep, glob | Mutation configuration + mutflow setup |
| test-executor | `.omp/agents/test-executor.md` | bash, read, grep, glob | Runs mutation test suite, captures output |
| test-auditor | `.omp/agents/test-auditor.md` | read, grep, glob, bash | Parses results, calculates score, zombie detection |
| test-refactor-specialist | `.omp/agents/test-refactor-specialist.md` | read, edit, write, grep, glob, bash | Generates refactored test code |

### Skill entry point
- `.omp/skills/mutation-test/SKILL.md` — thin wrapper: `/mutation-test` → spawns test-quality-reviewer via `task`

### Key implication for mutflow adaptation

- test-saboteur does NOT create per-mutation git worktrees — mutflow injects all variants at compile time
- test-saboteur configures `@MutFlowTest` annotations + mutflow Gradle plugin
- test-executor runs `./gradlew test` — JUnit 6 extension handles multi-run internally (baseline + mutation runs)
- Only ONE test-executor per test class (mutations serialized by mutflow's global lock, not by OMP parallelism)

