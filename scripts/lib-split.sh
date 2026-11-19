#!/usr/bin/env bash
# Shared split logic plus quilt-engine helpers. Sourced, never executed.
# Expects REPO_ROOT to be set by the caller.
#
# The upstream tree lives in .upstream/ (gitignored). Its module sources are
# copied into the same-named Maven modules; module-info.java is dropped
# because the fork builds classpath jars (and Java 8 has no JPMS).
# Everything else in a module directory (pom.xml, resources) is fork-owned.
# Patch mechanics (apply/order/state/regen) are quilt's: patches/ holds the
# -p1 files, patches/series sets apply order, .pc/ holds quilt state.

UPSTREAM_URL="https://github.com/faststats-dev/faststats-java.git"

# Upstream modules shipped by the Maven reactor. fabric is built by the
# separate Stonecutter project in mod-platforms/fabric; forge, neoforge and
# onboarding are not included for now.
MODULES="core config bukkit bungeecord hytale minestom nukkit sponge velocity"

split_upstream() {
    local up="${1:-$REPO_ROOT/.upstream}"

    for m in $MODULES; do
        rm -rf "$REPO_ROOT/$m/src"
        if [ ! -d "$up/$m/src" ]; then
            echo "ERROR: upstream module '$m' has no src directory" >&2
            return 1
        fi
        mkdir -p "$REPO_ROOT/$m/src"
        cp -a "$up/$m/src/." "$REPO_ROOT/$m/src/"
        # Java 8 classpath build: no JPMS descriptors.
        find "$REPO_ROOT/$m/src" -name module-info.java -delete
    done

    # Fixed files owned by the fork (generated resources, ...).
    if [ -d "$REPO_ROOT/overlays" ]; then
        cp -a "$REPO_ROOT/overlays/." "$REPO_ROOT/"
    fi
}

# --- quilt engine helpers (deterministic flags; --quiltrc - ignores user config) ---

# quilt_run <args...>: invoke quilt from the repo root.
quilt_run() {
    quilt --quiltrc - "$@" 2>&1
}

# stack_clean: true when no patch is applied (no session in progress).
stack_clean() {
    [ -z "$(quilt --quiltrc - applied 2>/dev/null || true)" ]
}

# stack_full: true when every series patch is applied (set comparison:
# `quilt unapplied` prints a status message on full stacks, so its output
# cannot be tested for emptiness).
stack_full() {
    [ "$({ quilt --quiltrc - applied 2>/dev/null || true; } | sed 's|^patches/||' | LC_ALL=C sort | sha256sum)" == "$(grep -vE '^\s*(#|$)' "$REPO_ROOT/patches/series" | LC_ALL=C sort | sha256sum)" ]
}

# quilt_top: basename of the topmost applied patch, empty when none.
quilt_top() {
    { quilt --quiltrc - top 2>/dev/null || true; } | sed 's|^patches/||'
}

# in_series <basename>: true when listed in patches/series.
in_series() {
    [ -f "$REPO_ROOT/patches/series" ] && grep -qxF "$1" "$REPO_ROOT/patches/series"
}

# series_max_prefix: highest two-digit prefix in patches/series (empty if none).
series_max_prefix() {
    [ -f "$REPO_ROOT/patches/series" ] || return 0
    cut -c1-2 "$REPO_ROOT/patches/series" | LC_ALL=C sort -u | tail -n 1
}

# norm_patch_arg <arg>: accept `02-foo.patch` or `patches/02-foo.patch`.
norm_patch_arg() {
    local a="$1"
    a="${a#patches/}"
    printf '%s' "$a"
}

# upstream_pinned_ok: .upstream exists at the SHA in upstream.ref.
upstream_pinned_ok() {
    [ -d "$REPO_ROOT/.upstream" ] || return 1
    local want have
    want="$(tr -d '[:space:]' < "$REPO_ROOT/upstream.ref")"
    have="$(git -C "$REPO_ROOT/.upstream" rev-parse HEAD 2>/dev/null)" || return 1
    [ "$want" == "$have" ]
}
