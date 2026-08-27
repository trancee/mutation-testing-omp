# How to contribute documentation following the Diataxis framework

Use this guide when you are adding, editing, or restructuring documentation in this repository. This guide assumes you are already familiar with Markdown and Git.

The documentation in this repo follows the [Diataxis](https://diataxis.fr) framework, which classifies every document into exactly one of four types. Before you write, classify your content. If it serves two needs, split it.

## Prerequisites

- You have identified the need you are documenting (a task to perform, a concept to explain, data to look up, or a skill to learn)
- You have read this guide at least once

## Step 1: Classify your content with the compass

Ask two questions:

1. **Does your content inform action (doing) or cognition (thinking)?**
2. **Does it serve acquisition of skill (study) or application of skill (work)?**

| Content informs... | And serves the user's... | It belongs to... |
|--------------------|--------------------------|-------------------|
| Action | Acquisition of skill | **Tutorial** |
| Action | Application of skill | **How-to guide** |
| Cognition | Application of skill | **Reference** |
| Cognition | Acquisition of skill | **Explanation** |

### Quick decision tree

- **Am I teaching someone to do something from scratch?** → Tutorial
- **Am I helping someone get a specific task done?** → How-to guide
- **Am I describing how something works or what options exist?** → Reference
- **Am I giving background, rationale, or "why" context?** → Explanation

## Step 2: Place your document in the right folder

```
docs/
  tutorials/        # Tutorials — learning-oriented ("I need to learn...")
  how-to/           # How-to guides — goal-oriented ("I need to...")
  reference/        # Reference — information-oriented ("What does this do?")
  explanation/      # Explanation — understanding-oriented ("Why does this...")
```

File names should be short, descriptive, and use lowercase with hyphens. Prefix titles with the type explicitly (e.g., `# Tutorial: Your first mutation test`, `# How to fix surviving mutations`, `# Reference: mutation-results.json format`).

## Step 3: Write according to your type's rules

Each type has specific writing rules. Follow your type's reference for the full guidance:

- [Writing tutorials](../../\.agents/skills/diataxis/references/tutorials.md)
- [Writing how-to guides](../../\.agents/skills/diataxis/references/how-to-guides.md)
- [Writing reference docs](../../\.agents/skills/diataxis/references/reference.md)
- [Writing explanation docs](../../\.agents/skills/diataxis/references/explanation.md)

### Tutorials (learning-oriented)

- Take the learner through a hands-on experience — you are responsible for their success
- Use "we" language: "We will...", "Now, let's..."
- Deliver visible results early and often
- Ruthlessly minimize explanation — one sentence max, with a link to more
- Ignore options and alternatives
- Do not assume domain knowledge
- Test end-to-end and include expected output

### How-to guides (goal-oriented)

- Address a real-world goal, not a tool's capabilities
- Assume the reader is already competent
- Provide executable, actionable steps
- Stay focused on the task — no teaching, no digressions
- Link to reference for complete option lists; link to explanation for "why"
- Handle real-world complexity, not just the happy path
- Name clearly: "How to [goal]"

### Reference (information-oriented)

- Describe the machinery — nothing more
- Be austere, accurate, complete, and neutral
- Mirror the structure of the thing you are describing
- State facts, list options, provide warnings
- Include examples to illustrate, not to teach
- Do not include opinions, interpretations, or "why"

### Explanation (understanding-oriented)

- Provide context, background, and "why"
- Can include opinions and multiple perspectives
- Make connections to other concepts and broader context
- Name with implicit "About..." prefix
- Keep it bounded — don't let reference or how-to creep in
- Could be read away from the product and still make sense

## Step 4: Cross-reference correctly

Each document should link to its neighbors by type, never by inlining content that belongs elsewhere.

| If your doc is... | Link to (don't inline) |
|--------------------|------------------------|
| Tutorial | How-to guides for the workflow steps; reference for option lists; explanation for "why" |
| How-to guide | Reference for complete parameters/options; explanation for "why" this approach works |
| Reference | Explanation for "why" a design decision was made; how-to guides for "how to use" |
| Explanation | Reference for factual details; how-to guides for procedures |

When in doubt, link to a sibling document and say "for more details, see..." rather than expanding inline.

## Step 5: Check quality

Before you commit, run each of these checks against your document:

1. **One type only** — the document clearly belongs to ONE type (not a mix)
2. **Title matches type** — the heading reflects both content and type
3. **Lives in the right folder** — matches the type's directory
4. **Cross-references are correct** — links point to the right type, no inline leakage

Then check your specific type's quality items using the [Diataxis quality checklist](../../\.agents/skills/diataxis/references/quality-checklist.md). The most common boundary violations are:

| Symptom | Problem | Fix |
|---------|---------|-----|
| Tutorial has paragraphs of "why" | Explanation leaked into tutorial | Extract to explanation doc, add a link |
| How-to guide teaches basics first | Tutorial content in how-to | Assume competence; link to tutorial |
| Reference says "you should..." | How-to guidance in reference | Move to how-to guide |
| Reference explains design decisions | Explanation in reference | Move to explanation doc |
| Explanation has step-by-step code | How-to in explanation | Move to how-to guide |
| One page tries to do everything | Mixed document | Split into separate docs by type |

## Step 6: Run the lint check

After writing, run the project's markdown lint check to catch style and link issues:

```bash
./scripts/check-markdown.sh docs/how-to/contribute-documentation.md
```

This runs markdownlint-cli2 (style) and lychee (broken links). Install Node.js (for markdownlint-cli2) and lychee (for link checking) if you do not have them.

## Workflow: one small step at a time

Do not try to restructure the entire `docs/` folder in one go. Instead:

1. Choose something in front of you — a page, a section, or a paragraph.
2. Assess it against the compass — what need does this serve? How well?
3. Decide the single next action that improves it.
4. Do it and consider it done.
5. Repeat.

At every point, the docs should be complete for their current stage — useful and structurally sound, even though the project is never "finished."

## See also

- [Diataxis framework overview](https://diataxis.fr)
- [Quality checklist](../../\.agents/skills/diataxis/references/quality-checklist.md)
- [Documentation index](../index.md)
