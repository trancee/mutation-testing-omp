# Research: R1 — OMP Agent/Task Dispatch System

Labels: wayfinder:research
Type: research
Status: in_progress (being resolved by R1OmpResearch scout subagent)

## Source files consulted

- `omp://tools/task.md` — Task tool documentation (concurrency, batch mode, output aggregation)
- `omp://task-agent-discovery.md` — Task agent discovery and selection
- `omp://tools/hub.md` — Hub tool (messaging, job control, process supervision)
- `omp://skills.md` — Skills documentation (SKILL.md frontmatter, discovery, slash-command mapping)
- `~/.omp/agent/agents/` — User-level agent definition directory
- `.omp/agents/` — Project-level agent definition directory
- `~/.agents/skills/wayfinder/agents/openai.yaml` — Wayfinder agent definition example
- `~/.agents/skills/research/agents/openai.yaml` — Research agent definition example
- `~/.agents/skills/code-review/SKILL.md` — Parallel subagent spawning pattern
- `~/.agents/skills/diagnosing-bugs/SKILL.md` — Agent dispatch with skills

## Answers to the 6 research questions

### 1. Agent definition

**OMP defines subagents as markdown files with YAML frontmatter**, placed in:
- Project scope: `.omp/agents/*.md`
- User scope: `~/.omp/agent/agents/*.md`
- Extension packages: `<extension-root>/agents/`
- Claude marketplace plugins (when `isProviderEnabled("claude-plugins")`)
- Bundled: `scout`, `designer`, `reviewer`, `security-reviewer`, `librarian`, `task`, `sonic`

Frontmatter fields: `name` (required, the dispatch key), `description` (required for native provider), `systemPrompt` (or body text serves as instructions), `tools` (CSV or array), `spawns` (which agents this agent can spawn), `model` (model selector, can use role aliases like `@review`), `thinkingLevel`, `output` (schema), `blocking`, `autoloadSkills`, `readSummarize`, `prewalk`, `advisor`.

Discovery precedence (first-wins by exact name, case-sensitive): project `.omp/agents` → user `.omp/agent/agents` → extension packages → Claude marketplace → bundled.

### 2. Domain-specific dispatch (namespace pattern)

**OMP supports any agent name as the dispatch key.** The `agent` parameter in the `task` tool matches the frontmatter `name` field exactly. Scott-CC's `Task(subagent_type="mutation-testing:test-quality-reviewer")` maps to an OMP agent file with `name: "test-quality-reviewer"` (or any chosen name) dispatched via:

```json
{ "context": "...", "tasks": [{ "agent": "test-quality-reviewer", "task": "..." }] }
```

Different agent types can be mixed in one `tasks[]` batch. The `mutation-testing:` namespace prefix used in Claude Code is not required in OMP — the agent name alone is the dispatch key. However, you could use namespaced names like `mt-test-saboteur` for clarity.

### 3. Parallel execution

**Parallel execution uses `tasks[]` batch mode** (default on). One subagent spawns per item, all in parallel. A session-scoped `Semaphore` (`task.maxConcurrency`, default 32) bounds fan-out. Each spawn can use a different `agent` type.

Two execution modes:
- **Async** (`async.enabled=true`): spawns background jobs, results delivered later
- **Sync** (default or `blocking: true` on agent): blocks until all complete, results in `details.results[]`

For Scott-CC's "all Task calls in ONE message" pattern, the OMP equivalent is a single `tasks[]` batch call OR multiple parallel `task` tool invocations in one assistant message (both bounded by the semaphore).

### 4. Tool availability

**Tools are declared per-agent, not inherited from parent.** In frontmatter: `tools` (CSV or array). Key behaviors:
- If `spawns` is declared, `task` is auto-added to the agent's tools (depth permitting)
- `hub` is retained for collaboration messaging unless explicitly restricted
- `yield` is auto-added if tools are specified
- `read-summarize: false` forces `read` to return verbatim content (default for `scout`/`librarian`)
- `expand exec to eval + bash` — exec tools expand to bash/eval internally
- `task` tool is stripped at `task.maxRecursionDepth` (default 2)

So to give an agent bash, read, edit, grep, glob, lsp, and task, you'd declare those in its frontmatter.

### 5. Skill-to-task mapping

**Skills (SKILL.md) are prompt-driven instructions, not executable command manifests.** They do NOT have an `allowed-tools` field like Claude Code commands. The agent has all tools available, and the skill's instructions tell it what to use.

Key differences from Scott-CC:
- Scott-CC command has `allowed-tools: Task(...), Read, Grep, Glob, Bash, AskUserQuestion` → OMP: agent `tools` frontmatter field restricts available tools
- Scott-CC `$ARGUMENTS` parsing → OMP: `/skill:<name> [args]` passes args as surrounding prose, or the task prompt itself carries the parameters
- Scott-CC `AskUserQuestion` → OMP `ask` tool
- `disableModelInvocation: true` on skill frontmatter means the model doesn't auto-invoke it — requires explicit `/skill:<name>` command

Skills that dispatch subagents (like `code-review`) include the dispatch instruction in their body: "Both axes run as parallel sub-agents in one message." The `research` skill says "Spin up a background agent."

### 6. Result aggregation

**Two mechanisms:**
1. **Sync batch**: `task` tool's `details.results[]` contains one `SingleResult` per spawn with `output`, `structuredOutput`, `exitCode`, `durationMs`, `tokens`
2. **Async**: each spawn is a background job; completion injected as async-result message into the parent conversation; `hub wait` can block on job completion

For orchestrator coordination: the orchestrating agent calls `task` with a `tasks[]` batch, waits for all results, then processes them. The `hub` tool provides `jobs` (status snapshot), `wait` (block on completion), and `send`/`inbox` (peer messaging between agents) for ongoing coordination.

## Scott-CC → OMP mapping table

| Scott-CC (Claude Code) | OMP equivalent |
|---|---|
| `Task(subagent_type="mutation-testing:test-quality-reviewer")` | `task` tool with `agent: "test-quality-reviewer"` in `tasks[]` |
| `allowed-tools: Task(...), Read, Grep, Glob, Bash, AskUserQuestion` | Agent `tools` frontmatter field |
| `$ARGUMENTS` | Task prompt carries params; `/skill:<name> [args]` |
| `AskUserQuestion` | `ask` tool |
| Git worktree bash commands | `bash` tool (same git commands) |
| Parallel `Task` calls in one message | `tasks[]` batch in one `task` call |
| Plugin ships agents under namespace | Create `.omp/agents/*.md` OR `~/.omp/agent/agents/*.md` |
| Agent orchestrator receives subagent output | `details.results[]` (sync) or async-result injection |

## Key implications for porting Scott-CC

1. **Agent definition**: Create 5 agent files (test-quality-reviewer, test-saboteur, test-executor, test-auditor, test-refactor-specialist) in `.omp/agents/` with appropriate `tools` frontmatter.
2. **Orchestration**: The orchestrator agent uses `task` tool with `tasks[]` batch for parallel dispatch.
3. **Skill entry point**: A `/mutation-test` skill (SKILL.md) provides the slash-command interface, with instructions to dispatch the orchestrator agent via `task`.
4. **No namespace needed**: Agent names can be flat (`test-saboteur`) but can use prefixes (`mt-test-saboteur`) for clarity.
5. **Concurrency**: The 32-agent default limit means the 15 test-executor agents fit within one batch.
