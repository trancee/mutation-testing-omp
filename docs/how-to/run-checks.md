# How to run documentation checks

Use this guide when you need to verify that your documentation changes pass the project's markdown lint and link-check tools.

## Prerequisites

- **Node.js**, required for `markdownlint-cli2` style checks
- **lychee**, required for internal and external link checks

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

The `--offline` flag skips external URL checks. Use it when the network is unavailable.

## What the checks do

1. **markdownlint-cli2** checks Markdown headings, formatting, and blank lines. Configuration lives in `.markdownlint-cli2.jsonc`.
2. **lychee** checks internal and external links. Offline mode checks internal links only.

## Common issues and fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `npx: command not found` | Node.js not installed | Install Node.js (see above) |
| `lychee: command not found` | lychee not installed | Install lychee (see above) |
| MD047 | File does not end with one newline | End the file with exactly one newline |
| MD024 | Duplicate headings | Use unique headings or leave the existing project exemption in place |
| MD013 | Line too long | No action; the project configuration disables this rule |

## CI behavior

The project's CI runs both checks on every push and pull request. See `.github/workflows/ci.yml` for the workflow configuration.
