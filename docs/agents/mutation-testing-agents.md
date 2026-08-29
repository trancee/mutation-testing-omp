# Mutation-testing agent reference

This reference lists the project-scoped agents that implement the mutation-testing pipeline. Agent names are their `task` dispatch keys.

## Agent definitions

| Agent | Definition | Model | Thinking level | Declared tools |
|-------|------------|-------|----------------|----------------|
| `test-quality-reviewer` | [`.omp/agents/test-quality-reviewer.md`](../../.omp/agents/test-quality-reviewer.md) | `@review` | High | `task`, `hub`, `read`, `grep`, `glob`, `bash` |
| `test-saboteur` | [`.omp/agents/test-saboteur.md`](../../.omp/agents/test-saboteur.md) | `@default` | High | `bash`, `read`, `write`, `edit`, `grep`, `glob`, `ast_grep`, `lsp` |
| `test-executor` | [`.omp/agents/test-executor.md`](../../.omp/agents/test-executor.md) | `@default` | Medium | `bash`, `read`, `grep`, `glob` |
| `test-auditor` | [`.omp/agents/test-auditor.md`](../../.omp/agents/test-auditor.md) | `@default` | High | `read`, `grep`, `glob`, `bash` |
| `test-refactor-specialist` | [`.omp/agents/test-refactor-specialist.md`](../../.omp/agents/test-refactor-specialist.md) | `@review` | High | Not specified in frontmatter |

## Agent contracts

| Agent | Input | Output | May change project files |
|-------|-------|--------|--------------------------|
| `test-quality-reviewer` | Project path, target filters, mode, approval setting | Combined mutation-quality report | Through spawned agents |
| `test-saboteur` | Kotlin production and test sources | Mutflow configuration, annotations, and wrapped test calls | Yes |
| `test-executor` | Project path and an annotated test class | Gradle status, stdout, JUnit XML, and mutation-results path | No |
| `test-auditor` | Executor results, structured mutation results, and source | Scores, confidence, gaps, survivors, zombie candidates, and redundant groups | No |
| `test-refactor-specialist` | Audit report and original tests | Proposed or approved test refactors, diffs, and rollback instructions | Only when approval permits |

## Spawn permissions

`test-quality-reviewer` declares these agents in its `spawns` list:

- `test-saboteur`
- `test-executor`
- `test-auditor`
- `test-refactor-specialist`

The other four agents do not declare child agents.

## Dispatch examples

```text
task with agent: "test-saboteur", task: "Annotate source in <project-path>"
task with agent: "test-executor", task: "Run tests for <TestClass> in <project-path>"
task with agent: "test-auditor", task: "Audit results in <project-path>"
task with agent: "test-refactor-specialist", task: "Improve tests based on audit"
```

For pipeline relationships and ordering, see [About the OMP 5-agent mutation testing system](../explanation/agent-system.md). For the accepted design, see [ADR-002](../adr/0002-agent-structure-and-orchestration-model.md).
