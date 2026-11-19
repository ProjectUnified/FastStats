#!/usr/bin/env bash
# Rebuild everything from the pinned upstream commit: fresh-clone .upstream,
# copy its module sources into the same-named Maven modules, copy overlays,
# then `quilt push -a --fuzz=0` the patches/series stack. No arguments.
# This is also the reset hatch: it wipes quilt state (.pc/) and rebuilds.
set -euo pipefail
export LC_ALL=C
export QUILT_PATCHES=patches

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=lib-split.sh
source "$REPO_ROOT/scripts/lib-split.sh"

SHA="$(tr -d '[:space:]' < upstream.ref)"
if ! [[ "$SHA" =~ ^[0-9a-f]{40}$ ]]; then
    echo "ERROR: upstream.ref must contain a single 40-char commit SHA, got: '$SHA'" >&2
    exit 1
fi

rm -rf .upstream .pc
git clone --no-checkout "$UPSTREAM_URL" .upstream
if ! git -C .upstream checkout "$SHA"; then
    echo "ERROR: SHA $SHA unreachable; re-resolve HEAD and overwrite upstream.ref:" >&2
    echo "  git ls-remote $UPSTREAM_URL HEAD" >&2
    exit 1
fi

split_upstream "$REPO_ROOT/.upstream"
# NB: quilt push -a exits 2 on the fully-applied noop, so judge by the
# post-condition (empty unapplied list), not the exit code.
quilt_run push -a --fuzz=0 || true
if ! stack_full; then
    echo "ERROR: stack not fully applied; .rej files and the failing patch are listed above." >&2
    echo "Next step: bash scripts/edit-patch.sh <that-patch>" >&2
    exit 1
fi

echo "Synced to $SHA; $(quilt --quiltrc - applied 2>/dev/null | wc -l) patch(es) applied."
echo "Next step: mvn clean package"
