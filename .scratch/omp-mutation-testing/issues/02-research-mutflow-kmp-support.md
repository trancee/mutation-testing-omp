# Research mutflow KMP support and compiler plugin internals

Labels: wayfinder:research
Type: research
Status: resolved (findings in research/02-mutflow-kmp-support.md)

## Question

How does mutflow handle Kotlin Multiplatform, and what are the implications for porting Scott-CC's mutation-testing approach to Kotlin?

Specifically, investigate:

1. **KMP target support**: Does mutflow support KMP targets beyond JVM (JS, Native, Android)? How does the dual-compilation approach (separate `mutatedMain` source set) interact with KMP's source set hierarchy (`commonMain`, `jvmMain`, `jsMain`, etc.)?
2. **Compiler plugin architecture**: How does the `MutflowIrTransformer` inject mutation points? What's the full mutation operator catalog (`MutationOperator`, `ReturnMutationOperator`, `WhenMutationOperator`, `FunctionBodyMutationOperator`) and how are new operators added?
3. **Runtime registry**: How does `MutFlow.underTest` + `@MutFlowTest` work at runtime? How does the global mutation registry discover points, select mutations, and activate them per run?
4. **Semantic mutation feasibility**: Can mutflow's compile-once meta-mutant approach produce context-aware semantic mutations (like Scott-CC's LLM-guided saboteur), or is it restricted to the predefined operator catalog? What's the extension point?
5. **Parallel execution**: How does mutflow handle test parallelization, mutation ordering, and the `Selection`/`Shuffle` modes? How does this interact with OMP's task-based parallelism?
6. **ZOMBIE detection compatibility**: mutflow uses a global run model (all mutations in one class, selected per run). Scott-CC compares test outcomes per-worktree. How does mutflow's model expose per-mutation test pass/fail data needed for zombie detection?
