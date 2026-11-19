#!/usr/bin/env bash
# Regenerate one patch from worktree edits (`quilt refresh`), then push the
# deferred rest (`quilt push -a`). No state file: `quilt top` is the session.
# Also records patches started with `bash scripts/new-patch.sh <NN-name>`.
# Usage: bash scripts/finish-patch.sh <patch-file> [--continue]
# Without --continue: `quilt top` must be <patch-file>; it is refreshed and
#   the rest is pushed. An empty result is an error: drop the patch with
#   `quilt delete -r <patch-file>` instead of committing an empty one.
# With --continue: skip refresh, just resume `quilt push -a` after the
#   operator resolved a conflict (.rej files / markers).
# On push conflict the remaining stack stays unapplied; resolve and re-run
# with --continue (refresh is NOT re-run, so resolutions are preserved).
# NB: quilt push -a exits 2 on the fully-applied noop; completion is judged
# by the post-condition (full stack), not the exit code.
set -euo pipefail
export LC_ALL=C
export QUILT_PATCHES=patches

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=lib-split.sh
source "$REPO_ROOT/scripts/lib-split.sh"

[ "$#" -ge 1 ] || { echo "Usage: bash scripts/finish-patch.sh <patch-file> [--continue]" >&2; exit 1; }
TARGET="$(norm_patch_arg "$1")"
MODE="${2:-}"

push_rest() {
    quilt_run push -a --fuzz=0 || true
    if stack_full; then
        echo "Stack fully applied."
        echo "Final consistency check: bash scripts/sync-upstream.sh"
        return 0
    fi
    echo "ERROR: conflict pushing the rest (.rej files listed above)." >&2
    echo "Resolve the .rej files, test with mvn -pl <module> -am test," >&2
    echo "then resume: bash scripts/finish-patch.sh $TARGET --continue" >&2
    return 1
}

if [ "$MODE" == "--continue" ]; then
    push_rest
    exit "$?"
fi

[ "$MODE" == "" ] || { echo "ERROR: unknown flag: $MODE (only --continue)" >&2; exit 1; }
TOP="$(quilt_top || true)"
[ "$TOP" == "$TARGET" ] || {
    echo "ERROR: quilt top is '${TOP:-<none>}', not patches/$TARGET." >&2
    if [ -z "$TOP" ]; then
        echo "Next step: bash scripts/edit-patch.sh $TARGET" >&2
    else
        echo "Next step: bash scripts/finish-patch.sh $TOP" >&2
    fi
    exit 1
}
quilt_run refresh -p ab --no-timestamps --no-index || true
if ! grep -q '^@@' "$REPO_ROOT/patches/$TARGET"; then
    echo "ERROR: no changes recorded in patches/$TARGET (empty patch)." >&2
    echo "Usual cause: edited files were never 'quilt add'ed, so refresh ignored them." >&2
    echo "Drop it with: quilt delete -r $TARGET" >&2
    exit 1
fi
echo "Refreshed patches/$TARGET."
push_rest
