#!/usr/bin/env bash
# Start a new patch at the end of the series: validates <NN-name>, requires
# a fully-pushed stack, and runs `quilt new`. The operator then edits the
# tree and records with `bash scripts/finish-patch.sh <NN-name>` (same as an
# edit session; empty result is an error there).
# The name must match ^[0-9]{2}-[a-z0-9-]+\.patch$ with a numeric prefix
# strictly greater than every series entry. New files must be `quilt add`ed
# BEFORE creation, or refresh misses them.
set -euo pipefail
export LC_ALL=C
export QUILT_PATCHES=patches

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=lib-split.sh
source "$REPO_ROOT/scripts/lib-split.sh"

[ "$#" -eq 1 ] || { echo "Usage: bash scripts/new-patch.sh <NN-name>" >&2; exit 1; }
NAME="$(norm_patch_arg "$1")"
if ! [[ "$NAME" =~ ^[0-9]{2}-[a-z0-9-]+\.patch$ ]]; then
    echo "ERROR: name must match ^[0-9]{2}-[a-z0-9-]+\\.patch\\$: $NAME" >&2
    exit 1
fi
if [ -e "$REPO_ROOT/patches/$NAME" ]; then
    echo "ERROR: patches/$NAME already exists." >&2
    exit 1
fi
NEWPREFIX="${NAME:0:2}"
MAXPREFIX="$(series_max_prefix)"
if [ -n "$MAXPREFIX" ] && ! [[ "$NEWPREFIX" > "$MAXPREFIX" ]]; then
    echo "ERROR: prefix $NEWPREFIX is not greater than series max $MAXPREFIX." >&2
    echo "New patches always append at the end; reordering prefixes is forbidden." >&2
    exit 1
fi
if ! stack_full; then
    echo "ERROR: quilt stack is not fully pushed (top: $(quilt_top))." >&2
    echo "Finish the session first or restore with bash scripts/sync-upstream.sh." >&2
    exit 1
fi

quilt_run new "$NAME"
echo "Started patches/$NAME (empty, on top)."
echo "Next steps:"
echo "  1. quilt add every file you will touch (BEFORE editing or creating), then edit."
echo "  2. Record: bash scripts/finish-patch.sh $NAME"
