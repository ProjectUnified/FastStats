#!/usr/bin/env bash
# Enter edit mode for one existing patch: rebuild the module sources from
# .upstream + overlays, `quilt push` up to AND INCLUDING the target (later
# patches stay deferred; quilt tracks the stack in .pc/, no state file).
# The target stays applied so edits refine it: regenerating from a reverted
# tree would silently drop the patch's own hunks.
# Refuses only on a PARTIAL stack (a session in progress). A fully-pushed or
# clean tree is rebuilt deterministically; uncommitted new-patch work on such
# a tree is wiped, so commit new patches first.
# Usage: bash scripts/edit-patch.sh <patch-file>  (basename, with or without
# the patches/ prefix). Never modifies patches/ itself.
# NB: quilt push exit codes are unreliable (2 on noop); completion is judged
# by the post-condition (`quilt top` == target), not the exit code.
set -euo pipefail
export LC_ALL=C
export QUILT_PATCHES=patches

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=lib-split.sh
source "$REPO_ROOT/scripts/lib-split.sh"

[ "$#" -eq 1 ] || { echo "Usage: bash scripts/edit-patch.sh <patch-file>" >&2; exit 1; }
TARGET="$(norm_patch_arg "$1")"
in_series "$TARGET" || { echo "ERROR: patches/$TARGET is not in patches/series." >&2; exit 1; }
if ! stack_clean && ! stack_full; then
    echo "ERROR: quilt stack is partially applied (top: $(quilt_top)); a session is in progress." >&2
    echo "Finish it (bash scripts/finish-patch.sh $(quilt_top)) or reset with bash scripts/sync-upstream.sh." >&2
    exit 1
fi
if ! upstream_pinned_ok; then
    echo "ERROR: .upstream is missing or not at the pinned SHA; run first:" >&2
    echo "  bash scripts/sync-upstream.sh" >&2
    exit 1
fi

split_upstream "$REPO_ROOT/.upstream"
rm -rf "$REPO_ROOT/.pc"
quilt_run push "$TARGET" --fuzz=0 || true
if [ "$(quilt_top)" != "$TARGET" ]; then
    echo "ERROR: failed below/at patches/$TARGET (top is '$(quilt_top)'); resolve or reset first:" >&2
    echo "  bash scripts/sync-upstream.sh  (restores the synced tree)" >&2
    exit 1
fi

echo "Editing patches/$TARGET (applied, on top); later patches deferred:"
quilt --quiltrc - unapplied 2>/dev/null
echo "Next steps:"
echo "  1. quilt add every file you will touch (BEFORE editing or creating), then edit."
echo "     Do NOT run sync-upstream.sh mid-session. Deleted files: quilt add <file>, then rm it."
echo "     Unrecorded edits are invisible to refresh and will be lost."
echo "  2. Test what still compiles: mvn -pl <module> -am test"
echo "     (deferred patches can break mid-session compilation.)"
echo "  3. Regenerate: bash scripts/finish-patch.sh $TARGET"
