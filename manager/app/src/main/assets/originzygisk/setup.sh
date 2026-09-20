#!/system/bin/sh
# OriginZygisk setup: arrange the universal payload into the runtime layout
# for this device's ABI(s) and write the engine's own module.prop.
# Usage: setup.sh <deploy-dir>
set -e
D="$1"
SRC="$D/payload"
cd "$D" || exit 1

VER="unknown"
[ -f "$SRC/version" ] && VER=$(cat "$SRC/version")

# Pick the ABI list with the most entries (Tango devices split it across props).
CPU_ABIS_PROP1=$(getprop ro.system.product.cpu.abilist)
CPU_ABIS_PROP2=$(getprop ro.product.cpu.abilist)
if [ "${#CPU_ABIS_PROP2}" -gt "${#CPU_ABIS_PROP1}" ]; then
  CPU_ABIS=$CPU_ABIS_PROP2
else
  CPU_ABIS=$CPU_ABIS_PROP1
fi

SUPPORTS_32BIT=false
SUPPORTS_64BIT=false
case "$CPU_ABIS" in *arm64-v8a*|*x86_64*) SUPPORTS_64BIT=true ;; esac
case "$CPU_ABIS" in *armeabi*) SUPPORTS_32BIT=true ;; esac
case "$CPU_ABIS" in
  *x86*) [ "$CPU_ABIS" = "x86_64" ] || SUPPORTS_32BIT=true ;;
esac

# Map to the payload's per-ABI dir names + machikado suffixes.
case "$CPU_ABIS" in
  *arm64-v8a*|*armeabi*) A64=arm64-v8a; A32=armeabi-v7a; M64=arm64; M32=arm ;;
  *)                     A64=x86_64;    A32=x86;         M64=x86_64; M32=x86 ;;
esac

# Runtime layout expected by the engine's built-in paths:
#   bin/zygiskd64  bin/zygisk-ptrace64  lib64/libzygisk.so
#   bin/zygiskd32  bin/zygisk-ptrace32  lib/libzygisk.so
rm -rf bin lib lib64 machikado.*
mkdir -p bin

if [ "$SUPPORTS_64BIT" = "true" ]; then
  mkdir -p lib64
  cp "$SRC/bin/$A64/zygiskd"             bin/zygiskd64
  cp "$SRC/lib/$A64/libzygisk_ptrace.so" bin/zygisk-ptrace64
  cp "$SRC/lib/$A64/libzygisk.so"        lib64/libzygisk.so
  [ -f "$SRC/machikado.$M64" ] && cp "$SRC/machikado.$M64" "machikado.$M64"
fi

if [ "$SUPPORTS_32BIT" = "true" ]; then
  mkdir -p lib
  cp "$SRC/bin/$A32/zygiskd"             bin/zygiskd32
  cp "$SRC/lib/$A32/libzygisk_ptrace.so" bin/zygisk-ptrace32
  cp "$SRC/lib/$A32/libzygisk.so"        lib/libzygisk.so
  [ -f "$SRC/machikado.$M32" ] && cp "$SRC/machikado.$M32" "machikado.$M32"
fi

# Permissions + SELinux contexts (libs must look like system libraries).
chmod -R 0755 bin
[ -d lib64 ] && { chmod 0644 lib64/* ; chcon u:object_r:system_file:s0 lib64/* 2>/dev/null || true ; }
[ -d lib ]   && { chmod 0644 lib/*   ; chcon u:object_r:system_file:s0 lib/* 2>/dev/null || true ; }
[ -f "machikado.$M64" ] && chmod 0755 "machikado.$M64"
[ -f "machikado.$M32" ] && chmod 0755 "machikado.$M32"

# The engine reads/writes its own module.prop here
# (prepare_environment + status display).
cat > module.prop <<EOF
id=originzygisk
name=OriginZygisk
version=$VER
versionCode=1
author=OriginSU
description=Built-in Zygisk for OriginSU (BreZygisk based)
EOF
cp -f module.prop module.prop.bak
chmod 0644 module.prop module.prop.bak

exit 0
