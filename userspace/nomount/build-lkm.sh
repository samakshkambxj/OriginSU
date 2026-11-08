#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
#
# Builds the vendored NoMount module inside an Android DDK container and drops
# the stripped module at $NOMOUNT_OUT. Invoked by
# .github/workflows/ddk-lkm.yml; it is an OriginSU-authored file, not upstream
# (see UPSTREAM.md).
#
# The vendored sources are never written to: the module is compiled from a copy
# in a temporary directory, so `userspace/nomount/kernel/src/` stays
# byte-identical to upstream and re-syncing remains a mechanical diff.
#
# Required environment (provided by the DDK image unless noted):
#   KDIR          kernel build directory inside the container
#   NOMOUNT_OUT   absolute path for the produced .ko (host-visible)
#   NOMOUNT_ARCH  kernel architecture to build for; defaults to arm64. The module
#                 has no arch-specific code, so x86_64 uses the same sources.
#
set -eu

# Resolve the vendored sources relative to this script so it works from any
# checkout location, including the CI runner workspace.
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SELF_DIR/kernel/src"

[ -d "$SRC" ] || { echo "NoMount source not found at $SRC" >&2; exit 1; }
[ -n "${KDIR:-}" ] || { echo "KDIR is not set; run inside the Android DDK image" >&2; exit 1; }
[ -n "${NOMOUNT_OUT:-}" ] || { echo "NOMOUNT_OUT is not set" >&2; exit 1; }

NOMOUNT_ARCH="${NOMOUNT_ARCH:-arm64}"

# Some KMIs ship DDK headers that reference page-pinner helpers the kernel does
# not export, which fails the build for a module that never uses them. Disable
# the include and its single call site in the header copy, as upstream does.
#
# This writes outside the workspace (/opt/ddk), which only works when the
# container user owns those headers. Patch best-effort: if it is not permitted,
# continue, so a build that actually needed the patch fails on its own with a
# readable compiler error instead of an obscure permission failure here.
patched=0
for mm in /opt/ddk/src/*/include/linux/mm.h /opt/ddk/kdir/*/include/linux/mm.h \
    /opt/ddk/kdir-x64/*/include/linux/mm.h; do
    [ -f "$mm" ] || continue
    grep -q '^#include <linux/page_pinner.h>' "$mm" || continue
    if cp "$mm" "$mm.orig" 2>/dev/null &&
        sed -i 's|^#include <linux/page_pinner.h>|/* NoMount: page_pinner disabled */|' "$mm" 2>/dev/null &&
        sed -i 's|^\tpage_pinner_put_page(page);|\t/* NoMount: page_pinner disabled */|' "$mm" 2>/dev/null; then
        echo "-- patched $mm"
        patched=$((patched + 1))
    else
        echo "!! cannot patch $mm (headers not writable); continuing" >&2
    fi
done
echo "-- page_pinner headers patched: $patched"

build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT
cp -a "$SRC/." "$build_dir/"

echo "-- building NoMount from $build_dir against $KDIR"
echo "-- NoMount architecture: $NOMOUNT_ARCH"

# Each CI step inherits the image environment, not the previous step's exports.
# Match the x86_64 KernelSU build without the image's arm64 cross compiler.
case "$NOMOUNT_ARCH" in
x86_64)
    unset ARCH CROSS_COMPILE
    kmi=$(basename "$KDIR")
    build_root="$KDIR"
    if [ "$kmi" = android17-6.18 ]; then
        output_args="MO=$build_dir"
    else
        output_args="src=$build_dir"
    fi
    toolchain_args=""
    ;;
*)
    build_root="$KDIR"
    output_args=""
    toolchain_args="LLVM=1 LLVM_IAS=1"
    ;;
esac

echo "-- building against $build_root ${output_args:-in place}"
# shellcheck disable=SC2086  # the output and toolchain arguments are deliberate word splits
CONFIG_NOMOUNT=m make -C "$build_root" $output_args ARCH="$NOMOUNT_ARCH" $toolchain_args M="$build_dir" modules

ko="$build_dir/nomount.ko"
[ -f "$ko" ] || { echo "nomount.ko was not produced in $build_dir" >&2; exit 1; }

# Match the kernelsu.ko assets: strip debug/unneeded and drop BTF.
llvm-strip --strip-unneeded -R .BTF "$ko"

mkdir -p "$(dirname "$NOMOUNT_OUT")"
cp "$ko" "$NOMOUNT_OUT"
ls -la "$NOMOUNT_OUT"
