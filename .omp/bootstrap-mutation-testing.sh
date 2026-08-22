#!/usr/bin/env bash
set -euo pipefail

# bootstrap-mutation-testing.sh
# Installs the OMP 5-agent mutation testing system into a Kotlin project.
#
# Usage:
#   ./bootstrap-mutation-testing.sh <project-path> [--kmp]
#
# Copies .omp/ agents, skills, and Gradle scripts into the target project,
# configures build.gradle.kts and settings.gradle.kts.

PROJECT_PATH="${1:-.}"
KMP_MODE="${2:--jvm}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "$KMP_MODE" == "--kmp" ]]; then
    IS_KMP=1
elif [[ "$KMP_MODE" == "--jvm" ]]; then
    IS_KMP=0
else
    echo "Usage: $0 <project-path> [--kmp]"
    exit 1
fi

if [[ ! -d "$PROJECT_PATH" ]]; then
    echo "Error: project path '$PROJECT_PATH' does not exist"
    exit 1
fi

echo "Bootstrapping mutation testing into: $PROJECT_PATH"
echo "Mode: $( ((IS_KMP)) && echo "KMP" || echo "JVM" )"

# --- Step 1: Copy .omp directory ---
echo ""
echo "Copying .omp agents, skills, and scripts..."

target_dir="$PROJECT_PATH/.omp"
if [[ -d "$target_dir" ]]; then
    echo "  Warning: .omp directory already exists — merging"
fi
mkdir -p "$target_dir"
cp -r "$SCRIPT_DIR/agents" "$target_dir/"
cp -r "$SCRIPT_DIR/skills" "$target_dir/"
cp "$SCRIPT_DIR/mutation-results.gradle.kts" "$target_dir/"

# --- Step 2: Configure settings.gradle.kts ---
echo ""
echo "Configuring settings.gradle.kts..."

settings_file="$PROJECT_PATH/settings.gradle.kts"
if [[ ! -f "$settings_file" ]]; then
    root_name=""
    if [[ "$IS_KMP" == "1" ]]; then
        root_name='rootProject.name = "my-kmp-project"'
    else
        root_name='rootProject.name = "my-jvm-project"'
    fi
    cat > "$settings_file" << 'EOF'
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

EOF
    echo "$root_name" >> "$settings_file"
    echo "  Created settings.gradle.kts with pluginManagement"
else
    if grep -q "pluginManagement" "$settings_file"; then
        echo "  pluginManagement already present — skipping"
    else
        tmp=$(mktemp)
        cat << 'EOF'
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

EOF
        cat "$settings_file" >> "$tmp"
        mv "$tmp" "$settings_file"
        echo "  Added pluginManagement block"
    fi
fi

# --- Step 3: Configure build.gradle.kts ---
echo ""
echo "Configuring build.gradle.kts..."

build_file="$PROJECT_PATH/build.gradle.kts"
if [[ ! -f "$build_file" ]]; then
    echo "  Error: build.gradle.kts not found in $PROJECT_PATH"
    exit 1
fi

cp "$build_file" "$build_file.bak"

needs_plugin=""
needs_deps=""
needs_apply=""

if ! grep -q 'io.github.anschnapp.mutflow' "$build_file"; then
    needs_plugin="yes"
fi
if ! grep -q 'junit-jupiter-api' "$build_file"; then
    needs_deps="yes"
fi
if ! grep -q 'mutation-results.gradle.kts' "$build_file"; then
    needs_apply="yes"
fi

if [[ -n "$needs_plugin" ]]; then
    # Insert mutflow plugin into the plugins block
    if grep -q '^plugins {' "$build_file"; then
        sed -i.bak2 '/^plugins {/a\
    id("io.github.anschnapp.mutflow") version "1.0.5"' "$build_file"
    else
        # No plugins block — create one
        {
            echo 'plugins {'
            echo '    id("io.github.anschnapp.mutflow") version "1.0.5"'
            echo '}'
            echo ''
            cat "$build_file"
        } > "$build_file.tmp"
        mv "$build_file.tmp" "$build_file"
    fi
    echo "  Added mutflow plugin"
fi

if [[ -n "$needs_deps" ]]; then
    if grep -q '^dependencies {' "$build_file"; then
        sed -i.bak3 '/^dependencies {/a\
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")\
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.3")\
    testImplementation("io.github.anschnapp.mutflow:mutflow-junit6:1.0.5")' "$build_file"
    else
        # Append dependencies block
        cat >> "$build_file" << 'EOF'

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("io.github.anschnapp.mutflow:mutflow-junit6:1.0.5")
}
EOF
    fi
    echo "  Added JUnit 6 + mutflow-junit6 dependencies"
fi

if [[ -n "$needs_apply" ]]; then
    # Add apply after the first top-level closing brace of plugins block
    # We use awk to insert after the first standalone '}'
    if grep -q '^}$' "$build_file"; then
        first_close=$(grep -n '^}$' "$build_file" | head -1 | cut -d: -f1)
        sed -i.bak4 "${first_close}a\\
\\
apply(from = rootProject.file(\".omp/mutation-results.gradle.kts\"))" "$build_file"
    fi
    echo "  Applied mutation-results.gradle.kts"
fi

if [[ "$IS_KMP" == "1" ]]; then
    # For KMP, ensure mutflow targets jvmTest — mutflow plugin applies per-JVM-source-set
    if ! grep -q '^mutflow {' "$build_file"; then
        cat >> "$build_file" << 'EOF'

mutflow {
    enabled = true
    targets = listOf("jvmTest")
}
EOF
        echo "  Added mutflow KMP configuration (jvmTest targets)"
    fi
else
    if ! grep -q '^mutflow {' "$build_file"; then
        cat >> "$build_file" << 'EOF'

mutflow {
    enabled = true
}
EOF
        echo "  Added mutflow configuration"
    fi
fi

# Clean up temp files
rm -f "$build_file.bak" "$build_file.bak2" "$build_file.bak3" "$build_file.bak4"

# --- Step 4: Summary ---
echo ""
echo "✅ Bootstrap complete!"
echo ""
echo "Next steps:"
echo "  1. Run: /mutation-test $PROJECT_PATH"
echo "     The saboteur agent will annotate @MutationTarget and @MutFlowTest"
echo "  2. test-executor runs: gradle mutationResults"
echo "  3. test-auditor parses results and reports score"
echo "  4. test-refactor-specialist proposes boundary tests for survivors"
echo ""
echo "Or run directly: cd $PROJECT_PATH && gradle mutationResults"
