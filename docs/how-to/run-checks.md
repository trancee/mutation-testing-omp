# How to run documentation checks

Use this guide when you need to verify that your documentation changes pass the project's markdown lint and link-check tools.

## Prerequisites

- **Node.js** (required for `markdownlint-cli2` — style checking)
- **lychee** (required for link checking — validates internal and external URLs)

### Install Node.js

If you have [SDKMAN](https://sdkman.io):

```bash
sdk install node 22
```

Or download from [nodejs.org](https://nodejs.org/). Verify:

```bash
node --version
npx --version
```

### Install lychee

```bash
brew install lychee
```

Or via curl:

```bash
curl -LsSf https://github.com/lycheeverse/lychee/releases/latest/download/lychee-installer.sh | sh
```

## Running the checks

### Check a single file

```bash
./scripts/check-markdown.sh docs/how-to/contribute-documentation.md
```

### Check all markdown files

```bash
./scripts/check-markdown.sh
```

### Check offline (internal links only)

```bash
./scripts/check-markdown.sh --offline docs/
```

The `--offline` flag skips external URL checks — useful when you don't have network access.

## What the checks do

1. **markdownlint-cli2** — checks Markdown style (headings, formatting, blank lines, etc.). Config lives in `.markdownlint-cli2.jsonc`.
2. **lychee** — checks for broken links (internal file links and anchors, plus external URLs unless `--offline`).

## Common issues and fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `npx: command not found` | Node.js not installed | Install Node.js (see above) |
| `lychee: command not found` | lychee not installed | Install lychee (see above) |
| MD047 | File does not end with a single newline | Add a blank line at the end of the file |
| MD024 | Duplicate headings | Use unique headings or disable MD024 (already disabled in config) |
| MD013 | Line too long | Already disabled in config — long prose is allowed |

## CI behavior

The project's CI runs both checks on every push and pull request. See `.github/workflows/ci.yml` for the workflow configuration.
