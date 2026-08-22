# Research OMP agent and task dispatch system

Labels: wayfinder:research
Type: research
Status: resolved (findings in research/01-omp-agent-dispatch.md)

## Question

How does OMP's agent and task dispatch system work, and how does it map to the Claude Code `Task(subagent_type=...)` pattern that Scott-CC's mutation-testing plugin uses?

Specifically, investigate:

1. **Agent definition**: How are subagents defined in OMP? Are they skills (SKILL.md in `~/.agents/skills/`), or a different construct? How does the `task` tool's `agent` parameter select which subagent to spawn?
2. **Domain-specific dispatch**: Can OMP express the `mutation-testing:test-saboteur` namespace pattern that Scott-CC uses (5 agents under a `mutation-testing:` namespace)? If not, what's the OMP equivalent?
3. **Parallel execution**: How does parallel task execution work for the test-executor×N pattern (Scott-CC launches 15 executors simultaneously in one message)? What's the OMP `task` tool equivalent of sending all Task calls in one message?
4. **Tool availability**: What tools (bash, read, edit, grep, glob, lsp, etc.) are available to dispatched subagents vs the orchestrating agent?
5. **Skill-to-task mapping**: How does a slash-command skill (like `/mutation-test`) dispatch to subagents in OMP, and what's the equivalent of `allowed-tools: Task(...)` in the command manifest?
6. **Result aggregation**: How does the orchestrator (test-quality-reviewer) receive and aggregate results from parallel subagents?
