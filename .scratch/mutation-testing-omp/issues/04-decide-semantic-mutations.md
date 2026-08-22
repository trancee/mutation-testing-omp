# Bring LLM-guided semantic mutations to Kotlin via mutflow

Labels: wayfinder:task
Type: task
Status: resolved (grilling session 2026-08-22)
Blocked by: (resolved — R2 research complete)

## Question

How to bridge Scott-CC's LLM-guided semantic mutation approach with mutflow's predefined compiler-plugin operator catalog?

Scott-CC's test-saboteur uses an LLM to identify business logic (not framework boilerplate) and create context-aware mutations (boundary conditions, return values, boolean logic, arithmetic). mutflow injects predefined operators at compile time via its IR transformer. Investigate and decide:

1. Can mutflow's `MutationOperator` interface be extended to add LLM-guided mutation points, or must we use a separate mutation injection approach?
2. Should we use mutflow's operators as-is and add a post-filtering step (LLM review of generated mutations to pick the high-value ones), or pre-select mutation targets via LLM analysis?
3. What's the trade-off between mutflow's compile-once speed and Scott-CC's per-mutant parallel worktree model for mutation quality?
4. How to map Scott-CC's 5 mutation strategies (boundary, return value, boolean logic, arithmetic, exception types) to mutflow's operator catalog?
5. Can mutflow's `@SuppressMutations` + `// mutflow:ignore` suppression mechanism be leveraged for context-aware exclusion (like Scott-CC's "skip framework code")?
## Resolution

### Decisions (from grilling 2026-08-22)
1. **Use mutflow operators as-is + LLM suppression targeting**: No separate mutation injection. LLM identifies non-business framework code; saboteur adds `// mutflow:ignore` / `@SuppressMutations` to suppress low-value mutations. mutflow's operators drive all mutations.
2. **Pre-select targets via LLM analysis**: Not post-filtering — LLM proactively adds `@MutationTarget` to business logic classes and suppression comments to framework code before mutflow runs.
3. **Compile-once speed wins**: No per-mutant worktree compilation. Trade mutation quality for speed; mutflow's comprehensive operator catalog ensures broad coverage.

### Scott-CC mutation strategy → mutflow operator mapping

| Scott-CC Strategy | mutflow Operator | Match |
|---|---|---|
| Boundary conditions | RelationalComparisonOperator (> ↔ >=, < ↔ <=) + ConstantBoundaryOperator | Full |
| Return values | BooleanReturnOperator, NullableReturnOperator | Full |
| Boolean logic | BooleanInversionOperator, EqualitySwapOperator, BooleanLogicOperator (&& ↔ \|\|) | Full |
| Arithmetic | ArithmeticOperator (+ ↔ -, * ↔ /, % ↔ /) | Full |
| Exception types | None | **Skip** — no mutflow operator for exception mutation |

### test-saboteur role (redefined)

In the mutflow model, test-saboteur is a **mutation targeting specialist**, not a mutation creator:
- Reads source code, identifies business logic vs framework boilerplate
- Adds `@MutationTarget` to business logic classes (`Calculator.kt`, `AuthService.kt`, etc.)
- Adds `// mutflow:ignore` comments to: logging, debug utilities, framework wiring, heuristics
- Adds `@SuppressMutations` to classes that are pure data carriers or trivial getters/setters
- Does NOT create mutations — mutflow's compiler plugin injects them at compile time

### LLM-guided targeting workflow

1. Saboteur reads all source files in `commonMain`/`jvmMain`
2. LLM identifies business-logic classes vs framework/infrastructure code
3. Saboteur adds `@MutationTarget` to business-logic classes only
4. Saboteur adds `// mutflow:ignore` to non-business lines within targeted classes
5. mutflow compiler plugin injects operators only into `@MutationTarget` classes
6. Tests run with mutflow's multi-run model (baseline + mutation runs)

### Exception type mutations

**Open gap**: mutflow has no operator for mutating exception types (throw `IllegalArgumentException` → throw `RuntimeException`). This strategy is **skipped** in the initial port. Future work: extend mutflow's compiler plugin with a custom `ExceptionTypeMutationOperator`, or use a separate LLM saboteur step for exception-only mutations.

