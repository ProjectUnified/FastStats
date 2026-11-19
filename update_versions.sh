#!/usr/bin/env bash
# Keeps child module versions in sync with the root project version.
# Maven modules are updated from the reactor; the standalone Fabric Gradle
# project is updated from the same version in stonecutter.properties.toml.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1)"
if [ -z "$VERSION" ]; then
    echo "ERROR: could not read the project version from pom.xml" >&2
    exit 1
fi

# Fork versions are <upstream-version>.<patch-number>.
UPSTREAM_VERSION="$(sed -n 's/^version=//p' "$ROOT/.upstream/gradle.properties" 2>/dev/null | head -n 1 || true)"
if [ -n "$UPSTREAM_VERSION" ] && [[ "$VERSION" != "$UPSTREAM_VERSION".* ]]; then
    echo "WARNING: fork version $VERSION should be $UPSTREAM_VERSION.<patch-number>" >&2
fi

MODULE_DIRS=()

# Function to recursively find valid pom.xml files
find_modules() {
    local dir="$1"
    local pom_file="$dir/pom.xml"
    if [[ -f "$pom_file" ]] && grep -q '<packaging>pom</packaging>' "$pom_file"; then
        MODULE_DIRS+=("$dir")
        for subdir in "$dir"/*/; do
            [[ -d "$subdir" ]] || continue
            find_modules "$subdir"
        done
    fi
}

find_modules "$ROOT"

for dir in "${MODULE_DIRS[@]}"; do
    echo "Updating versions for module in directory: $dir"
    (cd "$dir" && mvn -B -N versions:update-child-modules -DgenerateBackupPoms=false)
done

FABRIC_PROPS="$ROOT/mod-platforms/fabric/stonecutter.properties.toml"
if [ -f "$FABRIC_PROPS" ]; then
    echo "Updating version for the Fabric module: $FABRIC_PROPS"
    sed -i -E "s/^mod\.version *= *\".*\"/mod.version = \"$VERSION\"/" "$FABRIC_PROPS"
    sed -i -E "s/^deps\.faststats *= *\".*\"/deps.faststats = \"$VERSION\"/" "$FABRIC_PROPS"
fi

echo "Version: $VERSION"
