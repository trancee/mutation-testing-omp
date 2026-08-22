# About the OMP 5-agent mutation testing system

The mutation testing system uses five specialized agents that run in a sequential handshake. Each agent has a single responsibility.

## Agent table

| Agent | Dispatch key | Tools | Model | Role |
|-------|-------------|-------|-------|------|
| `test-quality-reviewer` | `test-quality-reviewer` | task, hub, read, grep, glob, bash | @review | Orchestrator — coordinates the pipeline |
| `test-saboteur` | `test-saboteur` | bash, read, write, edit, grep, glob, ast_grep, lsp | @default | Mutation targeting — adds `@MutationTarget`, `@MutFlowTest`, `// mutflow:ignore` |
| `test-executor` | `test-executor` | bash, read, grep, glob | @default | Test execution — runs `./gradlew test`, captures stdout + JUnit XML + JSON |
| `test-auditor` | `test-auditor` | read, grep, glob, bash | @default | Results analysis — calculates score, finds zombies, detects over-mocked tests |
| `test-refactor-specialist` | `test-refactor-specialist` | read, edit, write, grep, glob, bash | @review | Test improvement — proposes boundary tests for surviving mutations |

## How the agents work together

The `/mutation-test` skill dispatches to the `test-quality-reviewer` orchestrator, which runs four phases in order:

1. **Saboteur phase** — the saboteur reads source files, identifies business-logic classes, and annotates them with `@MutationTarget`, annotates test classes with `@MutFlowTest`, and adds `// mutflow:ignore` to framework noise. It also wraps existing assertions in `MutFlow.underTest { }` blocks.

2. **Executor phase** — the orchestrator dispatches one `test-executor` per `@MutFlowTest` class in a parallel `task` batch. Each executor runs `./gradlew test --tests <TestClass>`. mutflow's JUnit 6 extension handles the baseline run (run 0) and one mutation per subsequent run internally.

3. **Audit phase** — the auditor parses the `mutation-results.json` output and JUnit XML, calculates the mutation score, identifies zombie test candidates, and detects over-mocked tests.

4. **Refactor phase** — the refactor specialist reviews the auditor's findings and generates improved test code for flagged issues.

## Why this order is fixed

mutflow's run model requires this ordering:

- The saboteur must annotate source before executors run. Without `@MutationTarget` and `@MutFlowTest`, mutflow has no mutation targets.
- Executors must finish before the auditor. mutflow discovers mutation points during the baseline run, then activates one mutation per subsequent run. The auditor needs all runs complete before calculating scores.
- The auditor must finish before the refactor specialist. The refactor specialist needs survivor lists and zombie candidates to know which tests to improve.

Parallel execution happens *within* each phase. Multiple executors run in one `task` batch. mutflow's global synchronized lock (`MutationRegistry.withSession()`) serializes mutation runs internally, so executors block-and-wait rather than running mutations concurrently.

## Dispatch keys

Each agent is dispatched by its `name` field in `~/.omp/agent/agents/` or `.omp/agents/`. The orchestrator calls them via the `task` tool:

```
task with agent: "test-saboteur", task: "Annotate source in <project-path>"
task with agent: "test-executor", task: "Run tests for <TestClass> in <project-path>"
task with agent: "test-auditor", task: "Audit results in <project-path>"
task with agent: "test-refactor-specialist", task: "Improve tests based on audit"
```

For the full design rationale, see [ADR-002](../adr/0002-agent-structure-and-orchestration-model.md).
