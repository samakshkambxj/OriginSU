#!/system/bin/sh
# OriginZygisk setup: unpack payload + fix perms + compat shim.
# Usage: setup.sh <deploy-dir>
set -e
D="$1"
mkdir -p "$D/payload"
# NOTE: unzip may warn about busy executables when the engine is running;
# that is harmless (binaries are replaced on next reboot/disabled state).
cd "$D/payload" || exit 1
{ unzip -o "$D/payload.zip" >/dev/null 2>&1 || true; }
chmod -R 755 "$D/payload/bin"
chcon -R u:object_r:system_file:s0 "$D/payload/bin" 2>/dev/null || true
# Compat shim: the BreZygisk monitor reads and updates its status in
# /data/adb/modules/rezygisk/module.prop and exits without it.
# This entry doubles as the engine status display in module lists.
mkdir -p /data/adb/modules/rezygisk
if [ ! -f /data/adb/modules/rezygisk/module.prop ]; then
    printf 'id=rezygisk\nname=OriginZygisk engine\nversion=v1\nversionCode=1\nauthor=OriginSU\ndescription=Standalone engine status is written here by the monitor.\n' > /data/adb/modules/rezygisk/module.prop
fi
cp /data/adb/modules/rezygisk/module.prop /data/adb/modules/rezygisk/module.prop.bak
# Provider presence: Zygisk modules (e.g. Play Integrity Fix) probe for an
# installed provider via `find /data/adb/modules* -name libzygisk.so`.
# Stage the real engine libs here so standalone deploy satisfies them.
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
    if [ -f "$D/payload/lib/$abi/libzygisk.so" ]; then
        mkdir -p "/data/adb/modules/rezygisk/zygisk-libs/$abi"
        cp "$D/payload/lib/$abi/libzygisk.so" "/data/adb/modules/rezygisk/zygisk-libs/$abi/"
    fi
done
